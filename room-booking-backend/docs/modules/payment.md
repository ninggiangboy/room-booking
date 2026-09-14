# `payment`

## Goal

Own provider-independent payment state — collection obligation, operation, webhook, refund
execution, dispute gateway — as a module whose boundary against `ledger` is the single most
important one in the whole marketplace, and never let that boundary blur.

## Forces that shaped it

- **Payment state is not booking state or accounting state.** This is invariant 4.3 in
  `docs/marketplace-problem-breakdown.md`, quoted rather than paraphrased because paraphrasing it is
  how it gets lost: merging `payment` and `ledger` would delete exactly the boundary that document
  exists to protect. They stay two modules for the life of this system, not just for this migration.
- **R0 applies here before anything else does.** `PaymentFailureCategory` and `PaymentMethodFamily`
  were used by migrations `005` and `021` — two different migration groups — which made them look
  like shared-kernel candidates. Once both migrations are grouped into this one module, they are
  single-owner types with no cross-module use at all. `RefundExecutionState` (`021` + `023`, the
  latter now part of `booking`) resolves the same way once `023` is correctly counted as `booking`.
  None of these three move to `platform`; recomputing ownership after the module merges is what
  shows they never needed to.
- **`payment_operations` is the aggregate root** (in-module in-degree 9), with `collection_obligations`
  the second root (7) — an obligation to collect and the operations that attempt to satisfy it are
  distinct aggregates, which is why they are separate clusters rather than one.

## What it owns

- **`obligation` cluster** — `collection_obligations` (root), `collection_schedule_items`.
- **`operation` cluster** — `payment_operations` (root), `payment_attempts`,
  `payment_operation_observations`, `payment_method_references`, `payment_routes`,
  `payment_timeline_entries`, `payment_webhook_deliveries`.
- **`refund` cluster** — `refund_executions` (root), `refund_capture_allocations`.
- **`dispute` cluster** — `payment_disputes` (root), `payment_dispute_events`,
  `payment_dispute_evidence`.
- **`reconciliation` cluster** — `payment_reconciliation_runs` (root),
  `payment_reconciliation_cases`, `payment_reconciliation_entries`.

See [`../data-model/005-payment.md`](../data-model/005-payment.md),
[`../data-model/021-payment-orchestration.md`](../data-model/021-payment-orchestration.md), and
[`../features/payment-orchestration.md`](../features/payment-orchestration.md).

## Aggregate clusters inside it

17 tables across 5 clusters, each with a distinct root; `operation` is the largest because a webhook,
a route, and a timeline entry are all facts about one payment operation's life, not separate
aggregates of their own.

## What it does not own

Whether a booking gets confirmed (`booking`'s state machine reads payment outcomes, it does not own
them) or whether a payout is released to a host (`ledger`'s job, downstream of this module's
settled/failed state). `payment` records what a payment provider said happened; it does not record
what the business decided to do about it financially.

## Public API

`payment.types` — a `@NamedInterface` carrying `ReconciliationRunState` and `ReducerOutcome` — is
public today, consumed by `ledger` (an R3 case: `ledger` is already downstream of `payment`, so these
two types stay here rather than moving to `platform`). No service-level API exists yet.

## Allowed dependencies

None with live code today. Schema carries 13 foreign keys into `market`, 8 into `booking`, 3 into
`identity`, 1 into `platform`.

## Data coupling `verify()` cannot see

Inbound: `ledger` (5), `support` (2), `booking` (1). Outbound: `market` (13), `booking` (8),
`identity` (3), `platform` (1). The `payment`↔`booking` coupling in both directions is exactly the
kind of thing invariant 4.3 warns about: it is real, it is invisible to
`ApplicationModules.verify()`, and it is the reason a new foreign key crossing this specific boundary
should be treated as a design decision, not a routine migration — see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether payment-state transitions should publish `@ApplicationModuleListener` events for `ledger` to
react to, instead of `ledger` polling or directly reading settled state, is left for when this
module's service layer is written — that would be the natural first real use of the event
publication registry outside the auth-email exception.

## Exit criteria

- All 17 tables and their entities/repositories live under `dev.ngb.backend.payment.internal.model`
  / `.repository`, in the five clusters above.
- `PaymentFailureCategory`, `PaymentMethodFamily`, and `RefundExecutionState` are confirmed
  single-owner and stay inside `payment.internal.model`, not `platform`.
- `ReconciliationRunState` and `ReducerOutcome` live in `payment.types`, declared with
  `@NamedInterface("types")`.
- `ApplicationModules.verify()` passes with `payment`'s only declared dependencies being `market`,
  `booking`, `identity`, and `platform`, and with `payment`/`ledger` remaining two separate modules.
