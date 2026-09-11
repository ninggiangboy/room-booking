--liquibase formatted sql

-- The platform launches in Vietnam, but "Vietnam" is not a constant to be hard-coded into pricing,
-- tax, payout, and disclosure logic. Every consequential decision has to name the market whose rules
-- governed it, the legal entity accountable for it, and the exact policy version it applied -- and it
-- has to still name them years later, when the current configuration has moved on.
--
-- These tables are that configuration, and they are effective-dated and immutable rather than
-- editable. A policy bundle is never updated in place; a new version supersedes it, and a historical
-- replay resolves the version recorded on the row instead of whatever is active today.
--
-- Missing or expired configuration fails closed. There is deliberately no default market and no
-- fallback bundle: inferring a market from a currency, a phone number, or an IP address is how a
-- booking ends up contracted under rules nobody approved.

--changeset ninggiangboy:013-01-markets
CREATE TABLE markets (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_code           VARCHAR(2) NOT NULL,
    display_name          VARCHAR(120) NOT NULL,
    default_currency      VARCHAR(3) NOT NULL,
    default_locale        VARCHAR(35) NOT NULL,
    default_time_zone     VARCHAR(64) NOT NULL,
    lifecycle_state       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    activation_version    INTEGER NOT NULL DEFAULT 0,
    activated_at          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_markets_code UNIQUE (market_code),
    CONSTRAINT ck_markets_code CHECK (market_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_markets_currency CHECK (default_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_markets_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    -- A market that has never been activated cannot claim an activation instant, and an active one
    -- must be able to say when it became active.
    CONSTRAINT ck_markets_activation CHECK (
        (lifecycle_state = 'DRAFT') = (activated_at IS NULL)
    ),
    -- The time zone is a full IANA Region/City identifier. Aliases such as UTC, GMT, or CET are
    -- rejected because they carry no DST history, and property-local dates depend on that history.
    CONSTRAINT ck_markets_time_zone CHECK (default_time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$'),
    CONSTRAINT ck_markets_activation_version CHECK (activation_version >= 0),
    CONSTRAINT ck_markets_version CHECK (version >= 0)
);
--rollback DROP TABLE markets;

--changeset ninggiangboy:013-02-market-supported-values
CREATE TABLE market_currencies (
    market_id     UUID NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    is_contract   BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_market_currencies PRIMARY KEY (market_id, currency),
    CONSTRAINT fk_market_currencies_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_market_currencies_code CHECK (currency ~ '^[A-Z]{3}$')
);

-- Presentation currencies may be many; the currency a contract is actually denominated in is one.
CREATE UNIQUE INDEX uk_market_currencies_one_contract
    ON market_currencies (market_id)
    WHERE is_contract = true;

CREATE TABLE market_locales (
    market_id     UUID NOT NULL,
    locale        VARCHAR(35) NOT NULL,
    is_default    BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_market_locales PRIMARY KEY (market_id, locale),
    CONSTRAINT fk_market_locales_market FOREIGN KEY (market_id) REFERENCES markets (id)
);

CREATE UNIQUE INDEX uk_market_locales_one_default
    ON market_locales (market_id)
    WHERE is_default = true;

CREATE TABLE market_time_zones (
    market_id     UUID NOT NULL,
    time_zone     VARCHAR(64) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_market_time_zones PRIMARY KEY (market_id, time_zone),
    CONSTRAINT fk_market_time_zones_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_market_time_zones_iana CHECK (time_zone ~ '^[A-Za-z_]+/[A-Za-z0-9_+/-]+$')
);
--rollback DROP TABLE market_time_zones;
--rollback DROP TABLE market_locales;
--rollback DROP TABLE market_currencies;

--changeset ninggiangboy:013-03-legal-entities
CREATE TABLE legal_entities (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_key              VARCHAR(64) NOT NULL,
    legal_name              VARCHAR(255) NOT NULL,
    country_code            VARCHAR(2) NOT NULL,
    registration_reference  VARCHAR(128),
    tax_identifier_ref      VARCHAR(128),
    registered_address_ref  UUID,
    lifecycle_state         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_legal_entities_key UNIQUE (entity_key),
    CONSTRAINT ck_legal_entities_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_legal_entities_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_legal_entities_version CHECK (version >= 0)
);

-- Which entity is accountable in which market, and for what span. An entity can be replaced in a
-- market without rewriting the bookings that the previous entity contracted.
CREATE TABLE legal_entity_markets (
    legal_entity_id   UUID NOT NULL,
    market_id         UUID NOT NULL,
    effective_from    TIMESTAMPTZ NOT NULL,
    effective_until   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_legal_entity_markets PRIMARY KEY (legal_entity_id, market_id, effective_from),
    CONSTRAINT fk_legal_entity_markets_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_legal_entity_markets_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_legal_entity_markets_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    -- At most one entity is accountable in a market at any instant. Two overlapping rows would make
    -- "who is the counterparty to this booking" unanswerable.
    CONSTRAINT ex_legal_entity_markets_no_overlap EXCLUDE USING gist (
        market_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);
--rollback DROP TABLE legal_entity_markets;
--rollback DROP TABLE legal_entities;

--changeset ninggiangboy:013-04-market-policy-bundles
CREATE TABLE market_policy_bundles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_id           UUID NOT NULL,
    legal_entity_id     UUID NOT NULL,
    bundle_type         VARCHAR(32) NOT NULL,
    bundle_version      INTEGER NOT NULL,
    content_digest      CHAR(64) NOT NULL,
    document_reference  VARCHAR(255) NOT NULL,
    effective_from      TIMESTAMPTZ NOT NULL,
    effective_until     TIMESTAMPTZ,
    approval_record_id  UUID,
    superseded_by       UUID,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_market_policy_bundles_version UNIQUE (market_id, bundle_type, bundle_version),
    CONSTRAINT fk_market_policy_bundles_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_market_policy_bundles_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_market_policy_bundles_superseded FOREIGN KEY (superseded_by)
        REFERENCES market_policy_bundles (id),
    CONSTRAINT ck_market_policy_bundles_type CHECK (
        bundle_type IN (
            'PRODUCT', 'LEGAL', 'TAX', 'PRIVACY', 'RETENTION',
            'CANCELLATION', 'DISCLOSURE', 'PAYOUT'
        )
    ),
    CONSTRAINT ck_market_policy_bundles_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_market_policy_bundles_version_number CHECK (bundle_version > 0),
    CONSTRAINT ck_market_policy_bundles_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    -- One bundle of each type is in force per market at any instant. Overlapping versions would let
    -- two decisions made a second apart apply different approved rules.
    CONSTRAINT ex_market_policy_bundles_no_overlap EXCLUDE USING gist (
        market_id WITH =,
        bundle_type WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);

CREATE INDEX idx_market_policy_bundles_resolution
    ON market_policy_bundles (market_id, bundle_type, effective_from DESC);
--rollback DROP TABLE market_policy_bundles;

--changeset ninggiangboy:013-05-market-capabilities
CREATE TABLE market_capabilities (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    market_id            UUID NOT NULL,
    capability           VARCHAR(64) NOT NULL,
    method_key           VARCHAR(64),
    rail_key             VARCHAR(64),
    availability_state   VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    eligibility_rule     JSONB,
    effective_from       TIMESTAMPTZ NOT NULL,
    effective_until      TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_market_capabilities_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT ck_market_capabilities_availability CHECK (
        availability_state IN ('DISABLED', 'PILOT', 'ENABLED', 'SUSPENDED')
    ),
    CONSTRAINT ck_market_capabilities_rule CHECK (
        eligibility_rule IS NULL OR jsonb_typeof(eligibility_rule) = 'object'
    ),
    CONSTRAINT ck_market_capabilities_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_market_capabilities_version CHECK (version >= 0)
);

-- COALESCE gives the optional method and rail a concrete value so the exclusion constraint treats
-- two rows with no method as the same capability rather than as two unrelated NULLs.
CREATE INDEX idx_market_capabilities_resolution
    ON market_capabilities (market_id, capability, effective_from DESC);

ALTER TABLE market_capabilities
    ADD CONSTRAINT ex_market_capabilities_no_overlap EXCLUDE USING gist (
        market_id WITH =,
        capability WITH =,
        COALESCE(method_key, '') WITH =,
        COALESCE(rail_key, '') WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    );
--rollback DROP TABLE market_capabilities;

--changeset ninggiangboy:013-06-provider-accounts
CREATE TABLE provider_accounts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_key           VARCHAR(96) NOT NULL,
    account_version       SMALLINT NOT NULL DEFAULT 1,
    provider_family       VARCHAR(48) NOT NULL,
    capability            VARCHAR(64) NOT NULL,
    market_id             UUID,
    legal_entity_id       UUID,
    currency              VARCHAR(3),
    environment           VARCHAR(16) NOT NULL,
    lifecycle_state       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    secret_reference      VARCHAR(255) NOT NULL,
    webhook_secret_ref    VARCHAR(255),
    effective_from        TIMESTAMPTZ NOT NULL,
    effective_until       TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_provider_accounts_key UNIQUE (account_key, account_version),
    CONSTRAINT fk_provider_accounts_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_provider_accounts_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT ck_provider_accounts_environment CHECK (
        environment IN ('SANDBOX', 'PRODUCTION')
    ),
    CONSTRAINT ck_provider_accounts_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_provider_accounts_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_provider_accounts_account_version CHECK (account_version > 0),
    CONSTRAINT ck_provider_accounts_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_provider_accounts_version CHECK (version >= 0)
);

CREATE INDEX idx_provider_accounts_resolution
    ON provider_accounts (market_id, capability, lifecycle_state, effective_from DESC);
--rollback DROP TABLE provider_accounts;

--changeset ninggiangboy:013-07-localized-contents
CREATE TABLE localized_contents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_key         VARCHAR(128) NOT NULL,
    locale              VARCHAR(35) NOT NULL,
    content_version     INTEGER NOT NULL,
    body                TEXT NOT NULL,
    content_digest      CHAR(64) NOT NULL,
    market_id           UUID,
    policy_bundle_id    UUID,
    reviewer_id         UUID,
    reviewed_at         TIMESTAMPTZ,
    lifecycle_state     VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from      TIMESTAMPTZ NOT NULL,
    effective_until     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_localized_contents_version UNIQUE (content_key, locale, content_version),
    CONSTRAINT fk_localized_contents_market FOREIGN KEY (market_id) REFERENCES markets (id),
    CONSTRAINT fk_localized_contents_bundle FOREIGN KEY (policy_bundle_id)
        REFERENCES market_policy_bundles (id),
    CONSTRAINT ck_localized_contents_digest CHECK (content_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_localized_contents_version_number CHECK (content_version > 0),
    CONSTRAINT ck_localized_contents_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'RETIRED')
    ),
    -- Approved content names who approved it and when. Content that shows a legal disclosure with no
    -- reviewer is exactly the content that must not be renderable.
    CONSTRAINT ck_localized_contents_review CHECK (
        lifecycle_state <> 'APPROVED'
        OR (reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_localized_contents_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    )
);

CREATE INDEX idx_localized_contents_resolution
    ON localized_contents (content_key, locale, effective_from DESC)
    WHERE lifecycle_state = 'APPROVED';
--rollback DROP TABLE localized_contents;

--changeset ninggiangboy:013-08-market-approval-records
CREATE TABLE market_approval_records (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type          VARCHAR(48) NOT NULL,
    subject_id            UUID NOT NULL,
    decision              VARCHAR(16) NOT NULL,
    approver_id           UUID NOT NULL,
    approver_role         VARCHAR(48) NOT NULL,
    reason                TEXT NOT NULL,
    evidence_reference    VARCHAR(255),
    decided_at            TIMESTAMPTZ NOT NULL,
    superseded_by         UUID,
    CONSTRAINT fk_market_approval_records_superseded FOREIGN KEY (superseded_by)
        REFERENCES market_approval_records (id),
    CONSTRAINT ck_market_approval_records_decision CHECK (
        decision IN ('APPROVED', 'REJECTED', 'REVOKED')
    ),
    CONSTRAINT ck_market_approval_records_subject CHECK (
        subject_type IN (
            'MARKET', 'LEGAL_ENTITY', 'POLICY_BUNDLE',
            'MARKET_CAPABILITY', 'PROVIDER_ACCOUNT', 'LOCALIZED_CONTENT'
        )
    )
);

CREATE INDEX idx_market_approval_records_subject
    ON market_approval_records (subject_type, subject_id, decided_at DESC);

ALTER TABLE market_policy_bundles
    ADD CONSTRAINT fk_market_policy_bundles_approval FOREIGN KEY (approval_record_id)
        REFERENCES market_approval_records (id);
--rollback ALTER TABLE market_policy_bundles DROP CONSTRAINT fk_market_policy_bundles_approval;
--rollback DROP TABLE market_approval_records;

--changeset ninggiangboy:013-09-vietnam-market-baseline
-- Seeds only the objective facts about the launch market: its code, contract currency, default
-- locale, and IANA zone. These are properties of Vietnam, not decisions anyone has to make.
--
-- The market is seeded DRAFT, and no legal entity, policy bundle, provider account, or approval
-- record is seeded with it. Those are the output of the decision gate in
-- docs/features/multi-market-compliance-and-localization.md: an accountable owner has to approve the
-- platform's legal role, its tax interpretation, and its providers. Seeding an APPROVED policy
-- bundle that no one approved would fabricate the exact evidence this table exists to preserve, and
-- would silently defeat the fail-closed rule -- publication, quoting, and booking are supposed to be
-- impossible until real configuration is loaded, and seeding a placeholder would make them possible.
--
-- The timestamp is a fixed literal rather than now(): a seed must produce the same rows on every
-- database, and the application clock is the only thing allowed to decide a current instant.
INSERT INTO markets (
    market_code, display_name, default_currency, default_locale, default_time_zone,
    lifecycle_state, activation_version, created_at, updated_at
) VALUES (
    'VN', 'Vietnam', 'VND', 'vi-VN', 'Asia/Ho_Chi_Minh',
    'DRAFT', 0, TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2026-01-01 00:00:00+00'
);

INSERT INTO market_currencies (market_id, currency, is_contract, created_at)
SELECT id, 'VND', true, TIMESTAMPTZ '2026-01-01 00:00:00+00' FROM markets WHERE market_code = 'VN';

INSERT INTO market_locales (market_id, locale, is_default, created_at)
SELECT id, 'vi-VN', true, TIMESTAMPTZ '2026-01-01 00:00:00+00' FROM markets WHERE market_code = 'VN';

INSERT INTO market_locales (market_id, locale, is_default, created_at)
SELECT id, 'en-US', false, TIMESTAMPTZ '2026-01-01 00:00:00+00' FROM markets WHERE market_code = 'VN';

INSERT INTO market_time_zones (market_id, time_zone, created_at)
SELECT id, 'Asia/Ho_Chi_Minh', TIMESTAMPTZ '2026-01-01 00:00:00+00'
FROM markets WHERE market_code = 'VN';
--rollback DELETE FROM market_time_zones WHERE market_id IN (SELECT id FROM markets WHERE market_code = 'VN');
--rollback DELETE FROM market_locales WHERE market_id IN (SELECT id FROM markets WHERE market_code = 'VN');
--rollback DELETE FROM market_currencies WHERE market_id IN (SELECT id FROM markets WHERE market_code = 'VN');
--rollback DELETE FROM markets WHERE market_code = 'VN';
