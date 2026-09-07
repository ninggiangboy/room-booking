# Vietnam market readiness and internationalization

## Purpose

This document defines the target backend contract for launching Room Booking in Vietnam while
preserving a non-breaking path to additional markets. It expands the cross-market parts of D02,
D07, D21, and D22 in the
[marketplace target map](../marketplace-problem-breakdown.md) without duplicating pricing, payment,
finance, identity, or booking authority.

The central question is:

> How does every consequential record retain the market, language, currency, time, policy, provider,
> and legal provenance needed to operate in Vietnam and add another country without reinterpreting
> history or scattering country checks through domain code?

## Status and dependencies

This is a target design. No market registry, legal-entity configuration, localized policy service,
or Vietnam provider configuration is implemented in Java. Existing tables contain partial country,
currency, and time-zone fields but do not constitute market readiness.

Vietnam is the only active market for the target release. Product counsel, tax/accounting owners,
security/privacy owners, and contracted providers must approve effective-dated policy content before
production activation. This document defines how those decisions are represented; it does not offer
legal or tax advice.

Authoritative regulatory material must be reviewed from primary sources, including the Government's
[legal document portal](https://vanban.chinhphu.vn/), the official text of
[Decree 13/2023/ND-CP on personal-data protection](https://vanban.chinhphu.vn/?pageid=27160&docid=207759),
the [State Bank of Vietnam](https://www.sbv.gov.vn/), and the General Department of Taxation's
[electronic-invoice portal](https://hoadondientu.gdt.gov.vn/). A decision record stores the exact
document/version and professional interpretation used; a generic portal link is never treated as an
approved rule.

Required dependencies are:

1. Contracting/legal role, legal entity, host eligibility, privacy roles, and record-retention owner.
2. Property/accommodation-type model and verified Vietnam address/geographic authority.
3. VND quote, tax, fee, invoice, collection, ledger, payout, refund, and reconciliation contracts.
4. Vietnamese/English content, template, policy-disclosure, and accessibility ownership.
5. Provider onboarding, data-processing boundaries, webhook/query recovery, and exit plans.
6. Market activation simulation, approval, immutable audit, and rollback/kill-switch behavior.

## Goals

- Make market context explicit and immutable on every contract, money movement, policy decision,
  disclosure, invoice, identity/eligibility decision, and model decision.
- Activate Vietnam without embedding `if country == "VN"` logic in domain services.
- Support Vietnamese (`vi-VN`) and approved fallback content with stable message keys and versions.
- Keep VND rounding, display, accounting, provider, and settlement behavior consistent.
- Configure provider and policy availability by market and effective interval.
- Preserve the original meaning of bookings and evidence after configuration changes.
- Prove a second market can be configured without breaking schemas, APIs, events, or historical replay.

## Non-goals

- Activating a second country, legal entity, collection currency, or deployment region.
- Encoding unapproved legal, tax, licensing, consumer-rights, invoice, or retention interpretations.
- Designing guest, host, support, finance, or admin user interfaces.
- Treating translation, geocoding, payment, identity, tax, or ML providers as policy authorities.
- Performing cross-currency booking modification or combining multiple markets in one booking.

## Core principles and invariants

### Market is contract context, not presentation metadata

`market_code` is resolved before quote creation from the property market and eligible legal entity.
The client may request a locale but cannot select the legal/tax/payment market for a booking. A
confirmed booking snapshots its market context; property or account changes never rewrite it.

### Configuration is effective-dated and approved

Every policy bundle has stable identity, version, market, legal entity, effective interval, status,
owner, approval evidence, content digest, and supersession reference. Activation is atomic. Overlap
is rejected for configuration scopes that require one authority.

### Language is not legal meaning

Localized text references a stable semantic key and policy version. Vietnamese and fallback
renderings are immutable artifacts with translator/reviewer provenance. A translation update creates
a new version and never changes evidence of what a user accepted.

### Currency never relies on locale

Amounts use integer minor units plus ISO 4217 currency. Locale controls rendering only. Each booking
has one contractual quote currency and each ledger subledger has one accounting currency. FX, when
introduced for another market, uses an immutable quoted rate and separate gain/loss accounting.

### Providers supply evidence, not policy

Provider capability, account, region, supported currency/method, data location, credentials,
timeouts, idempotency, query/webhook support, and exit plan are versioned by market. Provider output
is verified evidence consumed by the owning domain.

## Canonical context

Every relevant API response, command, event, and immutable snapshot uses the following concepts:

| Concept | Contract |
| --- | --- |
| `marketCode` | Stable platform code; `VN` is the only active release value |
| `legalEntityId` | Platform entity accountable for the configured operation |
| `currency` | ISO 4217 code; target Vietnam commercial flows use `VND` |
| `locale` | BCP 47 presentation preference such as `vi-VN`; never legal authority |
| `timeZone` | IANA zone for property-local dates, deadlines, and rendered instants |
| `policyBundleId/version` | Immutable approved rules and disclosures used by a decision |
| `providerAccountId/version` | Approved external integration context without exposing secrets |
| `contentKey/version` | Stable semantic message plus immutable localized rendering |
| `decisionInstant` | UTC instant at which effective configuration was resolved |

Missing authoritative context fails closed for publication, quote, booking confirmation, collection,
payout, and high-impact decisions. Read-only discovery may degrade only with explicit reason codes and
must not invent eligibility or price.

## Conceptual data model

- `markets`: stable code, supported locales/currencies/time zones, lifecycle, and activation version.
- `legal_entities`: accountable entity and approved markets; sensitive registration data is protected.
- `market_policy_bundles`: effective-dated product/legal/tax/privacy/retention references and digest.
- `market_capabilities`: capability/method/rail availability with deterministic eligibility conditions.
- `provider_accounts`: provider family, capability, market/entity/currency scope, lifecycle, and secret reference.
- `localized_contents`: semantic key, locale, immutable content version, reviewer, and policy linkage.
- `market_approval_records`: actor, role, evidence reference, decision, instant, and supersession.

Booking, quote, payment, refund, journal, payout, review, case, notification, experiment, feature, and
prediction records snapshot or reference the exact applicable context. Filterable fields remain
relational; JSON may retain immutable external evidence or rendered snapshots, not active policy.

The current schema requires forward migrations. Backfill marks historical rows with an explicit
legacy context and blocks consequential target workflows until the row is reconciled; it must not
guess a market from currency, phone number, IP address, or current user profile.

## Service boundaries

- `MarketContextService` resolves property market, active legal entity, currency, and time zone.
- `PolicyRegistry` resolves one approved effective policy bundle and returns immutable references.
- `LocalizationService` renders approved semantic content without changing domain decisions.
- Domain services remain authoritative for eligibility, price, booking, payment, finance, review,
  safety, and support outcomes.
- Provider adapters validate their approved account/capability context and return normalized evidence.

These are logical modular-monolith boundaries. They do not require network calls or independent
deployment. Domain transactions consume a previously resolved configuration snapshot or lock the
applicable configuration row; they never depend on a remote configuration service mid-commit.

## API and event behavior

Public discovery requests may supply `locale` and presentation currency preferences, but target
Vietnam bookings use the authoritative property market and VND contract. Search, listing, quote, and
booking responses expose `marketCode`, contractual `currency`, applicable time zone, and user-safe
policy/disclosure references.

Administrative domain commands for proposing, approving, activating, superseding, or disabling
configuration require scoped authority, reason, optimistic version, and idempotency key. This design
specifies commands and audit behavior only, not an admin UI.

Committed events include `marketCode`, schema version, correlation/causation, occurred time, owning
aggregate identity, and relevant immutable policy/provider references. Consumers reject unsupported
market/schema combinations to quarantine rather than silently apply defaults.

## Security, privacy, and ML

- Secrets live in a secret manager and events/logs contain only provider-account references.
- Personal-data purpose, consent/other approved basis, retention, access, deletion restriction, and
  cross-border/provider evidence are versioned under the approved Vietnam policy interpretation.
- Sensitive documents and exact addresses are encrypted, purpose-limited, audited, and excluded from
  general analytics and model features.
- Model training and serving record market, locale, feature time/version, model version, fallback,
  and policy decision. A model validated for one market is not automatically valid for another.
- Vietnam launch evaluation covers Vietnamese text quality, demographic/market slices permitted by
  policy, false-positive impact, calibration, latency, cost, abuse, and deterministic fallback.

## Target-release dependencies and completion gates

1. **Decision gate:** accountable owners approve Vietnam role, entity, providers, supported supply,
   policies, data flows, retention, invoice/tax interpretation, and evidence sources.
2. **Contract gate:** canonical context is present in schemas, APIs, events, snapshots, and errors;
   listing-centric historical rows have an explicit migration/backfill plan.
3. **Domain gate:** unique rental and pooled hotel journeys resolve identical market semantics across
   quote, booking, payment, cancellation, ledger, payout, messaging, review, dispute, and ML.
4. **Localization gate:** Vietnamese and fallback artifacts are complete, versioned, reviewed, and
   reproducible from acceptance/notification evidence.
5. **Provider gate:** approved Vietnam integrations pass sandbox/contract, retry, webhook/query,
   reconciliation, outage, credential-rotation, and exit tests.
6. **Release gate:** production activation is atomic, reversible for new actions, immutable for
   history, audited, monitored, and proven by a synthetic second-market compatibility test.

## Verification checklist

- [ ] A client cannot choose a different booking market than the property authority.
- [ ] Unique rentals and pooled hotel types retain market context through complete booking journeys.
- [ ] VND amounts reconcile from quote through refund, journal, payout, invoice, and statement.
- [ ] Expired, overlapping, unapproved, or missing policy/provider configuration fails closed.
- [ ] Historical replay resolves the recorded version rather than current configuration.
- [ ] Locale changes alter presentation only, never amounts, eligibility, deadlines, or policy meaning.
- [ ] Duplicate activation and provider callbacks are idempotent; out-of-order facts quarantine safely.
- [ ] Sensitive data is absent from logs, general events, analytics, and prohibited model features.
- [ ] ML failure uses a versioned deterministic fallback and cannot change transactional truth.
- [ ] A synthetic second market is added in tests without schema/API/event breaking changes.

## Decisions required before implementation

1. Approved Vietnam contracting and legal-entity model, accommodation responsibilities, and owners.
2. Host individual/organization eligibility, verification, licensing, appeal, and refresh policies.
3. VND price-display, fee, tax, invoice, refund, withholding, ledger, and payout interpretations.
4. Approved payment, payout, identity, messaging, translation, geocoding, storage, and ML providers.
5. Vietnamese/fallback locales, policy-copy approval, accessibility, and customer-contact commitments.
6. Personal-data roles, purposes, consent/other approved bases, retention, deletion, incident,
   provider, and cross-border controls under the approved professional interpretation.
7. Target rate plans and payment schedules; unsupported variants must be explicit product exclusions.
8. Configuration approval roles, maker-checker thresholds, emergency disable behavior, and audit retention.

Each decision is an ADR with owner, date, primary-source links, professional approval evidence,
alternatives, effective interval, affected contracts, rollback behavior, and revisit trigger.
