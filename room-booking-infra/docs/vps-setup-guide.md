> This guide is provider-agnostic and applies to the central observability host and every
> `dev`/`staging`/`production` host equally — the differences between them are `.env` values, not
> setup steps. It is written ahead of any real deployment; correct it against reality the first time
> it's followed, per the documentation discipline in `docs/conventions.md`.

# VPS setup guide

## Provisioning

1. Provision a VPS from any provider. Sizing: the observability host needs ~4 GB (see
   `docs/observability.md`'s sizing note); an application host (`dev`/`staging`/`production`) needs
   enough for the JVM plus Postgres plus MinIO — size generously rather than to the bare minimum,
   since under-sizing is the most common first-deployment failure across this whole platform.
2. Point DNS: an A record per published hostname on this host (the application's API hostname for a
   deploy host; the OTLP, Loki-push, and Grafana hostnames for the observability host).
3. Install Docker and Docker Compose.
4. **If this host ships container logs** (every host does, except a bare Kubernetes node once that
   exists): install the Docker `loki` logging driver plugin —
   `docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions`.

## Deploying

1. Clone (or `rsync`) `room-booking-infra/` to the host. `release.yml`/`promote.yml`
   (`docs/cicd.md`) SSH in and expect `deploy/` at exactly `/opt/room-booking-infra/deploy/` on an
   application host — use that path so CI's deploy step needs no per-host configuration; the
   observability host has no CI deploy step, so its own path choice doesn't matter as much, but the
   same convention (`/opt/room-booking-infra/observability/`) keeps every host consistent.
2. Copy `.env.example` to `.env` in that same directory (`observability/.env` on the observability
   host, `deploy/.env` on an application host) and fill it in with this host's real values — never
   commit the filled-in file. Remember to double every literal `$` in a bcrypt hash to `$$`
   (`observability/.env.example` explains why).
3. `docker compose -f compose.observability.yaml up -d` (observability host) or
   `docker compose -f compose.deploy.yaml up -d` (application host) — the first deploy on an
   application host needs `IMAGE_TAG` in `.env` set by hand to a real SHA already pushed to GHCR;
   every deploy after that, `release.yml`/`promote.yml` update it automatically.
4. Confirm Caddy obtains a TLS certificate on first request — check its logs for ACME success. No
   certbot cron is needed; renewal is automatic.

## Ongoing operations

- **Certificate renewal** — automatic via Caddy; nothing to schedule.
- **Backups** — an application host schedules `pg_dump` (pushed off-host) and either provider
  snapshots or `mc mirror` for MinIO; see `docs/runbook-production.md`'s Preconditions for the exact
  choice once made. The observability host is explicitly not backed up — see
  `docs/observability.md`.
- **Shipping a new application version** — never done by building on this host. The image is built
  in CI (`docs/cicd.md`) and pulled here by commit SHA; this host only ever runs
  `docker compose -f compose.deploy.yaml up -d` with an updated `.env` or a fresh `docker compose
  pull` for the new tag.
- **Rotating a secret** — update `.env`, then
  `docker compose -f <compose file> up -d --force-recreate` for the affected service. See
  `docs/configuration-and-secrets.md`.

## Sizing reference

| Host | Runs | Floor |
| --- | --- | --- |
| Observability | Prometheus, Loki, Tempo, Grafana, OTel Collector, Caddy | ~4 GB |
| `dev` / `staging` / `production` | Application, Postgres, MinIO, Caddy | Sized to the JVM's heap plus both data stores' working set — no single number applies across environments; state the real figure here once `dev` is deployed and observed. |
