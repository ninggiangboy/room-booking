--liquibase formatted sql

-- Self-service contact-channel management (adding a channel beyond the primary email registration
-- creates, and proving control of it) needs its own token type. EMAIL_VERIFICATION never needed to
-- name which channel it proves control of, because verification always resolved to "the holder's
-- current primary email channel" -- but a holder managing more than one channel of the same type
-- needs the token to name the specific row it was issued for.

--changeset ninggiangboy:039-01-contact-channel-verification-token-type
ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (
    type IN (
        'EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH_TOKEN', 'CONTACT_CHANNEL_VERIFICATION'
    )
);

ALTER TABLE auth_tokens ADD COLUMN channel_id UUID;
ALTER TABLE auth_tokens
    ADD CONSTRAINT fk_auth_tokens_channel FOREIGN KEY (channel_id) REFERENCES contact_channels (id);

-- Only an outstanding contact-channel verification token is ever looked up by channel; a consumed
-- or superseded one is found through the account/type/status index that already exists.
CREATE INDEX idx_auth_tokens_channel_pending
    ON auth_tokens (channel_id)
    WHERE consumed_at IS NULL AND channel_id IS NOT NULL;
--rollback DROP INDEX idx_auth_tokens_channel_pending;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT fk_auth_tokens_channel;
--rollback ALTER TABLE auth_tokens DROP COLUMN channel_id;
--rollback ALTER TABLE auth_tokens DROP CONSTRAINT ck_auth_tokens_type;
--rollback ALTER TABLE auth_tokens ADD CONSTRAINT ck_auth_tokens_type CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'REFRESH_TOKEN'));

-- A holder removing a secondary channel through self-service must not turn into a row deletion:
-- a consumed auth_tokens row can still point at the channel through the FK just added above, and a
-- removed channel is evidence (what a holder once claimed reachability through), not a mistake to
-- erase. revoked_at is therefore a fourth independent instant alongside created_at/updated_at/
-- verified_at, following the same "status column, never a DELETE" discipline account_holders and
-- capability_restrictions already use.
--changeset ninggiangboy:039-02-contact-channel-self-removal
ALTER TABLE contact_channels ADD COLUMN revoked_at TIMESTAMPTZ;

DROP INDEX uk_contact_channels_one_primary;
CREATE UNIQUE INDEX uk_contact_channels_one_primary
    ON contact_channels (account_holder_id, channel_type)
    WHERE is_primary = true AND superseded_by IS NULL AND revoked_at IS NULL;
--rollback DROP INDEX uk_contact_channels_one_primary;
--rollback CREATE UNIQUE INDEX uk_contact_channels_one_primary ON contact_channels (account_holder_id, channel_type) WHERE is_primary = true AND superseded_by IS NULL;
--rollback ALTER TABLE contact_channels DROP COLUMN revoked_at;
