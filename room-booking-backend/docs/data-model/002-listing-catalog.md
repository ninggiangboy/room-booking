# Phase 002 — Listing catalog

## Goal

Allow a host to manage one independently bookable accommodation, its images, and searchable amenities.

## Tables

- `listings`: capacity, stay rules, base price, address and publication lifecycle.
- `listing_images`: ordered media with at most one cover image.
- `amenities`: centrally managed amenity vocabulary.
- `listing_amenities`: searchable many-to-many assignment.

## Service rules

- Only active users with the `HOST` role may publish.
- Validate ISO currency/country codes and an IANA timezone in the application.
- A listing may move from `DRAFT` to `PUBLISHED` only when required content, address, price and a cover image exist.
- Store both `storage_key` and delivery URL so a CDN/provider can change later.
- Coordinates are sufficient for MVP. Introduce PostGIS in a new migration when radius or map-boundary search is needed.

## Exit criteria

- A host can draft, publish, pause and archive a listing.
- Published listings can be filtered by city, guest capacity and amenities.
- Database constraints prevent invalid capacity, price, night limits and multiple cover images.
