--liquibase formatted sql

-- Every domain in the target release repeats the same four needs: a command must be safe to retry,
-- a fact must reach other components exactly once, a consumer must not apply the same fact twice,
-- and a privileged action must leave evidence that survives the row it changed. Migrations 000-011
-- solved none of these, so each later domain would otherwise invent its own incompatible version.
--
-- These tables are the shared primitives. They deliberately hold no request bodies, bearer tokens,
-- or unrestricted responses: a retry-safety record is not a place to cache arbitrary payloads, and
-- an audit row that contained secrets could never be retained as long as auditing requires. Domains
-- with stronger retention, uniqueness, or result-shape needs may still add a specialised table; they
-- may not weaken these.
--
-- All five are written by the application, so none of them carry DEFAULT now(): see migration 011.

--changeset ninggiangboy:012-01-command-idempotency-records
CREATE TABLE command_idempotency_records (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type              VARCHAR(48) NOT NULL,
    scope_key               VARCHAR(128) NOT NULL,
    operation               VARCHAR(96) NOT NULL,
    key_digest              CHAR(64) NOT NULL,
    request_digest          CHAR(64) NOT NULL,
    contract_version        VARCHAR(16) NOT NULL,
    actor_type              VARCHAR(24),
    actor_id                UUID,
    resource_type           VARCHAR(48),
    resource_id             UUID,
    state                   VARCHAR(16) NOT NULL,
    response_status         SMALLINT,
    response_resource_type  VARCHAR(48),
    response_resource_id    UUID,
    response_reference      VARCHAR(64),
    response_projection     JSONB,
    failure_code            VARCHAR(64),
    lease_owner             VARCHAR(128),
    lease_expires_at        TIMESTAMPTZ,
    first_request_id        VARCHAR(64) NOT NULL,
    correlation_id          VARCHAR(64) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    completed_at            TIMESTAMPTZ,
    retain_until            TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_command_idempotency_records_scope
        UNIQUE (scope_type, scope_key, operation, key_digest),
    CONSTRAINT ck_command_idempotency_records_state CHECK (
        state IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_command_idempotency_records_digests CHECK (
        key_digest ~ '^[0-9a-f]{64}$' AND request_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_command_idempotency_records_actor CHECK (
        (actor_type IS NULL) = (actor_id IS NULL)
    ),
    CONSTRAINT ck_command_idempotency_records_resource CHECK (
        (resource_type IS NULL) = (resource_id IS NULL)
    ),
    CONSTRAINT ck_command_idempotency_records_response_resource CHECK (
        (response_resource_type IS NULL) = (response_resource_id IS NULL)
    ),
    CONSTRAINT ck_command_idempotency_records_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    -- An in-progress record has no outcome yet; a settled one can never be reopened without an
    -- outcome, which is what makes a replay answerable without re-running the command.
    CONSTRAINT ck_command_idempotency_records_settlement CHECK (
        (state = 'IN_PROGRESS' AND completed_at IS NULL AND failure_code IS NULL)
        OR (state = 'SUCCEEDED' AND completed_at IS NOT NULL AND response_status IS NOT NULL)
        OR (state = 'FAILED' AND completed_at IS NOT NULL AND failure_code IS NOT NULL)
    ),
    CONSTRAINT ck_command_idempotency_records_response_status CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    ),
    CONSTRAINT ck_command_idempotency_records_projection CHECK (
        response_projection IS NULL OR jsonb_typeof(response_projection) = 'object'
    ),
    CONSTRAINT ck_command_idempotency_records_retention CHECK (retain_until > created_at),
    CONSTRAINT ck_command_idempotency_records_version CHECK (version >= 0)
);

-- Recovering an abandoned command means finding records whose lease has lapsed. The predicate is
-- evaluated against a bound decision instant, never against now(), so the index stays immutable.
CREATE INDEX idx_command_idempotency_records_stalled
    ON command_idempotency_records (lease_expires_at)
    WHERE state = 'IN_PROGRESS';

CREATE INDEX idx_command_idempotency_records_expiry
    ON command_idempotency_records (retain_until);

CREATE INDEX idx_command_idempotency_records_resource
    ON command_idempotency_records (resource_type, resource_id, created_at DESC)
    WHERE resource_id IS NOT NULL;
--rollback DROP TABLE command_idempotency_records;

--changeset ninggiangboy:012-02-outbox-events
CREATE TABLE outbox_events (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_name             VARCHAR(96) NOT NULL,
    schema_version         SMALLINT NOT NULL,
    aggregate_type         VARCHAR(48) NOT NULL,
    aggregate_id           UUID NOT NULL,
    aggregate_version      BIGINT NOT NULL,
    occurred_at            TIMESTAMPTZ NOT NULL,
    recorded_at            TIMESTAMPTZ NOT NULL,
    market_code            VARCHAR(2),
    correlation_id         VARCHAR(64) NOT NULL,
    causation_id           VARCHAR(64),
    actor_type             VARCHAR(24),
    actor_reference        VARCHAR(64),
    content_type           VARCHAR(64) NOT NULL DEFAULT 'application/json',
    sensitivity_class      VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    payload                JSONB NOT NULL,
    deduplication_key      VARCHAR(128),
    publication_state      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    available_at           TIMESTAMPTZ NOT NULL,
    attempt_count          INTEGER NOT NULL DEFAULT 0,
    lease_owner            VARCHAR(128),
    lease_expires_at       TIMESTAMPTZ,
    published_at           TIMESTAMPTZ,
    last_error_class       VARCHAR(64),
    version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_outbox_events_deduplication
        UNIQUE (event_name, deduplication_key),
    CONSTRAINT ck_outbox_events_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_outbox_events_aggregate_version CHECK (aggregate_version >= 0),
    CONSTRAINT ck_outbox_events_market CHECK (market_code IS NULL OR market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_outbox_events_actor CHECK ((actor_type IS NULL) = (actor_reference IS NULL)),
    CONSTRAINT ck_outbox_events_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_outbox_events_payload CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_outbox_events_publication_state CHECK (
        publication_state IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'DISCARDED')
    ),
    CONSTRAINT ck_outbox_events_published CHECK (
        (publication_state = 'PUBLISHED') = (published_at IS NOT NULL)
    ),
    CONSTRAINT ck_outbox_events_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_outbox_events_recording CHECK (recorded_at >= occurred_at),
    CONSTRAINT ck_outbox_events_version CHECK (version >= 0)
);

-- The publisher claims a bounded batch with FOR UPDATE SKIP LOCKED ordered by availability; this is
-- the index that keeps that claim from scanning published history.
CREATE INDEX idx_outbox_events_ready
    ON outbox_events (available_at, id)
    WHERE publication_state IN ('PENDING', 'FAILED');

CREATE INDEX idx_outbox_events_stalled_lease
    ON outbox_events (lease_expires_at)
    WHERE publication_state = 'PUBLISHING';

CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id, occurred_at);
--rollback DROP TABLE outbox_events;

--changeset ninggiangboy:012-03-consumer-inbox-receipts
CREATE TABLE consumer_inbox_receipts (
    consumer_name      VARCHAR(96) NOT NULL,
    event_id           UUID NOT NULL,
    consumer_version   SMALLINT NOT NULL,
    schema_version     SMALLINT NOT NULL,
    first_seen_at      TIMESTAMPTZ NOT NULL,
    state              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count      INTEGER NOT NULL DEFAULT 0,
    lease_owner        VARCHAR(128),
    lease_expires_at   TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    effect_type        VARCHAR(48),
    effect_id          UUID,
    failure_class      VARCHAR(64),
    version            BIGINT NOT NULL DEFAULT 0,
    -- The receipt is keyed by consumer and event, not by consumer version: a handler upgrade must
    -- not silently replay effects that already happened. A deliberate rebuild uses a new
    -- consumer_name and suppresses external side effects.
    CONSTRAINT pk_consumer_inbox_receipts PRIMARY KEY (consumer_name, event_id),
    CONSTRAINT ck_consumer_inbox_receipts_versions CHECK (
        consumer_version > 0 AND schema_version > 0
    ),
    CONSTRAINT ck_consumer_inbox_receipts_state CHECK (
        state IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')
    ),
    CONSTRAINT ck_consumer_inbox_receipts_completion CHECK (
        (state IN ('COMPLETED', 'SKIPPED')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_consumer_inbox_receipts_effect CHECK (
        (effect_type IS NULL) = (effect_id IS NULL)
    ),
    CONSTRAINT ck_consumer_inbox_receipts_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_consumer_inbox_receipts_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_consumer_inbox_receipts_version CHECK (version >= 0)
);

CREATE INDEX idx_consumer_inbox_receipts_backlog
    ON consumer_inbox_receipts (consumer_name, first_seen_at)
    WHERE state IN ('PENDING', 'FAILED');

CREATE INDEX idx_consumer_inbox_receipts_stalled_lease
    ON consumer_inbox_receipts (lease_expires_at)
    WHERE state = 'PROCESSING';
--rollback DROP TABLE consumer_inbox_receipts;

--changeset ninggiangboy:012-04-audit-events
CREATE TABLE audit_events (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at               TIMESTAMPTZ NOT NULL,
    action                    VARCHAR(96) NOT NULL,
    owning_domain             VARCHAR(48) NOT NULL,
    target_type               VARCHAR(48) NOT NULL,
    target_id                 UUID NOT NULL,
    outcome                   VARCHAR(16) NOT NULL,
    reason_code               VARCHAR(64),
    actor_type                VARCHAR(24) NOT NULL,
    actor_id                  UUID,
    actor_reference           VARCHAR(64),
    on_behalf_of_organization UUID,
    delegated_role            VARCHAR(48),
    delegated_permission      VARCHAR(96),
    approver_id               UUID,
    request_id                VARCHAR(64),
    correlation_id            VARCHAR(64) NOT NULL,
    causation_id              VARCHAR(64),
    market_code               VARCHAR(2),
    policy_reference          VARCHAR(128),
    before_digest             CHAR(64),
    after_digest              CHAR(64),
    change_summary            JSONB,
    retention_class           VARCHAR(24) NOT NULL,
    retain_until              TIMESTAMPTZ,
    legal_hold                BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ck_audit_events_outcome CHECK (
        outcome IN ('ALLOWED', 'DENIED', 'FAILED', 'PARTIAL')
    ),
    CONSTRAINT ck_audit_events_actor_type CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER', 'ANONYMOUS')
    ),
    -- A human actor is identified; a system or provider actor carries a reference instead. Requiring
    -- one of the two means no audit row can name nobody.
    CONSTRAINT ck_audit_events_actor_identity CHECK (
        actor_id IS NOT NULL OR actor_reference IS NOT NULL
    ),
    CONSTRAINT ck_audit_events_market CHECK (market_code IS NULL OR market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_audit_events_digests CHECK (
        (before_digest IS NULL OR before_digest ~ '^[0-9a-f]{64}$')
        AND (after_digest IS NULL OR after_digest ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_audit_events_change_summary CHECK (
        change_summary IS NULL OR jsonb_typeof(change_summary) = 'object'
    ),
    CONSTRAINT ck_audit_events_retention_class CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    -- A permanent row has no expiry; every other class must say when it stops being retained.
    CONSTRAINT ck_audit_events_retention CHECK (
        (retention_class = 'PERMANENT') = (retain_until IS NULL)
    )
);

CREATE INDEX idx_audit_events_target_timeline
    ON audit_events (target_type, target_id, occurred_at DESC);

CREATE INDEX idx_audit_events_actor_timeline
    ON audit_events (actor_id, occurred_at DESC)
    WHERE actor_id IS NOT NULL;

CREATE INDEX idx_audit_events_correlation
    ON audit_events (correlation_id);

CREATE INDEX idx_audit_events_retention
    ON audit_events (retain_until)
    WHERE retain_until IS NOT NULL AND legal_hold = false;
--rollback DROP TABLE audit_events;

--changeset ninggiangboy:012-05-audit-events-append-only splitStatements:false
-- Audit evidence is worthless if the application that produced it can also revise it. The trigger
-- refuses UPDATE and DELETE for every caller, so correcting a mistaken audit row means appending a
-- new one -- which is the same additive-correction rule the platform applies to all historical
-- evidence. Erasure and legal-hold handling run out of band with elevated privileges.
CREATE FUNCTION audit_events_reject_mutation() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only; record a correcting event instead'
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_reject_mutation();
--rollback DROP TRIGGER trg_audit_events_append_only ON audit_events;
--rollback DROP FUNCTION audit_events_reject_mutation();

--changeset ninggiangboy:012-06-external-resource-references
CREATE TABLE external_resource_references (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_account_key      VARCHAR(96) NOT NULL,
    provider_account_version  SMALLINT NOT NULL,
    resource_type             VARCHAR(48) NOT NULL,
    external_id               VARCHAR(255) NOT NULL,
    internal_aggregate_type   VARCHAR(48) NOT NULL,
    internal_aggregate_id     UUID NOT NULL,
    lifecycle_state           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    linked_at                 TIMESTAMPTZ NOT NULL,
    detached_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    -- One provider resource resolves to one platform aggregate, and one platform aggregate holds one
    -- live reference per provider account and resource type. Both directions are needed: without the
    -- first a webhook is ambiguous, without the second a retry can create a duplicate provider
    -- resource that nothing ever reconciles.
    CONSTRAINT uk_external_resource_references_external
        UNIQUE (provider_account_key, resource_type, external_id),
    CONSTRAINT ck_external_resource_references_lifecycle CHECK (
        lifecycle_state IN ('ACTIVE', 'DETACHED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_external_resource_references_detachment CHECK (
        (lifecycle_state = 'ACTIVE') = (detached_at IS NULL)
    ),
    CONSTRAINT ck_external_resource_references_account_version CHECK (
        provider_account_version > 0
    ),
    CONSTRAINT ck_external_resource_references_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_external_resource_references_active_internal
    ON external_resource_references (
        provider_account_key, resource_type, internal_aggregate_type, internal_aggregate_id
    )
    WHERE lifecycle_state = 'ACTIVE';

CREATE INDEX idx_external_resource_references_internal
    ON external_resource_references (internal_aggregate_type, internal_aggregate_id);
--rollback DROP TABLE external_resource_references;
