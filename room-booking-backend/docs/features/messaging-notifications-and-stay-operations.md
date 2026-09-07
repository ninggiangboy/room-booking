# Messaging, Notifications, and Stay Operations

## Purpose

This document defines the target design for D12 — Messaging and Notifications and D13 — Check-in,
Stay, Property Operations, and Incident Response from the
[marketplace problem breakdown](../marketplace-problem-breakdown.md). It answers one connected
product question: after a guest has an inquiry or accepted stay contract, how does the platform
deliver trustworthy communication, safe property access, operational fulfillment, and recoverable
incident handling without confusing a delivered message or device signal with booking truth?

D12 and D13 are designed together because booking conversations, transactional reminders,
pre-arrival instructions, access release, readiness, and in-stay escalation share actors, timing,
privacy boundaries, and one support timeline. They remain separate logical authorities:

- **Messaging** owns immutable conversation content, participants, visibility, moderation state, and
  read position.
- **Notification orchestration** owns notification intents, recipient/channel decisions, rendered
  artifacts, attempts, and delivery evidence.
- **Stay operations** owns readiness tasks, access grants, check-in/out evidence, operational
  incidents, and the proposed stay-outcome decision.

The [availability and booking design](availability-reservation-and-booking.md) remains authoritative
for the stay contract and booking lifecycle. The
[cancellation and modification design](cancellation-modification-and-refund.md) owns cancellation,
modification, relocation entitlement, and financial remedy decisions. The
[payment design](payment-orchestration.md) owns provider collection movement, while the
[ledger and payout design](ledger-reconciliation-and-host-payout.md) owns economic postings and host
release. [Trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns
cross-domain risk decisions, content moderation, protective restrictions, fraud labels, and appeals;
the [disputes, damage claims, insurance, and support design](disputes-damage-claims-and-support.md)
owns support cases, damage/provider claims, remedy authorization, financial funding decisions, and
case appeals. This feature retains original message and incident evidence and the urgent operational
route. It communicates and supplies evidence to those domains; it cannot silently change their state.
[Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md) owns
review rights, double-blind publication, original review revisions, aggregates, and aspect evidence;
D12 delivers its neutral reminders and D13 supplies completion evidence only.
[Data, experimentation, and machine-learning platform](data-experimentation-and-ml-platform.md)
owns cross-domain event/experiment contracts, point-in-time model data, and prediction lifecycle.
D12–D13 own consent-critical delivery, message/access/incident truth, urgency floors, and deterministic
fallback when analytical or model dependencies are unavailable.

## Status and dependencies

[Vietnam market readiness and internationalization](multi-market-compliance-and-localization.md)
owns approved locales, content-policy context, provider accounts, and market activation.

This is an implementation-oriented **target design**, not a description of implemented booking
communication or property operations. No Java API, migration, provider adapter, worker, or state
machine proposed here exists merely because it appears in this document.

The repository currently provides these foundations:

- identity, role, authentication, verified-email, and host-profile tables and Java services;
- listing address, IANA time zone, capacity, house-rule, image, and publication foundations in
  [migration 002](../data-model/002-listing-catalog.md);
- booking participants, local stay dates, accepted listing snapshot, lifecycle, and overlap defense
  in [migration 004](../data-model/004-booking.md) and `004-booking.sql`;
- completed-stay review and favorite foundations in
  [migration 006](../data-model/006-trust-engagement.md);
- a narrow `EmailSender` SMTP port plus after-commit authentication listeners for email verification
  and password reset.

The current authentication email path is not a durable notification platform. It has no outbox,
intent record, template/version registry, delivery attempt, provider callback inbox, retry worker,
preference evaluation, or reconciliation. The repository has no conversation, message, attachment,
notification, check-in, access-grant, task, incident, or stay-evidence tables and no related product
APIs.

Recommended dependency order:

1. Freeze booking participant, delegated co-host, exact-address disclosure, official check-in/out,
   and stay-outcome ownership decisions.
2. Introduce transactional outbox/inbox primitives and a booking-scoped authorization policy.
3. Deliver immutable booking conversations and one durable transactional email channel.
4. Add pre-arrival instruction release, manual access fallback, readiness, check-in/out evidence,
   and incident intake.
5. Add provider callbacks, push/SMS, scheduled reminders, attachments, support/moderation access,
   and operational integrations.
6. Add approved smart-lock/property-management integrations, Vietnamese translation, advanced routing,
   and bounded AI assistance after deterministic workflows produce reliable evidence.

The target release supports Vietnam with Vietnamese plus an approved fallback, unique-rental and
pooled hotel bookings, inquiry/booking conversations, in-app inbox, approved transactional channels,
controlled check-in/access instructions, readiness, incidents, provider recovery, and bounded AI
assistance. Dependency order may validate a narrower path first, but that path is not the completed
feature.

## Goals

- Give authorized guests, hosts, co-hosts, and support actors one auditable, booking-contextual
  communication surface.
- Create notification intent exactly once from committed domain facts and track each delivery
  attempt without granting delivery state domain authority.
- Deliver mandatory transactional information reliably while respecting channel consent,
  preferences, quiet hours, locale, and market policy.
- Reveal exact address, arrival instructions, and access secrets only to the correct actor and only
  when deterministic release conditions hold.
- Represent property readiness, check-in/out, no-access failures, and stay incidents as explicit
  evidence and state transitions.
- Separate operational triage from authoritative cancellation, refund, relocation, claim, payment,
  ledger, review, and safety decisions.
- Make workflows safe under retries, duplicate/out-of-order events, provider uncertainty, worker
  crashes, daylight-saving transitions, and policy/configuration changes.
- Give support and operations an authorized, chronological evidence view plus controlled domain
  commands, without direct database edits.
- Preserve original content, render versions, provider evidence, access history, and decision
  provenance for disputes, audits, privacy requests, and replay.
- Use a correct modular-monolith boundary; distributed extraction is a measured-scale capability
  activated only when load or organizational evidence justifies it.

## Non-goals

- Owning booking confirmation, cancellation, modification, inventory release, no-show, or final
  `COMPLETED` transitions.
- Calculating prices, add-on amounts, refund entitlement, relocation budget, tax, ledger entries,
  payout eligibility, or claim liability.
- Building a general consumer chat/social network or allowing arbitrary non-booking contacts.
- Guaranteeing real-time delivery over email, SMS, push, device, or property-management providers.
- Treating email opens, message reads, Global Positioning System (GPS), or lock events as conclusive
  presence, consent, safety, or contractual evidence.
- Building a full workforce-management, hotel property-management, customer-support, insurance,
  emergency-dispatch, or law-enforcement system.
- Storing raw smart-lock master credentials, identity documents, payment credentials, or provider
  secrets in conversation content.
- Letting a Large Language Model (LLM) autonomously send consequential promises, disclose secrets,
  classify safety severity conclusively, or execute remedies.
- Introducing microservices, Kafka, event sourcing, sharding, vector databases, or multi-provider
  channel routing before measured need.

## Core principles and invariants

### Communication reports facts; it does not create them

A notification about confirmation exists because `BookingConfirmed` committed. A delivered email
does not confirm a booking, a read receipt does not accept a policy, and an AI draft does not promise
a refund. Consequential user actions use authenticated domain commands with explicit confirmation,
not free-form reply interpretation.

### Conversation, notification, and stay state are independent

One user-visible timeline may project all three domains, but their records and transition authority
stay separate. A chat message may link to an incident; the incident may request a booking remedy;
the cancellation domain alone records the entitlement and booking change.

### Evidence is immutable and interpretation is additive

Sent message revisions, rendered notification artifacts, provider callbacks, access observations,
task attestations, and incident evidence are immutable. Redaction, withdrawal, moderation, corrected
translation, superseding instructions, or a changed finding adds a new record and preserves the
original under its lawful access policy.

### Authorization is evaluated at write and read time

Participant membership at send time is recorded, but every later read also applies resource scope,
account restriction, support purpose, legal hold, retention, and sensitive-field policy. Revoking a
co-host stops new access; it does not erase who previously accessed or authored evidence.

### Secrets are not ordinary messages

Door codes, lock tokens, exact entry details, and identity-registration data use encrypted,
purpose-specific records with short-lived authorized retrieval. A message may contain an opaque
action reference, never the reusable provider secret. Push/SMS previews contain no exact address,
code, identity detail, allegation, or sensitive incident description.

### Time uses explicit local and instant semantics

Stay dates remain half-open `[check_in, check_out)` in the booking's snapshotted IANA time zone.
Scheduled actions retain local wall time, zone ID, resolved UTC instant, daylight-saving resolution,
policy version, and schedule version. Workers compare instants; user explanations show the intended
local time and zone.

### Delivery is at-least-once; user-visible intent is effectively once

Events, jobs, and provider callbacks may repeat. A stable intent key prevents duplicate logical
notices, and a stable attempt key prevents avoidable duplicate sends. Provider acceptance is not
delivery; delivery, bounce, complaint, and unknown are evidence states.

### Missing or stale operational evidence never fabricates success

No readiness attestation means readiness is unknown, not ready. No check-in signal means arrival is
unknown, not no-show. Provider timeout means access provisioning is unknown until queried or
manually replaced. Safety-critical uncertainty escalates to a human path.

### Network calls never hold business locks

Conversation/message, notification intent, access instruction, task, and incident transitions commit
locally with outbox work. Email, SMS, push, translation, malware scan, object storage, smart lock,
property-management, or support provider calls occur after commit.

### Database constraints are the final local defense

Uniqueness and check constraints defend one logical message per idempotency scope, one notification
intent per fact/recipient/purpose, monotonic participant read positions, one active access grant per
scope/version, valid state transitions through guarded updates, and non-overlapping active secret
validity where required.

### Critical paths have a non-AI, non-provider fallback

Guests can retrieve already-released instructions from a durable platform surface during channel
outage. Access automation has an approved manual path. Incident routing has deterministic severity
questions and an emergency instruction path. Translation and AI drafts can be disabled without
breaking contractual communication.

### Historical replay is deterministic

An intent records source event ID/version, notification policy version, template version, locale,
recipient address token, and render variables or their immutable source reference. Stay decisions
record the booking snapshot, evidence IDs, policy version, actor, and effective time used.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Conversation | Authorized communication container linked to an inquiry, booking, incident, or support case |
| Participant | Actor with a role, membership interval, and permission set in a conversation |
| Message | Immutable authored or system-generated content envelope with a stable sequence |
| Revision | Additive correction/redaction/withdrawal record; it never overwrites original evidence |
| Structured action | Typed, server-generated call to an authenticated domain command or view |
| Read position | Highest contiguous message sequence acknowledged by one participant/device policy |
| Notification intent | Durable decision that a defined fact should be communicated to one recipient for one purpose |
| Delivery attempt | One provider/channel submission for an intent and route |
| Delivery evidence | Provider/platform observation such as accepted, delivered, bounced, complained, or unknown |
| Transactional notice | Communication necessary for a requested service, contract, security, safety, or legally approved purpose |
| Marketing notice | Promotional communication governed by separate consent and suppression policy |
| Mandatory notice | Transactional notice that preference settings cannot suppress, subject to lawful channel availability |
| Quiet hours | Recipient-local interval delaying non-urgent eligible delivery, not changing domain deadlines |
| Template version | Immutable approved content, variable schema, channel, locale, and classification |
| Operational stay | Fulfillment projection for one booking revision from preparation through outcome proposal |
| Instruction set | Versioned pre-arrival, address, entry, house, contact, and fallback information |
| Release condition | Deterministic predicate controlling whether instruction fields become retrievable |
| Access grant | Time-bounded authorization to enter a property, separate from the underlying device credential |
| Access secret | Encrypted code/token/credential material returned only through a controlled reveal path |
| Readiness task | Assigned operational work with deadline, evidence requirement, and status |
| Observation | Immutable signal from a person or provider, not automatically a final fact |
| Stay evidence | Typed observation considered by a versioned stay-outcome decision |
| Incident | Operational issue requiring severity, ownership, service-level objective (SLO), evidence, and resolution |
| Safety escalation | Immediate, separately routed protective workflow; not merely a high-priority support ticket |
| Remedy request | Auditable request to an owning domain for relocation, refund, cancellation, block, hold, or other action |
| Local schedule | Wall-clock rule plus IANA zone resolved to a UTC instant under an explicit daylight-saving policy |
| Provider acceptance | Provider acknowledged submission; it does not prove end-user delivery or device action |

Public resource identifiers should be opaque UUID or Universally Unique Lexicographically Sortable
Identifier (ULID)-like values. Internal message ordering uses a conversation-scoped increasing
`BIGINT` sequence allocated transactionally; timestamps alone never define order.

## End-to-end flow

```text
BookingConfirmed (committed booking fact)
  -> inbox deduplication
  -> create booking conversation and participant snapshot if absent
  -> create notification intents from versioned policy
  -> render approved locale/channel templates
  -> submit attempts asynchronously and record provider evidence
  -> create operational stay and readiness/instruction schedule
  -> host publishes versioned instructions; policy controls field release
  -> provision access grant asynchronously or activate manual fallback
  -> guest retrieves instructions through authorized reveal endpoint
  -> readiness/check-in/out/device observations append to stay evidence
  -> ordinary issue or safety report opens and routes an incident
  -> incident requests controlled remedy from the owning domain when needed
  -> stay operations proposes outcome from policy plus evidence
  -> booking commits CheckInRecorded/StayCompleted/NoShow or review-required result
  -> notifications and review eligibility consume committed facts
```

The local transaction boundary is one aggregate decision plus outbox facts. For example, sending a
message authorizes the participant, reserves a sequence, inserts the immutable message, updates the
conversation version, and writes `MessageSent` in one transaction. Attachment upload and scanning,
notification transport, translation, and moderation occur in separate retryable steps.

The cross-domain flow is a saga. Stay operations can place an operational block request, open an
incident, or request relocation/refund review. The inventory, booking, cancellation, finance, and
support domains accept or reject those commands under their own current versions and policies.

## Ownership and source-of-truth matrix

| Fact or decision | Authoritative owner | Use in this feature |
| --- | --- | --- |
| Actor/session/role/contact verification | [Identity and access](identity-accounts-and-access.md) | Authorization and routable contact input |
| Co-host/listing operational permission | Listing/delegation policy | Conversation and task membership |
| Current listing content and address | Listing catalog | Draft instructions; never rewrite booking history |
| Accepted address/rules/time zone/check-in snapshot | Booking | Contractual instruction and schedule input |
| Booking participant and lifecycle | Booking | Conversation scope and stay eligibility |
| Cancellation/modification/relocation entitlement | Cancellation | Render committed result; request, never invent |
| Payment/refund movement | Payment | Render safe committed state only |
| Host balance/payout state | Ledger/payout | Render safe committed state only |
| Conversation content/read position | Messaging | Primary authority |
| Notification intent/render/attempt/delivery | Notification orchestration | Primary authority |
| Provider transport callback | Notification adapter evidence | Input to delivery state |
| Instruction/access/task/observation | Stay operations | Primary authority |
| Final booking stay lifecycle | Booking | Accepts evidence-backed transition request |
| Incident coordination | Stay operations for operational cases; support/safety for transferred cases | Opens/routes and retains linked timeline |
| Final safety/moderation decision | Trust and safety | Applies intervention and appeal policy |
| Review eligibility/publication | Reviews | Consumes committed stay outcome |

## Conversation model and participant authorization

### Conversation scopes

Supported scopes are explicit, not inferred from arbitrary user pairs:

- `INQUIRY`: guest and listing operator before a booking, with restricted disclosure;
- `BOOKING`: participants in one accepted booking/revision;
- `INCIDENT`: restricted thread for an incident, optionally linked to selected booking messages;
- `SUPPORT`: controlled case communication with purpose-bound agent access.

The target release implements `INQUIRY`, `BOOKING`, and authorized operations/support conversation
scopes. An inquiry converted to booking creates or links a new
booking conversation rather than silently broadening pre-booking history to newly added operators.

### Membership and permissions

Each participant record contains actor ID, actor type, role (`GUEST`, `HOST`, `CO_HOST`, `SUPPORT`,
`SYSTEM`), joined/left instants, authority source/version, and permissions such as read, send,
upload, manage templates, view sensitive instructions, or moderate. Host identity from the booking
snapshot is not enough to grant every current employee/co-host access.

Rules include:

- the contracting guest and explicitly delegated travel-party members receive only their approved
  scope;
- the booking host and co-host need current listing operational authority;
- removing a co-host closes membership immediately for future reads and sends;
- support access requires case assignment, purpose code, time-bound elevation, and an audit record;
- moderator emergency access requires role, reason, incident/case reference, and enhanced logging;
- blocked-user policy may stop new free-form messages while preserving mandatory system notices and
  emergency reporting;
- suspended accounts lose normal send capability but retain approved access to critical travel,
  safety, appeal, and legal information.

### Ordering, edits, and deletion

Messages receive a strictly increasing sequence within the conversation. The API paginates by
opaque cursor containing sequence and authorization context, not timestamp offset. A send retry with
the same `(conversation_id, sender_id, idempotency_key)` returns the original message.

Messages are immutable after send. A correction creates a revision linked to the original
and renders an “edited” state; a withdrawal hides ordinary presentation but preserves protected
evidence. Hard deletion is limited to retention/privacy policy after holds expire and must not break
referential audit. Users cannot erase another participant's copy of material dispute evidence.

Read state records the highest contiguous visible sequence. It does not imply comprehension,
contract acceptance, presence, or consent. Per-device receipts may be telemetry, but the durable
participant read position is monotonic.

## Message content, attachments, translation, and structured actions

### Content envelopes

A message has a typed envelope:

- `TEXT` with original UTF-8 content and detected/declared language;
- `MEDIA` or `ATTACHMENT` referencing quarantined/approved object metadata;
- `SYSTEM_FACT` rendered from a committed domain event and immutable template version;
- `STRUCTURED_ACTION` containing an action type and opaque resource reference;
- `INSTRUCTION_UPDATE` linking to an instruction version without embedding secrets;
- `INCIDENT_UPDATE` linking to a user-safe incident projection.

Clients submit only allowed author content. They cannot set `SYSTEM`, delivery, moderation,
translation, support, or action-authority fields. HTML is sanitized or avoided; links are normalized
and risk-scanned. Unicode normalization must not destroy the retained original bytes/content hash.

### Attachments and media

Use a staged upload flow:

1. authorize conversation and declare media type, size, checksum, and purpose;
2. issue a short-lived upload capability to quarantine storage;
3. verify checksum, size, actual type, decompression limits, and malware policy;
4. strip dangerous metadata where policy permits while preserving the protected original when
   evidence retention requires it;
5. mark approved or rejected and emit a content-ready fact;
6. allow message finalization only for approved objects, or show a pending attachment safely.

Object storage URLs are short-lived and authorization is rechecked on download. General events and
logs contain object IDs, not signed URLs, filenames with personal data, or extracted content.

### Translation

Translation is a derived view. Retain original content, source language, translated text, target
locale, provider/model version, glossary version, time, and confidence. Show that a translation is
machine-generated and always permit viewing the original. Re-translation creates a new version.

Translation must protect placeholders, amounts, dates, property names, access references, and policy
terms. It may not alter structured actions or become evidence that the author made a statement in
the target language. Failure falls back to original content rather than blocking message delivery.

### Structured actions

Buttons such as “view booking,” “pay balance,” “review cancellation,” “report access problem,” or
“contact support” carry a server-issued action type and resource ID. Opening the action performs
fresh authentication, authorization, expiry, and domain-state checks. A message link is not a bearer
authorization and cannot encode an authoritative amount or requested transition.

## Abuse prevention, contact masking, and moderation

Deterministic controls run synchronously before accepting obviously invalid or dangerous content:

- participant authorization and account/listing state;
- message, conversation, recipient, device, and network rate limits;
- size/type/link limits and attachment quarantine;
- repeated-message and bulk-recipient detection;
- prohibited secret types and known malicious URL/hash checks;
- market-policy contact masking before the permitted disclosure stage.

Risk rules and classifiers may flag scam, off-platform payment, credential theft, harassment,
extortion, hate, sexual content, personal data, or evasion. A model score creates an action proposal,
not an invisible final decision. Policy maps evidence and confidence to allow, warn, mask, quarantine,
delay, human review, restrict, or safety-escalate. Every intervention stores policy/model version,
reason code, evidence references, actor, explanation class, appeal route, and outcome.

Contact masking must be booking-stage- and market-aware. Pre-booking content can mask telephone numbers,
emails, external handles, payment links, and encoded variants. Post-confirmation policy may permit
necessary contact details without permitting off-platform payment solicitation. Original evidence
is retained under restricted access; ordinary recipients see the policy-approved projection.

Safety reports bypass normal recipient blocking and quiet hours, enter a dedicated queue, display
approved emergency guidance, and can restrict further contact. The platform must not claim to be an
emergency service; exact country-specific wording and routing require legal/safety approval.

## Notification orchestration

### Fact-to-intent policy

An inbox consumer evaluates a committed domain event against an effective-dated notification policy.
The policy defines purpose, classification, eligible recipients, mandatory status, channels, timing,
fallback, template family, variable contract, quiet-hour rule, expiry, and deduplication key.

Recommended intent uniqueness is:

```text
(source_event_id, recipient_actor_id, purpose_code, policy_version)
```

Reprocessing the same event returns the same intent. A genuinely changed domain fact emits a new
event and may create a superseding intent. Operators cannot bypass uniqueness by changing a template
or provider route.

Recipient resolution snapshots actor ID and approved destination reference/version. The destination
itself remains in the identity/contact boundary. A contact change after intent creation follows an
explicit policy: security alerts may notify old and new independently; ordinary unsent reminders
may re-resolve before dispatch; already-submitted attempts are immutable.

### Classification, consent, preference, and quiet hours

Classification must be approved per market and purpose:

- `TRANSACTIONAL_MANDATORY`: security, booking-critical, safety, required legal/contractual notices;
- `TRANSACTIONAL_OPTIONAL`: useful service reminders a user may configure;
- `MARKETING`: promotion, re-engagement, cross-sell, or other campaign purpose.

Consent records purpose/category, channel, jurisdiction basis, source, notice version, actor, granted
and withdrawn instants, and evidence. Marketing suppression is immediate for future sends. Mandatory
transactional messages ignore ordinary opt-out only where approved, but still honor invalid,
complained, unsafe, or legally suppressed destinations through an alternate in-app/support path.

Quiet hours use the recipient's chosen or policy-derived IANA zone. They delay optional notices,
never move a booking/payment/cancellation deadline, and never delay approved urgent access or safety
communication. A scheduled reminder records the original domain deadline so copy does not imply that
delivery time changed entitlement.

### Channel selection and fallback

Channel policy considers classification, verified destinations, consent, urgency, sensitivity,
locale support, device capability, cost cap, recent failures, and market rules. Target default:

1. durable in-app notification/inbox projection;
2. transactional email for booking-critical notices;
3. support escalation when no routable critical destination exists.

Push and SMS are later channels. Fallback is a policy-defined route, not an attempt storm. For
example, provider-rejected email may create an in-app alert and support exception; it must not expose
access data in SMS merely to improve delivery rate.

### Templates and rendering

Templates are immutable, versioned, channel- and locale-specific artifacts with:

- stable family and purpose code;
- status `DRAFT`, `IN_REVIEW`, `APPROVED`, `ACTIVE`, `RETIRED`;
- classification and market scope;
- subject/title/body and safe preview variant;
- typed variable schema, data classification, escaping rule, and required/optional markers;
- links/action schemas and expiration;
- approver, approval evidence, effective interval, and rollback predecessor;
- snapshot/golden render tests.

Rendering uses only allowlisted structured facts. Missing required variables fail the render into an
operations queue; they are never filled by an LLM. Money uses authoritative minor units/currency and
approved locale formatting. Dates carry instant, local wall time, zone, and locale. Rendering stores
the template/version, variable-source hash, content hash, and protected rendered artifact or
reproducible inputs.

### Attempts and delivery state

Suggested attempt states are:

```text
PLANNED -> CLAIMED -> SUBMITTING -> ACCEPTED -> DELIVERED
                       |              |          |
                       v              v          +-> COMPLAINED
                    FAILED         BOUNCED
                       |
                       +-> UNKNOWN -> query/reconcile/retry-or-escalate
```

`ACCEPTED` means provider receipt only. State reduction is monotonic by evidence precedence while
retaining all observations. A late bounce or complaint may follow accepted/delivered evidence and
updates destination health without erasing history. Opens and clicks are optional privacy-reviewed
telemetry, not delivery or consent authority.

Retries use stable provider idempotency where available, exponential backoff with jitter, provider
and destination circuit breakers, expiry, and attempt caps. An unknown submission is queried or
reconciled before a new provider send when duplicate impact is material.

### Reminder scheduling

Schedules derive from committed facts and versioned rules for payment, check-in, checkout, review,
cancellation deadlines, incident follow-up, and payout. Each job has schedule key, anchor fact and
version, wall-time rule, zone, resolved instant, policy version, supersession key, and state.

When a booking is modified, the new revision supersedes future jobs; a worker rechecks the latest
authorized source version before creating an intent. Cancellation invalidates inapplicable arrival
jobs but creates cancellation notices from the cancellation event. A job that fires late may be
discarded if its usefulness expiry passed; it must not send misleading stale instructions.

## Pre-arrival instructions and controlled disclosure

An instruction set is versioned per booking revision and separates fields by sensitivity:

- public/early: general arrival window, preparation checklist, host contact method;
- confirmed-booking: approximate directions and approved house reminders;
- time-gated: exact address, unit/floor, entry route, meeting point;
- secret: access code/token and fallback verification data;
- post-entry: appliance, parking, network, safety equipment, checkout details.

Release conditions are deterministic predicates over authoritative facts, for example confirmed
booking, payment requirement satisfied, identity/compliance condition satisfied, current instant
within approved window, no access/safety restriction, and actor permission. Product/legal owners
must approve which conditions are allowed; hosts cannot add arbitrary payment or identity demands.

The retrieval endpoint evaluates conditions each time and returns only released fields. It records
actor, booking, instruction version, field class, purpose, time, session/device risk, and outcome.
Revocation stops future retrieval and triggers a security/operations workflow; it cannot retract a
secret already seen, so replacement and incident procedures are mandatory.

Instruction changes after initial release create a superseding version, notify affected actors, and
retain the old version. Consequential changes close to arrival may require host confirmation,
support review, or a guest-visible acknowledgment depending on policy. Current listing edits never
rewrite the accepted booking instruction basis silently.

For partial platform outage, an encrypted, device-bound offline package may contain only already
released, short-lived information. It expires, is remotely invalidatable when possible, never holds
provider master credentials, and is excluded from backups/analytics. The target may omit offline caching
and instead require a documented support/manual fallback.

## Access grants and smart-device integration

### Access lifecycle

An access grant is platform authorization; a provider credential is one fulfillment method:

```text
DRAFT -> ELIGIBLE -> PROVISIONING -> ACTIVE -> EXPIRED
                         |             |
                         v             +-> REVOKING -> REVOKED
                      UNKNOWN
                         |
                         +-> manual recovery / query / replacement
```

Eligibility references booking revision, actor/party, property/unit, validity window, access policy,
and instruction version. Provisioning occurs asynchronously with a stable operation key. Provider
success creates an immutable observation and encrypted credential reference; it does not alter the
booking.

Validity should begin only as early as approved arrival access and end after checkout plus a bounded
operational grace. Early check-in or late checkout requires an accepted modification/add-on fact
before access validity changes. Cancellation, relocation, safety restriction, or listing emergency
block triggers a revocation operation. If revocation outcome is unknown, operations treats the code
as potentially active and follows the compromised-access runbook.

### Secret handling

- Provider API credentials live in a secrets manager, not the database or logs.
- Guest codes/tokens are encrypted with envelope keys and purpose-separated key access.
- Plaintext appears only in a short response to an authenticated, authorized reveal request.
- No plaintext is emitted to analytics, general events, traces, support notes, push, SMS, or email.
- Support sees whether a grant exists and its last provider evidence, not the secret by default.
- Reveal rate, device/session anomaly, copy behavior where observable, and access changes are audited.
- Key rotation, emergency invalidation, and provider-account compromise have tested runbooks.

### Manual and provider fallback

Every property declares approved access modes and fallback prerequisites before publication or before
accepting affected bookings. A smart-lock outage may switch to an in-person key handoff or verified
lockbox under explicit authorization. Fallback must not ask a guest to pay an unquoted deposit or
send identity/payment data through chat.

The platform stores provider-native references and raw signed evidence under restricted retention.
Adapter normalization cannot discard timestamps, device IDs, result codes, or correlation needed to
investigate access failure. Provider webhooks are signature-verified, deduplicated, and treated as
observations; scheduled query/reconciliation detects lost callbacks.

## Readiness and property operations

An operational stay is created only for an eligible booking revision. Suggested state dimensions
remain independent:

- preparation: `NOT_SCHEDULED`, `SCHEDULED`, `IN_PROGRESS`, `READY`, `BLOCKED`, `UNKNOWN`;
- access: `NOT_REQUIRED`, `PENDING`, `ACTIVE`, `FAILED`, `REVOKED`, `EXPIRED`;
- presence evidence: `NONE`, `ARRIVAL_REPORTED`, `CHECKED_IN_RECORDED`, `DEPARTURE_REPORTED`;
- incident exposure: `NONE`, `OPEN_NON_SAFETY`, `OPEN_SAFETY`, `RESOLVED`, `TRANSFERRED`;
- outcome proposal: `PENDING`, `COMPLETION_ELIGIBLE`, `NO_SHOW_REVIEW`, `CANCELLED`, `EXCEPTION`.

Do not collapse these into one status. A property can be ready while access provisioning failed; a
guest can report arrival while a safety case remains open.

### Tasks and readiness

Task types include turnover cleaning, inspection, linen, consumables, maintenance verification,
key handoff, registration preparation, and checkout inspection. Each task records property/listing,
booking/revision where applicable, local service window and resolved instants, assignee type/ID,
required evidence, status, dependencies, policy/template version, and audit.

Assignment honors listing operator permissions and least-privilege field access. A cleaner does not
need guest payment history or all messages. Completion is an attestation plus required evidence;
the system may validate time/location/device signals as supporting evidence but not silently certify
quality. Reopening or overriding a task creates a reasoned transition.

Turnover scheduling uses previous checkout, next check-in, property time zone, configured buffer,
and accepted early/late changes. A collision or incomplete critical task before cutoff creates a
readiness exception and may request an inventory emergency block or guest remediation. Stay
operations cannot directly cancel a booking or fabricate availability.

### Maintenance and emergency blocks

Maintenance records asset/area, severity, guest impact, observation, work order, expected window,
resolution evidence, and listing/inventory action requests. An unsafe or unusable condition can
request an immediate calendar block for future dates and a support/safety case for current bookings.
Inventory owns the block and overlap implications; listing owns publication state.

## Check-in, checkout, and stay-outcome evidence

Check-in evidence can include an authenticated guest report, host/operator attestation, in-person
registration, provider access observation, support verification, or approved device evidence. Each
observation records source, subject, event time and received time, source zone, confidence/quality,
provider reference, booking revision, and integrity metadata.

No single optional app interaction is universally conclusive. A lock open may be a cleaner; a GPS
signal may be spoofed or absent; a message read proves neither arrival nor satisfaction. A versioned
policy determines whether evidence is sufficient to request `CheckInRecorded` from booking or needs
review.

Checkout follows the same evidence approach. It can trigger inspection and access expiry but cannot
by itself settle damage liability. Lost property and damage evidence are preserved and transferred
to the future claims/support domain with custody and visibility controls.

### Stay completion proposal

At contractual checkout plus a configured grace period, a worker evaluates:

- latest accepted booking revision and cancellation state;
- official local checkout instant under the snapshotted zone;
- check-in/out and consumption evidence;
- open incident, safety, dispute, or no-show review flags;
- approved policy/version and manual decisions.

If eligible, stay operations emits a completion request/evidence bundle; booking performs the
guarded lifecycle transition and emits `StayCompleted`. If evidence conflicts or a material case is
open, the result is `EXCEPTION` and enters review. Review eligibility and host-fund release consume
the committed booking fact, not the worker's proposal.

The exact directional rights, submission deadline, double-blind release, and correction behavior
after `StayCompleted` are defined in
[the review design](review-reputation-and-aspect-intelligence.md). D12/D13 never open a right merely
because a reminder, access, checkout, or incident event exists.

No-show is likewise an evidence-backed booking decision. Absence of a message/read/device event is
insufficient. Host report, attempted contact, access readiness, guest response, and support evidence
follow the cancellation design's no-show policy.

## Incident intake, triage, and response

### Incident types and severity

Initial categories include cannot access, host unreachable, property not ready, material listing
mismatch, utility failure, maintenance, amenity failure, cleanliness, noise, lost property,
access-security concern, and safety report. Category and severity are separate.

Deterministic triage questions establish immediate danger, medical/fire/crime concern, vulnerable
party, inability to secure shelter, active unauthorized access, habitability, time to check-in,
ongoing stay, and communication availability. Suggested levels:

| Severity | Meaning | Initial behavior |
| --- | --- | --- |
| `S0_SAFETY_CRITICAL` | Immediate threat or potentially severe harm | Show approved emergency guidance; page dedicated safety/human owner; restrict unsafe automation |
| `S1_URGENT` | Cannot enter, uninhabitable, stranded, or severe active disruption | 24/7-capable queue if offered; rapid host/support escalation and fallback |
| `S2_HIGH` | Material stay impact requiring time-bound remedy | Assign operations/support with explicit SLO |
| `S3_STANDARD` | Ordinary service issue | Normal queue and host response window |
| `S4_INFORMATIONAL` | Question/evidence without current impact | Track and answer without false urgency |

Exact thresholds, hours, emergency copy, and regulatory obligations are launch-market decisions.
An ML prediction may raise priority or recommend questions; it may never lower a user-declared safety
report below the deterministic minimum or close the case.

### Incident lifecycle

```text
OPEN -> TRIAGED -> ASSIGNED -> RESPONDING -> MITIGATED -> RESOLVED -> CLOSED
  |        |          |             |
  |        +-> SAFETY_ESCALATED     +-> REMEDY_PENDING
  +-> DUPLICATE/LINKED
  +-> TRANSFERRED_TO_SUPPORT/TRUST/CLAIMS
```

Each transition records actor, reason, previous version, time, evidence references, SLO clock effect,
and outbox facts. `MITIGATED` means immediate impact was reduced; it is not a final entitlement or
admission. `RESOLVED` records operational resolution, while `CLOSED` requires communication and any
linked domain requests to reach an allowed state.

### Evidence and custody

Evidence may be a selected message, attachment, photo/video, call metadata, task attestation,
provider observation, listing/booking snapshot, or support note. Store immutable hashes, capture and
received time, uploader/source, booking/incident context, transformations, visibility, retention,
and legal-hold state. Never copy all private conversation content into every incident by default;
link selected evidence with purpose-scoped authorization.

Support notes are distinct from participant-visible messages. Assertions are labeled by source and
must not be presented as verified facts without a decision record. Downloads use short-lived links,
malware controls, and access audit.

### Remedy and relocation boundary

An incident can request:

- contact escalation or manual access fallback;
- listing/inventory emergency block;
- cancellation/refund/credit/waiver preview or execution;
- relocation search and budget authorization;
- payment collection pause, payout/host-fund hold, or risk review;
- trust/safety restriction or claims intake.

Each request contains incident/evidence IDs, requested action, reason code, urgency, actor, policy
reference, and idempotency key. The owning domain returns accepted/rejected/pending with its own
decision ID/version. Operations renders that result and never edits another domain's tables.

Relocation candidate search must honor dates, occupancy, accessibility requirements, safety,
location constraints, inventory, and authorized budget. Search can rank candidates, but booking and
cancellation own replacement contract and economics. An agent cannot promise a property before an
authoritative hold and quote exist.

## Scheduled host messages and operational automation

Hosts may configure approved message schedules relative to committed anchors such as confirmation,
local check-in, or checkout. The schedule stores listing scope, message template/version, offset,
wall-time/zone semantics, booking eligibility filter, required facts, sender presentation, approval
state, and effective interval.

Before send, the worker rechecks booking revision/status, participant permission, current template,
duplicate/supersession key, and prohibited/sensitive variables. A host cannot schedule undisclosed
fees, external payment instructions, unverified amenities, discriminatory screening, or access
secrets in ordinary message content. Changes affect future jobs only and preserve prior sends.

Automation may create tasks or draft messages from deterministic events. Consequential actions such
as changing access windows, marking readiness, completing a stay, closing a safety incident, or
approving a remedy require their domain transition and evidence. Automation has a per-listing and
global kill switch.

## Conceptual data model

All records below are proposed future tables. Existing migrations remain unchanged; implementation
requires forward-only Liquibase changesets.

### Messaging records

| Record | Important fields and constraints |
| --- | --- |
| `conversations` | ID, scope type/reference, status, next sequence, created time, optimistic version; unique active scope identity |
| `conversation_participants` | conversation, actor, role, permissions, authority source/version, joined/left time; non-overlapping active membership per actor/role |
| `messages` | conversation, sequence, sender, type, original content/reference, language, content hash, reply-to, idempotency key, created time; unique conversation sequence and sender key |
| `message_revisions` | message, revision, kind, replacement projection/reference, actor/reason, created time; original remains immutable |
| `message_attachments` | message/upload object, purpose, scan/transformation states, checksum, classification, retention/hold |
| `message_translations` | message/revision, locale, engine/version, glossary, content, confidence, created time; unique source version/locale/engine version |
| `conversation_read_positions` | conversation, participant, highest contiguous sequence, updated time/version; monotonic guarded update |
| `message_moderation_actions` | message/content version, policy/model, reason/evidence, action, reviewer, appeal state, timestamps |

Free-form content may be stored encrypted separately from searchable metadata. Database full-text
search is not automatically authorized for support; purpose-scoped retrieval and audit are required.

### Notification records

| Record | Important fields and constraints |
| --- | --- |
| `notification_policies` | immutable version, purpose, classification, recipient/channel/timing/fallback rules, market/effective interval, status |
| `notification_templates` | family/version, channel, locale, typed variables, preview, approval/effective state, content hash |
| `notification_preferences` | actor, purpose/category, channel, enabled state, quiet hours/zone, source, version |
| `communication_consents` | actor, category/channel/market, notice/legal basis reference, grant/withdraw evidence and times |
| `notification_intents` | source event/version, recipient, purpose, policy/template choice, schedule/expiry, state, supersession, immutable input hash; unique logical intent |
| `notification_renders` | intent, channel/locale/template, variable-source hash, protected artifact/content hash, render status/error |
| `delivery_attempts` | intent/route/attempt number, provider account, idempotency key, state, provider reference, claimed/submitted times, retry time; unique attempt key/reference |
| `delivery_observations` | attempt, provider event ID/type/time, received time, verified raw reference/hash; unique provider account/event ID |
| `contact_delivery_health` | contact reference/version/channel, bounded status, bounce/complaint counters, suppression/review state and source |
| `scheduled_communications` | schedule/supersession key, anchor/version, local rule/zone/resolved instant, state, claimed time, expiry |

Do not duplicate raw email/telephone destinations broadly. Store opaque identity-contact references
and only the minimum protected dispatch snapshot required for audit/retry.

### Stay-operation records

| Record | Important fields and constraints |
| --- | --- |
| `operational_stays` | booking/revision, listing/unit, local dates/time zone snapshot, state dimensions, policy versions, optimistic version; unique booking revision |
| `instruction_sets` | operational stay, version, status/effective interval, author/approver, structured field references/classification, supersedes, content hash |
| `instruction_access_audit` | actor, instruction version, field class, purpose, session/device risk reference, decision/result, time |
| `access_grants` | operational stay, actor/party/unit, validity instants, policy, mode/provider, state/version, encrypted secret reference, revocation status |
| `access_operations` | grant, operation type, stable key, provider/account/reference, request hash, state, retry/query data |
| `access_observations` | operation/grant/device, provider event identity, type, occurred/received times, integrity/raw reference |
| `operational_tasks` | listing/stay, type, service window, assignee, evidence requirements, status, dependencies, version |
| `task_evidence` | task, observation type/source, protected object/reference, occurred/received times, integrity metadata |
| `stay_observations` | stay, subject/type/source, event/received time, provider/reference, quality/confidence, immutable payload reference |
| `stay_outcome_decisions` | stay, proposal/result, policy version, evidence set/hash, actor, reason, effective time; additive supersession |
| `maintenance_records` | listing/asset/category/severity, impact/window/state, evidence and inventory/listing action references |
| `incidents` | booking/listing/stay, reporter, category, severity, safety flag, state/owner/SLO, policy version, optimistic version |
| `incident_events` | incident sequence, typed transition/note/evidence link, actor, reason, visibility, immutable time/hash |
| `incident_evidence_links` | incident, source domain/type/ID/version, purpose, visibility, custody/retention/hold |
| `remedy_requests` | incident, target domain, action, reason/evidence set, idempotency key, state, target decision reference |

### Shared reliability and governance records

- `outbox_events`: aggregate identity/version, schema/version, correlation/causation, payload reference,
  publish state, retry metadata;
- `inbox_receipts`: consumer, event ID, schema version, result/reference, received/processed time;
- `provider_webhook_inbox`: provider/account/event ID, signature result, raw protected artifact, parsing
  version, processing state;
- `idempotency_records`: actor/scope/key, canonical request hash, response/status reference, expiry;
- `policy_versions`, `audit_records`, and `legal_holds`: shared concepts with domain-specific scope.

### Constraints and indexes

Critical indexes include conversation scope, participant active membership, message sequence and
sender idempotency, unread lookup, intent schedule/state, attempt retry/provider reference, provider
event deduplication, operational stay booking/revision, task assignee/deadline/state, access validity/
state, incident owner/severity/SLO/state, and evidence source identity.

Partial unique constraints should cover one active conversation for a booking scope, one logical
intent identity, one active current instruction version, and provider-native event identity. Check
constraints limit state vocabularies and require terminal timestamps/reasons. Cross-row lifecycle
rules use guarded service transitions and, where feasible, stored procedures or constraints rather
than accepting client-selected status.

### Migration and backfill

Do not edit migrations `001`, `002`, `004`, `006`, or authentication migrations. Add forward
migrations in deployable slices. Existing confirmed/future bookings can receive conversations and
operational stays through an idempotent backfill using the current booking snapshot. Do not fabricate
past messages, deliveries, readiness, access, check-in, or incident evidence.

Authentication emails should migrate only after the durable notification vertical slice proves
equivalent token secrecy and user behavior. Preserve the current adapter during dual-run/shadow
validation; never send both paths to users without a deduplication plan. Deployment must be
expand/backfill/dual-read-or-shadow/cutover/contract, with rollback switches for workers/providers.

## Service boundaries

These are logical modules in the modular monolith, not required microservices.

| Module | Owns | Does not own |
| --- | --- | --- |
| Conversation service | Scope, membership, message sequence/content, read position | Booking state, general identity roles, remedies |
| Content safety gateway | Synchronous controls, moderation proposals/actions, evidence | Final cross-domain safety policy outside delegated scope |
| Attachment service | Quarantine, scanning, object authorization, transformations | Conversation participation |
| Translation service | Derived translations and provenance | Original author meaning or facts |
| Notification policy service | Fact-to-intent, recipient/purpose/channel/timing policy | Source domain truth |
| Template/render service | Approved templates, typed rendering, immutable artifacts | Inventing missing variables |
| Delivery service | Route, attempt execution, provider evidence, retry/reconciliation | Booking/payment transitions |
| Schedule service | Versioned reminder/host-message jobs and supersession | Moving domain deadlines |
| Instruction service | Versioned instruction fields and release decisions | Booking confirmation or payment satisfaction |
| Access service | Grants, secret reveal, provider operations, revocation/recovery | Device provider's native truth or booking state |
| Operations service | Operational stay, readiness, tasks, observations, outcome proposal | Final booking completion/no-show |
| Incident service | Intake, severity, routing, evidence timeline, remedy requests | Refund/relocation/payment/ledger final decisions |
| Booking/cancellation/payment/finance | Their existing authoritative state | Message or delivery state |
| Trust/support/claims | Transferred case decisions, appeals, controlled remedies | Silent edits to operations evidence |

Provider adapters implement narrow ports for email/SMS/push, storage/scanning, translation, smart
locks, property-management systems, and paging/case tools. Each adapter preserves native references,
supports idempotency/query where available, classifies failures, verifies callbacks, and exposes
health. Core policy remains provider-neutral.

## API behavior

The following operations are illustrative target contracts, not implemented routes.

### Guest and host conversation APIs

- `GET /api/v1/bookings/{bookingId}/conversation`
- `GET /api/v1/conversations/{conversationId}/messages?after=...&limit=...`
- `POST /api/v1/conversations/{conversationId}/messages`
- `POST /api/v1/conversations/{conversationId}/attachments/initiate`
- `POST /api/v1/conversations/{conversationId}/read-position`
- `POST /api/v1/messages/{messageId}/report`
- `POST /api/v1/messages/{messageId}/translations`

Message creation requires `Idempotency-Key`, content type, client message ID, and allowed content or
approved attachment IDs. The server determines sender, membership, sequence, policy/moderation
status, timestamps, and structured-action authority. A successful retry returns the original
resource. A reused key with a different canonical request hash returns conflict.

Lists expose only currently authorized projections. Hidden/moderated items use stable tombstones when
needed to preserve sequence; the API does not reveal whether a protected support participant or
safety evidence exists to an unauthorized actor.

### Preference and notification APIs

- `GET/PUT /api/v1/users/me/communication-preferences`
- `GET /api/v1/users/me/notifications`
- `POST /api/v1/users/me/notifications/{id}/acknowledge`
- internal `POST /internal/v1/notification-intents/from-event`
- admin template/policy preview, approve, activate, retire, replay, and suppress operations.

Preference writes use optimistic version or `If-Match`, validate zone/locale/channel, and cannot
disable approved mandatory classes. An in-app acknowledgment is not equivalent to legal acceptance
unless the owning domain runs a separate authenticated acceptance command.

Admin activation uses maker-checker for high-impact mandatory/security templates and records a
render diff plus sample fixtures. Replay references existing intent/source facts and cannot invent a
new event identity merely to resend.

### Stay and access APIs

- `GET /api/v1/bookings/{bookingId}/arrival`
- `GET /api/v1/bookings/{bookingId}/instructions`
- `POST /api/v1/bookings/{bookingId}/access-grants/{grantId}/reveal`
- `POST /api/v1/bookings/{bookingId}/arrival-report`
- `POST /api/v1/bookings/{bookingId}/departure-report`
- host `PUT /api/v1/operational-stays/{stayId}/instructions`
- host `POST /api/v1/operational-tasks/{taskId}/transition`
- host/internal access provision, rotate, revoke, and query operations.

Instruction responses include booking/instruction version, released field classes, local arrival
window/zone, next availability instant for withheld non-secret fields where safe, fallback contact
action, and stale/version metadata. The reveal endpoint requires recent authentication or step-up
according to risk and returns `Cache-Control: no-store`.

Clients cannot claim readiness/check-in/completion by setting status. They submit typed observations;
the server authorizes, records evidence, and evaluates policy. All writes use idempotency and expected
version where repeat or conflict is consequential.

### Incident APIs

- `POST /api/v1/bookings/{bookingId}/incidents`
- `GET /api/v1/incidents/{incidentId}`
- `POST /api/v1/incidents/{incidentId}/evidence/initiate`
- `POST /api/v1/incidents/{incidentId}/messages`
- internal/agent `POST /api/v1/incidents/{incidentId}/transitions`
- internal/agent `POST /api/v1/incidents/{incidentId}/remedy-requests`

Incident intake accepts category, immediate-danger answers, user description, selected message IDs,
and approved evidence references. The server determines initial severity floor, safety route, owner,
SLO, and visible guidance. Idempotent duplicate reports may link to one incident while preserving
each report and reporter.

Support responses expose only role-appropriate data. Guests do not see internal risk reasons, other
guests, staff identity beyond approved presentation, device secrets, or unverified allegations.
Hosts do not automatically see confidential safety statements.

### Error semantics

| HTTP | Stable code | Meaning and retry behavior |
| --- | --- | --- |
| `400` | `INVALID_MESSAGE_CONTENT` | Shape/type/size invalid; correct request |
| `401` | `AUTHENTICATION_REQUIRED` | Authenticate or step up; do not blind retry |
| `403/404` | `RESOURCE_NOT_ACCESSIBLE` | No authorized scope; use consistent non-enumerating behavior |
| `409` | `IDEMPOTENCY_KEY_REUSED` | Same key, different request; use a new key only for new intent |
| `409` | `CONVERSATION_VERSION_CONFLICT` | Refresh membership/conversation state |
| `409` | `STALE_OPERATIONAL_STAY` | Refresh booking revision/stay version |
| `409` | `ACCESS_STATE_CONFLICT` | Requested access transition no longer valid; query current state |
| `410` | `INSTRUCTION_ACCESS_EXPIRED` | Grant/instruction no longer retrievable; use fallback/support |
| `422` | `MESSAGE_NOT_PERMITTED` | Policy blocks content; show safe reason/appeal without detector detail |
| `422` | `INSTRUCTION_NOT_RELEASED` | Release conditions not met; return only safe next action/time |
| `422` | `BOOKING_NOT_OPERATIONALLY_ELIGIBLE` | Booking state/revision disallows operation |
| `429` | `COMMUNICATION_RATE_LIMITED` | Honor server retry time; emergency reporting uses separate protected route |
| `503` | `DELIVERY_CHANNEL_UNAVAILABLE` | Intent remains durable; client usually should not recreate it |
| `503` | `ACCESS_PROVIDER_UNAVAILABLE` | State is pending/unknown; offer approved fallback, not repeated provision |

Provider codes, account existence, moderation features, exact release predicates, private incident
data, and contact validity are not exposed unnecessarily. Responses include correlation ID and safe
retry guidance; they never include access secrets in errors or logs.

## Event contracts

Events are committed past-tense facts with event ID, schema version, occurred time, aggregate ID and
version, actor/reference, correlation ID, causation ID, market, and minimal payload. Producers use a
transactional outbox. Consumers persist inbox deduplication before side effects and tolerate replay.

Representative messaging events:

- `ConversationCreated`, `ConversationParticipantAdded`, `ConversationParticipantRemoved`;
- `MessageSent`, `MessageRevisionAdded`, `MessageAttachmentApproved`, `MessageFlagged`;
- `MessageModerationApplied`, `ConversationReadPositionAdvanced`.

Representative notification events:

- `NotificationIntentCreated`, `NotificationRendered`, `DeliveryAttemptSubmitted`;
- `NotificationProviderAccepted`, `NotificationDelivered`, `NotificationBounced`;
- `NotificationComplained`, `NotificationSuppressed`, `CriticalNotificationUndeliverable`.

Representative stay-operation events:

- `OperationalStayCreated`, `InstructionSetPublished`, `InstructionReleased`;
- `AccessGrantProvisioningStarted`, `AccessGrantActivated`, `AccessGrantRevoked`,
  `AccessGrantOutcomeUnknown`;
- `ReadinessChanged`, `OperationalTaskOverdue`, `CheckInObserved`, `CheckOutObserved`;
- `IncidentOpened`, `IncidentSeverityChanged`, `SafetyEscalationRequested`, `IncidentMitigated`,
  `RemedyRequested`, `IncidentClosed`, `StayCompletionRequested`.

Payloads contain IDs, versions, reason codes, timing, and authorized summary classifications—not
message bodies, exact addresses, door codes, signed URLs, raw incident descriptions, identity data,
or provider secrets. Consumers fetch protected detail using workload identity and purpose.

Booking, payment, cancellation, payout, identity, listing, and review events consumed here are never
rewritten. Schema evolution is additive where possible; incompatible changes use a new version and
dual-consumer rollout. Replay must not re-send expired/stale notices or re-provision access without
current-policy checks.

## Concurrency and idempotency

### Message races

Lock the conversation row briefly to verify current membership/version, allocate one sequence,
insert message and outbox, then commit. Alternatively, use an atomic sequence update returning the
number. Unique constraints on conversation sequence and sender idempotency key resolve races. Do not
lock while scanning/uploading/moderating externally.

Participant removal versus send uses the transaction order that obtains the conversation/membership
guard first; the loser reloads and returns the committed result or access error. A message accepted
before removal remains evidence.

### Intent and delivery races

Unique logical intent identity selects one winner for duplicate domain events. Workers claim due
rows with bounded `FOR UPDATE SKIP LOCKED`, lease owner/expiry, and attempt state. A crashed lease is
recoverable. Provider submission uses a stable operation key derived from attempt identity; unknown
outcomes enter query/reconciliation before resubmission.

Preference withdrawal racing with dispatch follows a documented cutoff: recheck immediately before
claim/submission for suppressible purposes, then preserve any already-submitted attempt evidence.
Mandatory/security policy may differ and must be approved.

### Schedule and booking-change races

Each schedule references anchor aggregate/version and a supersession key. The worker locks the job,
fetches current source truth without holding a network transaction, and atomically marks discarded or
creates the intent. Modification/cancellation creates superseding jobs and invalidations. A stale job
cannot send because the input fingerprint/version fails revalidation.

### Access and lifecycle races

One active grant version per booking/actor/unit/mode is defended by a partial unique constraint.
Provision, rotate, and revoke use distinct stable operation keys. Cancellation versus activation
follows booking event order plus current-state recheck: a late activation for an ineligible booking
immediately creates revocation and an incident, never a usable success projection.

Check-in versus cancellation/no-show/completion is decided by booking using expected aggregate
version and policy-defined effective times. Operations preserves both observations and requests one
guarded transition. It never overwrites the losing fact.

### Incident races

Transitions use optimistic incident version and append-only sequence. Duplicate intake can be
merged/linked only by an authorized command that preserves reporter/evidence records. Safety severity
cannot be lowered concurrently without elevated review and reason. Remedy requests have unique
`(incident_id, target_domain, action_type, idempotency_key)` identity.

Canonical lock ordering within this feature is conversation or operational stay, then child state,
then outbox. Cross-domain work never holds both modules' locks; it uses committed commands/events.

## Security, privacy, and access control

### Authorization

- Enforce actor/resource relationship on every conversation, instruction, task, access, incident,
  evidence, preference, template, and admin operation.
- Separate host listing ownership from co-host operational, messaging, instruction, access, incident,
  and staff-management permissions.
- Use workload identities and purpose-bound service authorization for protected cross-domain reads.
- Require recent step-up for secret reveal, access rotation, emergency override, bulk export, legal
  hold, high-impact template activation, or confidential case access as policy dictates.
- Use maker-checker for broad mandatory notices, mass replay, and high-impact access overrides.

### Data classification and minimization

Classify message content, contact details, exact address, travel dates, access secrets, identity
registration, incident allegations, safety data, photos, device events, staff assignments, provider
payloads, and internal notes separately. General logs/events/metrics use opaque IDs and reason codes.
Trace baggage must not carry message bodies, email/phone, address, filenames, or secrets.

Protect data in transit and at rest; use field/envelope encryption for access secrets and highly
sensitive evidence. Signed links are short-lived, single-purpose where feasible, audience-bound, and
redacted from referrers/logs. Content Security Policy and link handling reduce phishing/exfiltration.

### Retention, privacy rights, and legal hold

Retention varies by communication purpose, contract evidence, safety/support need, attachment type,
market, and legal hold. A privacy deletion request may remove ordinary projections or de-identify
derived data while lawful fraud, financial, contractual, or dispute evidence remains restricted.
Record the policy and reason; do not claim deletion when content remains on hold.

Exports require identity verification, exclude other participants' protected data as required, and
preserve understandable context without exposing internal safety methods or device secrets. Legal
holds are scoped, authorized, audited, reviewable, and released explicitly.

### Threat controls

Cover account takeover, co-host privilege escalation, booking enumeration, support snooping, phishing,
malicious uploads, Unicode/encoded contact evasion, spam, harassment, stalking, secret scraping,
signed-link leakage, provider callback forgery, lock takeover, replayed codes, insider misuse, and
model prompt injection from user content.

Safety and abuse controls must be evaluated for disparate impact and appeal. Do not expose detection
thresholds that make evasion easy. Preserve an independently trusted notification route for access
or account changes where appropriate.

## Observability and operations

### Business and correctness metrics

- booking conversations created exactly once and participant authorization denial rate;
- message send success, moderation action/appeal outcome, report rate, and time to first host reply;
- intent creation completeness by source fact/purpose and duplicate-intent constraint violations;
- critical notice render/send/delivery/undeliverable counts and end-to-end age;
- destination bounce/complaint/suppression and fallback effectiveness;
- instruction publication/release/retrieval and last-minute change rate;
- access provisioning/activation/revocation/unknown outcome and manual fallback rate;
- readiness completion before cutoff, overdue critical tasks, and turnover collision rate;
- incident volume by category/severity, acknowledge/mitigate/resolve time, escalation and reopen rate;
- completion/no-show proposal exceptions and linked remedy outcomes.

Metrics must not optimize provider “delivered” at the expense of complaints, consent, sensitive-data
exposure, or user harm.

### Technical metrics and SLO candidates

- outbox/inbox oldest age, retry count, dead-letter/review queue depth;
- message write/read p50/p95/p99 and unread projection freshness;
- notification intent/render queue lag, channel submission latency/error/unknown rate;
- webhook signature failure, dedup hit, parse failure, callback lag;
- object scan queue age and rejection/failure rate;
- schedule firing lateness and stale-job discard rate;
- secret reveal latency/denial/anomaly and key/provider health;
- incident page/assignment latency by severity and on-call acknowledgment;
- database contention, hot conversations, task claims, and slow query shapes.

Candidate SLOs require product/operations approval. Safety-critical page acknowledgment, arrival
instruction availability, and critical transactional intent completeness need separate objectives
from ordinary chat or marketing delivery.

### Dashboards, alerts, and queues

Provide booking communication health, notification pipeline/channel health, arrival/readiness,
access-provider/revocation, incident/SLO, moderation, and privacy/access dashboards. Alert on missing
critical intents after source facts, oldest outbox age, broad render failure, provider outage,
complaint spike, access activation/revocation unknown near arrival, critical task overdue, unassigned
S0/S1 incidents, and anomalous secret/support access.

Manual queues need owner, priority, aging, claim/lease, safe context, allowed actions, maker-checker
where needed, outcome reason, and audit. Operators can replay/reconcile controlled work, rotate or
revoke grants, activate fallback, correct templates forward, link duplicates, transfer cases, and
request domain remedies. They cannot edit evidence or mark provider/domain success without proof.

Runbooks cover notification-provider outage, bad template rollout, bounce/complaint spike, event lag,
malware/storage outage, smart-lock outage/compromise, leaked code, guest locked out, property not
ready, host unreachable, safety escalation, large incident, privacy request/hold, and restore/replay.

## Failure behavior

| Failure or race | Required behavior |
| --- | --- |
| Database commit succeeds, client times out sending message | Retry key returns original message and sequence |
| Attachment scan/storage unavailable | Keep object quarantined/pending; allow text separately if safe; never expose unscanned file |
| Translation unavailable or low confidence | Show original with status; do not block or invent translation |
| Moderation provider unavailable | Apply deterministic policy/fallback queue; safety reports still route |
| Duplicate/out-of-order source event | Inbox deduplicates; aggregate version/fingerprint prevents stale intent |
| Missing template/variable | Fail render to owned queue; do not ask AI to fill facts |
| Email/SMS/push provider rejects | Classify retryability, update route health, apply approved fallback |
| Provider submission times out | Mark unknown, query/reconcile before duplicate send when material |
| Late bounce/complaint | Append evidence, suppress destination per policy, preserve prior state/history |
| Preference withdrawn during dispatch | Recheck at cutoff; suppress unsubmitted optional work, retain submitted evidence |
| Schedule worker fires after modification/cancellation | Revalidate source version and discard/supersede stale job |
| Notification system down | Source domain remains committed; durable intent/outbox catches up; show in-app truth when available |
| Host has not published instructions | Surface readiness exception before cutoff; never fabricate content |
| Instruction changes after release | Version, notify, audit; old content remains protected evidence |
| Smart-lock provision times out | Mark unknown, query provider, activate manual fallback/incident near arrival |
| Cancellation races with access activation | Recheck; revoke late grant and open exception; do not reactivate booking |
| Access secret suspected leaked | Revoke/rotate, restrict reveal, notify independently, audit and incident-route |
| Readiness task overdue | Escalate and request appropriate block/remedy; do not auto-complete |
| Guest reports cannot enter | Create S1 floor, page route/fallback, preserve evidence; provider “active” is not closure |
| Conflicting check-in observations | Preserve all and require policy/human resolution; no silent overwrite |
| Safety classifier unavailable | Deterministic safety questions and human escalation remain available |
| Remedy target domain unavailable | Incident remains open/remedy pending; retry idempotently and communicate uncertainty |
| Worker crashes after external success | Provider key/query/callback reconstructs outcome without duplicate effect |
| Backup restore loses projections | Replay outbox/inbox safely, rebuild projections, reconcile providers and critical schedules |

## Testing and verification

### Deterministic and unit tests

- Conversation scope/membership permission matrix, join/leave boundaries, blocked/suspended actors.
- Message Unicode, length, link, type, reply, edit/withdrawal, tombstone, and read-position rules.
- Notification classification, consent, preference, destination health, quiet-hour, urgency, fallback,
  expiry, and supersession precedence.
- Template typed variables, escaping, locale, currency, pluralization, date/zone/daylight-saving, safe
  preview, missing-variable failure, and golden rendering.
- Instruction field classification/release predicates and exact-address/access-secret separation.
- Access eligibility, validity, rotation, cancellation/revocation, early/late accepted-change cases.
- Task dependency/readiness, incident severity floor, SLO clocks, transfer, reopen, and remedy rules.
- Check-in/out/completion/no-show evidence matrices including conflicting and absent signals.

### Real-database and concurrency tests

- Unique active booking conversation, message sequence, sender idempotency, logical intent, provider
  event, active grant, and remedy request constraints.
- Concurrent sends allocate distinct ordered sequences; same retry creates one message.
- Participant removal versus send/read; read positions never regress.
- Duplicate events/workers create one intent; worker lease crash safely reclaims.
- Preference withdrawal versus claim/submission and schedule firing versus modification/cancellation.
- Provision versus revoke/rotate and late activation after cancellation.
- Incident transitions, severity change, duplicate intake, and remedy submission under optimistic lock.

### Provider and contract tests

- Email/SMS/push request mapping, stable keys, retry taxonomy, query capability, callbacks, signature
  verification, reordered delivery/bounce/complaint observations, and redaction.
- Object upload/checksum/type/malware/decompression/metadata behavior and signed-download expiry.
- Translation placeholder preservation and provider/model provenance.
- Smart-lock provision/query/rotate/revoke, lost webhook, unknown outcome, device clock skew, and
  provider-account compromise.
- Property-management task/status mapping with unknown values and replay.
- Versioned event producer/consumer compatibility and protected-detail fetch authorization.

### Security, privacy, abuse, and AI tests

- Cross-guest/listing/host/co-host/support/market access, revoked authority, enumeration resistance.
- Exact address before release, secret reveal without step-up, logs/traces/events/cache/browser history,
  stale signed URLs, and screenshot/offline-package risk controls.
- Malicious files, HTML/script, URLs, Unicode/encoded contacts, prompt injection, spam storms, and
  emergency-route abuse.
- Legal hold, retention expiry, export/redaction, privacy deletion propagation, and evidence custody.
- Moderation false-positive/negative slices, appeal restoration, protected-group fairness, drift, and
  human override.
- AI draft hallucination, prohibited promise/secret, unsafe translation, confidence fallback, audit,
  and global/per-market kill switch.

### Failure, replay, and operational acceptance

- Inject every failure-matrix case including process crash before/after local commit and external
  success, database failover, provider outage, callback loss, and queue backlog.
- Replay duplicate/reordered outbox and webhook history without duplicate messages, notices, access
  grants, transitions, or remedies.
- Restore a backup, replay from durable watermarks, rebuild unread/in-app projections, reconcile
  provider outcomes, and verify future schedules/access revocations.
- Demonstrate that an operator can resolve a locked-out guest, bad template, unknown access outcome,
  overdue readiness task, and incident transfer using product tools without SQL.
- Run accessibility, localization, screen-reader, slow/offline network, and urgent-contact journey
  tests on supported clients.

## Caching, performance, and scaling

PostgreSQL remains authoritative. Safe caches include approved template artifacts, public/static
listing instruction fragments, participant projections with short TTL plus version, and derived
translations. Do not cache plaintext access secrets, confidential incidents, support elevations, or
authorization decisions beyond a request. Instruction/reveal responses use private/no-store policy.

Conversation reads use `(conversation_id, sequence)` keyset pagination and bounded page sizes.
Unread counts are rebuildable projections with source watermark; message history remains authority.
Avoid loading all messages/attachments as one Spring Data JDBC aggregate.

Workers claim bounded batches by state/due time with `SKIP LOCKED`, leases, jitter, quotas, and fair
partitioning across tenants/providers/severities. Per-recipient and per-conversation ordering is used
only where behavior requires it; global ordering is unnecessary. Backpressure protects mandatory
transactional and S0/S1 work from marketing or bulk host schedules.

Large content resides in object storage; relational rows store metadata/references. Apply upload,
render, translation, and evidence size/count limits. Precompute future schedules over a bounded
horizon and recompute on source changes rather than scanning all bookings each minute.

Hotspots include viral/bulk conversations, mass travel disruption, provider outage retries, arrival
peaks by local time, one large operator's task queue, and incident surges. Use rate/budget controls,
priority queues, circuit breakers, isolated worker pools, and operational degradation before adding
new infrastructure.

Split services or partition tables only after metrics show sustained contention, retention/region
requirements, independently scaling delivery/media workloads, or team/reliability ownership needs.
Kafka, search indexes, and specialized case platforms are optional projections/integrations, never
the conversation, notification-intent, access, or incident source of truth.

## Appropriate use of AI

Useful bounded applications include:

- drafting host/agent replies from explicitly authorized booking/listing facts;
- translating messages while retaining original and provenance;
- classifying spam, scams, harassment, off-platform payment, and sensitive-content risk;
- summarizing a long conversation/incident timeline with direct evidence links;
- recommending incident category, severity escalation, missing triage questions, queue priority, and
  response runbooks;
- extracting proposed operational tasks or structured fields for human confirmation;
- predicting notification channel/delivery risk or staffing volume within consent and fairness rules.

Prerequisites are purpose-approved training data, point-in-time-correct labels, lineage, access and
retention controls, representative language/market slices, adversarial testing, baseline rules,
human review capacity, versioned prompts/models, confidence calibration, cost/latency budgets,
monitoring, appeal, and kill switches. Private messages and incident data are not general-purpose
training data by default.

AI output is always labeled and stores input-evidence references, model/prompt version, output,
confidence, actor acceptance/edit, and final outcome where lawful. Retrieval is authorization-
filtered; user content is untrusted and cannot instruct tools or expand data access.

An AI/ML model must never be authoritative for:

- booking, payment, refund, pricing, tax, ledger, payout, or policy state;
- exact address or access-secret release, code generation/revocation, property readiness, check-in,
  no-show, or stay completion;
- emergency/safety disposition, account restriction, evidence truth, claim liability, or case closure;
- consent/legal classification, mandatory-notice basis, retention, or legal hold;
- sending a promise, exception, fee, refund, relocation, or unverified listing claim without an
  approved deterministic fact/template and required human/domain authorization.

Fallback is deterministic templates, manual translation/operations, fixed severity questions, and
human queues. Disabling every model must leave critical communication, access recovery, incident
intake, and authoritative state correct.

## Target-release dependencies and completion gates

All dependencies below are required for the supported Vietnam journeys. Channel/provider breadth is
limited to approved launch integrations, while failure and fallback behavior is complete.

### Dependency 0 — Decisions, threat model, and reference journeys

Agree first market/language/support hours, participant/delegation matrix, contact masking, message
retention/edit/delete/legal hold, notification classification/consent/quiet hours, exact-address and
instruction release, official check-in/out/time-zone policy, access fallback, incident severity/SLO/
safety ownership, and stay-outcome evidence policy. Produce ADRs, threat/data-flow model, template
fixtures, provider evaluation, reference journeys, and failure/recovery test vectors.

Exit: accountable owners approve boundaries and one end-to-end booking communication/access/incident
example is reproducible on paper with no contradictory authority.

### Dependency 1 — Booking conversation and durable in-app timeline

Add conversation, participant, immutable text message, sequence/idempotency, read position, basic
rate limits/reporting, authorization, audit, outbox/inbox, and in-app timeline. No attachments,
translation, AI, or pre-booking inquiry.

Exit: one confirmed booking creates one correctly scoped conversation; concurrent/retried sends and
revoked co-host access pass database/security tests; timeline rebuild succeeds.

### Dependency 2 — Transactional intent and approved delivery channels

Add versioned policy/template, typed render, intent, attempt, SMTP/provider adapter, retry/unknown,
bounce/complaint where supported, in-app notification, preferences for optional purposes, and basic
booking confirmation/change/cancellation reminders. Migrate authentication email only after shadow
equivalence and deduplication proof.

Exit: each reference fact creates exactly one logical intent; critical render/send failures are
visible/recoverable; provider outage and replay do not change booking truth or duplicate effects.

### Dependency 3 — Arrival instructions and manual access fallback

Add operational stay, versioned instruction sets, field classification/release, reveal audit,
host publication deadlines, guest arrival view, no-store responses, and one approved manual access
mode. Add deterministic check-in/out observations without automatic completion.

Exit: unauthorized/pre-release exact-address and secret tests fail closed; a confirmed guest can
retrieve released instructions during channel outage; missing/changed instructions create owned
exceptions.

### Dependency 4 — Readiness, tasks, and incident flow

Add turnover/readiness tasks, deadlines, basic host/staff assignment, incident intake, deterministic
S0–S4 floor, human routing, evidence links, SLO clocks, timeline, and controlled remedy requests.

Exit: locked-out/property-not-ready/safety reference incidents route within agreed objectives, retain
evidence, and request—never directly execute—booking/financial remedies.

### Dependency 5 — Access-provider integration and stay outcome

Integrate one smart-lock or property-access provider with provision/query/revoke, encrypted secret
reveal, unknown-outcome recovery, webhook inbox, reconciliation, manual fallback, and compromise
runbooks. Add policy-driven completion/no-show proposals and booking-owned final transition.

Exit: duplicate/reordered callbacks and cancellation races produce no usable invalid grant; provider
outage has tested fallback; completion and no-show require approved evidence and remain replayable.

### Dependency 6 — Required channels, media, Vietnamese localization, and integrations

Add push/SMS only with approved consent and preview policy, attachment quarantine/scanning, translation,
scheduled host messages, richer readiness/maintenance, and selected property-management integration.
Use provider failover only after stable intent/attempt semantics exist.

Exit: each new channel/integration passes contract, privacy, security, failure, cost, localization,
and operational acceptance; mandatory work is isolated from bulk traffic.

### Dependency 7 — Bounded AI and measured-scale controls

Shadow then assist with reply drafts, translation quality, moderation/risk triage, summaries, and
staffing/channel predictions. Add human review, appeals, drift/fairness monitoring, and kill switches.
Partition or extract delivery/media/operations only from measured evidence.

Exit: models beat deterministic/human baselines on approved metrics without safety, complaint,
fairness, privacy, latency, or support regressions; disabling them preserves all invariants.

## Verification checklist

### Functional and correctness

- [ ] Conversation scope, participant/delegation permissions, lifecycle, sequence, idempotency, edits,
  withdrawal, reads, and structured actions are explicit.
- [ ] Messaging, notification, stay operations, booking, cancellation, payment, finance, support,
  trust, claims, and review authorities do not overlap.
- [ ] Every notification intent derives from a committed fact under a versioned policy and template.
- [ ] Transactional/marketing classification, consent, preferences, quiet hours, locale, channel,
  fallback, expiry, retry, bounce, complaint, and reconciliation are covered.
- [ ] Exact address, instructions, access grants/secrets, release/revocation, and manual fallback are
  deterministic and auditable.
- [ ] Readiness, tasks, maintenance, check-in/out observations, outcome proposal, and final booking
  transition boundary are defined.
- [ ] Incident category/severity/SLO, safety route, evidence, transfer, remedy, relocation, and closure
  behavior are testable.
- [ ] Local dates, IANA zones, daylight-saving resolution, UTC instants, schedule supersession, and
  booking revision changes are reproducible.

### Concurrency and recovery

- [ ] Database uniqueness/guards are specified for messages, intents, provider events, grants, and
  remedies; optimistic transitions and lock order are documented.
- [ ] Duplicate/out-of-order events, client retries, worker crashes, unknown provider outcomes, stale
  schedules, cancellation/access races, and conflicting observations converge safely.
- [ ] No database transaction remains open over provider, storage, scan, translation, or device calls.
- [ ] Outbox/inbox replay, backup restore, provider reconciliation, and projection rebuild have
  acceptance tests and operational owners.
- [ ] Critical communication, access recovery, safety intake, and incident routing function under AI
  and optional-provider degradation.

### Security, privacy, and abuse

- [ ] Actor/resource/support/workload authorization and step-up/maker-checker controls are approved.
- [ ] Messages, attachments, contact details, exact address, access secrets, incident evidence,
  provider payloads, and notes have classification, encryption, redaction, retention, and audit.
- [ ] Contact masking, scam/off-platform payment, spam, harassment, malicious upload, phishing,
  enumeration, insider access, and secret-leak controls are tested.
- [ ] Consent withdrawal, preference, complaint/suppression, export, deletion, legal hold, moderation,
  appeal, and evidence-integrity behavior is defined per launch market.
- [ ] General logs, events, metrics, push/SMS previews, URLs, and caches contain no unnecessary
  sensitive content or access secret.

### Operations and completion

- [ ] Business/correctness/technical metrics, SLOs, alerts, dashboards, queues, runbooks, owners, and
  escalation paths exist before enabling each required capability.
- [ ] Template/policy/access/provider changes are versioned, reviewed, canaried where appropriate,
  reversible forward, and independently disableable.
- [ ] Existing authentication email migration avoids dual-send and preserves token secrecy.
- [ ] Support resolves reference failures through controlled product operations without SQL or
  editing immutable evidence.
- [ ] Target requirements and completion gates are explicit; optional smart-device/provider breadth
  and service extraction remain behind measured gates, while bounded AI assistance is verified.

## Decisions required before implementation

Record consequential choices as Architecture Decision Records (ADRs) with owner, date, context,
alternatives, decision, consequences, rollout, and revisit trigger. At minimum decide:

1. First market, languages/locales, user time-zone derivation, supported clients, operating/support
   hours, and accessibility commitments.
2. Required conversation scopes, inquiry-to-booking history behavior, travel-party participation, and
   whether hosts can initiate contact before confirmation.
3. Co-host/staff permission matrix, membership revocation semantics, support/moderator emergency
   access, purpose limits, step-up, and audit review.
4. Message editing/withdrawal/tombstone behavior, read receipts, retention, user export/deletion,
   evidence preservation, and legal-hold policy per content class.
5. Contact-detail and off-platform-payment policy by booking stage/market, intervention levels,
   user explanation, appeal, and safety-report bypass.
6. Attachment types/sizes, quarantine/scanning provider, metadata treatment, evidence originals,
   object region, signed-link lifetime, and failure fallback.
7. Translation languages/provider, disclosure, glossary, quality threshold, privacy/training terms,
   re-translation, and human escalation.
8. Notification purpose catalog and legal classification: mandatory transactional, optional
   transactional, and marketing in each launch jurisdiction.
9. Consent evidence, preference model, quiet hours, urgent exceptions, destination-change cutoff,
   bounce/complaint suppression, and critical-undeliverable handling.
10. Target transport/provider, provider-account/region strategy, idempotency/query/callback capability,
    retry/fallback/cost caps, data processing, retention, failover, and exit plan.
11. Template authoring, locale fallback, typed variable ownership, approval/maker-checker thresholds,
    activation/canary/rollback, and mass-replay authority.
12. Which reminders exist, their authoritative anchors, timing/expiry, supersession behavior, and
    whether channel delivery is merely best effort or operationally guaranteed.
13. Exact-address and instruction field taxonomy, release predicates/timing, acknowledged changes,
    offline availability, and behavior when payment/identity/risk facts are unavailable.
14. Official check-in/out wall times, booking time-zone snapshot/change rules, daylight-saving gap/
    overlap resolver, early/late/baggage/add-on ownership, and operational grace periods.
15. Access modes required for publication/booking, smart-lock build-versus-buy choice, credential
    generation, validity, reveal step-up, encryption/key ownership, rotation/revocation, provider
    reconciliation, and manual fallback.
16. Readiness task catalog, turnover buffers, assignee/staff model, evidence requirements, missed
    cutoff behavior, maintenance severity, and who can request emergency blocks.
17. What constitutes sufficient check-in/out evidence, who may attest, how conflicts are reviewed,
    and which signals are explicitly insufficient alone.
18. Stay completion/no-show policy, checkout grace, open incident/dispute gates, booking transition
    owner, review eligibility timing, and host-fund release dependency.
19. Incident category and S0–S4 definitions, deterministic severity floor, SLO clocks/pauses,
    ownership/on-call coverage, safety-specific route/copy, emergency-service boundary, and appeals.
20. Evidence taxonomy, custody/integrity, participant/internal visibility, retention/hold, selected
    message linking, support notes, and confidential safety treatment.
21. Remedy-request catalog and ownership for access fallback, inventory block, cancellation/refund,
    relocation/budget, payment/payout hold, trust restriction, claim, and communication.
22. Relocation service level, candidate constraints, inventory/quote requirement, expense authority,
    approval thresholds, and behavior when no replacement exists.
23. Scheduled host-message policy, approved templates/variables, prohibited content, cancellation/
    modification supersession, sender presentation, and automation kill switch.
24. AI/ML approved use cases, training/data-processing permission, evidence grounding, thresholds,
    human review, protected-language/market evaluation, audit, appeal, cost/latency limits, and kill
    switches.
25. Data residency, encryption/key management, privacy rights, lawful retention, legal hold, employee
    monitoring, regulator/law-enforcement request, and cross-border provider requirements.
26. Modular-monolith package ownership, shared outbox/inbox design, migration/backfill order,
    authentication-email cutover, capacity targets, SLOs, restore objectives, and evidence required
    before service extraction or partitioning.
