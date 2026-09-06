# Booking modification, cancellation, and refund policy

## Purpose

This document defines how Room Booking changes or ends an accepted stay contract and how it derives
the resulting inventory, guest, host, platform, tax, promotion, payment, accounting, and operational
consequences. The central question is: given the exact contract accepted at booking time and the
facts known at an effective instant, what replacement contract or cancellation entitlement is
authoritative, reproducible, and safe to execute?

It expands D11 in the
[marketplace problem breakdown](../marketplace-problem-breakdown.md). It relies on:

- [Availability, reservation, and booking lifecycle](availability-reservation-and-booking.md) for
  inventory claims, booking lifecycle authority, local stay dates, and replacement-claim mechanics;
- [Dynamic pricing, quotes, money allocation, and settlement](dynamic-pricing-and-settlement.md) for
  quote lines, tax decisions, and financial allocation;
- [Ledger, reconciliation, and host payout](ledger-reconciliation-and-host-payout.md) for balanced
  postings, host payable/release, payout recovery, statements, close, and reconciliation;
- [Payment orchestration](payment-orchestration.md) for verified void/refund movement, refund
  reservations, provider recovery, and reconciliation.
- [Messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md) for
  delivery of committed change/cancellation facts and versioned check-in, no-show, incident, and
  relocation evidence.

This feature owns the contractual decision that a booking is cancelled or replaced and the immutable
calculation of its consequences. It does not infer that a provider refund succeeded, directly edit
ledger balances, reopen inventory outside the booking/inventory transaction, or treat notification
delivery and operational observations as entitlement authority.

## Status and dependencies

This is a target design. The repository contains schema foundations but no Java cancellation,
modification, refund-policy, relocation, or support-remedy implementation.

Current foundations are:

- [`004-booking.sql`](../../src/main/resources/db/changelog/changes/004-booking.sql) creates
  `bookings` with guest/host cancellation states, one cancellation-policy label, immutable listing
  JavaScript Object Notation (JSON), summary money columns, dates, timestamps, and optimistic
  version; it also creates immutable `booking_nights` price summaries;
- [`005-payment.sql`](../../src/main/resources/db/changelog/changes/005-payment.sql) creates coarse
  payment-attempt, refund, and provider-webhook records;
- [`004-booking.md`](../data-model/004-booking.md) documents the present booking state and overlap
  model;
- [`005-payment.md`](../data-model/005-payment.md) documents the present provider-facing refund
  foundation.

The current schema is insufficient for this design because it does not preserve executable policy
versions, accepted human disclosures, booking revisions, cancellation decisions, line-level
entitlement/funding allocation, refund instructions, modification proposals, delta inventory holds,
policy overrides, or relocation cases. The `bookings.cancellation_policy` values `FLEXIBLE`,
`MODERATE`, and `STRICT` are labels, not executable historical rules. The current `cancelled_at`
column does not identify who requested or authorized cancellation, which clock/policy version was
used, what inventory was released, or why each amount was retained or returned.

Existing migrations remain unchanged. Implementation requires forward-only migrations, explicit
backfill rules, and compatibility reads while current booking and payment fields remain in use.

Recommended dependency order:

1. Decide the first market, contracting/legal model, policy families, official check-in time, and
   customer-support authority.
2. Implement immutable booking financial lines and accepted policy/disclosure snapshots.
3. Implement authoritative inventory claims and a booking transition timeline.
4. Implement deterministic cancellation preview and idempotent pre-stay guest cancellation.
5. Connect approved refund instructions to payment operations and ledger postings.
6. Add host cancellation and bounded relocation/remedy workflows.
7. Add shortening, extension, date movement, party changes, and replacement bookings.
8. Add governed exception programs only after evidence, approval, funding, and audit controls exist.

The smallest correct vertical slice is one confirmed, single-unit, single-currency booking cancelled
in full by the guest before check-in under one approved policy family. It still needs line-level
explanation, exact inventory release, an immutable entitlement decision, idempotent refund handoff,
and recovery from partial failure.

## Goals

- Reproduce cancellation and change outcomes from booking-time snapshots and explicit current facts.
- Give guest and host an itemized preview before accepting a consequential action.
- Make preview and execution use the same deterministic policy evaluator.
- Release exactly the inventory authorized by the committed decision, exactly once.
- Preserve the original booking unless a replacement change can commit safely.
- Support guest cancellation, host cancellation, no-show, early departure, shortening, extension,
  date movement, party changes, rate-plan changes, and listing replacement as distinct intents.
- Calculate guest entitlement, host effect, platform effect, tax adjustment, promotion reversal, and
  external-money instruction at line level in integer minor units.
- Keep contractual entitlement distinct from payment movement, ledger posting, payout recovery,
  invoice/credit-note generation, and notification delivery.
- Make policy exceptions, waivers, force-majeure programs, goodwill, and relocation attributable,
  funded, approved, limited, and auditable.
- Survive duplicate commands, stale previews, concurrent cancellation/modification, process crashes,
  delayed providers, and out-of-order events without double release or double refund.
- Give support and finance a booking-centric timeline with the facts, versions, calculations,
  approvals, external operations, and unresolved exceptions needed to explain an outcome.

## Non-goals

- Defining country law, tax law, consumer rights, insurance terms, or force-majeure eligibility
  without an approved market-specific policy owner.
- Treating the current `FLEXIBLE`/`MODERATE`/`STRICT` label as enough to calculate an entitlement.
- Letting a payment provider determine cancellation eligibility, penalties, host earnings, or tax.
- Marking an external refund successful from a browser callback, support assertion, or notification.
- Mutating a confirmed booking's original financial, listing, party, policy, or nightly snapshots.
- Using cancellation as a generic way to correct arbitrary accounting or provider data.
- Modeling disputes, chargebacks, damage claims, insurance claims, or safety incidents in full; those
  domains may produce an approved remedy or override consumed here.
- Building unrestricted administrative create/read/update/delete (CRUD) operations over bookings,
  policy decisions, refunds, or ledger entries.
- Launching general multi-listing carts, split stays, hotel quantity inventory, cross-currency
  replacement, or automatic re-accommodation in the first release.
- Allowing a machine-learning (ML) model or large language model (LLM) to decide entitlement, legal
  eligibility, authoritative money, inventory release, or booking state.

## Core principles and invariants

### The accepted policy is part of the contract

Every confirmed booking references an immutable, executable cancellation/change policy version and
the exact human-readable disclosure accepted by the guest. Later host settings, experiments, policy
edits, translations, or market configuration cannot worsen that accepted contract retroactively.

An applicable mandatory-law or platform-protection override is represented by a new, explicit,
effective-dated decision layer. It never overwrites the accepted policy snapshot.

### One evaluator powers preview and execution

Preview and execution call the same versioned evaluator with the same canonical input schema. A
preview is advisory because time, booking state, prior refunds, stay facts, inventory, tax content,
or authorization may change before acceptance. Execution either consumes a still-valid preview
whose input fingerprint matches current authoritative facts or recomputes and returns a material
diff for renewed consent.

No separate user-interface (UI) formula, provider rule, support spreadsheet, or notification
template calculates the outcome.

### The effective instant is explicit and monotonic

Policy deadlines are evaluated at a server-recorded Coordinated Universal Time (UTC) instant. Rules
expressed relative to local check-in resolve against the booking's snapshotted Internet Assigned
Numbers Authority (IANA) time zone and official local check-in time. The client may communicate user
intent but cannot backdate the effective instant.

When an authorized request is durably received before a cutoff but finishes later, policy uses the
durable receipt instant if the approved market policy says receipt controls. Queue delay must not
silently move the guest into a worse penalty band.

### Contract, inventory, entitlement, movement, and accounting are separate facts

A committed cancellation can release inventory while a refund is pending. An approved refund can
exist before a payment provider moves money. A provider refund can succeed before the ledger event is
posted. A host payout recovery may remain open after the guest has been made whole.

These dimensions correlate through immutable identifiers and events; they do not share one combined
status.

### Historical decisions are immutable and corrections are additive

A committed cancellation decision, booking revision, allocation, and policy evaluation are never
edited to reflect a later interpretation. Corrections create a superseding decision, explicit
adjustment, reversal, or remedy linked to the prior record, with actor, reason, evidence, and
approval.

### Inventory is released only by the booking/inventory transaction

Payment, finance, messaging, support UI, and policy evaluation cannot directly mark a night
available. The cancellation or replacement commit locks the booking and claims, verifies the
expected versions, changes contractual ownership, and releases/transfers the exact affected nights
atomically. Duplicate consumers replay the committed result.

Safety, maintenance, regulatory, host, or channel blocks remain independent of the released booking
claim. Cancelling a booking does not necessarily make a night sellable.

### Failed replacement preserves the original booking

A modification is a proposal for a new contract revision. Until its inventory and required payment
condition are ready and the replacement transaction commits, the current revision and its claims
remain authoritative. Expiry, decline, lost inventory, stale price, or guest abandonment releases
only delta resources; it does not partially edit the original contract.

### Financial effects reconcile line by line

Every cancellation or modification output maps back to immutable booking financial lines and states:

- the amount returned or newly due from the guest;
- the amount retained, lost, or recovered from the host;
- the platform fee retained, reversed, or funded as goodwill;
- tax retained, reversed, or recalculated by an approved tax decision;
- promotion/credit consumption, restoration, expiry, and funder impact;
- partner, protection, relocation, or other funded adjustments.

All amounts use integer minor units in one declared International Organization for Standardization
(ISO) 4217 currency per decision. Rounding uses a versioned deterministic remainder rule. The
allocation must balance before commit.

### Entitlement cannot prove external money movement

The cancellation domain issues an exact, immutable refund or collection instruction. Payment
selects eligible verified captures and performs void/refund/collection operations. Only verified
provider evidence changes payment movement state. A failed or delayed refund never reactivates a
cancelled booking and never causes policy recalculation using newer rules.

### Cumulative effects have transactional ceilings

For one booking financial line and currency, under the appropriate locked aggregate:

```text
0 <= cancelled_or_adjusted_quantity <= original_committed_quantity
0 <= total_entitled_refund <= policy_refundable_consideration + approved_external_funding
0 <= successful_refunds + active_refund_reservations <= refundable_verified_capture
0 <= promotion_restored <= promotion_consumed_by_booking
0 <= inventory_released <= inventory_claimed_by_booking
```

An adjustment cannot refund the same source value twice. Database uniqueness and allocation rows are
the final defense; application totals provide clearer errors and reconciliation.

### Actor authority and reason are part of the command

Guest, host, automated expiry worker, stay-operations agent, support agent, finance operator, and
policy administrator have different allowed actions. Host cancellation is not encoded as guest
cancellation. A privileged exception includes reason taxonomy, evidence, authorization scope,
approval tier, policy/override version, and audit correlation.

### External failure is expected

No database transaction or inventory lock remains open during a provider call. Durable local
decisions and outbox facts precede asynchronous refund, ledger, payout, invoice, notification, and
relocation work. Each consumer is idempotent, retryable where safe, and reconcilable.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Accepted policy | Immutable executable policy version and disclosure accepted with a booking revision |
| Policy family | Host/product choice such as flexible or non-refundable; a family selects a versioned rule set rather than containing logic itself |
| Effective instant | Server-authoritative timestamp used to select a cancellation/change band |
| Official check-in instant | Booking-snapshotted local check-in date/time resolved in its IANA time zone |
| Cancellation preview | Expiring, non-authoritative explanation calculated from a fingerprint of current facts |
| Cancellation decision | Immutable committed determination that ends all or part of a stay contract and allocates consequences |
| Cancellation entitlement | Contractual amount/value owed to or retained from parties; not proof of money movement |
| Penalty | Non-refunded contractual value allocated to an identified beneficiary/funder treatment |
| Refund instruction | Exact approved amount and allocation sent to payment for execution against verified captures |
| Funding allocation | Identification of which party/account economically bears a refund, waiver, credit, relocation, or fee reversal |
| Booking revision | Immutable version of dates, party, terms, policy, and financial snapshot that supersedes an earlier revision |
| Modification proposal | Expiring candidate replacement revision plus inventory and money prerequisites |
| Delta | Deterministic difference between current and proposed contract line allocations |
| Net-new nights | Proposed stay dates not already owned by the current booking claim |
| Released nights | Current stay dates absent from the committed replacement/cancellation |
| Waiver | Authorized decision to reduce a contractual penalty, with an explicit funding source |
| Override program | Versioned market/platform rule that supersedes part of accepted policy under defined evidence and dates |
| Goodwill adjustment | Platform/host-funded remedy beyond contractual entitlement |
| Host cancellation | Host/platform termination attributable to supply failure; it has different remedy and performance consequences from guest cancellation |
| No-show | Evidence-backed stay outcome under a versioned decision policy, not merely lack of an app check-in event |
| Early departure | A checked-in guest leaves before contractual checkout; consumed and future nights may have different treatment |
| Relocation | Workflow to find or fund alternative accommodation after host/platform supply failure |
| Refund state | Provider execution state owned by payment; separate from entitlement and booking state |
| Recovery | Collection or offset of host/platform amounts after payout or other financial finalization |
| ID | Identifier; external IDs are opaque and internal identities are never authorization evidence |
| API | Application programming interface exposed to a client or another trusted module |
| AI | Artificial intelligence used only in the bounded advisory roles defined later |

Identifiers are opaque Universally Unique Identifier (UUID) or Universally Unique Lexicographically
Sortable Identifier (ULID)-like values at APIs. Stay nights are listing-local `LocalDate` values in
half-open ranges `[check_in, check_out)`. Deadlines and receipt times are UTC instants. Money is an
integer minor-unit amount plus ISO 4217 currency. Policy, booking, quote, allocation, tax, and
configuration versions are immutable identities, not display revision strings.

## End-to-end flow

### Guest cancellation

```text
guest requests preview
  -> authorize booking participant
  -> load current booking revision and accepted policy
  -> resolve official check-in instant and effective receipt instant
  -> load prior decisions, stay facts, and financial allocations
  -> deterministic policy/tax/funding evaluation
  -> persist expiring preview + input fingerprint + explanation
  -> guest accepts preview with booking/preview versions and idempotency key
  -> lock booking, active claim, idempotency row, and relevant allocation ceilings
  -> validate preview fingerprint or return changed outcome
  -> commit cancellation decision, booking transition, exact claim release, instructions, outbox
  -> asynchronously void/refund, post ledger effects, update payout, issue documents, notify
  -> reconcile every downstream result without recalculating entitlement
```

### Host cancellation and relocation

```text
host requests cancellation with structured reason/evidence
  -> authorize host/operator capability; apply step-up/risk controls
  -> calculate guest make-whole + host/platform impact
  -> require support/maker-checker approval when threshold or timing demands
  -> commit host-attributed cancellation and release/replace inventory ownership
  -> start refund and relocation/remedy workflows from immutable instructions
  -> apply host performance consequence, payout hold/recovery, documents, notifications
  -> close only when money/relocation exceptions are resolved or explicitly transferred
```

### Modification

```text
guest/host proposes dates, party, rate plan, or listing replacement
  -> authorize mutable fields and required counterparty consent
  -> quote complete proposed contract; diff against current immutable revision
  -> hold net-new inventory; retain original claims
  -> calculate additional collection or refund entitlement
  -> satisfy positive-delta payment condition when required
  -> accept with proposal/current versions and idempotency key
  -> lock current booking, proposal, current claims, delta claims, and allocations
  -> validate all prerequisites and atomically commit replacement revision/claim transfer
  -> issue capture/refund/ledger/document/notification work from committed delta
  -> compensate pre-commit money and release delta hold if replacement loses the race
```

Each local transaction is short. Policy evaluation may occur before the commit, but the commit
revalidates its authoritative input fingerprint. Provider and notification calls occur only after a
durable operation/instruction exists.

## Ownership and source-of-truth matrix

| Fact or decision | Authority | This feature's use |
| --- | --- | --- |
| Actor identity, roles, step-up state | Identity/access | Authorize command and record actor |
| Listing time zone, capacity, check-in rules | Booking snapshot for contract; catalog for proposed replacement | Resolve accepted deadlines; validate new proposal |
| Current stay contract and lifecycle | Booking | Decide whether cancellation/change is allowed |
| Current and delta inventory claims | Calendar/inventory | Release, retain, or transfer atomically |
| Accepted price, policy, tax, party, and listing facts | Booking revision snapshots | Historical calculation inputs |
| Proposed price and tax | Quote/pricing/tax | Build replacement revision and delta |
| Cancellation/change entitlement | Cancellation/modification decision | Authoritative output owned here |
| Captured, authorized, voided, refunded money | Payment | Determine executable movement, never entitlement |
| Economic ownership and balances | Ledger/finance | Post/reverse from approved allocations |
| Host payout state and recoverability | Settlement | Apply holds, offsets, or recovery without changing entitlement |
| Stay consumption/no-show evidence | Stay operations/support | Versioned input, not an implicit app signal |
| Exception/claim eligibility | Support/trust/protection program | Approved override input with evidence/version |
| Invoice/credit-note requirement | Tax/document policy | Consume decision and financial facts |
| Notification delivery | Messaging | Explain committed facts; never create them |

## Policy representation and evaluation

### Policy package

An executable policy package contains:

- immutable `policy_version_id`, family, version, lifecycle, owner, and approval;
- market, legal entity, listing/rate-plan applicability, booking/stay date applicability, and
  effective interval;
- official cutoff anchor and time-zone semantics;
- ordered bands with boundaries, grace rules, refund fractions or fixed treatment;
- line categories and component-level refund behavior;
- no-show, early-departure, host-cancellation, and change rules where applicable;
- fee, tax, promotion, credit, payout, and recovery references;
- caps, floors, rounding/remainder policy, and currency restrictions;
- required evidence and approval tier for exceptional branches;
- stable reason/explanation codes and localized disclosure template versions;
- test vectors approved with the policy.

Prefer a typed, validated rule schema and a bounded evaluator over arbitrary scripts. A policy
administrator can compose only supported predicates and outcomes; they cannot run database queries,
call providers, access unrelated user data, or post arbitrary amounts.

Published versions are immutable. Draft changes produce a new version. A booking stores both the
resolved executable version and disclosure content/version so support can reproduce what the guest
saw even if a translation catalog changes.

### Canonical evaluation input

The evaluator receives a fully materialized input, for example:

```text
policy_version_id
booking_id, booking_revision_id, booking_version
actor_type, action_type, structured_reason
request_received_at, official_check_in_at, listing_time_zone
stay dates and per-night consumption state
accepted financial lines and allocation version
captured/refunded/adjusted ceilings as factual inputs
prior cancellation/change/remedy decision IDs
approved override/waiver decision and funding source, if any
tax decision inputs/version appropriate to the event
market/legal-entity policy versions
```

The normalized input is hashed. The output stores evaluator version, input hash, result hash, rule
trace, and explanation codes. Sensitive facts are referenced rather than copied unless necessary
for durable evidence.

### Rule precedence

Use explicit precedence rather than incidental rule order. Recommended high-to-low layers are:

1. mandatory market/legal decision supplied by an approved policy or tax authority;
2. confirmed safety, protection, or extenuating-circumstance override;
3. host-attributed cancellation/remedy rule;
4. accepted booking/rate-plan policy;
5. explicit host waiver reducing a host-benefiting penalty;
6. authorized platform goodwill funded by platform budget;
7. deterministic rounding and monetary ceilings.

Higher layers may improve a guest outcome or impose a required treatment only within their approved
authority. They do not erase lower-layer provenance. If two overrides conflict, reject to manual
review rather than choose by database insertion order.

### Deadline and time-zone semantics

Policy configuration must name its anchor, such as the booking's official local check-in time, and
its interval inclusivity. Example:

```text
official_check_in_at = resolve(
  booking.check_in local date,
  snapshotted official check-in wall time,
  snapshotted IANA time zone,
  approved daylight-saving-time (DST) resolver
)

hours_before_check_in = official_check_in_at - effective_instant
```

Use instant arithmetic after resolving the local anchor. Never subtract local date-times without a
zone. For a daylight-saving gap or overlap, use the booking platform's documented resolver captured
with the policy version: reject impossible configuration, select the earlier/later offset explicitly,
or shift forward by a defined rule. Tests cover both transitions.

Policy bands declare boundaries precisely, for example `effective_instant < cutoff` versus
`effective_instant <= cutoff`. The product copy must match the executable inequality.

### Grace periods

A booking-time grace period should specify:

- start event: confirmed/accepted instant, not client display time;
- maximum duration;
- whether it ends early at a pre-check-in boundary;
- which fees/components remain non-refundable;
- exclusions for last-minute or non-refundable products;
- market/legal override;
- whether fraud/manual-review delays count;
- exact inclusive/exclusive boundary.

The evaluator selects at most one applicable grace outcome and explains why it did or did not apply.

### Policy validation and test vectors

Publishing a policy version must fail when:

- bands overlap or leave unintended gaps;
- a boundary lacks an anchor/time zone/inclusivity rule;
- a fraction, cap, or fixed amount is outside permitted bounds;
- line-category behavior is missing;
- guest refund and funding allocation cannot balance;
- a reason references an inactive approval or evidence policy;
- disclosure text and executable examples disagree;
- any supplied test vector produces a different expected result.

Golden test vectors include exact instants immediately before, at, and after each boundary, bookings
created inside/outside grace, full and partial stays, prior adjustments, tax-inclusive/exclusive
lines, promotions by different funders, and zero-capture cases.

## Cancellation calculation

### Calculation stages

For each immutable booking financial line, calculate in this order:

1. Identify original quantity, amount, currency, payer, beneficiary, funder, tax category, service
   period, and refund behavior.
2. Split consumed, retained, released, cancelled, and unaffected quantities.
3. Select the applicable policy band at the effective instant.
4. Apply contractual refund fraction/fixed treatment and non-refundable components.
5. Apply approved waiver or override without losing its separate funding allocation.
6. Ask the tax domain for the exact adjustment decision; do not derive tax by a generic percentage.
7. Reverse, restore, retain, expire, or reissue promotion/credit value under its accepted rule.
8. Subtract prior entitlement/adjustments against the same source allocations.
9. Apply caps/floors and deterministic minor-unit remainder allocation.
10. Reconcile guest, host, platform, tax, promotion, partner, and external-funder views to zero-sum
    economic allocation plus any explicitly funded new benefit.

### Line-level model

For a source line `i`, conceptual values are:

```text
cancelled_base_i = allocated_original_amount_i for cancelled quantity
contract_refund_i = policy_i(cancelled_base_i, effective_instant, stay facts)
override_refund_i = approved improvement beyond contract_refund_i
prior_applied_i = prior committed entitlement against the same source allocation
net_entitlement_i = max(0, contract_refund_i + override_refund_i - prior_applied_i)
retained_i = cancelled_base_i - contract_refund_i
```

The exact model is typed by line category. Negative line items, discounts, credits, taxes, and
percentage fees are not forced through this formula if doing so changes their economic meaning.
Instead, their rule emits explicit debit/credit allocation effects.

### Accommodation nights

Evaluate accommodation per snapshotted night where policies differ by time or stay consumption.
Examples include:

- all future nights fully refundable before a cutoff;
- first night retained and remaining nights refundable;
- a percentage of unused nights refundable;
- consumed nights non-refundable after check-in;
- early-departure notice making only nights after a later boundary refundable;
- no-show retaining a defined number/value of nights.

Do not average nightly prices before applying a per-night rule. Variable nightly amounts make average
calculation materially incorrect.

### Fees and included services

Each fee line declares its refund trigger and funder/beneficiary. Cleaning may be refundable if the
stay never begins, while a provider/service fee may follow a different accepted and legally approved
rule. A bundled or inclusive fee still needs internal allocation if refund, tax, host earning, or
platform revenue treatment differs.

Avoid policy names such as `refundFees=true`; encode which line categories, conditions, caps, and
timing the decision covers.

### Tax adjustment

Cancellation supplies the original tax decision, event type, effective date, reversed/retained base
lines, jurisdiction, legal entity, and prior adjustments to `TaxService`. Tax returns versioned
adjustment lines and document/reporting requirements.

The cancellation evaluator does not assume tax is proportional, refundable, or determined by the
guest's current location. If tax content is unavailable and the market requires a determination
before commitment, fail closed or route to a governed queue. Never guess.

### Promotions, coupons, and credits

For every benefit, the accepted snapshot states:

- funder and budget allocation;
- whether cancellation restores, expires, partially restores, or converts value;
- whether value is cash-refundable;
- whether a penalty is calculated before or after the benefit;
- deterministic proportional/remainder treatment;
- abuse and repeat-use constraints;
- original and replacement-booking transfer rules.

A platform-funded discount does not reduce host entitlement unless the accepted funding allocation
says so. Cash refund must not exceed captured refundable consideration merely because a coupon is
restored. A non-cash credit restoration is a liability operation, not a provider refund.

### Decision output

An immutable decision contains:

- booking/current revision, policy/disclosure, evaluator, tax, allocation, and configuration versions;
- actor, request/effective timestamps, reason, evidence, override, approval, correlation;
- affected nights/items/quantities and inventory-release instruction;
- original, consumed, cancelled, retained, refunded, newly due, and unaffected line allocations;
- guest cash refund, guest credit, host impact/recovery, platform impact, tax adjustment, promotion
  reversal/restoration, partner impact, and relocation/protection funding;
- payment void/refund or new collection instructions;
- ledger posting intent, payout hold/recovery intent, document intent, and notification reason codes;
- calculation trace safe for operations and a separately curated user explanation;
- decision version, input/result hashes, and supersession linkage.

The output must balance. A decision that cannot identify funding or cannot reconcile is not
committable.

## Cancellation lifecycle and state

### Preview lifecycle

```text
ACTIVE -> ACCEPTED
ACTIVE -> EXPIRED
ACTIVE -> SUPERSEDED
ACTIVE -> STALE
```

`ACCEPTED` means an execution command consumed the exact preview input/result; it does not mean a
refund succeeded. A preview has a short expiry and the booking version, effective-time band,
financial-allocation version, policy version, prior-decision watermark, and input hash.

### Cancellation command/decision lifecycle

```text
RECEIVED -> COMMITTED
RECEIVED -> REJECTED
RECEIVED -> REVIEW_REQUIRED -> COMMITTED | REJECTED | EXPIRED
```

Once `COMMITTED`, the decision is immutable. Downstream status is projected separately:

```text
inventory: RELEASED | PARTIALLY_RELEASED | RETAINED_BLOCKED | EXCEPTION
payment:   NOT_REQUIRED | VOID_PENDING | REFUND_PENDING | PARTIAL | SUCCEEDED | FAILED | UNKNOWN
ledger:    PENDING | POSTED | EXCEPTION
payout:    NOT_AFFECTED | HELD | RECOVERY_PENDING | RESOLVED | EXCEPTION
documents: NOT_REQUIRED | PENDING | ISSUED | FAILED
relocation:NOT_REQUIRED | OPEN | OFFERED | ACCEPTED | CLOSED | FAILED
```

These are read-model dimensions, not a reason to reopen the committed decision.

### Booking transitions

| Current booking condition | Actor/action | Contract result | Inventory result | Money result |
| --- | --- | --- | --- | --- |
| Provisional hold | Guest abandons or expiry wins | Expire checkout under D08 | Release hold exactly once | Void/refund late success by D09 |
| Confirmed, pre-stay | Guest full cancellation | `CANCELLED_BY_GUEST` | Release all booking-owned nights; independent blocks remain | Execute decision instructions asynchronously |
| Confirmed, pre-stay | Host full cancellation | `CANCELLED_BY_HOST` | Release or transfer to remediation block | Full/extra remedy per approved policy |
| Confirmed, in stay | Guest early departure | New terminal/adjusted stay decision | Release only eligible future nights | Refund/penalty from consumed/future split |
| Confirmed, arrival passed | Evidence-backed no-show | `NO_SHOW` | Release future nights at policy point | Apply accepted no-show allocation |
| Checked in/completed | Ordinary cancellation | Reject; use early-departure/support path | No implicit release | No implicit refund |
| Already terminal | Same idempotent command | Replay | No second release | No second instruction |
| Already terminal | Different incompatible command | Conflict/manual remedy | None | None |

The final enum names may evolve, but the actor, cause, effective instant, and consequences remain
separate from a display-friendly journey label.

## Guest cancellation

The guest can cancel only a booking in an eligible contractual state and only for the booking they
own. The command contains cancellation/preview ID, expected booking version, reason category,
optional structured comment, and idempotency key. It never contains trusted refund amounts or dates
to release.

Execution:

1. Authenticate and authorize the guest without revealing other bookings.
2. Load or recompute the preview from authoritative facts.
3. If the result is materially worse than accepted preview, return a changed-decision response and
   require explicit re-acceptance; do not silently cancel.
4. If the same or better and auto-acceptance is permitted by documented policy, persist exactly which
   comparison rule was used. The safer default is exact-result acceptance.
5. In one transaction lock idempotency, booking/revision, claims, prior allocations, and decision
   ceiling rows in a documented order.
6. Commit decision, booking transition, exact claim release, refund/void instructions, financial
   intents, timeline, and outbox.
7. Return the committed contractual outcome immediately with each asynchronous status labelled.

Reason collection must not make statutory or contractual rights conditional on intrusive free text.
Comments are minimized and sanitized; reason codes are stable analytics inputs but do not override
policy without an approved program.

## Host cancellation

Host cancellation is a supply failure and has different authorization, funding, guest protection,
ranking/performance, and support consequences. It must never be recorded as guest intent.

Before acceptance, show the host:

- guest consequence and communication timing;
- host earnings forfeited, fees/penalties, payout hold or recovery exposure;
- affected performance metrics and possible listing restrictions;
- evidence requirements and escalation path;
- whether immediate support/relocation is triggered.

Recommended controls:

- require host/operator permission for the exact listing/booking;
- require step-up authentication near arrival or above a monetary/operational threshold;
- collect a structured cause such as property unavailable, safety issue, duplicate inventory,
  regulatory closure, or host emergency;
- preserve evidence with access control and retention policy;
- require support approval for same-day/in-stay cancellation where guest safety is affected;
- prevent self-dealing through related replacement listings or fabricated guest cancellation;
- apply performance consequences from a separate versioned policy, with appeal.

Host liability cannot exceed contract/legal/program rules merely because relocation ultimately costs
more. Any platform-funded make-whole amount is an explicit expense/receivable decision.

## No-show and early departure

### No-show

No-show requires a policy-defined decision time and evidence set. Absence of an app event, Global
Positioning System (GPS) ping, message read, or smart-lock record alone is insufficient. Evidence may
combine host attestation, failed guest contact, access-system signals, and support review, with a
guest dispute window.

The decision defines:

- when inventory for remaining nights may be released;
- accommodation/fee/tax/host entitlement treatment;
- whether later guest arrival remains possible;
- effect on review eligibility, payout release, and support appeal;
- correction path if the determination was wrong.

### Early departure

An early-departure request splits consumed and future nights using listing-local dates and the
accepted rule. It may require a notice period, host acknowledgement, or support evidence. The guest
must not be promised that released nights will be resold or that resale automatically changes their
refund unless the accepted policy explicitly provides a resale-based benefit and defines its
reconciliation window.

If future nights are reopened, a later correction cannot reclaim inventory already sold. Remedy is
financial/operational, not deletion of the new booking.

## Host-caused relocation and guest remedy

Relocation is a case workflow, not an implicit extension of the cancellation transaction. The
committed host cancellation first protects contractual truth and emits a relocation requirement.
The relocation case records:

- original booking and cancellation decision;
- guest party, accessibility/hard requirements, destination/date constraints, and consent;
- approved budget/currency, funding party, limit, and expiry;
- candidates/offers and why they satisfy mandatory constraints;
- guest selection or refusal;
- replacement booking/third-party evidence;
- incremental accommodation, transport, credit, and support cost;
- host recovery/platform expense allocation;
- service-level agreement (SLA), owner, communications, and final outcome.

Search/recommendation may propose candidates but cannot reserve them or decide equivalence. Exact
address and sensitive stay data are disclosed only to authorized case participants. Automated
booking of a replacement requires explicit guest consent and the normal quote/inventory/payment
commit path.

If no acceptable inventory exists, the policy may provide full refund, travel credit, hotel
reimbursement, transport, or another remedy. Each option requires approved terms, funding, receipt
evidence where applicable, and a decision independent of provider movement.

## Partial refund, waiver, and support adjustment

A partial refund that does not change dates is a remedy/financial adjustment, not a fake booking
modification. It starts from a support, host, incident, protection, or policy decision with:

- source booking lines and maximum eligible amount;
- reason and evidence references;
- contractual versus goodwill portion;
- guest, host, platform, insurer, or partner funding allocation;
- tax and document treatment;
- approval tier and maker-checker status;
- prior adjustments and remaining ceiling;
- exact refund or credit instruction.

Hosts may offer a waiver within amounts economically attributable to them, subject to policy and
payout state. They cannot refund platform/tax/other-party value. Support may grant platform goodwill
only within role, market, per-case, daily, and aggregate budgets. Above-threshold or self-related
cases require an independent approver.

Do not expose an unrestricted `amountMinor` refund endpoint to ordinary support tools. The UI chooses
eligible source lines or an approved remedy type; the server calculates ceilings and allocation.

## Modification model

### Classify the requested change

| Change | Recommended treatment |
| --- | --- |
| Extend checkout | Hold added nights, quote full proposed stay, collect positive delta, commit revision |
| Shorten checkout | Calculate cancellation of removed future nights, release exactly those nights, commit revision and refund instruction |
| Move dates with overlap | Retain common nights, hold net-new nights, release removed nights only at commit |
| Shift to disjoint dates | Treat as replacement claim while preserving original until commit |
| Increase party/units | Revalidate capacity/rules, hold quantity if applicable, collect delta |
| Decrease party/units | Reprice under accepted change policy; do not assume proportional refund |
| Change rate plan | New quote and policy acceptance; may be prohibited after confirmation |
| Add/remove service | Versioned line-item adjustment if service supports change |
| Change primary guest | Usually prohibited or identity/support workflow, not ordinary modification |
| Change listing | Replacement booking linked to original; never edit `listing_id` in historical revision |
| Pure goodwill refund | Support adjustment, not modification |

### Proposal lifecycle

```text
DRAFT -> QUOTED -> INVENTORY_HELD -> PAYMENT_READY -> READY_TO_COMMIT -> COMMITTED
   |        |             |               |                 |
   +--------+-------------+---------------+-----------------> EXPIRED
   +--------+-------------+---------------+-----------------> CANCELLED
   +--------+-------------+---------------+-----------------> FAILED
```

`PAYMENT_READY` means the configured positive-delta condition—normally a verified authorization—has
been met; it does not necessarily mean final capture/settlement. A negative delta requires a durable
refund entitlement/instruction, not provider completion, before commit.

### Full proposed-contract quote and delta

Pricing calculates a complete proposed quote from current valid product/tax rules plus explicit
change-policy guarantees. Then the modification service compares immutable allocation identities:

```text
proposed contract total and allocation
- preserved value/allocations credited from current revision
= modification delta allocation
```

Do not simply subtract two headline totals. The delta must explain accommodation nights, fees,
discounts, credits, taxes, policy value, host proceeds, and platform/funder impact. Removed services
may incur cancellation treatment; preserved services may retain original price if guaranteed; added
services use the approved new quote.

The policy must choose whether unchanged nights retain original price, the full stay is repriced, or
a bounded hybrid applies. This choice is disclosed and versioned.

### Inventory delta

Represent original, proposed, common, added, and removed inventory explicitly:

```text
common = current claim intersection proposed requirement
added  = proposed requirement - current claim
removed = current claim - proposed requirement
```

Hold `added` inventory under the proposal; keep `common` and `removed` owned by the original booking
until commit. The commit locks resources in canonical listing/date order, verifies both claims and
versions, consumes/transfers added claims, updates the active contract pointer, and releases removed
claims in one transaction.

For a single-unit PostgreSQL exclusion model, a delta hold may overlap the booking it intends to
replace. The target claim model should identify common ownership or use safe retirement/insert order
inside one locked transaction. Never weaken the global no-overlap constraint to make modification
easier.

### Positive, zero, and negative money delta

- **Positive delta:** create a new collection obligation linked to the proposal. Prefer verified
  authorization before contract commit; capture follows the committed revision. If only direct sale
  is supported, captured-before-commit failure requires idempotent refund compensation.
- **Zero delta:** commit only after inventory and consent validation; still create allocation and
  revision records.
- **Negative delta:** commit the replacement and immutable refund entitlement/instruction together;
  provider refund proceeds asynchronously.
- **Mixed delta:** do not net unrelated currencies or funding sources. Within one currency, netting
  is allowed only if finance/legal policy and allocation provenance remain explicit.

If capture after a committed revision finally fails, use the payment/default policy: bounded retry,
revert through an explicit compensating revision if original inventory is still protected, or route
to support. Never silently rewrite the current revision or pretend payment succeeded.

### Consent and counterparty approval

A guest-initiated change requires the guest to accept new price, dates, party, policy, and material
terms. A host-initiated change cannot bind the guest merely because it is cheaper. It creates an
offer with an expiry; rejection/expiry preserves the original booking unless a separate authorized
host-cancellation process applies.

Host approval is required only where the product/rate plan explicitly requires it. Approval has an
SLA, version, scope, and expiry. A later change to the proposal invalidates prior approval.

### Booking revisions

On commit, create a new immutable booking revision containing:

- predecessor and modification/cancellation decision linkage;
- listing, dates, party, quantity, check-in/out, house rules, and policy snapshots;
- complete nightly and financial line snapshots;
- accepted quote, tax, promotion, and allocation versions;
- guest/host consent and disclosure evidence;
- contractual effective instant and created/committed timestamps.

The booking aggregate points to one current revision for operational reads. Historical revisions
remain queryable and are never rewritten. A change number shown to users is derived from committed
revision order, not used as security identity.

## Force majeure and extenuating circumstances

Do not start with an LLM or open-ended support judgment. Model exception handling as a governed,
versioned program:

- eligible event taxonomy, geography, occurrence/effective window, and authoritative evidence source;
- affected booking/stay conditions and excluded products;
- guest/host/platform rights and obligations;
- exact policy override and funding allocation;
- application window, evidence requirements, appeal, and decision SLA;
- fraud/duplicate-claim controls;
- program owner, legal approval, budget, sunset, and retrospective review.

An event declaration does not automatically change bookings. Each booking receives an immutable
eligibility/override decision. Bulk incidents use deterministic candidate selection, dry-run impact,
rate-limited commands, maker-checker approval, progress checkpoints, and per-booking idempotency.

If evidence is incomplete or rules conflict, keep the contractual booking state unchanged and route
to review. Emergency safety operations may block inventory independently without fabricating a
cancellation outcome.

## Conceptual data model

### Current versus proposed records

Current tables remain migration inputs:

- `bookings`: current lifecycle and summary snapshots;
- `booking_nights`: current immutable nightly price summary;
- `payment_attempts`, `refunds`, `payment_webhook_events`: coarse provider evidence.

Proposed target records follow. Names are conceptual and may be adjusted in implementation, but the
separations and constraints are required.

### Policy and disclosure

`cancellation_policy_definitions`

- stable policy family identity, owner, market/legal entity, lifecycle;
- host/product selection metadata.

`cancellation_policy_versions`

- immutable version identity and definition reference;
- effective interval, typed rule document/schema version, evaluator version;
- applicability, time-zone/cutoff semantics, reason/explanation catalog;
- approval/published/retired timestamps and actor;
- content hash and test-vector suite version.

`policy_disclosure_versions`

- policy version, locale, title/body/structured summary;
- semantic content hash and approved translation provenance.

`booking_policy_acceptances`

- booking revision, policy and disclosure version;
- actor, accepted timestamp, channel/session, consent evidence hash;
- snapshotted official check-in wall time and IANA zone.

Published policy and accepted disclosure rows are append-only.

### Booking revisions and nights

`booking_revisions`

- booking, monotonic revision number, predecessor;
- revision type: original, modification, correction, compensating revision;
- listing, party, dates, quantity, state-relevant terms;
- quote, policy acceptance, financial snapshot/allocation version;
- proposal/decision/correlation, committed actor/time;
- immutable snapshot hashes.

`booking_revision_nights`

- revision, local stay date, listing/unit/resource;
- price/allocation identities and consumption state references.

`bookings.current_revision_id` points to exactly one committed revision. The current denormalized
columns may remain temporarily for compatibility but must match the current revision through one
write path and reconciliation.

### Previews and decisions

`cancellation_previews`

- booking/revision/version, requester, action/reason;
- effective/official-check-in instants, policy/evaluator/config versions;
- input/result hashes, line result JSON or normalized child references;
- expires/status, created timestamp.

`cancellation_decisions`

- booking/revision, decision type, actor/cause/effective instant;
- accepted preview, policy/override/evaluator/tax/allocation versions;
- input/result hashes, status, committed timestamp;
- supersedes/adjusts decision, evidence/approval references;
- unique command/idempotency identity and optimistic version.

`cancellation_decision_lines`

- decision and source booking financial line/allocation;
- line category, affected quantity/service dates;
- original/cancelled/consumed/retained/refund/new-due amounts;
- guest/host/platform/tax/promotion/partner funding effects;
- currency, rounding rule/remainder, explanation code.

`inventory_release_instructions`

- decision, claim, resource/date/quantity, release reason;
- unique `(decision_id, claim_allocation_id)` or equivalent defense;
- applied status/time/version.

### Refund and financial instructions

`refund_instructions`

- cancellation/modification/remedy decision;
- booking financial allocation version;
- beneficiary, amount/currency, source-line allocations;
- funding/tax/ledger/document references;
- reason, policy/override, actor/approval;
- execution deadline, stable idempotency identity, state/version.

Payment owns `refund_executions`, capture selection, provider operations, observations, and verified
movement. Finance owns posting instructions/journals, host recovery, payout holds, and statement
effects. The cancellation database may project their statuses but cannot mark them successful.

`adjustment_instructions`

- decision, type: guest credit, host recovery, platform goodwill, promotion restore, tax adjustment,
  document request, payout hold;
- exact allocation/provenance and downstream idempotency key.

### Modification proposals

`booking_modification_proposals`

- booking/current revision/version, initiator, allowed change type;
- proposed quote/revision draft, policy acceptance requirement;
- inventory delta hold and expiry;
- positive/negative/zero delta allocation;
- payment obligation/authorization or refund-instruction prerequisite;
- guest/host approvals, status/version, expiry;
- canonical request/input/result hashes and idempotency identity.

`booking_modification_deltas`

- proposal, dimension and source/proposed allocation identities;
- before/after/added/removed amounts or quantities;
- currency, rule version, explanation.

### Overrides, evidence, and relocation

`policy_override_programs` and immutable versions define approved exception scope and funding.

`policy_override_decisions` record booking, program/version, evidence references, reviewer/automation,
reason, result, approval, effective/expiry timestamps, and audit correlation.

`relocation_cases`, `relocation_offers`, and `relocation_expenses` record case ownership, guest hard
constraints/consent, budget/funding, candidate offers, linked replacement, receipts, status, SLA,
and outcome.

Evidence metadata stays separate from encrypted/restricted object storage. The ordinary booking
timeline references evidence IDs and redacted summaries, not unrestricted documents.

### Idempotency, events, and timeline

`domain_idempotency_records` scope actor, operation, booking/proposal, key, canonical request hash,
processing state, resource/result, response projection, and expiry/retention.

`booking_timeline_entries` record committed business facts, actor, public/support visibility,
correlation, and source event. They are not a second mutable state machine.

Transactional `outbox_events` and consumer inbox records provide event publication/deduplication.

### Constraints and indexes

Required database defenses include:

- one current committed revision per booking;
- unique revision number per booking and immutable predecessor chain;
- one committed terminal full-cancellation decision per current contract branch;
- unique command/idempotency key within actor/resource/operation scope;
- one inventory release application per decision/claim allocation;
- one downstream instruction per decision/type/allocation identity;
- non-negative amounts/quantities and valid three-letter currency;
- every decision line currency equals its decision currency unless an explicit foreign-exchange
  (FX) sub-decision exists;
- no active modification proposal may own the same delta claim twice;
- optimistic non-negative versions and valid state check constraints;
- foreign keys to immutable policy, disclosure, booking revision, quote, allocation, tax, and approval
  records;
- partial indexes for active previews/proposals, review queues, unapplied releases, pending downstream
  work, and SLA deadlines;
- timeline indexes by booking and occurrence, policy audit indexes by version/effective interval.

Cross-row monetary sums and full allocation balance require locked service validation plus scheduled
reconciliation because ordinary Structured Query Language (SQL) `CHECK` constraints cannot safely
express them. Inventory overlap/quantity constraints and payment refund ceilings remain final
defenses in their domains.

### Migration and deployment

Use forward migrations only:

1. Add policy/version/disclosure, booking revision, decision/instruction, modification, override,
   relocation, idempotency, timeline, and outbox structures without changing existing semantics.
2. Create one original `booking_revision` for each legacy booking from its stored dates, listing
   snapshot, nightly rows, totals, and policy label; mark policy provenance `LEGACY_UNRESOLVED` unless
   an exact historical rule version can be proven.
3. Do not invent a detailed cancellation policy from `FLEXIBLE`/`MODERATE`/`STRICT`. Legacy bookings
   with insufficient evidence use an explicitly approved conservative/manual policy path.
4. Add nullable `current_revision_id` and instruction links, backfill and verify, then enforce
   constraints in a later migration.
5. Dual-write current denormalized booking cancellation fields and target decisions/revisions while
   old readers exist; reconcile every mismatch.
6. Route new bookings only to published executable policy versions after disclosure acceptance is
   stored.
7. Enable preview, then execution by cohort/market, with a kill switch back to governed manual review.
8. Retire legacy write paths only after backfill, replay, reconciliation, operational tooling, and
   rollback-window exit criteria pass.

Never rewrite legacy records to make them appear more precise than the available evidence.

## Service boundaries

Logical modules can initially share one Spring Boot deployment and PostgreSQL database.

### `CancellationPolicyService`

Selects and validates effective policy packages at quote/booking time. Publishes immutable versions
and disclosure references. It does not inspect provider balances or mutate bookings.

### `CancellationEvaluationService`

Builds canonical inputs and deterministically calculates previews/decision allocations. It is a pure
domain evaluator over supplied facts and versioned rules, with no provider calls or direct state
mutation.

### `CancellationCommandService`

Authorizes execution, validates idempotency and preview freshness, locks aggregates, commits the
decision/booking transition/inventory instruction, and writes downstream instructions/outbox facts.

### `BookingModificationService`

Creates proposals, coordinates complete proposed quotes, delta holds, counterparty consent, payment
prerequisites, and atomic booking-revision/claim replacement. It does not calculate tax or call a
payment provider directly.

### `BookingLifecycleService`

Owns allowed booking/stay transitions, current revision pointer, and timeline. It rejects attempts
by downstream consumers to write booking status.

### `InventoryClaimService`

Owns claims and atomic release/transfer by listing-local date/resource. It applies one instruction
once and returns committed facts.

### `QuoteService` and `TaxService`

Produce complete proposed-contract quotes, delta allocations, and versioned tax adjustment
decisions. Cancellation does not copy or reimplement their formulas.

### `PaymentService`

Consumes exact collection/refund instructions, reserves verified monetary ceilings, performs
provider operations, and emits verified outcomes according to
[Payment orchestration](payment-orchestration.md). It cannot change entitlement.

### `LedgerService`, `PayoutService`, and `DocumentService`

Post balanced effects, hold/recover host funds, and issue invoices/credit notes from versioned
instructions. They cannot reopen a booking or silently alter the cancellation allocation.
Detailed journal, payout, statement, reconciliation, and close behavior follows
[Ledger, reconciliation, and host payout](ledger-reconciliation-and-host-payout.md).

### `RelocationService` and `SupportCaseService`

Coordinate people, evidence, offers, budgets, approval, and SLA. They issue approved remedy/override
decisions; they do not bypass booking, inventory, payment, or ledger contracts.

### `NotificationService`

Renders localized communications from committed events and user-safe explanations. Delivery failure
does not roll back cancellation or modification.

## API behavior

Endpoints are illustrative target contracts, not currently implemented routes.

### Guest operations

```text
POST /api/v1/bookings/{bookingId}/cancellation-previews
GET  /api/v1/bookings/{bookingId}/cancellation-previews/{previewId}
POST /api/v1/bookings/{bookingId}/cancellations
POST /api/v1/bookings/{bookingId}/modification-proposals
GET  /api/v1/bookings/{bookingId}/modification-proposals/{proposalId}
POST /api/v1/bookings/{bookingId}/modification-proposals/{proposalId}/accept
POST /api/v1/bookings/{bookingId}/modification-proposals/{proposalId}/cancel
GET  /api/v1/bookings/{bookingId}/changes
```

Preview request:

```json
{
  "action": "FULL_GUEST_CANCELLATION",
  "reasonCode": "PLANS_CHANGED",
  "expectedBookingVersion": 7
}
```

Preview response:

```json
{
  "previewId": "canprev_01...",
  "bookingId": "book_01...",
  "bookingVersion": 7,
  "effectiveAt": "2026-09-06T08:15:27Z",
  "expiresAt": "2026-09-06T08:20:27Z",
  "currency": "VND",
  "guestCashRefundMinor": 3200000,
  "guestCreditRestoredMinor": 200000,
  "retainedPenaltyMinor": 450000,
  "lines": [
    {
      "category": "ACCOMMODATION",
      "refundMinor": 3000000,
      "retainedMinor": 450000,
      "explanationCode": "FLEXIBLE_BEFORE_CUTOFF"
    },
    {
      "category": "CLEANING_FEE",
      "refundMinor": 200000,
      "retainedMinor": 0,
      "explanationCode": "STAY_NOT_STARTED"
    }
  ],
  "inventoryEffect": "RELEASE_ALL_BOOKING_NIGHTS",
  "refundMovementStatus": "NOT_STARTED",
  "policyDisclosureVersion": "cpdv_01...",
  "version": 1
}
```

Cancellation acceptance:

```json
{
  "previewId": "canprev_01...",
  "expectedBookingVersion": 7,
  "expectedPreviewVersion": 1,
  "idempotencyKey": "guest-generated-opaque-key"
}
```

A successful response distinguishes the committed contract result from asynchronous work:

```json
{
  "cancellationId": "candec_01...",
  "bookingStatus": "CANCELLED_BY_GUEST",
  "contractCommittedAt": "2026-09-06T08:16:03Z",
  "guestCashRefundMinor": 3200000,
  "currency": "VND",
  "inventoryStatus": "RELEASED",
  "refundStatus": "PENDING",
  "ledgerStatus": "PENDING",
  "nextRecommendedAction": "WAIT_FOR_REFUND",
  "version": 1
}
```

Clients never submit trusted refund, penalty, tax, host impact, policy version, inventory-release
dates, booking state, or provider result.

### Host operations

```text
POST /api/v1/host/bookings/{bookingId}/cancellation-previews
POST /api/v1/host/bookings/{bookingId}/cancellation-requests
POST /api/v1/host/bookings/{bookingId}/modification-offers
POST /api/v1/host/bookings/{bookingId}/waiver-offers
GET  /api/v1/host/bookings/{bookingId}/change-impact
```

Host cancellation may return `202 Accepted` with `REVIEW_REQUIRED` rather than cancel immediately.
Responses show host-visible economic impact but omit guest payment instrument, internal risk reason,
platform-funded benefit details not contractually disclosable, and unrelated support evidence.

### Support and internal operations

```text
POST /api/v1/support/bookings/{bookingId}/adjustment-previews
POST /api/v1/support/bookings/{bookingId}/adjustment-requests
POST /api/v1/support/cancellation-requests/{id}/approve
POST /api/v1/support/cancellation-requests/{id}/reject
POST /api/v1/support/bookings/{bookingId}/override-evaluations
GET  /api/v1/support/bookings/{bookingId}/financial-change-timeline
POST /internal/refund-instructions/{instructionId}/acknowledgements
POST /internal/inventory-release-instructions/{instructionId}/apply
POST /internal/booking-modifications/{proposalId}/payment-readiness
```

Privileged commands use service/actor identity, explicit capability, reason, evidence, expected
version, stable command ID, canonical hash, and approval reference. They are bounded operations, not
generic row-edit APIs.

### Read consistency and replay

Command responses come from committed transactional state, not an eventually consistent timeline
projection. Repeating the same idempotency key and canonical request returns the original decision
and current downstream status projection. The same key with different material input returns a
conflict.

`GET` may expose lagging payment/ledger/document projections with `asOf`, source version, and
refresh guidance. It never changes policy or creates a provider operation.

### Error semantics

| Condition | Hypertext Transfer Protocol (HTTP) status | Stable code | Retry/user behavior |
| --- | --- | --- | --- |
| Invalid action/reason/date/party | 400 | `INVALID_BOOKING_CHANGE` | Correct request |
| Guest/host lacks resource authority | 403 | `BOOKING_CHANGE_ACCESS_DENIED` | Stop; do not reveal details |
| Opaque booking/preview/proposal unknown | 404 | `BOOKING_CHANGE_NOT_FOUND` | Verify identifier |
| Booking state cannot accept action | 409 | `BOOKING_CHANGE_STATE_CONFLICT` | Refresh booking |
| Booking version changed | 409 | `BOOKING_VERSION_CONFLICT` | Request new preview |
| Preview expired | 409 | `CANCELLATION_PREVIEW_EXPIRED` | Re-preview |
| Preview facts/result changed | 409 | `CANCELLATION_OUTCOME_CHANGED` | Show diff and re-accept |
| Proposal expired | 409 | `MODIFICATION_PROPOSAL_EXPIRED` | Create new proposal |
| Proposed inventory lost | 409 | `MODIFICATION_INVENTORY_UNAVAILABLE` | Keep original; choose alternatives |
| Price/policy changed | 409 | `MODIFICATION_TERMS_CHANGED` | Show full diff and re-accept |
| Additional payment prerequisite absent | 409 | `MODIFICATION_PAYMENT_NOT_READY` | Complete bounded payment flow |
| Accepted workflow awaits payment action/result | 202 | `MODIFICATION_PAYMENT_ACTION_REQUIRED` | Complete action or wait using same proposal |
| Key reused with different input | 409 | `IDEMPOTENCY_KEY_REUSED` | Use new key for new intent |
| Manual approval required | 202 | `BOOKING_CHANGE_REVIEW_REQUIRED` | Wait; show SLA |
| Policy/tax facts unavailable | 503 | `BOOKING_CHANGE_DECISION_UNAVAILABLE` | Retry same intent; never guess |
| Downstream refund pending | 200/202 | `REFUND_PENDING` | Cancellation remains committed; poll/read status |

Return generic `404`/`403` behavior consistently to prevent booking enumeration. Human messages are
localized and safe; clients depend on stable codes and structured fields, not parsed prose.

## Event contracts

Committed facts may include:

- `CancellationPreviewCreated` only when analytics/expiry processing needs it;
- `BookingCancellationRequested` for durable review workflows;
- `BookingCancelledByGuest`;
- `BookingCancelledByHost`;
- `BookingPartiallyCancelled`;
- `CancellationDecisionCommitted`;
- `InventoryReleasedForCancellation`;
- `RefundInstructionCreated`;
- `HostRecoveryInstructionCreated`;
- `PolicyOverrideDecided`;
- `RelocationRequired`, `RelocationOfferAccepted`, `RelocationClosed`;
- `BookingModificationProposed`, `BookingModificationExpired`, `BookingModified`;
- `BookingRevisionCommitted`;
- `NoShowDecided` and `EarlyDepartureCommitted`.

Events describe committed past facts, not commands such as `RefundGuestNow`. Each envelope carries:

```text
event_id, event_type, schema_version
occurred_at
aggregate_type, aggregate_id, aggregate_version
booking_id, current_revision_id where relevant
correlation_id, causation_id
actor_type and non-sensitive actor reference
market/legal-entity reference
minimal decision/instruction identities
trace context
```

Large calculations, evidence, address, messages, and payment details are not copied into general
events. Authorized consumers fetch the immutable decision by ID. An event may include amount/currency
only when necessary for the specific restricted consumer contract.

The producer writes outbox data in the same transaction as the business fact. Relays may publish
duplicates. Consumers store `(consumer, event_id)` or domain instruction identity in an inbox and
commit the inbox marker with their local effect. Schema evolution is backward compatible; replay
must reproduce one effect without consulting today's policy.

Ordering is guaranteed only per aggregate/version where the transport supports it. Consumers that
receive version `n+1` before `n` defer, fetch current authoritative state, or apply a monotonic
reducer. A later payment event cannot undo the booking cancellation decision.

## Concurrency and idempotency

### Command idempotency

Canonical request identity includes action, booking/proposal, expected versions, accepted preview or
offer, and actor scope. Free-form comments excluded from the material hash must not affect the
decision. Store request hash before work and return the original resource/response on replay.

Separate namespaces exist for:

- cancellation preview creation;
- cancellation execution;
- modification proposal and acceptance;
- host/support approval;
- inventory release/transfer;
- refund/collection instruction;
- provider operation;
- ledger posting/reversal;
- payout hold/recovery;
- document/notification delivery;
- event outbox/inbox processing.

Reusing a client key is not sufficient to deduplicate provider, ledger, or inventory effects; each
downstream operation derives its own stable identity from the committed decision/allocation.

### Lock order

Use one documented order to reduce deadlock risk, for example:

1. idempotency record;
2. booking aggregate and current revision pointer;
3. cancellation decision or modification proposal;
4. inventory resources/claims in canonical listing, unit, and local-date order;
5. booking financial allocation/entitlement ceiling rows in canonical ID order;
6. promotion/credit reservation rows where the transaction must update them;
7. downstream instruction and outbox rows.

Do not lock payment-provider operations or make network calls inside this transaction. When a shared
database transaction across modules becomes impractical, retain the same logical fencing/version and
use a saga with compensating outcomes.

### Important races

| Race | Winner/behavior |
| --- | --- |
| Two guest cancellation submissions | First valid committed command wins; same key replays; second incompatible command sees terminal conflict |
| Guest cancellation versus host cancellation | Locked booking version chooses one; preserve losing request/evidence for support, do not rewrite actor attribution |
| Cancellation versus check-in | Policy-defined cutoff and locked lifecycle/version choose one; durable request receipt may govern entitlement |
| Cancellation versus completion/no-show | One lifecycle transition wins; loser re-evaluates under appropriate remedy path |
| Cancellation versus payout batch | Entitlement decision commits; payout service atomically excludes/holds unpaid items or creates recovery if already submitted |
| Cancellation versus payment capture | Payment and booking sagas converge; void/refund instruction respects verified capture and reservations |
| Two partial adjustments | Locked source-allocation ceilings prevent duplicate/excess entitlement |
| Modification versus cancellation | One booking version wins; failed modification releases delta only; cancellation applies to current committed revision |
| Two modifications | At most policy-approved active proposals; only one expected current version can commit |
| Modification versus new demand | Delta hold protects added dates; removed dates remain unavailable until replacement commit |
| Preview crosses policy cutoff | Execution detects changed band and requires renewed consent unless exact approved receipt-time rule preserves it |
| Host waiver versus support override | Explicit precedence and versioning; ambiguous conflict routes to review |

### Database and recovery defenses

Use optimistic booking/proposal versions for friendly conflicts, row locks for cumulative allocations,
unique indexes for once-only effects, and inventory exclusion/quantity constraints as final defenses.
Serializable isolation is not a substitute for explicit domain constraints and may be used only when
measured and tested.

Workers claim bounded batches with leases/fencing or `FOR UPDATE SKIP LOCKED`. A stale worker cannot
apply an instruction after a newer attempt owns its fencing token. Crashes before commit have no
effect; crashes after commit are recovered from durable instructions/outbox.

## Security, privacy, and access control

### Authorization

- Guests access only bookings where they are the authorized contracting guest/delegate.
- Hosts/co-hosts need listing-scoped permission for preview, offer, waiver, or cancellation; each
  permission is separate.
- Host cancellation, same-day/in-stay action, large remedy, payout recovery override, and evidence
  access may require recent step-up authentication.
- Support permissions separate read, preview, request, approve, execute, and view-financial-evidence
  capabilities.
- Finance can inspect/post/reconcile money effects but cannot invent cancellation entitlement.
- Policy administrators can draft/publish rules but cannot approve their own exceptional refund or
  retroactively change accepted policy.
- Maker-checker rules apply above market-specific money/risk thresholds and to employee-related cases.

Authorization is checked again at execution, not inferred from who created a preview.

### Data minimization and protection

Cancellation/change data may contain travel dates, exact property address, identity, health/safety
claims, communications, receipts, and payment metadata. Classify each field, encrypt restricted data,
limit projections, use opaque public IDs, and redact logs/traces/support views.

Store evidence content in restricted object storage with malware scanning, content type/size limits,
short-lived access, audit, retention, legal hold, and deletion policy. General events contain only
references. Do not put free-form reasons, provider payloads, access codes, tax identifiers, or exact
addresses in metric labels.

### Abuse and fraud controls

Monitor repeated preview probing near cutoffs, host coercion to make guests cancel, fabricated
property failures, refund social engineering, collusion, duplicate evidence, coupon restoration
abuse, repeated relocation claims, account takeover, and employee self-dealing.

Rate limits and risk review may delay privileged/manual action but must not silently remove a legal
or contractual right. Preserve the durable request instant and provide a safe human escalation path.

### Policy and secret governance

Policy publishing uses least privilege, review, immutable versioning, change diff, test vectors, and
audit. Provider credentials remain in payment/secret management; this service receives only safe
references and verified status. Manual database edits are prohibited as an operations workflow.

## Observability and operations

### Correctness and business metrics

- cancellations by actor, policy family/version, timing band, market, reason, and stay stage;
- modification proposals, acceptance, expiry, failure, and preserved-original rate by change type;
- preview-to-execution outcome-change rate and amount of change;
- guest refund entitlement versus verified refunded amount and age;
- host impact, platform-funded goodwill/relocation, tax adjustment, promotion restoration, and recovery;
- exact-once inventory release mismatches and post-cancellation sellability;
- duplicate/idempotency replay and hash-conflict rates;
- entitlement allocation imbalance or cumulative-ceiling rejection;
- host cancellation rate, near-arrival rate, relocation SLA/outcome, and appeal reversal;
- support adjustment by reason/agent/approver and threshold utilization;
- legacy/manual-policy volume and unresolved evidence;
- cancellation complaint, contact, chargeback, and repeat-booking outcomes.

Metrics distinguish requested, previewed, committed, instructed, submitted, verified, posted, and
resolved states. Do not report a committed entitlement as a successful refund.

### Technical metrics

- preview/evaluation latency, error rate, policy cache hit/version, and boundary distribution;
- booking/claim/allocation lock wait, deadlock, retry, version conflict, and constraint violation;
- active proposal/hold count and oldest overdue expiry;
- outbox/inbox lag, attempt count, dead-letter volume, and oldest unprocessed instruction;
- refund, ledger, payout, document, notification, and relocation projection freshness;
- reconciliation mismatch count/value and time to resolution;
- policy publication validation failure and disclosure mismatch;
- authorization denial, step-up failure, suspicious action, and evidence-access anomaly.

### Initial service-level objective (SLO) candidates

- 100% of committed cancellations have a policy/evaluator/input/result version and balanced allocation;
- zero known double inventory release, excess entitlement, or duplicate provider refund caused by
  platform replay;
- 99.9% of deterministic cancellation previews return within an agreed regional latency target when
  authoritative dependencies are healthy;
- 99.9% of committed decisions publish an outbox event within the relay target;
- 100% of pending monetary instructions enter scheduled reconciliation by the next relevant provider
  cycle;
- near-arrival host cancellations reach the relocation/support queue within the defined operational SLA.

Final targets require traffic, provider, market, and support-hour evidence.

### Dashboards, alerts, and runbooks

Provide dashboards for cancellation funnel/outcomes, modifications, host-caused incidents,
entitlement-to-refund aging, inventory release correctness, downstream saga state, relocation cases,
policy versions, and manual adjustments.

Alert on:

- committed cancellation without inventory result or refund instruction beyond threshold;
- inventory release exceeding/lagging decision allocation;
- refund/ledger/payout projection mismatch or stale reconciliation;
- modification committed without required payment readiness or revision/claim mismatch;
- policy evaluator imbalance or sudden result distribution shift after publication;
- host cancellation/relocation SLA breach;
- repeated privileged adjustments, approval bypass, or evidence access anomaly;
- oldest pending instruction/queue age, not merely queue length.

Runbooks cover stuck cancellation saga, stale preview disputes, wrong published policy containment,
refund failure/unknown, payout already sent, failed booking replacement, double-booking risk during
claim transfer, tax/document outage, bulk incident program, relocation failure, and audited correction.

Operational tools expose bounded commands such as retry downstream instruction, refresh verified
status, place payout hold, create superseding adjustment request, or escalate case. They never expose
direct state/amount edits.

## Failure behavior

| Failure or inconsistency | Required behavior |
| --- | --- |
| Policy service unavailable before preview | Fail closed with retryable error; do not guess |
| Tax adjustment unavailable where required | Do not commit an unexplained money decision; queue/manual path according to approved market policy |
| Preview created but response lost | Replay by idempotency or create another advisory preview; no contract effect |
| Preview expires/crosses cutoff before execution | Re-evaluate; return material diff and require consent |
| Process crashes before cancellation commit | No booking/inventory/money effect |
| Process crashes after commit before response | Replay committed result from idempotency record |
| Outbox relay fails | Booking remains cancelled; relay retries same event ID |
| Inventory release transaction conflicts | Whole cancellation transaction retries/loses before commit; never commit contract without required atomic release instruction/application |
| Independent maintenance block exists | Remove booking claim only; date remains blocked/unavailable |
| Refund provider times out | Entitlement remains; payment retains reservation, queries same operation, and does not issue blind duplicate |
| Refund fails finally | Booking remains cancelled; expose failure, retry/new operation only under payment policy, reconcile/escalate |
| Refund succeeds but webhook is lost | Payment query/reconciliation records verified success once |
| Ledger consumer fails | Retry same posting identity; entitlement/payment facts remain unchanged |
| Host payout already submitted | Create hold if possible or explicit recovery/negative balance; never reduce guest entitlement silently |
| Notification fails | Retry delivery; do not roll back decision |
| Modification loses added inventory | Fail/expire proposal, release delta resources, preserve original booking |
| Positive-delta payment succeeds but replacement commit fails | Void/refund idempotently; preserve original; open exception if compensation unresolved |
| Replacement commits but later capture fails | Apply explicit dunning/compensating-revision/support policy; never claim money moved |
| Worker crashes during claim transfer | Transaction rollback or fenced recovery yields either original or committed revision, never an unowned partial state |
| Host cancels near arrival and relocation is unavailable | Preserve committed cancellation; execute approved fallback remedy and escalate SLA |
| Override evidence later proves invalid | Do not edit original decision; create fraud/correction case and authorized additive financial recovery |
| Policy bug discovered after decisions | Disable version for new use, identify affected decisions by version/hash, dry-run remediation, issue approved superseding adjustments |
| Duplicate/out-of-order downstream event | Inbox deduplicates; reducers are monotonic and fetch authoritative state when a version gap exists |
| Analytics/search cache is stale | Never use it for execution; read transaction authority |

## Testing and verification

### Deterministic policy tests

- Golden vectors for every policy family/version, line category, and reason branch.
- Instants immediately before, exactly at, and after every cutoff.
- Grace period start/end, last-minute exclusion, official check-in changes, and durable receipt time.
- IANA time zones with daylight-saving gaps/overlaps and zones without daylight saving.
- Variable nightly rates, first-night penalties, percentages, fixed caps, and non-refundable lines.
- Cleaning/platform/host/service fee combinations and tax-inclusive/exclusive adjustments.
- Host/platform-funded discounts, cash/non-cash credits, partial restoration, and deterministic remainder.
- Prior partial adjustments/refunds and cumulative ceilings.
- Disclosure examples match executable results and stable explanation codes.
- Same canonical input/version always produces the same result hash.

### Booking and inventory integration tests

Use real PostgreSQL constraints and transactions for:

- full cancellation releases each booking claim once;
- maintenance/channel blocks survive booking claim release;
- adjacent booking can win only after release commit;
- cancellation versus check-in/no-show/completion has one valid transition;
- two actor cancellations cannot both commit;
- extend, shorten, overlapping move, disjoint move, party/quantity change, and listing replacement;
- failed/expired modification preserves original revision and claims;
- committed replacement has one current revision and exact retained/added/released claims;
- concurrent replacement demand cannot take delta-held nights;
- range-exclusion/claim ordering does not create a self-conflict or protection gap.

### Monetary and property tests

Generate booking lines and actions and assert:

- decision lines balance across guest, host, platform, tax, promotion, partner, and external funding;
- refund/retained/new-due values are non-negative within typed rules;
- the same source allocation is not cancelled/refunded twice;
- restored promotion never exceeds consumed value;
- refunded cash never exceeds verified refundable capture unless an explicit separate funded payment
  path exists;
- applying then exactly reversing a permitted change returns the intended economic position through
  additive entries, not row edits;
- rounding remainders are assigned once and totals reconcile in every supported currency exponent;
- headline total subtraction never substitutes for line-level delta.

### State-machine and idempotency tests

- Exhaustively test permitted and forbidden preview, decision, proposal, booking, instruction, and
  projection transitions.
- Replay identical commands before, during, and after commit; return one resource/effect.
- Reuse a key with different material input; reject deterministically.
- Duplicate/reordered outbox, inbox, payment, ledger, payout, and notification events.
- Worker lease expiry and stale fencing token.
- Process crash at each boundary between durable command, booking transition, claim release, outbox,
  refund submission, verified result, ledger posting, and response.

### Authorization and security tests

- Cross-guest, cross-host, co-host listing scope, suspended actor, and stale session access.
- Separate permissions for preview, offer, cancel, waive, adjust, approve, financial evidence, and
  policy publication.
- Step-up expiry, maker-checker separation, threshold/currency conversion policy, employee-related
  case, and approval replay.
- Enumeration-resistant errors, redacted logs/traces/events, safe support projections, and restricted
  evidence downloads.
- Malicious filenames/content, oversized evidence, malware result, expired signed uniform resource
  locator (URL), and retention.
- Host coercion/refund abuse rate limits without losing durable-right receipt timestamps.

### Provider and contract tests

- Payment consumes exact refund instruction and never recalculates entitlement.
- Full/partial/multi-capture refund, void-before-capture, unknown timeout, delayed webhook, final
  failure, reversal, and reconciliation mismatch.
- Ledger posting and reversal identities, payout pre-submit hold, post-submit recovery/negative balance.
- Tax adjustment/document adapters for cancellation, partial cancellation, replacement, and outage.
- Notification templates render amounts/statuses from committed decision and label pending movement.
- Provider sandbox plus recorded contract fixtures are versioned and scrubbed of sensitive data.

### Recovery and operational tests

- Wrong policy version kill switch and impact query.
- Bulk force-majeure dry run, approval, checkpoint/restart, per-booking deduplication, and budget cap.
- Relocation SLA escalation, candidate unavailable race, guest rejection, reimbursement, and case close.
- Legacy booking with unresolved policy provenance routes to approved fallback/manual review.
- Backup restore followed by outbox replay and reconciliation reproduces decisions and no duplicate
  inventory/money effects.
- Game days for database failover, queue outage, payment outage, tax outage, clock skew, refund
  backlog, and near-arrival mass host cancellation.

## Caching, performance, and scaling

- PostgreSQL booking revisions, policies, decisions, claims, instructions, and financial allocations
  are transactional authority. Caches and analytics are never authoritative for execution.
- Immutable published policy/disclosure versions may be cached by exact ID/content hash. Eviction or
  cache loss must not change results.
- Current booking projections may be cached for reads with version/freshness, but preview/execution
  reloads authoritative facts.
- Bound policy schema complexity, number of bands, booking lines, stay length, proposal count, and
  explanation trace size. Reject pathological requests before expensive work.
- Index active previews/proposals by expiry; cancellation decisions/timeline by booking; work queues
  by state/deadline; policy versions by definition/effective interval; allocations by source line.
- Expiry, outbox, and recovery workers claim small ordered batches with `SKIP LOCKED`, lease/fencing,
  jitter, and backlog-pressure metrics.
- Partition high-volume timeline/outbox/evidence metadata only after measured index/storage/retention
  pressure. Keep booking/decision joins operable and archive without breaking audit.
- Hot listings/dates can cause claim-lock contention during modifications; acquire canonical locks,
  keep transactions short, and fail with a useful conflict rather than extend lock time.
- During dependency degradation, keep existing bookings readable, disable new previews/executions
  whose exact result cannot be proven, allow safe idempotent reads/replays, and continue recovery.
- Do not extract microservices, introduce Kafka/event sourcing, or create a policy domain-specific
  language (DSL) platform until measured throughput, ownership, or isolation justifies the
  consistency cost.

Capacity planning should measure cancellation peaks after disruptions, average/maximum booking
lines, preview-to-execution ratio, policy-evaluation central processing unit (CPU) use, lock wait,
delta-hold occupancy, refund-instruction throughput, relocation concurrency, and evidence storage—not
only average traffic.

## Appropriate use of AI

Useful advisory applications, after deterministic foundations and labeled outcomes exist, include:

- cancellation/no-show probability for host forecasting, reserve planning, and marketplace analysis;
- detecting anomalous refund, waiver, host-cancellation, evidence, collusion, or relocation patterns;
- ranking support/review queues by SLA, risk, vulnerability, and expected complexity under explicit
  rules;
- extracting a draft structured timeline or evidence checklist from messages/documents for human
  verification;
- suggesting relocation candidates after deterministic dates, capacity, accessibility, budget, and
  safety filters;
- explaining an already committed decision in plain language from structured rule traces, with
  template validation and deterministic fallback;
- forecasting refund/contact workload and staffing needs.

Prerequisites include point-in-time-correct events, stable reason labels, leakage controls, human
outcome quality, policy/model versioning, market/language fairness slices, calibration, adversarial
tests, privacy review, drift monitoring, and a kill switch. Human overrides become training labels
only after quality review; they are not automatically ground truth.

Models and LLMs must not:

- choose the authoritative policy version or interpret law;
- decide contractual/exception eligibility, force majeure, no-show, or fault;
- calculate or alter refund, penalty, tax, host impact, platform funding, credit, or payout;
- release inventory, commit a booking revision, submit a provider operation, or post ledger entries;
- fabricate evidence, communication, consent, or reason;
- hide a policy boundary, sensitive risk rationale, or less favorable outcome;
- personalize cancellation rights or penalties by willingness to pay, protected traits, or predicted
  complaint behavior.

Every advisory output stores model/version, features/provenance, confidence, allowed action set,
decision owner, and fallback. Low confidence, model outage, or policy conflict falls back to
deterministic ordering/templates or human review without changing contractual truth.

## Rollout plan

### Phase 0 — Product, legal, finance, and operational decisions

Choose first market/currency/legal entity, contract parties, policy families, disclosure/consent,
official check-in anchor, grace/cutoff semantics, fee/tax/promotion behavior, host liability,
support/approval limits, payout recovery, and refund timing claims. Approve ADRs, policy test vectors,
threat model, provider capabilities, and operational ownership.

Exit: each unresolved launch choice has an accountable owner, approved default, testable examples,
and revisit trigger; no implementation relies on the legacy policy label alone.

### Phase 1 — Executable policy and immutable booking foundation

Add forward schemas for policy/disclosure versions, acceptance, booking revisions, line-level
financial allocation, evaluator traces, idempotency, timeline, and outbox. Backfill legacy bookings
without inventing missing provenance. Implement policy authoring validation and deterministic preview
in shadow/read-only mode.

Exit: approved golden vectors pass; every newly confirmed booking references an executable policy and
disclosure; shadow previews reconcile and legacy ambiguity is measurable.

### Phase 2 — Full pre-stay guest cancellation

Implement preview/acceptance, locked booking transition, exact single-unit claim release, immutable
decision lines, downstream instructions, outbox, user-safe response, replay, and recovery. Start with
one policy family/provider path if needed, while preserving the target contracts.

Exit: concurrent/replayed cancellation releases once, returns one entitlement, survives crash at
every boundary, and exposes refund as separately pending/succeeded/failed.

### Phase 3 — Refund, ledger, payout, tax, and document integration

Connect exact refund instructions to payment reservations/operations; post balanced finance effects;
hold or recover host payout; issue required invoice/credit-note changes; reconcile provider and
ledger outcomes. Add finance/support exception queues and runbooks.

Exit: reference bookings reconcile guest, host, platform, tax, promotion, payment, ledger, payout,
and document results; no failed downstream step changes the committed entitlement.

### Phase 4 — Host cancellation and relocation

Add host-attributed preview/request, step-up, approvals, performance consequences, payout recovery,
guest make-whole, relocation case/budget/offer flow, evidence, SLA, and appeals.

Exit: near-arrival host cancellation reaches support within SLA, guest remedies and funding are
traceable, replacement requires consent/normal booking commit, and host/platform effects reconcile.

### Phase 5 — Partial cancellation, no-show, early departure, and support remedies

Add per-night/quantity entitlement, consumption evidence, future-night release, no-show decision,
host waiver, partial refund, goodwill budget, maker-checker, and correction/appeal.

Exit: partial actions cannot reuse source allocation or over-release inventory; consequential manual
actions are bounded, approved, audited, and recoverable.

### Phase 6 — Extension and shortening

Implement immutable booking revisions, complete proposed quote/delta allocation, net-new delta hold,
positive authorization/negative refund instruction, atomic revision/claim commit, and compensation.

Exit: extend/shorten race tests preserve the original until commit, changes reconcile line by line,
and failure releases only delta resources.

### Phase 7 — General modification and governed exception programs

Add overlapping/disjoint date movement, party/rate-plan/service changes, host offers, linked listing
replacement, then versioned extenuating-circumstance programs and controlled bulk execution. Add
quantity inventory only through its separately approved model.

Exit: all supported change types have explicit consent, policy, inventory, payment, and rollback
semantics; bulk programs are dry-run, approved, budgeted, restartable, and auditable.

## Verification checklist

### Functional and contractual correctness

- [ ] Every new booking references immutable executable policy and accepted disclosure versions.
- [ ] Preview and execution use the same evaluator and disclose exact cutoff semantics.
- [ ] Every committed decision stores authoritative time, actor, facts, versions, hashes, and trace.
- [ ] Line outcomes reconcile guest, host, platform, tax, promotion, partner, and funder effects.
- [ ] Variable nightly prices, fees, credits, taxes, prior adjustments, and rounding reproduce exactly.
- [ ] Guest, host, no-show, early-departure, partial-remedy, and modification intents remain distinct.
- [ ] A committed cancellation releases only its booking claim and does not remove independent blocks.
- [ ] Booking revisions are immutable and one current revision is authoritative.
- [ ] Failed/expired replacement preserves the original contract and releases only delta resources.
- [ ] Positive/zero/negative modification deltas use explicit allocation and money prerequisites.

### Concurrency, retry, and recovery

- [ ] Two competing cancellations/modifications have one deterministic winner.
- [ ] Cancellation versus check-in/completion/payout/capture follows documented race semantics.
- [ ] Idempotent replay returns one decision, release, refund instruction, and posting identity.
- [ ] Same idempotency key with different intent is rejected.
- [ ] Database constraints prevent over-release, duplicate instruction, invalid revision, and cumulative
  source-allocation reuse.
- [ ] No transaction/lock spans a payment, tax-provider, notification, or relocation network call.
- [ ] Crash/failure injection at every saga boundary converges without rewriting the decision.
- [ ] Duplicate/out-of-order events and stale workers cannot regress state or duplicate effects.
- [ ] Backup restore plus replay/reconciliation preserves contractual and monetary invariants.

### Security, privacy, and governance

- [ ] Guest/host/co-host/support/finance/policy roles are resource- and action-scoped.
- [ ] Step-up and maker-checker apply to configured high-risk actions.
- [ ] Policy publication requires validation, approval, immutable version, disclosure, and golden tests.
- [ ] Evidence, exact address, travel dates, messages, payment/tax data, and free text are minimized,
  encrypted/restricted, retained, and redacted appropriately.
- [ ] Host coercion, refund abuse, collusion, employee self-dealing, and account takeover have controls.
- [ ] Overrides, waivers, goodwill, relocation, and corrections identify funding and approval.
- [ ] AI/ML remains advisory and can be disabled without losing deterministic behavior.

### Operations and customer explanation

- [ ] APIs distinguish contract commitment, inventory release, entitlement, provider refund, ledger,
  payout recovery, document, notification, and relocation states.
- [ ] User copy matches executable boundary inclusivity and never promises unverified movement.
- [ ] Support sees a redacted, versioned booking/change/financial timeline and bounded recovery commands.
- [ ] Finance can reconcile source allocation through refund, journal, payout/recovery, tax, and document.
- [ ] Dashboards and alerts cover stuck instructions, mismatches, policy shifts, SLA, and oldest backlog.
- [ ] Wrong-policy, provider outage, payout-sent, replacement-failure, bulk-event, and relocation runbooks
  are exercised.
- [ ] Rollout/kill switch can disable new decisions while preserving reads, replay, and recovery.

### Delivery and compatibility

- [ ] Forward migrations preserve applied booking/payment changesets.
- [ ] Legacy policy labels are not misrepresented as executable historical rules.
- [ ] Backfill, dual-write/read, reconciliation, and retirement gates are measurable.
- [ ] Existing booking/payment fields remain consistent during compatibility period.
- [ ] Documentation, API contracts, policy test vectors, and operational ownership are approved before
  enabling execution by market/cohort.

## Decisions required before implementation

Record consequential choices as Architecture Decision Records (ADRs) with owner, date, context,
alternatives, decision, consequences, rollout, and revisit trigger:

1. First market, currency, legal entity, contracting role, mandatory cancellation rights, and tax/
   invoice/credit-note obligations.
2. Launch policy families, host configurability, non-refundable products, grace period, and exact
   cutoff inequalities.
3. Official check-in wall time, time-zone snapshot/change policy, daylight-saving resolver, and
   whether durable server receipt or commit time controls deadlines.
4. Required policy disclosure, localization, material-change comparison, consent evidence, and
   retention.
5. Refund behavior for accommodation nights, cleaning fee, host/platform/service fees, deposits,
   extras, and consumed services.
6. Promotion, coupon, cash-equivalent credit, goodwill credit, gift-card, loyalty, and benefit
   restoration/cash-refund/funder rules.
7. Tax adjustment authority/provider fallback and conditions that block versus manually route a
   cancellation.
8. Guest refund, host retained earning/liability, platform fee/goodwill, provider fee, and partner/
   protection funding allocation.
9. Inventory-release instant for guest/host cancellation, no-show, early departure, and cases where
   an independent safety/maintenance block must replace the booking claim.
10. Payment authorize/capture/void/refund behavior, partial/multi-capture allocation, refund SLA copy,
    and treatment when no verified capture exists.
11. Host payout hold, reserve, post-payout recovery, negative-balance, write-off, and host statement
    policy.
12. Guest, host, co-host, support, finance, and policy-admin permissions; step-up; monetary thresholds;
    maker-checker; employee-related cases.
13. Host cancellation reason/evidence, performance consequence, guest make-whole, appeal, and
    near-arrival/in-stay support SLA.
14. Relocation scope, equivalence constraints, consent, budget, platform/host funding, reimbursement,
    and fallback when no supply exists.
15. No-show evidence/decision time/dispute window and early-departure notice, release, payout, and
    review-eligibility policy.
16. Modification scope, unchanged-night price guarantee versus full repricing, rate-plan changes,
    counterparty approval, proposal/hold lifetime, and maximum active proposals.
17. Positive-delta payment readiness condition, direct-sale compensation, capture failure after
    commit, and negative-delta refund timing.
18. Listing replacement versus in-place revision boundary, confirmation-code behavior, and which
    history appears to guest/host.
19. Exception/force-majeure program authority, evidence sources, funding/budget, automation boundary,
    appeal, bulk-operation controls, and sunset.
20. Legacy booking fallback when exact accepted policy/disclosure cannot be proven, including whether
    to choose a conservative guest-favorable rule or mandatory manual review.
21. Retention/legal hold/deletion for decisions, consent, evidence, financial records, timelines, and
    model features by market.
22. SLOs, queue ownership, support hours, escalation, reconciliation cadence, and kill-switch owner.
23. Forward migration, backfill, dual-write/read, rollout cohort, compatibility duration, and the
    conditions for retiring legacy policy/status/summary fields.

Recommended default for the first release: one market and currency, one approved policy family,
single-unit confirmed bookings, full guest cancellation before check-in, exact-preview acceptance,
server receipt time with a snapshotted check-in anchor, immediate transactional claim release,
line-level immutable entitlement, asynchronous provider refund, and manual review for ambiguous
legacy/exception cases. Add host cancellation and modification only after payment, ledger, payout,
and support recovery paths are operable.
