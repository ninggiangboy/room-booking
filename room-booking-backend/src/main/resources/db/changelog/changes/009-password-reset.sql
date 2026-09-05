--liquibase formatted sql

--changeset ninggiangboy:009-01-password-reset-token-type
ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (
    type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH_TOKEN')
);
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
--rollback ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (type IN ('EMAIL_VERIFICATION', 'REFRESH_TOKEN'));
