# Trust, safety, fraud, and content moderation

## Purpose

This document defines how Room Booking detects, evaluates, prevents, contains, reviews, and learns
from abuse across accounts, listings, bookings, payments, payouts, messages, reviews, and stays. It
expands D15 in the
[marketplace problem breakdown](../marketplace-problem-breakdown.md) into an implementation-oriented
target design.

The central question is:

> Given an actor, resource, proposed action, current context, and historical evidence, what bounded
> protective decision may the platform take now, how is that decision enforced by the owning
> domain, and how can an authorized reviewer later explain, correct, or appeal it?

Trust and safety is a cross-domain control plane, not the owner of every controlled object. This
feature owns versioned risk assessments, policy decisions, moderation decisions, protective
interventions, risk-review queues, fraud labels, and appeal evidence. Identity owns accounts and
authentication; listing owns publication state; booking owns inventory and contract state; payment
owns provider money movement; finance owns ledger and payout state; messaging owns message content;
reviews owns original reviews; and stay operations owns operational incidents.

[Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md) owns
verified rights, immutable review revisions, double-blind reveal, public visibility projection,
transparent aggregates, and aspect/reputation evidence. D15 owns the exact-revision moderation and
confirmed-manipulation decisions that D14 enforces; neither domain silently assumes the other's
authority.

[Messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md)
owns urgent incident intake, evidence capture, safe communication, and operational mitigation.
[Disputes, damage claims, insurance, and customer support](disputes-damage-claims-and-support.md)
owns customer-support cases, damage claims, disputes, insurance/protection claims, financial remedies,
and case appeals. D15 may restrict an action, create or link a risk review, and request a domain
command; it never edits another domain's authoritative state or invents a refund, charge, inventory
release, ledger entry, or case remedy.
[Data, experimentation, and machine-learning platform](data-experimentation-and-ml-platform.md)
owns generic point-in-time feature, label, artifact, prediction, experiment, and monitoring
primitives. D15 owns risk evidence policy, consequential decision, restriction, human review,
explanation, appeal, and the admissibility of adjudicated outcomes as labels.

## Status and dependencies

This is a target design. The repository does not currently implement a risk-decision service,
cross-domain signal registry, risk review queue, content-moderation pipeline, account/listing/action
restriction model, appeal workflow, device intelligence, graph analysis, fraud-label store, or
trust-specific Java Application Programming Interface (API).

Current foundations are useful but narrow:

- [`001-identity.sql`](../../src/main/resources/db/changelog/changes/001-identity.sql) creates users
  with `ACTIVE`, `SUSPENDED`, and `DELETED` status, roles, contact verification timestamps, and a
  coarse host identity status. The Java application implements authentication, opaque refresh-token
  rotation, token revocation, host onboarding, and administrator suspension/reactivation.
- [`002-listing-catalog.sql`](../../src/main/resources/db/changelog/changes/002-listing-catalog.sql)
  creates listing content, media references, host ownership, address, and publication status, but no
  verification, duplication, provenance, or moderation state.
- [`004-booking.sql`](../../src/main/resources/db/changelog/changes/004-booking.sql) creates booking
  participant, stay, snapshot, and lifecycle foundations. It has no explicit risk hold or assessment
  reference.
- [`005-payment.sql`](../../src/main/resources/db/changelog/changes/005-payment.sql) creates coarse
  attempts, refunds, and a webhook inbox. Provider failure text and success state are not fraud
  decisions, accounting facts, or chargeback workflows.
- [`006-trust-engagement.sql`](../../src/main/resources/db/changelog/changes/006-trust-engagement.sql)
  creates verified-stay review and favorite foundations. Despite its filename, it does not implement
  D15 trust, fraud, safety, or moderation capabilities.
- [Payment orchestration](payment-orchestration.md),
  [booking lifecycle](availability-reservation-and-booking.md),
  [cancellation and refund](cancellation-modification-and-refund.md),
  [ledger and payout](ledger-reconciliation-and-host-payout.md), and
  [messaging/stay operations](messaging-notifications-and-stay-operations.md) define target contracts
  that risk decisions must consume and influence without bypassing.

Existing `users.status = SUSPENDED` is an implemented coarse account-wide control, but it has no
policy version, scope, effective interval, reason taxonomy, approval, appeal, or immutable decision
record. It must not be represented as the full restriction design. Existing host
`identity_status = VERIFIED` is evidence that a configured verification step completed, not proof
that the actor or every future action is safe.

All proposed tables and fields require forward-only Liquibase migrations when implementation is
authorized. Existing applied changesets remain unchanged. Historical rows need explicit backfill
provenance such as `LEGACY_UNKNOWN`; implementation must never fabricate a past risk assessment.

Recommended dependency order:

1. Approve the launch threat model, prohibited-use policy, privacy boundary, decision taxonomy,
   reviewer authority, appeal policy, and emergency runbooks.
2. Establish durable domain events, actor/resource identifiers, audit, idempotency, and supportable
   account/listing/booking/payment enforcement commands.
3. Implement deterministic velocity rules and versioned decisions for a small set of high-confidence
   account, listing-publication, booking, payment, and payout risks.
4. Add explicit restrictions/challenges, a human review queue, evidence access, expiry, appeal, and
   recovery before broad automated denial.
5. Add message, attachment, listing, and review moderation with quarantine and additive revisions.
6. Add provider signals and supervised models only after label quality, point-in-time features,
   fairness evaluation, fallback, shadowing, and kill switches exist.
7. Add relationship graphs and cross-market automation only when measured loss/abuse and review
   capacity justify their operational and privacy cost.

The minimum viable product (MVP) should protect account recovery, listing publication, checkout,
payment, payout-destination change, and safety reporting with deterministic controls and human
review. It should not attempt a universal reputation score or autonomous fraud model.

## Goals

- Define threat coverage across the complete marketplace journey, including abuse outside checkout.
- Produce one explainable, immutable risk decision for each consequential evaluation.
- Separate observations, features, scores, policies, decisions, restrictions, and domain outcomes.
- Let authoritative domains enforce a bounded decision atomically with their own state transition.
- Prevent duplicate side effects under retries, concurrent evaluations, and out-of-order events.
- Support `ALLOW`, `CHALLENGE`, `HOLD`, `MANUAL_REVIEW`, `LIMIT`, and `DENY` outcomes with explicit
  scope, duration, reason, evidence, and appeal behavior.
- Detect account takeover, synthetic/duplicate identities, listing fraud, payment/payout abuse,
  collusion, incentive manipulation, content abuse, and physical-safety risk.
- Preserve useful evidence with provenance, integrity, retention, access history, and legal holds.
- Minimize false positives and disproportionate impact through staged interventions, calibration,
  fairness analysis, human review, and appeal.
- Give reviewers safe tools and service-level objectives (SLOs) without indefinite inventory or
  money holds.
- Feed confirmed outcomes back into rules/models without circular labels or post-outcome leakage.
- Degrade safely when risk providers, feature pipelines, models, or review capacity are unavailable.
- Make policy, model, threshold, feature, and manual-action versions reproducible.

## Non-goals

- Guaranteeing zero fraud, zero harmful content, or zero physical-safety incidents.
- Treating one risk score, identity check, payment-provider response, device fingerprint, or review
  rating as proof of trustworthiness.
- Replacing authentication, authorization, booking, payment, ledger, messaging, review, operations,
  dispute, legal, sanctions, or emergency-service authorities.
- Letting analysts directly edit user, listing, booking, payment, refund, payout, or ledger rows.
- Holding guest money, host funds, or inventory indefinitely while an investigation remains open.
- Secretly changing price or search eligibility from an unreviewed inferred willingness to pay.
- Building a single permanent global score for guests, hosts, listings, or devices.
- Fully automated adverse decisions based only on opaque machine-learning (ML) output.
- Collecting precise device, biometric, contact-graph, or behavioral data merely because it might be
  useful later.
- Performing law-enforcement investigations or promising emergency response beyond the platform's
  approved role and market-specific runbooks.
- Introducing microservices, graph databases, stream processors, a feature store, or third-party
  risk vendors before a measured need and accountable owner exist.

## Core principles and invariants

### A score is not a decision

A score estimates a defined risk over a defined horizon. A decision applies an effective-dated
policy to approved evidence for one actor, resource, and action. Every consequential decision must
record outcome, reason codes, policy version, evidence/feature snapshot reference, model/rule
versions, decision time, scope, expiry, and decision maker. User-facing explanations use approved
reason families and never expose exploitable detector detail.

### Risk advises; authoritative domains enforce

Risk returns a bounded decision contract. The domain accepting a command validates that decision's
subject, action, resource, freshness, and version inside its transaction. Booking alone claims or
releases inventory; payment alone initiates provider movement; identity alone revokes sessions;
listing alone changes publication; finance alone applies payout eligibility. Risk never writes those
tables as a shortcut.

### High-impact actions are explicit, proportional, and appealable

Account suspension, booking denial, listing removal, payout hold, content removal, and long-lived
limitations require an approved policy, evidence, stable reason code, effective scope, duration or
review date, notification rule, and appeal path unless disclosure would create a documented safety
or legal hazard. Use the least intrusive action that controls the credible risk.

### Deterministic safety floors survive optional intelligence

Verified session/account state, resource authorization, transaction limits, allow/deny lists,
velocity budgets, upload scanning, payment/provider requirements, and emergency routing continue to
work if every model and optional vendor is disabled. A model timeout cannot silently convert a
required challenge or hard policy prohibition into approval.

### Evidence is immutable; interpretation is versioned

Raw provider observations, domain facts, content revisions, analyst notes, policy evaluations, and
decisions are append-only or superseded additively. Corrections identify the prior record and reason.
Derived features, scores, and labels retain definition/version/provenance so historical replay does
not apply today's meaning to yesterday's evidence.

### Unknown is distinct from safe and unsafe

Missing features, vendor timeout, unverified identity, inconclusive review, and low model confidence
are explicit states. Policy selects a safe fallback per action and risk tier. Missing evidence must
not be coerced to zero risk, and an absence of adverse history must not become a positive identity
claim.

### Time bounds every temporary intervention

Challenges, risk holds, manual reviews, velocity limits, payout holds, and emergency restrictions
have creation time, deadline, expiry or next review, escalation owner, and fail-safe behavior.
Inventory holds use the booking contract's time to live (TTL) and cannot be extended by an
unbounded risk queue.
Money holds follow ledger/payout policy and remain explicit liabilities, not revenue.

### One action has one effective decision

A canonical evaluation key identifies actor, action, resource, command intent, and policy epoch.
Retries replay the recorded decision. A later re-evaluation creates a new decision with an explicit
supersession link; it does not mutate the original. The authoritative domain records which decision
it enforced.

### Relationship does not prove culpability

Shared Internet Protocol (IP) addresses, devices, payment instruments, addresses, networks,
operators, or behavior may indicate a relationship, but hotels, families, offices, carriers, and
shared networks create benign connections. Graph signals require provenance, confidence, temporal
context, necessity, and policy guardrails. A weak association alone cannot authorize a high-impact
adverse decision.

### Safety intake always remains available

Blocking, suspension, or content moderation cannot prevent an actor from accessing the approved
emergency/safety report path, essential booking information, appeal, or legally required notice.
Safety triage has a deterministic severity floor and human escalation independent of commercial
risk optimization.

### Privacy and fairness constrain feature value

Data collection must have a defined purpose, necessity, owner, retention, access policy, and market
approval. Protected attributes and close proxies are excluded from operational decisions unless
legal review establishes a necessary permitted purpose, such as auditing disparate impact. Fairness
evaluation data is access-separated from production decision inputs.

### Historical contract and money remain immutable

A risk decision may prevent a proposed action or request a supported domain transition. It does not
rewrite an accepted quote, booking snapshot, provider fact, tax decision, journal, review original,
or past communication. Corrections use restrictions, domain commands, reversals, adjustments, or
replacement records.

### External systems are evidence, not authority

Identity, payment, device, sanctions, malware, content, and fraud vendors may return observations or
recommendations. The platform verifies authenticity, normalizes provider-native references, applies
its own versioned policy, handles duplicates/outages, and retains enough permitted evidence to
explain or reconcile the outcome.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Protected action | A proposed operation evaluated before an authoritative domain commits it |
| Subject | Actor, listing, booking, content item, instrument, device, or relationship being assessed |
| Signal | Atomic observation with source, event time, ingestion time, confidence, and provenance |
| Evidence | Authorized facts retained to support a decision, review, appeal, or investigation |
| Feature | Versioned transformation of signals available at a precise decision time |
| Score | Model/rule estimate for a named risk and horizon; never an enforcement command |
| Policy | Effective-dated rules mapping context, facts, and scores to a bounded decision |
| Decision | Immutable outcome for one evaluation: allow, challenge, hold, review, limit, or deny |
| Intervention | Enforceable restriction, challenge, quarantine, or review created by a decision |
| Challenge | Additional proof required before the protected action may continue |
| Risk hold | Explicit time-bounded block on one action or eligibility dimension |
| Restriction | Scoped capability limitation on an actor/resource with effective interval and state |
| Moderation item | Versioned content submitted for automated/deterministic/human policy evaluation |
| Quarantine | Content or resource unavailable to ordinary consumers pending a decision |
| Review task | Work item assigned to an authorized risk/moderation reviewer under an SLO |
| Appeal | Request to reconsider a decision using the preserved decision and new/clarified evidence |
| Label | Versioned post-event outcome used for evaluation or training, with confidence and source |
| Policy epoch | Stable configuration/version set used to deduplicate and reproduce evaluations |
| Velocity window | Bounded count/amount/distinct-entity measure over event time and defined dimensions |
| Entity link | Time-bounded, confidence-scored relationship between two subjects |
| False positive | Legitimate behavior incorrectly restricted or escalated |
| False negative | Harmful behavior incorrectly allowed or missed |
| ATO | Account takeover: unauthorized control of an account or session |
| KYC/KYB | Know Your Customer/Business identity and business-verification processes |
| AML | Anti-money laundering controls owned with legal/compliance policy |
| TTL | Time to live: maximum duration before a temporary record expires |
| HTTP | Hypertext Transfer Protocol used by the illustrative API contracts |
| SQL | Structured Query Language; direct production database edits are not a domain command |

All event times use UTC instants. Listing stay rules still use listing-local dates and IANA time
zones. Money signals use integer minor units plus ISO 4217 currency and legal-entity context; values
are never compared across currencies without an approved, timestamped conversion feature.

## End-to-end flow

```text
actor proposes protected action
  -> authoritative domain validates identity, permission, resource, and request shape
  -> risk orchestrator resolves action policy and canonical evaluation key
  -> fetch point-in-time domain facts and approved recent signals
  -> compute deterministic features/rules and optional versioned model scores
  -> policy produces bounded decision plus explanation and expiry
  -> persist decision, evidence references, and outbox fact
  -> authoritative domain atomically enforces or rejects that decision
  -> challenge, hold, or manual review may gather additional evidence
  -> reviewer/appeal produces a new superseding decision when authorized
  -> committed domain outcomes and confirmed abuse feed delayed labels and monitoring
```

The synchronous boundary ends after the risk decision is durably recorded and the caller receives a
signed/internal decision reference. The authoritative domain performs its own short transaction and
records the enforced decision ID. External vendor calls should not occur while authoritative domain
locks are held. A provider request needed on the critical path is made before that domain transaction
under a strict timeout, or converted into a challenge/review/pending state.

Asynchronous workers ingest committed events, compute non-critical signals, reconcile expired
interventions, route review tasks, notify users, and generate labels. They cannot retroactively make
an already committed booking unavailable or reverse money. Late evidence may create a new restriction,
incident, dispute gateway record, or domain command according to policy.

## Threat model and protected surfaces

### Threat families

| Family | Representative attacks | Primary protected moments |
| --- | --- | --- |
| Account/identity | Credential stuffing, ATO, fake or duplicate identity, synthetic identity | Register, login, recovery, verification, role/capability change |
| Listing/supply | Copied photos, fake address, unavailable property, bait-and-switch, unsafe/prohibited listing | Draft upload, publish, material edit, republish |
| Payment | Stolen instrument, card testing, friendly fraud, refund abuse, chargeback manipulation | Instrument attach, payment start, capture, refund |
| Payout/financial crime | Payout diversion, self-booking, collusion, laundering, mule accounts | Destination change, fund release, payout submit |
| Promotion/growth | Coupon farming, referral rings, credit cycling, fake acquisition | Grant, reserve, redeem, withdraw benefit |
| Search/reviews | Click manipulation, fake/incentivized review, competitor sabotage | Impression/click intake, review submit/publish/vote/report |
| Booking/stay | Parties, unauthorized guests, no-show abuse, damage, theft, neighborhood harm | Booking request, pre-arrival, check-in, in-stay report |
| Communication/content | Phishing, off-platform payment, harassment, discrimination, malicious links/files | Message/listing/review submission and edit |
| Severe safety | Trafficking indicators, violence, illegal goods, credible threats, missing person | Report intake, moderation escalation, emergency route |
| Platform/internal | Reviewer misuse, policy tampering, data exfiltration, collusive override | Admin access, bulk action, export, break-glass |

Threat analysis must identify attacker goal, target, preconditions, signals, prevention, detection,
containment, user harm if wrong, recovery, and residual risk. Abuse changes after controls ship, so
policy/version ownership and adversarial monitoring are part of the design rather than an annual
document exercise.

### Protected-action tiers

| Tier | Examples | Default latency and fallback |
| --- | --- | --- |
| T0 informational | Favorite, low-risk page interaction | Asynchronous monitoring; rate-limit obvious abuse |
| T1 reversible | Send message, submit unpublished review, save draft | Fast decision; quarantine/limit if uncertain |
| T2 scarce/contractual | Publish listing, create hold, confirm booking, apply promotion | Synchronous decision; bounded challenge/review |
| T3 money/privilege | Capture, refund, payout destination change, payout release, admin override | Step-up, strong evidence, tighter policy and audit |
| T4 urgent safety | Credible immediate-danger report or severe content | Never silently fail; deterministic route and human escalation |

Each action registers its maximum decision latency, permissible outcomes, required feature freshness,
failure mode, hold limit, disclosure policy, appeal policy, and enforcement owner. Generic risk
endpoints cannot accept arbitrary action names supplied by clients.

## Ownership and source-of-truth matrix

| Fact or decision | Owner | D15 use or output |
| --- | --- | --- |
| Account/session/contact verification | Identity | Consumed fact; D15 may request challenge/restriction/session revocation |
| Host identity/business/market eligibility | Compliance/identity | Consumed fact; D15 does not redefine KYC/KYB or sanctions result |
| Listing content, media, address, publication | Listing | Consumed; D15 owns moderation/risk decision and requests publish/pause action |
| Availability and booking contract | Inventory/booking | Consumed; D15 returns booking-action decision or explicit hold dimension |
| Quote and promotion entitlement | Pricing/growth | Consumed; D15 may deny/limit redemption, never recalculate amount |
| Provider payment movement | Payment | Consumed evidence; D15 may challenge/hold future action, not mark funds moved |
| Economic ownership and payout | Ledger/finance | Consumed; D15 supplies explicit payout hold/release eligibility fact |
| Message/review original | Messaging/reviews | Consumed; D15 owns moderation decision, not original-content mutation |
| Stay incident evidence | Stay operations | Consumed; D15 owns safety/risk intervention, D16 owns support/claim remedy |
| Risk signal/feature/score/decision | Trust and safety | Authoritative D15 record |
| Restriction/challenge/moderation decision | Trust and safety | Authoritative D15 record enforced by owning domain |
| Support/dispute/damage/insurance case | D16 support/claims | D15 may open/link/transfer but does not decide financial remedy |
| Policy/model configuration | Governance/ML plus D15 policy owner | Versioned input; D15 records exact applied version |

## Signal, evidence, and feature design

### Signal contract

Every signal must include:

- immutable `signal_id`, `signal_type`, subject references, source domain/provider, source record or
  event ID, schema version, event time, received time, and expiry/retention class;
- value type and normalized value, confidence/quality, collection purpose, market/legal-entity
  scope, and sensitivity classification;
- provenance explaining whether it is a direct domain fact, verified provider observation,
  user allegation, deterministic derivation, or model inference;
- deduplication identity and supersession/correction references;
- access policy and a pointer to protected detail rather than unnecessary payload duplication.

Allegations are signals, not confirmed labels. Provider reason codes retain their native values as
evidence while platform mappings are versioned. Arrival after ingestion is recorded so point-in-time
replay can exclude facts unknown at the original decision.

### Feature computation

Feature definitions specify name, type, entity/action scope, window, event-time rule, missing-value
semantics, source versions, transformation, currency/time-zone treatment, owner, privacy approval,
and online/offline parity test. Representative features include:

- failed login and recovery velocity per account/network/device;
- distinct accounts or instruments associated with a device over approved windows;
- listing image/content similarity with provenance and confidence;
- booking/payment attempt counts, amount velocity, lead time, trip distance category, and mismatch
  indicators without inferring protected traits;
- payout destination age, recent account-security changes, new-device/session risk, and amount;
- coupon/referral relationship and redemption velocity;
- message link/contact/off-platform-payment indicators;
- confirmed review/booking relationship patterns and outcome rates.

Features used synchronously must have a maximum age and explicit missing behavior. Durable decision
snapshots store values or content-addressed immutable references sufficient for replay. Online
features must not query an unbounded event history during checkout.

### Velocity controls

Velocity rules define exact event, subject dimensions, distinctness, time window, count or amount,
late-event policy, threshold, reset semantics, and action. Use database-backed counters or bounded
queries initially. Approximate counters may protect very high-volume reversible actions later, but
they cannot be the only evidence for irreversible or financial decisions.

Counter updates are idempotent by source-event ID. Event-time windows handle late events explicitly;
wall-clock bucket boundaries do not silently pardon or double count behavior. Distributed rate
limits are a protective layer, not the historical system of record.

### Entity relationships

Entity links use typed endpoints such as account, verified identity, listing, address, device,
network, payment token, payout destination, booking, promotion, and content. Links record first/last
seen, evidence type, confidence, direction, source, expiry, and policy-permitted uses. Raw payment or
bank credentials are never copied into D15; provider tokens or stable privacy-preserving references
are used where approved.

Graph-derived features begin as bounded relational queries. A graph database or graph neural network
requires measured query/value need, privacy review, adversarial testing, explainability strategy,
and operational ownership. Links are not exposed in user-facing reasons and are access-restricted to
prevent revealing other users.

### Evidence quality ladder

Policy distinguishes:

1. verified authoritative facts;
2. authenticated provider observations;
3. corroborated independent observations;
4. deterministic derived signals;
5. calibrated model inference;
6. single-party allegation or unverified content;
7. missing/unknown information.

Severity can justify precautionary containment before confirmation, especially for safety, but the
decision must say it is precautionary, expire or review promptly, and never relabel an allegation as
confirmed abuse.

## Decision framework

### Decision inputs and output

A decision request is internal and contains a registered action type, authenticated actor and
session, target resource/version, domain command/idempotency identity, market, event time, and the
minimum authoritative facts. The orchestrator resolves remaining facts server-side. Clients cannot
supply trusted risk scores, verification state, account age, price, payout eligibility, or labels.

The response contains:

- `decision_id`, outcome, decision version, policy ID/version/epoch, evaluated and expiry times;
- actor, subject, protected action, resource and command identity;
- machine-readable internal reasons and an approved user-explanation family;
- required challenge or review reference, restrictions to apply, and safe retry behavior;
- feature/evidence snapshot digest, rule hits, optional model IDs/versions/scores, confidence, and
  fallback mode;
- correlation/causation IDs and whether mandatory facts were missing.

### Outcome semantics

| Outcome | Meaning | Enforcement rule |
| --- | --- | --- |
| `ALLOW` | Evaluation found no policy reason to block this exact action now | Domain still validates all native invariants |
| `CHALLENGE` | More proof or step-up action is required | No protected commit until approved challenge succeeds |
| `HOLD` | Temporarily stop one named eligibility/action dimension | Explicit deadline, owner, state, and expiry required |
| `MANUAL_REVIEW` | Authorized human decision is required | Review SLA and expiry/fallback are part of policy |
| `LIMIT` | Action is permitted only within stated scope/rate/amount/capability | Domain enforces exact limit, not a vague risk flag |
| `DENY` | This action is prohibited under the applied policy | Stable reason family and appeal/disclosure behavior required |

An account-wide suspension is a restriction produced through a separate governed command, not an
implicit side effect of every `DENY`. A decision can combine a primary outcome with a small approved
set of secondary interventions, such as deny payout-destination change and revoke affected sessions.

### Precedence and composition

Policy evaluates in this order:

1. legal/market and safety prohibitions that must fail closed;
2. actor/resource authorization and account capability restrictions;
3. known compromised credentials/instruments/content and explicit block lists;
4. deterministic limits, velocity, verification, and domain-specific hard rules;
5. time-bounded challenges and human-review requirements;
6. calibrated optional model/risk recommendations;
7. default allow or registered safe fallback.

The most protective compatible outcome wins, but policy must avoid nonsensical composition. For
example, a mandatory legal deny cannot be overridden by a low fraud score, while a stale optional
model cannot escalate a permitted action beyond the configured deterministic fallback. Each rule
records whether it is mandatory, advisory, or explanation-only.

### Policy representation and release

Policies are immutable versions with scope, effective interval, priority, action types, typed input
schema, rule tree, thresholds, outcome, user reason mapping, review/appeal route, owner, approvers,
simulation evidence, rollout percentage, and kill-switch behavior. Activation is maker-checker for
T3/T4 or broad adverse-impact changes. Emergency changes expire and receive post-use review.

Before activation, replay proposed policy against a representative point-in-time dataset and report
decision deltas, loss/abuse capture, challenge/review volume, latency, and fairness slices. Start in
shadow mode, then canary by stable assignment. Rollback selects the last approved version; it never
rewrites past decisions.

## Challenges, restrictions, and lifecycle states

### Challenge lifecycle

```text
REQUIRED -> STARTED -> SUBMITTED -> PASSED
    |          |           |          |
    +----------+-----------+----------+-> EXPIRED
                           +------------> FAILED
                           +------------> CANCELLED
```

A challenge is bound to actor, session or account, action, resource, policy decision, permitted
method, maximum attempts, expiry, and reuse policy. Passing a challenge does not grant general trust;
it creates a new or resumed evaluation with verified evidence. Identity document, liveness,
multi-factor authentication, payment confirmation, phone/email verification, or manual proof each
have different security and retention rules.

### Restriction lifecycle

```text
PROPOSED -> ACTIVE -> EXPIRED
               |  \-> REVOKED
               |  \-> SUPERSEDED
               +----> APPEAL_PENDING -> ACTIVE / REVOKED / SUPERSEDED
```

Restrictions specify target, capability/action scope, optional resource scope, reason, policy and
decision references, starts/ends, review date, enforcement mode, actor, and version. Examples are
`LOGIN_BLOCKED`, `MESSAGING_LIMITED`, `LISTING_PUBLICATION_BLOCKED`, `BOOKING_CREATE_BLOCKED`,
`PAYMENT_CHALLENGE_REQUIRED`, `PAYOUT_RELEASE_HELD`, and `CONTENT_QUARANTINED`.

Restrictions are checked at the authoritative command boundary, not only hidden in UI. Database
constraints or transactional enforcement exist where bypass would cause inventory/financial harm.
An expiry worker marks state, but enforcement compares the current instant to effective bounds so a
late worker cannot extend a restriction accidentally.

### Decision and review lifecycle

Risk decisions are immutable. Their operational projection may be `EFFECTIVE`, `SATISFIED`,
`EXPIRED`, or `SUPERSEDED`, but the original outcome does not change. Review tasks move through:

```text
QUEUED -> CLAIMED -> IN_REVIEW -> DECIDED
   |         |           |          |
   +---------+-----------+----------+-> CANCELLED
             +-----------------------> ESCALATED
```

Claim leases expire and may be reassigned. The final reviewer action creates a new decision or
confirms the current one; it never edits the automated decision in place.

## Domain integrations

### Identity, authentication, and account takeover

Protect registration, login, refresh, password reset, email/phone change, account recovery, new
device/session, role grant, host onboarding, and sensitive profile change. Controls include hashed
credential/session intelligence, attempt velocity, breached-secret checks where approved, verified
contact freshness, security-change cooldown, device/session anomaly, and step-up authentication.

Identity owns credential validation, session creation/revocation, recovery proof, and account status.
D15 may return challenge/deny or request a scoped restriction/session revocation. A global
`SUSPENDED` state is reserved for policy-approved account-wide incapacity; lower-risk behavior uses
scoped restrictions. Recovery must account for attacker-controlled contact changes and retain a safe
path for the legitimate owner.

### Host onboarding and compliance

KYC/KYB, sanctions screening, beneficial ownership, licensing, tax identity, and payout eligibility
have legal/compliance owners. D15 consumes their versioned results and detects manipulation,
duplication, or mismatch. It must not reinterpret a sanctions match or persist raw identity documents
without the approved compliance boundary.

A verified host may still create a fraudulent listing or suffer ATO. Conversely, incomplete host
verification is not itself proof of abuse; policy maps it to unavailable capabilities or a challenge.

### Listing, media, and publication risk

Publication evaluation uses ownership/capability facts, address confidence, media scan result,
duplicate/similarity evidence, prohibited category/rules, content claims, prior enforcement, and
market requirements. Listing owns `DRAFT`, `PUBLISHED`, `PAUSED`, and `ARCHIVED`; D15 records
`ALLOW`, `QUARANTINE`, `REVIEW`, or `DENY` publication decisions and asks listing to enforce them.

Material edits to address, host identity, title/description, property type, capacity, critical
amenities, or images invalidate the relevant prior decision and require evaluation of a new listing
version. Search consumes listing publication and moderation state; it does not independently hide a
listing based on an unversioned score. Copied-content detection retains source/provenance and permits
legitimate portfolio or licensed reuse through review.

### Booking and stay risk

At checkout, evaluate exact guest/session, listing/host, dates, party, accepted quote, booking intent,
velocity, prior outcomes, and approved payment context. Risk cannot override availability, price, or
booking rules. A booking risk hold must fit within the inventory hold TTL; manual review that cannot
finish before expiry releases inventory and returns a clear recoverable outcome.

Late risk evidence after confirmation may request a scoped booking intervention, support/safety case,
party verification, or payout hold. Only booking/cancellation/support authority decides contract
change and remedy. Party-risk predictions cannot replace a reported incident or be treated as proof
that damage occurred.

### Payment, refund, chargeback, and payout risk

Payment owns provider operation and verified outcomes. D15 consumes tokenized instrument references,
authentication results, provider risk observations, attempt velocity, amount/currency, booking facts,
and historical outcomes. It may challenge, limit, deny, or review an operation before submission.
It never marks an authorization/capture/refund successful.

Chargebacks and provider disputes are post-event evidence with allegation, stage, reason, deadline,
and final outcome. [Payment orchestration](payment-orchestration.md) owns the provider dispute
gateway; D16 owns support/dispute coordination; finance owns reserves, liability, and accounting.
D15 owns fraud interpretation and future protective policy. A chargeback filing is not automatically
a confirmed-fraud label, and a won dispute is not automatically proof of guest abuse.

Payout-destination creation/change and funds release require stronger controls: verified ownership,
step-up authentication, security-change cooling period, destination age, provider/compliance state,
and risk decision. D15 creates `PAYOUT_RELEASE_HELD` eligibility; ledger preserves host payable and
decides release with all finance/compliance/dispute dimensions. D15 cannot seize or recognize held
funds as revenue.

### Promotion, referral, and marketplace manipulation

Promotion/growth owns benefit eligibility, budgets, reservation, redemption, and reversal. D15
detects multi-account farming, referral rings, self-referral, credit cycling, scripted interaction,
and policy evasion. Actions are scoped to the benefit when possible rather than disabling unrelated
booking capability.

Click or impression anomalies are excluded or down-weighted in analytics/ranking through versioned
quality signals. Transactional search eligibility remains owned by listing/inventory; enforcement
must avoid letting competitors suppress a listing through fabricated traffic or reports.

### Messaging, review, and content

Messaging and reviews retain original immutable content and participant authority. D15 evaluates
content revisions for scam, off-platform payment, personal data, harassment, discrimination,
threats, sexual exploitation, illegal goods, spam, malicious links/files, extortion, irrelevance,
and coordinated manipulation.

Moderation creates a decision and visibility action reference. Redaction/removal is additive; the
original remains protected evidence subject to retention/legal policy. A message used for urgent
safety reporting must route even if normal messaging is limited. Review eligibility and aggregate
calculation remain owned by reviews; D15 supplies publish/quarantine/remove decisions and confirmed
manipulation labels.

The lifecycle, non-leaking double-blind publication, aggregate inclusion, aspect extraction, and
contextual human-reputation safeguards are specified in
[the D14 review design](review-reputation-and-aspect-intelligence.md). A pending score or report is
not enough to alter D14 public arithmetic without an effective policy decision.

### Safety and stay incidents

[Stay operations](messaging-notifications-and-stay-operations.md) owns incident intake, immediate-
danger questions, severity floor, evidence, operational mitigation, and safety escalation. D15 may
detect a safety signal from content or behavior, impose precautionary restrictions, and create/link
a safety review. D16 coordinates support remedies and claims.

Commercial risk queues must never delay S0/S1 routing. Immediate-danger guidance follows approved
market runbooks and does not imply the platform replaces local emergency services. Details are
disclosed only to authorized responders with purpose, step-up, audit, and time-bounded access.

## Content moderation design

### Intake and revision identity

Every submitted listing text/image, message, attachment, review, profile field, or public response
has a stable content item and immutable revision. Intake records author, resource/participant scope,
language/locale, content type, hash/storage reference, client submission ID, created time, and
visibility default. Attachments remain quarantined until type validation and malware scanning pass.

Moderation operates on the exact revision. Editing content creates a new revision and invalidates the
prior publish decision where material. A clean text decision cannot approve a later attachment or
changed URL.

### Pipeline

```text
submission
  -> structural validation and secure upload/quarantine
  -> deterministic block/allow rules and malware/link checks
  -> optional classifiers with per-policy confidence
  -> policy composition
  -> publish, mask, warn, quarantine, reject, or urgent escalation
  -> human review for configured uncertainty/severity
  -> appeal/restoration/removal as additive decisions
```

The synchronous path is bounded. Low-risk content may publish with asynchronous monitoring if policy
accepts the residual risk; high-risk attachment, listing, or public content remains quarantined.
Private-message scanning is limited by approved purpose, privacy notice, retention, and access.

### Moderation outcomes

| Outcome | Visibility/effect |
| --- | --- |
| `PUBLISH` | Exact revision visible to its authorized audience |
| `MASK` | Approved sensitive span or contact detail replaced; original retained as protected evidence |
| `WARN` | Content may proceed after contextual warning or confirmation |
| `QUARANTINE` | Hidden pending time-bounded review |
| `REJECT` | Revision not published/sent; safe reason and appeal provided where allowed |
| `REMOVE` | Previously visible revision hidden through additive decision |
| `ESCALATE_SAFETY` | Independently route urgent safety workflow; visibility decided separately |

Model confidence does not directly map to severity across all languages and content types. Policy
uses category-specific thresholds and human routing. Translation may help reviewers but original
content, language, translator/model version, and uncertainty remain visible.

### Contact masking and malicious content

Contact/off-platform-payment rules use approved patterns and classifiers. Masked content retains a
span map and reason without placing sensitive text in ordinary logs. Uniform Resource Locators
(URLs) are canonicalized safely, checked without automatic privileged fetching, and rendered to
prevent homograph or redirect abuse.
Files are validated by bytes rather than declared type, scanned in isolated infrastructure, served
by short-lived authorized links, and never executed in reviewer browsers.

### Reports and coordinated abuse

User reports create allegations linked to the exact revision and reporter relationship. Rate limits,
reputation of report behavior, corroboration, and reviewer policy reduce report brigading. The
reported user does not receive reporter identity. Duplicate reports may join one investigation while
retaining separate evidence and reporter acknowledgment.

## Manual review, escalation, and appeals

### Queue design

Routing uses action tier, suspected harm, market/language, deadline, inventory/money exposure,
required reviewer skill, conflict rules, and legal/safety restrictions. Queue priority is policy-
bounded; an ML priority score may order peers but cannot demote T4 or breach statutory/provider
deadlines.

Tasks contain a minimized case packet: decision, exact requested action, timeline, authoritative
facts, evidence references, policy/model versions, prior related decisions, approved recommended
actions, deadline, and disclosure constraints. Reviewers fetch protected details just in time. Bulk
export and unrestricted graph browsing are prohibited.

### Reviewer authority

Roles are scoped, for example account security, listing moderation, payment fraud, payout risk,
content moderation, severe safety, quality assurance, and appeals. Each action defines maximum scope,
duration, amount exposure, required step-up, and maker-checker threshold. Reviewers issue domain
commands through supported APIs and cannot alter raw evidence or historical decisions.

Self-review, related-account conflicts, and repeated overrides are detected and routed. Break-glass
access has declared purpose, short expiry, notification, immutable audit, and mandatory post-use
review.

### Appeal semantics

An appeal references one effective decision/restriction/moderation action, appellant authority,
submitted grounds/evidence, locale, deadline, and prior appeal history. Intake acknowledges receipt
without promising reversal. The appeal reviewer sees the original policy/evidence and any permitted
new facts, records findings, and creates a superseding decision or confirms the original.

Appeal success restores eligible capability/content prospectively and triggers necessary domain
commands; it does not erase audit history or automatically create monetary entitlement. If harm
occurred, D16 evaluates support/remedy under its own authority. Appeals need SLO, accessibility,
translation, non-retaliation, and quality sampling.

### D15 and D16 boundary

| Scenario | D15 authority | D16 authority |
| --- | --- | --- |
| Suspicious checkout | Assess, challenge, limit, deny, risk-review | Help user navigate existing decision; no risk override |
| Chargeback | Fraud interpretation and future controls | Coordinate provider dispute/evidence/remedy with payment/finance |
| Property damage | Precautionary restriction and abuse label after sufficient evidence | Damage claim, negotiation, protection/insurance, financial outcome |
| Harassment report | Content/safety restriction and moderation | Support communication, accommodation remedy, case ownership |
| Listing misrepresentation | Publication restriction/moderation | Guest complaint, relocation/refund/claim coordination |
| False positive | Superseding risk decision and capability restoration | Service recovery/goodwill request through governed remedy policy |

One operational UI may show a unified timeline, but it must preserve authority, permissions,
reason codes, and separate state machines.

## Feedback labels and learning loop

### Label lifecycle

Labels are not copied directly from every adverse event. A label records subject/action, taxonomy,
value, confidence, source outcome, evidence, applicable time interval, adjudication state, created
by/version, and supersession. Suggested states are `PROVISIONAL`, `CONFIRMED`, `DISPUTED`,
`REVERSED`, and `EXPIRED_FOR_TRAINING`.

Examples:

- confirmed ATO after legitimate-owner recovery evidence;
- stolen-instrument fraud after provider/issuer outcome, distinct from generic decline;
- chargeback outcome plus reason and dispute result, not one binary fraud field;
- verified listing misrepresentation after investigation;
- confirmed review manipulation after relationship/evidence review;
- moderation-policy violation at exact content revision;
- damage claim outcome separate from proof of intentional abuse.

Training data uses labels available only after the decision timestamp and applies maturation windows.
Rules/models that caused review must not train on their own decision as ground truth. Selective
labeling, reviewer disagreement, appeal reversals, provider delay, and investigation bias are
measured.

### Model evaluation and governance

For each model define target event/horizon, population, action tier, feature allowlist, exclusions,
training window, label maturity, calibration, cost matrix, slice metrics, latency budget, model card,
owner, expiry, and fallback. Evaluation includes precision/recall or ranking metrics, calibrated loss
and harm, challenge/review rate, false-positive recovery, reviewer workload, latency, stability,
adversarial robustness, and fairness slices.

Deploy in offline replay, shadow, reviewer-assist, small canary, then bounded automation. Every
prediction records artifact/version, feature-set version, evaluated time, score/uncertainty, and
serving fallback. Drift or quality breach disables model contribution through a kill switch while
deterministic controls remain.

## Conceptual data model

These are proposed structures, not implemented tables.

### Core risk records

| Entity | Important fields and constraints |
| --- | --- |
| `risk_subjects` | Stable typed reference to account/listing/booking/content/instrument/device; no raw secret; unique `(subject_type, source_id)` |
| `risk_signals` | Immutable type/value or protected-detail reference, subjects, provenance, event/ingest time, confidence, purpose, retention; unique source dedupe key |
| `risk_feature_definitions` | Immutable versioned schema, transformation, windows, missing semantics, privacy approval, online/offline owner |
| `risk_feature_snapshots` | Decision-time values/digest, feature versions, as-of/watermark, missing flags; immutable |
| `risk_model_predictions` | Artifact/model/feature versions, target/horizon, score, uncertainty, evaluated time, fallback; immutable |
| `risk_policies` | Immutable effective-dated scope, typed rule document, outcome mappings, approvals, rollout and kill-switch behavior |
| `risk_decisions` | Evaluation key, action, actor/subject/resource, immutable outcome/reasons, policy epoch, snapshot/prediction references, expiry, supersedes, actor type |
| `risk_decision_rule_hits` | Ordered rule IDs, input references, result, contribution, redacted explanation; no duplicated secrets |

`risk_decisions` should have a unique canonical evaluation identity such as
`(protected_action, actor_id, resource_type, resource_id, command_id, policy_epoch)` and an index on
subject/action/evaluated time. A separate table can map which authoritative domain command enforced
which decision.

### Intervention and review records

| Entity | Important fields and constraints |
| --- | --- |
| `risk_challenges` | Decision, method, subject/action binding, state, attempt limit, expiry, satisfied evidence; raw secrets excluded |
| `risk_restrictions` | Target, capability/resource scope, state, effective interval, decision/reason/policy, review date, version |
| `risk_review_tasks` | Queue/skill, tier/severity, subject/action, decision, priority basis, deadline, lease, state, assigned reviewer |
| `risk_review_actions` | Append-only reviewer decision/action, authority, reason, evidence, policy, created time, superseding decision/domain command |
| `risk_appeals` | Decision/restriction/moderation target, appellant, grounds, evidence, state, deadline, assigned independent reviewer, outcome |
| `risk_access_audit` | Purpose-bound views/exports/changes with actor, resource, time, session, reason and break-glass reference |

Prevent multiple active restrictions for the same exact target/scope/policy intent with a partial
unique index or serialized creation logic. Overlapping restrictions from different valid reasons may
coexist; effective enforcement composes them deterministically.

### Moderation and relationship records

| Entity | Important fields and constraints |
| --- | --- |
| `content_items` | Owning domain/type/resource/author/audience; stable identity only |
| `content_revisions` | Immutable sequence, language, text or protected storage reference, hash, created time; unique `(content_item_id, revision_number)` |
| `moderation_assessments` | Revision, detector/rule/model versions, categories, confidence, protected spans, scan evidence |
| `moderation_decisions` | Revision, outcome, policy/reasons, visibility instruction, reviewer/automation, expiry/supersedes |
| `content_reports` | Reporter, exact revision, category/allegation, relationship, event time, dedupe/group reference, privacy state |
| `entity_links` | Typed endpoints, relation, confidence, provenance, first/last seen, permitted purpose, expiry |
| `risk_labels` | Versioned taxonomy, subject/action, value/confidence, evidence/outcome, adjudication state, available time, supersession |

Content payloads remain in their owning domain or protected object store where possible. D15 stores
stable references, hashes, necessary extracted spans, and decisions. A foreign key is useful within
one database where lifecycle permits it; polymorphic cross-domain IDs need application-level type
validation and reconciliation.

### Shared infrastructure

Use transactional `outbox_events`, idempotent consumer inbox records, policy/model registries, and
append-only admin audit. Provider webhook/observation inboxes use `(provider_account,
provider_event_id)` uniqueness. Large raw provider payloads and uploaded evidence use encrypted,
retention-controlled storage referenced by digest; secrets and unnecessary personal data stay out of
relational JSON and logs.

### Migration and backfill

Implement in additive stages: create registries/decision/audit first, then restrictions/reviews,
then moderation/signals/labels. Dual-read current `users.status` while backfilling only known active
account-wide restrictions from current state; mark origin `LEGACY_STATUS` and do not invent reason or
past policy. Listing/review content receives a current-version identity, but prior moderation outcome
is `NOT_EVALUATED`, not `APPROVED`.

Add nullable decision references to authoritative domain commands only after D15 records exist. Then
require them for configured protected actions. Backfills are restartable, idempotent, measured, and
never mutate applied migrations. Rollback disables enforcement by policy/action and retains evidence;
it does not drop history.

## Service boundaries

Logical modules can remain in the modular monolith initially:

| Module | Contract |
| --- | --- |
| Risk policy registry | Validate, simulate, approve, activate, retire, and resolve effective policy versions |
| Signal intake | Authenticate/dedupe observations, classify provenance, retention, and subject links |
| Feature evaluator | Produce bounded point-in-time features with missing/freshness metadata |
| Model adapter | Invoke optional registered model under deadline and return versioned prediction/fallback |
| Risk decision orchestrator | Compose rules/evidence/scores into one immutable bounded decision |
| Intervention service | Create/expire/supersede challenges and scoped restrictions |
| Moderation service | Evaluate exact content revisions and issue visibility/safety instructions |
| Review/appeal service | Route leased work, enforce reviewer authority, record superseding decisions |
| Label service | Adjudicate outcome labels and publish training/evaluation facts |
| Risk reconciliation | Detect unenforced decisions, stale holds, orphan tasks, missing outcomes and provider drift |

Each authoritative domain provides a narrow enforcement adapter: `IdentityRiskEnforcement`,
`ListingModerationEnforcement`, `BookingRiskEnforcement`, `PaymentRiskEnforcement`,
`PayoutRiskEligibility`, `MessagingModerationEnforcement`, and `ReviewModerationEnforcement`. These
names describe contracts, not required remote services.

The decision orchestrator may read stable projections but must fetch or receive authoritative
high-impact facts. It does not own domain lifecycle. The governance module owns reviewer roles,
policy approvals, feature/model release authorization, and break-glass. D16 consumes risk evidence
through authorized views and emits case outcomes that may later become adjudicated labels.

## API behavior

All endpoints below are illustrative target contracts. They do not currently exist. Public APIs
expose safe outcomes and next actions; detailed evaluation, signal, graph, policy, and reviewer APIs
are internal/admin-only and require workload identity or scoped operational roles.

### Internal protected-action evaluation

```http
POST /internal/v1/risk/evaluations
Idempotency-Key: 4a7d...

{
  "protectedAction": "BOOKING_CREATE",
  "actorId": "0d22...",
  "sessionId": "a9a1...",
  "resource": {"type": "LISTING", "id": "a51f...", "version": 17},
  "commandId": "checkout-018f...",
  "market": "VN",
  "context": {
    "quoteId": "58c2...",
    "bookingIntentId": "c98c..."
  }
}
```

```json
{
  "decisionId": "4b8e...",
  "outcome": "CHALLENGE",
  "policyVersion": "booking-risk-v7",
  "evaluatedAt": "2026-09-06T04:15:30Z",
  "expiresAt": "2026-09-06T04:25:30Z",
  "reasonFamily": "ADDITIONAL_VERIFICATION_REQUIRED",
  "challenge": {
    "challengeId": "c18f...",
    "allowedMethods": ["PAYMENT_AUTHENTICATION"]
  },
  "decisionVersion": 0
}
```

The caller supplies only registered context identifiers. Risk fetches account status, listing facts,
quote total, payment evidence, and prior outcomes from authoritative sources. An unknown action,
resource mismatch, stale resource version, or unregistered context field fails validation. A repeat
with the same canonical command and policy epoch returns the same decision. Reusing the idempotency
key with different normalized input returns conflict.

The authoritative domain calls a second internal operation or records enforcement locally:

```http
POST /internal/v1/risk/decisions/{decisionId}/enforcements
```

It provides domain command ID, outcome, aggregate version, and committed time. Risk verifies subject,
action, decision freshness, and outcome consistency. This acknowledgment supports reconciliation; it
does not let risk commit the domain transition.

### Challenge APIs

- `GET /api/v1/risk/challenges/{challengeId}` returns safe status and allowed next action only to the
  bound actor/session.
- `POST /api/v1/risk/challenges/{challengeId}/start` begins an allowed provider/identity step with a
  challenge-specific idempotency key.
- `POST /api/v1/risk/challenges/{challengeId}/complete` consumes provider proof or redirects to
  server-side verification; it never trusts a client `passed=true` value.
- `POST /api/v1/risk/challenges/{challengeId}/cancel` abandons a non-mandatory challenge.

Responses do not disclose detector thresholds, related accounts, device links, provider-native
fraud detail, or whether a particular person is under investigation. Successful completion returns
a new evaluation/continuation token bound to the original action; it is not a reusable trust badge.

### User restrictions and appeals

- `GET /api/v1/users/me/capability-restrictions` returns active user-visible restrictions, scope,
  reason family, effective time, next review/expiry, and appeal availability.
- `POST /api/v1/restrictions/{restrictionId}/appeals` accepts grounds, locale, and staged evidence
  references under one idempotency key.
- `GET /api/v1/appeals/{appealId}` returns status, deadline/expectation, requested information, and
  final user-facing outcome.

For safety/legal reasons, some details may be delayed or withheld, but the API returns the most
specific approved explanation and process. It must not claim that a model made the final decision.
Accessibility and alternate verification paths are part of challenge/appeal design.

### Content and report APIs

Owning domains submit exact content revisions internally:

- `POST /internal/v1/moderation/evaluations` with owner/type/resource/revision/storage references;
- `POST /api/v1/messages/{messageId}/reports`, `POST /api/v1/reviews/{reviewId}/reports`, and
  `POST /api/v1/listings/{listingId}/reports` for actor-authorized allegations;
- `GET /api/v1/reports/{reportId}` for safe acknowledgment/status, without target enforcement detail;
- owner-specific content responses expose `PUBLISHED`, `PENDING_REVIEW`, `ACTION_REQUIRED`, or
  `REMOVED` plus permitted reason and appeal.

Repeated identical reports by one reporter/resource/revision are idempotent. Distinct evidence may
append to an existing report/investigation. Report APIs always retain an emergency path and must
protect reporter identity.

### Reviewer and governance APIs

Restricted operations include:

- queue search and `POST /internal/v1/risk/review-tasks/{id}/claim` with lease/version;
- protected evidence retrieval with purpose, case/task, reason, and step-up token;
- `POST /internal/v1/risk/review-tasks/{id}/decisions` using an approved action catalog;
- appeal assignment and decision by an independent authorized reviewer;
- policy/model simulation, approval, activation, canary, rollback, and kill switch;
- signal/decision/restriction timeline and reconciliation exception views;
- label proposal, adjudication, dispute, reversal, and training eligibility controls.

Bulk actions require preview, bounded query snapshot, maximum target count, maker-checker where
material, dry-run result, idempotent execution, and per-target outcome. Review APIs never provide a
generic database-update primitive.

### Versioning, authorization, and response behavior

Mutating operations use idempotency keys and, for existing resources, optimistic `version` or
`If-Match`. APIs return the original resource on safe replay. `202 Accepted` is suitable only when a
durable challenge/review/report resource exists; it must not mask an unrecorded asynchronous task.

Actor/resource authorization is recalculated on every call. Possessing an opaque decision,
challenge, report, task, appeal, or evidence ID is insufficient. Public enumeration uses opaque IDs,
consistent not-found responses, rate limiting, and no differences that reveal account/listing risk
state to unrelated callers.

### Error semantics

| HTTP | Stable code | Meaning and client behavior |
| --- | --- | --- |
| `400` | `RISK_REQUEST_INVALID` | Malformed or unregistered action/context; correct request, do not retry unchanged |
| `401` | `AUTHENTICATION_REQUIRED` | No valid session; authenticate through identity flow |
| `403` | `ACTION_NOT_PERMITTED` | Authorization or non-disclosable restriction; do not infer detector details |
| `404` | `RISK_RESOURCE_NOT_FOUND` | Missing or inaccessible resource, intentionally indistinguishable |
| `409` | `IDEMPOTENCY_KEY_REUSED` | Same key with different normalized request |
| `409` | `RISK_DECISION_STALE` | Resource/policy/action binding no longer valid; request a new evaluation |
| `409` | `CHALLENGE_STATE_CONFLICT` | Challenge already terminal or wrong sequence; fetch current state |
| `409` | `REVIEW_VERSION_CONFLICT` | Another reviewer/process changed the task; reload before acting |
| `422` | `ADDITIONAL_VERIFICATION_REQUIRED` | Complete the returned safe challenge |
| `422` | `ACTION_LIMITED` | Respect exact allowed scope/limit; do not retry outside it |
| `422` | `CONTENT_NOT_PERMITTED` | Exact revision violates policy; edit or appeal if offered |
| `423` | `ACTION_UNDER_REVIEW` | Durable bounded review exists; poll/back off using returned timing |
| `429` | `ACTION_RATE_LIMITED` | Retry after server-provided duration; repeated attempts may extend policy response only by rule |
| `503` | `RISK_DECISION_UNAVAILABLE` | Registered safe fallback cannot produce a decision; retry with backoff |

`DENY` may map to `403` or `422` depending on whether the request is forbidden or valid but fails a
business protection condition. Public error bodies carry correlation ID, safe reason family,
user-action options, retry timing, and appeal link where allowed. They exclude scores, thresholds,
related entities, internal notes, raw provider results, and protected evidence.

## Event contracts

Events are committed past-tense facts published through a transactional outbox. Every event includes
event ID, schema version, occurred time, producer, aggregate type/ID/version, correlation and
causation IDs, actor type/ID where permitted, market/legal-entity scope, and sensitivity metadata.
Payloads contain identifiers and safe classifications, not raw messages, identity documents,
payment credentials, device fingerprints, exact graph links, or reviewer notes.

### D15-produced events

- `RiskSignalRecorded`, `RiskFeatureSnapshotCreated`, `RiskPredictionRecorded`;
- `RiskDecisionMade`, `RiskDecisionSuperseded`, `RiskDecisionEnforced`,
  `RiskDecisionEnforcementFailed`;
- `RiskChallengeRequired`, `RiskChallengePassed`, `RiskChallengeFailed`, `RiskChallengeExpired`;
- `CapabilityRestrictionActivated`, `CapabilityRestrictionExpired`,
  `CapabilityRestrictionRevoked`;
- `RiskReviewQueued`, `RiskReviewEscalated`, `RiskReviewDecided`;
- `ContentRevisionAssessed`, `ContentQuarantined`, `ContentModerated`,
  `SafetyContentEscalated`;
- `RiskAppealSubmitted`, `RiskAppealDecided`;
- `RiskLabelProposed`, `RiskLabelConfirmed`, `RiskLabelReversed`;
- `RiskPolicyActivated`, `RiskPolicyRolledBack`, `RiskModelContributionDisabled`.

`RiskDecisionMade` minimally identifies decision, protected action, subject/resource, outcome,
policy version/epoch, evaluated/expiry times, safe internal reason codes, review/challenge reference,
and feature snapshot digest. Consumers fetch protected detail only with workload authorization.

### Consumed facts

D15 consumes committed identity/session/contact changes; host/compliance capability; listing/media/
address versions; availability/quote/booking lifecycle; payment/refund/dispute provider evidence;
ledger/payout destination/release/outcome; message/content/report; incident/stay; review; promotion;
and D16 case outcomes. It does not treat a delivery attempt, search projection, warehouse row, or
model output as authoritative domain state.

Examples include `SessionRevoked`, `PayoutAccountChanged`, `ListingChanged`, `BookingConfirmed`,
`PaymentCaptured`, `ProviderDisputeUpdated`, `PayoutSubmitted`, `MessageSent`, `IncidentOpened`,
`ReviewPublished`, and `CaseClosed`. Each consumer uses an inbox/deduplication key and tolerates
unknown future fields and out-of-order versions.

### Ordering, replay, and corrections

There is no global order. Consumers order only within a declared aggregate stream using aggregate
version or source sequence. Stale events may still contribute historical evidence, but they cannot
overwrite newer projections. Corrections emit new events referencing the original; erasure or
redaction propagates tombstone/purpose changes without silently corrupting financial/legal evidence.

Rebuilding projections from events must reproduce applicable feature watermarks and decisions from
stored snapshots. Replay never re-executes domain side effects. An explicit replay mode suppresses
new notifications, restrictions, provider calls, and labels unless a separate authorized recovery
command requests them.

## Concurrency and idempotency

### Evaluation races

Normalize and hash the decision request before inserting the canonical evaluation record. A unique
constraint on action/actor/resource/command/policy epoch selects one winner. Losers read and return
the committed decision. If inputs differ under one client idempotency key, reject with conflict.

Fact acquisition and optional provider/model work occur without holding authoritative domain locks.
Before enforcement, the domain checks decision subject/action/resource version and expiry. If facts
changed, it requests a new decision rather than extending or editing the stale one.

### Policy activation races

An evaluation binds one policy epoch at start and records it. Atomic configuration publication makes
new requests see either old or new complete policy sets, never a partially updated mix. In-flight
decisions may finish under the old epoch within its configured grace; enforcement rules define
whether a severe emergency revocation invalidates them immediately.

### Restriction composition and expiry

Create/supersede restrictions under a target/scope lock or serializable command with optimistic
version. Enforcement evaluates all currently effective restrictions using deterministic precedence.
An expiry worker uses compare-and-set on state/version; a late expiry cannot revoke a newer
restriction. Revocation and appeal resolution append history and invalidate caches after commit.

### Review and appeal races

Queue claim uses `SELECT ... FOR UPDATE SKIP LOCKED` or atomic lease update. Only the lease owner and
version may decide; expired lease and stale version fail. A unique terminal-action identity prevents
double decision. Appeal submission is unique per decision/appellant/appeal round while allowing
explicitly authorized new evidence or escalation. An appeal result cannot override a newer unrelated
restriction accidentally.

### Counters, signals, and event processing

Signals are unique by source/provider event identity. Counter updates record contributing event IDs
or use an idempotent aggregation ledger; retries do not increment twice. For strong transaction
limits, lock the relevant counter/budget row or enforce a database constraint. Eventually consistent
features may be stale only when policy defines a safe fallback.

Outbox records commit with D15 state. Consumers insert inbox identity before effect and mark complete
only in the same local transaction as the effect. Poison records move to a visible exception queue;
they are not acknowledged as successfully processed.

### Domain-effect races

- A booking decision does not reserve inventory; booking revalidates and claims atomically.
- A risk review cannot extend an inventory hold beyond booking policy without a new authorized hold.
- Payment/provider success racing with risk deny is resolved by payment/cancellation compensation,
  never by deleting the provider fact.
- Payout submission racing with a new hold uses finance's eligibility lock/fence; if already
  submitted, provider/ledger recovery owns the outcome.
- Content edit racing with moderation binds decisions to revision; an old approval cannot publish a
  new revision.
- Account recovery racing with attacker action uses session/security-change versions and revokes
  stale continuation/challenge tokens.

No database transaction or lock remains open across identity, payment, moderation, malware, device,
model, object-storage, messaging, or other uncontrolled network calls.

## Security, privacy, and access control

### Authorization and privileged actions

Apply both role and resource/purpose authorization. Guest/host users see only their own safe
decisions, challenges, restrictions, reports, and appeals. Reviewers see only assigned/eligible
queues and just-in-time evidence. Service workloads receive action-specific scopes. Admin status
alone is insufficient for severe safety data, raw identity evidence, financial instruments, device
links, or bulk export.

Require step-up and immutable audit for account restriction, session revocation, listing removal,
payout hold/release request, broad content action, label confirmation, appeal decision, policy/model
activation, bulk action, evidence export, and break-glass. Maker-checker applies to high-impact or
large-scope changes. Reviewer actions carry authority source and maximum permitted scope.

### Data minimization and separation

Maintain a data inventory for identifiers, network/device data, behavioral events, content,
location, travel, identity, payment/payout references, allegations, safety evidence, labels, and
models. Each field has purpose, collection notice/lawful basis, market availability, retention,
owner, recipients, region, and deletion/correction behavior.

Separate:

- authentication secrets and raw tokens from risk signals;
- raw identity/biometric documents from decision summaries;
- payment credentials/bank details from tokenized relationship references;
- fairness audit attributes from operational decision features;
- raw message/incident content from general feature/logging systems;
- reviewer identity/internal notes from user-facing explanations;
- analytics/training copies from transactional evidence.

Device or network intelligence must be proportionate, salt/key-rotatable where hashed, resistant to
cross-purpose tracking, and short-lived unless justified. Do not create covert permanent identity
from device signals. Exact location, contacts, communications, and travel history are not general
graph features.

### Secrets, storage, and logging

Encrypt sensitive fields and evidence at rest with managed keys and scoped decrypt permissions.
Provider credentials live in secret management and rotate. Signed evidence URLs are short-lived,
audience-bound, non-guessable, and `Cache-Control: no-store`. Malware scanning and reviewer preview
run in isolated contexts.

Logs, traces, metrics, events, alerts, and error responses exclude raw content, documents, access
secrets, payment/bank data, full IP/device identifiers, graph neighbors, and allegations. Use opaque
IDs and coarse reason categories. Log viewing and risk-query activity is itself audited and monitored.

### Retention, rights, and legal holds

Retention is data-class and market specific. Expire transient device/network and unsuccessful
challenge data quickly; retain contractual, fraud, safety, financial, moderation, and appeal evidence
only for approved purposes and periods. Legal holds are scoped, authorized, time-reviewed, and
released explicitly.

Export/correction/deletion workflows protect other users, reporter identity, detection integrity,
legal obligations, and immutable accounting/contract history. Deletion propagates to feature stores,
warehouse, caches, training corpora, and future model builds. When lawful evidence must remain,
restrict and pseudonymize it rather than representing it as fully deleted.

### Abuse of the protection system

Protect report, appeal, recovery, verification, evidence upload, challenge, and reviewer workflows
against spam, enumeration, denial of service, social engineering, and brigading. Rate limits are
actor/resource/network aware but always retain accessible urgent-safety and account-recovery paths.
Support/reviewer prompts never reveal how to evade exact thresholds.

### Fairness and non-discrimination

Before using a feature, assess necessity, error mechanism, proxy risk, affected groups, accessibility,
and market law. Monitor challenge/deny/hold/review and appeal reversal rates by approved fairness
slices with privacy-protected access. Compare like-for-like action/risk populations and investigate
coverage/label bias rather than assuming equal aggregate rates are correct.

Users receive alternative verification where feasible. Language, disability-related behavior,
shared networks, travel patterns, name/address formats, payment access, and new-to-platform status
must not be treated as fraud without validated evidence. An accountable human owns remediation when
disparate harm is detected.

## Observability and operations

### Business and protection metrics

- confirmed fraud/loss and prevented exposure by action, market, policy, attack family, and cohort;
- ATO recovery volume/time, compromised-session containment, and repeat compromise;
- listing/content/review violation prevalence, report rate, time exposed, restoration and recurrence;
- booking/payment/payout challenge, hold, review, deny, and completion outcomes;
- chargeback, refund-abuse, promotion-abuse, fake-review, and payout-diversion outcomes;
- safety report acknowledgment/mitigation/escalation time, never optimized against commercial cost;
- user friction, checkout abandonment after challenge, host publication delay, and support contacts;
- false-positive estimates, appeal rate/outcome/time, reviewer disagreement, and repeat review.

Metrics distinguish decisions, enforced interventions, provider outcomes, confirmed labels, and
estimated prevented loss. They must not claim prevention from a denied action without a valid
counterfactual or mature label.

### Correctness and technical metrics

- evaluation volume/latency by tier/action/policy, timeout, fallback, error, and missing-feature rate;
- policy resolution/version distribution, shadow/canary delta, kill-switch and stale-decision rate;
- decision-to-enforcement mismatch, unenforced/stale decisions, expired holds/restrictions, and
  domain reconciliation age;
- signal/event ingestion lag, duplicate/out-of-order rate, counter drift, feature freshness and
  online/offline skew;
- model latency/error/calibration/drift and prediction distribution by version/slice;
- review queue depth/oldest age, lease expiry, reassignment, SLO breach and reviewer throughput;
- moderation scan/classifier latency, quarantine age, provider outage, and unsafe restoration;
- outbox/inbox lag, retry/dead-letter volume, provider-webhook age, and recovery duration;
- privileged access, bulk export, break-glass, override and policy-change anomalies.

### SLO candidates and alerting

Set SLOs by protected action tier. T4 safety intake/routing and T3 payout/security changes have
separate availability and paging expectations from T0 analytics. Candidate objectives include
evaluation availability/latency, challenge continuation, maximum manual-review hold time,
urgent-content routing, restriction propagation/revocation, appeal acknowledgment/decision, and
event/reconciliation freshness.

Page on failed T4 routing, broad enforcement mismatch, policy/config corruption, restriction cache
leak, suspicious privileged access, payout-hold bypass, decision-store outage without safe fallback,
or a model/policy causing a sharp adverse-action spike. Queue growth and label/model drift may ticket
or page according to exposure and deadline.

### Dashboards, runbooks, and manual operations

Maintain dashboards for account security, listing integrity, booking/payment, payout, promotions,
content/moderation, safety, appeals/fairness, models, providers, and system correctness. Every metric
links to definition and owner.

Runbooks cover provider/model outage, feature staleness, policy rollback, false-positive spike,
credential attack, fake-listing campaign, card testing, payout diversion, scam-message outbreak,
malware, report brigading, review manipulation, severe safety escalation, reviewer backlog,
privacy/security incident, event replay, and restore/reconciliation. Manual operations use product
commands with reason, preview, idempotency, approval, outcome, and audit—never direct SQL edits.

## Failure behavior

| Failure or race | Required behavior |
| --- | --- |
| Risk decision store unavailable | Use registered per-action fail-safe; T2/T3 may challenge/hold/decline safely, T4 intake still routes; never invent allow |
| Optional model timeout/error | Apply deterministic policy and record fallback; stay within action latency budget |
| Required authoritative fact unavailable | Return explicit unavailable/challenge/review according to tier; do not coerce missing to safe |
| Risk vendor timeout or ambiguous response | Persist unknown operation, query/reconcile idempotently; never resubmit blindly |
| Signal/event duplicate | Deduplicate by source identity; no repeated counter, restriction, task, or label |
| Signal/event arrives out of order | Record historical evidence and version; do not overwrite newer projection or replay past side effect |
| Feature projection stale | Enforce freshness policy and deterministic fallback; emit metric and repair asynchronously |
| Policy version invalid/partial | Reject activation; serve last approved complete epoch and alert |
| Policy causes adverse-action spike | Kill switch/rollback future evaluations, preserve decisions, investigate impacted users and restoration |
| Review queue misses deadline | Escalate/reassign; apply predeclared release/challenge/deny fallback; never hold inventory/money indefinitely |
| Reviewer crashes after decision commit | Outbox/enforcement reconciliation resumes effect exactly once |
| Domain enforcement fails after risk decision | Keep decision and exception; retry/re-evaluate by contract, never claim restriction applied |
| Domain commits but acknowledgment is lost | Reconcile by domain command/decision ID and record enforcement once |
| Restriction expiry worker is late | Effective-time check stops enforcement at expiry; worker catches up without extending it |
| Appeal races with newer decision | Bind result to exact target; compose/supersede only authorized scope, preserve newer unrelated control |
| Content scanner unavailable | Quarantine required types; approved low-risk text uses deterministic policy; urgent report path remains |
| Moderation decision arrives after edit | Apply only to exact revision; evaluate current revision separately |
| Account compromise suspected during payout change | Revoke/step-up sessions, hold exact capability, preserve host payable, route review |
| Payment succeeds after risk/booking expiry | Payment/booking compensation and reconciliation own outcome; never delete provider evidence |
| Payout already submitted before new hold | Finance/provider recovery owns in-flight movement; new hold applies to future eligibility and case handling |
| Warehouse/training pipeline unavailable | No transactional impact; buffer/rebuild with lineage and deletion propagation |
| Evidence storage unavailable | Preserve intake metadata and safe containment; retry upload/access, do not close case as evidence-free success |
| Regional/privacy control unavailable | Stop prohibited collection/processing for affected scope and use approved reduced-feature policy |

## Testing and verification

### Deterministic and policy tests

- Golden tests for every policy version, action tier, precedence, missing-value path, reason mapping,
  expiry, locale, market, and kill-switch state.
- Boundary/property tests for velocity windows, currency amounts, distinct counts, late events,
  threshold equality, cooldown, challenge attempts, restriction intervals, and review deadlines.
- Policy schema rejection, unsafe combination, unreachable branch, expired dependency, rollout
  assignment, simulation reproducibility, and old-version replay.
- Domain matrix tests prove D15 cannot create availability, price, booking, payment, ledger, payout,
  message, review, incident-remedy, or support-case truth.

### Database and concurrency tests

- Real PostgreSQL tests for evaluation uniqueness, source-event dedupe, immutable/superseding
  decisions, one content revision sequence, effective restrictions, task leases, and appeal rounds.
- Concurrent same-command evaluation returns one decision; different payload under same key conflicts.
- Review claim/expiry/reassignment produces one effective reviewer decision.
- Restriction activate/expire/revoke/appeal races compose correctly and stale versions fail.
- Counter/event retry cannot double count; out-of-order corrections preserve newer state.
- Decision enforcement/acknowledgment crash points reconcile exactly once.

### Domain and provider integration tests

- Account recovery, session revocation, listing publish/edit, booking/hold expiry, payment action,
  refund, payout destination/release, message/review moderation, and incident escalation contracts.
- Signed webhook/observation verification, replay, invalid signature, duplicate provider event,
  timeout-before/after provider success, unknown state, query recovery, and provider schema drift.
- Malware/content/identity/device providers use contract fixtures without real sensitive data and
  prove data-minimization/redaction behavior.
- Payment/risk race, payout/risk race, booking inventory TTL versus review SLA, content edit versus
  moderation, and account recovery versus attacker session.

### Security, privacy, and abuse tests

- Cross-user/resource/market/queue access, guessed IDs, stale assignment, privilege escalation,
  maker-checker bypass, self-review, break-glass expiry, and audit completeness.
- Injection, malicious file/type mismatch, decompression bomb, executable preview, unsafe URL/redirect,
  Unicode obfuscation, prompt injection, and stored cross-site scripting defenses.
- Log/event/error/cache scans ensure absence of raw credentials, documents, bank/payment data,
  sensitive content, device identifiers, graph neighbors, reporter identity, and access secrets.
- Export/correction/deletion/legal-hold propagation into features, warehouse, evidence, labels,
  caches, and retraining sets.
- Rate-limit/report/appeal/challenge/recovery abuse and T4 safety-path availability under attack.

### Model, fairness, and adversarial tests

- Point-in-time training joins, matured labels, selective-label bias, leakage, online/offline parity,
  missingness, calibration, chronological split, and unseen-entity/market tests.
- Precision/recall/cost and false-positive/appeal outcomes by action, market, language, device/network
  context, new-user status, accessibility path, and legally approved fairness slices.
- Adversarial mutation, coordinated low-and-slow attacks, graph poisoning, model extraction, evasion,
  drift, data/provider outage, and confidence fallback.
- Shadow/canary decision-delta replay proves models cannot bypass deterministic hard rules and kill
  switch returns to baseline within the SLO.

### Recovery and operational acceptance

- Crash/failure injection at every outbox, provider, decision, restriction, task, enforcement,
  notification, appeal, and label transition.
- Backup restore followed by inbox/outbox replay and reconciliation proves decisions, active
  restrictions, deadlines, evidence links, and domain enforcement agree.
- Reference scenarios can be handled without SQL: ATO recovery, false listing, suspicious checkout,
  payment success after timeout, payout diversion, scam message, fake review, severe safety report,
  appeal restoration, and mistaken restriction.
- Reviewers can explain which facts, policy, model/rule version, authority, action, and appeal path
  produced every consequential result.

## Caching, performance, and scaling

Cache immutable policy/model metadata by version and publish the active policy epoch atomically.
Cache bounded, non-sensitive subject features with explicit as-of/watermark, maximum age, market,
purpose, and invalidation. Restriction caches are advisory performance layers: authoritative T2/T3
commands perform a version/freshness-aware check and fail safely if they cannot establish current
state. Do not cache raw content, documents, graph neighborhoods, access secrets, or plaintext device/
payment identifiers.

Critical query/index shapes include:

- decision lookup by canonical evaluation key and subject/action/time;
- active restrictions by target/scope/effective interval;
- review tasks by queue/state/priority/deadline with lease-ready partial indexes;
- signal dedupe and subject/type/event-time windows;
- provider observation by provider account/reference/event ID;
- content item/revision and effective moderation decision;
- appeal by appellant/state/deadline and label by subject/taxonomy/available time;
- reconciliation scans by state/next-attempt/oldest age.

Bound synchronous work: registered feature allowlist, fixed windows, candidate/entity caps, one
policy epoch, strict provider/model deadlines, and response size limit. Separate T4 routing capacity
from commercial evaluation traffic. Apply backpressure to optional enrichment and analytics before
protection or safety paths.

Partitioning may eventually follow signal/event time and region; independent model serving,
moderation workers, or risk evaluation deployment may follow latency/scaling/reliability needs. A
graph store is justified only by measured relational query limits and protection value. Each
extraction needs contract versioning, regional/privacy boundary, replay, fallback, SLO, and owner.

Capacity tests model credential attacks, card testing, fake-account bursts, viral listing/report
traffic, checkout peaks, payout batches, moderation backlog, and regional provider outage. Scale
must preserve idempotency and evidence—not merely low average latency.

## Appropriate use of AI

AI/ML can assist with:

- calibrated account, listing, booking, payment, payout, promotion, or review-abuse prediction;
- anomaly and relationship-pattern scoring on approved, minimized features;
- image/text duplication and listing-claim consistency review;
- phishing, off-platform payment, harassment, discrimination, threat, spam, malware, and unsafe
  content classification;
- language identification and translation for reviewer assistance while retaining originals;
- review-task priority within deterministic severity/deadline floors;
- evidence-grounded timeline summary, duplicate clustering, and recommended missing questions;
- policy simulation, attack-pattern exploration, and reviewer quality sampling.

Prerequisites are a defined target/horizon, mature/adjudicated labels, point-in-time features,
privacy/legal approval, representative multilingual evaluation, calibrated confidence, latency and
cost budgets, reviewer capacity, versioned artifacts/prompts, evidence citations, fairness and drift
monitoring, human override/appeal, shadow/canary rollout, and a tested kill switch.

Large language model (LLM) summaries and recommendations must cite authorized internal evidence,
mark uncertainty, resist prompt injection from user content, and never turn allegations into facts.
Private communications and safety evidence are not general-purpose training data. Provider retention
and model-training terms require approval.

AI/ML must not own:

- identity truth, authentication, sanctions/legal determination, or account entitlement;
- listing factual truth, availability, quote, price, tax, promotion budget, or booking state;
- payment/refund/payout movement, ledger posting, financial ownership, or confiscation;
- final severe-safety response, emergency guidance, guilt, legal conclusion, or law-enforcement
  disclosure;
- irreversible/high-impact restriction, content removal, claim/remedy, or appeal without the
  versioned policy and authorized human/deterministic authority required for that action.

Disabling every model and LLM must leave authentication protections, deterministic limits,
authoritative domain rules, essential moderation/quarantine, challenge/review routing, appeal, and
urgent safety intake operational.

## Rollout plan

### Phase 0 — Governance, threat model, and reference cases

Approve launch market/legal role, threat taxonomy, protected-action registry/tiers, intervention and
reason catalog, privacy/data inventory, evidence/retention, reviewer roles/limits, appeal/disclosure,
fairness slices, SLOs, safety runbooks, and D15/D16 boundary. Define loss/harm reference cases and
baseline metrics.

Exit: accountable product, risk, safety, legal/privacy, security, identity, listing, booking,
payments, finance, operations, support, and data owners approve the decision/enforcement contract and
MVP threat coverage.

### Phase 1 — Decision, audit, and restriction foundation

Add forward migrations for policy versions, immutable risk decisions, rule hits, scoped restrictions,
outbox/inbox, enforcement acknowledgments, and privileged audit. Implement registered action
evaluation with deterministic fallback and current account-status compatibility.

Exit: reference decisions replay from stored inputs; retry produces one result; active restrictions
are enforced at authoritative boundaries; expiry/revocation/recovery and audit work without SQL.

### Phase 2 — Account security and high-risk change protection

Protect login/recovery/contact change, role/capability change, payout-destination change, and critical
admin actions with deterministic velocity, session/security-change versions, step-up challenges,
cooldowns, and scoped restriction/session-revocation requests.

Exit: credential attack and payout-diversion scenarios are detected/contained; legitimate recovery,
alternate challenge, expiry, and false-positive restoration meet SLOs.

### Phase 3 — Listing, booking, payment, and payout vertical slice

Add listing publish/material-edit evaluation, checkout risk decision within inventory TTL, payment
action controls, payout-release eligibility input, provider observation inbox, bounded review queue,
and cross-domain enforcement reconciliation. Begin with explainable deterministic rules and one
launch provider/market.

Exit: fake-listing, suspicious checkout, card-testing, payment-timeout, self-booking, and payout-hold
reference cases preserve inventory/money authorities and recover under duplicate/late events.

### Phase 4 — Content moderation and user reporting

Add stable content revisions, secure attachment quarantine/scan, listing/message/review moderation,
contact/link masking, report intake, urgent safety routing, reviewer language/skill queues, additive
removal/restoration, and reporter privacy.

Exit: prohibited-content reference cases route correctly; exact revisions and evidence are
reproducible; edits, provider outage, brigading, appeal, and safety escalation meet policy/SLO.

### Phase 5 — Appeals, labels, and operational quality

Add independent appeals, maker-checker, reviewer conflict/quality sampling, adjudicated label
taxonomy, domain/support outcome ingestion, fairness and false-positive dashboards, policy replay/
simulation, and comprehensive runbooks.

Exit: every high-impact action has an accessible permitted explanation/appeal, reviewer actions are
bounded/audited, labels distinguish allegation from confirmation, and policy deltas are measurable.

### Phase 6 — Governed ML assistance

Build point-in-time datasets and start models/classifiers in offline and shadow modes. Progress to
reviewer assistance and bounded canary automation only after calibration, multilingual/adversarial/
fairness evaluation, workload capacity, drift monitoring, and kill-switch rehearsals.

Exit: models improve an approved loss/harm/friction objective over deterministic baseline without
violating latency, fairness, appeal, privacy, or domain-authority guardrails; disabling them is safe.

### Phase 7 — Relationship intelligence and multi-market depth

Add bounded entity-link analysis, coordinated-abuse investigations, more providers/rails/languages,
market-specific policy packs, and automation based on measured need. Consider graph infrastructure or
service extraction only after profiling.

Exit: each market has approved data/use/policy/reviewer/safety readiness, graph signals are
explainable and proportionate, and scale changes demonstrate benefit greater than operational and
privacy cost.

## Verification checklist

### Functional and authority

- [ ] Threat families, protected actions, tiers, actors, owners, and D15/D16 boundary are explicit.
- [ ] Signals, evidence, features, predictions, policies, decisions, restrictions, labels, and domain
  outcomes are separate versioned concepts.
- [ ] Risk decisions cannot create inventory, booking, price, tax, payment, ledger, payout, review,
  message, stay outcome, claim, or remedy facts.
- [ ] Every protected domain verifies decision binding/freshness and records enforcement identity.
- [ ] Allow/challenge/hold/review/limit/deny semantics, precedence, expiry, fallback, reason, and appeal
  are deterministic.
- [ ] Account, listing, booking, payment, payout, promotion, content, review, and safety reference
  cases have explicit controls and residual risk.
- [ ] Manual review cannot retain inventory or money beyond approved bounded policy.
- [ ] Content decisions bind exact immutable revisions; original evidence and restoration history
  remain protected and reproducible.

### Concurrency and recovery

- [ ] Evaluation, provider observations, signals, counters, reports, reviews, appeals, labels, outbox,
  and inbox have stable idempotency/deduplication identities.
- [ ] Database uniqueness/locking/versioning selects one effective decision, restriction transition,
  reviewer action, and enforcement acknowledgment under concurrency.
- [ ] Policy activation is atomic; replay uses the recorded epoch; rollback affects future decisions
  without rewriting history.
- [ ] Stale decisions, late events, provider ambiguity, expired leases, and domain-effect races have
  tested reconciliation/compensation paths.
- [ ] No database lock/transaction crosses an uncontrolled provider, model, storage, or scan call.
- [ ] Backup/restore plus replay preserves active restrictions, deadlines, evidence, audit, and domain
  enforcement agreement.

### Security, privacy, fairness, and safety

- [ ] Every collected datum and feature has purpose, necessity, market approval, sensitivity,
  retention, access, deletion/correction, and owner.
- [ ] Protected attributes/fairness data are access-separated and not unreviewed operational inputs.
- [ ] Public responses/logs/events/metrics/caches exclude detector detail, graph neighbors, reporter
  identity, secrets, raw documents, credentials, bank/payment data, and unnecessary content.
- [ ] Reviewer and admin permissions, conflicts, step-up, maker-checker, bulk actions, evidence access,
  export, and break-glass are bounded and audited.
- [ ] Challenges/appeals provide accessible alternatives where feasible; impact and reversal are
  monitored by approved fairness slices.
- [ ] T4 safety intake/routing remains available during account/content limits, attack, queue backlog,
  provider failure, and AI/model outage.
- [ ] Export/deletion/legal-hold changes propagate to evidence, signals, features, warehouse,
  training data, caches, and subsequent model builds.

### Operations and ML

- [ ] Protection, correctness, friction, false-positive, appeal, fairness, latency, queue, provider,
  event, policy, and model metrics have definitions, owners, alerts, dashboards, and runbooks.
- [ ] Reviewer SLO/fallback and staffing capacity are approved for each protected action tier.
- [ ] Policy simulation/shadow/canary/rollback reports decision delta, exposure, user friction,
  reviewer demand, latency, and fairness.
- [ ] Labels preserve source/confidence/adjudication and avoid outcome leakage, circular labels, and
  treating allegations/declines/chargebacks as automatic fraud truth.
- [ ] Every model has target/horizon, model card, point-in-time features, mature labels, calibration,
  slice/adversarial tests, fallback, owner, expiry, monitoring, and kill switch.
- [ ] Disabling optional vendors, models, and LLMs leaves the deterministic safety floor and
  authoritative domain behavior operational.
- [ ] MVP scope and phase exit criteria are measurable; graph infrastructure and broad automation are
  deferred until evidence justifies them.

## Decisions required before implementation

Each consequential choice should be recorded in an architecture decision record (ADR) with owner,
date, context, alternatives, decision, consequences, rollout, metrics, expiry or revisit trigger.

1. Launch-market threat model, marketplace/legal role, regulatory risk duties, prohibited uses, and
   risk accepted by the platform versus providers/hosts/guests.
2. D15 versus identity/compliance/D16/support/safety/finance authority, including who may request and
   who may enforce each restriction, contract change, funds hold, remedy, or disclosure.
3. Protected-action registry, T0–T4 tier for each action, latency budget, allowed outcomes, maximum
   hold/review duration, failure fallback, and enforcement owner.
4. Risk decision, reason, intervention, content, incident, and confirmed-label taxonomies, including
   stable internal versus user-facing reason mappings.
5. Account-wide suspension criteria versus scoped restriction defaults, effective/expiry/review
   semantics, session revocation, essential-access exceptions, and reactivation authority.
6. Registration/login/recovery/contact-change security signals, challenge methods, attempt/velocity
   limits, new-device/security-change cooldowns, and legitimate-owner recovery path.
7. Host KYC/KYB, sanctions/AML, licensing, beneficial-owner, and market-capability provider/owner
   boundary; retention and appeal of provider results.
8. Listing verification and material-edit policy: address/property proof, image/content provenance,
   duplication, prohibited categories, quarantine, publication SLO, and restoration.
9. Checkout risk-review timing relative to quote and inventory-hold TTL, request-to-book behavior,
   payment sequencing, and user outcome when review cannot finish.
10. Payment risk and strong-customer-authentication responsibilities, provider data permitted in
    D15, ambiguous provider recovery, card-testing limits, and friendly-fraud treatment.
11. Payout-destination verification, cooling period, step-up, payout hold/release interface, reserve/
    compliance/dispute composition, in-flight payout race, and host explanation.
12. Promotion/referral/credit abuse scope, entity-link use, benefit-only versus account restriction,
    and recovery for legitimate shared households/businesses.
13. Message/listing/review/profile content policy, private-message scanning purpose, contact/off-
    platform-payment rules, language coverage, synchronous versus asynchronous moderation, and
    exact user disclosures.
14. Attachment/image/URL scanning vendors, quarantine behavior, supported types/sizes, isolated
    preview, provider retention/region, outage fallback, and appeal/evidence policy.
15. Urgent-safety categories and deterministic severity floor, market emergency guidance, on-call
    ownership, external disclosure rules, preservation, and separation from ordinary fraud queues.
16. User-report deduplication, reporter protection, brigading prevention, status disclosure, abuse
    penalties, and emergency-path rate-limit exceptions.
17. Reviewer roles/skills/markets/languages, queue priority, leases, SLO/escalation, conflict rules,
    productivity/quality policy, step-up, monetary/capability limits, and maker-checker thresholds.
18. Appeal eligibility, deadline, number of rounds, independent-review requirement, accessible
    submission, evidence rules, explanation limits, restoration, and D16 service-recovery handoff.
19. Data inventory and feature allowlist: device/network/location/content/behavior/identity/payment/
    graph purposes, notice/lawful basis, region, hashing/linkability, retention, access, and deletion.
20. Protected attributes and proxy review, fairness audit population/slices/metrics/thresholds,
    privacy-separated analysis, remediation owner, and market legal approval.
21. Evidence integrity, object storage, encryption/key separation, access audit, legal hold, export,
    correction/deletion, reporter/third-party data handling, and retention schedule.
22. Policy language/schema, precedence, simulation dataset, approval authority, shadow/canary
    assignment, emergency expiry, rollback, and policy-epoch grace for in-flight decisions.
23. Signal/event contracts, canonical subject identifiers, device/instrument privacy-preserving
    linkage, velocity event-time behavior, feature freshness, reconciliation, and backfill provenance.
24. Label taxonomy and maturation/adjudication: chargeback/decline/dispute, ATO, damage, cancellation,
    content, fake listing/review, appeal reversal, reviewer disagreement, and training eligibility.
25. Initial deterministic rules and thresholds, loss/harm/friction objectives, manual-review capacity,
    reference scenarios, baseline period, and phase exit gates.
26. Model/provider build-versus-buy choices, target/horizon, training data, multilingual coverage,
    explainability, calibration, adversarial/fairness gates, latency/cost, retention, and kill switch.
27. Relationship/graph analysis necessity, link types/confidence/expiry, benign-sharing safeguards,
    reviewer exposure, infrastructure threshold, and prohibition on association-only adverse action.
28. Public API explanation vocabulary, challenge/restriction/report/appeal UX, retry timing,
    accessibility/localization, notification channel, and security-based nondisclosure.
29. Observability/SLO/error-budget definitions, alert severity, reviewer/provider capacity, policy/
    model drift, false-positive incident response, and executive risk reporting without misleading
    prevented-loss claims.
30. Legacy migration and rollout: how current `users.status`, host identity status, existing content,
    bookings/payments/reviews, and unassessed history map to target records without fabricating past
    approval, reasons, or decisions.
