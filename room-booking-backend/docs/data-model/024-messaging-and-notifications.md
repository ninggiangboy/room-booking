# Migration 024 — Messaging and notifications

## Goal

Communication reports facts; it does not create them. This migration exists so that what the platform
said to somebody — a message a host wrote, a confirmation email the system sent — is a durable,
attributable record that lives beside the domain events rather than inside them, and so that a message
nobody was entitled to send, or a notice delivered twice, is refused by the database rather than
noticed later in a support case.

Eighteen tables, twenty-two changesets. Four of those changesets contain no table at all: they carry
the rules no column can express.

## Six forces shaping it

**A conversation is a scope, not a pair of users.** Threads exist for an inquiry, a booking, an
incident or a support case, and membership follows current authority over that scope. A `CO_HOST`
removed today must not read tomorrow's messages, so participation is a row with a joined and a left
instant, at most one active membership per actor and role, and elevated support access carries a
purpose code and an expiry.

**Order inside a conversation is a number, not a timestamp.** Pagination, unread counts and dispute
evidence all depend on a strictly increasing per-conversation sequence. `conversations.next_sequence`
is the allocator, a unique key refuses a collision, a trigger refuses a message whose sequence the
allocator never issued, and a second trigger refuses an allocator that moves backwards.

**A message is evidence the moment it is sent.** The words, the sender, the order and the content hash
are frozen at insert. Corrections are `message_revisions` naming the original; withdrawal hides a
presentation without destroying what was said; translations and moderation decisions sit beside the
message rather than replacing it. Deleting a message under legal hold is refused outright.

**Delivery is at-least-once; the user-visible notice is effectively once.** One committed event, one
recipient, one purpose, one policy version means one `notification_intent` — a unique key, not a
convention — and an operator cannot escape it by choosing a different template or provider route. The
intent's identity columns and input hash are frozen, so an existing intent cannot be quietly repointed
to achieve the same duplicate by a quieter route.

**Consent and health are destination facts, not message facts.** Whether a person may be sent a
marketing notice, and whether an address still accepts mail, outlive any one send. They are their own
rows, keyed to the identity domain's `contact_channels`, so withdrawal and suppression apply
immediately to everything that follows.

**Sensitive content is referenced, not copied.** Message bodies may live encrypted elsewhere,
attachments live in object storage behind scan states, rendered artifacts are references and hashes.
What stays in these tables is the metadata required to authorize, order, retry and audit — and no raw
email address or telephone number anywhere.

## Verified behaviour

Eighty-two scenarios were executed against PostgreSQL 17 with the migration applied. Every refusal is
paired with an accepted counterpart, so the table shows a rule being enforced rather than a table that
refuses everything.

| Scenario | Result |
| --- | --- |
| A second live conversation for the same booking scope | refused |
| A conversation for a different scope | accepted |
| A second active membership for the same actor and role | refused |
| Re-adding a departed agent as a new membership row | accepted |
| Support membership with no purpose code or expiry | refused |
| Departure recorded with no removal reason | refused |
| A `SYSTEM` participant carrying an account holder | refused |
| A message taking a sequence the allocator never issued | refused |
| A message taking the sequence just issued | accepted |
| A second message on a sequence already taken | refused |
| A replayed send with the same sender idempotency key | refused |
| A message from a participant who has left | refused |
| A message whose participant belongs to another conversation | refused |
| A message claiming a sender the participant row does not belong to | refused |
| A message from a participant row that does not exist | refused |
| A text message stored both inline and by reference | refused |
| A system fact with a human sender | refused |
| A system fact citing no committed event | refused |
| A structured action with no action to perform | refused |
| A structured action carrying its server-issued target | accepted |
| A message into a closed conversation | refused |
| Editing what a message said | refused |
| Withdrawing a message | accepted |
| A withdrawn message with no withdrawal instant | refused |
| Deleting a message under legal hold | refused |
| Editing or deleting a message revision | refused — append-only |
| A message forgetting a revision that already exists | refused |
| A message citing an attachment still being scanned | refused |
| An approved attachment linked to its message | accepted |
| A rejected attachment that will not say why | refused |
| A read position moving backwards | refused |
| A read position moving forwards | accepted |
| A conversation rewinding its sequence allocator | refused |
| A classifier restricting a person on its own authority | refused |
| A reviewer restricting on the same evidence | accepted |
| A reviewer decision naming no reviewer | refused |
| Re-translating under the same engine version | refused |
| Editing a stored translation | refused — append-only |
| A second intent for the same event, recipient, purpose and policy | refused |
| A second intent for the same event with a different purpose | accepted |
| Editing the rules of an approved policy | refused |
| Retiring an approved policy | accepted |
| Deleting or editing an approved template | refused |
| Repointing a decided intent at another recipient | refused |
| Rewriting a decided intent's input hash | refused |
| Superseding an intent with its replacement | accepted |
| An intent claiming supersession with no successor | refused |
| A half-held worker lease | refused |
| An attempt accepted by a provider it was never sent to | refused |
| A failed attempt that will not say how it failed | refused |
| A delivered attempt with no completion instant | refused |
| A second attempt planned after the first was accepted | accepted |
| A third attempt reusing the second attempt number | refused |
| A second attempt reusing the first's provider reference | refused |
| Scheduling a retry of an already-delivered attempt | refused |
| A provider delivery event | accepted |
| The same provider event id replayed | refused |
| A late complaint after that delivery | accepted |
| A webhook observation with no stored artifact | refused |
| Editing or deleting a provider observation | refused — append-only |
| A second successful render of the same intent, channel and locale | refused |
| A blocked render that will not name the missing variable | refused |
| A blocked render naming what was missing | accepted |
| A second live consent for the same category and channel | refused |
| Withdrawal recorded before the grant | refused |
| Withdrawing and then granting again | accepted |
| A second category default for the same channel | refused |
| Quiet hours with no zone to evaluate them in | refused |
| Quiet hours in a named zone | accepted |
| A second live job with the same supersession key | refused |
| Superseding a job and scheduling its replacement | accepted |
| A job claiming it fired with nothing to show for it | refused |
| A discarded job with no reason | refused |
| A suppressed destination with no reason or source | refused |
| A suppressed destination attributed to a provider bounce | accepted |
| Negative bounce counters | refused |

Full rollback of all twenty-two changesets leaves no tables, no functions and no changelog rows, and
the migration re-applies cleanly afterwards.

## Tables

**Conversation.** `conversations` (scope, status, sequence allocator),
`conversation_participants` (membership with its authority source, permissions and elevation expiry).

**Messages.** `messages`, `message_revisions`, `message_attachments`, `message_translations`,
`conversation_read_positions`, `message_moderation_actions`.

**Notification policy.** `notification_policies` (fact-to-intent rules, frozen once approved),
`notification_templates` (channel- and locale-specific wording, frozen once approved).

**Recipient choice.** `notification_preferences` (optional notices, per category and channel, with
quiet hours in a named zone), `communication_consents` (grant and withdrawal evidence).

**Dispatch.** `notification_intents`, `notification_renders`, `delivery_attempts`,
`delivery_observations`, `contact_delivery_health`, `scheduled_communications`.

## Design rules this migration follows

- Status values are `VARCHAR` + `CHECK`, never PostgreSQL enum types; the longest literal fits the
  declared width.
- No `DEFAULT now()` on any application-written column, and no partial index predicate reads the
  clock — the worker binds its own instant.
- Worker queues claim with `FOR UPDATE SKIP LOCKED` under a lease with an owner and an expiry; a
  half-held lease is refused by `CHECK`.
- Append-only and freeze triggers cover `INSERT` where a child could otherwise be appended to a frozen
  parent, and the foreign keys onto evidence tables restrict rather than cascade, because a cascade
  would fire the trigger and fail.
- `NULLS NOT DISTINCT` is used where a nullable column is part of an identity — a category-wide
  preference default, a market-wide consent — so two competing defaults cannot both exist.

## What this migration leaves open

- **No in-app inbox projection.** The durable in-app channel is a delivery route here; the read model a
  guest's notification bell renders from belongs with the discovery projections in migration `029`.
- **No message full-text search.** The feature document is explicit that database search is not
  automatically authorized for support, and that retrieval must be purpose-scoped and audited. Adding
  an index before that access path exists would create the capability without the control.
- **No encryption at rest for message bodies.** The schema supports it — `body_reference` points at
  external encrypted storage and the content hash stays in the row — but the key management that makes
  it real is a platform concern, not a migration.
- **Stay operations are migration `025`.** The feature document's third half shares these notification
  tables and adds nothing to them.

## Deviations from the plan and the feature document

**No second outbox, inbox, idempotency or audit table.** The document proposes `outbox_events`,
`inbox_receipts`, `provider_webhook_inbox`, `idempotency_records` and `audit_records` as shared
records. Migration `012` delivered `command_idempotency_records`, `outbox_events`,
`consumer_inbox_receipts` and append-only `audit_events`; `021` delivered `payment_webhook_deliveries`;
`013` delivered `provider_accounts`. A second copy of any of them would mean two answers to the same
question and a second publisher to operate.

**Participant authority is checked by a trigger, not only by the service.** The document asks for the
conversation row to be locked while membership is verified. That is still the write path, but the
`BEFORE INSERT` trigger on `messages` re-checks that the sender is an active participant of this
conversation writing as themselves. It is the defence that survives a service defect, and it costs one
indexed lookup per message.

**Message sequence is validated against the allocator.** The document asks for a unique constraint on
`(conversation_id, sequence)`. That stops two messages sharing a number; it does not stop a writer
skipping the allocator and claiming a number far ahead of it, leaving a permanent hole no later send
can fill. The same trigger closes that.

**A classifier may not restrict a person alone.** The document says a model score creates an action
proposal rather than an invisible final decision. `ck_message_moderation_actions_authority` makes that
a constraint: `RESTRICT` and `SAFETY_ESCALATE` cannot be attributed to `MODEL`.

**An attachment cannot be cited before it is approved.** The document describes the staged upload flow
as a sequence of service steps. `ck_message_attachments_finalization` makes the last step a rule: a
`message_id` may only be set on an object whose scan state is `APPROVED`.

## What this closes

Nothing was left dangling by an earlier migration for this one to pick up. `scheduled_communications`
takes forward references to `bookings` and `booking_revisions` created in `020` and `023`, and
`notification_intents` references `contact_channels` from `014` and `provider_accounts` from `013`,
all of which already existed.

## Exit criteria

- Twenty-two changesets apply to an empty database and to a database at `023`.
- Enum-width audit returns no rows.
- Eighty-two probe scenarios behave as tabulated above.
- Full rollback leaves zero tables, zero functions and zero changelog rows; re-apply is clean.
- Every model field maps to a real column and every `NOT NULL` column has a field, nullability
  included.
- `compileJava` and `javadoc` are clean and the application boots with all eighteen repositories
  resolved.
