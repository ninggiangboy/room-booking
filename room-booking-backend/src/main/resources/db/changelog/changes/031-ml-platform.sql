--liquibase formatted sql

-- A model advises; it never decides. Migration 030 established that a contract, an arrival, a run
-- and a number are different records. This one says the same thing one layer up: a feature, a
-- label, a training set, a registered model, a prediction and a decision are six different records,
-- and the reason to keep them apart is that collapsing any two of them produces a system that
-- cannot answer the only question that matters after something goes wrong -- what did this model
-- actually see, and who agreed it could act on it.
--
-- Nothing here decides anything. A prediction row is evidence that a model was asked and what it
-- answered; the action taken belongs to the domain that took it. decision_references is an index
-- for monitoring, not an authority: it can say that a booking decision cited prediction X under
-- policy version Y, and it can say that the policy overrode the recommendation, but it cannot
-- change either. As in 030, no table here carries a foreign key into account_holders, listings,
-- bookings, payments or reviews -- an inference write must never take a lock on a row a guest is
-- trying to book.
--
-- Eight forces shape it:
--
--   Point-in-time correctness is a column, not a convention. An offline feature value records when
--   its source occurred, when that source became knowable, and the interval over which the value
--   is effective -- and a value may not become effective before it could have been known. That one
--   constraint is what stops a training set learning from the future, which is the failure that
--   produces a model with excellent offline metrics and no online effect whatsoever.
--
--   Not observed is not negative. A label observation is positive, negative, unresolved, censored
--   or excluded, and the last three carry no value at all. An unrendered listing is not a
--   rejection, an unreviewed case is not a safe one, and a stay that has not finished has no
--   satisfaction. A resolved label may not be dated before its own horizon has ended.
--
--   An outcome that was only observed because an earlier system selected it must say so. Fraud,
--   moderation, support and ranking outcomes are visible only for the cases some previous model or
--   rule chose to act on. A label definition that admits selection bias forces every observation
--   under it to name the selecting policy, so that the bias is a column a dataset builder has to
--   handle rather than a footnote nobody read.
--
--   A training set is a claim about a moment, and it either gave every label its full horizon or
--   it says it modelled censoring. Random splits are refused for time-dependent behaviour, because
--   the same booking, host or near-duplicate listing on both sides of a split is the cheapest way
--   to produce a number that means nothing.
--
--   A registered model version is immutable from the moment it is trained. Changing weights,
--   prompt configuration or the artifact is a new version -- not because bytes are sacred, but
--   because every prediction, evaluation and decision downstream names a version, and a mutable
--   version silently reassigns their meaning.
--
--   Approval is separation of duties, enforced. A model owner cannot self-approve. A model whose
--   impact is safety, financial, pricing, eligibility or moderation cannot reach APPROVED without
--   the domain, the risk and privacy reviews, and the evidence rows to point at -- including a
--   fairness evaluation with slices and a baseline it was actually compared against.
--
--   Routing is where a model becomes live, and routing is the only thing a rollback changes.
--   Immutable history is never rolled back. One active route exists per consumer, decision scope
--   and market; a champion/challenger split has to name the experiment epoch it is measured under,
--   because an unmeasured traffic split is not a comparison, it is a gamble with a percentage sign.
--
--   A prediction is bounded, expiring evidence, not an attribute of a person. Its output is size
--   capped so that a raw feature dump cannot be logged under the name of a score, it carries an
--   explicit uncertainty state, and a failed inference is recorded as the fallback it was rather
--   than quietly omitted -- evaluating only the predictions that succeeded hides exactly the
--   outage and fallback harm the evaluation exists to find.
--
-- Note on what this migration does not create. The event, ingestion, metric and experiment records
-- from the same feature document are migration 030 and are referenced here, not redefined: a
-- champion/challenger route names an experiment_epoch, a feature value names the pipeline_run that
-- produced it, and a feature or label definition names the data_product_registry entry it reads.
-- The trigger functions platform_append_only() and platform_contract_freeze() also come from 030
-- and are reused here rather than duplicated. Object storage for artifacts, datasets and embedding
-- vectors, model hosting, and the serving runtime itself are operational choices no table holds;
-- what is stored here is the manifest, the checksum, the lifecycle and the access policy.
--
-- Every table here is written by the application, a pipeline worker or a serving process, so none
-- of them carry DEFAULT now(): see migration 011.

--changeset ninggiangboy:031-01-feature-definitions
CREATE TABLE feature_definitions (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_key                VARCHAR(120) NOT NULL,
    semantic_version           SMALLINT NOT NULL,
    display_name               VARCHAR(200) NOT NULL,
    entity_kind                VARCHAR(24) NOT NULL,
    context_keys               VARCHAR(200),
    value_type                 VARCHAR(16) NOT NULL,
    allowed_range              VARCHAR(120),
    allowed_categories         VARCHAR(500),
    missing_representation     VARCHAR(24) NOT NULL,
    default_policy             VARCHAR(24) NOT NULL DEFAULT 'NONE',
    default_value              VARCHAR(120),
    computation_kind           VARCHAR(24) NOT NULL,
    computation_specification  VARCHAR(1000) NOT NULL,
    computation_digest         CHAR(64) NOT NULL,
    materialization_mode       VARCHAR(24) NOT NULL,
    source_data_product_id     UUID NOT NULL,
    event_time_basis           VARCHAR(24) NOT NULL,
    window_days                INTEGER,
    lookback_days              INTEGER,
    freshness_target_minutes   INTEGER NOT NULL,
    time_to_live_minutes       INTEGER,
    available_offline          BOOLEAN NOT NULL DEFAULT true,
    available_batch_serving    BOOLEAN NOT NULL DEFAULT false,
    available_online_serving   BOOLEAN NOT NULL DEFAULT false,
    parity_test_reference      VARCHAR(200),
    reproducible_offline       BOOLEAN NOT NULL DEFAULT true,
    consequential_use_allowed  BOOLEAN NOT NULL DEFAULT false,
    validation_test_reference  VARCHAR(200),
    skew_threshold             NUMERIC(7,6),
    privacy_class              VARCHAR(16) NOT NULL,
    sensitive_attribute        BOOLEAN NOT NULL DEFAULT false,
    sensitive_attribute_basis  VARCHAR(48),
    sensitive_review_reference VARCHAR(200),
    purpose                    VARCHAR(48) NOT NULL,
    prohibited_consumers       VARCHAR(300),
    training_eligible          BOOLEAN NOT NULL DEFAULT false,
    retention_days             INTEGER NOT NULL,
    deletion_behaviour         VARCHAR(24) NOT NULL,
    business_owner             VARCHAR(64) NOT NULL,
    technical_steward          VARCHAR(64) NOT NULL,
    replaced_by_id             UUID,
    supersedes_id              UUID,
    status                     VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    reviewed_at                TIMESTAMPTZ,
    activated_at               TIMESTAMPTZ,
    deprecated_at              TIMESTAMPTZ,
    retired_at                 TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_feature_definitions_key UNIQUE (feature_key, semantic_version),
    CONSTRAINT fk_feature_definitions_source FOREIGN KEY (source_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT fk_feature_definitions_replaced_by FOREIGN KEY (replaced_by_id)
        REFERENCES feature_definitions (id),
    CONSTRAINT fk_feature_definitions_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES feature_definitions (id),
    -- "user_score_7" is a name that survives exactly as long as the person who wrote it. The
    -- pattern forces meaning and semantic version into the key, and the window rule below forces a
    -- windowed feature to carry its own window, so that guest_confirmed_bookings_count_365d_v1 can
    -- never be silently swapped for the 30-day one in a feature set nobody re-read.
    CONSTRAINT ck_feature_definitions_key CHECK (
        feature_key ~ '^[a-z][a-z0-9_]*_v[0-9]{1,3}$'
    ),
    CONSTRAINT ck_feature_definitions_key_window CHECK (
        window_days IS NULL OR feature_key ~ ('_' || window_days || 'd_v')
    ),
    CONSTRAINT ck_feature_definitions_semantic_version CHECK (semantic_version >= 1),
    CONSTRAINT ck_feature_definitions_supersession CHECK (
        semantic_version = 1 OR supersedes_id IS NOT NULL
    ),
    CONSTRAINT ck_feature_definitions_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    CONSTRAINT ck_feature_definitions_value_type CHECK (
        value_type IN ('BOOLEAN', 'INTEGER', 'DECIMAL', 'CATEGORICAL', 'EMBEDDING', 'TIMESTAMP')
    ),
    -- A categorical feature without its category list is an open string column, and an open string
    -- column is how an unreviewed value reaches a model as a new level nobody trained on.
    CONSTRAINT ck_feature_definitions_categories CHECK (
        value_type <> 'CATEGORICAL' OR allowed_categories IS NOT NULL
    ),
    CONSTRAINT ck_feature_definitions_missing CHECK (
        missing_representation IN ('EXPLICIT_MISSING', 'ZERO', 'DEFAULT_VALUE', 'SENTINEL')
    ),
    CONSTRAINT ck_feature_definitions_default_policy CHECK (
        default_policy IN ('NONE', 'CONSTANT', 'LAST_OBSERVED', 'POPULATION_MEDIAN')
    ),
    CONSTRAINT ck_feature_definitions_default_value CHECK (
        (default_policy = 'CONSTANT') = (default_value IS NOT NULL)
    ),
    CONSTRAINT ck_feature_definitions_computation_kind CHECK (
        computation_kind IN ('AGGREGATE', 'DERIVED', 'RATIO', 'EMBEDDING', 'PASSTHROUGH',
                             'EFFECTIVE_DATED')
    ),
    CONSTRAINT ck_feature_definitions_materialization CHECK (
        materialization_mode IN ('REQUEST_COMPUTED', 'BATCH', 'INCREMENTAL',
                                 'STATIC_EFFECTIVE_DATED')
    ),
    CONSTRAINT ck_feature_definitions_event_time_basis CHECK (
        event_time_basis IN ('SOURCE_OCCURRED', 'SOURCE_AVAILABLE')
    ),
    CONSTRAINT ck_feature_definitions_windows CHECK (
        (window_days IS NULL OR window_days > 0)
            AND (lookback_days IS NULL OR lookback_days > 0)
            AND (window_days IS NULL OR lookback_days IS NULL OR lookback_days >= window_days)
    ),
    CONSTRAINT ck_feature_definitions_freshness CHECK (freshness_target_minutes > 0),
    -- A value served online with no expiry is a value that outlives its own source. The time to
    -- live is what stops yesterday's session signal being presented as the current one.
    CONSTRAINT ck_feature_definitions_ttl CHECK (
        NOT available_online_serving OR (time_to_live_minutes IS NOT NULL
            AND time_to_live_minutes > 0)
    ),
    CONSTRAINT ck_feature_definitions_available_somewhere CHECK (
        available_offline OR available_batch_serving OR available_online_serving
    ),
    -- Two independent implementations of one formula diverge, and the divergence is discovered in
    -- production by a model behaving differently from its evaluation. A feature offered on both
    -- paths has to name the parity test that proves they agree.
    CONSTRAINT ck_feature_definitions_parity CHECK (
        NOT (available_offline AND available_online_serving) OR parity_test_reference IS NOT NULL
    ),
    -- Some online signals genuinely cannot be reconstructed in warehouse time. They are allowed to
    -- exist and allowed to be marked, but a feature nobody can reproduce cannot be the basis of a
    -- consequential decision, because there is no way to show afterwards what it actually was.
    CONSTRAINT ck_feature_definitions_reproducible CHECK (
        reproducible_offline OR NOT consequential_use_allowed
    ),
    CONSTRAINT ck_feature_definitions_privacy_class CHECK (
        privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    -- A protected or highly sensitive trait may exist as a feature only as a named, reviewed,
    -- lawful decision with a document behind it. Absent both columns, the registry simply has no
    -- way to record such a feature, which is the intended outcome.
    CONSTRAINT ck_feature_definitions_sensitive CHECK (
        NOT sensitive_attribute
            OR (sensitive_attribute_basis IS NOT NULL AND sensitive_review_reference IS NOT NULL)
    ),
    CONSTRAINT ck_feature_definitions_training_eligible CHECK (
        NOT training_eligible OR privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS')
    ),
    CONSTRAINT ck_feature_definitions_retention CHECK (retention_days > 0),
    CONSTRAINT ck_feature_definitions_deletion_behaviour CHECK (
        deletion_behaviour IN ('ERASE', 'PSEUDONYMIZE', 'SUPPRESS', 'RETAIN_UNDER_LEGAL_BASIS')
    ),
    CONSTRAINT ck_feature_definitions_skew CHECK (
        skew_threshold IS NULL OR (skew_threshold > 0 AND skew_threshold <= 1)
    ),
    CONSTRAINT ck_feature_definitions_status CHECK (
        status IN ('DRAFT', 'REVIEWED', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_feature_definitions_activated CHECK (
        status IN ('DRAFT', 'REVIEWED') OR activated_at IS NOT NULL
    ),
    -- Retiring a feature that models still read is a production incident scheduled for later. A
    -- deprecated feature says what replaces it, so the migration is a task rather than a surprise.
    CONSTRAINT ck_feature_definitions_deprecation CHECK (
        status <> 'DEPRECATED' OR (deprecated_at IS NOT NULL AND replaced_by_id IS NOT NULL)
    ),
    CONSTRAINT ck_feature_definitions_retirement CHECK (
        status <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_feature_definitions_not_self CHECK (
        replaced_by_id IS DISTINCT FROM id AND supersedes_id IS DISTINCT FROM id
    ),
    CONSTRAINT ck_feature_definitions_version CHECK (version >= 0)
);

CREATE INDEX idx_feature_definitions_source ON feature_definitions (source_data_product_id);
CREATE INDEX idx_feature_definitions_status ON feature_definitions (status, entity_kind);
--rollback DROP TABLE feature_definitions;

--changeset ninggiangboy:031-02-feature-set-versions
CREATE TABLE feature_set_versions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    set_key          VARCHAR(96) NOT NULL,
    semantic_version SMALLINT NOT NULL,
    display_name     VARCHAR(200) NOT NULL,
    purpose          VARCHAR(500) NOT NULL,
    entity_kind      VARCHAR(24) NOT NULL,
    member_digest    CHAR(64) NOT NULL,
    business_owner   VARCHAR(64) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    frozen_at        TIMESTAMPTZ,
    retired_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_feature_set_versions_key UNIQUE (set_key, semantic_version),
    -- The digest is over the member definition versions. Two sets with the same members are the
    -- same set, and a registered model that names a set version names an exact list of features --
    -- which is what makes "this model may not read features it did not declare" checkable.
    CONSTRAINT uk_feature_set_versions_member_digest UNIQUE (set_key, member_digest),
    CONSTRAINT ck_feature_set_versions_semantic_version CHECK (semantic_version >= 1),
    CONSTRAINT ck_feature_set_versions_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    CONSTRAINT ck_feature_set_versions_status CHECK (
        status IN ('DRAFT', 'FROZEN', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_feature_set_versions_frozen CHECK (
        status = 'DRAFT' OR frozen_at IS NOT NULL
    ),
    CONSTRAINT ck_feature_set_versions_retired CHECK (
        status <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_feature_set_versions_version CHECK (version >= 0)
);
--rollback DROP TABLE feature_set_versions;

--changeset ninggiangboy:031-03-feature-set-members
CREATE TABLE feature_set_members (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_set_version_id UUID NOT NULL,
    feature_definition_id  UUID NOT NULL,
    ordinal                INTEGER NOT NULL,
    required               BOOLEAN NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ NOT NULL,
    -- A set version names one exact version of each feature. Both uniqueness rules matter: the
    -- same feature cannot appear twice, and the ordinal cannot collide, because a vector whose
    -- column order is ambiguous is a vector the serving path and the training path can disagree
    -- about without either of them raising anything.
    CONSTRAINT uk_feature_set_members_feature
        UNIQUE (feature_set_version_id, feature_definition_id),
    CONSTRAINT uk_feature_set_members_ordinal UNIQUE (feature_set_version_id, ordinal),
    CONSTRAINT fk_feature_set_members_set FOREIGN KEY (feature_set_version_id)
        REFERENCES feature_set_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_feature_set_members_feature FOREIGN KEY (feature_definition_id)
        REFERENCES feature_definitions (id),
    CONSTRAINT ck_feature_set_members_ordinal CHECK (ordinal >= 0)
);

CREATE INDEX idx_feature_set_members_feature ON feature_set_members (feature_definition_id);
--rollback DROP TABLE feature_set_members;

--changeset ninggiangboy:031-04-offline-feature-values
CREATE TABLE offline_feature_values (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_definition_id UUID NOT NULL,
    entity_kind           VARCHAR(24) NOT NULL,
    entity_pseudonym      VARCHAR(64) NOT NULL,
    context_digest        CHAR(64),
    source_occurred_at    TIMESTAMPTZ NOT NULL,
    source_available_at   TIMESTAMPTZ NOT NULL,
    valid_from            TIMESTAMPTZ NOT NULL,
    valid_to              TIMESTAMPTZ,
    value_state           VARCHAR(16) NOT NULL,
    value_numeric         NUMERIC(24,8),
    value_text            VARCHAR(200),
    value_reference       VARCHAR(200),
    imputation_method     VARCHAR(24),
    pipeline_run_id       UUID NOT NULL,
    source_version        BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    -- One value per feature version, entity, context and effective start. NULLS NOT DISTINCT is
    -- required because a feature with no request context has a null digest, and without it the
    -- entity-level features -- the majority -- would have no uniqueness at all.
    CONSTRAINT uk_offline_feature_values_effective
        UNIQUE NULLS NOT DISTINCT
        (feature_definition_id, entity_pseudonym, context_digest, valid_from),
    CONSTRAINT fk_offline_feature_values_definition FOREIGN KEY (feature_definition_id)
        REFERENCES feature_definitions (id),
    CONSTRAINT fk_offline_feature_values_run FOREIGN KEY (pipeline_run_id)
        REFERENCES pipeline_runs (id),
    -- Pseudonyms are hex, for the same reason they are in 030: the pattern is what stops an email
    -- address or a raw account identifier being written into a feature row by a service that took
    -- a shortcut, and a feature store is precisely where such a shortcut would be permanent.
    CONSTRAINT ck_offline_feature_values_pseudonym CHECK (
        entity_pseudonym ~ '^[0-9a-f]{16,64}$'
    ),
    CONSTRAINT ck_offline_feature_values_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    -- A value cannot have become knowable before the thing it describes happened.
    CONSTRAINT ck_offline_feature_values_availability CHECK (
        source_available_at >= source_occurred_at
    ),
    -- This is the whole of point-in-time correctness in one line. A value may not be effective
    -- before the moment it could have been read, so a training example at time t can only pick up
    -- values that a serving request at time t would also have seen. Without it a model trains on
    -- the settled outcome of the very event it is meant to predict, scores beautifully offline and
    -- does nothing at all in production, and the cause is invisible in every metric that was run.
    CONSTRAINT ck_offline_feature_values_point_in_time CHECK (valid_from >= source_available_at),
    CONSTRAINT ck_offline_feature_values_interval CHECK (
        valid_to IS NULL OR valid_to > valid_from
    ),
    CONSTRAINT ck_offline_feature_values_state CHECK (
        value_state IN ('PRESENT', 'MISSING', 'SUPPRESSED', 'IMPUTED')
    ),
    -- Missing is a state with its own row, not an absent row. A lookup that finds nothing cannot
    -- tell "this guest has never booked" from "the pipeline did not run", and a model trained on
    -- the first while served the second is being fed a different distribution than it learned.
    CONSTRAINT ck_offline_feature_values_present CHECK (
        value_state NOT IN ('PRESENT', 'IMPUTED')
            OR num_nonnulls(value_numeric, value_text, value_reference) = 1
    ),
    CONSTRAINT ck_offline_feature_values_absent CHECK (
        value_state NOT IN ('MISSING', 'SUPPRESSED')
            OR num_nonnulls(value_numeric, value_text, value_reference) = 0
    ),
    -- An imputed value that does not say how it was imputed is indistinguishable from an observed
    -- one, and the difference is the difference between a measurement and a guess.
    CONSTRAINT ck_offline_feature_values_imputation CHECK (
        (value_state = 'IMPUTED') = (imputation_method IS NOT NULL)
    ),
    CONSTRAINT ck_offline_feature_values_imputation_method CHECK (
        imputation_method IS NULL
            OR imputation_method IN ('CONSTANT', 'LAST_OBSERVED', 'POPULATION_MEDIAN')
    ),
    CONSTRAINT ck_offline_feature_values_source_version CHECK (
        source_version IS NULL OR source_version >= 0
    )
);

CREATE INDEX idx_offline_feature_values_lookup
    ON offline_feature_values (feature_definition_id, entity_pseudonym, valid_from DESC);
CREATE INDEX idx_offline_feature_values_run ON offline_feature_values (pipeline_run_id);
--rollback DROP TABLE offline_feature_values;

--changeset ninggiangboy:031-05-online-feature-values
CREATE TABLE online_feature_values (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_definition_id UUID NOT NULL,
    entity_kind           VARCHAR(24) NOT NULL,
    entity_pseudonym      VARCHAR(64) NOT NULL,
    context_digest        CHAR(64),
    value_state           VARCHAR(16) NOT NULL,
    value_numeric         NUMERIC(24,8),
    value_text            VARCHAR(200),
    value_reference       VARCHAR(200),
    as_of                 TIMESTAMPTZ NOT NULL,
    source_event_time     TIMESTAMPTZ NOT NULL,
    source_sequence       BIGINT NOT NULL DEFAULT 0,
    expires_at            TIMESTAMPTZ NOT NULL,
    pipeline_run_id       UUID,
    historical_rebuild    BOOLEAN NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    -- The online store holds one current value per feature, entity and context. It is a rebuildable
    -- projection, not evidence: everything here can be recomputed from offline_feature_values and
    -- the source products, which is why it is the one table in this migration that is not
    -- append-only.
    CONSTRAINT uk_online_feature_values_current
        UNIQUE NULLS NOT DISTINCT (feature_definition_id, entity_pseudonym, context_digest),
    CONSTRAINT fk_online_feature_values_definition FOREIGN KEY (feature_definition_id)
        REFERENCES feature_definitions (id) ON DELETE CASCADE,
    CONSTRAINT fk_online_feature_values_run FOREIGN KEY (pipeline_run_id)
        REFERENCES pipeline_runs (id),
    CONSTRAINT ck_online_feature_values_pseudonym CHECK (
        entity_pseudonym ~ '^[0-9a-f]{16,64}$'
    ),
    CONSTRAINT ck_online_feature_values_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    CONSTRAINT ck_online_feature_values_state CHECK (
        value_state IN ('PRESENT', 'MISSING', 'SUPPRESSED', 'IMPUTED')
    ),
    CONSTRAINT ck_online_feature_values_present CHECK (
        value_state NOT IN ('PRESENT', 'IMPUTED')
            OR num_nonnulls(value_numeric, value_text, value_reference) = 1
    ),
    CONSTRAINT ck_online_feature_values_absent CHECK (
        value_state NOT IN ('MISSING', 'SUPPRESSED')
            OR num_nonnulls(value_numeric, value_text, value_reference) = 0
    ),
    -- A served value carries its own expiry, so that a stale read is a detectable state rather
    -- than a silently old number. The serving path treats expiry as missing; it does not guess.
    CONSTRAINT ck_online_feature_values_expiry CHECK (expires_at > as_of),
    CONSTRAINT ck_online_feature_values_source_time CHECK (as_of >= source_event_time),
    CONSTRAINT ck_online_feature_values_sequence CHECK (source_sequence >= 0),
    CONSTRAINT ck_online_feature_values_version CHECK (version >= 0)
);

CREATE INDEX idx_online_feature_values_expiry ON online_feature_values (expires_at);
--rollback DROP TABLE online_feature_values;

--changeset ninggiangboy:031-06-feature-invalidations
CREATE TABLE feature_invalidations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_definition_id UUID NOT NULL,
    scope                 VARCHAR(16) NOT NULL,
    entity_pseudonym      VARCHAR(64),
    partition_key         VARCHAR(64),
    reason                VARCHAR(32) NOT NULL,
    reason_detail         VARCHAR(500) NOT NULL,
    data_correction_id    UUID,
    effective_from        TIMESTAMPTZ NOT NULL,
    effective_to          TIMESTAMPTZ,
    serving_behaviour     VARCHAR(24) NOT NULL,
    requested_by          VARCHAR(64) NOT NULL,
    recorded_at           TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_feature_invalidations_definition FOREIGN KEY (feature_definition_id)
        REFERENCES feature_definitions (id) ON DELETE CASCADE,
    CONSTRAINT fk_feature_invalidations_correction FOREIGN KEY (data_correction_id)
        REFERENCES data_corrections (id),
    CONSTRAINT ck_feature_invalidations_scope CHECK (
        scope IN ('DEFINITION', 'ENTITY', 'PARTITION')
    ),
    CONSTRAINT ck_feature_invalidations_entity CHECK (
        (scope = 'ENTITY') = (entity_pseudonym IS NOT NULL)
    ),
    CONSTRAINT ck_feature_invalidations_partition CHECK (
        (scope = 'PARTITION') = (partition_key IS NOT NULL)
    ),
    CONSTRAINT ck_feature_invalidations_pseudonym CHECK (
        entity_pseudonym IS NULL OR entity_pseudonym ~ '^[0-9a-f]{16,64}$'
    ),
    -- The five reasons a served value stops being usable. Each one comes from somewhere the model
    -- owner does not control -- a correction upstream, a person exercising a right, a source that
    -- turned out to be wrong, a definition retired -- which is why the invalidation is a record the
    -- serving path reads rather than a cache eviction somebody remembered to run.
    CONSTRAINT ck_feature_invalidations_reason CHECK (
        reason IN ('CORRECTION', 'SUBJECT_DELETION', 'CONSENT_WITHDRAWAL', 'SOURCE_INVALIDATION',
                   'DEFINITION_RETIREMENT')
    ),
    CONSTRAINT ck_feature_invalidations_correction_named CHECK (
        reason <> 'CORRECTION' OR data_correction_id IS NOT NULL
    ),
    -- A deletion or a withdrawn consent is not a value that expires later; it is one that stops
    -- being usable and stays that way, so it may not carry an end date.
    CONSTRAINT ck_feature_invalidations_terminal CHECK (
        reason NOT IN ('SUBJECT_DELETION', 'CONSENT_WITHDRAWAL') OR effective_to IS NULL
    ),
    CONSTRAINT ck_feature_invalidations_interval CHECK (
        effective_to IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT ck_feature_invalidations_serving_behaviour CHECK (
        serving_behaviour IN ('REMOVE', 'RETURN_SUPPRESSED', 'BLOCK_MODEL')
    ),
    -- Erasure removes; it does not degrade to a suppressed placeholder that still says the subject
    -- exists, and it does not merely block one model while the value stays readable to the next.
    CONSTRAINT ck_feature_invalidations_deletion_removes CHECK (
        reason <> 'SUBJECT_DELETION' OR serving_behaviour = 'REMOVE'
    )
);

CREATE INDEX idx_feature_invalidations_definition
    ON feature_invalidations (feature_definition_id, effective_from DESC);
CREATE INDEX idx_feature_invalidations_entity
    ON feature_invalidations (entity_pseudonym) WHERE entity_pseudonym IS NOT NULL;
--rollback DROP TABLE feature_invalidations;

--changeset ninggiangboy:031-07-label-definitions
CREATE TABLE label_definitions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label_key                VARCHAR(120) NOT NULL,
    semantic_version         SMALLINT NOT NULL,
    target_name              VARCHAR(96) NOT NULL,
    entity_kind              VARCHAR(24) NOT NULL,
    context_keys             VARCHAR(200),
    prediction_time_rule     VARCHAR(500) NOT NULL,
    horizon                  INTERVAL NOT NULL,
    maturity_delay           INTERVAL NOT NULL DEFAULT INTERVAL '0',
    positive_definition      VARCHAR(500) NOT NULL,
    negative_definition      VARCHAR(500) NOT NULL,
    unresolved_definition    VARCHAR(500) NOT NULL,
    censoring_rule           VARCHAR(500) NOT NULL,
    exclusion_rule           VARCHAR(500),
    source_data_product_id   UUID NOT NULL,
    source_event_mapping     VARCHAR(500) NOT NULL,
    source_mapping_version   VARCHAR(32) NOT NULL,
    correction_behaviour     VARCHAR(24) NOT NULL,
    appeal_behaviour         VARCHAR(24) NOT NULL,
    known_biases             VARCHAR(1000) NOT NULL,
    selection_bias_present   BOOLEAN NOT NULL DEFAULT false,
    selection_policy_required BOOLEAN NOT NULL DEFAULT false,
    privacy_class            VARCHAR(16) NOT NULL,
    training_eligible        BOOLEAN NOT NULL DEFAULT false,
    retention_days           INTEGER NOT NULL,
    deletion_behaviour       VARCHAR(24) NOT NULL,
    quality_owner            VARCHAR(64) NOT NULL,
    adjudication_process     VARCHAR(500) NOT NULL,
    comparable_with_previous BOOLEAN NOT NULL DEFAULT true,
    supersedes_id            UUID,
    status                   VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    reviewed_at              TIMESTAMPTZ,
    activated_at             TIMESTAMPTZ,
    deprecated_at            TIMESTAMPTZ,
    retired_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_label_definitions_key UNIQUE (label_key, semantic_version),
    CONSTRAINT fk_label_definitions_source FOREIGN KEY (source_data_product_id)
        REFERENCES data_product_registry (id),
    CONSTRAINT fk_label_definitions_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES label_definitions (id),
    -- One overloaded "converted" or "good" label is several different questions sharing a column.
    -- booking_confirmed_within_7d_v1 and stay_completed_without_severe_incident_v1 are different
    -- targets with different horizons, different sources and different failure modes, and a name
    -- that carries its version is the cheapest way to stop one being evaluated as the other.
    CONSTRAINT ck_label_definitions_key CHECK (
        label_key ~ '^[a-z][a-z0-9_]*_v[0-9]{1,3}$'
    ),
    CONSTRAINT ck_label_definitions_semantic_version CHECK (semantic_version >= 1),
    CONSTRAINT ck_label_definitions_supersession CHECK (
        semantic_version = 1 OR supersedes_id IS NOT NULL
    ),
    CONSTRAINT ck_label_definitions_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    -- A target without a horizon is not a target; it is a hope that something happens eventually.
    CONSTRAINT ck_label_definitions_horizon CHECK (horizon > INTERVAL '0'),
    CONSTRAINT ck_label_definitions_maturity_delay CHECK (maturity_delay >= INTERVAL '0'),
    CONSTRAINT ck_label_definitions_correction_behaviour CHECK (
        correction_behaviour IN ('NEW_REVISION', 'NEW_VERSION')
    ),
    CONSTRAINT ck_label_definitions_appeal_behaviour CHECK (
        appeal_behaviour IN ('REOPENS_LABEL', 'RECORDED_ONLY', 'NOT_APPLICABLE')
    ),
    -- If outcomes are only visible for the cases an earlier system chose to act on, every
    -- observation has to name that system. Otherwise "not reviewed" enters training as "safe" and
    -- the next model learns to reproduce the previous one's blind spot, with more confidence.
    CONSTRAINT ck_label_definitions_selection CHECK (
        NOT selection_bias_present OR selection_policy_required
    ),
    CONSTRAINT ck_label_definitions_privacy_class CHECK (
        privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_label_definitions_training_eligible CHECK (
        NOT training_eligible OR privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS')
    ),
    CONSTRAINT ck_label_definitions_retention CHECK (retention_days > 0),
    CONSTRAINT ck_label_definitions_deletion_behaviour CHECK (
        deletion_behaviour IN ('ERASE', 'PSEUDONYMIZE', 'SUPPRESS', 'RETAIN_UNDER_LEGAL_BASIS')
    ),
    CONSTRAINT ck_label_definitions_status CHECK (
        status IN ('DRAFT', 'REVIEWED', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_label_definitions_activated CHECK (
        status IN ('DRAFT', 'REVIEWED') OR activated_at IS NOT NULL
    ),
    CONSTRAINT ck_label_definitions_retirement CHECK (
        status <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    CONSTRAINT ck_label_definitions_not_self CHECK (supersedes_id IS DISTINCT FROM id),
    CONSTRAINT ck_label_definitions_version CHECK (version >= 0)
);

CREATE INDEX idx_label_definitions_source ON label_definitions (source_data_product_id);
--rollback DROP TABLE label_definitions;

--changeset ninggiangboy:031-08-label-observations
CREATE TABLE label_observations (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label_definition_id      UUID NOT NULL,
    example_key              VARCHAR(128) NOT NULL,
    entity_kind              VARCHAR(24) NOT NULL,
    entity_pseudonym         VARCHAR(64) NOT NULL,
    context_digest           CHAR(64),
    prediction_at            TIMESTAMPTZ NOT NULL,
    horizon_ends_at          TIMESTAMPTZ NOT NULL,
    observed_at              TIMESTAMPTZ,
    matured_at               TIMESTAMPTZ,
    value_state              VARCHAR(16) NOT NULL,
    label_value              NUMERIC(12,6),
    example_weight           NUMERIC(10,6) NOT NULL DEFAULT 1,
    censoring_reason         VARCHAR(48),
    exclusion_reason         VARCHAR(48),
    observation_basis        VARCHAR(24) NOT NULL,
    selection_policy_version VARCHAR(64),
    selection_model_version  VARCHAR(64),
    reviewer_role_class      VARCHAR(32),
    reviewer_confidence      NUMERIC(5,4),
    policy_version           VARCHAR(64),
    appeal_outcome           VARCHAR(24),
    source_revision_digest   CHAR(64),
    revision_number          INTEGER NOT NULL DEFAULT 1,
    supersedes_observation_id UUID,
    pipeline_run_id          UUID NOT NULL,
    recorded_at              TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    -- One example, one standing observation per revision. A correction is a new row that names the
    -- one it replaces; the previous row stays exactly as it was, because a model that was released
    -- against it has to remain reproducible whatever the label turns out to be later.
    CONSTRAINT uk_label_observations_example
        UNIQUE (label_definition_id, example_key, revision_number),
    CONSTRAINT fk_label_observations_definition FOREIGN KEY (label_definition_id)
        REFERENCES label_definitions (id),
    CONSTRAINT fk_label_observations_run FOREIGN KEY (pipeline_run_id)
        REFERENCES pipeline_runs (id),
    -- A revision describes one particular earlier observation. When retention removes that
    -- observation, the revision has nothing left to correct and goes with it, so a retention job
    -- does not have to delete an example's history in a particular order to succeed.
    CONSTRAINT fk_label_observations_supersedes FOREIGN KEY (supersedes_observation_id)
        REFERENCES label_observations (id) ON DELETE CASCADE,
    CONSTRAINT ck_label_observations_pseudonym CHECK (
        entity_pseudonym ~ '^[0-9a-f]{16,64}$'
    ),
    CONSTRAINT ck_label_observations_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    CONSTRAINT ck_label_observations_horizon CHECK (horizon_ends_at > prediction_at),
    -- Five states, and the three that are not an answer are the important ones. A stay that has
    -- not finished has no satisfaction; a case nobody reviewed is not a safe case; a listing that
    -- was never rendered is not a rejection. Collapsing any of them into NEGATIVE is how a model
    -- learns the shape of the previous system's coverage instead of the behaviour it was aimed at.
    CONSTRAINT ck_label_observations_state CHECK (
        value_state IN ('POSITIVE', 'NEGATIVE', 'UNRESOLVED', 'CENSORED', 'EXCLUDED')
    ),
    CONSTRAINT ck_label_observations_resolved CHECK (
        value_state NOT IN ('POSITIVE', 'NEGATIVE')
            OR (observed_at IS NOT NULL AND matured_at IS NOT NULL AND label_value IS NOT NULL)
    ),
    CONSTRAINT ck_label_observations_unresolved CHECK (
        value_state NOT IN ('UNRESOLVED', 'CENSORED', 'EXCLUDED') OR label_value IS NULL
    ),
    -- An answer may not be dated before the window in which it could have happened has closed.
    -- Reading a seven-day outcome on day three is not an early result; it is a different question.
    CONSTRAINT ck_label_observations_maturity CHECK (
        matured_at IS NULL OR matured_at >= horizon_ends_at
    ),
    CONSTRAINT ck_label_observations_observed_order CHECK (
        observed_at IS NULL OR observed_at >= prediction_at
    ),
    CONSTRAINT ck_label_observations_censored CHECK (
        (value_state = 'CENSORED') = (censoring_reason IS NOT NULL)
    ),
    CONSTRAINT ck_label_observations_excluded CHECK (
        (value_state = 'EXCLUDED') = (exclusion_reason IS NOT NULL)
    ),
    -- An actor who can generate unlimited activity can otherwise dominate a training set by
    -- volume alone, so the weight has a ceiling rather than being whatever the job computed.
    CONSTRAINT ck_label_observations_weight CHECK (
        example_weight > 0 AND example_weight <= 1000
    ),
    CONSTRAINT ck_label_observations_basis CHECK (
        observation_basis IN ('ORGANIC', 'RANDOMIZED_AUDIT', 'EXPLORATION', 'HUMAN_REVIEW')
    ),
    -- A human decision is not a clean label. It was made by somebody with a role, under a policy
    -- version, with a confidence, and it may have been overturned on appeal. Recording all four is
    -- what lets a dataset builder measure reviewer disagreement and policy drift instead of
    -- training on them as if they were ground truth.
    CONSTRAINT ck_label_observations_human_review CHECK (
        observation_basis <> 'HUMAN_REVIEW'
            OR (reviewer_role_class IS NOT NULL AND policy_version IS NOT NULL
                AND reviewer_confidence IS NOT NULL)
    ),
    CONSTRAINT ck_label_observations_confidence CHECK (
        reviewer_confidence IS NULL OR (reviewer_confidence > 0 AND reviewer_confidence <= 1)
    ),
    CONSTRAINT ck_label_observations_appeal_outcome CHECK (
        appeal_outcome IS NULL
            OR appeal_outcome IN ('UPHELD', 'REVERSED', 'PARTIALLY_REVERSED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_label_observations_revision CHECK (
        revision_number >= 1 AND (revision_number = 1) = (supersedes_observation_id IS NULL)
    ),
    CONSTRAINT ck_label_observations_not_self CHECK (
        supersedes_observation_id IS DISTINCT FROM id
    )
);

CREATE INDEX idx_label_observations_definition
    ON label_observations (label_definition_id, prediction_at);
CREATE INDEX idx_label_observations_entity ON label_observations (entity_pseudonym);
CREATE INDEX idx_label_observations_run ON label_observations (pipeline_run_id);
--rollback DROP TABLE label_observations;

--changeset ninggiangboy:031-09-training-dataset-manifests
CREATE TABLE training_dataset_manifests (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dataset_key              VARCHAR(96) NOT NULL,
    semantic_version         SMALLINT NOT NULL,
    purpose                  VARCHAR(500) NOT NULL,
    owner_reference          VARCHAR(64) NOT NULL,
    approved_model_family    VARCHAR(32) NOT NULL,
    label_definition_id      UUID NOT NULL,
    feature_set_version_id   UUID NOT NULL,
    population_expression    VARCHAR(500) NOT NULL,
    sampling_strategy        VARCHAR(32) NOT NULL,
    split_strategy           VARCHAR(32) NOT NULL,
    time_dependent           BOOLEAN NOT NULL DEFAULT true,
    grouping_expression      VARCHAR(200),
    train_range_start        TIMESTAMPTZ NOT NULL,
    train_range_end          TIMESTAMPTZ NOT NULL,
    validation_range_start   TIMESTAMPTZ NOT NULL,
    validation_range_end     TIMESTAMPTZ NOT NULL,
    test_range_start         TIMESTAMPTZ NOT NULL,
    test_range_end           TIMESTAMPTZ NOT NULL,
    prediction_time_rule     VARCHAR(500) NOT NULL,
    feature_cutoff_at        TIMESTAMPTZ NOT NULL,
    label_cutoff_at          TIMESTAMPTZ NOT NULL,
    censoring_modelled       BOOLEAN NOT NULL DEFAULT false,
    source_snapshot_reference VARCHAR(200) NOT NULL,
    code_version             VARCHAR(64) NOT NULL,
    config_digest            CHAR(64) NOT NULL,
    specification_digest     CHAR(64) NOT NULL,
    random_seed              BIGINT NOT NULL,
    consent_query_reference  VARCHAR(200) NOT NULL,
    exclusion_expression     VARCHAR(500),
    deletion_watermark_at    TIMESTAMPTZ NOT NULL,
    privacy_class            VARCHAR(16) NOT NULL,
    retention_days           INTEGER NOT NULL,
    class_weighting          VARCHAR(200),
    leakage_checks_passed    BOOLEAN NOT NULL DEFAULT false,
    known_limitations        VARCHAR(1000) NOT NULL,
    example_count            BIGINT NOT NULL,
    subject_count            BIGINT NOT NULL,
    positive_rate            NUMERIC(9,8),
    slice_distribution_reference VARCHAR(200),
    artifact_reference       VARCHAR(200) NOT NULL,
    artifact_checksum        CHAR(64) NOT NULL,
    access_policy            VARCHAR(64) NOT NULL,
    reuse_state              VARCHAR(32) NOT NULL DEFAULT 'REUSABLE',
    invalidated_at           TIMESTAMPTZ,
    invalidation_reason      VARCHAR(500),
    built_at                 TIMESTAMPTZ NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_training_dataset_manifests_key UNIQUE (dataset_key, semantic_version),
    -- Building the same specification over the same snapshots twice is a retry, not a second
    -- dataset. The digest is what makes a crashed build safe to restart without quietly creating a
    -- near-identical manifest that a later model is registered against by accident.
    CONSTRAINT uk_training_dataset_manifests_specification UNIQUE (specification_digest),
    CONSTRAINT fk_training_dataset_manifests_label FOREIGN KEY (label_definition_id)
        REFERENCES label_definitions (id),
    CONSTRAINT fk_training_dataset_manifests_feature_set FOREIGN KEY (feature_set_version_id)
        REFERENCES feature_set_versions (id),
    CONSTRAINT ck_training_dataset_manifests_semantic_version CHECK (semantic_version >= 1),
    CONSTRAINT ck_training_dataset_manifests_sampling CHECK (
        sampling_strategy IN ('FULL_POPULATION', 'UNIFORM', 'STRATIFIED', 'NEGATIVE_DOWNSAMPLED',
                              'IMPORTANCE_WEIGHTED')
    ),
    CONSTRAINT ck_training_dataset_manifests_split CHECK (
        split_strategy IN ('TEMPORAL', 'ENTITY_GROUPED', 'TEMPORAL_ENTITY_GROUPED', 'RANDOM')
    ),
    -- A random row split on marketplace behaviour puts the same booking, host, guest or near
    -- duplicate listing on both sides of the boundary, and the test score it produces is a
    -- measurement of the leak rather than of the model. Allowed only where nothing is time
    -- dependent, which in this domain is almost nowhere.
    CONSTRAINT ck_training_dataset_manifests_random_split CHECK (
        NOT time_dependent OR split_strategy <> 'RANDOM'
    ),
    CONSTRAINT ck_training_dataset_manifests_grouping CHECK (
        split_strategy NOT IN ('ENTITY_GROUPED', 'TEMPORAL_ENTITY_GROUPED')
            OR grouping_expression IS NOT NULL
    ),
    CONSTRAINT ck_training_dataset_manifests_ranges CHECK (
        train_range_end > train_range_start
            AND validation_range_end > validation_range_start
            AND test_range_end > test_range_start
    ),
    -- Temporal splits run forwards. Validation that overlaps training, or a test window that
    -- starts before validation ends, reports a number that the production timeline cannot produce.
    CONSTRAINT ck_training_dataset_manifests_temporal_order CHECK (
        split_strategy NOT IN ('TEMPORAL', 'TEMPORAL_ENTITY_GROUPED')
            OR (train_range_end <= validation_range_start
                AND validation_range_end <= test_range_start)
    ),
    CONSTRAINT ck_training_dataset_manifests_cutoffs CHECK (
        label_cutoff_at >= feature_cutoff_at AND feature_cutoff_at >= test_range_end
    ),
    CONSTRAINT ck_training_dataset_manifests_built CHECK (built_at >= label_cutoff_at),
    -- A dataset built without pinning a deletion watermark cannot say which erasures it honoured,
    -- and therefore cannot be shown to have honoured any of them.
    CONSTRAINT ck_training_dataset_manifests_deletion_watermark CHECK (
        deletion_watermark_at <= built_at
    ),
    CONSTRAINT ck_training_dataset_manifests_privacy_class CHECK (
        privacy_class IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_training_dataset_manifests_retention CHECK (retention_days > 0),
    CONSTRAINT ck_training_dataset_manifests_counts CHECK (
        example_count >= 0 AND subject_count >= 0 AND subject_count <= example_count
    ),
    CONSTRAINT ck_training_dataset_manifests_positive_rate CHECK (
        positive_rate IS NULL OR (positive_rate >= 0 AND positive_rate <= 1)
    ),
    -- A manifest is a claim about a moment. Later deletion, correction or expiry makes it unusable
    -- for a new build, and that is recorded here rather than by silently rewriting the artifact --
    -- a model already released against it has to stay reproducible even once it may not be reused.
    CONSTRAINT ck_training_dataset_manifests_reuse_state CHECK (
        reuse_state IN ('REUSABLE', 'INVALIDATED_BY_DELETION', 'INVALIDATED_BY_CORRECTION',
                        'EXPIRED')
    ),
    CONSTRAINT ck_training_dataset_manifests_invalidated CHECK (
        (reuse_state = 'REUSABLE') = (invalidated_at IS NULL)
    ),
    CONSTRAINT ck_training_dataset_manifests_invalidation_reason CHECK (
        (invalidated_at IS NULL) = (invalidation_reason IS NULL)
    ),
    CONSTRAINT ck_training_dataset_manifests_version CHECK (version >= 0)
);

CREATE INDEX idx_training_dataset_manifests_label
    ON training_dataset_manifests (label_definition_id);
CREATE INDEX idx_training_dataset_manifests_feature_set
    ON training_dataset_manifests (feature_set_version_id);
--rollback DROP TABLE training_dataset_manifests;

--changeset ninggiangboy:031-10-model-versions
CREATE TABLE model_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_key                   VARCHAR(96) NOT NULL,
    model_version               VARCHAR(64) NOT NULL,
    model_family                VARCHAR(32) NOT NULL,
    target_name                 VARCHAR(96) NOT NULL,
    horizon                     INTERVAL,
    impact_class                VARCHAR(24) NOT NULL,
    intended_decisions          VARCHAR(500) NOT NULL,
    prohibited_uses             VARCHAR(500) NOT NULL,
    training_dataset_manifest_id UUID NOT NULL,
    feature_set_version_id      UUID NOT NULL,
    label_definition_id         UUID NOT NULL,
    artifact_reference          VARCHAR(200) NOT NULL,
    artifact_checksum           CHAR(64) NOT NULL,
    code_version                VARCHAR(64) NOT NULL,
    environment_digest          CHAR(64) NOT NULL,
    random_seed                 BIGINT,
    model_card_reference        VARCHAR(200),
    inference_contract_reference VARCHAR(200) NOT NULL,
    output_schema_digest        CHAR(64) NOT NULL,
    input_validation_reference  VARCHAR(200) NOT NULL,
    latency_budget_ms           INTEGER NOT NULL,
    cost_budget_micros          BIGINT,
    fallback_behaviour          VARCHAR(24) NOT NULL,
    monitoring_reference        VARCHAR(200),
    retraining_trigger          VARCHAR(500),
    retirement_trigger          VARCHAR(500),
    rollback_target_id          UUID,
    owner_reference             VARCHAR(64) NOT NULL,
    external_provider           VARCHAR(64),
    provider_licence_reference  VARCHAR(200),
    provider_training_rights    VARCHAR(32),
    provider_retention_days     INTEGER,
    provider_residency          VARCHAR(32),
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    trained_at                  TIMESTAMPTZ,
    validated_at                TIMESTAMPTZ,
    approved_at                 TIMESTAMPTZ,
    activated_at                TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_model_versions_key UNIQUE (model_key, model_version),
    -- Re-registering the same bytes under the same key returns the same version rather than
    -- creating a second one. Changing the bytes and keeping the version number is the thing this
    -- refuses: every prediction, evaluation and decision downstream names a version, and a mutable
    -- version silently reassigns what all of them meant.
    CONSTRAINT uk_model_versions_artifact UNIQUE (model_key, artifact_checksum),
    CONSTRAINT fk_model_versions_manifest FOREIGN KEY (training_dataset_manifest_id)
        REFERENCES training_dataset_manifests (id),
    CONSTRAINT fk_model_versions_feature_set FOREIGN KEY (feature_set_version_id)
        REFERENCES feature_set_versions (id),
    CONSTRAINT fk_model_versions_label FOREIGN KEY (label_definition_id)
        REFERENCES label_definitions (id),
    CONSTRAINT fk_model_versions_rollback_target FOREIGN KEY (rollback_target_id)
        REFERENCES model_versions (id),
    CONSTRAINT ck_model_versions_family CHECK (
        model_family IN ('QUERY_UNDERSTANDING', 'SEMANTIC_RETRIEVAL', 'GUEST_PREFERENCE',
                         'LEARNING_TO_RANK', 'REVIEW_INTELLIGENCE', 'DEMAND_FORECAST',
                         'PRICE_ELASTICITY', 'PROMOTION_UPLIFT', 'RISK_SCORING',
                         'SUPPORT_ASSISTANCE', 'MESSAGING_OPTIMIZATION', 'CONTENT_CLASSIFICATION')
    ),
    -- The impact class is what decides how many independent people have to agree before this
    -- version may be routed traffic. It is declared at registration, not argued about at promotion.
    CONSTRAINT ck_model_versions_impact_class CHECK (
        impact_class IN ('ADVISORY', 'SAFETY', 'FINANCIAL', 'PRICING', 'ELIGIBILITY', 'MODERATION')
    ),
    CONSTRAINT ck_model_versions_horizon CHECK (horizon IS NULL OR horizon > INTERVAL '0'),
    CONSTRAINT ck_model_versions_latency_budget CHECK (latency_budget_ms > 0),
    CONSTRAINT ck_model_versions_cost_budget CHECK (
        cost_budget_micros IS NULL OR cost_budget_micros >= 0
    ),
    -- Every registered version declares what the consumer does when it cannot answer. A model with
    -- no declared fallback becomes a hard dependency the moment it is routed traffic, and the
    -- decision about failing open or closed gets made by a timeout instead of by a person.
    CONSTRAINT ck_model_versions_fallback CHECK (
        fallback_behaviour IN ('DETERMINISTIC_BASELINE', 'NO_PREDICTION', 'HUMAN_REVIEW', 'BLOCK')
    ),
    CONSTRAINT ck_model_versions_provider CHECK (
        external_provider IS NULL
            OR (provider_licence_reference IS NOT NULL AND provider_training_rights IS NOT NULL
                AND provider_residency IS NOT NULL)
    ),
    CONSTRAINT ck_model_versions_provider_rights CHECK (
        provider_training_rights IS NULL
            OR provider_training_rights IN ('PROHIBITED', 'PERMITTED_WITH_APPROVAL')
    ),
    -- Safety, money, price, eligibility and moderation inputs do not go to a provider that is
    -- permitted to train on them. That is an expansion of data purpose granted by a procurement
    -- decision rather than by anyone who reviewed what the model sees.
    CONSTRAINT ck_model_versions_provider_consequential CHECK (
        impact_class = 'ADVISORY' OR provider_training_rights IS NULL
            OR provider_training_rights = 'PROHIBITED'
    ),
    CONSTRAINT ck_model_versions_provider_retention CHECK (
        provider_retention_days IS NULL OR provider_retention_days >= 0
    ),
    CONSTRAINT ck_model_versions_status CHECK (
        status IN ('DRAFT', 'TRAINED', 'VALIDATED', 'APPROVED', 'SHADOW', 'CANARY', 'ACTIVE',
                   'REJECTED', 'RETIRED', 'ROLLED_BACK')
    ),
    -- Nothing may be validated without the card that states its population, its limitations and
    -- the uses it is excluded from, because that document is what the approvers are approving.
    CONSTRAINT ck_model_versions_model_card CHECK (
        status IN ('DRAFT', 'TRAINED') OR model_card_reference IS NOT NULL
    ),
    CONSTRAINT ck_model_versions_trained CHECK (
        status = 'DRAFT' OR trained_at IS NOT NULL
    ),
    CONSTRAINT ck_model_versions_validated CHECK (
        status IN ('DRAFT', 'TRAINED', 'REJECTED') OR validated_at IS NOT NULL
    ),
    CONSTRAINT ck_model_versions_approved CHECK (
        status IN ('DRAFT', 'TRAINED', 'VALIDATED', 'REJECTED') OR approved_at IS NOT NULL
    ),
    CONSTRAINT ck_model_versions_retired CHECK (
        status <> 'RETIRED' OR retired_at IS NOT NULL
    ),
    -- A rollback with nowhere to roll back to is a rollback plan in name only. Anything that can
    -- take live traffic names either an earlier version or its deterministic fallback.
    CONSTRAINT ck_model_versions_rollback_target CHECK (
        status NOT IN ('CANARY', 'ACTIVE')
            OR rollback_target_id IS NOT NULL
            OR fallback_behaviour = 'DETERMINISTIC_BASELINE'
    ),
    CONSTRAINT ck_model_versions_not_self CHECK (rollback_target_id IS DISTINCT FROM id),
    CONSTRAINT ck_model_versions_version CHECK (version >= 0)
);

CREATE INDEX idx_model_versions_status ON model_versions (model_key, status);
CREATE INDEX idx_model_versions_manifest ON model_versions (training_dataset_manifest_id);
CREATE INDEX idx_model_versions_feature_set ON model_versions (feature_set_version_id);
--rollback DROP TABLE model_versions;

--changeset ninggiangboy:031-11-model-approvals
CREATE TABLE model_approvals (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version_id   UUID NOT NULL,
    approval_role      VARCHAR(24) NOT NULL,
    approver_reference VARCHAR(64) NOT NULL,
    decision           VARCHAR(16) NOT NULL,
    decision_reason    VARCHAR(500) NOT NULL,
    evidence_reference VARCHAR(200),
    approved_scope     VARCHAR(200) NOT NULL,
    decided_at         TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    -- One standing decision per role per version. A second opinion from the same function is a new
    -- version's problem, not a way to collect approvals until one of them says yes.
    CONSTRAINT uk_model_approvals_role UNIQUE (model_version_id, approval_role),
    CONSTRAINT fk_model_approvals_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id) ON DELETE CASCADE,
    -- The roles are separate because the questions are separate. Whether the model works is a
    -- different question from whether it is lawful to use, and both are different from whether the
    -- domain that will act on it accepts the authority being handed over.
    CONSTRAINT ck_model_approvals_role CHECK (
        approval_role IN ('DOMAIN', 'RISK', 'PRIVACY', 'SECURITY', 'LEGAL', 'FINANCE', 'ML_OWNER')
    ),
    CONSTRAINT ck_model_approvals_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    -- An approval whose scope is not written down expands by usage. The scope is what a later
    -- promotion command is checked against when somebody asks for more traffic or a new market.
    CONSTRAINT ck_model_approvals_scope CHECK (length(btrim(approved_scope)) > 0)
);
--rollback DROP TABLE model_approvals;

--changeset ninggiangboy:031-12-model-evaluation-runs
CREATE TABLE model_evaluation_runs (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version_id           UUID NOT NULL,
    evaluation_kind            VARCHAR(24) NOT NULL,
    baseline_kind              VARCHAR(32) NOT NULL,
    baseline_model_version_id  UUID,
    training_dataset_manifest_id UUID,
    evaluation_dataset_reference VARCHAR(200) NOT NULL,
    evaluation_cutoff_at       TIMESTAMPTZ NOT NULL,
    code_version               VARCHAR(64) NOT NULL,
    config_digest              CHAR(64) NOT NULL,
    primary_metric_key         VARCHAR(120) NOT NULL,
    primary_metric_value       NUMERIC(18,8) NOT NULL,
    baseline_metric_value      NUMERIC(18,8),
    calibration_error          NUMERIC(12,8),
    drift_status               VARCHAR(16),
    latency_p95_ms             INTEGER,
    result                     VARCHAR(8) NOT NULL,
    conclusion                 VARCHAR(500) NOT NULL,
    run_at                     TIMESTAMPTZ NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_model_evaluation_runs_scope
        UNIQUE (model_version_id, evaluation_kind, baseline_kind, evaluation_cutoff_at),
    CONSTRAINT fk_model_evaluation_runs_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_model_evaluation_runs_baseline FOREIGN KEY (baseline_model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_model_evaluation_runs_manifest FOREIGN KEY (training_dataset_manifest_id)
        REFERENCES training_dataset_manifests (id),
    CONSTRAINT ck_model_evaluation_runs_kind CHECK (
        evaluation_kind IN ('OFFLINE', 'SHADOW', 'FAIRNESS', 'ROBUSTNESS', 'CALIBRATION', 'DRIFT')
    ),
    -- A model is only better than something. The four baselines exist because the interesting
    -- comparison is usually not against the previous model: it is against the deterministic rule
    -- that already works, and against doing nothing when the features are stale or missing --
    -- which is the state a real serving path spends a measurable fraction of its time in.
    CONSTRAINT ck_model_evaluation_runs_baseline_kind CHECK (
        baseline_kind IN ('PRODUCTION_HEURISTIC', 'STATISTICAL_BASELINE', 'CURRENT_CHAMPION',
                          'NO_PREDICTION', 'STALE_FEATURE_FALLBACK')
    ),
    CONSTRAINT ck_model_evaluation_runs_champion CHECK (
        (baseline_kind = 'CURRENT_CHAMPION') = (baseline_model_version_id IS NOT NULL)
    ),
    CONSTRAINT ck_model_evaluation_runs_not_self CHECK (
        baseline_model_version_id IS DISTINCT FROM model_version_id
    ),
    -- A comparison that reports only the candidate's number is not a comparison.
    CONSTRAINT ck_model_evaluation_runs_baseline_value CHECK (
        baseline_kind = 'NO_PREDICTION' OR baseline_metric_value IS NOT NULL
    ),
    CONSTRAINT ck_model_evaluation_runs_calibration CHECK (
        evaluation_kind <> 'CALIBRATION' OR calibration_error IS NOT NULL
    ),
    CONSTRAINT ck_model_evaluation_runs_drift CHECK (
        drift_status IS NULL OR drift_status IN ('NONE', 'OBSERVED', 'SEVERE')
    ),
    CONSTRAINT ck_model_evaluation_runs_latency CHECK (
        latency_p95_ms IS NULL OR latency_p95_ms >= 0
    ),
    CONSTRAINT ck_model_evaluation_runs_result CHECK (result IN ('PASS', 'WARN', 'FAIL')),
    CONSTRAINT ck_model_evaluation_runs_run_at CHECK (run_at >= evaluation_cutoff_at)
);

CREATE INDEX idx_model_evaluation_runs_model ON model_evaluation_runs (model_version_id, result);
--rollback DROP TABLE model_evaluation_runs;

--changeset ninggiangboy:031-13-model-evaluation-slices
CREATE TABLE model_evaluation_slices (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_evaluation_run_id UUID NOT NULL,
    slice_dimension         VARCHAR(48) NOT NULL,
    slice_value             VARCHAR(96) NOT NULL,
    party_affected          VARCHAR(16) NOT NULL,
    harm_class              VARCHAR(24) NOT NULL,
    subject_count           BIGINT NOT NULL,
    metric_key              VARCHAR(120) NOT NULL,
    metric_value            NUMERIC(18,8) NOT NULL,
    reference_value         NUMERIC(18,8),
    disparity               NUMERIC(12,8),
    threshold               NUMERIC(12,8),
    breached                BOOLEAN NOT NULL DEFAULT false,
    mitigation              VARCHAR(500),
    minimum_cohort_size     INTEGER NOT NULL,
    released                BOOLEAN NOT NULL DEFAULT false,
    limitation_note         VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_model_evaluation_slices_value
        UNIQUE (model_evaluation_run_id, slice_dimension, slice_value, metric_key),
    CONSTRAINT fk_model_evaluation_slices_run FOREIGN KEY (model_evaluation_run_id)
        REFERENCES model_evaluation_runs (id) ON DELETE CASCADE,
    -- A model affects both sides of the marketplace, and an aggregate that is fine can hide a
    -- guest-side error rate and a host-side exposure effect pulling in opposite directions.
    CONSTRAINT ck_model_evaluation_slices_party CHECK (
        party_affected IN ('GUEST', 'HOST', 'BOTH')
    ),
    CONSTRAINT ck_model_evaluation_slices_harm_class CHECK (
        harm_class IN ('NONE', 'OPPORTUNITY', 'ERROR_RATE', 'PRICE_PARITY', 'FALSE_RESTRICTION',
                       'NEW_SUPPLY', 'LANGUAGE_COVERAGE', 'SAFETY')
    ),
    CONSTRAINT ck_model_evaluation_slices_counts CHECK (
        subject_count >= 0 AND minimum_cohort_size > 0
    ),
    -- A slice too small to report without identifying the people in it is not released. The one
    -- exception is severe safety harm: a privacy threshold is a reason to handle a finding
    -- carefully, never a reason for nobody to be told that a small group is being hurt.
    CONSTRAINT ck_model_evaluation_slices_release CHECK (
        NOT released OR subject_count >= minimum_cohort_size OR harm_class = 'SAFETY'
    ),
    CONSTRAINT ck_model_evaluation_slices_breach CHECK (
        NOT breached OR (threshold IS NOT NULL AND mitigation IS NOT NULL)
    ),
    CONSTRAINT ck_model_evaluation_slices_disparity CHECK (
        disparity IS NULL OR reference_value IS NOT NULL
    )
);
--rollback DROP TABLE model_evaluation_slices;

--changeset ninggiangboy:031-14-model-release-routes
CREATE TABLE model_release_routes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer                    VARCHAR(48) NOT NULL,
    decision_scope              VARCHAR(96) NOT NULL,
    market_code                 VARCHAR(2),
    champion_model_version_id   UUID NOT NULL,
    challenger_model_version_id UUID,
    challenger_share            NUMERIC(5,4),
    experiment_epoch_id         UUID,
    route_mode                  VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    fallback_behaviour          VARCHAR(24) NOT NULL,
    fail_mode                   VARCHAR(12) NOT NULL,
    inference_budget_ms         INTEGER NOT NULL,
    guardrail_reference         VARCHAR(200),
    rollback_target_version_id  UUID,
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_to                TIMESTAMPTZ,
    approved_by                 VARCHAR(64),
    approved_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_model_release_routes_champion FOREIGN KEY (champion_model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_model_release_routes_challenger FOREIGN KEY (challenger_model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_model_release_routes_rollback FOREIGN KEY (rollback_target_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_model_release_routes_epoch FOREIGN KEY (experiment_epoch_id)
        REFERENCES experiment_epochs (id),
    CONSTRAINT ck_model_release_routes_mode CHECK (
        route_mode IN ('DISABLED', 'SHADOW', 'CANARY', 'ACTIVE')
    ),
    CONSTRAINT ck_model_release_routes_market CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_model_release_routes_challenger CHECK (
        (challenger_model_version_id IS NULL) = (challenger_share IS NULL)
    ),
    CONSTRAINT ck_model_release_routes_share CHECK (
        challenger_share IS NULL OR (challenger_share > 0 AND challenger_share < 1)
    ),
    -- Splitting traffic between two models is an experiment whether or not anybody calls it one.
    -- Naming the epoch is what makes the split analysable under the rules of 030 -- sealed
    -- allocation, declared guardrails, a stopping method -- instead of being a percentage somebody
    -- moves by feel while watching a dashboard.
    CONSTRAINT ck_model_release_routes_challenger_epoch CHECK (
        challenger_model_version_id IS NULL OR experiment_epoch_id IS NOT NULL
    ),
    CONSTRAINT ck_model_release_routes_distinct CHECK (
        challenger_model_version_id IS DISTINCT FROM champion_model_version_id
    ),
    CONSTRAINT ck_model_release_routes_fallback CHECK (
        fallback_behaviour IN ('DETERMINISTIC_BASELINE', 'NO_PREDICTION', 'HUMAN_REVIEW', 'BLOCK')
    ),
    -- Whether a consumer proceeds without a prediction or refuses to proceed is the consumer's
    -- policy, and it belongs to the route rather than to the model, because the same model can be
    -- safe to skip on a search page and unsafe to skip at a payout decision.
    CONSTRAINT ck_model_release_routes_fail_mode CHECK (
        fail_mode IN ('FAIL_OPEN', 'FAIL_CLOSED')
    ),
    CONSTRAINT ck_model_release_routes_budget CHECK (inference_budget_ms > 0),
    -- Live traffic requires a named approver and something to fall back to. A route that carries
    -- real decisions with neither is the state every post-incident review discovers afterwards.
    CONSTRAINT ck_model_release_routes_approval CHECK (
        route_mode NOT IN ('CANARY', 'ACTIVE')
            OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_model_release_routes_rollback_target CHECK (
        route_mode NOT IN ('CANARY', 'ACTIVE')
            OR rollback_target_version_id IS NOT NULL
            OR fallback_behaviour = 'DETERMINISTIC_BASELINE'
    ),
    CONSTRAINT ck_model_release_routes_interval CHECK (
        effective_to IS NULL OR effective_to > effective_from
    ),
    CONSTRAINT ck_model_release_routes_version CHECK (version >= 0)
);

-- One champion per consumer, decision scope and market at a time. NULLS NOT DISTINCT is required
-- because a market-independent route has a null market code, and without it the global routes --
-- the ones most likely to be duplicated by two people promoting at once -- would be unconstrained.
CREATE UNIQUE INDEX uk_model_release_routes_active
    ON model_release_routes (consumer, decision_scope, market_code) NULLS NOT DISTINCT
    WHERE route_mode = 'ACTIVE' AND effective_to IS NULL;

CREATE INDEX idx_model_release_routes_champion
    ON model_release_routes (champion_model_version_id);
CREATE INDEX idx_model_release_routes_epoch
    ON model_release_routes (experiment_epoch_id) WHERE experiment_epoch_id IS NOT NULL;
--rollback DROP TABLE model_release_routes;

--changeset ninggiangboy:031-15-prediction-records
CREATE TABLE prediction_records (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version_id         UUID NOT NULL,
    model_release_route_id   UUID,
    consumer                 VARCHAR(48) NOT NULL,
    target_name              VARCHAR(96) NOT NULL,
    canonical_context_digest CHAR(64) NOT NULL,
    request_key              VARCHAR(128) NOT NULL,
    entity_kind              VARCHAR(24) NOT NULL,
    entity_pseudonym         VARCHAR(64),
    market_code              VARCHAR(2),
    feature_set_version_id   UUID NOT NULL,
    oldest_feature_as_of     TIMESTAMPTZ,
    serving_mode             VARCHAR(16) NOT NULL,
    outputs                  JSONB,
    output_byte_size         INTEGER NOT NULL DEFAULT 0,
    uncertainty_status       VARCHAR(32) NOT NULL DEFAULT 'UNAVAILABLE',
    reason_codes             VARCHAR(300),
    status                   VARCHAR(24) NOT NULL,
    fallback_reason          VARCHAR(48),
    experiment_exposure_id   UUID,
    latency_ms               INTEGER,
    predicted_at             TIMESTAMPTZ NOT NULL,
    expires_at               TIMESTAMPTZ NOT NULL,
    retention_class          VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    created_at               TIMESTAMPTZ NOT NULL,
    -- Asking the same question twice returns the same answer. Without this the retry after a
    -- timeout produces a second prediction with a different value, and the decision log ends up
    -- naming whichever one the consumer happened to read.
    CONSTRAINT uk_prediction_records_request
        UNIQUE (consumer, target_name, canonical_context_digest, request_key),
    CONSTRAINT fk_prediction_records_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_prediction_records_route FOREIGN KEY (model_release_route_id)
        REFERENCES model_release_routes (id),
    CONSTRAINT fk_prediction_records_feature_set FOREIGN KEY (feature_set_version_id)
        REFERENCES feature_set_versions (id),
    CONSTRAINT fk_prediction_records_exposure FOREIGN KEY (experiment_exposure_id)
        REFERENCES experiment_exposures (id),
    CONSTRAINT ck_prediction_records_pseudonym CHECK (
        entity_pseudonym IS NULL OR entity_pseudonym ~ '^[0-9a-f]{16,64}$'
    ),
    CONSTRAINT ck_prediction_records_entity_kind CHECK (
        entity_kind IN ('GUEST', 'HOST', 'LISTING', 'PROPERTY', 'ACCOMMODATION_TYPE', 'BOOKING',
                        'SESSION', 'QUERY', 'MARKET', 'DESTINATION')
    ),
    CONSTRAINT ck_prediction_records_market CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_prediction_records_serving_mode CHECK (
        serving_mode IN ('ONLINE', 'BATCH', 'SHADOW')
    ),
    -- The eight ways a prediction can fail to be a prediction. They are recorded, not omitted:
    -- evaluating only the requests that returned a score hides the outage, hides how often the
    -- features were too stale to use, and makes the fallback path -- which is the path a real
    -- consumer spends a measurable share of its traffic on -- invisible to every quality metric.
    CONSTRAINT ck_prediction_records_status CHECK (
        status IN ('PREDICTED', 'TIMED_OUT', 'FEATURE_MISSING', 'FEATURE_STALE',
                   'MODEL_UNAVAILABLE', 'OUT_OF_SCOPE', 'CONSENT_DENIED', 'SUPPRESSED')
    ),
    CONSTRAINT ck_prediction_records_outputs CHECK (
        (status = 'PREDICTED') = (outputs IS NOT NULL)
    ),
    CONSTRAINT ck_prediction_records_fallback CHECK (
        (status <> 'PREDICTED') = (fallback_reason IS NOT NULL)
    ),
    -- A bounded output, so that a convenient log of "everything the model saw" cannot be written
    -- here under the name of a score. Raw feature dumps are exactly what a prediction record must
    -- not become: they carry the input data's privacy class without any of its controls.
    CONSTRAINT ck_prediction_records_output_size CHECK (
        output_byte_size >= 0 AND output_byte_size <= 4096
    ),
    -- A score with no statement about its own reliability gets read as certainty by whoever needs
    -- it to be certain, so the column has no null: it is either a validated range, an
    -- acknowledged extrapolation, or an admission that the model cannot say.
    CONSTRAINT ck_prediction_records_uncertainty CHECK (
        uncertainty_status IN ('WITHIN_VALIDATED_RANGE', 'OUTSIDE_VALIDATED_RANGE', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_prediction_records_expiry CHECK (expires_at > predicted_at),
    CONSTRAINT ck_prediction_records_latency CHECK (latency_ms IS NULL OR latency_ms >= 0),
    CONSTRAINT ck_prediction_records_retention_class CHECK (
        retention_class IN ('STANDARD', 'SHORT', 'LEGAL_HOLD')
    )
);

CREATE INDEX idx_prediction_records_model ON prediction_records (model_version_id, predicted_at);
CREATE INDEX idx_prediction_records_expiry ON prediction_records (expires_at);
CREATE INDEX idx_prediction_records_exposure
    ON prediction_records (experiment_exposure_id) WHERE experiment_exposure_id IS NOT NULL;
--rollback DROP TABLE prediction_records;

--changeset ninggiangboy:031-16-model-actions
CREATE TABLE model_actions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version_id       UUID NOT NULL,
    model_release_route_id UUID,
    action_type            VARCHAR(16) NOT NULL,
    expected_version       BIGINT NOT NULL,
    actor_kind             VARCHAR(24) NOT NULL,
    actor_reference        VARCHAR(64) NOT NULL,
    approver_reference     VARCHAR(64),
    action_scope           VARCHAR(200) NOT NULL,
    traffic_share          NUMERIC(5,4),
    reason_code            VARCHAR(48) NOT NULL,
    reason_detail          VARCHAR(500) NOT NULL,
    triggering_evaluation_id UUID,
    requested_at           TIMESTAMPTZ NOT NULL,
    applied                BOOLEAN NOT NULL DEFAULT false,
    applied_at             TIMESTAMPTZ,
    failure_reason         VARCHAR(200),
    created_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_model_actions_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_model_actions_route FOREIGN KEY (model_release_route_id)
        REFERENCES model_release_routes (id),
    CONSTRAINT fk_model_actions_evaluation FOREIGN KEY (triggering_evaluation_id)
        REFERENCES model_evaluation_runs (id),
    CONSTRAINT ck_model_actions_type CHECK (
        action_type IN ('APPROVE', 'PROMOTE', 'PAUSE', 'ROLLBACK', 'RETIRE', 'REJECT')
    ),
    CONSTRAINT ck_model_actions_expected_version CHECK (expected_version >= 0),
    CONSTRAINT ck_model_actions_actor_kind CHECK (
        actor_kind IN ('OPERATOR', 'MONITORING_AUTOMATION')
    ),
    -- Handing a model more authority is a two-person decision; the person who asks for it is not
    -- the person who grants it.
    CONSTRAINT ck_model_actions_approval CHECK (
        action_type NOT IN ('APPROVE', 'PROMOTE')
            OR (approver_reference IS NOT NULL AND approver_reference <> actor_reference)
    ),
    -- Monitoring may take a model out of the path the moment a threshold breaks, and that is the
    -- whole point of a kill switch. It may never put one in. Automation that can promote is a
    -- release process with no human in it, discovered later by whoever is on call.
    CONSTRAINT ck_model_actions_automation CHECK (
        actor_kind <> 'MONITORING_AUTOMATION'
            OR (action_type IN ('PAUSE', 'ROLLBACK') AND triggering_evaluation_id IS NOT NULL)
    ),
    CONSTRAINT ck_model_actions_traffic_share CHECK (
        traffic_share IS NULL OR (traffic_share > 0 AND traffic_share <= 1)
    ),
    CONSTRAINT ck_model_actions_promote_scope CHECK (
        action_type <> 'PROMOTE' OR model_release_route_id IS NOT NULL
    ),
    CONSTRAINT ck_model_actions_applied CHECK (
        (applied AND applied_at IS NOT NULL) OR (NOT applied AND applied_at IS NULL)
    ),
    CONSTRAINT ck_model_actions_failure CHECK (NOT applied OR failure_reason IS NULL)
);

CREATE INDEX idx_model_actions_model ON model_actions (model_version_id, requested_at);
--rollback DROP TABLE model_actions;

--changeset ninggiangboy:031-17-decision-references
CREATE TABLE decision_references (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_domain        VARCHAR(32) NOT NULL,
    decision_reference     VARCHAR(64) NOT NULL,
    decision_kind          VARCHAR(48) NOT NULL,
    policy_version         VARCHAR(64) NOT NULL,
    factual_snapshot_digest CHAR(64),
    prediction_record_id   UUID,
    fallback_reason        VARCHAR(48),
    experiment_exposure_id UUID,
    recommended_action     VARCHAR(48),
    selected_action        VARCHAR(48) NOT NULL,
    policy_override_applied BOOLEAN NOT NULL DEFAULT false,
    constraint_results     VARCHAR(500),
    reason_codes           VARCHAR(300),
    actor_kind             VARCHAR(24) NOT NULL,
    correlation_id         VARCHAR(64),
    decided_at             TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL,
    -- The decision itself belongs to the domain that made it. This row is an index for monitoring
    -- and replay, and it carries the domain's own identifier rather than a copy of its state, so
    -- that nothing here can drift away from -- or be mistaken for -- the authoritative record.
    CONSTRAINT uk_decision_references_decision UNIQUE (decision_domain, decision_reference),
    CONSTRAINT fk_decision_references_prediction FOREIGN KEY (prediction_record_id)
        REFERENCES prediction_records (id),
    CONSTRAINT fk_decision_references_exposure FOREIGN KEY (experiment_exposure_id)
        REFERENCES experiment_exposures (id),
    CONSTRAINT ck_decision_references_domain CHECK (
        decision_domain IN ('DISCOVERY', 'PRICING', 'BOOKING', 'PAYMENT', 'RISK', 'MODERATION',
                            'SUPPORT', 'MESSAGING', 'REVIEW', 'PAYOUT')
    ),
    CONSTRAINT ck_decision_references_actor_kind CHECK (
        actor_kind IN ('SYSTEM', 'OPERATOR', 'GUEST', 'HOST')
    ),
    -- Every decision either used a prediction or did not, and the one that did not has to say why.
    -- An absent reference reads as "no model was involved" and an unrecorded fallback reads the
    -- same way, which is how a week of model outage becomes a week of apparently normal decisions.
    -- Both columns together is the ordinary case for a degraded request: the record of what was
    -- asked is kept, and the reason the answer could not be used is kept beside it.
    CONSTRAINT ck_decision_references_prediction_or_fallback CHECK (
        prediction_record_id IS NOT NULL OR fallback_reason IS NOT NULL
    ),
    -- A model is not credited for a decision the policy overrode. If the policy changed the
    -- outcome, the recommendation has to be recorded alongside what was actually done, because the
    -- difference between them is the measurement -- of the policy, and of the model.
    CONSTRAINT ck_decision_references_override CHECK (
        NOT policy_override_applied
            OR (recommended_action IS NOT NULL AND recommended_action <> selected_action)
    )
);

CREATE INDEX idx_decision_references_prediction
    ON decision_references (prediction_record_id) WHERE prediction_record_id IS NOT NULL;
CREATE INDEX idx_decision_references_decided ON decision_references (decision_domain, decided_at);
--rollback DROP TABLE decision_references;

--changeset ninggiangboy:031-18-platform-append-only splitStatements:false
-- The same rule as 030, applied to the evidence this migration produces. A feature value, a label
-- observation, an approval, an evaluation, a prediction and a decision reference are all records
-- of something that happened. Editing one destroys the only account of it, and the account is
-- precisely what an investigation after a bad decision has to read. The trigger function itself is
-- the one created in 030; only the triggers are new. DELETE stays available throughout, because
-- retention and erasure must be able to remove rows nobody may rewrite.
CREATE TRIGGER trg_offline_feature_values_append_only
    BEFORE UPDATE ON offline_feature_values
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_feature_set_members_append_only
    BEFORE UPDATE ON feature_set_members
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_feature_invalidations_append_only
    BEFORE UPDATE ON feature_invalidations
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_label_observations_append_only
    BEFORE UPDATE ON label_observations
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_model_approvals_append_only
    BEFORE UPDATE ON model_approvals
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_model_evaluation_runs_append_only
    BEFORE UPDATE ON model_evaluation_runs
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

-- A slice may be released once the cohort is large enough to report, and nothing else about a
-- measured result may move.
CREATE TRIGGER trg_model_evaluation_slices_append_only
    BEFORE UPDATE ON model_evaluation_slices
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('released');

CREATE TRIGGER trg_prediction_records_append_only
    BEFORE UPDATE ON prediction_records
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('retention_class', 'expires_at');

CREATE TRIGGER trg_model_actions_append_only
    BEFORE UPDATE ON model_actions
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only('applied', 'applied_at', 'failure_reason');

CREATE TRIGGER trg_decision_references_append_only
    BEFORE UPDATE ON decision_references
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();
--rollback DROP TRIGGER trg_decision_references_append_only ON decision_references;
--rollback DROP TRIGGER trg_model_actions_append_only ON model_actions;
--rollback DROP TRIGGER trg_prediction_records_append_only ON prediction_records;
--rollback DROP TRIGGER trg_model_evaluation_slices_append_only ON model_evaluation_slices;
--rollback DROP TRIGGER trg_model_evaluation_runs_append_only ON model_evaluation_runs;
--rollback DROP TRIGGER trg_model_approvals_append_only ON model_approvals;
--rollback DROP TRIGGER trg_label_observations_append_only ON label_observations;
--rollback DROP TRIGGER trg_feature_invalidations_append_only ON feature_invalidations;
--rollback DROP TRIGGER trg_feature_set_members_append_only ON feature_set_members;
--rollback DROP TRIGGER trg_offline_feature_values_append_only ON offline_feature_values;

--changeset ninggiangboy:031-19-definition-registry-immutability splitStatements:false
-- A feature definition, a label definition and a feature set version are contracts in exactly the
-- sense 030 established: once they leave DRAFT, models have been trained against them and values
-- have been written under them, so changing what they mean rewrites the meaning of data that
-- already exists. The lifecycle columns may move the row forward through review, activation,
-- deprecation and retirement; everything else is compared as JSON and refused. The function is the
-- one from 030, reused so that both halves of the platform freeze contracts the same way.
CREATE TRIGGER trg_feature_definitions_freeze
    BEFORE UPDATE OR DELETE ON feature_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'reviewed_at', 'activated_at', 'deprecated_at', 'retired_at', 'replaced_by_id',
        'business_owner', 'technical_steward', 'prohibited_consumers', 'skew_threshold',
        'validation_test_reference', 'updated_at', 'version');

CREATE TRIGGER trg_label_definitions_freeze
    BEFORE UPDATE OR DELETE ON label_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'reviewed_at', 'activated_at', 'deprecated_at', 'retired_at',
        'quality_owner', 'adjudication_process', 'known_biases', 'updated_at', 'version');

CREATE TRIGGER trg_feature_set_versions_freeze
    BEFORE UPDATE OR DELETE ON feature_set_versions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'frozen_at', 'retired_at', 'business_owner', 'updated_at', 'version');
--rollback DROP TRIGGER trg_feature_set_versions_freeze ON feature_set_versions;
--rollback DROP TRIGGER trg_label_definitions_freeze ON label_definitions;
--rollback DROP TRIGGER trg_feature_definitions_freeze ON feature_definitions;

--changeset ninggiangboy:031-20-feature-set-membership splitStatements:false
-- The membership is the set. A model version names a set version and is thereby understood to read
-- exactly those features in exactly those versions; adding one afterwards means the model was
-- evaluated on one input list and served on another, and nothing in the evaluation would show it.
-- So membership is frozen when the set leaves DRAFT, and the set cannot leave DRAFT empty.
CREATE FUNCTION feature_set_membership_seal() RETURNS TRIGGER AS $$
DECLARE
    set_status TEXT;
    set_kind TEXT;
    member_kind TEXT;
BEGIN
    SELECT status, entity_kind INTO set_status, set_kind
    FROM feature_set_versions
    WHERE id = COALESCE(NEW.feature_set_version_id, OLD.feature_set_version_id);

    IF set_status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'feature set version % is % and its membership is the contract a registered model was '
            'evaluated against; publish a new set version instead',
            COALESCE(NEW.feature_set_version_id, OLD.feature_set_version_id), set_status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP <> 'DELETE' THEN
        SELECT entity_kind INTO member_kind
        FROM feature_definitions
        WHERE id = NEW.feature_definition_id;

        -- A set keyed by listing cannot contain a guest-keyed feature: there is no join that makes
        -- the lookup well defined, and whatever the serving path returns for it is an accident.
        IF member_kind <> set_kind THEN
            RAISE EXCEPTION
                'feature set version % is keyed by % and cannot contain a %-keyed feature',
                NEW.feature_set_version_id, set_kind, member_kind
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_feature_set_members_seal
    BEFORE INSERT OR UPDATE OR DELETE ON feature_set_members
    FOR EACH ROW
    EXECUTE FUNCTION feature_set_membership_seal();

-- Members reference the set, so they cannot exist at the instant it is inserted. A set created
-- already frozen would therefore be an empty contract that no member can ever be added to -- and
-- since a model may name it, an empty feature set is a model reading nothing at all.
CREATE FUNCTION feature_set_freeze_requirements() RETURNS TRIGGER AS $$
DECLARE
    member_count INTEGER;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'a feature set version is created in draft and frozen once its members exist; it may '
            'not be inserted already %', NEW.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.status = 'DRAFT' AND NEW.status <> 'DRAFT' THEN
        SELECT count(*) INTO member_count
        FROM feature_set_members
        WHERE feature_set_version_id = NEW.id;

        IF member_count = 0 THEN
            RAISE EXCEPTION
                'feature set version % has no members; freezing it would register a model input '
                'list that is empty', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_feature_set_versions_freeze_requirements
    BEFORE INSERT OR UPDATE ON feature_set_versions
    FOR EACH ROW
    EXECUTE FUNCTION feature_set_freeze_requirements();
--rollback DROP TRIGGER trg_feature_set_versions_freeze_requirements ON feature_set_versions;
--rollback DROP FUNCTION feature_set_freeze_requirements();
--rollback DROP TRIGGER trg_feature_set_members_seal ON feature_set_members;
--rollback DROP FUNCTION feature_set_membership_seal();

--changeset ninggiangboy:031-21-feature-value-integrity splitStatements:false
-- A feature value has to agree with the definition it claims to be an instance of. Three ways it
-- can fail to: the definition is not in service, the value is keyed by the wrong kind of entity, or
-- the value does not have the declared type. All three produce a store that answers lookups with
-- something -- which is worse than answering with nothing, because the model consumes it silently.
CREATE FUNCTION feature_value_integrity() RETURNS TRIGGER AS $$
DECLARE
    definition RECORD;
    expected_column TEXT;
BEGIN
    SELECT feature_key, entity_kind, value_type, allowed_categories, status,
           available_online_serving, available_offline
      INTO definition
    FROM feature_definitions
    WHERE id = NEW.feature_definition_id;

    IF definition.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
        RAISE EXCEPTION
            'feature % is %; values may be written only against a definition that is in service',
            definition.feature_key, definition.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.entity_kind <> definition.entity_kind THEN
        RAISE EXCEPTION
            'feature % is keyed by % but the value is keyed by %',
            definition.feature_key, definition.entity_kind, NEW.entity_kind
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_TABLE_NAME = 'online_feature_values' AND NOT definition.available_online_serving THEN
        RAISE EXCEPTION
            'feature % is not declared available for online serving; declare the availability and '
            'the parity test rather than writing into the serving store',
            definition.feature_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_TABLE_NAME = 'offline_feature_values' AND NOT definition.available_offline THEN
        RAISE EXCEPTION
            'feature % is not declared available offline', definition.feature_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- Nothing to type-check about an absent value, and an imputed one is typed like a present one.
    IF NEW.value_state IN ('MISSING', 'SUPPRESSED') THEN
        RETURN NEW;
    END IF;

    expected_column := CASE definition.value_type
        WHEN 'INTEGER' THEN 'value_numeric'
        WHEN 'DECIMAL' THEN 'value_numeric'
        WHEN 'EMBEDDING' THEN 'value_reference'
        ELSE 'value_text'
    END;

    IF (expected_column = 'value_numeric' AND NEW.value_numeric IS NULL)
            OR (expected_column = 'value_text' AND NEW.value_text IS NULL)
            OR (expected_column = 'value_reference' AND NEW.value_reference IS NULL) THEN
        RAISE EXCEPTION
            'feature % is declared % and its value must be stored in %',
            definition.feature_key, definition.value_type, expected_column
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- An unreviewed category arriving as a new level is how a model meets an input it has never
    -- seen and produces a confident number about it. The registry declared the levels; this is
    -- where that declaration becomes true rather than aspirational.
    IF definition.value_type = 'CATEGORICAL'
            AND NOT (NEW.value_text
                = ANY (string_to_array(definition.allowed_categories, ','))) THEN
        RAISE EXCEPTION
            'value % is not one of the categories declared for feature %',
            NEW.value_text, definition.feature_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_offline_feature_values_integrity
    BEFORE INSERT ON offline_feature_values
    FOR EACH ROW
    EXECUTE FUNCTION feature_value_integrity();

CREATE TRIGGER trg_online_feature_values_integrity
    BEFORE INSERT OR UPDATE ON online_feature_values
    FOR EACH ROW
    EXECUTE FUNCTION feature_value_integrity();

-- The online store is a projection of ordered source events, and events do not arrive in order. A
-- late message carrying an older state must not overwrite a newer projection, or the served value
-- oscillates and no amount of monitoring explains why. A declared historical rebuild is the one
-- case where writing an older state is the intent rather than the accident.
CREATE FUNCTION online_feature_value_monotonicity() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.historical_rebuild THEN
        RETURN NEW;
    END IF;

    IF (NEW.source_event_time, NEW.source_sequence) < (OLD.source_event_time, OLD.source_sequence)
    THEN
        RAISE EXCEPTION
            'online feature value % is at source time %/% and may not be overwritten by an older '
            'input at %/%; mark the write as a historical rebuild if that is the intent',
            OLD.id, OLD.source_event_time, OLD.source_sequence,
            NEW.source_event_time, NEW.source_sequence
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_online_feature_values_monotonicity
    BEFORE UPDATE ON online_feature_values
    FOR EACH ROW
    EXECUTE FUNCTION online_feature_value_monotonicity();
--rollback DROP TRIGGER trg_online_feature_values_monotonicity ON online_feature_values;
--rollback DROP FUNCTION online_feature_value_monotonicity();
--rollback DROP TRIGGER trg_online_feature_values_integrity ON online_feature_values;
--rollback DROP TRIGGER trg_offline_feature_values_integrity ON offline_feature_values;
--rollback DROP FUNCTION feature_value_integrity();

--changeset ninggiangboy:031-22-label-observation-integrity splitStatements:false
-- The label definition owns the horizon and the maturity delay; the observation must be consistent
-- with both. The row-level checks already refuse an answer dated before its horizon closed, but the
-- horizon itself comes from the definition, so the arithmetic happens here.
CREATE FUNCTION label_observation_integrity() RETURNS TRIGGER AS $$
DECLARE
    definition RECORD;
BEGIN
    SELECT label_key, entity_kind, horizon, maturity_delay, status, selection_policy_required
      INTO definition
    FROM label_definitions
    WHERE id = NEW.label_definition_id;

    IF definition.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
        RAISE EXCEPTION
            'label % is %; observations may be recorded only against a definition in service',
            definition.label_key, definition.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.entity_kind <> definition.entity_kind THEN
        RAISE EXCEPTION
            'label % is keyed by % but the observation is keyed by %',
            definition.label_key, definition.entity_kind, NEW.entity_kind
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- The horizon is the definition's, not the job's. A pipeline that computes its own end instant
    -- is a second implementation of the contract, and the two drift the first time either changes.
    IF NEW.horizon_ends_at <> NEW.prediction_at + definition.horizon THEN
        RAISE EXCEPTION
            'label % has horizon % from %, which ends at %, not at %',
            definition.label_key, definition.horizon, NEW.prediction_at,
            NEW.prediction_at + definition.horizon, NEW.horizon_ends_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- An outcome that can be reversed on appeal or corrected downstream is not settled the instant
    -- the window closes. Reading it at that instant records the provisional answer as the final
    -- one, and the correction that follows arrives after the model has already trained on it.
    IF NEW.matured_at IS NOT NULL
            AND NEW.matured_at < NEW.horizon_ends_at + definition.maturity_delay THEN
        RAISE EXCEPTION
            'label % matures % after its horizon ends; % is too early',
            definition.label_key, definition.maturity_delay, NEW.matured_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF definition.selection_policy_required AND NEW.selection_policy_version IS NULL THEN
        RAISE EXCEPTION
            'label % admits selection bias, so every observation must name the policy that caused '
            'this example to be observed at all', definition.label_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_label_observations_integrity
    BEFORE INSERT ON label_observations
    FOR EACH ROW
    EXECUTE FUNCTION label_observation_integrity();
--rollback DROP TRIGGER trg_label_observations_integrity ON label_observations;
--rollback DROP FUNCTION label_observation_integrity();

--changeset ninggiangboy:031-23-manifest-integrity splitStatements:false
-- A manifest is a claim that a dataset was built a particular way at a particular moment, and the
-- claim is only worth something if it cannot be edited afterwards. What may still move is whether
-- the dataset is usable for a new build: a later erasure or correction invalidates it for reuse
-- without touching the artifact, so a model already released against it stays reproducible.
CREATE FUNCTION training_manifest_integrity() RETURNS TRIGGER AS $$
DECLARE
    label RECORD;
    set_status TEXT;
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT label_key, horizon, maturity_delay, status INTO label
        FROM label_definitions
        WHERE id = NEW.label_definition_id;

        IF label.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
            RAISE EXCEPTION
                'label % is %; a dataset may not be built against a definition that is not in '
                'service', label.label_key, label.status
                USING ERRCODE = 'restrict_violation';
        END IF;

        SELECT status INTO set_status
        FROM feature_set_versions
        WHERE id = NEW.feature_set_version_id;

        -- A dataset built from a set whose membership can still change is not reproducible, and
        -- the manifest's whole purpose is to be reproducible.
        IF set_status = 'DRAFT' THEN
            RAISE EXCEPTION
                'feature set version % is still draft; freeze the membership before building a '
                'dataset against it', NEW.feature_set_version_id
                USING ERRCODE = 'restrict_violation';
        END IF;

        -- The last example predicted at the feature cutoff does not have an answer until its
        -- horizon has run and the outcome has settled. A label cutoff earlier than that means part
        -- of the dataset is censored -- which is allowed, but only if the build says so and
        -- handles it. Silently treating a not-yet-matured outcome as a negative teaches the model
        -- that slow outcomes do not happen.
        IF NOT NEW.censoring_modelled
                AND NEW.label_cutoff_at
                    < NEW.feature_cutoff_at + label.horizon + label.maturity_delay THEN
            RAISE EXCEPTION
                'label % needs % plus % after the feature cutoff at % to mature, so a label cutoff '
                'at % leaves examples unresolved; extend the cutoff or declare censoring modelled',
                label.label_key, label.horizon, label.maturity_delay, NEW.feature_cutoff_at,
                NEW.label_cutoff_at
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.reuse_state <> 'REUSABLE' THEN
            RAISE EXCEPTION
                'a manifest is recorded as reusable and invalidated later by an event that makes '
                'it unusable; it may not be inserted already %', NEW.reuse_state
                USING ERRCODE = 'restrict_violation';
        END IF;

        RETURN NEW;
    END IF;

    old_state := to_jsonb(OLD);
    new_state := to_jsonb(NEW);
    FOREACH permitted IN ARRAY ARRAY[
        'reuse_state', 'invalidated_at', 'invalidation_reason', 'updated_at', 'version'
    ] LOOP
        old_state := old_state - permitted;
        new_state := new_state - permitted;
    END LOOP;

    IF new_state IS DISTINCT FROM old_state THEN
        RAISE EXCEPTION
            'manifest % describes a dataset that was already built; build a new version rather '
            'than restating what this one was', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- Once a deletion or a correction has made a dataset unusable, it does not become usable again
    -- because the job that noticed ran twice.
    IF OLD.reuse_state <> 'REUSABLE' AND NEW.reuse_state = 'REUSABLE' THEN
        RAISE EXCEPTION
            'manifest % was invalidated (%); build a new dataset rather than returning this one '
            'to service', OLD.id, OLD.reuse_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_training_dataset_manifests_integrity
    BEFORE INSERT OR UPDATE ON training_dataset_manifests
    FOR EACH ROW
    EXECUTE FUNCTION training_manifest_integrity();
--rollback DROP TRIGGER trg_training_dataset_manifests_integrity ON training_dataset_manifests;
--rollback DROP FUNCTION training_manifest_integrity();

--changeset ninggiangboy:031-24-model-version-immutability splitStatements:false
-- Everything that says what a model is -- the artifact and its checksum, the dataset, the feature
-- set, the label, the output schema, the impact class, the decisions it is intended for and the
-- uses it is excluded from -- is frozen the moment the version leaves draft. Retuning weights or
-- changing a prompt is a new version. Widening intended_decisions in place would be worse: it
-- expands a model's authority without any of the approvals that authority required, and every
-- approval row already recorded would appear to cover the new scope.
CREATE TRIGGER trg_model_versions_freeze
    BEFORE UPDATE OR DELETE ON model_versions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'model_card_reference', 'monitoring_reference', 'retraining_trigger',
        'retirement_trigger', 'rollback_target_id', 'owner_reference', 'trained_at',
        'validated_at', 'approved_at', 'activated_at', 'retired_at', 'updated_at', 'version');
--rollback DROP TRIGGER trg_model_versions_freeze ON model_versions;

--changeset ninggiangboy:031-25-model-promotion-requirements splitStatements:false
-- The gate between "this model scores well" and "this model decides things". Evaluations and
-- approvals reference the model version, so they cannot exist at the instant it is inserted; a
-- version created already approved would skip every check below, which is why the insert is
-- refused outright rather than waved through.
--
-- What APPROVED requires: a dataset whose leakage checks passed and which is still reusable, an
-- offline evaluation against a declared baseline that did not fail, a fairness evaluation that
-- actually produced slices, and the approvals the impact class calls for -- none of them signed by
-- the model's own owner. An advisory model needs the domain that will consume it. Anything
-- touching safety, money, price, eligibility or moderation needs risk, privacy and security as
-- well, and legal or finance depending on which of those it touches.
CREATE FUNCTION model_version_promotion() RETURNS TRIGGER AS $$
DECLARE
    manifest RECORD;
    offline_runs INTEGER;
    fairness_slices INTEGER;
    required_role TEXT;
    approved_roles TEXT[];
    self_approver TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status NOT IN ('DRAFT', 'TRAINED') THEN
            RAISE EXCEPTION
                'a model version is registered as draft or trained and promoted once its '
                'evaluations and approvals exist; it may not be inserted already %', NEW.status
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.status IN ('REJECTED', 'RETIRED') AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION
            'model version % is %; register a new version rather than returning this one to '
            'service', OLD.id, OLD.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.status = 'VALIDATED' AND OLD.status <> 'VALIDATED' THEN
        SELECT leakage_checks_passed, reuse_state, dataset_key INTO manifest
        FROM training_dataset_manifests
        WHERE id = NEW.training_dataset_manifest_id;

        IF NOT manifest.leakage_checks_passed THEN
            RAISE EXCEPTION
                'dataset % has not passed its leakage checks; a model validated against it would '
                'be measuring the leak', manifest.dataset_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF manifest.reuse_state <> 'REUSABLE' THEN
            RAISE EXCEPTION
                'dataset % is % and may no longer be used for a new release',
                manifest.dataset_key, manifest.reuse_state
                USING ERRCODE = 'restrict_violation';
        END IF;

        SELECT count(*) INTO offline_runs
        FROM model_evaluation_runs
        WHERE model_version_id = NEW.id
          AND evaluation_kind = 'OFFLINE'
          AND result <> 'FAIL';

        IF offline_runs = 0 THEN
            RAISE EXCEPTION
                'model version % has no offline evaluation against a baseline that did not fail',
                NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF NEW.status = 'APPROVED' AND OLD.status <> 'APPROVED' THEN
        IF OLD.status <> 'VALIDATED' THEN
            RAISE EXCEPTION
                'model version % is %; a version is validated before it is approved',
                NEW.id, OLD.status
                USING ERRCODE = 'restrict_violation';
        END IF;

        -- An aggregate number can be excellent while one market, one language or one class of
        -- host carries every error. A fairness evaluation with no slices measured nothing.
        SELECT count(*) INTO fairness_slices
        FROM model_evaluation_slices s
        JOIN model_evaluation_runs r ON r.id = s.model_evaluation_run_id
        WHERE r.model_version_id = NEW.id
          AND r.evaluation_kind = 'FAIRNESS';

        IF fairness_slices = 0 THEN
            RAISE EXCEPTION
                'model version % has no fairness evaluation slices; an aggregate result cannot '
                'show which group is carrying the error', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        SELECT array_agg(approval_role), max(approver_reference) FILTER (
                   WHERE approver_reference = NEW.owner_reference)
          INTO approved_roles, self_approver
        FROM model_approvals
        WHERE model_version_id = NEW.id
          AND decision = 'APPROVED';

        -- An owner who can sign their own expansion of authority is the whole control, absent.
        IF self_approver IS NOT NULL THEN
            RAISE EXCEPTION
                'model version % is owned by % and cannot be approved by the same person',
                NEW.id, NEW.owner_reference
                USING ERRCODE = 'restrict_violation';
        END IF;

        FOREACH required_role IN ARRAY (
            CASE
                WHEN NEW.impact_class = 'ADVISORY' THEN ARRAY['DOMAIN']
                WHEN NEW.impact_class IN ('FINANCIAL', 'PRICING')
                    THEN ARRAY['DOMAIN', 'RISK', 'PRIVACY', 'SECURITY', 'FINANCE']
                ELSE ARRAY['DOMAIN', 'RISK', 'PRIVACY', 'SECURITY', 'LEGAL']
            END
        ) LOOP
            IF approved_roles IS NULL OR NOT (required_role = ANY (approved_roles)) THEN
                RAISE EXCEPTION
                    'model version % has impact class % and requires a % approval',
                    NEW.id, NEW.impact_class, required_role
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END LOOP;
    END IF;

    -- Shadow, canary and active are routing states. Only an approved version reaches them, which
    -- is what makes "only approved versions may enter shadow or canary" a fact rather than a rule
    -- somebody follows.
    IF NEW.status IN ('SHADOW', 'CANARY', 'ACTIVE')
            AND OLD.status NOT IN ('APPROVED', 'SHADOW', 'CANARY', 'ACTIVE') THEN
        RAISE EXCEPTION
            'model version % is % and may not be routed traffic before it is approved',
            NEW.id, OLD.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_model_versions_promotion
    BEFORE INSERT OR UPDATE ON model_versions
    FOR EACH ROW
    EXECUTE FUNCTION model_version_promotion();
--rollback DROP TRIGGER trg_model_versions_promotion ON model_versions;
--rollback DROP FUNCTION model_version_promotion();

--changeset ninggiangboy:031-26-route-integrity splitStatements:false
-- Routing is the only place a model becomes live, which makes it the only place that has to be
-- guarded and the only thing a rollback changes. A retired, rejected or rolled-back version may
-- not be routed traffic in any mode, an active route must point at a version that is approved or
-- already active, and a challenger split must name an epoch that is actually running -- otherwise
-- the traffic is being divided without anybody measuring the result, which is the expensive way of
-- running no experiment at all.
CREATE FUNCTION model_release_route_integrity() RETURNS TRIGGER AS $$
DECLARE
    champion RECORD;
    challenger RECORD;
    epoch_state TEXT;
BEGIN
    SELECT model_key, model_version, status INTO champion
    FROM model_versions
    WHERE id = NEW.champion_model_version_id;

    IF champion.status IN ('DRAFT', 'TRAINED', 'VALIDATED', 'REJECTED', 'RETIRED', 'ROLLED_BACK')
    THEN
        RAISE EXCEPTION
            'model % % is % and may not be routed traffic',
            champion.model_key, champion.model_version, champion.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.route_mode = 'ACTIVE' AND champion.status NOT IN ('APPROVED', 'ACTIVE') THEN
        RAISE EXCEPTION
            'model % % is % and cannot be the champion of an active route',
            champion.model_key, champion.model_version, champion.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.challenger_model_version_id IS NOT NULL THEN
        SELECT model_key, model_version, status INTO challenger
        FROM model_versions
        WHERE id = NEW.challenger_model_version_id;

        IF challenger.status IN ('DRAFT', 'TRAINED', 'VALIDATED', 'REJECTED', 'RETIRED',
                                 'ROLLED_BACK') THEN
            RAISE EXCEPTION
                'challenger % % is % and may not be routed traffic',
                challenger.model_key, challenger.model_version, challenger.status
                USING ERRCODE = 'restrict_violation';
        END IF;

        SELECT state INTO epoch_state
        FROM experiment_epochs
        WHERE id = NEW.experiment_epoch_id;

        IF epoch_state NOT IN ('SCHEDULED', 'RUNNING') THEN
            RAISE EXCEPTION
                'epoch % is % and cannot carry a champion/challenger split; a split nobody is '
                'analysing is a traffic change, not a comparison',
                NEW.experiment_epoch_id, epoch_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    -- A rollback target that is itself out of service is a rollback that fails at the moment it is
    -- needed, which is the moment nobody has time to discover it.
    IF NEW.rollback_target_version_id IS NOT NULL
            AND EXISTS (SELECT 1 FROM model_versions
                        WHERE id = NEW.rollback_target_version_id
                          AND status IN ('DRAFT', 'TRAINED', 'VALIDATED', 'REJECTED',
                                         'RETIRED')) THEN
        RAISE EXCEPTION
            'rollback target % is not in a state that could take traffic',
            NEW.rollback_target_version_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_model_release_routes_integrity
    BEFORE INSERT OR UPDATE ON model_release_routes
    FOR EACH ROW
    EXECUTE FUNCTION model_release_route_integrity();
--rollback DROP TRIGGER trg_model_release_routes_integrity ON model_release_routes;
--rollback DROP FUNCTION model_release_route_integrity();

--changeset ninggiangboy:031-27-prediction-integrity splitStatements:false
-- Two things a prediction must not be able to claim. The first is that a retired model produced
-- it: retirement blocks new predictions, and a consumer that kept calling anyway has to fail
-- loudly rather than accumulate scores from a model whose assumptions were declared invalid. The
-- second is that a model read features it never declared -- the registered feature set version is
-- the input contract the evaluation and the approvals were about, and a prediction resolved
-- against a different set is a different model wearing this one's version number.
CREATE FUNCTION prediction_record_integrity() RETURNS TRIGGER AS $$
DECLARE
    model RECORD;
    route RECORD;
BEGIN
    SELECT model_key, model_version, status, feature_set_version_id INTO model
    FROM model_versions
    WHERE id = NEW.model_version_id;

    IF model.status NOT IN ('APPROVED', 'SHADOW', 'CANARY', 'ACTIVE') THEN
        RAISE EXCEPTION
            'model % % is % and may not produce predictions',
            model.model_key, model.model_version, model.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.feature_set_version_id <> model.feature_set_version_id THEN
        RAISE EXCEPTION
            'model % % is registered against feature set version % and may not be served from %',
            model.model_key, model.model_version, model.feature_set_version_id,
            NEW.feature_set_version_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.model_release_route_id IS NOT NULL THEN
        SELECT consumer, champion_model_version_id, challenger_model_version_id INTO route
        FROM model_release_routes
        WHERE id = NEW.model_release_route_id;

        IF route.consumer <> NEW.consumer THEN
            RAISE EXCEPTION
                'route % serves consumer % but the prediction was recorded for %',
                NEW.model_release_route_id, route.consumer, NEW.consumer
                USING ERRCODE = 'restrict_violation';
        END IF;

        -- The route that was resolved is pinned once per request and recorded. A prediction whose
        -- model is on neither side of its own route means the resolution and the record disagree,
        -- and the analysis that joins them afterwards would attribute the outcome to the wrong arm.
        IF NEW.model_version_id NOT IN (route.champion_model_version_id,
                                        COALESCE(route.challenger_model_version_id,
                                                 route.champion_model_version_id)) THEN
            RAISE EXCEPTION
                'model version % is neither the champion nor the challenger of route %',
                NEW.model_version_id, NEW.model_release_route_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prediction_records_integrity
    BEFORE INSERT ON prediction_records
    FOR EACH ROW
    EXECUTE FUNCTION prediction_record_integrity();
--rollback DROP TRIGGER trg_prediction_records_integrity ON prediction_records;
--rollback DROP FUNCTION prediction_record_integrity();

--changeset ninggiangboy:031-28-decision-reference-coherence splitStatements:false
-- A decision may cite a prediction it could actually have had. Citing one made after the decision
-- is a join error; citing one that had already expired is a consumer serving a stale score past
-- the validity the model declared for it, which is exactly the case an evaluation needs to see
-- separated from a fresh prediction rather than averaged in with it.
CREATE FUNCTION decision_reference_coherence() RETURNS TRIGGER AS $$
DECLARE
    prediction RECORD;
BEGIN
    IF NEW.prediction_record_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT predicted_at, expires_at, status, experiment_exposure_id INTO prediction
    FROM prediction_records
    WHERE id = NEW.prediction_record_id;

    IF NEW.decided_at < prediction.predicted_at THEN
        RAISE EXCEPTION
            'decision % is dated % and cannot have used a prediction made at %',
            NEW.decision_reference, NEW.decided_at, prediction.predicted_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.decided_at >= prediction.expires_at THEN
        RAISE EXCEPTION
            'prediction % expired at % and may not be cited by a decision made at %',
            NEW.prediction_record_id, prediction.expires_at, NEW.decided_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- The referenced record is a fallback rather than a prediction, so the decision may not
    -- present it as a score that was used. It has to carry the reason, for the same purpose an
    -- experiment exposure carries one: otherwise a week of model outage reads as a week of normal
    -- model-assisted decisions, and the harm the fallback caused is attributed to the model.
    IF prediction.status <> 'PREDICTED' AND NEW.fallback_reason IS NULL THEN
        RAISE EXCEPTION
            'prediction % is % rather than a prediction; the decision must record the fallback',
            NEW.prediction_record_id, prediction.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF prediction.experiment_exposure_id IS NOT NULL
            AND NEW.experiment_exposure_id IS DISTINCT FROM prediction.experiment_exposure_id THEN
        RAISE EXCEPTION
            'prediction % was made under exposure % and the decision cites %',
            NEW.prediction_record_id, prediction.experiment_exposure_id,
            NEW.experiment_exposure_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_decision_references_coherence
    BEFORE INSERT ON decision_references
    FOR EACH ROW
    EXECUTE FUNCTION decision_reference_coherence();
--rollback DROP TRIGGER trg_decision_references_coherence ON decision_references;
--rollback DROP FUNCTION decision_reference_coherence();
