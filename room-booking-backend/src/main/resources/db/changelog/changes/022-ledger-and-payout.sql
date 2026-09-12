--liquibase formatted sql

-- Everything before this migration records what the platform intended and what a provider reported.
-- Neither of those is an accounting position. This migration adds the journal that says what the
-- platform actually owns, owes, and has earned, and the machinery that turns a host's share of it
-- into money in a bank account.
--
-- Six things force the shape:
--
--   The journal is the source of truth, and it balances or it does not exist. A posted transaction
--   is a set of positive postings in one book, one legal entity, and one currency whose debits equal
--   its credits. No row-level CHECK can see across the rows of a transaction, so the balance rule is
--   a DEFERRABLE INITIALLY DEFERRED constraint trigger: it fires at COMMIT, when the whole set is
--   visible, and an unbalanced set can never be committed by any code path.
--
--   Posted history is immutable. A mistake is corrected by a reversal that references the original
--   and a new transaction that supersedes it -- never by editing the original. That is enforced by a
--   trigger, not by convention, because the whole value of the journal is that yesterday's entry
--   still says what it said yesterday.
--
--   One business fact produces at most one accounting effect. The unique key
--   (accounting_book_id, source_type, source_id, posting_purpose) is what makes a replayed event, a
--   duplicated webhook, a retried worker, and a client timeout all converge on the transaction that
--   already exists instead of posting a second one.
--
--   Owning money and being able to receive it are different states. A host can be economically
--   entitled to an amount that is not yet released, is under a hold, is backing a reserve, or is
--   funding a recovery. Those are four separate records against one payable allocation, each with
--   its own reason and its own lifetime, because collapsing them into a status column is how an
--   amount silently stops being the host's.
--
--   A payable allocation is consumed exactly once at the platform boundary. A partial unique index
--   lets at most one live payout item hold any allocation, so two concurrent payout planners cannot
--   both select the same money, whatever the application does with its locks.
--
--   A payout crosses the same submission fence a payment does. An instruction that reached the
--   provider can never be marked cancelled, a terminal failure needs a classification, and a timeout
--   resolves to an unknown state that must be queried rather than retried.
--
-- Note on payout destinations: the feature document proposes a payout_destinations table. Migration
-- 015 already delivered payout_destination_claims -- tokenized account reference, ownership
-- verification state, cooling-off instant, one active destination per holder/market/currency, and a
-- detachment lifecycle. That is the same concept with the account-takeover defences already built,
-- so payout_instructions points at it rather than at a second destination registry.
--
-- Note on reconciliation: migration 021 delivered payment_reconciliation_runs, entries and cases,
-- which compare a provider's payment report against payment_operations. This migration reconciles a
-- different pair -- external financial artifacts against the journal, the clearing accounts, and the
-- payouts -- so its tables are named finance_reconciliation_* and are deliberately separate. A green
-- payment report does not prove the ledger agrees with the bank.
--
-- Note on platform primitives: the document proposes finance_idempotency_records,
-- finance_outbox_events, finance_inbox_events and finance_audit_actions. Migration 012 delivered
-- command_idempotency_records, outbox_events, consumer_inbox_receipts and append-only audit_events,
-- all of which carry a scope or aggregate type. A second outbox would need a second publisher and a
-- second ordering guarantee, so finance uses the existing ones.
--
-- Note on FX: the document constrains the target release to
-- booking currency = collection currency = ledger currency = payout currency, and treats additional
-- currencies as a designed extension. No fx_conversions table is created here. What the release does
-- enforce is that a transaction, a payout, a statement, and a reserve each carry exactly one
-- currency and never net one against another.

--changeset ninggiangboy:022-01-accounting-books
-- A book is one legal entity and one accounting purpose. Every journal row names its book, so a
-- second book can be added later without any existing row becoming ambiguous.
CREATE TABLE accounting_books (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    book_key                    VARCHAR(64) NOT NULL,
    legal_entity_id             UUID NOT NULL,
    display_name                VARCHAR(128) NOT NULL,
    purpose                     VARCHAR(24) NOT NULL,
    functional_currency         VARCHAR(3) NOT NULL,
    reporting_currency          VARCHAR(3),
    period_timezone             VARCHAR(64) NOT NULL,
    lifecycle_state             VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    owner_actor_id              UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_accounting_books_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT uk_accounting_books_key UNIQUE (book_key),
    CONSTRAINT ck_accounting_books_purpose CHECK (
        purpose IN ('MARKETPLACE_SUBLEDGER', 'STATUTORY', 'MANAGEMENT', 'PROVIDER_CONTROL')
    ),
    CONSTRAINT ck_accounting_books_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')
    ),
    CONSTRAINT ck_accounting_books_currency CHECK (
        functional_currency ~ '^[A-Z]{3}$'
        AND (reporting_currency IS NULL OR reporting_currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_accounting_books_effective CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_accounting_books_version CHECK (version >= 0)
);

CREATE INDEX idx_accounting_books_entity ON accounting_books (legal_entity_id, lifecycle_state);
--rollback DROP TABLE accounting_books;

--changeset ninggiangboy:022-02-ledger-accounts
-- The chart of accounts. Finance approves the normal balance, the currency, and which dimensions a
-- posting to this account must and must not carry; the posting engine refuses anything else. An
-- account that has been posted to is retired, never deleted, or replay stops working.
CREATE TABLE ledger_accounts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accounting_book_id          UUID NOT NULL,
    account_code                VARCHAR(32) NOT NULL,
    account_name                VARCHAR(128) NOT NULL,
    account_class               VARCHAR(24) NOT NULL,
    account_family              VARCHAR(48) NOT NULL,
    normal_balance              VARCHAR(8) NOT NULL,
    currency                    VARCHAR(3),
    -- Which dimensions a posting to this account must carry, and which it must not. Held as an
    -- immutable policy snapshot rather than columns, because the dimension set is finance policy and
    -- the engine reads it whole; the dimensions themselves stay relational on ledger_postings.
    required_dimensions         JSONB NOT NULL DEFAULT '[]'::jsonb,
    forbidden_dimensions        JSONB NOT NULL DEFAULT '[]'::jsonb,
    manual_posting_allowed      BOOLEAN NOT NULL DEFAULT false,
    report_mapping_code         VARCHAR(64),
    lifecycle_state             VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    approved_by_actor_id        UUID,
    approved_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ledger_accounts_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT uk_ledger_accounts_code UNIQUE (accounting_book_id, account_code),
    CONSTRAINT ck_ledger_accounts_class CHECK (
        account_class IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE', 'CONTRA_REVENUE')
    ),
    CONSTRAINT ck_ledger_accounts_normal_balance CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_accounts_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_accounts_lifecycle CHECK (
        lifecycle_state IN ('DRAFT', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_ledger_accounts_approval CHECK (
        (lifecycle_state = 'DRAFT')
            OR (approved_by_actor_id IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_ledger_accounts_effective CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_ledger_accounts_dimension_policy CHECK (
        jsonb_typeof(required_dimensions) = 'array' AND jsonb_typeof(forbidden_dimensions) = 'array'
    ),
    CONSTRAINT ck_ledger_accounts_version CHECK (version >= 0)
);

CREATE INDEX idx_ledger_accounts_family
    ON ledger_accounts (accounting_book_id, account_family, lifecycle_state);
--rollback DROP TABLE ledger_accounts;

--changeset ninggiangboy:022-03-posting-rule-versions
-- The deterministic mapping from an approved source fact to a set of postings. A transaction pins
-- the version it used, so replaying a two-year-old booking produces the entries that were correct
-- two years ago rather than the entries today's chart of accounts would produce.
CREATE TABLE posting_rule_versions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_key                    VARCHAR(96) NOT NULL,
    version_number              INTEGER NOT NULL,
    accounting_book_id          UUID NOT NULL,
    source_fact_type            VARCHAR(64) NOT NULL,
    source_schema_version        INTEGER NOT NULL DEFAULT 1,
    market_code                 VARCHAR(2),
    precedence                  INTEGER NOT NULL DEFAULT 100,
    -- The approved rule artifact itself, kept whole and hashed, because "which rule ran" must be
    -- answerable from the row and not from whatever the deployed code happens to say today.
    rule_document               JSONB NOT NULL,
    rule_document_hash          CHAR(64) NOT NULL,
    test_vector_version         VARCHAR(32),
    publication_state           VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from              TIMESTAMPTZ NOT NULL,
    effective_until             TIMESTAMPTZ,
    authored_by_actor_id        UUID,
    approved_by_actor_id        UUID,
    published_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_posting_rule_versions_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_posting_rule_versions_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT uk_posting_rule_versions_number UNIQUE (rule_key, version_number),
    CONSTRAINT ck_posting_rule_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_posting_rule_versions_state CHECK (
        publication_state IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'SUPERSEDED', 'WITHDRAWN')
    ),
    -- Approval is not a comment on the row; a published rule names who approved it and when.
    CONSTRAINT ck_posting_rule_versions_approval CHECK (
        publication_state NOT IN ('APPROVED', 'PUBLISHED', 'SUPERSEDED')
            OR approved_by_actor_id IS NOT NULL
    ),
    CONSTRAINT ck_posting_rule_versions_published CHECK (
        (publication_state IN ('PUBLISHED', 'SUPERSEDED')) = (published_at IS NOT NULL)
    ),
    CONSTRAINT ck_posting_rule_versions_hash CHECK (rule_document_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_posting_rule_versions_document CHECK (jsonb_typeof(rule_document) = 'object'),
    CONSTRAINT ck_posting_rule_versions_effective CHECK (
        effective_until IS NULL OR effective_until > effective_from
    ),
    CONSTRAINT ck_posting_rule_versions_precedence CHECK (precedence > 0),
    CONSTRAINT ck_posting_rule_versions_version CHECK (version >= 0)
);

-- Rule selection is by fact type, book, market and effective instant, in that order.
CREATE INDEX idx_posting_rule_versions_selection
    ON posting_rule_versions (source_fact_type, accounting_book_id, effective_from DESC)
    WHERE publication_state = 'PUBLISHED';
--rollback DROP TABLE posting_rule_versions;

--changeset ninggiangboy:022-04-accounting-periods
-- The governed interval a transaction posts into. Two overlapping periods for one book would make
-- "which period does this belong to" ambiguous at close, so the exclusion constraint forbids it
-- outright rather than leaving it to the code that creates them.
CREATE TABLE accounting_periods (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accounting_book_id          UUID NOT NULL,
    period_label                VARCHAR(32) NOT NULL,
    period_range                DATERANGE NOT NULL,
    period_timezone             VARCHAR(64) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    -- What was true at close, so the close can be reproduced rather than retold.
    source_watermark_at         TIMESTAMPTZ,
    unposted_source_count       INTEGER NOT NULL DEFAULT 0,
    open_exception_count        INTEGER NOT NULL DEFAULT 0,
    balance_hash                CHAR(64),
    close_version               INTEGER NOT NULL DEFAULT 0,
    soft_closed_at              TIMESTAMPTZ,
    hard_closed_at              TIMESTAMPTZ,
    reopened_at                 TIMESTAMPTZ,
    closed_by_actor_id          UUID,
    approved_by_actor_id        UUID,
    reopen_reason_code          VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_accounting_periods_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT uk_accounting_periods_label UNIQUE (accounting_book_id, period_label),
    CONSTRAINT ex_accounting_periods_no_overlap EXCLUDE USING gist (
        accounting_book_id WITH =,
        period_range WITH &&
    ),
    CONSTRAINT ck_accounting_periods_state CHECK (
        state IN ('OPEN', 'SOFT_CLOSING', 'HARD_CLOSED', 'REOPENED')
    ),
    CONSTRAINT ck_accounting_periods_range CHECK (NOT isempty(period_range)),
    -- A hard close is the claim that the numbers are final, so it must carry the evidence that made
    -- it final. A reopen does not erase that; it records its own reason beside it.
    CONSTRAINT ck_accounting_periods_hard_close CHECK (
        state <> 'HARD_CLOSED'
            OR (hard_closed_at IS NOT NULL
                AND closed_by_actor_id IS NOT NULL
                AND approved_by_actor_id IS NOT NULL
                AND balance_hash IS NOT NULL
                AND source_watermark_at IS NOT NULL)
    ),
    CONSTRAINT ck_accounting_periods_soft_close CHECK (
        state <> 'SOFT_CLOSING' OR soft_closed_at IS NOT NULL
    ),
    CONSTRAINT ck_accounting_periods_reopen CHECK (
        (state = 'REOPENED') = (reopened_at IS NOT NULL AND reopen_reason_code IS NOT NULL)
    ),
    CONSTRAINT ck_accounting_periods_hash CHECK (
        balance_hash IS NULL OR balance_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_accounting_periods_counts CHECK (
        unposted_source_count >= 0 AND open_exception_count >= 0 AND close_version >= 0
    ),
    CONSTRAINT ck_accounting_periods_version CHECK (version >= 0)
);

CREATE INDEX idx_accounting_periods_open
    ON accounting_periods (accounting_book_id, lower(period_range))
    WHERE state IN ('OPEN', 'REOPENED');
--rollback DROP TABLE accounting_periods;

--changeset ninggiangboy:022-05-ledger-transactions
-- One approved economic event. The unique key on (book, source type, source id, purpose) is the
-- single defence against a replayed event, a duplicated webhook, a retried worker, and a client
-- timeout each posting the same money again. The source input hash is stored beside it so a second
-- request with the same key but different facts is recognised as a conflict rather than absorbed.
CREATE TABLE ledger_transactions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accounting_book_id          UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    accounting_period_id        UUID,
    currency                    VARCHAR(3) NOT NULL,

    source_type                 VARCHAR(64) NOT NULL,
    source_id                   UUID NOT NULL,
    source_version              INTEGER NOT NULL DEFAULT 1,
    source_input_hash           CHAR(64) NOT NULL,
    posting_purpose             VARCHAR(48) NOT NULL,
    posting_rule_version_id     UUID NOT NULL,

    occurred_at                 TIMESTAMPTZ NOT NULL,
    accounting_date             DATE NOT NULL,
    accounting_timezone         VARCHAR(64) NOT NULL,

    state                       VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    description                 VARCHAR(255),
    reason_code                 VARCHAR(64),
    rejection_code              VARCHAR(64),

    -- A correction is two rows: one that negates the original and one that says what should have
    -- been posted. Neither touches the original.
    reverses_transaction_id     UUID,
    supersedes_transaction_id   UUID,

    actor_id                    UUID,
    process_name                VARCHAR(96),
    approval_reference          VARCHAR(128),
    correlation_id              UUID,
    causation_id                UUID,

    posted_at                   TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ledger_transactions_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_ledger_transactions_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_ledger_transactions_period FOREIGN KEY (accounting_period_id)
        REFERENCES accounting_periods (id),
    CONSTRAINT fk_ledger_transactions_rule FOREIGN KEY (posting_rule_version_id)
        REFERENCES posting_rule_versions (id),
    CONSTRAINT fk_ledger_transactions_reverses FOREIGN KEY (reverses_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_ledger_transactions_supersedes FOREIGN KEY (supersedes_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT uk_ledger_transactions_source
        UNIQUE (accounting_book_id, source_type, source_id, posting_purpose),
    CONSTRAINT ck_ledger_transactions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_transactions_state CHECK (
        state IN ('RECEIVED', 'VALIDATED', 'POSTED', 'REJECTED', 'REVIEW_REQUIRED')
    ),
    CONSTRAINT ck_ledger_transactions_posted CHECK (
        (state = 'POSTED') = (posted_at IS NOT NULL)
    ),
    -- A posted transaction belongs to a period, or the close cannot account for it.
    CONSTRAINT ck_ledger_transactions_period_required CHECK (
        state <> 'POSTED' OR accounting_period_id IS NOT NULL
    ),
    CONSTRAINT ck_ledger_transactions_rejection CHECK (
        (state = 'REJECTED') = (rejection_code IS NOT NULL)
    ),
    -- A reversal names what it reverses and is not also a supersession of something else.
    CONSTRAINT ck_ledger_transactions_reversal CHECK (
        reverses_transaction_id IS NULL OR reverses_transaction_id <> id
    ),
    CONSTRAINT ck_ledger_transactions_supersession CHECK (
        supersedes_transaction_id IS NULL OR supersedes_transaction_id <> id
    ),
    CONSTRAINT ck_ledger_transactions_hash CHECK (source_input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ledger_transactions_source_version CHECK (source_version > 0),
    CONSTRAINT ck_ledger_transactions_version CHECK (version >= 0)
);

-- At most one reversal of any transaction. A second one would negate the same effect twice.
CREATE UNIQUE INDEX uk_ledger_transactions_reversal
    ON ledger_transactions (reverses_transaction_id)
    WHERE reverses_transaction_id IS NOT NULL AND state = 'POSTED';

CREATE INDEX idx_ledger_transactions_period
    ON ledger_transactions (accounting_period_id, accounting_date)
    WHERE state = 'POSTED';
-- Source work that has not reached the journal yet, oldest first, for the posting worker.
CREATE INDEX idx_ledger_transactions_unposted
    ON ledger_transactions (created_at)
    WHERE state IN ('RECEIVED', 'VALIDATED', 'REVIEW_REQUIRED');
CREATE INDEX idx_ledger_transactions_source_lookup
    ON ledger_transactions (source_type, source_id);
--rollback DROP TABLE ledger_transactions;

--changeset ninggiangboy:022-06-ledger-postings
-- One positive amount on one account in one direction, carrying the dimensions that let the entry be
-- explained afterwards. The amount is never negative: a debit and a credit are different facts, not
-- the same number with different signs, and a bare negative in a money column is an invitation to
-- read it the wrong way.
CREATE TABLE ledger_postings (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id              UUID NOT NULL,
    sequence_number             SMALLINT NOT NULL,
    ledger_account_id           UUID NOT NULL,
    direction                   VARCHAR(8) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,

    host_account_holder_id      UUID,
    guest_account_holder_id     UUID,
    listing_id                  UUID,
    market_code                 VARCHAR(2),
    booking_id                  UUID,
    booking_revision            INTEGER,
    booking_line_item_id        UUID,
    service_period_start        DATE,
    service_period_end          DATE,

    collection_obligation_id    UUID,
    payment_operation_id        UUID,
    provider_account_id         UUID,
    payment_dispute_id          UUID,
    -- Payout, reserve, recovery and reconciliation dimensions are declared here and constrained by
    -- the forward links further down this file, once the tables they point at exist.
    payout_instruction_id       UUID,
    payout_item_id              UUID,
    host_reserve_id             UUID,
    host_recovery_id            UUID,
    reconciliation_case_id      UUID,

    tax_calculation_line_id     UUID,
    promotion_version_id        UUID,
    reconciliation_category     VARCHAR(48),
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: a posted transaction is frozen by a row trigger, so a
    -- cascade would be a promise the trigger refuses to keep.
    CONSTRAINT fk_ledger_postings_transaction FOREIGN KEY (transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_ledger_postings_account FOREIGN KEY (ledger_account_id)
        REFERENCES ledger_accounts (id),
    CONSTRAINT fk_ledger_postings_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_ledger_postings_guest FOREIGN KEY (guest_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_ledger_postings_listing FOREIGN KEY (listing_id) REFERENCES listings (id),
    CONSTRAINT fk_ledger_postings_market FOREIGN KEY (market_code)
        REFERENCES markets (market_code),
    CONSTRAINT fk_ledger_postings_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_ledger_postings_line FOREIGN KEY (booking_line_item_id)
        REFERENCES booking_line_items (id),
    CONSTRAINT fk_ledger_postings_obligation FOREIGN KEY (collection_obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT fk_ledger_postings_operation FOREIGN KEY (payment_operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_ledger_postings_provider_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_ledger_postings_dispute FOREIGN KEY (payment_dispute_id)
        REFERENCES payment_disputes (id),
    CONSTRAINT fk_ledger_postings_tax_line FOREIGN KEY (tax_calculation_line_id)
        REFERENCES tax_calculation_lines (id),
    CONSTRAINT fk_ledger_postings_promotion FOREIGN KEY (promotion_version_id)
        REFERENCES promotion_versions (id),
    CONSTRAINT uk_ledger_postings_sequence UNIQUE (transaction_id, sequence_number),
    CONSTRAINT ck_ledger_postings_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_postings_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_ledger_postings_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_ledger_postings_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_ledger_postings_revision CHECK (
        booking_revision IS NULL OR booking_revision > 0
    ),
    -- A service period is two dates or no dates, and the second is not before the first.
    CONSTRAINT ck_ledger_postings_service_period CHECK (
        (service_period_start IS NULL) = (service_period_end IS NULL)
        AND (service_period_end IS NULL OR service_period_end >= service_period_start)
    )
);

CREATE INDEX idx_ledger_postings_transaction ON ledger_postings (transaction_id, sequence_number);
CREATE INDEX idx_ledger_postings_account ON ledger_postings (ledger_account_id, created_at DESC);
CREATE INDEX idx_ledger_postings_host
    ON ledger_postings (host_account_holder_id, currency, created_at DESC)
    WHERE host_account_holder_id IS NOT NULL;
CREATE INDEX idx_ledger_postings_booking ON ledger_postings (booking_id)
    WHERE booking_id IS NOT NULL;
CREATE INDEX idx_ledger_postings_payout ON ledger_postings (payout_instruction_id)
    WHERE payout_instruction_id IS NOT NULL;
CREATE INDEX idx_ledger_postings_provider ON ledger_postings (provider_account_id, created_at DESC)
    WHERE provider_account_id IS NOT NULL;
--rollback DROP TABLE ledger_postings;

--changeset ninggiangboy:022-07-ledger-balance-rule splitStatements:false
-- Debits must equal credits, and no row-level CHECK can see across the rows of a transaction. A
-- DEFERRABLE INITIALLY DEFERRED constraint trigger fires once at COMMIT, when the whole posting set
-- is visible, so an unbalanced journal cannot be committed by any code path -- including a
-- hand-written statement in a console. The rule applies only to POSTED transactions, because a
-- RECEIVED one is still being assembled.
--
-- It also enforces the other two things a balanced set needs: at least two postings, and one
-- currency matching the transaction's own.
CREATE FUNCTION ledger_transactions_validate_balance() RETURNS TRIGGER AS $$
DECLARE
    target_transaction UUID;
    transaction_state  VARCHAR(16);
    transaction_ccy    VARCHAR(3);
    debit_total        BIGINT;
    credit_total       BIGINT;
    posting_count      INTEGER;
    currency_count     INTEGER;
BEGIN
    IF TG_TABLE_NAME = 'ledger_transactions' THEN
        target_transaction := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target_transaction := OLD.transaction_id;
    ELSE
        target_transaction := NEW.transaction_id;
    END IF;

    SELECT state, currency INTO transaction_state, transaction_ccy
        FROM ledger_transactions WHERE id = target_transaction;

    -- The transaction was deleted in this same statement, or was never posted. Either way there is
    -- no balanced set to insist on.
    IF transaction_state IS NULL OR transaction_state <> 'POSTED' THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(sum(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0),
           COALESCE(sum(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0),
           count(*),
           count(DISTINCT currency)
      INTO debit_total, credit_total, posting_count, currency_count
      FROM ledger_postings
     WHERE transaction_id = target_transaction;

    IF posting_count < 2 THEN
        RAISE EXCEPTION
            'posted transaction % has % posting(s); a balanced entry needs at least two',
            target_transaction, posting_count
            USING ERRCODE = 'check_violation';
    END IF;

    IF currency_count <> 1 OR NOT EXISTS (
        SELECT 1 FROM ledger_postings
         WHERE transaction_id = target_transaction AND currency = transaction_ccy
    ) THEN
        RAISE EXCEPTION
            'posted transaction % must post a single currency matching its own (%)',
            target_transaction, transaction_ccy
            USING ERRCODE = 'check_violation';
    END IF;

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION
            'posted transaction % debits % but credits %',
            target_transaction, debit_total, credit_total
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_postings_balance
    AFTER INSERT OR UPDATE OR DELETE ON ledger_postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_transactions_validate_balance();

-- Posting the transaction is itself the moment the rule must hold, otherwise a transaction could be
-- moved to POSTED after its postings were written and skip the check entirely.
CREATE CONSTRAINT TRIGGER trg_ledger_transactions_balance
    AFTER INSERT OR UPDATE ON ledger_transactions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ledger_transactions_validate_balance();
--rollback DROP TRIGGER trg_ledger_transactions_balance ON ledger_transactions;
--rollback DROP TRIGGER trg_ledger_postings_balance ON ledger_postings;
--rollback DROP FUNCTION ledger_transactions_validate_balance();

--changeset ninggiangboy:022-08-ledger-immutability splitStatements:false
-- Posted history is immutable. The whole value of the journal is that yesterday's entry still says
-- what it said yesterday, so a posted transaction and its postings are frozen and a mistake is
-- corrected by a reversal plus a new transaction. Only the fields that describe the transaction's
-- relationship to later corrections may still move, since a reversal is written after the fact.
CREATE FUNCTION ledger_transactions_freeze_posted() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'ledger transaction % is posted and cannot be deleted; post a reversal instead', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.accounting_book_id  IS DISTINCT FROM OLD.accounting_book_id
        OR NEW.legal_entity_id         IS DISTINCT FROM OLD.legal_entity_id
        OR NEW.currency                IS DISTINCT FROM OLD.currency
        OR NEW.source_type             IS DISTINCT FROM OLD.source_type
        OR NEW.source_id               IS DISTINCT FROM OLD.source_id
        OR NEW.source_input_hash       IS DISTINCT FROM OLD.source_input_hash
        OR NEW.posting_purpose         IS DISTINCT FROM OLD.posting_purpose
        OR NEW.posting_rule_version_id IS DISTINCT FROM OLD.posting_rule_version_id
        OR NEW.occurred_at             IS DISTINCT FROM OLD.occurred_at
        OR NEW.accounting_date         IS DISTINCT FROM OLD.accounting_date
        OR NEW.accounting_period_id    IS DISTINCT FROM OLD.accounting_period_id
        OR NEW.state                   IS DISTINCT FROM OLD.state
        OR NEW.posted_at               IS DISTINCT FROM OLD.posted_at
    THEN
        RAISE EXCEPTION
            'ledger transaction % is posted; correct it with a reversal and a new transaction '
            'rather than altering the original', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_transactions_frozen
    BEFORE UPDATE OR DELETE ON ledger_transactions
    FOR EACH ROW WHEN (OLD.state = 'POSTED')
    EXECUTE FUNCTION ledger_transactions_freeze_posted();

-- INSERT is covered as well as UPDATE and DELETE. A balanced extra pair of postings added to an
-- already posted transaction would satisfy the deferred balance rule and still change what the
-- transaction says, which is exactly the edit the freeze exists to prevent.
--
-- This means a transaction is written un-posted, its postings are inserted, and it is then moved to
-- POSTED in the same database transaction. That is the lifecycle the feature document specifies
-- (RECEIVED -> VALIDATED -> POSTED), and it is what lets the rule be stated simply: nothing may be
-- added to a transaction that is already posted.
CREATE FUNCTION ledger_postings_freeze_posted() RETURNS TRIGGER AS $$
DECLARE
    owning_state   VARCHAR(16);
    owning_id      UUID;
BEGIN
    owning_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.transaction_id ELSE NEW.transaction_id END;
    SELECT state INTO owning_state FROM ledger_transactions WHERE id = owning_id;

    IF owning_state = 'POSTED' THEN
        RAISE EXCEPTION
            'transaction % is posted; its postings cannot be added to, altered, or removed',
            owning_id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_postings_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON ledger_postings
    FOR EACH ROW EXECUTE FUNCTION ledger_postings_freeze_posted();
--rollback DROP TRIGGER trg_ledger_postings_frozen ON ledger_postings;
--rollback DROP FUNCTION ledger_postings_freeze_posted();
--rollback DROP TRIGGER trg_ledger_transactions_frozen ON ledger_transactions;
--rollback DROP FUNCTION ledger_transactions_freeze_posted();

--changeset ninggiangboy:022-09-host-payable-allocations
-- What the host is owed, as a thing that can be reserved, consumed and recovered against rather
-- than as a balance somebody recomputes. Owning the money and being able to receive it are separate:
-- ownership_state says whose it is, release_state says whether it may enter a payout, and the two
-- move independently. Collapsing them into one status is how an amount silently stops being the
-- host's when a hold is placed on it.
CREATE TABLE host_payable_allocations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accounting_book_id          UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    host_account_holder_id      UUID NOT NULL,
    source_posting_id           UUID NOT NULL,
    booking_id                  UUID,
    booking_line_item_id        UUID,
    currency                    VARCHAR(3) NOT NULL,

    original_amount_minor       BIGINT NOT NULL,
    reserved_amount_minor       BIGINT NOT NULL DEFAULT 0,
    consumed_amount_minor       BIGINT NOT NULL DEFAULT 0,
    recovered_amount_minor      BIGINT NOT NULL DEFAULT 0,
    remaining_amount_minor      BIGINT NOT NULL,

    ownership_state             VARCHAR(16) NOT NULL DEFAULT 'PROVISIONAL',
    release_state               VARCHAR(16) NOT NULL DEFAULT 'NOT_SCHEDULED',
    release_policy_version_id   UUID,
    release_trigger_type        VARCHAR(32),
    scheduled_release_at        TIMESTAMPTZ,
    release_timezone            VARCHAR(64),
    released_at                 TIMESTAMPTZ,
    service_period_start        DATE,
    service_period_end          DATE,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_payable_allocations_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_host_payable_allocations_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_host_payable_allocations_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_payable_allocations_posting FOREIGN KEY (source_posting_id)
        REFERENCES ledger_postings (id),
    CONSTRAINT fk_host_payable_allocations_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_host_payable_allocations_line FOREIGN KEY (booking_line_item_id)
        REFERENCES booking_line_items (id),
    -- One allocation per journal posting. A second would double the host's entitlement from a single
    -- accounting entry.
    CONSTRAINT uk_host_payable_allocations_posting UNIQUE (source_posting_id),
    CONSTRAINT ck_host_payable_allocations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_host_payable_allocations_amounts CHECK (
        original_amount_minor > 0
        AND reserved_amount_minor >= 0
        AND consumed_amount_minor >= 0
        AND recovered_amount_minor >= 0
    ),
    -- The ceiling: an allocation can never give out more than it holds, whatever the service that
    -- holds the lock believes.
    CONSTRAINT ck_host_payable_allocations_ceiling CHECK (
        reserved_amount_minor + consumed_amount_minor + recovered_amount_minor
            <= original_amount_minor
    ),
    -- remaining is stored so the payout planner can index and filter on it, and constrained so it
    -- can never disagree with the parts it is derived from.
    CONSTRAINT ck_host_payable_allocations_remaining CHECK (
        remaining_amount_minor
            = original_amount_minor - consumed_amount_minor - recovered_amount_minor
    ),
    CONSTRAINT ck_host_payable_allocations_ownership CHECK (
        ownership_state IN ('PROVISIONAL', 'PAYABLE', 'REVERSED', 'RECOVERY_DUE')
    ),
    CONSTRAINT ck_host_payable_allocations_release CHECK (
        release_state IN ('NOT_SCHEDULED', 'SCHEDULED', 'AVAILABLE', 'HELD', 'RESERVED', 'CONSUMED')
    ),
    -- A scheduled release names its instant and the zone the schedule was computed in; a civil
    -- cutoff read in the wrong zone pays a host a day early or a day late.
    CONSTRAINT ck_host_payable_allocations_schedule CHECK (
        release_state = 'NOT_SCHEDULED'
            OR (scheduled_release_at IS NOT NULL
                AND release_timezone IS NOT NULL
                AND release_policy_version_id IS NOT NULL)
    ),
    -- released_at records that the amount became available, and a later hold does not un-record it.
    CONSTRAINT ck_host_payable_allocations_released CHECK (
        release_state NOT IN ('AVAILABLE', 'RESERVED', 'CONSUMED') OR released_at IS NOT NULL
    ),
    CONSTRAINT ck_host_payable_allocations_consumed CHECK (
        release_state <> 'CONSUMED' OR consumed_amount_minor > 0
    ),
    CONSTRAINT ck_host_payable_allocations_service_period CHECK (
        (service_period_start IS NULL) = (service_period_end IS NULL)
        AND (service_period_end IS NULL OR service_period_end >= service_period_start)
    ),
    CONSTRAINT ck_host_payable_allocations_version CHECK (version >= 0)
);

-- The payout planner's query: this host's money, in this currency, that has matured and has
-- something left. The maturity instant is bound by the caller, so no now() appears in the predicate.
CREATE INDEX idx_host_payable_allocations_eligible
    ON host_payable_allocations (host_account_holder_id, currency, scheduled_release_at)
    WHERE release_state IN ('SCHEDULED', 'AVAILABLE')
      AND ownership_state = 'PAYABLE'
      AND remaining_amount_minor > 0;
CREATE INDEX idx_host_payable_allocations_booking
    ON host_payable_allocations (booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX idx_host_payable_allocations_host
    ON host_payable_allocations (host_account_holder_id, currency, created_at DESC);
--rollback DROP TABLE host_payable_allocations;

--changeset ninggiangboy:022-10-host-fund-release-decisions
-- Why an allocation was, or was not, released -- recorded every time the question is asked rather
-- than only when the answer is yes. A host who asks why their money is not available gets the reason
-- codes and the input versions that produced them, and a failed predicate is an explainable state
-- rather than an amount that quietly vanished from the balance.
CREATE TABLE host_fund_release_decisions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payable_allocation_id       UUID NOT NULL,
    evaluated_at               TIMESTAMPTZ NOT NULL,
    decision                    VARCHAR(16) NOT NULL,
    reason_codes                JSONB NOT NULL DEFAULT '[]'::jsonb,
    release_policy_version_id   UUID,
    input_versions              JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_hash                  CHAR(64) NOT NULL,
    next_review_at              TIMESTAMPTZ,
    actor_id                    UUID,
    process_name                VARCHAR(96),
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: release decisions are append-only evidence and the trigger
    -- in changeset 022-30 refuses the cascade.
    CONSTRAINT fk_host_fund_release_decisions_allocation FOREIGN KEY (payable_allocation_id)
        REFERENCES host_payable_allocations (id),
    CONSTRAINT ck_host_fund_release_decisions_decision CHECK (
        decision IN ('RELEASED', 'DEFERRED', 'HELD', 'BLOCKED')
    ),
    CONSTRAINT ck_host_fund_release_decisions_reasons CHECK (
        jsonb_typeof(reason_codes) = 'array' AND jsonb_typeof(input_versions) = 'object'
    ),
    -- Anything but a release owes the host an explanation.
    CONSTRAINT ck_host_fund_release_decisions_explained CHECK (
        decision = 'RELEASED' OR jsonb_array_length(reason_codes) > 0
    ),
    CONSTRAINT ck_host_fund_release_decisions_hash CHECK (input_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_host_fund_release_decisions_allocation
    ON host_fund_release_decisions (payable_allocation_id, evaluated_at DESC);
CREATE INDEX idx_host_fund_release_decisions_review
    ON host_fund_release_decisions (next_review_at)
    WHERE next_review_at IS NOT NULL;
--rollback DROP TABLE host_fund_release_decisions;

--changeset ninggiangboy:022-11-payout-holds
-- A hold stops money from leaving without changing whose money it is. That distinction is the whole
-- point of the table: a dispute, a compliance gap or a cooling-off period makes an amount
-- unavailable, and none of them make it the platform's revenue.
CREATE TABLE payout_holds (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer_domain               VARCHAR(32) NOT NULL,
    hold_type                   VARCHAR(40) NOT NULL,
    host_account_holder_id      UUID NOT NULL,
    booking_id                  UUID,
    payable_allocation_id       UUID,
    payout_instruction_id       UUID,

    currency                    VARCHAR(3),
    maximum_amount_minor        BIGINT,

    reason_code                 VARCHAR(64) NOT NULL,
    public_reason_key           VARCHAR(128),
    policy_version_id           UUID,
    evidence_reference          VARCHAR(255),

    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from              TIMESTAMPTZ NOT NULL,
    review_due_at               TIMESTAMPTZ,
    expires_at                  TIMESTAMPTZ,
    released_at                 TIMESTAMPTZ,
    released_by_actor_id        UUID,
    release_reason_code         VARCHAR(64),
    replaced_by_hold_id         UUID,

    placed_by_actor_id          UUID,
    approval_reference          VARCHAR(128),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payout_holds_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_payout_holds_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_payout_holds_allocation FOREIGN KEY (payable_allocation_id)
        REFERENCES host_payable_allocations (id),
    CONSTRAINT fk_payout_holds_replacement FOREIGN KEY (replaced_by_hold_id)
        REFERENCES payout_holds (id),
    CONSTRAINT ck_payout_holds_issuer CHECK (
        issuer_domain IN ('FINANCE', 'RISK', 'COMPLIANCE', 'SUPPORT', 'LEGAL', 'PAYMENTS', 'MANUAL')
    ),
    CONSTRAINT ck_payout_holds_type CHECK (
        hold_type IN ('BOOKING_INCIDENT', 'PAYMENT_OUTCOME', 'CHARGEBACK', 'IDENTITY',
                      'TAX_READINESS', 'SANCTIONS', 'DESTINATION_COOLING_OFF',
                      'ACCOUNT_TAKEOVER_RISK', 'LEGAL_ORDER', 'NEGATIVE_BALANCE_RECOVERY',
                      'RECONCILIATION_EXCEPTION', 'MANUAL_EMERGENCY')
    ),
    CONSTRAINT ck_payout_holds_state CHECK (
        state IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'REPLACED')
    ),
    -- A capped hold names its currency; an uncapped one holds whatever the scope contains.
    CONSTRAINT ck_payout_holds_amount CHECK (
        (maximum_amount_minor IS NULL) = (currency IS NULL)
        AND (maximum_amount_minor IS NULL OR maximum_amount_minor > 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_payout_holds_release CHECK (
        (state IN ('RELEASED', 'EXPIRED'))
            = (released_at IS NOT NULL AND release_reason_code IS NOT NULL)
    ),
    -- A hold is replaced, not deleted, so the reason the money was ever held stays on the record.
    CONSTRAINT ck_payout_holds_replacement CHECK (
        (state = 'REPLACED') = (replaced_by_hold_id IS NOT NULL)
    ),
    CONSTRAINT ck_payout_holds_expiry CHECK (
        expires_at IS NULL OR expires_at > effective_from
    ),
    CONSTRAINT ck_payout_holds_version CHECK (version >= 0)
);

-- The eligibility check reads active holds by scope; the sweep reads them by expiry and review date,
-- binding its own instant.
CREATE INDEX idx_payout_holds_scope
    ON payout_holds (host_account_holder_id, hold_type) WHERE state = 'ACTIVE';
CREATE INDEX idx_payout_holds_allocation
    ON payout_holds (payable_allocation_id) WHERE payable_allocation_id IS NOT NULL;
CREATE INDEX idx_payout_holds_expiry
    ON payout_holds (expires_at) WHERE state = 'ACTIVE' AND expires_at IS NOT NULL;
CREATE INDEX idx_payout_holds_review
    ON payout_holds (review_due_at) WHERE state = 'ACTIVE' AND review_due_at IS NOT NULL;
--rollback DROP TABLE payout_holds;

--changeset ninggiangboy:022-12-host-reserves
-- A reserve is a bounded, disclosed retention with a basis and a release schedule, not a flag on the
-- host. The cap and the maturity instant are on the row so a host can be told what is being held,
-- why, how much it can ever reach, and when it ends.
CREATE TABLE host_reserves (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_account_holder_id      UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    accounting_book_id          UUID NOT NULL,
    currency                    VARCHAR(3) NOT NULL,

    reserve_basis               VARCHAR(32) NOT NULL,
    policy_version_id           UUID,
    target_amount_minor         BIGINT NOT NULL,
    cap_amount_minor            BIGINT NOT NULL,
    held_amount_minor           BIGINT NOT NULL DEFAULT 0,
    released_amount_minor       BIGINT NOT NULL DEFAULT 0,
    consumed_amount_minor       BIGINT NOT NULL DEFAULT 0,

    state                       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from              TIMESTAMPTZ NOT NULL,
    matures_at                  TIMESTAMPTZ,
    closed_at                   TIMESTAMPTZ,
    disclosure_reference        VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_reserves_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_reserves_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_host_reserves_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT ck_host_reserves_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_host_reserves_basis CHECK (
        reserve_basis IN ('RISK_POLICY', 'CONTRACTUAL', 'CHARGEBACK_EXPOSURE',
                          'NEW_HOST_RAMP', 'REGULATORY', 'MANUAL')
    ),
    CONSTRAINT ck_host_reserves_state CHECK (
        state IN ('ACTIVE', 'MATURED', 'RELEASED', 'CLOSED')
    ),
    CONSTRAINT ck_host_reserves_amounts CHECK (
        target_amount_minor >= 0 AND cap_amount_minor > 0
        AND held_amount_minor >= 0 AND released_amount_minor >= 0 AND consumed_amount_minor >= 0
    ),
    -- The cap is what makes a reserve bounded rather than an open-ended withholding.
    CONSTRAINT ck_host_reserves_cap CHECK (
        target_amount_minor <= cap_amount_minor AND held_amount_minor <= cap_amount_minor
    ),
    -- Nothing can be released or consumed that was never held.
    CONSTRAINT ck_host_reserves_outflow CHECK (
        released_amount_minor + consumed_amount_minor <= held_amount_minor
    ),
    CONSTRAINT ck_host_reserves_closed CHECK (
        (state IN ('RELEASED', 'CLOSED')) = (closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_host_reserves_maturity CHECK (
        matures_at IS NULL OR matures_at > effective_from
    ),
    CONSTRAINT ck_host_reserves_version CHECK (version >= 0)
);

CREATE INDEX idx_host_reserves_host
    ON host_reserves (host_account_holder_id, currency) WHERE state = 'ACTIVE';
CREATE INDEX idx_host_reserves_maturity
    ON host_reserves (matures_at) WHERE state = 'ACTIVE' AND matures_at IS NOT NULL;
--rollback DROP TABLE host_reserves;

--changeset ninggiangboy:022-13-host-reserve-allocations
-- Which payable allocation funded which reserve, and where it went afterwards. Every subtraction
-- from a host's available balance has to name an item; a reserve that is only a number on a profile
-- cannot be explained, appealed, or unwound.
CREATE TABLE host_reserve_allocations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_reserve_id             UUID NOT NULL,
    payable_allocation_id       UUID,
    ledger_posting_id           UUID,
    movement                    VARCHAR(16) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    reason_code                 VARCHAR(64),
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_host_reserve_allocations_reserve FOREIGN KEY (host_reserve_id)
        REFERENCES host_reserves (id),
    CONSTRAINT fk_host_reserve_allocations_allocation FOREIGN KEY (payable_allocation_id)
        REFERENCES host_payable_allocations (id),
    CONSTRAINT fk_host_reserve_allocations_posting FOREIGN KEY (ledger_posting_id)
        REFERENCES ledger_postings (id),
    CONSTRAINT ck_host_reserve_allocations_movement CHECK (
        movement IN ('HELD', 'RELEASED', 'CONSUMED')
    ),
    CONSTRAINT ck_host_reserve_allocations_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_host_reserve_allocations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Money entering a reserve comes from a named payable allocation. Money leaving it does not
    -- necessarily return to one.
    CONSTRAINT ck_host_reserve_allocations_source CHECK (
        movement <> 'HELD' OR payable_allocation_id IS NOT NULL
    )
);

CREATE INDEX idx_host_reserve_allocations_reserve
    ON host_reserve_allocations (host_reserve_id, occurred_at);
CREATE INDEX idx_host_reserve_allocations_allocation
    ON host_reserve_allocations (payable_allocation_id)
    WHERE payable_allocation_id IS NOT NULL;
--rollback DROP TABLE host_reserve_allocations;

--changeset ninggiangboy:022-14-payout-instructions
-- A durable intent to move an exact amount to one destination. The amount is fixed when the
-- instruction is planned and the adapter cannot recompute it, which is what stops a retry from
-- quietly paying out newly eligible money the approval never covered.
--
-- The same submission fence a payment crosses applies here: once an instruction has reached the
-- provider it can never be marked cancelled, and a terminal failure has to say what kind of failure
-- it was. A timeout leaves the instruction in SUBMITTED with no settlement, which is a state that
-- must be queried rather than retried.
CREATE TABLE payout_instructions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    host_account_holder_id      UUID NOT NULL,
    accounting_book_id          UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    destination_claim_id        UUID NOT NULL,
    provider_account_id         UUID,

    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    rail_key                    VARCHAR(64) NOT NULL,

    schedule_policy_version_id  UUID,
    payout_policy_version_id    UUID,
    schedule_cutoff_at          TIMESTAMPTZ,
    schedule_timezone           VARCHAR(64),
    request_input_hash          CHAR(64) NOT NULL,
    idempotency_key             VARCHAR(128) NOT NULL,
    provider_request_key        VARCHAR(128),
    provider_reference          VARCHAR(255),

    state                       VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    -- The instruction is claimed by exactly one worker before it reaches the network, and a late
    -- result from a crashed worker is rejected by comparing the token it carries.
    lease_owner                 VARCHAR(96),
    lease_expires_at            TIMESTAMPTZ,
    fencing_token               BIGINT NOT NULL DEFAULT 0,
    attempt_count               INTEGER NOT NULL DEFAULT 0,
    next_attempt_at             TIMESTAMPTZ,

    submitted_at                TIMESTAMPTZ,
    settled_at                  TIMESTAMPTZ,
    failed_at                   TIMESTAMPTZ,
    returned_at                 TIMESTAMPTZ,
    cancelled_at                TIMESTAMPTZ,
    failure_class               VARCHAR(40),
    failure_reason_code         VARCHAR(64),
    return_reason_code          VARCHAR(64),
    reissued_as_instruction_id  UUID,

    statement_id                UUID,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payout_instructions_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_payout_instructions_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_payout_instructions_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_payout_instructions_destination FOREIGN KEY (destination_claim_id)
        REFERENCES payout_destination_claims (id),
    CONSTRAINT fk_payout_instructions_provider_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_payout_instructions_reissue FOREIGN KEY (reissued_as_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT uk_payout_instructions_public_id UNIQUE (public_id),
    CONSTRAINT uk_payout_instructions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_payout_instructions_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Never a zero or negative transfer. A host owed nothing gets a statement, not a payout.
    CONSTRAINT ck_payout_instructions_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_payout_instructions_state CHECK (
        state IN ('DRAFT', 'ITEMS_RESERVED', 'READY_TO_SUBMIT', 'SUBMITTING', 'SUBMITTED',
                  'IN_TRANSIT', 'PAID', 'FAILED', 'CANCELLED', 'RETURNED', 'RECOVERY_POSTED',
                  'MANUAL_REVIEW')
    ),
    -- The submission fence, as a constraint. An instruction that reached the provider cannot be
    -- cancelled, because the provider may have paid it after the platform stopped waiting.
    CONSTRAINT ck_payout_instructions_fence CHECK (
        state <> 'CANCELLED' OR submitted_at IS NULL
    ),
    CONSTRAINT ck_payout_instructions_cancelled CHECK (
        (state = 'CANCELLED') = (cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_payout_instructions_submitted CHECK (
        state NOT IN ('SUBMITTED', 'IN_TRANSIT', 'PAID', 'RETURNED', 'RECOVERY_POSTED')
            OR submitted_at IS NOT NULL
    ),
    -- PAID means the approved finality condition was met. A provider saying "processed" is not it.
    -- Written as two one-directional clauses: settled_at records that the money arrived, and moving
    -- the instruction to manual review afterwards must not un-record it.
    CONSTRAINT ck_payout_instructions_settled CHECK (
        (state NOT IN ('PAID', 'RETURNED', 'RECOVERY_POSTED') OR settled_at IS NOT NULL)
        AND (settled_at IS NULL
             OR state IN ('PAID', 'RETURNED', 'RECOVERY_POSTED', 'MANUAL_REVIEW'))
    ),
    CONSTRAINT ck_payout_instructions_returned CHECK (
        state NOT IN ('RETURNED', 'RECOVERY_POSTED')
            OR (returned_at IS NOT NULL AND return_reason_code IS NOT NULL)
    ),
    -- A failure without a classification cannot be routed to retry, host action, or manual review.
    -- One-directional for the same reason as settlement: a failed instruction that is escalated to
    -- manual review still failed, and the classification is how anyone knows why.
    CONSTRAINT ck_payout_instructions_failure CHECK (
        (state <> 'FAILED' OR (failed_at IS NOT NULL AND failure_class IS NOT NULL))
        AND (failed_at IS NULL OR failure_class IS NOT NULL)
    ),
    CONSTRAINT ck_payout_instructions_failure_class CHECK (
        failure_class IS NULL OR failure_class IN (
            'PRE_SUBMISSION_RETRYABLE', 'PROVIDER_UNKNOWN', 'RAIL_RETRYABLE',
            'HOST_ACTION_REQUIRED', 'COMPLIANCE_HOLD', 'DESTINATION_TERMINAL',
            'MANUAL_RECONCILIATION')
    ),
    -- A reissue may only exist once the original is proven terminal, or the same money is sent twice.
    CONSTRAINT ck_payout_instructions_reissue CHECK (
        reissued_as_instruction_id IS NULL
            OR (state IN ('FAILED', 'RETURNED', 'RECOVERY_POSTED')
                AND reissued_as_instruction_id <> id)
    ),
    CONSTRAINT ck_payout_instructions_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_payout_instructions_hash CHECK (request_input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_payout_instructions_counters CHECK (
        attempt_count >= 0 AND fencing_token >= 0
    ),
    CONSTRAINT ck_payout_instructions_version CHECK (version >= 0)
);

-- One provider request key per provider account. The adapter reuses it on retry so the provider
-- itself collapses a duplicate submission.
CREATE UNIQUE INDEX uk_payout_instructions_provider_key
    ON payout_instructions (provider_account_id, provider_request_key)
    WHERE provider_request_key IS NOT NULL;

CREATE INDEX idx_payout_instructions_host
    ON payout_instructions (host_account_holder_id, created_at DESC);
CREATE INDEX idx_payout_instructions_ready
    ON payout_instructions (next_attempt_at)
    WHERE state IN ('READY_TO_SUBMIT', 'SUBMITTING');
-- Submitted with no settlement yet: the set that must be queried, never resubmitted.
CREATE INDEX idx_payout_instructions_unresolved
    ON payout_instructions (submitted_at)
    WHERE state IN ('SUBMITTED', 'IN_TRANSIT');
CREATE INDEX idx_payout_instructions_stalled_lease
    ON payout_instructions (lease_expires_at) WHERE lease_owner IS NOT NULL;
--rollback DROP TABLE payout_instructions;

--changeset ninggiangboy:022-15-payout-items
-- Exactly which payable allocations this payout consists of. The partial unique index is the real
-- defence against two concurrent planners selecting the same money: at most one live item may hold
-- any allocation, whatever the application does with its locks.
CREATE TABLE payout_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_instruction_id       UUID NOT NULL,
    payable_allocation_id       UUID NOT NULL,
    source_posting_id           UUID,
    selected_amount_minor       BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    state                       VARCHAR(16) NOT NULL DEFAULT 'RESERVED',
    host_reserve_id             UUID,
    host_recovery_id            UUID,
    released_at                 TIMESTAMPTZ,
    settled_at                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payout_items_instruction FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT fk_payout_items_allocation FOREIGN KEY (payable_allocation_id)
        REFERENCES host_payable_allocations (id),
    CONSTRAINT fk_payout_items_posting FOREIGN KEY (source_posting_id)
        REFERENCES ledger_postings (id),
    CONSTRAINT fk_payout_items_reserve FOREIGN KEY (host_reserve_id)
        REFERENCES host_reserves (id),
    CONSTRAINT uk_payout_items_allocation UNIQUE (payout_instruction_id, payable_allocation_id),
    CONSTRAINT ck_payout_items_amount CHECK (selected_amount_minor > 0),
    CONSTRAINT ck_payout_items_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payout_items_state CHECK (
        state IN ('RESERVED', 'SETTLED', 'RELEASED', 'RETURNED')
    ),
    -- Releasing an item back to the balance is a fact with an instant, not the absence of one.
    CONSTRAINT ck_payout_items_released CHECK (
        (state = 'RELEASED') = (released_at IS NOT NULL)
    ),
    CONSTRAINT ck_payout_items_settled CHECK (
        state NOT IN ('SETTLED', 'RETURNED') OR settled_at IS NOT NULL
    ),
    CONSTRAINT ck_payout_items_version CHECK (version >= 0)
);

-- Exactly once at the platform boundary: an allocation may be held by one live item only. A released
-- item frees the allocation for a later payout; a returned one does not, because the money already
-- left and comes back through recovery.
CREATE UNIQUE INDEX uk_payout_items_live_allocation
    ON payout_items (payable_allocation_id)
    WHERE state IN ('RESERVED', 'SETTLED', 'RETURNED');

CREATE INDEX idx_payout_items_instruction ON payout_items (payout_instruction_id);
--rollback DROP TABLE payout_items;

--changeset ninggiangboy:022-16-payout-operations
-- One attempt to make the provider do something: submit, query, cancel, or retry. Separated from the
-- instruction for the same reason payment operations are separated from obligations -- one durable
-- intent produces many external calls whose outcomes arrive late, out of order, and sometimes twice.
CREATE TABLE payout_operations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_instruction_id       UUID NOT NULL,
    provider_account_id         UUID,
    operation_type              VARCHAR(16) NOT NULL,
    attempt_number              INTEGER NOT NULL,

    amount_minor                BIGINT,
    currency                    VARCHAR(3),

    provider_request_key        VARCHAR(128),
    provider_reference          VARCHAR(255),
    request_digest              CHAR(64),
    state                       VARCHAR(32) NOT NULL DEFAULT 'PREPARED',
    normalized_provider_state   VARCHAR(24),
    failure_category            VARCHAR(40),
    provider_failure_code       VARCHAR(96),

    lease_owner                 VARCHAR(96),
    lease_expires_at            TIMESTAMPTZ,
    fencing_token               BIGINT NOT NULL DEFAULT 0,
    next_attempt_at             TIMESTAMPTZ,

    submitted_at                TIMESTAMPTZ,
    resolved_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payout_operations_instruction FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT fk_payout_operations_provider_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT uk_payout_operations_attempt
        UNIQUE (payout_instruction_id, operation_type, attempt_number),
    CONSTRAINT ck_payout_operations_type CHECK (
        operation_type IN ('SUBMIT', 'QUERY', 'CANCEL', 'RETRY')
    ),
    CONSTRAINT ck_payout_operations_attempt CHECK (attempt_number > 0),
    -- A query moves no money and therefore carries no amount; everything else does.
    CONSTRAINT ck_payout_operations_monetary CHECK (
        (operation_type = 'QUERY' AND amount_minor IS NULL AND currency IS NULL)
        OR (operation_type <> 'QUERY' AND amount_minor > 0 AND currency ~ '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_payout_operations_state CHECK (
        state IN ('PREPARED', 'SUBMITTING', 'SUBMITTED', 'SUCCEEDED', 'FAILED',
                  'UNKNOWN', 'CANCELLED_BEFORE_SUBMISSION')
    ),
    -- The submission fence again: only an operation that never left can be abandoned outright.
    CONSTRAINT ck_payout_operations_fence CHECK (
        state <> 'CANCELLED_BEFORE_SUBMISSION' OR submitted_at IS NULL
    ),
    CONSTRAINT ck_payout_operations_failure CHECK (
        state <> 'FAILED' OR failure_category IS NOT NULL
    ),
    CONSTRAINT ck_payout_operations_resolved CHECK (
        (state IN ('SUCCEEDED', 'FAILED', 'CANCELLED_BEFORE_SUBMISSION'))
            = (resolved_at IS NOT NULL)
    ),
    CONSTRAINT ck_payout_operations_lease CHECK (
        (lease_owner IS NULL) = (lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_payout_operations_digest CHECK (
        request_digest IS NULL OR request_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_payout_operations_fencing CHECK (fencing_token >= 0),
    CONSTRAINT ck_payout_operations_version CHECK (version >= 0)
);

CREATE INDEX idx_payout_operations_instruction
    ON payout_operations (payout_instruction_id, created_at);
CREATE INDEX idx_payout_operations_ready
    ON payout_operations (next_attempt_at) WHERE state IN ('PREPARED', 'UNKNOWN');
CREATE INDEX idx_payout_operations_unresolved
    ON payout_operations (submitted_at) WHERE state IN ('SUBMITTING', 'SUBMITTED', 'UNKNOWN');
CREATE INDEX idx_payout_operations_reference
    ON payout_operations (provider_reference) WHERE provider_reference IS NOT NULL;
--rollback DROP TABLE payout_operations;

--changeset ninggiangboy:022-17-payout-observations
-- What the provider or bank actually said, kept verbatim and never rewritten. A returned transfer
-- does not turn its settlement into a non-event; it is a later observation that funds a recovery.
-- The operation reference is nullable because evidence sometimes arrives for a transfer the platform
-- cannot yet place, and that orphan is exactly the row reconciliation needs to keep.
CREATE TABLE payout_observations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_operation_id         UUID,
    payout_instruction_id       UUID,
    provider_account_id         UUID NOT NULL,
    source                      VARCHAR(16) NOT NULL,
    provider_event_id           VARCHAR(255),
    provider_reference          VARCHAR(255),

    normalized_state            VARCHAR(24) NOT NULL,
    reducer_outcome             VARCHAR(16) NOT NULL,
    amount_minor                BIGINT,
    fee_amount_minor            BIGINT,
    currency                    VARCHAR(3),
    reason_code                 VARCHAR(96),

    verification_method         VARCHAR(24) NOT NULL,
    signing_key_version         VARCHAR(32),
    payload_digest              CHAR(64) NOT NULL,
    payload_reference           VARCHAR(255),
    provider_occurred_at        TIMESTAMPTZ,
    observed_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: observations are append-only and the trigger in changeset
    -- 022-30 refuses the cascade.
    CONSTRAINT fk_payout_observations_operation FOREIGN KEY (payout_operation_id)
        REFERENCES payout_operations (id),
    CONSTRAINT fk_payout_observations_instruction FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT fk_payout_observations_provider_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT ck_payout_observations_source CHECK (
        source IN ('WEBHOOK', 'API_QUERY', 'REPORT_FILE', 'BANK_FEED', 'MANUAL')
    ),
    CONSTRAINT ck_payout_observations_state CHECK (
        normalized_state IN ('ACCEPTED', 'IN_TRANSIT', 'PAID', 'FAILED', 'RETURNED',
                             'CANCELLED', 'UNKNOWN')
    ),
    -- The same reducer vocabulary migration 021 uses for payment evidence. What a reducer did with
    -- a provider fact is the same question on both sides, and one vocabulary keeps it answerable.
    CONSTRAINT ck_payout_observations_reducer CHECK (
        reducer_outcome IN ('APPLIED', 'IGNORED_STALE', 'QUARANTINED', 'UNMAPPED')
    ),
    -- An observation that could not be attached to an operation is either unmapped or quarantined.
    -- Anything else claiming no operation is a defect in the reducer.
    CONSTRAINT ck_payout_observations_mapping CHECK (
        payout_operation_id IS NOT NULL OR reducer_outcome IN ('UNMAPPED', 'QUARANTINED')
    ),
    -- A webhook is only evidence if its signature was checked against a known key version.
    CONSTRAINT ck_payout_observations_signature CHECK (
        source <> 'WEBHOOK'
            OR (verification_method = 'WEBHOOK_SIGNATURE' AND signing_key_version IS NOT NULL)
    ),
    CONSTRAINT ck_payout_observations_verification CHECK (
        verification_method IN ('WEBHOOK_SIGNATURE', 'AUTHENTICATED_API', 'SIGNED_FILE',
                                'BANK_CHANNEL', 'OPERATOR_ATTESTED')
    ),
    CONSTRAINT ck_payout_observations_money CHECK (
        (amount_minor IS NULL OR amount_minor >= 0)
        AND (fee_amount_minor IS NULL OR fee_amount_minor >= 0)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND (amount_minor IS NULL OR currency IS NOT NULL)
    ),
    CONSTRAINT ck_payout_observations_digest CHECK (payload_digest ~ '^[0-9a-f]{64}$')
);

-- The same provider event delivered twice is one row, so a redelivery cannot be applied a second
-- time.
CREATE UNIQUE INDEX uk_payout_observations_event
    ON payout_observations (provider_account_id, provider_event_id)
    WHERE provider_event_id IS NOT NULL;

CREATE INDEX idx_payout_observations_operation
    ON payout_observations (payout_operation_id, observed_at)
    WHERE payout_operation_id IS NOT NULL;
CREATE INDEX idx_payout_observations_reference
    ON payout_observations (provider_reference) WHERE provider_reference IS NOT NULL;
CREATE INDEX idx_payout_observations_orphans
    ON payout_observations (observed_at) WHERE payout_operation_id IS NULL;
--rollback DROP TABLE payout_observations;

--changeset ninggiangboy:022-18-host-statements
-- What the host is told, frozen at the moment they were told it. An issued statement is never
-- edited: a later correction is a new version that links back, so a host who saved a PDF and a host
-- who reloads the page are looking at the same numbers with the same identity.
CREATE TABLE host_statements (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    host_account_holder_id      UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    accounting_book_id          UUID NOT NULL,
    currency                    VARCHAR(3) NOT NULL,

    period_range                DATERANGE NOT NULL,
    period_timezone             VARCHAR(64) NOT NULL,
    payout_instruction_id       UUID,

    opening_balance_minor       BIGINT NOT NULL DEFAULT 0,
    gross_entitlement_minor     BIGINT NOT NULL DEFAULT 0,
    deductions_minor            BIGINT NOT NULL DEFAULT 0,
    withholding_minor           BIGINT NOT NULL DEFAULT 0,
    reserved_minor              BIGINT NOT NULL DEFAULT 0,
    held_minor                  BIGINT NOT NULL DEFAULT 0,
    recovered_minor             BIGINT NOT NULL DEFAULT 0,
    paid_out_minor              BIGINT NOT NULL DEFAULT 0,
    returned_minor              BIGINT NOT NULL DEFAULT 0,
    closing_balance_minor       BIGINT NOT NULL DEFAULT 0,

    statement_version           INTEGER NOT NULL DEFAULT 1,
    status                      VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    data_hash                   CHAR(64) NOT NULL,
    document_reference          VARCHAR(255),
    generated_at                TIMESTAMPTZ,
    issued_at                   TIMESTAMPTZ,
    superseded_by_statement_id  UUID,
    failure_reason_code         VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_statements_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_statements_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_host_statements_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_host_statements_payout FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT fk_host_statements_supersession FOREIGN KEY (superseded_by_statement_id)
        REFERENCES host_statements (id),
    CONSTRAINT uk_host_statements_public_id UNIQUE (public_id),
    CONSTRAINT uk_host_statements_period UNIQUE (
        host_account_holder_id, legal_entity_id, currency, period_range, statement_version
    ),
    CONSTRAINT ck_host_statements_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_host_statements_range CHECK (NOT isempty(period_range)),
    CONSTRAINT ck_host_statements_status CHECK (
        status IN ('DRAFT', 'GENERATED', 'ISSUED', 'FAILED', 'SUPERSEDED_BY_CORRECTION')
    ),
    CONSTRAINT ck_host_statements_version_number CHECK (statement_version > 0),
    CONSTRAINT ck_host_statements_generated CHECK (
        status NOT IN ('GENERATED', 'ISSUED', 'SUPERSEDED_BY_CORRECTION')
            OR generated_at IS NOT NULL
    ),
    -- issued_at records that the host was told, and a later correction does not un-tell them.
    CONSTRAINT ck_host_statements_issued CHECK (
        (status <> 'ISSUED' OR issued_at IS NOT NULL)
        AND (issued_at IS NULL OR status IN ('ISSUED', 'SUPERSEDED_BY_CORRECTION'))
    ),
    CONSTRAINT ck_host_statements_supersession CHECK (
        (status = 'SUPERSEDED_BY_CORRECTION') = (superseded_by_statement_id IS NOT NULL)
    ),
    CONSTRAINT ck_host_statements_failure CHECK (
        (status = 'FAILED') = (failure_reason_code IS NOT NULL)
    ),
    CONSTRAINT ck_host_statements_amounts CHECK (
        gross_entitlement_minor >= 0 AND deductions_minor >= 0 AND withholding_minor >= 0
        AND reserved_minor >= 0 AND held_minor >= 0 AND recovered_minor >= 0
        AND paid_out_minor >= 0 AND returned_minor >= 0
    ),
    CONSTRAINT ck_host_statements_hash CHECK (data_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_host_statements_version CHECK (version >= 0)
);

CREATE INDEX idx_host_statements_host
    ON host_statements (host_account_holder_id, currency, lower(period_range) DESC);
CREATE INDEX idx_host_statements_payout
    ON host_statements (payout_instruction_id) WHERE payout_instruction_id IS NOT NULL;
--rollback DROP TABLE host_statements;

--changeset ninggiangboy:022-19-host-statement-lines
-- Every number on a statement traces to a posting, a payout item, or an explicit adjustment. Totals
-- are composed from these lines rather than recomputed from today's fee, tax, or cancellation
-- settings, which is what makes a two-year-old statement still reproducible.
CREATE TABLE host_statement_lines (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    statement_id                UUID NOT NULL,
    line_number                 SMALLINT NOT NULL,
    line_type                   VARCHAR(32) NOT NULL,
    direction                   VARCHAR(8) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    description_key             VARCHAR(128),
    reason_code                 VARCHAR(64),

    ledger_posting_id           UUID,
    payout_item_id              UUID,
    payable_allocation_id       UUID,
    booking_id                  UUID,
    service_period_start        DATE,
    service_period_end          DATE,
    document_reference          VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: statement lines are frozen once their statement is issued,
    -- so the cascade would be a promise the trigger in changeset 022-32 refuses to keep.
    CONSTRAINT fk_host_statement_lines_statement FOREIGN KEY (statement_id)
        REFERENCES host_statements (id),
    CONSTRAINT fk_host_statement_lines_posting FOREIGN KEY (ledger_posting_id)
        REFERENCES ledger_postings (id),
    CONSTRAINT fk_host_statement_lines_payout_item FOREIGN KEY (payout_item_id)
        REFERENCES payout_items (id),
    CONSTRAINT fk_host_statement_lines_allocation FOREIGN KEY (payable_allocation_id)
        REFERENCES host_payable_allocations (id),
    CONSTRAINT fk_host_statement_lines_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_host_statement_lines_number UNIQUE (statement_id, line_number),
    CONSTRAINT ck_host_statement_lines_number CHECK (line_number > 0),
    CONSTRAINT ck_host_statement_lines_type CHECK (
        line_type IN ('GROSS_ENTITLEMENT', 'HOST_FUNDED_DISCOUNT', 'COMMISSION', 'PLATFORM_FEE',
                      'FEE_TAX', 'WITHHOLDING', 'RESERVE_HELD', 'RESERVE_RELEASED',
                      'REFUND', 'CANCELLATION', 'DISPUTE', 'CHARGEBACK', 'RECOVERY',
                      'ADJUSTMENT', 'PAYOUT', 'PAYOUT_FEE', 'PAYOUT_RETURN')
    ),
    -- Unsigned amounts with an explicit direction, the same rule the booking lines follow.
    CONSTRAINT ck_host_statement_lines_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_host_statement_lines_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_host_statement_lines_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Every line names where it came from. A line with no source is a plug.
    CONSTRAINT ck_host_statement_lines_source CHECK (
        ledger_posting_id IS NOT NULL
        OR payout_item_id IS NOT NULL
        OR payable_allocation_id IS NOT NULL
    ),
    CONSTRAINT ck_host_statement_lines_service_period CHECK (
        (service_period_start IS NULL) = (service_period_end IS NULL)
        AND (service_period_end IS NULL OR service_period_end >= service_period_start)
    )
);

CREATE INDEX idx_host_statement_lines_statement
    ON host_statement_lines (statement_id, line_number);
CREATE INDEX idx_host_statement_lines_booking
    ON host_statement_lines (booking_id) WHERE booking_id IS NOT NULL;
--rollback DROP TABLE host_statement_lines;

--changeset ninggiangboy:022-20-external-financial-artifacts
-- The file or feed a reconciliation ran against, kept by reference and hash rather than re-fetched.
-- A settlement report that is downloaded again a month later may not be the report that was
-- reconciled, and an argument about the numbers has to be settled from what was actually read.
CREATE TABLE external_financial_artifacts (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_kind                 VARCHAR(24) NOT NULL,
    provider_account_id         UUID,
    legal_entity_id             UUID NOT NULL,
    bank_account_reference      VARCHAR(128),
    currency                    VARCHAR(3),

    coverage_start              TIMESTAMPTZ NOT NULL,
    coverage_end                TIMESTAMPTZ NOT NULL,
    cutoff_timezone             VARCHAR(64) NOT NULL,
    artifact_reference          VARCHAR(255) NOT NULL,
    content_hash                CHAR(64) NOT NULL,
    schema_version              VARCHAR(32) NOT NULL,
    parser_version              VARCHAR(32) NOT NULL,
    sequence_number             INTEGER,
    completeness                VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    retention_class             VARCHAR(24) NOT NULL DEFAULT 'EXTENDED',
    supersedes_artifact_id      UUID,

    generated_at                TIMESTAMPTZ,
    received_at                 TIMESTAMPTZ NOT NULL,
    parsed_at                   TIMESTAMPTZ,
    parse_failure_code          VARCHAR(64),
    row_count                   INTEGER NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_external_financial_artifacts_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_external_financial_artifacts_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_external_financial_artifacts_supersedes FOREIGN KEY (supersedes_artifact_id)
        REFERENCES external_financial_artifacts (id),
    -- The same bytes ingested twice are one artifact, so a re-uploaded report cannot be reconciled
    -- a second time and double every row it contains.
    CONSTRAINT uk_external_financial_artifacts_content UNIQUE (content_hash),
    CONSTRAINT ck_external_financial_artifacts_kind CHECK (
        source_kind IN ('PROVIDER_REPORT', 'PROVIDER_API', 'BANK_STATEMENT',
                        'BANK_FEED', 'MANUAL_UPLOAD')
    ),
    CONSTRAINT ck_external_financial_artifacts_completeness CHECK (
        completeness IN ('UNKNOWN', 'COMPLETE', 'PARTIAL', 'CORRUPT')
    ),
    CONSTRAINT ck_external_financial_artifacts_retention CHECK (
        retention_class IN ('SHORT', 'STANDARD', 'EXTENDED', 'PERMANENT')
    ),
    CONSTRAINT ck_external_financial_artifacts_coverage CHECK (coverage_end > coverage_start),
    CONSTRAINT ck_external_financial_artifacts_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_external_financial_artifacts_currency CHECK (
        currency IS NULL OR currency ~ '^[A-Z]{3}$'
    ),
    -- A parsed artifact is one the rows can be trusted to have come from; a failed parse says why.
    CONSTRAINT ck_external_financial_artifacts_parse CHECK (
        (parsed_at IS NOT NULL AND parse_failure_code IS NULL)
        OR (parsed_at IS NULL)
    ),
    CONSTRAINT ck_external_financial_artifacts_rows CHECK (row_count >= 0),
    CONSTRAINT ck_external_financial_artifacts_supersedes CHECK (
        supersedes_artifact_id IS NULL OR supersedes_artifact_id <> id
    )
);

CREATE INDEX idx_external_financial_artifacts_coverage
    ON external_financial_artifacts (source_kind, provider_account_id, coverage_end DESC);
CREATE INDEX idx_external_financial_artifacts_unparsed
    ON external_financial_artifacts (received_at) WHERE parsed_at IS NULL;
--rollback DROP TABLE external_financial_artifacts;

--changeset ninggiangboy:022-21-external-financial-records
-- One normalized row out of an artifact, stored so matching can run against the database instead of
-- re-parsing a file. The row hash makes a duplicate within one artifact detectable, which is a real
-- provider behaviour and not a hypothetical one.
CREATE TABLE external_financial_records (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id                 UUID NOT NULL,
    native_row_number           INTEGER,
    native_reference            VARCHAR(255),
    record_type                 VARCHAR(32) NOT NULL,

    amount_minor                BIGINT NOT NULL,
    fee_amount_minor            BIGINT,
    currency                    VARCHAR(3) NOT NULL,
    native_status               VARCHAR(64),
    normalized_status           VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
    occurred_at                 TIMESTAMPTZ,
    available_at                TIMESTAMPTZ,

    provider_object_ref         VARCHAR(255),
    platform_reference          VARCHAR(128),
    settlement_batch_reference  VARCHAR(128),
    counterparty_display        VARCHAR(128),
    row_hash                    CHAR(64) NOT NULL,
    match_state                 VARCHAR(16) NOT NULL DEFAULT 'UNMATCHED',
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: external records are append-only evidence and the trigger
    -- in changeset 022-30 refuses the cascade.
    CONSTRAINT fk_external_financial_records_artifact FOREIGN KEY (artifact_id)
        REFERENCES external_financial_artifacts (id),
    CONSTRAINT uk_external_financial_records_row UNIQUE (artifact_id, row_hash),
    CONSTRAINT ck_external_financial_records_type CHECK (
        record_type IN ('CHARGE', 'REFUND', 'FEE', 'ADJUSTMENT', 'TRANSFER', 'PAYOUT',
                        'PAYOUT_RETURN', 'CHARGEBACK', 'CHARGEBACK_REVERSAL', 'OTHER')
    ),
    CONSTRAINT ck_external_financial_records_status CHECK (
        normalized_status IN ('PENDING', 'AVAILABLE', 'SETTLED', 'FAILED', 'RETURNED', 'UNKNOWN')
    ),
    CONSTRAINT ck_external_financial_records_match_state CHECK (
        match_state IN ('UNMATCHED', 'MATCHED', 'EXCEPTION', 'OUT_OF_SCOPE')
    ),
    CONSTRAINT ck_external_financial_records_money CHECK (
        amount_minor >= 0
        AND (fee_amount_minor IS NULL OR fee_amount_minor >= 0)
        AND currency ~ '^[A-Z]{3}$'
    ),
    CONSTRAINT ck_external_financial_records_hash CHECK (row_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_external_financial_records_row_number CHECK (
        native_row_number IS NULL OR native_row_number > 0
    )
);

CREATE INDEX idx_external_financial_records_artifact
    ON external_financial_records (artifact_id, native_row_number);
CREATE INDEX idx_external_financial_records_object
    ON external_financial_records (provider_object_ref) WHERE provider_object_ref IS NOT NULL;
CREATE INDEX idx_external_financial_records_platform_ref
    ON external_financial_records (platform_reference) WHERE platform_reference IS NOT NULL;
CREATE INDEX idx_external_financial_records_unmatched
    ON external_financial_records (currency, occurred_at) WHERE match_state = 'UNMATCHED';
--rollback DROP TABLE external_financial_records;

--changeset ninggiangboy:022-22-finance-reconciliation-runs
-- One bounded comparison: one control layer, one account, one currency, one coverage interval. The
-- layer is on the row because a green provider report proves nothing about whether the bank agrees
-- with the ledger, and one aggregate total must never be allowed to stand in for another control.
CREATE TABLE finance_reconciliation_runs (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    control_layer               VARCHAR(40) NOT NULL,
    accounting_book_id          UUID,
    legal_entity_id             UUID NOT NULL,
    provider_account_id         UUID,
    ledger_account_id           UUID,
    artifact_id                 UUID,
    currency                    VARCHAR(3) NOT NULL,

    coverage_start              TIMESTAMPTZ NOT NULL,
    coverage_end                TIMESTAMPTZ NOT NULL,
    cutoff_timezone             VARCHAR(64) NOT NULL,
    rule_version                VARCHAR(32) NOT NULL,
    materiality_version         VARCHAR(32) NOT NULL,
    input_watermark_at          TIMESTAMPTZ,
    input_hash                  CHAR(64),

    state                       VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    internal_record_count       INTEGER NOT NULL DEFAULT 0,
    external_record_count       INTEGER NOT NULL DEFAULT 0,
    matched_count               INTEGER NOT NULL DEFAULT 0,
    exception_count             INTEGER NOT NULL DEFAULT 0,
    matched_amount_minor        BIGINT NOT NULL DEFAULT 0,
    difference_amount_minor     BIGINT NOT NULL DEFAULT 0,

    started_at                  TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,
    failure_class               VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_finance_reconciliation_runs_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_finance_reconciliation_runs_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_finance_reconciliation_runs_provider_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_finance_reconciliation_runs_ledger_account FOREIGN KEY (ledger_account_id)
        REFERENCES ledger_accounts (id),
    CONSTRAINT fk_finance_reconciliation_runs_artifact FOREIGN KEY (artifact_id)
        REFERENCES external_financial_artifacts (id),
    CONSTRAINT ck_finance_reconciliation_runs_layer CHECK (
        control_layer IN ('SNAPSHOT_TO_OBLIGATION', 'OBLIGATION_TO_CAPTURE',
                          'PAYMENT_TO_JOURNAL', 'PROVIDER_REPORT_TO_CLEARING',
                          'SETTLEMENT_TO_BANK', 'PAYABLE_TO_PAYOUT_ITEMS',
                          'PAYOUT_TO_IN_TRANSIT', 'RETURN_TO_RECOVERY',
                          'TAX_TO_DOCUMENT', 'SUBLEDGER_TO_GENERAL_LEDGER')
    ),
    CONSTRAINT ck_finance_reconciliation_runs_state CHECK (
        state IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_finance_reconciliation_runs_completion CHECK (
        (state IN ('COMPLETED', 'FAILED', 'CANCELLED')) = (completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_finance_reconciliation_runs_failure CHECK (
        state <> 'FAILED' OR failure_class IS NOT NULL
    ),
    CONSTRAINT ck_finance_reconciliation_runs_coverage CHECK (coverage_end > coverage_start),
    CONSTRAINT ck_finance_reconciliation_runs_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_reconciliation_runs_counts CHECK (
        internal_record_count >= 0 AND external_record_count >= 0
        AND matched_count >= 0 AND exception_count >= 0
    ),
    CONSTRAINT ck_finance_reconciliation_runs_hash CHECK (
        input_hash IS NULL OR input_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_finance_reconciliation_runs_version CHECK (version >= 0)
);

CREATE INDEX idx_finance_reconciliation_runs_layer
    ON finance_reconciliation_runs (control_layer, coverage_end DESC);
CREATE INDEX idx_finance_reconciliation_runs_artifact
    ON finance_reconciliation_runs (artifact_id) WHERE artifact_id IS NOT NULL;
--rollback DROP TABLE finance_reconciliation_runs;

--changeset ninggiangboy:022-23-finance-reconciliation-matches
-- The verdict on one comparison, with both sides named. Fuzzy and model-assisted candidates may be
-- recorded here but may never auto-close: the rule tier that produced the match is stored so the
-- difference between "the provider object id matched exactly" and "the amounts were close enough" is
-- always visible afterwards.
CREATE TABLE finance_reconciliation_matches (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                      UUID NOT NULL,
    outcome                     VARCHAR(32) NOT NULL,
    rule_tier                   SMALLINT NOT NULL,
    rule_version                VARCHAR(32) NOT NULL,
    confidence_class            VARCHAR(16) NOT NULL DEFAULT 'DETERMINISTIC',

    external_record_id          UUID,
    ledger_transaction_id       UUID,
    ledger_posting_id           UUID,
    payout_instruction_id       UUID,
    payment_operation_id        UUID,

    currency                    VARCHAR(3),
    internal_amount_minor       BIGINT,
    external_amount_minor       BIGINT,
    difference_amount_minor     BIGINT,
    materiality                 VARCHAR(16) NOT NULL DEFAULT 'LOW',

    case_id                     UUID,
    supersedes_match_id         UUID,
    next_action_code            VARCHAR(64),
    matched_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: a match is the immutable record of what was compared and
    -- the append-only trigger refuses the cascade.
    CONSTRAINT fk_finance_reconciliation_matches_run FOREIGN KEY (run_id)
        REFERENCES finance_reconciliation_runs (id),
    CONSTRAINT fk_finance_reconciliation_matches_external FOREIGN KEY (external_record_id)
        REFERENCES external_financial_records (id),
    CONSTRAINT fk_finance_reconciliation_matches_transaction FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_finance_reconciliation_matches_posting FOREIGN KEY (ledger_posting_id)
        REFERENCES ledger_postings (id),
    CONSTRAINT fk_finance_reconciliation_matches_payout FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT fk_finance_reconciliation_matches_operation FOREIGN KEY (payment_operation_id)
        REFERENCES payment_operations (id),
    CONSTRAINT fk_finance_reconciliation_matches_supersedes FOREIGN KEY (supersedes_match_id)
        REFERENCES finance_reconciliation_matches (id),
    CONSTRAINT ck_finance_reconciliation_matches_outcome CHECK (
        outcome IN ('MATCHED_EXACT', 'MATCHED_AGGREGATE', 'EXPECTED_TIMING_DIFFERENCE',
                    'AMOUNT_MISMATCH', 'CURRENCY_MISMATCH', 'STATUS_MISMATCH',
                    'DUPLICATE_EXTERNAL', 'DUPLICATE_INTERNAL', 'MISSING_INTERNAL',
                    'MISSING_EXTERNAL', 'UNKNOWN_REFERENCE', 'OUTSIDE_COVERAGE',
                    'PARSER_OR_SCHEMA_ERROR')
    ),
    CONSTRAINT ck_finance_reconciliation_matches_confidence CHECK (
        confidence_class IN ('DETERMINISTIC', 'BOUNDED', 'SUGGESTED')
    ),
    CONSTRAINT ck_finance_reconciliation_matches_materiality CHECK (
        materiality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    -- The matching ladder is ordered, and the tier that produced a result is part of the result.
    CONSTRAINT ck_finance_reconciliation_matches_tier CHECK (rule_tier BETWEEN 1 AND 6),
    -- Only the first four tiers are deterministic enough to auto-match; a bounded composite or a
    -- suggestion is a candidate for a person, never a closed comparison.
    CONSTRAINT ck_finance_reconciliation_matches_auto CHECK (
        outcome NOT IN ('MATCHED_EXACT', 'MATCHED_AGGREGATE')
            OR (rule_tier <= 4 AND confidence_class = 'DETERMINISTIC')
    ),
    -- A match names both sides. Missing-internal and missing-external are precisely the rows that
    -- cannot, and say which side is absent.
    CONSTRAINT ck_finance_reconciliation_matches_sides CHECK (
        (outcome NOT IN ('MATCHED_EXACT', 'MATCHED_AGGREGATE')
            OR (external_record_id IS NOT NULL
                AND (ledger_transaction_id IS NOT NULL
                     OR ledger_posting_id IS NOT NULL
                     OR payout_instruction_id IS NOT NULL
                     OR payment_operation_id IS NOT NULL)))
        AND (outcome <> 'MISSING_EXTERNAL' OR external_record_id IS NULL)
    ),
    CONSTRAINT ck_finance_reconciliation_matches_money CHECK (
        (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND (internal_amount_minor IS NULL OR internal_amount_minor >= 0)
        AND (external_amount_minor IS NULL OR external_amount_minor >= 0)
    ),
    CONSTRAINT ck_finance_reconciliation_matches_supersedes CHECK (
        supersedes_match_id IS NULL OR supersedes_match_id <> id
    )
);

CREATE INDEX idx_finance_reconciliation_matches_run
    ON finance_reconciliation_matches (run_id, outcome);
CREATE INDEX idx_finance_reconciliation_matches_external
    ON finance_reconciliation_matches (external_record_id) WHERE external_record_id IS NOT NULL;
CREATE INDEX idx_finance_reconciliation_matches_exceptions
    ON finance_reconciliation_matches (materiality, matched_at DESC)
    WHERE outcome NOT IN ('MATCHED_EXACT', 'MATCHED_AGGREGATE', 'EXPECTED_TIMING_DIFFERENCE');
--rollback DROP TABLE finance_reconciliation_matches;

--changeset ninggiangboy:022-24-finance-reconciliation-cases
-- A difference somebody owns until it is explained. Resolution links the evidence and any approved
-- correction; it never edits either side of the comparison, and it never posts a balancing entry
-- purely to make a report agree.
CREATE TABLE finance_reconciliation_cases (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    case_type                   VARCHAR(32) NOT NULL,
    severity                    VARCHAR(16) NOT NULL DEFAULT 'LOW',
    materiality                 VARCHAR(16) NOT NULL DEFAULT 'LOW',
    opened_by_run_id            UUID,
    legal_entity_id             UUID NOT NULL,
    accounting_book_id          UUID,
    provider_account_id         UUID,

    currency                    VARCHAR(3),
    exposure_amount_minor       BIGINT,
    booking_id                  UUID,
    host_account_holder_id      UUID,
    payout_instruction_id       UUID,
    ledger_transaction_id       UUID,
    external_record_id          UUID,

    state                       VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    blocks_payout               BOOLEAN NOT NULL DEFAULT false,
    blocks_period_close         BOOLEAN NOT NULL DEFAULT false,
    assigned_to_actor_id        UUID,
    due_at                      TIMESTAMPTZ,

    proposed_resolution         VARCHAR(40),
    resolution                  VARCHAR(40),
    resolution_note             VARCHAR(512),
    correction_transaction_id   UUID,
    approved_by_actor_id        UUID,
    resolved_by_actor_id        UUID,
    resolved_at                 TIMESTAMPTZ,
    reopened_count              INTEGER NOT NULL DEFAULT 0,
    opened_at                   TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_finance_reconciliation_cases_run FOREIGN KEY (opened_by_run_id)
        REFERENCES finance_reconciliation_runs (id),
    CONSTRAINT fk_finance_reconciliation_cases_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_finance_reconciliation_cases_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_finance_reconciliation_cases_provider_account FOREIGN KEY (provider_account_id)
        REFERENCES provider_accounts (id),
    CONSTRAINT fk_finance_reconciliation_cases_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_finance_reconciliation_cases_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_finance_reconciliation_cases_payout FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT fk_finance_reconciliation_cases_transaction FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT fk_finance_reconciliation_cases_external FOREIGN KEY (external_record_id)
        REFERENCES external_financial_records (id),
    CONSTRAINT fk_finance_reconciliation_cases_correction FOREIGN KEY (correction_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT uk_finance_reconciliation_cases_public_id UNIQUE (public_id),
    CONSTRAINT ck_finance_reconciliation_cases_type CHECK (
        case_type IN ('MISSING_INTERNAL', 'MISSING_EXTERNAL', 'AMOUNT_MISMATCH',
                      'CURRENCY_MISMATCH', 'STATUS_MISMATCH', 'DUPLICATE', 'UNKNOWN_REFERENCE',
                      'FEE_VARIANCE', 'TIMING_BREACH', 'BALANCE_BREAK', 'PARSER_ERROR')
    ),
    CONSTRAINT ck_finance_reconciliation_cases_severity CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_finance_reconciliation_cases_materiality CHECK (
        materiality IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),
    CONSTRAINT ck_finance_reconciliation_cases_state CHECK (
        state IN ('OPEN', 'TRIAGED', 'ASSIGNED', 'INVESTIGATING', 'RESOLUTION_PROPOSED',
                  'ESCALATED', 'RESOLVED', 'REOPENED')
    ),
    CONSTRAINT ck_finance_reconciliation_cases_resolution_kind CHECK (
        (proposed_resolution IS NULL OR proposed_resolution IN (
            'CONFIRMED_TIMING', 'PROVIDER_CORRECTION_REQUESTED', 'INTERNAL_FACT_RECOVERED',
            'DUPLICATE_COMPENSATED', 'JOURNAL_CORRECTION', 'REFUND_OR_PAYOUT_ACTION',
            'WRITTEN_OFF', 'FALSE_POSITIVE'))
        AND (resolution IS NULL OR resolution IN (
            'CONFIRMED_TIMING', 'PROVIDER_CORRECTION_REQUESTED', 'INTERNAL_FACT_RECOVERED',
            'DUPLICATE_COMPENSATED', 'JOURNAL_CORRECTION', 'REFUND_OR_PAYOUT_ACTION',
            'WRITTEN_OFF', 'FALSE_POSITIVE'))
    ),
    -- Resolution names a person and an outcome. resolved_at survives a reopen, because the case was
    -- in fact resolved once and the history of that matters.
    CONSTRAINT ck_finance_reconciliation_cases_resolved CHECK (
        state <> 'RESOLVED'
            OR (resolved_at IS NOT NULL
                AND resolution IS NOT NULL
                AND resolved_by_actor_id IS NOT NULL)
    ),
    -- A correction that touches the journal is an approved posting, not a quiet plug.
    CONSTRAINT ck_finance_reconciliation_cases_correction CHECK (
        resolution <> 'JOURNAL_CORRECTION'
            OR (correction_transaction_id IS NOT NULL AND approved_by_actor_id IS NOT NULL)
    ),
    CONSTRAINT ck_finance_reconciliation_cases_writeoff CHECK (
        resolution <> 'WRITTEN_OFF' OR approved_by_actor_id IS NOT NULL
    ),
    CONSTRAINT ck_finance_reconciliation_cases_money CHECK (
        (exposure_amount_minor IS NULL) = (currency IS NULL)
        AND (currency IS NULL OR currency ~ '^[A-Z]{3}$')
        AND (exposure_amount_minor IS NULL OR exposure_amount_minor >= 0)
    ),
    CONSTRAINT ck_finance_reconciliation_cases_reopened CHECK (reopened_count >= 0),
    CONSTRAINT ck_finance_reconciliation_cases_version CHECK (version >= 0)
);

CREATE INDEX idx_finance_reconciliation_cases_open
    ON finance_reconciliation_cases (severity, due_at)
    WHERE state NOT IN ('RESOLVED');
CREATE INDEX idx_finance_reconciliation_cases_payout_block
    ON finance_reconciliation_cases (host_account_holder_id)
    WHERE blocks_payout = true AND state <> 'RESOLVED';
CREATE INDEX idx_finance_reconciliation_cases_close_block
    ON finance_reconciliation_cases (accounting_book_id)
    WHERE blocks_period_close = true AND state <> 'RESOLVED';
--rollback DROP TABLE finance_reconciliation_cases;

--changeset ninggiangboy:022-25-finance-adjustment-requests
-- There is no free-form posting endpoint. An adjustment picks a type from an allowlist, and the type
-- decides which accounts, dimensions, amount bounds and approvals apply. The request hash is what
-- approval is bound to, so editing the amount after approval invalidates the approval instead of
-- silently carrying it forward.
CREATE TABLE finance_adjustment_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    adjustment_type             VARCHAR(40) NOT NULL,
    accounting_book_id          UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    currency                    VARCHAR(3) NOT NULL,
    amount_minor                BIGINT NOT NULL,

    source_type                 VARCHAR(64) NOT NULL,
    source_id                   UUID NOT NULL,
    host_account_holder_id      UUID,
    booking_id                  UUID,
    reconciliation_case_id      UUID,
    posting_rule_version_id     UUID,

    request_hash                CHAR(64) NOT NULL,
    reason_code                 VARCHAR(64) NOT NULL,
    justification               VARCHAR(1024),
    evidence_reference          VARCHAR(255),
    requires_approvals          SMALLINT NOT NULL DEFAULT 1,
    approval_count              SMALLINT NOT NULL DEFAULT 0,

    status                      VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    proposed_by_actor_id        UUID NOT NULL,
    submitted_at                TIMESTAMPTZ,
    decided_at                  TIMESTAMPTZ,
    posted_transaction_id       UUID,
    rejection_code              VARCHAR(64),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_finance_adjustment_requests_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_finance_adjustment_requests_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_finance_adjustment_requests_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_finance_adjustment_requests_booking FOREIGN KEY (booking_id)
        REFERENCES bookings (id),
    CONSTRAINT fk_finance_adjustment_requests_case FOREIGN KEY (reconciliation_case_id)
        REFERENCES finance_reconciliation_cases (id),
    CONSTRAINT fk_finance_adjustment_requests_rule FOREIGN KEY (posting_rule_version_id)
        REFERENCES posting_rule_versions (id),
    CONSTRAINT fk_finance_adjustment_requests_transaction FOREIGN KEY (posted_transaction_id)
        REFERENCES ledger_transactions (id),
    CONSTRAINT uk_finance_adjustment_requests_public_id UNIQUE (public_id),
    -- The same adjustment proposed twice is one request, which is what stops a double-clicked
    -- approval screen from posting the correction twice.
    CONSTRAINT uk_finance_adjustment_requests_hash UNIQUE (accounting_book_id, request_hash),
    CONSTRAINT ck_finance_adjustment_requests_type CHECK (
        adjustment_type IN ('DUPLICATE_FEE_CORRECTION', 'PROVIDER_FEE_TRUE_UP',
                            'GOODWILL_FUNDING', 'ROUNDING_CORRECTION', 'RECOVERY',
                            'WRITE_OFF', 'RECLASSIFICATION', 'FX_DIFFERENCE')
    ),
    CONSTRAINT ck_finance_adjustment_requests_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'POSTED',
                   'FAILED_REVIEW_REQUIRED')
    ),
    CONSTRAINT ck_finance_adjustment_requests_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_finance_adjustment_requests_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_adjustment_requests_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_finance_adjustment_requests_submitted CHECK (
        status = 'DRAFT' OR submitted_at IS NOT NULL
    ),
    CONSTRAINT ck_finance_adjustment_requests_decided CHECK (
        (status IN ('APPROVED', 'REJECTED', 'POSTED')) = (decided_at IS NOT NULL)
    ),
    CONSTRAINT ck_finance_adjustment_requests_rejection CHECK (
        (status = 'REJECTED') = (rejection_code IS NOT NULL)
    ),
    -- Posting is the last step and it names the journal it produced, so an adjustment can always be
    -- traced to its entry and back.
    CONSTRAINT ck_finance_adjustment_requests_posted CHECK (
        (status = 'POSTED') = (posted_transaction_id IS NOT NULL)
    ),
    -- Approval is counted, not assumed: a request cannot reach APPROVED with fewer approvals than
    -- its own type demands.
    CONSTRAINT ck_finance_adjustment_requests_approvals CHECK (
        requires_approvals >= 1 AND approval_count >= 0
        AND (status NOT IN ('APPROVED', 'POSTED') OR approval_count >= requires_approvals)
    ),
    CONSTRAINT ck_finance_adjustment_requests_version CHECK (version >= 0)
);

CREATE INDEX idx_finance_adjustment_requests_pending
    ON finance_adjustment_requests (submitted_at)
    WHERE status IN ('SUBMITTED', 'FAILED_REVIEW_REQUIRED');
CREATE INDEX idx_finance_adjustment_requests_host
    ON finance_adjustment_requests (host_account_holder_id, created_at DESC)
    WHERE host_account_holder_id IS NOT NULL;
--rollback DROP TABLE finance_adjustment_requests;

--changeset ninggiangboy:022-26-finance-adjustment-approvals
-- Who approved what, bound to the exact request hash they saw. A proposer cannot approve their own
-- request -- that is the whole point of maker-checker, and it is enforced here rather than left to
-- the screen that renders the button.
CREATE TABLE finance_adjustment_approvals (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    adjustment_request_id       UUID NOT NULL,
    approver_actor_id           UUID NOT NULL,
    decision                    VARCHAR(16) NOT NULL,
    approved_request_hash       CHAR(64) NOT NULL,
    authentication_strength     VARCHAR(24) NOT NULL DEFAULT 'SESSION',
    break_glass                 BOOLEAN NOT NULL DEFAULT false,
    break_glass_expires_at      TIMESTAMPTZ,
    reason_code                 VARCHAR(64),
    note                        VARCHAR(512),
    decided_at                  TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    -- Deliberately not ON DELETE CASCADE: an approval is append-only evidence of a person's decision
    -- and the trigger in changeset 022-30 refuses the cascade.
    CONSTRAINT fk_finance_adjustment_approvals_request FOREIGN KEY (adjustment_request_id)
        REFERENCES finance_adjustment_requests (id),
    -- One decision per approver per request. A second would let one person satisfy a two-approval
    -- threshold by clicking twice.
    CONSTRAINT uk_finance_adjustment_approvals_approver
        UNIQUE (adjustment_request_id, approver_actor_id),
    CONSTRAINT ck_finance_adjustment_approvals_decision CHECK (
        decision IN ('APPROVED', 'REJECTED')
    ),
    CONSTRAINT ck_finance_adjustment_approvals_strength CHECK (
        authentication_strength IN ('SESSION', 'STEP_UP', 'HARDWARE_TOKEN')
    ),
    -- Break-glass is short-lived by construction, and says when it stops being valid.
    CONSTRAINT ck_finance_adjustment_approvals_break_glass CHECK (
        break_glass = (break_glass_expires_at IS NOT NULL)
    ),
    CONSTRAINT ck_finance_adjustment_approvals_rejection CHECK (
        decision <> 'REJECTED' OR reason_code IS NOT NULL
    ),
    CONSTRAINT ck_finance_adjustment_approvals_hash CHECK (
        approved_request_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_finance_adjustment_approvals_request
    ON finance_adjustment_approvals (adjustment_request_id, decided_at);
--rollback DROP TABLE finance_adjustment_approvals;

--changeset ninggiangboy:022-27-host-recoveries
-- What a host owes the platform after money already left, and how it is being collected. A recovery
-- is not a deletion of the original payout: the payout stays in history and the recovery sits beside
-- it with its own notice, consent basis, and appeal state, because a host is entitled to know why
-- their next payout is smaller.
CREATE TABLE host_recoveries (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id                   VARCHAR(32) NOT NULL,
    host_account_holder_id      UUID NOT NULL,
    legal_entity_id             UUID NOT NULL,
    accounting_book_id          UUID NOT NULL,
    currency                    VARCHAR(3) NOT NULL,

    cause                       VARCHAR(32) NOT NULL,
    causal_source_type          VARCHAR(64) NOT NULL,
    causal_source_id            UUID NOT NULL,
    booking_id                  UUID,
    payout_instruction_id       UUID,

    original_amount_minor       BIGINT NOT NULL,
    recovered_amount_minor      BIGINT NOT NULL DEFAULT 0,
    written_off_amount_minor    BIGINT NOT NULL DEFAULT 0,
    remaining_amount_minor      BIGINT NOT NULL,

    waterfall_policy_version_id UUID,
    state                       VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    notice_sent_at              TIMESTAMPTZ,
    contract_basis_reference    VARCHAR(255),
    disputed_at                 TIMESTAMPTZ,
    written_off_at              TIMESTAMPTZ,
    written_off_by_actor_id     UUID,
    closed_at                   TIMESTAMPTZ,
    opened_at                   TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_host_recoveries_host FOREIGN KEY (host_account_holder_id)
        REFERENCES account_holders (id),
    CONSTRAINT fk_host_recoveries_entity FOREIGN KEY (legal_entity_id)
        REFERENCES legal_entities (id),
    CONSTRAINT fk_host_recoveries_book FOREIGN KEY (accounting_book_id)
        REFERENCES accounting_books (id),
    CONSTRAINT fk_host_recoveries_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_host_recoveries_payout FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    CONSTRAINT uk_host_recoveries_public_id UNIQUE (public_id),
    -- One recovery per causal instruction. A second would try to collect the same debt twice.
    CONSTRAINT uk_host_recoveries_cause UNIQUE (causal_source_type, causal_source_id),
    CONSTRAINT ck_host_recoveries_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_host_recoveries_cause CHECK (
        cause IN ('CANCELLATION', 'REFUND', 'CHARGEBACK', 'DISPUTE', 'GUEST_REMEDY',
                  'WITHHOLDING_CORRECTION', 'DUPLICATE_PAYOUT', 'PAYOUT_RETURN_FEE',
                  'APPROVED_ADJUSTMENT')
    ),
    CONSTRAINT ck_host_recoveries_state CHECK (
        state IN ('OPEN', 'OFFSETTING', 'COLLECTION_PENDING', 'RECOVERED',
                  'DISPUTED', 'WRITTEN_OFF', 'MANUAL_REVIEW')
    ),
    CONSTRAINT ck_host_recoveries_amounts CHECK (
        original_amount_minor > 0
        AND recovered_amount_minor >= 0 AND written_off_amount_minor >= 0
    ),
    -- The ceiling: the platform can never collect and write off more than it was owed.
    CONSTRAINT ck_host_recoveries_ceiling CHECK (
        recovered_amount_minor + written_off_amount_minor <= original_amount_minor
    ),
    CONSTRAINT ck_host_recoveries_remaining CHECK (
        remaining_amount_minor
            = original_amount_minor - recovered_amount_minor - written_off_amount_minor
    ),
    -- A write-off is an approved accounting event with a name attached, not a quiet zeroing.
    CONSTRAINT ck_host_recoveries_write_off CHECK (
        (written_off_amount_minor > 0)
            = (written_off_at IS NOT NULL AND written_off_by_actor_id IS NOT NULL)
    ),
    -- A dispute that is settled lets the recovery resume, and disputed_at stays: the fact that the
    -- host once contested this debt is part of why the collection went the way it did.
    CONSTRAINT ck_host_recoveries_disputed CHECK (
        state <> 'DISPUTED' OR disputed_at IS NOT NULL
    ),
    CONSTRAINT ck_host_recoveries_closed CHECK (
        state NOT IN ('RECOVERED', 'WRITTEN_OFF') OR closed_at IS NOT NULL
    ),
    CONSTRAINT ck_host_recoveries_version CHECK (version >= 0)
);

CREATE INDEX idx_host_recoveries_host
    ON host_recoveries (host_account_holder_id, currency)
    WHERE state NOT IN ('RECOVERED', 'WRITTEN_OFF');
CREATE INDEX idx_host_recoveries_payout
    ON host_recoveries (payout_instruction_id) WHERE payout_instruction_id IS NOT NULL;
--rollback DROP TABLE host_recoveries;

--changeset ninggiangboy:022-28-host-recovery-allocations
-- Each step of the waterfall, in order, with the amount it consumed. The step number is stored so a
-- host statement can show that the reserve was used before future earnings were touched, which is
-- the difference between a contractual offset and an unexplained deduction.
CREATE TABLE host_recovery_allocations (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_recovery_id            UUID NOT NULL,
    step_number                 SMALLINT NOT NULL,
    method                      VARCHAR(32) NOT NULL,
    amount_minor                BIGINT NOT NULL,
    currency                    VARCHAR(3) NOT NULL,

    payable_allocation_id       UUID,
    host_reserve_id             UUID,
    ledger_posting_id           UUID,
    collection_obligation_id    UUID,

    state                       VARCHAR(16) NOT NULL DEFAULT 'APPLIED',
    policy_version_id           UUID,
    occurred_at                 TIMESTAMPTZ NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_host_recovery_allocations_recovery FOREIGN KEY (host_recovery_id)
        REFERENCES host_recoveries (id),
    CONSTRAINT fk_host_recovery_allocations_allocation FOREIGN KEY (payable_allocation_id)
        REFERENCES host_payable_allocations (id),
    CONSTRAINT fk_host_recovery_allocations_reserve FOREIGN KEY (host_reserve_id)
        REFERENCES host_reserves (id),
    CONSTRAINT fk_host_recovery_allocations_posting FOREIGN KEY (ledger_posting_id)
        REFERENCES ledger_postings (id),
    CONSTRAINT fk_host_recovery_allocations_obligation FOREIGN KEY (collection_obligation_id)
        REFERENCES collection_obligations (id),
    CONSTRAINT uk_host_recovery_allocations_step UNIQUE (host_recovery_id, step_number),
    CONSTRAINT ck_host_recovery_allocations_method CHECK (
        method IN ('RESERVE_APPLIED', 'AVAILABLE_PAYABLE_OFFSET', 'FUTURE_PAYABLE_OFFSET',
                   'AUTHORIZED_DEBIT', 'PROTECTION_RECOVERY', 'WRITTEN_OFF')
    ),
    CONSTRAINT ck_host_recovery_allocations_state CHECK (
        state IN ('PLANNED', 'APPLIED', 'REVERSED', 'FAILED')
    ),
    CONSTRAINT ck_host_recovery_allocations_step CHECK (step_number BETWEEN 1 AND 6),
    CONSTRAINT ck_host_recovery_allocations_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_host_recovery_allocations_currency CHECK (currency ~ '^[A-Z]{3}$'),
    -- Each method names the thing it consumed, so no step can take money from nowhere.
    CONSTRAINT ck_host_recovery_allocations_source CHECK (
        (method <> 'RESERVE_APPLIED' OR host_reserve_id IS NOT NULL)
        AND (method NOT IN ('AVAILABLE_PAYABLE_OFFSET', 'FUTURE_PAYABLE_OFFSET')
             OR payable_allocation_id IS NOT NULL)
        AND (method <> 'AUTHORIZED_DEBIT' OR collection_obligation_id IS NOT NULL)
    )
);

CREATE INDEX idx_host_recovery_allocations_recovery
    ON host_recovery_allocations (host_recovery_id, step_number);
CREATE INDEX idx_host_recovery_allocations_allocation
    ON host_recovery_allocations (payable_allocation_id)
    WHERE payable_allocation_id IS NOT NULL;
--rollback DROP TABLE host_recovery_allocations;

--changeset ninggiangboy:022-29-ledger-dimension-links
-- The payout, reserve, recovery and reconciliation dimensions on ledger_postings, and the statement
-- a payout belongs to, could not be constrained when their columns were declared because the tables
-- they point at are created later in this same migration. They are closed here, in the same forward
-- style migration 020 used to close the booking reference migration 018 had to leave open.
ALTER TABLE ledger_postings
    ADD CONSTRAINT fk_ledger_postings_payout_instruction FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id),
    ADD CONSTRAINT fk_ledger_postings_payout_item FOREIGN KEY (payout_item_id)
        REFERENCES payout_items (id),
    ADD CONSTRAINT fk_ledger_postings_reserve FOREIGN KEY (host_reserve_id)
        REFERENCES host_reserves (id),
    ADD CONSTRAINT fk_ledger_postings_recovery FOREIGN KEY (host_recovery_id)
        REFERENCES host_recoveries (id),
    ADD CONSTRAINT fk_ledger_postings_reconciliation_case FOREIGN KEY (reconciliation_case_id)
        REFERENCES finance_reconciliation_cases (id);

ALTER TABLE payout_instructions
    ADD CONSTRAINT fk_payout_instructions_statement FOREIGN KEY (statement_id)
        REFERENCES host_statements (id);

ALTER TABLE payout_items
    ADD CONSTRAINT fk_payout_items_recovery FOREIGN KEY (host_recovery_id)
        REFERENCES host_recoveries (id);

ALTER TABLE payout_holds
    ADD CONSTRAINT fk_payout_holds_payout FOREIGN KEY (payout_instruction_id)
        REFERENCES payout_instructions (id);

ALTER TABLE finance_reconciliation_matches
    ADD CONSTRAINT fk_finance_reconciliation_matches_case FOREIGN KEY (case_id)
        REFERENCES finance_reconciliation_cases (id);
--rollback ALTER TABLE finance_reconciliation_matches DROP CONSTRAINT fk_finance_reconciliation_matches_case;
--rollback ALTER TABLE payout_holds DROP CONSTRAINT fk_payout_holds_payout;
--rollback ALTER TABLE payout_items DROP CONSTRAINT fk_payout_items_recovery;
--rollback ALTER TABLE payout_instructions DROP CONSTRAINT fk_payout_instructions_statement;
--rollback ALTER TABLE ledger_postings DROP CONSTRAINT fk_ledger_postings_reconciliation_case;
--rollback ALTER TABLE ledger_postings DROP CONSTRAINT fk_ledger_postings_recovery;
--rollback ALTER TABLE ledger_postings DROP CONSTRAINT fk_ledger_postings_reserve;
--rollback ALTER TABLE ledger_postings DROP CONSTRAINT fk_ledger_postings_payout_item;
--rollback ALTER TABLE ledger_postings DROP CONSTRAINT fk_ledger_postings_payout_instruction;

--changeset ninggiangboy:022-30-finance-evidence-append-only splitStatements:false
-- Five tables record what was decided, observed, compared or approved at a point in time. None of
-- them may be edited: a release decision that turns out to have been wrong is followed by another
-- decision, a provider observation that contradicts an earlier one is a new row, and an approval
-- that somebody regrets is answered by a rejection, not by a rewrite. The rule is a trigger rather
-- than a convention because these rows are the evidence, and evidence that can be quietly amended is
-- not evidence.
CREATE FUNCTION finance_evidence_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        '% is append-only; append a correcting record instead of altering the evidence',
        TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_host_fund_release_decisions_append_only
    BEFORE UPDATE OR DELETE ON host_fund_release_decisions
    FOR EACH ROW EXECUTE FUNCTION finance_evidence_reject_mutation();

CREATE TRIGGER trg_payout_observations_append_only
    BEFORE UPDATE OR DELETE ON payout_observations
    FOR EACH ROW EXECUTE FUNCTION finance_evidence_reject_mutation();

CREATE TRIGGER trg_finance_reconciliation_matches_append_only
    BEFORE UPDATE OR DELETE ON finance_reconciliation_matches
    FOR EACH ROW EXECUTE FUNCTION finance_evidence_reject_mutation();

CREATE TRIGGER trg_finance_adjustment_approvals_append_only
    BEFORE UPDATE OR DELETE ON finance_adjustment_approvals
    FOR EACH ROW EXECUTE FUNCTION finance_evidence_reject_mutation();

CREATE TRIGGER trg_host_reserve_allocations_append_only
    BEFORE UPDATE OR DELETE ON host_reserve_allocations
    FOR EACH ROW EXECUTE FUNCTION finance_evidence_reject_mutation();
--rollback DROP TRIGGER trg_host_reserve_allocations_append_only ON host_reserve_allocations;
--rollback DROP TRIGGER trg_finance_adjustment_approvals_append_only ON finance_adjustment_approvals;
--rollback DROP TRIGGER trg_finance_reconciliation_matches_append_only ON finance_reconciliation_matches;
--rollback DROP TRIGGER trg_payout_observations_append_only ON payout_observations;
--rollback DROP TRIGGER trg_host_fund_release_decisions_append_only ON host_fund_release_decisions;
--rollback DROP FUNCTION finance_evidence_reject_mutation();

--changeset ninggiangboy:022-31-external-evidence-frozen splitStatements:false
-- External evidence is not quite append-only: an artifact is parsed after it arrives and a record is
-- marked matched after it is compared. Everything else about both is frozen, so the numbers a
-- reconciliation ran against cannot be adjusted afterwards to make the run agree. That distinction
-- matters: an artifact whose amounts can be edited proves nothing at all.
CREATE FUNCTION external_financial_artifacts_freeze_content() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'external financial artifact % is immutable evidence and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.source_kind         IS DISTINCT FROM OLD.source_kind
        OR NEW.provider_account_id IS DISTINCT FROM OLD.provider_account_id
        OR NEW.legal_entity_id     IS DISTINCT FROM OLD.legal_entity_id
        OR NEW.currency            IS DISTINCT FROM OLD.currency
        OR NEW.coverage_start      IS DISTINCT FROM OLD.coverage_start
        OR NEW.coverage_end        IS DISTINCT FROM OLD.coverage_end
        OR NEW.cutoff_timezone     IS DISTINCT FROM OLD.cutoff_timezone
        OR NEW.artifact_reference  IS DISTINCT FROM OLD.artifact_reference
        OR NEW.content_hash        IS DISTINCT FROM OLD.content_hash
        OR NEW.received_at         IS DISTINCT FROM OLD.received_at
    THEN
        RAISE EXCEPTION
            'external financial artifact % is immutable evidence; ingest a superseding artifact '
            'instead of altering this one', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_external_financial_artifacts_frozen
    BEFORE UPDATE OR DELETE ON external_financial_artifacts
    FOR EACH ROW EXECUTE FUNCTION external_financial_artifacts_freeze_content();

CREATE FUNCTION external_financial_records_freeze_content() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'external financial record % is immutable evidence and cannot be deleted', OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.artifact_id     IS DISTINCT FROM OLD.artifact_id
        OR NEW.record_type     IS DISTINCT FROM OLD.record_type
        OR NEW.amount_minor    IS DISTINCT FROM OLD.amount_minor
        OR NEW.fee_amount_minor IS DISTINCT FROM OLD.fee_amount_minor
        OR NEW.currency        IS DISTINCT FROM OLD.currency
        OR NEW.native_reference IS DISTINCT FROM OLD.native_reference
        OR NEW.provider_object_ref IS DISTINCT FROM OLD.provider_object_ref
        OR NEW.occurred_at     IS DISTINCT FROM OLD.occurred_at
        OR NEW.row_hash        IS DISTINCT FROM OLD.row_hash
    THEN
        RAISE EXCEPTION
            'external financial record % is immutable evidence; only its match state may change',
            OLD.id
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_external_financial_records_frozen
    BEFORE UPDATE OR DELETE ON external_financial_records
    FOR EACH ROW EXECUTE FUNCTION external_financial_records_freeze_content();
--rollback DROP TRIGGER trg_external_financial_records_frozen ON external_financial_records;
--rollback DROP FUNCTION external_financial_records_freeze_content();
--rollback DROP TRIGGER trg_external_financial_artifacts_frozen ON external_financial_artifacts;
--rollback DROP FUNCTION external_financial_artifacts_freeze_content();

--changeset ninggiangboy:022-32-issued-statement-frozen splitStatements:false
-- A host who downloaded a statement and a host who reloads the page must see the same numbers. Once
-- a statement is issued its figures and its lines are frozen; a correction is a new version that
-- links back. Only the supersession relationship may still be written, because that is recorded
-- after the correction exists.
CREATE FUNCTION host_statements_freeze_issued() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'statement % was issued at % and cannot be deleted', OLD.id, OLD.issued_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    IF NEW.opening_balance_minor  IS DISTINCT FROM OLD.opening_balance_minor
        OR NEW.gross_entitlement_minor IS DISTINCT FROM OLD.gross_entitlement_minor
        OR NEW.deductions_minor        IS DISTINCT FROM OLD.deductions_minor
        OR NEW.withholding_minor       IS DISTINCT FROM OLD.withholding_minor
        OR NEW.reserved_minor          IS DISTINCT FROM OLD.reserved_minor
        OR NEW.held_minor              IS DISTINCT FROM OLD.held_minor
        OR NEW.recovered_minor         IS DISTINCT FROM OLD.recovered_minor
        OR NEW.paid_out_minor          IS DISTINCT FROM OLD.paid_out_minor
        OR NEW.returned_minor          IS DISTINCT FROM OLD.returned_minor
        OR NEW.closing_balance_minor   IS DISTINCT FROM OLD.closing_balance_minor
        OR NEW.currency                IS DISTINCT FROM OLD.currency
        OR NEW.period_range            IS DISTINCT FROM OLD.period_range
        OR NEW.statement_version       IS DISTINCT FROM OLD.statement_version
        OR NEW.data_hash               IS DISTINCT FROM OLD.data_hash
        OR NEW.issued_at               IS DISTINCT FROM OLD.issued_at
    THEN
        RAISE EXCEPTION
            'statement % was issued at %; issue a corrected version instead of altering it',
            OLD.id, OLD.issued_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_host_statements_frozen
    BEFORE UPDATE OR DELETE ON host_statements
    FOR EACH ROW WHEN (OLD.issued_at IS NOT NULL)
    EXECUTE FUNCTION host_statements_freeze_issued();

-- INSERT is covered too. Appending a line to an issued statement changes what the host was told
-- just as surely as editing one does, and the host's saved copy would no longer match the page.
CREATE FUNCTION host_statement_lines_freeze_issued() RETURNS TRIGGER AS $$
DECLARE
    owning_issued_at TIMESTAMPTZ;
    owning_id        UUID;
BEGIN
    owning_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.statement_id ELSE NEW.statement_id END;
    SELECT issued_at INTO owning_issued_at FROM host_statements WHERE id = owning_id;

    IF owning_issued_at IS NOT NULL THEN
        RAISE EXCEPTION
            'statement % was issued at %; its lines cannot be added to, altered, or removed',
            owning_id, owning_issued_at
            USING ERRCODE = 'restrict_violation';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_host_statement_lines_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON host_statement_lines
    FOR EACH ROW EXECUTE FUNCTION host_statement_lines_freeze_issued();
--rollback DROP TRIGGER trg_host_statement_lines_frozen ON host_statement_lines;
--rollback DROP FUNCTION host_statement_lines_freeze_issued();
--rollback DROP TRIGGER trg_host_statements_frozen ON host_statements;
--rollback DROP FUNCTION host_statements_freeze_issued();

--changeset ninggiangboy:022-33-payout-item-sum splitStatements:false
-- A payout is the sum of the items it consists of. If the two can drift, the instruction sends an
-- amount the host's balance never gave up, or gives up money the transfer never carried -- and
-- either way the difference lands in an account nobody can explain.
--
-- Like the journal balance rule this is a cross-row sum, so it is a DEFERRABLE INITIALLY DEFERRED
-- constraint trigger that fires at COMMIT with the whole item set visible. It applies only once the
-- instruction has reserved its items; a DRAFT is still being assembled.
CREATE FUNCTION payout_instructions_validate_item_sum() RETURNS TRIGGER AS $$
DECLARE
    target_instruction UUID;
    instruction_state  VARCHAR(16);
    instruction_amount BIGINT;
    item_total         BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'payout_instructions' THEN
        target_instruction := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        target_instruction := OLD.payout_instruction_id;
    ELSE
        target_instruction := NEW.payout_instruction_id;
    END IF;

    SELECT state, amount_minor INTO instruction_state, instruction_amount
        FROM payout_instructions WHERE id = target_instruction;

    IF instruction_state IS NULL OR instruction_state IN ('DRAFT', 'CANCELLED') THEN
        RETURN NULL;
    END IF;

    SELECT COALESCE(sum(selected_amount_minor), 0) INTO item_total
        FROM payout_items
        WHERE payout_instruction_id = target_instruction
          AND state <> 'RELEASED';

    IF item_total <> instruction_amount THEN
        RAISE EXCEPTION
            'payout instruction % is for % but its items sum to %',
            target_instruction, instruction_amount, item_total
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_payout_items_sum
    AFTER INSERT OR UPDATE OR DELETE ON payout_items
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION payout_instructions_validate_item_sum();

-- Reserving the items is itself the moment the rule must hold, otherwise an instruction could leave
-- DRAFT after its items were written and skip the check entirely.
CREATE CONSTRAINT TRIGGER trg_payout_instructions_item_sum
    AFTER INSERT OR UPDATE ON payout_instructions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION payout_instructions_validate_item_sum();
--rollback DROP TRIGGER trg_payout_instructions_item_sum ON payout_instructions;
--rollback DROP TRIGGER trg_payout_items_sum ON payout_items;
--rollback DROP FUNCTION payout_instructions_validate_item_sum();
