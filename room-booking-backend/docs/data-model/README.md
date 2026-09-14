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
| 007 | Email verification | `007-email-verification.sql` | Created `email_verification_tokens`; renamed to `auth_tokens` by `008` |
| 008 | Authentication tokens | `008-auth-tokens.sql` | One token table carries verification and refresh tokens, keyed by `type` |
| 009 | Password reset | `009-password-reset.sql` | `PASSWORD_RESET` added to the token-type constraint |
| 010 | Location search | `010-location-search.sql` | Geographic areas and localized aliases; retained and reused by `017` |
| 011 | Application-owned timestamps | `011-application-owned-timestamps.sql` | The shared UTC application clock is the only writer of audit timestamps |
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
| 024 | Messaging and notifications | `024-messaging-and-notifications.sql` | What the platform said to somebody, kept apart from the facts that caused it: conversation membership and message order enforced by trigger, frozen approved wording, and one notice per event, recipient, purpose and policy |
| 025 | Stay operations | `025-stay-operations.sql` | What happened in the building, with custody: five independent readiness dimensions, instructions released per sensitivity band and audited on every retrieval, access entitlements kept apart from the credentials that honour them, and a safety severity no model can quietly lower |
| 026 | Reviews and reputation | `026-reviews-and-reputation.sql` | A completed stay earns a bounded right to speak, not a rating: sealed until both sides have or the window closes, immutable once written, and kept apart from every number derived from it |
| 027 | Trust, safety and moderation | `027-trust-safety-and-moderation.sql` | What was observed, what was decided under which version of which rule, and which domain command actually honoured it -- three separable rows, so a refusal can be explained, enforced and appealed rather than merely recorded |
| 028 | Disputes, damage claims and support | `028-disputes-and-support.sql` | An allegation, a finding, a decision and a movement of money are four different rows: a case coordinates all four without owning any of them, and no remedy exists without a named funder, a checked ceiling and an approval bound to the terms it approved |
| 029 | Discovery projections | `029-discovery-projections.sql` | Discovery reads and never decides: every table is derived and rebuildable, an opt-out is enforced where the next batch job cannot undo it, and a rank carries the epoch, policy, model and feature digest that produced it |
| 030 | Data and experimentation | `030-data-and-experimentation.sql` | Analytics observes and never repairs: contracts, arrivals, runs and numbers are different records, a number may not claim more authority than the run beneath it, and an experiment epoch is frozen the moment it buckets its first unit |
| 031 | ML platform | `031-ml-platform.sql` | A model advises and never decides: a feature value may not become effective before it could have been read, an unobserved outcome is not a negative one, a registered version is frozen the moment it is trained, and nothing reaches live traffic without approvals its own owner did not sign |
| 032 | Admin and governance | `032-admin-and-governance.sql` | An administrative action is a domain command with a name on it, never a database edit: authority is defined apart from the grant that confers it, separation of duties is refused at insert rather than reported quarterly, a configured value carries its schema, scope, priority, interval, owner and version, a kill switch is always pullable and never re-armable without approval, and emergency access expires and owes a review |
| 033 | Host operations | `033-host-operations.sql` | Advice that does not say what it is based on, how sure it is and who pays for it is pressure with a number on it: a host-facing metric is a published definition with an evidence floor rather than a query, a rate is stored beside the counts that produced it, a market benchmark clears a contributor floor or carries the true reason it was suppressed, a forecast carries the interval around its own estimate, every piece of advice records what was shown and what the host chose, and a bulk edit reports what it did to every target rather than that it succeeded |
| 034 | Growth and loyalty | `034-growth-and-loyalty.sql` | Every incentive is somebody's money: a programme that grants value names the legal entity that owes it and the ledger account it comes off, credit is held as lots so expiry belongs to the money rather than to the balance, eligibility is a version and a recorded reason, a referral qualifies against a stay that happened and is reversed before it is closed, nobody is contacted without a consent that covers this category and this channel, no campaign may claim uplift without a holdout, and a waitlist offer needs a quote holding the room behind it |

## Shared conventions

- Target vocabulary separates `property`, `accommodation_type`, optional `physical_unit`, public
  `listing`, and `rate_plan`. The listing-centric tables of `002`-`006` no longer exist: changeset
  `016-01-retire-historical-listing-stack` dropped all twelve of them in one statement group,
  because their foreign keys would not let them be retired separately.
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

## Target-release schema coverage

Migrations `012`-`034` delivered the forward migrations this document used to list as outstanding.
Every domain in [`../marketplace-problem-breakdown.md`](../marketplace-problem-breakdown.md) now has
a target-state schema representation. D23 (reliability, observability, performance, scale) needed no
tables of its own: it is satisfied by the outbox, inbox, dead-letter and audit primitives in `012`
plus runtime configuration.

Counted against a database with `000`-`034` applied:

| Measure | Count |
| --- | --- |
| Migration files, all included in `db.changelog-master.yaml` | 35 |
| Changesets applied | 554 |
| Tables, excluding Liquibase and PostGIS bookkeeping | 423 |
| Tables created, then retired by `016` | 435, then 12 |
| `CHECK` constraints | 3552 |
| Foreign keys | 1285 |
| Unique constraints | 330 |
| Exclusion constraints | 16 |
| Deferrable constraints | 31 |
| Project-owned `plpgsql` functions | 177 |
| Triggers | 299 |
| Spring Data JDBC aggregates and their repositories | 421 each |
| Enums mirroring a `CHECK` vocabulary | 841 |

Two tables carry no Java aggregate: `geo_areas` and `geo_area_names`, created by `010` and
deliberately reused rather than rebuilt by `017`, whose data-model note explains why the model was
already correct.

## What the schema does not yet prove

A schema is not a running system, and these three gaps are the ones most easily misread:

- **The services are not written.** The application still exposes only identity, authentication and
  host onboarding, across two controllers and fourteen endpoints. Every other table is reachable
  only through its repository.
- **The `014` identity cutover has not happened.** `014-10-backfill-identity-from-users` is written
  and applied, but it ran against an empty `users` table, so it has never been exercised against
  real rows. The running services still read and write `users`, `user_roles`, `host_profiles` and
  `auth_tokens`; nothing dual-writes `account_holders`, `contact_channels`, `auth_credentials` or
  `capability_grants`. An account registered today has no row in the target identity model. Steps 3
  to 5 of the cutover staged in `014`'s file comment - backfill, reconcile, switch reads - remain to
  be done, and the backfill must be re-run after dual-writing starts.
- **Constraint coverage is not the same as workflow coverage.** The invariants below the tables were
  probed scenario by scenario per migration, but no end-to-end journey has been executed against
  them.

## Deployment rule

Liquibase runs files through `db.changelog-master.yaml`. Never edit an applied changeset; add a new
forward migration. Test both an empty-database migration and an upgrade from the latest production
snapshot before release.
