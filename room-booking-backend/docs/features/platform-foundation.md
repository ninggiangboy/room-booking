# Platform foundation

## Purpose

This document defines the target shared backend foundation for Room Booking. It expands
[D00 — Platform foundation](../marketplace-problem-breakdown.md#d00--platform-foundation) from the
marketplace master map into an implementation-oriented design.

The central question is:

> Which identifiers, time and money types, request and application programming interface (API)
> error contracts, transaction patterns, security boundaries, migration rules, audit records, and
> delivery primitives must every domain share so retries, concurrency, historical replay, and
> partial failure have one predictable meaning?

D00 owns reusable primitives and conformance rules. It does not own the product decision made with
those primitives. In particular:

- [availability and booking](availability-reservation-and-booking.md) owns listing-local inventory
  dates, claims, booking transitions, and the payment-versus-expiry race;
- [pricing and settlement](dynamic-pricing-and-settlement.md) owns price composition, rounding at
  allocation boundaries, quotes, and financial meaning;
- [payment orchestration](payment-orchestration.md) owns provider operations and payment-specific
  idempotency and recovery;
- [Vietnam market readiness](multi-market-compliance-and-localization.md) owns allowed markets,
  locales, currencies, policies, legal entities, and provider context;
- [data, experimentation, and machine learning (ML)](data-experimentation-and-ml-platform.md) owns
  event taxonomy and schema governance, analytical ingestion, lineage, metrics, experiments, and
  model artifacts;
- [identity, accounts, and access](identity-accounts-and-access.md) owns authenticated actors,
  sessions, and resource-scoped authority; governance owns privileged policy changes and
  overrides; and reliability owns journey-specific service-level objectives (SLOs), capacity, and
  disaster-recovery operation.

The foundation supplies consistent building blocks to those domains without becoming a generic
service that centralizes their business logic.

## Status and dependencies

This is a target design. The repository implements part of the foundation but not the complete D00
contract.

Current foundations are:

- Java 25, Spring Boot, Spring Data Java Database Connectivity (JDBC), PostgreSQL, and
  forward-ordered Liquibase changesets;
- PostgreSQL `pgcrypto`, `citext`, and `btree_gist` extensions from
  [migration 000](../data-model/000-platform.md), with PostGIS and `pg_trgm` added by migration 010;
- database-generated Universally Unique Identifier (UUID) primary keys, `timestamptz` event/audit
  columns, `date` stay dates, `bigint` minor-unit amounts, string statuses with check constraints,
  and optimistic `version` columns across the historical schema;
- a shared Coordinated Universal Time (UTC) `Clock`, Spring Data JDBC creation/modification
  auditing, and PostgreSQL-specific scalar conversion;
- one JavaScript Object Notation (JSON)
  [`ApiErrorResponse`](../../src/main/java/dev/ngb/backend/dto/ApiErrorResponse.java) shape and
  centralized exception translation;
- stateless bearer-token security, current account/role reloading, password encoding, and separate
  handlers for unauthenticated and forbidden requests;
- OpenAPI generation, a public basic health endpoint, and local PostgreSQL, object-storage, and mail
  dependencies; and
- in-process authentication email events handled after commit.

These are useful foundations, not proof of complete platform behavior. The repository does not yet
contain:

- canonical value types for money, country, currency, locale, Internet Assigned Numbers Authority
  (IANA) time zone, public reference, or request context;
- request/correlation propagation, structured logging fields, distributed trace conventions, or
  redaction enforcement;
- a durable command-idempotency registry with canonical request identity and replayed responses;
- a transactional outbox, general consumer inbox, event-envelope implementation, or replay tooling;
- an append-only cross-domain audit-event primitive;
- a registry and compatibility policy for API error codes and event schemas;
- production readiness/dependency health, metrics and alerts, backup/restore verification, or
  controlled recovery commands; or
- an automated expand/backfill/contract migration compatibility suite.

`ApplicationEventPublisher` with an after-commit listener is currently a best-effort email mechanism.
A process failure after commit can lose delivery, so it must not be reused for contractual,
inventory, financial, safety, or privacy-critical side effects.

D00 has no business-domain prerequisite, but its implementation depends on platform decisions about
identifier policy, deployment compatibility, event transport, secret/key management, telemetry,
retention, and operational ownership. Recommended dependency order:

1. Approve canonical types, request context, error envelope, and security/redaction baseline.
2. Add conformance libraries and tests without changing existing API semantics.
3. Add command idempotency, outbox, consumer inbox, and append-only audit primitives through a new
   forward migration.
4. Migrate one low-risk workflow to the durable patterns and prove retry/crash recovery.
5. Require the patterns for money, inventory, policy, safety, privacy, and external side effects.
6. Add migration compatibility, restore/reconciliation, and observability release gates.
7. Introduce a broker, separate platform service, partitioning, or multi-region coordination only
   after measured thresholds justify the additional failure modes.

No Java code, configuration, or migration is changed by this document.

## Goals

- Give every durable entity, public reference, external reference, command, event, and audit entry a
  stable identity with an explicitly defined scope.
- Make UTC instants, listing-local dates, IANA time zones, deadlines, and daylight-saving behavior
  unambiguous at API, Java, database, event, and display boundaries.
- Make invalid or mixed-currency arithmetic structurally difficult and all rounding explicit.
- Distinguish request diagnostics, tracing, causal lineage, command idempotency, and optimistic
  concurrency instead of overloading one identifier.
- Return stable, safe, machine-readable failures across controllers and security filters.
- Attribute protected and asynchronous actions to a verified human, service, provider, or system
  actor and the effective authorization context.
- Make a local transaction and its required asynchronous facts atomic through an outbox.
- Make command, worker, webhook, and event retries converge on the original result without duplicate
  business effects.
- Preserve append-only evidence for security-sensitive, privileged, contractual, and financial
  actions without logging secrets or unnecessary personal data.
- Evolve schema, API, and event contracts through compatible forward changes and observable
  backfills.
- Supply health, logs, metrics, traces, alerts, and recovery evidence proportional to the risk of a
  workflow.
- Keep the modular monolith straightforward until measured scale or ownership needs justify
  extraction.

## Non-goals

- Defining authentication/session lifecycle or resource-specific permissions;
  [D01](identity-accounts-and-access.md) owns them.
- Defining listing, inventory, quote, booking, payment, ledger, review, safety, support, policy, or
  model states.
- Treating a shared library, API gateway, event broker, cache, or analytics store as a business
  system of record.
- Requiring microservices, Kafka, event sourcing, Command Query Responsibility Segregation (CQRS),
  a service mesh, sharding, or multi-region writes for the target release.
- Inventing a generic workflow engine, rule engine, repository abstraction, or universal domain
  entity.
- Providing an administrative user interface, a standalone runbook, staffing plan, cloud topology,
  or vendor selection.
- Storing raw credentials, payment secrets, identity documents, access codes, or unrestricted
  personal data in shared context, logs, events, idempotency responses, or audit metadata.
- Converting historical timestamps, amounts, or identifiers by guessing missing context.
- Using a large language model (LLM) to generate identifiers, resolve time or currency, classify an
  authoritative error, grant access, approve a migration, or decide whether a side effect occurred.

## Core principles and invariants

### Shared primitives have one semantic definition

`Instant`, `LocalDate`, `ZoneId`, `Money`, `CurrencyCode`, `CountryCode`, `LocaleTag`, `RequestContext`,
`ActorContext`, and idempotency identity must have one canonical contract. A domain may add stricter
rules but cannot reinterpret the primitive.

Serialization, database mapping, validation, comparison, and test fixtures use the same definition.
Stringly typed copies of these concepts are accepted only at untrusted boundaries and are parsed
before business logic.

### Identity is stable, opaque, and separate from presentation

An internal entity identity is never derived from a name, email, phone number, provider identifier,
external calendar identifier, or human-readable booking code. Renaming, account correction,
provider migration, and localization cannot change internal identity.

A human-readable reference is a separately generated lookup key. It is not authentication evidence,
does not reveal sequential business volume, and does not replace resource authorization. External
provider identifiers are namespaced by provider account and resource type.

### Time has an explicit clock and calendar context

Events and deadlines use UTC instants. Stay nights and calendar restrictions use listing-local
dates. Converting between them requires an explicit IANA time zone and a documented boundary rule.
Neither the API server default time zone nor the caller's device time zone may supply missing domain
context.

The application shared `Clock` is the source of current time in business code. Database time may
set persistence metadata inside a transaction, but one decision must not silently mix several
independent notions of “now.”

### Money is an amount in minor units plus currency

No monetary type or calculation uses binary floating point. `amountMinor` without `currency` is not
a complete money value. Addition and comparison require identical currencies. Multiplication,
division, tax allocation, discount allocation, and foreign exchange use checked arithmetic and an
explicit rounding policy owned by the financial domain.

Locale controls rendering, never currency identity, scale, eligibility, or arithmetic.

### A request ID is not an idempotency key

A request identifier (ID) identifies one transport attempt for diagnosis. A trace ID links technical
work. A correlation ID links a business journey. A causation ID identifies the command or event that
caused new work. An idempotency key identifies one caller intent in a declared scope. An aggregate
version guards concurrent mutation.

These values may be related, but none substitutes for another. A retry normally has a new request
ID and trace while retaining the same command idempotency key and business correlation ID.

### Retried intent replays one outcome

For an idempotent command, the server owns a canonical projection of material input. The first
accepted use stores its hash, scope, actor/resource binding, state, and eventual result. The same key
and same material input returns the original result or safely continues incomplete work. The same
key with different material input fails with `IDEMPOTENCY_KEY_REUSED`.

Transport retries, worker retries, provider retries, webhook deduplication, outbox publication, and
consumer deduplication use separate namespaces. A random request ID alone never proves repeated
intent.

### Errors are stable contracts and disclose only safe context

Clients branch on a Hypertext Transfer Protocol (HTTP) status and stable domain error code, not
exception class, localized message, Structured Query Language (SQL) state, provider text, or stack
trace. Expected failures include safe structured data needed to correct or retry the request.
Unexpected failures return a generic message and an error or request reference; their cause remains
in protected telemetry.

Authentication and authorization failures reveal no more resource existence, account state, policy,
or risk evidence than the caller is entitled to know.

### Authorization follows the action across boundaries

Every protected command records the verified actor and effective authority used for the decision.
User-supplied actor IDs, roles, owner IDs, service names, or admin reasons are not trusted.
Asynchronous work carries minimal attribution and causation from the committed command; a worker's
service identity does not erase the initiating actor.

Each owning domain re-authorizes mutable or delayed operations when policy requires current
authority. A historical actor snapshot explains a past action but cannot grant future access.

### Transactions are short; required facts commit together

All relational changes that establish one local invariant, the command-idempotency outcome, the
audit evidence, and required outbox events commit in one short PostgreSQL transaction. Uncontrolled
network calls, user interaction, sleeps, and long computation occur outside database locks.

Application checks provide helpful errors; unique, foreign-key, check, exclusion, and locked-counter
constraints remain the final concurrent defense.

### Events describe committed facts

An event is a past-tense fact about committed domain state. It has immutable identity, schema
version, aggregate version, occurrence time, correlation and causation, provenance, and a minimal
payload. It is not a remote command disguised as an event and does not grant its consumer authority
to mutate another domain outside that domain's command contract.

Delivery is at least once. Producers use an outbox; consumers that cause durable effects use an
inbox or an equally strong domain idempotency defense. Analytics and search projections remain
rebuildable and non-authoritative.

### History is corrected additively

Confirmed contracts, financial facts, audit entries, published event versions, and accepted policy
references are not overwritten to match current state. Corrections use a new version, supersession,
reversal, adjustment, redaction marker, or replacement record with provenance.

Privacy erasure may remove or irreversibly transform personal content while preserving the minimum
non-personal integrity evidence permitted by approved policy. An audit trail is not an excuse to
retain unrestricted personal data forever.

### Schema and deployments evolve through compatible states

Applied Liquibase changesets are immutable. Schema evolution uses new forward changes and an
expand/backfill/switch/contract sequence when old and new application versions overlap. A deploy is
not considered safe because migration SQL succeeded; read/write compatibility, bounded backfill,
constraint validation, rollback behavior, and recovery are verified.

### Observability is evidence, not authority

Logs, traces, metrics, health checks, dashboards, and alerts explain runtime behavior but do not
decide booking, money, permission, or policy truth. Sensitive data is minimized and redacted before
export. Sampling must never discard required audit, financial, inventory, or reconciliation facts.

## Domain vocabulary

| Term | Definition |
| --- | --- |
| Entity ID | Stable UUID identity of one platform-owned entity; not meaningful to a user |
| Public reference | Opaque, human-readable support/confirmation reference distinct from entity ID |
| External reference | Identifier assigned by a provider, unique only within provider account and resource type |
| Request ID | Unique identifier for one inbound or outbound transport attempt |
| Trace ID | Identifier connecting technical spans for one execution path |
| Correlation ID | Stable identifier connecting commands and facts in one business journey |
| Causation ID | ID of the command/event directly responsible for a new fact |
| Idempotency key | Caller-chosen opaque key for one intent within a documented scope and retention window |
| Canonical request hash | Server-computed digest of the material command fields after validation and normalization |
| Aggregate version | Monotonic value used to detect stale concurrent writes |
| UTC instant | Absolute point on the timeline, represented in Java as `Instant` and PostgreSQL as `timestamptz` |
| Local date | Calendar date without an offset, represented as `LocalDate`/PostgreSQL `date` |
| IANA time zone | Named civil-time rule set, represented as `ZoneId`, used to interpret property-local time |
| Deadline | UTC instant after which a command is no longer eligible; equality is treated as expired unless a domain says otherwise |
| Money | Signed integer minor-unit amount paired with an International Organization for Standardization (ISO) 4217 currency code |
| Scale | Configured number of decimal digits used at a currency boundary; not inferred from locale |
| Actor context | Verified human/service/provider/system identity plus effective authorization evidence |
| Event envelope | Stable transport metadata surrounding a versioned domain payload |
| Outbox | Durable rows written with domain state and later published at least once |
| Inbox | Durable consumer receipt used to apply one event effect at most once per consumer contract |
| Audit event | Append-only evidence of a security-sensitive or consequential action and outcome |
| Projection | Rebuildable read model derived from authoritative facts |
| Expand/backfill/contract | Compatible schema evolution: add, populate, switch, then remove after old dependencies are gone |

## Ownership and source-of-truth matrix

| Fact or decision | Authoritative owner | D00 responsibility | Never authoritative |
| --- | --- | --- | --- |
| Human/service identity and current authority | Identity plus owning domain authorization | Verified `ActorContext` shape and propagation | Request body, forwarded role header, stale token claim alone |
| Entity identity | Owning domain's transactional row | UUID generation/validation convention | Name, email, public/provider reference |
| Human-readable reference | Owning domain record | Generator, normalization, collision contract | Reference format as proof of access |
| Current time for a decision | Owning command using shared clock | UTC clock and type contract | Client/device time, server default zone, provider time alone |
| Property-local calendar meaning | Property/inventory domain | `LocalDate`/IANA-zone conversion primitive | Offset-only value or guest locale |
| Monetary economic meaning | Pricing/tax/payment/finance owner | Exact `Money` type and checked arithmetic | Locale formatting, client total, floating point |
| Market/currency/locale eligibility | Market/policy governance | Structural canonical types | Shape validation alone or inferred country |
| Command result | Owning domain transaction | Scoped idempotency record/replay | Request ID, gateway retry state, analytics event |
| Domain fact | Owning aggregate | Durable event envelope/outbox | Broker acknowledgement, cache, search index |
| Consumer effect | Consuming domain transaction | Inbox/deduplication primitive | Delivery count or in-memory listener state |
| Consequential action evidence | Owning domain/governance audit policy | Append-only audit envelope/writer | Application log or trace sample |
| API error meaning | Owning domain plus error-contract registry | Stable envelope/status/disclosure rules | Exception text, provider code, SQL state |
| Event taxonomy and analytical meaning | D19 data governance | Transport envelope and durable delivery | Table-change feed without contract |
| Runtime diagnosis | D23/journey owner | Safe telemetry conventions | Metrics/logs as transactional state |

Actors using the foundation are guests, hosts/co-hosts, support/finance/risk/admin operators,
authenticated workloads, verified providers, and scheduled system jobs. Each actor type has a
separate authentication boundary and least-privilege capability. `SYSTEM` is a named workload and
causation context, not an anonymous superuser. A provider can assert only facts within its configured
account/capability contract. An operator can invoke only domain commands allowed by current scope,
limit, approval, and reason; D00 never grants business authority merely because a caller is internal.

## End-to-end flow

```text
Client/provider/worker
  -> API or consumer boundary
     validate shape and bounded size
     create request + trace context
     authenticate actor/provider
     parse canonical platform types
  -> owning domain command
     authorize actor/resource/action
     acquire scoped idempotency record
     load authoritative state and version
     resolve policy/time/money context
  -> one local transaction
     enforce domain + database invariants
     persist state/version
     complete idempotency result
     append audit evidence when required
     append outbox fact(s)
  -> commit
  -> response containing result/version/request ID

Outbox relay -> transport or local dispatcher -> consumer inbox
  -> consumer-owned transaction -> derived state or authorized domain command
  -> acknowledge only after durable completion

Failure anywhere -> retry original identity, query committed state, or quarantine;
                    never infer success from a timeout
```

The local transaction is the only atomic boundary. Publication and consumer handling form an
asynchronous saga. A producer does not wait inside its transaction for an event broker, email
provider, payment provider, object store, or another domain. A consumer that needs another domain to
change authoritative state invokes that domain's idempotent command instead of writing its tables.

## Identifier and reference design

### Entity identifiers

- New platform-owned aggregate and entity IDs use UUIDs and remain stable for the record's lifetime.
- The current PostgreSQL `gen_random_uuid()` default is an acceptable target-release generator.
  Callers may preallocate a UUID for aggregate construction, but a database default remains a final
  defense for SQL-created rows.
- UUID ordering is not business ordering. Pagination uses an explicit ordered tuple such as
  `(created_at, id)` and a signed/opaque cursor.
- Possession or unpredictability of an ID is not authorization. Every resource lookup applies the
  authenticated actor and resource policy.
- An ID is serialized in lowercase canonical UUID form. Invalid forms fail at the boundary and are
  never passed into repository queries.
- Entity type remains part of the contract. The same UUID value in two tables does not imply the
  same entity.

Time-sortable identifiers may be evaluated only after measured index locality or ingestion needs.
Changing the algorithm must not change UUID contract shape, equality, or historical IDs.

### Public references

Public references such as booking confirmation and support case codes:

- use a type-specific namespace and alphabet that avoids ambiguous characters;
- contain enough random entropy to prevent practical enumeration;
- may include a check character to detect transcription errors;
- are unique under a database constraint on normalized form;
- never encode user ID, date, amount, property, market volume, or sequential database position;
- can be replaced or aliased without changing the entity ID; and
- require normal authorization before protected detail is returned.

The exact prefix, length, alphabet, entropy, and checksum are a product/support decision. D00 owns
the generator and normalization contract after that decision; each domain owns when a reference is
issued and who may use it.

### External references

An external reference is stored with:

```text
provider family + provider account ID/version + resource type + external ID
```

The tuple is unique where the provider contract guarantees uniqueness. Provider IDs are treated as
opaque, size-bounded strings. They are not parsed for business meaning and do not replace the
platform aggregate ID. Raw provider evidence is retained only by the owning integration under its
security and retention policy.

## Time, date, and time-zone semantics

[Date, time, and time-zone handling](date-time-and-time-zone-handling.md) is the authoritative
reference for this section: it owns the runtime invariants, the shared calendar primitives, the
multi-zone query shape, and the failure cases they prevent. What follows is the summary every
other domain must satisfy.

### Canonical representations

| Meaning | Java | PostgreSQL | API/event |
| --- | --- | --- | --- |
| Event or decision time | `Instant` | `timestamptz` | UTC Request for Comments (RFC) 3339-style timestamp ending in `Z` |
| Stay/calendar date | `LocalDate` | `date` | `YYYY-MM-DD` |
| Property time zone | `ZoneId` | validated text/reference | canonical IANA zone ID |
| Local scheduled time | `LocalDateTime` plus `ZoneId` and resolution policy | separate local fields plus zone/version | local value, zone, and resulting instant |
| Duration | `Duration` for elapsed time | interval or integer seconds when constrained | documented unit or ISO duration |

Offset-only values do not identify a civil time zone. The stored property/listing IANA zone is the
authority for stay dates, local cutoff rules, and future local schedules. A guest locale or current
offset is presentation input only.

### Current time

- Business services receive the shared `Clock`; they do not call `Instant.now()`, `LocalDate.now()`,
  or the system default zone directly.
- One command captures `decisionInstant` once and passes it through policy evaluation, persistence,
  audit, and events.
- Database-generated `created_at` remains acceptable metadata. Where exact equality between the
  business decision and persisted record matters, the captured decision instant is written
  explicitly.
- Tests replace the clock with a fixed or controllable clock.
- Provider timestamps are evidence with provider provenance, not the platform receipt or decision
  time.

The current exception handler calls `Instant.now()` directly. Implementation should route it through
the shared clock so response time remains testable; this is a target gap, not an implemented change.

### Stay dates and daylight-saving behavior

Stay ranges remain half-open `[check_in, check_out)` in property-local dates. Every date in the range
is an inventory night; checkout is not. Date iteration occurs as local dates, not by repeatedly
adding 24 hours to an instant.

For a local scheduled timestamp that falls in a civil-time gap or overlap, the owning policy must
select one of these explicit behaviors:

| Condition | Required policy |
| --- | --- |
| Unique local offset | Resolve to that instant |
| Gap/nonexistent local time | Reject configuration or move according to a stored named rule |
| Overlap/two valid offsets | Require earlier/later-offset rule and retain the chosen offset |

Booking deadlines should preferably be derived from an elapsed duration and stored as an instant.
Rules stated as local civil times store the local value, IANA zone, resolution rule, resulting
instant, and relevant policy version so future time-zone database changes do not reinterpret the
historical decision.

### Interval semantics

- Effective configuration intervals are explicitly half-open `[effective_from, effective_until)`.
- A deadline check uses `decisionInstant >= expiresAt` as expired unless the owning domain documents
  a different comparison.
- Query cursors use a deterministic secondary ID when timestamps tie.
- Duration limits use one documented unit and checked conversion; ambiguous integer “timeout” fields
  are forbidden.
- Clock skew never permits a client or provider timestamp to extend an authoritative deadline.

## Country, locale, and currency primitives

- Country codes use uppercase ISO 3166-1 alpha-2 representation after strict boundary validation.
- Presentation locales use canonical Best Current Practice (BCP) 47 language tags such as `vi-VN`.
  A language-only field, where truly required, states its separate semantics.
- Currency codes use uppercase ISO 4217 alphabetic representation. Matching the three-letter shape
  is necessary but not sufficient; allowed currencies and effective scale come from approved
  market configuration.
- IANA zone IDs are validated against the application's installed time-zone rules and against the
  authoritative property/market context. Fixed offsets and short abbreviations are rejected where a
  property zone is required.
- Normalization is performed once at the boundary. The original user spelling may be retained only
  when presentation or evidence requires it.

Vietnam is the only active target market, but D00 must not hard-code `VN`, `vi-VN`, `VND`, or a
specific zone into generic value types. Market activation and allowed combinations belong to the
[market contract](multi-market-compliance-and-localization.md).

## Money and exact arithmetic

### Canonical money value

A canonical money value contains:

```text
Money(amountMinor: signed 64-bit integer, currency: CurrencyCode)
```

Negative values are allowed in the primitive because ledgers and adjustments need direction; each
domain constrains sign for its field. Database columns use `bigint` and a constrained currency code.
Any aggregate total that must balance also has database checks or transactional locked-counter
defenses.

### Arithmetic rules

- Add, subtract, compare, minimum, and maximum reject different currencies.
- Negation and absolute value detect the signed 64-bit minimum overflow case.
- Multiplication and allocation use checked intermediate arithmetic; overflow fails closed with a
  stable internal/domain error rather than wrapping.
- Division always names rounding mode and allocation owner. Remainders are distributed by a stable,
  documented order so replay returns the same line-level result.
- Percentage/rate values use exact decimal or rational representation with bounded scale; binary
  floating point is forbidden.
- A domain snapshots the rate, scale, rounding policy/version, input amount, output amount, and any
  remainder allocation when historical reproduction requires them.
- JSON amounts use integer minor units plus currency. Human decimal strings are accepted only at a
  boundary that has a known currency/scale and strict parser.

### Conversion and display

Currency conversion is not a method on `Money`. An approved foreign-exchange decision produces a
new money value plus rate, rate scale, source, timestamps, spread/fee, rounding, and policy
provenance. Locale-specific display is a presentation concern and cannot be parsed back as financial
authority.

The platform foundation validates structure and exact arithmetic. Pricing, tax, quote, ledger,
refund, and payout domains own economic meaning and permitted rounding points.

## Request, actor, and causal context

### Inbound request context

The API boundary creates an immutable context with at least:

| Field | Source and rule |
| --- | --- |
| `requestId` | Server-generated per transport attempt; a valid bounded caller value may be retained as `clientRequestId` |
| `traceId`/`spanId` | Telemetry layer; never accepted as authorization or idempotency |
| `correlationId` | Existing authorized journey or server-generated root; untrusted caller values are not blindly propagated |
| `causationId` | Current command ID or parent event ID |
| `actorType`/`actorId` | Authentication/provider/service boundary, never request JSON |
| `authContext` | Effective roles, organization/resource grants, session/service credential reference, and verification instant |
| `receivedAt` | Shared UTC clock captured at the boundary |
| `clientContext` | Bounded locale/device/channel metadata allowed by privacy policy; not authoritative domain state |

Context is explicitly passed into application entry points or exposed through a narrow request
context abstraction. Business services do not depend on raw servlet objects or thread-local state;
workers reconstruct context from verified event/job metadata.

### Outbound propagation

Outbound requests use a new request/attempt ID and retain approved correlation and causation fields.
They carry an integration-specific idempotency key when the provider supports it. Only allowlisted,
bounded metadata crosses a trust boundary. Internal actor roles, raw bearer tokens, cookies, network
addresses, and arbitrary inbound headers are not forwarded.

### Logging context

Every structured log can include safe forms of request, trace, correlation, actor type, operation,
resource type/ID, outcome, and duration. Values classified as credentials, session/token material,
payment data, private messages, evidence, exact location, identity documents, or arbitrary request
bodies are excluded or purposefully redacted.

## Validation and API contract conventions

### Boundary validation

The API boundary validates syntax, type, size, collection count, and basic range before invoking a
domain command. The domain then validates authorization, current state, policy, cross-field rules,
and invariants. The database/provider boundary remains the last defense.

Unknown JSON fields follow one documented compatibility policy. Enumerated commands fail on unknown
values; response readers must tolerate additive fields. Blank, absent, and explicit `null` are not
silently conflated when update semantics differ.

### Version and concurrency fields

Mutable resource responses expose an opaque version or entity tag when clients can submit edits.
Commands carry the expected version using the documented body field or conditional header. A stale
version returns `RESOURCE_VERSION_CONFLICT` with safe current-version/reload guidance. Blind retry is
allowed only if the command contract is commutative or the client reloads authoritative state.

### Pagination

Collection APIs use bounded page size and opaque cursor pagination for large or changing datasets.
The cursor commits to filter/sort version and last ordered tuple. It is integrity-protected and does
not contain sensitive plaintext. Offset pagination is limited to small, stable administrative data
where drift and cost are accepted.

## API behavior

D00 does not require a public “platform” controller. Guest, host, admin, provider, and internal APIs
apply the same boundary contracts in their owning domain.

Illustrative request:

```http
POST /api/v1/bookings
Authorization: Bearer <access-token>
Idempotency-Key: <opaque-command-key>
X-Correlation-Id: <authorized-existing-journey-or-new-client-hint>
Content-Type: application/json
```

Illustrative successful response behavior:

- return the owning domain's status and representation;
- echo the server request ID in a response header;
- include resource ID/public reference and version where applicable;
- return the same semantic result for a valid idempotent replay, with a replay indicator header if
  operators and clients need it; and
- never expose database row shape, internal exception name, secret, or provider-native error.

Administrative recovery or replay commands require scoped authority, reason, target identity,
idempotency key, expected version where applicable, and maker-checker approval for configured high-
impact actions. Read-only health cannot expose dependency credentials, topology, or sensitive
failure detail to unauthenticated callers.

### Error semantics

The target error envelope extends the current shape additively:

```json
{
  "timestamp": "2030-01-02T03:04:05Z",
  "status": 409,
  "code": "IDEMPOTENCY_KEY_REUSED",
  "message": "The idempotency key was already used for different input.",
  "data": {
    "operation": "CREATE_BOOKING"
  },
  "path": "/api/v1/bookings",
  "requestId": "2e3ae80a-2f57-4be5-bbae-8eec34ac6319"
}
```

`message` may be localized at a presentation boundary; `code`, `status`, and safe data semantics are
stable. Field validation uses a bounded list of field paths and codes rather than rejected secret
values.

| Condition | HTTP behavior | Stable code example | Retry behavior |
| --- | --- | --- | --- |
| Malformed shape/type | `400` | `VALIDATION_ERROR` | Correct request |
| Auth missing/invalid | `401` | `UNAUTHORIZED` | Reauthenticate; do not reveal resource |
| Auth valid, action forbidden | `403` | `FORBIDDEN` | Do not retry unchanged |
| Authorized resource absent | `404` | domain `*_NOT_FOUND` | Correct reference; enumeration-safe |
| State/version/idempotency conflict | `409` | `RESOURCE_VERSION_CONFLICT` | Reload or replay original intent |
| Semantically ineligible input | `422` when adopted consistently | domain-specific | Correct business input |
| Rate/capacity limit | `429` | `RATE_LIMITED` | Honor safe `Retry-After` |
| Dependency unavailable before effect | `503` | `DEPENDENCY_UNAVAILABLE` | Bounded retry with same idempotency key |
| Known processing timeout/unknown outcome | `202`, `409`, or `503` per command | `OUTCOME_PENDING` | Query/replay same intent; never replace blindly |
| Unexpected server failure | `500` | `INTERNAL_SERVER_ERROR` | Retry only when command is idempotent |

The owning domain registers each public code, status, data schema, retry classification, disclosure
class, and deprecation plan. One code cannot acquire different meanings in different endpoints.
Provider codes and SQL states are mapped to internal diagnostics and safe domain outcomes.

## Command idempotency

### Scope and canonical request identity

Every idempotent command declares:

```text
scope = actor/tenant + operation type + owning resource or creation namespace + idempotency key
request identity = hash(canonical material input + contract version)
```

Canonical material input is a typed server projection after syntax normalization. It distinguishes
absent from explicit null where meaningful, sorts unordered sets, preserves ordered lists, excludes
diagnostic metadata, and includes authoritative references/version supplied by the intent. It does
not hash arbitrary raw JSON whose whitespace or field order can differ.

Keys are opaque, size bounded, and stored as a protected digest where retaining plaintext creates
unnecessary disclosure. Retention is at least the maximum client/provider retry and business dispute
window for the command, then follows the owning domain's policy. Expiry never permits a duplicate
financial or contractual effect: durable business uniqueness remains after the generic replay record
expires.

### State machine

```text
ABSENT -> IN_PROGRESS -> SUCCEEDED
                     -> FAILED_FINAL
                     -> OUTCOME_PENDING -> SUCCEEDED | FAILED_FINAL
IN_PROGRESS --expired lease/recovery--> IN_PROGRESS
```

- `IN_PROGRESS` has a bounded owner lease/heartbeat and durable command identity.
- A concurrent identical caller observes/polls or receives `OUTCOME_PENDING`; it does not execute the
  mutation again.
- `SUCCEEDED` stores the created resource and a safe response projection/version.
- `FAILED_FINAL` may replay a deterministic business failure when policy requires stable behavior.
- Transient failures before any effect can release/retry the record; unknown outcomes remain pending
  until authoritative recovery proves the result.
- Lease expiry permits recovery, not assumption that no effect occurred.

### Transaction pattern

For a purely local command, acquiring the idempotency record, writing domain state, recording audit,
appending outbox facts, and storing the result occur in one transaction. If a failure rolls the
transaction back, all roll back.

For an external effect, the first transaction creates the domain instruction/operation and outbox
row. A worker submits it with the provider-specific key after commit. The final result transaction
records verified provider evidence and advances the instruction. Unknown results are queried before
resubmission.

Database unique constraints on business identities remain mandatory. Generic idempotency cannot by
itself prevent two different keys from attempting the same booking confirmation, journal posting,
refund, or provider operation.

## Conceptual data model

### Current foundation

Migration `000-platform.sql` installs extensions only. Later historical migrations independently use
UUID defaults, UTC timestamps, version columns, status checks, unique keys, and monetary checks.
There is no shared D00 table for command idempotency, outbox/inbox, external references, or audit.

### Proposed shared records

#### `command_idempotency_records`

- `id`, scope type/key, operation, key digest, canonical request hash, contract version;
- actor type/ID and owning resource type/ID where known;
- state, response status/resource/reference/safe projection, failure code;
- lease owner/expiry, first request/correlation IDs, created/completed/retain-until timestamps; and
- unique `(scope_type, scope_key, operation, key_digest)` plus indexes for lease recovery and expiry.

The record contains no bearer token, raw credential, arbitrary request body, or unrestricted
response. A domain may use a specialized table when its retention, uniqueness, or result shape is
stronger than the generic primitive.

#### `outbox_events`

- immutable event ID, event name, schema version, aggregate type/ID/version;
- occurred/recorded time, market where applicable, correlation ID, causation ID;
- initiating actor type/ID or approved pseudonymous reference;
- content type, sensitivity class, payload, and optional deterministic business deduplication key;
- publication state, available time, attempt count, lease owner/expiry, published time, and safe last
  error classification; and
- indexes for ready/expired-lease claims and aggregate diagnostics.

Payload is immutable after commit. Mutable publication metadata is operational state, not event
meaning. The event taxonomy, payload ownership, schema registry, analytics contract, and retention
are governed by D19.

#### `consumer_inbox_receipts`

- consumer name/version, event ID/schema version, first-seen time;
- processing state, attempt/lease metadata, completed time, effect reference, and failure class; and
- primary/unique key `(consumer_name, event_id)`.

The consumer inserts/completes its receipt in the same transaction as its durable effect. Handler
version changes do not replay past effects accidentally; explicit rebuild consumers use a distinct
consumer identity and suppress external side effects.

#### `audit_events`

- immutable audit ID/time, action, target type/ID, outcome, reason code;
- verified actor type/ID, delegated organization/role/permission context, approver where applicable;
- request/correlation/causation IDs, owning domain, market/policy references where applicable;
- minimal structured before/after references or digests, retention class, and integrity metadata.

Normal application roles cannot update or delete audit rows. D00 owns the append-only envelope and
writer contract; the owning domain defines audited actions and evidence; D21 defines privileged
review, override, maker-checker, and break-glass policy; D22 defines retention, erasure, and legal
hold.

#### `external_resource_references`

Use a shared registry only where several domains truly need provider-to-platform resolution.
Otherwise keep the tuple in the owning integration table. Any shared form includes provider account
identity/version, resource type, external ID, internal aggregate type/ID, lifecycle, and uniqueness
constraints without provider secrets.

### Constraints and indexes

- State/status values have check constraints and timestamp/state shape checks.
- Attempts, versions, amounts, lengths, and lease values have bounded checks.
- Idempotency and external-reference scopes have database uniqueness.
- Outbox claims use an index on `(state, available_at)` and lease expiry; consumers claim bounded
  batches using skip-locked semantics.
- Audit indexes support target timeline, actor timeline, correlation, and time-bounded retention
  queries without indexing sensitive payload text.
- Foreign keys are used when records share the same lifecycle and database boundary. Cross-domain
  historical references may intentionally avoid cascading deletion and retain type/ID snapshots.
- JSON is limited to immutable versioned payload/evidence or bounded safe response projections;
  searchable policy, state, ownership, and scheduling fields stay relational.

### Migration, backfill, and deployment

Implementation requires new forward Liquibase changesets; applied files `000` through `010` remain
unchanged.

Recommended sequence:

1. Add shared tables and nullable/additive request/audit context where needed.
2. Deploy writers capable of both old behavior and new durable records behind a kill switch.
3. Backfill only derivable identifiers/context in bounded, checkpointed batches; mark missing
   provenance as explicit legacy/unknown instead of guessing.
4. Verify counts, uniqueness, event/result reconciliation, redaction, and query performance.
5. Switch consumers and critical workflows to the durable path.
6. Validate new not-null/check/foreign-key constraints after data is clean.
7. Remove obsolete fields or best-effort paths only in a later changeset after every supported
   application version has stopped reading/writing them.

Large backfills do not run as one unbounded startup transaction. Deployment rollback disables new
commands or returns the application to a compatible reader/writer; it never reverses already
committed business facts or drops populated schema. A corrective forward migration repairs a bad
applied migration.

## Service boundaries

These are logical modules/interfaces inside the modular monolith, not required network services.

### `IdentifierFactory` and `PublicReferenceService`

Generate/validate canonical UUIDs and approved type-specific public references. Domains own issuance
time and authorization. The service does not look up arbitrary domain records.

### `PlatformClock` and `CalendarContext`

Expose one injectable UTC clock and strict conversions requiring local date, IANA zone, and
resolution policy. Domain services own business deadlines and stay/calendar rules.

### `Money` and `ExactAllocation`

Provide checked same-currency arithmetic and deterministic remainder allocation primitives. Pricing,
tax, finance, and refund domains supply the economic policy and recorded rounding version.

### `RequestContextFactory`

Creates trusted request/trace/correlation/actor context at HTTP, provider, scheduled-job, and event
boundaries; supplies safe propagation and logging fields.

### `IdempotentCommandExecutor`

Acquires a scoped record, verifies canonical input identity, coordinates the local transaction, and
replays a bounded safe result. Domain commands provide scope, material-input projection, retention,
business uniqueness, and result serializer.

### `OutboxAppender` and `OutboxRelay`

Append committed domain facts inside the producer transaction and publish bounded leased batches
after commit. Transport adapters are replaceable. D19 owns the registered event contract and
analytical routing.

### `InboxExecutor`

Deduplicates one consumer/event identity and commits the receipt with the consumer's effect. It
supports quarantine and explicit side-effect-free rebuild identities.

### `AuditRecorder`

Validates the append-only audit envelope and sensitivity policy and writes it inside the command
transaction. It cannot decide whether an action is authorized or approve its own evidence.

### `ErrorContractRegistry` and API exception mapping

Ensure each public error code has one status, disclosure class, data schema, retry class, and owner.
The registry may be code/generated documentation rather than a runtime database table.

### `SecretProvider` and cryptographic boundary

Resolve versioned secret/key handles from the deployment environment or secret manager. Domain and
provider code receives the narrow operation it needs rather than broadly exporting raw secret
material. Rotation and dual-read/single-write behavior are explicit.

### `TelemetryFacade`

Emits structured logs, metrics, and spans through redaction and cardinality controls. Required audit
and domain facts bypass telemetry sampling and remain durable in their owning stores.

## Event contracts

### Canonical envelope

Every durable domain event contains:

```json
{
  "eventId": "uuid",
  "eventName": "BookingConfirmed",
  "schemaVersion": 1,
  "occurredAt": "2030-01-02T03:04:05Z",
  "recordedAt": "2030-01-02T03:04:05Z",
  "producer": "booking",
  "aggregateType": "booking",
  "aggregateId": "uuid",
  "aggregateVersion": 7,
  "marketCode": "VN",
  "correlationId": "uuid",
  "causationId": "uuid",
  "actor": {
    "type": "USER",
    "id": "uuid"
  },
  "payload": {}
}
```

Optional context is omitted rather than populated with invented values. Sensitive actor/payload
fields may use approved pseudonymous references or be absent. Event metadata does not authorize its
holder to fetch protected data.

### Naming, versioning, and compatibility

- Names are committed past-tense business facts, not table-change names or commands.
- Schema version is an integer scoped to event name. Additive optional fields preserve a version
  only when consumer contract permits; removal, changed meaning/type, or tighter assumptions require
  a new version.
- Producers retain conformance fixtures; consumers explicitly declare supported versions and
  quarantine unknown incompatible input.
- Event ID is stable across publication retries. A corrected fact has a new event ID and explicit
  correction/supersession relation.
- Aggregate version supports ordering diagnostics but transport order is not assumed.
- Payload contains identities and minimum committed facts. Consumers fetch current protected detail
  only through authorized APIs and must tolerate that it changed or was erased.

### Outbox publication

The relay claims a bounded ready batch using a lease, publishes with event ID as transport
deduplication metadata, then records success. A crash before success marking can republish. A crash
before publish lets the lease expire. Both are expected at-least-once paths.

Retry uses capped exponential backoff plus jitter, with error classification. Poison or permanently
unsupported records enter quarantine/dead-letter state with alert, evidence, and an authorized
replay command. Deleting an event to clear lag is forbidden.

### Consumer application

Consumers assume duplicate, delayed, and out-of-order delivery. They compare authoritative aggregate
version/state where ordering matters and use one of:

- inbox receipt plus effect in one transaction;
- a domain uniqueness constraint keyed by source event/business fact; or
- an idempotent command into the owning domain.

Acknowledgement occurs only after durable effect. Replay/rebuild uses a declared range, consumer
identity/version, reason, dry-run or side-effect suppression, approval, progress evidence, and
reconciliation. Search, analytics, and model rebuilds cannot call external money or notification
effects.

## Concurrency and idempotency

### Locking hierarchy

Each domain documents its aggregate-specific locks. Shared rules are:

1. acquire the command idempotency scope before executing the same intent;
2. lock owning aggregate roots/resources in stable type and ID order;
3. lock dependent counters/dates in deterministic order;
4. append audit/outbox rows without acquiring unrelated aggregate locks; and
5. commit before external calls.

Transactions do not lock a broad user, listing, or global platform row merely to serialize
unrelated work. Deadlocks are treated as retryable only when the whole command is idempotent and the
retry is bounded.

### Optimistic concurrency

`version` begins at zero and advances on each semantic mutation. A version conflict is a business-
visible stale-write outcome, not an unconditional retry. Internal workers may reload and retry when
their operation is proven commutative or their transition function revalidates current state.

Version is not an event sequence shared across aggregates. Immutable child/snapshot records use
their own identity or parent version rather than a meaningless mutable counter.

### Race outcomes

| Race | Winner and defense |
| --- | --- |
| Same key, same request | First command executes; others replay/wait on its record |
| Same key, different request | Existing identity wins; later request gets `IDEMPOTENCY_KEY_REUSED` |
| Different keys, same business effect | Domain unique/check/lock invariant permits at most one |
| Worker lease expires during slow work | Old/new owner reconcile instruction/effect identity before action |
| Event delivered twice | Inbox/business uniqueness applies one durable consumer effect |
| Events arrive out of order | Consumer state machine/version rules apply, defer, or quarantine |
| Deploy overlaps old/new writers | Expand/dual-compatible schema and contract version prevent ambiguity |
| Timeout after commit | Client replays same key or queries created resource; server returns committed result |

### Final database defenses

The foundation requires, but does not replace, domain constraints. Examples include unique normalized
email, booking overlap exclusion, pooled inventory checks under locks, cumulative payment/refund
ceilings, one ledger posting per business fact, and one review right per booking/direction. An
application-level “check then insert” without a database defense is incomplete for a contested
invariant.

## Security, privacy, and access control

### Trust boundaries

| Boundary | Required controls |
| --- | --- |
| Public API | Authentication where required, validation, resource authorization, rate/abuse control, safe errors |
| Admin/operations | Scoped permissions, step-up/maker-checker where configured, reason, audit, expiry |
| Provider webhook | Raw-body size limit, provider/account routing, signature/freshness/replay verification, inbox |
| Worker/event consumer | Workload identity, least privilege, supported schema, inbox, bounded retry |
| Database | Separate least-privilege application/migration/operations roles and protected credentials |
| Object/analytics/telemetry export | Classification, purpose, encryption, retention, access audit, deletion propagation |

Role checks alone are insufficient. Domain commands authorize the actor against the resource,
organization, market, current account state, and operation. Internal service identity is not blanket
admin authority.

### Secrets and encryption

- Production secrets come from an approved secret manager or deployment injection, never committed
  defaults, database columns, logs, events, URLs, or exception messages.
- Secret references include purpose/version; rotation supports overlapping verification where
  necessary and single-current use for new signing/encryption.
- Passwords use the configured adaptive password encoder; opaque high-entropy tokens are stored as
  hashes when lookup/verification does not require recovery.
- Transport and storage encryption are platform requirements, but field-level protection is chosen
  by data classification and access need rather than applied as undocumented ad hoc crypto.
- Key loss, compromise, rotation failure, and revoked provider credentials have fail-closed behavior,
  alerting, and recovery evidence.

### Logging, error, event, and audit minimization

Allowlist fields instead of applying best-effort regex redaction to arbitrary bodies. Never emit raw
passwords, bearer/refresh/reset/verification tokens, signing keys, provider secrets, card/bank data,
access codes, private messages, unrestricted evidence, identity documents, or exact protected
addresses.

Email, phone, network, device, and user-agent data are personal data and are hashed, truncated,
tokenized, or omitted according to use and approved policy. High-cardinality personal identifiers do
not become metric labels.

### Audit integrity and access

Audit writers append only and cannot approve their own privileged action. Reads are purpose-scoped
and themselves audited for sensitive exports. Corrections append a superseding event. Retention and
privacy transformation are policy-driven, observable, and do not permit a general administrator to
silently erase evidence.

### Abuse controls

Request size/count limits, rate limits, cursor limits, idempotency scope quotas, expensive-query
budgets, upload validation, and worker batch bounds prevent platform primitives from becoming denial-
of-service amplifiers. Error and reference formats resist enumeration but never substitute for
authorization.

## Observability and operations

### Signals

Required platform metrics include:

- requests by route template/method/status class, latency, cancellation, and active count;
- domain error code rate and unexpected error rate without unbounded labels;
- authentication/authorization denial class and rate-limit action without sensitive actor labels;
- database pool use, transaction duration, rollback, lock wait, deadlock, slow query, and migration
  status;
- idempotency acquisition, replay, key mismatch, pending age, lease recovery, and duplicate-effect
  invariant violations;
- outbox ready count/oldest age, claim/publish latency, attempts, lease expiry, quarantine, and event
  name/schema rejection;
- inbox processing latency, duplicate rate, pending age, retry, quarantine, and handler version;
- audit append failure, unauthorized audit read/export, and retention backlog;
- dependency latency/error/circuit state and provider unknown-outcome backlog; and
- Java Virtual Machine (JVM)/runtime saturation, worker queue depth, resource use, restart, and
  build/deploy version.

Correctness counters such as duplicate contractual effects, unbalanced money, overbooked inventory,
and missing required audit/outbox records have a target of zero and page/escalate according to the
owning domain. Metrics do not replace reconciliation queries.

### Structured logs and traces

Logs are machine-parseable and use route templates and stable operation/error names. Expected client
failures are not logged as stack traces. Unexpected and dependency failures include safe causal
context and an error ID. Trace sampling may be increased on error, but trace payload remains
redacted.

One business journey can be followed from inbound request through transaction, outbox event,
consumer, and provider attempt using correlation/causation while still distinguishing each request
and span.

### Health

- Liveness answers whether the process should be restarted and does not depend on every remote
  provider.
- Readiness answers whether this instance can safely accept the class of traffic it serves.
- Dependency detail is authenticated/internal; the public health response is minimal.
- A non-critical dependency may produce explicit degraded mode. A missing authority needed for
  booking, money, policy, or permission fails that operation closed rather than declaring fabricated
  readiness.

### SLO candidates and alerts

Journey owners set numeric targets and error budgets. D00 supplies consistent measurement for API
availability/latency, database transaction health, idempotency convergence, event-delivery lag,
consumer recovery, and audit durability. Initial release gates must define at least:

- accepted request success and latency by critical journey;
- maximum normal and alerting age for required outbox/inbox work;
- maximum recovery time for a stuck lease, poison event, and unknown external outcome;
- zero tolerated duplicate money/inventory/contract effects and missing required audit facts;
- migration duration/lock budget and backfill throughput/error threshold; and
- backup recovery point objective (RPO), recovery time objective (RTO), and reconciliation time.

No numeric value is silently invented in a library. Each value has owner, evidence, review date, and
degradation/stop response.

### Authorized recovery commands

Backend operations require read-only inspection and authorized commands to:

- inspect one idempotency intent and return/continue its known result;
- release or reclaim an expired work lease after effect reconciliation;
- retry or quarantine a bounded outbox/inbox range;
- replay a compatible event range to a named side-effect-safe consumer;
- rotate/revoke a secret version and inspect dependent configuration readiness;
- pause a producer/consumer/integration with a reason and expiry;
- validate/backfill a migration batch and compare checkpoints; and
- restore a backup into an isolated environment and run domain reconciliation.

Commands record actor, reason, scope, dry-run/approval, idempotency, result, and audit evidence. Direct
database edits are not a recovery interface.

## Failure behavior

| Failure | Required behavior | Recovery/evidence |
| --- | --- | --- |
| Invalid ID/time/currency/locale | Reject before domain mutation with stable safe code | Validation metrics; no retry unchanged |
| Missing property time zone or market context | Fail closed for consequential decision | Correct authoritative configuration; audit if override attempted |
| Same idempotency key, different input | Reject conflict; never execute second intent | Return operation/scope only, not original sensitive body |
| Client disconnect after commit | Preserve result; do not compensate solely for disconnect | Replay same key or query authorized resource |
| Process crash during local transaction | PostgreSQL commits all or none | Retry original idempotent command |
| Process crash after commit before publish | Domain state and outbox remain durable | Relay claims ready event after restart |
| Relay crash after publish before marking | Event may be delivered again | Same event ID; consumer inbox/business dedupe |
| Consumer crash before commit | No effect/receipt commits | Redelivery applies once |
| Consumer crash after commit before ack | Effect and receipt already committed | Redelivery observes completed receipt |
| Unsupported event schema | Do not guess or discard | Quarantine, alert owner, deploy compatible handler, authorized replay |
| Out-of-order event | Apply only if transition/version permits | Defer, query authority, or quarantine with evidence |
| Database unavailable | Reject/read-degrade only where safe; do not accept unaudited mutation | Bounded retry, readiness change, incident evidence |
| Lock timeout/deadlock | Roll back complete transaction | Bounded full-command retry only with same idempotency identity |
| Telemetry exporter unavailable | Business transaction continues if safe; buffer/drop telemetry by policy | Local counters/log signal; telemetry never blocks critical locks |
| Audit append fails for required action | Roll back/deny the consequential action | Repair audit dependency; never proceed invisibly |
| Outbox append fails for required fact | Roll back producer transaction | Retry original command |
| Secret unavailable/expired | Fail closed for protected/provider operation | Alert, rotate/restore approved version; no plaintext fallback |
| Migration partially fails | Liquibase records actual state; application stays on compatible version | Diagnose, apply corrective forward changeset, verify upgrade path |
| Backfill produces mismatch | Stop checkpoint; do not activate new read path | Investigate, repair deterministically, rerun reconciliation |
| Clock/time-zone rule changes | Historical stored instants/snapshots remain unchanged | New future decisions use approved current rules/version |
| Backup restore succeeds but events differ | Do not declare recovery complete | Replay/reconcile by domain before traffic activation |

## Testing and verification

### Deterministic and property tests

- UUID/reference validation, collision handling, normalization, transcription checksum, and no
  embedded business data.
- Fixed-clock deadline equality, before/after boundaries, leap day, month/year boundary, and negative
  durations.
- IANA zone conversion for unique, gap, and overlap local times; stay-date iteration never assumes
  24-hour days.
- Money same/different-currency operations, overflow, signed-minimum edge, exact allocation,
  remainder ordering, and deterministic replay.
- Country/currency/locale/time-zone canonicalization and invalid/unsupported distinctions.
- Canonical request hashing under reordered object fields, unordered sets, ordered lists,
  null/absent differences, Unicode normalization policy, and contract versions.
- Error-code registry uniqueness, safe-data schema, status, retry class, and OpenAPI examples.

### Real-database and migration tests

- Empty-database application of all Liquibase changesets and upgrade from the latest representative
  production snapshot.
- UUID defaults, check/unique/foreign-key/exclusion constraints, UTC persistence, optimistic versions,
  and transaction rollback.
- Concurrent acquisition of the same/different idempotency intent and lease-expiry recovery.
- Atomic domain state/idempotency/audit/outbox commit under injected failure at every write boundary.
- Concurrent outbox claims skip locked work without loss; lease expiry and duplicate publish are
  safe.
- Consumer receipt and durable effect commit together.
- Expand/backfill/switch/contract compatibility with old/new readers and writers, restartable batch
  checkpoints, constraint validation, and rollback-to-compatible-application behavior.

### Concurrency and failure injection

- Many identical requests with one key create one result; different payload with the same key never
  mutates state.
- Different keys racing for one unique business effect resolve through the domain/database defense.
- Deadlock, lock timeout, connection loss before/after commit, worker termination, duplicate event,
  delayed event, out-of-order event, poison payload, and unavailable transport.
- External timeout before/after provider-side effect uses query/reconciliation and never blind
  replacement.
- Telemetry, audit, and outbox failures verify their distinct blocking/degradation policies.

### Security and privacy tests

- Unauthenticated, wrong-role, cross-user, cross-organization, stale-account, service, and provider
  authorization boundaries.
- Header spoofing cannot choose actor, role, internal correlation trust, client address, or service
  identity.
- Logs, traces, metrics, error bodies, events, audit, and idempotency projections contain no seeded
  secret/private markers.
- Secret rotation overlap/revocation, webhook replay/signature checks, audit append-only permissions,
  protected health detail, export auditing, and retention/deletion transformation.
- Reference and error behavior do not expose resource existence beyond policy.

### API, event, and recovery contracts

- Error envelopes from controllers and Spring Security handlers match the same schema and request ID.
- OpenAPI documents idempotency, version, retry, error, and pagination behavior.
- Producer/consumer contract fixtures cover every supported event version and unknown-version
  quarantine.
- Replay is bounded, approved, checkpointed, observable, and incapable of external side effects
  unless the explicit recovery action authorizes them.
- Backup restore is followed by outbox/inbox, identity, inventory, booking, payment, ledger, and audit
  reconciliation before acceptance.

## Caching, performance, and scaling

Platform value types, authorization facts required for a transaction, idempotency state, audit,
outbox, and inbox remain authoritative in PostgreSQL for the target release. A cache may hold public
configuration or a projection only with owner, key/version, time to live (TTL), invalidation,
stampede control, maximum staleness, and safe fallback.

Critical query shapes are:

- point lookup/unique acquisition of scoped idempotency identity;
- oldest ready outbox work and expired leases in bounded batches;
- consumer/event receipt lookup;
- target/actor/correlation/time-bounded audit retrieval; and
- checkpointed retention/backfill scans.

Batch size, transaction duration, payload size, attempt count, and per-scope outstanding work are
bounded. Payloads larger than the approved event limit use an authorized immutable object reference
with digest and retention; consumers do not receive arbitrary database snapshots.

Hotspot mitigation begins with measured query/lock evidence, narrower aggregate ownership, indexes,
bounded leases, and workload isolation. Table partitioning is considered when retention/index/vacuum
measurements show a sustained need. A dedicated broker is considered when PostgreSQL relay
throughput, fan-out, latency, retention, or isolation misses approved SLOs despite tuning. Service
extraction requires an accountable owner, versioned remote contract, independent SLO, and evidence
that the operational benefit exceeds new consistency and recovery cost.

Multi-region writes, global ID coordination beyond UUIDs, cross-region consensus, sharding, event
sourcing, and CQRS are measured-scale capabilities, not target-release defaults.

## Appropriate use of AI

Artificial intelligence (AI) has no authoritative role in D00 primitives. Deterministic code,
database constraints, approved configuration, cryptographic verification, and explicit operator
decisions own identifiers, time, money, errors, authorization, idempotency, events, migrations,
audit, and recovery.

AI may assist engineers offline by suggesting documentation, tests, log queries, incident summaries,
or migration review observations. Such output requires human review, uses sanitized inputs, records
tool/model provenance where retained, and cannot execute production commands or approve release
gates. No production foundation capability depends on an LLM response.

## Target-release dependencies and completion gates

### Dependency 0 — Decisions and ownership

Approve identifier/reference format, request/correlation trust, error compatibility, currency/zone
validation sources, event governance split, secret/key platform, telemetry stack, retention classes,
deployment compatibility window, SLOs, RPO/RTO, and operations owners.

Gate: each item has an accountable owner, decision record, alternatives/consequences, effective date,
and revisit trigger.

### Dependency 1 — Canonical primitives and API boundary

Implement value types, shared clock use, request/actor context, structured safe logging fields,
error-code registry, target error envelope, version/idempotency headers, and conformance tests.

Gate: one identity workflow and one representative domain command use the primitives; invalid,
stale, unauthorized, overflow, and retry behavior match OpenAPI and tests.

### Dependency 2 — Durable command and event foundation

Add forward migrations and modules for command idempotency, transactional outbox, consumer inbox, and
append-only audit; register event schemas under D19 governance.

Gate: crash injection at every transaction/publication/consumption boundary loses no committed fact,
duplicates no durable effect, and produces observable recovery evidence.

### Dependency 3 — Critical-domain adoption

Adopt the durable primitives for inventory/booking, payment/refund, ledger/payout, policy/admin,
safety/support, privacy deletion, and required provider interactions.

Gate: every critical command declares idempotency scope, database invariant, audit policy, outbox
facts, consumer dedupe, failure matrix, and authorized recovery path; end-to-end race tests pass.

### Dependency 4 — Compatible schema and deployment discipline

Automate empty/upgrade migration tests, old/new application compatibility, bounded backfills,
constraint validation, deploy disable/rollback behavior, and artifact/version evidence.

Gate: a representative expand/backfill/switch/contract exercise survives interruption and rollback
without data guesswork, loss, duplicate effect, or incompatible readers.

### Dependency 5 — Operability and recovery

Define journey SLOs and thresholds, internal readiness, alerts, audited recovery commands, backup
restore, event replay, and cross-domain reconciliation.

Gate: a controlled restore and failure game proves approved RPO/RTO/reconciliation targets; required
audit/outbox facts remain complete and telemetry contains no seeded sensitive values.

### Required target capability

Dependencies 0–5 are cumulative required target behavior. D00 is incomplete if only utility classes,
health, or an outbox table exists; adoption and verified recovery in every correctness-critical
domain are part of the gate.

### Designed extension boundaries

- Additional markets/currencies/time zones use the same primitives and market-keyed configuration.
- New transports/providers implement the same evidence, idempotency, and secret boundaries.
- New public-reference formats are versioned by entity type without changing internal IDs.

The target contract must allow these extensions, but only Vietnam and approved target-release
integrations are active.

### Measured-scale capabilities

Activate a broker, partitioning, separate platform deployment, sharding, multi-region coordination,
or advanced trace infrastructure only when named owners demonstrate sustained SLO, throughput,
retention, regulatory isolation, or team-ownership evidence that the modular-monolith/PostgreSQL
design cannot safely meet. The activation decision includes load evidence, failure/replay design,
cost, rollback, and a post-launch review trigger.

## Verification checklist

### Functional and contract correctness

- [ ] Entity, public, and external identifiers have distinct validated scopes and database uniqueness.
- [ ] All business time comes from an injectable UTC clock and every local date conversion names an IANA zone.
- [ ] Gap/overlap local times, half-open intervals, and exact deadline equality have deterministic tests.
- [ ] Every monetary value carries currency; mixed currency, overflow, and implicit rounding fail.
- [ ] API and security failures use one stable safe envelope with request ID and registered code semantics.
- [ ] Request, trace, correlation, causation, idempotency, aggregate version, and provider IDs are not conflated.
- [ ] Pagination, validation, unknown fields, null/absent updates, and compatibility are documented and bounded.

### Concurrency and recovery

- [ ] Same-key retries replay one result and changed input is rejected without mutation.
- [ ] Different keys cannot bypass domain/database uniqueness for one business effect.
- [ ] State, idempotency result, required audit, and outbox facts commit atomically.
- [ ] Duplicate, delayed, unsupported, and out-of-order events converge, defer, or quarantine without guessed state.
- [ ] Lease expiry, deadlock, timeout, process crash, and transport outage have tested bounded recovery.
- [ ] Applied migrations remain unchanged and upgrade/backfill/rollback compatibility tests pass.
- [ ] Backup restore includes domain and event reconciliation before service activation.

### Security and privacy

- [ ] Actor/service/provider identity and resource authority are verified at every boundary.
- [ ] Secrets, tokens, payment/private/evidence data, and exact protected locations are absent from
  telemetry and generic records.
- [ ] Required audit is append-only, purpose-accessed, export-audited, and subject to approved
  retention/privacy transformation.
- [ ] Public errors, references, health, and timing do not create avoidable enumeration or topology disclosure.
- [ ] Secret rotation, revoked keys, forged context headers, webhook replay, and least-privilege
  database roles are tested.

### Operations

- [ ] Platform and journey metrics have approved numeric SLO/alert thresholds, owners, and response actions.
- [ ] Oldest outbox/inbox/idempotency work, quarantines, migration state, and audit failures are visible and alerted.
- [ ] Recovery commands are bounded, idempotent, authorized, reasoned, audited, and dry-run/maker-checker capable.
- [ ] Structured log and trace correlation crosses request, transaction, outbox, consumer, and provider boundaries.
- [ ] Scale infrastructure remains disabled until its documented measured threshold and owner exist.

## Decisions required before implementation

Each consequential choice should be recorded in an architecture decision record (ADR) with owner,
date, context, alternatives, decision, consequences, rollout, and revisit trigger.

1. Will entity UUIDs remain random database/application UUIDs, and which component generates them for
   each creation path?
2. What prefix, alphabet, entropy, length, checksum, normalization, and rotation/alias policy applies
   to each public reference type?
3. Which request and trace header formats are accepted, generated, echoed, and propagated across
   trusted versus public boundaries?
4. What correlation ID is the root for quote/booking/payment/stay/case journeys, and when may a caller
   resume an existing correlation?
5. What canonical serialization/hashing contract and retention window applies to each idempotent
   command class?
6. Which deterministic failures are replayed, how long may `IN_PROGRESS` remain leased, and what
   response represents an unresolved outcome?
7. Which target error envelope additions are backward compatible with current clients, and will
   semantic ineligibility use `400`, `409`, or `422` consistently?
8. Who owns the error-code registry and compatibility review for API/OpenAPI changes?
9. Which locale/currency/time-zone data sources and update process are approved, and how are rule
   versions recorded for historical local-time decisions?
10. Which rounding modes and deterministic remainder allocation orders are approved for pricing,
    tax, ledger, refunds, payouts, and future foreign exchange?
11. What event-envelope fields are mandatory per sensitivity class, and where is the schema registry
    maintained under D19 governance?
12. Will target-release outbox delivery use a PostgreSQL dispatcher only, and what measured lag,
    throughput, or fan-out threshold activates a broker?
13. What payload-size limit, object-reference pattern, retention, encryption, and deletion behavior
    apply to durable events?
14. Which consumers require inbox rows versus a domain-specific uniqueness constraint, and how are
    handler/rebuild identities versioned?
15. Which actions require audit, before/after reference, maker-checker, break-glass handling, or
    fail-closed audit persistence?
16. What data-classification, redaction allowlist, log/trace sampling, retention, and telemetry-region
    rules apply?
17. Which secret manager/key-management service, workload identity, rotation cadence, emergency
    revocation, and dual-version verification pattern are approved?
18. Which PostgreSQL roles perform migrations, runtime reads/writes, read-only investigation,
    recovery, and backup/restore, and how is use audited?
19. How many old/new application contract versions must coexist during deployment, and who approves
    the destructive contract step?
20. What maximum migration lock duration, backfill batch/lag budget, validation threshold, and abort
    behavior are required?
21. What numeric SLOs, event-lag alerts, idempotency recovery bounds, RPO, RTO, and reconciliation
    completion targets apply to each critical journey?
22. Which recovery commands are implemented first, who may execute/approve them, and which production
    actions always require dry run or maker-checker?
23. What evidence and owner thresholds justify partitioning, broker adoption, service extraction,
    sharding, or multi-region operation?
