# `booking`

## Goal

Own the stay contract and its full lifecycle — the reservation itself, its revision chain,
cancellation, modification, refund instruction, and relocation — as one module, because the revision
chain *is* booking state, not an adjunct to it.

## Forces that shaped it

- **The strongest bidirectional cycle in the whole schema forces migrations `004`, `020`, and `023`
  together.** `bookings → booking_revisions` and `booking_revisions → bookings` form a two-way cycle,
  and `023` (cancellation/modification) adds 13 more foreign keys back into `020`'s booking-lifecycle
  tables. Splitting the revision chain from the booking it revises would create a boundary every
  write transaction has to cross twice.
- **`bookings` is overwhelmingly the dominant root** (in-module in-degree 21, more than double the
  next root); `booking_revisions` is the clear second root (12), and every other cluster —
  cancellation, policy, instruction, relocation — resolves back to one of these two.
- **`BookingActorType` is the named exception to the R2 shared-type rule.** Five modules use it
  (`payment`, `ledger`, `stay`, `review`, and `booking`'s own cancellation slice), which on count
  alone would satisfy R2 ("≥3 modules, no natural owner"). It stays in `booking.types` instead,
  because all five consumers are already downstream of `booking` — direction of dependency decides
  ownership here, not the number of consumers. See [`platform.md`](platform.md#shared-type-rules).

## What it owns

- **`contract` cluster** — `bookings` (root), `booking_items`, `booking_nights`,
  `booking_line_items`, `booking_supply_snapshots`, `booking_party_snapshots`,
  `booking_financial_snapshots`, `booking_state_transitions`, `booking_timeline_entries`,
  `booking_checkouts`, `booking_policy_acceptances`.
- **`revision` cluster** — `booking_revisions` (root), `booking_revision_nights`,
  `booking_modification_proposals`, `booking_modification_deltas`.
- **`policy` cluster** — `cancellation_policy_definitions` (root), `cancellation_policy_versions`,
  `policy_disclosure_versions`, `policy_override_programs`, `policy_override_program_versions`,
  `policy_override_decisions`.
- **`decision` cluster** — `cancellation_decisions` (root), `cancellation_decision_lines`,
  `cancellation_previews`.
- **`instruction` cluster** — `refund_instructions` (root), `adjustment_instructions`,
  `inventory_release_instructions`.
- **`relocation` cluster** — `relocation_cases` (root), `relocation_offers`, `relocation_expenses`.

See [`../data-model/004-booking.md`](../data-model/004-booking.md),
[`../data-model/020-booking-lifecycle.md`](../data-model/020-booking-lifecycle.md),
[`../data-model/023-cancellation-and-modification.md`](../data-model/023-cancellation-and-modification.md),
[`../features/availability-reservation-and-booking.md`](../features/availability-reservation-and-booking.md),
and [`../features/cancellation-modification-and-refund.md`](../features/cancellation-modification-and-refund.md).

## Aggregate clusters inside it

30 tables across 6 clusters — the third-largest module after `support` and `trust`; `contract` and
`revision` are kept as separate clusters despite the tight FK cycle between their roots, because a
booking and its revision history are still two distinct aggregates with different write patterns
(one mutates rarely, the other appends).

## What it does not own

The price a night was quoted at (`pricing`, referenced 14 times by this module's tables), the
physical listing being booked (`supply`), or whether a payment settled (`payment`) — `booking` owns
the contract and its state transitions, and instructs those other modules rather than replicating
their state.

## Public API

`booking.types` — a `@NamedInterface` carrying `BookingActorType` — is public today. No service-level
API exists yet; the eventual candidates are a `BookingLookup`-shaped read interface and a
`BookingConfirmed`-shaped published event, following the module-internals sketch in the migration
plan.

## Allowed dependencies

None with live code today. Schema carries 16 foreign keys into `identity`, 14 into `pricing`
(including the R3 `pricing :: types` consumption), 13 into `supply`, 8 into `inventory`, 5 into
`market`, 1 into `ledger`, 1 into `payment`.

## Data coupling `verify()` cannot see

Inbound: `support` (4), `review` (4), `ledger` (9), `stay` (11), `growth` (7), `payment` (8),
`messaging` (3), `hostops` (1), `inventory` (1). Outbound: `identity` (16), `pricing` (14), `supply`
(13), `inventory` (8), `market` (5), `ledger` (1), `payment` (1). `booking` sits at the center of the
transactional graph — more modules reference it than any module besides `identity` and `market` — so
a new foreign key crossing into or out of `booking` deserves the same scrutiny as a change to
`allowedDependencies`, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether the `relocation` cluster (used when a booking cannot be honored and a guest is moved to a
different property) should become its own module once it grows real workflow code is left open; at
10 tables combined it does not yet meet the bar this migration used to split anything out.

## Exit criteria

- All 30 tables and their entities/repositories live under `dev.ngb.backend.booking.internal.model`
  / `.repository`, in the six clusters above.
- `BookingActorType` lives in `booking.types`, declared with `@NamedInterface("types")`.
- `ApplicationModules.verify()` passes with `booking`'s only declared dependencies being `identity`,
  `pricing` (including `pricing :: types`), `supply`, `inventory`, `market`, `ledger`, and `payment`.
