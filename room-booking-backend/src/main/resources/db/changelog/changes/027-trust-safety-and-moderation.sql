--liquibase formatted sql

-- A score is not a decision, and a decision is not enforcement. This migration exists so that the
-- three stay separable rows: what was observed, what was decided under which version of which rule,
-- and which authoritative domain command actually honoured that decision. Collapsing any two of them
-- is how a protection system becomes unauditable and unappealable.
--
-- Six things force the shape:
--
--   Risk advises; domains enforce. Nothing here writes inventory, money, sessions or publication.
--   A decision is recorded, and a separate enforcement row says which domain command consumed it --
--   so "this booking was denied" and "this booking was never created" are different claims with
--   different evidence. The enforcement row is refused if it claims to have proceeded on a decision
--   that denied, or if it arrives after the decision expired.
--
--   One action has one effective decision. The canonical evaluation identity is unique across
--   action, actor, resource, command and policy epoch, so a retry replays the recorded decision
--   rather than producing a second one. Re-evaluation is a new row naming its predecessor; the
--   original outcome is frozen by trigger.
--
--   Evidence is immutable; interpretation is versioned. Signals, feature snapshots, model
--   predictions, rule hits, challenge attempts, content revisions, detector assessments and access
--   audit rows are append-only -- covering INSERT on children of anything that can freeze, which is
--   the defect migration 022 found when a balanced pair of postings could be appended to a posted
--   transaction. A correction is a new row that names the row it corrects.
--
--   Unknown is not safe. Missing facts are a recorded state, not an absence. A decision that allows
--   while mandatory facts were missing must name the registered fallback it used, a prediction with
--   no score must say which fallback served it, and a signal whose provenance is a single party's
--   allegation may not carry a verified confidence class.
--
--   Time bounds every temporary intervention. A hold needs a deadline, a quarantine needs a review
--   date, a challenge needs an expiry and an attempt ceiling, and a restriction is either permanent
--   by declaration or carries an end or a review date. An active restriction's end may never move
--   later, because a late expiry worker extending a restriction is indistinguishable from a new
--   punishment nobody decided.
--
--   Relationship does not prove culpability, and a protected attribute is not a feature. An entity
--   link may only be used for an adverse decision with corroboration behind it; a feature derived
--   from a protected attribute needs a named permitted purpose and a legal review reference; and a
--   feature kept for fairness auditing may not also be an operational decision input, which is the
--   access separation the feature document requires made structural rather than procedural.
--
-- Note on what this migration does not create. The document proposes risk outbox, inbox and audit
-- primitives. Migration 012 delivered outbox_events, consumer_inbox_receipts, command_idempotency_
-- records and append-only audit_events; a second copy of any of them would mean two publishers to
-- operate. risk_access_audit is not a duplicate of audit_events: it records reads and exports of
-- protected risk evidence, which the general audit stream deliberately does not carry.
--
-- Note on restrictions. Migration 014 owns capability_restrictions, which is what authorization
-- evaluates on every request. risk_restrictions is the governed intervention behind it -- reason,
-- policy, decision, appeal, review date, enforcement mode -- and names the capability_restrictions
-- row that identity wrote to honour it. Risk does not write that row itself.
--
-- Note on supersession. Three lineages here -- restrictions, moderation decisions and labels --
-- pair a "one live row" index with a self-referencing successor pointer, which means the outgoing row
-- must name its replacement before that replacement exists. Their foreign keys are DEFERRABLE
-- INITIALLY DEFERRED so the pair commits together; probing found this the same way migration 025 did,
-- by trying to replace a row and discovering there was no order in which it could be done.
--
-- Note on secrets. No raw credential, document, token, bank detail, message body or full device
-- identifier is stored here. Content lives in its owning domain or in protected storage; this domain
-- keeps stable references, digests, span maps and decisions.

--changeset ninggiangboy:027-01-risk-subjects
-- One stable typed handle for anything that can be assessed. Without it every risk row would carry a
-- polymorphic pair and no two tables would agree on how to spell the same actor.
CREATE TABLE risk_subjects (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type            VARCHAR(24) NOT NULL,
    source_id               VARCHAR(128) NOT NULL,
    account_holder_id       UUID,
    display_reference       VARCHAR(128),
    market_id               UUID,
    first_seen_at           TIMESTAMPTZ NOT NULL,
    last_seen_at            TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_subjects_account_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_subjects_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_risk_subjects_type CHECK (
        subject_type IN ('ACCOUNT', 'ORGANIZATION', 'LISTING', 'PROPERTY', 'BOOKING', 'CONTENT',
                         'PAYMENT_INSTRUMENT', 'PAYOUT_DESTINATION', 'DEVICE', 'NETWORK',
                         'PROMOTION', 'CONVERSATION', 'REVIEW')
    ),
    -- An account subject resolves to a real account holder; the rest are domain identifiers this
    -- domain does not own and must not pretend to.
    CONSTRAINT ck_risk_subjects_account_link CHECK (
        subject_type <> 'ACCOUNT' OR account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_risk_subjects_seen CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT ck_risk_subjects_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_risk_subjects_identity ON risk_subjects (subject_type, source_id);

CREATE INDEX idx_risk_subjects_account ON risk_subjects (account_holder_id)
    WHERE account_holder_id IS NOT NULL;
--rollback DROP TABLE risk_subjects;

--changeset ninggiangboy:027-02-risk-protected-actions
-- The registry of what may be evaluated at all. The feature document is explicit that a generic risk
-- endpoint must not accept an action name invented by a client, and that every action declares its
-- latency budget, permissible outcomes, failure mode and appeal path in advance rather than in the
-- code that happens to call it.
CREATE TABLE risk_protected_actions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_key                  VARCHAR(64) NOT NULL,
    action_tier                 VARCHAR(4) NOT NULL,
    enforcement_owner_domain    VARCHAR(32) NOT NULL,
    max_decision_latency_ms     INTEGER NOT NULL,
    permitted_outcomes          TEXT[] NOT NULL,
    required_feature_max_age_s  INTEGER,
    failure_mode                VARCHAR(24) NOT NULL,
    fallback_outcome            VARCHAR(16),
    hold_limit_seconds          INTEGER,
    human_escalation_required   BOOLEAN NOT NULL DEFAULT FALSE,
    disclosure_policy           VARCHAR(24) NOT NULL,
    appeal_available            BOOLEAN NOT NULL DEFAULT TRUE,
    appeal_suppression_reason   VARCHAR(128),
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_risk_protected_actions_key UNIQUE (action_key),
    CONSTRAINT ck_risk_protected_actions_tier CHECK (action_tier IN ('T0', 'T1', 'T2', 'T3', 'T4')),
    CONSTRAINT ck_risk_protected_actions_owner CHECK (
        enforcement_owner_domain IN ('IDENTITY', 'LISTING', 'INVENTORY', 'BOOKING', 'PAYMENT',
                                     'FINANCE', 'MESSAGING', 'REVIEW', 'PROMOTION', 'SUPPORT',
                                     'STAY_OPERATIONS', 'ADMIN')
    ),
    CONSTRAINT ck_risk_protected_actions_latency CHECK (max_decision_latency_ms > 0),
    -- The six outcomes are the whole vocabulary. An action narrows that set; it cannot extend it.
    CONSTRAINT ck_risk_protected_actions_outcomes CHECK (
        cardinality(permitted_outcomes) > 0
        AND permitted_outcomes <@ ARRAY['ALLOW', 'CHALLENGE', 'HOLD', 'MANUAL_REVIEW', 'LIMIT', 'DENY']
    ),
    CONSTRAINT ck_risk_protected_actions_failure CHECK (
        failure_mode IN ('FAIL_CLOSED', 'FAIL_OPEN', 'REGISTERED_FALLBACK')
    ),
    CONSTRAINT ck_risk_protected_actions_fallback CHECK (
        (failure_mode = 'REGISTERED_FALLBACK') = (fallback_outcome IS NOT NULL)
    ),
    CONSTRAINT ck_risk_protected_actions_fallback_member CHECK (
        fallback_outcome IS NULL OR fallback_outcome = ANY (permitted_outcomes)
    ),
    -- An urgent safety action may never silently fail and always routes to a human. Writing this as
    -- a check rather than a runbook is the point: the registry cannot be edited into a state where
    -- a vendor timeout quietly approves a credible danger report.
    CONSTRAINT ck_risk_protected_actions_urgent CHECK (
        action_tier <> 'T4' OR (failure_mode <> 'FAIL_OPEN' AND human_escalation_required)
    ),
    CONSTRAINT ck_risk_protected_actions_hold CHECK (
        NOT ('HOLD' = ANY (permitted_outcomes)) OR hold_limit_seconds IS NOT NULL
    ),
    CONSTRAINT ck_risk_protected_actions_disclosure CHECK (
        disclosure_policy IN ('FULL_REASON', 'REASON_FAMILY', 'MINIMAL', 'WITHHELD_SAFETY',
                              'WITHHELD_LEGAL')
    ),
    -- No appeal path needs a written reason. The feature document allows suppression only where
    -- disclosure would create a documented safety or legal hazard, so the document is the column.
    CONSTRAINT ck_risk_protected_actions_appeal CHECK (
        appeal_available OR appeal_suppression_reason IS NOT NULL
    ),
    CONSTRAINT ck_risk_protected_actions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_risk_protected_actions_version CHECK (version >= 0)
);

CREATE INDEX idx_risk_protected_actions_active ON risk_protected_actions (action_tier)
    WHERE status = 'ACTIVE';
--rollback DROP TABLE risk_protected_actions;

--changeset ninggiangboy:027-03-risk-signals
-- An atomic observation with its provenance attached. Signals are append-only: a correction is a new
-- row naming the row it corrects, because rewriting an observation makes every decision taken on it
-- unexplainable afterwards.
CREATE TABLE risk_signals (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    signal_type                 VARCHAR(64) NOT NULL,
    schema_version              INTEGER NOT NULL,
    source_domain               VARCHAR(32) NOT NULL,
    provider_account_id         UUID,
    source_record_id            VARCHAR(128) NOT NULL,
    provenance                  VARCHAR(24) NOT NULL,
    value_type                  VARCHAR(16) NOT NULL,
    normalized_value            VARCHAR(255),
    numeric_value               NUMERIC(18, 6),
    amount_minor                BIGINT,
    currency                    VARCHAR(3),
    protected_detail_reference  VARCHAR(255),
    confidence_class            VARCHAR(24) NOT NULL,
    confidence_score            NUMERIC(5, 4),
    collection_purpose          VARCHAR(48) NOT NULL,
    market_id                   UUID,
    legal_entity_id             UUID,
    sensitivity_class           VARCHAR(16) NOT NULL,
    retention_class             VARCHAR(24) NOT NULL,
    event_time                  TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    corrects_signal_id          UUID,
    correction_reason           VARCHAR(128),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_signals_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_risk_signals_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_risk_signals_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_risk_signals_correction FOREIGN KEY (corrects_signal_id) REFERENCES risk_signals (id),
    CONSTRAINT ck_risk_signals_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_risk_signals_source_domain CHECK (
        source_domain IN ('IDENTITY', 'LISTING', 'INVENTORY', 'BOOKING', 'PAYMENT', 'FINANCE',
                          'MESSAGING', 'REVIEW', 'PROMOTION', 'STAY_OPERATIONS', 'SUPPORT',
                          'PROVIDER', 'PLATFORM', 'USER_REPORT')
    ),
    -- The evidence quality ladder, as a column. Policy reads it to decide what an observation is
    -- allowed to justify.
    CONSTRAINT ck_risk_signals_provenance CHECK (
        provenance IN ('DOMAIN_FACT', 'PROVIDER_OBSERVATION', 'CORROBORATED_OBSERVATION',
                       'DERIVED_SIGNAL', 'MODEL_INFERENCE', 'USER_ALLEGATION')
    ),
    CONSTRAINT ck_risk_signals_value_type CHECK (
        value_type IN ('BOOLEAN', 'CATEGORICAL', 'NUMERIC', 'MONEY', 'REFERENCE', 'ABSENT')
    ),
    -- An absent value is a recorded state, not a blank row, and every other value type must actually
    -- carry the shape it claims.
    CONSTRAINT ck_risk_signals_value_shape CHECK (
        (value_type = 'ABSENT'
            AND normalized_value IS NULL AND numeric_value IS NULL AND amount_minor IS NULL)
        OR (value_type IN ('BOOLEAN', 'CATEGORICAL') AND normalized_value IS NOT NULL)
        OR (value_type = 'NUMERIC' AND numeric_value IS NOT NULL)
        OR (value_type = 'MONEY' AND amount_minor IS NOT NULL AND currency IS NOT NULL)
        OR (value_type = 'REFERENCE' AND protected_detail_reference IS NOT NULL)
    ),
    CONSTRAINT ck_risk_signals_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_risk_signals_confidence_class CHECK (
        confidence_class IN ('VERIFIED', 'AUTHENTICATED', 'CORROBORATED', 'DERIVED', 'CALIBRATED',
                             'UNVERIFIED', 'UNKNOWN')
    ),
    -- An allegation is a signal, never a confirmed fact. One party saying something happened cannot
    -- be recorded at a confidence the platform reserves for facts it checked itself.
    CONSTRAINT ck_risk_signals_allegation CHECK (
        provenance <> 'USER_ALLEGATION'
            OR confidence_class IN ('UNVERIFIED', 'UNKNOWN')
    ),
    CONSTRAINT ck_risk_signals_confidence_score CHECK (
        confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1)
    ),
    CONSTRAINT ck_risk_signals_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_risk_signals_retention CHECK (
        retention_class IN ('TRANSIENT', 'SHORT_TERM', 'OPERATIONAL', 'CONTRACTUAL', 'REGULATORY',
                            'SAFETY_EVIDENCE', 'LEGAL_HOLD')
    ),
    -- Transient device and network observations expire; the feature document is explicit that they
    -- may not accumulate into a covert permanent identity.
    CONSTRAINT ck_risk_signals_transient_expiry CHECK (
        retention_class <> 'TRANSIENT' OR expires_at IS NOT NULL
    ),
    CONSTRAINT ck_risk_signals_expiry CHECK (expires_at IS NULL OR expires_at > received_at),
    CONSTRAINT ck_risk_signals_correction CHECK (
        (corrects_signal_id IS NULL) = (correction_reason IS NULL)
    ),
    CONSTRAINT ck_risk_signals_no_self_correction CHECK (
        corrects_signal_id IS NULL OR corrects_signal_id <> id
    )
);

-- Provider and domain retries deliver the same observation more than once. Dedupe identity is the
-- source that produced it, not the value, so a redelivered fact cannot double a velocity counter.
CREATE UNIQUE INDEX uk_risk_signals_source
    ON risk_signals (source_domain, provider_account_id, source_record_id)
    NULLS NOT DISTINCT;

-- Point-in-time replay reads by event time but must exclude what had not arrived yet, so both
-- instants are in the index.
CREATE INDEX idx_risk_signals_replay ON risk_signals (signal_type, event_time, received_at);
--rollback DROP TABLE risk_signals;

--changeset ninggiangboy:027-04-risk-signal-subjects
-- One observation can be about several subjects at once -- an account, a device and an instrument in
-- the same login. Keeping that as rows rather than three nullable columns is what lets a subject's
-- evidence be gathered without scanning every signal in the system.
CREATE TABLE risk_signal_subjects (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_signal_id      UUID NOT NULL,
    risk_subject_id     UUID NOT NULL,
    subject_role        VARCHAR(24) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_signal_subjects_signal FOREIGN KEY (risk_signal_id)
        REFERENCES risk_signals (id),
    CONSTRAINT fk_risk_signal_subjects_subject FOREIGN KEY (risk_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT uk_risk_signal_subjects_identity UNIQUE (risk_signal_id, risk_subject_id, subject_role),
    CONSTRAINT ck_risk_signal_subjects_role CHECK (
        subject_role IN ('PRIMARY', 'COUNTERPARTY', 'RESOURCE', 'INSTRUMENT', 'CONTEXT')
    )
);

CREATE INDEX idx_risk_signal_subjects_lookup ON risk_signal_subjects (risk_subject_id, created_at);
--rollback DROP TABLE risk_signal_subjects;

--changeset ninggiangboy:027-05-risk-feature-definitions
-- What a feature means, versioned, with its privacy approval attached. A decision replayed a year
-- later must apply the definition that was in force then, which is only possible if the definition
-- was frozen rather than edited.
CREATE TABLE risk_feature_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_key                 VARCHAR(64) NOT NULL,
    feature_version             INTEGER NOT NULL,
    value_type                  VARCHAR(16) NOT NULL,
    entity_scope                VARCHAR(24) NOT NULL,
    window_seconds              INTEGER,
    event_time_rule             VARCHAR(24) NOT NULL,
    missing_value_semantics     VARCHAR(24),
    transformation_reference    VARCHAR(255) NOT NULL,
    source_signal_types         TEXT[] NOT NULL,
    currency_handling           VARCHAR(24),
    time_zone_handling          VARCHAR(24),
    owner_team                  VARCHAR(64) NOT NULL,
    online_serving              BOOLEAN NOT NULL DEFAULT FALSE,
    max_age_seconds             INTEGER,
    parity_test_reference       VARCHAR(255),
    protected_attribute_derived BOOLEAN NOT NULL DEFAULT FALSE,
    permitted_purpose           VARCHAR(64),
    legal_review_reference      VARCHAR(255),
    fairness_audit_only         BOOLEAN NOT NULL DEFAULT FALSE,
    operational_use_permitted   BOOLEAN NOT NULL DEFAULT TRUE,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approved_at                 TIMESTAMPTZ,
    approved_by_account_holder_id UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_risk_feature_definitions_version UNIQUE (feature_key, feature_version),
    CONSTRAINT fk_risk_feature_definitions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_risk_feature_definitions_value_type CHECK (
        value_type IN ('BOOLEAN', 'CATEGORICAL', 'COUNT', 'AMOUNT', 'RATIO', 'DURATION', 'SCORE')
    ),
    CONSTRAINT ck_risk_feature_definitions_scope CHECK (
        entity_scope IN ('ACCOUNT', 'ORGANIZATION', 'LISTING', 'BOOKING', 'DEVICE', 'NETWORK',
                         'INSTRUMENT', 'CONTENT', 'ACTION')
    ),
    CONSTRAINT ck_risk_feature_definitions_event_time CHECK (
        event_time_rule IN ('EVENT_TIME', 'INGESTION_TIME', 'DECISION_TIME')
    ),
    CONSTRAINT ck_risk_feature_definitions_window CHECK (
        window_seconds IS NULL OR window_seconds > 0
    ),
    CONSTRAINT ck_risk_feature_definitions_sources CHECK (cardinality(source_signal_types) > 0),
    -- A feature read on the critical path must say how old it may be and what it means when absent.
    -- Without both, a vendor timeout silently becomes a zero, and a zero silently becomes safe.
    CONSTRAINT ck_risk_feature_definitions_online CHECK (
        NOT online_serving
            OR (max_age_seconds IS NOT NULL AND max_age_seconds > 0
                AND missing_value_semantics IS NOT NULL AND parity_test_reference IS NOT NULL)
    ),
    CONSTRAINT ck_risk_feature_definitions_missing CHECK (
        missing_value_semantics IS NULL
            OR missing_value_semantics IN ('EXPLICIT_UNKNOWN', 'POLICY_FALLBACK', 'BLOCK_EVALUATION')
    ),
    -- A protected attribute or a close proxy is only permitted with a named purpose and a legal
    -- review behind it. The feature document allows exactly one such purpose -- auditing disparate
    -- impact -- and that audit population is access-separated from production decision inputs, so a
    -- fairness feature may not also be an operational input.
    CONSTRAINT ck_risk_feature_definitions_protected CHECK (
        NOT protected_attribute_derived
            OR (permitted_purpose IS NOT NULL AND legal_review_reference IS NOT NULL)
    ),
    CONSTRAINT ck_risk_feature_definitions_fairness_separation CHECK (
        NOT fairness_audit_only OR NOT operational_use_permitted
    ),
    CONSTRAINT ck_risk_feature_definitions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_risk_feature_definitions_approval CHECK (
        (status = 'DRAFT') = (approved_at IS NULL)
        AND (approved_at IS NULL) = (approved_by_account_holder_id IS NULL)
    ),
    CONSTRAINT ck_risk_feature_definitions_version_counter CHECK (version >= 0)
);

CREATE INDEX idx_risk_feature_definitions_approved ON risk_feature_definitions (feature_key)
    WHERE status = 'APPROVED';
--rollback DROP TABLE risk_feature_definitions;

--changeset ninggiangboy:027-06-risk-feature-snapshots
-- The values as they stood at the instant of one evaluation. This is what makes a decision
-- reproducible: not the feature store as it is now, but the digest of what was actually read.
CREATE TABLE risk_feature_snapshots (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_key          VARCHAR(128) NOT NULL,
    feature_set_reference   VARCHAR(128) NOT NULL,
    feature_versions        JSONB NOT NULL,
    values_digest           CHAR(64) NOT NULL,
    values_document         JSONB,
    values_storage_reference VARCHAR(255),
    as_of_time              TIMESTAMPTZ NOT NULL,
    watermark_time          TIMESTAMPTZ NOT NULL,
    missing_feature_keys    TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    stale_feature_keys      TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    computed_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_risk_feature_snapshots_evaluation UNIQUE (evaluation_key, feature_set_reference),
    CONSTRAINT ck_risk_feature_snapshots_digest CHECK (values_digest ~ '^[0-9a-f]{64}$'),
    -- Either the values are here or they are addressable somewhere retention-controlled. A snapshot
    -- with neither cannot replay anything and is worse than no snapshot, because it looks like one.
    CONSTRAINT ck_risk_feature_snapshots_payload CHECK (
        (values_document IS NOT NULL) <> (values_storage_reference IS NOT NULL)
    ),
    -- The watermark is how far the input streams had actually progressed. It cannot be ahead of the
    -- instant the snapshot claims to describe.
    CONSTRAINT ck_risk_feature_snapshots_watermark CHECK (watermark_time <= as_of_time)
);

CREATE INDEX idx_risk_feature_snapshots_computed ON risk_feature_snapshots (computed_at);
--rollback DROP TABLE risk_feature_snapshots;

--changeset ninggiangboy:027-07-risk-model-predictions
-- An estimate of a named risk over a named horizon. There is deliberately no action column on this
-- table: a score is not a decision, and the only way to keep that true over years of pressure is for
-- the row to have nowhere to put one.
CREATE TABLE risk_model_predictions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_key              VARCHAR(128) NOT NULL,
    model_key                   VARCHAR(64) NOT NULL,
    model_version               VARCHAR(32) NOT NULL,
    artifact_reference          VARCHAR(255) NOT NULL,
    feature_set_reference       VARCHAR(128) NOT NULL,
    risk_feature_snapshot_id    UUID,
    target_event                VARCHAR(64) NOT NULL,
    horizon_seconds             INTEGER NOT NULL,
    score                       NUMERIC(7, 6),
    uncertainty                 NUMERIC(7, 6),
    calibration_reference       VARCHAR(128),
    serving_fallback            VARCHAR(24) NOT NULL DEFAULT 'NONE',
    latency_ms                  INTEGER,
    evaluated_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_model_predictions_snapshot FOREIGN KEY (risk_feature_snapshot_id)
        REFERENCES risk_feature_snapshots (id),
    CONSTRAINT uk_risk_model_predictions_identity
        UNIQUE (evaluation_key, model_key, model_version),
    CONSTRAINT ck_risk_model_predictions_horizon CHECK (horizon_seconds > 0),
    CONSTRAINT ck_risk_model_predictions_score CHECK (
        score IS NULL OR (score >= 0 AND score <= 1)
    ),
    CONSTRAINT ck_risk_model_predictions_uncertainty CHECK (
        uncertainty IS NULL OR (uncertainty >= 0 AND uncertainty <= 1)
    ),
    CONSTRAINT ck_risk_model_predictions_fallback CHECK (
        serving_fallback IN ('NONE', 'TIMEOUT', 'KILL_SWITCH', 'MISSING_FEATURES', 'UNAVAILABLE',
                             'SHADOW_ONLY')
    ),
    -- A served prediction has a score; a fallback has none. Recording a fallback with a number
    -- attached is how a timeout becomes a confident zero.
    CONSTRAINT ck_risk_model_predictions_served CHECK (
        (serving_fallback = 'NONE') = (score IS NOT NULL)
    ),
    CONSTRAINT ck_risk_model_predictions_latency CHECK (latency_ms IS NULL OR latency_ms >= 0)
);

CREATE INDEX idx_risk_model_predictions_model ON risk_model_predictions (model_key, evaluated_at);
--rollback DROP TABLE risk_model_predictions;

--changeset ninggiangboy:027-08-risk-policies
-- The effective-dated rules a decision is taken under. Immutable once active, because a decision
-- names a policy version and an epoch, and editing the version in place would rewrite the reason for
-- every decision already taken under it.
CREATE TABLE risk_policies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key                  VARCHAR(64) NOT NULL,
    policy_version              INTEGER NOT NULL,
    policy_epoch                VARCHAR(64) NOT NULL,
    scope_type                  VARCHAR(24) NOT NULL DEFAULT 'GLOBAL',
    scope_id                    UUID,
    market_id                   UUID,
    action_keys                 TEXT[] NOT NULL,
    max_action_tier             VARCHAR(4) NOT NULL,
    priority                    INTEGER NOT NULL,
    input_schema_reference      VARCHAR(255) NOT NULL,
    rule_document               JSONB NOT NULL,
    outcome_mapping             JSONB NOT NULL,
    user_reason_mapping         JSONB NOT NULL,
    review_route_queue_key      VARCHAR(64),
    appeal_route                VARCHAR(48),
    owner_team                  VARCHAR(64) NOT NULL,
    authored_by_account_holder_id UUID NOT NULL,
    simulation_evidence_reference VARCHAR(255),
    rollout_percentage          INTEGER NOT NULL DEFAULT 100,
    shadow_mode                 BOOLEAN NOT NULL DEFAULT FALSE,
    kill_switch_engaged         BOOLEAN NOT NULL DEFAULT FALSE,
    kill_switch_reason          VARCHAR(128),
    emergency                   BOOLEAN NOT NULL DEFAULT FALSE,
    post_use_review_at          TIMESTAMPTZ,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,
    activated_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_risk_policies_version UNIQUE (policy_key, policy_version),
    CONSTRAINT fk_risk_policies_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_risk_policies_author FOREIGN KEY (authored_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_risk_policies_scope CHECK (
        scope_type IN ('GLOBAL', 'MARKET', 'ORGANIZATION', 'LISTING', 'SEGMENT')
    ),
    CONSTRAINT ck_risk_policies_scope_id CHECK ((scope_type = 'GLOBAL') = (scope_id IS NULL)),
    CONSTRAINT ck_risk_policies_actions CHECK (cardinality(action_keys) > 0),
    CONSTRAINT ck_risk_policies_tier CHECK (max_action_tier IN ('T0', 'T1', 'T2', 'T3', 'T4')),
    CONSTRAINT ck_risk_policies_rollout CHECK (
        rollout_percentage >= 0 AND rollout_percentage <= 100
    ),
    CONSTRAINT ck_risk_policies_kill_switch CHECK (
        kill_switch_engaged = (kill_switch_reason IS NOT NULL)
    ),
    -- An emergency change expires and receives a post-use review. Both are the difference between an
    -- emergency and a permanent change nobody approved.
    CONSTRAINT ck_risk_policies_emergency CHECK (
        NOT emergency OR (effective_until IS NOT NULL AND post_use_review_at IS NOT NULL)
    ),
    CONSTRAINT ck_risk_policies_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'ACTIVE', 'SUSPENDED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_risk_policies_activation CHECK (
        status IN ('DRAFT', 'APPROVED')
            OR (effective_from IS NOT NULL AND activated_at IS NOT NULL)
    ),
    CONSTRAINT ck_risk_policies_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    -- A canary is a rollout below a hundred per cent, and a rollout below a hundred per cent needs
    -- the replay it was judged on. Shadow mode decides nothing, so it is exempt.
    CONSTRAINT ck_risk_policies_simulation CHECK (
        status NOT IN ('ACTIVE', 'SUSPENDED', 'SUPERSEDED')
            OR shadow_mode
            OR simulation_evidence_reference IS NOT NULL
    ),
    CONSTRAINT ck_risk_policies_version_counter CHECK (version >= 0)
);

-- Two live versions of the same policy key with overlapping effective intervals would make the
-- applicable rule a race. An evaluation binds one epoch; the database makes sure there is one to bind.
ALTER TABLE risk_policies
    ADD CONSTRAINT ex_risk_policies_effective_overlap EXCLUDE USING GIST (
        policy_key WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    ) WHERE (status = 'ACTIVE' AND NOT shadow_mode);

CREATE INDEX idx_risk_policies_active ON risk_policies (policy_key, priority)
    WHERE status = 'ACTIVE';
--rollback ALTER TABLE risk_policies DROP CONSTRAINT ex_risk_policies_effective_overlap;
--rollback DROP TABLE risk_policies;

--changeset ninggiangboy:027-09-risk-policy-approvals
-- Maker-checker, as rows. A policy that can deny money or restrict an account needs approvers who
-- are not its author, and counting them is a database question rather than a screen the deploy
-- pipeline can be told to skip.
CREATE TABLE risk_policy_approvals (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_policy_id              UUID NOT NULL,
    approver_account_holder_id  UUID NOT NULL,
    approval_role               VARCHAR(32) NOT NULL,
    decision                    VARCHAR(16) NOT NULL,
    rationale                   VARCHAR(512),
    step_up_assurance_level     VARCHAR(8) NOT NULL,
    decided_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_policy_approvals_policy FOREIGN KEY (risk_policy_id)
        REFERENCES risk_policies (id),
    CONSTRAINT fk_risk_policy_approvals_approver FOREIGN KEY (approver_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_risk_policy_approvals_approver
        UNIQUE (risk_policy_id, approver_account_holder_id),
    CONSTRAINT ck_risk_policy_approvals_role CHECK (
        approval_role IN ('POLICY_OWNER', 'RISK_GOVERNANCE', 'LEGAL', 'SAFETY', 'ENGINEERING')
    ),
    CONSTRAINT ck_risk_policy_approvals_decision CHECK (
        decision IN ('APPROVED', 'REJECTED', 'ABSTAINED')
    ),
    CONSTRAINT ck_risk_policy_approvals_rejection CHECK (
        decision <> 'REJECTED' OR rationale IS NOT NULL
    ),
    CONSTRAINT ck_risk_policy_approvals_assurance CHECK (
        step_up_assurance_level IN ('AAL1', 'AAL2', 'AAL3')
    )
);
--rollback DROP TABLE risk_policy_approvals;

--changeset ninggiangboy:027-10-risk-decisions
-- One evaluation, one immutable outcome. The canonical identity below is what makes a retry replay
-- an existing decision instead of taking a second one, and what makes "the same request got two
-- different answers" impossible rather than merely unlikely.
CREATE TABLE risk_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_key              VARCHAR(128) NOT NULL,
    protected_action            VARCHAR(64) NOT NULL,
    actor_subject_id            UUID NOT NULL,
    resource_type               VARCHAR(32),
    resource_id                 UUID,
    resource_version            BIGINT,
    command_id                  UUID,
    client_idempotency_key      VARCHAR(128),
    policy_epoch                VARCHAR(64) NOT NULL,
    risk_policy_id              UUID,
    market_id                   UUID,
    auth_session_id             UUID,
    outcome                     VARCHAR(16) NOT NULL,
    hold_dimension              VARCHAR(48),
    limit_scope                 JSONB,
    internal_reasons            TEXT[] NOT NULL,
    user_reason_family          VARCHAR(48),
    risk_feature_snapshot_id    UUID,
    model_prediction_ids        UUID[] NOT NULL DEFAULT ARRAY[]::UUID[],
    fallback_mode               VARCHAR(24),
    mandatory_facts_missing     BOOLEAN NOT NULL DEFAULT FALSE,
    precautionary               BOOLEAN NOT NULL DEFAULT FALSE,
    decided_by                  VARCHAR(16) NOT NULL DEFAULT 'AUTOMATION',
    decided_by_account_holder_id UUID,
    correlation_id              UUID,
    evaluated_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    projection                  VARCHAR(16) NOT NULL DEFAULT 'EFFECTIVE',
    supersedes_decision_id      UUID,
    superseded_by_decision_id   UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_decisions_actor FOREIGN KEY (actor_subject_id) REFERENCES risk_subjects (id),
    CONSTRAINT fk_risk_decisions_policy FOREIGN KEY (risk_policy_id) REFERENCES risk_policies (id),
    CONSTRAINT fk_risk_decisions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_risk_decisions_session FOREIGN KEY (auth_session_id) REFERENCES auth_sessions (id),
    CONSTRAINT fk_risk_decisions_snapshot FOREIGN KEY (risk_feature_snapshot_id)
        REFERENCES risk_feature_snapshots (id),
    CONSTRAINT fk_risk_decisions_decider FOREIGN KEY (decided_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_decisions_supersedes FOREIGN KEY (supersedes_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_decisions_superseded_by FOREIGN KEY (superseded_by_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT ck_risk_decisions_outcome CHECK (
        outcome IN ('ALLOW', 'CHALLENGE', 'HOLD', 'MANUAL_REVIEW', 'LIMIT', 'DENY')
    ),
    -- A hold stops one named dimension until a stated instant. A hold without either is an outage
    -- with a reason code.
    CONSTRAINT ck_risk_decisions_hold CHECK (
        outcome <> 'HOLD' OR (hold_dimension IS NOT NULL AND expires_at IS NOT NULL)
    ),
    -- A limit the domain cannot read is a vague risk flag, which the feature document rules out by
    -- name.
    CONSTRAINT ck_risk_decisions_limit CHECK (outcome <> 'LIMIT' OR limit_scope IS NOT NULL),
    -- A refusal a person is told about must have an approved reason family behind it. Internal
    -- reasons stay internal; exposing detector detail is how thresholds get mapped by attackers.
    CONSTRAINT ck_risk_decisions_deny_reason CHECK (
        outcome NOT IN ('DENY', 'LIMIT') OR user_reason_family IS NOT NULL
    ),
    CONSTRAINT ck_risk_decisions_reasons CHECK (cardinality(internal_reasons) > 0),
    CONSTRAINT ck_risk_decisions_fallback CHECK (
        fallback_mode IS NULL
            OR fallback_mode IN ('REGISTERED_FALLBACK', 'DETERMINISTIC_ONLY', 'MODEL_DISABLED',
                                 'PROVIDER_TIMEOUT')
    ),
    -- Missing evidence is not an approval. Allowing a protected action while a mandatory fact was
    -- unavailable is permitted only as the registered fallback, named on the row.
    CONSTRAINT ck_risk_decisions_missing_facts CHECK (
        NOT mandatory_facts_missing OR outcome <> 'ALLOW' OR fallback_mode IS NOT NULL
    ),
    -- Containment before confirmation is allowed, but it must say so and it must end. An unbounded
    -- precautionary restriction is a punishment with no finding behind it.
    CONSTRAINT ck_risk_decisions_precautionary CHECK (
        NOT precautionary OR expires_at IS NOT NULL
    ),
    CONSTRAINT ck_risk_decisions_decider CHECK (
        decided_by IN ('AUTOMATION', 'REVIEWER', 'APPEAL', 'SYSTEM_FALLBACK')
        AND (decided_by IN ('REVIEWER', 'APPEAL')) = (decided_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_decisions_projection CHECK (
        projection IN ('EFFECTIVE', 'SATISFIED', 'EXPIRED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_risk_decisions_supersession CHECK (
        (projection = 'SUPERSEDED') = (superseded_by_decision_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_decisions_self_supersession CHECK (
        superseded_by_decision_id IS NULL OR superseded_by_decision_id <> id
    ),
    CONSTRAINT ck_risk_decisions_expiry CHECK (expires_at IS NULL OR expires_at > evaluated_at),
    CONSTRAINT ck_risk_decisions_resource CHECK (
        (resource_type IS NULL) = (resource_id IS NULL)
    ),
    CONSTRAINT ck_risk_decisions_version CHECK (version >= 0)
);

-- The canonical evaluation identity from the feature document. NULLS NOT DISTINCT matters: an action
-- with no resource and no command must still collapse to one decision per actor and epoch rather
-- than to an unlimited number of rows that all look unique because something was null.
CREATE UNIQUE INDEX uk_risk_decisions_evaluation
    ON risk_decisions (protected_action, actor_subject_id, resource_type, resource_id, command_id,
                       policy_epoch)
    NULLS NOT DISTINCT;

-- One client idempotency key is one answer. Differing inputs under the same key are a conflict the
-- service must report, not a second decision.
CREATE UNIQUE INDEX uk_risk_decisions_client_key
    ON risk_decisions (client_idempotency_key)
    WHERE client_idempotency_key IS NOT NULL;

CREATE INDEX idx_risk_decisions_subject ON risk_decisions (actor_subject_id, evaluated_at);
CREATE INDEX idx_risk_decisions_action ON risk_decisions (protected_action, evaluated_at);
--rollback DROP TABLE risk_decisions;

--changeset ninggiangboy:027-11-risk-decision-rule-hits
-- Which rules fired, in order, and whether each one was allowed to change the answer. Append-only:
-- an explanation edited after the fact is not an explanation.
CREATE TABLE risk_decision_rule_hits (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_decision_id        UUID NOT NULL,
    hit_sequence            INTEGER NOT NULL,
    rule_id                 VARCHAR(96) NOT NULL,
    rule_class              VARCHAR(20) NOT NULL,
    result                  VARCHAR(16) NOT NULL,
    contributed_to_outcome  BOOLEAN NOT NULL,
    contribution_weight     NUMERIC(7, 6),
    input_references        TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    redacted_explanation    VARCHAR(512),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_decision_rule_hits_decision FOREIGN KEY (risk_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT uk_risk_decision_rule_hits_sequence UNIQUE (risk_decision_id, hit_sequence),
    CONSTRAINT ck_risk_decision_rule_hits_sequence CHECK (hit_sequence >= 0),
    CONSTRAINT ck_risk_decision_rule_hits_class CHECK (
        rule_class IN ('MANDATORY', 'ADVISORY', 'EXPLANATION_ONLY')
    ),
    CONSTRAINT ck_risk_decision_rule_hits_result CHECK (
        result IN ('MATCHED', 'NOT_MATCHED', 'SKIPPED', 'ERRORED', 'INPUTS_MISSING')
    ),
    -- A rule registered as explanation-only may describe the answer but never move it. This is the
    -- structural half of "a stale optional model cannot escalate a permitted action".
    CONSTRAINT ck_risk_decision_rule_hits_explanation CHECK (
        rule_class <> 'EXPLANATION_ONLY' OR NOT contributed_to_outcome
    ),
    -- A rule that did not match, was skipped or errored contributed nothing. Recording otherwise
    -- would let a failed evaluation be presented as a finding.
    CONSTRAINT ck_risk_decision_rule_hits_contribution CHECK (
        result = 'MATCHED' OR NOT contributed_to_outcome
    )
);
--rollback DROP TABLE risk_decision_rule_hits;

--changeset ninggiangboy:027-12-risk-decision-enforcements
-- Which authoritative domain command actually honoured which decision. Risk never writes inventory,
-- money, sessions or publication, so this row is the only place the two halves meet -- and it is the
-- row that answers "was the deny enforced, or merely recorded?".
CREATE TABLE risk_decision_enforcements (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_decision_id        UUID NOT NULL,
    enforcing_domain        VARCHAR(32) NOT NULL,
    command_type            VARCHAR(64) NOT NULL,
    command_id              UUID NOT NULL,
    enforcement_result      VARCHAR(24) NOT NULL,
    applied_restriction_id  UUID,
    divergence_reason       VARCHAR(255),
    enforced_at             TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_decision_enforcements_decision FOREIGN KEY (risk_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT uk_risk_decision_enforcements_command
        UNIQUE (enforcing_domain, command_type, command_id),
    CONSTRAINT ck_risk_decision_enforcements_domain CHECK (
        enforcing_domain IN ('IDENTITY', 'LISTING', 'INVENTORY', 'BOOKING', 'PAYMENT', 'FINANCE',
                             'MESSAGING', 'REVIEW', 'PROMOTION', 'SUPPORT', 'STAY_OPERATIONS',
                             'ADMIN')
    ),
    CONSTRAINT ck_risk_decision_enforcements_result CHECK (
        enforcement_result IN ('PROCEEDED', 'BLOCKED', 'CHALLENGED', 'HELD', 'LIMITED',
                               'REEVALUATION_REQUESTED', 'DIVERGED')
    ),
    -- A domain that did something other than what the decision said must say why. Silent divergence
    -- is the failure mode that makes the whole record worthless.
    CONSTRAINT ck_risk_decision_enforcements_divergence CHECK (
        (enforcement_result = 'DIVERGED') = (divergence_reason IS NOT NULL)
    )
);

CREATE INDEX idx_risk_decision_enforcements_decision
    ON risk_decision_enforcements (risk_decision_id);
--rollback DROP TABLE risk_decision_enforcements;

--changeset ninggiangboy:027-13-risk-challenges
-- Additional proof demanded before a protected action may continue. A challenge is bound to one
-- actor, one action and one decision, and it expires: passing something last week is not proof about
-- what is happening now.
CREATE TABLE risk_challenges (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_decision_id            UUID NOT NULL,
    subject_id                  UUID NOT NULL,
    auth_session_id             UUID,
    protected_action            VARCHAR(64) NOT NULL,
    resource_type               VARCHAR(32),
    resource_id                 UUID,
    challenge_method            VARCHAR(32) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    state_rank                  SMALLINT NOT NULL DEFAULT 0,
    max_attempts                SMALLINT NOT NULL,
    attempts_used               SMALLINT NOT NULL DEFAULT 0,
    reuse_permitted             BOOLEAN NOT NULL DEFAULT FALSE,
    satisfied_evidence_reference VARCHAR(255),
    resulting_assurance_level   VARCHAR(8),
    failure_reason              VARCHAR(64),
    issued_at                   TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_challenges_decision FOREIGN KEY (risk_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_challenges_subject FOREIGN KEY (subject_id) REFERENCES risk_subjects (id),
    CONSTRAINT fk_risk_challenges_session FOREIGN KEY (auth_session_id)
        REFERENCES auth_sessions (id),
    CONSTRAINT ck_risk_challenges_method CHECK (
        challenge_method IN ('EMAIL_VERIFICATION', 'PHONE_VERIFICATION', 'MFA', 'PASSWORD_REENTRY',
                             'IDENTITY_DOCUMENT', 'LIVENESS', 'PAYMENT_CONFIRMATION',
                             'THREE_D_SECURE', 'MANUAL_PROOF')
    ),
    -- The lifecycle is paired to an integer so monotonicity is one comparison. FAILED, EXPIRED and
    -- CANCELLED share the terminal rank: they are different endings, not different distances.
    CONSTRAINT ck_risk_challenges_state_rank CHECK (
        (state = 'REQUIRED' AND state_rank = 0)
        OR (state = 'STARTED' AND state_rank = 1)
        OR (state = 'SUBMITTED' AND state_rank = 2)
        OR (state = 'PASSED' AND state_rank = 3)
        OR (state = 'FAILED' AND state_rank = 3)
        OR (state = 'EXPIRED' AND state_rank = 3)
        OR (state = 'CANCELLED' AND state_rank = 3)
    ),
    CONSTRAINT ck_risk_challenges_attempts CHECK (
        max_attempts > 0 AND attempts_used >= 0 AND attempts_used <= max_attempts
    ),
    -- Passing means there is evidence it was passed. A state flipped without one is an assertion.
    CONSTRAINT ck_risk_challenges_passed CHECK (
        state <> 'PASSED'
            OR (satisfied_evidence_reference IS NOT NULL AND completed_at IS NOT NULL
                AND resulting_assurance_level IS NOT NULL)
    ),
    CONSTRAINT ck_risk_challenges_failed CHECK (
        state <> 'FAILED' OR (failure_reason IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_risk_challenges_terminal CHECK (
        (state_rank = 3) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_risk_challenges_assurance CHECK (
        resulting_assurance_level IS NULL
            OR resulting_assurance_level IN ('AAL1', 'AAL2', 'AAL3')
    ),
    CONSTRAINT ck_risk_challenges_expiry CHECK (expires_at > issued_at),
    CONSTRAINT ck_risk_challenges_resource CHECK ((resource_type IS NULL) = (resource_id IS NULL)),
    CONSTRAINT ck_risk_challenges_version CHECK (version >= 0)
);

-- One live challenge per decision and method. Issuing a second one is how an attempt ceiling gets
-- reset by asking again.
CREATE UNIQUE INDEX uk_risk_challenges_live
    ON risk_challenges (risk_decision_id, challenge_method)
    WHERE state IN ('REQUIRED', 'STARTED', 'SUBMITTED');

CREATE INDEX idx_risk_challenges_subject ON risk_challenges (subject_id, issued_at);
--rollback DROP TABLE risk_challenges;

--changeset ninggiangboy:027-14-risk-challenge-attempts
-- Every try, kept. The attempt ceiling only means something if the attempts are rows rather than a
-- counter a service can forget to increment.
CREATE TABLE risk_challenge_attempts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_challenge_id       UUID NOT NULL,
    attempt_number          SMALLINT NOT NULL,
    outcome                 VARCHAR(16) NOT NULL,
    failure_reason          VARCHAR(64),
    provider_account_id     UUID,
    provider_reference      VARCHAR(128),
    client_descriptor       VARCHAR(255),
    attempted_at            TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_challenge_attempts_challenge FOREIGN KEY (risk_challenge_id)
        REFERENCES risk_challenges (id),
    CONSTRAINT fk_risk_challenge_attempts_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_risk_challenge_attempts_number
        UNIQUE (risk_challenge_id, attempt_number),
    CONSTRAINT ck_risk_challenge_attempts_number CHECK (attempt_number > 0),
    CONSTRAINT ck_risk_challenge_attempts_outcome CHECK (
        outcome IN ('PASSED', 'FAILED', 'ABANDONED', 'ERRORED')
    ),
    CONSTRAINT ck_risk_challenge_attempts_failure CHECK (
        outcome = 'PASSED' OR failure_reason IS NOT NULL
    )
);
--rollback DROP TABLE risk_challenge_attempts;

--changeset ninggiangboy:027-15-risk-restrictions
-- The governed intervention behind an enforced limitation. Migration 014's capability_restrictions is
-- what authorization evaluates on every request; this row is the reason it exists, with the policy,
-- the decision, the review date and the appeal path that a bare capability row cannot carry.
CREATE TABLE risk_restrictions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_subject_id           UUID NOT NULL,
    restriction_type            VARCHAR(40) NOT NULL,
    capability_scope            VARCHAR(64) NOT NULL,
    resource_type               VARCHAR(32),
    resource_id                 UUID,
    policy_intent               VARCHAR(48) NOT NULL,
    reason_code                 VARCHAR(64) NOT NULL,
    user_reason_family          VARCHAR(48),
    risk_policy_id              UUID,
    risk_decision_id            UUID,
    origin                      VARCHAR(24) NOT NULL DEFAULT 'RISK_DECISION',
    enforcement_mode            VARCHAR(24) NOT NULL,
    capability_restriction_id   UUID,
    state                       VARCHAR(16) NOT NULL DEFAULT 'PROPOSED',
    high_impact                 BOOLEAN NOT NULL DEFAULT FALSE,
    permanent                   BOOLEAN NOT NULL DEFAULT FALSE,
    appeal_available            BOOLEAN NOT NULL DEFAULT TRUE,
    appeal_suppression_reason   VARCHAR(128),
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    review_due_at               TIMESTAMPTZ,
    activated_at                TIMESTAMPTZ,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(128),
    revoked_by_account_holder_id UUID,
    superseded_by_restriction_id UUID,
    created_by_account_holder_id UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_restrictions_target FOREIGN KEY (target_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT fk_risk_restrictions_policy FOREIGN KEY (risk_policy_id) REFERENCES risk_policies (id),
    CONSTRAINT fk_risk_restrictions_decision FOREIGN KEY (risk_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_restrictions_capability FOREIGN KEY (capability_restriction_id)
        REFERENCES capability_restrictions (id),
    CONSTRAINT fk_risk_restrictions_revoker FOREIGN KEY (revoked_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_restrictions_creator FOREIGN KEY (created_by_account_holder_id)
        REFERENCES account_holders (id),
    -- Deferred on purpose. The live index below refuses two restrictions with the same intent, so
    -- the outgoing row must name its replacement before that replacement exists; an immediate check
    -- would make replacing a restriction impossible without first leaving the subject unrestricted.
    CONSTRAINT fk_risk_restrictions_supersession FOREIGN KEY (superseded_by_restriction_id)
        REFERENCES risk_restrictions (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_risk_restrictions_type CHECK (
        restriction_type IN ('LOGIN_BLOCKED', 'SESSION_REVOKED', 'MESSAGING_LIMITED',
                             'LISTING_PUBLICATION_BLOCKED', 'LISTING_QUARANTINED',
                             'BOOKING_CREATE_BLOCKED', 'PAYMENT_CHALLENGE_REQUIRED',
                             'PAYMENT_METHOD_BLOCKED', 'PAYOUT_RELEASE_HELD',
                             'PAYOUT_DESTINATION_LOCKED', 'PROMOTION_REDEMPTION_BLOCKED',
                             'REVIEW_SUBMISSION_BLOCKED', 'CONTENT_QUARANTINED',
                             'ACCOUNT_SUSPENDED')
    ),
    CONSTRAINT ck_risk_restrictions_origin CHECK (
        origin IN ('RISK_DECISION', 'REVIEWER_ACTION', 'APPEAL_OUTCOME', 'LEGAL_ORDER',
                   'LEGACY_STATUS')
    ),
    -- A restriction carried over from the old account status column has no policy and no decision
    -- behind it, and the feature document is explicit that none may be invented for it. The row says
    -- what it is: a limitation inherited from a system that did not record why.
    CONSTRAINT ck_risk_restrictions_legacy CHECK (
        origin <> 'LEGACY_STATUS' OR (risk_policy_id IS NULL AND risk_decision_id IS NULL)
    ),
    CONSTRAINT ck_risk_restrictions_enforcement CHECK (
        enforcement_mode IN ('HARD_BLOCK', 'CHALLENGE_REQUIRED', 'RATE_LIMITED', 'SHADOW_MONITOR',
                             'DISCLOSURE_ONLY')
    ),
    CONSTRAINT ck_risk_restrictions_state CHECK (
        state IN ('PROPOSED', 'ACTIVE', 'APPEAL_PENDING', 'EXPIRED', 'REVOKED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_risk_restrictions_activation CHECK (
        (state = 'PROPOSED') = (activated_at IS NULL)
    ),
    -- Every temporary intervention ends or is looked at again. A restriction that is neither
    -- declared permanent nor bounded is an indefinite punishment with no owner.
    CONSTRAINT ck_risk_restrictions_bounded CHECK (
        permanent OR effective_until IS NOT NULL OR review_due_at IS NOT NULL
    ),
    CONSTRAINT ck_risk_restrictions_permanent CHECK (
        NOT permanent OR effective_until IS NULL
    ),
    CONSTRAINT ck_risk_restrictions_interval CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    -- Suspension, publication removal and payout holds are appealable unless withholding is itself
    -- documented. The suppression reason is that document.
    CONSTRAINT ck_risk_restrictions_appeal CHECK (
        appeal_available OR appeal_suppression_reason IS NOT NULL
    ),
    CONSTRAINT ck_risk_restrictions_high_impact CHECK (
        NOT high_impact OR (user_reason_family IS NOT NULL AND reason_code IS NOT NULL)
    ),
    CONSTRAINT ck_risk_restrictions_revocation CHECK (
        (state = 'REVOKED')
            = (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL
               AND revoked_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_restrictions_supersession CHECK (
        (state = 'SUPERSEDED') = (superseded_by_restriction_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_restrictions_self_supersession CHECK (
        superseded_by_restriction_id IS NULL OR superseded_by_restriction_id <> id
    ),
    CONSTRAINT ck_risk_restrictions_resource CHECK (
        (resource_type IS NULL) = (resource_id IS NULL)
    ),
    CONSTRAINT ck_risk_restrictions_version CHECK (version >= 0)
);

-- One live restriction per target, scope and policy intent. Overlapping restrictions from different
-- intents may coexist and compose; two rows expressing the same intent are a double punishment whose
-- lifting only removes half.
CREATE UNIQUE INDEX uk_risk_restrictions_live
    ON risk_restrictions (target_subject_id, capability_scope, resource_type, resource_id,
                          policy_intent)
    NULLS NOT DISTINCT
    WHERE state IN ('PROPOSED', 'ACTIVE', 'APPEAL_PENDING');

-- Enforcement reads every currently effective restriction for one target on every protected command,
-- so the lookup stays proportional to live restrictions rather than to lifetime history.
CREATE INDEX idx_risk_restrictions_enforcement
    ON risk_restrictions (target_subject_id, effective_from)
    WHERE state IN ('ACTIVE', 'APPEAL_PENDING');

ALTER TABLE risk_decision_enforcements
    ADD CONSTRAINT fk_risk_decision_enforcements_restriction FOREIGN KEY (applied_restriction_id)
        REFERENCES risk_restrictions (id);
--rollback ALTER TABLE risk_decision_enforcements DROP CONSTRAINT fk_risk_decision_enforcements_restriction;
--rollback DROP TABLE risk_restrictions;

--changeset ninggiangboy:027-16-risk-review-queues
-- Where work waits, and who is allowed to take it. Routing by skill and tier is what keeps a severe
-- safety report out of a general fraud queue.
CREATE TABLE risk_review_queues (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_key               VARCHAR(64) NOT NULL,
    display_name            VARCHAR(128) NOT NULL,
    required_skill          VARCHAR(48) NOT NULL,
    minimum_action_tier     VARCHAR(4) NOT NULL DEFAULT 'T0',
    market_id               UUID,
    language_tag            VARCHAR(35),
    target_response_seconds INTEGER NOT NULL,
    escalation_queue_id     UUID,
    conflict_rules          JSONB,
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_risk_review_queues_key UNIQUE (queue_key),
    CONSTRAINT fk_risk_review_queues_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_risk_review_queues_escalation FOREIGN KEY (escalation_queue_id)
        REFERENCES risk_review_queues (id),
    CONSTRAINT ck_risk_review_queues_skill CHECK (
        required_skill IN ('ACCOUNT_SECURITY', 'LISTING_MODERATION', 'PAYMENT_FRAUD', 'PAYOUT_RISK',
                           'CONTENT_MODERATION', 'SEVERE_SAFETY', 'QUALITY_ASSURANCE', 'APPEALS')
    ),
    CONSTRAINT ck_risk_review_queues_tier CHECK (
        minimum_action_tier IN ('T0', 'T1', 'T2', 'T3', 'T4')
    ),
    CONSTRAINT ck_risk_review_queues_response CHECK (target_response_seconds > 0),
    CONSTRAINT ck_risk_review_queues_status CHECK (status IN ('ACTIVE', 'PAUSED', 'RETIRED')),
    CONSTRAINT ck_risk_review_queues_self_escalation CHECK (
        escalation_queue_id IS NULL OR escalation_queue_id <> id
    ),
    CONSTRAINT ck_risk_review_queues_version CHECK (version >= 0)
);
--rollback DROP TABLE risk_review_queues;

--changeset ninggiangboy:027-17-risk-review-tasks
-- One unit of human work, leased rather than assigned, so an abandoned claim returns to the queue
-- without anyone deciding it was abandoned.
CREATE TABLE risk_review_tasks (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_review_queue_id        UUID NOT NULL,
    risk_decision_id            UUID,
    subject_id                  UUID NOT NULL,
    protected_action            VARCHAR(64),
    action_tier                 VARCHAR(4) NOT NULL,
    suspected_harm              VARCHAR(48) NOT NULL,
    priority_basis              VARCHAR(24) NOT NULL,
    priority_score              NUMERIC(7, 6),
    exposure_amount_minor       BIGINT,
    exposure_currency           VARCHAR(3),
    case_packet_reference       VARCHAR(255),
    disclosure_constraints      TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    state                       VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
    deadline_at                 TIMESTAMPTZ NOT NULL,
    lease_owner                 VARCHAR(128),
    lease_expires_at            TIMESTAMPTZ,
    lease_fencing_token         BIGINT NOT NULL DEFAULT 0,
    assigned_reviewer_account_holder_id UUID,
    escalated_to_queue_id       UUID,
    closed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_review_tasks_queue FOREIGN KEY (risk_review_queue_id)
        REFERENCES risk_review_queues (id),
    CONSTRAINT fk_risk_review_tasks_decision FOREIGN KEY (risk_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_review_tasks_subject FOREIGN KEY (subject_id) REFERENCES risk_subjects (id),
    CONSTRAINT fk_risk_review_tasks_reviewer FOREIGN KEY (assigned_reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_review_tasks_escalation FOREIGN KEY (escalated_to_queue_id)
        REFERENCES risk_review_queues (id),
    CONSTRAINT ck_risk_review_tasks_tier CHECK (action_tier IN ('T0', 'T1', 'T2', 'T3', 'T4')),
    CONSTRAINT ck_risk_review_tasks_priority_basis CHECK (
        priority_basis IN ('DETERMINISTIC_SEVERITY', 'DEADLINE', 'EXPOSURE', 'MODEL_ASSISTED')
    ),
    -- An urgent safety task is ordered by its severity and its deadline, never by a model. The
    -- feature document is explicit that a priority score may order peers but cannot demote T4.
    CONSTRAINT ck_risk_review_tasks_urgent_priority CHECK (
        action_tier <> 'T4' OR priority_basis IN ('DETERMINISTIC_SEVERITY', 'DEADLINE')
    ),
    CONSTRAINT ck_risk_review_tasks_exposure CHECK (
        (exposure_amount_minor IS NULL) = (exposure_currency IS NULL)
        AND (exposure_currency IS NULL OR exposure_currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_risk_review_tasks_state CHECK (
        state IN ('QUEUED', 'CLAIMED', 'IN_REVIEW', 'DECIDED', 'ESCALATED', 'CANCELLED')
    ),
    CONSTRAINT ck_risk_review_tasks_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    -- Claimed work is held by someone. A claimed task with no lease is work nobody can be asked
    -- about and nobody has to finish.
    CONSTRAINT ck_risk_review_tasks_claimed CHECK (
        state NOT IN ('CLAIMED', 'IN_REVIEW') OR lease_owner IS NOT NULL
    ),
    CONSTRAINT ck_risk_review_tasks_closed CHECK (
        (state IN ('DECIDED', 'CANCELLED')) = (closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_risk_review_tasks_escalation CHECK (
        (state = 'ESCALATED') = (escalated_to_queue_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_review_tasks_fencing CHECK (lease_fencing_token >= 0),
    CONSTRAINT ck_risk_review_tasks_version CHECK (version >= 0)
);

-- Claiming reads the oldest unclaimed work in one queue under FOR UPDATE SKIP LOCKED, so the index
-- is the claim order itself.
CREATE INDEX idx_risk_review_tasks_claimable
    ON risk_review_tasks (risk_review_queue_id, deadline_at, priority_score)
    WHERE state = 'QUEUED';

CREATE INDEX idx_risk_review_tasks_subject ON risk_review_tasks (subject_id, created_at);
--rollback DROP TABLE risk_review_tasks;

--changeset ninggiangboy:027-18-risk-review-actions
-- What a reviewer did, appended. A reviewer never edits an automated decision; the action creates a
-- new one. The subject is denormalized onto the row so self-review is a constraint rather than a
-- query somebody remembers to run.
CREATE TABLE risk_review_actions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_review_task_id         UUID NOT NULL,
    action_sequence             INTEGER NOT NULL,
    reviewer_account_holder_id  UUID NOT NULL,
    subject_account_holder_id   UUID,
    authority_source            VARCHAR(32) NOT NULL,
    action_type                 VARCHAR(32) NOT NULL,
    terminal                    BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code                 VARCHAR(64) NOT NULL,
    reviewer_notes_reference    VARCHAR(255),
    evidence_references         TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    risk_policy_id              UUID,
    resulting_decision_id       UUID,
    domain_command_reference    VARCHAR(128),
    max_permitted_scope         VARCHAR(48) NOT NULL,
    step_up_assurance_level     VARCHAR(8) NOT NULL,
    requires_second_approval    BOOLEAN NOT NULL DEFAULT FALSE,
    second_approver_account_holder_id UUID,
    lease_fencing_token         BIGINT NOT NULL,
    acted_at                    TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_review_actions_task FOREIGN KEY (risk_review_task_id)
        REFERENCES risk_review_tasks (id),
    CONSTRAINT fk_risk_review_actions_reviewer FOREIGN KEY (reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_review_actions_subject FOREIGN KEY (subject_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_review_actions_policy FOREIGN KEY (risk_policy_id)
        REFERENCES risk_policies (id),
    CONSTRAINT fk_risk_review_actions_decision FOREIGN KEY (resulting_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_review_actions_second_approver FOREIGN KEY (second_approver_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_risk_review_actions_sequence UNIQUE (risk_review_task_id, action_sequence),
    CONSTRAINT ck_risk_review_actions_sequence CHECK (action_sequence > 0),
    CONSTRAINT ck_risk_review_actions_authority CHECK (
        authority_source IN ('QUEUE_SKILL', 'ESCALATION', 'BREAK_GLASS', 'APPEAL_PANEL',
                             'QUALITY_ASSURANCE')
    ),
    CONSTRAINT ck_risk_review_actions_type CHECK (
        action_type IN ('CONFIRM_DECISION', 'OVERTURN_DECISION', 'REQUEST_EVIDENCE',
                        'ISSUE_RESTRICTION', 'LIFT_RESTRICTION', 'ISSUE_CHALLENGE',
                        'ESCALATE', 'CANCEL', 'ANNOTATE')
    ),
    -- Nobody reviews their own case. The denormalized subject is validated against the task by
    -- trigger, so the constraint cannot be defeated by writing a different account holder here.
    CONSTRAINT ck_risk_review_actions_self_review CHECK (
        subject_account_holder_id IS NULL
            OR subject_account_holder_id <> reviewer_account_holder_id
    ),
    CONSTRAINT ck_risk_review_actions_second_approval CHECK (
        (NOT requires_second_approval OR second_approver_account_holder_id IS NOT NULL)
        AND (second_approver_account_holder_id IS NULL
             OR second_approver_account_holder_id <> reviewer_account_holder_id)
    ),
    -- A reviewer who changed the answer produced a new decision. Overturning without one leaves the
    -- old outcome standing while the record says otherwise.
    CONSTRAINT ck_risk_review_actions_overturn CHECK (
        action_type <> 'OVERTURN_DECISION' OR resulting_decision_id IS NOT NULL
    ),
    CONSTRAINT ck_risk_review_actions_assurance CHECK (
        step_up_assurance_level IN ('AAL1', 'AAL2', 'AAL3')
    ),
    CONSTRAINT ck_risk_review_actions_fencing CHECK (lease_fencing_token >= 0)
);

-- One terminal action per task. A double decision is the race the lease is meant to prevent and this
-- index is what proves it did.
CREATE UNIQUE INDEX uk_risk_review_actions_terminal
    ON risk_review_actions (risk_review_task_id)
    WHERE terminal;
--rollback DROP TABLE risk_review_actions;

--changeset ninggiangboy:027-19-risk-appeals
-- A request to look again at one effective decision. An appeal is not a second opinion from the same
-- person: the reviewer is recorded and must differ from whoever decided the first time.
CREATE TABLE risk_appeals (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_kind                 VARCHAR(24) NOT NULL,
    risk_decision_id            UUID,
    risk_restriction_id         UUID,
    moderation_decision_id      UUID,
    appellant_subject_id        UUID NOT NULL,
    appellant_authority         VARCHAR(24) NOT NULL,
    appeal_round                SMALLINT NOT NULL DEFAULT 1,
    grounds_reference           VARCHAR(255) NOT NULL,
    evidence_references         TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    language_tag                VARCHAR(35),
    state                       VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    deadline_at                 TIMESTAMPTZ NOT NULL,
    original_decider_account_holder_id UUID,
    assigned_reviewer_account_holder_id UUID,
    risk_review_task_id         UUID,
    outcome                     VARCHAR(24),
    findings_reference          VARCHAR(255),
    superseding_decision_id     UUID,
    restoration_effective_from  TIMESTAMPTZ,
    remedy_referral_reference   VARCHAR(128),
    submitted_at                TIMESTAMPTZ NOT NULL,
    acknowledged_at             TIMESTAMPTZ,
    decided_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_appeals_decision FOREIGN KEY (risk_decision_id) REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_appeals_restriction FOREIGN KEY (risk_restriction_id)
        REFERENCES risk_restrictions (id),
    CONSTRAINT fk_risk_appeals_appellant FOREIGN KEY (appellant_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT fk_risk_appeals_original_decider FOREIGN KEY (original_decider_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_appeals_reviewer FOREIGN KEY (assigned_reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_appeals_task FOREIGN KEY (risk_review_task_id)
        REFERENCES risk_review_tasks (id),
    CONSTRAINT fk_risk_appeals_superseding FOREIGN KEY (superseding_decision_id)
        REFERENCES risk_decisions (id),
    CONSTRAINT ck_risk_appeals_target_kind CHECK (
        target_kind IN ('RISK_DECISION', 'RISK_RESTRICTION', 'MODERATION_DECISION')
    ),
    -- Exactly one target, and it is the one the kind names. An appeal pointing at two things has no
    -- single decision to supersede.
    CONSTRAINT ck_risk_appeals_target CHECK (
        num_nonnulls(risk_decision_id, risk_restriction_id, moderation_decision_id) = 1
        AND (target_kind <> 'RISK_DECISION' OR risk_decision_id IS NOT NULL)
        AND (target_kind <> 'RISK_RESTRICTION' OR risk_restriction_id IS NOT NULL)
        AND (target_kind <> 'MODERATION_DECISION' OR moderation_decision_id IS NOT NULL)
    ),
    CONSTRAINT ck_risk_appeals_authority CHECK (
        appellant_authority IN ('SUBJECT', 'ACCOUNT_OWNER', 'AUTHORIZED_AGENT', 'LEGAL_GUARDIAN')
    ),
    CONSTRAINT ck_risk_appeals_round CHECK (appeal_round > 0),
    CONSTRAINT ck_risk_appeals_state CHECK (
        state IN ('SUBMITTED', 'ACKNOWLEDGED', 'IN_REVIEW', 'DECIDED', 'WITHDRAWN', 'EXPIRED')
    ),
    -- An independent reviewer means a different person. Where the original decision was automated
    -- there is nobody to exclude, and the column is null.
    CONSTRAINT ck_risk_appeals_independence CHECK (
        assigned_reviewer_account_holder_id IS NULL
            OR original_decider_account_holder_id IS NULL
            OR assigned_reviewer_account_holder_id <> original_decider_account_holder_id
    ),
    CONSTRAINT ck_risk_appeals_outcome CHECK (
        outcome IS NULL
            OR outcome IN ('ORIGINAL_CONFIRMED', 'GRANTED', 'PARTIALLY_GRANTED', 'OUT_OF_SCOPE')
    ),
    CONSTRAINT ck_risk_appeals_decided CHECK (
        (state = 'DECIDED')
            = (outcome IS NOT NULL AND decided_at IS NOT NULL AND findings_reference IS NOT NULL)
    ),
    -- Restoration is prospective and dated. A granted appeal does not erase history and does not by
    -- itself create a monetary entitlement -- any remedy is a referral to support, which owns it.
    CONSTRAINT ck_risk_appeals_restoration CHECK (
        outcome IS NULL OR outcome NOT IN ('GRANTED', 'PARTIALLY_GRANTED')
            OR restoration_effective_from IS NOT NULL
    ),
    CONSTRAINT ck_risk_appeals_supersession CHECK (
        superseding_decision_id IS NULL
            OR outcome IN ('GRANTED', 'PARTIALLY_GRANTED')
    ),
    CONSTRAINT ck_risk_appeals_acknowledged CHECK (
        state = 'SUBMITTED' OR acknowledged_at IS NOT NULL
    ),
    CONSTRAINT ck_risk_appeals_version CHECK (version >= 0)
);

-- One appeal per target, appellant and round. A second submission is either the same appeal being
-- retried, which must collapse, or a new round, which must say so.
CREATE UNIQUE INDEX uk_risk_appeals_round
    ON risk_appeals (target_kind, risk_decision_id, risk_restriction_id, moderation_decision_id,
                     appellant_subject_id, appeal_round)
    NULLS NOT DISTINCT;

CREATE INDEX idx_risk_appeals_open ON risk_appeals (deadline_at)
    WHERE state IN ('SUBMITTED', 'ACKNOWLEDGED', 'IN_REVIEW');
--rollback DROP TABLE risk_appeals;

--changeset ninggiangboy:027-20-risk-access-audit
-- Who looked at protected risk evidence, why, and under what authority. Migration 012's audit_events
-- records what changed; this records what was read, which is the half that matters when the abuse is
-- a reviewer browsing rather than a reviewer acting.
CREATE TABLE risk_access_audit (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_account_holder_id     UUID NOT NULL,
    auth_session_id             UUID,
    access_kind                 VARCHAR(16) NOT NULL,
    resource_type               VARCHAR(48) NOT NULL,
    resource_id                 UUID,
    resource_selector           VARCHAR(255),
    record_count                INTEGER,
    declared_purpose            VARCHAR(64) NOT NULL,
    purpose_reason              VARCHAR(255) NOT NULL,
    risk_review_task_id         UUID,
    approval_reference          VARCHAR(128),
    break_glass                 BOOLEAN NOT NULL DEFAULT FALSE,
    break_glass_reference       VARCHAR(128),
    post_use_review_required    BOOLEAN NOT NULL DEFAULT FALSE,
    outcome                     VARCHAR(16) NOT NULL,
    accessed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_access_audit_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_access_audit_session FOREIGN KEY (auth_session_id)
        REFERENCES auth_sessions (id),
    CONSTRAINT fk_risk_access_audit_task FOREIGN KEY (risk_review_task_id)
        REFERENCES risk_review_tasks (id),
    CONSTRAINT ck_risk_access_audit_kind CHECK (
        access_kind IN ('VIEW', 'EXPORT', 'CHANGE', 'QUERY')
    ),
    -- An export is a bulk disclosure. It needs an approval reference and it must say how much left,
    -- because "an export happened" without a size is not an audit record.
    CONSTRAINT ck_risk_access_audit_export CHECK (
        access_kind <> 'EXPORT' OR (approval_reference IS NOT NULL AND record_count IS NOT NULL)
    ),
    -- Break-glass always carries its grant and always earns a post-use review. Tying the two
    -- together here means the review cannot be skipped by forgetting to set a flag.
    CONSTRAINT ck_risk_access_audit_break_glass CHECK (
        break_glass = (break_glass_reference IS NOT NULL)
        AND (NOT break_glass OR post_use_review_required)
    ),
    CONSTRAINT ck_risk_access_audit_outcome CHECK (
        outcome IN ('PERMITTED', 'DENIED', 'PARTIAL', 'ERRORED')
    ),
    CONSTRAINT ck_risk_access_audit_count CHECK (record_count IS NULL OR record_count >= 0)
);

CREATE INDEX idx_risk_access_audit_actor ON risk_access_audit (actor_account_holder_id, accessed_at);
CREATE INDEX idx_risk_access_audit_break_glass ON risk_access_audit (accessed_at)
    WHERE break_glass;
--rollback DROP TABLE risk_access_audit;

--changeset ninggiangboy:027-21-risk-velocity-counters
-- A bounded count or amount over an event-time window, held as a row so a strong transaction limit
-- can be enforced by locking it rather than by hoping two requests do not arrive together.
CREATE TABLE risk_velocity_counters (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_key                VARCHAR(64) NOT NULL,
    dimension_digest        CHAR(64) NOT NULL,
    dimension_document      JSONB NOT NULL,
    window_start            TIMESTAMPTZ NOT NULL,
    window_end              TIMESTAMPTZ NOT NULL,
    event_count             BIGINT NOT NULL DEFAULT 0,
    distinct_entity_count   BIGINT NOT NULL DEFAULT 0,
    amount_minor            BIGINT NOT NULL DEFAULT 0,
    currency                VARCHAR(3),
    threshold_count         BIGINT,
    threshold_amount_minor  BIGINT,
    late_event_count        BIGINT NOT NULL DEFAULT 0,
    last_event_time         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_risk_velocity_counters_window
        UNIQUE (rule_key, dimension_digest, window_start, window_end),
    CONSTRAINT ck_risk_velocity_counters_digest CHECK (dimension_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_risk_velocity_counters_window CHECK (window_end > window_start),
    CONSTRAINT ck_risk_velocity_counters_counts CHECK (
        event_count >= 0 AND distinct_entity_count >= 0 AND amount_minor >= 0
        AND late_event_count >= 0 AND late_event_count <= event_count
    ),
    CONSTRAINT ck_risk_velocity_counters_distinct CHECK (distinct_entity_count <= event_count),
    CONSTRAINT ck_risk_velocity_counters_currency CHECK (
        (amount_minor = 0 AND currency IS NULL) OR currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_risk_velocity_counters_thresholds CHECK (
        (threshold_count IS NULL OR threshold_count > 0)
        AND (threshold_amount_minor IS NULL OR threshold_amount_minor > 0)
    ),
    CONSTRAINT ck_risk_velocity_counters_version CHECK (version >= 0)
);
--rollback DROP TABLE risk_velocity_counters;

--changeset ninggiangboy:027-22-risk-velocity-contributions
-- Which event moved which counter. The unique key is the source event, so a redelivery increments
-- nothing and a counter can be rebuilt from its own history rather than trusted.
CREATE TABLE risk_velocity_contributions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_velocity_counter_id    UUID NOT NULL,
    source_event_id             VARCHAR(128) NOT NULL,
    risk_signal_id              UUID,
    delta_count                 BIGINT NOT NULL DEFAULT 0,
    delta_amount_minor          BIGINT NOT NULL DEFAULT 0,
    event_time                  TIMESTAMPTZ NOT NULL,
    late_arrival                BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_risk_velocity_contributions_counter FOREIGN KEY (risk_velocity_counter_id)
        REFERENCES risk_velocity_counters (id),
    CONSTRAINT fk_risk_velocity_contributions_signal FOREIGN KEY (risk_signal_id)
        REFERENCES risk_signals (id),
    CONSTRAINT uk_risk_velocity_contributions_event
        UNIQUE (risk_velocity_counter_id, source_event_id),
    CONSTRAINT ck_risk_velocity_contributions_delta CHECK (
        delta_count >= 0 AND delta_amount_minor >= 0 AND (delta_count > 0 OR delta_amount_minor > 0)
    )
);
--rollback DROP TABLE risk_velocity_contributions;

--changeset ninggiangboy:027-23-content-items
-- A stable handle for something somebody wrote. The text itself stays in the domain that owns it;
-- what lives here is the identity moderation decisions can be attached to without this domain
-- becoming a second copy of every message and listing in the platform.
CREATE TABLE content_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owning_domain           VARCHAR(32) NOT NULL,
    content_type            VARCHAR(32) NOT NULL,
    resource_type           VARCHAR(32) NOT NULL,
    resource_id             UUID NOT NULL,
    author_subject_id       UUID,
    audience_scope          VARCHAR(24) NOT NULL,
    market_id               UUID,
    lifecycle               VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_content_items_author FOREIGN KEY (author_subject_id) REFERENCES risk_subjects (id),
    CONSTRAINT fk_content_items_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT uk_content_items_resource
        UNIQUE (owning_domain, content_type, resource_type, resource_id),
    CONSTRAINT ck_content_items_domain CHECK (
        owning_domain IN ('LISTING', 'MESSAGING', 'REVIEW', 'IDENTITY', 'SUPPORT',
                          'STAY_OPERATIONS')
    ),
    CONSTRAINT ck_content_items_type CHECK (
        content_type IN ('LISTING_TEXT', 'LISTING_MEDIA', 'MESSAGE', 'MESSAGE_ATTACHMENT',
                         'REVIEW_TEXT', 'REVIEW_RESPONSE', 'REVIEW_MEDIA', 'PROFILE_FIELD',
                         'PROFILE_MEDIA', 'SUPPORT_NOTE', 'INCIDENT_REPORT')
    ),
    CONSTRAINT ck_content_items_audience CHECK (
        audience_scope IN ('PUBLIC', 'AUTHENTICATED', 'PARTICIPANTS', 'PRIVATE', 'INTERNAL')
    ),
    CONSTRAINT ck_content_items_lifecycle CHECK (
        lifecycle IN ('ACTIVE', 'WITHDRAWN', 'DELETED_BY_OWNER', 'RETAINED_EVIDENCE')
    ),
    CONSTRAINT ck_content_items_version CHECK (version >= 0)
);

CREATE INDEX idx_content_items_author ON content_items (author_subject_id, created_at)
    WHERE author_subject_id IS NOT NULL;
--rollback DROP TABLE content_items;

--changeset ninggiangboy:027-24-content-revisions
-- Moderation happens against an exact revision, never against "the listing". An edit is a new
-- revision, and a decision taken on the old one does not carry over -- which is the only way a clean
-- text approval cannot be made to publish a changed link.
CREATE TABLE content_revisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_item_id             UUID NOT NULL,
    revision_number             INTEGER NOT NULL,
    language_tag                VARCHAR(35),
    body_reference              VARCHAR(255),
    body_digest                 CHAR(64) NOT NULL,
    attachment_count            SMALLINT NOT NULL DEFAULT 0,
    attachment_scan_state       VARCHAR(16),
    contains_links              BOOLEAN NOT NULL DEFAULT FALSE,
    client_submission_id        VARCHAR(128),
    author_subject_id           UUID,
    visibility_default          VARCHAR(16) NOT NULL,
    submitted_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_content_revisions_item FOREIGN KEY (content_item_id) REFERENCES content_items (id),
    CONSTRAINT fk_content_revisions_author FOREIGN KEY (author_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT uk_content_revisions_number UNIQUE (content_item_id, revision_number),
    CONSTRAINT ck_content_revisions_number CHECK (revision_number > 0),
    CONSTRAINT ck_content_revisions_digest CHECK (body_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_content_revisions_attachments CHECK (
        attachment_count >= 0
        AND (attachment_count > 0) = (attachment_scan_state IS NOT NULL)
    ),
    CONSTRAINT ck_content_revisions_scan_state CHECK (
        attachment_scan_state IS NULL
            OR attachment_scan_state IN ('PENDING', 'CLEAN', 'INFECTED', 'UNSUPPORTED', 'FAILED')
    ),
    -- An attachment stays quarantined until validation and scanning pass. A revision carrying an
    -- unscanned file may not default to visible.
    CONSTRAINT ck_content_revisions_quarantine CHECK (
        attachment_scan_state IS NULL OR attachment_scan_state = 'CLEAN'
            OR visibility_default = 'QUARANTINED'
    ),
    CONSTRAINT ck_content_revisions_visibility CHECK (
        visibility_default IN ('VISIBLE', 'PENDING', 'QUARANTINED', 'HIDDEN')
    )
);

-- Retried submissions must collapse onto one revision rather than creating a second identical one
-- that a reviewer then has to decide about twice.
CREATE UNIQUE INDEX uk_content_revisions_submission
    ON content_revisions (content_item_id, client_submission_id)
    WHERE client_submission_id IS NOT NULL;
--rollback DROP TABLE content_revisions;

--changeset ninggiangboy:027-25-moderation-assessments
-- What a detector said about one revision. Assessments are evidence, not outcomes: several detectors
-- may disagree about the same text and all of them are kept, because a decision that cites only the
-- detector that agreed with it cannot be audited.
CREATE TABLE moderation_assessments (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_revision_id         UUID NOT NULL,
    detector_key                VARCHAR(64) NOT NULL,
    detector_kind               VARCHAR(16) NOT NULL,
    detector_version            VARCHAR(32) NOT NULL,
    policy_reference            VARCHAR(128),
    category_scores             JSONB NOT NULL,
    top_category                VARCHAR(48),
    protected_span_reference    VARCHAR(255),
    scan_evidence_reference     VARCHAR(255),
    language_detected           VARCHAR(35),
    routing_recommendation      VARCHAR(24) NOT NULL,
    serving_fallback            VARCHAR(24) NOT NULL DEFAULT 'NONE',
    assessed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_moderation_assessments_revision FOREIGN KEY (content_revision_id)
        REFERENCES content_revisions (id),
    CONSTRAINT uk_moderation_assessments_detector
        UNIQUE (content_revision_id, detector_key, detector_version),
    CONSTRAINT ck_moderation_assessments_kind CHECK (
        detector_kind IN ('DETERMINISTIC', 'CLASSIFIER', 'MALWARE', 'LINK_CHECK', 'HUMAN')
    ),
    -- A recommendation is where the item should go next, never what should happen to it. The
    -- outcome vocabulary lives on the decision table and nowhere else.
    CONSTRAINT ck_moderation_assessments_routing CHECK (
        routing_recommendation IN ('NO_ACTION', 'HUMAN_REVIEW', 'URGENT_SAFETY', 'HOLD_FOR_POLICY')
    ),
    CONSTRAINT ck_moderation_assessments_fallback CHECK (
        serving_fallback IN ('NONE', 'TIMEOUT', 'KILL_SWITCH', 'UNAVAILABLE', 'UNSUPPORTED_LANGUAGE')
    ),
    -- A detector that did not run has no scores to offer. Recording one anyway is how an outage
    -- becomes a clean bill of health.
    CONSTRAINT ck_moderation_assessments_served CHECK (
        serving_fallback = 'NONE' OR top_category IS NULL
    ),
    -- A malware scan that found something must keep the evidence of what it found.
    CONSTRAINT ck_moderation_assessments_scan_evidence CHECK (
        detector_kind <> 'MALWARE' OR scan_evidence_reference IS NOT NULL
    )
);
--rollback DROP TABLE moderation_assessments;

--changeset ninggiangboy:027-26-moderation-decisions
-- The outcome for one revision, with the visibility instruction the owning domain is expected to
-- carry out. Removing something later is a new decision, not an edit of the one that published it.
CREATE TABLE moderation_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_revision_id         UUID NOT NULL,
    outcome                     VARCHAR(20) NOT NULL,
    visibility_instruction      VARCHAR(24) NOT NULL,
    risk_policy_id              UUID,
    policy_reference            VARCHAR(128),
    internal_reasons            TEXT[] NOT NULL,
    user_reason_family          VARCHAR(48),
    span_map_reference          VARCHAR(255),
    decider_type                VARCHAR(16) NOT NULL,
    decided_by_account_holder_id UUID,
    risk_review_task_id         UUID,
    state                       VARCHAR(16) NOT NULL DEFAULT 'EFFECTIVE',
    review_deadline_at          TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ,
    supersedes_decision_id      UUID,
    superseded_by_decision_id   UUID,
    decided_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_moderation_decisions_revision FOREIGN KEY (content_revision_id)
        REFERENCES content_revisions (id),
    CONSTRAINT fk_moderation_decisions_policy FOREIGN KEY (risk_policy_id)
        REFERENCES risk_policies (id),
    CONSTRAINT fk_moderation_decisions_decider FOREIGN KEY (decided_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_moderation_decisions_task FOREIGN KEY (risk_review_task_id)
        REFERENCES risk_review_tasks (id),
    CONSTRAINT fk_moderation_decisions_supersedes FOREIGN KEY (supersedes_decision_id)
        REFERENCES moderation_decisions (id),
    -- Deferred for the same reason as the restriction lineage: one effective decision per revision
    -- means the standing decision must point at its successor before the successor is written.
    CONSTRAINT fk_moderation_decisions_superseded_by FOREIGN KEY (superseded_by_decision_id)
        REFERENCES moderation_decisions (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_moderation_decisions_outcome CHECK (
        outcome IN ('PUBLISH', 'MASK', 'WARN', 'QUARANTINE', 'REJECT', 'REMOVE', 'ESCALATE_SAFETY')
    ),
    CONSTRAINT ck_moderation_decisions_visibility CHECK (
        visibility_instruction IN ('VISIBLE', 'VISIBLE_MASKED', 'VISIBLE_WITH_WARNING', 'HIDDEN',
                                   'NOT_PUBLISHED', 'UNCHANGED')
    ),
    -- Urgent safety routing decides nothing about visibility. Treating an escalation as a removal is
    -- how a report gets answered by hiding it instead of acting on it.
    CONSTRAINT ck_moderation_decisions_escalation CHECK (
        outcome <> 'ESCALATE_SAFETY' OR visibility_instruction = 'UNCHANGED'
    ),
    -- Quarantine is hiding something while a person looks at it. Without a deadline it is removal
    -- under a gentler name.
    CONSTRAINT ck_moderation_decisions_quarantine CHECK (
        outcome <> 'QUARANTINE' OR (review_deadline_at IS NOT NULL
                                    AND visibility_instruction = 'HIDDEN')
    ),
    -- Masking keeps the original as protected evidence and records where the replaced spans were.
    CONSTRAINT ck_moderation_decisions_mask CHECK (
        (outcome = 'MASK') = (span_map_reference IS NOT NULL)
    ),
    CONSTRAINT ck_moderation_decisions_mask_visibility CHECK (
        outcome <> 'MASK' OR visibility_instruction = 'VISIBLE_MASKED'
    ),
    -- Anything the author is told about carries an approved reason family.
    CONSTRAINT ck_moderation_decisions_adverse_reason CHECK (
        outcome NOT IN ('REJECT', 'REMOVE', 'QUARANTINE') OR user_reason_family IS NOT NULL
    ),
    CONSTRAINT ck_moderation_decisions_reasons CHECK (cardinality(internal_reasons) > 0),
    CONSTRAINT ck_moderation_decisions_decider CHECK (
        decider_type IN ('AUTOMATION', 'REVIEWER', 'APPEAL')
        AND (decider_type = 'AUTOMATION') = (decided_by_account_holder_id IS NULL)
    ),
    CONSTRAINT ck_moderation_decisions_state CHECK (
        state IN ('EFFECTIVE', 'EXPIRED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_moderation_decisions_supersession CHECK (
        (state = 'SUPERSEDED') = (superseded_by_decision_id IS NOT NULL)
    ),
    CONSTRAINT ck_moderation_decisions_self_supersession CHECK (
        superseded_by_decision_id IS NULL OR superseded_by_decision_id <> id
    ),
    CONSTRAINT ck_moderation_decisions_expiry CHECK (expires_at IS NULL OR expires_at > decided_at),
    CONSTRAINT ck_moderation_decisions_version CHECK (version >= 0)
);

-- One effective decision per revision. Two live answers about the same text is the state in which
-- the owning domain has to guess which one to obey.
CREATE UNIQUE INDEX uk_moderation_decisions_effective
    ON moderation_decisions (content_revision_id)
    WHERE state = 'EFFECTIVE';

CREATE INDEX idx_moderation_decisions_quarantine_review
    ON moderation_decisions (review_deadline_at)
    WHERE state = 'EFFECTIVE' AND outcome = 'QUARANTINE';

ALTER TABLE risk_appeals
    ADD CONSTRAINT fk_risk_appeals_moderation_decision FOREIGN KEY (moderation_decision_id)
        REFERENCES moderation_decisions (id);
--rollback ALTER TABLE risk_appeals DROP CONSTRAINT fk_risk_appeals_moderation_decision;
--rollback DROP TABLE moderation_decisions;

--changeset ninggiangboy:027-27-content-reports
-- Somebody's allegation about an exact revision. A report is evidence that a complaint was made, not
-- evidence that the complaint is true, and the reporter's identity is not the reported party's to
-- learn.
CREATE TABLE content_reports (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_revision_id             UUID NOT NULL,
    reporter_subject_id             UUID NOT NULL,
    reporter_relationship           VARCHAR(24) NOT NULL,
    category                        VARCHAR(48) NOT NULL,
    severity_band                   VARCHAR(16) NOT NULL,
    allegation_reference            VARCHAR(255),
    language_tag                    VARCHAR(35),
    dedupe_group_id                 UUID,
    risk_review_task_id             UUID,
    reporter_disclosure             VARCHAR(16) NOT NULL DEFAULT 'CONFIDENTIAL',
    disclosure_authorization_reference VARCHAR(128),
    acknowledged_at                 TIMESTAMPTZ,
    adjudicated_outcome             VARCHAR(24),
    adjudicated_at                  TIMESTAMPTZ,
    moderation_decision_id          UUID,
    event_time                      TIMESTAMPTZ NOT NULL,
    reported_at                     TIMESTAMPTZ NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_content_reports_revision FOREIGN KEY (content_revision_id)
        REFERENCES content_revisions (id),
    CONSTRAINT fk_content_reports_reporter FOREIGN KEY (reporter_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT fk_content_reports_task FOREIGN KEY (risk_review_task_id)
        REFERENCES risk_review_tasks (id),
    CONSTRAINT fk_content_reports_decision FOREIGN KEY (moderation_decision_id)
        REFERENCES moderation_decisions (id),
    CONSTRAINT ck_content_reports_relationship CHECK (
        reporter_relationship IN ('PARTICIPANT', 'AUDIENCE', 'HOST', 'GUEST', 'THIRD_PARTY',
                                  'AUTHORITY', 'PLATFORM')
    ),
    CONSTRAINT ck_content_reports_severity CHECK (
        severity_band IN ('URGENT_SAFETY', 'HIGH', 'STANDARD', 'LOW')
    ),
    -- The reported party never learns who reported them unless disclosure was separately authorized,
    -- which is a decision with a reference, not a default.
    CONSTRAINT ck_content_reports_disclosure CHECK (
        reporter_disclosure IN ('CONFIDENTIAL', 'AUTHORITY_ONLY', 'DISCLOSED')
        AND (reporter_disclosure = 'CONFIDENTIAL')
            = (disclosure_authorization_reference IS NULL)
    ),
    CONSTRAINT ck_content_reports_adjudication CHECK (
        (adjudicated_outcome IS NULL) = (adjudicated_at IS NULL)
        AND (adjudicated_outcome IS NULL
             OR adjudicated_outcome IN ('SUBSTANTIATED', 'UNSUBSTANTIATED', 'OUT_OF_SCOPE',
                                        'DUPLICATE', 'ABUSIVE_REPORT'))
    ),
    -- An urgent safety report is acknowledged to its reporter. The intake path must stay open even
    -- to somebody the platform has otherwise restricted.
    CONSTRAINT ck_content_reports_urgent_ack CHECK (
        severity_band <> 'URGENT_SAFETY' OR risk_review_task_id IS NOT NULL
    ),
    CONSTRAINT ck_content_reports_version CHECK (version >= 0)
);

-- One reporter, one revision, one category, one report. Repeat submissions are the same complaint;
-- brigading is many reporters, which stays visible as many rows.
CREATE UNIQUE INDEX uk_content_reports_reporter
    ON content_reports (content_revision_id, reporter_subject_id, category);

CREATE INDEX idx_content_reports_group ON content_reports (dedupe_group_id)
    WHERE dedupe_group_id IS NOT NULL;
CREATE INDEX idx_content_reports_open ON content_reports (severity_band, reported_at)
    WHERE adjudicated_at IS NULL;
--rollback DROP TABLE content_reports;

--changeset ninggiangboy:027-28-entity-links
-- A time-bounded, confidence-scored relationship between two subjects. Shared addresses, devices and
-- networks are the everyday condition of hotels, families and offices, so a link is a question rather
-- than a finding, and the column that says whether it may justify an adverse decision has
-- corroboration behind it as a check.
CREATE TABLE entity_links (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    endpoint_a_subject_id       UUID NOT NULL,
    endpoint_b_subject_id       UUID NOT NULL,
    relation                    VARCHAR(40) NOT NULL,
    direction                   VARCHAR(16) NOT NULL DEFAULT 'UNDIRECTED',
    evidence_class              VARCHAR(24) NOT NULL,
    confidence                  NUMERIC(5, 4) NOT NULL,
    corroborating_evidence_count SMALLINT NOT NULL DEFAULT 1,
    observation_count           BIGINT NOT NULL DEFAULT 1,
    source_domain               VARCHAR(32) NOT NULL,
    permitted_purposes          TEXT[] NOT NULL,
    adverse_use_permitted       BOOLEAN NOT NULL DEFAULT FALSE,
    first_seen_at               TIMESTAMPTZ NOT NULL,
    last_seen_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_entity_links_endpoint_a FOREIGN KEY (endpoint_a_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT fk_entity_links_endpoint_b FOREIGN KEY (endpoint_b_subject_id)
        REFERENCES risk_subjects (id),
    CONSTRAINT uk_entity_links_pair
        UNIQUE (endpoint_a_subject_id, endpoint_b_subject_id, relation),
    CONSTRAINT ck_entity_links_distinct CHECK (endpoint_a_subject_id <> endpoint_b_subject_id),
    CONSTRAINT ck_entity_links_relation CHECK (
        relation IN ('SHARED_DEVICE', 'SHARED_NETWORK', 'SHARED_INSTRUMENT', 'SHARED_PAYOUT',
                     'SHARED_ADDRESS', 'SHARED_CONTACT', 'REFERRAL', 'BOOKED_WITH', 'REVIEWED',
                     'MESSAGED', 'ORGANIZATION_MEMBER', 'SUSPECTED_DUPLICATE')
    ),
    CONSTRAINT ck_entity_links_direction CHECK (
        direction IN ('UNDIRECTED', 'A_TO_B', 'B_TO_A')
    ),
    -- An undirected pair is stored once, in a canonical order. Without this the same relationship can
    -- exist twice with two different confidences.
    CONSTRAINT ck_entity_links_canonical CHECK (
        direction <> 'UNDIRECTED' OR endpoint_a_subject_id < endpoint_b_subject_id
    ),
    CONSTRAINT ck_entity_links_evidence_class CHECK (
        evidence_class IN ('VERIFIED', 'AUTHENTICATED', 'CORROBORATED', 'DERIVED', 'INFERRED')
    ),
    CONSTRAINT ck_entity_links_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_entity_links_counts CHECK (
        corroborating_evidence_count >= 1 AND observation_count >= 1
    ),
    CONSTRAINT ck_entity_links_purposes CHECK (cardinality(permitted_purposes) > 0),
    -- A weak association alone cannot authorize a high-impact adverse decision. Independent
    -- corroboration and a confidence the platform is prepared to defend are the price of that use.
    CONSTRAINT ck_entity_links_adverse_use CHECK (
        NOT adverse_use_permitted
            OR (corroborating_evidence_count >= 2 AND confidence >= 0.8
                AND evidence_class IN ('VERIFIED', 'AUTHENTICATED', 'CORROBORATED'))
    ),
    CONSTRAINT ck_entity_links_seen CHECK (last_seen_at >= first_seen_at),
    -- Links expire. A relationship observed once three years ago is not evidence about today, and
    -- letting it persist is how a graph becomes a permanent record of who somebody once shared a
    -- hotel network with.
    CONSTRAINT ck_entity_links_expiry CHECK (expires_at > last_seen_at),
    CONSTRAINT ck_entity_links_version CHECK (version >= 0)
);

CREATE INDEX idx_entity_links_endpoint_a ON entity_links (endpoint_a_subject_id, relation);
CREATE INDEX idx_entity_links_endpoint_b ON entity_links (endpoint_b_subject_id, relation);
--rollback DROP TABLE entity_links;

--changeset ninggiangboy:027-29-risk-label-taxonomy-versions
-- What the labels mean, versioned. A model trained against last year's taxonomy must be able to say
-- which vocabulary its ground truth was written in.
CREATE TABLE risk_label_taxonomy_versions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxonomy_key            VARCHAR(48) NOT NULL,
    taxonomy_version        INTEGER NOT NULL,
    label_values            TEXT[] NOT NULL,
    definition_reference    VARCHAR(255) NOT NULL,
    owner_team              VARCHAR(64) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from          TIMESTAMPTZ,
    effective_until         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_risk_label_taxonomy_versions_version UNIQUE (taxonomy_key, taxonomy_version),
    CONSTRAINT ck_risk_label_taxonomy_versions_values CHECK (cardinality(label_values) > 0),
    CONSTRAINT ck_risk_label_taxonomy_versions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_risk_label_taxonomy_versions_effective CHECK (
        (status = 'DRAFT') = (effective_from IS NULL)
    ),
    CONSTRAINT ck_risk_label_taxonomy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_risk_label_taxonomy_versions_version_counter CHECK (version >= 0)
);
--rollback DROP TABLE risk_label_taxonomy_versions;

--changeset ninggiangboy:027-30-risk-labels
-- A post-event outcome, adjudicated, with the window it is true over and the instant it became
-- available. Labels are not copied from adverse events: a decision to deny is not proof that denying
-- was right, and a model may not treat its own output as the answer it was graded against.
CREATE TABLE risk_labels (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_label_taxonomy_version_id  UUID NOT NULL,
    subject_id                      UUID NOT NULL,
    protected_action                VARCHAR(64),
    label_value                     VARCHAR(48) NOT NULL,
    confidence                      NUMERIC(5, 4),
    source_kind                     VARCHAR(24) NOT NULL,
    source_outcome_reference        VARCHAR(128),
    evidence_reference              VARCHAR(255),
    risk_decision_id                UUID,
    adjudication_state              VARCHAR(24) NOT NULL DEFAULT 'PROVISIONAL',
    adjudicated_by_account_holder_id UUID,
    training_eligible               BOOLEAN NOT NULL DEFAULT FALSE,
    applicable_from                 TIMESTAMPTZ NOT NULL,
    applicable_until                TIMESTAMPTZ,
    observed_at                     TIMESTAMPTZ NOT NULL,
    available_for_training_at       TIMESTAMPTZ NOT NULL,
    supersedes_label_id             UUID,
    superseded_by_label_id          UUID,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_risk_labels_taxonomy FOREIGN KEY (risk_label_taxonomy_version_id)
        REFERENCES risk_label_taxonomy_versions (id),
    CONSTRAINT fk_risk_labels_subject FOREIGN KEY (subject_id) REFERENCES risk_subjects (id),
    CONSTRAINT fk_risk_labels_decision FOREIGN KEY (risk_decision_id) REFERENCES risk_decisions (id),
    CONSTRAINT fk_risk_labels_adjudicator FOREIGN KEY (adjudicated_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_risk_labels_supersedes FOREIGN KEY (supersedes_label_id) REFERENCES risk_labels (id),
    -- Deferred for the same reason again: the live index keys on the supersession pointer being
    -- null, so a reversal must fill it in before the corrected label row exists.
    CONSTRAINT fk_risk_labels_superseded_by FOREIGN KEY (superseded_by_label_id)
        REFERENCES risk_labels (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_risk_labels_source_kind CHECK (
        source_kind IN ('HUMAN_ADJUDICATION', 'PROVIDER_OUTCOME', 'DOMAIN_EVENT',
                        'AUTOMATED_DECISION', 'USER_REPORT')
    ),
    -- A rule or model that caused the review must not train on its own decision as ground truth, and
    -- an unverified report is not ground truth either.
    CONSTRAINT ck_risk_labels_training_source CHECK (
        NOT training_eligible OR source_kind NOT IN ('AUTOMATED_DECISION', 'USER_REPORT')
    ),
    CONSTRAINT ck_risk_labels_adjudication CHECK (
        adjudication_state IN ('PROVISIONAL', 'CONFIRMED', 'DISPUTED', 'REVERSED',
                               'EXPIRED_FOR_TRAINING')
    ),
    -- Only a confirmed label with evidence behind it may be trained on, and confirmation by a person
    -- names the person.
    CONSTRAINT ck_risk_labels_training_state CHECK (
        NOT training_eligible
            OR (adjudication_state = 'CONFIRMED' AND evidence_reference IS NOT NULL)
    ),
    CONSTRAINT ck_risk_labels_human_adjudication CHECK (
        source_kind <> 'HUMAN_ADJUDICATION' OR adjudicated_by_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_risk_labels_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    ),
    -- The maturation window. A label becomes usable for training strictly after the outcome that
    -- produced it was observed, so a model cannot be handed the answer at the moment it was asked.
    CONSTRAINT ck_risk_labels_maturation CHECK (available_for_training_at > observed_at),
    CONSTRAINT ck_risk_labels_applicable CHECK (
        applicable_until IS NULL OR applicable_until > applicable_from
    ),
    CONSTRAINT ck_risk_labels_supersession CHECK (
        superseded_by_label_id IS NULL OR superseded_by_label_id <> id
    ),
    CONSTRAINT ck_risk_labels_version CHECK (version >= 0)
);

-- One live label per subject, action and taxonomy. A reversal supersedes rather than coexisting,
-- because two contradictory answers in a training set are worse than neither.
CREATE UNIQUE INDEX uk_risk_labels_live
    ON risk_labels (risk_label_taxonomy_version_id, subject_id, protected_action)
    NULLS NOT DISTINCT
    WHERE superseded_by_label_id IS NULL;

CREATE INDEX idx_risk_labels_training
    ON risk_labels (risk_label_taxonomy_version_id, available_for_training_at)
    WHERE training_eligible;
--rollback DROP TABLE risk_labels;

--changeset ninggiangboy:027-31-risk-evidence-append-only splitStatements:false
-- Evidence is immutable; interpretation is versioned. Every table below is something that was
-- observed, computed, tried, read or asserted at a point in time, and a correction to any of them is
-- a new row that names the one it corrects. Editing one in place would rewrite the reason for every
-- decision already taken on it, which is exactly what makes a protection system unappealable.
--
-- One function serves all of them: the rule is identical, and eleven copies of the same six lines is
-- eleven places for the rule to drift.
CREATE FUNCTION risk_evidence_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; row % cannot be % -- record a correcting row instead',
        TG_TABLE_NAME, OLD.id, lower(TG_OP)
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_signals_append_only
    BEFORE UPDATE OR DELETE ON risk_signals
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_signal_subjects_append_only
    BEFORE UPDATE OR DELETE ON risk_signal_subjects
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_feature_snapshots_append_only
    BEFORE UPDATE OR DELETE ON risk_feature_snapshots
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_model_predictions_append_only
    BEFORE UPDATE OR DELETE ON risk_model_predictions
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_decision_rule_hits_append_only
    BEFORE UPDATE OR DELETE ON risk_decision_rule_hits
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_decision_enforcements_append_only
    BEFORE UPDATE OR DELETE ON risk_decision_enforcements
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_challenge_attempts_append_only
    BEFORE UPDATE OR DELETE ON risk_challenge_attempts
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_policy_approvals_append_only
    BEFORE UPDATE OR DELETE ON risk_policy_approvals
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_review_actions_append_only
    BEFORE UPDATE OR DELETE ON risk_review_actions
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_access_audit_append_only
    BEFORE UPDATE OR DELETE ON risk_access_audit
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_risk_velocity_contributions_append_only
    BEFORE UPDATE OR DELETE ON risk_velocity_contributions
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_content_revisions_append_only
    BEFORE UPDATE OR DELETE ON content_revisions
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();

CREATE TRIGGER trg_moderation_assessments_append_only
    BEFORE UPDATE OR DELETE ON moderation_assessments
    FOR EACH ROW EXECUTE FUNCTION risk_evidence_append_only();
--rollback DROP TRIGGER trg_moderation_assessments_append_only ON moderation_assessments;
--rollback DROP TRIGGER trg_content_revisions_append_only ON content_revisions;
--rollback DROP TRIGGER trg_risk_velocity_contributions_append_only ON risk_velocity_contributions;
--rollback DROP TRIGGER trg_risk_access_audit_append_only ON risk_access_audit;
--rollback DROP TRIGGER trg_risk_review_actions_append_only ON risk_review_actions;
--rollback DROP TRIGGER trg_risk_policy_approvals_append_only ON risk_policy_approvals;
--rollback DROP TRIGGER trg_risk_challenge_attempts_append_only ON risk_challenge_attempts;
--rollback DROP TRIGGER trg_risk_decision_enforcements_append_only ON risk_decision_enforcements;
--rollback DROP TRIGGER trg_risk_decision_rule_hits_append_only ON risk_decision_rule_hits;
--rollback DROP TRIGGER trg_risk_model_predictions_append_only ON risk_model_predictions;
--rollback DROP TRIGGER trg_risk_feature_snapshots_append_only ON risk_feature_snapshots;
--rollback DROP TRIGGER trg_risk_signal_subjects_append_only ON risk_signal_subjects;
--rollback DROP TRIGGER trg_risk_signals_append_only ON risk_signals;
--rollback DROP FUNCTION risk_evidence_append_only();

--changeset ninggiangboy:027-32-risk-registry-immutability splitStatements:false
-- Three registries are named by rows that must keep their meaning: a decision names the policy
-- version and epoch it was taken under, a snapshot names the feature versions it was computed from,
-- and a label names the taxonomy it was written in. Editing any of them in place would silently
-- change what those stored rows say, so an approved version is frozen and a change is a new version.
--
-- The lifecycle columns stay open on purpose: retiring a version, closing its effective interval,
-- engaging a kill switch and recording a post-use review are operations on the version, not changes
-- to what it means.
CREATE FUNCTION risk_feature_definitions_freeze_approved() RETURNS TRIGGER AS $$
DECLARE
    candidate risk_feature_definitions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'feature % version % is approved and cannot be deleted',
            OLD.feature_key, OLD.feature_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'feature % version % is approved; approve a new version instead of editing it',
            OLD.feature_key, OLD.feature_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_feature_definitions_freeze
    BEFORE UPDATE OR DELETE ON risk_feature_definitions
    FOR EACH ROW WHEN (OLD.status IN ('APPROVED', 'DEPRECATED', 'RETIRED'))
    EXECUTE FUNCTION risk_feature_definitions_freeze_approved();

CREATE FUNCTION risk_policies_freeze_active() RETURNS TRIGGER AS $$
DECLARE
    candidate risk_policies%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'policy % version % has been active and cannot be deleted',
            OLD.policy_key, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.kill_switch_engaged := OLD.kill_switch_engaged;
    candidate.kill_switch_reason := OLD.kill_switch_reason;
    candidate.post_use_review_at := OLD.post_use_review_at;
    candidate.rollout_percentage := OLD.rollout_percentage;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'policy % version % is active; publish a new version instead of editing it',
            OLD.policy_key, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    -- An active policy's window may be closed but never reopened or moved earlier. Rollback selects
    -- the last approved version; it never rewrites when this one applied.
    IF OLD.effective_until IS NOT NULL
       AND (NEW.effective_until IS NULL OR NEW.effective_until > OLD.effective_until) THEN
        RAISE EXCEPTION
            'policy % version % already ended at %; its window cannot be extended',
            OLD.policy_key, OLD.policy_version, OLD.effective_until
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_policies_freeze
    BEFORE UPDATE OR DELETE ON risk_policies
    FOR EACH ROW WHEN (OLD.status IN ('ACTIVE', 'SUSPENDED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION risk_policies_freeze_active();

CREATE FUNCTION risk_label_taxonomy_versions_freeze_active() RETURNS TRIGGER AS $$
DECLARE
    candidate risk_label_taxonomy_versions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'label taxonomy % version % is active and cannot be deleted',
            OLD.taxonomy_key, OLD.taxonomy_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'label taxonomy % version % is active; issue a new version instead',
            OLD.taxonomy_key, OLD.taxonomy_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_label_taxonomy_versions_freeze
    BEFORE UPDATE OR DELETE ON risk_label_taxonomy_versions
    FOR EACH ROW WHEN (OLD.status IN ('ACTIVE', 'DEPRECATED', 'RETIRED'))
    EXECUTE FUNCTION risk_label_taxonomy_versions_freeze_active();
--rollback DROP TRIGGER trg_risk_label_taxonomy_versions_freeze ON risk_label_taxonomy_versions;
--rollback DROP FUNCTION risk_label_taxonomy_versions_freeze_active();
--rollback DROP TRIGGER trg_risk_policies_freeze ON risk_policies;
--rollback DROP FUNCTION risk_policies_freeze_active();
--rollback DROP TRIGGER trg_risk_feature_definitions_freeze ON risk_feature_definitions;
--rollback DROP FUNCTION risk_feature_definitions_freeze_approved();

--changeset ninggiangboy:027-33-risk-policy-maker-checker splitStatements:false
-- A policy that can deny money or restrict an account needs approvers who are not its author. Two of
-- them, both distinct from whoever wrote it, and none of them having rejected it. The count is a
-- cross-row fact no column can express, so it is checked where the activation happens rather than in
-- the pipeline that is supposed to have checked it already.
--
-- An emergency change is exempt from the count and not from accountability: its own check forces an
-- expiry and a post-use review date, so it ends and somebody looks at it afterwards.
CREATE FUNCTION risk_policy_approvals_guard_author() RETURNS TRIGGER AS $$
DECLARE
    policy_author UUID;
BEGIN
    SELECT authored_by_account_holder_id INTO policy_author
      FROM risk_policies WHERE id = NEW.risk_policy_id;

    -- NOT FOUND is tested explicitly: the foreign key has not fired yet on an insert, and a NULL
    -- comparison would let the write through.
    IF NOT FOUND THEN
        RAISE EXCEPTION 'risk policy % does not exist', NEW.risk_policy_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF policy_author = NEW.approver_account_holder_id THEN
        RAISE EXCEPTION
            'account holder % authored risk policy % and cannot approve it',
            NEW.approver_account_holder_id, NEW.risk_policy_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_policy_approvals_guard_author
    BEFORE INSERT ON risk_policy_approvals
    FOR EACH ROW EXECUTE FUNCTION risk_policy_approvals_guard_author();

CREATE FUNCTION risk_policies_guard_activation() RETURNS TRIGGER AS $$
DECLARE
    approval_count INTEGER;
    rejection_count INTEGER;
BEGIN
    -- INSERT is covered as well as UPDATE. A row written straight into the active state would
    -- otherwise skip every approval the update path enforces, which is the same defect migration 022
    -- found when a balanced pair of postings could be appended to a posted transaction.
    IF TG_OP = 'UPDATE' AND OLD.status = 'ACTIVE' THEN
        RETURN NEW;
    END IF;

    SELECT count(*) FILTER (WHERE decision = 'APPROVED'),
           count(*) FILTER (WHERE decision = 'REJECTED')
      INTO approval_count, rejection_count
      FROM risk_policy_approvals WHERE risk_policy_id = NEW.id;

    IF rejection_count > 0 THEN
        RAISE EXCEPTION
            'risk policy % version % carries % rejection(s) and cannot be activated',
            NEW.policy_key, NEW.policy_version, rejection_count
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.max_action_tier IN ('T3', 'T4') AND NOT NEW.emergency AND approval_count < 2 THEN
        RAISE EXCEPTION
            'risk policy % version % reaches tier % and needs two approvers, but has %',
            NEW.policy_key, NEW.policy_version, NEW.max_action_tier, approval_count
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF approval_count < 1 THEN
        RAISE EXCEPTION
            'risk policy % version % has no approval and cannot be activated',
            NEW.policy_key, NEW.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_policies_guard_activation
    BEFORE INSERT OR UPDATE ON risk_policies
    FOR EACH ROW WHEN (NEW.status = 'ACTIVE')
    EXECUTE FUNCTION risk_policies_guard_activation();
--rollback DROP TRIGGER trg_risk_policies_guard_activation ON risk_policies;
--rollback DROP FUNCTION risk_policies_guard_activation();
--rollback DROP TRIGGER trg_risk_policy_approvals_guard_author ON risk_policy_approvals;
--rollback DROP FUNCTION risk_policy_approvals_guard_author();

--changeset ninggiangboy:027-34-risk-decision-integrity splitStatements:false
-- A decision is immutable and a re-evaluation is a new row. What may still move is the operational
-- projection -- the same outcome, later satisfied, expired or superseded -- and the pointer to
-- whatever superseded it.
--
-- The second guard is the one the registry exists for: an action may only receive an outcome it
-- registered, taken under a policy whose epoch matches the one the decision claims to have bound. A
-- generic endpoint accepting an arbitrary action name, or an evaluation recording an epoch it never
-- read, are both how a protection system stops being reproducible.
CREATE FUNCTION risk_decisions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate risk_decisions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'risk decision % is immutable and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.projection := OLD.projection;
    candidate.superseded_by_decision_id := OLD.superseded_by_decision_id;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'risk decision % is immutable; record a superseding decision instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.projection = 'SUPERSEDED' AND NEW.projection <> 'SUPERSEDED' THEN
        RAISE EXCEPTION
            'risk decision % was superseded and cannot return to %', OLD.id, NEW.projection
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_decisions_freeze
    BEFORE UPDATE OR DELETE ON risk_decisions
    FOR EACH ROW EXECUTE FUNCTION risk_decisions_freeze();

CREATE FUNCTION risk_decisions_guard_registry() RETURNS TRIGGER AS $$
DECLARE
    action_row risk_protected_actions%ROWTYPE;
    policy_row risk_policies%ROWTYPE;
BEGIN
    SELECT * INTO action_row FROM risk_protected_actions WHERE action_key = NEW.protected_action;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'protected action % is not registered', NEW.protected_action
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF action_row.status <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'protected action % is % and cannot be evaluated',
            NEW.protected_action, action_row.status USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT (NEW.outcome = ANY (action_row.permitted_outcomes)) THEN
        RAISE EXCEPTION
            'protected action % does not permit outcome %',
            NEW.protected_action, NEW.outcome USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.risk_policy_id IS NOT NULL THEN
        SELECT * INTO policy_row FROM risk_policies WHERE id = NEW.risk_policy_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'risk policy % does not exist', NEW.risk_policy_id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF policy_row.policy_epoch <> NEW.policy_epoch THEN
            RAISE EXCEPTION
                'risk policy % belongs to epoch %, not the epoch % this decision bound',
                NEW.risk_policy_id, policy_row.policy_epoch, NEW.policy_epoch
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NOT (NEW.protected_action = ANY (policy_row.action_keys)) THEN
            RAISE EXCEPTION
                'risk policy % does not cover protected action %',
                NEW.risk_policy_id, NEW.protected_action USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_decisions_guard_registry
    BEFORE INSERT ON risk_decisions
    FOR EACH ROW EXECUTE FUNCTION risk_decisions_guard_registry();
--rollback DROP TRIGGER trg_risk_decisions_guard_registry ON risk_decisions;
--rollback DROP FUNCTION risk_decisions_guard_registry();
--rollback DROP TRIGGER trg_risk_decisions_freeze ON risk_decisions;
--rollback DROP FUNCTION risk_decisions_freeze();

--changeset ninggiangboy:027-35-risk-enforcement-fidelity splitStatements:false
-- The row that says a domain honoured a decision is the only bridge between advice and effect, and
-- it is the one worth lying about. A command cannot record that it proceeded on a decision that
-- denied it, cannot honour a decision that had already expired or been superseded, and must say so
-- explicitly when it did something the decision did not authorize.
CREATE FUNCTION risk_decision_enforcements_guard() RETURNS TRIGGER AS $$
DECLARE
    decision_row risk_decisions%ROWTYPE;
    expected_result VARCHAR(24);
BEGIN
    SELECT * INTO decision_row FROM risk_decisions WHERE id = NEW.risk_decision_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'risk decision % does not exist', NEW.risk_decision_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF decision_row.projection = 'SUPERSEDED' THEN
        RAISE EXCEPTION
            'risk decision % was superseded and cannot be enforced', NEW.risk_decision_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF decision_row.expires_at IS NOT NULL AND NEW.enforced_at > decision_row.expires_at THEN
        RAISE EXCEPTION
            'risk decision % expired at % and cannot be enforced at %',
            NEW.risk_decision_id, decision_row.expires_at, NEW.enforced_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.enforced_at < decision_row.evaluated_at THEN
        RAISE EXCEPTION
            'risk decision % was evaluated at %, after the enforcement instant %',
            NEW.risk_decision_id, decision_row.evaluated_at, NEW.enforced_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- A divergence or a request to re-evaluate is allowed under any outcome; both say plainly that
    -- the domain did not simply carry the decision out. Everything else must match.
    IF NEW.enforcement_result NOT IN ('DIVERGED', 'REEVALUATION_REQUESTED') THEN
        expected_result := CASE decision_row.outcome
                               WHEN 'ALLOW' THEN 'PROCEEDED'
                               WHEN 'DENY' THEN 'BLOCKED'
                               WHEN 'CHALLENGE' THEN 'CHALLENGED'
                               WHEN 'HOLD' THEN 'HELD'
                               WHEN 'LIMIT' THEN 'LIMITED'
                               WHEN 'MANUAL_REVIEW' THEN 'HELD'
                           END;

        -- A limited action that was permitted within its stated scope may record either that it was
        -- limited or that it proceeded under the limit; nothing else collapses.
        IF NEW.enforcement_result <> expected_result
           AND NOT (decision_row.outcome = 'LIMIT' AND NEW.enforcement_result = 'PROCEEDED') THEN
            RAISE EXCEPTION
                'risk decision % decided % but enforcement recorded %; record DIVERGED with a reason',
                NEW.risk_decision_id, decision_row.outcome, NEW.enforcement_result
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_decision_enforcements_guard
    BEFORE INSERT ON risk_decision_enforcements
    FOR EACH ROW EXECUTE FUNCTION risk_decision_enforcements_guard();
--rollback DROP TRIGGER trg_risk_decision_enforcements_guard ON risk_decision_enforcements;
--rollback DROP FUNCTION risk_decision_enforcements_guard();

--changeset ninggiangboy:027-36-risk-challenge-lifecycle splitStatements:false
-- A challenge moves forward only. Its attempt ceiling is the whole point of having one, so attempts
-- are refused past the limit and on a challenge that has already ended -- otherwise the ceiling is
-- enforced by whichever service happens to remember it, and a retry loop is a brute-force tool.
CREATE FUNCTION risk_challenges_guard_lifecycle() RETURNS TRIGGER AS $$
DECLARE
    candidate risk_challenges%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'risk challenge % is evidence of a step-up and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state_rank = 3 THEN
        candidate := NEW;
        candidate.updated_at := OLD.updated_at;
        candidate.version := OLD.version;

        IF candidate IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION
                'risk challenge % already ended as %; issue a new challenge instead',
                OLD.id, OLD.state USING ERRCODE = 'restrict_violation';
        END IF;

        RETURN NEW;
    END IF;

    IF NEW.state_rank < OLD.state_rank THEN
        RAISE EXCEPTION
            'risk challenge % cannot move from % back to %', OLD.id, OLD.state, NEW.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.attempts_used < OLD.attempts_used THEN
        RAISE EXCEPTION
            'risk challenge % has used % attempts; the count cannot fall to %',
            OLD.id, OLD.attempts_used, NEW.attempts_used USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.max_attempts <> OLD.max_attempts OR NEW.expires_at <> OLD.expires_at THEN
        RAISE EXCEPTION
            'risk challenge % cannot have its attempt ceiling or expiry changed after issue', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_challenges_guard_lifecycle
    BEFORE UPDATE OR DELETE ON risk_challenges
    FOR EACH ROW EXECUTE FUNCTION risk_challenges_guard_lifecycle();

CREATE FUNCTION risk_challenge_attempts_guard() RETURNS TRIGGER AS $$
DECLARE
    challenge_row risk_challenges%ROWTYPE;
BEGIN
    SELECT * INTO challenge_row FROM risk_challenges WHERE id = NEW.risk_challenge_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'risk challenge % does not exist', NEW.risk_challenge_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF challenge_row.state_rank = 3 THEN
        RAISE EXCEPTION
            'risk challenge % already ended as %; no further attempt may be recorded',
            NEW.risk_challenge_id, challenge_row.state USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.attempt_number > challenge_row.max_attempts THEN
        RAISE EXCEPTION
            'risk challenge % permits % attempts; attempt % is beyond the ceiling',
            NEW.risk_challenge_id, challenge_row.max_attempts, NEW.attempt_number
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.attempted_at > challenge_row.expires_at THEN
        RAISE EXCEPTION
            'risk challenge % expired at %; an attempt at % is not proof of anything',
            NEW.risk_challenge_id, challenge_row.expires_at, NEW.attempted_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_challenge_attempts_guard
    BEFORE INSERT ON risk_challenge_attempts
    FOR EACH ROW EXECUTE FUNCTION risk_challenge_attempts_guard();
--rollback DROP TRIGGER trg_risk_challenge_attempts_guard ON risk_challenge_attempts;
--rollback DROP FUNCTION risk_challenge_attempts_guard();
--rollback DROP TRIGGER trg_risk_challenges_guard_lifecycle ON risk_challenges;
--rollback DROP FUNCTION risk_challenges_guard_lifecycle();

--changeset ninggiangboy:027-37-risk-restriction-lifecycle splitStatements:false
-- An expiry worker marks state; it does not decide how long a limitation lasts. Once a restriction is
-- active its end may be brought forward -- lifting somebody's limitation early is always allowed --
-- but never pushed back, because a late worker extending a restriction is indistinguishable from a
-- punishment nobody decided, and neither the subject nor an appeal reviewer could tell the two apart
-- afterwards.
--
-- What the restriction is about is fixed at the same moment: target, scope, resource, type and start
-- cannot be rewritten once it is in force, so an active restriction cannot be quietly repurposed
-- onto a different account or a different capability.
CREATE FUNCTION risk_restrictions_guard_lifecycle() RETURNS TRIGGER AS $$
DECLARE
    candidate risk_restrictions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'risk restriction % is part of an appealable record and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state IN ('EXPIRED', 'REVOKED', 'SUPERSEDED') THEN
        candidate := NEW;
        candidate.updated_at := OLD.updated_at;
        candidate.version := OLD.version;

        IF candidate IS DISTINCT FROM OLD THEN
            RAISE EXCEPTION
                'risk restriction % already ended as %; issue a new restriction instead',
                OLD.id, OLD.state USING ERRCODE = 'restrict_violation';
        END IF;

        RETURN NEW;
    END IF;

    IF OLD.state IN ('ACTIVE', 'APPEAL_PENDING') THEN
        IF NEW.target_subject_id <> OLD.target_subject_id
           OR NEW.capability_scope <> OLD.capability_scope
           OR NEW.restriction_type <> OLD.restriction_type
           OR NEW.policy_intent <> OLD.policy_intent
           OR NEW.effective_from <> OLD.effective_from
           OR NEW.resource_type IS DISTINCT FROM OLD.resource_type
           OR NEW.resource_id IS DISTINCT FROM OLD.resource_id THEN
            RAISE EXCEPTION
                'risk restriction % is in force; what it restricts cannot be changed', OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.effective_until IS NULL AND OLD.effective_until IS NOT NULL THEN
            RAISE EXCEPTION
                'risk restriction % ends at %; it cannot be made open-ended',
                OLD.id, OLD.effective_until USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.effective_until IS NOT NULL AND OLD.effective_until IS NOT NULL
           AND NEW.effective_until > OLD.effective_until THEN
            RAISE EXCEPTION
                'risk restriction % ends at % and cannot be extended to %',
                OLD.id, OLD.effective_until, NEW.effective_until
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_restrictions_guard_lifecycle
    BEFORE UPDATE OR DELETE ON risk_restrictions
    FOR EACH ROW EXECUTE FUNCTION risk_restrictions_guard_lifecycle();
--rollback DROP TRIGGER trg_risk_restrictions_guard_lifecycle ON risk_restrictions;
--rollback DROP FUNCTION risk_restrictions_guard_lifecycle();

--changeset ninggiangboy:027-38-risk-review-independence splitStatements:false
-- Three rules about who decides. A reviewer may not act on their own case -- the subject is carried
-- on the action row so the comparison is a constraint, and the trigger makes sure that carried value
-- is the task's actual subject rather than whatever the caller supplied. Only the current lease
-- holder may act, which is what the fencing token is for: an expired lease that wakes up late
-- carries an old token and is refused rather than deciding a case somebody else has since taken. And
-- an appeal is heard by somebody other than the person who decided the first time.
CREATE FUNCTION risk_review_tasks_guard_lease() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.lease_fencing_token < OLD.lease_fencing_token THEN
        RAISE EXCEPTION
            'review task % has issued lease token %; % is a stale claim',
            OLD.id, OLD.lease_fencing_token, NEW.lease_fencing_token
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state IN ('DECIDED', 'CANCELLED') AND NEW.state <> OLD.state THEN
        RAISE EXCEPTION
            'review task % is closed as % and cannot be reopened', OLD.id, OLD.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_review_tasks_guard_lease
    BEFORE UPDATE ON risk_review_tasks
    FOR EACH ROW EXECUTE FUNCTION risk_review_tasks_guard_lease();

CREATE FUNCTION risk_review_actions_guard() RETURNS TRIGGER AS $$
DECLARE
    task_row risk_review_tasks%ROWTYPE;
    task_subject_account UUID;
BEGIN
    SELECT * INTO task_row FROM risk_review_tasks WHERE id = NEW.risk_review_task_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'review task % does not exist', NEW.risk_review_task_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT account_holder_id INTO task_subject_account
      FROM risk_subjects WHERE id = task_row.subject_id;

    IF task_subject_account IS NOT NULL
       AND NEW.subject_account_holder_id IS DISTINCT FROM task_subject_account THEN
        RAISE EXCEPTION
            'review task % concerns account holder %, not %',
            NEW.risk_review_task_id, task_subject_account, NEW.subject_account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF task_subject_account IS NOT NULL
       AND task_subject_account = NEW.reviewer_account_holder_id THEN
        RAISE EXCEPTION
            'account holder % is the subject of review task % and cannot review it',
            NEW.reviewer_account_holder_id, NEW.risk_review_task_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.lease_fencing_token <> task_row.lease_fencing_token THEN
        RAISE EXCEPTION
            'review task % is held under lease token %; % is not the current holder',
            NEW.risk_review_task_id, task_row.lease_fencing_token, NEW.lease_fencing_token
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.terminal AND task_row.state NOT IN ('CLAIMED', 'IN_REVIEW') THEN
        RAISE EXCEPTION
            'review task % is %; a terminal action requires a claimed task',
            NEW.risk_review_task_id, task_row.state USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_review_actions_guard
    BEFORE INSERT ON risk_review_actions
    FOR EACH ROW EXECUTE FUNCTION risk_review_actions_guard();

CREATE FUNCTION risk_appeals_guard_independence() RETURNS TRIGGER AS $$
DECLARE
    original_decider UUID;
BEGIN
    IF NEW.risk_decision_id IS NOT NULL THEN
        SELECT decided_by_account_holder_id INTO original_decider
          FROM risk_decisions WHERE id = NEW.risk_decision_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'risk decision % does not exist', NEW.risk_decision_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSIF NEW.moderation_decision_id IS NOT NULL THEN
        SELECT decided_by_account_holder_id INTO original_decider
          FROM moderation_decisions WHERE id = NEW.moderation_decision_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'moderation decision % does not exist', NEW.moderation_decision_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        SELECT created_by_account_holder_id INTO original_decider
          FROM risk_restrictions WHERE id = NEW.risk_restriction_id;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'risk restriction % does not exist', NEW.risk_restriction_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    -- The original decider is recorded from the target rather than supplied, so the independence
    -- check on this table compares against who actually decided.
    IF NEW.original_decider_account_holder_id IS DISTINCT FROM original_decider THEN
        RAISE EXCEPTION
            'appeal names % as the original decider, but the target was decided by %',
            NEW.original_decider_account_holder_id, original_decider
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF original_decider IS NOT NULL
       AND NEW.assigned_reviewer_account_holder_id = original_decider THEN
        RAISE EXCEPTION
            'account holder % decided the appealed matter and cannot hear the appeal',
            original_decider USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_risk_appeals_guard_independence
    BEFORE INSERT OR UPDATE ON risk_appeals
    FOR EACH ROW EXECUTE FUNCTION risk_appeals_guard_independence();
--rollback DROP TRIGGER trg_risk_appeals_guard_independence ON risk_appeals;
--rollback DROP FUNCTION risk_appeals_guard_independence();
--rollback DROP TRIGGER trg_risk_review_actions_guard ON risk_review_actions;
--rollback DROP FUNCTION risk_review_actions_guard();
--rollback DROP TRIGGER trg_risk_review_tasks_guard_lease ON risk_review_tasks;
--rollback DROP FUNCTION risk_review_tasks_guard_lease();

--changeset ninggiangboy:027-39-moderation-binds-to-revision splitStatements:false
-- Moderation decides about an exact revision, and an edit is a new revision. The consequence the
-- feature document states plainly is that an old approval cannot publish a new revision -- so a
-- decision that makes something visible is refused unless it names the latest revision of its item.
-- A removal or an escalation is always allowed on an older revision: hiding something that was
-- published, or routing a safety concern about what was said then, are both about the past.
--
-- The decision itself is frozen once written. Restoring or removing later is a new decision naming
-- the one it supersedes, which is how a moderation history stays readable instead of becoming a
-- single row whose current value nobody can account for.
CREATE FUNCTION moderation_decisions_guard_revision() RETURNS TRIGGER AS $$
DECLARE
    item_id UUID;
    this_revision INTEGER;
    latest_revision INTEGER;
BEGIN
    SELECT content_item_id, revision_number INTO item_id, this_revision
      FROM content_revisions WHERE id = NEW.content_revision_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'content revision % does not exist', NEW.content_revision_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.outcome IN ('PUBLISH', 'MASK', 'WARN') THEN
        SELECT max(revision_number) INTO latest_revision
          FROM content_revisions WHERE content_item_id = item_id;

        IF this_revision < latest_revision THEN
            RAISE EXCEPTION
                'content revision % is superseded by revision % of the same item; % cannot make it visible',
                this_revision, latest_revision, NEW.outcome
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_moderation_decisions_guard_revision
    BEFORE INSERT ON moderation_decisions
    FOR EACH ROW EXECUTE FUNCTION moderation_decisions_guard_revision();

CREATE FUNCTION moderation_decisions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate moderation_decisions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'moderation decision % is immutable and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.superseded_by_decision_id := OLD.superseded_by_decision_id;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'moderation decision % is immutable; record a superseding decision instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state = 'SUPERSEDED' AND NEW.state <> 'SUPERSEDED' THEN
        RAISE EXCEPTION
            'moderation decision % was superseded and cannot return to %', OLD.id, NEW.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_moderation_decisions_freeze
    BEFORE UPDATE OR DELETE ON moderation_decisions
    FOR EACH ROW EXECUTE FUNCTION moderation_decisions_freeze();
--rollback DROP TRIGGER trg_moderation_decisions_freeze ON moderation_decisions;
--rollback DROP FUNCTION moderation_decisions_freeze();
--rollback DROP TRIGGER trg_moderation_decisions_guard_revision ON moderation_decisions;
--rollback DROP FUNCTION moderation_decisions_guard_revision();
