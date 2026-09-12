--liquibase formatted sql

-- A cancellation is a recalculation of a contract, and this migration exists so that the
-- recalculation can be shown to the person it took money from.
--
-- Six things force the shape:
--
--   The rule that was quoted is the rule that applies. A guest agreed to a cancellation policy at a
--   moment in time, in a language, with a stated check-in wall clock. If the policy family is later
--   edited, the booking must still be settled under the words the guest was shown. So a policy
--   version is immutable once published, its disclosure text is immutable once approved, and the
--   booking stores which version it accepted rather than a label that can be redefined underneath it.
--
--   A booking's terms are a sequence, not a value. A modification does not edit dates, nights and
--   amounts in place -- it commits a new revision that supersedes the previous one and names it as
--   its predecessor. Exactly one committed revision is current, the chain cannot fork, and a
--   committed revision cannot be altered afterwards. Without this, a cancellation calculated after a
--   modification would settle terms that no longer describe what either party agreed to.
--
--   Deciding is separate from doing. A cancellation decision is one immutable arithmetic result:
--   what was retained, what is refunded, who funds it, what tax moves. Releasing nights, returning
--   money, recovering from a host and adjusting a promotion are four downstream effects in four
--   other domains, each addressed by an instruction row with its own idempotency identity. This
--   domain may project their status and may never mark them successful.
--
--   A preview is a promise with an expiry. The number a guest is shown before confirming must be the
--   number they are charged, so the decision cites the preview it accepted, and the preview carries
--   the input hash, the evaluator version, and the instant it stops being usable. A decision built
--   on a stale preview is a different calculation wearing the old one's number.
--
--   An allocation that does not add up is a defect nobody notices. The decision total and its lines
--   are written in one transaction by one service, so the sum rule is enforced by a deferred
--   constraint trigger that fires at COMMIT, not left to the code holding the lock.
--
--   Money is unsigned with an explicit direction and an explicit funder. "Who pays for this
--   goodwill" is the question every cancellation argument turns on, and a bare negative number in an
--   amount column cannot answer it.
--
-- Note on the sum rule: the feature document states that cross-row monetary sums cannot safely be
-- expressed and must be left to locked service validation plus scheduled reconciliation. That is
-- true of ordinary CHECK constraints, which see one row. It is not true of a DEFERRABLE INITIALLY
-- DEFERRED constraint trigger, which sees the whole set at COMMIT. Migration 022 used one to make
-- the journal balance; this migration uses the same mechanism to make a committed decision's lines
-- sum to its totals. Scheduled reconciliation remains worth having, but it is no longer the first
-- line of defence.
--
-- Note on the actor vocabulary: every "who did this" column here allows PROVIDER alongside the four
-- platform actors. A channel manager really does cancel bookings and really does import revised
-- terms, and the alternative -- a five-value vocabulary here beside the six-value BookingActorType
-- migrations 020 and 021 already use -- would be a Java enum wider than its own CHECK, which fails
-- at runtime rather than at compile time.
--
-- Note on records this migration does not create. The document proposes domain_idempotency_records,
-- booking_timeline_entries, and an outbox. Migration 012 delivered command_idempotency_records with
-- an actor/operation/resource scope, a canonical request hash and a retention class, and delivered
-- outbox_events and consumer_inbox_receipts; migration 020 delivered booking_timeline_entries with
-- visibility and correlation. A second idempotency table would mean two answers to "has this command
-- already run", and a second outbox needs a second publisher. Decisions and proposals therefore
-- carry an idempotency_key that resolves against the existing record.
--
-- Note on booking_policy_acceptances: migration 020 already created it, keyed by booking and policy
-- type. The document wants acceptance bound to a booking revision, so changeset 023-20 adds the
-- revision reference forward rather than building a second acceptance table.
--
-- Note on the legacy backfill: the document's migration plan assumes live bookings whose original
-- revision must be reconstructed and marked LEGACY_UNRESOLVED. Migration 020 replaced the historical
-- booking tables outright, so there are no rows to reconstruct. The provenance vocabulary is kept on
-- booking_revisions because it still describes a legitimate state -- a revision imported from a
-- channel manager has the same evidential weakness -- but nothing is backfilled here.

--changeset ninggiangboy:023-01-cancellation-policy-definitions
-- The policy family: a stable identity a host or a market can select, under which many versions come
-- and go. Selecting a family is not selecting terms, which is why nothing here carries a rule.
CREATE TABLE cancellation_policy_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key                  VARCHAR(48) NOT NULL,
    display_name_key            VARCHAR(128) NOT NULL,
    owner_type                  VARCHAR(16) NOT NULL,
    owner_account_holder_id     UUID,
    market_code                 VARCHAR(2),
    legal_entity_id             UUID,

    -- What supply this family may be attached to. A family built for long stays must not silently
    -- become selectable for a one-night booking.
    applies_to_product_types    VARCHAR(32)[] NOT NULL DEFAULT '{}',
    minimum_nights              SMALLINT,
    maximum_nights              SMALLINT,
    host_selectable             BOOLEAN NOT NULL DEFAULT true,

    lifecycle                   VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    retired_at                  TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cancellation_policy_definitions_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_cancellation_policy_definitions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_cancellation_policy_definitions_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT uk_cancellation_policy_definitions_key UNIQUE (policy_key),
    CONSTRAINT ck_cancellation_policy_definitions_owner CHECK (
        owner_type IN ('PLATFORM', 'MARKET', 'HOST')
    ),
    -- A host-owned family without a host, or a platform family with one, is a row whose meaning
    -- cannot be read from itself.
    CONSTRAINT ck_cancellation_policy_definitions_owner_ref CHECK (
        (owner_type = 'HOST') = (owner_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_cancellation_policy_definitions_market_ref CHECK (
        owner_type <> 'MARKET' OR market_code IS NOT NULL
    ),
    CONSTRAINT ck_cancellation_policy_definitions_lifecycle CHECK (
        lifecycle IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_cancellation_policy_definitions_retirement CHECK (
        lifecycle <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_cancellation_policy_definitions_nights CHECK (
        (minimum_nights IS NULL OR minimum_nights > 0)
            AND (maximum_nights IS NULL OR maximum_nights > 0)
            AND (minimum_nights IS NULL OR maximum_nights IS NULL
                 OR maximum_nights >= minimum_nights)
    ),
    CONSTRAINT ck_cancellation_policy_definitions_version CHECK (version >= 0)
);

CREATE INDEX idx_cancellation_policy_definitions_market
    ON cancellation_policy_definitions (market_code, lifecycle);
CREATE INDEX idx_cancellation_policy_definitions_owner
    ON cancellation_policy_definitions (owner_account_holder_id)
    WHERE owner_account_holder_id IS NOT NULL;
--rollback DROP TABLE cancellation_policy_definitions;

--changeset ninggiangboy:023-02-cancellation-policy-versions
-- The terms themselves, frozen. Everything a settlement needs to reproduce an old calculation is
-- here: the typed rule document, the schema it conforms to, the evaluator build that reads it, the
-- cutoff semantics, and a content hash so an altered document is detectable rather than plausible.
--
-- effective_range is half-open. A version that ended and a version that began at the same instant
-- must not both apply to a booking quoted at exactly that instant.
CREATE TABLE cancellation_policy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_definition_id        UUID NOT NULL,
    version_label               VARCHAR(64) NOT NULL,
    version_number              INTEGER NOT NULL,

    rule_document               JSONB NOT NULL,
    rule_schema_version         VARCHAR(32) NOT NULL,
    evaluator_version           VARCHAR(32) NOT NULL,
    content_hash                CHAR(64) NOT NULL,
    test_vector_suite_version   VARCHAR(32),

    -- Cutoff semantics. A "48 hours before check-in" rule is meaningless without saying which clock
    -- measures it, and settling in the wrong zone moves real money.
    cutoff_basis                VARCHAR(24) NOT NULL DEFAULT 'PROPERTY_LOCAL',
    cutoff_time_zone_source     VARCHAR(24) NOT NULL DEFAULT 'PROPERTY',
    reason_catalog_version      VARCHAR(32) NOT NULL,

    effective_from              TIMESTAMPTZ NOT NULL,
    effective_to                TIMESTAMPTZ,

    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approved_by_actor_id        UUID,
    approved_at                 TIMESTAMPTZ,
    published_at                TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cancellation_policy_versions_definition FOREIGN KEY (policy_definition_id)
        REFERENCES cancellation_policy_definitions (id),
    CONSTRAINT fk_cancellation_policy_versions_approver FOREIGN KEY (approved_by_actor_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_cancellation_policy_versions_label
        UNIQUE (policy_definition_id, version_label),
    CONSTRAINT uk_cancellation_policy_versions_number
        UNIQUE (policy_definition_id, version_number),
    CONSTRAINT ck_cancellation_policy_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_cancellation_policy_versions_state CHECK (
        state IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'RETIRED')
    ),
    CONSTRAINT ck_cancellation_policy_versions_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cancellation_policy_versions_cutoff CHECK (
        cutoff_basis IN ('PROPERTY_LOCAL', 'GUEST_LOCAL', 'UTC', 'BOOKING_TIME_ZONE')
    ),
    CONSTRAINT ck_cancellation_policy_versions_zone_source CHECK (
        cutoff_time_zone_source IN ('PROPERTY', 'MARKET', 'LEGAL_ENTITY', 'EXPLICIT')
    ),
    -- Publication is the moment the version becomes settleable, so it cannot be inferred later from
    -- a state column somebody edited.
    CONSTRAINT ck_cancellation_policy_versions_approval CHECK (
        state NOT IN ('APPROVED', 'PUBLISHED', 'RETIRED')
            OR (approved_at IS NOT NULL AND approved_by_actor_id IS NOT NULL)
    ),
    CONSTRAINT ck_cancellation_policy_versions_publication CHECK (
        (state NOT IN ('PUBLISHED', 'RETIRED') OR published_at IS NOT NULL)
            AND (published_at IS NULL OR state IN ('PUBLISHED', 'RETIRED'))
    ),
    CONSTRAINT ck_cancellation_policy_versions_retirement CHECK (
        state <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_cancellation_policy_versions_interval CHECK (
        effective_to IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT ck_cancellation_policy_versions_version CHECK (version >= 0)
);

CREATE INDEX idx_cancellation_policy_versions_effective
    ON cancellation_policy_versions (policy_definition_id, effective_from DESC)
    WHERE state = 'PUBLISHED';
CREATE INDEX idx_cancellation_policy_versions_hash
    ON cancellation_policy_versions (content_hash);
--rollback DROP TABLE cancellation_policy_versions;

--changeset ninggiangboy:023-03-policy-disclosure-versions
-- The words the guest actually read, per locale. A settlement argument is rarely about the rule
-- document -- it is about what the booking page said. The semantic hash is over the meaning rather
-- than the markup, so a styling change does not invalidate an acceptance and a wording change does.
--
-- Rows are append-only once approved. New locales may still be added to a published policy, because
-- a translation approved in March is a legitimate addition to terms published in January; what may
-- never happen is an approved translation changing under an acceptance that cites it.
CREATE TABLE policy_disclosure_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_version_id           UUID NOT NULL,
    locale                      VARCHAR(35) NOT NULL,

    title                       VARCHAR(255) NOT NULL,
    body                        TEXT NOT NULL,
    structured_summary          JSONB,
    semantic_content_hash       CHAR(64) NOT NULL,

    translation_source          VARCHAR(16) NOT NULL DEFAULT 'PROFESSIONAL',
    translated_from_locale      VARCHAR(35),
    approved_by_actor_id        UUID,
    approved_at                 TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_policy_disclosure_versions_policy FOREIGN KEY (policy_version_id)
        REFERENCES cancellation_policy_versions (id),
    CONSTRAINT fk_policy_disclosure_versions_approver FOREIGN KEY (approved_by_actor_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_policy_disclosure_versions_locale UNIQUE (policy_version_id, locale),
    CONSTRAINT ck_policy_disclosure_versions_hash CHECK (
        semantic_content_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_policy_disclosure_versions_source CHECK (
        translation_source IN ('HOST', 'MACHINE', 'PROFESSIONAL')
    ),
    -- A machine translation is storable; a machine translation presented as approved terms is not.
    CONSTRAINT ck_policy_disclosure_versions_approval CHECK (
        (approved_at IS NULL) = (approved_by_actor_id IS NULL)
    ),
    CONSTRAINT ck_policy_disclosure_versions_machine CHECK (
        translation_source <> 'MACHINE' OR approved_at IS NULL
    )
);

CREATE INDEX idx_policy_disclosure_versions_policy
    ON policy_disclosure_versions (policy_version_id, locale);
--rollback DROP TABLE policy_disclosure_versions;

--changeset ninggiangboy:023-04-booking-revisions
-- What the contract said, at each point it said something different. A modification, a correction,
-- and a compensating reversal all produce a new revision rather than editing the last one; the
-- booking's own columns are the projection of whichever revision is current.
--
-- Three defences live here. Revision numbers are unique within a booking, so two concurrent
-- modifications cannot both claim to be number four. A predecessor is claimed at most once, so the
-- chain cannot fork into two histories that both look authoritative. And at most one committed
-- revision is un-superseded, so "the current terms" is a question with one answer.
CREATE TABLE booking_revisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    revision_number             INTEGER NOT NULL,
    predecessor_revision_id     UUID,
    revision_type               VARCHAR(24) NOT NULL,
    provenance                  VARCHAR(24) NOT NULL DEFAULT 'PLATFORM_EVALUATED',

    listing_id                  UUID NOT NULL,
    rate_plan_id                UUID NOT NULL,
    stay_range                  DATERANGE NOT NULL,
    check_in_date               DATE NOT NULL,
    check_out_date              DATE NOT NULL,
    property_time_zone          VARCHAR(64) NOT NULL,
    check_in_local_time         TIME NOT NULL,
    check_in_instant            TIMESTAMPTZ NOT NULL,

    adult_count                 SMALLINT NOT NULL DEFAULT 1,
    child_count                 SMALLINT NOT NULL DEFAULT 0,
    infant_count                SMALLINT NOT NULL DEFAULT 0,
    pet_count                   SMALLINT NOT NULL DEFAULT 0,
    unit_quantity               SMALLINT NOT NULL DEFAULT 1,

    currency                    VARCHAR(3) NOT NULL,
    total_amount_minor          BIGINT NOT NULL,

    quote_id                    UUID,
    policy_version_id           UUID,
    policy_acceptance_id        UUID,
    financial_snapshot_id       UUID,
    allocation_version          INTEGER NOT NULL DEFAULT 1,

    -- Both references are closed by changeset 023-20; the tables they name are created further
    -- down this same file.
    source_proposal_id          UUID,
    source_decision_id          UUID,
    correlation_id              VARCHAR(64) NOT NULL,

    terms_hash                  CHAR(64) NOT NULL,
    nights_hash                 CHAR(64) NOT NULL,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    committed_by_actor_type     VARCHAR(24),
    committed_by_actor_id       UUID,
    committed_at                TIMESTAMPTZ,
    superseded_at               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_revisions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_booking_revisions_predecessor FOREIGN KEY (predecessor_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_booking_revisions_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_booking_revisions_rate_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id),
    CONSTRAINT fk_booking_revisions_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_booking_revisions_policy FOREIGN KEY (policy_version_id)
        REFERENCES cancellation_policy_versions (id),
    CONSTRAINT fk_booking_revisions_acceptance FOREIGN KEY (policy_acceptance_id)
        REFERENCES booking_policy_acceptances (id),
    CONSTRAINT fk_booking_revisions_snapshot FOREIGN KEY (financial_snapshot_id)
        REFERENCES booking_financial_snapshots (id),
    CONSTRAINT uk_booking_revisions_number UNIQUE (booking_id, revision_number),
    CONSTRAINT ck_booking_revisions_number CHECK (revision_number > 0),
    CONSTRAINT ck_booking_revisions_type CHECK (
        revision_type IN ('ORIGINAL', 'MODIFICATION', 'CORRECTION', 'COMPENSATING')
    ),
    CONSTRAINT ck_booking_revisions_provenance CHECK (
        provenance IN ('PLATFORM_EVALUATED', 'CHANNEL_IMPORTED', 'SUPPORT_ENTERED',
                       'LEGACY_UNRESOLVED')
    ),
    CONSTRAINT ck_booking_revisions_status CHECK (
        status IN ('DRAFT', 'COMMITTED', 'SUPERSEDED', 'ABANDONED')
    ),
    -- The first revision is the only one without a predecessor, and every later one must name the
    -- revision it replaces. A chain with a hole in it cannot be replayed.
    CONSTRAINT ck_booking_revisions_chain CHECK (
        (revision_number = 1) = (predecessor_revision_id IS NULL)
    ),
    CONSTRAINT ck_booking_revisions_original CHECK (
        (revision_number = 1) = (revision_type = 'ORIGINAL')
    ),
    CONSTRAINT ck_booking_revisions_self CHECK (predecessor_revision_id <> id),
    CONSTRAINT ck_booking_revisions_commit CHECK (
        (status NOT IN ('COMMITTED', 'SUPERSEDED')
            OR (committed_at IS NOT NULL AND committed_by_actor_type IS NOT NULL))
        AND (committed_at IS NULL OR status IN ('COMMITTED', 'SUPERSEDED'))
    ),
    CONSTRAINT ck_booking_revisions_actor CHECK (
        committed_by_actor_type IS NULL
            OR committed_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_booking_revisions_supersession CHECK (
        (status = 'SUPERSEDED') = (superseded_at IS NOT NULL)
    ),
    CONSTRAINT ck_booking_revisions_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT ck_booking_revisions_range_agrees CHECK (
        stay_range = daterange(check_in_date, check_out_date, '[)')
    ),
    CONSTRAINT ck_booking_revisions_party CHECK (
        adult_count > 0 AND child_count >= 0 AND infant_count >= 0
            AND pet_count >= 0 AND unit_quantity > 0
    ),
    CONSTRAINT ck_booking_revisions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_revisions_total CHECK (total_amount_minor >= 0),
    CONSTRAINT ck_booking_revisions_allocation CHECK (allocation_version > 0),
    CONSTRAINT ck_booking_revisions_terms_hash CHECK (terms_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_booking_revisions_nights_hash CHECK (nights_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_booking_revisions_version CHECK (version >= 0)
);

-- One answer to "what are the current terms". Two un-superseded committed revisions would let a
-- refund settle against one set of dates while the calendar holds another.
CREATE UNIQUE INDEX uk_booking_revisions_current ON booking_revisions (booking_id)
    WHERE status = 'COMMITTED';
-- The chain cannot fork: a predecessor is replaced once.
CREATE UNIQUE INDEX uk_booking_revisions_predecessor ON booking_revisions (predecessor_revision_id)
    WHERE predecessor_revision_id IS NOT NULL AND status IN ('COMMITTED', 'SUPERSEDED');
CREATE INDEX idx_booking_revisions_booking
    ON booking_revisions (booking_id, revision_number DESC);
--rollback DROP INDEX uk_booking_revisions_predecessor;
--rollback DROP INDEX uk_booking_revisions_current;
--rollback DROP TABLE booking_revisions;

--changeset ninggiangboy:023-05-booking-revision-nights
-- The nightly shape of one revision: which date, which unit, which resource consumed it, and which
-- price identity explains the amount. Kept per revision rather than per booking because shortening a
-- stay must leave the original nights readable, not delete them.
CREATE TABLE booking_revision_nights (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_revision_id         UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    listing_id                  UUID NOT NULL,
    physical_unit_id            UUID,
    inventory_resource_id       UUID NOT NULL,
    inventory_claim_id          UUID,

    unit_quantity               SMALLINT NOT NULL DEFAULT 1,
    currency                    VARCHAR(3) NOT NULL,
    nightly_amount_minor        BIGINT NOT NULL,
    price_version               INTEGER,
    daily_price_component_id    UUID,
    allocation_reference        VARCHAR(128),

    consumption_state           VARCHAR(16) NOT NULL DEFAULT 'CONSUMED',
    created_at                  TIMESTAMPTZ NOT NULL,
    -- The cascade is honest here: a draft revision that is abandoned takes its nights with it. A
    -- committed one cannot be deleted at all, because changeset 023-23 freezes both the revision and
    -- its nights, so the cascade never has to break that promise.
    CONSTRAINT fk_booking_revision_nights_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_revision_nights_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id),
    CONSTRAINT fk_booking_revision_nights_unit FOREIGN KEY (physical_unit_id)
        REFERENCES physical_units (id),
    CONSTRAINT fk_booking_revision_nights_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT fk_booking_revision_nights_claim FOREIGN KEY (inventory_claim_id)
        REFERENCES inventory_claims (id),
    CONSTRAINT uk_booking_revision_nights_date
        UNIQUE (booking_revision_id, stay_date, physical_unit_id),
    CONSTRAINT ck_booking_revision_nights_quantity CHECK (unit_quantity > 0),
    CONSTRAINT ck_booking_revision_nights_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_revision_nights_amount CHECK (nightly_amount_minor >= 0),
    CONSTRAINT ck_booking_revision_nights_consumption CHECK (
        consumption_state IN ('CONSUMED', 'RELEASED', 'TRANSFERRED', 'NOT_CLAIMED')
    )
);

CREATE INDEX idx_booking_revision_nights_revision
    ON booking_revision_nights (booking_revision_id, stay_date);
CREATE INDEX idx_booking_revision_nights_claim ON booking_revision_nights (inventory_claim_id)
    WHERE inventory_claim_id IS NOT NULL;
--rollback DROP TABLE booking_revision_nights;

--changeset ninggiangboy:023-06-cancellation-previews
-- The number the guest was shown before they confirmed. It exists as a row, not as a response body,
-- because the decision must be able to prove that it settled the figure the guest accepted rather
-- than a recalculation that happened to run a minute later at a different price.
--
-- The input hash is what makes staleness detectable: if the booking, the policy, or the clock moved
-- between preview and decision, the recomputed hash differs and the decision is refused in service
-- code. The expiry is what stops an unaccepted preview being redeemed next week.
CREATE TABLE cancellation_previews (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,
    policy_version_id           UUID NOT NULL,

    requested_by_actor_type     VARCHAR(24) NOT NULL,
    requested_by_actor_id       UUID,
    action_type                 VARCHAR(24) NOT NULL,
    reason_code                 VARCHAR(64) NOT NULL,
    reason_category             VARCHAR(32) NOT NULL,

    -- Both instants are stored because the rule is expressed against one and audited against the
    -- other. A cutoff computed from a check-in time the property later changed must still be
    -- reproducible from the numbers that were used.
    effective_at                TIMESTAMPTZ NOT NULL,
    official_check_in_at        TIMESTAMPTZ NOT NULL,
    property_time_zone          VARCHAR(64) NOT NULL,

    evaluator_version           VARCHAR(32) NOT NULL,
    config_version              VARCHAR(32),
    input_hash                  CHAR(64) NOT NULL,
    result_hash                 CHAR(64) NOT NULL,
    result_document             JSONB NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    retained_amount_minor       BIGINT NOT NULL DEFAULT 0,
    refund_amount_minor         BIGINT NOT NULL DEFAULT 0,
    new_due_amount_minor        BIGINT NOT NULL DEFAULT 0,

    status                      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at                  TIMESTAMPTZ NOT NULL,
    consumed_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_cancellation_previews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_cancellation_previews_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_cancellation_previews_policy FOREIGN KEY (policy_version_id)
        REFERENCES cancellation_policy_versions (id),
    CONSTRAINT ck_cancellation_previews_actor CHECK (
        requested_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_cancellation_previews_action CHECK (
        action_type IN ('FULL_CANCELLATION', 'PARTIAL_CANCELLATION', 'MODIFICATION',
                        'NO_SHOW', 'RELOCATION')
    ),
    CONSTRAINT ck_cancellation_previews_category CHECK (
        reason_category IN ('GUEST_CHOICE', 'HOST_CHOICE', 'PLATFORM_ACTION', 'EXTENUATING',
                            'SAFETY', 'FRAUD', 'SUPPLY_FAILURE', 'PAYMENT_FAILURE')
    ),
    CONSTRAINT ck_cancellation_previews_status CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'EXPIRED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_cancellation_previews_consumption CHECK (
        (status = 'CONSUMED') = (consumed_at IS NOT NULL)
    ),
    CONSTRAINT ck_cancellation_previews_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_cancellation_previews_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_cancellation_previews_amounts CHECK (
        retained_amount_minor >= 0 AND refund_amount_minor >= 0 AND new_due_amount_minor >= 0
    ),
    -- A preview cannot both return money and ask for more. If a modification does both, they net to
    -- one direction before the guest ever sees it.
    CONSTRAINT ck_cancellation_previews_direction CHECK (
        refund_amount_minor = 0 OR new_due_amount_minor = 0
    ),
    CONSTRAINT ck_cancellation_previews_input_hash CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cancellation_previews_result_hash CHECK (result_hash ~ '^[0-9a-f]{64}$')
);

-- The sweeper's index. No now() in the predicate: a partial index whose predicate moved with the
-- clock would stop matching the rows it was built for.
CREATE INDEX idx_cancellation_previews_active ON cancellation_previews (expires_at)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_cancellation_previews_booking
    ON cancellation_previews (booking_id, created_at DESC);
CREATE INDEX idx_cancellation_previews_input ON cancellation_previews (input_hash);
--rollback DROP TABLE cancellation_previews;

--changeset ninggiangboy:023-07-cancellation-decisions
-- The committed arithmetic. One row says what the contract became, under which rule, on whose
-- authority, at which instant -- and nothing downstream may recalculate it. A mistake is corrected
-- by a superseding decision that names this one, never by an edit, because the guest was already
-- told this number.
--
-- The idempotency key is scoped to the booking rather than global: a retried cancel command must
-- converge on the decision that already exists, and two different bookings legitimately share a
-- client-generated key.
CREATE TABLE cancellation_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,
    resulting_revision_id       UUID,

    decision_type               VARCHAR(24) NOT NULL,
    cause_category              VARCHAR(32) NOT NULL,
    reason_code                 VARCHAR(64) NOT NULL,
    decided_by_actor_type       VARCHAR(24) NOT NULL,
    decided_by_actor_id         UUID,
    effective_at                TIMESTAMPTZ NOT NULL,

    accepted_preview_id         UUID,
    policy_version_id           UUID NOT NULL,
    -- Constrained by changeset 023-20; policy_override_decisions is created further down.
    policy_override_decision_id UUID,
    evaluator_version           VARCHAR(32) NOT NULL,
    tax_calculation_id          UUID,
    allocation_version          INTEGER NOT NULL DEFAULT 1,

    input_hash                  CHAR(64) NOT NULL,
    result_hash                 CHAR(64) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    original_amount_minor       BIGINT NOT NULL,
    retained_amount_minor       BIGINT NOT NULL DEFAULT 0,
    refund_amount_minor         BIGINT NOT NULL DEFAULT 0,
    new_due_amount_minor        BIGINT NOT NULL DEFAULT 0,
    host_compensation_minor     BIGINT NOT NULL DEFAULT 0,
    platform_cost_minor         BIGINT NOT NULL DEFAULT 0,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    supersedes_decision_id      UUID,
    superseded_by_decision_id   UUID,
    approval_reference          VARCHAR(128),
    evidence_reference          VARCHAR(255),

    idempotency_key             VARCHAR(128) NOT NULL,
    correlation_id              VARCHAR(64) NOT NULL,
    committed_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cancellation_decisions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_cancellation_decisions_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_cancellation_decisions_result_revision FOREIGN KEY (resulting_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_cancellation_decisions_preview FOREIGN KEY (accepted_preview_id)
        REFERENCES cancellation_previews (id),
    CONSTRAINT fk_cancellation_decisions_policy FOREIGN KEY (policy_version_id)
        REFERENCES cancellation_policy_versions (id),
    CONSTRAINT fk_cancellation_decisions_tax FOREIGN KEY (tax_calculation_id)
        REFERENCES tax_calculations (id),
    CONSTRAINT fk_cancellation_decisions_supersedes FOREIGN KEY (supersedes_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT fk_cancellation_decisions_superseded_by FOREIGN KEY (superseded_by_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT uk_cancellation_decisions_public_id UNIQUE (public_id),
    CONSTRAINT uk_cancellation_decisions_idempotency UNIQUE (booking_id, idempotency_key),
    -- A preview is redeemed once. Without this, the same accepted figure could found two decisions
    -- and refund the guest twice for one cancellation.
    CONSTRAINT uk_cancellation_decisions_preview UNIQUE (accepted_preview_id),
    CONSTRAINT ck_cancellation_decisions_type CHECK (
        decision_type IN ('FULL_CANCELLATION', 'PARTIAL_CANCELLATION', 'MODIFICATION',
                          'NO_SHOW', 'RELOCATION', 'CORRECTION')
    ),
    CONSTRAINT ck_cancellation_decisions_cause CHECK (
        cause_category IN ('GUEST_CHOICE', 'HOST_CHOICE', 'PLATFORM_ACTION', 'EXTENUATING',
                           'SAFETY', 'FRAUD', 'SUPPLY_FAILURE', 'PAYMENT_FAILURE')
    ),
    CONSTRAINT ck_cancellation_decisions_actor CHECK (
        decided_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_cancellation_decisions_status CHECK (
        status IN ('DRAFT', 'COMMITTED', 'SUPERSEDED', 'VOIDED')
    ),
    CONSTRAINT ck_cancellation_decisions_commit CHECK (
        (status NOT IN ('COMMITTED', 'SUPERSEDED') OR committed_at IS NOT NULL)
            AND (committed_at IS NULL OR status IN ('COMMITTED', 'SUPERSEDED'))
    ),
    CONSTRAINT ck_cancellation_decisions_supersession CHECK (
        (status = 'SUPERSEDED') = (superseded_by_decision_id IS NOT NULL)
    ),
    CONSTRAINT ck_cancellation_decisions_self CHECK (
        supersedes_decision_id <> id AND superseded_by_decision_id <> id
    ),
    CONSTRAINT ck_cancellation_decisions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_cancellation_decisions_amounts CHECK (
        original_amount_minor >= 0 AND retained_amount_minor >= 0
            AND refund_amount_minor >= 0 AND new_due_amount_minor >= 0
            AND host_compensation_minor >= 0 AND platform_cost_minor >= 0
    ),
    -- Nothing may be retained and refunded beyond what the contract was worth.
    CONSTRAINT ck_cancellation_decisions_ceiling CHECK (
        retained_amount_minor + refund_amount_minor <= original_amount_minor
    ),
    CONSTRAINT ck_cancellation_decisions_direction CHECK (
        refund_amount_minor = 0 OR new_due_amount_minor = 0
    ),
    CONSTRAINT ck_cancellation_decisions_allocation CHECK (allocation_version > 0),
    CONSTRAINT ck_cancellation_decisions_input_hash CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cancellation_decisions_result_hash CHECK (result_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cancellation_decisions_version CHECK (version >= 0)
);

-- A booking is cancelled in full once. A second live full cancellation would release the same nights
-- twice and found a second refund against a contract that no longer exists.
CREATE UNIQUE INDEX uk_cancellation_decisions_terminal ON cancellation_decisions (booking_id)
    WHERE status = 'COMMITTED' AND decision_type IN ('FULL_CANCELLATION', 'NO_SHOW');
CREATE INDEX idx_cancellation_decisions_booking
    ON cancellation_decisions (booking_id, effective_at DESC);
CREATE INDEX idx_cancellation_decisions_correlation
    ON cancellation_decisions (correlation_id);
--rollback DROP INDEX uk_cancellation_decisions_terminal;
--rollback DROP TABLE cancellation_decisions;

--changeset ninggiangboy:023-08-cancellation-decision-lines
-- The arithmetic, line by line. Each line traces to the booking line it recalculates and says what
-- happened to it: how much was consumed by nights already stayed, how much the policy retained, how
-- much comes back, and which party funds each part.
--
-- Funding is split out because "the guest got a full refund" and "the host paid for it" are
-- different facts, and every settlement dispute is about the second one. The five funding columns
-- carry the answer rather than leaving it to be inferred from a reason code.
CREATE TABLE cancellation_decision_lines (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cancellation_decision_id    UUID NOT NULL,
    line_number                 SMALLINT NOT NULL,
    source_booking_line_id      UUID,
    allocation_reference        VARCHAR(128),

    line_category               VARCHAR(32) NOT NULL,
    affected_quantity           NUMERIC(10,3) NOT NULL DEFAULT 1,
    service_from_date           DATE,
    service_to_date             DATE,

    currency                    VARCHAR(3) NOT NULL,
    original_amount_minor       BIGINT NOT NULL,
    cancelled_amount_minor      BIGINT NOT NULL DEFAULT 0,
    consumed_amount_minor       BIGINT NOT NULL DEFAULT 0,
    retained_amount_minor       BIGINT NOT NULL DEFAULT 0,
    refund_amount_minor         BIGINT NOT NULL DEFAULT 0,
    new_due_amount_minor        BIGINT NOT NULL DEFAULT 0,

    guest_funded_minor          BIGINT NOT NULL DEFAULT 0,
    host_funded_minor           BIGINT NOT NULL DEFAULT 0,
    platform_funded_minor       BIGINT NOT NULL DEFAULT 0,
    partner_funded_minor        BIGINT NOT NULL DEFAULT 0,
    tax_effect_minor            BIGINT NOT NULL DEFAULT 0,
    promotion_effect_minor      BIGINT NOT NULL DEFAULT 0,

    rounding_rule               VARCHAR(24) NOT NULL DEFAULT 'HALF_UP',
    rounding_remainder_minor    BIGINT NOT NULL DEFAULT 0,
    explanation_code            VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: a committed decision's lines are frozen by trigger, so a
    -- cascade would promise a delete the row trigger refuses to perform.
    CONSTRAINT fk_cancellation_decision_lines_decision FOREIGN KEY (cancellation_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT fk_cancellation_decision_lines_source FOREIGN KEY (source_booking_line_id)
        REFERENCES booking_line_items (id),
    CONSTRAINT uk_cancellation_decision_lines_number
        UNIQUE (cancellation_decision_id, line_number),
    CONSTRAINT ck_cancellation_decision_lines_number CHECK (line_number > 0),
    CONSTRAINT ck_cancellation_decision_lines_category CHECK (
        line_category IN ('ACCOMMODATION', 'CLEANING_FEE', 'SERVICE_FEE', 'HOST_FEE',
                          'EXTRA_GUEST_FEE', 'PET_FEE', 'RESORT_FEE', 'DISCOUNT', 'PROMOTION',
                          'TAX', 'SECURITY_DEPOSIT', 'CANCELLATION_FEE', 'GOODWILL', 'OTHER')
    ),
    CONSTRAINT ck_cancellation_decision_lines_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_cancellation_decision_lines_quantity CHECK (affected_quantity > 0),
    CONSTRAINT ck_cancellation_decision_lines_service_dates CHECK (
        service_from_date IS NULL OR service_to_date IS NULL
            OR service_to_date >= service_from_date
    ),
    CONSTRAINT ck_cancellation_decision_lines_amounts CHECK (
        original_amount_minor >= 0 AND cancelled_amount_minor >= 0
            AND consumed_amount_minor >= 0 AND retained_amount_minor >= 0
            AND refund_amount_minor >= 0 AND new_due_amount_minor >= 0
            AND guest_funded_minor >= 0 AND host_funded_minor >= 0
            AND platform_funded_minor >= 0 AND partner_funded_minor >= 0
    ),
    -- What was consumed, retained and refunded came out of one original amount. A line that
    -- disposes of more than it started with is how a refund quietly exceeds the booking.
    CONSTRAINT ck_cancellation_decision_lines_disposal CHECK (
        consumed_amount_minor + retained_amount_minor + refund_amount_minor
            <= original_amount_minor
    ),
    -- Every refunded minor unit is paid for by somebody. This is the constraint that makes the
    -- funding columns trustworthy instead of decorative.
    CONSTRAINT ck_cancellation_decision_lines_funding CHECK (
        refund_amount_minor
            = guest_funded_minor + host_funded_minor + platform_funded_minor + partner_funded_minor
    ),
    CONSTRAINT ck_cancellation_decision_lines_direction CHECK (
        refund_amount_minor = 0 OR new_due_amount_minor = 0
    ),
    CONSTRAINT ck_cancellation_decision_lines_rounding CHECK (
        rounding_rule IN ('HALF_UP', 'HALF_EVEN', 'FLOOR', 'CEILING', 'LARGEST_REMAINDER')
    )
);

CREATE INDEX idx_cancellation_decision_lines_decision
    ON cancellation_decision_lines (cancellation_decision_id, line_number);
CREATE INDEX idx_cancellation_decision_lines_source
    ON cancellation_decision_lines (source_booking_line_id)
    WHERE source_booking_line_id IS NOT NULL;
--rollback DROP TABLE cancellation_decision_lines;

--changeset ninggiangboy:023-09-inventory-release-instructions
-- Telling the calendar to give the nights back. It is an instruction rather than a direct write
-- because inventory is another domain's authority and the release may fail, be retried, or arrive
-- after the claim was already released by a sweeper.
--
-- The unique key is the exactly-once defence: one release per decision per claim, so a retried
-- worker cannot release a claim a second time after the resource was re-sold.
CREATE TABLE inventory_release_instructions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cancellation_decision_id    UUID NOT NULL,
    inventory_claim_id          UUID NOT NULL,
    inventory_resource_id       UUID NOT NULL,
    release_range               DATERANGE NOT NULL,
    quantity                    INTEGER NOT NULL DEFAULT 1,
    release_reason              VARCHAR(48) NOT NULL,

    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    applied_at                  TIMESTAMPTZ,
    applied_fencing_token       BIGINT,
    failure_reason              VARCHAR(255),
    attempt_count               INTEGER NOT NULL DEFAULT 0,

    idempotency_key             VARCHAR(128) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inventory_release_instructions_decision FOREIGN KEY (cancellation_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT fk_inventory_release_instructions_claim FOREIGN KEY (inventory_claim_id)
        REFERENCES inventory_claims (id),
    CONSTRAINT fk_inventory_release_instructions_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT uk_inventory_release_instructions_claim
        UNIQUE (cancellation_decision_id, inventory_claim_id),
    CONSTRAINT uk_inventory_release_instructions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_inventory_release_instructions_state CHECK (
        state IN ('PENDING', 'APPLIED', 'FAILED', 'ABANDONED', 'NOT_REQUIRED')
    ),
    CONSTRAINT ck_inventory_release_instructions_applied CHECK (
        (state = 'APPLIED') = (applied_at IS NOT NULL)
    ),
    -- A release that claims to have been applied without the token it acted under cannot be checked
    -- against the claim it says it released.
    CONSTRAINT ck_inventory_release_instructions_token CHECK (
        state <> 'APPLIED' OR applied_fencing_token IS NOT NULL
    ),
    CONSTRAINT ck_inventory_release_instructions_failure CHECK (
        state <> 'FAILED' OR failure_reason IS NOT NULL
    ),
    CONSTRAINT ck_inventory_release_instructions_range CHECK (
        NOT isempty(release_range) AND lower_inc(release_range) AND NOT upper_inc(release_range)
    ),
    CONSTRAINT ck_inventory_release_instructions_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_release_instructions_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_inventory_release_instructions_version CHECK (version >= 0)
);

CREATE INDEX idx_inventory_release_instructions_pending
    ON inventory_release_instructions (created_at)
    WHERE state IN ('PENDING', 'FAILED');
CREATE INDEX idx_inventory_release_instructions_claim
    ON inventory_release_instructions (inventory_claim_id);
--rollback DROP TABLE inventory_release_instructions;

--changeset ninggiangboy:023-10-refund-instructions
-- The entitlement to have money returned. This domain decides what is owed and to whom; the payment
-- domain decides how it moves and whether it arrived. The separation is the whole point: a refund
-- that a provider has not completed is still owed, and a refund this domain cannot execute is still
-- decided.
--
-- projected_execution_state exists so a guest-facing screen can say "sent to your bank" without
-- joining across domains, and it is explicitly not authority. Payment owns refund_executions; a
-- write here can never make a refund successful.
--
-- instruction_version is the reissue counter. A refund whose destination failed is reissued as a new
-- version of the same instruction rather than as a second instruction, which is what makes the
-- payment domain's uniqueness on (refund_instruction_id, instruction_version) meaningful.
CREATE TABLE refund_instructions (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                       VARCHAR(32) NOT NULL,
    source_decision_type            VARCHAR(24) NOT NULL,
    cancellation_decision_id        UUID,
    booking_id                      UUID NOT NULL,
    booking_revision_id             UUID NOT NULL,
    allocation_version              INTEGER NOT NULL DEFAULT 1,
    instruction_version             INTEGER NOT NULL DEFAULT 1,
    sequence_number                 SMALLINT NOT NULL DEFAULT 1,

    beneficiary_role                VARCHAR(16) NOT NULL DEFAULT 'GUEST',
    beneficiary_account_holder_id   UUID NOT NULL,
    currency                        VARCHAR(3) NOT NULL,
    amount_minor                    BIGINT NOT NULL,

    guest_funded_minor              BIGINT NOT NULL DEFAULT 0,
    host_funded_minor               BIGINT NOT NULL DEFAULT 0,
    platform_funded_minor           BIGINT NOT NULL DEFAULT 0,
    partner_funded_minor            BIGINT NOT NULL DEFAULT 0,

    tax_calculation_id              UUID,
    ledger_transaction_id           UUID,
    document_reference              VARCHAR(255),

    reason_code                     VARCHAR(64) NOT NULL,
    policy_version_id               UUID,
    approved_by_actor_type          VARCHAR(24) NOT NULL,
    approved_by_actor_id            UUID,
    approved_at                     TIMESTAMPTZ NOT NULL,
    execution_deadline_at           TIMESTAMPTZ,

    state                           VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
    projected_execution_state       VARCHAR(24),
    projection_observed_at          TIMESTAMPTZ,
    cancelled_at                    TIMESTAMPTZ,
    cancellation_reason             VARCHAR(64),

    idempotency_key                 VARCHAR(128) NOT NULL,
    correlation_id                  VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_instructions_decision FOREIGN KEY (cancellation_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT fk_refund_instructions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_refund_instructions_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_refund_instructions_beneficiary FOREIGN KEY (beneficiary_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_refund_instructions_tax FOREIGN KEY (tax_calculation_id)
        REFERENCES tax_calculations (id),
    CONSTRAINT fk_refund_instructions_ledger FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_refund_instructions_policy FOREIGN KEY (policy_version_id)
        REFERENCES cancellation_policy_versions (id),
    CONSTRAINT uk_refund_instructions_public_id UNIQUE (public_id),
    CONSTRAINT uk_refund_instructions_idempotency UNIQUE (idempotency_key),
    -- One instruction per decision, beneficiary and position. A replayed commit converges here
    -- rather than issuing a second entitlement to the same money.
    CONSTRAINT uk_refund_instructions_decision
        UNIQUE (cancellation_decision_id, beneficiary_account_holder_id, sequence_number),
    CONSTRAINT ck_refund_instructions_source CHECK (
        source_decision_type IN ('CANCELLATION', 'MODIFICATION', 'REMEDY', 'CORRECTION',
                                 'RELOCATION')
    ),
    -- Everything except a support-entered correction traces to a committed decision row.
    CONSTRAINT ck_refund_instructions_decision_ref CHECK (
        source_decision_type = 'CORRECTION' OR cancellation_decision_id IS NOT NULL
    ),
    CONSTRAINT ck_refund_instructions_beneficiary_role CHECK (
        beneficiary_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_refund_instructions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_refund_instructions_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_refund_instructions_funding CHECK (
        guest_funded_minor >= 0 AND host_funded_minor >= 0
            AND platform_funded_minor >= 0 AND partner_funded_minor >= 0
            AND amount_minor = guest_funded_minor + host_funded_minor
                + platform_funded_minor + partner_funded_minor
    ),
    CONSTRAINT ck_refund_instructions_actor CHECK (
        approved_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_refund_instructions_state CHECK (
        state IN ('ISSUED', 'ACCEPTED', 'SETTLED', 'CANCELLED', 'SUPERSEDED')
    ),
    -- An instruction may only be withdrawn while no execution has accepted it. After that the
    -- remedy is a new instruction, because the money may already be moving.
    CONSTRAINT ck_refund_instructions_cancellation CHECK (
        (state = 'CANCELLED') = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_refund_instructions_cancellation_reason CHECK (
        cancelled_at IS NULL OR cancellation_reason IS NOT NULL
    ),
    CONSTRAINT ck_refund_instructions_projection CHECK (
        (projected_execution_state IS NULL) = (projection_observed_at IS NULL)
    ),
    CONSTRAINT ck_refund_instructions_projection_value CHECK (
        projected_execution_state IS NULL OR projected_execution_state IN (
            'APPROVED', 'RESERVED', 'SUBMITTING', 'PENDING', 'SUCCEEDED',
            'PARTIALLY_SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_FINAL', 'UNKNOWN')
    ),
    CONSTRAINT ck_refund_instructions_versions CHECK (
        allocation_version > 0 AND instruction_version > 0 AND sequence_number > 0
    ),
    CONSTRAINT ck_refund_instructions_version CHECK (version >= 0)
);

CREATE INDEX idx_refund_instructions_booking
    ON refund_instructions (booking_id, created_at DESC);
CREATE INDEX idx_refund_instructions_open ON refund_instructions (execution_deadline_at)
    WHERE state IN ('ISSUED', 'ACCEPTED');
CREATE INDEX idx_refund_instructions_beneficiary
    ON refund_instructions (beneficiary_account_holder_id, created_at DESC);
--rollback DROP TABLE refund_instructions;

--changeset ninggiangboy:023-11-adjustment-instructions
-- Everything a decision sets in motion that is not a refund: credit issued to a guest, money to be
-- recovered from a host, goodwill the platform funds, a promotion returned to its holder, a tax
-- correction, a document to reissue, a payout to hold.
--
-- They share one table because they share one problem -- each must be produced exactly once from a
-- decision and handed to a domain that will act on it -- and differ only in which domain reads them.
-- The target reference is deliberately untyped: it names a row in another domain whose table this
-- one must not depend on.
CREATE TABLE adjustment_instructions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cancellation_decision_id    UUID NOT NULL,
    booking_id                  UUID NOT NULL,
    adjustment_type             VARCHAR(32) NOT NULL,
    sequence_number             SMALLINT NOT NULL DEFAULT 1,

    counterparty_role           VARCHAR(16) NOT NULL,
    counterparty_account_holder_id UUID,
    currency                    VARCHAR(3),
    amount_minor                BIGINT,

    allocation_reference        VARCHAR(128),
    provenance_reference        VARCHAR(255),
    target_domain               VARCHAR(24) NOT NULL,
    target_reference_id         UUID,

    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    dispatched_at               TIMESTAMPTZ,
    acknowledged_at             TIMESTAMPTZ,
    failure_reason              VARCHAR(255),
    attempt_count               INTEGER NOT NULL DEFAULT 0,

    downstream_idempotency_key  VARCHAR(128) NOT NULL,
    correlation_id              VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_adjustment_instructions_decision FOREIGN KEY (cancellation_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT fk_adjustment_instructions_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_adjustment_instructions_counterparty FOREIGN KEY (counterparty_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_adjustment_instructions_key UNIQUE (downstream_idempotency_key),
    -- One instruction of each type per decision per position. This is what stops a retried commit
    -- opening a second host recovery for the same cancellation.
    CONSTRAINT uk_adjustment_instructions_decision
        UNIQUE (cancellation_decision_id, adjustment_type, sequence_number),
    CONSTRAINT ck_adjustment_instructions_type CHECK (
        adjustment_type IN ('GUEST_CREDIT', 'HOST_RECOVERY', 'PLATFORM_GOODWILL',
                            'PROMOTION_RESTORE', 'TAX_ADJUSTMENT', 'DOCUMENT_REQUEST',
                            'PAYOUT_HOLD', 'PAYOUT_RELEASE')
    ),
    CONSTRAINT ck_adjustment_instructions_counterparty CHECK (
        counterparty_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_adjustment_instructions_target CHECK (
        target_domain IN ('FINANCE', 'PAYMENTS', 'PRICING', 'TAX', 'DOCUMENTS', 'LOYALTY')
    ),
    CONSTRAINT ck_adjustment_instructions_state CHECK (
        state IN ('PENDING', 'DISPATCHED', 'ACKNOWLEDGED', 'FAILED', 'ABANDONED')
    ),
    -- Money and currency travel together or not at all. An amount without a currency is a number
    -- whose meaning the receiving domain has to guess.
    CONSTRAINT ck_adjustment_instructions_money CHECK (
        (amount_minor IS NULL) = (currency IS NULL)
    ),
    CONSTRAINT ck_adjustment_instructions_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_adjustment_instructions_amount CHECK (
        amount_minor IS NULL OR amount_minor > 0
    ),
    -- The types that move money must say how much. A document request legitimately need not.
    CONSTRAINT ck_adjustment_instructions_monetary CHECK (
        adjustment_type NOT IN ('GUEST_CREDIT', 'HOST_RECOVERY', 'PLATFORM_GOODWILL',
                                'TAX_ADJUSTMENT')
            OR amount_minor IS NOT NULL
    ),
    CONSTRAINT ck_adjustment_instructions_dispatch CHECK (
        (state IN ('DISPATCHED', 'ACKNOWLEDGED') OR dispatched_at IS NULL)
            AND (state NOT IN ('DISPATCHED', 'ACKNOWLEDGED') OR dispatched_at IS NOT NULL)
    ),
    CONSTRAINT ck_adjustment_instructions_acknowledgement CHECK (
        (state = 'ACKNOWLEDGED') = (acknowledged_at IS NOT NULL)
    ),
    CONSTRAINT ck_adjustment_instructions_failure CHECK (
        state <> 'FAILED' OR failure_reason IS NOT NULL
    ),
    CONSTRAINT ck_adjustment_instructions_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_adjustment_instructions_version CHECK (version >= 0)
);

CREATE INDEX idx_adjustment_instructions_pending
    ON adjustment_instructions (target_domain, created_at)
    WHERE state IN ('PENDING', 'FAILED');
CREATE INDEX idx_adjustment_instructions_booking
    ON adjustment_instructions (booking_id, created_at DESC);
--rollback DROP TABLE adjustment_instructions;

--changeset ninggiangboy:023-12-booking-modification-proposals
-- A proposed change to a live contract, waiting for the other party. It is a separate row from the
-- revision it would produce because most proposals are never accepted, and an abandoned one must not
-- leave a half-changed booking behind.
--
-- A proposal that would move dates holds the new nights while it waits. Without that hold the guest
-- accepts a change to nights that were sold to somebody else in the meantime, and the platform finds
-- out at commit time -- after telling both parties it was agreed.
--
-- The money prerequisite is explicit rather than inferred: a change that costs more must name the
-- obligation that collects it, and one that costs less must name the refund instruction that returns
-- it, before it can be committed.
CREATE TABLE booking_modification_proposals (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                       VARCHAR(32) NOT NULL,
    booking_id                      UUID NOT NULL,
    current_revision_id             UUID NOT NULL,
    proposed_revision_id            UUID,

    initiated_by_actor_type         VARCHAR(24) NOT NULL,
    initiated_by_actor_id           UUID,
    change_type                     VARCHAR(24) NOT NULL,
    reason_code                     VARCHAR(64) NOT NULL,

    proposed_quote_id               UUID,
    requires_policy_acceptance      BOOLEAN NOT NULL DEFAULT false,
    policy_version_id               UUID,
    inventory_hold_id               UUID,
    hold_expires_at                 TIMESTAMPTZ,

    currency                        VARCHAR(3) NOT NULL,
    delta_direction                 VARCHAR(8) NOT NULL DEFAULT 'ZERO',
    delta_amount_minor              BIGINT NOT NULL DEFAULT 0,
    collection_obligation_id        UUID,
    refund_instruction_id           UUID,

    guest_approval_state            VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    guest_approved_at               TIMESTAMPTZ,
    host_approval_state             VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    host_approved_at                TIMESTAMPTZ,

    status                          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    expires_at                      TIMESTAMPTZ NOT NULL,
    committed_at                    TIMESTAMPTZ,
    declined_reason                 VARCHAR(64),

    request_hash                    CHAR(64) NOT NULL,
    input_hash                      CHAR(64) NOT NULL,
    result_hash                     CHAR(64),
    idempotency_key                 VARCHAR(128) NOT NULL,
    correlation_id                  VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_modification_proposals_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_booking_modification_proposals_current FOREIGN KEY (current_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_booking_modification_proposals_proposed FOREIGN KEY (proposed_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_booking_modification_proposals_quote FOREIGN KEY (proposed_quote_id)
        REFERENCES quotes (id),
    CONSTRAINT fk_booking_modification_proposals_policy FOREIGN KEY (policy_version_id)
        REFERENCES cancellation_policy_versions (id),
    CONSTRAINT fk_booking_modification_proposals_hold FOREIGN KEY (inventory_hold_id)
        REFERENCES inventory_holds (id),
    CONSTRAINT fk_booking_modification_proposals_obligation FOREIGN KEY (collection_obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_booking_modification_proposals_refund FOREIGN KEY (refund_instruction_id)
        REFERENCES refund_instructions (id),
    CONSTRAINT uk_booking_modification_proposals_public_id UNIQUE (public_id),
    CONSTRAINT uk_booking_modification_proposals_idempotency
        UNIQUE (booking_id, idempotency_key),
    CONSTRAINT uk_booking_modification_proposals_revision UNIQUE (proposed_revision_id),
    CONSTRAINT ck_booking_modification_proposals_actor CHECK (
        initiated_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_booking_modification_proposals_change CHECK (
        change_type IN ('DATES', 'GUEST_COUNT', 'UNIT_QUANTITY', 'LISTING_TRANSFER',
                        'RATE_PLAN', 'PRICE_CORRECTION', 'MIXED')
    ),
    CONSTRAINT ck_booking_modification_proposals_status CHECK (
        status IN ('OPEN', 'ACCEPTED', 'COMMITTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_booking_modification_proposals_approval_states CHECK (
        guest_approval_state IN ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'DECLINED')
            AND host_approval_state IN ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'DECLINED')
    ),
    CONSTRAINT ck_booking_modification_proposals_guest_approval CHECK (
        (guest_approval_state = 'APPROVED') = (guest_approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_booking_modification_proposals_host_approval CHECK (
        (host_approval_state = 'APPROVED') = (host_approved_at IS NOT NULL)
    ),
    -- Nothing is committed while a party who had to agree has not. This is the row-level version of
    -- the rule the service is meant to apply, kept here because a support tool bypassing the service
    -- is exactly how an unagreed change gets made.
    CONSTRAINT ck_booking_modification_proposals_consent CHECK (
        status <> 'COMMITTED'
            OR (guest_approval_state IN ('NOT_REQUIRED', 'APPROVED')
                AND host_approval_state IN ('NOT_REQUIRED', 'APPROVED'))
    ),
    CONSTRAINT ck_booking_modification_proposals_commit CHECK (
        (status = 'COMMITTED')
            = (committed_at IS NOT NULL AND proposed_revision_id IS NOT NULL)
    ),
    CONSTRAINT ck_booking_modification_proposals_decline CHECK (
        status <> 'DECLINED' OR declined_reason IS NOT NULL
    ),
    CONSTRAINT ck_booking_modification_proposals_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_booking_modification_proposals_direction CHECK (
        delta_direction IN ('ZERO', 'INCREASE', 'DECREASE')
    ),
    CONSTRAINT ck_booking_modification_proposals_delta CHECK (
        delta_amount_minor >= 0 AND (delta_direction = 'ZERO') = (delta_amount_minor = 0)
    ),
    -- A change that costs money cannot be committed without the row that collects or returns it.
    CONSTRAINT ck_booking_modification_proposals_money CHECK (
        status <> 'COMMITTED' OR delta_direction = 'ZERO'
            OR (delta_direction = 'INCREASE' AND collection_obligation_id IS NOT NULL)
            OR (delta_direction = 'DECREASE' AND refund_instruction_id IS NOT NULL)
    ),
    CONSTRAINT ck_booking_modification_proposals_policy CHECK (
        NOT requires_policy_acceptance OR policy_version_id IS NOT NULL
    ),
    -- A proposal holding inventory must say when the hold lapses, or the nights are never reclaimed.
    CONSTRAINT ck_booking_modification_proposals_hold CHECK (
        (inventory_hold_id IS NULL) = (hold_expires_at IS NULL)
    ),
    CONSTRAINT ck_booking_modification_proposals_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_booking_modification_proposals_request_hash CHECK (
        request_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_booking_modification_proposals_input_hash CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_booking_modification_proposals_result_hash CHECK (
        result_hash IS NULL OR result_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_booking_modification_proposals_version CHECK (version >= 0)
);

-- One live proposal per booking. Two open proposals would each hold different nights and each
-- believe they describe the next revision.
CREATE UNIQUE INDEX uk_booking_modification_proposals_open
    ON booking_modification_proposals (booking_id)
    WHERE status IN ('OPEN', 'ACCEPTED');
CREATE INDEX idx_booking_modification_proposals_expiry
    ON booking_modification_proposals (expires_at)
    WHERE status IN ('OPEN', 'ACCEPTED');
CREATE INDEX idx_booking_modification_proposals_booking
    ON booking_modification_proposals (booking_id, created_at DESC);
--rollback DROP INDEX uk_booking_modification_proposals_open;
--rollback DROP TABLE booking_modification_proposals;

--changeset ninggiangboy:023-13-booking-modification-deltas
-- What exactly changes, dimension by dimension, with the before and after side by side. A guest
-- asking "why does it cost more" gets an answer from these rows rather than from a total that moved.
CREATE TABLE booking_modification_deltas (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id                 UUID NOT NULL,
    dimension                   VARCHAR(32) NOT NULL,
    sequence_number             SMALLINT NOT NULL,

    source_allocation_reference VARCHAR(128),
    proposed_allocation_reference VARCHAR(128),
    before_value                VARCHAR(255),
    after_value                 VARCHAR(255),

    currency                    VARCHAR(3),
    before_amount_minor         BIGINT,
    after_amount_minor          BIGINT,
    added_amount_minor          BIGINT NOT NULL DEFAULT 0,
    removed_amount_minor        BIGINT NOT NULL DEFAULT 0,
    added_quantity              NUMERIC(10,3) NOT NULL DEFAULT 0,
    removed_quantity            NUMERIC(10,3) NOT NULL DEFAULT 0,

    rule_version                VARCHAR(64),
    explanation_code            VARCHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_booking_modification_deltas_proposal FOREIGN KEY (proposal_id)
        REFERENCES booking_modification_proposals (id) ON DELETE CASCADE,
    CONSTRAINT uk_booking_modification_deltas_position
        UNIQUE (proposal_id, dimension, sequence_number),
    -- A proposal cannot claim the same night, line or unit twice: the second claim would be
    -- double-counted into the delta the guest is asked to pay.
    CONSTRAINT uk_booking_modification_deltas_source
        UNIQUE (proposal_id, source_allocation_reference),
    CONSTRAINT ck_booking_modification_deltas_dimension CHECK (
        dimension IN ('NIGHT', 'LINE_ITEM', 'UNIT', 'GUEST_COUNT', 'RATE_PLAN', 'LISTING',
                      'TAX', 'PROMOTION', 'FEE')
    ),
    CONSTRAINT ck_booking_modification_deltas_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_booking_modification_deltas_money CHECK (
        (before_amount_minor IS NULL AND after_amount_minor IS NULL) OR currency IS NOT NULL
    ),
    CONSTRAINT ck_booking_modification_deltas_amounts CHECK (
        (before_amount_minor IS NULL OR before_amount_minor >= 0)
            AND (after_amount_minor IS NULL OR after_amount_minor >= 0)
            AND added_amount_minor >= 0 AND removed_amount_minor >= 0
            AND added_quantity >= 0 AND removed_quantity >= 0
    ),
    CONSTRAINT ck_booking_modification_deltas_number CHECK (sequence_number > 0)
);

CREATE INDEX idx_booking_modification_deltas_proposal
    ON booking_modification_deltas (proposal_id, dimension, sequence_number);
--rollback DROP TABLE booking_modification_deltas;

--changeset ninggiangboy:023-14-policy-override-programs
-- The approved reason for ignoring the policy the guest agreed to. An earthquake, an outbreak, a
-- platform outage: each is a named programme with an owner, a budget, a sunset date and a legal
-- approval, so that "we waived the fee" is a decision somebody signed rather than a habit.
CREATE TABLE policy_override_programs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_key                 VARCHAR(64) NOT NULL,
    display_name_key            VARCHAR(128) NOT NULL,
    event_category              VARCHAR(32) NOT NULL,

    owner_account_holder_id     UUID,
    legal_entity_id             UUID,
    market_code                 VARCHAR(2),

    lifecycle                   VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sunset_at                   TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    retrospective_review_due_at TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_policy_override_programs_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_policy_override_programs_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_policy_override_programs_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT uk_policy_override_programs_key UNIQUE (program_key),
    CONSTRAINT ck_policy_override_programs_category CHECK (
        event_category IN ('NATURAL_DISASTER', 'PUBLIC_HEALTH', 'CIVIL_UNREST', 'TRAVEL_RESTRICTION',
                           'PLATFORM_INCIDENT', 'SUPPLY_FAILURE', 'SAFETY', 'GOODWILL', 'LEGAL')
    ),
    CONSTRAINT ck_policy_override_programs_lifecycle CHECK (
        lifecycle IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_policy_override_programs_retirement CHECK (
        lifecycle <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_policy_override_programs_version CHECK (version >= 0)
);

CREATE INDEX idx_policy_override_programs_active
    ON policy_override_programs (event_category, sunset_at)
    WHERE lifecycle = 'ACTIVE';
--rollback DROP TABLE policy_override_programs;

--changeset ninggiangboy:023-15-policy-override-program-versions
-- The scope and the funding, frozen. Which geography, which dates, which evidence, whose money. It
-- is versioned separately from the programme because an event's footprint changes as it unfolds, and
-- a booking overridden under Tuesday's scope must still be explainable after Thursday's widening.
CREATE TABLE policy_override_program_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id                  UUID NOT NULL,
    version_number              INTEGER NOT NULL,

    scope_document              JSONB NOT NULL,
    eligibility_from            TIMESTAMPTZ NOT NULL,
    eligibility_to              TIMESTAMPTZ,
    application_window_ends_at  TIMESTAMPTZ,
    evidence_requirement        VARCHAR(24) NOT NULL DEFAULT 'NONE',
    decision_sla_hours          INTEGER,

    funding_party               VARCHAR(16) NOT NULL,
    guest_funded_basis_points   INTEGER NOT NULL DEFAULT 0,
    host_funded_basis_points    INTEGER NOT NULL DEFAULT 0,
    platform_funded_basis_points INTEGER NOT NULL DEFAULT 0,
    currency                    VARCHAR(3),
    budget_cap_minor            BIGINT,
    per_booking_cap_minor       BIGINT,

    content_hash                CHAR(64) NOT NULL,
    approved_by_actor_id        UUID,
    approved_at                 TIMESTAMPTZ,
    published_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_policy_override_program_versions_program FOREIGN KEY (program_id)
        REFERENCES policy_override_programs (id),
    CONSTRAINT fk_policy_override_program_versions_approver FOREIGN KEY (approved_by_actor_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_policy_override_program_versions_number UNIQUE (program_id, version_number),
    CONSTRAINT ck_policy_override_program_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_policy_override_program_versions_evidence CHECK (
        evidence_requirement IN ('NONE', 'SELF_DECLARED', 'DOCUMENTARY', 'AUTHORITATIVE')
    ),
    CONSTRAINT ck_policy_override_program_versions_funding CHECK (
        funding_party IN ('GUEST', 'HOST', 'PLATFORM', 'SHARED')
    ),
    -- The split is exhaustive. A programme that funds 60% from the platform and says nothing about
    -- the rest is how a host silently ends up paying for it.
    CONSTRAINT ck_policy_override_program_versions_split CHECK (
        guest_funded_basis_points >= 0 AND host_funded_basis_points >= 0
            AND platform_funded_basis_points >= 0
            AND guest_funded_basis_points + host_funded_basis_points
                + platform_funded_basis_points = 10000
    ),
    CONSTRAINT ck_policy_override_program_versions_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_policy_override_program_versions_budget CHECK (
        (budget_cap_minor IS NULL AND per_booking_cap_minor IS NULL) OR currency IS NOT NULL
    ),
    CONSTRAINT ck_policy_override_program_versions_caps CHECK (
        (budget_cap_minor IS NULL OR budget_cap_minor > 0)
            AND (per_booking_cap_minor IS NULL OR per_booking_cap_minor > 0)
    ),
    CONSTRAINT ck_policy_override_program_versions_eligibility CHECK (
        eligibility_to IS NULL OR eligibility_to > eligibility_from
    ),
    CONSTRAINT ck_policy_override_program_versions_sla CHECK (
        decision_sla_hours IS NULL OR decision_sla_hours > 0
    ),
    CONSTRAINT ck_policy_override_program_versions_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_policy_override_program_versions_approval CHECK (
        (approved_at IS NULL) = (approved_by_actor_id IS NULL)
    ),
    -- Nothing is settleable under an unapproved scope.
    CONSTRAINT ck_policy_override_program_versions_publication CHECK (
        published_at IS NULL OR approved_at IS NOT NULL
    )
);

CREATE INDEX idx_policy_override_program_versions_program
    ON policy_override_program_versions (program_id, version_number DESC);
--rollback DROP TABLE policy_override_program_versions;

--changeset ninggiangboy:023-16-policy-override-decisions
-- Whether one booking qualifies. A declared event never changes a booking by itself; each booking
-- gets its own immutable eligibility decision naming the programme version it was judged under and
-- the evidence that was weighed.
--
-- A rejection is recorded as fully as an approval, because the guest will ask why, and because a
-- pattern of rejections is how a badly scoped programme is discovered.
CREATE TABLE policy_override_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,
    program_id                  UUID NOT NULL,
    program_version_id          UUID NOT NULL,

    requested_by_actor_type     VARCHAR(24) NOT NULL,
    requested_by_actor_id       UUID,
    reviewer_type               VARCHAR(16) NOT NULL,
    reviewer_actor_id           UUID,

    evidence_reference          VARCHAR(255),
    evidence_class              VARCHAR(24) NOT NULL DEFAULT 'NONE',
    reason_code                 VARCHAR(64) NOT NULL,
    result                      VARCHAR(16) NOT NULL,
    result_note_key             VARCHAR(128),

    currency                    VARCHAR(3),
    granted_amount_minor        BIGINT,
    effective_from              TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,

    approval_reference          VARCHAR(128),
    correlation_id              VARCHAR(64) NOT NULL,
    decided_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_policy_override_decisions_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_policy_override_decisions_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_policy_override_decisions_program FOREIGN KEY (program_id)
        REFERENCES policy_override_programs (id),
    CONSTRAINT fk_policy_override_decisions_version FOREIGN KEY (program_version_id)
        REFERENCES policy_override_program_versions (id),
    -- One judgement per booking per programme. A second would let a guest re-apply until somebody
    -- said yes.
    CONSTRAINT uk_policy_override_decisions_booking UNIQUE (booking_id, program_id),
    CONSTRAINT ck_policy_override_decisions_requester CHECK (
        requested_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_policy_override_decisions_reviewer CHECK (
        reviewer_type IN ('AUTOMATED', 'AGENT', 'SPECIALIST', 'LEGAL')
    ),
    -- A human review must name the human. "An agent approved it" without an agent is not a record.
    CONSTRAINT ck_policy_override_decisions_reviewer_ref CHECK (
        reviewer_type = 'AUTOMATED' OR reviewer_actor_id IS NOT NULL
    ),
    CONSTRAINT ck_policy_override_decisions_evidence CHECK (
        evidence_class IN ('NONE', 'SELF_DECLARED', 'DOCUMENTARY', 'AUTHORITATIVE')
    ),
    CONSTRAINT ck_policy_override_decisions_evidence_ref CHECK (
        evidence_class = 'NONE' OR evidence_reference IS NOT NULL
    ),
    CONSTRAINT ck_policy_override_decisions_result CHECK (
        result IN ('APPROVED', 'REJECTED', 'DEFERRED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_policy_override_decisions_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_policy_override_decisions_money CHECK (
        (granted_amount_minor IS NULL) = (currency IS NULL)
    ),
    CONSTRAINT ck_policy_override_decisions_grant CHECK (
        granted_amount_minor IS NULL
            OR (granted_amount_minor > 0 AND result = 'APPROVED')
    ),
    CONSTRAINT ck_policy_override_decisions_expiry CHECK (
        expires_at IS NULL OR expires_at > effective_from
    )
);

CREATE INDEX idx_policy_override_decisions_program
    ON policy_override_decisions (program_id, decided_at DESC);
CREATE INDEX idx_policy_override_decisions_booking ON policy_override_decisions (booking_id);
--rollback DROP TABLE policy_override_decisions;

--changeset ninggiangboy:023-17-relocation-cases
-- A guest with nowhere to sleep tonight. This is the only part of the domain with a clock that
-- matters in minutes, so the case carries its own deadline and its own budget rather than borrowing
-- the cancellation's.
--
-- Hard constraints are stored explicitly because "anything nearby" is not an acceptable offer to a
-- guest travelling with a wheelchair, a pet, or a connecting flight at six.
CREATE TABLE relocation_cases (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                       VARCHAR(32) NOT NULL,
    booking_id                      UUID NOT NULL,
    booking_revision_id             UUID NOT NULL,
    cancellation_decision_id        UUID,

    cause                           VARCHAR(32) NOT NULL,
    opened_by_actor_type            VARCHAR(24) NOT NULL,
    opened_by_actor_id              UUID,
    owner_actor_id                  UUID,

    hard_constraints                JSONB NOT NULL DEFAULT '{}'::jsonb,
    guest_consent_state             VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    guest_consent_at                TIMESTAMPTZ,

    currency                        VARCHAR(3) NOT NULL,
    budget_cap_minor                BIGINT NOT NULL,
    approved_spend_minor            BIGINT NOT NULL DEFAULT 0,
    funding_party                   VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
    override_decision_id            UUID,

    replacement_booking_id          UUID,
    state                           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    outcome                         VARCHAR(24),
    resolution_due_at               TIMESTAMPTZ NOT NULL,
    resolved_at                     TIMESTAMPTZ,

    correlation_id                  VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_relocation_cases_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_relocation_cases_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_relocation_cases_decision FOREIGN KEY (cancellation_decision_id)
        REFERENCES cancellation_decisions (id),
    CONSTRAINT fk_relocation_cases_override FOREIGN KEY (override_decision_id)
        REFERENCES policy_override_decisions (id),
    CONSTRAINT fk_relocation_cases_replacement FOREIGN KEY (replacement_booking_id)
        REFERENCES bookings (id),
    CONSTRAINT uk_relocation_cases_public_id UNIQUE (public_id),
    CONSTRAINT ck_relocation_cases_cause CHECK (
        cause IN ('HOST_CANCELLATION', 'PROPERTY_UNAVAILABLE', 'SAFETY', 'MISREPRESENTATION',
                  'OVERBOOKING', 'NATURAL_EVENT', 'REGULATORY')
    ),
    CONSTRAINT ck_relocation_cases_opener CHECK (
        opened_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_relocation_cases_consent CHECK (
        guest_consent_state IN ('PENDING', 'GIVEN', 'REFUSED', 'NOT_REACHABLE')
    ),
    CONSTRAINT ck_relocation_cases_consent_time CHECK (
        (guest_consent_state = 'GIVEN') = (guest_consent_at IS NOT NULL)
    ),
    CONSTRAINT ck_relocation_cases_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Spending cannot exceed the budget that was approved for it, whatever an agent types into a
    -- tool at two in the morning.
    CONSTRAINT ck_relocation_cases_budget CHECK (
        budget_cap_minor > 0 AND approved_spend_minor >= 0
            AND approved_spend_minor <= budget_cap_minor
    ),
    CONSTRAINT ck_relocation_cases_funding CHECK (
        funding_party IN ('GUEST', 'HOST', 'PLATFORM', 'SHARED')
    ),
    CONSTRAINT ck_relocation_cases_state CHECK (
        state IN ('OPEN', 'OFFERING', 'ACCEPTED', 'RESOLVED', 'ABANDONED')
    ),
    CONSTRAINT ck_relocation_cases_outcome CHECK (
        outcome IS NULL OR outcome IN ('REBOOKED', 'REFUNDED', 'GUEST_DECLINED',
                                       'GUEST_SELF_ARRANGED', 'NO_CONTACT')
    ),
    CONSTRAINT ck_relocation_cases_resolution CHECK (
        (state = 'RESOLVED') = (resolved_at IS NOT NULL AND outcome IS NOT NULL)
    ),
    -- A case that says it rebooked the guest must name the booking it rebooked them into, and that
    -- booking cannot be the one that failed.
    CONSTRAINT ck_relocation_cases_replacement CHECK (
        outcome IS DISTINCT FROM 'REBOOKED' OR replacement_booking_id IS NOT NULL
    ),
    CONSTRAINT ck_relocation_cases_replacement_distinct CHECK (
        replacement_booking_id IS NULL OR replacement_booking_id <> booking_id
    ),
    CONSTRAINT ck_relocation_cases_version CHECK (version >= 0)
);

CREATE INDEX idx_relocation_cases_due ON relocation_cases (resolution_due_at)
    WHERE state IN ('OPEN', 'OFFERING', 'ACCEPTED');
CREATE INDEX idx_relocation_cases_booking ON relocation_cases (booking_id, created_at DESC);
--rollback DROP TABLE relocation_cases;

--changeset ninggiangboy:023-18-relocation-offers
-- What the guest was offered and what they said. Declined offers are kept: three declines is
-- evidence about the offers, not about the guest, and a case that settled on the fourth option needs
-- the first three to be explainable.
CREATE TABLE relocation_offers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relocation_case_id          UUID NOT NULL,
    sequence_number             SMALLINT NOT NULL,

    listing_id                  UUID,
    external_description        VARCHAR(500),
    stay_range                  DATERANGE NOT NULL,
    distance_metres             INTEGER,
    meets_hard_constraints      BOOLEAN NOT NULL DEFAULT true,

    currency                    VARCHAR(3) NOT NULL,
    offered_amount_minor        BIGINT NOT NULL,
    guest_contribution_minor    BIGINT NOT NULL DEFAULT 0,
    platform_cost_minor         BIGINT NOT NULL DEFAULT 0,
    host_cost_minor             BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(16) NOT NULL DEFAULT 'OFFERED',
    presented_at                TIMESTAMPTZ NOT NULL,
    responded_at                TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ,
    decline_reason              VARCHAR(64),
    resulting_booking_id        UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_relocation_offers_case FOREIGN KEY (relocation_case_id)
        REFERENCES relocation_cases (id) ON DELETE CASCADE,
    CONSTRAINT fk_relocation_offers_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_relocation_offers_booking FOREIGN KEY (resulting_booking_id)
        REFERENCES bookings (id),
    CONSTRAINT uk_relocation_offers_number UNIQUE (relocation_case_id, sequence_number),
    CONSTRAINT ck_relocation_offers_number CHECK (sequence_number > 0),
    -- An offer is either a listing on the platform or a described alternative off it, never neither.
    CONSTRAINT ck_relocation_offers_subject CHECK (
        listing_id IS NOT NULL OR external_description IS NOT NULL
    ),
    CONSTRAINT ck_relocation_offers_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_relocation_offers_distance CHECK (
        distance_metres IS NULL OR distance_metres >= 0
    ),
    CONSTRAINT ck_relocation_offers_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_relocation_offers_amounts CHECK (
        offered_amount_minor >= 0 AND guest_contribution_minor >= 0
            AND platform_cost_minor >= 0 AND host_cost_minor >= 0
    ),
    CONSTRAINT ck_relocation_offers_state CHECK (
        state IN ('OFFERED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_relocation_offers_response CHECK (
        (state IN ('ACCEPTED', 'DECLINED')) = (responded_at IS NOT NULL)
    ),
    CONSTRAINT ck_relocation_offers_decline CHECK (
        state <> 'DECLINED' OR decline_reason IS NOT NULL
    ),
    CONSTRAINT ck_relocation_offers_acceptance CHECK (
        resulting_booking_id IS NULL OR state = 'ACCEPTED'
    )
);

-- One accepted offer per case. A guest cannot be relocated into two replacement stays.
CREATE UNIQUE INDEX uk_relocation_offers_accepted ON relocation_offers (relocation_case_id)
    WHERE state = 'ACCEPTED';
CREATE INDEX idx_relocation_offers_case
    ON relocation_offers (relocation_case_id, sequence_number);
--rollback DROP INDEX uk_relocation_offers_accepted;
--rollback DROP TABLE relocation_offers;

--changeset ninggiangboy:023-19-relocation-expenses
-- Receipts. A taxi, a night in a hotel, a meal while waiting: each is a separate claim with its own
-- evidence and its own approval, because the case budget is spent in pieces and every piece has to
-- be justifiable to finance afterwards.
CREATE TABLE relocation_expenses (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    relocation_case_id          UUID NOT NULL,
    sequence_number             SMALLINT NOT NULL,
    expense_type                VARCHAR(24) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    claimed_amount_minor        BIGINT NOT NULL,
    approved_amount_minor       BIGINT NOT NULL DEFAULT 0,

    receipt_reference           VARCHAR(255),
    incurred_on                 DATE NOT NULL,
    claimed_by_actor_type       VARCHAR(24) NOT NULL,
    claimed_by_actor_id         UUID,

    state                       VARCHAR(16) NOT NULL DEFAULT 'CLAIMED',
    approved_by_actor_id        UUID,
    approved_at                 TIMESTAMPTZ,
    rejection_reason            VARCHAR(64),
    reimbursement_instruction_id UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_relocation_expenses_case FOREIGN KEY (relocation_case_id)
        REFERENCES relocation_cases (id) ON DELETE CASCADE,
    CONSTRAINT fk_relocation_expenses_approver FOREIGN KEY (approved_by_actor_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_relocation_expenses_reimbursement FOREIGN KEY (reimbursement_instruction_id)
        REFERENCES refund_instructions (id),
    CONSTRAINT uk_relocation_expenses_number UNIQUE (relocation_case_id, sequence_number),
    CONSTRAINT ck_relocation_expenses_number CHECK (sequence_number > 0),
    CONSTRAINT ck_relocation_expenses_type CHECK (
        expense_type IN ('ACCOMMODATION', 'TRANSPORT', 'MEALS', 'COMMUNICATION', 'STORAGE', 'OTHER')
    ),
    CONSTRAINT ck_relocation_expenses_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Nothing is approved for more than it was claimed for.
    CONSTRAINT ck_relocation_expenses_amounts CHECK (
        claimed_amount_minor > 0 AND approved_amount_minor >= 0
            AND approved_amount_minor <= claimed_amount_minor
    ),
    CONSTRAINT ck_relocation_expenses_claimant CHECK (
        claimed_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_relocation_expenses_state CHECK (
        state IN ('CLAIMED', 'APPROVED', 'REJECTED', 'REIMBURSED')
    ),
    CONSTRAINT ck_relocation_expenses_approval CHECK (
        (state NOT IN ('APPROVED', 'REIMBURSED')
            OR (approved_at IS NOT NULL AND approved_by_actor_id IS NOT NULL))
        AND (approved_at IS NULL OR state IN ('APPROVED', 'REIMBURSED'))
    ),
    CONSTRAINT ck_relocation_expenses_rejection CHECK (
        state <> 'REJECTED' OR rejection_reason IS NOT NULL
    ),
    CONSTRAINT ck_relocation_expenses_reimbursement CHECK (
        state <> 'REIMBURSED' OR reimbursement_instruction_id IS NOT NULL
    ),
    CONSTRAINT ck_relocation_expenses_version CHECK (version >= 0)
);

CREATE INDEX idx_relocation_expenses_case
    ON relocation_expenses (relocation_case_id, sequence_number);
CREATE INDEX idx_relocation_expenses_pending ON relocation_expenses (created_at)
    WHERE state = 'CLAIMED';
--rollback DROP TABLE relocation_expenses;

--changeset ninggiangboy:023-20-revision-links
-- The references that could not be constrained where their columns were declared, because the tables
-- they point at are created later in this same file, plus the two columns earlier migrations need in
-- order to speak about revisions at all.
--
-- bookings.current_revision_id is nullable on purpose. The booking row is written before its first
-- revision exists, and a NOT NULL column here would make the ordinary create path impossible without
-- a deferred constraint that buys nothing.
ALTER TABLE booking_revisions
    ADD CONSTRAINT fk_booking_revisions_proposal FOREIGN KEY (source_proposal_id)
        REFERENCES booking_modification_proposals (id),
    ADD CONSTRAINT fk_booking_revisions_decision FOREIGN KEY (source_decision_id)
        REFERENCES cancellation_decisions (id);

ALTER TABLE cancellation_decisions
    ADD CONSTRAINT fk_cancellation_decisions_override FOREIGN KEY (policy_override_decision_id)
        REFERENCES policy_override_decisions (id);

ALTER TABLE bookings
    ADD COLUMN current_revision_id UUID,
    ADD CONSTRAINT fk_bookings_current_revision FOREIGN KEY (current_revision_id)
        REFERENCES booking_revisions (id);

ALTER TABLE booking_policy_acceptances
    ADD COLUMN booking_revision_id UUID,
    ADD CONSTRAINT fk_booking_policy_acceptances_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id);

CREATE INDEX idx_bookings_current_revision ON bookings (current_revision_id)
    WHERE current_revision_id IS NOT NULL;
--rollback DROP INDEX idx_bookings_current_revision;
--rollback ALTER TABLE booking_policy_acceptances DROP CONSTRAINT fk_booking_policy_acceptances_revision, DROP COLUMN booking_revision_id;
--rollback ALTER TABLE bookings DROP CONSTRAINT fk_bookings_current_revision, DROP COLUMN current_revision_id;
--rollback ALTER TABLE cancellation_decisions DROP CONSTRAINT fk_cancellation_decisions_override;
--rollback ALTER TABLE booking_revisions DROP CONSTRAINT fk_booking_revisions_decision, DROP CONSTRAINT fk_booking_revisions_proposal;

--changeset ninggiangboy:023-21-refund-instruction-link
-- The promise migration 021 made. refund_executions.refund_instruction_id was declared NOT NULL with
-- no foreign key, because the instruction it names is decided here and this migration did not exist
-- yet. It is closed now, in the same forward style migration 020 used for the booking reference
-- migration 018 had to leave open, and 022 used for its own ledger dimensions.
--
-- With this in place, an execution cannot be created against an entitlement nobody decided: the
-- payment domain can still refuse to pay, but it can no longer pay something that was never owed.
ALTER TABLE refund_executions
    ADD CONSTRAINT fk_refund_executions_instruction FOREIGN KEY (refund_instruction_id)
        REFERENCES refund_instructions (id);
--rollback ALTER TABLE refund_executions DROP CONSTRAINT fk_refund_executions_instruction;

--changeset ninggiangboy:023-22-policy-immutability splitStatements:false
-- Published terms do not change. A booking settled next year cites this row, and if the rule
-- document could be edited the citation would prove nothing.
--
-- Retirement is the one permitted move, because a version has to be able to stop applying: state may
-- go to RETIRED, and effective_to and retired_at may be filled in to say when. Everything else --
-- the rule document, the hash, the evaluator, the cutoff semantics -- is fixed at publication.
CREATE FUNCTION cancellation_policy_versions_freeze_published() RETURNS TRIGGER AS $$
DECLARE
    candidate cancellation_policy_versions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'policy version % is published; it can be retired but never deleted',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.effective_to := OLD.effective_to;
    candidate.retired_at := OLD.retired_at;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'policy version % is published; only its retirement may still be recorded',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.state <> OLD.state AND NOT (OLD.state = 'PUBLISHED' AND NEW.state = 'RETIRED') THEN
        RAISE EXCEPTION
            'policy version % cannot move from % to %',
            OLD.id, OLD.state, NEW.state USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cancellation_policy_versions_frozen
    BEFORE UPDATE OR DELETE ON cancellation_policy_versions
    FOR EACH ROW
    WHEN (OLD.state IN ('PUBLISHED', 'RETIRED'))
    EXECUTE FUNCTION cancellation_policy_versions_freeze_published();

-- Approved disclosure text is frozen the same way, and for the same reason: an acceptance cites the
-- semantic hash of what the guest read.
--
-- INSERT is deliberately not covered here, unlike the decision lines below. A translation approved
-- in March is a legitimate addition to terms published in January -- it adds a locale rather than
-- changing one -- and the unique key on (policy_version_id, locale) means it can never quietly
-- replace an existing disclosure that an acceptance already names.
CREATE FUNCTION policy_disclosure_versions_freeze_approved() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'disclosure % is approved; its text cannot be altered or removed',
        OLD.id USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_policy_disclosure_versions_frozen
    BEFORE UPDATE OR DELETE ON policy_disclosure_versions
    FOR EACH ROW
    WHEN (OLD.approved_at IS NOT NULL)
    EXECUTE FUNCTION policy_disclosure_versions_freeze_approved();
--rollback DROP TRIGGER trg_policy_disclosure_versions_frozen ON policy_disclosure_versions;
--rollback DROP FUNCTION policy_disclosure_versions_freeze_approved();
--rollback DROP TRIGGER trg_cancellation_policy_versions_frozen ON cancellation_policy_versions;
--rollback DROP FUNCTION cancellation_policy_versions_freeze_published();

--changeset ninggiangboy:023-23-revision-immutability splitStatements:false
-- A committed revision is what the parties agreed to. Superseding it is a new row; editing it is a
-- rewrite of history that would make every downstream citation wrong.
--
-- Supersession is the one permitted move: status may go from COMMITTED to SUPERSEDED and
-- superseded_at may be filled in. The terms themselves never move.
CREATE FUNCTION booking_revisions_freeze_committed() RETURNS TRIGGER AS $$
DECLARE
    candidate booking_revisions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'revision % of booking % is committed; it can be superseded but never deleted',
            OLD.revision_number, OLD.booking_id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.superseded_at := OLD.superseded_at;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'revision % of booking % is committed; only its supersession may still be recorded',
            OLD.revision_number, OLD.booking_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status <> OLD.status AND NOT (OLD.status = 'COMMITTED' AND NEW.status = 'SUPERSEDED') THEN
        RAISE EXCEPTION
            'revision % of booking % cannot move from % to %',
            OLD.revision_number, OLD.booking_id, OLD.status, NEW.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_revisions_frozen
    BEFORE UPDATE OR DELETE ON booking_revisions
    FOR EACH ROW
    WHEN (OLD.status IN ('COMMITTED', 'SUPERSEDED'))
    EXECUTE FUNCTION booking_revisions_freeze_committed();

-- The nights of a committed revision are frozen too, and INSERT is covered along with UPDATE and
-- DELETE. A trigger that guards only UPDATE and DELETE still lets a night be appended to a revision
-- both parties already agreed to, which changes the stay just as surely as editing one would.
--
-- The consequence is the ordinary write order: a revision is created DRAFT, given its nights, and
-- then committed -- all in one database transaction. That is the sequence the evaluation service
-- follows anyway, because a revision is only committable once its nights are known.
CREATE FUNCTION booking_revision_nights_freeze_committed() RETURNS TRIGGER AS $$
DECLARE
    owning_id UUID;
    owning_status VARCHAR(16);
BEGIN
    owning_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.booking_revision_id
                      ELSE NEW.booking_revision_id END;
    SELECT status INTO owning_status FROM booking_revisions WHERE id = owning_id;

    IF owning_status IN ('COMMITTED', 'SUPERSEDED') THEN
        RAISE EXCEPTION
            'revision % is committed; its nights cannot be added to, altered, or removed',
            owning_id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_revision_nights_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON booking_revision_nights
    FOR EACH ROW EXECUTE FUNCTION booking_revision_nights_freeze_committed();
--rollback DROP TRIGGER trg_booking_revision_nights_frozen ON booking_revision_nights;
--rollback DROP FUNCTION booking_revision_nights_freeze_committed();
--rollback DROP TRIGGER trg_booking_revisions_frozen ON booking_revisions;
--rollback DROP FUNCTION booking_revisions_freeze_committed();

--changeset ninggiangboy:023-24-decision-immutability splitStatements:false
-- A committed decision is the number the guest was told. Correcting it means a superseding decision
-- that references this one, never an edit, because the original is the evidence that the platform
-- said what it said.
--
-- Two moves stay open on a committed row: recording that it was superseded, and recording the
-- revision it produced. The second is unavoidable -- the resulting revision does not exist until the
-- decision has been committed and acted on -- and it is a one-way write: it may be set once, never
-- changed or cleared.
CREATE FUNCTION cancellation_decisions_freeze_committed() RETURNS TRIGGER AS $$
DECLARE
    candidate cancellation_decisions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'decision % is committed; it can be superseded but never deleted',
            OLD.public_id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.superseded_by_decision_id := OLD.superseded_by_decision_id;
    candidate.resulting_revision_id := OLD.resulting_revision_id;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'decision % is committed; its arithmetic and its authority cannot be altered',
            OLD.public_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.resulting_revision_id IS NOT NULL
            AND NEW.resulting_revision_id IS DISTINCT FROM OLD.resulting_revision_id THEN
        RAISE EXCEPTION
            'decision % already produced revision %; it cannot be pointed at another',
            OLD.public_id, OLD.resulting_revision_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status <> OLD.status AND NOT (OLD.status = 'COMMITTED' AND NEW.status = 'SUPERSEDED') THEN
        RAISE EXCEPTION
            'decision % cannot move from % to %',
            OLD.public_id, OLD.status, NEW.status USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cancellation_decisions_frozen
    BEFORE UPDATE OR DELETE ON cancellation_decisions
    FOR EACH ROW
    WHEN (OLD.status IN ('COMMITTED', 'SUPERSEDED'))
    EXECUTE FUNCTION cancellation_decisions_freeze_committed();

-- The lines are frozen with the decision, INSERT included. An extra line appended to a committed
-- decision would change the allocation while leaving the totals -- and the guest's screen --
-- untouched, which is the quietest way this domain could go wrong.
--
-- So a decision is written DRAFT, given its lines, and committed, in one database transaction. The
-- deferred sum rule in the next changeset checks the whole set at COMMIT, which is the only point at
-- which the set is complete.
CREATE FUNCTION cancellation_decision_lines_freeze_committed() RETURNS TRIGGER AS $$
DECLARE
    owning_id UUID;
    owning_status VARCHAR(16);
BEGIN
    owning_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.cancellation_decision_id
                      ELSE NEW.cancellation_decision_id END;
    SELECT status INTO owning_status FROM cancellation_decisions WHERE id = owning_id;

    IF owning_status IN ('COMMITTED', 'SUPERSEDED') THEN
        RAISE EXCEPTION
            'decision % is committed; its lines cannot be added to, altered, or removed',
            owning_id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cancellation_decision_lines_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON cancellation_decision_lines
    FOR EACH ROW EXECUTE FUNCTION cancellation_decision_lines_freeze_committed();
--rollback DROP TRIGGER trg_cancellation_decision_lines_frozen ON cancellation_decision_lines;
--rollback DROP FUNCTION cancellation_decision_lines_freeze_committed();
--rollback DROP TRIGGER trg_cancellation_decisions_frozen ON cancellation_decisions;
--rollback DROP FUNCTION cancellation_decisions_freeze_committed();

--changeset ninggiangboy:023-25-decision-allocation-balance splitStatements:false
-- The rule no single row can see: a committed decision's lines must account for exactly the totals
-- the decision states, in exactly one currency.
--
-- The feature document says cross-row sums cannot safely be expressed in the database and must be
-- left to locked service validation plus scheduled reconciliation. That is true of a CHECK
-- constraint, which sees one row. A DEFERRABLE INITIALLY DEFERRED constraint trigger sees the whole
-- set at COMMIT, after every line is written and after the decision has been moved to COMMITTED --
-- which is the only moment the question can be asked. Migration 022 uses the same mechanism to make
-- the journal balance.
--
-- The trigger is on both tables on purpose. On the lines alone, a decision could be written with
-- correct lines, committed in the same transaction, and then have its totals changed by a later
-- transaction that touches no line at all. Firing on the decision as well closes that path.
--
-- Draft decisions are skipped: an allocation under construction is expected not to add up yet.
CREATE FUNCTION cancellation_decisions_validate_allocation() RETURNS TRIGGER AS $$
DECLARE
    target_decision UUID;
    decision_status VARCHAR(16);
    decision_ccy VARCHAR(3);
    decision_refund BIGINT;
    decision_retained BIGINT;
    decision_new_due BIGINT;
    line_refund BIGINT;
    line_retained BIGINT;
    line_new_due BIGINT;
    line_count INTEGER;
    currency_count INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'cancellation_decisions' THEN
        target_decision := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target_decision := OLD.cancellation_decision_id;
    ELSE
        target_decision := NEW.cancellation_decision_id;
    END IF;

    SELECT status, currency, refund_amount_minor, retained_amount_minor, new_due_amount_minor
      INTO decision_status, decision_ccy, decision_refund, decision_retained, decision_new_due
      FROM cancellation_decisions WHERE id = target_decision;

    IF decision_status IS NULL OR decision_status NOT IN ('COMMITTED', 'SUPERSEDED') THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(sum(refund_amount_minor), 0), COALESCE(sum(retained_amount_minor), 0),
           COALESCE(sum(new_due_amount_minor), 0), count(*), count(DISTINCT currency)
      INTO line_refund, line_retained, line_new_due, line_count, currency_count
      FROM cancellation_decision_lines WHERE cancellation_decision_id = target_decision;

    IF line_count = 0 THEN
        RAISE EXCEPTION
            'committed decision % has no allocation lines',
            target_decision USING ERRCODE = 'check_violation';
    END IF;

    IF currency_count <> 1 OR NOT EXISTS (
            SELECT 1 FROM cancellation_decision_lines
            WHERE cancellation_decision_id = target_decision AND currency = decision_ccy) THEN
        RAISE EXCEPTION
            'committed decision % must allocate a single currency matching its own (%)',
            target_decision, decision_ccy USING ERRCODE = 'check_violation';
    END IF;

    IF line_refund <> decision_refund THEN
        RAISE EXCEPTION
            'committed decision % refunds % but its lines allocate %',
            target_decision, decision_refund, line_refund USING ERRCODE = 'check_violation';
    END IF;

    IF line_retained <> decision_retained THEN
        RAISE EXCEPTION
            'committed decision % retains % but its lines allocate %',
            target_decision, decision_retained, line_retained USING ERRCODE = 'check_violation';
    END IF;

    IF line_new_due <> decision_new_due THEN
        RAISE EXCEPTION
            'committed decision % charges % more but its lines allocate %',
            target_decision, decision_new_due, line_new_due USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_cancellation_decision_lines_balance
    AFTER INSERT OR UPDATE OR DELETE ON cancellation_decision_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cancellation_decisions_validate_allocation();

CREATE CONSTRAINT TRIGGER trg_cancellation_decisions_balance
    AFTER INSERT OR UPDATE ON cancellation_decisions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION cancellation_decisions_validate_allocation();
--rollback DROP TRIGGER trg_cancellation_decisions_balance ON cancellation_decisions;
--rollback DROP TRIGGER trg_cancellation_decision_lines_balance ON cancellation_decision_lines;
--rollback DROP FUNCTION cancellation_decisions_validate_allocation();
