# Migration 012 — Shared platform primitives

## Goal

Give every later domain one correct implementation of retry safety, fact publication, exactly-once
consumption, and tamper-evident audit, instead of twenty incompatible ones.

Migrations `000`–`011` solved none of these. Each domain design in `docs/features/` assumes they
exist: the booking commit sequence needs an idempotency record, the payment provider integration
needs an outbox, the finance close needs an append-only audit trail. This is the first migration of
the target release because nothing else can be built correctly without it.

## Tables

- `command_idempotency_records`: one row per `(scope_type, scope_key, operation, key_digest)`; holds
  the request digest, a lease for crash recovery, and a bounded safe projection of the response.
- `outbox_events`: immutable domain facts committed in the same transaction as the state they
  describe, plus mutable publication metadata (state, lease, attempts).
- `consumer_inbox_receipts`: keyed `(consumer_name, event_id)`; inserted and completed in the same
  transaction as the consumer's durable effect.
- `audit_events`: append-only evidence of privileged actions, enforced by a trigger.
- `external_resource_references`: provider-to-platform resolution where several domains need it.

## Design rules

- **No secrets, no raw bodies.** A retry-safety record is not a response cache: it stores a digest of
  the request and a bounded `response_projection`, never the request body, a bearer token, or a
  credential. An audit row that contained secrets could not legally be retained as long as auditing
  requires.
- **Payload is immutable after commit; publication metadata is not.** `outbox_events.payload` records
  what happened. `publication_state`, `attempt_count`, and the lease columns are operational state and
  carry no event meaning.
- **The inbox receipt is keyed by consumer name, not consumer version.** A handler upgrade must not
  silently replay effects that already happened. A deliberate rebuild uses a new `consumer_name` and
  suppresses external side effects.
- **Audit rows cannot be updated or deleted**, including by the application's own role. Correcting a
  mistaken audit row means appending a correcting one — the same additive-correction rule the
  platform applies to all historical evidence. Erasure and legal hold run out of band with elevated
  privileges; `legal_hold` and `retain_until` exist so that process has something to read.
- **Generic idempotency is not a substitute for business uniqueness.** Two different idempotency keys
  can still attempt the same booking confirmation, journal posting, or refund. Unique constraints on
  business identity remain mandatory in every later migration.
- **Lease predicates are never `now()`.** `idx_command_idempotency_records_stalled` indexes
  `lease_expires_at` without a time predicate, because a partial index whose predicate moves is not
  immutable. Recovery workers bind their own decision instant, per
  [`../conventions/04-time-and-clock.md`](../conventions/04-time-and-clock.md).
- All five tables are application-written, so none carries `DEFAULT now()` — see migration `011`.
- `012-05` sets `splitStatements:false` because the trigger function body is dollar-quoted and
  contains its own `;`, which Liquibase would otherwise split mid-statement.

## Ownership boundaries

D00 owns the append-only envelope and the writer contract. The owning domain defines which actions
are audited and what evidence they carry. D21 defines privileged review, override, maker-checker, and
break-glass policy. D22 defines retention, erasure, and legal hold. The event taxonomy, payload
ownership, schema registry, and analytics contract are governed by D19 in migration `030` — this
migration provides the transport, not the vocabulary.

The full target design is
[`../features/platform-foundation.md`](../features/platform-foundation.md).

## Exit criteria

- Replaying a command with the same idempotency key returns the stored outcome without re-running it.
- A fact committed with its state change is published at least once and consumed at most once.
- `UPDATE` and `DELETE` on `audit_events` fail for the application role.
- A settled idempotency record always carries an outcome; an in-progress one never does.
