# Room Booking data-model history and target-state gaps

The numbered SQL files are historical, ordered Liquibase changesets, not product phases and not a
progressive definition of the finished backend. They describe what exists today. The authoritative
target release is defined by the master map and feature designs; differences require new forward-only
migrations rather than edits to applied changesets.

For the product-wide problem map, domain boundaries, implementation dependencies, and cumulative
completion gates beyond these historical changesets, see
[`../marketplace-problem-breakdown.md`](../marketplace-problem-breakdown.md).

The target shared identifier, time, locale, money, request/error, command-idempotency, outbox/inbox,
audit, compatible migration/deployment, security, observability, and recovery primitives are
documented in [`../features/platform-foundation.md`](../features/platform-foundation.md).

The target principal and organization model, account lifecycle, credential/session integrity,
refresh reuse detection, assurance and step-up, resource-scoped capability authorization, bounded
delegation, account recovery, operator authority, and erasure design is documented in
[`../features/identity-accounts-and-access.md`](../features/identity-accounts-and-access.md).

The target availability evaluation, inventory-hold, concurrency, booking lifecycle, modification,
and external calendar design is documented in
[`../features/availability-reservation-and-booking.md`](../features/availability-reservation-and-booking.md).

The target provider-independent payment state, operation idempotency, webhook/query recovery,
refund execution, dispute gateway, and reconciliation design is documented in
[`../features/payment-orchestration.md`](../features/payment-orchestration.md).

The target executable cancellation policy, booking revision, immutable entitlement, exact inventory
release, modification delta, host cancellation, relocation, and refund-instruction design is
documented in
[`../features/cancellation-modification-and-refund.md`](../features/cancellation-modification-and-refund.md).

The target accounting books, posting rules, immutable balanced journal, host payable and release,
payout destinations/instructions, statements, reconciliation, recovery, and close design is
documented in
[`../features/ledger-reconciliation-and-host-payout.md`](../features/ledger-reconciliation-and-host-payout.md).

The target booking-conversation, notification-intent and delivery, controlled arrival-instruction
and access, readiness, stay evidence, and operational-incident design is documented in
[`../features/messaging-notifications-and-stay-operations.md`](../features/messaging-notifications-and-stay-operations.md).

The target cross-domain signal, risk-decision, challenge/restriction, content-moderation, human
review/appeal, fraud-label, privacy/fairness, and model-governance design is documented in
[`../features/trust-safety-fraud-and-moderation.md`](../features/trust-safety-fraud-and-moderation.md).

The target booking-centric support case, evidence custody, damage claim, payment dispute,
protection/insurance, remedy/funding decision, agent authority, appeal, and quality design is
documented in
[`../features/disputes-damage-claims-and-support.md`](../features/disputes-damage-claims-and-support.md).

The target verified review-right, immutable revision, double-blind publication, exact-revision
moderation, public aggregate, aspect intelligence, reviewer attention, and contextual reputation
design is documented in
[`../features/review-reputation-and-aspect-intelligence.md`](../features/review-reputation-and-aspect-intelligence.md).

The target event taxonomy, durable outbox/inbox, analytical lineage, semantic metric,
experimentation, point-in-time feature/label, model registry, prediction, privacy, and recovery design
is documented in
[`../features/data-experimentation-and-ml-platform.md`](../features/data-experimentation-and-ml-platform.md).

The target Vietnam market context, policy/provider registry, VND/locale semantics, immutable
configuration provenance, and second-market compatibility boundary are documented in
[`../features/multi-market-compliance-and-localization.md`](../features/multi-market-compliance-and-localization.md).

| Migration | Historical scope | File | Existing result |
| --- | --- | --- | --- |
| 000 | PostgreSQL foundation | `000-platform.sql` | UUID, case-insensitive email and range constraints are available |
| 001 | Identity | `001-identity.sql` | Users can register and become hosts |
| 002 | Listing catalog | `002-listing-catalog.sql` | Superseded: retired by `016`, replaced by property/accommodation type/listing |
| 003 | Calendar and pricing | `003-calendar-pricing.sql` | Superseded: retired by `016`, replaced by `018` inventory |
| 004 | Booking | `004-booking.sql` | Superseded: retired by `016`, replaced by `020` booking lifecycle |
| 005 | Payment | `005-payment.sql` | Superseded: retired by `016`, replaced by `021` payment orchestration |
| 006 | Trust and engagement | `006-trust-engagement.sql` | Superseded: retired by `016`, replaced by `026` reviews and `029` saved listings |
| 012 | Shared platform primitives | `012-platform-primitives.sql` | Commands are retry-safe, facts publish once, audit is append-only |
| 013 | Market configuration | `013-market-configuration.sql` | Every decision can name the market, entity, and policy version that governed it |
| 014 | Identity target model | `014-identity-target-model.sql` | Authority is scoped to resources, sessions own token lineage, contacts are verified |
| 015 | Host verification | `015-host-verification.sql` | A seller's identity, eligibility, and payout destination are established before publication |
| 016 | Supply catalog | `016-supply-catalog.sql` | Property, accommodation type, unit, listing, and rate plan replace the listing-centric model |
| 017 | Geographic catalog | `017-geographic-catalog.sql` | Properties resolve to destinations, landmarks, and explainable coordinates |
| 018 | Inventory and calendar | `018-inventory-and-calendar.sql` | Overselling is impossible under concurrency, for both unique and pooled supply |
| 019 | Pricing, promotions, quotes, tax | `019-pricing-promotions-quotes-tax.sql` | Every price shown can be explained afterwards: immutable rule versions, write-once quotes, per-line tax liability |
| 020 | Booking lifecycle | `020-booking-lifecycle.sql` | The contract survives what happens to it: five independent state dimensions, snapshotted terms, append-only evidence |
| 021 | Payment orchestration | `021-payment-orchestration.sql` | What happens after money leaves the platform's control stays provable: database-enforced capture and refund ceilings, a submission fence, append-only provider evidence |
| 022 | Ledger, reconciliation, and host payout | `022-ledger-and-payout.sql` | The journal that says what the platform owns and owes: a deferred balance rule no code path can commit around, immutable posted history, and host money consumed exactly once at the payout boundary |
| 023 | Cancellation, modification, and refund | `023-cancellation-and-modification.sql` | A cancellation is a recalculation somebody must be able to explain: frozen policy terms, a booking revision chain that cannot fork, and a committed decision whose lines are made to add up by a deferred rule |

## Shared conventions

- Target vocabulary separates `property`, `accommodation_type`, optional `physical_unit`, public
  `listing`, and `rate_plan`. Existing listing-centric tables are historical foundations.
- Unique rentals use accommodation-type capacity one; hotel room types use pooled per-date quantity.
- Stay ranges are half-open: `[check_in, check_out)`. A checkout date can be another booking's check-in date.
- Stay dates use PostgreSQL `date`; events use `timestamptz`; each listing stores its IANA timezone.
  [Date, time, and time-zone handling](../features/date-time-and-time-zone-handling.md) owns these rules.
- Money uses `bigint` minor units and ISO 4217 currency codes. Floating-point types are forbidden for money.
- UUIDs are generated in PostgreSQL by default, while callers may still supply their own UUID.
- Status values are strings with check constraints. They are easier to evolve than PostgreSQL enum types.
- For tables the application writes, `created_at` and `updated_at` come from the shared UTC application
  clock through Spring Data JDBC auditing, and migration `011` removed their `DEFAULT now()` so a second
  database clock cannot silently supply them. Application services also maintain optimistic-lock `version`.
- Business rows are retained for history. Account/listing deletion is represented by status, not a hard delete.
- JSONB is limited to immutable historical snapshots; filterable business data stays relational.

## Transaction boundaries

Use independent Spring Data JDBC aggregate roots for identity, property/catalog, accommodation type,
inventory day/claim, booking, payment, ledger, review, and model-decision ownership. Do not model all
calendar days or physical units as children of one listing aggregate; saving a large JDBC aggregate
can cause unnecessary replacement and contention.

The critical booking transaction for either inventory mode is:

1. Load and lock every requested accommodation-type inventory date in stable order.
2. Validate that all nights exist, have sufficient sellable quantity, and satisfy stay rules.
3. Recalculate the price on the server.
4. Insert the booking, immutable nightly/offer snapshots, and quantity claim; physical-unit assignment
   may remain null until the configured hotel assignment point.
5. Commit, then initiate the external payment.

Unique rentals retain an exclusion constraint; pooled types use locked per-date counters plus
database checks preventing sold/held quantity from exceeding capacity.

## Deployment rule

Liquibase runs files through `db.changelog-master.yaml`. Never edit an applied changeset; add a new forward migration. Test both an empty-database migration and an upgrade from the latest production snapshot before release.

## Forward migrations required for the target release

- Account holders, organizations and membership, contact channels, durable sessions, credentials,
  capability grants and scoped restrictions, authentication-attempt evidence, and identity audit,
  following the target identity design above.
- Property, accommodation type, physical unit, public listing, rate plan, and both inventory modes.
- Market-keyed configuration and immutable Vietnam policy/provider/tax/invoice provenance.
- Messaging, notifications, check-in, access, and stay operations, following the target design above.
- Trust, safety, fraud prevention, content moderation, risk review, and appeals, following the target
  design above.
- Promotion/coupon rules.
- Host payout ledger and marketplace reconciliation, following the target finance design above.
- iCal import/export.
- Disputes, damage claims, protection/insurance integration, and support cases, following the target
  design above.
- Review cycles, double-blind publication, exact-revision moderation, rebuildable rating/aspect
  projections, and contextual reputation, following the target review design above.
- Shared command idempotency, durable outbox/inbox, append-only audit, and compatible deployment
  primitives, following the target platform-foundation design above.
- Governed event taxonomy, analytical products and metrics, deterministic experiments, point-in-time
  ML data, model lifecycle, bounded prediction serving, and model-artifact lineage, following the
  target data/ML design above.

These are cumulative target gaps, not optional product phases. Their migration order follows foreign
keys, backfill safety, and compatibility requirements; the release is incomplete until every
required domain design has a verified target-state representation.
