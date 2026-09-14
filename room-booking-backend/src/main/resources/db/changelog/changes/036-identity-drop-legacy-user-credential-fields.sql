--liquibase formatted sql

-- Migration 014 already backfilled every users row into its target-model equivalent: the password
-- hash into auth_credentials (PASSWORD), and email/phone verification into contact_channels (EMAIL
-- and PHONE). Live application code has now moved onto those tables (see
-- docs/modules/identity.md), so users.password_hash, users.email_verified_at, and
-- users.phone_verified_at are dead columns nothing reads or writes anymore.

--changeset ninggiangboy:036-01-drop-legacy-user-credential-fields
ALTER TABLE users
    DROP COLUMN password_hash,
    DROP COLUMN email_verified_at,
    DROP COLUMN phone_verified_at;
--rollback ALTER TABLE users ADD COLUMN password_hash VARCHAR(255), ADD COLUMN email_verified_at TIMESTAMPTZ, ADD COLUMN phone_verified_at TIMESTAMPTZ;
