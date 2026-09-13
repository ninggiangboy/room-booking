--liquibase formatted sql

-- Analytics observes; it never repairs. A booking, a ledger posting, a published review and a
-- moderation decision are owned by the domains that accepted them, and nothing in this migration
-- may edit one. What it may do is say that its own copy disagrees, and say so in a row that names
-- the run, the code and the source snapshot it came from. Every table below is either a contract,
-- an arrival, a run, or a number produced by a run -- and each of those is a different kind of
-- record with a different schema, because collapsing them is how a prediction ends up being cited
-- as a fact.
--
-- Seven forces shape it:
--
--   A fact, an observation, a prediction and a decision are different records. This migration owns
--   observations (event_arrivals) and derived numbers (metric_materializations, analysis
--   estimates). It owns no decisions: experiment_actions records that somebody commanded a stop,
--   not that the experiment was stopped -- the experiment's own state is the domain's. Nothing here
--   references account_holders, listings or bookings by foreign key, and that is deliberate: an
--   analytical write must never take a lock on a row a guest is trying to book.
--
--   Historical evidence is immutable and corrections are additive. Arrivals, assignments,
--   exposures, run inputs and estimates are append-only by trigger. A wrong number is corrected by
--   a data_corrections row and a restating materialization, never by an UPDATE. A correction must
--   name either one event or a bounded time range, because an unbounded correction is an
--   unreviewable licence to rewrite history.
--
--   Nothing here stores a payload. An arrival carries a digest, a byte size and a reference to
--   restricted landing storage -- not the body. Quarantine is a diagnosis, not a shadow data lake,
--   and it carries its own shorter retention class. There is no column anywhere in this migration
--   for an email address, a phone number, a message body, an access instruction, a payment
--   instrument, a government identifier, an IP address or a user-agent string.
--
--   Identity is pseudonymous and linking is declared. Subjects appear as hex pseudonyms, checked by
--   pattern so that a user id or an address cannot be written into one by accident. The link table
--   knows three methods -- authentication, explicit declaration, account merge -- and the
--   vocabulary deliberately omits device fingerprint, shared address and payment instrument,
--   because those are exactly the covert links the design forbids. A suppressed link is terminal
--   and cannot be revived, and no new experiment assignment may be written for a suppressed
--   subject.
--
--   Assignment is not exposure. An assignment says which variant a unit would get. An exposure says
--   the treatment could actually have affected it, and carries the rule version that defined
--   "could". An exposure whose actual treatment differs from the assigned variant must say a
--   fallback happened -- a failed model call that silently degraded to control behaviour would
--   otherwise be counted as a successful treatment delivery, which inflates every estimate that
--   follows. An exposure cannot precede its own assignment.
--
--   An epoch becomes immutable at its first assignment. Bucket ranges are an exclusion constraint,
--   not a convention, so two variants cannot claim the same bucket. Once a unit has been bucketed,
--   the allocation, the salt, the population and the exposure rule are frozen and the variant rows
--   cannot be inserted, edited or deleted; material change means a new epoch. Re-allocating an
--   epoch under a running experiment is not a configuration change, it is the destruction of the
--   comparison.
--
--   A number may not claim more authority than its inputs. A run that consumed a degraded input
--   cannot report PASS. A run with a standing FAIL check cannot report PASS. A materialization
--   cannot be published as current off a run that did not succeed. A conclusion cannot be drawn
--   from an analysis with a sample-ratio mismatch. And a second look at a running experiment
--   requires a declared sequential method, because peeking at an unadjusted p-value is not a
--   stopping rule.
--
-- Note on what this migration does not create. outbox_events and consumer_inbox_receipts already
-- exist as shared primitives from migration 012 and are not redefined here; event_definitions is
-- the registry those events are published against. The feature, label, training, model and
-- prediction records from the same feature document are the ML half and belong to migration 031.
-- Kafka topology, Debezium configuration, object storage and warehouse layout are operational
-- choices that no table can hold.
--
-- Every table here is written by the application or a pipeline worker, so none of them carry
-- DEFAULT now(): see migration 011.

--changeset ninggiangboy:030-01-data-product-registry
CREATE TABLE data_product_registry (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_key               VARCHAR(96) NOT NULL,
    semantic_version          VARCHAR(16) NOT NULL,
    data_layer                VARCHAR(16) NOT NULL,
    display_name              VARCHAR(200) NOT NULL,
    grain                     VARCHAR(200) NOT NULL,
    primary_key_columns       VARCHAR(400) NOT NULL,
    schema_digest             CHAR(64) NOT NULL,
    schema_reference          VARCHAR(200) NOT NULL,
    storage_reference         VARCHAR(200) NOT NULL,
    business_owner            VARCHAR(64) NOT NULL,
    technical_steward         VARCHAR(64) NOT NULL,
    freshness_target_minutes  INTEGER NOT NULL,
    max_tolerated_lag_minutes INTEGER NOT NULL,
    allowed_lateness_minutes  INTEGER NOT NULL,
    privacy_class             VARCHAR(16) NOT NULL,
    contains_personal_data    BOOLEAN NOT NULL,
    training_allowed          BOOLEAN NOT NULL DEFAULT false,
    retention_days            INTEGER NOT NULL,
    deletion_behaviour        VARCHAR(24) NOT NULL,
    minimum_cohort_size       INTEGER,
    restatement_policy        VARCHAR(24) NOT NULL,
    status                    VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    supersedes_id             UUID,
    reviewed_at               TIMESTAMPTZ,
    published_at              TIMESTAMPTZ,
    deprecated_at             TIMESTAMPTZ,
    retired_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_data_product_registry_version UNIQUE (dataset_key, semantic_version),
    CONSTRAINT fk_data_product_registry_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT ck_data_product_registry_key CHECK (dataset_key ~ '^[a-z][a-z0-9_]{4,95}$'),
    CONSTRAINT ck_data_product_registry_semver CHECK (
        semantic_version ~ '^[0-9]{1,4}\.[0-9]{1,4}\.[0-9]{1,4}$'
    ),
    CONSTRAINT ck_data_product_registry_layer CHECK (
        data_layer IN ('LANDING', 'CONFORMED', 'SEMANTIC', 'SERVING')
    ),
    CONSTRAINT ck_data_product_registry_digest CHECK (schema_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_data_product_registry_privacy CHECK (
        privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_data_product_registry_deletion CHECK (
        deletion_behaviour IN ('ERASE', 'PSEUDONYMIZE', 'SUPPRESS', 'RETAIN_UNDER_LEGAL_BASIS')
    ),
    CONSTRAINT ck_data_product_registry_restatement CHECK (
        restatement_policy IN ('NONE', 'OPEN_PARTITION', 'VERSIONED_RESTATEMENT')
    ),
    CONSTRAINT ck_data_product_registry_status CHECK (
        status IN ('DRAFT', 'REVIEWED', 'CURRENT', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_data_product_registry_freshness CHECK (
        freshness_target_minutes > 0
            AND max_tolerated_lag_minutes >= freshness_target_minutes
            AND allowed_lateness_minutes >= 0
    ),
    CONSTRAINT ck_data_product_registry_retention CHECK (retention_days > 0),
    CONSTRAINT ck_data_product_registry_cohort CHECK (
        minimum_cohort_size IS NULL OR minimum_cohort_size >= 5
    ),
    -- Raw does not mean ungoverned: a landing product declares its retention and deletion
    -- behaviour like every other layer, and a product holding personal data must say what a
    -- deletion request does to it rather than leaving the answer to whoever writes the job.
    CONSTRAINT ck_data_product_registry_personal CHECK (
        NOT contains_personal_data OR privacy_class IN ('PERSONAL', 'RESTRICTED', 'PSEUDONYMOUS')
    ),
    -- Lawful collection is not blanket permission to train. A product that carries personal or
    -- restricted data cannot be declared a training corpus by the team that happens to own it.
    CONSTRAINT ck_data_product_registry_training CHECK (
        NOT training_allowed OR privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS')
    ),
    CONSTRAINT ck_data_product_registry_lifecycle CHECK (
        (status IN ('DRAFT', 'REVIEWED')) OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_data_product_registry_deprecated CHECK (
        (status = 'DEPRECATED') = (deprecated_at IS NOT NULL)
    ),
    CONSTRAINT ck_data_product_registry_retired CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_data_product_registry_version CHECK (version >= 0)
);

-- One dataset key may have many versions on the shelf but only one that consumers read by default.
CREATE UNIQUE INDEX uk_data_product_registry_current
    ON data_product_registry (dataset_key)
    WHERE status = 'CURRENT';

CREATE INDEX idx_data_product_registry_layer ON data_product_registry (data_layer, status);
CREATE INDEX idx_data_product_registry_owner ON data_product_registry (business_owner, status);
--rollback DROP TABLE data_product_registry;

--changeset ninggiangboy:030-02-data-product-dependencies
CREATE TABLE data_product_dependencies (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_product_id               UUID NOT NULL,
    upstream_data_product_id      UUID NOT NULL,
    minimum_watermark_lag_minutes INTEGER NOT NULL DEFAULT 0,
    required                      BOOLEAN NOT NULL DEFAULT true,
    declassification_approval     VARCHAR(64),
    declassification_reason       VARCHAR(500),
    created_at                    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_data_product_dependencies_edge
        UNIQUE (data_product_id, upstream_data_product_id),
    CONSTRAINT fk_data_product_dependencies_product FOREIGN KEY (data_product_id)
        REFERENCES data_product_registry (id) ON DELETE CASCADE,
    CONSTRAINT fk_data_product_dependencies_upstream FOREIGN KEY (upstream_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT ck_data_product_dependencies_not_self CHECK (
        data_product_id <> upstream_data_product_id
    ),
    CONSTRAINT ck_data_product_dependencies_lag CHECK (minimum_watermark_lag_minutes >= 0),
    -- Loosening a classification downstream is allowed, but only as a named approval with a
    -- recorded reason. Without this pair the trigger in 030-30 refuses the edge outright.
    CONSTRAINT ck_data_product_dependencies_declassification CHECK (
        (declassification_approval IS NULL) = (declassification_reason IS NULL)
    )
);

CREATE INDEX idx_data_product_dependencies_upstream
    ON data_product_dependencies (upstream_data_product_id);
--rollback DROP TABLE data_product_dependencies;

--changeset ninggiangboy:030-03-event-definitions
CREATE TABLE event_definitions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_name                VARCHAR(96) NOT NULL,
    schema_version            SMALLINT NOT NULL,
    event_class               VARCHAR(24) NOT NULL,
    producer                  VARCHAR(48) NOT NULL,
    description               VARCHAR(1000) NOT NULL,
    grain                     VARCHAR(200) NOT NULL,
    source_transaction        VARCHAR(120),
    schema_digest             CHAR(64) NOT NULL,
    schema_reference          VARCHAR(200) NOT NULL,
    example_reference         VARCHAR(200),
    ordering_semantics        VARCHAR(24) NOT NULL,
    compatibility_policy      VARCHAR(24) NOT NULL,
    privacy_class             VARCHAR(16) NOT NULL,
    legal_basis               VARCHAR(32),
    training_allowed          BOOLEAN NOT NULL DEFAULT false,
    expected_daily_volume     BIGINT,
    retention_days            INTEGER NOT NULL,
    quarantine_retention_days INTEGER NOT NULL,
    business_owner            VARCHAR(64) NOT NULL,
    technical_steward         VARCHAR(64) NOT NULL,
    status                    VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    replacement_definition_id UUID,
    last_production_at        TIMESTAMPTZ,
    deletion_plan             VARCHAR(500),
    reviewed_at               TIMESTAMPTZ,
    activated_at              TIMESTAMPTZ,
    deprecated_at             TIMESTAMPTZ,
    retired_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_event_definitions_version UNIQUE (event_name, schema_version),
    CONSTRAINT fk_event_definitions_replacement FOREIGN KEY (replacement_definition_id)
        REFERENCES event_definitions (id),
    CONSTRAINT ck_event_definitions_name CHECK (event_name ~ '^[A-Z][A-Za-z0-9]{2,95}$'),
    CONSTRAINT ck_event_definitions_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_event_definitions_class CHECK (
        event_class IN ('DOMAIN_FACT', 'INTERACTION_OBSERVATION', 'PROVIDER_OBSERVATION',
                        'OPERATIONAL_EVENT', 'CORRECTION', 'DERIVED_FACT')
    ),
    CONSTRAINT ck_event_definitions_ordering CHECK (
        ordering_semantics IN ('NONE', 'AGGREGATE_VERSION', 'PRODUCER_SEQUENCE')
    ),
    CONSTRAINT ck_event_definitions_compatibility CHECK (
        compatibility_policy IN ('BACKWARD', 'FORWARD', 'FULL', 'NONE')
    ),
    CONSTRAINT ck_event_definitions_privacy CHECK (
        privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_event_definitions_status CHECK (
        status IN ('DRAFT', 'REVIEWED', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_event_definitions_digest CHECK (schema_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_event_definitions_retention CHECK (
        retention_days > 0
            AND quarantine_retention_days > 0
            AND quarantine_retention_days <= retention_days
    ),
    CONSTRAINT ck_event_definitions_volume CHECK (
        expected_daily_volume IS NULL OR expected_daily_volume >= 0
    ),
    -- A committed source outcome must be able to name the transaction that committed it. Without
    -- that, nothing distinguishes a domain fact from something a service decided to announce.
    CONSTRAINT ck_event_definitions_source_transaction CHECK (
        event_class <> 'DOMAIN_FACT' OR source_transaction IS NOT NULL
    ),
    -- Activation is the promise that consumers may build on this contract, so the things a
    -- consumer needs -- a worked example, an expected volume, a stamped activation -- have to be
    -- present before the status can claim it.
    CONSTRAINT ck_event_definitions_activation CHECK (
        status IN ('DRAFT', 'REVIEWED')
            OR (activated_at IS NOT NULL
                AND example_reference IS NOT NULL
                AND expected_daily_volume IS NOT NULL)
    ),
    -- Deprecation without a last production date and a deletion or replay plan is not deprecation,
    -- it is an abandoned topic that nobody dares switch off.
    CONSTRAINT ck_event_definitions_deprecation CHECK (
        status NOT IN ('DEPRECATED', 'RETIRED')
            OR (last_production_at IS NOT NULL AND deletion_plan IS NOT NULL)
    ),
    CONSTRAINT ck_event_definitions_training CHECK (
        NOT training_allowed OR privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS')
    ),
    -- Personal or restricted collection has to name the basis on which it is collected at all.
    CONSTRAINT ck_event_definitions_legal_basis CHECK (
        privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS') OR legal_basis IS NOT NULL
    ),
    CONSTRAINT ck_event_definitions_stamps CHECK (
        (reviewed_at IS NULL OR activated_at IS NULL OR activated_at >= reviewed_at)
            AND (activated_at IS NULL OR deprecated_at IS NULL OR deprecated_at >= activated_at)
            AND (deprecated_at IS NULL OR retired_at IS NULL OR retired_at >= deprecated_at)
    ),
    CONSTRAINT ck_event_definitions_version CHECK (version >= 0)
);

CREATE INDEX idx_event_definitions_status ON event_definitions (status, event_class);
CREATE INDEX idx_event_definitions_producer ON event_definitions (producer, status);
--rollback DROP TABLE event_definitions;

--changeset ninggiangboy:030-04-event-definition-consumers
CREATE TABLE event_definition_consumers (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_definition_id  UUID NOT NULL,
    consumer_name        VARCHAR(64) NOT NULL,
    consumer_kind        VARCHAR(24) NOT NULL,
    contract_version     VARCHAR(16) NOT NULL,
    unknown_field_policy VARCHAR(16) NOT NULL DEFAULT 'IGNORE',
    declared_at          TIMESTAMPTZ NOT NULL,
    withdrawn_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_event_definition_consumers_contract
        UNIQUE (event_definition_id, consumer_name, contract_version),
    CONSTRAINT fk_event_definition_consumers_definition FOREIGN KEY (event_definition_id)
        REFERENCES event_definitions (id) ON DELETE CASCADE,
    CONSTRAINT ck_event_definition_consumers_kind CHECK (
        consumer_kind IN ('SERVICE', 'PIPELINE', 'EXPORT', 'EXPERIMENT')
    ),
    -- An unknown field is never promoted to a feature on its own. A consumer states in advance
    -- whether it ignores one, quarantines the record, or fails loudly.
    CONSTRAINT ck_event_definition_consumers_unknown_fields CHECK (
        unknown_field_policy IN ('IGNORE', 'QUARANTINE', 'FAIL')
    ),
    CONSTRAINT ck_event_definition_consumers_withdrawal CHECK (
        withdrawn_at IS NULL OR withdrawn_at >= declared_at
    )
);

CREATE INDEX idx_event_definition_consumers_active
    ON event_definition_consumers (event_definition_id)
    WHERE withdrawn_at IS NULL;
--rollback DROP TABLE event_definition_consumers;

--changeset ninggiangboy:030-05-event-arrivals
CREATE TABLE event_arrivals (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id                 UUID NOT NULL,
    event_definition_id      UUID,
    event_name               VARCHAR(96) NOT NULL,
    schema_version           SMALLINT NOT NULL,
    event_class              VARCHAR(24) NOT NULL,
    producer                 VARCHAR(48) NOT NULL,
    producer_environment     VARCHAR(16) NOT NULL,
    source_kind              VARCHAR(24) NOT NULL,
    origin_application       VARCHAR(64),
    application_version      VARCHAR(32),
    occurred_at              TIMESTAMPTZ NOT NULL,
    committed_at             TIMESTAMPTZ,
    received_at              TIMESTAMPTZ NOT NULL,
    ingested_at              TIMESTAMPTZ,
    aggregate_type           VARCHAR(48),
    aggregate_id             UUID,
    aggregate_version        BIGINT,
    correlation_id           VARCHAR(64),
    causation_id             VARCHAR(64),
    subject_pseudonym        VARCHAR(64),
    market_code              VARCHAR(2),
    payload_digest           CHAR(64) NOT NULL,
    payload_byte_size        INTEGER NOT NULL,
    payload_reference        VARCHAR(200),
    validation_state         VARCHAR(16) NOT NULL,
    rejection_reason         VARCHAR(48),
    duplicate_of_arrival_id  UUID,
    quarantine_reason        VARCHAR(48),
    quarantine_detail        VARCHAR(500),
    retention_class          VARCHAR(24) NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    suppressed_at            TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_event_arrivals_definition FOREIGN KEY (event_definition_id)
        REFERENCES event_definitions (id),
    -- A duplicate row is evidence about one particular original. When retention removes that
    -- original, the note that it was re-sent has nothing left to describe, so it goes with it --
    -- otherwise the retention job deletes in arrival order or not at all, and probing found it
    -- failing on exactly the rows it was written to remove.
    CONSTRAINT fk_event_arrivals_duplicate_of FOREIGN KEY (duplicate_of_arrival_id)
        REFERENCES event_arrivals (id) ON DELETE CASCADE,
    CONSTRAINT ck_event_arrivals_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_event_arrivals_environment CHECK (
        producer_environment IN ('PRODUCTION', 'STAGING', 'DEVELOPMENT', 'TEST')
    ),
    CONSTRAINT ck_event_arrivals_source CHECK (
        source_kind IN ('OUTBOX_CDC', 'CLIENT_COLLECTOR', 'PROVIDER_WEBHOOK',
                        'INTERNAL_PIPELINE', 'BACKFILL')
    ),
    CONSTRAINT ck_event_arrivals_validation CHECK (
        validation_state IN ('ACCEPTED', 'DUPLICATE', 'REJECTED', 'QUARANTINED')
    ),
    CONSTRAINT ck_event_arrivals_retention_class CHECK (
        retention_class IN ('STANDARD', 'SHORT', 'QUARANTINE', 'LEGAL_HOLD')
    ),
    CONSTRAINT ck_event_arrivals_digest CHECK (payload_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_event_arrivals_market CHECK (market_code IS NULL OR market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_event_arrivals_pseudonym CHECK (
        subject_pseudonym IS NULL OR subject_pseudonym ~ '^[0-9a-f]{32,64}$'
    ),
    CONSTRAINT ck_event_arrivals_aggregate CHECK (
        (aggregate_type IS NULL) = (aggregate_id IS NULL)
    ),
    CONSTRAINT ck_event_arrivals_aggregate_version CHECK (
        aggregate_version IS NULL OR aggregate_version >= 0
    ),
    CONSTRAINT ck_event_arrivals_duplicate CHECK (
        (validation_state = 'DUPLICATE') = (duplicate_of_arrival_id IS NOT NULL)
    ),
    CONSTRAINT ck_event_arrivals_rejection CHECK (
        (validation_state = 'REJECTED') = (rejection_reason IS NOT NULL)
    ),
    CONSTRAINT ck_event_arrivals_quarantine CHECK (
        (validation_state = 'QUARANTINED') = (quarantine_reason IS NOT NULL)
    ),
    -- Quarantine keeps only what is needed to debug the record, and it keeps it for less time than
    -- the accepted stream. Forcing the retention class here is what stops quarantine turning into
    -- a second, ungoverned copy of everything that ever failed validation.
    CONSTRAINT ck_event_arrivals_quarantine_retention CHECK (
        validation_state <> 'QUARANTINED' OR retention_class = 'QUARANTINE'
    ),
    -- An implausible event time is a reason to reject an arrival, not a reason to refuse to record
    -- that it happened. So the coherence rule binds only what was accepted: rejected evidence is
    -- stored exactly as it came in, skewed clock and all.
    CONSTRAINT ck_event_arrivals_accepted_ordering CHECK (
        validation_state <> 'ACCEPTED' OR received_at >= occurred_at
    ),
    CONSTRAINT ck_event_arrivals_ingestion CHECK (
        ingested_at IS NULL OR (validation_state = 'ACCEPTED' AND ingested_at >= received_at)
    ),
    -- A fact republished from a source transaction can say when that transaction committed; an
    -- observation cannot, and must not pretend to.
    CONSTRAINT ck_event_arrivals_commit CHECK (
        (source_kind = 'OUTBOX_CDC') = (committed_at IS NOT NULL)
    ),
    -- Authoritative history may be backfilled with synthetic event ids and explicit provenance.
    -- Client-side evidence may not: nobody impressed a listing in a period we were not measuring,
    -- and a backfilled impression would be an invention that later ranking models train on.
    CONSTRAINT ck_event_arrivals_backfill CHECK (
        source_kind <> 'BACKFILL' OR event_class <> 'INTERACTION_OBSERVATION'
    ),
    -- The body lives in restricted landing storage. This row carries its size and digest so that
    -- an oversized or altered payload is detectable without the transactional database holding it.
    CONSTRAINT ck_event_arrivals_payload_size CHECK (
        payload_byte_size >= 0 AND payload_byte_size <= 262144
    ),
    CONSTRAINT ck_event_arrivals_expiry CHECK (expires_at > received_at),
    CONSTRAINT ck_event_arrivals_suppression CHECK (
        suppressed_at IS NULL OR suppressed_at >= received_at
    )
);

-- Inbox uniqueness is the last duplicate defence, so one envelope identity may be accepted once.
-- The duplicate rows that record the later attempts are deliberately outside the constraint --
-- refusing to store them would destroy the evidence that a producer is re-sending.
CREATE UNIQUE INDEX uk_event_arrivals_accepted_event_id
    ON event_arrivals (event_id)
    WHERE validation_state <> 'DUPLICATE';

CREATE INDEX idx_event_arrivals_contract ON event_arrivals (event_name, schema_version, received_at);
CREATE INDEX idx_event_arrivals_occurred ON event_arrivals (occurred_at);
CREATE INDEX idx_event_arrivals_aggregate
    ON event_arrivals (aggregate_type, aggregate_id, aggregate_version);
CREATE INDEX idx_event_arrivals_subject ON event_arrivals (subject_pseudonym)
    WHERE subject_pseudonym IS NOT NULL;
CREATE INDEX idx_event_arrivals_unhealthy ON event_arrivals (validation_state, received_at)
    WHERE validation_state <> 'ACCEPTED';
CREATE INDEX idx_event_arrivals_expiry ON event_arrivals (expires_at);
--rollback DROP TABLE event_arrivals;

--changeset ninggiangboy:030-06-privacy-subject-links
CREATE TABLE privacy_subject_links (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_pseudonym          VARCHAR(64) NOT NULL,
    source_subject_kind       VARCHAR(24) NOT NULL,
    target_pseudonym          VARCHAR(64) NOT NULL,
    target_subject_kind       VARCHAR(24) NOT NULL,
    link_method               VARCHAR(24) NOT NULL,
    confidence                NUMERIC(5,4),
    purpose                   VARCHAR(48) NOT NULL,
    legal_basis               VARCHAR(32) NOT NULL,
    consent_reference         VARCHAR(64),
    retroactive_merge_allowed BOOLEAN NOT NULL DEFAULT false,
    effective_from            TIMESTAMPTZ NOT NULL,
    effective_to              TIMESTAMPTZ,
    state                     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    revoked_at                TIMESTAMPTZ,
    suppressed_at             TIMESTAMPTZ,
    suppression_reason        VARCHAR(48),
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_privacy_subject_links_scope
        UNIQUE (source_pseudonym, target_pseudonym, purpose, effective_from),
    -- Pseudonyms are hex. The pattern exists so that an email address, a phone number or a raw
    -- account identifier cannot be written into a link row by a service that took a shortcut.
    CONSTRAINT ck_privacy_subject_links_source_pseudonym CHECK (
        source_pseudonym ~ '^[0-9a-f]{32,64}$'
    ),
    CONSTRAINT ck_privacy_subject_links_target_pseudonym CHECK (
        target_pseudonym ~ '^[0-9a-f]{32,64}$'
    ),
    CONSTRAINT ck_privacy_subject_links_not_self CHECK (source_pseudonym <> target_pseudonym),
    CONSTRAINT ck_privacy_subject_links_source_kind CHECK (
        source_subject_kind IN ('ANONYMOUS_SESSION', 'DEVICE_INSTALLATION', 'AUTHENTICATED_SUBJECT')
    ),
    CONSTRAINT ck_privacy_subject_links_target_kind CHECK (
        target_subject_kind IN ('ANONYMOUS_SESSION', 'DEVICE_INSTALLATION', 'AUTHENTICATED_SUBJECT')
    ),
    -- Three methods, and the omissions are the point: there is no device fingerprint, no shared
    -- network address, no shared payment instrument and no household inference. A link the subject
    -- did not make and cannot see is exactly the covert identity this design forbids, and a
    -- vocabulary that has no name for it cannot be talked into one later.
    CONSTRAINT ck_privacy_subject_links_method CHECK (
        link_method IN ('AUTHENTICATION', 'EXPLICIT_DECLARATION', 'ACCOUNT_MERGE')
    ),
    -- Authentication is certain, so a confidence on it would be false precision. Everything else is
    -- an inference and has to say how sure it is.
    CONSTRAINT ck_privacy_subject_links_confidence CHECK (
        (link_method = 'AUTHENTICATION' AND confidence IS NULL)
            OR (link_method <> 'AUTHENTICATION' AND confidence > 0 AND confidence <= 1)
    ),
    -- Merging a person's past anonymous behaviour into their account is a separate permission from
    -- linking their future behaviour, and it needs a consent record to point at.
    CONSTRAINT ck_privacy_subject_links_retroactive CHECK (
        NOT retroactive_merge_allowed OR consent_reference IS NOT NULL
    ),
    CONSTRAINT ck_privacy_subject_links_interval CHECK (
        effective_to IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT ck_privacy_subject_links_state CHECK (
        state IN ('ACTIVE', 'REVOKED', 'SUPPRESSED')
    ),
    CONSTRAINT ck_privacy_subject_links_revoked CHECK ((state = 'REVOKED') = (revoked_at IS NOT NULL)),
    CONSTRAINT ck_privacy_subject_links_suppressed CHECK (
        (state = 'SUPPRESSED') = (suppressed_at IS NOT NULL AND suppression_reason IS NOT NULL)
    ),
    CONSTRAINT ck_privacy_subject_links_version CHECK (version >= 0)
);

CREATE INDEX idx_privacy_subject_links_source ON privacy_subject_links (source_pseudonym, state);
CREATE INDEX idx_privacy_subject_links_target ON privacy_subject_links (target_pseudonym, state);
CREATE INDEX idx_privacy_subject_links_suppressed ON privacy_subject_links (suppressed_at)
    WHERE state = 'SUPPRESSED';
--rollback DROP TABLE privacy_subject_links;

--changeset ninggiangboy:030-07-data-corrections
CREATE TABLE data_corrections (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correction_kind           VARCHAR(24) NOT NULL,
    target_kind               VARCHAR(24) NOT NULL,
    corrects_event_id         UUID,
    target_data_product_id    UUID,
    target_partition_key      VARCHAR(64),
    source_range_start        TIMESTAMPTZ,
    source_range_end          TIMESTAMPTZ,
    reason_code               VARCHAR(48) NOT NULL,
    reason_detail             VARCHAR(500) NOT NULL,
    replacement_event_id      UUID,
    replacement_reference     VARCHAR(200),
    original_occurred_at      TIMESTAMPTZ,
    effective_at              TIMESTAMPTZ NOT NULL,
    correction_schema_version SMALLINT NOT NULL DEFAULT 1,
    actor_kind                VARCHAR(24) NOT NULL,
    actor_reference           VARCHAR(64) NOT NULL,
    approved_by               VARCHAR(64),
    application_state         VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    applied_at                TIMESTAMPTZ,
    recorded_at               TIMESTAMPTZ NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_data_corrections_product FOREIGN KEY (target_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT ck_data_corrections_kind CHECK (
        correction_kind IN ('SEMANTIC_CORRECTION', 'INVALIDATION',
                            'PRIVACY_SUPPRESSION', 'LATE_RESTATEMENT')
    ),
    CONSTRAINT ck_data_corrections_target_kind CHECK (
        target_kind IN ('EVENT', 'DATASET_PARTITION', 'METRIC_MATERIALIZATION')
    ),
    CONSTRAINT ck_data_corrections_actor CHECK (
        actor_kind IN ('OPERATOR', 'PRIVACY_JOB', 'PIPELINE', 'SOURCE_DOMAIN')
    ),
    CONSTRAINT ck_data_corrections_event_target CHECK (
        (target_kind = 'EVENT') = (corrects_event_id IS NOT NULL)
    ),
    CONSTRAINT ck_data_corrections_product_target CHECK (
        target_kind = 'EVENT'
            OR (target_data_product_id IS NOT NULL AND target_partition_key IS NOT NULL)
    ),
    -- A correction that names neither one event nor a closed time range is an open licence to
    -- rewrite history, so the range must have both ends.
    CONSTRAINT ck_data_corrections_range CHECK (
        (source_range_start IS NULL) = (source_range_end IS NULL)
    ),
    CONSTRAINT ck_data_corrections_range_order CHECK (
        source_range_end IS NULL OR source_range_end > source_range_start
    ),
    CONSTRAINT ck_data_corrections_bounded CHECK (
        target_kind = 'EVENT' OR source_range_start IS NOT NULL
    ),
    -- Saying a number was wrong is one thing; saying what it should have been is another, and a
    -- semantic correction is only useful if it carries the replacement.
    CONSTRAINT ck_data_corrections_replacement CHECK (
        correction_kind <> 'SEMANTIC_CORRECTION'
            OR replacement_event_id IS NOT NULL
            OR replacement_reference IS NOT NULL
    ),
    -- Erasing a subject does not restate their behaviour as something else. A suppression removes;
    -- it never substitutes.
    CONSTRAINT ck_data_corrections_suppression CHECK (
        correction_kind <> 'PRIVACY_SUPPRESSION'
            OR (replacement_event_id IS NULL AND replacement_reference IS NULL)
    ),
    -- Replays must be able to tell original arrival from corrected effect, so the corrected
    -- effective time may not be dated before the thing it corrects.
    CONSTRAINT ck_data_corrections_effective CHECK (
        original_occurred_at IS NULL OR effective_at >= original_occurred_at
    ),
    CONSTRAINT ck_data_corrections_schema_version CHECK (correction_schema_version > 0),
    CONSTRAINT ck_data_corrections_application CHECK (
        application_state IN ('PENDING', 'APPLIED', 'FAILED')
    ),
    CONSTRAINT ck_data_corrections_applied CHECK (
        (application_state = 'APPLIED') = (applied_at IS NOT NULL)
    ),
    -- An invalidation destroys the standing of evidence other people are relying on, so a person
    -- other than the requester has to have agreed to it.
    CONSTRAINT ck_data_corrections_approval CHECK (
        correction_kind <> 'INVALIDATION'
            OR (approved_by IS NOT NULL AND approved_by <> actor_reference)
    )
);

CREATE INDEX idx_data_corrections_event ON data_corrections (corrects_event_id)
    WHERE corrects_event_id IS NOT NULL;
CREATE INDEX idx_data_corrections_product
    ON data_corrections (target_data_product_id, effective_at);
CREATE INDEX idx_data_corrections_pending ON data_corrections (recorded_at)
    WHERE application_state = 'PENDING';
--rollback DROP TABLE data_corrections;

--changeset ninggiangboy:030-08-pipeline-runs
CREATE TABLE pipeline_runs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_product_id          UUID NOT NULL,
    run_kind                 VARCHAR(16) NOT NULL,
    specification_digest     CHAR(64) NOT NULL,
    code_version             VARCHAR(64) NOT NULL,
    config_digest            CHAR(64) NOT NULL,
    partition_key            VARCHAR(64),
    input_watermark          TIMESTAMPTZ NOT NULL,
    output_watermark         TIMESTAMPTZ NOT NULL,
    deletion_watermark       TIMESTAMPTZ,
    state                    VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    quality_state            VARCHAR(8) NOT NULL DEFAULT 'UNKNOWN',
    accepted_degraded_input  BOOLEAN NOT NULL DEFAULT false,
    degraded_input_reason    VARCHAR(500),
    rows_read                BIGINT,
    rows_written             BIGINT,
    rows_rejected            BIGINT,
    output_snapshot_reference VARCHAR(200),
    output_digest            CHAR(64),
    published_as_current     BOOLEAN NOT NULL DEFAULT false,
    attempt_count            INTEGER NOT NULL DEFAULT 0,
    lease_owner              VARCHAR(128),
    lease_expires_at         TIMESTAMPTZ,
    fencing_token            BIGINT,
    failure_class            VARCHAR(64),
    started_at               TIMESTAMPTZ,
    finished_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    -- Re-running the same product version over the same inputs with the same code returns the same
    -- run rather than a second output, which is what makes a retry after a crash safe.
    CONSTRAINT uk_pipeline_runs_specification
        UNIQUE NULLS NOT DISTINCT
        (data_product_id, specification_digest, input_watermark, partition_key),
    CONSTRAINT fk_pipeline_runs_product FOREIGN KEY (data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT ck_pipeline_runs_kind CHECK (
        run_kind IN ('SCHEDULED', 'BACKFILL', 'RESTATEMENT', 'MANUAL')
    ),
    CONSTRAINT ck_pipeline_runs_state CHECK (
        state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_pipeline_runs_quality CHECK (quality_state IN ('PASS', 'WARN', 'FAIL', 'UNKNOWN')),
    CONSTRAINT ck_pipeline_runs_digests CHECK (
        specification_digest ~ '^[0-9a-f]{64}$'
            AND config_digest ~ '^[0-9a-f]{64}$'
            AND (output_digest IS NULL OR output_digest ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_pipeline_runs_watermarks CHECK (output_watermark >= input_watermark),
    CONSTRAINT ck_pipeline_runs_counts CHECK (
        (rows_read IS NULL OR rows_read >= 0)
            AND (rows_written IS NULL OR rows_written >= 0)
            AND (rows_rejected IS NULL OR rows_rejected >= 0)
    ),
    CONSTRAINT ck_pipeline_runs_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_pipeline_runs_lease CHECK ((lease_owner IS NULL) = (lease_expires_at IS NULL)),
    CONSTRAINT ck_pipeline_runs_finished CHECK (
        (state IN ('SUCCEEDED', 'FAILED', 'CANCELLED')) = (finished_at IS NOT NULL)
    ),
    CONSTRAINT ck_pipeline_runs_timing CHECK (finished_at IS NULL OR finished_at >= started_at),
    CONSTRAINT ck_pipeline_runs_failure CHECK (
        (state = 'FAILED') = (failure_class IS NOT NULL)
    ),
    CONSTRAINT ck_pipeline_runs_output CHECK (
        state <> 'SUCCEEDED' OR output_snapshot_reference IS NOT NULL
    ),
    -- A failed run does not get to leave its half-written output standing as the current version of
    -- the dataset; the previous good version keeps serving until a run actually succeeds.
    CONSTRAINT ck_pipeline_runs_publication CHECK (
        NOT published_as_current
            OR (state = 'SUCCEEDED' AND quality_state IN ('PASS', 'WARN'))
    ),
    -- Consuming a degraded upstream is allowed. Consuming it and then reporting your own output as
    -- clean is not: that is how one known-bad dataset becomes a chain of dashboards nobody
    -- questions.
    CONSTRAINT ck_pipeline_runs_degraded_pair CHECK (
        accepted_degraded_input = (degraded_input_reason IS NOT NULL)
    ),
    CONSTRAINT ck_pipeline_runs_degraded_quality CHECK (
        NOT accepted_degraded_input OR quality_state <> 'PASS'
    ),
    CONSTRAINT ck_pipeline_runs_fencing CHECK (fencing_token IS NULL OR fencing_token >= 0),
    CONSTRAINT ck_pipeline_runs_version CHECK (version >= 0)
);

CREATE INDEX idx_pipeline_runs_product_state ON pipeline_runs (data_product_id, state, created_at);
CREATE INDEX idx_pipeline_runs_claimable ON pipeline_runs (created_at)
    WHERE state IN ('PENDING', 'RUNNING');
-- An unpartitioned product has exactly one current run, and a partitioned one has exactly one per
-- partition. NULLS NOT DISTINCT is what makes the unpartitioned case actually unique.
CREATE UNIQUE INDEX uk_pipeline_runs_current
    ON pipeline_runs (data_product_id, partition_key) NULLS NOT DISTINCT
    WHERE published_as_current;
--rollback DROP TABLE pipeline_runs;

--changeset ninggiangboy:030-09-pipeline-run-inputs
CREATE TABLE pipeline_run_inputs (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id          UUID NOT NULL,
    upstream_data_product_id UUID NOT NULL,
    upstream_pipeline_run_id UUID,
    snapshot_reference       VARCHAR(200) NOT NULL,
    watermark                TIMESTAMPTZ NOT NULL,
    input_digest             CHAR(64),
    row_count                BIGINT,
    degraded                 BOOLEAN NOT NULL DEFAULT false,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_pipeline_run_inputs_snapshot
        UNIQUE (pipeline_run_id, upstream_data_product_id, snapshot_reference),
    CONSTRAINT fk_pipeline_run_inputs_run FOREIGN KEY (pipeline_run_id)
        REFERENCES pipeline_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_run_inputs_upstream_product FOREIGN KEY (upstream_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT fk_pipeline_run_inputs_upstream_run FOREIGN KEY (upstream_pipeline_run_id)
        REFERENCES pipeline_runs (id),
    CONSTRAINT ck_pipeline_run_inputs_digest CHECK (
        input_digest IS NULL OR input_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_pipeline_run_inputs_rows CHECK (row_count IS NULL OR row_count >= 0)
);

CREATE INDEX idx_pipeline_run_inputs_upstream_run
    ON pipeline_run_inputs (upstream_pipeline_run_id)
    WHERE upstream_pipeline_run_id IS NOT NULL;
--rollback DROP TABLE pipeline_run_inputs;

--changeset ninggiangboy:030-10-data-quality-check-definitions
CREATE TABLE data_quality_check_definitions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_key            VARCHAR(96) NOT NULL,
    check_version        SMALLINT NOT NULL,
    data_product_id      UUID NOT NULL,
    quality_dimension    VARCHAR(24) NOT NULL,
    description          VARCHAR(500) NOT NULL,
    expression_reference VARCHAR(200) NOT NULL,
    threshold_expression VARCHAR(200),
    severity             VARCHAR(8) NOT NULL,
    consumer_behaviour   VARCHAR(16) NOT NULL,
    owner                VARCHAR(64) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_data_quality_check_definitions_version UNIQUE (check_key, check_version),
    CONSTRAINT fk_data_quality_check_definitions_product FOREIGN KEY (data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT ck_data_quality_check_definitions_key CHECK (check_key ~ '^[a-z][a-z0-9_]{4,95}$'),
    CONSTRAINT ck_data_quality_check_definitions_check_version CHECK (check_version > 0),
    CONSTRAINT ck_data_quality_check_definitions_dimension CHECK (
        quality_dimension IN ('FRESHNESS', 'COMPLETENESS', 'UNIQUENESS', 'SCHEMA_VALIDITY',
                              'REFERENTIAL_INTEGRITY', 'RANGE', 'DISTRIBUTION', 'ORDERING',
                              'PRIVACY', 'RECONCILIATION', 'REPRODUCIBILITY')
    ),
    CONSTRAINT ck_data_quality_check_definitions_severity CHECK (severity IN ('WARN', 'FAIL')),
    CONSTRAINT ck_data_quality_check_definitions_behaviour CHECK (
        consumer_behaviour IN ('BLOCK', 'FALLBACK', 'QUARANTINE', 'ACCEPT')
    ),
    CONSTRAINT ck_data_quality_check_definitions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    -- Declaring a check fatal and then declaring its failure acceptable is a contradiction. Either
    -- the check is a warning or the consumer has to do something when it fails.
    CONSTRAINT ck_data_quality_check_definitions_coherent CHECK (
        severity <> 'FAIL' OR consumer_behaviour <> 'ACCEPT'
    ),
    -- A privacy check exists to find data that should not be in the product at all, so a warning
    -- that everyone ignores is not an acceptable outcome for one.
    CONSTRAINT ck_data_quality_check_definitions_privacy CHECK (
        quality_dimension <> 'PRIVACY' OR severity = 'FAIL'
    ),
    CONSTRAINT ck_data_quality_check_definitions_version_number CHECK (version >= 0)
);

CREATE INDEX idx_data_quality_check_definitions_product
    ON data_quality_check_definitions (data_product_id, status);
--rollback DROP TABLE data_quality_check_definitions;

--changeset ninggiangboy:030-11-data-quality-results
CREATE TABLE data_quality_results (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_run_id          UUID NOT NULL,
    data_quality_check_id    UUID NOT NULL,
    status                   VARCHAR(8) NOT NULL,
    observed_value           NUMERIC(20,6),
    expected_value           NUMERIC(20,6),
    observed_text            VARCHAR(200),
    failed_row_count         BIGINT,
    sample_reference         VARCHAR(200),
    evaluated_at             TIMESTAMPTZ NOT NULL,
    owner_action             VARCHAR(16) NOT NULL DEFAULT 'NONE',
    action_actor             VARCHAR(64),
    action_reason            VARCHAR(500),
    acted_at                 TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_data_quality_results_check UNIQUE (pipeline_run_id, data_quality_check_id),
    CONSTRAINT fk_data_quality_results_run FOREIGN KEY (pipeline_run_id)
        REFERENCES pipeline_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_data_quality_results_check FOREIGN KEY (data_quality_check_id)
        REFERENCES data_quality_check_definitions (id),
    CONSTRAINT ck_data_quality_results_status CHECK (status IN ('PASS', 'WARN', 'FAIL', 'UNKNOWN')),
    CONSTRAINT ck_data_quality_results_action CHECK (
        owner_action IN ('NONE', 'ACKNOWLEDGED', 'QUARANTINED', 'RESTATED', 'WAIVED')
    ),
    -- Unknown means the check could not be evaluated. Recording a number beside it would turn "we
    -- do not know" into "we measured zero", which is the mistake this whole migration is against.
    CONSTRAINT ck_data_quality_results_unknown CHECK (
        status <> 'UNKNOWN' OR (observed_value IS NULL AND observed_text IS NULL)
    ),
    CONSTRAINT ck_data_quality_results_failed_rows CHECK (
        failed_row_count IS NULL OR failed_row_count >= 0
    ),
    -- Waiving a failure is a decision, and a decision has an owner and a reason on the record.
    CONSTRAINT ck_data_quality_results_waiver CHECK (
        owner_action = 'NONE'
            OR (action_actor IS NOT NULL AND action_reason IS NOT NULL AND acted_at IS NOT NULL)
    )
);

CREATE INDEX idx_data_quality_results_failing
    ON data_quality_results (data_quality_check_id, evaluated_at)
    WHERE status IN ('WARN', 'FAIL');
--rollback DROP TABLE data_quality_results;

--changeset ninggiangboy:030-12-metric-definitions
CREATE TABLE metric_definitions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_key                VARCHAR(120) NOT NULL,
    semantic_version          SMALLINT NOT NULL,
    metric_family             VARCHAR(24) NOT NULL,
    business_question         VARCHAR(500) NOT NULL,
    human_definition          VARCHAR(1000) NOT NULL,
    numerator_expression      VARCHAR(500) NOT NULL,
    denominator_expression    VARCHAR(500),
    measure_unit              VARCHAR(24) NOT NULL,
    currency_handling         VARCHAR(24),
    fx_data_product_id        UUID,
    grain                     VARCHAR(200) NOT NULL,
    population_expression     VARCHAR(500) NOT NULL,
    exclusion_expression      VARCHAR(500),
    deduplication_rule        VARCHAR(200) NOT NULL,
    bot_filter_version        VARCHAR(32) NOT NULL,
    window_days               INTEGER NOT NULL,
    window_time_basis         VARCHAR(16) NOT NULL,
    window_time_zone          VARCHAR(64) NOT NULL,
    attribution_rule          VARCHAR(120),
    outcome_maturity_days     INTEGER NOT NULL,
    source_data_product_id    UUID NOT NULL,
    minimum_quality_state     VARCHAR(8) NOT NULL DEFAULT 'PASS',
    authority_class           VARCHAR(24) NOT NULL,
    business_owner            VARCHAR(64) NOT NULL,
    technical_steward         VARCHAR(64) NOT NULL,
    sensitivity_class         VARCHAR(16) NOT NULL,
    intended_decisions        VARCHAR(500) NOT NULL,
    query_version             VARCHAR(64) NOT NULL,
    validation_example_reference VARCHAR(200),
    restatement_policy        VARCHAR(24) NOT NULL,
    comparable_with_previous  BOOLEAN NOT NULL DEFAULT true,
    supersedes_id             UUID,
    status                    VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    reviewed_at               TIMESTAMPTZ,
    published_at              TIMESTAMPTZ,
    deprecated_at             TIMESTAMPTZ,
    retired_at                TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_metric_definitions_version UNIQUE (metric_key, semantic_version),
    CONSTRAINT fk_metric_definitions_source FOREIGN KEY (source_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT fk_metric_definitions_fx FOREIGN KEY (fx_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT fk_metric_definitions_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES metric_definitions (id),
    -- "booking_conversion_rate" is not a metric name; it is four metrics arguing. The pattern
    -- forces the window and the semantic version into the name itself, so that
    -- confirmed_booking_per_exposed_search_session_28d_v2 can never be confused with
    -- completed_stay_per_unique_guest_90d_v1 in a dashboard title or a slide.
    CONSTRAINT ck_metric_definitions_key CHECK (
        metric_key ~ '^[a-z][a-z0-9_]*_[0-9]{1,4}d_v[0-9]{1,3}$'
    ),
    CONSTRAINT ck_metric_definitions_semantic_version CHECK (semantic_version > 0),
    CONSTRAINT ck_metric_definitions_family CHECK (
        metric_family IN ('DISCOVERY', 'STAY_VALUE', 'GUEST_ECONOMICS', 'HOST_ECONOMICS',
                          'PLATFORM_ECONOMICS', 'RELIABILITY', 'SAFETY_SUPPORT', 'MARKETPLACE')
    ),
    CONSTRAINT ck_metric_definitions_unit CHECK (
        measure_unit IN ('RATIO', 'COUNT', 'MONEY_MINOR', 'DURATION_SECONDS', 'SCORE')
    ),
    CONSTRAINT ck_metric_definitions_time_basis CHECK (
        window_time_basis IN ('EVENT_TIME', 'INGEST_TIME')
    ),
    -- A full IANA identifier, for the same reason markets carry one: an aggregation boundary that
    -- does not know its own daylight-saving history silently double-counts an hour twice a year.
    CONSTRAINT ck_metric_definitions_time_zone CHECK (
        window_time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$' OR window_time_zone = 'UTC'
    ),
    CONSTRAINT ck_metric_definitions_authority CHECK (
        authority_class IN ('OPERATIONAL', 'PRODUCT', 'AUDITED_FINANCIAL')
    ),
    CONSTRAINT ck_metric_definitions_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_metric_definitions_quality_floor CHECK (
        minimum_quality_state IN ('PASS', 'WARN')
    ),
    CONSTRAINT ck_metric_definitions_restatement CHECK (
        restatement_policy IN ('NONE', 'OPEN_PARTITION', 'VERSIONED_RESTATEMENT')
    ),
    CONSTRAINT ck_metric_definitions_status CHECK (
        status IN ('DRAFT', 'REVIEWED', 'CURRENT', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_metric_definitions_windows CHECK (
        window_days > 0 AND outcome_maturity_days >= 0
    ),
    -- A rate without a denominator is a count wearing a percentage sign.
    CONSTRAINT ck_metric_definitions_ratio CHECK (
        measure_unit <> 'RATIO' OR denominator_expression IS NOT NULL
    ),
    -- Money aggregated across markets is either kept in native currency or converted by a named,
    -- finance-approved rate dataset. There is no third option where the numbers just add up.
    -- The IS NOT NULL is load-bearing. "currency_handling IN (...)" alone evaluates to NULL when
    -- the column is null, and a CHECK that evaluates to NULL passes -- so the money metric that
    -- says nothing at all about currency, which is the case worth catching, would sail through.
    CONSTRAINT ck_metric_definitions_currency CHECK (
        measure_unit <> 'MONEY_MINOR'
            OR (currency_handling IS NOT NULL
                AND currency_handling IN ('NATIVE_PARTITIONED', 'CONVERTED'))
    ),
    CONSTRAINT ck_metric_definitions_currency_scope CHECK (
        measure_unit = 'MONEY_MINOR' OR currency_handling IS NULL
    ),
    CONSTRAINT ck_metric_definitions_fx CHECK (
        (currency_handling = 'CONVERTED') = (fx_data_product_id IS NOT NULL)
    ),
    -- An audited financial number may not be sourced from a warning-grade dataset, and it may not
    -- be quietly restated the way a product metric can: closed periods follow finance correction
    -- policy, not analytics convenience.
    CONSTRAINT ck_metric_definitions_audited CHECK (
        authority_class <> 'AUDITED_FINANCIAL'
            OR (minimum_quality_state = 'PASS' AND restatement_policy <> 'OPEN_PARTITION')
    ),
    -- An outcome with a maturity horizon that is measured over a shorter window than the horizon
    -- itself reports an answer before the answer exists.
    CONSTRAINT ck_metric_definitions_maturity CHECK (outcome_maturity_days <= window_days),
    CONSTRAINT ck_metric_definitions_publication CHECK (
        status IN ('DRAFT', 'REVIEWED') OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_metric_definitions_deprecated CHECK (
        (status = 'DEPRECATED') = (deprecated_at IS NOT NULL)
    ),
    CONSTRAINT ck_metric_definitions_retired CHECK ((status = 'RETIRED') = (retired_at IS NOT NULL)),
    -- A semantic change makes a new version, and a new version that is not comparable with the one
    -- it replaces has to say so where every consumer reading the definition will see it.
    CONSTRAINT ck_metric_definitions_supersession CHECK (
        semantic_version = 1 OR supersedes_id IS NOT NULL
    ),
    CONSTRAINT ck_metric_definitions_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_metric_definitions_current
    ON metric_definitions (metric_key)
    WHERE status = 'CURRENT';

CREATE INDEX idx_metric_definitions_family ON metric_definitions (metric_family, status);
CREATE INDEX idx_metric_definitions_source ON metric_definitions (source_data_product_id);
--rollback DROP TABLE metric_definitions;

--changeset ninggiangboy:030-13-metric-materializations
CREATE TABLE metric_materializations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_definition_id  UUID NOT NULL,
    pipeline_run_id       UUID NOT NULL,
    slice_digest          CHAR(64) NOT NULL,
    slice_descriptor      JSONB NOT NULL,
    window_start          TIMESTAMPTZ NOT NULL,
    window_end            TIMESTAMPTZ NOT NULL,
    measured_value        NUMERIC(24,8) NOT NULL,
    value_minor           BIGINT,
    currency              VARCHAR(3),
    denominator_value     NUMERIC(24,8),
    sample_size           BIGINT,
    data_cutoff_at        TIMESTAMPTZ NOT NULL,
    completeness          NUMERIC(5,4),
    quality_state         VARCHAR(8) NOT NULL,
    publication_state     VARCHAR(16) NOT NULL DEFAULT 'PROVISIONAL',
    restates_id           UUID,
    materialized_at       TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_metric_materializations_slice
        UNIQUE (metric_definition_id, slice_digest, window_start, window_end, pipeline_run_id),
    CONSTRAINT fk_metric_materializations_metric FOREIGN KEY (metric_definition_id)
        REFERENCES metric_definitions (id),
    CONSTRAINT fk_metric_materializations_run FOREIGN KEY (pipeline_run_id)
        REFERENCES pipeline_runs (id),
    CONSTRAINT fk_metric_materializations_restates FOREIGN KEY (restates_id)
        REFERENCES metric_materializations (id),
    CONSTRAINT ck_metric_materializations_digest CHECK (slice_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_metric_materializations_slice_object CHECK (
        jsonb_typeof(slice_descriptor) = 'object'
    ),
    CONSTRAINT ck_metric_materializations_window CHECK (window_end > window_start),
    CONSTRAINT ck_metric_materializations_cutoff CHECK (data_cutoff_at > window_start),
    CONSTRAINT ck_metric_materializations_money CHECK (
        (value_minor IS NULL) = (currency IS NULL)
    ),
    CONSTRAINT ck_metric_materializations_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_metric_materializations_sample CHECK (sample_size IS NULL OR sample_size >= 0),
    CONSTRAINT ck_metric_materializations_completeness CHECK (
        completeness IS NULL OR (completeness >= 0 AND completeness <= 1)
    ),
    CONSTRAINT ck_metric_materializations_quality CHECK (
        quality_state IN ('PASS', 'WARN', 'FAIL', 'UNKNOWN')
    ),
    CONSTRAINT ck_metric_materializations_publication CHECK (
        publication_state IN ('PROVISIONAL', 'CURRENT', 'RESTATED', 'WITHDRAWN')
    ),
    -- A number whose checks failed, or whose checks never ran, may exist and may be investigated.
    -- It may not be the number a report shows.
    CONSTRAINT ck_metric_materializations_current CHECK (
        publication_state <> 'CURRENT' OR quality_state IN ('PASS', 'WARN')
    ),
    CONSTRAINT ck_metric_materializations_restatement CHECK (
        restates_id IS NULL OR publication_state IN ('PROVISIONAL', 'CURRENT')
    ),
    CONSTRAINT ck_metric_materializations_not_self CHECK (restates_id IS NULL OR restates_id <> id)
);

-- One published value per metric version, slice and window. Late data restates by publishing a new
-- row that names the one it replaces, never by editing the number somebody already quoted.
CREATE UNIQUE INDEX uk_metric_materializations_current
    ON metric_materializations (metric_definition_id, slice_digest, window_start, window_end)
    WHERE publication_state = 'CURRENT';

CREATE INDEX idx_metric_materializations_window
    ON metric_materializations (metric_definition_id, window_start, window_end);
CREATE INDEX idx_metric_materializations_run ON metric_materializations (pipeline_run_id);
--rollback DROP TABLE metric_materializations;

--changeset ninggiangboy:030-14-experiment-definitions
CREATE TABLE experiment_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_key          VARCHAR(96) NOT NULL,
    title                   VARCHAR(200) NOT NULL,
    hypothesis              VARCHAR(1000) NOT NULL,
    namespace               VARCHAR(32) NOT NULL,
    layer                   VARCHAR(48) NOT NULL,
    exclusive_in_namespace  BOOLEAN NOT NULL DEFAULT true,
    market_code             VARCHAR(2),
    business_owner          VARCHAR(64) NOT NULL,
    analyst                 VARCHAR(64) NOT NULL,
    novelty_assumption      VARCHAR(500),
    carryover_assumption    VARCHAR(500),
    long_term_holdout       BOOLEAN NOT NULL DEFAULT false,
    holdout_share           NUMERIC(5,4),
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    reviewed_at             TIMESTAMPTZ,
    archived_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_experiment_definitions_key UNIQUE (experiment_key),
    CONSTRAINT ck_experiment_definitions_key CHECK (experiment_key ~ '^[a-z][a-z0-9_]{4,95}$'),
    -- A closed namespace list is what makes collision detection possible at all. A team that
    -- invents its own namespace string has opted out of every exclusion rule in this table.
    CONSTRAINT ck_experiment_definitions_namespace CHECK (
        namespace IN ('SEARCH_RANK', 'PUBLIC_PRICE', 'CHECKOUT', 'MESSAGING', 'NOTIFICATION',
                      'HOST_PRICING_TOOLS', 'REVIEW_PRESENTATION', 'ONBOARDING', 'SUPPORT')
    ),
    CONSTRAINT ck_experiment_definitions_market CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_experiment_definitions_status CHECK (
        status IN ('DRAFT', 'REVIEWED', 'SCHEDULED', 'RUNNING', 'PAUSED', 'STOPPED',
                   'COMPLETED', 'ANALYZED', 'ARCHIVED', 'ROLLED_BACK')
    ),
    CONSTRAINT ck_experiment_definitions_holdout CHECK (
        long_term_holdout = (holdout_share IS NOT NULL)
    ),
    CONSTRAINT ck_experiment_definitions_holdout_share CHECK (
        holdout_share IS NULL OR (holdout_share > 0 AND holdout_share <= 0.1)
    ),
    -- A short experiment that has not said what it assumes about novelty and carryover has not
    -- been reviewed, whatever the status column says.
    CONSTRAINT ck_experiment_definitions_review CHECK (
        status = 'DRAFT'
            OR (reviewed_at IS NOT NULL
                AND novelty_assumption IS NOT NULL
                AND carryover_assumption IS NOT NULL)
    ),
    CONSTRAINT ck_experiment_definitions_archived CHECK (
        (status = 'ARCHIVED') = (archived_at IS NOT NULL)
    ),
    CONSTRAINT ck_experiment_definitions_version CHECK (version >= 0)
);

CREATE INDEX idx_experiment_definitions_namespace ON experiment_definitions (namespace, status);
--rollback DROP TABLE experiment_definitions;

--changeset ninggiangboy:030-15-experiment-epochs
CREATE TABLE experiment_epochs (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_definition_id       UUID NOT NULL,
    epoch_number                   INTEGER NOT NULL,
    assignment_unit                VARCHAR(24) NOT NULL,
    population_expression          VARCHAR(500) NOT NULL,
    population_digest              CHAR(64) NOT NULL,
    exclusion_expression           VARCHAR(500),
    bucket_count                   INTEGER NOT NULL DEFAULT 10000,
    salt_version                   VARCHAR(32) NOT NULL,
    allocator_version              VARCHAR(32) NOT NULL,
    exposure_rule_key              VARCHAR(96) NOT NULL,
    exposure_rule_version          SMALLINT NOT NULL,
    repeated_exposure_policy       VARCHAR(16) NOT NULL,
    estimand                       VARCHAR(24) NOT NULL,
    analysis_unit                  VARCHAR(24) NOT NULL,
    clustering_expression          VARCHAR(200),
    baseline_rate                  NUMERIC(12,8),
    minimum_detectable_effect      NUMERIC(12,8) NOT NULL,
    target_sample_size             BIGINT NOT NULL,
    minimum_runtime_days           INTEGER NOT NULL,
    maximum_runtime_days           INTEGER NOT NULL,
    alpha                          NUMERIC(6,5) NOT NULL,
    interval_method                VARCHAR(24) NOT NULL,
    sequential_method              VARCHAR(24) NOT NULL DEFAULT 'NONE',
    multiple_comparison_correction VARCHAR(24) NOT NULL DEFAULT 'NONE',
    variance_reduction             VARCHAR(24) NOT NULL DEFAULT 'NONE',
    missing_data_policy            VARCHAR(24) NOT NULL,
    decision_rule                  VARCHAR(500) NOT NULL,
    practical_significance         NUMERIC(12,8),
    rollback_plan                  VARCHAR(500) NOT NULL,
    approved_by                    VARCHAR(64),
    approved_at                    TIMESTAMPTZ,
    state                          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    scheduled_start_at             TIMESTAMPTZ,
    started_at                     TIMESTAMPTZ,
    scheduled_stop_at              TIMESTAMPTZ,
    stopped_at                     TIMESTAMPTZ,
    first_assignment_at            TIMESTAMPTZ,
    sealed                         BOOLEAN NOT NULL DEFAULT false,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL,
    version                        BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_experiment_epochs_number UNIQUE (experiment_definition_id, epoch_number),
    CONSTRAINT fk_experiment_epochs_definition FOREIGN KEY (experiment_definition_id)
        REFERENCES experiment_definitions (id),
    CONSTRAINT ck_experiment_epochs_number CHECK (epoch_number >= 1),
    CONSTRAINT ck_experiment_epochs_unit CHECK (
        assignment_unit IN ('USER', 'SESSION', 'LISTING', 'HOST', 'BOOKING',
                            'MARKET', 'TIME_SWITCHBACK')
    ),
    CONSTRAINT ck_experiment_epochs_analysis_unit CHECK (
        analysis_unit IN ('USER', 'SESSION', 'LISTING', 'HOST', 'BOOKING',
                          'MARKET', 'TIME_SWITCHBACK')
    ),
    CONSTRAINT ck_experiment_epochs_digest CHECK (population_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_experiment_epochs_buckets CHECK (
        bucket_count > 0 AND bucket_count <= 1000000
    ),
    CONSTRAINT ck_experiment_epochs_exposure_policy CHECK (
        repeated_exposure_policy IN ('FIRST_ONLY', 'PER_REQUEST', 'PER_UNIT_DAY', 'RAW')
    ),
    CONSTRAINT ck_experiment_epochs_estimand CHECK (
        estimand IN ('INTENT_TO_TREAT', 'TREATMENT_ON_TREATED')
    ),
    CONSTRAINT ck_experiment_epochs_interval_method CHECK (
        interval_method IN ('NORMAL_APPROXIMATION', 'BOOTSTRAP', 'DELTA_METHOD', 'BAYESIAN')
    ),
    CONSTRAINT ck_experiment_epochs_sequential CHECK (
        sequential_method IN ('NONE', 'ALPHA_SPENDING', 'MIXTURE_SPRT', 'GROUP_SEQUENTIAL')
    ),
    CONSTRAINT ck_experiment_epochs_correction CHECK (
        multiple_comparison_correction IN ('NONE', 'BONFERRONI', 'BENJAMINI_HOCHBERG', 'HOLM')
    ),
    CONSTRAINT ck_experiment_epochs_variance_reduction CHECK (
        variance_reduction IN ('NONE', 'CUPED', 'STRATIFICATION', 'REGRESSION_ADJUSTMENT')
    ),
    CONSTRAINT ck_experiment_epochs_missing_data CHECK (
        missing_data_policy IN ('COMPLETE_CASE', 'IMPUTE_DECLARED', 'CENSOR', 'EXCLUDE_UNIT')
    ),
    CONSTRAINT ck_experiment_epochs_state CHECK (
        state IN ('DRAFT', 'APPROVED', 'RUNNING', 'PAUSED', 'STOPPED', 'COMPLETED')
    ),
    CONSTRAINT ck_experiment_epochs_alpha CHECK (alpha > 0 AND alpha < 0.5),
    CONSTRAINT ck_experiment_epochs_power CHECK (
        minimum_detectable_effect > 0
            AND target_sample_size > 0
            AND minimum_runtime_days >= 1
            AND maximum_runtime_days >= minimum_runtime_days
    ),
    CONSTRAINT ck_experiment_epochs_baseline CHECK (
        baseline_rate IS NULL OR (baseline_rate >= 0 AND baseline_rate <= 1)
    ),
    -- Leaving draft means somebody accepted responsibility for the design. An epoch that starts
    -- assigning traffic without an approver is an experiment nobody agreed to run.
    CONSTRAINT ck_experiment_epochs_approval CHECK (
        state = 'DRAFT' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    -- Sealing is not a flag somebody sets; it is the observable fact that a unit has been bucketed.
    CONSTRAINT ck_experiment_epochs_seal CHECK (sealed = (first_assignment_at IS NOT NULL)),
    CONSTRAINT ck_experiment_epochs_timing CHECK (
        (started_at IS NULL OR first_assignment_at IS NULL OR first_assignment_at >= started_at)
            AND (stopped_at IS NULL OR started_at IS NULL OR stopped_at >= started_at)
            AND (scheduled_stop_at IS NULL OR scheduled_start_at IS NULL
                 OR scheduled_stop_at > scheduled_start_at)
    ),
    CONSTRAINT ck_experiment_epochs_running CHECK (
        state IN ('DRAFT', 'APPROVED') OR started_at IS NOT NULL
    ),
    -- Clustered analysis needs to say what the clusters are, or the intervals it reports are too
    -- narrow by an amount nobody can estimate afterwards.
    CONSTRAINT ck_experiment_epochs_clustering CHECK (
        analysis_unit = assignment_unit OR clustering_expression IS NOT NULL
    ),
    CONSTRAINT ck_experiment_epochs_version CHECK (version >= 0)
);

CREATE INDEX idx_experiment_epochs_state ON experiment_epochs (state, started_at);
CREATE INDEX idx_experiment_epochs_definition ON experiment_epochs (experiment_definition_id, state);
--rollback DROP TABLE experiment_epochs;

--changeset ninggiangboy:030-16-experiment-variants
CREATE TABLE experiment_variants (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_epoch_id   UUID NOT NULL,
    variant_key           VARCHAR(48) NOT NULL,
    display_name          VARCHAR(120) NOT NULL,
    is_control            BOOLEAN NOT NULL DEFAULT false,
    bucket_range          INT4RANGE NOT NULL,
    treatment_reference   VARCHAR(200),
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_experiment_variants_key UNIQUE (experiment_epoch_id, variant_key),
    CONSTRAINT fk_experiment_variants_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id) ON DELETE CASCADE,
    CONSTRAINT ck_experiment_variants_key_shape CHECK (variant_key ~ '^[a-z][a-z0-9_]{0,47}$'),
    -- Half-open bucket ranges, starting at zero, never empty. The hash lands in exactly one.
    CONSTRAINT ck_experiment_variants_range CHECK (
        NOT isempty(bucket_range)
            AND lower_inc(bucket_range)
            AND NOT upper_inc(bucket_range)
            AND lower(bucket_range) >= 0
    ),
    -- Two variants claiming the same bucket is not a configuration mistake to be found in review;
    -- it is a unit receiving two treatments, and the database refuses it.
    CONSTRAINT ex_experiment_variants_no_bucket_overlap EXCLUDE USING GIST (
        experiment_epoch_id WITH =,
        bucket_range WITH &&
    )
);

-- Exactly one control per epoch: an experiment with two controls has no baseline, and one with
-- none has nothing to compare against.
CREATE UNIQUE INDEX uk_experiment_variants_control
    ON experiment_variants (experiment_epoch_id)
    WHERE is_control;
--rollback DROP TABLE experiment_variants;

--changeset ninggiangboy:030-17-experiment-exclusions
CREATE TABLE experiment_exclusions (
    id                               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_epoch_id              UUID NOT NULL,
    related_experiment_definition_id UUID NOT NULL,
    relation_kind                    VARCHAR(24) NOT NULL,
    reason                           VARCHAR(500) NOT NULL,
    created_at                       TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_experiment_exclusions_pair
        UNIQUE (experiment_epoch_id, related_experiment_definition_id),
    CONSTRAINT fk_experiment_exclusions_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id) ON DELETE CASCADE,
    CONSTRAINT fk_experiment_exclusions_related FOREIGN KEY (related_experiment_definition_id)
        REFERENCES experiment_definitions (id),
    CONSTRAINT ck_experiment_exclusions_kind CHECK (
        relation_kind IN ('MUTUALLY_EXCLUSIVE', 'REQUIRED_CO_EXPERIMENT', 'KNOWN_INTERACTION')
    )
);

CREATE INDEX idx_experiment_exclusions_related
    ON experiment_exclusions (related_experiment_definition_id, relation_kind);
--rollback DROP TABLE experiment_exclusions;

--changeset ninggiangboy:030-18-experiment-metrics
CREATE TABLE experiment_metrics (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_epoch_id     UUID NOT NULL,
    metric_definition_id    UUID NOT NULL,
    metric_role             VARCHAR(16) NOT NULL,
    expected_direction      VARCHAR(16) NOT NULL,
    guardrail_threshold     NUMERIC(12,8),
    guardrail_breach_action VARCHAR(16),
    confirmatory            BOOLEAN NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_experiment_metrics_metric UNIQUE (experiment_epoch_id, metric_definition_id),
    CONSTRAINT fk_experiment_metrics_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id) ON DELETE CASCADE,
    CONSTRAINT fk_experiment_metrics_metric FOREIGN KEY (metric_definition_id)
        REFERENCES metric_definitions (id),
    CONSTRAINT ck_experiment_metrics_role CHECK (
        metric_role IN ('PRIMARY', 'SECONDARY', 'GUARDRAIL')
    ),
    CONSTRAINT ck_experiment_metrics_direction CHECK (
        expected_direction IN ('INCREASE', 'DECREASE', 'NO_CHANGE')
    ),
    -- A guardrail without a threshold and a named response is a hope, not a guardrail.
    CONSTRAINT ck_experiment_metrics_guardrail CHECK (
        (metric_role = 'GUARDRAIL')
            = (guardrail_threshold IS NOT NULL AND guardrail_breach_action IS NOT NULL)
    ),
    CONSTRAINT ck_experiment_metrics_breach_action CHECK (
        guardrail_breach_action IS NULL
            OR guardrail_breach_action IN ('ALERT', 'PAUSE', 'STOP')
    ),
    -- The primary metric is the one the decision rule is written against, so it is confirmatory by
    -- definition; declaring it exploratory after the fact is how a negative result becomes a
    -- "learning".
    CONSTRAINT ck_experiment_metrics_primary CHECK (metric_role <> 'PRIMARY' OR confirmatory)
);

CREATE INDEX idx_experiment_metrics_metric ON experiment_metrics (metric_definition_id);
CREATE INDEX idx_experiment_metrics_role ON experiment_metrics (experiment_epoch_id, metric_role);
--rollback DROP TABLE experiment_metrics;

--changeset ninggiangboy:030-19-experiment-assignments
CREATE TABLE experiment_assignments (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_epoch_id      UUID NOT NULL,
    experiment_variant_id    UUID NOT NULL,
    unit_kind                VARCHAR(24) NOT NULL,
    unit_pseudonym           VARCHAR(64) NOT NULL,
    bucket                   INTEGER NOT NULL,
    salt_version             VARCHAR(32) NOT NULL,
    allocator_version        VARCHAR(32) NOT NULL,
    eligibility_digest       CHAR(64) NOT NULL,
    eligibility_evaluated_at TIMESTAMPTZ NOT NULL,
    assignment_reason        VARCHAR(48) NOT NULL,
    market_code              VARCHAR(2),
    assigned_at              TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    -- One unit, one variant, for the life of the epoch. Two concurrent requests race here; the
    -- loser reads the winner's row rather than bucketing the unit a second time.
    CONSTRAINT uk_experiment_assignments_unit UNIQUE (experiment_epoch_id, unit_pseudonym),
    CONSTRAINT fk_experiment_assignments_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id),
    CONSTRAINT fk_experiment_assignments_variant FOREIGN KEY (experiment_variant_id)
        REFERENCES experiment_variants (id),
    CONSTRAINT ck_experiment_assignments_unit_kind CHECK (
        unit_kind IN ('USER', 'SESSION', 'LISTING', 'HOST', 'BOOKING', 'MARKET', 'TIME_SWITCHBACK')
    ),
    CONSTRAINT ck_experiment_assignments_pseudonym CHECK (
        unit_pseudonym ~ '^[0-9a-f]{32,64}$'
    ),
    CONSTRAINT ck_experiment_assignments_bucket CHECK (bucket >= 0),
    CONSTRAINT ck_experiment_assignments_digest CHECK (eligibility_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_experiment_assignments_market CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_experiment_assignments_eligibility_timing CHECK (
        assigned_at >= eligibility_evaluated_at
    )
);

CREATE INDEX idx_experiment_assignments_variant
    ON experiment_assignments (experiment_variant_id, assigned_at);
CREATE INDEX idx_experiment_assignments_unit_history
    ON experiment_assignments (unit_pseudonym, assigned_at);
--rollback DROP TABLE experiment_assignments;

--changeset ninggiangboy:030-20-experiment-exposures
CREATE TABLE experiment_exposures (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_assignment_id UUID NOT NULL,
    exposure_dedupe_key      VARCHAR(128) NOT NULL,
    surface                  VARCHAR(48) NOT NULL,
    actual_treatment         VARCHAR(48) NOT NULL,
    fallback_applied         BOOLEAN NOT NULL DEFAULT false,
    fallback_reason          VARCHAR(48),
    exposure_rule_key        VARCHAR(96) NOT NULL,
    exposure_rule_version    SMALLINT NOT NULL,
    evidence_source          VARCHAR(24) NOT NULL,
    event_arrival_id         UUID,
    request_id               VARCHAR(64),
    result_set_id            VARCHAR(64),
    decision_reference       VARCHAR(64),
    prediction_reference     VARCHAR(64),
    occurred_at              TIMESTAMPTZ NOT NULL,
    received_at              TIMESTAMPTZ NOT NULL,
    retention_class          VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    expires_at               TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    -- The declared dedupe key is what turns "count exposures" into one unambiguous number, whether
    -- the rule counts once per unit, once per request, or once per unit per day.
    CONSTRAINT uk_experiment_exposures_dedupe
        UNIQUE (experiment_assignment_id, exposure_dedupe_key),
    CONSTRAINT fk_experiment_exposures_assignment FOREIGN KEY (experiment_assignment_id)
        REFERENCES experiment_assignments (id),
    CONSTRAINT fk_experiment_exposures_arrival FOREIGN KEY (event_arrival_id)
        REFERENCES event_arrivals (id),
    CONSTRAINT ck_experiment_exposures_evidence CHECK (
        evidence_source IN ('SERVER', 'CLIENT_CONFIRMED')
    ),
    -- A treatment the model failed to deliver is still an assignment, but it is not a delivered
    -- treatment. Recording the fallback is what keeps a broken inference path from being measured
    -- as a successful one -- and from quietly inflating every estimate computed afterwards.
    CONSTRAINT ck_experiment_exposures_fallback CHECK (
        fallback_applied = (fallback_reason IS NOT NULL)
    ),
    -- Viewability is client evidence. Claiming a client-confirmed exposure with no accepted
    -- arrival behind it is claiming to have seen something nobody reported.
    CONSTRAINT ck_experiment_exposures_client_evidence CHECK (
        evidence_source <> 'CLIENT_CONFIRMED' OR event_arrival_id IS NOT NULL
    ),
    CONSTRAINT ck_experiment_exposures_receipt CHECK (received_at >= occurred_at),
    CONSTRAINT ck_experiment_exposures_expiry CHECK (expires_at > occurred_at),
    CONSTRAINT ck_experiment_exposures_retention CHECK (
        retention_class IN ('STANDARD', 'SHORT', 'LEGAL_HOLD')
    ),
    CONSTRAINT ck_experiment_exposures_rule_version CHECK (exposure_rule_version > 0)
);

CREATE INDEX idx_experiment_exposures_assignment_time
    ON experiment_exposures (experiment_assignment_id, occurred_at);
CREATE INDEX idx_experiment_exposures_surface ON experiment_exposures (surface, occurred_at);
CREATE INDEX idx_experiment_exposures_expiry ON experiment_exposures (expires_at);
--rollback DROP TABLE experiment_exposures;

--changeset ninggiangboy:030-21-experiment-analysis-runs
CREATE TABLE experiment_analysis_runs (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_epoch_id        UUID NOT NULL,
    analysis_plan_version      SMALLINT NOT NULL,
    look_number                INTEGER NOT NULL DEFAULT 1,
    data_cutoff_at             TIMESTAMPTZ NOT NULL,
    estimand                   VARCHAR(24) NOT NULL,
    interval_method            VARCHAR(24) NOT NULL,
    sequential_method          VARCHAR(24) NOT NULL,
    alpha_spent                NUMERIC(6,5),
    srm_status                 VARCHAR(16) NOT NULL,
    srm_p_value                NUMERIC(10,8),
    integrity_status           VARCHAR(8) NOT NULL,
    data_completeness          NUMERIC(5,4),
    assignment_count           BIGINT NOT NULL,
    exposure_count             BIGINT NOT NULL,
    novelty_period_end         TIMESTAMPTZ,
    code_digest                CHAR(64) NOT NULL,
    dataset_snapshot_reference VARCHAR(200) NOT NULL,
    conclusion                 VARCHAR(24),
    conclusion_rationale       VARCHAR(1000),
    analyzed_by                VARCHAR(64) NOT NULL,
    run_at                     TIMESTAMPTZ NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_experiment_analysis_runs_look
        UNIQUE (experiment_epoch_id, analysis_plan_version, look_number),
    CONSTRAINT fk_experiment_analysis_runs_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id),
    CONSTRAINT ck_experiment_analysis_runs_plan_version CHECK (analysis_plan_version > 0),
    CONSTRAINT ck_experiment_analysis_runs_look CHECK (look_number >= 1),
    CONSTRAINT ck_experiment_analysis_runs_estimand CHECK (
        estimand IN ('INTENT_TO_TREAT', 'TREATMENT_ON_TREATED')
    ),
    CONSTRAINT ck_experiment_analysis_runs_interval_method CHECK (
        interval_method IN ('NORMAL_APPROXIMATION', 'BOOTSTRAP', 'DELTA_METHOD', 'BAYESIAN')
    ),
    CONSTRAINT ck_experiment_analysis_runs_sequential CHECK (
        sequential_method IN ('NONE', 'ALPHA_SPENDING', 'MIXTURE_SPRT', 'GROUP_SEQUENTIAL')
    ),
    -- Looking twice at a running experiment and stopping on whichever look crossed the threshold is
    -- not sequential testing, it is peeking, and it makes the stated error rate a fiction. A second
    -- look has to name the method that pays for it.
    CONSTRAINT ck_experiment_analysis_runs_peeking CHECK (
        look_number = 1 OR sequential_method <> 'NONE'
    ),
    CONSTRAINT ck_experiment_analysis_runs_alpha_spent CHECK (
        alpha_spent IS NULL OR (alpha_spent > 0 AND alpha_spent < 1)
    ),
    CONSTRAINT ck_experiment_analysis_runs_srm CHECK (
        srm_status IN ('PASS', 'WARN', 'FAIL', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_experiment_analysis_runs_srm_p CHECK (
        srm_p_value IS NULL OR (srm_p_value >= 0 AND srm_p_value <= 1)
    ),
    CONSTRAINT ck_experiment_analysis_runs_integrity CHECK (
        integrity_status IN ('PASS', 'WARN', 'FAIL')
    ),
    CONSTRAINT ck_experiment_analysis_runs_completeness CHECK (
        data_completeness IS NULL OR (data_completeness >= 0 AND data_completeness <= 1)
    ),
    CONSTRAINT ck_experiment_analysis_runs_counts CHECK (
        assignment_count >= 0 AND exposure_count >= 0
    ),
    CONSTRAINT ck_experiment_analysis_runs_digest CHECK (code_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_experiment_analysis_runs_conclusion_vocabulary CHECK (
        conclusion IS NULL
            OR conclusion IN ('SHIP', 'DO_NOT_SHIP', 'INCONCLUSIVE', 'ITERATE', 'INVALID')
    ),
    CONSTRAINT ck_experiment_analysis_runs_conclusion_pair CHECK (
        (conclusion IS NULL) = (conclusion_rationale IS NULL)
    ),
    -- A sample-ratio mismatch means the randomisation did not do what it claimed, so every
    -- comparison downstream of it is suspect. Such a run may record that it is invalid; it may not
    -- record a ship decision.
    CONSTRAINT ck_experiment_analysis_runs_integrity_gate CHECK (
        conclusion IS NULL
            OR conclusion = 'INVALID'
            OR (srm_status IN ('PASS', 'NOT_APPLICABLE') AND integrity_status <> 'FAIL')
    )
);

CREATE INDEX idx_experiment_analysis_runs_epoch ON experiment_analysis_runs (experiment_epoch_id, run_at);
--rollback DROP TABLE experiment_analysis_runs;

--changeset ninggiangboy:030-22-experiment-analysis-estimates
CREATE TABLE experiment_analysis_estimates (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_analysis_run_id  UUID NOT NULL,
    experiment_metric_id        UUID NOT NULL,
    experiment_variant_id       UUID NOT NULL,
    comparison_variant_id       UUID NOT NULL,
    slice_key                   VARCHAR(96),
    estimate                    NUMERIC(24,10) NOT NULL,
    interval_low                NUMERIC(24,10) NOT NULL,
    interval_high               NUMERIC(24,10) NOT NULL,
    relative_effect             NUMERIC(24,10),
    p_value                     NUMERIC(10,8),
    posterior_probability       NUMERIC(10,8),
    treatment_sample_size       BIGINT NOT NULL,
    control_sample_size         BIGINT NOT NULL,
    confirmatory                BOOLEAN NOT NULL DEFAULT false,
    guardrail_breached          BOOLEAN NOT NULL DEFAULT false,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_experiment_analysis_estimates_scope
        UNIQUE NULLS NOT DISTINCT
        (experiment_analysis_run_id, experiment_metric_id, experiment_variant_id, slice_key),
    CONSTRAINT fk_experiment_analysis_estimates_run FOREIGN KEY (experiment_analysis_run_id)
        REFERENCES experiment_analysis_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_experiment_analysis_estimates_metric FOREIGN KEY (experiment_metric_id)
        REFERENCES experiment_metrics (id),
    CONSTRAINT fk_experiment_analysis_estimates_variant FOREIGN KEY (experiment_variant_id)
        REFERENCES experiment_variants (id),
    CONSTRAINT fk_experiment_analysis_estimates_comparison FOREIGN KEY (comparison_variant_id)
        REFERENCES experiment_variants (id),
    CONSTRAINT ck_experiment_analysis_estimates_comparison CHECK (
        experiment_variant_id <> comparison_variant_id
    ),
    -- A point estimate outside its own interval is arithmetic that went wrong somewhere upstream,
    -- and it is worth refusing at the boundary rather than explaining in a review.
    CONSTRAINT ck_experiment_analysis_estimates_interval CHECK (
        interval_low <= estimate AND estimate <= interval_high
    ),
    CONSTRAINT ck_experiment_analysis_estimates_samples CHECK (
        treatment_sample_size >= 0 AND control_sample_size >= 0
    ),
    CONSTRAINT ck_experiment_analysis_estimates_p_value CHECK (
        p_value IS NULL OR (p_value >= 0 AND p_value <= 1)
    ),
    CONSTRAINT ck_experiment_analysis_estimates_posterior CHECK (
        posterior_probability IS NULL
            OR (posterior_probability >= 0 AND posterior_probability <= 1)
    ),
    -- A frequentist reading and a Bayesian reading of the same number are different claims, and
    -- reporting both invites whichever one looks better to be quoted.
    CONSTRAINT ck_experiment_analysis_estimates_inference CHECK (
        p_value IS NULL OR posterior_probability IS NULL
    )
);

CREATE INDEX idx_experiment_analysis_estimates_metric
    ON experiment_analysis_estimates (experiment_metric_id);
CREATE INDEX idx_experiment_analysis_estimates_breaches
    ON experiment_analysis_estimates (experiment_analysis_run_id)
    WHERE guardrail_breached;
--rollback DROP TABLE experiment_analysis_estimates;

--changeset ninggiangboy:030-23-experiment-actions
CREATE TABLE experiment_actions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    experiment_definition_id UUID NOT NULL,
    experiment_epoch_id      UUID,
    action_type              VARCHAR(16) NOT NULL,
    command_key              VARCHAR(128) NOT NULL,
    expected_version         BIGINT NOT NULL,
    reason_code              VARCHAR(48) NOT NULL,
    reason_detail            VARCHAR(500) NOT NULL,
    actor_kind               VARCHAR(24) NOT NULL,
    actor_reference          VARCHAR(64) NOT NULL,
    approver_reference       VARCHAR(64),
    triggering_metric_id     UUID,
    applied                  BOOLEAN NOT NULL DEFAULT false,
    applied_at               TIMESTAMPTZ,
    failure_reason           VARCHAR(200),
    requested_at             TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_experiment_actions_command UNIQUE (experiment_definition_id, command_key),
    CONSTRAINT fk_experiment_actions_definition FOREIGN KEY (experiment_definition_id)
        REFERENCES experiment_definitions (id),
    CONSTRAINT fk_experiment_actions_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id),
    CONSTRAINT fk_experiment_actions_metric FOREIGN KEY (triggering_metric_id)
        REFERENCES experiment_metrics (id),
    CONSTRAINT ck_experiment_actions_type CHECK (
        action_type IN ('SCHEDULE', 'START', 'PAUSE', 'RESUME', 'STOP',
                        'ROLLOUT', 'ROLLBACK', 'ARCHIVE')
    ),
    CONSTRAINT ck_experiment_actions_actor_kind CHECK (
        actor_kind IN ('OPERATOR', 'GUARDRAIL_AUTOMATION')
    ),
    CONSTRAINT ck_experiment_actions_expected_version CHECK (expected_version >= 0),
    -- Shipping a treatment to everyone, or pulling it back, changes what every guest sees. One
    -- person may request it; a different person has to agree.
    CONSTRAINT ck_experiment_actions_approval CHECK (
        action_type NOT IN ('ROLLOUT', 'ROLLBACK')
            OR (approver_reference IS NOT NULL AND approver_reference <> actor_reference)
    ),
    -- Automation is allowed to stop a treatment that is hurting people, because waiting for a human
    -- costs more than a false pause. It is not allowed to roll one out, and when it acts it must
    -- name the guardrail that fired.
    CONSTRAINT ck_experiment_actions_automation CHECK (
        actor_kind <> 'GUARDRAIL_AUTOMATION'
            OR (action_type IN ('PAUSE', 'STOP') AND triggering_metric_id IS NOT NULL)
    ),
    CONSTRAINT ck_experiment_actions_applied CHECK (applied = (applied_at IS NOT NULL)),
    CONSTRAINT ck_experiment_actions_failure CHECK (NOT applied OR failure_reason IS NULL),
    -- Everything except archiving the experiment record itself acts on a particular epoch.
    CONSTRAINT ck_experiment_actions_scope CHECK (
        action_type = 'ARCHIVE' OR experiment_epoch_id IS NOT NULL
    )
);

CREATE INDEX idx_experiment_actions_definition
    ON experiment_actions (experiment_definition_id, requested_at);
CREATE INDEX idx_experiment_actions_pending ON experiment_actions (requested_at)
    WHERE NOT applied;
--rollback DROP TABLE experiment_actions;

--changeset ninggiangboy:030-24-platform-append-only splitStatements:false
-- Arrivals, assignments, exposures, run inputs, estimates and published numbers are evidence about
-- what happened. Editing one is not a fix; it is the destruction of the only record that could have
-- shown the error. Each trigger names the columns that may still move -- an ingestion stamp, a
-- retention decision, an owner's response -- and everything else is compared as JSON and refused
-- if it changed. DELETE stays available, because retention and erasure have to be able to remove
-- rows even though nobody may rewrite them.
CREATE FUNCTION platform_append_only() RETURNS TRIGGER AS $$
DECLARE
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    old_state := to_jsonb(OLD);
    new_state := to_jsonb(NEW);
    IF TG_NARGS > 0 THEN
        FOREACH permitted IN ARRAY TG_ARGV LOOP
            old_state := old_state - permitted;
            new_state := new_state - permitted;
        END LOOP;
    END IF;
    IF new_state IS DISTINCT FROM old_state THEN
        RAISE EXCEPTION
            '% row % is append-only; record a correction rather than editing what was observed',
            TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_event_arrivals_append_only
    BEFORE UPDATE ON event_arrivals
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only(
        'ingested_at', 'suppressed_at', 'retention_class', 'expires_at');

CREATE TRIGGER trg_data_corrections_append_only
    BEFORE UPDATE ON data_corrections
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('application_state', 'applied_at');

CREATE TRIGGER trg_pipeline_run_inputs_append_only
    BEFORE UPDATE ON pipeline_run_inputs
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_data_quality_results_append_only
    BEFORE UPDATE ON data_quality_results
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only(
        'owner_action', 'action_actor', 'action_reason', 'acted_at');

CREATE TRIGGER trg_metric_materializations_append_only
    BEFORE UPDATE ON metric_materializations
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('publication_state');

CREATE TRIGGER trg_experiment_assignments_append_only
    BEFORE UPDATE ON experiment_assignments
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_experiment_exposures_append_only
    BEFORE UPDATE ON experiment_exposures
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('retention_class', 'expires_at');

CREATE TRIGGER trg_experiment_analysis_estimates_append_only
    BEFORE UPDATE ON experiment_analysis_estimates
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_experiment_actions_append_only
    BEFORE UPDATE ON experiment_actions
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('applied', 'applied_at', 'failure_reason');
--rollback DROP TRIGGER trg_experiment_actions_append_only ON experiment_actions;
--rollback DROP TRIGGER trg_experiment_analysis_estimates_append_only ON experiment_analysis_estimates;
--rollback DROP TRIGGER trg_experiment_exposures_append_only ON experiment_exposures;
--rollback DROP TRIGGER trg_experiment_assignments_append_only ON experiment_assignments;
--rollback DROP TRIGGER trg_metric_materializations_append_only ON metric_materializations;
--rollback DROP TRIGGER trg_data_quality_results_append_only ON data_quality_results;
--rollback DROP TRIGGER trg_pipeline_run_inputs_append_only ON pipeline_run_inputs;
--rollback DROP TRIGGER trg_data_corrections_append_only ON data_corrections;
--rollback DROP TRIGGER trg_event_arrivals_append_only ON event_arrivals;
--rollback DROP FUNCTION platform_append_only();

--changeset ninggiangboy:030-25-contract-registry-immutability splitStatements:false
-- A contract that can be edited after consumers built on it is not a contract. Once an event
-- definition, a data product version, a metric version or a quality check leaves DRAFT, its
-- meaning is frozen: the lifecycle columns may move it forward through review, publication,
-- deprecation and retirement, and every other column is compared as JSON and refused. A semantic
-- change is a new version, which is exactly what makes the old numbers still readable.
CREATE FUNCTION platform_contract_freeze() RETURNS TRIGGER AS $$
DECLARE
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            '% row % is a published contract; retire it rather than deleting the definition that '
            'historical rows were produced under',
            TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    old_state := to_jsonb(OLD);
    new_state := to_jsonb(NEW);
    IF TG_NARGS > 0 THEN
        FOREACH permitted IN ARRAY TG_ARGV LOOP
            old_state := old_state - permitted;
            new_state := new_state - permitted;
        END LOOP;
    END IF;
    IF new_state IS DISTINCT FROM old_state THEN
        RAISE EXCEPTION
            '% row % left DRAFT and is frozen; publish a new version rather than changing what '
            'existing consumers already read',
            TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status = 'RETIRED' AND NEW.status <> 'RETIRED' THEN
        RAISE EXCEPTION
            '% row % was retired; register a new version rather than returning this one to service',
            TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_event_definitions_freeze
    BEFORE UPDATE OR DELETE ON event_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'reviewed_at', 'activated_at', 'deprecated_at', 'retired_at',
        'last_production_at', 'deletion_plan', 'replacement_definition_id',
        'expected_daily_volume', 'business_owner', 'technical_steward', 'updated_at', 'version');

CREATE TRIGGER trg_data_product_registry_freeze
    BEFORE UPDATE OR DELETE ON data_product_registry
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'reviewed_at', 'published_at', 'deprecated_at', 'retired_at',
        'business_owner', 'technical_steward', 'updated_at', 'version');

CREATE TRIGGER trg_metric_definitions_freeze
    BEFORE UPDATE OR DELETE ON metric_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'reviewed_at', 'published_at', 'deprecated_at', 'retired_at',
        'business_owner', 'technical_steward', 'intended_decisions', 'updated_at', 'version');

CREATE TRIGGER trg_data_quality_check_definitions_freeze
    BEFORE UPDATE OR DELETE ON data_quality_check_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze('status', 'owner', 'updated_at', 'version');

-- Retiring an event version while a consumer still reads it is how a pipeline breaks on a Tuesday
-- morning. The consumer list is a set of rows precisely so that this can be checked rather than
-- remembered.
CREATE FUNCTION event_definition_retirement_guard() RETURNS TRIGGER AS $$
DECLARE
    standing_consumer TEXT;
BEGIN
    SELECT consumer_name INTO standing_consumer
    FROM event_definition_consumers
    WHERE event_definition_id = NEW.id
      AND withdrawn_at IS NULL
    LIMIT 1;

    IF standing_consumer IS NOT NULL THEN
        RAISE EXCEPTION
            'event definition % still has consumer %; withdraw it before retiring the contract '
            'it reads',
            NEW.event_name, standing_consumer
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_event_definitions_retirement
    BEFORE UPDATE ON event_definitions
    FOR EACH ROW
    WHEN (NEW.status = 'RETIRED' AND OLD.status <> 'RETIRED')
    EXECUTE FUNCTION event_definition_retirement_guard();
--rollback DROP TRIGGER trg_event_definitions_retirement ON event_definitions;
--rollback DROP FUNCTION event_definition_retirement_guard();
--rollback DROP TRIGGER trg_data_quality_check_definitions_freeze ON data_quality_check_definitions;
--rollback DROP TRIGGER trg_metric_definitions_freeze ON metric_definitions;
--rollback DROP TRIGGER trg_data_product_registry_freeze ON data_product_registry;
--rollback DROP TRIGGER trg_event_definitions_freeze ON event_definitions;
--rollback DROP FUNCTION platform_contract_freeze();

--changeset ninggiangboy:030-26-epoch-sealing splitStatements:false
-- The moment the first unit is bucketed, the comparison exists and the allocation that produced it
-- is history. Re-salting, re-populating, widening a variant or changing the exposure rule after
-- that point does not adjust the experiment; it silently mixes two different experiments into one
-- result set that no analysis can separate afterwards. Material change means a new epoch, and the
-- cost of a new epoch is exactly the point.
CREATE FUNCTION experiment_epoch_seal() RETURNS TRIGGER AS $$
DECLARE
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    old_state := to_jsonb(OLD);
    new_state := to_jsonb(NEW);
    FOREACH permitted IN ARRAY ARRAY[
        'state', 'stopped_at', 'scheduled_stop_at', 'first_assignment_at', 'sealed',
        'updated_at', 'version'
    ] LOOP
        old_state := old_state - permitted;
        new_state := new_state - permitted;
    END LOOP;

    IF new_state IS DISTINCT FROM old_state THEN
        RAISE EXCEPTION
            'epoch % has assigned units since %; open a new epoch rather than changing the '
            'allocation that produced the comparison',
            OLD.id, OLD.first_assignment_at
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_experiment_epochs_seal
    BEFORE UPDATE ON experiment_epochs
    FOR EACH ROW
    WHEN (OLD.sealed)
    EXECUTE FUNCTION experiment_epoch_seal();

-- The variants, the declared metrics and the exclusions are all part of the design that the first
-- assignment froze, so they are sealed with the epoch -- and INSERT is covered as well as UPDATE
-- and DELETE. Adding a variant re-allocates every bucket it claims. Adding a primary metric after
-- the data is in is choosing the question once the answer is visible. Both are refused here rather
-- than argued about in a readout.
CREATE FUNCTION experiment_epoch_child_seal() RETURNS TRIGGER AS $$
DECLARE
    epoch_id UUID;
    sealed_at TIMESTAMPTZ;
BEGIN
    epoch_id := COALESCE(NEW.experiment_epoch_id, OLD.experiment_epoch_id);

    SELECT first_assignment_at INTO sealed_at
    FROM experiment_epochs
    WHERE id = epoch_id;

    IF sealed_at IS NOT NULL THEN
        RAISE EXCEPTION
            'epoch % has assigned units since %; its % are part of the design that was frozen and '
            'a change requires a new epoch',
            epoch_id, sealed_at, TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_experiment_variants_seal
    BEFORE INSERT OR UPDATE OR DELETE ON experiment_variants
    FOR EACH ROW
    EXECUTE FUNCTION experiment_epoch_child_seal();

CREATE TRIGGER trg_experiment_metrics_seal
    BEFORE INSERT OR UPDATE OR DELETE ON experiment_metrics
    FOR EACH ROW
    EXECUTE FUNCTION experiment_epoch_child_seal();

CREATE TRIGGER trg_experiment_exclusions_seal
    BEFORE INSERT OR UPDATE OR DELETE ON experiment_exclusions
    FOR EACH ROW
    EXECUTE FUNCTION experiment_epoch_child_seal();
--rollback DROP TRIGGER trg_experiment_exclusions_seal ON experiment_exclusions;
--rollback DROP TRIGGER trg_experiment_metrics_seal ON experiment_metrics;
--rollback DROP TRIGGER trg_experiment_variants_seal ON experiment_variants;
--rollback DROP FUNCTION experiment_epoch_child_seal();
--rollback DROP TRIGGER trg_experiment_epochs_seal ON experiment_epochs;
--rollback DROP FUNCTION experiment_epoch_seal();

--changeset ninggiangboy:030-27-epoch-activation-requirements splitStatements:false
-- Two rules that are easy to state in a design document and easy to forget under deadline.
--
-- First: a host's price is the host's to set, and the price a guest is shown is a disclosure, not a
-- treatment arm. Randomising a public price or a host pricing tool by guest breaks host control,
-- price consistency and the fairness of what two people are quoted for the same night. Those
-- namespaces may randomise by listing, host or market -- never by the person looking.
--
-- Second: an experiment that measures only what it hopes to improve will find it. Leaving DRAFT
-- requires at least one primary metric and at least one guardrail, so that a conversion gain bought
-- with cancellations, incidents or host earnings is visible in the same analysis that claims it.
CREATE FUNCTION experiment_epoch_activation() RETURNS TRIGGER AS $$
DECLARE
    experiment_namespace TEXT;
    primary_count INTEGER;
    guardrail_count INTEGER;
    control_count INTEGER;
    widest INTEGER;
BEGIN
    SELECT namespace INTO experiment_namespace
    FROM experiment_definitions
    WHERE id = NEW.experiment_definition_id;

    IF experiment_namespace IN ('PUBLIC_PRICE', 'HOST_PRICING_TOOLS')
            AND NEW.assignment_unit IN ('USER', 'SESSION') THEN
        RAISE EXCEPTION
            'namespace % may not be randomised by %; a price is the host''s setting and the same '
            'listing may not quote two guests differently to measure an effect',
            experiment_namespace, NEW.assignment_unit
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- An epoch's variants and metrics reference the epoch, so they cannot exist at the moment it
    -- is inserted. An epoch that is created already running would therefore skip every check below
    -- -- which is why it is refused outright rather than waved through as a special case.
    IF TG_OP = 'INSERT' AND NEW.state <> 'DRAFT' THEN
        RAISE EXCEPTION
            'an epoch is created in draft and approved once its variants, its primary metric and '
            'its guardrails exist; it may not be inserted already %', NEW.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.state <> 'DRAFT' AND OLD.state = 'DRAFT' THEN
        SELECT count(*) FILTER (WHERE metric_role = 'PRIMARY'),
               count(*) FILTER (WHERE metric_role = 'GUARDRAIL')
          INTO primary_count, guardrail_count
        FROM experiment_metrics
        WHERE experiment_epoch_id = NEW.id;

        IF primary_count = 0 THEN
            RAISE EXCEPTION
                'epoch % has no primary metric; the decision rule has nothing to be evaluated '
                'against', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF guardrail_count = 0 THEN
            RAISE EXCEPTION
                'epoch % has no guardrail metric; a gain that costs cancellations, incidents or '
                'host earnings would not be visible in its own analysis', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        SELECT count(*) FILTER (WHERE is_control), max(upper(bucket_range))
          INTO control_count, widest
        FROM experiment_variants
        WHERE experiment_epoch_id = NEW.id;

        IF control_count <> 1 THEN
            RAISE EXCEPTION
                'epoch % must have exactly one control variant before it leaves draft', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF widest > NEW.bucket_count THEN
            RAISE EXCEPTION
                'epoch % allocates buckets up to % but hashes into only %; the units above the '
                'ceiling would never be assigned',
                NEW.id, widest, NEW.bucket_count
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_experiment_epochs_activation
    BEFORE INSERT OR UPDATE ON experiment_epochs
    FOR EACH ROW
    EXECUTE FUNCTION experiment_epoch_activation();
--rollback DROP TRIGGER trg_experiment_epochs_activation ON experiment_epochs;
--rollback DROP FUNCTION experiment_epoch_activation();

--changeset ninggiangboy:030-28-assignment-integrity splitStatements:false
-- An assignment is the claim that this unit belongs in this arm, and every part of that claim is
-- checkable: the epoch is actually running, the variant belongs to it, the bucket really falls in
-- the variant's range under the salt and allocator the epoch declared, and the unit is the kind of
-- thing the epoch randomises. A bucket written by hand into the wrong arm is indistinguishable
-- from a hashing bug six weeks later.
--
-- Two further refusals matter more than the arithmetic. A subject whose links have been suppressed
-- is not enrolled in anything new -- an erasure that leaves the next assignment job free to bucket
-- the same person again is not an erasure. And a unit already carrying an incompatible treatment
-- is refused rather than quietly counted, because an accidental overlap analysed as if it were a
-- factorial design is two experiments reporting each other's effects.
CREATE FUNCTION experiment_assignment_integrity() RETURNS TRIGGER AS $$
DECLARE
    epoch experiment_epochs%ROWTYPE;
    variant experiment_variants%ROWTYPE;
    definition_id UUID;
    experiment_namespace TEXT;
    conflicting_key TEXT;
BEGIN
    SELECT * INTO epoch FROM experiment_epochs WHERE id = NEW.experiment_epoch_id;
    SELECT * INTO variant FROM experiment_variants WHERE id = NEW.experiment_variant_id;

    IF variant.experiment_epoch_id <> epoch.id THEN
        RAISE EXCEPTION
            'variant % belongs to epoch %, not %',
            variant.id, variant.experiment_epoch_id, epoch.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF epoch.state <> 'RUNNING' THEN
        RAISE EXCEPTION
            'epoch % is %; a unit may only be bucketed while the epoch is running',
            epoch.id, epoch.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.unit_kind <> epoch.assignment_unit THEN
        RAISE EXCEPTION
            'epoch % randomises by % but the assignment names a %',
            epoch.id, epoch.assignment_unit, NEW.unit_kind
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.salt_version <> epoch.salt_version
            OR NEW.allocator_version <> epoch.allocator_version THEN
        RAISE EXCEPTION
            'assignment claims salt %/allocator % but epoch % declared %/%; the bucket would not '
            'be reproducible',
            NEW.salt_version, NEW.allocator_version, epoch.id,
            epoch.salt_version, epoch.allocator_version
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.bucket >= epoch.bucket_count THEN
        RAISE EXCEPTION
            'bucket % is outside the % buckets epoch % hashes into',
            NEW.bucket, epoch.bucket_count, epoch.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT (variant.bucket_range @> NEW.bucket) THEN
        RAISE EXCEPTION
            'bucket % does not fall in variant %, which holds %',
            NEW.bucket, variant.variant_key, variant.bucket_range
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF EXISTS (
        SELECT 1 FROM privacy_subject_links
        WHERE state = 'SUPPRESSED'
          AND (source_pseudonym = NEW.unit_pseudonym OR target_pseudonym = NEW.unit_pseudonym)
    ) THEN
        RAISE EXCEPTION
            'subject % is suppressed; a unit that asked to be forgotten is not enrolled in a new '
            'experiment by the next batch that runs',
            NEW.unit_pseudonym
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT experiment_definition_id INTO definition_id
    FROM experiment_epochs WHERE id = NEW.experiment_epoch_id;

    SELECT namespace INTO experiment_namespace
    FROM experiment_definitions WHERE id = definition_id;

    SELECT other_definition.experiment_key INTO conflicting_key
    FROM experiment_assignments existing
        JOIN experiment_epochs other_epoch ON other_epoch.id = existing.experiment_epoch_id
        JOIN experiment_definitions other_definition
            ON other_definition.id = other_epoch.experiment_definition_id
    WHERE existing.unit_pseudonym = NEW.unit_pseudonym
      AND other_epoch.experiment_definition_id <> definition_id
      AND other_epoch.state IN ('RUNNING', 'PAUSED')
      AND (
          (other_definition.namespace = experiment_namespace
               AND other_definition.exclusive_in_namespace
               AND EXISTS (SELECT 1 FROM experiment_definitions self_definition
                           WHERE self_definition.id = definition_id
                             AND self_definition.exclusive_in_namespace))
          OR EXISTS (
              SELECT 1 FROM experiment_exclusions exclusion
              WHERE exclusion.relation_kind = 'MUTUALLY_EXCLUSIVE'
                AND ((exclusion.experiment_epoch_id = NEW.experiment_epoch_id
                          AND exclusion.related_experiment_definition_id
                              = other_epoch.experiment_definition_id)
                     OR (exclusion.experiment_epoch_id = other_epoch.id
                          AND exclusion.related_experiment_definition_id = definition_id))
          )
      )
    LIMIT 1;

    IF conflicting_key IS NOT NULL THEN
        RAISE EXCEPTION
            'unit % already carries an incompatible treatment from %; an accidental overlap is not '
            'a factorial design',
            NEW.unit_pseudonym, conflicting_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_experiment_assignments_integrity
    BEFORE INSERT ON experiment_assignments
    FOR EACH ROW
    EXECUTE FUNCTION experiment_assignment_integrity();
--rollback DROP TRIGGER trg_experiment_assignments_integrity ON experiment_assignments;
--rollback DROP FUNCTION experiment_assignment_integrity();

--changeset ninggiangboy:030-29-exposure-integrity splitStatements:false
-- Exposure is the claim that the treatment could actually have reached this unit, and it is the
-- denominator of everything measured on the treated. So it is checked against the assignment it
-- names: it cannot happen before the unit was bucketed, it cannot happen before the epoch started,
-- and it must cite the exposure rule version the epoch declared -- otherwise "exposed" silently
-- means two different things across one experiment's lifetime.
--
-- The last check is the one that protects the estimates. If no fallback is declared, the treatment
-- recorded as delivered must be the variant the unit was assigned. A model call that timed out and
-- quietly served control behaviour, logged as a successful treatment, dilutes the treated group
-- with untreated units and biases every effect toward zero -- while the dashboard reports full
-- delivery.
CREATE FUNCTION experiment_exposure_integrity() RETURNS TRIGGER AS $$
DECLARE
    assignment experiment_assignments%ROWTYPE;
    epoch experiment_epochs%ROWTYPE;
    variant experiment_variants%ROWTYPE;
BEGIN
    SELECT * INTO assignment FROM experiment_assignments WHERE id = NEW.experiment_assignment_id;
    SELECT * INTO epoch FROM experiment_epochs WHERE id = assignment.experiment_epoch_id;
    SELECT * INTO variant FROM experiment_variants WHERE id = assignment.experiment_variant_id;

    IF NEW.occurred_at < assignment.assigned_at THEN
        RAISE EXCEPTION
            'exposure at % precedes the assignment it cites, made at %; the unit could not have '
            'been reached by a treatment it had not yet been given',
            NEW.occurred_at, assignment.assigned_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF epoch.started_at IS NOT NULL AND NEW.occurred_at < epoch.started_at THEN
        RAISE EXCEPTION
            'exposure at % precedes the start of epoch % at %',
            NEW.occurred_at, epoch.id, epoch.started_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.exposure_rule_key <> epoch.exposure_rule_key
            OR NEW.exposure_rule_version <> epoch.exposure_rule_version THEN
        RAISE EXCEPTION
            'exposure cites rule %/% but epoch % declared %/%; two definitions of "exposed" in one '
            'experiment cannot be analysed together',
            NEW.exposure_rule_key, NEW.exposure_rule_version, epoch.id,
            epoch.exposure_rule_key, epoch.exposure_rule_version
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT NEW.fallback_applied AND NEW.actual_treatment <> variant.variant_key THEN
        RAISE EXCEPTION
            'exposure reports treatment % for a unit assigned to %; a delivery that differed from '
            'the assignment must declare the fallback that caused it',
            NEW.actual_treatment, variant.variant_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_experiment_exposures_integrity
    BEFORE INSERT ON experiment_exposures
    FOR EACH ROW
    EXECUTE FUNCTION experiment_exposure_integrity();
--rollback DROP TRIGGER trg_experiment_exposures_integrity ON experiment_exposures;
--rollback DROP FUNCTION experiment_exposure_integrity();

--changeset ninggiangboy:030-30-quality-honesty splitStatements:false
-- Data quality fails quietly. A check goes red, the run finishes anyway, the dataset publishes, and
-- three dashboards and a model carry on as though nothing happened -- because nothing in the system
-- ever had to reconcile "this check failed" with "this run passed". These triggers make that
-- contradiction impossible to store.
--
-- A run cannot report PASS while a failing check stands against it, and the failing check cannot be
-- recorded against a run already claiming PASS. The way out is not to hide the result but to waive
-- it, with a named owner and a reason, which is a decision somebody can be asked about later.
CREATE FUNCTION pipeline_run_quality_honesty() RETURNS TRIGGER AS $$
DECLARE
    run_id UUID;
    failing_check TEXT;
    claimed_quality TEXT;
BEGIN
    IF TG_TABLE_NAME = 'data_quality_results' THEN
        run_id := NEW.pipeline_run_id;
        IF NEW.status <> 'FAIL' OR NEW.owner_action = 'WAIVED' THEN
            RETURN NEW;
        END IF;
        SELECT quality_state INTO claimed_quality FROM pipeline_runs WHERE id = run_id;
        IF claimed_quality = 'PASS' THEN
            RAISE EXCEPTION
                'run % already reports PASS; downgrade its quality state before filing a failing '
                'check against it, so that no moment exists in which both statements stand', run_id
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    SELECT definition.check_key INTO failing_check
    FROM data_quality_results result
        JOIN data_quality_check_definitions definition
            ON definition.id = result.data_quality_check_id
    WHERE result.pipeline_run_id = NEW.id
      AND result.status = 'FAIL'
      AND result.owner_action <> 'WAIVED'
    LIMIT 1;

    IF failing_check IS NOT NULL THEN
        RAISE EXCEPTION
            'run % cannot report PASS while check % is failing; downgrade the run, waive the check '
            'with an owner and a reason, or fix the data',
            NEW.id, failing_check
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_data_quality_results_honesty
    BEFORE INSERT OR UPDATE ON data_quality_results
    FOR EACH ROW
    EXECUTE FUNCTION pipeline_run_quality_honesty();

CREATE TRIGGER trg_pipeline_runs_quality_honesty
    BEFORE UPDATE ON pipeline_runs
    FOR EACH ROW
    WHEN (NEW.quality_state = 'PASS' AND OLD.quality_state IS DISTINCT FROM 'PASS')
    EXECUTE FUNCTION pipeline_run_quality_honesty();

-- A published number inherits the standing of the run that produced it. It cannot come from a run
-- that did not succeed, it cannot come from a run over a different dataset than the metric declares
-- as its source, and it cannot claim a cleaner quality state than the run it came out of. The last
-- one is the quiet failure: a WARN run producing a PASS number is how a known-degraded input ends
-- up quoted as fact in a decision meeting.
CREATE FUNCTION metric_materialization_provenance() RETURNS TRIGGER AS $$
DECLARE
    run pipeline_runs%ROWTYPE;
    metric metric_definitions%ROWTYPE;
    run_rank INTEGER;
    value_rank INTEGER;
BEGIN
    SELECT * INTO run FROM pipeline_runs WHERE id = NEW.pipeline_run_id;
    SELECT * INTO metric FROM metric_definitions WHERE id = NEW.metric_definition_id;

    IF run.state <> 'SUCCEEDED' THEN
        RAISE EXCEPTION
            'run % is %; a number may not be published from a run that did not finish successfully',
            run.id, run.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF run.data_product_id <> metric.source_data_product_id THEN
        RAISE EXCEPTION
            'metric % declares its source dataset but the run produced a different one; lineage '
            'would not resolve',
            metric.metric_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    run_rank := CASE run.quality_state
        WHEN 'PASS' THEN 3 WHEN 'WARN' THEN 2 WHEN 'UNKNOWN' THEN 1 ELSE 0 END;
    value_rank := CASE NEW.quality_state
        WHEN 'PASS' THEN 3 WHEN 'WARN' THEN 2 WHEN 'UNKNOWN' THEN 1 ELSE 0 END;

    IF value_rank > run_rank THEN
        RAISE EXCEPTION
            'materialization claims quality % from a run that reported %; a number cannot be '
            'cleaner than the run that computed it',
            NEW.quality_state, run.quality_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.publication_state = 'CURRENT' THEN
        IF metric.status NOT IN ('CURRENT', 'DEPRECATED') THEN
            RAISE EXCEPTION
                'metric % is %; a draft or retired definition does not publish a current number',
                metric.metric_key, metric.status
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF metric.minimum_quality_state = 'PASS' AND NEW.quality_state <> 'PASS' THEN
            RAISE EXCEPTION
                'metric % requires PASS-grade inputs but the number is %',
                metric.metric_key, NEW.quality_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_metric_materializations_provenance
    BEFORE INSERT OR UPDATE ON metric_materializations
    FOR EACH ROW
    EXECUTE FUNCTION metric_materialization_provenance();
--rollback DROP TRIGGER trg_metric_materializations_provenance ON metric_materializations;
--rollback DROP FUNCTION metric_materialization_provenance();
--rollback DROP TRIGGER trg_pipeline_runs_quality_honesty ON pipeline_runs;
--rollback DROP TRIGGER trg_data_quality_results_honesty ON data_quality_results;
--rollback DROP FUNCTION pipeline_run_quality_honesty();

--changeset ninggiangboy:030-31-classification-propagation splitStatements:false
-- The most restrictive classification follows the data. A conformed product built from personal
-- arrivals is personal, and a serving product built from it is personal too, unless somebody with
-- the authority to say so has approved a transformation that genuinely removes the exposure. What
-- this refuses is the common, unremarkable version of the mistake: a team declares its new
-- aggregate NON_PERSONAL because it feels aggregate, and the training permission that follows from
-- that classification is granted by nobody in particular.
CREATE FUNCTION data_product_classification_propagation() RETURNS TRIGGER AS $$
DECLARE
    downstream_class TEXT;
    upstream_class TEXT;
    downstream_key TEXT;
    upstream_key TEXT;
    downstream_rank INTEGER;
    upstream_rank INTEGER;
BEGIN
    SELECT privacy_class, dataset_key INTO downstream_class, downstream_key
    FROM data_product_registry WHERE id = NEW.data_product_id;

    SELECT privacy_class, dataset_key INTO upstream_class, upstream_key
    FROM data_product_registry WHERE id = NEW.upstream_data_product_id;

    downstream_rank := CASE downstream_class
        WHEN 'RESTRICTED' THEN 3 WHEN 'PERSONAL' THEN 2 WHEN 'PSEUDONYMOUS' THEN 1 ELSE 0 END;
    upstream_rank := CASE upstream_class
        WHEN 'RESTRICTED' THEN 3 WHEN 'PERSONAL' THEN 2 WHEN 'PSEUDONYMOUS' THEN 1 ELSE 0 END;

    IF downstream_rank < upstream_rank AND NEW.declassification_approval IS NULL THEN
        RAISE EXCEPTION
            'dataset % is % but reads from % which is %; a looser classification downstream needs '
            'an approved transformation, not a new row',
            downstream_key, downstream_class, upstream_key, upstream_class
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_data_product_dependencies_classification
    BEFORE INSERT OR UPDATE ON data_product_dependencies
    FOR EACH ROW
    EXECUTE FUNCTION data_product_classification_propagation();
--rollback DROP TRIGGER trg_data_product_dependencies_classification ON data_product_dependencies;
--rollback DROP FUNCTION data_product_classification_propagation();

--changeset ninggiangboy:030-32-suppression-is-terminal splitStatements:false
-- Suppression is the analytical half of an erasure request, and an erasure that can be undone by
-- an UPDATE is not one. Once a link is suppressed, it stays suppressed: the stamp and the reason
-- cannot be cleared, and the state cannot return to ACTIVE. Re-linking the same subject means a new
-- row with a new consent and a new effective interval, which leaves the original request visible
-- as the fact it is.
CREATE FUNCTION privacy_subject_link_terminal() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.state <> 'SUPPRESSED' THEN
        RAISE EXCEPTION
            'link % was suppressed at % because %; record a new link rather than reviving the one '
            'the subject asked to have removed',
            OLD.id, OLD.suppressed_at, OLD.suppression_reason
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.suppressed_at IS DISTINCT FROM OLD.suppressed_at
            OR NEW.suppression_reason IS DISTINCT FROM OLD.suppression_reason THEN
        RAISE EXCEPTION
            'link % was suppressed at %; that is a fact about a request that was made and it '
            'cannot be rewritten',
            OLD.id, OLD.suppressed_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_privacy_subject_links_terminal
    BEFORE UPDATE ON privacy_subject_links
    FOR EACH ROW
    WHEN (OLD.state = 'SUPPRESSED')
    EXECUTE FUNCTION privacy_subject_link_terminal();
--rollback DROP TRIGGER trg_privacy_subject_links_terminal ON privacy_subject_links;
--rollback DROP FUNCTION privacy_subject_link_terminal();

--changeset ninggiangboy:030-33-arrival-contract-state splitStatements:false
-- An arrival that names a registered contract has to agree with it. The name, the schema version
-- and the class come from the definition, not from whatever the producer happened to put in the
-- envelope, and a retired contract accepts nothing further -- its consumers are gone and its
-- retention was already planned. An arrival for a contract nobody registered is not refused, it is
-- quarantined: that is evidence of an unregistered producer, and losing it would hide exactly the
-- thing worth finding.
CREATE FUNCTION event_arrival_contract_state() RETURNS TRIGGER AS $$
DECLARE
    definition event_definitions%ROWTYPE;
BEGIN
    IF NEW.event_definition_id IS NULL THEN
        IF NEW.validation_state = 'ACCEPTED' THEN
            RAISE EXCEPTION
                'arrival for %/% names no registered contract and cannot be accepted; quarantine '
                'it so the unregistered producer is visible',
                NEW.event_name, NEW.schema_version
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO definition FROM event_definitions WHERE id = NEW.event_definition_id;

    IF definition.event_name <> NEW.event_name
            OR definition.schema_version <> NEW.schema_version
            OR definition.event_class <> NEW.event_class THEN
        RAISE EXCEPTION
            'arrival claims %/% as % but the contract it cites is %/% as %',
            NEW.event_name, NEW.schema_version, NEW.event_class,
            definition.event_name, definition.schema_version, definition.event_class
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.validation_state = 'ACCEPTED'
            AND definition.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
        RAISE EXCEPTION
            'contract %/% is %; an arrival against it may be quarantined but not accepted',
            definition.event_name, definition.schema_version, definition.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_event_arrivals_contract_state
    BEFORE INSERT ON event_arrivals
    FOR EACH ROW
    EXECUTE FUNCTION event_arrival_contract_state();
--rollback DROP TRIGGER trg_event_arrivals_contract_state ON event_arrivals;
--rollback DROP FUNCTION event_arrival_contract_state();
