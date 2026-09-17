# Configuration and secrets

How one value travels from a committed default to a running instance in any environment, without a
secret ever being committed.

## The idiom

The repository already uses environment-variable defaults for anything that varies by environment:

```properties
app.email-verification.url=${EMAIL_VERIFICATION_URL:http://localhost:3000/verify-email}
```

Every setting this platform introduces follows the same shape — a committed default that makes
`local-mini` work with no configuration, overridden by a variable in every other environment. This
is what rule 6 in `docs/conventions.md` means by "configuration is parameterized, never duplicated
per environment": a new environment is new values against one definition, never a new properties
file.

## Where a value lives, per environment

| Environment | Source of values |
| --- | --- |
| `local-mini`, `local` | The committed defaults in `application.properties`. Nothing to configure. |
| `dev`, `staging`, `production` | An untracked `.env` file on that environment's host, read by `compose.deploy.yaml` via `env_file:`. A committed `.env.example` documents every required key with a placeholder value. |
| CI (build and deploy steps) | GitHub Actions secrets, scoped to the `dev`, `staging`, and `production` GitHub Environments so a `staging` secret is never visible to a `dev` job. |
| Kubernetes (once it exists) | Kubernetes Secrets, one per namespace — Secrets don't span namespaces, so the same credential is provisioned once per environment rather than referenced across them. |

## Backend settings that must be audited onto this idiom

As of Phase 2, these settings in `application.properties` are still blank-in-shared, filled only by
the `local` profile, rather than `${VAR:default}`:

- `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`
- `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password`
- `security.jwt.secret`
- `app.secret-box.key`

Each moves to `${VAR:default}` where a safe local default exists (the datasource and mail settings
already have one in `application-local.properties`); the two secrets (`security.jwt.secret`,
`app.secret-box.key`) keep a **local-only** default that is deliberately public — see the existing
comments in `application-local.properties` — and every deployed environment must inject its own.

## Rotation

If a value leaks: rotate it at its source (regenerate the credential, mint a new signing key), write
the new value into the affected environment's `.env` or GitHub Actions secret, redeploy
(`docker compose -f compose.deploy.yaml up -d --force-recreate` for the affected service, or the
equivalent Kubernetes Secret update plus rollout restart later), and record the rotation — what
changed and why, not the value itself — in that environment's runbook Deployment log.

## Planned additions — Phase 1 of the production stack expansion

`docs/production-stack-expansion.md` proposes PgBouncer, Redis, Alertmanager, and continuous
Postgres backup (pgBackRest or WAL-G) for Phase 1 — before or alongside the first real deployment.
These are not implemented yet; the variables below are the intended shape, following the same
idiom as every other setting in this document, recorded now so the design is reviewable before the
Compose fragments and `application.properties` changes land.

| Component | New variable(s) | Source of values, once implemented |
| --- | --- | --- |
| PgBouncer | `spring.datasource.url` keeps pointing at Postgres directly in `local-mini`/`local`; in `dev`/`staging`/`production` it points at PgBouncer's host:port instead of Postgres's — a value change in `.env`, not a new property. | `deploy/.env` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (blank/local default for `local-mini`/`local`, matching the existing idiom for `spring.mail.*`) | `deploy/.env` |
| Alertmanager | `ALERTMANAGER_SLACK_WEBHOOK_URL` (or the chosen notification channel's equivalent) — lives in the **central** observability stack's `.env`, not per-deployed-environment, matching how Prometheus/Loki/Tempo/Grafana are already configured once centrally | `observability/.env` |
| Backup tool | `PGBACKREST_REPO_TYPE`/equivalent target (or `WALG_*` if WAL-G is chosen instead — the two are alternatives, not both), pointing at the same object storage already in use (MinIO, or a provider volume per `runbook-production.md`'s existing backup precondition) | `deploy/.env` |

Nothing here is committed to `application.properties` or any Compose file yet — see
`docs/production-stack-expansion.md`'s "What happens next" for the sequencing.

## What must never happen

The development JWT secret and secret-box key baked into `room-booking-backend/src/main/resources/
application-local.properties` are dev-only by design and documented as such in that file. Reusing
them in any deployed environment is a defect, not a shortcut — anyone with the repository has them.
