# Disputes, damage claims, insurance, and customer support

## Purpose

This document defines how Room Booking receives a problem, preserves evidence, coordinates people
and deadlines, reaches a governed decision, and executes an authorized remedy when the normal
booking journey does not resolve itself. It expands D16 in the
[marketplace problem breakdown](../marketplace-problem-breakdown.md) into an implementation-oriented
target design.

The central question is:

> Given a booking-related allegation or service failure, which facts can be established, which
> accepted policy applies, what response or remedy is authorized, who funds it, and how can every
> consequential action later be reproduced, reviewed, or appealed?

D16 owns support-case coordination, damage-claim adjudication, party negotiation, protection or
insurance submission, remedy authorization, case communications, and support quality review. It is
not a universal write surface over the marketplace. The
[booking design](availability-reservation-and-booking.md) owns stay-contract and inventory state;
the [cancellation design](cancellation-modification-and-refund.md) owns ordinary contractual
cancellation or modification entitlement; [payment orchestration](payment-orchestration.md) owns
provider collection, refund, and payment-dispute movement; the
[ledger and payout design](ledger-reconciliation-and-host-payout.md) owns economic postings, host
payable, reserves, recovery, and payout; [messaging and stay operations](messaging-notifications-and-stay-operations.md)
owns original communications, access and incident evidence; and
[trust and safety](trust-safety-fraud-and-moderation.md) owns risk, moderation, restrictions, and
fraud labels.
[Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md) owns
review rights, original revisions, publication, aggregates, and derived aspect/reputation evidence;
D16 may link or request review/moderation action but cannot edit those facts.

One support workspace may present a unified timeline, but it must retain those authority boundaries.
An agent uses allowlisted domain commands and never edits a booking, payment, provider case, journal,
payout, message, incident, review, restriction, or listing row directly.

[Data, experimentation, and machine-learning platform](data-experimentation-and-ml-platform.md)
owns generic analytical lineage, approved support labels/features, model registry, and prediction
monitoring. D16 owns case evidence access, routing constraints, agent authority, remedies, appeals,
and whether an advisory prediction may influence a human workflow.

## Status and dependencies

[Vietnam market readiness and internationalization](multi-market-compliance-and-localization.md)
owns market-specific authority, legal/provider context, retention, and localized policy evidence.

This is a target design. The repository does not currently implement support cases, queues, service-
level agreement (SLA) clocks, claims, evidence custody, negotiations, remedy decisions, agent
authority limits, protection/insurance integration, or support-specific Application Programming
Interfaces (APIs), workers, or Java modules.

Current foundations are narrower:

- [`001-identity.sql`](../../src/main/resources/db/changelog/changes/001-identity.sql) and the Java
  application provide authenticated users, roles, host onboarding, and coarse administrator account
  controls. They do not provide support-agent roles, delegated case access, purpose-bound access,
  skill routing, or monetary authority.
- [`002-listing-catalog.sql`](../../src/main/resources/db/changelog/changes/002-listing-catalog.sql)
  provides listing, host, address, media, and publication foundations. It does not preserve listing
  revision history, inspection results, or claim evidence.
- [`004-booking.sql`](../../src/main/resources/db/changelog/changes/004-booking.sql) provides booking
  parties, local stay dates, listing snapshots, total amounts, cancellation states, and overlap
  protection. It has no support timeline, case hold, contract revision, incident link, or remedy
  reference.
- [`005-payment.sql`](../../src/main/resources/db/changelog/changes/005-payment.sql) provides coarse
  payment attempts, refunds, and an idempotent webhook inbox. It has no chargeback/provider-dispute
  case, refund instruction, evidence manifest, recovery allocation, or support-case link.
- [`006-trust-engagement.sql`](../../src/main/resources/db/changelog/changes/006-trust-engagement.sql)
  provides verified-stay reviews and favorites. It is not a dispute, claim, safety, or support-case
  system.

The detailed booking, payment, cancellation, finance, operations, and trust documents describe
target contracts that D16 will call or consume; their existence does not imply implementation. All
proposed records here require new forward-only Liquibase migrations when implementation is
authorized. Existing applied migrations must remain unchanged. Historical backfill must use honest
provenance such as `LEGACY_IMPORTED` or `UNKNOWN`, not fabricated evidence or decisions.

Recommended dependency order:

1. Decide launch-market support scope, hours, severity definitions, emergency boundaries, policy
   owners, case taxonomies, remedy catalog, funding model, authority limits, retention, appeal, and
   protection-program terms.
2. Establish durable identity, booking, payment, cancellation, ledger, notification, incident,
   audit, outbox/inbox, and protected-file contracts.
3. Deliver booking-centric intake, immutable timeline, deterministic routing, SLA clocks, agent
   work queues, case notes, and safe read-only domain context.
4. Add controlled ordinary remedies through cancellation, payment, and ledger commands with exact
   policy, funding, approval, and idempotency.
5. Add damage claims, negotiation, evidence custody, payout holds, recovery, appeal, and protection
   decisions with human adjudication.
6. Add payment-provider disputes and insurance/protection-provider submission only after deadlines,
   export controls, reconciliation, and unknown-outcome recovery work.
7. Add AI summarization, routing, evidence retrieval, and recommendation only after reliable labels,
   citation coverage, privacy controls, evaluation, human authority, and kill switches exist.

The target release supports authenticated guest and host intake for a confirmed
booking, a booking-centric timeline, deterministic priority and ownership, human case handling,
approved full/partial refund and platform-credit remedies, independent approval above thresholds,
and complete audit. Damage protection, provider chargeback representment, automated evidence
classification, and third-party insurance recovery come later unless required by the launch market.

## Goals

- Give guests, hosts, operations, support, safety, risk, payment, and finance one correlated but
  authority-preserving case view.
- Accept reports through safe channels, acknowledge them promptly, and preserve urgent escalation
  even when optional dependencies fail.
- Represent classification, severity, queue, owner, SLA clocks, escalations, transfers, duplicates,
  reopenings, and closure explicitly.
- Preserve evidence integrity, provenance, custody, visibility, retention, redaction, and legal hold.
- Distinguish allegation, observation, finding, policy eligibility, liability allocation, remedy
  decision, external execution, and accounting effect.
- Reproduce each decision from immutable facts and the exact effective-dated policy, program terms,
  authority grant, and approval used at decision time.
- Support structured party negotiation without making silence, a chat message, or an expired offer
  an implicit admission.
- Authorize refunds, credits, host adjustments, reserves, reimbursements, platform expense, or claim
  payments only through a catalog with explicit funder and transactional ceilings.
- Submit provider disputes or protection/insurance claims idempotently and recover from unknown,
  duplicate, partial, or late outcomes.
- Prevent agents from bypassing booking, inventory, payment, ledger, payout, trust, moderation, or
  incident invariants.
- Protect case participants and sensitive allegations with least privilege, purpose-bound access,
  redacted logging, and complete audit.
- Provide explanations, appeals, quality review, reconciliation, and measurable operational
  outcomes without hiding uncertainty.
- Start as logical modules in the Spring Boot modular monolith and scale only from measured need.

## Non-goals

- Guaranteeing that every allegation can be proven or every dissatisfied party can be made whole.
- Replacing emergency services, law enforcement, courts, licensed insurance claims adjusters, legal
  advice, or market-required complaint bodies.
- Letting support reinterpret availability, price, tax, cancellation contracts, payment evidence,
  ledger balances, or provider status from free text.
- Treating a report, chargeback, refund request, negative review, device event, or model score as a
  confirmed fact, admission, fraud label, or liability decision.
- Giving agents a generic endpoint to change booking status, refund arbitrary amounts, edit
  balances, release payout, alter evidence, or overwrite provider cases.
- Making D16 the owner of original messages, incidents, listing revisions, reviews, payment events,
  risk restrictions, or financial journal entries.
- Keeping inventory, guest money, or host funds on an indefinite support hold.
- Promise-based automation that grants remedies from an email or chat response without an
  authenticated, versioned command.
- Building a general customer relationship management platform, contact center, legal-case system,
  insurer core, or workforce-management suite before marketplace needs justify it.
- Using a Large Language Model (LLM) as authority for safety severity, factual findings, policy,
  legal liability, fraud, remedy amount, insurance coverage, money movement, or case closure.
- Introducing microservices, event sourcing, Kafka, a vector database, or a graph database merely
  because a case timeline crosses domains.

## Core principles and invariants

### A case coordinates authoritative facts; it does not replace them

A case links immutable references to booking, payment, cancellation, ledger, incident, message,
listing, review, and risk facts. It may request actions from their owners. It may not copy a mutable
summary and later treat that copy as authority, and it may not directly mutate neighboring domain
state. Every projected fact records its source ID, version, observed time, and freshness.

### An allegation is not a finding

Intake preserves what a person reported, in their words and context. An observation records what a
source supplied. A finding records an authorized interpretation with evidence, standard of proof,
confidence or uncertainty, decision maker, and policy version. User-visible and internal displays
must not collapse these categories or present an accused party as culpable before adjudication.

### Policy eligibility, remedy, execution, and accounting are separate

The support decision records what remedy is authorized and who funds it. Cancellation establishes
ordinary contract entitlement; payment proves refund or charge movement; finance records ownership,
expense, recovery, reserve, and payout effects. A case is not resolved financially until required
downstream commands have known outcomes or explicit exception ownership.

### Every monetary effect has an explicit funder and ceiling

Each remedy line identifies beneficiary, economic funder, amount in integer minor units, ISO 4217
currency, source allocation, reason, policy/program version, tax/document treatment, approvals,
remaining ceiling, and downstream command. Host-funded, platform-funded, insurer-funded, partner-
funded, and guest-funded values never silently substitute for one another.

### Evidence is immutable; custody and interpretation are additive

Original uploads, hashes, capture metadata, provider artifacts, selected message revisions, notes,
submissions, decisions, and access history are append-only or superseded additively. Redaction
creates a presentation derivative; it does not destroy the protected original. Transformations and
exports identify inputs, tool/version, operator, time, and integrity digest.

### The accepted contract and effective policy are reproducible

Ordinary entitlement starts from the booking's accepted snapshots. Exception, protection, and
support policies are effective-dated and versioned. A decision stores the selected version and
canonical input digest. Reopening a case under new evidence or policy creates a superseding decision;
it does not recalculate the historical decision invisibly using today's rules.

### Temporary controls are bounded

Case-related payout holds, response windows, negotiation offers, evidence deadlines, provider
deadlines, temporary listing controls, and manual reviews have explicit scope, start, deadline,
expiry or next-review instant, owner, escalation, and fail-safe behavior. An open case by itself is
not permission to hold all host funds or inventory indefinitely.

### Safety triage is always available and has a deterministic floor

Safety intake, approved emergency guidance, and human escalation must work without AI, a claim
provider, or a complete cross-domain timeline. A model may raise urgency but may not lower a user-
declared safety concern below deterministic screening. Support never implies that the platform is an
emergency service unless the approved market role actually provides one.

### One intent produces at most one consequential effect

A canonical command key covers actor, case, action type, target, decision version, and client intent.
Retries replay the original result. Downstream domains receive stable instruction IDs. Database
uniqueness, optimistic versions, locks, approval hashes, and provider idempotency remain the final
defenses against duplicate credits, refunds, claim payments, holds, or submissions.

### Human authority is scoped and independently checked

Agent skill, market, queue, resource scope, maximum amount, remedy type, shift state, step-up
authentication, conflict rules, and approval tier are evaluated at execution time. Above-threshold,
self-related, exceptional, or high-impact actions require an independent approver bound to the exact
request digest. Break-glass actions expire and receive mandatory review.

### Closure never hides unresolved work

A case may close only when required participant communication, safety handoff, remedy decisions,
downstream executions, appeal window behavior, provider submissions, and exception ownership satisfy
the closure policy. A user-interface status cannot erase pending money, an open provider deadline,
or a legally retained evidence obligation.

### Corrections and appeals preserve history

An appeal or quality correction references the challenged decision and may affirm or supersede it.
It never edits original evidence, removes the decision trail, or silently reverses money. Capability
restoration, additional remedy, recovery, or reversal uses new authorized domain commands.

### Unknown external outcomes are first-class

A timeout while submitting provider evidence, issuing a reimbursement, or creating an insurance
claim does not mean failure. Persist intent before the network call, query by the stable provider
reference or idempotency key, reconcile, and escalate aged uncertainty. Never repeat a possibly
successful external action with a new identity merely to make the UI progress.

### Sensitive access is purpose-bound and auditable

Case assignment is not blanket permission to browse private messages, exact addresses, identity
documents, access secrets, payment data, safety reports, or medical/legal information. Fetch only
the approved evidence for a declared purpose; record field-level or artifact access where warranted;
re-evaluate access at read time; and revoke it when the purpose expires.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Support contact | One inbound or outbound interaction; it may create or join a case but is not the case itself |
| Case | Durable coordination aggregate for one problem scope, participants, timelines, decisions, and work |
| Incident | Operational or safety occurrence owned by stay operations; it may be linked to a support case |
| Complaint | A participant's assertion that service, conduct, content, or outcome was unacceptable |
| Dispute | A contested fact, obligation, charge, payout, policy application, or remedy |
| Provider dispute | A payment-provider retrieval request, inquiry, chargeback, or representment lifecycle owned operationally by payment |
| Chargeback | A rail/provider reversal process; it is not a normal marketplace refund or automatic proof of fraud |
| Damage claim | Structured request for compensation for alleged loss or property damage tied to a stay |
| Protection program | Platform contract or discretionary program with explicit eligibility and limits; it is not described as insurance unless legally structured as such |
| Insurance claim | Submission under a regulated insurance policy to an authorized provider or claims administrator |
| Claimant | Party requesting compensation or coverage |
| Respondent | Party whose conduct or responsibility is at issue; the term does not imply fault |
| Evidence item | Immutable source artifact or authoritative domain reference with provenance and custody |
| Assertion | Statement attributed to a person or system, not yet established as a finding |
| Finding | Versioned adjudicated conclusion supported by cited evidence and a declared proof standard |
| Decision | Authorized application of a policy/program to findings and immutable inputs |
| Remedy | Approved non-financial or financial action intended to resolve a case consequence |
| Remedy instruction | Immutable, idempotent command contract accepted by the domain that executes the remedy |
| Funder | Economic party or account bearing a remedy line: host, guest, platform, insurer, or partner |
| Recovery | Attempt to obtain value from a party/provider after the platform has paid or recognized a loss |
| Reserve | Explicit, capped finance policy allocation; not an informal support hold |
| Offer | Time-bounded proposal in a negotiation; it is not accepted until an authenticated acceptance commits |
| SLA clock | Versioned timer for acknowledgement, first response, next action, resolution, appeal, or provider deadline |
| Business calendar | Market/time-zone-aware working-hour and holiday definition used by applicable SLA rules |
| Queue | Work classification with eligibility, priority, ownership, capacity, and escalation policy |
| Assignment lease | Time-bounded right for one worker/agent to act on a work item; it is not case ownership truth |
| Reopen | Additive transition from a closed case after qualifying new evidence or failed remedy; not deletion of closure history |
| Appeal | Formal challenge to a consequential case decision under defined eligibility and deadline |
| Case currency | Presentation or decision currency for a monetary scope; source and settlement currencies remain separate facts |
| Event time | Instant at which an occurrence happened according to its source |
| Received time | Instant at which Room Booking observed it; never substituted silently for event time |
| Local stay date | Listing-local calendar date within the half-open stay range `[check_in, check_out)` |

Case IDs, claim IDs, decision IDs, evidence IDs, work-item IDs, remedy IDs, and instruction IDs are
opaque globally unique identifiers. Human-facing case references are separate non-secret lookup
codes. All deadlines are UTC instants with the governing IANA time zone, local display value,
business-calendar version, and daylight-saving resolution retained where relevant. Money never uses
floating point.

## End-to-end flow

```text
authenticated report / operational transfer / provider notice
        |
        v
intake + duplicate/link check + immediate safety screen
        |
        +--> urgent safety runbook and D13/D15 escalation
        |
        v
case created -> classified -> severity/SLA -> queue/owner
        |
        v
authorized timeline projection + evidence requests/custody
        |
        v
investigation -> findings -> applicable policy/program version
        |
        +--> party negotiation / provider deadline / specialist review
        |
        v
remedy proposal -> authority and ceiling -> maker-checker if required
        |
        v
immutable case decision + idempotent domain instructions
        |
        +--> booking/cancellation       changes contract/inventory
        +--> payment                    refund/provider dispute movement
        +--> ledger/payout              postings, holds, reserve, recovery
        +--> D15/listing/operations     protective or operational action
        +--> insurer/protection vendor  submission and recovery evidence
        |
        v
outcome reconciliation + participant communication
        |
        v
resolved -> appeal/quality window -> closed -> eligible reopen if new fact
```

Case creation, classification, evidence registration, decisions, and outbox facts use short local
transactions. A case saga coordinates neighboring commands and providers; it never holds a database
transaction open across a network call. Each target domain accepts or rejects its instruction under
its own lock, invariant, authority, and idempotency rules and returns a committed fact. Case closure
projects those outcomes but cannot manufacture them.

## Ownership and source-of-truth matrix

| Fact or action | Authoritative owner | D16 role |
| --- | --- | --- |
| User identity, authentication, account state | Identity | Verify actor and request scoped access/change through supported commands |
| Accepted stay contract and booking state | Booking | Link snapshot/version; request an allowed transition |
| Sellable nights and inventory claim | Inventory | Read authoritative result; never reserve/release directly |
| Cancellation/modification entitlement | Cancellation | Consume preview/decision; request governed ordinary or exception evaluation |
| Provider charge/refund/dispute observation | Payment | Link normalized evidence; authorize allowed dispute strategy or refund instruction |
| Economic ownership, host payable, reserve, recovery, payout | Ledger/finance | Supply explicit remedy/funder instruction; consume posted outcome |
| Original listing content and publication | Listing | Cite exact revision; request correction/restriction through owner |
| Original messages and attachments | Messaging | Select purpose-bound revisions as evidence; never copy all conversation by default |
| Access, readiness, check-in/out, incident | Stay operations | Link evidence; coordinate follow-up and remedy |
| Risk/moderation decision, restriction, fraud label | D15 trust and safety | Request review/control and consume authorized outcome; never override it |
| Original review, publication, and aggregate | [D14 Reviews](review-reputation-and-aspect-intelligence.md) | Link exact revision and request moderation/correction through D15/D14; never edit it |
| Case, work, negotiation, claim, finding, remedy decision, appeal | D16 | Create, version, authorize, explain, and audit |
| External insurance/protection claim observation | D16 provider gateway | Normalize evidence; policy/authorized adjuster remains coverage authority as applicable |
| Notification delivery | Notification orchestration | Request a committed communication intent and display delivery evidence |

## Case model and lifecycle

### Case scope and canonical identity

A case has one primary problem scope and may link related subcases. Recommended canonical scope is
`market + caseType + primaryBookingId + primaryIssueOccurrence`, with participant and source
references used for duplicate suggestions. Do not merge cases from fuzzy similarity automatically.
Merging can expose one party's private information to another or mix distinct legal deadlines.

Creation records source channel, reporter, represented party, booking/listing/payment scope, report
text, reported occurrence time, received time, locale, contact preference, immediate safety answers,
and client idempotency key. Anonymous or unauthenticated safety reporting may be supported by policy,
but ordinary account/monetary requests require authenticated ownership or verified delegation.

Duplicate handling has three outcomes:

- `SAME_CASE`: attach the new contact to the existing case with visibility checks;
- `RELATED_CASE`: retain separate cases and add a typed, access-controlled link;
- `DISTINCT`: create independently.

Only an authorized agent or deterministic exact-key rule can merge. Unmerge is an additive audited
correction that restores independent visibility; it never deletes prior access history.

### Case state machine

```text
NEW -> TRIAGED -> ASSIGNED -> INVESTIGATING -> DECISION_PENDING -> REMEDY_PENDING
 |        |          |              |                 |                 |
 |        +----------+--------------+-----------------+------------> ESCALATED
 |                                                                  |
 +-> DUPLICATE/LINKED                                         -> RESOLVED -> CLOSED
                                                                      |        |
                                                                      +-> REOPENED
                                                                      +-> APPEAL_PENDING

NEW/TRIAGED/ASSIGNED/INVESTIGATING may -> WITHDRAWN when policy permits.
CLOSED is historically terminal; reopening creates a new lifecycle episode and preserves closure.
```

State is not enough to describe all work. Separate dimensions track immediate safety, customer
waiting, agent waiting, third-party waiting, provider deadline, financial execution, appeal, and
legal hold. A case can be `INVESTIGATING` while a provider dispute is `ACTION_REQUIRED`, or
`REMEDY_PENDING` while a refund is `UNKNOWN`.

Every transition includes expected case version, actor, reason code, occurred/committed time,
related work or decision, participant-visible explanation template, SLA effect, and outbox event.
Allowed transitions are centrally versioned and enforced in application logic. Optimistic version
and unique transition identity prevent lost updates and duplicate effects.

### Closure and reopening

Closure policy evaluates the case type and required dimensions. At minimum:

- immediate safety escalation is complete or owned by a named external/internal route;
- required findings and decisions are recorded;
- accepted remedy instructions have succeeded, reached a disclosed pending external state, or have
  a named exception owner and next deadline;
- parties received the required decision and appeal information;
- payment/provider/protection deadlines are not silently abandoned;
- open child cases or legal holds are linked and do not require the parent to stay operationally
  open;
- quality-review sampling and retention classes are assigned.

Qualifying reopen triggers include new material evidence, remedy failure, provider reversal, missed
commitment, successful appeal, or a linked authoritative correction. Reopen uses a stable command
key and creates an episode with reason and linkage. A simple repeated contact after a final answer
may create a contact or appeal, not necessarily reopen the entire case.

## Classification, severity, queues, and SLA

### Classification dimensions

Do not encode one overloaded `case_type`. Use controlled dimensions:

- journey: pre-booking, checkout, pre-stay, check-in, in-stay, checkout, post-stay, payout;
- issue family: access, cleanliness, amenity, listing mismatch, host/guest conduct, cancellation,
  refund, payment, payout, damage, safety, review, account, compliance, or other approved family;
- request: information, operational help, contract change, refund/credit, compensation, claim,
  provider dispute, appeal, or complaint;
- impact: inconvenience, service loss, monetary exposure, property loss, displacement, injury/safety,
  account compromise, or regulatory complaint;
- responsibility status: unknown, contested, shared, established, or not applicable;
- sensitivity: ordinary, financial, identity, safety, health, legal, minor/vulnerable person, or
  restricted regulator/law-enforcement handling.

Classification is versioned. A later correction retains the prior value and routing consequences.
Free-text tags help search but never grant authority, select financial policy, or satisfy reporting.

### Severity floor

Severity represents current harm and urgency, not how angry a message sounds or how valuable the
customer is.

| Level | Meaning | Required initial behavior |
| --- | --- | --- |
| `S0_SAFETY_CRITICAL` | Immediate or potentially severe physical safety threat | Approved emergency guidance, dedicated human escalation, strict access, no automated downgrade |
| `S1_URGENT` | Stranded, no access, uninhabitable stay, active account/payout compromise, or expiring high-impact deadline | Page eligible owner and start rapid response/containment |
| `S2_HIGH` | Material stay or financial harm with a short remedy/provider window | Skilled queue, time-bounded next action, supervisor escalation |
| `S3_STANDARD` | Ordinary service, complaint, claim, payment, or payout issue | Normal business-calendar SLA |
| `S4_INFORMATIONAL` | Question or low-impact record with no active loss | Self-service or bounded standard queue |

Deterministic intake questions set a minimum. Severity can increase on new evidence and may decrease
only with an authorized reason after immediate danger is addressed. Customer tier, host revenue, or
estimated claim acceptance may influence service routing within a severity band but may never lower
safety, legal, accessibility, or provider-deadline priority.

### SLA clocks and deadlines

Store independent clocks for acknowledgement, first human response, next action, host/guest response,
evidence submission, decision, remedy execution, appeal, provider dispute, and insurance/protection
submission. Each clock records policy/version, start instant, due instant, pause intervals and reason,
business calendar/time zone, breached time, escalation route, and completion event.

Pauses are allowlisted—for example waiting for claimant evidence where policy permits—and are never
inferred from generic `PENDING`. Safety acknowledgement and statutory/provider deadlines generally
do not pause. Updating an SLA policy does not rewrite existing due times unless an approved migration
or explicit favorable correction does so additively.

### Queue and assignment behavior

Routing considers severity, case/claim type, market, language, time zone, deadline, monetary exposure,
sensitivity, required license/skill, participant conflict, agent authority, current workload, and
continuity. A deterministic rule establishes eligibility and hard priority. An optional model may
order equivalent eligible work but cannot hide S0/S1 work or breach a fixed deadline.

Work items use renewable assignment leases with fencing tokens. Expired leases return work to the
queue; they do not change case ownership or erase drafts. Transfers require reason, target acceptance
where appropriate, visibility recalculation, handoff summary, and SLA behavior. Queue capacity
controls trigger overflow/escalation rather than silently extending promises.

## Unified case timeline

The timeline is a read projection, not a new system of record. It contains normalized entries such
as contact received, message selected, incident updated, booking changed, payment observed, refund
submitted, journal posted, payout held, evidence added, finding made, offer accepted, decision issued,
provider deadline changed, appeal filed, and case closed.

Each entry retains:

- timeline entry ID and schema version;
- source domain, source object ID/version, source event ID, and immutable source link;
- event time and Room Booking received/committed time;
- actor/source classification and tenant/legal-entity/market scope;
- correlation and causation IDs;
- participant visibility and redaction policy;
- supersession/correction reference;
- projection watermark and freshness.

Timeline ordering uses event time for human context and received/sequence metadata to expose late
arrival. Equal timestamps use a deterministic tie-breaker but do not claim causal order. Missing
events appear as an explicit incomplete or stale projection. Agents can open the authoritative
source when authorized; a timeline summary never authorizes a remedy.

## Evidence, integrity, and custody

### Evidence intake

Evidence sources include participant uploads, exact message or review revisions, listing/booking
snapshots, access/readiness/incident observations, receipts and estimates, repair invoices, property
inspection records, payment/provider artifacts, call metadata, agent notes, and approved third-party
reports. Every item records source/uploader, represented party, capture and received times, original
name/type/size, cryptographic digest, storage object/version, malware status, transformations,
language, case/claim context, visibility, retention class, and legal-hold state.

Uploads use allowlisted media types, size/count limits, content sniffing, malware scanning,
decompression and archive limits, image metadata handling, safe filenames, isolated storage, and
short-lived authorized download URLs. Content is quarantined until required checks pass. A digest
proves platform possession of bytes, not that the depicted event is true.

### Provenance and evidence quality

Use a controlled provenance/quality ladder rather than one confidence number:

1. authoritative immutable Room Booking domain fact;
2. authenticated provider fact with verified origin;
3. contemporaneous participant artifact with capture metadata;
4. later participant assertion or uploaded artifact;
5. derived/transformed content with full lineage;
6. unverified external assertion.

Quality depends on the question. A payment record can prove movement but not property condition; a
photo can show visible damage but not responsibility or replacement cost. Findings cite exact
evidence fragments and record conflicts, gaps, and alternative explanations.

### Access, disclosure, redaction, and legal hold

Evidence has separate access scopes for submitting party, other party, assigned agent, safety,
claims adjuster, finance, risk, legal/privacy, external provider, and regulator/law enforcement where
lawful. Disclosure policy applies purpose, market, case stage, consent/legal basis, minimization, and
procedural-fairness requirements. Internal notes are never automatically disclosed as participant
messages.

Redactions create immutable derivatives linked to the original and include redaction reason,
operator/tool, coordinates or fields, review, and digest. Provider exports are allowlisted manifests,
not arbitrary case ZIP files. Legal hold suspends ordinary deletion only for scoped artifacts and
does not grant broader access.

### Evidence lifecycle

Suggested states are:

```text
REGISTERED -> QUARANTINED -> AVAILABLE -> INCLUDED_IN_REVIEW -> INCLUDED_IN_SUBMISSION
                       \-> REJECTED_UNSAFE
AVAILABLE -> REDACTED_DERIVATIVE_CREATED
AVAILABLE/REJECTED -> RETENTION_EXPIRED -> DELETION_PENDING -> DELETED_OR_CRYPTO_ERASED
```

Deletion preserves non-content tombstones and audit where legally permitted. A late appeal or
provider request may extend retention only through an authorized legal/policy action; the system
must not retain all private content forever because it could someday be useful.

## Investigation, findings, and decision policy

### Investigation plan

The case type selects a versioned investigation template containing required questions, mandatory
authoritative facts, permitted evidence categories, conflict checks, response windows, proof
standard, specialist/approval requirements, disclosure rules, and possible outcomes. Agents may add
steps but cannot mark required steps complete without the corresponding evidence or an explicit
unavailable reason.

Tasks separate collection from interpretation. Example tasks include verify booking eligibility,
request dated damage evidence, confirm check-in/access state, compare listing snapshot, validate
invoice, obtain respondent statement, check prior remedy ceiling, retrieve provider deadline, or
request a D15 safety/risk decision.

### Findings

A finding records subject, question, outcome (`SUPPORTED`, `NOT_SUPPORTED`, `INCONCLUSIVE`, or
`NOT_APPLICABLE`), proof standard, cited evidence IDs/fragments, contradictory evidence, confidence
where policy allows it, author/role, reviewed time, and finding-policy version. `INCONCLUSIVE` is not
coerced into claimant or respondent fault.

Some findings may be reusable across decisions, but only within their scope. A finding that damage
occurred does not prove the respondent caused it; a finding of responsibility does not prove the
claimed replacement amount; protection eligibility does not automatically create a guest debt.

### Decision precedence

Apply rules in this recommended order:

1. immediate safety, sanctions/legal, privacy, and court/regulator constraints;
2. accepted booking contract and ordinary cancellation/modification entitlement;
3. mandatory market consumer or accommodation obligations from approved legal policy;
4. applicable protection/insurance contract and exclusions;
5. host/guest marketplace terms and documented liability allocation;
6. approved service-recovery or goodwill policy;
7. authorized exceptional manual review.

The selected policy package records market, legal entity, case type, booking/occurrence time basis,
effective interval, terms/disclosure version, decision schema, rule set, remedy catalog, authority
matrix, and appeal behavior. Unknown applicability routes to review; agents do not choose the most
generous or restrictive version from memory.

### Decision record

Every consequential decision contains:

- case/claim and affected parties/resources;
- exact policy/program/terms and authority versions;
- canonical input and evidence-manifest digests;
- findings and unresolved uncertainty;
- accepted/rejected remedy lines and funding allocation;
- tax/document, booking, payment, ledger, payout, risk, and communication instructions;
- decision maker, approvals, conflict checks, timestamps, effective interval, and expiry;
- internal reason codes plus approved participant explanations;
- appeal eligibility, deadline, route, and disclosure constraints;
- superseded decision link where applicable.

Decisions are immutable. A correction, appeal, late provider result, or recovery creates a new
decision/effect linked to the original.

## Party communication and negotiation

### Structured negotiation

Negotiation supports a bounded claim amount and approved remedy types. An offer includes proposer,
recipient, exact lines/amount/currency, funding assumption, non-financial terms, evidence/policy
references, expiry instant, withdrawal rules, confidentiality/disclosure copy where valid, and
authenticated acceptance requirements.

```text
DRAFT -> SENT -> VIEWED -> ACCEPTED
             |       |\-> REJECTED
             |       +-> COUNTERED
             +---------> EXPIRED
SENT/VIEWED -> WITHDRAWN when policy permits
```

Acceptance is a signed-in explicit command against offer version and digest. Silence, message read,
agent note, or free-text agreement is not acceptance. Concurrent acceptance and withdrawal are
serialized; the first valid committed transition wins. Accepted terms still require authority,
funding, and downstream validation and must not promise provider movement as complete.

### Communication rules

Participant communication distinguishes acknowledgement, investigation update, evidence request,
provisional offer, final decision, remedy submitted, remedy completed, provider delay, and closure.
Templates are localized and versioned; case-specific facts are rendered from authorized sources.
Sensitive allegations, addresses, access secrets, internal detector detail, other-party personal
data, and raw provider artifacts are minimized.

Agents do not make undocumented promises in ordinary conversation. A structured action reference
opens the authoritative acceptance or evidence flow. Calls, where supported, record consent and
metadata according to market policy; transcripts are sensitive evidence, not general analytics text.

## Damage claims

### Eligibility and intake

A damage claim references an eligible accepted booking/stay revision, claimant and respondent,
incident/inspection if any, alleged occurrence interval, discovery time, submission time, itemized
loss, requested currency, evidence, prior deposits/remedies, and applicable claim/protection policy.
Eligibility validates participant authority, claim window, stay relationship, covered property or
loss type, duplicate/prior settlement, excluded conduct, jurisdiction/market, and any notice or
inspection requirements.

Late or incomplete claims are recorded and explained, not silently discarded. Policy may allow an
exception review. Safety, illegal conduct, or account compromise is separately escalated to D13/D15;
a damage claim outcome is not itself a fraud or safety label.

### Itemized loss and valuation

Each claimed item records category, description, ownership/relationship to the listing, age/condition
before stay, alleged damage/loss, requested remedy, original cost if known, repair estimate, replacement
evidence, salvage, depreciation rule, deductible/excess, currency, and supporting artifacts.

The valuation engine is deterministic and versioned. Depending on approved terms, a line may use
reasonable repair cost, like-kind replacement less depreciation, actual cash value, capped cleaning,
or another explicit method. It must prevent duplicate line recovery, unsupported tax/fee inclusion,
cross-currency arithmetic without an approved foreign-exchange decision, and total awards above
program/booking/item/category limits.

Estimates, invoices, and receipts prove different things. A submitted estimate is not proof of paid
cost. The decision records accepted amount, rejected amount, adjustment reason, and uncertainty per
line so support can explain the result.

### Responsibility and claim state

Responsibility evaluation separates occurrence, causation, pre-existing condition, ordinary wear,
host maintenance, guest/visitor action, third party, shared contribution, and insufficient evidence.
The standard of proof and permitted presumptions are legal/program decisions. Prior complaints or
risk signals may prioritize investigation but cannot substitute for evidence of this claim.

```text
DRAFT -> SUBMITTED -> ELIGIBILITY_REVIEW -> EVIDENCE_COLLECTION -> RESPONDENT_REVIEW
                         |                         |                    |
                         +-> INELIGIBLE            +--------------------+
                                                   v
                                             ADJUDICATION -> DECIDED
                                                               |
                                      +------------------------+--------------------+
                                      v                        v                    v
                               PAYMENT_PENDING          RECOVERY_PENDING       APPEALED
                                      |                        |
                                      +-----------> SETTLED <-+
DECIDED/SETTLED -> CLOSED; qualifying new evidence may create REOPENED episode.
```

Claim status does not overwrite case, payment, ledger, recovery, provider, or appeal status.

### Deposits, host holds, and recovery

If the product uses a damage deposit, payment owns authorization/capture/release movement and the
accepted contract defines permitted use. A deposit is not automatically host revenue, and a case
cannot capture it merely because a claim exists. The claim decision produces an exact instruction
within the authorized amount and timing.

Before a final claim decision, finance may accept a scoped payout hold or reserve instruction under
its policy. It identifies maximum amount, currency, affected booking allocations, expiry/review,
reason, host explanation, appeal, and release trigger. Claim closure or denial emits release/recovery
instructions; it never updates payable rows directly.

If the platform pays the claimant before recovering from the responsible party or insurer, ledger
records platform expense/receivable and the recovery waterfall explicitly. Future host offsets,
authorized debits, insurer recovery, and write-off follow finance and legal policy; support cannot
collect by manipulating unrelated payouts.

## Payment disputes and chargebacks

Payment ingests and normalizes provider retrieval requests, inquiries, chargebacks, amounts, reasons,
deadlines, required evidence, observations, and outcomes. D16 owns case strategy, evidence selection,
participant communication, and authorization to accept or represent the dispute. Finance owns
provisional debit, provider fee, reserve, recovery, loss, and reversal postings. D15 separately
decides whether evidence supports a fraud label or future protective control.

Recommended correlated provider state:

```text
INQUIRY_OR_RETRIEVAL -> ACTION_REQUIRED -> EVIDENCE_READY -> SUBMITTING
       |                                     |                 |
       +-> ACCEPTED                          +-> EXPIRED       +-> UNKNOWN
                                                               |
                                                               v
                                               EVIDENCE_SUBMITTED -> UNDER_REVIEW
                                                                         |
                                                       +-----------------+-------------+
                                                       v                 v             v
                                                      WON               LOST        WITHDRAWN
```

An evidence manifest is frozen before submission and contains only allowlisted, relevant artifacts.
The submission records provider account, native case/reference, exact deadline, request idempotency,
manifest digest, terms/attestation, submitting actor/approval, payload digest, response/observation,
and query/reconciliation plan. An LLM may draft a narrative from cited facts but cannot invent
delivery, identity, consent, presence, or policy claims.

Refund and dispute can race. The case displays both, payment enforces cumulative movement, and
reconciliation detects double-credit exposure. A chargeback never mutates the original successful
capture into failure, and accepting a provider dispute is not implemented as a normal refund.

## Protection and insurance integration

### Product boundary and truthful naming

The product must decide per market whether it offers a contractual host/guest protection program,
acts as a distributor/intermediary for insurance, or only links an independent policy. Marketing,
terms, eligibility, premium/fee treatment, claims authority, complaints, licensing, cancellation,
and data sharing must match that role. Until approved, use neutral internal terms and do not promise
"insurance" coverage.

The platform always owns its booking facts, case experience, consent capture, evidence minimization,
provider interface, and reconciliation. A carrier or authorized administrator may own coverage and
claim adjudication. Provider acceptance is evidence, not proof of payment; internal ledger facts are
posted only from verified outcomes and approved accounting rules.

### Program and coverage snapshot

At booking or qualifying event, retain the program/policy identifier and version, covered party,
insured/protected object, coverage territory and period, limit/currency, deductible/excess,
exclusions reference, premium/fee if any, disclosure/consent, provider/legal entity, and document
references. Do not apply today's coverage to a historical booking.

Coverage determination records `ELIGIBLE`, `INELIGIBLE`, `REFERRED`, or `UNKNOWN`, cited terms,
facts, exclusions, limits, decision authority, explanation, appeal/complaint route, and expiry. A
platform program may use an internal deterministic evaluator with human approval; regulated coverage
decisions must follow the approved provider/legal authority.

### Provider claim saga

```text
LOCAL_APPROVED -> SUBMISSION_QUEUED -> SUBMITTING -> SUBMITTED -> PROVIDER_REVIEW
                                      |                |              |
                                      +-> UNKNOWN      +-> INFO_REQUIRED
                                                                      |
                              +---------------------------------------+
                              v                    v                  v
                           APPROVED             PARTIAL             DENIED
                              |                    |                  |
                              +----------> PAYMENT_PENDING            +-> APPEAL_OR_COMPLAINT
                                                 |
                                              PAID -> RECONCILED
```

Persist the submission intent and stable provider key before the external call. Normalize provider
observations append-only, reject stale regression, query unknown outcomes, and reconcile claim
payment against bank/provider and ledger evidence. Provider requests for more information create
scoped evidence tasks and new submission versions; they do not mutate the original manifest.

### Build-versus-buy boundary

Provider-assisted capabilities may include policy issuance, regulated disclosures, adjuster network,
coverage adjudication, fraud tooling, payment, and statutory reporting. Room Booking must still own
provider selection/version, eligibility handoff, booking/case mapping, consent, minimized evidence,
deadline monitoring, idempotency, user experience, complaint escalation, accounting handoff,
reconciliation, audit, outage plan, portability, and exit/export.

## Remedy and financial decisioning

### Remedy catalog

Use a versioned allowlisted catalog rather than an arbitrary amount/action form. Candidate types:

- information, apology, contact or operational assistance;
- re-cleaning, repair, replacement amenity, access recovery, or host task;
- booking cancellation/modification or relocation request;
- refund of eligible source lines;
- host waiver or adjustment limited to host-attributable value;
- platform-funded travel credit, coupon, or goodwill payment;
- reimbursement against verified receipts within an approved category/cap;
- damage/protection claim payment;
- payment-dispute acceptance or representment;
- scoped payout hold/reserve/release or recovery request;
- listing correction, risk review, moderation, or safety escalation request.

Each catalog entry defines eligible case/actor/market, required findings/evidence, amount formula or
cap, permitted funders, compatible currencies, tax/document behavior, downstream owner/command,
agent authority, approval tier, user explanation, expiry, reversibility, reconciliation, and appeal.

### Entitlement and goodwill

Keep at least three decision bases distinct:

- `CONTRACTUAL`: amount due under the accepted booking/cancellation terms or mandatory legal policy;
- `PROTECTION_OR_CLAIM`: amount due under an applicable program/policy;
- `GOODWILL`: discretionary platform or host service recovery without rewriting contract liability.

Goodwill never changes historical price or tax facts. It posts to the approved platform/host expense
or credit accounts. A host may offer value only from amounts they can legally/economically control;
they cannot refund taxes, platform fees, insurer funds, or another host's balance.

### Calculation and cumulative ceilings

The decision service consumes source booking/quote lines, prior cancellation/refund/credit/claim
decisions, verified movement, ledger allocations, applicable policy/program limits, approval budgets,
and currency. For each source and remedy category enforce transactionally:

```text
remaining eligible remedy
  = approved maximum under policy/program
  - prior effective remedy decisions
  - active remedy reservations
  - non-reversed completed remedies
  + explicit superseding reversals where permitted
```

Platform goodwill also checks per-case, per-agent, per-team/day, per-user/period, campaign/program,
market/legal-entity, and global budget limits. Limits prevent abuse but must not silently reduce a
mandatory contractual entitlement. Currency conversion, where allowed, uses an approved, versioned
foreign-exchange decision and preserves source/target values and rounding.

### Remedy lifecycle

```text
DRAFT -> PROPOSED -> APPROVAL_PENDING -> APPROVED -> INSTRUCTION_PENDING
  |          |              |               |               |
  +-------> REJECTED <------+               +-> EXPIRED     +-> EXECUTING
                                                                  |
                                               +------------------+----------------+
                                               v                  v                v
                                            SUCCEEDED          PARTIAL           UNKNOWN
                                                                                  |
                                                                                  +-> RECONCILING
SUCCEEDED/PARTIAL may -> REVERSED_OR_RECOVERED only through a new authorized decision.
```

Approval binds the exact decision and evidence digests. Material changes invalidate approval. An
`APPROVED` refund means entitlement was authorized; only payment `SUCCEEDED` proves external refund
movement. A posted ledger event proves accounting effect, not bank receipt by the beneficiary.

### Instruction contract

Every downstream remedy instruction includes instruction ID/version, case/decision/remedy IDs,
target aggregate and expected version where required, beneficiary, funder allocations, source lines,
exact amount/currency, tax/document references, reason/policy/approval, deadline, idempotency key,
correlation/causation, and requested public explanation. The receiving domain reauthorizes scope and
validates its own ceilings; it may return `ACCEPTED`, `REJECTED`, `PENDING`, `UNKNOWN`, or a committed
result with its domain identity.

## Agent workspace, authority, and quality

### Workspace composition

The agent view shows case purpose, participants and representation, current severity/SLA, assigned
work, approved timeline projection, evidence tasks, exact authoritative links, policies, findings,
negotiation, proposed/approved remedies, downstream statuses, communications, appeal, and audit.
Sensitive panels load just in time after purpose and capability checks.

The workspace must show uncertainty and freshness. Cached balance, payment, booking, or restriction
data is labeled with watermark and cannot drive execution. Consequential actions always invoke an
authoritative preview followed by a confirmed command.

### Authority matrix

Authority grants are effective-dated and scoped by role, team, market, language, case type, severity,
sensitivity, remedy type, maximum amount/currency or converted reference, daily/aggregate exposure,
legal entity, work hours/on-call status, training/certification, and step-up requirements. Revocation
applies immediately to new commands even if the case remains assigned.

Examples of independent checks:

- the maker cannot approve their own above-threshold decision;
- an agent cannot act on their own, related, or previously hosted/booked account;
- payment-dispute submission requires the provider skill and deadline authority;
- regulated insurance adjudication requires the approved provider/adjuster role;
- S0 evidence and exact address require separate purpose-bound access;
- finance postings and payout release remain finance commands, never support permissions.

### Supervisor, break-glass, and manual exceptions

Supervisor approval records approver eligibility, exact request digest, policy tier, time, comments,
and expiry. Delegation cannot exceed the delegator's authority and is immutable/audited.

Break-glass is limited to defined urgent actions, requires step-up authentication and declared reason,
expires quickly, alerts security/operations, and triggers mandatory independent review. It cannot be
used to invent provider success, edit financial history, bypass sanctions/legal constraints, or
remove evidence.

Manual exceptions select an approved exception code and policy; free text is supplemental. If no
approved remedy exists, the agent escalates to a policy owner instead of improvising a database edit.

### Quality review

Sample decisions by random cohort and risk strata such as severity, amount, policy exception,
appeal, agent/team, market/language, protected accessibility context, reversal, repeat contact, and
customer harm. Quality reviewers assess evidence citation, classification, policy selection,
reasoning consistency, amount/funding accuracy, authorization, communication, timeliness, privacy,
and downstream completion.

Quality corrections create coaching, policy defect, tooling defect, re-review, or superseding
decision tasks. Do not optimize agents only for short handling time or closure rate; those incentives
can suppress investigation, transfers, reopenings, and justified remedies.

## Appeals and complaints

An appeal references one eligible decision, appellant/representation, grounds, new evidence, locale,
submission time, deadline policy, requested outcome, and prior appeal history. Intake acknowledges
receipt without promising reversal. The appeal reviewer must be independent where policy requires
and receives the original evidence/policy/decision plus permitted new facts.

```text
SUBMITTED -> ELIGIBILITY_REVIEW -> ASSIGNED -> REVIEWING -> DECIDED -> COMMUNICATED -> CLOSED
                 |                                        |
                 +-> INELIGIBLE                            +-> AFFIRMED
                                                          +-> MODIFIED
                                                          +-> REVERSED
                                                          +-> REMANDED
```

An appeal result is a superseding decision. Financial or capability effects require new idempotent
commands and reconciliation. Appeal success does not erase the original decision, and appeal failure
does not suppress a market-required external complaint route. The system records regulator,
ombudsman, carrier complaint, legal notice, or accessibility escalation separately with restricted
access and deadlines.

## Conceptual data model

### Current versus proposed records

No existing table is a D16 aggregate. The booking, payment, review, identity, and listing tables are
neighboring source records only. Proposed names are conceptual and may be refined during migration
design.

### Core case records

`support_cases`

- case ID/reference, primary booking/listing/participant scope, market/legal entity;
- source, type/classification versions, severity, sensitivity, status and lifecycle episode;
- reporter/represented party, owner team/agent, queue, priority, optimistic version;
- occurrence/received/opened/resolved/closed/reopened times;
- current SLA summary, latest decision, appeal/legal-hold flags, retention class;
- idempotency origin, created/updated times.

`case_participants` and `case_relationships`

- participant actor or external party, role, representation/consent basis, visibility scope, effective
  interval, contact preference;
- typed links between cases/incidents/claims/provider disputes without merging their authorities.

`case_classification_history` and `case_transitions`

- prior/new values, taxonomy/transition-policy version, actor/reason, evidence, effective and commit
  times, expected version, lifecycle episode, correlation.

`case_contacts`, `case_notes`, and `case_timeline_entries`

- channel/direction, author/source, exact content or authorized reference, visibility, template,
  locale, delivery/reference state, source event/version, event/received time, correction/supersession,
  projection watermark. Internal notes and participant communications are separate.

### Work and SLA records

`support_queues`, `routing_policy_versions`, and `agent_skill_grants`

- effective scope, eligibility, capacity, priority, overflow, required skill, authority version, and
  governance metadata.

`case_work_items` and `work_item_leases`

- task type, case/claim, required skill, priority, due time, state, owner, lease/fence, attempts,
  completion evidence, transfer/escalation.

`case_sla_clocks`

- clock type, policy/business-calendar version, start/due/complete/breach instants, pause intervals,
  escalation level, and current state. A uniqueness rule prevents two effective clocks of the same
  type/episode unless the policy explicitly supports parallel deadlines.

### Evidence records

`case_evidence_items`

- evidence ID, case/claim, source type/domain/object/version, submitter, provenance/quality class,
  capture/received times, storage object/version, content digest, type/size, scan state, sensitivity,
  visibility, retention, legal hold, and supersession.

`evidence_transformations`, `evidence_redactions`, `evidence_access_log`, and
`evidence_disclosure_manifests`

- immutable lineage from originals to derivatives/exports; purpose, actor/recipient, approved fields,
  decision/legal basis, access time, expiry, digest, and review.

`case_assertions` and `case_findings`

- exact attributed assertion; finding question/outcome, proof standard, cited fragments, conflicts,
  policy/version, author/reviewer, decision link, and supersession.

### Claim, negotiation, and provider records

`damage_claims` and `damage_claim_items`

- claimant/respondent, booking/stay, occurrence/discovery/submission, eligibility/program, status;
- item category, condition, requested/accepted amounts, currency, valuation method/version,
  depreciation/deductible/cap, findings, evidence, and prior-recovery linkage.

`case_offers` and `case_offer_lines`

- parties, version/digest, exact remedy terms, amounts/funders, sent/viewed/accepted/rejected/
  withdrawn/expired state, deadlines, authenticated acceptance, and supersession.

`protection_program_versions`, `coverage_snapshots`, `external_claims`,
`external_claim_submissions`, and `external_claim_observations`

- approved program/coverage terms, consent/disclosure, provider account/reference, deadlines, frozen
  manifest, stable provider key, payload/response digests, normalized append-only state, payment and
  reconciliation linkage.

Payment-owned provider dispute tables remain in payment. D16 stores a typed link, strategy decision,
evidence manifest, and authorized submission command rather than duplicating provider observations.

### Decision, remedy, authority, and audit records

`support_policy_versions`, `investigation_template_versions`, `remedy_catalog_versions`, and
`authority_policy_versions`

- immutable effective-dated configuration with market/legal entity, validation state, approver,
  activation/deactivation, disclosure/templates, test vectors, and digest.

`case_decisions` and `case_decision_findings`

- policy/input/evidence digests, result/reasons, decision basis, author/approver, authority snapshot,
  explanation, appeal behavior, and supersession.

`case_remedies`, `case_remedy_lines`, `remedy_reservations`, and `remedy_instructions`

- beneficiary/funder/source allocations, amount/currency, caps/prior consumption, tax/document
  references, lifecycle, approval hash, target domain, stable command identity, outcome, retry/
  reconciliation, reversal/recovery links.

`case_appeals`, `case_quality_reviews`, and `support_audit_events`

- grounds/evidence/deadlines/results, sampled criteria/quality findings/correction, and immutable
  privileged read/write/action history.

### Constraints and indexes

Required defenses include:

- unique intake idempotency per actor/channel/client command;
- unique effective case transition per case and command identity;
- unique downstream instruction identity and target-domain idempotency key;
- unique effective approval for an exact decision digest and approval role;
- no self-approval where an independent approver is required;
- positive minor-unit amounts and three-letter currency format;
- line sums equal remedy/claim decision totals per currency and funding allocation;
- cumulative source/remedy/program ceilings under transactional locks;
- offer acceptance uniqueness and version match;
- provider account plus external claim/submission reference uniqueness;
- evidence digest/storage identity and immutable source/version metadata;
- optimistic versions on mutable orchestration aggregates;
- indexes for queue/state/priority/due time, case/booking, participant/case, open SLA due time,
  claim/status/deadline, provider/reference, remedy/status, and evidence retention/legal hold.

Cross-row money balance, authority, and transition rules still require transactional service logic;
database constraints remain the final defense where representable. Append-only decision/evidence/
audit tables deny ordinary update/delete permissions.

### Migration, backfill, and deployment

1. Add case/taxonomy/SLA/audit/outbox records in a new forward migration; do not edit migrations
   `001`–`010`.
2. Add purpose-bound evidence metadata and storage integration before accepting participant files.
3. Add remedy policy/decision/instruction tables with nullable links to target-domain records; deploy
   read-only preview before execution.
4. Add claims, offers, provider submissions, appeals, and quality records incrementally.
5. Backfill only cases that can be proven from legacy support artifacts, with source/digest/import
   time and `LEGACY_IMPORTED`; do not infer findings, SLA compliance, or participant consent.
6. Dual-read existing external support tooling where applicable, compare counts/status/deadlines,
   and route one cohort to the new system.
7. Enable domain commands by remedy type behind separate kill switches and reconcile every effect.
8. Retire legacy mutation paths only after audit export, backfill, access review, replay, financial
   reconciliation, and rollback-window exit criteria pass.

Backfills are restartable, bounded, observable, and side-effect free. They never send messages,
submit claims, issue refunds, create holds, or post ledger entries.

## Service boundaries

Logical services may remain packages/modules in the existing Spring Boot deployment and PostgreSQL
database.

### `SupportIntakeService`

Authenticates or records approved anonymous intake, validates booking/participant scope, performs
the deterministic safety floor, deduplicates exact requests, and creates/links a case. It cannot
classify fraud or authorize money.

### `CaseLifecycleService`

Owns case state, versions, participants, classification history, relationships, closure/reopen, and
timeline source registration. It does not mutate neighboring aggregates.

### `CaseRoutingService` and `SlaService`

Evaluate effective routing/clock policies, create work, manage leases/fences, pause/resume allowed
clocks, and escalate breaches. They do not alter case findings or remedies.

### `CaseTimelineProjectionService`

Consumes committed domain events and builds a rebuildable, visibility-aware projection with
watermarks. It is never used as the execution authority.

### `EvidenceService`

Registers uploads/references, scans and quarantines files, verifies digests, controls purpose-bound
read/disclosure, retains transformation lineage, executes retention/legal-hold policy, and audits
access. It does not decide whether an allegation is true.

### `InvestigationService` and `FindingService`

Instantiate versioned investigation plans and create immutable evidence-cited findings under scoped
human authority. They do not calculate payment or ledger state.

### `DamageClaimService` and `ValuationService`

Own damage-claim eligibility, items, lifecycle, deterministic versioned valuation, response windows,
and claim decisions. Risk labels, external insurance authority, and money movement remain separate.

### `NegotiationService`

Creates versioned offers/counteroffers, validates participant visibility and explicit acceptance,
expires offers, and supplies accepted terms to decisioning. It cannot bypass remedy approval.

### `SupportPolicyService` and `RemedyDecisionService`

Select effective policy/catalog/authority, build canonical inputs, calculate exact remedy/funding
lines and ceilings, obtain approval, commit immutable decisions/reservations, and create downstream
instructions. They never call an external provider inside the transaction.

### `RemedyOrchestrationService`

Dispatches accepted instructions to booking, cancellation, payment, finance, payout, operations,
listing, notification, or D15; correlates committed outcomes; retries safe delivery; reconciles
unknown/partial states; and gates closure. It does not implement the target domain's invariants.

### `PaymentDisputeCaseService`

Coordinates response strategy and frozen evidence manifests for payment-owned provider disputes,
then calls `PaymentDisputeGateway`. It does not ingest raw webhooks or post chargeback loss.

### `ProtectionProgramService` and `ExternalClaimGateway`

Snapshot program eligibility/disclosures, coordinate authorized coverage decisions, submit stable
provider requests, normalize observations, query unknown outcomes, and reconcile claim payment.
Provider adapters remain isolated from core case state.

### `AppealService` and `CaseQualityService`

Own appeal eligibility/independent review/superseding outcomes and risk-based quality sampling,
respectively. They cannot rewrite the reviewed decision.

### Neighbor contracts

| Neighbor | Supplies to D16 | Consumes from D16 |
| --- | --- | --- |
| Identity/authorization | Actor, roles, session assurance, delegation, account status | Purpose-bound access audit and supported account-action requests |
| Booking/inventory | Contract snapshots, state, claim/revision outcomes | Authorized cancellation/modification/relocation commands |
| Cancellation/tax/quote | Ordinary entitlement preview/decision, line allocation, tax treatment | Exception/remedy request with policy and evidence |
| Payment | Movement/provider-dispute facts and execution result | Exact refund or dispute-strategy/evidence instruction |
| Ledger/payout | Balances, allocation, hold/recovery/payout results | Funding/posting/hold/reserve/release/recovery instruction |
| Messaging/operations | Original content revisions, delivery, access/readiness/incident facts | Communication intent, evidence selection, operational task/remedy request |
| D15 trust/safety | Decisions, restrictions, moderation, fraud labels | Risk/safety/moderation review request and adjudicated case outcome evidence |
| Listing/reviews | Exact content/review revisions and ownership | Correction/moderation request through owning boundaries |
| Notification | Delivery evidence | Versioned participant communication intent |
| Data/ML | Governed features/models/evaluation | Minimized case outcomes and mature labels under policy |

## API behavior

All endpoints below are illustrative target contracts and are not implemented routes.

### Guest and host operations

```text
POST /api/v1/bookings/{bookingId}/support-cases
GET  /api/v1/support-cases/{caseId}
GET  /api/v1/support-cases/{caseId}/timeline
POST /api/v1/support-cases/{caseId}/contacts
POST /api/v1/support-cases/{caseId}/evidence-upload-intents
POST /api/v1/support-cases/{caseId}/evidence
GET  /api/v1/support-cases/{caseId}/evidence/{evidenceId}
POST /api/v1/support-cases/{caseId}/offers/{offerId}/acceptance
POST /api/v1/support-cases/{caseId}/appeals

POST /api/v1/bookings/{bookingId}/damage-claims
GET  /api/v1/damage-claims/{claimId}
POST /api/v1/damage-claims/{claimId}/items
POST /api/v1/damage-claims/{claimId}/responses
POST /api/v1/damage-claims/{claimId}/submission
```

Clients submit problem category, occurrence time, description, requested help, structured safety
answers, locale, evidence intent, and idempotency key. The server derives participants, accepted
booking revision, listing/market/legal entity, policy candidates, amount ceilings, authority, and
visibility. Clients never supply trusted host ID, booking owner, fault, policy version, funder,
approved amount, case status, severity below the safety floor, or downstream state.

Case responses expose only participant-visible timeline entries, evidence, reason families,
deadlines, decisions, remedy progress, and appeal routes. They omit internal notes, detector logic,
other-party private data, privileged provider evidence, and unrelated linked cases. `version` or
ETag supports optimistic writes; a stale response includes the latest safe state.

### Agent and supervisor operations

```text
GET  /api/v1/support/work-items?queue=&state=&dueBefore=
POST /api/v1/support/work-items/{workItemId}/claim
POST /api/v1/support/work-items/{workItemId}/renew
POST /api/v1/support-cases/{caseId}/classification
POST /api/v1/support-cases/{caseId}/transitions
POST /api/v1/support-cases/{caseId}/evidence-requests
POST /api/v1/support-cases/{caseId}/findings
POST /api/v1/support-cases/{caseId}/offers
POST /api/v1/support-cases/{caseId}/remedy-previews
POST /api/v1/support-cases/{caseId}/remedy-decisions
POST /api/v1/support/remedy-decisions/{decisionId}/approvals
POST /api/v1/support/remedy-instructions/{instructionId}/dispatch
POST /api/v1/support-cases/{caseId}/closure
POST /api/v1/support-cases/{caseId}/reopen
POST /api/v1/support/appeals/{appealId}/decisions
POST /api/v1/support-cases/{caseId}/quality-reviews
```

Preview returns exact source lines, maximums, funding, policy/version, evidence requirements,
authority tier, downstream effects, warnings, expiry, and digest. Execution must reference the
preview/decision digest and fresh expected versions. The server re-evaluates actor authority, case
state, prior remedies, source ceilings, approval, and target-domain preconditions.

### Internal and provider operations

```text
POST /internal/v1/support-cases/from-incidents
POST /internal/v1/support-cases/{caseId}/domain-evidence-links
POST /internal/v1/support/remedy-instructions/{instructionId}/outcomes
POST /internal/v1/support/payment-disputes/{disputeId}/strategies
POST /internal/v1/support/payment-disputes/{disputeId}/evidence-manifests
POST /internal/v1/support/external-claims/{claimId}/submissions
POST /internal/v1/support/external-claims/{claimId}/queries
POST /webhooks/v1/claims/{providerAccountKey}
```

Internal calls use workload identity, allowlisted audience, resource scope, signed correlation, and
service idempotency. Provider webhooks verify the raw request before parsing, persist an inbox row
before processing, minimize retained payload, and return transport success independently from
business acceptance.

### Illustrative remedy request

```json
{
  "idempotencyKey": "client-case-7-remedy-2",
  "caseVersion": 12,
  "previewId": "b5a87074-5dc8-4d8d-a5d4-4f298fbc48b2",
  "previewDigest": "sha256:...",
  "requestedRemedies": [
    {
      "catalogCode": "REFUND_ELIGIBLE_ACCOMMODATION",
      "sourceLineIds": ["booking-line-3"]
    },
    {
      "catalogCode": "PLATFORM_GOODWILL_CREDIT",
      "requestedAmountMinor": 200000,
      "currency": "VND"
    }
  ],
  "reasonCode": "MATERIAL_LISTING_MISMATCH",
  "evidenceIds": ["f85d318c-a38e-4c80-91c4-578594c2972d"]
}
```

`requestedAmountMinor` is permitted only for catalog entries whose server policy accepts a bounded
request. The response contains the server-calculated eligible amount and funder; it never trusts the
client value as authority.

### Error semantics

| HTTP | Code | Meaning and retry behavior |
| --- | --- | --- |
| `400` | `CASE_INPUT_INVALID` | Shape or controlled value is invalid; correct before retrying |
| `401` | `AUTHENTICATION_REQUIRED` | Authenticate or complete required step-up |
| `403` | `CASE_ACCESS_DENIED` | Actor lacks participant/purpose/role access; reveal no case existence beyond safe policy |
| `403` | `AGENT_AUTHORITY_INSUFFICIENT` | Remedy/action exceeds current grant; escalate, do not retry unchanged |
| `404` | `CASE_NOT_FOUND` | No visible resource; response avoids cross-user disclosure |
| `409` | `CASE_VERSION_CONFLICT` | Reload safe current version and reconsider the command |
| `409` | `CASE_STATE_CONFLICT` | Requested transition is not allowed from effective state |
| `409` | `DUPLICATE_OR_RELATED_CASE` | Returns an authorized existing reference or safe next step |
| `409` | `OFFER_NO_LONGER_ACCEPTABLE` | Offer expired, changed, was withdrawn, or concurrently accepted |
| `409` | `REMEDY_CEILING_EXCEEDED` | Source/program/budget is already consumed or reserved |
| `409` | `APPROVAL_STALE` | Decision digest changed; obtain approval for the new exact request |
| `422` | `EVIDENCE_UNAVAILABLE_OR_UNSAFE` | Evidence is quarantined, expired, corrupt, or not authorized for purpose |
| `422` | `CLAIM_INELIGIBLE` | Stable safe reason and appeal/exception route where allowed |
| `422` | `POLICY_APPLICABILITY_UNKNOWN` | Human/policy-owner review required; do not guess |
| `423` | `CASE_ACTION_TEMPORARILY_RESTRICTED` | Active legal/safety/risk control blocks this action; disclose only approved reason |
| `429` | `CASE_INTAKE_RATE_LIMITED` | Retry after supplied interval; safety-reporting fallback remains available |
| `502` | `DEPENDENCY_REJECTED_INSTRUCTION` | Target domain rejected; inspect safe reason before a new decision |
| `503` | `CASE_PROVIDER_UNAVAILABLE` | Intent is retained; retry/query status with the same idempotency identity |
| `504` | `CASE_EXTERNAL_OUTCOME_UNKNOWN` | Do not create a new intent; poll/reconcile the existing instruction |

Errors include stable code, correlation ID, safe message, retryability, and structured data such as
latest version or retry time. They never expose internal notes, another participant's details,
detector features, credentials, or restricted evidence.

## Event contracts

Events are committed past-tense facts. Every event carries event ID, schema version, occurred and
committed times, aggregate ID/version, market/legal entity where needed, correlation and causation
IDs, actor class, data classification, and minimal identities. Producers publish through a
transactional outbox; consumers use an inbox or equivalent durable deduplication.

### D16-produced events

| Event | Meaning | Typical consumers |
| --- | --- | --- |
| `SupportCaseOpened` | Canonical case intake committed | Routing, notification, analytics |
| `SupportCaseClassified` | Versioned classification/severity changed | SLA, routing, safety, analytics |
| `SupportCaseAssigned` | Work ownership/lease committed | Agent workspace, SLA |
| `SupportCaseEscalated` | Approved escalation threshold/route activated | On-call, supervisor, safety |
| `CaseEvidenceRegistered` | Immutable evidence/reference metadata committed | Scan, investigation, retention |
| `CaseEvidenceAvailable` | Evidence passed required intake controls | Investigation, disclosure |
| `CaseFindingRecorded` | Evidence-cited finding committed | Decisioning, appeal, D15 label review |
| `CaseOfferSent` / `CaseOfferAccepted` | Exact negotiated proposal state committed | Decisioning, notification |
| `DamageClaimSubmitted` | Claim scope and item manifest frozen | Claims queue, finance hold evaluation |
| `DamageClaimDecided` | Authorized itemized decision committed | Remedy, finance, notification, D15 evidence |
| `CaseRemedyAuthorized` | Immutable remedy/funding decision committed | Orchestration, finance, audit |
| `RemedyInstructionIssued` | One idempotent target-domain command committed | Target domain consumer |
| `RemedyOutcomeObserved` | Correlated target/provider outcome recorded | Case lifecycle, reconciliation |
| `ExternalClaimSubmitted` | Provider acknowledged a specific submission | SLA, notification, reconciliation |
| `ExternalClaimOutcomeRecorded` | Normalized provider outcome committed | Remedy, finance, appeal |
| `CaseAppealSubmitted` / `CaseAppealDecided` | Appeal lifecycle fact committed | Queue, remedy, quality |
| `SupportCaseResolved` / `SupportCaseClosed` / `SupportCaseReopened` | Lifecycle fact committed | Notification, analytics, linked domains |

Events contain evidence/decision IDs, not private content or unrestricted download URLs. Consumers
fetch detail through authorized APIs. Corrections and supersessions emit new facts; they do not
reuse the old event identity.

### Consumed facts

D16 consumes booking and stay transitions, cancellation decisions, payment/refund/dispute
observations, ledger/hold/payout outcomes, messages/notification delivery, listing revisions,
incidents/access/readiness evidence, review revisions, and D15 decisions/restrictions. Each handler
records source event ID and source aggregate version, rejects exact duplicates, tolerates unrelated
out-of-order events, and rebuilds projections from the authoritative log or API.

An event cannot grant agent authority or directly trigger an unbounded monetary remedy. It may open
work or request a deterministic preview. Consequential execution requires an applicable policy and
command identity.

### Ordering, replay, and deletion

Partition/order only where aggregate causality requires it; never assume global event order. A lower
aggregate version cannot regress a projection. A late authoritative correction appends a corrected
timeline entry and may reopen a case. Replay suppresses notifications, provider calls, and remedy
commands unless replaying a durable uncompleted instruction under its original identity.

Privacy deletion propagates tombstones or minimized identity according to retention/legal-hold
policy. Analytics and model consumers must apply the same deletion and label-supersession facts.

## Concurrency and idempotency

### Canonical command identity

Idempotency scope includes authenticated actor or source, operation, case/claim/decision, target,
client/provider key, and policy epoch where material. Store request digest, initial response,
effective resource IDs, status, expiry/retention, and conflict behavior. Reusing a key with a
different digest returns conflict; a retry with the same digest returns the prior result.

### Lock order

Within the D16 database use a stable order:

```text
idempotency record
  -> support case
  -> claim or offer
  -> decision/remedy aggregate
  -> source ceiling and remedy reservation
  -> approval/work item/SLA rows as required
  -> outbox
```

Neighbor domains are never locked in the same transaction through a network call. D16 commits its
instruction, then the receiving domain locks its own aggregates. Cross-domain outcomes converge via
events/query recovery.

### Important races

| Race | Required winner/behavior |
| --- | --- |
| Two identical intake requests | Unique idempotency returns one case |
| Similar reports from two parties | Keep distinct/related unless authorized exact merge |
| Two agents claim one task | Lease compare-and-set/fencing grants one active worker |
| Agent transition versus new urgent evidence | Optimistic conflict; urgent evidence may escalate independently and must be re-read |
| Offer acceptance versus expiry/withdrawal | Serialize offer row; first valid committed transition wins |
| Two remedy decisions consume one source line | Lock source ceiling/reservations; cumulative database-backed guard rejects excess |
| Approval versus edited decision | Approval digest mismatch invalidates stale approval |
| Case closure versus pending instruction | Closure predicate rejects unless explicit exception owner/state satisfies policy |
| Claim decision versus new evidence | Commit one version; later evidence triggers review/supersession, never in-place change |
| Refund versus chargeback | Payment ceiling/reconciliation prevents silent double credit; case keeps both states |
| Payout batch versus case hold | Finance lock/order decides; late hold cannot rewrite submitted payout and may create recovery |
| Evidence retention deletion versus legal hold | Transactional effective hold wins if committed before deletion claim; ambiguous state escalates |
| Provider submission timeout versus retry | Same stable provider key; query before retry with no new identity |
| Appeal versus remedy execution | Appeal policy may issue explicit bounded hold; it never cancels an in-flight effect by status edit |

### Worker fencing and recovery

Queue, SLA, evidence scan, retention, instruction dispatch, provider query, reconciliation, and
notification workers claim bounded batches with lease owner, lease expiry, and fencing token. Each
side effect verifies the current fence and original idempotency identity. A crashed worker can be
reclaimed; a stale worker cannot finalize newer work.

Jobs use database time, capped exponential backoff with jitter, retry classification, dead-letter or
manual review for terminal malformed input, and age/materiality/severity escalation. Recovery never
creates a new remedy or provider submission merely because the original response was lost.

## Security, privacy, and access control

### Actor and resource authorization

Guests and hosts see only cases and evidence for bookings in which they are eligible participants or
verified representatives. Co-host/operator access is scoped to listing, booking, task, time interval,
and permission. Assignment alone does not grant an agent every sensitive field. Internal access
combines identity, active role, team, skill, market, case purpose, current assignment or approved
escalation, sensitivity, legal entity, and step-up state.

Existence-hiding responses protect unrelated cases. Participant blocking or account restriction does
not erase case access needed for safety, appeal, legal, or financial resolution; instead the system
selects safe mediated communication and stricter access.

### Sensitive data and storage

Classify allegations, exact addresses, identity and contact data, access/security details, health or
injury information, payment/provider artifacts, bank/payout context, private communications, minors/
vulnerable persons, legal correspondence, and investigation notes. Store content separately from
searchable metadata where useful; encrypt in transit and at rest; use managed keys and rotation;
isolate malware; prohibit secrets and raw payment credentials.

Search indexes, logs, traces, metrics, analytics, and model prompts contain opaque IDs and approved
categories rather than raw evidence. Debug access to protected content is exceptional and audited.
Provider credentials live in secret management and never in case records.

### Abuse and adversarial behavior

Controls cover case spam, harassment through support, fabricated/edited evidence, duplicate claims,
refund/credit farming, collusive damage claims, agent social engineering, deadline abuse, malicious
uploads, insider access, self-dealing, provider-webhook forgery, and enumeration. Rate limits and
restrictions must retain a safe path for urgent safety reports and legitimate appeals.

D15 receives minimized signals and adjudicated outcomes. A support denial, claimant loss, or agent
suspicion alone is not a fraud label. Conversely, an active D15 restriction does not permit support
to deny contractual rights without the applicable policy decision.

### Retention, legal hold, and privacy rights

Define retention by artifact, jurisdiction, contract/claim/financial purpose, appeal/provider window,
litigation/regulatory hold, and sensitivity. Apply data-subject access/correction/deletion through
reviewed workflows that preserve other-party rights, financial/legal records, and evidence integrity
where required. Derived features, search indexes, caches, and model datasets receive deletion or
restriction propagation.

Access and disclosure exports are minimized, authenticated, encrypted, expiring, and logged. Legal
or law-enforcement requests follow a separate verified intake and approval path, not ordinary agent
download capability.

## Observability and operations

### Business, fairness, and quality metrics

- contacts, unique cases, claims, duplicates, transfers, reopenings, and repeat contacts by safe
  case/market/channel cohorts;
- acknowledgement, first response, next action, decision, remedy, appeal, provider deadline, and
  closure time distributions—not only averages;
- SLA attainment/breach and aging by severity, queue, language, market, sensitivity, and operating
  hours;
- remedy requested/authorized/executed/failed/unknown amounts by type, beneficiary, funder, currency,
  policy, and reason;
- claim acceptance/partial/denial, item adjustments, protection/provider recovery, and appeal
  reversal rates with mature cohorts;
- agent decision consistency, evidence-citation completeness, approval overrides, quality scores,
  privacy incidents, and unauthorized-access attempts;
- customer effort, verified issue resolution, recurrence, repeat contact, and satisfaction measured
  without pressuring claimants or safety reporters;
- outcome and wait-time disparities across reviewed lawful slices, accessibility/language support,
  and false-denial/reversal indicators.

Do not celebrate low remedy cost, high denial, short handle time, or low reopen rate without quality,
harm, fairness, and downstream-completion context.

### Correctness and technical metrics

- duplicate command/effect prevented, idempotency conflict, optimistic conflict, lease expiry, and
  stale-worker rejection counts;
- case/timeline projection lag and source-version gaps;
- orphan remedy instructions, cumulative-ceiling violations prevented, stale approvals, line/funder
  reconciliation mismatch, and unresolved `UNKNOWN` outcomes;
- evidence scan latency/failure, quarantine depth, hash mismatch, expired link access, redaction and
  retention backlog;
- provider submission/query/webhook latency, signature failure, schema drift, deadline-at-risk, and
  reconciliation mismatch;
- outbox/inbox age, retry depth, dead-letter count, dependency latency/error, database contention,
  queue depth, and worker saturation.

### Initial SLO candidates

Exact targets are launch decisions, but define and measure:

- availability and latency of authenticated intake and the deterministic safety route;
- time to acknowledge and page S0/S1 cases;
- percentage of fixed provider/legal deadlines met;
- freshness of authoritative booking/payment/finance data displayed before execution;
- time for committed remedy instructions to reach an acknowledged target-domain state;
- maximum age of unknown monetary/provider outcomes by severity and materiality;
- durability/rebuild time for case decisions, timeline, evidence metadata, and audit.

Exclude planned maintenance only by written policy. Never hide breaches by changing severity,
closing/reopening, pausing without an allowlisted reason, or resetting a clock.

### Dashboards, alerts, and runbooks

Dashboards cover urgent intake, SLA/queue aging, staffing/capacity, sensitive access, claim deadlines,
remedy and funder reconciliation, payout holds, provider disputes, external claims, evidence pipeline,
appeals/quality, event lag, and dependency health.

Page on unowned S0/S1 cases, safety-route failure, imminent fixed deadlines, unauthorized/bulk
evidence access, systemic duplicate money risk, stuck high-value instructions, provider signature
failure spike, timeline/source divergence, and evidence-integrity failure. Ticket or queue slower
quality, aging, and reconciliation issues by materiality.

Runbooks include emergency/safety handoff, stranded guest and host unreachable, account/payout
compromise, provider dispute deadline, damage claim surge, refund/chargeback collision, payout hold
race, unknown claim payment, evidence malware or leak, queue overflow, notification outage, policy
rollback, AI kill switch, backup restore, and audited manual repair through supported commands.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Identity/session unavailable | Preserve approved safety intake fallback; do not grant ordinary case or money access |
| Booking/listing source unavailable | Accept minimal report if safe, mark context incomplete, defer consequential decision |
| Timeline projection stale | Display watermark/gap; fetch authority for execution; never infer absence |
| Routing service failure | Use deterministic fallback queue and severity floor; alert on unowned urgent work |
| Queue overload | Invoke overflow/on-call/capacity policy and honest revised expectations; do not hide work |
| Agent disconnect after command | Retry same idempotency key and return committed decision/effect |
| Concurrent case update | Reject stale version; reload and preserve draft separately |
| Evidence upload interrupted | Resume/replace upload intent safely; do not register incomplete bytes as available |
| Malware scanner unavailable | Quarantine files; continue text/safety path with reduced evidence |
| Object storage unavailable | Retain metadata/intake and retry; never claim artifact received without digest |
| Evidence hash mismatch | Quarantine, block use/export, alert integrity owner, retain audit |
| Policy/config unavailable | Use an approved cached immutable version only if applicability is proven; otherwise review |
| Cancellation/payment/ledger rejects remedy | Record rejection reason, release local reservation when safe, return to decision review |
| Target-domain response lost | Query by stable instruction ID; do not issue a new instruction |
| Refund succeeds after local timeout | Payment evidence reconciles original instruction; no duplicate refund |
| Chargeback arrives after refund | Keep both facts; payment/finance reconciliation opens double-credit exposure |
| Payout submitted before hold | Do not rewrite payout; create bounded recovery/next-payout action under finance policy |
| Provider dispute gateway down | Preserve manifest/deadline, retry/query, page before expiry; support manual approved export if designed |
| Claim provider timeout | Persist `UNKNOWN`, query same external key, reconcile late webhook |
| Provider sends duplicate/out-of-order event | Inbox deduplicates; reducer rejects stale regression and appends valid observation |
| Notification fails | Case state remains committed; retry/fallback and expose delivery status to agent |
| Appeal arrives during execution | Record appeal; apply only an explicit authorized hold/reversal, never status mutation |
| Worker crashes mid-batch | Lease/fence permits safe reclaim and exact replay |
| AI unavailable or unsafe | Deterministic routing/templates and human tools continue; no authority is lost |
| Regional outage/restore | Restore transactional data, replay outbox/inbox, rebuild timeline, reconcile instructions/providers before broad writes |

## Testing and verification

### Deterministic and state-machine tests

- Case, claim, offer, remedy, external-claim, appeal, work-item, SLA, and evidence transition tables,
  including every invalid/terminal/reopen path.
- Classification and severity floors for access failure, displacement, safety, payment, payout, damage,
  and ordinary questions.
- Business-calendar, UTC deadline, local display, daylight-saving overlap/gap, pause/resume, breach,
  escalation, and policy-version cases.
- Policy precedence and historical replay using booking occurrence, claim submission, and effective
  terms.
- Damage eligibility, item valuation, depreciation, deductible, category/total caps, prior settlement,
  line rounding, and multi-currency rejection/approved conversion.
- Remedy catalog eligibility, contractual/protection/goodwill separation, funder allocation, prior
  consumption, reservation, approval, tax/document instruction, and explanation.
- Closure predicates with pending safety, evidence, remedy, provider deadline, notification, appeal,
  child case, and exception owner.

### Real-database, concurrency, and property tests

- Duplicate intake and command keys create one logical result; changed request digest conflicts.
- Two agents cannot hold the same effective work lease; stale fencing cannot complete it.
- Concurrent offer acceptance/withdrawal/expiry yields one valid terminal result.
- Concurrent remedies cannot exceed source, policy, program, agent, or budget ceilings.
- Self-approval and insufficient authority fail at the final transactional boundary.
- Case close versus remedy creation, evidence legal hold versus deletion, claim decision versus new
  evidence, and appeal versus execution follow defined race outcomes.
- Remedy and funding lines sum exactly by currency; integer arithmetic and rounding conserve value.
- Append-only evidence, finding, decision, provider observation, transition, and audit protections
  reject unauthorized update/delete.

### Domain and provider contract tests

- Booking/cancellation/payment/ledger/payout/operations/D15 commands validate identity, version,
  amount, reason, and idempotency and return stable rejection/outcome semantics.
- Refund success, failure, unknown, late success, and chargeback collision reconcile without double
  credit or fabricated state.
- Payout hold-before/after-batch, release, expiry, recovery, and negative-host-balance paths preserve
  finance authority.
- Provider dispute and claim adapters cover signature verification, retries, duplicate/out-of-order
  webhooks, schema drift, deadline change, partial approval, information request, denial, payment, and
  unknown outcome.
- Frozen evidence manifests serialize deterministically and contain only allowlisted artifacts.
- Contract tests use provider sandboxes/mocks without logging credentials or real sensitive data.

### Security, privacy, and abuse tests

- Cross-user, cross-booking, cross-host, expired delegation, unassigned agent, wrong market/team,
  revoked skill, and purpose mismatch access are denied without existence leakage.
- Step-up, maker-checker, self/related-account conflict, break-glass expiry, bulk access, and authority
  amount boundaries.
- Upload spoofing, malware, archive bombs, oversized content, filename/path abuse, expired URL,
  unauthorized derivative/original access, and hash mismatch.
- Participant visibility, internal-note separation, exact-address/safety/health/payment redaction,
  legal hold, export, deletion, and model/analytics propagation.
- Case spam, duplicate claims, evidence tampering, collusion, credit farming, harassment through
  support, and forged provider webhook scenarios.

### Replay, recovery, and operational acceptance

- Outbox/inbox replay and projection rebuild are deterministic and side-effect suppressed.
- Worker crash before/after claim, local commit, provider call, and outcome persistence recovers with
  the same identities.
- Backup restore followed by event replay, evidence integrity sampling, instruction/provider
  reconciliation, SLA restoration, and financial control totals.
- Queue overflow, provider outage, storage/scanner outage, payment/ledger outage, notification outage,
  and AI kill-switch game days.
- Reference journeys are executable end to end: cannot access, listing mismatch/relocation, ordinary
  partial refund, damage claim, payment chargeback, payout issue, safety escalation, appeal reversal,
  and external-claim late payment.
- Finance and support resolve exceptions with product commands and audit, never direct SQL changes.

## Caching, performance, and scaling

Case commands, effective policy selection, authority, remedy ceilings, evidence metadata, and
transitions use transactional PostgreSQL authority. Cache only immutable policy/program versions,
taxonomies, templates, safe listing/booking snapshots, and authorization-independent reference data
with version keys and bounded TTL. Before execution fetch current identity/authority, case version,
source ceilings, booking/payment/ledger state, restrictions, and approval.

Critical query shapes are bounded:

- eligible queue work ordered by hard priority/due time with keyset pagination;
- one case summary plus paginated timeline and separately authorized evidence metadata;
- open SLA clocks by due time and escalation state;
- claims/provider submissions by state/deadline;
- remedy instructions by state/next attempt/materiality;
- retention items by expiry/legal-hold state;
- audit by actor/resource/time under restricted access.

Do not join every source domain table for each case page. Build a rebuildable timeline/read projection
and fetch authoritative detail on demand. Avoid raw evidence full-text indexing by default; if search
is justified, apply field allowlists, access filters, retention propagation, audit, and false-result
handling. Cursor pagination is stable under append-only timelines.

Batch workers use bounded `SKIP LOCKED`-style claims or optimistic leasing where appropriate, per-
provider/account rate limits, backpressure, and fair queue partitions. Large files move directly
between clients/providers and protected object storage using short-lived intents; application nodes
do not buffer them unnecessarily.

Scale from measured signals: queue depth, hot booking/case contention, timeline size, upload volume,
provider rate limits, read/write latency, event lag, and team/regulatory isolation. Possible later
extractions are media scanning, search projection, notification delivery, or provider adapters—not
the core case transaction by assumption. Partitioning, cold evidence tiers, or separate deployments
need an owner, SLO, replay plan, access boundary, and demonstrated benefit.

## Appropriate use of AI

Useful bounded components include:

- language/intent suggestion and translation for intake, while preserving original text;
- queue or skill recommendation within deterministic eligibility and severity floors;
- case and timeline summarization with sentence-level citations to authorized evidence;
- missing-evidence or inconsistent-timeline prompts;
- similar resolved-case retrieval from an approved, access-compatible corpus;
- damage-image or document classification, duplicate-image detection, and estimate-line extraction;
- policy retrieval and remedy recommendation constrained to effective approved catalogs;
- provider-dispute narrative drafts grounded in a frozen evidence manifest;
- quality-review sampling and possible privacy/promise/policy defects;
- workload and arrival forecasting for staffing, never individual adverse action.

Prerequisites include consent/legal basis, minimized allowlisted data, point-in-time training sets,
label maturity, case/market/language coverage, access-aware retrieval, prompt/model/version logging,
citation evaluation, calibration, adversarial and injection testing, privacy/security review,
human-feedback governance, latency/cost budgets, deterministic fallback, shadow/canary rollout, drift
monitoring, and kill switches.

Evaluate routing recall at severity/deadline floors, citation precision/coverage, unsupported-claim
rate, summary omission of material contrary evidence, recommendation acceptance and overturn rate,
privacy leakage, language/accessibility quality, outcome disparities, agent over-reliance, handling
time, and verified resolution. Faster text generation is not success if decisions become less
accurate or less contestable.

AI may never be authoritative for:

- emergency/safety severity downgrade or emergency instructions outside approved copy;
- identity, legal status, sanctions, fraud, discrimination, culpability, or factual findings;
- accepted contract, availability, booking state, price, tax, payment/provider result, ledger,
  payout, balance, refund ceiling, or financial ownership;
- damage causation, valuation award, liability, insurance coverage, claim acceptance/denial, legal
  rights, waiver, or complaint route;
- agent authority, evidence disclosure, retention/legal hold, high-impact restriction, final remedy,
  appeal result, case closure, or external submission;
- fabricated evidence, citations, promises, provider facts, or user statements.

Model output is advisory and visibly labeled with confidence/limitations. An authorized human verifies
citations and owns consequential decisions. Disabling every model must leave intake, safety routing,
policy lookup, case handling, remedies, providers, appeals, and reconciliation operable.

## Target-release dependencies and completion gates

All dependencies below form one complete case and remedy capability for the target release.

### Dependency 0 — Product, legal, finance, safety, and operational decisions

Approve launch market/hours/languages, case and severity taxonomies, emergency role, SLAs/business
calendars, policy precedence, remedy catalog/funding, agent authority/approval, evidence/retention,
appeal/complaint route, damage/protection scope, payment-dispute strategy, provider boundary, and
reference cases. Define SLOs, quality/fairness metrics, budgets, runbooks, and owners.

Exit: product, support, safety, risk, legal/privacy, finance/tax, payment, booking, and engineering
approve the authority map, reference outcomes, and prohibited actions; unresolved market obligations
are documented as launch blockers.

### Dependency 1 — Case, timeline, routing, and audit foundation

Add booking-centric case intake, participants, classification/severity, deterministic safety floor,
case transitions, SLA clocks, queues/work leases, internal notes, participant contacts, immutable
audit, outbox/inbox, and a rebuildable domain timeline. Start read-only against neighboring domains.

Exit: authenticated guest/host reference cases deduplicate correctly, urgent cases route under
dependency failure, access isolation holds, SLA/reopen behavior is testable, and restore/replay
rebuilds the timeline without side effects.

### Dependency 2 — Evidence and ordinary support flow

Add protected uploads/references, scan/quarantine, provenance/custody, access audit, redaction,
retention, investigation templates, findings, versioned communication, and structured evidence
requests. Support informational and operational remedies through D13/notification commands.

Exit: cannot-access, host-unreachable, cleanliness, and listing-mismatch cases preserve exact evidence,
meet routing/communication controls, and never expose or mutate unauthorized source data.

### Dependency 3 — Governed monetary remedies

Add policy/remedy catalogs, immutable preview/decision, funder allocation, authority and maker-checker,
transactional ceilings/reservations, exact refund/credit/host-adjustment instructions, payment/ledger/
payout outcomes, closure gates, reconciliation, and kill switches. Begin with low bounded cohorts.

Exit: full/partial refund and platform goodwill reference cases reconcile booking lines, movement,
ledger, host/platform/tax effects, and retries exactly; no agent can issue an arbitrary or duplicate
amount.

### Dependency 4 — Damage claim, negotiation, and appeal

Add claim eligibility/items, versioned valuation, responsibility findings, party response windows,
offers/counteroffers, payout-hold request, itemized decision, claim remedy/recovery, appeal, and
quality sampling. Keep adjudication human-led.

Exit: damage reference cases cover pre-existing/wear/repair/replacement/shared/inconclusive outcomes,
deadlines, caps, funding, payout races, appeal reversal, privacy, and additive D15 label feedback.

### Dependency 5 — Payment-provider disputes

Integrate payment-owned dispute notices, fixed deadlines, strategy decisions, frozen evidence
manifests, representment/acceptance commands, provider result, finance posting/recovery, refund race,
and daily reconciliation.

Exit: retrieval, won, lost, accepted, expired, duplicate, out-of-order, outage, late webhook, and
refund-plus-chargeback cases are reproducible and financially reconciled.

### Dependency 6 — Protection or insurance provider integration

After legal/product approval, snapshot coverage/disclosures, implement one provider adapter,
information requests, partial/denied/approved outcomes, claim payment/recovery, complaint handoff,
portability, outage/manual fallback, and reconciliation. Do not market unapproved insurance.

Exit: one end-to-end provider claim cohort meets consent, deadline, evidence, access, accounting,
complaint, recovery, and unknown-outcome controls with a tested provider exit plan.

### Dependency 7 — Bounded intelligence and measured-scale controls

Add citation-grounded summaries, routing suggestions, evidence extraction, policy retrieval,
provider narrative drafts, quality assistance, staffing forecasts, and only then measured deployment
or storage extraction. Roll out offline, shadow, reviewer-assist, and canary before bounded automation.

Exit: each model improves approved outcomes without safety-floor violations, unsupported claims,
privacy leakage, unfair disparity, missed deadlines, or loss of fallback; scale changes have measured
need, owner, SLO, replay, and rollback.

## Verification checklist

### Functional and authority correctness

- [ ] Case, incident, provider dispute, damage claim, D15 review, cancellation decision, and finance
  reconciliation remain distinct linked aggregates.
- [ ] Allegations, observations, findings, policy eligibility, remedy decisions, execution, and
  accounting are visibly separate.
- [ ] Every case transition, classification, SLA change, finding, decision, approval, and closure is
  versioned and attributable.
- [ ] Closure checks safety, communication, remedies, providers, appeals, and exception ownership;
  reopening preserves history.
- [ ] Damage decisions are itemized and distinguish occurrence, causation, responsibility, valuation,
  coverage, and recovery.
- [ ] Offers require explicit authenticated acceptance and cannot silently promise money movement.
- [ ] Support uses authoritative domain commands and has no generic booking/balance/provider edit.

### Monetary, concurrency, and recovery

- [ ] Every remedy line identifies beneficiary, funder, source, amount/currency, tax/document
  treatment, policy, approval, and downstream instruction.
- [ ] Contractual, protection/claim, and goodwill value remain distinct and exactly reconciled.
- [ ] Cumulative source/program/budget/refund ceilings survive concurrent commands and retries.
- [ ] Maker-checker approvals bind exact digests; self-approval and stale approval fail.
- [ ] Duplicate/late/out-of-order command, event, webhook, refund, chargeback, payout, and claim
  outcomes converge without duplicate effect.
- [ ] Unknown external outcomes retain one identity, query/reconcile, age, alert, and never fabricate
  success/failure.
- [ ] Outbox/inbox replay and backup restore rebuild state without repeating external side effects.

### Security, privacy, safety, and fairness

- [ ] Deterministic urgent safety intake/routing works when models and optional providers are down.
- [ ] Participant, agent, supervisor, provider, safety, finance, risk, and legal access is purpose-
  bound, least-privilege, re-evaluated, and audited.
- [ ] Evidence has digest, provenance, scan, custody, visibility, redaction lineage, retention, and
  legal-hold behavior.
- [ ] Upload, webhook, enumeration, social-engineering, insider, collusion, and claim-abuse controls
  are tested without blocking the safety/appeal route.
- [ ] User explanations avoid unproven culpability, protected detector detail, and other-party data.
- [ ] Quality/fairness review covers language, accessibility, market, wait, remedy, denial, and appeal
  outcomes with lawful reviewed slices.
- [ ] Privacy deletion/export propagates to projections, search, analytics, and model data subject to
  approved retention/legal holds.

### Operations, providers, and AI

- [ ] Every queue/clock has owner, business calendar, escalation, overflow, SLO, dashboard, alert,
  and runbook.
- [ ] Fixed payment/protection/legal deadlines cannot be paused or hidden by generic status changes.
- [ ] Payment-dispute and claim-provider submissions use frozen minimized manifests, stable keys,
  verified callbacks, query recovery, and reconciliation.
- [ ] Remedy/funder, provider, evidence, appeal, quality, access, and projection exception queues have
  accountable owners and aging metrics.
- [ ] Product tools resolve reference cases without direct database/provider edits.
- [ ] AI summaries/recommendations cite authorized evidence, expose uncertainty, pass injection/
  privacy/fairness evaluation, and have fallback plus kill switch.
- [ ] No model owns safety downgrade, fact, policy, liability, coverage, money, authority, disclosure,
  appeal, external submission, or closure.

### Delivery and compatibility

- [ ] New schema arrives only through reviewed forward Liquibase migrations and honest backfill
  provenance.
- [ ] Existing booking/payment/review/identity semantics remain valid while D16 is disabled.
- [ ] Feature flags isolate intake, each remedy type, damage claims, payment disputes, provider claims,
  and AI assistance.
- [ ] Empty-database and production-upgrade migrations, dual reads/writes, rollback windows, audit
  export, and provider exit are verified before legacy path retirement.
- [ ] Documentation, API schemas, policy test vectors, runbooks, training, templates, and ownership
  are approved before cohort expansion.

## Decisions required before implementation

Each consequential choice should become an Architecture Decision Record (ADR) or product/legal
decision record with owner, date, context, alternatives, decision, consequences, rollout, and revisit
trigger. Recommended defaults below are assumptions, not implemented policy.

1. Launch market, legal entity, languages, channels, support hours, 24/7 safety commitment, and
   externally advertised response times. Recommended: one market and honest channel/hour scope with
   always-available safety guidance/escalation.
2. Case taxonomy, primary-scope identity, exact duplicate/merge/link rules, human case reference,
   lifecycle transitions, terminal closure, and qualifying reopen triggers.
3. Severity questions/floors, who may downgrade, queue eligibility, overflow/on-call behavior, and
   customer tier use. Recommended: tier orders peers only and never changes safety/legal floors.
4. SLA clock types, business calendars/time zones, pause reasons, fixed provider/legal deadlines,
   breach communication, escalation, and reporting rules.
5. Guest, host, co-host, representative, anonymous safety reporter, support, safety, risk, finance,
   claims, legal, and provider participant/access model.
6. Contact recording/transcription, consent, translation, internal-note, participant-visible message,
   and official-decision communication policy.
7. Evidence types/limits, storage region, encryption/key boundary, malware vendor, metadata stripping,
   integrity digest, transformation/redaction review, disclosure, and download controls.
8. Retention schedule by contact/evidence/finding/claim/provider/financial/audit purpose, legal holds,
   privacy rights, deletion tombstones, and cross-system propagation.
9. Investigation templates, proof standards, required facts, `INCONCLUSIVE` treatment, procedural
   fairness, respondent response windows, and specialist review.
10. Policy precedence and effective-time basis for contract, mandatory law, protection, marketplace
    terms, goodwill, and manual exceptions.
11. Remedy catalog and which domain owns each execution: operational task, relocation, cancellation,
    refund, credit, reimbursement, host adjustment, payout hold, recovery, listing/risk action.
12. Launch funding model by line—host, platform, insurer, partner, guest—and tax, invoice/credit-note,
    promotion, foreign-exchange, and accounting treatment.
13. Source, case, program, agent/team/day, user/period, market/legal-entity, and aggregate remedy
    ceilings; reservation expiry; mandatory entitlement versus budget exhaustion behavior.
14. Agent roles, skill/certification, monetary/action limits, step-up, maker-checker thresholds,
    self/related-account conflicts, delegation, supervisor, break-glass, and audit review.
15. Which cancellation/support exceptions can override ordinary entitlement and who owns the
    executable exception policy. Recommended: explicit versioned exception decisions, never free-
    form agent waiver.
16. Travel credit/store value issuer, expiry, transferability, breakage, refundability, currency,
    consumer disclosure, ledger, tax, abuse, and insolvency treatment before launching credits.
17. Relocation policy: eligibility, guest consent, comparable-stay criteria, inventory/quote path,
    hotel/transport reimbursement, budget, funding, host recovery, and no-inventory fallback.
18. Damage-claim eligibility/window, covered parties/property/loss, ordinary wear, cleaning, excluded
    conduct, inspection/notice, respondent process, proof standard, item evidence, and appeal.
19. Damage valuation methods, repair versus replacement, age/depreciation, deductible/excess,
    category/booking/program caps, tax/shipping/labor, salvage, currency/FX, and duplicate recovery.
20. Deposit/preauthorization policy, custody/legal role, capture/release deadline, evidence, dispute,
    participant disclosure, and payment/ledger treatment if deposits are offered.
21. Pre-decision host payout hold/reserve basis, maximum/duration, disclosure, appeal, expiry/review,
    payout-race behavior, release, recovery waterfall, negative balance, collection, and write-off.
22. Protection product legal characterization, terms/disclosures, covered booking snapshot, fee or
    premium, limits/exclusions, decision authority, complaint route, funding/recovery, reserves, and
    use of the word insurance.
23. Build-versus-buy for claims administration/insurance, provider due diligence, data residency and
    sub-processors, API/webhook/file contract, idempotency, portability, outage/manual fallback,
    reconciliation, audit, and termination/export rights.
24. Payment retrieval/chargeback strategy by reason and value, automatic accept thresholds,
    evidence allowlist, representment authority, deadline buffer, customer communication, refund race,
    provisional accounting, D15 label maturation, and provider abstraction.
25. Negotiation scope, offer/counteroffer limits, expiry/withdrawal, acceptance authentication,
    confidentiality/release terms where lawful, authority recheck, and failed execution behavior.
26. Appeal eligibility, deadlines, independence, number of levels, new evidence, remedy hold,
    participant explanation, external complaint/ombudsman/carrier route, and reversal execution.
27. Quality sampling, scorecard, calibration, coaching, policy/tool defect routing, reviewer
    independence, agent incentives, and whether outcomes affect authority grants.
28. Case closure predicates and how pending bank/provider/insurance, child case, legal hold, appeal,
    or named exception ownership appears to participants and metrics.
29. Event schemas, outbox/inbox ownership, source projection retention, replay side-effect suppression,
    timeline rebuild, data deletion, and backup/restore reconciliation.
30. SLOs, materiality thresholds, dashboard/alert ownership, queue staffing, surge plans, on-call,
    incident severity, and supported repair commands.
31. AI data allowlist, model/provider residency, access-aware retrieval, prompt injection defense,
    citation and unsupported-claim thresholds, human verification, monitoring, rollout, kill switch,
    and prohibited authority.
32. Dependency order and completion gate. Required: case/timeline/routing/audit, protected evidence,
    governed remedies, damage/appeal, payment disputes, approved provider protection, and bounded AI.
