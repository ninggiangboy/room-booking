# Migration 010 — Location-search schema

## Purpose

Migration `010-location-search.sql` adds the persistence and indexing required by the global
location-search feature. The numeric prefix is the next Liquibase migration number; it does not
introduce a new product or data-model roadmap phase.

The complete product behavior, source strategy, import pipeline, API concepts, privacy rules, and
rollout plan live in [`docs/features/location-search.md`](../features/location-search.md).

## Database capabilities

The migration enables:

- PostGIS for spatial types, indexes, distance, containment, and viewport queries;
- `pg_trgm` for typo-tolerant normalized destination-name search.

Both extensions must be available to the migration role. Local development uses a PostGIS-enabled
PostgreSQL image; a managed database may require an administrator to enable the extensions first.

## `geo_areas`

`geo_areas` is a provider-neutral catalog of searchable destinations. It stores:

- an application-owned UUID;
- a same-country parent relationship;
- ISO 3166 alpha-2 country code;
- generic area type and optional administrative level;
- canonical and normalized names;
- optional center geography point;
- optional valid multipolygon boundary;
- external source, source identifier, and source version;
- lifecycle dates, active state, timestamps, and optimistic-lock version.

The table supports countries, administrative areas, localities, neighborhoods, and selected points
of interest without assuming that every country follows a province/district/ward hierarchy.

Key constraints guarantee:

- uniqueness of `(source, source_id)`;
- parent and child country consistency;
- country roots at administrative level zero;
- administrative levels between one and ten;
- valid date ranges and boundary geometries;
- non-negative optimistic-lock versions.

Indexes support parent traversal, country/type browsing, trigram name lookup, center-distance
queries, and polygon containment.

## `geo_area_names`

`geo_area_names` stores localized and alternate names independently of area identity. Supported
name types are preferred, short, alias, and historic.

The composite primary key prevents duplicate normalized names for one language and area. A partial
unique index allows at most one preferred name per language. A GIN trigram index supports
autocomplete and fuzzy lookup.

## Listing extensions

The migration adds three columns to `listings`:

| Column | Purpose |
| --- | --- |
| `geo_area_id` | Optional most-specific catalog area confidently assigned to the listing |
| `formatted_address` | Host-confirmed address snapshot for display and booking history |
| `location` | Stored generated `geography(Point, 4326)` used by spatial search |

`location` is generated from the existing numeric coordinate columns:

```sql
ST_SetSRID(
    ST_MakePoint(longitude, latitude),
    4326
)::geography
```

Latitude and longitude remain the single writable representation. Application code must update
them together and must not write the generated column. The existing coordinate constraint keeps
the pair complete and in range.

The listing indexes contain only published rows relevant to public search:

- a B-tree index on catalog area and guest capacity;
- a GIST index on non-null generated locations.

## Data ownership

The catalog and listing address serve different purposes:

- `geo_areas` is mutable imported reference data used to resolve destinations;
- listing address columns are host-confirmed snapshots;
- booking `listing_snapshot` remains the immutable historical representation.

Catalog imports must not rewrite listing or booking address snapshots. `listings.geo_area_id` is
nullable so incomplete catalog coverage cannot prevent a valid property from being represented.

## Migration behavior

Existing listing coordinates automatically populate the generated geography column when the
migration is applied. No third-party geographic records are seeded. A separate importer must load
version-pinned and validated datasets after deployment.

Rollback removes the listing indexes and columns, alternate-name and area tables, then the newly
introduced extensions in reverse changeset order.
