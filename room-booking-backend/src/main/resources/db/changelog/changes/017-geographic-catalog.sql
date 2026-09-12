--liquibase formatted sql

-- Migration 010 built geo_areas and geo_area_names as a reusable destination catalog, and that model
-- survives unchanged: area_type expresses product meaning while admin_level preserves whatever
-- hierarchy the source country happens to have, which is the right shape precisely because
-- administrative structures differ between countries and change over time.
--
-- What did not survive is the link from supply to that catalog. Migration 016 retired the old
-- listings table and with it the listings.geo_area_id column, because a listing is no longer the
-- thing that sits somewhere -- a property is. This migration reattaches the catalog to properties and
-- adds the two things D04 needs beyond a destination list: curated points of interest that a guest
-- measures distance to, and honest provenance for how a property's coordinates were arrived at.
--
-- Address parts are stored on the property as display snapshots, not as authoritative administrative
-- levels. A booking made in 2026 should still render the address the guest saw even after a province
-- is renamed or a district is merged, so these columns are deliberately not foreign keys.

--changeset ninggiangboy:017-01-property-address-snapshot
ALTER TABLE properties
    ADD COLUMN address_line VARCHAR(255),
    ADD COLUMN ward VARCHAR(120),
    ADD COLUMN district VARCHAR(120),
    ADD COLUMN city VARCHAR(120),
    ADD COLUMN postal_code VARCHAR(24),
    ADD COLUMN geo_area_id UUID,
    ADD CONSTRAINT fk_properties_geo_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id);

CREATE INDEX idx_properties_geo_area
    ON properties (geo_area_id)
    WHERE lifecycle_state = 'ACTIVE';
--rollback DROP INDEX idx_properties_geo_area;
--rollback ALTER TABLE properties DROP CONSTRAINT fk_properties_geo_area;
--rollback ALTER TABLE properties DROP COLUMN geo_area_id;
--rollback ALTER TABLE properties DROP COLUMN postal_code;
--rollback ALTER TABLE properties DROP COLUMN city;
--rollback ALTER TABLE properties DROP COLUMN district;
--rollback ALTER TABLE properties DROP COLUMN ward;
--rollback ALTER TABLE properties DROP COLUMN address_line;

--changeset ninggiangboy:017-02-property-area-assignments
-- A property sits in several areas at once: a country, a province, a city, and a neighbourhood. The
-- primary geo_area_id above answers "which destination is this filed under"; this table answers
-- "which destinations should this property appear under", which is the question search asks.
--
-- Assignments are derived rather than declared, so each one records how it was decided. A
-- boundary-based assignment can be recomputed when the catalog is reimported; a host-declared or
-- operator-corrected one must survive that reimport, and without the source recorded there is no way
-- to tell them apart.
CREATE TABLE property_area_assignments (
    property_id         UUID NOT NULL,
    geo_area_id         UUID NOT NULL,
    assignment_source   VARCHAR(24) NOT NULL,
    is_primary          BOOLEAN NOT NULL DEFAULT false,
    catalog_version     VARCHAR(64),
    assigned_at         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_property_area_assignments PRIMARY KEY (property_id, geo_area_id),
    CONSTRAINT fk_property_area_assignments_property FOREIGN KEY (property_id)
        REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_property_area_assignments_area FOREIGN KEY (geo_area_id)
        REFERENCES geo_areas (id),
    CONSTRAINT ck_property_area_assignments_source CHECK (
        assignment_source IN ('BOUNDARY', 'PROXIMITY', 'HOST_DECLARED', 'OPERATOR', 'IMPORT')
    )
);

CREATE UNIQUE INDEX uk_property_area_assignments_one_primary
    ON property_area_assignments (property_id)
    WHERE is_primary = true;

-- Search retrieves by area, so this is the direction that carries the traffic.
CREATE INDEX idx_property_area_assignments_area
    ON property_area_assignments (geo_area_id, property_id);
--rollback DROP TABLE property_area_assignments;

--changeset ninggiangboy:017-03-points-of-interest
-- Distinct from geo_areas of type POINT_OF_INTEREST, which are curated *destinations* a guest
-- searches for. These are landmarks a guest measures *distance to* -- an airport, a beach, a station
-- -- and which appear on a listing page as "1.2 km from Ben Thanh Market". The two have different
-- lifecycles and different curation standards, and conflating them makes every measured landmark
-- also a searchable destination.
CREATE TABLE points_of_interest (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geo_area_id         UUID,
    poi_key             VARCHAR(96) NOT NULL,
    poi_type            VARCHAR(32) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    normalized_name     VARCHAR(200) NOT NULL,
    country_code        VARCHAR(2) NOT NULL,
    latitude            NUMERIC(9,6) NOT NULL,
    longitude           NUMERIC(9,6) NOT NULL,
    -- The numeric pair is the only writable coordinate; PostgreSQL derives the spatial value from
    -- it. This is the same rule properties follows, and it exists so the numeric and spatial
    -- representations cannot drift apart -- application code has no way to write one without the
    -- other. See docs/features/location-search.md.
    position            geography(Point, 4326)
        GENERATED ALWAYS AS (
            ST_SetSRID(
                ST_MakePoint(longitude::DOUBLE PRECISION, latitude::DOUBLE PRECISION),
                4326
            )::geography
        ) STORED,
    importance_rank     SMALLINT NOT NULL DEFAULT 0,
    source              VARCHAR(32) NOT NULL,
    source_id           VARCHAR(128),
    source_version      VARCHAR(64),
    active              BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_points_of_interest_key UNIQUE (poi_key),
    CONSTRAINT fk_points_of_interest_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id),
    CONSTRAINT ck_points_of_interest_type CHECK (
        poi_type IN ('AIRPORT', 'TRAIN_STATION', 'BUS_STATION', 'METRO_STATION', 'PORT',
                     'BEACH', 'LANDMARK', 'MUSEUM', 'PARK', 'SHOPPING', 'HOSPITAL',
                     'UNIVERSITY', 'CONVENTION_CENTRE', 'OTHER')
    ),
    CONSTRAINT ck_points_of_interest_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_points_of_interest_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_points_of_interest_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_points_of_interest_importance CHECK (importance_rank >= 0),
    CONSTRAINT ck_points_of_interest_version CHECK (version >= 0)
);

CREATE INDEX idx_points_of_interest_position ON points_of_interest USING GIST (position)
    WHERE active = true;
CREATE INDEX idx_points_of_interest_country_type
    ON points_of_interest (country_code, poi_type, importance_rank DESC)
    WHERE active = true;
CREATE INDEX idx_points_of_interest_name_trgm
    ON points_of_interest USING GIN (normalized_name gin_trgm_ops);
--rollback DROP TABLE points_of_interest;

--changeset ninggiangboy:017-04-property-poi-distances
-- Precomputed because a listing page shows several of these and computing them per request across
-- every candidate in a search result is the difference between a fast page and a slow one. The
-- computed_at instant and catalog version are what make a stale distance detectable once either
-- the property or the landmark has moved.
CREATE TABLE property_poi_distances (
    property_id             UUID NOT NULL,
    point_of_interest_id    UUID NOT NULL,
    straight_line_metres    INTEGER NOT NULL,
    travel_time_seconds     INTEGER,
    travel_mode             VARCHAR(16),
    computed_at             TIMESTAMPTZ NOT NULL,
    catalog_version         VARCHAR(64),
    CONSTRAINT pk_property_poi_distances PRIMARY KEY (property_id, point_of_interest_id),
    CONSTRAINT fk_property_poi_distances_property FOREIGN KEY (property_id)
        REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_property_poi_distances_poi FOREIGN KEY (point_of_interest_id)
        REFERENCES points_of_interest (id) ON DELETE CASCADE,
    CONSTRAINT ck_property_poi_distances_metres CHECK (straight_line_metres >= 0),
    -- Travel time and mode travel together: "18 minutes" means nothing without saying by what.
    CONSTRAINT ck_property_poi_distances_travel CHECK (
        (travel_time_seconds IS NULL) = (travel_mode IS NULL)
    ),
    CONSTRAINT ck_property_poi_distances_travel_time CHECK (
        travel_time_seconds IS NULL OR travel_time_seconds >= 0
    ),
    CONSTRAINT ck_property_poi_distances_mode CHECK (
        travel_mode IS NULL OR travel_mode IN ('WALKING', 'DRIVING', 'TRANSIT', 'CYCLING')
    )
);

CREATE INDEX idx_property_poi_distances_nearest
    ON property_poi_distances (property_id, straight_line_metres);
--rollback DROP TABLE property_poi_distances;

--changeset ninggiangboy:017-05-geocoding-results
-- Every attempt to turn an address into coordinates is recorded, not just the one that won. When a
-- listing's position is wrong a guest is sent to the wrong place, and diagnosing that means knowing
-- which provider produced the point, how confident it was, and what the host had typed at the time.
-- The winning attempt is marked rather than overwriting the others.
CREATE TABLE geocoding_results (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id             UUID NOT NULL,
    direction               VARCHAR(16) NOT NULL,
    provider_account_key    VARCHAR(96),
    provider_account_version SMALLINT,
    query_digest            CHAR(64) NOT NULL,
    matched_latitude        NUMERIC(9,6),
    matched_longitude       NUMERIC(9,6),
    matched_address         VARCHAR(500),
    confidence              VARCHAR(16) NOT NULL,
    match_type              VARCHAR(32),
    raw_response            JSONB,
    is_applied              BOOLEAN NOT NULL DEFAULT false,
    requested_at            TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_geocoding_results_property FOREIGN KEY (property_id)
        REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT ck_geocoding_results_direction CHECK (direction IN ('FORWARD', 'REVERSE')),
    CONSTRAINT ck_geocoding_results_confidence CHECK (
        confidence IN ('EXACT', 'HIGH', 'MEDIUM', 'LOW', 'HOST_PINNED', 'FAILED')
    ),
    CONSTRAINT ck_geocoding_results_digest CHECK (query_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_geocoding_results_coordinates CHECK (
        (matched_latitude IS NULL) = (matched_longitude IS NULL)
    ),
    CONSTRAINT ck_geocoding_results_latitude CHECK (
        matched_latitude IS NULL OR matched_latitude BETWEEN -90 AND 90
    ),
    CONSTRAINT ck_geocoding_results_longitude CHECK (
        matched_longitude IS NULL OR matched_longitude BETWEEN -180 AND 180
    ),
    -- A failed attempt cannot be the one that was applied, and an applied one must have produced a
    -- point. Otherwise a property could claim provenance from an attempt that returned nothing.
    CONSTRAINT ck_geocoding_results_applied CHECK (
        is_applied = false
        OR (confidence <> 'FAILED' AND matched_latitude IS NOT NULL)
    ),
    CONSTRAINT ck_geocoding_results_response CHECK (
        raw_response IS NULL OR jsonb_typeof(raw_response) = 'object'
    ),
    CONSTRAINT ck_geocoding_results_provider CHECK (
        (provider_account_key IS NULL) = (provider_account_version IS NULL)
    )
);

-- At most one attempt is the one a property's coordinates actually came from.
CREATE UNIQUE INDEX uk_geocoding_results_one_applied
    ON geocoding_results (property_id)
    WHERE is_applied = true;

CREATE INDEX idx_geocoding_results_property
    ON geocoding_results (property_id, requested_at DESC);
--rollback DROP TABLE geocoding_results;

--changeset ninggiangboy:017-06-destination-search-support
-- Autocomplete has to rank "Ho Chi Minh City" above a neighbourhood nobody searches for, and has to
-- do it in the few milliseconds a keystroke allows. Popularity is a derived figure recomputed from
-- search and booking traffic, so it is kept beside the catalog rather than inside it: reimporting the
-- geographic catalog must not discard what the platform has learned about which destinations people
-- actually want.
CREATE TABLE geo_area_search_profiles (
    geo_area_id             UUID PRIMARY KEY,
    search_volume_rank      INTEGER NOT NULL DEFAULT 0,
    bookable_property_count INTEGER NOT NULL DEFAULT 0,
    is_suggestable          BOOLEAN NOT NULL DEFAULT true,
    computed_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_geo_area_search_profiles_area FOREIGN KEY (geo_area_id)
        REFERENCES geo_areas (id) ON DELETE CASCADE,
    CONSTRAINT ck_geo_area_search_profiles_rank CHECK (search_volume_rank >= 0),
    CONSTRAINT ck_geo_area_search_profiles_count CHECK (bookable_property_count >= 0),
    CONSTRAINT ck_geo_area_search_profiles_version CHECK (version >= 0)
);

-- Suggesting a destination with nothing bookable in it wastes the guest's only query.
CREATE INDEX idx_geo_area_search_profiles_suggestions
    ON geo_area_search_profiles (search_volume_rank DESC, bookable_property_count DESC)
    WHERE is_suggestable = true AND bookable_property_count > 0;
--rollback DROP TABLE geo_area_search_profiles;
