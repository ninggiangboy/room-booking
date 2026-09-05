--liquibase formatted sql

--changeset ninggiangboy:003-01-availability-days
CREATE TABLE availability_days (
    listing_id            UUID NOT NULL,
    stay_date             DATE NOT NULL,
    availability_status   VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    nightly_price_minor   BIGINT NOT NULL,
    minimum_nights        SMALLINT,
    block_reason          VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_availability_days PRIMARY KEY (listing_id, stay_date),
    CONSTRAINT fk_availability_days_listing FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT ck_availability_days_status CHECK (availability_status IN ('AVAILABLE', 'BLOCKED')),
    CONSTRAINT ck_availability_days_price CHECK (nightly_price_minor >= 0),
    CONSTRAINT ck_availability_days_minimum_nights CHECK (minimum_nights IS NULL OR minimum_nights > 0),
    CONSTRAINT ck_availability_days_version CHECK (version >= 0)
);

CREATE INDEX idx_availability_days_search
    ON availability_days (stay_date, availability_status, listing_id);
--rollback DROP TABLE availability_days;
