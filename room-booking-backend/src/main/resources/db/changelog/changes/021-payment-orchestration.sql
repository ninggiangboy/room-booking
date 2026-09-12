--liquibase formatted sql

-- Money leaves the platform's control the moment a request reaches a payment provider, and this
-- migration exists so that everything which happens after that moment is still provable.
--
-- Five things force the shape:
--
--   A timeout is not a failure. The provider may have taken the money after the platform stopped
--   waiting. So an operation that has crossed the submission fence can never be marked failed
--   without evidence, and it can never be replaced by a second operation while its outcome is
--   unknown -- that is how a guest gets charged twice. The database enforces the fence directly:
--   CANCELLED_BEFORE_SUBMISSION is only storable on a row that was never submitted.
--
--   What the guest owes, which journey they took, which side effect was requested, and what the
--   provider said are four different facts with four different lifetimes. One obligation outlives
--   many attempts, one attempt issues many operations, and one operation accumulates many
--   observations that arrive late, out of order, and sometimes twice. Collapsing them is what makes
--   retries unsafe, so they are four tables.
--
--   Ceilings on money are checked by the database, not only by the code holding the lock. Captured
--   plus reserved can never exceed what is owed, and refunded plus reserved can never exceed what
--   was captured. If application locking is ever wrong, the row still refuses.
--
--   Provider evidence is append-only. A webhook that arrives stale is stored and ignored, never
--   deleted; a refund does not rewrite its capture into a failure; a dispute does not turn a
--   successful capture into an unsuccessful one. Corrections are new rows.
--
--   Every amount is unsigned and carries an explicit operation type. An authorization, a capture,
--   a void, a refund and a chargeback are all positive numbers with different meanings, because a
--   negative number in a money column is an invitation to read it the wrong way.
--
-- Note on the provider registry: the feature document proposes a new payment_provider_accounts
-- table. Migration 013 already delivered provider_accounts -- capability-scoped, bound to a legal
-- entity and market, carrying secret aliases rather than secrets, with an effective period and a
-- maker-checker lifecycle. Building a second registry beside it would mean two answers to "which
-- merchant account is this". This migration therefore adds payment_routes, which holds only what is
-- specific to routing a payment, and points at the existing account.
--
-- Note on the legacy tables: the document's migration and backfill plan assumes payment_attempts,
-- refunds and payment_webhook_events are still live. Migration 016 retired the whole legacy stack,
-- so there is no data to backfill and no dual-write period. The names are reused here with their
-- target meanings.
--
-- Note on refund instructions: a refund is decided in the cancellation and modification domain,
-- which migration 023 delivers. refund_executions therefore carries refund_instruction_id without a
-- foreign key, and 023 adds the constraint forward -- the same way 020 closed the booking reference
-- that 018 had to leave open.

--changeset ninggiangboy:021-01-payment-routes
-- What it takes to send a payment to one merchant account: which methods and currencies it can
-- serve, what it is allowed to do, and whether it is currently accepting new work. Kept apart from
-- provider_accounts because disabling new submissions during an outage must not touch the account's
-- identity, credentials, or ownership of operations already in flight.
CREATE TABLE payment_routes (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_account_id     UUID NOT NULL,
    method_family           VARCHAR(24) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    market_code             VARCHAR(2),

    supports_authorize      BOOLEAN NOT NULL DEFAULT FALSE,
    supports_capture        BOOLEAN NOT NULL DEFAULT FALSE,
    supports_sale           BOOLEAN NOT NULL DEFAULT FALSE,
    supports_void           BOOLEAN NOT NULL DEFAULT FALSE,
    supports_refund         BOOLEAN NOT NULL DEFAULT FALSE,
    supports_partial_refund BOOLEAN NOT NULL DEFAULT FALSE,
    supports_query          BOOLEAN NOT NULL DEFAULT FALSE,
    supports_customer_action BOOLEAN NOT NULL DEFAULT FALSE,
    supports_tokenization   BOOLEAN NOT NULL DEFAULT FALSE,

    min_amount_minor        BIGINT,
    max_amount_minor        BIGINT,

    routing_priority        SMALLINT NOT NULL DEFAULT 100,
    routing_weight          SMALLINT NOT NULL DEFAULT 100,
    routing_policy_version  VARCHAR(32) NOT NULL,

    -- Separate switches on purpose. An outage that stops new collection must still let refunds,
    -- webhooks and status queries through, or the platform loses the ability to resolve the money
    -- it has already taken.
    submissions_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    refunds_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    circuit_state           VARCHAR(16) NOT NULL DEFAULT 'CLOSED',
    circuit_changed_at      TIMESTAMPTZ,
    circuit_reason          VARCHAR(255),

    effective_from          TIMESTAMPTZ NOT NULL,
    effective_until         TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_routes_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_payment_routes_scope UNIQUE (provider_account_id, method_family, currency),
    CONSTRAINT ck_payment_routes_method CHECK (
        method_family IN ('CARD', 'WALLET', 'BANK_TRANSFER', 'BANK_REDIRECT',
                          'QR_CODE', 'DIRECT_DEBIT', 'CASH')
    ),
    CONSTRAINT ck_payment_routes_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_routes_circuit CHECK (
        circuit_state IN ('CLOSED', 'HALF_OPEN', 'OPEN')
    ),
    CONSTRAINT ck_payment_routes_circuit_change CHECK (
        (circuit_state = 'CLOSED') OR circuit_changed_at IS NOT NULL
    ),
    CONSTRAINT ck_payment_routes_limits CHECK (
        (min_amount_minor IS NULL OR min_amount_minor >= 0)
        AND (max_amount_minor IS NULL OR max_amount_minor > 0)
        AND (min_amount_minor IS NULL OR max_amount_minor IS NULL
             OR max_amount_minor >= min_amount_minor)
    ),
    -- A route that can neither sell nor authorize collects nothing; it is a configuration mistake
    -- that would otherwise surface as an unexplained empty candidate set at checkout.
    CONSTRAINT ck_payment_routes_collects CHECK (supports_sale OR supports_authorize),
    -- Capture only means something after an authorization, and a partial refund only after a refund.
    CONSTRAINT ck_payment_routes_capture CHECK (supports_authorize OR NOT supports_capture),
    CONSTRAINT ck_payment_routes_partial_refund CHECK (supports_refund OR NOT supports_partial_refund),
    CONSTRAINT ck_payment_routes_ranking CHECK (routing_priority > 0 AND routing_weight >= 0),
    CONSTRAINT ck_payment_routes_span CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_payment_routes_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_routes_selection
    ON payment_routes (method_family, currency, routing_priority, effective_from DESC)
    WHERE submissions_enabled;
--rollback DROP TABLE payment_routes;

--changeset ninggiangboy:021-02-collection-obligations
-- What the guest owes, fixed at the moment the booking's terms were accepted. The client never
-- supplies an amount; it supplies a checkout, and the amount is read from here. Re-routing to
-- another provider, a second attempt, or a provider migration never changes this row's identity.
CREATE TABLE collection_obligations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    booking_id                  UUID NOT NULL,
    checkout_id                 UUID,
    financial_snapshot_id       UUID NOT NULL,
    purpose                     VARCHAR(24) NOT NULL,

    debtor_account_holder_id    UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    market_code                 VARCHAR(2) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    amount_minor                BIGINT NOT NULL,

    -- Derived from successful operations under a lock on this row. They are stored rather than
    -- recomputed because the ceiling checks below have to be enforceable by the row itself.
    captured_amount_minor       BIGINT NOT NULL DEFAULT 0,
    capture_reserved_minor      BIGINT NOT NULL DEFAULT 0,
    refunded_amount_minor       BIGINT NOT NULL DEFAULT 0,
    refund_reserved_minor       BIGINT NOT NULL DEFAULT 0,
    disputed_amount_minor       BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    capture_policy              VARCHAR(24) NOT NULL,
    capture_policy_version      VARCHAR(32) NOT NULL,
    -- An immutable snapshot of the method families policy permitted when the obligation was created.
    -- Snapshotted rather than joined, because a later policy change must not retroactively make a
    -- completed collection look unauthorised.
    allowed_method_families     JSONB NOT NULL,
    policy_bundle_id            UUID,

    due_at                      TIMESTAMPTZ NOT NULL,
    expires_at                  TIMESTAMPTZ,
    settled_at                  TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    cancellation_reason         VARCHAR(64),

    correlation_id              VARCHAR(64) NOT NULL,
    created_by_actor_type       VARCHAR(24) NOT NULL,
    created_by_actor_id         UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_collection_obligations_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_collection_obligations_checkout FOREIGN KEY (checkout_id)
        REFERENCES booking_checkouts (id),
    CONSTRAINT fk_collection_obligations_snapshot FOREIGN KEY (financial_snapshot_id)
        REFERENCES booking_financial_snapshots (id),
    CONSTRAINT fk_collection_obligations_debtor FOREIGN KEY (debtor_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_collection_obligations_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_collection_obligations_bundle FOREIGN KEY (policy_bundle_id)
        REFERENCES market_policy_bundles (id),
    CONSTRAINT uk_collection_obligations_public_id UNIQUE (public_id),
    -- One obligation per booking and purpose. A booking gets a second obligation only by declaring a
    -- different purpose, which is what stops a retry from quietly doubling what the guest owes.
    CONSTRAINT uk_collection_obligations_purpose UNIQUE (booking_id, purpose, financial_snapshot_id),
    CONSTRAINT ck_collection_obligations_purpose CHECK (
        purpose IN ('BOOKING_TOTAL', 'DEPOSIT', 'BALANCE', 'MODIFICATION_DELTA', 'DAMAGE_CLAIM')
    ),
    CONSTRAINT ck_collection_obligations_state CHECK (
        state IN ('OPEN', 'ACTION_REQUIRED', 'PROCESSING', 'AUTHORIZED', 'PARTIALLY_PAID',
                  'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'VOIDED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_collection_obligations_capture_policy CHECK (
        capture_policy IN ('SALE', 'AUTHORIZE_THEN_CAPTURE', 'MANUAL_CAPTURE')
    ),
    CONSTRAINT ck_collection_obligations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_collection_obligations_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_collection_obligations_unsigned CHECK (
        captured_amount_minor >= 0 AND capture_reserved_minor >= 0
            AND refunded_amount_minor >= 0 AND refund_reserved_minor >= 0
            AND disputed_amount_minor >= 0
    ),
    -- The two ceilings that carry real money. Nothing may be captured beyond what is owed, and
    -- nothing may be refunded beyond what was captured -- including amounts merely reserved by an
    -- in-flight operation, which is what stops two concurrent partial refunds from overdrawing.
    CONSTRAINT ck_collection_obligations_capture_ceiling CHECK (
        captured_amount_minor + capture_reserved_minor <= amount_minor
    ),
    CONSTRAINT ck_collection_obligations_refund_ceiling CHECK (
        refunded_amount_minor + refund_reserved_minor <= captured_amount_minor
    ),
    CONSTRAINT ck_collection_obligations_dispute_ceiling CHECK (
        disputed_amount_minor <= captured_amount_minor
    ),
    -- A refund does not reopen a satisfied obligation, so PAID stays reachable only by having
    -- collected the whole amount, and the refunded states say so about themselves.
    CONSTRAINT ck_collection_obligations_paid CHECK (
        state <> 'PAID' OR captured_amount_minor = amount_minor
    ),
    CONSTRAINT ck_collection_obligations_refund_state CHECK (
        state NOT IN ('PARTIALLY_REFUNDED', 'REFUNDED') OR refunded_amount_minor > 0
    ),
    -- settled_at records when collection completed, and a later refund does not un-record it.
    -- Requiring both directions would force the row to forget that the money was ever collected the
    -- moment any of it was returned, which is exactly the fact a refund argument turns on.
    CONSTRAINT ck_collection_obligations_settlement CHECK (
        (state <> 'PAID' OR settled_at IS NOT NULL)
        AND (settled_at IS NULL OR state IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'))
    ),
    CONSTRAINT ck_collection_obligations_cancellation CHECK (
        (state IN ('CANCELLED', 'EXPIRED')) = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_collection_obligations_cancellation_reason CHECK (
        cancelled_at IS NULL OR cancellation_reason IS NOT NULL
    ),
    CONSTRAINT ck_collection_obligations_expiry CHECK (
        expires_at IS NULL OR expires_at >= due_at
    ),
    CONSTRAINT ck_collection_obligations_methods CHECK (
        jsonb_typeof(allowed_method_families) = 'array' AND allowed_method_families <> '[]'::jsonb
    ),
    CONSTRAINT ck_collection_obligations_actor CHECK (
        created_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER')
    ),
    CONSTRAINT ck_collection_obligations_version CHECK (version >= 0)
);

CREATE INDEX idx_collection_obligations_booking
    ON collection_obligations (booking_id, created_at DESC);
CREATE INDEX idx_collection_obligations_debtor
    ON collection_obligations (debtor_account_holder_id, created_at DESC);
-- Collecting what is due, and expiring what was never collected. The predicate names states rather
-- than comparing to now(), so the decision instant is bound by the query.
CREATE INDEX idx_collection_obligations_due ON collection_obligations (due_at)
    WHERE state IN ('OPEN', 'ACTION_REQUIRED', 'PROCESSING', 'AUTHORIZED', 'PARTIALLY_PAID');
--rollback DROP TABLE collection_obligations;

--changeset ninggiangboy:021-03-collection-schedule-items
-- Deposits, pay-now amounts and later balances as separate due components. An installment plan is
-- not three attempts against one total: each component has its own due date, its own state and its
-- own attempts, and a failed balance years later must not erase the confirmation the deposit bought.
-- Modifications add a replacement schedule version rather than rewriting a satisfied item.
CREATE TABLE collection_schedule_items (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obligation_id           UUID NOT NULL,
    schedule_version        INTEGER NOT NULL DEFAULT 1,
    sequence_number         SMALLINT NOT NULL,
    component_type          VARCHAR(16) NOT NULL,

    currency                VARCHAR(3) NOT NULL,
    amount_minor            BIGINT NOT NULL,
    satisfied_amount_minor  BIGINT NOT NULL DEFAULT 0,

    due_at                  TIMESTAMPTZ NOT NULL,
    -- A guest is told "due on 3 March", not an instant. Both are stored: the civil date and zone the
    -- guest was shown, and the instant the platform actually acts on.
    due_local_date          DATE,
    due_timezone            VARCHAR(64),

    confirmation_condition  VARCHAR(40) NOT NULL,
    state                   VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    attempt_count           INTEGER NOT NULL DEFAULT 0,
    next_attempt_at         TIMESTAMPTZ,
    final_deadline_at       TIMESTAMPTZ,
    retry_policy_version    VARCHAR(32),
    default_policy_version  VARCHAR(32),

    replaces_item_id        UUID,
    satisfied_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_collection_schedule_items_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_schedule_items_replaces FOREIGN KEY (replaces_item_id)
        REFERENCES collection_schedule_items (id),
    CONSTRAINT uk_collection_schedule_items_sequence
        UNIQUE (obligation_id, schedule_version, sequence_number),
    CONSTRAINT ck_collection_schedule_items_component CHECK (
        component_type IN ('PAY_NOW', 'DEPOSIT', 'BALANCE', 'INSTALLMENT')
    ),
    CONSTRAINT ck_collection_schedule_items_state CHECK (
        state IN ('OPEN', 'PROCESSING', 'SATISFIED', 'WAIVED', 'DEFAULTED', 'CANCELLED')
    ),
    CONSTRAINT ck_collection_schedule_items_condition CHECK (
        confirmation_condition IN ('FULL_AMOUNT_CAPTURED', 'THIS_COMPONENT_CAPTURED',
                                   'FULL_AMOUNT_AUTHORIZED', 'MANDATE_VALIDATED',
                                   'DELAYED_PAYMENT_ACCEPTED', 'NONE')
    ),
    CONSTRAINT ck_collection_schedule_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_collection_schedule_items_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_collection_schedule_items_satisfied_amount CHECK (
        satisfied_amount_minor >= 0 AND satisfied_amount_minor <= amount_minor
    ),
    CONSTRAINT ck_collection_schedule_items_satisfied CHECK (
        (state = 'SATISFIED') = (satisfied_at IS NOT NULL)
    ),
    CONSTRAINT ck_collection_schedule_items_satisfied_full CHECK (
        state <> 'SATISFIED' OR satisfied_amount_minor = amount_minor
    ),
    -- A local due date without its zone is not a date anybody can act on.
    CONSTRAINT ck_collection_schedule_items_local_due CHECK (
        (due_local_date IS NULL) = (due_timezone IS NULL)
    ),
    CONSTRAINT ck_collection_schedule_items_deadline CHECK (
        final_deadline_at IS NULL OR final_deadline_at >= due_at
    ),
    CONSTRAINT ck_collection_schedule_items_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_collection_schedule_items_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_collection_schedule_items_schedule_version CHECK (schedule_version > 0),
    CONSTRAINT ck_collection_schedule_items_version CHECK (version >= 0)
);

CREATE INDEX idx_collection_schedule_items_obligation
    ON collection_schedule_items (obligation_id, schedule_version, sequence_number);
-- The dunning worker's claim query: components whose next attempt is due. Workers take them with
-- FOR UPDATE SKIP LOCKED in bounded batches.
CREATE INDEX idx_collection_schedule_items_due ON collection_schedule_items (next_attempt_at)
    WHERE state IN ('OPEN', 'PROCESSING');
--rollback DROP TABLE collection_schedule_items;

--changeset ninggiangboy:021-04-collection-schedule-sum splitStatements:false
-- A schedule whose components do not add up to the obligation is a promise to collect the wrong
-- amount, and it is invisible until the last component is due. No row-level CHECK can see across
-- rows, so the rule is a deferred constraint trigger: a schedule is inserted as several statements
-- and only has to balance by the time the transaction commits.
CREATE FUNCTION collection_schedule_items_validate_sum() RETURNS TRIGGER AS $$
DECLARE
    target_obligation UUID;
    target_version    INTEGER;
    scheduled_total   BIGINT;
    obligated_total   BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        target_obligation := OLD.obligation_id;
        target_version := OLD.schedule_version;
    ELSE
        target_obligation := NEW.obligation_id;
        target_version := NEW.schedule_version;
    END IF;

    SELECT amount_minor INTO obligated_total
        FROM collection_obligations
        WHERE id = target_obligation;

    -- The obligation went away in the same transaction, which a cascade delete does. There is then
    -- no schedule left to balance.
    IF obligated_total IS NULL THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(sum(amount_minor), 0) INTO scheduled_total
        FROM collection_schedule_items
        WHERE obligation_id = target_obligation
          AND schedule_version = target_version
          AND state <> 'CANCELLED';

    IF scheduled_total <> obligated_total THEN
        RAISE EXCEPTION
            'collection schedule version % for obligation % sums to % but the obligation is %',
            target_version, target_obligation, scheduled_total, obligated_total
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_collection_schedule_items_sum
    AFTER INSERT OR UPDATE OR DELETE ON collection_schedule_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION collection_schedule_items_validate_sum();
--rollback DROP TRIGGER trg_collection_schedule_items_sum ON collection_schedule_items;
--rollback DROP FUNCTION collection_schedule_items_validate_sum();

--changeset ninggiangboy:021-05-payment-method-references
-- A pointer to a credential the platform deliberately does not hold. Everything here is either an
-- opaque provider token or display metadata a support agent may read aloud. The column that could
-- hold a card number is four characters wide and checked to be four digits, so it cannot.
CREATE TABLE payment_method_references (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_account_holder_id     UUID NOT NULL,
    provider_account_id         UUID NOT NULL,
    provider_token              VARCHAR(255) NOT NULL,
    provider_customer_reference VARCHAR(255),

    method_family               VARCHAR(24) NOT NULL,
    brand                       VARCHAR(48),
    funding_type                VARCHAR(16),
    masked_suffix               CHAR(4),
    expiry_month                SMALLINT,
    expiry_year                 SMALLINT,
    billing_country             VARCHAR(2),

    reusable                    BOOLEAN NOT NULL DEFAULT FALSE,
    mandate_reference           VARCHAR(255),
    consent_reference           VARCHAR(255),
    consent_recorded_at         TIMESTAMPTZ,

    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    retention_category          VARCHAR(32) NOT NULL,
    last_used_at                TIMESTAMPTZ,
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_method_references_owner FOREIGN KEY (owner_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_payment_method_references_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    -- A token belongs to one provider account and is not portable to another. The uniqueness says so.
    CONSTRAINT uk_payment_method_references_token UNIQUE (provider_account_id, provider_token),
    CONSTRAINT ck_payment_method_references_method CHECK (
        method_family IN ('CARD', 'WALLET', 'BANK_TRANSFER', 'BANK_REDIRECT',
                          'QR_CODE', 'DIRECT_DEBIT', 'CASH')
    ),
    CONSTRAINT ck_payment_method_references_funding CHECK (
        funding_type IS NULL OR funding_type IN ('CREDIT', 'DEBIT', 'PREPAID', 'UNKNOWN')
    ),
    CONSTRAINT ck_payment_method_references_state CHECK (
        state IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'REPLACED')
    ),
    -- Four digits and nothing else. A PAN does not fit in this column by construction.
    CONSTRAINT ck_payment_method_references_suffix CHECK (
        masked_suffix IS NULL OR masked_suffix ~ '^[0-9]{4}$'
    ),
    CONSTRAINT ck_payment_method_references_expiry CHECK (
        (expiry_month IS NULL) = (expiry_year IS NULL)
        AND (expiry_month IS NULL OR expiry_month BETWEEN 1 AND 12)
        AND (expiry_year IS NULL OR expiry_year BETWEEN 2000 AND 2100)
    ),
    CONSTRAINT ck_payment_method_references_country CHECK (
        billing_country IS NULL OR billing_country ~ '^[A-Z]{2}$'
    ),
    -- Reuse needs recorded consent. Without it the platform cannot show why it was allowed to charge
    -- the guest a second time.
    CONSTRAINT ck_payment_method_references_consent CHECK (
        NOT reusable OR consent_recorded_at IS NOT NULL
    ),
    CONSTRAINT ck_payment_method_references_revocation CHECK (
        (state = 'REVOKED') = (revoked_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_method_references_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_method_references_owner
    ON payment_method_references (owner_account_holder_id, created_at DESC)
    WHERE state = 'ACTIVE';
--rollback DROP TABLE payment_method_references;

--changeset ninggiangboy:021-06-payment-attempts
-- One guest journey toward satisfying an obligation: this method, this provider, this sequence of
-- customer actions. Reloading the page is not a new attempt; a new instrument is. The state here is
-- a projection for the UI -- the operations are what actually happened.
CREATE TABLE payment_attempts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obligation_id               UUID NOT NULL,
    schedule_item_id            UUID,
    attempt_number              SMALLINT NOT NULL,

    provider_account_id         UUID NOT NULL,
    payment_route_id            UUID,
    method_reference_id         UUID,
    method_family               VARCHAR(24) NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    requested_amount_minor      BIGINT NOT NULL,
    authorized_amount_minor     BIGINT NOT NULL DEFAULT 0,
    captured_amount_minor       BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    -- Two reasons, deliberately. The guest sees a safe one; operations and support see the
    -- provider's, which may carry issuer detail that must never reach a browser.
    failure_category            VARCHAR(32),
    guest_reason_code           VARCHAR(48),
    restricted_failure_code     VARCHAR(64),

    action_type                 VARCHAR(24),
    action_deadline_at          TIMESTAMPTZ,

    routing_policy_version      VARCHAR(32),
    risk_decision_id            UUID,
    risk_policy_version         VARCHAR(32),

    idempotency_record_id       UUID,
    request_digest              CHAR(64) NOT NULL,
    correlation_id              VARCHAR(64) NOT NULL,
    actor_type                  VARCHAR(24) NOT NULL,
    actor_id                    UUID,
    source_channel              VARCHAR(32) NOT NULL DEFAULT 'WEB',

    created_at                  TIMESTAMPTZ NOT NULL,
    terminal_at                 TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_attempts_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_payment_attempts_schedule_item FOREIGN KEY (schedule_item_id)
        REFERENCES collection_schedule_items (id),
    CONSTRAINT fk_payment_attempts_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_payment_attempts_route FOREIGN KEY (payment_route_id)
        REFERENCES payment_routes (id),
    CONSTRAINT fk_payment_attempts_method FOREIGN KEY (method_reference_id)
        REFERENCES payment_method_references (id),
    CONSTRAINT fk_payment_attempts_idempotency FOREIGN KEY (idempotency_record_id)
        REFERENCES command_idempotency_records (id),
    CONSTRAINT uk_payment_attempts_number UNIQUE (obligation_id, attempt_number),
    CONSTRAINT ck_payment_attempts_number CHECK (attempt_number > 0),
    CONSTRAINT ck_payment_attempts_method CHECK (
        method_family IN ('CARD', 'WALLET', 'BANK_TRANSFER', 'BANK_REDIRECT',
                          'QR_CODE', 'DIRECT_DEBIT', 'CASH')
    ),
    CONSTRAINT ck_payment_attempts_state CHECK (
        state IN ('CREATED', 'SUBMITTING', 'REQUIRES_ACTION', 'PROCESSING', 'AUTHORIZED',
                  'PARTIALLY_CAPTURED', 'CAPTURED', 'PARTIALLY_REFUNDED', 'REFUNDED',
                  'VOIDED', 'AUTHORIZATION_EXPIRED', 'FAILED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_payment_attempts_failure_category CHECK (
        failure_category IS NULL OR failure_category IN (
            'PAYMENT_METHOD_DECLINED', 'AUTHENTICATION_REQUIRED', 'AUTHENTICATION_FAILED',
            'INSUFFICIENT_FUNDS', 'PAYMENT_METHOD_INVALID', 'RISK_BLOCKED',
            'PROVIDER_TEMPORARY_FAILURE', 'OUTCOME_UNKNOWN', 'CONFIGURATION_ERROR',
            'AMOUNT_OR_CURRENCY_MISMATCH')
    ),
    CONSTRAINT ck_payment_attempts_action CHECK (
        action_type IS NULL OR action_type IN (
            'REDIRECT', 'THREE_D_SECURE', 'WALLET_APPROVAL', 'QR_SCAN', 'MANDATE_SETUP')
    ),
    -- An attempt that asks the guest for an action but names no deadline is an attempt nobody will
    -- ever time out, and it holds inventory while it waits.
    CONSTRAINT ck_payment_attempts_action_deadline CHECK (
        state <> 'REQUIRES_ACTION' OR (action_type IS NOT NULL AND action_deadline_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_attempts_failure CHECK (
        state <> 'FAILED' OR failure_category IS NOT NULL
    ),
    CONSTRAINT ck_payment_attempts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_attempts_amount CHECK (requested_amount_minor > 0),
    CONSTRAINT ck_payment_attempts_progress CHECK (
        authorized_amount_minor >= 0 AND captured_amount_minor >= 0
            AND captured_amount_minor <= requested_amount_minor
    ),
    CONSTRAINT ck_payment_attempts_captured_state CHECK (
        state <> 'CAPTURED' OR captured_amount_minor = requested_amount_minor
    ),
    CONSTRAINT ck_payment_attempts_digest CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_attempts_actor CHECK (
        actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER')
    ),
    CONSTRAINT ck_payment_attempts_terminal CHECK (
        (state IN ('CAPTURED', 'REFUNDED', 'VOIDED', 'AUTHORIZATION_EXPIRED',
                   'FAILED', 'CANCELLED', 'EXPIRED'))
        OR terminal_at IS NULL
    ),
    CONSTRAINT ck_payment_attempts_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_attempts_obligation
    ON payment_attempts (obligation_id, attempt_number DESC);
CREATE INDEX idx_payment_attempts_pending_action ON payment_attempts (action_deadline_at)
    WHERE state = 'REQUIRES_ACTION';
--rollback DROP TABLE payment_attempts;

--changeset ninggiangboy:021-07-payment-operations
-- One external side effect: authorize, capture, sale, void, refund, or query. This is the auditable
-- record of what was actually asked of a provider. A transport retry reuses this row and its
-- provider request key; a new financial intent gets a new row and a new key.
CREATE TABLE payment_operations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id                  UUID,
    obligation_id               UUID NOT NULL,
    parent_operation_id         UUID,
    operation_type              VARCHAR(16) NOT NULL,

    currency                    VARCHAR(3),
    amount_minor                BIGINT,

    idempotency_scope           VARCHAR(64) NOT NULL,
    idempotency_key             VARCHAR(128) NOT NULL,
    request_hash                CHAR(64) NOT NULL,

    provider_account_id         UUID NOT NULL,
    provider_request_key        VARCHAR(128) NOT NULL,
    provider_operation_ref      VARCHAR(255),
    provider_object_ref         VARCHAR(255),

    state                       VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    state_changed_at            TIMESTAMPTZ NOT NULL,
    submission_count            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at             TIMESTAMPTZ,
    deadline_at                 TIMESTAMPTZ,

    -- A worker claims an operation by taking the lease and bumping the fencing token. A late worker
    -- holding a stale token cannot submit, which is what stops two workers paying the same provider
    -- request twice.
    lease_owner                 VARCHAR(128),
    lease_expires_at            TIMESTAMPTZ,
    fencing_token               BIGINT NOT NULL DEFAULT 0,

    result_code                 VARCHAR(64),
    failure_category            VARCHAR(32),
    restricted_failure_code     VARCHAR(64),

    correlation_id              VARCHAR(64) NOT NULL,
    causation_id                VARCHAR(64),
    actor_type                  VARCHAR(24) NOT NULL,
    actor_id                    UUID,

    created_at                  TIMESTAMPTZ NOT NULL,
    submitted_at                TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_operations_attempt FOREIGN KEY (attempt_id)
        REFERENCES payment_attempts (id),
    CONSTRAINT fk_payment_operations_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_payment_operations_parent FOREIGN KEY (parent_operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_payment_operations_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_payment_operations_idempotency UNIQUE (idempotency_scope, idempotency_key),
    -- The key the provider deduplicates on. Unique per account, so a retry can never become a
    -- second charge.
    CONSTRAINT uk_payment_operations_provider_key
        UNIQUE (provider_account_id, provider_request_key),
    CONSTRAINT ck_payment_operations_type CHECK (
        operation_type IN ('AUTHORIZE', 'CAPTURE', 'SALE', 'VOID', 'REFUND', 'QUERY')
    ),
    CONSTRAINT ck_payment_operations_state CHECK (
        state IN ('PLANNED', 'READY_TO_SUBMIT', 'SUBMITTING', 'PENDING', 'REQUIRES_ACTION',
                  'SUCCEEDED', 'FAILED', 'UNKNOWN', 'EXPIRED', 'CANCELLED_BEFORE_SUBMISSION')
    ),
    -- Every operation that moves money carries a positive amount and a currency; a status query
    -- carries neither. Money is never stored negative -- the type says what direction it moves.
    CONSTRAINT ck_payment_operations_monetary CHECK (
        (operation_type = 'QUERY' AND amount_minor IS NULL AND currency IS NULL)
        OR (operation_type <> 'QUERY' AND amount_minor > 0 AND currency ~ '^[A-Z]{3}$')
    ),
    -- The submission fence, as a constraint. Once a request has crossed to the provider it can only
    -- be resolved by evidence; cancelling it locally would be a claim the platform cannot support.
    CONSTRAINT ck_payment_operations_fence CHECK (
        state <> 'CANCELLED_BEFORE_SUBMISSION' OR submitted_at IS NULL
    ),
    CONSTRAINT ck_payment_operations_submitted CHECK (
        state NOT IN ('SUBMITTING', 'PENDING', 'REQUIRES_ACTION', 'SUCCEEDED', 'UNKNOWN')
        OR submitted_at IS NOT NULL
    ),
    CONSTRAINT ck_payment_operations_resolved CHECK (
        (state IN ('SUCCEEDED', 'FAILED', 'EXPIRED', 'CANCELLED_BEFORE_SUBMISSION'))
        = (resolved_at IS NOT NULL)
    ),
    -- FAILED is a claim that the effect did not happen. It needs a reason; a timeout is UNKNOWN.
    CONSTRAINT ck_payment_operations_failure CHECK (
        state <> 'FAILED' OR failure_category IS NOT NULL
    ),
    CONSTRAINT ck_payment_operations_failure_category CHECK (
        failure_category IS NULL OR failure_category IN (
            'PAYMENT_METHOD_DECLINED', 'AUTHENTICATION_REQUIRED', 'AUTHENTICATION_FAILED',
            'INSUFFICIENT_FUNDS', 'PAYMENT_METHOD_INVALID', 'RISK_BLOCKED',
            'PROVIDER_TEMPORARY_FAILURE', 'OUTCOME_UNKNOWN', 'CONFIGURATION_ERROR',
            'AMOUNT_OR_CURRENCY_MISMATCH')
    ),
    -- A capture belongs to its authorization and a child refund to its parent. A sale has no parent.
    CONSTRAINT ck_payment_operations_parent CHECK (
        operation_type NOT IN ('CAPTURE', 'VOID') OR parent_operation_id IS NOT NULL
    ),
    CONSTRAINT ck_payment_operations_submission_count CHECK (submission_count >= 0),
    CONSTRAINT ck_payment_operations_submission_agrees CHECK (
        (submission_count = 0) = (submitted_at IS NULL)
    ),
    CONSTRAINT ck_payment_operations_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_payment_operations_fencing_token CHECK (fencing_token >= 0),
    CONSTRAINT ck_payment_operations_digest CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_operations_actor CHECK (
        actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER')
    ),
    CONSTRAINT ck_payment_operations_version CHECK (version >= 0)
);

-- One provider reference maps to one operation per account, where the provider guarantees it. The
-- index is partial because the reference does not exist until the provider answers.
CREATE UNIQUE INDEX uk_payment_operations_provider_ref
    ON payment_operations (provider_account_id, provider_operation_ref)
    WHERE provider_operation_ref IS NOT NULL;

CREATE INDEX idx_payment_operations_attempt ON payment_operations (attempt_id, created_at);
CREATE INDEX idx_payment_operations_obligation ON payment_operations (obligation_id, created_at);
-- The executor's work queue and the recovery worker's queue. Both name states instead of comparing
-- against now(), so the decision instant stays in the query.
CREATE INDEX idx_payment_operations_ready ON payment_operations (next_attempt_at)
    WHERE state IN ('READY_TO_SUBMIT', 'PENDING', 'REQUIRES_ACTION');
CREATE INDEX idx_payment_operations_unresolved ON payment_operations (submitted_at)
    WHERE state IN ('UNKNOWN', 'PENDING', 'SUBMITTING');
CREATE INDEX idx_payment_operations_stalled_lease ON payment_operations (lease_expires_at)
    WHERE state = 'SUBMITTING';
--rollback DROP TABLE payment_operations;

--changeset ninggiangboy:021-08-payment-operation-observations
-- Every verified thing a provider ever said, kept forever. The current state of an operation is a
-- reduction over these rows, so a stale webhook is stored and marked ignored rather than dropped --
-- otherwise the reduction is not reproducible and a disagreement about money has no record to
-- settle it. operation_id is nullable because an orphan provider object is evidence too, and
-- guessing which operation it belongs to is exactly what must not happen.
CREATE TABLE payment_operation_observations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_id                UUID,
    provider_account_id         UUID NOT NULL,
    source                      VARCHAR(24) NOT NULL,

    provider_event_id           VARCHAR(255),
    provider_object_ref         VARCHAR(255),
    provider_status             VARCHAR(64) NOT NULL,
    normalized_state            VARCHAR(32) NOT NULL,

    currency                    VARCHAR(3),
    amount_minor                BIGINT,

    provider_occurred_at        TIMESTAMPTZ,
    received_at                 TIMESTAMPTZ NOT NULL,

    payload_digest              CHAR(64) NOT NULL,
    payload_reference           VARCHAR(255),
    verification_method         VARCHAR(32) NOT NULL,
    verification_key_version    VARCHAR(32),

    reducer_outcome             VARCHAR(16) NOT NULL,
    quarantine_reason           VARCHAR(64),
    CONSTRAINT fk_payment_operation_observations_operation FOREIGN KEY (operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_payment_operation_observations_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT ck_payment_operation_observations_source CHECK (
        source IN ('API_RESPONSE', 'WEBHOOK', 'QUERY', 'RECONCILIATION_IMPORT')
    ),
    CONSTRAINT ck_payment_operation_observations_normalized CHECK (
        normalized_state IN ('AUTHORIZED', 'CAPTURED', 'PARTIALLY_CAPTURED', 'VOIDED',
                             'REFUNDED', 'PARTIALLY_REFUNDED', 'DECLINED', 'FAILED',
                             'PENDING', 'REQUIRES_ACTION', 'EXPIRED', 'DISPUTED', 'UNKNOWN')
    ),
    CONSTRAINT ck_payment_operation_observations_outcome CHECK (
        reducer_outcome IN ('APPLIED', 'IGNORED_STALE', 'QUARANTINED', 'UNMAPPED')
    ),
    CONSTRAINT ck_payment_operation_observations_quarantine CHECK (
        (reducer_outcome = 'QUARANTINED') = (quarantine_reason IS NOT NULL)
    ),
    -- An observation that could not be mapped to an operation is exactly the orphan case, and it is
    -- the only one allowed to carry no operation.
    CONSTRAINT ck_payment_operation_observations_mapping CHECK (
        (operation_id IS NOT NULL) OR reducer_outcome IN ('UNMAPPED', 'QUARANTINED')
    ),
    CONSTRAINT ck_payment_operation_observations_money CHECK (
        (amount_minor IS NULL) = (currency IS NULL)
        AND (amount_minor IS NULL OR amount_minor >= 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
    ),
    -- A webhook is only evidence once its signature verified. An unverified body is a security
    -- record elsewhere, never a provider fact.
    CONSTRAINT ck_payment_operation_observations_verification CHECK (
        verification_method IN ('WEBHOOK_SIGNATURE', 'AUTHENTICATED_API', 'RESTRICTED_IMPORT')
    ),
    CONSTRAINT ck_payment_operation_observations_signature CHECK (
        source <> 'WEBHOOK'
        OR (verification_method = 'WEBHOOK_SIGNATURE' AND verification_key_version IS NOT NULL)
    ),
    CONSTRAINT ck_payment_operation_observations_digest CHECK (
        payload_digest ~ '^[0-9a-f]{64}$'
    )
);

-- One provider event is stored once per account, however many times it is delivered. Partial,
-- because a query result and an API response have no event id of their own.
CREATE UNIQUE INDEX uk_payment_operation_observations_event
    ON payment_operation_observations (provider_account_id, provider_event_id)
    WHERE provider_event_id IS NOT NULL;

-- The same query answered twice produces the same digest, and storing it twice would double-count
-- nothing but would make the evidence set larger than the evidence.
CREATE UNIQUE INDEX uk_payment_operation_observations_digest
    ON payment_operation_observations (operation_id, source, payload_digest)
    WHERE operation_id IS NOT NULL;

CREATE INDEX idx_payment_operation_observations_operation
    ON payment_operation_observations (operation_id, received_at);
CREATE INDEX idx_payment_operation_observations_object
    ON payment_operation_observations (provider_account_id, provider_object_ref)
    WHERE provider_object_ref IS NOT NULL;
CREATE INDEX idx_payment_operation_observations_orphans
    ON payment_operation_observations (received_at)
    WHERE reducer_outcome IN ('UNMAPPED', 'QUARANTINED');
--rollback DROP TABLE payment_operation_observations;

--changeset ninggiangboy:021-09-payment-webhook-deliveries
-- The ingress record, written before any business effect is applied. It exists so that a provider
-- can be acknowledged quickly and the work can be retried without asking the provider to send the
-- event again -- and so that a replay of an event already processed changes nothing.
CREATE TABLE payment_webhook_deliveries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_account_id         UUID NOT NULL,
    endpoint_key                VARCHAR(96) NOT NULL,
    provider_event_id           VARCHAR(255) NOT NULL,
    provider_event_type         VARCHAR(96) NOT NULL,
    provider_object_ref         VARCHAR(255),

    signature_verified          BOOLEAN NOT NULL,
    verification_key_version    VARCHAR(32),
    payload_digest              CHAR(64) NOT NULL,
    payload_reference           VARCHAR(255),

    state                       VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    attempt_count               INTEGER NOT NULL DEFAULT 0,
    lease_owner                 VARCHAR(128),
    lease_expires_at            TIMESTAMPTZ,
    next_attempt_at             TIMESTAMPTZ,
    failure_class               VARCHAR(64),
    observation_id              UUID,

    received_at                 TIMESTAMPTZ NOT NULL,
    processed_at                TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_webhook_deliveries_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_payment_webhook_deliveries_observation FOREIGN KEY (observation_id)
        REFERENCES payment_operation_observations (id),
    -- Deduplication by account and provider event id. This is the whole point of the table.
    CONSTRAINT uk_payment_webhook_deliveries_event
        UNIQUE (provider_account_id, provider_event_id),
    CONSTRAINT ck_payment_webhook_deliveries_state CHECK (
        state IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'RETRYABLE_FAILED',
                  'DEAD_LETTER', 'IGNORED_UNSUPPORTED')
    ),
    -- Only a signature-verified delivery may ever be processed into a provider fact.
    CONSTRAINT ck_payment_webhook_deliveries_verified CHECK (
        signature_verified OR state IN ('RECEIVED', 'DEAD_LETTER')
    ),
    CONSTRAINT ck_payment_webhook_deliveries_key_version CHECK (
        NOT signature_verified OR verification_key_version IS NOT NULL
    ),
    CONSTRAINT ck_payment_webhook_deliveries_processing CHECK (
        (state IN ('PROCESSED', 'IGNORED_UNSUPPORTED')) = (processed_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_webhook_deliveries_failure CHECK (
        state NOT IN ('RETRYABLE_FAILED', 'DEAD_LETTER') OR failure_class IS NOT NULL
    ),
    CONSTRAINT ck_payment_webhook_deliveries_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_payment_webhook_deliveries_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_payment_webhook_deliveries_digest CHECK (payload_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payment_webhook_deliveries_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_webhook_deliveries_backlog
    ON payment_webhook_deliveries (next_attempt_at)
    WHERE state IN ('RECEIVED', 'RETRYABLE_FAILED');
CREATE INDEX idx_payment_webhook_deliveries_stalled_lease
    ON payment_webhook_deliveries (lease_expires_at)
    WHERE state = 'PROCESSING';
CREATE INDEX idx_payment_webhook_deliveries_object
    ON payment_webhook_deliveries (provider_account_id, provider_object_ref)
    WHERE provider_object_ref IS NOT NULL;
--rollback DROP TABLE payment_webhook_deliveries;

--changeset ninggiangboy:021-10-refund-executions
-- Carrying out a refund somebody else decided. The entitlement, the amount, and the reason come
-- from a cancellation, modification, or remedy decision; this domain never recalculates them. The
-- instruction is unique here so that replaying the instruction cannot pay the guest twice.
CREATE TABLE refund_executions (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Deliberately no foreign key yet. The refund instruction lives in the cancellation and
    -- modification domain, which migration 023 delivers; 023 adds the constraint forward.
    refund_instruction_id           UUID NOT NULL,
    instruction_version             INTEGER NOT NULL DEFAULT 1,

    booking_id                      UUID NOT NULL,
    obligation_id                   UUID NOT NULL,
    beneficiary_account_holder_id   UUID NOT NULL,
    provider_account_id             UUID,

    currency                        VARCHAR(3) NOT NULL,
    approved_amount_minor           BIGINT NOT NULL,
    reserved_amount_minor           BIGINT NOT NULL DEFAULT 0,
    executed_amount_minor           BIGINT NOT NULL DEFAULT 0,

    state                           VARCHAR(24) NOT NULL DEFAULT 'APPROVED',
    failure_category                VARCHAR(32),
    restricted_failure_code         VARCHAR(64),

    reason_code                     VARCHAR(48) NOT NULL,
    policy_version                  VARCHAR(32),
    approved_by_actor_type          VARCHAR(24) NOT NULL,
    approved_by_actor_id            UUID,
    approved_at                     TIMESTAMPTZ NOT NULL,
    execution_deadline_at           TIMESTAMPTZ,
    completed_at                    TIMESTAMPTZ,

    idempotency_key                 VARCHAR(128) NOT NULL,
    correlation_id                  VARCHAR(64) NOT NULL,
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_executions_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_refund_executions_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_refund_executions_beneficiary FOREIGN KEY (beneficiary_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_refund_executions_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_refund_executions_instruction
        UNIQUE (refund_instruction_id, instruction_version),
    CONSTRAINT uk_refund_executions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_refund_executions_state CHECK (
        state IN ('APPROVED', 'RESERVED', 'SUBMITTING', 'PENDING', 'SUCCEEDED',
                  'PARTIALLY_SUCCEEDED', 'FAILED_RETRYABLE', 'FAILED_FINAL', 'UNKNOWN')
    ),
    CONSTRAINT ck_refund_executions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_refund_executions_amounts CHECK (
        approved_amount_minor > 0
            AND reserved_amount_minor >= 0
            AND executed_amount_minor >= 0
            AND executed_amount_minor <= approved_amount_minor
            AND reserved_amount_minor <= approved_amount_minor
    ),
    -- A refund is complete only when the whole approved amount moved. One successful child does not
    -- make a multi-capture refund whole.
    CONSTRAINT ck_refund_executions_succeeded CHECK (
        state <> 'SUCCEEDED' OR executed_amount_minor = approved_amount_minor
    ),
    CONSTRAINT ck_refund_executions_completion CHECK (
        (state IN ('SUCCEEDED', 'FAILED_FINAL')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_refund_executions_failure CHECK (
        state NOT IN ('FAILED_RETRYABLE', 'FAILED_FINAL') OR failure_category IS NOT NULL
    ),
    CONSTRAINT ck_refund_executions_failure_category CHECK (
        failure_category IS NULL OR failure_category IN (
            'PAYMENT_METHOD_DECLINED', 'PAYMENT_METHOD_INVALID', 'PROVIDER_TEMPORARY_FAILURE',
            'OUTCOME_UNKNOWN', 'CONFIGURATION_ERROR', 'AMOUNT_OR_CURRENCY_MISMATCH',
            'NO_REFUNDABLE_CAPTURE')
    ),
    CONSTRAINT ck_refund_executions_actor CHECK (
        approved_by_actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER')
    ),
    CONSTRAINT ck_refund_executions_instruction_version CHECK (instruction_version > 0),
    CONSTRAINT ck_refund_executions_version CHECK (version >= 0)
);

CREATE INDEX idx_refund_executions_booking ON refund_executions (booking_id, created_at DESC);
CREATE INDEX idx_refund_executions_obligation ON refund_executions (obligation_id, created_at DESC);
CREATE INDEX idx_refund_executions_open ON refund_executions (execution_deadline_at)
    WHERE state IN ('APPROVED', 'RESERVED', 'SUBMITTING', 'PENDING', 'FAILED_RETRYABLE', 'UNKNOWN');
--rollback DROP TABLE refund_executions;

--changeset ninggiangboy:021-11-refund-capture-allocations
-- Which capture each part of a refund comes out of. Money can only be returned through a capture
-- that actually took it, and the same capture must not fund two refunds beyond what it holds, so
-- the allocation is a row rather than an arithmetic assumption.
CREATE TABLE refund_capture_allocations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    refund_execution_id         UUID NOT NULL,
    capture_operation_id        UUID NOT NULL,
    refund_operation_id         UUID,
    sequence_number             SMALLINT NOT NULL,

    currency                    VARCHAR(3) NOT NULL,
    allocated_amount_minor      BIGINT NOT NULL,
    settled_amount_minor        BIGINT NOT NULL DEFAULT 0,
    state                       VARCHAR(16) NOT NULL DEFAULT 'RESERVED',

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refund_capture_allocations_execution FOREIGN KEY (refund_execution_id)
        REFERENCES refund_executions (id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_capture_allocations_capture FOREIGN KEY (capture_operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_refund_capture_allocations_refund FOREIGN KEY (refund_operation_id)
        REFERENCES payment_operations (id),
    -- One refund draws from a given capture once. A second draw is a second allocation row on a
    -- different refund, which is what the refundable ceiling is then computed over.
    CONSTRAINT uk_refund_capture_allocations_capture
        UNIQUE (refund_execution_id, capture_operation_id),
    CONSTRAINT uk_refund_capture_allocations_sequence
        UNIQUE (refund_execution_id, sequence_number),
    CONSTRAINT ck_refund_capture_allocations_state CHECK (
        state IN ('RESERVED', 'SUBMITTED', 'SETTLED', 'RELEASED', 'FAILED')
    ),
    CONSTRAINT ck_refund_capture_allocations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_refund_capture_allocations_amounts CHECK (
        allocated_amount_minor > 0
            AND settled_amount_minor >= 0
            AND settled_amount_minor <= allocated_amount_minor
    ),
    CONSTRAINT ck_refund_capture_allocations_settled CHECK (
        state <> 'SETTLED' OR settled_amount_minor = allocated_amount_minor
    ),
    -- A released reservation has returned its money to the refundable pool and settled nothing.
    CONSTRAINT ck_refund_capture_allocations_released CHECK (
        state <> 'RELEASED' OR settled_amount_minor = 0
    ),
    CONSTRAINT ck_refund_capture_allocations_submitted CHECK (
        state IN ('RESERVED', 'RELEASED') OR refund_operation_id IS NOT NULL
    ),
    CONSTRAINT ck_refund_capture_allocations_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_refund_capture_allocations_version CHECK (version >= 0)
);

CREATE INDEX idx_refund_capture_allocations_capture
    ON refund_capture_allocations (capture_operation_id)
    WHERE state IN ('RESERVED', 'SUBMITTED', 'SETTLED');
--rollback DROP TABLE refund_capture_allocations;

--changeset ninggiangboy:021-12-payment-disputes
-- A chargeback is not a refund, and it does not make the original capture unsuccessful. It is a
-- separate fact hanging off the capture, with its own deadline, its own outcome, and its own money.
-- Missing the response deadline loses the case by default, so the deadline is a column workers page
-- on rather than something buried in a provider payload.
CREATE TABLE payment_disputes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_account_id         UUID NOT NULL,
    provider_dispute_id         VARCHAR(255) NOT NULL,
    provider_case_reference     VARCHAR(255),

    capture_operation_id        UUID,
    obligation_id               UUID,
    booking_id                  UUID,

    dispute_type                VARCHAR(16) NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    disputed_amount_minor       BIGINT NOT NULL,
    fee_amount_minor            BIGINT NOT NULL DEFAULT 0,
    recovered_amount_minor      BIGINT NOT NULL DEFAULT 0,

    reason_category             VARCHAR(48) NOT NULL,
    provider_reason_code        VARCHAR(64),
    status                      VARCHAR(24) NOT NULL DEFAULT 'INQUIRY_OR_RETRIEVAL',

    opened_at                   TIMESTAMPTZ NOT NULL,
    respond_by                  TIMESTAMPTZ,
    submitted_at                TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_disputes_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_payment_disputes_capture FOREIGN KEY (capture_operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_payment_disputes_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_payment_disputes_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_payment_disputes_provider UNIQUE (provider_account_id, provider_dispute_id),
    CONSTRAINT ck_payment_disputes_type CHECK (
        dispute_type IN ('INQUIRY', 'RETRIEVAL', 'CHARGEBACK', 'PRE_ARBITRATION', 'ARBITRATION')
    ),
    CONSTRAINT ck_payment_disputes_status CHECK (
        status IN ('INQUIRY_OR_RETRIEVAL', 'ACTION_REQUIRED', 'EVIDENCE_SUBMITTED',
                   'UNDER_REVIEW', 'WON', 'LOST', 'ACCEPTED', 'EXPIRED')
    ),
    -- The terminal statuses are the outcome. A separate outcome column would only create a second
    -- place for the answer to live, and a chance for the two to disagree.
    CONSTRAINT ck_payment_disputes_resolution CHECK (
        (status IN ('WON', 'LOST', 'ACCEPTED', 'EXPIRED')) = (resolved_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_disputes_submission CHECK (
        status <> 'EVIDENCE_SUBMITTED' OR submitted_at IS NOT NULL
    ),
    -- A case that needs a response and has no deadline is a case that will be lost silently.
    CONSTRAINT ck_payment_disputes_deadline CHECK (
        status <> 'ACTION_REQUIRED' OR respond_by IS NOT NULL
    ),
    CONSTRAINT ck_payment_disputes_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_disputes_amounts CHECK (
        disputed_amount_minor > 0 AND fee_amount_minor >= 0
            AND recovered_amount_minor >= 0 AND recovered_amount_minor <= disputed_amount_minor
    ),
    CONSTRAINT ck_payment_disputes_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_disputes_capture ON payment_disputes (capture_operation_id);
CREATE INDEX idx_payment_disputes_booking ON payment_disputes (booking_id, opened_at DESC);
CREATE INDEX idx_payment_disputes_deadline ON payment_disputes (respond_by)
    WHERE status IN ('INQUIRY_OR_RETRIEVAL', 'ACTION_REQUIRED', 'UNDER_REVIEW');
--rollback DROP TABLE payment_disputes;

--changeset ninggiangboy:021-13-payment-dispute-events
-- Everything the provider said about a case, in the order it was learned. Append-only, because the
-- history of a case is itself evidence in the case.
CREATE TABLE payment_dispute_events (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id                  UUID NOT NULL,
    sequence_number             INTEGER NOT NULL,
    event_type                  VARCHAR(48) NOT NULL,
    from_status                 VARCHAR(24),
    to_status                   VARCHAR(24),
    currency                    VARCHAR(3),
    amount_minor                BIGINT,
    provider_event_id           VARCHAR(255),
    payload_digest              CHAR(64),
    occurred_at                 TIMESTAMPTZ NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL,
    actor_type                  VARCHAR(24) NOT NULL,
    actor_id                    UUID,
    note                        VARCHAR(512),
    -- Deliberately not ON DELETE CASCADE: this table is append-only, so a cascade would be a promise
    -- the row trigger refuses to keep.
    CONSTRAINT fk_payment_dispute_events_dispute FOREIGN KEY (dispute_id)
        REFERENCES payment_disputes (id),
    CONSTRAINT uk_payment_dispute_events_sequence UNIQUE (dispute_id, sequence_number),
    CONSTRAINT ck_payment_dispute_events_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_payment_dispute_events_money CHECK (
        (amount_minor IS NULL) = (currency IS NULL)
        AND (amount_minor IS NULL OR amount_minor >= 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_payment_dispute_events_digest CHECK (
        payload_digest IS NULL OR payload_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_payment_dispute_events_actor CHECK (
        actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    )
);

CREATE INDEX idx_payment_dispute_events_dispute
    ON payment_dispute_events (dispute_id, sequence_number DESC);
--rollback DROP TABLE payment_dispute_events;

--changeset ninggiangboy:021-14-payment-dispute-evidence
-- The manifest of what was sent to the provider to defend a case. Once submitted it cannot change:
-- a representment the platform cannot reproduce exactly is not a defence it can stand behind.
CREATE TABLE payment_dispute_evidence (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dispute_id                  UUID NOT NULL,
    evidence_type               VARCHAR(48) NOT NULL,
    display_name                VARCHAR(255) NOT NULL,
    content_digest              CHAR(64) NOT NULL,
    storage_reference           VARCHAR(255),
    content_type                VARCHAR(96),
    byte_size                   BIGINT,
    scan_state                  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    redaction_applied           BOOLEAN NOT NULL DEFAULT FALSE,

    prepared_by_actor_type      VARCHAR(24) NOT NULL,
    prepared_by_actor_id        UUID,
    approved_by_actor_id        UUID,
    approved_at                 TIMESTAMPTZ,
    submitted_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    -- Deliberately not ON DELETE CASCADE: submitted evidence is frozen by a row trigger, so a
    -- cascade would be a promise the trigger refuses to keep.
    CONSTRAINT fk_payment_dispute_evidence_dispute FOREIGN KEY (dispute_id)
        REFERENCES payment_disputes (id),
    CONSTRAINT uk_payment_dispute_evidence_digest UNIQUE (dispute_id, content_digest),
    CONSTRAINT ck_payment_dispute_evidence_scan CHECK (
        scan_state IN ('PENDING', 'CLEAN', 'INFECTED', 'FAILED', 'NOT_APPLICABLE')
    ),
    -- A file nobody scanned, or one that came back infected, is never submitted to a provider.
    CONSTRAINT ck_payment_dispute_evidence_submission CHECK (
        submitted_at IS NULL
        OR (scan_state IN ('CLEAN', 'NOT_APPLICABLE') AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_dispute_evidence_approval CHECK (
        (approved_at IS NULL) = (approved_by_actor_id IS NULL)
    ),
    CONSTRAINT ck_payment_dispute_evidence_digest_format CHECK (
        content_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_payment_dispute_evidence_size CHECK (byte_size IS NULL OR byte_size > 0),
    CONSTRAINT ck_payment_dispute_evidence_actor CHECK (
        prepared_by_actor_type IN ('SUPPORT_AGENT', 'SYSTEM', 'WORKER')
    ),
    CONSTRAINT ck_payment_dispute_evidence_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_dispute_evidence_dispute ON payment_dispute_evidence (dispute_id);
--rollback DROP TABLE payment_dispute_evidence;

--changeset ninggiangboy:021-15-payment-reconciliation-runs
-- Comparing what the platform believes against what the provider reports, on a schedule. The
-- watermark is stored so a rerun covers the same period deterministically, and the counts are stored
-- so "the import was short" is answerable without re-reading the file.
CREATE TABLE payment_reconciliation_runs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_account_id         UUID NOT NULL,
    run_type                    VARCHAR(24) NOT NULL,
    source_reference            VARCHAR(255),
    period_start                TIMESTAMPTZ NOT NULL,
    period_end                  TIMESTAMPTZ NOT NULL,
    watermark                   VARCHAR(128),

    state                       VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    external_row_count          INTEGER NOT NULL DEFAULT 0,
    matched_count               INTEGER NOT NULL DEFAULT 0,
    exception_count             INTEGER NOT NULL DEFAULT 0,

    started_at                  TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,
    failure_class               VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_reconciliation_runs_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_payment_reconciliation_runs_period
        UNIQUE (provider_account_id, run_type, period_start, period_end),
    CONSTRAINT ck_payment_reconciliation_runs_type CHECK (
        run_type IN ('STATUS_RECOVERY', 'DAILY_IMPORT', 'SETTLEMENT_CYCLE', 'MANUAL')
    ),
    CONSTRAINT ck_payment_reconciliation_runs_state CHECK (
        state IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_payment_reconciliation_runs_completion CHECK (
        (state IN ('COMPLETED', 'FAILED', 'CANCELLED')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_payment_reconciliation_runs_failure CHECK (
        state <> 'FAILED' OR failure_class IS NOT NULL
    ),
    CONSTRAINT ck_payment_reconciliation_runs_period_order CHECK (period_end > period_start),
    CONSTRAINT ck_payment_reconciliation_runs_counts CHECK (
        external_row_count >= 0 AND matched_count >= 0 AND exception_count >= 0
    ),
    CONSTRAINT ck_payment_reconciliation_runs_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_reconciliation_runs_account
    ON payment_reconciliation_runs (provider_account_id, period_end DESC);
--rollback DROP TABLE payment_reconciliation_runs;

--changeset ninggiangboy:021-16-payment-reconciliation-entries
-- One external row compared against one internal operation, with the verdict. The external row is
-- kept by digest rather than re-fetched, so a later argument about what the provider file said is
-- settled from the record and not from a fresh download of a file that may have changed.
CREATE TABLE payment_reconciliation_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                      UUID NOT NULL,
    provider_account_id         UUID NOT NULL,
    external_row_digest         CHAR(64) NOT NULL,
    provider_object_ref         VARCHAR(255),
    provider_request_key        VARCHAR(128),

    operation_id                UUID,
    outcome                     VARCHAR(32) NOT NULL,
    materiality                 VARCHAR(16) NOT NULL DEFAULT 'LOW',

    currency                    VARCHAR(3),
    external_amount_minor       BIGINT,
    internal_amount_minor       BIGINT,
    difference_amount_minor     BIGINT,

    observed_at                 TIMESTAMPTZ,
    compared_at                 TIMESTAMPTZ NOT NULL,
    case_id                     UUID,
    -- Deliberately not ON DELETE CASCADE: a reconciliation entry is the immutable record of what was
    -- compared, and the append-only trigger refuses the cascade.
    CONSTRAINT fk_payment_reconciliation_entries_run FOREIGN KEY (run_id)
        REFERENCES payment_reconciliation_runs (id),
    CONSTRAINT fk_payment_reconciliation_entries_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_payment_reconciliation_entries_operation FOREIGN KEY (operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT uk_payment_reconciliation_entries_row UNIQUE (run_id, external_row_digest),
    CONSTRAINT ck_payment_reconciliation_entries_outcome CHECK (
        outcome IN ('MATCHED', 'TIMING_DIFFERENCE', 'MISSING_PROVIDER',
                    'MISSING_INTERNAL_OR_ORPHAN', 'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH',
                    'ACCOUNT_MISMATCH', 'STATE_MISMATCH', 'DUPLICATE_SUSPECTED')
    ),
    CONSTRAINT ck_payment_reconciliation_entries_materiality CHECK (
        materiality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    -- A match names the operation it matched. An orphan is precisely the row that cannot.
    CONSTRAINT ck_payment_reconciliation_entries_match CHECK (
        outcome <> 'MATCHED' OR operation_id IS NOT NULL
    ),
    CONSTRAINT ck_payment_reconciliation_entries_orphan CHECK (
        outcome <> 'MISSING_INTERNAL_OR_ORPHAN' OR operation_id IS NULL
    ),
    CONSTRAINT ck_payment_reconciliation_entries_money CHECK (
        (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND (external_amount_minor IS NULL OR external_amount_minor >= 0)
        AND (internal_amount_minor IS NULL OR internal_amount_minor >= 0)
    ),
    CONSTRAINT ck_payment_reconciliation_entries_digest CHECK (
        external_row_digest ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_payment_reconciliation_entries_run
    ON payment_reconciliation_entries (run_id, outcome);
CREATE INDEX idx_payment_reconciliation_entries_operation
    ON payment_reconciliation_entries (operation_id)
    WHERE operation_id IS NOT NULL;
CREATE INDEX idx_payment_reconciliation_entries_exceptions
    ON payment_reconciliation_entries (materiality, compared_at DESC)
    WHERE outcome <> 'MATCHED';
--rollback DROP TABLE payment_reconciliation_entries;

--changeset ninggiangboy:021-17-payment-reconciliation-cases
-- An exception somebody has to resolve, and the record of how. A quarantined provider transaction
-- blocks confirmation and payout until it is either matched by proof or refunded, and never by
-- editing a provider reference until the report balances.
CREATE TABLE payment_reconciliation_cases (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    provider_account_id         UUID NOT NULL,
    opened_by_run_id            UUID,
    operation_id                UUID,
    obligation_id               UUID,
    booking_id                  UUID,

    category                    VARCHAR(32) NOT NULL,
    severity                    VARCHAR(16) NOT NULL DEFAULT 'LOW',
    status                      VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    blocks_payout               BOOLEAN NOT NULL DEFAULT FALSE,

    currency                    VARCHAR(3),
    exposure_amount_minor       BIGINT,

    assigned_to_actor_id        UUID,
    resolution                  VARCHAR(32),
    resolution_note             VARCHAR(512),
    repair_operation_id         UUID,
    resolved_by_actor_id        UUID,
    resolved_at                 TIMESTAMPTZ,

    opened_at                   TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_reconciliation_cases_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_payment_reconciliation_cases_run FOREIGN KEY (opened_by_run_id)
        REFERENCES payment_reconciliation_runs (id),
    CONSTRAINT fk_payment_reconciliation_cases_operation FOREIGN KEY (operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_payment_reconciliation_cases_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_payment_reconciliation_cases_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_payment_reconciliation_cases_repair FOREIGN KEY (repair_operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT uk_payment_reconciliation_cases_public_id UNIQUE (public_id),
    CONSTRAINT ck_payment_reconciliation_cases_category CHECK (
        category IN ('ORPHAN_TRANSACTION', 'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH',
                     'ACCOUNT_MISMATCH', 'STATE_MISMATCH', 'DUPLICATE_SUSPECTED',
                     'MISSING_PROVIDER', 'DOUBLE_CREDIT_EXPOSURE')
    ),
    CONSTRAINT ck_payment_reconciliation_cases_severity CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_payment_reconciliation_cases_status CHECK (
        status IN ('OPEN', 'INVESTIGATING', 'BLOCKED', 'RESOLVED', 'WRITTEN_OFF')
    ),
    CONSTRAINT ck_payment_reconciliation_cases_resolution CHECK (
        (status IN ('RESOLVED', 'WRITTEN_OFF'))
        = (resolved_at IS NOT NULL AND resolution IS NOT NULL AND resolved_by_actor_id IS NOT NULL)
    ),
    CONSTRAINT ck_payment_reconciliation_cases_resolution_kind CHECK (
        resolution IS NULL OR resolution IN (
            'MATCHED_WITH_EVIDENCE', 'REPAIRED_BY_COMMAND', 'REFUNDED', 'VOIDED',
            'PROVIDER_CORRECTED', 'NO_ACTION_REQUIRED', 'WRITTEN_OFF')
    ),
    CONSTRAINT ck_payment_reconciliation_cases_repair CHECK (
        resolution <> 'REPAIRED_BY_COMMAND' OR repair_operation_id IS NOT NULL
    ),
    CONSTRAINT ck_payment_reconciliation_cases_money CHECK (
        (exposure_amount_minor IS NULL) = (currency IS NULL)
        AND (exposure_amount_minor IS NULL OR exposure_amount_minor >= 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_payment_reconciliation_cases_version CHECK (version >= 0)
);

CREATE INDEX idx_payment_reconciliation_cases_open
    ON payment_reconciliation_cases (severity, opened_at)
    WHERE status IN ('OPEN', 'INVESTIGATING', 'BLOCKED');
-- Finding what is holding a payout back, which is the question finance asks of this table.
CREATE INDEX idx_payment_reconciliation_cases_payout_block
    ON payment_reconciliation_cases (booking_id)
    WHERE blocks_payout AND status <> 'RESOLVED';
--rollback DROP TABLE payment_reconciliation_cases;

--changeset ninggiangboy:021-18-reconciliation-entry-case-link
ALTER TABLE payment_reconciliation_entries
    ADD CONSTRAINT fk_payment_reconciliation_entries_case FOREIGN KEY (case_id)
        REFERENCES payment_reconciliation_cases (id);
--rollback ALTER TABLE payment_reconciliation_entries DROP CONSTRAINT fk_payment_reconciliation_entries_case;

--changeset ninggiangboy:021-19-payment-timeline-entries
-- What a support agent is allowed to see about a payment, in order. It is a projection, written from
-- committed facts, so that answering "what happened to my money" never means reading raw provider
-- payloads or joining six tables under time pressure.
CREATE TABLE payment_timeline_entries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    obligation_id               UUID NOT NULL,
    sequence_number             INTEGER NOT NULL,
    entry_type                  VARCHAR(48) NOT NULL,
    attempt_id                  UUID,
    operation_id                UUID,
    dispute_id                  UUID,

    currency                    VARCHAR(3),
    amount_minor                BIGINT,
    summary_code                VARCHAR(64) NOT NULL,
    detail                      VARCHAR(512),
    visibility                  VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',

    occurred_at                 TIMESTAMPTZ NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL,
    actor_type                  VARCHAR(24) NOT NULL,
    actor_id                    UUID,
    -- Deliberately not ON DELETE CASCADE: append-only, so the cascade could not run.
    CONSTRAINT fk_payment_timeline_entries_obligation FOREIGN KEY (obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_payment_timeline_entries_attempt FOREIGN KEY (attempt_id)
        REFERENCES payment_attempts (id),
    CONSTRAINT fk_payment_timeline_entries_operation FOREIGN KEY (operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_payment_timeline_entries_dispute FOREIGN KEY (dispute_id)
        REFERENCES payment_disputes (id),
    CONSTRAINT uk_payment_timeline_entries_sequence UNIQUE (obligation_id, sequence_number),
    CONSTRAINT ck_payment_timeline_entries_sequence_number CHECK (sequence_number > 0),
    CONSTRAINT ck_payment_timeline_entries_visibility CHECK (
        visibility IN ('GUEST', 'HOST', 'BOTH', 'INTERNAL')
    ),
    CONSTRAINT ck_payment_timeline_entries_money CHECK (
        (amount_minor IS NULL) = (currency IS NULL)
        AND (amount_minor IS NULL OR amount_minor >= 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_payment_timeline_entries_actor CHECK (
        actor_type IN ('GUEST', 'HOST', 'SUPPORT_AGENT', 'SYSTEM', 'WORKER', 'PROVIDER')
    )
);

CREATE INDEX idx_payment_timeline_entries_obligation
    ON payment_timeline_entries (obligation_id, sequence_number DESC);
--rollback DROP TABLE payment_timeline_entries;

--changeset ninggiangboy:021-20-payment-evidence-append-only splitStatements:false
-- The four tables a money dispute is decided from. Protecting them only in application code leaves
-- every future code path free to rewrite the evidence, and the one path that forgets is the one that
-- matters. A stale webhook is appended and marked ignored; a wrong comparison is appended again by a
-- later run; nothing here is ever edited in place.
CREATE FUNCTION payment_evidence_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; append a correcting record instead of altering the evidence', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_operation_observations_append_only
    BEFORE UPDATE OR DELETE ON payment_operation_observations
    FOR EACH ROW EXECUTE FUNCTION payment_evidence_reject_mutation();

CREATE TRIGGER trg_payment_dispute_events_append_only
    BEFORE UPDATE OR DELETE ON payment_dispute_events
    FOR EACH ROW EXECUTE FUNCTION payment_evidence_reject_mutation();

CREATE TRIGGER trg_payment_reconciliation_entries_append_only
    BEFORE UPDATE OR DELETE ON payment_reconciliation_entries
    FOR EACH ROW EXECUTE FUNCTION payment_evidence_reject_mutation();

CREATE TRIGGER trg_payment_timeline_entries_append_only
    BEFORE UPDATE OR DELETE ON payment_timeline_entries
    FOR EACH ROW EXECUTE FUNCTION payment_evidence_reject_mutation();
--rollback DROP TRIGGER trg_payment_timeline_entries_append_only ON payment_timeline_entries;
--rollback DROP TRIGGER trg_payment_reconciliation_entries_append_only ON payment_reconciliation_entries;
--rollback DROP TRIGGER trg_payment_dispute_events_append_only ON payment_dispute_events;
--rollback DROP TRIGGER trg_payment_operation_observations_append_only ON payment_operation_observations;
--rollback DROP FUNCTION payment_evidence_reject_mutation();

--changeset ninggiangboy:021-21-submitted-evidence-frozen splitStatements:false
-- Dispute evidence is mutable while it is being assembled and frozen the moment it is sent. A
-- representment the platform cannot reproduce byte for byte is not a defence it can stand behind,
-- and "we changed the file after submitting" is the sentence that loses the case.
CREATE FUNCTION payment_dispute_evidence_freeze_submitted() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.submitted_at IS NOT NULL THEN
        RAISE EXCEPTION
            'dispute evidence % was submitted at % and can no longer be altered',
            OLD.id, OLD.submitted_at
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payment_dispute_evidence_frozen
    BEFORE UPDATE OR DELETE ON payment_dispute_evidence
    FOR EACH ROW EXECUTE FUNCTION payment_dispute_evidence_freeze_submitted();
--rollback DROP TRIGGER trg_payment_dispute_evidence_frozen ON payment_dispute_evidence;
--rollback DROP FUNCTION payment_dispute_evidence_freeze_submitted();
