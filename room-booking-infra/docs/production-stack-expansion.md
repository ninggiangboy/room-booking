# Production stack expansion — design proposal

> **Status: proposal, decisions made, not yet implemented.** Nothing in this document has been
> wired into `compose.deploy.yaml`, `terraform/`, `k8s/`, or any runbook. Per `docs/conventions.md`
> rule 1, the *before* step — updating the relevant runbook to state intent — happens once
> implementation of a phase actually starts, not before. Every open question this document
> originally carried has been resolved (see "Decisions" below); what remains is sequencing the
> implementation work itself.

## Why this document exists

Today's `production` design (`docs/runbook-production.md`, structurally identical to `staging` per
conventions rule 8) runs `room-booking-backend` + PostgreSQL/PostGIS + MinIO behind Caddy, with
telemetry sent to the central LGTM stack. That is enough to run the one deployable that exists. The
owner asked for a fuller production posture — connection pooling, caching, event streaming,
analytics, a gateway, and load balancing — before treating production as complete. This document
proposes concrete components for each, phased so nothing is installed to serve a feature that
doesn't exist yet (the same discipline `platform-architecture.md`'s "What is explicitly not covered
yet" section already applies).

## Scope and non-goals

- Infrastructure only. Feature work that would actually *use* some of these (a search-indexing
  service, an analytics ETL job) is out of scope — `room-booking-backend`'s analytics/ML/discovery
  schema domains are persistence-only today (root `README.md`), and standing up their consumers
  before they exist would be exactly the "hypothetical future requirement" this platform's own
  conventions warn against.
- This does not re-litigate the two-axis (environment × runtime target) model or the one-artifact
  promotion pipeline in `platform-architecture.md`. Everything below slots into that model, deployed
  through the same `dev → staging → production` promotion.

## Proposed components

| # | Component | Role | Phase | Why this phase |
| --- | --- | --- | --- | --- |
| 1 | **PgBouncer** | Connection pooling in front of PostgreSQL | 1 | HikariCP already pools app-side, but PgBouncer protects Postgres once a second connecting workload exists (CDC in phase 4, a second service in phase 3) or replicas multiply connections. Cheap and transparent enough to add before it's strictly required. |
| 2 | **Redis** | Cache, rate limiting, session/refresh-token lookups | 1 | Offloads read-hot paths (e.g. the email-verification rate limiting the backend already implements in Postgres) and becomes the natural shared store once more than one backend replica needs to agree on a rate limit. |
| 3 | **Alertmanager** | Routes Prometheus alerts to Slack/email/webhook | 1 | `platform-architecture.md` currently defers this to "arrives with Kubernetes, unconfigured." Proposing to pull it forward: a production that pages nobody isn't complete regardless of runtime target. |
| 4 | **pgBackRest or WAL-G** | Continuous WAL archiving + point-in-time recovery | 1 | `runbook-production.md`'s Preconditions already require a backup schedule and a rehearsed restore, but a scheduled `pg_dump` alone only ever recovers to the last dump. This is the highest-value near-term hardening of the eight-plus-extras list if minimizing the data-loss window matters. |
| 5 | **HAProxy** | Fronts a Postgres HA setup (Patroni): TCP-level health-checked routing to whichever node Patroni currently holds as primary | 2 | Deliberately *not* used for backend HTTP load balancing — see "Decisions" below for why that would duplicate Caddy. Its phase-2 trigger is adopting Postgres HA, independent of backend replica count. |
| 6 | **API gateway — Caddy, extended** | Central routing/auth/rate-limit policy across services, staying the platform's HTTP edge it already is | 3 | `service-contract.md` already anticipates a second service. Caddy's reverse-proxy config grows to route by service instead of being replaced — see "Decisions". |
| 7 | **Apache Kafka (KRaft mode)** | Cross-service event bus | 3 | `spring-modulith-starter-jdbc`'s event publication registry already gives the one existing deployable a transactional, in-process outbox. Kafka would duplicate that until a second service exists to consume events across a process boundary. KRaft mode: no separate ZooKeeper ensemble to operate. |
| 8 | **Kafka UI** | Operational visibility for #7 | 3 | Trivial to add alongside Kafka. |
| 9 | **CDC (Debezium)** | Streams Postgres row changes into #7 | 4 | Needs both the message bus (#7) and a real downstream consumer (search indexing, ClickHouse) to justify running. |
| 10 | **ClickHouse** | OLAP store for the analytics/ML schema domains | 4 | The 420-table schema already reserves this domain (root `README.md`), but no service reads or writes it yet. Fed by whichever of CDC or batch ETL fits the feature that lands first — see "Decisions". |
| 11 | **Search engine** (Meilisearch or OpenSearch) | Backs the "discovery" schema domain | 4 | Same reasoning as #10 — the schema exists, the feature doesn't yet. |

Two components from the original list of eight are addressed as **deferred, not phased**:

| Component | Why deferred |
| --- | --- |
| **Secrets manager (e.g. HashiCorp Vault)** | `conventions.md` rule 2 already covers secrets via untracked `.env` / GitHub Environment secrets, and `platform-architecture.md` explicitly rejected mTLS-grade complexity as "disproportionate for a side project's threat model." Vault is the same trade-off — listed here so the decision is visible rather than silently skipped, not recommended until multiple services or a compliance requirement need centralized rotation. |
| **CDN / WAF (e.g. Cloudflare)** | Same reasoning: worth adding once real user traffic and real MinIO-served media volume exist, not before. |

## Phased rollout

- **Phase 1 — before or alongside the first real production deploy:** PgBouncer, Redis,
  Alertmanager, pgBackRest/WAL-G.
- **Phase 2 — once Postgres HA (Patroni) is adopted:** HAProxy, fronting Patroni. Not gated on
  backend replica count — Caddy already load-balances the app tier via its own `lb` directive, and a
  single backend replica has no HTTP tier to balance in the first place.
- **Phase 3 — once a second deployable service exists** (`service-contract.md`'s "second service"
  scenario stops being hypothetical): Caddy's routing extended to cover per-service policy, Kafka
  (KRaft), Kafka UI.
- **Phase 4 — once analytics/discovery features are actually built on top of the existing schema:**
  CDC, ClickHouse, search engine.
- **Deferred, revisit only if the threat model or team size changes:** secrets manager, CDN/WAF.

## Decisions

1. **HAProxy fronts Postgres HA, not backend HTTP traffic.** Assigning HAProxy to app-tier load
   balancing as well would mean two proxies doing overlapping jobs, since Caddy stays the HTTP edge
   (decision 3). The split: Caddy owns HTTP/service routing and TLS, the role it already has; HAProxy
   owns TCP-level, health-checked routing to whichever Postgres node Patroni currently holds as
   primary — the pairing HAProxy is built for, and something Caddy has no comparable primitive for.
   This is the "best," not the "fewest moving parts," answer, per the owner's explicit instruction.
2. **Kafka in KRaft mode, not Redpanda.** Redpanda would have been the lower-operational-overhead
   choice (no separate ZooKeeper/KRaft controller quorum to reason about, on top of Kafka-API
   compatibility). The owner chose Kafka explicitly; KRaft mode is taken as given rather than
   revisited, since it removes the one operational cost (a separate ZooKeeper ensemble) Redpanda
   would have avoided, without giving up the reference implementation and its wider tooling/ecosystem
   support.
3. **Caddy stays the gateway; no Traefik.** Traefik was proposed and initially chosen, then reversed
   by the owner in favor of keeping Caddy. This is actually consistent with `platform-architecture.md`'s
   existing "run it once, not three times" bias and its own prior "Caddy's own reverse-proxy config is
   adequate" reasoning: introducing a second reverse proxy to replace a working one would have been
   churn without a capability Caddy's `lb` directive and routing config can't already provide for this
   platform's scale. Phase 3 therefore extends `deploy/caddy/Caddyfile` with per-service routes as the
   second deployable arrives, rather than swapping components. Revisit only if a concrete Caddy
   limitation (a policy it genuinely cannot express) shows up once that second service exists.
4. **ClickHouse ingestion supports both CDC and batch ETL.** The owner's answer was "could be
   both" rather than picking one. Concretely: CDC (#9) stays the default path for domains that need
   near-real-time freshness in ClickHouse; a direct batch ETL job is the fallback for domains where a
   scheduled job is simpler and near-real-time isn't a requirement. Which domain gets which is decided
   per analytics feature when phase 4 work actually starts, not fixed here.

## What happens next

Per `docs/conventions.md` rule 1, once a phase is agreed: the relevant runbook is updated first
(*before*, stating intent), then the Compose/Terraform/Kubernetes change is made, then a dated
Deployment log entry is added (*after*), and anything durable is folded into
`docs/platform-architecture.md` and the root `README.md`'s Tech stack list. Nothing in this document
changes those files yet — it is the thing to review before any of that starts.
