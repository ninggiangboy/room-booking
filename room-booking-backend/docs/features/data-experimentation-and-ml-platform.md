# Data, experimentation, and machine-learning platform

## Purpose

This document defines how Room Booking should collect trustworthy product and domain evidence,
calculate governed metrics, run causal experiments, build point-in-time-correct datasets, register
features and model artifacts, serve predictions, and preserve deterministic domain authority.
It expands D19 — Data Platform, Analytics, Metrics, and Experimentation and D20 — Machine-learning
and Decisioning Platform from the
[marketplace problem breakdown](../marketplace-problem-breakdown.md) as one implementation-oriented
design while retaining a strict authority boundary between the two domains.

The central questions are:

> How can the marketplace learn from behavior and outcomes without allowing incomplete analytics,
> experimental treatment, or a probabilistic model to corrupt booking, money, safety, or policy
> truth?

> How can a result be reproduced later from the exact event, metric, feature, label, experiment,
> model, policy, and fallback versions that existed when it was produced?

D19 owns event taxonomy, analytical ingestion, identity-safe attribution, transformations, semantic
metrics, data quality, experiments, research datasets, and deletion propagation. D20 owns feature
definitions, training-set assembly, model artifacts, evaluation and approval, prediction execution,
model monitoring, and release controls. A consuming domain owns the final decision and its legal,
financial, inventory, safety, or contractual constraints.

Examples of those consuming domains include:

- [Personalized search and discovery](personalized-discovery.md), which owns candidate eligibility,
  ranking policy, result order, and guest-facing explanations;
- [Dynamic pricing, quotes, money allocation, and settlement](dynamic-pricing-and-settlement.md),
  which owns deterministic price calculation, host constraints, tax inputs, quote snapshots, and
  offer application;
- [Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md),
  which owns review rights, visibility, aggregates, aspect evidence, and reputation projections;
- [Trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md), which owns
  policy decisions, interventions, moderation, review, and appeals;
- [Disputes, claims, and support](disputes-damage-claims-and-support.md), which owns case decisions,
  remedies, and human authority; and
- [Messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md),
  which owns consent-critical delivery, original communication/incident evidence, urgent routing,
  and stay-operation truth.

## Status and dependencies

This is a target design. It does not describe an implemented data warehouse, event bus, experiment
service, feature store, training pipeline, model registry, or inference service.

The current repository provides only these relevant foundations:

- PostgreSQL, Spring Data JDBC, Liquibase, UTC audit timestamps, UUID support, and optimistic versions
  described in the [platform data model](../data-model/000-platform.md) and
  [data-model roadmap](../data-model/README.md);
- transactional schemas for identity, listings, calendars, bookings, payments, reviews, favorites,
  and location search;
- two in-process immutable Java application-event records for authentication email work;
- post-commit authentication email listeners; and
- basic Spring Boot health exposure.

The repository does **not** currently contain:

- a durable transactional outbox or consumer inbox;
- a versioned domain-event envelope or schema registry;
- behavior-event ingestion, bot filtering, consent enforcement, or identity stitching;
- warehouse/lake storage, transformations, lineage, semantic metrics, or data-quality automation;
- experiment definitions, deterministic assignment, exposure records, guardrails, or analysis;
- a feature registry, point-in-time training-set builder, model registry, artifact storage, online
  feature serving, prediction log, monitoring, or model release workflow; or
- production implementations of most domain events and model consumers described in the feature
  documents.

In-process `ApplicationEventPublisher` plus `@TransactionalEventListener(AFTER_COMMIT)` is useful for
today's best-effort email path, but it is not a durable marketplace event backbone. A process crash
after database commit and before listener completion can lose work. This design proposes forward
migrations and modules when implementation begins; it does not modify an applied migration.

Recommended dependency order:

1. Approve event, metric, privacy, experiment, model-governance, and ownership conventions.
2. Introduce a shared outbox/inbox contract and producer conformance tests.
3. Instrument one complete journey using server facts and validated client interactions.
4. Establish raw, conformed, and semantic analytical layers with reconciliation and deletion.
5. Publish a deterministic metric catalog and experiment assignment/exposure service.
6. Build one point-in-time dataset and deterministic or interpretable model behind shadow mode.
7. Add governed batch or online inference with consumer-owned constraints and fallback.
8. Add specialized streaming, online feature infrastructure, or separate deployment units only when
   measured freshness, throughput, reliability, or team-ownership needs justify them.

This platform depends on stable domain identifiers and committed outcome facts. It must not delay
correct non-ML implementations of search, quote, booking, payment, review, safety, or support.

## Goals

- Create one versioned vocabulary for committed domain facts and measured user interactions.
- Preserve source provenance from production transaction through metric, dataset, feature,
  prediction, decision, and experiment result.
- Correlate session, search, impression, click, quote, booking, payment, stay, review, and support
  outcomes without uncontrolled fingerprinting.
- Make business and operational metrics reproducible, reviewable, and resistant to definition drift.
- Run stable, mutually compatible experiments whose exposure and outcomes support causal analysis.
- Prevent future-information leakage, training/serving skew, invalid labels, and selective-outcome
  bias in machine-learning workflows.
- Supply bounded predictions with explicit freshness, confidence, version, reason, and fallback state.
- Keep final domain decisions inside deterministic policy and safety constraints.
- Propagate consent changes, opt-out, correction, deletion, and retention through analytical and ML
  derivatives.
- Detect missing, duplicated, late, reordered, malformed, or statistically implausible data before it
  influences product or model decisions.
- Start with infrastructure proportional to current traffic and make later extraction evidence-led.
- Give product, engineering, data, ML, privacy, security, risk, finance, and operations a shared audit
  trail for consequential changes.

## Non-goals

- Replacing transactional domain tables with a warehouse, lake, search index, cache, or event log.
- Implementing event sourcing or Command Query Responsibility Segregation (CQRS).
- Requiring Kafka, a lakehouse, a real-time feature store, a vector database, Kubernetes, or separate
  microservices for the first correct vertical slice.
- Letting an experiment bypass availability, money, tax, contractual disclosure, safety, privacy, or
  host-control rules.
- Letting a model directly confirm or cancel bookings, calculate tax, post ledger entries, capture or
  refund money, move payouts, publish or remove reviews, suspend actors, or decide support remedies.
- Treating clicks, conversion, or model score as a complete measure of guest, host, or marketplace
  value.
- Building a universal user score, universal listing score, or irreversible global trust score.
- Collecting data speculatively without purpose, owner, retention, access, and deletion rules.
- Reusing sensitive support, message, payment, identity, or safety content as general training data.
- Promising statistically significant or causal results when the design, traffic, or assumptions do
  not support them.
- Automating model promotion before deterministic baselines, shadow evaluation, rollback, and
  accountable approval exist.

## Core principles and invariants

### Transactional facts remain authoritative

Bookings, availability, quotes, payment observations, ledger entries, review revisions, moderation
decisions, and case outcomes are owned by their transactional domains. Analytics may reconcile and
report a discrepancy but must never silently repair or mutate the source record. A prediction cannot
make a stale search result bookable or make a provider observation an accounting fact.

### Facts, observations, predictions, and decisions are different records

A committed fact states what a source domain accepted. An observation records something seen, such
as a client interaction or provider response. A prediction estimates an outcome. A decision applies
policy to facts and optional predictions. These types must use different schemas and ownership; none
is renamed to make another layer appear certain.

### Historical evidence is immutable and corrections are additive

Accepted event envelopes, experiment exposures, feature snapshots used for predictions, model
artifacts, prediction records, and decision references are immutable. Correction, invalidation,
redaction, label maturation, and restatement create linked records or replacement versions. Replays
must be able to distinguish original arrival time from corrected effective time.

### Contracts are typed, versioned, and owned

Every production event, metric, feature, label, experiment, and model has a stable key, owner,
semantic description, schema/type, version, lifecycle status, compatibility policy, and consumers.
Free-form JSON may carry bounded extension metadata, but cannot replace governed columns or schemas
for essential dimensions.

### Time has explicit semantics

Records distinguish at least:

- `occurredAt`: when the source fact or observation happened;
- `committedAt`: when the authoritative source transaction committed;
- `receivedAt`: when the collector accepted the record;
- `ingestedAt`: when the analytical layer persisted it;
- `effectiveFrom`/`effectiveTo`: when a policy or definition applies;
- `featureAsOf`: the latest permissible information time for one feature vector;
- `predictionAt`: when an inference was requested or computed; and
- `labelObservedAt` and `labelMaturedAt`: when an outcome became known and safe to evaluate.

Listing-local dates remain local-date facts with an IANA timezone. Event instants use UTC. A late
event can affect a past event-time window without pretending it arrived on time.

### At-least-once delivery must converge

Durable domain publication assumes at-least-once relay and possible reordering. Event identity,
producer aggregate version, inbox deduplication, idempotent transformations, deterministic merge
logic, and reconciliation must converge after retries and replay. Exactly-once marketing language is
not an excuse to omit database defenses.

### Identity attribution is bounded and privacy-safe

Authenticated user ID, first-party pseudonymous installation/session ID, and request/correlation IDs
may be linked only under declared purpose and consent. Identity merges are versioned and reversible
where policy requires. The platform must not create covert cross-device or cross-site identity using
uncontrolled fingerprinting.

### Assignment is not exposure

An experiment assignment is stable for its declared unit, scope, and epoch. Exposure is recorded
only when the eligible treatment could actually affect the unit. Merely computing a branch, returning
an unrendered candidate, or prefetching a response is not necessarily exposure.

### Point-in-time correctness is non-negotiable

A training or evaluation row can use only facts available by its declared prediction time, including
the historical version and freshness that online serving could have seen. Later cancellation,
moderation, appeal, refund, chargeback, or support outcomes may be labels when the target calls for
them; they must not leak into earlier features.

### Models advise within domain-owned constraints

D20 returns a prediction, uncertainty, reason codes, and fallback metadata. The consumer applies
eligibility, legal policy, price bounds, safety controls, and final action. The persisted decision
references the prediction but belongs to the consuming domain. If D20 is unavailable or outside its
budget, a named deterministic fallback applies.

### Missing and stale data are explicit states

Missing evidence is not automatically zero, false, low quality, high risk, or negative preference.
Features carry presence, source time, freshness, and imputation provenance. Consumers declare
maximum age and safe behavior for missing or stale values.

### Metrics optimize a balanced marketplace

Primary metrics are accompanied by guardrails for completed-stay satisfaction, cancellations,
incidents, refunds, host economics, listing exposure, latency, and fairness. A local conversion gain
does not justify hidden fees, unsafe stays, host harm, or long-term marketplace degradation.

### Privacy, fairness, and abuse controls begin at definition time

Data purpose, legal basis/consent where applicable, allowed features, prohibited proxies, retention,
access, cohort thresholds, deletion behavior, and fairness slices are reviewed before collection or
modeling. Filtering bot or manipulation traffic is versioned and cannot erase inconvenient genuine
outcomes without an auditable rule.

### Complexity requires measured need

The MVP may use PostgreSQL outbox tables, bounded event ingestion, scheduled exports, SQL
transformations, object storage or a selected analytical warehouse, and simple batch artifacts. New
distributed systems require measured volume, latency, availability, cost, or ownership evidence plus
an operator, SLO, recovery design, and exit plan.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Domain fact | A committed past-tense record owned by a transactional domain |
| Observation | A measured client, server, provider, or operational occurrence that may be incomplete |
| Event envelope | Common identity, version, time, actor, lineage, privacy, and payload metadata around an event |
| Event taxonomy | Registry of allowed event types, meanings, owners, schemas, and lifecycle states |
| Schema compatibility | Rules defining whether producers/consumers can read old and new event versions |
| Outbox | Rows written atomically with a source-domain change and relayed after commit |
| Inbox | Consumer-side durable event receipt used to deduplicate application of side effects |
| Replay | Reprocessing retained facts through a selected consumer/version without duplicating effects |
| Correction | Additive statement that supersedes or invalidates prior analytical meaning |
| Watermark | Declared event time through which a dataset believes inputs are sufficiently complete |
| Raw layer | Restricted append-only copy of accepted source records plus ingestion metadata |
| Conformed layer | Validated, deduplicated, privacy-classified, typed facts with shared dimensions |
| Semantic layer | Governed business metrics and dimensions built from conformed facts |
| Data product | Owned dataset or contract with purpose, schema, quality objectives, access, and lifecycle |
| Metric | Versioned formula, grain, population, windows, exclusions, owner, and interpretation |
| Operational metric | Low-latency signal for service operation; not necessarily an audited business report |
| Audited report | Finance/compliance output reconciled to authoritative sources under controlled definitions |
| Assignment unit | Stable entity randomized in an experiment, such as user, session, listing, host, or market |
| Assignment | Deterministic allocation of an eligible unit to an experiment variant |
| Exposure | Evidence that a treatment had a reasonable opportunity to affect the assigned unit |
| Experiment epoch | Immutable version of population, variants, allocation, hypothesis, and metric plan |
| Guardrail | Metric or invariant that can block, stop, or reverse a rollout despite primary-metric lift |
| Holdout | Stable untreated population retained to measure cumulative or long-term effects |
| Sample-ratio mismatch | Observed variant counts differ unexpectedly from declared assignment proportions |
| Attribution window | Time and rules connecting an exposure or action to later outcomes |
| Feature definition | Typed, versioned computation with entity keys, event-time semantics, freshness, and owner |
| Feature value | One value for one entity/context at an `asOf` time, including presence and provenance |
| Feature set | Immutable collection of compatible feature-definition versions used by a model |
| Label | Versioned outcome for a target/horizon, with observation and maturity status |
| Training example | Entity/context, prediction time, point-in-time features, label, weight, and provenance |
| Artifact | Immutable serialized model or rule package identified by hash and build lineage |
| Model version | Registered artifact plus feature set, training/evaluation evidence, status, and owner |
| Prediction | Immutable model output for a target, entity/context, time, and model/feature versions |
| Decision | Domain-owned application of policy to facts and optional predictions |
| Shadow mode | Computing predictions without allowing them to affect user or domain outcomes |
| Champion/challenger | Current approved model and a separately evaluated candidate |
| Training-serving skew | Difference between how a feature is produced offline and at inference time |
| Label leakage | Information unavailable at prediction time improperly reveals the target |
| Drift | Material change in inputs, predictions, labels, calibration, or performance over time |
| Censoring | Outcome cannot yet be observed for the full target horizon |
| Selective labels | Outcomes are observed only for cases selected by an earlier policy or model |
| Kill switch | Audited control that disables a model, treatment, feature source, or pipeline scope |

## End-to-end flow

The evidence and learning loop is:

```text
authoritative domain transaction                 validated client interaction
  -> fact + outbox in one commit                   -> consent/schema/rate validation
  -> at-least-once relay                           -> idempotent accepted observation
                \                                  /
                 -> raw immutable ingestion envelope
                 -> validation, dedupe, privacy classification, bot/internal filtering
                 -> conformed facts + shared dimensions + source reconciliation
                 -> semantic metrics, experiment exposures, labels, and feature history
                 -> point-in-time dataset -> train/evaluate -> register/approve artifact
                 -> shadow/canary inference -> bounded prediction
                 -> consuming domain applies policy/constraints -> domain decision
                 -> outcome facts return to analytics and model evaluation
```

The primary transaction boundary is always inside the source domain. It changes authoritative state
and inserts its outbox fact atomically. Relay, ingestion, transformation, training, and inference are
separate operations. No warehouse or model call is held inside an inventory, booking, money, review,
or safety transaction.

For a synchronous prediction:

```text
consumer creates canonical decision context
  -> resolve approved experiment/config epoch
  -> fetch only allowlisted features at or before request time
  -> invoke approved model under a strict deadline
  -> return prediction/version/confidence/freshness or explicit fallback
  -> consumer validates deterministic constraints and commits its own decision
  -> decision + prediction reference + outbox fact are persisted
```

For a batch prediction, the same authority holds: D20 writes a rebuildable prediction projection;
the consumer chooses whether and how to use it at decision time.

## Platform layers and authority

### Logical layers

| Layer | Owns | Must not own |
| --- | --- | --- |
| Transactional domains | Business facts, invariants, decisions, corrective actions | Analytical joins or training pipelines |
| Collection and event contracts | Event acceptance, envelope validation, delivery evidence | Source-domain business truth |
| Analytical storage | Immutable ingestion, conformed history, reproducible transformations | Booking, money, inventory, review, or safety mutations |
| Semantic metrics | Versioned measures and governed dimensions | Silent redefinition of transactional outcomes |
| Experimentation | Eligibility inputs, stable assignment, exposure, analysis plan/results | Domain eligibility or unsafe treatment |
| Feature platform | Approved computations and point-in-time values | Final user/business decisions |
| ML lifecycle | Training, artifact registry, evaluation, deployment status | Policy waivers or source facts |
| Prediction runtime | Bounded prediction and fallback metadata | Consequential action |
| Domain decision service | Constraints, policy, action, explanation, audit | Re-implementing model training or metric SQL |

These are logical module boundaries. They can begin inside the modular monolith plus scheduled data
jobs. Physical extraction is a later operational decision.

### Source-of-truth matrix

| Fact | Authority | D19/D20 use |
| --- | --- | --- |
| User/account state | Identity | Pseudonymous dimensions and permitted features |
| Listing publication/content | Listing | Historical dimensions and candidate features |
| Availability and restriction | Inventory | Time-correct eligibility features, never booking authority |
| Search result order | Discovery decision | Impression attribution and ranking evaluation |
| Quote amount and allocation | Pricing/quote | Price/value features and outcome metrics |
| Booking lifecycle | Booking | Funnel and completed-stay labels |
| Provider money movement | Payment/payout evidence | Reliability metrics and approved labels |
| Economic balance/revenue | Ledger/finance | Reconciled financial metrics, not clickstream arithmetic |
| Review text/visibility/aspects | Reviews/D15 | Approved features, extraction evaluation, satisfaction evidence |
| Risk action/moderation | D15 | Decision analysis and adjudicated labels |
| Support remedy/case outcome | D16 | Approved quality labels and operations metrics |
| Assignment/exposure | D19 | Causal analysis and model evaluation |
| Feature/model/prediction | D20 | Reproducibility and bounded consumer input |
| Final modeled action | Consuming domain | Outcome attribution; not overwritten by D20 |

## Event taxonomy and schema governance

### Event classes

Use distinct namespaces or an explicit `eventClass`:

- **domain fact**: committed source-domain outcome such as `BookingConfirmed`;
- **interaction observation**: validated user-interface action such as `ListingImpressionRecorded`;
- **provider observation**: signed or reconciled external evidence;
- **operational event**: pipeline or service lifecycle evidence;
- **correction/tombstone**: additive invalidation, privacy suppression, or semantic correction; and
- **derived fact**: a governed result such as `ExperimentExposureRecorded` or
  `FeatureMaterializationCompleted`.

Commands, desired actions, and mutable snapshots must not masquerade as committed events.

### Canonical envelope

An illustrative versioned envelope is:

```json
{
  "eventId": "uuid",
  "eventName": "BookingConfirmed",
  "eventClass": "DOMAIN_FACT",
  "schemaVersion": 1,
  "producer": "booking",
  "producerEnvironment": "production",
  "occurredAt": "2026-09-06T10:00:00Z",
  "committedAt": "2026-09-06T10:00:00.125Z",
  "receivedAt": null,
  "aggregateType": "BOOKING",
  "aggregateId": "uuid",
  "aggregateVersion": 7,
  "correlationId": "uuid",
  "causationId": "uuid",
  "actor": {"type": "USER", "id": "pseudonymous-reference"},
  "privacyClass": "PSEUDONYMOUS",
  "market": "VN",
  "payload": {"bookingId": "uuid", "quoteId": "uuid"},
  "metadata": {"traceId": "opaque-value"}
}
```

Payloads contain stable identifiers, state/version, and minimum routing data. Consumers fetch
authorized sensitive detail from its owner or a purpose-built restricted data product. Raw email,
phone, address, message/review text, access instructions, payment instrument data, government IDs,
secrets, and unrestricted device/network data do not belong in the general envelope.

### Registry lifecycle and compatibility

An event definition moves through `DRAFT -> REVIEWED -> ACTIVE -> DEPRECATED -> RETIRED`. Activation
requires an owner, description, grain, source transaction, privacy classification, JSON/Avro-like
schema or equivalent typed contract, examples, ordering semantics, retention, expected volume,
consumers, and compatibility tests.

Recommended compatibility policy:

- additive optional fields may remain within one schema version if consumers ignore unknown fields;
- renamed, removed, retyped, newly required, or semantically changed fields require a new version;
- an event name's grain and meaning never change in place;
- producer and consumer deployment must support an explicit overlap window;
- deprecated versions retain a last-production date and deletion/replay plan; and
- registry changes are reviewed like API contracts, not edited informally in dashboard SQL.

### Ordering and corrections

Global ordering is neither assumed nor required. Aggregate version establishes local source order
when the domain exposes one. Consumers encountering a gap may wait, fetch an authorized snapshot,
or quarantine according to contract. A later version may supersede earlier state but never changes
the historical envelope.

Corrections include `correctionId`, `correctsEventId` or a bounded source range, reason code,
effective time, actor/process, and correction schema version. Physical erasure remains a separate
privacy operation when legally and operationally required.

## Instrumentation and attribution

### Server versus client evidence

Prefer server-created facts for acceptance, eligibility, quote, booking, payment, refund, completion,
review publication, and remedy outcomes. Use client instrumentation for rendering, clicks, scroll,
map interactions, and declared interface choices that the server cannot observe.

A client event is accepted only when it passes:

- schema and supported-version validation;
- event ID and batch-size limits;
- issued session/request/search token validation where applicable;
- plausible event-time and sequence bounds;
- origin/application-version allowlist;
- consent and purpose checks;
- payload size, field allowlist, and sensitive-data scanning;
- rate, bot, automation, and internal/test-traffic controls; and
- resource plausibility without leaking whether an unauthorized resource exists.

The collector records acceptance/rejection reason and server receive time. It never trusts the
client to assert booking, payment, experiment assignment, price, safety, or review truth.

### Impression semantics

Each surface defines viewability before implementation. A search impression might require that the
listing card intersects a visible viewport for a minimum duration; a notification exposure might
require provider delivery or in-app rendering; a price treatment might be exposed when a materially
different payable offer is rendered. Definitions are versioned and avoid claiming exposure from
server generation alone.

Repeated views require a named counting rule such as raw view, deduplicated view per request, or
deduplicated view per unit/day. All three may be valid metrics, but cannot share one ambiguous name.

### Correlation identifiers

Use opaque identifiers with bounded scope:

- `requestId` for one API request;
- `correlationId` for one business journey or saga;
- `sessionId` for one declared first-party session;
- `searchRequestId` for one normalized search execution;
- `resultSetId` and `rankDecisionId` for one ordered candidate set;
- `quoteId`, `bookingId`, and domain IDs from authoritative owners;
- `assignmentId` and `exposureId` from experimentation; and
- `predictionId` and `decisionId` across D20 and the consumer.

Identifiers are not authorization credentials. Public tokens are unguessable and scoped; analytical
IDs are not exposed when unnecessary.

### Identity resolution

Identity states include anonymous session, authenticated user, known internal/test actor, deleted or
suppressed subject, and deliberately unlinked observation. Authentication may link future events to
a user under purpose and retention rules. Historical anonymous events are merged only when the
approved policy permits it; otherwise they stay anonymous.

An identity-link record identifies source pseudonym, target pseudonym, method, confidence for
non-authenticated links, effective interval, consent/purpose, and reversal/deletion status. Do not
infer a household or person from shared IP, exact location, device fingerprint, or payment data for
general personalization.

### Attribution

Attribution is not one universal last-click field. Each use case declares eligible touchpoints,
unit, outcome, lookback window, cross-device policy, ordering, multi-touch rule, treatment of direct
traffic, and cancellation/refund maturity. Causal experiment estimates take precedence over
observational attribution for treatment impact.

## Durable ingestion and processing

### Transactional outbox

The source domain writes business state and one or more outbox rows in the same local transaction.
An outbox row includes event ID/name/schema, aggregate ID/version, occurrence/commit time,
correlation/causation, privacy class, compact payload, publication status, attempts, next-attempt
time, and lease/fencing metadata.

A relay claims a bounded ordered batch, publishes or transfers it, and records delivery evidence.
Failure after publish but before acknowledgment causes a duplicate, which consumers must tolerate.
Outbox cleanup occurs only after retention and replay requirements are satisfied.

One shared physical outbox is a reasonable modular-monolith default if domain ownership, access,
payload limits, and index contention remain controlled. Finance or other restricted domains may use
separate tables or payload references without changing the common envelope.

### Consumer inbox and idempotent application

A stateful consumer inserts `(consumerName, eventId, consumerContractVersion)` under a unique
constraint in the same transaction as its local projection change. Repeated delivery returns the
prior result or no-op. When one source event legitimately creates multiple effects, each effect has
a deterministic business key.

Analytical append ingestion may retain duplicate arrivals for operational evidence while exposing a
deduplicated conformed view. It never double-counts them in governed metrics.

### Client collector

The behavior collector accepts bounded compressed batches, authenticates application origin where
possible, applies quotas, returns accepted/rejected counts, and makes a batch retry safe through
event IDs. `202 Accepted` means the collector durably accepted observations, not that all warehouse
transformations completed.

Offline clients may send delayed events within a configured maximum age. The envelope keeps client
event time and server receive time. Events outside the analytical window may be retained in a
restricted late-event stream or rejected with a stable reason; they must not be silently shifted to
the current day.

### Delivery modes and backpressure

The first implementation may relay to a bounded PostgreSQL landing table or export immutable files
to approved object/warehouse storage. Scheduled micro-batches are acceptable when freshness
objectives allow them. A stream broker becomes justified by measured throughput, fan-out, isolation,
or latency, not by the existence of events.

Backpressure prioritizes authoritative outbox durability, then security/financial consumers, then
product projections, then optional analytics enrichment. Analytics slowness must not exhaust the
transactional connection pool or block source commits.

## Analytical storage, transformation, and lineage

### Data layers

Use a small number of explicit layers:

1. **Landing/raw**: immutable accepted envelopes, arrival attempts, source checksums, and access
   restrictions.
2. **Conformed**: deduplicated facts, shared dimensions, correction status, standardized time and
   identifiers, consent/retention classification, and bot/internal filters.
3. **Semantic**: governed facts and dimensions at declared grain, with versioned metric-ready logic.
4. **Serving/export**: dashboards, approved research datasets, experiment analysis, and model inputs.

Raw does not mean ungoverned. Encryption, access, retention, deletion, malware/content controls, and
data minimization apply from landing onward.

### Dimensional history

Mutable dimensions such as listing attributes, host status, market, policy, or taxonomy use
effective-dated versions where historical analysis requires them. A booking event joins to the
listing or policy version valid at event time, not automatically to today's record. Dimensions
retain stable surrogate/version IDs and source provenance.

### Transform contracts

Each transformation declares:

- output name, grain, primary key, and schema;
- source datasets and minimum watermarks;
- code/SQL version and deterministic parameters;
- owner, schedule, freshness target, and retry policy;
- tests for uniqueness, nullability, ranges, referential integrity, and reconciliation;
- privacy classification, access, retention, and deletion behavior; and
- downstream consumers and breaking-change process.

A run records input partitions/watermarks/checksums, code version, start/end, result counts, rejected
records, quality status, and output snapshot/version. Failed runs do not publish a partial semantic
version as current.

### Reconciliation

Daily or use-case-appropriate checks compare conformed facts with authoritative source counts and
amounts. Examples include confirmed/completed/cancelled bookings, successful refunds, published
reviews, and ledger-defined revenue. Differences produce categorized cases: delayed, duplicate,
missing source, missing analytical, definition mismatch, privacy suppression, or unknown.

Financial dashboards source audited amounts from finance-owned exports and definitions. They do not
reconstruct revenue from quote or payment events when ledger truth exists.

### Late data and restatement

Watermarks and allowed lateness are per data product. A late but valid fact may restate an open
partition and increment its semantic version. Published experiment reports identify their data
cutoff, completeness, restatement policy, and whether conclusions changed. Closed audited periods
follow finance/compliance correction policy rather than generic analytics overwrite.

## Metric and semantic governance

### Metric definition

Every governed metric records:

- stable metric key and semantic version;
- human definition and business question;
- numerator, denominator, unit, grain, and dimensions;
- eligible population, exclusions, deduplication, and bot/internal rules;
- event-time windows, timezone, attribution, and outcome-maturity rules;
- source datasets and minimum quality/freshness;
- owner, reviewers, sensitivity classification, and intended decisions;
- query/transformation version and validation examples; and
- deprecation, comparability, and restatement policy.

`booking_conversion_rate` is insufficiently precise. A valid name might distinguish
`confirmed_booking_per_exposed_search_session_28d_v2` from
`completed_stay_per_unique_guest_90d_v1`.

### Metric families

| Family | Representative governed measures | Important qualifications |
| --- | --- | --- |
| Discovery | eligible result rate, zero-result rate, click, quote, confirmed booking | Position, availability freshness, trip context, bot filtering |
| Stay value | completion, satisfaction, aspect outcomes, repeat intent | Maturity horizon, review selection bias, incident severity |
| Guest economics | payable total, refund, credit, value perception | Currency, tax/fee treatment, cancellation maturity |
| Host economics | nights, occupancy, Average Daily Rate, host entitlement/paid amount | Available-night denominator, cancellations, ledger reconciliation |
| Platform economics | recognized revenue, variable cost, contribution | Finance-owned definitions; never Gross Booking Value alone |
| Reliability | latency, errors, stale data, recovery time | Service, market, version, dependency, fallback |
| Safety/support | incidents, escalations, remedies, recurrence | Severity, adjudication, privacy thresholds, delayed labels |
| Marketplace | liquidity, concentration, new-listing exposure, repeat supply/demand | Market comparability and long-term guardrails |

Currency aggregation uses either native-currency partitions or a versioned finance-approved foreign
exchange conversion dataset and conversion time. Integer minor-unit source values remain exact; no
floating-point value is used as transactional money.

### Metric changes

Semantic changes create a new version. Cosmetic description fixes may not. Reports display version
and freshness. During migration, old and new versions run in parallel with quantified differences.
Deleting the old name before consumers migrate is prohibited.

### Operational versus analytical truth

Low-latency service metrics may be approximate and optimized for alerting. Product metrics may be
restated as late facts arrive. Audited financial/tax reports follow reconciled close rules. Their
labels, freshness, and authority must be visibly different.

## Experimentation design

### Experiment lifecycle

An experiment follows:

```text
DRAFT -> REVIEWED -> SCHEDULED -> RUNNING -> PAUSED -> COMPLETED -> ANALYZED -> ARCHIVED
                         |           |
                         +-> STOPPED +-> ROLLED_BACK
```

`REVIEWED` requires a hypothesis, assignment unit, population, exclusions, variants, allocation,
power/sample-duration estimate, exposure rule, primary metric, guardrails, analysis plan, novelty and
carryover assumptions, owner, approvers, start/stop criteria, and rollback. The epoch becomes
immutable after first assignment; material change creates a new epoch.

### Stable deterministic assignment

For an eligible unit and experiment epoch:

```text
bucket = unsigned_hash(namespace_salt_version || experiment_epoch_id || canonical_unit_id)
         mod BUCKET_COUNT
```

Contiguous configured bucket ranges map to variants. Salts are secret or restricted where needed to
prevent gaming, but assignment is reproducible by authorized services. Allocation change creates a
new epoch unless a pre-approved monotonic ramp policy preserves analysis validity.

Assignment unit choice follows interference risk:

- user for persistent guest experiences;
- session for short-lived intent experiments when carryover is acceptable;
- listing or host for supply-side controls;
- booking for post-contract communications when earlier exposure cannot contaminate it;
- market/geography or time switchback for interventions with strong marketplace spillover.

Do not randomize a host price setting by guest when it violates host control, public-price policy,
fairness, or contractual disclosure.

### Eligibility and assignment snapshots

Eligibility belongs to the relevant domain/policy, while D19 records the evaluated inputs and
version. An assignment stores experiment/epoch, unit type and pseudonymous ID, variant, assigned at,
eligibility snapshot/hash, allocator version, namespace, and reason. A repeat lookup returns the same
assignment for the epoch.

Assignment before authentication must have an explicit merge policy. Rebucketing a person merely
because they signed in creates contamination and is prohibited unless the experiment definition
selects session as its unit.

### Exposure contract

An exposure includes assignment ID, unit, variant, surface/decision, occurred and received times,
request/result/decision IDs, exposure-rule version, experiment context, and deduplication key. The
producer that knows treatment could affect the experience should emit it. Client evidence may be
required for viewability; server assignment alone is insufficient.

Repeated exposure policy is declared per experiment. Analysis distinguishes first exposure,
exposure count, duration, and cross-over. A failed model call that falls back to control behavior is
recorded as assigned treatment with actual-treatment/fallback metadata, not falsely counted as a
successful treatment delivery.

### Mutual exclusion and interaction

Experiments declare namespace, layer, exclusions, required co-experiments, and known interaction
risks. One unit receives at most one incompatible treatment per namespace/epoch. Factorial designs
are explicit; accidental overlap is not treated as a factorial experiment after the fact.

Examples of namespaces include search rank, public price, checkout, messaging, host pricing tools,
and review presentation. A global registry detects collisions before activation.

### Statistical analysis contract

Before launch, the analysis plan declares:

- estimand, such as intent-to-treat or treatment-on-treated;
- unit of analysis and clustering;
- baseline rate/variance and minimum detectable effect;
- sample size, minimum runtime, and maximum runtime;
- confidence/credible interval method and alpha/error budget;
- repeated-look or sequential-testing method;
- multiple-primary-metric or multiple-comparison correction;
- missing, late, censored, and triggered-unit handling;
- variance reduction or covariate adjustment fixed in advance;
- heterogeneity slices that are exploratory versus confirmatory; and
- decision rule, practical significance, and guardrail thresholds.

The platform should provide validated methods and templates; it must not reduce experiment approval
to a dashboard showing green percentages. Peeking and stopping on an unadjusted p-value is not a
valid sequential policy.

### Sample-ratio mismatch and integrity checks

Automated checks include assignment counts against declared ratios, missing exposures, exposure
before assignment, cross-over, duplicate units, incompatible overlap, client/server disagreement,
event loss by application version, bot/internal imbalance, and outcome availability by variant.
Material sample-ratio mismatch pauses interpretation and may automatically stop a risky treatment.

### Guardrails, stopping, and rollback

Guardrails include hard invariants and statistical/product limits. Examples are booking errors,
latency, payment failure, cancellation, refund, incident severity, support contacts, guest payable
price, host net/earnings, new-listing exposure, concentration, and fairness slices. Financial and
safety guardrails use domain-owned facts.

Emergency stop disables new treatment delivery while retaining assignment and exposure history.
Rollback restores an approved deterministic behavior; it does not delete unfavorable evidence.

### Long-term holdouts and novelty

Short experiments can miss learning effects, seasonality, host response, repeat behavior, or
marketplace equilibrium. High-impact ranking, pricing, promotion, and notification programs may keep
a small stable holdout with reviewed cost and fairness. Reports separate novelty period, mature
period, and post-treatment carryover.

### Attribution and causal limitations

Randomization supports causal claims only when assignment, exposure, interference, compliance,
missingness, and outcome definitions are valid. Marketplaces have network effects: treating guests
can change host availability and untreated guest outcomes. Cluster, market-level, or switchback
designs may be needed. Observational propensity adjustment does not automatically repair a flawed
experiment.

Contextual bandits and adaptive allocation are later options. They require logged action
probabilities, exploration support, off-policy evaluation, safety constraints, and a different
analysis contract; ordinary A/B analysis must not be applied blindly.

## Data quality, lineage, and governance

### Quality dimensions

Each production data product defines objectives for:

- freshness and maximum tolerated lag;
- completeness against an authoritative or expected source;
- uniqueness at its declared grain;
- schema and type validity;
- referential integrity and valid dimension history;
- accepted ranges and distribution stability;
- event-time ordering or gap behavior where applicable;
- privacy classification and prohibited-field absence;
- reconciliation accuracy; and
- reproducibility from retained inputs and code.

Quality status is `PASS`, `WARN`, `FAIL`, or `UNKNOWN`. A failed upstream product cannot silently
publish a downstream dataset as healthy. Consumers declare whether they block, fall back, quarantine,
or accept a warning.

### Contract tests and quarantine

Producer contract tests validate example envelopes and compatibility in continuous integration.
Runtime validation sends malformed, unsupported, over-sized, or policy-violating records to a
restricted quarantine containing only the evidence needed to debug them. Quarantine is not an
unbounded shadow data lake and follows shorter retention where possible.

Unknown fields do not become features automatically. New enum values follow an `UNKNOWN`-safe
consumer policy or require coordinated rollout. Sudden null, cardinality, or volume changes alert the
owner before dependent metrics/models are trusted.

### Lineage and reproducibility

For any dashboard number, experiment result, feature value, or model artifact, authorized operators
must be able to traverse:

```text
result
  -> semantic/feature/model version
  -> transformation or training run
  -> code/config/artifact hashes
  -> source dataset snapshots and watermarks
  -> accepted events and authoritative source identifiers
```

Lineage metadata is append-only and access-controlled. Reproduction uses the historical definition,
not today's SQL or feature code.

### Ownership and change control

Every data product has one accountable business/domain owner and one technical steward. Critical
financial, privacy, safety, experiment, and model changes require appropriate independent review.
Owners define deprecation timelines, on-call expectations, data-quality objectives, and consumers.

Dashboard creation does not create a new authoritative metric. Ad hoc analysis is allowed in
restricted workspaces, but a result used for recurring product decisions must graduate to a reviewed
semantic definition.

## Privacy, consent, retention, and subject rights

### Purpose-bound collection

The collection catalog records purpose, data categories, source, subject type, legal basis or
consent requirement, allowed consumers, residency constraints, retention, deletion action, and
whether training is allowed. The most restrictive applicable policy follows data into derived
products unless an approved transformation changes the classification.

Consent state is effective-dated. Collection and use are separate checks: a historical event that
was lawfully collected is not automatically eligible for every new model purpose. Opt-out of
personalization must immediately affect serving policy and propagate to durable profiles and future
training under the approved deletion timetable.

### Minimization and pseudonymization

Use pseudonymous subject and entity IDs in general analytics. Keep re-identification mappings in a
separately controlled service or table. Coarsen location/time when exact values are unnecessary.
Tokenize or remove free text, network identifiers, user-agent detail, and provider payloads.

Authentication secrets, payment credentials, government IDs, exact access instructions, private
messages, unredacted support/safety evidence, and exact private addresses are prohibited in general
events and feature stores. A restricted evidence product requires its own purpose, access, and
retention; it does not become a general training corpus.

### Retention and deletion propagation

Retention is defined independently for raw events, conformed facts, semantic aggregates,
assignments/exposures, feature history, training examples, predictions, artifacts, and decision audit.
A deletion request resolves all governed subject identifiers and produces a deletion/suppression job
with a scope, policy basis, deadline, and proof of completion.

Propagation covers:

- landing and warehouse partitions;
- materialized metrics and user-level exports;
- experiment assignment/exposure records where erasure is permitted;
- offline and online feature values;
- training datasets and reusable embeddings;
- caches and prediction stores;
- research notebooks/exports under controlled registries; and
- eligibility for future model builds.

Immutable contractual, financial, fraud, safety, or legal records may require retention or
pseudonymized suppression instead of erasure. That exception is decided by the owning policy/legal
domain and is recorded; D19/D20 do not invent it.

Already trained artifacts require an approved model-deletion policy. At minimum, deleted examples
are excluded from future training, datasets are invalidated, and affected models are tracked for
scheduled retraining. Higher-risk cases may require immediate retirement or machine-unlearning only
when validated and legally necessary; claims that a model has “forgotten” data require evidence.

### Aggregate release and research access

Host and market insights apply minimum cohort sizes, contribution limits, suppression, and
anti-differencing rules. Researchers receive time-bound, purpose-specific, least-privilege datasets
with export controls and audit. Joining two individually approved datasets requires renewed review
when the combination increases re-identification or discrimination risk.

## Label design and outcome maturity

### Label contract

Every label definition includes:

- target name, entity/context grain, prediction timestamp, and horizon;
- positive, negative, unresolved, censored, and excluded outcomes;
- authoritative source events and versioned mapping;
- observation time, maturity delay, correction and appeal behavior;
- known selection, survivorship, reporting, and measurement biases;
- privacy/training eligibility and retention;
- quality owner and adjudication process; and
- version compatibility with earlier training sets.

Examples distinguish `BOOKING_CONFIRMED_WITHIN_7D`, `STAY_COMPLETED_WITHOUT_SEVERE_INCIDENT`,
`REFUND_REQUIRED_WITHIN_60D`, and `REVIEW_CLEANLINESS_SATISFACTION` rather than one overloaded
`CONVERTED` or `GOOD` label.

### Delayed, corrected, and censored outcomes

A booking confirmation is an earlier label than completed-stay satisfaction. Cancellation, refund,
chargeback, moderation appeal, damage claim, or support remedy can mature months later. Training runs
declare a cutoff that gives each target its full horizon or explicitly model censoring.

Label corrections create a new label version or revision linked to the prior observation. Previously
released models remain historically reproducible and are evaluated for impact; their old training
manifest is not edited.

### Selective labels and human feedback

Fraud, moderation, support, and ranking outcomes are often observed only after an earlier system
selected a case, listing, or treatment. “Not reviewed” is not equivalent to “safe,” and “not booked”
provides no satisfaction label. Dataset builders preserve selection policy/model versions and use
exploration, randomized audit samples, positive-unlabeled methods, or other reviewed approaches where
appropriate.

Human decisions include reviewer identity/role class, policy version, evidence available at the
time, confidence, appeal/reversal, and quality audit. Raw agent overrides do not become clean labels
automatically. Inter-reviewer disagreement and policy changes are measured.

### Negative sampling and weighting

Implicit-feedback models must define impression eligibility, sampling frame, position/exposure,
observation window, and weights. An unrendered listing is not a negative. A rendered but unavailable
listing indicates an instrumentation defect, not preference. Repeated activity from one actor or
manipulation cluster is bounded rather than allowed to dominate training.

## Feature platform

### Feature definition registry

Each feature definition records:

- stable key and semantic version;
- entity keys and optional request/context keys;
- data type, allowed range/categories, missing representation, and default policy;
- exact event-time computation and source contracts;
- freshness expectation, time-to-live, lookback, and update mode;
- privacy class, purpose, prohibited consumers, retention, and deletion behavior;
- owner, validation tests, skew checks, and monitoring thresholds;
- availability in offline, batch-serving, and online-serving contexts; and
- deprecation/replacement relationship.

Feature names express meaning and window, for example
`guest_confirmed_bookings_count_365d_v1`, not `user_score_7`. Raw personally identifying fields and
opaque third-party risk scores are not copied into general feature namespaces.

### Point-in-time lookup

For training example `i` with prediction time `t_i`, each feature value must satisfy:

```text
source_occurred_at <= t_i
source_available_at <= t_i, when online availability matters
feature_valid_from <= t_i < feature_valid_to, for effective-dated values
```

The lookup selects the latest permitted value by entity/context and definition version. If no value
qualifies, it returns explicit missing state and approved imputation metadata. Current-table joins
that can see future edits are prohibited for historical training.

### Offline and online parity

Prefer one reviewed computation specification with separate execution adapters rather than two
independent formulas. Parity tests replay the same fixtures through offline and online paths and
compare values, missingness, categories, timestamps, and rounding.

Some online signals, such as session clicks, may not exist in warehouse time. Their logging must
capture the exact serving value or enough ordered source observations to reproduce it. Any
non-reproducible feature is explicitly marked and restricted from consequential use.

### Materialization modes

- **Request-computed** features use bounded domain facts or request context under the serving
  deadline.
- **Batch** features are computed on a schedule and expose `asOf`, watermark, and freshness.
- **Incremental** features update from events using idempotent reducers.
- **Static/effective-dated** features use versioned configuration or taxonomy.

The MVP can store bounded latest feature projections in PostgreSQL or consumer-owned tables and
historical values in analytical storage. A dedicated online feature store is justified only when
multiple models require shared low-latency features and measured parity/freshness problems outweigh
its operational cost.

### Feature access and invalidation

Consumers authenticate as a service/use case and request an approved feature set. Authorization
filters by purpose, market, subject consent, model approval, and privacy class. A model artifact cannot
request features not declared in its registered feature set.

Corrections, deletion, consent withdrawal, source invalidation, and definition retirement produce
invalidation records. Serving either removes the value, returns suppressed/missing, or blocks the
model according to policy. Caches include feature/version/as-of identity and never outlive the most
restrictive source requirement.

## Training and evaluation

### Training-set manifest

Every dataset build creates an immutable manifest containing:

- dataset/version ID, purpose, owner, and approved model family;
- example population, sampling, split, target/horizon, and label version;
- prediction-time rule and point-in-time feature-set versions;
- source snapshot IDs, watermarks, code/config hashes, and random seeds;
- privacy/consent query, exclusions, deletion watermark, and retention;
- class weights, censoring, leakage checks, and known limitations;
- row/subject counts and slice distributions; and
- artifact location/checksum with access policy.

Random row splits are usually invalid for time-dependent marketplace behavior. Prefer temporal
train/validation/test splits and entity-aware grouping to prevent the same booking, listing, host,
guest, or near-duplicate content from leaking across splits.

### Baselines and candidate evaluation

Every model is compared with:

- the current deterministic or heuristic production behavior;
- a simple interpretable statistical baseline where suitable;
- the current champion model if one exists; and
- “no prediction” or stale/missing-feature fallback cases.

Offline evaluation covers target-appropriate discrimination/ranking error, calibration,
uncertainty, business utility under constraints, latency/resource cost, stability, and defined
fairness/market slices. A better aggregate Area Under the Curve (AUC) or Normalized Discounted
Cumulative Gain (NDCG) alone is insufficient.

### Evaluation by model family

| Family | Offline evidence | Online or operational guardrails |
| --- | --- | --- |
| Query/retrieval | recall at bounded candidate size, language/geography slices | zero-result, latency, hard-filter correctness |
| Learning-to-rank | NDCG/recall, calibration, position-bias correction | completed stay, satisfaction, concentration, cancellations |
| Review extraction | span/target/aspect precision/recall, factual grounding | correction, appeal, subgroup/language quality |
| Forecasting | rolling-origin error, interval coverage, hierarchy coherence | host overrides, stability, realized error |
| Price elasticity/uplift | causal identification checks, policy simulation | host net, guest total, fairness, cancellation, margin |
| Fraud/safety | precision-recall, calibration, cost by threshold, delay | false-positive appeal, incident/chargeback, latency |
| Support | routing accuracy, severity recall, grounded recommendation | SLA, escalation, remedy quality, agent override |
| Messaging | delivery/engagement prediction and uplift | opt-out, complaint, critical-delivery reliability |

### Fairness evaluation

The review defines prohibited sensitive features and likely proxies, legitimate business need,
affected parties, harm types, slices, metrics, thresholds, mitigations, and appeal/override path.
Where sensitive attributes cannot or should not be collected, use approved audits, geographic or
counterfactual proxy analysis with explicit limitations, and qualitative review; do not claim that
fairness is proven by absence of measured attributes.

Evaluate both guest and host effects: opportunity/exposure, error rates, price/benefit parity for
equivalent contexts, false-positive restrictions, new-supply treatment, language coverage, and
feedback-loop concentration. Small groups use privacy-preserving thresholds without hiding severe
safety harms.

### Robustness and adversarial evaluation

Test malformed/missing inputs, distribution shifts, coordinated clicks/reviews, duplicated listings,
prompt injection in user content, model extraction attacks, feature poisoning, provider outages,
and fallback. External pretrained or hosted models require license, provenance, security, privacy,
residency, retention, and provider-training review.

## Model registry and lifecycle

### Registered model version

A registered model version contains:

- model key/version and immutable artifact checksum;
- family, target, horizon, intended decisions, and prohibited uses;
- training-set manifest, code/environment/dependency versions, and random seeds;
- feature-set and label versions;
- offline evaluation, slices, limitations, and model card;
- inference contract, input validation, output schema, latency/cost budget, and fallback;
- owner, approvers, approval evidence, status, activation scope, and timestamps;
- monitoring thresholds, retraining/retirement triggers, and rollback target; and
- provider/model license, data-processing, retention, and security details when external.

Status follows:

```text
DRAFT -> TRAINED -> VALIDATED -> APPROVED -> SHADOW -> CANARY -> ACTIVE
                     |              |           |         |
                     +-> REJECTED   +-> RETIRED +-> ROLLED_BACK
```

Only approved versions may enter shadow/canary. One active version exists per explicitly scoped
consumer/market/decision route unless champion/challenger behavior is itself registered.

### Model card and approval

The model card states purpose, architecture at an appropriate level, data period/population,
features/categories, labels, performance, calibration, uncertainty, fairness and robustness results,
known limitations, excluded uses, human/domain controls, privacy/security review, operational budget,
and monitoring plan.

Consequential safety, financial, pricing, eligibility-adjacent, or moderation uses require domain,
risk/privacy/security, and relevant legal/finance approval. An ML owner cannot self-approve a material
expansion of model authority.

### Promotion and rollback

Promotion is an audited command with expected source version, scope, traffic share, start time,
guardrails, and rollback target. Artifacts are immutable; changing weights or prompt configuration
creates a new version. Rollback changes routing to the prior approved model or deterministic fallback
and preserves prediction/decision history.

### Retirement

A model is retired when target semantics, label policy, feature availability, market behavior,
provider terms, fairness results, or operating assumptions become invalid. Retirement blocks new
predictions but retains restricted metadata and artifacts as required for historical audit. Consumers
must handle `MODEL_RETIRED` without unsafe continuation.

## Prediction and decisioning

### Prediction contract

An online or batch prediction returns:

```json
{
  "predictionId": "uuid",
  "modelKey": "booking-propensity",
  "modelVersion": "bp-2026-09-01.3",
  "featureSetVersion": "discovery-v4",
  "predictionAt": "2026-09-06T10:00:00Z",
  "target": "BOOKING_CONFIRMED_WITHIN_7D",
  "horizon": "P7D",
  "outputs": {"probability": 0.42},
  "uncertainty": {"status": "WITHIN_VALIDATED_RANGE"},
  "reasonCodes": ["TRIP_PRICE_COMPETITIVE", "CLEANLINESS_MATCH"],
  "freshness": {"oldestRequiredAsOf": "2026-09-06T09:55:00Z"},
  "status": "PREDICTED",
  "fallback": null
}
```

The request uses a canonical context ID/hash, target, consumer, market, event time, required deadline,
and idempotency key. The model runtime validates schema, consent, feature presence/freshness, approved
scope, and traffic routing before execution.

### Deterministic decision boundary

The consuming domain follows:

```text
authoritative facts + explicit user/host intent + effective policy
              + optional versioned prediction
              -> deterministic constraints and action selection
              -> domain-owned decision and explanation
```

The decision records policy version, relevant factual snapshot IDs, prediction ID or fallback reason,
experiment assignment/exposure reference, constraint results, selected action, reason codes, actor,
and correlation. Repeating the same idempotent decision command returns the committed result even if
a newer model is now active.

### Deadlines and fallback

Each consumer declares a total inference budget and safe fallback:

- discovery falls back to a deterministic rank;
- pricing falls back to configured host/base rules and deterministic constraints;
- review extraction queues or preserves the original without generated intelligence;
- fraud/risk uses approved deterministic rules and may route to human review rather than approve;
- support uses normal queues and human triage; and
- messaging uses template/rule routing for critical notices.

A timeout does not trigger an unbounded synchronous retry. The prediction records `TIMED_OUT`,
`FEATURE_MISSING`, `FEATURE_STALE`, `MODEL_UNAVAILABLE`, `OUT_OF_SCOPE`, `CONSENT_DENIED`, or another
stable fallback reason. Fail-open versus fail-closed is owned by consumer policy and reviewed per use
case.

### Batch inference

Batch output includes entity/context, prediction time, valid interval, model/feature versions,
prediction, uncertainty, and run lineage. Consumers reject expired or superseded predictions. Batch
scores are projections, not durable actor attributes, and are removed/rebuilt on feature correction,
consent change, or model retirement.

### Explanations

User-visible explanations derive from approved structured facts and reason templates. They do not
reveal protected traits, sensitive profile inferences, fraud rules, raw probabilities, another
party's private information, or exploitable thresholds. Internal explanations distinguish feature
contribution from causal reason; neither is presented as certainty.

### Model-family boundaries

| Model family | D20 may produce | Consumer remains authoritative for |
| --- | --- | --- |
| Query understanding | intent/entity candidates and confidence | Destination selection and eligible retrieval |
| Semantic retrieval | similarity candidates/scores | Publication, filters, availability, final candidate set |
| Guest preference | contextual preference features | Explicit filters, profile controls, final rank |
| Learning-to-rank | relevance/propensity scores | Ordering policy, diversity, sponsorship, explanation |
| Review intelligence | spans, aspects, sentiment, summary draft, uncertainty | Review truth, visibility, aggregates, published claims |
| Forecast/demand | expected demand/occupancy interval | Calendar truth and host-controlled availability |
| Price elasticity | response curve/uplift estimate | Candidate generation, floor/ceiling/net, tax, quote amount |
| Promotion uplift | incremental-outcome estimate | Eligibility, budget, funding, disclosure, redemption |
| Fraud/chargeback/damage | calibrated risk estimates | Challenge, restriction, denial, appeal, payout hold |
| Support | routing/severity/recommendation | Safety escalation, remedy, communication, case decision |
| Messaging | channel/time/engagement estimate or draft | Consent, critical delivery, approved template/content |
| Content/image/spam | classification/candidate match | Publication/moderation action and appeal |

## Feedback loops and decision evaluation

### Prediction-to-outcome join

Evaluation joins prediction and domain decision to mature outcomes using immutable IDs and declared
horizons. It distinguishes model requested, model returned, fallback used, action selected, treatment
exposed, and outcome observed. Evaluating only successful predictions hides outage and fallback harm.

### Policy and model effects

Observed outcomes reflect both model score and policy action. A model cannot be credited for a
decision the policy overrode, and an appeal reversal may indicate policy, label, feature, or model
failure. Decision logs retain both layers for replay and counterfactual simulation.

### Feedback-loop controls

Ranking controls position bias and popularity reinforcement; pricing controls endogeneity and host
response; fraud controls selective review and adversarial adaptation; support controls agent
automation bias; review intelligence controls language and publication selection. Mitigations include
randomized audits, bounded exploration, inverse-propensity or doubly robust evaluation when valid,
stable holdouts, exposure caps, diversity floors, and qualitative review.

Generated model outputs never become new labels merely because users later saw them. For example, an
LLM summary cannot train against itself as factual review truth without independent evidence.

## Build-versus-buy boundaries

| Capability | Provider may supply | Room Booking must own |
| --- | --- | --- |
| Warehouse/lake | Storage, compute, catalog primitives | Data contracts, privacy, metrics, quality, portability |
| Event/stream platform | Durable transport, partitions, connectors | Source commit, envelope, idempotency, replay policy |
| Experiment product | Assignment/analysis mechanics | Eligibility, exposure semantics, guardrails, decisions |
| Feature platform | Materialization and serving primitives | Definitions, purpose/access, point-in-time correctness |
| Training compute | Managed jobs and accelerators | Dataset/label validity, code, evaluation, approval |
| Model registry/serving | Artifact storage, routing, autoscaling | Authority limits, release policy, fallback, audit |
| Foundation/LLM model | Base model and inference API | Data sent, grounding, moderation, evaluation, final action |
| Monitoring | Infrastructure and statistical tooling | Thresholds, owner, incident response, retirement decision |

Provider-native IDs, artifact hashes, configuration, sanitized observations, costs, residency, and
retention evidence are retained. Export and fallback plans avoid making transactional correctness
dependent on one provider's proprietary dashboard.

## Conceptual data model

No tables below exist today. Implementation uses new forward Liquibase migrations after the
transactional outbox and privacy model are approved. Logical entities may share physical tables at
MVP scale but keep the ownership and constraints below.

### Event and ingestion records

| Record | Important fields and constraints |
| --- | --- |
| `event_definitions` | Stable name/version, class, owner, schema hash/body reference, privacy, compatibility, status; unique name/version |
| `outbox_events` | Event/envelope identity, aggregate/version, payload/reference, status, attempt/lease; unique event ID and optional aggregate-event business key |
| `consumer_inbox` | Consumer/contract/event, processed result/time; unique consumer + contract version + event ID |
| `event_arrivals` | Event ID, source, received/ingested times, checksum, validation, duplicate-of, quarantine; append-only |
| `privacy_subject_links` | Restricted source/subject pseudonyms, purpose, effective interval, deletion/suppression; no general access |
| `data_corrections` | Target dataset/event/key, correction type/reason, replacement, effective time, actor, audit |
| `pipeline_runs` | Product/version, input/output snapshots and watermarks, code/config hash, quality state, counts, timing |
| `data_quality_results` | Run/check/version, severity, observed/expected, sample reference, status, owner/action |
| `data_product_registry` | Dataset key/version, grain/schema, owner, SLA, privacy, retention, lineage, status |

Hot outbox indexes should cover unpublished due rows and aggregate/version lookup. Inbox uniqueness is
the final duplicate defense. Large analytical arrivals must move out of the transactional database
before retention or write volume threatens booking workloads.

### Metric and experiment records

| Record | Important fields and constraints |
| --- | --- |
| `metric_definitions` | Metric key/version, formula reference, grain/population/windows, owner, sources, status |
| `metric_materializations` | Metric/version, slice/window, value, unit/currency, data cutoff, quality, run/snapshot |
| `experiment_definitions` | Stable experiment key, owner, hypothesis, namespace, status |
| `experiment_epochs` | Immutable population, unit, variants, allocation, exposure rule, metrics, guardrails, start/stop; unique experiment/epoch |
| `experiment_assignments` | Epoch, unit type/pseudonym, variant, allocator version, assigned time, eligibility hash; unique epoch/unit |
| `experiment_exposures` | Exposure ID/dedupe key, assignment, surface, actual treatment/fallback, event/receive time, context refs |
| `experiment_analysis_runs` | Epoch, plan version, cutoff, method, quality/SRM status, estimates/intervals, code hash, conclusion |
| `experiment_actions` | Pause/stop/rollout/rollback command, expected version, actor/approval, reason, audit |

Assignments and exposures are immutable. Epoch activation uses optimistic versioning and a database
constraint preventing overlapping active allocation within an exclusive namespace/scope where it can
be expressed locally.

### Feature, training, model, and prediction records

| Record | Important fields and constraints |
| --- | --- |
| `feature_definitions` | Feature key/version, entity/context schema, type, computation, sources, freshness, privacy, owner, status |
| `feature_set_versions` | Set key/version and immutable member definition versions; unique set/version/member |
| `offline_feature_values` | Entity/context, feature/version, as-of/source-available time, value/reference, provenance, suppression |
| `online_feature_values` | Entity/context, feature/version, value, as-of, expiry, provenance/version; rebuildable |
| `label_definitions` | Label key/version, target/horizon, source/maturity/censor/correction rules, owner, status |
| `label_observations` | Example/entity/context, label/version, prediction time, observed/matured time, value/status, source revisions |
| `training_dataset_manifests` | Dataset/version, target, population, feature set, source snapshots, code, privacy/deletion watermark, checksums |
| `model_versions` | Model/version, artifact hash/reference, dataset/feature/label versions, evaluations/card, owner, status |
| `model_release_routes` | Consumer/scope, champion/challenger, share, epoch, fallback, version; optimistic lock |
| `prediction_records` | Prediction ID, canonical request/dedupe, target, model/feature versions, output, uncertainty, status/fallback, time, expiry |
| `model_evaluation_runs` | Model/version, dataset/cutoff, metrics/slices, calibration/drift/fairness, code hash, result |
| `model_actions` | Approve/promote/pause/rollback/retire, actor/approver, expected version, scope, reason, audit |

Large feature values, datasets, and artifacts live in approved analytical/object storage; relational
records store manifests, checksums, access, and lifecycle. Artifact references are content-addressed
or otherwise immutable. Prediction payloads are bounded and avoid raw feature dumps.

### Decision references

Each consuming domain stores or emits its own `decisionId`, policy version, factual snapshot
references, prediction ID or fallback, experiment/exposure reference where applicable, action, and
reason codes. D20 may keep a cross-domain index for monitoring but does not own or mutate the decision.

### Migration, backfill, and deployment implications

1. Add the common registry, outbox/inbox, and restricted audit foundations in new forward migrations.
2. Dual-run existing after-commit email events only if their migration into durable notifications is
   explicitly scoped; do not claim durability before then.
3. Onboard one producer and consumer at a time with schema contract tests and reconciliation.
4. Backfill authoritative historical facts from source tables with synthetic event IDs, explicit
   `BACKFILL` provenance, source snapshot/watermark, and no false original emission time.
5. Never fabricate client impressions or experiment exposures for periods without evidence.
6. Build semantic versions in shadow and compare with reference cases before making them current.
7. Store model/dataset artifacts only after retention, encryption, and access controls exist.
8. Roll back routing or publication, not immutable historical evidence.

## Service boundaries

These are conceptual modules and do not require immediate microservice extraction.

| Module | Contract |
| --- | --- |
| `EventContractRegistry` | Register, validate, activate, deprecate, and resolve typed event contracts |
| `TransactionalOutboxRelay` | Lease due rows, publish at least once, and expose delivery evidence |
| `BehaviorEventCollector` | Validate, consent-check, rate-limit, deduplicate, and durably accept client observations |
| `AnalyticalIngestionService` | Persist arrivals, validate envelopes, dedupe conformed facts, quarantine failures |
| `IdentityAttributionService` | Resolve approved pseudonymous session/user links and deletion scopes |
| `DataTransformationOrchestrator` | Run versioned transformations with snapshots, watermarks, quality, and lineage |
| `MetricCatalog` | Own semantic definitions, compatibility, queries, and materialization metadata |
| `ExperimentService` | Validate experiments, assign stable variants, register actual exposure, control lifecycle |
| `ExperimentAnalysisService` | Build frozen analysis sets, run approved methods, publish qualified results |
| `FeatureRegistry` | Own feature contracts, sets, access, lifecycle, and computation references |
| `FeatureMaterializationService` | Produce offline/online values with as-of/freshness/provenance and invalidation |
| `TrainingDatasetBuilder` | Join features and mature labels point-in-time and create immutable manifests |
| `ModelRegistry` | Register artifacts/cards/evaluations, enforce approvals and lifecycle |
| `ModelTrainingOrchestrator` | Execute reproducible jobs without automatically approving output |
| `PredictionService` | Route approved models, fetch allowed features, enforce deadline, log result/fallback |
| `ModelMonitoringService` | Evaluate availability, latency, drift, calibration, fairness, outcome, and cost |
| `PrivacyPropagationService` | Apply purpose, retention, suppression, correction, and deletion across derivatives |
| `DataMLAdminService` | Provide audited configuration, review, pause, rollback, replay, and exception workflows |

Domain adapters translate source facts and consumer contexts without copying domain policy. One
shared platform team can own primitives while each feature/metric/model retains a named domain owner.

## API behavior

Public APIs are illustrative target contracts. Administrative and internal APIs require stronger
authentication, authorization, network isolation, audit, and optimistic version checks.

### Client interaction ingestion

```text
POST /api/v1/analytics/events:batch
```

The request contains a bounded list of event IDs, names/versions, occurred times, server-issued
context tokens, and allowlisted payloads. The server derives authenticated actor/session context,
application version, receive time, consent state, and origin where possible.

Example response:

```json
{
  "batchId": "uuid",
  "accepted": 12,
  "rejected": 1,
  "duplicate": 2,
  "results": [
    {"eventId": "uuid", "status": "ACCEPTED"},
    {"eventId": "uuid", "status": "REJECTED", "code": "EVENT_CONTEXT_INVALID"}
  ]
}
```

The response does not reveal another actor's resources, experiment eligibility, fraud controls, or
server-side truth. Retrying the same event ID returns its accepted/duplicate outcome.

### Experiment operations

```text
POST /api/v1/internal/experiments/{experimentKey}/assignments
POST /api/v1/internal/experiments/{experimentKey}/exposures
GET  /api/v1/internal/experiments/{experimentKey}/assignment

POST /api/v1/admin/experiments
POST /api/v1/admin/experiments/{id}/epochs
POST /api/v1/admin/experiment-epochs/{id}/review
POST /api/v1/admin/experiment-epochs/{id}/start
POST /api/v1/admin/experiment-epochs/{id}/pause
POST /api/v1/admin/experiment-epochs/{id}/stop
GET  /api/v1/admin/experiment-epochs/{id}/analysis
```

The assignment caller supplies the experiment key, declared unit/context, eligibility evidence
reference, and idempotency key. It cannot choose a variant. Exposure requires an existing assignment,
actual-delivery status, surface, server-issued context, and deduplication key.

Lifecycle commands require expected version, reason, actor, and approval where configured. `start`
fails if contracts, guardrails, analysis plan, collision checks, or owners are incomplete.

### Feature and prediction operations

```text
POST /api/v1/internal/features:resolve
POST /api/v1/internal/predictions
GET  /api/v1/internal/predictions/{predictionId}

POST /api/v1/admin/features
POST /api/v1/admin/feature-sets
POST /api/v1/admin/models
POST /api/v1/admin/models/{id}/approve
POST /api/v1/admin/model-routes/{id}/promote
POST /api/v1/admin/model-routes/{id}/rollback
POST /api/v1/admin/models/{id}/retire
```

Feature access identifies consumer/purpose, entity/context references, `asOf`, required set version,
and freshness. Prediction requests identify a registered target/consumer route and deadline; they do
not send arbitrary model paths or raw feature maps from clients.

An idempotent repeat returns the stored prediction/decision input identity. A new active model does
not change that response. Callers wanting a new prediction use a new canonical context/version.

### Metric and lineage operations

```text
GET  /api/v1/admin/data-products/{key}/versions/{version}
GET  /api/v1/admin/metrics/{key}/versions/{version}
GET  /api/v1/admin/pipeline-runs/{id}/lineage
GET  /api/v1/admin/data-quality/incidents
POST /api/v1/admin/data-quality/incidents/{id}/acknowledge
POST /api/v1/admin/replays
POST /api/v1/admin/privacy-propagation-jobs
```

Ad hoc SQL, raw event payloads, training rows, and unrestricted feature values are not general REST
resources. They use separately controlled analytical environments and export approval.

### Authoritative and client-supplied inputs

| Input | Authority |
| --- | --- |
| Event ID and local occurred time | Client-proposed, validated and bounded |
| User identity, consent, market, application origin | Server/policy derived |
| Search/result/rank context | Server-issued signed or persisted reference |
| Booking/payment/stay/review outcome | Owning transactional domain only |
| Experiment unit/eligibility | Calling domain plus experiment definition; variant chosen by D19 |
| Feature set/model route | Registered and server-selected |
| Final domain action | Consuming domain, never client or D20 |

### Error semantics

Stable examples include:

| Code | HTTP behavior | Retry guidance |
| --- | --- | --- |
| `EVENT_SCHEMA_UNSUPPORTED` | `400` | Upgrade instrumentation; do not retry unchanged |
| `EVENT_CONTEXT_INVALID` | `400` or enumeration-safe `404` | Refresh server context |
| `EVENT_BATCH_TOO_LARGE` | `413` | Split within declared bounds |
| `EVENT_RATE_LIMITED` | `429` | Retry with bounded backoff if still within age |
| `DATA_PURPOSE_NOT_ALLOWED` | `403` | Do not retry without policy change |
| `EXPERIMENT_NOT_ACTIVE` | `409` | Use control/fallback |
| `EXPERIMENT_UNIT_INVALID` | `400` | Correct caller contract |
| `EXPERIMENT_CONFLICT` | `409` | Resolve namespace/configuration |
| `EXPERIMENT_VERSION_CONFLICT` | `409` | Reload current epoch/version |
| `EXPOSURE_ASSIGNMENT_MISSING` | `409` | Obtain assignment or record diagnostic only |
| `FEATURE_SET_NOT_APPROVED` | `409` | Use approved version |
| `FEATURE_NOT_AVAILABLE_AS_OF` | `200` with missing state or `424` | Consumer follows registered fallback |
| `FEATURE_STALE` | `200` with stale state or `424` | Fallback; do not spin-retry |
| `MODEL_NOT_APPROVED` | `409` | Use approved route |
| `MODEL_OUT_OF_SCOPE` | `422` | Correct target/market/consumer |
| `PREDICTION_DEADLINE_EXCEEDED` | `200` fallback or `504` internally | Apply deterministic fallback |
| `PREDICTION_UNAVAILABLE` | `200` fallback or `503` internally | Apply fallback; retry only outside decision path |
| `QUALITY_GATE_FAILED` | `409` | Quarantine/block dependent publication |
| `REPLAY_SCOPE_UNSAFE` | `422` | Narrow scope or approve side-effect suppression |

Public clients receive generic safe explanations. Internal responses may include opaque incident,
version, and lineage IDs but not sensitive features, experiment salts, model extraction detail, or
another party's data.

## Event contracts

Events are committed past-tense facts. Producers insert outbox rows in the transaction that commits
the source fact. Consumers assume duplicate, delayed, and out-of-order delivery and use an inbox or
equivalent durable deduplication.

### Platform events

| Event | Producer | Main consumers and meaning |
| --- | --- | --- |
| `EventContractActivated` | Contract registry | Producers/consumers may use one approved schema version |
| `AnalyticalEventAccepted` | Collector/ingestion | Restricted operational evidence; not a source-domain fact |
| `AnalyticalEventRejected` | Collector/ingestion | Quality/security triage without retaining prohibited payload |
| `DataCorrectionRegistered` | Data governance | A bounded source/dataset meaning is superseded or suppressed |
| `DataProductMaterialized` | Transformation orchestrator | New immutable snapshot/version passed quality gates |
| `DataQualityGateFailed` | Quality service | Dependent publication or model route may need pause/fallback |
| `MetricDefinitionActivated` | Metric catalog | A semantic version is approved for named uses |
| `ExperimentEpochStarted` | Experiment service | Immutable epoch began accepting assignments |
| `ExperimentUnitAssigned` | Experiment service | One declared unit received a stable variant |
| `ExperimentExposureRecorded` | Experiment service | Treatment could affect unit under rule version |
| `ExperimentEpochPaused` / `ExperimentEpochStopped` | Experiment service | No new treatment exposure within scope |
| `ExperimentAnalysisPublished` | Analysis service | Qualified result plus cutoff/method/quality, not automatic rollout |
| `FeatureDefinitionActivated` | Feature registry | One computation/version is approved for named purposes |
| `FeatureMaterializationCompleted` | Feature service | Versioned values and watermark are available |
| `FeatureValuesInvalidated` | Feature/privacy service | Values must no longer be served or used for future training |
| `TrainingDatasetBuilt` | Dataset builder | Immutable manifest/artifact completed and passed gates |
| `ModelVersionRegistered` | Model registry | Artifact and lineage recorded, not yet approved |
| `ModelVersionApproved` | Model governance | Model may enter named shadow/canary scope |
| `ModelRouteChanged` | Model registry | Active/shadow/challenger routing changed under audit |
| `PredictionProduced` | Prediction service | Versioned output returned; consumer still owns action |
| `PredictionFellBack` | Prediction service | Named failure/default occurred and must be evaluated |
| `ModelDriftDetected` | Monitoring | Threshold crossed; owner evaluates pause/retrain/retire |
| `ModelVersionRetired` | Model governance | No new inference is permitted for the retired scope |
| `PrivacyPropagationCompleted` | Privacy service | Declared derivatives applied requested action with evidence |

High-volume interaction observations need not each be republished as internal platform events after
accepted ingestion. Their stored envelope and batch manifest are sufficient when downstream
processing can consume them safely.

### Minimum event identity

Every platform event includes event ID/name/schema, producer, occurred/committed times, aggregate or
record ID/version, correlation/causation, actor/process, privacy class, market/scope when applicable,
and minimal payload identifiers. Lifecycle events include prior/new status and expected version.
Artifact/dataset events include immutable hashes and manifests rather than embedding large content.

### Event evolution and replay

Consumers register supported schema ranges and behavior for unknown enum values. Replay selects
source range, original schema, target consumer/code version, side-effect policy, privacy/deletion
watermark, and correlation ID. Production side effects are suppressed unless the consumer contract
explicitly defines idempotent re-application.

A replay creates a run record with counts, failures, output snapshot, and comparison to current
state. It does not rewrite original event times, assignments, exposures, predictions, or domain
decisions.

## Concurrency and idempotency

### Stable keys

| Operation | Idempotency or uniqueness scope | Repeated behavior |
| --- | --- | --- |
| Write outbox fact | Source business event/type/version | Return/retain same event ID; no second logical fact |
| Consume event | Consumer + contract version + event ID | Return prior application/no-op |
| Accept client event | Origin/application + event ID | Return accepted/duplicate status |
| Run transformation | Product/version + input snapshot/watermark + code/config hash | Return same run/output or explicit failed run |
| Materialize metric | Metric/version + slice/window + source snapshot | One published value version |
| Assign experiment | Epoch + canonical unit | Same variant and assignment ID |
| Record exposure | Epoch + assignment + surface + declared exposure dedupe key | Same exposure/no duplicate count |
| Build feature value | Feature/version + entity/context + as-of/source version | Deterministic value or linked correction |
| Build dataset | Dataset specification hash + source snapshots | Same manifest/artifact identity |
| Register model | Model key/version or artifact hash under declared policy | Same record or conflict on different artifact |
| Predict | Consumer + target + canonical context/version + request key | Same stored prediction/fallback |
| Change route | Route/scope + command key + expected version | Same result or version conflict |
| Propagate deletion | Subject-scope + request/version | Resume/return same job and proof |

### Race behavior

- Two concurrent experiment assignment requests race on the unique epoch/unit constraint; the winner
  creates the assignment and the loser reads it.
- Start/pause/stop and allocation edits use optimistic versions. No in-place allocation edit is
  allowed after first assignment; create a new epoch.
- Two route promotions use optimistic locking and an approval record. Only one champion becomes
  active for the exact scope/version.
- Feature update reducers compare source event/aggregate version and event time. Older input cannot
  overwrite a newer projection unless processing a declared historical rebuild.
- Training runs pin immutable snapshots and deletion watermark before reading. Later deletion marks
  the manifest invalid for reuse and triggers policy action; it does not mutate the artifact silently.
- Prediction logging is committed independently of the consumer's domain transaction. The consumer
  embeds the prediction ID/output hash or fallback in its own decision so loss of later analytics
  cannot change the action.
- A model route may change between request start and completion. The resolved route/model version is
  pinned once per canonical request and recorded.

### Locking and transaction boundaries

Use short transactions and a consistent order: definition/route record, assignment or feature row,
audit, outbox. Do not lock transactional booking, ledger, payment, or review rows from a platform
transaction. Cross-domain coordination uses committed IDs/events or purpose-built read contracts.

Workers claim bounded batches with `FOR UPDATE SKIP LOCKED` or equivalent leasing, `leaseOwner`,
`leaseUntil`, attempt count, and fencing token. Expired leases are reclaimable. No database lock is
held across broker, object-storage, warehouse, training, or hosted-model network calls.

### Database defenses

Database constraints protect event/assignment/inbox/model/route uniqueness, valid lifecycle states,
non-negative attempts, interval ordering, one immutable feature-set membership, and referential
integrity. Application checks provide explanations but do not replace constraints. Analytical stores
add merge keys and quality tests even when they lack transactional uniqueness.

## Security, privacy, and access control

### Roles and separation of duties

Recommended capabilities include:

- event producer and consumer identities scoped to named contracts;
- data engineer access to approved pseudonymous products, not production credentials;
- analyst access to semantic/research products, not raw unrestricted payloads;
- experiment author, reviewer, launcher, and emergency stopper;
- ML developer, model validator, model approver, and route operator;
- privacy steward for purpose/retention/deletion policy;
- security/risk reviewer for adversarial and sensitive use;
- domain owner for decision authority and fallback; and
- audited break-glass operator with time-bound access.

Material model/experiment changes use maker-checker where risk warrants it. Production launch
credentials are separate from development/training identities.

### Data and artifact protection

Encrypt data in transit and at rest. Store warehouse, object, registry, model-provider, and signing
credentials in a secret manager; rotate and scope them. Artifacts and datasets use immutable hashes,
malware/provenance scanning, signed build evidence where appropriate, access logs, and environment
separation.

Never deserialize untrusted model artifacts inside privileged application processes. Serving images
or containers use pinned dependencies, vulnerability scanning, resource limits, egress controls, and
reproducible build metadata.

### Injection, exfiltration, and abuse

Validate event payload schemas and reject executable content. Free text sent to a language model is
untrusted data; prompts/tools must not grant it authority. Apply prompt-injection defenses,
allowlisted retrieval, bounded output schemas, content moderation, and no secret-bearing context.

Protect assignment endpoints and salts from manipulation. Rate-limit event ingestion, prevent users
from choosing their variant or prediction inputs, detect replay/coordinated traffic, and separate
test/internal traffic. Do not expose raw scores or thresholds that materially help fraud evasion.

### Logging and audit

Logs use opaque IDs, versions, status, sizes, latencies, and safe reason codes. They exclude feature
vectors, raw content, exact addresses, credentials, tokens, experiment salts, training rows, and
sensitive predictions. Privileged reads, exports, corrections, replays, approvals, launches,
rollbacks, and deletions create immutable audit records.

### Tenant, market, and environment isolation

Production/staging/development events and artifacts never mix. Market/legal-entity and organization
boundaries are carried where needed. Cross-market model reuse requires explicit approval that
taxonomy, language, law, population, calibration, and data-transfer constraints are compatible.

## Observability and operations

### Business and correctness metrics

- source facts versus ingested/conformed facts by event/version/market;
- duplicate, late, rejected, quarantined, missing, and corrected event rates;
- assignment distribution, sample-ratio mismatch, missing/duplicate exposure, cross-over, and
  incompatible experiment overlap;
- metric freshness, restatement count, definition adoption, and dashboard-version drift;
- point-in-time join violations, label maturity/correction, feature missing/stale/skew rates;
- prediction volume, fallback, outcome join coverage, calibration, drift, fairness, and consumer
  decision override by model/version/scope;
- model release/rollback frequency, time in shadow/canary, incident count, and stale active model;
- privacy deletion age/completion, orphan derivatives, unauthorized access/export, and retention
  expiry; and
- data/compute/storage/provider cost by product, experiment, model, market, and environment.

Marketplace outcome dashboards pair conversion with completion, satisfaction, cancellation, refund,
incident, host earnings, exposure concentration, latency, and long-term retention. Financial metrics
name their ledger/finance definition and close status.

### Technical metrics

- outbox oldest age, due count, attempts, lease expiry, and relay throughput;
- collector request/event volume, latency, error, payload size, and throttling;
- ingestion and transformation lag by watermark and partition;
- warehouse/job queue time, run duration, retry, failed partition, and resource consumption;
- feature materialization lag, online lookup latency/error/cache hit, and invalidation age;
- training duration/failure, artifact size, registry availability, and dependency reproducibility;
- inference p50/p95/p99 latency, timeout/error/saturation, cold start, and provider quota;
- model input/output distribution, feature drift, data-quality dependency state, and cost per request;
- replay/deletion backlog, dead-letter/quarantine age, and recovery throughput.

### SLO candidates

Final values require traffic and domain review. Candidate objectives are:

- 99.9% of committed outbox facts become durably visible to required consumers within five minutes,
  excluding declared disaster windows;
- accepted client interactions reach conformed storage within one hour for product analytics;
- daily governed products publish by their declared business cutoff with `PASS` quality;
- experiment assignment p99 meets the consuming surface budget and always has deterministic control
  fallback;
- online prediction availability/latency is defined per consumer, never as one misleading global SLO;
- privacy serving suppression takes effect quickly, with durable derivative deletion completed within
  the approved legal/policy deadline; and
- critical quality or sample-ratio failures stop dependent automated rollout within the documented
  response time.

### Alerts and dashboards

Page on sustained source-publication loss, safety/financial consumer gaps, material sample-ratio
mismatch in a live high-risk experiment, widespread prediction failure without safe fallback,
privacy propagation breach, unauthorized export, or runaway spend. Ticket or notify owners for less
urgent freshness, drift, correction, and deprecation issues.

Dashboards expose definition/version, source cutoff/watermark, quality, freshness, market, and known
limitations. A green pipeline dashboard does not imply a model or business result is healthy.

### Operational tools and runbooks

Operators need bounded commands to pause an experiment, route to fallback, retire a model, quarantine
a producer/version, retry an idempotent partition, replay a consumer in dry-run, invalidate features,
and resume a deletion job. Tools show predicted scope and require confirmation/approval according to
risk; they never invite direct database editing.

Runbooks cover event loss/gap, schema incompatibility, bad metric release, sample-ratio mismatch,
feature skew/staleness, model drift/regression, provider outage/quota, artifact compromise, privacy
incident, cost spike, and disaster restore/replay.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Source transaction rolls back | No domain fact or outbox event exists |
| Process crashes after source commit | Durable outbox relay resumes; source fact remains correct |
| Relay publishes twice | Inbox/conformed dedupe prevents duplicate effect/count |
| Event arrives out of order | Use aggregate version/event time; buffer, rebuild, or quarantine gaps according to contract |
| Event schema is unknown | Reject/quarantine safely; do not guess semantics |
| Client sends forged outcome | Reject; authoritative outcome comes from source domain |
| Collector unavailable | Client buffers within bounded policy; transactional journeys continue |
| Analytics sink unavailable | Outbox/landing backlog applies backpressure away from transactional pools; source domains continue |
| Bad producer deployment | Quarantine affected version, stop dependent publication, roll producer back, reconcile/backfill |
| Transformation crashes mid-run | Output stays unpublished; retry pinned inputs idempotently |
| Late/corrected source fact | Restate open versions with lineage; flag affected reports/models/experiments |
| Warehouse snapshot is incomplete | Quality `FAIL/UNKNOWN`; do not publish qualified metric/model dataset |
| Identity link is wrong | Add reversal, rebuild affected derivatives, audit downstream exposure |
| Consent withdrawn/deletion requested | Suppress serving, queue durable propagation, block future reuse, retain only approved exceptions |
| Experiment assignment service unavailable | Use registered control/default; do not randomize locally with a different rule |
| Exposure loss suspected | Mark analysis invalid/incomplete; do not impute treatment delivery silently |
| Sample-ratio mismatch | Pause interpretation/treatment according to severity; investigate before decision |
| Guardrail breach | Stop new treatment, preserve evidence, restore fallback, notify accountable owners |
| Feature materialization late | Return explicit stale/missing and consumer fallback; never substitute an undocumented value |
| Online/offline feature skew | Quarantine candidate model or route to fallback; repair and re-evaluate |
| Training job fails | No model is registered/approved from partial output |
| Artifact checksum/signature differs | Refuse load, isolate artifact, alert security/model owner |
| Registry unavailable | Continue pinned already-loaded approved model only if policy permits; block promotion/new route |
| Online model times out | Record fallback and return within consumer deadline |
| Hosted model provider fails | Use approved local/rule fallback; no uncontrolled provider retry in domain transaction |
| Prediction log write fails | Consumer can still use only if decision records required immutable output/version; open reconciliation |
| Model quality/drift threshold breaches | Pause/rollback or require owner review according to configured severity |
| Outcome labels are delayed | Keep evaluation censored/unknown until maturity; do not score as negative |
| Replay accidentally targets side effects | Dry-run/side-effect gate blocks it; require explicit reviewed mode and idempotency |
| Backup restore | Reconcile source facts, restore registries/artifacts, replay outbox/inbox, rebuild projections, prove deletion state before broad automation |

## Testing and verification

### Contract and deterministic tests

- Validate every registered envelope version with golden examples and incompatible-change fixtures.
- Verify hash-based experiment assignment is stable across language/runtime versions and handles unit
  normalization exactly.
- Verify metric SQL/computation against approved reference populations, timezones, currencies,
  deduplication, late events, and exclusions.
- Test feature computations for exact as-of boundaries, missingness, effective dates, windows,
  rounding, and source availability time.
- Reproduce training manifests and artifact hashes from pinned inputs/configuration/seeds within the
  declared determinism limits.
- Verify domain fallbacks and reason templates without any model dependency.

### Real-database and concurrency tests

- Source state and outbox insert commit or roll back together.
- Concurrent consumer deliveries produce one inbox application and one derived effect.
- Concurrent assignment requests produce one stable variant.
- Concurrent exposure retries produce one logical exposure under the configured dedupe rule.
- Epoch start/edit/pause and model route promotion/rollback respect optimistic version and state.
- Worker lease expiry, fencing, `SKIP LOCKED`, retry, and crash recovery converge.
- Feature reducers reject stale aggregate versions and preserve deterministic rebuild behavior.
- Deletion and materialization races cannot republish suppressed subject values.

### Data and lineage tests

- Reconcile event counts and finance-approved amounts to authoritative source fixtures.
- Inject duplicates, gaps, reordering, corrections, malformed payloads, new enums, clock skew, and late
  partitions.
- Assert failed inputs are quarantined and failed transformations never publish a current snapshot.
- Trace a metric, training row, model, prediction, and decision back to versioned inputs.
- Verify raw-to-semantic privacy classification never becomes less restrictive without approval.
- Test deletion across raw/conformed/semantic/feature/training/cache/export registries and proof.

### Experiment tests

- Test eligibility, assignment unit, allocation, namespace exclusion, merge, exposure, and dedupe.
- Simulate sample-ratio mismatch, missing exposure, variant crossover, bot imbalance, event loss, and
  guardrail breach.
- Validate power/sample planning and analysis implementations against known statistical fixtures.
- Test sequential/multiple-comparison policy and forbid unplanned stop conclusions.
- Verify intent-to-treat populations, censoring, attribution windows, clustering, and late outcomes.
- Run A/A experiments before using the platform for consequential rollout.

### Model and feature tests

- Detect target leakage, future joins, entity overlap, position bias, selection bias, and duplicate
  content across splits.
- Compare offline and online feature values from identical event histories.
- Evaluate simple baseline, champion, challenger, fallback, calibration, uncertainty, and cost.
- Exercise cold start, missing/stale features, unseen categories, language/market slices, drift,
  extreme values, adversarial traffic, and provider failure.
- Verify an unapproved/retired/out-of-scope model cannot serve and a model cannot request undeclared
  features.
- Shadow and canary tests prove prediction differences do not bypass consumer constraints.
- Validate fairness thresholds and qualitative review for affected guest/host cohorts.

### Security and privacy tests

- Cross-role, cross-market, cross-environment, and unauthorized dataset/model/experiment access.
- Secret, credential, exact address, private content, and prohibited-field scanning in events/logs.
- Event forgery, replay abuse, assignment manipulation, payload bombs, prompt injection, malicious
  artifact, dependency compromise, and model endpoint extraction/denial-of-service.
- Consent effective-date, personalization opt-out, identity unlink, deletion, legal-hold exception,
  and aggregate anti-differencing behavior.
- Audit completeness for access, export, correction, replay, launch, rollback, and deletion.

### Failure and recovery tests

- Crash before/after source commit, outbox claim, publish, inbox receipt, projection write, exposure,
  transformation publish, feature update, prediction, and decision commit.
- Broker/warehouse/object store/model provider timeouts and partial successes.
- Restore a production-like backup, apply deletion ledger, replay events, rebuild semantic/features,
  reload approved artifacts, and reconcile output without duplicate domain side effects.
- Run kill-switch drills for experiment, feature, model route, hosted provider, and optional pipeline.

## Caching, performance, and scaling

### Latency classes

Separate paths have different objectives:

- source outbox insertion is part of a short domain transaction;
- behavior collection acknowledges durable acceptance without waiting for transformation;
- experiment assignment must fit the user-facing surface or use a local deterministic SDK backed by
  signed/versioned configuration;
- online feature lookup and prediction have consumer-specific deadlines;
- batch metrics/training favor throughput, reproducibility, and cost over request latency; and
- privacy deletion/replay has policy deadlines and bounded operational impact.

### Safe caches

Cache event/metric/feature/model definitions by immutable version. Assignment caches key epoch and
unit, but database uniqueness or deterministic computation remains authoritative. Feature caches key
entity/context, definition version, as-of/epoch, consent state, and source version, and expire no
later than the feature or privacy policy allows. Prediction caches are used only when canonical input,
model/feature version, time validity, and consumer purpose match exactly.

A cache miss or outage invokes declared lookup/fallback. Cache content never becomes booking,
money, review, or safety truth.

### Query and index shapes

Critical relational indexes include:

- outbox due status/next attempt with bounded ordering;
- inbox consumer/event uniqueness;
- event arrival by event ID, name/version, received/event time, and partition;
- assignment by epoch/unit and experiment/unit history;
- exposure by assignment/time and dedupe key;
- feature latest value by definition/version/entity/context/as-of;
- model route by consumer/scope/status and model status/version;
- prediction by canonical request key, model/version, time, and outcome join reference;
- pipeline/quality jobs by state, schedule, lease, and age; and
- privacy jobs by subject scope/status/deadline.

Do not index unbounded arbitrary JSON paths. Promote stable high-value fields into typed columns or
analytical partitions.

### Partitioning and retention

High-volume arrival, interaction, exposure, feature-history, and prediction data can partition by
event date and possibly market after measured need. Partition keys preserve deletion and common
query patterns. Dropping an expired partition is permitted only after legal hold, replay, artifact
lineage, and aggregate dependencies are resolved.

### Scaling triggers

Consider a broker when polling/relay cannot meet lag and fan-out objectives without hurting the
database. Consider a dedicated online feature store when shared low-latency lookup volume,
freshness, and parity justify it. Separate model serving when resource isolation, independent scaling,
specialized runtime, or deployment cadence demands it. Partition or shard only after measuring
volume, contention, query shape, and operational ownership.

### Cost and overload controls

Bound event batch size, payload, cardinality, feature width, candidate/model calls, inference tokens,
training resources, concurrent jobs, query scans, and export volume. Apply quotas and budgets by
environment/team/model. During overload, shed optional enrichment, shadow predictions, and ad hoc
jobs before authoritative relays, privacy work, or required fallbacks.

## Appropriate use of AI

This entire platform exists partly to make AI and machine learning safe and useful, but not every
problem needs a learned model.

### Appropriate uses

- destination/query interpretation and semantic candidate retrieval;
- review aspect/target/sentiment extraction and evidence-grounded summaries;
- guest preference and session-intent representation;
- booking propensity and learning-to-rank;
- demand/occupancy forecasts and constrained price-response estimates;
- promotion uplift and later bounded contextual exploration;
- fraud, chargeback, cancellation, no-show, damage, spam, and anomaly likelihood;
- support routing, severity assistance, evidence-grounded summaries, and response drafts;
- content quality, duplicate listing/image similarity, and moderation candidate detection;
- data-quality anomaly detection, schema mapping suggestions, and metric/test drafting; and
- developer/analyst assistance that remains subject to code review and reproducibility.

### Prerequisites

An AI/ML use requires a named problem, consumer and decision owner, deterministic baseline, approved
data purpose, point-in-time features, valid labels or grounded evaluation set, success/guardrail
metrics, slice/fairness/adversarial evaluation, uncertainty/fallback, model card, registry, monitoring,
rollback, and operational owner. Generative use also requires grounding, output schema, hallucination
and prompt-injection testing, moderation, provider-data review, and human review where impact warrants.

### Prohibited model authority

No model or Large Language Model (LLM) may:

- invent or override availability, inventory claims, booking state, or stay completion;
- calculate authoritative prices, fees, taxes, refunds, ledger entries, balances, host entitlement,
  or payout;
- create, alter, or represent user-authored reviews/messages as original content;
- publish/remove content, suspend/restrict an actor, decide an appeal, or determine liability/remedy
  without domain-owned policy and authorized human/process control;
- infer or use protected/highly sensitive traits without an explicit lawful, necessary, reviewed use;
- silently expand data purpose, retention, or provider training rights;
- fabricate explanations, evidence, scarcity, earnings, safety, or causal claims; or
- stay active without versioned evidence, monitoring, deterministic fallback, kill switch, and owner.

LLM-generated text is a draft or structured candidate unless an explicitly approved low-risk
workflow says otherwise. The original source and extracted evidence remain available for review.

## Rollout plan

### Phase 0 — Governance and reference contracts

Approve owners, data classes, purpose/consent/retention, event envelope, schema compatibility, time
semantics, metric template, experiment policy, feature/label/model templates, access roles, approval
levels, and initial SLOs. Create reference events and end-to-end test vectors for search-to-completed
stay. Select the first analytical storage boundary without assuming a permanent vendor.

Exit criteria:

- Architecture/product decision records (ADRs) capture all launch-blocking decisions.
- The source-of-truth matrix and prohibited model authority are accepted by domain owners.
- Privacy/security review approves the initial event fields and subject-right workflow.
- Golden envelope, assignment, metric, point-in-time feature, and fallback examples pass review.

### Phase 1 — Durable facts and one journey

Add forward migrations for common outbox/inbox, event registry, bounded behavior acceptance, and audit.
Instrument one complete search/impression/click/quote/booking/confirmation/completion journey using
server facts plus validated client observations. Use scheduled export or a simple approved landing
store; no mandatory broker.

Exit criteria:

- Source commit/outbox atomicity and consumer dedupe pass real-database/crash tests.
- Journey IDs correlate without prohibited personal data or fingerprinting.
- Reconciliation detects missing/duplicate events and backfill has explicit provenance.
- Transactional latency remains within domain budgets during analytics backlog.

### Phase 2 — Conformed data and governed metrics

Build raw/conformed/semantic layers, shared historical dimensions, lineage, quality gates, retention,
deletion propagation, and an initial metric catalog. Publish discovery funnel, completed-stay,
cancellation/refund, host earning, and reliability metrics with finance-owned definitions where
applicable.

Exit criteria:

- Metrics reproduce approved fixtures and name grain/population/window/version.
- Quality/freshness and source reconciliation are visible and alertable.
- Deletion/opt-out reaches the initial analytical layers with proof.
- Dashboards distinguish operational, product, and audited financial authority.

### Phase 3 — Deterministic experimentation

Implement experiment registry/epochs, stable assignment, actual exposure, namespace exclusion,
guardrails, sample-ratio checks, frozen analysis datasets, lifecycle controls, and A/A validation.
Start with a low-risk presentation or ranking-policy experiment whose control is the production
deterministic baseline.

Exit criteria:

- Assignment is stable across retry/login policy and exposure follows reviewed semantics.
- A/A results show acceptable allocation, instrumentation, and false-positive behavior.
- Guardrail breach and emergency stop drills restore control without evidence loss.
- Analysis reports state cutoff, method, uncertainty, practical significance, and limitations.

### Phase 4 — Feature and training foundations

Add feature/label registries, point-in-time materialization, training manifests, temporal/entity-aware
splits, offline/online parity tests, model registry, artifact integrity, and model cards. Build one
simple interpretable candidate for a bounded use case, initially offline.

Exit criteria:

- No leakage occurs in golden adversarial datasets.
- Dataset and artifact reproduce from immutable manifests.
- Feature access, consent, deletion, freshness, and missingness behave as registered.
- Candidate beats or meaningfully complements the deterministic baseline under approved metrics.

### Phase 5 — Shadow and batch decision support

Run the first model in shadow, then optionally publish non-authoritative batch predictions for host or
operator insight. Join predictions, actual consumer decisions, fallbacks, and mature outcomes.

Exit criteria:

- Shadow availability, latency, cost, drift, calibration, fairness, and fallback are acceptable.
- Domain constraints reject unsafe or invalid predictions in replay tests.
- Operators can inspect lineage and use kill switch/rollback.
- No user or domain state depends on the shadow path.

### Phase 6 — Constrained canary and controlled experiment

Enable a small canary for one bounded consumer behind deterministic constraints, stable experiment
assignment, monitoring, and rollback. Ramp only through pre-approved gates. Suitable early examples
are ranking inside an already eligible candidate set or assistance that a human/domain policy reviews.

Exit criteria:

- Online primary value improves or meets the decision rule with all guardrails healthy.
- Fallback works under dependency outage and route rollback meets the recovery objective.
- Outcome/fairness slices and long-term risks have accountable review.
- The consumer persists policy, prediction, experiment, action, and explanation references.

### Phase 7 — Selective real-time and advanced learning

Add streaming updates, a dedicated online feature store, specialized serving, champion/challenger,
long-term holdouts, uplift models, or contextual bandits only for measured use cases. Expand markets
and model families one approved scope at a time.

Exit criteria:

- Infrastructure adoption has measured latency/scale/reliability benefit and staffed ownership.
- Adaptive systems log action probabilities and pass off-policy/safety evaluation.
- Disaster recovery, privacy propagation, cost limits, and artifact portability are proven.
- Each model family retains a deterministic or human-operable fallback.

## Verification checklist

### Functional and correctness

- [ ] Source-domain facts and client observations have distinct typed contracts and authority.
- [ ] Event, metric, experiment, feature, label, model, prediction, and decision versions are traceable.
- [ ] Outbox/source commit and inbox/application are atomic at their local boundaries.
- [ ] Stable assignment cannot be chosen by a client and exposure means actual treatment opportunity.
- [ ] Metric grain, population, windows, exclusions, currency, and maturity are explicit.
- [ ] Training features are point-in-time correct and labels preserve delay/correction/censoring.
- [ ] Model artifacts and training manifests are immutable and reproducible.
- [ ] Every consumer applies deterministic constraints and records fallback/decision evidence.

### Experiment and model quality

- [ ] A/A, sample-ratio, crossover, collision, missing-exposure, and event-loss checks pass.
- [ ] Analysis plan, power, sequential/multiple-testing, guardrails, and practical decision rules are
  approved before treatment.
- [ ] Models beat appropriate simple baselines on target metrics and pass calibration, drift,
  fairness, robustness, latency, and cost gates.
- [ ] Offline/online feature parity and prediction/outcome joins include failed/fallback traffic.
- [ ] Selective labels, position bias, marketplace interference, and feedback loops are addressed.
- [ ] Shadow, canary, kill switch, rollback, and retirement work under tested procedures.

### Recovery and operations

- [ ] Duplicate, delayed, reordered, malformed, missing, corrected, and backfilled facts converge.
- [ ] Failed transformation cannot publish partial output as current.
- [ ] Quality status propagates to dependent metrics, experiments, features, and models.
- [ ] Backlog/backpressure does not exhaust transactional resources.
- [ ] Restore plus outbox/inbox replay rebuilds projections without duplicate domain side effects.
- [ ] Dashboards, alerts, runbooks, owners, SLOs, budgets, and manual exception tools exist.

### Security, privacy, and fairness

- [ ] Collection has purpose, consent/legal basis where applicable, minimization, retention, and owner.
- [ ] Prohibited secrets, credentials, exact access/address data, and private content are absent from
  general events, logs, feature stores, and training data.
- [ ] Access/export/approval/replay/correction/deletion actions are least-privilege and audited.
- [ ] Opt-out/deletion reaches analytics, features, datasets, models, predictions, and caches under
  approved exception policy.
- [ ] Experiment and model reviews cover both guest and host harm, protected traits/proxies, small
  cohorts, abuse, and appeal/human control.
- [ ] Provider contracts cover residency, retention, training, security, portability, and deletion.

## Decisions required before implementation

Each consequential choice should be recorded in an ADR with owner, date, context, alternatives,
decision, consequences, rollout, and revisit trigger.

1. Which team/domain owns the common event envelope, schema registry, outbox library, and operational
   on-call responsibility?
2. Will the modular monolith start with one shared outbox table or domain-specific outboxes behind one
   relay contract?
3. What are the first delivery sink and transformation tools, and what measured trigger justifies a
   broker or separate data platform?
4. Which event schema representation and backward/forward compatibility rules are mandatory?
5. What aggregate ordering/gap policy applies to each first producer?
6. Which initial journey and event versions form the Phase 1 reference contract?
7. What client impression/viewability semantics apply to search, listing detail, map, price,
   notification, and host recommendation surfaces?
8. Which first-party anonymous/session identifiers may be collected, linked on authentication, and
   retained? Is historical merge allowed?
9. Which bot, automation, employee, test, and fraud filters apply, and how are their versions/audits
   retained?
10. What event lateness, clock-skew, offline-client, quarantine, and replay windows apply?
11. Which data residency, encryption, key-management, backup, and disaster-recovery requirements
    apply to each launch market?
12. What purposes, legal bases/consents, retention periods, deletion deadlines, legal-hold exceptions,
    and model-retraining responses apply to each data class?
13. What minimum cohort, contribution bound, suppression, and anti-differencing policy protects host
    and market analytics?
14. Which semantic metric versions define discovery success, completed-stay satisfaction, host
    earnings, platform contribution, refund, incident, and liquidity?
15. Which metrics require ledger/finance close versus provisional product reporting, and how are
    restatements communicated?
16. What quality/freshness objectives block a dashboard, experiment decision, training run, feature,
    or model route?
17. Who may create, review, launch, pause, stop, analyze, and roll out an experiment?
18. What experiment namespaces, collision rules, unit hierarchy, and anonymous-to-authenticated merge
    policy apply?
19. Which approved statistical methods, confidence/error budgets, sequential rules, multiple-testing
    corrections, and practical-significance thresholds are supported?
20. Which primary and guardrail metrics, minimum duration, maturity horizon, stopping limits, and
    long-term holdouts apply to ranking, pricing, promotions, messaging, reviews, and host tools?
21. How will marketplace interference be handled: individual randomization, clustered assignment,
    market experiments, or switchbacks?
22. May any public price or personalized benefit vary by guest? If so, under which host-control,
    disclosure, fairness, legal, and experiment rules?
23. What is the canonical feature-definition language or code packaging, and how is offline/online
    parity enforced?
24. Which features and model families may use review text, messages, support cases, payment facts,
    device/network data, or third-party signals?
25. Which sensitive/proxy features are prohibited globally and which require use-case approval?
26. What label maturity, correction, appeal, censoring, selective-label, and human-adjudication policy
    applies to each first model family?
27. Where will historical features, training datasets, artifacts, and latest online projections live
    in the MVP, and what are their retention/cost limits?
28. What model registry, artifact format, checksum/signing, dependency capture, and reproducibility
    standard is required?
29. Who may validate, approve, promote, rollback, or retire each risk tier of model?
30. What model-risk tiers determine review depth, maker-checker approval, monitoring, and human appeal?
31. Which first bounded model use case will be built, and what deterministic baseline and fallback
    must it beat?
32. What online latency, availability, freshness, throughput, and cost budget applies to each consumer?
33. Does a consumer fail open, fail closed, route to human review, or fall back to rules for each model
    failure reason?
34. Which prediction inputs/outputs and decision evidence must be retained, for how long, and who may
    inspect explanations?
35. Which fairness definitions, guest/host slices, proxy audits, thresholds, and remediation/appeal
    mechanisms apply per use case and market?
36. What hosted AI/ML providers are permitted, and what residency, retention, training, licensing,
    content safety, portability, and outage terms apply?
37. Which generative outputs may be shown automatically versus requiring human/domain review, and
    what grounding and correction UX is required?
38. What monitoring thresholds automatically page, pause, roll back, quarantine, retrain, or retire?
39. Which operations may replay production facts, how are side effects suppressed, and what approval
    and evidence are required?
40. What measured volume, latency, fan-out, reliability, team, and cost thresholds justify Kafka or
    another broker, a dedicated feature store, specialized model serving, partitioning, or service
    extraction?
41. How will affected experiments, metrics, datasets, and active models be invalidated and re-reviewed
    after a source correction, policy change, privacy deletion, or label-definition change?
42. Which cross-domain decision log fields are mandatory so support, risk, finance, product, and model
    owners can reproduce one consequential outcome?

Recommended MVP: one governed event envelope, PostgreSQL transactional outbox/inbox, a bounded
client collector, one approved analytical store and SQL transformation path, a small semantic metric
catalog, deterministic experiment assignment/exposure, and deterministic product baselines. Add one
interpretable model only after point-in-time features, mature labels, shadow evaluation, consumer
fallback, privacy/fairness review, monitoring, and rollback are demonstrably ready.
