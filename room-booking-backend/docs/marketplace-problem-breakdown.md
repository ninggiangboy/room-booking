# Room Booking marketplace problem breakdown

## 1. Purpose

This document is the master problem map for the Room Booking product. It brings the platform's
major business, data, operational, financial, and machine-learning problems into one place so the
team can see the whole marketplace before implementing isolated features.

It answers five questions:

1. What problem domains must a serious accommodation marketplace solve?
2. Which system owns each decision and each piece of state?
3. Which invariants must remain true across domains?
4. What depends on what, and in what order must implementation proceed toward the single target
   release?
5. Which topics already have a detailed design and which still require one?

This is the normative decomposition and target-state map for the backend. It defines one complete
release rather than a sequence of progressively less-incomplete products. Dependency order controls
implementation and verification only; every capability marked **required** must pass its completion
gate before the release is considered complete. Individual feature documents remain the
authoritative detailed designs:

- [Global location and address search](features/location-search.md)
- [Personalized search and discovery](features/personalized-discovery.md)
- [Dynamic pricing, quotes, money allocation, and settlement](features/dynamic-pricing-and-settlement.md)
- [Availability, reservation, and booking lifecycle](features/availability-reservation-and-booking.md)
- [Payment orchestration](features/payment-orchestration.md)
- [Booking modification, cancellation, and refund policy](features/cancellation-modification-and-refund.md)
- [Ledger, reconciliation, and host payout](features/ledger-reconciliation-and-host-payout.md)
- [Messaging, notifications, and stay operations](features/messaging-notifications-and-stay-operations.md)
- [Trust, safety, fraud, and content moderation](features/trust-safety-fraud-and-moderation.md)
- [Disputes, damage claims, insurance, and customer support](features/disputes-damage-claims-and-support.md)
- [Reviews, aspect intelligence, and reputation](features/review-reputation-and-aspect-intelligence.md)
- [Data, experimentation, and machine-learning platform](features/data-experimentation-and-ml-platform.md)
- [Vietnam market readiness and internationalization](features/multi-market-compliance-and-localization.md)
- [Data-model history and target gaps](data-model/README.md)

When this document and a feature-specific document discuss the same subject, the feature-specific
document owns the detailed algorithm and contract. This document owns the platform-wide boundary,
dependency, priority, and integration view.

## 2. Status and current baseline

This is the target architecture and product definition as of 2026-09-07. It is not a description of a
fully implemented system.

The repository currently has database foundations for:

- users, authentication, roles, and host profile state;
- listing catalog, images, amenities, addresses, and publication state;
- one inventory unit per listing and one availability row per local stay date;
- booking and immutable nightly snapshots with overlap protection;
- payment attempts, refunds, and a webhook inbox;
- completed-stay reviews and favorites;
- PostGIS-backed geographic areas and listing coordinates.

The Java application currently exposes identity, authentication, and host-onboarding behavior. Most
listing, calendar, search, booking, payment, review, finance, and operations services described in
this document are not implemented yet. Existing migrations are foundations, not proof that their
full product workflows exist.

All future schema changes must use new forward-only Liquibase migrations. Never modify an applied
changeset merely to make it match this target design.

## 3. Product model and explicit assumptions

The target release is a two-sided marketplace launching in Vietnam:

- a **host** publishes and operates accommodation supply;
- a **guest** searches for and books a stay;
- the **platform** enables discovery, calculates a quote, coordinates payment, records obligations,
  supports the stay, and arranges settlement;
- external providers may process payments, verify identity, deliver messages, screen risk, calculate
  tax, or supply maps, but do not silently become the platform's source of truth.

Target technical assumptions are:

- a `property` is the physical and operational accommodation location;
- an `accommodation_type` is a sellable category at a property;
- a `physical_unit` is an optional specifically assigned room, apartment, or home;
- a `listing` is the public presentation of an accommodation type, not inventory authority;
- unique rentals use sellable quantity one while hotel room types use pooled per-date quantity;
- stay ranges are half-open: `[check_in, check_out)`;
- listing-local dates control nights and calendar rules;
- event times use UTC instants while listings retain an IANA timezone;
- money uses ISO 4217 currency and integer minor units;
- a missing availability row means not bookable;
- server-side rules, not clients or models, make authoritative eligibility and money decisions;
- the application is a modular monolith with durable domain boundaries, transactional outbox/inbox
  where cross-domain delivery matters, and extraction-ready contracts without requiring premature
  distributed deployment;
- PostgreSQL remains the transactional source of truth until measured scale requires otherwise.

The target supports unique rentals and hotel-style pooled room types from the same explicit
inventory abstraction. Split stays, packages, long-term leases, auctions, and alternative merchant
models remain excluded product categories, not unfinished versions of the target release. Adding
one requires an explicit product decision, contract review, and forward migration.

### 3.1 Target release definition

The backend release is complete only when all required domain capabilities work together for the
Vietnam market and both supported supply modes. The release includes:

- host/property onboarding, catalog, media, publication, rate plans, unique-unit and pooled inventory;
- location search, authoritative trip eligibility, quotes, holds, booking, payment, cancellation,
  modification, refund, ledger, payout, messaging, stay completion, reviews, disputes, and support
  domain commands;
- versioned Vietnam market configuration for locale, currency, time zone, legal/tax decisions,
  payment and payout providers, policies, disclosures, invoices, and retention;
- production ML for personalized ranking, review intelligence, bounded host pricing recommendations,
  and fraud/content moderation, plus support and messaging assistance where evidence permits;
- immutable history, idempotency, recovery, reconciliation, security, privacy, observability, and
  automated verification proportional to each domain's risk.

The release activates one market and may use one approved provider per external capability. All
contracts carry market, currency, locale, time-zone, policy, and provider provenance so a second
market can be added without changing historical meaning or breaking public contracts. Multi-region
deployment, simultaneous provider routing, and infrastructure introduced solely for unmeasured
scale are conditional capabilities, not release requirements.

This repository's target is the backend domain platform. Guest, host, finance, support, risk, and
admin user-interface design; staffing/training plans; and standalone operational runbook artifacts
are outside scope. The backend still defines authorized commands, evidence queries, audit records,
metrics, alerts, recovery semantics, and escalation inputs those consumers require. References to a
runbook in a feature design are external acceptance dependencies, not UI or runbook deliverables in
this repository.

#### Capability classification

Every detailed design must classify behavior using these terms:

- **Required target capability** — must be implemented and verified before the release is complete.
- **Designed extension boundary** — deliberately outside the supported product category, but the
  target contract must not prevent its later addition.
- **Measured-scale capability** — activated only after an explicit load, reliability, regulatory, or
  organizational threshold is met; absence does not make the initial release incomplete.

Labels such as reduced launch scope, basic version, or later delivery must not be used to defer
correctness, supported journeys, recovery, or a capability declared required by this document.

#### Domain completion matrix

| Domains | Target-release classification |
| --- | --- |
| D00–D05 | Required: platform, identity/host eligibility, property/catalog, location, both inventory modes, and channel boundaries |
| D06–D16 | Required: personalized discovery through booking/money/stay/review/trust/support as complete connected journeys |
| D17 | Designed extension: loyalty, referrals, and growth programs; any launch promotion still obeys D07/D10 accounting contracts |
| D18 | Required: host portfolio operations, performance evidence, and bounded pricing recommendations for supported supply |
| D19–D20 | Required: governed data/experimentation platform and the four production ML families named above |
| D21–D22 | Required: controlled policy commands and Vietnam market/privacy/security/localization readiness; user-interface design is excluded |
| D23 | Required for backend correctness and availability; multi-region, sharding, and service extraction are measured-scale capabilities |

The matrix is exhaustive for the target release. A feature document may narrow provider or method
breadth for Vietnam, but it may not downgrade a required domain invariant, recovery path, or supported
journey.

## 4. Platform-wide invariants

The following rules cut across every domain.

### 4.1 Availability is revalidated at commitment time

Search results and quotes are snapshots. Booking creation must lock or atomically claim the exact
inventory, recheck every night and restriction, and retain a database-level overlap constraint as
the final guard. A high ranking score or valid payment authorization cannot make unavailable
inventory bookable.

### 4.2 Price is authoritative only in a versioned quote

Search may display an estimate, but booking uses a server-created quote containing all nights,
fees, discounts, taxes, currency, rule versions, expiry, and allocation inputs. The booking stores
an immutable financial snapshot. Recalculating today's rules must never change historical totals.

### 4.3 Payment state is not booking state or accounting state

A provider charge can succeed while local processing is delayed. A booking can be held while no
money has moved. Captured cash is not automatically platform revenue. These states are related by
explicit workflows, idempotency keys, and reconciliation rather than shared status columns.

### 4.4 Every monetary amount has ownership and provenance

Each line identifies payer, beneficiary, funder, tax treatment, currency, source rule, and economic
classification. Guest total, host entitlement, host payout, platform revenue, tax payable, provider
fees, promotional expense, and cash balance are distinct values.

### 4.5 Explicit guest intent outranks inferred preference

Dates, occupancy, maximum total, accessibility requirements, room type, and selected amenities are
hard constraints. Personalization may order eligible options but must not override explicit input.

### 4.6 Models advise inside deterministic constraints

ML can predict demand, relevance, price elasticity, fraud, cancellation, or support risk. It cannot
invent availability, calculate tax, post ledger entries, waive policy, or execute a payout. Model
outputs are versioned inputs to a rule-constrained decision service with deterministic fallback.

### 4.7 Historical evidence is immutable; corrections are additive

Bookings, quotes, ledger entries, tax decisions, provider events, moderation decisions, and admin
actions must be reproducible later. Corrections use new versions, reversals, or adjustment records;
they do not rewrite material history.

### 4.8 Sensitive actions are least-privilege and auditable

Changing payout destinations, issuing large refunds, overriding tax, revealing exact addresses,
suspending accounts, and adjusting balances require scoped authorization and immutable audit data.
High-risk administrative actions should use maker-checker approval.

### 4.9 External calls are assumed to fail or repeat

Every payment, payout, message, identity check, calendar import, and webhook workflow needs stable
idempotency, retry classification, timeout handling, inbox/outbox persistence, and reconciliation.

### 4.10 User-facing claims must be explainable

Guests should understand why an amount is charged and why a result is recommended. Hosts should
understand why dates are unavailable, how prices were produced, and how payout was calculated.
Support needs the exact evidence and versions used at the time.

## 5. Actors and their goals

| Actor | Primary goals | Main platform risks |
| --- | --- | --- |
| Guest | Find a suitable, trustworthy stay at a clear total price and complete it safely | Misleading content, hidden fees, fraud, cancellation, inaccessible property, failed support |
| Host | Publish supply, control availability and price, protect property, receive predictable earnings | Double booking, fraud, damage, unfair ranking, payout delay, unclear fees or tax |
| Co-host/operator | Manage listings and operations under delegated authority | Excess permissions, unclear accountability, conflicting edits |
| Support agent | Resolve incidents quickly with complete context and safe tools | Unauthorized refunds, missing evidence, inconsistent policy |
| Risk/moderation analyst | Prevent harm while minimizing false positives | Biased models, delayed intervention, adversarial users |
| Finance operator | Reconcile cash, obligations, revenue, refunds, and payouts | Imbalanced ledger, provider drift, manual adjustments |
| Tax/compliance operator | Configure market-specific obligations and produce reports | Stale rules, wrong jurisdiction, missing seller identity |
| Marketplace operator | Balance demand, supply, conversion, quality, and long-term trust | Short-term optimization that damages one side of the market |
| External provider | Perform a bounded capability such as payment or verification | Outage, API drift, duplicated events, provider lock-in |

## 6. End-to-end lifecycle

The product is one connected lifecycle rather than a collection of independent screens:

```text
account and identity
        |
host onboarding, compliance, and payout readiness
        |
listing creation, verification, media, and publication
        |
calendar, restrictions, availability, and price inputs
        |
destination retrieval and eligible-candidate search
        |
listing/guest intelligence and personalized ranking
        |
authoritative quote and offer selection
        |
inventory hold and booking orchestration
        |
payment authorization/capture and confirmation
        |
pre-stay messaging, check-in, stay operations, and incidents
        |
completion, review, cancellation/refund, or dispute
        |
accounting allocation, tax, settlement, payout, and reconciliation
        |
analytics, experimentation, model learning, and marketplace feedback
```

Later steps feed earlier ones. Completed stays improve review intelligence and ranking. Search
demand informs host pricing. Cancellation and incident history informs risk and policy. Payout and
support quality influence host retention. These feedback loops need event attribution without
allowing analytics stores to become transactional authorities.

## 7. Capability and dependency map

| ID | Domain | Depends primarily on | Produces |
| --- | --- | --- | --- |
| D00 | Platform foundation | None | IDs, time/money conventions, migrations, security baseline |
| D01 | Identity and access | D00 | Authenticated actors, roles, sessions, delegated permissions |
| D02 | Host onboarding and compliance | D01 | Host eligibility, legal identity, payout readiness |
| D03 | Listing supply and content | D01, D02 | Publishable, searchable accommodation truth |
| D04 | Location and geographic catalog | D03 | Destination resolution and spatial candidate retrieval |
| D05 | Inventory, calendar, and channel sync | D03 | Authoritative sellable nights and stay restrictions |
| D06 | Search, discovery, and recommendation | D03–D05 | Eligible, ordered, explainable listing candidates |
| D07 | Pricing, offers, quote, and tax | D02, D03, D05 | Expiring payable quote and financial allocation inputs |
| D08 | Reservation and booking lifecycle | D01, D05, D07 | Held or confirmed stay contract and immutable snapshots |
| D09 | Payment orchestration | D08 | Authorized/captured/refunded external money movement |
| D10 | Ledger, settlement, and payout | D02, D07–D09 | Balanced obligations, host statements, payouts, reconciliation |
| D11 | Changes, cancellation, and refunds | D05, D07–D10 | Replacement contract, penalties, reversals, released inventory |
| D12 | Messaging and notifications | D01, D03, D08 | Auditable communication and delivery status |
| D13 | Check-in and stay operations | D03, D08, D12 | Access, operational tasks, incident evidence, stay completion |
| D14 | Reviews and reputation | D08, D13 | Verified feedback, aspect evidence, actor reputation |
| D15 | Trust, safety, fraud, and moderation | All transactional domains | Risk decisions, interventions, moderated content |
| D16 | Disputes, claims, and customer support | D08–D15 | Case decisions, remedies, financial adjustments |
| D17 | Growth, loyalty, and marketplace incentives | D06–D11 | Referrals, credits, campaigns, retention programs |
| D18 | Host performance and revenue tools | D03–D07, D10, D14 | Host insights, recommendations, operational controls |
| D19 | Data, analytics, and experimentation | Domain events | Metrics, attribution, experiments, decision evidence |
| D20 | ML and decisioning platform | D19 plus domain labels | Governed features, models, predictions, monitoring |
| D21 | Admin and policy governance | All domains | Versioned policy, controlled overrides, audit tools |
| D22 | Privacy, security, legal, and internationalization | All domains | Market and user protection constraints |
| D23 | Reliability, observability, and scale | All domains | Operable services, SLOs, recovery, capacity controls |

The table expresses logical dependencies, not a requirement to create 24 microservices. Start with
modules and transaction boundaries inside one application. Split deployment units only when team
ownership, scaling profile, reliability isolation, or regulatory boundary makes the cost worthwhile.

## 8. Detailed domain breakdown

### D00 — Platform foundation

#### Problem

Every later workflow needs consistent primitives. If identity, time, money, idempotency, error
contracts, migrations, audit, and events are improvised per feature, cross-domain correctness fails.

#### Subproblems

- UUID and human-readable reference generation.
- UTC event time versus listing-local calendar date.
- IANA timezone validation and daylight-saving behavior.
- ISO country, language, and currency validation.
- Integer minor-unit money and currency-specific scale.
- Request correlation, causation, and actor identity.
- API validation and stable machine-readable errors.
- Authentication, authorization, secrets, and encryption boundaries.
- Optimistic locking and explicit pessimistic locking where required.
- Transactional outbox and idempotent event consumers.
- Forward-only migration and backward-compatible deployment discipline.
- Structured logging, metrics, tracing, health, and audit conventions.

#### Hard rules

- Floating point is forbidden for money.
- A local stay date is never inferred from the API server's timezone.
- External identifiers are not internal domain identity.
- Retried commands must either return the original result or safely continue it.
- A request ID is diagnostic metadata, not an idempotency key.

#### Target-release foundation

Define shared types and conventions, database constraints, error envelopes, security baseline, audit
metadata, and the outbox pattern before implementing multi-step external workflows.

### D01 — Identity, accounts, and access

#### Problem

The platform must know which human or organization is acting, what they may do, and how to recover
access without enabling account takeover.

#### Subproblems

- Registration, email/phone verification, login, logout, and token rotation.
- Password reset, session inventory, device revocation, and suspicious-login alerts.
- Guest, host, co-host, support, finance, risk, and admin roles.
- Resource-level authorization: owning a listing is stronger than merely having `HOST` role.
- Organization accounts, team members, delegated permissions, and audit attribution.
- Account status, suspension, deletion request, anonymization, and legal retention.
- Contact-channel verification and notification preferences.
- Step-up authentication for payout, refund, and security changes.

#### Decisions

- Whether one person can maintain multiple host organizations.
- Whether social/enterprise login is supported.
- How co-host permissions are scoped per listing or portfolio.
- Which security changes require reauthentication or a cooling-off period.

#### Exit criteria

An action can be attributed to one actor and authorization context; sessions can be revoked; role
checks and resource ownership are enforced server-side; account recovery cannot bypass safeguards.

### D02 — Host onboarding, KYC/KYB, and market eligibility

#### Problem

A published host may receive money and provide regulated accommodation. The platform must establish
who the seller is, where they operate, whether they are eligible, and where settlement may go.

#### Subproblems

- Individual versus business host profile.
- Legal name, address, date of birth/incorporation, and beneficial owners.
- Identity-document and liveness verification through a bounded provider integration.
- Sanctions, PEP, watch-list, age, and market-specific screening.
- Tax identifiers, registration status, invoices, withholding, and seller reporting facts.
- Local rental permit, tourism registration, zoning, and annual-night limits.
- Payout account ownership and verification.
- Verification expiry, periodic refresh, material-change detection, and appeals.
- Restrictions such as `CAN_DRAFT`, `CAN_PUBLISH`, `CAN_ACCEPT_BOOKING`, and `CAN_RECEIVE_PAYOUT`.

#### Hard rules

- Verification provider output is evidence; platform policy decides capability eligibility.
- Failure to receive payout must not erase host entitlement.
- Sensitive identity documents use strict access, encryption, retention, and deletion policies.
- Market launch requires an approved legal/tax/payment configuration, not only a country dropdown.

#### Target-release requirement

Vietnam may use one approved verification and payout provider, but individual and organization
hosts, beneficial-owner evidence where applicable, periodic re-screening, manual review fallback,
and market-specific eligibility are required. Additional countries and simultaneous provider rails
reuse the same versioned contracts as designed extensions.

### D03 — Listing supply, catalog, media, and content quality

#### Problem

A host must describe one real, bookable space accurately enough for eligibility, search, pricing,
operations, safety, and the booking contract.

#### Subproblems

- Draft/save/publish/pause/archive lifecycle.
- Property type, room type, capacity, beds, bedrooms, bathrooms, and shared-space semantics.
- Address capture, geocoding, host pin confirmation, and public location obfuscation.
- Structured amenities with versioned vocabulary and translations.
- Accessibility claims with evidence and precise definitions.
- House rules, check-in windows, checkout, pets, smoking, parties, and age requirements.
- Safety equipment, licenses, emergency details, and regulatory disclosures.
- Image/video upload, scanning, transformations, ordering, cover selection, and rights.
- Description translation, prohibited-content moderation, and duplicate/fake listing detection.
- Publication completeness, quality score, review queues, and change history.
- Co-host ownership, portfolio organization, and bulk edits.

#### Hard rules

- Publication is an explicit server-side decision over required facts and host eligibility.
- Searchable structured facts are not derived only from free text.
- Exact location is shared only according to booking and privacy policy.
- Material facts used by a booking are snapshotted so later listing edits do not change the contract.
- AI may suggest or extract content but a host must confirm material factual claims.

#### Current and future boundary

The current schema supports the basic catalog, media, amenities, address, and state. Service APIs,
media processing, moderation, accessibility evidence, quality evaluation, and change approval remain
future work.

### D04 — Location, destination, and geographic intelligence

#### Problem

Guests think in cities, neighborhoods, landmarks, map areas, travel times, and colloquial names.
Addresses alone do not provide a global discovery experience.

#### Subproblems

- Provider-neutral country/administrative/locality/neighborhood/POI catalog.
- Localized, alternate, historical, misspelled, and transliterated names.
- Geocoding and reverse geocoding with provenance and confidence.
- Destination autocomplete and ambiguity resolution.
- Radius, polygon, map viewport, and dateline-aware retrieval.
- Listing-to-area assignment and hierarchy maintenance.
- Travel-time and distance-to-POI features.
- Approximate public map positions that protect exact addresses.
- Catalog imports, licensing, versioning, corrections, and operational backfills.
- Overlapping marketing regions that do not fit one administrative parent tree.

#### Hard rules

- Candidate retrieval uses the platform's persisted geographic data; public search does not call a
  third-party geocoder for every request.
- Latitude and longitude order and coordinate reference system are explicit.
- External feature IDs retain provenance but internal UUIDs remain stable identity.
- A catalog mismatch must not silently move a listing or block a valid host address.

Detailed design: [Global location and address search](features/location-search.md).

### D05 — Inventory, availability, restrictions, and channel sync

#### Problem

The platform must prove which nights can be sold and prevent concurrent or externally sourced
reservations from creating double booking.

#### Subproblems

- Rolling calendar materialization for each listing-local date.
- Manual block/unblock, maintenance, owner stay, and operational blackout reasons.
- Base and per-date minimum/maximum stay.
- Check-in/check-out weekday restrictions.
- Advance notice, booking window, preparation time, and same-day cutoff.
- Orphan-gap prevention and length-of-stay rules.
- Inventory hold with expiry during checkout.
- Confirmed booking occupancy and release on cancellation/expiry.
- Calendar edits concurrent with holds and bookings.
- iCal import/export, recurrence, UID handling, time normalization, and cancellation.
- Channel-manager/webhook integrations and conflict policy.
- Sync freshness, health, backfill, alerting, and manual conflict resolution.
- Pooled quantity inventory for room types and multiple interchangeable units.

#### Authoritative state

For unique rentals and pooled hotel room types, the platform calendar plus active claims and
per-date capacity are authoritative. Imported events are normalized into explicit blocks with
source and provenance. An external calendar's latest response is not queried inside a booking
transaction.

#### Hard rules

- Missing calendar rows are unavailable.
- Search availability is advisory; booking rechecks under lock.
- Holds and confirmed reservations block overlap according to explicit state and expiry semantics.
- An overdue active hold is transitioned and its claim released atomically by a worker or by
  on-path cleanup before another checkout claims the inventory; time passing alone does not mutate
  an active database constraint.
- Adjacent stays are permitted because ranges are half-open.
- Single-unit inventory uses non-overlapping range claims; pooled multi-unit inventory requires
  per-date quantity enforcement and cannot be implemented by weakening the single-unit constraint.

#### Target-release requirement

The release supports manual calendars, per-date pricing and restrictions, transactional holds,
overlap protection, iCalendar exchange, channel-manager boundaries, unique rentals, and pooled
room-type quantity. Provider integrations may be limited to the providers approved for Vietnam, but
their conflict, freshness, retry, and reconciliation behavior is not deferred.

Detailed design: [Availability, reservation, and booking lifecycle](features/availability-reservation-and-booking.md).

### D06 — Search, discovery, listing intelligence, and recommendation

#### Problem

For a destination with hundreds or thousands of listings, the platform must first retrieve every
eligible option and then rank the options most suitable for this guest and trip.

#### Subproblems

- Query understanding for destination, dates, party, filters, map state, and locale.
- Geographic retrieval and hard eligibility filtering.
- Complete-stay availability and authoritative total-price enrichment.
- Text, amenity, quality, location, popularity, freshness, and value features.
- Review-aspect extraction for cleanliness, noise, Wi-Fi, location, communication, accuracy, and
  other controlled dimensions.
- Listing intelligence with score, evidence count, recency, uncertainty, and trend per aspect.
- Guest preference profiles from searches, clicks, favorites, bookings, cancellations, and reviews.
- Separate long-term preference, current-session intent, and current-trip context.
- Price sensitivity based on relative total-trip value, not inferred income.
- Deterministic baseline scoring and later learning-to-rank.
- Cold start for new users, new listings, and new destinations.
- Diversity, deduplication, exploration, fairness, and sponsored-placement labeling.
- Stable pagination, impression logging, attribution, and ranking explanation.
- Abuse resistance against click farms, fake reviews, and popularity lock-in.

#### Ranking stages

```text
request validation
  -> destination/spatial retrieval
  -> publication, capacity, dates, restrictions, and explicit filters
  -> quote/total-price enrichment
  -> feature enrichment
  -> baseline or personalized relevance
  -> diversity, policy, fairness, and controlled exploration
  -> response projection and impression events
```

#### Hard rules

- Hard constraints precede ranking.
- Explicit intent beats inferred preference.
- A model score never makes an unavailable listing eligible.
- Missing review evidence is unknown, not automatically bad.
- Review attention and review sentiment are separate signals.
- Optimization targets completed, satisfactory stays and marketplace health, not clicks alone.
- Protected or unsupported sensitive characteristics are excluded.

Detailed design: [Personalized search and discovery](features/personalized-discovery.md).

### D07 — Pricing, promotions, quotes, and tax determination

#### Problem

For a specific listing, trip, market, guest context, and instant, the system must calculate what the
guest may pay, what the host may earn, what the platform may retain, and which tax obligations apply.

#### Subproblems

- Base, weekday/weekend, season, event, lead-time, occupancy, and length-of-stay rules.
- Host manual overrides, minimum/maximum bounds, desired net proceeds, and strategy controls.
- Mandatory and optional fees with payer, beneficiary, applicability, and taxable basis.
- Rate plans, cancellation flexibility, meals, deposits, and other offer conditions.
- Coupons, campaigns, loyalty credits, referrals, and funded discounts.
- Promotion eligibility, stacking, budget, reservation, redemption, and reversal.
- Guest-facing price display, inclusivity, currency conversion, and fee transparency.
- Tax jurisdiction, place/time of supply, guest/host/platform roles, exemptions, registration,
  collection, withholding, remittance, invoice, and seller reporting.
- Quote creation, versioning, expiry, refresh, comparison, and immutable line-item snapshots.
- Host payout preview, platform contribution, processor cost, and constraint evaluation.
- Demand forecasting, booking propensity, elasticity, causal uplift, and safe optimization.
- Personalized offer eligibility and ordering without hidden willingness-to-pay price discrimination.

#### Hard rules

- An LLM does not calculate prices, taxes, refunds, ledger entries, or payouts.
- The quote contains the exact rule and tax-content versions used.
- Discounts state their economic funder; fees state their beneficiary.
- Host minimum net, price floors/ceilings, law, tax, currency precision, and promotion budget are
  deterministic constraints.
- ML may propose candidates only inside those constraints.
- Captured money, host payout, platform revenue, and tax payable are not interchangeable.

Detailed design: [Dynamic pricing, quotes, money allocation, and settlement](features/dynamic-pricing-and-settlement.md).

### D08 — Reservation and booking lifecycle

#### Problem

The booking engine converts an expiring search/quote snapshot into a durable accommodation contract
without overselling inventory or losing state when payment and external calls are asynchronous.

#### Subproblems

- Instant-book versus request-to-book eligibility.
- Inventory preflight and atomic temporary hold.
- Hold TTL, extension policy, release, and stale-hold cleanup.
- Quote validation and refresh when price or eligibility changes.
- Guest count, child/infant/pet semantics, identity, age, and house-rule acceptance.
- Booking state machine and allowed transitions.
- Payment coordination without holding a database transaction over a network call.
- Confirmation codes, guest/host snapshots, policy acceptance, and contractual timestamps.
- Booking expiry, payment recovery, duplicate-submit handling, and idempotency.
- Host approval timeout for request-to-book.
- Multi-room, split-stay, group, and installment extensions.
- Booking history, timeline, actor attribution, and support visibility.

#### Recommended state model

```text
DRAFT
  -> HELD
      -> PAYMENT_PENDING
          -> CONFIRMED
              -> CHECKED_IN
                  -> COMPLETED

HELD/PAYMENT_PENDING -> EXPIRED
HELD/CONFIRMED       -> DECLINED or CANCELLED
CONFIRMED            -> MODIFICATION_PENDING
```

Actual names may differ, but transition ownership and side effects must be explicit. Do not compress
payment, inventory, stay, and refund facts into one ambiguous status.

#### Commit sequence

1. Validate the command and idempotency key.
2. Lock or atomically claim every requested night.
3. Validate listing state, restrictions, party, quote, and policy.
4. Create the hold/booking and immutable snapshots in one transaction.
5. Commit and emit an outbox event.
6. Initiate or continue payment outside the transaction.
7. Process verified provider results idempotently.
8. Confirm the booking and notify actors, or expire/release it safely.

#### Hard rules

- The database remains the final double-booking defense.
- A quote freezes proposed terms but does not reserve inventory; only an active hold/claim does.
- A booking references one accepted quote version.
- Client totals and client state transitions are never trusted.
- Booking, inventory, payment, refund, modification, and stay states remain distinct internally even
  when the product displays one derived journey status.
- Every transition is authorized, idempotent, auditable, and has documented side effects.
- Recovery workers can converge interrupted workflows to a valid state.

#### Target-release completion gate

Two concurrent guests cannot reserve the same night; repeated requests do not duplicate a booking
or charge; expired holds release inventory; confirmed history remains stable after listing changes;
support can explain the complete booking timeline.

Detailed design: [Availability, reservation, and booking lifecycle](features/availability-reservation-and-booking.md).

### D09 — Payment orchestration

#### Problem

The platform must coordinate unreliable, asynchronous payment providers while preventing duplicate
charges and keeping booking state recoverable.

#### Subproblems

- Payment intent/attempt creation with provider-independent states.
- Authorization, capture, sale, void, refund, and partial refund.
- 3-D Secure, SCA, redirects, asynchronous methods, and client confirmation.
- Pay-now, pay-later, deposits, installments, and balance collection.
- Saved payment-method tokens without storing raw credentials.
- Decline classification, guest retry, provider retry, and smart routing.
- Idempotent outbound requests and deduplicated inbound webhooks.
- Signature verification, event ordering, unknown events, and replay.
- Orphan provider transactions and scheduled reconciliation.
- Chargebacks, retrieval requests, evidence, fees, and representment.
- Provider abstraction, routing, outage mode, and migration.
- PCI scope, secrets, logging redaction, and restricted operational access.

#### Hard rules

- Do not keep database locks open while calling a payment provider.
- Provider references and idempotency keys are unique within their defined scope.
- A webhook is stored before processing and marked processed only after local commit.
- Provider success is verified server-to-server.
- Cumulative refund and capture rules are enforced transactionally.
- Raw card or bank credentials never enter ordinary application storage or logs.

#### Target-release completion gate

A booking can recover from client disconnect, repeated request, repeated webhook, delayed webhook,
decline, timeout, and process restart without duplicate financial effect.

Detailed design: [Payment orchestration](features/payment-orchestration.md).

### D10 — Accounting ledger, settlement, host payout, and reconciliation

#### Problem

Moving money is not enough. The platform must know who owns every amount, when it becomes payable,
what was actually moved externally, and how to prove balances later.

#### Subproblems

- Immutable double-entry journal and chart of accounts.
- Guest receivable/cash, host payable, platform revenue, tax payable, provider fee, promotion expense,
  reserves, disputes, and chargeback accounts.
- Booking financial allocation from quote line items.
- Recognition timing versus collection timing.
- Host entitlement, deductions, withholding, reserve, available balance, and payout.
- Payout schedule, threshold, bank/wallet rail, batching, retries, and returns.
- Payout-destination cooling-off and risk holds.
- Host statements, platform invoices, tax invoices, credit notes, and seller reporting.
- Provider balance, transaction, fee, refund, dispute, and payout reconciliation.
- Multi-currency subledgers, FX quote/rate provenance, rounding, and gain/loss.
- Negative balances, future offset, collections, and write-off policy.
- Manual journal adjustment with approval and evidence.
- Finance close, aging, exceptions, and audit export.

#### Hard rules

- Every journal entry balances debits and credits in one currency/subledger.
- Posted entries are reversed, not edited or deleted.
- Payment-provider state is evidence, not the ledger.
- A failed payout changes cash movement state, not the existence of host entitlement.
- Reconciliation differences are explicit exception records; they are not hidden by overwriting data.
- Tax content and economic allocations are effective-dated and versioned.

#### Target-release requirement

Deterministic allocation, a balanced ledger, host entitlement, automated payout through the approved
Vietnam rail, reserves, applicable withholding, reconciliation, statements, and close form one
completion boundary. Additional currencies and legal entities are designed extensions, but ownership,
currency, market, and journal semantics must already support them without reinterpretation.

Detailed designs:

- [Dynamic pricing, quotes, money allocation, and settlement](features/dynamic-pricing-and-settlement.md)
  defines the upstream commercial allocation and finance inputs.
- [Ledger, reconciliation, and host payout](features/ledger-reconciliation-and-host-payout.md)
  is the authoritative implementation-level design for D10.

### D11 — Booking modification, cancellation, and refund policy

#### Problem

A stay can change after confirmation. The platform must determine inventory, contractual, tax, and
financial consequences consistently for guest, host, and platform.

#### Subproblems

- Guest cancellation with effective time in the policy's timezone.
- Host cancellation, no-show, early departure, shortened or extended stay.
- Date, party, rate-plan, fee, and listing changes.
- Repricing only the delta versus creating a replacement booking.
- Policy version accepted at booking time.
- Grace periods, deadlines, per-night penalties, non-refundable components, and caps.
- Cleaning/service fee and tax treatment after cancellation.
- Guest refund entitlement, host cancellation earnings, platform fee retention, and promotion reversal.
- Force majeure and extenuating-circumstance overrides.
- Inventory release timing and race with replacement demand.
- Payment void/refund, ledger reversal, payout recovery, invoices, and notifications.
- Host-caused relocation, guest credit, and additional platform expense.
- Approval thresholds, appeal, and support evidence.

#### Hard rules

- Cancellation policy is a versioned executable rule and a human-readable disclosure.
- Preview uses the same calculation engine as execution.
- The execution result is immutable and references facts, policy version, actor, and override reason.
- Refund eligibility and actual provider refund movement are separate states.
- Modification is atomic from the guest's perspective: the original booking remains valid unless the
  replacement inventory and money transition succeeds.

#### Recommended implementation order

1. Full guest cancellation before check-in.
2. Expiry/payment-failure release.
3. Host cancellation and relocation workflow.
4. Partial refund and support adjustment.
5. Date extension/shortening.
6. General modification and replacement booking.
7. Force-majeure policy automation.

Detailed design: [Booking modification, cancellation, and refund policy](features/cancellation-modification-and-refund.md).

### D12 — Messaging and notifications

#### Problem

Guests and hosts need contextual communication, while the platform must prevent abuse, preserve
evidence, localize delivery, and avoid losing critical transactional messages.

#### Messaging subproblems

- Conversation creation tied to inquiry or booking.
- Participant authorization and exact-address disclosure timing.
- Text, attachment, image, template, and structured action messages.
- Anti-spam, scam, harassment, prohibited-content, and off-platform-payment detection.
- Contact-detail masking according to marketplace policy.
- Translation with the original content retained.
- Read state, delivery state, retention, export, deletion, and legal hold.
- Scheduled host messages and AI-assisted replies grounded in listing/booking facts.
- Emergency reporting and moderator access with explicit reason.

#### Notification subproblems

- Domain event to notification intent.
- Email, push, SMS, and market-specific messaging channels.
- Transactional versus marketing classification and consent.
- Locale, timezone, quiet hours, user preference, and mandatory notices.
- Template version, variables, preview, approval, and rollback.
- Deduplication, rate limit, retry, fallback, bounce, and delivery status.
- Reminder schedules for payment, check-in, review, cancellation deadlines, and payout.

#### Hard rules

- Notifications are projections of committed domain facts; they do not create booking/payment state.
- AI cannot promise a refund, policy exception, or unconfirmed amenity.
- Sensitive content is not placed in push/SMS previews unnecessarily.
- A message used in a dispute retains integrity and access history.

Detailed design: [Messaging, notifications, and stay operations](features/messaging-notifications-and-stay-operations.md).

### D13 — Check-in, stay, property operations, and incident response

#### Problem

A confirmed booking is not the outcome; the guest must gain safe access, the property must be ready,
and failures during the stay must be recoverable.

#### Subproblems

- Pre-arrival instructions and release conditions.
- Smart-lock/access-code generation, validity window, revocation, and audit.
- In-person check-in, identity confirmation, deposits, and registration forms.
- Early check-in, late checkout, baggage, and paid add-ons.
- Cleaning schedules, turnover time, staff assignment, and readiness confirmation.
- Maintenance tasks, unavailable equipment, and emergency block.
- Guest cannot enter, host unreachable, utility outage, safety incident, or property mismatch.
- In-stay issue reporting, severity, SLA, response, evidence, and resolution.
- Relocation inventory and cost authorization.
- Checkout confirmation, damage evidence, lost property, and stay completion.
- Integration with property-management and smart-device providers.

#### Hard rules

- Access secrets are time-limited, least-privilege, encrypted, and never broadly logged.
- Operational failure can create a case, remedy, or financial adjustment but must not bypass audit.
- Stay completion comes from explicit lifecycle rules, not simply a review submission.
- Safety-critical incidents have a separate escalation path from ordinary support.

Detailed design: [Messaging, notifications, and stay operations](features/messaging-notifications-and-stay-operations.md).

### D14 — Reviews, aspect intelligence, and reputation

#### Problem

Feedback must represent real stays, help future guests, improve matching, inform hosts, and resist
retaliation or manipulation.

#### Subproblems

- Guest-to-listing/host and host-to-guest review eligibility.
- Review window, double-blind publication, reminder, edit, and expiry.
- Overall and controlled category ratings.
- Text, language, translation, attachments, and structured private feedback.
- Moderation for abuse, personal information, extortion, irrelevance, and policy violations.
- Appeal, redaction, removal reason, and restored content.
- Helpful votes and report abuse.
- Bayesian/uncertainty-aware aggregates, trends, evidence count, and recency.
- Review-aspect extraction, sentiment, target, confidence, and human/model version.
- Guest aspect-attention and preference inference.
- Host and guest reputation with context, not one irreversible global score.
- Fake review, reciprocal manipulation, and retaliatory-pattern detection.

#### Hard rules

- Only verified eligible participants may review.
- Published aggregates derive from review truth and can be rebuilt.
- Removal never silently erases moderation evidence.
- Model-derived summaries link to supporting review evidence and disclose uncertainty.
- Mention frequency indicates attention, not necessarily positive or negative sentiment.

#### Target-release requirement

Verified directional reviews, double-blind publication, versioned moderation, rebuildable
aggregates, controlled aspect extraction, and evidence-qualified personalization signals are one
completion boundary. No model-derived review signal may serve before its evidence, privacy,
moderation, fallback, and rebuild contracts pass verification.

Detailed design:
[Reviews, aspect intelligence, and reputation](features/review-reputation-and-aspect-intelligence.md).

### D15 — Trust, safety, fraud, and content moderation

#### Problem

The marketplace joins strangers, property, identity, communication, and money. Abuse prevention
must span the full graph rather than live only at card checkout.

#### Threat families

- Account takeover, fake identity, duplicate accounts, and synthetic identity.
- Fake, copied, unavailable, or misrepresented listing.
- Stolen payment instrument, friendly fraud, and chargeback abuse.
- Host/guest collusion, money laundering, self-booking, and payout diversion.
- Coupon, referral, credit, refund, and cancellation abuse.
- Fake reviews, incentivized reviews, click manipulation, and competitor sabotage.
- Parties, unauthorized guests, property damage, theft, and neighborhood harm.
- Harassment, discrimination, trafficking, illegal goods, and physical safety threats.
- Phishing, off-platform payment, malicious links, and attachment malware.

#### System components

- Real-time risk signals and deterministic policy rules.
- Versioned risk features and supervised/anomaly/graph model scores.
- Decision service: allow, challenge, hold, manual review, limit, or deny.
- Step-up identity/payment verification.
- Velocity and relationship-graph analysis.
- Content moderation pipeline with model and human queues.
- Analyst case tooling, reason codes, evidence, and appeal.
- Post-event labels from chargeback, damage, cancellation, and confirmed abuse.
- False-positive, fairness, drift, and adversarial monitoring.

#### Hard rules

- Risk score is not itself a decision or an explanation.
- High-impact actions use policy, evidence, reason code, actor/model version, and appeal path.
- Sensitive/protected attributes and proxies require legal review and strict necessity.
- Manual review has SLA and safe fallback; it cannot hold guest money or host inventory indefinitely.
- Risk holds are represented explicitly in booking and payout eligibility.

Detailed design:
[Trust, safety, fraud, and content moderation](features/trust-safety-fraud-and-moderation.md).

### D16 — Disputes, damage claims, insurance, and customer support

#### Problem

When normal automation fails, the platform needs a consistent case system that joins evidence,
policy, communication, financial remedy, and human authority.

#### Subproblems

- Booking-centric support case and complete event timeline.
- Classification, severity, ownership, queue, SLA, escalation, and reopening.
- Guest complaint, host complaint, damage claim, chargeback, safety case, and payout issue.
- Evidence upload, integrity, access log, retention, and redaction.
- Negotiation between parties and deadline management.
- Policy lookup and version used at decision time.
- Refund, credit, host adjustment, platform expense, reserve, or claim payment.
- Insurance/protection-provider submission and recovery.
- Agent permission, monetary limit, maker-checker, and supervisor approval.
- Decision reason, communication, appeal, and quality review.
- AI case summarization and recommendation with citations to internal evidence.

#### Hard rules

- Support tools call the same authoritative money and booking commands as product flows.
- Agents cannot directly edit balances, booking history, or provider state.
- Every remedy identifies who funds it and produces ledger entries where applicable.
- AI output is advisory; an authorized human owns consequential decisions.
- Safety and emergency flows have dedicated runbooks and access controls.

Detailed design:
[Disputes, damage claims, insurance, and customer support](features/disputes-damage-claims-and-support.md).

### D17 — Growth, loyalty, referrals, and incentives

#### Problem

The platform needs acquisition and retention mechanisms without creating accounting ambiguity,
fraud, hidden price discrimination, or marketplace imbalance.

#### Subproblems

- Referral invitation, qualification, reward timing, caps, and anti-self-referral checks.
- Guest credits with currency, expiry, market, transferability, and refund behavior.
- Loyalty tiers, benefits, qualification window, downgrade, and partner status.
- Campaign audience, consent, frequency, attribution, and incremental uplift.
- First-booking, reactivation, destination, supply, and host-funded promotions.
- Gift cards and stored value, including regulatory and breakage concerns.
- Affiliate tracking, commission, fraud, cancellation reversal, and payout.
- Waitlists, wish lists, saved searches, and price/availability alerts.

#### Hard rules

- Incentives use the same quote, funder, ledger, and reversal architecture as other discounts.
- Eligibility is versioned and explainable.
- Models optimize incremental behavior, not redemption by users who would book anyway.
- Promotion experiments preserve price transparency and fairness guardrails.

### D18 — Host operations, performance, and revenue management

#### Problem

Supply quality depends on giving hosts understandable controls and feedback without making the
platform's recommendations opaque or coercive.

#### Subproblems

- Calendar and reservation dashboard.
- Bulk availability, restriction, price, and listing-content edits.
- Expected payout preview and statement explanation.
- Occupancy, ADR, RevPAR-like metrics, conversion, ranking visibility, and cancellation rate.
- Competitor/market aggregate insight with privacy thresholds.
- Demand forecast and recommended price range.
- Host floor, ceiling, desired net, aggressiveness, and automatic-pricing opt-in.
- Promotion suggestions with estimated incremental bookings and host cost.
- Listing-quality checklist, review-aspect trend, and operational action suggestions.
- Response rate/time, acceptance, cancellation, and policy compliance.
- Multi-listing portfolio, co-host assignments, and external PMS/channel manager.

#### Hard rules

- Recommendations state evidence, uncertainty, expected impact, and economic effect.
- Hosts can set bounds, opt out, override, and see the active source of each calendar value.
- The platform does not fabricate scarcity or guaranteed earnings.
- Aggregated market intelligence must not expose another host's private data.

### D19 — Data platform, analytics, metrics, and experimentation

#### Problem

Product and model decisions require trustworthy event history and causal measurement. Transactional
tables alone cannot answer every behavioral question, while analytics data cannot control bookings.

Detailed design: [Data, experimentation, and machine-learning platform](features/data-experimentation-and-ml-platform.md).

#### Subproblems

- Versioned event taxonomy, schema registry, ownership, and documentation.
- Correlation across session, search, impression, click, quote, booking, payment, stay, and review.
- Consent-aware client/server instrumentation and bot/internal-traffic filtering.
- Durable outbox, stream/batch ingestion, warehouse/lake, transformations, and lineage.
- Metric definitions for search success, conversion, completion, satisfaction, host earnings,
  cancellation, incident, refund, margin, and marketplace liquidity.
- Data quality tests, freshness, completeness, uniqueness, and reconciliation.
- A/B assignment, exposure logging, interaction between experiments, and sample-ratio checks.
- Guardrails, sequential testing policy, novelty effects, and long-term holdouts.
- Attribution windows for ranking, promotion, referral, and notification effects.
- Operational dashboards versus audited financial reports.
- Privacy deletion/retention propagation and controlled research datasets.

#### Hard rules

- An experiment assignment is stable for its declared unit and scope.
- Exposure is logged only when the treatment could affect the user.
- Metric SQL and semantic definitions are versioned.
- Analytics discrepancies never silently modify transactional truth.
- Model training data prevents future-information leakage.

### D20 — Machine-learning and decisioning platform

#### Problem

Ranking, review understanding, demand, price, fraud, support, and messaging models need shared
governance and reproducibility without forcing premature infrastructure complexity.

Detailed design: [Data, experimentation, and machine-learning platform](features/data-experimentation-and-ml-platform.md).

#### Model families

- Destination/query understanding and semantic retrieval.
- Review aspect, sentiment, summarization, and anomaly detection.
- Guest preference and session-intent representation.
- Booking propensity and learning-to-rank.
- Demand and occupancy forecasting.
- Price elasticity and constrained price optimization.
- Promotion uplift and contextual-bandit exploration.
- Cancellation, no-show, fraud, chargeback, and damage risk.
- Support routing, severity, summarization, and resolution recommendation.
- Content quality, duplicate listing, image, spam, and safety moderation.

#### Platform subproblems

- Label definitions and point-in-time-correct training datasets.
- Offline/online feature definitions, freshness, ownership, and validation.
- Model registry, artifact lineage, approval, and rollback.
- Batch and online inference with timeouts and deterministic fallback.
- Shadow, canary, A/B, champion/challenger, and kill switch.
- Accuracy, calibration, business KPI, latency, cost, drift, fairness, and abuse monitoring.
- Explanation reason codes and decision logs.
- Human feedback quality and avoidance of feedback loops.
- Privacy, deletion, consent, retention, and restricted features.

#### Implementation dependency chain

The following steps are implementation dependencies for the same target release, not separately
shippable product versions:

1. Instrument events and establish deterministic rules.
2. Build trustworthy aggregates and offline evaluation.
3. Add simple interpretable models for one bounded decision.
4. Shadow predictions without user impact.
5. Canary behind constraints and fallback.
6. Run controlled experiments with guardrails.
7. Add online features or bandits only when traffic and latency needs justify them.

#### Hard rules

- Each prediction records model version, feature version/time, output, and fallback status.
- LLM-generated text is grounded, moderated, and never authoritative for money or eligibility.
- A model is retired when labels, policy, market, or behavior make its assumptions invalid.
- No model launches without a rollback path and an accountable owner.

### D21 — Admin, policy, configuration, and governance

#### Problem

Operations need controlled ways to configure the marketplace and handle exceptions. Direct database
edits create invisible policy and destroy auditability.

#### Subproblems

- Role/permission administration and scoped operational access.
- Effective-dated country, tax, fee, cancellation, risk, and publication policies.
- Listing/user review and moderation queues.
- Booking, payment, refund, payout, and reconciliation inspection.
- Safe commands for pause, cancel, retry, refund, hold, release, and adjustment.
- Maker-checker for material financial, tax, identity, and safety decisions.
- Feature flag, experiment, model, template, and integration kill switches.
- Configuration validation, simulation, preview, approval, rollout, and rollback.
- Immutable admin audit with before/after references and reason.
- Break-glass access, expiry, alert, and post-use review.

#### Hard rules

- Admin UI does not bypass domain invariants.
- Configuration has schema, scope, priority, effective interval, owner, and version.
- Manual override is explicit data with expiry/review, not an unexplained final value.
- Production access and bulk exports are monitored and minimized.

### D22 — Privacy, application security, legal readiness, and internationalization

#### Problem

The platform handles identity, location, private communication, payment metadata, behavior, and
cross-border transactions. Product capability must remain within user consent, legal role, and
market readiness.

#### Privacy and security subproblems

- Data inventory, classification, purpose, lawful basis/consent, and ownership.
- Data minimization, retention, export, correction, deletion, and legal hold.
- Encryption in transit/at rest, key management, secret rotation, and field protection.
- Tenant/resource authorization, IDOR prevention, input validation, rate limits, and abuse controls.
- Secure media upload, malware scan, content-type validation, and signed delivery.
- Dependency, container, infrastructure, and supply-chain security.
- Incident detection, response, disclosure, forensics, and recovery.
- Vendor assessment, data-processing agreement, region, and subprocessors.
- Personalized-data controls and propagation of deletion to derived features/models.

#### Legal and internationalization subproblems

- Marketplace legal model and contracting entity per market.
- Consumer disclosures, terms acceptance, cancellation rights, and accessibility.
- Short-term rental regulation, licenses, registration, and night limits.
- Tax collection, invoice, withholding, and seller reporting.
- Payment licensing/marketplace provider constraints and stored-value rules.
- Language, locale, address, names, phone, number, date, timezone, and pluralization.
- Currency display, supported charge/settlement currency, FX, and rounding.
- Data residency and cross-border transfer requirements.

#### Hard rules

- A country is not launched until legal, tax, payment, support, language, safety, and payout readiness
  have explicit owners and approval.
- Privacy controls apply to logs, warehouse data, features, backups, and model artifacts—not only
  primary tables.
- Exact location and identity documents receive purpose-specific access.
- Policy text shown to a user is versioned and acceptance is provable.

### D23 — Reliability, observability, performance, and scale

#### Problem

The product must remain correct during concurrency, partial failure, traffic spikes, provider
outages, deployment, and recovery. Availability without financial/inventory correctness is failure.

#### Subproblems

- SLOs and error budgets by journey, not only by endpoint.
- Structured logs, traces, metrics, correlation, and sensitive-data redaction.
- Queue/worker lag, retry, poison message, dead-letter, and replay tooling.
- Database connection, slow-query, lock, bloat, backup, restore, and failover operations.
- Cache ownership, TTL, invalidation, stampede protection, and stale-data policy.
- Search latency, pagination stability, spatial indexes, and bounded feature enrichment.
- Payment/provider outage mode and reconciliation backlog.
- Capacity planning for seasonal demand and high-contention listings.
- Graceful degradation for recommendations, maps, translation, notifications, and providers.
- Zero/low-downtime schema and application rollout.
- Disaster recovery objectives and tested restore procedures.
- Regional architecture, data residency, and consistency if expansion requires them.

#### Hard rules

- Cache is never the final authority for inventory, quote acceptance, ledger balance, or permission.
- Retry is bounded and safe only with idempotency.
- Search can degrade to a baseline rank; booking correctness cannot degrade.
- Backup existence is insufficient; restoration and reconciliation are tested.
- Scale problems are measured before introducing sharding, CQRS, or microservices.

## 9. Cross-domain workflows and ownership

### 9.1 Publish a listing

```text
host command
  -> identity/resource authorization
  -> host market and compliance eligibility
  -> listing completeness and content/safety checks
  -> address/location confidence
  -> calendar horizon and price availability
  -> publication decision and version
  -> search-index/update event
```

The listing domain owns publication. Search consumes publication state; it does not decide it.
Compliance and moderation provide required decisions, and listing publication records the versions
used.

### 9.2 Search and personalized ranking

```text
guest request
  -> destination resolution
  -> listing/spatial retrieval
  -> availability and explicit-filter eligibility
  -> price/quote estimate enrichment
  -> listing intelligence and guest/trip features
  -> rank, diversify, explain
  -> impression attribution
```

Search owns ordering and response projection. Calendar owns availability. Pricing owns total-trip
calculation. Listing owns content. Review intelligence supplies evidence. None of those consumers
duplicates the source domain's rules.

### 9.3 Quote, hold, pay, and confirm

```text
quote request
  -> deterministic pricing/tax/promotion decision
  -> quote snapshot and expiry
  -> booking command with idempotency key
  -> inventory claim under lock
  -> booking/financial snapshot commit
  -> payment attempt
  -> verified provider outcome
  -> booking confirmation or expiry
  -> ledger allocation and notifications
```

This workflow is a saga across local transactions and provider calls. The booking module coordinates
the lifecycle; the calendar enforces inventory, payment owns provider interaction, and finance owns
economic postings.

### 9.4 Cancel and refund

```text
cancellation preview
  -> accepted policy and current facts
  -> penalty/refund/allocation calculation
  -> authorized cancellation command
  -> booking transition and inventory release
  -> refund/void request
  -> ledger reversal or adjustment
  -> payout eligibility update
  -> notifications and reconciliation
```

The cancellation decision and provider money movement are separate but correlated. A provider
delay must not cause policy recalculation with new rules.

### 9.5 Complete stay and pay host

```text
stay completion eligibility
  -> incident/dispute/risk hold check
  -> host entitlement becomes available
  -> payout batch instruction
  -> provider outcome
  -> ledger cash movement
  -> reconciliation
  -> host statement and notification
```

Payout failure leaves an amount payable to the host. It does not turn that amount into platform
revenue.

### 9.6 Incident and support remedy

```text
report
  -> severity and safety triage
  -> booking/listing/message/payment evidence timeline
  -> response and temporary controls
  -> policy/manual decision
  -> domain command for remedy
  -> money adjustment if needed
  -> communication, appeal, and closure
```

Support owns case coordination, not direct mutation of another domain's records.

## 10. Systems of record

| Fact or decision | Authoritative owner | Derived consumers |
| --- | --- | --- |
| Actor identity and session | Identity | Every protected domain |
| Host capability eligibility | Host compliance policy | Listing, booking, payout |
| Current listing content/state | Listing catalog | Search, quote, booking, operations |
| Destination hierarchy and coordinate | Geographic catalog/listing address | Search, tax, display |
| Sellable local date and restriction | Calendar/inventory | Search, quote, booking |
| Result order and explanation | Discovery decision | Guest UI, analytics |
| Listing quality/aspect aggregate | Review intelligence | Search, host insights, risk |
| Guest preference profile | Personalization | Search and offer ordering |
| Payable amount before expiry | Quote | Booking and guest UI |
| Accepted stay contract | Booking snapshot | Payment, operations, support, finance |
| Provider money movement | Payment/payout integration record | Booking, ledger reconciliation |
| Economic ownership and balance | Double-entry ledger | Statements, payout, finance reports |
| Tax determination | Tax decision snapshot | Quote, ledger, invoice/reporting |
| Cancellation entitlement | Cancellation decision | Payment, ledger, booking, notifications |
| Message content | Messaging | Support, moderation, dispute evidence |
| Stay incident/case | Operations/support | Risk, finance, review eligibility |
| Original review | Reviews | Aggregates, intelligence, moderation |
| Experiment assignment/exposure | Experimentation | Analytics and model evaluation |
| Model artifact/prediction | ML platform | Bounded decision services |
| Policy/manual override | Governance/audit | Relevant domain decision |

“System of record” does not mean one physical service forever. It means one domain contract owns
the truth and invariants. Replicas, indexes, feature stores, warehouse tables, and caches are
rebuildable projections.

## 11. Core event map

Events connect committed facts to asynchronous work. Names below are conceptual and should carry
event ID, schema version, occurred time, aggregate ID/version, correlation/causation ID, and actor
where appropriate.

| Domain | Representative committed events |
| --- | --- |
| Identity | `UserRegistered`, `ContactVerified`, `SessionRevoked`, `AccountRestricted` |
| Host compliance | `HostVerificationSubmitted`, `HostCapabilityChanged`, `PayoutAccountChanged` |
| Listing | `ListingCreated`, `ListingChanged`, `ListingPublished`, `ListingPaused` |
| Calendar | `AvailabilityChanged`, `CalendarImportApplied`, `InventoryHeld`, `HoldReleased` |
| Discovery | `SearchExecuted`, `ListingImpressionRecorded`, `ListingInteractionRecorded` |
| Pricing | `QuoteCreated`, `QuoteExpired`, `PromotionReserved`, `PromotionReleased` |
| Booking | `BookingCreated`, `BookingConfirmed`, `BookingExpired`, `BookingCancelled`, `StayCompleted` |
| Payment | `PaymentAttemptStarted`, `PaymentAuthorized`, `PaymentCaptured`, `RefundSucceeded` |
| Finance | `JournalPosted`, `HostFundsAvailable`, `PayoutSubmitted`, `PayoutSettled` |
| Messaging | `MessageSent`, `MessageFlagged`, `NotificationRequested`, `NotificationDelivered` |
| Operations | `CheckInRecorded`, `IncidentOpened`, `RelocationStarted` |
| Reviews | `ReviewSubmitted`, `ReviewPublished`, `ReviewModerated`, `AspectProfileUpdated` |
| Risk/support | `RiskDecisionMade`, `CaseOpened`, `RemedyApproved`, `CaseClosed` |

An event describes a committed fact, not a command disguised in past tense. Sensitive payloads are
minimized; consumers fetch authorized detail when necessary. Producers publish through an outbox so
database commit and event visibility cannot drift.

## 12. Concurrency, idempotency, and consistency strategy

### 12.1 Operations requiring strong local consistency

- Claiming or releasing inventory.
- Accepting one quote into one booking.
- Enforcing booking state transitions.
- Cumulative capture and refund limits.
- Promotion budget reservation/redemption.
- Posting balanced ledger journals.
- Making funds available and consuming them in a payout.
- One eligible review per direction and booking.

Use database constraints, row locks, optimistic versions, and short transactions. Application checks
improve error messages but do not replace enforceable constraints.

### 12.2 Operations suited to eventual consistency

- Search index and destination projection updates.
- Rating and review-aspect aggregates.
- Guest preference profiles.
- Analytics and warehouse loading.
- Notifications.
- Host dashboards.
- Risk feature refresh where the checkout path has safe fallbacks.
- Provider reconciliation after webhooks.

Each projection records source version/watermark and exposes freshness where it affects decisions.

### 12.3 Idempotency scopes

| Operation | Stable key scope | Repeated-result behavior |
| --- | --- | --- |
| Create quote | Guest/session plus client request | Return the same quote or explicit expired state |
| Create booking | Guest plus booking command | Return the same booking |
| Start payment attempt | Booking plus attempt command | Return the same provider attempt |
| Process webhook | Provider account plus provider event ID | No duplicate side effect |
| Execute cancellation | Booking plus cancellation command | Return the same decision |
| Issue refund | Booking/payment plus refund command | Return the same refund |
| Post journal | Business event plus posting type/version | No duplicate journal |
| Submit payout | Payout instruction | Return the same provider instruction |
| Import calendar event | Source calendar plus external UID/version | Update once according to source semantics |

## 13. Non-functional quality attributes by journey

### Search

- Fast bounded latency and stable pagination.
- Graceful fallback from personalized to deterministic rank.
- Fresh-enough inventory/price indication with explicit final revalidation.
- Protection against expensive unbounded map/text/filter queries.

### Checkout and booking

- Strong inventory correctness.
- Explicit quote expiry and error recovery.
- Idempotent commands across mobile/network retry.
- No provider network call inside the inventory transaction.

### Money

- Exact arithmetic, immutable versions, balanced allocation, and reconciliation.
- Strong authorization and audit for manual changes.
- Recovery from webhook loss, provider drift, and payout return.

### Stay and safety

- Reliable access to critical instructions even during partial outage.
- Urgent incident routing and human escalation.
- Careful protection of address, identity, messages, and access secrets.

### Analytics and ML

- Point-in-time correctness, lineage, privacy propagation, and monitored fallback.
- Causal evaluation for interventions such as promotion and pricing.
- Fairness and marketplace guardrails beyond a single conversion metric.

## 14. Implementation dependency map and completion gates

The groups below define a safe build order toward the single target release. They are not product
increments, launch cohorts, migration numbers, or microservice boundaries. Passing one gate allows
dependent work to proceed; it does not constitute a releasable reduced product.

### Dependency group A — Supply and policy foundations

#### Scope

- Approve the Vietnam market/legal/provider baseline and versioned market context.
- Add forward migrations for property, accommodation type, optional physical unit, public listing,
  rate plan, and market-keyed configuration before implementing new supply APIs.
- Complete property/listing CRUD and publication APIs for unique rentals and pooled hotel room types.
- Media upload workflow and structured amenities.
- Host/organization/co-host authorization and listing validation.
- Calendar generation, capacity, manual block/unblock, nightly price, and stay-rule APIs for both
  inventory modes.
- Rebuildable local development fixtures and API documentation.

#### Exit

A verified individual or organization host can publish a unique rental or pooled room type and
control a 12–18 month sellable calendar. No search, booking, or ML shortcut compensates for missing
supply or market correctness.

### Dependency group B — Location search and trip eligibility

#### Scope

- Import a bounded geographic catalog.
- Destination autocomplete and spatial retrieval.
- Published accommodation type, pooled quantity, occupancy, amenities, dates, and total-price filter.
- Deterministic baseline rank and stable pagination.
- Search/impression/click instrumentation.

#### Exit

Anonymous and signed-in guests can find eligible listings for a trip with a reliable non-personalized
fallback. Search never claims final availability.

### Dependency group C — Quote and inventory commitment

#### Scope

- Fee/rate-plan/discount rule representation.
- Versioned quote with line items and expiry.
- Versioned Vietnam market tax/disclosure adapter approved for the supported business model.
- Inventory-hold transaction, TTL, release worker, unique-unit overlap defense, and pooled-quantity
  capacity defense.
- Host expected-proceeds preview.

#### Exit

The same server inputs reproduce the same quote; unique or pooled capacity cannot be oversold;
expired holds converge safely; quote totals reconcile exactly.

### Dependency group D — Booking and approved payment provider

#### Scope

- Booking state machine and immutable listing/policy/financial snapshots.
- Payment attempt, authorization/capture, verified webhook, and retry recovery.
- Confirmation and expiration workflows.
- Transactional outbox and basic guest/host notifications.

#### Exit

The platform completes approved instant-book and request-to-book flows for unique and pooled supply
and survives duplicate commands, delayed webhooks, client disconnect, payment decline, and restart.

### Dependency group E — Cancellation, refund, and support domain

#### Scope

- Versioned cancellation policy and preview.
- Guest/host cancellation and inventory release.
- Full and partial refund orchestration.
- Booking-centric support timeline, reason codes, and controlled remedies.
- Provider reconciliation exceptions.

#### Exit

Every cancellation explains guest refund, host impact, platform impact, tax impact, and promotion
reversal; actual refund movement is traceable separately.

### Dependency group F — Ledger, statements, and host payout

#### Scope

- Double-entry ledger and booking allocations.
- Host entitlement/availability rules.
- Payout account controls, payout batch/provider integration, and reconciliation.
- Host statement and finance exception tooling.
- Manual adjustment with approval and reversal.

#### Exit

Captured cash, ledger balance, provider balance, and payout can be reconciled; failed payout preserves
host liability; journals balance and are never edited.

### Dependency group G — Stay operations, messaging, and reviews

#### Scope

- Booking conversation and transactional templates.
- Check-in instructions/access and operational incident workflow.
- Stay completion.
- Double-blind or explicitly chosen review publication policy.
- Review moderation and rebuildable category aggregates.

#### Exit

A booking can be operationally fulfilled, incidents can be handled, and only eligible completed stays
create trustworthy feedback.

### Dependency group H — Trust, safety, dispute, and compliance

#### Scope

- Cross-domain risk signals and decision service.
- Manual review queues, content moderation, appeal, and fraud labels.
- Damage claim/dispute workflows and financial remedies.
- Stronger KYC/KYB, sanctions, local licenses, payout holds, and audit.

#### Exit

High-risk decisions have evidence, reason, policy/model version, authorized action, SLA, and appeal;
risk controls integrate with booking and payout without opaque state mutation.

### Dependency group I — Review intelligence and personalized discovery

#### Scope

- Review aspect taxonomy/extraction and listing intelligence profiles.
- Guest long-term preference, session intent, and trip-context profiles.
- Baseline feature logging, point-in-time datasets, shadow/canary evaluation, and production
  learning-to-rank model with deterministic fallback.
- Cold start, diversity, fairness, exploration, explanations, and opt-out.

#### Exit

Personalization improves completed-stay satisfaction under controlled experiments, falls back safely,
and can explain its main non-sensitive reasons.

### Dependency group J — Market-aware pricing and offer optimization

#### Scope

- Demand forecast and comparable-market features.
- Booking propensity and causal price-elasticity analysis.
- Host-constrained candidate price optimization.
- Production host pricing recommendations; promotion uplift and contextual exploration only when
  the approved product policy includes them.
- Model registry, shadow/canary, guardrails, drift, and kill switch.

#### Exit

Automated recommendations respect host net/floor/ceiling, tax, fee, fairness, and marketplace policy;
incremental value is measured against a valid control, not inferred from raw conversion.

### Dependency group K — Cross-domain target-release acceptance

#### Scope

- Verify effective-dated legal/tax/payment configuration keyed by market across every domain.
- Verify Vietnamese localization, invoice/reporting, VND handling, and approved payment/payout rails.
- Verify organizations, portfolios, co-host roles, PMS/channel manager boundaries, unique rentals,
  and pooled room-type inventory through complete journeys.
- Run recovery, reconciliation, security/privacy, ML fallback, historical replay, and synthetic
  second-market contract suites.

#### Exit

Vietnam has an approved readiness checklist and each inventory mode preserves its concurrency
invariants. A synthetic second-market contract test proves expansion does not depend on
code-embedded country rules or reinterpret existing records.

### Conditional scale group L — Service extraction and multi-region infrastructure

#### Activation evidence

- Measure contention, latency, storage, team ownership, and failure blast radius.
- Extract search indexing/inference, notifications, media, payment adapters, or data pipelines when
  their profiles justify independent deployment.
- Introduce partitioning, replicas, regional routing, or CQRS projections where measured.

#### Completion gate when activated

Each added distributed boundary has an owner, SLO, versioned contract, failure/replay strategy, and
demonstrated benefit greater than its consistency and operational cost.

## 15. Gap from current repository to target release

The detailed designs for **Availability, Reservation, and Booking Lifecycle**, **Payment
Orchestration**, **Booking Modification, Cancellation, and Refund Policy**, **Ledger,
Reconciliation, and Host Payout**, **Messaging, Notifications, and Stay Operations**, **Trust,
Safety, Fraud, and Content Moderation**, **Disputes, Damage Claims, Insurance, and Customer
Support**, **Reviews, Aspect Intelligence, and Reputation**, **Data, Experimentation, and
Machine-learning Platform**, and **Vietnam Market Readiness and Internationalization** are now available:

- [`features/availability-reservation-and-booking.md`](features/availability-reservation-and-booking.md)
  defines complete-stay eligibility, inventory holds/claims, booking transitions, and the
  payment-versus-expiry race;
- [`features/payment-orchestration.md`](features/payment-orchestration.md) defines provider-independent
  collection obligations, attempts/operations/evidence, authorization/capture/refund, 3DS/SCA,
  idempotent webhook/query recovery, reconciliation, PCI boundaries, and completion gates.
- [`features/cancellation-modification-and-refund.md`](features/cancellation-modification-and-refund.md)
  defines executable policy/disclosure versions, deterministic preview and entitlement, atomic
  inventory release, booking revisions, modification delta holds, host cancellation/relocation, and
  downstream refund/ledger/payout boundaries.
- [`features/ledger-reconciliation-and-host-payout.md`](features/ledger-reconciliation-and-host-payout.md)
  defines versioned posting rules, immutable balanced journals, host payable/release/holds/reserves,
  payout destinations and execution, statements, reconciliation cases, close, recovery, and audit.
- [`features/messaging-notifications-and-stay-operations.md`](features/messaging-notifications-and-stay-operations.md)
  defines booking-scoped conversation authority, fact-derived notification intent and delivery,
  controlled instruction/access release, readiness and stay evidence, incident triage, operational
  remedies, provider recovery, security boundaries, and completion gates.
- [`features/trust-safety-fraud-and-moderation.md`](features/trust-safety-fraud-and-moderation.md)
  defines cross-domain signals and evidence, versioned policy decisions, scoped challenges and
  restrictions, content moderation, human review and appeal, domain enforcement contracts,
  adjudicated labels, model governance, privacy/fairness controls, failure recovery, and completion gates.
- [`features/disputes-damage-claims-and-support.md`](features/disputes-damage-claims-and-support.md)
  defines booking-centric cases and timelines, classification/queues/SLA, evidence custody,
  damage claims, payment disputes, protection/insurance integration, remedy/funding decisions,
  agent authority, appeals, provider recovery, quality controls, and completion gates.
- [`features/review-reputation-and-aspect-intelligence.md`](features/review-reputation-and-aspect-intelligence.md)
  defines verified directional rights, immutable revisions, double-blind reveal, exact-revision
  moderation integration, transparent aggregates, aspect evidence/profiles, contextual reputation,
  privacy/fairness controls, recovery, and completion gates.
- [`features/data-experimentation-and-ml-platform.md`](features/data-experimentation-and-ml-platform.md)
  defines event/schema governance, durable ingestion, identity-safe attribution, semantic metrics,
  deterministic experiments, point-in-time features and labels, model/artifact lifecycle, bounded
  predictions, domain-owned decisions, privacy/fairness controls, recovery, and completion gates.
- [`features/multi-market-compliance-and-localization.md`](features/multi-market-compliance-and-localization.md)
  defines the Vietnam market contract, effective policy/provider configuration, VND and locale
  semantics, historical provenance, activation gates, and non-breaking second-market boundary.

Implementation follows the dependency map above while all domain designs converge on the same
release gate. Existing single-listing inventory and coarse payment/review migrations are historical
foundations and require forward migrations for property/accommodation type/physical unit, pooled
quantity, rate plans, market context, durable events, complete money movement, and governed ML.
Provider, policy, privacy, tax, review, and model decisions may be resolved in parallel, but no
reduced vertical slice is relabeled as the finished product. The D22 design now covers multi-market
compliance and localization, linked to D02, D07, D21, and the Vietnam launch decisions.

## 16. Decisions that must be made explicitly

Some architectural questions cannot be answered by implementation detail alone.

### Marketplace and legal model

- Is the platform an agent, merchant of record, deemed supplier, payment facilitator, or a different
  role per jurisdiction?
- Who contracts with the guest for accommodation?
- Who issues which invoice and who bears refund, chargeback, tax, and protection-program liability?

### Booking model

- Instant book only, request to book, or both?
- Does payment happen before or after host approval?
- How long can inventory be held, and can a hold be extended?
- Are split stays, multi-listing carts, group bookings, or installments in scope?

### Inventory model

- How are property, accommodation type, physical unit, listing, and rate plan identifiers migrated
  from the current listing-centric schema?
- Which hotel operations require assigning a physical unit before arrival rather than at check-in?
- Which external calendar/channel source wins during conflict?

### Pricing and personalization policy

- Which components may the host control?
- Which mandatory fees are allowed and how must they be displayed?
- Does the platform recommend public prices, automatically apply them, or both by opt-in?
- Which personalized benefits/offers are permitted, and which user signals are prohibited?

### Cancellation and protection

- Which policy families exist and who can configure them?
- Which events qualify for exception handling?
- Does the platform fund relocation, damage protection, travel credit, or insurance?

### Settlement

- When does host entitlement become available?
- Which risk/dispute conditions delay payout?
- Can negative balances offset future earnings?
- Which charge, settlement, and payout currencies are supported first?

### Review and reputation

- Immediate or double-blind publication?
- What can be appealed or removed?
- How are host-to-guest reviews used without creating unfair exclusion?

### Market entry

- Which approved Vietnam legal role, VND payment/payout rails, tax treatment, invoice rules, data
  controls, languages, and support contracts satisfy the target-release gate?
- Which capabilities are explicitly unavailable outside Vietnam while preserving the market-keyed
  contract?

These decisions should be recorded as architecture/product decision records with owner, date,
assumptions, alternatives, and revisit trigger.

## 17. Build-versus-buy boundaries

Buying a provider capability does not outsource product responsibility.

| Capability | Usually provider-assisted | Platform must still own |
| --- | --- | --- |
| Payment | Card vault, acquiring, SCA, rail connectivity | Booking coordination, idempotency, allocation, ledger, reconciliation |
| Payout | Bank/wallet rail and status | Host entitlement, eligibility, statement, retries, balance |
| Identity | Document/liveness checks | Host capability policy, appeal, retention, audit |
| Tax | Rate/content or calculation adapter | Legal role, product classification, snapshots, invoice/reporting workflow |
| Maps/geocoding | Base data/API | Listing confirmation, catalog identity, privacy, search behavior |
| Messaging | Email/SMS/push transport | Notification intent, consent, templates, retries, audit |
| Moderation/ML | Detection models | Policy, final decision, explanation, appeal, monitoring |
| Smart lock | Device/access API | Booking authorization, code validity, incident recovery |

Provider abstraction must preserve provider-native references and raw evidence needed for support,
while keeping core domain state provider-neutral enough to migrate.

## 18. Testing strategy by risk

### Transactional and property tests

- Date-range boundaries, DST-adjacent stays, and calendar restriction combinations.
- Two concurrent holds/bookings for the same night.
- Quote line items, rounding, tax inclusivity, discount stacking, and allocation balance.
- Booking/payment/cancellation state transition matrices.
- Cumulative capture/refund and promotion budget constraints.
- Every ledger journal balances; reversal restores intended economic position.

### Failure and recovery tests

- Client timeout after local commit.
- Provider timeout before/after provider-side success.
- Duplicate and out-of-order webhook.
- Worker crash between claim and completion.
- Outbox retry and consumer replay.
- Calendar sync sends update/cancellation repeatedly.
- Notification provider outage.
- Payout return after prior success notification.

### Security and authorization tests

- Cross-user/cross-host resource access.
- Co-host permission boundaries.
- Payout account and refund step-up authentication.
- Admin monetary limits and maker-checker.
- Signed webhook, upload validation, redacted logs, and expired access links.

### Search, data, and model tests

- Hard-filter recall and map/date boundary correctness.
- Stable pagination and deterministic fallback.
- Feature point-in-time correctness and deletion propagation.
- Offline metric plus online guardrail definition.
- Cold start, drift, fairness slices, adversarial manipulation, shadow/canary rollback.

### Financial and operational acceptance

- Quote, booking, payment, ledger, invoice, payout, and provider report reconcile for reference cases.
- Cancellation and dispute examples reproduce from stored versions.
- Finance/support can resolve exceptions using product tools without editing the database.
- Backup restore is followed by event/reconciliation recovery and verified business balances.

## 19. Definition of done for any domain

A feature is not complete when only its happy-path endpoint works. At minimum it needs:

- written scope, non-goals, owner, and source-of-truth boundary;
- states, transitions, invariants, authorization, and failure behavior;
- API/event contracts with idempotency and versioning where needed;
- forward migration, constraints, indexes, retention, and rollback/deployment plan;
- audit and support visibility;
- privacy/security/threat review proportional to the data and action;
- logs, metrics, traces, alerts, SLOs, recovery commands, and external runbook inputs;
- test cases for concurrency, retries, boundary conditions, and recovery;
- product copy and explanation for consequential decisions;
- analytics/experiment instrumentation without contaminating transactional truth;
- updated README, data-model documentation, and feature design links.

For money, inventory, identity, moderation, and safety work, also require reconciliation or review
queues and an accountable operational owner.

## 20. Conditional infrastructure and excluded product categories

- Do not split domains into microservices merely because the logical map is large; extraction is a
  measured-scale capability and not evidence of product completeness.
- Do not introduce Kafka, sharding, CQRS, event sourcing, a feature store, or vector database without
  measured need and a clear owner.
- Do not train personalization before impression/outcome instrumentation and a strong baseline exist.
- Do not optimize dynamic price before deterministic quote, allocation, ledger, and host bounds exist.
- Do not add hotel quantity inventory by weakening single-unit overlap checks.
- Do not encode country tax policy as scattered `if country == ...` branches.
- Do not use an LLM as the authority for factual listing claims, availability, money, policy, or
  safety decisions.
- Do not let admin tools directly rewrite database state to make support appear fast.
- Do not treat provider success responses, caches, indexes, or warehouse tables as transactional
  sources of truth.

## 21. Authoritative feature-design index

Completed focused designs:

- [`availability-reservation-and-booking.md`](features/availability-reservation-and-booking.md)
- [`payment-orchestration.md`](features/payment-orchestration.md)
- [`cancellation-modification-and-refund.md`](features/cancellation-modification-and-refund.md)
- [`ledger-reconciliation-and-host-payout.md`](features/ledger-reconciliation-and-host-payout.md)
- [`messaging-notifications-and-stay-operations.md`](features/messaging-notifications-and-stay-operations.md)
- [`trust-safety-fraud-and-moderation.md`](features/trust-safety-fraud-and-moderation.md)
- [`disputes-damage-claims-and-support.md`](features/disputes-damage-claims-and-support.md)
- [`review-reputation-and-aspect-intelligence.md`](features/review-reputation-and-aspect-intelligence.md)
- [`data-experimentation-and-ml-platform.md`](features/data-experimentation-and-ml-platform.md)
- [`personalized-discovery.md`](features/personalized-discovery.md)
- [`dynamic-pricing-and-settlement.md`](features/dynamic-pricing-and-settlement.md)

The focused target designs are complete, including
[`multi-market-compliance-and-localization.md`](features/multi-market-compliance-and-localization.md),
which combines the cross-market parts of D02, D07, D21, and D22 without duplicating pricing,
payment, finance, or identity authority. New design documents are added only for a newly approved
product category or when an existing authority cannot safely contain a required contract.

Use the [standard feature-design document prompt](templates/feature-design-document-prompt.md) to
expand any remaining domain into a consistent implementation-oriented document. A future request can
be as short as: `Dùng feature-doc prompt chuẩn cho next document.`
