--liquibase formatted sql

--changeset ninggiangboy:004-01-bookings
CREATE TABLE bookings (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    confirmation_code        VARCHAR(32) NOT NULL,
    listing_id               UUID NOT NULL,
    guest_id                 UUID NOT NULL,
    host_id                  UUID NOT NULL,
    check_in                 DATE NOT NULL,
    check_out                DATE NOT NULL,
    guest_count              SMALLINT NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    currency                 VARCHAR(3) NOT NULL,
    accommodation_minor      BIGINT NOT NULL,
    cleaning_fee_minor       BIGINT NOT NULL DEFAULT 0,
    service_fee_minor        BIGINT NOT NULL DEFAULT 0,
    tax_minor                BIGINT NOT NULL DEFAULT 0,
    discount_minor           BIGINT NOT NULL DEFAULT 0,
    total_minor              BIGINT NOT NULL,
    cancellation_policy      VARCHAR(16) NOT NULL,
    listing_snapshot         JSONB NOT NULL,
    guest_note               TEXT,
    payment_expires_at       TIMESTAMPTZ,
    confirmed_at             TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_bookings_confirmation_code UNIQUE (confirmation_code),
    CONSTRAINT fk_bookings_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_bookings_guest FOREIGN KEY (guest_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_host FOREIGN KEY (host_id) REFERENCES users (id),
    CONSTRAINT ck_bookings_stay_range CHECK (check_out > check_in),
    CONSTRAINT ck_bookings_guest_count CHECK (guest_count > 0),
    CONSTRAINT ck_bookings_status CHECK (
        status IN (
            'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED_BY_GUEST',
            'CANCELLED_BY_HOST', 'EXPIRED', 'COMPLETED', 'NO_SHOW'
        )
    ),
    CONSTRAINT ck_bookings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_bookings_money CHECK (
        accommodation_minor >= 0
        AND cleaning_fee_minor >= 0
        AND service_fee_minor >= 0
        AND tax_minor >= 0
        AND discount_minor >= 0
        AND total_minor >= 0
        AND total_minor = accommodation_minor + cleaning_fee_minor + service_fee_minor + tax_minor - discount_minor
    ),
    CONSTRAINT ck_bookings_cancellation_policy CHECK (
        cancellation_policy IN ('FLEXIBLE', 'MODERATE', 'STRICT')
    ),
    CONSTRAINT ck_bookings_snapshot CHECK (jsonb_typeof(listing_snapshot) = 'object'),
    CONSTRAINT ck_bookings_payment_expiration CHECK (
        status <> 'PENDING_PAYMENT' OR payment_expires_at IS NOT NULL
    ),
    CONSTRAINT ck_bookings_version CHECK (version >= 0),
    CONSTRAINT ex_bookings_no_active_overlap EXCLUDE USING gist (
        listing_id WITH =,
        daterange(check_in, check_out, '[)') WITH &&
    ) WHERE (status IN ('PENDING_PAYMENT', 'CONFIRMED'))
);

CREATE INDEX idx_bookings_guest_created ON bookings (guest_id, created_at DESC);
CREATE INDEX idx_bookings_host_check_in ON bookings (host_id, check_in);
CREATE INDEX idx_bookings_listing_dates ON bookings (listing_id, check_in, check_out);
CREATE INDEX idx_bookings_expiring_payment
    ON bookings (payment_expires_at)
    WHERE status = 'PENDING_PAYMENT';
--rollback DROP TABLE bookings;

--changeset ninggiangboy:004-02-booking-nights
CREATE TABLE booking_nights (
    booking_id            UUID NOT NULL,
    stay_date             DATE NOT NULL,
    nightly_price_minor   BIGINT NOT NULL,
    discount_minor        BIGINT NOT NULL DEFAULT 0,
    tax_minor             BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_booking_nights PRIMARY KEY (booking_id, stay_date),
    CONSTRAINT fk_booking_nights_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT ck_booking_nights_money CHECK (
        nightly_price_minor >= 0 AND discount_minor >= 0 AND tax_minor >= 0
    )
);
--rollback DROP TABLE booking_nights;
