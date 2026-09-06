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
- The target provider-independent obligation, attempt/operation/evidence model, authorization and
  capture semantics, customer-action flow, webhook/query recovery, dispute gateway, and migration
  path are documented in
  [`../features/payment-orchestration.md`](../features/payment-orchestration.md).
- Payment-provider state proves external money movement; it is not a ledger or a calculation of host
  payout and platform revenue. The authoritative target journal, host-payable, payout, and
  reconciliation design is documented in
  [`../features/ledger-reconciliation-and-host-payout.md`](../features/ledger-reconciliation-and-host-payout.md).
- Cancellation, modification, and support policy own the immutable line-level refund entitlement and
  funding instruction that payment executes; that boundary is documented in
  [`../features/cancellation-modification-and-refund.md`](../features/cancellation-modification-and-refund.md).
- Future payment/refund/payout risk inputs, challenges and scoped restrictions, fraud interpretation,
  provider-observation evidence, chargeback-label caution, and authoritative money boundary follow
  [`../features/trust-safety-fraud-and-moderation.md`](../features/trust-safety-fraud-and-moderation.md).
  A provider decline, chargeback, or risk score is not itself a confirmed-fraud label.
- Future support case strategy, frozen dispute-evidence manifests, damage/protection claim remedies,
  explicit funders, appeal, and provider-deadline coordination follow
  [`../features/disputes-damage-claims-and-support.md`](../features/disputes-damage-claims-and-support.md).
  Payment retains provider movement and dispute-observation authority and executes only exact
  authorized instructions.

## Exit criteria

- Replayed client requests and webhooks do not create duplicate charges.
- Payment failure leaves enough detail for support without exposing sensitive data.
- Partial and full refunds are auditable.
