# Availability, reservation, and booking lifecycle

## Purpose

This document defines how Room Booking decides whether a stay can be sold, temporarily reserves
inventory during checkout, converts an accepted quote into a confirmed booking, and safely changes
or releases inventory later. It expands D05 and D08 in
[`../marketplace-problem-breakdown.md`](../marketplace-problem-breakdown.md) and consumes the quote
contract from [`dynamic-pricing-and-settlement.md`](dynamic-pricing-and-settlement.md). Detailed
post-confirmation cancellation, replacement-contract, and refund-entitlement behavior is defined in
[`cancellation-modification-and-refund.md`](cancellation-modification-and-refund.md). Balanced journal,
host-fund release, payout, and financial reconciliation behavior is defined in
[`ledger-reconciliation-and-host-payout.md`](ledger-reconciliation-and-host-payout.md).
Booking conversations, fact-derived notification delivery, controlled arrival/access, operational
observations, incidents, and evidence-backed completion requests are defined in
[`messaging-notifications-and-stay-operations.md`](messaging-notifications-and-stay-operations.md);
this document retains final booking lifecycle authority.
[Trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns versioned
checkout risk decisions, bounded challenge/review, and scoped interventions; this document alone
claims/releases inventory and enforces any applicable booking decision under its own invariants.
The [disputes, damage claims, insurance, and support design](disputes-damage-claims-and-support.md)
owns case coordination and remedy authorization but must request every booking or inventory change
through the guarded commands defined here.
[Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md) owns
directional review rights and publication after consuming the committed `StayCompleted` fact; it
cannot infer or change the booking outcome defined here.

The central product question is:

> For this exact guest, stay, inventory resource, and instant, can the platform reserve the complete
> trip and progress it to a durable contract without selling the same inventory twice?

## Status and dependencies

[Vietnam market readiness and internationalization](multi-market-compliance-and-localization.md)
owns market, currency, locale, policy-bundle, and provider context consumed by this design.

This is a target design, not a description of implemented Java APIs. The current repository already
provides useful transactional foundations:

- [`003-calendar-pricing.md`](../data-model/003-calendar-pricing.md) defines one
  `availability_days` row per listing-local date and makes missing rows unavailable;
- [`004-booking.md`](../data-model/004-booking.md) defines half-open booking ranges, immutable nightly
  snapshots, and a PostgreSQL exclusion constraint for overlapping pending/confirmed bookings;
- [`005-payment.md`](../data-model/005-payment.md) defines provider attempts, refunds, and webhook
  idempotency records.
- [`payment-orchestration.md`](payment-orchestration.md) defines the target provider-independent
  obligation, operation, verified-evidence, recovery, and compensation boundary consumed here.
- [`cancellation-modification-and-refund.md`](cancellation-modification-and-refund.md) defines the
  target executable policy, immutable entitlement, exact cancellation release, booking-revision,
  modification-delta, and host-relocation boundary.

The repository does not yet implement calendar, quote, hold, booking, payment, expiry, modification,
cancellation, or channel-sync services. Existing migrations must be extended by forward migrations,
not edited.

The target-release inventory model supports both unique rentals and pooled hotel room types. A
`property` owns one or more `accommodation types`; each type has a public `listing`, rate plans,
per-date sellable capacity, and optional assignable `physical units`. A unique rental is represented
by capacity one rather than by a separate simplified model.

The central invariant is:

> At most the configured sellable quantity of an inventory resource may be actively committed for
> every listing-local stay date.

The recommended dependency order is:

1. Listing catalog, publication lifecycle, capacity, house rules, and IANA time zone.
2. Materialized calendar, manual blocks, nightly pricing inputs, and stay restrictions.
3. Deterministic, versioned quote and policy acceptance.
4. Explicit hold/claim, provisional booking, and idempotency foundation.
5. Payment orchestration, verified provider outcomes, and confirmation recovery.
6. Cancellation, refund, modification, check-in, and completion.
7. iCalendar, request-to-book, channel managers, pooled quantity, and physical-unit assignment.

Search and personalized ranking may consume advisory availability in parallel, but they cannot delay
or replace authoritative checkout revalidation.

## Goals

- Evaluate complete-stay availability with stable reason codes.
- Support per-date restrictions, blocks, holds, bookings, and external blocks.
- Prevent concurrent oversell for unique-unit and pooled quantity inventory.
- Survive retries, client disconnects, process restarts, and delayed/duplicate webhooks.
- Make accepted prices, policies, time calculations, and lifecycle changes reproducible.
- Support cancellation, extension, shortening, date movement, and partial refund safely.
- Expose a complete booking/inventory timeline to operations.
- Support instant-book, request-to-book, iCalendar, channel managers, and multi-room bookings without
  weakening shared invariants.

## Non-goals

- Calculating price, tax, platform revenue, host proceeds, ledger entries, or payout.
- Using cache, search index, analytics data, payment provider, or iCalendar as inventory authority.
- Calling a third party during the inventory transaction.
- Intentional overbooking in the single-unit model.
- Building microservices before measured scale or ownership requires them.
- Rewriting confirmed history when current listing configuration changes.

## Core principles and invariants

### Local stay dates define inventory

Stay inventory is based on local dates in the listing's IANA time zone, not elapsed 24-hour periods.
This definition is shared by calendar, pricing, booking, cancellation, and external normalization.

### Search estimates; checkout commits

Search availability is advisory. Checkout repeats all checks and claims inventory atomically.
Database constraints are the final oversell defense, while application checks provide a coherent
decision and useful errors. No external payment or channel call occurs while inventory is locked.

### Quote, hold, booking, and payment are different facts

A quote freezes proposed price and terms but does not reserve a room. A hold is an explicit claim
with owner, purpose, state, expiry, and fencing/version. Booking, inventory, payment, refund, change,
and stay states remain separate internally even if the UI projects one journey status.

### Idempotency replays an outcome

A repeated command returns its original resource and outcome. It creates no duplicate booking,
claim, payment attempt, confirmation code, refund, or externally visible side effect.

### Failed modification preserves the original contract

A booking change is a replacement proposal. If its inventory, acceptance, or payment conditions do
not succeed, the original confirmed booking and claim remain valid.

### External synchronization does not own live truth

iCalendar is a delayed exchange of blocks, not real-time quantity, pricing, or restriction truth.
External conflicts are recorded and remediated rather than silently overwriting a guest contract.

### Models do not own transactional truth

ML may recommend calendar controls or detect anomalies, but it never declares inventory available,
overrides an active claim, or executes a contract transition.

### Date-range invariants

- `check_out > check_in`.
- Occupied nights are exactly local dates `d` where `check_in <= d < check_out`.
- Night count is calendar-day difference, never elapsed hours divided by 24.
- Checkout does not occupy the checkout date; adjacent stays are valid unless a turnover rule adds a
  preparation block.
- Every occupied date must exist within the materialized calendar horizon. Missing rows fail closed.
- `date`/`LocalDate` represents stay dates, `time`/`LocalTime` represents wall-clock policy, and
  `timestamptz`/`Instant` represents expiry, creation, provider, and audit facts.

### Inventory invariants

- Active claims for a single-unit resource never overlap.
- For a pool, `held_quantity + booked_quantity <= sellable_quantity` on every date.
- Expiration is a state transition. Time passing alone must not leave a stale active constraint
  indefinitely or allow two owners to believe they won.
- Confirmation converts hold ownership to booking ownership without a release/reinsert gap.
- Cancellation or expiry releases/decrements inventory exactly once.
- Historical booking nights remain after the active occupancy projection is retired.

### Contract and lifecycle invariants

- A booking references one accepted quote version or an approved auditable manual source.
- Client totals, status, expiry, host, and snapshot data are never authoritative.
- Confirmed bookings preserve listing, address, time-zone, party, policy, rule, nightly, and financial
  snapshots needed to reproduce the agreement.
- Every transition checks current state and version, records actor/reason/correlation, and writes an
  outbox event atomically.
- Terminal state cannot be reopened by a late worker or webhook.
- Payment success alone cannot resurrect released inventory.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Stay date | Local calendar date at the listing representing one occupied night |
| Stay range | Half-open local-date range `[check_in, check_out)` |
| Property | Physical and operational accommodation location |
| Accommodation type | Sellable category whose per-date capacity is inventory authority |
| Physical unit | Optional specifically assigned room, apartment, or home within an accommodation type |
| Listing | Public presentation of an accommodation type; never inventory authority |
| Inventory resource | Unique accommodation type constrained to capacity one |
| Inventory pool | Accommodation type with interchangeable physical units and quantity greater than one |
| Block | Host, operations, maintenance, legal, or external prohibition |
| Claim | Database-enforced consumption of inventory by a hold, booking, or block |
| Hold | Temporary claim owned by checkout, host approval, or modification |
| Reservation/checkout | Workflow connecting intent, quote, hold, payment, and provisional booking |
| Booking | Durable accommodation contract after required confirmation conditions succeed |
| Quote | Versioned, expiring price/policy proposal; never inventory ownership |
| CTA / CTD | Closed to arrival / closed to departure |
| Fencing token | Monotonic token preventing stale processes from changing newer state |

## End-to-end booking flow

```text
search availability estimate
  -> authoritative quote and policy snapshot
  -> guest acceptance with idempotency key
  -> locked availability revalidation
  -> active inventory hold/claim and provisional booking commit
  -> payment attempt outside the inventory transaction
  -> verified, deduplicated provider outcome
  -> hold converted to confirmed booking claim, or released/expired
  -> outbox-driven notification, finance, search, and channel projections
  -> check-in and completed stay
  -> cancellation/modification/refund branches when requested
```

This is a saga across short local transactions. The booking orchestrator coordinates progress, but
each participating domain remains authoritative for its own facts.

## Availability is a decision, not a boolean

Complete-stay availability is:

```text
listing is published and booking-enabled
AND party satisfies capacity and house rules
AND request satisfies booking window and advance notice
AND every occupied local date exists and is host-sellable
AND arrival, departure, stay-through, rate-plan, and gap restrictions pass
AND no higher-priority block conflicts
AND sufficient inventory remains after active holds/bookings
= eligible at checkedAt
```

A useful result contains:

```text
AvailabilityDecision
├── decision: AVAILABLE | UNAVAILABLE | REQUIRES_APPROVAL
├── publicReasonCodes[]
├── internalEvidence[]
├── checkedAt
├── inventoryModel and requestedQuantity
├── availableQuantityByDate[]
├── restrictionVersions[]
└── inventoryFingerprint
```

The fingerprint can detect change but does not reserve anything.

### Evaluation algorithm

1. Validate bounded dates, party, rooms, pets, and requested quantity.
2. Load listing, inventory model, IANA zone, and booking capability.
3. Derive listing-local “today” and cutoff facts from one injected server `Clock`.
4. Enumerate exact dates in `[check_in, check_out)`.
5. Load all calendar rows, restrictions, blocks, and occupancy state in bounded queries.
6. Require loaded row count to equal night count.
7. Evaluate listing/rate-plan eligibility and party capacity.
8. Evaluate booking horizon, notice, same-day cutoff, arrival and departure rules.
9. Evaluate stay-through, length-of-stay, preparation, and orphan-gap rules.
10. Subtract active claims, excluding the caller's own claim when refreshing/modifying.
11. Require sufficient inventory on every date, not merely in aggregate.
12. Return decision, reasons, versions, and fingerprint.

The claim path repeats the algorithm while holding the relevant rows or performing an equivalent
atomic compare-and-update.

### Rule precedence

1. Legal, safety, admin, and listing-disabled blocks.
2. Confirmed internal/external booking claims.
3. Active checkout or approval holds.
4. Host, maintenance, and operations blocks.
5. Explicit per-date restrictions.
6. Rate-plan restrictions.
7. Listing defaults.
8. Platform defaults.

A lower layer cannot relax a higher one. Support overrides require privilege, reason, audit, and
expiry where relevant, and can never erase an existing guest contract.

### Restriction semantics

| Rule | Evaluation |
| --- | --- |
| Minimum stay on arrival | Total nights must meet the rule effective on check-in |
| Minimum stay-through | Total nights must meet the maximum applicable value across occupied dates |
| Maximum stay | Total nights must not exceed the strictest applicable value |
| CTA | Check-in date only |
| CTD | Check-out date only |
| Closed to stay | Every occupied date |
| Arrival/departure weekday | Listing-local weekday of corresponding boundary |
| Advance notice | Booking instant versus listing-local check-in cutoff |
| Booking window | Check-in versus listing-local current instant/date |
| Preparation time | Dates before/after another occupancy claim |
| Orphan gap | Resulting free gap between neighboring claims |

The product must decide whether the existing `minimum_nights` means arrival-based or stay-through.
Target-release semantics are arrival-based. If a configured market adopts stay-through rules, it
uses a separate versioned field rather than reinterpreting arrival restrictions.

Stable public reasons should include `LISTING_NOT_BOOKABLE`, `MISSING_CALENDAR_DAY`, `DATE_BLOCKED`,
`INVENTORY_UNAVAILABLE`, `CAPACITY_EXCEEDED`, `MINIMUM_STAY_NOT_MET`, `MAXIMUM_STAY_EXCEEDED`,
`CLOSED_TO_ARRIVAL`, `CLOSED_TO_DEPARTURE`, `ADVANCE_NOTICE_NOT_MET`,
`PREPARATION_TIME_REQUIRED`, `RATE_PLAN_UNAVAILABLE`, `EXTERNAL_SYNC_TOO_STALE`, `QUOTE_EXPIRED`,
and `QUOTE_CHANGED`. Do not reveal whether another guest or a private safety block caused a conflict.

## Time zones and daylight saving

Use the listing IANA zone, not a fixed offset. IANA publishes the time-zone and daylight-saving data:
<https://data.iana.org/time-zones/tz-link.html>.

A March 7 to March 10 stay always occupies March 7, 8, and 9—three nights—even if a DST transition
makes the elapsed wall-clock duration 71 or 73 hours.

When a policy requires an instant, resolve the snapshotted local date/time and zone. A local time can
fall in a spring-forward gap or occur twice in a fall-back overlap. Recommended policy:

- prevent invalid recurring host times where feasible, otherwise shift a gap time forward only under
  explicit documented behavior;
- select the earlier offset for ambiguous check-in and later offset for ambiguous check-out;
- snapshot local date/time, zone, resolved offset and instant, plus calculation/version provenance;
- never recalculate a confirmed contractual deadline merely because runtime time-zone data changed.

Changing a listing time zone requires host confirmation, audit, future-booking validation, calendar
regeneration, and operational review when contractual instants would move. Inject `Clock`, use one
decision instant per command, run infrastructure in UTC, and never trust the client clock.

## Conceptual data model

### Inventory resource and calendar

Introduce a stable inventory identity:

```text
inventory_resources(id, listing_id, resource_type, pool_id, sellable_quantity, status, version)
```

`resource_type` is `SINGLE_UNIT`, `PHYSICAL_UNIT`, or `QUANTITY_POOL`. The current listing maps to
one `SINGLE_UNIT` resource.

Evolve `availability_days` through a forward migration toward explicit fields such as host sellable,
sellable quantity, minimum stay on arrival, minimum stay-through, maximum stay, CTA, CTD,
closed-to-stay, restriction-set version, price version, and optimistic version. Price remains a fast
materialized input; authoritative quote composition belongs to pricing.

Represent block provenance separately:

```text
inventory_blocks
├── resource/pool and stay range
├── quantity
├── source_type: HOST | OPERATIONS | MAINTENANCE | ICAL | CHANNEL_MANAGER
├── source/external identity
├── status: ACTIVE | RELEASED | SUPERSEDED | CONFLICT
├── reason and private note
└── actor, timestamps, version
```

An import must never delete a host-owned block.

### Holds and claims

```text
inventory_holds
├── id/public_id, reservation_id, subject_id, quote_id
├── purpose: CHECKOUT | HOST_APPROVAL | MODIFICATION
├── status: ACTIVE | CONSUMED | EXPIRED | RELEASED
├── expires_at, max_expires_at, extension_count
├── release_reason, fencing_token
└── timestamps and version

inventory_claims
├── resource_id
├── stay_range as canonical daterange [)
├── claim_type: HOLD | BOOKING | BLOCK | EXTERNAL_RESERVATION
├── status: ACTIVE | RELEASED | EXPIRED | SUPERSEDED
├── hold_id/booking_id/block_id
├── expires_at when temporary
└── fencing_token, timestamps, version
```

For single-unit resources, a GiST exclusion constraint prevents overlap by `resource_id` and
`stay_range` where `status = 'ACTIVE'`. PostgreSQL range types explicitly support this model:
<https://www.postgresql.org/docs/current/rangetypes.html>.

Do not use `expires_at > now()` in a partial-index predicate. Time passing does not change an index
entry. A worker or on-path command must transition stale `ACTIVE` claims. Confirmation updates the
hold claim into a booking claim without temporarily releasing inventory.

### Checkout, booking, transitions, and idempotency

```text
booking_checkouts/reservations
├── guest, listing, quote, optional booking
├── flow_type: INSTANT | REQUEST
├── status: DRAFT | HELD | PAYMENT_PENDING | HOST_PENDING | SUCCEEDED | EXPIRED | FAILED
├── accepted terms/version/time
└── expiry, failure, timestamps, version

booking_state_transitions
├── booking, from/to, transition code
├── actor type/id and reason
├── command/correlation identity
├── safe metadata
└── occurred_at

idempotency_records
├── scope, subject_id, idempotency_key
├── canonical request hash
├── resource/result identity and safe response snapshot
├── status and locked_until
└── timestamps
```

Unique `(scope, subject_id, idempotency_key)` prevents collisions. Reusing the key with a different
request hash returns `IDEMPOTENCY_KEY_REUSED`.

The current schema creates a `PENDING_PAYMENT` booking early because payment attempts reference it.
That remains acceptable if it is explicitly provisional, owns a hold, is never shown as confirmed,
and stays as audit history after expiry. Add explicit lifecycle fields rather than overloading that
one status further.

At confirmation preserve listing/address/time-zone, host/guest party, dates/local times/resolved
instants, capacity/pets/quantity, accepted house rules and cancellation policy, quote/rate plan,
nightly and line-item money, inventory decision versions, acceptance evidence, and source channel.
Queryable state remains relational; JSON is reserved for immutable display snapshots.

## Single-unit claim transaction

```text
BEGIN
  claim or replay the guest-scoped idempotency key
  load listing and exact requested calendar days
  lock rows in ascending (resource_id, stay_date) order
  atomically expire a conflicting stale hold if its deadline passed
  re-evaluate all stay, party, listing, rate-plan, and block rules at one instant
  validate quote owner, trip, party, currency, version, terms, and expiry
  create provisional booking/checkout and immutable snapshots
  insert ACTIVE hold and ACTIVE range claim
  append transition and outbox fact
  persist idempotent response
COMMIT
```

Two guests then serialize on day rows; the exclusion constraint is the final defense if a lock path
is missed. Map its conflict to `INVENTORY_JUST_SOLD`. Lock multiple resources by stable resource ID
then date to reduce deadlocks. Optimistic `version` alone cannot prevent two guests from winning.

A host calendar edit must also coordinate with claims. Host price changes do not affect a quote
already protected by a valid hold. A conflicting safety suspension or external confirmed booking
creates an explicit remediation incident, never silent guest deletion.

## Multi-room and multi-unit inventory

Individually distinct units use one `PHYSICAL_UNIT` resource per unit and the range-exclusion
algorithm. Truly interchangeable hotel rooms use one pool row per local date:

```text
inventory_pool_days
├── pool_id, stay_date
├── total_quantity, out_of_service_quantity
├── held_quantity, booked_quantity
└── version
```

For requested quantity `q`, lock every date in stable order and require:

```text
total_quantity - out_of_service_quantity - held_quantity - booked_quantity >= q
```

Increment held quantity for every date and insert itemized hold detail in one transaction.
Confirmation moves `q` from held to booked; release decrements held. Checks prevent negative counters
and capacity overflow. A conditional-update approach is also valid only if partial dates roll back as
one transaction.

Future bookings become:

```text
booking -> booking_items[] -> resource/pool, rate plan, range, quantity, guests, item status
                         \-> booking_item_nights[]
```

Whole/item/quantity cancellation releases exactly corresponding inventory and money allocations.
Never remove the listing overlap constraint to simulate multiple rooms without first adding explicit
pool enforcement and an `inventory_model` discriminator.

## Hold lifecycle

```text
ACTIVE -> CONSUMED
       -> EXPIRED
       -> RELEASED
```

Terminal holds never reactivate; a retry after termination creates a new generation. TTL is
configurable by payment method, market, risk challenge, and flow. A reasonable experiment for card
instant-book checkout is 10–15 minutes, not a universal constant.

Do not extend on page refresh. A bounded extension may be allowed for a proven active payment
challenge, with maximum total duration, extension count, new fencing token, and abuse monitoring.

Expiration uses both:

1. on-path cleanup that locks and expires a conflicting overdue hold before retrying the claim; and
2. a sweeper using bounded `FOR UPDATE SKIP LOCKED` batches.

The displayed countdown is advisory. Server transaction state decides whether a hold is consumable.
A final payment decline can release immediately; retryable failure can retain only the remaining
bounded TTL. Correctness never depends on the browser sending an abandonment request.

## Coordinated booking state machines

The UI journey can remain:

```text
DRAFT -> HELD -> PAYMENT_PENDING -> CONFIRMED -> CHECKED_IN -> COMPLETED
```

Internally, `PAYMENT_PENDING` still has an active hold. Keep dimensions distinct:

```text
checkout: DRAFT | ACTIVE | SUCCEEDED | EXPIRED | FAILED
hold:     ACTIVE | CONSUMED | EXPIRED | RELEASED
booking:  PROVISIONAL | CONFIRMED | CANCELLED | COMPLETED | NO_SHOW
payment:  NOT_STARTED | REQUIRES_ACTION | AUTHORIZED | CAPTURED | FAILED | VOIDED | REFUNDED
stay:     NOT_STARTED | CHECKED_IN | CHECKED_OUT | NO_SHOW
change:   NONE | PENDING | APPLIED | REJECTED | EXPIRED
refund:   NOT_REQUIRED | PENDING | PARTIAL | REFUNDED | FAILED
```

| From | Trigger | Result | Critical side effect |
| --- | --- | --- | --- |
| None | Accept valid quote | Provisional + held | Create snapshots and active claim |
| Provisional | Start payment | Payment pending/action | Create one provider attempt after commit |
| Provisional | Verified payment condition | Confirmed | Convert hold claim to booking claim |
| Provisional | Hold deadline/final decline | Expired/failed | Release claim once; void/refund if needed |
| Host pending | Approve/decline/timeout | Payment/confirmed or declined | Preserve or release approval hold |
| Confirmed | Check-in | Stay checked in | Record operations evidence |
| Confirmed | Cancel | Cancelled | Release eligible inventory; start refund separately |
| Confirmed | Checkout eligibility | Completed | Emit review/host-entitlement facts |

Payments submit verified outcomes to the booking orchestrator; they do not set arbitrary booking
statuses. Workers and support also use explicit authorized transition commands rather than editing
columns.

## Instant-book flow

1. Quote service performs advisory availability and stores exact price/policy with `expiresAt`; no
   inventory is held.
2. Guest accepts quote with party, terms version, and idempotency key.
3. A short local transaction revalidates and creates provisional booking, snapshots, hold, claim,
   timeline, and outbox event.
4. After commit, payment creates/reuses one provider attempt. It may require asynchronous client
   action.
5. Verified provider outcome is deduplicated.
6. A short transaction locks booking and hold, checks version/fence/totals, confirms and converts the
   claim, or follows failure/expiry compensation.
7. Notifications, search/calendar projection, finance, and outbound channels consume committed
   events asynchronously.

### Payment success versus hold expiry

Never confirm blindly when success arrives after expiry. Preferred card flow where supported:

1. authorize while hold is active;
2. on verified authorization, lock booking and hold;
3. if claim is active, confirm/consume and then capture under chosen policy;
4. if expired, reacquire inventory only under explicit policy and a fresh atomic claim;
5. if reacquisition fails, void authorization or idempotently refund captured funds and reconcile.

The transaction locking hold/claim decides the winner, not client display time, provider event time,
or webhook arrival order. Expiry and confirmation workers use the same locks so one valid transition
wins.

## Quote expiry and repricing

Quote expiry asks whether terms remain acceptable; hold expiry asks whether inventory remains owned;
payment expiry asks whether the provider flow remains usable. They may align by policy but are separate
facts.

At hold creation validate quote subject/session, listing, trip, party, currency, rate plan, promotion,
tax/policy versions, expiry, and current inventory. During a valid hold, ordinary host price changes
do not alter the accepted amount.

Re-quote results:

| Result | Behavior |
| --- | --- |
| `UNCHANGED` | Continue safely |
| `LOWER_TOTAL` | Show new amount; auto-accept only if policy proves terms are no worse |
| `HIGHER_TOTAL` | Require explicit acceptance |
| `POLICY_CHANGED` | Require explicit acceptance |
| `PROMOTION_UNAVAILABLE` | Explain new lines and require acceptance |
| `INVENTORY_LOST` | Stop and suggest alternatives |

Store old/new quote IDs and structured differences. Never silently charge a higher amount.

## Idempotency and retries

Require keys for hold creation/release/extension, payment start, host approval, cancellation,
modification acceptance, and refund instruction. Scope by subject and operation and hash canonical
semantic input.

- Duplicate in progress returns `REQUEST_IN_PROGRESS` plus polling location or waits briefly.
- Duplicate completed command returns the original resource and response semantics.
- Same key with different input returns `IDEMPOTENCY_KEY_REUSED`.
- A recovery worker can take over stale in-progress work only after `locked_until` with a new fence.
- Provider request keys, webhook inbox keys, outbox IDs, consumer inbox IDs, and ledger posting keys
  are independent boundaries; application idempotency does not replace them.

## Request-to-book

Request-to-book adds host-decision delay, authorization lifetime, inventory opportunity cost, and
guest uncertainty. Possible policies:

- **Exclusive approval hold:** strong guest expectation, but slow/abusive hosts can suppress supply.
- **Non-exclusive queue:** better utilization, but acceptance must atomically claim inventory and
  losing guests were never guaranteed dates.
- **Hybrid:** supported only when the approved Vietnam booking policy requires it; otherwise it is an
  explicit excluded policy rather than unfinished request-to-book behavior.

The target supports instant book and an exclusive `HOST_APPROVAL` request flow with bounded response
SLA, automatic timeout, host quality controls, and transparent countdown. Implementation may verify
instant book first because request-to-book depends on its claim invariants, but both flows share the
release gate. Explicitly decide authorization/capture timing, approval versus authorization expiry,
withdrawal, terms guarantee, and late approval behavior.

## Cancellation

Cancellation separates deterministic entitlement from asynchronous money movement:

```text
preview from accepted policy and contractual time facts
  -> persist versioned expiring decision
  -> authorized idempotent acceptance
  -> lock booking and claim
  -> transition booking and release eligible inventory atomically
  -> emit event
  -> void/refund, ledger adjustments, notifications, reconciliation asynchronously
```

A booking can be cancelled and inventory reopened while refund is pending/failed; refund failure must
not reactivate the stay. Before check-in, release all eligible dates. During stay, release only future
dates permitted by early-departure policy. Host cancellation must preserve evidence and trigger
remediation/relocation policy. Safety or maintenance blocks may remain after booking cancellation.

## Modification, extension, and partial refund

A modification is a replacement proposal, not an in-place edit:

```text
booking_modification
├── original booking/version
├── proposed dates, party, units, terms
├── quote and inventory delta hold
├── additional-payment/refund delta
├── status and expiry
└── acceptance/audit
```

The original remains valid until commit.

- **Extend:** claim only added dates, accept/collect delta, then atomically expand original claim and
  consume delta hold. Failure releases delta only.
- **Shorten:** apply cancellation policy, atomically shrink claim/release future dates, preserve old
  nights and add superseding/adjustment records rather than deleting history.
- **Move:** keep original claim, hold only net-new dates, validate the full proposed stay including
  new CTA/CTD boundaries, then replace range atomically.
- **Party/quantity change:** revalidate capacity, house rules, price, tax, risk, and quantity.
- **Partial refund only:** goodwill/service recovery may change money without dates; it belongs to
  refund/finance workflow, not a fake reservation modification.

With range exclusions, expanding an original claim can conflict with its own adjacent delta claim.
Lock both and retire/consume the delta in the same transaction with safe statement ordering.

## Stay lifecycle

Confirmation grants a future contract; it does not prove arrival. Check-in evidence may come from
guest, host, smart-lock, or support policy, but should not require sensitive GPS alone. Completion
occurs after contractual checkout plus grace, unless an incident/dispute requires review. No-show is
a consequential decision with evidence, not absence of an app click. Retiring an active post-checkout
claim is projection cleanup; booking/night history remains.

Completion grants downstream eligibility; it does not itself create a review. D14 consumes the exact
committed `StayCompleted` booking revision and applies the bounded review right, deadline,
double-blind publication, and correction rules in
[the review design](review-reputation-and-aspect-intelligence.md).

## iCalendar and channel managers

iCalendar is standardized by RFC 5545: <https://www.rfc-editor.org/rfc/rfc5545.html>. Treat inbound
events as source-owned blocks. Feeds generally cannot guarantee quantity, price, restrictions,
payment, guest details, or real-time delivery.

Store connections with encrypted secret URL, direction, listing/resource, adapter, status,
refresh/stale policy, ETag/Last-Modified, last attempt/success, failure count, and version. Calendar
URLs act like credentials. Fetchers must restrict schemes, block private/link-local destinations,
revalidate redirects, cap time/size/recurrence expansion, and isolate parsing to prevent SSRF and
resource-exhaustion attacks.

Normalize events by connection + `UID` + `RECURRENCE-ID`, retaining `SEQUENCE`, `DTSTAMP`,
`LAST-MODIFIED`, `STATUS`, raw date type/zone, normalized local range, content hash, import generation,
first/last seen, warnings, and controlled raw evidence. Updates and cancellations are idempotent.

- Date `DTEND` is exclusive and fits accommodation ranges.
- Zoned date-times convert deterministically to listing-local occupied dates.
- Floating/missing zones require an adapter rule or quarantine, not silent guessing.
- Recurrence expands only through bounded calendar horizon.
- Explicit cancellation is strong evidence; an event missing from one partial fetch is not.
- Retire missing events only after complete successful generations/grace, never after failed parse.

Outbound feeds use stable opaque UIDs and high-entropy revocable URLs. Export confirmed blocks without
guest name, contact, price, notes, or raw internal IDs.

Channel-manager adapters add provider reservation/event version idempotency, acknowledgement/replay,
resource/rate-plan mapping, quantities/restrictions where supported, backfill, and reconciliation.
Normalize provider events into internal commands; do not put provider-specific status in core booking
state.

Suggested precedence is safety/admin block, confirmed internal/external reservation, active hold,
host/operations block, imported iCalendar block, then open calendar. If confirmed reservations
conflict, preserve both, stop further sale, open an oversell incident, and remediate—never silently
delete one. Expose sync freshness; stale high-risk connections may warn hosts or pause instant book
under explicit policy.

## Service boundaries

| Concern | Authority | Reservation engine input/output |
| --- | --- | --- |
| Listing status, capacity, time zone, house rules | Listing catalog | Reads versioned values; snapshots contract fields |
| Calendar, restrictions, blocks, inventory claims | Calendar/inventory | Locks and claims; emits inventory facts |
| Price, fees, promotions, tax, allocation | Quote/pricing | Accepts immutable quote; does not recalculate money |
| Identity and risk eligibility | Identity/risk | Reads stable subject and decision version |
| Contract lifecycle | Booking | Owns booking state and transition timeline |
| Authorization/capture/refund | Payment | Consumes verified provider-independent outcome |
| Ledger and payout | [Finance](ledger-reconciliation-and-host-payout.md) | Consumes booking/payment facts; cannot mutate booking directly |
| External events | Channel sync | Normalizes provider data into source-owned blocks/reservations |
| Notifications | Messaging | Consumes committed events; has no transition authority |

These boundaries can remain modules in one Spring Boot deployment and one PostgreSQL database.

Recommended application services:

- `AvailabilityQueryService` evaluates complete stays without claiming inventory.
- `InventoryClaimService` creates, consumes, extends, expires, and releases claims atomically.
- `BookingCheckoutService` accepts quotes and creates/replays provisional checkout state.
- `BookingLifecycleService` owns authorized booking transitions and immutable snapshots.
- `BookingModificationService` coordinates replacement proposals and delta claims.
- `CancellationService` accepts a versioned cancellation decision and releases inventory.
- Detailed policy evaluation and decision ownership belong to
  [`cancellation-modification-and-refund.md`](cancellation-modification-and-refund.md); this module
  applies only its authorized inventory and booking-lifecycle effects.
- `CalendarManagementService` owns host calendar edits, blocks, restrictions, and horizon generation.
- `CalendarSyncService` normalizes iCalendar/channel inputs and records freshness/conflicts.
- `BookingRecoveryService` converges interrupted workflows without inventing provider facts.

Repository methods for locked date reads and claim mutations must document conceptual SQL, bound
parameters, parsed method-name operators where applicable, and result cardinality as required by the
repository guidelines.

## API behavior

```text
POST /api/v1/availability/check
POST /api/v1/quotes
POST /api/v1/booking-checkouts
GET  /api/v1/booking-checkouts/{id}
POST /api/v1/booking-checkouts/{id}/payment-attempts
POST /api/v1/booking-checkouts/{id}/release
GET  /api/v1/bookings/{id}
GET  /api/v1/bookings/{id}/timeline
POST /api/v1/bookings/{id}/cancellation-previews
POST /api/v1/bookings/{id}/cancellations
POST /api/v1/bookings/{id}/modification-quotes
POST /api/v1/bookings/{id}/modifications
GET  /api/v1/host/listings/{id}/calendar
PUT  /api/v1/host/listings/{id}/calendar-days
POST /api/v1/host/listings/{id}/blocks
POST /api/v1/host/booking-requests/{id}/approve
POST /api/v1/host/booking-requests/{id}/decline
POST /api/v1/host/calendar-connections
POST /api/v1/host/calendar-connections/{id}/sync
```

Checkout accepts quote ID, party/terms acceptance, and idempotency key—not totals, host, snapshots,
expiry, or status. Cancellation/modification preview is separate from execution.

### Error semantics

| Condition | HTTP | Stable code | Next action |
| --- | --- | --- | --- |
| Invalid dates/party | 400 | `INVALID_STAY_REQUEST` | Correct input |
| Unauthorized participant | 403 | `BOOKING_ACCESS_DENIED` | Stop |
| Unknown opaque ID | 404 | `BOOKING_NOT_FOUND` | Verify ID |
| Quote expired | 409 | `QUOTE_EXPIRED` | Re-quote |
| Price/terms changed | 409 | `QUOTE_CHANGED` | Show diff and re-accept |
| Lost inventory race | 409 | `INVENTORY_JUST_SOLD` | Suggest alternatives |
| Invalid transition | 409 | `BOOKING_STATE_CONFLICT` | Refresh state |
| Reused key/different input | 409 | `IDEMPOTENCY_KEY_REUSED` | New key for new intent |
| Temporary dependency failure | 503 | `BOOKING_DEPENDENCY_UNAVAILABLE` | Retry same key |

## Event contracts

Committed outbox facts include `AvailabilityChanged`, `InventoryHeld`, `InventoryHoldExtended`,
`InventoryHoldExpired`, `InventoryHoldReleased`, `BookingCheckoutCreated`,
`BookingPaymentRequested`, `BookingConfirmed`, `BookingDeclined`, `BookingExpired`,
`BookingCancelled`, `BookingModificationRequested`, `BookingModified`, `CheckInRecorded`,
`StayCompleted`, `CalendarImportApplied`, `CalendarSyncFailed`, and `InventoryConflictDetected`.

Events carry ID, aggregate/version, occurrence time, correlation/causation, and minimal non-sensitive
payload. Consumers tolerate duplicates. Notifications consume facts and never create booking truth.

## Workers and recovery

- expire bounded hold batches with `FOR UPDATE SKIP LOCKED` and fencing;
- time out host requests and ask payment to void authorization;
- complete eligible stays after checkout/grace;
- relay outbox events with stable IDs;
- fetch, parse, normalize, apply, and monitor external calendars in separate failure stages;
- reconcile overdue active holds, provisional bookings without holds, confirmed bookings without
  claims, orphan claims, invalid quantity counters, late payment success, external conflicts, stale
  connections, and stuck outbox records.

Repair through idempotent domain commands or open an operations case; avoid silent SQL correction.

## Failure behavior

| Failure | Behavior |
| --- | --- |
| Client timeout after hold commit | Same key returns original checkout and remaining TTL |
| Crash before payment call | Recovery continues the committed checkout once |
| Provider result unknown | Reconcile/query under same attempt; never create blind duplicate |
| Duplicate/out-of-order webhook | Inbox and state/version make side effect once |
| Payment after expiry | Fresh claim if explicitly allowed; otherwise void/refund |
| Expiry versus confirmation | Same locks; one transition wins |
| Host block versus checkout | Locks/constraint choose one; loser receives conflict |
| Missing calendar row | Fail closed |
| Quote service unavailable | Never trust client total |
| Notifications/outbox relay down | Booking remains correct; retry and alert lag |
| Failed iCalendar fetch | Preserve last good state, mark stale, retry |
| Confirmed external conflict | Stop sale and open oversell incident |
| Deadlock/serialization abort | Bounded retry under same idempotency key |

## Security, privacy, and access control

- Authorize guest by booking participation and host by listing/co-host permission.
- Use opaque public IDs and redact exact address/access instructions until policy permits disclosure.
- Encrypt calendar URLs and sensitive snapshots; never log full feed secrets.
- Rate-limit holds by guest, device, payment method, IP risk, listing, and market.
- Detect systematic hold expiry, automation, linked accounts, and inventory hoarding.
- Bound hold extensions and concurrent request-to-book attempts.
- Separate support permissions for read, cancel, refund, host-cancel, and emergency block.
- Require strong audit/approval for manual confirmation and consequential overrides.
- Validate webhook signatures and constrain hostile calendar inputs.
- Minimize PII in events, idempotency snapshots, logs, analytics, and outbound feeds.

## Observability and operations

Measure availability reasons, quote-to-hold and hold-to-confirm funnels, claim conflicts, expiry and
extension rates, idempotency replays/mismatches, lock waits/deadlocks, constraint violations, late
payment successes, worker oldest-overdue age, outbox lag, modifications, cancellations, host response
time, external sync lag/failure, and oversell incidents.

Initial correctness objectives:

- zero confirmed internal double bookings from single-unit concurrency;
- 99.9% of overdue holds reflected as expired within one minute, with on-path cleanup still safe;
- every confirmed booking has exactly one valid booking claim and immutable snapshots;
- every consequential mutation has actor, reason, correlation, version, timeline, and idempotency
  where retryable;
- booking correctness remains functional when search, recommendations, notifications, or analytics
  are unavailable.

## Testing and verification

Implementation tests must use real PostgreSQL for range, constraint, and locking behavior.

- Date/rule: one-night, adjacent, leap date, year boundary, DST gap/overlap, missing middle day, CTA,
  CTD, min/max stay, notice/window, preparation, orphan gap, party/pets/quantity.
- Concurrency: competing holds, overlap versus adjacency, host block race, expiry/confirmation race,
  cancellation/new hold, modification delta, and pool capacity exhaustion.
- Recovery: duplicate command before/during/after completion, same key/different body, crash after
  commit, duplicate/out-of-order webhook, stale fencing token, delayed sweeper, and outbox replay.
- Change: extend, shorten, move, price increase acceptance, extra charge/refund delta, failed change
  preserving original contract, partial quantity cancellation, and early departure.
- Channel: exclusive `DTEND`, UID update, recurrence exception, cancellation, partial/malformed feed,
  missing event grace, recurrence explosion, SSRF redirect, duplicate import, conflict, and no-PII
  export.
- Property tests: active single-unit claims never overlap; counters stay within capacity; a hold is
  consumed/released once; terminal transitions do not reverse; replay adds no effect; failed change
  leaves one valid original contract.

## Caching, performance, and scaling

- Materialize a rolling 12–18 month calendar in batches and extend it daily.
- Index resource/date, active claim range, active hold expiry, upcoming guest/host booking,
  transition timeline, idempotency key, external UID, and sync schedule.
- Lock in deterministic order and test popular-listing/holiday skew, not only uniform fixtures.
- Cache availability briefly by dates, party, versions, and restrictions, but never confirm from
  cache or depend on invalidation for correctness.
- Do not partition or extract a service until measured size, vacuum behavior, contention, latency,
  regional constraints, or ownership justify it. Eventual replicas cannot independently accept the
  same inventory without a stronger distributed protocol.

## Appropriate use of AI

Useful model applications are demand/cancellation forecasts, host recommendations for minimum stay
or preparation time, host-response prediction, hold-abuse and sync-drift anomalies, and alternative
date suggestions. Models provide confidence/reasons, deterministic fallback, versioning, guardrails,
and a kill switch.

Models never mark missing rows available, override a claim/constraint, invent host approval, change
accepted price, directly transition booking/payment/refund, or silently overbook based on predicted
cancellations. LLMs may summarize support timelines but do not create contractual facts.

## Target-release dependencies and completion gates

These steps are cumulative implementation dependencies for one complete release. They are not
separate product versions, and every step below is required before the booking domain is complete.

### Dependency 0 — Decisions

Record instant-book and request-to-book policy, both inventory modes, hold/extension limits,
payment authorize/capture order,
minimum-stay semantics, horizon/notice rules, first-market cancellation behavior, provisional booking
status, and canonical active-claim authority.

### Dependency 1 — Calendar and eligibility

Add the property/accommodation-type/physical-unit and per-date capacity forward migrations first.
Implement rolling calendar generation, host date/block/restriction/capacity commands, complete-stay
evaluator, reason codes, injected clock, stable date-range locking, and metrics for both inventory modes.

Exit: the host controls a bounded calendar and every trip has a reproducible eligibility decision.

### Dependency 2 — Quote acceptance and holds

Add forward migrations for holds/claims, lifecycle detail, idempotency, timeline, and outbox. Implement
atomic capacity-one range claims and pooled per-date quantity claims, explicit release, sweeper, and
on-path cleanup. Keep the existing booking overlap constraint until both target invariants are
backfilled, verified, and authoritative.

Exit: competing guests cannot hold the same night and repeated checkout returns the same result.

### Dependency 3 — Payment and confirmation saga

Implement the approved payment contract from
[`payment-orchestration.md`](payment-orchestration.md): provider-independent attempt/operation,
webhook verification/deduplication, late-success compensation, hold-to-booking claim conversion,
recovery, and timeline.

Exit: client/provider/process retries do not duplicate booking or charge.

### Dependency 4 — Cancellation and refund

Implement versioned preview, idempotent guest/host execution, exact inventory release, separate
refund state, ledger correlation, and exception operation.

### Dependency 5 — Modification and stay

Implement delta quote/hold for extend, shorten, and move; preserve original on failure; add check-in,
completion, early departure, and no-show decisions.

### Dependency 6 — iCalendar

Implement secure connection, normalized import blocks, no-PII export, freshness, conflicts, backoff,
and reconciliation.

### Dependency 7 — Request-to-book and professional supply

Add host approval after its SLA/payment/inventory policy is approved, then complete physical-unit
assignment, property-management/channel-manager integration, and quantity reconciliation over the
pooled inventory foundation established in Dependencies 1–2.

## Verification checklist

### Availability and inventory correctness

- Every published accommodation type has a 12–18 month local-date calendar and explicit capacity;
  missing dates fail closed.
- Complete-stay restrictions and capacity are deterministic and explainable.
- Accepted quote creates one explicit expiring hold and database-enforced active claim.
- Concurrent overlapping holds/bookings cannot both win; adjacent stays remain valid.
- PostgreSQL concurrency, date boundaries, recovery, and DST cases pass.

### Workflow and recovery

- Duplicate commands/provider events produce no duplicate side effect.
- Payment calls occur outside inventory-lock transactions.
- Confirmation versus expiry has one deterministic winner and compensation.
- Confirmed listing, time, policy, party, nightly, and financial facts are immutable.
- Expiry/cancellation releases inventory exactly once even with a delayed worker.
- Refund state remains distinct from booking/inventory state.

### Operations

- Every transition is visible in an authorized support timeline.
- Alerts expose stuck holds, missing claims, late payments, outbox lag, and conflicts.

## Decisions required before implementation

Create architecture decisions for:

1. canonical claim authority and migration from booking-only overlap;
2. quote versus hold lifetime;
3. authorization/capture order and late-success compensation;
4. instant versus request-to-book policy;
5. minimum-stay and CTA/CTD semantics;
6. time-zone change and DST resolution;
7. capacity-one versus pooled inventory constraints and physical-unit assignment timing;
8. iCalendar precedence, missing-event grace, and stale-feed response;
9. cancellation inventory-release point;
10. modification delta/replacement strategy.

Product owners must still answer hold length, eligible extensions, delayed payment methods, first
booking horizon, host controls, multi-item scope, price preservation during changes, early-departure
release, request approval SLA, external-source precedence, no-show evidence, and oversell ownership.
