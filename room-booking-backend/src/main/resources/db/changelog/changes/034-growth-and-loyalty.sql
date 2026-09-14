--liquibase formatted sql

-- Every incentive is somebody's money. A referral bonus, a credit, a gift card, a loyalty benefit
-- and an affiliate commission are not marketing artefacts that happen to have amounts attached;
-- each one is a real obligation owed by a named party, and the moment the platform stops modelling
-- one as money it becomes either a discount nobody can audit or a promise nobody can keep. That is
-- the whole of this migration. The domain document names four failures to avoid -- accounting
-- ambiguity, fraud, hidden price discrimination and marketplace imbalance -- and all four begin the
-- same way: value leaves the platform through a path that does not name its funder, its rule
-- version, or the ledger account it came out of.
--
-- So this migration builds no second discount engine. A promotional benefit reaching a guest is
-- migration 019's promotion version and redemption, cited here rather than copied, because 019
-- already carries the stacking group, the host-funded share, the tax treatment and the redemption
-- reversal. What this migration adds is where the value comes from before it becomes a discount,
-- who is owed it, when it matures, when it expires and what happens when the booking that earned
-- it is cancelled.
--
-- Seven forces shape it:
--
--   An incentive names its funder or it does not exist. A program that grants value carries the
--   legal entity that owes it, the accounting book it is recorded in and the ledger liability
--   account it is drawn against, and every movement of a stored-value balance cites the ledger
--   transaction that posted it. There is deliberately no column here into which a service can
--   write a credit without a posting behind it, because a balance that exists only in a growth
--   table is a liability the finance close will never see.
--
--   Expiry belongs to the money, not to the account. Credits are lots, each with its own origin,
--   its own expiry, its own refund behaviour and its own market restriction, and a redemption
--   draws down named lots. A single balance column cannot answer which currency, which market or
--   which promise a guest's remaining value came from, and it silently expires the oldest promise
--   the platform made alongside the newest.
--
--   Eligibility is a version and a recorded reason, never a query run again later. Every decision
--   about whether someone qualified stores the program version it was taken under, the outcome,
--   an approved reason code and the sentence the guest was shown. A guest asking why they were not
--   eligible is answered from the row that decided it, not by re-running today's rules against a
--   question from last quarter.
--
--   Anti-self-referral is a constraint, not a hope. A referrer may not be the referee, a person may
--   be referred once per program, a reward matures against a stay that actually completed, and a
--   reward earned by a booking that is later cancelled is reversed before the attribution itself
--   can be closed. Where a shared device, contact or payment instrument was detected, qualifying
--   anyway requires a named override rather than a silent pass.
--
--   Contacting someone is not a side effect of being in an audience. An audience membership cites
--   the consent that permits contact and the eligibility evaluation that put it there, a touchpoint
--   is one of migration 024's notification intents rather than a private send path, and the holdout
--   arm can never be contacted at all -- if it could, it would not be a holdout.
--
--   A claim of uplift requires a holdout. An uplift row cites migration 030's experiment analysis
--   run, carries the interval around its own effect, and cannot record that a campaign worked
--   unless that interval excludes zero. Counting redemptions measures who took the money; it does
--   not measure whether anyone behaved differently, and the domain rule is explicitly that models
--   optimise incremental behaviour rather than redemption by guests who would have booked anyway.
--
--   A standing alert is standing consent, and it stops itself. Saved searches, wish lists, price
--   and availability alerts and waitlist entries each name the consent that permits their
--   notification and carry an expiry, because a subscription created once and never re-confirmed
--   becomes a channel the guest cannot remember agreeing to. A waitlist offer may not be sent
--   without a quote behind it: telling someone a room opened up without holding one is the same
--   fabricated scarcity migration 033 refuses to let a forecast invent.
--
-- Note on what this migration does not create. Discounts, stacking, host-funded shares and their
-- reversal are migration 019's promotions, promotion_versions, promotion_assignments and
-- promotion_redemptions. Quotes and their line items are 019; bookings are 020; the ledger,
-- accounting books, payout instructions and host recoveries are 022. Consent, notification
-- policies, intents and delivery are 024. Wish lists rest on migration 029's saved_listings, which
-- already records the save, the surface and the unsave; this migration adds the collections over
-- them rather than a second saved-listing table. Experiments, holdout shares, assignments and
-- analysis runs are 030, and metric definitions are 030 as well. Personalisation settings and
-- erasure directives are 029. The trigger functions platform_append_only() and
-- platform_contract_freeze() come from 030 and are reused.
--
-- Every table here is written by the application, so none of them carry DEFAULT now(): see
-- migration 011.

--changeset ninggiangboy:034-01-growth-programs
CREATE TABLE growth_programs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_key                 VARCHAR(64) NOT NULL,
    program_kind                VARCHAR(24) NOT NULL,
    market_code                 VARCHAR(2),
    display_name                VARCHAR(160) NOT NULL,
    purpose_statement           VARCHAR(1000) NOT NULL,
    grants_value                BOOLEAN NOT NULL,
    funding_legal_entity_id     UUID,
    accounting_book_id          UUID,
    liability_account_id        UUID,
    funder_type                 VARCHAR(16) NOT NULL,
    owner_actor_id              UUID,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    opened_at                   TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_growth_programs_key UNIQUE (program_key),
    CONSTRAINT fk_growth_programs_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_growth_programs_entity FOREIGN KEY (funding_legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_growth_programs_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_growth_programs_account FOREIGN KEY (liability_account_id)
        REFERENCES ledger_accounts (id),
    CONSTRAINT ck_growth_programs_kind CHECK (
        program_kind IN ('REFERRAL', 'CREDIT_GRANT', 'LOYALTY', 'CAMPAIGN', 'GIFT_CARD',
                         'AFFILIATE')
    ),
    CONSTRAINT ck_growth_programs_funder_type CHECK (
        funder_type IN ('PLATFORM', 'HOST', 'PARTNER', 'MIXED')
    ),
    CONSTRAINT ck_growth_programs_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED')
    ),
    CONSTRAINT ck_growth_programs_market_code CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    -- A program that hands out value has to say whose balance sheet it comes off. The three
    -- accounting columns travel together because any one of them alone cannot post a liability.
    CONSTRAINT ck_growth_programs_funding CHECK (
        grants_value = (funding_legal_entity_id IS NOT NULL AND accounting_book_id IS NOT NULL
                        AND liability_account_id IS NOT NULL)
    ),
    -- Four of the six kinds are value by definition; a campaign or a loyalty tier may be purely
    -- informational, and only those two are allowed to declare that they grant nothing.
    CONSTRAINT ck_growth_programs_value_kinds CHECK (
        program_kind IN ('LOYALTY', 'CAMPAIGN') OR grants_value
    ),
    CONSTRAINT ck_growth_programs_opened CHECK (status = 'DRAFT' OR opened_at IS NOT NULL),
    CONSTRAINT ck_growth_programs_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_growth_programs_version CHECK (version >= 0)
);

CREATE INDEX idx_growth_programs_kind ON growth_programs (program_kind, market_code)
    WHERE status = 'ACTIVE';
--rollback DROP TABLE growth_programs;

--changeset ninggiangboy:034-02-growth-program-versions
CREATE TABLE growth_program_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_id           UUID NOT NULL,
    version_number              INTEGER NOT NULL,
    eligibility_payload         JSONB NOT NULL,
    eligibility_explanation     VARCHAR(1000) NOT NULL,
    reward_kind                 VARCHAR(24) NOT NULL,
    reward_amount_minor         BIGINT,
    reward_percent              NUMERIC(6,4),
    reward_currency             VARCHAR(3),
    maximum_reward_minor        BIGINT,
    promotion_version_id        UUID,
    credit_validity_days        INTEGER,
    qualification_event         VARCHAR(32) NOT NULL,
    maturity_delay_days         INTEGER NOT NULL DEFAULT 0,
    per_subject_reward_limit    INTEGER,
    program_reward_limit        INTEGER,
    budget_total_minor          BIGINT,
    budget_currency             VARCHAR(3),
    content_digest              CHAR(64) NOT NULL,
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    approved_by                 UUID,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at                TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_growth_program_versions_number UNIQUE (growth_program_id, version_number),
    CONSTRAINT fk_growth_program_versions_program FOREIGN KEY (growth_program_id)
        REFERENCES growth_programs (id),
    CONSTRAINT fk_growth_program_versions_promotion FOREIGN KEY (promotion_version_id)
        REFERENCES promotion_versions (id),
    CONSTRAINT ck_growth_program_versions_reward_kind CHECK (
        reward_kind IN ('CREDIT', 'PROMOTION', 'TIER_BENEFIT', 'COMMISSION', 'GIFT_CARD_VALUE',
                        'NONE')
    ),
    CONSTRAINT ck_growth_program_versions_event CHECK (
        qualification_event IN ('ACCOUNT_CREATED', 'FIRST_BOOKING_CONFIRMED', 'BOOKING_CONFIRMED',
                                'STAY_COMPLETED', 'PURCHASE_SETTLED', 'MANUAL_AWARD')
    ),
    CONSTRAINT ck_growth_program_versions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    -- A benefit that reaches a guest as a discount is migration 019's, not a second one written
    -- here; naming the promotion version is what keeps stacking, tax treatment and the host-funded
    -- share under the rules that already govern every other discount on the quote.
    CONSTRAINT ck_growth_program_versions_promotion_ref CHECK (
        (reward_kind = 'PROMOTION') = (promotion_version_id IS NOT NULL)
    ),
    -- Credit granted without a validity period is a liability with no end, which is exactly the
    -- accounting ambiguity the domain document refuses.
    CONSTRAINT ck_growth_program_versions_credit CHECK (
        reward_kind <> 'CREDIT'
            OR (reward_amount_minor IS NOT NULL AND reward_currency IS NOT NULL
                AND credit_validity_days IS NOT NULL)
    ),
    CONSTRAINT ck_growth_program_versions_commission CHECK (
        reward_kind <> 'COMMISSION' OR reward_percent IS NOT NULL
    ),
    CONSTRAINT ck_growth_program_versions_amount CHECK (
        (reward_amount_minor IS NULL OR reward_amount_minor > 0)
            AND (maximum_reward_minor IS NULL OR maximum_reward_minor > 0)
            AND (reward_amount_minor IS NULL OR maximum_reward_minor IS NULL
                 OR reward_amount_minor <= maximum_reward_minor)
    ),
    CONSTRAINT ck_growth_program_versions_percent CHECK (
        reward_percent IS NULL OR (reward_percent > 0 AND reward_percent <= 1)
    ),
    CONSTRAINT ck_growth_program_versions_currency CHECK (
        (reward_currency IS NULL OR reward_currency ~ '^[A-Z]{3}$')
            AND (budget_currency IS NULL OR budget_currency ~ '^[A-Z]{3}$')
            AND (reward_amount_minor IS NULL) = (reward_currency IS NULL)
            AND (budget_total_minor IS NULL) = (budget_currency IS NULL)
    ),
    CONSTRAINT ck_growth_program_versions_limits CHECK (
        (per_subject_reward_limit IS NULL OR per_subject_reward_limit > 0)
            AND (program_reward_limit IS NULL OR program_reward_limit > 0)
            AND (budget_total_minor IS NULL OR budget_total_minor > 0)
            AND (credit_validity_days IS NULL OR credit_validity_days > 0)
            AND maturity_delay_days >= 0
    ),
    CONSTRAINT ck_growth_program_versions_window CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_growth_program_versions_published CHECK (
        status = 'DRAFT' OR (published_at IS NOT NULL AND approved_by IS NOT NULL)
    ),
    CONSTRAINT ck_growth_program_versions_retired CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_growth_program_versions_version CHECK (version >= 0)
);

CREATE INDEX idx_growth_program_versions_active ON growth_program_versions (growth_program_id)
    WHERE status = 'ACTIVE';
--rollback DROP TABLE growth_program_versions;

--changeset ninggiangboy:034-03-growth-eligibility-evaluations
CREATE TABLE growth_eligibility_evaluations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_version_id   UUID NOT NULL,
    subject_kind                VARCHAR(24) NOT NULL,
    account_holder_id           UUID,
    anonymous_unit_key          VARCHAR(128),
    outcome                     VARCHAR(16) NOT NULL,
    reason_code                 VARCHAR(64),
    guest_explanation           VARCHAR(1000) NOT NULL,
    input_digest                CHAR(64) NOT NULL,
    evaluated_at                TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_growth_eligibility_evaluations_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT fk_growth_eligibility_evaluations_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_growth_eligibility_evaluations_subject_kind CHECK (
        subject_kind IN ('ACCOUNT_HOLDER', 'ANONYMOUS_UNIT')
    ),
    CONSTRAINT ck_growth_eligibility_evaluations_outcome CHECK (
        outcome IN ('ELIGIBLE', 'INELIGIBLE', 'CAPPED', 'OUT_OF_WINDOW', 'SUPPRESSED')
    ),
    CONSTRAINT ck_growth_eligibility_evaluations_subject CHECK (
        num_nonnulls(account_holder_id, anonymous_unit_key) = 1
            AND (subject_kind = 'ANONYMOUS_UNIT') = (anonymous_unit_key IS NOT NULL)
    ),
    -- An unfavourable outcome carries an approved reason code, and a favourable one carries none;
    -- a guest who is told no is owed the reason the platform actually used, not a sentence written
    -- afterwards by whoever fielded the complaint.
    CONSTRAINT ck_growth_eligibility_evaluations_reason CHECK (
        (outcome = 'ELIGIBLE') = (reason_code IS NULL)
    ),
    CONSTRAINT ck_growth_eligibility_evaluations_expiry CHECK (
        expires_at IS NULL OR expires_at > evaluated_at
    )
);

CREATE INDEX idx_growth_eligibility_evaluations_subject
    ON growth_eligibility_evaluations (account_holder_id, evaluated_at DESC)
    WHERE account_holder_id IS NOT NULL;
--rollback DROP TABLE growth_eligibility_evaluations;

--changeset ninggiangboy:034-04-referral-codes
CREATE TABLE referral_codes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_version_id   UUID NOT NULL,
    owner_account_holder_id     UUID NOT NULL,
    code                        VARCHAR(32) NOT NULL,
    issued_at                   TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    suspension_reason           VARCHAR(64),
    invitation_count            INTEGER NOT NULL DEFAULT 0,
    qualified_count             INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_referral_codes_code UNIQUE (code),
    CONSTRAINT uk_referral_codes_owner UNIQUE (growth_program_version_id, owner_account_holder_id),
    CONSTRAINT fk_referral_codes_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT fk_referral_codes_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_referral_codes_state CHECK (
        state IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'REVOKED')
    ),
    -- One-way: a suspended or revoked code says why. A code later reinstated keeps the sentence
    -- that suspended it, because erasing it is the only other way to answer what happened.
    CONSTRAINT ck_referral_codes_suspension CHECK (
        state NOT IN ('SUSPENDED', 'REVOKED') OR suspension_reason IS NOT NULL
    ),
    CONSTRAINT ck_referral_codes_expiry CHECK (expires_at IS NULL OR expires_at > issued_at),
    CONSTRAINT ck_referral_codes_counts CHECK (invitation_count >= 0 AND qualified_count >= 0),
    CONSTRAINT ck_referral_codes_version CHECK (version >= 0)
);
--rollback DROP TABLE referral_codes;

--changeset ninggiangboy:034-05-referral-invitations
CREATE TABLE referral_invitations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referral_code_id            UUID NOT NULL,
    invited_channel             VARCHAR(16) NOT NULL,
    invited_contact_digest      CHAR(64),
    notification_intent_id      UUID,
    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    suppression_reason          VARCHAR(48),
    invited_at                  TIMESTAMPTZ NOT NULL,
    viewed_at                   TIMESTAMPTZ,
    accepted_at                 TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ NOT NULL,
    accepted_account_holder_id  UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_referral_invitations_code FOREIGN KEY (referral_code_id)
        REFERENCES referral_codes (id),
    CONSTRAINT fk_referral_invitations_intent FOREIGN KEY (notification_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_referral_invitations_holder FOREIGN KEY (accepted_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_referral_invitations_channel CHECK (
        invited_channel IN ('EMAIL', 'SMS', 'LINK', 'SOCIAL')
    ),
    CONSTRAINT ck_referral_invitations_state CHECK (
        state IN ('PENDING', 'VIEWED', 'ACCEPTED', 'EXPIRED', 'SUPPRESSED')
    ),
    -- An addressed invitation stores a digest of the address and never the address itself: the
    -- platform needs to know it already wrote to this person, not to keep a contact list of people
    -- who never joined. A shared link has no addressee, so it carries no digest at all.
    CONSTRAINT ck_referral_invitations_contact CHECK (
        (invited_channel IN ('EMAIL', 'SMS')) = (invited_contact_digest IS NOT NULL)
    ),
    CONSTRAINT ck_referral_invitations_accepted CHECK (
        (state = 'ACCEPTED')
            = (accepted_at IS NOT NULL AND accepted_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_referral_invitations_suppressed CHECK (
        (state = 'SUPPRESSED') = (suppression_reason IS NOT NULL)
    ),
    CONSTRAINT ck_referral_invitations_expiry CHECK (expires_at > invited_at),
    CONSTRAINT ck_referral_invitations_version CHECK (version >= 0)
);

-- One invitation per contact per code. A referral programme that lets the same address be invited
-- again every week is an unsolicited-mail programme with a reward attached.
CREATE UNIQUE INDEX uk_referral_invitations_contact
    ON referral_invitations (referral_code_id, invited_contact_digest)
    WHERE invited_contact_digest IS NOT NULL;
--rollback DROP TABLE referral_invitations;

--changeset ninggiangboy:034-06-referral-attributions
CREATE TABLE referral_attributions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_id           UUID NOT NULL,
    referral_code_id            UUID NOT NULL,
    referral_invitation_id      UUID,
    referrer_account_holder_id  UUID NOT NULL,
    referee_account_holder_id   UUID NOT NULL,
    attribution_basis           VARCHAR(24) NOT NULL,
    attributed_at               TIMESTAMPTZ NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    qualifying_booking_id       UUID,
    qualified_at                TIMESTAMPTZ,
    rejection_reason            VARCHAR(64),
    reversal_reason             VARCHAR(64),
    reversed_at                 TIMESTAMPTZ,
    shared_device_signal        BOOLEAN NOT NULL DEFAULT false,
    shared_contact_signal       BOOLEAN NOT NULL DEFAULT false,
    shared_instrument_signal    BOOLEAN NOT NULL DEFAULT false,
    fraud_override_reason       VARCHAR(64),
    fraud_override_by           UUID,
    screening_payload           JSONB NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_referral_attributions_referee UNIQUE (growth_program_id,
                                                        referee_account_holder_id),
    CONSTRAINT fk_referral_attributions_program FOREIGN KEY (growth_program_id)
        REFERENCES growth_programs (id),
    CONSTRAINT fk_referral_attributions_code FOREIGN KEY (referral_code_id)
        REFERENCES referral_codes (id),
    CONSTRAINT fk_referral_attributions_invitation FOREIGN KEY (referral_invitation_id)
        REFERENCES referral_invitations (id),
    CONSTRAINT fk_referral_attributions_referrer FOREIGN KEY (referrer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_referral_attributions_referee FOREIGN KEY (referee_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_referral_attributions_booking FOREIGN KEY (qualifying_booking_id)
        REFERENCES bookings (id),
    CONSTRAINT ck_referral_attributions_basis CHECK (
        attribution_basis IN ('INVITATION_LINK', 'CODE_ENTERED', 'CONTACT_MATCH')
    ),
    CONSTRAINT ck_referral_attributions_state CHECK (
        state IN ('PENDING', 'QUALIFIED', 'REJECTED', 'REVERSED')
    ),
    -- Referring yourself is the cheapest fraud there is, and it is one comparison. The unique
    -- constraint above is the other half: a person is referred once, by whoever got there first.
    CONSTRAINT ck_referral_attributions_self CHECK (
        referrer_account_holder_id <> referee_account_holder_id
    ),
    -- Two one-way clauses rather than one equality: a reversed attribution qualified first, and
    -- the booking that qualified it is what the reversal is about. An equality here would force
    -- that evidence to be erased on the way out.
    CONSTRAINT ck_referral_attributions_qualified CHECK (
        (state NOT IN ('QUALIFIED', 'REVERSED')
             OR (qualifying_booking_id IS NOT NULL AND qualified_at IS NOT NULL))
            AND (state NOT IN ('PENDING', 'REJECTED')
                 OR (qualifying_booking_id IS NULL AND qualified_at IS NULL))
    ),
    CONSTRAINT ck_referral_attributions_rejected CHECK (
        (state = 'REJECTED') = (rejection_reason IS NOT NULL)
    ),
    CONSTRAINT ck_referral_attributions_reversed CHECK (
        (state = 'REVERSED') = (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)
    ),
    -- Where the screening found a shared device, contact or payment instrument, qualifying anyway
    -- is a decision somebody makes on the record rather than an outcome the pipeline arrives at.
    CONSTRAINT ck_referral_attributions_override CHECK (
        state <> 'QUALIFIED'
            OR NOT (shared_device_signal OR shared_contact_signal OR shared_instrument_signal)
            OR (fraud_override_reason IS NOT NULL AND fraud_override_by IS NOT NULL)
    ),
    CONSTRAINT ck_referral_attributions_override_pair CHECK (
        (fraud_override_reason IS NULL) = (fraud_override_by IS NULL)
    ),
    CONSTRAINT ck_referral_attributions_version CHECK (version >= 0)
);

CREATE INDEX idx_referral_attributions_referrer
    ON referral_attributions (referrer_account_holder_id, state);
--rollback DROP TABLE referral_attributions;

--changeset ninggiangboy:034-07-referral-reward-grants
CREATE TABLE referral_reward_grants (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    referral_attribution_id     UUID NOT NULL,
    growth_program_version_id   UUID NOT NULL,
    beneficiary_side            VARCHAR(16) NOT NULL,
    beneficiary_holder_id       UUID NOT NULL,
    reward_kind                 VARCHAR(24) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    stored_value_lot_id         UUID,
    promotion_redemption_id     UUID,
    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    matures_at                  TIMESTAMPTZ NOT NULL,
    granted_at                  TIMESTAMPTZ,
    reversed_at                 TIMESTAMPTZ,
    reversal_reason             VARCHAR(64),
    forfeit_reason              VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_referral_reward_grants_side UNIQUE (referral_attribution_id, beneficiary_side),
    CONSTRAINT fk_referral_reward_grants_attribution FOREIGN KEY (referral_attribution_id)
        REFERENCES referral_attributions (id),
    CONSTRAINT fk_referral_reward_grants_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT fk_referral_reward_grants_holder FOREIGN KEY (beneficiary_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_referral_reward_grants_redemption FOREIGN KEY (promotion_redemption_id)
        REFERENCES promotion_redemptions (id),
    CONSTRAINT ck_referral_reward_grants_side CHECK (
        beneficiary_side IN ('REFERRER', 'REFEREE')
    ),
    CONSTRAINT ck_referral_reward_grants_kind CHECK (
        reward_kind IN ('CREDIT', 'PROMOTION')
    ),
    CONSTRAINT ck_referral_reward_grants_state CHECK (
        state IN ('PENDING', 'MATURED', 'GRANTED', 'REVERSED', 'FORFEITED')
    ),
    CONSTRAINT ck_referral_reward_grants_amount CHECK (
        amount_minor > 0 AND currency ~ '^[A-Z]{3}$'
    ),
    -- A granted reward has landed somewhere a guest can actually spend it: a credit lot or a
    -- promotion redemption, one of the two and never both. A reward that is GRANTED with neither
    -- is a number on a screen.
    CONSTRAINT ck_referral_reward_grants_landing CHECK (
        state NOT IN ('GRANTED', 'REVERSED')
            OR num_nonnulls(stored_value_lot_id, promotion_redemption_id) = 1
    ),
    CONSTRAINT ck_referral_reward_grants_kind_landing CHECK (
        (stored_value_lot_id IS NULL OR reward_kind = 'CREDIT')
            AND (promotion_redemption_id IS NULL OR reward_kind = 'PROMOTION')
    ),
    -- Same shape, same reason: a reversed reward was granted, and the instant it was granted is
    -- part of what a guest asking where their credit went is owed.
    CONSTRAINT ck_referral_reward_grants_granted CHECK (
        (state NOT IN ('GRANTED', 'REVERSED') OR granted_at IS NOT NULL)
            AND (state IN ('GRANTED', 'REVERSED') OR granted_at IS NULL)
    ),
    CONSTRAINT ck_referral_reward_grants_reversed CHECK (
        (state = 'REVERSED') = (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_referral_reward_grants_forfeited CHECK (
        (state = 'FORFEITED') = (forfeit_reason IS NOT NULL)
    ),
    CONSTRAINT ck_referral_reward_grants_version CHECK (version >= 0)
);

CREATE INDEX idx_referral_reward_grants_maturity ON referral_reward_grants (matures_at)
    WHERE state = 'PENDING';
--rollback DROP TABLE referral_reward_grants;

--changeset ninggiangboy:034-08-stored-value-accounts
CREATE TABLE stored_value_accounts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    account_kind                VARCHAR(24) NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    market_code                 VARCHAR(2),
    accounting_book_id          UUID NOT NULL,
    liability_account_id        UUID NOT NULL,
    balance_minor               BIGINT NOT NULL DEFAULT 0,
    reserved_minor              BIGINT NOT NULL DEFAULT 0,
    lifetime_granted_minor      BIGINT NOT NULL DEFAULT 0,
    lifetime_redeemed_minor     BIGINT NOT NULL DEFAULT 0,
    lifetime_expired_minor      BIGINT NOT NULL DEFAULT 0,
    lifecycle_state             VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    frozen_reason               VARCHAR(64),
    opened_at                   TIMESTAMPTZ NOT NULL,
    closed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_stored_value_accounts_holder UNIQUE (account_holder_id, account_kind, currency),
    CONSTRAINT fk_stored_value_accounts_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_stored_value_accounts_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_stored_value_accounts_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_stored_value_accounts_account FOREIGN KEY (liability_account_id)
        REFERENCES ledger_accounts (id),
    CONSTRAINT ck_stored_value_accounts_kind CHECK (
        account_kind IN ('PROMOTIONAL_CREDIT', 'GIFT_CARD_BALANCE', 'GOODWILL_CREDIT',
                         'REFUND_CREDIT')
    ),
    CONSTRAINT ck_stored_value_accounts_state CHECK (
        lifecycle_state IN ('ACTIVE', 'FROZEN', 'CLOSED')
    ),
    CONSTRAINT ck_stored_value_accounts_currency CHECK (
        currency ~ '^[A-Z]{3}$' AND (market_code IS NULL OR market_code ~ '^[A-Z]{2}$')
    ),
    -- A balance cannot go negative and a reservation cannot exceed the balance it is held against:
    -- the two together are what stop a quote spending credit that a second quote already holds.
    CONSTRAINT ck_stored_value_accounts_balance CHECK (
        balance_minor >= 0 AND reserved_minor >= 0 AND reserved_minor <= balance_minor
    ),
    CONSTRAINT ck_stored_value_accounts_lifetime CHECK (
        lifetime_granted_minor >= 0 AND lifetime_redeemed_minor >= 0
            AND lifetime_expired_minor >= 0
    ),
    CONSTRAINT ck_stored_value_accounts_frozen CHECK (
        lifecycle_state <> 'FROZEN' OR frozen_reason IS NOT NULL
    ),
    -- An account closed while it still holds value is value quietly taken back. Closing means the
    -- balance was spent, expired or refunded first.
    CONSTRAINT ck_stored_value_accounts_closed CHECK (
        (lifecycle_state = 'CLOSED') = (closed_at IS NOT NULL)
            AND (lifecycle_state <> 'CLOSED' OR balance_minor = 0)
    ),
    CONSTRAINT ck_stored_value_accounts_version CHECK (version >= 0)
);
--rollback DROP TABLE stored_value_accounts;

--changeset ninggiangboy:034-09-stored-value-lots
CREATE TABLE stored_value_lots (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stored_value_account_id     UUID NOT NULL,
    source_kind                 VARCHAR(24) NOT NULL,
    growth_program_version_id   UUID,
    source_reference_id         UUID,
    original_minor              BIGINT NOT NULL,
    remaining_minor             BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    granted_at                  TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    refundable                  BOOLEAN NOT NULL DEFAULT false,
    transferable                BOOLEAN NOT NULL DEFAULT false,
    restricted_market_code      VARCHAR(2),
    minimum_booking_minor       BIGINT,
    ledger_transaction_id       UUID NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    revoked_reason              VARCHAR(64),
    closed_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stored_value_lots_account FOREIGN KEY (stored_value_account_id)
        REFERENCES stored_value_accounts (id),
    CONSTRAINT fk_stored_value_lots_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT fk_stored_value_lots_market FOREIGN KEY (restricted_market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_stored_value_lots_transaction FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT ck_stored_value_lots_source CHECK (
        source_kind IN ('REFERRAL_REWARD', 'CAMPAIGN_GRANT', 'GIFT_CARD', 'GOODWILL',
                        'REFUND_TO_CREDIT', 'LOYALTY_BENEFIT', 'MANUAL_AWARD')
    ),
    CONSTRAINT ck_stored_value_lots_state CHECK (
        state IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_stored_value_lots_amounts CHECK (
        original_minor > 0 AND remaining_minor >= 0 AND remaining_minor <= original_minor
            AND (minimum_booking_minor IS NULL OR minimum_booking_minor > 0)
    ),
    CONSTRAINT ck_stored_value_lots_currency CHECK (
        currency ~ '^[A-Z]{3}$'
            AND (restricted_market_code IS NULL OR restricted_market_code ~ '^[A-Z]{2}$')
    ),
    -- One-way in each direction rather than an equality: an expired or revoked lot may still have
    -- carried a remaining balance at the moment it closed, and that figure is what a breakage
    -- posting and a guest complaint are both about.
    CONSTRAINT ck_stored_value_lots_remaining CHECK (
        (state <> 'ACTIVE' OR remaining_minor > 0)
            AND (state <> 'EXHAUSTED' OR remaining_minor = 0)
    ),
    CONSTRAINT ck_stored_value_lots_closed CHECK (
        (state = 'ACTIVE') = (closed_at IS NULL)
    ),
    CONSTRAINT ck_stored_value_lots_revoked CHECK (
        (state = 'REVOKED') = (revoked_reason IS NOT NULL)
    ),
    CONSTRAINT ck_stored_value_lots_expiry CHECK (
        expires_at IS NULL OR expires_at > granted_at
    ),
    CONSTRAINT ck_stored_value_lots_version CHECK (version >= 0)
);

-- Redemption draws down the lot that expires first, so the expiry order is the access path.
CREATE INDEX idx_stored_value_lots_draw
    ON stored_value_lots (stored_value_account_id, expires_at NULLS LAST, granted_at)
    WHERE state = 'ACTIVE';
--rollback DROP TABLE stored_value_lots;

--changeset ninggiangboy:034-10-stored-value-entries
CREATE TABLE stored_value_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stored_value_account_id     UUID NOT NULL,
    stored_value_lot_id         UUID,
    entry_kind                  VARCHAR(24) NOT NULL,
    balance_delta_minor         BIGINT NOT NULL,
    reserved_delta_minor        BIGINT NOT NULL,
    balance_after_minor         BIGINT NOT NULL,
    reserved_after_minor        BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    quote_id                    UUID,
    booking_id                  UUID,
    stored_value_hold_id        UUID,
    ledger_transaction_id       UUID,
    reverses_entry_id           UUID,
    reason_code                 VARCHAR(64),
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_stored_value_entries_account FOREIGN KEY (stored_value_account_id)
        REFERENCES stored_value_accounts (id),
    CONSTRAINT fk_stored_value_entries_lot FOREIGN KEY (stored_value_lot_id)
        REFERENCES stored_value_lots (id),
    CONSTRAINT fk_stored_value_entries_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_stored_value_entries_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_stored_value_entries_transaction FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_stored_value_entries_reverses FOREIGN KEY (reverses_entry_id)
        REFERENCES stored_value_entries (id),
    CONSTRAINT ck_stored_value_entries_kind CHECK (
        entry_kind IN ('GRANT', 'RESERVE', 'RELEASE', 'REDEEM', 'EXPIRE', 'REVOKE', 'BREAKAGE',
                       'TRANSFER_IN', 'TRANSFER_OUT', 'REVERSE')
    ),
    CONSTRAINT ck_stored_value_entries_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Each kind of movement has exactly one shape. Reservation moves nothing off the balance;
    -- redemption moves the same amount off both, because what is spent is what was held.
    CONSTRAINT ck_stored_value_entries_shape CHECK (
        (entry_kind NOT IN ('GRANT', 'TRANSFER_IN')
             OR (balance_delta_minor > 0 AND reserved_delta_minor = 0))
            AND (entry_kind <> 'RESERVE'
                 OR (balance_delta_minor = 0 AND reserved_delta_minor > 0))
            AND (entry_kind <> 'RELEASE'
                 OR (balance_delta_minor = 0 AND reserved_delta_minor < 0))
            AND (entry_kind <> 'REDEEM'
                 OR (balance_delta_minor < 0 AND balance_delta_minor = reserved_delta_minor))
            AND (entry_kind NOT IN ('EXPIRE', 'REVOKE', 'BREAKAGE', 'TRANSFER_OUT')
                 OR (balance_delta_minor < 0 AND reserved_delta_minor = 0))
    ),
    -- Every movement of the balance posts to the ledger. Holding and releasing a reservation moves
    -- no value and posts nothing, which is why the requirement is written against the delta rather
    -- than against the kind.
    CONSTRAINT ck_stored_value_entries_posting CHECK (
        (balance_delta_minor = 0) OR (ledger_transaction_id IS NOT NULL)
    ),
    CONSTRAINT ck_stored_value_entries_reversal CHECK (
        (entry_kind = 'REVERSE') = (reverses_entry_id IS NOT NULL)
    ),
    CONSTRAINT ck_stored_value_entries_after CHECK (
        balance_after_minor >= 0 AND reserved_after_minor >= 0
            AND reserved_after_minor <= balance_after_minor
    ),
    -- A redemption names the booking it paid for and the quote it was held against; an expiry
    -- names neither. A movement that cannot say what it was for is how credit disappears.
    CONSTRAINT ck_stored_value_entries_purpose CHECK (
        (entry_kind NOT IN ('RESERVE', 'RELEASE', 'REDEEM')
             OR (quote_id IS NOT NULL AND stored_value_hold_id IS NOT NULL))
            AND (entry_kind <> 'REDEEM' OR booking_id IS NOT NULL)
            AND (entry_kind NOT IN ('EXPIRE', 'REVOKE', 'BREAKAGE') OR reason_code IS NOT NULL)
    ),
    -- Value moves out of a lot, never out of the account in general: without the lot there is no
    -- answer to which promise expired and which one was spent.
    CONSTRAINT ck_stored_value_entries_lot CHECK (
        entry_kind = 'REVERSE' OR stored_value_lot_id IS NOT NULL
    )
);

CREATE INDEX idx_stored_value_entries_account
    ON stored_value_entries (stored_value_account_id, occurred_at DESC);
CREATE UNIQUE INDEX uk_stored_value_entries_reverses
    ON stored_value_entries (reverses_entry_id) WHERE reverses_entry_id IS NOT NULL;
--rollback DROP TABLE stored_value_entries;

--changeset ninggiangboy:034-11-stored-value-holds
CREATE TABLE stored_value_holds (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stored_value_account_id     UUID NOT NULL,
    quote_id                    UUID NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'HELD',
    held_at                     TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    resolved_at                 TIMESTAMPTZ,
    booking_id                  UUID,
    release_reason              VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_stored_value_holds_quote UNIQUE (quote_id),
    CONSTRAINT fk_stored_value_holds_account FOREIGN KEY (stored_value_account_id)
        REFERENCES stored_value_accounts (id),
    CONSTRAINT fk_stored_value_holds_quote FOREIGN KEY (quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_stored_value_holds_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT ck_stored_value_holds_state CHECK (
        state IN ('HELD', 'CONSUMED', 'RELEASED', 'EXPIRED')
    ),
    CONSTRAINT ck_stored_value_holds_amount CHECK (
        amount_minor > 0 AND currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_stored_value_holds_resolved CHECK (
        (state = 'HELD') = (resolved_at IS NULL)
    ),
    CONSTRAINT ck_stored_value_holds_consumed CHECK (
        (state = 'CONSUMED') = (booking_id IS NOT NULL)
    ),
    CONSTRAINT ck_stored_value_holds_released CHECK (
        state <> 'RELEASED' OR release_reason IS NOT NULL
    ),
    -- A hold with no expiry is credit a guest can never get back, because nothing ever releases it.
    CONSTRAINT ck_stored_value_holds_expiry CHECK (expires_at > held_at),
    CONSTRAINT ck_stored_value_holds_version CHECK (version >= 0)
);

CREATE INDEX idx_stored_value_holds_expiry ON stored_value_holds (expires_at)
    WHERE state = 'HELD';
--rollback DROP TABLE stored_value_holds;

--changeset ninggiangboy:034-12-gift-cards
CREATE TABLE gift_cards (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_version_id   UUID NOT NULL,
    serial_reference            VARCHAR(64) NOT NULL,
    code_digest                 CHAR(64) NOT NULL,
    issuing_legal_entity_id     UUID NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,
    face_value_minor            BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    purchaser_account_holder_id UUID,
    purchase_payment_reference  VARCHAR(128),
    recipient_contact_digest    CHAR(64),
    recipient_holder_id         UUID,
    breakage_policy             VARCHAR(24) NOT NULL,
    issued_at                   TIMESTAMPTZ NOT NULL,
    activated_at                TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ,
    redeemed_at                 TIMESTAMPTZ,
    redeemed_into_lot_id        UUID,
    breakage_recognized_at      TIMESTAMPTZ,
    breakage_transaction_id     UUID,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
    void_reason                 VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_gift_cards_serial UNIQUE (serial_reference),
    CONSTRAINT uk_gift_cards_code UNIQUE (code_digest),
    CONSTRAINT fk_gift_cards_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT fk_gift_cards_entity FOREIGN KEY (issuing_legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_gift_cards_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_gift_cards_purchaser FOREIGN KEY (purchaser_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_gift_cards_recipient FOREIGN KEY (recipient_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_gift_cards_lot FOREIGN KEY (redeemed_into_lot_id)
        REFERENCES stored_value_lots (id),
    CONSTRAINT fk_gift_cards_breakage FOREIGN KEY (breakage_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT ck_gift_cards_state CHECK (
        state IN ('ISSUED', 'ACTIVE', 'REDEEMED', 'EXPIRED', 'VOIDED')
    ),
    CONSTRAINT ck_gift_cards_breakage_policy CHECK (
        breakage_policy IN ('NEVER_EXPIRES', 'EXPIRES_TO_BREAKAGE', 'EXPIRES_TO_REFUND')
    ),
    CONSTRAINT ck_gift_cards_amount CHECK (
        face_value_minor > 0 AND currency ~ '^[A-Z]{3}$' AND market_code ~ '^[A-Z]{2}$'
    ),
    -- Several markets forbid gift-card expiry outright. Making the expiry column agree with the
    -- declared policy is what stops a card issued under a no-expiry rule from quietly acquiring a
    -- date, and stops a card issued with a date from having no policy behind it.
    CONSTRAINT ck_gift_cards_expiry_policy CHECK (
        (breakage_policy = 'NEVER_EXPIRES') = (expires_at IS NULL)
    ),
    CONSTRAINT ck_gift_cards_expiry_order CHECK (expires_at IS NULL OR expires_at > issued_at),
    CONSTRAINT ck_gift_cards_activated CHECK (state = 'ISSUED' OR activated_at IS NOT NULL),
    CONSTRAINT ck_gift_cards_redeemed CHECK (
        (state = 'REDEEMED') = (redeemed_at IS NOT NULL AND redeemed_into_lot_id IS NOT NULL)
    ),
    CONSTRAINT ck_gift_cards_voided CHECK ((state = 'VOIDED') = (void_reason IS NOT NULL)),
    -- Breakage is revenue recognised from value a guest paid for and did not use. It is only
    -- available where the policy said so, and it posts like any other movement of money.
    CONSTRAINT ck_gift_cards_breakage CHECK (
        (breakage_recognized_at IS NULL) = (breakage_transaction_id IS NULL)
            AND (breakage_recognized_at IS NULL OR breakage_policy = 'EXPIRES_TO_BREAKAGE')
    ),
    CONSTRAINT ck_gift_cards_version CHECK (version >= 0)
);

CREATE INDEX idx_gift_cards_expiry ON gift_cards (expires_at)
    WHERE state = 'ACTIVE' AND expires_at IS NOT NULL;
--rollback DROP TABLE gift_cards;

--changeset ninggiangboy:034-13-loyalty-tier-definitions
CREATE TABLE loyalty_tier_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_version_id   UUID NOT NULL,
    tier_key                    VARCHAR(32) NOT NULL,
    tier_rank                   SMALLINT NOT NULL,
    display_name                VARCHAR(120) NOT NULL,
    qualification_window_days   INTEGER NOT NULL,
    qualifying_nights           INTEGER,
    qualifying_bookings         INTEGER,
    qualifying_spend_minor      BIGINT,
    qualifying_currency         VARCHAR(3),
    benefit_summary             VARCHAR(1000) NOT NULL,
    benefit_payload             JSONB NOT NULL,
    downgrade_grace_days        INTEGER NOT NULL DEFAULT 0,
    partner_status_matchable    BOOLEAN NOT NULL DEFAULT false,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_loyalty_tier_definitions_key UNIQUE (growth_program_version_id, tier_key),
    CONSTRAINT uk_loyalty_tier_definitions_rank UNIQUE (growth_program_version_id, tier_rank),
    CONSTRAINT fk_loyalty_tier_definitions_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT ck_loyalty_tier_definitions_rank CHECK (tier_rank >= 0 AND tier_rank <= 10),
    CONSTRAINT ck_loyalty_tier_definitions_window CHECK (
        qualification_window_days > 0 AND downgrade_grace_days >= 0
    ),
    -- Every tier above the base one is reachable by something measurable. A tier with no threshold
    -- is a tier the platform hands out at its discretion, which is the marketplace imbalance the
    -- domain document warns about wearing a loyalty badge.
    CONSTRAINT ck_loyalty_tier_definitions_thresholds CHECK (
        tier_rank = 0
            OR num_nonnulls(qualifying_nights, qualifying_bookings, qualifying_spend_minor) >= 1
    ),
    CONSTRAINT ck_loyalty_tier_definitions_amounts CHECK (
        (qualifying_nights IS NULL OR qualifying_nights > 0)
            AND (qualifying_bookings IS NULL OR qualifying_bookings > 0)
            AND (qualifying_spend_minor IS NULL OR qualifying_spend_minor > 0)
            AND (qualifying_spend_minor IS NULL) = (qualifying_currency IS NULL)
            AND (qualifying_currency IS NULL OR qualifying_currency ~ '^[A-Z]{3}$')
    )
);
--rollback DROP TABLE loyalty_tier_definitions;

--changeset ninggiangboy:034-14-loyalty-memberships
CREATE TABLE loyalty_memberships (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    growth_program_id           UUID NOT NULL,
    current_tier_definition_id  UUID NOT NULL,
    current_program_version_id  UUID NOT NULL,
    window_start                DATE NOT NULL,
    window_end                  DATE NOT NULL,
    window_time_zone            VARCHAR(64) NOT NULL,
    qualifying_nights           INTEGER NOT NULL DEFAULT 0,
    qualifying_bookings         INTEGER NOT NULL DEFAULT 0,
    qualifying_spend_minor      BIGINT NOT NULL DEFAULT 0,
    spend_currency              VARCHAR(3) NOT NULL,
    tier_effective_from         TIMESTAMPTZ NOT NULL,
    tier_review_at              TIMESTAMPTZ NOT NULL,
    downgrade_protected_until   TIMESTAMPTZ,
    membership_state            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    state_reason                VARCHAR(64),
    joined_at                   TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_loyalty_memberships_holder UNIQUE (account_holder_id, growth_program_id),
    CONSTRAINT fk_loyalty_memberships_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_loyalty_memberships_program FOREIGN KEY (growth_program_id)
        REFERENCES growth_programs (id),
    CONSTRAINT fk_loyalty_memberships_tier FOREIGN KEY (current_tier_definition_id)
        REFERENCES loyalty_tier_definitions (id),
    CONSTRAINT fk_loyalty_memberships_version FOREIGN KEY (current_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT ck_loyalty_memberships_state CHECK (
        membership_state IN ('ACTIVE', 'LAPSED', 'SUSPENDED', 'CLOSED')
    ),
    -- The qualification window is a civil window, so it carries the zone it is computed in for the
    -- same reason every other civil deadline in this schema does: a stay that counted in Hanoi
    -- must not stop counting because the job that evaluated it ran in another zone.
    CONSTRAINT ck_loyalty_memberships_window CHECK (window_end > window_start),
    CONSTRAINT ck_loyalty_memberships_counts CHECK (
        qualifying_nights >= 0 AND qualifying_bookings >= 0 AND qualifying_spend_minor >= 0
            AND spend_currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_loyalty_memberships_review CHECK (tier_review_at > tier_effective_from),
    CONSTRAINT ck_loyalty_memberships_reason CHECK (
        (membership_state IN ('SUSPENDED', 'CLOSED')) = (state_reason IS NOT NULL)
    ),
    CONSTRAINT ck_loyalty_memberships_version CHECK (version >= 0)
);

CREATE INDEX idx_loyalty_memberships_review ON loyalty_memberships (tier_review_at)
    WHERE membership_state = 'ACTIVE';
--rollback DROP TABLE loyalty_memberships;

--changeset ninggiangboy:034-15-loyalty-qualifying-events
CREATE TABLE loyalty_qualifying_events (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loyalty_membership_id       UUID NOT NULL,
    event_kind                  VARCHAR(24) NOT NULL,
    booking_id                  UUID,
    occurred_at                 TIMESTAMPTZ NOT NULL,
    counted_window_start        DATE NOT NULL,
    nights_counted              INTEGER NOT NULL DEFAULT 0,
    bookings_counted            INTEGER NOT NULL DEFAULT 0,
    spend_minor                 BIGINT NOT NULL DEFAULT 0,
    currency                    VARCHAR(3) NOT NULL,
    reverses_event_id           UUID,
    reversal_reason             VARCHAR(64),
    adjustment_reason           VARCHAR(64),
    approved_by                 UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loyalty_qualifying_events_membership FOREIGN KEY (loyalty_membership_id)
        REFERENCES loyalty_memberships (id),
    CONSTRAINT fk_loyalty_qualifying_events_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_loyalty_qualifying_events_reverses FOREIGN KEY (reverses_event_id)
        REFERENCES loyalty_qualifying_events (id),
    CONSTRAINT ck_loyalty_qualifying_events_kind CHECK (
        event_kind IN ('STAY_COMPLETED', 'BOOKING_CONFIRMED', 'PARTNER_ACTIVITY',
                       'MANUAL_ADJUSTMENT')
    ),
    CONSTRAINT ck_loyalty_qualifying_events_booking CHECK (
        (event_kind IN ('STAY_COMPLETED', 'BOOKING_CONFIRMED')) = (booking_id IS NOT NULL)
    ),
    -- Credit added by hand is credit somebody is answerable for.
    CONSTRAINT ck_loyalty_qualifying_events_manual CHECK (
        (event_kind = 'MANUAL_ADJUSTMENT')
            = (adjustment_reason IS NOT NULL AND approved_by IS NOT NULL)
    ),
    -- A cancelled stay takes its nights back by appending a negative event, never by editing the
    -- one that counted them: the tier a guest held last month has to stay explainable.
    CONSTRAINT ck_loyalty_qualifying_events_reversal CHECK (
        (reverses_event_id IS NULL) = (reversal_reason IS NULL)
            AND (reverses_event_id IS NULL
                 OR (nights_counted <= 0 AND bookings_counted <= 0 AND spend_minor <= 0))
            AND (reverses_event_id IS NOT NULL
                 OR (nights_counted >= 0 AND bookings_counted >= 0 AND spend_minor >= 0))
    ),
    CONSTRAINT ck_loyalty_qualifying_events_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_loyalty_qualifying_events_membership
    ON loyalty_qualifying_events (loyalty_membership_id, occurred_at DESC);
CREATE UNIQUE INDEX uk_loyalty_qualifying_events_reverses
    ON loyalty_qualifying_events (reverses_event_id) WHERE reverses_event_id IS NOT NULL;
--rollback DROP TABLE loyalty_qualifying_events;

--changeset ninggiangboy:034-16-loyalty-tier-transitions
CREATE TABLE loyalty_tier_transitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loyalty_membership_id       UUID NOT NULL,
    from_tier_definition_id     UUID,
    to_tier_definition_id       UUID NOT NULL,
    transition_reason           VARCHAR(24) NOT NULL,
    evidence_summary            VARCHAR(1000) NOT NULL,
    qualifying_nights           INTEGER NOT NULL,
    qualifying_bookings         INTEGER NOT NULL,
    qualifying_spend_minor      BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    window_start                DATE NOT NULL,
    window_end                  DATE NOT NULL,
    effective_from              TIMESTAMPTZ NOT NULL,
    review_at                   TIMESTAMPTZ,
    approved_by                 UUID,
    approval_reason             VARCHAR(64),
    notification_intent_id      UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_loyalty_tier_transitions_membership FOREIGN KEY (loyalty_membership_id)
        REFERENCES loyalty_memberships (id),
    CONSTRAINT fk_loyalty_tier_transitions_from FOREIGN KEY (from_tier_definition_id)
        REFERENCES loyalty_tier_definitions (id),
    CONSTRAINT fk_loyalty_tier_transitions_to FOREIGN KEY (to_tier_definition_id)
        REFERENCES loyalty_tier_definitions (id),
    CONSTRAINT fk_loyalty_tier_transitions_intent FOREIGN KEY (notification_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT ck_loyalty_tier_transitions_reason CHECK (
        transition_reason IN ('ENROLLED', 'QUALIFIED', 'DOWNGRADED', 'MANUAL_GRANT',
                              'PARTNER_STATUS_MATCH', 'PROGRAM_CLOSED')
    ),
    CONSTRAINT ck_loyalty_tier_transitions_tiers CHECK (
        from_tier_definition_id IS DISTINCT FROM to_tier_definition_id
            AND (transition_reason = 'ENROLLED') = (from_tier_definition_id IS NULL)
    ),
    -- A tier granted outside the published thresholds is a discretionary benefit, and the person
    -- who exercised the discretion is named in the same row.
    CONSTRAINT ck_loyalty_tier_transitions_approval CHECK (
        (transition_reason IN ('MANUAL_GRANT', 'PARTNER_STATUS_MATCH'))
            = (approved_by IS NOT NULL AND approval_reason IS NOT NULL)
    ),
    -- A downgrade is the transition a guest is most likely to dispute, so it carries the same
    -- counts and the same window the qualification was judged against.
    CONSTRAINT ck_loyalty_tier_transitions_counts CHECK (
        qualifying_nights >= 0 AND qualifying_bookings >= 0 AND qualifying_spend_minor >= 0
            AND currency ~ '^[A-Z]{3}$' AND window_end > window_start
    )
);

CREATE INDEX idx_loyalty_tier_transitions_membership
    ON loyalty_tier_transitions (loyalty_membership_id, effective_from DESC);
--rollback DROP TABLE loyalty_tier_transitions;

--changeset ninggiangboy:034-17-growth-campaigns
CREATE TABLE growth_campaigns (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_version_id   UUID NOT NULL,
    campaign_key                VARCHAR(64) NOT NULL,
    display_name                VARCHAR(160) NOT NULL,
    objective                   VARCHAR(32) NOT NULL,
    market_code                 VARCHAR(2),
    audience_definition         JSONB NOT NULL,
    audience_explanation        VARCHAR(1000) NOT NULL,
    required_consent_category   VARCHAR(48) NOT NULL,
    required_channel            VARCHAR(16) NOT NULL,
    frequency_cap_per_window    INTEGER NOT NULL,
    frequency_window_days       INTEGER NOT NULL,
    holdout_share               NUMERIC(5,4) NOT NULL,
    experiment_definition_id    UUID,
    send_window_from            TIMESTAMPTZ NOT NULL,
    send_window_until           TIMESTAMPTZ NOT NULL,
    fairness_review_reference   VARCHAR(128),
    fairness_reviewed_at        TIMESTAMPTZ,
    price_transparency_attested BOOLEAN NOT NULL DEFAULT false,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    cancellation_reason         VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_growth_campaigns_key UNIQUE (campaign_key),
    CONSTRAINT fk_growth_campaigns_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT fk_growth_campaigns_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_growth_campaigns_experiment FOREIGN KEY (experiment_definition_id)
        REFERENCES experiment_definitions (id),
    CONSTRAINT ck_growth_campaigns_objective CHECK (
        objective IN ('ACQUISITION', 'REACTIVATION', 'DESTINATION', 'SUPPLY_GROWTH', 'RETENTION',
                      'WINBACK')
    ),
    CONSTRAINT ck_growth_campaigns_channel CHECK (
        required_channel IN ('EMAIL', 'SMS', 'PUSH', 'IN_APP')
    ),
    CONSTRAINT ck_growth_campaigns_status CHECK (
        status IN ('DRAFT', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT ck_growth_campaigns_market_code CHECK (
        market_code IS NULL OR market_code ~ '^[A-Z]{2}$'
    ),
    -- A frequency cap of zero is not a cap, it is a campaign that cannot run; a window of zero days
    -- is a cap that resets continuously, which is the same thing as having none.
    CONSTRAINT ck_growth_campaigns_frequency CHECK (
        frequency_cap_per_window > 0 AND frequency_window_days > 0
    ),
    -- A holdout share of zero is allowed and means the campaign is not claiming to be measured.
    -- What is not allowed is claiming uplift later without one: see campaign_uplift_results.
    CONSTRAINT ck_growth_campaigns_holdout CHECK (
        holdout_share >= 0 AND holdout_share < 1
    ),
    CONSTRAINT ck_growth_campaigns_window CHECK (send_window_until > send_window_from),
    -- Leaving DRAFT is the point at which a campaign becomes something done to real people, so it
    -- is the point at which the fairness review and the price-transparency attestation are owed.
    CONSTRAINT ck_growth_campaigns_review CHECK (
        status = 'DRAFT'
            OR (price_transparency_attested AND fairness_reviewed_at IS NOT NULL
                AND fairness_review_reference IS NOT NULL)
    ),
    CONSTRAINT ck_growth_campaigns_cancelled CHECK (
        (status = 'CANCELLED') = (cancellation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_growth_campaigns_version CHECK (version >= 0)
);
--rollback DROP TABLE growth_campaigns;

--changeset ninggiangboy:034-18-campaign-audience-memberships
CREATE TABLE campaign_audience_memberships (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_campaign_id          UUID NOT NULL,
    account_holder_id           UUID NOT NULL,
    arm                         VARCHAR(16) NOT NULL,
    experiment_assignment_id    UUID,
    eligibility_evaluation_id   UUID NOT NULL,
    communication_consent_id    UUID,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ELIGIBLE',
    suppression_reason          VARCHAR(48),
    entered_at                  TIMESTAMPTZ NOT NULL,
    contact_count               INTEGER NOT NULL DEFAULT 0,
    last_contacted_at           TIMESTAMPTZ,
    converted_at                TIMESTAMPTZ,
    conversion_booking_id       UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_campaign_audience_memberships_holder
        UNIQUE (growth_campaign_id, account_holder_id),
    CONSTRAINT fk_campaign_audience_memberships_campaign FOREIGN KEY (growth_campaign_id)
        REFERENCES growth_campaigns (id),
    CONSTRAINT fk_campaign_audience_memberships_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_campaign_audience_memberships_assignment FOREIGN KEY (experiment_assignment_id)
        REFERENCES experiment_assignments (id),
    CONSTRAINT fk_campaign_audience_memberships_evaluation FOREIGN KEY (eligibility_evaluation_id)
        REFERENCES growth_eligibility_evaluations (id),
    CONSTRAINT fk_campaign_audience_memberships_consent FOREIGN KEY (communication_consent_id)
        REFERENCES communication_consents (id),
    CONSTRAINT ck_campaign_audience_memberships_arm CHECK (arm IN ('TREATMENT', 'HOLDOUT')),
    CONSTRAINT ck_campaign_audience_memberships_state CHECK (
        state IN ('ELIGIBLE', 'SUPPRESSED', 'CONTACTED', 'CONVERTED', 'EXCLUDED')
    ),
    CONSTRAINT ck_campaign_audience_memberships_suppressed CHECK (
        (state = 'SUPPRESSED') = (suppression_reason IS NOT NULL)
    ),
    CONSTRAINT ck_campaign_audience_memberships_converted CHECK (
        (state = 'CONVERTED') = (converted_at IS NOT NULL)
    ),
    -- The holdout is the only evidence the campaign did anything. A holdout that gets contacted
    -- once, for one send, quietly turns the measurement into a comparison of nothing.
    CONSTRAINT ck_campaign_audience_memberships_holdout CHECK (
        arm <> 'HOLDOUT' OR contact_count = 0
    ),
    -- Nobody is contacted without a consent row behind the contact.
    CONSTRAINT ck_campaign_audience_memberships_consent CHECK (
        contact_count = 0 OR communication_consent_id IS NOT NULL
    ),
    CONSTRAINT ck_campaign_audience_memberships_counts CHECK (
        contact_count >= 0 AND (contact_count = 0) = (last_contacted_at IS NULL)
    ),
    CONSTRAINT ck_campaign_audience_memberships_version CHECK (version >= 0)
);

CREATE INDEX idx_campaign_audience_memberships_campaign
    ON campaign_audience_memberships (growth_campaign_id, state);
--rollback DROP TABLE campaign_audience_memberships;

--changeset ninggiangboy:034-19-campaign-touchpoints
CREATE TABLE campaign_touchpoints (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audience_membership_id      UUID NOT NULL,
    touchpoint_number           INTEGER NOT NULL,
    notification_intent_id      UUID NOT NULL,
    channel                     VARCHAR(16) NOT NULL,
    communication_consent_id    UUID NOT NULL,
    consent_checked_at          TIMESTAMPTZ NOT NULL,
    requested_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_campaign_touchpoints_number UNIQUE (audience_membership_id, touchpoint_number),
    CONSTRAINT uk_campaign_touchpoints_intent UNIQUE (notification_intent_id),
    CONSTRAINT fk_campaign_touchpoints_membership FOREIGN KEY (audience_membership_id)
        REFERENCES campaign_audience_memberships (id),
    CONSTRAINT fk_campaign_touchpoints_intent FOREIGN KEY (notification_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_campaign_touchpoints_consent FOREIGN KEY (communication_consent_id)
        REFERENCES communication_consents (id),
    CONSTRAINT ck_campaign_touchpoints_channel CHECK (
        channel IN ('EMAIL', 'SMS', 'PUSH', 'IN_APP')
    ),
    CONSTRAINT ck_campaign_touchpoints_number CHECK (touchpoint_number > 0),
    CONSTRAINT ck_campaign_touchpoints_checked CHECK (consent_checked_at <= requested_at)
);

CREATE INDEX idx_campaign_touchpoints_requested
    ON campaign_touchpoints (audience_membership_id, requested_at DESC);
--rollback DROP TABLE campaign_touchpoints;

--changeset ninggiangboy:034-20-campaign-uplift-results
CREATE TABLE campaign_uplift_results (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_campaign_id          UUID NOT NULL,
    experiment_analysis_run_id  UUID NOT NULL,
    metric_definition_id        UUID NOT NULL,
    treatment_subjects          BIGINT NOT NULL,
    holdout_subjects            BIGINT NOT NULL,
    treatment_rate              NUMERIC(16,8),
    holdout_rate                NUMERIC(16,8),
    incremental_effect          NUMERIC(16,8) NOT NULL,
    effect_interval_low         NUMERIC(16,8) NOT NULL,
    effect_interval_high        NUMERIC(16,8) NOT NULL,
    interval_confidence         NUMERIC(5,4) NOT NULL,
    incremental_cost_minor      BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    cost_per_incremental_minor  BIGINT,
    conclusion                  VARCHAR(24) NOT NULL,
    conclusion_rationale        VARCHAR(1000) NOT NULL,
    computed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_campaign_uplift_results_run
        UNIQUE (growth_campaign_id, experiment_analysis_run_id, metric_definition_id),
    CONSTRAINT fk_campaign_uplift_results_campaign FOREIGN KEY (growth_campaign_id)
        REFERENCES growth_campaigns (id),
    CONSTRAINT fk_campaign_uplift_results_run FOREIGN KEY (experiment_analysis_run_id)
        REFERENCES experiment_analysis_runs (id),
    CONSTRAINT fk_campaign_uplift_results_metric FOREIGN KEY (metric_definition_id)
        REFERENCES metric_definitions (id),
    CONSTRAINT ck_campaign_uplift_results_conclusion CHECK (
        conclusion IN ('INCREMENTAL', 'NO_DETECTED_EFFECT', 'HARMFUL')
    ),
    -- No holdout, no claim. A campaign measured only against the people it contacted counts
    -- redemptions, which tells you who took the money and not whether anyone behaved differently.
    CONSTRAINT ck_campaign_uplift_results_holdout CHECK (
        holdout_subjects > 0 AND treatment_subjects > 0
    ),
    CONSTRAINT ck_campaign_uplift_results_interval CHECK (
        effect_interval_low <= incremental_effect AND incremental_effect <= effect_interval_high
            AND interval_confidence > 0.5 AND interval_confidence < 1
    ),
    -- The conclusion is not a summary somebody writes; it is the interval's position relative to
    -- zero. Between the two cases lies the honest third answer: the campaign was not measurable.
    CONSTRAINT ck_campaign_uplift_results_verdict CHECK (
        (conclusion = 'INCREMENTAL') = (effect_interval_low > 0)
            AND (conclusion = 'HARMFUL') = (effect_interval_high < 0)
    ),
    -- Cost per incremental unit divides by the effect, so it exists only where there was one.
    CONSTRAINT ck_campaign_uplift_results_cost CHECK (
        incremental_cost_minor >= 0 AND currency ~ '^[A-Z]{3}$'
            AND (cost_per_incremental_minor IS NULL OR conclusion = 'INCREMENTAL')
    )
);
--rollback DROP TABLE campaign_uplift_results;

--changeset ninggiangboy:034-21-affiliate-partners
CREATE TABLE affiliate_partners (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    growth_program_version_id   UUID NOT NULL,
    partner_key                 VARCHAR(64) NOT NULL,
    legal_name                  VARCHAR(255) NOT NULL,
    country_code                VARCHAR(2) NOT NULL,
    registration_reference      VARCHAR(128),
    tax_form_reference          VARCHAR(128),
    commission_percent          NUMERIC(6,4) NOT NULL,
    commission_currency         VARCHAR(3) NOT NULL,
    attribution_model           VARCHAR(24) NOT NULL,
    attribution_window_days     INTEGER NOT NULL,
    payout_hold_days            INTEGER NOT NULL,
    payout_destination_ref      VARCHAR(128),
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    suspension_reason           VARCHAR(64),
    activated_at                TIMESTAMPTZ,
    terminated_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_affiliate_partners_key UNIQUE (partner_key),
    CONSTRAINT fk_affiliate_partners_version FOREIGN KEY (growth_program_version_id)
        REFERENCES growth_program_versions (id),
    CONSTRAINT ck_affiliate_partners_model CHECK (
        attribution_model IN ('LAST_CLICK', 'FIRST_CLICK')
    ),
    CONSTRAINT ck_affiliate_partners_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'TERMINATED')
    ),
    CONSTRAINT ck_affiliate_partners_commission CHECK (
        commission_percent > 0 AND commission_percent <= 0.5
            AND commission_currency ~ '^[A-Z]{3}$' AND country_code ~ '^[A-Z]{2}$'
    ),
    -- An unbounded attribution window lets a partner claim a booking made months after a click it
    -- had nothing to do with. Ninety days is the outer edge of a defensible claim.
    CONSTRAINT ck_affiliate_partners_window CHECK (
        attribution_window_days > 0 AND attribution_window_days <= 90
    ),
    -- The payout hold is what makes a cancellation reversible. Paying a commission the day the
    -- booking is made means recovering it from a partner later, which is a different problem.
    CONSTRAINT ck_affiliate_partners_hold CHECK (payout_hold_days >= 0),
    CONSTRAINT ck_affiliate_partners_activated CHECK (
        status = 'DRAFT' OR activated_at IS NOT NULL
    ),
    CONSTRAINT ck_affiliate_partners_suspension CHECK (
        status <> 'SUSPENDED' OR suspension_reason IS NOT NULL
    ),
    CONSTRAINT ck_affiliate_partners_terminated CHECK (
        (status = 'TERMINATED') = (terminated_at IS NOT NULL)
    ),
    CONSTRAINT ck_affiliate_partners_version CHECK (version >= 0)
);
--rollback DROP TABLE affiliate_partners;

--changeset ninggiangboy:034-22-affiliate-attributions
CREATE TABLE affiliate_attributions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    affiliate_partner_id        UUID NOT NULL,
    click_reference             VARCHAR(128) NOT NULL,
    landing_surface             VARCHAR(32) NOT NULL,
    anonymous_unit_key          VARCHAR(128),
    account_holder_id           UUID,
    clicked_at                  TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    booking_id                  UUID,
    attributed_at               TIMESTAMPTZ,
    state                       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    rejection_reason            VARCHAR(64),
    fraud_signals               JSONB NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_affiliate_attributions_click UNIQUE (affiliate_partner_id, click_reference),
    CONSTRAINT fk_affiliate_attributions_partner FOREIGN KEY (affiliate_partner_id)
        REFERENCES affiliate_partners (id),
    CONSTRAINT fk_affiliate_attributions_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_affiliate_attributions_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT ck_affiliate_attributions_state CHECK (
        state IN ('OPEN', 'ATTRIBUTED', 'EXPIRED', 'REJECTED')
    ),
    CONSTRAINT ck_affiliate_attributions_subject CHECK (
        num_nonnulls(anonymous_unit_key, account_holder_id) >= 1
    ),
    CONSTRAINT ck_affiliate_attributions_attributed CHECK (
        (state = 'ATTRIBUTED') = (booking_id IS NOT NULL AND attributed_at IS NOT NULL)
    ),
    CONSTRAINT ck_affiliate_attributions_rejected CHECK (
        (state = 'REJECTED') = (rejection_reason IS NOT NULL)
    ),
    CONSTRAINT ck_affiliate_attributions_window CHECK (expires_at > clicked_at),
    CONSTRAINT ck_affiliate_attributions_version CHECK (version >= 0)
);

-- A booking is credited to at most one partner. Without this, two partners each holding a click
-- both invoice for the same stay, and the platform pays twice for one guest.
CREATE UNIQUE INDEX uk_affiliate_attributions_booking
    ON affiliate_attributions (booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX idx_affiliate_attributions_open
    ON affiliate_attributions (affiliate_partner_id, expires_at) WHERE state = 'OPEN';
--rollback DROP TABLE affiliate_attributions;

--changeset ninggiangboy:034-23-affiliate-commissions
CREATE TABLE affiliate_commissions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    affiliate_attribution_id    UUID NOT NULL,
    affiliate_partner_id        UUID NOT NULL,
    booking_id                  UUID NOT NULL,
    commissionable_base_minor   BIGINT NOT NULL,
    commission_percent          NUMERIC(6,4) NOT NULL,
    commission_minor            BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    matures_at                  TIMESTAMPTZ NOT NULL,
    earned_at                   TIMESTAMPTZ,
    ledger_transaction_id       UUID,
    paid_at                     TIMESTAMPTZ,
    payout_instruction_id       UUID,
    reversed_at                 TIMESTAMPTZ,
    reversal_reason             VARCHAR(64),
    reversal_transaction_id     UUID,
    withheld_reason             VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_affiliate_commissions_attribution UNIQUE (affiliate_attribution_id),
    CONSTRAINT fk_affiliate_commissions_attribution FOREIGN KEY (affiliate_attribution_id)
        REFERENCES affiliate_attributions (id),
    CONSTRAINT fk_affiliate_commissions_partner FOREIGN KEY (affiliate_partner_id)
        REFERENCES affiliate_partners (id),
    CONSTRAINT fk_affiliate_commissions_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_affiliate_commissions_transaction FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_affiliate_commissions_reversal FOREIGN KEY (reversal_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_affiliate_commissions_payout FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT ck_affiliate_commissions_state CHECK (
        state IN ('PENDING', 'EARNED', 'PAID', 'REVERSED', 'WITHHELD')
    ),
    CONSTRAINT ck_affiliate_commissions_amounts CHECK (
        commissionable_base_minor >= 0 AND commission_minor >= 0
            AND commission_percent > 0 AND commission_percent <= 0.5
            AND currency ~ '^[A-Z]{3}$'
    ),
    -- The commission equals its own stated terms, to the rounding. A figure that does not follow
    -- from the base and the percentage is a number somebody chose, and that is where an affiliate
    -- programme stops being a commission and becomes a negotiated payment nobody approved.
    CONSTRAINT ck_affiliate_commissions_arithmetic CHECK (
        commission_minor
            BETWEEN floor(commissionable_base_minor * commission_percent)
            AND ceil(commissionable_base_minor * commission_percent)
    ),
    -- Earning is what posts, so the instant and the posting travel together. WITHHELD is left
    -- unconstrained on purpose: a commission can be held back before it is earned, for a missing
    -- tax form, or after it is earned, while a fraud review runs.
    CONSTRAINT ck_affiliate_commissions_earned CHECK (
        (state <> 'PENDING' OR (earned_at IS NULL AND ledger_transaction_id IS NULL))
            AND (state NOT IN ('EARNED', 'PAID', 'REVERSED')
                 OR (earned_at IS NOT NULL AND ledger_transaction_id IS NOT NULL))
    ),
    CONSTRAINT ck_affiliate_commissions_paid CHECK (
        (state = 'PAID') = (paid_at IS NOT NULL AND payout_instruction_id IS NOT NULL)
    ),
    CONSTRAINT ck_affiliate_commissions_reversed CHECK (
        (state = 'REVERSED')
            = (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL
               AND reversal_transaction_id IS NOT NULL)
    ),
    CONSTRAINT ck_affiliate_commissions_withheld CHECK (
        (state = 'WITHHELD') = (withheld_reason IS NOT NULL)
    ),
    CONSTRAINT ck_affiliate_commissions_version CHECK (version >= 0)
);

CREATE INDEX idx_affiliate_commissions_maturity ON affiliate_commissions (matures_at)
    WHERE state = 'EARNED';
--rollback DROP TABLE affiliate_commissions;

--changeset ninggiangboy:034-24-listing-collections
CREATE TABLE listing_collections (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    title                       VARCHAR(120) NOT NULL,
    description                 VARCHAR(500),
    visibility                  VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    share_token_digest          CHAR(64),
    item_count                  INTEGER NOT NULL DEFAULT 0,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    archived_at                 TIMESTAMPTZ,
    deleted_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listing_collections_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_listing_collections_visibility CHECK (
        visibility IN ('PRIVATE', 'LINK_SHARED', 'PUBLIC')
    ),
    CONSTRAINT ck_listing_collections_state CHECK (
        state IN ('ACTIVE', 'ARCHIVED', 'DELETED')
    ),
    -- A link-shared collection is reachable by anyone holding the link, so the link is a secret
    -- and only its digest is kept; a private or public one has no link to keep.
    CONSTRAINT ck_listing_collections_share CHECK (
        (visibility = 'LINK_SHARED') = (share_token_digest IS NOT NULL)
    ),
    CONSTRAINT ck_listing_collections_archived CHECK (
        (state = 'ARCHIVED') = (archived_at IS NOT NULL)
    ),
    CONSTRAINT ck_listing_collections_deleted CHECK (
        (state = 'DELETED') = (deleted_at IS NOT NULL)
    ),
    CONSTRAINT ck_listing_collections_count CHECK (item_count >= 0),
    CONSTRAINT ck_listing_collections_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_listing_collections_title
    ON listing_collections (account_holder_id, title) WHERE state = 'ACTIVE';
--rollback DROP TABLE listing_collections;

--changeset ninggiangboy:034-25-listing-collection-items
CREATE TABLE listing_collection_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_collection_id       UUID NOT NULL,
    saved_listing_id            UUID NOT NULL,
    position                    INTEGER NOT NULL,
    note                        VARCHAR(280),
    added_at                    TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_listing_collection_items_saved
        UNIQUE (listing_collection_id, saved_listing_id),
    CONSTRAINT fk_listing_collection_items_collection FOREIGN KEY (listing_collection_id)
        REFERENCES listing_collections (id),
    CONSTRAINT fk_listing_collection_items_saved FOREIGN KEY (saved_listing_id)
        REFERENCES saved_listings (id),
    CONSTRAINT ck_listing_collection_items_position CHECK (position >= 0),
    CONSTRAINT ck_listing_collection_items_version CHECK (version >= 0)
);

-- Reordering a collection swaps positions within one transaction, so the uniqueness of a position
-- is only meaningful at commit; an immediate constraint would refuse every reorder that does not
-- happen to renumber in a conflict-free order.
ALTER TABLE listing_collection_items
    ADD CONSTRAINT uk_listing_collection_items_position
    UNIQUE (listing_collection_id, position) DEFERRABLE INITIALLY DEFERRED;
--rollback DROP TABLE listing_collection_items;

--changeset ninggiangboy:034-26-saved-searches
CREATE TABLE saved_searches (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    title                       VARCHAR(120) NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,
    geo_area_id                 UUID,
    search_criteria             JSONB NOT NULL,
    criteria_digest             CHAR(64) NOT NULL,
    stay_range                  DATERANGE,
    adult_count                 SMALLINT,
    child_count                 SMALLINT,
    notify_cadence              VARCHAR(16) NOT NULL DEFAULT 'NEVER',
    communication_consent_id    UUID,
    last_evaluated_at           TIMESTAMPTZ,
    last_result_count           INTEGER,
    last_notified_at            TIMESTAMPTZ,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_saved_searches_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_saved_searches_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT fk_saved_searches_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id),
    CONSTRAINT fk_saved_searches_consent FOREIGN KEY (communication_consent_id)
        REFERENCES communication_consents (id),
    CONSTRAINT ck_saved_searches_cadence CHECK (
        notify_cadence IN ('NEVER', 'INSTANT', 'DAILY', 'WEEKLY')
    ),
    CONSTRAINT ck_saved_searches_state CHECK (
        state IN ('ACTIVE', 'PAUSED', 'EXPIRED', 'DELETED')
    ),
    CONSTRAINT ck_saved_searches_market_code CHECK (market_code ~ '^[A-Z]{2}$'),
    -- A saved search that sends nothing is a bookmark and needs no consent. A saved search that
    -- sends something is a standing subscription, and it names the consent that permits it and the
    -- date it stops asking.
    CONSTRAINT ck_saved_searches_consent CHECK (
        (notify_cadence = 'NEVER')
            OR (communication_consent_id IS NOT NULL AND expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_saved_searches_counts CHECK (
        (last_result_count IS NULL OR last_result_count >= 0)
            AND (adult_count IS NULL OR adult_count > 0)
            AND (child_count IS NULL OR child_count >= 0)
    ),
    CONSTRAINT ck_saved_searches_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_saved_searches_criteria
    ON saved_searches (account_holder_id, criteria_digest) WHERE state <> 'DELETED';
CREATE INDEX idx_saved_searches_due ON saved_searches (notify_cadence, last_evaluated_at)
    WHERE state = 'ACTIVE' AND notify_cadence <> 'NEVER';
--rollback DROP TABLE saved_searches;

--changeset ninggiangboy:034-27-demand-alerts
CREATE TABLE demand_alerts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    alert_kind                  VARCHAR(24) NOT NULL,
    listing_id                  UUID,
    saved_search_id             UUID,
    stay_range                  DATERANGE,
    threshold_percent           NUMERIC(5,4),
    threshold_amount_minor      BIGINT,
    threshold_currency          VARCHAR(3),
    remaining_units_threshold   SMALLINT,
    baseline_amount_minor       BIGINT,
    baseline_captured_at        TIMESTAMPTZ,
    communication_consent_id    UUID NOT NULL,
    notify_channel              VARCHAR(16) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    trigger_count               INTEGER NOT NULL DEFAULT 0,
    last_triggered_at           TIMESTAMPTZ,
    triggered_intent_id         UUID,
    cancellation_reason         VARCHAR(48),
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_demand_alerts_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_demand_alerts_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_demand_alerts_search FOREIGN KEY (saved_search_id)
        REFERENCES saved_searches (id),
    CONSTRAINT fk_demand_alerts_consent FOREIGN KEY (communication_consent_id)
        REFERENCES communication_consents (id),
    CONSTRAINT fk_demand_alerts_intent FOREIGN KEY (triggered_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT ck_demand_alerts_kind CHECK (
        alert_kind IN ('PRICE_DROP', 'AVAILABILITY_OPENED', 'LAST_UNITS', 'SAVED_SEARCH_MATCH')
    ),
    CONSTRAINT ck_demand_alerts_channel CHECK (
        notify_channel IN ('EMAIL', 'SMS', 'PUSH', 'IN_APP')
    ),
    CONSTRAINT ck_demand_alerts_state CHECK (
        state IN ('ACTIVE', 'TRIGGERED', 'EXPIRED', 'CANCELLED')
    ),
    CONSTRAINT ck_demand_alerts_target CHECK (
        num_nonnulls(listing_id, saved_search_id) = 1
            AND (alert_kind = 'SAVED_SEARCH_MATCH') = (saved_search_id IS NOT NULL)
    ),
    -- A price-drop alert compares against a price that was actually observed, and says when. An
    -- alert with no baseline can fire on any price at all and call it a drop.
    CONSTRAINT ck_demand_alerts_baseline CHECK (
        (alert_kind = 'PRICE_DROP')
            = (baseline_amount_minor IS NOT NULL AND baseline_captured_at IS NOT NULL)
    ),
    -- Scarcity is a count or it is a sales technique. A last-units alert names how few is few.
    CONSTRAINT ck_demand_alerts_units CHECK (
        (alert_kind = 'LAST_UNITS')
            = (remaining_units_threshold IS NOT NULL AND remaining_units_threshold > 0)
    ),
    CONSTRAINT ck_demand_alerts_threshold CHECK (
        (threshold_percent IS NULL OR (threshold_percent > 0 AND threshold_percent < 1))
            AND (threshold_amount_minor IS NULL) = (threshold_currency IS NULL)
            AND (threshold_currency IS NULL OR threshold_currency ~ '^[A-Z]{3}$')
            AND (threshold_amount_minor IS NULL OR threshold_amount_minor > 0)
    ),
    CONSTRAINT ck_demand_alerts_triggered CHECK (
        trigger_count >= 0 AND (trigger_count = 0) = (last_triggered_at IS NULL)
    ),
    CONSTRAINT ck_demand_alerts_cancelled CHECK (
        (state = 'CANCELLED') = (cancellation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_demand_alerts_version CHECK (version >= 0)
);

CREATE INDEX idx_demand_alerts_listing ON demand_alerts (listing_id, alert_kind)
    WHERE state = 'ACTIVE' AND listing_id IS NOT NULL;
CREATE INDEX idx_demand_alerts_expiry ON demand_alerts (expires_at) WHERE state = 'ACTIVE';
--rollback DROP TABLE demand_alerts;

--changeset ninggiangboy:034-28-waitlist-entries
CREATE TABLE waitlist_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    listing_id                  UUID,
    accommodation_type_id       UUID,
    geo_area_id                 UUID,
    stay_range                  DATERANGE NOT NULL,
    adult_count                 SMALLINT NOT NULL,
    child_count                 SMALLINT NOT NULL DEFAULT 0,
    unit_quantity               SMALLINT NOT NULL DEFAULT 1,
    maximum_nightly_minor       BIGINT,
    currency                    VARCHAR(3),
    communication_consent_id    UUID NOT NULL,
    queue_position              INTEGER,
    state                       VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    joined_at                   TIMESTAMPTZ NOT NULL,
    offered_at                  TIMESTAMPTZ,
    offer_expires_at            TIMESTAMPTZ,
    offer_quote_id              UUID,
    offer_intent_id             UUID,
    converted_booking_id        UUID,
    withdrawal_reason           VARCHAR(48),
    expires_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_waitlist_entries_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_waitlist_entries_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_waitlist_entries_type FOREIGN KEY (accommodation_type_id)
        REFERENCES accommodation_types (id),
    CONSTRAINT fk_waitlist_entries_area FOREIGN KEY (geo_area_id) REFERENCES geo_areas (id),
    CONSTRAINT fk_waitlist_entries_consent FOREIGN KEY (communication_consent_id)
        REFERENCES communication_consents (id),
    CONSTRAINT fk_waitlist_entries_quote FOREIGN KEY (offer_quote_id) REFERENCES quotes (id),
    CONSTRAINT fk_waitlist_entries_intent FOREIGN KEY (offer_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_waitlist_entries_booking FOREIGN KEY (converted_booking_id)
        REFERENCES bookings (id),
    CONSTRAINT ck_waitlist_entries_state CHECK (
        state IN ('WAITING', 'OFFERED', 'CONVERTED', 'EXPIRED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_waitlist_entries_target CHECK (
        num_nonnulls(listing_id, accommodation_type_id, geo_area_id) = 1
    ),
    CONSTRAINT ck_waitlist_entries_party CHECK (
        adult_count > 0 AND child_count >= 0 AND unit_quantity > 0
            AND (maximum_nightly_minor IS NULL) = (currency IS NULL)
            AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
            AND (maximum_nightly_minor IS NULL OR maximum_nightly_minor > 0)
    ),
    -- Telling somebody a room opened up without holding one is fabricated scarcity with their
    -- calendar attached. An offer names the quote that holds the inventory and the moment the
    -- hold runs out, so the guest is never racing a room that was never theirs.
    CONSTRAINT ck_waitlist_entries_offer CHECK (
        (state = 'OFFERED')
            = (offered_at IS NOT NULL AND offer_expires_at IS NOT NULL
               AND offer_quote_id IS NOT NULL)
    ),
    CONSTRAINT ck_waitlist_entries_offer_window CHECK (
        offer_expires_at IS NULL OR (offered_at IS NOT NULL AND offer_expires_at > offered_at)
    ),
    CONSTRAINT ck_waitlist_entries_converted CHECK (
        (state = 'CONVERTED') = (converted_booking_id IS NOT NULL)
    ),
    CONSTRAINT ck_waitlist_entries_withdrawn CHECK (
        (state = 'WITHDRAWN') = (withdrawal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_waitlist_entries_expiry CHECK (expires_at > joined_at),
    CONSTRAINT ck_waitlist_entries_position CHECK (queue_position IS NULL OR queue_position > 0),
    CONSTRAINT ck_waitlist_entries_version CHECK (version >= 0)
);

-- One live request per guest per target per stay. Joining the same queue twice does not move
-- anybody up it; it just doubles the notifications.
CREATE UNIQUE INDEX uk_waitlist_entries_listing
    ON waitlist_entries (account_holder_id, listing_id, stay_range)
    WHERE state IN ('WAITING', 'OFFERED') AND listing_id IS NOT NULL;
CREATE INDEX idx_waitlist_entries_queue
    ON waitlist_entries (listing_id, stay_range, queue_position)
    WHERE state = 'WAITING' AND listing_id IS NOT NULL;
--rollback DROP TABLE waitlist_entries;

--changeset ninggiangboy:034-29-growth-append-only splitStatements:false
-- Each of these rows records something that was decided, moved, counted or shown at one moment.
-- An eligibility decision edited later answers a question nobody asked; a stored-value entry
-- edited later breaks the only chain that ties a guest's balance to the ledger; a qualifying event
-- edited later makes the tier a guest held last quarter unexplainable; an uplift result edited
-- later turns a measurement into a claim. Corrections are new rows, and a reversal is a new row
-- that names the one it reverses. These reuse the function migration 030 installed.
CREATE TRIGGER trg_growth_eligibility_evaluations_ao
    BEFORE UPDATE ON growth_eligibility_evaluations
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_stored_value_entries_append_only
    BEFORE UPDATE ON stored_value_entries
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_loyalty_qualifying_events_ao
    BEFORE UPDATE ON loyalty_qualifying_events
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_loyalty_tier_transitions_ao
    BEFORE UPDATE ON loyalty_tier_transitions
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_campaign_touchpoints_append_only
    BEFORE UPDATE ON campaign_touchpoints
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_campaign_uplift_results_ao
    BEFORE UPDATE ON campaign_uplift_results
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();
--rollback DROP TRIGGER trg_campaign_uplift_results_ao ON campaign_uplift_results;
--rollback DROP TRIGGER trg_campaign_touchpoints_append_only ON campaign_touchpoints;
--rollback DROP TRIGGER trg_loyalty_tier_transitions_ao ON loyalty_tier_transitions;
--rollback DROP TRIGGER trg_loyalty_qualifying_events_ao ON loyalty_qualifying_events;
--rollback DROP TRIGGER trg_stored_value_entries_append_only ON stored_value_entries;
--rollback DROP TRIGGER trg_growth_eligibility_evaluations_ao ON growth_eligibility_evaluations;

--changeset ninggiangboy:034-30-growth-contract-freeze splitStatements:false
-- A programme whose eligibility rules, reward amount or expiry period can be edited after people
-- have qualified under them cannot answer what anybody qualified for, and an affiliate agreement
-- whose commission rate can be edited after bookings were attributed cannot answer what the
-- partner is owed. Once one of these leaves DRAFT it is frozen except for the columns that carry
-- it through its own lifecycle: different terms are a new version, not an edit to this one.
CREATE TRIGGER trg_growth_programs_freeze
    BEFORE UPDATE OR DELETE ON growth_programs
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'opened_at', 'closed_at', 'owner_actor_id', 'updated_at', 'version');

CREATE TRIGGER trg_growth_program_versions_freeze
    BEFORE UPDATE OR DELETE ON growth_program_versions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'published_at', 'retired_at', 'effective_until', 'updated_at', 'version');

CREATE TRIGGER trg_affiliate_partners_freeze
    BEFORE UPDATE OR DELETE ON affiliate_partners
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'suspension_reason', 'activated_at', 'terminated_at', 'payout_destination_ref',
        'tax_form_reference', 'updated_at', 'version');
--rollback DROP TRIGGER trg_affiliate_partners_freeze ON affiliate_partners;
--rollback DROP TRIGGER trg_growth_program_versions_freeze ON growth_program_versions;
--rollback DROP TRIGGER trg_growth_programs_freeze ON growth_programs;

--changeset ninggiangboy:034-31-loyalty-tier-seal splitStatements:false
-- Freezing the version row does not freeze its tiers, and the tiers are what a guest qualified
-- against. Without this, a published loyalty programme can gain a tier, or lose one, and every
-- membership measured against it silently changes meaning while the version number stays put.
-- This is the same defect migrations 022, 027, 030 and 033 each found in a different shape: a
-- frozen parent with an unfrozen child table.
CREATE FUNCTION loyalty_tier_definition_seal() RETURNS TRIGGER AS $$
DECLARE
    version_status VARCHAR(16);
    subject_id UUID;
BEGIN
    subject_id := COALESCE(NEW.growth_program_version_id, OLD.growth_program_version_id);
    SELECT status INTO version_status FROM growth_program_versions WHERE id = subject_id;
    IF NOT FOUND THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    IF version_status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'growth program version % is %; its tiers are what memberships were qualified '
            'against, so a different set of tiers is a new version rather than an edit',
            subject_id, version_status
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loyalty_tier_definitions_seal
    BEFORE INSERT OR UPDATE OR DELETE ON loyalty_tier_definitions
    FOR EACH ROW
    EXECUTE FUNCTION loyalty_tier_definition_seal();
--rollback DROP TRIGGER trg_loyalty_tier_definitions_seal ON loyalty_tier_definitions;
--rollback DROP FUNCTION loyalty_tier_definition_seal();

--changeset ninggiangboy:034-32-stored-value-integrity splitStatements:false
-- The entry and the balance it produced are written together or the chain is worthless: an entry
-- that claims a balance the account does not hold cannot be reconciled against the ledger, and the
-- first time anyone tries is when a guest says their credit vanished. Value also moves out of a
-- named lot in a matching currency, because a balance that cannot say which promise it came from
-- cannot say which promise expired.
CREATE FUNCTION stored_value_entry_integrity() RETURNS TRIGGER AS $$
DECLARE
    account_row stored_value_accounts%ROWTYPE;
    lot_row stored_value_lots%ROWTYPE;
BEGIN
    SELECT * INTO account_row FROM stored_value_accounts WHERE id = NEW.stored_value_account_id;
    IF NEW.currency <> account_row.currency THEN
        RAISE EXCEPTION
            'entry is denominated in % while its account holds %; stored value is not converted '
            'on the way through an entry',
            NEW.currency, account_row.currency
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.balance_after_minor <> account_row.balance_minor
            OR NEW.reserved_after_minor <> account_row.reserved_minor THEN
        RAISE EXCEPTION
            'entry records a balance of %/% held while the account stands at %/%; the movement '
            'and the balance it produced are written in one transaction or neither is true',
            NEW.balance_after_minor, NEW.reserved_after_minor,
            account_row.balance_minor, account_row.reserved_minor
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.entry_kind IN ('GRANT', 'REDEEM', 'RESERVE', 'TRANSFER_IN')
            AND account_row.lifecycle_state <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'account is %; a frozen or closed balance may be released or written off but not '
            'spent or topped up',
            account_row.lifecycle_state
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.stored_value_lot_id IS NOT NULL THEN
        SELECT * INTO lot_row FROM stored_value_lots WHERE id = NEW.stored_value_lot_id;
        IF lot_row.stored_value_account_id <> NEW.stored_value_account_id THEN
            RAISE EXCEPTION
                'lot % belongs to a different stored-value account than the entry drawing on it',
                NEW.stored_value_lot_id
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF lot_row.currency <> NEW.currency THEN
            RAISE EXCEPTION
                'lot % is denominated in % and the entry in %',
                NEW.stored_value_lot_id, lot_row.currency, NEW.currency
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.entry_kind = 'REDEEM' AND lot_row.state <> 'ACTIVE' THEN
            RAISE EXCEPTION
                'lot % is %; expired and revoked value is not quietly spendable',
                NEW.stored_value_lot_id, lot_row.state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stored_value_entries_integrity
    BEFORE INSERT ON stored_value_entries
    FOR EACH ROW
    EXECUTE FUNCTION stored_value_entry_integrity();

-- A gift card is money somebody already paid for. Redeeming it moves the whole face value into one
-- lot in the same currency, held by the person redeeming it, and the card cannot be redeemed after
-- it expired or recognised as breakage before it did.
CREATE FUNCTION gift_card_redemption_integrity() RETURNS TRIGGER AS $$
DECLARE
    lot_row stored_value_lots%ROWTYPE;
    lot_holder UUID;
BEGIN
    IF NEW.state = 'REDEEMED' AND OLD.state <> 'REDEEMED' THEN
        IF NEW.expires_at IS NOT NULL AND NEW.redeemed_at >= NEW.expires_at THEN
            RAISE EXCEPTION
                'gift card % expired at % and cannot be redeemed at %',
                NEW.serial_reference, NEW.expires_at, NEW.redeemed_at
                USING ERRCODE = 'restrict_violation';
        END IF;
        SELECT * INTO lot_row FROM stored_value_lots WHERE id = NEW.redeemed_into_lot_id;
        IF lot_row.original_minor <> NEW.face_value_minor
                OR lot_row.currency <> NEW.currency THEN
            RAISE EXCEPTION
                'gift card % is worth % % and was redeemed into a lot of % %',
                NEW.serial_reference, NEW.face_value_minor, NEW.currency,
                lot_row.original_minor, lot_row.currency
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF lot_row.source_kind <> 'GIFT_CARD' THEN
            RAISE EXCEPTION
                'gift card % was redeemed into a lot recorded as %',
                NEW.serial_reference, lot_row.source_kind
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.recipient_holder_id IS NOT NULL THEN
            SELECT account_holder_id INTO lot_holder FROM stored_value_accounts
                WHERE id = lot_row.stored_value_account_id;
            IF lot_holder <> NEW.recipient_holder_id THEN
                RAISE EXCEPTION
                    'gift card % names a recipient and was redeemed into somebody else''s balance',
                    NEW.serial_reference
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END IF;
    END IF;
    IF NEW.breakage_recognized_at IS NOT NULL
            AND OLD.breakage_recognized_at IS NULL
            AND (NEW.expires_at IS NULL OR NEW.breakage_recognized_at < NEW.expires_at) THEN
        RAISE EXCEPTION
            'breakage on gift card % was recognised before it expired; value a guest can still '
            'spend is a liability and not revenue',
            NEW.serial_reference
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_gift_cards_integrity
    BEFORE UPDATE ON gift_cards
    FOR EACH ROW
    EXECUTE FUNCTION gift_card_redemption_integrity();
--rollback DROP TRIGGER trg_gift_cards_integrity ON gift_cards;
--rollback DROP FUNCTION gift_card_redemption_integrity();
--rollback DROP TRIGGER trg_stored_value_entries_integrity ON stored_value_entries;
--rollback DROP FUNCTION stored_value_entry_integrity();

--changeset ninggiangboy:034-33-referral-integrity splitStatements:false
-- The column check refuses the obvious self-referral. These refuse the rest: qualifying on a
-- stranger's booking, qualifying against a code that was suspended for fraud, paying out before
-- the reward matured, and closing an attribution while the money it produced is still outstanding.
-- That last one is the whole point of recording a reversal: a cancelled booking that leaves a
-- granted reward behind has simply paid somebody for a stay that never happened.
CREATE FUNCTION referral_attribution_integrity() RETURNS TRIGGER AS $$
DECLARE
    code_row referral_codes%ROWTYPE;
    booking_row bookings%ROWTYPE;
    outstanding INTEGER;
BEGIN
    SELECT * INTO code_row FROM referral_codes WHERE id = NEW.referral_code_id;
    IF TG_OP = 'INSERT' THEN
        IF NEW.state <> 'PENDING' THEN
            RAISE EXCEPTION
                'a referral attribution is recorded as PENDING and qualifies later; inserting it '
                'as % skips the qualification it is supposed to prove',
                NEW.state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF code_row.state <> 'ACTIVE' THEN
            RAISE EXCEPTION
                'referral code % is %; a suspended or revoked code does not keep earning',
                code_row.code, code_row.state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF code_row.owner_account_holder_id <> NEW.referrer_account_holder_id THEN
            RAISE EXCEPTION
                'the attribution credits a referrer who does not own referral code %',
                code_row.code
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF NEW.state = 'QUALIFIED' AND OLD.state IS DISTINCT FROM 'QUALIFIED' THEN
        SELECT * INTO booking_row FROM bookings WHERE id = NEW.qualifying_booking_id;
        IF booking_row.guest_account_holder_id <> NEW.referee_account_holder_id THEN
            RAISE EXCEPTION
                'booking % was made by somebody other than the referee it is supposed to qualify',
                NEW.qualifying_booking_id
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF booking_row.lifecycle_state NOT IN ('CONFIRMED', 'CHECKED_IN', 'COMPLETED') THEN
            RAISE EXCEPTION
                'booking % is %; a referral qualifies against a booking that actually stands',
                NEW.qualifying_booking_id, booking_row.lifecycle_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.state = 'REVERSED' AND OLD.state <> 'REVERSED' THEN
        SELECT count(*) INTO outstanding FROM referral_reward_grants
            WHERE referral_attribution_id = NEW.id AND state = 'GRANTED';
        IF outstanding > 0 THEN
            RAISE EXCEPTION
                '% reward grant(s) under this attribution are still granted; reverse the value '
                'before closing the attribution that justified it',
                outstanding
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_referral_attributions_integrity
    BEFORE INSERT OR UPDATE ON referral_attributions
    FOR EACH ROW
    EXECUTE FUNCTION referral_attribution_integrity();

CREATE FUNCTION referral_reward_grant_integrity() RETURNS TRIGGER AS $$
DECLARE
    attribution_row referral_attributions%ROWTYPE;
BEGIN
    SELECT * INTO attribution_row FROM referral_attributions
        WHERE id = NEW.referral_attribution_id;
    IF TG_OP = 'INSERT' AND attribution_row.state IN ('REJECTED', 'REVERSED') THEN
        RAISE EXCEPTION
            'the attribution behind this reward is %; a closed referral does not start paying',
            attribution_row.state
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.state = 'GRANTED' AND OLD.state IS DISTINCT FROM 'GRANTED' THEN
        IF attribution_row.state <> 'QUALIFIED' THEN
            RAISE EXCEPTION
                'the attribution behind this reward is % rather than QUALIFIED; the reward is '
                'what qualification pays for',
                attribution_row.state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.granted_at < NEW.matures_at THEN
            RAISE EXCEPTION
                'reward granted at % matures at %; the maturity delay is what makes a cancelled '
                'booking reversible before the money leaves',
                NEW.granted_at, NEW.matures_at
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_referral_reward_grants_integrity
    BEFORE INSERT OR UPDATE ON referral_reward_grants
    FOR EACH ROW
    EXECUTE FUNCTION referral_reward_grant_integrity();
--rollback DROP TRIGGER trg_referral_reward_grants_integrity ON referral_reward_grants;
--rollback DROP FUNCTION referral_reward_grant_integrity();
--rollback DROP TRIGGER trg_referral_attributions_integrity ON referral_attributions;
--rollback DROP FUNCTION referral_attribution_integrity();

--changeset ninggiangboy:034-34-campaign-contact-integrity splitStatements:false
-- Being in an audience is not permission to be written to. Every touchpoint proves, at the moment
-- it is created, that a consent covering this category and this channel was in force for this
-- person, that the campaign was actually running, and that the frequency cap the campaign
-- published for itself has not already been used up. The holdout can never be contacted at all --
-- a holdout that receives one send stops being evidence of anything.
CREATE FUNCTION campaign_touchpoint_integrity() RETURNS TRIGGER AS $$
DECLARE
    membership_row campaign_audience_memberships%ROWTYPE;
    campaign_row growth_campaigns%ROWTYPE;
    consent_row communication_consents%ROWTYPE;
    recent_count INTEGER;
BEGIN
    SELECT * INTO membership_row FROM campaign_audience_memberships
        WHERE id = NEW.audience_membership_id;
    SELECT * INTO campaign_row FROM growth_campaigns WHERE id = membership_row.growth_campaign_id;
    SELECT * INTO consent_row FROM communication_consents WHERE id = NEW.communication_consent_id;
    IF membership_row.arm = 'HOLDOUT' THEN
        RAISE EXCEPTION
            'this audience membership is in the holdout; contacting it destroys the only '
            'comparison that could show the campaign did anything'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF membership_row.state IN ('SUPPRESSED', 'EXCLUDED') THEN
        RAISE EXCEPTION
            'audience membership is %; a suppressed person stays suppressed for the whole send',
            membership_row.state
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF campaign_row.status <> 'RUNNING' THEN
        RAISE EXCEPTION
            'campaign % is %; sends happen while a campaign runs and not before or after it',
            campaign_row.campaign_key, campaign_row.status
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.requested_at < campaign_row.send_window_from
            OR NEW.requested_at >= campaign_row.send_window_until THEN
        RAISE EXCEPTION
            'send requested at % lies outside the campaign window [%, %)',
            NEW.requested_at, campaign_row.send_window_from, campaign_row.send_window_until
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF consent_row.account_holder_id <> membership_row.account_holder_id THEN
        RAISE EXCEPTION
            'the consent cited belongs to a different person than the one being contacted'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF consent_row.category_code <> campaign_row.required_consent_category
            OR consent_row.channel <> campaign_row.required_channel THEN
        RAISE EXCEPTION
            'consent covers %/% and the campaign requires %/%; consent to one kind of message is '
            'not consent to another',
            consent_row.category_code, consent_row.channel,
            campaign_row.required_consent_category, campaign_row.required_channel
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.channel <> campaign_row.required_channel THEN
        RAISE EXCEPTION
            'touchpoint uses % while the campaign and its consent are for %',
            NEW.channel, campaign_row.required_channel
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF consent_row.granted_at > NEW.requested_at
            OR (consent_row.withdrawn_at IS NOT NULL
                AND consent_row.withdrawn_at <= NEW.requested_at) THEN
        RAISE EXCEPTION
            'consent was not in force at %; it was granted at % and withdrawn at %',
            NEW.requested_at, consent_row.granted_at, consent_row.withdrawn_at
            USING ERRCODE = 'restrict_violation';
    END IF;
    SELECT count(*) INTO recent_count FROM campaign_touchpoints
        WHERE audience_membership_id = NEW.audience_membership_id
          AND requested_at
              > NEW.requested_at - make_interval(days => campaign_row.frequency_window_days);
    IF recent_count >= campaign_row.frequency_cap_per_window THEN
        RAISE EXCEPTION
            'this person has already received % message(s) in the last % day(s) and the campaign '
            'capped itself at %',
            recent_count, campaign_row.frequency_window_days,
            campaign_row.frequency_cap_per_window
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_campaign_touchpoints_integrity
    BEFORE INSERT ON campaign_touchpoints
    FOR EACH ROW
    EXECUTE FUNCTION campaign_touchpoint_integrity();
--rollback DROP TRIGGER trg_campaign_touchpoints_integrity ON campaign_touchpoints;
--rollback DROP FUNCTION campaign_touchpoint_integrity();

--changeset ninggiangboy:034-35-loyalty-transition-integrity splitStatements:false
-- A tier change is written after the membership has moved, and it must describe the move that
-- actually happened: the tier it names as current is the tier the membership now holds, a
-- qualification goes up, a downgrade goes down, and a tier reached outside the published
-- thresholds is a named decision rather than a reason code the pipeline chose for itself.
CREATE FUNCTION loyalty_tier_transition_integrity() RETURNS TRIGGER AS $$
DECLARE
    membership_row loyalty_memberships%ROWTYPE;
    from_rank SMALLINT;
    to_rank SMALLINT;
    to_version UUID;
BEGIN
    SELECT * INTO membership_row FROM loyalty_memberships WHERE id = NEW.loyalty_membership_id;
    IF membership_row.current_tier_definition_id <> NEW.to_tier_definition_id THEN
        RAISE EXCEPTION
            'the transition records a move to a tier the membership does not hold; the membership '
            'is moved first and the transition records what happened'
            USING ERRCODE = 'restrict_violation';
    END IF;
    SELECT tier_rank, growth_program_version_id INTO to_rank, to_version
        FROM loyalty_tier_definitions WHERE id = NEW.to_tier_definition_id;
    IF to_version <> membership_row.current_program_version_id THEN
        RAISE EXCEPTION
            'the tier reached belongs to a different programme version than the membership names'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.from_tier_definition_id IS NOT NULL THEN
        SELECT tier_rank INTO from_rank FROM loyalty_tier_definitions
            WHERE id = NEW.from_tier_definition_id;
        IF NEW.transition_reason = 'QUALIFIED' AND to_rank <= from_rank THEN
            RAISE EXCEPTION
                'a qualification that does not raise the tier is a downgrade or a renewal, and '
                'is recorded as one; rank went from % to %',
                from_rank, to_rank
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.transition_reason IN ('DOWNGRADED', 'PROGRAM_CLOSED') AND to_rank >= from_rank THEN
            RAISE EXCEPTION
                'a downgrade that does not lower the tier is not one; rank went from % to %',
                from_rank, to_rank
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF NEW.effective_from < membership_row.joined_at THEN
        RAISE EXCEPTION
            'the transition takes effect at %, before the membership existed at %',
            NEW.effective_from, membership_row.joined_at
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loyalty_tier_transitions_integrity
    BEFORE INSERT ON loyalty_tier_transitions
    FOR EACH ROW
    EXECUTE FUNCTION loyalty_tier_transition_integrity();

-- A qualifying event counts toward a window the membership is actually in, and a reversal names an
-- event under the same membership. Counting a stay into a window that closed is how a tier appears
-- for a guest who never qualified for it.
CREATE FUNCTION loyalty_qualifying_event_integrity() RETURNS TRIGGER AS $$
DECLARE
    membership_row loyalty_memberships%ROWTYPE;
    reversed_row loyalty_qualifying_events%ROWTYPE;
    booking_row bookings%ROWTYPE;
BEGIN
    SELECT * INTO membership_row FROM loyalty_memberships WHERE id = NEW.loyalty_membership_id;
    IF membership_row.membership_state NOT IN ('ACTIVE', 'LAPSED') THEN
        RAISE EXCEPTION
            'membership is %; a suspended or closed membership does not keep accruing',
            membership_row.membership_state
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.currency <> membership_row.spend_currency THEN
        RAISE EXCEPTION
            'event is denominated in % while the membership accrues in %',
            NEW.currency, membership_row.spend_currency
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF NEW.reverses_event_id IS NOT NULL THEN
        SELECT * INTO reversed_row FROM loyalty_qualifying_events WHERE id = NEW.reverses_event_id;
        IF reversed_row.loyalty_membership_id <> NEW.loyalty_membership_id THEN
            RAISE EXCEPTION
                'the event being reversed belongs to a different membership'
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.nights_counted < -reversed_row.nights_counted
                OR NEW.bookings_counted < -reversed_row.bookings_counted
                OR NEW.spend_minor < -reversed_row.spend_minor THEN
            RAISE EXCEPTION
                'a reversal may take back at most what the event it names contributed'
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSIF NEW.booking_id IS NOT NULL THEN
        SELECT * INTO booking_row FROM bookings WHERE id = NEW.booking_id;
        IF booking_row.guest_account_holder_id <> membership_row.account_holder_id THEN
            RAISE EXCEPTION
                'booking % was made by somebody other than the member it would accrue to',
                NEW.booking_id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_loyalty_qualifying_events_integrity
    BEFORE INSERT ON loyalty_qualifying_events
    FOR EACH ROW
    EXECUTE FUNCTION loyalty_qualifying_event_integrity();
--rollback DROP TRIGGER trg_loyalty_qualifying_events_integrity ON loyalty_qualifying_events;
--rollback DROP FUNCTION loyalty_qualifying_event_integrity();
--rollback DROP TRIGGER trg_loyalty_tier_transitions_integrity ON loyalty_tier_transitions;
--rollback DROP FUNCTION loyalty_tier_transition_integrity();

--changeset ninggiangboy:034-36-affiliate-integrity splitStatements:false
-- The attribution window belongs to the agreement, not to the click: a partner that could set its
-- own expiry per click would credit itself with every booking that ever followed. The commission
-- follows the agreement's rate, matures before it is paid, and once paid is recovered through
-- migration 022's recovery path rather than reversed here -- money that has already left is not
-- un-sent by editing a row.
CREATE FUNCTION affiliate_attribution_integrity() RETURNS TRIGGER AS $$
DECLARE
    partner_row affiliate_partners%ROWTYPE;
BEGIN
    SELECT * INTO partner_row FROM affiliate_partners WHERE id = NEW.affiliate_partner_id;
    IF TG_OP = 'INSERT' THEN
        IF partner_row.status <> 'ACTIVE' THEN
            RAISE EXCEPTION
                'affiliate partner % is %; clicks are only attributable while the agreement runs',
                partner_row.partner_key, partner_row.status
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.expires_at
                <> NEW.clicked_at + make_interval(days => partner_row.attribution_window_days) THEN
            RAISE EXCEPTION
                'the attribution window is the agreement''s % day(s); this click claims one ending '
                'at % instead',
                partner_row.attribution_window_days, NEW.expires_at
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF NEW.state = 'ATTRIBUTED' AND OLD.state IS DISTINCT FROM 'ATTRIBUTED' THEN
        IF NEW.attributed_at < NEW.clicked_at OR NEW.attributed_at >= NEW.expires_at THEN
            RAISE EXCEPTION
                'the booking was attributed at %, outside the window [%, %) the click opened',
                NEW.attributed_at, NEW.clicked_at, NEW.expires_at
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_affiliate_attributions_integrity
    BEFORE INSERT OR UPDATE ON affiliate_attributions
    FOR EACH ROW
    EXECUTE FUNCTION affiliate_attribution_integrity();

CREATE FUNCTION affiliate_commission_integrity() RETURNS TRIGGER AS $$
DECLARE
    partner_row affiliate_partners%ROWTYPE;
    attribution_row affiliate_attributions%ROWTYPE;
BEGIN
    SELECT * INTO partner_row FROM affiliate_partners WHERE id = NEW.affiliate_partner_id;
    SELECT * INTO attribution_row FROM affiliate_attributions
        WHERE id = NEW.affiliate_attribution_id;
    IF TG_OP = 'INSERT' THEN
        IF attribution_row.state <> 'ATTRIBUTED' THEN
            RAISE EXCEPTION
                'the attribution behind this commission is %; nothing is owed for a click that '
                'was never credited with a booking',
                attribution_row.state
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF attribution_row.booking_id <> NEW.booking_id
                OR attribution_row.affiliate_partner_id <> NEW.affiliate_partner_id THEN
            RAISE EXCEPTION
                'the commission names a different booking or partner than the attribution it cites'
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.commission_percent <> partner_row.commission_percent
                OR NEW.currency <> partner_row.commission_currency THEN
            RAISE EXCEPTION
                'the agreement pays % in % and this commission claims % in %',
                partner_row.commission_percent, partner_row.commission_currency,
                NEW.commission_percent, NEW.currency
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.matures_at
                < attribution_row.attributed_at
                  + make_interval(days => partner_row.payout_hold_days) THEN
            RAISE EXCEPTION
                'the agreement holds commission for % day(s) after attribution; this one matures '
                'at % instead',
                partner_row.payout_hold_days, NEW.matures_at
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF NEW.state = 'PAID' AND OLD.state <> 'PAID' AND NEW.paid_at < NEW.matures_at THEN
            RAISE EXCEPTION
                'commission paid at % matures at %; the hold is what makes a cancellation '
                'reversible before the money leaves',
                NEW.paid_at, NEW.matures_at
                USING ERRCODE = 'restrict_violation';
        END IF;
        IF NEW.state = 'REVERSED' AND OLD.state = 'PAID' THEN
            RAISE EXCEPTION
                'this commission was already paid; recover it through the recovery path in '
                'migration 022 rather than reversing a payment that has left'
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_affiliate_commissions_integrity
    BEFORE INSERT OR UPDATE ON affiliate_commissions
    FOR EACH ROW
    EXECUTE FUNCTION affiliate_commission_integrity();
--rollback DROP TRIGGER trg_affiliate_commissions_integrity ON affiliate_commissions;
--rollback DROP FUNCTION affiliate_commission_integrity();
--rollback DROP TRIGGER trg_affiliate_attributions_integrity ON affiliate_attributions;
--rollback DROP FUNCTION affiliate_attribution_integrity();

--changeset ninggiangboy:034-37-standing-consent-integrity splitStatements:false
-- A saved search, a price alert and a waitlist entry are each a standing arrangement to contact
-- somebody later. The consent they cite has to be that person's own and in force when the
-- arrangement is made, or the first message sent under it is one nobody agreed to. A collection
-- item has the mirror-image problem: a wish list that can hold another person's saved listing is
-- a way to read what somebody else saved.
CREATE FUNCTION growth_consent_ownership() RETURNS TRIGGER AS $$
DECLARE
    consent_row communication_consents%ROWTYPE;
BEGIN
    IF NEW.communication_consent_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
            AND NEW.communication_consent_id IS NOT DISTINCT FROM OLD.communication_consent_id THEN
        RETURN NEW;
    END IF;
    SELECT * INTO consent_row FROM communication_consents WHERE id = NEW.communication_consent_id;
    IF consent_row.account_holder_id <> NEW.account_holder_id THEN
        RAISE EXCEPTION
            '% cites a consent belonging to somebody else',
            TG_TABLE_NAME
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF consent_row.withdrawn_at IS NOT NULL THEN
        RAISE EXCEPTION
            '% cites a consent that was withdrawn at %; a withdrawn permission does not come back '
            'because a new subscription was created under it',
            TG_TABLE_NAME, consent_row.withdrawn_at
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_saved_searches_consent
    BEFORE INSERT OR UPDATE ON saved_searches
    FOR EACH ROW
    EXECUTE FUNCTION growth_consent_ownership();

CREATE TRIGGER trg_demand_alerts_consent
    BEFORE INSERT OR UPDATE ON demand_alerts
    FOR EACH ROW
    EXECUTE FUNCTION growth_consent_ownership();

CREATE TRIGGER trg_waitlist_entries_consent
    BEFORE INSERT OR UPDATE ON waitlist_entries
    FOR EACH ROW
    EXECUTE FUNCTION growth_consent_ownership();

CREATE FUNCTION growth_subject_ownership() RETURNS TRIGGER AS $$
DECLARE
    owner_id UUID;
    other_id UUID;
BEGIN
    IF TG_TABLE_NAME = 'listing_collection_items' THEN
        SELECT account_holder_id INTO owner_id FROM listing_collections
            WHERE id = NEW.listing_collection_id;
        SELECT account_holder_id INTO other_id FROM saved_listings WHERE id = NEW.saved_listing_id;
        IF owner_id <> other_id THEN
            RAISE EXCEPTION
                'the saved listing added to this collection was saved by somebody else'
                USING ERRCODE = 'restrict_violation';
        END IF;
    ELSE
        IF NEW.saved_search_id IS NULL THEN
            RETURN NEW;
        END IF;
        SELECT account_holder_id INTO other_id FROM saved_searches WHERE id = NEW.saved_search_id;
        IF other_id <> NEW.account_holder_id THEN
            RAISE EXCEPTION
                'the alert watches a saved search belonging to somebody else'
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_listing_collection_items_owner
    BEFORE INSERT OR UPDATE ON listing_collection_items
    FOR EACH ROW
    EXECUTE FUNCTION growth_subject_ownership();

CREATE TRIGGER trg_demand_alerts_owner
    BEFORE INSERT OR UPDATE ON demand_alerts
    FOR EACH ROW
    EXECUTE FUNCTION growth_subject_ownership();
--rollback DROP TRIGGER trg_demand_alerts_owner ON demand_alerts;
--rollback DROP TRIGGER trg_listing_collection_items_owner ON listing_collection_items;
--rollback DROP FUNCTION growth_subject_ownership();
--rollback DROP TRIGGER trg_waitlist_entries_consent ON waitlist_entries;
--rollback DROP TRIGGER trg_demand_alerts_consent ON demand_alerts;
--rollback DROP TRIGGER trg_saved_searches_consent ON saved_searches;
--rollback DROP FUNCTION growth_consent_ownership();
