--liquibase formatted sql

-- A published host receives money and provides regulated accommodation. Before either is allowed the
-- platform has to establish who the seller is, where they operate, whether they are eligible there,
-- and where settlement may go. host_profiles from migration 001 records a bio and an identity status
-- enum; it cannot carry legal identity, beneficial owners, screening outcomes, permits, or a verified
-- payout destination, and it cannot explain why a capability was granted.
--
-- The central rule from D02 is that provider output is evidence, not a decision. A vendor returning
-- "identity matched" does not grant CAN_PUBLISH; a platform policy reads that evidence and decides.
-- The tables are shaped around that split: verification_documents and screening_checks hold what was
-- observed, eligibility_decisions holds what the platform concluded and under which policy version,
-- and the resulting authority is written to capability_grants from migration 014.
--
-- The second rule is that failing to receive a payout must never erase entitlement. Payout
-- destinations therefore live here as verifiable claims about where money may go; what a host is
-- owed is the ledger's business in migration 022 and is unaffected by a destination being rejected.
--
-- Identity documents are held by reference with an explicit retention instant. The bytes live in
-- encrypted object storage behind the D00 secret boundary; a database dump is not a passport scan.

--changeset ninggiangboy:015-01-host-legal-profiles
CREATE TABLE host_legal_profiles (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id       UUID NOT NULL,
    profile_type            VARCHAR(16) NOT NULL,
    legal_name              VARCHAR(255) NOT NULL,
    legal_name_digest       CHAR(64) NOT NULL,
    date_of_birth           DATE,
    incorporation_date      DATE,
    registration_number     VARCHAR(128),
    registered_country      VARCHAR(2) NOT NULL,
    address_reference       VARCHAR(255),
    market_code             VARCHAR(2) NOT NULL,
    lifecycle_state         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    retain_until            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_host_legal_profiles_holder UNIQUE (account_holder_id),
    CONSTRAINT fk_host_legal_profiles_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_legal_profiles_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_host_legal_profiles_type CHECK (profile_type IN ('INDIVIDUAL', 'BUSINESS')),
    -- An individual has a date of birth and no incorporation date; a business is the reverse. Mixing
    -- them is how an age check silently passes on a company that has no age.
    CONSTRAINT ck_host_legal_profiles_individual CHECK (
        profile_type <> 'INDIVIDUAL'
        OR (date_of_birth IS NOT NULL AND incorporation_date IS NULL)
    ),
    CONSTRAINT ck_host_legal_profiles_business CHECK (
        profile_type <> 'BUSINESS'
        OR (incorporation_date IS NOT NULL AND registration_number IS NOT NULL
            AND date_of_birth IS NULL)
    ),
    CONSTRAINT ck_host_legal_profiles_country CHECK (registered_country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_host_legal_profiles_name_digest CHECK (legal_name_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_host_legal_profiles_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'SUBMITTED', 'VERIFIED', 'REJECTED', 'EXPIRED')
    ),
    CONSTRAINT ck_host_legal_profiles_version CHECK (version >= 0)
);

CREATE INDEX idx_host_legal_profiles_market_state
    ON host_legal_profiles (market_code, lifecycle_state);

-- Matching a screening hit against a name must not require scanning plaintext names.
CREATE INDEX idx_host_legal_profiles_name_digest ON host_legal_profiles (legal_name_digest);
--rollback DROP TABLE host_legal_profiles;

--changeset ninggiangboy:015-02-beneficial-owners
CREATE TABLE beneficial_owners (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id UUID NOT NULL,
    full_name             VARCHAR(255) NOT NULL,
    full_name_digest      CHAR(64) NOT NULL,
    date_of_birth         DATE NOT NULL,
    nationality           VARCHAR(2),
    ownership_basis_points INTEGER,
    is_control_person     BOOLEAN NOT NULL DEFAULT false,
    address_reference     VARCHAR(255),
    declared_at           TIMESTAMPTZ NOT NULL,
    superseded_by         UUID,
    retain_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_beneficial_owners_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_beneficial_owners_superseded FOREIGN KEY (superseded_by)
        REFERENCES beneficial_owners (id),
    CONSTRAINT ck_beneficial_owners_nationality CHECK (
        nationality IS NULL OR nationality ~ '^[A-Z]{2}$'
    ),
    -- Basis points rather than a percentage: 25% is 2500 exactly, with no floating-point rounding to
    -- argue about when a threshold decides whether someone must be screened at all.
    CONSTRAINT ck_beneficial_owners_ownership CHECK (
        ownership_basis_points IS NULL
        OR (ownership_basis_points > 0 AND ownership_basis_points <= 10000)
    ),
    -- A declared owner is on the register because of ownership, control, or both. Neither would mean
    -- the row explains nothing about why this person was declared.
    CONSTRAINT ck_beneficial_owners_basis CHECK (
        ownership_basis_points IS NOT NULL OR is_control_person = true
    ),
    CONSTRAINT ck_beneficial_owners_name_digest CHECK (full_name_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_beneficial_owners_version CHECK (version >= 0)
);

CREATE INDEX idx_beneficial_owners_profile
    ON beneficial_owners (host_legal_profile_id)
    WHERE superseded_by IS NULL;

CREATE INDEX idx_beneficial_owners_name_digest ON beneficial_owners (full_name_digest);
--rollback DROP TABLE beneficial_owners;

--changeset ninggiangboy:015-03-verification-cases
CREATE TABLE verification_cases (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id   UUID NOT NULL,
    case_type               VARCHAR(24) NOT NULL,
    status                  VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    provider_account_key    VARCHAR(96),
    provider_account_version SMALLINT,
    opened_at               TIMESTAMPTZ NOT NULL,
    submitted_at            TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    outcome                 VARCHAR(16),
    outcome_reason_code     VARCHAR(64),
    manual_reviewer_id      UUID,
    manual_reviewed_at      TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_verification_cases_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT ck_verification_cases_type CHECK (
        case_type IN ('IDENTITY', 'BUSINESS', 'LIVENESS', 'ADDRESS', 'PAYOUT_OWNERSHIP', 'REFRESH')
    ),
    CONSTRAINT ck_verification_cases_status CHECK (
        status IN ('OPEN', 'AWAITING_INPUT', 'AWAITING_PROVIDER', 'MANUAL_REVIEW', 'COMPLETED',
                   'ABANDONED')
    ),
    CONSTRAINT ck_verification_cases_outcome CHECK (
        outcome IS NULL OR outcome IN ('PASSED', 'FAILED', 'INCONCLUSIVE')
    ),
    CONSTRAINT ck_verification_cases_completion CHECK (
        (status = 'COMPLETED') = (completed_at IS NOT NULL AND outcome IS NOT NULL)
    ),
    CONSTRAINT ck_verification_cases_manual_review CHECK (
        (manual_reviewer_id IS NULL) = (manual_reviewed_at IS NULL)
    ),
    CONSTRAINT ck_verification_cases_provider CHECK (
        (provider_account_key IS NULL) = (provider_account_version IS NULL)
    ),
    CONSTRAINT ck_verification_cases_version CHECK (version >= 0)
);

-- Only one live case of each kind per profile: two concurrent identity checks would race to decide
-- the same question and the loser's evidence would silently disappear.
CREATE UNIQUE INDEX uk_verification_cases_one_open
    ON verification_cases (host_legal_profile_id, case_type)
    WHERE status <> 'COMPLETED' AND status <> 'ABANDONED';

-- Verification goes stale. The re-screening sweep binds its own decision instant, so the index
-- carries no time predicate.
CREATE INDEX idx_verification_cases_expiry
    ON verification_cases (expires_at)
    WHERE status = 'COMPLETED' AND expires_at IS NOT NULL;

CREATE INDEX idx_verification_cases_manual_queue
    ON verification_cases (opened_at)
    WHERE status = 'MANUAL_REVIEW';
--rollback DROP TABLE verification_cases;

--changeset ninggiangboy:015-04-verification-documents
CREATE TABLE verification_documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    verification_case_id    UUID NOT NULL,
    document_type           VARCHAR(32) NOT NULL,
    issuing_country         VARCHAR(2),
    storage_reference       VARCHAR(255) NOT NULL,
    content_digest          CHAR(64) NOT NULL,
    encryption_key_version  SMALLINT NOT NULL,
    scan_state              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    expires_on              DATE,
    uploaded_at             TIMESTAMPTZ NOT NULL,
    retain_until            TIMESTAMPTZ NOT NULL,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_verification_documents_case FOREIGN KEY (verification_case_id)
        REFERENCES verification_cases (id),
    CONSTRAINT ck_verification_documents_type CHECK (
        document_type IN ('PASSPORT', 'NATIONAL_ID', 'DRIVING_LICENCE', 'RESIDENCE_PERMIT',
                          'COMPANY_REGISTRATION', 'PROOF_OF_ADDRESS', 'BANK_STATEMENT', 'SELFIE')
    ),
    CONSTRAINT ck_verification_documents_country CHECK (
        issuing_country IS NULL OR issuing_country ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT ck_verification_documents_scan CHECK (
        scan_state IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED')
    ),
    CONSTRAINT ck_verification_documents_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    -- Every identity document carries an explicit deletion deadline from the moment it is uploaded.
    -- A document with no retention instant is one nobody ever deletes.
    CONSTRAINT ck_verification_documents_retention CHECK (retain_until > uploaded_at),
    CONSTRAINT ck_verification_documents_version CHECK (version >= 0)
);

CREATE INDEX idx_verification_documents_case ON verification_documents (verification_case_id);

CREATE INDEX idx_verification_documents_retention
    ON verification_documents (retain_until)
    WHERE deleted_at IS NULL;
--rollback DROP TABLE verification_documents;

--changeset ninggiangboy:015-05-screening-checks
CREATE TABLE screening_checks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id   UUID NOT NULL,
    beneficial_owner_id     UUID,
    screening_type          VARCHAR(24) NOT NULL,
    provider_account_key    VARCHAR(96),
    provider_account_version SMALLINT,
    list_version            VARCHAR(64),
    result                  VARCHAR(16) NOT NULL,
    match_count             INTEGER NOT NULL DEFAULT 0,
    evidence_reference      VARCHAR(255),
    adjudicated_by          UUID,
    adjudicated_at          TIMESTAMPTZ,
    adjudication_outcome    VARCHAR(16),
    screened_at             TIMESTAMPTZ NOT NULL,
    next_screening_due_at   TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_screening_checks_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_screening_checks_owner FOREIGN KEY (beneficial_owner_id)
        REFERENCES beneficial_owners (id),
    CONSTRAINT ck_screening_checks_type CHECK (
        screening_type IN ('SANCTIONS', 'PEP', 'WATCHLIST', 'ADVERSE_MEDIA', 'AGE', 'MARKET_SPECIFIC')
    ),
    CONSTRAINT ck_screening_checks_result CHECK (
        result IN ('CLEAR', 'POTENTIAL_MATCH', 'CONFIRMED_MATCH', 'ERROR')
    ),
    -- A potential match is a question, not an answer. It has to be adjudicated by a person before any
    -- capability decision reads it, and the row records who did so and what they concluded.
    CONSTRAINT ck_screening_checks_adjudication CHECK (
        (adjudicated_by IS NULL) = (adjudicated_at IS NULL)
    ),
    CONSTRAINT ck_screening_checks_adjudication_outcome CHECK (
        (adjudicated_at IS NULL) = (adjudication_outcome IS NULL)
    ),
    CONSTRAINT ck_screening_checks_outcome_values CHECK (
        adjudication_outcome IS NULL
        OR adjudication_outcome IN ('FALSE_POSITIVE', 'TRUE_MATCH', 'INCONCLUSIVE')
    ),
    CONSTRAINT ck_screening_checks_match_count CHECK (match_count >= 0),
    CONSTRAINT ck_screening_checks_match_consistency CHECK (
        (result = 'CLEAR') = (match_count = 0)
    ),
    CONSTRAINT ck_screening_checks_provider CHECK (
        (provider_account_key IS NULL) = (provider_account_version IS NULL)
    ),
    CONSTRAINT ck_screening_checks_version CHECK (version >= 0)
);

CREATE INDEX idx_screening_checks_profile
    ON screening_checks (host_legal_profile_id, screening_type, screened_at DESC);

CREATE INDEX idx_screening_checks_pending_adjudication
    ON screening_checks (screened_at)
    WHERE result IN ('POTENTIAL_MATCH', 'CONFIRMED_MATCH') AND adjudicated_at IS NULL;

CREATE INDEX idx_screening_checks_rescreen_due
    ON screening_checks (next_screening_due_at)
    WHERE next_screening_due_at IS NOT NULL;
--rollback DROP TABLE screening_checks;

--changeset ninggiangboy:015-06-host-tax-identifiers
CREATE TABLE host_tax_identifiers (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id   UUID NOT NULL,
    market_code             VARCHAR(2) NOT NULL,
    identifier_type         VARCHAR(32) NOT NULL,
    identifier_digest       CHAR(64) NOT NULL,
    identifier_last4        VARCHAR(4),
    secret_reference        VARCHAR(255) NOT NULL,
    registration_status     VARCHAR(24) NOT NULL DEFAULT 'DECLARED',
    withholding_applies     BOOLEAN NOT NULL DEFAULT false,
    seller_reporting_scope  VARCHAR(24),
    validated_at            TIMESTAMPTZ,
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_tax_identifiers_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_host_tax_identifiers_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_host_tax_identifiers_type CHECK (
        identifier_type IN ('PERSONAL_TAX_CODE', 'BUSINESS_TAX_CODE', 'VAT_NUMBER', 'FOREIGN_TIN')
    ),
    CONSTRAINT ck_host_tax_identifiers_status CHECK (
        registration_status IN ('DECLARED', 'VALIDATED', 'INVALID', 'EXPIRED')
    ),
    CONSTRAINT ck_host_tax_identifiers_validation CHECK (
        (registration_status = 'VALIDATED') = (validated_at IS NOT NULL)
    ),
    CONSTRAINT ck_host_tax_identifiers_digest CHECK (identifier_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_host_tax_identifiers_last4 CHECK (
        identifier_last4 IS NULL OR identifier_last4 ~ '^[A-Za-z0-9]{1,4}$'
    ),
    CONSTRAINT ck_host_tax_identifiers_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_host_tax_identifiers_version CHECK (version >= 0)
);

-- One live identifier of each type per profile and market. The full value is never stored: a digest
-- proves sameness, the last four characters let a host recognize which one they gave us, and the
-- value itself lives behind the secret boundary.
CREATE UNIQUE INDEX uk_host_tax_identifiers_live
    ON host_tax_identifiers (host_legal_profile_id, market_code, identifier_type)
    WHERE effective_until IS NULL;
--rollback DROP TABLE host_tax_identifiers;

--changeset ninggiangboy:015-07-regulatory-registrations
CREATE TABLE regulatory_registrations (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id   UUID NOT NULL,
    market_code             VARCHAR(2) NOT NULL,
    registration_type       VARCHAR(32) NOT NULL,
    authority_name          VARCHAR(255),
    registration_number     VARCHAR(128),
    jurisdiction_reference  VARCHAR(128),
    annual_night_limit      INTEGER,
    status                  VARCHAR(24) NOT NULL DEFAULT 'DECLARED',
    evidence_reference      VARCHAR(255),
    valid_from              DATE,
    valid_until             DATE,
    verified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_regulatory_registrations_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_regulatory_registrations_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_regulatory_registrations_type CHECK (
        registration_type IN ('RENTAL_PERMIT', 'TOURISM_REGISTRATION', 'ZONING_CLEARANCE',
                              'FIRE_SAFETY', 'BUSINESS_LICENCE')
    ),
    CONSTRAINT ck_regulatory_registrations_status CHECK (
        status IN ('DECLARED', 'VERIFIED', 'REJECTED', 'EXPIRED', 'NOT_REQUIRED')
    ),
    CONSTRAINT ck_regulatory_registrations_verification CHECK (
        (status = 'VERIFIED') = (verified_at IS NOT NULL)
    ),
    -- An annual night limit is a cap the calendar has to enforce; zero would mean the property may
    -- never be let, which is a refusal rather than a limit.
    CONSTRAINT ck_regulatory_registrations_night_limit CHECK (
        annual_night_limit IS NULL OR annual_night_limit > 0
    ),
    CONSTRAINT ck_regulatory_registrations_validity CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from
    ),
    CONSTRAINT ck_regulatory_registrations_version CHECK (version >= 0)
);

CREATE INDEX idx_regulatory_registrations_profile
    ON regulatory_registrations (host_legal_profile_id, market_code, registration_type);

CREATE INDEX idx_regulatory_registrations_expiry
    ON regulatory_registrations (valid_until)
    WHERE status = 'VERIFIED' AND valid_until IS NOT NULL;
--rollback DROP TABLE regulatory_registrations;

--changeset ninggiangboy:015-08-payout-destination-claims
CREATE TABLE payout_destination_claims (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id       UUID NOT NULL,
    host_legal_profile_id   UUID NOT NULL,
    market_code             VARCHAR(2) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    rail_key                VARCHAR(64) NOT NULL,
    account_name_digest     CHAR(64) NOT NULL,
    account_reference       VARCHAR(255) NOT NULL,
    account_last4           VARCHAR(4),
    provider_account_key    VARCHAR(96),
    provider_account_version SMALLINT,
    ownership_state         VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',
    ownership_evidence_ref  VARCHAR(255),
    verified_at             TIMESTAMPTZ,
    cooling_off_until       TIMESTAMPTZ,
    lifecycle_state         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    detached_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payout_destination_claims_holder FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_payout_destination_claims_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_payout_destination_claims_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_payout_destination_claims_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payout_destination_claims_ownership CHECK (
        ownership_state IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT ck_payout_destination_claims_verification CHECK (
        (ownership_state = 'VERIFIED') = (verified_at IS NOT NULL)
    ),
    CONSTRAINT ck_payout_destination_claims_lifecycle CHECK (
        lifecycle_state IN ('ACTIVE', 'DETACHED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_payout_destination_claims_detachment CHECK (
        (lifecycle_state = 'ACTIVE') = (detached_at IS NULL)
    ),
    CONSTRAINT ck_payout_destination_claims_digest CHECK (
        account_name_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_payout_destination_claims_provider CHECK (
        (provider_account_key IS NULL) = (provider_account_version IS NULL)
    ),
    CONSTRAINT ck_payout_destination_claims_version CHECK (version >= 0)
);

-- One active destination per holder, market, and currency. Two would make "where does this payout
-- go" ambiguous at exactly the moment money moves.
CREATE UNIQUE INDEX uk_payout_destination_claims_active
    ON payout_destination_claims (account_holder_id, market_code, currency)
    WHERE lifecycle_state = 'ACTIVE';

-- A newly added destination is held before money may be sent to it, which blunts payout diversion
-- after an account takeover. The sweep binds its own instant, so no time predicate here.
CREATE INDEX idx_payout_destination_claims_cooling_off
    ON payout_destination_claims (cooling_off_until)
    WHERE cooling_off_until IS NOT NULL AND lifecycle_state = 'ACTIVE';
--rollback DROP TABLE payout_destination_claims;

--changeset ninggiangboy:015-09-eligibility-decisions
CREATE TABLE host_eligibility_decisions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id   UUID NOT NULL,
    market_code             VARCHAR(2) NOT NULL,
    capability              VARCHAR(48) NOT NULL,
    decision                VARCHAR(16) NOT NULL,
    reason_code             VARCHAR(64) NOT NULL,
    policy_bundle_id        UUID,
    evidence_case_ids       UUID[],
    decided_by              UUID,
    decided_automatically   BOOLEAN NOT NULL DEFAULT true,
    granted_grant_id        UUID,
    decided_at              TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    superseded_by           UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_host_eligibility_decisions_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_host_eligibility_decisions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_host_eligibility_decisions_bundle FOREIGN KEY (policy_bundle_id)
        REFERENCES market_policy_bundles (id),
    CONSTRAINT fk_host_eligibility_decisions_grant FOREIGN KEY (granted_grant_id)
        REFERENCES capability_grants (id),
    CONSTRAINT fk_host_eligibility_decisions_superseded FOREIGN KEY (superseded_by)
        REFERENCES host_eligibility_decisions (id),
    -- These are exactly the D02 capability restrictions. They are decided here from evidence and
    -- then written to capability_grants, which stays the single authority the rest of the platform
    -- evaluates: no service asks this table whether a host may publish.
    CONSTRAINT ck_host_eligibility_decisions_capability CHECK (
        capability IN ('CAN_DRAFT', 'CAN_PUBLISH', 'CAN_ACCEPT_BOOKING', 'CAN_RECEIVE_PAYOUT')
    ),
    CONSTRAINT ck_host_eligibility_decisions_decision CHECK (
        decision IN ('GRANTED', 'DENIED', 'REVOKED', 'SUSPENDED')
    ),
    -- A granted decision points at the grant it produced; a denial has no grant to point at. This is
    -- what ties platform authority back to the evidence that justified it.
    CONSTRAINT ck_host_eligibility_decisions_grant_link CHECK (
        (decision = 'GRANTED') = (granted_grant_id IS NOT NULL)
    ),
    CONSTRAINT ck_host_eligibility_decisions_reviewer CHECK (
        decided_automatically = true OR decided_by IS NOT NULL
    )
);

CREATE INDEX idx_host_eligibility_decisions_current
    ON host_eligibility_decisions (host_legal_profile_id, market_code, capability, decided_at DESC);

CREATE INDEX idx_host_eligibility_decisions_expiry
    ON host_eligibility_decisions (effective_until)
    WHERE effective_until IS NOT NULL AND superseded_by IS NULL;
--rollback DROP TABLE host_eligibility_decisions;

--changeset ninggiangboy:015-10-verification-appeals
CREATE TABLE verification_appeals (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_legal_profile_id   UUID NOT NULL,
    contested_decision_id   UUID NOT NULL,
    submitted_by            UUID NOT NULL,
    submitted_at            TIMESTAMPTZ NOT NULL,
    grounds                 TEXT NOT NULL,
    evidence_reference      VARCHAR(255),
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    reviewer_id             UUID,
    reviewed_at             TIMESTAMPTZ,
    outcome                 VARCHAR(16),
    outcome_reason_code     VARCHAR(64),
    resulting_decision_id   UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_verification_appeals_profile FOREIGN KEY (host_legal_profile_id)
        REFERENCES host_legal_profiles (id),
    CONSTRAINT fk_verification_appeals_contested FOREIGN KEY (contested_decision_id)
        REFERENCES host_eligibility_decisions (id),
    CONSTRAINT fk_verification_appeals_resulting FOREIGN KEY (resulting_decision_id)
        REFERENCES host_eligibility_decisions (id),
    CONSTRAINT fk_verification_appeals_submitter FOREIGN KEY (submitted_by) REFERENCES users (id),
    CONSTRAINT ck_verification_appeals_status CHECK (
        status IN ('OPEN', 'IN_REVIEW', 'CLOSED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_verification_appeals_outcome CHECK (
        outcome IS NULL OR outcome IN ('UPHELD', 'OVERTURNED', 'PARTIAL')
    ),
    -- A closed appeal must have been reviewed by a named person and must say what it concluded. An
    -- appeal that closes with no reviewer and no outcome is a refusal with extra steps.
    CONSTRAINT ck_verification_appeals_closure CHECK (
        status <> 'CLOSED'
        OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL AND outcome IS NOT NULL)
    ),
    -- An overturned appeal has to produce a new decision; otherwise nothing actually changed for the
    -- host, and the capability they appealed about stays exactly as it was.
    CONSTRAINT ck_verification_appeals_overturn CHECK (
        outcome IS NULL OR outcome <> 'OVERTURNED' OR resulting_decision_id IS NOT NULL
    ),
    CONSTRAINT ck_verification_appeals_version CHECK (version >= 0)
);

CREATE INDEX idx_verification_appeals_queue
    ON verification_appeals (submitted_at)
    WHERE status IN ('OPEN', 'IN_REVIEW');

CREATE INDEX idx_verification_appeals_profile
    ON verification_appeals (host_legal_profile_id, submitted_at DESC);
--rollback DROP TABLE verification_appeals;
