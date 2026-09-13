--liquibase formatted sql

-- An allegation, a finding, a decision and a movement of money are four different rows. This
-- migration exists so that a support case can coordinate all of them without becoming the authority
-- for any of them: what a person said, what an authorized reviewer concluded from evidence, what
-- policy version permitted which remedy, and which domain command actually moved the money are kept
-- separable, because collapsing them is how a support system starts paying claims it cannot explain
-- and refusing claims it cannot defend.
--
-- Eight things force the shape:
--
--   A case coordinates authoritative facts; it never owns them. Every neighbouring fact this domain
--   reads is recorded with its source domain, source identifier, source version and observed
--   instant, so a stale copy can be recognised as stale rather than mistaken for authority. No row
--   here writes inventory, money, publication or capability. A remedy instruction is a request with
--   a stable identity, and the receiving domain's answer arrives as a separate append-only outcome.
--
--   Allegation, assertion, finding and decision form a one-way ladder. An intake preserves what was
--   reported in the reporter's own framing; an assertion attributes a statement to the party who
--   made it; a finding is an authorized interpretation that must cite evidence and name its proof
--   standard; a decision selects a policy version and consumes findings. An INCONCLUSIVE finding may
--   not be cited as the ground of an adverse decision, because "we could not tell" and "we found
--   against you" are opposite answers and only one of them is appealable on its merits.
--
--   Every monetary effect names a funder and a ceiling that was checked. A remedy line carries
--   beneficiary, funder class, amount in integer minor units, currency, decision basis and either a
--   reservation held against a source ceiling or a budget window it consumed. A host may fund only
--   what a host can economically control, so host-funded lines are refused on tax and platform-fee
--   sources. Line sums must agree with the remedy total in the remedy's own currency.
--
--   Approval binds an exact digest, and the maker is not the checker. An approval names the decision
--   digest it saw, not merely the decision; when the decision is re-derived the digest changes and
--   the old approval no longer matches. Self-approval is refused by constraint, and one effective
--   approval exists per decision and approval role.
--
--   Closure never hides unresolved work. A case cannot reach a closed state while a remedy
--   instruction is still in flight or its outcome is unknown, while an external claim submission has
--   an open deadline, or while evidence under legal hold has an unresolved obligation -- unless an
--   exception owner and a next deadline are named. A status field cannot erase pending money.
--
--   An unknown external outcome is a state, not a failure. Submission intent and a stable provider
--   key are persisted before the external call, the key is unique per provider account, observations
--   are append-only, and an observation that would move a provider claim backwards is refused as a
--   stale regression. A retry reuses the key; it never invents a second identity to make a screen
--   move.
--
--   Evidence is immutable and custody is additive. Originals, transformations, redactions, reads and
--   disclosure manifests are append-only. A redaction produces a derivative that names its original;
--   it never destroys the protected bytes. An item under legal hold cannot enter a deletion state,
--   and the hold's scope does not widen anyone's read access.
--
--   Every temporary control is bounded. SLA clocks, offers, evidence deadlines, provider deadlines,
--   work-item leases, payout-hold requests and break-glass authority each carry an expiry or a next
--   review instant. An open case is not, by itself, permission to hold a host's funds forever.
--
-- Note on what this migration does not create. The document proposes support_audit_events. Migration
-- 012 delivered append-only audit_events together with outbox_events, consumer_inbox_receipts and
-- command_idempotency_records, and migration 027 declined the same duplication for the same reason:
-- a second audit stream is a second thing to operate and a second place for the two to disagree.
-- evidence_access_log is not that duplicate -- it records purpose-bound reads and exports of
-- protected case evidence at artifact granularity, which the general audit stream does not carry.
--
-- Note on neighbouring remedies. Migration 025 owns remedy_requests, which stay operations uses to
-- ask another domain for an operational action during a live stay. remedy_instructions here is the
-- monetary and contractual equivalent owned by a support decision. They are deliberately separate:
-- one is an operational request with no funder, the other must name a funder, a ceiling and an
-- approval before it may exist at all.
--
-- Note on provider disputes. Migration 021 owns payment_disputes and its provider observations.
-- This domain stores a typed link, the strategy decided for that dispute, the frozen evidence
-- manifest and the authorized submission command. It does not copy provider observations, because
-- two normalizations of one provider's answer is one normalization too many.
--
-- Note on supersession. Two lineages here -- findings and decisions -- pair a "one live row" partial
-- index with a self-referencing successor pointer. Their foreign keys are DEFERRABLE INITIALLY
-- DEFERRED so the outgoing row may name a replacement the same transaction is about to create, which
-- migration 027 had to do for the same reason. The deferred key solves the reference but not the
-- index: the partial unique index is checked immediately, so the replacement must be written after
-- the outgoing row has been moved out of the live state, never before. Probing found this by trying
-- it the other way round and discovering there was no order in which both rows could exist.
--
-- Note on secrets. No identity document, access secret, payment credential, message body or call
-- recording is stored here. Evidence rows keep a storage object reference, a content digest and the
-- metadata needed to reason about custody; the bytes stay in protected storage.

--changeset ninggiangboy:028-01-support-policy-versions
-- The effective-dated package a decision is taken under: which market and legal entity, which case
-- types it governs, how entitlement is ordered, which remedy catalogue and authority matrix apply,
-- and what appeal behaviour follows. A decision stores the version it selected, so reopening a case
-- under new rules produces a superseding decision rather than silently recomputing history.
CREATE TABLE support_policy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key                  VARCHAR(48) NOT NULL,
    policy_version              INTEGER NOT NULL,
    market_id                   UUID,
    legal_entity_id             UUID,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    -- Which cases this package governs, and how the occurrence instant is chosen when a booking
    -- spans a policy change. "Which rules applied" must be answerable from the booking alone.
    governed_case_types         TEXT[] NOT NULL DEFAULT '{}',
    occurrence_time_basis       VARCHAR(24) NOT NULL,

    -- The precedence ladder the document fixes: safety and legal constraints first, accepted
    -- contract next, mandatory market obligations, protection terms, marketplace terms, goodwill,
    -- and authorized exceptional review last. Stored so a decision can prove which rung it stopped on.
    precedence_ladder           TEXT[] NOT NULL DEFAULT '{}',
    decision_schema_version     INTEGER NOT NULL,
    rule_set_reference          VARCHAR(128) NOT NULL,
    remedy_catalog_key          VARCHAR(48) NOT NULL,
    authority_policy_key        VARCHAR(48) NOT NULL,
    business_calendar_reference VARCHAR(64) NOT NULL,

    appeal_available            BOOLEAN NOT NULL DEFAULT true,
    appeal_window_days          SMALLINT,
    appeal_independence_required BOOLEAN NOT NULL DEFAULT true,
    external_complaint_route    VARCHAR(64),
    disclosure_version          VARCHAR(32) NOT NULL,

    validation_state            VARCHAR(16) NOT NULL DEFAULT 'UNVALIDATED',
    test_vector_reference       VARCHAR(128),
    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_support_policy_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_support_policy_versions_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_support_policy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_support_policy_versions_identity UNIQUE (policy_key, policy_version),
    CONSTRAINT ck_support_policy_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_support_policy_versions_occurrence_basis CHECK (
        occurrence_time_basis IN ('BOOKING_ACCEPTED_AT', 'OCCURRENCE_AT', 'CASE_OPENED_AT',
                                  'STAY_CHECK_IN_AT')
    ),
    CONSTRAINT ck_support_policy_versions_validation CHECK (
        validation_state IN ('UNVALIDATED', 'VALIDATING', 'VALID', 'INVALID')
    ),
    -- A published package must have been validated and approved. An unvalidated rule set becomes the
    -- reason a refund was wrong, and the record would show nobody ever checked it.
    CONSTRAINT ck_support_policy_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND validation_state = 'VALID' AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    -- Suppressing appeal is a legal posture, not a default. It must name where the complaint goes.
    CONSTRAINT ck_support_policy_versions_appeal CHECK (
        appeal_available OR external_complaint_route IS NOT NULL
    ),
    CONSTRAINT ck_support_policy_versions_appeal_window CHECK (
        NOT appeal_available OR appeal_window_days > 0
    ),
    CONSTRAINT ck_support_policy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_support_policy_versions_schema CHECK (
        decision_schema_version > 0 AND policy_version > 0
    ),
    CONSTRAINT ck_support_policy_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_support_policy_versions_open
    ON support_policy_versions (policy_key, market_id, legal_entity_id) NULLS NOT DISTINCT
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
CREATE INDEX idx_support_policy_versions_effective
    ON support_policy_versions (policy_key, effective_from DESC);
--rollback DROP TABLE support_policy_versions;

--changeset ninggiangboy:028-02-investigation-template-versions
-- What a case type requires before anyone is allowed to conclude anything: mandatory authoritative
-- facts, permitted evidence categories, response windows, proof standard and possible outcomes. An
-- agent may add steps but may not mark a required step complete without evidence or a named reason
-- it was unavailable, which is enforced in the finding and decision rows that cite this template.
CREATE TABLE investigation_template_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key                VARCHAR(48) NOT NULL,
    template_version            INTEGER NOT NULL,
    market_id                   UUID,
    case_type                   VARCHAR(32) NOT NULL,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    required_questions          TEXT[] NOT NULL DEFAULT '{}',
    mandatory_fact_domains      TEXT[] NOT NULL DEFAULT '{}',
    permitted_evidence_kinds    TEXT[] NOT NULL DEFAULT '{}',
    conflict_checks             TEXT[] NOT NULL DEFAULT '{}',
    permitted_outcomes          TEXT[] NOT NULL DEFAULT '{}',
    proof_standard              VARCHAR(32) NOT NULL,

    claimant_response_window_hours INTEGER,
    respondent_response_window_hours INTEGER,
    specialist_skill_required   VARCHAR(48),
    approval_tier_required      VARCHAR(16),
    disclosure_rule_reference   VARCHAR(128),

    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_investigation_template_versions_market FOREIGN KEY (market_id)
        REFERENCES markets (id),
    CONSTRAINT fk_investigation_template_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_investigation_template_versions_identity UNIQUE (template_key, template_version),
    CONSTRAINT ck_investigation_template_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    -- A template is selected by case type, so it must name one that can actually exist. Otherwise it
    -- is configuration that will never be reached and nothing will ever say so.
    CONSTRAINT ck_investigation_template_versions_case_type CHECK (
        case_type IN ('INFORMATION_REQUEST', 'SERVICE_COMPLAINT', 'STAY_PROBLEM', 'SAFETY_REPORT',
                      'CANCELLATION_REQUEST', 'MODIFICATION_REQUEST', 'REFUND_REQUEST',
                      'PAYMENT_PROBLEM', 'PAYOUT_PROBLEM', 'DAMAGE_CLAIM', 'PROVIDER_DISPUTE',
                      'PROTECTION_CLAIM', 'ACCOUNT_PROBLEM', 'COMPLIANCE_REQUEST', 'APPEAL',
                      'REGULATOR_COMPLAINT')
    ),
    CONSTRAINT ck_investigation_template_versions_proof CHECK (
        proof_standard IN ('BALANCE_OF_PROBABILITIES', 'CLEAR_AND_CONVINCING',
                           'DOCUMENTED_EVIDENCE_REQUIRED', 'PROVIDER_DETERMINATION')
    ),
    CONSTRAINT ck_investigation_template_versions_tier CHECK (
        approval_tier_required IS NULL
            OR approval_tier_required IN ('NONE', 'PEER', 'SUPERVISOR', 'SPECIALIST', 'LEGAL')
    ),
    -- A template with no permitted outcome cannot conclude a case, and a template with no proof
    -- requirement is an invitation to conclude one from nothing.
    CONSTRAINT ck_investigation_template_versions_outcomes CHECK (
        cardinality(permitted_outcomes) > 0
    ),
    CONSTRAINT ck_investigation_template_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_investigation_template_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_investigation_template_versions_windows CHECK (
        (claimant_response_window_hours IS NULL OR claimant_response_window_hours > 0)
        AND (respondent_response_window_hours IS NULL OR respondent_response_window_hours > 0)
    ),
    CONSTRAINT ck_investigation_template_versions_number CHECK (template_version > 0),
    CONSTRAINT ck_investigation_template_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_investigation_template_versions_open
    ON investigation_template_versions (template_key, market_id) NULLS NOT DISTINCT
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
CREATE INDEX idx_investigation_template_versions_case_type
    ON investigation_template_versions (case_type, market_id, effective_from DESC);
--rollback DROP TABLE investigation_template_versions;

--changeset ninggiangboy:028-03-remedy-catalog-versions
-- The allowlist of things support is permitted to give, and on whose money. An arbitrary
-- amount-and-reason form is how a support desk becomes an unbudgeted payments system; every remedy
-- line must name an entry here, and the entry fixes the permitted funders, the ceiling formula, the
-- downstream owner, the authority tier and whether the effect can be reversed at all.
CREATE TABLE remedy_catalog_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    catalog_key                 VARCHAR(48) NOT NULL,
    catalog_version             INTEGER NOT NULL,
    remedy_code                 VARCHAR(48) NOT NULL,
    market_id                   UUID,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    remedy_kind                 VARCHAR(32) NOT NULL,
    decision_basis_allowed      TEXT[] NOT NULL DEFAULT '{}',
    eligible_case_types         TEXT[] NOT NULL DEFAULT '{}',
    eligible_actor_roles        TEXT[] NOT NULL DEFAULT '{}',
    permitted_funders           TEXT[] NOT NULL DEFAULT '{}',
    permitted_currencies        TEXT[] NOT NULL DEFAULT '{}',
    required_finding_questions  TEXT[] NOT NULL DEFAULT '{}',
    required_evidence_kinds     TEXT[] NOT NULL DEFAULT '{}',

    -- A monetary entry needs a ceiling that can be evaluated, not a policy paragraph.
    monetary                    BOOLEAN NOT NULL,
    amount_formula              VARCHAR(48),
    maximum_amount_minor        BIGINT,
    maximum_amount_currency     VARCHAR(3),
    ceiling_scope               VARCHAR(24),

    target_domain               VARCHAR(24),
    target_command_type         VARCHAR(48),
    tax_treatment               VARCHAR(24) NOT NULL,
    document_requirement        VARCHAR(24) NOT NULL,
    authority_tier              VARCHAR(16) NOT NULL,
    approval_tier               VARCHAR(16) NOT NULL,
    reversibility               VARCHAR(24) NOT NULL,
    expiry_days                 SMALLINT,
    appeal_available            BOOLEAN NOT NULL DEFAULT true,
    explanation_template_key    VARCHAR(64) NOT NULL,

    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_remedy_catalog_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_remedy_catalog_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_remedy_catalog_versions_identity UNIQUE (catalog_key, catalog_version, remedy_code),
    CONSTRAINT ck_remedy_catalog_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_remedy_catalog_versions_kind CHECK (
        remedy_kind IN ('INFORMATION', 'APOLOGY', 'OPERATIONAL_ASSISTANCE', 'HOST_TASK',
                        'RECLEANING', 'REPAIR', 'REPLACEMENT_AMENITY', 'ACCESS_RECOVERY',
                        'BOOKING_CANCELLATION', 'BOOKING_MODIFICATION', 'RELOCATION',
                        'REFUND', 'HOST_WAIVER', 'TRAVEL_CREDIT', 'COUPON', 'GOODWILL_PAYMENT',
                        'RECEIPTED_REIMBURSEMENT', 'CLAIM_PAYMENT', 'DISPUTE_ACCEPTANCE',
                        'DISPUTE_REPRESENTMENT', 'PAYOUT_HOLD', 'PAYOUT_RELEASE', 'RECOVERY_REQUEST',
                        'LISTING_CORRECTION', 'RISK_REVIEW_REQUEST', 'MODERATION_REQUEST',
                        'SAFETY_ESCALATION')
    ),
    CONSTRAINT ck_remedy_catalog_versions_funders CHECK (
        permitted_funders <@ ARRAY['PLATFORM', 'HOST', 'GUEST', 'INSURER', 'PARTNER']
    ),
    CONSTRAINT ck_remedy_catalog_versions_basis CHECK (
        decision_basis_allowed <@ ARRAY['CONTRACTUAL', 'PROTECTION_OR_CLAIM', 'GOODWILL']
            AND cardinality(decision_basis_allowed) > 0
    ),
    CONSTRAINT ck_remedy_catalog_versions_tax CHECK (
        tax_treatment IN ('NOT_APPLICABLE', 'PRICE_ADJUSTING', 'NON_PRICE_ADJUSTING',
                          'REQUIRES_TAX_DECISION')
    ),
    CONSTRAINT ck_remedy_catalog_versions_document CHECK (
        document_requirement IN ('NONE', 'RECEIPT', 'INVOICE', 'ESTIMATE', 'PROVIDER_DETERMINATION')
    ),
    CONSTRAINT ck_remedy_catalog_versions_tiers CHECK (
        authority_tier IN ('AGENT', 'SENIOR_AGENT', 'SPECIALIST', 'SUPERVISOR', 'MANAGER', 'LEGAL')
        AND approval_tier IN ('NONE', 'PEER', 'SUPERVISOR', 'SPECIALIST', 'LEGAL')
    ),
    CONSTRAINT ck_remedy_catalog_versions_reversibility CHECK (
        reversibility IN ('IRREVERSIBLE', 'REVERSIBLE_BY_DECISION', 'RECOVERABLE_FROM_PARTY')
    ),
    -- A monetary remedy without a funder, a currency, a formula and a ceiling is a blank cheque; a
    -- non-monetary one must not carry an amount that nothing will ever check.
    CONSTRAINT ck_remedy_catalog_versions_monetary CHECK (
        (monetary AND cardinality(permitted_funders) > 0 AND cardinality(permitted_currencies) > 0
             AND amount_formula IS NOT NULL AND ceiling_scope IS NOT NULL)
        OR (NOT monetary AND amount_formula IS NULL AND maximum_amount_minor IS NULL
             AND ceiling_scope IS NULL)
    ),
    CONSTRAINT ck_remedy_catalog_versions_ceiling_scope CHECK (
        ceiling_scope IS NULL
            OR ceiling_scope IN ('PER_CASE', 'PER_BOOKING', 'PER_SOURCE_LINE', 'PER_CLAIM_ITEM',
                                 'PER_PROGRAM', 'PER_USER_PERIOD')
    ),
    CONSTRAINT ck_remedy_catalog_versions_maximum CHECK (
        (maximum_amount_minor IS NULL) = (maximum_amount_currency IS NULL)
        AND (maximum_amount_minor IS NULL OR maximum_amount_minor > 0)
        AND (maximum_amount_currency IS NULL OR maximum_amount_currency ~ '^[A-Z]{3}$')
    ),
    -- A remedy that asks another domain to act must say which domain and which command, because a
    -- downstream instruction with no named owner has nowhere to go and no one to reconcile it.
    CONSTRAINT ck_remedy_catalog_versions_target CHECK (
        (target_domain IS NULL) = (target_command_type IS NULL)
        AND (NOT monetary OR target_domain IS NOT NULL)
    ),
    CONSTRAINT ck_remedy_catalog_versions_domain CHECK (
        target_domain IS NULL
            OR target_domain IN ('PAYMENT', 'LEDGER', 'PAYOUT', 'BOOKING', 'CANCELLATION',
                                 'INVENTORY', 'PRICING', 'IDENTITY', 'RISK', 'SUPPLY',
                                 'STAY_OPERATIONS', 'COMMUNICATION')
    ),
    -- Every element is an ISO 4217 alphabetic code. A CHECK cannot iterate an array, so the array is
    -- flattened and matched whole; an empty array is permitted because a non-monetary entry has none.
    CONSTRAINT ck_remedy_catalog_versions_currencies CHECK (
        array_to_string(permitted_currencies, ',') ~ '^(|[A-Z]{3}(,[A-Z]{3})*)$'
    ),
    CONSTRAINT ck_remedy_catalog_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_remedy_catalog_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_remedy_catalog_versions_expiry CHECK (expiry_days IS NULL OR expiry_days > 0),
    CONSTRAINT ck_remedy_catalog_versions_number CHECK (catalog_version > 0),
    CONSTRAINT ck_remedy_catalog_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_remedy_catalog_versions_open
    ON remedy_catalog_versions (catalog_key, remedy_code, market_id) NULLS NOT DISTINCT
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
CREATE INDEX idx_remedy_catalog_versions_code
    ON remedy_catalog_versions (remedy_code, effective_from DESC);
--rollback DROP TABLE remedy_catalog_versions;

--changeset ninggiangboy:028-04-authority-policy-versions
-- What a role may do without asking, and where an independent approver becomes mandatory. Authority
-- is evaluated at execution time against this version, not at assignment time: revoking a grant must
-- stop the next command even though the case stays assigned to the same person.
CREATE TABLE authority_policy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    authority_policy_key        VARCHAR(48) NOT NULL,
    authority_version           INTEGER NOT NULL,
    role_code                   VARCHAR(32) NOT NULL,
    market_id                   UUID,
    legal_entity_id             UUID,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    permitted_remedy_kinds      TEXT[] NOT NULL DEFAULT '{}',
    permitted_case_types        TEXT[] NOT NULL DEFAULT '{}',
    maximum_severity            VARCHAR(24),
    permitted_sensitivity       TEXT[] NOT NULL DEFAULT '{}',
    maximum_amount_minor        BIGINT,
    maximum_amount_currency     VARCHAR(3),
    daily_exposure_minor        BIGINT,
    daily_exposure_currency     VARCHAR(3),

    approval_tier_above_maximum VARCHAR(16) NOT NULL,
    independent_approver_required BOOLEAN NOT NULL DEFAULT true,
    step_up_authentication_required BOOLEAN NOT NULL DEFAULT false,
    conflict_rules              TEXT[] NOT NULL DEFAULT '{}',
    required_certifications     TEXT[] NOT NULL DEFAULT '{}',
    on_call_only                BOOLEAN NOT NULL DEFAULT false,

    -- Break-glass is a defined set of urgent actions with a hard expiry and a mandatory review, not
    -- an escape hatch. A grant that never expires is indistinguishable from a permanent privilege.
    break_glass_permitted       BOOLEAN NOT NULL DEFAULT false,
    break_glass_actions         TEXT[] NOT NULL DEFAULT '{}',
    break_glass_maximum_minutes SMALLINT,
    break_glass_review_required BOOLEAN NOT NULL DEFAULT true,

    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_authority_policy_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_authority_policy_versions_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_authority_policy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_authority_policy_versions_identity
        UNIQUE (authority_policy_key, authority_version, role_code),
    CONSTRAINT ck_authority_policy_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_authority_policy_versions_severity CHECK (
        maximum_severity IS NULL
            OR maximum_severity IN ('S0_SAFETY_CRITICAL', 'S1_URGENT', 'S2_HIGH', 'S3_STANDARD',
                                    'S4_INFORMATIONAL')
    ),
    CONSTRAINT ck_authority_policy_versions_tier CHECK (
        approval_tier_above_maximum IN ('NONE', 'PEER', 'SUPERVISOR', 'SPECIALIST', 'LEGAL')
    ),
    CONSTRAINT ck_authority_policy_versions_sensitivity CHECK (
        permitted_sensitivity <@ ARRAY['ORDINARY', 'FINANCIAL', 'IDENTITY', 'SAFETY', 'HEALTH',
                                       'LEGAL', 'VULNERABLE_PERSON', 'RESTRICTED_AUTHORITY']
    ),
    CONSTRAINT ck_authority_policy_versions_amount CHECK (
        (maximum_amount_minor IS NULL) = (maximum_amount_currency IS NULL)
        AND (maximum_amount_minor IS NULL OR maximum_amount_minor >= 0)
        AND (maximum_amount_currency IS NULL OR maximum_amount_currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_authority_policy_versions_daily CHECK (
        (daily_exposure_minor IS NULL) = (daily_exposure_currency IS NULL)
        AND (daily_exposure_minor IS NULL OR daily_exposure_minor >= 0)
        AND (daily_exposure_currency IS NULL OR daily_exposure_currency ~ '^[A-Z]{3}$')
    ),
    -- A break-glass grant that names no action, no expiry, or no review is not break-glass.
    CONSTRAINT ck_authority_policy_versions_break_glass CHECK (
        NOT break_glass_permitted
            OR (cardinality(break_glass_actions) > 0 AND break_glass_maximum_minutes > 0
                AND break_glass_review_required AND step_up_authentication_required)
    ),
    CONSTRAINT ck_authority_policy_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_authority_policy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_authority_policy_versions_number CHECK (authority_version > 0),
    CONSTRAINT ck_authority_policy_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_authority_policy_versions_open
    ON authority_policy_versions (authority_policy_key, role_code, market_id, legal_entity_id)
    NULLS NOT DISTINCT
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
--rollback DROP TABLE authority_policy_versions;

--changeset ninggiangboy:028-05-routing-policy-versions
-- Deterministic eligibility and hard priority, with an optional model permitted only to order work
-- that is already equivalent. The rank floor is what stops a ranking change from hiding a safety
-- case behind a lucrative one.
CREATE TABLE routing_policy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    routing_policy_key          VARCHAR(48) NOT NULL,
    routing_version             INTEGER NOT NULL,
    market_id                   UUID,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    eligibility_rule_reference  VARCHAR(128) NOT NULL,
    priority_rule_reference     VARCHAR(128) NOT NULL,
    severity_rank_floor         VARCHAR(24) NOT NULL,
    model_ordering_permitted    BOOLEAN NOT NULL DEFAULT false,
    model_reference             VARCHAR(128),
    continuity_preferred        BOOLEAN NOT NULL DEFAULT true,
    conflict_exclusion_rules    TEXT[] NOT NULL DEFAULT '{}',
    overflow_queue_key          VARCHAR(48),
    overflow_threshold          INTEGER,
    escalation_route            VARCHAR(64) NOT NULL,

    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_routing_policy_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_routing_policy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_routing_policy_versions_identity UNIQUE (routing_policy_key, routing_version),
    CONSTRAINT ck_routing_policy_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_routing_policy_versions_floor CHECK (
        severity_rank_floor IN ('S0_SAFETY_CRITICAL', 'S1_URGENT', 'S2_HIGH', 'S3_STANDARD',
                                'S4_INFORMATIONAL')
    ),
    -- A model may order peers; it may not be the thing that decides who is a peer.
    CONSTRAINT ck_routing_policy_versions_model CHECK (
        model_ordering_permitted = (model_reference IS NOT NULL)
    ),
    CONSTRAINT ck_routing_policy_versions_overflow CHECK (
        (overflow_queue_key IS NULL) = (overflow_threshold IS NULL)
        AND (overflow_threshold IS NULL OR overflow_threshold > 0)
    ),
    CONSTRAINT ck_routing_policy_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_routing_policy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_routing_policy_versions_number CHECK (routing_version > 0),
    CONSTRAINT ck_routing_policy_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_routing_policy_versions_open
    ON routing_policy_versions (routing_policy_key, market_id) NULLS NOT DISTINCT
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
--rollback DROP TABLE routing_policy_versions;

--changeset ninggiangboy:028-06-protection-program-versions
-- The approved terms of a protection or insurance product, and the role the platform actually plays
-- in it. Until a market approves the role, the product may not be called insurance; the declared
-- role is stored here so naming, disclosure and claims authority can be checked against it rather
-- than against marketing copy.
CREATE TABLE protection_program_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_key                 VARCHAR(48) NOT NULL,
    program_version             INTEGER NOT NULL,
    market_id                   UUID NOT NULL,
    legal_entity_id             UUID,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    platform_role               VARCHAR(24) NOT NULL,
    display_name_key            VARCHAR(64) NOT NULL,
    covered_party               VARCHAR(16) NOT NULL,
    covered_object              VARCHAR(24) NOT NULL,
    coverage_territory          VARCHAR(64) NOT NULL,
    coverage_period_rule        VARCHAR(32) NOT NULL,

    limit_amount_minor          BIGINT NOT NULL,
    limit_currency              VARCHAR(3) NOT NULL,
    deductible_amount_minor     BIGINT NOT NULL DEFAULT 0,
    per_item_limit_minor        BIGINT,
    exclusions_reference        VARCHAR(128) NOT NULL,
    premium_amount_minor        BIGINT,
    premium_currency            VARCHAR(3),

    provider_account_id         UUID,
    provider_program_reference  VARCHAR(128),
    adjudication_authority      VARCHAR(24) NOT NULL,
    submission_deadline_days    SMALLINT,
    consent_required            BOOLEAN NOT NULL DEFAULT true,
    disclosure_version          VARCHAR(32) NOT NULL,
    document_reference          VARCHAR(256),
    complaint_route             VARCHAR(64) NOT NULL,

    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_protection_program_versions_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_protection_program_versions_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_protection_program_versions_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_protection_program_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_protection_program_versions_identity UNIQUE (program_key, program_version),
    CONSTRAINT ck_protection_program_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_protection_program_versions_role CHECK (
        platform_role IN ('CONTRACTUAL_PROTECTION', 'INSURANCE_DISTRIBUTOR', 'INTRODUCER_ONLY')
    ),
    CONSTRAINT ck_protection_program_versions_party CHECK (
        covered_party IN ('HOST', 'GUEST', 'BOTH')
    ),
    CONSTRAINT ck_protection_program_versions_object CHECK (
        covered_object IN ('PROPERTY_DAMAGE', 'CONTENTS', 'LIABILITY', 'TRIP_INTERRUPTION',
                           'INCOME_LOSS', 'MEDICAL')
    ),
    CONSTRAINT ck_protection_program_versions_authority CHECK (
        adjudication_authority IN ('PLATFORM_DETERMINISTIC', 'PLATFORM_WITH_APPROVAL',
                                   'PROVIDER_ADMINISTRATOR', 'CARRIER')
    ),
    -- A regulated role adjudicates through the approved provider, not through an internal evaluator.
    -- The alternative is a platform quietly deciding insurance claims it is not licensed to decide.
    CONSTRAINT ck_protection_program_versions_regulated CHECK (
        platform_role = 'CONTRACTUAL_PROTECTION'
            OR (adjudication_authority IN ('PROVIDER_ADMINISTRATOR', 'CARRIER')
                AND provider_account_id IS NOT NULL AND provider_program_reference IS NOT NULL)
    ),
    CONSTRAINT ck_protection_program_versions_limits CHECK (
        limit_amount_minor > 0 AND limit_currency ~ '^[A-Z]{3}$'
        AND deductible_amount_minor >= 0 AND deductible_amount_minor < limit_amount_minor
        AND (per_item_limit_minor IS NULL
             OR (per_item_limit_minor > 0 AND per_item_limit_minor <= limit_amount_minor))
    ),
    CONSTRAINT ck_protection_program_versions_premium CHECK (
        (premium_amount_minor IS NULL) = (premium_currency IS NULL)
        AND (premium_amount_minor IS NULL OR premium_amount_minor >= 0)
        AND (premium_currency IS NULL OR premium_currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_protection_program_versions_deadline CHECK (
        submission_deadline_days IS NULL OR submission_deadline_days > 0
    ),
    CONSTRAINT ck_protection_program_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_protection_program_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_protection_program_versions_number CHECK (program_version > 0),
    CONSTRAINT ck_protection_program_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_protection_program_versions_open
    ON protection_program_versions (program_key, market_id)
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
--rollback DROP TABLE protection_program_versions;

--changeset ninggiangboy:028-07-support-queues
-- Where work waits, and what a person must hold to take it. Capacity limits exist so that overflow
-- and escalation happen visibly instead of promises quietly lengthening behind a growing backlog.
CREATE TABLE support_queues (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    queue_key                   VARCHAR(48) NOT NULL,
    display_name_key            VARCHAR(64) NOT NULL,
    market_id                   UUID,
    legal_entity_id             UUID,

    status                      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    case_types                  TEXT[] NOT NULL DEFAULT '{}',
    severity_floor              VARCHAR(24) NOT NULL,
    sensitivity_scope           TEXT[] NOT NULL DEFAULT '{}',
    languages                   TEXT[] NOT NULL DEFAULT '{}',
    required_skills             TEXT[] NOT NULL DEFAULT '{}',
    business_calendar_reference VARCHAR(64) NOT NULL,
    time_zone                   VARCHAR(64) NOT NULL,

    concurrency_limit           INTEGER,
    depth_warning_threshold     INTEGER,
    depth_overflow_threshold    INTEGER,
    overflow_queue_key          VARCHAR(48),
    escalation_route            VARCHAR(64) NOT NULL,
    paging_required             BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_support_queues_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_support_queues_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT uk_support_queues_key UNIQUE (queue_key),
    CONSTRAINT ck_support_queues_status CHECK (status IN ('ACTIVE', 'DRAINING', 'RETIRED')),
    CONSTRAINT ck_support_queues_floor CHECK (
        severity_floor IN ('S0_SAFETY_CRITICAL', 'S1_URGENT', 'S2_HIGH', 'S3_STANDARD',
                           'S4_INFORMATIONAL')
    ),
    CONSTRAINT ck_support_queues_sensitivity CHECK (
        sensitivity_scope <@ ARRAY['ORDINARY', 'FINANCIAL', 'IDENTITY', 'SAFETY', 'HEALTH', 'LEGAL',
                                   'VULNERABLE_PERSON', 'RESTRICTED_AUTHORITY']
    ),
    -- A queue that can hold safety-critical work must be able to wake a human. A silent pager on an
    -- S0 queue is the same as no queue at all.
    CONSTRAINT ck_support_queues_paging CHECK (
        severity_floor NOT IN ('S0_SAFETY_CRITICAL', 'S1_URGENT') OR paging_required
    ),
    CONSTRAINT ck_support_queues_thresholds CHECK (
        (concurrency_limit IS NULL OR concurrency_limit > 0)
        AND (depth_warning_threshold IS NULL OR depth_warning_threshold > 0)
        AND (depth_overflow_threshold IS NULL OR depth_overflow_threshold > 0)
        AND (depth_warning_threshold IS NULL OR depth_overflow_threshold IS NULL
             OR depth_overflow_threshold >= depth_warning_threshold)
    ),
    CONSTRAINT ck_support_queues_overflow CHECK (
        overflow_queue_key IS NULL OR overflow_queue_key <> queue_key
    ),
    CONSTRAINT ck_support_queues_row_version CHECK (version >= 0)
);

CREATE INDEX idx_support_queues_market ON support_queues (market_id, status);
--rollback DROP TABLE support_queues;

--changeset ninggiangboy:028-08-agent-skill-grants
-- What one person is actually permitted to hold, bounded in time. Revocation is a closed interval,
-- not a deleted row, because "who could have acted on this case in March" must stay answerable.
CREATE TABLE agent_skill_grants (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    skill_code                  VARCHAR(48) NOT NULL,
    role_code                   VARCHAR(32) NOT NULL,
    authority_policy_version_id UUID NOT NULL,
    market_id                   UUID,
    legal_entity_id             UUID,

    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    granted_at                  TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(48),
    granted_by_account_holder_id UUID NOT NULL,

    certification_reference     VARCHAR(128),
    certification_expires_at    TIMESTAMPTZ,
    languages                   TEXT[] NOT NULL DEFAULT '{}',
    delegated_from_grant_id     UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_agent_skill_grants_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_agent_skill_grants_authority FOREIGN KEY (authority_policy_version_id)
        REFERENCES authority_policy_versions (id),
    CONSTRAINT fk_agent_skill_grants_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_agent_skill_grants_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_agent_skill_grants_granter FOREIGN KEY (granted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_agent_skill_grants_delegation FOREIGN KEY (delegated_from_grant_id)
        REFERENCES agent_skill_grants (id),
    CONSTRAINT ck_agent_skill_grants_state CHECK (
        state IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'REVOKED')
    ),
    -- Nobody grants themselves authority, and a delegation must come from somebody else's grant.
    CONSTRAINT ck_agent_skill_grants_self CHECK (
        account_holder_id <> granted_by_account_holder_id
    ),
    CONSTRAINT ck_agent_skill_grants_revocation CHECK (
        (state = 'REVOKED') = (revoked_at IS NOT NULL)
        AND (revoked_at IS NULL OR (revocation_reason IS NOT NULL AND revoked_at >= granted_at))
    ),
    CONSTRAINT ck_agent_skill_grants_interval CHECK (
        effective_until IS NULL OR effective_until > granted_at
    ),
    CONSTRAINT ck_agent_skill_grants_certification CHECK (
        certification_expires_at IS NULL OR certification_reference IS NOT NULL
    ),
    CONSTRAINT ck_agent_skill_grants_row_version CHECK (version >= 0)
);

-- One live grant of a skill per person and scope. Two would make a revocation ambiguous, and an
-- ambiguous revocation is a revocation that did not happen.
CREATE UNIQUE INDEX uk_agent_skill_grants_live
    ON agent_skill_grants (account_holder_id, skill_code, market_id, legal_entity_id)
    NULLS NOT DISTINCT
    WHERE state IN ('ACTIVE', 'SUSPENDED');
CREATE INDEX idx_agent_skill_grants_skill ON agent_skill_grants (skill_code, state);
--rollback DROP TABLE agent_skill_grants;

--changeset ninggiangboy:028-09-support-cases
-- The coordination aggregate: one problem scope, its participants, its deadlines and its decisions.
-- A single overloaded case type would make routing, policy selection and reporting all depend on one
-- badly chosen word, so classification is kept in controlled dimensions and the dimensions are
-- versioned. State alone also cannot describe the work, because a case can be investigating while a
-- provider deadline is expiring and a refund outcome is unknown -- so safety, waiting party,
-- financial execution, appeal and legal hold are separate columns, not values of one status.
CREATE TABLE support_cases (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_reference              VARCHAR(32) NOT NULL,
    market_id                   UUID NOT NULL,
    legal_entity_id             UUID,

    -- The scope this case is about. Every one of these is a neighbouring domain's identifier; none
    -- of them is written by this domain.
    primary_booking_id          UUID,
    primary_listing_id          UUID,
    primary_property_id         UUID,
    primary_conversation_id     UUID,
    primary_incident_id         UUID,
    primary_payment_dispute_id  UUID,

    source_channel              VARCHAR(24) NOT NULL,
    intake_command_id           VARCHAR(96),
    anonymous_report            BOOLEAN NOT NULL DEFAULT false,
    reporter_account_holder_id  UUID,
    reporter_role               VARCHAR(16) NOT NULL,
    represented_party_account_holder_id UUID,
    report_text_reference       VARCHAR(256),
    locale                      VARCHAR(16) NOT NULL,
    contact_preference          VARCHAR(16) NOT NULL,

    -- Classification, versioned. A later correction keeps the prior value in
    -- case_classification_history along with the routing it caused.
    taxonomy_version            INTEGER NOT NULL,
    case_type                   VARCHAR(32) NOT NULL,
    journey_stage               VARCHAR(24) NOT NULL,
    issue_family                VARCHAR(24) NOT NULL,
    request_kind                VARCHAR(24) NOT NULL,
    impact_class                VARCHAR(24) NOT NULL,
    responsibility_status       VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    sensitivity_class           VARCHAR(24) NOT NULL DEFAULT 'ORDINARY',

    severity                    VARCHAR(24) NOT NULL,
    severity_rank               SMALLINT NOT NULL,
    severity_floor              VARCHAR(24) NOT NULL,
    severity_change_reason      VARCHAR(48),

    state                       VARCHAR(24) NOT NULL DEFAULT 'NEW',
    lifecycle_episode           SMALLINT NOT NULL DEFAULT 1,
    support_queue_id            UUID,
    owner_team                  VARCHAR(48),
    owner_account_holder_id     UUID,
    priority                    INTEGER NOT NULL DEFAULT 0,

    -- The separate dimensions. None of them is implied by state, and a user-interface status must
    -- not be able to erase any of them.
    safety_state                VARCHAR(24) NOT NULL DEFAULT 'NOT_APPLICABLE',
    waiting_on                  VARCHAR(24) NOT NULL DEFAULT 'NONE',
    financial_execution_state   VARCHAR(24) NOT NULL DEFAULT 'NONE',
    appeal_state                VARCHAR(24) NOT NULL DEFAULT 'NONE',
    provider_deadline_at        TIMESTAMPTZ,
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    legal_hold_reference        VARCHAR(128),

    occurrence_at               TIMESTAMPTZ,
    received_at                 TIMESTAMPTZ NOT NULL,
    opened_at                   TIMESTAMPTZ NOT NULL,
    first_response_at           TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    reopened_at                 TIMESTAMPTZ,
    withdrawn_at                TIMESTAMPTZ,

    support_policy_version_id   UUID,
    investigation_template_version_id UUID,
    routing_policy_version_id   UUID,
    latest_decision_id          UUID,
    duplicate_of_case_id        UUID,

    -- Closure may name an exception owner, but only with a date on which someone must look again.
    -- "Owned by finance" with no deadline is how a case closes over money nobody ever paid.
    closure_exception_owner_account_holder_id UUID,
    closure_exception_reason    VARCHAR(48),
    closure_exception_deadline_at TIMESTAMPTZ,

    retention_class             VARCHAR(24) NOT NULL,
    quality_sampled             BOOLEAN NOT NULL DEFAULT false,
    next_sequence               BIGINT NOT NULL DEFAULT 1,
    source_system               VARCHAR(24) NOT NULL DEFAULT 'NATIVE',
    import_digest               CHAR(64),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_support_cases_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_support_cases_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_support_cases_booking FOREIGN KEY (primary_booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_support_cases_listing FOREIGN KEY (primary_listing_id) REFERENCES listings (id),
    CONSTRAINT fk_support_cases_property FOREIGN KEY (primary_property_id) REFERENCES properties (id),
    CONSTRAINT fk_support_cases_conversation FOREIGN KEY (primary_conversation_id)
        REFERENCES conversations (id),
    CONSTRAINT fk_support_cases_incident FOREIGN KEY (primary_incident_id) REFERENCES incidents (id),
    CONSTRAINT fk_support_cases_payment_dispute FOREIGN KEY (primary_payment_dispute_id)
        REFERENCES payment_disputes (id),
    CONSTRAINT fk_support_cases_reporter FOREIGN KEY (reporter_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_support_cases_represented FOREIGN KEY (represented_party_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_support_cases_queue FOREIGN KEY (support_queue_id) REFERENCES support_queues (id),
    CONSTRAINT fk_support_cases_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_support_cases_policy FOREIGN KEY (support_policy_version_id)
        REFERENCES support_policy_versions (id),
    CONSTRAINT fk_support_cases_template FOREIGN KEY (investigation_template_version_id)
        REFERENCES investigation_template_versions (id),
    CONSTRAINT fk_support_cases_routing FOREIGN KEY (routing_policy_version_id)
        REFERENCES routing_policy_versions (id),
    CONSTRAINT fk_support_cases_duplicate FOREIGN KEY (duplicate_of_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_support_cases_exception_owner FOREIGN KEY (closure_exception_owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_support_cases_reference UNIQUE (case_reference),
    CONSTRAINT ck_support_cases_source_channel CHECK (
        source_channel IN ('WEB', 'MOBILE_APP', 'EMAIL', 'PHONE', 'CHAT', 'IN_STAY_FLOW',
                           'PARTNER', 'INTERNAL', 'AUTOMATED_DETECTION', 'REGULATOR', 'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_support_cases_reporter_role CHECK (
        reporter_role IN ('GUEST', 'HOST', 'CO_HOST', 'AGENT', 'SYSTEM', 'THIRD_PARTY', 'AUTHORITY')
    ),
    CONSTRAINT ck_support_cases_contact_preference CHECK (
        contact_preference IN ('IN_APP', 'EMAIL', 'SMS', 'PHONE', 'NONE')
    ),
    CONSTRAINT ck_support_cases_case_type CHECK (
        case_type IN ('INFORMATION_REQUEST', 'SERVICE_COMPLAINT', 'STAY_PROBLEM', 'SAFETY_REPORT',
                      'CANCELLATION_REQUEST', 'MODIFICATION_REQUEST', 'REFUND_REQUEST',
                      'PAYMENT_PROBLEM', 'PAYOUT_PROBLEM', 'DAMAGE_CLAIM', 'PROVIDER_DISPUTE',
                      'PROTECTION_CLAIM', 'ACCOUNT_PROBLEM', 'COMPLIANCE_REQUEST', 'APPEAL',
                      'REGULATOR_COMPLAINT')
    ),
    CONSTRAINT ck_support_cases_journey CHECK (
        journey_stage IN ('PRE_BOOKING', 'CHECKOUT', 'PRE_STAY', 'CHECK_IN', 'IN_STAY',
                          'CHECK_OUT', 'POST_STAY', 'PAYOUT', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_support_cases_issue_family CHECK (
        issue_family IN ('ACCESS', 'CLEANLINESS', 'AMENITY', 'LISTING_MISMATCH', 'HOST_CONDUCT',
                         'GUEST_CONDUCT', 'CANCELLATION', 'REFUND', 'PAYMENT', 'PAYOUT', 'DAMAGE',
                         'SAFETY', 'REVIEW', 'ACCOUNT', 'COMPLIANCE', 'OTHER')
    ),
    CONSTRAINT ck_support_cases_request_kind CHECK (
        request_kind IN ('INFORMATION', 'OPERATIONAL_HELP', 'CONTRACT_CHANGE', 'REFUND_OR_CREDIT',
                         'COMPENSATION', 'CLAIM', 'PROVIDER_DISPUTE', 'APPEAL', 'COMPLAINT')
    ),
    CONSTRAINT ck_support_cases_impact CHECK (
        impact_class IN ('INCONVENIENCE', 'SERVICE_LOSS', 'MONETARY_EXPOSURE', 'PROPERTY_LOSS',
                         'DISPLACEMENT', 'INJURY_OR_SAFETY', 'ACCOUNT_COMPROMISE',
                         'REGULATORY_COMPLAINT')
    ),
    CONSTRAINT ck_support_cases_responsibility CHECK (
        responsibility_status IN ('UNKNOWN', 'CONTESTED', 'SHARED', 'ESTABLISHED', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_support_cases_sensitivity CHECK (
        sensitivity_class IN ('ORDINARY', 'FINANCIAL', 'IDENTITY', 'SAFETY', 'HEALTH', 'LEGAL',
                              'VULNERABLE_PERSON', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_support_cases_severity CHECK (
        severity IN ('S0_SAFETY_CRITICAL', 'S1_URGENT', 'S2_HIGH', 'S3_STANDARD', 'S4_INFORMATIONAL')
        AND severity_floor IN ('S0_SAFETY_CRITICAL', 'S1_URGENT', 'S2_HIGH', 'S3_STANDARD',
                               'S4_INFORMATIONAL')
    ),
    -- The rank is stored so queues can order without parsing names, and pinned to the label so the
    -- two cannot drift into disagreeing about which case is more urgent.
    CONSTRAINT ck_support_cases_severity_rank CHECK (
        severity_rank = CASE severity
            WHEN 'S0_SAFETY_CRITICAL' THEN 0 WHEN 'S1_URGENT' THEN 1 WHEN 'S2_HIGH' THEN 2
            WHEN 'S3_STANDARD' THEN 3 ELSE 4 END
    ),
    -- Deterministic intake sets a floor. A model, a tier, or an agent's mood may raise urgency but
    -- may never take a case below what the reporter's own safety answers established.
    CONSTRAINT ck_support_cases_severity_floor CHECK (
        severity_rank <= CASE severity_floor
            WHEN 'S0_SAFETY_CRITICAL' THEN 0 WHEN 'S1_URGENT' THEN 1 WHEN 'S2_HIGH' THEN 2
            WHEN 'S3_STANDARD' THEN 3 ELSE 4 END
    ),
    CONSTRAINT ck_support_cases_state CHECK (
        state IN ('NEW', 'TRIAGED', 'ASSIGNED', 'INVESTIGATING', 'DECISION_PENDING',
                  'REMEDY_PENDING', 'ESCALATED', 'RESOLVED', 'CLOSED', 'REOPENED',
                  'APPEAL_PENDING', 'DUPLICATE', 'WITHDRAWN')
    ),
    CONSTRAINT ck_support_cases_safety_state CHECK (
        safety_state IN ('NOT_APPLICABLE', 'SCREENING', 'GUIDANCE_GIVEN', 'ESCALATED',
                         'HANDED_OFF', 'RESOLVED')
    ),
    CONSTRAINT ck_support_cases_waiting CHECK (
        waiting_on IN ('NONE', 'CUSTOMER', 'HOST', 'AGENT', 'THIRD_PARTY', 'PROVIDER',
                       'INTERNAL_DOMAIN', 'LEGAL')
    ),
    CONSTRAINT ck_support_cases_financial_state CHECK (
        financial_execution_state IN ('NONE', 'PENDING', 'EXECUTING', 'PARTIAL', 'UNKNOWN',
                                      'COMPLETE', 'REVERSED')
    ),
    CONSTRAINT ck_support_cases_appeal_state CHECK (
        appeal_state IN ('NONE', 'ELIGIBLE', 'SUBMITTED', 'REVIEWING', 'DECIDED', 'EXHAUSTED')
    ),
    CONSTRAINT ck_support_cases_retention CHECK (
        retention_class IN ('STANDARD', 'EXTENDED', 'FINANCIAL_RECORD', 'LEGAL_HOLD',
                            'REGULATORY_RECORD', 'MINIMAL')
    ),
    CONSTRAINT ck_support_cases_source_system CHECK (
        source_system IN ('NATIVE', 'LEGACY_IMPORTED')
    ),
    -- An anonymous report has no account behind it, so it cannot also claim a reporter; and an
    -- imported case must carry the digest of what it was imported from.
    CONSTRAINT ck_support_cases_anonymous CHECK (
        NOT anonymous_report OR reporter_account_holder_id IS NULL
    ),
    CONSTRAINT ck_support_cases_import CHECK (
        (source_system = 'LEGACY_IMPORTED') = (import_digest IS NOT NULL)
    ),
    -- Anonymous reporting is a safety affordance. A request that moves money or changes an account
    -- needs an authenticated owner, because there is nobody to pay and nobody to hold responsible.
    CONSTRAINT ck_support_cases_authenticated_request CHECK (
        request_kind NOT IN ('CONTRACT_CHANGE', 'REFUND_OR_CREDIT', 'COMPENSATION', 'CLAIM',
                             'PROVIDER_DISPUTE')
            OR reporter_account_holder_id IS NOT NULL
    ),
    -- A safety case may not be filed as informational, whichever way the classification is corrected.
    CONSTRAINT ck_support_cases_safety_floor CHECK (
        (case_type <> 'SAFETY_REPORT' AND impact_class <> 'INJURY_OR_SAFETY')
            OR severity_rank <= 1
    ),
    CONSTRAINT ck_support_cases_duplicate CHECK (
        (state = 'DUPLICATE') = (duplicate_of_case_id IS NOT NULL)
        AND (duplicate_of_case_id IS NULL OR duplicate_of_case_id <> id)
    ),
    CONSTRAINT ck_support_cases_withdrawn CHECK (
        (state = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)
    ),
    CONSTRAINT ck_support_cases_resolution CHECK (
        (resolved_at IS NULL OR resolved_at >= opened_at)
        AND (closed_at IS NULL OR closed_at >= opened_at)
        AND (first_response_at IS NULL OR first_response_at >= received_at)
        AND opened_at >= received_at
    ),
    CONSTRAINT ck_support_cases_closed CHECK (
        state <> 'CLOSED' OR closed_at IS NOT NULL
    ),
    CONSTRAINT ck_support_cases_reopened CHECK (
        (lifecycle_episode > 1) = (reopened_at IS NOT NULL)
    ),
    CONSTRAINT ck_support_cases_legal_hold CHECK (
        legal_hold = (legal_hold_reference IS NOT NULL)
    ),
    -- A closure exception is a named person and a date. Any of the three present demands the others.
    CONSTRAINT ck_support_cases_closure_exception CHECK (
        (closure_exception_owner_account_holder_id IS NULL)
            = (closure_exception_deadline_at IS NULL)
        AND (closure_exception_owner_account_holder_id IS NULL)
            = (closure_exception_reason IS NULL)
    ),
    CONSTRAINT ck_support_cases_episode CHECK (lifecycle_episode > 0),
    CONSTRAINT ck_support_cases_sequence CHECK (next_sequence > 0),
    CONSTRAINT ck_support_cases_row_version CHECK (version >= 0)
);

-- One case per intake command. Two identical submissions from a retrying client must converge on the
-- same case rather than produce two investigations of one problem.
CREATE UNIQUE INDEX uk_support_cases_intake
    ON support_cases (source_channel, reporter_account_holder_id, intake_command_id)
    NULLS NOT DISTINCT
    WHERE intake_command_id IS NOT NULL;
CREATE INDEX idx_support_cases_queue_priority
    ON support_cases (support_queue_id, severity_rank, priority DESC, opened_at)
    WHERE state NOT IN ('CLOSED', 'DUPLICATE', 'WITHDRAWN');
CREATE INDEX idx_support_cases_owner
    ON support_cases (owner_account_holder_id, state)
    WHERE state NOT IN ('CLOSED', 'DUPLICATE', 'WITHDRAWN');
CREATE INDEX idx_support_cases_booking ON support_cases (primary_booking_id);
CREATE INDEX idx_support_cases_provider_deadline
    ON support_cases (provider_deadline_at)
    WHERE provider_deadline_at IS NOT NULL AND state <> 'CLOSED';
CREATE INDEX idx_support_cases_legal_hold ON support_cases (market_id) WHERE legal_hold;
--rollback DROP TABLE support_cases;

--changeset ninggiangboy:028-10-case-participants
-- Who is on a case, in what role, and on what basis they are allowed to be there. Representation is
-- stored explicitly, because someone acting on another person's behalf is a consent decision with an
-- expiry, not a permanent property of the account. Visibility scope is per participant, so attaching
-- a second reporter to a case cannot silently show them the first reporter's evidence.
CREATE TABLE case_participants (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    participant_role            VARCHAR(24) NOT NULL,
    account_holder_id           UUID,
    external_party_reference    VARCHAR(128),
    external_party_kind         VARCHAR(24),

    representation_basis        VARCHAR(24),
    represented_account_holder_id UUID,
    consent_reference           VARCHAR(128),
    consent_expires_at          TIMESTAMPTZ,

    visibility_scope            VARCHAR(24) NOT NULL,
    contact_preference          VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
    locale                      VARCHAR(16),
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    removal_reason              VARCHAR(48),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_participants_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_participants_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_participants_represented FOREIGN KEY (represented_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_case_participants_role CHECK (
        participant_role IN ('REPORTER', 'CLAIMANT', 'RESPONDENT', 'GUEST', 'HOST', 'CO_HOST',
                             'ASSIGNED_AGENT', 'SUPERVISOR', 'SPECIALIST', 'ADJUSTER', 'LEGAL',
                             'INTERPRETER', 'REPRESENTATIVE', 'PROVIDER', 'AUTHORITY', 'OBSERVER')
    ),
    -- A participant is one of ours or somebody else's, never both and never neither.
    CONSTRAINT ck_case_participants_identity CHECK (
        (account_holder_id IS NOT NULL) <> (external_party_reference IS NOT NULL)
        AND (external_party_reference IS NULL) = (external_party_kind IS NULL)
    ),
    CONSTRAINT ck_case_participants_external_kind CHECK (
        external_party_kind IS NULL
            OR external_party_kind IN ('INSURER', 'ADJUSTER', 'CONTRACTOR', 'LAW_ENFORCEMENT',
                                       'REGULATOR', 'OMBUDSMAN', 'LEGAL_COUNSEL', 'OTHER')
    ),
    -- Acting for somebody else requires naming them, naming the basis, and -- where the basis is a
    -- consent rather than a legal status -- a reference to the consent and a date it stops.
    CONSTRAINT ck_case_participants_representation CHECK (
        (representation_basis IS NULL) = (represented_account_holder_id IS NULL)
        AND (representation_basis IS NULL
             OR representation_basis IN ('ACCOUNT_DELEGATION', 'WRITTEN_CONSENT', 'LEGAL_GUARDIAN',
                                         'POWER_OF_ATTORNEY', 'ORGANIZATION_MEMBERSHIP',
                                         'STATUTORY_AUTHORITY'))
        AND (representation_basis NOT IN ('ACCOUNT_DELEGATION', 'WRITTEN_CONSENT')
             OR (consent_reference IS NOT NULL AND consent_expires_at IS NOT NULL))
    ),
    CONSTRAINT ck_case_participants_self_representation CHECK (
        represented_account_holder_id IS NULL
            OR account_holder_id IS NULL
            OR represented_account_holder_id <> account_holder_id
    ),
    -- The same controlled vocabulary support_cases uses. A participant whose preferred channel is a
    -- word nothing recognises is a participant nobody can be sure was contacted.
    CONSTRAINT ck_case_participants_contact_preference CHECK (
        contact_preference IN ('IN_APP', 'EMAIL', 'SMS', 'PHONE', 'NONE')
    ),
    CONSTRAINT ck_case_participants_visibility CHECK (
        visibility_scope IN ('NONE', 'OWN_SUBMISSIONS', 'PARTICIPANT_SHARED', 'FULL_CASE',
                             'INTERNAL_FULL', 'RESTRICTED_AUTHORITY')
    ),
    -- A party to the dispute never sees the internal record; only staff roles can.
    CONSTRAINT ck_case_participants_internal_visibility CHECK (
        visibility_scope <> 'INTERNAL_FULL'
            OR participant_role IN ('ASSIGNED_AGENT', 'SUPERVISOR', 'SPECIALIST', 'LEGAL', 'ADJUSTER')
    ),
    CONSTRAINT ck_case_participants_interval CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_case_participants_removal CHECK (
        removal_reason IS NULL OR effective_until IS NOT NULL
    ),
    CONSTRAINT ck_case_participants_row_version CHECK (version >= 0)
);

-- One live participation per person and role on a case. A second live row would make revoking access
-- a question of which row you happened to find.
CREATE UNIQUE INDEX uk_case_participants_live
    ON case_participants (support_case_id, participant_role, account_holder_id,
                          external_party_reference)
    NULLS NOT DISTINCT
    WHERE effective_until IS NULL;
CREATE INDEX idx_case_participants_holder ON case_participants (account_holder_id, support_case_id);
--rollback DROP TABLE case_participants;

--changeset ninggiangboy:028-11-case-relationships
-- Typed links between a case and the things it relates to, without merging their authorities. Fuzzy
-- similarity never merges cases automatically: merging can show one party another party's private
-- evidence and can collapse two distinct legal deadlines into one, so a merge is an authorized act
-- and an unmerge is an additive correction that restores independent visibility.
CREATE TABLE case_relationships (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    relation_type               VARCHAR(32) NOT NULL,
    related_domain              VARCHAR(24) NOT NULL,
    related_case_id             UUID,
    related_object_id           UUID,
    related_object_reference    VARCHAR(128),
    related_object_version      BIGINT,

    direction                   VARCHAR(16) NOT NULL,
    visibility_scope            VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_ONLY',
    established_by_actor_type   VARCHAR(16) NOT NULL,
    established_by_account_holder_id UUID,
    established_at              TIMESTAMPTZ NOT NULL,
    reason_code                 VARCHAR(48) NOT NULL,

    detached_at                 TIMESTAMPTZ,
    detach_reason               VARCHAR(48),
    detached_by_account_holder_id UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_relationships_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_case_relationships_related_case FOREIGN KEY (related_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_case_relationships_actor FOREIGN KEY (established_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_relationships_detacher FOREIGN KEY (detached_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_case_relationships_type CHECK (
        relation_type IN ('DUPLICATE_OF', 'RELATED_TO', 'CHILD_OF', 'MERGED_INTO', 'UNMERGED_FROM',
                          'CAUSED_BY', 'SUPERSEDES', 'ESCALATED_FROM', 'REFERENCES_INCIDENT',
                          'REFERENCES_CLAIM', 'REFERENCES_PROVIDER_DISPUTE', 'REFERENCES_RISK_CASE')
    ),
    CONSTRAINT ck_case_relationships_domain CHECK (
        related_domain IN ('SUPPORT', 'BOOKING', 'PAYMENT', 'LEDGER', 'PAYOUT', 'CANCELLATION',
                           'STAY_OPERATIONS', 'RISK', 'REVIEW', 'IDENTITY', 'SUPPLY',
                           'COMMUNICATION', 'EXTERNAL')
    ),
    CONSTRAINT ck_case_relationships_direction CHECK (
        direction IN ('OUTGOING', 'INCOMING', 'SYMMETRIC')
    ),
    CONSTRAINT ck_case_relationships_visibility CHECK (
        visibility_scope IN ('INTERNAL_ONLY', 'PARTICIPANT_SHARED', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_relationships_actor_type CHECK (
        established_by_actor_type IN ('AGENT', 'SUPERVISOR', 'SYSTEM_EXACT_KEY')
    ),
    -- A merge is never automatic. Only a named human or a deterministic exact-key rule may declare it.
    CONSTRAINT ck_case_relationships_merge_authority CHECK (
        relation_type NOT IN ('DUPLICATE_OF', 'MERGED_INTO')
            OR established_by_actor_type = 'SYSTEM_EXACT_KEY'
            OR established_by_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_case_relationships_target CHECK (
        (related_domain = 'SUPPORT') = (related_case_id IS NOT NULL)
        AND (related_case_id IS NOT NULL OR related_object_id IS NOT NULL
             OR related_object_reference IS NOT NULL)
        AND (related_case_id IS NULL OR related_case_id <> support_case_id)
    ),
    CONSTRAINT ck_case_relationships_detach CHECK (
        (detached_at IS NULL) = (detach_reason IS NULL)
        AND (detached_at IS NULL OR detached_at >= established_at)
    ),
    CONSTRAINT ck_case_relationships_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_case_relationships_live
    ON case_relationships (support_case_id, relation_type, related_domain, related_case_id,
                           related_object_id, related_object_reference)
    NULLS NOT DISTINCT
    WHERE detached_at IS NULL;
CREATE INDEX idx_case_relationships_related_case ON case_relationships (related_case_id);
--rollback DROP TABLE case_relationships;

--changeset ninggiangboy:028-12-case-classification-history
-- Every classification change keeps the prior value and the routing it caused. Correcting a case
-- from a refund request to a damage claim changes which policy, queue and deadline applied; erasing
-- the old value would erase the reason the first three days went the way they did.
CREATE TABLE case_classification_history (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    lifecycle_episode           SMALLINT NOT NULL,
    dimension                   VARCHAR(24) NOT NULL,
    prior_value                 VARCHAR(32),
    new_value                   VARCHAR(32) NOT NULL,

    taxonomy_version            INTEGER NOT NULL,
    changed_by_actor_type       VARCHAR(16) NOT NULL,
    changed_by_account_holder_id UUID,
    reason_code                 VARCHAR(48) NOT NULL,
    evidence_reference          VARCHAR(128),
    routing_consequence         VARCHAR(48),
    prior_queue_id              UUID,
    new_queue_id                UUID,

    effective_at                TIMESTAMPTZ NOT NULL,
    committed_at                TIMESTAMPTZ NOT NULL,
    correlation_id              UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_classification_history_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_case_classification_history_actor FOREIGN KEY (changed_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_classification_history_prior_queue FOREIGN KEY (prior_queue_id)
        REFERENCES support_queues (id),
    CONSTRAINT fk_case_classification_history_new_queue FOREIGN KEY (new_queue_id)
        REFERENCES support_queues (id),
    CONSTRAINT ck_case_classification_history_dimension CHECK (
        dimension IN ('CASE_TYPE', 'JOURNEY_STAGE', 'ISSUE_FAMILY', 'REQUEST_KIND', 'IMPACT_CLASS',
                      'RESPONSIBILITY_STATUS', 'SENSITIVITY_CLASS', 'SEVERITY')
    ),
    CONSTRAINT ck_case_classification_history_actor_type CHECK (
        changed_by_actor_type IN ('AGENT', 'SUPERVISOR', 'SYSTEM', 'INTAKE_RULE')
    ),
    -- A human change names a human. A severity reduction always does, because lowering urgency is the
    -- one classification change that can hurt somebody.
    CONSTRAINT ck_case_classification_history_human CHECK (
        changed_by_actor_type IN ('SYSTEM', 'INTAKE_RULE')
            OR changed_by_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_case_classification_history_change CHECK (
        prior_value IS NULL OR prior_value <> new_value
    ),
    CONSTRAINT ck_case_classification_history_episode CHECK (lifecycle_episode > 0),
    CONSTRAINT ck_case_classification_history_taxonomy CHECK (taxonomy_version > 0)
);

CREATE INDEX idx_case_classification_history_case
    ON case_classification_history (support_case_id, committed_at DESC);
--rollback DROP TABLE case_classification_history;

--changeset ninggiangboy:028-13-case-transitions
-- Append-only record of every state change, carrying the version the actor expected to be acting on.
-- A unique command identity per case makes a retried transition replay rather than fire twice, which
-- matters because a transition carries an SLA effect and an outbound event.
CREATE TABLE case_transitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    lifecycle_episode           SMALLINT NOT NULL,
    sequence_number             BIGINT NOT NULL,
    from_state                  VARCHAR(24),
    to_state                    VARCHAR(24) NOT NULL,

    command_id                  VARCHAR(96) NOT NULL,
    expected_case_version       BIGINT NOT NULL,
    transition_policy_version   INTEGER NOT NULL,
    actor_type                  VARCHAR(16) NOT NULL,
    actor_account_holder_id     UUID,
    reason_code                 VARCHAR(48) NOT NULL,
    explanation_template_key    VARCHAR(64),

    related_work_item_id        UUID,
    related_decision_id         UUID,
    sla_effect                  VARCHAR(24) NOT NULL DEFAULT 'NONE',
    outbox_event_id             UUID,

    occurred_at                 TIMESTAMPTZ NOT NULL,
    committed_at                TIMESTAMPTZ NOT NULL,
    correlation_id              UUID,
    causation_id                UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_transitions_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_transitions_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_transitions_outbox FOREIGN KEY (outbox_event_id) REFERENCES outbox_events (id),
    CONSTRAINT uk_case_transitions_command UNIQUE (support_case_id, command_id),
    CONSTRAINT uk_case_transitions_sequence UNIQUE (support_case_id, sequence_number),
    CONSTRAINT ck_case_transitions_states CHECK (
        to_state IN ('NEW', 'TRIAGED', 'ASSIGNED', 'INVESTIGATING', 'DECISION_PENDING',
                     'REMEDY_PENDING', 'ESCALATED', 'RESOLVED', 'CLOSED', 'REOPENED',
                     'APPEAL_PENDING', 'DUPLICATE', 'WITHDRAWN')
        AND (from_state IS NULL OR from_state IN ('NEW', 'TRIAGED', 'ASSIGNED', 'INVESTIGATING',
                                                  'DECISION_PENDING', 'REMEDY_PENDING', 'ESCALATED',
                                                  'RESOLVED', 'CLOSED', 'REOPENED', 'APPEAL_PENDING',
                                                  'DUPLICATE', 'WITHDRAWN'))
        AND (from_state IS NULL OR from_state <> to_state)
    ),
    CONSTRAINT ck_case_transitions_actor_type CHECK (
        actor_type IN ('AGENT', 'SUPERVISOR', 'SYSTEM', 'PARTICIPANT', 'SCHEDULER')
    ),
    CONSTRAINT ck_case_transitions_human CHECK (
        actor_type IN ('SYSTEM', 'SCHEDULER') OR actor_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_case_transitions_sla_effect CHECK (
        sla_effect IN ('NONE', 'START', 'PAUSE', 'RESUME', 'COMPLETE', 'RESET')
    ),
    -- Closure is the one transition that must always say who closed it and why in words a
    -- participant can be shown. A case that closed for reason "SYSTEM" explains nothing to anybody.
    CONSTRAINT ck_case_transitions_closure CHECK (
        to_state <> 'CLOSED' OR explanation_template_key IS NOT NULL
    ),
    CONSTRAINT ck_case_transitions_episode CHECK (lifecycle_episode > 0 AND sequence_number > 0),
    CONSTRAINT ck_case_transitions_version CHECK (expected_case_version >= 0),
    CONSTRAINT ck_case_transitions_times CHECK (committed_at >= occurred_at)
);

CREATE INDEX idx_case_transitions_case
    ON case_transitions (support_case_id, sequence_number DESC);
--rollback DROP TABLE case_transitions;

--changeset ninggiangboy:028-14-case-contacts
-- One inbound or outbound interaction with a participant. A contact may create or join a case but is
-- not the case itself, and it is kept apart from internal notes so that an internal observation can
-- never be delivered to a participant by being mistaken for a message.
CREATE TABLE case_contacts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    lifecycle_episode           SMALLINT NOT NULL,
    direction                   VARCHAR(16) NOT NULL,
    channel                     VARCHAR(24) NOT NULL,
    purpose                     VARCHAR(32) NOT NULL,

    participant_id              UUID,
    author_actor_type           VARCHAR(16) NOT NULL,
    author_account_holder_id    UUID,

    -- Content lives where its domain owns it. A support desk that copies message bodies becomes a
    -- second, unmoderated store of everything two people ever said to each other.
    content_reference           VARCHAR(256),
    conversation_id             UUID,
    message_id                  UUID,
    notification_intent_id      UUID,
    template_key                VARCHAR(64),
    template_version            INTEGER,
    locale                      VARCHAR(16),
    visibility_scope            VARCHAR(24) NOT NULL,

    delivery_state              VARCHAR(16) NOT NULL DEFAULT 'NOT_APPLICABLE',
    delivery_reference          VARCHAR(128),
    consent_reference           VARCHAR(128),
    recording_consent           BOOLEAN,

    occurred_at                 TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,
    source_event_id             UUID,
    corrects_contact_id         UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_contacts_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_contacts_participant FOREIGN KEY (participant_id)
        REFERENCES case_participants (id),
    CONSTRAINT fk_case_contacts_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_contacts_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id),
    CONSTRAINT fk_case_contacts_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_case_contacts_notification FOREIGN KEY (notification_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_case_contacts_correction FOREIGN KEY (corrects_contact_id)
        REFERENCES case_contacts (id),
    CONSTRAINT ck_case_contacts_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_case_contacts_channel CHECK (
        channel IN ('IN_APP_MESSAGE', 'EMAIL', 'SMS', 'PUSH', 'PHONE', 'CHAT', 'POSTAL',
                    'PARTNER_PORTAL', 'PROVIDER_PORTAL')
    ),
    CONSTRAINT ck_case_contacts_purpose CHECK (
        purpose IN ('INTAKE', 'ACKNOWLEDGEMENT', 'INVESTIGATION_UPDATE', 'EVIDENCE_REQUEST',
                    'PROVISIONAL_OFFER', 'FINAL_DECISION', 'REMEDY_SUBMITTED', 'REMEDY_COMPLETED',
                    'PROVIDER_DELAY', 'APPEAL_INFORMATION', 'CLOSURE', 'SAFETY_GUIDANCE', 'OTHER')
    ),
    CONSTRAINT ck_case_contacts_author_type CHECK (
        author_actor_type IN ('AGENT', 'SUPERVISOR', 'PARTICIPANT', 'SYSTEM', 'PROVIDER')
    ),
    CONSTRAINT ck_case_contacts_visibility CHECK (
        visibility_scope IN ('PARTICIPANT_SHARED', 'SENDER_AND_RECIPIENT', 'INTERNAL_ONLY',
                             'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_contacts_delivery CHECK (
        delivery_state IN ('NOT_APPLICABLE', 'PENDING', 'SENT', 'DELIVERED', 'FAILED', 'SUPPRESSED')
    ),
    -- An outbound contact to a participant is rendered from an approved template. Undocumented
    -- promises in free text are the classic way a support desk creates obligations nobody authorized.
    CONSTRAINT ck_case_contacts_template CHECK (
        direction = 'INBOUND' OR author_actor_type = 'PROVIDER'
            OR (template_key IS NOT NULL AND template_version IS NOT NULL AND locale IS NOT NULL)
    ),
    CONSTRAINT ck_case_contacts_template_pair CHECK (
        (template_key IS NULL) = (template_version IS NULL)
        AND (template_version IS NULL OR template_version > 0)
    ),
    -- A recorded call must say whether consent was captured, in either direction.
    CONSTRAINT ck_case_contacts_recording CHECK (
        channel <> 'PHONE' OR recording_consent IS NOT NULL
    ),
    CONSTRAINT ck_case_contacts_correction CHECK (
        corrects_contact_id IS NULL OR corrects_contact_id <> id
    ),
    CONSTRAINT ck_case_contacts_times CHECK (received_at >= occurred_at),
    CONSTRAINT ck_case_contacts_episode CHECK (lifecycle_episode > 0)
);

CREATE INDEX idx_case_contacts_case ON case_contacts (support_case_id, occurred_at DESC);
CREATE INDEX idx_case_contacts_message ON case_contacts (message_id) WHERE message_id IS NOT NULL;
--rollback DROP TABLE case_contacts;

--changeset ninggiangboy:028-15-case-notes
-- Internal working notes, deliberately a different table from case_contacts. A note is never
-- automatically disclosed as a participant message, and a note that is later disclosed under a
-- lawful request records that disclosure rather than changing its own visibility in place.
CREATE TABLE case_notes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    lifecycle_episode           SMALLINT NOT NULL,
    note_kind                   VARCHAR(24) NOT NULL,
    author_actor_type           VARCHAR(16) NOT NULL,
    author_account_holder_id    UUID,

    content_reference           VARCHAR(256) NOT NULL,
    content_hash                CHAR(64) NOT NULL,
    locale                      VARCHAR(16),
    sensitivity_class           VARCHAR(24) NOT NULL DEFAULT 'ORDINARY',
    visibility_scope            VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_ONLY',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,

    written_at                  TIMESTAMPTZ NOT NULL,
    supersedes_note_id          UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_notes_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_notes_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_notes_supersession FOREIGN KEY (supersedes_note_id) REFERENCES case_notes (id),
    CONSTRAINT ck_case_notes_kind CHECK (
        note_kind IN ('OBSERVATION', 'HANDOFF_SUMMARY', 'INVESTIGATION_STEP', 'POLICY_REASONING',
                      'SUPERVISOR_GUIDANCE', 'QUALITY_REMARK', 'SAFETY_NOTE', 'LEGAL_NOTE')
    ),
    CONSTRAINT ck_case_notes_author_type CHECK (
        author_actor_type IN ('AGENT', 'SUPERVISOR', 'SPECIALIST', 'LEGAL', 'SYSTEM')
    ),
    CONSTRAINT ck_case_notes_human CHECK (
        author_actor_type = 'SYSTEM' OR author_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_case_notes_sensitivity CHECK (
        sensitivity_class IN ('ORDINARY', 'FINANCIAL', 'IDENTITY', 'SAFETY', 'HEALTH', 'LEGAL',
                              'VULNERABLE_PERSON', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_notes_visibility CHECK (
        visibility_scope IN ('INTERNAL_ONLY', 'SUPERVISOR_ONLY', 'LEGAL_ONLY',
                             'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_notes_supersession CHECK (
        supersedes_note_id IS NULL OR supersedes_note_id <> id
    ),
    CONSTRAINT ck_case_notes_episode CHECK (lifecycle_episode > 0)
);

CREATE INDEX idx_case_notes_case ON case_notes (support_case_id, written_at DESC);
--rollback DROP TABLE case_notes;

--changeset ninggiangboy:028-16-case-timeline-entries
-- A read projection, not a system of record. Every entry names the source that is authoritative for
-- it and the version it was read at, so an agent can open the real thing; a timeline summary never
-- authorizes a remedy. The watermark and completeness columns exist so a missing event shows as a
-- known gap rather than as an absence of anything having happened.
CREATE TABLE case_timeline_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    schema_version              INTEGER NOT NULL,
    entry_kind                  VARCHAR(32) NOT NULL,

    source_domain               VARCHAR(24) NOT NULL,
    source_object_type          VARCHAR(48) NOT NULL,
    source_object_id            UUID,
    source_object_reference     VARCHAR(128),
    source_object_version       BIGINT,
    source_event_id             UUID,

    actor_class                 VARCHAR(24) NOT NULL,
    actor_account_holder_id     UUID,
    market_id                   UUID,
    legal_entity_id             UUID,

    event_at                    TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,
    committed_at                TIMESTAMPTZ NOT NULL,
    tie_breaker                 BIGINT NOT NULL,

    visibility_scope            VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_ONLY',
    redaction_policy_key        VARCHAR(48),
    summary_template_key        VARCHAR(64) NOT NULL,
    supersedes_entry_id         UUID,
    correction_reason           VARCHAR(48),

    projection_watermark_at     TIMESTAMPTZ NOT NULL,
    projection_complete         BOOLEAN NOT NULL DEFAULT true,
    incompleteness_reason       VARCHAR(48),
    correlation_id              UUID,
    causation_id                UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_timeline_entries_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_case_timeline_entries_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_timeline_entries_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_case_timeline_entries_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_case_timeline_entries_supersession FOREIGN KEY (supersedes_entry_id)
        REFERENCES case_timeline_entries (id),
    CONSTRAINT ck_case_timeline_entries_source_domain CHECK (
        source_domain IN ('SUPPORT', 'BOOKING', 'PAYMENT', 'LEDGER', 'PAYOUT', 'CANCELLATION',
                          'INVENTORY', 'PRICING', 'STAY_OPERATIONS', 'RISK', 'REVIEW', 'IDENTITY',
                          'SUPPLY', 'COMMUNICATION', 'EXTERNAL')
    ),
    CONSTRAINT ck_case_timeline_entries_actor_class CHECK (
        actor_class IN ('GUEST', 'HOST', 'AGENT', 'SUPERVISOR', 'SYSTEM', 'PROVIDER', 'AUTHORITY')
    ),
    CONSTRAINT ck_case_timeline_entries_visibility CHECK (
        visibility_scope IN ('INTERNAL_ONLY', 'PARTICIPANT_SHARED', 'SENDER_AND_RECIPIENT',
                             'RESTRICTED_AUTHORITY')
    ),
    -- A projected entry must be traceable to something. An entry with no source identity is a claim
    -- the timeline is making on its own behalf, which is exactly what a projection may not do.
    CONSTRAINT ck_case_timeline_entries_source CHECK (
        source_object_id IS NOT NULL OR source_object_reference IS NOT NULL
    ),
    CONSTRAINT ck_case_timeline_entries_completeness CHECK (
        projection_complete = (incompleteness_reason IS NULL)
    ),
    CONSTRAINT ck_case_timeline_entries_correction CHECK (
        (supersedes_entry_id IS NULL) = (correction_reason IS NULL)
        AND (supersedes_entry_id IS NULL OR supersedes_entry_id <> id)
    ),
    CONSTRAINT ck_case_timeline_entries_times CHECK (
        received_at >= event_at AND committed_at >= received_at
    ),
    CONSTRAINT ck_case_timeline_entries_schema CHECK (schema_version > 0)
);

-- One projected entry per source event on a case. Replaying a consumer must converge, not duplicate.
CREATE UNIQUE INDEX uk_case_timeline_entries_source_event
    ON case_timeline_entries (support_case_id, source_domain, source_event_id)
    WHERE source_event_id IS NOT NULL;
CREATE INDEX idx_case_timeline_entries_case
    ON case_timeline_entries (support_case_id, event_at DESC, tie_breaker DESC);
--rollback DROP TABLE case_timeline_entries;

--changeset ninggiangboy:028-17-case-work-items
-- The unit a person or worker actually claims. Work is separate from case state because a case can
-- be investigating while three different people hold three different tasks on it, and because an
-- expired claim must return the work without changing who owns the case or discarding their draft.
CREATE TABLE case_work_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    lifecycle_episode           SMALLINT NOT NULL,
    damage_claim_id             UUID,
    work_item_type              VARCHAR(32) NOT NULL,
    support_queue_id            UUID NOT NULL,

    required_skill              VARCHAR(48),
    severity_rank               SMALLINT NOT NULL,
    priority                    INTEGER NOT NULL DEFAULT 0,
    priority_basis              VARCHAR(24) NOT NULL,
    due_at                      TIMESTAMPTZ,

    state                       VARCHAR(16) NOT NULL DEFAULT 'READY',
    owner_account_holder_id     UUID,
    attempt_count               INTEGER NOT NULL DEFAULT 0,
    maximum_attempts            INTEGER NOT NULL DEFAULT 5,
    last_failure_reason         VARCHAR(48),

    completion_evidence_reference VARCHAR(128),
    completed_at                TIMESTAMPTZ,
    completed_by_account_holder_id UUID,
    transferred_from_account_holder_id UUID,
    transfer_reason             VARCHAR(48),
    handoff_summary_note_id     UUID,
    escalated_to_queue_id       UUID,
    escalation_reason           VARCHAR(48),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_work_items_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_work_items_queue FOREIGN KEY (support_queue_id) REFERENCES support_queues (id),
    CONSTRAINT fk_case_work_items_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_work_items_completer FOREIGN KEY (completed_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_work_items_transferor FOREIGN KEY (transferred_from_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_work_items_handoff FOREIGN KEY (handoff_summary_note_id)
        REFERENCES case_notes (id),
    CONSTRAINT fk_case_work_items_escalation_queue FOREIGN KEY (escalated_to_queue_id)
        REFERENCES support_queues (id),
    CONSTRAINT ck_case_work_items_type CHECK (
        work_item_type IN ('TRIAGE', 'SAFETY_RESPONSE', 'FIRST_RESPONSE', 'EVIDENCE_COLLECTION',
                           'EVIDENCE_REVIEW', 'RESPONDENT_STATEMENT', 'VALUATION', 'ADJUDICATION',
                           'APPROVAL', 'REMEDY_EXECUTION', 'PROVIDER_SUBMISSION', 'PROVIDER_QUERY',
                           'RECONCILIATION', 'APPEAL_REVIEW', 'QUALITY_REVIEW', 'CLOSURE_CHECK')
    ),
    CONSTRAINT ck_case_work_items_state CHECK (
        state IN ('READY', 'CLAIMED', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED', 'CANCELLED',
                  'DEAD_LETTER')
    ),
    CONSTRAINT ck_case_work_items_priority_basis CHECK (
        priority_basis IN ('DETERMINISTIC_SEVERITY', 'DEADLINE', 'MONETARY_EXPOSURE',
                           'MODEL_ORDERING', 'MANUAL')
    ),
    -- A model may order equivalent work. It may not be the reason a safety or urgent item sits where
    -- it does, because an ordering a model chose is an ordering nobody can be held to.
    CONSTRAINT ck_case_work_items_urgent_priority CHECK (
        severity_rank > 1 OR priority_basis IN ('DETERMINISTIC_SEVERITY', 'DEADLINE')
    ),
    CONSTRAINT ck_case_work_items_ownership CHECK (
        (state IN ('CLAIMED', 'IN_PROGRESS')) = (owner_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_case_work_items_completion CHECK (
        (state = 'COMPLETED') = (completed_at IS NOT NULL)
        AND (completed_at IS NULL OR completed_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_case_work_items_transfer CHECK (
        (transferred_from_account_holder_id IS NULL) = (transfer_reason IS NULL)
    ),
    CONSTRAINT ck_case_work_items_escalation CHECK (
        (escalated_to_queue_id IS NULL) = (escalation_reason IS NULL)
    ),
    CONSTRAINT ck_case_work_items_attempts CHECK (
        attempt_count >= 0 AND maximum_attempts > 0 AND attempt_count <= maximum_attempts
    ),
    CONSTRAINT ck_case_work_items_severity_rank CHECK (severity_rank BETWEEN 0 AND 4),
    CONSTRAINT ck_case_work_items_episode CHECK (lifecycle_episode > 0),
    CONSTRAINT ck_case_work_items_row_version CHECK (version >= 0)
);

-- One open item of a type per case episode. Two triage tasks on one case is two people doing the
-- same work and one of them discovering it was wasted.
CREATE UNIQUE INDEX uk_case_work_items_open
    ON case_work_items (support_case_id, lifecycle_episode, work_item_type)
    WHERE state IN ('READY', 'CLAIMED', 'IN_PROGRESS', 'BLOCKED');
CREATE INDEX idx_case_work_items_claimable
    ON case_work_items (support_queue_id, severity_rank, priority DESC, due_at)
    WHERE state = 'READY';
CREATE INDEX idx_case_work_items_due
    ON case_work_items (due_at)
    WHERE state IN ('READY', 'CLAIMED', 'IN_PROGRESS') AND due_at IS NOT NULL;
CREATE INDEX idx_case_work_items_owner
    ON case_work_items (owner_account_holder_id)
    WHERE state IN ('CLAIMED', 'IN_PROGRESS');
--rollback DROP TABLE case_work_items;

--changeset ninggiangboy:028-18-work-item-leases
-- A claim on work, held for a bounded time under a monotonic fencing token. The token is what makes
-- a stale worker harmless: it can wake up after its lease expired and be refused, rather than
-- finalising work that somebody else has since redone.
CREATE TABLE work_item_leases (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_work_item_id           UUID NOT NULL,
    fencing_token               BIGINT NOT NULL,
    owner_kind                  VARCHAR(16) NOT NULL,
    owner_account_holder_id     UUID,
    owner_worker_reference      VARCHAR(96),

    acquired_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    renewed_at                  TIMESTAMPTZ,
    renewal_count               INTEGER NOT NULL DEFAULT 0,
    released_at                 TIMESTAMPTZ,
    release_reason              VARCHAR(24),

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_work_item_leases_item FOREIGN KEY (case_work_item_id)
        REFERENCES case_work_items (id),
    CONSTRAINT fk_work_item_leases_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_work_item_leases_token UNIQUE (case_work_item_id, fencing_token),
    CONSTRAINT ck_work_item_leases_owner_kind CHECK (owner_kind IN ('AGENT', 'WORKER')),
    CONSTRAINT ck_work_item_leases_owner CHECK (
        (owner_kind = 'AGENT') = (owner_account_holder_id IS NOT NULL)
        AND (owner_kind = 'WORKER') = (owner_worker_reference IS NOT NULL)
    ),
    CONSTRAINT ck_work_item_leases_release CHECK (
        (released_at IS NULL) = (release_reason IS NULL)
        AND (released_at IS NULL OR released_at >= acquired_at)
        AND (release_reason IS NULL
             OR release_reason IN ('COMPLETED', 'ABANDONED', 'EXPIRED', 'TRANSFERRED', 'REVOKED'))
    ),
    CONSTRAINT ck_work_item_leases_expiry CHECK (
        expires_at > acquired_at
        AND (renewed_at IS NULL OR renewed_at >= acquired_at)
        AND renewal_count >= 0
        AND (renewal_count = 0) = (renewed_at IS NULL)
    ),
    CONSTRAINT ck_work_item_leases_token_positive CHECK (fencing_token > 0)
);

-- One unreleased lease per work item. Two live leases is two workers believing they own the task,
-- which the fencing token then has to resolve after both have already acted.
CREATE UNIQUE INDEX uk_work_item_leases_live
    ON work_item_leases (case_work_item_id) WHERE released_at IS NULL;
CREATE INDEX idx_work_item_leases_expiry
    ON work_item_leases (expires_at) WHERE released_at IS NULL;
--rollback DROP TABLE work_item_leases;

--changeset ninggiangboy:028-19-case-sla-clocks
-- Independent clocks, one per promise. A single "SLA breached" flag would hide that acknowledgement
-- was instant, the decision was on time, and only the provider deadline slipped. Pauses are recorded
-- with an allowlisted reason and a total, because a pause inferred from a generic pending status is
-- how a deadline quietly stops running while a customer is still waiting.
CREATE TABLE case_sla_clocks (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    lifecycle_episode           SMALLINT NOT NULL,
    clock_type                  VARCHAR(32) NOT NULL,

    support_policy_version_id   UUID,
    policy_reference            VARCHAR(96) NOT NULL,
    business_calendar_reference VARCHAR(64) NOT NULL,
    time_zone                   VARCHAR(64) NOT NULL,
    parallel_deadline_permitted BOOLEAN NOT NULL DEFAULT false,

    started_at                  TIMESTAMPTZ NOT NULL,
    due_at                      TIMESTAMPTZ NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    paused_at                   TIMESTAMPTZ,
    pause_reason                VARCHAR(32),
    paused_total_seconds        BIGINT NOT NULL DEFAULT 0,
    completed_at                TIMESTAMPTZ,
    completion_event_reference  VARCHAR(96),
    breached_at                 TIMESTAMPTZ,
    escalation_level            SMALLINT NOT NULL DEFAULT 0,
    escalation_route            VARCHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_sla_clocks_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_sla_clocks_policy FOREIGN KEY (support_policy_version_id)
        REFERENCES support_policy_versions (id),
    CONSTRAINT ck_case_sla_clocks_type CHECK (
        clock_type IN ('ACKNOWLEDGEMENT', 'FIRST_HUMAN_RESPONSE', 'NEXT_ACTION', 'HOST_RESPONSE',
                       'GUEST_RESPONSE', 'EVIDENCE_SUBMISSION', 'DECISION', 'REMEDY_EXECUTION',
                       'APPEAL', 'PROVIDER_DISPUTE', 'PROTECTION_SUBMISSION')
    ),
    CONSTRAINT ck_case_sla_clocks_state CHECK (
        state IN ('RUNNING', 'PAUSED', 'COMPLETED', 'BREACHED', 'CANCELLED')
    ),
    CONSTRAINT ck_case_sla_clocks_pause_reason CHECK (
        pause_reason IS NULL
            OR pause_reason IN ('AWAITING_CLAIMANT_EVIDENCE', 'AWAITING_RESPONDENT_STATEMENT',
                                'AWAITING_CUSTOMER_REPLY', 'AWAITING_THIRD_PARTY',
                                'OUTSIDE_BUSINESS_HOURS', 'APPROVED_EXCEPTION')
    ),
    -- Safety acknowledgement and externally fixed deadlines do not pause. A statutory clock does not
    -- stop because the office closed, and a safety acknowledgement that pauses is not one.
    CONSTRAINT ck_case_sla_clocks_unpausable CHECK (
        clock_type NOT IN ('ACKNOWLEDGEMENT', 'PROVIDER_DISPUTE', 'PROTECTION_SUBMISSION')
            OR (state <> 'PAUSED' AND paused_at IS NULL AND paused_total_seconds = 0)
    ),
    CONSTRAINT ck_case_sla_clocks_pause CHECK (
        (state = 'PAUSED') = (paused_at IS NOT NULL)
        AND (paused_at IS NULL OR pause_reason IS NOT NULL)
        AND paused_total_seconds >= 0
    ),
    CONSTRAINT ck_case_sla_clocks_completion CHECK (
        (state = 'COMPLETED') = (completed_at IS NOT NULL)
        AND (completed_at IS NULL OR completion_event_reference IS NOT NULL)
    ),
    CONSTRAINT ck_case_sla_clocks_breach CHECK (
        (state = 'BREACHED') = (breached_at IS NOT NULL)
        AND (breached_at IS NULL OR breached_at >= due_at)
    ),
    CONSTRAINT ck_case_sla_clocks_due CHECK (due_at > started_at),
    CONSTRAINT ck_case_sla_clocks_escalation CHECK (escalation_level >= 0),
    CONSTRAINT ck_case_sla_clocks_episode CHECK (lifecycle_episode > 0),
    CONSTRAINT ck_case_sla_clocks_row_version CHECK (version >= 0)
);

-- One effective clock of a type per episode, unless the policy explicitly supports parallel
-- deadlines. Two live acknowledgement clocks means two different answers to "were we on time".
CREATE UNIQUE INDEX uk_case_sla_clocks_effective
    ON case_sla_clocks (support_case_id, lifecycle_episode, clock_type)
    WHERE state IN ('RUNNING', 'PAUSED') AND NOT parallel_deadline_permitted;
CREATE INDEX idx_case_sla_clocks_due
    ON case_sla_clocks (due_at) WHERE state IN ('RUNNING', 'PAUSED');
CREATE INDEX idx_case_sla_clocks_case ON case_sla_clocks (support_case_id, clock_type);
--rollback DROP TABLE case_sla_clocks;

--changeset ninggiangboy:028-20-case-evidence-items
-- What was submitted, by whom, when, and how much weight the question at hand permits it. Provenance
-- is a ladder rather than one confidence number, because a payment record can prove that money moved
-- and nothing about the condition of a sofa. A digest proves the platform holds these exact bytes;
-- it proves nothing about whether the depicted event happened.
CREATE TABLE case_evidence_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    evidence_kind               VARCHAR(32) NOT NULL,

    source_type                 VARCHAR(24) NOT NULL,
    source_domain               VARCHAR(24),
    source_object_id            UUID,
    source_object_reference     VARCHAR(128),
    source_object_version       BIGINT,
    submitted_by_actor_type     VARCHAR(16) NOT NULL,
    submitted_by_account_holder_id UUID,
    represented_party_account_holder_id UUID,

    provenance_class            VARCHAR(40) NOT NULL,
    provenance_rank             SMALLINT NOT NULL,
    captured_at                 TIMESTAMPTZ,
    capture_metadata_present    BOOLEAN NOT NULL DEFAULT false,
    received_at                 TIMESTAMPTZ NOT NULL,

    storage_object_reference    VARCHAR(256),
    storage_object_version      VARCHAR(64),
    content_hash                CHAR(64),
    media_type                  VARCHAR(96),
    byte_size                   BIGINT,
    original_filename_reference VARCHAR(256),
    language                    VARCHAR(16),

    scan_state                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    state                       VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    sensitivity_class           VARCHAR(24) NOT NULL DEFAULT 'ORDINARY',
    visibility_scope            VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_ONLY',
    retention_class             VARCHAR(24) NOT NULL,
    retention_expires_at        TIMESTAMPTZ,
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    legal_hold_reference        VARCHAR(128),
    legal_hold_until            TIMESTAMPTZ,
    deleted_at                  TIMESTAMPTZ,
    deletion_method             VARCHAR(24),

    supersedes_evidence_id      UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_evidence_items_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_case_evidence_items_submitter FOREIGN KEY (submitted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_evidence_items_represented FOREIGN KEY (represented_party_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_evidence_items_supersession FOREIGN KEY (supersedes_evidence_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT ck_case_evidence_items_kind CHECK (
        evidence_kind IN ('PHOTO', 'VIDEO', 'DOCUMENT', 'RECEIPT', 'INVOICE', 'ESTIMATE',
                          'INSPECTION_REPORT', 'MESSAGE_REVISION', 'REVIEW_REVISION',
                          'LISTING_SNAPSHOT', 'BOOKING_SNAPSHOT', 'PAYMENT_ARTIFACT',
                          'PROVIDER_ARTIFACT', 'ACCESS_OBSERVATION', 'INCIDENT_RECORD',
                          'CALL_METADATA', 'AGENT_NOTE', 'THIRD_PARTY_REPORT', 'DERIVATIVE')
    ),
    CONSTRAINT ck_case_evidence_items_source_type CHECK (
        source_type IN ('PARTICIPANT_UPLOAD', 'DOMAIN_FACT', 'PROVIDER_ARTIFACT', 'AGENT_CAPTURE',
                        'THIRD_PARTY', 'DERIVED')
    ),
    CONSTRAINT ck_case_evidence_items_submitter_type CHECK (
        submitted_by_actor_type IN ('GUEST', 'HOST', 'AGENT', 'SYSTEM', 'PROVIDER', 'THIRD_PARTY')
    ),
    -- The provenance ladder from the feature document, with its rank pinned to its name so ordering
    -- and labelling cannot disagree about which of two artifacts carries more weight.
    CONSTRAINT ck_case_evidence_items_provenance CHECK (
        provenance_class IN ('AUTHORITATIVE_DOMAIN_FACT', 'AUTHENTICATED_PROVIDER_FACT',
                             'CONTEMPORANEOUS_PARTICIPANT_ARTIFACT', 'LATER_PARTICIPANT_ASSERTION',
                             'DERIVED_WITH_LINEAGE', 'UNVERIFIED_EXTERNAL_ASSERTION')
        AND provenance_rank = CASE provenance_class
            WHEN 'AUTHORITATIVE_DOMAIN_FACT' THEN 1 WHEN 'AUTHENTICATED_PROVIDER_FACT' THEN 2
            WHEN 'CONTEMPORANEOUS_PARTICIPANT_ARTIFACT' THEN 3
            WHEN 'LATER_PARTICIPANT_ASSERTION' THEN 4 WHEN 'DERIVED_WITH_LINEAGE' THEN 5
            ELSE 6 END
    ),
    -- Contemporaneous means the capture metadata is actually there. Without it the claim is a later
    -- assertion about when a photograph was taken, and the ladder must not be climbed on assertion.
    CONSTRAINT ck_case_evidence_items_contemporaneous CHECK (
        provenance_class <> 'CONTEMPORANEOUS_PARTICIPANT_ARTIFACT'
            OR (capture_metadata_present AND captured_at IS NOT NULL)
    ),
    CONSTRAINT ck_case_evidence_items_scan_state CHECK (
        scan_state IN ('NOT_APPLICABLE', 'PENDING', 'CLEAN', 'INFECTED', 'UNSCANNABLE')
    ),
    CONSTRAINT ck_case_evidence_items_state CHECK (
        state IN ('REGISTERED', 'QUARANTINED', 'AVAILABLE', 'INCLUDED_IN_REVIEW',
                  'INCLUDED_IN_SUBMISSION', 'REJECTED_UNSAFE', 'REDACTED_DERIVATIVE_CREATED',
                  'RETENTION_EXPIRED', 'DELETION_PENDING', 'DELETED_OR_CRYPTO_ERASED')
    ),
    CONSTRAINT ck_case_evidence_items_sensitivity CHECK (
        sensitivity_class IN ('ORDINARY', 'FINANCIAL', 'IDENTITY', 'SAFETY', 'HEALTH', 'LEGAL',
                              'VULNERABLE_PERSON', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_evidence_items_visibility CHECK (
        visibility_scope IN ('SUBMITTER_ONLY', 'INTERNAL_ONLY', 'PARTICIPANT_SHARED',
                             'PROVIDER_DISCLOSED', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_evidence_items_retention CHECK (
        retention_class IN ('STANDARD', 'EXTENDED', 'FINANCIAL_RECORD', 'LEGAL_HOLD',
                            'REGULATORY_RECORD', 'MINIMAL')
    ),
    -- An uploaded artifact must be identified by its bytes. Without a digest there is no way to show
    -- later that the file a decision cited is the file still in storage.
    CONSTRAINT ck_case_evidence_items_stored_bytes CHECK (
        source_type NOT IN ('PARTICIPANT_UPLOAD', 'AGENT_CAPTURE', 'THIRD_PARTY', 'DERIVED')
            OR (storage_object_reference IS NOT NULL AND content_hash IS NOT NULL
                AND media_type IS NOT NULL AND byte_size > 0)
    ),
    -- A projected domain fact names the object and the version it was read at, so a later change to
    -- that object is visible as a change rather than silently rewriting the evidence.
    CONSTRAINT ck_case_evidence_items_domain_fact CHECK (
        source_type <> 'DOMAIN_FACT'
            OR (source_domain IS NOT NULL AND source_object_version IS NOT NULL
                AND (source_object_id IS NOT NULL OR source_object_reference IS NOT NULL))
    ),
    -- Nothing becomes usable before it is known to be safe, and an infected upload is never
    -- available for anything.
    CONSTRAINT ck_case_evidence_items_quarantine CHECK (
        state NOT IN ('AVAILABLE', 'INCLUDED_IN_REVIEW', 'INCLUDED_IN_SUBMISSION',
                      'REDACTED_DERIVATIVE_CREATED')
            OR scan_state IN ('CLEAN', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_case_evidence_items_rejected CHECK (
        state <> 'REJECTED_UNSAFE' OR scan_state IN ('INFECTED', 'UNSCANNABLE')
    ),
    -- A hold suspends deletion for as long as it is stated to run; it never widens who may read.
    CONSTRAINT ck_case_evidence_items_legal_hold CHECK (
        legal_hold = (legal_hold_reference IS NOT NULL)
        AND (NOT legal_hold OR state NOT IN ('DELETION_PENDING', 'DELETED_OR_CRYPTO_ERASED'))
    ),
    CONSTRAINT ck_case_evidence_items_deletion CHECK (
        (state = 'DELETED_OR_CRYPTO_ERASED') = (deleted_at IS NOT NULL)
        AND (deleted_at IS NULL OR deletion_method IS NOT NULL)
        AND (deletion_method IS NULL
             OR deletion_method IN ('OBJECT_DELETED', 'CRYPTO_ERASED', 'PROVIDER_DELETED'))
    ),
    CONSTRAINT ck_case_evidence_items_supersession CHECK (
        supersedes_evidence_id IS NULL OR supersedes_evidence_id <> id
    ),
    CONSTRAINT ck_case_evidence_items_times CHECK (
        captured_at IS NULL OR captured_at <= received_at
    ),
    CONSTRAINT ck_case_evidence_items_row_version CHECK (version >= 0)
);

-- The same bytes submitted twice to one case are one item. Re-uploading is not new evidence.
CREATE UNIQUE INDEX uk_case_evidence_items_digest
    ON case_evidence_items (support_case_id, content_hash)
    WHERE content_hash IS NOT NULL AND state <> 'DELETED_OR_CRYPTO_ERASED';
CREATE INDEX idx_case_evidence_items_case ON case_evidence_items (support_case_id, received_at DESC);
CREATE INDEX idx_case_evidence_items_claim ON case_evidence_items (damage_claim_id);
CREATE INDEX idx_case_evidence_items_retention
    ON case_evidence_items (retention_expires_at)
    WHERE retention_expires_at IS NOT NULL AND NOT legal_hold
      AND state NOT IN ('DELETED_OR_CRYPTO_ERASED', 'DELETION_PENDING');
CREATE INDEX idx_case_evidence_items_legal_hold
    ON case_evidence_items (legal_hold_until) WHERE legal_hold;
--rollback DROP TABLE case_evidence_items;

--changeset ninggiangboy:028-21-evidence-transformations
-- Immutable lineage from an original to anything derived from it. A transcript, a converted image, a
-- translated document and an export are all things somebody may later dispute, so each names its
-- input, the tool and version that produced it, the operator, the instant and the output digest.
CREATE TABLE evidence_transformations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_evidence_id          UUID NOT NULL,
    derived_evidence_id         UUID NOT NULL,
    transformation_kind         VARCHAR(24) NOT NULL,

    tool_reference              VARCHAR(96) NOT NULL,
    tool_version                VARCHAR(32) NOT NULL,
    parameters_digest           CHAR(64),
    operator_actor_type         VARCHAR(16) NOT NULL,
    operator_account_holder_id  UUID,
    purpose                     VARCHAR(48) NOT NULL,

    performed_at                TIMESTAMPTZ NOT NULL,
    output_hash                 CHAR(64) NOT NULL,
    lossy                       BOOLEAN NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_evidence_transformations_source FOREIGN KEY (source_evidence_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT fk_evidence_transformations_derived FOREIGN KEY (derived_evidence_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT fk_evidence_transformations_operator FOREIGN KEY (operator_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_evidence_transformations_derived UNIQUE (derived_evidence_id),
    CONSTRAINT ck_evidence_transformations_kind CHECK (
        transformation_kind IN ('REDACTION', 'TRANSCRIPTION', 'TRANSLATION', 'FORMAT_CONVERSION',
                                'THUMBNAIL', 'METADATA_STRIP', 'EXPORT_PACKAGE', 'OCR')
    ),
    CONSTRAINT ck_evidence_transformations_operator_type CHECK (
        operator_actor_type IN ('AGENT', 'SUPERVISOR', 'SYSTEM', 'PROVIDER')
    ),
    CONSTRAINT ck_evidence_transformations_human CHECK (
        operator_actor_type IN ('SYSTEM', 'PROVIDER') OR operator_account_holder_id IS NOT NULL
    ),
    -- A derivative of itself is a lineage loop, and a lineage loop makes the original unfindable.
    CONSTRAINT ck_evidence_transformations_distinct CHECK (
        source_evidence_id <> derived_evidence_id
    )
);

CREATE INDEX idx_evidence_transformations_source
    ON evidence_transformations (source_evidence_id, performed_at DESC);
--rollback DROP TABLE evidence_transformations;

--changeset ninggiangboy:028-22-evidence-redactions
-- A redaction is a presentation derivative, never a destruction. The protected original stays where
-- it was; this row records what was hidden, on what ground, by whom, and whether anybody checked.
CREATE TABLE evidence_redactions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_evidence_id          UUID NOT NULL,
    derived_evidence_id         UUID NOT NULL,
    transformation_id           UUID NOT NULL,

    redaction_reason            VARCHAR(32) NOT NULL,
    legal_basis                 VARCHAR(48),
    redacted_regions            TEXT[] NOT NULL DEFAULT '{}',
    redacted_fields             TEXT[] NOT NULL DEFAULT '{}',
    operator_account_holder_id  UUID NOT NULL,
    performed_at                TIMESTAMPTZ NOT NULL,

    reviewed_by_account_holder_id UUID,
    reviewed_at                 TIMESTAMPTZ,
    review_outcome              VARCHAR(16),
    output_hash                 CHAR(64) NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_evidence_redactions_source FOREIGN KEY (source_evidence_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT fk_evidence_redactions_derived FOREIGN KEY (derived_evidence_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT fk_evidence_redactions_transformation FOREIGN KEY (transformation_id)
        REFERENCES evidence_transformations (id),
    CONSTRAINT fk_evidence_redactions_operator FOREIGN KEY (operator_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_evidence_redactions_reviewer FOREIGN KEY (reviewed_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_evidence_redactions_derived UNIQUE (derived_evidence_id),
    CONSTRAINT ck_evidence_redactions_reason CHECK (
        redaction_reason IN ('OTHER_PARTY_PERSONAL_DATA', 'IDENTITY_DOCUMENT', 'PAYMENT_DATA',
                             'ACCESS_SECRET', 'EXACT_ADDRESS', 'HEALTH_DATA', 'MINOR_PRESENT',
                             'LEGAL_PRIVILEGE', 'PROVIDER_MINIMIZATION')
    ),
    -- A redaction that hides nothing is not a redaction; something must be named as removed.
    CONSTRAINT ck_evidence_redactions_scope CHECK (
        cardinality(redacted_regions) > 0 OR cardinality(redacted_fields) > 0
    ),
    CONSTRAINT ck_evidence_redactions_review CHECK (
        (reviewed_at IS NULL) = (reviewed_by_account_holder_id IS NULL)
        AND (reviewed_at IS NULL) = (review_outcome IS NULL)
        AND (review_outcome IS NULL OR review_outcome IN ('CONFIRMED', 'INSUFFICIENT', 'EXCESSIVE'))
        AND (reviewed_by_account_holder_id IS NULL
             OR reviewed_by_account_holder_id <> operator_account_holder_id)
    ),
    CONSTRAINT ck_evidence_redactions_distinct CHECK (
        source_evidence_id <> derived_evidence_id
    )
);

CREATE INDEX idx_evidence_redactions_source ON evidence_redactions (source_evidence_id);
--rollback DROP TABLE evidence_redactions;

--changeset ninggiangboy:028-23-evidence-access-log
-- Who read which protected artifact, for what declared purpose, and under what authority. Being
-- assigned a case is not permission to browse private messages, exact addresses, identity documents
-- or medical detail; access is re-evaluated at read time and this row is the proof it was.
CREATE TABLE evidence_access_log (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_evidence_item_id       UUID NOT NULL,
    support_case_id             UUID NOT NULL,
    access_kind                 VARCHAR(16) NOT NULL,

    actor_type                  VARCHAR(16) NOT NULL,
    actor_account_holder_id     UUID,
    actor_role_code             VARCHAR(32),
    external_recipient_reference VARCHAR(128),

    declared_purpose            VARCHAR(48) NOT NULL,
    authority_policy_version_id UUID,
    agent_skill_grant_id        UUID,
    approval_reference          VARCHAR(96),
    break_glass                 BOOLEAN NOT NULL DEFAULT false,
    break_glass_reference       VARCHAR(96),
    post_use_review_required    BOOLEAN NOT NULL DEFAULT false,

    fields_accessed             TEXT[] NOT NULL DEFAULT '{}',
    outcome                     VARCHAR(16) NOT NULL,
    denial_reason               VARCHAR(48),
    accessed_at                 TIMESTAMPTZ NOT NULL,
    access_expires_at           TIMESTAMPTZ,
    session_reference           VARCHAR(96),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_evidence_access_log_item FOREIGN KEY (case_evidence_item_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT fk_evidence_access_log_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_evidence_access_log_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_evidence_access_log_authority FOREIGN KEY (authority_policy_version_id)
        REFERENCES authority_policy_versions (id),
    CONSTRAINT fk_evidence_access_log_grant FOREIGN KEY (agent_skill_grant_id)
        REFERENCES agent_skill_grants (id),
    CONSTRAINT ck_evidence_access_log_kind CHECK (
        access_kind IN ('VIEW', 'DOWNLOAD', 'EXPORT', 'DISCLOSE', 'TRANSFORM')
    ),
    CONSTRAINT ck_evidence_access_log_actor_type CHECK (
        actor_type IN ('AGENT', 'SUPERVISOR', 'SPECIALIST', 'ADJUSTER', 'LEGAL', 'PARTICIPANT',
                       'PROVIDER', 'AUTHORITY', 'SYSTEM')
    ),
    CONSTRAINT ck_evidence_access_log_outcome CHECK (
        outcome IN ('GRANTED', 'DENIED', 'EXPIRED')
        AND (outcome = 'DENIED') = (denial_reason IS NOT NULL)
    ),
    -- An export or a disclosure leaves the platform. It names who authorized it and, when it goes to
    -- someone outside, who received it.
    CONSTRAINT ck_evidence_access_log_export CHECK (
        access_kind NOT IN ('EXPORT', 'DISCLOSE') OR outcome <> 'GRANTED'
            OR approval_reference IS NOT NULL
    ),
    CONSTRAINT ck_evidence_access_log_external CHECK (
        actor_type NOT IN ('PROVIDER', 'AUTHORITY') OR external_recipient_reference IS NOT NULL
    ),
    CONSTRAINT ck_evidence_access_log_internal_actor CHECK (
        actor_type IN ('SYSTEM', 'PROVIDER', 'AUTHORITY') OR actor_account_holder_id IS NOT NULL
    ),
    -- Break-glass always earns a review afterwards. An emergency read nobody looks at again is just
    -- an unlogged privilege with extra steps.
    CONSTRAINT ck_evidence_access_log_break_glass CHECK (
        break_glass = (break_glass_reference IS NOT NULL)
        AND (NOT break_glass OR post_use_review_required)
    ),
    CONSTRAINT ck_evidence_access_log_expiry CHECK (
        access_expires_at IS NULL OR access_expires_at > accessed_at
    )
);

CREATE INDEX idx_evidence_access_log_item
    ON evidence_access_log (case_evidence_item_id, accessed_at DESC);
CREATE INDEX idx_evidence_access_log_actor
    ON evidence_access_log (actor_account_holder_id, accessed_at DESC);
CREATE INDEX idx_evidence_access_log_break_glass
    ON evidence_access_log (accessed_at DESC) WHERE break_glass;
--rollback DROP TABLE evidence_access_log;

--changeset ninggiangboy:028-24-evidence-disclosure-manifests
-- An allowlisted package, frozen before it leaves. Provider exports are not arbitrary case archives:
-- the manifest fixes exactly which artifacts and fields go, under which decision and legal basis,
-- to whom, until when, and with which digest -- so a later dispute about what was sent has an answer.
CREATE TABLE evidence_disclosure_manifests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    manifest_kind               VARCHAR(24) NOT NULL,
    manifest_version            INTEGER NOT NULL DEFAULT 1,

    recipient_kind              VARCHAR(24) NOT NULL,
    recipient_reference         VARCHAR(128) NOT NULL,
    provider_account_id         UUID,
    purpose                     VARCHAR(48) NOT NULL,
    legal_basis                 VARCHAR(48) NOT NULL,
    minimization_policy_key     VARCHAR(48) NOT NULL,

    evidence_item_ids           UUID[] NOT NULL,
    approved_fields             TEXT[] NOT NULL DEFAULT '{}',
    item_count                  INTEGER NOT NULL,
    manifest_digest             CHAR(64) NOT NULL,

    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    frozen_at                   TIMESTAMPTZ,
    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    disclosed_at                TIMESTAMPTZ,
    access_expires_at           TIMESTAMPTZ,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(48),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_evidence_disclosure_manifests_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_evidence_disclosure_manifests_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_evidence_disclosure_manifests_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_evidence_disclosure_manifests_version
        UNIQUE (support_case_id, manifest_kind, recipient_reference, manifest_version),
    CONSTRAINT ck_evidence_disclosure_manifests_kind CHECK (
        manifest_kind IN ('PROVIDER_DISPUTE', 'PROTECTION_CLAIM', 'PARTICIPANT_DISCLOSURE',
                          'REGULATOR_REQUEST', 'LEGAL_REQUEST', 'DATA_SUBJECT_REQUEST')
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_recipient CHECK (
        recipient_kind IN ('PAYMENT_PROVIDER', 'INSURER', 'ADJUSTER', 'PARTICIPANT', 'REGULATOR',
                           'LAW_ENFORCEMENT', 'LEGAL_COUNSEL')
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_state CHECK (
        state IN ('DRAFT', 'FROZEN', 'APPROVED', 'DISCLOSED', 'EXPIRED', 'REVOKED')
    ),
    -- Nothing leaves before it is frozen and approved, and what leaves must not be empty or
    -- mis-counted: the count is stored so a manifest cannot quietly gain an artifact after approval.
    CONSTRAINT ck_evidence_disclosure_manifests_freeze CHECK (
        state = 'DRAFT'
            OR (frozen_at IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_count CHECK (
        item_count = cardinality(evidence_item_ids) AND item_count > 0
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_disclosed CHECK (
        (state IN ('DISCLOSED', 'EXPIRED', 'REVOKED')) = (disclosed_at IS NOT NULL)
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_revocation CHECK (
        (state = 'REVOKED') = (revoked_at IS NOT NULL)
        AND (revoked_at IS NULL) = (revocation_reason IS NULL)
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_provider_link CHECK (
        recipient_kind <> 'PAYMENT_PROVIDER' OR provider_account_id IS NOT NULL
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_expiry CHECK (
        access_expires_at IS NULL OR disclosed_at IS NULL OR access_expires_at > disclosed_at
    ),
    CONSTRAINT ck_evidence_disclosure_manifests_number CHECK (manifest_version > 0),
    CONSTRAINT ck_evidence_disclosure_manifests_row_version CHECK (version >= 0)
);

CREATE INDEX idx_evidence_disclosure_manifests_case
    ON evidence_disclosure_manifests (support_case_id, state);
--rollback DROP TABLE evidence_disclosure_manifests;

--changeset ninggiangboy:028-25-case-assertions
-- What somebody said, attributed to them, kept as what it is. An assertion is not a finding and must
-- never be displayed as one: recording that a guest states the lock was broken is not recording that
-- the lock was broken. Keeping the two in separate tables is what makes that distinction survive
-- every screen, export and report built on top of them later.
CREATE TABLE case_assertions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    asserted_by_actor_type      VARCHAR(16) NOT NULL,
    asserted_by_account_holder_id UUID,
    asserted_by_external_reference VARCHAR(128),
    on_behalf_of_account_holder_id UUID,

    assertion_subject           VARCHAR(32) NOT NULL,
    statement_reference         VARCHAR(256) NOT NULL,
    statement_hash              CHAR(64) NOT NULL,
    locale                      VARCHAR(16) NOT NULL,
    source_contact_id           UUID,
    source_evidence_id          UUID,

    asserted_at                 TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,
    sensitivity_class           VARCHAR(24) NOT NULL DEFAULT 'ORDINARY',
    visibility_scope            VARCHAR(24) NOT NULL DEFAULT 'INTERNAL_ONLY',
    withdrawn_at                TIMESTAMPTZ,
    withdrawal_reason           VARCHAR(48),
    corrects_assertion_id       UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_assertions_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_assertions_asserter FOREIGN KEY (asserted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_assertions_behalf FOREIGN KEY (on_behalf_of_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_assertions_contact FOREIGN KEY (source_contact_id)
        REFERENCES case_contacts (id),
    CONSTRAINT fk_case_assertions_evidence FOREIGN KEY (source_evidence_id)
        REFERENCES case_evidence_items (id),
    CONSTRAINT fk_case_assertions_correction FOREIGN KEY (corrects_assertion_id)
        REFERENCES case_assertions (id),
    CONSTRAINT ck_case_assertions_actor_type CHECK (
        asserted_by_actor_type IN ('GUEST', 'HOST', 'CO_HOST', 'AGENT', 'THIRD_PARTY', 'PROVIDER',
                                   'AUTHORITY')
    ),
    CONSTRAINT ck_case_assertions_identity CHECK (
        (asserted_by_account_holder_id IS NOT NULL) <> (asserted_by_external_reference IS NOT NULL)
    ),
    CONSTRAINT ck_case_assertions_subject CHECK (
        assertion_subject IN ('OCCURRENCE', 'CONDITION_BEFORE_STAY', 'CONDITION_AFTER_STAY',
                              'CAUSATION', 'RESPONSIBILITY', 'VALUE', 'PRIOR_DISCLOSURE',
                              'ACCESS', 'COMMUNICATION', 'PAYMENT', 'IDENTITY', 'OTHER')
    ),
    CONSTRAINT ck_case_assertions_sensitivity CHECK (
        sensitivity_class IN ('ORDINARY', 'FINANCIAL', 'IDENTITY', 'SAFETY', 'HEALTH', 'LEGAL',
                              'VULNERABLE_PERSON', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_assertions_visibility CHECK (
        visibility_scope IN ('INTERNAL_ONLY', 'PARTICIPANT_SHARED', 'RESTRICTED_AUTHORITY')
    ),
    CONSTRAINT ck_case_assertions_withdrawal CHECK (
        (withdrawn_at IS NULL) = (withdrawal_reason IS NULL)
        AND (withdrawn_at IS NULL OR withdrawn_at >= asserted_at)
    ),
    CONSTRAINT ck_case_assertions_correction CHECK (
        corrects_assertion_id IS NULL OR corrects_assertion_id <> id
    ),
    CONSTRAINT ck_case_assertions_times CHECK (received_at >= asserted_at)
);

CREATE INDEX idx_case_assertions_case ON case_assertions (support_case_id, asserted_at DESC);
CREATE INDEX idx_case_assertions_claim ON case_assertions (damage_claim_id);
--rollback DROP TABLE case_assertions;

--changeset ninggiangboy:028-26-case-findings
-- An authorized interpretation, and the only kind of row a decision may rest on. A finding names the
-- question it answers, the proof standard it was held to, the evidence it cites and the evidence
-- that contradicts it. INCONCLUSIVE is a real answer and is never rounded towards whichever party
-- happened to file first, so an adverse decision may not cite one as its ground.
CREATE TABLE case_findings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    lifecycle_episode           SMALLINT NOT NULL,

    finding_question            VARCHAR(64) NOT NULL,
    subject_kind                VARCHAR(24) NOT NULL,
    subject_account_holder_id   UUID,
    subject_reference           VARCHAR(128),

    outcome                     VARCHAR(16) NOT NULL,
    proof_standard              VARCHAR(32) NOT NULL,
    confidence                  NUMERIC(4, 3),
    cited_evidence_ids          UUID[] NOT NULL DEFAULT '{}',
    cited_fragments             TEXT[] NOT NULL DEFAULT '{}',
    contradicting_evidence_ids  UUID[] NOT NULL DEFAULT '{}',
    unresolved_gaps             TEXT[] NOT NULL DEFAULT '{}',
    alternative_explanations    TEXT[] NOT NULL DEFAULT '{}',

    investigation_template_version_id UUID NOT NULL,
    finding_policy_reference    VARCHAR(96) NOT NULL,
    author_actor_type           VARCHAR(24) NOT NULL,
    author_account_holder_id    UUID,
    reviewed_by_account_holder_id UUID,
    reviewed_at                 TIMESTAMPTZ,

    reusable_scope              VARCHAR(24) NOT NULL DEFAULT 'THIS_CASE',
    concluded_at                TIMESTAMPTZ NOT NULL,
    superseded_by_finding_id    UUID,
    superseded_at               TIMESTAMPTZ,
    supersession_reason         VARCHAR(48),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_findings_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_findings_subject FOREIGN KEY (subject_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_findings_template FOREIGN KEY (investigation_template_version_id)
        REFERENCES investigation_template_versions (id),
    CONSTRAINT fk_case_findings_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_findings_reviewer FOREIGN KEY (reviewed_by_account_holder_id)
        REFERENCES account_holders (id),
    -- The outgoing finding names its replacement, and the replacement does not exist until the same
    -- transaction creates it. Deferring the check is what lets the pair commit together; an
    -- immediate check would make replacing a finding impossible without first having none.
    CONSTRAINT fk_case_findings_supersession FOREIGN KEY (superseded_by_finding_id)
        REFERENCES case_findings (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_case_findings_outcome CHECK (
        outcome IN ('SUPPORTED', 'NOT_SUPPORTED', 'INCONCLUSIVE', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_case_findings_subject_kind CHECK (
        subject_kind IN ('CASE', 'PARTY', 'BOOKING', 'LISTING', 'CLAIM_ITEM', 'PAYMENT',
                         'PROVIDER_DISPUTE', 'COVERAGE')
    ),
    CONSTRAINT ck_case_findings_proof CHECK (
        proof_standard IN ('BALANCE_OF_PROBABILITIES', 'CLEAR_AND_CONVINCING',
                           'DOCUMENTED_EVIDENCE_REQUIRED', 'PROVIDER_DETERMINATION')
    ),
    CONSTRAINT ck_case_findings_author_type CHECK (
        author_actor_type IN ('AGENT', 'SUPERVISOR', 'SPECIALIST', 'ADJUSTER', 'AUTOMATED_EVALUATOR')
    ),
    CONSTRAINT ck_case_findings_human CHECK (
        author_actor_type = 'AUTOMATED_EVALUATOR' OR author_account_holder_id IS NOT NULL
    ),
    -- A conclusion either way must cite something. A finding with no evidence is an opinion with a
    -- policy version attached, which is the shape of an unappealable decision.
    CONSTRAINT ck_case_findings_citation CHECK (
        outcome NOT IN ('SUPPORTED', 'NOT_SUPPORTED') OR cardinality(cited_evidence_ids) > 0
    ),
    -- Saying "we could not tell" is only honest if the gap is named.
    CONSTRAINT ck_case_findings_inconclusive CHECK (
        outcome <> 'INCONCLUSIVE' OR cardinality(unresolved_gaps) > 0
    ),
    CONSTRAINT ck_case_findings_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    ),
    CONSTRAINT ck_case_findings_reusable CHECK (
        reusable_scope IN ('THIS_CASE', 'THIS_CLAIM', 'THIS_BOOKING', 'THIS_PARTY')
    ),
    CONSTRAINT ck_case_findings_review CHECK (
        (reviewed_at IS NULL) = (reviewed_by_account_holder_id IS NULL)
        AND (reviewed_by_account_holder_id IS NULL OR author_account_holder_id IS NULL
             OR reviewed_by_account_holder_id <> author_account_holder_id)
    ),
    CONSTRAINT ck_case_findings_supersession CHECK (
        (superseded_by_finding_id IS NULL) = (supersession_reason IS NULL)
        AND (superseded_by_finding_id IS NULL) = (superseded_at IS NULL)
        AND (superseded_by_finding_id IS NULL OR superseded_by_finding_id <> id)
    ),
    CONSTRAINT ck_case_findings_episode CHECK (lifecycle_episode > 0)
);

-- One live answer to a question per case and subject. A second live finding on the same question is
-- two different answers with equal standing, and a decision would have to pick one arbitrarily.
CREATE UNIQUE INDEX uk_case_findings_live
    ON case_findings (support_case_id, finding_question, subject_kind, subject_account_holder_id,
                      subject_reference, damage_claim_id)
    NULLS NOT DISTINCT
    WHERE superseded_by_finding_id IS NULL;
CREATE INDEX idx_case_findings_case ON case_findings (support_case_id, concluded_at DESC);
CREATE INDEX idx_case_findings_claim ON case_findings (damage_claim_id);
--rollback DROP TABLE case_findings;

--changeset ninggiangboy:028-27-damage-claims
-- A claim against a specific accepted booking, by a specific claimant, about a specific interval.
-- Its state is its own: a claim can be decided while payment is pending, recovery is unfinished and
-- an appeal is open, and none of those may overwrite the others. Late or incomplete claims are
-- recorded with the reason they were late rather than discarded, because an ineligible claim a
-- person can see explained is very different from one that silently vanished.
CREATE TABLE damage_claims (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    claim_reference             VARCHAR(32) NOT NULL,
    claimant_account_holder_id  UUID NOT NULL,
    respondent_account_holder_id UUID,

    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID,
    operational_stay_id         UUID,
    incident_id                 UUID,
    listing_id                  UUID,

    occurrence_from             TIMESTAMPTZ NOT NULL,
    occurrence_until            TIMESTAMPTZ,
    discovered_at               TIMESTAMPTZ NOT NULL,
    submitted_at                TIMESTAMPTZ NOT NULL,
    claim_window_deadline_at    TIMESTAMPTZ,
    respondent_deadline_at      TIMESTAMPTZ,

    requested_currency          VARCHAR(3) NOT NULL,
    requested_total_minor       BIGINT NOT NULL,
    accepted_total_minor        BIGINT,
    deductible_minor            BIGINT NOT NULL DEFAULT 0,
    prior_recovery_minor        BIGINT NOT NULL DEFAULT 0,

    eligibility_state           VARCHAR(16) NOT NULL DEFAULT 'UNASSESSED',
    ineligibility_reason        VARCHAR(48),
    late_submission             BOOLEAN NOT NULL DEFAULT false,
    late_submission_reason      VARCHAR(48),
    exception_review_reference  VARCHAR(96),
    protection_program_version_id UUID,
    coverage_snapshot_id        UUID,

    state                       VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    lifecycle_episode           SMALLINT NOT NULL DEFAULT 1,
    responsibility_outcome      VARCHAR(24) NOT NULL DEFAULT 'UNDETERMINED',
    valuation_policy_reference  VARCHAR(96),
    decided_at                  TIMESTAMPTZ,
    case_decision_id            UUID,
    settled_at                  TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,

    deposit_reference           VARCHAR(96),
    payout_hold_id              UUID,
    appeal_state                VARCHAR(24) NOT NULL DEFAULT 'NONE',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_damage_claims_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_damage_claims_claimant FOREIGN KEY (claimant_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_damage_claims_respondent FOREIGN KEY (respondent_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_damage_claims_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_damage_claims_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_damage_claims_stay FOREIGN KEY (operational_stay_id)
        REFERENCES operational_stays (id),
    CONSTRAINT fk_damage_claims_incident FOREIGN KEY (incident_id) REFERENCES incidents (id),
    CONSTRAINT fk_damage_claims_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_damage_claims_program FOREIGN KEY (protection_program_version_id)
        REFERENCES protection_program_versions (id),
    CONSTRAINT fk_damage_claims_payout_hold FOREIGN KEY (payout_hold_id)
        REFERENCES payout_holds (id),
    CONSTRAINT uk_damage_claims_reference UNIQUE (claim_reference),
    CONSTRAINT ck_damage_claims_state CHECK (
        state IN ('DRAFT', 'SUBMITTED', 'ELIGIBILITY_REVIEW', 'INELIGIBLE', 'EVIDENCE_COLLECTION',
                  'RESPONDENT_REVIEW', 'ADJUDICATION', 'DECIDED', 'PAYMENT_PENDING',
                  'RECOVERY_PENDING', 'APPEALED', 'SETTLED', 'CLOSED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_damage_claims_eligibility CHECK (
        eligibility_state IN ('UNASSESSED', 'ELIGIBLE', 'INELIGIBLE', 'REFERRED')
        AND (eligibility_state = 'INELIGIBLE') = (ineligibility_reason IS NOT NULL)
    ),
    CONSTRAINT ck_damage_claims_ineligible_state CHECK (
        state <> 'INELIGIBLE' OR eligibility_state = 'INELIGIBLE'
    ),
    CONSTRAINT ck_damage_claims_responsibility CHECK (
        responsibility_outcome IN ('UNDETERMINED', 'GUEST_OR_VISITOR', 'HOST_MAINTENANCE',
                                   'PRE_EXISTING', 'ORDINARY_WEAR', 'THIRD_PARTY', 'SHARED',
                                   'INSUFFICIENT_EVIDENCE', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_damage_claims_appeal_state CHECK (
        appeal_state IN ('NONE', 'ELIGIBLE', 'SUBMITTED', 'REVIEWING', 'DECIDED', 'EXHAUSTED')
    ),
    -- Money is integer minor units and a three-letter code, and nothing may be accepted beyond what
    -- was asked for. A claim that awards more than it claimed is not a generous decision, it is a bug.
    CONSTRAINT ck_damage_claims_amounts CHECK (
        requested_currency ~ '^[A-Z]{3}$'
        AND requested_total_minor > 0
        AND (accepted_total_minor IS NULL
             OR (accepted_total_minor >= 0 AND accepted_total_minor <= requested_total_minor))
        AND deductible_minor >= 0 AND prior_recovery_minor >= 0
    ),
    -- A late claim says why it was late. "Late" with no reason is indistinguishable from a claim
    -- that was quietly dropped for being inconvenient.
    CONSTRAINT ck_damage_claims_late CHECK (
        late_submission = (late_submission_reason IS NOT NULL)
    ),
    -- A decided claim has an amount and the decision that fixed it; neither alone is a decision.
    CONSTRAINT ck_damage_claims_decided CHECK (
        state NOT IN ('DECIDED', 'PAYMENT_PENDING', 'RECOVERY_PENDING', 'SETTLED')
            OR (decided_at IS NOT NULL AND accepted_total_minor IS NOT NULL
                AND case_decision_id IS NOT NULL)
    ),
    -- Deciding against a claimant requires the responsibility question to have been answered one way
    -- or the other; "undetermined" is not a ground for taking somebody's money.
    CONSTRAINT ck_damage_claims_responsibility_required CHECK (
        state NOT IN ('DECIDED', 'PAYMENT_PENDING', 'RECOVERY_PENDING', 'SETTLED')
            OR accepted_total_minor = 0
            OR responsibility_outcome NOT IN ('UNDETERMINED', 'INSUFFICIENT_EVIDENCE')
    ),
    CONSTRAINT ck_damage_claims_settled CHECK (
        (state = 'SETTLED') = (settled_at IS NOT NULL)
    ),
    CONSTRAINT ck_damage_claims_closed CHECK (
        (state = 'CLOSED') = (closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_damage_claims_parties CHECK (
        respondent_account_holder_id IS NULL
            OR respondent_account_holder_id <> claimant_account_holder_id
    ),
    CONSTRAINT ck_damage_claims_times CHECK (
        (occurrence_until IS NULL OR occurrence_until >= occurrence_from)
        AND discovered_at >= occurrence_from
        AND submitted_at >= discovered_at
        AND (decided_at IS NULL OR decided_at >= submitted_at)
        AND (respondent_deadline_at IS NULL OR respondent_deadline_at > submitted_at)
    ),
    CONSTRAINT ck_damage_claims_episode CHECK (lifecycle_episode > 0),
    CONSTRAINT ck_damage_claims_row_version CHECK (version >= 0)
);

-- One live claim per claimant, booking and occurrence start. A second is either the same loss filed
-- twice or a distinct occurrence, and the occurrence instant is what separates them.
CREATE UNIQUE INDEX uk_damage_claims_live
    ON damage_claims (booking_id, claimant_account_holder_id, occurrence_from)
    WHERE state NOT IN ('CLOSED', 'WITHDRAWN', 'INELIGIBLE');
CREATE INDEX idx_damage_claims_case ON damage_claims (support_case_id);
CREATE INDEX idx_damage_claims_state_deadline
    ON damage_claims (state, respondent_deadline_at)
    WHERE state NOT IN ('CLOSED', 'WITHDRAWN', 'SETTLED');
CREATE INDEX idx_damage_claims_respondent
    ON damage_claims (respondent_account_holder_id, state);

-- The claim-scoped columns declared earlier can only gain their foreign keys now that the table they
-- point at exists. Declaring them late does not make them optional.
ALTER TABLE case_work_items
    ADD CONSTRAINT fk_case_work_items_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id);
ALTER TABLE case_evidence_items
    ADD CONSTRAINT fk_case_evidence_items_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id);
ALTER TABLE evidence_disclosure_manifests
    ADD CONSTRAINT fk_evidence_disclosure_manifests_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id);
ALTER TABLE case_assertions
    ADD CONSTRAINT fk_case_assertions_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id);
ALTER TABLE case_findings
    ADD CONSTRAINT fk_case_findings_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id);
--rollback ALTER TABLE case_findings DROP CONSTRAINT fk_case_findings_claim;
--rollback ALTER TABLE case_assertions DROP CONSTRAINT fk_case_assertions_claim;
--rollback ALTER TABLE evidence_disclosure_manifests DROP CONSTRAINT fk_evidence_disclosure_manifests_claim;
--rollback ALTER TABLE case_evidence_items DROP CONSTRAINT fk_case_evidence_items_claim;
--rollback ALTER TABLE case_work_items DROP CONSTRAINT fk_case_work_items_claim;
--rollback DROP TABLE damage_claims;

--changeset ninggiangboy:028-28-damage-claim-items
-- One claimed loss, valued by a named method. Estimates, invoices and receipts prove different
-- things, so the document kind that supports a line is recorded beside the amount it supports: a
-- submitted estimate is not proof of a cost anybody paid. Accepted and rejected amounts are stored
-- separately with an adjustment reason, because "we gave you less" needs to be explainable per line.
CREATE TABLE damage_claim_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    damage_claim_id             UUID NOT NULL,
    line_number                 SMALLINT NOT NULL,
    category                    VARCHAR(32) NOT NULL,
    description_reference       VARCHAR(256) NOT NULL,

    ownership_relation          VARCHAR(24) NOT NULL,
    condition_before            VARCHAR(24) NOT NULL,
    age_months                  INTEGER,
    alleged_loss_kind           VARCHAR(24) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    requested_amount_minor      BIGINT NOT NULL,
    original_cost_minor         BIGINT,
    repair_estimate_minor       BIGINT,
    replacement_cost_minor      BIGINT,
    salvage_value_minor         BIGINT NOT NULL DEFAULT 0,

    valuation_method            VARCHAR(32),
    valuation_method_version    INTEGER,
    depreciation_rule           VARCHAR(32),
    depreciation_amount_minor   BIGINT NOT NULL DEFAULT 0,
    deductible_share_minor      BIGINT NOT NULL DEFAULT 0,
    category_cap_minor          BIGINT,

    accepted_amount_minor       BIGINT,
    rejected_amount_minor       BIGINT,
    adjustment_reason           VARCHAR(48),
    uncertainty_note_reference  VARCHAR(256),
    supporting_document_kind    VARCHAR(24),
    supporting_evidence_ids     UUID[] NOT NULL DEFAULT '{}',
    case_finding_id             UUID,
    prior_recovery_reference    VARCHAR(96),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_damage_claim_items_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id),
    CONSTRAINT fk_damage_claim_items_finding FOREIGN KEY (case_finding_id)
        REFERENCES case_findings (id),
    CONSTRAINT uk_damage_claim_items_line UNIQUE (damage_claim_id, line_number),
    CONSTRAINT ck_damage_claim_items_category CHECK (
        category IN ('FURNITURE', 'APPLIANCE', 'ELECTRONICS', 'LINEN', 'DECOR', 'FLOORING',
                     'WALL_OR_CEILING', 'FIXTURE', 'STRUCTURE', 'OUTDOOR', 'CLEANING',
                     'LOST_INCOME', 'REPLACEMENT_STAY', 'OTHER')
    ),
    CONSTRAINT ck_damage_claim_items_ownership CHECK (
        ownership_relation IN ('LISTING_PROPERTY', 'HOST_PERSONAL', 'GUEST_PERSONAL',
                               'THIRD_PARTY', 'SHARED_BUILDING')
    ),
    CONSTRAINT ck_damage_claim_items_condition CHECK (
        condition_before IN ('NEW', 'GOOD', 'FAIR', 'WORN', 'ALREADY_DAMAGED', 'UNKNOWN')
    ),
    CONSTRAINT ck_damage_claim_items_loss_kind CHECK (
        alleged_loss_kind IN ('DAMAGE', 'THEFT', 'LOSS', 'SOILING', 'DESTRUCTION',
                              'EXCESSIVE_CLEANING', 'UNAUTHORIZED_USE')
    ),
    CONSTRAINT ck_damage_claim_items_valuation_method CHECK (
        valuation_method IS NULL
            OR valuation_method IN ('REASONABLE_REPAIR_COST', 'LIKE_KIND_LESS_DEPRECIATION',
                                    'ACTUAL_CASH_VALUE', 'CAPPED_CLEANING', 'DOCUMENTED_INVOICE',
                                    'PROVIDER_DETERMINATION')
    ),
    CONSTRAINT ck_damage_claim_items_document_kind CHECK (
        supporting_document_kind IS NULL
            OR supporting_document_kind IN ('RECEIPT', 'INVOICE', 'ESTIMATE', 'PHOTO_ONLY',
                                            'INSPECTION_REPORT', 'NONE')
    ),
    -- A method with no version is not reproducible, and a depreciated amount with no rule is a
    -- number somebody chose.
    CONSTRAINT ck_damage_claim_items_method_version CHECK (
        (valuation_method IS NULL) = (valuation_method_version IS NULL)
        AND (valuation_method_version IS NULL OR valuation_method_version > 0)
        AND (depreciation_amount_minor = 0 OR depreciation_rule IS NOT NULL)
    ),
    CONSTRAINT ck_damage_claim_items_amounts CHECK (
        currency ~ '^[A-Z]{3}$'
        AND requested_amount_minor > 0
        AND salvage_value_minor >= 0 AND depreciation_amount_minor >= 0
        AND deductible_share_minor >= 0
        AND (original_cost_minor IS NULL OR original_cost_minor >= 0)
        AND (repair_estimate_minor IS NULL OR repair_estimate_minor >= 0)
        AND (replacement_cost_minor IS NULL OR replacement_cost_minor >= 0)
        AND (category_cap_minor IS NULL OR category_cap_minor > 0)
    ),
    -- Whatever was accepted and whatever was rejected must together be exactly what was claimed, and
    -- the shortfall must be explained. Otherwise a line can quietly lose money to rounding.
    CONSTRAINT ck_damage_claim_items_split CHECK (
        (accepted_amount_minor IS NULL) = (rejected_amount_minor IS NULL)
        AND (accepted_amount_minor IS NULL
             OR (accepted_amount_minor >= 0 AND rejected_amount_minor >= 0
                 AND accepted_amount_minor + rejected_amount_minor = requested_amount_minor))
        AND (rejected_amount_minor IS NULL OR rejected_amount_minor = 0
             OR adjustment_reason IS NOT NULL)
    ),
    -- An accepted amount must respect the category cap it was measured against.
    CONSTRAINT ck_damage_claim_items_cap CHECK (
        accepted_amount_minor IS NULL OR category_cap_minor IS NULL
            OR accepted_amount_minor <= category_cap_minor
    ),
    -- Paying for a replacement means the document that proves a cost, not one that estimates it.
    CONSTRAINT ck_damage_claim_items_document_required CHECK (
        accepted_amount_minor IS NULL OR accepted_amount_minor = 0
            OR valuation_method <> 'DOCUMENTED_INVOICE'
            OR supporting_document_kind IN ('RECEIPT', 'INVOICE')
    ),
    CONSTRAINT ck_damage_claim_items_line_number CHECK (line_number > 0),
    CONSTRAINT ck_damage_claim_items_age CHECK (age_months IS NULL OR age_months >= 0),
    CONSTRAINT ck_damage_claim_items_row_version CHECK (version >= 0)
);

CREATE INDEX idx_damage_claim_items_claim ON damage_claim_items (damage_claim_id, line_number);
--rollback DROP TABLE damage_claim_items;

--changeset ninggiangboy:028-29-case-offers
-- A bounded, structured proposal between two parties. Acceptance is an explicit signed-in command
-- against an exact offer version and digest -- silence, a read receipt, an agent's note and a
-- friendly free-text reply are none of them acceptance. The digest is what makes that enforceable:
-- if the terms changed after the offer was sent, the acceptance no longer matches what was accepted.
CREATE TABLE case_offers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    offer_number                SMALLINT NOT NULL,
    offer_version               INTEGER NOT NULL DEFAULT 1,

    proposer_kind               VARCHAR(16) NOT NULL,
    proposer_account_holder_id  UUID,
    recipient_account_holder_id UUID NOT NULL,
    counters_offer_id           UUID,

    currency                    VARCHAR(3) NOT NULL,
    total_amount_minor          BIGINT NOT NULL,
    funding_assumption          VARCHAR(24) NOT NULL,
    non_financial_terms_reference VARCHAR(256),
    policy_reference            VARCHAR(96),
    evidence_item_ids           UUID[] NOT NULL DEFAULT '{}',
    confidentiality_terms_key   VARCHAR(64),

    content_digest              CHAR(64) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    sent_at                     TIMESTAMPTZ,
    viewed_at                   TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ,
    responded_at                TIMESTAMPTZ,
    rejection_reason            VARCHAR(48),
    withdrawal_reason           VARCHAR(48),

    accepted_by_account_holder_id UUID,
    accepted_digest             CHAR(64),
    acceptance_authentication_reference VARCHAR(96),
    accepted_offer_version      INTEGER,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_offers_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_offers_claim FOREIGN KEY (damage_claim_id) REFERENCES damage_claims (id),
    CONSTRAINT fk_case_offers_proposer FOREIGN KEY (proposer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_offers_recipient FOREIGN KEY (recipient_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_offers_counter FOREIGN KEY (counters_offer_id) REFERENCES case_offers (id),
    CONSTRAINT fk_case_offers_acceptor FOREIGN KEY (accepted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_case_offers_number UNIQUE (support_case_id, offer_number, offer_version),
    CONSTRAINT ck_case_offers_proposer_kind CHECK (
        proposer_kind IN ('GUEST', 'HOST', 'PLATFORM', 'INSURER')
    ),
    CONSTRAINT ck_case_offers_proposer_identity CHECK (
        (proposer_kind IN ('GUEST', 'HOST')) = (proposer_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_case_offers_state CHECK (
        state IN ('DRAFT', 'SENT', 'VIEWED', 'ACCEPTED', 'REJECTED', 'COUNTERED', 'EXPIRED',
                  'WITHDRAWN')
    ),
    CONSTRAINT ck_case_offers_funding CHECK (
        funding_assumption IN ('PLATFORM', 'HOST', 'GUEST', 'INSURER', 'PARTNER', 'MIXED')
    ),
    CONSTRAINT ck_case_offers_amount CHECK (
        currency ~ '^[A-Z]{3}$' AND total_amount_minor >= 0
    ),
    -- An offer only exists for the other side once it has been sent, and a sent offer always has an
    -- expiry. An offer with no deadline is a liability that never closes.
    CONSTRAINT ck_case_offers_sent CHECK (
        (state = 'DRAFT') = (sent_at IS NULL)
        AND (sent_at IS NULL OR expires_at > sent_at)
        AND (state = 'DRAFT' OR expires_at IS NOT NULL)
    ),
    -- Acceptance names the person, the exact digest they saw, the version they saw it at, and the
    -- authentication behind the command. Anything less is agreement inferred from behaviour.
    CONSTRAINT ck_case_offers_acceptance CHECK (
        (state = 'ACCEPTED')
            = (accepted_by_account_holder_id IS NOT NULL AND accepted_digest IS NOT NULL
               AND acceptance_authentication_reference IS NOT NULL
               AND accepted_offer_version IS NOT NULL)
    ),
    -- The digest accepted is the digest offered. If the terms moved, the acceptance is of something
    -- that no longer exists and must be refused rather than reinterpreted.
    CONSTRAINT ck_case_offers_digest_match CHECK (
        accepted_digest IS NULL
            OR (accepted_digest = content_digest AND accepted_offer_version = offer_version)
    ),
    -- Only the party the offer was made to can accept it.
    CONSTRAINT ck_case_offers_acceptor CHECK (
        accepted_by_account_holder_id IS NULL
            OR accepted_by_account_holder_id = recipient_account_holder_id
    ),
    CONSTRAINT ck_case_offers_response CHECK (
        (state IN ('ACCEPTED', 'REJECTED', 'COUNTERED', 'WITHDRAWN')) = (responded_at IS NOT NULL)
        AND (state = 'REJECTED') = (rejection_reason IS NOT NULL)
        AND (state = 'WITHDRAWN') = (withdrawal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_case_offers_parties CHECK (
        proposer_account_holder_id IS NULL
            OR proposer_account_holder_id <> recipient_account_holder_id
    ),
    CONSTRAINT ck_case_offers_counter CHECK (
        counters_offer_id IS NULL OR counters_offer_id <> id
    ),
    CONSTRAINT ck_case_offers_times CHECK (
        (viewed_at IS NULL OR sent_at IS NOT NULL)
        AND (viewed_at IS NULL OR viewed_at >= sent_at)
        AND (responded_at IS NULL OR sent_at IS NULL OR responded_at >= sent_at)
    ),
    CONSTRAINT ck_case_offers_numbers CHECK (offer_number > 0 AND offer_version > 0),
    CONSTRAINT ck_case_offers_row_version CHECK (version >= 0)
);

-- At most one live offer per case thread. A second open offer to the same person on the same case is
-- two contradictory promises, and whichever one is accepted the other becomes a dispute of its own.
CREATE UNIQUE INDEX uk_case_offers_live
    ON case_offers (support_case_id, offer_number)
    WHERE state IN ('DRAFT', 'SENT', 'VIEWED');
-- One accepted offer per case. Two accepted offers is two settlements of one dispute.
CREATE UNIQUE INDEX uk_case_offers_accepted
    ON case_offers (support_case_id) WHERE state = 'ACCEPTED';
CREATE INDEX idx_case_offers_expiry
    ON case_offers (expires_at) WHERE state IN ('SENT', 'VIEWED');
CREATE INDEX idx_case_offers_claim ON case_offers (damage_claim_id);
--rollback DROP TABLE case_offers;

--changeset ninggiangboy:028-30-case-offer-lines
-- The exact terms of an offer, one line each. The lines are what a decision later turns into remedy
-- lines, so they carry the same shape: a remedy code, a beneficiary, a funder and an amount that
-- something can be checked against.
CREATE TABLE case_offer_lines (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_offer_id               UUID NOT NULL,
    line_number                 SMALLINT NOT NULL,
    remedy_code                 VARCHAR(48) NOT NULL,
    remedy_catalog_version_id   UUID,

    beneficiary_kind            VARCHAR(16) NOT NULL,
    beneficiary_account_holder_id UUID,
    funder_kind                 VARCHAR(16) NOT NULL,
    funder_account_holder_id    UUID,

    currency                    VARCHAR(3) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    source_reference            VARCHAR(96),
    non_financial_term_reference VARCHAR(256),
    explanation_template_key    VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_offer_lines_offer FOREIGN KEY (case_offer_id) REFERENCES case_offers (id),
    CONSTRAINT fk_case_offer_lines_catalog FOREIGN KEY (remedy_catalog_version_id)
        REFERENCES remedy_catalog_versions (id),
    CONSTRAINT fk_case_offer_lines_beneficiary FOREIGN KEY (beneficiary_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_offer_lines_funder FOREIGN KEY (funder_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_case_offer_lines_number UNIQUE (case_offer_id, line_number),
    CONSTRAINT ck_case_offer_lines_beneficiary_kind CHECK (
        beneficiary_kind IN ('GUEST', 'HOST', 'PLATFORM', 'THIRD_PARTY')
    ),
    CONSTRAINT ck_case_offer_lines_funder_kind CHECK (
        funder_kind IN ('PLATFORM', 'HOST', 'GUEST', 'INSURER', 'PARTNER')
    ),
    CONSTRAINT ck_case_offer_lines_party_identity CHECK (
        (beneficiary_kind IN ('GUEST', 'HOST')) = (beneficiary_account_holder_id IS NOT NULL)
        AND (funder_kind IN ('HOST', 'GUEST')) = (funder_account_holder_id IS NOT NULL)
    ),
    -- Nobody funds a payment to themselves. A line where the funder and the beneficiary are the same
    -- account moves no money and hides whatever was actually meant.
    CONSTRAINT ck_case_offer_lines_distinct_parties CHECK (
        beneficiary_account_holder_id IS NULL OR funder_account_holder_id IS NULL
            OR beneficiary_account_holder_id <> funder_account_holder_id
    ),
    CONSTRAINT ck_case_offer_lines_amount CHECK (
        currency ~ '^[A-Z]{3}$' AND amount_minor >= 0
    ),
    CONSTRAINT ck_case_offer_lines_number_positive CHECK (line_number > 0)
);

CREATE INDEX idx_case_offer_lines_offer ON case_offer_lines (case_offer_id, line_number);
--rollback DROP TABLE case_offer_lines;

--changeset ninggiangboy:028-31-coverage-snapshots
-- The coverage that applied at the qualifying moment, frozen there. Applying today's programme to a
-- historical booking is the single most tempting mistake in this area and the one that produces
-- promises the carrier never made, so the terms, limits, exclusions and disclosure version are
-- captured against the booking and never recomputed.
CREATE TABLE coverage_snapshots (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protection_program_version_id UUID NOT NULL,
    booking_id                  UUID NOT NULL,
    support_case_id             UUID,
    damage_claim_id             UUID,
    covered_account_holder_id   UUID NOT NULL,

    snapshot_basis              VARCHAR(24) NOT NULL,
    snapshot_at                 TIMESTAMPTZ NOT NULL,
    coverage_from               TIMESTAMPTZ NOT NULL,
    coverage_until              TIMESTAMPTZ NOT NULL,
    coverage_territory          VARCHAR(64) NOT NULL,

    limit_amount_minor          BIGINT NOT NULL,
    limit_currency              VARCHAR(3) NOT NULL,
    deductible_amount_minor     BIGINT NOT NULL DEFAULT 0,
    per_item_limit_minor        BIGINT,
    consumed_amount_minor       BIGINT NOT NULL DEFAULT 0,
    exclusions_reference        VARCHAR(128) NOT NULL,

    determination               VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    determination_authority     VARCHAR(24) NOT NULL,
    cited_terms_reference       VARCHAR(128),
    cited_exclusion_codes       TEXT[] NOT NULL DEFAULT '{}',
    determined_by_account_holder_id UUID,
    determined_at               TIMESTAMPTZ,
    explanation_template_key    VARCHAR(64),
    appeal_route                VARCHAR(64),
    determination_expires_at    TIMESTAMPTZ,

    consent_reference           VARCHAR(128),
    disclosure_version          VARCHAR(32) NOT NULL,
    document_reference          VARCHAR(256),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_coverage_snapshots_program FOREIGN KEY (protection_program_version_id)
        REFERENCES protection_program_versions (id),
    CONSTRAINT fk_coverage_snapshots_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_coverage_snapshots_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_coverage_snapshots_claim FOREIGN KEY (damage_claim_id)
        REFERENCES damage_claims (id),
    CONSTRAINT fk_coverage_snapshots_covered FOREIGN KEY (covered_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_coverage_snapshots_determiner FOREIGN KEY (determined_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_coverage_snapshots_basis CHECK (
        snapshot_basis IN ('BOOKING_ACCEPTED', 'STAY_START', 'CLAIM_OCCURRENCE', 'QUALIFYING_EVENT')
    ),
    CONSTRAINT ck_coverage_snapshots_determination CHECK (
        determination IN ('ELIGIBLE', 'INELIGIBLE', 'REFERRED', 'UNKNOWN')
    ),
    CONSTRAINT ck_coverage_snapshots_authority CHECK (
        determination_authority IN ('PLATFORM_DETERMINISTIC', 'PLATFORM_WITH_APPROVAL',
                                    'PROVIDER_ADMINISTRATOR', 'CARRIER')
    ),
    -- A determination either way cites the terms it rests on and says where a person can contest it.
    -- Denying coverage without naming an exclusion is the definition of an unappealable refusal.
    CONSTRAINT ck_coverage_snapshots_cited CHECK (
        determination NOT IN ('ELIGIBLE', 'INELIGIBLE')
            OR (cited_terms_reference IS NOT NULL AND determined_at IS NOT NULL
                AND appeal_route IS NOT NULL)
    ),
    CONSTRAINT ck_coverage_snapshots_exclusion CHECK (
        determination <> 'INELIGIBLE' OR cardinality(cited_exclusion_codes) > 0
    ),
    -- A platform-approved determination names the human who approved it; a carrier's does not,
    -- because the carrier is not one of our account holders.
    CONSTRAINT ck_coverage_snapshots_approver CHECK (
        determination_authority <> 'PLATFORM_WITH_APPROVAL'
            OR determination NOT IN ('ELIGIBLE', 'INELIGIBLE')
            OR determined_by_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_coverage_snapshots_limits CHECK (
        limit_amount_minor > 0 AND limit_currency ~ '^[A-Z]{3}$'
        AND deductible_amount_minor >= 0
        AND consumed_amount_minor >= 0 AND consumed_amount_minor <= limit_amount_minor
        AND (per_item_limit_minor IS NULL
             OR (per_item_limit_minor > 0 AND per_item_limit_minor <= limit_amount_minor))
    ),
    CONSTRAINT ck_coverage_snapshots_period CHECK (coverage_until > coverage_from),
    CONSTRAINT ck_coverage_snapshots_expiry CHECK (
        determination_expires_at IS NULL OR determined_at IS NULL
            OR determination_expires_at > determined_at
    ),
    CONSTRAINT ck_coverage_snapshots_row_version CHECK (version >= 0)
);

-- One snapshot per programme, booking and covered party. A second would let two different sets of
-- terms both claim to be the ones that applied.
CREATE UNIQUE INDEX uk_coverage_snapshots_identity
    ON coverage_snapshots (protection_program_version_id, booking_id, covered_account_holder_id,
                           snapshot_basis);
CREATE INDEX idx_coverage_snapshots_claim ON coverage_snapshots (damage_claim_id);

ALTER TABLE damage_claims
    ADD CONSTRAINT fk_damage_claims_coverage FOREIGN KEY (coverage_snapshot_id)
        REFERENCES coverage_snapshots (id);
--rollback ALTER TABLE damage_claims DROP CONSTRAINT fk_damage_claims_coverage;
--rollback DROP TABLE coverage_snapshots;

--changeset ninggiangboy:028-32-external-claims
-- The platform's side of a claim held by somebody else. The provider key is stable and is written
-- before the first call, so a timeout is answered by querying that key rather than by creating a
-- second claim to make a screen move. Provider acceptance is evidence of a decision, not proof that
-- money arrived; payment and reconciliation are separate states for exactly that reason.
CREATE TABLE external_claims (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    protection_program_version_id UUID NOT NULL,
    coverage_snapshot_id        UUID NOT NULL,
    provider_account_id         UUID NOT NULL,

    provider_claim_key          VARCHAR(128) NOT NULL,
    provider_claim_reference    VARCHAR(128),
    state                       VARCHAR(24) NOT NULL DEFAULT 'LOCAL_APPROVED',
    currency                    VARCHAR(3) NOT NULL,
    requested_amount_minor      BIGINT NOT NULL,
    approved_amount_minor       BIGINT,
    paid_amount_minor           BIGINT NOT NULL DEFAULT 0,

    local_approval_decision_id  UUID,
    submission_deadline_at      TIMESTAMPTZ,
    next_query_at               TIMESTAMPTZ,
    last_observed_at            TIMESTAMPTZ,
    observation_sequence        BIGINT NOT NULL DEFAULT 0,

    denial_reason_code          VARCHAR(64),
    information_request_reference VARCHAR(128),
    appeal_or_complaint_reference VARCHAR(128),
    ledger_transaction_id       UUID,
    reconciled_at               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_external_claims_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_external_claims_claim FOREIGN KEY (damage_claim_id) REFERENCES damage_claims (id),
    CONSTRAINT fk_external_claims_program FOREIGN KEY (protection_program_version_id)
        REFERENCES protection_program_versions (id),
    CONSTRAINT fk_external_claims_coverage FOREIGN KEY (coverage_snapshot_id)
        REFERENCES coverage_snapshots (id),
    CONSTRAINT fk_external_claims_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_external_claims_ledger FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    -- The stable key is unique per provider account. This is the single defence against a retry
    -- after a timeout becoming a second claim the carrier will happily open.
    CONSTRAINT uk_external_claims_provider_key UNIQUE (provider_account_id, provider_claim_key),
    CONSTRAINT ck_external_claims_state CHECK (
        state IN ('LOCAL_APPROVED', 'SUBMISSION_QUEUED', 'SUBMITTING', 'SUBMITTED', 'UNKNOWN',
                  'PROVIDER_REVIEW', 'INFO_REQUIRED', 'APPROVED', 'PARTIAL', 'DENIED',
                  'PAYMENT_PENDING', 'PAID', 'RECONCILED', 'APPEAL_OR_COMPLAINT', 'WITHDRAWN')
    ),
    CONSTRAINT ck_external_claims_amounts CHECK (
        currency ~ '^[A-Z]{3}$'
        AND requested_amount_minor > 0
        AND (approved_amount_minor IS NULL
             OR (approved_amount_minor >= 0 AND approved_amount_minor <= requested_amount_minor))
        AND paid_amount_minor >= 0
        AND (approved_amount_minor IS NULL OR paid_amount_minor <= approved_amount_minor)
    ),
    -- An outcome from the provider carries the amount it decided; a denial carries its reason code.
    CONSTRAINT ck_external_claims_outcome CHECK (
        (state NOT IN ('APPROVED', 'PARTIAL', 'PAYMENT_PENDING', 'PAID', 'RECONCILED')
             OR approved_amount_minor IS NOT NULL)
        AND (state = 'DENIED') = (denial_reason_code IS NOT NULL)
        AND (state <> 'PARTIAL' OR approved_amount_minor < requested_amount_minor)
    ),
    CONSTRAINT ck_external_claims_info_required CHECK (
        state <> 'INFO_REQUIRED' OR information_request_reference IS NOT NULL
    ),
    -- Reconciliation means the ledger says so too, not that the provider said it paid.
    CONSTRAINT ck_external_claims_reconciled CHECK (
        (state = 'RECONCILED') = (reconciled_at IS NOT NULL)
        AND (reconciled_at IS NULL OR ledger_transaction_id IS NOT NULL)
    ),
    -- An unknown outcome must have a query scheduled. An unknown nobody ever asks about again is an
    -- outcome that silently becomes a loss.
    CONSTRAINT ck_external_claims_unknown CHECK (
        state <> 'UNKNOWN' OR next_query_at IS NOT NULL
    ),
    CONSTRAINT ck_external_claims_sequence CHECK (observation_sequence >= 0),
    CONSTRAINT ck_external_claims_row_version CHECK (version >= 0)
);

CREATE INDEX idx_external_claims_case ON external_claims (support_case_id);
CREATE INDEX idx_external_claims_deadline
    ON external_claims (submission_deadline_at)
    WHERE state IN ('LOCAL_APPROVED', 'SUBMISSION_QUEUED', 'INFO_REQUIRED');
CREATE INDEX idx_external_claims_query
    ON external_claims (next_query_at)
    WHERE state IN ('UNKNOWN', 'SUBMITTING', 'SUBMITTED', 'PROVIDER_REVIEW', 'PAYMENT_PENDING');
--rollback DROP TABLE external_claims;

--changeset ninggiangboy:028-33-external-claim-submissions
-- One attempt to hand the provider a frozen manifest, with the intent written before the call. A
-- provider asking for more information produces a new submission version against the same claim key;
-- it never edits the manifest that was already sent, because what was sent is now a fact about the
-- past that somebody may need to prove.
CREATE TABLE external_claim_submissions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_claim_id           UUID NOT NULL,
    submission_version          INTEGER NOT NULL,
    submission_kind             VARCHAR(24) NOT NULL,

    idempotency_key             VARCHAR(96) NOT NULL,
    manifest_id                 UUID NOT NULL,
    manifest_digest             CHAR(64) NOT NULL,
    payload_digest              CHAR(64) NOT NULL,
    attestation_reference       VARCHAR(128),
    terms_version               VARCHAR(32),

    submitted_by_account_holder_id UUID,
    approval_reference          VARCHAR(96),
    provider_deadline_at        TIMESTAMPTZ,
    intent_recorded_at          TIMESTAMPTZ NOT NULL,
    dispatched_at               TIMESTAMPTZ,
    outcome                     VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    response_digest             CHAR(64),
    response_reference          VARCHAR(128),
    failure_classification      VARCHAR(24),
    next_query_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_external_claim_submissions_claim FOREIGN KEY (external_claim_id)
        REFERENCES external_claims (id),
    CONSTRAINT fk_external_claim_submissions_manifest FOREIGN KEY (manifest_id)
        REFERENCES evidence_disclosure_manifests (id),
    CONSTRAINT fk_external_claim_submissions_submitter FOREIGN KEY (submitted_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_external_claim_submissions_version
        UNIQUE (external_claim_id, submission_version),
    CONSTRAINT uk_external_claim_submissions_idempotency
        UNIQUE (external_claim_id, idempotency_key),
    CONSTRAINT ck_external_claim_submissions_kind CHECK (
        submission_kind IN ('INITIAL', 'ADDITIONAL_INFORMATION', 'CORRECTION', 'APPEAL',
                            'WITHDRAWAL')
    ),
    CONSTRAINT ck_external_claim_submissions_outcome CHECK (
        outcome IN ('PENDING', 'ACCEPTED', 'REJECTED', 'UNKNOWN', 'FAILED')
    ),
    -- An unknown outcome is a state that owes a query, not a failure and not a success.
    CONSTRAINT ck_external_claim_submissions_unknown CHECK (
        outcome <> 'UNKNOWN' OR next_query_at IS NOT NULL
    ),
    CONSTRAINT ck_external_claim_submissions_failure CHECK (
        (outcome = 'FAILED') = (failure_classification IS NOT NULL)
        AND (failure_classification IS NULL
             OR failure_classification IN ('TRANSIENT', 'PERMANENT_INPUT', 'PERMANENT_AUTHORIZATION',
                                           'PROVIDER_ERROR'))
    ),
    -- Intent is recorded before the call, so a dispatch can never be earlier than the record of
    -- having decided to make it. This is the ordering that makes a lost response recoverable.
    CONSTRAINT ck_external_claim_submissions_ordering CHECK (
        dispatched_at IS NULL OR dispatched_at >= intent_recorded_at
    ),
    CONSTRAINT ck_external_claim_submissions_response CHECK (
        outcome NOT IN ('ACCEPTED', 'REJECTED') OR response_digest IS NOT NULL
    ),
    CONSTRAINT ck_external_claim_submissions_number CHECK (submission_version > 0)
);

CREATE INDEX idx_external_claim_submissions_claim
    ON external_claim_submissions (external_claim_id, submission_version DESC);
CREATE INDEX idx_external_claim_submissions_query
    ON external_claim_submissions (next_query_at) WHERE outcome IN ('PENDING', 'UNKNOWN');
--rollback DROP TABLE external_claim_submissions;

--changeset ninggiangboy:028-34-external-claim-observations
-- Append-only normalized provider statements. An observation carries the sequence the claim was at
-- when it arrived, so a late delivery that would move the claim backwards can be recorded and then
-- refused as a stale regression instead of quietly undoing a newer outcome.
CREATE TABLE external_claim_observations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_claim_id           UUID NOT NULL,
    external_claim_submission_id UUID,
    observation_sequence        BIGINT NOT NULL,
    source_kind                 VARCHAR(16) NOT NULL,

    provider_event_reference    VARCHAR(128),
    observed_state              VARCHAR(24) NOT NULL,
    currency                    VARCHAR(3),
    approved_amount_minor       BIGINT,
    paid_amount_minor           BIGINT,
    denial_reason_code          VARCHAR(64),
    information_request_reference VARCHAR(128),
    provider_deadline_at        TIMESTAMPTZ,

    payload_digest              CHAR(64) NOT NULL,
    observed_at                 TIMESTAMPTZ NOT NULL,
    received_at                 TIMESTAMPTZ NOT NULL,
    applied                     BOOLEAN NOT NULL,
    rejection_reason            VARCHAR(32),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_external_claim_observations_claim FOREIGN KEY (external_claim_id)
        REFERENCES external_claims (id),
    CONSTRAINT fk_external_claim_observations_submission FOREIGN KEY (external_claim_submission_id)
        REFERENCES external_claim_submissions (id),
    CONSTRAINT uk_external_claim_observations_sequence
        UNIQUE (external_claim_id, observation_sequence),
    -- The same provider event delivered twice is one observation. Providers retry; we must converge.
    CONSTRAINT uk_external_claim_observations_event
        UNIQUE (external_claim_id, provider_event_reference),
    CONSTRAINT ck_external_claim_observations_source CHECK (
        source_kind IN ('WEBHOOK', 'QUERY', 'RECONCILIATION', 'MANUAL')
    ),
    CONSTRAINT ck_external_claim_observations_state CHECK (
        observed_state IN ('SUBMITTED', 'PROVIDER_REVIEW', 'INFO_REQUIRED', 'APPROVED', 'PARTIAL',
                           'DENIED', 'PAYMENT_PENDING', 'PAID', 'WITHDRAWN', 'UNKNOWN')
    ),
    CONSTRAINT ck_external_claim_observations_amounts CHECK (
        (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND (approved_amount_minor IS NULL OR approved_amount_minor >= 0)
        AND (paid_amount_minor IS NULL OR paid_amount_minor >= 0)
        AND ((approved_amount_minor IS NULL AND paid_amount_minor IS NULL)
             OR currency IS NOT NULL)
    ),
    -- An observation that was not applied says why. Recording the refusal is the point: a stale or
    -- contradictory provider message is evidence about the provider, not something to discard.
    CONSTRAINT ck_external_claim_observations_applied CHECK (
        applied = (rejection_reason IS NULL)
        AND (rejection_reason IS NULL
             OR rejection_reason IN ('STALE_REGRESSION', 'DUPLICATE', 'UNPARSEABLE',
                                     'AMOUNT_CONFLICT', 'UNKNOWN_CLAIM'))
    ),
    CONSTRAINT ck_external_claim_observations_times CHECK (received_at >= observed_at),
    CONSTRAINT ck_external_claim_observations_sequence_positive CHECK (observation_sequence > 0)
);

CREATE INDEX idx_external_claim_observations_claim
    ON external_claim_observations (external_claim_id, observation_sequence DESC);
--rollback DROP TABLE external_claim_observations;

--changeset ninggiangboy:028-35-payment-dispute-strategies
-- What this domain decides about a provider dispute that payment owns. Migration 021 normalizes the
-- provider's own observations; duplicating them here would give the company two accounts of one
-- provider's answer. What lives here instead is the part payment cannot decide: whether to accept or
-- represent, which evidence goes, who authorized the submission, and against which deadline.
CREATE TABLE payment_dispute_strategies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    payment_dispute_id          UUID NOT NULL,

    strategy                    VARCHAR(24) NOT NULL,
    strategy_reason_code        VARCHAR(48) NOT NULL,
    decided_by_account_holder_id UUID,
    decided_at                  TIMESTAMPTZ,
    approval_reference          VARCHAR(96),
    case_decision_id            UUID,

    manifest_id                 UUID,
    manifest_digest             CHAR(64),
    provider_deadline_at        TIMESTAMPTZ NOT NULL,
    submission_command_id       VARCHAR(96),
    submitted_at                TIMESTAMPTZ,
    submission_outcome          VARCHAR(16) NOT NULL DEFAULT 'NOT_SUBMITTED',
    narrative_reference         VARCHAR(256),
    narrative_drafted_by_model  BOOLEAN NOT NULL DEFAULT false,
    narrative_reviewed_by_account_holder_id UUID,

    state                       VARCHAR(24) NOT NULL DEFAULT 'PENDING_STRATEGY',
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_dispute_strategies_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_payment_dispute_strategies_dispute FOREIGN KEY (payment_dispute_id)
        REFERENCES payment_disputes (id),
    CONSTRAINT fk_payment_dispute_strategies_decider FOREIGN KEY (decided_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_payment_dispute_strategies_manifest FOREIGN KEY (manifest_id)
        REFERENCES evidence_disclosure_manifests (id),
    CONSTRAINT fk_payment_dispute_strategies_reviewer
        FOREIGN KEY (narrative_reviewed_by_account_holder_id) REFERENCES account_holders (id),
    CONSTRAINT uk_payment_dispute_strategies_dispute UNIQUE (payment_dispute_id),
    CONSTRAINT uk_payment_dispute_strategies_command UNIQUE (submission_command_id),
    CONSTRAINT ck_payment_dispute_strategies_strategy CHECK (
        strategy IN ('UNDECIDED', 'ACCEPT', 'REPRESENT', 'PARTIAL_REPRESENT', 'REFER_TO_LEGAL')
    ),
    CONSTRAINT ck_payment_dispute_strategies_state CHECK (
        state IN ('PENDING_STRATEGY', 'EVIDENCE_READY', 'SUBMITTING', 'SUBMITTED', 'ACCEPTED',
                  'UNKNOWN', 'CLOSED')
    ),
    CONSTRAINT ck_payment_dispute_strategies_outcome CHECK (
        submission_outcome IN ('NOT_SUBMITTED', 'PENDING', 'ACCEPTED', 'REJECTED', 'UNKNOWN',
                               'FAILED')
    ),
    -- Choosing a strategy is a human act with a named authorizer. A dispute that was represented
    -- because a default said so is one nobody can be asked to explain.
    CONSTRAINT ck_payment_dispute_strategies_decision CHECK (
        (strategy = 'UNDECIDED')
            OR (decided_by_account_holder_id IS NOT NULL AND decided_at IS NOT NULL)
    ),
    -- Representment means an evidence manifest was frozen and a submission command was authorized.
    CONSTRAINT ck_payment_dispute_strategies_representment CHECK (
        strategy NOT IN ('REPRESENT', 'PARTIAL_REPRESENT')
            OR state NOT IN ('SUBMITTING', 'SUBMITTED')
            OR (manifest_id IS NOT NULL AND manifest_digest IS NOT NULL
                AND submission_command_id IS NOT NULL AND approval_reference IS NOT NULL)
    ),
    -- A model may draft a narrative from cited facts. A human signs it before it reaches a provider,
    -- because an invented claim about delivery or consent is fraud regardless of who typed it.
    CONSTRAINT ck_payment_dispute_strategies_narrative CHECK (
        NOT narrative_drafted_by_model
            OR submitted_at IS NULL
            OR narrative_reviewed_by_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_payment_dispute_strategies_submitted CHECK (
        (submitted_at IS NULL) OR submission_outcome <> 'NOT_SUBMITTED'
    ),
    CONSTRAINT ck_payment_dispute_strategies_row_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_dispute_strategies_case ON payment_dispute_strategies (support_case_id);
CREATE INDEX idx_payment_dispute_strategies_deadline
    ON payment_dispute_strategies (provider_deadline_at)
    WHERE state NOT IN ('SUBMITTED', 'ACCEPTED', 'CLOSED');
--rollback DROP TABLE payment_dispute_strategies;

--changeset ninggiangboy:028-36-case-decisions
-- The immutable record of what was decided, under which version of which rules, on which inputs. A
-- correction, an appeal, a late provider result or a recovery creates a new decision that names the
-- one it replaces; nothing edits a decision that was already communicated to a participant. The
-- input and evidence digests are what make a decision reproducible years later, when the policy that
-- produced it has been superseded four times.
CREATE TABLE case_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    damage_claim_id             UUID,
    lifecycle_episode           SMALLINT NOT NULL,
    command_id                  VARCHAR(96) NOT NULL,

    decision_kind               VARCHAR(24) NOT NULL,
    support_policy_version_id   UUID NOT NULL,
    authority_policy_version_id UUID NOT NULL,
    remedy_catalog_key          VARCHAR(48),
    remedy_catalog_version      INTEGER,
    protection_program_version_id UUID,

    decision_basis              VARCHAR(24) NOT NULL,
    precedence_rung             VARCHAR(32) NOT NULL,
    outcome                     VARCHAR(24) NOT NULL,
    result_reason_codes         TEXT[] NOT NULL DEFAULT '{}',
    unresolved_uncertainty      TEXT[] NOT NULL DEFAULT '{}',

    input_digest                CHAR(64) NOT NULL,
    evidence_manifest_digest    CHAR(64) NOT NULL,
    decision_digest             CHAR(64) NOT NULL,

    decided_by_actor_type       VARCHAR(24) NOT NULL,
    decided_by_account_holder_id UUID,
    authority_snapshot_reference VARCHAR(128) NOT NULL,
    conflict_checks_passed      BOOLEAN NOT NULL DEFAULT false,
    break_glass                 BOOLEAN NOT NULL DEFAULT false,
    break_glass_reference       VARCHAR(96),
    manual_exception_code       VARCHAR(48),

    internal_reason_reference   VARCHAR(256),
    participant_explanation_template_key VARCHAR(64),
    appeal_available            BOOLEAN NOT NULL DEFAULT true,
    appeal_deadline_at          TIMESTAMPTZ,
    appeal_route                VARCHAR(64),
    disclosure_constraint       VARCHAR(32),

    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ,
    communicated_at             TIMESTAMPTZ,
    superseded_by_decision_id   UUID,
    superseded_at               TIMESTAMPTZ,
    supersession_reason         VARCHAR(48),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_decisions_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_decisions_claim FOREIGN KEY (damage_claim_id) REFERENCES damage_claims (id),
    CONSTRAINT fk_case_decisions_policy FOREIGN KEY (support_policy_version_id)
        REFERENCES support_policy_versions (id),
    CONSTRAINT fk_case_decisions_authority FOREIGN KEY (authority_policy_version_id)
        REFERENCES authority_policy_versions (id),
    CONSTRAINT fk_case_decisions_program FOREIGN KEY (protection_program_version_id)
        REFERENCES protection_program_versions (id),
    CONSTRAINT fk_case_decisions_decider FOREIGN KEY (decided_by_account_holder_id)
        REFERENCES account_holders (id),
    -- The outgoing decision names its successor, which the same transaction creates. Deferring the
    -- check lets the pair commit together; an immediate one would demand the replacement exist first.
    CONSTRAINT fk_case_decisions_supersession FOREIGN KEY (superseded_by_decision_id)
        REFERENCES case_decisions (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_case_decisions_command UNIQUE (support_case_id, command_id),
    CONSTRAINT ck_case_decisions_kind CHECK (
        decision_kind IN ('ELIGIBILITY', 'LIABILITY', 'VALUATION', 'REMEDY', 'COVERAGE',
                          'DISPUTE_STRATEGY', 'APPEAL_RESULT', 'CLOSURE', 'RECOVERY')
    ),
    CONSTRAINT ck_case_decisions_basis CHECK (
        decision_basis IN ('CONTRACTUAL', 'PROTECTION_OR_CLAIM', 'GOODWILL', 'MIXED',
                           'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_case_decisions_precedence CHECK (
        precedence_rung IN ('SAFETY_LEGAL', 'ACCEPTED_CONTRACT', 'MANDATORY_MARKET_OBLIGATION',
                            'PROTECTION_CONTRACT', 'MARKETPLACE_TERMS', 'SERVICE_RECOVERY',
                            'EXCEPTIONAL_MANUAL_REVIEW')
    ),
    CONSTRAINT ck_case_decisions_outcome CHECK (
        outcome IN ('GRANTED', 'PARTIALLY_GRANTED', 'REFUSED', 'REFERRED', 'NO_ACTION',
                    'AFFIRMED', 'MODIFIED', 'REVERSED', 'REMANDED')
    ),
    CONSTRAINT ck_case_decisions_state CHECK (
        state IN ('DRAFT', 'PENDING_APPROVAL', 'EFFECTIVE', 'SUPERSEDED', 'VOIDED')
    ),
    CONSTRAINT ck_case_decisions_decider_type CHECK (
        decided_by_actor_type IN ('AGENT', 'SUPERVISOR', 'SPECIALIST', 'ADJUSTER', 'LEGAL',
                                  'DETERMINISTIC_EVALUATOR')
    ),
    CONSTRAINT ck_case_decisions_human CHECK (
        decided_by_actor_type = 'DETERMINISTIC_EVALUATOR' OR decided_by_account_holder_id IS NOT NULL
    ),
    -- Goodwill is service recovery and nothing else. Letting a goodwill payment be booked against the
    -- contract rung is how a discretionary gesture quietly becomes an admission of liability.
    CONSTRAINT ck_case_decisions_goodwill CHECK (
        decision_basis <> 'GOODWILL' OR precedence_rung = 'SERVICE_RECOVERY'
    ),
    -- Refusing somebody requires stating why in codes that can be counted and audited, not only in
    -- prose, and requires the conflict checks to have actually run.
    CONSTRAINT ck_case_decisions_refusal CHECK (
        outcome NOT IN ('REFUSED', 'PARTIALLY_GRANTED')
            OR cardinality(result_reason_codes) > 0
    ),
    CONSTRAINT ck_case_decisions_conflict_checks CHECK (
        state <> 'EFFECTIVE' OR conflict_checks_passed
    ),
    -- An effective decision has a start, an explanation a participant can be shown, and the digest
    -- trio that lets it be re-derived.
    CONSTRAINT ck_case_decisions_effective CHECK (
        state NOT IN ('EFFECTIVE', 'SUPERSEDED')
            OR (effective_from IS NOT NULL
                AND participant_explanation_template_key IS NOT NULL)
    ),
    -- Appeal is either available with a deadline and a route, or unavailable and explained by the
    -- policy version. A decision that says "no appeal" and nothing else is a dead end by accident.
    CONSTRAINT ck_case_decisions_appeal CHECK (
        NOT appeal_available OR (appeal_deadline_at IS NOT NULL AND appeal_route IS NOT NULL)
    ),
    CONSTRAINT ck_case_decisions_disclosure CHECK (
        disclosure_constraint IS NULL
            OR disclosure_constraint IN ('NONE', 'SUMMARY_ONLY', 'REDACT_OTHER_PARTY',
                                         'AUTHORITY_ONLY', 'LEGAL_REVIEW_REQUIRED')
    ),
    -- Break-glass names its authorization; a manual exception names its approved code. Neither is a
    -- free-text justification written after the fact.
    CONSTRAINT ck_case_decisions_break_glass CHECK (
        break_glass = (break_glass_reference IS NOT NULL)
    ),
    CONSTRAINT ck_case_decisions_exception CHECK (
        precedence_rung <> 'EXCEPTIONAL_MANUAL_REVIEW' OR manual_exception_code IS NOT NULL
    ),
    CONSTRAINT ck_case_decisions_supersession CHECK (
        (superseded_by_decision_id IS NULL) = (superseded_at IS NULL)
        AND (superseded_by_decision_id IS NULL) = (supersession_reason IS NULL)
        AND (superseded_by_decision_id IS NULL OR superseded_by_decision_id <> id)
        AND (state = 'SUPERSEDED') = (superseded_by_decision_id IS NOT NULL)
    ),
    CONSTRAINT ck_case_decisions_catalog CHECK (
        (remedy_catalog_key IS NULL) = (remedy_catalog_version IS NULL)
        AND (remedy_catalog_version IS NULL OR remedy_catalog_version > 0)
    ),
    CONSTRAINT ck_case_decisions_expiry CHECK (
        expires_at IS NULL OR effective_from IS NULL OR expires_at > effective_from
    ),
    CONSTRAINT ck_case_decisions_episode CHECK (lifecycle_episode > 0),
    CONSTRAINT ck_case_decisions_row_version CHECK (version >= 0)
);

-- One effective decision of a kind per case episode and claim. Two would mean the case has two
-- answers to the same question and the participant has been told one of them.
CREATE UNIQUE INDEX uk_case_decisions_effective
    ON case_decisions (support_case_id, lifecycle_episode, decision_kind, damage_claim_id)
    NULLS NOT DISTINCT
    WHERE state = 'EFFECTIVE';
CREATE INDEX idx_case_decisions_case ON case_decisions (support_case_id, created_at DESC);
CREATE INDEX idx_case_decisions_claim ON case_decisions (damage_claim_id);
CREATE INDEX idx_case_decisions_appeal_deadline
    ON case_decisions (appeal_deadline_at) WHERE state = 'EFFECTIVE' AND appeal_available;

ALTER TABLE support_cases
    ADD CONSTRAINT fk_support_cases_latest_decision FOREIGN KEY (latest_decision_id)
        REFERENCES case_decisions (id);
ALTER TABLE damage_claims
    ADD CONSTRAINT fk_damage_claims_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id);
ALTER TABLE payment_dispute_strategies
    ADD CONSTRAINT fk_payment_dispute_strategies_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id);
ALTER TABLE external_claims
    ADD CONSTRAINT fk_external_claims_approval_decision FOREIGN KEY (local_approval_decision_id)
        REFERENCES case_decisions (id);
ALTER TABLE case_transitions
    ADD CONSTRAINT fk_case_transitions_decision FOREIGN KEY (related_decision_id)
        REFERENCES case_decisions (id);
ALTER TABLE case_transitions
    ADD CONSTRAINT fk_case_transitions_work_item FOREIGN KEY (related_work_item_id)
        REFERENCES case_work_items (id);
--rollback ALTER TABLE case_transitions DROP CONSTRAINT fk_case_transitions_work_item;
--rollback ALTER TABLE case_transitions DROP CONSTRAINT fk_case_transitions_decision;
--rollback ALTER TABLE external_claims DROP CONSTRAINT fk_external_claims_approval_decision;
--rollback ALTER TABLE payment_dispute_strategies DROP CONSTRAINT fk_payment_dispute_strategies_decision;
--rollback ALTER TABLE damage_claims DROP CONSTRAINT fk_damage_claims_decision;
--rollback ALTER TABLE support_cases DROP CONSTRAINT fk_support_cases_latest_decision;
--rollback DROP TABLE case_decisions;

--changeset ninggiangboy:028-37-case-decision-findings
-- Which findings a decision actually rested on, and in what role. Keeping this explicit is what lets
-- an appeal argue with the reasoning rather than only with the outcome: a decision that cites a
-- finding as supporting must face the findings recorded as contradicting it.
CREATE TABLE case_decision_findings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_decision_id            UUID NOT NULL,
    case_finding_id             UUID NOT NULL,
    citation_role               VARCHAR(24) NOT NULL,
    weight                      VARCHAR(16) NOT NULL,
    finding_outcome_at_citation VARCHAR(16) NOT NULL,
    note_reference              VARCHAR(256),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_decision_findings_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT fk_case_decision_findings_finding FOREIGN KEY (case_finding_id)
        REFERENCES case_findings (id),
    CONSTRAINT uk_case_decision_findings_pair UNIQUE (case_decision_id, case_finding_id),
    CONSTRAINT ck_case_decision_findings_role CHECK (
        citation_role IN ('DETERMINATIVE', 'SUPPORTING', 'CONTRADICTING', 'CONTEXT')
    ),
    CONSTRAINT ck_case_decision_findings_weight CHECK (
        weight IN ('PRIMARY', 'SECONDARY', 'CORROBORATING', 'NOT_RELIED_ON')
    ),
    CONSTRAINT ck_case_decision_findings_outcome CHECK (
        finding_outcome_at_citation IN ('SUPPORTED', 'NOT_SUPPORTED', 'INCONCLUSIVE',
                                        'NOT_APPLICABLE')
    ),
    -- An unresolved question cannot be the thing a decision turns on. "We could not tell" is not a
    -- ground; it is the reason a decision has to rest on something else or not be taken at all.
    CONSTRAINT ck_case_decision_findings_determinative CHECK (
        citation_role <> 'DETERMINATIVE'
            OR finding_outcome_at_citation IN ('SUPPORTED', 'NOT_SUPPORTED')
    ),
    CONSTRAINT ck_case_decision_findings_not_relied CHECK (
        (weight = 'NOT_RELIED_ON') = (citation_role = 'CONTEXT')
    )
);

CREATE INDEX idx_case_decision_findings_finding ON case_decision_findings (case_finding_id);
--rollback DROP TABLE case_decision_findings;

--changeset ninggiangboy:028-38-case-decision-approvals
-- Maker-checker, bound to a digest rather than to a row. An approval records the exact decision
-- digest the approver saw; if the decision is re-derived the digest moves and the old approval no
-- longer matches anything, which is what "material changes invalidate approval" has to mean if it is
-- to be more than a convention. Append-only, because an approval that can be edited is a signature
-- that can be moved onto a different document.
CREATE TABLE case_decision_approvals (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_decision_id            UUID NOT NULL,
    approval_role               VARCHAR(16) NOT NULL,
    approval_tier               VARCHAR(16) NOT NULL,

    approver_account_holder_id  UUID NOT NULL,
    maker_account_holder_id     UUID NOT NULL,
    agent_skill_grant_id        UUID,
    authority_policy_version_id UUID NOT NULL,

    approved_decision_digest    CHAR(64) NOT NULL,
    request_digest              CHAR(64) NOT NULL,
    outcome                     VARCHAR(16) NOT NULL,
    refusal_reason              VARCHAR(48),
    comments_reference          VARCHAR(256),
    step_up_authentication_reference VARCHAR(96),

    decided_at                  TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    invalidated_at              TIMESTAMPTZ,
    invalidation_reason         VARCHAR(32),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_case_decision_approvals_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT fk_case_decision_approvals_approver FOREIGN KEY (approver_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_decision_approvals_maker FOREIGN KEY (maker_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_decision_approvals_grant FOREIGN KEY (agent_skill_grant_id)
        REFERENCES agent_skill_grants (id),
    CONSTRAINT fk_case_decision_approvals_authority FOREIGN KEY (authority_policy_version_id)
        REFERENCES authority_policy_versions (id),
    CONSTRAINT ck_case_decision_approvals_role CHECK (
        approval_role IN ('PEER', 'SUPERVISOR', 'SPECIALIST', 'FINANCE', 'LEGAL')
    ),
    CONSTRAINT ck_case_decision_approvals_tier CHECK (
        approval_tier IN ('PEER', 'SUPERVISOR', 'SPECIALIST', 'LEGAL')
    ),
    CONSTRAINT ck_case_decision_approvals_outcome CHECK (
        outcome IN ('APPROVED', 'REFUSED', 'ESCALATED')
        AND (outcome = 'REFUSED') = (refusal_reason IS NOT NULL)
    ),
    -- The maker never approves their own work. This is the whole point of the row existing.
    CONSTRAINT ck_case_decision_approvals_self CHECK (
        approver_account_holder_id <> maker_account_holder_id
    ),
    CONSTRAINT ck_case_decision_approvals_invalidation CHECK (
        (invalidated_at IS NULL) = (invalidation_reason IS NULL)
        AND (invalidation_reason IS NULL
             OR invalidation_reason IN ('DIGEST_CHANGED', 'AUTHORITY_REVOKED', 'EXPIRED',
                                        'SUPERSEDED'))
        AND (invalidated_at IS NULL OR invalidated_at >= decided_at)
    ),
    CONSTRAINT ck_case_decision_approvals_expiry CHECK (
        expires_at IS NULL OR expires_at > decided_at
    )
);

-- One live approval per decision and role. A second is either a duplicate click or a second opinion
-- collected until somebody agreed, and neither should be able to stand as the authorization.
CREATE UNIQUE INDEX uk_case_decision_approvals_live
    ON case_decision_approvals (case_decision_id, approval_role)
    WHERE outcome = 'APPROVED' AND invalidated_at IS NULL;
CREATE INDEX idx_case_decision_approvals_approver
    ON case_decision_approvals (approver_account_holder_id, decided_at DESC);
--rollback DROP TABLE case_decision_approvals;

--changeset ninggiangboy:028-39-case-remedies
-- What a decision authorized, tracked separately from what actually happened. APPROVED means an
-- entitlement was authorized; only the downstream domain's own success proves the money moved, and a
-- posted ledger entry proves an accounting effect rather than a beneficiary's bank receipt. Keeping
-- authorization and execution in different columns is what stops a support screen from telling a
-- guest their refund is complete because somebody clicked approve.
CREATE TABLE case_remedies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    case_decision_id            UUID NOT NULL,
    damage_claim_id             UUID,
    remedy_code                 VARCHAR(48) NOT NULL,
    remedy_catalog_version_id   UUID NOT NULL,
    remedy_kind                 VARCHAR(32) NOT NULL,
    monetary                    BOOLEAN NOT NULL,

    decision_basis              VARCHAR(24) NOT NULL,
    currency                    VARCHAR(3),
    total_amount_minor          BIGINT,
    executed_amount_minor       BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    approval_required           BOOLEAN NOT NULL DEFAULT false,
    approval_digest             CHAR(64),
    approved_at                 TIMESTAMPTZ,
    rejection_reason            VARCHAR(48),
    expires_at                  TIMESTAMPTZ,

    execution_started_at        TIMESTAMPTZ,
    execution_completed_at      TIMESTAMPTZ,
    unknown_since               TIMESTAMPTZ,
    next_reconciliation_at      TIMESTAMPTZ,
    exception_owner_account_holder_id UUID,
    exception_deadline_at       TIMESTAMPTZ,

    reverses_remedy_id          UUID,
    reversal_decision_id        UUID,
    explanation_template_key    VARCHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_remedies_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_remedies_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT fk_case_remedies_claim FOREIGN KEY (damage_claim_id) REFERENCES damage_claims (id),
    CONSTRAINT fk_case_remedies_catalog FOREIGN KEY (remedy_catalog_version_id)
        REFERENCES remedy_catalog_versions (id),
    CONSTRAINT fk_case_remedies_exception_owner FOREIGN KEY (exception_owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_remedies_reversal_target FOREIGN KEY (reverses_remedy_id)
        REFERENCES case_remedies (id),
    CONSTRAINT fk_case_remedies_reversal_decision FOREIGN KEY (reversal_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT ck_case_remedies_state CHECK (
        state IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING', 'APPROVED', 'REJECTED', 'EXPIRED',
                  'INSTRUCTION_PENDING', 'EXECUTING', 'SUCCEEDED', 'PARTIAL', 'UNKNOWN',
                  'RECONCILING', 'REVERSED_OR_RECOVERED')
    ),
    CONSTRAINT ck_case_remedies_basis CHECK (
        decision_basis IN ('CONTRACTUAL', 'PROTECTION_OR_CLAIM', 'GOODWILL')
    ),
    CONSTRAINT ck_case_remedies_kind CHECK (
        remedy_kind IN ('INFORMATION', 'APOLOGY', 'OPERATIONAL_ASSISTANCE', 'HOST_TASK',
                        'RECLEANING', 'REPAIR', 'REPLACEMENT_AMENITY', 'ACCESS_RECOVERY',
                        'BOOKING_CANCELLATION', 'BOOKING_MODIFICATION', 'RELOCATION',
                        'REFUND', 'HOST_WAIVER', 'TRAVEL_CREDIT', 'COUPON', 'GOODWILL_PAYMENT',
                        'RECEIPTED_REIMBURSEMENT', 'CLAIM_PAYMENT', 'DISPUTE_ACCEPTANCE',
                        'DISPUTE_REPRESENTMENT', 'PAYOUT_HOLD', 'PAYOUT_RELEASE', 'RECOVERY_REQUEST',
                        'LISTING_CORRECTION', 'RISK_REVIEW_REQUEST', 'MODERATION_REQUEST',
                        'SAFETY_ESCALATION')
    ),
    -- A monetary remedy has an amount and a currency; a non-monetary one has neither, so nothing can
    -- later read an amount off a remedy that was never about money.
    CONSTRAINT ck_case_remedies_monetary CHECK (
        monetary = (total_amount_minor IS NOT NULL)
        AND (total_amount_minor IS NULL) = (currency IS NULL)
        AND (total_amount_minor IS NULL OR total_amount_minor > 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND executed_amount_minor >= 0
        AND (total_amount_minor IS NULL OR executed_amount_minor <= total_amount_minor)
    ),
    -- Approval binds a digest. An approved remedy with no digest is an approval of nothing in
    -- particular, and there would be no way to tell later that the terms had moved underneath it.
    CONSTRAINT ck_case_remedies_approval CHECK (
        (NOT approval_required AND approval_digest IS NULL AND approved_at IS NULL)
        OR state IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING', 'REJECTED', 'EXPIRED')
        OR (approval_digest IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_case_remedies_rejection CHECK (
        (state = 'REJECTED') = (rejection_reason IS NOT NULL)
    ),
    -- Partial execution means strictly less than authorized moved; success means all of it did.
    CONSTRAINT ck_case_remedies_partial CHECK (
        state <> 'PARTIAL' OR total_amount_minor IS NULL
            OR (executed_amount_minor > 0 AND executed_amount_minor < total_amount_minor)
    ),
    CONSTRAINT ck_case_remedies_succeeded CHECK (
        state <> 'SUCCEEDED' OR total_amount_minor IS NULL
            OR executed_amount_minor = total_amount_minor
    ),
    -- An unknown outcome is owned by somebody, with a date. An unknown left to itself becomes either
    -- an unpaid entitlement or a double payment, and nobody finds out which until a customer asks.
    CONSTRAINT ck_case_remedies_unknown CHECK (
        state NOT IN ('UNKNOWN', 'RECONCILING')
            OR (unknown_since IS NOT NULL AND next_reconciliation_at IS NOT NULL)
    ),
    CONSTRAINT ck_case_remedies_exception CHECK (
        (exception_owner_account_holder_id IS NULL) = (exception_deadline_at IS NULL)
    ),
    -- Reversing or recovering is a new authorized decision, never a status edit on the original.
    CONSTRAINT ck_case_remedies_reversal CHECK (
        (reverses_remedy_id IS NULL) = (reversal_decision_id IS NULL)
        AND (reverses_remedy_id IS NULL OR reverses_remedy_id <> id)
    ),
    CONSTRAINT ck_case_remedies_execution_times CHECK (
        (execution_completed_at IS NULL OR execution_started_at IS NOT NULL)
        AND (execution_completed_at IS NULL OR execution_completed_at >= execution_started_at)
    ),
    CONSTRAINT ck_case_remedies_row_version CHECK (version >= 0)
);

CREATE INDEX idx_case_remedies_case ON case_remedies (support_case_id, state);
CREATE INDEX idx_case_remedies_decision ON case_remedies (case_decision_id);
CREATE INDEX idx_case_remedies_reconciliation
    ON case_remedies (next_reconciliation_at) WHERE state IN ('UNKNOWN', 'RECONCILING');
CREATE INDEX idx_case_remedies_expiry
    ON case_remedies (expires_at)
    WHERE state IN ('PROPOSED', 'APPROVAL_PENDING', 'APPROVED') AND expires_at IS NOT NULL;
--rollback DROP TABLE case_remedies;

--changeset ninggiangboy:028-40-case-remedy-lines
-- One beneficiary, one funder, one amount. Host-funded, platform-funded, insurer-funded and
-- guest-funded values never substitute for one another, so the funder is a column rather than an
-- assumption, and a host cannot be made to fund what a host does not control: taxes and platform
-- fees belong to whoever levied them.
CREATE TABLE case_remedy_lines (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_remedy_id              UUID NOT NULL,
    line_number                 SMALLINT NOT NULL,

    beneficiary_kind            VARCHAR(16) NOT NULL,
    beneficiary_account_holder_id UUID,
    funder_kind                 VARCHAR(16) NOT NULL,
    funder_account_holder_id    UUID,

    currency                    VARCHAR(3) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    source_kind                 VARCHAR(24) NOT NULL,
    source_reference            VARCHAR(96),
    source_line_id              UUID,
    damage_claim_item_id        UUID,

    tax_treatment               VARCHAR(24) NOT NULL,
    tax_document_reference      VARCHAR(96),
    ceiling_scope               VARCHAR(24) NOT NULL,
    remedy_reservation_id       UUID,
    remedy_budget_window_id     UUID,
    reason_code                 VARCHAR(48) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_remedy_lines_remedy FOREIGN KEY (case_remedy_id)
        REFERENCES case_remedies (id),
    CONSTRAINT fk_case_remedy_lines_beneficiary FOREIGN KEY (beneficiary_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_remedy_lines_funder FOREIGN KEY (funder_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_remedy_lines_claim_item FOREIGN KEY (damage_claim_item_id)
        REFERENCES damage_claim_items (id),
    CONSTRAINT uk_case_remedy_lines_number UNIQUE (case_remedy_id, line_number),
    CONSTRAINT ck_case_remedy_lines_beneficiary_kind CHECK (
        beneficiary_kind IN ('GUEST', 'HOST', 'PLATFORM', 'THIRD_PARTY')
    ),
    CONSTRAINT ck_case_remedy_lines_funder_kind CHECK (
        funder_kind IN ('PLATFORM', 'HOST', 'GUEST', 'INSURER', 'PARTNER')
    ),
    CONSTRAINT ck_case_remedy_lines_party_identity CHECK (
        (beneficiary_kind IN ('GUEST', 'HOST')) = (beneficiary_account_holder_id IS NOT NULL)
        AND (funder_kind IN ('HOST', 'GUEST')) = (funder_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_case_remedy_lines_distinct_parties CHECK (
        beneficiary_account_holder_id IS NULL OR funder_account_holder_id IS NULL
            OR beneficiary_account_holder_id <> funder_account_holder_id
    ),
    CONSTRAINT ck_case_remedy_lines_source CHECK (
        source_kind IN ('BOOKING_LINE', 'QUOTE_LINE', 'TAX_LINE', 'PLATFORM_FEE',
                        'HOST_PAYOUT_ALLOCATION', 'CLAIM_ITEM', 'PROTECTION_LIMIT',
                        'GOODWILL_BUDGET', 'DEPOSIT', 'NOT_APPLICABLE')
    ),
    -- A host may give away only value a host controls. Taxes and platform fees are somebody else's
    -- money, and a host waiver against them is a refund the platform pays without deciding to.
    CONSTRAINT ck_case_remedy_lines_host_funding CHECK (
        funder_kind <> 'HOST' OR source_kind NOT IN ('TAX_LINE', 'PLATFORM_FEE', 'PROTECTION_LIMIT')
    ),
    -- Goodwill comes out of a goodwill budget, not out of a booking line. Charging a discretionary
    -- gesture to a contractual source is how the contract silently changes.
    CONSTRAINT ck_case_remedy_lines_goodwill_source CHECK (
        source_kind <> 'GOODWILL_BUDGET' OR funder_kind IN ('PLATFORM', 'PARTNER')
    ),
    CONSTRAINT ck_case_remedy_lines_tax CHECK (
        tax_treatment IN ('NOT_APPLICABLE', 'PRICE_ADJUSTING', 'NON_PRICE_ADJUSTING',
                          'REQUIRES_TAX_DECISION')
        AND (tax_treatment <> 'PRICE_ADJUSTING' OR tax_document_reference IS NOT NULL)
    ),
    CONSTRAINT ck_case_remedy_lines_ceiling CHECK (
        ceiling_scope IN ('PER_CASE', 'PER_BOOKING', 'PER_SOURCE_LINE', 'PER_CLAIM_ITEM',
                          'PER_PROGRAM', 'PER_USER_PERIOD', 'NOT_APPLICABLE')
    ),
    -- Money leaves against something that was checked: either a reservation held on a source ceiling
    -- or a budget window. A line with neither was never measured against a limit at all.
    CONSTRAINT ck_case_remedy_lines_guarded CHECK (
        amount_minor = 0 OR ceiling_scope = 'NOT_APPLICABLE'
            OR remedy_reservation_id IS NOT NULL OR remedy_budget_window_id IS NOT NULL
    ),
    CONSTRAINT ck_case_remedy_lines_amount CHECK (
        currency ~ '^[A-Z]{3}$' AND amount_minor >= 0
    ),
    CONSTRAINT ck_case_remedy_lines_number CHECK (line_number > 0),
    CONSTRAINT ck_case_remedy_lines_row_version CHECK (version >= 0)
);

CREATE INDEX idx_case_remedy_lines_remedy ON case_remedy_lines (case_remedy_id, line_number);
CREATE INDEX idx_case_remedy_lines_claim_item ON case_remedy_lines (damage_claim_item_id);
--rollback DROP TABLE case_remedy_lines;

--changeset ninggiangboy:028-41-remedy-reservations
-- A hold taken on a source ceiling while a remedy is being decided. Without it, two agents each
-- reading "remaining eligible" a second apart both see the full amount and both authorize it, and
-- the source line is refunded twice. The reservation is what makes the cumulative rule a
-- transactional fact rather than a calculation somebody performed a moment ago.
CREATE TABLE remedy_reservations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    case_remedy_id              UUID,
    ceiling_scope               VARCHAR(24) NOT NULL,
    scope_key                   VARCHAR(128) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    ceiling_amount_minor        BIGINT NOT NULL,
    reserved_amount_minor       BIGINT NOT NULL,
    consumed_amount_minor       BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(16) NOT NULL DEFAULT 'HELD',
    held_at                     TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    released_at                 TIMESTAMPTZ,
    release_reason              VARCHAR(32),
    held_by_account_holder_id   UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_remedy_reservations_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_remedy_reservations_remedy FOREIGN KEY (case_remedy_id)
        REFERENCES case_remedies (id),
    CONSTRAINT fk_remedy_reservations_holder FOREIGN KEY (held_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_remedy_reservations_scope CHECK (
        ceiling_scope IN ('PER_CASE', 'PER_BOOKING', 'PER_SOURCE_LINE', 'PER_CLAIM_ITEM',
                          'PER_PROGRAM', 'PER_USER_PERIOD')
    ),
    CONSTRAINT ck_remedy_reservations_state CHECK (
        state IN ('HELD', 'CONSUMED', 'RELEASED', 'EXPIRED')
    ),
    -- Nothing may be reserved or consumed beyond the ceiling it was measured against, and what was
    -- consumed can never exceed what was reserved.
    CONSTRAINT ck_remedy_reservations_amounts CHECK (
        currency ~ '^[A-Z]{3}$'
        AND ceiling_amount_minor >= 0
        AND reserved_amount_minor > 0
        AND reserved_amount_minor <= ceiling_amount_minor
        AND consumed_amount_minor >= 0
        AND consumed_amount_minor <= reserved_amount_minor
    ),
    CONSTRAINT ck_remedy_reservations_consumed CHECK (
        state <> 'CONSUMED' OR (consumed_amount_minor > 0 AND case_remedy_id IS NOT NULL)
    ),
    CONSTRAINT ck_remedy_reservations_release CHECK (
        (state IN ('RELEASED', 'EXPIRED')) = (released_at IS NOT NULL)
        AND (released_at IS NULL) = (release_reason IS NULL)
        AND (release_reason IS NULL
             OR release_reason IN ('REMEDY_REJECTED', 'REMEDY_EXPIRED', 'LEASE_EXPIRED',
                                   'SUPERSEDED', 'MANUAL_RELEASE'))
    ),
    -- A hold without an expiry is a permanent reduction of somebody's entitlement by an agent who
    -- opened a screen once and never came back.
    CONSTRAINT ck_remedy_reservations_expiry CHECK (expires_at > held_at),
    CONSTRAINT ck_remedy_reservations_row_version CHECK (version >= 0)
);

CREATE INDEX idx_remedy_reservations_scope
    ON remedy_reservations (ceiling_scope, scope_key, currency) WHERE state = 'HELD';
CREATE INDEX idx_remedy_reservations_expiry
    ON remedy_reservations (expires_at) WHERE state = 'HELD';
CREATE INDEX idx_remedy_reservations_case ON remedy_reservations (support_case_id);

ALTER TABLE case_remedy_lines
    ADD CONSTRAINT fk_case_remedy_lines_reservation FOREIGN KEY (remedy_reservation_id)
        REFERENCES remedy_reservations (id);
--rollback ALTER TABLE case_remedy_lines DROP CONSTRAINT fk_case_remedy_lines_reservation;
--rollback DROP TABLE remedy_reservations;

--changeset ninggiangboy:028-42-remedy-budget-windows
-- The other kind of ceiling: not what a booking line can give back, but what an agent, a team, a
-- campaign or a market may spend on goodwill in a period. These limits exist to bound abuse and
-- mistake, and they must never be able to reduce a mandatory contractual entitlement -- which is why
-- a contractual line is not charged against a budget window at all.
CREATE TABLE remedy_budget_windows (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_scope                VARCHAR(24) NOT NULL,
    scope_key                   VARCHAR(128) NOT NULL,
    market_id                   UUID,
    legal_entity_id             UUID,

    window_from                 TIMESTAMPTZ NOT NULL,
    window_until                TIMESTAMPTZ NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    limit_amount_minor          BIGINT NOT NULL,
    reserved_amount_minor       BIGINT NOT NULL DEFAULT 0,
    consumed_amount_minor       BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    breach_behaviour            VARCHAR(24) NOT NULL,
    escalation_route            VARCHAR(64),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_remedy_budget_windows_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_remedy_budget_windows_legal_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT ck_remedy_budget_windows_scope CHECK (
        budget_scope IN ('PER_AGENT_DAY', 'PER_TEAM_DAY', 'PER_CASE', 'PER_USER_PERIOD',
                         'PER_CAMPAIGN', 'PER_MARKET_PERIOD', 'GLOBAL_PERIOD')
    ),
    CONSTRAINT ck_remedy_budget_windows_state CHECK (
        state IN ('OPEN', 'EXHAUSTED', 'CLOSED')
    ),
    CONSTRAINT ck_remedy_budget_windows_breach CHECK (
        breach_behaviour IN ('REFUSE', 'REQUIRE_APPROVAL', 'ESCALATE')
        AND (breach_behaviour = 'REFUSE' OR escalation_route IS NOT NULL)
    ),
    -- Reserved plus consumed can never exceed the limit. This is the invariant the whole table
    -- exists to hold, and holding it here means no service can forget to check it.
    CONSTRAINT ck_remedy_budget_windows_amounts CHECK (
        currency ~ '^[A-Z]{3}$'
        AND limit_amount_minor >= 0
        AND reserved_amount_minor >= 0 AND consumed_amount_minor >= 0
        AND reserved_amount_minor + consumed_amount_minor <= limit_amount_minor
    ),
    CONSTRAINT ck_remedy_budget_windows_interval CHECK (window_until > window_from),
    CONSTRAINT ck_remedy_budget_windows_row_version CHECK (version >= 0)
);

-- One window per scope, key, currency and period. Two overlapping windows for one agent-day means
-- the daily limit is whichever one the code happened to read.
CREATE UNIQUE INDEX uk_remedy_budget_windows_identity
    ON remedy_budget_windows (budget_scope, scope_key, currency, window_from);
CREATE INDEX idx_remedy_budget_windows_open
    ON remedy_budget_windows (budget_scope, scope_key, window_until) WHERE state = 'OPEN';

ALTER TABLE case_remedy_lines
    ADD CONSTRAINT fk_case_remedy_lines_budget FOREIGN KEY (remedy_budget_window_id)
        REFERENCES remedy_budget_windows (id);
--rollback ALTER TABLE case_remedy_lines DROP CONSTRAINT fk_case_remedy_lines_budget;
--rollback DROP TABLE remedy_budget_windows;

--changeset ninggiangboy:028-43-remedy-budget-consumptions
-- Append-only proof of what each remedy line took from a window, keyed so that a retry cannot
-- consume twice. The window carries the running total for speed; these rows carry the audit that the
-- total can be rebuilt from, which matters the first time the two disagree.
CREATE TABLE remedy_budget_consumptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    remedy_budget_window_id     UUID NOT NULL,
    case_remedy_line_id         UUID NOT NULL,
    support_case_id             UUID NOT NULL,
    entry_kind                  VARCHAR(16) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    authorized_by_account_holder_id UUID,
    approval_reference          VARCHAR(96),
    recorded_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_remedy_budget_consumptions_window FOREIGN KEY (remedy_budget_window_id)
        REFERENCES remedy_budget_windows (id),
    CONSTRAINT fk_remedy_budget_consumptions_line FOREIGN KEY (case_remedy_line_id)
        REFERENCES case_remedy_lines (id),
    CONSTRAINT fk_remedy_budget_consumptions_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_remedy_budget_consumptions_authorizer FOREIGN KEY (authorized_by_account_holder_id)
        REFERENCES account_holders (id),
    -- One entry of a kind per line and window. A replayed message must converge on the same total.
    CONSTRAINT uk_remedy_budget_consumptions_line
        UNIQUE (remedy_budget_window_id, case_remedy_line_id, entry_kind),
    CONSTRAINT ck_remedy_budget_consumptions_kind CHECK (
        entry_kind IN ('RESERVE', 'CONSUME', 'RELEASE', 'REVERSE')
    ),
    CONSTRAINT ck_remedy_budget_consumptions_amount CHECK (
        currency ~ '^[A-Z]{3}$' AND amount_minor > 0
    )
);

CREATE INDEX idx_remedy_budget_consumptions_window
    ON remedy_budget_consumptions (remedy_budget_window_id, recorded_at);
--rollback DROP TABLE remedy_budget_consumptions;

--changeset ninggiangboy:028-44-remedy-instructions
-- The request this domain sends to whoever actually owns the effect, and the only place the answer
-- lands. Support never writes a refund, a ledger posting or a payout release itself: it commits an
-- instruction with a stable identity, the receiving domain reauthorizes it against its own ceilings,
-- and its answer -- accepted, rejected, pending, unknown, or a committed result with the domain's own
-- identity -- comes back here. A lost response is queried by the same idempotency key; it is never
-- retried under a new one, because a second identity is how one refund becomes two.
CREATE TABLE remedy_instructions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_remedy_id              UUID NOT NULL,
    support_case_id             UUID NOT NULL,
    case_decision_id            UUID NOT NULL,
    instruction_version         INTEGER NOT NULL DEFAULT 1,

    target_domain               VARCHAR(24) NOT NULL,
    command_type                VARCHAR(48) NOT NULL,
    command_id                  VARCHAR(96) NOT NULL,
    idempotency_key             VARCHAR(96) NOT NULL,
    target_aggregate_type       VARCHAR(48),
    target_aggregate_id         UUID,
    expected_target_version     BIGINT,

    beneficiary_kind            VARCHAR(16) NOT NULL,
    beneficiary_account_holder_id UUID,
    currency                    VARCHAR(3),
    amount_minor                BIGINT,
    funder_allocation_digest    CHAR(64),
    source_line_references      TEXT[] NOT NULL DEFAULT '{}',
    tax_document_reference      VARCHAR(96),

    reason_code                 VARCHAR(48) NOT NULL,
    policy_reference            VARCHAR(96) NOT NULL,
    approval_digest             CHAR(64),
    requested_explanation_template_key VARCHAR(64),
    deadline_at                 TIMESTAMPTZ,

    state                       VARCHAR(16) NOT NULL DEFAULT 'PREPARED',
    dispatched_at               TIMESTAMPTZ,
    result_state                VARCHAR(16),
    result_reference            VARCHAR(128),
    result_aggregate_id         UUID,
    result_amount_minor         BIGINT,
    rejection_reason            VARCHAR(64),
    attempt_count               INTEGER NOT NULL DEFAULT 0,
    next_query_at               TIMESTAMPTZ,
    reconciled_at               TIMESTAMPTZ,
    correlation_id              UUID,
    causation_id                UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_remedy_instructions_remedy FOREIGN KEY (case_remedy_id)
        REFERENCES case_remedies (id),
    CONSTRAINT fk_remedy_instructions_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_remedy_instructions_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT fk_remedy_instructions_beneficiary FOREIGN KEY (beneficiary_account_holder_id)
        REFERENCES account_holders (id),
    -- One command identity in the whole system, and one idempotency key per receiving domain. These
    -- two uniqueness rules are the last defence against a duplicate credit.
    CONSTRAINT uk_remedy_instructions_command UNIQUE (command_id),
    CONSTRAINT uk_remedy_instructions_idempotency UNIQUE (target_domain, idempotency_key),
    CONSTRAINT ck_remedy_instructions_domain CHECK (
        target_domain IN ('PAYMENT', 'LEDGER', 'PAYOUT', 'BOOKING', 'CANCELLATION', 'INVENTORY',
                          'PRICING', 'IDENTITY', 'RISK', 'SUPPLY', 'STAY_OPERATIONS',
                          'COMMUNICATION')
    ),
    CONSTRAINT ck_remedy_instructions_state CHECK (
        state IN ('PREPARED', 'DISPATCHED', 'ACCEPTED', 'REJECTED', 'PENDING', 'UNKNOWN',
                  'SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_remedy_instructions_result_state CHECK (
        result_state IS NULL
            OR result_state IN ('ACCEPTED', 'REJECTED', 'PENDING', 'UNKNOWN', 'COMMITTED',
                                'PARTIAL')
    ),
    CONSTRAINT ck_remedy_instructions_beneficiary_kind CHECK (
        beneficiary_kind IN ('GUEST', 'HOST', 'PLATFORM', 'THIRD_PARTY')
        AND (beneficiary_kind IN ('GUEST', 'HOST')) = (beneficiary_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_remedy_instructions_amount CHECK (
        (amount_minor IS NULL) = (currency IS NULL)
        AND (amount_minor IS NULL OR amount_minor > 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND (result_amount_minor IS NULL OR result_amount_minor >= 0)
        AND (result_amount_minor IS NULL OR amount_minor IS NULL
             OR result_amount_minor <= amount_minor)
    ),
    -- Anything that moves money names the funding split it was authorized under and the approval it
    -- rests on. An instruction with an amount and no approval digest is an unbudgeted payment.
    CONSTRAINT ck_remedy_instructions_money_authorization CHECK (
        amount_minor IS NULL
            OR (funder_allocation_digest IS NOT NULL AND approval_digest IS NOT NULL)
    ),
    -- Once it leaves, it must have left at a recorded moment; before it leaves, nothing may claim an
    -- answer. "Succeeded" on an instruction that was never dispatched is a screen lying to a guest.
    CONSTRAINT ck_remedy_instructions_dispatch CHECK (
        (state = 'PREPARED') = (dispatched_at IS NULL)
        AND (dispatched_at IS NOT NULL OR result_state IS NULL)
    ),
    CONSTRAINT ck_remedy_instructions_rejection CHECK (
        (state = 'REJECTED') = (rejection_reason IS NOT NULL)
    ),
    -- A committed result names the receiving domain's own identity for it. Without that there is
    -- nothing to reconcile against and no way to prove the effect exists over there.
    CONSTRAINT ck_remedy_instructions_committed CHECK (
        state NOT IN ('SUCCEEDED', 'PARTIAL')
            OR (result_reference IS NOT NULL AND result_state IN ('COMMITTED', 'PARTIAL'))
    ),
    -- An unknown outcome owes a query. Retrying is querying: the identity never changes.
    CONSTRAINT ck_remedy_instructions_unknown CHECK (
        state NOT IN ('PENDING', 'UNKNOWN') OR next_query_at IS NOT NULL
    ),
    CONSTRAINT ck_remedy_instructions_expected_version CHECK (
        (target_aggregate_type IS NULL) = (target_aggregate_id IS NULL)
        AND (expected_target_version IS NULL OR target_aggregate_id IS NOT NULL)
        AND (expected_target_version IS NULL OR expected_target_version >= 0)
    ),
    CONSTRAINT ck_remedy_instructions_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_remedy_instructions_number CHECK (instruction_version > 0),
    CONSTRAINT ck_remedy_instructions_row_version CHECK (version >= 0)
);

CREATE INDEX idx_remedy_instructions_remedy ON remedy_instructions (case_remedy_id, state);
CREATE INDEX idx_remedy_instructions_case ON remedy_instructions (support_case_id, state);
CREATE INDEX idx_remedy_instructions_query
    ON remedy_instructions (next_query_at) WHERE state IN ('DISPATCHED', 'PENDING', 'UNKNOWN');
CREATE INDEX idx_remedy_instructions_deadline
    ON remedy_instructions (deadline_at)
    WHERE state NOT IN ('SUCCEEDED', 'REJECTED', 'FAILED', 'CANCELLED');
--rollback DROP TABLE remedy_instructions;

--changeset ninggiangboy:028-45-case-appeals
-- One appeal against one eligible decision. Intake acknowledges receipt without promising reversal,
-- and independence is a recorded property of the reviewer rather than an intention: the reviewer of
-- an appeal may not be the person whose decision is being appealed. A successful appeal produces a
-- superseding decision and new idempotent commands; it never erases the original or silently
-- reverses money, and a failed appeal never suppresses a market-required external complaint route.
CREATE TABLE case_appeals (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    challenged_decision_id      UUID NOT NULL,
    appeal_number               SMALLINT NOT NULL DEFAULT 1,

    appellant_account_holder_id UUID NOT NULL,
    appellant_role              VARCHAR(16) NOT NULL,
    representative_participant_id UUID,
    grounds_code                VARCHAR(48) NOT NULL,
    grounds_reference           VARCHAR(256),
    requested_outcome           VARCHAR(24) NOT NULL,
    new_evidence_item_ids       UUID[] NOT NULL DEFAULT '{}',
    locale                      VARCHAR(16) NOT NULL,

    submitted_at                TIMESTAMPTZ NOT NULL,
    deadline_policy_reference   VARCHAR(96) NOT NULL,
    submission_deadline_at      TIMESTAMPTZ,
    late_submission             BOOLEAN NOT NULL DEFAULT false,

    state                       VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
    eligibility_state           VARCHAR(16) NOT NULL DEFAULT 'UNASSESSED',
    ineligibility_reason        VARCHAR(48),
    independence_required       BOOLEAN NOT NULL DEFAULT true,
    reviewer_account_holder_id  UUID,
    original_decider_account_holder_id UUID,
    assigned_at                 TIMESTAMPTZ,

    outcome                     VARCHAR(16),
    resulting_decision_id       UUID,
    decided_at                  TIMESTAMPTZ,
    communicated_at             TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    external_complaint_route    VARCHAR(64),
    external_complaint_reference VARCHAR(128),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_appeals_case FOREIGN KEY (support_case_id) REFERENCES support_cases (id),
    CONSTRAINT fk_case_appeals_decision FOREIGN KEY (challenged_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT fk_case_appeals_appellant FOREIGN KEY (appellant_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_appeals_representative FOREIGN KEY (representative_participant_id)
        REFERENCES case_participants (id),
    CONSTRAINT fk_case_appeals_reviewer FOREIGN KEY (reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_appeals_original_decider FOREIGN KEY (original_decider_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_appeals_result FOREIGN KEY (resulting_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT uk_case_appeals_number UNIQUE (challenged_decision_id, appeal_number),
    CONSTRAINT ck_case_appeals_role CHECK (
        appellant_role IN ('GUEST', 'HOST', 'CO_HOST', 'THIRD_PARTY', 'REPRESENTATIVE')
    ),
    CONSTRAINT ck_case_appeals_requested_outcome CHECK (
        requested_outcome IN ('REVERSE', 'INCREASE_REMEDY', 'RECLASSIFY', 'REOPEN_INVESTIGATION',
                              'CORRECT_RECORD')
    ),
    CONSTRAINT ck_case_appeals_state CHECK (
        state IN ('SUBMITTED', 'ELIGIBILITY_REVIEW', 'INELIGIBLE', 'ASSIGNED', 'REVIEWING',
                  'DECIDED', 'COMMUNICATED', 'CLOSED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_case_appeals_eligibility CHECK (
        eligibility_state IN ('UNASSESSED', 'ELIGIBLE', 'INELIGIBLE')
        AND (eligibility_state = 'INELIGIBLE') = (ineligibility_reason IS NOT NULL)
        AND (state = 'INELIGIBLE') = (eligibility_state = 'INELIGIBLE')
    ),
    CONSTRAINT ck_case_appeals_outcome CHECK (
        outcome IS NULL OR outcome IN ('AFFIRMED', 'MODIFIED', 'REVERSED', 'REMANDED')
    ),
    -- Nobody reviews their own decision where independence is required. An appeal heard by the person
    -- who decided is a second copy of the first answer.
    CONSTRAINT ck_case_appeals_independence CHECK (
        NOT independence_required OR reviewer_account_holder_id IS NULL
            OR original_decider_account_holder_id IS NULL
            OR reviewer_account_holder_id <> original_decider_account_holder_id
    ),
    -- The appellant does not review their own appeal either.
    CONSTRAINT ck_case_appeals_reviewer_party CHECK (
        reviewer_account_holder_id IS NULL
            OR reviewer_account_holder_id <> appellant_account_holder_id
    ),
    -- A decided appeal has an outcome, and any outcome that changes anything is a new decision.
    -- Affirming needs no new decision; modifying or reversing without one is money moved by status.
    CONSTRAINT ck_case_appeals_decided CHECK (
        (state IN ('DECIDED', 'COMMUNICATED', 'CLOSED') AND eligibility_state = 'ELIGIBLE')
            = (outcome IS NOT NULL AND decided_at IS NOT NULL)
    ),
    CONSTRAINT ck_case_appeals_result_decision CHECK (
        outcome IS NULL OR outcome = 'AFFIRMED' OR resulting_decision_id IS NOT NULL
    ),
    -- An appeal that failed still leaves the external route open where the market requires one.
    CONSTRAINT ck_case_appeals_external_route CHECK (
        external_complaint_reference IS NULL OR external_complaint_route IS NOT NULL
    ),
    CONSTRAINT ck_case_appeals_assignment CHECK (
        (assigned_at IS NULL) = (reviewer_account_holder_id IS NULL)
    ),
    CONSTRAINT ck_case_appeals_times CHECK (
        (decided_at IS NULL OR decided_at >= submitted_at)
        AND (communicated_at IS NULL OR communicated_at >= decided_at)
        AND (closed_at IS NULL OR closed_at >= submitted_at)
    ),
    CONSTRAINT ck_case_appeals_number CHECK (appeal_number > 0),
    CONSTRAINT ck_case_appeals_row_version CHECK (version >= 0)
);

-- One live appeal per decision. A second open appeal on one decision is two reviews that can reach
-- two answers, and the participant would be entitled to both.
CREATE UNIQUE INDEX uk_case_appeals_live
    ON case_appeals (challenged_decision_id)
    WHERE state NOT IN ('CLOSED', 'WITHDRAWN', 'INELIGIBLE');
CREATE INDEX idx_case_appeals_case ON case_appeals (support_case_id, state);
CREATE INDEX idx_case_appeals_reviewer ON case_appeals (reviewer_account_holder_id, state);
--rollback DROP TABLE case_appeals;

--changeset ninggiangboy:028-46-case-quality-reviews
-- Sampled review of decisions, stratified rather than random-only, so that severity, amount, policy
-- exception, appeal, reversal and accessibility context are all represented. The findings are kept
-- as separate assessed dimensions because a review that produces one score cannot distinguish an
-- agent who was slow from one who cited no evidence, and only one of those is a quality problem
-- worth correcting by coaching.
CREATE TABLE case_quality_reviews (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    support_case_id             UUID NOT NULL,
    case_decision_id            UUID,
    sample_cohort               VARCHAR(32) NOT NULL,
    sample_stratum              VARCHAR(48) NOT NULL,
    sampling_policy_reference   VARCHAR(96) NOT NULL,

    reviewer_account_holder_id  UUID NOT NULL,
    reviewed_account_holder_id  UUID,
    reviewed_at                 TIMESTAMPTZ NOT NULL,

    evidence_citation_rating    VARCHAR(16) NOT NULL,
    classification_rating       VARCHAR(16) NOT NULL,
    policy_selection_rating     VARCHAR(16) NOT NULL,
    amount_accuracy_rating      VARCHAR(16) NOT NULL,
    authorization_rating        VARCHAR(16) NOT NULL,
    communication_rating        VARCHAR(16) NOT NULL,
    timeliness_rating           VARCHAR(16) NOT NULL,
    privacy_rating              VARCHAR(16) NOT NULL,
    downstream_completion_rating VARCHAR(16) NOT NULL,

    overall_outcome             VARCHAR(32) NOT NULL,
    findings_reference          VARCHAR(256),
    correction_kind             VARCHAR(24),
    correction_reference        VARCHAR(128),
    correction_due_at           TIMESTAMPTZ,
    disputed_by_reviewed_party  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_quality_reviews_case FOREIGN KEY (support_case_id)
        REFERENCES support_cases (id),
    CONSTRAINT fk_case_quality_reviews_decision FOREIGN KEY (case_decision_id)
        REFERENCES case_decisions (id),
    CONSTRAINT fk_case_quality_reviews_reviewer FOREIGN KEY (reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_case_quality_reviews_reviewed FOREIGN KEY (reviewed_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_case_quality_reviews_cohort CHECK (
        sample_cohort IN ('RANDOM', 'HIGH_SEVERITY', 'HIGH_AMOUNT', 'POLICY_EXCEPTION', 'APPEALED',
                          'REVERSED', 'REPEAT_CONTACT', 'ACCESSIBILITY_CONTEXT', 'NEW_AGENT',
                          'CUSTOMER_HARM')
    ),
    CONSTRAINT ck_case_quality_reviews_ratings CHECK (
        evidence_citation_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND classification_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND policy_selection_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND amount_accuracy_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND authorization_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND communication_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND timeliness_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND privacy_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
        AND downstream_completion_rating IN ('MEETS', 'MINOR_GAP', 'MAJOR_GAP', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_case_quality_reviews_outcome CHECK (
        overall_outcome IN ('SATISFACTORY', 'COACHING_REQUIRED', 'POLICY_DEFECT', 'TOOLING_DEFECT',
                            'RE_REVIEW_REQUIRED', 'SUPERSEDING_DECISION_REQUIRED')
    ),
    CONSTRAINT ck_case_quality_reviews_correction CHECK (
        (overall_outcome = 'SATISFACTORY') = (correction_kind IS NULL)
        AND (correction_kind IS NULL
             OR correction_kind IN ('COACHING', 'POLICY_CHANGE', 'TOOLING_TICKET', 'RE_REVIEW',
                                    'SUPERSEDING_DECISION'))
        AND (correction_kind IS NULL) = (correction_due_at IS NULL)
    ),
    -- Nobody reviews their own work, and a quality review must say whose work was reviewed whenever
    -- there was a person behind it.
    CONSTRAINT ck_case_quality_reviews_self CHECK (
        reviewed_account_holder_id IS NULL
            OR reviewed_account_holder_id <> reviewer_account_holder_id
    ),
    CONSTRAINT ck_case_quality_reviews_row_version CHECK (version >= 0)
);

-- One review per decision and cohort. Re-reviewing under a different cohort is legitimate; reviewing
-- the same decision twice under the same cohort is sampling until the answer is convenient.
CREATE UNIQUE INDEX uk_case_quality_reviews_sample
    ON case_quality_reviews (case_decision_id, sample_cohort) WHERE case_decision_id IS NOT NULL;
CREATE INDEX idx_case_quality_reviews_case ON case_quality_reviews (support_case_id);
CREATE INDEX idx_case_quality_reviews_correction
    ON case_quality_reviews (correction_due_at) WHERE correction_due_at IS NOT NULL;
--rollback DROP TABLE case_quality_reviews;

--changeset ninggiangboy:028-47-support-append-only splitStatements:false
-- Everything below is a record of something that happened: a state change, a reclassification, a
-- contact, a note, a transformation, a read, a citation, a provider statement, a budget movement, a
-- projected event. Editing any of them in place would rewrite the reason a decision was taken on
-- them. A correction is a new row that names the one it corrects.
--
-- One function serves all of them, because the rule is identical and eleven copies of it are eleven
-- places for it to drift apart.
CREATE FUNCTION support_record_append_only() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; row % cannot be % -- record a correcting row instead',
        TG_TABLE_NAME, OLD.id, lower(TG_OP)
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_transitions_append_only
    BEFORE UPDATE OR DELETE ON case_transitions
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_case_classification_history_append_only
    BEFORE UPDATE OR DELETE ON case_classification_history
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_case_contacts_append_only
    BEFORE UPDATE OR DELETE ON case_contacts
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_case_notes_append_only
    BEFORE UPDATE OR DELETE ON case_notes
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_case_timeline_entries_append_only
    BEFORE UPDATE OR DELETE ON case_timeline_entries
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_evidence_transformations_append_only
    BEFORE UPDATE OR DELETE ON evidence_transformations
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_evidence_access_log_append_only
    BEFORE UPDATE OR DELETE ON evidence_access_log
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_case_decision_findings_append_only
    BEFORE UPDATE OR DELETE ON case_decision_findings
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_external_claim_observations_append_only
    BEFORE UPDATE OR DELETE ON external_claim_observations
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();

CREATE TRIGGER trg_remedy_budget_consumptions_append_only
    BEFORE UPDATE OR DELETE ON remedy_budget_consumptions
    FOR EACH ROW EXECUTE FUNCTION support_record_append_only();
--rollback DROP TRIGGER trg_remedy_budget_consumptions_append_only ON remedy_budget_consumptions;
--rollback DROP TRIGGER trg_external_claim_observations_append_only ON external_claim_observations;
--rollback DROP TRIGGER trg_case_decision_findings_append_only ON case_decision_findings;
--rollback DROP TRIGGER trg_evidence_access_log_append_only ON evidence_access_log;
--rollback DROP TRIGGER trg_evidence_transformations_append_only ON evidence_transformations;
--rollback DROP TRIGGER trg_case_timeline_entries_append_only ON case_timeline_entries;
--rollback DROP TRIGGER trg_case_notes_append_only ON case_notes;
--rollback DROP TRIGGER trg_case_contacts_append_only ON case_contacts;
--rollback DROP TRIGGER trg_case_classification_history_append_only ON case_classification_history;
--rollback DROP TRIGGER trg_case_transitions_append_only ON case_transitions;
--rollback DROP FUNCTION support_record_append_only();

--changeset ninggiangboy:028-48-support-registry-immutability splitStatements:false
-- Six registries are named by rows that must keep their meaning: a decision names the support policy
-- and authority versions it was taken under, a finding names its investigation template, a remedy
-- names its catalogue entry, a coverage snapshot names its programme, and a case names the routing
-- policy that put it where it is. Editing a published version in place would silently change what
-- every one of those stored rows says. Publishing a new version is the only way to change the rules.
--
-- Closing an effective interval and retiring a version stay open: those are operations on the
-- version's lifecycle, not changes to what it means.
CREATE FUNCTION support_registry_freeze_published() RETURNS TRIGGER AS $$
DECLARE
    frozen JSONB;
    proposed JSONB;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            '% row % is published and cannot be deleted',
            TG_TABLE_NAME, OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    -- The lifecycle columns are compared out of both sides rather than copied across, so one
    -- function can serve six tables whose remaining columns have nothing in common.
    frozen := to_jsonb(OLD) - 'status' - 'effective_until' - 'updated_at' - 'version';
    proposed := to_jsonb(NEW) - 'status' - 'effective_until' - 'updated_at' - 'version';

    IF frozen IS DISTINCT FROM proposed THEN
        RAISE EXCEPTION
            '% row % is published; publish a new version instead of editing it',
            TG_TABLE_NAME, OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    -- A closed window may never be reopened or moved later. Rolling back selects an earlier version;
    -- it does not rewrite when this one applied.
    IF OLD.effective_until IS NOT NULL
       AND (NEW.effective_until IS NULL OR NEW.effective_until > OLD.effective_until) THEN
        RAISE EXCEPTION
            '% row % already ended at %; its window cannot be extended',
            TG_TABLE_NAME, OLD.id, OLD.effective_until USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_support_policy_versions_freeze
    BEFORE UPDATE OR DELETE ON support_policy_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION support_registry_freeze_published();

CREATE TRIGGER trg_investigation_template_versions_freeze
    BEFORE UPDATE OR DELETE ON investigation_template_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION support_registry_freeze_published();

CREATE TRIGGER trg_remedy_catalog_versions_freeze
    BEFORE UPDATE OR DELETE ON remedy_catalog_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION support_registry_freeze_published();

CREATE TRIGGER trg_authority_policy_versions_freeze
    BEFORE UPDATE OR DELETE ON authority_policy_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION support_registry_freeze_published();

CREATE TRIGGER trg_routing_policy_versions_freeze
    BEFORE UPDATE OR DELETE ON routing_policy_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION support_registry_freeze_published();

CREATE TRIGGER trg_protection_program_versions_freeze
    BEFORE UPDATE OR DELETE ON protection_program_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION support_registry_freeze_published();
--rollback DROP TRIGGER trg_protection_program_versions_freeze ON protection_program_versions;
--rollback DROP TRIGGER trg_routing_policy_versions_freeze ON routing_policy_versions;
--rollback DROP TRIGGER trg_authority_policy_versions_freeze ON authority_policy_versions;
--rollback DROP TRIGGER trg_remedy_catalog_versions_freeze ON remedy_catalog_versions;
--rollback DROP TRIGGER trg_investigation_template_versions_freeze ON investigation_template_versions;
--rollback DROP TRIGGER trg_support_policy_versions_freeze ON support_policy_versions;
--rollback DROP FUNCTION support_registry_freeze_published();

--changeset ninggiangboy:028-49-evidence-custody splitStatements:false
-- Evidence is immutable; custody and interpretation are additive. What a file is -- its bytes, where
-- they came from, when they were captured and received, and how much weight its provenance permits --
-- never changes. What may change is its lifecycle: scanning, visibility, retention, hold and
-- deletion. Letting the identity move would let somebody swap the artifact underneath a decision
-- that cited it, and the digest recorded in the decision would still appear to match.
CREATE FUNCTION case_evidence_items_freeze_identity() RETURNS TRIGGER AS $$
DECLARE
    candidate case_evidence_items%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'evidence % cannot be deleted; record a deletion state and keep the tombstone',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.scan_state := OLD.scan_state;
    candidate.state := OLD.state;
    candidate.visibility_scope := OLD.visibility_scope;
    candidate.sensitivity_class := OLD.sensitivity_class;
    candidate.retention_class := OLD.retention_class;
    candidate.retention_expires_at := OLD.retention_expires_at;
    candidate.legal_hold := OLD.legal_hold;
    candidate.legal_hold_reference := OLD.legal_hold_reference;
    candidate.legal_hold_until := OLD.legal_hold_until;
    candidate.deleted_at := OLD.deleted_at;
    candidate.deletion_method := OLD.deletion_method;
    candidate.damage_claim_id := OLD.damage_claim_id;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'evidence % is immutable; submit a new item and supersede this one',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    -- Deleted means deleted. Bringing an erased artifact back to available would be a claim that
    -- bytes we destroyed are once again in our possession.
    IF OLD.state = 'DELETED_OR_CRYPTO_ERASED' AND NEW.state <> OLD.state THEN
        RAISE EXCEPTION
            'evidence % was erased; it cannot return to %', OLD.id, NEW.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_evidence_items_freeze
    BEFORE UPDATE OR DELETE ON case_evidence_items
    FOR EACH ROW EXECUTE FUNCTION case_evidence_items_freeze_identity();

-- An assertion is what somebody said. It may be withdrawn, once, and nothing else about it moves;
-- correcting a statement is a new assertion naming the one it corrects.
CREATE FUNCTION case_assertions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate case_assertions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'assertion % cannot be deleted; withdraw it or record a correcting assertion',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.withdrawn_at := OLD.withdrawn_at;
    candidate.withdrawal_reason := OLD.withdrawal_reason;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'assertion % is immutable; record a correcting assertion instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.withdrawn_at IS NOT NULL AND NEW.withdrawn_at IS DISTINCT FROM OLD.withdrawn_at THEN
        RAISE EXCEPTION
            'assertion % was already withdrawn at %', OLD.id, OLD.withdrawn_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_assertions_freeze
    BEFORE UPDATE OR DELETE ON case_assertions
    FOR EACH ROW EXECUTE FUNCTION case_assertions_freeze();

-- A redaction records what was hidden. The review of it may be filled in once, by somebody who is
-- not the operator, and nothing else changes -- a redaction whose scope can be edited afterwards is
-- not evidence of what was disclosed.
CREATE FUNCTION evidence_redactions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate evidence_redactions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'redaction % cannot be deleted', OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.reviewed_by_account_holder_id := OLD.reviewed_by_account_holder_id;
    candidate.reviewed_at := OLD.reviewed_at;
    candidate.review_outcome := OLD.review_outcome;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'redaction % is immutable; create a new derivative instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.reviewed_at IS NOT NULL AND NEW.reviewed_at IS DISTINCT FROM OLD.reviewed_at THEN
        RAISE EXCEPTION
            'redaction % was already reviewed at %', OLD.id, OLD.reviewed_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evidence_redactions_freeze
    BEFORE UPDATE OR DELETE ON evidence_redactions
    FOR EACH ROW EXECUTE FUNCTION evidence_redactions_freeze();

-- A frozen manifest is what was sent. After it is frozen only its own lifecycle moves; the artifacts
-- it names and the digest over them are exactly what a provider or a regulator received.
CREATE FUNCTION evidence_disclosure_manifests_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate evidence_disclosure_manifests%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'manifest % is frozen and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.disclosed_at := OLD.disclosed_at;
    candidate.access_expires_at := OLD.access_expires_at;
    candidate.revoked_at := OLD.revoked_at;
    candidate.revocation_reason := OLD.revocation_reason;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'manifest % is frozen; raise a new manifest version instead of editing it', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_evidence_disclosure_manifests_freeze
    BEFORE UPDATE OR DELETE ON evidence_disclosure_manifests
    FOR EACH ROW WHEN (OLD.state <> 'DRAFT')
    EXECUTE FUNCTION evidence_disclosure_manifests_freeze();
--rollback DROP TRIGGER trg_evidence_disclosure_manifests_freeze ON evidence_disclosure_manifests;
--rollback DROP FUNCTION evidence_disclosure_manifests_freeze();
--rollback DROP TRIGGER trg_evidence_redactions_freeze ON evidence_redactions;
--rollback DROP FUNCTION evidence_redactions_freeze();
--rollback DROP TRIGGER trg_case_assertions_freeze ON case_assertions;
--rollback DROP FUNCTION case_assertions_freeze();
--rollback DROP TRIGGER trg_case_evidence_items_freeze ON case_evidence_items;
--rollback DROP FUNCTION case_evidence_items_freeze_identity();

--changeset ninggiangboy:028-50-case-decision-integrity splitStatements:false
-- A decision that has been communicated is a fact about the past. Its policy versions, digests,
-- reasoning and outcome are frozen; only its own lifecycle -- supersession, communication, an earlier
-- expiry -- may still move. An appeal, a correction, a late provider result or a recovery creates a
-- new decision naming this one.
CREATE FUNCTION case_decisions_freeze_effective() RETURNS TRIGGER AS $$
DECLARE
    candidate case_decisions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'decision % is effective and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.communicated_at := OLD.communicated_at;
    candidate.expires_at := OLD.expires_at;
    candidate.superseded_by_decision_id := OLD.superseded_by_decision_id;
    candidate.superseded_at := OLD.superseded_at;
    candidate.supersession_reason := OLD.supersession_reason;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'decision % is effective; issue a superseding decision instead of editing it', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- An expiry may be brought forward but never pushed out. Extending a decision's life after the
    -- fact is a new authorization wearing the old one's clothes.
    IF OLD.expires_at IS NOT NULL
       AND (NEW.expires_at IS NULL OR NEW.expires_at > OLD.expires_at) THEN
        RAISE EXCEPTION
            'decision % already expires at %; that cannot be extended', OLD.id, OLD.expires_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state = 'SUPERSEDED' AND NEW.state <> 'SUPERSEDED' THEN
        RAISE EXCEPTION
            'decision % was superseded and cannot become %', OLD.id, NEW.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_decisions_freeze
    BEFORE UPDATE OR DELETE ON case_decisions
    FOR EACH ROW WHEN (OLD.state IN ('EFFECTIVE', 'SUPERSEDED', 'VOIDED'))
    EXECUTE FUNCTION case_decisions_freeze_effective();

-- Maker-checker only works if the maker on the approval row is the person who actually made the
-- decision. Left to the application, a caller could name a convenient stranger as the maker and
-- approve their own work while satisfying every constraint on the row.
CREATE FUNCTION case_decision_approvals_bind_maker() RETURNS TRIGGER AS $$
DECLARE
    decision case_decisions%ROWTYPE;
BEGIN
    SELECT * INTO decision FROM case_decisions WHERE id = NEW.case_decision_id;

    IF decision.decided_by_account_holder_id IS DISTINCT FROM NEW.maker_account_holder_id THEN
        RAISE EXCEPTION
            'approval names maker % but decision % was made by %',
            NEW.maker_account_holder_id, decision.id, decision.decided_by_account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- An approval is of an exact digest. Approving a digest the decision does not currently carry is
    -- approving something that no longer exists.
    IF NEW.approved_decision_digest IS DISTINCT FROM decision.decision_digest THEN
        RAISE EXCEPTION
            'approval carries digest % but decision % currently reads %',
            NEW.approved_decision_digest, decision.id, decision.decision_digest
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_decision_approvals_bind_maker
    BEFORE INSERT ON case_decision_approvals
    FOR EACH ROW EXECUTE FUNCTION case_decision_approvals_bind_maker();

-- An approval may be invalidated and nothing else. A signature that can be edited afterwards is a
-- signature that can be moved onto a different document.
CREATE FUNCTION case_decision_approvals_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate case_decision_approvals%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'approval % cannot be deleted; invalidate it instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.invalidated_at := OLD.invalidated_at;
    candidate.invalidation_reason := OLD.invalidation_reason;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'approval % is immutable; record a new approval instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.invalidated_at IS NOT NULL THEN
        RAISE EXCEPTION
            'approval % was already invalidated at %', OLD.id, OLD.invalidated_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_decision_approvals_freeze
    BEFORE UPDATE OR DELETE ON case_decision_approvals
    FOR EACH ROW EXECUTE FUNCTION case_decision_approvals_freeze();

-- A decision becomes effective only if every approval standing against it still matches its current
-- digest and none of them refused. Covering INSERT matters as much as UPDATE: a decision written
-- straight into EFFECTIVE would otherwise skip the check entirely, which is the same shape of defect
-- migration 027 found in its policy activation guard.
CREATE FUNCTION case_decisions_guard_effective() RETURNS TRIGGER AS $$
DECLARE
    stale_count INTEGER;
    refusal_count INTEGER;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.state = 'EFFECTIVE' THEN
        RETURN NEW;
    END IF;

    SELECT count(*) INTO stale_count
    FROM case_decision_approvals a
    WHERE a.case_decision_id = NEW.id
      AND a.invalidated_at IS NULL
      AND a.outcome = 'APPROVED'
      AND a.approved_decision_digest <> NEW.decision_digest;

    IF stale_count > 0 THEN
        RAISE EXCEPTION
            'decision % has % standing approval(s) of a different digest; re-approve or invalidate them',
            NEW.id, stale_count USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT count(*) INTO refusal_count
    FROM case_decision_approvals a
    WHERE a.case_decision_id = NEW.id
      AND a.invalidated_at IS NULL
      AND a.outcome = 'REFUSED';

    IF refusal_count > 0 THEN
        RAISE EXCEPTION
            'decision % has % standing refusal(s) and cannot become effective',
            NEW.id, refusal_count USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_decisions_guard_effective
    BEFORE INSERT OR UPDATE ON case_decisions
    FOR EACH ROW WHEN (NEW.state = 'EFFECTIVE')
    EXECUTE FUNCTION case_decisions_guard_effective();
--rollback DROP TRIGGER trg_case_decisions_guard_effective ON case_decisions;
--rollback DROP FUNCTION case_decisions_guard_effective();
--rollback DROP TRIGGER trg_case_decision_approvals_freeze ON case_decision_approvals;
--rollback DROP FUNCTION case_decision_approvals_freeze();
--rollback DROP TRIGGER trg_case_decision_approvals_bind_maker ON case_decision_approvals;
--rollback DROP FUNCTION case_decision_approvals_bind_maker();
--rollback DROP TRIGGER trg_case_decisions_freeze ON case_decisions;
--rollback DROP FUNCTION case_decisions_freeze_effective();

--changeset ninggiangboy:028-51-offer-lifecycle splitStatements:false
-- An offer is a promise with a digest. Once it leaves the draft, its terms are what the other party
-- saw; changing them afterwards would make the digest they accepted a digest of something else. Only
-- the lifecycle moves, and a terminal state is terminal: an expired offer does not come back to life
-- because somebody reopened the screen.
CREATE FUNCTION case_offers_freeze_sent() RETURNS TRIGGER AS $$
DECLARE
    candidate case_offers%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'offer % has been sent and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.viewed_at := OLD.viewed_at;
    candidate.responded_at := OLD.responded_at;
    candidate.rejection_reason := OLD.rejection_reason;
    candidate.withdrawal_reason := OLD.withdrawal_reason;
    candidate.accepted_by_account_holder_id := OLD.accepted_by_account_holder_id;
    candidate.accepted_digest := OLD.accepted_digest;
    candidate.acceptance_authentication_reference := OLD.acceptance_authentication_reference;
    candidate.accepted_offer_version := OLD.accepted_offer_version;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'offer % was already sent; counter it with a new offer instead of editing it', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.state IN ('ACCEPTED', 'REJECTED', 'COUNTERED', 'EXPIRED', 'WITHDRAWN')
       AND NEW.state <> OLD.state THEN
        RAISE EXCEPTION
            'offer % already reached %; it cannot become %', OLD.id, OLD.state, NEW.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- Acceptance follows from an offer the recipient could actually see. Accepting a draft, or an
    -- offer already withdrawn, is agreement to something that was never open.
    IF NEW.state = 'ACCEPTED' AND OLD.state NOT IN ('SENT', 'VIEWED') THEN
        RAISE EXCEPTION
            'offer % is %; only a sent or viewed offer can be accepted', OLD.id, OLD.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_offers_freeze
    BEFORE UPDATE OR DELETE ON case_offers
    FOR EACH ROW WHEN (OLD.state <> 'DRAFT')
    EXECUTE FUNCTION case_offers_freeze_sent();

-- The lines are the terms. Appending one to an offer that has already been sent would change what
-- was offered without changing the offer row, which is the same defect covering only UPDATE would
-- leave open on any child of something that can freeze.
CREATE FUNCTION case_offer_lines_guard_sent() RETURNS TRIGGER AS $$
DECLARE
    offer_state VARCHAR(16);
    target UUID;
BEGIN
    target := CASE TG_OP WHEN 'DELETE' THEN OLD.case_offer_id ELSE NEW.case_offer_id END;
    SELECT state INTO offer_state FROM case_offers WHERE id = target;

    IF offer_state IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION
            'offer % is %; its lines cannot be changed (%)', target, offer_state, lower(TG_OP)
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_offer_lines_guard
    BEFORE INSERT OR UPDATE OR DELETE ON case_offer_lines
    FOR EACH ROW EXECUTE FUNCTION case_offer_lines_guard_sent();
--rollback DROP TRIGGER trg_case_offer_lines_guard ON case_offer_lines;
--rollback DROP FUNCTION case_offer_lines_guard_sent();
--rollback DROP TRIGGER trg_case_offers_freeze ON case_offers;
--rollback DROP FUNCTION case_offers_freeze_sent();

--changeset ninggiangboy:028-52-remedy-fidelity splitStatements:false
-- Three rules about money, each of which the application would otherwise have to remember every time.
--
-- First: the lines must add up to the remedy, in the remedy's own currency. A deferred constraint
-- trigger is the right shape because a remedy and its lines are written together and neither is
-- complete until the transaction ends.
CREATE FUNCTION case_remedy_lines_balance() RETURNS TRIGGER AS $$
DECLARE
    target UUID;
    remedy case_remedies%ROWTYPE;
    line_total BIGINT;
    foreign_currency INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'case_remedies' THEN
        target := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target := OLD.case_remedy_id;
    ELSE
        target := NEW.case_remedy_id;
    END IF;

    SELECT * INTO remedy FROM case_remedies WHERE id = target;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    -- Only an authorized remedy has to balance. A draft is allowed to be half-written.
    IF remedy.state IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING', 'REJECTED', 'EXPIRED') THEN
        RETURN NULL;
    END IF;

    IF NOT remedy.monetary THEN
        RETURN NULL;
    END IF;

    SELECT count(*) INTO foreign_currency
    FROM case_remedy_lines l
    WHERE l.case_remedy_id = target AND l.currency <> remedy.currency;

    IF foreign_currency > 0 THEN
        RAISE EXCEPTION
            'remedy % is in % but % of its lines are in another currency; cross-currency arithmetic needs an approved exchange decision',
            target, remedy.currency, foreign_currency USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT coalesce(sum(l.amount_minor), 0) INTO line_total
    FROM case_remedy_lines l WHERE l.case_remedy_id = target;

    IF line_total <> remedy.total_amount_minor THEN
        RAISE EXCEPTION
            'remedy % authorizes % % but its lines sum to %',
            target, remedy.total_amount_minor, remedy.currency, line_total
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_case_remedy_lines_balance
    AFTER INSERT OR UPDATE OR DELETE ON case_remedy_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION case_remedy_lines_balance();

CREATE CONSTRAINT TRIGGER trg_case_remedies_balance
    AFTER INSERT OR UPDATE ON case_remedies
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION case_remedy_lines_balance();

-- Second: a line may only name a funder the catalogue entry permits. The catalogue is where "who can
-- pay for this" was decided, and reading it at execution time is what makes that decision binding
-- rather than advisory.
CREATE FUNCTION case_remedy_lines_check_funder() RETURNS TRIGGER AS $$
DECLARE
    remedy case_remedies%ROWTYPE;
    catalog remedy_catalog_versions%ROWTYPE;
BEGIN
    SELECT * INTO remedy FROM case_remedies WHERE id = NEW.case_remedy_id;
    SELECT * INTO catalog FROM remedy_catalog_versions WHERE id = remedy.remedy_catalog_version_id;

    IF NOT (NEW.funder_kind = ANY (catalog.permitted_funders)) THEN
        RAISE EXCEPTION
            'remedy % (%) does not permit a %-funded line; permitted funders are %',
            remedy.id, catalog.remedy_code, NEW.funder_kind, catalog.permitted_funders
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF catalog.monetary AND NOT (NEW.currency = ANY (catalog.permitted_currencies)) THEN
        RAISE EXCEPTION
            'remedy % (%) does not permit currency %; permitted currencies are %',
            remedy.id, catalog.remedy_code, NEW.currency, catalog.permitted_currencies
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_remedy_lines_funder
    BEFORE INSERT OR UPDATE ON case_remedy_lines
    FOR EACH ROW EXECUTE FUNCTION case_remedy_lines_check_funder();

-- Third: an instruction may not exist before its remedy was approved, and once dispatched its
-- identity is frozen. A retry after a timeout queries the same key; it never leaves under a new one,
-- because a second identity downstream is how one refund quietly becomes two.
CREATE FUNCTION remedy_instructions_guard() RETURNS TRIGGER AS $$
DECLARE
    remedy case_remedies%ROWTYPE;
    candidate remedy_instructions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'instruction % cannot be deleted; cancel it instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP = 'INSERT' THEN
        SELECT * INTO remedy FROM case_remedies WHERE id = NEW.case_remedy_id;
        IF remedy.state IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING', 'REJECTED', 'EXPIRED') THEN
            RAISE EXCEPTION
                'remedy % is %; nothing may be instructed before it is approved',
                remedy.id, remedy.state USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.dispatched_at IS NULL THEN
        RETURN NEW;
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.result_state := OLD.result_state;
    candidate.result_reference := OLD.result_reference;
    candidate.result_aggregate_id := OLD.result_aggregate_id;
    candidate.result_amount_minor := OLD.result_amount_minor;
    candidate.rejection_reason := OLD.rejection_reason;
    candidate.attempt_count := OLD.attempt_count;
    candidate.next_query_at := OLD.next_query_at;
    candidate.reconciled_at := OLD.reconciled_at;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'instruction % was dispatched as % to %; raise a new instruction version instead of editing it',
            OLD.id, OLD.command_id, OLD.target_domain USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_remedy_instructions_guard
    BEFORE INSERT OR UPDATE OR DELETE ON remedy_instructions
    FOR EACH ROW EXECUTE FUNCTION remedy_instructions_guard();
--rollback DROP TRIGGER trg_remedy_instructions_guard ON remedy_instructions;
--rollback DROP FUNCTION remedy_instructions_guard();
--rollback DROP TRIGGER trg_case_remedy_lines_funder ON case_remedy_lines;
--rollback DROP FUNCTION case_remedy_lines_check_funder();
--rollback DROP TRIGGER trg_case_remedies_balance ON case_remedies;
--rollback DROP TRIGGER trg_case_remedy_lines_balance ON case_remedy_lines;
--rollback DROP FUNCTION case_remedy_lines_balance();

--changeset ninggiangboy:028-53-claim-and-provider-saga splitStatements:false
-- A claim's accepted total is the sum of what was accepted on its lines, in one currency. Without
-- this the header and the itemisation can disagree, and the participant is shown one number while
-- the instruction carries another.
CREATE FUNCTION damage_claim_items_balance() RETURNS TRIGGER AS $$
DECLARE
    target UUID;
    claim damage_claims%ROWTYPE;
    accepted_total BIGINT;
    unpriced INTEGER;
    foreign_currency INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'damage_claims' THEN
        target := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target := OLD.damage_claim_id;
    ELSE
        target := NEW.damage_claim_id;
    END IF;

    SELECT * INTO claim FROM damage_claims WHERE id = target;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    IF claim.state NOT IN ('DECIDED', 'PAYMENT_PENDING', 'RECOVERY_PENDING', 'SETTLED') THEN
        RETURN NULL;
    END IF;

    SELECT count(*) INTO foreign_currency
    FROM damage_claim_items i WHERE i.damage_claim_id = target AND i.currency <> claim.requested_currency;

    IF foreign_currency > 0 THEN
        RAISE EXCEPTION
            'claim % is in % but % of its items are in another currency',
            target, claim.requested_currency, foreign_currency USING ERRCODE = 'restrict_violation';
    END IF;

    -- Deciding a claim means deciding every line of it. An item left unpriced is a loss the claimant
    -- was never told about either way.
    SELECT count(*) INTO unpriced
    FROM damage_claim_items i
    WHERE i.damage_claim_id = target AND i.accepted_amount_minor IS NULL;

    IF unpriced > 0 THEN
        RAISE EXCEPTION
            'claim % is decided but % of its items have no accepted amount', target, unpriced
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT coalesce(sum(i.accepted_amount_minor), 0) INTO accepted_total
    FROM damage_claim_items i WHERE i.damage_claim_id = target;

    IF accepted_total <> claim.accepted_total_minor THEN
        RAISE EXCEPTION
            'claim % accepted % % but its items sum to %',
            target, claim.accepted_total_minor, claim.requested_currency, accepted_total
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_damage_claim_items_balance
    AFTER INSERT OR UPDATE OR DELETE ON damage_claim_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION damage_claim_items_balance();

CREATE CONSTRAINT TRIGGER trg_damage_claims_balance
    AFTER INSERT OR UPDATE ON damage_claims
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION damage_claim_items_balance();

-- A provider's answer may arrive late. An observation whose sequence is behind what the claim has
-- already applied must not be allowed to claim it was applied, because applying it would move the
-- claim backwards -- turning a recorded denial into a pending review, or an approved amount into an
-- older, smaller one. The observation is still kept: a contradictory provider message is evidence
-- about the provider.
CREATE FUNCTION external_claim_observations_reject_stale() RETURNS TRIGGER AS $$
DECLARE
    current_sequence BIGINT;
BEGIN
    SELECT observation_sequence INTO current_sequence
    FROM external_claims WHERE id = NEW.external_claim_id;

    IF NEW.applied AND NEW.observation_sequence <= current_sequence THEN
        RAISE EXCEPTION
            'observation % is behind claim % at sequence %; record it as a stale regression instead of applying it',
            NEW.observation_sequence, NEW.external_claim_id, current_sequence
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_external_claim_observations_stale
    BEFORE INSERT ON external_claim_observations
    FOR EACH ROW EXECUTE FUNCTION external_claim_observations_reject_stale();

-- The stable provider key is written before the first call and never changes. Changing it after a
-- submission has gone out would orphan whatever the provider opened under the old one, and the only
-- way to find it again would be to ask a human to search the provider's portal.
CREATE FUNCTION external_claims_freeze_key() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'external claim % cannot be deleted; withdraw it instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.provider_claim_key <> OLD.provider_claim_key
       OR NEW.provider_account_id <> OLD.provider_account_id THEN
        RAISE EXCEPTION
            'claim % was submitted under provider key %; it cannot be re-keyed',
            OLD.id, OLD.provider_claim_key USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.coverage_snapshot_id <> OLD.coverage_snapshot_id
       OR NEW.protection_program_version_id <> OLD.protection_program_version_id THEN
        RAISE EXCEPTION
            'claim % was submitted against a fixed coverage snapshot; it cannot be re-based', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_external_claims_freeze_key
    BEFORE UPDATE OR DELETE ON external_claims
    FOR EACH ROW EXECUTE FUNCTION external_claims_freeze_key();

-- A submission is what was sent. Only the outcome of the call moves afterwards.
CREATE FUNCTION external_claim_submissions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate external_claim_submissions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'submission % cannot be deleted', OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.dispatched_at := OLD.dispatched_at;
    candidate.outcome := OLD.outcome;
    candidate.response_digest := OLD.response_digest;
    candidate.response_reference := OLD.response_reference;
    candidate.failure_classification := OLD.failure_classification;
    candidate.next_query_at := OLD.next_query_at;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'submission % is frozen; raise a new submission version instead of editing it', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_external_claim_submissions_freeze
    BEFORE UPDATE OR DELETE ON external_claim_submissions
    FOR EACH ROW EXECUTE FUNCTION external_claim_submissions_freeze();
--rollback DROP TRIGGER trg_external_claim_submissions_freeze ON external_claim_submissions;
--rollback DROP FUNCTION external_claim_submissions_freeze();
--rollback DROP TRIGGER trg_external_claims_freeze_key ON external_claims;
--rollback DROP FUNCTION external_claims_freeze_key();
--rollback DROP TRIGGER trg_external_claim_observations_stale ON external_claim_observations;
--rollback DROP FUNCTION external_claim_observations_reject_stale();
--rollback DROP TRIGGER trg_damage_claims_balance ON damage_claims;
--rollback DROP TRIGGER trg_damage_claim_items_balance ON damage_claim_items;
--rollback DROP FUNCTION damage_claim_items_balance();

--changeset ninggiangboy:028-54-case-closure-predicate splitStatements:false
-- Closure never hides unresolved work. A status is the cheapest thing in the system to change, and
-- closing a case is exactly the moment somebody would want to change one: the queue looks better,
-- the handling time drops, and the pending refund, the open provider deadline and the unanswered
-- appeal all become invisible. So the predicate is evaluated here rather than in whichever service
-- happens to be closing the case, and an exception is permitted only with a named owner and a date.
--
-- Covering INSERT as well as UPDATE matters: a case written straight into CLOSED would otherwise
-- skip every check, which is the defect shape migration 022 found and migration 027 found again.
CREATE FUNCTION support_cases_guard_closure() RETURNS TRIGGER AS $$
DECLARE
    open_remedies INTEGER;
    open_instructions INTEGER;
    open_provider INTEGER;
    open_appeals INTEGER;
    has_exception BOOLEAN;
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.state = 'CLOSED' THEN
        RETURN NEW;
    END IF;

    -- Immediate safety is never an exception anybody may own away. Either the escalation finished or
    -- a named route holds it; a case cannot close while somebody is still being screened.
    IF NEW.safety_state IN ('SCREENING', 'ESCALATED') THEN
        RAISE EXCEPTION
            'case % cannot close while safety is %; complete the escalation or hand it off',
            NEW.id, NEW.safety_state USING ERRCODE = 'restrict_violation';
    END IF;

    has_exception := NEW.closure_exception_owner_account_holder_id IS NOT NULL
        AND NEW.closure_exception_deadline_at IS NOT NULL;

    SELECT count(*) INTO open_remedies
    FROM case_remedies r
    WHERE r.support_case_id = NEW.id
      AND r.state IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING', 'APPROVED', 'INSTRUCTION_PENDING',
                      'EXECUTING', 'UNKNOWN', 'RECONCILING');

    SELECT count(*) INTO open_instructions
    FROM remedy_instructions i
    WHERE i.support_case_id = NEW.id
      AND i.state IN ('PREPARED', 'DISPATCHED', 'ACCEPTED', 'PENDING', 'UNKNOWN');

    SELECT count(*) INTO open_provider
    FROM external_claims c
    WHERE c.support_case_id = NEW.id
      AND c.state IN ('LOCAL_APPROVED', 'SUBMISSION_QUEUED', 'SUBMITTING', 'SUBMITTED', 'UNKNOWN',
                      'PROVIDER_REVIEW', 'INFO_REQUIRED', 'PAYMENT_PENDING');

    SELECT count(*) INTO open_appeals
    FROM case_appeals a
    WHERE a.support_case_id = NEW.id
      AND a.state IN ('SUBMITTED', 'ELIGIBILITY_REVIEW', 'ASSIGNED', 'REVIEWING', 'DECIDED');

    -- An open appeal is never closeable by exception: the participant is owed an answer, and a
    -- closed case is what a support desk shows them instead of one.
    IF open_appeals > 0 THEN
        RAISE EXCEPTION
            'case % has % open appeal(s) and cannot close', NEW.id, open_appeals
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF (open_remedies > 0 OR open_instructions > 0 OR open_provider > 0) AND NOT has_exception THEN
        RAISE EXCEPTION
            'case % has % unfinished remedy(s), % in-flight instruction(s) and % open provider claim(s); name an exception owner and a deadline or finish them',
            NEW.id, open_remedies, open_instructions, open_provider
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_support_cases_guard_closure
    BEFORE INSERT OR UPDATE ON support_cases
    FOR EACH ROW WHEN (NEW.state = 'CLOSED')
    EXECUTE FUNCTION support_cases_guard_closure();

-- Closure is historically terminal. Reopening preserves it by starting a new episode, so a case that
-- leaves CLOSED without advancing its episode would be rewriting the closure rather than recording
-- that the problem came back.
CREATE FUNCTION support_cases_guard_reopen() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.lifecycle_episode <= OLD.lifecycle_episode THEN
        RAISE EXCEPTION
            'case % was closed in episode %; reopening must start a new episode',
            OLD.id, OLD.lifecycle_episode USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.closed_at IS DISTINCT FROM OLD.closed_at THEN
        RAISE EXCEPTION
            'case % closed at %; that closure cannot be erased by reopening',
            OLD.id, OLD.closed_at USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_support_cases_guard_reopen
    BEFORE UPDATE ON support_cases
    FOR EACH ROW WHEN (OLD.state = 'CLOSED' AND NEW.state <> 'CLOSED')
    EXECUTE FUNCTION support_cases_guard_reopen();

-- Severity represents current harm. It may rise freely; lowering it is an authorized act that must
-- name its reason, and safety-critical work may not be de-escalated while the safety route is still
-- open. A model raising urgency is fine; a model lowering somebody's declared safety concern is not.
CREATE FUNCTION support_cases_guard_severity() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.severity_rank > OLD.severity_rank THEN
        IF NEW.severity_change_reason IS NULL THEN
            RAISE EXCEPTION
                'case % is being lowered from % to % without a reason', OLD.id, OLD.severity,
                NEW.severity USING ERRCODE = 'restrict_violation';
        END IF;

        IF OLD.severity = 'S0_SAFETY_CRITICAL'
           AND NEW.safety_state NOT IN ('RESOLVED', 'HANDED_OFF') THEN
            RAISE EXCEPTION
                'case % is safety-critical and its safety state is %; it cannot be de-escalated yet',
                OLD.id, NEW.safety_state USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_support_cases_guard_severity
    BEFORE UPDATE ON support_cases
    FOR EACH ROW WHEN (NEW.severity_rank > OLD.severity_rank)
    EXECUTE FUNCTION support_cases_guard_severity();
--rollback DROP TRIGGER trg_support_cases_guard_severity ON support_cases;
--rollback DROP FUNCTION support_cases_guard_severity();
--rollback DROP TRIGGER trg_support_cases_guard_reopen ON support_cases;
--rollback DROP FUNCTION support_cases_guard_reopen();
--rollback DROP TRIGGER trg_support_cases_guard_closure ON support_cases;
--rollback DROP FUNCTION support_cases_guard_closure();

--changeset ninggiangboy:028-55-work-item-fencing splitStatements:false
-- A lease token is monotonic per work item. This is what makes a crashed worker recoverable and a
-- stale one harmless: the crashed worker's lease expires, the next claim takes a higher token, and
-- when the original wakes up its token is behind and every side effect it attempts can be refused.
-- Reissuing a token that was already used would make the two indistinguishable.
CREATE FUNCTION work_item_leases_monotonic_token() RETURNS TRIGGER AS $$
DECLARE
    highest BIGINT;
BEGIN
    SELECT max(fencing_token) INTO highest
    FROM work_item_leases WHERE case_work_item_id = NEW.case_work_item_id;

    IF highest IS NOT NULL AND NEW.fencing_token <= highest THEN
        RAISE EXCEPTION
            'work item % has already issued token %; % would not be newer',
            NEW.case_work_item_id, highest, NEW.fencing_token
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_item_leases_monotonic
    BEFORE INSERT ON work_item_leases
    FOR EACH ROW EXECUTE FUNCTION work_item_leases_monotonic_token();

-- A released lease is history. Renewing it or moving its owner afterwards would let a worker that
-- already handed the work back carry on as though it still held it.
CREATE FUNCTION work_item_leases_freeze_released() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'lease % was released and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RAISE EXCEPTION
        'lease % was released at % for %; acquire a new lease instead',
        OLD.id, OLD.released_at, OLD.release_reason USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_item_leases_freeze
    BEFORE UPDATE OR DELETE ON work_item_leases
    FOR EACH ROW WHEN (OLD.released_at IS NOT NULL)
    EXECUTE FUNCTION work_item_leases_freeze_released();

-- Completing a work item is a side effect, and a side effect verifies the fence. An item cannot be
-- completed by somebody who is not its current lease holder, which is the difference between a
-- worker finishing its own work and a stale worker finalising somebody else's.
CREATE FUNCTION case_work_items_guard_completion() RETURNS TRIGGER AS $$
DECLARE
    holder UUID;
BEGIN
    SELECT owner_account_holder_id INTO holder
    FROM work_item_leases
    WHERE case_work_item_id = NEW.id AND released_at IS NULL;

    IF holder IS NOT NULL AND NEW.completed_by_account_holder_id IS DISTINCT FROM holder THEN
        RAISE EXCEPTION
            'work item % is leased to %; % cannot complete it',
            NEW.id, holder, NEW.completed_by_account_holder_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_work_items_guard_completion
    BEFORE UPDATE ON case_work_items
    FOR EACH ROW WHEN (NEW.state = 'COMPLETED' AND OLD.state <> 'COMPLETED')
    EXECUTE FUNCTION case_work_items_guard_completion();
--rollback DROP TRIGGER trg_case_work_items_guard_completion ON case_work_items;
--rollback DROP FUNCTION case_work_items_guard_completion();
--rollback DROP TRIGGER trg_work_item_leases_freeze ON work_item_leases;
--rollback DROP FUNCTION work_item_leases_freeze_released();
--rollback DROP TRIGGER trg_work_item_leases_monotonic ON work_item_leases;
--rollback DROP FUNCTION work_item_leases_monotonic_token();

--changeset ninggiangboy:028-56-remedy-approval-immutability splitStatements:false
-- Approval binds the exact terms that were approved. Without this, an approved remedy could have its
-- amount doubled, be re-pointed at a different catalogue entry, and then be marked rejected -- all
-- while its approval digest sat unchanged and its instruction had already moved the money. Probing
-- found exactly that sequence: the digest column recorded what was approved but nothing stopped the
-- row from becoming something else afterwards, which is what "material changes invalidate approval"
-- has to mean if it is to be more than a sentence in a document.
--
-- What stays open is the execution story: how much actually moved, when, whether the outcome is
-- unknown, who owns the exception, and whether a later authorized decision reversed it.
CREATE FUNCTION case_remedies_freeze_approved() RETURNS TRIGGER AS $$
DECLARE
    candidate case_remedies%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'remedy % was approved and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.state := OLD.state;
    candidate.executed_amount_minor := OLD.executed_amount_minor;
    candidate.execution_started_at := OLD.execution_started_at;
    candidate.execution_completed_at := OLD.execution_completed_at;
    candidate.unknown_since := OLD.unknown_since;
    candidate.next_reconciliation_at := OLD.next_reconciliation_at;
    candidate.exception_owner_account_holder_id := OLD.exception_owner_account_holder_id;
    candidate.exception_deadline_at := OLD.exception_deadline_at;
    candidate.reverses_remedy_id := OLD.reverses_remedy_id;
    candidate.reversal_decision_id := OLD.reversal_decision_id;
    candidate.expires_at := OLD.expires_at;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'remedy % was approved under digest %; propose a new remedy instead of editing this one',
            OLD.id, OLD.approval_digest USING ERRCODE = 'restrict_violation';
    END IF;

    -- Rejection is a pre-approval outcome. Undoing an authorized remedy is a new decision that
    -- reverses or recovers it, because by this point somebody downstream may already have paid.
    IF NEW.state = 'REJECTED' THEN
        RAISE EXCEPTION
            'remedy % is %; an authorized remedy is reversed by a new decision, never rejected',
            OLD.id, OLD.state USING ERRCODE = 'restrict_violation';
    END IF;

    -- An approved remedy may still lapse unused, but only while nothing has been instructed.
    IF NEW.state = 'EXPIRED' AND OLD.state <> 'APPROVED' THEN
        RAISE EXCEPTION
            'remedy % is %; it is past the point where it can simply expire', OLD.id, OLD.state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_remedies_freeze
    BEFORE UPDATE OR DELETE ON case_remedies
    FOR EACH ROW WHEN (OLD.state NOT IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING', 'REJECTED'))
    EXECUTE FUNCTION case_remedies_freeze_approved();

-- The lines are the terms. Appending one to an approved remedy would change what was authorized
-- without changing the remedy row at all -- the same defect covering only UPDATE leaves open on any
-- child of something that can freeze, which migration 022 found on ledger postings and migration 027
-- found again on risk evidence.
CREATE FUNCTION case_remedy_lines_guard_approved() RETURNS TRIGGER AS $$
DECLARE
    target UUID;
    remedy_state VARCHAR(24);
BEGIN
    IF TG_OP = 'DELETE' THEN
        target := OLD.case_remedy_id;
    ELSE
        target := NEW.case_remedy_id;
    END IF;

    SELECT state INTO remedy_state FROM case_remedies WHERE id = target;

    IF remedy_state NOT IN ('DRAFT', 'PROPOSED', 'APPROVAL_PENDING') THEN
        RAISE EXCEPTION
            'remedy % is %; its lines cannot be changed (%)', target, remedy_state, lower(TG_OP)
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE TG_OP WHEN 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_case_remedy_lines_guard
    BEFORE INSERT OR UPDATE OR DELETE ON case_remedy_lines
    FOR EACH ROW EXECUTE FUNCTION case_remedy_lines_guard_approved();
--rollback DROP TRIGGER trg_case_remedy_lines_guard ON case_remedy_lines;
--rollback DROP FUNCTION case_remedy_lines_guard_approved();
--rollback DROP TRIGGER trg_case_remedies_freeze ON case_remedies;
--rollback DROP FUNCTION case_remedies_freeze_approved();
