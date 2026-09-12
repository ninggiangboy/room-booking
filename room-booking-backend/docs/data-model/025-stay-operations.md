# Migration 025 — Stay operations

## Goal

A stay is the part of a booking that happens in a building. This migration exists so that what was
prepared, who was let in, what was observed and what went wrong are durable facts with custody — and
so that none of them can quietly become a booking decision. Migration `024` delivered the half of
the feature document that carries words; this is the half that carries operations.

Fifteen tables, twenty-four changesets. Nine of those changesets contain no table at all: they carry
the rules no column can express.

## Six forces shaping it

**Readiness is not one status.** A property can be ready while access provisioning failed; a guest
can report arrival while a safety case is open; a stay can be over while its outcome is under review.
The feature document is explicit that these must not be collapsed, so `operational_stays` carries
five independent state columns — preparation, access, presence, incident exposure, outcome proposal —
exactly as `bookings` carries five in migration `020`.

**Instructions are released, not stored once.** Address, entry route and access code sit in different
sensitivity bands with different release conditions, so each band is its own reference. A change
after release creates a superseding version rather than an edit, and every retrieval is audited with
its purpose and its outcome. Revocation stops future retrieval; it cannot retract a code already
seen, which is why the audit is append-only and replacement is a first-class operation.

**An access grant is platform authorization; a provider credential is one way of honouring it.**
Keeping them apart is what lets a smart-lock outage fall back to an in-person handoff without
rewriting the guest's entitlement, and what lets an unknown revocation outcome be treated as
"possibly still open" instead of silently succeeding. One live grant per stay, person, unit and mode
is a partial unique index, not a service convention.

**Evidence is collected; conclusions are separate and additive.** A lock opening may be a cleaner, a
location signal may be spoofed, a read receipt proves nothing about arrival. Observations are
immutable rows carrying source, both times, confidence and provenance; the completion or no-show
proposal is a separate decision row naming the policy version and the evidence it weighed. Booking
still owns the transition.

**An incident has an order and a floor.** Its timeline is an append-only sequence allocated by the
incident row, so two agents acting at once cannot produce two "third" events. And a severity somebody
declared as a safety report cannot be lowered by a model, a script or a quiet update without a named
reviewer and a reason, nor can the safety flag be cleared at all.

**Operations never edits another domain's tables.** Every cross-domain ask — an emergency calendar
block, a refund, a payout hold, a relocation budget — is a `remedy_requests` row with its own
idempotency identity, and the owning domain's answer is recorded as a reference. That is the
difference between asking and reaching across.

## Verified behaviour

One hundred and twenty-six scenarios were executed against PostgreSQL 17 with the migration applied.
Every refusal is paired with an accepted counterpart, so the table shows a rule being enforced rather
than a table that refuses everything.

| Scenario | Result |
| --- | --- |
| A stay on a committed revision of its own booking | accepted |
| A stay built on another booking's revision | refused |
| A stay on a `DRAFT` or `SUPERSEDED` revision | refused |
| A second stay for the same booking revision | refused |
| A second live stay for one booking | refused |
| Supersede-then-insert for a new revision, in one transaction | accepted |
| An invented preparation state | refused |
| A checkout before check-in | refused |
| Superseded without naming the successor, or superseding itself | refused |
| A draft instruction version, then releasing it | accepted |
| A second released version for one stay | refused |
| A released version with no effective instant | refused |
| A duplicate version number for one stay | refused |
| Editing the content of a released version | refused |
| Superseding a released version | accepted |
| Deleting a released version | refused |
| A revocation with no reason | refused |
| An allowed retrieval of the secret band | accepted |
| A refusal that does not say why | refused |
| An allowance that released nothing | refused |
| Rewriting or deleting an access audit row | refused |
| An invented field class | refused |
| A draft keypad grant for the guest | accepted |
| A smart-lock grant with no provider account | refused |
| A grant whose stay belongs to another booking | refused |
| A grant citing another stay's instruction version | refused |
| A validity window that ends before it starts | refused |
| Activating a grant that names its credential | accepted |
| An active smart-lock grant with no credential behind it | refused |
| An in-person handoff, which has no credential to name | accepted |
| A second live keypad code for one guest and unit | refused |
| A lockbox fallback alongside the keypad code | accepted |
| Extending a live grant with no new booking revision | refused |
| Extending it together with the revision that justified it | accepted |
| Recording three reveals | accepted |
| Resetting the reveal counter | refused |
| Clearing the credential reference of a live grant | refused |
| A revocation whose provider outcome is not known | refused |
| A revocation confirmed by the provider | accepted |
| Reviving a revoked grant | refused |
| A fallback nobody authorized | refused |
| A planned provisioning operation | accepted |
| The same operation key twice | refused |
| A planned operation that claims to have been submitted | refused |
| A failure with no category | refused |
| A timed-out submission left `UNKNOWN` and retryable | accepted |
| A claim with an owner but no expiry | refused |
| A signed provider observation | accepted |
| The same provider event delivered twice | refused |
| A verified observation that does not say how it was verified | refused |
| Rewriting or deleting a provider observation | refused |
| A critical turnover task that demands evidence | accepted |
| A service window that ends before it opens | refused |
| Assigning a task to nobody | refused |
| A completion nobody attested to | refused |
| Completing an evidence-bearing task with no evidence | refused |
| A photo with nothing behind it | refused |
| Completing on evidence that has not passed scanning | refused |
| Recording the scan result on frozen evidence | accepted |
| Completing once the evidence passed | accepted |
| Signing off a task whose dependency is still open | refused |
| The dependent task once its dependency was done | accepted |
| Swapping the artifact an attestation rests on | refused |
| Placing a legal hold on frozen evidence | accepted |
| Deleting evidence under legal hold | refused |
| A guest arrival report | accepted |
| An arrival report by nobody in particular | refused |
| The same device signal recorded twice | refused |
| Moving the time an arrival was observed | refused |
| Deleting a held observation | refused |
| A completion proposal awaiting booking | accepted |
| A second live proposal for one stay | refused |
| An acceptance naming no booking transition | refused |
| An acceptance that names the transition booking made | accepted |
| Rewriting the evidence a decision rested on | refused |
| Unhooking a decision from its booking transition | refused |
| Deleting an outcome decision | refused |
| An unsafe condition, newly reported and not yet triaged | accepted |
| Triaging an unsafe condition without deciding about the calendar | refused |
| Triaging it with a block requested of inventory | accepted |
| An applied block that names no block | refused |
| An applied block naming the inventory row that carries it | accepted |
| A resolution with no time | refused |
| A declared safety report at the deterministic floor | accepted |
| A safety report filed in the ordinary queue | refused |
| A severity and a rank that disagree | refused |
| Closing an incident that was never resolved | refused |
| A duplicate of nothing in particular, or of itself | refused |
| A transfer with no case to point at | refused |
| Assigning the safety report to a named specialist | accepted |
| An assignment to nobody, or a queue owner with no queue | refused |
| Clearing a declared safety flag | refused |
| Lowering severity with no reason given | refused |
| Lowering a safety report on a classifier's say-so | refused |
| Lowering it after a named reviewer looked | accepted |
| Winding the event allocator backwards | refused |
| The first event, on a sequence the incident issued | accepted |
| An event on a sequence nobody issued | refused |
| Two events sharing one sequence number | refused |
| A transition that does not say what it moved to | refused |
| A severity change with no reason recorded | refused |
| Rewriting or deleting an incident event | refused |
| An event for an incident that does not exist | refused |
| Linking one piece of task evidence with a purpose | accepted |
| Linking the same artifact to one case twice | refused |
| Repointing a link at a different artifact | refused |
| Transferring custody to claims and holding it | accepted |
| Purging evidence under legal hold | refused |
| Deleting a held evidence link | refused |
| Asking inventory for an emergency block | accepted |
| The same ask under the same key twice | refused |
| An acceptance with no decision to look up | refused |
| Inventory's answer, recorded as a reference | accepted |
| Turning a block request into a refund request | refused |
| A rejection with no reason | refused |

Full rollback and re-apply were each proved twice: zero tables, zero functions, zero changelog rows
after rollback, and a clean re-apply afterwards.

## Tables

**Stay.** `operational_stays` (five independent state dimensions, snapshotted local times, one live
stay per booking).

**Instructions.** `instruction_sets` (versioned, banded by sensitivity, frozen once released),
`instruction_access_audit` (append-only record of every retrieval).

**Access.** `access_grants` (platform entitlement), `access_operations` (one provider call under a
stable key), `access_observations` (append-only provider and device evidence).

**Property operations.** `operational_tasks`, `task_evidence`, `maintenance_records`.

**Stay evidence and outcome.** `stay_observations`, `stay_outcome_decisions`.

**Incidents.** `incidents` (category and severity kept apart, with its own event allocator),
`incident_events`, `incident_evidence_links`, `remedy_requests`.

## Design rules this migration follows

- Status values are `VARCHAR` + `CHECK`, never PostgreSQL enum types; the longest literal fits the
  declared width.
- No `DEFAULT now()` on any application-written column, and no partial index predicate reads the
  clock — the worker binds its own instant.
- Worker queues claim with `FOR UPDATE SKIP LOCKED` under a lease with an owner and an expiry; a
  half-held lease is refused by `CHECK`.
- Append-only and freeze triggers use the `%ROWTYPE` candidate pattern: copy `NEW`, reset the columns
  that may legitimately move, refuse if anything else differs.
- Foreign keys onto append-only and frozen tables restrict rather than cascade, because a cascade
  would fire the trigger and fail.
- `NULLS NOT DISTINCT` is used on `uk_access_grants_live`, where a nullable unit is part of the
  identity, so two live credentials cannot both exist for a whole-property grant.
- The three forward-pointing supersession foreign keys are `DEFERRABLE INITIALLY DEFERRED`. This is
  not a weakening: the live-row indexes force the outgoing row to name its replacement before that
  replacement exists, so checking at commit is what makes the mandated write order legal.

## What this migration leaves open

- **No offline instruction package.** The feature document allows an encrypted, device-bound cache of
  already-released fields for a partial outage, and equally allows the target to omit it in favour of
  a documented support fallback. Nothing here stores one.
- **No relocation search.** `remedy_requests` can ask for `RELOCATION_SEARCH` and
  `RELOCATION_BUDGET`; the candidate search and its economics belong to booking and cancellation,
  whose `relocation_cases`, `relocation_offers` and `relocation_expenses` arrived in `023`.
- **No support-case tables.** An incident can be transferred to `SUPPORT`, `TRUST` or `CLAIMS` and
  records the reference it was given, but those domains are migrations `027` and `028`.
- **No secret storage or key management.** Grants carry a reference to an envelope-encrypted artifact
  and the key reference needed to open it. The key management that makes that real is a platform
  concern, not a migration.

## Deviations from the plan and the feature document

**No second outbox, inbox, idempotency, audit or legal-hold table.** The document proposes
`outbox_events`, `inbox_receipts`, `provider_webhook_inbox`, `idempotency_records`, `policy_versions`,
`audit_records` and `legal_holds` as shared records. Migration `012` delivered
`command_idempotency_records`, `outbox_events`, `consumer_inbox_receipts` and append-only
`audit_events`; `013` delivered `provider_accounts`; `021` delivered `payment_webhook_deliveries`;
`024` delivered the conversation an incident hangs off. Legal hold stays a column on the rows that can
be held, as it is in `021` and `024`.

**A stay is validated against its revision, not merely joined to it.** The document says an
operational stay is created only for an eligible booking revision. A foreign key proves the revision
exists; it does not prove the revision belongs to that booking or that it was ever committed. A
`BEFORE INSERT` trigger checks both, and the same trigger on `access_grants` checks that the stay
belongs to the grant's booking and that any cited instruction version belongs to that stay.

**Task completion is checked against the evidence, not only against the attestation.** The document
says completion is an attestation plus required evidence. The attestation half is a check constraint;
the evidence half cannot be, because it lives in another table. A trigger refuses completion of an
evidence-bearing task with nothing that passed scanning, and refuses sign-off while a declared
dependency is still open.

**An unsafe property cannot leave triage without a calendar decision.** The document says an unsafe or
unusable condition *can* request an immediate block. `ck_maintenance_records_unsafe_block` makes the
decision mandatory rather than the block: past `REPORTED`, such a record must show that a block was
requested, applied or refused. Silence is not a decision.

**Severity has an orderable rank, and lowering it is guarded.** The document gives five severity
levels and says a model may never lower a declared safety report below the deterministic minimum.
`severity_rank` is stored beside `severity` and paired to it by a check, `ck_incidents_safety_floor`
keeps a safety report at `S0` or `S1`, and a trigger refuses any lowering without a reason, refuses
lowering a safety report without a named reviewer, and refuses clearing the safety flag at all.

**Incident timelines use the allocator pattern from `024`.** The document asks for an append-only
sequence. The unique key alone would let a writer claim a number far ahead of the allocator and leave
a permanent hole, so `incidents.next_sequence` issues the numbers, a trigger refuses an unissued one,
and a second trigger refuses an allocator that moves backwards.

## What this closes

`maintenance_records.source_incident_id` is written in changeset `025-11`, before `incidents` exists.
Changeset `025-12` closes it with `fk_maintenance_records_incident` in the same migration, and the
rollback drops the constraint before the table.

Nothing was left dangling by an earlier migration for this one to pick up. `operational_stays`,
`access_grants` and `operational_tasks` take references to `bookings` and `booking_revisions` from
`020` and `023`, `properties`, `listings` and `physical_units` from `016`, `provider_accounts` from
`013`, `inventory_blocks` from `018`, `auth_sessions` from `014` and `conversations` from `024`, all
of which already existed.

## Exit criteria

- Twenty-four changesets applied cleanly, and the enum-width audit returns no rows.
- One hundred and twenty-six probe scenarios behave as tabulated above.
- Full rollback leaves no table, no function and no changelog row; re-apply is clean. Both proved
  twice.
- Every model field maps to a real column and every `NOT NULL` column has a field, nullability
  included.
- `compileJava` and `javadoc` are clean, and the application boots with all fifteen repositories
  resolved.
