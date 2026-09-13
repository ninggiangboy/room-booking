--liquibase formatted sql

-- An administrative action is a domain command with a name on it, never a database edit. That is
-- the whole of this migration. Everything an operator can do to the running marketplace -- widen
-- somebody's authority, change a configured value, pull a kill switch, cancel a booking, refund a
-- payment, export a table -- becomes a row that names who asked, under what authority, against
-- which version of which rule, who else agreed, and what actually happened. The alternative is the
-- failure the domain document describes in one line: direct database edits create invisible policy
-- and destroy auditability. Invisible policy is not merely undocumented; it is policy nobody can
-- roll back, because nothing records what it replaced.
--
-- Admin does not get its own copy of the marketplace. Nothing here writes a booking, a payment, a
-- payout or a listing. An operational command row is a request and a recorded outcome; the domain
-- that owns the row still applies every invariant it has. A command that would have been refused
-- when a guest issued it is refused when an operator issues it, and the refusal is the outcome
-- this table stores. There is deliberately no column anywhere in this migration into which an
-- operator can type a booking state, a ledger amount or a payout total.
--
-- Seven forces shape it:
--
--   Authority is defined in one place and enforced in another. A role definition says what an
--   operator role means -- which permissions, which blast radius, how long a grant may last, what
--   training it needs, which other roles it may not be held alongside. The grant that actually
--   opens the door is migration 014's capability_grants, unchanged. Keeping the definition apart
--   from the grant is what makes it possible to ask what a role meant last March, after the role
--   has been narrowed twice since.
--
--   Separation of duties is a constraint, not a habit. A role version names the roles it conflicts
--   with, and an assignment that would give one operator both sides of a maker-checker pair is
--   refused at insert. Every approval in this migration also refuses an approver equal to the
--   requester -- on access, on configuration, on money commands, on bulk exports. Written down and
--   not enforced, separation of duties survives exactly until the week somebody is on leave.
--
--   A configured value has a schema, a scope, a priority, an effective interval, an owner and a
--   version. That sentence is the domain document's hard rule and it is six columns here, plus an
--   exclusion constraint so two versions of one setting cannot be in force at the same instant.
--   Resolution order is data, so the question "why did this request see that value" has an answer
--   that does not depend on reading the resolver's source code.
--
--   Maker-checker is a state machine with evidence, not a checkbox. A change request carries the
--   proposed value, the validation and simulation results it passed, the approvals its impact
--   class requires, and the rollout that applied it. It cannot reach APPROVED without them and it
--   cannot be applied without being approved. A rollback is a new rollout back to a named earlier
--   version -- history is never rewritten, because the incident review needs to see the value that
--   was live during the incident.
--
--   A kill switch is asymmetric on purpose. Turning one off never needs an approval: the whole
--   point of a kill switch is that the person holding it at three in the morning does not have to
--   find a second approver. Turning one back on always does. A flag also carries an expiry, and
--   past it the only permitted moves are off and retired -- a permanent temporary flag is how a
--   rollout mechanism quietly becomes undocumented product behaviour.
--
--   Emergency access is bounded and reviewed, or it is just access. A break-glass grant names the
--   incident, is approved by somebody other than the requester, expires by an interval the role
--   itself caps, records every target it touched, and owes a post-use review by somebody who
--   neither requested nor approved it. An unreviewed expired grant is the audit finding.
--
--   Reading production in bulk is an action, and it is recorded like one. An export names its
--   purpose, its legal basis, the data classes it will contain and the rows it expects, is
--   approved by somebody else, expires, and logs every retrieval against it. Minimisation is a
--   column -- the requested classes -- rather than an intention.
--
-- Note on what this migration does not create. The effective-dated country, tax, fee, cancellation
-- and risk policies the domain document lists are migrations 013, 019, 023 and 027; they are cited
-- here by a governed-artifact reference on a change request, not redefined, so that publishing a
-- policy version and changing a configured value go through one maker-checker machine instead of
-- two. The moderation and review queues are 027 and 028. The immutable admin audit is migration
-- 012's audit_events, which already carries actor, reason code, before and after digests, and
-- retention -- this migration writes into it rather than duplicating it. The trigger functions
-- platform_append_only() and platform_contract_freeze() come from 030 and are reused here.
-- Inspection of bookings, payments, payouts and reconciliation is read access over tables that
-- already exist; what this migration adds for it is the export request and the access log.
--
-- Every table here is written by the application, so none of them carry DEFAULT now(): see
-- migration 011.

--changeset ninggiangboy:032-01-operator-role-definitions
CREATE TABLE operator_role_definitions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_key                    VARCHAR(64) NOT NULL,
    role_version                INTEGER NOT NULL,
    display_name                VARCHAR(200) NOT NULL,
    purpose                     VARCHAR(500) NOT NULL,
    authority_class             VARCHAR(24) NOT NULL,
    market_scope                VARCHAR(16) NOT NULL DEFAULT 'MARKET',
    data_sensitivity            VARCHAR(16) NOT NULL,
    maximum_grant_days          INTEGER NOT NULL,
    recertification_days        INTEGER NOT NULL,
    requires_training           BOOLEAN NOT NULL DEFAULT false,
    training_reference          VARCHAR(200),
    assignment_approval_role    VARCHAR(64) NOT NULL,
    break_glass_eligible        BOOLEAN NOT NULL DEFAULT false,
    break_glass_maximum_minutes INTEGER,
    break_glass_review_hours    INTEGER,
    owner_reference             VARCHAR(64) NOT NULL,
    supersedes_id               UUID,
    status                      VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    activated_at                TIMESTAMPTZ,
    deprecated_at               TIMESTAMPTZ,
    retired_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_operator_role_definitions_version UNIQUE (role_key, role_version),
    CONSTRAINT fk_operator_role_definitions_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES operator_role_definitions (id),
    CONSTRAINT ck_operator_role_definitions_key CHECK (role_key ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_operator_role_definitions_version_number CHECK (role_version > 0),
    -- The blast radius of the role, which is what decides how many approvals an assignment needs
    -- and whether the role may ever be held under break-glass.
    CONSTRAINT ck_operator_role_definitions_authority CHECK (
        authority_class IN (
            'READ_ONLY', 'SUPPORT', 'OPERATIONAL', 'FINANCIAL',
            'IDENTITY', 'SAFETY', 'PLATFORM_ADMIN'
        )
    ),
    CONSTRAINT ck_operator_role_definitions_market_scope CHECK (
        market_scope IN ('GLOBAL', 'MARKET')
    ),
    CONSTRAINT ck_operator_role_definitions_sensitivity CHECK (
        data_sensitivity IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    -- An operator role that never expires is a permanent standing authority nobody re-examines.
    -- Both bounds are required and both are finite.
    CONSTRAINT ck_operator_role_definitions_grant_days CHECK (
        maximum_grant_days > 0 AND maximum_grant_days <= 366
    ),
    CONSTRAINT ck_operator_role_definitions_recertification CHECK (
        recertification_days > 0 AND recertification_days <= maximum_grant_days
    ),
    CONSTRAINT ck_operator_role_definitions_training CHECK (
        NOT requires_training OR training_reference IS NOT NULL
    ),
    -- Emergency access without a cap on its length and a deadline for reviewing it is ordinary
    -- access granted in a hurry, so the role must state both before anybody can break the glass.
    CONSTRAINT ck_operator_role_definitions_break_glass CHECK (
        break_glass_eligible
            = (break_glass_maximum_minutes IS NOT NULL AND break_glass_review_hours IS NOT NULL)
    ),
    CONSTRAINT ck_operator_role_definitions_break_glass_bounds CHECK (
        break_glass_maximum_minutes IS NULL
            OR (break_glass_maximum_minutes > 0 AND break_glass_maximum_minutes <= 1440)
    ),
    CONSTRAINT ck_operator_role_definitions_break_glass_review CHECK (
        break_glass_review_hours IS NULL
            OR (break_glass_review_hours > 0 AND break_glass_review_hours <= 168)
    ),
    CONSTRAINT ck_operator_role_definitions_read_only_break_glass CHECK (
        NOT break_glass_eligible OR authority_class <> 'READ_ONLY'
    ),
    CONSTRAINT ck_operator_role_definitions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_operator_role_definitions_activation CHECK (
        (status = 'DRAFT') = (activated_at IS NULL)
    ),
    CONSTRAINT ck_operator_role_definitions_deprecation CHECK (
        status <> 'DEPRECATED' OR deprecated_at IS NOT NULL
    ),
    CONSTRAINT ck_operator_role_definitions_retirement CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_operator_role_definitions_version_column CHECK (version >= 0)
);

CREATE INDEX idx_operator_role_definitions_key ON operator_role_definitions (role_key, status);
CREATE INDEX idx_operator_role_definitions_authority
    ON operator_role_definitions (authority_class) WHERE status = 'ACTIVE';
--rollback DROP TABLE operator_role_definitions;

--changeset ninggiangboy:032-02-operator-role-permissions
CREATE TABLE operator_role_permissions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_definition_id       UUID NOT NULL,
    permission_key           VARCHAR(96) NOT NULL,
    permission_kind          VARCHAR(16) NOT NULL,
    owning_domain            VARCHAR(48) NOT NULL,
    scope_type               VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    data_sensitivity         VARCHAR(16) NOT NULL,
    requires_second_approval BOOLEAN NOT NULL DEFAULT false,
    requires_reason_code     BOOLEAN NOT NULL DEFAULT true,
    justification            VARCHAR(500) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_operator_role_permissions_member
        UNIQUE (role_definition_id, permission_key, scope_type),
    CONSTRAINT fk_operator_role_permissions_role FOREIGN KEY (role_definition_id)
        REFERENCES operator_role_definitions (id) ON DELETE CASCADE,
    CONSTRAINT ck_operator_role_permissions_key CHECK (permission_key ~ '^[a-z][a-z0-9_.]*$'),
    -- Reading, changing state and moving money are three different things to be able to grant, and
    -- an EXPORT permission is called out separately because bulk reading is its own risk.
    CONSTRAINT ck_operator_role_permissions_kind CHECK (
        permission_kind IN ('READ', 'WRITE', 'COMMAND', 'EXPORT', 'ADMINISTER')
    ),
    CONSTRAINT ck_operator_role_permissions_scope CHECK (
        scope_type IN ('GLOBAL', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'BOOKING', 'MARKET')
    ),
    CONSTRAINT ck_operator_role_permissions_sensitivity CHECK (
        data_sensitivity IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    -- A read permission is satisfied by one operator; nothing is changed by it and a second
    -- approver has nothing to approve.
    CONSTRAINT ck_operator_role_permissions_second_approval CHECK (
        NOT requires_second_approval OR permission_kind <> 'READ'
    ),
    CONSTRAINT ck_operator_role_permissions_justification CHECK (
        length(btrim(justification)) > 0
    )
);

CREATE INDEX idx_operator_role_permissions_permission
    ON operator_role_permissions (permission_key);
--rollback DROP TABLE operator_role_permissions;

--changeset ninggiangboy:032-03-operator-role-conflicts
CREATE TABLE operator_role_conflicts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lower_role_key          VARCHAR(64) NOT NULL,
    higher_role_key         VARCHAR(64) NOT NULL,
    conflict_basis          VARCHAR(24) NOT NULL,
    rationale               VARCHAR(500) NOT NULL,
    exception_allowed       BOOLEAN NOT NULL DEFAULT false,
    exception_approval_role VARCHAR(64),
    declared_by             UUID NOT NULL,
    declared_at             TIMESTAMPTZ NOT NULL,
    withdrawn_at            TIMESTAMPTZ,
    withdrawal_reason       VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_operator_role_conflicts_pair UNIQUE (lower_role_key, higher_role_key),
    CONSTRAINT fk_operator_role_conflicts_declared_by FOREIGN KEY (declared_by)
        REFERENCES account_holders (id),
    -- The pair is stored in one order so that the conflict between A and B is one row rather than
    -- two rows that can disagree about whether the conflict still stands.
    CONSTRAINT ck_operator_role_conflicts_order CHECK (lower_role_key < higher_role_key),
    CONSTRAINT ck_operator_role_conflicts_basis CHECK (
        conflict_basis IN ('MAKER_CHECKER', 'FINANCIAL_CONTROL', 'PRIVACY', 'SAFETY', 'REGULATORY')
    ),
    CONSTRAINT ck_operator_role_conflicts_rationale CHECK (length(btrim(rationale)) > 0),
    CONSTRAINT ck_operator_role_conflicts_exception CHECK (
        exception_allowed = (exception_approval_role IS NOT NULL)
    ),
    CONSTRAINT ck_operator_role_conflicts_withdrawal CHECK (
        (withdrawn_at IS NULL) = (withdrawal_reason IS NULL)
    ),
    CONSTRAINT ck_operator_role_conflicts_version CHECK (version >= 0)
);

CREATE INDEX idx_operator_role_conflicts_lower ON operator_role_conflicts (lower_role_key)
    WHERE withdrawn_at IS NULL;
CREATE INDEX idx_operator_role_conflicts_higher ON operator_role_conflicts (higher_role_key)
    WHERE withdrawn_at IS NULL;
--rollback DROP TABLE operator_role_conflicts;

--changeset ninggiangboy:032-04-operator-role-assignments
CREATE TABLE operator_role_assignments (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_definition_id     UUID NOT NULL,
    role_key               VARCHAR(64) NOT NULL,
    operator_id            UUID NOT NULL,
    market_code            VARCHAR(2),
    justification          VARCHAR(500) NOT NULL,
    requested_by           UUID NOT NULL,
    requested_at           TIMESTAMPTZ NOT NULL,
    approved_by            UUID NOT NULL,
    approved_at            TIMESTAMPTZ NOT NULL,
    training_attestation   VARCHAR(200),
    conflict_exception_id  UUID,
    capability_grant_id    UUID,
    effective_from         TIMESTAMPTZ NOT NULL,
    effective_until        TIMESTAMPTZ NOT NULL,
    recertification_due_at TIMESTAMPTZ NOT NULL,
    recertified_at         TIMESTAMPTZ,
    recertified_by         UUID,
    assignment_state       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    revoked_at             TIMESTAMPTZ,
    revoked_by             UUID,
    revocation_reason      VARCHAR(64),
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_operator_role_assignments_role FOREIGN KEY (role_definition_id)
        REFERENCES operator_role_definitions (id),
    CONSTRAINT fk_operator_role_assignments_operator FOREIGN KEY (operator_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operator_role_assignments_requested_by FOREIGN KEY (requested_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operator_role_assignments_approved_by FOREIGN KEY (approved_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operator_role_assignments_recertified_by FOREIGN KEY (recertified_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operator_role_assignments_revoked_by FOREIGN KEY (revoked_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operator_role_assignments_conflict FOREIGN KEY (conflict_exception_id)
        REFERENCES operator_role_conflicts (id),
    CONSTRAINT fk_operator_role_assignments_grant FOREIGN KEY (capability_grant_id)
        REFERENCES capability_grants (id),
    CONSTRAINT fk_operator_role_assignments_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    -- Nobody grants themselves operational authority, and nobody approves their own request. This
    -- is the cheapest separation of duties in the migration and the one most often skipped.
    CONSTRAINT ck_operator_role_assignments_self_approval CHECK (
        approved_by <> operator_id AND approved_by <> requested_by
    ),
    CONSTRAINT ck_operator_role_assignments_justification CHECK (
        length(btrim(justification)) > 0
    ),
    CONSTRAINT ck_operator_role_assignments_span CHECK (effective_until > effective_from),
    CONSTRAINT ck_operator_role_assignments_recertification CHECK (
        recertification_due_at > effective_from AND recertification_due_at <= effective_until
    ),
    CONSTRAINT ck_operator_role_assignments_recertified CHECK (
        (recertified_at IS NULL) = (recertified_by IS NULL)
    ),
    CONSTRAINT ck_operator_role_assignments_recertifier CHECK (
        recertified_by IS NULL OR recertified_by <> operator_id
    ),
    CONSTRAINT ck_operator_role_assignments_state CHECK (
        assignment_state IN ('ACTIVE', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_operator_role_assignments_revocation CHECK (
        (assignment_state = 'REVOKED')
            = (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revocation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_operator_role_assignments_version CHECK (version >= 0)
);

-- One live assignment of a role to an operator per market. A second one would make the expiry of
-- the first meaningless, because the authority would simply continue under the other row.
CREATE UNIQUE INDEX uk_operator_role_assignments_live
    ON operator_role_assignments (operator_id, role_key, market_code) NULLS NOT DISTINCT
    WHERE assignment_state = 'ACTIVE';
CREATE INDEX idx_operator_role_assignments_operator
    ON operator_role_assignments (operator_id, assignment_state);
CREATE INDEX idx_operator_role_assignments_due
    ON operator_role_assignments (recertification_due_at) WHERE assignment_state = 'ACTIVE';
--rollback DROP TABLE operator_role_assignments;

--changeset ninggiangboy:032-05-break-glass-grants
CREATE TABLE break_glass_grants (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_definition_id  UUID NOT NULL,
    operator_id         UUID NOT NULL,
    market_code         VARCHAR(2),
    trigger_kind        VARCHAR(24) NOT NULL,
    incident_reference  VARCHAR(128) NOT NULL,
    justification       VARCHAR(1000) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL,
    approved_by         UUID NOT NULL,
    approved_at         TIMESTAMPTZ NOT NULL,
    approval_channel    VARCHAR(24) NOT NULL,
    granted_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    alert_dispatched_at TIMESTAMPTZ NOT NULL,
    capability_grant_id UUID,
    closed_at           TIMESTAMPTZ,
    closure_reason      VARCHAR(24),
    activity_count      INTEGER NOT NULL DEFAULT 0,
    review_due_at       TIMESTAMPTZ NOT NULL,
    review_state        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by         UUID,
    reviewed_at         TIMESTAMPTZ,
    review_finding      VARCHAR(24),
    review_notes        VARCHAR(2000),
    follow_up_reference VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_break_glass_grants_role FOREIGN KEY (role_definition_id)
        REFERENCES operator_role_definitions (id),
    CONSTRAINT fk_break_glass_grants_operator FOREIGN KEY (operator_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_break_glass_grants_approved_by FOREIGN KEY (approved_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_break_glass_grants_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_break_glass_grants_grant FOREIGN KEY (capability_grant_id)
        REFERENCES capability_grants (id),
    CONSTRAINT fk_break_glass_grants_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_break_glass_grants_trigger CHECK (
        trigger_kind IN (
            'INCIDENT', 'OUTAGE', 'SAFETY', 'REGULATORY_ORDER', 'FRAUD_CONTAINMENT', 'DATA_RECOVERY'
        )
    ),
    CONSTRAINT ck_break_glass_grants_incident CHECK (length(btrim(incident_reference)) > 0),
    CONSTRAINT ck_break_glass_grants_justification CHECK (length(btrim(justification)) > 0),
    -- Approval out of band is legitimate at three in the morning; approval by the person asking is
    -- not, and a break-glass grant is precisely the moment that rule stops being convenient.
    CONSTRAINT ck_break_glass_grants_approver CHECK (approved_by <> operator_id),
    CONSTRAINT ck_break_glass_grants_channel CHECK (
        approval_channel IN ('CONSOLE', 'ON_CALL_PAGE', 'VOICE', 'WRITTEN_ORDER')
    ),
    CONSTRAINT ck_break_glass_grants_order CHECK (
        approved_at >= requested_at AND granted_at >= approved_at
    ),
    CONSTRAINT ck_break_glass_grants_expiry CHECK (expires_at > granted_at),
    -- The alert is part of the grant, not a downstream nicety. An emergency elevation nobody was
    -- told about is indistinguishable from a quiet one.
    CONSTRAINT ck_break_glass_grants_alert CHECK (alert_dispatched_at >= granted_at),
    CONSTRAINT ck_break_glass_grants_closure CHECK (
        (closed_at IS NULL) = (closure_reason IS NULL)
    ),
    CONSTRAINT ck_break_glass_grants_closure_reason CHECK (
        closure_reason IS NULL OR closure_reason IN ('EXPIRED', 'SURRENDERED', 'REVOKED')
    ),
    CONSTRAINT ck_break_glass_grants_closure_time CHECK (
        closed_at IS NULL OR closed_at >= granted_at
    ),
    CONSTRAINT ck_break_glass_grants_activity_count CHECK (activity_count >= 0),
    CONSTRAINT ck_break_glass_grants_review_due CHECK (review_due_at > expires_at),
    CONSTRAINT ck_break_glass_grants_review_state CHECK (
        review_state IN ('PENDING', 'IN_REVIEW', 'REVIEWED', 'ESCALATED')
    ),
    -- A completed review names a reviewer, an instant and a finding. Marking one reviewed without
    -- saying what was found is how an unreviewed grant leaves the queue.
    CONSTRAINT ck_break_glass_grants_reviewed CHECK (
        (review_state IN ('REVIEWED', 'ESCALATED'))
            = (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_finding IS NOT NULL)
    ),
    CONSTRAINT ck_break_glass_grants_finding CHECK (
        review_finding IS NULL
            OR review_finding IN ('APPROPRIATE', 'EXCESSIVE_SCOPE', 'UNJUSTIFIED', 'POLICY_BREACH')
    ),
    -- The reviewer is a third person: not the operator who used the access and not whoever let
    -- them have it.
    CONSTRAINT ck_break_glass_grants_reviewer CHECK (
        reviewed_by IS NULL OR (reviewed_by <> operator_id AND reviewed_by <> approved_by)
    ),
    CONSTRAINT ck_break_glass_grants_escalation CHECK (
        review_finding IS NULL
            OR review_finding = 'APPROPRIATE'
            OR follow_up_reference IS NOT NULL
    ),
    CONSTRAINT ck_break_glass_grants_version CHECK (version >= 0)
);

CREATE INDEX idx_break_glass_grants_operator ON break_glass_grants (operator_id, granted_at);
CREATE INDEX idx_break_glass_grants_incident ON break_glass_grants (incident_reference);
CREATE INDEX idx_break_glass_grants_open_review ON break_glass_grants (review_due_at)
    WHERE review_state IN ('PENDING', 'IN_REVIEW');
--rollback DROP TABLE break_glass_grants;

--changeset ninggiangboy:032-06-break-glass-activities
CREATE TABLE break_glass_activities (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    break_glass_grant_id UUID NOT NULL,
    sequence_number      INTEGER NOT NULL,
    occurred_at          TIMESTAMPTZ NOT NULL,
    permission_key       VARCHAR(96) NOT NULL,
    owning_domain        VARCHAR(48) NOT NULL,
    target_type          VARCHAR(48) NOT NULL,
    target_id            UUID,
    target_reference     VARCHAR(128),
    operation            VARCHAR(16) NOT NULL,
    row_count            INTEGER NOT NULL DEFAULT 1,
    data_sensitivity     VARCHAR(16) NOT NULL,
    audit_event_id       UUID,
    request_id           VARCHAR(64),
    created_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_break_glass_activities_sequence
        UNIQUE (break_glass_grant_id, sequence_number),
    CONSTRAINT fk_break_glass_activities_grant FOREIGN KEY (break_glass_grant_id)
        REFERENCES break_glass_grants (id) ON DELETE CASCADE,
    CONSTRAINT fk_break_glass_activities_audit FOREIGN KEY (audit_event_id)
        REFERENCES audit_events (id),
    CONSTRAINT ck_break_glass_activities_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_break_glass_activities_operation CHECK (
        operation IN ('READ', 'WRITE', 'COMMAND', 'EXPORT')
    ),
    -- A target is identified by its key or by a reference for things that have no row, and never
    -- by neither: an activity nobody can resolve is not evidence of anything.
    CONSTRAINT ck_break_glass_activities_target CHECK (
        target_id IS NOT NULL OR target_reference IS NOT NULL
    ),
    CONSTRAINT ck_break_glass_activities_row_count CHECK (row_count >= 0),
    CONSTRAINT ck_break_glass_activities_sensitivity CHECK (
        data_sensitivity IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    )
);

CREATE INDEX idx_break_glass_activities_occurred
    ON break_glass_activities (occurred_at, owning_domain);
--rollback DROP TABLE break_glass_activities;

--changeset ninggiangboy:032-07-configuration-schemas
CREATE TABLE configuration_schemas (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schema_key              VARCHAR(96) NOT NULL,
    schema_version          INTEGER NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    owning_domain           VARCHAR(48) NOT NULL,
    value_shape             VARCHAR(24) NOT NULL,
    value_schema_reference  VARCHAR(200) NOT NULL,
    value_schema_digest     CHAR(64) NOT NULL,
    allowed_scope_types     TEXT[] NOT NULL,
    default_value           JSONB NOT NULL,
    impact_class            VARCHAR(24) NOT NULL,
    blast_radius            VARCHAR(16) NOT NULL,
    requires_maker_checker  BOOLEAN NOT NULL DEFAULT true,
    required_approval_roles TEXT[] NOT NULL,
    requires_simulation     BOOLEAN NOT NULL DEFAULT false,
    requires_preview        BOOLEAN NOT NULL DEFAULT false,
    rollback_supported      BOOLEAN NOT NULL DEFAULT true,
    rollback_note           VARCHAR(500),
    change_freeze_applies   BOOLEAN NOT NULL DEFAULT true,
    owner_reference         VARCHAR(64) NOT NULL,
    documentation_reference VARCHAR(200) NOT NULL,
    supersedes_id           UUID,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    activated_at            TIMESTAMPTZ,
    deprecated_at           TIMESTAMPTZ,
    retired_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_configuration_schemas_version UNIQUE (schema_key, schema_version),
    CONSTRAINT fk_configuration_schemas_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES configuration_schemas (id),
    CONSTRAINT ck_configuration_schemas_key CHECK (schema_key ~ '^[a-z][a-z0-9_.]*$'),
    CONSTRAINT ck_configuration_schemas_version_number CHECK (schema_version > 0),
    CONSTRAINT ck_configuration_schemas_shape CHECK (
        value_shape IN (
            'BOOLEAN', 'INTEGER', 'DECIMAL', 'STRING', 'MONEY',
            'DURATION', 'PERCENTAGE', 'OBJECT', 'LIST'
        )
    ),
    CONSTRAINT ck_configuration_schemas_digest CHECK (value_schema_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_configuration_schemas_scopes CHECK (
        array_length(allowed_scope_types, 1) IS NOT NULL
            AND allowed_scope_types <@ ARRAY[
                'GLOBAL', 'MARKET', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'SEGMENT'
            ]::TEXT[]
    ),
    CONSTRAINT ck_configuration_schemas_default CHECK (
        jsonb_typeof(default_value) IS NOT NULL
    ),
    CONSTRAINT ck_configuration_schemas_impact CHECK (
        impact_class IN (
            'COSMETIC', 'OPERATIONAL', 'PRICING', 'FINANCIAL',
            'IDENTITY', 'SAFETY', 'REGULATORY'
        )
    ),
    CONSTRAINT ck_configuration_schemas_blast_radius CHECK (
        blast_radius IN ('SCOPED', 'MARKET', 'GLOBAL')
    ),
    -- A value that reaches money, identity, safety or a regulator is never changed by one person,
    -- whatever the setting's own flag says. The flag may tighten the rule and may not loosen it.
    CONSTRAINT ck_configuration_schemas_maker_checker CHECK (
        requires_maker_checker
            OR impact_class IN ('COSMETIC', 'OPERATIONAL')
    ),
    CONSTRAINT ck_configuration_schemas_approval_roles CHECK (
        requires_maker_checker = (array_length(required_approval_roles, 1) IS NOT NULL)
    ),
    -- A setting that cannot be rolled back has to say why, because that is the sentence somebody
    -- needs during the incident that the change caused.
    CONSTRAINT ck_configuration_schemas_rollback CHECK (
        rollback_supported OR rollback_note IS NOT NULL
    ),
    CONSTRAINT ck_configuration_schemas_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_configuration_schemas_activation CHECK (
        (status = 'DRAFT') = (activated_at IS NULL)
    ),
    CONSTRAINT ck_configuration_schemas_deprecation CHECK (
        status <> 'DEPRECATED' OR deprecated_at IS NOT NULL
    ),
    CONSTRAINT ck_configuration_schemas_retirement CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_configuration_schemas_version_column CHECK (version >= 0)
);

CREATE INDEX idx_configuration_schemas_key ON configuration_schemas (schema_key, status);
CREATE INDEX idx_configuration_schemas_domain ON configuration_schemas (owning_domain)
    WHERE status = 'ACTIVE';
--rollback DROP TABLE configuration_schemas;

--changeset ninggiangboy:032-08-configuration-settings
-- The domain document's hard rule is that configuration has a schema, a scope, a priority, an
-- effective interval, an owner and a version. Four of those six live here, because they belong to
-- the addressable setting rather than to any one value it has held; the effective interval and the
-- version belong to the value and live in the next table. Splitting them is what lets the resolver
-- answer "which setting won" and the auditor answer "what was it set to in March" separately.
CREATE TABLE configuration_settings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    configuration_schema_id UUID NOT NULL,
    schema_key              VARCHAR(96) NOT NULL,
    scope_type              VARCHAR(16) NOT NULL,
    scope_id                UUID,
    market_code             VARCHAR(2),
    resolution_priority     INTEGER NOT NULL,
    owner_reference         VARCHAR(64) NOT NULL,
    description             VARCHAR(500) NOT NULL,
    setting_state           VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    retired_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_configuration_settings_scope
        UNIQUE NULLS NOT DISTINCT (configuration_schema_id, scope_type, scope_id, market_code),
    CONSTRAINT fk_configuration_settings_schema FOREIGN KEY (configuration_schema_id)
        REFERENCES configuration_schemas (id),
    CONSTRAINT fk_configuration_settings_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_configuration_settings_scope CHECK (
        scope_type IN ('GLOBAL', 'MARKET', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'SEGMENT')
    ),
    CONSTRAINT ck_configuration_settings_scope_id CHECK (
        (scope_type IN ('GLOBAL', 'MARKET')) = (scope_id IS NULL)
    ),
    CONSTRAINT ck_configuration_settings_market CHECK (
        (scope_type = 'MARKET') = (market_code IS NOT NULL)
    ),
    -- A global setting is the floor everything else overrides, so it is the lowest priority by
    -- construction rather than by whoever entered the number.
    CONSTRAINT ck_configuration_settings_priority CHECK (
        resolution_priority >= 0
            AND (scope_type <> 'GLOBAL' OR resolution_priority = 0)
            AND (scope_type = 'GLOBAL' OR resolution_priority > 0)
    ),
    CONSTRAINT ck_configuration_settings_description CHECK (length(btrim(description)) > 0),
    CONSTRAINT ck_configuration_settings_state CHECK (
        setting_state IN ('ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_configuration_settings_retirement CHECK (
        (setting_state = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_configuration_settings_version CHECK (version >= 0)
);

CREATE INDEX idx_configuration_settings_resolution
    ON configuration_settings (schema_key, resolution_priority DESC)
    WHERE setting_state = 'ACTIVE';
--rollback DROP TABLE configuration_settings;

--changeset ninggiangboy:032-09-configuration-versions
CREATE TABLE configuration_versions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    configuration_setting_id UUID NOT NULL,
    version_number           INTEGER NOT NULL,
    value                    JSONB NOT NULL,
    value_digest             CHAR(64) NOT NULL,
    effective_from           TIMESTAMPTZ NOT NULL,
    effective_until          TIMESTAMPTZ,
    change_request_id        UUID,
    origin                   VARCHAR(24) NOT NULL,
    supersedes_version_id    UUID,
    rollback_of_version_id   UUID,
    applied_by               UUID NOT NULL,
    applied_at               TIMESTAMPTZ NOT NULL,
    superseded_by            UUID,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_configuration_versions_number
        UNIQUE (configuration_setting_id, version_number),
    CONSTRAINT fk_configuration_versions_setting FOREIGN KEY (configuration_setting_id)
        REFERENCES configuration_settings (id),
    CONSTRAINT fk_configuration_versions_supersedes FOREIGN KEY (supersedes_version_id)
        REFERENCES configuration_versions (id),
    CONSTRAINT fk_configuration_versions_rollback FOREIGN KEY (rollback_of_version_id)
        REFERENCES configuration_versions (id),
    CONSTRAINT fk_configuration_versions_superseded_by FOREIGN KEY (superseded_by)
        REFERENCES configuration_versions (id),
    CONSTRAINT fk_configuration_versions_applied_by FOREIGN KEY (applied_by)
        REFERENCES account_holders (id),
    CONSTRAINT ck_configuration_versions_number_positive CHECK (version_number > 0),
    CONSTRAINT ck_configuration_versions_digest CHECK (value_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_configuration_versions_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_configuration_versions_origin CHECK (
        origin IN ('BOOTSTRAP', 'CHANGE_REQUEST', 'ROLLBACK', 'KILL_SWITCH', 'MIGRATION')
    ),
    -- Only the bootstrap that seeds a setting and a migration that moves it may exist without a
    -- request behind them; everything an operator did has a request to point at.
    CONSTRAINT ck_configuration_versions_request CHECK (
        origin IN ('BOOTSTRAP', 'MIGRATION') OR change_request_id IS NOT NULL
    ),
    CONSTRAINT ck_configuration_versions_rollback_target CHECK (
        (origin = 'ROLLBACK') = (rollback_of_version_id IS NOT NULL)
    ),
    -- The first version of a setting has nothing before it; every later one names what it replaced,
    -- so the chain can be walked backwards without reconstructing it from timestamps.
    CONSTRAINT ck_configuration_versions_chain CHECK (
        (version_number = 1) = (supersedes_version_id IS NULL)
    ),
    CONSTRAINT ck_configuration_versions_self_chain CHECK (
        supersedes_version_id IS DISTINCT FROM id
            AND rollback_of_version_id IS DISTINCT FROM id
            AND superseded_by IS DISTINCT FROM id
    ),
    CONSTRAINT ck_configuration_versions_applied_order CHECK (applied_at <= effective_from),
    CONSTRAINT ck_configuration_versions_version_column CHECK (version >= 0),
    -- One value of one setting is in force at any instant. Two overlapping versions would mean two
    -- requests a second apart could legitimately see different configuration and both be right.
    CONSTRAINT ex_configuration_versions_no_overlap EXCLUDE USING gist (
        configuration_setting_id WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);

CREATE INDEX idx_configuration_versions_effective
    ON configuration_versions (configuration_setting_id, effective_from DESC);
CREATE INDEX idx_configuration_versions_open
    ON configuration_versions (configuration_setting_id) WHERE effective_until IS NULL;
--rollback DROP TABLE configuration_versions;

--changeset ninggiangboy:032-10-change-requests
-- One maker-checker machine for everything an operator can change. A configured value, a feature
-- flag and a governed artifact published by another domain -- a market policy bundle from 013, a
-- tax rule version from 019, a cancellation policy from 023, a risk policy from 027 -- all travel
-- the same path: proposed, validated, approved by somebody else, applied through a rollout. Those
-- artifacts are not redefined here; the request names one by domain, type and reference, so that
-- the approval trail for publishing a policy and for changing a timeout look the same to whoever
-- has to audit them.
CREATE TABLE change_requests (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_kind                VARCHAR(24) NOT NULL,
    configuration_setting_id   UUID,
    feature_flag_definition_id UUID,
    artifact_domain            VARCHAR(48),
    artifact_type              VARCHAR(48),
    artifact_reference         VARCHAR(200),
    title                      VARCHAR(200) NOT NULL,
    intent                     VARCHAR(2000) NOT NULL,
    proposed_value             JSONB,
    current_value_digest       CHAR(64),
    impact_class               VARCHAR(24) NOT NULL,
    blast_radius               VARCHAR(16) NOT NULL,
    affected_markets           TEXT[],
    required_approval_roles    TEXT[] NOT NULL,
    rollback_plan              VARCHAR(1000) NOT NULL,
    expedited                  BOOLEAN NOT NULL DEFAULT false,
    expedited_justification    VARCHAR(500),
    expedited_review_due_at    TIMESTAMPTZ,
    requested_by               UUID NOT NULL,
    requested_at               TIMESTAMPTZ NOT NULL,
    request_state              VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    validated_at               TIMESTAMPTZ,
    approved_at                TIMESTAMPTZ,
    applied_at                 TIMESTAMPTZ,
    rejected_at                TIMESTAMPTZ,
    rejection_reason           VARCHAR(64),
    withdrawn_at               TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_change_requests_setting FOREIGN KEY (configuration_setting_id)
        REFERENCES configuration_settings (id),
    CONSTRAINT fk_change_requests_requested_by FOREIGN KEY (requested_by)
        REFERENCES account_holders (id),
    CONSTRAINT ck_change_requests_target_kind CHECK (
        target_kind IN ('CONFIGURATION', 'FEATURE_FLAG', 'GOVERNED_ARTIFACT')
    ),
    -- Exactly one target, and the columns that identify it are required together. A request that
    -- names two things cannot be approved, because the approvers would be agreeing to different
    -- changes.
    CONSTRAINT ck_change_requests_target CHECK (
        num_nonnulls(configuration_setting_id, feature_flag_definition_id, artifact_reference) = 1
    ),
    CONSTRAINT ck_change_requests_configuration_target CHECK (
        (target_kind = 'CONFIGURATION') = (configuration_setting_id IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_flag_target CHECK (
        (target_kind = 'FEATURE_FLAG') = (feature_flag_definition_id IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_artifact_target CHECK (
        (target_kind = 'GOVERNED_ARTIFACT')
            = (artifact_domain IS NOT NULL AND artifact_type IS NOT NULL
                AND artifact_reference IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_intent CHECK (length(btrim(intent)) > 0),
    -- A configuration or flag change proposes a value. An artifact publication does not: the value
    -- is the artifact, it lives in the domain that owns it, and a copy of it here would be a
    -- second version of the thing being approved that nothing keeps in step with the first.
    CONSTRAINT ck_change_requests_proposed_value CHECK (
        (target_kind = 'GOVERNED_ARTIFACT') = (proposed_value IS NULL)
    ),
    CONSTRAINT ck_change_requests_current_digest CHECK (
        current_value_digest IS NULL OR current_value_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_change_requests_impact CHECK (
        impact_class IN (
            'COSMETIC', 'OPERATIONAL', 'PRICING', 'FINANCIAL',
            'IDENTITY', 'SAFETY', 'REGULATORY'
        )
    ),
    CONSTRAINT ck_change_requests_blast_radius CHECK (
        blast_radius IN ('SCOPED', 'MARKET', 'GLOBAL')
    ),
    CONSTRAINT ck_change_requests_markets CHECK (
        affected_markets IS NULL OR array_length(affected_markets, 1) IS NOT NULL
    ),
    CONSTRAINT ck_change_requests_approval_roles CHECK (
        array_length(required_approval_roles, 1) IS NOT NULL
    ),
    -- Every request carries the sentence that says how to undo it. "We would roll forward" is an
    -- acceptable answer; having never been asked is not.
    CONSTRAINT ck_change_requests_rollback_plan CHECK (length(btrim(rollback_plan)) > 0),
    -- An expedited change skips waiting, never reviewing. It owes a justification at the time and
    -- a review afterwards, both recorded before it can move.
    CONSTRAINT ck_change_requests_expedited CHECK (
        expedited
            = (expedited_justification IS NOT NULL AND expedited_review_due_at IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_state CHECK (
        request_state IN (
            'DRAFT', 'VALIDATED', 'AWAITING_APPROVAL', 'APPROVED',
            'APPLIED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED'
        )
    ),
    CONSTRAINT ck_change_requests_validated CHECK (
        (request_state IN ('DRAFT', 'WITHDRAWN')) OR validated_at IS NOT NULL
    ),
    CONSTRAINT ck_change_requests_approved CHECK (
        (request_state IN ('APPROVED', 'APPLIED')) = (approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_applied CHECK (
        (request_state = 'APPLIED') = (applied_at IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_rejected CHECK (
        (request_state = 'REJECTED')
            = (rejected_at IS NOT NULL AND rejection_reason IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_withdrawn CHECK (
        (request_state = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)
    ),
    CONSTRAINT ck_change_requests_order CHECK (
        (validated_at IS NULL OR validated_at >= requested_at)
            AND (approved_at IS NULL OR approved_at >= validated_at)
            AND (applied_at IS NULL OR applied_at >= approved_at)
    ),
    CONSTRAINT ck_change_requests_version CHECK (version >= 0)
);

CREATE INDEX idx_change_requests_state ON change_requests (request_state, requested_at);
CREATE INDEX idx_change_requests_setting ON change_requests (configuration_setting_id)
    WHERE configuration_setting_id IS NOT NULL;
CREATE INDEX idx_change_requests_artifact ON change_requests (artifact_domain, artifact_reference)
    WHERE artifact_reference IS NOT NULL;
CREATE INDEX idx_change_requests_expedited_review ON change_requests (expedited_review_due_at)
    WHERE expedited;

ALTER TABLE configuration_versions ADD CONSTRAINT fk_configuration_versions_request
    FOREIGN KEY (change_request_id) REFERENCES change_requests (id);
--rollback ALTER TABLE configuration_versions DROP CONSTRAINT fk_configuration_versions_request;
--rollback DROP TABLE change_requests;

--changeset ninggiangboy:032-11-change-request-approvals
CREATE TABLE change_request_approvals (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_request_id UUID NOT NULL,
    approval_role     VARCHAR(64) NOT NULL,
    decision          VARCHAR(16) NOT NULL,
    approver_id       UUID NOT NULL,
    decided_at        TIMESTAMPTZ NOT NULL,
    approved_digest   CHAR(64) NOT NULL,
    comment           VARCHAR(2000),
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_change_request_approvals_role UNIQUE (change_request_id, approval_role),
    CONSTRAINT fk_change_request_approvals_request FOREIGN KEY (change_request_id)
        REFERENCES change_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_change_request_approvals_approver FOREIGN KEY (approver_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_change_request_approvals_decision CHECK (
        decision IN ('APPROVED', 'REJECTED')
    ),
    -- The approval is of a particular proposed value. Recording the digest is what stops a request
    -- being edited after approval and applied as something nobody agreed to.
    CONSTRAINT ck_change_request_approvals_digest CHECK (approved_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_change_request_approvals_rejection_comment CHECK (
        decision <> 'REJECTED' OR comment IS NOT NULL
    )
);

CREATE INDEX idx_change_request_approvals_approver
    ON change_request_approvals (approver_id, decided_at);
--rollback DROP TABLE change_request_approvals;

--changeset ninggiangboy:032-12-change-request-validations
CREATE TABLE change_request_validations (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_request_id  UUID NOT NULL,
    validation_kind    VARCHAR(24) NOT NULL,
    attempt_number     INTEGER NOT NULL DEFAULT 1,
    outcome            VARCHAR(16) NOT NULL,
    performed_at       TIMESTAMPTZ NOT NULL,
    performed_by       VARCHAR(64) NOT NULL,
    evidence_reference VARCHAR(200),
    finding            VARCHAR(2000),
    validated_digest   CHAR(64) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_change_request_validations_attempt
        UNIQUE (change_request_id, validation_kind, attempt_number),
    CONSTRAINT fk_change_request_validations_request FOREIGN KEY (change_request_id)
        REFERENCES change_requests (id) ON DELETE CASCADE,
    -- Schema checks the shape, policy checks whether it is allowed, simulation runs it against
    -- recorded traffic, preview renders what a user would see, and conflict looks for another
    -- request moving the same thing. They are separate because they fail for separate reasons.
    CONSTRAINT ck_change_request_validations_kind CHECK (
        validation_kind IN ('SCHEMA', 'POLICY', 'SIMULATION', 'PREVIEW', 'DRY_RUN', 'CONFLICT')
    ),
    CONSTRAINT ck_change_request_validations_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_change_request_validations_outcome CHECK (
        outcome IN ('PASS', 'WARN', 'FAIL', 'SKIPPED')
    ),
    -- A skipped or failed check has to say what happened; a passing one may stay silent.
    CONSTRAINT ck_change_request_validations_finding CHECK (
        outcome = 'PASS' OR finding IS NOT NULL
    ),
    CONSTRAINT ck_change_request_validations_evidence CHECK (
        validation_kind NOT IN ('SIMULATION', 'PREVIEW', 'DRY_RUN')
            OR outcome = 'SKIPPED'
            OR evidence_reference IS NOT NULL
    ),
    CONSTRAINT ck_change_request_validations_digest CHECK (validated_digest ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_change_request_validations_request
    ON change_request_validations (change_request_id, performed_at);
--rollback DROP TABLE change_request_validations;

--changeset ninggiangboy:032-13-configuration-rollouts
-- Applying an approved change is its own record, because a change that reached ten per cent of one
-- market and was stopped is not the same event as one that reached everybody. A rollback is a
-- rollout with a named earlier target, never an edit to the rollout that went wrong: the incident
-- review has to be able to see both what was live during the incident and what replaced it.
CREATE TABLE configuration_rollouts (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    change_request_id          UUID NOT NULL,
    stage                      VARCHAR(16) NOT NULL,
    stage_sequence             INTEGER NOT NULL,
    target_share               NUMERIC(5,4),
    target_markets             TEXT[],
    started_at                 TIMESTAMPTZ NOT NULL,
    started_by                 UUID NOT NULL,
    completed_at               TIMESTAMPTZ,
    rollout_state              VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    resulting_version_id       UUID,
    health_check_reference     VARCHAR(200),
    observation_window_minutes INTEGER,
    aborted_at                 TIMESTAMPTZ,
    abort_reason               VARCHAR(64),
    rollback_of_rollout_id     UUID,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_configuration_rollouts_sequence UNIQUE (change_request_id, stage_sequence),
    CONSTRAINT fk_configuration_rollouts_request FOREIGN KEY (change_request_id)
        REFERENCES change_requests (id),
    CONSTRAINT fk_configuration_rollouts_started_by FOREIGN KEY (started_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_configuration_rollouts_version FOREIGN KEY (resulting_version_id)
        REFERENCES configuration_versions (id),
    CONSTRAINT fk_configuration_rollouts_rollback FOREIGN KEY (rollback_of_rollout_id)
        REFERENCES configuration_rollouts (id),
    CONSTRAINT ck_configuration_rollouts_stage CHECK (
        stage IN ('CANARY', 'PARTIAL', 'FULL', 'ROLLBACK')
    ),
    CONSTRAINT ck_configuration_rollouts_sequence_number CHECK (stage_sequence > 0),
    CONSTRAINT ck_configuration_rollouts_share CHECK (
        (stage IN ('CANARY', 'PARTIAL')) = (target_share IS NOT NULL)
    ),
    CONSTRAINT ck_configuration_rollouts_share_bounds CHECK (
        target_share IS NULL OR (target_share > 0 AND target_share < 1)
    ),
    CONSTRAINT ck_configuration_rollouts_state CHECK (
        rollout_state IN ('RUNNING', 'COMPLETED', 'ABORTED', 'ROLLED_BACK')
    ),
    CONSTRAINT ck_configuration_rollouts_completed CHECK (
        (rollout_state IN ('COMPLETED', 'ROLLED_BACK')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_configuration_rollouts_completion_order CHECK (
        completed_at IS NULL OR completed_at >= started_at
    ),
    CONSTRAINT ck_configuration_rollouts_aborted CHECK (
        (rollout_state = 'ABORTED') = (aborted_at IS NOT NULL AND abort_reason IS NOT NULL)
    ),
    -- A staged rollout that made a change live has a version to point at. An aborted one may not:
    -- nothing became effective, and inventing a version for it would put a value in the history
    -- that was never served.
    CONSTRAINT ck_configuration_rollouts_resulting_version CHECK (
        rollout_state <> 'ABORTED' OR resulting_version_id IS NULL
    ),
    CONSTRAINT ck_configuration_rollouts_rollback_target CHECK (
        (stage = 'ROLLBACK') = (rollback_of_rollout_id IS NOT NULL)
    ),
    CONSTRAINT ck_configuration_rollouts_self CHECK (
        rollback_of_rollout_id IS DISTINCT FROM id
    ),
    -- A canary nobody watched is a full rollout with extra steps, so the window and the check it
    -- was judged by are required of the staged ones.
    CONSTRAINT ck_configuration_rollouts_observation CHECK (
        stage NOT IN ('CANARY', 'PARTIAL')
            OR (observation_window_minutes IS NOT NULL AND observation_window_minutes > 0
                AND health_check_reference IS NOT NULL)
    ),
    CONSTRAINT ck_configuration_rollouts_version CHECK (version >= 0)
);

CREATE INDEX idx_configuration_rollouts_running ON configuration_rollouts (started_at)
    WHERE rollout_state = 'RUNNING';
--rollback DROP TABLE configuration_rollouts;

--changeset ninggiangboy:032-14-feature-flag-definitions
CREATE TABLE feature_flag_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key                VARCHAR(96) NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    flag_kind               VARCHAR(24) NOT NULL,
    owning_domain           VARCHAR(48) NOT NULL,
    owner_reference         VARCHAR(64) NOT NULL,
    purpose                 VARCHAR(500) NOT NULL,
    default_enabled         BOOLEAN NOT NULL DEFAULT false,
    allowed_scope_types     TEXT[] NOT NULL,
    targeting_supported     BOOLEAN NOT NULL DEFAULT false,
    disables_capability     VARCHAR(96),
    enable_approval_roles   TEXT[] NOT NULL,
    expires_on              DATE NOT NULL,
    extension_count         INTEGER NOT NULL DEFAULT 0,
    extension_reason        VARCHAR(500),
    documentation_reference VARCHAR(200) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    activated_at            TIMESTAMPTZ,
    deprecated_at           TIMESTAMPTZ,
    retired_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_feature_flag_definitions_key UNIQUE (flag_key),
    CONSTRAINT ck_feature_flag_definitions_key CHECK (flag_key ~ '^[a-z][a-z0-9_.]*$'),
    -- A release flag is temporary scaffolding, an operational flag is a dial, a kill switch is a
    -- brake and a permission flag gates access. They are separated because the rules about who may
    -- move them, and in which direction, are different for each.
    CONSTRAINT ck_feature_flag_definitions_kind CHECK (
        flag_kind IN ('RELEASE', 'OPERATIONAL', 'EXPERIMENT', 'PERMISSION', 'KILL_SWITCH')
    ),
    CONSTRAINT ck_feature_flag_definitions_scopes CHECK (
        array_length(allowed_scope_types, 1) IS NOT NULL
            AND allowed_scope_types <@ ARRAY[
                'GLOBAL', 'MARKET', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'SEGMENT'
            ]::TEXT[]
    ),
    -- A kill switch exists to turn something off, so it must name what it turns off. A switch
    -- whose effect nobody wrote down is one nobody will dare pull during the incident.
    CONSTRAINT ck_feature_flag_definitions_kill_switch CHECK (
        (flag_kind = 'KILL_SWITCH') = (disables_capability IS NOT NULL)
    ),
    -- A kill switch is off by default and a pull always works; putting one back on is the change
    -- that needs approval, so it always has roles to name.
    CONSTRAINT ck_feature_flag_definitions_kill_switch_default CHECK (
        flag_kind <> 'KILL_SWITCH' OR NOT default_enabled
    ),
    CONSTRAINT ck_feature_flag_definitions_enable_roles CHECK (
        array_length(enable_approval_roles, 1) IS NOT NULL
    ),
    CONSTRAINT ck_feature_flag_definitions_targeting CHECK (
        NOT targeting_supported OR flag_kind <> 'KILL_SWITCH'
    ),
    -- Every flag has a date by which it is gone. Extending one is allowed and is recorded as an
    -- extension with a reason, because a flag on its fourth extension is product behaviour that
    -- never went through a product decision.
    CONSTRAINT ck_feature_flag_definitions_extension CHECK (
        extension_count >= 0 AND (extension_count = 0) = (extension_reason IS NULL)
    ),
    CONSTRAINT ck_feature_flag_definitions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_feature_flag_definitions_activation CHECK (
        (status = 'DRAFT') = (activated_at IS NULL)
    ),
    CONSTRAINT ck_feature_flag_definitions_deprecation CHECK (
        status <> 'DEPRECATED' OR deprecated_at IS NOT NULL
    ),
    CONSTRAINT ck_feature_flag_definitions_retirement CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_feature_flag_definitions_version CHECK (version >= 0)
);

CREATE INDEX idx_feature_flag_definitions_expiry ON feature_flag_definitions (expires_on)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_feature_flag_definitions_domain ON feature_flag_definitions (owning_domain);

ALTER TABLE change_requests ADD CONSTRAINT fk_change_requests_flag
    FOREIGN KEY (feature_flag_definition_id) REFERENCES feature_flag_definitions (id);
--rollback ALTER TABLE change_requests DROP CONSTRAINT fk_change_requests_flag;
--rollback DROP TABLE feature_flag_definitions;

--changeset ninggiangboy:032-15-feature-flag-states
-- The asymmetry is the point. Turning a flag off never needs a second person: the whole value of a
-- kill switch is that whoever is holding the pager at three in the morning can pull it without
-- finding anybody. Turning one back on always does, because that is the decision that puts the
-- risk back, and it is made by somebody who has had time to think.
CREATE TABLE feature_flag_states (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feature_flag_definition_id UUID NOT NULL,
    scope_type                 VARCHAR(16) NOT NULL,
    scope_id                   UUID,
    market_code                VARCHAR(2),
    enabled                    BOOLEAN NOT NULL,
    rollout_share              NUMERIC(5,4),
    targeting_rule             JSONB,
    targeting_digest           CHAR(64),
    effective_from             TIMESTAMPTZ NOT NULL,
    effective_until            TIMESTAMPTZ,
    origin                     VARCHAR(24) NOT NULL,
    change_request_id          UUID,
    actor_id                   UUID NOT NULL,
    reason_code                VARCHAR(64) NOT NULL,
    incident_reference         VARCHAR(128),
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL,
    version                    BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_feature_flag_states_definition FOREIGN KEY (feature_flag_definition_id)
        REFERENCES feature_flag_definitions (id),
    CONSTRAINT fk_feature_flag_states_request FOREIGN KEY (change_request_id)
        REFERENCES change_requests (id),
    CONSTRAINT fk_feature_flag_states_actor FOREIGN KEY (actor_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_feature_flag_states_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_feature_flag_states_scope CHECK (
        scope_type IN ('GLOBAL', 'MARKET', 'ORGANIZATION', 'PROPERTY', 'LISTING', 'SEGMENT')
    ),
    CONSTRAINT ck_feature_flag_states_scope_id CHECK (
        (scope_type IN ('GLOBAL', 'MARKET')) = (scope_id IS NULL)
    ),
    CONSTRAINT ck_feature_flag_states_market CHECK (
        (scope_type = 'MARKET') = (market_code IS NOT NULL)
    ),
    CONSTRAINT ck_feature_flag_states_share CHECK (
        rollout_share IS NULL OR (rollout_share > 0 AND rollout_share <= 1)
    ),
    CONSTRAINT ck_feature_flag_states_disabled_share CHECK (enabled OR rollout_share IS NULL),
    CONSTRAINT ck_feature_flag_states_targeting CHECK (
        (targeting_rule IS NULL) = (targeting_digest IS NULL)
    ),
    CONSTRAINT ck_feature_flag_states_targeting_shape CHECK (
        targeting_rule IS NULL OR jsonb_typeof(targeting_rule) = 'object'
    ),
    CONSTRAINT ck_feature_flag_states_targeting_digest CHECK (
        targeting_digest IS NULL OR targeting_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_feature_flag_states_disabled_targeting CHECK (
        enabled OR targeting_rule IS NULL
    ),
    CONSTRAINT ck_feature_flag_states_origin CHECK (
        origin IN ('BOOTSTRAP', 'CHANGE_REQUEST', 'KILL_SWITCH_PULL', 'ROLLBACK', 'EXPIRY')
    ),
    -- Enabling goes through a request. Pulling a switch and letting a flag expire both turn things
    -- off, and neither is allowed to turn anything on.
    CONSTRAINT ck_feature_flag_states_enable_origin CHECK (
        NOT enabled OR origin IN ('BOOTSTRAP', 'CHANGE_REQUEST', 'ROLLBACK')
    ),
    CONSTRAINT ck_feature_flag_states_request CHECK (
        (origin IN ('CHANGE_REQUEST', 'ROLLBACK')) = (change_request_id IS NOT NULL)
    ),
    -- An emergency pull says which emergency, so the post-incident review can find it.
    CONSTRAINT ck_feature_flag_states_incident CHECK (
        origin <> 'KILL_SWITCH_PULL' OR incident_reference IS NOT NULL
    ),
    CONSTRAINT ck_feature_flag_states_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_feature_flag_states_version CHECK (version >= 0),
    -- The scope columns are coalesced rather than compared directly: an exclusion constraint
    -- treats two nulls as non-conflicting, so a global scope would silently be exempt from the
    -- rule and two contradictory global states could be in force at once.
    CONSTRAINT ex_feature_flag_states_no_overlap EXCLUDE USING gist (
        feature_flag_definition_id WITH =,
        scope_type WITH =,
        (COALESCE(scope_id, '00000000-0000-0000-0000-000000000000'::UUID)) WITH =,
        (COALESCE(market_code, '**')) WITH =,
        tstzrange(effective_from, effective_until, '[)') WITH &&
    )
);

CREATE INDEX idx_feature_flag_states_open ON feature_flag_states (feature_flag_definition_id)
    WHERE effective_until IS NULL;
CREATE INDEX idx_feature_flag_states_pulls ON feature_flag_states (effective_from)
    WHERE origin = 'KILL_SWITCH_PULL';
--rollback DROP TABLE feature_flag_states;

--changeset ninggiangboy:032-16-operational-command-definitions
-- The catalogue of things an operator is allowed to ask the marketplace to do. Every entry names
-- the permission that opens it, whether it moves money, whether it can be undone, and what it
-- costs at most. What no entry does is describe how to perform the action: the domain that owns
-- the booking, the payment or the payout still performs it under its own invariants, and an
-- operator asking for something that domain refuses gets a refusal, recorded as the outcome.
CREATE TABLE operational_command_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_key             VARCHAR(96) NOT NULL,
    command_version         INTEGER NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    owning_domain           VARCHAR(48) NOT NULL,
    action_kind             VARCHAR(24) NOT NULL,
    target_type             VARCHAR(48) NOT NULL,
    required_permission_key VARCHAR(96) NOT NULL,
    monetary                BOOLEAN NOT NULL DEFAULT false,
    maximum_amount_minor    BIGINT,
    amount_currency         VARCHAR(3),
    reversible              BOOLEAN NOT NULL DEFAULT false,
    reversal_command_key    VARCHAR(96),
    requires_maker_checker  BOOLEAN NOT NULL DEFAULT false,
    required_approval_roles TEXT[],
    requires_reason_code    BOOLEAN NOT NULL DEFAULT true,
    reason_code_set         VARCHAR(64),
    requires_justification  BOOLEAN NOT NULL DEFAULT false,
    dry_run_supported       BOOLEAN NOT NULL DEFAULT false,
    daily_execution_limit   INTEGER,
    owner_reference         VARCHAR(64) NOT NULL,
    documentation_reference VARCHAR(200) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    activated_at            TIMESTAMPTZ,
    deprecated_at           TIMESTAMPTZ,
    retired_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_operational_command_definitions_version UNIQUE (command_key, command_version),
    CONSTRAINT ck_operational_command_definitions_key CHECK (command_key ~ '^[a-z][a-z0-9_.]*$'),
    CONSTRAINT ck_operational_command_definitions_version_number CHECK (command_version > 0),
    CONSTRAINT ck_operational_command_definitions_action CHECK (
        action_kind IN (
            'PAUSE', 'RESUME', 'CANCEL', 'RETRY', 'REFUND', 'HOLD',
            'RELEASE', 'ADJUST', 'REPROCESS', 'CLOSE', 'REASSIGN'
        )
    ),
    CONSTRAINT ck_operational_command_definitions_permission CHECK (
        required_permission_key ~ '^[a-z][a-z0-9_.]*$'
    ),
    -- A command that moves money states its ceiling and its currency. An uncapped manual refund is
    -- the single most expensive thing an administrative console can offer.
    CONSTRAINT ck_operational_command_definitions_monetary CHECK (
        monetary = (maximum_amount_minor IS NOT NULL AND amount_currency IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_definitions_amount CHECK (
        maximum_amount_minor IS NULL OR maximum_amount_minor > 0
    ),
    CONSTRAINT ck_operational_command_definitions_currency CHECK (
        amount_currency IS NULL OR amount_currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_operational_command_definitions_reversal CHECK (
        reversible = (reversal_command_key IS NOT NULL)
    ),
    -- Money and one-way doors are never one person's decision, whatever the entry would prefer.
    CONSTRAINT ck_operational_command_definitions_maker_checker CHECK (
        requires_maker_checker OR (NOT monetary AND reversible)
    ),
    CONSTRAINT ck_operational_command_definitions_approval_roles CHECK (
        requires_maker_checker = (array_length(required_approval_roles, 1) IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_definitions_reason CHECK (
        requires_reason_code = (reason_code_set IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_definitions_limit CHECK (
        daily_execution_limit IS NULL OR daily_execution_limit > 0
    ),
    CONSTRAINT ck_operational_command_definitions_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'DEPRECATED', 'RETIRED')
    ),
    CONSTRAINT ck_operational_command_definitions_activation CHECK (
        (status = 'DRAFT') = (activated_at IS NULL)
    ),
    CONSTRAINT ck_operational_command_definitions_deprecation CHECK (
        status <> 'DEPRECATED' OR deprecated_at IS NOT NULL
    ),
    CONSTRAINT ck_operational_command_definitions_retirement CHECK (
        (status = 'RETIRED') = (retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_definitions_version_column CHECK (version >= 0)
);

CREATE INDEX idx_operational_command_definitions_domain
    ON operational_command_definitions (owning_domain, action_kind);
CREATE INDEX idx_operational_command_definitions_permission
    ON operational_command_definitions (required_permission_key) WHERE status = 'ACTIVE';
--rollback DROP TABLE operational_command_definitions;

--changeset ninggiangboy:032-17-operational-command-executions
CREATE TABLE operational_command_executions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_definition_id UUID NOT NULL,
    command_key           VARCHAR(96) NOT NULL,
    idempotency_key       VARCHAR(64) NOT NULL,
    actor_id              UUID NOT NULL,
    role_assignment_id    UUID,
    break_glass_grant_id  UUID,
    target_domain         VARCHAR(48) NOT NULL,
    target_type           VARCHAR(48) NOT NULL,
    target_id             UUID,
    target_reference      VARCHAR(128),
    market_code           VARCHAR(2),
    parameters            JSONB NOT NULL,
    parameters_digest     CHAR(64) NOT NULL,
    amount_minor          BIGINT,
    amount_currency       VARCHAR(3),
    reason_code           VARCHAR(64),
    justification         VARCHAR(2000),
    dry_run               BOOLEAN NOT NULL DEFAULT false,
    requested_at          TIMESTAMPTZ NOT NULL,
    dispatch_state        VARCHAR(24) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_at           TIMESTAMPTZ,
    dispatched_at         TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    domain_outcome        VARCHAR(24),
    domain_reference      VARCHAR(128),
    refusal_reason        VARCHAR(200),
    failure_reason        VARCHAR(200),
    before_digest         CHAR(64),
    after_digest          CHAR(64),
    audit_event_id        UUID,
    correlation_id        VARCHAR(64) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_operational_command_executions_idempotency
        UNIQUE (command_key, idempotency_key),
    CONSTRAINT fk_operational_command_executions_definition FOREIGN KEY (command_definition_id)
        REFERENCES operational_command_definitions (id),
    CONSTRAINT fk_operational_command_executions_actor FOREIGN KEY (actor_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_operational_command_executions_assignment FOREIGN KEY (role_assignment_id)
        REFERENCES operator_role_assignments (id),
    CONSTRAINT fk_operational_command_executions_break_glass FOREIGN KEY (break_glass_grant_id)
        REFERENCES break_glass_grants (id),
    CONSTRAINT fk_operational_command_executions_audit FOREIGN KEY (audit_event_id)
        REFERENCES audit_events (id),
    CONSTRAINT fk_operational_command_executions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    -- Ordinary authority or emergency authority, and the row says which. An execution under
    -- neither is an operator acting with no recorded basis at all.
    CONSTRAINT ck_operational_command_executions_authority CHECK (
        num_nonnulls(role_assignment_id, break_glass_grant_id) = 1
    ),
    CONSTRAINT ck_operational_command_executions_target CHECK (
        target_id IS NOT NULL OR target_reference IS NOT NULL
    ),
    CONSTRAINT ck_operational_command_executions_parameters CHECK (
        jsonb_typeof(parameters) = 'object'
    ),
    CONSTRAINT ck_operational_command_executions_parameters_digest CHECK (
        parameters_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_operational_command_executions_amount CHECK (
        (amount_minor IS NULL) = (amount_currency IS NULL)
    ),
    CONSTRAINT ck_operational_command_executions_amount_value CHECK (
        amount_minor IS NULL OR amount_minor > 0
    ),
    CONSTRAINT ck_operational_command_executions_currency CHECK (
        amount_currency IS NULL OR amount_currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_operational_command_executions_state CHECK (
        dispatch_state IN (
            'PENDING_APPROVAL', 'APPROVED', 'DISPATCHED',
            'SUCCEEDED', 'REFUSED', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_operational_command_executions_approved CHECK (
        (dispatch_state IN ('PENDING_APPROVAL', 'CANCELLED')) = (approved_at IS NULL)
    ),
    CONSTRAINT ck_operational_command_executions_dispatched CHECK (
        (dispatch_state IN ('PENDING_APPROVAL', 'APPROVED', 'CANCELLED'))
            = (dispatched_at IS NULL)
    ),
    CONSTRAINT ck_operational_command_executions_completed CHECK (
        (dispatch_state IN ('SUCCEEDED', 'REFUSED', 'FAILED')) = (completed_at IS NOT NULL)
    ),
    -- A refusal by the owning domain is a normal, expected outcome and is recorded with its
    -- reason. An administrative console that can only record successes is one whose logs make
    -- every denied attempt look like it never happened.
    CONSTRAINT ck_operational_command_executions_outcome CHECK (
        (dispatch_state IN ('SUCCEEDED', 'REFUSED', 'FAILED')) = (domain_outcome IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_executions_outcome_value CHECK (
        domain_outcome IS NULL
            OR domain_outcome IN ('APPLIED', 'NO_CHANGE', 'REFUSED', 'PARTIAL', 'ERROR')
    ),
    CONSTRAINT ck_operational_command_executions_refusal CHECK (
        (dispatch_state = 'REFUSED') = (refusal_reason IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_executions_failure CHECK (
        (dispatch_state = 'FAILED') = (failure_reason IS NOT NULL)
    ),
    -- A dry run answers what would happen; it may not claim to have changed anything.
    CONSTRAINT ck_operational_command_executions_dry_run CHECK (
        NOT dry_run OR (after_digest IS NULL AND domain_outcome IS DISTINCT FROM 'APPLIED')
    ),
    CONSTRAINT ck_operational_command_executions_digests CHECK (
        (before_digest IS NULL OR before_digest ~ '^[0-9a-f]{64}$')
            AND (after_digest IS NULL OR after_digest ~ '^[0-9a-f]{64}$')
    ),
    -- Something that applied a change records both sides of it, which is what makes the audit row
    -- a before and after rather than an assertion.
    CONSTRAINT ck_operational_command_executions_applied_digests CHECK (
        domain_outcome IS DISTINCT FROM 'APPLIED'
            OR (before_digest IS NOT NULL AND after_digest IS NOT NULL)
    ),
    CONSTRAINT ck_operational_command_executions_order CHECK (
        (approved_at IS NULL OR approved_at >= requested_at)
            AND (dispatched_at IS NULL OR dispatched_at >= approved_at)
            AND (completed_at IS NULL OR completed_at >= dispatched_at)
    ),
    CONSTRAINT ck_operational_command_executions_justification CHECK (
        justification IS NULL OR length(btrim(justification)) > 0
    ),
    CONSTRAINT ck_operational_command_executions_version CHECK (version >= 0)
);

CREATE INDEX idx_operational_command_executions_actor
    ON operational_command_executions (actor_id, requested_at);
CREATE INDEX idx_operational_command_executions_target
    ON operational_command_executions (target_type, target_id);
CREATE INDEX idx_operational_command_executions_pending
    ON operational_command_executions (requested_at)
    WHERE dispatch_state IN ('PENDING_APPROVAL', 'APPROVED', 'DISPATCHED');
--rollback DROP TABLE operational_command_executions;

--changeset ninggiangboy:032-18-operational-command-approvals
CREATE TABLE operational_command_approvals (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    command_execution_id  UUID NOT NULL,
    approval_role         VARCHAR(64) NOT NULL,
    decision              VARCHAR(16) NOT NULL,
    approver_id           UUID NOT NULL,
    decided_at            TIMESTAMPTZ NOT NULL,
    approved_digest       CHAR(64) NOT NULL,
    approved_amount_minor BIGINT,
    comment               VARCHAR(2000),
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_operational_command_approvals_role
        UNIQUE (command_execution_id, approval_role),
    CONSTRAINT fk_operational_command_approvals_execution FOREIGN KEY (command_execution_id)
        REFERENCES operational_command_executions (id) ON DELETE CASCADE,
    CONSTRAINT fk_operational_command_approvals_approver FOREIGN KEY (approver_id)
        REFERENCES account_holders (id),
    CONSTRAINT ck_operational_command_approvals_decision CHECK (
        decision IN ('APPROVED', 'REJECTED')
    ),
    -- The approval names the exact parameters it saw, so that editing a command after approval
    -- invalidates the approval instead of inheriting it.
    CONSTRAINT ck_operational_command_approvals_digest CHECK (
        approved_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_operational_command_approvals_amount CHECK (
        approved_amount_minor IS NULL OR approved_amount_minor > 0
    ),
    CONSTRAINT ck_operational_command_approvals_rejection_comment CHECK (
        decision <> 'REJECTED' OR comment IS NOT NULL
    )
);

CREATE INDEX idx_operational_command_approvals_approver
    ON operational_command_approvals (approver_id, decided_at);
--rollback DROP TABLE operational_command_approvals;

--changeset ninggiangboy:032-19-bulk-export-requests
-- Reading production in bulk is an action, and it is recorded like one. Minimisation is the
-- columns: the data classes asked for, the rows expected, the redaction profile applied and the
-- date the artifact stops existing. An export with no expiry is a copy of the marketplace living
-- somewhere nobody is monitoring, and it is the reason breach notifications name years-old files.
CREATE TABLE bulk_export_requests (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purpose               VARCHAR(1000) NOT NULL,
    purpose_class         VARCHAR(24) NOT NULL,
    legal_basis           VARCHAR(64) NOT NULL,
    owning_domain         VARCHAR(48) NOT NULL,
    data_classes          TEXT[] NOT NULL,
    data_sensitivity      VARCHAR(16) NOT NULL,
    redaction_profile     VARCHAR(64),
    query_reference       VARCHAR(200) NOT NULL,
    market_code           VARCHAR(2),
    estimated_row_count   BIGINT NOT NULL,
    actual_row_count      BIGINT,
    destination_kind      VARCHAR(24) NOT NULL,
    destination_reference VARCHAR(200) NOT NULL,
    requested_by          UUID NOT NULL,
    requested_at          TIMESTAMPTZ NOT NULL,
    approved_by           UUID,
    approved_at           TIMESTAMPTZ,
    export_state          VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    generated_at          TIMESTAMPTZ,
    artifact_digest       CHAR(64),
    artifact_byte_size    BIGINT,
    expires_at            TIMESTAMPTZ NOT NULL,
    access_count          INTEGER NOT NULL DEFAULT 0,
    rejection_reason      VARCHAR(64),
    revoked_by            UUID,
    revoked_at            TIMESTAMPTZ,
    revocation_reason     VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_bulk_export_requests_requested_by FOREIGN KEY (requested_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_bulk_export_requests_approved_by FOREIGN KEY (approved_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_bulk_export_requests_revoked_by FOREIGN KEY (revoked_by)
        REFERENCES account_holders (id),
    CONSTRAINT fk_bulk_export_requests_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT ck_bulk_export_requests_purpose CHECK (length(btrim(purpose)) > 0),
    CONSTRAINT ck_bulk_export_requests_purpose_class CHECK (
        purpose_class IN (
            'INCIDENT', 'REGULATORY', 'LEGAL_HOLD', 'FINANCE',
            'ANALYTICS', 'MIGRATION', 'SUBJECT_REQUEST'
        )
    ),
    CONSTRAINT ck_bulk_export_requests_classes CHECK (
        array_length(data_classes, 1) IS NOT NULL
    ),
    CONSTRAINT ck_bulk_export_requests_sensitivity CHECK (
        data_sensitivity IN ('NON_PERSONAL', 'PSEUDONYMOUS', 'PERSONAL', 'RESTRICTED')
    ),
    -- Personal data leaves the platform under a named redaction profile or it does not leave.
    CONSTRAINT ck_bulk_export_requests_redaction CHECK (
        data_sensitivity IN ('NON_PERSONAL', 'PSEUDONYMOUS') OR redaction_profile IS NOT NULL
    ),
    CONSTRAINT ck_bulk_export_requests_destination CHECK (
        destination_kind IN (
            'SECURE_BUCKET', 'ANALYST_WORKSPACE', 'REGULATOR_TRANSFER',
            'LEGAL_COUNSEL', 'SUBJECT_DELIVERY'
        )
    ),
    -- Analytics is the one purpose with an alternative: the analytical layer built in migration
    -- 030 exists so that routine analysis never needs a copy of production personal data.
    CONSTRAINT ck_bulk_export_requests_analytics CHECK (
        purpose_class <> 'ANALYTICS' OR data_sensitivity IN ('NON_PERSONAL', 'PSEUDONYMOUS')
    ),
    CONSTRAINT ck_bulk_export_requests_rows CHECK (
        estimated_row_count >= 0 AND (actual_row_count IS NULL OR actual_row_count >= 0)
    ),
    CONSTRAINT ck_bulk_export_requests_state CHECK (
        export_state IN ('REQUESTED', 'APPROVED', 'GENERATED', 'REJECTED', 'EXPIRED', 'REVOKED')
    ),
    CONSTRAINT ck_bulk_export_requests_approval CHECK (
        (export_state IN ('REQUESTED', 'REJECTED'))
            = (approved_by IS NULL AND approved_at IS NULL)
    ),
    -- Nobody approves their own bulk read of the marketplace.
    CONSTRAINT ck_bulk_export_requests_approver CHECK (
        approved_by IS NULL OR approved_by <> requested_by
    ),
    CONSTRAINT ck_bulk_export_requests_generated CHECK (
        (export_state IN ('GENERATED', 'EXPIRED', 'REVOKED'))
            = (generated_at IS NOT NULL AND artifact_digest IS NOT NULL
                AND artifact_byte_size IS NOT NULL AND actual_row_count IS NOT NULL)
    ),
    CONSTRAINT ck_bulk_export_requests_artifact_digest CHECK (
        artifact_digest IS NULL OR artifact_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_bulk_export_requests_artifact_size CHECK (
        artifact_byte_size IS NULL OR artifact_byte_size >= 0
    ),
    CONSTRAINT ck_bulk_export_requests_expiry CHECK (expires_at > requested_at),
    CONSTRAINT ck_bulk_export_requests_access_count CHECK (access_count >= 0),
    CONSTRAINT ck_bulk_export_requests_rejected CHECK (
        (export_state = 'REJECTED') = (rejection_reason IS NOT NULL)
    ),
    CONSTRAINT ck_bulk_export_requests_revoked CHECK (
        (export_state = 'REVOKED')
            = (revoked_by IS NOT NULL AND revoked_at IS NOT NULL
                AND revocation_reason IS NOT NULL)
    ),
    CONSTRAINT ck_bulk_export_requests_order CHECK (
        (approved_at IS NULL OR approved_at >= requested_at)
            AND (generated_at IS NULL OR generated_at >= approved_at)
    ),
    CONSTRAINT ck_bulk_export_requests_version CHECK (version >= 0)
);

CREATE INDEX idx_bulk_export_requests_requester
    ON bulk_export_requests (requested_by, requested_at);
CREATE INDEX idx_bulk_export_requests_live ON bulk_export_requests (expires_at)
    WHERE export_state IN ('APPROVED', 'GENERATED');
--rollback DROP TABLE bulk_export_requests;

--changeset ninggiangboy:032-20-bulk-export-accesses
CREATE TABLE bulk_export_accesses (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bulk_export_request_id UUID NOT NULL,
    sequence_number        INTEGER NOT NULL,
    accessed_at            TIMESTAMPTZ NOT NULL,
    accessor_id            UUID NOT NULL,
    access_kind            VARCHAR(16) NOT NULL,
    byte_count             BIGINT NOT NULL,
    source_address_digest  CHAR(64),
    request_id             VARCHAR(64),
    audit_event_id         UUID,
    created_at             TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_bulk_export_accesses_sequence
        UNIQUE (bulk_export_request_id, sequence_number),
    CONSTRAINT fk_bulk_export_accesses_request FOREIGN KEY (bulk_export_request_id)
        REFERENCES bulk_export_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_bulk_export_accesses_accessor FOREIGN KEY (accessor_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_bulk_export_accesses_audit FOREIGN KEY (audit_event_id)
        REFERENCES audit_events (id),
    CONSTRAINT ck_bulk_export_accesses_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_bulk_export_accesses_kind CHECK (
        access_kind IN ('DOWNLOAD', 'PREVIEW', 'TRANSFER', 'VERIFY')
    ),
    CONSTRAINT ck_bulk_export_accesses_bytes CHECK (byte_count >= 0),
    -- The address is kept as a digest: enough to recognise that two retrievals came from the same
    -- place, not enough to become another copy of somebody's location.
    CONSTRAINT ck_bulk_export_accesses_address CHECK (
        source_address_digest IS NULL OR source_address_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_bulk_export_accesses_accessor
    ON bulk_export_accesses (accessor_id, accessed_at);
--rollback DROP TABLE bulk_export_accesses;

--changeset ninggiangboy:032-21-admin-append-only splitStatements:false
-- Approvals, validations, activity logs and retrieval logs are evidence about what an operator
-- did and who agreed to it. Editing one is not a correction; it is the destruction of the only
-- record that could have shown the problem. Configuration versions are here too: a version may be
-- closed by a later one, and nothing else about it may move, because the incident review needs to
-- read the value that was actually live. These reuse the function migration 030 installed rather
-- than duplicating it, so both halves of the platform protect evidence the same way.
CREATE TRIGGER trg_operator_role_permissions_append_only
    BEFORE UPDATE ON operator_role_permissions
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_break_glass_activities_append_only
    BEFORE UPDATE ON break_glass_activities
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_change_request_approvals_append_only
    BEFORE UPDATE ON change_request_approvals
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_change_request_validations_append_only
    BEFORE UPDATE ON change_request_validations
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_command_approvals_append_only
    BEFORE UPDATE ON operational_command_approvals
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_bulk_export_accesses_append_only
    BEFORE UPDATE ON bulk_export_accesses
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only();

CREATE TRIGGER trg_configuration_versions_append_only
    BEFORE UPDATE ON configuration_versions
    FOR EACH ROW
    EXECUTE FUNCTION platform_append_only(
        'effective_until', 'superseded_by', 'updated_at', 'version');
--rollback DROP TRIGGER trg_configuration_versions_append_only ON configuration_versions;
--rollback DROP TRIGGER trg_bulk_export_accesses_append_only ON bulk_export_accesses;
--rollback DROP TRIGGER trg_command_approvals_append_only ON operational_command_approvals;
--rollback DROP TRIGGER trg_change_request_validations_append_only ON change_request_validations;
--rollback DROP TRIGGER trg_change_request_approvals_append_only ON change_request_approvals;
--rollback DROP TRIGGER trg_break_glass_activities_append_only ON break_glass_activities;
--rollback DROP TRIGGER trg_operator_role_permissions_append_only ON operator_role_permissions;

--changeset ninggiangboy:032-22-governance-registry-immutability splitStatements:false
-- A role that can be widened after it was granted, a configuration schema that can change what it
-- permits after values were approved against it, a flag whose meaning can be edited after it was
-- turned on, a command whose ceiling can be raised after it was approved -- each of those turns an
-- approval already given into approval of something nobody read. Once one of these definitions
-- leaves DRAFT it is frozen except for the columns that move it through its own lifecycle. A
-- different meaning is a new version, which is what keeps the old approvals readable.
CREATE TRIGGER trg_operator_role_definitions_freeze
    BEFORE UPDATE OR DELETE ON operator_role_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'activated_at', 'deprecated_at', 'retired_at',
        'owner_reference', 'updated_at', 'version');

CREATE TRIGGER trg_configuration_schemas_freeze
    BEFORE UPDATE OR DELETE ON configuration_schemas
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'activated_at', 'deprecated_at', 'retired_at',
        'owner_reference', 'documentation_reference', 'updated_at', 'version');

-- A flag's expiry may move, and moving it is recorded as an extension with a reason. Everything
-- else about the flag -- what it is, what it disables, who may turn it on -- is frozen.
CREATE TRIGGER trg_feature_flag_definitions_freeze
    BEFORE UPDATE OR DELETE ON feature_flag_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'activated_at', 'deprecated_at', 'retired_at', 'owner_reference',
        'expires_on', 'extension_count', 'extension_reason', 'updated_at', 'version');

CREATE TRIGGER trg_command_definitions_freeze
    BEFORE UPDATE OR DELETE ON operational_command_definitions
    FOR EACH ROW
    WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION platform_contract_freeze(
        'status', 'activated_at', 'deprecated_at', 'retired_at',
        'owner_reference', 'documentation_reference', 'updated_at', 'version');
--rollback DROP TRIGGER trg_command_definitions_freeze ON operational_command_definitions;
--rollback DROP TRIGGER trg_feature_flag_definitions_freeze ON feature_flag_definitions;
--rollback DROP TRIGGER trg_configuration_schemas_freeze ON configuration_schemas;
--rollback DROP TRIGGER trg_operator_role_definitions_freeze ON operator_role_definitions;

--changeset ninggiangboy:032-23-role-permission-membership splitStatements:false
-- The permissions are the role. An assignment names a role version and is thereby understood to
-- confer exactly those permissions; adding one afterwards would widen every grant already made,
-- retroactively and silently, and nothing in the approval trail would show it. So membership is
-- frozen when the role leaves DRAFT, and a role cannot be activated with nothing in it.
CREATE FUNCTION operator_role_membership_seal() RETURNS TRIGGER AS $$
DECLARE
    role_status TEXT;
    role_sensitivity TEXT;
    sensitivity_rank INTEGER;
    member_rank INTEGER;
BEGIN
    SELECT status, data_sensitivity INTO role_status, role_sensitivity
    FROM operator_role_definitions
    WHERE id = COALESCE(NEW.role_definition_id, OLD.role_definition_id);

    IF role_status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'operator role % is % and its permissions are what every existing assignment was '
            'approved against; publish a new role version instead',
            COALESCE(NEW.role_definition_id, OLD.role_definition_id), role_status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP <> 'DELETE' THEN
        sensitivity_rank := CASE role_sensitivity
            WHEN 'NON_PERSONAL' THEN 0 WHEN 'PSEUDONYMOUS' THEN 1
            WHEN 'PERSONAL' THEN 2 ELSE 3 END;
        member_rank := CASE NEW.data_sensitivity
            WHEN 'NON_PERSONAL' THEN 0 WHEN 'PSEUDONYMOUS' THEN 1
            WHEN 'PERSONAL' THEN 2 ELSE 3 END;

        -- The role's declared sensitivity is what the approver of an assignment read. A permission
        -- that reaches further than that makes the declaration false.
        IF member_rank > sensitivity_rank THEN
            RAISE EXCEPTION
                'permission % reaches % data while operator role % is declared %; raise the role '
                'declaration rather than widening it quietly',
                NEW.permission_key, NEW.data_sensitivity, NEW.role_definition_id, role_sensitivity
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operator_role_permissions_seal
    BEFORE INSERT OR UPDATE OR DELETE ON operator_role_permissions
    FOR EACH ROW
    EXECUTE FUNCTION operator_role_membership_seal();

-- Permissions reference the role, so they cannot exist at the instant it is inserted. A role
-- created already active would be a permanently empty authority that no permission can ever be
-- added to -- and since an assignment may name it, an empty role is an operator granted a title
-- and nothing else, which is worse than a refusal because it looks like it worked.
CREATE FUNCTION operator_role_activation_requirements() RETURNS TRIGGER AS $$
DECLARE
    permission_count INTEGER;
BEGIN
    IF TG_OP = 'INSERT' AND NEW.status <> 'DRAFT' THEN
        RAISE EXCEPTION
            'an operator role is created in draft and activated once its permissions exist; it '
            'may not be inserted already %', NEW.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF TG_OP = 'UPDATE' AND OLD.status = 'DRAFT' AND NEW.status <> 'DRAFT' THEN
        SELECT count(*) INTO permission_count
        FROM operator_role_permissions
        WHERE role_definition_id = NEW.id;

        IF permission_count = 0 THEN
            RAISE EXCEPTION
                'operator role % has no permissions; activating it would register an authority '
                'that grants nothing', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operator_role_definitions_activation
    BEFORE INSERT OR UPDATE ON operator_role_definitions
    FOR EACH ROW
    EXECUTE FUNCTION operator_role_activation_requirements();
--rollback DROP TRIGGER trg_operator_role_definitions_activation ON operator_role_definitions;
--rollback DROP FUNCTION operator_role_activation_requirements();
--rollback DROP TRIGGER trg_operator_role_permissions_seal ON operator_role_permissions;
--rollback DROP FUNCTION operator_role_membership_seal();

--changeset ninggiangboy:032-24-role-assignment-integrity splitStatements:false
-- Separation of duties written down and not enforced survives exactly until the week somebody is
-- on leave. The conflict table says which pairs of roles one person may not hold; this refuses the
-- assignment that would create the pair, and it refuses it at insert rather than reporting it in a
-- quarterly access review, by which time every decision made under both roles has already been
-- made. An exception is possible and is a row: it names the conflict it overrides, and the
-- conflict itself has to have said an exception was allowed.
CREATE FUNCTION operator_role_assignment_integrity() RETURNS TRIGGER AS $$
DECLARE
    role_record RECORD;
    conflicting_role TEXT;
    exception_record RECORD;
BEGIN
    SELECT role_key, status, market_scope, maximum_grant_days, recertification_days,
           requires_training
    INTO role_record
    FROM operator_role_definitions
    WHERE id = NEW.role_definition_id;

    IF role_record.status <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'operator role % is %; authority is granted against an active role definition only',
            NEW.role_definition_id, role_record.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.role_key <> role_record.role_key THEN
        RAISE EXCEPTION
            'assignment names role key % while role definition % is %; the denormalized key is '
            'what the live-assignment index is enforced on',
            NEW.role_key, NEW.role_definition_id, role_record.role_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF (role_record.market_scope = 'MARKET') <> (NEW.market_code IS NOT NULL) THEN
        RAISE EXCEPTION
            'operator role % is scoped % and the assignment market code does not match it',
            role_record.role_key, role_record.market_scope
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- The role's own cap is the ceiling. Granting for longer than the role permits is how a
    -- bounded authority becomes a standing one without anybody deciding that it should.
    IF NEW.effective_until
            > NEW.effective_from + make_interval(days => role_record.maximum_grant_days) THEN
        RAISE EXCEPTION
            'operator role % caps a grant at % days and this assignment runs longer',
            role_record.role_key, role_record.maximum_grant_days
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.recertification_due_at
            > NEW.effective_from + make_interval(days => role_record.recertification_days) THEN
        RAISE EXCEPTION
            'operator role % requires recertification within % days and this assignment defers it',
            role_record.role_key, role_record.recertification_days
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF role_record.requires_training AND NEW.training_attestation IS NULL THEN
        RAISE EXCEPTION
            'operator role % requires training and this assignment records no attestation',
            role_record.role_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.assignment_state = 'ACTIVE' THEN
        SELECT CASE WHEN c.lower_role_key = NEW.role_key THEN c.higher_role_key
                    ELSE c.lower_role_key END
        INTO conflicting_role
        FROM operator_role_conflicts c
        JOIN operator_role_assignments a
            ON a.operator_id = NEW.operator_id
           AND a.assignment_state = 'ACTIVE'
           AND a.id IS DISTINCT FROM NEW.id
           AND a.role_key = CASE WHEN c.lower_role_key = NEW.role_key THEN c.higher_role_key
                                 ELSE c.lower_role_key END
        WHERE c.withdrawn_at IS NULL
          AND NEW.role_key IN (c.lower_role_key, c.higher_role_key)
        LIMIT 1;

        IF conflicting_role IS NOT NULL THEN
            IF NEW.conflict_exception_id IS NULL THEN
                RAISE EXCEPTION
                    'operator % already holds % which is declared in conflict with %; one person '
                    'holding both sides of a maker-checker pair is the control failing quietly',
                    NEW.operator_id, conflicting_role, NEW.role_key
                    USING ERRCODE = 'restrict_violation';
            END IF;

            SELECT lower_role_key, higher_role_key, exception_allowed, withdrawn_at
            INTO exception_record
            FROM operator_role_conflicts
            WHERE id = NEW.conflict_exception_id;

            IF NOT exception_record.exception_allowed
                    OR exception_record.withdrawn_at IS NOT NULL
                    OR NEW.role_key NOT IN (
                        exception_record.lower_role_key, exception_record.higher_role_key)
                    OR conflicting_role NOT IN (
                        exception_record.lower_role_key, exception_record.higher_role_key) THEN
                RAISE EXCEPTION
                    'the cited exception does not permit operator % to hold % alongside %',
                    NEW.operator_id, NEW.role_key, conflicting_role
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_operator_role_assignments_integrity
    BEFORE INSERT OR UPDATE ON operator_role_assignments
    FOR EACH ROW
    EXECUTE FUNCTION operator_role_assignment_integrity();
--rollback DROP TRIGGER trg_operator_role_assignments_integrity ON operator_role_assignments;
--rollback DROP FUNCTION operator_role_assignment_integrity();

--changeset ninggiangboy:032-25-break-glass-integrity splitStatements:false
-- Emergency access is bounded and reviewed, or it is just access with a better story. The role
-- itself caps how long a grant may run and how soon it owes a review; this refuses a grant that
-- exceeds either. Once granted, the facts of the grant are fixed: who holds it, what justified it
-- and when it ends may not be edited afterwards, because the only person likely to want to edit
-- them is the person the review is about.
CREATE FUNCTION break_glass_grant_integrity() RETURNS TRIGGER AS $$
DECLARE
    role_record RECORD;
BEGIN
    SELECT role_key, status, break_glass_eligible, break_glass_maximum_minutes,
           break_glass_review_hours
    INTO role_record
    FROM operator_role_definitions
    WHERE id = NEW.role_definition_id;

    IF TG_OP = 'INSERT' THEN
        IF role_record.status <> 'ACTIVE' THEN
            RAISE EXCEPTION
                'operator role % is %; emergency access is granted against an active role only',
                NEW.role_definition_id, role_record.status
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NOT role_record.break_glass_eligible THEN
            RAISE EXCEPTION
                'operator role % is not break-glass eligible; an emergency is not a reason to '
                'invent an authority nobody reviewed in advance', role_record.role_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.expires_at > NEW.granted_at
                + make_interval(mins => role_record.break_glass_maximum_minutes) THEN
            RAISE EXCEPTION
                'operator role % caps emergency access at % minutes and this grant runs longer',
                role_record.role_key, role_record.break_glass_maximum_minutes
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.review_due_at > NEW.expires_at
                + make_interval(hours => role_record.break_glass_review_hours) THEN
            RAISE EXCEPTION
                'operator role % requires review within % hours of expiry and this grant defers it',
                role_record.role_key, role_record.break_glass_review_hours
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.review_state <> 'PENDING' THEN
            RAISE EXCEPTION
                'an emergency grant is reviewed after it is used; it may not be inserted already %',
                NEW.review_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.role_definition_id IS DISTINCT FROM OLD.role_definition_id
                OR NEW.operator_id IS DISTINCT FROM OLD.operator_id
                OR NEW.approved_by IS DISTINCT FROM OLD.approved_by
                OR NEW.granted_at IS DISTINCT FROM OLD.granted_at
                OR NEW.expires_at IS DISTINCT FROM OLD.expires_at
                OR NEW.incident_reference IS DISTINCT FROM OLD.incident_reference
                OR NEW.justification IS DISTINCT FROM OLD.justification
                OR NEW.review_due_at IS DISTINCT FROM OLD.review_due_at THEN
            RAISE EXCEPTION
                'emergency grant % is fixed once granted; close it and request another rather '
                'than editing what the review will read', OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF OLD.review_state IN ('REVIEWED', 'ESCALATED')
                AND NEW.review_state NOT IN ('REVIEWED', 'ESCALATED') THEN
            RAISE EXCEPTION
                'emergency grant % has been reviewed; a finding is not reopened by clearing it',
                OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;

        -- A grant that did something has to be reviewed against what it did. Closing the review
        -- while the activity log is empty is only honest when the access was never used.
        IF NEW.review_state IN ('REVIEWED', 'ESCALATED') AND NEW.closed_at IS NULL THEN
            RAISE EXCEPTION
                'emergency grant % is still open; review it after the access has ended', OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_break_glass_grants_integrity
    BEFORE INSERT OR UPDATE ON break_glass_grants
    FOR EACH ROW
    EXECUTE FUNCTION break_glass_grant_integrity();

-- An activity recorded outside the window the grant was open for is either a clock problem or an
-- access that did not happen under this grant at all, and both are worth refusing rather than
-- filing. The counter is maintained here rather than by the caller so that a grant cannot report
-- fewer actions than it logged.
CREATE FUNCTION break_glass_activity_window() RETURNS TRIGGER AS $$
DECLARE
    grant_record RECORD;
BEGIN
    SELECT granted_at, expires_at, closed_at INTO grant_record
    FROM break_glass_grants
    WHERE id = NEW.break_glass_grant_id;

    IF NEW.occurred_at < grant_record.granted_at THEN
        RAISE EXCEPTION
            'activity at % precedes emergency grant % which opened at %',
            NEW.occurred_at, NEW.break_glass_grant_id, grant_record.granted_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.occurred_at > LEAST(grant_record.expires_at,
            COALESCE(grant_record.closed_at, grant_record.expires_at)) THEN
        RAISE EXCEPTION
            'activity at % falls after emergency grant % stopped granting anything',
            NEW.occurred_at, NEW.break_glass_grant_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    UPDATE break_glass_grants
    SET activity_count = activity_count + 1
    WHERE id = NEW.break_glass_grant_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_break_glass_activities_window
    BEFORE INSERT ON break_glass_activities
    FOR EACH ROW
    EXECUTE FUNCTION break_glass_activity_window();
--rollback DROP TRIGGER trg_break_glass_activities_window ON break_glass_activities;
--rollback DROP FUNCTION break_glass_activity_window();
--rollback DROP TRIGGER trg_break_glass_grants_integrity ON break_glass_grants;
--rollback DROP FUNCTION break_glass_grant_integrity();

--changeset ninggiangboy:032-26-change-request-progression splitStatements:false
-- Maker-checker is a state machine with evidence behind every step, not a checkbox somebody ticks.
-- A request reaches VALIDATED only when the checks its schema demands have actually run and
-- passed; APPROVED only when every role the change requires has approved the exact value now
-- proposed, none of them the person who proposed it; APPLIED only from APPROVED. The proposal is
-- frozen once it leaves DRAFT, which is what stops the oldest trick of all: approval of a small
-- change, followed by an edit, followed by application of a large one.
CREATE FUNCTION change_request_progression() RETURNS TRIGGER AS $$
DECLARE
    schema_record RECORD;
    required_role TEXT;
    approval_record RECORD;
    check_outcome TEXT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.request_state <> 'DRAFT' THEN
            RAISE EXCEPTION
                'a change request is created in draft and moves forward as its evidence arrives; '
                'it may not be inserted already %', NEW.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.request_state IN ('APPLIED', 'REJECTED', 'WITHDRAWN', 'SUPERSEDED')
            AND NEW.request_state IS DISTINCT FROM OLD.request_state THEN
        RAISE EXCEPTION
            'change request % is %; raise a new request rather than reviving a closed one',
            OLD.id, OLD.request_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.request_state <> 'DRAFT' THEN
        IF NEW.target_kind IS DISTINCT FROM OLD.target_kind
                OR NEW.configuration_setting_id IS DISTINCT FROM OLD.configuration_setting_id
                OR NEW.feature_flag_definition_id IS DISTINCT FROM OLD.feature_flag_definition_id
                OR NEW.artifact_reference IS DISTINCT FROM OLD.artifact_reference
                OR NEW.proposed_value IS DISTINCT FROM OLD.proposed_value
                OR NEW.required_approval_roles IS DISTINCT FROM OLD.required_approval_roles
                OR NEW.impact_class IS DISTINCT FROM OLD.impact_class
                OR NEW.requested_by IS DISTINCT FROM OLD.requested_by THEN
            RAISE EXCEPTION
                'change request % has left draft; what is being changed, and who has to agree to '
                'it, are what the validations ran against', OLD.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF NEW.request_state = 'VALIDATED' AND OLD.request_state = 'DRAFT' THEN
        FOREACH required_role IN ARRAY ARRAY['SCHEMA', 'POLICY'] LOOP
            SELECT outcome INTO check_outcome
            FROM change_request_validations
            WHERE change_request_id = NEW.id AND validation_kind = required_role
            ORDER BY attempt_number DESC
            LIMIT 1;

            IF check_outcome IS NULL OR check_outcome = 'FAIL' THEN
                RAISE EXCEPTION
                    'change request % has no passing % validation; a value nobody checked the '
                    'shape or the policy of is not ready to be approved', NEW.id, required_role
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END LOOP;

        IF NEW.configuration_setting_id IS NOT NULL THEN
            SELECT sch.requires_simulation, sch.requires_preview, sch.status
            INTO schema_record
            FROM configuration_settings s
            JOIN configuration_schemas sch ON sch.id = s.configuration_schema_id
            WHERE s.id = NEW.configuration_setting_id;

            IF schema_record.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
                RAISE EXCEPTION
                    'change request % targets a setting whose schema is %; a draft or retired '
                    'schema defines nothing to validate against', NEW.id, schema_record.status
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF schema_record.requires_simulation THEN
                SELECT outcome INTO check_outcome
                FROM change_request_validations
                WHERE change_request_id = NEW.id AND validation_kind = 'SIMULATION'
                ORDER BY attempt_number DESC
                LIMIT 1;

                IF check_outcome IS NULL OR check_outcome IN ('FAIL', 'SKIPPED') THEN
                    RAISE EXCEPTION
                        'the schema behind change request % requires a simulation and none has '
                        'passed', NEW.id
                        USING ERRCODE = 'restrict_violation';
                END IF;
            END IF;

            IF schema_record.requires_preview THEN
                SELECT outcome INTO check_outcome
                FROM change_request_validations
                WHERE change_request_id = NEW.id AND validation_kind = 'PREVIEW'
                ORDER BY attempt_number DESC
                LIMIT 1;

                IF check_outcome IS NULL OR check_outcome IN ('FAIL', 'SKIPPED') THEN
                    RAISE EXCEPTION
                        'the schema behind change request % requires a preview and none has '
                        'passed', NEW.id
                        USING ERRCODE = 'restrict_violation';
                END IF;
            END IF;
        END IF;
    END IF;

    IF NEW.request_state = 'AWAITING_APPROVAL' AND OLD.request_state <> 'AWAITING_APPROVAL'
            AND OLD.request_state <> 'VALIDATED' THEN
        RAISE EXCEPTION
            'change request % is %; approvals are collected on a validated proposal',
            OLD.id, OLD.request_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.request_state = 'APPROVED' AND OLD.request_state <> 'APPROVED' THEN
        IF OLD.request_state <> 'AWAITING_APPROVAL' THEN
            RAISE EXCEPTION
                'change request % is %; it reaches approved from awaiting approval only',
                OLD.id, OLD.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;

        FOREACH required_role IN ARRAY NEW.required_approval_roles LOOP
            SELECT decision, approver_id INTO approval_record
            FROM change_request_approvals
            WHERE change_request_id = NEW.id AND approval_role = required_role;

            IF NOT FOUND OR approval_record.decision <> 'APPROVED' THEN
                RAISE EXCEPTION
                    'change request % has no approval from the % role it requires',
                    NEW.id, required_role
                    USING ERRCODE = 'restrict_violation';
            END IF;

            -- The person proposing a change is never one of the people agreeing to it, whichever
            -- roles they happen to hold.
            IF approval_record.approver_id = NEW.requested_by THEN
                RAISE EXCEPTION
                    'change request % was approved for the % role by the person who raised it',
                    NEW.id, required_role
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END LOOP;
    END IF;

    IF NEW.request_state = 'APPLIED' AND OLD.request_state <> 'APPLIED'
            AND OLD.request_state <> 'APPROVED' THEN
        RAISE EXCEPTION
            'change request % is %; only an approved change is applied', OLD.id, OLD.request_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.request_state = 'REJECTED' AND OLD.request_state <> 'REJECTED' THEN
        PERFORM 1 FROM change_request_approvals
        WHERE change_request_id = NEW.id AND decision = 'REJECTED';

        IF NOT FOUND THEN
            RAISE EXCEPTION
                'change request % records no rejection; a refusal names the reviewer who refused',
                NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_change_requests_progression
    BEFORE INSERT OR UPDATE ON change_requests
    FOR EACH ROW
    EXECUTE FUNCTION change_request_progression();

-- An approval is of a value, not of a request. If the proposal moved after the approval was given,
-- the approval is of something that no longer exists.
CREATE FUNCTION change_request_approval_integrity() RETURNS TRIGGER AS $$
DECLARE
    request_record RECORD;
    existing_digest CHAR(64);
BEGIN
    SELECT request_state, requested_by, required_approval_roles INTO request_record
    FROM change_requests
    WHERE id = NEW.change_request_id;

    IF request_record.request_state NOT IN ('VALIDATED', 'AWAITING_APPROVAL') THEN
        RAISE EXCEPTION
            'change request % is %; it is not open for approval',
            NEW.change_request_id, request_record.request_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.approver_id = request_record.requested_by THEN
        RAISE EXCEPTION
            'change request % cannot be approved by the person who raised it',
            NEW.change_request_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT (NEW.approval_role = ANY (request_record.required_approval_roles)) THEN
        RAISE EXCEPTION
            'change request % does not require a % approval; an unrequested sign-off does not '
            'stand in for a missing one', NEW.change_request_id, NEW.approval_role
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- Every approver signed the same thing or the approvals are not of one change.
    SELECT approved_digest INTO existing_digest
    FROM change_request_approvals
    WHERE change_request_id = NEW.change_request_id
    LIMIT 1;

    IF FOUND AND existing_digest <> NEW.approved_digest THEN
        RAISE EXCEPTION
            'change request % already carries approvals of a different value; the earlier '
            'sign-offs are not approvals of what is now proposed', NEW.change_request_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_change_request_approvals_integrity
    BEFORE INSERT ON change_request_approvals
    FOR EACH ROW
    EXECUTE FUNCTION change_request_approval_integrity();
--rollback DROP TRIGGER trg_change_request_approvals_integrity ON change_request_approvals;
--rollback DROP FUNCTION change_request_approval_integrity();
--rollback DROP TRIGGER trg_change_requests_progression ON change_requests;
--rollback DROP FUNCTION change_request_progression();

--changeset ninggiangboy:032-27-configuration-integrity splitStatements:false
-- A value becomes effective because a request was approved and applied, or because the platform
-- seeded it, and there is no third way in. The chain columns are checked against the setting they
-- claim to belong to, because a supersession pointing at another setting's history would make the
-- question "what was this set to in March" answerable two different ways.
CREATE FUNCTION configuration_version_integrity() RETURNS TRIGGER AS $$
DECLARE
    setting_record RECORD;
    request_record RECORD;
    chain_setting UUID;
BEGIN
    SELECT s.setting_state, s.configuration_schema_id, sch.status AS schema_status,
           sch.requires_maker_checker, sch.rollback_supported, sch.schema_key
    INTO setting_record
    FROM configuration_settings s
    JOIN configuration_schemas sch ON sch.id = s.configuration_schema_id
    WHERE s.id = NEW.configuration_setting_id;

    IF setting_record.setting_state <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'configuration setting % is retired; a retired setting is not given new values',
            NEW.configuration_setting_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF setting_record.schema_status NOT IN ('ACTIVE', 'DEPRECATED') THEN
        RAISE EXCEPTION
            'configuration schema % is %; a value has no meaning without a published schema',
            setting_record.schema_key, setting_record.schema_status
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- A setting whose schema says two people must agree may not receive a value that never went
    -- through a request, whatever the origin column would like to claim.
    IF setting_record.requires_maker_checker
            AND NEW.origin IN ('BOOTSTRAP', 'KILL_SWITCH') THEN
        RAISE EXCEPTION
            'configuration schema % requires maker-checker; a % value bypasses the approval it '
            'exists to require', setting_record.schema_key, NEW.origin
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT setting_record.rollback_supported AND NEW.origin = 'ROLLBACK' THEN
        RAISE EXCEPTION
            'configuration schema % declares that it cannot be rolled back; rolling forward is '
            'the only honest move', setting_record.schema_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.change_request_id IS NOT NULL THEN
        SELECT request_state, configuration_setting_id INTO request_record
        FROM change_requests
        WHERE id = NEW.change_request_id;

        IF request_record.configuration_setting_id
                IS DISTINCT FROM NEW.configuration_setting_id THEN
            RAISE EXCEPTION
                'change request % is not about configuration setting %; a foreign key proves the '
                'request exists, not that it is about this setting',
                NEW.change_request_id, NEW.configuration_setting_id
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF request_record.request_state NOT IN ('APPROVED', 'APPLIED') THEN
            RAISE EXCEPTION
                'change request % is %; a value becomes effective when its request is approved',
                NEW.change_request_id, request_record.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF NEW.supersedes_version_id IS NOT NULL THEN
        SELECT configuration_setting_id INTO chain_setting
        FROM configuration_versions WHERE id = NEW.supersedes_version_id;

        IF chain_setting IS DISTINCT FROM NEW.configuration_setting_id THEN
            RAISE EXCEPTION
                'version % supersedes a value belonging to another setting', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF NEW.rollback_of_version_id IS NOT NULL THEN
        SELECT configuration_setting_id INTO chain_setting
        FROM configuration_versions WHERE id = NEW.rollback_of_version_id;

        IF chain_setting IS DISTINCT FROM NEW.configuration_setting_id THEN
            RAISE EXCEPTION
                'version % claims to roll back a value belonging to another setting', NEW.id
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_configuration_versions_integrity
    BEFORE INSERT ON configuration_versions
    FOR EACH ROW
    EXECUTE FUNCTION configuration_version_integrity();

-- The schema lists the scopes it makes sense at, and a list nothing enforces is a comment. A
-- per-listing override of a payout hold that the payout schema only ever meant globally is not a
-- narrower setting; it is a value the resolver will either ignore or apply somewhere its owner
-- never reasoned about, and both of those are worse than a refusal at the point of creation.
CREATE FUNCTION configuration_setting_scope() RETURNS TRIGGER AS $$
DECLARE
    schema_record RECORD;
BEGIN
    SELECT schema_key, status, allowed_scope_types INTO schema_record
    FROM configuration_schemas
    WHERE id = NEW.configuration_schema_id;

    IF NEW.schema_key <> schema_record.schema_key THEN
        RAISE EXCEPTION
            'setting names schema key % while schema % is %; the denormalized key is what the '
            'resolver reads', NEW.schema_key, NEW.configuration_schema_id, schema_record.schema_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF schema_record.status NOT IN ('ACTIVE', 'DEPRECATED') THEN
        RAISE EXCEPTION
            'configuration schema % is %; a setting has nothing to conform to until its schema is '
            'published', schema_record.schema_key, schema_record.status
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT (NEW.scope_type = ANY (schema_record.allowed_scope_types)) THEN
        RAISE EXCEPTION
            'configuration schema % does not allow the % scope', schema_record.schema_key,
            NEW.scope_type
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_configuration_settings_scope
    BEFORE INSERT OR UPDATE ON configuration_settings
    FOR EACH ROW
    EXECUTE FUNCTION configuration_setting_scope();
--rollback DROP TRIGGER trg_configuration_settings_scope ON configuration_settings;
--rollback DROP FUNCTION configuration_setting_scope();
--rollback DROP TRIGGER trg_configuration_versions_integrity ON configuration_versions;
--rollback DROP FUNCTION configuration_version_integrity();

--changeset ninggiangboy:032-28-feature-flag-state-integrity splitStatements:false
-- The asymmetry enforced. Off is always available and needs nobody: an operator who has to find a
-- second approver before pulling a brake will not pull it. On goes through an applied request
-- naming this flag. Past the flag's expiry date only off remains, because a flag that has outlived
-- the date its owner agreed to is undocumented product behaviour with a switch attached.
CREATE FUNCTION feature_flag_state_integrity() RETURNS TRIGGER AS $$
DECLARE
    flag_record RECORD;
    request_record RECORD;
BEGIN
    SELECT flag_key, flag_kind, status, allowed_scope_types, targeting_supported, expires_on,
           default_enabled
    INTO flag_record
    FROM feature_flag_definitions
    WHERE id = NEW.feature_flag_definition_id;

    IF flag_record.status = 'DRAFT' THEN
        RAISE EXCEPTION
            'feature flag % is still a draft; it has no state until it is activated',
            flag_record.flag_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF flag_record.status = 'RETIRED' AND NEW.enabled THEN
        RAISE EXCEPTION
            'feature flag % is retired; register a new flag rather than switching a retired one '
            'back on', flag_record.flag_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NOT (NEW.scope_type = ANY (flag_record.allowed_scope_types)) THEN
        RAISE EXCEPTION
            'feature flag % does not allow the % scope; a scope its owner never considered is a '
            'rollout nobody sized', flag_record.flag_key, NEW.scope_type
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- Seeding is not a way in. A bootstrap state records the default the flag was registered with,
    -- once, before anything else; otherwise the approval that enabling requires could be skipped
    -- by anyone willing to write the word BOOTSTRAP in the origin column.
    IF NEW.origin = 'BOOTSTRAP' THEN
        IF NEW.enabled AND NOT flag_record.default_enabled THEN
            RAISE EXCEPTION
                'feature flag % is registered as off by default; enabling it is a change somebody '
                'has to approve, not a seed value', flag_record.flag_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        PERFORM 1 FROM feature_flag_states
        WHERE feature_flag_definition_id = NEW.feature_flag_definition_id
          AND scope_type = NEW.scope_type
          AND scope_id IS NOT DISTINCT FROM NEW.scope_id
          AND market_code IS NOT DISTINCT FROM NEW.market_code;

        IF FOUND THEN
            RAISE EXCEPTION
                'feature flag % already has a state at this scope; a seed value is the first one '
                'or it is not a seed value', flag_record.flag_key
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    IF NEW.targeting_rule IS NOT NULL AND NOT flag_record.targeting_supported THEN
        RAISE EXCEPTION
            'feature flag % does not support targeting', flag_record.flag_key
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.enabled AND NEW.effective_from::DATE > flag_record.expires_on THEN
        RAISE EXCEPTION
            'feature flag % expired on %; extend it with a recorded reason or retire it, rather '
            'than enabling it past the date its owner agreed to',
            flag_record.flag_key, flag_record.expires_on
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.enabled AND NEW.change_request_id IS NOT NULL THEN
        SELECT request_state, feature_flag_definition_id INTO request_record
        FROM change_requests
        WHERE id = NEW.change_request_id;

        IF request_record.feature_flag_definition_id IS DISTINCT FROM NEW.feature_flag_definition_id
        THEN
            RAISE EXCEPTION
                'change request % is not about feature flag %',
                NEW.change_request_id, flag_record.flag_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF request_record.request_state NOT IN ('APPROVED', 'APPLIED') THEN
            RAISE EXCEPTION
                'change request % is %; a flag is enabled under an approved request',
                NEW.change_request_id, request_record.request_state
                USING ERRCODE = 'restrict_violation';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_feature_flag_states_integrity
    BEFORE INSERT ON feature_flag_states
    FOR EACH ROW
    EXECUTE FUNCTION feature_flag_state_integrity();
--rollback DROP TRIGGER trg_feature_flag_states_integrity ON feature_flag_states;
--rollback DROP FUNCTION feature_flag_state_integrity();

--changeset ninggiangboy:032-29-command-execution-integrity splitStatements:false
-- An operator acts under authority that was live at the time, against a command that is live now,
-- within the ceiling that command declares, with whatever approvals it requires already given by
-- somebody else. None of that makes the action happen: the domain that owns the booking or the
-- payment still applies its own rules, and this refuses only the requests that were never entitled
-- to be asked. Parameters freeze at dispatch so that what was approved is what was sent.
CREATE FUNCTION operational_command_execution_integrity() RETURNS TRIGGER AS $$
DECLARE
    command_record RECORD;
    assignment_record RECORD;
    grant_record RECORD;
    required_role TEXT;
    approval_record RECORD;
BEGIN
    SELECT command_key, status, target_type, monetary, maximum_amount_minor, amount_currency,
           requires_maker_checker, required_approval_roles, requires_reason_code,
           requires_justification, dry_run_supported
    INTO command_record
    FROM operational_command_definitions
    WHERE id = NEW.command_definition_id;

    IF TG_OP = 'INSERT' THEN
        IF command_record.status <> 'ACTIVE' THEN
            RAISE EXCEPTION
                'operational command % is %; a retired or draft command is not an available action',
                command_record.command_key, command_record.status
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.command_key <> command_record.command_key
                OR NEW.target_type <> command_record.target_type THEN
            RAISE EXCEPTION
                'execution names command % against % while definition % is % against %',
                NEW.command_key, NEW.target_type, NEW.command_definition_id,
                command_record.command_key, command_record.target_type
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF command_record.monetary THEN
            IF NEW.amount_minor IS NULL THEN
                RAISE EXCEPTION
                    'operational command % moves money and this execution names no amount',
                    command_record.command_key
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF NEW.amount_minor > command_record.maximum_amount_minor THEN
                RAISE EXCEPTION
                    'operational command % is capped at % and this execution asks for %',
                    command_record.command_key, command_record.maximum_amount_minor,
                    NEW.amount_minor
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF NEW.amount_currency <> command_record.amount_currency THEN
                RAISE EXCEPTION
                    'operational command % is capped in % and this execution is in %',
                    command_record.command_key, command_record.amount_currency, NEW.amount_currency
                    USING ERRCODE = 'restrict_violation';
            END IF;
        ELSIF NEW.amount_minor IS NOT NULL THEN
            RAISE EXCEPTION
                'operational command % does not move money and this execution names an amount',
                command_record.command_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF command_record.requires_reason_code AND NEW.reason_code IS NULL THEN
            RAISE EXCEPTION
                'operational command % requires a reason code', command_record.command_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF command_record.requires_justification AND NEW.justification IS NULL THEN
            RAISE EXCEPTION
                'operational command % requires a written justification',
                command_record.command_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.dry_run AND NOT command_record.dry_run_supported THEN
            RAISE EXCEPTION
                'operational command % has no dry run; asking for one would return an answer that '
                'means nothing', command_record.command_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF NEW.role_assignment_id IS NOT NULL THEN
            SELECT operator_id, assignment_state, effective_from, effective_until
            INTO assignment_record
            FROM operator_role_assignments
            WHERE id = NEW.role_assignment_id;

            IF assignment_record.operator_id <> NEW.actor_id THEN
                RAISE EXCEPTION
                    'execution cites an assignment belonging to another operator'
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF assignment_record.assignment_state <> 'ACTIVE'
                    OR NEW.requested_at < assignment_record.effective_from
                    OR NEW.requested_at >= assignment_record.effective_until THEN
                RAISE EXCEPTION
                    'the cited assignment was not in force at %; expired authority is not '
                    'authority', NEW.requested_at
                    USING ERRCODE = 'restrict_violation';
            END IF;
        ELSE
            SELECT operator_id, granted_at, expires_at, closed_at INTO grant_record
            FROM break_glass_grants
            WHERE id = NEW.break_glass_grant_id;

            IF grant_record.operator_id <> NEW.actor_id THEN
                RAISE EXCEPTION
                    'execution cites an emergency grant belonging to another operator'
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF NEW.requested_at < grant_record.granted_at
                    OR NEW.requested_at > LEAST(grant_record.expires_at,
                        COALESCE(grant_record.closed_at, grant_record.expires_at)) THEN
                RAISE EXCEPTION
                    'the cited emergency grant was not open at %', NEW.requested_at
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END IF;

        IF NEW.dispatch_state NOT IN ('PENDING_APPROVAL', 'APPROVED') THEN
            RAISE EXCEPTION
                'an execution is recorded when it is asked for; it may not be inserted already %',
                NEW.dispatch_state
                USING ERRCODE = 'restrict_violation';
        END IF;

        IF command_record.requires_maker_checker AND NEW.dispatch_state = 'APPROVED' THEN
            RAISE EXCEPTION
                'operational command % requires approval and its approvals are rows that cannot '
                'exist yet', command_record.command_key
                USING ERRCODE = 'restrict_violation';
        END IF;

        RETURN NEW;
    END IF;

    IF NEW.parameters IS DISTINCT FROM OLD.parameters
            OR NEW.parameters_digest IS DISTINCT FROM OLD.parameters_digest
            OR NEW.amount_minor IS DISTINCT FROM OLD.amount_minor
            OR NEW.target_id IS DISTINCT FROM OLD.target_id
            OR NEW.actor_id IS DISTINCT FROM OLD.actor_id THEN
        RAISE EXCEPTION
            'execution % is fixed once requested; what was approved has to be what was sent',
            OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.dispatch_state IN ('SUCCEEDED', 'REFUSED', 'FAILED', 'CANCELLED')
            AND NEW.dispatch_state IS DISTINCT FROM OLD.dispatch_state THEN
        RAISE EXCEPTION
            'execution % is %; issue another command rather than reopening a finished one',
            OLD.id, OLD.dispatch_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.dispatch_state = 'APPROVED' AND OLD.dispatch_state = 'PENDING_APPROVAL'
            AND command_record.requires_maker_checker THEN
        FOREACH required_role IN ARRAY command_record.required_approval_roles LOOP
            SELECT decision, approver_id, approved_digest, approved_amount_minor
            INTO approval_record
            FROM operational_command_approvals
            WHERE command_execution_id = NEW.id AND approval_role = required_role;

            IF NOT FOUND OR approval_record.decision <> 'APPROVED' THEN
                RAISE EXCEPTION
                    'execution % has no approval from the % role command % requires',
                    NEW.id, required_role, command_record.command_key
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF approval_record.approver_id = NEW.actor_id THEN
                RAISE EXCEPTION
                    'execution % was approved for the % role by the operator issuing it',
                    NEW.id, required_role
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF approval_record.approved_digest <> NEW.parameters_digest THEN
                RAISE EXCEPTION
                    'the % approval on execution % is of different parameters',
                    required_role, NEW.id
                    USING ERRCODE = 'restrict_violation';
            END IF;

            IF NEW.amount_minor IS NOT NULL
                    AND approval_record.approved_amount_minor IS NOT NULL
                    AND NEW.amount_minor > approval_record.approved_amount_minor THEN
                RAISE EXCEPTION
                    'execution % asks for % which exceeds the % approved for the % role',
                    NEW.id, NEW.amount_minor, approval_record.approved_amount_minor, required_role
                    USING ERRCODE = 'restrict_violation';
            END IF;
        END LOOP;
    END IF;

    IF NEW.dispatch_state = 'DISPATCHED' AND OLD.dispatch_state <> 'DISPATCHED'
            AND OLD.dispatch_state <> 'APPROVED' THEN
        RAISE EXCEPTION
            'execution % is %; a command is sent to its domain once it is approved',
            OLD.id, OLD.dispatch_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_command_executions_integrity
    BEFORE INSERT OR UPDATE ON operational_command_executions
    FOR EACH ROW
    EXECUTE FUNCTION operational_command_execution_integrity();

-- An approver cannot approve their own command, and cannot approve one that has already gone.
CREATE FUNCTION operational_command_approval_integrity() RETURNS TRIGGER AS $$
DECLARE
    execution_record RECORD;
BEGIN
    SELECT actor_id, dispatch_state, command_definition_id INTO execution_record
    FROM operational_command_executions
    WHERE id = NEW.command_execution_id;

    IF execution_record.actor_id = NEW.approver_id THEN
        RAISE EXCEPTION
            'execution % cannot be approved by the operator issuing it', NEW.command_execution_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF execution_record.dispatch_state <> 'PENDING_APPROVAL' THEN
        RAISE EXCEPTION
            'execution % is %; approving it now would be approving something already decided',
            NEW.command_execution_id, execution_record.dispatch_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    PERFORM 1 FROM operational_command_definitions
    WHERE id = execution_record.command_definition_id
      AND NEW.approval_role = ANY (required_approval_roles);

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'execution % does not require a % approval', NEW.command_execution_id, NEW.approval_role
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_command_approvals_integrity
    BEFORE INSERT ON operational_command_approvals
    FOR EACH ROW
    EXECUTE FUNCTION operational_command_approval_integrity();
--rollback DROP TRIGGER trg_command_approvals_integrity ON operational_command_approvals;
--rollback DROP FUNCTION operational_command_approval_integrity();
--rollback DROP TRIGGER trg_command_executions_integrity ON operational_command_executions;
--rollback DROP FUNCTION operational_command_execution_integrity();

--changeset ninggiangboy:032-30-bulk-export-integrity splitStatements:false
-- A bulk read of production is approved before it happens, retrieved only while it is live, and
-- counted. The counter is maintained here rather than by the caller because the number of times a
-- copy of the marketplace was pulled down is exactly the figure whoever pulled it has the least
-- interest in being accurate.
CREATE FUNCTION bulk_export_progression() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.export_state <> 'REQUESTED' THEN
            RAISE EXCEPTION
                'an export is requested before it is approved; it may not be inserted already %',
                NEW.export_state
                USING ERRCODE = 'restrict_violation';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.purpose IS DISTINCT FROM OLD.purpose
            OR NEW.data_classes IS DISTINCT FROM OLD.data_classes
            OR NEW.data_sensitivity IS DISTINCT FROM OLD.data_sensitivity
            OR NEW.query_reference IS DISTINCT FROM OLD.query_reference
            OR NEW.redaction_profile IS DISTINCT FROM OLD.redaction_profile
            OR NEW.requested_by IS DISTINCT FROM OLD.requested_by THEN
        RAISE EXCEPTION
            'export request % is fixed once raised; what was approved is which data, redacted '
            'how, for what', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.export_state = 'APPROVED' AND OLD.export_state <> 'APPROVED'
            AND OLD.export_state <> 'REQUESTED' THEN
        RAISE EXCEPTION
            'export request % is %; approval follows the request', OLD.id, OLD.export_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.export_state = 'GENERATED' AND OLD.export_state <> 'GENERATED'
            AND OLD.export_state <> 'APPROVED' THEN
        RAISE EXCEPTION
            'export request % is %; the artifact is produced after approval',
            OLD.id, OLD.export_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF OLD.export_state IN ('REJECTED', 'REVOKED')
            AND NEW.export_state IS DISTINCT FROM OLD.export_state THEN
        RAISE EXCEPTION
            'export request % is %; raise another request rather than reviving this one',
            OLD.id, OLD.export_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bulk_export_requests_progression
    BEFORE INSERT OR UPDATE ON bulk_export_requests
    FOR EACH ROW
    EXECUTE FUNCTION bulk_export_progression();

CREATE FUNCTION bulk_export_access_window() RETURNS TRIGGER AS $$
DECLARE
    export_record RECORD;
BEGIN
    SELECT export_state, generated_at, expires_at INTO export_record
    FROM bulk_export_requests
    WHERE id = NEW.bulk_export_request_id;

    IF export_record.export_state <> 'GENERATED' THEN
        RAISE EXCEPTION
            'export request % is %; there is nothing to retrieve',
            NEW.bulk_export_request_id, export_record.export_state
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.accessed_at < export_record.generated_at THEN
        RAISE EXCEPTION
            'retrieval at % precedes the artifact it claims to have read', NEW.accessed_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    -- The expiry is the whole control. An export readable after it expired is an export with no
    -- expiry, and the row saying otherwise makes it worse rather than better.
    IF NEW.accessed_at >= export_record.expires_at THEN
        RAISE EXCEPTION
            'export request % expired at %; a retrieval after that is not covered by the approval',
            NEW.bulk_export_request_id, export_record.expires_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    UPDATE bulk_export_requests
    SET access_count = access_count + 1
    WHERE id = NEW.bulk_export_request_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bulk_export_accesses_window
    BEFORE INSERT ON bulk_export_accesses
    FOR EACH ROW
    EXECUTE FUNCTION bulk_export_access_window();
--rollback DROP TRIGGER trg_bulk_export_accesses_window ON bulk_export_accesses;
--rollback DROP FUNCTION bulk_export_access_window();
--rollback DROP TRIGGER trg_bulk_export_requests_progression ON bulk_export_requests;
--rollback DROP FUNCTION bulk_export_progression();
