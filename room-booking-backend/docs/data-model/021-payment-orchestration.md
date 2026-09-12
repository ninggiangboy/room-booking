# Migration 021 — Payment orchestration

## Goal

Money leaves the platform's control the moment a request reaches a payment provider. This migration
builds the schema that makes everything after that moment provable: what the guest owes, which
journey they took, which side effect was requested of which provider, and exactly what that provider
said in reply.

Seventeen tables, twenty-one changesets. Nothing here calculates entitlement, revenue, or payout —
those belong to cancellation policy, the ledger, and finance. This domain executes and records.

## Five forces shaping it

**A timeout is not a failure.** The provider may have taken the money after the platform stopped
waiting. So an operation that has crossed the submission fence can never be marked failed without
evidence, and it can never be replaced by a second operation while its outcome is unknown — that is
how a guest gets charged twice. The fence is a constraint, not a convention:
`CANCELLED_BEFORE_SUBMISSION` is only storable on a row that was never submitted.

**Obligation, attempt, operation, and evidence are four different facts with four different
lifetimes.** One obligation outlives many attempts; one attempt issues many operations; one operation
accumulates many observations that arrive late, out of order, and sometimes twice. Conflating them is
what makes retries unsafe, so they are four tables.

**Ceilings on money are checked by the database, not only by the code holding the lock.** Captured
plus reserved can never exceed what is owed; refunded plus reserved can never exceed what was
captured. If application locking is ever wrong, the row still refuses.

**Provider evidence is append-only.** A stale webhook is stored and marked ignored, never deleted. A
refund does not rewrite its capture into a failure, and a chargeback does not turn a successful
capture into an unsuccessful one. Corrections are new rows.

**Every amount is unsigned and carries an explicit operation type.** An authorization, a capture, a
void, a refund and a chargeback are all positive numbers with different meanings, because a negative
number in a money column is an invitation to read it the wrong way.

### Verified behaviour

Each scenario below was executed against PostgreSQL 17 and its outcome observed. Every refusal is
paired with a legitimate counterpart that was accepted, so the constraints are shown to reject the
wrong thing without also rejecting the right one.

| Scenario | Expected | Result |
| --- | --- | --- |
| A route that can sell | accepted | accepted |
| A route that can neither sell nor authorize | refused | collects check rejects |
| A route offering partial refunds but no refunds | refused | partial-refund check rejects |
| An obligation for the booking total | accepted | accepted |
| Capturing beyond the amount owed | refused | capture-ceiling check rejects |
| Capturing the whole amount and settling | accepted | accepted |
| Two partial refunds overdrawing the capture | refused | refund-ceiling check rejects |
| A partial refund after settlement, keeping the settlement record | accepted | accepted |
| A `PAID` obligation that was never settled | refused | settlement check rejects |
| Reopening a settled obligation without clearing the settlement | refused | settlement check rejects |
| A dispute larger than the capture | refused | dispute-ceiling check rejects |
| A second obligation for the same purpose and snapshot | refused | unique purpose rejects |
| An obligation permitting no payment method at all | refused | methods check rejects |
| A balanced two-component schedule | accepted | accepted at commit |
| A schedule short by one component | refused | deferred sum trigger rejects |
| A replacement schedule version that adds up | accepted | accepted at commit |
| Raising one component so the schedule overshoots | refused | deferred sum trigger rejects |
| A saved card with recorded consent | accepted | accepted |
| A full card number in the masked-suffix column | refused | column width rejects |
| A reusable token with no recorded consent | refused | consent check rejects |
| The same provider token stored twice on one account | refused | unique token rejects |
| The first attempt on an obligation | accepted | accepted |
| Waiting on the guest with no deadline | refused | action-deadline check rejects |
| Waiting on the guest with a deadline | accepted | accepted |
| A failed attempt with no reason | refused | failure check rejects |
| A second attempt reusing attempt number 1 | refused | unique attempt number rejects |
| A sale ready to submit | accepted | accepted |
| A second operation reusing the provider request key | refused | unique provider key rejects |
| Crossing the submission fence | accepted | accepted |
| Cancelling locally after submitting | refused | fence check rejects |
| Failing a submitted operation with no evidence | refused | failure check rejects |
| A timeout leaving the operation `UNKNOWN` | accepted | accepted |
| A status query carrying money | refused | monetary check rejects |
| A status query carrying no money | accepted | accepted |
| A negative refund amount | refused | monetary check rejects |
| A capture with no authorization behind it | refused | parent check rejects |
| A signature-verified capture webhook | accepted | accepted |
| A webhook taken as evidence without a signature | refused | signature check rejects |
| The same provider event stored twice | refused | unique event rejects |
| Applying an observation mapped to no operation | refused | mapping check rejects |
| An orphan provider object kept as unmapped evidence | accepted | accepted |
| Rewriting provider evidence | refused | trigger rejects |
| Deleting provider evidence | refused | trigger rejects |
| A verified webhook delivery | accepted | accepted |
| The same webhook delivered twice | refused | unique delivery rejects |
| Processing an unsigned webhook | refused | verification check rejects |
| A refund execution for an approved instruction | accepted | accepted |
| The same refund instruction executed twice | refused | unique instruction rejects |
| Claiming a full refund from a partial result | refused | success check rejects |
| A refund that moved the whole approved amount | accepted | accepted |
| Allocating a refund against a capture | accepted | accepted |
| Draining the same capture twice for one refund | refused | unique allocation rejects |
| A submitted allocation with no refund operation | refused | submitted check rejects |
| A released reservation that also settled money | refused | released check rejects |
| A chargeback opened against a capture | accepted | accepted |
| The disputed capture still reading `SUCCEEDED` afterwards | unchanged | unchanged |
| A case needing a response with no deadline | refused | deadline check rejects |
| A case needing a response by a deadline | accepted | accepted |
| A resolved case with no resolution time | refused | resolution check rejects |
| Approved, scanned dispute evidence | accepted | accepted |
| Submitting an infected file to the provider | refused | submission check rejects |
| Submitting the approved evidence | accepted | accepted |
| Changing evidence after it was submitted | refused | freeze trigger rejects |
| A daily reconciliation run | accepted | accepted |
| A match that names no operation | refused | match check rejects |
| A match naming its operation | accepted | accepted |
| An orphan that somehow names an operation | refused | orphan check rejects |
| Editing a comparison after the fact | refused | trigger rejects |
| A case repaired by a command that is not recorded | refused | repair check rejects |
| A case repaired by a recorded command | accepted | accepted |

## Tables

**What is owed.** `collection_obligations` (the immutable amount, fixed from the booking's accepted
financial snapshot, carrying the capture and refund ceilings), `collection_schedule_items` (deposits,
pay-now amounts and later balances as separate due components, each with its own state and attempts).

**Who is being asked.** `payment_routes` (what it takes to send a payment to one merchant account,
and the independent switches that stop new collection during an outage without also stopping refunds
and webhooks), `payment_method_references` (a pointer to a credential the platform deliberately does
not hold).

**What was requested.** `payment_attempts` (one guest journey — this method, this provider, this
sequence of customer actions), `payment_operations` (one external side effect, with the provider
request key a retry reuses and the fencing token a second worker cannot).

**What the provider said.** `payment_operation_observations` (append-only evidence, including orphan
objects that map to no operation), `payment_webhook_deliveries` (the ingress record, written before
any business effect, deduplicated per account and event).

**Returning money.** `refund_executions` (carrying out a refund somebody else decided),
`refund_capture_allocations` (which capture each part comes out of, so the same capture cannot fund
two refunds beyond what it holds).

**Contested money.** `payment_disputes`, `payment_dispute_events` (append-only),
`payment_dispute_evidence` (mutable while assembled, frozen the moment it is sent).

**Proving it balances.** `payment_reconciliation_runs`, `payment_reconciliation_entries`
(append-only, one external row compared against one internal operation),
`payment_reconciliation_cases` (an exception somebody has to resolve, and how).

**Explaining it.** `payment_timeline_entries` (append-only projection, so answering "what happened to
my money" never means reading raw provider payloads under time pressure).

## Design rules

- The two ceilings live on `collection_obligations` as check constraints, not only in the service
  that holds the lock. `captured + capture_reserved <= amount` and
  `refunded + refund_reserved <= captured` are the constraints that carry real money.
- `settled_at` records when collection completed and a later refund does not un-record it. Requiring
  both directions would force the row to forget that the money was ever collected the moment any of
  it was returned — exactly the fact a refund argument turns on.
- A schedule must sum to its obligation. No row-level `CHECK` can see across rows, so the rule is a
  **deferred constraint trigger**: a schedule is inserted as several statements and only has to
  balance by the time the transaction commits.
- `masked_suffix` is `CHAR(4)` with a four-digit check. A card number does not fit in that column by
  construction, rather than by a reviewer noticing.
- Partial index predicates name states, never `now()`. A predicate depending on the clock silently
  stops matching rows as time moves; the worker binds its decision instant in the query instead.
- Four tables are append-only by trigger: observations, dispute events, reconciliation entries, and
  timeline entries. None of them uses `ON DELETE CASCADE`, because a cascade fires the row trigger
  and fails — a cascade on append-only evidence is a promise the trigger refuses to keep. Dispute
  evidence is the same lesson in a narrower form: it is editable until `submitted_at` is set and
  frozen afterwards, so its foreign key restricts too.

## What this migration leaves open

`refund_executions.refund_instruction_id` deliberately carries **no foreign key**. A refund is decided
in the cancellation and modification domain, which migration `023` delivers; `023` adds the
constraint forward, the same way `020` closed the `inventory_claims.booking_id` reference that `018`
had to leave open. Applied migrations are never edited.

## Deviations from the plan and the feature document

**No second provider registry.** The feature document proposes `payment_provider_accounts`. Migration
`013` already delivered `provider_accounts` — capability-scoped, bound to a legal entity and market,
carrying secret aliases rather than secrets, with an effective period and a maker-checker lifecycle.
Building a second registry beside it would mean two answers to "which merchant account is this", so
this migration adds `payment_routes`, which holds only what is specific to routing a payment.

**The obligation is called `collection_obligations`, not `payment_orders`.** The document offers both
names for the identity and uses "collection obligation" throughout its own vocabulary. The plan names
the table `collection_obligations`. The concept name won.

**No migration or backfill.** The document's migration plan assumes `payment_attempts`, `refunds` and
`payment_webhook_events` are still live and describes a dual-write period. Migration `016` retired the
whole legacy stack, so there is no data to backfill and no compatibility window. The names are reused
here with their target meanings.

**`payment_disputes` has no `outcome` column.** The document lists an outcome alongside status. The
terminal statuses (`WON`, `LOST`, `ACCEPTED`, `EXPIRED`) already are the outcome; a separate column
would only create a second place for the answer to live and a chance for the two to disagree.

**The shared `reconciliation_cases` aggregate is not created here.** The document notes finance may
own it. This migration creates `payment_reconciliation_cases` scoped to payment operations; finance's
settlement and clearing cases belong to migration `022`.

## Exit criteria

- All 21 changesets apply, roll back, and re-apply cleanly against PostgreSQL 17.
- The scenarios above behave as tabulated.
- Every model field maps to a real column, and every column has a field, verified in both directions.
- `compileJava` and `javadoc` pass, and the application boots with every repository's derived query
  name resolved.
