# `messaging`

## Goal

Own conversation, message, notification intent, and delivery as one module — the platform's outbound
and inbound communication layer — kept separate from `stay`'s operational workflow even though a
feature document groups the two, because the FK graph only supports the coupling running one way.

## Forces that shaped it

- **Not merged with `stay`, despite a feature document pairing them.** The foreign-key graph shows
  only a one-edge coupling (`stay → messaging`, 1 FK) — nowhere near the bidirectional density that
  forced `identity`'s or `booking`'s internal merges. A shared feature narrative is not, by itself, a
  reason to merge two modules; the FK cycle test is.
- **`messages` is the aggregate root** (in-module in-degree 5), with `conversations` (4) and
  `notification_intents` (4) close behind — three distinct concerns (a message thread, the
  conversation container, and an outbound notification) that share a module but not a root.

## What it owns

- **`conversation` cluster** — `conversations` (root), `conversation_participants`,
  `conversation_read_positions`, `communication_consents`.
- **`notification` cluster** — `notification_intents` (root), `notification_templates`,
  `notification_policies`, `notification_preferences`, `notification_renders`,
  `scheduled_communications`.
- **`delivery` cluster** — `messages` (root), `message_attachments`, `message_revisions`,
  `message_translations`, `message_moderation_actions`, `delivery_attempts`, `delivery_observations`,
  `contact_delivery_health`.

See [`../data-model/024-messaging-and-notifications.md`](../data-model/024-messaging-and-notifications.md)
and [`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).

## Aggregate clusters inside it

18 tables across 3 clusters. `delivery` is named for the outbound act (attempts, observations,
health) as distinct from `notification`'s intent-and-template layer that decides *what* to send;
`messages` anchors `delivery` because a message's own revisions, translations, and moderation actions
are all facts about that one message.

## What it does not own

The operational stay or task a message references (`stay`'s aggregate, referenced back here by 1 FK)
or a support case's timeline (`support`, which references `messaging` 4 times for case
correspondence) — `messaging` owns the communication artifact itself, not the workflow that triggered
it.

## Public API

No live service exists yet. The eventual API is a send/deliver surface (`NotificationSender`-shaped)
other modules call rather than writing to `messages`/`notification_intents` directly.

## Allowed dependencies

None with live code today. Schema carries 12 foreign keys into `identity`, 6 into `market`, 3 into
`booking`, 1 into `supply`.

## Data coupling `verify()` cannot see

Inbound: `support` (4), `stay` (1), `growth` (10). Outbound: `identity` (12), `market` (6), `booking`
(3), `supply` (1). The `growth → messaging` edge (10 FKs) is the largest single inbound coupling —
campaign touchpoints and referral invitations are delivered through this module's notification
machinery — invisible to `ApplicationModules.verify()`, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether notification delivery should move onto the `@ApplicationModuleListener` registry once this
module has a service layer — it is a strong candidate, since "send a notification" is exactly the
kind of post-commit, at-least-once-delivery work the registry exists for — is left for that module's
own design.

## Exit criteria

- All 18 tables and their entities/repositories live under `dev.ngb.backend.messaging.internal.model`
  / `.repository`, in the three clusters above.
- `ApplicationModules.verify()` passes with `messaging`'s only declared dependencies being `identity`,
  `market`, `booking`, and `supply`.
