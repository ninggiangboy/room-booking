# Observability

The LGTM stack (Loki, Grafana, Tempo, Prometheus) across every environment, and the exact
application configuration that gets it there.

## Two shapes of the same stack

`local-mini` and `local` run `grafana/otel-lgtm`, Grafana's all-in-one image bundling all four
components plus Loki's push endpoint and an OTLP collector, pre-wired with correlated datasources.
`dev`, `staging`, and `production` all send telemetry to one **central** observability host running
the same four components as **separate** containers, plus a standalone OpenTelemetry Collector — the
shape Kubernetes later runs via Helm. See `docs/platform-architecture.md` for why one central stack
rather than one per deployed environment, and rule 7 in `docs/conventions.md` for why the bundled
image never appears in a deployed environment.

## Application configuration

Spring Boot 4.1 ships a dedicated starter for OpenTelemetry traces and logs, separate from the
existing Micrometer OTLP metrics registry — two different property namespaces, verified against the
Spring Boot 4.1 configuration metadata rather than assumed:

```gradle
    // Traces: this starter wires Micrometer Tracing's OTel bridge and the OTLP trace exporter.
    // Metrics are a separate mechanism (the OTLP Micrometer registry below), because Boot 4.1 keeps
    // metrics export independent of this starter.
    implementation 'org.springframework.boot:spring-boot-starter-opentelemetry'
    implementation 'io.micrometer:micrometer-registry-otlp'
    // Logs: Boot's starter configures OTLP log-export infrastructure but does NOT install a Logback
    // appender on its own -- confirmed empirically (see below) after an initial pass wrongly assumed
    // it did. This appender, plus logback-spring.xml and a wiring component, are what actually
    // connect Logback to it.
    implementation 'io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.28.1-alpha'
```

No explicit version on the first two — Spring Boot's Gradle plugin manages them, as with every other
Spring-managed dependency in `build.gradle`. The logging appender is pinned explicitly: it is not
part of Spring's dependency management, and every upstream release is tagged `-alpha`; `2.28.1-alpha`
requires `opentelemetry-api` 1.62.0+, which matches the version Boot 4.1.1 already pins (confirmed
via `./gradlew dependencies`).

**The logging appender needs two more files**, because Boot's starter does not auto-install one —
confirmed by testing: with the starter alone, traces and metrics reached the central stack but Loki
received zero log lines. `src/main/resources/logback-spring.xml` keeps Boot's default console
appender and adds an `OTEL` appender alongside it:

```xml
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
    <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"/>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="OTEL"/>
    </root>
</configuration>
```

and `dev.ngb.backend.config.OpenTelemetryLogbackAppenderInstaller` (an `InitializingBean`) calls
`OpenTelemetryAppender.install(openTelemetry)` at startup — without it, the appender exists but stays
inert and silently drops every record. Verified end to end: after adding both files, a fresh request
produced log lines in Loki labelled `service_name=room-booking-backend`,
`deployment_environment=local`.

```properties
# Traces and logs go through the OpenTelemetry starter's OTLP exporters; metrics go through the
# separate Micrometer OTLP registry. All three point at OTLP_ENDPOINT, defaulting to the local stack
# in room-booking-infra/compose.local.yaml. DEPLOY_ENV tags every signal so one Grafana instance can
# separate local, dev, staging, and production. OTLP_AUTH_HEADER carries the deployed environments'
# basic-auth credential (see docs/configuration-and-secrets.md); it is blank, and therefore sent as
# no header at all, in both local environments.
management.opentelemetry.tracing.export.otlp.endpoint=${OTLP_ENDPOINT:http://localhost:4318}/v1/traces
management.opentelemetry.tracing.export.otlp.headers.Authorization=${OTLP_AUTH_HEADER:}
management.opentelemetry.logging.export.otlp.endpoint=${OTLP_ENDPOINT:http://localhost:4318}/v1/logs
management.opentelemetry.logging.export.otlp.headers.Authorization=${OTLP_AUTH_HEADER:}
# Metrics push only where nothing scrapes -- the two local environments. Every deployed environment
# sets OTLP_METRICS_PUSH_ENABLED=false and is scraped instead; see the table below.
management.otlp.metrics.export.enabled=${OTLP_METRICS_PUSH_ENABLED:true}
management.otlp.metrics.export.url=${OTLP_ENDPOINT:http://localhost:4318}/v1/metrics
management.otlp.metrics.export.headers.Authorization=${OTLP_AUTH_HEADER:}

# Local development samples every trace; deployed environments lower the rate to avoid saturating
# the collector at real traffic volume.
management.tracing.sampling.probability=${OTLP_SAMPLE_RATE:1.0}

# Tags every signal so the central Grafana can filter by environment.
management.opentelemetry.resource-attributes.deployment.environment=${DEPLOY_ENV:local}

# Health is the only actuator endpoint exposed by default; metrics and the Prometheus endpoint are
# needed for local inspection and for the central Prometheus's scrape path in deployed environments.
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

`/actuator/prometheus` also needs `io.micrometer:micrometer-registry-prometheus` on the classpath
(confirmed empirically: without it, the endpoint 401'd from Spring Security's default deny-all on an
unmatched path rather than existing) and an explicit `permitAll()` entry in
`SecurityConfig.securityFilterChain`, alongside `/actuator/health` — the central Prometheus reaches
it only through the deployed environment's own Caddy, which is what actually authenticates the
request (`caddy/Caddyfile`), so the application itself does not need to check credentials again.

**Known discrepancy, not a bug:** the OTLP registry (local environments) and the Prometheus registry
(deployed environments, scraped) name the same measurement differently —
`http_server_requests_milliseconds_count` via OTLP versus
`http_server_requests_active_seconds_count` / `http_server_requests_seconds_count` via
`/actuator/prometheus` — because each registry picks its own base time unit and Micrometer's naming
convention differs by exporter. A Grafana dashboard built locally will not plot unmodified against a
deployed environment's data; either build dashboards against `deployed`-shaped names from the start,
or use a query that tries both.

This lives in **shared** `application.properties`, not a profile — putting it in a profile would
defeat the portability mechanism in `docs/platform-architecture.md`: every environment differs only
by the values of `OTLP_ENDPOINT`, `OTLP_AUTH_HEADER`, `OTLP_SAMPLE_RATE`, and `DEPLOY_ENV`, never by
which properties file loads.

No `logging.pattern.*` override is needed for trace/span correlation in console output: Boot fills
its default log pattern's correlation placeholder with `[traceId,spanId]` automatically once a
`Tracer` bean exists on the context, which `spring-boot-starter-opentelemetry` provides. Confirm this
in the `local-mini` verification rather than pre-empting it with a redundant property.

**Rule 9 in `docs/conventions.md`** — telemetry export must never block the application — is
verified directly in `docs/runbook-local.md`: stop the observability container and confirm the
application keeps serving traffic.

## Module-boundary spans: `spring-modulith-observability`

`room-booking-backend` is a 22-module Spring Modulith monolith, and Modulith ships an observability
module built for exactly this shape: a `BeanPostProcessor` decorates beans exposed by a module's
root package with an interceptor that records module entry and exit as spans, and every
`@ApplicationModuleListener` hop becomes a child span of the event that triggered it. Added as
`implementation 'org.springframework.modulith:spring-modulith-observability'` (version managed by
the already-imported Modulith BOM); it resolves cleanly against that BOM (verified in Phase 0) and
the application starts normally with it present.

**What `local-mini` verification actually showed:** a register/login request's trace in Tempo
carried only Spring Security's own spans (`security filterchain before/after`, `authorize request`)
and the HTTP span itself — no module-boundary span appeared. The likely reason: `AuthController`
calling its own module's service stays inside `identity`'s root package the whole way, so the
interceptor never sees a call cross into a *different* module's exposed API — nothing here spans two
modules yet. This needs re-checking against a request that genuinely does (host onboarding, which
grants a capability, is the current codebase's best candidate) before concluding the dependency is
working as intended. Until then, treat "module-boundary spans appear in Tempo" as unconfirmed rather
than working, and note the outcome here once checked. If it turns out not to produce useful spans
even across a genuine module boundary, remove the dependency and record that decision in this
document rather than leaving it silently unused.

## What each environment sends and where

| Environment | Sends to | How |
| --- | --- | --- |
| `local-mini`, `local` | The local `grafana-lgtm` container | Direct OTLP, no auth (never leaves the machine) |
| `local` (Postgres, MinIO, Mailpit containers) | The local `grafana-lgtm` container's Loki endpoint | Docker `loki` logging driver |
| `dev`, `staging`, `production` (application) | The central observability host | OTLP over HTTPS with a basic-auth header |
| `dev`, `staging`, `production` (Postgres, MinIO containers) | The central observability host's Loki push endpoint | Docker `loki` logging driver, over HTTPS with basic auth |
| Central Prometheus | Each environment's `/actuator/prometheus`, Postgres exporter, and MinIO metrics endpoint | Scrape, through that environment's reverse proxy, with a matching `basic_auth` block |

## The central stack's two public ingestion hostnames

Exactly two hostnames are published from the observability host, both behind TLS and basic auth:

- The OpenTelemetry Collector's OTLP HTTP endpoint — every application's traces, metrics, and logs.
- Loki's push endpoint — container logs from every deployed host's Docker `loki` logging driver,
  which has no OTLP client and therefore needs this second path in.

Grafana is published on a third hostname, behind its own login rather than basic auth. Prometheus,
Tempo, and Loki's query API are never public — only Grafana talks to them, over the Docker network
on the observability host.

## Sizing

The central observability host runs six containers (Prometheus, Loki, Tempo, Grafana, the OTel
Collector, Caddy) and will not fit comfortably in 2 GB of memory. Plan for 4 GB, set short retention
on all three telemetry stores, and set memory limits per container — under-sizing this host is the
most likely way the first deployment fails, so it is a Precondition in `docs/runbook-dev.md`, not a
postmortem.

## Planned: Alertmanager (Phase 1 of the production stack expansion)

`docs/platform-architecture.md` originally deferred Alertmanager to "arrives with Kubernetes,
unconfigured." `docs/production-stack-expansion.md` proposes pulling it forward instead: a
production Prometheus that nobody is paged from is not a complete production, and there is no reason
to wait for a runtime-target migration to fix that. Not implemented yet — the design:

- Runs on the **central** observability host, as a seventh container alongside Prometheus, Loki,
  Tempo, Grafana, the OTel Collector, and Caddy — one more component in the stack that already runs
  once for every deployed environment, not once per environment (the same reasoning
  `platform-architecture.md` already gives for the rest of this stack).
- Prometheus gets an `alerting:` block pointing at it, and a rules file defining what pages — at
  minimum, the application health endpoint failing, the observability host itself losing scrape
  targets, and a Postgres/MinIO exporter going dark.
- Routes to whatever notification channel is chosen (Slack webhook, email, or a generic webhook);
  see `docs/configuration-and-secrets.md`'s "Planned additions" table for the variable shape.
- Sizing: the "Sizing" section above will need revisiting once this lands — a seventh container on a
  host already planned for 4 GB.

## What is not backed up

Prometheus, Loki, and Tempo data are explicitly **not** backed up anywhere in this platform — they
are short-retention operational data, not a system of record. Stated here so nobody assumes
otherwise; see `docs/release-and-rollback.md` and each deployed environment's runbook for what *is*
backed up (Postgres, MinIO).
