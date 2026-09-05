--liquibase formatted sql

--changeset ninggiangboy:005-01-payment-attempts
CREATE TABLE payment_attempts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id              UUID NOT NULL,
    provider                VARCHAR(24) NOT NULL,
    provider_payment_id     VARCHAR(255),
    idempotency_key         VARCHAR(128) NOT NULL,
    amount_minor            BIGINT NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    status                  VARCHAR(24) NOT NULL DEFAULT 'CREATED',
    failure_code            VARCHAR(120),
    failure_message         VARCHAR(500),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_attempts_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT uk_payment_attempts_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payment_attempts_provider CHECK (provider IN ('STRIPE', 'MOMO', 'VNPAY', 'MANUAL')),
    CONSTRAINT ck_payment_attempts_amount CHECK (amount_minor >= 0),
    CONSTRAINT ck_payment_attempts_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_attempts_status CHECK (
        status IN ('CREATED', 'REQUIRES_ACTION', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_payment_attempts_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_payment_attempts_provider_reference
    ON payment_attempts (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE INDEX idx_payment_attempts_booking ON payment_attempts (booking_id, created_at DESC);

CREATE UNIQUE INDEX uk_payment_attempts_one_success
    ON payment_attempts (booking_id)
    WHERE status = 'SUCCEEDED';
--rollback DROP TABLE payment_attempts;

--changeset ninggiangboy:005-02-refunds
CREATE TABLE refunds (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_attempt_id    UUID NOT NULL,
    provider_refund_id    VARCHAR(255),
    idempotency_key       VARCHAR(128) NOT NULL,
    amount_minor          BIGINT NOT NULL,
    reason                VARCHAR(255) NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refunds_payment_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts (id),
    CONSTRAINT uk_refunds_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_refunds_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_refunds_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_refunds_version CHECK (version >= 0)
);

CREATE UNIQUE INDEX uk_refunds_provider_reference
    ON refunds (provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;

CREATE INDEX idx_refunds_payment_attempt ON refunds (payment_attempt_id, created_at DESC);
--rollback DROP TABLE refunds;

--changeset ninggiangboy:005-03-payment-webhook-events
CREATE TABLE payment_webhook_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider              VARCHAR(24) NOT NULL,
    provider_event_id     VARCHAR(255) NOT NULL,
    event_type            VARCHAR(120) NOT NULL,
    processing_status     VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    failure_message       VARCHAR(500),
    received_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at          TIMESTAMPTZ,
    CONSTRAINT uk_payment_webhook_events_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT ck_payment_webhook_events_provider CHECK (
        provider IN ('STRIPE', 'MOMO', 'VNPAY', 'MANUAL')
    ),
    CONSTRAINT ck_payment_webhook_events_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED')
    ),
    CONSTRAINT ck_payment_webhook_events_processed_at CHECK (
        processing_status <> 'PROCESSED' OR processed_at IS NOT NULL
    )
);

CREATE INDEX idx_payment_webhook_events_pending
    ON payment_webhook_events (received_at)
    WHERE processing_status IN ('RECEIVED', 'FAILED');
--rollback DROP TABLE payment_webhook_events;
