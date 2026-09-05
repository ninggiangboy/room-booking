# Phase 005 — Payment

## Goal

Track external payment attempts and refunds without coupling provider state to the booking row.

## Tables

- `payment_attempts`: every attempt, provider reference, amount and idempotency key.
- `refunds`: partial or full refund attempts linked to the successful charge attempt.
- `payment_webhook_events`: provider event inbox used to deduplicate and retry webhook processing safely.

## Service rules

- Create a unique idempotency key before calling a payment provider.
- A booking can have several failed attempts but should have at most one successful captured total unless the product later supports installments.
- Insert the provider event ID into `payment_webhook_events` before processing. A unique conflict means it has already been received.
- Webhook processing must lock the payment row before changing its state and record `PROCESSED` only after the local transaction succeeds.
- Mark the booking `CONFIRMED` only after a verified successful provider event.
- Never store card or bank credentials, full provider payloads, or secrets.
- Enforce cumulative refund amount not exceeding the succeeded payment in transactional application logic.

## Exit criteria

- Replayed client requests and webhooks do not create duplicate charges.
- Payment failure leaves enough detail for support without exposing sensitive data.
- Partial and full refunds are auditable.
