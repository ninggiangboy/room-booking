# Service contract

What any service in this monorepo must provide to be deployable on this platform. Written once so
that adding a frontend or a worker later is filling in a template rather than designing a
deployment. `room-booking-backend` is retrofitted onto this contract as the first, and so far only,
implementer — see the "Backend, against the contract" section below for exactly how, and treat any
gap between that section and reality as a defect in one or the other.

## The six requirements

1. **Build to an OCI image from one command.** The command must need no interactive input and must
   produce a tagged image on a machine with only the service's own toolchain installed — no shared
   platform tooling. `room-booking-backend` uses `./gradlew bootBuildImage` (Spring Boot's Gradle
   plugin, already applied; no Dockerfile to maintain). Another service brings its own equivalent —
   a plain `Dockerfile`, `npm run build && docker build`, whatever fits its ecosystem. The platform
   only requires that this one command exists and produces a runnable image.

2. **Take all configuration from environment variables**, with defaults that make `local-mini` work
   with zero configuration. See `docs/configuration-and-secrets.md` for the exact idiom
   (`${VAR:default}`). A service that reads a config file the platform doesn't know about, or that
   needs a manual setup step beyond starting its container, does not meet the contract.

3. **Expose a health endpoint** the platform can probe (Compose healthchecks locally, Kubernetes
   liveness/readiness probes later), and a readiness signal distinct from liveness wherever the two
   genuinely differ — a service that is running but not yet able to serve traffic (still warming a
   cache, still connecting to its database) must say so.

4. **Log structured records to stdout**, and emit OTLP traces and metrics to whatever `OTLP_ENDPOINT`
   names, tagged with `DEPLOY_ENV`. Container logs reach the central stack through the Docker `loki`
   driver at the host level (`docs/observability.md`); OTLP is what the service itself is
   responsible for emitting.

5. **Own no deployment topology.** A service ships a Compose fragment (for `compose.deploy.yaml`)
   and, once Kubernetes exists, a manifest following the platform's template — but it does not
   invent its own reverse proxy, its own certificate handling, its own secret-mounting convention,
   or its own logging arrangement. Those are platform concerns so that every service is operated the
   same way.

6. **Be stateless, or declare its state explicitly.** A service with no state needs no volume and no
   backup plan. A service with state (a database, an object store, a local cache that must survive a
   restart) says exactly what it is and where it lives, because that declaration is what
   `docs/release-and-rollback.md`'s backup and migration planning keys off. An undeclared volume is
   data nobody is backing up.

## Backend, against the contract

| Requirement | How `room-booking-backend` meets it |
| --- | --- |
| 1. One-command build | `./gradlew bootBuildImage`, via Spring Boot's Gradle plugin. |
| 2. Environment-variable configuration | `application.properties` uses `${VAR:default}` throughout for anything that varies by environment — see `docs/configuration-and-secrets.md` for the specific keys. |
| 3. Health endpoint | `/actuator/health/liveness` and `/actuator/health/readiness`, Boot's built-in probe groups — **not** plain `/actuator/health`, which aggregates every registered indicator including `MailHealthIndicator`. Confirmed empirically: a transient SMTP outage made plain `/actuator/health` return `503` while the process was otherwise healthy and serving traffic. Every healthcheck in this platform (`deploy/compose.deploy.yaml`, `k8s/room-booking/deployment.yaml`) uses the scoped paths for exactly this reason. |
| 4. Logs, traces, metrics | Structured logs to stdout by Boot's default; OTLP traces, metrics, and logs via `spring-boot-starter-opentelemetry` plus `micrometer-registry-otlp`, all pointed at `${OTLP_ENDPOINT}` — see `docs/observability.md`. |
| 5. No owned topology | Ships as a Compose fragment in `compose.deploy.yaml`; the reverse proxy, TLS, and secrets around it are the platform's, not the application's. |
| 6. Declared state | Stateless itself. Its state lives in the separately-declared `postgres` and `minio` services, each with their own volume. |

## What a second service would need to add

Nothing here is backend-specific by design, but a second service is the first real test of that
claim. Concretely: its own build command (requirement 1), its own `${VAR:default}` audit
(requirement 2), a health endpoint in whatever form its framework provides (requirement 3), an OTLP
client and stdout logging (requirement 4), a Compose fragment added to `compose.deploy.yaml`
(requirement 5), and an explicit statement of whether it holds state (requirement 6). If any of
these turns out to need a platform-level change to accommodate, that change belongs in this
document and in `docs/platform-architecture.md`, not as a one-off exception for that service.
