--liquibase formatted sql

-- A booking is a contract, and this migration exists so that the contract survives everything that
-- happens to it afterwards.
--
-- Four things force that shape:
--
--   A booking is not in one state. It is in several at once, and they move independently. A stay can
--   be confirmed while its payment is still authorising, cancelled while its refund is pending, and
--   completed while a damage claim is open. Collapsing those into a single status column forces
--   invented combinations like CONFIRMED_REFUND_FAILED, and every new intersection adds another.
--   So lifecycle, payment, stay, change, and refund are separate columns with separate vocabularies,
--   exactly as docs/features/availability-reservation-and-booking.md requires.
--
--   The terms a guest agreed to must still be readable years later, after the listing was renamed,
--   the rate plan retired, the house rules rewritten, and the host's address corrected. A booking
--   therefore snapshots what was agreed instead of joining to rows that have since moved on.
--
--   Money is written unsigned with an explicit direction, and a modification never edits a line -- it
--   supersedes it at a new revision. History that can be rewritten is not history.
--
--   A provisional booking holds real inventory. One without a deadline is an unsellable night that
--   nobody will ever reclaim, so the database refuses to store one.
--
-- Note on the shape: the plan for this migration sketched a single state machine
-- DRAFT -> HELD -> PAYMENT_PENDING -> CONFIRMED -> CHECKED_IN -> COMPLETED. That sequence is the
-- guest-facing journey, and it is preserved on booking_checkouts, which is the row the checkout UI
-- actually follows. The booking itself keeps the distinct dimensions, because the feature document
-- is explicit that the journey and the internal state are not the same thing.
--
-- Note on multi-room stays: migration 018 made QUANTITY_POOL supply real and quotes carry a
-- unit_quantity, so a booking can already span several rooms. Nights therefore hang off
-- booking_items rather than off the booking directly; a single-room stay simply has one item.

--changeset ninggiangboy:020-01-bookings
-- The contract itself. Every column here is either a fact about what was agreed, or one of the five
-- independent state dimensions.
CREATE TABLE bookings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    revision                    INTEGER NOT NULL DEFAULT 1,

    guest_account_holder_id     UUID NOT NULL,
    host_account_holder_id      UUID NOT NULL,

    property_id                 UUID NOT NULL,
    accommodation_type_id       UUID NOT NULL,
    listing_id                  UUID NOT NULL,
    rate_plan_id                UUID NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,

    quote_id                    UUID NOT NULL,
    flow_type                   VARCHAR(16) NOT NULL,
    source_channel              VARCHAR(32) NOT NULL DEFAULT 'WEB',

    -- The five dimensions. They are deliberately not one column.
    lifecycle_state             VARCHAR(16) NOT NULL DEFAULT 'PROVISIONAL',
    payment_state               VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    stay_state                  VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    change_state                VARCHAR(16) NOT NULL DEFAULT 'NONE',
    refund_state                VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',

    -- The stay as a civil fact, and the instants those civil facts resolved to at booking time.
    stay_range                  DATERANGE NOT NULL,
    check_in_date               DATE NOT NULL,
    check_out_date              DATE NOT NULL,
    property_time_zone          VARCHAR(64) NOT NULL,
    check_in_local_time         TIME NOT NULL,
    check_out_local_time        TIME NOT NULL,
    check_in_instant            TIMESTAMPTZ NOT NULL,
    check_out_instant           TIMESTAMPTZ NOT NULL,
    time_zone_version           VARCHAR(32),

    adult_count                 SMALLINT NOT NULL DEFAULT 1,
    child_count                 SMALLINT NOT NULL DEFAULT 0,
    infant_count                SMALLINT NOT NULL DEFAULT 0,
    pet_count                   SMALLINT NOT NULL DEFAULT 0,
    unit_quantity               SMALLINT NOT NULL DEFAULT 1,

    currency                    VARCHAR(3) NOT NULL,
    accommodation_amount_minor  BIGINT NOT NULL,
    discount_amount_minor       BIGINT NOT NULL DEFAULT 0,
    fee_amount_minor            BIGINT NOT NULL DEFAULT 0,
    tax_amount_minor            BIGINT NOT NULL DEFAULT 0,
    total_amount_minor          BIGINT NOT NULL,
    host_payout_estimate_minor  BIGINT,

    terms_version               VARCHAR(64),
    cancellation_policy_key     VARCHAR(48) NOT NULL,
    cancellation_policy_version VARCHAR(64),
    restriction_set_version     INTEGER,
    price_version               INTEGER,

    hold_expires_at             TIMESTAMPTZ,
    confirmed_at                TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    cancelled_by                VARCHAR(16),
    cancellation_reason_code    VARCHAR(64),
    checked_in_at               TIMESTAMPTZ,
    checked_out_at              TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_bookings_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_bookings_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_bookings_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_bookings_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_bookings_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_bookings_rate_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id),
    CONSTRAINT fk_bookings_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_bookings_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),

    -- One accepted offer becomes one booking. Without this, a retried acceptance that slipped past
    -- the idempotency record would sell the same nights twice under two booking rows.
    CONSTRAINT uk_bookings_quote UNIQUE (quote_id),
    CONSTRAINT uk_bookings_public_id UNIQUE (public_id),

    CONSTRAINT ck_bookings_flow CHECK (flow_type IN ('INSTANT', 'REQUEST')),
    CONSTRAINT ck_bookings_source CHECK (
        source_channel IN ('WEB', 'IOS', 'ANDROID', 'PARTNER_API', 'SUPPORT', 'CHANNEL_MANAGER')
    ),
    CONSTRAINT ck_bookings_lifecycle CHECK (
        lifecycle_state IN ('PROVISIONAL', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')
    ),
    CONSTRAINT ck_bookings_payment_state CHECK (
        payment_state IN ('NOT_STARTED', 'REQUIRES_ACTION', 'AUTHORIZED', 'CAPTURED',
                          'FAILED', 'VOIDED', 'REFUNDED')
    ),
    CONSTRAINT ck_bookings_stay_state CHECK (
        stay_state IN ('NOT_STARTED', 'CHECKED_IN', 'CHECKED_OUT', 'NO_SHOW')
    ),
    CONSTRAINT ck_bookings_change_state CHECK (
        change_state IN ('NONE', 'PENDING', 'APPLIED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT ck_bookings_refund_state CHECK (
        refund_state IN ('NOT_REQUIRED', 'PENDING', 'PARTIAL', 'REFUNDED', 'FAILED')
    ),

    CONSTRAINT ck_bookings_dates CHECK (check_out_date > check_in_date),
    -- The range and the two dates are the same fact stored twice, because the exclusion constraint
    -- needs a range and every report needs the dates. If they ever disagree the booking is corrupt
    -- in a way no later read would notice, so they are forced to agree at write time.
    CONSTRAINT ck_bookings_range_agrees CHECK (
        stay_range = daterange(check_in_date, check_out_date, '[)')
    ),
    CONSTRAINT ck_bookings_instants CHECK (check_out_instant > check_in_instant),

    CONSTRAINT ck_bookings_party CHECK (
        adult_count > 0 AND child_count >= 0 AND infant_count >= 0
            AND pet_count >= 0 AND unit_quantity > 0
    ),

    CONSTRAINT ck_bookings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_bookings_amounts CHECK (
        accommodation_amount_minor >= 0 AND discount_amount_minor >= 0
            AND fee_amount_minor >= 0 AND tax_amount_minor >= 0 AND total_amount_minor >= 0
            AND (host_payout_estimate_minor IS NULL OR host_payout_estimate_minor >= 0)
    ),
    CONSTRAINT ck_bookings_total CHECK (
        total_amount_minor = accommodation_amount_minor
            - discount_amount_minor + fee_amount_minor + tax_amount_minor
    ),

    -- A provisional booking is consuming nights nobody has paid for. Without a deadline those nights
    -- are never reclaimed by any sweeper, because a sweeper can only act on a time it can read.
    CONSTRAINT ck_bookings_provisional_deadline CHECK (
        lifecycle_state <> 'PROVISIONAL' OR hold_expires_at IS NOT NULL
    ),
    CONSTRAINT ck_bookings_confirmation CHECK (
        lifecycle_state NOT IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW') OR confirmed_at IS NOT NULL
    ),
    CONSTRAINT ck_bookings_cancellation CHECK (
        (lifecycle_state = 'CANCELLED') = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_bookings_cancelled_by CHECK (
        (cancelled_at IS NULL) = (cancelled_by IS NULL)
    ),
    CONSTRAINT ck_bookings_cancelled_by_party CHECK (
        cancelled_by IS NULL OR cancelled_by IN ('GUEST', 'HOST', 'PLATFORM', 'SYSTEM')
    ),
    CONSTRAINT ck_bookings_completion CHECK (
        lifecycle_state <> 'COMPLETED' OR completed_at IS NOT NULL
    ),
    -- No-show is a decision about a stay, so the two dimensions must agree when it is taken.
    CONSTRAINT ck_bookings_no_show CHECK (
        lifecycle_state <> 'NO_SHOW' OR stay_state = 'NO_SHOW'
    ),
    CONSTRAINT ck_bookings_arrival CHECK (
        stay_state NOT IN ('CHECKED_IN', 'CHECKED_OUT') OR checked_in_at IS NOT NULL
    ),
    CONSTRAINT ck_bookings_departure CHECK (
        (stay_state = 'CHECKED_OUT') = (checked_out_at IS NOT NULL)
    ),
    -- A guest cannot arrive at a stay that was never confirmed.
    CONSTRAINT ck_bookings_stay_needs_confirmation CHECK (
        stay_state = 'NOT_STARTED' OR lifecycle_state <> 'PROVISIONAL'
    ),
    CONSTRAINT ck_bookings_revision CHECK (revision > 0),
    CONSTRAINT ck_bookings_version CHECK (version >= 0)
);

CREATE INDEX idx_bookings_guest ON bookings (guest_account_holder_id, check_in_date DESC);
CREATE INDEX idx_bookings_host ON bookings (host_account_holder_id, check_in_date DESC);
CREATE INDEX idx_bookings_type_stay ON bookings (accommodation_type_id, check_in_date);
-- The sweeper's index. The predicate names no time function on purpose: a partial index whose
-- predicate depended on now() would silently stop matching rows as the clock moved.
CREATE INDEX idx_bookings_provisional_expiry ON bookings (hold_expires_at)
    WHERE lifecycle_state = 'PROVISIONAL';
CREATE INDEX idx_bookings_arrivals ON bookings (check_in_date)
    WHERE lifecycle_state = 'CONFIRMED';
--rollback DROP TABLE bookings;

--changeset ninggiangboy:020-02-booking-checkouts
-- The guest's attempt to turn an offer into a contract. It is a separate row from the booking
-- because most attempts never become one, and an abandoned checkout must not leave a half-built
-- booking behind for support to explain.
CREATE TABLE booking_checkouts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id               VARCHAR(32) NOT NULL,
    idempotency_key         VARCHAR(128) NOT NULL,
    guest_account_holder_id UUID NOT NULL,
    listing_id              UUID NOT NULL,
    quote_id                UUID NOT NULL,
    inventory_hold_id       UUID,
    booking_id              UUID,
    flow_type               VARCHAR(16) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    accepted_terms_version  VARCHAR(64),
    terms_accepted_at       TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ NOT NULL,
    succeeded_at            TIMESTAMPTZ,
    failure_code            VARCHAR(48),
    failure_reason          VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_checkouts_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_booking_checkouts_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_booking_checkouts_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_booking_checkouts_hold FOREIGN KEY (inventory_hold_id)
        REFERENCES inventory_holds (id),
    CONSTRAINT fk_booking_checkouts_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_booking_checkouts_public_id UNIQUE (public_id),
    CONSTRAINT uk_booking_checkouts_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_booking_checkouts_flow CHECK (flow_type IN ('INSTANT', 'REQUEST')),
    -- This is the guest-facing journey the plan described, kept on the row the UI follows.
    CONSTRAINT ck_booking_checkouts_status CHECK (
        status IN ('DRAFT', 'HELD', 'PAYMENT_PENDING', 'HOST_PENDING',
                   'SUCCEEDED', 'EXPIRED', 'FAILED')
    ),
    -- Success is defined by having produced a booking, not by a status word.
    CONSTRAINT ck_booking_checkouts_success CHECK (
        (status = 'SUCCEEDED') = (booking_id IS NOT NULL AND succeeded_at IS NOT NULL)
    ),
    CONSTRAINT ck_booking_checkouts_failure CHECK (
        status <> 'FAILED' OR failure_code IS NOT NULL
    ),
    CONSTRAINT ck_booking_checkouts_terms CHECK (
        (accepted_terms_version IS NULL) = (terms_accepted_at IS NULL)
    ),
    -- Past DRAFT the guest has accepted terms; a held stay with no recorded acceptance is a contract
    -- nobody can prove was agreed to.
    CONSTRAINT ck_booking_checkouts_terms_required CHECK (
        status = 'DRAFT' OR terms_accepted_at IS NOT NULL
    ),
    CONSTRAINT ck_booking_checkouts_version CHECK (version >= 0)
);

CREATE INDEX idx_booking_checkouts_guest
    ON booking_checkouts (guest_account_holder_id, created_at DESC);
CREATE INDEX idx_booking_checkouts_live ON booking_checkouts (expires_at)
    WHERE status IN ('DRAFT', 'HELD', 'PAYMENT_PENDING', 'HOST_PENDING');
--rollback DROP TABLE booking_checkouts;

--changeset ninggiangboy:020-03-inventory-claim-booking-link
-- Migration 018 gave inventory_claims a booking_id it could not constrain, because bookings did not
-- exist yet. Until now nothing stopped a claim citing a booking that was never written, which is
-- precisely the state that makes a night look sold with no contract behind it. The reference is
-- closed here, where the target it needs finally exists.
ALTER TABLE inventory_claims
    ADD CONSTRAINT fk_inventory_claims_booking FOREIGN KEY (booking_id) REFERENCES bookings (id);
--rollback ALTER TABLE inventory_claims DROP CONSTRAINT fk_inventory_claims_booking;

--changeset ninggiangboy:020-04-booking-items
-- One claimed resource within a booking. A whole-home stay has exactly one; three interchangeable
-- hotel rooms have three, so that cancelling one of them releases one room's nights and one room's
-- money rather than approximating both.
CREATE TABLE booking_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    item_number                 SMALLINT NOT NULL,
    inventory_resource_id       UUID NOT NULL,
    rate_plan_id                UUID NOT NULL,
    physical_unit_id            UUID,
    stay_range                  DATERANGE NOT NULL,
    quantity                    SMALLINT NOT NULL DEFAULT 1,
    adult_count                 SMALLINT NOT NULL DEFAULT 1,
    child_count                 SMALLINT NOT NULL DEFAULT 0,
    infant_count                SMALLINT NOT NULL DEFAULT 0,
    item_status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    currency                    VARCHAR(3) NOT NULL,
    accommodation_amount_minor  BIGINT NOT NULL,
    total_amount_minor          BIGINT NOT NULL,
    cancelled_at                TIMESTAMPTZ,
    cancellation_reason_code    VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_items_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_items_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT fk_booking_items_rate_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id),
    CONSTRAINT fk_booking_items_unit FOREIGN KEY (physical_unit_id) REFERENCES physical_units (id),
    CONSTRAINT uk_booking_items_number UNIQUE (booking_id, item_number),
    CONSTRAINT ck_booking_items_number CHECK (item_number > 0),
    CONSTRAINT ck_booking_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_booking_items_party CHECK (
        adult_count > 0 AND child_count >= 0 AND infant_count >= 0
    ),
    CONSTRAINT ck_booking_items_status CHECK (
        item_status IN ('ACTIVE', 'CANCELLED', 'MODIFIED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_booking_items_cancellation CHECK (
        (item_status = 'CANCELLED') = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_booking_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_items_amounts CHECK (
        accommodation_amount_minor >= 0 AND total_amount_minor >= 0
    ),
    CONSTRAINT ck_booking_items_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_booking_items_version CHECK (version >= 0)
);

CREATE INDEX idx_booking_items_booking ON booking_items (booking_id, item_number);
CREATE INDEX idx_booking_items_resource ON booking_items (inventory_resource_id);
--rollback DROP TABLE booking_items;

--changeset ninggiangboy:020-05-booking-nights
-- What was agreed for each night, kept per night rather than as a total, because a shortened stay,
-- a partial refund, and a per-night tax all need to name the exact nights they concern.
CREATE TABLE booking_nights (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    booking_item_id             UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    inventory_resource_id       UUID NOT NULL,
    quantity                    SMALLINT NOT NULL DEFAULT 1,
    currency                    VARCHAR(3) NOT NULL,
    accommodation_amount_minor  BIGINT NOT NULL,
    discount_amount_minor       BIGINT NOT NULL DEFAULT 0,
    fee_amount_minor            BIGINT NOT NULL DEFAULT 0,
    tax_amount_minor            BIGINT NOT NULL DEFAULT 0,
    total_amount_minor          BIGINT NOT NULL,
    price_rule_version_id       UUID,
    daily_price_component_id    UUID,
    is_released                 BOOLEAN NOT NULL DEFAULT false,
    released_at                 TIMESTAMPTZ,
    release_reason              VARCHAR(48),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_nights_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_nights_item FOREIGN KEY (booking_item_id)
        REFERENCES booking_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_nights_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT fk_booking_nights_rule_version FOREIGN KEY (price_rule_version_id)
        REFERENCES price_rule_versions (id),
    CONSTRAINT fk_booking_nights_component FOREIGN KEY (daily_price_component_id)
        REFERENCES daily_price_components (id),
    CONSTRAINT uk_booking_nights_date UNIQUE (booking_item_id, stay_date),
    CONSTRAINT ck_booking_nights_quantity CHECK (quantity > 0),
    CONSTRAINT ck_booking_nights_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_nights_amounts CHECK (
        accommodation_amount_minor >= 0 AND discount_amount_minor >= 0
            AND fee_amount_minor >= 0 AND tax_amount_minor >= 0 AND total_amount_minor >= 0
    ),
    CONSTRAINT ck_booking_nights_total CHECK (
        total_amount_minor = accommodation_amount_minor
            - discount_amount_minor + fee_amount_minor + tax_amount_minor
    ),
    -- A released night keeps its money history; only its claim on inventory ends. Shortening a stay
    -- must never delete the nights that were once sold.
    CONSTRAINT ck_booking_nights_release CHECK (is_released = (released_at IS NOT NULL)),
    CONSTRAINT ck_booking_nights_version CHECK (version >= 0)
);

CREATE INDEX idx_booking_nights_booking ON booking_nights (booking_id, stay_date);
CREATE INDEX idx_booking_nights_occupancy ON booking_nights (inventory_resource_id, stay_date)
    WHERE is_released = false;
--rollback DROP TABLE booking_nights;

--changeset ninggiangboy:020-06-booking-line-items
-- The money of the contract, line by line, at a revision. A modification writes a new revision
-- rather than editing these rows, so the question "what did this guest owe in March" always has an
-- answer.
CREATE TABLE booking_line_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              UUID NOT NULL,
    booking_item_id         UUID,
    revision                INTEGER NOT NULL DEFAULT 1,
    line_number             SMALLINT NOT NULL,
    line_type               VARCHAR(32) NOT NULL,
    component_code          VARCHAR(48) NOT NULL,
    description_key         VARCHAR(128),
    direction               VARCHAR(8) NOT NULL,
    quantity                NUMERIC(10,3) NOT NULL DEFAULT 1,
    unit_amount_minor       BIGINT NOT NULL,
    amount_minor            BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    payer_role              VARCHAR(16) NOT NULL,
    beneficiary_role        VARCHAR(16) NOT NULL,
    funder_role             VARCHAR(16),
    supplier_role           VARCHAR(16),
    tax_treatment           VARCHAR(24) NOT NULL DEFAULT 'TAXABLE',
    refundability           VARCHAR(24) NOT NULL DEFAULT 'POLICY_BASED',
    commission_basis        BOOLEAN NOT NULL DEFAULT false,
    source_quote_line_id    UUID,
    price_rule_version_id   UUID,
    promotion_version_id    UUID,
    tax_calculation_line_id UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_booking_line_items_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_line_items_item FOREIGN KEY (booking_item_id)
        REFERENCES booking_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_line_items_quote_line FOREIGN KEY (source_quote_line_id)
        REFERENCES quote_line_items (id),
    CONSTRAINT fk_booking_line_items_rule_version FOREIGN KEY (price_rule_version_id)
        REFERENCES price_rule_versions (id),
    CONSTRAINT fk_booking_line_items_promotion_version FOREIGN KEY (promotion_version_id)
        REFERENCES promotion_versions (id),
    CONSTRAINT fk_booking_line_items_tax_line FOREIGN KEY (tax_calculation_line_id)
        REFERENCES tax_calculation_lines (id),
    CONSTRAINT uk_booking_line_items_number UNIQUE (booking_id, revision, line_number),
    CONSTRAINT ck_booking_line_items_revision CHECK (revision > 0),
    CONSTRAINT ck_booking_line_items_number CHECK (line_number > 0),
    CONSTRAINT ck_booking_line_items_type CHECK (
        line_type IN ('ACCOMMODATION', 'CLEANING_FEE', 'SERVICE_FEE', 'HOST_FEE',
                      'EXTRA_GUEST_FEE', 'PET_FEE', 'RESORT_FEE', 'DISCOUNT', 'PROMOTION',
                      'TAX', 'SECURITY_DEPOSIT', 'OTHER')
    ),
    -- Amounts are unsigned and the direction carries the sign. A bare negative number cannot say
    -- whether it is a discount, a refund, a correction, or a defect.
    CONSTRAINT ck_booking_line_items_direction CHECK (direction IN ('CHARGE', 'CREDIT')),
    CONSTRAINT ck_booking_line_items_amounts CHECK (
        unit_amount_minor >= 0 AND amount_minor >= 0 AND quantity > 0
    ),
    CONSTRAINT ck_booking_line_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_line_items_payer CHECK (
        payer_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_booking_line_items_beneficiary CHECK (
        beneficiary_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_booking_line_items_funder CHECK (
        funder_role IS NULL OR funder_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_booking_line_items_supplier CHECK (
        supplier_role IS NULL OR supplier_role IN ('HOST', 'PLATFORM', 'THIRD_PARTY')
    ),
    CONSTRAINT ck_booking_line_items_tax_treatment CHECK (
        tax_treatment IN ('TAXABLE', 'EXEMPT', 'ZERO_RATED', 'OUT_OF_SCOPE', 'TAX_LINE')
    ),
    CONSTRAINT ck_booking_line_items_refundability CHECK (
        refundability IN ('REFUNDABLE', 'NON_REFUNDABLE', 'POLICY_BASED')
    ),
    -- A tax line is never itself taxable and never enters a commission base, or the platform would
    -- earn commission on the government's money.
    CONSTRAINT ck_booking_line_items_tax_line CHECK (
        line_type <> 'TAX' OR (tax_treatment = 'TAX_LINE' AND commission_basis = false)
    ),
    CONSTRAINT ck_booking_line_items_discount_source CHECK (
        line_type NOT IN ('DISCOUNT', 'PROMOTION')
            OR price_rule_version_id IS NOT NULL OR promotion_version_id IS NOT NULL
    )
);

CREATE INDEX idx_booking_line_items_booking
    ON booking_line_items (booking_id, revision, line_number);
--rollback DROP TABLE booking_line_items;

--changeset ninggiangboy:020-07-booking-financial-snapshots
-- What the contract was worth at each revision. Written once per revision and never revised, because
-- it is the row finance, tax, and support all cite when they disagree about an amount.
CREATE TABLE booking_financial_snapshots (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    revision                    INTEGER NOT NULL,
    reason                      VARCHAR(24) NOT NULL,
    quote_id                    UUID,
    currency                    VARCHAR(3) NOT NULL,
    accommodation_amount_minor  BIGINT NOT NULL,
    discount_amount_minor       BIGINT NOT NULL DEFAULT 0,
    fee_amount_minor            BIGINT NOT NULL DEFAULT 0,
    tax_amount_minor            BIGINT NOT NULL DEFAULT 0,
    total_amount_minor          BIGINT NOT NULL,
    guest_paid_amount_minor     BIGINT NOT NULL DEFAULT 0,
    platform_fee_amount_minor   BIGINT NOT NULL DEFAULT 0,
    host_payout_amount_minor    BIGINT NOT NULL DEFAULT 0,
    tax_calculation_id          UUID,
    calculation_hash            CHAR(64) NOT NULL,
    captured_at                 TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: this table is append-only evidence, so a cascade would
    -- be a promise the row trigger refuses to keep. A booking with evidence cannot be deleted.
    CONSTRAINT fk_booking_financial_snapshots_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_booking_financial_snapshots_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_booking_financial_snapshots_tax FOREIGN KEY (tax_calculation_id)
        REFERENCES tax_calculations (id),
    CONSTRAINT uk_booking_financial_snapshots_revision UNIQUE (booking_id, revision),
    CONSTRAINT ck_booking_financial_snapshots_revision CHECK (revision > 0),
    CONSTRAINT ck_booking_financial_snapshots_reason CHECK (
        reason IN ('CONFIRMATION', 'MODIFICATION', 'CANCELLATION', 'ADJUSTMENT', 'COMPLETION')
    ),
    CONSTRAINT ck_booking_financial_snapshots_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_financial_snapshots_amounts CHECK (
        accommodation_amount_minor >= 0 AND discount_amount_minor >= 0
            AND fee_amount_minor >= 0 AND tax_amount_minor >= 0 AND total_amount_minor >= 0
            AND guest_paid_amount_minor >= 0 AND platform_fee_amount_minor >= 0
            AND host_payout_amount_minor >= 0
    ),
    CONSTRAINT ck_booking_financial_snapshots_total CHECK (
        total_amount_minor = accommodation_amount_minor
            - discount_amount_minor + fee_amount_minor + tax_amount_minor
    )
);

CREATE INDEX idx_booking_financial_snapshots_booking
    ON booking_financial_snapshots (booking_id, revision DESC);
--rollback DROP TABLE booking_financial_snapshots;

--changeset ninggiangboy:020-08-booking-supply-snapshots
-- What the guest was actually shown and the host actually offered. Kept because a listing can be
-- renamed, re-photographed, re-addressed, or archived, and none of that may change what a past
-- booking says was booked.
CREATE TABLE booking_supply_snapshots (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              UUID NOT NULL,
    listing_title           VARCHAR(255) NOT NULL,
    property_name           VARCHAR(255),
    room_type               VARCHAR(32) NOT NULL,
    space_sharing           VARCHAR(16) NOT NULL,
    rate_plan_name          VARCHAR(160) NOT NULL,
    meal_plan               VARCHAR(24) NOT NULL,
    address_line            VARCHAR(255),
    locality                VARCHAR(128),
    region                  VARCHAR(128),
    postal_code             VARCHAR(24),
    country_code            VARCHAR(2) NOT NULL,
    latitude                NUMERIC(9,6),
    longitude               NUMERIC(9,6),
    time_zone               VARCHAR(64) NOT NULL,
    display_locale          VARCHAR(35) NOT NULL,
    display_payload         JSONB,
    captured_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_booking_supply_snapshots_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT uk_booking_supply_snapshots_booking UNIQUE (booking_id),
    CONSTRAINT ck_booking_supply_snapshots_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_booking_supply_snapshots_coordinates CHECK (
        (latitude IS NULL) = (longitude IS NULL)
            AND (latitude IS NULL OR (latitude BETWEEN -90 AND 90))
            AND (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
    )
);
--rollback DROP TABLE booking_supply_snapshots;

--changeset ninggiangboy:020-09-booking-party-snapshots
-- Who the parties were at booking time. Contact details are referenced, not copied: a booking must
-- not become a second, stale, unmanaged store of personal data that erasure requests cannot reach.
CREATE TABLE booking_party_snapshots (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              UUID NOT NULL,
    party_role              VARCHAR(16) NOT NULL,
    account_holder_id       UUID NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    preferred_locale        VARCHAR(35),
    contact_channel_id      UUID,
    organization_id         UUID,
    captured_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_booking_party_snapshots_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_party_snapshots_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_booking_party_snapshots_channel FOREIGN KEY (contact_channel_id)
        REFERENCES contact_channels (id),
    CONSTRAINT uk_booking_party_snapshots_role UNIQUE (booking_id, party_role),
    CONSTRAINT ck_booking_party_snapshots_role CHECK (
        party_role IN ('GUEST', 'HOST', 'CO_HOST', 'PRIMARY_GUEST')
    )
);
--rollback DROP TABLE booking_party_snapshots;

--changeset ninggiangboy:020-10-booking-policy-acceptances
-- Evidence that the guest agreed to each specific version of each specific policy. A booking that
-- cites a cancellation policy without evidence of acceptance is a term the platform cannot enforce
-- and a refund argument it cannot win.
CREATE TABLE booking_policy_acceptances (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    booking_checkout_id         UUID,
    policy_type                 VARCHAR(32) NOT NULL,
    policy_key                  VARCHAR(96) NOT NULL,
    policy_version              VARCHAR(64) NOT NULL,
    disclosure_digest           CHAR(64) NOT NULL,
    accepted_by_account_holder_id UUID NOT NULL,
    acceptance_channel          VARCHAR(32) NOT NULL,
    locale                      VARCHAR(35) NOT NULL,
    client_ip_digest            CHAR(64),
    user_agent_digest           CHAR(64),
    accepted_at                 TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: this table is append-only evidence, so a cascade would
    -- be a promise the row trigger refuses to keep. A booking with evidence cannot be deleted.
    CONSTRAINT fk_booking_policy_acceptances_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_booking_policy_acceptances_checkout FOREIGN KEY (booking_checkout_id)
        REFERENCES booking_checkouts (id),
    CONSTRAINT fk_booking_policy_acceptances_actor FOREIGN KEY (accepted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_booking_policy_acceptances_policy
        UNIQUE (booking_id, policy_type, policy_version),
    CONSTRAINT ck_booking_policy_acceptances_type CHECK (
        policy_type IN ('HOUSE_RULES', 'CANCELLATION', 'TERMS_OF_SERVICE', 'PRIVACY',
                        'SAFETY', 'GUEST_STANDARDS', 'DAMAGE_POLICY')
    ),
    CONSTRAINT ck_booking_policy_acceptances_channel CHECK (
        acceptance_channel IN ('WEB', 'IOS', 'ANDROID', 'PARTNER_API', 'SUPPORT')
    )
);

CREATE INDEX idx_booking_policy_acceptances_booking
    ON booking_policy_acceptances (booking_id, policy_type);
--rollback DROP TABLE booking_policy_acceptances;

--changeset ninggiangboy:020-11-booking-state-transitions
-- Every move of every dimension, with who moved it and why. The dimension is named explicitly so
-- that a payment moving to CAPTURED and a stay moving to CHECKED_IN are legible as different kinds
-- of event rather than two rows with unrelated words in the same column.
CREATE TABLE booking_state_transitions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL,
    sequence_number     BIGINT NOT NULL,
    dimension           VARCHAR(16) NOT NULL,
    from_state          VARCHAR(24),
    to_state            VARCHAR(24) NOT NULL,
    transition_code     VARCHAR(64) NOT NULL,
    actor_type          VARCHAR(24) NOT NULL,
    actor_id            UUID,
    reason_code         VARCHAR(64),
    command_id          VARCHAR(64),
    correlation_id      VARCHAR(64) NOT NULL,
    causation_id        VARCHAR(64),
    metadata            JSONB,
    occurred_at         TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: this table is append-only evidence, so a cascade would
    -- be a promise the row trigger refuses to keep. A booking with evidence cannot be deleted.
    CONSTRAINT fk_booking_state_transitions_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT uk_booking_state_transitions_sequence UNIQUE (booking_id, sequence_number),
    CONSTRAINT ck_booking_state_transitions_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_booking_state_transitions_dimension CHECK (
        dimension IN ('LIFECYCLE', 'PAYMENT', 'STAY', 'CHANGE', 'REFUND')
    ),
    -- A transition from a state to itself records nothing and hides a retry that should have been
    -- recognised as a duplicate.
    CONSTRAINT ck_booking_state_transitions_moves CHECK (
        from_state IS NULL OR from_state <> to_state
    ),
    CONSTRAINT ck_booking_state_transitions_actor CHECK (
        actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    )
);

CREATE INDEX idx_booking_state_transitions_booking
    ON booking_state_transitions (booking_id, sequence_number DESC);
CREATE INDEX idx_booking_state_transitions_dimension
    ON booking_state_transitions (booking_id, dimension, occurred_at DESC);
--rollback DROP TABLE booking_state_transitions;

--changeset ninggiangboy:020-12-booking-timeline-entries
-- The human-readable account of the booking, with an explicit audience. Internal notes and guest
-- messages living in one table without a visibility column is how a support note ends up rendered
-- in a guest's itinerary.
CREATE TABLE booking_timeline_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL,
    sequence_number     BIGINT NOT NULL,
    entry_code          VARCHAR(64) NOT NULL,
    visibility          VARCHAR(16) NOT NULL,
    title_key           VARCHAR(128) NOT NULL,
    body_key            VARCHAR(128),
    parameters          JSONB,
    actor_type          VARCHAR(24) NOT NULL,
    actor_id            UUID,
    occurred_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_booking_timeline_entries_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id) ON DELETE CASCADE,
    CONSTRAINT uk_booking_timeline_entries_sequence UNIQUE (booking_id, sequence_number),
    CONSTRAINT ck_booking_timeline_entries_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_booking_timeline_entries_visibility CHECK (
        visibility IN ('GUEST', 'HOST', 'BOTH', 'INTERNAL')
    ),
    CONSTRAINT ck_booking_timeline_entries_actor CHECK (
        actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    )
);

CREATE INDEX idx_booking_timeline_entries_booking
    ON booking_timeline_entries (booking_id, sequence_number DESC);
--rollback DROP TABLE booking_timeline_entries;

--changeset ninggiangboy:020-13-booking-evidence-append-only splitStatements:false
-- Transitions, money snapshots, and policy acceptances are the three records a dispute is decided
-- from. Protecting them only in application code leaves every future code path free to rewrite the
-- evidence, and the one path that forgets is the one that matters. Corrections are appended: a
-- wrong transition is followed by a compensating one, a wrong amount by a new revision.
CREATE FUNCTION booking_evidence_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; append a correcting record instead of altering the evidence', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_state_transitions_append_only
    BEFORE UPDATE OR DELETE ON booking_state_transitions
    FOR EACH ROW EXECUTE FUNCTION booking_evidence_reject_mutation();

CREATE TRIGGER trg_booking_financial_snapshots_append_only
    BEFORE UPDATE OR DELETE ON booking_financial_snapshots
    FOR EACH ROW EXECUTE FUNCTION booking_evidence_reject_mutation();

CREATE TRIGGER trg_booking_policy_acceptances_append_only
    BEFORE UPDATE OR DELETE ON booking_policy_acceptances
    FOR EACH ROW EXECUTE FUNCTION booking_evidence_reject_mutation();
--rollback DROP TRIGGER trg_booking_policy_acceptances_append_only ON booking_policy_acceptances;
--rollback DROP TRIGGER trg_booking_financial_snapshots_append_only ON booking_financial_snapshots;
--rollback DROP TRIGGER trg_booking_state_transitions_append_only ON booking_state_transitions;
--rollback DROP FUNCTION booking_evidence_reject_mutation();
