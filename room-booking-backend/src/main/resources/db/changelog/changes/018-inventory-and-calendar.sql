--liquibase formatted sql

-- This is where overselling is actually prevented, and it is prevented by the database rather than by
-- application logic, because application logic cannot see the transaction that is committing
-- alongside it.
--
-- Two defences, because the two inventory modes are genuinely different problems:
--
--   A UNIQUE_RENTAL is one indivisible place. Two stays overlapping on it is always wrong, whatever
--   the quantities say, so it is defended by a GiST exclusion constraint over (resource, daterange).
--   Postgres refuses the second overlapping claim outright.
--
--   A QUANTITY_POOL is a count of interchangeable rooms. Overlap is normal and expected; what is
--   wrong is the count exceeding capacity. It is defended by a per-date counter with a check
--   constraint, and callers must lock the date rows in a stable order before reading the counts.
--
-- An exclusion constraint cannot express the pooled rule and a counter cannot express the unique
-- rule, which is why both exist rather than one general mechanism.
--
-- The claim is the single thing that consumes inventory. A hold, a booking, a host block, and an
-- imported external reservation all become rows in the same table, so a host cannot block a night
-- that a guest is simultaneously holding, and an iCal import cannot quietly overwrite either.
--
-- Note on the shape: docs/features/availability-reservation-and-booking.md keys inventory on
-- listing_id, which was correct when a listing was the sellable thing. Migration 016 moved that
-- authority to accommodation_type, so inventory hangs off accommodation types here. A listing is a
-- presentation of inventory and can never promise a night.

--changeset ninggiangboy:018-01-inventory-resources
CREATE TABLE inventory_resources (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id   UUID NOT NULL,
    resource_type           VARCHAR(16) NOT NULL,
    physical_unit_id        UUID,
    sellable_quantity       INTEGER NOT NULL DEFAULT 1,
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_resources_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_inventory_resources_unit FOREIGN KEY (physical_unit_id)
        REFERENCES physical_units (id),
    CONSTRAINT ck_inventory_resources_type CHECK (
        resource_type IN ('SINGLE_UNIT', 'PHYSICAL_UNIT', 'QUANTITY_POOL')
    ),
    -- A PHYSICAL_UNIT resource is the calendar of one specific room and must name it. The other two
    -- kinds have no single room behind them and must not pretend to.
    CONSTRAINT ck_inventory_resources_unit_link CHECK (
        (resource_type = 'PHYSICAL_UNIT') = (physical_unit_id IS NOT NULL)
    ),
    -- Only a pool has a quantity above one. A single unit claiming capacity 2 would silently disable
    -- the exclusion constraint that is its entire defence.
    CONSTRAINT ck_inventory_resources_quantity CHECK (
        (resource_type = 'QUANTITY_POOL' AND sellable_quantity >= 1)
        OR (resource_type <> 'QUANTITY_POOL' AND sellable_quantity = 1)
    ),
    CONSTRAINT ck_inventory_resources_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_inventory_resources_version CHECK (version >= 0)
);

-- One resource per physical unit, and one non-unit resource per accommodation type. Two calendars for
-- the same sellable thing is the defect this prevents.
CREATE UNIQUE INDEX uk_inventory_resources_unit
    ON inventory_resources (physical_unit_id)
    WHERE physical_unit_id IS NOT NULL;

CREATE UNIQUE INDEX uk_inventory_resources_type_primary
    ON inventory_resources (accommodation_type_id)
    WHERE resource_type <> 'PHYSICAL_UNIT' AND status <> 'RETIRED';

CREATE INDEX idx_inventory_resources_type ON inventory_resources (accommodation_type_id, status);

-- Lets inventory_claims carry a copy of resource_type under a composite foreign key. The copy is
-- needed because PostgreSQL forbids a subquery in an index predicate, and the exclusion constraint
-- has to know which defence applies without looking the resource up. The composite key is what stops
-- the copy from ever disagreeing with the resource it names.
ALTER TABLE inventory_resources
    ADD CONSTRAINT uk_inventory_resources_id_type UNIQUE (id, resource_type);
--rollback DROP TABLE inventory_resources;

--changeset ninggiangboy:018-02-availability-days
CREATE TABLE availability_days (
    inventory_resource_id       UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    host_sellable               BOOLEAN NOT NULL DEFAULT true,
    sellable_quantity           INTEGER NOT NULL DEFAULT 1,
    held_quantity               INTEGER NOT NULL DEFAULT 0,
    booked_quantity             INTEGER NOT NULL DEFAULT 0,
    blocked_quantity            INTEGER NOT NULL DEFAULT 0,
    nightly_price_minor         BIGINT,
    currency                    VARCHAR(3),
    price_version               INTEGER NOT NULL DEFAULT 0,
    minimum_stay_on_arrival     SMALLINT,
    minimum_stay_through        SMALLINT,
    maximum_stay                SMALLINT,
    closed_to_arrival           BOOLEAN NOT NULL DEFAULT false,
    closed_to_departure         BOOLEAN NOT NULL DEFAULT false,
    closed_to_stay              BOOLEAN NOT NULL DEFAULT false,
    restriction_set_version     INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_availability_days PRIMARY KEY (inventory_resource_id, stay_date),
    CONSTRAINT fk_availability_days_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id) ON DELETE CASCADE,
    -- The pooled-capacity defence. Held, booked, and blocked nights together can never exceed what
    -- is sellable on that date. A caller that locks these rows in a stable order and then writes a
    -- claim cannot oversell, because the write itself fails.
    CONSTRAINT ck_availability_days_capacity CHECK (
        held_quantity + booked_quantity + blocked_quantity <= sellable_quantity
    ),
    CONSTRAINT ck_availability_days_quantities CHECK (
        sellable_quantity >= 0 AND held_quantity >= 0
        AND booked_quantity >= 0 AND blocked_quantity >= 0
    ),
    -- A price is meaningless without its currency, and a stored price that lost its currency is how
    -- a guest gets charged the right number of the wrong money.
    CONSTRAINT ck_availability_days_price CHECK (
        (nightly_price_minor IS NULL) = (currency IS NULL)
    ),
    CONSTRAINT ck_availability_days_price_value CHECK (
        nightly_price_minor IS NULL OR nightly_price_minor >= 0
    ),
    CONSTRAINT ck_availability_days_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_availability_days_stay_rules CHECK (
        (minimum_stay_on_arrival IS NULL OR minimum_stay_on_arrival > 0)
        AND (minimum_stay_through IS NULL OR minimum_stay_through > 0)
        AND (maximum_stay IS NULL OR maximum_stay > 0)
    ),
    CONSTRAINT ck_availability_days_versions CHECK (
        price_version >= 0 AND restriction_set_version >= 0
    ),
    CONSTRAINT ck_availability_days_version CHECK (version >= 0)
);

-- Search asks "which resources have every night in this range sellable", which reads by date first.
CREATE INDEX idx_availability_days_search
    ON availability_days (stay_date, inventory_resource_id)
    WHERE host_sellable = true AND closed_to_stay = false;

CREATE INDEX idx_availability_days_resource_range
    ON availability_days (inventory_resource_id, stay_date);
--rollback DROP TABLE availability_days;

--changeset ninggiangboy:018-03-inventory-claims
CREATE TABLE inventory_claims (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_resource_id   UUID NOT NULL,
    resource_type           VARCHAR(16) NOT NULL,
    stay_range              DATERANGE NOT NULL,
    claim_type              VARCHAR(24) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    quantity                INTEGER NOT NULL DEFAULT 1,
    hold_id                 UUID,
    booking_id              UUID,
    block_id                UUID,
    external_reservation_id UUID,
    expires_at              TIMESTAMPTZ,
    released_at             TIMESTAMPTZ,
    release_reason          VARCHAR(48),
    fencing_token           BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_claims_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    -- The composite reference is what keeps the denormalized resource_type honest: it cannot be set
    -- to a value the resource does not actually have, so the exclusion constraint below can trust it.
    CONSTRAINT fk_inventory_claims_resource_type FOREIGN KEY (inventory_resource_id, resource_type)
        REFERENCES inventory_resources (id, resource_type),
    CONSTRAINT ck_inventory_claims_resource_type CHECK (
        resource_type IN ('SINGLE_UNIT', 'PHYSICAL_UNIT', 'QUANTITY_POOL')
    ),
    CONSTRAINT ck_inventory_claims_type CHECK (
        claim_type IN ('HOLD', 'BOOKING', 'BLOCK', 'EXTERNAL_RESERVATION')
    ),
    CONSTRAINT ck_inventory_claims_status CHECK (
        status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_inventory_claims_quantity CHECK (quantity > 0),
    -- Half-open by construction: a checkout date is the next guest's check-in date, and an empty or
    -- inverted range would consume nothing while appearing to consume something.
    CONSTRAINT ck_inventory_claims_range CHECK (
        NOT isempty(stay_range)
        AND lower_inc(stay_range)
        AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_inventory_claims_release CHECK (
        (status IN ('RELEASED', 'EXPIRED')) = (released_at IS NOT NULL)
    ),
    -- A temporary claim must say when it lapses; a permanent one must not pretend to.
    CONSTRAINT ck_inventory_claims_expiry CHECK (
        (claim_type = 'HOLD') = (expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_claims_version CHECK (version >= 0),

    -- The unique-rental defence. Postgres refuses a second ACTIVE claim whose range overlaps an
    -- existing one on the same resource -- including across concurrent transactions, which is
    -- precisely what application-level checking cannot do. Released and expired claims are outside
    -- the predicate so a cancelled stay frees its nights immediately.
    --
    -- Pooled resources are deliberately exempt: overlap is normal for them, and their defence is the
    -- per-date capacity check on availability_days.
    CONSTRAINT ex_inventory_claims_no_overlap EXCLUDE USING gist (
        inventory_resource_id WITH =,
        stay_range WITH &&
    ) WHERE (status = 'ACTIVE' AND resource_type <> 'QUANTITY_POOL')
);
--rollback DROP TABLE inventory_claims;

--changeset ninggiangboy:018-04-inventory-holds
-- A hold is the promise that nights stay reservable while a guest finishes paying. It is temporary by
-- construction: max_expires_at bounds how far extensions can push it, so a stalled checkout cannot
-- keep inventory off the market indefinitely by repeatedly asking for more time.
--
-- The fencing token exists because expiry is a race. A sweeper deciding a hold has lapsed and a
-- checkout completing payment can act at the same instant; the token lets the later writer detect
-- that it is acting on a decision that has already been superseded.
CREATE TABLE inventory_holds (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id               VARCHAR(32) NOT NULL,
    accommodation_type_id   UUID NOT NULL,
    subject_id              UUID,
    quote_id                UUID,
    purpose                 VARCHAR(24) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    quantity                INTEGER NOT NULL DEFAULT 1,
    stay_range              DATERANGE NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL,
    max_expires_at          TIMESTAMPTZ NOT NULL,
    extension_count         SMALLINT NOT NULL DEFAULT 0,
    consumed_at             TIMESTAMPTZ,
    released_at             TIMESTAMPTZ,
    release_reason          VARCHAR(48),
    fencing_token           BIGINT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_inventory_holds_public_id UNIQUE (public_id),
    CONSTRAINT fk_inventory_holds_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_inventory_holds_purpose CHECK (
        purpose IN ('CHECKOUT', 'HOST_APPROVAL', 'MODIFICATION')
    ),
    CONSTRAINT ck_inventory_holds_status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'EXPIRED', 'RELEASED')
    ),
    CONSTRAINT ck_inventory_holds_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_holds_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    -- Extensions may move expires_at, but never past the ceiling set when the hold was created.
    CONSTRAINT ck_inventory_holds_expiry_ceiling CHECK (expires_at <= max_expires_at),
    CONSTRAINT ck_inventory_holds_consumed CHECK (
        (status = 'CONSUMED') = (consumed_at IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_holds_released CHECK (
        (status IN ('RELEASED', 'EXPIRED')) = (released_at IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_holds_extensions CHECK (extension_count >= 0),
    CONSTRAINT ck_inventory_holds_version CHECK (version >= 0)
);

-- The expiry sweep indexes the column with no time predicate: a partial index whose predicate moves
-- is not immutable, so the worker binds its own decision instant instead.
CREATE INDEX idx_inventory_holds_expiry
    ON inventory_holds (expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_inventory_holds_subject ON inventory_holds (subject_id, created_at DESC);

ALTER TABLE inventory_claims
    ADD CONSTRAINT fk_inventory_claims_hold FOREIGN KEY (hold_id) REFERENCES inventory_holds (id);
--rollback ALTER TABLE inventory_claims DROP CONSTRAINT fk_inventory_claims_hold;
--rollback DROP TABLE inventory_holds;

--changeset ninggiangboy:018-05-inventory-blocks
-- Blocks record why nights are unavailable when nobody has booked them: the host is staying there,
-- the room is being repainted, or another channel sold it. Provenance matters because the rules
-- differ -- an iCal import may replace the blocks it created on a previous run, but it must never
-- delete a block a host made by hand.
CREATE TABLE inventory_blocks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_resource_id   UUID NOT NULL,
    stay_range              DATERANGE NOT NULL,
    quantity                INTEGER NOT NULL DEFAULT 1,
    source_type             VARCHAR(24) NOT NULL,
    source_reference        VARCHAR(255),
    external_uid            VARCHAR(255),
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    reason_code             VARCHAR(48),
    private_note            TEXT,
    actor_type              VARCHAR(24) NOT NULL,
    actor_id                UUID,
    released_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_blocks_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT ck_inventory_blocks_source CHECK (
        source_type IN ('HOST', 'OPERATIONS', 'MAINTENANCE', 'ICAL', 'CHANNEL_MANAGER')
    ),
    CONSTRAINT ck_inventory_blocks_status CHECK (
        status IN ('ACTIVE', 'RELEASED', 'SUPERSEDED', 'CONFLICT')
    ),
    CONSTRAINT ck_inventory_blocks_actor CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER')
    ),
    CONSTRAINT ck_inventory_blocks_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_blocks_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_inventory_blocks_released CHECK (
        (status IN ('RELEASED', 'SUPERSEDED')) = (released_at IS NOT NULL)
    ),
    CONSTRAINT ck_inventory_blocks_version CHECK (version >= 0)
);

-- One live block per external event, so re-importing the same calendar updates rather than duplicates.
CREATE UNIQUE INDEX uk_inventory_blocks_external
    ON inventory_blocks (inventory_resource_id, source_type, external_uid)
    WHERE external_uid IS NOT NULL AND status = 'ACTIVE';

CREATE INDEX idx_inventory_blocks_resource
    ON inventory_blocks (inventory_resource_id, stay_range)
    WHERE status = 'ACTIVE';

ALTER TABLE inventory_claims
    ADD CONSTRAINT fk_inventory_claims_block FOREIGN KEY (block_id) REFERENCES inventory_blocks (id);
--rollback ALTER TABLE inventory_claims DROP CONSTRAINT fk_inventory_claims_block;
--rollback DROP TABLE inventory_blocks;

--changeset ninggiangboy:018-06-calendar-sync
-- External calendars are the main way a small host oversells: they list the same apartment here and
-- on another site, and whichever system learns about a booking last sells a night that is already
-- gone. Import cannot prevent that entirely -- the other site may take a booking a minute before our
-- sync runs -- so the design makes the conflict visible rather than pretending it cannot happen.
CREATE TABLE ical_connections (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_resource_id   UUID NOT NULL,
    direction               VARCHAR(16) NOT NULL,
    feed_url                TEXT,
    export_token_digest     CHAR(64),
    remote_label            VARCHAR(120),
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sync_interval_seconds   INTEGER NOT NULL DEFAULT 3600,
    last_success_at         TIMESTAMPTZ,
    last_failure_at         TIMESTAMPTZ,
    consecutive_failures    SMALLINT NOT NULL DEFAULT 0,
    next_sync_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ical_connections_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id) ON DELETE CASCADE,
    CONSTRAINT ck_ical_connections_direction CHECK (direction IN ('IMPORT', 'EXPORT')),
    CONSTRAINT ck_ical_connections_status CHECK (
        status IN ('ACTIVE', 'PAUSED', 'FAILING', 'DISABLED')
    ),
    -- An import needs somewhere to read from; an export needs a secret to authorise reads. The
    -- export token is stored only as a digest, because a leaked feed URL discloses a host's whole
    -- occupancy pattern.
    CONSTRAINT ck_ical_connections_import CHECK (
        direction <> 'IMPORT' OR feed_url IS NOT NULL
    ),
    CONSTRAINT ck_ical_connections_export CHECK (
        direction <> 'EXPORT' OR export_token_digest IS NOT NULL
    ),
    CONSTRAINT ck_ical_connections_token_digest CHECK (
        export_token_digest IS NULL OR export_token_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_ical_connections_interval CHECK (sync_interval_seconds >= 60),
    CONSTRAINT ck_ical_connections_failures CHECK (consecutive_failures >= 0),
    CONSTRAINT ck_ical_connections_version CHECK (version >= 0)
);

CREATE INDEX idx_ical_connections_due
    ON ical_connections (next_sync_at)
    WHERE status = 'ACTIVE' AND direction = 'IMPORT';

CREATE TABLE ical_sync_runs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ical_connection_id      UUID NOT NULL,
    started_at              TIMESTAMPTZ NOT NULL,
    finished_at             TIMESTAMPTZ,
    outcome                 VARCHAR(16),
    events_seen             INTEGER NOT NULL DEFAULT 0,
    blocks_created          INTEGER NOT NULL DEFAULT 0,
    blocks_released         INTEGER NOT NULL DEFAULT 0,
    conflicts_detected      INTEGER NOT NULL DEFAULT 0,
    feed_digest             CHAR(64),
    failure_class           VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ical_sync_runs_connection FOREIGN KEY (ical_connection_id)
        REFERENCES ical_connections (id) ON DELETE CASCADE,
    CONSTRAINT ck_ical_sync_runs_outcome CHECK (
        outcome IS NULL OR outcome IN ('SUCCESS', 'PARTIAL', 'FAILED', 'UNCHANGED')
    ),
    CONSTRAINT ck_ical_sync_runs_finish CHECK (
        (finished_at IS NULL) = (outcome IS NULL)
    ),
    CONSTRAINT ck_ical_sync_runs_counts CHECK (
        events_seen >= 0 AND blocks_created >= 0
        AND blocks_released >= 0 AND conflicts_detected >= 0
    ),
    CONSTRAINT ck_ical_sync_runs_digest CHECK (
        feed_digest IS NULL OR feed_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_ical_sync_runs_connection
    ON ical_sync_runs (ical_connection_id, started_at DESC);

CREATE TABLE external_reservations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_resource_id   UUID NOT NULL,
    ical_connection_id      UUID,
    external_uid            VARCHAR(255) NOT NULL,
    external_source         VARCHAR(48) NOT NULL,
    stay_range              DATERANGE NOT NULL,
    summary                 VARCHAR(255),
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    conflict_state          VARCHAR(16) NOT NULL DEFAULT 'NONE',
    first_seen_at           TIMESTAMPTZ NOT NULL,
    last_seen_at            TIMESTAMPTZ NOT NULL,
    cancelled_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_external_reservations_uid
        UNIQUE (inventory_resource_id, external_source, external_uid),
    CONSTRAINT fk_external_reservations_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT fk_external_reservations_connection FOREIGN KEY (ical_connection_id)
        REFERENCES ical_connections (id),
    CONSTRAINT ck_external_reservations_status CHECK (
        status IN ('ACTIVE', 'CANCELLED', 'SUPERSEDED')
    ),
    -- A conflict means the external calendar claims nights this platform has already sold. It is
    -- recorded rather than resolved automatically, because either answer -- cancelling our booking or
    -- ignoring theirs -- strands a real guest, and a person has to decide which.
    CONSTRAINT ck_external_reservations_conflict CHECK (
        conflict_state IN ('NONE', 'DETECTED', 'RESOLVED', 'IGNORED')
    ),
    CONSTRAINT ck_external_reservations_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_external_reservations_cancelled CHECK (
        (status = 'CANCELLED') = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_external_reservations_version CHECK (version >= 0)
);

CREATE INDEX idx_external_reservations_conflicts
    ON external_reservations (inventory_resource_id, first_seen_at)
    WHERE conflict_state = 'DETECTED';

ALTER TABLE inventory_claims
    ADD CONSTRAINT fk_inventory_claims_external FOREIGN KEY (external_reservation_id)
        REFERENCES external_reservations (id);
--rollback ALTER TABLE inventory_claims DROP CONSTRAINT fk_inventory_claims_external;
--rollback DROP TABLE external_reservations;
--rollback DROP TABLE ical_sync_runs;
--rollback DROP TABLE ical_connections;
