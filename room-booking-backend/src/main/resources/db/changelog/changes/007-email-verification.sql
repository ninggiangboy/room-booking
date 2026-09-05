--liquibase formatted sql

--changeset ninggiangboy:007-01-email-verification-tokens
CREATE TABLE email_verification_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_email_verification_tokens_version CHECK (version >= 0)
);

CREATE INDEX idx_email_verification_tokens_user
    ON email_verification_tokens (user_id, consumed_at);
CREATE INDEX idx_email_verification_tokens_expiry
    ON email_verification_tokens (expires_at) WHERE consumed_at IS NULL;
--rollback DROP TABLE email_verification_tokens;
