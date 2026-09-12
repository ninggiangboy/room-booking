--liquibase formatted sql

-- A stay is the part of a booking that happens in a building. This migration exists so that what was
-- prepared, who was let in, what was observed and what went wrong are durable facts with custody --
-- and so that none of them can quietly become a booking decision. Migration 024 delivered the half of
-- this feature document that carries words; this is the half that carries operations.
--
-- Six things force the shape:
--
--   Readiness is not one status. A property can be ready while access provisioning failed; a guest
--   can report arrival while a safety case is open; a stay can be over while its outcome is under
--   review. The feature document is explicit that these dimensions must not be collapsed, so an
--   operational stay carries five independent state columns rather than one, exactly as booking
--   carries five in migration 020.
--
--   Instructions are released, not stored once. Address, entry route and access code sit in different
--   sensitivity bands with different release conditions, changes after release create a superseding
--   version rather than an edit, and every retrieval is audited with its purpose and outcome. A
--   revocation stops future retrieval; it cannot retract a code already seen, which is why the audit
--   is append-only and replacement is a first-class operation rather than a correction.
--
--   An access grant is platform authorization; a provider credential is one way of fulfilling it.
--   Keeping them apart is what lets a smart-lock outage fall back to an in-person handoff without
--   rewriting the guest's entitlement, and what lets an unknown revocation outcome be treated as
--   "possibly still open" instead of silently succeeding. One live grant per stay, person, unit and
--   mode is a partial unique index, not a service convention.
--
--   Evidence is collected; conclusions are separate and additive. A lock opening may be a cleaner, a
--   location signal may be spoofed, a read receipt proves nothing about arrival. Observations are
--   therefore immutable rows with source, both times, confidence and provenance, and the completion
--   or no-show proposal is a separate decision row naming the policy version and the evidence it
--   weighed. Booking still owns the transition; this domain only proposes.
--
--   An incident has an order and a floor. Its timeline is an append-only sequence allocated by the
--   incident row, so two agents acting at once cannot produce two "third" events; and a severity a
--   person declared as a safety report cannot be lowered by a model, a script or a quiet update
--   without a named reviewer and a reason.
--
--   Operations never edits another domain's tables. Every cross-domain ask -- an emergency calendar
--   block, a refund, a payout hold, a relocation budget -- is a remedy request row with its own
--   idempotency identity, and the owning domain's answer is recorded as a reference. That is the
--   difference between asking and reaching across.
--
-- Note on what this migration does not create. The document proposes outbox_events, inbox_receipts,
-- provider_webhook_inbox, idempotency_records, policy_versions, audit_records and legal_holds as
-- shared records. Migration 012 delivered command_idempotency_records, outbox_events,
-- consumer_inbox_receipts and append-only audit_events, 013 delivered provider_accounts, 021
-- delivered payment_webhook_deliveries and 024 delivered the conversation an incident hangs off. A
-- second copy of any of them would mean two answers to the same question. Legal hold stays a column
-- on the rows that can be held, as it is in 021 and 024.
--
-- Note on secrets. No access code, token or provider credential is stored here. A grant carries a
-- reference to an envelope-encrypted secret and the key reference used to open it; the plaintext
-- exists only in the response to an authorized reveal, and the reveal itself is counted and audited.

--changeset ninggiangboy:025-01-operational-stays
-- The operational view of one committed booking revision: where it is, when it starts and ends in
-- local terms, and the five independent things that can be true about it at once. It is created only
-- for an eligible revision and superseded rather than edited when the booking changes.
CREATE TABLE operational_stays (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,

    property_id                 UUID NOT NULL,
    listing_id                  UUID NOT NULL,
    physical_unit_id            UUID,
    market_code                 VARCHAR(2) NOT NULL,

    guest_account_holder_id     UUID NOT NULL,
    host_account_holder_id      UUID NOT NULL,

    -- The civil facts are snapshotted with the zone that resolved them, because a property's zone
    -- database entry can change and a stay must still be readable as the parties agreed it.
    property_time_zone          VARCHAR(64) NOT NULL,
    time_zone_version           VARCHAR(16),
    check_in_date               DATE NOT NULL,
    check_out_date              DATE NOT NULL,
    check_in_local_time         TIME NOT NULL,
    check_out_local_time        TIME NOT NULL,
    check_in_instant            TIMESTAMPTZ NOT NULL,
    check_out_instant           TIMESTAMPTZ NOT NULL,

    -- Five dimensions, deliberately not one. Collapsing them would force a lie the first time
    -- a property was ready but its lock was not, which is the ordinary case rather than the
    -- exception.
    preparation_state           VARCHAR(16) NOT NULL DEFAULT 'NOT_SCHEDULED',
    access_state                VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    presence_state              VARCHAR(24) NOT NULL DEFAULT 'NONE',
    incident_exposure           VARCHAR(20) NOT NULL DEFAULT 'NONE',
    outcome_proposal            VARCHAR(24) NOT NULL DEFAULT 'PENDING',

    access_policy_version       VARCHAR(32),
    instruction_policy_version  VARCHAR(32),
    completion_policy_version   VARCHAR(32),
    turnover_buffer_minutes     INTEGER NOT NULL DEFAULT 0,

    status                      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    superseded_by_stay_id       UUID,
    superseded_at               TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_operational_stays_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_operational_stays_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_operational_stays_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_operational_stays_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_operational_stays_unit FOREIGN KEY (physical_unit_id) REFERENCES physical_units (id),
    CONSTRAINT fk_operational_stays_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operational_stays_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    -- Deferred on purpose. The live-stay index below means the outgoing stay has to be marked
    -- superseded before its replacement can exist, so the successor reference is written a moment
    -- before the row it names. Checking it at commit keeps that order legal without weakening it.
    CONSTRAINT fk_operational_stays_supersession FOREIGN KEY (superseded_by_stay_id)
        REFERENCES operational_stays (id) DEFERRABLE INITIALLY DEFERRED,
    -- One operational view per revision. A modification produces a new revision and therefore a new
    -- stay, so the old preparation record stays readable next to the new one.
    CONSTRAINT uk_operational_stays_revision UNIQUE (booking_id, booking_revision_id),
    CONSTRAINT ck_operational_stays_preparation CHECK (
        preparation_state IN ('NOT_SCHEDULED', 'SCHEDULED', 'IN_PROGRESS', 'READY', 'BLOCKED', 'UNKNOWN')
    ),
    CONSTRAINT ck_operational_stays_access CHECK (
        access_state IN ('NOT_REQUIRED', 'PENDING', 'ACTIVE', 'FAILED', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT ck_operational_stays_presence CHECK (
        presence_state IN ('NONE', 'ARRIVAL_REPORTED', 'CHECKED_IN_RECORDED', 'DEPARTURE_REPORTED')
    ),
    CONSTRAINT ck_operational_stays_exposure CHECK (
        incident_exposure IN ('NONE', 'OPEN_NON_SAFETY', 'OPEN_SAFETY', 'RESOLVED', 'TRANSFERRED')
    ),
    CONSTRAINT ck_operational_stays_outcome CHECK (
        outcome_proposal IN ('PENDING', 'COMPLETION_ELIGIBLE', 'NO_SHOW_REVIEW', 'CANCELLED', 'EXCEPTION')
    ),
    CONSTRAINT ck_operational_stays_status CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'CLOSED')),
    CONSTRAINT ck_operational_stays_supersession CHECK (
        (status = 'SUPERSEDED') = (superseded_by_stay_id IS NOT NULL AND superseded_at IS NOT NULL)
    ),
    CONSTRAINT ck_operational_stays_closure CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL),
    CONSTRAINT ck_operational_stays_self_supersession CHECK (superseded_by_stay_id <> id),
    CONSTRAINT ck_operational_stays_dates CHECK (check_out_date > check_in_date),
    CONSTRAINT ck_operational_stays_instants CHECK (check_out_instant > check_in_instant),
    CONSTRAINT ck_operational_stays_buffer CHECK (turnover_buffer_minutes >= 0),
    CONSTRAINT ck_operational_stays_version CHECK (version >= 0)
);

-- At most one live operational stay per booking. Two would mean two answers to "is it ready".
CREATE UNIQUE INDEX uk_operational_stays_live ON operational_stays (booking_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_operational_stays_arrival ON operational_stays (check_in_instant)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_operational_stays_completion ON operational_stays (check_out_instant)
    WHERE status = 'ACTIVE' AND outcome_proposal = 'PENDING';
CREATE INDEX idx_operational_stays_property ON operational_stays (property_id, check_in_date);
CREATE INDEX idx_operational_stays_guest ON operational_stays (guest_account_holder_id, check_in_date DESC);
--rollback DROP TABLE operational_stays;

--changeset ninggiangboy:025-02-instruction-sets
-- A versioned arrival instruction, banded by sensitivity. The bands are separate references rather
-- than one blob because release is evaluated per band: a guest may see the arrival window long before
-- the exact address, and the access code later still.
CREATE TABLE instruction_sets (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operational_stay_id         UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,
    version_number              INTEGER NOT NULL,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    authored_by_account_holder_id UUID,
    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,

    -- One reference per sensitivity band. Nothing here is the content itself; the content lives
    -- behind these references so that a band can be withheld without the row knowing its text.
    early_fields_reference      VARCHAR(256),
    confirmed_fields_reference  VARCHAR(256),
    time_gated_fields_reference VARCHAR(256),
    secret_fields_reference     VARCHAR(256),
    post_entry_fields_reference VARCHAR(256),
    content_hash                CHAR(64) NOT NULL,

    -- Release conditions are a closed set of approved predicates. A host cannot invent a payment or
    -- identity demand by writing prose into an instruction; they can only turn these on.
    requires_confirmed_booking  BOOLEAN NOT NULL DEFAULT true,
    requires_payment_satisfied  BOOLEAN NOT NULL DEFAULT true,
    requires_identity_verified  BOOLEAN NOT NULL DEFAULT false,
    time_gate_opens_at          TIMESTAMPTZ,

    supersedes_instruction_set_id UUID,
    supersession_reason         VARCHAR(48),
    acknowledgement_required    BOOLEAN NOT NULL DEFAULT false,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(48),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_instruction_sets_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_instruction_sets_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_instruction_sets_author FOREIGN KEY (authored_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_instruction_sets_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_instruction_sets_supersedes FOREIGN KEY (supersedes_instruction_set_id)
        REFERENCES instruction_sets (id),
    CONSTRAINT uk_instruction_sets_version UNIQUE (operational_stay_id, version_number),
    CONSTRAINT ck_instruction_sets_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'RELEASED', 'SUPERSEDED', 'REVOKED')
    ),
    CONSTRAINT ck_instruction_sets_version_number CHECK (version_number > 0),
    CONSTRAINT ck_instruction_sets_release CHECK (
        status <> 'RELEASED' OR (effective_from IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_instruction_sets_approval CHECK (
        (approved_at IS NULL) = (approved_by_account_holder_id IS NULL)
    ),
    CONSTRAINT ck_instruction_sets_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_instruction_sets_supersession CHECK (
        (status = 'SUPERSEDED') = (supersession_reason IS NOT NULL)
    ),
    CONSTRAINT ck_instruction_sets_revocation CHECK (
        (status = 'REVOKED') = (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_instruction_sets_self_supersession CHECK (supersedes_instruction_set_id <> id),
    CONSTRAINT ck_instruction_sets_row_version CHECK (version >= 0)
);

-- One current version per stay. Two released versions would mean the guest and the cleaner could be
-- reading different entry routes at the same moment.
CREATE UNIQUE INDEX uk_instruction_sets_current ON instruction_sets (operational_stay_id)
    WHERE status = 'RELEASED';
CREATE INDEX idx_instruction_sets_stay ON instruction_sets (operational_stay_id, version_number DESC);
--rollback DROP TABLE instruction_sets;

--changeset ninggiangboy:025-03-instruction-access-audit
-- Who asked for which band, why, and what they got. Append-only: this is the record consulted when a
-- code turns out to have been used by somebody who should not have had it, and its value depends
-- entirely on nobody being able to tidy it afterwards.
CREATE TABLE instruction_access_audit (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instruction_set_id          UUID NOT NULL,
    operational_stay_id         UUID NOT NULL,

    actor_type                  VARCHAR(16) NOT NULL,
    actor_account_holder_id     UUID,
    auth_session_id             UUID,
    assurance_level             VARCHAR(8),
    device_risk_reference       VARCHAR(64),

    purpose_code                VARCHAR(48) NOT NULL,
    requested_field_class       VARCHAR(24) NOT NULL,
    released_field_classes      VARCHAR(24)[] NOT NULL DEFAULT '{}',

    decision                    VARCHAR(16) NOT NULL,
    denial_reason               VARCHAR(48),

    correlation_id              UUID,
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_instruction_access_audit_set FOREIGN KEY (instruction_set_id)
        REFERENCES instruction_sets (id),
    CONSTRAINT fk_instruction_access_audit_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_instruction_access_audit_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_instruction_access_audit_session FOREIGN KEY (auth_session_id)
        REFERENCES auth_sessions (id),
    CONSTRAINT ck_instruction_access_audit_actor CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER', 'ANONYMOUS')
    ),
    CONSTRAINT ck_instruction_access_audit_assurance CHECK (
        assurance_level IS NULL OR assurance_level IN ('AAL1', 'AAL2', 'AAL3')
    ),
    CONSTRAINT ck_instruction_access_audit_field_class CHECK (
        requested_field_class IN ('PUBLIC_EARLY', 'CONFIRMED_BOOKING', 'TIME_GATED', 'SECRET', 'POST_ENTRY')
    ),
    CONSTRAINT ck_instruction_access_audit_decision CHECK (
        decision IN ('ALLOWED', 'DENIED', 'FAILED', 'PARTIAL')
    ),
    -- A refusal that does not say why is not an audit record, and an allowance that names nothing
    -- released cannot be reconciled against what the guest actually saw.
    CONSTRAINT ck_instruction_access_audit_denial CHECK (
        decision NOT IN ('DENIED', 'PARTIAL') OR denial_reason IS NOT NULL
    ),
    CONSTRAINT ck_instruction_access_audit_release CHECK (
        decision NOT IN ('ALLOWED', 'PARTIAL') OR cardinality(released_field_classes) > 0
    )
);

CREATE INDEX idx_instruction_access_audit_set
    ON instruction_access_audit (instruction_set_id, occurred_at DESC);
CREATE INDEX idx_instruction_access_audit_actor
    ON instruction_access_audit (actor_account_holder_id, occurred_at DESC);
-- Reveals of the secret band are what a compromised-access investigation starts from.
CREATE INDEX idx_instruction_access_audit_secret ON instruction_access_audit (occurred_at DESC)
    WHERE requested_field_class = 'SECRET';
--rollback DROP TABLE instruction_access_audit;

--changeset ninggiangboy:025-04-access-grants
-- The platform's own answer to "is this person entitled to open this door, when". A provider
-- credential is one way of honouring it; an in-person handoff is another. Keeping the entitlement
-- separate from the credential is what makes a lock outage a fulfilment problem rather than a
-- question about whether the guest may enter at all.
CREATE TABLE access_grants (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operational_stay_id         UUID NOT NULL,
    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,

    subject_account_holder_id   UUID NOT NULL,
    party_role                  VARCHAR(16) NOT NULL,
    property_id                 UUID NOT NULL,
    physical_unit_id            UUID,

    access_mode                 VARCHAR(24) NOT NULL,
    provider_account_id         UUID,
    device_reference            VARCHAR(64),
    instruction_set_id          UUID,
    access_policy_version       VARCHAR(32) NOT NULL,

    -- Validity begins no earlier than approved arrival access and ends after checkout plus a bounded
    -- grace. Changeset 025-19 is what stops the window being widened without a new booking revision.
    valid_from                  TIMESTAMPTZ NOT NULL,
    valid_until                 TIMESTAMPTZ NOT NULL,

    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',

    -- The secret is not here. These are the envelope-encrypted artifact and the key it needs, so a
    -- database reader with no key access learns that a credential exists and nothing more.
    secret_reference            VARCHAR(256),
    secret_key_reference        VARCHAR(128),
    secret_last_revealed_at     TIMESTAMPTZ,
    reveal_count                INTEGER NOT NULL DEFAULT 0,

    revocation_requested_at     TIMESTAMPTZ,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(48),
    -- An unknown revocation outcome is not a revocation. Operations must treat the code as possibly
    -- live and follow the compromised-access runbook, so the fact is recorded rather than assumed.
    revocation_outcome_known    BOOLEAN NOT NULL DEFAULT false,

    fallback_mode               VARCHAR(24),
    fallback_authorized_by      UUID,
    fallback_authorized_at      TIMESTAMPTZ,

    failure_reason              VARCHAR(48),
    superseded_by_grant_id      UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_grants_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_access_grants_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_access_grants_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_access_grants_subject FOREIGN KEY (subject_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_access_grants_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_access_grants_unit FOREIGN KEY (physical_unit_id) REFERENCES physical_units (id),
    CONSTRAINT fk_access_grants_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_access_grants_instructions FOREIGN KEY (instruction_set_id)
        REFERENCES instruction_sets (id),
    CONSTRAINT fk_access_grants_fallback_authorizer FOREIGN KEY (fallback_authorized_by)
        REFERENCES account_holders (id),
    -- Deferred for the same reason as the stay supersession above: the live-grant index requires
    -- the outgoing grant to name its replacement before the replacement row exists.
    CONSTRAINT fk_access_grants_supersession FOREIGN KEY (superseded_by_grant_id)
        REFERENCES access_grants (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_access_grants_party_role CHECK (
        party_role IN ('GUEST', 'HOST', 'CO_HOST', 'OPERATOR', 'SUPPORT', 'CONTRACTOR')
    ),
    CONSTRAINT ck_access_grants_mode CHECK (
        access_mode IN ('SMART_LOCK', 'KEYPAD_CODE', 'MOBILE_KEY', 'LOCKBOX',
                        'IN_PERSON_HANDOFF', 'FRONT_DESK')
    ),
    CONSTRAINT ck_access_grants_fallback_mode CHECK (
        fallback_mode IS NULL OR fallback_mode IN ('SMART_LOCK', 'KEYPAD_CODE', 'MOBILE_KEY',
                                                  'LOCKBOX', 'IN_PERSON_HANDOFF', 'FRONT_DESK')
    ),
    CONSTRAINT ck_access_grants_state CHECK (
        state IN ('DRAFT', 'ELIGIBLE', 'PROVISIONING', 'ACTIVE', 'EXPIRED',
                  'UNKNOWN', 'REVOKING', 'REVOKED', 'FAILED')
    ),
    CONSTRAINT ck_access_grants_validity CHECK (valid_until > valid_from),
    -- A provider-fulfilled mode needs an account to talk to; a manual mode must not pretend to have
    -- one, because that is how an outage gets mistaken for a working lock.
    CONSTRAINT ck_access_grants_provider_required CHECK (
        access_mode NOT IN ('SMART_LOCK', 'KEYPAD_CODE', 'MOBILE_KEY') OR provider_account_id IS NOT NULL
    ),
    -- An active credential-based grant must name the credential it issued. Without this a provider
    -- failure could leave a grant reading ACTIVE with nothing behind it.
    CONSTRAINT ck_access_grants_active_secret CHECK (
        state <> 'ACTIVE'
            OR access_mode IN ('IN_PERSON_HANDOFF', 'FRONT_DESK', 'LOCKBOX')
            OR (secret_reference IS NOT NULL AND secret_key_reference IS NOT NULL)
    ),
    CONSTRAINT ck_access_grants_revoking CHECK (
        state <> 'REVOKING' OR (revocation_requested_at IS NOT NULL AND revocation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_access_grants_revoked CHECK (
        state <> 'REVOKED'
            OR (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL
                AND revocation_outcome_known)
    ),
    CONSTRAINT ck_access_grants_failure CHECK (state <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT ck_access_grants_fallback CHECK (
        fallback_mode IS NULL
            OR (fallback_authorized_by IS NOT NULL AND fallback_authorized_at IS NOT NULL)
    ),
    CONSTRAINT ck_access_grants_reveal CHECK (
        reveal_count >= 0 AND (reveal_count = 0) = (secret_last_revealed_at IS NULL)
    ),
    CONSTRAINT ck_access_grants_self_supersession CHECK (superseded_by_grant_id <> id),
    CONSTRAINT ck_access_grants_version CHECK (version >= 0)
);

-- One live grant per stay, person, unit and mode. Two would mean two codes in circulation with no
-- way to say which one revocation was about.
CREATE UNIQUE INDEX uk_access_grants_live
    ON access_grants (operational_stay_id, subject_account_holder_id, physical_unit_id, access_mode)
    NULLS NOT DISTINCT
    WHERE state IN ('ELIGIBLE', 'PROVISIONING', 'ACTIVE', 'UNKNOWN', 'REVOKING');
CREATE INDEX idx_access_grants_validity ON access_grants (valid_until)
    WHERE state IN ('ACTIVE', 'UNKNOWN');
CREATE INDEX idx_access_grants_stay ON access_grants (operational_stay_id, created_at DESC);
-- The queue an operator watches: entitlements whose fulfilment outcome is not known.
CREATE INDEX idx_access_grants_unresolved ON access_grants (updated_at)
    WHERE state IN ('PROVISIONING', 'UNKNOWN', 'REVOKING');
--rollback DROP TABLE access_grants;

--changeset ninggiangboy:025-05-access-operations
-- One attempt to make a provider agree with the grant. Provision, rotate and revoke carry distinct
-- stable keys so a retry of one can never be mistaken for the other -- which matters most for revoke,
-- where a lost response must be retried rather than assumed.
CREATE TABLE access_operations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_grant_id             UUID NOT NULL,
    operation_type              VARCHAR(16) NOT NULL,
    operation_key               VARCHAR(96) NOT NULL,
    attempt_number              INTEGER NOT NULL DEFAULT 1,

    provider_account_id         UUID,
    provider_reference          VARCHAR(128),
    request_hash                CHAR(64) NOT NULL,

    state                       VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    failure_category            VARCHAR(32),
    failure_detail              VARCHAR(256),

    claimed_at                  TIMESTAMPTZ,
    claimed_by                  VARCHAR(64),
    lease_expires_at            TIMESTAMPTZ,
    submitted_at                TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    next_retry_at               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_access_operations_grant FOREIGN KEY (access_grant_id) REFERENCES access_grants (id),
    CONSTRAINT fk_access_operations_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_access_operations_key UNIQUE (access_grant_id, operation_type, operation_key),
    CONSTRAINT ck_access_operations_type CHECK (
        operation_type IN ('PROVISION', 'ROTATE', 'REVOKE', 'QUERY', 'EXTEND')
    ),
    CONSTRAINT ck_access_operations_state CHECK (
        state IN ('PLANNED', 'SUBMITTING', 'PENDING', 'SUCCEEDED', 'FAILED',
                  'UNKNOWN', 'CANCELLED_BEFORE_SUBMISSION')
    ),
    CONSTRAINT ck_access_operations_failure_category CHECK (
        failure_category IS NULL OR failure_category IN (
            'PROVIDER_REJECTED', 'DEVICE_UNREACHABLE', 'DEVICE_BATTERY', 'AUTHENTICATION',
            'RATE_LIMITED', 'TIMEOUT', 'INVALID_REQUEST', 'INTERNAL'
        )
    ),
    -- The submission fence. A row that was never put on the wire may be cancelled freely; one that
    -- was must resolve to a real outcome, and a timeout can only be UNKNOWN.
    CONSTRAINT ck_access_operations_submission CHECK (
        (submitted_at IS NULL) = (state IN ('PLANNED', 'CANCELLED_BEFORE_SUBMISSION'))
    ),
    CONSTRAINT ck_access_operations_failure CHECK (
        state <> 'FAILED' OR failure_category IS NOT NULL
    ),
    CONSTRAINT ck_access_operations_completion CHECK (
        state NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED_BEFORE_SUBMISSION') OR completed_at IS NOT NULL
    ),
    CONSTRAINT ck_access_operations_retry CHECK (
        next_retry_at IS NULL OR state IN ('PLANNED', 'FAILED', 'UNKNOWN')
    ),
    CONSTRAINT ck_access_operations_lease CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL AND lease_expires_at IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_access_operations_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_access_operations_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_access_operations_provider_reference
    ON access_operations (provider_account_id, provider_reference)
    WHERE provider_account_id IS NOT NULL AND provider_reference IS NOT NULL;
CREATE INDEX idx_access_operations_due ON access_operations (next_retry_at)
    WHERE state IN ('PLANNED', 'FAILED', 'UNKNOWN');
CREATE INDEX idx_access_operations_leases ON access_operations (lease_expires_at)
    WHERE state = 'SUBMITTING';
CREATE INDEX idx_access_operations_grant ON access_operations (access_grant_id, created_at DESC);
--rollback DROP TABLE access_operations;

--changeset ninggiangboy:025-06-access-observations
-- What the provider or the device said happened. Append-only, native references preserved: adapter
-- normalization may add meaning but must never discard the timestamp, device identity or result code
-- an access failure investigation runs on.
CREATE TABLE access_observations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    access_grant_id             UUID NOT NULL,
    access_operation_id         UUID,

    provider_account_id         UUID,
    provider_event_id           VARCHAR(128),
    device_reference            VARCHAR(64),

    event_type                  VARCHAR(32) NOT NULL,
    result_code                 VARCHAR(48),
    occurred_at                 TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,

    source                      VARCHAR(24) NOT NULL,
    verification_method         VARCHAR(24),
    integrity_verified          BOOLEAN NOT NULL DEFAULT false,
    payload_reference           VARCHAR(256),
    payload_hash                CHAR(64),

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_access_observations_grant FOREIGN KEY (access_grant_id)
        REFERENCES access_grants (id),
    CONSTRAINT fk_access_observations_operation FOREIGN KEY (access_operation_id)
        REFERENCES access_operations (id),
    CONSTRAINT fk_access_observations_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT ck_access_observations_event_type CHECK (
        event_type IN ('CREDENTIAL_CREATED', 'CREDENTIAL_ROTATED', 'CREDENTIAL_REVOKED',
                       'CREDENTIAL_EXPIRED', 'DOOR_OPENED', 'DOOR_LOCKED', 'ACCESS_DENIED',
                       'DEVICE_OFFLINE', 'DEVICE_BATTERY_LOW', 'UNKNOWN')
    ),
    CONSTRAINT ck_access_observations_source CHECK (
        source IN ('API_RESPONSE', 'WEBHOOK', 'QUERY', 'RECONCILIATION_IMPORT')
    ),
    CONSTRAINT ck_access_observations_verification CHECK (
        verification_method IS NULL
            OR verification_method IN ('WEBHOOK_SIGNATURE', 'AUTHENTICATED_API', 'RESTRICTED_IMPORT')
    ),
    -- A verified observation must say how it was verified. An unverified one is still kept, because
    -- an unsigned callback is evidence of something -- just not of itself.
    CONSTRAINT ck_access_observations_integrity CHECK (
        NOT integrity_verified OR verification_method IS NOT NULL
    )
);

-- The provider's own event identity is the deduplication key: the same callback delivered twice must
-- not become two door openings.
CREATE UNIQUE INDEX uk_access_observations_provider_event
    ON access_observations (provider_account_id, provider_event_id)
    WHERE provider_account_id IS NOT NULL AND provider_event_id IS NOT NULL;
CREATE INDEX idx_access_observations_grant ON access_observations (access_grant_id, occurred_at DESC);
CREATE INDEX idx_access_observations_operation ON access_observations (access_operation_id);
--rollback DROP TABLE access_observations;

--changeset ninggiangboy:025-07-operational-tasks
-- A unit of preparation work with a window, an owner and a standard of proof. Turnover scheduling is
-- driven by the previous checkout, the next check-in and the property's buffer, so the window is kept
-- in both local and instant terms rather than derived at read time.
CREATE TABLE operational_tasks (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id                 UUID NOT NULL,
    listing_id                  UUID,
    physical_unit_id            UUID,
    operational_stay_id         UUID,
    booking_id                  UUID,
    booking_revision_id         UUID,

    task_type                   VARCHAR(32) NOT NULL,
    -- Critical tasks are the ones whose absence at cutoff makes a readiness exception rather than a
    -- note. Cleaning before arrival is critical; restocking a welcome basket is not.
    is_critical                 BOOLEAN NOT NULL DEFAULT false,

    property_time_zone          VARCHAR(64) NOT NULL,
    service_date                DATE NOT NULL,
    window_start_local          TIME,
    window_end_local            TIME,
    window_start_instant        TIMESTAMPTZ NOT NULL,
    window_end_instant          TIMESTAMPTZ NOT NULL,
    due_at                      TIMESTAMPTZ NOT NULL,

    assignee_type               VARCHAR(16) NOT NULL DEFAULT 'UNASSIGNED',
    assignee_account_holder_id  UUID,
    assignee_reference          VARCHAR(96),

    -- Least privilege is a property of the assignment: a cleaner needs the address and the window,
    -- not the guest's payment history, and the required evidence list is what they are asked for.
    requires_evidence           BOOLEAN NOT NULL DEFAULT false,
    required_evidence_types     VARCHAR(32)[] NOT NULL DEFAULT '{}',

    depends_on_task_id          UUID,
    status                      VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    blocked_reason              VARCHAR(48),

    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    completed_by_account_holder_id UUID,
    completion_note             VARCHAR(512),
    cancelled_at                TIMESTAMPTZ,
    cancellation_reason         VARCHAR(48),

    reopen_count                INTEGER NOT NULL DEFAULT 0,
    reopen_reason               VARCHAR(48),
    policy_version              VARCHAR(32),
    template_version            VARCHAR(32),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_operational_tasks_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_operational_tasks_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_operational_tasks_unit FOREIGN KEY (physical_unit_id) REFERENCES physical_units (id),
    CONSTRAINT fk_operational_tasks_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_operational_tasks_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_operational_tasks_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_operational_tasks_assignee FOREIGN KEY (assignee_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operational_tasks_completer FOREIGN KEY (completed_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operational_tasks_dependency FOREIGN KEY (depends_on_task_id)
        REFERENCES operational_tasks (id),
    CONSTRAINT ck_operational_tasks_type CHECK (
        task_type IN ('TURNOVER_CLEANING', 'INSPECTION', 'LINEN', 'CONSUMABLES',
                      'MAINTENANCE_VERIFICATION', 'KEY_HANDOFF', 'REGISTRATION_PREPARATION',
                      'CHECKOUT_INSPECTION')
    ),
    CONSTRAINT ck_operational_tasks_assignee_type CHECK (
        assignee_type IN ('UNASSIGNED', 'ACCOUNT_HOLDER', 'COLLABORATOR', 'VENDOR')
    ),
    CONSTRAINT ck_operational_tasks_status CHECK (
        status IN ('PLANNED', 'SCHEDULED', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED',
                   'COMPLETED', 'CANCELLED', 'REOPENED')
    ),
    CONSTRAINT ck_operational_tasks_window CHECK (window_end_instant > window_start_instant),
    CONSTRAINT ck_operational_tasks_assignment CHECK (
        (assignee_type = 'UNASSIGNED')
            = (assignee_account_holder_id IS NULL AND assignee_reference IS NULL)
    ),
    CONSTRAINT ck_operational_tasks_named_assignee CHECK (
        assignee_type <> 'ACCOUNT_HOLDER' OR assignee_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_operational_tasks_work_needs_owner CHECK (
        status NOT IN ('ASSIGNED', 'IN_PROGRESS') OR assignee_type <> 'UNASSIGNED'
    ),
    -- Completion is an attestation: somebody's name and a time, never a status a client may set on
    -- its own. Changeset 025-20 adds the evidence half of the same rule.
    CONSTRAINT ck_operational_tasks_completion CHECK (
        status <> 'COMPLETED'
            OR (completed_at IS NOT NULL AND completed_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_operational_tasks_blocked CHECK (
        status <> 'BLOCKED' OR blocked_reason IS NOT NULL
    ),
    CONSTRAINT ck_operational_tasks_cancelled CHECK (
        (status = 'CANCELLED') = (cancelled_at IS NOT NULL AND cancellation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_operational_tasks_reopen CHECK (
        reopen_count >= 0 AND (reopen_count = 0 OR reopen_reason IS NOT NULL)
    ),
    CONSTRAINT ck_operational_tasks_evidence_types CHECK (
        NOT requires_evidence OR cardinality(required_evidence_types) > 0
    ),
    CONSTRAINT ck_operational_tasks_self_dependency CHECK (depends_on_task_id <> id),
    CONSTRAINT ck_operational_tasks_version CHECK (version >= 0)
);

-- The two queues that matter: what one person owes today, and what is still open against a cutoff.
CREATE INDEX idx_operational_tasks_assignee
    ON operational_tasks (assignee_account_holder_id, due_at)
    WHERE status IN ('SCHEDULED', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'REOPENED');
CREATE INDEX idx_operational_tasks_due ON operational_tasks (due_at)
    WHERE status IN ('PLANNED', 'SCHEDULED', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'REOPENED');
CREATE INDEX idx_operational_tasks_property ON operational_tasks (property_id, service_date);
CREATE INDEX idx_operational_tasks_stay ON operational_tasks (operational_stay_id);
--rollback DROP TABLE operational_tasks;

--changeset ninggiangboy:025-08-task-evidence
-- What was submitted in support of a task attestation. The system may check that a photo was taken in
-- the right window near the right place; it may not decide from that alone that the room is clean, so
-- these rows support a completion rather than constituting one.
CREATE TABLE task_evidence (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operational_task_id         UUID NOT NULL,

    evidence_type               VARCHAR(24) NOT NULL,
    source                      VARCHAR(16) NOT NULL,
    submitted_by_account_holder_id UUID,

    object_reference            VARCHAR(256),
    content_hash                CHAR(64),
    scan_state                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',

    captured_at                 TIMESTAMPTZ,
    received_at                 TIMESTAMPTZ NOT NULL,
    device_reference            VARCHAR(64),
    signal_quality              VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN',

    sensitivity_class           VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_task_evidence_task FOREIGN KEY (operational_task_id)
        REFERENCES operational_tasks (id),
    CONSTRAINT fk_task_evidence_submitter FOREIGN KEY (submitted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_task_evidence_type CHECK (
        evidence_type IN ('PHOTO', 'VIDEO', 'CHECKLIST', 'ATTESTATION', 'DEVICE_SIGNAL',
                          'DOCUMENT', 'PROVIDER_REPORT')
    ),
    CONSTRAINT ck_task_evidence_source CHECK (
        source IN ('ASSIGNEE', 'HOST', 'GUEST', 'OPERATOR', 'SUPPORT', 'PROVIDER', 'DEVICE', 'SYSTEM')
    ),
    CONSTRAINT ck_task_evidence_scan_state CHECK (
        scan_state IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_task_evidence_signal_quality CHECK (
        signal_quality IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN')
    ),
    CONSTRAINT ck_task_evidence_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_task_evidence_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    -- An uploaded artifact must be addressable and hashable; an attestation or checklist need not be.
    CONSTRAINT ck_task_evidence_artifact CHECK (
        evidence_type NOT IN ('PHOTO', 'VIDEO', 'DOCUMENT', 'PROVIDER_REPORT')
            OR (object_reference IS NOT NULL AND content_hash IS NOT NULL)
    )
);

CREATE INDEX idx_task_evidence_task ON task_evidence (operational_task_id, received_at DESC);
CREATE INDEX idx_task_evidence_pending_scan ON task_evidence (received_at)
    WHERE scan_state IN ('PENDING', 'FAILED');
--rollback DROP TABLE task_evidence;

--changeset ninggiangboy:025-09-stay-observations
-- Typed evidence about what happened during the stay. Clients submit these; they never set a stay's
-- outcome. Both times are kept because the gap between them is what tells a reviewer that a device
-- reported an arrival four hours late rather than that the guest arrived late.
CREATE TABLE stay_observations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operational_stay_id         UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,

    subject                     VARCHAR(24) NOT NULL,
    observation_type            VARCHAR(32) NOT NULL,
    source                      VARCHAR(16) NOT NULL,
    reported_by_account_holder_id UUID,

    event_time                  TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,
    source_time_zone            VARCHAR(64),

    confidence                  VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN',
    quality_flags               VARCHAR(32)[] NOT NULL DEFAULT '{}',

    provider_account_id         UUID,
    provider_reference          VARCHAR(128),
    access_observation_id       UUID,

    payload_reference           VARCHAR(256),
    payload_hash                CHAR(64),
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_stay_observations_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_stay_observations_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_stay_observations_reporter FOREIGN KEY (reported_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_stay_observations_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_stay_observations_access FOREIGN KEY (access_observation_id)
        REFERENCES access_observations (id),
    CONSTRAINT ck_stay_observations_subject CHECK (
        subject IN ('PROPERTY_READINESS', 'GUEST_ARRIVAL', 'GUEST_DEPARTURE', 'PRESENCE',
                    'OCCUPANCY', 'CONSUMPTION', 'CONDITION')
    ),
    CONSTRAINT ck_stay_observations_type CHECK (
        observation_type IN ('GUEST_REPORT', 'HOST_ATTESTATION', 'OPERATOR_ATTESTATION',
                             'REGISTRATION_RECORD', 'ACCESS_EVENT', 'DEVICE_SIGNAL',
                             'SUPPORT_VERIFICATION', 'PROVIDER_REPORT')
    ),
    CONSTRAINT ck_stay_observations_source CHECK (
        source IN ('ASSIGNEE', 'HOST', 'GUEST', 'OPERATOR', 'SUPPORT', 'PROVIDER', 'DEVICE', 'SYSTEM')
    ),
    CONSTRAINT ck_stay_observations_confidence CHECK (
        confidence IN ('LOW', 'MEDIUM', 'HIGH', 'UNKNOWN')
    ),
    CONSTRAINT ck_stay_observations_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    -- A report by a person names the person; a provider report names the provider. An anonymous
    -- arrival claim is not evidence of anything.
    CONSTRAINT ck_stay_observations_attribution CHECK (
        (observation_type IN ('GUEST_REPORT', 'HOST_ATTESTATION', 'OPERATOR_ATTESTATION',
                              'SUPPORT_VERIFICATION') AND reported_by_account_holder_id IS NOT NULL)
        OR (observation_type IN ('REGISTRATION_RECORD', 'ACCESS_EVENT', 'DEVICE_SIGNAL',
                                 'PROVIDER_REPORT'))
    )
);

CREATE UNIQUE INDEX uk_stay_observations_provider_reference
    ON stay_observations (provider_account_id, provider_reference)
    WHERE provider_account_id IS NOT NULL AND provider_reference IS NOT NULL;
CREATE INDEX idx_stay_observations_stay ON stay_observations (operational_stay_id, event_time DESC);
CREATE INDEX idx_stay_observations_subject
    ON stay_observations (operational_stay_id, subject, event_time DESC);
--rollback DROP TABLE stay_observations;

--changeset ninggiangboy:025-10-stay-outcome-decisions
-- What operations concluded and asked booking for. It is a proposal: review eligibility and host-fund
-- release consume booking's committed fact, never this row. Supersession is additive, so a later
-- correction never erases what the earlier evaluation saw.
CREATE TABLE stay_outcome_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operational_stay_id         UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,

    proposal                    VARCHAR(24) NOT NULL,
    policy_version              VARCHAR(32) NOT NULL,
    reason_code                 VARCHAR(48) NOT NULL,
    effective_at                TIMESTAMPTZ NOT NULL,

    -- The evidence considered, named and hashed. Re-running the same evaluation over the same rows
    -- must produce the same hash; a different one means the inputs moved.
    evidence_observation_ids    UUID[] NOT NULL DEFAULT '{}',
    evidence_hash               CHAR(64) NOT NULL,

    decided_by_actor_type       VARCHAR(16) NOT NULL,
    decided_by_actor_id         UUID,

    result                      VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    booking_transition_id       UUID,
    rejection_reason            VARCHAR(48),
    requested_at                TIMESTAMPTZ NOT NULL,
    responded_at                TIMESTAMPTZ,

    superseded_by_decision_id   UUID,
    superseded_at               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stay_outcome_decisions_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_stay_outcome_decisions_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_stay_outcome_decisions_transition FOREIGN KEY (booking_transition_id)
        REFERENCES booking_state_transitions (id),
    -- Deferred: one live decision per stay means the superseded row must point at its successor
    -- before that successor can be inserted.
    CONSTRAINT fk_stay_outcome_decisions_supersession FOREIGN KEY (superseded_by_decision_id)
        REFERENCES stay_outcome_decisions (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_stay_outcome_decisions_proposal CHECK (
        proposal IN ('COMPLETION_ELIGIBLE', 'NO_SHOW_REVIEW', 'EXCEPTION', 'CANCELLED')
    ),
    CONSTRAINT ck_stay_outcome_decisions_result CHECK (
        result IN ('PENDING', 'ACCEPTED_BY_BOOKING', 'REJECTED_BY_BOOKING', 'WITHDRAWN')
    ),
    CONSTRAINT ck_stay_outcome_decisions_actor CHECK (
        decided_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    -- An accepted proposal must name the booking transition that carried it out, so the two records
    -- can be reconciled without guessing.
    CONSTRAINT ck_stay_outcome_decisions_acceptance CHECK (
        result <> 'ACCEPTED_BY_BOOKING' OR booking_transition_id IS NOT NULL
    ),
    CONSTRAINT ck_stay_outcome_decisions_rejection CHECK (
        result <> 'REJECTED_BY_BOOKING' OR rejection_reason IS NOT NULL
    ),
    CONSTRAINT ck_stay_outcome_decisions_response CHECK (
        (result = 'PENDING') = (responded_at IS NULL)
    ),
    CONSTRAINT ck_stay_outcome_decisions_supersession CHECK (
        (superseded_by_decision_id IS NULL) = (superseded_at IS NULL)
    ),
    CONSTRAINT ck_stay_outcome_decisions_self_supersession CHECK (superseded_by_decision_id <> id),
    CONSTRAINT ck_stay_outcome_decisions_version CHECK (version >= 0)
);

-- One live proposal per stay. A second would let booking be asked for two contradictory transitions.
CREATE UNIQUE INDEX uk_stay_outcome_decisions_live ON stay_outcome_decisions (operational_stay_id)
    WHERE superseded_at IS NULL AND result <> 'WITHDRAWN';
CREATE INDEX idx_stay_outcome_decisions_pending ON stay_outcome_decisions (requested_at)
    WHERE result = 'PENDING';
--rollback DROP TABLE stay_outcome_decisions;

--changeset ninggiangboy:025-11-maintenance-records
-- A defect in the building, and what was asked of the domains that own the consequences. Inventory
-- owns the calendar block and listing owns publication; this row records the request and the answer,
-- which is why the block reference is a foreign key and not a status word.
CREATE TABLE maintenance_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id                 UUID NOT NULL,
    listing_id                  UUID,
    physical_unit_id            UUID,
    asset_reference             VARCHAR(96),

    category                    VARCHAR(24) NOT NULL,
    severity                    VARCHAR(16) NOT NULL,
    guest_impact                VARCHAR(16) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'REPORTED',

    reported_by_account_holder_id UUID,
    reported_at                 TIMESTAMPTZ NOT NULL,
    source_incident_id          UUID,
    source_task_id              UUID,
    observation_note            VARCHAR(512),

    expected_start_at           TIMESTAMPTZ,
    expected_end_at             TIMESTAMPTZ,
    work_order_reference        VARCHAR(96),
    resolved_at                 TIMESTAMPTZ,
    resolution_note             VARCHAR(512),
    resolution_evidence_reference VARCHAR(256),
    deferral_reason             VARCHAR(48),

    block_request_state         VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED',
    block_requested_at          TIMESTAMPTZ,
    inventory_block_id          UUID,
    listing_action_requested    VARCHAR(24) NOT NULL DEFAULT 'NONE',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_maintenance_records_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_maintenance_records_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_maintenance_records_unit FOREIGN KEY (physical_unit_id)
        REFERENCES physical_units (id),
    CONSTRAINT fk_maintenance_records_reporter FOREIGN KEY (reported_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_maintenance_records_task FOREIGN KEY (source_task_id)
        REFERENCES operational_tasks (id),
    CONSTRAINT fk_maintenance_records_block FOREIGN KEY (inventory_block_id)
        REFERENCES inventory_blocks (id),
    CONSTRAINT ck_maintenance_records_category CHECK (
        category IN ('PLUMBING', 'ELECTRICAL', 'APPLIANCE', 'HVAC', 'STRUCTURAL', 'PEST',
                     'SAFETY_EQUIPMENT', 'CLEANLINESS', 'NETWORK', 'ACCESS_HARDWARE', 'OTHER')
    ),
    CONSTRAINT ck_maintenance_records_severity CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_maintenance_records_impact CHECK (
        guest_impact IN ('NONE', 'COSMETIC', 'DEGRADED', 'UNUSABLE', 'UNSAFE')
    ),
    CONSTRAINT ck_maintenance_records_state CHECK (
        state IN ('REPORTED', 'TRIAGED', 'SCHEDULED', 'IN_PROGRESS', 'RESOLVED',
                  'DEFERRED', 'CANCELLED')
    ),
    CONSTRAINT ck_maintenance_records_block_state CHECK (
        block_request_state IN ('NOT_REQUESTED', 'REQUESTED', 'APPLIED', 'REJECTED')
    ),
    CONSTRAINT ck_maintenance_records_listing_action CHECK (
        listing_action_requested IN ('NONE', 'PAUSE_PUBLICATION', 'CONTENT_CORRECTION')
    ),
    -- A condition that makes the place unusable or unsafe cannot leave triage without a decision
    -- having been taken about the calendar. Requesting and being refused is a decision; silence is not.
    CONSTRAINT ck_maintenance_records_unsafe_block CHECK (
        state = 'REPORTED'
            OR guest_impact NOT IN ('UNUSABLE', 'UNSAFE')
            OR block_request_state <> 'NOT_REQUESTED'
    ),
    CONSTRAINT ck_maintenance_records_block_request CHECK (
        (block_request_state = 'NOT_REQUESTED') = (block_requested_at IS NULL)
    ),
    CONSTRAINT ck_maintenance_records_block_applied CHECK (
        block_request_state <> 'APPLIED' OR inventory_block_id IS NOT NULL
    ),
    CONSTRAINT ck_maintenance_records_resolution CHECK (
        (state = 'RESOLVED') = (resolved_at IS NOT NULL)
    ),
    CONSTRAINT ck_maintenance_records_deferral CHECK (
        state <> 'DEFERRED' OR deferral_reason IS NOT NULL
    ),
    CONSTRAINT ck_maintenance_records_window CHECK (
        expected_end_at IS NULL OR expected_start_at IS NULL OR expected_end_at > expected_start_at
    ),
    CONSTRAINT ck_maintenance_records_version CHECK (version >= 0)
);

CREATE INDEX idx_maintenance_records_property ON maintenance_records (property_id, reported_at DESC);
CREATE INDEX idx_maintenance_records_open ON maintenance_records (severity, reported_at)
    WHERE state IN ('REPORTED', 'TRIAGED', 'SCHEDULED', 'IN_PROGRESS');
CREATE INDEX idx_maintenance_records_block ON maintenance_records (inventory_block_id);
--rollback DROP TABLE maintenance_records;

--changeset ninggiangboy:025-12-incidents
-- Something is wrong during a stay. Category and severity are separate facts: a cleanliness report can
-- be urgent and a safety report can be informational only if nobody declared danger. severity_rank is
-- the orderable form of severity, paired to it by a check so the two can never disagree, and it is
-- what changeset 025-23 uses to refuse a quiet downgrade.
CREATE TABLE incidents (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID,
    operational_stay_id         UUID,
    listing_id                  UUID,
    property_id                 UUID,
    market_code                 VARCHAR(2),
    conversation_id             UUID,

    reporter_account_holder_id  UUID,
    reporter_role               VARCHAR(16) NOT NULL,
    description_reference       VARCHAR(256),

    category                    VARCHAR(24) NOT NULL,
    severity                    VARCHAR(24) NOT NULL,
    severity_rank               SMALLINT NOT NULL,
    -- Set by deterministic triage from what the reporter answered, never by a model. Once true it
    -- stays true: a person said they were in danger, and later calm does not unsay it.
    safety_flag                 BOOLEAN NOT NULL DEFAULT false,
    severity_change_reason      VARCHAR(48),
    severity_reviewed_by_account_holder_id UUID,
    severity_reviewed_at        TIMESTAMPTZ,

    state                       VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    owner_type                  VARCHAR(24),
    owner_account_holder_id     UUID,
    owner_queue                 VARCHAR(48),

    policy_version              VARCHAR(32) NOT NULL,
    slo_target_at               TIMESTAMPTZ,
    first_response_at           TIMESTAMPTZ,
    mitigated_at                TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,

    duplicate_of_incident_id    UUID,
    transferred_domain          VARCHAR(16),
    transferred_reference       VARCHAR(96),
    transferred_at              TIMESTAMPTZ,

    -- The allocator for the event timeline, exactly as conversations.next_sequence is for messages.
    next_sequence               BIGINT NOT NULL DEFAULT 1,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_incidents_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_incidents_stay FOREIGN KEY (operational_stay_id) REFERENCES operational_stays (id),
    CONSTRAINT fk_incidents_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_incidents_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_incidents_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_incidents_reporter FOREIGN KEY (reporter_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_incidents_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_incidents_severity_reviewer FOREIGN KEY (severity_reviewed_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_incidents_duplicate FOREIGN KEY (duplicate_of_incident_id) REFERENCES incidents (id),
    CONSTRAINT ck_incidents_reporter_role CHECK (
        reporter_role IN ('GUEST', 'HOST', 'CO_HOST', 'OPERATOR', 'SUPPORT', 'SYSTEM')
    ),
    CONSTRAINT ck_incidents_category CHECK (
        category IN ('CANNOT_ACCESS', 'HOST_UNREACHABLE', 'PROPERTY_NOT_READY', 'LISTING_MISMATCH',
                     'UTILITY_FAILURE', 'MAINTENANCE', 'AMENITY_FAILURE', 'CLEANLINESS', 'NOISE',
                     'LOST_PROPERTY', 'ACCESS_SECURITY', 'SAFETY_REPORT')
    ),
    CONSTRAINT ck_incidents_severity CHECK (
        severity IN ('S0_SAFETY_CRITICAL', 'S1_URGENT', 'S2_HIGH', 'S3_STANDARD', 'S4_INFORMATIONAL')
    ),
    -- The rank is the severity, restated so it can be compared. Pairing them here is what lets the
    -- downgrade guard read one integer instead of parsing a string.
    CONSTRAINT ck_incidents_severity_rank CHECK (
        (severity = 'S0_SAFETY_CRITICAL' AND severity_rank = 0)
            OR (severity = 'S1_URGENT' AND severity_rank = 1)
            OR (severity = 'S2_HIGH' AND severity_rank = 2)
            OR (severity = 'S3_STANDARD' AND severity_rank = 3)
            OR (severity = 'S4_INFORMATIONAL' AND severity_rank = 4)
    ),
    -- The deterministic floor. A declared safety concern cannot sit in an ordinary queue, whatever a
    -- classifier thinks of the wording.
    CONSTRAINT ck_incidents_safety_floor CHECK (NOT safety_flag OR severity_rank <= 1),
    CONSTRAINT ck_incidents_state CHECK (
        state IN ('OPEN', 'TRIAGED', 'ASSIGNED', 'RESPONDING', 'MITIGATED', 'REMEDY_PENDING',
                  'RESOLVED', 'CLOSED', 'SAFETY_ESCALATED', 'DUPLICATE', 'TRANSFERRED')
    ),
    CONSTRAINT ck_incidents_owner_type CHECK (
        owner_type IS NULL
            OR owner_type IN ('HOST', 'OPERATIONS', 'SUPPORT_AGENT', 'SAFETY_SPECIALIST', 'QUEUE')
    ),
    CONSTRAINT ck_incidents_assignment CHECK (
        state NOT IN ('ASSIGNED', 'RESPONDING') OR owner_type IS NOT NULL
    ),
    CONSTRAINT ck_incidents_named_owner CHECK (
        owner_type IS NULL OR owner_type = 'QUEUE' OR owner_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_incidents_queue_owner CHECK (owner_type <> 'QUEUE' OR owner_queue IS NOT NULL),
    CONSTRAINT ck_incidents_mitigation CHECK (state <> 'MITIGATED' OR mitigated_at IS NOT NULL),
    CONSTRAINT ck_incidents_resolution CHECK (state <> 'RESOLVED' OR resolved_at IS NOT NULL),
    -- Closing requires an operational resolution first. Closing without one is how an unanswered
    -- safety report disappears from a queue.
    CONSTRAINT ck_incidents_closure CHECK (
        state <> 'CLOSED' OR (closed_at IS NOT NULL AND resolved_at IS NOT NULL)
    ),
    CONSTRAINT ck_incidents_duplicate CHECK (
        (state = 'DUPLICATE') = (duplicate_of_incident_id IS NOT NULL)
    ),
    CONSTRAINT ck_incidents_transfer CHECK (
        (state = 'TRANSFERRED')
            = (transferred_domain IS NOT NULL AND transferred_reference IS NOT NULL
               AND transferred_at IS NOT NULL)
    ),
    CONSTRAINT ck_incidents_transfer_domain CHECK (
        transferred_domain IS NULL OR transferred_domain IN ('SUPPORT', 'TRUST', 'CLAIMS')
    ),
    CONSTRAINT ck_incidents_severity_review CHECK (
        (severity_reviewed_at IS NULL) = (severity_reviewed_by_account_holder_id IS NULL)
    ),
    CONSTRAINT ck_incidents_self_duplicate CHECK (duplicate_of_incident_id <> id),
    CONSTRAINT ck_incidents_sequence CHECK (next_sequence > 0),
    CONSTRAINT ck_incidents_version CHECK (version >= 0)
);

CREATE INDEX idx_incidents_queue ON incidents (severity_rank, slo_target_at)
    WHERE state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED');
CREATE INDEX idx_incidents_owner ON incidents (owner_account_holder_id, slo_target_at)
    WHERE state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED');
CREATE INDEX idx_incidents_safety ON incidents (created_at DESC) WHERE safety_flag;
CREATE INDEX idx_incidents_booking ON incidents (booking_id, created_at DESC);
CREATE INDEX idx_incidents_stay ON incidents (operational_stay_id, created_at DESC);

-- Closes the forward reference left in changeset 025-11: a maintenance record may have been raised
-- from an incident, and the incident table did not exist yet when that column was written.
ALTER TABLE maintenance_records
    ADD CONSTRAINT fk_maintenance_records_incident FOREIGN KEY (source_incident_id)
        REFERENCES incidents (id);
--rollback ALTER TABLE maintenance_records DROP CONSTRAINT fk_maintenance_records_incident;
--rollback DROP TABLE incidents;

--changeset ninggiangboy:025-13-incident-events
-- The incident's timeline, append-only and ordered by a number the incident issued. Two agents acting
-- at the same moment cannot both write event three, and nothing already written can be edited to make
-- a response look faster than it was.
CREATE TABLE incident_events (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id                 UUID NOT NULL,
    sequence_number             BIGINT NOT NULL,

    event_type                  VARCHAR(24) NOT NULL,
    previous_state              VARCHAR(24),
    new_state                   VARCHAR(24),
    previous_severity           VARCHAR(24),
    new_severity                VARCHAR(24),

    actor_type                  VARCHAR(16) NOT NULL,
    actor_account_holder_id     UUID,
    reason_code                 VARCHAR(48),
    note_reference              VARCHAR(256),
    visibility                  VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',

    related_reference_type      VARCHAR(24),
    related_reference_id        UUID,
    slo_clock_effect            VARCHAR(8) NOT NULL DEFAULT 'NONE',

    incident_version_before     BIGINT,
    content_hash                CHAR(64),
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_incident_events_incident FOREIGN KEY (incident_id) REFERENCES incidents (id),
    CONSTRAINT fk_incident_events_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_incident_events_sequence UNIQUE (incident_id, sequence_number),
    CONSTRAINT ck_incident_events_type CHECK (
        event_type IN ('STATE_TRANSITION', 'SEVERITY_CHANGE', 'ASSIGNMENT', 'NOTE',
                       'EVIDENCE_LINKED', 'REMEDY_REQUESTED', 'REMEDY_RESULT',
                       'COMMUNICATION', 'SLO_CLOCK')
    ),
    CONSTRAINT ck_incident_events_actor CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER', 'ANONYMOUS')
    ),
    CONSTRAINT ck_incident_events_visibility CHECK (
        visibility IN ('GUEST', 'HOST', 'BOTH', 'INTERNAL')
    ),
    CONSTRAINT ck_incident_events_slo_effect CHECK (
        slo_clock_effect IN ('NONE', 'START', 'PAUSE', 'RESUME', 'STOP')
    ),
    CONSTRAINT ck_incident_events_reference CHECK (
        (related_reference_type IS NULL) = (related_reference_id IS NULL)
    ),
    CONSTRAINT ck_incident_events_reference_type CHECK (
        related_reference_type IS NULL
            OR related_reference_type IN ('MESSAGE', 'EVIDENCE_LINK', 'REMEDY_REQUEST',
                                          'OPERATIONAL_TASK', 'MAINTENANCE_RECORD', 'ACCESS_GRANT')
    ),
    -- A transition event must say what it moved to, and a severity change must say what it moved to
    -- and why. An unexplained severity change is the one an audit always asks about.
    CONSTRAINT ck_incident_events_transition CHECK (
        event_type <> 'STATE_TRANSITION' OR new_state IS NOT NULL
    ),
    CONSTRAINT ck_incident_events_severity CHECK (
        event_type <> 'SEVERITY_CHANGE' OR (new_severity IS NOT NULL AND reason_code IS NOT NULL)
    ),
    CONSTRAINT ck_incident_events_sequence CHECK (sequence_number > 0)
);

CREATE INDEX idx_incident_events_incident ON incident_events (incident_id, sequence_number);
--rollback DROP TABLE incident_events;

--changeset ninggiangboy:025-14-incident-evidence-links
-- Selected evidence, linked with a purpose rather than copied. Nothing here duplicates a message or a
-- photo; it names one and records why it is in this case, who may see it, and under whose custody it
-- now sits. That is what makes a later transfer to claims or trust auditable.
CREATE TABLE incident_evidence_links (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id                 UUID NOT NULL,

    source_domain               VARCHAR(16) NOT NULL,
    source_type                 VARCHAR(32) NOT NULL,
    source_id                   UUID NOT NULL,
    source_version              BIGINT,

    purpose_code                VARCHAR(48) NOT NULL,
    visibility                  VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    linked_by_account_holder_id UUID,
    linked_at                   TIMESTAMPTZ NOT NULL,
    content_hash                CHAR(64),

    custody_state               VARCHAR(16) NOT NULL DEFAULT 'HELD',
    transferred_to_domain       VARCHAR(16),
    transferred_reference       VARCHAR(96),
    sensitivity_class           VARCHAR(16) NOT NULL DEFAULT 'CONFIDENTIAL',
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'EXTENDED',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_incident_evidence_links_incident FOREIGN KEY (incident_id) REFERENCES incidents (id),
    CONSTRAINT fk_incident_evidence_links_linker FOREIGN KEY (linked_by_account_holder_id)
        REFERENCES account_holders (id),
    -- The same artifact linked twice would give two custody records for one thing.
    CONSTRAINT uk_incident_evidence_links_source
        UNIQUE (incident_id, source_domain, source_type, source_id),
    CONSTRAINT ck_incident_evidence_links_domain CHECK (
        source_domain IN ('MESSAGING', 'OPERATIONS', 'ACCESS', 'LISTING', 'BOOKING',
                          'PAYMENTS', 'SUPPORT')
    ),
    CONSTRAINT ck_incident_evidence_links_type CHECK (
        source_type IN ('MESSAGE', 'MESSAGE_ATTACHMENT', 'TASK_EVIDENCE', 'STAY_OBSERVATION',
                        'ACCESS_OBSERVATION', 'MAINTENANCE_RECORD', 'LISTING_SNAPSHOT',
                        'BOOKING_SNAPSHOT', 'SUPPORT_NOTE', 'PHOTO', 'VIDEO', 'CALL_METADATA')
    ),
    CONSTRAINT ck_incident_evidence_links_visibility CHECK (
        visibility IN ('GUEST', 'HOST', 'BOTH', 'INTERNAL')
    ),
    CONSTRAINT ck_incident_evidence_links_custody CHECK (
        custody_state IN ('HELD', 'TRANSFERRED', 'RELEASED', 'PURGED')
    ),
    CONSTRAINT ck_incident_evidence_links_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_incident_evidence_links_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_incident_evidence_links_transfer CHECK (
        (custody_state = 'TRANSFERRED')
            = (transferred_to_domain IS NOT NULL AND transferred_reference IS NOT NULL)
    ),
    CONSTRAINT ck_incident_evidence_links_transfer_domain CHECK (
        transferred_to_domain IS NULL OR transferred_to_domain IN ('SUPPORT', 'TRUST', 'CLAIMS')
    ),
    -- Held evidence is not purged. Retention deletion is legitimate; deleting what a legal hold names
    -- is not, and the difference must survive a bulk retention job.
    CONSTRAINT ck_incident_evidence_links_hold CHECK (custody_state <> 'PURGED' OR NOT legal_hold),
    CONSTRAINT ck_incident_evidence_links_version CHECK (version >= 0)
);

CREATE INDEX idx_incident_evidence_links_incident
    ON incident_evidence_links (incident_id, linked_at DESC);
CREATE INDEX idx_incident_evidence_links_source
    ON incident_evidence_links (source_domain, source_type, source_id);
CREATE INDEX idx_incident_evidence_links_hold ON incident_evidence_links (incident_id)
    WHERE legal_hold;
--rollback DROP TABLE incident_evidence_links;

--changeset ninggiangboy:025-15-remedy-requests
-- Operations asking another domain to do something, and that domain's answer. This table is the whole
-- boundary: there is no path from an incident to a refund, a block or a payout hold except a row here
-- and a decision reference coming back. Operations never edits another domain's tables.
CREATE TABLE remedy_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id                 UUID NOT NULL,

    target_domain               VARCHAR(16) NOT NULL,
    action_type                 VARCHAR(32) NOT NULL,
    idempotency_key             VARCHAR(96) NOT NULL,
    request_hash                CHAR(64) NOT NULL,

    reason_code                 VARCHAR(48) NOT NULL,
    urgency                     VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    policy_version              VARCHAR(32),
    evidence_link_ids           UUID[] NOT NULL DEFAULT '{}',

    requested_by_actor_type     VARCHAR(16) NOT NULL,
    requested_by_actor_id       UUID,
    requested_at                TIMESTAMPTZ NOT NULL,

    state                       VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    target_decision_reference   VARCHAR(96),
    target_decision_id          UUID,
    target_decision_version     BIGINT,
    rejection_reason            VARCHAR(48),
    failure_reason              VARCHAR(48),
    responded_at                TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_remedy_requests_incident FOREIGN KEY (incident_id) REFERENCES incidents (id),
    -- The identity the feature document names: one incident, one domain, one action, one key. A retry
    -- of the same ask is the same row; a different ask needs a different key.
    CONSTRAINT uk_remedy_requests_identity
        UNIQUE (incident_id, target_domain, action_type, idempotency_key),
    CONSTRAINT ck_remedy_requests_target_domain CHECK (
        target_domain IN ('BOOKING', 'CANCELLATION', 'PAYMENTS', 'FINANCE', 'INVENTORY',
                          'LISTING', 'MESSAGING', 'TRUST', 'SUPPORT', 'CLAIMS')
    ),
    CONSTRAINT ck_remedy_requests_action CHECK (
        action_type IN ('CONTACT_ESCALATION', 'MANUAL_ACCESS_FALLBACK', 'INVENTORY_EMERGENCY_BLOCK',
                        'CANCELLATION_PREVIEW', 'CANCELLATION_EXECUTE', 'REFUND_PREVIEW',
                        'REFUND_EXECUTE', 'GUEST_CREDIT', 'FEE_WAIVER', 'RELOCATION_SEARCH',
                        'RELOCATION_BUDGET', 'COLLECTION_PAUSE', 'PAYOUT_HOLD', 'RISK_REVIEW',
                        'SAFETY_RESTRICTION', 'CLAIMS_INTAKE')
    ),
    CONSTRAINT ck_remedy_requests_urgency CHECK (urgency IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_remedy_requests_actor CHECK (
        requested_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    ),
    CONSTRAINT ck_remedy_requests_state CHECK (
        state IN ('REQUESTED', 'DISPATCHED', 'PENDING', 'ACCEPTED', 'REJECTED',
                  'COMPLETED', 'FAILED', 'WITHDRAWN')
    ),
    -- An accepted or completed request must name the other domain's own decision. Without it the
    -- incident view would be showing an outcome nobody can look up.
    CONSTRAINT ck_remedy_requests_acceptance CHECK (
        state NOT IN ('ACCEPTED', 'COMPLETED') OR target_decision_reference IS NOT NULL
    ),
    CONSTRAINT ck_remedy_requests_rejection CHECK (
        state <> 'REJECTED' OR rejection_reason IS NOT NULL
    ),
    CONSTRAINT ck_remedy_requests_failure CHECK (state <> 'FAILED' OR failure_reason IS NOT NULL),
    CONSTRAINT ck_remedy_requests_response CHECK (
        state NOT IN ('ACCEPTED', 'REJECTED', 'COMPLETED', 'FAILED') OR responded_at IS NOT NULL
    ),
    CONSTRAINT ck_remedy_requests_version CHECK (version >= 0)
);

CREATE INDEX idx_remedy_requests_incident ON remedy_requests (incident_id, requested_at DESC);
CREATE INDEX idx_remedy_requests_open ON remedy_requests (requested_at)
    WHERE state IN ('REQUESTED', 'DISPATCHED', 'PENDING');
CREATE INDEX idx_remedy_requests_decision ON remedy_requests (target_domain, target_decision_id);
--rollback DROP TABLE remedy_requests;

--changeset ninggiangboy:025-16-operational-stay-integrity splitStatements:false
-- A stay is the operational view of one committed booking revision, and a grant hangs off one stay.
-- Both relationships are foreign keys, but a foreign key only proves the rows exist -- not that they
-- are about the same booking. These two checks are what stop a stay being built on another booking's
-- revision, or a grant on another booking's stay, which would let a guest be admitted to a property
-- they never reserved.
CREATE FUNCTION operational_stays_validate_write() RETURNS TRIGGER AS $$
DECLARE
    revision_booking UUID;
    revision_status VARCHAR(16);
BEGIN
    SELECT booking_id, status INTO revision_booking, revision_status
      FROM booking_revisions WHERE id = NEW.booking_revision_id;

    -- NOT FOUND as well as a mismatch: the foreign key fires after this trigger, so a missing row
    -- would leave the comparison NULL and the error would name the wrong column.
    IF NOT FOUND OR revision_booking <> NEW.booking_id THEN
        RAISE EXCEPTION
            'booking revision % does not belong to booking %',
            NEW.booking_revision_id, NEW.booking_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF revision_status <> 'COMMITTED' THEN
        RAISE EXCEPTION
            'booking revision % is %; only a committed revision may be operated',
            NEW.booking_revision_id, revision_status USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operational_stays_validate_write
    BEFORE INSERT ON operational_stays
    FOR EACH ROW EXECUTE FUNCTION operational_stays_validate_write();

CREATE FUNCTION access_grants_validate_write() RETURNS TRIGGER AS $$
DECLARE
    stay_booking UUID;
    stay_status VARCHAR(16);
    instruction_stay UUID;
BEGIN
    SELECT booking_id, status INTO stay_booking, stay_status
      FROM operational_stays WHERE id = NEW.operational_stay_id;

    IF NOT FOUND OR stay_booking <> NEW.booking_id THEN
        RAISE EXCEPTION
            'operational stay % does not belong to booking %',
            NEW.operational_stay_id, NEW.booking_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF stay_status <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'operational stay % is %; a superseded or closed stay cannot issue access',
            NEW.operational_stay_id, stay_status USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.instruction_set_id IS NOT NULL THEN
        SELECT operational_stay_id INTO instruction_stay
          FROM instruction_sets WHERE id = NEW.instruction_set_id;

        IF NOT FOUND OR instruction_stay <> NEW.operational_stay_id THEN
            RAISE EXCEPTION
                'instruction set % belongs to stay % and not to stay %',
                NEW.instruction_set_id, instruction_stay, NEW.operational_stay_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_access_grants_validate_write
    BEFORE INSERT ON access_grants
    FOR EACH ROW EXECUTE FUNCTION access_grants_validate_write();
--rollback DROP TRIGGER trg_access_grants_validate_write ON access_grants;
--rollback DROP FUNCTION access_grants_validate_write();
--rollback DROP TRIGGER trg_operational_stays_validate_write ON operational_stays;
--rollback DROP FUNCTION operational_stays_validate_write();

--changeset ninggiangboy:025-17-instruction-version-immutability splitStatements:false
-- Once an instruction version has been released, its content is what somebody acted on. A change is a
-- new version that supersedes it, never an edit: the guest who already read the old entry route must
-- still be explicable, and the access audit rows point at a version whose meaning has to stay fixed.
-- What may still move is the version's own lifecycle -- ending, being superseded, being revoked.
CREATE FUNCTION instruction_sets_freeze_released() RETURNS TRIGGER AS $$
DECLARE
    candidate instruction_sets%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'instruction set % (version %) was released and cannot be deleted',
            OLD.id, OLD.version_number USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.supersession_reason := OLD.supersession_reason;
    candidate.revoked_at := OLD.revoked_at;
    candidate.revocation_reason := OLD.revocation_reason;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'instruction set % (version %) is released; issue a superseding version instead of editing it',
            OLD.id, OLD.version_number USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_instruction_sets_freeze
    BEFORE UPDATE OR DELETE ON instruction_sets
    FOR EACH ROW WHEN (OLD.status IN ('RELEASED', 'SUPERSEDED', 'REVOKED'))
    EXECUTE FUNCTION instruction_sets_freeze_released();
--rollback DROP TRIGGER trg_instruction_sets_freeze ON instruction_sets;
--rollback DROP FUNCTION instruction_sets_freeze_released();

--changeset ninggiangboy:025-18-operational-evidence-append-only splitStatements:false
-- Three tables exist only to be believed later: who retrieved which instruction band, what the lock
-- provider reported, and what happened to an incident in what order. None of them has a legitimate
-- update or delete. A row written in error is corrected by a new row that says so.
--
-- Because of this the foreign keys into them restrict rather than cascade: a cascade would fire this
-- trigger and fail the parent delete, which is a confusing way to learn the rule.
CREATE FUNCTION operational_evidence_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'table % is append-only; row % cannot be % (record the correction as a new row)',
        TG_TABLE_NAME, OLD.id, lower(TG_OP) USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_instruction_access_audit_append_only
    BEFORE UPDATE OR DELETE ON instruction_access_audit
    FOR EACH ROW EXECUTE FUNCTION operational_evidence_reject_mutation();

CREATE TRIGGER trg_access_observations_append_only
    BEFORE UPDATE OR DELETE ON access_observations
    FOR EACH ROW EXECUTE FUNCTION operational_evidence_reject_mutation();

CREATE TRIGGER trg_incident_events_append_only
    BEFORE UPDATE OR DELETE ON incident_events
    FOR EACH ROW EXECUTE FUNCTION operational_evidence_reject_mutation();
--rollback DROP TRIGGER trg_incident_events_append_only ON incident_events;
--rollback DROP TRIGGER trg_access_observations_append_only ON access_observations;
--rollback DROP TRIGGER trg_instruction_access_audit_append_only ON instruction_access_audit;
--rollback DROP FUNCTION operational_evidence_reject_mutation();

--changeset ninggiangboy:025-19-access-grant-lifecycle-guards splitStatements:false
-- Three things about a grant must be true regardless of which service wrote it.
--
-- A validity window may only widen while the entitlement is still being decided, or together with the
-- booking revision that justified it. Otherwise "early check-in" becomes a database update instead of
-- an accepted modification, and the guest is in the building before the host agreed they could be.
--
-- Revocation is terminal. A revoked grant that could be moved back to ACTIVE would mean a code
-- somebody was told to stop using quietly starting to work again.
--
-- A reveal counter only goes up. It is the number an investigation compares against the access audit,
-- and a resettable counter proves nothing.
CREATE FUNCTION access_grants_guard_lifecycle() RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.valid_from, NEW.valid_until) IS DISTINCT FROM (OLD.valid_from, OLD.valid_until)
       AND OLD.state NOT IN ('DRAFT', 'ELIGIBLE')
       AND NEW.booking_revision_id = OLD.booking_revision_id THEN
        RAISE EXCEPTION
            'access grant % is %; its validity window may only change with a new booking revision',
            OLD.id, OLD.state USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state = 'REVOKED' AND NEW.state <> 'REVOKED' THEN
        RAISE EXCEPTION
            'access grant % was revoked at %; issue a replacement grant instead of reviving it',
            OLD.id, OLD.revoked_at USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.reveal_count < OLD.reveal_count THEN
        RAISE EXCEPTION
            'access grant % cannot lower its reveal count from % to %',
            OLD.id, OLD.reveal_count, NEW.reveal_count USING ERRCODE = 'restrict_violation';
    END IF;

    -- Clearing the credential reference while the grant is live would orphan whatever the provider
    -- still holds, leaving nothing to revoke.
    IF OLD.secret_reference IS NOT NULL AND NEW.secret_reference IS NULL
       AND NEW.state IN ('ACTIVE', 'UNKNOWN', 'REVOKING') THEN
        RAISE EXCEPTION
            'access grant % is %; its credential reference cannot be cleared while it may still open a door',
            OLD.id, NEW.state USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_access_grants_guard_lifecycle
    BEFORE UPDATE ON access_grants
    FOR EACH ROW EXECUTE FUNCTION access_grants_guard_lifecycle();
--rollback DROP TRIGGER trg_access_grants_guard_lifecycle ON access_grants;
--rollback DROP FUNCTION access_grants_guard_lifecycle();

--changeset ninggiangboy:025-20-task-completion-requires-evidence splitStatements:false
-- Completion is an attestation plus the evidence the task asked for. The table check already forces a
-- name and a time; this forces the proof, which no row-level check can see because it lives in
-- another table. Infected or unscanned uploads do not count -- otherwise the standard of proof would
-- be "something was uploaded".
--
-- A dependency is enforced here too: signing off a checkout inspection before the cleaning it depends
-- on was done is how a readiness exception turns into a guest arriving to an unready property.
CREATE FUNCTION operational_tasks_guard_completion() RETURNS TRIGGER AS $$
DECLARE
    usable_evidence INTEGER;
    dependency_status VARCHAR(16);
BEGIN
    IF NEW.requires_evidence THEN
        SELECT count(*) INTO usable_evidence
          FROM task_evidence
         WHERE operational_task_id = NEW.id
           AND scan_state IN ('CLEAN', 'NOT_APPLICABLE');

        IF usable_evidence = 0 THEN
            RAISE EXCEPTION
                'task % requires evidence (%) and has none that passed scanning',
                NEW.id, array_to_string(NEW.required_evidence_types, ', ')
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF NEW.depends_on_task_id IS NOT NULL THEN
        SELECT status INTO dependency_status
          FROM operational_tasks WHERE id = NEW.depends_on_task_id;

        IF FOUND AND dependency_status <> 'COMPLETED' THEN
            RAISE EXCEPTION
                'task % depends on task %, which is %',
                NEW.id, NEW.depends_on_task_id, dependency_status
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operational_tasks_guard_completion
    BEFORE UPDATE ON operational_tasks
    FOR EACH ROW WHEN (NEW.status = 'COMPLETED' AND OLD.status IS DISTINCT FROM 'COMPLETED')
    EXECUTE FUNCTION operational_tasks_guard_completion();
--rollback DROP TRIGGER trg_operational_tasks_guard_completion ON operational_tasks;
--rollback DROP FUNCTION operational_tasks_guard_completion();

--changeset ninggiangboy:025-21-stay-evidence-freeze splitStatements:false
-- Evidence rows are not append-only in the strict sense, because three things about them legitimately
-- move after they are written: a scan finishes, a retention class is reassessed, a legal hold is
-- placed or lifted. Everything else -- what was observed, when, by whom, and the artifact it points
-- at -- is frozen at insert, and a held row cannot be deleted at all.
CREATE FUNCTION task_evidence_freeze_content() RETURNS TRIGGER AS $$
DECLARE
    candidate task_evidence%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.legal_hold THEN
            RAISE EXCEPTION
                'task evidence % is under legal hold and cannot be deleted',
                OLD.id USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;

    candidate := NEW;
    candidate.scan_state := OLD.scan_state;
    candidate.sensitivity_class := OLD.sensitivity_class;
    candidate.retention_class := OLD.retention_class;
    candidate.legal_hold := OLD.legal_hold;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'task evidence % is frozen; only its scan, classification and hold may change',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_task_evidence_freeze
    BEFORE UPDATE OR DELETE ON task_evidence
    FOR EACH ROW EXECUTE FUNCTION task_evidence_freeze_content();

CREATE FUNCTION stay_observations_freeze_content() RETURNS TRIGGER AS $$
DECLARE
    candidate stay_observations%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.legal_hold THEN
            RAISE EXCEPTION
                'stay observation % is under legal hold and cannot be deleted',
                OLD.id USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;

    candidate := NEW;
    candidate.retention_class := OLD.retention_class;
    candidate.legal_hold := OLD.legal_hold;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'stay observation % is frozen; record a further observation instead of editing this one',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stay_observations_freeze
    BEFORE UPDATE OR DELETE ON stay_observations
    FOR EACH ROW EXECUTE FUNCTION stay_observations_freeze_content();

-- An evidence link may change custody, visibility and retention -- that is its whole working life --
-- but never which artifact it points at or which case it belongs to. Repointing a link would let a
-- decision record cite something it never saw.
CREATE FUNCTION incident_evidence_links_freeze_source() RETURNS TRIGGER AS $$
DECLARE
    candidate incident_evidence_links%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.legal_hold THEN
            RAISE EXCEPTION
                'evidence link % is under legal hold and cannot be deleted',
                OLD.id USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;

    candidate := NEW;
    candidate.visibility := OLD.visibility;
    candidate.custody_state := OLD.custody_state;
    candidate.transferred_to_domain := OLD.transferred_to_domain;
    candidate.transferred_reference := OLD.transferred_reference;
    candidate.sensitivity_class := OLD.sensitivity_class;
    candidate.retention_class := OLD.retention_class;
    candidate.legal_hold := OLD.legal_hold;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'evidence link % cannot be repointed; unlink it and link the correct artifact',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incident_evidence_links_freeze
    BEFORE UPDATE OR DELETE ON incident_evidence_links
    FOR EACH ROW EXECUTE FUNCTION incident_evidence_links_freeze_source();
--rollback DROP TRIGGER trg_incident_evidence_links_freeze ON incident_evidence_links;
--rollback DROP FUNCTION incident_evidence_links_freeze_source();
--rollback DROP TRIGGER trg_stay_observations_freeze ON stay_observations;
--rollback DROP FUNCTION stay_observations_freeze_content();
--rollback DROP TRIGGER trg_task_evidence_freeze ON task_evidence;
--rollback DROP FUNCTION task_evidence_freeze_content();

--changeset ninggiangboy:025-22-incident-sequence-integrity splitStatements:false
-- The same allocator discipline migration 024 gave conversations, for the same reason: an incident
-- timeline is dispute evidence, and a gap or a reordering in it changes what the record says about how
-- fast anybody responded. The incident issues the numbers; the events may only take issued ones.
CREATE FUNCTION incidents_guard_sequence() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.next_sequence < OLD.next_sequence THEN
        RAISE EXCEPTION
            'incident % cannot move its event allocator from % back to %',
            OLD.id, OLD.next_sequence, NEW.next_sequence USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incidents_sequence_forward
    BEFORE UPDATE ON incidents
    FOR EACH ROW EXECUTE FUNCTION incidents_guard_sequence();

CREATE FUNCTION incident_events_validate_write() RETURNS TRIGGER AS $$
DECLARE
    allocator BIGINT;
BEGIN
    SELECT next_sequence INTO allocator FROM incidents WHERE id = NEW.incident_id;

    -- NOT FOUND is tested explicitly: the foreign key has not fired yet, and a NULL comparison would
    -- fall through and report a sequence problem for a missing incident.
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'incident % does not exist', NEW.incident_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.sequence_number >= allocator THEN
        RAISE EXCEPTION
            'event sequence % was never issued by incident % (allocator is at %)',
            NEW.sequence_number, NEW.incident_id, allocator USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incident_events_validate_write
    BEFORE INSERT ON incident_events
    FOR EACH ROW EXECUTE FUNCTION incident_events_validate_write();
--rollback DROP TRIGGER trg_incident_events_validate_write ON incident_events;
--rollback DROP FUNCTION incident_events_validate_write();
--rollback DROP TRIGGER trg_incidents_sequence_forward ON incidents;
--rollback DROP FUNCTION incidents_guard_sequence();

--changeset ninggiangboy:025-23-incident-severity-floor splitStatements:false
-- The feature document's hardest rule about incidents: a model may raise priority or suggest
-- questions, but it may never lower a user-declared safety report below the deterministic minimum or
-- close the case. Expressed as a constraint rather than a service rule, because the service that gets
-- it wrong is exactly the automated one.
--
-- Two things follow. A safety flag that a person set cannot be cleared -- later calm does not unsay a
-- declaration of danger. And any move to a less urgent severity must carry a reason; on a safety
-- incident it must also carry a named reviewer, which is what "elevated review" means when written
-- down.
CREATE FUNCTION incidents_guard_severity() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.safety_flag AND NOT NEW.safety_flag THEN
        RAISE EXCEPTION
            'incident % was reported as a safety concern; the flag cannot be cleared',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.severity_rank > OLD.severity_rank THEN
        IF NEW.severity_change_reason IS NULL THEN
            RAISE EXCEPTION
                'incident % cannot be lowered from % to % without a reason',
                OLD.id, OLD.severity, NEW.severity USING ERRCODE = 'restrict_violation';
        END IF;

        IF OLD.safety_flag
           AND (NEW.severity_reviewed_by_account_holder_id IS NULL
                OR NEW.severity_reviewed_at IS NULL
                OR NEW.severity_reviewed_at IS NOT DISTINCT FROM OLD.severity_reviewed_at) THEN
            RAISE EXCEPTION
                'incident % is a safety report; lowering it from % to % requires a named reviewer',
                OLD.id, OLD.severity, NEW.severity USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incidents_guard_severity
    BEFORE UPDATE ON incidents
    FOR EACH ROW EXECUTE FUNCTION incidents_guard_severity();
--rollback DROP TRIGGER trg_incidents_guard_severity ON incidents;
--rollback DROP FUNCTION incidents_guard_severity();

--changeset ninggiangboy:025-24-decision-and-request-identity-freeze splitStatements:false
-- Two identities that must not be repointed.
--
-- An outcome decision names the policy version and the evidence it weighed at the moment it was made.
-- Its result and the booking transition that carried it out arrive later and may be written; the
-- basis may not, or a proposal accepted on one set of facts could be made to read as though it had
-- been made on another.
--
-- A remedy request's identity is the four columns the unique key covers plus the hash of what was
-- asked. Freezing them is what stops the duplicate the unique key refuses being achieved by editing
-- an existing row instead -- the same defence migration 024 gave notification intents.
CREATE FUNCTION stay_outcome_decisions_freeze_basis() RETURNS TRIGGER AS $$
DECLARE
    candidate stay_outcome_decisions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'outcome decision % cannot be deleted; supersede it instead',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.result := OLD.result;
    candidate.booking_transition_id := OLD.booking_transition_id;
    candidate.rejection_reason := OLD.rejection_reason;
    candidate.responded_at := OLD.responded_at;
    candidate.superseded_by_decision_id := OLD.superseded_by_decision_id;
    candidate.superseded_at := OLD.superseded_at;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'outcome decision % is frozen; its proposal, policy version and evidence cannot be rewritten',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    -- The booking transition may be recorded once. Repointing it would move an accepted proposal onto
    -- a different booking event after the fact.
    IF OLD.booking_transition_id IS NOT NULL
       AND NEW.booking_transition_id IS DISTINCT FROM OLD.booking_transition_id THEN
        RAISE EXCEPTION
            'outcome decision % already names booking transition %',
            OLD.id, OLD.booking_transition_id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stay_outcome_decisions_freeze
    BEFORE UPDATE OR DELETE ON stay_outcome_decisions
    FOR EACH ROW EXECUTE FUNCTION stay_outcome_decisions_freeze_basis();

CREATE FUNCTION remedy_requests_freeze_identity() RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.incident_id, NEW.target_domain, NEW.action_type, NEW.idempotency_key,
        NEW.request_hash, NEW.requested_at)
       IS DISTINCT FROM
       (OLD.incident_id, OLD.target_domain, OLD.action_type, OLD.idempotency_key,
        OLD.request_hash, OLD.requested_at) THEN
        RAISE EXCEPTION
            'remedy request % cannot change what it asked for or of whom; raise a new request',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_remedy_requests_freeze_identity
    BEFORE UPDATE ON remedy_requests
    FOR EACH ROW EXECUTE FUNCTION remedy_requests_freeze_identity();
--rollback DROP TRIGGER trg_remedy_requests_freeze_identity ON remedy_requests;
--rollback DROP FUNCTION remedy_requests_freeze_identity();
--rollback DROP TRIGGER trg_stay_outcome_decisions_freeze ON stay_outcome_decisions;
--rollback DROP FUNCTION stay_outcome_decisions_freeze_basis();
