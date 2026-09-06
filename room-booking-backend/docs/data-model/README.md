# Room Booking data-model roadmap

The database is delivered in small, ordered phases. Each phase can be deployed only after the previous phase has reached its exit criteria.

For the product-wide problem map, domain boundaries, dependencies, and recommended delivery order
beyond these initial schema phases, see
[`../marketplace-problem-breakdown.md`](../marketplace-problem-breakdown.md).

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

| Phase | Scope | Migration | Result |
| --- | --- | --- | --- |
| 000 | PostgreSQL foundation | `000-platform.sql` | UUID, case-insensitive email and range constraints are available |
| 001 | Identity | `001-identity.sql` | Users can register and become hosts |
| 002 | Listing catalog | `002-listing-catalog.sql` | Hosts can draft and publish bookable spaces |
| 003 | Calendar and pricing | `003-calendar-pricing.sql` | Hosts can expose availability and nightly prices |
| 004 | Booking | `004-booking.sql` | Guests can reserve stays without double booking |
| 005 | Payment | `005-payment.sql` | Payment attempts and refunds are auditable and idempotent |
| 006 | Trust and engagement | `006-trust-engagement.sql` | Completed stays can be reviewed and listings saved |

## Shared conventions

- A `listing` is one independently bookable space. Its inventory is one.
- Stay ranges are half-open: `[check_in, check_out)`. A checkout date can be another booking's check-in date.
- Stay dates use PostgreSQL `date`; events use `timestamptz`; each listing stores its IANA timezone.
- Money uses `bigint` minor units and ISO 4217 currency codes. Floating-point types are forbidden for money.
- UUIDs are generated in PostgreSQL by default, while callers may still supply their own UUID.
- Status values are strings with check constraints. They are easier to evolve than PostgreSQL enum types.
- `created_at` is database-generated; application services update `updated_at` and optimistic-lock `version`.
- Business rows are retained for history. Account/listing deletion is represented by status, not a hard delete.
- JSONB is limited to immutable historical snapshots; filterable business data stays relational.

## Transaction boundaries

Use independent Spring Data JDBC aggregate roots for `User`, `Listing`, `AvailabilityDay`, `Booking`, `PaymentAttempt`, and `Review`. Do not model all calendar days as children of `Listing`; saving a large JDBC aggregate can cause unnecessary child-row replacement.

The critical booking transaction is:

1. Load and lock every requested `availability_days` row with `SELECT ... FOR UPDATE`.
2. Validate that all nights exist, are available, and satisfy stay rules.
3. Recalculate the price on the server.
4. Insert `bookings` and its immutable `booking_nights` snapshots.
5. Commit, then initiate the external payment.

The PostgreSQL exclusion constraint remains the final protection against concurrent overlapping bookings.

## Deployment rule

Liquibase runs files through `db.changelog-master.yaml`. Never edit an applied changeset; add a new forward migration. Test both an empty-database migration and an upgrade from the latest production snapshot before release.

## Future phases

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
- Shared durable events, governed analytical products and metrics, deterministic experiments,
  point-in-time ML data, model lifecycle, and bounded prediction serving, following the target
  data/ML design above.
- Hotel-style quantity inventory. That requires `properties -> room_types -> inventory_by_date` and must not reuse the single-inventory listing assumption silently.
