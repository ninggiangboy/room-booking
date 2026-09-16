--liquibase formatted sql

-- Step-up (proving a stronger factor immediately before a sensitive action) needs a short-lived
-- proof a workflow can present after the TOTP check that produced it. Reusing auth_tokens keeps the
-- one-time-secret pattern (only a digest persisted, consumed exactly once) this codebase already
-- applies to every other opaque credential, rather than inventing a second mechanism.

--changeset ninggiangboy:040-01-step-up-token-type
ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (
    type IN (
        'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH_TOKEN', 'CONTACT_CHANNEL_VERIFICATION',
        'STEP_UP'
    )
);
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
--rollback ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH_TOKEN', 'CONTACT_CHANNEL_VERIFICATION'));
