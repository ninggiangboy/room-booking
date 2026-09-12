--liquibase formatted sql

-- A completed booking creates a bounded right to say something, not a review. This migration exists
-- so that what somebody said about a stay is attributable, sealed until both sides have spoken or the
-- window has closed, immutable once written, and separable from every derived number computed out of
-- it -- and so that none of those properties depends on a service remembering them.
--
-- Six things force the shape:
--
--   The right and the review are different objects. Booking completion opens a right with a deadline
--   and a policy snapshot; exercising it produces one review aggregate. One right per cycle and
--   direction, one review per right, one review per booking and direction: three unique constraints,
--   because a second review for the same stay is a rating the platform never earned.
--
--   Double blind is a timing rule, and timing rules leak. A review is sealed until both sides have
--   submitted or the deadline has passed, so nothing may publish before its cycle revealed -- checked
--   by trigger, because a publication row written a moment early tells the other party what happened.
--   The cycle state is monotonic for the same reason: a late moderation removal changes a review's
--   visibility, never the fact that the cycle revealed.
--
--   Original meaning is immutable; corrections are additive. Revisions are insert-only and numbered,
--   the category ratings belonging to a submitted revision cannot be appended to afterwards, and a
--   substantive edit after reveal is refused outright -- typographical, privacy and legal corrections
--   are a named kind of revision rather than an exception to the rule.
--
--   Visibility has five dimensions, not one status. Authoring, cycle disclosure, moderation, public
--   projection and intelligence eligibility move independently; the feature document is explicit that
--   collapsing them produces an unreviewable cross-product. Publication intervals are rows, so
--   removing and restoring a review leaves a history rather than rewriting a timestamp.
--
--   Derived numbers are projections and say what they were derived from. Aggregates carry the exact
--   sum and count they were computed from and a watermark; aspect profiles, reviewer attention and
--   reputation views carry the taxonomy, model and policy versions plus a manifest digest. None of
--   them is the source of a rating, and none may be read without an approved purpose.
--
--   Missing evidence is unknown, not bad. A reputation view with too little behind it records
--   INSUFFICIENT_EVIDENCE rather than a low number, and every aspect value carries a count so a single
--   mention cannot present itself as a finding.
--
-- Note on what this migration does not create. The document proposes review_command_idempotency,
-- review_outbox_events, review_inbox_receipts, review_jobs and review_audit_events. Migration 012
-- delivered command_idempotency_records, outbox_events, consumer_inbox_receipts and append-only
-- audit_events; a second copy of any of them would mean two answers to the same question and a second
-- publisher to operate. Worker leases follow the house pattern set in 021 and 024 -- owner, expiry and
-- fencing token on the row being worked -- rather than a separate jobs table.
--
-- Note on the legacy table. There is nothing to drop: migration 016 retired the whole listing-centric
-- 002-006 stack, including the old reviews table, when it rebuilt supply. Nothing here supersedes a
-- live table.
--
-- Note on moderation. Generic content moderation belongs to the trust and safety domain, which is
-- migration 027. What lives here is the projection of a decision that this domain needs in order to
-- be correct about visibility, keyed by the source event so the same decision cannot be applied twice.

--changeset ninggiangboy:026-01-review-policy-versions
-- The effective-dated rules a review cycle is judged by. Frozen once published, because every right
-- snapshots the version it was opened under and a historical deadline must never move.
CREATE TABLE review_policy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_key                  VARCHAR(48) NOT NULL,
    policy_version              INTEGER NOT NULL,
    market_code                 VARCHAR(2),
    locale_family               VARCHAR(16),

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,

    -- Which booking outcomes open a right, and in which directions.
    eligible_outcomes           VARCHAR(24)[] NOT NULL DEFAULT '{}',
    eligible_directions         VARCHAR(24)[] NOT NULL DEFAULT '{}',
    submission_window_days      SMALLINT NOT NULL,
    deadline_convention         VARCHAR(24) NOT NULL,

    reveal_rule                 VARCHAR(32) NOT NULL,
    maximum_moderation_hold_hours SMALLINT,
    edit_rule                   VARCHAR(32) NOT NULL,
    withdrawal_allowed          BOOLEAN NOT NULL DEFAULT true,
    response_window_days        SMALLINT,

    rating_schema_version       INTEGER NOT NULL,
    category_schema_reference   VARCHAR(128),
    aggregate_rule_version      INTEGER NOT NULL,
    display_rounding_rule       VARCHAR(24) NOT NULL,

    disclosure_version          VARCHAR(32),
    reminder_schedule_family    VARCHAR(48),
    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_policy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_review_policy_versions_identity UNIQUE (policy_key, policy_version),
    CONSTRAINT ck_review_policy_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_review_policy_versions_deadline_convention CHECK (
        deadline_convention IN ('LISTING_LOCAL_MIDNIGHT', 'GUEST_LOCAL_MIDNIGHT', 'EXACT_INSTANT')
    ),
    CONSTRAINT ck_review_policy_versions_reveal_rule CHECK (
        reveal_rule IN ('DOUBLE_BLIND', 'IMMEDIATE', 'LEGACY_IMMEDIATE_PUBLICATION')
    ),
    CONSTRAINT ck_review_policy_versions_edit_rule CHECK (
        edit_rule IN ('UNTIL_COUNTERPART_OR_DEADLINE', 'UNTIL_DEADLINE', 'NO_EDIT')
    ),
    CONSTRAINT ck_review_policy_versions_rounding CHECK (
        display_rounding_rule IN ('HALF_UP_ONE_DECIMAL', 'HALF_EVEN_ONE_DECIMAL', 'FLOOR_ONE_DECIMAL')
    ),
    CONSTRAINT ck_review_policy_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_policy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_review_policy_versions_window CHECK (submission_window_days > 0),
    CONSTRAINT ck_review_policy_versions_hold CHECK (
        maximum_moderation_hold_hours IS NULL OR maximum_moderation_hold_hours > 0
    ),
    CONSTRAINT ck_review_policy_versions_response_window CHECK (
        response_window_days IS NULL OR response_window_days > 0
    ),
    CONSTRAINT ck_review_policy_versions_schema CHECK (
        rating_schema_version > 0 AND aggregate_rule_version > 0
    ),
    CONSTRAINT ck_review_policy_versions_row_version CHECK (version >= 0)
);

-- One open-ended published version per policy and market. Two would make "the current rules" a
-- question with two answers, which is exactly what an effective-dated registry exists to prevent.
CREATE UNIQUE INDEX uk_review_policy_versions_open
    ON review_policy_versions (policy_key, market_code) NULLS NOT DISTINCT
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
CREATE INDEX idx_review_policy_versions_effective
    ON review_policy_versions (policy_key, effective_from DESC);
--rollback DROP TABLE review_policy_versions;

--changeset ninggiangboy:026-02-review-cycles
-- The disclosure coordinator for one completed booking. It owns neither review's content; it owns
-- when either may become visible, which is why its state is monotonic and its reveal epoch is issued
-- once under a lease.
CREATE TABLE review_cycles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id                  UUID NOT NULL,
    booking_revision_id         UUID NOT NULL,
    listing_id                  UUID NOT NULL,
    property_id                 UUID NOT NULL,
    guest_account_holder_id     UUID NOT NULL,
    -- Attribution is snapshotted, not inferred later from current ownership: a listing that changes
    -- hands must not silently reattribute what a guest said about somebody else's service.
    host_account_holder_id      UUID NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,
    listing_time_zone           VARCHAR(64) NOT NULL,

    source_event_id             UUID NOT NULL,
    source_aggregate_version    INTEGER,
    completed_at                TIMESTAMPTZ NOT NULL,

    review_policy_version_id    UUID NOT NULL,
    opens_at                    TIMESTAMPTZ NOT NULL,
    submission_deadline_at      TIMESTAMPTZ NOT NULL,
    local_deadline_date         DATE NOT NULL,
    local_deadline_time         TIME NOT NULL,

    state                       VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    -- Monotonic, and issued exactly once: changeset 026-31 refuses a state that moves backwards and a
    -- reveal epoch that is reissued.
    state_rank                  SMALLINT NOT NULL DEFAULT 0,
    reveal_version              INTEGER,
    revealed_at                 TIMESTAMPTZ,
    reveal_reason               VARCHAR(32),
    maximum_hold_expires_at     TIMESTAMPTZ,

    claimed_at                  TIMESTAMPTZ,
    claimed_by                  VARCHAR(64),
    lease_expires_at            TIMESTAMPTZ,
    fencing_token               BIGINT NOT NULL DEFAULT 0,

    correction_reason           VARCHAR(48),
    superseded_by_cycle_id      UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_cycles_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_review_cycles_revision FOREIGN KEY (booking_revision_id)
        REFERENCES booking_revisions (id),
    CONSTRAINT fk_review_cycles_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_review_cycles_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_review_cycles_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_cycles_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_cycles_policy FOREIGN KEY (review_policy_version_id)
        REFERENCES review_policy_versions (id),
    -- Deferred for the same reason as 025's stay supersession: the live index below forces the
    -- outgoing cycle to name its replacement before that replacement exists.
    CONSTRAINT fk_review_cycles_supersession FOREIGN KEY (superseded_by_cycle_id)
        REFERENCES review_cycles (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_review_cycles_state CHECK (
        state IN ('OPEN', 'ONE_SIDED_SEALED', 'REVEALING', 'REVEALED', 'CLOSED_EMPTY', 'SUPERSEDED')
    ),
    -- The rank is the state restated so it can be compared, paired to it here so the two cannot
    -- disagree and the monotonicity guard can read one integer.
    CONSTRAINT ck_review_cycles_state_rank CHECK (
        (state = 'OPEN' AND state_rank = 0)
            OR (state = 'ONE_SIDED_SEALED' AND state_rank = 1)
            OR (state = 'REVEALING' AND state_rank = 2)
            OR (state = 'REVEALED' AND state_rank = 3)
            OR (state = 'CLOSED_EMPTY' AND state_rank = 3)
            OR (state = 'SUPERSEDED' AND state_rank = 4)
    ),
    CONSTRAINT ck_review_cycles_deadline CHECK (submission_deadline_at > opens_at),
    CONSTRAINT ck_review_cycles_reveal CHECK (
        (state = 'REVEALED')
            = (revealed_at IS NOT NULL AND reveal_version IS NOT NULL AND reveal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_review_cycles_reveal_reason CHECK (
        reveal_reason IS NULL
            OR reveal_reason IN ('BOTH_SUBMITTED', 'DEADLINE_PASSED', 'MAXIMUM_HOLD_REACHED',
                                 'POLICY_IMMEDIATE')
    ),
    CONSTRAINT ck_review_cycles_reveal_version CHECK (reveal_version IS NULL OR reveal_version > 0),
    CONSTRAINT ck_review_cycles_supersession CHECK (
        (state = 'SUPERSEDED') = (superseded_by_cycle_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_cycles_self_supersession CHECK (superseded_by_cycle_id <> id),
    CONSTRAINT ck_review_cycles_lease CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL AND lease_expires_at IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_review_cycles_fencing CHECK (fencing_token >= 0),
    CONSTRAINT ck_review_cycles_version CHECK (version >= 0)
);

-- One live cycle per booking. A second would let one consumed stay produce two sets of ratings.
CREATE UNIQUE INDEX uk_review_cycles_live ON review_cycles (booking_id)
    WHERE state <> 'SUPERSEDED';
CREATE INDEX idx_review_cycles_due ON review_cycles (submission_deadline_at)
    WHERE state IN ('OPEN', 'ONE_SIDED_SEALED');
CREATE INDEX idx_review_cycles_holds ON review_cycles (maximum_hold_expires_at)
    WHERE state = 'REVEALING';
CREATE INDEX idx_review_cycles_leases ON review_cycles (lease_expires_at)
    WHERE claimed_at IS NOT NULL;
CREATE INDEX idx_review_cycles_listing ON review_cycles (listing_id, completed_at DESC);
--rollback DROP TABLE review_cycles;

--changeset ninggiangboy:026-03-review-rights
-- One bounded permission to say something, in one direction, with the policy that granted it frozen
-- onto the row. A right is not reopened because an author withdrew or moderation removed the text;
-- revocation needs a superseding booking fact, not a support preference.
CREATE TABLE review_rights (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_cycle_id             UUID NOT NULL,
    booking_id                  UUID NOT NULL,
    policy_lineage              VARCHAR(48) NOT NULL,
    direction                   VARCHAR(24) NOT NULL,

    author_account_holder_id    UUID NOT NULL,
    -- When a co-host writes for the host, both are recorded. It must never read as though the
    -- principal personally wrote what somebody else wrote.
    represented_account_holder_id UUID,
    subject_listing_id          UUID,
    subject_account_holder_id   UUID,

    state                       VARCHAR(16) NOT NULL DEFAULT 'PENDING_SOURCE',
    opened_at                   TIMESTAMPTZ,
    deadline_at                 TIMESTAMPTZ NOT NULL,
    exercised_at                TIMESTAMPTZ,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(48),
    revocation_source_event_id  UUID,

    review_policy_version_id    UUID NOT NULL,
    source_aggregate_version    INTEGER,
    review_record_id            UUID,
    delegation_reference        VARCHAR(96),
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'EXTENDED',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_rights_cycle FOREIGN KEY (review_cycle_id) REFERENCES review_cycles (id),
    CONSTRAINT fk_review_rights_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_review_rights_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_rights_represented FOREIGN KEY (represented_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_rights_subject_listing FOREIGN KEY (subject_listing_id)
        REFERENCES listings (id),
    CONSTRAINT fk_review_rights_subject_actor FOREIGN KEY (subject_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_rights_policy FOREIGN KEY (review_policy_version_id)
        REFERENCES review_policy_versions (id),
    CONSTRAINT uk_review_rights_cycle_direction UNIQUE (review_cycle_id, direction),
    CONSTRAINT uk_review_rights_booking_direction UNIQUE (booking_id, policy_lineage, direction),
    CONSTRAINT ck_review_rights_direction CHECK (
        direction IN ('GUEST_TO_LISTING', 'HOST_TO_GUEST')
    ),
    CONSTRAINT ck_review_rights_state CHECK (
        state IN ('PENDING_SOURCE', 'OPEN', 'EXERCISED', 'EXPIRED', 'REVOKED')
    ),
    -- The shape of a right depends on its direction. A guest-to-listing right names a listing; a
    -- host-to-guest right names a person. Neither may be silently missing its subject.
    CONSTRAINT ck_review_rights_subject CHECK (
        (direction = 'GUEST_TO_LISTING' AND subject_listing_id IS NOT NULL)
            OR (direction = 'HOST_TO_GUEST' AND subject_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_rights_opened CHECK (state = 'PENDING_SOURCE' OR opened_at IS NOT NULL),
    CONSTRAINT ck_review_rights_exercised CHECK (
        (state = 'EXERCISED') = (exercised_at IS NOT NULL AND review_record_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_rights_revoked CHECK (
        (state = 'REVOKED')
            = (revoked_at IS NOT NULL AND revocation_reason IS NOT NULL
               AND revocation_source_event_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_rights_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_review_rights_version CHECK (version >= 0)
);

-- A right produces at most one review aggregate, and no two rights may point at the same one.
CREATE UNIQUE INDEX uk_review_rights_record ON review_rights (review_record_id)
    WHERE review_record_id IS NOT NULL;
CREATE INDEX idx_review_rights_author ON review_rights (author_account_holder_id, deadline_at)
    WHERE state = 'OPEN';
CREATE INDEX idx_review_rights_due ON review_rights (deadline_at) WHERE state = 'OPEN';
--rollback DROP TABLE review_rights;

--changeset ninggiangboy:026-04-review-records
-- The aggregate identity of one review. It holds no text: the words live in immutable revisions, and
-- this row holds which revision is currently submitted and the five independent dimensions that
-- decide what anybody may see.
CREATE TABLE review_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_right_id             UUID NOT NULL,
    review_cycle_id             UUID NOT NULL,
    booking_id                  UUID NOT NULL,
    listing_id                  UUID,
    direction                   VARCHAR(24) NOT NULL,

    author_account_holder_id    UUID NOT NULL,
    represented_account_holder_id UUID,
    subject_listing_id          UUID,
    subject_account_holder_id   UUID,

    submitted_revision_id       UUID,
    latest_revision_number      INTEGER NOT NULL DEFAULT 0,

    -- Five dimensions, deliberately not one. A combined enum would be an unreviewable cross-product,
    -- and every one of these moves for a different reason and by a different authority.
    authoring_state             VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    disclosure_state            VARCHAR(16) NOT NULL DEFAULT 'SEALED',
    moderation_state            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    public_projection           VARCHAR(16) NOT NULL DEFAULT 'NOT_PUBLIC',
    intelligence_eligibility    VARCHAR(24) NOT NULL DEFAULT 'PENDING',

    submitted_at                TIMESTAMPTZ,
    withdrawn_at                TIMESTAMPTZ,
    withdrawal_reason           VARCHAR(48),
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'EXTENDED',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_records_right FOREIGN KEY (review_right_id) REFERENCES review_rights (id),
    CONSTRAINT fk_review_records_cycle FOREIGN KEY (review_cycle_id) REFERENCES review_cycles (id),
    CONSTRAINT fk_review_records_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_review_records_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_review_records_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_records_represented FOREIGN KEY (represented_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_records_subject_listing FOREIGN KEY (subject_listing_id)
        REFERENCES listings (id),
    CONSTRAINT fk_review_records_subject_actor FOREIGN KEY (subject_account_holder_id)
        REFERENCES account_holders (id),
    -- One review per right, and one per booking and direction. The second is not redundant: it holds
    -- even if a right were ever recreated.
    CONSTRAINT uk_review_records_right UNIQUE (review_right_id),
    CONSTRAINT uk_review_records_booking_direction UNIQUE (booking_id, direction),
    CONSTRAINT ck_review_records_direction CHECK (
        direction IN ('GUEST_TO_LISTING', 'HOST_TO_GUEST')
    ),
    CONSTRAINT ck_review_records_authoring CHECK (
        authoring_state IN ('DRAFT', 'SUBMITTED_FINAL', 'AUTHOR_WITHDRAWN')
    ),
    CONSTRAINT ck_review_records_disclosure CHECK (
        disclosure_state IN ('SEALED', 'REVEAL_ELIGIBLE', 'REVEALED')
    ),
    CONSTRAINT ck_review_records_moderation CHECK (
        moderation_state IN ('PENDING', 'PUBLISH', 'MASK', 'QUARANTINE', 'REJECT', 'REMOVE', 'RESTORED')
    ),
    CONSTRAINT ck_review_records_projection CHECK (
        public_projection IN ('NOT_PUBLIC', 'PUBLIC', 'PUBLIC_REDACTED', 'HIDDEN')
    ),
    CONSTRAINT ck_review_records_intelligence CHECK (
        intelligence_eligibility IN ('PENDING', 'INCLUDED', 'EXCLUDED', 'REPROCESS_REQUIRED')
    ),
    CONSTRAINT ck_review_records_submission CHECK (
        (authoring_state = 'SUBMITTED_FINAL')
            = (submitted_revision_id IS NOT NULL AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT ck_review_records_withdrawal CHECK (
        (authoring_state = 'AUTHOR_WITHDRAWN')
            = (withdrawn_at IS NOT NULL AND withdrawal_reason IS NOT NULL)
    ),
    -- Nothing may be public before its cycle revealed. The projection column is the fast check, and
    -- the trigger in 026-33 makes it true of publication rows too, where the leak would occur.
    CONSTRAINT ck_review_records_public_needs_reveal CHECK (
        public_projection NOT IN ('PUBLIC', 'PUBLIC_REDACTED') OR disclosure_state = 'REVEALED'
    ),
    CONSTRAINT ck_review_records_subject CHECK (
        (direction = 'GUEST_TO_LISTING' AND subject_listing_id IS NOT NULL)
            OR (direction = 'HOST_TO_GUEST' AND subject_account_holder_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_records_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_review_records_revision_number CHECK (latest_revision_number >= 0),
    CONSTRAINT ck_review_records_version CHECK (version >= 0)
);

CREATE INDEX idx_review_records_listing ON review_records (listing_id, submitted_at DESC)
    WHERE public_projection IN ('PUBLIC', 'PUBLIC_REDACTED');
CREATE INDEX idx_review_records_author ON review_records (author_account_holder_id, created_at DESC);
CREATE INDEX idx_review_records_subject_actor
    ON review_records (subject_account_holder_id, created_at DESC);
CREATE INDEX idx_review_records_cycle ON review_records (review_cycle_id);
CREATE INDEX idx_review_records_intelligence ON review_records (updated_at)
    WHERE intelligence_eligibility IN ('PENDING', 'REPROCESS_REQUIRED');
--rollback DROP TABLE review_records;

--changeset ninggiangboy:026-05-review-revisions
-- What was actually said, insert-only and numbered. An edit is a new revision; the earlier one stays
-- readable, which is what lets a moderation decision be tied to the exact text it judged rather than
-- to whatever the review says today.
CREATE TABLE review_revisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_record_id            UUID NOT NULL,
    revision_number             INTEGER NOT NULL,
    revision_kind               VARCHAR(24) NOT NULL DEFAULT 'AUTHOR_SUBMISSION',

    overall_rating              SMALLINT,
    rating_schema_version       INTEGER NOT NULL,

    public_text                 TEXT,
    original_locale             VARCHAR(16),
    detected_language           VARCHAR(16),
    detected_language_confidence NUMERIC(4, 3),
    structured_public_reference VARCHAR(256),
    structured_private_reference VARCHAR(256),

    content_digest              CHAR(64) NOT NULL,
    client_submission_id        VARCHAR(96),
    client_request_digest       CHAR(64),

    author_account_holder_id    UUID NOT NULL,
    acting_account_holder_id    UUID,

    supersedes_revision_id      UUID,
    correction_reason           VARCHAR(48),
    disclosure_version          VARCHAR(32),
    review_policy_version_id    UUID NOT NULL,

    received_at                 TIMESTAMPTZ NOT NULL,
    committed_at                TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_revisions_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_revisions_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_revisions_acting FOREIGN KEY (acting_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_revisions_supersedes FOREIGN KEY (supersedes_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT fk_review_revisions_policy FOREIGN KEY (review_policy_version_id)
        REFERENCES review_policy_versions (id),
    CONSTRAINT uk_review_revisions_number UNIQUE (review_record_id, revision_number),
    CONSTRAINT ck_review_revisions_kind CHECK (
        revision_kind IN ('AUTHOR_SUBMISSION', 'TYPOGRAPHICAL_CORRECTION', 'PRIVACY_REDACTION',
                          'LEGAL_CORRECTION', 'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_review_revisions_number CHECK (revision_number > 0),
    CONSTRAINT ck_review_revisions_rating CHECK (
        overall_rating IS NULL OR (overall_rating >= 1 AND overall_rating <= 5)
    ),
    CONSTRAINT ck_review_revisions_language_confidence CHECK (
        detected_language_confidence IS NULL
            OR (detected_language_confidence >= 0 AND detected_language_confidence <= 1)
    ),
    -- A correction says why. An ordinary submission does not need a reason; a redaction does.
    CONSTRAINT ck_review_revisions_correction CHECK (
        revision_kind NOT IN ('TYPOGRAPHICAL_CORRECTION', 'PRIVACY_REDACTION', 'LEGAL_CORRECTION')
            OR (correction_reason IS NOT NULL AND supersedes_revision_id IS NOT NULL)
    ),
    CONSTRAINT ck_review_revisions_self_supersession CHECK (supersedes_revision_id <> id),
    CONSTRAINT ck_review_revisions_schema CHECK (rating_schema_version > 0)
);

-- An idempotent resubmission returns the revision it already created rather than writing a second.
CREATE UNIQUE INDEX uk_review_revisions_client_submission
    ON review_revisions (review_record_id, client_submission_id)
    WHERE client_submission_id IS NOT NULL;
CREATE INDEX idx_review_revisions_record ON review_revisions (review_record_id, revision_number DESC);
--rollback DROP TABLE review_revisions;

--changeset ninggiangboy:026-06-review-category-values
-- The per-category ratings belonging to one revision. They are part of what was said, so they are
-- written with their revision and never appended to a submitted one -- changeset 026-32 refuses that,
-- because adding a cleanliness score after the fact changes the review as surely as editing the text.
CREATE TABLE review_category_values (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_revision_id          UUID NOT NULL,
    category_code               VARCHAR(32) NOT NULL,
    category_schema_version     INTEGER NOT NULL,

    rating_value                SMALLINT,
    not_applicable              BOOLEAN NOT NULL DEFAULT false,
    subject_target              VARCHAR(24) NOT NULL DEFAULT 'LISTING',
    is_public                   BOOLEAN NOT NULL DEFAULT true,
    structured_reason_reference VARCHAR(256),

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_category_values_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT uk_review_category_values_category UNIQUE (review_revision_id, category_code),
    CONSTRAINT ck_review_category_values_target CHECK (
        subject_target IN ('LISTING', 'HOST_SERVICE', 'GUEST_CONDUCT', 'PLATFORM')
    ),
    -- A category is a rating or an explicit "not applicable". It is never silently absent, because a
    -- missing score and a score of one are different facts about the stay.
    CONSTRAINT ck_review_category_values_value CHECK (
        (not_applicable AND rating_value IS NULL)
            OR (NOT not_applicable AND rating_value IS NOT NULL
                AND rating_value >= 1 AND rating_value <= 5)
    ),
    CONSTRAINT ck_review_category_values_schema CHECK (category_schema_version > 0)
);

CREATE INDEX idx_review_category_values_revision ON review_category_values (review_revision_id);
--rollback DROP TABLE review_category_values;

--changeset ninggiangboy:026-07-review-publications
-- When a review was visible, as an interval rather than a timestamp. Removing and restoring produce
-- two rows, so the history of what the public could see is reconstructible; rewriting a published_at
-- would lose exactly that.
CREATE TABLE review_publications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_record_id            UUID NOT NULL,
    review_revision_id          UUID NOT NULL,
    review_cycle_id             UUID NOT NULL,
    reveal_version              INTEGER NOT NULL,

    published_from              TIMESTAMPTZ NOT NULL,
    published_until             TIMESTAMPTZ,
    publication_reason          VARCHAR(32) NOT NULL,
    retraction_reason           VARCHAR(48),

    public_derivative_reference VARCHAR(256),
    redaction_reference         VARCHAR(256),
    moderation_decision_id      UUID,
    moderation_decision_version INTEGER,
    review_policy_version_id    UUID NOT NULL,

    aggregate_event_id          UUID,
    actor_type                  VARCHAR(16) NOT NULL DEFAULT 'SYSTEM',
    actor_account_holder_id     UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_publications_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_publications_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT fk_review_publications_cycle FOREIGN KEY (review_cycle_id)
        REFERENCES review_cycles (id),
    CONSTRAINT fk_review_publications_policy FOREIGN KEY (review_policy_version_id)
        REFERENCES review_policy_versions (id),
    CONSTRAINT fk_review_publications_actor FOREIGN KEY (actor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_review_publications_reason CHECK (
        publication_reason IN ('CYCLE_REVEAL', 'MODERATION_RESTORED', 'LEGAL_CORRECTION',
                               'LEGACY_IMPORT')
    ),
    CONSTRAINT ck_review_publications_retraction CHECK (
        (published_until IS NULL) = (retraction_reason IS NULL)
    ),
    CONSTRAINT ck_review_publications_interval CHECK (
        published_until IS NULL OR published_until > published_from
    ),
    CONSTRAINT ck_review_publications_actor CHECK (
        actor_type IN ('USER', 'OPERATOR', 'SYSTEM', 'PROVIDER', 'ANONYMOUS')
    ),
    CONSTRAINT ck_review_publications_reveal_version CHECK (reveal_version > 0),
    -- Two overlapping intervals for one review would make "was it visible then" unanswerable. The
    -- exclusion constraint is the same device migration 018 uses to stop double-booking a unit.
    CONSTRAINT ex_review_publications_overlap EXCLUDE USING GIST (
        review_record_id WITH =,
        tstzrange(published_from, published_until, '[)') WITH &&
    )
);

CREATE UNIQUE INDEX uk_review_publications_open ON review_publications (review_record_id)
    WHERE published_until IS NULL;
CREATE INDEX idx_review_publications_record ON review_publications (review_record_id, published_from DESC);
--rollback DROP TABLE review_publications;

--changeset ninggiangboy:026-08-review-responses
-- The host's public answer to a published review. One per review under the single-response model, so
-- a host cannot bury a rating under a thread.
CREATE TABLE review_responses (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_record_id            UUID NOT NULL,
    listing_id                  UUID,
    responder_account_holder_id UUID NOT NULL,
    acting_account_holder_id    UUID,

    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    response_deadline_at        TIMESTAMPTZ,
    submitted_at                TIMESTAMPTZ,
    withdrawn_at                TIMESTAMPTZ,
    withdrawal_reason           VARCHAR(48),

    submitted_revision_id       UUID,
    latest_revision_number      INTEGER NOT NULL DEFAULT 0,
    moderation_state            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    public_projection           VARCHAR(16) NOT NULL DEFAULT 'NOT_PUBLIC',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_responses_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_responses_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_review_responses_responder FOREIGN KEY (responder_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_responses_acting FOREIGN KEY (acting_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_review_responses_state CHECK (
        state IN ('DRAFT', 'SUBMITTED', 'WITHDRAWN', 'EXPIRED')
    ),
    CONSTRAINT ck_review_responses_moderation CHECK (
        moderation_state IN ('PENDING', 'PUBLISH', 'MASK', 'QUARANTINE', 'REJECT', 'REMOVE', 'RESTORED')
    ),
    CONSTRAINT ck_review_responses_projection CHECK (
        public_projection IN ('NOT_PUBLIC', 'PUBLIC', 'PUBLIC_REDACTED', 'HIDDEN')
    ),
    CONSTRAINT ck_review_responses_submission CHECK (
        (state = 'SUBMITTED') = (submitted_revision_id IS NOT NULL AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT ck_review_responses_withdrawal CHECK (
        (state = 'WITHDRAWN') = (withdrawn_at IS NOT NULL AND withdrawal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_review_responses_revision_number CHECK (latest_revision_number >= 0),
    CONSTRAINT ck_review_responses_version CHECK (version >= 0)
);

-- One live response per review. A withdrawn one leaves the way clear for a replacement.
CREATE UNIQUE INDEX uk_review_responses_live ON review_responses (review_record_id)
    WHERE state IN ('DRAFT', 'SUBMITTED');
CREATE INDEX idx_review_responses_due ON review_responses (response_deadline_at)
    WHERE state = 'DRAFT';
--rollback DROP TABLE review_responses;

--changeset ninggiangboy:026-09-review-response-revisions
-- Insert-only text for a response, for the same reason review text is: a moderation decision must be
-- tied to the words it judged.
CREATE TABLE review_response_revisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_response_id          UUID NOT NULL,
    revision_number             INTEGER NOT NULL,
    response_text               TEXT NOT NULL,
    original_locale             VARCHAR(16),
    content_digest              CHAR(64) NOT NULL,
    author_account_holder_id    UUID NOT NULL,
    acting_account_holder_id    UUID,
    supersedes_revision_id      UUID,
    received_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_response_revisions_response FOREIGN KEY (review_response_id)
        REFERENCES review_responses (id),
    CONSTRAINT fk_review_response_revisions_author FOREIGN KEY (author_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_response_revisions_acting FOREIGN KEY (acting_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_response_revisions_supersedes FOREIGN KEY (supersedes_revision_id)
        REFERENCES review_response_revisions (id),
    CONSTRAINT uk_review_response_revisions_number UNIQUE (review_response_id, revision_number),
    CONSTRAINT ck_review_response_revisions_number CHECK (revision_number > 0),
    CONSTRAINT ck_review_response_revisions_self CHECK (supersedes_revision_id <> id)
);

CREATE INDEX idx_review_response_revisions_response
    ON review_response_revisions (review_response_id, revision_number DESC);
--rollback DROP TABLE review_response_revisions;

--changeset ninggiangboy:026-10-review-private-feedback
-- What one party said to the other, or to the platform, that was never meant to be public. Explicitly
-- excluded from aggregates and from ordinary aspect processing: it is not a rating and must not become
-- one by being counted.
CREATE TABLE review_private_feedback (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_revision_id          UUID,
    review_right_id             UUID NOT NULL,
    audience                    VARCHAR(16) NOT NULL,
    purpose_code                VARCHAR(48) NOT NULL,

    payload_reference           VARCHAR(256) NOT NULL,
    payload_digest              CHAR(64) NOT NULL,
    encryption_key_reference    VARCHAR(128),

    legal_basis                 VARCHAR(24) NOT NULL,
    consent_reference           VARCHAR(96),
    retention_class             VARCHAR(16) NOT NULL DEFAULT 'STANDARD',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    -- Stated, not implied. A later pipeline that wants this material has to change a column and be
    -- seen doing it.
    excluded_from_aggregates    BOOLEAN NOT NULL DEFAULT true,
    excluded_from_intelligence  BOOLEAN NOT NULL DEFAULT true,

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_private_feedback_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT fk_review_private_feedback_right FOREIGN KEY (review_right_id)
        REFERENCES review_rights (id),
    CONSTRAINT ck_review_private_feedback_audience CHECK (
        audience IN ('HOST', 'GUEST', 'PLATFORM', 'SUPPORT')
    ),
    CONSTRAINT ck_review_private_feedback_basis CHECK (
        legal_basis IN ('CONSENT', 'CONTRACT', 'LEGITIMATE_INTEREST', 'LEGAL_OBLIGATION')
    ),
    CONSTRAINT ck_review_private_feedback_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_review_private_feedback_consent CHECK (
        legal_basis <> 'CONSENT' OR consent_reference IS NOT NULL
    )
);

CREATE INDEX idx_review_private_feedback_right ON review_private_feedback (review_right_id);
--rollback DROP TABLE review_private_feedback;

--changeset ninggiangboy:026-11-review-translations
-- A derived reading of an exact revision, never a replacement for it. Keyed by the source digest as
-- well as the revision, so a translation cannot silently survive a correction to the text it renders.
CREATE TABLE review_translations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_revision_id          UUID NOT NULL,
    source_digest               CHAR(64) NOT NULL,
    target_locale               VARCHAR(16) NOT NULL,

    translation_source          VARCHAR(16) NOT NULL,
    engine_version              VARCHAR(48),
    glossary_version            VARCHAR(48),
    translated_text             TEXT,
    content_reference           VARCHAR(256),
    confidence                  NUMERIC(4, 3),

    moderation_state            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    superseded_by_translation_id UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_translations_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT fk_review_translations_supersession FOREIGN KEY (superseded_by_translation_id)
        REFERENCES review_translations (id),
    CONSTRAINT uk_review_translations_identity
        UNIQUE (review_revision_id, source_digest, target_locale, translation_source, engine_version),
    CONSTRAINT ck_review_translations_source CHECK (
        translation_source IN ('HOST', 'MACHINE', 'PROFESSIONAL')
    ),
    CONSTRAINT ck_review_translations_moderation CHECK (
        moderation_state IN ('PENDING', 'PUBLISH', 'MASK', 'QUARANTINE', 'REJECT', 'REMOVE', 'RESTORED')
    ),
    CONSTRAINT ck_review_translations_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    ),
    CONSTRAINT ck_review_translations_content CHECK (
        translated_text IS NOT NULL OR content_reference IS NOT NULL
    ),
    CONSTRAINT ck_review_translations_self CHECK (superseded_by_translation_id <> id)
);

CREATE INDEX idx_review_translations_revision ON review_translations (review_revision_id, target_locale);
--rollback DROP TABLE review_translations;

--changeset ninggiangboy:026-12-review-media
-- Photographs attached to a review, behind the same quarantine discipline as every other upload: a
-- file is not part of a review until scanning has cleared it.
CREATE TABLE review_media (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_revision_id          UUID NOT NULL,
    media_type                  VARCHAR(16) NOT NULL,
    object_reference            VARCHAR(256) NOT NULL,
    content_digest              CHAR(64) NOT NULL,
    byte_size                   BIGINT,
    display_order               SMALLINT NOT NULL DEFAULT 0,

    scan_state                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    moderation_state            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    public_projection           VARCHAR(16) NOT NULL DEFAULT 'NOT_PUBLIC',
    derivative_reference        VARCHAR(256),

    retention_class             VARCHAR(16) NOT NULL DEFAULT 'EXTENDED',
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_media_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT uk_review_media_object UNIQUE (review_revision_id, object_reference),
    CONSTRAINT ck_review_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_review_media_scan CHECK (
        scan_state IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED', 'NOT_APPLICABLE')
    ),
    CONSTRAINT ck_review_media_moderation CHECK (
        moderation_state IN ('PENDING', 'PUBLISH', 'MASK', 'QUARANTINE', 'REJECT', 'REMOVE', 'RESTORED')
    ),
    CONSTRAINT ck_review_media_projection CHECK (
        public_projection IN ('NOT_PUBLIC', 'PUBLIC', 'PUBLIC_REDACTED', 'HIDDEN')
    ),
    -- Nothing unscanned is ever shown. The same rule migration 024 applies to message attachments.
    CONSTRAINT ck_review_media_public_needs_clean CHECK (
        public_projection NOT IN ('PUBLIC', 'PUBLIC_REDACTED') OR scan_state = 'CLEAN'
    ),
    CONSTRAINT ck_review_media_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_review_media_size CHECK (byte_size IS NULL OR byte_size > 0),
    CONSTRAINT ck_review_media_order CHECK (display_order >= 0),
    CONSTRAINT ck_review_media_version CHECK (version >= 0)
);

CREATE INDEX idx_review_media_revision ON review_media (review_revision_id, display_order);
CREATE INDEX idx_review_media_pending_scan ON review_media (created_at)
    WHERE scan_state IN ('PENDING', 'FAILED');
--rollback DROP TABLE review_media;

--changeset ninggiangboy:026-13-review-moderation-applications
-- The projection of a trust-and-safety decision that this domain needs in order to be correct about
-- visibility. The decision itself belongs to migration 027; what is kept here is which decision was
-- applied to which exact revision, keyed by source event so one decision cannot be applied twice.
CREATE TABLE review_moderation_applications (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_record_id            UUID NOT NULL,
    review_revision_id          UUID NOT NULL,
    source_event_id             UUID NOT NULL,

    moderation_decision_id      UUID NOT NULL,
    moderation_decision_version INTEGER NOT NULL,
    decision_digest             CHAR(64) NOT NULL,
    action                      VARCHAR(16) NOT NULL,
    policy_category             VARCHAR(48),

    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    applied_projection          VARCHAR(16) NOT NULL,
    applied_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_moderation_applications_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_moderation_applications_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    -- One application per source event. A replayed event changes nothing a second time.
    CONSTRAINT uk_review_moderation_applications_event UNIQUE (source_event_id),
    CONSTRAINT ck_review_moderation_applications_action CHECK (
        action IN ('PUBLISH', 'MASK', 'QUARANTINE', 'REJECT', 'REMOVE', 'RESTORED')
    ),
    CONSTRAINT ck_review_moderation_applications_projection CHECK (
        applied_projection IN ('NOT_PUBLIC', 'PUBLIC', 'PUBLIC_REDACTED', 'HIDDEN')
    ),
    CONSTRAINT ck_review_moderation_applications_interval CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_review_moderation_applications_version CHECK (moderation_decision_version > 0)
);

CREATE INDEX idx_review_moderation_applications_revision
    ON review_moderation_applications (review_revision_id, applied_at DESC);
CREATE INDEX idx_review_moderation_applications_record
    ON review_moderation_applications (review_record_id, applied_at DESC);
--rollback DROP TABLE review_moderation_applications;

--changeset ninggiangboy:026-14-review-helpful-votes
-- One vote per person per review. The author's own account holder is denormalized onto the row so
-- that voting for yourself is refused by the database and not only by the service that forgets.
CREATE TABLE review_helpful_votes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_record_id            UUID NOT NULL,
    voter_account_holder_id     UUID NOT NULL,
    review_author_account_holder_id UUID NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_helpful_votes_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_helpful_votes_voter FOREIGN KEY (voter_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_review_helpful_votes_voter UNIQUE (review_record_id, voter_account_holder_id),
    CONSTRAINT ck_review_helpful_votes_state CHECK (state IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_review_helpful_votes_self CHECK (
        voter_account_holder_id <> review_author_account_holder_id
    ),
    CONSTRAINT ck_review_helpful_votes_version CHECK (version >= 0)
);

CREATE INDEX idx_review_helpful_votes_record ON review_helpful_votes (review_record_id)
    WHERE state = 'ACTIVE';
--rollback DROP TABLE review_helpful_votes;

--changeset ninggiangboy:026-15-review-interaction-events
-- Append-only record of what people did with a review in public. Kept narrow and bounded on purpose:
-- the feature document is explicit that click volume must never be able to harm review writes, and
-- that this eventually belongs in analytical storage rather than here.
CREATE TABLE review_interaction_events (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_record_id            UUID NOT NULL,
    review_publication_id       UUID,
    interaction_type            VARCHAR(16) NOT NULL,
    actor_account_holder_id     UUID,
    session_reference           VARCHAR(96),
    request_id                  VARCHAR(96) NOT NULL,
    surface                     VARCHAR(32),
    position                    SMALLINT,
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_interaction_events_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_interaction_events_publication FOREIGN KEY (review_publication_id)
        REFERENCES review_publications (id),
    CONSTRAINT uk_review_interaction_events_request UNIQUE (request_id, interaction_type),
    CONSTRAINT ck_review_interaction_events_type CHECK (
        interaction_type IN ('DISPLAY', 'EXPAND', 'HELPFUL', 'UNHELPFUL', 'REPORT', 'TRANSLATE')
    ),
    CONSTRAINT ck_review_interaction_events_position CHECK (position IS NULL OR position >= 0)
);

CREATE INDEX idx_review_interaction_events_record
    ON review_interaction_events (review_record_id, occurred_at DESC);
--rollback DROP TABLE review_interaction_events;

--changeset ninggiangboy:026-16-review-public-aggregates
-- The transparent public average: the exact sum and count it was computed from, plus the distribution
-- it came out of. Nothing here is a model output -- that is the quality profile below, kept separate
-- so the number shown to guests stays explainable by arithmetic.
CREATE TABLE review_public_aggregates (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type                VARCHAR(16) NOT NULL,
    subject_id                  UUID NOT NULL,
    direction                   VARCHAR(24) NOT NULL,
    dimension_code              VARCHAR(32) NOT NULL,
    aggregate_rule_version      INTEGER NOT NULL,

    rating_sum                  BIGINT NOT NULL DEFAULT 0,
    rating_count                BIGINT NOT NULL DEFAULT 0,
    distribution_one            BIGINT NOT NULL DEFAULT 0,
    distribution_two            BIGINT NOT NULL DEFAULT 0,
    distribution_three          BIGINT NOT NULL DEFAULT 0,
    distribution_four           BIGINT NOT NULL DEFAULT 0,
    distribution_five           BIGINT NOT NULL DEFAULT 0,

    computed_average            NUMERIC(6, 4),
    display_value               NUMERIC(3, 1),
    publication_epoch           BIGINT NOT NULL DEFAULT 0,
    input_watermark             TIMESTAMPTZ,
    computed_at                 TIMESTAMPTZ,
    verified_at                 TIMESTAMPTZ,
    is_stale                    BOOLEAN NOT NULL DEFAULT false,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_review_public_aggregates_key
        UNIQUE (subject_type, subject_id, direction, dimension_code, aggregate_rule_version),
    CONSTRAINT ck_review_public_aggregates_subject CHECK (
        subject_type IN ('LISTING', 'HOST', 'GUEST', 'PROPERTY')
    ),
    CONSTRAINT ck_review_public_aggregates_direction CHECK (
        direction IN ('GUEST_TO_LISTING', 'HOST_TO_GUEST')
    ),
    CONSTRAINT ck_review_public_aggregates_counts CHECK (
        rating_sum >= 0 AND rating_count >= 0
            AND distribution_one >= 0 AND distribution_two >= 0 AND distribution_three >= 0
            AND distribution_four >= 0 AND distribution_five >= 0
    ),
    -- The distribution is the count, broken down. If the two disagree, one of them is wrong and the
    -- displayed average cannot be trusted either.
    CONSTRAINT ck_review_public_aggregates_distribution CHECK (
        distribution_one + distribution_two + distribution_three + distribution_four
            + distribution_five = rating_count
    ),
    -- A sum of ratings between one and five cannot exceed five times the count or fall below it.
    CONSTRAINT ck_review_public_aggregates_sum_bounds CHECK (
        rating_sum >= rating_count AND rating_sum <= rating_count * 5
    ),
    -- An average with nothing behind it is not zero, it is absent. Missing evidence is unknown.
    CONSTRAINT ck_review_public_aggregates_average CHECK (
        computed_average IS NULL OR (rating_count > 0 AND computed_average BETWEEN 1 AND 5)
    ),
    CONSTRAINT ck_review_public_aggregates_epoch CHECK (publication_epoch >= 0),
    CONSTRAINT ck_review_public_aggregates_rule CHECK (aggregate_rule_version > 0),
    CONSTRAINT ck_review_public_aggregates_version CHECK (version >= 0)
);

CREATE INDEX idx_review_public_aggregates_stale ON review_public_aggregates (input_watermark)
    WHERE is_stale;
CREATE INDEX idx_review_public_aggregates_subject
    ON review_public_aggregates (subject_type, subject_id);
--rollback DROP TABLE review_public_aggregates;

--changeset ninggiangboy:026-17-review-aggregate-contributions
-- Which publication contributed which value to which aggregate. Exists so that an incremental delta
-- can be proved idempotent and a rebuild can be compared against what was actually counted, rather
-- than both being believed.
CREATE TABLE review_aggregate_contributions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_public_aggregate_id  UUID NOT NULL,
    aggregate_rule_version      INTEGER NOT NULL,
    review_record_id            UUID NOT NULL,
    review_publication_id       UUID NOT NULL,

    contributed_value           SMALLINT NOT NULL,
    included_from               TIMESTAMPTZ NOT NULL,
    included_until              TIMESTAMPTZ,
    source_event_id             UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_aggregate_contributions_aggregate FOREIGN KEY (review_public_aggregate_id)
        REFERENCES review_public_aggregates (id),
    CONSTRAINT fk_review_aggregate_contributions_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_aggregate_contributions_publication FOREIGN KEY (review_publication_id)
        REFERENCES review_publications (id),
    CONSTRAINT uk_review_aggregate_contributions_identity
        UNIQUE (review_public_aggregate_id, review_publication_id),
    CONSTRAINT ck_review_aggregate_contributions_value CHECK (
        contributed_value >= 1 AND contributed_value <= 5
    ),
    CONSTRAINT ck_review_aggregate_contributions_interval CHECK (
        included_until IS NULL OR included_until > included_from
    )
);

CREATE INDEX idx_review_aggregate_contributions_aggregate
    ON review_aggregate_contributions (review_public_aggregate_id)
    WHERE included_until IS NULL;
--rollback DROP TABLE review_aggregate_contributions;

--changeset ninggiangboy:026-18-listing-quality-profiles
-- The ranking-quality projection, kept apart from the public average on purpose. A Bayesian posterior
-- with an uncertainty interval is a good input to ordering and a bad thing to show as "the rating",
-- so it lives in its own table with the model version that produced it.
CREATE TABLE listing_quality_profiles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id                  UUID NOT NULL,
    profile_version             INTEGER NOT NULL,

    posterior_mean              NUMERIC(6, 4),
    posterior_interval_low      NUMERIC(6, 4),
    posterior_interval_high     NUMERIC(6, 4),
    evidence_count              BIGINT NOT NULL DEFAULT 0,
    effective_evidence          NUMERIC(10, 4),
    recent_window_days          SMALLINT,
    recent_mean                 NUMERIC(6, 4),
    trend_direction             VARCHAR(16),

    aggregate_rule_version      INTEGER NOT NULL,
    model_version               VARCHAR(48) NOT NULL,
    input_watermark             TIMESTAMPTZ,
    source_manifest_digest      CHAR(64),
    status                      VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
    computed_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    superseded_by_profile_id    UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_listing_quality_profiles_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_listing_quality_profiles_supersession FOREIGN KEY (superseded_by_profile_id)
        REFERENCES listing_quality_profiles (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_listing_quality_profiles_version UNIQUE (listing_id, profile_version),
    CONSTRAINT ck_listing_quality_profiles_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    CONSTRAINT ck_listing_quality_profiles_trend CHECK (
        trend_direction IS NULL OR trend_direction IN ('IMPROVING', 'STABLE', 'DECLINING', 'UNKNOWN')
    ),
    CONSTRAINT ck_listing_quality_profiles_bounds CHECK (
        (posterior_mean IS NULL OR posterior_mean BETWEEN 1 AND 5)
            AND (recent_mean IS NULL OR recent_mean BETWEEN 1 AND 5)
    ),
    -- An interval that does not contain its own mean is not an uncertainty interval.
    CONSTRAINT ck_listing_quality_profiles_interval CHECK (
        posterior_interval_low IS NULL OR posterior_interval_high IS NULL
            OR posterior_mean IS NULL
            OR (posterior_interval_low <= posterior_mean
                AND posterior_mean <= posterior_interval_high)
    ),
    CONSTRAINT ck_listing_quality_profiles_evidence CHECK (
        evidence_count >= 0 AND (effective_evidence IS NULL OR effective_evidence >= 0)
    ),
    CONSTRAINT ck_listing_quality_profiles_profile_version CHECK (profile_version > 0),
    CONSTRAINT ck_listing_quality_profiles_self CHECK (superseded_by_profile_id <> id),
    CONSTRAINT ck_listing_quality_profiles_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_listing_quality_profiles_current ON listing_quality_profiles (listing_id)
    WHERE status = 'CURRENT';
CREATE INDEX idx_listing_quality_profiles_stale ON listing_quality_profiles (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE listing_quality_profiles;

--changeset ninggiangboy:026-19-host-review-profiles
-- The same projection for a host rather than a listing, because the two are different subjects: a
-- host may run several listings, and a listing may change hands.
CREATE TABLE host_review_profiles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_account_holder_id      UUID NOT NULL,
    profile_version             INTEGER NOT NULL,

    posterior_mean              NUMERIC(6, 4),
    posterior_interval_low      NUMERIC(6, 4),
    posterior_interval_high     NUMERIC(6, 4),
    evidence_count              BIGINT NOT NULL DEFAULT 0,
    effective_evidence          NUMERIC(10, 4),
    listing_count               INTEGER NOT NULL DEFAULT 0,
    recent_window_days          SMALLINT,
    recent_mean                 NUMERIC(6, 4),
    trend_direction             VARCHAR(16),

    aggregate_rule_version      INTEGER NOT NULL,
    model_version               VARCHAR(48) NOT NULL,
    input_watermark             TIMESTAMPTZ,
    source_manifest_digest      CHAR(64),
    status                      VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
    computed_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    superseded_by_profile_id    UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_review_profiles_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_review_profiles_supersession FOREIGN KEY (superseded_by_profile_id)
        REFERENCES host_review_profiles (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_host_review_profiles_version UNIQUE (host_account_holder_id, profile_version),
    CONSTRAINT ck_host_review_profiles_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    CONSTRAINT ck_host_review_profiles_trend CHECK (
        trend_direction IS NULL OR trend_direction IN ('IMPROVING', 'STABLE', 'DECLINING', 'UNKNOWN')
    ),
    CONSTRAINT ck_host_review_profiles_bounds CHECK (
        (posterior_mean IS NULL OR posterior_mean BETWEEN 1 AND 5)
            AND (recent_mean IS NULL OR recent_mean BETWEEN 1 AND 5)
    ),
    CONSTRAINT ck_host_review_profiles_interval CHECK (
        posterior_interval_low IS NULL OR posterior_interval_high IS NULL
            OR posterior_mean IS NULL
            OR (posterior_interval_low <= posterior_mean
                AND posterior_mean <= posterior_interval_high)
    ),
    CONSTRAINT ck_host_review_profiles_evidence CHECK (
        evidence_count >= 0 AND listing_count >= 0
            AND (effective_evidence IS NULL OR effective_evidence >= 0)
    ),
    CONSTRAINT ck_host_review_profiles_profile_version CHECK (profile_version > 0),
    CONSTRAINT ck_host_review_profiles_self CHECK (superseded_by_profile_id <> id),
    CONSTRAINT ck_host_review_profiles_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_host_review_profiles_current ON host_review_profiles (host_account_holder_id)
    WHERE status = 'CURRENT';
CREATE INDEX idx_host_review_profiles_stale ON host_review_profiles (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE host_review_profiles;

--changeset ninggiangboy:026-20-aspect-taxonomy-versions
-- The vocabulary extraction is allowed to use, versioned. Frozen once active, because every mention
-- and every profile value names the version it was produced under; a taxonomy edited in place would
-- silently change what a stored mention means.
CREATE TABLE aspect_taxonomy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxonomy_key                VARCHAR(48) NOT NULL,
    taxonomy_version            INTEGER NOT NULL,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,
    checksum                    CHAR(64) NOT NULL,
    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_aspect_taxonomy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_aspect_taxonomy_versions_identity UNIQUE (taxonomy_key, taxonomy_version),
    CONSTRAINT ck_aspect_taxonomy_versions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_aspect_taxonomy_versions_activation CHECK (
        status = 'DRAFT' OR (effective_from IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_aspect_taxonomy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_aspect_taxonomy_versions_number CHECK (taxonomy_version > 0),
    CONSTRAINT ck_aspect_taxonomy_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_aspect_taxonomy_versions_active ON aspect_taxonomy_versions (taxonomy_key)
    WHERE status = 'ACTIVE';
--rollback DROP TABLE aspect_taxonomy_versions;

--changeset ninggiangboy:026-21-aspect-definitions
-- One aspect within one taxonomy version. The sensitive-use tier is here rather than in code because
-- it decides whether an aspect may be shown to a guest, fed to ranking, or used at all.
CREATE TABLE aspect_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aspect_taxonomy_version_id  UUID NOT NULL,
    aspect_code                 VARCHAR(48) NOT NULL,
    aspect_group                VARCHAR(48) NOT NULL,
    definition_text             VARCHAR(512) NOT NULL,
    allowed_targets             VARCHAR(24)[] NOT NULL DEFAULT '{}',
    sensitive_use_tier          VARCHAR(24) NOT NULL DEFAULT 'GENERAL',
    localized_label_reference   VARCHAR(256),
    example_reference           VARCHAR(256),
    successor_aspect_code       VARCHAR(48),
    deprecated_at               TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_aspect_definitions_taxonomy FOREIGN KEY (aspect_taxonomy_version_id)
        REFERENCES aspect_taxonomy_versions (id),
    CONSTRAINT uk_aspect_definitions_code UNIQUE (aspect_taxonomy_version_id, aspect_code),
    CONSTRAINT ck_aspect_definitions_tier CHECK (
        sensitive_use_tier IN ('GENERAL', 'RESTRICTED', 'PROHIBITED_FOR_RANKING')
    ),
    CONSTRAINT ck_aspect_definitions_targets CHECK (cardinality(allowed_targets) > 0),
    CONSTRAINT ck_aspect_definitions_successor CHECK (successor_aspect_code <> aspect_code)
);

CREATE INDEX idx_aspect_definitions_taxonomy ON aspect_definitions (aspect_taxonomy_version_id);
--rollback DROP TABLE aspect_definitions;

--changeset ninggiangboy:026-22-review-extraction-runs
-- One attempt to read aspects out of one exact revision, with every version that could change the
-- answer recorded on the row. Replaying the same revision through the same versions must produce the
-- same output, and only one successful run per that identity may exist.
CREATE TABLE review_extraction_runs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_revision_id          UUID NOT NULL,
    source_digest               CHAR(64) NOT NULL,
    aspect_taxonomy_version_id  UUID NOT NULL,

    extractor_version           VARCHAR(48) NOT NULL,
    model_version               VARCHAR(48),
    prompt_version              VARCHAR(48),
    provider_account_id         UUID,
    config_version              VARCHAR(48),
    input_disclosure_version    VARCHAR(32),

    state                       VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    attempt_number              INTEGER NOT NULL DEFAULT 1,
    fencing_token               BIGINT NOT NULL DEFAULT 0,
    claimed_at                  TIMESTAMPTZ,
    claimed_by                  VARCHAR(64),
    lease_expires_at            TIMESTAMPTZ,
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,

    output_digest               CHAR(64),
    validation_summary          VARCHAR(256),
    error_class                 VARCHAR(48),
    cost_micros                 BIGINT,
    latency_millis              INTEGER,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_extraction_runs_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT fk_review_extraction_runs_taxonomy FOREIGN KEY (aspect_taxonomy_version_id)
        REFERENCES aspect_taxonomy_versions (id),
    CONSTRAINT fk_review_extraction_runs_provider FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT ck_review_extraction_runs_state CHECK (
        state IN ('PLANNED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED')
    ),
    CONSTRAINT ck_review_extraction_runs_success CHECK (
        state <> 'SUCCEEDED' OR (output_digest IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_review_extraction_runs_failure CHECK (
        state NOT IN ('FAILED', 'REJECTED') OR error_class IS NOT NULL
    ),
    CONSTRAINT ck_review_extraction_runs_lease CHECK (
        (claimed_at IS NULL AND claimed_by IS NULL AND lease_expires_at IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by IS NOT NULL AND lease_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_review_extraction_runs_attempt CHECK (attempt_number > 0 AND fencing_token >= 0),
    CONSTRAINT ck_review_extraction_runs_cost CHECK (
        (cost_micros IS NULL OR cost_micros >= 0) AND (latency_millis IS NULL OR latency_millis >= 0)
    ),
    CONSTRAINT ck_review_extraction_runs_row_version CHECK (version >= 0)
);

-- One successful extraction per revision, taxonomy and extractor. A rerun that succeeds twice would
-- double every mention it produced.
CREATE UNIQUE INDEX uk_review_extraction_runs_success
    ON review_extraction_runs (review_revision_id, aspect_taxonomy_version_id, extractor_version)
    WHERE state = 'SUCCEEDED';
CREATE INDEX idx_review_extraction_runs_due ON review_extraction_runs (created_at)
    WHERE state IN ('PLANNED', 'FAILED');
CREATE INDEX idx_review_extraction_runs_leases ON review_extraction_runs (lease_expires_at)
    WHERE state = 'RUNNING';
--rollback DROP TABLE review_extraction_runs;

--changeset ninggiangboy:026-23-review-aspect-mentions
-- One thing a review said about one aspect, with the run that read it out and the confidence it was
-- read with. Attention is not sentiment: a mention records both what was said and how strongly, and a
-- single mention is never a finding on its own.
CREATE TABLE review_aspect_mentions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_extraction_run_id    UUID NOT NULL,
    review_record_id            UUID NOT NULL,
    review_revision_id          UUID NOT NULL,
    aspect_taxonomy_version_id  UUID NOT NULL,
    aspect_code                 VARCHAR(48) NOT NULL,
    aspect_target               VARCHAR(24) NOT NULL,
    source_type                 VARCHAR(24) NOT NULL,

    source_span_start           INTEGER,
    source_span_end             INTEGER,
    structured_category_code    VARCHAR(32),

    sentiment                   VARCHAR(16) NOT NULL,
    intensity                   NUMERIC(4, 3),
    negated                     BOOLEAN NOT NULL DEFAULT false,
    qualifier                   VARCHAR(32),
    confidence                  NUMERIC(4, 3) NOT NULL,

    source_language             VARCHAR(16),
    translation_id              UUID,
    human_validated             BOOLEAN NOT NULL DEFAULT false,
    validated_by_account_holder_id UUID,
    superseded_by_mention_id    UUID,
    inclusion_state             VARCHAR(16) NOT NULL DEFAULT 'QUALIFIED',

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_aspect_mentions_run FOREIGN KEY (review_extraction_run_id)
        REFERENCES review_extraction_runs (id),
    CONSTRAINT fk_review_aspect_mentions_record FOREIGN KEY (review_record_id)
        REFERENCES review_records (id),
    CONSTRAINT fk_review_aspect_mentions_revision FOREIGN KEY (review_revision_id)
        REFERENCES review_revisions (id),
    CONSTRAINT fk_review_aspect_mentions_taxonomy FOREIGN KEY (aspect_taxonomy_version_id)
        REFERENCES aspect_taxonomy_versions (id),
    CONSTRAINT fk_review_aspect_mentions_translation FOREIGN KEY (translation_id)
        REFERENCES review_translations (id),
    CONSTRAINT fk_review_aspect_mentions_validator FOREIGN KEY (validated_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_review_aspect_mentions_supersession FOREIGN KEY (superseded_by_mention_id)
        REFERENCES review_aspect_mentions (id),
    CONSTRAINT ck_review_aspect_mentions_target CHECK (
        aspect_target IN ('LISTING', 'HOST_SERVICE', 'GUEST_CONDUCT', 'PLATFORM', 'AREA')
    ),
    CONSTRAINT ck_review_aspect_mentions_source CHECK (
        source_type IN ('FREE_TEXT', 'CATEGORY_RATING', 'STRUCTURED_ANSWER', 'MEDIA_CAPTION')
    ),
    CONSTRAINT ck_review_aspect_mentions_sentiment CHECK (
        sentiment IN ('POSITIVE', 'NEGATIVE', 'MIXED', 'NEUTRAL', 'UNKNOWN')
    ),
    CONSTRAINT ck_review_aspect_mentions_inclusion CHECK (
        inclusion_state IN ('QUALIFIED', 'EXCLUDED', 'SUPERSEDED', 'PENDING_REVIEW')
    ),
    CONSTRAINT ck_review_aspect_mentions_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_review_aspect_mentions_intensity CHECK (
        intensity IS NULL OR (intensity >= 0 AND intensity <= 1)
    ),
    -- A span into free text must be a real span; a structured mention names the answer it came from.
    CONSTRAINT ck_review_aspect_mentions_span CHECK (
        (source_span_start IS NULL AND source_span_end IS NULL)
            OR (source_span_start >= 0 AND source_span_end > source_span_start)
    ),
    CONSTRAINT ck_review_aspect_mentions_structured CHECK (
        source_type <> 'CATEGORY_RATING' OR structured_category_code IS NOT NULL
    ),
    CONSTRAINT ck_review_aspect_mentions_validation CHECK (
        NOT human_validated OR validated_by_account_holder_id IS NOT NULL
    ),
    CONSTRAINT ck_review_aspect_mentions_self CHECK (superseded_by_mention_id <> id)
);

CREATE INDEX idx_review_aspect_mentions_revision ON review_aspect_mentions (review_revision_id);
CREATE INDEX idx_review_aspect_mentions_aspect
    ON review_aspect_mentions (aspect_taxonomy_version_id, aspect_code, aspect_target)
    WHERE inclusion_state = 'QUALIFIED';
CREATE INDEX idx_review_aspect_mentions_run ON review_aspect_mentions (review_extraction_run_id);
--rollback DROP TABLE review_aspect_mentions;

--changeset ninggiangboy:026-24-aspect-profile-versions
-- What a body of reviews says about one listing or host, aspect by aspect, computed under named
-- versions and carrying the manifest of what it read. A profile is replaced, never edited.
CREATE TABLE aspect_profile_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type                VARCHAR(16) NOT NULL,
    subject_id                  UUID NOT NULL,
    profile_version             INTEGER NOT NULL,

    aspect_taxonomy_version_id  UUID NOT NULL,
    aggregation_version         VARCHAR(48) NOT NULL,
    input_watermark             TIMESTAMPTZ,
    source_manifest_digest      CHAR(64),
    review_count                BIGINT NOT NULL DEFAULT 0,

    status                      VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
    computed_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    superseded_by_profile_id    UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_aspect_profile_versions_taxonomy FOREIGN KEY (aspect_taxonomy_version_id)
        REFERENCES aspect_taxonomy_versions (id),
    CONSTRAINT fk_aspect_profile_versions_supersession FOREIGN KEY (superseded_by_profile_id)
        REFERENCES aspect_profile_versions (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_aspect_profile_versions_identity
        UNIQUE (subject_type, subject_id, profile_version),
    CONSTRAINT ck_aspect_profile_versions_subject CHECK (
        subject_type IN ('LISTING', 'HOST', 'PROPERTY')
    ),
    CONSTRAINT ck_aspect_profile_versions_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED')
    ),
    CONSTRAINT ck_aspect_profile_versions_counts CHECK (review_count >= 0),
    CONSTRAINT ck_aspect_profile_versions_number CHECK (profile_version > 0),
    CONSTRAINT ck_aspect_profile_versions_self CHECK (superseded_by_profile_id <> id),
    CONSTRAINT ck_aspect_profile_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_aspect_profile_versions_current
    ON aspect_profile_versions (subject_type, subject_id)
    WHERE status = 'CURRENT';
CREATE INDEX idx_aspect_profile_versions_stale ON aspect_profile_versions (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE aspect_profile_versions;

--changeset ninggiangboy:026-25-aspect-profile-values
-- One aspect within one profile version. Counts are kept beside every posterior so a strength backed
-- by two mentions can never present itself the way one backed by two hundred does.
CREATE TABLE aspect_profile_values (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aspect_profile_version_id   UUID NOT NULL,
    aspect_code                 VARCHAR(48) NOT NULL,
    aspect_target               VARCHAR(24) NOT NULL,

    mention_count               BIGINT NOT NULL DEFAULT 0,
    positive_count              BIGINT NOT NULL DEFAULT 0,
    negative_count              BIGINT NOT NULL DEFAULT 0,
    mixed_count                 BIGINT NOT NULL DEFAULT 0,
    neutral_count               BIGINT NOT NULL DEFAULT 0,
    effective_evidence          NUMERIC(10, 4),

    posterior_mean              NUMERIC(6, 4),
    posterior_interval_low      NUMERIC(6, 4),
    posterior_interval_high     NUMERIC(6, 4),
    recent_value                NUMERIC(6, 4),
    trend_direction             VARCHAR(16),
    evidence_class              VARCHAR(16) NOT NULL DEFAULT 'INSUFFICIENT',

    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_aspect_profile_values_profile FOREIGN KEY (aspect_profile_version_id)
        REFERENCES aspect_profile_versions (id),
    CONSTRAINT uk_aspect_profile_values_aspect
        UNIQUE (aspect_profile_version_id, aspect_code, aspect_target),
    CONSTRAINT ck_aspect_profile_values_target CHECK (
        aspect_target IN ('LISTING', 'HOST_SERVICE', 'GUEST_CONDUCT', 'PLATFORM', 'AREA')
    ),
    CONSTRAINT ck_aspect_profile_values_evidence_class CHECK (
        evidence_class IN ('INSUFFICIENT', 'STRENGTH', 'WEAKNESS', 'MIXED', 'NEUTRAL')
    ),
    CONSTRAINT ck_aspect_profile_values_counts CHECK (
        mention_count >= 0 AND positive_count >= 0 AND negative_count >= 0
            AND mixed_count >= 0 AND neutral_count >= 0
    ),
    -- The sentiment breakdown is the mention count, broken down.
    CONSTRAINT ck_aspect_profile_values_breakdown CHECK (
        positive_count + negative_count + mixed_count + neutral_count = mention_count
    ),
    -- A strength or a weakness needs something behind it. With no mentions the answer is insufficient
    -- evidence, which is a different statement from neutral.
    CONSTRAINT ck_aspect_profile_values_claim CHECK (
        evidence_class = 'INSUFFICIENT' OR mention_count > 0
    ),
    CONSTRAINT ck_aspect_profile_values_interval CHECK (
        posterior_interval_low IS NULL OR posterior_interval_high IS NULL
            OR posterior_mean IS NULL
            OR (posterior_interval_low <= posterior_mean
                AND posterior_mean <= posterior_interval_high)
    ),
    CONSTRAINT ck_aspect_profile_values_trend CHECK (
        trend_direction IS NULL OR trend_direction IN ('IMPROVING', 'STABLE', 'DECLINING', 'UNKNOWN')
    )
);

CREATE INDEX idx_aspect_profile_values_profile ON aspect_profile_values (aspect_profile_version_id);
--rollback DROP TABLE aspect_profile_values;

--changeset ninggiangboy:026-26-reviewer-attention-profiles
-- What one reviewer repeatedly notices, derived from their own reviews for a named purpose. Attention
-- is not preference and not sentiment: that somebody always mentions noise says what they attend to,
-- not what they want. Carries an opt-out because it is personal data about a person.
CREATE TABLE reviewer_attention_profiles (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reviewer_account_holder_id  UUID NOT NULL,
    profile_version             INTEGER NOT NULL,
    purpose_code                VARCHAR(48) NOT NULL,

    aspect_taxonomy_version_id  UUID NOT NULL,
    source_window_from          TIMESTAMPTZ,
    source_window_until         TIMESTAMPTZ,
    distinct_stay_count         INTEGER NOT NULL DEFAULT 0,
    input_watermark             TIMESTAMPTZ,
    source_manifest_digest      CHAR(64),

    status                      VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
    opted_out                   BOOLEAN NOT NULL DEFAULT false,
    deletion_requested_at       TIMESTAMPTZ,
    computed_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    superseded_by_profile_id    UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reviewer_attention_profiles_reviewer FOREIGN KEY (reviewer_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_reviewer_attention_profiles_taxonomy FOREIGN KEY (aspect_taxonomy_version_id)
        REFERENCES aspect_taxonomy_versions (id),
    CONSTRAINT fk_reviewer_attention_profiles_supersession FOREIGN KEY (superseded_by_profile_id)
        REFERENCES reviewer_attention_profiles (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uk_reviewer_attention_profiles_identity
        UNIQUE (reviewer_account_holder_id, purpose_code, profile_version),
    CONSTRAINT ck_reviewer_attention_profiles_status CHECK (
        status IN ('CURRENT', 'SUPERSEDED', 'REBUILDING', 'FAILED', 'WITHDRAWN')
    ),
    -- An opted-out profile is not current. The flag and the status cannot disagree, because a
    -- consumer reading only one of them would still be handed the data.
    CONSTRAINT ck_reviewer_attention_profiles_opt_out CHECK (NOT opted_out OR status = 'WITHDRAWN'),
    CONSTRAINT ck_reviewer_attention_profiles_window CHECK (
        source_window_until IS NULL OR source_window_from IS NULL
            OR source_window_until > source_window_from
    ),
    CONSTRAINT ck_reviewer_attention_profiles_counts CHECK (distinct_stay_count >= 0),
    CONSTRAINT ck_reviewer_attention_profiles_number CHECK (profile_version > 0),
    CONSTRAINT ck_reviewer_attention_profiles_self CHECK (superseded_by_profile_id <> id),
    CONSTRAINT ck_reviewer_attention_profiles_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_reviewer_attention_profiles_current
    ON reviewer_attention_profiles (reviewer_account_holder_id, purpose_code)
    WHERE status = 'CURRENT';
CREATE INDEX idx_reviewer_attention_profiles_expiry ON reviewer_attention_profiles (expires_at)
    WHERE status = 'CURRENT';
--rollback DROP TABLE reviewer_attention_profiles;

--changeset ninggiangboy:026-27-reviewer-attention-values
-- One aspect within one attention profile. What is shared with discovery is this minimized view, not
-- the review text it was derived from.
CREATE TABLE reviewer_attention_values (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reviewer_attention_profile_id UUID NOT NULL,
    aspect_code                 VARCHAR(48) NOT NULL,
    attention_score             NUMERIC(6, 4) NOT NULL,
    sentiment_evidence          NUMERIC(6, 4),
    distinct_stay_count         INTEGER NOT NULL DEFAULT 0,
    mention_count               BIGINT NOT NULL DEFAULT 0,
    confidence                  NUMERIC(4, 3) NOT NULL,
    recency_weight              NUMERIC(4, 3),
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_reviewer_attention_values_profile FOREIGN KEY (reviewer_attention_profile_id)
        REFERENCES reviewer_attention_profiles (id),
    CONSTRAINT uk_reviewer_attention_values_aspect
        UNIQUE (reviewer_attention_profile_id, aspect_code),
    CONSTRAINT ck_reviewer_attention_values_score CHECK (
        attention_score >= 0 AND attention_score <= 1
    ),
    CONSTRAINT ck_reviewer_attention_values_confidence CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT ck_reviewer_attention_values_recency CHECK (
        recency_weight IS NULL OR (recency_weight >= 0 AND recency_weight <= 1)
    ),
    -- A preference signal from one stay is not a preference. The count travels with the score so a
    -- consumer cannot use the number without seeing how thin it is.
    CONSTRAINT ck_reviewer_attention_values_counts CHECK (
        distinct_stay_count >= 0 AND mention_count >= 0
            AND (attention_score = 0 OR mention_count > 0)
    )
);

CREATE INDEX idx_reviewer_attention_values_profile
    ON reviewer_attention_values (reviewer_attention_profile_id);
--rollback DROP TABLE reviewer_attention_values;

--changeset ninggiangboy:026-28-reputation-policy-versions
-- The approved purposes reputation may be computed for, and what each is allowed to read. This is the
-- table that stops a general-purpose score existing: nothing may be computed without a purpose named
-- here, and each purpose declares its inputs, its minimum evidence and its prohibited proxies.
CREATE TABLE reputation_policy_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose_code                VARCHAR(48) NOT NULL,
    policy_version              INTEGER NOT NULL,
    subject_role                VARCHAR(16) NOT NULL,
    actor_role                  VARCHAR(16) NOT NULL,

    allowed_input_families      VARCHAR(32)[] NOT NULL DEFAULT '{}',
    prohibited_attributes       VARCHAR(48)[] NOT NULL DEFAULT '{}',
    minimum_evidence_count      INTEGER NOT NULL,
    formula_version             VARCHAR(48) NOT NULL,
    confidence_floor            NUMERIC(4, 3),
    validity_days               SMALLINT NOT NULL,

    explanation_required        BOOLEAN NOT NULL DEFAULT true,
    appeal_required             BOOLEAN NOT NULL DEFAULT true,
    fairness_evidence_reference VARCHAR(256),
    consumer_allowlist          VARCHAR(48)[] NOT NULL DEFAULT '{}',

    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ,
    effective_until             TIMESTAMPTZ,
    owner_account_holder_id     UUID,
    approved_by_account_holder_id UUID,
    approved_at                 TIMESTAMPTZ,
    content_hash                CHAR(64) NOT NULL,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reputation_policy_versions_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_reputation_policy_versions_approver FOREIGN KEY (approved_by_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT uk_reputation_policy_versions_identity UNIQUE (purpose_code, policy_version),
    CONSTRAINT ck_reputation_policy_versions_subject CHECK (
        subject_role IN ('HOST', 'GUEST', 'LISTING', 'PROPERTY')
    ),
    CONSTRAINT ck_reputation_policy_versions_actor CHECK (
        actor_role IN ('HOST', 'GUEST', 'OPERATOR', 'SYSTEM')
    ),
    CONSTRAINT ck_reputation_policy_versions_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'RETIRED')
    ),
    CONSTRAINT ck_reputation_policy_versions_publication CHECK (
        status NOT IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED')
            OR (effective_from IS NOT NULL AND approved_at IS NOT NULL
                AND approved_by_account_holder_id IS NOT NULL
                AND fairness_evidence_reference IS NOT NULL)
    ),
    -- A published purpose says who may consume it and what it may read. An empty allowlist would make
    -- the purpose meaningless as a control.
    CONSTRAINT ck_reputation_policy_versions_consumers CHECK (
        status <> 'PUBLISHED'
            OR (cardinality(consumer_allowlist) > 0 AND cardinality(allowed_input_families) > 0)
    ),
    CONSTRAINT ck_reputation_policy_versions_evidence CHECK (minimum_evidence_count > 0),
    CONSTRAINT ck_reputation_policy_versions_validity CHECK (validity_days > 0),
    CONSTRAINT ck_reputation_policy_versions_confidence CHECK (
        confidence_floor IS NULL OR (confidence_floor >= 0 AND confidence_floor <= 1)
    ),
    CONSTRAINT ck_reputation_policy_versions_interval CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_reputation_policy_versions_number CHECK (policy_version > 0),
    CONSTRAINT ck_reputation_policy_versions_row_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_reputation_policy_versions_open
    ON reputation_policy_versions (purpose_code)
    WHERE status = 'PUBLISHED' AND effective_until IS NULL;
--rollback DROP TABLE reputation_policy_versions;

--changeset ninggiangboy:026-29-contextual-reputation-views
-- One answer to one approved question about one subject, in one context, with an expiry. Not a score
-- table: there is no row here that is not tied to a purpose, and a subject with too little behind it
-- gets INSUFFICIENT_EVIDENCE rather than a low number.
CREATE TABLE contextual_reputation_views (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_type                VARCHAR(16) NOT NULL,
    subject_id                  UUID NOT NULL,
    purpose_code                VARCHAR(48) NOT NULL,
    context_key                 VARCHAR(96) NOT NULL,

    reputation_policy_version_id UUID NOT NULL,
    input_versions_digest       CHAR(64) NOT NULL,

    output_category             VARCHAR(24) NOT NULL,
    output_value                NUMERIC(6, 4),
    confidence                  NUMERIC(4, 3),
    insufficient_evidence_reason VARCHAR(48),

    evidence_window_from        TIMESTAMPTZ,
    evidence_window_until       TIMESTAMPTZ,
    evidence_count              BIGINT NOT NULL DEFAULT 0,
    evidence_manifest_digest    CHAR(64),

    computed_at                 TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ NOT NULL,
    review_at                   TIMESTAMPTZ,
    superseded_by_view_id       UUID,
    superseded_at               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_contextual_reputation_views_policy FOREIGN KEY (reputation_policy_version_id)
        REFERENCES reputation_policy_versions (id),
    CONSTRAINT fk_contextual_reputation_views_supersession FOREIGN KEY (superseded_by_view_id)
        REFERENCES contextual_reputation_views (id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_contextual_reputation_views_subject CHECK (
        subject_type IN ('HOST', 'GUEST', 'LISTING', 'PROPERTY')
    ),
    CONSTRAINT ck_contextual_reputation_views_category CHECK (
        output_category IN ('INSUFFICIENT_EVIDENCE', 'BELOW_EXPECTATION', 'MEETS_EXPECTATION',
                            'ABOVE_EXPECTATION', 'NOT_APPLICABLE')
    ),
    -- Missing evidence is unknown, not bad. An insufficient answer carries a reason and no number;
    -- a real answer carries a number and no reason.
    CONSTRAINT ck_contextual_reputation_views_insufficient CHECK (
        (output_category = 'INSUFFICIENT_EVIDENCE')
            = (insufficient_evidence_reason IS NOT NULL AND output_value IS NULL)
    ),
    CONSTRAINT ck_contextual_reputation_views_confidence CHECK (
        confidence IS NULL OR (confidence >= 0 AND confidence <= 1)
    ),
    CONSTRAINT ck_contextual_reputation_views_evidence CHECK (evidence_count >= 0),
    CONSTRAINT ck_contextual_reputation_views_window CHECK (
        evidence_window_until IS NULL OR evidence_window_from IS NULL
            OR evidence_window_until > evidence_window_from
    ),
    CONSTRAINT ck_contextual_reputation_views_expiry CHECK (expires_at > computed_at),
    CONSTRAINT ck_contextual_reputation_views_supersession CHECK (
        (superseded_by_view_id IS NULL) = (superseded_at IS NULL)
    ),
    CONSTRAINT ck_contextual_reputation_views_self CHECK (superseded_by_view_id <> id),
    CONSTRAINT ck_contextual_reputation_views_row_version CHECK (version >= 0)
);

-- One live answer per subject, purpose and context. Two would let a consumer pick the one it liked.
CREATE UNIQUE INDEX uk_contextual_reputation_views_live
    ON contextual_reputation_views (subject_type, subject_id, purpose_code, context_key)
    WHERE superseded_at IS NULL;
CREATE INDEX idx_contextual_reputation_views_expiry ON contextual_reputation_views (expires_at)
    WHERE superseded_at IS NULL;
--rollback DROP TABLE contextual_reputation_views;

--changeset ninggiangboy:026-30-policy-immutability splitStatements:false
-- Three registries are pointed at by rows that must keep their meaning: every right names the review
-- policy it was opened under, every mention names the taxonomy it was read against, and every
-- reputation view names the purpose policy that authorized it. Editing any of them in place would
-- silently change what those stored rows mean, so a published version is frozen and a change is a new
-- version.
--
-- Aspect definitions are covered on INSERT as well. Adding an aspect to an active taxonomy changes
-- what that taxonomy is without touching a single existing row, which is the same defect migration 022
-- found when a balanced pair of postings could be appended to a posted transaction.
CREATE FUNCTION review_policy_versions_freeze_published() RETURNS TRIGGER AS $$
DECLARE
    candidate review_policy_versions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'review policy % version % is published and cannot be deleted',
            OLD.policy_key, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'review policy % version % is published; publish a new version instead of editing it',
            OLD.policy_key, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_policy_versions_freeze
    BEFORE UPDATE OR DELETE ON review_policy_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION review_policy_versions_freeze_published();

CREATE FUNCTION reputation_policy_versions_freeze_published() RETURNS TRIGGER AS $$
DECLARE
    candidate reputation_policy_versions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'reputation purpose % version % is published and cannot be deleted',
            OLD.purpose_code, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'reputation purpose % version % is published; publish a new version instead',
            OLD.purpose_code, OLD.policy_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reputation_policy_versions_freeze
    BEFORE UPDATE OR DELETE ON reputation_policy_versions
    FOR EACH ROW WHEN (OLD.status IN ('PUBLISHED', 'SUPERSEDED', 'RETIRED'))
    EXECUTE FUNCTION reputation_policy_versions_freeze_published();

CREATE FUNCTION aspect_taxonomy_versions_freeze_active() RETURNS TRIGGER AS $$
DECLARE
    candidate aspect_taxonomy_versions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'aspect taxonomy % version % is active and cannot be deleted',
            OLD.taxonomy_key, OLD.taxonomy_version USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.status := OLD.status;
    candidate.effective_until := OLD.effective_until;
    candidate.updated_at := OLD.updated_at;
    candidate.version := OLD.version;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'aspect taxonomy % version % is active; issue a new taxonomy version instead',
            OLD.taxonomy_key, OLD.taxonomy_version USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aspect_taxonomy_versions_freeze
    BEFORE UPDATE OR DELETE ON aspect_taxonomy_versions
    FOR EACH ROW WHEN (OLD.status IN ('ACTIVE', 'DEPRECATED', 'RETIRED'))
    EXECUTE FUNCTION aspect_taxonomy_versions_freeze_active();

CREATE FUNCTION aspect_definitions_guard_taxonomy() RETURNS TRIGGER AS $$
DECLARE
    taxonomy_status VARCHAR(16);
    subject_taxonomy UUID;
BEGIN
    subject_taxonomy := CASE WHEN TG_OP = 'DELETE'
                             THEN OLD.aspect_taxonomy_version_id
                             ELSE NEW.aspect_taxonomy_version_id END;

    SELECT status INTO taxonomy_status
      FROM aspect_taxonomy_versions WHERE id = subject_taxonomy;

    -- NOT FOUND is tested explicitly: the foreign key has not fired yet on an insert, and a NULL
    -- comparison would let the write through.
    IF NOT FOUND THEN
        RAISE EXCEPTION
            'aspect taxonomy version % does not exist', subject_taxonomy
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF taxonomy_status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'aspect taxonomy version % is %; its aspect set is fixed',
            subject_taxonomy, taxonomy_status USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aspect_definitions_guard_taxonomy
    BEFORE INSERT OR UPDATE OR DELETE ON aspect_definitions
    FOR EACH ROW EXECUTE FUNCTION aspect_definitions_guard_taxonomy();
--rollback DROP TRIGGER trg_aspect_definitions_guard_taxonomy ON aspect_definitions;
--rollback DROP FUNCTION aspect_definitions_guard_taxonomy();
--rollback DROP TRIGGER trg_aspect_taxonomy_versions_freeze ON aspect_taxonomy_versions;
--rollback DROP FUNCTION aspect_taxonomy_versions_freeze_active();
--rollback DROP TRIGGER trg_reputation_policy_versions_freeze ON reputation_policy_versions;
--rollback DROP FUNCTION reputation_policy_versions_freeze_published();
--rollback DROP TRIGGER trg_review_policy_versions_freeze ON review_policy_versions;
--rollback DROP FUNCTION review_policy_versions_freeze_published();

--changeset ninggiangboy:026-31-cycle-monotonic-disclosure splitStatements:false
-- A cycle's state only moves forward, and it reveals once. Both matter because the reveal is what the
-- other party can observe: a cycle that could move back to sealed, or issue a second reveal epoch,
-- would let a late moderation removal or a retry tell somebody whether their counterpart had written
-- anything.
CREATE FUNCTION review_cycles_guard_disclosure() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.state_rank < OLD.state_rank THEN
        RAISE EXCEPTION
            'review cycle % cannot move from % back to %',
            OLD.id, OLD.state, NEW.state USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.revealed_at IS NOT NULL
       AND (NEW.revealed_at IS DISTINCT FROM OLD.revealed_at
            OR NEW.reveal_version IS DISTINCT FROM OLD.reveal_version) THEN
        RAISE EXCEPTION
            'review cycle % already revealed at % under epoch %',
            OLD.id, OLD.revealed_at, OLD.reveal_version USING ERRCODE = 'restrict_violation';
    END IF;

    -- The deadline is snapshotted from the policy when the cycle opens. A job running late must
    -- preserve the original semantics rather than move the line it is measuring against.
    IF NEW.submission_deadline_at IS DISTINCT FROM OLD.submission_deadline_at
       OR NEW.opens_at IS DISTINCT FROM OLD.opens_at
       OR NEW.review_policy_version_id IS DISTINCT FROM OLD.review_policy_version_id THEN
        RAISE EXCEPTION
            'review cycle % cannot change the window or policy it was opened under',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.fencing_token < OLD.fencing_token THEN
        RAISE EXCEPTION
            'review cycle % cannot lower its fencing token from % to %',
            OLD.id, OLD.fencing_token, NEW.fencing_token USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_cycles_guard_disclosure
    BEFORE UPDATE ON review_cycles
    FOR EACH ROW EXECUTE FUNCTION review_cycles_guard_disclosure();
--rollback DROP TRIGGER trg_review_cycles_guard_disclosure ON review_cycles;
--rollback DROP FUNCTION review_cycles_guard_disclosure();

--changeset ninggiangboy:026-32-review-content-immutability splitStatements:false
-- Original meaning is immutable; corrections are additive. Revisions and response revisions are
-- insert-only, and the category ratings belonging to a submitted revision cannot be appended to --
-- adding a cleanliness score after the fact changes what the review said as surely as rewriting its
-- text would.
CREATE FUNCTION review_content_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'table % is insert-only; row % cannot be % (write a new revision instead)',
        TG_TABLE_NAME, OLD.id, lower(TG_OP) USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_revisions_insert_only
    BEFORE UPDATE OR DELETE ON review_revisions
    FOR EACH ROW EXECUTE FUNCTION review_content_reject_mutation();

CREATE TRIGGER trg_review_response_revisions_insert_only
    BEFORE UPDATE OR DELETE ON review_response_revisions
    FOR EACH ROW EXECUTE FUNCTION review_content_reject_mutation();

CREATE FUNCTION review_category_values_guard_revision() RETURNS TRIGGER AS $$
DECLARE
    submitted UUID;
    subject_revision UUID;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION
            'category rating % belongs to an immutable revision and cannot be %',
            OLD.id, lower(TG_OP) USING ERRCODE = 'restrict_violation';
    END IF;

    subject_revision := NEW.review_revision_id;

    SELECT r.submitted_revision_id INTO submitted
      FROM review_revisions rev
      JOIN review_records r ON r.id = rev.review_record_id
     WHERE rev.id = subject_revision;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'review revision % does not exist', subject_revision
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF submitted IS NOT DISTINCT FROM subject_revision THEN
        RAISE EXCEPTION
            'revision % is already the submitted revision; its category ratings are fixed',
            subject_revision USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_category_values_guard
    BEFORE INSERT OR UPDATE OR DELETE ON review_category_values
    FOR EACH ROW EXECUTE FUNCTION review_category_values_guard_revision();
--rollback DROP TRIGGER trg_review_category_values_guard ON review_category_values;
--rollback DROP FUNCTION review_category_values_guard_revision();
--rollback DROP TRIGGER trg_review_response_revisions_insert_only ON review_response_revisions;
--rollback DROP TRIGGER trg_review_revisions_insert_only ON review_revisions;
--rollback DROP FUNCTION review_content_reject_mutation();

--changeset ninggiangboy:026-33-publication-not-before-reveal splitStatements:false
-- The rule the whole double-blind design rests on. A publication row written before its cycle revealed
-- is not merely early: it tells the counterpart that the other side submitted, which is precisely what
-- sealing exists to hide. No column can express it, because the reveal lives on the cycle.
--
-- The epoch is checked too. A publication carrying a stale reveal version would mean two workers
-- issued separate reveals, which the cycle guard already refuses; this is the second lock on the
-- same door.
CREATE FUNCTION review_publications_validate_write() RETURNS TRIGGER AS $$
DECLARE
    cycle_state VARCHAR(24);
    cycle_revealed_at TIMESTAMPTZ;
    cycle_reveal_version INTEGER;
    revision_record UUID;
BEGIN
    SELECT state, revealed_at, reveal_version
      INTO cycle_state, cycle_revealed_at, cycle_reveal_version
      FROM review_cycles WHERE id = NEW.review_cycle_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'review cycle % does not exist', NEW.review_cycle_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF cycle_revealed_at IS NULL THEN
        RAISE EXCEPTION
            'review cycle % is %; nothing may publish before it reveals',
            NEW.review_cycle_id, cycle_state USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.published_from < cycle_revealed_at THEN
        RAISE EXCEPTION
            'publication would start at %, before cycle % revealed at %',
            NEW.published_from, NEW.review_cycle_id, cycle_revealed_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.reveal_version <> cycle_reveal_version THEN
        RAISE EXCEPTION
            'publication carries reveal epoch % but cycle % is at epoch %',
            NEW.reveal_version, NEW.review_cycle_id, cycle_reveal_version
            USING ERRCODE = 'restrict_violation';
    END IF;

    SELECT review_record_id INTO revision_record
      FROM review_revisions WHERE id = NEW.review_revision_id;

    IF NOT FOUND OR revision_record <> NEW.review_record_id THEN
        RAISE EXCEPTION
            'revision % does not belong to review %',
            NEW.review_revision_id, NEW.review_record_id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_publications_validate_write
    BEFORE INSERT ON review_publications
    FOR EACH ROW EXECUTE FUNCTION review_publications_validate_write();

-- A publication interval is history once written. Closing it is the only edit, and it happens once.
CREATE FUNCTION review_publications_freeze_interval() RETURNS TRIGGER AS $$
DECLARE
    candidate review_publications%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'publication % is history and cannot be deleted; close it instead',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.published_until := OLD.published_until;
    candidate.retraction_reason := OLD.retraction_reason;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'publication % is frozen; only its end and reason may be written',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.published_until IS NOT NULL
       AND NEW.published_until IS DISTINCT FROM OLD.published_until THEN
        RAISE EXCEPTION
            'publication % already ended at %; restoring opens a new interval',
            OLD.id, OLD.published_until USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_publications_freeze
    BEFORE UPDATE OR DELETE ON review_publications
    FOR EACH ROW EXECUTE FUNCTION review_publications_freeze_interval();
--rollback DROP TRIGGER trg_review_publications_freeze ON review_publications;
--rollback DROP FUNCTION review_publications_freeze_interval();
--rollback DROP TRIGGER trg_review_publications_validate_write ON review_publications;
--rollback DROP FUNCTION review_publications_validate_write();

--changeset ninggiangboy:026-34-post-reveal-edit-guard splitStatements:false
-- After reveal, substantive edits are not allowed. The anti-retaliation edit window exists so an
-- author can revise while still sealed; once both sides are visible, rewriting a review in response to
-- what the other party said is exactly what the rule forbids.
--
-- Typographical, privacy and legal corrections remain possible, which is why they are named kinds of
-- revision rather than exceptions granted to a service.
CREATE FUNCTION review_revisions_guard_window() RETURNS TRIGGER AS $$
DECLARE
    cycle_revealed_at TIMESTAMPTZ;
    record_authoring VARCHAR(24);
BEGIN
    SELECT c.revealed_at, r.authoring_state
      INTO cycle_revealed_at, record_authoring
      FROM review_records r
      JOIN review_cycles c ON c.id = r.review_cycle_id
     WHERE r.id = NEW.review_record_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'review % does not exist', NEW.review_record_id USING ERRCODE = 'restrict_violation';
    END IF;

    IF cycle_revealed_at IS NOT NULL AND NEW.revision_kind = 'AUTHOR_SUBMISSION' THEN
        RAISE EXCEPTION
            'review % was revealed at %; a substantive edit is no longer allowed',
            NEW.review_record_id, cycle_revealed_at USING ERRCODE = 'restrict_violation';
    END IF;

    IF record_authoring = 'AUTHOR_WITHDRAWN' AND NEW.revision_kind = 'AUTHOR_SUBMISSION' THEN
        RAISE EXCEPTION
            'review % was withdrawn by its author and takes no further submissions',
            NEW.review_record_id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_revisions_guard_window
    BEFORE INSERT ON review_revisions
    FOR EACH ROW EXECUTE FUNCTION review_revisions_guard_window();
--rollback DROP TRIGGER trg_review_revisions_guard_window ON review_revisions;
--rollback DROP FUNCTION review_revisions_guard_window();

--changeset ninggiangboy:026-35-derived-evidence-immutability splitStatements:false
-- Everything derived from a review carries provenance, and provenance that can be edited is not
-- provenance. Interaction events, applied moderation decisions and aggregate contributions are
-- append-only outright; mentions and private feedback are frozen except for the few fields that
-- legitimately move afterwards -- inclusion, human validation, retention and hold.
CREATE FUNCTION review_derived_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'table % is append-only; row % cannot be % (record a correcting row instead)',
        TG_TABLE_NAME, OLD.id, lower(TG_OP) USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_interaction_events_append_only
    BEFORE UPDATE OR DELETE ON review_interaction_events
    FOR EACH ROW EXECUTE FUNCTION review_derived_reject_mutation();

CREATE TRIGGER trg_review_moderation_applications_append_only
    BEFORE UPDATE OR DELETE ON review_moderation_applications
    FOR EACH ROW EXECUTE FUNCTION review_derived_reject_mutation();

CREATE FUNCTION review_aggregate_contributions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate review_aggregate_contributions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'contribution % is audit evidence; close it instead of deleting it',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.included_until := OLD.included_until;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'contribution % is frozen; only the end of its inclusion may be written',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_aggregate_contributions_freeze
    BEFORE UPDATE OR DELETE ON review_aggregate_contributions
    FOR EACH ROW EXECUTE FUNCTION review_aggregate_contributions_freeze();

CREATE FUNCTION review_aspect_mentions_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate review_aspect_mentions%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'aspect mention % is extraction evidence and cannot be deleted; exclude it instead',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    candidate := NEW;
    candidate.inclusion_state := OLD.inclusion_state;
    candidate.human_validated := OLD.human_validated;
    candidate.validated_by_account_holder_id := OLD.validated_by_account_holder_id;
    candidate.superseded_by_mention_id := OLD.superseded_by_mention_id;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'aspect mention % is frozen; what the extractor read cannot be rewritten',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_aspect_mentions_freeze
    BEFORE UPDATE OR DELETE ON review_aspect_mentions
    FOR EACH ROW EXECUTE FUNCTION review_aspect_mentions_freeze();

CREATE FUNCTION review_private_feedback_freeze() RETURNS TRIGGER AS $$
DECLARE
    candidate review_private_feedback%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.legal_hold THEN
            RAISE EXCEPTION
                'private feedback % is under legal hold and cannot be deleted',
                OLD.id USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN OLD;
    END IF;

    candidate := NEW;
    candidate.retention_class := OLD.retention_class;
    candidate.legal_hold := OLD.legal_hold;

    IF candidate IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION
            'private feedback % is frozen; only retention and hold may change',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_private_feedback_freeze
    BEFORE UPDATE OR DELETE ON review_private_feedback
    FOR EACH ROW EXECUTE FUNCTION review_private_feedback_freeze();
--rollback DROP TRIGGER trg_review_private_feedback_freeze ON review_private_feedback;
--rollback DROP FUNCTION review_private_feedback_freeze();
--rollback DROP TRIGGER trg_review_aspect_mentions_freeze ON review_aspect_mentions;
--rollback DROP FUNCTION review_aspect_mentions_freeze();
--rollback DROP TRIGGER trg_review_aggregate_contributions_freeze ON review_aggregate_contributions;
--rollback DROP FUNCTION review_aggregate_contributions_freeze();
--rollback DROP TRIGGER trg_review_moderation_applications_append_only ON review_moderation_applications;
--rollback DROP TRIGGER trg_review_interaction_events_append_only ON review_interaction_events;
--rollback DROP FUNCTION review_derived_reject_mutation();

--changeset ninggiangboy:026-36-aggregate-epoch-monotonic splitStatements:false
-- The publication epoch is how a consumer knows whether the number it cached is older than the one in
-- front of it. A rebuild that lowered it would make a stale aggregate look fresh, so it only ever
-- moves forward. The counts themselves may fall: a removed review legitimately reduces them.
CREATE FUNCTION review_public_aggregates_guard_epoch() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.publication_epoch < OLD.publication_epoch THEN
        RAISE EXCEPTION
            'aggregate % cannot move its publication epoch from % back to %',
            OLD.id, OLD.publication_epoch, NEW.publication_epoch
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- The key identifies what the row is about. Repointing it would silently move a body of ratings
    -- onto a different listing or host.
    IF (NEW.subject_type, NEW.subject_id, NEW.direction, NEW.dimension_code,
        NEW.aggregate_rule_version)
       IS DISTINCT FROM
       (OLD.subject_type, OLD.subject_id, OLD.direction, OLD.dimension_code,
        OLD.aggregate_rule_version) THEN
        RAISE EXCEPTION
            'aggregate % cannot change the subject or rule version it counts for',
            OLD.id USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_public_aggregates_guard_epoch
    BEFORE UPDATE ON review_public_aggregates
    FOR EACH ROW EXECUTE FUNCTION review_public_aggregates_guard_epoch();
--rollback DROP TRIGGER trg_review_public_aggregates_guard_epoch ON review_public_aggregates;
--rollback DROP FUNCTION review_public_aggregates_guard_epoch();
