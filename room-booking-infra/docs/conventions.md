# Conventions

This document is the binding rule set for `room-booking-infra/`. Every change here — written by a
person or by an agent — must follow it. Where it disagrees with `README.md` or `AGENTS.md`, this
document wins and the other two must be corrected in the same change. This mirrors how
`room-booking-backend/docs/conventions/` governs the backend.

## The non-negotiables

1. **The deployment documentation discipline.** For any change to a deployed environment
   (`dev`, `staging`, `production`, and later Kubernetes):
   - *Before* — update that environment's runbook (`docs/runbook-<env>.md`) to describe the
     intended change and its preconditions. The updated runbook is what gets reviewed; it states
     intent, it does not record history yet.
   - *During* — amend the runbook as reality deviates, at the moment of discovery. A step that
     didn't work as written is corrected in the document immediately, not reconstructed afterward
     from memory.
   - *After* — add a dated entry to that runbook's **Deployment log**: what was deployed, what was
     verified, what broke, what configuration changed. Propagate anything durable into
     `platform-architecture.md` and the root `README.md`.

   A `staging` or `production` runbook that has never had a real deployment carries a banner marking
   it a design document, not yet a runbook. The banner comes off when the first Deployment log entry
   lands.

2. **No secret is committed.** A deployed environment's credentials live in an untracked `.env`
   (read via Compose's `env_file:`), a GitHub Actions secret, or — once Kubernetes exists — a
   Kubernetes Secret. A committed `.env.example` documents the required keys and is the only
   committed form. If a secret leaks: rotate it, redeploy, and note the rotation in the relevant
   runbook's Deployment log.

3. **Every image tag is pinned**, including this platform's own (`grafana/otel-lgtm`,
   `prom/prometheus`, `grafana/loki`, `grafana/tempo`, `grafana/grafana`,
   `otel/opentelemetry-collector-contrib`, `caddy`, `prometheuscommunity/postgres-exporter`,
   `postgis/postgis`, `minio/minio`, `axllent/mailpit`). No `:latest` anywhere — these stacks bundle
   several independently versioned projects, and a floating tag turns an unrelated restart into an
   unplanned upgrade.

4. **One artifact is promoted, never rebuilt.** The commit SHA that passed CI (`docs/cicd.md`) is
   the image that reaches `dev`, then `staging`, then `production`. Rebuilding per environment
   produces a different artifact than the one that was tested, which defeats the point of having
   `staging` at all.

5. **Every database migration is backward compatible with the previous release.** A migration must
   leave the schema usable by the application version it is replacing, because a rolling deploy runs
   both versions against it briefly. See `docs/release-and-rollback.md` for the expand/contract
   pattern this implies — this is the rule that makes rollback possible at all.

6. **Configuration is parameterized, never duplicated per environment.** A new environment is new
   values (`.env`, GitHub Actions secrets, or Kubernetes Secrets) against one shared definition, not
   a new properties file or a new compose file. See `docs/configuration-and-secrets.md`.

7. **The bundled observability image (`grafana/otel-lgtm`) is for `local-mini` and `local` only.**
   `dev`, `staging`, and `production` all send telemetry to one central stack running the components
   separately, which is the shape Kubernetes later runs via Helm. Using the bundled image in a
   deployed environment is a defect — Grafana documents it as unsuitable for production.

8. **`staging` matches `production` structurally.** They share one Compose definition
   (`compose.deploy.yaml`) and differ only in sizing, retention, secrets, and hostnames — never in
   which components run or how they're wired. A structural shortcut in staging is a defect, because
   it silently stops staging from predicting production, which is the only reason staging exists.

9. **Telemetry export never blocks the application.** An unreachable observability collector must
   degrade observability, not availability — the application keeps serving traffic. This is verified
   explicitly in the `local` runbook and must stay true in every deployed environment.

## Using this as a checklist

Before changing anything under `room-booking-infra/`, check it against the nine rules above. Before
deploying to `staging` or `production`, walk the relevant runbook's Preconditions section — that is
this folder's definition of done.
