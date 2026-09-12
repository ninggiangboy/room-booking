--liquibase formatted sql

-- Communication reports facts; it does not create them. This migration exists so that what the
-- platform said to somebody is a durable, attributable record separate from the domain event that
-- caused it -- and so that a message nobody was entitled to send, or a notice sent twice, is refused
-- by the database rather than noticed later in a support case.
--
-- Six things force the shape:
--
--   A conversation is a scope, not a pair of users. Threads exist for an inquiry, a booking, an
--   incident or a support case, and membership follows current authority over that scope rather than
--   who happened to be on the booking snapshot. A co-host removed today must not read tomorrow's
--   messages, so participation is a row with a joined and a left instant, and at most one active
--   membership per actor and role.
--
--   Order inside a conversation is a number, not a timestamp. Pagination, unread counts and dispute
--   evidence all depend on a strictly increasing per-conversation sequence, and two concurrent sends
--   must not be able to take the same one. The conversation carries the allocator, the unique key
--   refuses a collision, and a trigger refuses a message whose sequence the conversation never
--   issued.
--
--   A message is evidence the moment it is sent. Corrections are revisions that name the original;
--   withdrawal hides a presentation without destroying what was said; translation and moderation are
--   derived views that sit beside the original rather than replacing it. Nobody can erase another
--   participant's copy of material dispute evidence, so the original row is frozen at insert and only
--   its visibility may still move.
--
--   Delivery is at-least-once, but the user-visible notice is effectively once. One committed domain
--   event, one recipient, one purpose, one policy version means one intent -- a unique key, not a
--   convention -- and an operator cannot escape it by choosing a different template or provider
--   route. Attempts and provider observations hang below the intent, where retrying is safe.
--
--   Consent and health are destination facts, not message facts. Whether a person may be sent a
--   marketing notice, and whether an address still accepts mail, are answers that outlive any one
--   send. They live in their own rows, keyed to the identity-owned contact channel, so suppression is
--   immediate for everything that follows it.
--
--   Sensitive content is referenced, not copied. Message bodies may be stored encrypted elsewhere,
--   attachments live in object storage behind scan states, rendered artifacts are references and
--   hashes. What stays in these tables is the metadata required to authorize, order, retry and audit.
--
-- Note on what this migration does not create. The document proposes outbox_events, inbox_receipts,
-- provider_webhook_inbox, idempotency_records, policy_versions and audit_records as shared records.
-- Migration 012 delivered command_idempotency_records, outbox_events, consumer_inbox_receipts and
-- append-only audit_events; migration 021 delivered payment_webhook_deliveries for provider callbacks
-- and 013 delivered provider_accounts. A second copy of any of them would mean two answers to the
-- same question and a second publisher to operate. Intents therefore carry a source event reference
-- that resolves against the existing inbox, and attempts carry a provider account reference.
--
-- Note on destinations. The document is explicit that raw email addresses and telephone numbers must
-- not be duplicated across the platform. Nothing here stores one. An intent points at the identity
-- domain's contact_channels row and the version of it that was resolved; delivery health is keyed the
-- same way. The only protected snapshot kept is the provider's own reference for an attempt, which is
-- what a retry or a dispute needs.
--
-- Note on the stay-operations half of the feature document. It is a separate deployable slice and
-- becomes migration 025; the two halves share only the notification tables created here.

--changeset ninggiangboy:024-01-conversations
-- The thread itself: what it is about, whether it still accepts messages, and the allocator for
-- message order. next_sequence is the number the next message will take, so an empty conversation
-- starts at 1 and the counter never moves backwards.
CREATE TABLE conversations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope_type                  VARCHAR(16) NOT NULL,
    scope_reference_id          UUID NOT NULL,

    -- Denormalized handles for authorization and routing. They are the scope restated, not a second
    -- source of truth: a BOOKING conversation's scope_reference_id is its booking.
    listing_id                  UUID,
    booking_id                  UUID,
    market_code                 VARCHAR(2),

    status                      VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    restriction_reason          VARCHAR(48),
    closed_at                   TIMESTAMPTZ,
    closure_reason              VARCHAR(48),

    next_sequence               BIGINT NOT NULL DEFAULT 1,
    last_message_at             TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_conversations_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_conversations_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_conversations_market FOREIGN KEY (market_code) REFERENCES markets (market_code),
    CONSTRAINT ck_conversations_scope CHECK (
        scope_type IN ('INQUIRY', 'BOOKING', 'INCIDENT', 'SUPPORT')
    ),
    CONSTRAINT ck_conversations_status CHECK (
        status IN ('OPEN', 'RESTRICTED', 'CLOSED', 'ARCHIVED')
    ),
    -- A restricted thread that cannot say why it is restricted is a thread nobody can appeal.
    CONSTRAINT ck_conversations_restriction CHECK (
        status <> 'RESTRICTED' OR restriction_reason IS NOT NULL
    ),
    CONSTRAINT ck_conversations_closure CHECK (
        status NOT IN ('CLOSED', 'ARCHIVED')
            OR (closed_at IS NOT NULL AND closure_reason IS NOT NULL)
    ),
    CONSTRAINT ck_conversations_sequence CHECK (next_sequence >= 1),
    CONSTRAINT ck_conversations_version CHECK (version >= 0)
);

-- One live thread per scope. An inquiry converted to a booking gets its own booking conversation
-- rather than broadening the pre-booking history, which is why the key is the scope and not the pair
-- of people in it. Archived threads are excluded so a scope can be re-opened after retention closes
-- the old one.
CREATE UNIQUE INDEX uk_conversations_active_scope ON conversations (scope_type, scope_reference_id)
    WHERE status <> 'ARCHIVED';
CREATE INDEX idx_conversations_booking ON conversations (booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX idx_conversations_listing ON conversations (listing_id) WHERE listing_id IS NOT NULL;
CREATE INDEX idx_conversations_recent ON conversations (last_message_at DESC NULLS LAST);
--rollback DROP TABLE conversations;

--changeset ninggiangboy:024-02-conversation-participants
-- Who may read and write, and on whose authority. Permissions are enumerated per participant instead
-- of being derived from the role at read time, because "the host" is a changing set of people and the
-- thread must remember which of them was entitled when.
CREATE TABLE conversation_participants (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id             UUID NOT NULL,
    account_holder_id           UUID,
    actor_type                  VARCHAR(16) NOT NULL,
    participant_role            VARCHAR(16) NOT NULL,

    -- Capabilities, not a role name: READ, SEND, UPLOAD, MANAGE_TEMPLATES, VIEW_SENSITIVE, MODERATE.
    permissions                 VARCHAR(24)[] NOT NULL DEFAULT '{}',

    -- Why this person is here, and which version of that authority was checked. A support agent's
    -- row points at the case assignment that elevated them; a co-host's at the collaborator record.
    authority_source            VARCHAR(32) NOT NULL,
    authority_reference_id      UUID,
    authority_version           INTEGER,
    purpose_code                VARCHAR(48),
    elevation_expires_at        TIMESTAMPTZ,

    joined_at                   TIMESTAMPTZ NOT NULL,
    left_at                     TIMESTAMPTZ,
    removal_reason              VARCHAR(48),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_conversation_participants_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id),
    CONSTRAINT fk_conversation_participants_actor FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_conversation_participants_actor_type CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER', 'ANONYMOUS')
    ),
    CONSTRAINT ck_conversation_participants_role CHECK (
        participant_role IN ('GUEST', 'HOST', 'CO_HOST', 'SUPPORT', 'SYSTEM')
    ),
    -- Only the platform itself speaks without an account holder behind it.
    CONSTRAINT ck_conversation_participants_identity CHECK (
        (participant_role = 'SYSTEM') = (account_holder_id IS NULL)
    ),
    CONSTRAINT ck_conversation_participants_authority CHECK (
        authority_source IN ('BOOKING_PARTY', 'LISTING_OPERATOR', 'PROPERTY_COLLABORATOR',
                             'SUPPORT_ASSIGNMENT', 'MODERATION_ACCESS', 'PLATFORM')
    ),
    -- Support and moderation access is purpose-bound and time-bound; ordinary membership is neither.
    CONSTRAINT ck_conversation_participants_elevation CHECK (
        authority_source NOT IN ('SUPPORT_ASSIGNMENT', 'MODERATION_ACCESS')
            OR (purpose_code IS NOT NULL AND elevation_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_conversation_participants_departure CHECK (
        left_at IS NULL OR (left_at >= joined_at AND removal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_conversation_participants_version CHECK (version >= 0)
);

-- One active membership per actor and role. Rejoining after removal is a new row, so the thread can
-- still show that somebody was present for the messages sent while they were.
CREATE UNIQUE INDEX uk_conversation_participants_active
    ON conversation_participants (conversation_id, account_holder_id, participant_role)
    WHERE left_at IS NULL AND account_holder_id IS NOT NULL;
CREATE INDEX idx_conversation_participants_actor
    ON conversation_participants (account_holder_id, conversation_id)
    WHERE left_at IS NULL;
CREATE INDEX idx_conversation_participants_elevation
    ON conversation_participants (elevation_expires_at)
    WHERE left_at IS NULL AND elevation_expires_at IS NOT NULL;
--rollback DROP TABLE conversation_participants;

--changeset ninggiangboy:024-03-messages
-- One thing somebody said, or one fact the platform reported. The body may live here or behind a
-- reference to encrypted storage, but the content hash stays in the row either way: it is what makes
-- a later claim about what was written checkable.
CREATE TABLE messages (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id             UUID NOT NULL,
    sequence_number             BIGINT NOT NULL,

    sender_participant_id       UUID,
    sender_account_holder_id    UUID,
    message_type                VARCHAR(24) NOT NULL,

    body_text                   TEXT,
    body_reference              VARCHAR(256),
    content_hash                CHAR(64) NOT NULL,
    declared_language           VARCHAR(16),
    detected_language           VARCHAR(16),

    reply_to_message_id         UUID,
    idempotency_key             VARCHAR(64),

    -- Where a system-generated message came from, so it can be replayed and so nobody can hand-write
    -- one that claims a domain fact never committed.
    source_event_id             UUID,
    source_event_type           VARCHAR(64),
    action_type                 VARCHAR(32),
    action_resource_id          UUID,
    action_expires_at           TIMESTAMPTZ,

    visibility_state            VARCHAR(16) NOT NULL DEFAULT 'VISIBLE',
    latest_revision_number      SMALLINT NOT NULL DEFAULT 0,
    withdrawn_at                TIMESTAMPTZ,
    sensitivity_class           VARCHAR(16) NOT NULL DEFAULT 'CONFIDENTIAL',
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_participant FOREIGN KEY (sender_participant_id)
        REFERENCES conversation_participants (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_messages_reply_to FOREIGN KEY (reply_to_message_id) REFERENCES messages (id),
    CONSTRAINT uk_messages_sequence UNIQUE (conversation_id, sequence_number),
    CONSTRAINT ck_messages_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_messages_type CHECK (
        message_type IN ('TEXT', 'MEDIA', 'ATTACHMENT', 'SYSTEM_FACT', 'STRUCTURED_ACTION',
                         'INSTRUCTION_UPDATE', 'INCIDENT_UPDATE')
    ),
    -- A body is stored in exactly one place. Two copies would let them disagree, and the hash could
    -- then only vouch for one of them.
    CONSTRAINT ck_messages_body_single CHECK (
        NOT (body_text IS NOT NULL AND body_reference IS NOT NULL)
    ),
    CONSTRAINT ck_messages_body_present CHECK (
        message_type <> 'TEXT' OR body_text IS NOT NULL OR body_reference IS NOT NULL
    ),
    -- A message the platform wrote cites the committed fact it reports; a message a person wrote
    -- names the participant row that entitled them to write it.
    CONSTRAINT ck_messages_authorship CHECK (
        (message_type IN ('SYSTEM_FACT', 'INSTRUCTION_UPDATE', 'INCIDENT_UPDATE')
            AND sender_participant_id IS NULL AND sender_account_holder_id IS NULL)
        OR (message_type NOT IN ('SYSTEM_FACT', 'INSTRUCTION_UPDATE', 'INCIDENT_UPDATE')
            AND sender_participant_id IS NOT NULL AND sender_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_messages_system_source CHECK (
        message_type <> 'SYSTEM_FACT' OR (source_event_id IS NOT NULL AND source_event_type IS NOT NULL)
    ),
    -- A button carries a server-issued action and the resource it acts on. It is never an amount and
    -- never a transition, so there is nowhere here to put one.
    CONSTRAINT ck_messages_action CHECK (
        (message_type = 'STRUCTURED_ACTION')
            = (action_type IS NOT NULL AND action_resource_id IS NOT NULL)
    ),
    CONSTRAINT ck_messages_visibility CHECK (
        visibility_state IN ('VISIBLE', 'WITHDRAWN', 'QUARANTINED', 'MASKED')
    ),
    CONSTRAINT ck_messages_withdrawal CHECK (
        (visibility_state = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)
    ),
    CONSTRAINT ck_messages_revision CHECK (latest_revision_number >= 0),
    CONSTRAINT ck_messages_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_messages_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_messages_version CHECK (version >= 0)
);

-- A retried send with the same key returns the original message instead of writing a second one.
-- Partial because system messages have no sender and carry no client key.
CREATE UNIQUE INDEX uk_messages_sender_idempotency
    ON messages (conversation_id, sender_account_holder_id, idempotency_key)
    WHERE sender_account_holder_id IS NOT NULL AND idempotency_key IS NOT NULL;
CREATE INDEX idx_messages_conversation_recent ON messages (conversation_id, sequence_number DESC);
CREATE INDEX idx_messages_sender ON messages (sender_account_holder_id, created_at DESC)
    WHERE sender_account_holder_id IS NOT NULL;
CREATE INDEX idx_messages_hold ON messages (conversation_id) WHERE legal_hold;
--rollback DROP TABLE messages;

--changeset ninggiangboy:024-04-message-revisions
-- A correction or a withdrawal, recorded beside the message it changes. The original message row is
-- never edited, so a dispute can always be shown both what was written and what replaced it.
CREATE TABLE message_revisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id                  UUID NOT NULL,
    revision_number             SMALLINT NOT NULL,
    revision_kind               VARCHAR(24) NOT NULL,

    replacement_body_text       TEXT,
    replacement_body_reference  VARCHAR(256),
    replacement_content_hash    CHAR(64),

    actor_type                  VARCHAR(16) NOT NULL,
    actor_account_holder_id     UUID,
    reason_code                 VARCHAR(48) NOT NULL,
    policy_reference            VARCHAR(64),

    created_at                  TIMESTAMPTZ NOT NULL,
    -- The FK restricts rather than cascades: changeset 024-20 makes these rows append-only, so a
    -- cascade would fire the trigger and fail. Deleting a message means deleting its revisions first,
    -- under retention policy, deliberately.
    CONSTRAINT fk_message_revisions_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_message_revisions_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_message_revisions_number UNIQUE (message_id, revision_number),
    CONSTRAINT ck_message_revisions_number CHECK (revision_number >= 1),
    CONSTRAINT ck_message_revisions_kind CHECK (
        revision_kind IN ('CORRECTION', 'WITHDRAWAL', 'MODERATION_PROJECTION', 'REDACTION')
    ),
    CONSTRAINT ck_message_revisions_actor_type CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER', 'ANONYMOUS')
    ),
    -- A correction that carries no replacement is a claim with no content behind it.
    CONSTRAINT ck_message_revisions_replacement CHECK (
        revision_kind <> 'CORRECTION'
            OR ((replacement_body_text IS NOT NULL OR replacement_body_reference IS NOT NULL)
                AND replacement_content_hash IS NOT NULL)
    ),
    CONSTRAINT ck_message_revisions_body_single CHECK (
        NOT (replacement_body_text IS NOT NULL AND replacement_body_reference IS NOT NULL)
    )
);

CREATE INDEX idx_message_revisions_message ON message_revisions (message_id, revision_number);
--rollback DROP TABLE message_revisions;

--changeset ninggiangboy:024-05-message-attachments
-- An upload, from the moment it is declared to the moment a message may cite it. The conversation is
-- recorded before the message exists because authorization happens at declaration time, when there is
-- nothing to attach to yet.
CREATE TABLE message_attachments (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id             UUID NOT NULL,
    message_id                  UUID,
    uploaded_by_participant_id  UUID,

    object_key                  VARCHAR(256) NOT NULL,
    storage_bucket              VARCHAR(64) NOT NULL,
    attachment_purpose          VARCHAR(24) NOT NULL,

    declared_media_type         VARCHAR(128) NOT NULL,
    verified_media_type         VARCHAR(128),
    declared_size_bytes         BIGINT NOT NULL,
    verified_size_bytes         BIGINT,
    checksum_sha256             CHAR(64) NOT NULL,

    scan_state                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    scan_completed_at           TIMESTAMPTZ,
    rejection_reason            VARCHAR(48),
    transformation_state        VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    original_object_key         VARCHAR(256),

    sensitivity_class           VARCHAR(16) NOT NULL DEFAULT 'CONFIDENTIAL',
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    hold_reference              VARCHAR(64),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_message_attachments_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id),
    CONSTRAINT fk_message_attachments_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_message_attachments_uploader FOREIGN KEY (uploaded_by_participant_id)
        REFERENCES conversation_participants (id),
    CONSTRAINT uk_message_attachments_object UNIQUE (storage_bucket, object_key),
    CONSTRAINT ck_message_attachments_purpose CHECK (
        attachment_purpose IN ('MEDIA', 'DOCUMENT', 'EVIDENCE', 'INSTRUCTION')
    ),
    CONSTRAINT ck_message_attachments_scan CHECK (
        scan_state IN ('PENDING', 'SCANNING', 'APPROVED', 'REJECTED', 'QUARANTINED')
    ),
    -- The rule the staged upload flow exists for: a message may only cite an approved object. An
    -- attachment still being scanned has no message to be shown under.
    CONSTRAINT ck_message_attachments_finalization CHECK (
        message_id IS NULL OR scan_state = 'APPROVED'
    ),
    CONSTRAINT ck_message_attachments_verdict CHECK (
        scan_state NOT IN ('APPROVED', 'REJECTED', 'QUARANTINED')
            OR (scan_completed_at IS NOT NULL
                AND verified_media_type IS NOT NULL AND verified_size_bytes IS NOT NULL)
    ),
    CONSTRAINT ck_message_attachments_rejection CHECK (
        scan_state NOT IN ('REJECTED', 'QUARANTINED') OR rejection_reason IS NOT NULL
    ),
    CONSTRAINT ck_message_attachments_transformation CHECK (
        transformation_state IN ('NOT_REQUIRED', 'PENDING', 'COMPLETED', 'FAILED')
    ),
    CONSTRAINT ck_message_attachments_size CHECK (
        declared_size_bytes > 0 AND (verified_size_bytes IS NULL OR verified_size_bytes > 0)
    ),
    CONSTRAINT ck_message_attachments_sensitivity CHECK (
        sensitivity_class IN ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')
    ),
    CONSTRAINT ck_message_attachments_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_message_attachments_hold CHECK (
        NOT legal_hold OR hold_reference IS NOT NULL
    ),
    CONSTRAINT ck_message_attachments_version CHECK (version >= 0)
);

CREATE INDEX idx_message_attachments_message ON message_attachments (message_id)
    WHERE message_id IS NOT NULL;
CREATE INDEX idx_message_attachments_pending ON message_attachments (created_at)
    WHERE scan_state IN ('PENDING', 'SCANNING');
--rollback DROP TABLE message_attachments;

--changeset ninggiangboy:024-06-message-translations
-- A derived view of what somebody said, never a claim that they said it in the target language. The
-- engine and its version are part of the key, so re-translating with a better model adds a row
-- instead of quietly changing what a reader was shown yesterday.
CREATE TABLE message_translations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id                  UUID NOT NULL,
    -- 0 means the original message; a higher number means the text of that revision.
    source_revision_number      SMALLINT NOT NULL DEFAULT 0,
    target_locale               VARCHAR(16) NOT NULL,

    translation_source          VARCHAR(16) NOT NULL,
    engine_key                  VARCHAR(48),
    engine_version              VARCHAR(32) NOT NULL,
    glossary_version            VARCHAR(32),

    translated_text             TEXT,
    translated_reference        VARCHAR(256),
    confidence                  NUMERIC(5, 4),

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_message_translations_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT uk_message_translations_variant
        UNIQUE (message_id, source_revision_number, target_locale, engine_version),
    CONSTRAINT ck_message_translations_revision CHECK (source_revision_number >= 0),
    CONSTRAINT ck_message_translations_source CHECK (
        translation_source IN ('HOST', 'MACHINE', 'PROFESSIONAL')
    ),
    CONSTRAINT ck_message_translations_engine CHECK (
        translation_source <> 'MACHINE' OR engine_key IS NOT NULL
    ),
    CONSTRAINT ck_message_translations_body CHECK (
        (translated_text IS NOT NULL) <> (translated_reference IS NOT NULL)
    ),
    CONSTRAINT ck_message_translations_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    )
);

CREATE INDEX idx_message_translations_message ON message_translations (message_id, target_locale);
--rollback DROP TABLE message_translations;

--changeset ninggiangboy:024-07-conversation-read-positions
-- How far a participant has read, as the highest contiguous sequence they have been shown. It is not
-- comprehension, presence, acceptance or consent, and changeset 024-21 stops it moving backwards so a
-- late-arriving device cannot reopen a thread somebody already read.
CREATE TABLE conversation_read_positions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id             UUID NOT NULL,
    participant_id              UUID NOT NULL,

    last_read_sequence          BIGINT NOT NULL DEFAULT 0,
    read_at                     TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_conversation_read_positions_conversation FOREIGN KEY (conversation_id)
        REFERENCES conversations (id),
    CONSTRAINT fk_conversation_read_positions_participant FOREIGN KEY (participant_id)
        REFERENCES conversation_participants (id),
    CONSTRAINT uk_conversation_read_positions_participant UNIQUE (participant_id),
    CONSTRAINT ck_conversation_read_positions_sequence CHECK (last_read_sequence >= 0),
    CONSTRAINT ck_conversation_read_positions_read CHECK (
        (last_read_sequence = 0) OR read_at IS NOT NULL
    ),
    CONSTRAINT ck_conversation_read_positions_version CHECK (version >= 0)
);

CREATE INDEX idx_conversation_read_positions_conversation
    ON conversation_read_positions (conversation_id);
--rollback DROP TABLE conversation_read_positions;

--changeset ninggiangboy:024-08-message-moderation-actions
-- What was done to a message and on whose authority. A model score is a proposal: the row records
-- both the classifier and the policy that turned it into an action, so an appeal can be answered with
-- the reason rather than with a shrug.
CREATE TABLE message_moderation_actions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id                  UUID NOT NULL,
    content_revision_number     SMALLINT NOT NULL DEFAULT 0,

    policy_reference            VARCHAR(64) NOT NULL,
    policy_version              INTEGER NOT NULL,
    model_key                   VARCHAR(48),
    model_version               VARCHAR(32),
    risk_score                  NUMERIC(5, 4),

    action                      VARCHAR(16) NOT NULL,
    decided_by                  VARCHAR(16) NOT NULL,
    reviewer_account_holder_id  UUID,
    reason_code                 VARCHAR(48) NOT NULL,
    explanation_class           VARCHAR(32) NOT NULL,
    evidence_reference          VARCHAR(128),

    appeal_state                VARCHAR(16) NOT NULL DEFAULT 'NOT_APPEALABLE',
    appealed_at                 TIMESTAMPTZ,
    appeal_resolved_at          TIMESTAMPTZ,

    decided_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_message_moderation_actions_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_message_moderation_actions_reviewer FOREIGN KEY (reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_message_moderation_actions_action CHECK (
        action IN ('ALLOW', 'WARN', 'MASK', 'QUARANTINE', 'DELAY', 'HUMAN_REVIEW',
                   'RESTRICT', 'SAFETY_ESCALATE')
    ),
    CONSTRAINT ck_message_moderation_actions_decider CHECK (
        decided_by IN ('POLICY', 'MODEL', 'REVIEWER', 'SYSTEM')
    ),
    -- A classifier may propose; it may not be the whole authority for an outcome that restricts a
    -- person or escalates a safety case.
    CONSTRAINT ck_message_moderation_actions_authority CHECK (
        action NOT IN ('RESTRICT', 'SAFETY_ESCALATE') OR decided_by <> 'MODEL'
    ),
    CONSTRAINT ck_message_moderation_actions_reviewer_ref CHECK (
        (decided_by = 'REVIEWER') = (reviewer_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_message_moderation_actions_model CHECK (
        (model_key IS NULL) = (model_version IS NULL)
    ),
    CONSTRAINT ck_message_moderation_actions_score CHECK (
        risk_score IS NULL OR (risk_score >= 0 AND risk_score <= 1)
    ),
    CONSTRAINT ck_message_moderation_actions_appeal CHECK (
        appeal_state IN ('NOT_APPEALABLE', 'AVAILABLE', 'REQUESTED', 'UPHELD', 'OVERTURNED')
    ),
    CONSTRAINT ck_message_moderation_actions_appeal_times CHECK (
        (appeal_state IN ('REQUESTED', 'UPHELD', 'OVERTURNED')) = (appealed_at IS NOT NULL)
            AND ((appeal_state IN ('UPHELD', 'OVERTURNED')) = (appeal_resolved_at IS NOT NULL))
    ),
    CONSTRAINT ck_message_moderation_actions_version CHECK (version >= 0)
);

CREATE INDEX idx_message_moderation_actions_message
    ON message_moderation_actions (message_id, decided_at DESC);
CREATE INDEX idx_message_moderation_actions_appeals ON message_moderation_actions (appealed_at)
    WHERE appeal_state = 'REQUESTED';
--rollback DROP TABLE message_moderation_actions;

--changeset ninggiangboy:024-09-notification-policies
-- The rule that turns a committed domain fact into an intent to tell somebody. It is versioned and
-- immutable once approved, because the answer to "why was I sent this" has to be a document that
-- still says what it said at the time.
CREATE TABLE notification_policies (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key                  VARCHAR(64) NOT NULL,
    policy_version              INTEGER NOT NULL,

    purpose_code                VARCHAR(48) NOT NULL,
    category_code               VARCHAR(48) NOT NULL,
    classification              VARCHAR(24) NOT NULL,
    source_event_type           VARCHAR(64) NOT NULL,
    market_code                 VARCHAR(2),

    -- The rule bodies. They are snapshots of a decision rather than filterable business data, which
    -- is what jsonb is for here; everything a query needs to select a policy is a column above.
    recipient_rule              JSONB NOT NULL,
    channel_rule                JSONB NOT NULL,
    timing_rule                 JSONB NOT NULL,
    fallback_rule               JSONB,
    quiet_hours_rule            JSONB,
    variable_contract           JSONB NOT NULL,

    template_family             VARCHAR(64) NOT NULL,
    deduplication_key_template  VARCHAR(128) NOT NULL,
    expiry_seconds              INTEGER,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,
    approved_by_account_id      UUID,
    approved_at                 TIMESTAMPTZ,
    approval_evidence_reference VARCHAR(128),
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_policies_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_notification_policies_approver FOREIGN KEY (approved_by_account_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_notification_policies_version UNIQUE (policy_key, policy_version),
    CONSTRAINT ck_notification_policies_version_number CHECK (policy_version >= 1),
    CONSTRAINT ck_notification_policies_classification CHECK (
        classification IN ('TRANSACTIONAL_MANDATORY', 'TRANSACTIONAL_OPTIONAL', 'MARKETING')
    ),
    CONSTRAINT ck_notification_policies_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED')
    ),
    -- Approval is what freezes the row, so it cannot be claimed without who and when.
    CONSTRAINT ck_notification_policies_approval CHECK (
        status NOT IN ('APPROVED', 'ACTIVE', 'RETIRED')
            OR (approved_by_account_id IS NOT NULL AND approved_at IS NOT NULL
                AND effective_from IS NOT NULL)
    ),
    CONSTRAINT ck_notification_policies_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_notification_policies_expiry CHECK (expiry_seconds IS NULL OR expiry_seconds > 0),
    CONSTRAINT ck_notification_policies_row_version CHECK (version >= 0)
);

-- One live policy per purpose and market at a time. The predicate is the status rather than the
-- interval because a partial index may not read the clock.
CREATE UNIQUE INDEX uk_notification_policies_active
    ON notification_policies (purpose_code, market_code, source_event_type)
    NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';
CREATE INDEX idx_notification_policies_event ON notification_policies (source_event_type, status);
--rollback DROP TABLE notification_policies;

--changeset ninggiangboy:024-10-notification-templates
-- The words themselves, per channel and locale, immutable once approved. The typed variable schema is
-- the contract rendering is checked against: a missing required variable fails into an operations
-- queue and is never invented.
CREATE TABLE notification_templates (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_family             VARCHAR(64) NOT NULL,
    template_version            INTEGER NOT NULL,
    channel                     VARCHAR(16) NOT NULL,
    locale                      VARCHAR(16) NOT NULL,
    market_code                 VARCHAR(2),

    purpose_code                VARCHAR(48) NOT NULL,
    classification              VARCHAR(24) NOT NULL,
    subject_text                TEXT,
    body_text                   TEXT NOT NULL,
    preview_text                TEXT,
    variable_schema             JSONB NOT NULL,
    action_schema               JSONB,
    escaping_rule               VARCHAR(16) NOT NULL,

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,
    approved_by_account_id      UUID,
    approved_at                 TIMESTAMPTZ,
    approval_evidence_reference VARCHAR(128),
    rollback_predecessor_id     UUID,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_templates_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_notification_templates_approver FOREIGN KEY (approved_by_account_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_notification_templates_predecessor FOREIGN KEY (rollback_predecessor_id)
        REFERENCES notification_templates (id),
    CONSTRAINT uk_notification_templates_version
        UNIQUE (template_family, template_version, channel, locale),
    CONSTRAINT ck_notification_templates_version_number CHECK (template_version >= 1),
    CONSTRAINT ck_notification_templates_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT ck_notification_templates_classification CHECK (
        classification IN ('TRANSACTIONAL_MANDATORY', 'TRANSACTIONAL_OPTIONAL', 'MARKETING')
    ),
    CONSTRAINT ck_notification_templates_escaping CHECK (
        escaping_rule IN ('PLAIN_TEXT', 'HTML', 'MARKDOWN')
    ),
    CONSTRAINT ck_notification_templates_status CHECK (
        status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED')
    ),
    -- An email with no subject is not a rendering failure at send time; it is a template nobody
    -- finished.
    CONSTRAINT ck_notification_templates_subject CHECK (
        channel <> 'EMAIL' OR subject_text IS NOT NULL
    ),
    CONSTRAINT ck_notification_templates_approval CHECK (
        status NOT IN ('APPROVED', 'ACTIVE', 'RETIRED')
            OR (approved_by_account_id IS NOT NULL AND approved_at IS NOT NULL
                AND effective_from IS NOT NULL)
    ),
    CONSTRAINT ck_notification_templates_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_notification_templates_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_notification_templates_active
    ON notification_templates (template_family, channel, locale)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_notification_templates_family ON notification_templates (template_family, status);
--rollback DROP TABLE notification_templates;

--changeset ninggiangboy:024-11-notification-preferences
-- What a person chose about optional notices, per category and channel, with their quiet hours in a
-- named zone rather than an offset. Nothing here can suppress a mandatory transactional notice: that
-- decision belongs to the policy's classification, not to this row.
CREATE TABLE notification_preferences (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    category_code               VARCHAR(48) NOT NULL,
    purpose_code                VARCHAR(48),
    channel                     VARCHAR(16) NOT NULL,

    enabled                     BOOLEAN NOT NULL,
    quiet_hours_start           TIME,
    quiet_hours_end             TIME,
    quiet_hours_zone            VARCHAR(64),

    preference_source           VARCHAR(24) NOT NULL,
    source_reference            VARCHAR(128),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_preferences_actor FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_notification_preferences_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT ck_notification_preferences_source CHECK (
        preference_source IN ('USER', 'POLICY_DEFAULT', 'SUPPORT', 'IMPORT')
    ),
    -- Quiet hours are three facts or none. A window without a zone is a window nobody can evaluate.
    CONSTRAINT ck_notification_preferences_quiet_hours CHECK (
        (quiet_hours_start IS NULL AND quiet_hours_end IS NULL AND quiet_hours_zone IS NULL)
            OR (quiet_hours_start IS NOT NULL AND quiet_hours_end IS NOT NULL
                AND quiet_hours_zone IS NOT NULL)
    ),
    CONSTRAINT ck_notification_preferences_version CHECK (version >= 0)
);

-- A category default and a purpose-specific override are different rows; NULLS NOT DISTINCT is what
-- stops two competing category defaults existing at once.
CREATE UNIQUE INDEX uk_notification_preferences_scope
    ON notification_preferences (account_holder_id, category_code, purpose_code, channel)
    NULLS NOT DISTINCT;
--rollback DROP TABLE notification_preferences;

--changeset ninggiangboy:024-12-communication-consents
-- The evidence that a person agreed, or withdrew. Withdrawal is a timestamp on the same row rather
-- than a delete, because "prove you had permission when you sent it" is the question this table is
-- here to answer.
CREATE TABLE communication_consents (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_holder_id           UUID NOT NULL,
    category_code               VARCHAR(48) NOT NULL,
    channel                     VARCHAR(16) NOT NULL,
    market_code                 VARCHAR(2),

    legal_basis                 VARCHAR(24) NOT NULL,
    notice_reference            VARCHAR(128) NOT NULL,
    notice_version              INTEGER NOT NULL,
    grant_source                VARCHAR(24) NOT NULL,
    grant_evidence_reference    VARCHAR(128),
    granted_at                  TIMESTAMPTZ NOT NULL,

    withdrawn_at                TIMESTAMPTZ,
    withdrawal_source           VARCHAR(24),
    withdrawal_evidence_ref     VARCHAR(128),

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_communication_consents_actor FOREIGN KEY (account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_communication_consents_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_communication_consents_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT ck_communication_consents_basis CHECK (
        legal_basis IN ('CONSENT', 'CONTRACT', 'LEGITIMATE_INTEREST', 'LEGAL_OBLIGATION')
    ),
    CONSTRAINT ck_communication_consents_grant_source CHECK (
        grant_source IN ('SIGNUP', 'PREFERENCE_CENTRE', 'SUPPORT', 'IMPORT', 'CHECKOUT')
    ),
    CONSTRAINT ck_communication_consents_withdrawal CHECK (
        (withdrawn_at IS NULL AND withdrawal_source IS NULL)
            OR (withdrawn_at IS NOT NULL AND withdrawal_source IS NOT NULL
                AND withdrawn_at >= granted_at)
    ),
    CONSTRAINT ck_communication_consents_withdrawal_source CHECK (
        withdrawal_source IS NULL
            OR withdrawal_source IN ('PREFERENCE_CENTRE', 'SUPPORT', 'UNSUBSCRIBE_LINK',
                                     'PROVIDER_COMPLAINT', 'ACCOUNT_CLOSURE')
    ),
    CONSTRAINT ck_communication_consents_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_communication_consents_live
    ON communication_consents (account_holder_id, category_code, channel, market_code)
    NULLS NOT DISTINCT
    WHERE withdrawn_at IS NULL;
CREATE INDEX idx_communication_consents_actor
    ON communication_consents (account_holder_id, category_code);
--rollback DROP TABLE communication_consents;

--changeset ninggiangboy:024-13-notification-intents
-- The decision to tell one person one thing once. Everything below it -- renders, attempts, provider
-- observations -- may be retried freely, because the intent is what carries the identity.
CREATE TABLE notification_intents (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_event_id             UUID NOT NULL,
    source_event_type           VARCHAR(64) NOT NULL,
    source_aggregate_version    INTEGER,

    recipient_account_holder_id UUID NOT NULL,
    -- The destination stays in the identity boundary. What is kept here is which contact row was
    -- resolved and at which version, so a later address change is visible without copying the address.
    contact_channel_id          UUID,
    contact_channel_version     BIGINT,

    purpose_code                VARCHAR(48) NOT NULL,
    classification              VARCHAR(24) NOT NULL,
    notification_policy_id      UUID NOT NULL,
    template_family             VARCHAR(64) NOT NULL,
    locale                      VARCHAR(16) NOT NULL,

    scheduled_for               TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    -- The domain's own deadline, kept so copy never implies that delaying delivery moved it.
    domain_deadline_at          TIMESTAMPTZ,

    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    suppression_reason          VARCHAR(48),
    superseded_by_intent_id     UUID,
    failure_reason              VARCHAR(48),

    -- What the intent was built from. Reprocessing the same event must produce the same hash, and a
    -- changed fact must produce a different one; changeset 024-22 stops either being rewritten later.
    input_hash                  CHAR(64) NOT NULL,
    correlation_id              UUID,

    claimed_at                  TIMESTAMPTZ,
    claimed_by                  VARCHAR(64),
    lease_expires_at            TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_intents_recipient FOREIGN KEY (recipient_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_notification_intents_contact FOREIGN KEY (contact_channel_id)
        REFERENCES contact_channels (id),
    CONSTRAINT fk_notification_intents_policy FOREIGN KEY (notification_policy_id)
        REFERENCES notification_policies (id),
    CONSTRAINT fk_notification_intents_supersession FOREIGN KEY (superseded_by_intent_id)
        REFERENCES notification_intents (id),
    -- The uniqueness the whole domain rests on: one event, one recipient, one purpose, one policy
    -- version. Choosing a different template or provider route does not escape it.
    CONSTRAINT uk_notification_intents_logical
        UNIQUE (source_event_id, recipient_account_holder_id, purpose_code, notification_policy_id),
    CONSTRAINT ck_notification_intents_classification CHECK (
        classification IN ('TRANSACTIONAL_MANDATORY', 'TRANSACTIONAL_OPTIONAL', 'MARKETING')
    ),
    CONSTRAINT ck_notification_intents_state CHECK (
        state IN ('PENDING', 'SCHEDULED', 'SUPPRESSED', 'READY', 'DISPATCHING',
                  'COMPLETED', 'EXPIRED', 'FAILED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_notification_intents_suppression CHECK (
        (state = 'SUPPRESSED') = (suppression_reason IS NOT NULL)
    ),
    CONSTRAINT ck_notification_intents_supersession CHECK (
        (state = 'SUPERSEDED') = (superseded_by_intent_id IS NOT NULL)
    ),
    CONSTRAINT ck_notification_intents_failure CHECK (
        (state = 'FAILED') = (failure_reason IS NOT NULL)
    ),
    CONSTRAINT ck_notification_intents_completion CHECK (
        state NOT IN ('COMPLETED', 'EXPIRED', 'FAILED', 'SUPPRESSED') OR completed_at IS NOT NULL
    ),
    CONSTRAINT ck_notification_intents_expiry CHECK (
        expires_at IS NULL OR expires_at > scheduled_for
    ),
    -- A claim is a lease: it has an owner and an end, or it is not a claim.
    CONSTRAINT ck_notification_intents_lease CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL AND lease_expires_at IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_notification_intents_version CHECK (version >= 0)
);

CREATE INDEX idx_notification_intents_due ON notification_intents (scheduled_for)
    WHERE state IN ('PENDING', 'SCHEDULED', 'READY');
CREATE INDEX idx_notification_intents_recipient
    ON notification_intents (recipient_account_holder_id, created_at DESC);
CREATE INDEX idx_notification_intents_leases ON notification_intents (lease_expires_at)
    WHERE state = 'DISPATCHING';
--rollback DROP TABLE notification_intents;

--changeset ninggiangboy:024-14-notification-renders
-- What the words actually came out as, and from which inputs. Kept as hashes and a reference rather
-- than as the rendered body, so an audit can prove a render without the audit trail itself becoming
-- a copy of everybody's mail.
CREATE TABLE notification_renders (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_intent_id      UUID NOT NULL,
    channel                     VARCHAR(16) NOT NULL,
    locale                      VARCHAR(16) NOT NULL,
    notification_template_id    UUID,

    variable_source_hash        CHAR(64) NOT NULL,
    content_hash                CHAR(64),
    artifact_reference          VARCHAR(256),

    render_state                VARCHAR(32) NOT NULL,
    error_code                  VARCHAR(48),
    missing_variables           VARCHAR(64)[] NOT NULL DEFAULT '{}',

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notification_renders_intent FOREIGN KEY (notification_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_notification_renders_template FOREIGN KEY (notification_template_id)
        REFERENCES notification_templates (id),
    CONSTRAINT ck_notification_renders_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT ck_notification_renders_state CHECK (
        render_state IN ('RENDERED', 'FAILED', 'BLOCKED_MISSING_VARIABLE')
    ),
    -- A successful render names the template it used and hashes what it produced; a blocked one names
    -- what was missing. Neither is allowed to be silent.
    CONSTRAINT ck_notification_renders_success CHECK (
        render_state <> 'RENDERED'
            OR (notification_template_id IS NOT NULL AND content_hash IS NOT NULL)
    ),
    CONSTRAINT ck_notification_renders_blocked CHECK (
        render_state <> 'BLOCKED_MISSING_VARIABLE' OR cardinality(missing_variables) > 0
    ),
    CONSTRAINT ck_notification_renders_failure CHECK (
        render_state <> 'FAILED' OR error_code IS NOT NULL
    )
);

CREATE UNIQUE INDEX uk_notification_renders_success
    ON notification_renders (notification_intent_id, channel, locale)
    WHERE render_state = 'RENDERED';
CREATE INDEX idx_notification_renders_intent ON notification_renders (notification_intent_id);
CREATE INDEX idx_notification_renders_blocked ON notification_renders (created_at)
    WHERE render_state <> 'RENDERED';
--rollback DROP TABLE notification_renders;

--changeset ninggiangboy:024-15-delivery-attempts
-- One crossing to one provider. The state vocabulary is the document's own, and the fence is the same
-- one migration 021 uses for payments: nothing may claim a provider outcome without a submission
-- instant, and a failure must say which kind it was, so an unanswered send can only be UNKNOWN.
CREATE TABLE delivery_attempts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_intent_id      UUID NOT NULL,
    notification_render_id      UUID,
    channel                     VARCHAR(16) NOT NULL,
    route_key                   VARCHAR(48) NOT NULL,
    attempt_number              SMALLINT NOT NULL,

    provider_account_id         UUID,
    provider_idempotency_key    VARCHAR(128),
    provider_reference          VARCHAR(128),

    state                       VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    failure_category            VARCHAR(32),
    failure_detail              VARCHAR(256),

    claimed_at                  TIMESTAMPTZ,
    claimed_by                  VARCHAR(64),
    lease_expires_at            TIMESTAMPTZ,
    submitted_at                TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    next_retry_at               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_delivery_attempts_intent FOREIGN KEY (notification_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_delivery_attempts_render FOREIGN KEY (notification_render_id)
        REFERENCES notification_renders (id),
    CONSTRAINT fk_delivery_attempts_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_delivery_attempts_number
        UNIQUE (notification_intent_id, channel, attempt_number),
    CONSTRAINT ck_delivery_attempts_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_delivery_attempts_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT ck_delivery_attempts_state CHECK (
        state IN ('PLANNED', 'CLAIMED', 'SUBMITTING', 'ACCEPTED', 'DELIVERED',
                  'BOUNCED', 'COMPLAINED', 'FAILED', 'UNKNOWN')
    ),
    -- The fence. ACCEPTED means the provider took it, which cannot be true of something never sent.
    CONSTRAINT ck_delivery_attempts_submission CHECK (
        (state IN ('PLANNED', 'CLAIMED')) = (submitted_at IS NULL)
    ),
    CONSTRAINT ck_delivery_attempts_failure CHECK (
        (state = 'FAILED') = (failure_category IS NOT NULL)
    ),
    CONSTRAINT ck_delivery_attempts_failure_category CHECK (
        failure_category IS NULL
            OR failure_category IN ('PROVIDER_REJECTED', 'INVALID_DESTINATION', 'SUPPRESSED',
                                    'RATE_LIMITED', 'AUTHENTICATION', 'TIMEOUT', 'INTERNAL')
    ),
    -- Terminal for this attempt. A complaint may still arrive afterwards and is an observation,
    -- never a rewrite of when the attempt finished.
    CONSTRAINT ck_delivery_attempts_completion CHECK (
        state NOT IN ('DELIVERED', 'BOUNCED', 'COMPLAINED', 'FAILED') OR completed_at IS NOT NULL
    ),
    CONSTRAINT ck_delivery_attempts_lease CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL AND lease_expires_at IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    -- Retrying is for outcomes that might change. A delivered or complained-about attempt is done.
    CONSTRAINT ck_delivery_attempts_retry CHECK (
        next_retry_at IS NULL OR state IN ('FAILED', 'UNKNOWN', 'PLANNED')
    ),
    CONSTRAINT ck_delivery_attempts_version CHECK (version >= 0)
);

-- The provider's own handle for a send, unique within the account that issued it. This is what a
-- reconciliation query matches on when an outcome is unknown.
CREATE UNIQUE INDEX uk_delivery_attempts_provider_reference
    ON delivery_attempts (provider_account_id, provider_reference)
    WHERE provider_account_id IS NOT NULL AND provider_reference IS NOT NULL;
CREATE INDEX idx_delivery_attempts_intent ON delivery_attempts (notification_intent_id);
CREATE INDEX idx_delivery_attempts_retry ON delivery_attempts (next_retry_at)
    WHERE state IN ('FAILED', 'UNKNOWN');
CREATE INDEX idx_delivery_attempts_leases ON delivery_attempts (lease_expires_at)
    WHERE state IN ('CLAIMED', 'SUBMITTING');
--rollback DROP TABLE delivery_attempts;

--changeset ninggiangboy:024-16-delivery-observations
-- Provider evidence, append-only. A late bounce after a delivered event does not erase the delivery;
-- it is another row, and the attempt's state is reduced from all of them by precedence.
CREATE TABLE delivery_observations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_attempt_id         UUID NOT NULL,
    provider_account_id         UUID NOT NULL,
    provider_event_id           VARCHAR(128) NOT NULL,
    event_type                  VARCHAR(48) NOT NULL,

    observation_source          VARCHAR(24) NOT NULL,
    occurred_at                 TIMESTAMPTZ,
    received_at                 TIMESTAMPTZ NOT NULL,
    signature_verified          BOOLEAN NOT NULL DEFAULT false,
    raw_artifact_reference      VARCHAR(256),
    payload_hash                CHAR(64),
    reduced_state               VARCHAR(16),

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_delivery_observations_attempt FOREIGN KEY (delivery_attempt_id)
        REFERENCES delivery_attempts (id),
    CONSTRAINT fk_delivery_observations_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    -- The provider's event identity, deduplicated within its own account. Replayed webhooks are
    -- ordinary and must be free.
    CONSTRAINT uk_delivery_observations_event UNIQUE (provider_account_id, provider_event_id),
    CONSTRAINT ck_delivery_observations_source CHECK (
        observation_source IN ('API_RESPONSE', 'WEBHOOK', 'QUERY', 'RECONCILIATION_IMPORT')
    ),
    -- A webhook nobody authenticated is evidence of nothing, so it must at least record that.
    CONSTRAINT ck_delivery_observations_signature CHECK (
        observation_source <> 'WEBHOOK' OR raw_artifact_reference IS NOT NULL
    ),
    CONSTRAINT ck_delivery_observations_reduced_state CHECK (
        reduced_state IS NULL
            OR reduced_state IN ('ACCEPTED', 'DELIVERED', 'BOUNCED', 'COMPLAINED',
                                 'FAILED', 'UNKNOWN')
    )
);

CREATE INDEX idx_delivery_observations_attempt
    ON delivery_observations (delivery_attempt_id, received_at);
--rollback DROP TABLE delivery_observations;

--changeset ninggiangboy:024-17-contact-delivery-health
-- Whether a destination still accepts mail. It is keyed to the identity domain's contact row and the
-- version of it that was observed, so correcting a mistyped address starts a fresh history instead of
-- inheriting the old one's suppression.
CREATE TABLE contact_delivery_health (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    contact_channel_id          UUID NOT NULL,
    contact_channel_version     BIGINT NOT NULL,
    channel                     VARCHAR(16) NOT NULL,

    status                      VARCHAR(16) NOT NULL DEFAULT 'HEALTHY',
    hard_bounce_count           INTEGER NOT NULL DEFAULT 0,
    soft_bounce_count           INTEGER NOT NULL DEFAULT 0,
    complaint_count             INTEGER NOT NULL DEFAULT 0,
    last_success_at             TIMESTAMPTZ,
    last_bounce_at              TIMESTAMPTZ,
    last_complaint_at           TIMESTAMPTZ,

    suppressed_at               TIMESTAMPTZ,
    suppression_reason          VARCHAR(48),
    suppression_source          VARCHAR(24),
    review_state                VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_contact_delivery_health_contact FOREIGN KEY (contact_channel_id)
        REFERENCES contact_channels (id),
    CONSTRAINT uk_contact_delivery_health_contact
        UNIQUE (contact_channel_id, contact_channel_version, channel),
    CONSTRAINT ck_contact_delivery_health_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')
    ),
    CONSTRAINT ck_contact_delivery_health_status CHECK (
        status IN ('HEALTHY', 'DEGRADED', 'SUPPRESSED', 'UNROUTABLE')
    ),
    CONSTRAINT ck_contact_delivery_health_counts CHECK (
        hard_bounce_count >= 0 AND soft_bounce_count >= 0 AND complaint_count >= 0
    ),
    -- Suppression is a decision somebody or something made, and it has to be attributable: it is what
    -- will stop a future send.
    CONSTRAINT ck_contact_delivery_health_suppression CHECK (
        (status IN ('SUPPRESSED', 'UNROUTABLE'))
            = (suppressed_at IS NOT NULL AND suppression_reason IS NOT NULL
               AND suppression_source IS NOT NULL)
    ),
    CONSTRAINT ck_contact_delivery_health_suppression_source CHECK (
        suppression_source IS NULL
            OR suppression_source IN ('PROVIDER_BOUNCE', 'PROVIDER_COMPLAINT', 'POLICY',
                                      'SUPPORT', 'LEGAL')
    ),
    CONSTRAINT ck_contact_delivery_health_review CHECK (
        review_state IN ('NOT_REQUIRED', 'PENDING', 'CLEARED')
    ),
    CONSTRAINT ck_contact_delivery_health_version CHECK (version >= 0)
);

CREATE INDEX idx_contact_delivery_health_review ON contact_delivery_health (updated_at)
    WHERE review_state = 'PENDING';
--rollback DROP TABLE contact_delivery_health;

--changeset ninggiangboy:024-18-scheduled-communications
-- A reminder that has not happened yet. It names the fact it was derived from and the version of that
-- fact, so a modified or cancelled booking supersedes the job rather than leaving a worker to send
-- arrival instructions for a stay that moved.
CREATE TABLE scheduled_communications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_key                VARCHAR(64) NOT NULL,
    supersession_key            VARCHAR(128) NOT NULL,

    anchor_domain               VARCHAR(24) NOT NULL,
    anchor_aggregate_id         UUID NOT NULL,
    anchor_version              INTEGER NOT NULL,
    booking_id                  UUID,
    booking_revision_id         UUID,

    recipient_account_holder_id UUID NOT NULL,
    purpose_code                VARCHAR(48) NOT NULL,
    notification_policy_id      UUID NOT NULL,

    -- The rule is local wall time in a named zone, resolved once into an instant. Both are kept: the
    -- instant is what the worker polls, the wall time is what the rule meant.
    local_rule                  VARCHAR(64) NOT NULL,
    local_time                  TIME NOT NULL,
    time_zone                   VARCHAR(64) NOT NULL,
    resolved_run_at             TIMESTAMPTZ NOT NULL,
    usefulness_expires_at       TIMESTAMPTZ,

    state                       VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    claimed_at                  TIMESTAMPTZ,
    claimed_by                  VARCHAR(64),
    lease_expires_at            TIMESTAMPTZ,
    created_intent_id           UUID,
    discard_reason              VARCHAR(48),
    superseded_by_id            UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_scheduled_communications_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_scheduled_communications_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_scheduled_communications_recipient FOREIGN KEY (recipient_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_scheduled_communications_policy FOREIGN KEY (notification_policy_id)
        REFERENCES notification_policies (id),
    CONSTRAINT fk_scheduled_communications_intent FOREIGN KEY (created_intent_id)
        REFERENCES notification_intents (id),
    CONSTRAINT fk_scheduled_communications_supersession FOREIGN KEY (superseded_by_id)
        REFERENCES scheduled_communications (id),
    CONSTRAINT ck_scheduled_communications_anchor CHECK (
        anchor_domain IN ('BOOKING', 'PAYMENT', 'CANCELLATION', 'REVIEW', 'INCIDENT', 'PAYOUT')
    ),
    CONSTRAINT ck_scheduled_communications_anchor_version CHECK (anchor_version >= 1),
    CONSTRAINT ck_scheduled_communications_state CHECK (
        state IN ('SCHEDULED', 'CLAIMED', 'FIRED', 'DISCARDED', 'SUPERSEDED', 'EXPIRED')
    ),
    -- A job that says it fired must be able to show what it created.
    CONSTRAINT ck_scheduled_communications_fired CHECK (
        (state = 'FIRED') = (created_intent_id IS NOT NULL)
    ),
    CONSTRAINT ck_scheduled_communications_discard CHECK (
        (state IN ('DISCARDED', 'EXPIRED')) = (discard_reason IS NOT NULL)
    ),
    CONSTRAINT ck_scheduled_communications_supersession CHECK (
        (state = 'SUPERSEDED') = (superseded_by_id IS NOT NULL)
    ),
    CONSTRAINT ck_scheduled_communications_lease CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL AND lease_expires_at IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_scheduled_communications_version CHECK (version >= 0)
);

-- At most one live job per supersession key. A booking modification writes the replacement and marks
-- the old one superseded in the same transaction, so two workers can never both have something due.
CREATE UNIQUE INDEX uk_scheduled_communications_live
    ON scheduled_communications (supersession_key)
    WHERE state IN ('SCHEDULED', 'CLAIMED');
CREATE INDEX idx_scheduled_communications_due ON scheduled_communications (resolved_run_at)
    WHERE state = 'SCHEDULED';
CREATE INDEX idx_scheduled_communications_anchor
    ON scheduled_communications (anchor_domain, anchor_aggregate_id);
CREATE INDEX idx_scheduled_communications_leases ON scheduled_communications (lease_expires_at)
    WHERE state = 'CLAIMED';
--rollback DROP TABLE scheduled_communications;

--changeset ninggiangboy:024-19-conversation-sequence-integrity splitStatements:false
-- Message order is the thing every unread count, cursor and dispute export depends on, and it is
-- allocated by the conversation row. Two defences make the allocation honest.
--
-- The counter may only go forward. Lowering it would hand out a number already taken and, worse,
-- silently reorder a thread somebody has already read.
CREATE FUNCTION conversations_guard_sequence() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.next_sequence < OLD.next_sequence THEN
        RAISE EXCEPTION
            'conversation % cannot move its sequence allocator from % back to %',
            OLD.id, OLD.next_sequence, NEW.next_sequence USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_conversations_sequence_forward
    BEFORE UPDATE ON conversations
    FOR EACH ROW EXECUTE FUNCTION conversations_guard_sequence();

-- And a message may only take a number the conversation actually issued. The unique key already stops
-- two messages sharing a sequence; this stops a writer skipping the allocator altogether and claiming
-- a number far ahead of it, which would leave a permanent hole that later sends could never fill.
--
-- The same trigger is where participant authority is checked, because this is the last place before
-- the row exists: a sender must be an active participant of this conversation, writing as themselves.
-- The service checks it under a lock as well; this is the defence that survives a service defect.
CREATE FUNCTION messages_validate_write() RETURNS TRIGGER AS $$
DECLARE
    allocator BIGINT;
    conversation_status VARCHAR(16);
    participant_conversation UUID;
    participant_account UUID;
    participant_left TIMESTAMPTZ;
BEGIN
    SELECT next_sequence, status INTO allocator, conversation_status
      FROM conversations WHERE id = NEW.conversation_id;

    IF NEW.sequence_number >= allocator THEN
        RAISE EXCEPTION
            'message sequence % was never issued by conversation % (allocator is at %)',
            NEW.sequence_number, NEW.conversation_id, allocator
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF conversation_status IN ('CLOSED', 'ARCHIVED') THEN
        RAISE EXCEPTION
            'conversation % is %; it cannot receive further messages',
            NEW.conversation_id, conversation_status USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.sender_participant_id IS NOT NULL THEN
        SELECT conversation_id, account_holder_id, left_at
          INTO participant_conversation, participant_account, participant_left
          FROM conversation_participants WHERE id = NEW.sender_participant_id;

        -- NOT FOUND as well as a mismatch: the foreign key would refuse a participant that does not
        -- exist, but it fires after this trigger, and a NULL comparison here would fall through to
        -- the sender check and blame the wrong column.
        IF NOT FOUND OR participant_conversation <> NEW.conversation_id THEN
            RAISE EXCEPTION
                'participant % does not belong to conversation %',
                NEW.sender_participant_id, NEW.conversation_id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF participant_left IS NOT NULL THEN
            RAISE EXCEPTION
                'participant % left conversation % at % and cannot send',
                NEW.sender_participant_id, NEW.conversation_id, participant_left
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF participant_account IS DISTINCT FROM NEW.sender_account_holder_id THEN
            RAISE EXCEPTION
                'message claims sender % but participant % belongs to %',
                NEW.sender_account_holder_id, NEW.sender_participant_id, participant_account
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_messages_validate_write
    BEFORE INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION messages_validate_write();
--rollback DROP TRIGGER trg_messages_validate_write ON messages;
--rollback DROP FUNCTION messages_validate_write();
--rollback DROP TRIGGER trg_conversations_sequence_forward ON conversations;
--rollback DROP FUNCTION conversations_guard_sequence();

--changeset ninggiangboy:024-20-message-immutability splitStatements:false
-- A message is evidence from the moment it exists. Editing one would let a participant rewrite what
-- they said after the other party relied on it, which is exactly the move a dispute turns on.
--
-- What may still change is presentation and derived counters: visibility (withdrawal, masking,
-- quarantine), the pointer to the latest revision, and the audit columns. The words, the sender, the
-- order and the hash never move.
CREATE FUNCTION messages_freeze_content() RETURNS TRIGGER AS $$
DECLARE
    candidate messages%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- Retention and privacy deletion are legitimate; deleting held evidence is not.
        IF OLD.legal_hold THEN
            RAISE EXCEPTION
                'message % in conversation % is under legal hold and cannot be deleted',
                OLD.id, OLD.conversation_id USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;

    candidate := NEW;
    candidate.visibility_state := OLD.visibility_state;
    candidate.withdrawn_at := OLD.withdrawn_at;
    candidate.latest_revision_number := OLD.latest_revision_number;
    candidate.sensitivity_class := OLD.sensitivity_class;
    candidate.retention_class := OLD.retention_class;
    candidate.legal_hold := OLD.legal_hold;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'message % is immutable; correct it with a revision instead of editing what was sent',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    -- The revision pointer counts up with the revisions themselves.
    IF NEW.latest_revision_number < OLD.latest_revision_number THEN
        RAISE EXCEPTION
            'message % cannot forget revision %', OLD.id, OLD.latest_revision_number
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_messages_frozen
    BEFORE UPDATE OR DELETE ON messages
    FOR EACH ROW EXECUTE FUNCTION messages_freeze_content();

-- Revisions, translations and moderation records are the derived layer. Each is written once and
-- corrected by adding another, so that what a reader was shown last week is still reconstructable.
CREATE FUNCTION messaging_evidence_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; add a further record instead of altering the evidence', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_message_revisions_append_only
    BEFORE UPDATE OR DELETE ON message_revisions
    FOR EACH ROW EXECUTE FUNCTION messaging_evidence_reject_mutation();

CREATE TRIGGER trg_message_translations_append_only
    BEFORE UPDATE OR DELETE ON message_translations
    FOR EACH ROW EXECUTE FUNCTION messaging_evidence_reject_mutation();
--rollback DROP TRIGGER trg_message_translations_append_only ON message_translations;
--rollback DROP TRIGGER trg_message_revisions_append_only ON message_revisions;
--rollback DROP FUNCTION messaging_evidence_reject_mutation();
--rollback DROP TRIGGER trg_messages_frozen ON messages;
--rollback DROP FUNCTION messages_freeze_content();

--changeset ninggiangboy:024-21-read-position-monotonic splitStatements:false
-- Read position is a high-water mark, not a cursor. A phone that syncs late must not be able to lower
-- it and make a thread somebody has read look unread again -- and, worse, make an unread badge the
-- basis of a claim that a guest was never told something.
CREATE FUNCTION conversation_read_positions_guard_monotonic() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.last_read_sequence < OLD.last_read_sequence THEN
        RAISE EXCEPTION
            'read position for participant % cannot move from % back to %',
            OLD.participant_id, OLD.last_read_sequence, NEW.last_read_sequence
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_conversation_read_positions_monotonic
    BEFORE UPDATE ON conversation_read_positions
    FOR EACH ROW EXECUTE FUNCTION conversation_read_positions_guard_monotonic();
--rollback DROP TRIGGER trg_conversation_read_positions_monotonic ON conversation_read_positions;
--rollback DROP FUNCTION conversation_read_positions_guard_monotonic();

--changeset ninggiangboy:024-22-notification-artifact-immutability splitStatements:false
-- An approved policy or template is the answer to "why was I sent this, and who signed it off". If it
-- can be edited afterwards, the answer is whatever the last editor decided it should have been.
--
-- Lifecycle may still move -- APPROVED to ACTIVE to RETIRED, and the effective interval may be closed
-- -- because that is administration rather than a change of content. A new wording is a new version.
CREATE FUNCTION notification_policies_freeze_approved() RETURNS TRIGGER AS $$
DECLARE
    candidate notification_policies%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'notification policy %/% is approved; retire it instead of deleting it',
            OLD.policy_key, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'notification policy %/% is approved; publish a new version instead of editing it',
            OLD.policy_key, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_policies_frozen
    BEFORE UPDATE OR DELETE ON notification_policies
    FOR EACH ROW
    WHEN (OLD.status IN ('APPROVED', 'ACTIVE', 'RETIRED'))
    EXECUTE FUNCTION notification_policies_freeze_approved();

CREATE FUNCTION notification_templates_freeze_approved() RETURNS TRIGGER AS $$
DECLARE
    candidate notification_templates%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'template %/% is approved; retire it instead of deleting it',
            OLD.template_family, OLD.template_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'template %/% is approved; publish a new version instead of editing the words',
            OLD.template_family, OLD.template_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_templates_frozen
    BEFORE UPDATE OR DELETE ON notification_templates
    FOR EACH ROW
    WHEN (OLD.status IN ('APPROVED', 'ACTIVE', 'RETIRED'))
    EXECUTE FUNCTION notification_templates_freeze_approved();

-- An intent's identity and its input hash are what make "once" mean anything. The unique key stops a
-- second intent being created for the same event; this stops an existing one being repointed at a
-- different recipient, purpose or policy after the fact, which would achieve the same duplicate by a
-- quieter route. State, scheduling, leases and supersession remain free to move.
CREATE FUNCTION notification_intents_freeze_identity() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.source_event_id <> OLD.source_event_id
        OR NEW.recipient_account_holder_id <> OLD.recipient_account_holder_id
        OR NEW.purpose_code <> OLD.purpose_code
        OR NEW.notification_policy_id <> OLD.notification_policy_id
        OR NEW.input_hash <> OLD.input_hash
    THEN
        RAISE EXCEPTION
            'intent % is already decided; a changed fact is a new intent, not an edited one', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_intents_identity_frozen
    BEFORE UPDATE ON notification_intents
    FOR EACH ROW EXECUTE FUNCTION notification_intents_freeze_identity();

-- Renders and provider observations are evidence of what happened, and a late bounce never erases an
-- earlier delivery. Both are append-only; the attempt's state is the reduction of them.
CREATE FUNCTION notification_evidence_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; record a further observation instead of altering the evidence',
        TG_TABLE_NAME USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_notification_renders_append_only
    BEFORE UPDATE OR DELETE ON notification_renders
    FOR EACH ROW EXECUTE FUNCTION notification_evidence_reject_mutation();

CREATE TRIGGER trg_delivery_observations_append_only
    BEFORE UPDATE OR DELETE ON delivery_observations
    FOR EACH ROW EXECUTE FUNCTION notification_evidence_reject_mutation();
--rollback DROP TRIGGER trg_delivery_observations_append_only ON delivery_observations;
--rollback DROP TRIGGER trg_notification_renders_append_only ON notification_renders;
--rollback DROP FUNCTION notification_evidence_reject_mutation();
--rollback DROP TRIGGER trg_notification_intents_identity_frozen ON notification_intents;
--rollback DROP FUNCTION notification_intents_freeze_identity();
--rollback DROP TRIGGER trg_notification_templates_frozen ON notification_templates;
--rollback DROP FUNCTION notification_templates_freeze_approved();
--rollback DROP TRIGGER trg_notification_policies_frozen ON notification_policies;
--rollback DROP FUNCTION notification_policies_freeze_approved();
