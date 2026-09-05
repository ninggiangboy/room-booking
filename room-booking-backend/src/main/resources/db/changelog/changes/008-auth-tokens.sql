--liquibase formatted sql

--changeset ninggiangboy:008-01-generalize-auth-tokens
ALTER TABLE email_verification_tokens RENAME TO auth_tokens;
ALTER TABLE auth_tokens ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'EMAIL_VERIFICATION';
ALTER TABLE auth_tokens ALTER COLUMN type DROP DEFAULT;

ALTER TABLE auth_tokens
    RENAME CONSTRAINT fk_email_verification_tokens_user TO fk_auth_tokens_user;
ALTER TABLE auth_tokens
    RENAME CONSTRAINT uk_email_verification_tokens_hash TO uk_auth_tokens_hash;
ALTER TABLE auth_tokens
    RENAME CONSTRAINT ck_email_verification_tokens_version TO ck_auth_tokens_version;
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (
    type IN ('EMAIL_VERIFICATION', 'REFRESH_TOKEN')
);

DROP INDEX idx_email_verification_tokens_user;
DROP INDEX idx_email_verification_tokens_expiry;
CREATE INDEX idx_auth_tokens_user_type
    ON auth_tokens (user_id, type, consumed_at);
CREATE INDEX idx_auth_tokens_type_expiry
    ON auth_tokens (type, expires_at) WHERE consumed_at IS NULL;
--rollback DROP INDEX idx_auth_tokens_type_expiry;
--rollback DROP INDEX idx_auth_tokens_user_type;
--rollback CREATE INDEX idx_email_verification_tokens_expiry ON auth_tokens (expires_at) WHERE consumed_at IS NULL;
--rollback CREATE INDEX idx_email_verification_tokens_user ON auth_tokens (user_id, consumed_at);
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
--rollback ALTER TABLE auth_tokens RENAME CONSTRAINT ck_auth_tokens_version TO ck_email_verification_tokens_version;
--rollback ALTER TABLE auth_tokens RENAME CONSTRAINT uk_auth_tokens_hash TO uk_email_verification_tokens_hash;
--rollback ALTER TABLE auth_tokens RENAME CONSTRAINT fk_auth_tokens_user TO fk_email_verification_tokens_user;
--rollback ALTER TABLE auth_tokens DROP COLUMN type;
--rollback ALTER TABLE auth_tokens RENAME TO email_verification_tokens;
