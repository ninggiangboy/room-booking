--liquibase formatted sql

-- Retires the legacy identity schema (migration 001) now that every identity workflow has moved
-- onto the target model (migration 014): account_holders becomes the single principal root, and
-- user_roles/host_profiles/users are dropped. There is no data to migrate in this application, which
-- is exactly why doing every rewrite honestly, rather than skipping it, costs nothing here. See
-- docs/data-model/README.md "What the schema does not yet prove" and
-- docs/data-model/014-identity-target-model.md for the staged cutover this changeset completes.
--
-- account_holders.id is a fresh UUID, not equal to users.id, so every child column that currently
-- holds a user id is rewritten through the account_holders.user_id join before that join column is
-- dropped. On a fresh database every rewrite below is a no-op.
--
-- Adaptation from the original plan: while auditing every foreign key into users, four more turned
-- up outside identity that the plan text did not name -- listings.host_id, bookings.guest_id,
-- bookings.host_id, reviews.reviewer_id, reviews.reviewee_id, and favorites.user_id (migrations 002,
-- 004, 006). None of those modules has live application code yet, so this changeset repoints them
-- the same way as the two the plan did name, but keeps their column names unchanged rather than
-- renaming them: a rename would only be cosmetic today, and the modules that eventually read these
-- columns are better positioned to choose their own naming when they gain live code.

--changeset ninggiangboy:037-01-account-holder-profile-fields
-- avatar_url is the one users column with no home in the target model; every other users column
-- (email, phone_number, display_name, status) already has one in contact_channels or account_holders.
ALTER TABLE account_holders ADD COLUMN avatar_url VARCHAR(2048);

UPDATE account_holders h
SET avatar_url = u.avatar_url
FROM users u
WHERE u.id = h.user_id;
--rollback ALTER TABLE account_holders DROP COLUMN avatar_url;

--changeset ninggiangboy:037-02-repoint-identity-satellites
-- contact_channels
UPDATE contact_channels c SET user_id = h.id FROM account_holders h WHERE h.user_id = c.user_id;
ALTER TABLE contact_channels RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE contact_channels DROP CONSTRAINT fk_contact_channels_user;
ALTER TABLE contact_channels
    ADD CONSTRAINT fk_contact_channels_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders (id);
ALTER INDEX idx_contact_channels_user RENAME TO idx_contact_channels_account_holder;

-- auth_credentials
UPDATE auth_credentials c SET user_id = h.id FROM account_holders h WHERE h.user_id = c.user_id;
ALTER TABLE auth_credentials RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE auth_credentials DROP CONSTRAINT fk_auth_credentials_user;
ALTER TABLE auth_credentials
    ADD CONSTRAINT fk_auth_credentials_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders (id);

-- auth_sessions: both user_id and revoked_by point at users today.
UPDATE auth_sessions s SET user_id = h.id FROM account_holders h WHERE h.user_id = s.user_id;
UPDATE auth_sessions s SET revoked_by = h.id FROM account_holders h WHERE h.user_id = s.revoked_by;
ALTER TABLE auth_sessions RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE auth_sessions RENAME COLUMN revoked_by TO revoked_by_account_holder_id;
ALTER TABLE auth_sessions DROP CONSTRAINT fk_auth_sessions_user;
ALTER TABLE auth_sessions DROP CONSTRAINT fk_auth_sessions_revoker;
ALTER TABLE auth_sessions
    ADD CONSTRAINT fk_auth_sessions_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders (id),
    ADD CONSTRAINT fk_auth_sessions_revoked_by
        FOREIGN KEY (revoked_by_account_holder_id) REFERENCES account_holders (id);

-- auth_tokens
UPDATE auth_tokens t SET user_id = h.id FROM account_holders h WHERE h.user_id = t.user_id;
ALTER TABLE auth_tokens RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE auth_tokens DROP CONSTRAINT fk_auth_tokens_user;
ALTER TABLE auth_tokens
    ADD CONSTRAINT fk_auth_tokens_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders (id);
ALTER INDEX idx_auth_tokens_user_type RENAME TO idx_auth_tokens_account_holder_type;

-- auth_attempts: user_id is nullable here, an unresolved attempt names only an identifier digest.
UPDATE auth_attempts a SET user_id = h.id FROM account_holders h WHERE h.user_id = a.user_id;
ALTER TABLE auth_attempts RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE auth_attempts DROP CONSTRAINT fk_auth_attempts_user;
ALTER TABLE auth_attempts
    ADD CONSTRAINT fk_auth_attempts_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders (id);
ALTER INDEX idx_auth_attempts_user_velocity RENAME TO idx_auth_attempts_account_holder_velocity;

-- organization_members: both user_id (the member) and invited_by point at users today.
UPDATE organization_members m SET user_id = h.id FROM account_holders h WHERE h.user_id = m.user_id;
UPDATE organization_members m
SET invited_by = h.id
FROM account_holders h
WHERE h.user_id = m.invited_by;
ALTER TABLE organization_members RENAME COLUMN user_id TO member_holder_id;
ALTER TABLE organization_members RENAME COLUMN invited_by TO invited_by_account_holder_id;
ALTER TABLE organization_members DROP CONSTRAINT fk_organization_members_user;
ALTER TABLE organization_members DROP CONSTRAINT fk_organization_members_inviter;
ALTER TABLE organization_members
    ADD CONSTRAINT fk_organization_members_member_holder
        FOREIGN KEY (member_holder_id) REFERENCES account_holders (id),
    ADD CONSTRAINT fk_organization_members_invited_by
        FOREIGN KEY (invited_by_account_holder_id) REFERENCES account_holders (id);
ALTER INDEX idx_organization_members_user RENAME TO idx_organization_members_member_holder;
--rollback ALTER INDEX idx_organization_members_member_holder RENAME TO idx_organization_members_user;
--rollback ALTER TABLE organization_members DROP CONSTRAINT fk_organization_members_invited_by;
--rollback ALTER TABLE organization_members DROP CONSTRAINT fk_organization_members_member_holder;
--rollback ALTER TABLE organization_members ADD CONSTRAINT fk_organization_members_inviter FOREIGN KEY (invited_by_account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE organization_members ADD CONSTRAINT fk_organization_members_user FOREIGN KEY (member_holder_id) REFERENCES users (id);
--rollback ALTER TABLE organization_members RENAME COLUMN invited_by_account_holder_id TO invited_by;
--rollback ALTER TABLE organization_members RENAME COLUMN member_holder_id TO user_id;
--rollback ALTER INDEX idx_auth_attempts_account_holder_velocity RENAME TO idx_auth_attempts_user_velocity;
--rollback ALTER TABLE auth_attempts DROP CONSTRAINT fk_auth_attempts_account_holder;
--rollback ALTER TABLE auth_attempts ADD CONSTRAINT fk_auth_attempts_user FOREIGN KEY (account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE auth_attempts RENAME COLUMN account_holder_id TO user_id;
--rollback ALTER INDEX idx_auth_tokens_account_holder_type RENAME TO idx_auth_tokens_user_type;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT fk_auth_tokens_account_holder;
--rollback ALTER TABLE auth_tokens ADD CONSTRAINT fk_auth_tokens_user FOREIGN KEY (account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE auth_tokens RENAME COLUMN account_holder_id TO user_id;
--rollback ALTER TABLE auth_sessions DROP CONSTRAINT fk_auth_sessions_revoked_by;
--rollback ALTER TABLE auth_sessions DROP CONSTRAINT fk_auth_sessions_account_holder;
--rollback ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_sessions_revoker FOREIGN KEY (revoked_by_account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_sessions_user FOREIGN KEY (account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE auth_sessions RENAME COLUMN revoked_by_account_holder_id TO revoked_by;
--rollback ALTER TABLE auth_sessions RENAME COLUMN account_holder_id TO user_id;
--rollback ALTER TABLE auth_credentials DROP CONSTRAINT fk_auth_credentials_account_holder;
--rollback ALTER TABLE auth_credentials ADD CONSTRAINT fk_auth_credentials_user FOREIGN KEY (account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE auth_credentials RENAME COLUMN account_holder_id TO user_id;
--rollback ALTER INDEX idx_contact_channels_account_holder RENAME TO idx_contact_channels_user;
--rollback ALTER TABLE contact_channels DROP CONSTRAINT fk_contact_channels_account_holder;
--rollback ALTER TABLE contact_channels ADD CONSTRAINT fk_contact_channels_user FOREIGN KEY (account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE contact_channels RENAME COLUMN account_holder_id TO user_id;

--changeset ninggiangboy:037-03-repoint-external-references
-- hostverification.verification_appeals
UPDATE verification_appeals a
SET submitted_by = h.id
FROM account_holders h
WHERE h.user_id = a.submitted_by;
ALTER TABLE verification_appeals RENAME COLUMN submitted_by TO submitted_by_account_holder_id;
ALTER TABLE verification_appeals DROP CONSTRAINT fk_verification_appeals_submitter;
ALTER TABLE verification_appeals
    ADD CONSTRAINT fk_verification_appeals_submitter
        FOREIGN KEY (submitted_by_account_holder_id) REFERENCES account_holders (id);

-- supply.property_collaborators
UPDATE property_collaborators c SET user_id = h.id FROM account_holders h WHERE h.user_id = c.user_id;
ALTER TABLE property_collaborators RENAME COLUMN user_id TO account_holder_id;
ALTER TABLE property_collaborators DROP CONSTRAINT fk_property_collaborators_user;
ALTER TABLE property_collaborators
    ADD CONSTRAINT fk_property_collaborators_account_holder
        FOREIGN KEY (account_holder_id) REFERENCES account_holders (id);
ALTER INDEX idx_property_collaborators_user RENAME TO idx_property_collaborators_account_holder;

-- The four FKs below were not named by the original plan text (see the header note); they are
-- repointed with their column names left unchanged, since no live Java code reads them yet.
UPDATE listings l SET host_id = h.id FROM account_holders h WHERE h.user_id = l.host_id;
ALTER TABLE listings DROP CONSTRAINT fk_listings_host;
ALTER TABLE listings
    ADD CONSTRAINT fk_listings_host FOREIGN KEY (host_id) REFERENCES account_holders (id);

UPDATE bookings b SET guest_id = h.id FROM account_holders h WHERE h.user_id = b.guest_id;
UPDATE bookings b SET host_id = h.id FROM account_holders h WHERE h.user_id = b.host_id;
ALTER TABLE bookings DROP CONSTRAINT fk_bookings_guest;
ALTER TABLE bookings DROP CONSTRAINT fk_bookings_host;
ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_guest FOREIGN KEY (guest_id) REFERENCES account_holders (id),
    ADD CONSTRAINT fk_bookings_host FOREIGN KEY (host_id) REFERENCES account_holders (id);

UPDATE reviews r SET reviewer_id = h.id FROM account_holders h WHERE h.user_id = r.reviewer_id;
UPDATE reviews r SET reviewee_id = h.id FROM account_holders h WHERE h.user_id = r.reviewee_id;
ALTER TABLE reviews DROP CONSTRAINT fk_reviews_reviewer;
ALTER TABLE reviews DROP CONSTRAINT fk_reviews_reviewee;
ALTER TABLE reviews
    ADD CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES account_holders (id),
    ADD CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES account_holders (id);

UPDATE favorites f SET user_id = h.id FROM account_holders h WHERE h.user_id = f.user_id;
ALTER TABLE favorites DROP CONSTRAINT fk_favorites_user;
ALTER TABLE favorites
    ADD CONSTRAINT fk_favorites_user
        FOREIGN KEY (user_id) REFERENCES account_holders (id) ON DELETE CASCADE;
--rollback ALTER TABLE favorites DROP CONSTRAINT fk_favorites_user;
--rollback ALTER TABLE favorites ADD CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
--rollback ALTER TABLE reviews DROP CONSTRAINT fk_reviews_reviewee;
--rollback ALTER TABLE reviews DROP CONSTRAINT fk_reviews_reviewer;
--rollback ALTER TABLE reviews ADD CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users (id);
--rollback ALTER TABLE reviews ADD CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id);
--rollback ALTER TABLE bookings DROP CONSTRAINT fk_bookings_host;
--rollback ALTER TABLE bookings DROP CONSTRAINT fk_bookings_guest;
--rollback ALTER TABLE bookings ADD CONSTRAINT fk_bookings_host FOREIGN KEY (host_id) REFERENCES users (id);
--rollback ALTER TABLE bookings ADD CONSTRAINT fk_bookings_guest FOREIGN KEY (guest_id) REFERENCES users (id);
--rollback ALTER TABLE listings DROP CONSTRAINT fk_listings_host;
--rollback ALTER TABLE listings ADD CONSTRAINT fk_listings_host FOREIGN KEY (host_id) REFERENCES users (id);
--rollback ALTER INDEX idx_property_collaborators_account_holder RENAME TO idx_property_collaborators_user;
--rollback ALTER TABLE property_collaborators DROP CONSTRAINT fk_property_collaborators_account_holder;
--rollback ALTER TABLE property_collaborators ADD CONSTRAINT fk_property_collaborators_user FOREIGN KEY (account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE property_collaborators RENAME COLUMN account_holder_id TO user_id;
--rollback ALTER TABLE verification_appeals DROP CONSTRAINT fk_verification_appeals_submitter;
--rollback ALTER TABLE verification_appeals ADD CONSTRAINT fk_verification_appeals_submitter FOREIGN KEY (submitted_by_account_holder_id) REFERENCES users (id);
--rollback ALTER TABLE verification_appeals RENAME COLUMN submitted_by_account_holder_id TO submitted_by;

--changeset ninggiangboy:037-04-rewrite-capability-grants
-- Migration 014's backfill wrote grantee_id as a user id and grantee_type as 'USER'; rewrite both to
-- the holder-id, 'PERSON' space every other identity table now uses. This also fixes the latent bug
-- described in the plan: the Java GrantSource and PrincipalType enums never had a LEGACY/USER pair
-- that matched the database's check constraints, so reading a backfilled grant would have thrown.
UPDATE capability_grants g
SET grantee_id = h.id
FROM account_holders h
WHERE h.user_id = g.grantee_id
  AND g.grantee_type = 'USER';

UPDATE capability_grants SET grantee_type = 'PERSON' WHERE grantee_type = 'USER';
UPDATE capability_grants SET source = 'SELF_SERVICE' WHERE source = 'LEGACY';

ALTER TABLE capability_grants DROP CONSTRAINT ck_capability_grants_grantee_type;
ALTER TABLE capability_grants
    ADD CONSTRAINT ck_capability_grants_grantee_type
        CHECK (grantee_type IN ('PERSON', 'ORGANIZATION', 'SERVICE'));

ALTER TABLE capability_grants DROP CONSTRAINT ck_capability_grants_source;
ALTER TABLE capability_grants
    ADD CONSTRAINT ck_capability_grants_source
        CHECK (source IN ('SELF_SERVICE', 'DELEGATION', 'COMPLIANCE', 'RISK', 'GOVERNANCE'));

-- Anything registered after 014 but never granted a capability_grants row -- because it registered
-- before this application's registration workflow started dual-writing one -- gets one now, so
-- nothing loses its role in the cutover.
INSERT INTO capability_grants (
    grantee_type, grantee_id, role_name, capabilities, scope_type, scope_id,
    effective_from, reason_code, source, created_at, updated_at
)
SELECT
    'PERSON',
    h.id,
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
    'SELF_SERVICE',
    ur.created_at,
    ur.created_at
FROM user_roles ur
JOIN account_holders h ON h.user_id = ur.user_id
WHERE NOT EXISTS (
    SELECT 1 FROM capability_grants g
    WHERE g.grantee_id = h.id AND g.role_name = ur.role AND g.revoked_at IS NULL
);
-- Rolling back cannot distinguish a grant this changeset inserted from one a genuine self-service
-- host-onboarding request created in the meantime, so the inserted rows are left in place; this
-- matches the forward-only discipline the plan calls for. The constraint widening and grantee_type
-- reversal below are the reversible parts of this changeset.
--rollback ALTER TABLE capability_grants DROP CONSTRAINT ck_capability_grants_source;
--rollback ALTER TABLE capability_grants ADD CONSTRAINT ck_capability_grants_source CHECK (source IN ('SELF_SERVICE', 'DELEGATION', 'COMPLIANCE', 'RISK', 'GOVERNANCE', 'LEGACY'));
--rollback ALTER TABLE capability_grants DROP CONSTRAINT ck_capability_grants_grantee_type;
--rollback ALTER TABLE capability_grants ADD CONSTRAINT ck_capability_grants_grantee_type CHECK (grantee_type IN ('USER', 'ORGANIZATION', 'SERVICE'));
--rollback UPDATE capability_grants SET grantee_type = 'USER' WHERE grantee_type = 'PERSON';

--changeset ninggiangboy:037-05-drop-account-holder-user-link
ALTER TABLE account_holders DROP CONSTRAINT fk_account_holders_user;
ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_person_user;
DROP INDEX uk_account_holders_person_user;
ALTER TABLE account_holders DROP COLUMN user_id;
--rollback ALTER TABLE account_holders ADD COLUMN user_id UUID;
--rollback CREATE UNIQUE INDEX uk_account_holders_person_user ON account_holders (user_id) WHERE holder_type = 'PERSON';
--rollback ALTER TABLE account_holders ADD CONSTRAINT ck_account_holders_person_user CHECK ((holder_type = 'PERSON') = (user_id IS NOT NULL));
--rollback ALTER TABLE account_holders ADD CONSTRAINT fk_account_holders_user FOREIGN KEY (user_id) REFERENCES users (id);

--changeset ninggiangboy:037-06-drop-legacy-tables
DROP TABLE host_profiles;
DROP TABLE user_roles;
DROP TABLE users;
--rollback CREATE TABLE users (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), email CITEXT NOT NULL, phone_number VARCHAR(32), display_name VARCHAR(120) NOT NULL, avatar_url VARCHAR(2048), status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_users_email UNIQUE (email), CONSTRAINT uk_users_phone_number UNIQUE (phone_number), CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')), CONSTRAINT ck_users_version CHECK (version >= 0));
--rollback CREATE TABLE user_roles (user_id UUID NOT NULL, role VARCHAR(16) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role), CONSTRAINT ck_user_roles_role CHECK (role IN ('GUEST', 'HOST', 'ADMIN')));
--rollback CREATE TABLE host_profiles (user_id UUID PRIMARY KEY, bio TEXT, identity_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED', average_rating NUMERIC(3, 2), review_count INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0);

--changeset ninggiangboy:037-07-rename-unresolved-market-context
-- "Legacy" stopped being a thing that exists once the legacy schema was dropped by the previous
-- changeset; UNRESOLVED names the same fact -- no market has been recorded for this holder -- without
-- implying it came from an old system.
DROP INDEX idx_account_holders_unreconciled;
UPDATE account_holders SET context_state = 'UNRESOLVED' WHERE context_state = 'LEGACY_UNRECONCILED';
ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_context;
ALTER TABLE account_holders
    ADD CONSTRAINT ck_account_holders_context CHECK (context_state IN ('RESOLVED', 'UNRESOLVED'));
CREATE INDEX idx_account_holders_unreconciled
    ON account_holders (created_at)
    WHERE context_state = 'UNRESOLVED';
--rollback DROP INDEX idx_account_holders_unreconciled;
--rollback ALTER TABLE account_holders DROP CONSTRAINT ck_account_holders_context;
--rollback ALTER TABLE account_holders ADD CONSTRAINT ck_account_holders_context CHECK (context_state IN ('RESOLVED', 'LEGACY_UNRECONCILED'));
--rollback UPDATE account_holders SET context_state = 'LEGACY_UNRECONCILED' WHERE context_state = 'UNRESOLVED';
--rollback CREATE INDEX idx_account_holders_unreconciled ON account_holders (created_at) WHERE context_state = 'LEGACY_UNRECONCILED';

--changeset ninggiangboy:037-08-require-session-on-refresh-tokens
-- session_id was nullable only "for tokens issued before sessions existed" (migration 014's comment).
-- On a database where users never existed without sessions, and now that the legacy schema this
-- fallback protected is gone, every refresh token must carry a session. This is what lets the dead
-- session-less fallback branch in RefreshTokenService be deleted rather than merely left unreachable.
ALTER TABLE auth_tokens
    ADD CONSTRAINT ck_auth_tokens_refresh_requires_session
        CHECK ((type = 'REFRESH_TOKEN') = (session_id IS NOT NULL));
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_refresh_requires_session;
