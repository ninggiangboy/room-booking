# Migration 017 — Reattaching the geographic catalog to supply

## Goal

Connect the destination catalog to the new supply model, and make a property's position explainable.

## What was kept

`geo_areas` and `geo_area_names` from migration `010` are **unchanged**. The model is already right:
`area_type` expresses product meaning while `admin_level` preserves whatever hierarchy the source
country happens to have — which is the correct shape precisely because administrative structures
differ between countries and change over time.

What did not survive `016` was the *link*. The old `listings.geo_area_id` column went with the old
`listings` table, because a listing is no longer the thing that sits somewhere — a property is.

## Tables and columns

- `properties` gains `address_line`, `ward`, `district`, `city`, `postal_code`, `geo_area_id`.
- `property_area_assignments`: every area a property should appear under, with how that was decided.
- `points_of_interest`: landmarks a guest measures *distance to*.
- `property_poi_distances`: precomputed straight-line and travel distances.
- `geocoding_results`: every attempt to resolve an address, not just the winning one.
- `geo_area_search_profiles`: derived popularity, kept beside the catalog rather than inside it.

## Design rules

- **Address parts are display snapshots, not administrative authority.** They are deliberately not
  foreign keys: a booking made in 2026 should still render the address the guest saw, even after a
  province is renamed or a district is merged.
- **A primary area and an assignment set answer different questions.** `geo_area_id` says which
  destination the property is filed under; `property_area_assignments` says which destinations it
  should *appear* under, which is what search asks. A property is simultaneously in a country, a
  province, a city, and a neighbourhood.
- **Assignments record how they were derived.** A `BOUNDARY` assignment can be recomputed on the next
  catalog import; a `HOST_DECLARED` or `OPERATOR` one must survive that import. Without
  `assignment_source` there is no way to tell them apart, and a reimport silently discards human
  corrections.
- **Points of interest are not search destinations.** A `geo_areas` row of type `POINT_OF_INTEREST`
  is a curated place a guest *searches for*; a `points_of_interest` row is a landmark they *measure
  distance to* ("1.2 km from Ben Thanh Market"). The two have different curation standards, and
  conflating them makes every measured landmark also a searchable destination.
- **Distances are precomputed.** A listing page shows several, and computing them per request across
  every search candidate is the difference between a fast page and a slow one. `computed_at` and
  `catalog_version` are what make a stale distance detectable after either endpoint moves.
- **Travel time and travel mode travel together.** "18 minutes" means nothing without saying by what.
- **Every geocoding attempt is recorded, not just the winner.** When a property's position is wrong a
  guest is sent to the wrong place, and diagnosing that needs the provider, its confidence, and what
  the host had typed at the time. `uk_geocoding_results_one_applied` marks the attempt the
  coordinates actually came from, and `ck_geocoding_results_applied` refuses to let a failed attempt
  be the applied one.
- **Derived popularity is kept outside the catalog**, so reimporting the geographic catalog does not
  discard what the platform has learned about which destinations people actually want. Suggesting a
  destination with nothing bookable in it wastes the guest's only query, which is why the suggestion
  index requires a non-zero property count.

## A Liquibase trap this migration hit

A comment line beginning `-- property ...` is parsed as a `--property name= value=` directive and
fails the whole changelog. The rule is now recorded in
[`../conventions/03-entities-and-persistence.md`](../conventions/03-entities-and-persistence.md)
alongside the `splitStatements:false` and partial-index rules that bit earlier migrations.

## Exit criteria

- A property resolves to a primary destination and appears under every enclosing area.
- Reimporting the geographic catalog preserves operator and host corrections.
- A wrong position can be traced to the provider and confidence that produced it.
- Destination autocomplete never suggests an area with nothing bookable in it.
