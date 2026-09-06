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
- The target state decomposition, explicit hold/claim model, idempotency, payment-expiry races,
  modifications, and multi-unit evolution follow
  [`../features/availability-reservation-and-booking.md`](../features/availability-reservation-and-booking.md).
- Provider-independent collection obligations, authorization/capture, customer action, late-success
  compensation, and verified outcome recovery follow
  [`../features/payment-orchestration.md`](../features/payment-orchestration.md).
- The current totals are summary foundations. Future line-item ownership, tax provenance, host
  entitlement, and immutable financial allocation follow
  [`../features/dynamic-pricing-and-settlement.md`](../features/dynamic-pricing-and-settlement.md).
- The target balanced journal, host-payable release, payout, statement, and financial reconciliation
  boundary follows
  [`../features/ledger-reconciliation-and-host-payout.md`](../features/ledger-reconciliation-and-host-payout.md).
- Executable accepted policy versions, immutable cancellation decisions, booking revisions,
  modification delta holds, host cancellation/relocation, and exact release instructions follow
  [`../features/cancellation-modification-and-refund.md`](../features/cancellation-modification-and-refund.md).
- Booking-scoped conversations, fact-derived notifications, controlled arrival instructions/access,
  readiness and check-in/out evidence, incidents, and the booking-owned stay-outcome boundary follow
  [`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).

## Exit criteria

- Two concurrent transactions cannot reserve overlapping dates for one listing.
- Adjacent stays are accepted when one's checkout equals the other's check-in.
- Historical totals and listing details remain stable after listing edits.
