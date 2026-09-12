# Migration 018 — Inventory, holds, claims, and calendar sync

## Goal

Make overselling impossible, in the database, under concurrency.

This is the migration the rest of the booking path depends on. Application logic cannot prevent
overselling on its own, because it cannot see the transaction committing alongside it.

## Two defences, because there are two problems

A **`UNIQUE_RENTAL`** is one indivisible place. Two stays overlapping on it is always wrong, whatever
the quantities say. Defended by a GiST exclusion constraint over `(inventory_resource_id,
stay_range)` where the claim is `ACTIVE` — PostgreSQL refuses the second overlapping claim outright,
including across concurrent transactions.

A **`QUANTITY_POOL`** is a count of interchangeable rooms. Overlap is *normal*; what is wrong is the
count exceeding capacity. Defended by `ck_availability_days_capacity`:
`held + booked + blocked <= sellable`, per date. Callers lock the date rows in a stable order, then
write; the write itself fails if it would oversell.

An exclusion constraint cannot express the pooled rule and a counter cannot express the unique rule.
That is why both exist rather than one general mechanism.

### Verified behaviour

| Scenario | Expected | Result |
|---|---|---|
| Villa, two overlapping stays | refused | exclusion constraint rejects |
| Villa, adjacent stays (checkout = next check-in) | accepted | accepted |
| Villa, cancelled stay's nights resold | accepted | accepted |
| Villa, **two concurrent transactions** racing | exactly one wins | B blocked on A, then refused on A's commit |
| Pool of 3, three overlapping stays | accepted | accepted |
| Pool of 3, selling a 4th on one date | refused | capacity check rejects |

## Tables

- `inventory_resources`: the stable calendar identity — `SINGLE_UNIT`, `PHYSICAL_UNIT`, or
  `QUANTITY_POOL`.
- `availability_days`: per-date sellability, quantities, price input, and stay restrictions.
- `inventory_claims`: **the single thing that consumes inventory.**
- `inventory_holds`: temporary reservations while a guest pays.
- `inventory_blocks`: why nights are unavailable when nobody has booked them.
- `ical_connections`, `ical_sync_runs`, `external_reservations`: external calendar exchange.

## Design rules

- **One claim table for everything.** A hold, a booking, a host block, and an imported external
  reservation all become rows in `inventory_claims`. That is what stops a host blocking a night a
  guest is simultaneously holding, or an iCal import quietly overwriting either.
- **`resource_type` is denormalized onto the claim, under a composite foreign key.** PostgreSQL
  forbids a subquery in an index predicate, so the exclusion constraint cannot look the resource up —
  it has to know locally which defence applies. The composite key
  `fk_inventory_claims_resource_type` is what stops the copy ever disagreeing with the resource it
  names.
- **Ranges are half-open by construction.** `ck_inventory_claims_range` refuses an empty, inverted,
  or closed-upper range, so a checkout date is always the next guest's available check-in date.
- **Released and expired claims are outside the exclusion predicate**, so a cancelled stay frees its
  nights immediately rather than needing a cleanup job to run first.
- **A hold has a ceiling.** `max_expires_at` bounds how far extensions can push `expires_at`, so a
  stalled checkout cannot keep inventory off the market indefinitely by repeatedly asking for more
  time.
- **Fencing tokens exist because expiry is a race.** A sweeper deciding a hold has lapsed and a
  checkout completing payment can act at the same instant; the token lets the later writer detect it
  is acting on a superseded decision.
- **No `now()` in any expiry index predicate.** Time passing does not change an index entry. Sweeps
  index the timestamp column plainly and bind their own decision instant, per
  [`../conventions/04-time-and-clock.md`](../conventions/04-time-and-clock.md).
- **A price without its currency is refused.** A stored price that lost its currency is how a guest
  gets charged the right number of the wrong money.
- **An import must never delete a host-owned block.** `uk_inventory_blocks_external` is scoped by
  `source_type`, so re-importing a calendar updates the blocks *that import* created and leaves a
  host's hand-made block for the same dates untouched.
- **Calendar conflicts are recorded, not auto-resolved.** When an external calendar claims nights
  this platform has already sold, either resolution — cancelling our booking or ignoring theirs —
  strands a real guest. `external_reservations.conflict_state` makes it visible for a person to
  decide.
- **An export feed URL is a secret.** It discloses a host's whole occupancy pattern, so only a digest
  of the token is stored.

## Deviation from the feature document

`../features/availability-reservation-and-booking.md` keys `inventory_resources` on `listing_id`,
which was correct when a listing was the sellable thing. Migration `016` moved that authority to
`accommodation_type`, so inventory hangs off accommodation types here. A listing presents inventory
and can never promise a night.

The document also proposes a domain-local `idempotency_records`. This reuses
`command_idempotency_records` from migration `012` instead: booking's retention, uniqueness, and
result shape are not stronger than the shared primitive, which is the platform design's stated test
for when a domain-specific table is warranted.

## Exit criteria

- Two concurrent transactions cannot both claim the same unique-rental night.
- A pooled type cannot be sold beyond its per-date capacity.
- Adjacent stays are accepted; a cancelled stay's nights are immediately resellable.
- An expired hold releases inventory without a booking being lost to a race.
- Re-importing an external calendar never destroys a host's own block.
