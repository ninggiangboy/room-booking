--liquibase formatted sql

--changeset ninggiangboy:010-01-spatial-search-extensions
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
--rollback DROP EXTENSION IF EXISTS pg_trgm;
--rollback DROP EXTENSION IF EXISTS postgis;

--changeset ninggiangboy:010-02-geo-areas
CREATE TABLE geo_areas (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id         UUID,
    country_code      VARCHAR(2) NOT NULL,
    area_type         VARCHAR(24) NOT NULL,
    admin_level       SMALLINT,
    name              VARCHAR(200) NOT NULL,
    normalized_name   VARCHAR(200) NOT NULL,
    center            geography(Point, 4326),
    boundary          geometry(MultiPolygon, 4326),
    source            VARCHAR(32) NOT NULL,
    source_id         VARCHAR(128) NOT NULL,
    source_version    VARCHAR(64),
    active            BOOLEAN NOT NULL DEFAULT true,
    valid_from        DATE,
    valid_until       DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_geo_areas_source_id UNIQUE (source, source_id),
    CONSTRAINT uk_geo_areas_id_country UNIQUE (id, country_code),
    CONSTRAINT fk_geo_areas_parent FOREIGN KEY (parent_id, country_code)
        REFERENCES geo_areas (id, country_code),
    CONSTRAINT ck_geo_areas_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_geo_areas_type CHECK (
        area_type IN ('COUNTRY', 'ADMIN_AREA', 'LOCALITY', 'NEIGHBORHOOD', 'POINT_OF_INTEREST')
    ),
    CONSTRAINT ck_geo_areas_admin_level CHECK (admin_level IS NULL OR admin_level BETWEEN 0 AND 10),
    CONSTRAINT ck_geo_areas_country_shape CHECK (
        area_type <> 'COUNTRY' OR (parent_id IS NULL AND admin_level = 0)
    ),
    CONSTRAINT ck_geo_areas_admin_shape CHECK (
        area_type <> 'ADMIN_AREA' OR admin_level BETWEEN 1 AND 10
    ),
    CONSTRAINT ck_geo_areas_validity CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from
    ),
    CONSTRAINT ck_geo_areas_boundary_valid CHECK (boundary IS NULL OR ST_IsValid(boundary)),
    CONSTRAINT ck_geo_areas_version CHECK (version >= 0)
);

CREATE INDEX idx_geo_areas_parent ON geo_areas (parent_id);
CREATE INDEX idx_geo_areas_browse ON geo_areas (country_code, area_type, admin_level, active);
CREATE INDEX idx_geo_areas_name_trgm ON geo_areas USING GIN (normalized_name gin_trgm_ops);
CREATE INDEX idx_geo_areas_center ON geo_areas USING GIST (center);
CREATE INDEX idx_geo_areas_boundary ON geo_areas USING GIST (boundary);
--rollback DROP TABLE geo_areas;

--changeset ninggiangboy:010-03-geo-area-names
CREATE TABLE geo_area_names (
    geo_area_id       UUID NOT NULL,
    language_code     VARCHAR(35) NOT NULL DEFAULT 'und',
    name              VARCHAR(200) NOT NULL,
    normalized_name   VARCHAR(200) NOT NULL,
    name_type         VARCHAR(16) NOT NULL DEFAULT 'ALIAS',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_geo_area_names PRIMARY KEY (geo_area_id, language_code, normalized_name),
    CONSTRAINT fk_geo_area_names_area FOREIGN KEY (geo_area_id)
        REFERENCES geo_areas (id) ON DELETE CASCADE,
    CONSTRAINT ck_geo_area_names_type CHECK (
        name_type IN ('PREFERRED', 'SHORT', 'ALIAS', 'HISTORIC')
    )
);

CREATE UNIQUE INDEX uk_geo_area_names_preferred_language
    ON geo_area_names (geo_area_id, language_code)
    WHERE name_type = 'PREFERRED';

CREATE INDEX idx_geo_area_names_search
    ON geo_area_names USING GIN (normalized_name gin_trgm_ops);
--rollback DROP TABLE geo_area_names;

--changeset ninggiangboy:010-04-listing-spatial-location
ALTER TABLE listings
    ADD COLUMN geo_area_id UUID,
    ADD COLUMN formatted_address VARCHAR(500),
    ADD COLUMN location geography(Point, 4326)
        GENERATED ALWAYS AS (
            CASE
                WHEN latitude IS NOT NULL AND longitude IS NOT NULL
                    THEN ST_SetSRID(
                        ST_MakePoint(
                            longitude::DOUBLE PRECISION,
                            latitude::DOUBLE PRECISION
                        ),
                        4326
                    )::geography
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT fk_listings_geo_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id);

CREATE INDEX idx_listings_published_geo_area
    ON listings (geo_area_id, max_guests)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_listings_published_location
    ON listings USING GIST (location)
    WHERE status = 'PUBLISHED' AND location IS NOT NULL;
--rollback DROP INDEX idx_listings_published_location;
--rollback DROP INDEX idx_listings_published_geo_area;
--rollback ALTER TABLE listings DROP CONSTRAINT fk_listings_geo_area;
--rollback ALTER TABLE listings DROP COLUMN location;
--rollback ALTER TABLE listings DROP COLUMN formatted_address;
--rollback ALTER TABLE listings DROP COLUMN geo_area_id;
