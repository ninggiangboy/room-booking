# The event publication registry

## Goal

Replace a bare `ApplicationEventPublisher` with Spring Modulith's event publication registry as the
default mechanism for post-commit work between modules, so that an event whose listener has not run
is a row somebody can find rather than a message nobody knows was lost.

This document explains what changed, what deliberately did not, and the two rules the change
introduces. The table itself is specified in
[`../data-model/035-event-publication.md`](../data-model/035-event-publication.md); the module
boundaries it carries events across are in
[`modular-monolith.md`](modular-monolith.md).

## The problem it solves

Before this change, the only cross-component mechanism in the application was
`ApplicationEventPublisher` with an `@TransactionalEventListener(phase = AFTER_COMMIT)` handler —
used by `EmailVerificationService` and `PasswordResetService` to hand a raw token to
`AuthEmailNotifier` for delivery.

That mechanism is best-effort and nothing more. The listener runs in memory after the transaction
commits; if the process dies in that window, or the listener throws, the event is gone with no record
that it ever existed. [`../features/platform-foundation.md`](../features/platform-foundation.md) says
so explicitly, and adds the constraint that follows from it: such an event **must not carry
contractual, financial, or safety effects**.

With two components and one email, that constraint is livable. With 22 modules whose only sanctioned
way to reach each other is an event, it is not — it would mean no cross-module interaction could ever
carry a consequence that matters.

## What `@ApplicationModuleListener` actually does

The annotation composes three others:

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener
```

and, because `spring-modulith-starter-jdbc` is on the classpath, publication is recorded durably:

1. The publishing module commits its own transaction. **In that same transaction**, one
   `event_publication` row is written per `(event, listener)` pair, with `completion_date` null.
2. After commit, each listener runs asynchronously in a new transaction of its own. A listener that
   fails does not roll back the command that published the event — which is the isolation the bare
   `@TransactionalEventListener` already gave — but now its row also stays incomplete.
3. On success, the row is completed. On failure or process death, it is not, and
   `republish-outstanding-events-on-restart=true` causes it to be republished at the next startup.

The delivery guarantee is therefore **at-least-once**, not exactly-once. A listener must be
idempotent. For work that must not be applied twice, `platform`'s `consumer_inbox_receipts`
(migration `012`) is the deduplication primitive; see [`../modules/platform.md`](../modules/platform.md).

### The three settings that make it work, and why each is not a default

All three live in `application.properties`, and each overrides a framework default that would
otherwise be wrong here.

| Setting | Framework default | Why it is overridden |
| --- | --- | --- |
| `spring.modulith.events.republish-outstanding-events-on-restart` | `false` | Left at the default, the registry records incompleteness and then never acts on it — a log, not an outbox. This one setting is the difference between the two. |
| `spring.modulith.events.jdbc.schema-initialization.enabled` | `true` | The framework would create `event_publication` itself, bypassing Liquibase. Migration `035` creates it instead, per `../conventions/03-entities-and-persistence.md`. |
| `spring.autoconfigure.exclude` (Moments) | not excluded | `starter-jdbc` transitively pulls `spring-modulith-moments`, whose beans consume the application `Clock` and publish `DayHasPassed`/`MonthHasPassed`. Nothing consumes them; unused background autoconfiguration is not free. |

`@EnableAsync` on `RoomBookingBackendApplication` is the fourth requirement and the quietest: without
it, `@Async` has no effect, the listener runs synchronously inside the publisher's call, and the
isolation the annotation exists to provide is gone without any error.

### What needed no change

The registry writes `publication_date` from a `Clock` bean when one is present in the context, and
`TimeConfig.clock()` already is. The rule that the application clock is the only writer of time
(`../conventions/04-time-and-clock.md`) survives here for free — one of the few places in this
migration where a convention cost nothing to keep.

## The rule: an event may not carry a secret

`serialized_event` stores the event payload as JSON, in a table with **no retention policy**. Under
`completion-mode=update` (the default) the row persists after completion; under `delete` it is
removed only once the listener succeeds, which means a failing listener — an SMTP outage, say — is
precisely the condition that keeps the payload in the database longest.

So:

> **No event published through the registry may carry a secret, raw personal data, or anything else
> that must not sit indefinitely in a table with no retention policy.** An event carries the
> identifier needed to look the value up, never the value.

### The two named exceptions

`EmailVerificationIssued(recipient, rawToken)` and `PasswordResetIssued(recipient, rawToken)` carry
raw token secrets. The rest of the application never stores a raw token — only its digest — and
routing these two through the registry would defeat that design with one configuration line.

Both therefore stay on a bare `@TransactionalEventListener` in `AuthEmailNotifier`, and the reason is
recorded in that class's JavaDoc so it is not "fixed" later by someone tidying annotations. This is
not a regression: it is exactly the behavior these two events had before this change, unchanged and
now explained.

They cannot simply be rewritten to carry a token identifier instead, because the raw secret is not
recoverable from what is stored — only the digest is. Making them durable requires deciding whether
an authentication secret may be encrypted at rest, which is a D01 identity decision and is left open
in [`../modules/identity.md`](../modules/identity.md).

## `event_publication` is not `outbox_events`

Two tables, adjacent in purpose, answering different questions. Confusing them will eventually get
one of them deleted as redundant.

| | `event_publication` (Modulith, `035`) | `outbox_events` (migration `012`) |
| --- | --- | --- |
| A row is | one `(event, listener)` pair | one fact to be published |
| Answers | "which listener has not finished?" | "which fact has not left the system?" |
| Scope | in-process, between modules | outbound, to the world |
| Carries | payload plus completion state | full envelope: aggregate type/id/version, correlation, causation, market, sensitivity class, deduplication key, lease |
| Owned by | the framework | this application's D00 design |

`outbox_events` is the envelope for the D19 event taxonomy and for a broker, if and when one is
introduced. It is unused today because nothing has externalized an event yet — that is what it is
for, not evidence that it is dead. A third, further-removed concept, `analytics`'
`event_arrivals`, records that an event reached the data platform; see
[`../modules/analytics.md`](../modules/analytics.md).

Spring Modulith can externalize events to a broker itself. Whether to use that or the hand-built
`outbox_events` envelope is deliberately **not decided here** — no broker is in play, and the choice
should be made against a real externalization requirement rather than in advance of one.

## Verification

Compilation proves nothing about any of this. The cycle that does:

1. `docker compose -f compose.local.yaml up -d`, then start the application and confirm Liquibase
   applied `035` and the framework did not also try to create the table.
2. Publish an event handled by an `@ApplicationModuleListener` and confirm exactly one
   `event_publication` row exists with a null `completion_date` at the moment of commit.
3. Let the listener finish; confirm `completion_date` and `completion_attempts` are populated.
4. Kill the process between commit and listener completion, restart, and confirm the row is
   republished and then completed — once, not twice.

Step 4 is the only step that proves the guarantee, and it is also the step that exercises payload
serialization through Jackson 3 (`tools.jackson`, which Spring Boot 4 brings and which
`spring-modulith-events-jackson` consumes through the `ObjectMapper` bean). That is the one seam in
this change where a version mismatch could hide, so it must be run rather than reasoned about.

## What this does not do

It does not introduce Kafka, RabbitMQ, or any broker, and it does not make the application
distributed. It does not give exactly-once delivery. It does not retire `outbox_events`. And it does
not make the two auth email events durable — that is a separate, open identity decision.
