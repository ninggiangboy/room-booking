--liquibase formatted sql

-- Advice that does not say what it is based on, how sure it is, and who pays for it is not advice;
-- it is pressure with a number on it. That is the whole of this migration. Everything the platform
-- tells a host about their own business -- how they are performing, how that compares to the
-- market, what demand is coming, what a promotion would cost, what is wrong with a listing, what a
-- payout will be -- becomes a row that carries its evidence, its uncertainty, its expected impact
-- and its economic effect, and a second row recording what the host decided to do about it. The
-- failure the domain document names is a short one: recommendations that are opaque or coercive.
-- Opacity and coercion are the same defect seen from two sides. A host who cannot see why a number
-- was produced cannot argue with it, and a host who cannot argue with it has not been advised.
--
-- The platform does not get to invent facts about a host's business. Nothing here computes a
-- booking, a price, a payout or a review score. A performance metric cites migration 030's metric
-- definition that produced it and the pipeline watermark it was cut at; a payout preview cites the
-- obligations and allocations it summed and is explicitly non-binding; a quality checklist item
-- cites the listing content or review aspect that raised it. There is deliberately no column in
-- this migration into which the platform can type an earnings figure that no ledger row supports.
--
-- Six forces shape it:
--
--   A number shown to a host is a published metric, not a query. Which metrics a host may see, in
--   what words, and how many observations are needed before the number means anything, is a
--   registry row frozen once it leaves DRAFT. Two screens computing "occupancy" two different ways
--   is not a display inconsistency; it is two different claims about the same business, and the
--   host has no way to tell which one their decision was based on.
--
--   A rate is published with its denominator or not at all. Response rate, acceptance rate and
--   cancellation rate are stored beside the counts that produced them, and a metric whose
--   observation count falls under its own published minimum is recorded as insufficient rather
--   than rounded into a number. Ninety per cent of ten is a different fact from ninety per cent of
--   a thousand, and a host penalised by the first deserves to see which it was.
--
--   Market intelligence is aggregate or it is a leak. A benchmark names the cohort it was drawn
--   from, and the cohort names the minimum number of distinct contributors it will publish under.
--   Below that floor the row exists and carries a suppression reason instead of a value, because a
--   silently missing row is indistinguishable from a pipeline failure. Extremes are never
--   published: a maximum is one host's number wearing a cohort's name.
--
--   A forecast carries an interval or it is a guess with a decimal point. Every forecast row names
--   its run, its model version and its horizon, and its interval must contain its own point
--   estimate. The domain document forbids fabricated scarcity and guaranteed earnings, and an
--   interval is what makes the difference visible: a wide one says the platform does not know.
--
--   Advice is disclosed before it is decided, and both halves are kept. A disclosure records the
--   four facts the domain document requires -- evidence, uncertainty, expected impact, economic
--   effect -- as four columns that cannot be null, plus a digest of what was actually rendered. A
--   decision must cite a disclosure about the same piece of advice that preceded it, so "the host
--   accepted it" can never be recorded for advice the host was never shown the basis of. Ignoring
--   advice is a recordable outcome, because a system that only stores acceptances cannot tell a
--   good recommendation from one nobody dared refuse.
--
--   A bulk edit reports what it did to every target, not that it succeeded. One request against
--   four hundred nights is four hundred outcomes, each of which may be refused by the domain that
--   owns it -- a night under an active claim, a price under a floor, a restriction the market
--   forbids. A request that says APPLIED while sixty nights silently did not change is how a host
--   discovers in a support case that their calendar was never what they saw.
--
-- Note on what this migration does not create. Host pricing bounds, automation opt-in and per-date
-- price recommendations are migration 019's host_pricing_settings and price_recommendations, cited
-- here rather than rebuilt -- 019 already carries the floor, ceiling, desired net and the OFF /
-- SUGGEST / APPLY consent that the domain document calls automatic-pricing opt-in. Listing quality
-- scores and review-aspect trends are migration 026's listing_quality_profiles and aspect profiles;
-- this migration adds the actionable checklist over them, not a second score. Co-host assignment is
-- migration 016's property_collaborators, and external PMS and channel-manager connections are
-- migration 018's ical_connections. Host statements are migration 022; what this adds is a
-- forward-looking, explicitly non-binding preview. The metric registry is migration 030's
-- metric_definitions, which already carries the HOST_ECONOMICS family, the window, the time zone
-- and the restatement policy; this migration publishes from it rather than defining a parallel
-- vocabulary that would drift. Model versions and predictions are migration 031. The trigger
-- functions platform_append_only() and platform_contract_freeze() come from 030 and are reused.
--
-- Every table here is written by the application, so none of them carry DEFAULT now(): see
-- migration 011.

--changeset ninggiangboy:033-01-host-metric-publications
CREATE TABLE host_metric_publications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_definition_id        UUID NOT NULL,
    surface                     VARCHAR(24) NOT NULL,
    host_label                  VARCHAR(120) NOT NULL,
    plain_explanation           VARCHAR(1000) NOT NULL,
    interpretation_guidance     VARCHAR(1000) NOT NULL,
    improvement_guidance        VARCHAR(1000),
    insufficient_evidence_text  VARCHAR(500) NOT NULL,
    minimum_observations        INTEGER NOT NULL,
    subject_kinds               VARCHAR(24)[] NOT NULL,
    benchmarkable               BOOLEAN NOT NULL DEFAULT false,
    affects_ranking             BOOLEAN NOT NULL DEFAULT false,
    affects_standing            BOOLEAN NOT NULL DEFAULT false,
    consequence_explanation     VARCHAR(1000),
    display_precision           SMALLINT NOT NULL DEFAULT 0,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at                TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_host_metric_publications_surface UNIQUE (metric_definition_id, surface),
    CONSTRAINT fk_host_metric_publications_metric FOREIGN KEY (metric_definition_id)
        REFERENCES metric_definitions (id),
    CONSTRAINT ck_host_metric_publications_surface CHECK (
        surface IN ('HOST_DASHBOARD', 'HOST_CALENDAR', 'HOST_INSIGHTS', 'HOST_STATEMENT',
                    'HOST_API')
    ),
    CONSTRAINT ck_host_metric_publications_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    -- A number computed from four observations is not a rate, and showing it as one invites a host
    -- to change their business over noise. Below the floor the sentence is shown instead of the
    -- number, which is why the sentence is as mandatory as the floor.
    CONSTRAINT ck_host_metric_publications_minimum CHECK (minimum_observations > 0),
    CONSTRAINT ck_host_metric_publications_subjects CHECK (
        array_length(subject_kinds, 1) IS NOT NULL
    ),
    CONSTRAINT ck_host_metric_publications_precision CHECK (
        display_precision >= 0 AND display_precision <= 6
    ),
    -- A metric that moves a host's ranking or their standing on the platform is not informational,
    -- and the host is owed a statement of what it does to them in the same row that publishes it.
    CONSTRAINT ck_host_metric_publications_consequence CHECK (
        (affects_ranking OR affects_standing) = (consequence_explanation IS NOT NULL)
    ),
    CONSTRAINT ck_host_metric_publications_published CHECK (
        status = 'DRAFT' OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_host_metric_publications_retired CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_host_metric_publications_version CHECK (version >= 0)
);

CREATE INDEX idx_host_metric_publications_active ON host_metric_publications (surface)
    WHERE status = 'ACTIVE';
--rollback DROP TABLE host_metric_publications;

--changeset ninggiangboy:033-02-host-performance-metrics
CREATE TABLE host_performance_metrics (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    publication_id              UUID NOT NULL,
    subject_kind                VARCHAR(24) NOT NULL,
    subject_id                  UUID NOT NULL,
    market_code                 VARCHAR(2),
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    period_time_zone            VARCHAR(64) NOT NULL,
    evidence_state              VARCHAR(16) NOT NULL DEFAULT 'SUFFICIENT',
    measured_value              NUMERIC(24,8),
    value_minor                 BIGINT,
    currency                    VARCHAR(3),
    numerator_count             BIGINT,
    denominator_count           BIGINT,
    observation_count           BIGINT NOT NULL,
    comparison_value            NUMERIC(24,8),
    comparison_period_start     DATE,
    comparison_period_end       DATE,
    metric_materialization_id   UUID,
    input_watermark             TIMESTAMPTZ NOT NULL,
    computed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_host_performance_metrics_period
        UNIQUE (publication_id, subject_kind, subject_id, period_start, period_end, computed_at),
    CONSTRAINT fk_host_performance_metrics_publication FOREIGN KEY (publication_id)
        REFERENCES host_metric_publications (id),
    CONSTRAINT fk_host_performance_metrics_materialization FOREIGN KEY (metric_materialization_id)
        REFERENCES metric_materializations (id),
    CONSTRAINT ck_host_performance_metrics_subject CHECK (
        subject_kind IN ('LISTING', 'ACCOMMODATION_TYPE', 'PROPERTY', 'HOST')
    ),
    CONSTRAINT ck_host_performance_metrics_market CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_host_performance_metrics_period CHECK (period_end > period_start),
    CONSTRAINT ck_host_performance_metrics_zone CHECK (
        period_time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$' OR period_time_zone = 'UTC'
    ),
    CONSTRAINT ck_host_performance_metrics_evidence_state CHECK (
        evidence_state IN ('SUFFICIENT', 'INSUFFICIENT', 'SUPPRESSED', 'UNAVAILABLE')
    ),
    -- The number and the admission that there is no number are mutually exclusive. Storing both a
    -- value and an insufficient-evidence state leaves it to whichever screen reads the row first to
    -- decide which of the two the host is shown.
    CONSTRAINT ck_host_performance_metrics_value CHECK (
        (evidence_state = 'SUFFICIENT') = (measured_value IS NOT NULL)
    ),
    CONSTRAINT ck_host_performance_metrics_money CHECK (
        (value_minor IS NULL) = (currency IS NULL)
    ),
    CONSTRAINT ck_host_performance_metrics_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    -- A rate carries the two counts that produced it. Without them a host cannot tell a real
    -- decline from four cancellations in a quiet month, and neither can the support agent reading
    -- the same screen back to them.
    CONSTRAINT ck_host_performance_metrics_counts CHECK (
        (numerator_count IS NULL OR numerator_count >= 0)
            AND (denominator_count IS NULL OR denominator_count > 0)
            AND observation_count >= 0
            AND (numerator_count IS NULL OR denominator_count IS NULL
                 OR numerator_count <= denominator_count)
    ),
    CONSTRAINT ck_host_performance_metrics_comparison CHECK (
        (comparison_period_start IS NULL) = (comparison_period_end IS NULL)
            AND (comparison_value IS NULL OR comparison_period_start IS NOT NULL)
            AND (comparison_period_end IS NULL OR comparison_period_end > comparison_period_start)
    )
);

CREATE INDEX idx_host_performance_metrics_subject
    ON host_performance_metrics (subject_kind, subject_id, period_start DESC);
--rollback DROP TABLE host_performance_metrics;

--changeset ninggiangboy:033-03-host-response-metrics
CREATE TABLE host_response_metrics (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_account_holder_id      UUID NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    period_time_zone            VARCHAR(64) NOT NULL,
    evidence_state              VARCHAR(16) NOT NULL DEFAULT 'SUFFICIENT',

    inquiries_received          BIGINT NOT NULL DEFAULT 0,
    inquiries_responded         BIGINT NOT NULL DEFAULT 0,
    response_rate               NUMERIC(5,4),
    median_response_seconds     INTEGER,
    slowest_decile_seconds      INTEGER,

    requests_received           BIGINT NOT NULL DEFAULT 0,
    requests_accepted           BIGINT NOT NULL DEFAULT 0,
    requests_declined           BIGINT NOT NULL DEFAULT 0,
    requests_expired            BIGINT NOT NULL DEFAULT 0,
    acceptance_rate             NUMERIC(5,4),

    bookings_confirmed          BIGINT NOT NULL DEFAULT 0,
    host_cancellations          BIGINT NOT NULL DEFAULT 0,
    host_cancellation_rate      NUMERIC(5,4),
    excused_cancellations       BIGINT NOT NULL DEFAULT 0,

    policy_breaches_recorded    BIGINT NOT NULL DEFAULT 0,
    compliance_state            VARCHAR(24) NOT NULL DEFAULT 'IN_GOOD_STANDING',
    compliance_explanation      VARCHAR(1000),

    input_watermark             TIMESTAMPTZ NOT NULL,
    computed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_host_response_metrics_period
        UNIQUE (host_account_holder_id, period_start, period_end, computed_at),
    CONSTRAINT fk_host_response_metrics_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_host_response_metrics_period CHECK (period_end > period_start),
    CONSTRAINT ck_host_response_metrics_zone CHECK (
        period_time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$' OR period_time_zone = 'UTC'
    ),
    CONSTRAINT ck_host_response_metrics_evidence_state CHECK (
        evidence_state IN ('SUFFICIENT', 'INSUFFICIENT', 'SUPPRESSED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_host_response_metrics_counts CHECK (
        inquiries_received >= 0 AND inquiries_responded >= 0
            AND requests_received >= 0 AND requests_accepted >= 0 AND requests_declined >= 0
            AND requests_expired >= 0 AND bookings_confirmed >= 0 AND host_cancellations >= 0
            AND excused_cancellations >= 0 AND policy_breaches_recorded >= 0
    ),
    -- A host cannot have responded to more enquiries than arrived, and the three outcomes of a
    -- booking request cannot outnumber the requests. These are the arithmetic a support agent
    -- would do by hand when a host disputes their own numbers, done once here instead.
    CONSTRAINT ck_host_response_metrics_totals CHECK (
        inquiries_responded <= inquiries_received
            AND requests_accepted + requests_declined + requests_expired <= requests_received
            AND host_cancellations <= bookings_confirmed
            AND excused_cancellations <= host_cancellations
    ),
    -- A rate exists exactly when its denominator does. A stored rate that disagrees with the counts
    -- beside it is worse than no rate at all: whichever of the two the host is shown, the other is
    -- the one the platform acted on.
    CONSTRAINT ck_host_response_metrics_response_rate CHECK (
        (response_rate IS NULL) = (inquiries_received = 0)
            AND (response_rate IS NULL
                 OR abs(response_rate - (inquiries_responded::NUMERIC / inquiries_received))
                    <= 0.0001)
    ),
    CONSTRAINT ck_host_response_metrics_acceptance_rate CHECK (
        (acceptance_rate IS NULL) = (requests_received = 0)
            AND (acceptance_rate IS NULL
                 OR abs(acceptance_rate - (requests_accepted::NUMERIC / requests_received))
                    <= 0.0001)
    ),
    CONSTRAINT ck_host_response_metrics_cancellation_rate CHECK (
        (host_cancellation_rate IS NULL) = (bookings_confirmed = 0)
            AND (host_cancellation_rate IS NULL
                 OR abs(host_cancellation_rate
                        - (host_cancellations::NUMERIC / bookings_confirmed)) <= 0.0001)
    ),
    CONSTRAINT ck_host_response_metrics_durations CHECK (
        (median_response_seconds IS NULL OR median_response_seconds >= 0)
            AND (slowest_decile_seconds IS NULL OR slowest_decile_seconds >= 0)
            AND (median_response_seconds IS NULL OR slowest_decile_seconds IS NULL
                 OR slowest_decile_seconds >= median_response_seconds)
    ),
    CONSTRAINT ck_host_response_metrics_compliance CHECK (
        compliance_state IN ('IN_GOOD_STANDING', 'WATCH', 'WARNED', 'RESTRICTED', 'UNDER_REVIEW')
    ),
    -- Anything other than good standing is a consequence, and a consequence a host cannot read the
    -- reason for is one they cannot correct or appeal.
    CONSTRAINT ck_host_response_metrics_explanation CHECK (
        (compliance_state = 'IN_GOOD_STANDING') = (compliance_explanation IS NULL)
    )
);

CREATE INDEX idx_host_response_metrics_host
    ON host_response_metrics (host_account_holder_id, period_start DESC);
--rollback DROP TABLE host_response_metrics;

--changeset ninggiangboy:033-04-benchmark-cohort-definitions
CREATE TABLE benchmark_cohort_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_key                  VARCHAR(64) NOT NULL,
    cohort_version              INTEGER NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,
    display_name                VARCHAR(160) NOT NULL,
    selection_description       VARCHAR(1000) NOT NULL,
    geo_area_id                 UUID,
    property_type               VARCHAR(24),
    capacity_band_low           SMALLINT,
    capacity_band_high          SMALLINT,
    price_band_low_minor        BIGINT,
    price_band_high_minor       BIGINT,
    price_band_currency         VARCHAR(3),
    minimum_contributors        INTEGER NOT NULL,
    minimum_observations        INTEGER NOT NULL,
    maximum_contributor_share   NUMERIC(5,4) NOT NULL DEFAULT 0.2000,
    suppression_rule            VARCHAR(24) NOT NULL DEFAULT 'SUPPRESS_VALUE',
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at                TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_benchmark_cohort_definitions_version UNIQUE (cohort_key, cohort_version),
    CONSTRAINT fk_benchmark_cohort_definitions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_benchmark_cohort_definitions_area FOREIGN KEY (geo_area_id)
        REFERENCES geo_areas (id),
    CONSTRAINT ck_benchmark_cohort_definitions_key CHECK (cohort_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_benchmark_cohort_definitions_cohort_version CHECK (cohort_version > 0),
    CONSTRAINT ck_benchmark_cohort_definitions_market_code CHECK (market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_benchmark_cohort_definitions_property_type CHECK (
        property_type IS NULL
            OR property_type IN ('APARTMENT', 'HOUSE', 'VILLA', 'HOTEL', 'HOSTEL', 'GUESTHOUSE',
                                 'RESORT', 'HOMESTAY', 'BOUTIQUE_HOTEL', 'SERVICED_APARTMENT',
                                 'OTHER')
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_capacity CHECK (
        (capacity_band_low IS NULL OR capacity_band_low > 0)
            AND (capacity_band_high IS NULL OR capacity_band_high > 0)
            AND (capacity_band_low IS NULL OR capacity_band_high IS NULL
                 OR capacity_band_high >= capacity_band_low)
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_price_band CHECK (
        (price_band_low_minor IS NULL) = (price_band_currency IS NULL)
            AND (price_band_high_minor IS NULL OR price_band_currency IS NOT NULL)
            AND (price_band_low_minor IS NULL OR price_band_low_minor >= 0)
            AND (price_band_low_minor IS NULL OR price_band_high_minor IS NULL
                 OR price_band_high_minor > price_band_low_minor)
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_currency CHECK (
        price_band_currency IS NULL OR price_band_currency ~ '^[A-Z]{3}$'
    ),
    -- The privacy floor has a floor. A cohort of three is three hosts who can each subtract
    -- themselves from the published median and read the other two, which is the leak the domain
    -- document forbids stated as arithmetic rather than as an intention. The share ceiling catches
    -- the second shape of the same leak: a cohort of twenty in which one host owns half the nights
    -- is that host's number with nineteen others rounding it.
    CONSTRAINT ck_benchmark_cohort_definitions_floor CHECK (minimum_contributors >= 5),
    CONSTRAINT ck_benchmark_cohort_definitions_observations CHECK (
        minimum_observations >= minimum_contributors
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_share CHECK (
        maximum_contributor_share > 0 AND maximum_contributor_share <= 0.5
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_suppression CHECK (
        suppression_rule IN ('SUPPRESS_VALUE', 'SUPPRESS_ROW', 'WIDEN_COHORT')
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_published CHECK (
        status = 'DRAFT' OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_retired CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_benchmark_cohort_definitions_version CHECK (version >= 0)
);
--rollback DROP TABLE benchmark_cohort_definitions;

--changeset ninggiangboy:033-05-market-benchmark-aggregates
-- There are deliberately no minimum or maximum columns on this table. An extreme is one host's
-- number wearing the cohort's name, and no aggregation threshold protects it: the host who owns the
-- maximum recognises it immediately, and so does the competitor who was standing next to them.
-- Quartiles and a trimmed mean are what a cohort can say without saying who.
CREATE TABLE market_benchmark_aggregates (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cohort_definition_id        UUID NOT NULL,
    publication_id              UUID NOT NULL,
    period_start                DATE NOT NULL,
    period_end                  DATE NOT NULL,
    period_time_zone            VARCHAR(64) NOT NULL,
    publication_state           VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    suppression_reason          VARCHAR(32),
    contributor_count           INTEGER NOT NULL,
    observation_count           BIGINT NOT NULL,
    largest_contributor_share   NUMERIC(5,4),
    lower_quartile              NUMERIC(24,8),
    median_value                NUMERIC(24,8),
    upper_quartile              NUMERIC(24,8),
    trimmed_mean                NUMERIC(24,8),
    currency                    VARCHAR(3),
    input_watermark             TIMESTAMPTZ NOT NULL,
    computed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_market_benchmark_aggregates_period
        UNIQUE (cohort_definition_id, publication_id, period_start, period_end, computed_at),
    CONSTRAINT fk_market_benchmark_aggregates_cohort FOREIGN KEY (cohort_definition_id)
        REFERENCES benchmark_cohort_definitions (id),
    CONSTRAINT fk_market_benchmark_aggregates_publication FOREIGN KEY (publication_id)
        REFERENCES host_metric_publications (id),
    CONSTRAINT ck_market_benchmark_aggregates_period CHECK (period_end > period_start),
    CONSTRAINT ck_market_benchmark_aggregates_zone CHECK (
        period_time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$' OR period_time_zone = 'UTC'
    ),
    CONSTRAINT ck_market_benchmark_aggregates_state CHECK (
        publication_state IN ('PUBLISHED', 'SUPPRESSED')
    ),
    -- A suppressed benchmark is a row, not a missing row. Absence is indistinguishable from a
    -- pipeline that failed, and a host told nothing learns the wrong thing from the silence.
    CONSTRAINT ck_market_benchmark_aggregates_suppression CHECK (
        (publication_state = 'SUPPRESSED') = (suppression_reason IS NOT NULL)
    ),
    CONSTRAINT ck_market_benchmark_aggregates_reason CHECK (
        suppression_reason IS NULL
            OR suppression_reason IN ('TOO_FEW_CONTRIBUTORS', 'TOO_FEW_OBSERVATIONS',
                                      'CONTRIBUTOR_CONCENTRATION', 'QUALITY_GATE_FAILED',
                                      'COHORT_RETIRED')
    ),
    CONSTRAINT ck_market_benchmark_aggregates_values CHECK (
        (publication_state = 'PUBLISHED') = (median_value IS NOT NULL)
    ),
    CONSTRAINT ck_market_benchmark_aggregates_suppressed_blank CHECK (
        publication_state = 'PUBLISHED'
            OR (lower_quartile IS NULL AND median_value IS NULL AND upper_quartile IS NULL
                AND trimmed_mean IS NULL)
    ),
    CONSTRAINT ck_market_benchmark_aggregates_order CHECK (
        lower_quartile IS NULL OR median_value IS NULL OR upper_quartile IS NULL
            OR (lower_quartile <= median_value AND median_value <= upper_quartile)
    ),
    CONSTRAINT ck_market_benchmark_aggregates_counts CHECK (
        contributor_count >= 0 AND observation_count >= 0
    ),
    CONSTRAINT ck_market_benchmark_aggregates_share CHECK (
        largest_contributor_share IS NULL
            OR (largest_contributor_share > 0 AND largest_contributor_share <= 1)
    ),
    CONSTRAINT ck_market_benchmark_aggregates_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    )
);

CREATE INDEX idx_market_benchmark_aggregates_lookup
    ON market_benchmark_aggregates (publication_id, cohort_definition_id, period_start DESC);
--rollback DROP TABLE market_benchmark_aggregates;

--changeset ninggiangboy:033-06-demand-forecast-runs
CREATE TABLE demand_forecast_runs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_code                 VARCHAR(2) NOT NULL,
    geo_area_id                 UUID,
    forecast_basis              VARCHAR(24) NOT NULL,
    model_version_id            UUID,
    horizon_days                INTEGER NOT NULL,
    covers_from                 DATE NOT NULL,
    covers_until                DATE NOT NULL,
    feature_watermark           TIMESTAMPTZ NOT NULL,
    run_state                   VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    forecast_count              INTEGER NOT NULL DEFAULT 0,
    backtest_reference          VARCHAR(200),
    backtest_error_percent      NUMERIC(6,3),
    failure_reason              VARCHAR(500),
    superseded_by_run_id        UUID,
    generated_at                TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_demand_forecast_runs_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_demand_forecast_runs_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id),
    CONSTRAINT fk_demand_forecast_runs_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_demand_forecast_runs_supersession FOREIGN KEY (superseded_by_run_id)
        REFERENCES demand_forecast_runs (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_demand_forecast_runs_market_code CHECK (market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_demand_forecast_runs_basis CHECK (
        forecast_basis IN ('SEARCH_DEMAND', 'BOOKING_PACE', 'BLENDED', 'SEASONAL_NAIVE')
    ),
    CONSTRAINT ck_demand_forecast_runs_state CHECK (
        run_state IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SUPERSEDED')
    ),
    -- A forecast further out than the model was evaluated over is extrapolation presented as
    -- prediction. Two years is the outer bound anything here may claim to see.
    CONSTRAINT ck_demand_forecast_runs_horizon CHECK (horizon_days > 0 AND horizon_days <= 730),
    CONSTRAINT ck_demand_forecast_runs_coverage CHECK (
        covers_until > covers_from
            AND covers_until <= covers_from + make_interval(days => horizon_days)
    ),
    CONSTRAINT ck_demand_forecast_runs_counts CHECK (forecast_count >= 0),
    CONSTRAINT ck_demand_forecast_runs_error CHECK (
        backtest_error_percent IS NULL OR backtest_error_percent >= 0
    ),
    -- Written as an equality this said that a run which is superseded must never have finished,
    -- which made a completed run impossible to supersede -- and superseding a completed run is
    -- the ordinary case, since that is what a fresh run every morning does to yesterday's. The
    -- two directions are different rules: a finished run has an instant, an unfinished one has
    -- none, and a superseded run keeps whichever it had.
    CONSTRAINT ck_demand_forecast_runs_completion CHECK (
        (run_state NOT IN ('SUCCEEDED', 'FAILED') OR completed_at IS NOT NULL)
            AND (run_state NOT IN ('PENDING', 'RUNNING') OR completed_at IS NULL)
    ),
    CONSTRAINT ck_demand_forecast_runs_failure CHECK (
        (run_state = 'FAILED') = (failure_reason IS NOT NULL)
    ),
    CONSTRAINT ck_demand_forecast_runs_superseded CHECK (
        (run_state = 'SUPERSEDED') = (superseded_by_run_id IS NOT NULL)
    ),
    CONSTRAINT ck_demand_forecast_runs_self CHECK (superseded_by_run_id <> id),
    CONSTRAINT ck_demand_forecast_runs_version CHECK (version >= 0)
);

CREATE INDEX idx_demand_forecast_runs_market
    ON demand_forecast_runs (market_code, generated_at DESC);
--rollback DROP TABLE demand_forecast_runs;

--changeset ninggiangboy:033-07-demand-forecasts
CREATE TABLE demand_forecasts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    forecast_run_id             UUID NOT NULL,
    accommodation_type_id       UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    evidence_state              VARCHAR(16) NOT NULL DEFAULT 'SUFFICIENT',
    predicted_occupancy_percent NUMERIC(5,2),
    occupancy_interval_low      NUMERIC(5,2),
    occupancy_interval_high     NUMERIC(5,2),
    predicted_bookings          NUMERIC(10,2),
    bookings_interval_low       NUMERIC(10,2),
    bookings_interval_high      NUMERIC(10,2),
    interval_confidence         NUMERIC(4,3),
    market_pace_percent         NUMERIC(7,2),
    prior_year_occupancy_percent NUMERIC(5,2),
    confidence                  VARCHAR(16) NOT NULL,
    driver_summary              VARCHAR(1000),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_demand_forecasts_date
        UNIQUE (forecast_run_id, accommodation_type_id, stay_date),
    CONSTRAINT fk_demand_forecasts_run FOREIGN KEY (forecast_run_id)
        REFERENCES demand_forecast_runs (id) ON DELETE CASCADE,
    CONSTRAINT fk_demand_forecasts_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_demand_forecasts_evidence_state CHECK (
        evidence_state IN ('SUFFICIENT', 'INSUFFICIENT', 'SUPPRESSED', 'UNAVAILABLE')
    ),
    CONSTRAINT ck_demand_forecasts_confidence CHECK (
        confidence IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT ck_demand_forecasts_value CHECK (
        (evidence_state = 'SUFFICIENT')
            = (predicted_occupancy_percent IS NOT NULL OR predicted_bookings IS NOT NULL)
    ),
    CONSTRAINT ck_demand_forecasts_percent_bounds CHECK (
        (predicted_occupancy_percent IS NULL OR predicted_occupancy_percent BETWEEN 0 AND 100)
            AND (occupancy_interval_low IS NULL OR occupancy_interval_low BETWEEN 0 AND 100)
            AND (occupancy_interval_high IS NULL OR occupancy_interval_high BETWEEN 0 AND 100)
            AND (prior_year_occupancy_percent IS NULL
                 OR prior_year_occupancy_percent BETWEEN 0 AND 100)
    ),
    -- A point estimate with no interval around it is a guess with a decimal point, and it is
    -- exactly the shape fabricated scarcity takes: "you will be eighty per cent full" reads as a
    -- fact. An interval that does not contain its own point estimate is worse, because it looks
    -- like the platform did the arithmetic.
    CONSTRAINT ck_demand_forecasts_occupancy_interval CHECK (
        predicted_occupancy_percent IS NULL
            OR (occupancy_interval_low IS NOT NULL AND occupancy_interval_high IS NOT NULL
                AND occupancy_interval_low <= predicted_occupancy_percent
                AND predicted_occupancy_percent <= occupancy_interval_high)
    ),
    CONSTRAINT ck_demand_forecasts_bookings_interval CHECK (
        predicted_bookings IS NULL
            OR (bookings_interval_low IS NOT NULL AND bookings_interval_high IS NOT NULL
                AND bookings_interval_low >= 0
                AND bookings_interval_low <= predicted_bookings
                AND predicted_bookings <= bookings_interval_high)
    ),
    CONSTRAINT ck_demand_forecasts_interval_confidence CHECK (
        (predicted_occupancy_percent IS NULL AND predicted_bookings IS NULL)
            OR (interval_confidence IS NOT NULL
                AND interval_confidence > 0.5 AND interval_confidence < 1)
    ),
    CONSTRAINT ck_demand_forecasts_pace CHECK (
        market_pace_percent IS NULL OR market_pace_percent >= -100
    )
);

CREATE INDEX idx_demand_forecasts_type_date
    ON demand_forecasts (accommodation_type_id, stay_date);
--rollback DROP TABLE demand_forecasts;

--changeset ninggiangboy:033-08-promotion-suggestions
CREATE TABLE promotion_suggestions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id       UUID NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,
    suggestion_kind             VARCHAR(24) NOT NULL,
    applies_from                DATE NOT NULL,
    applies_until               DATE NOT NULL,
    discount_percent            NUMERIC(5,2) NOT NULL,
    minimum_nights              SMALLINT,
    currency                    VARCHAR(3) NOT NULL,

    baseline_bookings           NUMERIC(10,2) NOT NULL,
    baseline_revenue_minor      BIGINT NOT NULL,
    incremental_bookings        NUMERIC(10,2) NOT NULL,
    incremental_interval_low    NUMERIC(10,2) NOT NULL,
    incremental_interval_high   NUMERIC(10,2) NOT NULL,
    incremental_revenue_minor   BIGINT NOT NULL,
    host_cost_minor             BIGINT NOT NULL,
    funding_split               VARCHAR(24) NOT NULL,
    host_funded_share_percent   NUMERIC(5,2),

    forecast_run_id             UUID,
    model_version_id            UUID,
    evidence_basis              VARCHAR(24) NOT NULL,
    decision_state              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    accepted_promotion_id       UUID,
    generated_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_promotion_suggestions_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_promotion_suggestions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_promotion_suggestions_run FOREIGN KEY (forecast_run_id)
        REFERENCES demand_forecast_runs (id),
    CONSTRAINT fk_promotion_suggestions_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT fk_promotion_suggestions_promotion FOREIGN KEY (accepted_promotion_id)
        REFERENCES promotions (id),
    CONSTRAINT ck_promotion_suggestions_market_code CHECK (market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_promotion_suggestions_kind CHECK (
        suggestion_kind IN ('EARLY_BIRD', 'LAST_MINUTE', 'LENGTH_OF_STAY', 'WEEKLY', 'MONTHLY',
                            'ORPHAN_NIGHT', 'SEASONAL', 'NEW_LISTING')
    ),
    CONSTRAINT ck_promotion_suggestions_window CHECK (applies_until > applies_from),
    CONSTRAINT ck_promotion_suggestions_discount CHECK (
        discount_percent > 0 AND discount_percent <= 60
    ),
    CONSTRAINT ck_promotion_suggestions_nights CHECK (
        minimum_nights IS NULL OR minimum_nights > 0
    ),
    CONSTRAINT ck_promotion_suggestions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- An estimate of extra bookings means nothing without the number it is extra to. A suggestion
    -- that promises twelve bookings where the baseline was already eleven is a discount on demand
    -- the host had anyway, and the only way a host can see that is if the baseline is on the row.
    CONSTRAINT ck_promotion_suggestions_baseline CHECK (
        baseline_bookings >= 0 AND baseline_revenue_minor >= 0
    ),
    CONSTRAINT ck_promotion_suggestions_incremental CHECK (
        incremental_interval_low <= incremental_bookings
            AND incremental_bookings <= incremental_interval_high
    ),
    -- The interval is allowed to cross zero, and it must be allowed to: an honest model sometimes
    -- says this promotion may cost you bookings. What is never allowed is a cost the host cannot
    -- see, so the cost is not null and not negative.
    CONSTRAINT ck_promotion_suggestions_cost CHECK (host_cost_minor >= 0),
    CONSTRAINT ck_promotion_suggestions_funding CHECK (
        funding_split IN ('HOST_FUNDED', 'PLATFORM_FUNDED', 'SHARED')
    ),
    CONSTRAINT ck_promotion_suggestions_share CHECK (
        (funding_split = 'SHARED') = (host_funded_share_percent IS NOT NULL)
            AND (host_funded_share_percent IS NULL
                 OR (host_funded_share_percent > 0 AND host_funded_share_percent < 100))
    ),
    CONSTRAINT ck_promotion_suggestions_host_cost_consistency CHECK (
        funding_split <> 'PLATFORM_FUNDED' OR host_cost_minor = 0
    ),
    CONSTRAINT ck_promotion_suggestions_evidence CHECK (
        evidence_basis IN ('FORECAST_MODEL', 'MARKET_BENCHMARK', 'HISTORICAL_UPLIFT',
                           'EXPERIMENT_RESULT', 'HEURISTIC')
    ),
    -- A model-based suggestion names the model and the run it read. "The system suggested it" is
    -- not evidence, and it is not something a host can ask a second question about.
    CONSTRAINT ck_promotion_suggestions_model CHECK (
        evidence_basis <> 'FORECAST_MODEL'
            OR (model_version_id IS NOT NULL AND forecast_run_id IS NOT NULL)
    ),
    CONSTRAINT ck_promotion_suggestions_state CHECK (
        decision_state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_promotion_suggestions_accepted CHECK (
        (decision_state = 'ACCEPTED') = (accepted_promotion_id IS NOT NULL)
    ),
    CONSTRAINT ck_promotion_suggestions_expiry CHECK (expires_at > generated_at),
    CONSTRAINT ck_promotion_suggestions_version CHECK (version >= 0)
);

CREATE INDEX idx_promotion_suggestions_open
    ON promotion_suggestions (accommodation_type_id, expires_at)
    WHERE decision_state = 'PENDING';
--rollback DROP TABLE promotion_suggestions;

--changeset ninggiangboy:033-09-host-advice-disclosures
-- The domain document's first hard rule for this area names four things a recommendation must
-- state: evidence, uncertainty, expected impact, and economic effect. They are four columns here
-- and none of them may be null. A recommendation that cannot fill all four is one the platform is
-- not yet entitled to make, and the place to discover that is the insert, not the support case.
CREATE TABLE host_advice_disclosures (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    advice_kind                 VARCHAR(24) NOT NULL,
    price_recommendation_id     UUID,
    demand_forecast_id          UUID,
    promotion_suggestion_id     UUID,
    checklist_state_id          UUID,
    recipient_account_holder_id UUID NOT NULL,
    surface                     VARCHAR(24) NOT NULL,
    locale                      VARCHAR(35) NOT NULL,

    evidence_summary            VARCHAR(2000) NOT NULL,
    evidence_references         JSONB,
    uncertainty_statement       VARCHAR(1000) NOT NULL,
    expected_impact_statement   VARCHAR(1000) NOT NULL,
    economic_effect_statement   VARCHAR(1000) NOT NULL,
    who_pays                    VARCHAR(24) NOT NULL,
    confidence                  VARCHAR(16) NOT NULL,

    opt_out_offered             BOOLEAN NOT NULL DEFAULT true,
    override_offered            BOOLEAN NOT NULL DEFAULT true,
    model_version_id            UUID,
    rendered_digest             CHAR(64) NOT NULL,
    disclosed_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_host_advice_disclosures_recommendation FOREIGN KEY (price_recommendation_id)
        REFERENCES price_recommendations (id),
    CONSTRAINT fk_host_advice_disclosures_forecast FOREIGN KEY (demand_forecast_id)
        REFERENCES demand_forecasts (id),
    CONSTRAINT fk_host_advice_disclosures_suggestion FOREIGN KEY (promotion_suggestion_id)
        REFERENCES promotion_suggestions (id),
    CONSTRAINT fk_host_advice_disclosures_recipient FOREIGN KEY (recipient_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_advice_disclosures_model FOREIGN KEY (model_version_id)
        REFERENCES model_versions (id),
    CONSTRAINT ck_host_advice_disclosures_kind CHECK (
        advice_kind IN ('PRICE_RECOMMENDATION', 'DEMAND_FORECAST', 'PROMOTION_SUGGESTION',
                        'QUALITY_CHECKLIST_ITEM')
    ),
    CONSTRAINT ck_host_advice_disclosures_target CHECK (
        num_nonnulls(price_recommendation_id, demand_forecast_id, promotion_suggestion_id,
                     checklist_state_id) = 1
    ),
    -- The kind and the column that is filled in have to agree, or a query that filters on the kind
    -- reads a different set of rows from one that joins on the reference.
    CONSTRAINT ck_host_advice_disclosures_kind_target CHECK (
        (advice_kind = 'PRICE_RECOMMENDATION') = (price_recommendation_id IS NOT NULL)
            AND (advice_kind = 'DEMAND_FORECAST') = (demand_forecast_id IS NOT NULL)
            AND (advice_kind = 'PROMOTION_SUGGESTION') = (promotion_suggestion_id IS NOT NULL)
            AND (advice_kind = 'QUALITY_CHECKLIST_ITEM') = (checklist_state_id IS NOT NULL)
    ),
    CONSTRAINT ck_host_advice_disclosures_surface CHECK (
        surface IN ('HOST_DASHBOARD', 'HOST_CALENDAR', 'HOST_INSIGHTS', 'HOST_NOTIFICATION',
                    'HOST_API')
    ),
    CONSTRAINT ck_host_advice_disclosures_locale CHECK (
        locale ~ '^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$'
    ),
    CONSTRAINT ck_host_advice_disclosures_who_pays CHECK (
        who_pays IN ('HOST', 'PLATFORM', 'SHARED', 'NO_DIRECT_COST')
    ),
    CONSTRAINT ck_host_advice_disclosures_confidence CHECK (
        confidence IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT ck_host_advice_disclosures_references CHECK (
        evidence_references IS NULL OR jsonb_typeof(evidence_references) = 'array'
    ),
    -- Advice a host cannot refuse is an instruction. The two booleans record that the refusal and
    -- the override were actually on the screen, so "the host could have said no" is a stored fact
    -- rather than an assumption about a release that shipped eighteen months ago.
    CONSTRAINT ck_host_advice_disclosures_refusable CHECK (opt_out_offered OR override_offered),
    CONSTRAINT ck_host_advice_disclosures_digest CHECK (rendered_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_host_advice_disclosures_recipient
    ON host_advice_disclosures (recipient_account_holder_id, disclosed_at DESC);
--rollback DROP TABLE host_advice_disclosures;

--changeset ninggiangboy:033-10-host-advice-decisions
CREATE TABLE host_advice_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    disclosure_id               UUID NOT NULL,
    outcome                     VARCHAR(16) NOT NULL,
    actor_role                  VARCHAR(24) NOT NULL,
    decided_by                  UUID,
    reason_code                 VARCHAR(48),
    reason_text                 VARCHAR(1000),
    modification_summary        VARCHAR(1000),
    applied_reference           VARCHAR(200),
    decided_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    -- One decision per disclosure. A host who changes their mind is shown the advice again and a
    -- second disclosure is written, so the pairing of what was on the screen with what was chosen
    -- never has to be guessed at from timestamps.
    CONSTRAINT uk_host_advice_decisions_disclosure UNIQUE (disclosure_id),
    CONSTRAINT fk_host_advice_decisions_disclosure FOREIGN KEY (disclosure_id)
        REFERENCES host_advice_disclosures (id),
    CONSTRAINT fk_host_advice_decisions_actor FOREIGN KEY (decided_by)
        REFERENCES account_holders (id),
    CONSTRAINT ck_host_advice_decisions_outcome CHECK (
        outcome IN ('ACCEPTED', 'REJECTED', 'MODIFIED', 'IGNORED', 'EXPIRED')
    ),
    CONSTRAINT ck_host_advice_decisions_actor_role CHECK (
        actor_role IN ('HOST', 'CO_HOST', 'PLATFORM_AUTOMATION')
    ),
    -- Ignoring advice is an outcome the platform observes rather than one a host takes, so it has
    -- no actor. Every outcome a person takes has one. A system that only stores acceptances cannot
    -- tell a good recommendation from one nobody dared refuse.
    CONSTRAINT ck_host_advice_decisions_actor CHECK (
        (outcome IN ('IGNORED', 'EXPIRED'))
            = (actor_role = 'PLATFORM_AUTOMATION' AND decided_by IS NULL)
    ),
    CONSTRAINT ck_host_advice_decisions_reason CHECK (
        outcome <> 'REJECTED' OR reason_code IS NOT NULL
    ),
    CONSTRAINT ck_host_advice_decisions_modification CHECK (
        (outcome = 'MODIFIED') = (modification_summary IS NOT NULL)
    ),
    CONSTRAINT ck_host_advice_decisions_applied CHECK (
        applied_reference IS NULL OR outcome IN ('ACCEPTED', 'MODIFIED')
    )
);
--rollback DROP TABLE host_advice_decisions;

--changeset ninggiangboy:033-11-listing-quality-checklist-versions
CREATE TABLE listing_quality_checklist_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_key               VARCHAR(64) NOT NULL,
    checklist_version           INTEGER NOT NULL,
    market_code                 VARCHAR(2),
    display_name                VARCHAR(160) NOT NULL,
    purpose                     VARCHAR(500) NOT NULL,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at                TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_listing_quality_checklist_versions_version
        UNIQUE (checklist_key, checklist_version),
    CONSTRAINT fk_listing_quality_checklist_versions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_listing_quality_checklist_versions_key CHECK (
        checklist_key ~ '^[a-z][a-z0-9_]*$'
    ),
    CONSTRAINT ck_listing_quality_checklist_versions_number CHECK (checklist_version > 0),
    CONSTRAINT ck_listing_quality_checklist_versions_market_code CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_listing_quality_checklist_versions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_listing_quality_checklist_versions_published CHECK (
        status = 'DRAFT' OR published_at IS NOT NULL
    ),
    CONSTRAINT ck_listing_quality_checklist_versions_retired CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_listing_quality_checklist_versions_row_version CHECK (version >= 0)
);
--rollback DROP TABLE listing_quality_checklist_versions;

--changeset ninggiangboy:033-12-listing-quality-checklist-items
CREATE TABLE listing_quality_checklist_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checklist_version_id        UUID NOT NULL,
    item_key                    VARCHAR(64) NOT NULL,
    ordinal                     SMALLINT NOT NULL,
    category                    VARCHAR(24) NOT NULL,
    title                       VARCHAR(200) NOT NULL,
    guidance                    VARCHAR(1000) NOT NULL,
    evidence_source             VARCHAR(24) NOT NULL,
    severity                    VARCHAR(16) NOT NULL,
    blocks_publication          BOOLEAN NOT NULL DEFAULT false,
    expected_effect_statement   VARCHAR(1000) NOT NULL,
    effect_confidence           VARCHAR(16) NOT NULL,
    host_dismissible            BOOLEAN NOT NULL DEFAULT true,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_listing_quality_checklist_items_key
        UNIQUE (checklist_version_id, item_key),
    CONSTRAINT uk_listing_quality_checklist_items_ordinal
        UNIQUE (checklist_version_id, ordinal),
    CONSTRAINT fk_listing_quality_checklist_items_version FOREIGN KEY (checklist_version_id)
        REFERENCES listing_quality_checklist_versions (id) ON DELETE CASCADE,
    CONSTRAINT ck_listing_quality_checklist_items_key_shape CHECK (item_key ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_listing_quality_checklist_items_ordinal CHECK (ordinal > 0),
    CONSTRAINT ck_listing_quality_checklist_items_category CHECK (
        category IN ('CONTENT', 'MEDIA', 'PRICING', 'AVAILABILITY', 'POLICY', 'SAFETY',
                     'ACCESSIBILITY', 'RESPONSIVENESS', 'COMPLIANCE')
    ),
    CONSTRAINT ck_listing_quality_checklist_items_evidence CHECK (
        evidence_source IN ('LISTING_CONTENT', 'LISTING_MEDIA', 'REVIEW_ASPECT', 'QUALITY_PROFILE',
                            'RESPONSE_METRIC', 'MARKET_BENCHMARK', 'POLICY_REGISTRY')
    ),
    CONSTRAINT ck_listing_quality_checklist_items_severity CHECK (
        severity IN ('INFO', 'SUGGESTED', 'IMPORTANT', 'REQUIRED')
    ),
    CONSTRAINT ck_listing_quality_checklist_items_effect_confidence CHECK (
        effect_confidence IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    -- An item that blocks publication is a requirement, and a requirement a host may dismiss is not
    -- one. Calling it a suggestion while it stops the listing going live is the coercion the domain
    -- document names: the host is told it is advice and discovers it is a gate.
    CONSTRAINT ck_listing_quality_checklist_items_blocking CHECK (
        NOT blocks_publication OR (severity = 'REQUIRED' AND NOT host_dismissible)
    )
);
--rollback DROP TABLE listing_quality_checklist_items;

--changeset ninggiangboy:033-13-listing-quality-checklist-states
CREATE TABLE listing_quality_checklist_states (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id                  UUID NOT NULL,
    checklist_item_id           UUID NOT NULL,
    item_state                  VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    detected_at                 TIMESTAMPTZ NOT NULL,
    last_evaluated_at           TIMESTAMPTZ NOT NULL,
    evidence_reference          VARCHAR(200),
    evidence_detail             VARCHAR(1000),
    quality_profile_id          UUID,
    satisfied_at                TIMESTAMPTZ,
    verified_at                 TIMESTAMPTZ,
    dismissed_at                TIMESTAMPTZ,
    dismissed_by                UUID,
    dismissal_reason            VARCHAR(48),
    not_applicable_reason       VARCHAR(48),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_listing_quality_checklist_states_item UNIQUE (listing_id, checklist_item_id),
    CONSTRAINT fk_listing_quality_checklist_states_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id) ON DELETE CASCADE,
    CONSTRAINT fk_listing_quality_checklist_states_item FOREIGN KEY (checklist_item_id)
        REFERENCES listing_quality_checklist_items (id),
    CONSTRAINT fk_listing_quality_checklist_states_profile FOREIGN KEY (quality_profile_id)
        REFERENCES listing_quality_profiles (id),
    CONSTRAINT fk_listing_quality_checklist_states_dismisser FOREIGN KEY (dismissed_by)
        REFERENCES account_holders (id),
    CONSTRAINT ck_listing_quality_checklist_states_state CHECK (
        item_state IN ('OPEN', 'SATISFIED', 'DISMISSED', 'NOT_APPLICABLE', 'EXPIRED')
    ),
    -- Satisfied means the platform looked again and the thing it complained about is gone. Without
    -- the second look it means the host pressed a button, which is a different claim and a useless
    -- one to show a guest.
    CONSTRAINT ck_listing_quality_checklist_states_satisfied CHECK (
        (item_state = 'SATISFIED') = (satisfied_at IS NOT NULL AND verified_at IS NOT NULL)
    ),
    CONSTRAINT ck_listing_quality_checklist_states_dismissed CHECK (
        (item_state = 'DISMISSED')
            = (dismissed_at IS NOT NULL AND dismissed_by IS NOT NULL
               AND dismissal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_listing_quality_checklist_states_not_applicable CHECK (
        (item_state = 'NOT_APPLICABLE') = (not_applicable_reason IS NOT NULL)
    ),
    CONSTRAINT ck_listing_quality_checklist_states_evaluation CHECK (
        last_evaluated_at >= detected_at
    ),
    CONSTRAINT ck_listing_quality_checklist_states_version CHECK (version >= 0)
);

CREATE INDEX idx_listing_quality_checklist_states_open
    ON listing_quality_checklist_states (listing_id)
    WHERE item_state = 'OPEN';

-- The disclosure table is created before the checklist state it can point at, so this reference is
-- closed here rather than left dangling.
ALTER TABLE host_advice_disclosures
    ADD CONSTRAINT fk_host_advice_disclosures_checklist FOREIGN KEY (checklist_state_id)
        REFERENCES listing_quality_checklist_states (id);
--rollback ALTER TABLE host_advice_disclosures DROP CONSTRAINT fk_host_advice_disclosures_checklist;
--rollback DROP TABLE listing_quality_checklist_states;

--changeset ninggiangboy:033-14-calendar-value-sources
-- One row per calendar value that is actually in force, naming the layer that won and the layer it
-- beat. The domain document's rule is that a host can see the active source of each calendar value;
-- without this table the answer is "re-run the resolver and see what it says today", which is not
-- the same question the host asked, because the inputs have moved since.
CREATE TABLE calendar_value_sources (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_resource_id       UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    value_kind                  VARCHAR(24) NOT NULL,
    active_source               VARCHAR(24) NOT NULL,
    source_reference_kind       VARCHAR(32),
    source_reference_id         UUID,
    overridden_source           VARCHAR(24),
    precedence_rank             SMALLINT NOT NULL,
    resolved_display_value      VARCHAR(64) NOT NULL,
    resolved_amount_minor       BIGINT,
    currency                    VARCHAR(3),
    host_editable               BOOLEAN NOT NULL DEFAULT true,
    locked_reason               VARCHAR(48),
    effective_from              TIMESTAMPTZ NOT NULL,
    resolved_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_calendar_value_sources_value
        UNIQUE (inventory_resource_id, stay_date, value_kind),
    CONSTRAINT fk_calendar_value_sources_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id) ON DELETE CASCADE,
    CONSTRAINT ck_calendar_value_sources_kind CHECK (
        value_kind IN ('NIGHTLY_PRICE', 'MINIMUM_STAY', 'MAXIMUM_STAY', 'CLOSED_TO_ARRIVAL',
                       'CLOSED_TO_DEPARTURE', 'CLOSED_TO_STAY', 'SELLABLE_QUANTITY')
    ),
    CONSTRAINT ck_calendar_value_sources_source CHECK (
        active_source IN ('SYSTEM_DEFAULT', 'RATE_PLAN', 'PRICE_RULE', 'MANUAL_OVERRIDE',
                          'RECOMMENDATION_APPLIED', 'PROMOTION', 'BULK_EDIT', 'CHANNEL_SYNC',
                          'INVENTORY_BLOCK', 'INVENTORY_HOLD', 'BOOKING_CLAIM')
    ),
    CONSTRAINT ck_calendar_value_sources_overridden CHECK (
        overridden_source IS NULL
            OR (overridden_source IN ('SYSTEM_DEFAULT', 'RATE_PLAN', 'PRICE_RULE',
                                      'MANUAL_OVERRIDE', 'RECOMMENDATION_APPLIED', 'PROMOTION',
                                      'BULK_EDIT', 'CHANNEL_SYNC', 'INVENTORY_BLOCK',
                                      'INVENTORY_HOLD', 'BOOKING_CLAIM')
                AND overridden_source <> active_source)
    ),
    -- Everything except the two defaults came from a row somewhere, and the host is owed the
    -- pointer to it: "a rule set this" without saying which rule is the opacity the domain document
    -- is about, one level down.
    CONSTRAINT ck_calendar_value_sources_reference CHECK (
        (source_reference_kind IS NULL) = (source_reference_id IS NULL)
            AND (active_source IN ('SYSTEM_DEFAULT', 'RATE_PLAN')
                 OR source_reference_id IS NOT NULL)
    ),
    CONSTRAINT ck_calendar_value_sources_precedence CHECK (precedence_rank >= 0),
    CONSTRAINT ck_calendar_value_sources_money CHECK (
        (resolved_amount_minor IS NULL) = (currency IS NULL)
            AND (value_kind <> 'NIGHTLY_PRICE' OR resolved_amount_minor IS NOT NULL)
    ),
    CONSTRAINT ck_calendar_value_sources_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    -- A value the host cannot change has a reason on the same row. A greyed-out cell with no
    -- explanation is the calendar telling a host their own listing is not theirs.
    CONSTRAINT ck_calendar_value_sources_lock CHECK (host_editable = (locked_reason IS NULL)),
    CONSTRAINT ck_calendar_value_sources_version CHECK (version >= 0)
);

CREATE INDEX idx_calendar_value_sources_date
    ON calendar_value_sources (inventory_resource_id, stay_date);
--rollback DROP TABLE calendar_value_sources;

--changeset ninggiangboy:033-15-host-bulk-edit-requests
CREATE TABLE host_bulk_edit_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_account_holder_id      UUID NOT NULL,
    idempotency_key             VARCHAR(120) NOT NULL,
    edit_kind                   VARCHAR(24) NOT NULL,
    scope_description           VARCHAR(500) NOT NULL,
    listing_ids                 UUID[],
    accommodation_type_ids      UUID[],
    scope_from                  DATE,
    scope_until                 DATE,
    requested_change            JSONB NOT NULL,
    request_state               VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    preview_digest              CHAR(64),
    previewed_at                TIMESTAMPTZ,
    target_count                INTEGER NOT NULL DEFAULT 0,
    applied_count               INTEGER NOT NULL DEFAULT 0,
    skipped_count               INTEGER NOT NULL DEFAULT 0,
    refused_count               INTEGER NOT NULL DEFAULT 0,
    failure_reason              VARCHAR(500),
    requested_at                TIMESTAMPTZ NOT NULL,
    applied_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_host_bulk_edit_requests_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_host_bulk_edit_requests_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_host_bulk_edit_requests_kind CHECK (
        edit_kind IN ('AVAILABILITY', 'NIGHTLY_PRICE', 'STAY_RESTRICTION', 'RATE_PLAN',
                      'LISTING_CONTENT')
    ),
    CONSTRAINT ck_host_bulk_edit_requests_state CHECK (
        request_state IN ('DRAFT', 'PREVIEWED', 'APPLYING', 'APPLIED', 'PARTIALLY_APPLIED',
                          'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_host_bulk_edit_requests_change CHECK (
        jsonb_typeof(requested_change) = 'object'
    ),
    CONSTRAINT ck_host_bulk_edit_requests_scope CHECK (
        (array_length(listing_ids, 1) IS NOT NULL
         OR array_length(accommodation_type_ids, 1) IS NOT NULL)
            AND (scope_from IS NULL) = (scope_until IS NULL)
            AND (scope_until IS NULL OR scope_until >= scope_from)
    ),
    -- A bulk write that was never previewed is a write whose blast radius nobody saw, including the
    -- host who asked for it. The preview digest is what the applied run is checked against, so the
    -- four hundred nights that change are the four hundred that were shown.
    CONSTRAINT ck_host_bulk_edit_requests_preview CHECK (
        (preview_digest IS NULL) = (previewed_at IS NULL)
            AND (request_state IN ('DRAFT', 'CANCELLED') OR preview_digest IS NOT NULL)
    ),
    CONSTRAINT ck_host_bulk_edit_requests_digest CHECK (
        preview_digest IS NULL OR preview_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_host_bulk_edit_requests_counts CHECK (
        target_count >= 0 AND applied_count >= 0 AND skipped_count >= 0 AND refused_count >= 0
            AND applied_count + skipped_count + refused_count <= target_count
    ),
    -- APPLIED means every target applied. Anything less is PARTIALLY_APPLIED, and the difference
    -- is the whole reason this table exists: a host who reads "done" and later finds sixty nights
    -- unchanged learns it from a guest complaint.
    CONSTRAINT ck_host_bulk_edit_requests_applied CHECK (
        request_state <> 'APPLIED'
            OR (applied_count = target_count AND skipped_count = 0 AND refused_count = 0)
    ),
    CONSTRAINT ck_host_bulk_edit_requests_partial CHECK (
        request_state <> 'PARTIALLY_APPLIED'
            OR (applied_count + skipped_count + refused_count = target_count
                AND skipped_count + refused_count > 0)
    ),
    CONSTRAINT ck_host_bulk_edit_requests_applied_at CHECK (
        (request_state IN ('APPLIED', 'PARTIALLY_APPLIED')) = (applied_at IS NOT NULL)
    ),
    CONSTRAINT ck_host_bulk_edit_requests_failure CHECK (
        (request_state = 'FAILED') = (failure_reason IS NOT NULL)
    ),
    CONSTRAINT ck_host_bulk_edit_requests_version CHECK (version >= 0)
);

CREATE INDEX idx_host_bulk_edit_requests_host
    ON host_bulk_edit_requests (host_account_holder_id, requested_at DESC);
--rollback DROP TABLE host_bulk_edit_requests;

--changeset ninggiangboy:033-16-host-bulk-edit-targets
CREATE TABLE host_bulk_edit_targets (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bulk_edit_request_id        UUID NOT NULL,
    sequence_number             INTEGER NOT NULL,
    target_kind                 VARCHAR(24) NOT NULL,
    listing_id                  UUID,
    inventory_resource_id       UUID,
    stay_date                   DATE,
    outcome                     VARCHAR(16) NOT NULL,
    reason_code                 VARCHAR(48),
    reason_detail               VARCHAR(500),
    before_value                VARCHAR(120),
    after_value                 VARCHAR(120),
    attempted_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_host_bulk_edit_targets_sequence
        UNIQUE (bulk_edit_request_id, sequence_number),
    CONSTRAINT fk_host_bulk_edit_targets_request FOREIGN KEY (bulk_edit_request_id)
        REFERENCES host_bulk_edit_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_host_bulk_edit_targets_listing FOREIGN KEY (listing_id)
        REFERENCES listings (id),
    CONSTRAINT fk_host_bulk_edit_targets_resource FOREIGN KEY (inventory_resource_id)
        REFERENCES inventory_resources (id),
    CONSTRAINT ck_host_bulk_edit_targets_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_host_bulk_edit_targets_kind CHECK (
        target_kind IN ('AVAILABILITY_DAY', 'LISTING', 'RATE_PLAN', 'ACCOMMODATION_TYPE')
    ),
    CONSTRAINT ck_host_bulk_edit_targets_identity CHECK (
        num_nonnulls(listing_id, inventory_resource_id) = 1
            AND (target_kind <> 'AVAILABILITY_DAY'
                 OR (inventory_resource_id IS NOT NULL AND stay_date IS NOT NULL))
    ),
    CONSTRAINT ck_host_bulk_edit_targets_outcome CHECK (
        outcome IN ('APPLIED', 'SKIPPED', 'REFUSED')
    ),
    -- Every target that did not change says why in a code the host interface can translate.
    -- "Refused" with no reason is the same silence as no row at all, one layer further in.
    CONSTRAINT ck_host_bulk_edit_targets_reason CHECK (
        (outcome = 'APPLIED') = (reason_code IS NULL)
    ),
    CONSTRAINT ck_host_bulk_edit_targets_values CHECK (
        (outcome = 'APPLIED') = (after_value IS NOT NULL)
    )
);
--rollback DROP TABLE host_bulk_edit_targets;

--changeset ninggiangboy:033-17-host-payout-previews
-- A preview is arithmetic over what is already known, shown before the money exists. It is not a
-- statement and it is not a promise: it carries a validity horizon, it names the basis it was
-- computed on, and its net has to equal the components it is explained by. Migration 022 owns the
-- statement that is binding.
CREATE TABLE host_payout_previews (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_account_holder_id      UUID NOT NULL,
    subject_kind                VARCHAR(16) NOT NULL,
    booking_id                  UUID,
    period_start                DATE,
    period_end                  DATE,
    currency                    VARCHAR(3) NOT NULL,

    gross_accommodation_minor   BIGINT NOT NULL,
    gross_fees_minor            BIGINT NOT NULL DEFAULT 0,
    discounts_minor             BIGINT NOT NULL DEFAULT 0,
    platform_fee_minor          BIGINT NOT NULL DEFAULT 0,
    processing_fee_minor        BIGINT NOT NULL DEFAULT 0,
    tax_withheld_minor          BIGINT NOT NULL DEFAULT 0,
    reserve_retained_minor      BIGINT NOT NULL DEFAULT 0,
    adjustments_minor           BIGINT NOT NULL DEFAULT 0,
    estimated_net_minor         BIGINT NOT NULL,

    basis                       VARCHAR(24) NOT NULL,
    assumption_summary          VARCHAR(1000) NOT NULL,
    assumptions                 JSONB,
    earliest_release_date       DATE,
    expected_payout_date        DATE,
    computed_at                 TIMESTAMPTZ NOT NULL,
    valid_until                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_host_payout_previews_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_payout_previews_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT ck_host_payout_previews_subject CHECK (subject_kind IN ('BOOKING', 'PERIOD')),
    CONSTRAINT ck_host_payout_previews_subject_columns CHECK (
        (subject_kind = 'BOOKING') = (booking_id IS NOT NULL)
            AND (subject_kind = 'PERIOD') = (period_start IS NOT NULL AND period_end IS NOT NULL)
            AND (period_end IS NULL OR period_end > period_start)
    ),
    CONSTRAINT ck_host_payout_previews_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_host_payout_previews_components CHECK (
        gross_accommodation_minor >= 0 AND gross_fees_minor >= 0 AND discounts_minor >= 0
            AND platform_fee_minor >= 0 AND processing_fee_minor >= 0 AND tax_withheld_minor >= 0
            AND reserve_retained_minor >= 0
    ),
    -- The total has to equal the explanation. A net figure that does not fall out of the lines
    -- beside it is the number a host will quote back during a dispute, and nothing will reproduce
    -- it. Adjustments carry their own sign; every other component reduces the gross.
    CONSTRAINT ck_host_payout_previews_total CHECK (
        estimated_net_minor = gross_accommodation_minor + gross_fees_minor - discounts_minor
            - platform_fee_minor - processing_fee_minor - tax_withheld_minor
            - reserve_retained_minor + adjustments_minor
    ),
    CONSTRAINT ck_host_payout_previews_basis CHECK (
        basis IN ('CONFIRMED_OBLIGATIONS', 'FORECAST', 'MIXED')
    ),
    CONSTRAINT ck_host_payout_previews_assumptions CHECK (
        assumptions IS NULL OR jsonb_typeof(assumptions) = 'object'
    ),
    CONSTRAINT ck_host_payout_previews_dates CHECK (
        expected_payout_date IS NULL OR earliest_release_date IS NULL
            OR expected_payout_date >= earliest_release_date
    ),
    -- An estimate with no expiry becomes a quotation the moment a host screenshots it.
    CONSTRAINT ck_host_payout_previews_validity CHECK (valid_until > computed_at)
);

CREATE INDEX idx_host_payout_previews_host
    ON host_payout_previews (host_account_holder_id, computed_at DESC);
--rollback DROP TABLE host_payout_previews;

--changeset ninggiangboy:033-18-host-operations-append-only splitStatements:false
-- Every table here records something that was observed, computed or shown at a particular moment.
-- Editing one is not a correction, it is the destruction of the only record that could show what
-- the host was actually told: a metric restated in place makes last month's decision look like it
-- was taken on this month's number, and a disclosure edited after the fact makes every decision
-- citing it unreadable. Corrections are new rows with a later computed_at. These reuse the
-- function migration 030 installed rather than duplicating it.
CREATE TRIGGER trg_host_performance_metrics_append_only
    BEFORE UPDATE ON host_performance_metrics
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_host_response_metrics_append_only
    BEFORE UPDATE ON host_response_metrics
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_market_benchmark_aggregates_append_only
    BEFORE UPDATE ON market_benchmark_aggregates
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_demand_forecasts_append_only
    BEFORE UPDATE ON demand_forecasts
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_host_advice_disclosures_append_only
    BEFORE UPDATE ON host_advice_disclosures
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_host_advice_decisions_append_only
    BEFORE UPDATE ON host_advice_decisions
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_host_bulk_edit_targets_append_only
    BEFORE UPDATE ON host_bulk_edit_targets
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_checklist_items_append_only
    BEFORE UPDATE ON listing_quality_checklist_items
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_host_payout_previews_append_only
    BEFORE UPDATE ON host_payout_previews
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();
--rollback DROP TRIGGER trg_host_payout_previews_append_only ON host_payout_previews;
--rollback DROP TRIGGER trg_checklist_items_append_only ON listing_quality_checklist_items;
--rollback DROP TRIGGER trg_host_bulk_edit_targets_append_only ON host_bulk_edit_targets;
--rollback DROP TRIGGER trg_host_advice_decisions_append_only ON host_advice_decisions;
--rollback DROP TRIGGER trg_host_advice_disclosures_append_only ON host_advice_disclosures;
--rollback DROP TRIGGER trg_demand_forecasts_append_only ON demand_forecasts;
--rollback DROP TRIGGER trg_market_benchmark_aggregates_append_only ON market_benchmark_aggregates;
--rollback DROP TRIGGER trg_host_response_metrics_append_only ON host_response_metrics;
--rollback DROP TRIGGER trg_host_performance_metrics_append_only ON host_performance_metrics;

--changeset ninggiangboy:033-19-host-registry-immutability splitStatements:false
-- A metric whose wording or evidence floor can be edited after hosts were judged against it, a
-- cohort whose privacy floor can be lowered after its benchmarks were published, a checklist whose
-- items can change meaning after listings were marked against them -- each turns a published claim
-- into a claim about something nobody read. Once one of these leaves DRAFT it is frozen except for
-- the columns that carry it through its own lifecycle. A different meaning is a new version.
CREATE TRIGGER trg_host_metric_publications_freeze
    BEFORE UPDATE OR DELETE ON host_metric_publications
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'published_at', 'retired_at', 'updated_at', 'version');

CREATE TRIGGER trg_benchmark_cohort_definitions_freeze
    BEFORE UPDATE OR DELETE ON benchmark_cohort_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'published_at', 'retired_at', 'updated_at', 'version');

CREATE TRIGGER trg_checklist_versions_freeze
    BEFORE UPDATE OR DELETE ON listing_quality_checklist_versions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'published_at', 'retired_at', 'updated_at', 'version');
--rollback DROP TRIGGER trg_checklist_versions_freeze ON listing_quality_checklist_versions;
--rollback DROP TRIGGER trg_benchmark_cohort_definitions_freeze ON benchmark_cohort_definitions;
--rollback DROP TRIGGER trg_host_metric_publications_freeze ON host_metric_publications;

--changeset ninggiangboy:033-20-benchmark-privacy-floor splitStatements:false
-- The cohort's floor is only a privacy control if something refuses to publish below it, and the
-- suppression reason is only evidence if it has to be the true one. Both halves live here. A row
-- claiming TOO_FEW_CONTRIBUTORS while the cohort was comfortably above its floor is a suppression
-- nobody can explain later, and the shape it hides is a quality failure being reported as privacy.
CREATE FUNCTION benchmark_privacy_floor() RETURNS TRIGGER AS $$
DECLARE
    cohort_record RECORD;
    publication_record RECORD;
BEGIN
    SELECT minimum_contributors, minimum_observations, maximum_contributor_share, status
        INTO cohort_record
        FROM benchmark_cohort_definitions
        WHERE id = NEW.cohort_definition_id;

    SELECT benchmarkable, status INTO publication_record
        FROM host_metric_publications
        WHERE id = NEW.publication_id;

    IF NOT publication_record.benchmarkable THEN
        RAISE EXCEPTION
            'metric publication % is not benchmarkable; a metric is comparable across hosts only '
            'when its definition says so',
            NEW.publication_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.publication_state = 'PUBLISHED' THEN
        IF publication_record.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
            RAISE EXCEPTION
                'metric publication % is % and may not carry a published benchmark',
                NEW.publication_id, publication_record.status
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF cohort_record.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
            RAISE EXCEPTION
                'cohort % is % and may not carry a published benchmark',
                NEW.cohort_definition_id, cohort_record.status
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.contributor_count < cohort_record.minimum_contributors THEN
            RAISE EXCEPTION
                'benchmark has % contributors against a floor of %; suppress the value rather '
                'than publishing an aggregate the contributors can subtract themselves out of',
                NEW.contributor_count, cohort_record.minimum_contributors
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.observation_count < cohort_record.minimum_observations THEN
            RAISE EXCEPTION
                'benchmark has % observations against a floor of %',
                NEW.observation_count, cohort_record.minimum_observations
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.largest_contributor_share IS NULL THEN
            RAISE EXCEPTION
                'a published benchmark states how much of it the largest contributor supplied; '
                'without it the concentration ceiling cannot be checked'
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.largest_contributor_share > cohort_record.maximum_contributor_share THEN
            RAISE EXCEPTION
                'largest contributor share % exceeds the cohort ceiling %; this is one host''s '
                'number with the others rounding it',
                NEW.largest_contributor_share, cohort_record.maximum_contributor_share
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        IF NEW.suppression_reason = 'TOO_FEW_CONTRIBUTORS'
                AND NEW.contributor_count >= cohort_record.minimum_contributors THEN
            RAISE EXCEPTION
                'suppression claims too few contributors, but % meets the floor of %',
                NEW.contributor_count, cohort_record.minimum_contributors
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.suppression_reason = 'TOO_FEW_OBSERVATIONS'
                AND NEW.observation_count >= cohort_record.minimum_observations THEN
            RAISE EXCEPTION
                'suppression claims too few observations, but % meets the floor of %',
                NEW.observation_count, cohort_record.minimum_observations
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.suppression_reason = 'CONTRIBUTOR_CONCENTRATION'
                AND (NEW.largest_contributor_share IS NULL
                     OR NEW.largest_contributor_share
                        <= cohort_record.maximum_contributor_share) THEN
            RAISE EXCEPTION
                'suppression claims contributor concentration, but the largest share is within '
                'the cohort ceiling of %',
                cohort_record.maximum_contributor_share
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.suppression_reason = 'COHORT_RETIRED' AND cohort_record.status <> 'RETIRED' THEN
            RAISE EXCEPTION
                'suppression claims the cohort is retired, but cohort % is %',
                NEW.cohort_definition_id, cohort_record.status
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_market_benchmark_aggregates_privacy
    BEFORE INSERT OR UPDATE ON market_benchmark_aggregates
    FOR EACH ROW
    EXECUTE FUNCTION benchmark_privacy_floor();
--rollback DROP TRIGGER trg_market_benchmark_aggregates_privacy ON market_benchmark_aggregates;
--rollback DROP FUNCTION benchmark_privacy_floor();

--changeset ninggiangboy:033-21-host-metric-sufficiency splitStatements:false
-- The publication says how many observations its number needs before it means anything. This is
-- what makes that a rule rather than a field. It also refuses the reverse move: marking a metric
-- insufficient while the evidence was there is how an uncomfortable number disappears from a
-- dashboard without anybody deciding that it should.
CREATE FUNCTION host_metric_sufficiency() RETURNS TRIGGER AS $$
DECLARE
    publication_record RECORD;
BEGIN
    SELECT p.status, p.minimum_observations, p.subject_kinds, d.measure_unit
        INTO publication_record
        FROM host_metric_publications p
        JOIN metric_definitions d ON d.id = p.metric_definition_id
        WHERE p.id = NEW.publication_id;

    IF publication_record.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
        RAISE EXCEPTION
            'metric publication % is % and may not produce host-facing values',
            NEW.publication_id, publication_record.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT (NEW.subject_kind = ANY (publication_record.subject_kinds)) THEN
        RAISE EXCEPTION
            'metric publication % is not published for subject kind %',
            NEW.publication_id, NEW.subject_kind
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.evidence_state = 'SUFFICIENT'
            AND NEW.observation_count < publication_record.minimum_observations THEN
        RAISE EXCEPTION
            'metric has % observations against a published minimum of %; show the '
            'insufficient-evidence sentence rather than a number computed from noise',
            NEW.observation_count, publication_record.minimum_observations
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.evidence_state = 'INSUFFICIENT'
            AND NEW.observation_count >= publication_record.minimum_observations THEN
        RAISE EXCEPTION
            'metric has % observations, which meets the published minimum of %; it may not be '
            'recorded as insufficient evidence',
            NEW.observation_count, publication_record.minimum_observations
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF publication_record.measure_unit = 'MONEY_MINOR' THEN
        IF NEW.evidence_state = 'SUFFICIENT' AND NEW.value_minor IS NULL THEN
            RAISE EXCEPTION
                'metric % measures money and carries no minor-unit amount', NEW.publication_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSIF NEW.value_minor IS NOT NULL THEN
        RAISE EXCEPTION
            'metric % does not measure money and may not carry a minor-unit amount',
            NEW.publication_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF publication_record.measure_unit = 'RATIO' AND NEW.evidence_state = 'SUFFICIENT'
            AND NEW.denominator_count IS NULL THEN
        RAISE EXCEPTION
            'metric % is a ratio and must carry the denominator it was computed over',
            NEW.publication_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_host_performance_metrics_sufficiency
    BEFORE INSERT OR UPDATE ON host_performance_metrics
    FOR EACH ROW
    EXECUTE FUNCTION host_metric_sufficiency();
--rollback DROP TRIGGER trg_host_performance_metrics_sufficiency ON host_performance_metrics;
--rollback DROP FUNCTION host_metric_sufficiency();

--changeset ninggiangboy:033-22-demand-forecast-integrity splitStatements:false
-- A forecast belongs to the run that produced it, inside the window that run declared, and it may
-- only be written while that run is still running. Appending a row to a finished run is the same
-- defect migrations 022 and 027 found in two other places: the parent was checked when it closed,
-- and then the children kept arriving. The run's own count is checked against the rows at the
-- moment it claims to have succeeded, so a truncated run cannot report a complete one.
CREATE FUNCTION demand_forecast_integrity() RETURNS TRIGGER AS $$
DECLARE
    run_record RECORD;
BEGIN
    SELECT run_state, covers_from, covers_until INTO run_record
        FROM demand_forecast_runs WHERE id = NEW.forecast_run_id;

    IF run_record.run_state <> 'RUNNING' THEN
        RAISE EXCEPTION
            'forecast run % is %; forecasts are written while the run is RUNNING and the run is '
            'then closed against its own row count',
            NEW.forecast_run_id, run_record.run_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.stay_date < run_record.covers_from OR NEW.stay_date >= run_record.covers_until THEN
        RAISE EXCEPTION
            'forecast for % falls outside the window [%, %) the run declared',
            NEW.stay_date, run_record.covers_from, run_record.covers_until
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION demand_forecast_run_closure() RETURNS TRIGGER AS $$
DECLARE
    actual_count INTEGER;
BEGIN
    IF OLD.run_state IS DISTINCT FROM NEW.run_state AND NEW.run_state = 'SUCCEEDED' THEN
        SELECT count(*) INTO actual_count FROM demand_forecasts WHERE forecast_run_id = NEW.id;
        IF actual_count <> NEW.forecast_count THEN
            RAISE EXCEPTION
                'run % claims % forecasts but holds %; a run that reports more than it wrote is '
                'read as coverage the host does not have',
                NEW.id, NEW.forecast_count, actual_count
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF actual_count = 0 THEN
            RAISE EXCEPTION 'run % succeeded without producing a forecast', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF OLD.run_state IN ('SUCCEEDED', 'FAILED') AND NEW.run_state NOT IN ('SUCCEEDED', 'FAILED',
                                                                         'SUPERSEDED') THEN
        RAISE EXCEPTION
            'run % is %; a finished run is superseded by a new one rather than reopened',
            OLD.id, OLD.run_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_demand_forecasts_integrity
    BEFORE INSERT ON demand_forecasts
    FOR EACH ROW
    EXECUTE FUNCTION demand_forecast_integrity();

CREATE TRIGGER trg_demand_forecast_runs_closure
    BEFORE UPDATE ON demand_forecast_runs
    FOR EACH ROW
    EXECUTE FUNCTION demand_forecast_run_closure();
--rollback DROP TRIGGER trg_demand_forecast_runs_closure ON demand_forecast_runs;
--rollback DROP TRIGGER trg_demand_forecasts_integrity ON demand_forecasts;
--rollback DROP FUNCTION demand_forecast_run_closure();
--rollback DROP FUNCTION demand_forecast_integrity();

--changeset ninggiangboy:033-23-advice-disclosure-integrity splitStatements:false
-- Two rules, and they are the reason the pair of tables exists. Advice is disclosed while it is
-- still live: showing a host a recommendation that expired last Tuesday, or a forecast from a run
-- that failed, is not disclosure, it is a screenshot. And the economic effect on the screen is the
-- one recorded on the advice itself -- a suggestion booked as host-funded may not be shown to the
-- host as costing them nothing, which is the precise shape coercion takes when it is polite.
CREATE FUNCTION host_advice_disclosure_integrity() RETURNS TRIGGER AS $$
DECLARE
    recommendation_record RECORD;
    suggestion_record RECORD;
    forecast_state VARCHAR(16);
    checklist_state VARCHAR(16);
BEGIN
    IF NEW.advice_kind = 'PRICE_RECOMMENDATION' THEN
        SELECT decision_state, expires_at INTO recommendation_record
            FROM price_recommendations WHERE id = NEW.price_recommendation_id;
        IF recommendation_record.decision_state <> 'PENDING' THEN
            RAISE EXCEPTION
                'price recommendation % is already %; a decided recommendation is history, not '
                'advice',
                NEW.price_recommendation_id, recommendation_record.decision_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.disclosed_at >= recommendation_record.expires_at THEN
            RAISE EXCEPTION
                'price recommendation % expired at %; it describes a demand picture that no '
                'longer exists',
                NEW.price_recommendation_id, recommendation_record.expires_at
                USING ERRCODE = 'restrict_violation';
        END IF;

    ELSIF NEW.advice_kind = 'PROMOTION_SUGGESTION' THEN
        SELECT decision_state, expires_at, funding_split INTO suggestion_record
            FROM promotion_suggestions WHERE id = NEW.promotion_suggestion_id;
        IF suggestion_record.decision_state <> 'PENDING' THEN
            RAISE EXCEPTION
                'promotion suggestion % is already %',
                NEW.promotion_suggestion_id, suggestion_record.decision_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.disclosed_at >= suggestion_record.expires_at THEN
            RAISE EXCEPTION
                'promotion suggestion % expired at %',
                NEW.promotion_suggestion_id, suggestion_record.expires_at
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF (suggestion_record.funding_split = 'HOST_FUNDED' AND NEW.who_pays <> 'HOST')
                OR (suggestion_record.funding_split = 'PLATFORM_FUNDED'
                    AND NEW.who_pays <> 'PLATFORM')
                OR (suggestion_record.funding_split = 'SHARED' AND NEW.who_pays <> 'SHARED') THEN
            RAISE EXCEPTION
                'suggestion % is funded % but the disclosure tells the host % pays',
                NEW.promotion_suggestion_id, suggestion_record.funding_split, NEW.who_pays
                USING ERRCODE = 'restrict_violation';
        END IF;

    ELSIF NEW.advice_kind = 'DEMAND_FORECAST' THEN
        SELECT r.run_state INTO forecast_state
            FROM demand_forecasts f
            JOIN demand_forecast_runs r ON r.id = f.forecast_run_id
            WHERE f.id = NEW.demand_forecast_id;
        IF forecast_state <> 'SUCCEEDED' THEN
            RAISE EXCEPTION
                'forecast % comes from a run that is %; only a completed run may be shown to a '
                'host',
                NEW.demand_forecast_id, forecast_state
                USING ERRCODE = 'restrict_violation';
        END IF;

    ELSE
        SELECT item_state INTO checklist_state
            FROM listing_quality_checklist_states WHERE id = NEW.checklist_state_id;
        IF checklist_state <> 'OPEN' THEN
            RAISE EXCEPTION
                'checklist item % is %; a closed item is not an outstanding action',
                NEW.checklist_state_id, checklist_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION host_advice_decision_integrity() RETURNS TRIGGER AS $$
DECLARE
    disclosure_record RECORD;
BEGIN
    SELECT recipient_account_holder_id, disclosed_at, opt_out_offered, override_offered
        INTO disclosure_record
        FROM host_advice_disclosures WHERE id = NEW.disclosure_id;

    IF NEW.decided_at < disclosure_record.disclosed_at THEN
        RAISE EXCEPTION
            'decision at % predates the disclosure at %; a host cannot have answered advice they '
            'had not been shown',
            NEW.decided_at, disclosure_record.disclosed_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.actor_role = 'HOST'
            AND NEW.decided_by IS DISTINCT FROM disclosure_record.recipient_account_holder_id THEN
        RAISE EXCEPTION
            'decision was recorded for % but the advice was disclosed to %; a co-host decision is '
            'recorded as one',
            NEW.decided_by, disclosure_record.recipient_account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.outcome = 'REJECTED' AND NOT disclosure_record.opt_out_offered THEN
        RAISE EXCEPTION
            'disclosure % offered no way to decline; a refusal cannot be recorded against advice '
            'that did not allow one',
            NEW.disclosure_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.outcome = 'MODIFIED' AND NOT disclosure_record.override_offered THEN
        RAISE EXCEPTION
            'disclosure % offered no override', NEW.disclosure_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_host_advice_disclosures_integrity
    BEFORE INSERT ON host_advice_disclosures
    FOR EACH ROW
    EXECUTE FUNCTION host_advice_disclosure_integrity();

CREATE TRIGGER trg_host_advice_decisions_integrity
    BEFORE INSERT ON host_advice_decisions
    FOR EACH ROW
    EXECUTE FUNCTION host_advice_decision_integrity();
--rollback DROP TRIGGER trg_host_advice_decisions_integrity ON host_advice_decisions;
--rollback DROP TRIGGER trg_host_advice_disclosures_integrity ON host_advice_disclosures;
--rollback DROP FUNCTION host_advice_decision_integrity();
--rollback DROP FUNCTION host_advice_disclosure_integrity();

--changeset ninggiangboy:033-24-promotion-suggestion-integrity splitStatements:false
-- A suggestion whose numbers can move after it was shown makes its own disclosure unreadable: the
-- host accepted an estimate of eleven extra bookings and the row now says four, with the digest of
-- what was rendered sitting beside it unchanged. Once the suggestion is decided, the estimate, the
-- cost and the funding are frozen. The accepted promotion must also be the same market's, because
-- a suggestion for one market accepted into another is a discount nobody sized.
CREATE FUNCTION promotion_suggestion_integrity() RETURNS TRIGGER AS $$
DECLARE
    promotion_record RECORD;
    run_record RECORD;
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.decision_state <> 'PENDING' THEN
            RAISE EXCEPTION
                'a promotion suggestion is created PENDING; recording a decision at insert leaves '
                'no disclosure the host could have read'
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.forecast_run_id IS NOT NULL THEN
            SELECT run_state, covers_from, covers_until INTO run_record
                FROM demand_forecast_runs WHERE id = NEW.forecast_run_id;
            IF run_record.run_state <> 'SUCCEEDED' THEN
                RAISE EXCEPTION
                    'forecast run % is % and cannot support a suggestion shown to a host',
                    NEW.forecast_run_id, run_record.run_state
                    USING ERRCODE = 'restrict_violation';
            END IF;
            IF NEW.applies_from < run_record.covers_from
                    OR NEW.applies_until > run_record.covers_until THEN
                RAISE EXCEPTION
                    'suggestion covers [%, %) but its forecast run covers [%, %)',
                    NEW.applies_from, NEW.applies_until,
                    run_record.covers_from, run_record.covers_until
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.decision_state <> 'PENDING' THEN
        old_state := to_jsonb(OLD);
        new_state := to_jsonb(NEW);
        FOREACH permitted IN ARRAY ARRAY['updated_at', 'version'] LOOP
            old_state := old_state - permitted;
            new_state := new_state - permitted;
        END LOOP;
        IF new_state IS DISTINCT FROM old_state THEN
            RAISE EXCEPTION
                'suggestion % was already decided; a decided suggestion is the record of what the '
                'host was shown and agreed to',
                OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.decision_state IS DISTINCT FROM NEW.decision_state
            AND NEW.decision_state = 'ACCEPTED' THEN
        SELECT market_code, status INTO promotion_record
            FROM promotions WHERE id = NEW.accepted_promotion_id;
        IF promotion_record.market_code IS DISTINCT FROM NEW.market_code THEN
            RAISE EXCEPTION
                'suggestion is for market % but the accepted promotion is for market %',
                NEW.market_code, promotion_record.market_code
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF promotion_record.status = 'DRAFT' THEN
            RAISE EXCEPTION
                'promotion % is still a draft; accepting a suggestion means a promotion that '
                'exists for guests to receive',
                NEW.accepted_promotion_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF OLD.decision_state IS DISTINCT FROM NEW.decision_state THEN
        old_state := to_jsonb(OLD);
        new_state := to_jsonb(NEW);
        FOREACH permitted IN ARRAY ARRAY[
            'decision_state', 'accepted_promotion_id', 'updated_at', 'version'
        ] LOOP
            old_state := old_state - permitted;
            new_state := new_state - permitted;
        END LOOP;
        IF new_state IS DISTINCT FROM old_state THEN
            RAISE EXCEPTION
                'the estimate a suggestion was decided on may not change in the same breath as '
                'the decision'
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_promotion_suggestions_integrity
    BEFORE INSERT OR UPDATE ON promotion_suggestions
    FOR EACH ROW
    EXECUTE FUNCTION promotion_suggestion_integrity();
--rollback DROP TRIGGER trg_promotion_suggestions_integrity ON promotion_suggestions;
--rollback DROP FUNCTION promotion_suggestion_integrity();

--changeset ninggiangboy:033-25-bulk-edit-progression splitStatements:false
-- A bulk edit is previewed, applied, and then reports what it did to every target. The scope and
-- the change are frozen once the preview exists, because the digest the host approved describes
-- those and nothing else. Targets may only be written while the request is applying, and the
-- counts on the request are checked against the target rows at commit -- deferred, because the
-- request and its four hundred targets are written in one transaction and neither order of writes
-- should be forced on the service.
CREATE FUNCTION host_bulk_edit_progression() RETURNS TRIGGER AS $$
DECLARE
    old_state JSONB;
    new_state JSONB;
    permitted TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.request_state <> 'DRAFT' THEN
            RAISE EXCEPTION
                'a bulk edit is created DRAFT; inserting one already applied records an outcome '
                'for a preview nobody saw'
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.request_state IN ('APPLIED', 'PARTIALLY_APPLIED', 'FAILED', 'CANCELLED')
            AND OLD.request_state IS DISTINCT FROM NEW.request_state THEN
        RAISE EXCEPTION
            'bulk edit % is %; a finished edit is followed by a new request rather than reopened',
            OLD.id, OLD.request_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.request_state <> 'DRAFT' THEN
        old_state := to_jsonb(OLD);
        new_state := to_jsonb(NEW);
        FOREACH permitted IN ARRAY ARRAY[
            'request_state', 'target_count', 'applied_count', 'skipped_count', 'refused_count',
            'applied_at', 'failure_reason', 'updated_at', 'version'
        ] LOOP
            old_state := old_state - permitted;
            new_state := new_state - permitted;
        END LOOP;
        IF new_state IS DISTINCT FROM old_state THEN
            RAISE EXCEPTION
                'bulk edit % was previewed; the scope and the change the host approved are what '
                'the preview digest describes',
                OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF OLD.request_state IS DISTINCT FROM NEW.request_state THEN
        IF NEW.request_state = 'PREVIEWED' AND OLD.request_state <> 'DRAFT' THEN
            RAISE EXCEPTION 'a bulk edit is previewed from DRAFT, not from %', OLD.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.request_state = 'APPLYING' AND OLD.request_state <> 'PREVIEWED' THEN
            RAISE EXCEPTION 'a bulk edit is applied from PREVIEWED, not from %', OLD.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.request_state IN ('APPLIED', 'PARTIALLY_APPLIED')
                AND OLD.request_state <> 'APPLYING' THEN
            RAISE EXCEPTION
                'a bulk edit reports its outcome from APPLYING, not from %', OLD.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.request_state = 'CANCELLED' AND OLD.request_state NOT IN ('DRAFT', 'PREVIEWED') THEN
            RAISE EXCEPTION
                'a bulk edit that has begun applying is reported, not cancelled'
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION host_bulk_edit_target_window() RETURNS TRIGGER AS $$
DECLARE
    current_state VARCHAR(24);
BEGIN
    SELECT r.request_state INTO current_state
        FROM host_bulk_edit_requests r WHERE r.id = NEW.bulk_edit_request_id;
    IF current_state <> 'APPLYING' THEN
        RAISE EXCEPTION
            'bulk edit % is %; an outcome may only be recorded while the edit is applying',
            NEW.bulk_edit_request_id, current_state
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION host_bulk_edit_totals() RETURNS TRIGGER AS $$
DECLARE
    request_record RECORD;
    applied_rows INTEGER;
    skipped_rows INTEGER;
    refused_rows INTEGER;
    total_rows INTEGER;
    subject_id UUID;
BEGIN
    -- A CASE expression would not do here: plpgsql resolves every branch's record field, and
    -- NEW.bulk_edit_request_id does not exist on the request table.
    IF TG_TABLE_NAME = 'host_bulk_edit_requests' THEN
        subject_id := NEW.id;
    ELSE
        subject_id := NEW.bulk_edit_request_id;
    END IF;

    SELECT request_state, target_count, applied_count, skipped_count, refused_count
        INTO request_record
        FROM host_bulk_edit_requests WHERE id = subject_id;

    IF NOT FOUND OR request_record.request_state NOT IN ('APPLIED', 'PARTIALLY_APPLIED') THEN
        RETURN NULL;
    END IF;

    SELECT count(*) FILTER (WHERE outcome = 'APPLIED'),
           count(*) FILTER (WHERE outcome = 'SKIPPED'),
           count(*) FILTER (WHERE outcome = 'REFUSED'),
           count(*)
        INTO applied_rows, skipped_rows, refused_rows, total_rows
        FROM host_bulk_edit_targets WHERE bulk_edit_request_id = subject_id;

    IF total_rows <> request_record.target_count
            OR applied_rows <> request_record.applied_count
            OR skipped_rows <> request_record.skipped_count
            OR refused_rows <> request_record.refused_count THEN
        RAISE EXCEPTION
            'bulk edit % reports %/%/% of % targets but holds %/%/% of %; the summary a host reads '
            'must be the one the target rows add up to',
            subject_id, request_record.applied_count, request_record.skipped_count,
            request_record.refused_count, request_record.target_count,
            applied_rows, skipped_rows, refused_rows, total_rows
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_host_bulk_edit_requests_progression
    BEFORE INSERT OR UPDATE ON host_bulk_edit_requests
    FOR EACH ROW
    EXECUTE FUNCTION host_bulk_edit_progression();

CREATE TRIGGER trg_host_bulk_edit_targets_window
    BEFORE INSERT ON host_bulk_edit_targets
    FOR EACH ROW
    EXECUTE FUNCTION host_bulk_edit_target_window();

CREATE CONSTRAINT TRIGGER trg_host_bulk_edit_requests_totals
    AFTER UPDATE ON host_bulk_edit_requests
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION host_bulk_edit_totals();

CREATE CONSTRAINT TRIGGER trg_host_bulk_edit_targets_totals
    AFTER INSERT ON host_bulk_edit_targets
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION host_bulk_edit_totals();
--rollback DROP TRIGGER trg_host_bulk_edit_targets_totals ON host_bulk_edit_targets;
--rollback DROP TRIGGER trg_host_bulk_edit_requests_totals ON host_bulk_edit_requests;
--rollback DROP TRIGGER trg_host_bulk_edit_targets_window ON host_bulk_edit_targets;
--rollback DROP TRIGGER trg_host_bulk_edit_requests_progression ON host_bulk_edit_requests;
--rollback DROP FUNCTION host_bulk_edit_totals();
--rollback DROP FUNCTION host_bulk_edit_target_window();
--rollback DROP FUNCTION host_bulk_edit_progression();

--changeset ninggiangboy:033-26-checklist-state-integrity splitStatements:false
-- An item a host may not dismiss is one the platform is prepared to defend, and the place that has
-- to hold is the write that dismisses it. The rest is the pairing that makes "satisfied" mean
-- something: the platform looked again, and it looked after the host acted, not before.
CREATE FUNCTION listing_quality_checklist_state_integrity() RETURNS TRIGGER AS $$
DECLARE
    item_record RECORD;
BEGIN
    SELECT i.host_dismissible, i.blocks_publication, v.status
        INTO item_record
        FROM listing_quality_checklist_items i
        JOIN listing_quality_checklist_versions v ON v.id = i.checklist_version_id
        WHERE i.id = NEW.checklist_item_id;

    IF TG_OP = 'INSERT' AND item_record.status <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'checklist item % belongs to a checklist that is %; a host is measured against the '
            'published checklist, not a draft or a retired one',
            NEW.checklist_item_id, item_record.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.item_state = 'DISMISSED' AND NOT item_record.host_dismissible THEN
        RAISE EXCEPTION
            'checklist item % may not be dismissed by a host', NEW.checklist_item_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.item_state = 'SATISFIED' AND NEW.verified_at < NEW.satisfied_at THEN
        RAISE EXCEPTION
            'the item was verified at % and satisfied at %; a verification that predates the fix '
            'verified the problem, not the correction',
            NEW.verified_at, NEW.satisfied_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.item_state IS DISTINCT FROM NEW.item_state
            AND OLD.item_state = 'SATISFIED' AND NEW.item_state = 'OPEN'
            AND NEW.detected_at <= OLD.satisfied_at THEN
        RAISE EXCEPTION
            'item % was satisfied at % and is being reopened on a detection from %; reopening '
            'needs a fresh observation, not the one that was already resolved',
            OLD.id, OLD.satisfied_at, NEW.detected_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Freezing the checklist against edits is only half of it. The items are a separate table, so a
-- ninth item could be appended to a published checklist and every listing measured against it would
-- silently acquire an outstanding action nobody versioned -- the same append-to-a-frozen-parent
-- shape migrations 022, 027 and 030 each found in their own tables. Membership is fixed at
-- publication; a different set of items is a different checklist version.
CREATE FUNCTION listing_quality_checklist_item_seal() RETURNS TRIGGER AS $$
DECLARE
    version_status VARCHAR(16);
BEGIN
    IF TG_OP = 'DELETE' THEN
        SELECT status INTO version_status
            FROM listing_quality_checklist_versions WHERE id = OLD.checklist_version_id;
    ELSE
        SELECT status INTO version_status
            FROM listing_quality_checklist_versions WHERE id = NEW.checklist_version_id;
    END IF;

    -- A version being dropped takes its items with it, and the version's own freeze trigger is
    -- what refuses that; there is nothing for this one to add.
    IF NOT FOUND THEN
        RETURN COALESCE(NEW, OLD);
    END IF;

    IF version_status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'checklist version % is %; its items are what listings were measured against, so a '
            'different set of items is a new version rather than an edit to this one',
            COALESCE(NEW.checklist_version_id, OLD.checklist_version_id), version_status
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_checklist_items_seal
    BEFORE INSERT OR DELETE ON listing_quality_checklist_items
    FOR EACH ROW
    EXECUTE FUNCTION listing_quality_checklist_item_seal();

CREATE TRIGGER trg_checklist_states_integrity
    BEFORE INSERT OR UPDATE ON listing_quality_checklist_states
    FOR EACH ROW
    EXECUTE FUNCTION listing_quality_checklist_state_integrity();
--rollback DROP TRIGGER trg_checklist_states_integrity ON listing_quality_checklist_states;
--rollback DROP FUNCTION listing_quality_checklist_state_integrity();
--rollback DROP TRIGGER trg_checklist_items_seal ON listing_quality_checklist_items;
--rollback DROP FUNCTION listing_quality_checklist_item_seal();
