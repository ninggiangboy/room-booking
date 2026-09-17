# Platform architecture

Why this platform is shaped the way it is, with the alternative each decision beat. Read
`docs/conventions.md` first for the binding rules this document explains the reasoning behind.

## Two axes, kept separate

**Environment** is purpose — who the data belongs to and what breakage costs. **Runtime target** is
technology — what actually runs the containers.

| Environment | Purpose | Data | Deploys when | Runtime now | Later |
| --- | --- | --- | --- | --- | --- |
| `local-mini` | Run the backend with the least possible machinery | Throwaway | Manually | Compose, laptop | unchanged |
| `local` | Full local picture, infrastructure included | Throwaway | Manually | Compose, laptop | unchanged |
| `dev` | Shared, always-current integration target | Seeded, disposable | Automatically on merge | Compose, VPS | K8s namespace |
| `staging` | Verify a release candidate before users see it | Production-like, synthetic | Promoted from `dev` | Compose, VPS | K8s namespace |
| `production` | Real users, real data | Real | Approved promotion | Compose, VPS | K8s namespace |

Conflating these two axes was an earlier draft's mistake: it produced four "tiers" that didn't leave
room for `dev` versus `staging` as a distinct idea, and it implied Kubernetes was a fifth environment
rather than a second runtime target for the same three deployed environments. Keeping them separate
is what let `dev`/`staging`/`production` be added without inventing new machinery, and what makes
"migrate to Kubernetes" a runtime-target change applied to existing environments rather than three
new ones.

What actually separates each pair:

- **`local-mini` vs `local`** — machinery, not behaviour. `local-mini` needs no host-level setup.
  `local` adds infrastructure instrumentation and needs a one-time Docker plugin install. See
  `docs/runbook-local.md`.
- **`dev` vs `staging`** — expectation. `dev` is expected to break; it takes every merge
  automatically and anyone may find it broken. `staging` takes a deliberately promoted artifact and
  is structurally identical to production.
- **`staging` vs `production`** — values and gating, not structure. Same Compose definition,
  different sizing, retention, secrets, hostnames, and an approval gate (see `docs/cicd.md`).

## One artifact, promoted

The same image, identified by commit SHA, flows `dev → staging → production` (`docs/cicd.md`,
`docs/release-and-rollback.md`). It is never rebuilt per environment — a rebuild is a different
artifact, and promoting an artifact that was never tested is the thing `staging` exists to prevent.

## One central observability stack

The three deployed environments do not each run Prometheus, Loki, Tempo, and Grafana. One
observability stack (`docs/observability.md`) receives telemetry from all of them, and every signal
carries a `deployment.environment` resource attribute so one Grafana instance can separate
`local-mini`, `local`, `dev`, `staging`, and `production`.

**Alternative considered:** a stack per environment. Rejected for three reasons: running it once
instead of three times is the difference between a plausible and an implausible hosting bill for a
side project; comparing `staging` against `production` in one query is the main reason to have
`staging` at all, and per-environment stacks would make that a manual cross-reference; and a shared
stack forces the authenticated cross-network ingestion path (TLS, basic auth, a reverse proxy) into
existence from the first deployed environment — exactly the path Kubernetes needs later, so it gets
exercised in production for months before Kubernetes depends on it.

**The cost, accepted deliberately:** the observability host is a shared dependency. If it is down,
telemetry is lost across every environment at once. This is why rule 9 in `docs/conventions.md`
(telemetry export never blocks the application) is a binding rule and not a hope — losing
observability must never mean losing availability.

## The portability mechanism

Two things make "cheap on a VPS now, fast to move to Kubernetes later" true rather than aspirational:

1. **Application configuration is environment variables with local defaults**, following the idiom
   already in this repository
   (`app.email-verification.url=${EMAIL_VERIFICATION_URL:http://localhost:3000/verify-email}`).
   Every new setting follows it — see `docs/configuration-and-secrets.md`. A new environment, or a
   new runtime target for an existing one, is new variable values. Never a new properties file,
   never a code change.
2. **Deployed environments run the component shape Kubernetes will run.** Prometheus, Loki, Tempo,
   Grafana, and an OpenTelemetry Collector are separate components under Compose exactly as they
   become separate Helm releases. The bundled `grafana/otel-lgtm` image is used only in the two
   local environments. This is what keeps the eventual migration guide a mapping table instead of a
   redesign.

## Decisions and the alternative each beat

- **Terraform for Kubernetes provisioning, no Ansible.** Everything in this platform is
  containerized; Kubernetes plus Helm already covers configuration management for containerized
  workloads. Ansible would only earn a place if Postgres or MinIO moved onto bare VMs instead of
  running in-cluster, which stays a documented placeholder, not the plan.
- **Docker's `loki` logging driver over a Promtail sidecar**, for shipping container logs. Fewer
  moving parts — one host-level plugin install versus an extra container per host — and Promtail is
  in Grafana's maintenance-only status with Grafana Alloy as its replacement; there's no reason to
  build new design around a component with no future.
- **MinIO's native Prometheus endpoint over a sidecar exporter.** MinIO already serves metrics at
  `/minio/v2/metrics/cluster`; a sidecar would duplicate what the service already provides.
- **Direct Prometheus scrape over `remote_write`**, for pulling metrics from every environment into
  the central stack. Scraping needs nothing running on the source side beyond the metrics endpoint
  itself; `remote_write` would require each environment to run its own Prometheus or agent just to
  push, which is complexity with no corresponding benefit at this scale.
- **Basic auth plus TLS at a reverse proxy, not mTLS**, for the cross-network ingestion path. A
  reverse proxy with TLS termination and an auth header is a few lines of Caddy configuration; mTLS
  needs a certificate authority and a lifecycle to manage it, which is disproportionate for a side
  project's threat model. Revisit if that threat model changes.
- **GHCR over a self-hosted registry.** No infrastructure to run, no account beyond GitHub, and the
  monorepo already lives there.

## What is explicitly not covered yet

Named here so they are visible decisions rather than oversights: custom Grafana dashboards beyond
the defaults, log retention and cost policy beyond the sizing floor in `docs/observability.md`,
per-pull-request preview environments, choosing a cloud provider or a managed Postgres, and any
frontend service — which `docs/service-contract.md` is designed to accept but which does not exist
yet.

Alert rules and notification routing (Alertmanager) moved out of this list — see the next section.

## Planned: the production stack expansion

`docs/production-stack-expansion.md` is the design record for a fuller production posture beyond
what this document describes above: connection pooling (PgBouncer), caching and rate-limiting
(Redis), alert routing (Alertmanager, pulled forward from Kubernetes rather than waiting for it),
continuous Postgres backup (pgBackRest or WAL-G), Postgres HA fronted by HAProxy, a cross-service
event bus (Kafka in KRaft mode) once a second deployable exists, and an analytics path (CDC into
ClickHouse, a search engine) once the schema's already-reserved analytics/discovery domains get real
features. It is sequenced into four phases, each gated on a concrete trigger (first production
deploy, Postgres HA adoption, a second service, real analytics feature work) rather than a date, so
nothing is installed ahead of a feature that would use it. None of it is implemented yet; this
section exists so the plan is visible from the architecture document that everything else in this
file lives in, not buried in a separate doc nobody finds. Read
`docs/production-stack-expansion.md` for the full reasoning and the alternatives each choice beat.
