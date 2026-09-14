# `inventory`

## Goal

Own what is sellable right now — availability, holds, claims, blocks, and external calendar sync —
as the one module where overselling becomes structurally impossible rather than merely unlikely.

## Forces that shaped it

- **Deliberately not merged with `supply`.** `supply` answers "what exists"; `inventory` answers
  "what's sellable," and that distinction is load-bearing: this is the module whose GiST exclusion
  constraint enforces non-overlapping availability, a guarantee that has no meaning inside a catalog
  module. The `018`↔`020` (booking) cycle is only 1 foreign key each direction — nowhere near the
  bidirectional density that forced `identity`'s or `booking`'s internal merges — so there was no
  structural reason to fold `inventory` into either neighbor.
- **`inventory_resources` is the clear aggregate root** (in-module in-degree 6); holds, claims, and
  blocks are all resolved against a resource, and iCal sync state hangs off the same root through
  `ical_connections`.

## What it owns

- **`resource` cluster** — `inventory_resources` (root), `inventory_holds`, `inventory_claims`,
  `inventory_blocks`.
- **`calendar` cluster** — `availability_days`.
- **`sync` cluster** — `ical_connections`, `ical_sync_runs`, `external_reservations`.

See [`../data-model/003-calendar-pricing.md`](../data-model/003-calendar-pricing.md),
[`../data-model/018-inventory-and-calendar.md`](../data-model/018-inventory-and-calendar.md), and
[`../features/availability-reservation-and-booking.md`](../features/availability-reservation-and-booking.md).

## Aggregate clusters inside it

8 tables across 3 clusters — the smallest business module in the map besides `market` and
`hostverification`; no further subdivision is expected as it grows, because a second sync provider
or a new hold type adds rows to existing tables rather than new aggregate families.

## What it does not own

The listing or property a resource belongs to (`supply`) or the booking that consumes a hold
(`booking`) — it owns only whether a unit-night is available, held, claimed, or blocked, and the
external system state that can override that.

## Public API

No live service exists yet. The eventual API is the availability check and hold/claim lifecycle every
downstream module (chiefly `booking`) will call rather than reading `inventory`'s tables directly.

## Allowed dependencies

None with live code today. Schema carries 4 foreign keys into `supply` and 1 into `booking`.

## Data coupling `verify()` cannot see

Inbound: `booking` (8), `hostops` (2), `stay` (1), `pricing` (1). Outbound: `supply` (4), `booking`
(1) — the `018`↔`020` cycle noted above. None of this is enforced by `ApplicationModules.verify()`;
it is the clearest example in the whole schema of the non-overselling guarantee being a database
constraint that Modulith cannot see and cannot protect on its own.

## What it leaves open

Whether iCal sync failure handling needs its own event published to `messaging` (to notify a host of
a broken calendar connection) is left for when this module gets live code.

## Exit criteria

- All 8 tables and their entities/repositories live under `dev.ngb.backend.inventory.internal.model`
  / `.repository`, in the three clusters above.
- `ApplicationModules.verify()` passes with `inventory`'s only declared dependency being `supply`.
