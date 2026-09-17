> **Design document, not yet a runbook.** `dev` has not been deployed to a real host. Every file
> this runbook references exists and has been validated locally (`docker compose config`, Caddyfile
> `caddy validate`, and an end-to-end local rehearsal against a temporary copy of the central stack —
> see `docs/observability.md` and `docs/cicd.md` for what was actually exercised). What has **not**
> been exercised is a real VPS, real DNS, and real GitHub Environment secrets — this banner comes off
> once the first Deployment log entry below is real, per `docs/conventions.md`'s documentation
> discipline.

# Runbook — `dev`

## Preconditions

- The central observability host is deployed and reachable: `observability/compose.observability.yaml`,
  `observability/.env` filled in from `.env.example`, DNS for `OTEL_HOSTNAME`/`LOKI_HOSTNAME`/`GRAFANA_HOSTNAME`.
- A VPS provisioned, Docker installed, DNS pointed at it for `APP_HOSTNAME` (`docs/vps-setup-guide.md`).
- `deploy/.env` filled in from `deploy/.env.example` on that host, at `/opt/room-booking-infra/deploy/`
  — `DEPLOY_ENV=dev`, `OTLP_ENDPOINT`/`OTLP_AUTH_HEADER`/`LOKI_PUSH_URL` matching the central host's
  `INGEST_AUTH_USERNAME`/`INGEST_AUTH_PASSWORD_HASH`, and `SCRAPE_AUTH_USERNAME`/`PASSWORD_HASH` for
  the central Prometheus's `room-booking-dev*` scrape jobs to authenticate against
  (`observability/prometheus/prometheus.yml`).
- Real SMTP credentials in `deploy/.env` — Mailpit does not exist past the local environments.
- A GitHub Environment named `dev` (repository Settings → Environments) with secrets `DEV_SSH_HOST`,
  `DEV_SSH_USER`, `DEV_SSH_KEY`, and a repository variable `DEV_APP_HOSTNAME` — see
  `docs/cicd.md`'s "What each environment's deploy step actually needs".
- Sizing floor: this host runs the application plus Postgres and MinIO; size for comfortable
  headroom over a single JVM plus two data stores, not the bare minimum.
- **Planned, not yet implemented:** `docs/production-stack-expansion.md` Phase 1 adds PgBouncer,
  Redis, and continuous Postgres backup (pgBackRest or WAL-G) to this host's Compose fragment, and
  Alertmanager to the central observability host. This is a precondition here because Phase 1 is
  scoped to land before or alongside the first real deployment of any of `dev`/`staging`/`production`
  — this runbook will list PgBouncer/Redis connection details and backup verification steps once
  that work actually lands; until then, treat this bullet as the tracking pointer, not as done.

## Steps

1. `.github/workflows/release.yml` runs automatically on merge to `main`: build via
   `./gradlew bootBuildImage`, tag with the commit SHA, push to GHCR, SSH to the `dev` host, update
   `IMAGE_TAG` in `deploy/.env`, `docker compose -f deploy/compose.deploy.yaml up -d`.
2. `release.yml` runs smoke tests immediately after: a health check and a real register/login round
   trip against `https://${DEV_APP_HOSTNAME}`.

No manual step exists for a normal deploy — that is the point of `dev`. A manual re-deploy (e.g. to
pick up an infrastructure change without a new application release) is
`docker compose -f compose.deploy.yaml up -d` run by hand on the host against the current `.env`.

## Verification

1. The deployed SHA matches the merged commit (`docker compose -f compose.deploy.yaml images`, or
   the `IMAGE_TAG` line in `.env`).
2. `curl https://${DEV_APP_HOSTNAME}/actuator/health` returns `{"status":"UP"}`.
3. Central Grafana (`GRAFANA_HOSTNAME`), filtered to `deployment_environment="dev"`, shows traces,
   logs, and metrics from the just-deployed instance.
4. The central Prometheus's target list (`room-booking-dev`, `room-booking-dev-postgres`,
   `room-booking-dev-minio`) shows all three as `UP` through Caddy's TLS and basic auth.
5. `release.yml`'s smoke-test job passes.

## Rollback

Redeploy the previous commit SHA's image (`docs/release-and-rollback.md`) — never reverse a
migration. Since `dev` takes every merge automatically, "rollback" here more often means "the next
merge fixes it forward"; use an explicit redeploy only when a broken `dev` is actively blocking
other work: SSH in, set `IMAGE_TAG` in `.env` to the previous SHA, `docker compose -f
compose.deploy.yaml up -d`.

## Deployment log

*(Empty until the first real deployment. Each entry: date, commit SHA, what was verified, what
deviated from this document, what was corrected here as a result.)*
