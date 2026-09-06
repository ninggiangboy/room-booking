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
- The existing columns can represent immediate publication only as a coarse foundation. The target
  design recommends effective-dated review rights, immutable revisions, deterministic double-blind
  release, qualified publication intervals, and a transparent rebuildable aggregate in
  [`../features/review-reputation-and-aspect-intelligence.md`](../features/review-reputation-and-aspect-intelligence.md).
- Recalculate the future public listing-rating projection and existing host-profile counters after
  qualified publication changes; neither is the source of truth.
- Review eligibility must consume the committed booking stay outcome, not a message read, device
  signal, incident closure, or operations proposal; the target evidence and completion boundary is
  documented in
  [`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).
- Future exact-revision moderation, reports, quarantine/removal/restoration, fake-review and
  coordinated-manipulation detection, protected evidence, appeal, and confirmed fraud labels follow
  [`../features/trust-safety-fraud-and-moderation.md`](../features/trust-safety-fraud-and-moderation.md).
  Original review truth remains owned here; moderation changes visibility additively.
- Future review-related support complaints, evidence selection, remedy coordination, case appeal,
  and damage/claim outcomes follow
  [`../features/disputes-damage-claims-and-support.md`](../features/disputes-damage-claims-and-support.md).
  A support outcome does not silently edit a review or become a fraud/reputation label.

## Exit criteria

- An unrelated user cannot review a listing.
- Duplicate reviews and favorites are rejected by database uniqueness constraints.
- Rating values are restricted to 1–5.
