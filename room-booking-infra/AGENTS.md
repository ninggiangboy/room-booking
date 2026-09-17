# Repository Guidelines — room-booking-infra

The binding rule set for this folder is `docs/conventions.md`. Read it before changing a compose
file, workflow, or manifest here — where it disagrees with this summary, `docs/conventions.md` wins.

## What lives here

Deployment platform for the whole monorepo, not backend-specific: environment definitions
(`compose.local.yaml`, `compose.local-instrumented.yaml`, `compose.observability.yaml`,
`compose.deploy.yaml`), the GitHub Actions workflows that build and deploy them, and the
documentation in `docs/`. `room-booking-backend/` (and any future service) plugs into this platform
by meeting `docs/service-contract.md` — this folder never contains service-specific business logic.

## Commands

Run from `room-booking-infra/`:

- `make local-mini` — the backend's dependencies plus the all-in-one observability container.
- `make local` — `local-mini` plus Postgres/MinIO metrics and container log shipping.
- `make down` — stop whichever local environment is running.

`dev`/`staging`/`production` are not started by hand — see `docs/cicd.md` and the per-environment
runbooks in `docs/`.

## The documentation discipline

Rule 1 of `docs/conventions.md`: any change to a deployed environment updates that environment's
runbook *before* the change (as the plan), *during* (as reality deviates), and *after* (a dated
Deployment log entry). This is not optional narration — it is how `staging` and `production` stay
trustworthy as environments other people rely on.

## Non-negotiables (see `docs/conventions.md` for the full nine)

1. No secret is committed — untracked `.env`, GitHub Actions secret, or Kubernetes Secret only.
2. Every image tag is pinned. No `:latest`.
3. One artifact is promoted by commit SHA, never rebuilt per environment.
4. Every database migration is backward compatible with the previous release.
5. `staging` matches `production` structurally; only sizing, retention, secrets, and hostnames differ.
