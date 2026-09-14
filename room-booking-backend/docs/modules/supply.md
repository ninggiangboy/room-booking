# `supply`

## Goal

Own what physically or legally exists to be booked — property, accommodation type, physical unit,
listing, rate plan — and the geographic catalog that locates it, as one module answering "what is
there," distinct from `inventory`'s "what is sellable right now."

## Forces that shaped it

- **Four migrations merge because `017` exists only to connect the other three.** `017`
  (geographic catalog) contributes 3 foreign keys into `016` (supply catalog) and 3 into `010`
  (location search); `016` contributes 2 into `010`. A standalone `geo` module built from `017`
  alone would have exactly two consumers — `supply` and `discovery` — which is not enough
  justification for a 22nd module; it is a cluster inside `supply` instead.
- **`geo_areas` and `properties` are twin aggregate roots** (in-module in-degree 7 each), not one
  dominant root the way `bookings` or `support_cases` are elsewhere — the geographic catalog and the
  property catalog are genuinely two different aggregate families sharing one module.
- **Not merged with `inventory`.** Supply is "what exists"; inventory is "what's sellable," and the
  distinction matters enough that it stays a separate module — see [`inventory.md`](inventory.md).

## What it owns

- **`property` cluster** — `properties` (aggregate root), `physical_units`,
  `property_area_assignments`, `property_collaborators`, `property_house_rules`,
  `property_safety_items`, `property_poi_distances`.
- **`accommodation` cluster** — `accommodation_types` (root), `accommodation_type_amenities`.
- **`listing` cluster** — `listings` (root), `listing_contents`, `listing_media`,
  `listing_change_history`, `rate_plans`.
- **`amenity` cluster** — `amenity_definitions` (root), `amenity_translations`,
  `amenity_vocabularies`, `accessibility_claims`.
- **`geo` cluster** — `geo_areas` (root), `geo_area_names`, `geo_area_search_profiles`,
  `geocoding_results`, `points_of_interest`.

See [`../data-model/002-listing-catalog.md`](../data-model/002-listing-catalog.md),
[`../data-model/010-location-search.md`](../data-model/010-location-search.md),
[`../data-model/016-supply-catalog.md`](../data-model/016-supply-catalog.md),
[`../data-model/017-geographic-catalog.md`](../data-model/017-geographic-catalog.md), and
[`../features/location-search.md`](../features/location-search.md).

## Aggregate clusters inside it

23 tables across 5 clusters, two of them (`property`, `listing`) large enough that a contributor
adding a new supply-side table should default to placing it in whichever cluster's root it carries
a foreign key to, not by name prefix — `property_*` and `listing_*` prefixes exist in both clusters
and neither prefix alone tells you which cluster a new table belongs in.

## What it does not own

Availability, holds, or sync state for a listing — that is `inventory`'s aggregate, referenced back
here by foreign key. It does not own price or tax rules either (`pricing`), only the physical/
catalog facts a price rule or a booking is written against.

## Public API

No live service exists for this module yet. Its eventual API is the lookup surface other modules
already reach for by foreign key today: resolve a property, an accommodation type, a listing, or a
geo area by ID. Until that service exists, this module has no `internal/` visibility distinction to
enforce beyond what `ApplicationModules.verify()` checks by default.

## Allowed dependencies

None with live code today. Schema carries 4 foreign keys into `identity` and 1 into `market`.

## Data coupling `verify()` cannot see

Inbound: `booking` (13), `stay` (13), `pricing` (10), `discovery` (9), `hostops` (6), `review` (7),
`growth` (5), `inventory` (4), `support` (3), `messaging` (1), `ledger` (1). Outbound: `identity` (4),
`market` (1). This module sits near the center of the marketplace's data graph — almost every
downstream domain references a property, listing, or geo area — which is exactly why its module
boundary being *derived* rather than guessed (Fact 2 of the migration) matters: a wrong cluster
assignment here would be felt widely.

## What it leaves open

Whether `geo` outgrows its status as a `supply` cluster into its own module is explicitly left open;
today it has exactly the two consumers (`supply` itself, `discovery`) that keep it a cluster rather
than a module, per the migration plan's non-merge criteria in
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md).

## Exit criteria

- All 23 tables and their entities/repositories live under `dev.ngb.backend.supply.internal.model`
  / `.repository`, in the five clusters above.
- `ApplicationModules.verify()` passes with `supply`'s only declared dependencies being `identity`
  and `market`.
