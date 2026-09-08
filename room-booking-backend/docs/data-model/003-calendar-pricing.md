# Migration 003 — Calendar and pricing foundation

## Goal

Materialize each listing's sellable calendar so availability lookup, locking and nightly pricing remain simple.

## Table

`availability_days` contains one row per listing and local stay date. It stores host availability, the final nightly price, optional minimum-night override and an optimistic-lock version.

## Service rules

- Generate calendar rows for a rolling 12–18 month window.
- A missing row is not bookable; never interpret it as implicitly available.
- `stay_date` is a civil date in the owning listing's IANA timezone, so any query that reads it must
  join `listings` for that zone and must not use `CURRENT_DATE`. See
  [`../features/date-time-and-time-zone-handling.md`](../features/date-time-and-time-zone-handling.md).
- `AVAILABLE` means the host permits sale. Active bookings are checked separately and are not copied into this status.
- Update multiple dates in one transaction and use `version` for competing host/calendar-sync writes.
- Future seasonal or dynamic pricing rules should be compiled into this table. A booking snapshots the resulting nightly values.
- Complete-stay eligibility, restriction precedence, explicit inventory claims, hold expiration,
  multi-unit evolution, and external calendar synchronization follow
  [`../features/availability-reservation-and-booking.md`](../features/availability-reservation-and-booking.md).
- The target design for price rules, quotes, host net projections, promotions, tax, and financial
  allocation is documented in
  [`../features/dynamic-pricing-and-settlement.md`](../features/dynamic-pricing-and-settlement.md).
- Authoritative ledger, realized host payable, payout, statement, and financial reconciliation
  behavior follows
  [`../features/ledger-reconciliation-and-host-payout.md`](../features/ledger-reconciliation-and-host-payout.md).

## Exit criteria

- Host can block/unblock a date range and override price/minimum nights.
- Search can prove that every requested night has an available calendar row.
- Prices are always non-negative minor units in the listing currency.
