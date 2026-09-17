# Runbook — `local-mini` and `local`

## Preconditions

- Docker with Docker Compose (already a prerequisite of `room-booking-backend`).
- JDK 25 and the checked-in Gradle wrapper (already a prerequisite of `room-booking-backend`).
- **`local` only:** the Docker `loki` logging driver plugin, installed once per machine:
  ```bash
  docker plugin install grafana/loki-docker-driver:latest --alias loki --grant-all-permissions
  ```
  Not required for `local-mini` — this is exactly what keeps `local-mini` free of any host-level
  setup beyond Docker itself.

## Steps

### `local-mini`

```bash
cd room-booking-infra
make local-mini
cd ../room-booking-backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

### `local`

```bash
cd room-booking-infra
make local
cd ../room-booking-backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Stopping either

```bash
cd room-booking-infra
make down
```

## Verification

1. `docker compose -f compose.local.yaml ps` (add `-f compose.local-instrumented.yaml` for `local`)
   — every service healthy.
2. `http://localhost:3001` loads Grafana; log in with `admin` / `admin` (the image's default,
   unchanged locally since this instance never leaves the machine).
3. `bootRun` starts with no OTLP connection errors in the console.
4. Exercise `curl http://localhost:8080/actuator/health`, a register or login call
   (`POST /api/v1/auth/register` or `/login`), and one deliberately failing request (e.g. malformed
   login body).
5. Grafana Explore → **Tempo**: spans present under service `room-booking-backend`, and module
   spans if `spring-modulith-observability` was added — filter by `deployment.environment=local`
   or `local-mini`.
6. Grafana Explore → **Loki**, filtered to `service_name="room-booking-backend"`: log lines appear,
   labelled `deployment_environment="local"` (confirmed). **Not yet confirmed:** a specific request's
   trace-to-logs correlation, and the `bootRun` console's `[traceId,spanId]` placeholder being
   populated — neither could be exercised during this runbook's first pass because no code path hit
   in verification (register, login, health) logs anything at the application level; only
   framework-level logs fired, which aren't tied to a single request's span. `ApiExceptionHandler`
   logs unexpected (500-level) errors specifically — the next time this step runs, trigger one
   deliberately (e.g. a request that reaches a genuinely unhandled exception) and confirm both here.
7. Grafana Explore → **Prometheus**: the HTTP server request metric returns data points. Confirmed
   name: **`http_server_requests_milliseconds_count`** (and `..._sum`, `..._bucket`,
   `..._max_milliseconds`) — the `_milliseconds` suffix is Micrometer's OTLP naming, not the
   `_seconds` suffix a Prometheus-scrape registry would use. Each series carries
   `deployment_environment="local"` and `service_name="room-booking-backend"`, confirming the
   environment-tagging mechanism works end to end.
8. Stop the `grafana-lgtm` container only (`docker stop <container>`), then repeat step 4. The
   application must keep serving every request — an unreachable OTLP endpoint degrades
   observability, never availability (rule 9, `docs/conventions.md`). Restart the container after.
9. **`local` only:**
   - Grafana Explore → **Prometheus**: `pg_up` returns data, and a MinIO cluster metric (e.g. a
     bucket usage gauge) returns data — these are the two new `scrape_configs` jobs in
     `prometheus/prometheus.local.yml`.
   - Grafana Explore → **Prometheus**: the application's own OTLP-pushed metric from step 7 still
     returns data. The bundled Prometheus takes metrics only via OTLP push through the Collector, not
     via scraping (confirmed in `docs/observability.md`'s Phase 0 fact-check) — this step proves the
     bind-mounted `prometheus.local.yml` preserved the image's `otlp.promote_resource_attributes`
     block, which is what keeps `deployment.environment` and other labels attached to pushed metrics,
     rather than silently dropping them.
   - Grafana Explore → **Loki**: log lines from `postgres`, `minio`, `mailpit`, and
     `postgres-exporter` all appear, labelled by container name.

## Rollback

There is nothing to roll back — `make down` followed by `make local-mini` (or `make local`) returns
to a clean state. Local telemetry is disposable by design; nothing here persists across a restart.

## Deployment log

This environment runs on a developer's own machine and has no shared Deployment log — each
developer verifies their own setup against the checklist above. The one exception: if the
verification steps above stop matching reality (a metric name changes, a step no longer works as
written), fix this document immediately, per the documentation discipline in `docs/conventions.md`.
