# Phase 006 — Trust and engagement

## Goal

Add post-stay reviews and guest favorites after the booking core is stable.

## Tables

- `reviews`: guest-to-listing or host-to-guest feedback tied to a real booking.
- `favorites`: idempotent user-to-listing saves.

## Service rules

- Accept reviews only for `COMPLETED` bookings and verified participants.
- Permit at most one review of each type for a booking.
- Optional category scores apply only to guest-to-listing reviews.
- Publishing can be immediate for MVP. A later migration can add Airbnb-style double-blind deadlines.
- Recalculate listing and host rating counters after a published review; counters are not the source of truth.
- Review eligibility must consume the committed booking stay outcome, not a message read, device
  signal, incident closure, or operations proposal; the target evidence and completion boundary is
  documented in
  [`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).

## Exit criteria

- An unrelated user cannot review a listing.
- Duplicate reviews and favorites are rejected by database uniqueness constraints.
- Rating values are restricted to 1–5.
