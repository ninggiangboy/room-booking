# Phase 004 — Booking

## Goal

Create auditable stay reservations and make double booking impossible under concurrent requests.

## Tables

- `bookings`: participants, stay range, lifecycle, totals and listing/policy snapshots.
- `booking_nights`: immutable server-calculated price breakdown per occupied night.

## State flow

```text
PENDING_PAYMENT -> CONFIRMED -> COMPLETED
       |               |
       v               +-> CANCELLED_BY_GUEST / CANCELLED_BY_HOST / NO_SHOW
    EXPIRED
```

## Service rules

- Lock requested `availability_days` rows before calculating and inserting a booking.
- Generate an opaque, user-friendly confirmation code separately from the UUID.
- `PENDING_PAYMENT` must have `payment_expires_at`; an expiration worker moves stale rows to `EXPIRED`.
- Both `PENDING_PAYMENT` and `CONFIRMED` block overlaps through a GiST exclusion constraint.
- Snapshot listing name, address, cover image, check-in/out rules and cancellation policy at booking time.
- Validate that `booking_nights` exactly covers `[check_in, check_out)` and reconciles to booking totals inside the service transaction.

## Exit criteria

- Two concurrent transactions cannot reserve overlapping dates for one listing.
- Adjacent stays are accepted when one's checkout equals the other's check-in.
- Historical totals and listing details remain stable after listing edits.
