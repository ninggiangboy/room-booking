# Migration 002 — Listing-catalog foundation

## Goal

Record the historical listing-centric catalog foundation, images, and searchable amenities. The
target property/accommodation-type/physical-unit model requires forward migration.

## Tables

- `listings`: capacity, stay rules, base price, address and publication lifecycle.
- `listing_images`: ordered media with at most one cover image.
- `amenities`: centrally managed amenity vocabulary.
- `listing_amenities`: searchable many-to-many assignment.

## Service rules

- Only active users with the `HOST` role may publish.
- Validate ISO currency/country codes and an IANA timezone in the application; `IanaTimeZone` and the
  `@IanaZoneId` constraint implement the timezone rule, and `check_in_from`/`check_in_until`/
  `check_out_until` are civil times in that zone. See
  [Date, time, and time-zone handling](../features/date-time-and-time-zone-handling.md).
- A listing may move from `DRAFT` to `PUBLISHED` only when required content, address, price and a cover image exist.
- Store both `storage_key` and delivery URL so a CDN/provider can change later.
- Migration `002` stored numeric coordinates as an early foundation. Migration `010` now provides
  PostGIS-backed radius and map-boundary search; target code must use that spatial authority.
- Future exact-address/instruction release, co-host operational permissions, readiness tasks,
  maintenance, access integration, and stay incidents follow
  [`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).
- Future listing/media provenance, duplication and prohibited-content signals, version-bound
  publication risk decisions, quarantine, moderation, reports, review, and appeal follow
  [`../features/trust-safety-fraud-and-moderation.md`](../features/trust-safety-fraud-and-moderation.md).
  The listing domain retains publication-state authority and enforces an applicable risk decision.
- Future listing-mismatch complaints, exact-revision evidence, damage/property-loss claims,
  support remedies, and protection-provider handoff follow
  [`../features/disputes-damage-claims-and-support.md`](../features/disputes-damage-claims-and-support.md).
  A support case links listing truth and requests an owner command; it never edits publication state.
- Future verified review publication, listing-versus-host attribution, transparent rating aggregates,
  aspect strengths/weaknesses, ownership-transfer lineage, and rebuildable quality profiles follow
  [`../features/review-reputation-and-aspect-intelligence.md`](../features/review-reputation-and-aspect-intelligence.md).
  Listing content remains authoritative; review-derived evidence cannot silently rewrite it.

## Exit criteria

- A host can draft, publish, pause and archive a listing.
- Published listings can be filtered by city, guest capacity and amenities.
- Database constraints prevent invalid capacity, price, night limits and multiple cover images.
