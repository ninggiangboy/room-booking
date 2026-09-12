--liquibase formatted sql

-- The target vocabulary replaces the listing-centric one rather than extending it. A listing is no
-- longer the thing that is sold: a property is a physical location, an accommodation type is the
-- sellable category at that property, a physical unit is an optionally assigned specific room, and a
-- listing is the public presentation of an accommodation type. That separation is what lets one
-- hotel sell forty identical rooms from a pooled count while a single apartment sells itself once,
-- without the two being different codebases.
--
-- Migrations 002-006 cannot be reshaped into that model with ALTER statements, because the grain of
-- the tables is wrong: availability_days is keyed by listing, bookings reference a listing as
-- inventory, and neither is true any more. They are retired here and rebuilt forward.
--
-- The retirement is one changeset because foreign keys make it one unit: listings is the root of a
-- cluster that includes listing_images, listing_amenities, availability_days, bookings,
-- booking_nights, payment_attempts, refunds, reviews, and favorites. Dropping it piecemeal across
-- later migrations is not possible, so the whole historical stack goes at once and each later
-- migration builds its target tables on clean ground.
--
-- No Java code reads any of these tables and no environment holds data in them. The target
-- replacements arrive in 018 (inventory), 020 (booking), 021 (payment), 026 (reviews), and 029
-- (saved listings).

--changeset ninggiangboy:016-01-retire-historical-listing-stack
-- Dropped in reverse dependency order. This changeset is not reversible: rolling it back recreates
-- nothing, because the definitions live in applied changesets 002-006 which must never be edited.
-- Restoring the historical shape means restoring a database snapshot taken before this migration.
DROP TABLE IF EXISTS refunds;
DROP TABLE IF EXISTS payment_webhook_events;
DROP TABLE IF EXISTS payment_attempts;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS favorites;
DROP TABLE IF EXISTS booking_nights;
DROP TABLE IF EXISTS bookings;
DROP TABLE IF EXISTS availability_days;
DROP TABLE IF EXISTS listing_amenities;
DROP TABLE IF EXISTS listing_images;
DROP TABLE IF EXISTS amenities;
DROP TABLE IF EXISTS listings;
--rollback empty

--changeset ninggiangboy:016-02-properties
CREATE TABLE properties (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id       UUID NOT NULL,
    market_code             VARCHAR(2) NOT NULL,
    reference_code          VARCHAR(24) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    property_type           VARCHAR(32) NOT NULL,
    time_zone               VARCHAR(64) NOT NULL,
    country_code            VARCHAR(2) NOT NULL,
    formatted_address       VARCHAR(500),
    address_reference       VARCHAR(255),
    latitude                NUMERIC(9,6),
    longitude               NUMERIC(9,6),
    location                geography(Point, 4326)
        GENERATED ALWAYS AS (
            CASE
                WHEN latitude IS NOT NULL AND longitude IS NOT NULL
                    THEN ST_SetSRID(
                        ST_MakePoint(longitude::DOUBLE PRECISION, latitude::DOUBLE PRECISION),
                        4326
                    )::geography
                ELSE NULL
            END
        ) STORED,
    public_latitude         NUMERIC(9,6),
    public_longitude        NUMERIC(9,6),
    geocode_confidence      VARCHAR(16),
    geocode_source          VARCHAR(32),
    lifecycle_state         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_properties_reference UNIQUE (reference_code),
    CONSTRAINT fk_properties_holder FOREIGN KEY (account_holder_id) REFERENCES account_holders (id),
    CONSTRAINT fk_properties_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT ck_properties_type CHECK (
        property_type IN ('APARTMENT', 'HOUSE', 'VILLA', 'HOTEL', 'HOSTEL', 'GUESTHOUSE',
                          'RESORT', 'HOMESTAY', 'BOUTIQUE_HOTEL', 'SERVICED_APARTMENT', 'OTHER')
    ),
    CONSTRAINT ck_properties_country CHECK (country_code ~ '^[A-Z]{2}$'),
    -- The property's own IANA zone, not the market's: a market can span zones, and every stay date
    -- and deadline for this property is resolved in this zone. Aliases carry no DST history.
    CONSTRAINT ck_properties_time_zone CHECK (time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$'),
    CONSTRAINT ck_properties_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_properties_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    -- The obfuscated position shown before booking is stored, not computed per request, so every
    -- viewer sees the same circle. Recomputing it per request leaks the true point by triangulation.
    CONSTRAINT ck_properties_public_position CHECK (
        (public_latitude IS NULL) = (public_longitude IS NULL)
    ),
    CONSTRAINT ck_properties_geocode_confidence CHECK (
        geocode_confidence IS NULL
        OR geocode_confidence IN ('EXACT', 'HIGH', 'MEDIUM', 'LOW', 'HOST_PINNED')
    ),
    CONSTRAINT ck_properties_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')
    ),
    CONSTRAINT ck_properties_version CHECK (version >= 0)
);

CREATE INDEX idx_properties_holder ON properties (account_holder_id, lifecycle_state);
CREATE INDEX idx_properties_market ON properties (market_code, lifecycle_state);
CREATE INDEX idx_properties_location ON properties USING GIST (location)
    WHERE location IS NOT NULL;
--rollback DROP TABLE properties;

--changeset ninggiangboy:016-03-accommodation-types
CREATE TABLE accommodation_types (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id               UUID NOT NULL,
    reference_code            VARCHAR(24) NOT NULL,
    display_name              VARCHAR(255) NOT NULL,
    inventory_mode            VARCHAR(16) NOT NULL,
    sellable_quantity         INTEGER NOT NULL DEFAULT 1,
    room_type                 VARCHAR(32) NOT NULL,
    space_sharing             VARCHAR(16) NOT NULL,
    standard_occupancy        SMALLINT NOT NULL,
    maximum_occupancy         SMALLINT NOT NULL,
    maximum_adults            SMALLINT,
    maximum_children          SMALLINT,
    allows_infants            BOOLEAN NOT NULL DEFAULT true,
    bedroom_count             SMALLINT NOT NULL DEFAULT 0,
    bed_count                 SMALLINT NOT NULL DEFAULT 0,
    bathroom_count            NUMERIC(3,1) NOT NULL DEFAULT 0,
    floor_area_sqm            NUMERIC(7,2),
    lifecycle_state           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accommodation_types_reference UNIQUE (reference_code),
    CONSTRAINT fk_accommodation_types_property FOREIGN KEY (property_id) REFERENCES properties (id),
    -- The one decision that shapes everything downstream. A unique rental is sold once and defended
    -- by an overlap exclusion constraint; a pooled type is sold from a per-date count defended by a
    -- quantity check. Migration 018 builds both on this column.
    CONSTRAINT ck_accommodation_types_inventory_mode CHECK (
        inventory_mode IN ('UNIQUE_RENTAL', 'QUANTITY_POOL')
    ),
    CONSTRAINT ck_accommodation_types_unique_quantity CHECK (
        inventory_mode <> 'UNIQUE_RENTAL' OR sellable_quantity = 1
    ),
    CONSTRAINT ck_accommodation_types_pool_quantity CHECK (
        inventory_mode <> 'QUANTITY_POOL' OR sellable_quantity >= 1
    ),
    CONSTRAINT ck_accommodation_types_room_type CHECK (
        room_type IN ('ENTIRE_PLACE', 'PRIVATE_ROOM', 'SHARED_ROOM', 'DORM_BED')
    ),
    CONSTRAINT ck_accommodation_types_sharing CHECK (
        space_sharing IN ('EXCLUSIVE', 'SHARED_COMMON', 'SHARED_ALL')
    ),
    -- An entire place cannot be shared with anyone, by definition. Letting the two disagree is how a
    -- guest books what they believe is a private home and finds strangers in the kitchen.
    CONSTRAINT ck_accommodation_types_sharing_consistency CHECK (
        room_type <> 'ENTIRE_PLACE' OR space_sharing = 'EXCLUSIVE'
    ),
    CONSTRAINT ck_accommodation_types_occupancy CHECK (
        standard_occupancy > 0 AND maximum_occupancy >= standard_occupancy
    ),
    CONSTRAINT ck_accommodation_types_party_limits CHECK (
        (maximum_adults IS NULL OR maximum_adults > 0)
        AND (maximum_children IS NULL OR maximum_children >= 0)
    ),
    CONSTRAINT ck_accommodation_types_counts CHECK (
        bedroom_count >= 0 AND bed_count >= 0 AND bathroom_count >= 0
    ),
    CONSTRAINT ck_accommodation_types_area CHECK (floor_area_sqm IS NULL OR floor_area_sqm > 0),
    CONSTRAINT ck_accommodation_types_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')
    ),
    CONSTRAINT ck_accommodation_types_version CHECK (version >= 0)
);

CREATE INDEX idx_accommodation_types_property
    ON accommodation_types (property_id, lifecycle_state);
--rollback DROP TABLE accommodation_types;

--changeset ninggiangboy:016-04-physical-units
CREATE TABLE physical_units (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id   UUID NOT NULL,
    unit_label              VARCHAR(64) NOT NULL,
    floor_label             VARCHAR(32),
    status                  VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    out_of_service_reason   VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_physical_units_label UNIQUE (accommodation_type_id, unit_label),
    CONSTRAINT fk_physical_units_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_physical_units_status CHECK (
        status IN ('AVAILABLE', 'OUT_OF_SERVICE', 'RETIRED')
    ),
    CONSTRAINT ck_physical_units_out_of_service CHECK (
        (status = 'OUT_OF_SERVICE') = (out_of_service_reason IS NOT NULL)
    ),
    CONSTRAINT ck_physical_units_version CHECK (version >= 0)
);

CREATE INDEX idx_physical_units_type ON physical_units (accommodation_type_id, status);
--rollback DROP TABLE physical_units;

--changeset ninggiangboy:016-05-listings
CREATE TABLE listings (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id     UUID NOT NULL,
    reference_code            VARCHAR(24) NOT NULL,
    slug                      VARCHAR(160),
    status                    VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at              TIMESTAMPTZ,
    unpublished_at            TIMESTAMPTZ,
    quality_score             NUMERIC(5,2),
    quality_scored_at         TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_listings_reference UNIQUE (reference_code),
    CONSTRAINT uk_listings_slug UNIQUE (slug),
    CONSTRAINT fk_listings_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_listings_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'PAUSED', 'ARCHIVED')
    ),
    CONSTRAINT ck_listings_publication CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_listings_quality CHECK (
        (quality_score IS NULL) = (quality_scored_at IS NULL)
    ),
    CONSTRAINT ck_listings_quality_range CHECK (
        quality_score IS NULL OR quality_score BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_listings_version CHECK (version >= 0)
);

-- One public listing per accommodation type at a time. Two published listings for the same sellable
-- inventory would compete for the same nights and double-count in search.
CREATE UNIQUE INDEX uk_listings_one_live_per_type
    ON listings (accommodation_type_id)
    WHERE status IN ('PUBLISHED', 'PAUSED', 'IN_REVIEW');

CREATE INDEX idx_listings_published ON listings (status, published_at DESC)
    WHERE status = 'PUBLISHED';
--rollback DROP TABLE listings;

--changeset ninggiangboy:016-06-rate-plans
CREATE TABLE rate_plans (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id       UUID NOT NULL,
    reference_code              VARCHAR(24) NOT NULL,
    display_name                VARCHAR(160) NOT NULL,
    is_default                  BOOLEAN NOT NULL DEFAULT false,
    cancellation_policy_key     VARCHAR(48) NOT NULL,
    meal_plan                   VARCHAR(24) NOT NULL DEFAULT 'ROOM_ONLY',
    prepayment_requirement      VARCHAR(24) NOT NULL DEFAULT 'FULL_PREPAYMENT',
    deposit_required            BOOLEAN NOT NULL DEFAULT false,
    minimum_stay_nights         SMALLINT,
    maximum_stay_nights         SMALLINT,
    booking_window_days         SMALLINT,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_rate_plans_reference UNIQUE (reference_code),
    CONSTRAINT fk_rate_plans_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_rate_plans_meal CHECK (
        meal_plan IN ('ROOM_ONLY', 'BREAKFAST', 'HALF_BOARD', 'FULL_BOARD', 'ALL_INCLUSIVE')
    ),
    CONSTRAINT ck_rate_plans_prepayment CHECK (
        prepayment_requirement IN ('FULL_PREPAYMENT', 'DEPOSIT', 'PAY_AT_PROPERTY', 'INSTALMENTS')
    ),
    CONSTRAINT ck_rate_plans_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'ARCHIVED')
    ),
    CONSTRAINT ck_rate_plans_stay_bounds CHECK (
        (minimum_stay_nights IS NULL OR minimum_stay_nights > 0)
        AND (maximum_stay_nights IS NULL OR maximum_stay_nights > 0)
        AND (minimum_stay_nights IS NULL OR maximum_stay_nights IS NULL
             OR maximum_stay_nights >= minimum_stay_nights)
    ),
    CONSTRAINT ck_rate_plans_booking_window CHECK (
        booking_window_days IS NULL OR booking_window_days > 0
    ),
    CONSTRAINT ck_rate_plans_version CHECK (version >= 0)
);

-- Exactly one default offer per accommodation type, so a quote always has an unambiguous starting
-- point when the guest expressed no preference.
CREATE UNIQUE INDEX uk_rate_plans_one_default
    ON rate_plans (accommodation_type_id)
    WHERE is_default = true AND status <> 'ARCHIVED';

CREATE INDEX idx_rate_plans_type ON rate_plans (accommodation_type_id, status);
--rollback DROP TABLE rate_plans;

--changeset ninggiangboy:016-07-amenity-vocabulary
-- The old amenities table was a flat list of names. A vocabulary has to be versioned instead,
-- because search filters, guest expectations, and historical bookings all cite amenities: renaming
-- "Wifi" to "Wi-Fi" must not silently change what a guest booked last year, and retiring a term must
-- not orphan the listings that claimed it.
CREATE TABLE amenity_vocabularies (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vocabulary_key    VARCHAR(48) NOT NULL,
    vocabulary_version INTEGER NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from    TIMESTAMPTZ NOT NULL,
    effective_until   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_amenity_vocabularies_version UNIQUE (vocabulary_key, vocabulary_version),
    CONSTRAINT ck_amenity_vocabularies_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_amenity_vocabularies_version_number CHECK (vocabulary_version > 0),
    CONSTRAINT ck_amenity_vocabularies_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    )
);

CREATE TABLE amenity_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    amenity_vocabulary_id   UUID NOT NULL,
    amenity_key             VARCHAR(64) NOT NULL,
    category                VARCHAR(48) NOT NULL,
    value_type              VARCHAR(16) NOT NULL DEFAULT 'BOOLEAN',
    is_searchable           BOOLEAN NOT NULL DEFAULT true,
    requires_evidence       BOOLEAN NOT NULL DEFAULT false,
    sort_order              SMALLINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_amenity_definitions_key UNIQUE (amenity_vocabulary_id, amenity_key),
    CONSTRAINT fk_amenity_definitions_vocabulary FOREIGN KEY (amenity_vocabulary_id)
        REFERENCES amenity_vocabularies (id),
    CONSTRAINT ck_amenity_definitions_value_type CHECK (
        value_type IN ('BOOLEAN', 'COUNT', 'TEXT', 'ENUM')
    )
);

CREATE INDEX idx_amenity_definitions_category
    ON amenity_definitions (amenity_vocabulary_id, category, sort_order);

-- Amenity labels are translated, and the translation is separate from the key so that search and
-- storage use the stable key while presentation uses the reader's language.
CREATE TABLE amenity_translations (
    amenity_definition_id   UUID NOT NULL,
    locale                  VARCHAR(35) NOT NULL,
    label                   VARCHAR(160) NOT NULL,
    description             TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_amenity_translations PRIMARY KEY (amenity_definition_id, locale),
    CONSTRAINT fk_amenity_translations_definition FOREIGN KEY (amenity_definition_id)
        REFERENCES amenity_definitions (id) ON DELETE CASCADE
);

CREATE TABLE accommodation_type_amenities (
    accommodation_type_id   UUID NOT NULL,
    amenity_definition_id   UUID NOT NULL,
    count_value             SMALLINT,
    text_value              VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_accommodation_type_amenities
        PRIMARY KEY (accommodation_type_id, amenity_definition_id),
    CONSTRAINT fk_accommodation_type_amenities_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id) ON DELETE CASCADE,
    CONSTRAINT fk_accommodation_type_amenities_definition FOREIGN KEY (amenity_definition_id)
        REFERENCES amenity_definitions (id),
    CONSTRAINT ck_accommodation_type_amenities_count CHECK (
        count_value IS NULL OR count_value > 0
    )
);

CREATE INDEX idx_accommodation_type_amenities_definition
    ON accommodation_type_amenities (amenity_definition_id);
--rollback DROP TABLE accommodation_type_amenities;
--rollback DROP TABLE amenity_translations;
--rollback DROP TABLE amenity_definitions;
--rollback DROP TABLE amenity_vocabularies;

--changeset ninggiangboy:016-08-listing-content
CREATE TABLE listing_contents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id              UUID NOT NULL,
    locale                  VARCHAR(35) NOT NULL,
    is_source               BOOLEAN NOT NULL DEFAULT false,
    translation_source      VARCHAR(16) NOT NULL DEFAULT 'HOST',
    title                   VARCHAR(255) NOT NULL,
    summary                 TEXT,
    description             TEXT,
    neighbourhood_note      TEXT,
    moderation_state        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    moderated_at            TIMESTAMPTZ,
    content_version         INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_listing_contents_locale UNIQUE (listing_id, locale),
    CONSTRAINT fk_listing_contents_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_contents_translation_source CHECK (
        translation_source IN ('HOST', 'MACHINE', 'PROFESSIONAL')
    ),
    CONSTRAINT ck_listing_contents_moderation CHECK (
        moderation_state IN ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED')
    ),
    CONSTRAINT ck_listing_contents_moderated CHECK (
        (moderation_state = 'PENDING') = (moderated_at IS NULL)
    ),
    CONSTRAINT ck_listing_contents_version_number CHECK (content_version > 0),
    CONSTRAINT ck_listing_contents_version CHECK (version >= 0)
);

-- Exactly one locale is what the host actually wrote; the rest are translations of it. Without this
-- there is no answer to "which wording is authoritative" when two translations disagree.
CREATE UNIQUE INDEX uk_listing_contents_one_source
    ON listing_contents (listing_id)
    WHERE is_source = true;

CREATE INDEX idx_listing_contents_moderation_queue
    ON listing_contents (created_at)
    WHERE moderation_state IN ('PENDING', 'FLAGGED');
--rollback DROP TABLE listing_contents;

--changeset ninggiangboy:016-09-listing-media
CREATE TABLE listing_media (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id              UUID NOT NULL,
    media_type              VARCHAR(16) NOT NULL DEFAULT 'IMAGE',
    storage_reference       VARCHAR(255) NOT NULL,
    content_digest          CHAR(64) NOT NULL,
    content_type            VARCHAR(64) NOT NULL,
    byte_size               BIGINT NOT NULL,
    width_px                INTEGER,
    height_px               INTEGER,
    duration_ms             INTEGER,
    alt_text                VARCHAR(500),
    room_tag                VARCHAR(48),
    display_order           SMALLINT NOT NULL DEFAULT 0,
    is_cover                BOOLEAN NOT NULL DEFAULT false,
    scan_state              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    moderation_state        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    processing_state        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    rights_confirmed_at     TIMESTAMPTZ,
    uploaded_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listing_media_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_media_type CHECK (media_type IN ('IMAGE', 'VIDEO', 'FLOOR_PLAN', 'TOUR')),
    CONSTRAINT ck_listing_media_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_listing_media_size CHECK (byte_size > 0),
    CONSTRAINT ck_listing_media_dimensions CHECK (
        (width_px IS NULL OR width_px > 0) AND (height_px IS NULL OR height_px > 0)
    ),
    CONSTRAINT ck_listing_media_duration CHECK (duration_ms IS NULL OR duration_ms > 0),
    -- Three independent gates, because they fail independently: a file can be malware-free but
    -- depict something prohibited, or be clean and permitted but not yet resized for delivery.
    CONSTRAINT ck_listing_media_scan CHECK (
        scan_state IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED')
    ),
    CONSTRAINT ck_listing_media_moderation CHECK (
        moderation_state IN ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED')
    ),
    CONSTRAINT ck_listing_media_processing CHECK (
        processing_state IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')
    ),
    CONSTRAINT ck_listing_media_order CHECK (display_order >= 0),
    CONSTRAINT ck_listing_media_version CHECK (version >= 0)
);

-- At most one cover image per listing, following the historical uk_listing_images_one_cover rule.
CREATE UNIQUE INDEX uk_listing_media_one_cover
    ON listing_media (listing_id)
    WHERE is_cover = true;

CREATE INDEX idx_listing_media_gallery
    ON listing_media (listing_id, display_order)
    WHERE scan_state = 'CLEAN' AND moderation_state = 'APPROVED' AND processing_state = 'READY';

-- The same photograph uploaded against two different properties is a strong duplicate-listing signal.
CREATE INDEX idx_listing_media_digest ON listing_media (content_digest);
--rollback DROP TABLE listing_media;

--changeset ninggiangboy:016-10-house-rules-and-safety
CREATE TABLE property_house_rules (
    property_id                 UUID PRIMARY KEY,
    check_in_from               TIME NOT NULL,
    check_in_until              TIME,
    check_out_by                TIME NOT NULL,
    quiet_hours_from            TIME,
    quiet_hours_until           TIME,
    minimum_guest_age           SMALLINT,
    pets_policy                 VARCHAR(16) NOT NULL DEFAULT 'NOT_ALLOWED',
    smoking_policy              VARCHAR(16) NOT NULL DEFAULT 'NOT_ALLOWED',
    parties_policy              VARCHAR(16) NOT NULL DEFAULT 'NOT_ALLOWED',
    children_policy             VARCHAR(16) NOT NULL DEFAULT 'ALLOWED',
    additional_rules            TEXT,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_property_house_rules_property FOREIGN KEY (property_id)
        REFERENCES properties (id) ON DELETE CASCADE,
    -- Check-in and check-out are civil times in the property's zone, which is why the property owns
    -- its own time_zone column: 15:00 means three in the afternoon there, not here.
    CONSTRAINT ck_property_house_rules_policies CHECK (
        pets_policy IN ('ALLOWED', 'ON_REQUEST', 'NOT_ALLOWED')
        AND smoking_policy IN ('ALLOWED', 'OUTSIDE_ONLY', 'NOT_ALLOWED')
        AND parties_policy IN ('ALLOWED', 'ON_REQUEST', 'NOT_ALLOWED')
        AND children_policy IN ('ALLOWED', 'ON_REQUEST', 'NOT_ALLOWED')
    ),
    CONSTRAINT ck_property_house_rules_age CHECK (
        minimum_guest_age IS NULL OR minimum_guest_age BETWEEN 0 AND 120
    ),
    -- Quiet hours may legitimately cross midnight, so no ordering is imposed between them; both must
    -- simply be present or absent together.
    CONSTRAINT ck_property_house_rules_quiet_hours CHECK (
        (quiet_hours_from IS NULL) = (quiet_hours_until IS NULL)
    ),
    CONSTRAINT ck_property_house_rules_version CHECK (version >= 0)
);
--rollback DROP TABLE property_house_rules;

--changeset ninggiangboy:016-11-safety-and-accessibility
CREATE TABLE property_safety_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id         UUID NOT NULL,
    safety_item_key     VARCHAR(48) NOT NULL,
    is_present          BOOLEAN NOT NULL,
    last_checked_on     DATE,
    notes               VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_property_safety_items UNIQUE (property_id, safety_item_key),
    CONSTRAINT fk_property_safety_items_property FOREIGN KEY (property_id)
        REFERENCES properties (id) ON DELETE CASCADE,
    -- Absence is recorded explicitly as false rather than as a missing row. "This property has no
    -- smoke alarm" is information a guest is entitled to; "nobody asked" is not the same statement.
    CONSTRAINT ck_property_safety_items_version CHECK (version >= 0)
);

CREATE TABLE accessibility_claims (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id   UUID NOT NULL,
    claim_key               VARCHAR(64) NOT NULL,
    is_claimed              BOOLEAN NOT NULL,
    measurement_value       NUMERIC(7,2),
    measurement_unit        VARCHAR(16),
    evidence_media_id       UUID,
    verified_at             TIMESTAMPTZ,
    verified_by             UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_accessibility_claims UNIQUE (accommodation_type_id, claim_key),
    CONSTRAINT fk_accessibility_claims_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id) ON DELETE CASCADE,
    CONSTRAINT fk_accessibility_claims_evidence FOREIGN KEY (evidence_media_id)
        REFERENCES listing_media (id),
    -- An accessibility claim is one a guest may rely on to decide whether they can physically enter
    -- the property. Measurements carry their unit so "32" is never ambiguous, and verification is
    -- recorded with its evidence because an unverified claim and a verified one are different
    -- promises.
    CONSTRAINT ck_accessibility_claims_measurement CHECK (
        (measurement_value IS NULL) = (measurement_unit IS NULL)
    ),
    CONSTRAINT ck_accessibility_claims_verification CHECK (
        (verified_at IS NULL) = (verified_by IS NULL)
    ),
    CONSTRAINT ck_accessibility_claims_version CHECK (version >= 0)
);

CREATE INDEX idx_accessibility_claims_type ON accessibility_claims (accommodation_type_id);
--rollback DROP TABLE accessibility_claims;
--rollback DROP TABLE property_safety_items;

--changeset ninggiangboy:016-12-listing-change-history
CREATE TABLE listing_change_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id          UUID NOT NULL,
    change_type         VARCHAR(32) NOT NULL,
    field_path          VARCHAR(128),
    before_digest       CHAR(64),
    after_digest        CHAR(64),
    change_summary      JSONB,
    actor_type          VARCHAR(24) NOT NULL,
    actor_id            UUID,
    reason_code         VARCHAR(64),
    occurred_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_listing_change_history_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_change_history_type CHECK (
        change_type IN ('CREATED', 'CONTENT_EDITED', 'MEDIA_CHANGED', 'AMENITIES_CHANGED',
                        'RULES_CHANGED', 'PUBLISHED', 'PAUSED', 'ARCHIVED', 'MODERATED',
                        'OWNERSHIP_CHANGED')
    ),
    CONSTRAINT ck_listing_change_history_actor CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM')
    ),
    CONSTRAINT ck_listing_change_history_digests CHECK (
        (before_digest IS NULL OR before_digest ~ '^[0-9a-f]{64}$')
        AND (after_digest IS NULL OR after_digest ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_listing_change_history_summary CHECK (
        change_summary IS NULL OR jsonb_typeof(change_summary) = 'object'
    )
);

-- A guest disputing "the listing said there was air conditioning" needs the listing as it was on the
-- day they booked, not as it is now.
CREATE INDEX idx_listing_change_history_timeline
    ON listing_change_history (listing_id, occurred_at DESC);
--rollback DROP TABLE listing_change_history;

--changeset ninggiangboy:016-13-property-collaborators
CREATE TABLE property_collaborators (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id         UUID NOT NULL,
    user_id             UUID NOT NULL,
    collaborator_role   VARCHAR(24) NOT NULL,
    capability_grant_id UUID,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    invited_by          UUID,
    started_at          TIMESTAMPTZ NOT NULL,
    ended_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_property_collaborators_pair UNIQUE (property_id, user_id),
    CONSTRAINT fk_property_collaborators_property FOREIGN KEY (property_id)
        REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT fk_property_collaborators_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_property_collaborators_grant FOREIGN KEY (capability_grant_id)
        REFERENCES capability_grants (id),
    CONSTRAINT ck_property_collaborators_role CHECK (
        collaborator_role IN ('CO_HOST', 'CLEANER', 'MAINTENANCE', 'FRONT_DESK', 'VIEWER')
    ),
    CONSTRAINT ck_property_collaborators_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'ENDED')
    ),
    CONSTRAINT ck_property_collaborators_ended CHECK (
        (status = 'ENDED') = (ended_at IS NOT NULL)
    ),
    -- The row records that someone collaborates on this property; it never decides what they may do.
    -- capability_grant_id points at the property-scoped grant that does, so ending a collaboration
    -- and revoking its authority are one linked act rather than two that can drift apart.
    CONSTRAINT ck_property_collaborators_version CHECK (version >= 0)
);

CREATE INDEX idx_property_collaborators_user ON property_collaborators (user_id, status);
--rollback DROP TABLE property_collaborators;
