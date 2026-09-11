--liquibase formatted sql

-- Today's identity model answers "what role does this user have". The target release has to answer
-- "may this principal do this thing, to this resource, right now, and who said so" -- which is a
-- different question. Owning a listing is stronger than merely holding the HOST role, an organization
-- can act through members who are not its owner, a compliance hold must be able to remove a
-- capability without removing a role, and every one of those facts has to be explainable afterwards.
--
-- This changeset is deliberately additive. users, user_roles, and host_profiles keep working exactly
-- as they do, and remain the compatibility surface until every reader has moved. The identity design
-- stages the cutover across releases: add tables, write both, backfill, reconcile, switch reads, and
-- only then drop the fallback. Collapsing those steps into one migration is how a login outage
-- happens.
--
-- Identity audit is written through the D00 audit_events primitive from migration 012 rather than a
-- private table, so there is one append-only trail rather than one per domain.

--changeset ninggiangboy:014-01-account-holders
CREATE TABLE account_holders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holder_type         VARCHAR(16) NOT NULL,
    user_id             UUID,
    display_name        VARCHAR(255) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    market_code         VARCHAR(2),
    context_state       VARCHAR(24) NOT NULL DEFAULT 'RESOLVED',
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_account_holders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_account_holders_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT ck_account_holders_type CHECK (holder_type IN ('PERSON', 'ORGANIZATION')),
    CONSTRAINT ck_account_holders_status CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
    ),
    -- A person holder is the account of exactly one user; an organization exists independently of
    -- any single user and must not borrow one's identity.
    CONSTRAINT ck_account_holders_person_user CHECK (
        (holder_type = 'PERSON') = (user_id IS NOT NULL)
    ),
    -- Rows created before markets existed are marked legacy rather than assigned a guessed market.
    -- A resolved row must name its market; guessing one from a phone number or IP address is how a
    -- contract ends up under rules nobody approved.
    CONSTRAINT ck_account_holders_context CHECK (
        context_state IN ('RESOLVED', 'LEGACY_UNRECONCILED')
    ),
    CONSTRAINT ck_account_holders_market_resolution CHECK (
        (context_state = 'RESOLVED') = (market_code IS NOT NULL)
    ),
    CONSTRAINT ck_account_holders_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_account_holders_person_user
    ON account_holders (user_id)
    WHERE holder_type = 'PERSON';

CREATE INDEX idx_account_holders_unreconciled
    ON account_holders (created_at)
    WHERE context_state = 'LEGACY_UNRECONCILED';
--rollback DROP TABLE account_holders;

--changeset ninggiangboy:014-02-organization-members
CREATE TABLE organization_members (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL,
    user_id             UUID NOT NULL,
    member_role         VARCHAR(32) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'INVITED',
    invited_by          UUID,
    invited_at          TIMESTAMPTZ NOT NULL,
    joined_at           TIMESTAMPTZ,
    removed_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_organization_members_pair UNIQUE (organization_id, user_id),
    CONSTRAINT fk_organization_members_org FOREIGN KEY (organization_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_organization_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_organization_members_inviter FOREIGN KEY (invited_by) REFERENCES users (id),
    CONSTRAINT ck_organization_members_role CHECK (
        member_role IN ('OWNER', 'ADMIN', 'MANAGER', 'CO_HOST', 'FINANCE', 'VIEWER')
    ),
    CONSTRAINT ck_organization_members_status CHECK (
        status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')
    ),
    CONSTRAINT ck_organization_members_joined CHECK (
        status = 'INVITED' OR joined_at IS NOT NULL
    ),
    CONSTRAINT ck_organization_members_removed CHECK (
        (status = 'REMOVED') = (removed_at IS NOT NULL)
    ),
    CONSTRAINT ck_organization_members_version CHECK (version >= 0)
);

-- "At least one active owner" cannot be a row constraint, because the rule is about the absence of
-- other rows. The service enforces it transactionally; this index makes both that check and the
-- reconciliation query that backs it up cheap.
CREATE INDEX idx_organization_members_active_owners
    ON organization_members (organization_id)
    WHERE status = 'ACTIVE' AND member_role = 'OWNER';

CREATE INDEX idx_organization_members_user
    ON organization_members (user_id, status);
--rollback DROP TABLE organization_members;

--changeset ninggiangboy:014-03-capability-grants
CREATE TABLE capability_grants (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grantee_type            VARCHAR(16) NOT NULL,
    grantee_id              UUID NOT NULL,
    grantor_type            VARCHAR(16),
    grantor_id              UUID,
    role_name               VARCHAR(48),
    capabilities            TEXT[] NOT NULL,
    scope_type              VARCHAR(32) NOT NULL,
    scope_id                UUID,
    market_code             VARCHAR(2),
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    reason_code             VARCHAR(64) NOT NULL,
    source                  VARCHAR(16) NOT NULL,
    derived_from_grant_id   UUID,
    revoked_at              TIMESTAMPTZ,
    revocation_reason       VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_capability_grants_derived FOREIGN KEY (derived_from_grant_id)
        REFERENCES capability_grants (id),
    CONSTRAINT fk_capability_grants_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_capability_grants_grantee_type CHECK (
        grantee_type IN ('USER', 'ORGANIZATION', 'SERVICE')
    ),
    CONSTRAINT ck_capability_grants_grantor CHECK (
        (grantor_type IS NULL) = (grantor_id IS NULL)
    ),
    -- A global grant has no scope row to point at; every narrower scope must name its resource.
    -- This is what makes "owns this listing" expressible rather than only "has the HOST role".
    CONSTRAINT ck_capability_grants_scope CHECK (
        scope_type IN ('GLOBAL', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'BOOKING', 'MARKET')
    ),
    CONSTRAINT ck_capability_grants_scope_id CHECK (
        (scope_type = 'GLOBAL') = (scope_id IS NULL)
    ),
    CONSTRAINT ck_capability_grants_source CHECK (
        source IN ('SELF_SERVICE', 'DELEGATION', 'COMPLIANCE', 'RISK', 'GOVERNANCE', 'LEGACY')
    ),
    CONSTRAINT ck_capability_grants_capabilities CHECK (
        array_length(capabilities, 1) IS NOT NULL AND array_length(capabilities, 1) > 0
    ),
    CONSTRAINT ck_capability_grants_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_capability_grants_revocation CHECK (
        (revoked_at IS NULL) = (revocation_reason IS NULL)
    ),
    CONSTRAINT ck_capability_grants_version CHECK (version >= 0)
);

-- Point evaluation asks: what does this principal hold, on this resource, now. The partial index
-- omits revoked rows so an account with years of withdrawn grants still evaluates in constant time.
CREATE INDEX idx_capability_grants_evaluation
    ON capability_grants (grantee_id, scope_type, scope_id, effective_from)
    WHERE revoked_at IS NULL;

-- Revoking a delegation must revoke everything derived from it; this index makes that walk cheap.
CREATE INDEX idx_capability_grants_derived
    ON capability_grants (derived_from_grant_id)
    WHERE derived_from_grant_id IS NOT NULL;

CREATE INDEX idx_capability_grants_expiry
    ON capability_grants (effective_until)
    WHERE revoked_at IS NULL AND effective_until IS NOT NULL;
--rollback DROP TABLE capability_grants;

--changeset ninggiangboy:014-04-capability-restrictions
CREATE TABLE capability_restrictions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_type          VARCHAR(16) NOT NULL,
    principal_id            UUID NOT NULL,
    capability              VARCHAR(96),
    capability_group        VARCHAR(48),
    scope_type              VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    scope_id                UUID,
    reason_code             VARCHAR(64) NOT NULL,
    decision_reference      VARCHAR(128),
    effective_from          TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    lifted_by               UUID,
    lifted_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_capability_restrictions_principal CHECK (
        principal_type IN ('USER', 'ORGANIZATION', 'SERVICE')
    ),
    -- A restriction names either one capability or a group of them, never neither and never both.
    CONSTRAINT ck_capability_restrictions_target CHECK (
        (capability IS NULL) <> (capability_group IS NULL)
    ),
    CONSTRAINT ck_capability_restrictions_scope CHECK (
        scope_type IN ('GLOBAL', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'BOOKING', 'MARKET')
    ),
    CONSTRAINT ck_capability_restrictions_scope_id CHECK (
        (scope_type = 'GLOBAL') = (scope_id IS NULL)
    ),
    CONSTRAINT ck_capability_restrictions_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_capability_restrictions_lift CHECK (
        (lifted_by IS NULL) = (lifted_at IS NULL)
    ),
    CONSTRAINT ck_capability_restrictions_version CHECK (version >= 0)
);

-- Evaluation subtracts active restrictions from grants, so this lookup runs on every authorization
-- decision and must stay bounded.
CREATE INDEX idx_capability_restrictions_evaluation
    ON capability_restrictions (principal_id, scope_type, scope_id, effective_from)
    WHERE lifted_at IS NULL;
--rollback DROP TABLE capability_restrictions;

--changeset ninggiangboy:014-05-auth-sessions
CREATE TABLE auth_sessions (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   UUID NOT NULL,
    active_organization_id    UUID,
    authentication_method     VARCHAR(32) NOT NULL,
    assurance_level           VARCHAR(16) NOT NULL DEFAULT 'AAL1',
    last_assurance_proof_at   TIMESTAMPTZ NOT NULL,
    client_descriptor         VARCHAR(255),
    origin_hash               CHAR(64),
    rotation_generation       INTEGER NOT NULL DEFAULT 0,
    current_token_id          UUID,
    last_used_at              TIMESTAMPTZ NOT NULL,
    idle_expires_at           TIMESTAMPTZ NOT NULL,
    absolute_expires_at       TIMESTAMPTZ NOT NULL,
    revoked_at                TIMESTAMPTZ,
    revocation_reason         VARCHAR(48),
    revoked_by                UUID,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_auth_sessions_organization FOREIGN KEY (active_organization_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_auth_sessions_revoker FOREIGN KEY (revoked_by) REFERENCES users (id),
    CONSTRAINT ck_auth_sessions_method CHECK (
        authentication_method IN ('PASSWORD', 'PASSWORD_MFA', 'FEDERATED', 'RECOVERY', 'LEGACY')
    ),
    -- Assurance is the strength of the proof behind this session. A payout change may demand a
    -- stronger level than the one the session was established with, which is what step-up means.
    CONSTRAINT ck_auth_sessions_assurance CHECK (assurance_level IN ('AAL1', 'AAL2', 'AAL3')),
    CONSTRAINT ck_auth_sessions_origin_hash CHECK (
        origin_hash IS NULL OR origin_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_auth_sessions_revocation CHECK (
        (revoked_at IS NULL) = (revocation_reason IS NULL)
    ),
    CONSTRAINT ck_auth_sessions_expiry CHECK (absolute_expires_at > created_at),
    CONSTRAINT ck_auth_sessions_rotation CHECK (rotation_generation >= 0),
    CONSTRAINT ck_auth_sessions_version CHECK (version >= 0)
);

-- "Show me my devices" and "revoke everything" both read live sessions for one user. The partial
-- index keeps that list proportional to live sessions rather than to lifetime logins.
CREATE INDEX idx_auth_sessions_live
    ON auth_sessions (user_id, absolute_expires_at)
    WHERE revoked_at IS NULL;

-- The expiry sweep indexes the column without a time predicate: a partial index whose predicate
-- moves is not immutable, so the worker binds its own decision instant instead.
CREATE INDEX idx_auth_sessions_expiry_sweep
    ON auth_sessions (absolute_expires_at)
    WHERE revoked_at IS NULL;
--rollback DROP TABLE auth_sessions;

--changeset ninggiangboy:014-06-auth-credentials
CREATE TABLE auth_credentials (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL,
    credential_type       VARCHAR(24) NOT NULL,
    encoder_id            VARCHAR(32) NOT NULL,
    verifier_digest       TEXT,
    secret_reference      VARCHAR(255),
    key_version           SMALLINT,
    enrolled_at           TIMESTAMPTZ NOT NULL,
    last_used_at          TIMESTAMPTZ,
    disabled_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_auth_credentials_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_auth_credentials_type CHECK (
        credential_type IN ('PASSWORD', 'TOTP', 'WEBAUTHN', 'RECOVERY_CODE', 'FEDERATED')
    ),
    -- A credential is either something we can only verify (a password digest) or something we must
    -- be able to read back through the secret boundary (a TOTP seed). It is never both, and never
    -- neither: a credential row with no material cannot authenticate anyone.
    CONSTRAINT ck_auth_credentials_material CHECK (
        (verifier_digest IS NULL) <> (secret_reference IS NULL)
    ),
    CONSTRAINT ck_auth_credentials_key_version CHECK (
        (secret_reference IS NULL) = (key_version IS NULL)
    ),
    CONSTRAINT ck_auth_credentials_version CHECK (version >= 0)
);

-- One live credential of each type per principal. A second active password would make "which one is
-- current" a question the login path has to guess at.
CREATE UNIQUE INDEX uk_auth_credentials_one_active
    ON auth_credentials (user_id, credential_type)
    WHERE disabled_at IS NULL;
--rollback DROP TABLE auth_credentials;

--changeset ninggiangboy:014-07-contact-channels
CREATE TABLE contact_channels (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    channel_type            VARCHAR(16) NOT NULL,
    normalized_value        CITEXT NOT NULL,
    original_value          VARCHAR(320),
    purpose                 VARCHAR(24) NOT NULL DEFAULT 'ACCOUNT',
    is_primary              BOOLEAN NOT NULL DEFAULT false,
    verified_at             TIMESTAMPTZ,
    verification_method     VARCHAR(24),
    superseded_by           UUID,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_contact_channels_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_contact_channels_superseded FOREIGN KEY (superseded_by)
        REFERENCES contact_channels (id),
    CONSTRAINT ck_contact_channels_type CHECK (channel_type IN ('EMAIL', 'PHONE', 'PUSH')),
    CONSTRAINT ck_contact_channels_purpose CHECK (
        purpose IN ('ACCOUNT', 'BILLING', 'OPERATIONS', 'MARKETING')
    ),
    CONSTRAINT ck_contact_channels_verification CHECK (
        (verified_at IS NULL) = (verification_method IS NULL)
    ),
    CONSTRAINT ck_contact_channels_version CHECK (version >= 0)
);

-- Two accounts cannot both own the same verified primary email. Unverified and secondary rows are
-- deliberately outside the constraint: two people may each claim an address before either proves it,
-- and only proof confers exclusivity.
CREATE UNIQUE INDEX uk_contact_channels_verified_primary_value
    ON contact_channels (channel_type, normalized_value)
    WHERE verified_at IS NOT NULL AND is_primary = true;

CREATE UNIQUE INDEX uk_contact_channels_one_primary
    ON contact_channels (user_id, channel_type)
    WHERE is_primary = true AND superseded_by IS NULL;

CREATE INDEX idx_contact_channels_user
    ON contact_channels (user_id, channel_type);
--rollback DROP TABLE contact_channels;

--changeset ninggiangboy:014-08-auth-attempts
CREATE TABLE auth_attempts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID,
    identifier_digest     CHAR(64),
    attempt_type          VARCHAR(32) NOT NULL,
    outcome_class         VARCHAR(16) NOT NULL,
    failure_reason_class  VARCHAR(48),
    source_hash           CHAR(64),
    device_hash           CHAR(64),
    decision_reference    VARCHAR(128),
    occurred_at           TIMESTAMPTZ NOT NULL,
    retain_until          TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_auth_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_auth_attempts_type CHECK (
        attempt_type IN (
            'LOGIN', 'REFRESH', 'PASSWORD_RESET', 'EMAIL_VERIFICATION',
            'MFA_CHALLENGE', 'STEP_UP'
        )
    ),
    CONSTRAINT ck_auth_attempts_outcome CHECK (
        outcome_class IN ('SUCCESS', 'FAILURE', 'CHALLENGED', 'BLOCKED')
    ),
    -- An attempt that resolved to an account names it; one that did not carries a digest of the
    -- identifier that was tried. Storing the raw identifier would turn the failure log into a
    -- directory of which email addresses exist.
    CONSTRAINT ck_auth_attempts_subject CHECK (
        user_id IS NOT NULL OR identifier_digest IS NOT NULL
    ),
    CONSTRAINT ck_auth_attempts_digests CHECK (
        (identifier_digest IS NULL OR identifier_digest ~ '^[0-9a-f]{64}$')
        AND (source_hash IS NULL OR source_hash ~ '^[0-9a-f]{64}$')
        AND (device_hash IS NULL OR device_hash ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_auth_attempts_retention CHECK (retain_until > occurred_at)
);

-- Velocity control counts recent attempts for one subject; both shapes of subject need an index.
CREATE INDEX idx_auth_attempts_user_velocity
    ON auth_attempts (user_id, attempt_type, occurred_at DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX idx_auth_attempts_identifier_velocity
    ON auth_attempts (identifier_digest, attempt_type, occurred_at DESC)
    WHERE identifier_digest IS NOT NULL;

CREATE INDEX idx_auth_attempts_source_velocity
    ON auth_attempts (source_hash, occurred_at DESC)
    WHERE source_hash IS NOT NULL;

CREATE INDEX idx_auth_attempts_retention ON auth_attempts (retain_until);
--rollback DROP TABLE auth_attempts;

--changeset ninggiangboy:014-09-auth-tokens-rotation-lineage
-- Rotation lineage is what makes refresh-token reuse detectable. Without a superseded_by chain, a
-- stolen token replayed after the legitimate client has already rotated is indistinguishable from an
-- ordinary refresh, and the only safe response -- revoking the whole session -- has nothing to
-- revoke. The columns are nullable because existing tokens predate sessions entirely.
ALTER TABLE auth_tokens
    ADD COLUMN session_id UUID,
    ADD COLUMN rotation_generation INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN superseded_by UUID,
    ADD COLUMN consumption_reason VARCHAR(32),
    ADD CONSTRAINT fk_auth_tokens_session FOREIGN KEY (session_id) REFERENCES auth_sessions (id),
    ADD CONSTRAINT fk_auth_tokens_superseded FOREIGN KEY (superseded_by) REFERENCES auth_tokens (id),
    ADD CONSTRAINT ck_auth_tokens_rotation CHECK (rotation_generation >= 0),
    ADD CONSTRAINT ck_auth_tokens_consumption_reason CHECK (
        consumption_reason IS NULL
        OR consumption_reason IN ('USED', 'ROTATED', 'REVOKED', 'REUSE_DETECTED', 'EXPIRED', 'LOGOUT')
    ),
    -- A consumed token must say why. "Rotated normally" and "replayed by an attacker" are the same
    -- row shape otherwise, and telling them apart is the entire point of reuse detection.
    ADD CONSTRAINT ck_auth_tokens_consumption CHECK (
        (consumed_at IS NULL) = (consumption_reason IS NULL)
    );

CREATE INDEX idx_auth_tokens_session
    ON auth_tokens (session_id, rotation_generation)
    WHERE session_id IS NOT NULL;

ALTER TABLE auth_sessions
    ADD CONSTRAINT fk_auth_sessions_current_token FOREIGN KEY (current_token_id)
        REFERENCES auth_tokens (id);
--rollback ALTER TABLE auth_sessions DROP CONSTRAINT fk_auth_sessions_current_token;
--rollback DROP INDEX idx_auth_tokens_session;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_consumption;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_consumption_reason;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_rotation;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT fk_auth_tokens_superseded;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT fk_auth_tokens_session;
--rollback ALTER TABLE auth_tokens DROP COLUMN consumption_reason;
--rollback ALTER TABLE auth_tokens DROP COLUMN superseded_by;
--rollback ALTER TABLE auth_tokens DROP COLUMN rotation_generation;
--rollback ALTER TABLE auth_tokens DROP COLUMN session_id;

--changeset ninggiangboy:014-10-backfill-identity-from-users
-- Step 3 of the identity migration plan: give every existing account its target-model representation
-- so that both models describe the same reality before any reader switches over.
--
-- Every backfilled row carries its provenance honestly. Timestamps are copied from the source row
-- rather than stamped with a current instant, because the account holder did not come into existence
-- when this migration ran. Market context is left null and marked LEGACY_UNRECONCILED rather than
-- guessed: the multi-market design forbids inferring a market from a phone number or an address, and
-- a row marked legacy blocks consequential workflows until an operator reconciles it, which is the
-- intended outcome.
--
-- users.email and users.phone_number are already unique, so the verified-primary uniqueness on
-- contact_channels cannot be violated by this backfill.

INSERT INTO account_holders (
    holder_type, user_id, display_name, status, market_code, context_state, created_at, updated_at
)
SELECT
    'PERSON',
    u.id,
    u.display_name,
    CASE u.status WHEN 'DELETED' THEN 'CLOSED' WHEN 'SUSPENDED' THEN 'SUSPENDED' ELSE 'ACTIVE' END,
    NULL,
    'LEGACY_UNRECONCILED',
    u.created_at,
    u.updated_at
FROM users u;

INSERT INTO contact_channels (
    user_id, channel_type, normalized_value, original_value, purpose, is_primary,
    verified_at, verification_method, created_at, updated_at
)
SELECT
    u.id,
    'EMAIL',
    u.email,
    u.email,
    'ACCOUNT',
    true,
    u.email_verified_at,
    CASE WHEN u.email_verified_at IS NULL THEN NULL ELSE 'LEGACY' END,
    u.created_at,
    u.updated_at
FROM users u;

INSERT INTO contact_channels (
    user_id, channel_type, normalized_value, original_value, purpose, is_primary,
    verified_at, verification_method, created_at, updated_at
)
SELECT
    u.id,
    'PHONE',
    u.phone_number,
    u.phone_number,
    'ACCOUNT',
    true,
    u.phone_verified_at,
    CASE WHEN u.phone_verified_at IS NULL THEN NULL ELSE 'LEGACY' END,
    u.created_at,
    u.updated_at
FROM users u
WHERE u.phone_number IS NOT NULL;

INSERT INTO auth_credentials (
    user_id, credential_type, encoder_id, verifier_digest, enrolled_at, created_at, updated_at
)
SELECT u.id, 'PASSWORD', 'legacy', u.password_hash, u.created_at, u.created_at, u.updated_at
FROM users u;

-- The role-to-capability mapping below is the legacy equivalence, not a new authorization design.
-- Step 4 of the migration plan reconciles it: every protected route must evaluate identically under
-- the role check and the capability check before step 5 switches reads over. Scope is GLOBAL because
-- that is all a role ever expressed; resource-scoped grants such as "owns this listing" are created
-- by the supply domain, not derived from a role.
INSERT INTO capability_grants (
    grantee_type, grantee_id, role_name, capabilities, scope_type, scope_id,
    effective_from, reason_code, source, created_at, updated_at
)
SELECT
    'USER',
    ur.user_id,
    ur.role,
    CASE ur.role
        WHEN 'GUEST' THEN ARRAY[
            'BOOKING_CREATE', 'BOOKING_CANCEL_OWN', 'REVIEW_WRITE_OWN', 'MESSAGE_SEND']
        WHEN 'HOST' THEN ARRAY[
            'CAN_DRAFT', 'CAN_PUBLISH', 'CAN_ACCEPT_BOOKING', 'CAN_RECEIVE_PAYOUT',
            'LISTING_MANAGE_OWN', 'CALENDAR_MANAGE_OWN', 'MESSAGE_SEND']
        WHEN 'ADMIN' THEN ARRAY[
            'ACCOUNT_SUSPEND', 'ACCOUNT_REACTIVATE', 'CONFIGURATION_APPROVE',
            'SUPPORT_CASE_MANAGE']
    END,
    'GLOBAL',
    NULL,
    ur.created_at,
    'BACKFILL_FROM_USER_ROLES',
    'LEGACY',
    ur.created_at,
    ur.created_at
FROM user_roles ur;
--rollback DELETE FROM capability_grants WHERE source = 'LEGACY' AND reason_code = 'BACKFILL_FROM_USER_ROLES';
--rollback DELETE FROM auth_credentials WHERE encoder_id = 'legacy';
--rollback DELETE FROM contact_channels WHERE verification_method = 'LEGACY' OR verification_method IS NULL;
--rollback DELETE FROM account_holders WHERE context_state = 'LEGACY_UNRECONCILED';
