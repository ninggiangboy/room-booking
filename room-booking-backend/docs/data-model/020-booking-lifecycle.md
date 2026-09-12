# Migration 020 — Booking lifecycle

## Goal

Make a booking a contract that survives everything that happens to it afterwards.

Migration `018` made inventory safe under concurrency and `019` made money explainable. This
migration records the agreement those two produce, in a form that still answers questions years
later — after the listing was renamed, the rate plan retired, the house rules rewritten, and the
host's address corrected.

## Four forces shaping it

**A booking is not in one state.** It is in five at once, and they move independently. A stay can be
confirmed while payment is still authorising, cancelled while a refund is pending, and completed
while a damage claim is open. Collapsing that into one status column forces invented values like
`CONFIRMED_REFUND_FAILED`, and every new intersection adds another. `lifecycle_state`,
`payment_state`, `stay_state`, `change_state` and `refund_state` are therefore separate columns with
separate vocabularies.

**What was agreed is snapshotted, not joined to.** A listing is a living row; a contract is not. The
booking copies the presentation, the address, the time zone, the party names, and the accepted policy
versions, so that later edits to supply cannot retroactively change what a guest booked.

**Evidence is append-only, enforced by the database.** Transitions, financial snapshots, and policy
acceptances are the three records a dispute is decided from. Protecting them only in application code
leaves every future code path free to rewrite them, and the one path that forgets is the one that
matters. Corrections are appended: a wrong transition is followed by a compensating one, a wrong
amount by a new revision.

**A provisional booking always carries a deadline.** It is holding real inventory that nobody has
paid for. One without an expiry is an unsellable night no sweeper can ever reclaim, because a sweeper
can only act on a time it can read. The database refuses to store that row.

### Verified behaviour

Every row below was executed against PostgreSQL, not reasoned about.

| Scenario | Expected | Result |
|---|---|---|
| A valid provisional booking | accepted | accepted |
| A `stay_range` disagreeing with its own check-in and check-out dates | refused | range-agreement check rejects |
| A total that does not reconcile with accommodation, discount, fee and tax | refused | total check rejects |
| A second booking accepting an already-accepted quote | refused | unique quote rejects |
| A provisional booking holding inventory with no deadline | refused | deadline check rejects |
| A confirmed booking with no confirmation instant | refused | confirmation check rejects |
| A cancellation with no cancelling party recorded | refused | cancelled-by check rejects |
| A guest checked in to a booking that was never confirmed | refused | stay-needs-confirmation check rejects |
| A booking marked no-show whose stay says the guest checked out | refused | no-show agreement check rejects |
| A stay that ends before it begins | refused | date-order check rejects |
| An inventory claim citing a booking that does not exist | refused | foreign key rejects |
| An inventory claim citing the real booking | accepted | accepted |
| A night whose money does not reconcile | refused | night total check rejects |
| A night marked released with no release instant | refused | release check rejects |
| The same night sold twice on one item | refused | unique night rejects |
| A tax line that also feeds the commission base | refused | tax-line check rejects |
| A discount line citing neither a price rule nor a promotion | refused | discount-source check rejects |
| A negative line amount instead of a direction | refused | unsigned-amount check rejects |
| A transition that does not move | refused | movement check rejects |
| Two transitions claiming the same sequence number | refused | unique sequence rejects |
| Rewriting or deleting a recorded state transition | refused | trigger rejects |
| Rewriting or deleting a financial snapshot | refused | trigger rejects |
| Rewriting the evidence that a guest accepted a policy | refused | trigger rejects |
| A second financial snapshot at the same revision | refused | unique revision rejects |
| A checkout marked succeeded that produced no booking | refused | success check rejects |
| A held checkout with no recorded terms acceptance | refused | terms check rejects |
| The same checkout idempotency key used twice | refused | unique key rejects |
| Two party snapshots claiming the same role | refused | unique role rejects |
| A timeline entry visible to nobody the platform knows about | refused | visibility check rejects |

## Tables

**The contract and the attempt.** `bookings` (five independent dimensions, the stay as both civil
dates and resolved instants, and the money as agreed), `booking_checkouts` (one attempt to turn an
offer into a contract, carrying the guest-facing journey and the idempotency key that makes a retry
safe).

**What was sold.** `booking_items` (one claimed inventory resource — a whole-home stay has one, three
hotel rooms have three), `booking_nights` (per-night money, kept per night because a shortened stay
and a per-night tax each need to name exact nights), `booking_line_items` (unsigned amounts with an
explicit direction, revisioned rather than edited).

**What was agreed.** `booking_supply_snapshots` (what the guest was shown),
`booking_party_snapshots` (who the parties were, referencing contact records rather than copying
them), `booking_policy_acceptances` (which version of which policy, with the digest of the text
actually displayed).

**What happened.** `booking_state_transitions` (machine-readable, naming which dimension moved),
`booking_financial_snapshots` (what the contract was worth at each revision),
`booking_timeline_entries` (human-readable, with a mandatory audience).

## Design rules

- The stay is stored as a half-open `daterange` *and* as two dates, and a check constraint forces
  them to agree. They are the same fact twice: the exclusion machinery needs a range, every report
  needs dates, and a silent disagreement between them would corrupt a booking in a way no later read
  would notice.
- Civil times never appear without their IANA zone, and the instants they resolved to are frozen at
  booking time. A confirmed contractual deadline is never recomputed because time-zone data changed.
- Completion is compared on the resolved instant, not the civil date: a checkout hour in one zone is
  not a checkout hour in another, and completion opens real entitlements.
- Partial index predicates name no time function. A predicate depending on `now()` would silently
  stop matching rows as the clock moved, so the sweeper binds its decision instant in the query.
- The three append-only tables deliberately do **not** use `ON DELETE CASCADE`. A cascade would be a
  promise the row trigger refuses to keep; a booking with evidence against it cannot be deleted.

## What this migration fixes elsewhere

Migration `018` gave `inventory_claims` a `booking_id` column it could not constrain, because
`bookings` did not exist yet. Until now nothing stopped a claim citing a booking that was never
written — precisely the state that makes a night look sold with no contract behind it. Changeset
`020-03` closes the reference now that its target exists. Applied migrations are never edited.

## Deviations from the plan and the feature document

**The single state machine was not built.** The migration plan sketched
`DRAFT → HELD → PAYMENT_PENDING → CONFIRMED → CHECKED_IN → COMPLETED` as the booking's status. The
feature document is explicit that this is the guest-facing *journey* and that the internal dimensions
must stay distinct. The journey is preserved on `booking_checkouts.status`, which is the row the
checkout screen actually follows; the booking keeps the five dimensions.

**Nights hang off items, not off the booking.** The plan listed `booking_nights` directly under
`bookings`. Migration `018` made `QUANTITY_POOL` supply real and quotes carry a `unit_quantity`, so a
booking can already span several rooms, and the feature document's own target is
`booking → booking_items[] → booking_item_nights[]`. A single-room stay simply has one item.

**`idempotency_records` was not created.** The feature document names it, but migration `012` already
delivered `command_idempotency_records` with the same `(scope, subject_id, idempotency_key)`
uniqueness. A second table would have split one guarantee across two places.

## Exit criteria

- All 13 changesets apply, roll back, and re-apply cleanly against PostgreSQL 17.
- The 29 scenarios above behave as tabulated.
- Every model field maps to a real column, and every column has a field, verified in both directions.
- `compileJava` and `javadoc` pass, and the application boots with all 11 repositories' derived query
  names resolved.
