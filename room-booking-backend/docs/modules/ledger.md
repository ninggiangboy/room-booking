# `ledger`

## Goal

Own double-entry accounting, host payable, payout, statement, and reconciliation as a module whose
boundary against `payment` is preserved deliberately: `ledger` records what the business owes and has
paid, `payment` records what a provider said happened to a transaction. Neither substitutes for the
other.

## Forces that shaped it

- **`payment` ≠ `ledger` is the most important boundary in the marketplace**, per invariant 4.3 in
  `docs/marketplace-problem-breakdown.md` ("Payment state is not booking state or accounting state").
  `ledger` consumes payment's settled/failed outcomes; it does not duplicate payment's own state
  machine, and it is never merged with `payment` even though both modules are "about money."
- **`accounting_books` is the clearest aggregate root in this module** (in-module in-degree 12),
  followed by `payout_instructions` (10), `ledger_transactions` (7), and `ledger_postings` (6) — four
  distinct roots, which is why `ledger` gets six clusters rather than one flat `internal/model`.
- **`ledger` consumes `payment`'s R3-exposed types.** `ReconciliationRunState` and `ReducerOutcome`
  stay in `payment.types` rather than moving to `platform`, specifically because `ledger` is already
  downstream of `payment` (5 schema-level foreign keys, `ledger → payment`) — one more module
  depending on a type is not a reason to promote it to the shared kernel when the dependency already
  has a direction.

## What it owns

- **`book` cluster** — `accounting_books` (root), `accounting_periods`, `ledger_accounts`.
- **`journal` cluster** — `ledger_transactions` (root), `ledger_postings`, `posting_rule_versions`.
- **`payable` cluster** — `host_payable_allocations` (root), `host_reserves`,
  `host_reserve_allocations`, `host_recoveries`, `host_recovery_allocations`,
  `host_fund_release_decisions`.
- **`payout` cluster** — `payout_instructions` (root), `payout_items`, `payout_holds`,
  `payout_operations`, `payout_observations`.
- **`statement` cluster** — `host_statements` (root), `host_statement_lines`.
- **`reconciliation` cluster** — `finance_reconciliation_cases` (root), `finance_reconciliation_runs`,
  `finance_reconciliation_matches`, `finance_adjustment_requests`, `finance_adjustment_approvals`,
  `external_financial_artifacts`, `external_financial_records`.

See [`../data-model/022-ledger-and-payout.md`](../data-model/022-ledger-and-payout.md) and
[`../features/ledger-reconciliation-and-host-payout.md`](../features/ledger-reconciliation-and-host-payout.md).

## Aggregate clusters inside it

26 tables across six clusters, each anchored on the root with the highest in-module foreign-key
in-degree. `payable` and `reconciliation` are the widest clusters because a host's payable balance
and a reconciliation case both accumulate several kinds of supporting record (a reserve, a recovery,
an external artifact) that exist only to qualify the root, never as aggregates of their own.

## What it does not own

Whether a payment provider's transaction settled or failed — that is `payment`'s state, read by
`ledger` and never re-derived independently. Whether a booking is confirmed, cancelled, or modified —
that is `booking`'s state; `ledger` reacts to it, it does not decide it.

## Public API

None yet. No service exists in front of any `ledger` aggregate today; the module's root package will
carry whatever workflow-facing API (`LedgerLookup`-shaped interfaces, domain events for payout state
changes) its future service layer needs, once one exists.

## Allowed dependencies

None with live code today, since `ledger` has no service layer yet. The one declared exception is
`payment.types` (`@NamedInterface`), which `ledger` will depend on once its service layer reads
`ReconciliationRunState`/`ReducerOutcome`.

## Data coupling `verify()` cannot see

Outbound (this module's tables reference): `market` (20), `identity` (10), `booking` (9), `payment`
(5), `pricing` (2), `supply` (1), `hostverification` (1). Inbound (other modules' tables reference
this module's tables): `support` (2), `booking` (1), `growth` (10).

`ledger → booking` (9 FKs) and `booking → ledger` (1 FK, in the other direction) together are the
kind of coupling `ApplicationModules.verify()` cannot see at all: a ledger transaction names the
booking it settles, and a booking timeline entry can point back at a ledger transaction, with neither
reference expressed as a single Java import. `growth → ledger` (10 FKs) is the largest single inbound
edge, reflecting that stored-value and loyalty accounting is itself ledger-backed. See
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether payout release and reconciliation-case resolution should be driven by
`@ApplicationModuleListener` events from `payment` and `booking`, rather than a future service
reading their state directly, is left for when `ledger`'s own service layer is designed — this module
is one of the strongest candidates for that pattern precisely because of the `payment`/`booking`
coupling above.

## Exit criteria

- All 26 tables and their entities/repositories live under `dev.ngb.backend.ledger.internal.model` /
  `.repository`, in the six clusters above.
- `ledger`'s only declared `allowedDependencies` are `market`, `identity`, `booking`, `payment`
  (specifically `payment :: types`), `pricing`, `supply`, and `hostverification`.
- `ApplicationModules.verify()` passes with `ledger` and `payment` remaining two separate modules.
