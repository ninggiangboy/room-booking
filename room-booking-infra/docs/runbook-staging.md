> **Design document, not yet a runbook.** `staging` has not been deployed to a real host. Every file
> this runbook references (`deploy/compose.deploy.yaml`, `deploy/caddy/Caddyfile`,
> `.github/workflows/promote.yml`) exists, is shared byte-for-byte with `dev` and `production`
> (`docs/conventions.md` rule 8), and has been validated locally. This banner comes off once the
> first Deployment log entry below is real.

# Runbook — `staging`

## Preconditions

Everything `docs/runbook-dev.md` requires, plus:

- `dev` exists and the SHA being promoted has already run there.
- `staging`'s own `deploy/.env`, `APP_HOSTNAME`, and DNS — never reused from `dev`.
- A GitHub Environment named `staging` with secrets `STAGING_SSH_HOST`, `STAGING_SSH_USER`,
  `STAGING_SSH_KEY` and a repository variable `STAGING_APP_HOSTNAME` (`docs/cicd.md`).
- Co-tenancy decision: `staging` may share a host with `dev` (separate Compose project, separate
  hostnames) as a reasonable side-project economy, accepting that a `dev` resource spike can disturb
  `staging`. `production` never shares a host with either. Record which was chosen the first time
  this is deployed.
- **Planned, not yet implemented:** same Phase 1 additions as `docs/runbook-dev.md`'s precondition
  (PgBouncer, Redis, backup tooling) — rule 8 in `docs/conventions.md` means `staging` gets them the
  moment they land in the shared `compose.deploy.yaml`, never on a separate timeline from `dev`.

## Steps

1. Manually dispatch `.github/workflows/promote.yml` (Actions tab → Promote → Run workflow) with
   the commit SHA to promote (the SHA already running in `dev`).
2. `promote.yml` first confirms that SHA's image actually exists in GHCR, then deploys it to
   `staging` the same way `release.yml` deploys to `dev` (SSH, update `IMAGE_TAG`, `docker compose
   -f compose.deploy.yaml up -d`), then runs a smoke test against `STAGING_APP_HOSTNAME`.

## Verification

Everything in `docs/runbook-dev.md`'s verification, filtered to `deployment.environment=staging`,
plus — required before `production` is ever allowed to depend on this document —

1. **Deploy a second release and roll it back** to the previous SHA, following
   `docs/release-and-rollback.md` exactly as written. Note anything that didn't work as documented,
   and fix the document, not just the deployment.
2. **Run one real expand/contract migration pair end to end** across two releases: an additive
   migration in release *n*, the corresponding contract migration in release *n+1*, confirming the
   *n*-release application version kept working against the expanded schema in between.

## Rollback

Redeploy the previous SHA via `promote.yml` (or by hand:
`docker compose -f compose.deploy.yaml up -d` after resetting the image tag in `.env` or the compose
override). Never reverse a migration — see `docs/release-and-rollback.md`.

## Deployment log

*(Empty until the first real deployment. Each entry: date, commit SHA, what was verified — including
whether this deployment was the one that proved rollback and expand/contract — what deviated from
this document, what was corrected here as a result.)*
