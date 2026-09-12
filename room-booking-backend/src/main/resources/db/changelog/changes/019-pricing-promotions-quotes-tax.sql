--liquibase formatted sql

-- A price is not a number, it is a decision, and this migration exists so that every price the
-- platform ever showed can be explained after the fact.
--
-- Three things force that shape:
--
--   A guest who booked last March is entitled to the terms that were in force last March. If rules
--   were mutable rows, re-reading them today would answer a different question than the one the
--   guest actually agreed to. So rules are published as immutable versions and every priced row
--   cites the version it used.
--
--   A quote is an offer, and an offer that can silently change is not an offer. Quotes are therefore
--   written once with an expiry and a calculation hash; re-pricing produces a new quote rather than
--   editing an old one.
--
--   Tax liability is not ours to round off. Who is liable, who remits, and under whose rule is
--   recorded per line, because a single stay can mix a marketplace-liable lodging tax with a
--   supplier-liable service tax in the same night.
--
-- Money is integer minor units everywhere, never NUMERIC and never floating point. Rates and
-- percentages are bounded NUMERIC because they are ratios, not money.
--
-- Line amounts are unsigned with an explicit direction. A negative amount is ambiguous -- it can
-- mean a discount, a refund, a correction, or a sign error -- and ambiguity in money is a defect.
--
-- Note on the shape: docs/features/dynamic-pricing-and-settlement.md keys pricing on listing_id,
-- which was correct when a listing was the sellable thing. Migration 016 moved that authority to
-- accommodation_type and migration 018 followed it for inventory; pricing follows it here for the
-- same reason. A listing is a presentation and cannot carry a price that inventory does not have.

--changeset ninggiangboy:019-01-host-pricing-settings
-- The host's standing instruction to the pricing engine: the bounds it may move within and how much
-- freedom it has. Effective-dated, because lowering a floor today must not rewrite why a price was
-- chosen last week.
CREATE TABLE host_pricing_settings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id       UUID NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    strategy                    VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    automation_state            VARCHAR(16) NOT NULL DEFAULT 'OFF',
    base_amount_minor           BIGINT NOT NULL,
    floor_amount_minor          BIGINT NOT NULL,
    ceiling_amount_minor        BIGINT,
    target_net_amount_minor     BIGINT,
    minimum_net_amount_minor    BIGINT,
    occupancy_target_percent    NUMERIC(5,2),
    promotion_funding_cap_percent NUMERIC(5,2),
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_pricing_settings_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_host_pricing_settings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_host_pricing_settings_strategy CHECK (
        strategy IN ('MANUAL', 'RULE_BASED', 'RECOMMENDED', 'AUTOMATED')
    ),
    -- OFF means the engine may not write prices, SUGGEST means it may only propose, APPLY means it
    -- may set them. The distinction is the host's consent and must be explicit.
    CONSTRAINT ck_host_pricing_settings_automation CHECK (
        automation_state IN ('OFF', 'SUGGEST', 'APPLY')
    ),
    CONSTRAINT ck_host_pricing_settings_amounts CHECK (
        base_amount_minor > 0
        AND floor_amount_minor > 0
        AND (ceiling_amount_minor IS NULL OR ceiling_amount_minor > 0)
        AND (target_net_amount_minor IS NULL OR target_net_amount_minor >= 0)
        AND (minimum_net_amount_minor IS NULL OR minimum_net_amount_minor >= 0)
    ),
    -- A floor above the ceiling is an empty range: the engine would have no legal price to pick and
    -- would either fail every night or silently ignore one of the two bounds.
    CONSTRAINT ck_host_pricing_settings_bounds CHECK (
        ceiling_amount_minor IS NULL OR ceiling_amount_minor >= floor_amount_minor
    ),
    CONSTRAINT ck_host_pricing_settings_base_within_bounds CHECK (
        base_amount_minor >= floor_amount_minor
        AND (ceiling_amount_minor IS NULL OR base_amount_minor <= ceiling_amount_minor)
    ),
    CONSTRAINT ck_host_pricing_settings_percentages CHECK (
        (occupancy_target_percent IS NULL OR occupancy_target_percent BETWEEN 0 AND 100)
        AND (promotion_funding_cap_percent IS NULL
             OR promotion_funding_cap_percent BETWEEN 0 AND 100)
    ),
    CONSTRAINT ck_host_pricing_settings_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_host_pricing_settings_version CHECK (version >= 0),
    -- One set of instructions is in force per accommodation type at any instant. Overlapping
    -- settings would let two pricing runs a second apart obey different floors.
    CONSTRAINT ex_host_pricing_settings_no_overlap EXCLUDE USING gist (
        accommodation_type_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);

CREATE INDEX idx_host_pricing_settings_resolution
    ON host_pricing_settings (accommodation_type_id, effective_from DESC);
--rollback DROP TABLE host_pricing_settings;

--changeset ninggiangboy:019-02-price-rules
-- The rule is the stable identity that survives editing; the version is what pricing actually reads.
-- Separating them is what lets a host say "my weekly discount" across years of revisions without
-- rewriting what any past booking was charged.
CREATE TABLE price_rules (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope                   VARCHAR(24) NOT NULL,
    market_code             VARCHAR(2),
    account_holder_id       UUID,
    accommodation_type_id   UUID,
    rate_plan_id            UUID,
    rule_type               VARCHAR(32) NOT NULL,
    display_name            VARCHAR(160) NOT NULL,
    priority                INTEGER NOT NULL DEFAULT 100,
    compatibility_group     VARCHAR(48),
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_price_rules_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_price_rules_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_price_rules_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_price_rules_rate_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id),
    CONSTRAINT ck_price_rules_scope CHECK (
        scope IN ('PLATFORM', 'MARKET', 'HOST', 'ACCOMMODATION_TYPE', 'RATE_PLAN')
    ),
    CONSTRAINT ck_price_rules_rule_type CHECK (
        rule_type IN (
            'LENGTH_OF_STAY_DISCOUNT', 'EARLY_BIRD', 'LAST_MINUTE', 'ORPHAN_NIGHT',
            'DAY_OF_WEEK', 'SEASONAL', 'OCCUPANCY_RESPONSE', 'GAP_FILL',
            'MINIMUM_MARGIN_GUARD', 'SURCHARGE'
        )
    ),
    CONSTRAINT ck_price_rules_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'RETIRED')),
    CONSTRAINT ck_price_rules_priority CHECK (priority >= 0),
    -- A rule must name exactly the subject its scope claims. A MARKET rule without a market would
    -- apply everywhere; a HOST rule carrying a market code would apply under two different owners.
    CONSTRAINT ck_price_rules_scope_subject CHECK (
        (scope = 'PLATFORM' AND market_code IS NULL AND account_holder_id IS NULL
            AND accommodation_type_id IS NULL AND rate_plan_id IS NULL)
        OR (scope = 'MARKET' AND market_code IS NOT NULL AND account_holder_id IS NULL
            AND accommodation_type_id IS NULL AND rate_plan_id IS NULL)
        OR (scope = 'HOST' AND account_holder_id IS NOT NULL
            AND accommodation_type_id IS NULL AND rate_plan_id IS NULL)
        OR (scope = 'ACCOMMODATION_TYPE' AND accommodation_type_id IS NOT NULL
            AND rate_plan_id IS NULL)
        OR (scope = 'RATE_PLAN' AND rate_plan_id IS NOT NULL)
    ),
    CONSTRAINT ck_price_rules_version CHECK (version >= 0)
);

CREATE INDEX idx_price_rules_resolution ON price_rules (scope, status, priority);
CREATE INDEX idx_price_rules_type ON price_rules (accommodation_type_id)
    WHERE accommodation_type_id IS NOT NULL;
--rollback DROP TABLE price_rules;

--changeset ninggiangboy:019-03-price-rule-versions
-- The immutable payload. Once published, the condition and action are frozen: a priced night cites
-- this row, and changing it after the fact would rewrite history rather than record a change.
CREATE TABLE price_rule_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    price_rule_id       UUID NOT NULL,
    version_number      INTEGER NOT NULL,
    condition_payload   JSONB NOT NULL,
    action_payload      JSONB NOT NULL,
    content_digest      CHAR(64) NOT NULL,
    publication_state   VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from      TIMESTAMPTZ,
    effective_until     TIMESTAMPTZ,
    authored_by         UUID,
    approved_by         UUID,
    published_at        TIMESTAMPTZ,
    retired_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_price_rule_versions_number UNIQUE (price_rule_id, version_number),
    CONSTRAINT fk_price_rule_versions_rule FOREIGN KEY (price_rule_id) REFERENCES price_rules (id),
    CONSTRAINT fk_price_rule_versions_author FOREIGN KEY (authored_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_price_rule_versions_approver FOREIGN KEY (approved_by)
        REFERENCES account_holders (id),
    CONSTRAINT ck_price_rule_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_price_rule_versions_payloads CHECK (
        jsonb_typeof(condition_payload) = 'object' AND jsonb_typeof(action_payload) = 'object'
    ),
    CONSTRAINT ck_price_rule_versions_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_price_rule_versions_state CHECK (
        publication_state IN ('DRAFT', 'PUBLISHED', 'RETIRED')
    ),
    -- A published version must say when it took effect and who approved it. Without both, a priced
    -- night cannot be defended: nobody can show the rule was in force or that anyone agreed to it.
    CONSTRAINT ck_price_rule_versions_publication CHECK (
        publication_state = 'DRAFT'
        OR (published_at IS NOT NULL AND effective_from IS NOT NULL AND approved_by IS NOT NULL)
    ),
    CONSTRAINT ck_price_rule_versions_retirement CHECK (
        (publication_state = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_price_rule_versions_span CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    )
);

CREATE INDEX idx_price_rule_versions_live ON price_rule_versions (price_rule_id, effective_from DESC)
    WHERE publication_state = 'PUBLISHED';
--rollback DROP TABLE price_rule_versions;

--changeset ninggiangboy:019-04-price-rule-version-immutability splitStatements:false
-- Immutability enforced where it cannot be forgotten. A published version is evidence; the only
-- legal transition is publication state moving forward to RETIRED, which ends its life without
-- altering what it said.
CREATE FUNCTION price_rule_versions_freeze_published() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.publication_state = 'PUBLISHED' THEN
        IF NEW.condition_payload IS DISTINCT FROM OLD.condition_payload
            OR NEW.action_payload IS DISTINCT FROM OLD.action_payload
            OR NEW.content_digest IS DISTINCT FROM OLD.content_digest
            OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
            OR NEW.version_number IS DISTINCT FROM OLD.version_number
            OR NEW.price_rule_id IS DISTINCT FROM OLD.price_rule_id
        THEN
            RAISE EXCEPTION
                'price_rule_versions %: a published rule version is immutable', OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.publication_state = 'DRAFT' THEN
            RAISE EXCEPTION
                'price_rule_versions %: a published rule version cannot return to draft', OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_price_rule_versions_freeze
    BEFORE UPDATE ON price_rule_versions
    FOR EACH ROW EXECUTE FUNCTION price_rule_versions_freeze_published();

CREATE FUNCTION price_rule_versions_block_delete() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.publication_state <> 'DRAFT' THEN
        RAISE EXCEPTION
            'price_rule_versions %: a published rule version cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_price_rule_versions_no_delete
    BEFORE DELETE ON price_rule_versions
    FOR EACH ROW EXECUTE FUNCTION price_rule_versions_block_delete();
--rollback DROP TRIGGER trg_price_rule_versions_no_delete ON price_rule_versions;
--rollback DROP FUNCTION price_rule_versions_block_delete();
--rollback DROP TRIGGER trg_price_rule_versions_freeze ON price_rule_versions;
--rollback DROP FUNCTION price_rule_versions_freeze_published();

--changeset ninggiangboy:019-05-manual-price-overrides
-- The host's direct instruction for specific nights, which outranks every rule. It is recorded
-- rather than applied in place so that "why was this night 3,000,000 VND" has an answer naming a
-- person, and so that withdrawing the override restores the computed price instead of guessing one.
CREATE TABLE manual_price_overrides (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id   UUID NOT NULL,
    stay_range              DATERANGE NOT NULL,
    nightly_amount_minor    BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    promotion_behaviour     VARCHAR(24) NOT NULL DEFAULT 'ALLOW_PROMOTIONS',
    status                  VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    actor_account_holder_id UUID NOT NULL,
    reason                  VARCHAR(255),
    withdrawn_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_manual_price_overrides_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_manual_price_overrides_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_manual_price_overrides_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_manual_price_overrides_amount CHECK (nightly_amount_minor > 0),
    CONSTRAINT ck_manual_price_overrides_promotion CHECK (
        promotion_behaviour IN ('ALLOW_PROMOTIONS', 'EXCLUDE_PROMOTIONS')
    ),
    CONSTRAINT ck_manual_price_overrides_status CHECK (
        status IN ('ACTIVE', 'WITHDRAWN', 'SUPERSEDED')
    ),
    CONSTRAINT ck_manual_price_overrides_withdrawal CHECK (
        (status = 'ACTIVE') = (withdrawn_at IS NULL)
    ),
    -- Half-open like every other stay range in the schema: the upper bound is the first night the
    -- override does not cover.
    CONSTRAINT ck_manual_price_overrides_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_manual_price_overrides_version CHECK (version >= 0),
    -- Two active overrides on the same night would give one date two host-declared prices with no
    -- rule for choosing between them. Withdrawing the first is the way to replace it.
    CONSTRAINT ex_manual_price_overrides_no_overlap EXCLUDE USING gist (
        accommodation_type_id WITH =,
        stay_range WITH &&
    ) WHERE (status = 'ACTIVE')
);
--rollback DROP TABLE manual_price_overrides;

--changeset ninggiangboy:019-06-price-recommendations
-- What the model proposed, with the inputs that produced it. Kept separate from the price actually
-- charged because a recommendation is advice: the host may take it, ignore it, or override it, and
-- evaluating the model later requires knowing which of those happened.
CREATE TABLE price_recommendations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id       UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    current_amount_minor        BIGINT,
    recommended_amount_minor    BIGINT NOT NULL,
    predicted_booking_probability NUMERIC(5,4),
    predicted_occupancy_percent NUMERIC(5,2),
    confidence                  VARCHAR(16),
    binding_constraint          VARCHAR(32),
    reason_codes                JSONB,
    feature_set_version         VARCHAR(64),
    model_version               VARCHAR(64),
    optimizer_version           VARCHAR(64),
    decision_state              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_at                  TIMESTAMPTZ,
    generated_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_price_recommendations_generation
        UNIQUE (accommodation_type_id, stay_date, generated_at),
    CONSTRAINT fk_price_recommendations_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT ck_price_recommendations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_price_recommendations_amounts CHECK (
        recommended_amount_minor > 0
        AND (current_amount_minor IS NULL OR current_amount_minor >= 0)
    ),
    CONSTRAINT ck_price_recommendations_predictions CHECK (
        (predicted_booking_probability IS NULL OR predicted_booking_probability BETWEEN 0 AND 1)
        AND (predicted_occupancy_percent IS NULL OR predicted_occupancy_percent BETWEEN 0 AND 100)
    ),
    CONSTRAINT ck_price_recommendations_confidence CHECK (
        confidence IS NULL OR confidence IN ('LOW', 'MEDIUM', 'HIGH')
    ),
    CONSTRAINT ck_price_recommendations_reasons CHECK (
        reason_codes IS NULL OR jsonb_typeof(reason_codes) = 'array'
    ),
    CONSTRAINT ck_price_recommendations_state CHECK (
        decision_state IN ('PENDING', 'ACCEPTED', 'REJECTED', 'OVERRIDDEN', 'EXPIRED')
    ),
    CONSTRAINT ck_price_recommendations_decision CHECK (
        (decision_state = 'PENDING') = (decided_at IS NULL)
    ),
    -- Advice with no shelf life is advice about a demand picture that no longer exists.
    CONSTRAINT ck_price_recommendations_expiry CHECK (expires_at > generated_at)
);

CREATE INDEX idx_price_recommendations_open
    ON price_recommendations (accommodation_type_id, stay_date)
    WHERE decision_state = 'PENDING';
--rollback DROP TABLE price_recommendations;

--changeset ninggiangboy:019-07-daily-price-components
-- The materialized answer for one night: what the guest would be shown, and the arithmetic that got
-- there. Search and the calendar read this table rather than re-running the engine, so it is the
-- table that must never disagree with what a quote later charges.
--
-- The component breakdown is JSONB because it is an immutable record of one completed calculation,
-- never a filter target: queries ask for the final price on a date, not for every night where a
-- particular component happened to apply. The filterable facts stay relational.
CREATE TABLE daily_price_components (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accommodation_type_id       UUID NOT NULL,
    rate_plan_id                UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    price_version               BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    base_amount_minor           BIGINT NOT NULL,
    adjustment_total_minor      BIGINT NOT NULL DEFAULT 0,
    public_amount_minor         BIGINT NOT NULL,
    components                  JSONB NOT NULL,
    source_decision             VARCHAR(24) NOT NULL,
    manual_override_id          UUID,
    price_recommendation_id     UUID,
    is_current                  BOOLEAN NOT NULL DEFAULT true,
    materialized_at             TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_daily_price_components_version
        UNIQUE (accommodation_type_id, rate_plan_id, stay_date, price_version),
    CONSTRAINT fk_daily_price_components_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_daily_price_components_rate_plan FOREIGN KEY (rate_plan_id)
        REFERENCES rate_plans (id),
    CONSTRAINT fk_daily_price_components_override FOREIGN KEY (manual_override_id)
        REFERENCES manual_price_overrides (id),
    CONSTRAINT fk_daily_price_components_recommendation FOREIGN KEY (price_recommendation_id)
        REFERENCES price_recommendations (id),
    CONSTRAINT ck_daily_price_components_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_daily_price_components_base CHECK (base_amount_minor > 0),
    CONSTRAINT ck_daily_price_components_public CHECK (public_amount_minor > 0),
    -- The breakdown has to add up. If it does not, either the shown price or the explanation is
    -- wrong, and there is no way to tell which -- so neither may be stored.
    CONSTRAINT ck_daily_price_components_arithmetic CHECK (
        public_amount_minor = base_amount_minor + adjustment_total_minor
    ),
    CONSTRAINT ck_daily_price_components_breakdown CHECK (
        jsonb_typeof(components) = 'array'
    ),
    CONSTRAINT ck_daily_price_components_source CHECK (
        source_decision IN ('BASE', 'RULE_ENGINE', 'RECOMMENDATION', 'MANUAL_OVERRIDE')
    ),
    -- A row that claims a manual origin must name the override, and one that does not must not
    -- borrow its authority.
    CONSTRAINT ck_daily_price_components_override_link CHECK (
        (source_decision = 'MANUAL_OVERRIDE') = (manual_override_id IS NOT NULL)
    ),
    CONSTRAINT ck_daily_price_components_recommendation_link CHECK (
        source_decision = 'RECOMMENDATION' OR price_recommendation_id IS NULL
    ),
    CONSTRAINT ck_daily_price_components_price_version CHECK (price_version >= 0)
);

-- Exactly one current price per night and offer. Two would make the price shown depend on which row
-- the reader happened to find first.
CREATE UNIQUE INDEX uk_daily_price_components_one_current
    ON daily_price_components (accommodation_type_id, rate_plan_id, stay_date)
    WHERE is_current = true;

CREATE INDEX idx_daily_price_components_calendar
    ON daily_price_components (accommodation_type_id, stay_date)
    WHERE is_current = true;
--rollback DROP TABLE daily_price_components;

--changeset ninggiangboy:019-08-promotions
-- A promotion is somebody's money. The funding split is not cosmetic: it decides who absorbs the
-- discount when the booking settles, so it is recorded on the version the guest actually redeemed
-- rather than read from whatever the campaign says today.
CREATE TABLE promotions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_code             VARCHAR(2),
    owner_type              VARCHAR(16) NOT NULL,
    owner_account_holder_id UUID,
    promotion_code          VARCHAR(48),
    display_name            VARCHAR(160) NOT NULL,
    purpose                 VARCHAR(32) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    budget_total_minor      BIGINT,
    budget_currency         VARCHAR(3),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_promotions_code UNIQUE (promotion_code),
    CONSTRAINT fk_promotions_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_promotions_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_promotions_owner_type CHECK (owner_type IN ('PLATFORM', 'HOST')),
    -- A host promotion is funded from a host's account and must name it. A platform promotion is
    -- funded centrally and must not attribute itself to a host who never agreed to pay.
    CONSTRAINT ck_promotions_owner CHECK (
        (owner_type = 'HOST') = (owner_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_promotions_purpose CHECK (
        purpose IN (
            'ACQUISITION', 'RETENTION', 'OCCUPANCY_FILL', 'LAUNCH', 'SEASONAL',
            'SERVICE_RECOVERY', 'PARTNERSHIP'
        )
    ),
    CONSTRAINT ck_promotions_status CHECK (
        status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'EXHAUSTED', 'ENDED')
    ),
    -- A budget is an amount of a currency; half of that pair is meaningless on its own.
    CONSTRAINT ck_promotions_budget CHECK (
        (budget_total_minor IS NULL) = (budget_currency IS NULL)
    ),
    CONSTRAINT ck_promotions_budget_amount CHECK (
        budget_total_minor IS NULL OR budget_total_minor > 0
    ),
    CONSTRAINT ck_promotions_budget_currency CHECK (
        budget_currency IS NULL OR budget_currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_promotions_version CHECK (version >= 0)
);
--rollback DROP TABLE promotions;

--changeset ninggiangboy:019-09-promotion-versions
-- The frozen terms. A redemption cites a version, so tightening eligibility tomorrow cannot
-- retroactively invalidate a discount a guest already received.
CREATE TABLE promotion_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_id                UUID NOT NULL,
    version_number              INTEGER NOT NULL,
    eligibility_payload         JSONB NOT NULL,
    benefit_type                VARCHAR(24) NOT NULL,
    benefit_percent             NUMERIC(5,2),
    benefit_amount_minor        BIGINT,
    benefit_currency            VARCHAR(3),
    maximum_benefit_minor       BIGINT,
    host_funded_percent         NUMERIC(5,2) NOT NULL DEFAULT 0,
    stacking_group              VARCHAR(48),
    is_stackable                BOOLEAN NOT NULL DEFAULT false,
    tax_treatment               VARCHAR(24) NOT NULL DEFAULT 'REDUCES_TAXABLE_BASE',
    booking_window_from         TIMESTAMPTZ,
    booking_window_until        TIMESTAMPTZ,
    stay_window                 DATERANGE,
    per_guest_redemption_limit  INTEGER,
    total_redemption_limit      INTEGER,
    content_digest              CHAR(64) NOT NULL,
    publication_state           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at                TIMESTAMPTZ,
    approved_by                 UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_promotion_versions_number UNIQUE (promotion_id, version_number),
    CONSTRAINT fk_promotion_versions_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (id),
    CONSTRAINT fk_promotion_versions_approver FOREIGN KEY (approved_by)
        REFERENCES account_holders (id),
    CONSTRAINT ck_promotion_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_promotion_versions_eligibility CHECK (
        jsonb_typeof(eligibility_payload) = 'object'
    ),
    CONSTRAINT ck_promotion_versions_benefit_type CHECK (
        benefit_type IN ('PERCENT_OFF', 'FIXED_AMOUNT_OFF', 'FREE_NIGHTS', 'FEE_WAIVER')
    ),
    -- Each benefit shape carries exactly the parameters it needs. A percentage benefit with an
    -- amount, or an amount benefit with no currency, cannot be evaluated deterministically.
    CONSTRAINT ck_promotion_versions_benefit CHECK (
        (benefit_type = 'PERCENT_OFF'
            AND benefit_percent IS NOT NULL
            AND benefit_amount_minor IS NULL)
        OR (benefit_type = 'FIXED_AMOUNT_OFF'
            AND benefit_amount_minor IS NOT NULL
            AND benefit_currency IS NOT NULL
            AND benefit_percent IS NULL)
        OR (benefit_type IN ('FREE_NIGHTS', 'FEE_WAIVER')
            AND benefit_percent IS NULL
            AND benefit_amount_minor IS NULL)
    ),
    CONSTRAINT ck_promotion_versions_percent CHECK (
        benefit_percent IS NULL OR benefit_percent > 0 AND benefit_percent <= 100
    ),
    CONSTRAINT ck_promotion_versions_amount CHECK (
        (benefit_amount_minor IS NULL OR benefit_amount_minor > 0)
        AND (maximum_benefit_minor IS NULL OR maximum_benefit_minor > 0)
    ),
    CONSTRAINT ck_promotion_versions_currency CHECK (
        benefit_currency IS NULL OR benefit_currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_promotion_versions_funding CHECK (
        host_funded_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_promotion_versions_tax CHECK (
        tax_treatment IN ('REDUCES_TAXABLE_BASE', 'POST_TAX_REBATE', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_promotion_versions_booking_window CHECK (
        booking_window_until IS NULL OR booking_window_from IS NULL
        OR booking_window_until > booking_window_from
    ),
    CONSTRAINT ck_promotion_versions_stay_window CHECK (
        stay_window IS NULL
        OR (NOT isempty(stay_window) AND lower_inc(stay_window) AND NOT upper_inc(stay_window))
    ),
    CONSTRAINT ck_promotion_versions_limits CHECK (
        (per_guest_redemption_limit IS NULL OR per_guest_redemption_limit > 0)
        AND (total_redemption_limit IS NULL OR total_redemption_limit > 0)
    ),
    CONSTRAINT ck_promotion_versions_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_promotion_versions_state CHECK (
        publication_state IN ('DRAFT', 'PUBLISHED', 'RETIRED')
    ),
    CONSTRAINT ck_promotion_versions_publication CHECK (
        publication_state = 'DRAFT' OR (published_at IS NOT NULL AND approved_by IS NOT NULL)
    ),
    -- A stacking group only means something for a benefit that is allowed to stack.
    CONSTRAINT ck_promotion_versions_stacking CHECK (
        is_stackable OR stacking_group IS NULL
    )
);
--rollback DROP TABLE promotion_versions;

--changeset ninggiangboy:019-10-promotion-assignments
-- Who was offered what, decided before the guest ever priced a trip. Separating assignment from
-- redemption is what makes a promotion measurable: the guests who were eligible and did not book are
-- as much a part of the result as those who did.
CREATE TABLE promotion_assignments (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_version_id        UUID NOT NULL,
    guest_account_holder_id     UUID,
    anonymous_unit_key          VARCHAR(128),
    experiment_key              VARCHAR(64),
    experiment_arm              VARCHAR(64),
    eligibility_snapshot        JSONB,
    assigned_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_promotion_assignments_version FOREIGN KEY (promotion_version_id)
        REFERENCES promotion_versions (id),
    CONSTRAINT fk_promotion_assignments_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    -- An assignment is addressed either to a known account or to an anonymous unit, never to both
    -- and never to neither: an assignment nobody can claim is not an assignment.
    CONSTRAINT ck_promotion_assignments_subject CHECK (
        (guest_account_holder_id IS NULL) <> (anonymous_unit_key IS NULL)
    ),
    CONSTRAINT ck_promotion_assignments_experiment CHECK (
        (experiment_key IS NULL) = (experiment_arm IS NULL)
    ),
    CONSTRAINT ck_promotion_assignments_snapshot CHECK (
        eligibility_snapshot IS NULL OR jsonb_typeof(eligibility_snapshot) = 'object'
    ),
    CONSTRAINT ck_promotion_assignments_expiry CHECK (
        expires_at IS NULL OR expires_at > assigned_at
    )
);

-- One standing assignment per subject and version. Re-assigning the same guest to the same terms
-- twice would double-count both the offer and its cost.
CREATE UNIQUE INDEX uk_promotion_assignments_guest
    ON promotion_assignments (promotion_version_id, guest_account_holder_id)
    WHERE guest_account_holder_id IS NOT NULL;

CREATE UNIQUE INDEX uk_promotion_assignments_anonymous
    ON promotion_assignments (promotion_version_id, anonymous_unit_key)
    WHERE anonymous_unit_key IS NOT NULL;
--rollback DROP TABLE promotion_assignments;

--changeset ninggiangboy:019-11-quotes
-- The offer. Everything a guest was told about the price of a trip at one instant, frozen, with an
-- expiry and a hash of the inputs that produced it.
--
-- It is written once and never re-priced in place. If anything that fed it changes -- a rule, a tax
-- version, availability, the guest's party size -- the answer is a new quote, because an offer that
-- can change between being shown and being accepted is not an offer the guest agreed to.
--
-- The idempotency key is what makes a retried pricing request return the same offer instead of
-- minting a second one; a guest who double-taps must not see two different prices.
CREATE TABLE quotes (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                       VARCHAR(32) NOT NULL,
    idempotency_key                 VARCHAR(128) NOT NULL,
    guest_account_holder_id         UUID,
    anonymous_unit_key              VARCHAR(128),
    listing_id                      UUID NOT NULL,
    accommodation_type_id           UUID NOT NULL,
    rate_plan_id                    UUID NOT NULL,
    market_code                     VARCHAR(2) NOT NULL,
    inventory_hold_id               UUID,
    stay_range                      DATERANGE NOT NULL,
    adult_count                     SMALLINT NOT NULL DEFAULT 1,
    child_count                     SMALLINT NOT NULL DEFAULT 0,
    infant_count                    SMALLINT NOT NULL DEFAULT 0,
    unit_quantity                   SMALLINT NOT NULL DEFAULT 1,
    currency                        VARCHAR(3) NOT NULL,
    accommodation_amount_minor      BIGINT NOT NULL,
    discount_amount_minor           BIGINT NOT NULL DEFAULT 0,
    fee_amount_minor                BIGINT NOT NULL DEFAULT 0,
    tax_amount_minor                BIGINT NOT NULL DEFAULT 0,
    total_amount_minor              BIGINT NOT NULL,
    host_payout_estimate_minor      BIGINT,
    pricing_policy_version          VARCHAR(64),
    tax_content_version             VARCHAR(64),
    terms_version                   VARCHAR(64),
    calculation_hash                CHAR(64) NOT NULL,
    status                          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    accepted_at                     TIMESTAMPTZ,
    superseded_by                   UUID,
    expires_at                      TIMESTAMPTZ NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_quotes_public_id UNIQUE (public_id),
    CONSTRAINT uk_quotes_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_quotes_guest FOREIGN KEY (guest_account_holder_id) REFERENCES account_holders (id),
    CONSTRAINT fk_quotes_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_quotes_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_quotes_rate_plan FOREIGN KEY (rate_plan_id) REFERENCES rate_plans (id),
    CONSTRAINT fk_quotes_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_quotes_hold FOREIGN KEY (inventory_hold_id) REFERENCES inventory_holds (id),
    CONSTRAINT fk_quotes_superseded FOREIGN KEY (superseded_by) REFERENCES quotes (id),
    CONSTRAINT ck_quotes_subject CHECK (
        (guest_account_holder_id IS NULL) <> (anonymous_unit_key IS NULL)
    ),
    CONSTRAINT ck_quotes_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_quotes_party CHECK (
        adult_count >= 1 AND child_count >= 0 AND infant_count >= 0 AND unit_quantity >= 1
    ),
    -- Unsigned components with fixed roles. A discount is subtracted because it is a discount, not
    -- because somebody stored it negative.
    CONSTRAINT ck_quotes_amounts CHECK (
        accommodation_amount_minor >= 0
        AND discount_amount_minor >= 0
        AND fee_amount_minor >= 0
        AND tax_amount_minor >= 0
        AND total_amount_minor >= 0
        AND (host_payout_estimate_minor IS NULL OR host_payout_estimate_minor >= 0)
    ),
    -- The summary must equal its parts. A quote whose total does not reconcile is a quote that will
    -- charge one number and explain another.
    CONSTRAINT ck_quotes_total CHECK (
        total_amount_minor = accommodation_amount_minor
            - discount_amount_minor + fee_amount_minor + tax_amount_minor
    ),
    -- A discount cannot exceed what is being discounted.
    CONSTRAINT ck_quotes_discount_bound CHECK (
        discount_amount_minor <= accommodation_amount_minor + fee_amount_minor
    ),
    CONSTRAINT ck_quotes_range CHECK (
        NOT isempty(stay_range) AND lower_inc(stay_range) AND NOT upper_inc(stay_range)
    ),
    CONSTRAINT ck_quotes_hash CHECK (calculation_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_quotes_status CHECK (
        status IN ('OPEN', 'ACCEPTED', 'EXPIRED', 'SUPERSEDED', 'VOID')
    ),
    CONSTRAINT ck_quotes_acceptance CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL)),
    CONSTRAINT ck_quotes_supersession CHECK (
        (status = 'SUPERSEDED') = (superseded_by IS NOT NULL)
    ),
    -- An offer that expires at or before the moment it was made was never an offer.
    CONSTRAINT ck_quotes_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_quotes_version CHECK (version >= 0)
);

CREATE INDEX idx_quotes_guest ON quotes (guest_account_holder_id, created_at DESC)
    WHERE guest_account_holder_id IS NOT NULL;
CREATE INDEX idx_quotes_open ON quotes (expires_at) WHERE status = 'OPEN';
--rollback DROP TABLE quotes;

--changeset ninggiangboy:019-12-quote-nights
-- One row per night, so a guest asking "why is Friday more expensive" gets an answer, and so a later
-- date-level change (a cancellation refunding two of five nights) has per-night amounts to work from.
CREATE TABLE quote_nights (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id                    UUID NOT NULL,
    stay_date                   DATE NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    base_amount_minor           BIGINT NOT NULL,
    adjustment_total_minor      BIGINT NOT NULL DEFAULT 0,
    net_amount_minor            BIGINT NOT NULL,
    daily_price_component_id    UUID,
    price_version               BIGINT,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_quote_nights_date UNIQUE (quote_id, stay_date),
    CONSTRAINT fk_quote_nights_quote FOREIGN KEY (quote_id) REFERENCES quotes (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_quote_nights_price FOREIGN KEY (daily_price_component_id)
        REFERENCES daily_price_components (id),
    CONSTRAINT ck_quote_nights_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_quote_nights_amounts CHECK (
        base_amount_minor >= 0 AND net_amount_minor >= 0
    ),
    CONSTRAINT ck_quote_nights_arithmetic CHECK (
        net_amount_minor = base_amount_minor + adjustment_total_minor
    ),
    CONSTRAINT ck_quote_nights_price_version CHECK (price_version IS NULL OR price_version >= 0)
);
--rollback DROP TABLE quote_nights;

--changeset ninggiangboy:019-13-quote-line-items
-- The authoritative detail behind the summary. Every line names who pays it, who receives it, who
-- funds it, and who supplies it -- four different questions that a single "amount" column collapses
-- into one, which is how a cleaning fee ends up in the wrong party's payout.
CREATE TABLE quote_line_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id                    UUID NOT NULL,
    line_number                 SMALLINT NOT NULL,
    line_type                   VARCHAR(32) NOT NULL,
    component_code              VARCHAR(48) NOT NULL,
    description_key             VARCHAR(128),
    direction                   VARCHAR(8) NOT NULL,
    quantity                    NUMERIC(10,3) NOT NULL DEFAULT 1,
    unit_amount_minor           BIGINT NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    payer_role                  VARCHAR(16) NOT NULL,
    beneficiary_role            VARCHAR(16) NOT NULL,
    funder_role                 VARCHAR(16),
    supplier_role               VARCHAR(16),
    tax_treatment               VARCHAR(24) NOT NULL DEFAULT 'TAXABLE',
    refundability               VARCHAR(24) NOT NULL DEFAULT 'POLICY_BASED',
    commission_basis            BOOLEAN NOT NULL DEFAULT false,
    price_rule_version_id       UUID,
    promotion_version_id        UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_quote_line_items_number UNIQUE (quote_id, line_number),
    CONSTRAINT fk_quote_line_items_quote FOREIGN KEY (quote_id) REFERENCES quotes (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_quote_line_items_rule_version FOREIGN KEY (price_rule_version_id)
        REFERENCES price_rule_versions (id),
    CONSTRAINT fk_quote_line_items_promotion_version FOREIGN KEY (promotion_version_id)
        REFERENCES promotion_versions (id),
    CONSTRAINT ck_quote_line_items_type CHECK (
        line_type IN (
            'ACCOMMODATION', 'CLEANING_FEE', 'SERVICE_FEE', 'HOST_FEE', 'EXTRA_GUEST_FEE',
            'PET_FEE', 'RESORT_FEE', 'DISCOUNT', 'PROMOTION', 'TAX', 'SECURITY_DEPOSIT', 'OTHER'
        )
    ),
    -- Unsigned amounts with an explicit direction, so a discount can never be confused with a
    -- sign error and a correction can never be confused with a discount.
    CONSTRAINT ck_quote_line_items_direction CHECK (direction IN ('CHARGE', 'CREDIT')),
    CONSTRAINT ck_quote_line_items_amounts CHECK (
        unit_amount_minor >= 0 AND amount_minor >= 0 AND quantity > 0
    ),
    CONSTRAINT ck_quote_line_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_quote_line_items_payer CHECK (
        payer_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_quote_line_items_beneficiary CHECK (
        beneficiary_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_quote_line_items_funder CHECK (
        funder_role IS NULL OR funder_role IN ('GUEST', 'HOST', 'PLATFORM', 'AUTHORITY')
    ),
    CONSTRAINT ck_quote_line_items_supplier CHECK (
        supplier_role IS NULL OR supplier_role IN ('HOST', 'PLATFORM', 'THIRD_PARTY')
    ),
    CONSTRAINT ck_quote_line_items_tax_treatment CHECK (
        tax_treatment IN ('TAXABLE', 'EXEMPT', 'ZERO_RATED', 'OUT_OF_SCOPE', 'TAX_LINE')
    ),
    CONSTRAINT ck_quote_line_items_refundability CHECK (
        refundability IN ('REFUNDABLE', 'NON_REFUNDABLE', 'POLICY_BASED')
    ),
    -- A discount line must say which promotion or rule granted it. An unexplained deduction is
    -- indistinguishable from a defect when the booking later settles.
    CONSTRAINT ck_quote_line_items_discount_source CHECK (
        line_type NOT IN ('DISCOUNT', 'PROMOTION')
        OR price_rule_version_id IS NOT NULL
        OR promotion_version_id IS NOT NULL
    ),
    -- A tax line is never itself taxable and is never the basis for commission.
    CONSTRAINT ck_quote_line_items_tax_line CHECK (
        line_type <> 'TAX' OR (tax_treatment = 'TAX_LINE' AND commission_basis = false)
    ),
    CONSTRAINT ck_quote_line_items_number CHECK (line_number > 0)
);

CREATE INDEX idx_quote_line_items_quote ON quote_line_items (quote_id, line_number);
--rollback DROP TABLE quote_line_items;

--changeset ninggiangboy:019-14-promotion-redemptions
-- Budget is consumed at reservation, not at booking. A promotion whose budget is only debited once a
-- booking confirms will overspend, because every quote sitting open is an unrecorded commitment.
CREATE TABLE promotion_redemptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promotion_version_id        UUID NOT NULL,
    quote_id                    UUID,
    booking_id                  UUID,
    guest_account_holder_id     UUID,
    anonymous_unit_key          VARCHAR(128),
    currency                    VARCHAR(3) NOT NULL,
    benefit_amount_minor        BIGINT NOT NULL,
    host_funded_amount_minor    BIGINT NOT NULL DEFAULT 0,
    platform_funded_amount_minor BIGINT NOT NULL DEFAULT 0,
    state                       VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    reserved_at                 TIMESTAMPTZ NOT NULL,
    redeemed_at                 TIMESTAMPTZ,
    released_at                 TIMESTAMPTZ,
    reversed_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_promotion_redemptions_version FOREIGN KEY (promotion_version_id)
        REFERENCES promotion_versions (id),
    CONSTRAINT fk_promotion_redemptions_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_promotion_redemptions_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_promotion_redemptions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_promotion_redemptions_amounts CHECK (
        benefit_amount_minor > 0
        AND host_funded_amount_minor >= 0
        AND platform_funded_amount_minor >= 0
    ),
    -- The split must account for the whole benefit. A gap means somebody absorbed a cost that no
    -- ledger will ever attribute; an excess means the discount was funded twice.
    CONSTRAINT ck_promotion_redemptions_funding CHECK (
        host_funded_amount_minor + platform_funded_amount_minor = benefit_amount_minor
    ),
    CONSTRAINT ck_promotion_redemptions_state CHECK (
        state IN ('RESERVED', 'REDEEMED', 'RELEASED', 'REVERSED')
    ),
    CONSTRAINT ck_promotion_redemptions_timestamps CHECK (
        (state = 'REDEEMED') = (redeemed_at IS NOT NULL)
        AND (state = 'RELEASED') = (released_at IS NOT NULL)
        AND (state = 'REVERSED') = (reversed_at IS NOT NULL)
    ),
    CONSTRAINT ck_promotion_redemptions_subject CHECK (
        guest_account_holder_id IS NOT NULL OR anonymous_unit_key IS NOT NULL
    ),
    -- A redemption is anchored to the thing it discounts.
    CONSTRAINT ck_promotion_redemptions_anchor CHECK (
        quote_id IS NOT NULL OR booking_id IS NOT NULL
    ),
    CONSTRAINT ck_promotion_redemptions_version CHECK (version >= 0)
);

-- One live redemption of a promotion per quote. Without this, a retried checkout applies the same
-- discount twice and the budget is debited twice for one stay.
CREATE UNIQUE INDEX uk_promotion_redemptions_one_live_per_quote
    ON promotion_redemptions (promotion_version_id, quote_id)
    WHERE quote_id IS NOT NULL AND state IN ('RESERVED', 'REDEEMED');

CREATE INDEX idx_promotion_redemptions_budget
    ON promotion_redemptions (promotion_version_id, state);
--rollback DROP TABLE promotion_redemptions;

--changeset ninggiangboy:019-15-party-tax-profiles
-- Where a party is tax-resident and where it has an establishment decides which authority's rules
-- apply to its income. It is effective-dated because a host who moves country does not retroactively
-- change where last year's stays were taxed.
CREATE TABLE party_tax_profiles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_type              VARCHAR(16) NOT NULL,
    account_holder_id       UUID,
    legal_entity_id         UUID,
    entity_type             VARCHAR(16) NOT NULL,
    residence_country       VARCHAR(2) NOT NULL,
    establishment_country   VARCHAR(2),
    status                  VARCHAR(16) NOT NULL DEFAULT 'DECLARED',
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_party_tax_profiles_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_party_tax_profiles_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT ck_party_tax_profiles_party_type CHECK (
        party_type IN ('ACCOUNT_HOLDER', 'LEGAL_ENTITY')
    ),
    CONSTRAINT ck_party_tax_profiles_party CHECK (
        (party_type = 'ACCOUNT_HOLDER' AND account_holder_id IS NOT NULL AND legal_entity_id IS NULL)
        OR (party_type = 'LEGAL_ENTITY' AND legal_entity_id IS NOT NULL
            AND account_holder_id IS NULL)
    ),
    CONSTRAINT ck_party_tax_profiles_entity_type CHECK (
        entity_type IN ('INDIVIDUAL', 'BUSINESS')
    ),
    CONSTRAINT ck_party_tax_profiles_countries CHECK (
        residence_country ~ '^[A-Z]{2}$'
        AND (establishment_country IS NULL OR establishment_country ~ '^[A-Z]{2}$')
    ),
    -- DECLARED is what the party told us; VERIFIED is what evidence supports. Treating the two as
    -- the same is how an unverified claim ends up deciding a withholding rate.
    CONSTRAINT ck_party_tax_profiles_status CHECK (
        status IN ('DECLARED', 'VERIFIED', 'DISPUTED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_party_tax_profiles_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_party_tax_profiles_version CHECK (version >= 0),
    -- One profile per party at a time, per subject kind. Two overlapping profiles would let two
    -- calculations a second apart place the same party in two countries.
    CONSTRAINT ex_party_tax_profiles_holder_no_overlap EXCLUDE USING gist (
        account_holder_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    ) WHERE (account_holder_id IS NOT NULL),
    CONSTRAINT ex_party_tax_profiles_entity_no_overlap EXCLUDE USING gist (
        legal_entity_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    ) WHERE (legal_entity_id IS NOT NULL)
);
--rollback DROP TABLE party_tax_profiles;

--changeset ninggiangboy:019-16-tax-registrations
-- A registration number is a restricted identifier. Only a token and a digest are stored here: the
-- digest lets a resubmitted number be recognised as the same one, and the token lets the vault
-- return the plaintext to the one caller entitled to see it. Nothing in this schema can leak it.
CREATE TABLE tax_registrations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_tax_profile_id        UUID NOT NULL,
    jurisdiction_code           VARCHAR(16) NOT NULL,
    tax_type                    VARCHAR(32) NOT NULL,
    registration_token          VARCHAR(128) NOT NULL,
    registration_digest         CHAR(64) NOT NULL,
    registration_last_four      VARCHAR(4),
    verification_source         VARCHAR(48),
    verification_status         VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
    verified_at                 TIMESTAMPTZ,
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_tax_registrations_profile FOREIGN KEY (party_tax_profile_id)
        REFERENCES party_tax_profiles (id),
    CONSTRAINT ck_tax_registrations_jurisdiction CHECK (
        jurisdiction_code ~ '^[A-Z]{2}(-[A-Z0-9]{1,8})*$'
    ),
    CONSTRAINT ck_tax_registrations_tax_type CHECK (
        tax_type IN (
            'VAT', 'GST', 'SALES_TAX', 'LODGING_TAX', 'TOURIST_TAX', 'INCOME_TAX',
            'WITHHOLDING_TAX', 'CITY_TAX'
        )
    ),
    CONSTRAINT ck_tax_registrations_digest CHECK (registration_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tax_registrations_last_four CHECK (
        registration_last_four IS NULL OR registration_last_four ~ '^[A-Za-z0-9]{1,4}$'
    ),
    CONSTRAINT ck_tax_registrations_verification CHECK (
        verification_status IN ('UNVERIFIED', 'VERIFIED', 'INVALID', 'EXPIRED')
    ),
    CONSTRAINT ck_tax_registrations_verified_at CHECK (
        (verification_status = 'VERIFIED') = (verified_at IS NOT NULL)
    ),
    CONSTRAINT ck_tax_registrations_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_tax_registrations_version CHECK (version >= 0),
    -- One registration per party, jurisdiction and tax type at a time.
    CONSTRAINT ex_tax_registrations_no_overlap EXCLUDE USING gist (
        party_tax_profile_id WITH =,
        jurisdiction_code WITH =,
        tax_type WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);
--rollback DROP TABLE tax_registrations;

--changeset ninggiangboy:019-17-approval-subject-tax-rule
-- Migration 013 enumerated the things a market approval can be about, and tax rules did not exist
-- yet. Without this the tax rule table below is unusable: publishing one demands an approval record,
-- and an approval record naming a tax rule version is rejected by 013's own check. The list is
-- widened here rather than edited there, because 013 is already applied.
ALTER TABLE market_approval_records DROP CONSTRAINT ck_market_approval_records_subject;
ALTER TABLE market_approval_records ADD CONSTRAINT ck_market_approval_records_subject CHECK (
    subject_type IN (
        'MARKET', 'LEGAL_ENTITY', 'POLICY_BUNDLE',
        'MARKET_CAPABILITY', 'PROVIDER_ACCOUNT', 'LOCALIZED_CONTENT',
        'TAX_RULE_VERSION'
    )
);
--rollback ALTER TABLE market_approval_records DROP CONSTRAINT ck_market_approval_records_subject;
--rollback ALTER TABLE market_approval_records ADD CONSTRAINT ck_market_approval_records_subject CHECK (subject_type IN ('MARKET', 'LEGAL_ENTITY', 'POLICY_BUNDLE', 'MARKET_CAPABILITY', 'PROVIDER_ACCOUNT', 'LOCALIZED_CONTENT'));

--changeset ninggiangboy:019-18-tax-rule-versions
-- The authority's rule as we understood it on a date, with the source we read it from and the person
-- who approved that reading. A tax rate is not ours to infer, and a calculation that cannot name its
-- source cannot be defended to the authority that levied it.
CREATE TABLE tax_rule_versions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    jurisdiction_code       VARCHAR(16) NOT NULL,
    authority_name          VARCHAR(160) NOT NULL,
    tax_type                VARCHAR(32) NOT NULL,
    version_number          INTEGER NOT NULL,
    applicability_payload   JSONB NOT NULL,
    formula_payload         JSONB NOT NULL,
    rate_percent            NUMERIC(9,6),
    flat_amount_minor       BIGINT,
    flat_amount_currency    VARCHAR(3),
    remittance_model        VARCHAR(24) NOT NULL,
    rounding_mode           VARCHAR(16) NOT NULL DEFAULT 'HALF_UP',
    rounding_boundary       VARCHAR(16) NOT NULL DEFAULT 'LINE',
    source_reference        VARCHAR(500) NOT NULL,
    approval_record_id      UUID,
    content_digest          CHAR(64) NOT NULL,
    publication_state       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at            TIMESTAMPTZ,
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tax_rule_versions_number
        UNIQUE (jurisdiction_code, authority_name, tax_type, version_number),
    CONSTRAINT fk_tax_rule_versions_approval FOREIGN KEY (approval_record_id)
        REFERENCES market_approval_records (id),
    CONSTRAINT ck_tax_rule_versions_jurisdiction CHECK (
        jurisdiction_code ~ '^[A-Z]{2}(-[A-Z0-9]{1,8})*$'
    ),
    CONSTRAINT ck_tax_rule_versions_tax_type CHECK (
        tax_type IN (
            'VAT', 'GST', 'SALES_TAX', 'LODGING_TAX', 'TOURIST_TAX', 'INCOME_TAX',
            'WITHHOLDING_TAX', 'CITY_TAX'
        )
    ),
    CONSTRAINT ck_tax_rule_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_tax_rule_versions_payloads CHECK (
        jsonb_typeof(applicability_payload) = 'object' AND jsonb_typeof(formula_payload) = 'object'
    ),
    -- A rule charges a rate, a flat amount, or something the formula describes -- but a rate and a
    -- flat amount together have no defined order of application.
    CONSTRAINT ck_tax_rule_versions_charge CHECK (
        rate_percent IS NULL OR flat_amount_minor IS NULL
    ),
    CONSTRAINT ck_tax_rule_versions_rate CHECK (
        rate_percent IS NULL OR rate_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_tax_rule_versions_flat CHECK (
        (flat_amount_minor IS NULL) = (flat_amount_currency IS NULL)
        AND (flat_amount_minor IS NULL OR flat_amount_minor > 0)
        AND (flat_amount_currency IS NULL OR flat_amount_currency ~ '^[A-Z]{3}$')
    ),
    -- Who owes the authority. Marketplace-liable and supplier-liable produce different ledger
    -- postings and different filings for the same guest-facing amount.
    CONSTRAINT ck_tax_rule_versions_remittance CHECK (
        remittance_model IN ('MARKETPLACE_LIABLE', 'SUPPLIER_LIABLE', 'WITHHOLDING', 'PASS_THROUGH')
    ),
    CONSTRAINT ck_tax_rule_versions_rounding CHECK (
        rounding_mode IN ('HALF_UP', 'HALF_EVEN', 'DOWN', 'UP')
        AND rounding_boundary IN ('UNIT', 'NIGHT', 'LINE', 'TAX', 'INVOICE', 'TOTAL')
    ),
    CONSTRAINT ck_tax_rule_versions_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tax_rule_versions_state CHECK (
        publication_state IN ('DRAFT', 'PUBLISHED', 'RETIRED')
    ),
    -- A published tax rule must name the approval that accepted it. Nobody may publish a tax rate
    -- alone.
    CONSTRAINT ck_tax_rule_versions_publication CHECK (
        publication_state = 'DRAFT'
        OR (published_at IS NOT NULL AND approval_record_id IS NOT NULL)
    ),
    CONSTRAINT ck_tax_rule_versions_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    -- One published rule of a given type per authority at any instant. Two overlapping versions
    -- would let two calculations a second apart charge different tax on the same night.
    CONSTRAINT ex_tax_rule_versions_no_overlap EXCLUDE USING gist (
        jurisdiction_code WITH =,
        authority_name WITH =,
        tax_type WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    ) WHERE (publication_state = 'PUBLISHED')
);

CREATE INDEX idx_tax_rule_versions_resolution
    ON tax_rule_versions (jurisdiction_code, tax_type, effective_from DESC)
    WHERE publication_state = 'PUBLISHED';
--rollback DROP TABLE tax_rule_versions;

--changeset ninggiangboy:019-19-tax-calculations
-- The complete input snapshot beside the result. A tax figure that cannot be recomputed from what
-- was known at the time is not evidence, and tax is the one number that gets audited years later by
-- someone who was not in the room.
CREATE TABLE tax_calculations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id                UUID,
    booking_id              UUID,
    market_code             VARCHAR(2) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    request_fact_snapshot   JSONB NOT NULL,
    content_version         VARCHAR(64) NOT NULL,
    calculation_source      VARCHAR(24) NOT NULL DEFAULT 'INTERNAL',
    provider_account_id     UUID,
    provider_reference      VARCHAR(128),
    taxable_base_minor      BIGINT NOT NULL,
    tax_total_minor         BIGINT NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'CALCULATED',
    calculated_at           TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_tax_calculations_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_tax_calculations_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_tax_calculations_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT ck_tax_calculations_anchor CHECK (
        quote_id IS NOT NULL OR booking_id IS NOT NULL
    ),
    CONSTRAINT ck_tax_calculations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_tax_calculations_snapshot CHECK (
        jsonb_typeof(request_fact_snapshot) = 'object'
    ),
    CONSTRAINT ck_tax_calculations_source CHECK (
        calculation_source IN ('INTERNAL', 'PROVIDER', 'MANUAL')
    ),
    -- A result attributed to a provider must say which one and which of its responses it came from.
    CONSTRAINT ck_tax_calculations_provider_link CHECK (
        (calculation_source = 'PROVIDER')
            = (provider_account_id IS NOT NULL AND provider_reference IS NOT NULL)
    ),
    CONSTRAINT ck_tax_calculations_amounts CHECK (
        taxable_base_minor >= 0 AND tax_total_minor >= 0
    ),
    CONSTRAINT ck_tax_calculations_status CHECK (
        status IN ('CALCULATED', 'SUPERSEDED', 'VOID', 'FAILED')
    )
);

CREATE INDEX idx_tax_calculations_quote ON tax_calculations (quote_id)
    WHERE quote_id IS NOT NULL;
--rollback DROP TABLE tax_calculations;

--changeset ninggiangboy:019-20-tax-calculation-lines
-- One row per tax, per jurisdiction, per taxed line. A stay can be simultaneously subject to a
-- marketplace-liable city tax and a supplier-liable VAT; collapsing them into one figure loses
-- exactly the fact that decides who files what.
CREATE TABLE tax_calculation_lines (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tax_calculation_id      UUID NOT NULL,
    line_number             SMALLINT NOT NULL,
    tax_rule_version_id     UUID,
    quote_line_item_id      UUID,
    jurisdiction_code       VARCHAR(16) NOT NULL,
    tax_type                VARCHAR(32) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    taxable_base_minor      BIGINT NOT NULL,
    rate_percent            NUMERIC(9,6),
    tax_amount_minor        BIGINT NOT NULL,
    liable_party_role       VARCHAR(16) NOT NULL,
    remittance_party_role   VARCHAR(16) NOT NULL,
    remittance_model        VARCHAR(24) NOT NULL,
    is_included_in_price    BOOLEAN NOT NULL DEFAULT false,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tax_calculation_lines_number UNIQUE (tax_calculation_id, line_number),
    CONSTRAINT fk_tax_calculation_lines_calculation FOREIGN KEY (tax_calculation_id)
        REFERENCES tax_calculations (id) ON DELETE CASCADE,
    CONSTRAINT fk_tax_calculation_lines_rule FOREIGN KEY (tax_rule_version_id)
        REFERENCES tax_rule_versions (id),
    CONSTRAINT fk_tax_calculation_lines_quote_line FOREIGN KEY (quote_line_item_id)
        REFERENCES quote_line_items (id),
    CONSTRAINT ck_tax_calculation_lines_jurisdiction CHECK (
        jurisdiction_code ~ '^[A-Z]{2}(-[A-Z0-9]{1,8})*$'
    ),
    CONSTRAINT ck_tax_calculation_lines_tax_type CHECK (
        tax_type IN (
            'VAT', 'GST', 'SALES_TAX', 'LODGING_TAX', 'TOURIST_TAX', 'INCOME_TAX',
            'WITHHOLDING_TAX', 'CITY_TAX'
        )
    ),
    CONSTRAINT ck_tax_calculation_lines_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_tax_calculation_lines_amounts CHECK (
        taxable_base_minor >= 0 AND tax_amount_minor >= 0
    ),
    CONSTRAINT ck_tax_calculation_lines_rate CHECK (
        rate_percent IS NULL OR rate_percent BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_tax_calculation_lines_liable CHECK (
        liable_party_role IN ('GUEST', 'HOST', 'PLATFORM')
    ),
    CONSTRAINT ck_tax_calculation_lines_remittance_party CHECK (
        remittance_party_role IN ('HOST', 'PLATFORM')
    ),
    CONSTRAINT ck_tax_calculation_lines_remittance_model CHECK (
        remittance_model IN ('MARKETPLACE_LIABLE', 'SUPPLIER_LIABLE', 'WITHHOLDING', 'PASS_THROUGH')
    ),
    -- Marketplace-liable means the platform files it; the two must agree or the filing goes to the
    -- wrong party.
    CONSTRAINT ck_tax_calculation_lines_remittance_agreement CHECK (
        remittance_model <> 'MARKETPLACE_LIABLE' OR remittance_party_role = 'PLATFORM'
    ),
    CONSTRAINT ck_tax_calculation_lines_number CHECK (line_number > 0)
);

CREATE INDEX idx_tax_calculation_lines_rule ON tax_calculation_lines (tax_rule_version_id)
    WHERE tax_rule_version_id IS NOT NULL;
--rollback DROP TABLE tax_calculation_lines;
