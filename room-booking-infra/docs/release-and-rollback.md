# Release and rollback

The backend runs its Liquibase migrations at application startup
(`spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml`, applied every time
the process boots). That is unremarkable with one instance and a stop-then-start deploy. It stops
being unremarkable the moment a release overlaps two versions of the application — a rolling
restart, or `dev` and `staging` briefly running the same migration during a promotion — because both
versions then run against whatever the schema currently is.

## Expand/contract migrations, as a binding rule

A migration must leave the schema usable by the **previous** application version, not only the new
one. Concretely:

- **Additive changes** (a new nullable column, a new table, a new index) are safe as a single
  migration — the old code simply doesn't reference the new column yet.
- **Anything that removes or narrows** what the previous version relied on — dropping a column,
  renaming one, tightening a `NOT NULL` or a constraint, changing a type — is a **two-release**
  operation:
  1. *Expand* (release *n*): add the new shape alongside the old one; the application keeps writing
     both, or the new column is populated by a backfill, while the previous version keeps working
     unmodified.
  2. *Contract* (release *n+1*, only after release *n* is the oldest version running anywhere): drop
     or narrow the old shape, now that nothing depends on it.

This is the same instinct the backend's conventions already apply to migration history — "do not
edit an applied Liquibase changeset; add the next forward migration instead" — extended from *never
rewrite the past* to *never break compatibility with the version you're replacing*.

## Where migrations run

Migrations stay at application startup while every environment runs a single instance, which is the
case as of `dev`/`staging`/`production` in Phases 5–7. The explicit trigger for changing this: **the
moment any environment runs more than one application replica.** At that point, every replica
attempting the same migration on boot is still safe under Liquibase's own locking, but it stops
being the right design — migrations should run once, as a dedicated step before the new version's
replicas start, not be entangled with every instance's boot sequence. Write that separate-step design
down when the trigger fires; it is not designed here because the input it needs (which orchestrator,
what the deploy step looks like) doesn't exist until Kubernetes does.

## Rollback

Rollback is **redeploying the previous commit SHA's image.** It is never reversing a migration.
Because every migration is backward compatible with the release before it (the rule above), the
previous image runs correctly against the current schema — that compatibility is the entire point,
and it's what rollback trades on. Liquibase's `rollbackCount` and similar schema-reversal tooling are
explicitly not part of this procedure: reversing a migration risks losing data the new version
already wrote, and the whole design exists so that path is never needed. If a deployed migration
turns out to be wrong, the fix is a new forward migration, not a reversal.

## Promotion path

One image, one commit SHA, flows through all three deployed environments:

```
merge to main → build + tag(sha) → push GHCR → deploy dev automatically
                                                      │
                                          (someone decides it's good)
                                                      ▼
                                        deploy staging (make promote)
                                                      │
                                    (reviewer approves the GitHub Environment gate)
                                                      ▼
                                        deploy production (make promote)
```

The commit SHA is the release identity — always unambiguous, always reproducible from git. Moving
`dev`/`staging`/`production` tags on the image in GHCR exist only for human readability when
browsing the registry; the deploy tooling always resolves and records the exact SHA. No semantic
versioning is introduced — there is no release cadence yet to justify one, and the SHA already
serves every purpose a version number would.

## Verifying this document, not just following it

The first `staging` deployment (`docs/runbook-staging.md`) is required to exercise both halves of
this document before `production` ever depends on them: deploy a second release and roll it back to
the previous SHA, and run one real expand/contract migration pair end to end across two releases.
Either failing is a defect in this document, to be corrected here before `production` proceeds.
