> **Design document, not yet a runbook.** `production` has not been deployed to a real host. Every
> file this runbook references exists and is shared byte-for-byte with `dev`/`staging`
> (`docs/conventions.md` rule 8: same `deploy/compose.deploy.yaml`, different `.env`). This banner
> comes off once the first Deployment log entry below is real.

# Runbook — `production`

## Preconditions

Everything `docs/runbook-staging.md` requires, plus:

- The SHA being promoted has already been verified in `staging`, including — at least once, prior
  to this being the first production deployment — a proven rollback and a proven expand/contract
  migration.
- `production`'s host is isolated: never shared with `dev` or `staging`.
- A backup schedule configured and confirmed running *before* this deployment goes live, not after:
  a scheduled `pg_dump` pushed off-host, and either the VPS provider's volume snapshots or a
  scheduled `mc mirror` for MinIO — the choice made and the exact command written down the first
  time this is deployed.
- A restore rehearsed at least once against a non-production target. A backup nobody has restored is
  a hypothesis, not a backup.
- `OTLP_SAMPLE_RATE` set below `1.0` — sampling every request at production volume saturates the
  central collector.
- Retention and sizing on the central observability host reviewed against real traffic, not the
  `dev`/`staging` sizing floor.
- The GitHub Environment named `production` has a required reviewer configured.
- **Planned, not yet implemented:** same Phase 1 additions as `docs/runbook-dev.md`'s precondition
  (PgBouncer, Redis, Alertmanager, pgBackRest/WAL-G) — see `docs/production-stack-expansion.md`.
  These are scoped to land *before* `production`'s first real deployment, not after: the backup-tool
  choice in particular upgrades the `pg_dump`-based precondition already above to point-in-time
  recovery, which is worth having from the first real deployment rather than retrofitting once real
  user data exists. Phases 2–4 (Postgres HA/HAProxy, a second service's gateway routing and Kafka,
  and the CDC/ClickHouse/search-engine analytics path) are each gated on a trigger that hasn't
  happened yet and are not preconditions for this first deployment — see that document's phased
  rollout.

## Steps

1. Manually dispatch `promote.yml` with the commit SHA already verified in `staging`.
2. `promote.yml` pauses for the required reviewer's approval on the `production` GitHub Environment.
3. On approval, deploys that exact image to `production` via
   `docker compose -f compose.deploy.yaml up -d` with `production`'s `.env`.

## Verification

Everything in `docs/runbook-staging.md`'s verification, filtered to
`deployment.environment=production`, plus confirmation that the approval gate actually blocked the
deploy until approved (verify this the first time, not assumed from `promote.yml`'s configuration).

## Rollback

Redeploy the previous SHA via `promote.yml`, through the same approval gate — an emergency rollback
still goes through review, because an unreviewed change to production is exactly the risk the gate
exists to catch, even when that change is "go back to what was already running." Never reverse a
migration — see `docs/release-and-rollback.md`.

## Deployment log

*(Empty until the first real deployment. Each entry: date, commit SHA, what was verified, what
deviated from this document, what was corrected here as a result. This log is production's audit
trail of every release — keep every entry, never overwrite one.)*
