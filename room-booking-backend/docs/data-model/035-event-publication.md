# Migration 035 — Event publication registry

## Goal

Give `@ApplicationModuleListener` (Spring Modulith's post-commit, cross-module event handler) a
durable record of which `(event, listener)` pairs have not yet completed, so a process that dies
between commit and listener execution loses nothing.

This table belongs to `spring-modulith-events-jdbc`, not to this application's domain. It is recorded
here, alongside every other migration, because `docs/conventions/03-entities-and-persistence.md`
requires every schema change to go through Liquibase — including one the framework itself would
otherwise create automatically. See
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md) for
why the registry exists and how it relates to `outbox_events` (migration `012`).

## Table

- `event_publication`: one row per `(event, listener)` pair. `serialized_event` holds the event
  payload as JSON; `completion_date` is null until the listener finishes, at which point
  `completion_attempts` and `status` record how it got there. `republish-outstanding-events-on-restart`
  causes any row with a null `completion_date` to be retried at the next application startup.

## Why this migration is not written to the repository's own conventions

Every other table in this schema follows `docs/conventions/03-entities-and-persistence.md`:
bounded `VARCHAR(n)` with a `CHECK` constraint instead of unconstrained text, an optimistic-locking
`version` column, and (where retention applies) a `retain_until` column. `event_publication` violates
all three deliberately:

- **`TEXT` instead of `VARCHAR(n)` + `CHECK`.** `listener_id`, `event_type`, `serialized_event`, and
  `status` are unconstrained `TEXT`. The framework's own queries and its Jackson-based
  serialization/deserialization depend on this exact shape; adding a length constraint the framework
  does not expect risks a runtime failure on an event the application did not anticipate, not a
  schema improvement.
- **No `version` column.** The registry does its own completion bookkeeping through
  `completion_attempts` and `completion_date`; it is not this application's optimistic-concurrency
  model and must not be forced into it.
- **No `retain_until`.** The registry has no built-in retention policy of its own. This is a real
  gap — see the risk this creates, below — and not something this migration can fix by adding a
  column the framework does not read.

This is the same posture the repository already takes toward any third-party-owned schema: recorded
as a named exception, not silently normalized to house style.

## The risk this table creates, and how it is contained

Two application events already carry a raw secret: `EmailVerificationIssued(recipient, rawToken)` and
`PasswordResetIssued(recipient, rawToken)`. If either were published through this registry,
`serialized_event` would store the raw token in plaintext, indefinitely (under the default
`completion-mode=update`, the row simply stays with the payload it was written with, and
`completion-mode=delete` only removes the row *after* the listener runs, which is exactly the window
in which an SMTP outage would leave it in place longest). This would break the token design the rest
of the application follows — persist a digest, never the raw value — through a single Modulith
configuration line.

Both events are deliberately kept on a bare `@TransactionalEventListener` in `AuthEmailNotifier`,
never converted to `@ApplicationModuleListener`, so they never reach this table. See
[`../modules/identity.md`](../modules/identity.md) for the full explanation and
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md) for
the rule this establishes for every future event.

## Migration verification

- `event_publication` exists with the columns above and both indexes.
- `spring.modulith.events.jdbc.schema-initialization.enabled=false` in `application.properties`, so
  the framework never attempts to create this table itself.
- A publish → kill-before-listener → restart → replay cycle (see
  [`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md))
  produces exactly one completed row, not a duplicate.
