# Phase 003 — Calendar and pricing

## Goal

Materialize each listing's sellable calendar so availability lookup, locking and nightly pricing remain simple.

## Table

`availability_days` contains one row per listing and local stay date. It stores host availability, the final nightly price, optional minimum-night override and an optimistic-lock version.

## Service rules

- Generate calendar rows for a rolling 12–18 month window.
- A missing row is not bookable; never interpret it as implicitly available.
- `AVAILABLE` means the host permits sale. Active bookings are checked separately and are not copied into this status.
- Update multiple dates in one transaction and use `version` for competing host/calendar-sync writes.
- Future seasonal or dynamic pricing rules should be compiled into this table. A booking snapshots the resulting nightly values.

## Exit criteria

- Host can block/unblock a date range and override price/minimum nights.
- Search can prove that every requested night has an available calendar row.
- Prices are always non-negative minor units in the listing currency.
