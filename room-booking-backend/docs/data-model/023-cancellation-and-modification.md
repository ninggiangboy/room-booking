# Migration 023 — Cancellation, modification, and refund

## Goal

Migration `020` made a booking a contract. This migration makes that contract changeable without
making it deniable: a cancellation is a recalculation somebody must be able to explain to the person
it took money from, and a modification is a new agreement rather than an edit to the old one.

Nineteen tables, twenty-five changesets. Four of those changesets contain no table at all — two close
references forward, and two carry the rules no single row can express.

## Six forces shaping it

**The rule that was quoted is the rule that applies.** A guest accepted a cancellation policy at a
moment, in a language, against a stated check-in wall clock. A published `cancellation_policy_version`
is therefore frozen by trigger, as is an approved `policy_disclosure_version`, and the booking cites
the version rather than a label that can be redefined underneath it. Retirement is the one move left
open, because a version has to be able to stop applying.

**A booking's terms are a sequence, not a value.** A modification commits a new `booking_revision`
naming its predecessor. Exactly one committed revision is current, a predecessor is replaced at most
once so the chain cannot fork, and a committed revision is frozen. Without this, a refund calculated
after a modification settles terms neither party currently holds.

**Deciding is separate from doing.** A `cancellation_decision` is one immutable arithmetic result.
Releasing nights, returning money, recovering from a host, restoring a promotion — four effects in
other domains, each an instruction row with its own idempotency identity and its own exactly-once
key. This domain may project their status and may never mark them successful.

**A preview is a promise with an expiry.** The number shown before confirming must be the number
charged, so a decision cites the preview it accepted, a preview is redeemed at most once, and it
carries the input hash and evaluator version that make staleness detectable rather than invisible.

**An allocation that does not add up is a defect nobody notices.** A committed decision's lines must
sum to its totals, in one currency, and that is enforced by a `DEFERRABLE INITIALLY DEFERRED`
constraint trigger firing at `COMMIT`.

**Money is unsigned, with an explicit funder.** Every refunded minor unit on a decision line is
attributed to a guest, host, platform or partner, and a row-level `CHECK` requires the attribution to
be exhaustive. "Who paid for this goodwill" is the question every settlement argument turns on.

## Verified behaviour

Each row was executed against PostgreSQL 17 with the migration applied. Deferred rules were probed
with real `BEGIN`/`COMMIT` blocks, because a plpgsql subtransaction does not fire deferred triggers.

| Scenario | Result |
| --- | --- |
| Editing a published policy version's rule document | refused — frozen at publication |
| Deleting a published policy version | refused — retire it instead |
| Rewriting approved disclosure text | refused |
| Adding a later-approved locale to a published policy | accepted |
| Machine translation stored as approved terms | refused |
| Host-owned policy family naming no host | refused |
| Retiring a version that was never approved | refused |
| First revision carrying a predecessor | refused |
| Later revision carrying none | refused |
| Nights on a draft revision | accepted |
| Appending a night to a committed revision | refused |
| Editing a committed revision's total | refused |
| Deleting a committed revision | refused — supersede it |
| A second committed revision beside the first | refused — one current revision |
| Superseding, then committing the next revision | accepted |
| Two revisions replacing the same predecessor | refused — the chain cannot fork |
| Stay range disagreeing with its own dates | refused |
| Preview that both refunds and charges | refused |
| Preview expiring before it was created | refused |
| Second decision redeeming the same preview | refused |
| Retaining and refunding more than the contract was worth | refused |
| Superseded decision naming no successor | refused |
| Decision line whose refund nobody funds | refused |
| Decision line disposing of more than it started with | refused |
| Committing a decision whose lines match its totals | accepted |
| Committing a decision whose lines refund 600 of a stated 1000 | refused — deferred allocation rule |
| Committing a decision with no lines at all | refused |
| Committing a decision whose lines are in another currency | refused |
| Lowering the total *after* the lines were written, in the same commit | refused — the rule is on both tables |
| Appending a line to a committed decision | refused |
| Editing a committed decision's refund total | refused |
| Deleting a committed decision | refused |
| Recording the revision a committed decision produced | accepted |
| Pointing that decision at a different revision afterwards | refused |
| A second live full cancellation for one booking | refused |
| Refund instruction whose funding split does not sum | refused |
| Second instruction at the same decision, beneficiary and position | refused |
| Refund execution against an instruction nobody decided | refused — the forward key from `021` |
| Refund execution against a decided entitlement | accepted |
| Host recovery instruction naming no amount | refused |
| Amount with no currency | refused |
| Second recovery instruction for one decision | refused |
| Releasing the same claim twice for one decision | refused |
| Applied release carrying no fencing token | refused |
| Second live modification proposal for one booking | refused |
| Proposal holding inventory with no expiry | refused |
| Committing a proposal the host has not agreed to | refused |
| Committing a price increase with nothing to collect it | refused |
| Committing an agreed increase that names its obligation | accepted |
| The same night claimed twice by one proposal | refused |
| Override funding split leaving 40 percent unattributed | refused |
| Budget cap with no currency | refused |
| Re-applying to a programme that already judged this booking | refused |
| Specialist review naming no specialist | refused |
| Relocation spend beyond the approved budget | refused |
| Resolving a relocation case with no outcome | refused |
| Rebooking that names no replacement stay | refused |
| Relocating a guest into the stay that failed | refused |
| Second accepted offer on one relocation case | refused |
| Approving an expense for more than was claimed | refused |
| Marking an expense reimbursed with no instruction behind it | refused |

## Tables

**Policy and disclosure.** `cancellation_policy_definitions` (the family a host selects),
`cancellation_policy_versions` (the frozen terms, with rule document, evaluator version, cutoff
semantics and content hash), `policy_disclosure_versions` (the words the guest read, per locale, with
a semantic hash).

**Revisions.** `booking_revisions`, `booking_revision_nights`. `bookings.current_revision_id` and
`booking_policy_acceptances.booking_revision_id` are added forward by changeset `023-20`.

**Previews and decisions.** `cancellation_previews`, `cancellation_decisions`,
`cancellation_decision_lines`.

**Instructions.** `inventory_release_instructions`, `refund_instructions`, `adjustment_instructions`.

**Modification.** `booking_modification_proposals`, `booking_modification_deltas`.

**Overrides.** `policy_override_programs`, `policy_override_program_versions`,
`policy_override_decisions`.

**Relocation.** `relocation_cases`, `relocation_offers`, `relocation_expenses`.

## Design rules this migration follows

- Money is unsigned and carries an explicit direction and funder; a decision line's funding columns
  must sum to its refund exactly.
- A refund and a new charge are never both non-zero on the same preview, decision, or line — a
  modification nets to one direction before anybody is shown a number.
- Freeze triggers cover `INSERT` on every child table whose parent can become frozen. A trigger that
  guarded only `UPDATE` and `DELETE` would still let a night be appended to an agreed revision, or a
  line to a committed decision.
- Partial unique indexes carry the "at most one" rules: one current revision per booking, one
  unreplaced predecessor, one live full cancellation, one open proposal, one accepted relocation
  offer.
- No partial index predicate names `now()`; expiry sweepers order by a stored instant instead.
- State-and-timestamp checks are one-directional wherever the state can move on afterwards.

## What this migration leaves open

- **No legacy backfill.** The feature document's migration plan reconstructs an original revision for
  every live booking and marks it `LEGACY_UNRESOLVED`. Migration `020` replaced the historical booking
  tables outright, so there are no rows to reconstruct. The provenance vocabulary is kept, because a
  revision imported from a channel manager has the same evidential weakness.
- **No cross-instruction ceiling.** Nothing stops the sum of refund instructions issued against one
  decision from exceeding the decision's refund total; that remains service locking plus the payment
  domain's own refund ceiling against captures. The decision-to-lines sum, which is what the guest
  sees, is enforced.
- **No relocation search.** Finding candidate stays is a discovery problem; this migration stores the
  offers that were made and what the guest said about them.

## Deviations from the plan and the feature document

**Cross-row sums are enforced, not deferred to reconciliation.** The document states that cross-row
monetary sums cannot safely be expressed in the database and must be left to locked service
validation plus scheduled reconciliation. That is true of a `CHECK` constraint, which sees one row. A
`DEFERRABLE INITIALLY DEFERRED` constraint trigger sees the whole set at `COMMIT`, which is the only
moment the question can be asked. Migration `022` used the same mechanism for the journal. Scheduled
reconciliation remains worth having; it is no longer the first line of defence.

**The balance trigger sits on both tables.** On `cancellation_decision_lines` alone, a decision could
be written with correct lines, committed, and then have its totals changed by a later transaction
touching no line at all. The trigger on `cancellation_decisions` closes that path — and the probe that
proves it is the scenario that lowers the total in the same commit as the lines are written.

**Platform primitives are reused, not duplicated.** The document proposes
`domain_idempotency_records`, `booking_timeline_entries` and an outbox. Migration `012` delivered
`command_idempotency_records`, `outbox_events` and `consumer_inbox_receipts`; migration `020`
delivered `booking_timeline_entries`. Decisions and proposals carry an `idempotency_key` that resolves
against the existing record.

**`booking_policy_acceptances` gains a revision reference rather than a replacement.** Migration `020`
already created the table keyed by booking and policy type. The document wants acceptance bound to a
revision, so `023-20` adds the column forward.

**A decision is written draft, given its lines, and committed.** Because nothing may be added to a
committed decision, the command service inserts the decision, inserts its lines, and moves it to
`COMMITTED` within one database transaction — the same shape migration `022` uses for a posted
journal transaction, and the same shape a revision follows.

**Override programmes are versioned.** The document describes programmes and immutable versions; this
migration splits the scope, eligibility window, evidence requirement and funding split into
`policy_override_program_versions`, because an event's footprint widens as it unfolds and a booking
judged under Tuesday's scope must stay explainable after Thursday's widening.

## What this closes

`refund_executions.refund_instruction_id` was declared `NOT NULL` with no foreign key in migration
`021`, because the instruction it names is decided here. Changeset `023-21` adds the constraint —
the same forward move `020` used for the booking reference `018` had to leave open, and `022-29` used
for its own ledger dimensions. A refund execution can no longer exist against an entitlement nobody
decided.

## Exit criteria

- All 25 changesets apply, roll back to zero tables and zero functions, and re-apply cleanly against
  PostgreSQL 17.
- The scenarios above behave as tabulated.
- Every model field maps to a real column, and every `NOT NULL` column has a field, verified in both
  directions.
- `compileJava` and `javadoc` pass, and the application boots with every repository's derived query
  name resolved.
