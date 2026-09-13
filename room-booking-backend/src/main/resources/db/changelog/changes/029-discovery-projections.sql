--liquibase formatted sql

-- Discovery reads; it never decides. Everything in this migration is derived, rebuildable, and
-- subordinate to the authoritative rows it summarises: a listing profile is not inventory, a
-- preference feature is not a filter, an aspect score is not a review, and a ranking exposure is
-- not an outcome. If every table here were truncated, search would degrade to the deterministic
-- baseline and nothing about a booking, a price, or a review would change. That property is the
-- point, and several constraints below exist only to keep it true.
--
-- Eight forces shape it:
--
--   Behaviour is evidence, not truth. A discovery_events row records that something was observed --
--   an impression rendered, a listing clicked, a booking started. It is append-only, deduplicated
--   by the client-issued event key, and it never redefines booking or review truth. Transactional
--   outcomes reconcile from their own domains; the event stream only helps attribution. An event
--   that claims a rank must name the search request that produced that rank, so a fabricated
--   impression has nowhere to attach.
--
--   Raw event growth may not compete with bookings. Every event row carries its own expiry and a
--   retention class, because an unbounded clickstream in the transactional database is a capacity
--   incident waiting for a traffic spike. Nothing here stores an exact address, a coordinate, a
--   token, a payment detail, an IP address, or an unrestricted user-agent string; the columns for
--   them deliberately do not exist.
--
--   Opting out means the next job may not rebuild it. A personalization opt-out that clears a
--   cached profile and lets the following batch reconstruct it from the same behaviour is not an
--   opt-out. So a guest preference profile is refused outright for an opted-out subject, and an
--   erasure directive carries an evidence cutoff that every later profile must respect: a profile
--   whose evidence begins before the cutoff is refused by trigger, not by convention. Each derived
--   store records its own application of the directive, so "propagated" is a row, not a claim.
--
--   Importance and direction are different numbers. A guest who always chooses quiet listings and
--   a guest who mentions noise constantly are not the same guest. Every preference feature stores
--   importance, preferred level, and confidence separately, and keeps long-term and recent values
--   apart so one can be expired without destroying the other. Session intent is a third thing
--   again: it lives in its own table, it always has an expiry, and it is never the durable profile.
--
--   Low evidence is uncertainty, not poor quality. An unknown aspect contributes nothing, positive
--   or negative. A profile value with no evidence may not present itself as a strength or a
--   weakness, a listing with no reviews is shrunk to a recorded market prior rather than scored
--   zero, and the prior fallback level actually used is stored, because "we fell back to the
--   country prior" and "this neighbourhood is like this" are different statements.
--
--   Exposure is a budget, not a side effect. Exploration traffic given to new or uncertain listings
--   has a window, a traffic ceiling, a per-listing cap, and a quality floor. An exploration exposure
--   that names no open window is refused, and a window can never report consuming more than it
--   allocated. Sponsored placement is a separate, labelled reason that may never masquerade as
--   organic relevance.
--
--   An explanation must be able to point at something. Reason codes are an approved, versioned
--   vocabulary with minimum evidence and confidence thresholds attached. A reason is attached to an
--   exposure through a foreign key to that vocabulary -- a free-text array cannot be checked -- and
--   a reason declared personalized may not appear on an anonymous exposure. Where a reason cannot
--   be supported, the reason is omitted; the listing is not.
--
--   A rank must be reproducible. An exposure names its ranking epoch, its policy version, its model
--   version, its position, its score, and the digest of the feature vector that produced it. Epoch,
--   policy, and model must agree with each other, so a cursor bound to one epoch cannot silently be
--   served by a different ranker. Exposures are append-only: the record of what was shown is not
--   editable after the fact, which is the whole basis of position-bias correction later.
--
-- Note on what this migration does not create. The feature document lists review_aspect_mentions
-- and listing_aspect_scores among its likely records. Migration 026 already delivers both under the
-- names the reviews domain owns: review_aspect_mentions, and per-listing aspect aggregates as
-- aspect_profile_versions plus aspect_profile_values, with counts, posteriors, intervals, trend, and
-- an evidence class. Discovery consumes those rows; a second copy would be a second definition of
-- listing quality and the two would disagree within a week. listing_discovery_profiles names the
-- aspect profile version it read instead of restating its numbers.
--
-- Note on favorites. The plan describes dropping the legacy favorites table here. Migration 016
-- already dropped it along with the rest of the listing-centric foundation, so this migration only
-- creates its target replacement, saved_listings, which is keyed on account_holders rather than the
-- retired users table and carries the save, unsave, and re-save history the event contract needs.
--
-- Note on the event registry. Event names, owners, schemas, and retention windows belong to the
-- data platform, which migration 030 delivers as event_definitions. Until then discovery_events
-- carries its own event-type vocabulary as a check constraint and its own retention class; 030
-- links the two rather than replacing this table.
--
-- Note on the model registry. ranking_model_versions here is the discovery serving registry -- what
-- is live, what is shadowed, what share of traffic it takes, and what it falls back to. Migration
-- 031 delivers the general model lifecycle registry for training, evaluation, and release routing.
-- This table is the serving contract, not a duplicate of that lifecycle.

--changeset ninggiangboy:029-01-saved-listings
-- An explicit save is the strongest cheap preference signal there is, so it is stored as authoritative
-- state rather than inferred from the event stream. Unsaving is a state change, not a delete: a guest
-- who saves, unsaves, and saves again has told us something a row that vanished cannot.
CREATE TABLE saved_listings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    listing_id                  UUID NOT NULL,

    state                       VARCHAR(16) NOT NULL DEFAULT 'SAVED',
    saved_at                    TIMESTAMPTZ NOT NULL,
    unsaved_at                  TIMESTAMPTZ,
    save_count                  INTEGER NOT NULL DEFAULT 1,
    source_surface              VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    note                        VARCHAR(280),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saved_listings_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_saved_listings_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT uk_saved_listings_subject UNIQUE (account_holder_id, listing_id),
    CONSTRAINT ck_saved_listings_state CHECK (state IN ('SAVED', 'UNSAVED')),
    CONSTRAINT ck_saved_listings_surface CHECK (
        source_surface IN ('SEARCH_RESULTS', 'LISTING_DETAIL', 'MAP', 'RECOMMENDATION',
                           'BOOKING_FLOW', 'IMPORT', 'UNKNOWN')
    ),
    -- An unsaved row says when it was unsaved; a saved one cannot claim to have been.
    CONSTRAINT ck_saved_listings_unsaved CHECK ((state = 'UNSAVED') = (unsaved_at IS NOT NULL)),
    CONSTRAINT ck_saved_listings_order CHECK (unsaved_at IS NULL OR unsaved_at >= saved_at),
    CONSTRAINT ck_saved_listings_count CHECK (save_count >= 1),
    CONSTRAINT ck_saved_listings_row_version CHECK (version >= 0)
);

CREATE INDEX idx_saved_listings_listing ON saved_listings (listing_id, saved_at DESC)
    WHERE state = 'SAVED';
CREATE INDEX idx_saved_listings_holder ON saved_listings (account_holder_id, saved_at DESC)
    WHERE state = 'SAVED';
--rollback DROP TABLE saved_listings;

--changeset ninggiangboy:029-02-personalization-settings
-- What the guest actually chose about personalization, kept beside the reason they chose it and the
-- moment it took effect. This is the row every derived-profile job must read first; it is not a
-- cache and it is not derived from anything.
CREATE TABLE personalization_settings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,

    personalized_ranking        BOOLEAN NOT NULL DEFAULT true,
    behavioral_profiling        BOOLEAN NOT NULL DEFAULT true,
    session_intent_use          BOOLEAN NOT NULL DEFAULT true,
    explanation_display         BOOLEAN NOT NULL DEFAULT true,

    decision_source             VARCHAR(24) NOT NULL DEFAULT 'DEFAULT',
    decided_at                  TIMESTAMPTZ NOT NULL,
    effective_from              TIMESTAMPTZ NOT NULL,
    market_id                   UUID,
    consent_reference           VARCHAR(128),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_personalization_settings_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_personalization_settings_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT uk_personalization_settings_subject UNIQUE (account_holder_id),
    CONSTRAINT ck_personalization_settings_source CHECK (
        decision_source IN ('DEFAULT', 'GUEST_CHOICE', 'ONBOARDING', 'MARKET_POLICY',
                            'REGULATORY_REQUIREMENT', 'SUPPORT_ACTION')
    ),
    -- Personalized ranking without behavioral profiling is a contradiction: the ranking has nothing
    -- to personalize from.
    CONSTRAINT ck_personalization_settings_coherent CHECK (
        behavioral_profiling OR NOT personalized_ranking
    ),
    -- A choice the guest did not make must say whose choice it was.
    CONSTRAINT ck_personalization_settings_attributed CHECK (
        decision_source <> 'REGULATORY_REQUIREMENT' OR market_id IS NOT NULL
    ),
    CONSTRAINT ck_personalization_settings_row_version CHECK (version >= 0)
);
--rollback DROP TABLE personalization_settings;

--changeset ninggiangboy:029-03-personalization-erasure-directives
-- An instruction that behaviour before a named instant may no longer feed any derived profile.
-- The cutoff is the operative column: clearing a profile without it means the next nightly job
-- rebuilds exactly what was cleared, which is the failure the feature document calls out by name.
CREATE TABLE personalization_erasure_directives (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    directive_reference         VARCHAR(64) NOT NULL,

    scope                       VARCHAR(32) NOT NULL,
    evidence_cutoff_at          TIMESTAMPTZ NOT NULL,
    reason                      VARCHAR(32) NOT NULL,
    legal_basis_reference       VARCHAR(128),
    requested_at                TIMESTAMPTZ NOT NULL,
    requested_by_actor_type     VARCHAR(24) NOT NULL,
    requested_by_account_holder_id UUID,

    state                       VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    completed_at                TIMESTAMPTZ,
    deadline_at                 TIMESTAMPTZ NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_personalization_erasure_directives_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_personalization_erasure_directives_requester
        FOREIGN KEY (requested_by_account_holder_id) REFERENCES account_holders (id),
    CONSTRAINT uk_personalization_erasure_directives_reference UNIQUE (directive_reference),
    CONSTRAINT ck_personalization_erasure_directives_scope CHECK (
        scope IN ('RECENT_ACTIVITY', 'BEHAVIORAL_PROFILE', 'ALL_DERIVED_PERSONALIZATION',
                  'ACCOUNT_DELETION')
    ),
    CONSTRAINT ck_personalization_erasure_directives_reason CHECK (
        reason IN ('GUEST_REQUEST', 'OPT_OUT', 'ACCOUNT_CLOSURE', 'REGULATORY_ORDER',
                   'DATA_QUALITY_DEFECT', 'SUPPORT_ACTION')
    ),
    CONSTRAINT ck_personalization_erasure_directives_actor CHECK (
        requested_by_actor_type IN ('GUEST', 'SUPPORT_AGENT', 'PRIVACY_OFFICER', 'SYSTEM')
    ),
    CONSTRAINT ck_personalization_erasure_directives_state CHECK (
        state IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_personalization_erasure_directives_completion CHECK (
        (state IN ('COMPLETED', 'PARTIALLY_COMPLETED')) = (completed_at IS NOT NULL)
    ),
    -- A directive nobody can name the authority for is not an erasure, it is a deletion.
    CONSTRAINT ck_personalization_erasure_directives_authority CHECK (
        reason <> 'REGULATORY_ORDER' OR legal_basis_reference IS NOT NULL
    ),
    -- A partial completion must say so on the directive, not only in the application rows.
    CONSTRAINT ck_personalization_erasure_directives_order CHECK (
        completed_at IS NULL OR completed_at >= requested_at
    ),
    CONSTRAINT ck_personalization_erasure_directives_row_version CHECK (version >= 0)
);

CREATE INDEX idx_personalization_erasure_directives_open
    ON personalization_erasure_directives (deadline_at)
    WHERE state IN ('PENDING', 'IN_PROGRESS');
CREATE INDEX idx_personalization_erasure_directives_subject
    ON personalization_erasure_directives (account_holder_id, evidence_cutoff_at DESC);
--rollback DROP TABLE personalization_erasure_directives;

--changeset ninggiangboy:029-04-personalization-erasure-applications
-- One row per derived store that honoured the directive. Propagation to profiles, caches, training
-- datasets, and future model builds is the part that is normally forgotten, so each is a row with an
-- instant and a count rather than an assumption.
CREATE TABLE personalization_erasure_applications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    directive_id                UUID NOT NULL,
    target_store                VARCHAR(32) NOT NULL,

    outcome                     VARCHAR(24) NOT NULL,
    applied_at                  TIMESTAMPTZ NOT NULL,
    affected_row_count          BIGINT NOT NULL DEFAULT 0,
    watermark_applied_at        TIMESTAMPTZ,
    deferred_reason             VARCHAR(200),
    operator_reference          VARCHAR(128),

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_personalization_erasure_applications_directive FOREIGN KEY (directive_id)
        REFERENCES personalization_erasure_directives (id),
    CONSTRAINT uk_personalization_erasure_applications_target UNIQUE (directive_id, target_store),
    CONSTRAINT ck_personalization_erasure_applications_store CHECK (
        target_store IN ('GUEST_PREFERENCE_PROFILE', 'SESSION_INTENT', 'DISCOVERY_EVENTS',
                         'SERVING_CACHE', 'TRAINING_DATASET', 'FEATURE_STORE',
                         'EXPERIMENT_EXPOSURES', 'ANALYTICS_WAREHOUSE')
    ),
    CONSTRAINT ck_personalization_erasure_applications_outcome CHECK (
        outcome IN ('APPLIED', 'NOTHING_TO_ERASE', 'DEFERRED', 'NOT_TECHNICALLY_FEASIBLE', 'FAILED')
    ),
    CONSTRAINT ck_personalization_erasure_applications_count CHECK (affected_row_count >= 0),
    -- Anything short of applied owes an explanation. "Not technically feasible" is an acceptable
    -- answer only when it is a written one.
    CONSTRAINT ck_personalization_erasure_applications_explained CHECK (
        outcome IN ('APPLIED', 'NOTHING_TO_ERASE') OR deferred_reason IS NOT NULL
    ),
    CONSTRAINT ck_personalization_erasure_applications_applied CHECK (
        outcome <> 'APPLIED' OR affected_row_count >= 0
    )
);

CREATE INDEX idx_personalization_erasure_applications_directive
    ON personalization_erasure_applications (directive_id);
--rollback DROP TABLE personalization_erasure_applications;

--changeset ninggiangboy:029-05-ranking-policy-versions
-- Weights, caps, floors, and budgets as a versioned, approved row rather than constants scattered
-- through services. A ranking change that nobody can date, attribute, or roll back is not a tuning
-- decision, it is an incident with a delayed fuse.
CREATE TABLE ranking_policy_versions (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key                      VARCHAR(64) NOT NULL,
    policy_version                  INTEGER NOT NULL,
    market_id                       UUID,
    status                          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',

    baseline_weights                JSONB NOT NULL,
    personalized_weight             NUMERIC(5, 4) NOT NULL DEFAULT 0,
    personalization_confidence_floor NUMERIC(5, 4) NOT NULL DEFAULT 0,
    diversity_penalty               NUMERIC(5, 4) NOT NULL DEFAULT 0,
    max_same_host_run               SMALLINT,
    candidate_cap                   INTEGER NOT NULL,
    latency_budget_ms               INTEGER NOT NULL,
    enrichment_deadline_ms          INTEGER NOT NULL,
    exploration_traffic_share       NUMERIC(5, 4) NOT NULL DEFAULT 0,
    exploration_quality_floor       NUMERIC(5, 4) NOT NULL DEFAULT 0,
    explanation_min_confidence      NUMERIC(5, 4) NOT NULL DEFAULT 0,

    content_digest                  CHAR(64) NOT NULL,
    effective_from                  TIMESTAMPTZ,
    effective_until                 TIMESTAMPTZ,
    approved_by_account_holder_id   UUID,
    approved_at                     TIMESTAMPTZ,
    supersedes_policy_version_id    UUID,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ranking_policy_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_ranking_policy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_ranking_policy_versions_predecessor FOREIGN KEY (supersedes_policy_version_id)
        REFERENCES ranking_policy_versions (id),
    CONSTRAINT uk_ranking_policy_versions_identity UNIQUE (policy_key, policy_version),
    CONSTRAINT ck_ranking_policy_versions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_ranking_policy_versions_number CHECK (policy_version > 0),
    CONSTRAINT ck_ranking_policy_versions_fractions CHECK (
        personalized_weight BETWEEN 0 AND 1
            AND personalization_confidence_floor BETWEEN 0 AND 1
            AND diversity_penalty BETWEEN 0 AND 1
            AND exploration_traffic_share BETWEEN 0 AND 1
            AND exploration_quality_floor BETWEEN 0 AND 1
            AND explanation_min_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_ranking_policy_versions_budgets CHECK (
        candidate_cap > 0 AND latency_budget_ms > 0 AND enrichment_deadline_ms > 0
            AND enrichment_deadline_ms <= latency_budget_ms
    ),
    CONSTRAINT ck_ranking_policy_versions_host_run CHECK (
        max_same_host_run IS NULL OR max_same_host_run > 0
    ),
    -- A personalized contribution with no confidence floor lets a profile built from one click
    -- reorder a page of strong results.
    CONSTRAINT ck_ranking_policy_versions_gate CHECK (
        personalized_weight = 0 OR personalization_confidence_floor > 0
    ),
    -- Exploration spends real guest attention. It is allowed only with a quality floor beneath it.
    CONSTRAINT ck_ranking_policy_versions_exploration CHECK (
        exploration_traffic_share = 0 OR exploration_quality_floor > 0
    ),
    CONSTRAINT ck_ranking_policy_versions_approval CHECK (
        status <> 'ACTIVE'
            OR (approved_at IS NOT NULL AND approved_by_account_holder_id IS NOT NULL
                AND effective_from IS NOT NULL)
    ),
    CONSTRAINT ck_ranking_policy_versions_window CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_ranking_policy_versions_self CHECK (supersedes_policy_version_id <> id),
    CONSTRAINT ck_ranking_policy_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_ranking_policy_versions_active
    ON ranking_policy_versions (policy_key, market_id) NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';
--rollback DROP TABLE ranking_policy_versions;

--changeset ninggiangboy:029-06-ranking-model-versions
-- What is actually serving, what is only shadowing, and what happens when it times out. The
-- artifact checksum and the feature-schema digest are mandatory because a model whose inputs cannot
-- be identified cannot be reproduced, and a rank nobody can reproduce cannot be defended to a host.
CREATE TABLE ranking_model_versions (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_key                       VARCHAR(64) NOT NULL,
    model_version                   VARCHAR(48) NOT NULL,
    market_id                       UUID,
    status                          VARCHAR(24) NOT NULL DEFAULT 'REGISTERED',

    model_family                    VARCHAR(32) NOT NULL,
    feature_schema_digest           CHAR(64) NOT NULL,
    artifact_checksum               CHAR(64) NOT NULL,
    artifact_reference              VARCHAR(512),
    training_dataset_reference      VARCHAR(128),
    training_window_start           TIMESTAMPTZ,
    training_window_end             TIMESTAMPTZ,
    trained_at                      TIMESTAMPTZ,
    calibration_method              VARCHAR(32),
    evaluation_reference            VARCHAR(128),

    traffic_share                   NUMERIC(5, 4) NOT NULL DEFAULT 0,
    serving_timeout_ms              INTEGER NOT NULL,
    fallback_mode                   VARCHAR(24) NOT NULL DEFAULT 'DETERMINISTIC_BASELINE',
    fallback_model_version_id       UUID,

    approved_by_account_holder_id   UUID,
    approved_at                     TIMESTAMPTZ,
    promoted_at                     TIMESTAMPTZ,
    rolled_back_at                  TIMESTAMPTZ,
    rollback_reason                 VARCHAR(200),

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ranking_model_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_ranking_model_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_ranking_model_versions_fallback FOREIGN KEY (fallback_model_version_id)
        REFERENCES ranking_model_versions (id),
    CONSTRAINT uk_ranking_model_versions_identity UNIQUE (model_key, model_version),
    CONSTRAINT ck_ranking_model_versions_status CHECK (
        status IN ('REGISTERED', 'SHADOW', 'CANDIDATE', 'ACTIVE', 'ROLLED_BACK', 'RETIRED')
    ),
    CONSTRAINT ck_ranking_model_versions_family CHECK (
        model_family IN ('DETERMINISTIC_BASELINE', 'GRADIENT_BOOSTED_RANKER', 'LINEAR_RANKER',
                         'TWO_TOWER_RETRIEVAL', 'SEQUENCE_MODEL', 'CONTEXTUAL_BANDIT')
    ),
    CONSTRAINT ck_ranking_model_versions_calibration CHECK (
        calibration_method IS NULL
            OR calibration_method IN ('PLATT_SCALING', 'ISOTONIC_REGRESSION',
                                      'TEMPERATURE_SCALING', 'NONE')
    ),
    CONSTRAINT ck_ranking_model_versions_fallback_mode CHECK (
        fallback_mode IN ('DETERMINISTIC_BASELINE', 'PREVIOUS_MODEL', 'NONE')
    ),
    CONSTRAINT ck_ranking_model_versions_traffic CHECK (traffic_share BETWEEN 0 AND 1),
    CONSTRAINT ck_ranking_model_versions_timeout CHECK (serving_timeout_ms > 0),
    -- A shadow model observes; it does not serve. Giving it traffic is how a shadow deployment
    -- becomes an unannounced release.
    CONSTRAINT ck_ranking_model_versions_shadow CHECK (
        status <> 'SHADOW' OR traffic_share = 0
    ),
    CONSTRAINT ck_ranking_model_versions_active CHECK (
        status <> 'ACTIVE'
            OR (approved_at IS NOT NULL AND approved_by_account_holder_id IS NOT NULL
                AND promoted_at IS NOT NULL AND traffic_share > 0)
    ),
    -- A model that was pulled from service may not be serving. Writing the guard as a check rather
    -- than only as a transition rule matters: a row can be created already past a transition, and a
    -- guard that only watches the change would never see it.
    CONSTRAINT ck_ranking_model_versions_not_rolled_back CHECK (
        status <> 'ACTIVE' OR rolled_back_at IS NULL
    ),
    -- A rolled-back model keeps no traffic and owes a reason.
    CONSTRAINT ck_ranking_model_versions_rollback CHECK (
        status <> 'ROLLED_BACK'
            OR (rolled_back_at IS NOT NULL AND rollback_reason IS NOT NULL AND traffic_share = 0)
    ),
    CONSTRAINT ck_ranking_model_versions_fallback_target CHECK (
        (fallback_mode = 'PREVIOUS_MODEL') = (fallback_model_version_id IS NOT NULL)
    ),
    CONSTRAINT ck_ranking_model_versions_self CHECK (fallback_model_version_id <> id),
    CONSTRAINT ck_ranking_model_versions_training_window CHECK (
        training_window_end IS NULL OR training_window_start IS NULL
            OR training_window_end > training_window_start
    ),
    -- Never train on what happened after the prediction. A model whose training window has not
    -- closed before it was trained has already leaked its own labels.
    CONSTRAINT ck_ranking_model_versions_leakage CHECK (
        trained_at IS NULL OR training_window_end IS NULL OR trained_at >= training_window_end
    ),
    CONSTRAINT ck_ranking_model_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_ranking_model_versions_active
    ON ranking_model_versions (model_key, market_id) NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';
--rollback DROP TABLE ranking_model_versions;

--changeset ninggiangboy:029-07-ranking-epochs
-- A bounded window within which ordering is stable, so a cursor issued on page one still means the
-- same thing on page four. Rotation is useful and pure randomness is not: the tie-breaker seed lives
-- on the epoch, which is what makes the rotation both deliberate and reproducible.
CREATE TABLE ranking_epochs (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    epoch_key                       VARCHAR(64) NOT NULL,
    market_id                       UUID,
    ranking_policy_version_id       UUID NOT NULL,
    ranking_model_version_id        UUID,

    tie_breaker_seed                BIGINT NOT NULL,
    state                           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    opened_at                       TIMESTAMPTZ NOT NULL,
    closes_at                       TIMESTAMPTZ NOT NULL,
    closed_at                       TIMESTAMPTZ,
    invalidation_reason             VARCHAR(200),

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ranking_epochs_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_ranking_epochs_policy FOREIGN KEY (ranking_policy_version_id)
        REFERENCES ranking_policy_versions (id),
    CONSTRAINT fk_ranking_epochs_model FOREIGN KEY (ranking_model_version_id)
        REFERENCES ranking_model_versions (id),
    CONSTRAINT uk_ranking_epochs_key UNIQUE (epoch_key),
    CONSTRAINT ck_ranking_epochs_state CHECK (state IN ('OPEN', 'CLOSED', 'INVALIDATED')),
    CONSTRAINT ck_ranking_epochs_window CHECK (closes_at > opened_at),
    CONSTRAINT ck_ranking_epochs_closed CHECK (
        (state IN ('CLOSED', 'INVALIDATED')) = (closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_ranking_epochs_closed_order CHECK (closed_at IS NULL OR closed_at >= opened_at),
    -- An epoch pulled out from under live cursors must say why; "it just ended" is what closes_at
    -- is for.
    CONSTRAINT ck_ranking_epochs_invalidation CHECK (
        (state = 'INVALIDATED') = (invalidation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_ranking_epochs_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_ranking_epochs_open ON ranking_epochs (market_id) NULLS NOT DISTINCT
    WHERE state = 'OPEN';
CREATE INDEX idx_ranking_epochs_expiry ON ranking_epochs (closes_at) WHERE state = 'OPEN';
--rollback DROP TABLE ranking_epochs;

--changeset ninggiangboy:029-08-discovery-search-requests
-- The server-issued identity of one search, and the versions that served it. Every exposure and
-- every ranked event hangs off this row, which is what makes a fabricated impression detectable: it
-- would have to name a search that was never issued, at a position that was never returned.
CREATE TABLE discovery_search_requests (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    search_request_key              VARCHAR(64) NOT NULL,
    ranking_epoch_id                UUID NOT NULL,
    ranking_policy_version_id       UUID NOT NULL,
    ranking_model_version_id        UUID,

    account_holder_id               UUID,
    session_pseudonym               CHAR(64) NOT NULL,
    market_id                       UUID,
    locale                          VARCHAR(35) NOT NULL,
    requested_currency              VARCHAR(3) NOT NULL,

    sort_mode                       VARCHAR(24) NOT NULL DEFAULT 'RECOMMENDED',
    personalization_state           VARCHAR(32) NOT NULL,
    normalized_query_digest         CHAR(64) NOT NULL,
    destination_geo_area_id         UUID,
    search_mode                     VARCHAR(16) NOT NULL DEFAULT 'DESTINATION',
    check_in_date                   DATE,
    check_out_date                  DATE,
    night_count                     SMALLINT,
    guest_count                     SMALLINT,
    lead_time_days                  SMALLINT,

    geo_candidate_count             INTEGER NOT NULL DEFAULT 0,
    eligible_candidate_count        INTEGER NOT NULL DEFAULT 0,
    priced_candidate_count          INTEGER NOT NULL DEFAULT 0,
    ranked_candidate_count          INTEGER NOT NULL DEFAULT 0,
    returned_count                  INTEGER NOT NULL DEFAULT 0,
    page_number                     SMALLINT NOT NULL DEFAULT 1,

    prior_fallback_level            VARCHAR(16) NOT NULL DEFAULT 'NONE',
    fallback_code                   VARCHAR(32) NOT NULL DEFAULT 'NONE',
    total_latency_ms                INTEGER,
    enrichment_latency_ms           INTEGER,
    correlation_id                  VARCHAR(64),

    occurred_at                     TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_discovery_search_requests_epoch FOREIGN KEY (ranking_epoch_id)
        REFERENCES ranking_epochs (id),
    CONSTRAINT fk_discovery_search_requests_policy FOREIGN KEY (ranking_policy_version_id)
        REFERENCES ranking_policy_versions (id),
    CONSTRAINT fk_discovery_search_requests_model FOREIGN KEY (ranking_model_version_id)
        REFERENCES ranking_model_versions (id),
    CONSTRAINT fk_discovery_search_requests_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_discovery_search_requests_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_discovery_search_requests_destination FOREIGN KEY (destination_geo_area_id)
        REFERENCES geo_areas (id),
    CONSTRAINT uk_discovery_search_requests_key UNIQUE (search_request_key),
    CONSTRAINT ck_discovery_search_requests_sort CHECK (
        sort_mode IN ('RECOMMENDED', 'PRICE_LOW_TO_HIGH', 'RATING_HIGH_TO_LOW', 'DISTANCE', 'NEWEST')
    ),
    CONSTRAINT ck_discovery_search_requests_personalization CHECK (
        personalization_state IN ('PERSONALIZED', 'ANONYMOUS', 'OPTED_OUT', 'PROFILE_UNAVAILABLE',
                                  'CONFIDENCE_GATED', 'EXPERIMENT_CONTROL', 'EXPLICIT_SORT_MODE',
                                  'ENRICHMENT_DEADLINE_EXCEEDED')
    ),
    CONSTRAINT ck_discovery_search_requests_mode CHECK (
        search_mode IN ('DESTINATION', 'MAP_VIEWPORT', 'NEARBY', 'SAVED_SEARCH', 'FREE_TEXT')
    ),
    CONSTRAINT ck_discovery_search_requests_prior_level CHECK (
        prior_fallback_level IN ('NONE', 'NEIGHBORHOOD', 'LOCALITY', 'ADMIN_AREA', 'COUNTRY',
                                 'GLOBAL')
    ),
    CONSTRAINT ck_discovery_search_requests_fallback CHECK (
        fallback_code IN ('NONE', 'MODEL_TIMEOUT', 'MODEL_ERROR', 'PROFILE_UNAVAILABLE',
                          'LISTING_PROFILE_STALE', 'EXPERIMENT_CONFIG_INVALID',
                          'PRICING_UNAVAILABLE', 'FEATURE_LOOKUP_FAILED')
    ),
    CONSTRAINT ck_discovery_search_requests_currency CHECK (requested_currency ~ '^[A-Z]{3}$'),
    -- A personalized result needs somebody to have personalized it for.
    CONSTRAINT ck_discovery_search_requests_identified CHECK (
        personalization_state <> 'PERSONALIZED' OR account_holder_id IS NOT NULL
    ),
    -- An explicit sort bypasses the recommendation order by definition, so it may not also claim to
    -- have been personalized.
    CONSTRAINT ck_discovery_search_requests_explicit_sort CHECK (
        sort_mode = 'RECOMMENDED' OR personalization_state = 'EXPLICIT_SORT_MODE'
    ),
    CONSTRAINT ck_discovery_search_requests_stay CHECK (
        (check_in_date IS NULL) = (check_out_date IS NULL)
    ),
    CONSTRAINT ck_discovery_search_requests_range CHECK (
        check_out_date IS NULL OR check_out_date > check_in_date
    ),
    CONSTRAINT ck_discovery_search_requests_nights CHECK (
        night_count IS NULL OR check_in_date IS NULL
            OR night_count = (check_out_date - check_in_date)
    ),
    CONSTRAINT ck_discovery_search_requests_party CHECK (guest_count IS NULL OR guest_count > 0),
    CONSTRAINT ck_discovery_search_requests_counts CHECK (
        geo_candidate_count >= 0 AND eligible_candidate_count >= 0 AND priced_candidate_count >= 0
            AND ranked_candidate_count >= 0 AND returned_count >= 0 AND page_number >= 1
    ),
    -- Each hard-filter stage can only narrow the set. A stage that grows it has invented candidates.
    CONSTRAINT ck_discovery_search_requests_funnel CHECK (
        eligible_candidate_count <= geo_candidate_count
            AND priced_candidate_count <= eligible_candidate_count
            AND ranked_candidate_count <= priced_candidate_count
            AND returned_count <= ranked_candidate_count
    ),
    CONSTRAINT ck_discovery_search_requests_latency CHECK (
        (total_latency_ms IS NULL OR total_latency_ms >= 0)
            AND (enrichment_latency_ms IS NULL OR enrichment_latency_ms >= 0)
    ),
    -- Every search log row names its own deletion deadline; the transactional database is not an
    -- analytics warehouse and must not be allowed to become one by accident.
    CONSTRAINT ck_discovery_search_requests_retention CHECK (expires_at > occurred_at)
);

CREATE INDEX idx_discovery_search_requests_retention ON discovery_search_requests (expires_at);
CREATE INDEX idx_discovery_search_requests_subject
    ON discovery_search_requests (account_holder_id, occurred_at DESC)
    WHERE account_holder_id IS NOT NULL;
CREATE INDEX idx_discovery_search_requests_epoch
    ON discovery_search_requests (ranking_epoch_id, occurred_at DESC);
--rollback DROP TABLE discovery_search_requests;

--changeset ninggiangboy:029-09-discovery-events
-- Append-only behaviour, deduplicated by the key the client issued, expiring on a date it carries
-- itself. These rows help attribution; they never redefine what a booking or a review says. Notice
-- what is absent: no address, no coordinate, no token, no payment detail, no IP address, no raw
-- user-agent string. Those columns do not exist so that no future job can be tempted to fill them.
CREATE TABLE discovery_events (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_key                       VARCHAR(64) NOT NULL,
    event_type                      VARCHAR(32) NOT NULL,
    schema_version                  SMALLINT NOT NULL DEFAULT 1,

    occurred_at                     TIMESTAMPTZ NOT NULL,
    received_at                     TIMESTAMPTZ NOT NULL,
    search_request_id               UUID,
    session_pseudonym               CHAR(64) NOT NULL,
    account_holder_id               UUID,
    listing_id                      UUID,

    position                        SMALLINT,
    page_number                     SMALLINT,
    visible_render                  BOOLEAN,
    dwell_ms                        INTEGER,
    interaction_surface             VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',

    ingest_state                    VARCHAR(24) NOT NULL DEFAULT 'ACCEPTED',
    rejection_reason                VARCHAR(48),
    retention_class                 VARCHAR(24) NOT NULL DEFAULT 'STANDARD',
    expires_at                      TIMESTAMPTZ NOT NULL,
    context                         JSONB,

    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_discovery_events_search_request FOREIGN KEY (search_request_id)
        REFERENCES discovery_search_requests (id),
    CONSTRAINT fk_discovery_events_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_discovery_events_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT uk_discovery_events_key UNIQUE (event_key),
    CONSTRAINT ck_discovery_events_type CHECK (
        event_type IN ('SEARCH_SUBMITTED', 'SEARCH_RESULTS_RETURNED', 'LISTING_IMPRESSION',
                       'LISTING_CLICKED', 'LISTING_VIEWED', 'MAP_MARKER_SELECTED',
                       'LISTING_FAVORITED', 'LISTING_UNFAVORITED', 'BOOKING_STARTED',
                       'BOOKING_CREATED', 'BOOKING_CONFIRMED', 'BOOKING_CANCELLED',
                       'STAY_COMPLETED', 'REVIEW_SUBMITTED', 'REVIEW_PUBLISHED')
    ),
    CONSTRAINT ck_discovery_events_surface CHECK (
        interaction_surface IN ('SEARCH_RESULTS', 'MAP', 'LISTING_DETAIL', 'RECOMMENDATION',
                                'SAVED_LISTINGS', 'BOOKING_FLOW', 'NOTIFICATION', 'SERVER',
                                'UNKNOWN')
    ),
    CONSTRAINT ck_discovery_events_ingest_state CHECK (
        ingest_state IN ('ACCEPTED', 'DUPLICATE_DISCARDED', 'REJECTED', 'QUARANTINED')
    ),
    CONSTRAINT ck_discovery_events_retention_class CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'LEGAL_HOLD')
    ),
    CONSTRAINT ck_discovery_events_schema_version CHECK (schema_version > 0),
    -- A listing-scoped event that names no listing is not a fact about anything.
    CONSTRAINT ck_discovery_events_listing_scoped CHECK (
        event_type NOT IN ('LISTING_IMPRESSION', 'LISTING_CLICKED', 'LISTING_VIEWED',
                           'MAP_MARKER_SELECTED', 'LISTING_FAVORITED', 'LISTING_UNFAVORITED')
            OR listing_id IS NOT NULL
    ),
    -- An impression is a listing that was rendered where somebody could see it, not one that merely
    -- appeared in a server response. Position without a search request is unverifiable.
    CONSTRAINT ck_discovery_events_impression CHECK (
        event_type <> 'LISTING_IMPRESSION'
            OR (visible_render AND search_request_id IS NOT NULL AND position IS NOT NULL)
    ),
    CONSTRAINT ck_discovery_events_position CHECK (
        (position IS NULL OR (position >= 1 AND search_request_id IS NOT NULL))
            AND (page_number IS NULL OR page_number >= 1)
    ),
    -- Dwell is capped because an open browser tab is not evidence of interest.
    CONSTRAINT ck_discovery_events_dwell CHECK (
        dwell_ms IS NULL OR (dwell_ms >= 0 AND dwell_ms <= 1800000)
    ),
    CONSTRAINT ck_discovery_events_rejection CHECK (
        (ingest_state IN ('REJECTED', 'QUARANTINED')) = (rejection_reason IS NOT NULL)
    ),
    CONSTRAINT ck_discovery_events_retention CHECK (expires_at > occurred_at)
);

CREATE INDEX idx_discovery_events_retention ON discovery_events (expires_at)
    WHERE retention_class <> 'LEGAL_HOLD';
CREATE INDEX idx_discovery_events_listing ON discovery_events (listing_id, occurred_at DESC)
    WHERE listing_id IS NOT NULL AND ingest_state = 'ACCEPTED';
CREATE INDEX idx_discovery_events_subject ON discovery_events (account_holder_id, occurred_at DESC)
    WHERE account_holder_id IS NOT NULL AND ingest_state = 'ACCEPTED';
CREATE INDEX idx_discovery_events_search_request ON discovery_events (search_request_id)
    WHERE search_request_id IS NOT NULL;
--rollback DROP TABLE discovery_events;

--changeset ninggiangboy:029-10-recommendation-reason-codes
-- The approved vocabulary an explanation may draw from, with the evidence it must have behind it.
-- A reason is attached to a result through a foreign key to this table rather than as free text,
-- because a string column cannot be asked whether the claim it carries was ever approved.
CREATE TABLE recommendation_reason_codes (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reason_code                     VARCHAR(64) NOT NULL,
    vocabulary_version              INTEGER NOT NULL,
    status                          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',

    reason_class                    VARCHAR(24) NOT NULL,
    personalized                    BOOLEAN NOT NULL DEFAULT false,
    requires_evidence               BOOLEAN NOT NULL DEFAULT true,
    supporting_feature_key          VARCHAR(64),
    minimum_confidence              NUMERIC(5, 4) NOT NULL DEFAULT 0,
    minimum_effective_evidence      NUMERIC(10, 4) NOT NULL DEFAULT 0,
    localization_key                VARCHAR(96),
    disclosure_label_required       BOOLEAN NOT NULL DEFAULT false,
    disclosure_review_reference     VARCHAR(128),

    approved_by_account_holder_id   UUID,
    approved_at                     TIMESTAMPTZ,
    retired_at                      TIMESTAMPTZ,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_recommendation_reason_codes_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_recommendation_reason_codes_identity UNIQUE (reason_code, vocabulary_version),
    CONSTRAINT ck_recommendation_reason_codes_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_recommendation_reason_codes_class CHECK (
        reason_class IN ('QUALITY', 'VALUE', 'LOCATION', 'ASPECT_MATCH', 'PRICE_FIT',
                         'RELIABILITY', 'AVAILABILITY', 'NEW_LISTING', 'SPONSORED')
    ),
    CONSTRAINT ck_recommendation_reason_codes_version CHECK (vocabulary_version > 0),
    CONSTRAINT ck_recommendation_reason_codes_thresholds CHECK (
        minimum_confidence BETWEEN 0 AND 1 AND minimum_effective_evidence >= 0
    ),
    -- A claim that needs evidence must name the feature that would supply it and how much of it is
    -- enough. Otherwise "needs evidence" is a comment, not a rule.
    CONSTRAINT ck_recommendation_reason_codes_evidence CHECK (
        NOT requires_evidence
            OR (supporting_feature_key IS NOT NULL AND minimum_effective_evidence > 0
                AND minimum_confidence > 0)
    ),
    -- Saying something about this guest in particular demands a confidence threshold.
    CONSTRAINT ck_recommendation_reason_codes_personalized CHECK (
        NOT personalized OR minimum_confidence > 0
    ),
    -- Paid placement is labelled. It is never an explanation of relevance.
    CONSTRAINT ck_recommendation_reason_codes_sponsored CHECK (
        reason_class <> 'SPONSORED' OR (disclosure_label_required AND NOT personalized)
    ),
    -- Nothing reaches a guest until somebody approved the wording and it can be localized.
    CONSTRAINT ck_recommendation_reason_codes_approval CHECK (
        status <> 'ACTIVE'
            OR (approved_at IS NOT NULL AND approved_by_account_holder_id IS NOT NULL
                AND localization_key IS NOT NULL)
    ),
    CONSTRAINT ck_recommendation_reason_codes_retired CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_recommendation_reason_codes_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_recommendation_reason_codes_active
    ON recommendation_reason_codes (reason_code) WHERE status = 'ACTIVE';
--rollback DROP TABLE recommendation_reason_codes;

--changeset ninggiangboy:029-11-discovery-market-priors
-- What a listing with no evidence is assumed to be, and at which geographic level that assumption
-- came from. The fallback level is stored because "we used the country prior" and "this neighbourhood
-- is like this" are different statements, and only one of them is honest about a new destination.
CREATE TABLE discovery_market_priors (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_level                     VARCHAR(16) NOT NULL,
    geo_area_id                     UUID,
    market_id                       UUID,
    metric_key                      VARCHAR(64) NOT NULL,

    prior_mean                      NUMERIC(12, 6) NOT NULL,
    prior_strength                  NUMERIC(10, 4) NOT NULL,
    sample_size                     BIGINT NOT NULL DEFAULT 0,
    minimum_sample_size             INTEGER NOT NULL,

    aggregation_version             VARCHAR(48) NOT NULL,
    input_watermark                 TIMESTAMPTZ,
    status                          VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
    computed_at                     TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ NOT NULL,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_discovery_market_priors_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id),
    CONSTRAINT fk_discovery_market_priors_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_discovery_market_priors_scope CHECK (
        scope_level IN ('NEIGHBORHOOD', 'LOCALITY', 'ADMIN_AREA', 'COUNTRY', 'GLOBAL')
    ),
    CONSTRAINT ck_discovery_market_priors_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    -- A geographic prior names its area; the global prior is the only one that has none.
    CONSTRAINT ck_discovery_market_priors_area_scope CHECK (
        (scope_level = 'GLOBAL') = (geo_area_id IS NULL)
    ),
    CONSTRAINT ck_discovery_market_priors_strength CHECK (prior_strength > 0),
    CONSTRAINT ck_discovery_market_priors_sample CHECK (
        sample_size >= 0 AND minimum_sample_size > 0
    ),
    -- A prior thin enough to describe a handful of listings would leak them. It may be computed,
    -- but it may not be the current one anybody serves from.
    CONSTRAINT ck_discovery_market_priors_threshold CHECK (
        status <> 'CURRENT' OR sample_size >= minimum_sample_size
    ),
    CONSTRAINT ck_discovery_market_priors_retention CHECK (expires_at > computed_at),
    CONSTRAINT ck_discovery_market_priors_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_discovery_market_priors_current
    ON discovery_market_priors (scope_level, geo_area_id, metric_key) NULLS NOT DISTINCT
    WHERE status = 'CURRENT';
CREATE INDEX idx_discovery_market_priors_stale ON discovery_market_priors (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE discovery_market_priors;

--changeset ninggiangboy:029-12-listing-discovery-profiles
-- The versioned, rebuildable read model discovery serves from. It deliberately holds no availability
-- and no trip price: those change by date and are request-time facts owned by inventory and pricing.
-- What it holds is comparison and summary -- a smoothed rating, a relative price percentile, content
-- completeness -- each dated, each expiring, each naming the evidence version behind it.
CREATE TABLE listing_discovery_profiles (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id                      UUID NOT NULL,
    profile_version                 INTEGER NOT NULL,
    status                          VARCHAR(16) NOT NULL DEFAULT 'CURRENT',

    aspect_profile_version_id       UUID,
    listing_quality_profile_id      UUID,
    smoothed_overall_rating         NUMERIC(6, 4),
    completed_stay_count            BIGINT NOT NULL DEFAULT 0,
    review_count                    BIGINT NOT NULL DEFAULT 0,
    host_cancellation_rate          NUMERIC(5, 4),
    reliability_score               NUMERIC(5, 4),
    relative_price_percentile       NUMERIC(5, 4),
    fee_transparency                NUMERIC(5, 4),
    content_completeness            NUMERIC(5, 4),
    listing_age_days                INTEGER,
    last_meaningful_update_at       TIMESTAMPTZ,

    prior_fallback_level            VARCHAR(16) NOT NULL DEFAULT 'NONE',
    prior_scope_geo_area_id         UUID,
    feature_schema_digest           CHAR(64) NOT NULL,
    aggregation_version             VARCHAR(48) NOT NULL,
    input_watermark                 TIMESTAMPTZ,
    source_manifest_digest          CHAR(64),
    computed_at                     TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ NOT NULL,
    superseded_by_profile_id        UUID,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listing_discovery_profiles_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id),
    CONSTRAINT fk_listing_discovery_profiles_aspects FOREIGN KEY (aspect_profile_version_id)
        REFERENCES aspect_profile_versions (id),
    CONSTRAINT fk_listing_discovery_profiles_quality FOREIGN KEY (listing_quality_profile_id)
        REFERENCES listing_quality_profiles (id),
    CONSTRAINT fk_listing_discovery_profiles_prior_area FOREIGN KEY (prior_scope_geo_area_id)
        REFERENCES geo_areas (id),
    CONSTRAINT fk_listing_discovery_profiles_supersession FOREIGN KEY (superseded_by_profile_id)
        REFERENCES listing_discovery_profiles (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_listing_discovery_profiles_identity UNIQUE (listing_id, profile_version),
    CONSTRAINT ck_listing_discovery_profiles_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    CONSTRAINT ck_listing_discovery_profiles_number CHECK (profile_version > 0),
    CONSTRAINT ck_listing_discovery_profiles_prior_level CHECK (
        prior_fallback_level IN ('NONE', 'NEIGHBORHOOD', 'LOCALITY', 'ADMIN_AREA', 'COUNTRY',
                                 'GLOBAL')
    ),
    CONSTRAINT ck_listing_discovery_profiles_rating CHECK (
        smoothed_overall_rating IS NULL OR smoothed_overall_rating BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_listing_discovery_profiles_fractions CHECK (
        (host_cancellation_rate IS NULL OR host_cancellation_rate BETWEEN 0 AND 1)
            AND (reliability_score IS NULL OR reliability_score BETWEEN 0 AND 1)
            AND (relative_price_percentile IS NULL OR relative_price_percentile BETWEEN 0 AND 1)
            AND (fee_transparency IS NULL OR fee_transparency BETWEEN 0 AND 1)
            AND (content_completeness IS NULL OR content_completeness BETWEEN 0 AND 1)
    ),
    CONSTRAINT ck_listing_discovery_profiles_counts CHECK (
        completed_stay_count >= 0 AND review_count >= 0
            AND (listing_age_days IS NULL OR listing_age_days >= 0)
    ),
    -- A listing with no completed stays has no rating of its own. If it carries a number, that
    -- number came from a prior, and the profile has to say which one.
    CONSTRAINT ck_listing_discovery_profiles_prior_used CHECK (
        completed_stay_count > 0 OR smoothed_overall_rating IS NULL
            OR prior_fallback_level <> 'NONE'
    ),
    -- A geographic prior names the area it was taken from.
    CONSTRAINT ck_listing_discovery_profiles_prior_area CHECK (
        prior_fallback_level IN ('NONE', 'GLOBAL') OR prior_scope_geo_area_id IS NOT NULL
    ),
    CONSTRAINT ck_listing_discovery_profiles_retention CHECK (expires_at > computed_at),
    CONSTRAINT ck_listing_discovery_profiles_self CHECK (superseded_by_profile_id <> id),
    CONSTRAINT ck_listing_discovery_profiles_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_listing_discovery_profiles_current
    ON listing_discovery_profiles (listing_id) WHERE status = 'CURRENT';
CREATE INDEX idx_listing_discovery_profiles_stale ON listing_discovery_profiles (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE listing_discovery_profiles;

--changeset ninggiangboy:029-13-listing-discovery-features
-- One feature within one profile version, with its own confidence and evidence beside it. An unknown
-- feature is absent or explicitly insufficient; it is never stored as a zero, because zero quality
-- and no information are different claims and only one of them should be able to sink a listing.
CREATE TABLE listing_discovery_features (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_discovery_profile_id    UUID NOT NULL,
    feature_key                     VARCHAR(64) NOT NULL,
    feature_family                  VARCHAR(24) NOT NULL,

    numeric_value                   NUMERIC(14, 6),
    text_value                      VARCHAR(64),
    confidence                      NUMERIC(5, 4),
    effective_evidence              NUMERIC(10, 4),
    evidence_class                  VARCHAR(16) NOT NULL DEFAULT 'INSUFFICIENT',
    trend                           NUMERIC(6, 4),
    last_evidence_at                TIMESTAMPTZ,

    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_listing_discovery_features_profile FOREIGN KEY (listing_discovery_profile_id)
        REFERENCES listing_discovery_profiles (id),
    CONSTRAINT uk_listing_discovery_features_key
        UNIQUE (listing_discovery_profile_id, feature_key),
    CONSTRAINT ck_listing_discovery_features_family CHECK (
        feature_family IN ('QUALITY', 'VALUE', 'LOCATION', 'CONTENT', 'RELIABILITY', 'ASPECT',
                           'SUPPLY_CONDITION', 'HOST')
    ),
    CONSTRAINT ck_listing_discovery_features_evidence_class CHECK (
        evidence_class IN ('INSUFFICIENT', 'STRENGTH', 'WEAKNESS', 'MIXED', 'NEUTRAL')
    ),
    -- A feature holds one value, of one kind.
    CONSTRAINT ck_listing_discovery_features_value CHECK (
        (numeric_value IS NOT NULL) <> (text_value IS NOT NULL) OR
            (numeric_value IS NULL AND text_value IS NULL AND evidence_class = 'INSUFFICIENT')
    ),
    CONSTRAINT ck_listing_discovery_features_confidence CHECK (
        confidence IS NULL OR confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_listing_discovery_features_evidence CHECK (
        effective_evidence IS NULL OR effective_evidence >= 0
    ),
    -- Calling something a strength or a weakness requires evidence and a confidence to attach it to.
    CONSTRAINT ck_listing_discovery_features_claim CHECK (
        evidence_class = 'INSUFFICIENT'
            OR (effective_evidence IS NOT NULL AND effective_evidence > 0 AND confidence IS NOT NULL)
    )
);

CREATE INDEX idx_listing_discovery_features_profile
    ON listing_discovery_features (listing_discovery_profile_id);
--rollback DROP TABLE listing_discovery_features;

--changeset ninggiangboy:029-14-listing-outcome-aggregates
-- Funnel counts for one listing over one window, stored beside the exposure conditions that produced
-- them. A raw conversion rate is not a quality signal: a listing shown at rank one gets more clicks
-- for reasons that have nothing to do with the listing. So a corrected aggregate must carry its mean
-- position and its propensity-weighted denominator, and one that carries neither may not claim to be
-- corrected.
CREATE TABLE listing_outcome_aggregates (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id                      UUID NOT NULL,
    window_kind                     VARCHAR(16) NOT NULL,
    window_start                    DATE,
    window_end                      DATE NOT NULL,
    status                          VARCHAR(16) NOT NULL DEFAULT 'CURRENT',

    impression_count                BIGINT NOT NULL DEFAULT 0,
    propensity_weighted_impressions NUMERIC(14, 4),
    mean_position                   NUMERIC(7, 3),
    position_bias_corrected         BOOLEAN NOT NULL DEFAULT false,

    click_count                     BIGINT NOT NULL DEFAULT 0,
    detail_view_count               BIGINT NOT NULL DEFAULT 0,
    save_count                      BIGINT NOT NULL DEFAULT 0,
    booking_started_count           BIGINT NOT NULL DEFAULT 0,
    booking_confirmed_count         BIGINT NOT NULL DEFAULT 0,
    stay_completed_count            BIGINT NOT NULL DEFAULT 0,
    guest_cancellation_count        BIGINT NOT NULL DEFAULT 0,
    host_cancellation_count         BIGINT NOT NULL DEFAULT 0,
    expiration_count                BIGINT NOT NULL DEFAULT 0,
    no_show_count                   BIGINT NOT NULL DEFAULT 0,
    refund_count                    BIGINT NOT NULL DEFAULT 0,
    dispute_count                   BIGINT NOT NULL DEFAULT 0,
    repeat_booking_count            BIGINT NOT NULL DEFAULT 0,

    aggregation_version             VARCHAR(48) NOT NULL,
    input_watermark                 TIMESTAMPTZ,
    reconciled_at                   TIMESTAMPTZ,
    computed_at                     TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listing_outcome_aggregates_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id),
    CONSTRAINT uk_listing_outcome_aggregates_window
        UNIQUE (listing_id, window_kind, window_end),
    CONSTRAINT ck_listing_outcome_aggregates_window_kind CHECK (
        window_kind IN ('ROLLING_7D', 'ROLLING_30D', 'ROLLING_90D', 'ROLLING_365D', 'ALL_TIME')
    ),
    CONSTRAINT ck_listing_outcome_aggregates_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    CONSTRAINT ck_listing_outcome_aggregates_window CHECK (
        (window_kind = 'ALL_TIME') = (window_start IS NULL)
    ),
    CONSTRAINT ck_listing_outcome_aggregates_window_order CHECK (
        window_start IS NULL OR window_end >= window_start
    ),
    CONSTRAINT ck_listing_outcome_aggregates_counts CHECK (
        impression_count >= 0 AND click_count >= 0 AND detail_view_count >= 0 AND save_count >= 0
            AND booking_started_count >= 0 AND booking_confirmed_count >= 0
            AND stay_completed_count >= 0 AND guest_cancellation_count >= 0
            AND host_cancellation_count >= 0 AND expiration_count >= 0 AND no_show_count >= 0
            AND refund_count >= 0 AND dispute_count >= 0 AND repeat_booking_count >= 0
    ),
    CONSTRAINT ck_listing_outcome_aggregates_weights CHECK (
        (propensity_weighted_impressions IS NULL OR propensity_weighted_impressions >= 0)
            AND (mean_position IS NULL OR mean_position >= 1)
    ),
    -- Correction is a claim about method. Making it requires the exposure conditions it was made
    -- from to be present in the same row.
    CONSTRAINT ck_listing_outcome_aggregates_correction CHECK (
        NOT position_bias_corrected
            OR (propensity_weighted_impressions IS NOT NULL AND mean_position IS NOT NULL)
    ),
    CONSTRAINT ck_listing_outcome_aggregates_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_listing_outcome_aggregates_current
    ON listing_outcome_aggregates (listing_id, window_kind) WHERE status = 'CURRENT';
--rollback DROP TABLE listing_outcome_aggregates;

--changeset ninggiangboy:029-15-guest-preference-profiles
-- One guest's derived preference profile, dated and bounded by the evidence window it was built
-- from. evidence_from is the column that makes an erasure directive real: a profile whose evidence
-- reaches back before a directive's cutoff is refused, so clearing a profile cannot be undone by
-- tonight's batch job rebuilding it from exactly the behaviour that was supposed to be forgotten.
CREATE TABLE guest_preference_profiles (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id               UUID NOT NULL,
    profile_version                 INTEGER NOT NULL,
    status                          VARCHAR(16) NOT NULL DEFAULT 'CURRENT',

    evidence_from                   TIMESTAMPTZ NOT NULL,
    evidence_to                     TIMESTAMPTZ NOT NULL,
    input_watermark                 TIMESTAMPTZ,
    erasure_directive_id            UUID,

    feature_generation_version      VARCHAR(48) NOT NULL,
    feature_schema_digest           CHAR(64) NOT NULL,
    overall_confidence              NUMERIC(5, 4) NOT NULL DEFAULT 0,
    evidence_event_count            BIGINT NOT NULL DEFAULT 0,
    evidence_stay_count             BIGINT NOT NULL DEFAULT 0,

    computed_at                     TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ NOT NULL,
    superseded_by_profile_id        UUID,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_guest_preference_profiles_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_guest_preference_profiles_directive FOREIGN KEY (erasure_directive_id)
        REFERENCES personalization_erasure_directives (id),
    CONSTRAINT fk_guest_preference_profiles_supersession FOREIGN KEY (superseded_by_profile_id)
        REFERENCES guest_preference_profiles (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_guest_preference_profiles_identity UNIQUE (account_holder_id, profile_version),
    CONSTRAINT ck_guest_preference_profiles_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    CONSTRAINT ck_guest_preference_profiles_number CHECK (profile_version > 0),
    CONSTRAINT ck_guest_preference_profiles_window CHECK (evidence_to >= evidence_from),
    CONSTRAINT ck_guest_preference_profiles_confidence CHECK (
        overall_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_guest_preference_profiles_counts CHECK (
        evidence_event_count >= 0 AND evidence_stay_count >= 0
    ),
    -- Confidence without evidence is a number somebody made up.
    CONSTRAINT ck_guest_preference_profiles_grounded CHECK (
        overall_confidence = 0 OR evidence_event_count > 0 OR evidence_stay_count > 0
    ),
    CONSTRAINT ck_guest_preference_profiles_retention CHECK (expires_at > computed_at),
    CONSTRAINT ck_guest_preference_profiles_self CHECK (superseded_by_profile_id <> id),
    CONSTRAINT ck_guest_preference_profiles_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_guest_preference_profiles_current
    ON guest_preference_profiles (account_holder_id) WHERE status = 'CURRENT';
CREATE INDEX idx_guest_preference_profiles_stale ON guest_preference_profiles (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE guest_preference_profiles;

--changeset ninggiangboy:029-16-guest-preference-features
-- One preference dimension. Importance, direction, and confidence are three separate columns on
-- purpose: a guest who mentions noise in every review may care enormously about quiet or may have
-- been unlucky once, and collapsing those into a single score loses the only distinction that
-- matters. Long-term and recent values are kept apart so recent intent can be expired without
-- destroying stable history.
CREATE TABLE guest_preference_features (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    guest_preference_profile_id     UUID NOT NULL,
    dimension_kind                  VARCHAR(24) NOT NULL,
    dimension_key                   VARCHAR(64) NOT NULL,
    context_segment                 VARCHAR(32),

    importance                      NUMERIC(5, 4) NOT NULL DEFAULT 0,
    direction                       VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    preferred_level                 NUMERIC(6, 4),
    target_low                      NUMERIC(6, 4),
    target_high                     NUMERIC(6, 4),
    confidence                      NUMERIC(5, 4) NOT NULL DEFAULT 0,

    long_term_value                 NUMERIC(6, 4),
    recent_value                    NUMERIC(6, 4),
    evidence_event_count            BIGINT NOT NULL DEFAULT 0,
    evidence_stay_count             BIGINT NOT NULL DEFAULT 0,
    last_evidence_at                TIMESTAMPTZ,
    guest_corrected                 BOOLEAN NOT NULL DEFAULT false,

    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_guest_preference_features_profile FOREIGN KEY (guest_preference_profile_id)
        REFERENCES guest_preference_profiles (id),
    CONSTRAINT uk_guest_preference_features_dimension
        UNIQUE NULLS NOT DISTINCT
        (guest_preference_profile_id, dimension_kind, dimension_key, context_segment),
    CONSTRAINT ck_guest_preference_features_kind CHECK (
        dimension_kind IN ('ASPECT', 'PRICE', 'ROOM_TYPE', 'AMENITY', 'LOCATION', 'POLICY',
                           'TRIP_SHAPE', 'HOST_AFFINITY')
    ),
    CONSTRAINT ck_guest_preference_features_direction CHECK (
        direction IN ('POSITIVE', 'NEGATIVE', 'TARGET_RANGE', 'UNKNOWN')
    ),
    CONSTRAINT ck_guest_preference_features_segment CHECK (
        context_segment IS NULL
            OR context_segment IN ('SOLO_TRIP', 'FAMILY_TRIP', 'BUSINESS_TRIP', 'GROUP_TRIP',
                                   'LONG_STAY', 'WEEKEND')
    ),
    CONSTRAINT ck_guest_preference_features_bounds CHECK (
        importance BETWEEN 0 AND 1 AND confidence BETWEEN 0 AND 1
    ),
    -- A target range needs both ends and they must be the right way round; any other direction has
    -- no range to state.
    CONSTRAINT ck_guest_preference_features_range CHECK (
        (direction = 'TARGET_RANGE'
             AND target_low IS NOT NULL AND target_high IS NOT NULL AND target_low <= target_high)
        OR (direction <> 'TARGET_RANGE' AND target_low IS NULL AND target_high IS NULL)
    ),
    -- An unknown direction cannot also state a preferred level; that would be a direction.
    CONSTRAINT ck_guest_preference_features_unknown CHECK (
        direction <> 'UNKNOWN' OR preferred_level IS NULL
    ),
    CONSTRAINT ck_guest_preference_features_counts CHECK (
        evidence_event_count >= 0 AND evidence_stay_count >= 0
    ),
    -- Inferred confidence needs evidence. A guest who set the preference themselves is evidence of
    -- a different kind, and is allowed to stand alone.
    CONSTRAINT ck_guest_preference_features_grounded CHECK (
        confidence = 0 OR guest_corrected
            OR evidence_event_count > 0 OR evidence_stay_count > 0
    ),
    -- A contextual segment is a narrower claim than the general one, so it needs its own evidence
    -- rather than inheriting the profile's.
    CONSTRAINT ck_guest_preference_features_segmented CHECK (
        context_segment IS NULL OR guest_corrected OR evidence_stay_count > 0
    )
);

CREATE INDEX idx_guest_preference_features_profile
    ON guest_preference_features (guest_preference_profile_id);
--rollback DROP TABLE guest_preference_features;

--changeset ninggiangboy:029-17-guest-session-intents
-- What this session appears to be looking for, which is a different thing from what this guest
-- prefers. It is keyed on the session pseudonym, it always carries an expiry, and promoting it into
-- the durable profile is a flag somebody must set deliberately -- an anonymous session cannot grow
-- into a stored profile by accident.
CREATE TABLE guest_session_intents (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_pseudonym               CHAR(64) NOT NULL,
    account_holder_id               UUID,
    dimension_kind                  VARCHAR(24) NOT NULL,
    dimension_key                   VARCHAR(64) NOT NULL,

    observed_value                  NUMERIC(6, 4),
    observation_count               INTEGER NOT NULL DEFAULT 1,
    first_observed_at               TIMESTAMPTZ NOT NULL,
    last_observed_at                TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ NOT NULL,
    durable_promotion_allowed       BOOLEAN NOT NULL DEFAULT false,

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_guest_session_intents_account FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_guest_session_intents_dimension
        UNIQUE (session_pseudonym, dimension_kind, dimension_key),
    CONSTRAINT ck_guest_session_intents_kind CHECK (
        dimension_kind IN ('ASPECT', 'PRICE', 'ROOM_TYPE', 'AMENITY', 'LOCATION', 'POLICY',
                           'TRIP_SHAPE', 'HOST_AFFINITY')
    ),
    CONSTRAINT ck_guest_session_intents_count CHECK (observation_count >= 1),
    CONSTRAINT ck_guest_session_intents_order CHECK (last_observed_at >= first_observed_at),
    CONSTRAINT ck_guest_session_intents_expiry CHECK (expires_at > first_observed_at),
    -- Nothing anonymous may be promoted into a durable profile: there is no subject to attach it to
    -- and no settings row to ask for permission.
    CONSTRAINT ck_guest_session_intents_promotion CHECK (
        NOT durable_promotion_allowed OR account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_guest_session_intents_row_version CHECK (version >= 0)
);

CREATE INDEX idx_guest_session_intents_expiry ON guest_session_intents (expires_at);
--rollback DROP TABLE guest_session_intents;

--changeset ninggiangboy:029-18-exploration-budget-windows
-- Exploration spends guest attention on listings the ranker is unsure about. That is worth doing and
-- it is not free, so it gets a window, a ceiling, a per-listing cap, and a quality floor. A window
-- can never record consuming more than it allocated, which is what turns "bounded exploration" from
-- an intention into an invariant.
CREATE TABLE exploration_budget_windows (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    window_key                      VARCHAR(64) NOT NULL,
    market_id                       UUID,
    geo_area_id                     UUID,
    ranking_policy_version_id       UUID NOT NULL,
    state                           VARCHAR(16) NOT NULL DEFAULT 'OPEN',

    opens_at                        TIMESTAMPTZ NOT NULL,
    closes_at                       TIMESTAMPTZ NOT NULL,
    traffic_share_limit             NUMERIC(5, 4) NOT NULL,
    impression_limit                BIGINT NOT NULL,
    impressions_consumed            BIGINT NOT NULL DEFAULT 0,
    per_listing_impression_cap      INTEGER NOT NULL,
    quality_floor                   NUMERIC(5, 4) NOT NULL,
    safety_eligibility_required     BOOLEAN NOT NULL DEFAULT true,
    experiment_reference            VARCHAR(128),

    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_exploration_budget_windows_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_exploration_budget_windows_area FOREIGN KEY (geo_area_id)
        REFERENCES geo_areas (id),
    CONSTRAINT fk_exploration_budget_windows_policy FOREIGN KEY (ranking_policy_version_id)
        REFERENCES ranking_policy_versions (id),
    CONSTRAINT uk_exploration_budget_windows_key UNIQUE (window_key),
    CONSTRAINT ck_exploration_budget_windows_state CHECK (
        state IN ('OPEN', 'EXHAUSTED', 'SUSPENDED', 'CLOSED')
    ),
    CONSTRAINT ck_exploration_budget_windows_window CHECK (closes_at > opens_at),
    CONSTRAINT ck_exploration_budget_windows_limits CHECK (
        traffic_share_limit > 0 AND traffic_share_limit <= 1
            AND impression_limit > 0 AND per_listing_impression_cap > 0
            AND quality_floor > 0 AND quality_floor <= 1
    ),
    CONSTRAINT ck_exploration_budget_windows_consumption CHECK (
        impressions_consumed >= 0 AND impressions_consumed <= impression_limit
    ),
    -- An exhausted window is one that reached its ceiling, not one somebody labelled exhausted.
    CONSTRAINT ck_exploration_budget_windows_exhausted CHECK (
        state <> 'EXHAUSTED' OR impressions_consumed = impression_limit
    ),
    CONSTRAINT ck_exploration_budget_windows_row_version CHECK (version >= 0)
);

CREATE INDEX idx_exploration_budget_windows_open ON exploration_budget_windows (closes_at)
    WHERE state = 'OPEN';
--rollback DROP TABLE exploration_budget_windows;

--changeset ninggiangboy:029-19-ranking-exposures
-- What was actually shown, where, by which ranker, on what features. This is the row that makes
-- position-bias correction possible later: without a durable record of the position a listing was
-- given, every conversion rate computed from the event stream is confounded by the ranking that
-- produced it, and the model trained on those rates learns to reproduce its own history.
CREATE TABLE ranking_exposures (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    search_request_id               UUID NOT NULL,
    listing_id                      UUID NOT NULL,
    ranking_epoch_id                UUID NOT NULL,
    ranking_policy_version_id       UUID NOT NULL,
    ranking_model_version_id        UUID,

    exposure_reason                 VARCHAR(16) NOT NULL DEFAULT 'ORGANIC',
    exploration_budget_window_id    UUID,
    sponsored_label_shown           BOOLEAN NOT NULL DEFAULT false,
    position                        SMALLINT NOT NULL,
    page_number                     SMALLINT NOT NULL DEFAULT 1,

    score                           NUMERIC(12, 6),
    baseline_score                  NUMERIC(12, 6),
    personalized_contribution       NUMERIC(12, 6),
    position_propensity             NUMERIC(7, 6),
    diversity_adjusted              BOOLEAN NOT NULL DEFAULT false,

    listing_discovery_profile_id    UUID,
    guest_preference_profile_id     UUID,
    feature_vector_digest           CHAR(64),
    prior_fallback_level            VARCHAR(16) NOT NULL DEFAULT 'NONE',
    experiment_assignment_key       VARCHAR(64),

    occurred_at                     TIMESTAMPTZ NOT NULL,
    expires_at                      TIMESTAMPTZ NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ranking_exposures_search_request FOREIGN KEY (search_request_id)
        REFERENCES discovery_search_requests (id),
    CONSTRAINT fk_ranking_exposures_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_ranking_exposures_epoch FOREIGN KEY (ranking_epoch_id)
        REFERENCES ranking_epochs (id),
    CONSTRAINT fk_ranking_exposures_policy FOREIGN KEY (ranking_policy_version_id)
        REFERENCES ranking_policy_versions (id),
    CONSTRAINT fk_ranking_exposures_model FOREIGN KEY (ranking_model_version_id)
        REFERENCES ranking_model_versions (id),
    CONSTRAINT fk_ranking_exposures_exploration FOREIGN KEY (exploration_budget_window_id)
        REFERENCES exploration_budget_windows (id),
    CONSTRAINT fk_ranking_exposures_listing_profile FOREIGN KEY (listing_discovery_profile_id)
        REFERENCES listing_discovery_profiles (id),
    CONSTRAINT fk_ranking_exposures_guest_profile FOREIGN KEY (guest_preference_profile_id)
        REFERENCES guest_preference_profiles (id),
    CONSTRAINT uk_ranking_exposures_listing UNIQUE (search_request_id, listing_id),
    CONSTRAINT uk_ranking_exposures_position UNIQUE (search_request_id, position),
    CONSTRAINT ck_ranking_exposures_reason CHECK (
        exposure_reason IN ('ORGANIC', 'EXPLORATION', 'SPONSORED', 'PINNED')
    ),
    CONSTRAINT ck_ranking_exposures_prior_level CHECK (
        prior_fallback_level IN ('NONE', 'NEIGHBORHOOD', 'LOCALITY', 'ADMIN_AREA', 'COUNTRY',
                                 'GLOBAL')
    ),
    CONSTRAINT ck_ranking_exposures_position CHECK (position >= 1 AND page_number >= 1),
    CONSTRAINT ck_ranking_exposures_propensity CHECK (
        position_propensity IS NULL OR (position_propensity > 0 AND position_propensity <= 1)
    ),
    -- Exploration is spent from a named budget. Without one there is no ceiling and no cap, which
    -- is the whole difference between exploration and unrestricted randomness.
    CONSTRAINT ck_ranking_exposures_exploration CHECK (
        (exposure_reason = 'EXPLORATION') = (exploration_budget_window_id IS NOT NULL)
    ),
    -- Paid placement is labelled where the guest can see it, and nothing else claims to be.
    CONSTRAINT ck_ranking_exposures_sponsored CHECK (
        (exposure_reason = 'SPONSORED') = sponsored_label_shown
    ),
    -- A personalized contribution must name the profile it came from, or it cannot be explained,
    -- audited, or erased when the guest asks.
    CONSTRAINT ck_ranking_exposures_personalized CHECK (
        personalized_contribution IS NULL OR guest_preference_profile_id IS NOT NULL
    ),
    -- A model-served rank must be reproducible from a recorded feature vector.
    CONSTRAINT ck_ranking_exposures_reproducible CHECK (
        ranking_model_version_id IS NULL OR feature_vector_digest IS NOT NULL
    ),
    CONSTRAINT ck_ranking_exposures_retention CHECK (expires_at > occurred_at)
);

CREATE INDEX idx_ranking_exposures_retention ON ranking_exposures (expires_at);
CREATE INDEX idx_ranking_exposures_listing ON ranking_exposures (listing_id, occurred_at DESC);
CREATE INDEX idx_ranking_exposures_exploration
    ON ranking_exposures (exploration_budget_window_id, listing_id)
    WHERE exploration_budget_window_id IS NOT NULL;
CREATE INDEX idx_ranking_exposures_epoch ON ranking_exposures (ranking_epoch_id, occurred_at DESC);
--rollback DROP TABLE ranking_exposures;

--changeset ninggiangboy:029-20-ranking-exposure-reasons
-- The reasons actually shown for one result, each one a foreign key into the approved vocabulary
-- with the confidence and evidence that supported it at the time. A reason that cannot clear its own
-- threshold is simply not written; the listing still appears, without an explanation.
CREATE TABLE ranking_exposure_reasons (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ranking_exposure_id             UUID NOT NULL,
    recommendation_reason_code_id   UUID NOT NULL,
    display_rank                    SMALLINT NOT NULL,

    supporting_confidence           NUMERIC(5, 4),
    supporting_evidence             NUMERIC(10, 4),
    supporting_feature_key          VARCHAR(64),

    created_at                      TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_ranking_exposure_reasons_exposure FOREIGN KEY (ranking_exposure_id)
        REFERENCES ranking_exposures (id),
    CONSTRAINT fk_ranking_exposure_reasons_code FOREIGN KEY (recommendation_reason_code_id)
        REFERENCES recommendation_reason_codes (id),
    CONSTRAINT uk_ranking_exposure_reasons_code
        UNIQUE (ranking_exposure_id, recommendation_reason_code_id),
    CONSTRAINT uk_ranking_exposure_reasons_rank UNIQUE (ranking_exposure_id, display_rank),
    CONSTRAINT ck_ranking_exposure_reasons_rank CHECK (display_rank >= 1),
    CONSTRAINT ck_ranking_exposure_reasons_bounds CHECK (
        (supporting_confidence IS NULL OR supporting_confidence BETWEEN 0 AND 1)
            AND (supporting_evidence IS NULL OR supporting_evidence >= 0)
    )
);

CREATE INDEX idx_ranking_exposure_reasons_exposure
    ON ranking_exposure_reasons (ranking_exposure_id);
--rollback DROP TABLE ranking_exposure_reasons;

--changeset ninggiangboy:029-21-discovery-append-only splitStatements:false
-- Observation rows are written once. A search log, an event, an exposure, a derived feature, and an
-- erasure application are all statements about a moment that has passed; editing one rewrites
-- history, and every bias correction and every audit downstream depends on that not happening.
-- Deletion is deliberately still permitted, because retention windows and erasure directives have to
-- be able to remove these rows -- that is a different act from quietly changing what they said.
-- The permitted columns are passed per table, so one function serves seven tables with nothing else
-- in common.
CREATE FUNCTION discovery_append_only() RETURNS TRIGGER AS $$
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
            '% row % is append-only; write a new row rather than editing what was observed',
            TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_discovery_events_append_only
    BEFORE UPDATE ON discovery_events
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only(
        'ingest_state', 'rejection_reason', 'retention_class', 'expires_at');

CREATE TRIGGER trg_discovery_search_requests_append_only
    BEFORE UPDATE ON discovery_search_requests
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only('expires_at');

CREATE TRIGGER trg_ranking_exposures_append_only
    BEFORE UPDATE ON ranking_exposures
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only('expires_at');

CREATE TRIGGER trg_ranking_exposure_reasons_append_only
    BEFORE UPDATE ON ranking_exposure_reasons
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only();

CREATE TRIGGER trg_listing_discovery_features_append_only
    BEFORE UPDATE ON listing_discovery_features
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only();

CREATE TRIGGER trg_guest_preference_features_append_only
    BEFORE UPDATE ON guest_preference_features
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only();

CREATE TRIGGER trg_personalization_erasure_applications_append_only
    BEFORE UPDATE ON personalization_erasure_applications
    FOR EACH ROW EXECUTE FUNCTION discovery_append_only();
--rollback DROP TRIGGER trg_personalization_erasure_applications_append_only ON personalization_erasure_applications;
--rollback DROP TRIGGER trg_guest_preference_features_append_only ON guest_preference_features;
--rollback DROP TRIGGER trg_listing_discovery_features_append_only ON listing_discovery_features;
--rollback DROP TRIGGER trg_ranking_exposure_reasons_append_only ON ranking_exposure_reasons;
--rollback DROP TRIGGER trg_ranking_exposures_append_only ON ranking_exposures;
--rollback DROP TRIGGER trg_discovery_search_requests_append_only ON discovery_search_requests;
--rollback DROP TRIGGER trg_discovery_events_append_only ON discovery_events;
--rollback DROP FUNCTION discovery_append_only();

--changeset ninggiangboy:029-22-derived-profile-supersession splitStatements:false
-- A superseded profile version is the record of what was served yesterday. Editing it makes
-- yesterday's rank unreproducible, so it is frozen, and -- the part that is easy to forget -- its
-- feature rows are frozen with it, including against INSERT. A profile that can gain a new feature
-- after it stopped being current is not a version, it is a mutable table with a version column.
CREATE FUNCTION discovery_profile_freeze() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% row % is % and cannot be changed; compute a new profile version instead',
        TG_TABLE_NAME, OLD.id, OLD.status
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_listing_discovery_profiles_freeze
    BEFORE UPDATE ON listing_discovery_profiles
    FOR EACH ROW WHEN (OLD.status IN ('SUPERSEDED', 'FAILED'))
    EXECUTE FUNCTION discovery_profile_freeze();

CREATE TRIGGER trg_guest_preference_profiles_freeze
    BEFORE UPDATE ON guest_preference_profiles
    FOR EACH ROW WHEN (OLD.status IN ('SUPERSEDED', 'FAILED'))
    EXECUTE FUNCTION discovery_profile_freeze();

CREATE FUNCTION discovery_profile_child_guard() RETURNS TRIGGER AS $$
DECLARE
    parent_id UUID;
    parent_status TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    IF TG_TABLE_NAME = 'listing_discovery_features' THEN
        parent_id := NEW.listing_discovery_profile_id;
        SELECT status INTO parent_status FROM listing_discovery_profiles WHERE id = parent_id;
    ELSE
        parent_id := NEW.guest_preference_profile_id;
        SELECT status INTO parent_status FROM guest_preference_profiles WHERE id = parent_id;
    END IF;
    IF parent_status NOT IN ('CURRENT', 'REBUILDING') THEN
        RAISE EXCEPTION
            'profile % is %; a feature cannot be added to a profile version that is no longer being built',
            parent_id, parent_status
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_listing_discovery_features_guard
    BEFORE INSERT OR UPDATE ON listing_discovery_features
    FOR EACH ROW EXECUTE FUNCTION discovery_profile_child_guard();

CREATE TRIGGER trg_guest_preference_features_guard
    BEFORE INSERT OR UPDATE ON guest_preference_features
    FOR EACH ROW EXECUTE FUNCTION discovery_profile_child_guard();
--rollback DROP TRIGGER trg_guest_preference_features_guard ON guest_preference_features;
--rollback DROP TRIGGER trg_listing_discovery_features_guard ON listing_discovery_features;
--rollback DROP FUNCTION discovery_profile_child_guard();
--rollback DROP TRIGGER trg_guest_preference_profiles_freeze ON guest_preference_profiles;
--rollback DROP TRIGGER trg_listing_discovery_profiles_freeze ON listing_discovery_profiles;
--rollback DROP FUNCTION discovery_profile_freeze();

--changeset ninggiangboy:029-23-personalization-honored splitStatements:false
-- The opt-out and the erasure cutoff enforced where they cannot be skipped. A profile for a guest
-- who turned behavioral profiling off is refused outright, and a profile whose evidence window
-- reaches back past an erasure cutoff is refused even though every column in it is individually
-- valid -- which is exactly the case the feature document warns about, where clearing a profile is
-- undone by the next batch run rebuilding it from the same behaviour.
CREATE FUNCTION guest_preference_profile_permitted() RETURNS TRIGGER AS $$
DECLARE
    profiling_allowed BOOLEAN;
    cutoff TIMESTAMPTZ;
BEGIN
    -- Retiring a profile is how a service complies with an opt-out or an erasure directive. Probing
    -- found that guarding every write regardless of state made the guard self-defeating: the stale
    -- profile that the directive was meant to clear could no longer be superseded, so it stayed
    -- current forever. Only a profile that is servable has to answer for its evidence.
    IF NEW.status NOT IN ('CURRENT', 'REBUILDING') THEN
        RETURN NEW;
    END IF;

    SELECT behavioral_profiling INTO profiling_allowed
        FROM personalization_settings WHERE account_holder_id = NEW.account_holder_id;
    IF profiling_allowed IS NOT NULL AND NOT profiling_allowed THEN
        RAISE EXCEPTION
            'account holder % has turned behavioral profiling off; no preference profile may be built for them',
            NEW.account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT max(evidence_cutoff_at) INTO cutoff
        FROM personalization_erasure_directives
        WHERE account_holder_id = NEW.account_holder_id
            AND state IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED');
    IF cutoff IS NOT NULL AND NEW.evidence_from < cutoff THEN
        RAISE EXCEPTION
            'evidence from % predates the erasure cutoff % for account holder %; rebuild from behaviour after the cutoff',
            NEW.evidence_from, cutoff, NEW.account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_guest_preference_profiles_permitted
    BEFORE INSERT OR UPDATE ON guest_preference_profiles
    FOR EACH ROW EXECUTE FUNCTION guest_preference_profile_permitted();

CREATE FUNCTION guest_session_intent_permitted() RETURNS TRIGGER AS $$
DECLARE
    intent_allowed BOOLEAN;
    profiling_allowed BOOLEAN;
BEGIN
    SELECT session_intent_use, behavioral_profiling INTO intent_allowed, profiling_allowed
        FROM personalization_settings WHERE account_holder_id = NEW.account_holder_id;
    IF intent_allowed IS NOT NULL AND NOT (intent_allowed AND profiling_allowed) THEN
        RAISE EXCEPTION
            'account holder % has not permitted session intent to be kept; it may steer this session but not be promoted',
            NEW.account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_guest_session_intents_permitted
    BEFORE INSERT OR UPDATE ON guest_session_intents
    FOR EACH ROW WHEN (NEW.durable_promotion_allowed)
    EXECUTE FUNCTION guest_session_intent_permitted();
--rollback DROP TRIGGER trg_guest_session_intents_permitted ON guest_session_intents;
--rollback DROP FUNCTION guest_session_intent_permitted();
--rollback DROP TRIGGER trg_guest_preference_profiles_permitted ON guest_preference_profiles;
--rollback DROP FUNCTION guest_preference_profile_permitted();

--changeset ninggiangboy:029-24-exposure-integrity splitStatements:false
-- An exposure must agree with the search that produced it. The epoch, the policy, and the model have
-- to be the same ones the search request recorded, because a cursor bound to one epoch that is
-- quietly served by another ranker produces duplicate and missing results that look like a database
-- fault. And one guest's preference profile may never rank another guest's search: that is not a
-- relevance bug, it is a disclosure.
CREATE FUNCTION ranking_exposure_integrity() RETURNS TRIGGER AS $$
DECLARE
    epoch_state TEXT;
    epoch_policy UUID;
    epoch_model UUID;
    request_epoch UUID;
    request_state TEXT;
    request_holder UUID;
    request_ranked INTEGER;
    profile_holder UUID;
BEGIN
    SELECT state, ranking_policy_version_id, ranking_model_version_id
        INTO epoch_state, epoch_policy, epoch_model
        FROM ranking_epochs WHERE id = NEW.ranking_epoch_id;
    IF epoch_state = 'INVALIDATED' THEN
        RAISE EXCEPTION 'ranking epoch % was invalidated; it may not serve further results',
            NEW.ranking_epoch_id USING ERRCODE = 'restrict_violation';
    END IF;
    IF epoch_policy <> NEW.ranking_policy_version_id THEN
        RAISE EXCEPTION
            'exposure claims policy version % but epoch % was opened under %',
            NEW.ranking_policy_version_id, NEW.ranking_epoch_id, epoch_policy
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF epoch_model IS NOT NULL AND epoch_model IS DISTINCT FROM NEW.ranking_model_version_id THEN
        RAISE EXCEPTION
            'exposure claims model version % but epoch % was opened under %',
            NEW.ranking_model_version_id, NEW.ranking_epoch_id, epoch_model
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT ranking_epoch_id, personalization_state, account_holder_id, ranked_candidate_count
        INTO request_epoch, request_state, request_holder, request_ranked
        FROM discovery_search_requests WHERE id = NEW.search_request_id;
    IF request_epoch <> NEW.ranking_epoch_id THEN
        RAISE EXCEPTION
            'exposure names epoch % but its search request was served in epoch %',
            NEW.ranking_epoch_id, request_epoch
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.personalized_contribution IS NOT NULL AND request_state <> 'PERSONALIZED' THEN
        RAISE EXCEPTION
            'search request % was served as % and cannot carry a personalized contribution',
            NEW.search_request_id, request_state
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.guest_preference_profile_id IS NOT NULL THEN
        SELECT account_holder_id INTO profile_holder
            FROM guest_preference_profiles WHERE id = NEW.guest_preference_profile_id;
        IF profile_holder IS DISTINCT FROM request_holder THEN
            RAISE EXCEPTION
                'preference profile % belongs to another account holder than the one who searched',
                NEW.guest_preference_profile_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF request_ranked > 0 AND NEW.position > request_ranked THEN
        RAISE EXCEPTION
            'exposure claims position % but only % candidates were ranked for that search',
            NEW.position, request_ranked
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ranking_exposures_integrity
    BEFORE INSERT OR UPDATE ON ranking_exposures
    FOR EACH ROW EXECUTE FUNCTION ranking_exposure_integrity();
--rollback DROP TRIGGER trg_ranking_exposures_integrity ON ranking_exposures;
--rollback DROP FUNCTION ranking_exposure_integrity();

--changeset ninggiangboy:029-25-exploration-budget splitStatements:false
-- Exploration draws down a budget, and the draw-down happens in the same transaction as the exposure
-- it pays for. A window that is closed, suspended, exhausted, or outside its own dates cannot fund
-- anything, and a listing that has already used its per-listing cap stops there -- otherwise a single
-- uncertain listing absorbs the whole exploration pool and the pool stops being exploration.
CREATE FUNCTION exploration_budget_claim() RETURNS TRIGGER AS $$
DECLARE
    window_state TEXT;
    window_opens TIMESTAMPTZ;
    window_closes TIMESTAMPTZ;
    window_limit BIGINT;
    window_consumed BIGINT;
    listing_cap INTEGER;
    listing_used BIGINT;
BEGIN
    SELECT state, opens_at, closes_at, impression_limit, impressions_consumed,
           per_listing_impression_cap
        INTO window_state, window_opens, window_closes, window_limit, window_consumed, listing_cap
        FROM exploration_budget_windows
        WHERE id = NEW.exploration_budget_window_id
        FOR UPDATE;
    IF window_state <> 'OPEN' THEN
        RAISE EXCEPTION 'exploration budget window % is % and cannot fund an exposure',
            NEW.exploration_budget_window_id, window_state
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.occurred_at < window_opens OR NEW.occurred_at >= window_closes THEN
        RAISE EXCEPTION
            'exposure at % falls outside exploration window % which runs from % to %',
            NEW.occurred_at, NEW.exploration_budget_window_id, window_opens, window_closes
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF window_consumed >= window_limit THEN
        RAISE EXCEPTION
            'exploration budget window % has spent its allocation of % impressions',
            NEW.exploration_budget_window_id, window_limit
            USING ERRCODE = 'restrict_violation';
    END IF;
    SELECT count(*) INTO listing_used FROM ranking_exposures
        WHERE exploration_budget_window_id = NEW.exploration_budget_window_id
            AND listing_id = NEW.listing_id;
    IF listing_used >= listing_cap THEN
        RAISE EXCEPTION
            'listing % has used its exploration cap of % impressions in window %',
            NEW.listing_id, listing_cap, NEW.exploration_budget_window_id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ranking_exposures_exploration_claim
    BEFORE INSERT ON ranking_exposures
    FOR EACH ROW WHEN (NEW.exploration_budget_window_id IS NOT NULL)
    EXECUTE FUNCTION exploration_budget_claim();

CREATE FUNCTION exploration_budget_consume() RETURNS TRIGGER AS $$
BEGIN
    UPDATE exploration_budget_windows
        SET impressions_consumed = impressions_consumed + 1,
            state = CASE WHEN impressions_consumed + 1 >= impression_limit THEN 'EXHAUSTED'
                         ELSE state END,
            updated_at = NEW.created_at
        WHERE id = NEW.exploration_budget_window_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ranking_exposures_exploration_consume
    AFTER INSERT ON ranking_exposures
    FOR EACH ROW WHEN (NEW.exploration_budget_window_id IS NOT NULL)
    EXECUTE FUNCTION exploration_budget_consume();
--rollback DROP TRIGGER trg_ranking_exposures_exploration_consume ON ranking_exposures;
--rollback DROP FUNCTION exploration_budget_consume();
--rollback DROP TRIGGER trg_ranking_exposures_exploration_claim ON ranking_exposures;
--rollback DROP FUNCTION exploration_budget_claim();

--changeset ninggiangboy:029-26-reason-truthfulness splitStatements:false
-- An explanation is a claim made to a guest about a listing, so it has to clear the threshold that
-- was approved for it. A reason code that is not active cannot be shown, a personalized reason
-- cannot appear beside a result that was not personalized, a reason that requires evidence must
-- carry enough of it and name the feature it came from, and a sponsored label belongs only on a
-- sponsored placement. Where a reason cannot meet its own bar, the reason is omitted; the listing
-- still appears, which is the failure behaviour the feature document requires.
CREATE FUNCTION ranking_exposure_reason_supported() RETURNS TRIGGER AS $$
DECLARE
    code_status TEXT;
    code_class TEXT;
    code_personalized BOOLEAN;
    code_requires_evidence BOOLEAN;
    code_feature TEXT;
    code_min_confidence NUMERIC;
    code_min_evidence NUMERIC;
    exposure_personalized UUID;
    exposure_kind TEXT;
BEGIN
    SELECT status, reason_class, personalized, requires_evidence, supporting_feature_key,
           minimum_confidence, minimum_effective_evidence
        INTO code_status, code_class, code_personalized, code_requires_evidence, code_feature,
             code_min_confidence, code_min_evidence
        FROM recommendation_reason_codes WHERE id = NEW.recommendation_reason_code_id;
    IF code_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'reason code % is % and may not be shown to a guest',
            NEW.recommendation_reason_code_id, code_status
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT guest_preference_profile_id, exposure_reason INTO exposure_personalized, exposure_kind
        FROM ranking_exposures WHERE id = NEW.ranking_exposure_id;
    IF code_personalized AND exposure_personalized IS NULL THEN
        RAISE EXCEPTION
            'reason code % speaks about this guest, but exposure % was not personalized',
            NEW.recommendation_reason_code_id, NEW.ranking_exposure_id
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF code_class = 'SPONSORED' AND exposure_kind <> 'SPONSORED' THEN
        RAISE EXCEPTION
            'exposure % is % placement and may not carry a sponsorship disclosure',
            NEW.ranking_exposure_id, exposure_kind
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF code_requires_evidence THEN
        IF NEW.supporting_confidence IS NULL OR NEW.supporting_evidence IS NULL THEN
            RAISE EXCEPTION
                'reason code % requires evidence; record the confidence and evidence behind the claim',
                NEW.recommendation_reason_code_id
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.supporting_confidence < code_min_confidence
                OR NEW.supporting_evidence < code_min_evidence THEN
            RAISE EXCEPTION
                'reason code % needs confidence % and evidence %, but this claim has % and %',
                NEW.recommendation_reason_code_id, code_min_confidence, code_min_evidence,
                NEW.supporting_confidence, NEW.supporting_evidence
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.supporting_feature_key IS DISTINCT FROM code_feature THEN
            RAISE EXCEPTION
                'reason code % is supported by feature %, not %',
                NEW.recommendation_reason_code_id, code_feature, NEW.supporting_feature_key
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ranking_exposure_reasons_supported
    BEFORE INSERT OR UPDATE ON ranking_exposure_reasons
    FOR EACH ROW EXECUTE FUNCTION ranking_exposure_reason_supported();
--rollback DROP TRIGGER trg_ranking_exposure_reasons_supported ON ranking_exposure_reasons;
--rollback DROP FUNCTION ranking_exposure_reason_supported();

--changeset ninggiangboy:029-27-discovery-registry-immutability splitStatements:false
-- A policy version, a model version, and a reason code stop being editable the moment they leave
-- draft, because every exposure written under one is a claim about what it said. Lifecycle columns
-- still move -- a policy can be superseded, a model promoted or rolled back, a reason retired -- but
-- the weights, the checksum, and the wording are fixed. Changing them is a new version, which is the
-- only form of change that leaves the earlier ranks explicable.
CREATE FUNCTION discovery_registry_freeze() RETURNS TRIGGER AS $$
DECLARE
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            '% row % has left draft and cannot be deleted; results were served under it',
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
            '% row % has left draft; publish a new version rather than editing this one',
            TG_TABLE_NAME, OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ranking_policy_versions_freeze
    BEFORE UPDATE OR DELETE ON ranking_policy_versions
    FOR EACH ROW WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION discovery_registry_freeze(
        'status', 'effective_until', 'updated_at', 'version');

CREATE TRIGGER trg_ranking_model_versions_freeze
    BEFORE UPDATE OR DELETE ON ranking_model_versions
    FOR EACH ROW WHEN (OLD.status <> 'REGISTERED')
    EXECUTE FUNCTION discovery_registry_freeze(
        'status', 'traffic_share', 'promoted_at', 'rolled_back_at', 'rollback_reason',
        'updated_at', 'version');

CREATE TRIGGER trg_recommendation_reason_codes_freeze
    BEFORE UPDATE OR DELETE ON recommendation_reason_codes
    FOR EACH ROW WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION discovery_registry_freeze(
        'status', 'retired_at', 'updated_at', 'version');
--rollback DROP TRIGGER trg_recommendation_reason_codes_freeze ON recommendation_reason_codes;
--rollback DROP TRIGGER trg_ranking_model_versions_freeze ON ranking_model_versions;
--rollback DROP TRIGGER trg_ranking_policy_versions_freeze ON ranking_policy_versions;
--rollback DROP FUNCTION discovery_registry_freeze();

--changeset ninggiangboy:029-28-registry-terminal-states splitStatements:false
-- Probing found the hole this closes. A model rolled back for a latency regression could be set
-- straight back to ACTIVE, keeping the rollback instant and the rollback reason sitting beside it:
-- a row simultaneously claiming to be serving and to have been pulled from service, with no new
-- approval anywhere. Recording that something was rolled back is not a guard unless the row is held
-- to it. So a rollback is final and cannot be erased, and a superseded policy version cannot be
-- revived -- in both cases the way back is to publish a new version, which is the only form of
-- reversal that leaves the earlier ranks explicable and the incident visible.
CREATE FUNCTION discovery_registry_terminal() RETURNS TRIGGER AS $$
BEGIN
    IF TG_TABLE_NAME = 'ranking_model_versions' THEN
        IF OLD.rolled_back_at IS NOT NULL
                AND (NEW.rolled_back_at IS DISTINCT FROM OLD.rolled_back_at
                     OR NEW.rollback_reason IS DISTINCT FROM OLD.rollback_reason) THEN
            RAISE EXCEPTION
                'model % was rolled back at %; that is a fact about what happened and cannot be erased',
                OLD.id, OLD.rolled_back_at
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF OLD.status = 'ROLLED_BACK' AND NEW.status NOT IN ('ROLLED_BACK', 'RETIRED') THEN
            RAISE EXCEPTION
                'model % was rolled back (%); promote a new version rather than returning this one to service',
                OLD.id, OLD.rollback_reason
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        IF OLD.status = 'SUPERSEDED' AND NEW.status NOT IN ('SUPERSEDED', 'RETIRED') THEN
            RAISE EXCEPTION
                'policy version % was superseded; publish a new version rather than reviving this one',
                OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ranking_model_versions_terminal
    BEFORE UPDATE ON ranking_model_versions
    FOR EACH ROW EXECUTE FUNCTION discovery_registry_terminal();

CREATE TRIGGER trg_ranking_policy_versions_terminal
    BEFORE UPDATE ON ranking_policy_versions
    FOR EACH ROW EXECUTE FUNCTION discovery_registry_terminal();
--rollback DROP TRIGGER trg_ranking_policy_versions_terminal ON ranking_policy_versions;
--rollback DROP TRIGGER trg_ranking_model_versions_terminal ON ranking_model_versions;
--rollback DROP FUNCTION discovery_registry_terminal();
