# Room Booking Infra

The deployment platform for Room Booking: environment definitions, the CI/CD pipeline, the
observability stack, and the release/rollback procedure. `room-booking-backend/` is one deployable
service inside this platform; a frontend or a worker joins later by meeting the same contract
(`docs/service-contract.md`) rather than by redesigning anything here.

Read `docs/conventions.md` before changing anything in this folder — it is the binding rule set,
the way `room-booking-backend/docs/conventions/` is for the backend.

## The five environments

| Environment | Purpose | Runs on | Started with |
| --- | --- | --- | --- |
| `local-mini` | Run the backend with the least possible machinery | Your laptop | `make local-mini` |
| `local` | Full local picture, infrastructure instrumented too | Your laptop | `make local` |
| `dev` | Shared, always-current integration target | A VPS | Automatic, on merge to `main` (`.github/workflows/release.yml`) |
| `staging` | Verify a release candidate before users see it | A VPS | `gh workflow run promote.yml -f sha=<sha>` |
| `production` | Real users, real data | A VPS | Same `promote.yml` run, gated by a required reviewer |

`local-mini` and `local` are two modes of the same local stack — `local` is `local-mini` plus
Postgres/MinIO metrics and log shipping (`compose.local.yaml` / `compose.local-instrumented.yaml`).
`dev`, `staging`, and `production` share one Compose definition (`deploy/compose.deploy.yaml`) and
differ only in the values injected into it (`deploy/.env`); they all send telemetry to one central
observability stack (`observability/`) rather than each running their own. See
`docs/platform-architecture.md` for why.

## Layout

```text
room-booking-infra/
├── compose.local.yaml                 # local-mini
├── compose.local-instrumented.yaml    # local (overlay on the above)
├── prometheus/prometheus.local.yml    # local's scrape config for postgres-exporter + MinIO
├── observability/                     # The central stack: prometheus, loki, tempo, grafana,
│                                       # otel-collector, caddy -- see compose.observability.yaml
├── deploy/                            # Shared by dev/staging/production: compose.deploy.yaml,
│                                       # caddy/Caddyfile, .env.example
└── docs/                              # Everything below
```

## Quick start

```bash
cd room-booking-infra
make local-mini
cd ../room-booking-backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

The API is at `http://localhost:8080`, Grafana at `http://localhost:3001`. `make local` instead of
`make local-mini` adds Postgres/MinIO metrics and container log shipping — see
`docs/runbook-local.md` for its one-time prerequisite.

## Documentation

- [Conventions — the binding rule set for this folder](docs/conventions.md)
- [Platform architecture — environments, runtime targets, and every design decision](docs/platform-architecture.md)
- [Service contract — what a service must provide to be deployable](docs/service-contract.md)
- [Configuration and secrets](docs/configuration-and-secrets.md)
- [Release and rollback](docs/release-and-rollback.md)
- [CI/CD](docs/cicd.md)
- [Observability](docs/observability.md)
- [Runbook — local-mini and local](docs/runbook-local.md)
- [Runbook — dev](docs/runbook-dev.md)
- [Runbook — staging](docs/runbook-staging.md)
- [Runbook — production](docs/runbook-production.md)
- [VPS setup guide](docs/vps-setup-guide.md)
- [Migration guide — VPS to Kubernetes](docs/migration-vps-to-kubernetes.md)

## Status

As of this writing: `local-mini` and `local` run and are verified end to end (traces, metrics, and
logs confirmed reaching Grafana; see `docs/runbook-local.md`). The `dev`/`staging`/`production`
Compose files, Caddy configs, the central observability stack, and all three GitHub Actions
workflows (`ci.yml`, `release.yml`, `promote.yml`) exist and pass local validation (`docker compose
config`, `caddy validate`, `actionlint`, and an end-to-end rehearsal of the app pushing telemetry
through a temporary copy of the central stack — see `docs/observability.md`). None of the three
deployed environments has run on a real host yet — that needs a provisioned VPS per environment, DNS,
and the GitHub Environment secrets `docs/cicd.md` lists. Their runbooks carry a banner saying so and
are promoted to live runbooks on first real deployment, per the documentation discipline in
`docs/conventions.md`. Kubernetes (Phase 8, `terraform/` + `k8s/`) is not deployed anywhere real, but
its mechanics were verified end to end against a local `kind` cluster — real traces and logs flowing
through, Ingress TLS and basic auth tested directly, findings recorded in
`docs/migration-vps-to-kubernetes.md` — and stays that way, torn down, until a VPS is genuinely
outgrown per `docs/platform-architecture.md`.
