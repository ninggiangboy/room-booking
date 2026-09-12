# Migration 022 — Ledger, reconciliation, and host payout

## Goal

Everything before this migration records what the platform *intended* and what a provider *reported*.
Neither of those is an accounting position. This migration adds the journal that says what the
platform owns, owes, and has earned, and the machinery that turns a host's share of it into money in
a bank account.

Twenty-six tables, thirty-three changesets. Two of those changesets contain no tables at all: they
are the rules that no single row can express, enforced by deferred constraint triggers that fire at
`COMMIT` when the whole set is visible.

## Six forces shaping it

**The journal balances or it does not exist.** A posted transaction is a set of positive postings in
one book, one legal entity, and one currency whose debits equal its credits. No row-level `CHECK` can
see across the rows of a transaction, so the balance rule is a `DEFERRABLE INITIALLY DEFERRED`
constraint trigger. An unbalanced set cannot be committed by any code path — not by the posting
service, not by a hand-written statement in a console.

**Posted history is immutable.** A mistake is corrected by a reversal that references the original
and a new transaction that supersedes it, never by editing the original. A trigger enforces it,
because the entire value of a journal is that yesterday's entry still says what it said yesterday.

**One business fact produces at most one accounting effect.** The unique key
`(accounting_book_id, source_type, source_id, posting_purpose)` is what makes a replayed event, a
duplicated webhook, a retried worker, and a client timeout converge on the transaction that already
exists. The source input hash is stored beside it, so a second request with the same key but
different facts is a recognisable conflict rather than a silent absorption.

**Owning money and being able to receive it are different states.** A host can be economically
entitled to an amount that is not yet released, is under a hold, is backing a reserve, or is funding
a recovery. Those are four separate records against one payable allocation, each with its own reason
and its own lifetime. Collapsing them into a status column is how an amount silently stops being the
host's.

**A payable allocation is consumed exactly once at the platform boundary.** A partial unique index
lets at most one live payout item hold any allocation, so two concurrent payout planners cannot both
select the same money whatever the application does with its locks.

**A payout crosses the same submission fence a payment does.** An instruction that reached the
provider can never be marked cancelled, a terminal failure needs a classification, and a timeout
resolves to a state that must be queried rather than retried.

## Verified behaviour

Each row was executed against PostgreSQL 17 with the migration applied. Deferred rules were probed
with real `BEGIN`/`COMMIT` blocks, because a plpgsql subtransaction does not fire deferred triggers.

| Scenario | Result |
| --- | --- |
| Balanced posted transaction, two postings | accepted |
| Posted transaction debiting 1 000 000 and crediting 900 000 | refused — deferred balance rule |
| Posted transaction with a single posting | refused — a balanced entry needs at least two |
| Posted transaction mixing VND and USD postings | refused — one currency per transaction |
| Unbalanced transaction left in `RECEIVED` | accepted — still being assembled |
| Promoting that unbalanced transaction to `POSTED` | refused |
| Editing a posting on a posted transaction | refused |
| Appending a *balanced* pair to a posted transaction | refused |
| Deleting a posted transaction | refused — post a reversal instead |
| Recording a description on a posted transaction | accepted |
| Second transaction for the same source fact and purpose | refused — unique source key |
| Negative posting amount | refused |
| Overlapping accounting periods in one book | refused — GiST exclusion |
| Adjacent, non-overlapping period | accepted |
| Hard close with no balance hash or approver | refused |
| Reserving more than a payable allocation holds | refused — ceiling |
| `remaining` disagreeing with original minus consumed minus recovered | refused |
| Payout instruction whose items sum to less than its amount | refused — deferred item-sum rule |
| Payout instruction whose items sum exactly | accepted |
| Second live payout claiming an already-reserved allocation | refused — exactly once |
| Zero-amount payout instruction | refused |
| Cancelling a payout that already reached the provider | refused — submission fence |
| Marking a payout failed with no classification | refused |
| Escalating a *paid* payout to manual review | accepted — settlement survives |
| Reissuing before the original is proven terminal | refused |
| Statement line with no posting, item, or allocation behind it | refused — no plugs |
| Editing an issued statement's totals | refused |
| Appending a line to an issued statement | refused |
| Recording the supersession on an issued statement | accepted |
| Release decision that is not a release and gives no reason | refused |
| Editing a release decision | refused — append-only |
| Webhook observation whose signature was not verified | refused |
| Same provider event delivered twice | refused |
| Observation marked applied with no operation | refused |
| Rewriting an observation | refused — append-only |
| Re-ingesting an artifact with identical content | refused |
| Editing an external record's amount | refused — immutable evidence |
| Marking that external record matched | accepted |
| Deleting an artifact | refused |
| Auto-matching on a tier-5 composite | refused — only tiers 1–4 may auto-match |
| The same tier-5 candidate recorded as a suggestion | accepted |
| `MATCHED_EXACT` naming no internal side | refused |
| Rewriting a match | refused — append-only |
| Resolving a case by journal correction with no approver | refused |
| The same case with an approver | accepted |
| Adjustment reaching `APPROVED` below its own approval threshold | refused |
| The same approver deciding twice | refused — maker-checker |
| Break-glass approval with no expiry | refused |
| Recovery collecting more than it was owed | refused — ceiling |
| Waterfall step claiming a payable offset without naming an allocation | refused |
| Disputing a recovery and then resuming collection | accepted — `disputed_at` survives |
| Writing off a remainder with no approver | refused |
| Reserve releasing more than it holds | refused |
| Reserve held above its own cap | refused |
| Payout hold with a cap but no currency | refused |
| Releasing a hold with no reason | refused |

## Tables

**Governance and policy.** `accounting_books` (one legal entity, one accounting purpose),
`ledger_accounts` (the chart of accounts, with the dimension policy each account demands),
`posting_rule_versions` (the approved deterministic mapping, stored whole and hashed),
`accounting_periods` (governed intervals, non-overlapping per book by exclusion constraint).

**The journal.** `ledger_transactions` (one approved economic event, with its source identity,
pinned rule version, and reversal relationships), `ledger_postings` (one positive amount on one
account in one direction, carrying the dimensions that let the entry be explained afterwards).

**What the host is owed.** `host_payable_allocations` (the entitlement as a thing that can be
reserved, consumed and recovered against), `host_fund_release_decisions` (why it was or was not
released, recorded every time the question is asked), `payout_holds` (money stopped without changing
whose money it is), `host_reserves` and `host_reserve_allocations` (bounded, disclosed retention).

**Getting it out.** `payout_instructions` (a durable intent to move an exact amount to one
destination), `payout_items` (exactly which allocations it consists of), `payout_operations` (one
attempt to make the provider do something), `payout_observations` (what the provider or bank actually
said, kept verbatim).

**What the host is told.** `host_statements` and `host_statement_lines`, frozen at issue.

**Proving it balances.** `external_financial_artifacts` (the file or feed a run read, by reference
and hash), `external_financial_records` (its normalized rows), `finance_reconciliation_runs`,
`finance_reconciliation_matches`, `finance_reconciliation_cases`.

**Correcting it.** `finance_adjustment_requests` and `finance_adjustment_approvals` (an allowlisted
catalog under maker-checker), `host_recoveries` and `host_recovery_allocations` (what a host owes
after money already left, and each step of the waterfall that collected it).

## Design rules this migration follows

- Amounts are unsigned `*_minor BIGINT` with an explicit direction or type beside them. A debit and a
  credit are different facts, not the same number with different signs.
- Every state value is `VARCHAR` plus a `CHECK`; no PostgreSQL enum types.
- No `DEFAULT now()` on an application-written column, and no partial index predicate references
  `now()` — the worker binds its own decision instant.
- Evidence tables carry restricting foreign keys rather than `ON DELETE CASCADE`, because an
  append-only or freeze trigger refuses the cascade and the cascade would be a promise it cannot keep.
- A timestamp that records that something happened is never un-recorded by a later state. `settled_at`
  survives an escalation to manual review; `disputed_at` survives the dispute being settled; a
  statement's `issued_at` survives its supersession.

## What this migration leaves open

- **No FX tables.** The feature document constrains the target release to
  `booking currency = collection currency = ledger currency = payout currency` and classes additional
  currencies as a designed extension. What the release does enforce is that a transaction, a payout, a
  statement, and a reserve each carry exactly one currency and never net one against another.
- **No chart of accounts is seeded.** Which accounts exist, their normal balances, and when revenue
  is recognised are finance decisions, not schema decisions. The tables are ready; the policy is a
  prerequisite the document lists under its own decision gate.
- **No tax-document instruction table.** Legal numbering, issuer requirements, and credit notes belong
  to the tax and document domain. Finance supplies exact posted amounts and references to it.

## Deviations from the plan and the feature document

**Payout destinations reuse `payout_destination_claims` from migration `015`.** The document proposes
a `payout_destinations` table. Migration `015` already delivered the same concept with the
account-takeover defences built in: tokenized account reference, ownership verification state,
cooling-off instant, one active destination per holder/market/currency, and a detachment lifecycle. A
second destination registry would mean two answers to "where does this payout go" at exactly the
moment money moves.

**Reconciliation tables are prefixed `finance_`.** Migration `021` delivered
`payment_reconciliation_runs`, `entries` and `cases`, which compare a provider's payment report
against `payment_operations`. This migration reconciles a different pair — external artifacts against
the journal, the clearing accounts, and the payouts. A green payment report does not prove the ledger
agrees with the bank, so the two sets are deliberately separate and the names say which is which.
`finance_reconciliation_runs.control_layer` names which of the document's ten control layers a run
covers.

**Platform primitives are reused, not duplicated.** The document proposes
`finance_idempotency_records`, `finance_outbox_events`, `finance_inbox_events` and
`finance_audit_actions`. Migration `012` delivered `command_idempotency_records`, `outbox_events`,
`consumer_inbox_receipts` and append-only `audit_events`, each scoped by aggregate type. A second
outbox would need a second publisher and a second ordering guarantee.

**A transaction is written un-posted and then posted.** Because nothing may be added to a posted
transaction, the posting service inserts the transaction, inserts its postings, and moves it to
`POSTED` within one database transaction. That is the lifecycle the feature document specifies
(`RECEIVED -> VALIDATED -> POSTED`), and it is what lets the immutability rule be stated simply.

**`external_financial_records` is kept, although `021` stores only digests.** Migration `021` stores
a provider row's digest because it only needs to know whether it saw that row. Reconciliation against
the ledger has to compare amounts, fees, and timestamps, so the normalized row is stored.

## Exit criteria

- All 33 changesets apply, roll back to zero tables and zero functions, and re-apply cleanly against
  PostgreSQL 17.
- The scenarios above behave as tabulated.
- Every model field maps to a real column, and every `NOT NULL` column has a field, verified in both
  directions.
- `compileJava` and `javadoc` pass, and the application boots with every repository's derived query
  name resolved.
