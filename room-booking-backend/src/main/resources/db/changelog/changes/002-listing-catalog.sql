--liquibase formatted sql

--changeset ninggiangboy:002-01-listings
CREATE TABLE listings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id                 UUID NOT NULL,
    title                   VARCHAR(160) NOT NULL,
    description             TEXT NOT NULL,
    property_type           VARCHAR(32) NOT NULL,
    room_type               VARCHAR(24) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    max_guests              SMALLINT NOT NULL,
    bedrooms                SMALLINT NOT NULL DEFAULT 0,
    beds                    SMALLINT NOT NULL DEFAULT 0,
    bathrooms               NUMERIC(3, 1) NOT NULL DEFAULT 0,
    base_price_minor        BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    cleaning_fee_minor      BIGINT NOT NULL DEFAULT 0,
    service_fee_percent     NUMERIC(5, 2) NOT NULL DEFAULT 0,
    minimum_nights          SMALLINT NOT NULL DEFAULT 1,
    maximum_nights          SMALLINT NOT NULL DEFAULT 365,
    check_in_from           TIME NOT NULL,
    check_in_until          TIME,
    check_out_until         TIME NOT NULL,
    instant_book            BOOLEAN NOT NULL DEFAULT false,
    cancellation_policy     VARCHAR(16) NOT NULL DEFAULT 'MODERATE',
    timezone                VARCHAR(64) NOT NULL,
    address_line            VARCHAR(255) NOT NULL,
    ward                    VARCHAR(120),
    district                VARCHAR(120),
    city                    VARCHAR(120) NOT NULL,
    country_code            VARCHAR(2) NOT NULL,
    postal_code             VARCHAR(24),
    latitude                NUMERIC(9, 6),
    longitude               NUMERIC(9, 6),
    published_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listings_host FOREIGN KEY (host_id) REFERENCES users (id),
    CONSTRAINT ck_listings_property_type CHECK (
        property_type IN ('APARTMENT', 'HOUSE', 'VILLA', 'HOTEL_ROOM', 'GUEST_HOUSE', 'OTHER')
    ),
    CONSTRAINT ck_listings_room_type CHECK (
        room_type IN ('ENTIRE_PLACE', 'PRIVATE_ROOM', 'SHARED_ROOM')
    ),
    CONSTRAINT ck_listings_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT ck_listings_capacity CHECK (
        max_guests > 0 AND bedrooms >= 0 AND beds >= 0 AND bathrooms >= 0
    ),
    CONSTRAINT ck_listings_money CHECK (
        base_price_minor >= 0 AND cleaning_fee_minor >= 0 AND service_fee_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_listings_night_limits CHECK (
        minimum_nights > 0 AND maximum_nights >= minimum_nights
    ),
    CONSTRAINT ck_listings_check_in_window CHECK (
        check_in_until IS NULL OR check_in_until >= check_in_from
    ),
    CONSTRAINT ck_listings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_listings_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_listings_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT ck_listings_cancellation_policy CHECK (
        cancellation_policy IN ('FLEXIBLE', 'MODERATE', 'STRICT')
    ),
    CONSTRAINT ck_listings_published_at CHECK (
        (status = 'DRAFT' AND published_at IS NULL) OR status <> 'DRAFT'
    ),
    CONSTRAINT ck_listings_version CHECK (version >= 0)
);

CREATE INDEX idx_listings_host ON listings (host_id);
CREATE INDEX idx_listings_search ON listings (city, status, max_guests);
--rollback DROP TABLE listings;

--changeset ninggiangboy:002-02-listing-images
CREATE TABLE listing_images (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id     UUID NOT NULL,
    storage_key    VARCHAR(512) NOT NULL,
    url            VARCHAR(2048) NOT NULL,
    caption        VARCHAR(255),
    sort_order     INTEGER NOT NULL DEFAULT 0,
    is_cover       BOOLEAN NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_listing_images_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT uk_listing_images_storage_key UNIQUE (storage_key),
    CONSTRAINT uk_listing_images_order UNIQUE (listing_id, sort_order),
    CONSTRAINT ck_listing_images_sort_order CHECK (sort_order >= 0)
);

CREATE UNIQUE INDEX uk_listing_images_one_cover
    ON listing_images (listing_id)
    WHERE is_cover;
--rollback DROP TABLE listing_images;

--changeset ninggiangboy:002-03-amenities
CREATE TABLE amenities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    category    VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_amenities_code UNIQUE (code)
);

CREATE TABLE listing_amenities (
    listing_id   UUID NOT NULL,
    amenity_id   UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_listing_amenities PRIMARY KEY (listing_id, amenity_id),
    CONSTRAINT fk_listing_amenities_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT fk_listing_amenities_amenity FOREIGN KEY (amenity_id) REFERENCES amenities (id)
);

CREATE INDEX idx_listing_amenities_amenity ON listing_amenities (amenity_id, listing_id);
--rollback DROP TABLE listing_amenities;
--rollback DROP TABLE amenities;
