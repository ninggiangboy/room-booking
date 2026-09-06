# Global location and address search

## Purpose

This document defines how the room-booking platform stores listing addresses, maintains a
provider-neutral global destination catalog, and retrieves listings for destination, radius, and
map-viewport searches.

`010-location-search.sql` is the next ordered Liquibase migration. The `010` prefix is migration
history, not a product or data-model phase number.

The database objects introduced for this feature are summarized separately in
[`docs/data-model/010-location-search.md`](../data-model/010-location-search.md).

The design follows four principles:

1. A listing owns its current address snapshot and exact coordinate; a booking captures the
   immutable historical copy it needs.
2. Searchable destinations are shared catalog records, not copies of listing addresses.
3. Spatial candidate retrieval uses PostGIS and does not call an external provider.
4. External dataset identifiers remain provenance, while application UUIDs remain domain
   identity.

## Scope

The current migration provides:

- PostGIS and trigram-search database capabilities;
- a generic geographic-area hierarchy that works across countries;
- localized, alternate, short and historic destination names;
- an optional listing-to-area reference;
- a generated and spatially indexed listing point;
- database indexes for destination, radius and map-viewport candidate retrieval.

This migration does not provide:

- a GeoNames or geoBoundaries importer;
- seeded destination records;
- street-address autocomplete;
- map tiles;
- listing creation or search APIs;
- ranking or personalization;
- overlapping travel-region graphs;
- public approximate-coordinate generation.

Those capabilities can be added independently without changing the identity of existing listings.

## Domain concepts

### Listing address

A listing address describes one bookable property's physical location. It contains host-provided
text and coordinates:

```text
Listing address
├── address_line
├── ward
├── district
├── city
├── country_code
├── postal_code
├── formatted_address
├── latitude
├── longitude
└── location
```

The existing `ward`, `district`, and `city` names are address snapshots, not authoritative global
administrative levels. They remain suitable for display and booking history even when a country's
administrative structure or catalog data changes.

`latitude` and `longitude` are the only writable coordinate pair. PostgreSQL generates
`location geography(Point, 4326)` from them. This prevents numeric and spatial representations
from drifting apart. Application code must update both numeric coordinates in the same request and
must never try to write `location` directly.

### Geographic area

A geographic area is a reusable search destination, such as a country, state, province, city,
neighborhood, or selected landmark:

```text
Geographic area
├── internal UUID
├── parent area
├── country
├── generic type
├── numeric administrative level
├── canonical name
├── localized and alternate names
├── center point
├── optional boundary
└── source provenance
```

It is intentionally not modeled as `province -> district -> ward`. Administrative structures vary
between countries and can change over time. `area_type` expresses product meaning, while
`admin_level` preserves the source hierarchy when one exists.

Supported types are:

| Type | Meaning |
| --- | --- |
| `COUNTRY` | Country-level destination; requires `admin_level = 0` and no parent |
| `ADMIN_AREA` | Official administrative subdivision at levels 1 through 10 |
| `LOCALITY` | City, town, village or another populated place |
| `NEIGHBORHOOD` | Named sub-locality useful to travelers |
| `POINT_OF_INTEREST` | Curated landmark used as a destination, not every external POI |

### Area name

One area can be searched by multiple names without duplicating its identity. For example, a
locality may have a local-language preferred name, an English preferred name, a short name, an
ASCII spelling, and a historical name.

`geo_area_names.language_code` uses a BCP 47 language tag where known and `und` when the language
is undetermined. At most one `PREFERRED` name is allowed per area and language. Name types are:

- `PREFERRED`: display name for a particular language;
- `SHORT`: recognized abbreviated form;
- `ALIAS`: alternate spelling, transliteration or colloquial name;
- `HISTORIC`: former name retained for discovery and migration.

Both primary and alternate names carry an application-normalized form. Normalization is performed
before persistence so queries do not depend on locale-sensitive SQL transformations.

### Listing-to-area relationship

`listings.geo_area_id` points to the most specific catalog area confidently associated with the
listing. Ancestors can be traversed through `geo_areas.parent_id`.

The reference is nullable because catalog coverage is never allowed to block a valid listing. A
host can provide an address and exact map pin even when no catalog area matches. A later import or
backfill job can assign the area.

One parent hierarchy is adequate for the initial implementation. Overlapping concepts such as
metro regions, islands spanning administrative units, and marketing destinations will eventually
require a separate many-to-many area relation rather than overloading `parent_id`.

## Database model

### `geo_areas`

Important invariants are enforced by the database:

- `(source, source_id)` uniquely identifies an imported feature;
- a child and parent must have the same country code;
- country records are roots with administrative level zero;
- administrative areas have a level between one and ten;
- validity end dates cannot precede start dates;
- stored boundary polygons must be valid;
- optimistic-lock versions cannot be negative.

`center` is geography because distance calculations are expressed in meters. `boundary` is geometry
because polygon containment and bounding-box operators work naturally in SRID 4326 geometry.
Polygon inputs must be converted to `MultiPolygon` during import with `ST_Multi`.

### `geo_area_names`

The composite primary key prevents the same normalized name from being stored twice for one area
and language. A partial unique index prevents multiple preferred names in one language. A GIN
trigram index supports prefix-like and typo-tolerant lookup.

### Listing spatial columns

`formatted_address` is separate from catalog names and should contain the host-confirmed display
snapshot. `geo_area_id` is optional. `location` is generated as:

```sql
ST_SetSRID(
    ST_MakePoint(longitude, latitude),
    4326
)::geography
```

Longitude must always be passed before latitude. Existing coordinate constraints continue to
require either both numeric values or neither and constrain them to valid ranges.

The partial GIST index contains only published listings with coordinates. Draft, paused, archived,
and coordinate-less records do not enlarge the public spatial-search index.

## Data-source strategy

The application owns and serves the catalog, but it does not manually author a global gazetteer.
Imports use free, versioned datasets.

### ISO 3166

Use [ISO 3166](https://www.iso.org/iso-3166-country-codes.html) alpha-2 codes as the common
`country_code`. ISO codes identify countries and principal subdivisions but do not provide a full
worldwide locality hierarchy.

### GeoNames

Use [GeoNames data extracts](https://www.geonames.org/export/) as the initial global source for
country, administrative-area, populated-place, and alternate-name records.

The first import should use:

| File | Purpose |
| --- | --- |
| `countryInfo.txt` | Country records and country metadata |
| `admin1CodesASCII.txt` | Primary administrative subdivisions |
| `admin2Codes.txt` | Secondary administrative subdivisions where available |
| `cities500.zip` | Searchable populated places without importing every feature worldwide |
| `alternateNamesV2.zip` | Selected localized names, aliases, and historical names |

Do not start with `allCountries.zip`. It contains millions of features that are irrelevant to room
search and increases import, index, and review costs. Expand coverage only when search analytics
show that the smaller catalog is insufficient.

Map GeoNames records as follows:

```text
GeoNames feature class A + ADM* → ADMIN_AREA
GeoNames feature class P        → LOCALITY
GeoNames country record         → COUNTRY
GeoNames geonameid              → source_id
source                           → GEONAMES
```

Feature codes and country-specific hierarchy rules must be retained in staging data even if they
are mapped to generic serving types.

### geoBoundaries

Use [geoBoundaries](https://www.geoboundaries.org/api.html) only when exact administrative
containment or boundary rendering is required. Import one country and administrative level at a
time. Radius and map-viewport search require listing points, not worldwide administrative
polygons.

GeoNames and geoBoundaries identifiers are not assumed to match. A later crosswalk process should
match by country, administrative level, normalized name, parent, and spatial proximity, then queue
ambiguous matches for review.

### Curated destinations

Travel regions frequently differ from official administrative areas. Curated records use a
`CUSTOM` source and stable application-controlled source IDs. Examples include a metro area, coast,
ski region, or island group. Curated records must not silently replace official records; aliases or
future explicit relations connect them.

### Licensing and attribution

Dataset license and attribution requirements are operational metadata even though they are not
needed in every serving query. The importer must record, at minimum:

- source name and download URI;
- pinned release or snapshot date;
- file checksum;
- license identifier and attribution text;
- import start/end time and outcome;
- inserted, updated, deactivated, and rejected counts.

The schema currently stores row-level `source` and `source_version`. A later importer migration may
add an `geo_import_runs` audit table when the importer is implemented.

## Import pipeline

Imports never run during application startup. They are explicit offline jobs:

```text
Pinned source file
        ↓
Checksum verification
        ↓
Source-specific staging tables
        ↓
Schema and coordinate validation
        ↓
Type/name normalization
        ↓
Parent resolution
        ↓
Transactional upsert
        ↓
Deactivate missing records
        ↓
Metrics and rejected-row report
```

The importer must:

1. Download or accept an explicitly pinned source artifact.
2. Reject a checksum mismatch before touching serving tables.
3. Load raw fields into staging without losing source identifiers.
4. Reject invalid country codes, coordinate ranges, parent references, and geometries.
5. Normalize searchable names deterministically.
6. Resolve parents before children.
7. Upsert by `(source, source_id)` without changing internal UUIDs.
8. Mark disappeared records inactive rather than deleting them.
9. Preserve old names as `HISTORIC` when an area is renamed.
10. Emit counts and samples for human review before production promotion.

Imported catalog changes must not rewrite listing address snapshots or booking snapshots.

## Search behavior

### Destination autocomplete

The autocomplete endpoint searches canonical and alternate normalized names, filters inactive
records, applies country or viewport context when available, and groups duplicate name matches by
area identity.

Conceptual request:

```http
GET /api/v1/locations/suggest?q=munich&country=DE&limit=10
```

Conceptual response:

```json
[
  {
    "id": "area-uuid",
    "name": "München",
    "displayName": "München, Bayern, Deutschland",
    "type": "LOCALITY",
    "countryCode": "DE",
    "center": {
      "latitude": 48.137,
      "longitude": 11.575
    }
  }
]
```

The endpoint should require at least two normalized characters, cap result count, debounce clients,
and rank exact/prefix matches before fuzzy matches. Popularity may break ties later but must not
replace textual relevance.

### Search by selected area

A client submits the selected internal area ID, not the raw text it typed:

```http
GET /api/v1/listings/search?destinationId=...&checkIn=...&checkOut=...&guests=2
```

Candidate retrieval can use:

- direct `geo_area_id` equality for a locality;
- recursive descendants for an administrative parent;
- boundary containment when a verified polygon exists;
- center and a product-defined radius when no boundary exists.

Fallback behavior must be visible in logs and metrics because a center-radius approximation can
include listings outside an irregular area or exclude listings near its edges.

### Radius search

Distance uses geography and therefore meters:

```sql
SELECT l.*,
       ST_Distance(
           l.location,
           ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
       ) AS distance_meters
FROM listings l
WHERE l.status = 'PUBLISHED'
  AND l.location IS NOT NULL
  AND ST_DWithin(
      l.location,
      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
      :radius_meters
  );
```

The API must validate coordinate ranges, require a positive radius, and enforce a maximum radius
to protect latency and result quality.

### Map-viewport search

The client submits west, south, east, and north bounds after panning or zooming. Candidate retrieval
uses a geometry envelope:

```sql
SELECT l.*
FROM listings l
WHERE l.status = 'PUBLISHED'
  AND l.location IS NOT NULL
  AND l.location::geometry && ST_MakeEnvelope(
      :west,
      :south,
      :east,
      :north,
      4326
  );
```

The API must validate latitude order, longitude ranges, maximum viewport area, and antimeridian
crossing. An antimeridian-crossing viewport is represented as two envelopes rather than an invalid
single envelope.

### Availability and ranking

Spatial filtering only creates candidates. The booking search must still validate:

- publication state;
- guest capacity;
- every requested availability day;
- minimum and maximum stays;
- price and currency rules;
- selected amenities.

Initial ranking can combine textual match, distance, availability, price, rating, and stable ID as
a deterministic final tie-breaker. List and map presentations may eventually use different ranking
strategies, but both operate on valid candidates from the same authoritative inventory rules.

The evolution from this deterministic baseline to review-informed and guest-personalized ranking is
defined in [`personalized-discovery.md`](personalized-discovery.md). Geographic retrieval remains a
candidate-generation stage; personalization cannot override inventory or explicit search filters.

## Address capture

The listing workflow should be provider-neutral:

1. Host enters structured address text.
2. The UI may offer autocomplete if a compliant provider is configured.
3. The UI places a marker using the result or the host's current map view.
4. Host confirms or drags the marker to the actual property.
5. Backend validates the numeric coordinates and stores the address snapshot atomically.
6. PostgreSQL generates and indexes `location`.
7. A background process may associate the most specific `geo_area_id`.

Provider result IDs may be stored as source metadata in a future address model, but they must not
become listing IDs or required search keys. Provider storage and attribution terms must be checked
before persisting provider-returned address components or coordinates.

## Privacy and authorization

The database retains the exact address and point required for booking operations, support, and
fraud checks. Response shape—not database precision—controls disclosure.

| Caller/context | Address and point exposure |
| --- | --- |
| Public search | Area name and stable approximate point only |
| Listing owner | Exact stored address and point |
| Authorized operator | Exact data when required by role and workflow |
| Guest before confirmation | No house/unit number; approximate point by default |
| Eligible confirmed guest | Exact address according to booking policy |

Approximation must be deterministic per listing and disclosure policy. Generating a new random
offset on every request is unsafe because repeated samples can be averaged to estimate the exact
point. Exact address fields must also be omitted from logs, analytics events, cache keys, and public
search documents.

Booking creation stores the required address representation inside the existing immutable listing
snapshot so later host edits do not alter historical reservation details.

## Operations and deployment

- PostgreSQL must expose PostGIS and `pg_trgm` before Liquibase applies
  `010-location-search.sql`.
- Local development uses `postgis/postgis:17-3.6-alpine`.
- The upstream image is currently amd64-only, so Compose selects `linux/amd64`; Docker Desktop
  uses emulation on Apple Silicon.
- Managed PostgreSQL may require an administrator to install or allow extensions before the
  application role runs migrations.
- Dataset import workers require separate resource and timeout limits from interactive APIs.
- GIST and GIN index growth, query latency, no-result rate, fallback rate, and unmatched listing
  rate should be monitored.

Changing the Compose image does not erase the existing named PostgreSQL volume. On the first
application startup after the change, Liquibase creates the required extensions and applies the
forward migration. Production backups and an upgrade rehearsal remain required before rollout.

## Rollout plan

1. Deploy the PostGIS-capable database image or enable extensions in the managed database.
2. Apply `010-location-search.sql`; existing numeric coordinates generate spatial points
   automatically.
3. Implement and review a version-pinned GeoNames importer.
4. Import countries, administrative levels 1 and 2, `cities500`, and selected alternate names.
5. Implement destination autocomplete and internal ID selection.
6. Implement listing pin confirmation and spatial candidate queries.
7. Backfill `geo_area_id` where the catalog match is unambiguous.
8. Add geoBoundaries country by country only when containment is required.
9. Add stable approximate public coordinates before exposing map search.
10. Evaluate richer POIs, travel regions, and ranking only after observing real search behavior.

## Verification checklist

- Apply all migrations to an empty PostGIS database through Liquibase.
- Upgrade a database containing listings with null and non-null coordinate pairs.
- Verify `location` is generated and cannot be written directly.
- Verify longitude/latitude order with known coordinates.
- Verify parent areas cannot cross country codes.
- Verify invalid boundaries and date ranges are rejected.
- Verify one preferred name per area and language.
- Inspect query plans for radius, viewport, and trigram searches.
- Test antimeridian-crossing and very large viewport requests.
- Confirm public serialization never includes exact address fields.
- Verify source version, checksum, attribution, and rejected-row reports for every import.
