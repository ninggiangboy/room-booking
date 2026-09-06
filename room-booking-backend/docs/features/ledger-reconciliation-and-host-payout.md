# Ledger, reconciliation, and host payout

## Purpose

This document defines the target accounting-ledger, settlement, reconciliation, and host-payout
architecture for Room Booking. It expands
[D10 — Accounting ledger, settlement, host payout, and reconciliation](../marketplace-problem-breakdown.md#d10--accounting-ledger-settlement-host-payout-and-reconciliation)
from the marketplace master map into an implementation-oriented design.

The central question is:

> After a booking, collection, cancellation, dispute, or adjustment occurs, how does the platform
> prove who owns every minor unit, determine when host funds may be released, move exactly the
> authorized amount, detect external differences, and preserve an auditable history under retries,
> failures, returns, corrections, and financial close?

This feature is the authoritative detailed design for D10. It narrows and deepens the finance
sections of [dynamic pricing and settlement](dynamic-pricing-and-settlement.md):

- pricing and quote own the immutable commercial line items, tax decisions, and economic allocation
  supplied to finance;
- [payment orchestration](payment-orchestration.md) owns guest-facing provider operations and verified
  collection/refund evidence;
- [cancellation and modification](cancellation-modification-and-refund.md) owns line-level refund,
  host-recovery, and adjustment entitlement;
- [messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md)
  owns safe delivery of payout/stay facts and supplies committed stay/incident evidence used by
  release policy;
- [trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns versioned
  payout-risk decisions and explicit eligibility holds, while finance preserves host liability and
  owns final release and in-flight payout recovery;
- [disputes, damage claims, insurance, and customer support](disputes-damage-claims-and-support.md)
  owns case/claim findings, remedy authorization, explicit funding, provider coordination, and
  appeal, while finance validates and posts every hold, reserve, recovery, expense, and payout effect;
- this feature owns accounting policy application, balanced journals, derived balances, host-fund
  release, payout instructions, finance statements, reconciliation, close, and controlled correction.

External payment, payout, banking, tax, enterprise-accounting, notification, and access systems are
evidence or downstream destinations. None replaces the platform's marketplace subledger or may
silently determine host entitlement; delivery failure or an operations proposal cannot post a
journal or release funds.

## Status and dependencies

This is a target design. There is no Java ledger, settlement, host-payout, statement,
reconciliation, or financial-close implementation in the repository.

Current schema foundations are limited but useful:

- [`bookings` and `booking_nights`](../data-model/004-booking.md) retain booking-level and nightly
  summary amounts, but not complete ownership, recognition, or posting provenance;
- [`payment_attempts`, `refunds`, and `payment_webhook_events`](../data-model/005-payment.md) retain
  coarse provider-facing movement records and webhook deduplication;
- the target pricing design proposes immutable booking financial lines with payer, beneficiary,
  funder, tax, commission, refund, and rule provenance;
- the target payment design proposes collection obligations, operations, verified observations,
  disputes, and provider-side reconciliation records;
- the target cancellation design proposes immutable refund and host-recovery instructions.

Existing migration [`005-payment.sql`](../../src/main/resources/db/changelog/changes/005-payment.sql)
does not contain a chart of accounts, journal, host payable, payout destination, payout instruction,
statement, external settlement import, reconciliation case, accounting period, or adjustment
approval. Existing `SUCCEEDED` payment or refund rows are not ledger entries. All proposed structures
require new forward-only Liquibase migrations; applied migrations must not be edited.

Recommended dependency order:

1. Decide the launch legal entity, contractual role, accounting basis, first currency, and finance
   ownership.
2. Establish immutable booking financial lines and a versioned allocation contract from pricing.
3. Establish verified capture/refund facts and provider account identity from payment.
4. Implement an append-only, balanced journal and deterministic posting-rule registry.
5. Reconcile booking allocation, payment movement, and ledger posting before releasing funds.
6. Implement host entitlement maturity, holds, reserves, statements, and one payout rail.
7. Reconcile payout instructions, provider transfers, provider balance, and bank cash.
8. Add governed adjustments, negative-balance recovery, close, and audit exports.
9. Add multiple currencies, foreign exchange (FX), legal entities, rails, and automation only after
   the single-entity vertical slice closes cleanly.

The recommended minimum viable product (MVP) uses one legal entity, one accounting currency, one
collection currency equal to the accounting currency, one host-payout currency, and one payout
provider. The data model must still carry legal-entity, currency, provider-account, policy-version,
and source dimensions so expansion does not require rewriting history.

## Goals

- Record every material economic effect as one immutable, balanced journal transaction.
- Derive host payable, reserve, available-to-payout, payout-in-transit, tax payable, platform
  revenue, processor cost, and clearing balances without conflating them.
- Reproduce a journal from its immutable source facts and posting-rule version.
- Prevent duplicated, omitted, unbalanced, wrong-currency, or wrong-legal-entity postings.
- Separate contractual entitlement, accounting recognition, release eligibility, payout movement,
  and provider settlement.
- Determine host payout eligibility from explicit timing, compliance, risk, dispute, reserve,
  destination, threshold, and balance policies.
- Ensure a failed, delayed, or returned payout preserves or restores the correct host liability.
- Produce host statements whose lines trace to bookings, adjustments, journals, and provider
  references.
- Reconcile internal obligations and journals to payment providers, payout providers, and bank
  statements at transaction, batch, balance, and period levels.
- Route unexplained differences to owned cases instead of concealing them with edits.
- Support additive reversal, adjustment, recovery, write-off, and accounting-period correction.
- Protect payout destinations and privileged finance actions with strong authorization and audit.
- Give finance and support a complete, redacted, booking-to-cash timeline.
- Define measurable close, aging, mismatch, payout timeliness, and recovery controls.

## Non-goals

- Recalculating price, tax, promotion eligibility, cancellation policy, or guest refund entitlement.
- Determining whether a provider capture, refund, or payout occurred without verified provider or
  bank evidence.
- Implementing a general-purpose enterprise resource planning (ERP) system, general ledger, payroll,
  treasury platform, tax engine, or bank.
- Encoding a country's current accounting, tax, invoice, withholding, or unclaimed-property rules in
  this document.
- Exposing arbitrary debit/credit posting to public clients or ordinary support agents.
- Treating provider balances, dashboards, spreadsheets, analytics tables, caches, or statement
  projections as the source of truth.
- Editing or deleting posted transactions to make a reconciliation difference disappear.
- Automatically paying every positive host balance without release, compliance, risk, dispute, and
  destination checks.
- Netting unrelated legal entities or currencies merely because a provider reports one aggregate.
- Assuming a provider `paid` status proves final bank receipt or that a guest capture is revenue.
- Using an artificial-intelligence (AI) model to post money, release a hold, approve a payout,
  resolve a mismatch, or choose an accounting treatment.
- Introducing microservices, event sourcing, distributed ledgers, blockchain, or a warehouse-led
  accounting architecture without measured need.

## Core principles and invariants

### The journal is the economic source of truth

Booking snapshots prove what was contracted. Payment and payout records prove attempted or verified
external movements. The journal proves the platform's classified economic position.

Balances, statements, finance reports, and payout candidates are derived from posted entries and
explicit release/hold records. They must not be reconstructed from the current booking status or one
provider's dashboard.

### Every posted transaction balances within one book and currency

A journal transaction belongs to one accounting book, one legal entity, and one transaction
currency. It contains at least two positive postings with explicit debit or credit direction.

```text
sum(debit_minor) = sum(credit_minor)
```

Amounts from different currencies or legal entities never balance one another. Multi-currency
activity uses separate balanced transactions or explicit bridge/clearing accounts and stores the FX
relationship between them.

The posting service validates balance before insert and the database commit makes all postings
visible atomically. No partial journal is observable.

### Economic ownership and release timing are separate

An amount can belong economically to a host while remaining unavailable because the stay has not
reached its release instant, a reserve applies, a dispute is open, compliance is incomplete, or the
payout destination is cooling off.

The following are separate states:

```text
host entitlement created
host entitlement earned/recognized under approved policy
host amount payable in the ledger
host amount eligible for release
host amount reserved for a payout
payout submitted externally
payout settled or returned
```

A hold changes availability for payout; it does not silently reclassify host-owned money as platform
revenue.

### Provider evidence is not accounting authority

A verified provider fact may trigger a posting rule, but the provider does not choose the accounts,
owner, recognition timing, tax classification, or host release policy. An unknown provider outcome
remains unresolved; it is not posted as success or failure based on assumption.

Provider records are retained with native references and normalized facts so finance can prove the
relationship between external movement and internal entries.

### Posted history is immutable and corrections are additive

A posted journal transaction is never updated or deleted. A complete reversal references the
original and negates its accounting effect. A correction then posts a new transaction with its own
source, rule version, actor, reason, evidence, and approval.

Source booking lines, payout items, reconciliation matches, statements, and issued documents retain
their historical versions. A current projection may change, but it cannot erase prior truth.

### One business fact produces at most one intended accounting effect

Every posting request has a domain-defined source identity such as:

```text
(accounting_book_id, source_event_id, posting_purpose)
```

The chosen posting-rule version is part of the stored canonical input/hash, not a way to bypass the
unique business effect. The database enforces uniqueness. Replayed events, duplicated webhooks,
worker retries, and client timeouts return the previously posted transaction or a stable conflict if
material input differs.

### Source amounts and posting rules are versioned

Finance does not accept caller-supplied arbitrary accounts. It loads an approved posting-rule
version and immutable source facts, validates legal entity and currency, calculates deterministic
postings, verifies an input hash, and commits the journal.

Changing a chart-of-account mapping affects only future effective events unless finance approves an
explicit backdated correction. Historical replay uses the original rule version.

### Monetary arithmetic is exact

Money uses signed business effects but stored posting amounts are positive integer minor units with
an explicit direction. Currency is an ISO 4217 code with versioned minor-unit metadata. Binary
floating point is forbidden.

Percentages and FX rates use bounded decimal representations. Rounding occurs once at a documented
boundary with a versioned rule and deterministic remainder allocation. No remainder is discarded or
hidden in an unexplained account.

### Payout consumption is exactly once at the platform boundary

Each payable source allocation may be reserved by at most one active payout instruction and consumed
by at most one settled payout effect. Concurrent batch workers use locks, fencing, and unique payout
item constraints.

A provider retry reuses the same payout instruction and provider idempotency key. It does not select
the host's balance again.

### Payout failure preserves or restores the liability

Submission may move an amount from host payable to payout in transit according to approved policy.
A terminal provider failure or returned transfer posts the exact reverse/recovery effect and makes
the amount payable again or places it under an explicit hold/manual-review state. It never converts
the amount to revenue.

### Reconciliation differences are first-class facts

Missing internal records, missing external records, amount/currency mismatches, duplicates, fees,
timing differences, returns, and unknown references are recorded as typed results and cases.

Automated matching may close only cases covered by an approved deterministic rule and materiality
policy. Otherwise a person investigates. Resolution links evidence and any approved correction; it
does not overwrite either side.

### Closed periods remain reproducible

Financial close records source watermarks, reconciliation coverage, unresolved exceptions,
approvals, balance hashes, and export versions. Later-arriving events use the approved late-event
policy: post in an open period with prior-period attribution, reopen through governance, or create a
documented adjustment. They do not silently mutate a closed period.

### Network calls occur outside database transactions

Local transactions create durable payout/reconciliation work and commit before an uncontrolled
provider call. A later transaction applies verified evidence under lock. No ledger, host-balance, or
payout lock remains open while waiting for a provider, bank, tax, document, or notification system.

### Privileged changes are bounded and attributable

Chart-of-account changes, posting-rule publication, destination changes, large adjustments,
write-offs, hold overrides, payout release, reconciliation closure, and period close/reopen require
scoped roles. Configured high-risk actions require step-up authentication and maker-checker approval.

Direct database edits are not an operational workflow.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Accounting book | A coherent set of accounts and policies for one legal entity and accounting purpose |
| Legal entity | The platform entity that contracts, collects, owes, or reports the transaction |
| Chart of accounts (CoA) | Approved, effective-dated account catalog and classification |
| Ledger account | One account in a book, optionally carrying host, tax authority, provider, or other dimensions |
| Journal transaction | Atomic balanced set of postings representing one approved economic event |
| Posting | Positive amount assigned as a debit or credit to one ledger account and dimensions |
| Posting rule | Versioned deterministic mapping from an approved source fact to journal postings |
| Source fact | Immutable domain event or instruction authorized to create an accounting effect |
| Accounting date | Date/period to which a transaction posts under approved finance policy |
| Occurred at | UTC instant at which the underlying domain fact occurred |
| Recognition | Accounting classification of earned revenue, expense, asset, or liability under approved policy |
| Allocation | Immutable split of a contractual line among payer, beneficiary, funder, tax, commission, and other owners |
| Host entitlement | Amount contractually/economically attributable to a host before release controls |
| Host payable | Ledger liability owed to a host after applicable posting rules |
| Release schedule | Versioned rule and timestamp governing when an eligible payable item can be considered for payout |
| Payout hold | Explicit restriction preventing some amount from entering a payout while preserving ownership |
| Reserve | Amount retained under an approved risk/contract policy, with basis, limit, and release condition |
| Available-to-payout | Eligible host-payable amount after maturity, holds, reserves, recovery, threshold, and destination checks |
| Payout destination | Tokenized external bank/wallet destination with ownership, verification, capability, and lifecycle |
| Payout instruction | Durable platform intent to transfer an exact amount/currency to one destination |
| Payout item | Immutable allocation from eligible host-payable sources into one payout instruction |
| Payout in transit | Liability/clearing state for an externally submitted transfer not yet finally settled |
| Statement | Host-facing immutable presentation of opening balance, activity, deductions, holds, and transfer |
| Settlement | External movement and clearing of money among providers, banks, hosts, or other recipients |
| Reconciliation | Controlled comparison of internal facts/balances with independent external evidence |
| Reconciliation run | Bounded execution for one source, account, currency, legal entity, and coverage interval |
| Match | Evidence-backed relationship between one or more internal and external records |
| Exception case | Owned investigation for an unexplained or policy-significant reconciliation result |
| Timing difference | Expected temporary difference caused by known settlement cutoff or delay |
| Materiality | Approved count/amount/risk threshold determining review and escalation |
| Negative host balance | Net host dimension below zero because recoveries exceed current payable value |
| Recovery | Approved collection, reserve application, or future offset of an amount owed by a host |
| Write-off | Approved recognition that a receivable will not be recovered, without deleting its history |
| Financial period | Governed accounting interval with open, closing, closed, or reopened status |
| FX | Foreign exchange between two explicitly identified currencies and rates |
| Provider clearing | Internal account representing money due from or held by an external provider |
| Bank cash | Internal account corresponding to a controlled bank account and statement source |

Dates used for stay/service attribution remain listing-local dates from the booking snapshot. Event,
release, submission, settlement, and deadline fields use UTC instants. Accounting dates and provider
cutoff zones are explicit policy inputs; they are not inferred from the application server zone.

## End-to-end flow

### Booking collection through host payout

```text
immutable booking financial allocation
                |
verified payment capture fact from D09
                |
                v
posting-rule resolution + balance validation
                |
      atomic balanced journal commit
                |
     host-payable source allocations
                |
stay/cancellation/risk/compliance events
                |
 release policy + holds + reserve + recovery
                |
        eligible payout items
                |
   payout reservation and statement draft
                |
      durable provider instruction
                |
  external submit -> evidence/query/webhook
                |
 paid / failed / returned accounting effect
                |
 provider + bank + ledger reconciliation
                |
 statement finalization + finance close
```

### Transaction and saga boundaries

The flow is a saga across short local transactions and external systems:

1. Payment commits verified capture evidence and an outbox event.
2. Finance consumes the event, loads the immutable booking allocation, resolves the approved posting
   rule, and commits one balanced journal plus finance outbox records atomically.
3. Release workers evaluate due host-payable allocations against current authoritative release,
   compliance, risk, dispute, reserve, recovery, and destination facts. They record explicit
   eligibility or hold decisions.
4. A payout-planning transaction locks eligible items, assigns them once to a payout instruction,
   records its exact amount/destination/policy versions, and writes an outbox task.
5. A worker submits the already-fixed instruction outside the transaction using one provider key.
6. Verified provider evidence advances payout movement state. Finance posts submission, settlement,
   failure, or return effects idempotently according to policy.
7. Reconciliation imports independent provider/bank evidence, normalizes it, matches deterministically,
   and opens exceptions for unresolved differences.
8. Statement and close projections consume only committed finance facts and expose their source
   watermark.

No downstream failure rewrites the accepted booking allocation, verified provider evidence, host
entitlement, or prior journal. Recovery proceeds from durable work and additive facts.

## Ownership and source-of-truth matrix

| Fact or decision | Authoritative owner | Finance behavior |
| --- | --- | --- |
| Accepted stay and current revision | Booking | References immutable booking/revision; never edits it |
| Contractual line amount and allocation | Pricing/booking financial snapshot | Validates and maps through versioned posting rules |
| Tax calculation/adjustment | Tax domain | Posts exact approved tax lines; does not recalculate tax |
| Guest capture/refund movement | Payment orchestration | Consumes verified facts and reconciles provider evidence |
| Refund/host recovery entitlement | Cancellation/modification or approved remedy | Consumes exact immutable instruction; does not reinterpret policy |
| Stay completion/check-in evidence | Stay operations/booking | Uses as an input to release policy |
| Host identity and compliance eligibility | Host compliance | Blocks/releases payout eligibility; does not change journal ownership |
| Risk/dispute decision | Risk/dispute owner | Applies explicit scoped hold/reserve/recovery instruction |
| Chart of accounts and posting policy | Finance governance | Publishes versioned effective rules |
| Economic ownership and balances | Ledger | Authoritative posted journal and derived balance |
| Host release/hold/reserve decision | Settlement policy | Records versioned item-level decision |
| Payout platform intent | Payout service | Reserves exact items and creates immutable instruction |
| Payout provider movement | Payout integration | Stores verified observations and native references |
| External provider/bank statement | Reconciliation ingestion | Stores immutable evidence, not accounting classification |
| Match and exception resolution | Reconciliation/finance operations | Records links, evidence, approvals, and corrective command |
| Host statement | Statement service | Immutable projection of ledger/payout sources |
| Financial close | Finance close service | Freezes watermarks/evidence; does not erase late events |

## Accounting books, accounts, and dimensions

### Book boundary

An accounting book is scoped to one legal entity and accounting purpose. The launch may have one
marketplace subledger book, but every journal still stores `accounting_book_id` and `legal_entity_id`.
Cross-entity transactions use due-to/due-from accounts and independently balanced journals approved
by finance; they are not netted in one transaction.

Separate statutory, management, or provider-control views should be mappings/exports from stable
source entries when possible. Creating parallel books with duplicated posting logic requires an
explicit owner, reconciliation, and replay plan.

### Account families

Illustrative families—not approved accounting policy—include:

| Class | Example accounts |
| --- | --- |
| Assets | provider clearing, bank cash, provider receivable, chargeback receivable, host recovery receivable |
| Liabilities | guest funds/contract liability, host payable, host reserve, tax payable, partner payable, travel-credit liability, payout in transit |
| Revenue | host commission, guest service fee, ancillary commission |
| Expense/contra-revenue | promotion funding, processor fee, payout fee, goodwill, chargeback loss, FX gain/loss |

Finance approves normal balance, allowed currency, legal entity, party/provider dimensions, effective
interval, report mapping, and whether manual posting is permitted for each account.

### Required dimensions

Postings carry normalized dimensions needed for proof and reporting, including where applicable:

- legal entity and accounting book;
- account and transaction currency;
- booking, booking revision, booking financial line, and stay/service period;
- guest, host, listing, and market through controlled identifiers;
- payment order/operation, provider account, settlement batch, and bank account;
- payout instruction/item/destination;
- tax authority/type/decision and document;
- promotion/credit funder;
- dispute, cancellation, remedy, reserve, recovery, or reconciliation case;
- posting rule and source event/instruction.

Sensitive identifiers are referenced, not copied into general ledger descriptions. Dimension
cardinality and indexes are deliberate; arbitrary JSON tags cannot become the only queryable source.

### Account lifecycle

Accounts have `DRAFT`, `ACTIVE`, and `RETIRED` lifecycle. An account with postings is never deleted.
Retirement prevents future normal use after an effective instant while preserving replay. Reopening
or remapping requires a new approved version.

## Posting-rule engine

### Rule contract

A posting rule accepts one allowlisted source-fact type and immutable fact snapshot, then emits a
deterministic proposal:

```text
source identity and version
legal entity + accounting book
accounting/occurred dates
currency
postings with direction, positive amount, account, and dimensions
rule version + input hash + output hash
explanation/reconciliation codes
```

The service rejects missing provenance, an inactive account, illegal dimension combination,
unsupported currency, cross-book imbalance, a zero/negative posting amount, or a result whose debit
and credit totals differ.

### Rule precedence and effective time

Rule selection uses an explicit precedence, for example:

1. source-fact type and schema version;
2. legal entity and accounting book;
3. market/product/rate-plan category where accounting policy differs;
4. event/accounting effective instant;
5. approved rule version.

No generic latest-rule lookup is allowed for historical replay. The posting request pins the rule
selected at initial processing, and reprocessing verifies the same source hash.

### Posting lifecycle

Recommended state model:

```text
RECEIVED -> VALIDATED -> POSTED
    |           |
    +---------> REJECTED
    +---------> REVIEW_REQUIRED
```

`VALIDATED` may exist only inside the posting transaction unless a finance approval workflow is
required. A rejected source remains recoverable with a stable error. `POSTED` is terminal; subsequent
change uses reversal/adjustment.

### Illustrative lifecycle entries

Exact accounts and timing require finance/legal approval. The following examples communicate the
mechanics, not production policy.

On verified guest capture, an agency-style design might record:

```text
Dr provider clearing                       captured amount
Cr guest funds / contract liabilities      captured amount allocated by owner
```

At the approved recognition or entitlement event, liabilities may be reclassified into host
payable, platform revenue, tax payable, partner payable, and other approved destinations. Whether
that occurs at capture, check-in, checkout, cancellation, or another milestone is a Phase 0 decision.

On payout submission, where policy uses payout-in-transit:

```text
Dr host payable                            selected payout amount
Cr payout in transit                       selected payout amount
```

On final provider/bank settlement:

```text
Dr payout in transit                       settled amount
Cr provider clearing or bank cash          settled amount
```

Processor/payout fees, refunds, disputes, withholding, reserves, returns, and recoveries create
their own balanced transactions with exact source allocation. They are never forced into a headline
booking-total adjustment.

### Reversal and correction

A reversal:

- references exactly one posted transaction unless finance explicitly supports grouped reversal;
- reproduces every original dimension and swaps debit/credit direction;
- records reason, actor/process, approval, accounting date, and correlation;
- is itself immutable and idempotent;
- cannot exceed or partially reverse an original unless the original posting rule explicitly
  supports allocation-level partial adjustment.

After reversal, a correction posts from the corrected authoritative instruction. A reconciliation
operator chooses a governed resolution action; they do not type arbitrary balancing postings.

## Booking allocation, collection, and recognition

### Allocation handoff

Finance consumes an immutable booking financial allocation, not summary columns alone. Every source
line identifies payer, beneficiary, funder, supplier, currency, tax/commission categories, service
period, refund behavior, and allocation version.

The handoff must prove:

```text
guest total = guest-payable source lines
source line amount = complete ownership/funding allocation for that line
allocation currency = booking financial snapshot currency
```

If a booking lacks sufficient legacy provenance, do not invent host/platform/tax ownership from its
headline totals. Route it through an approved legacy mapping or manual settlement queue.

### Collection versus allocation

A verified capture says how much external value arrived. It can be less than, equal to, or—only as a
reconciliation exception—different from the current collection obligation. Finance posts the exact
verified movement and separately checks it against the immutable obligation/allocation.

An overpayment, underpayment, duplicate collection, orphan transaction, or currency mismatch is not
silently spread across owners. It opens an explicit exception and may require payment compensation.

### Recognition timing

Collection, host entitlement, platform revenue recognition, tax liability, and payout availability
may have different timestamps. Each posting stores both source occurrence and accounting date plus
the recognition-policy version.

The legal and finance owners must define recognition triggers for:

- accommodation and host-provided services;
- platform service fees/commission;
- non-refundable amounts and cancellation penalties;
- taxes collected or withheld;
- promotions, credits, partner amounts, and protection products;
- processor/payout fees;
- disputes, chargebacks, goodwill, and write-offs.

The application must not infer recognition from booking `CONFIRMED` or payment `SUCCEEDED` alone.

### Cancellation, refund, and modification effects

D11 supplies exact line-level entitlement and funding instructions. Finance validates instruction
identity, source-line ceilings, prior posted effects, currency, tax-decision reference, and approval,
then uses the pinned posting rule.

Guest refund movement and economic funding may occur at different times. A durable entitlement can
create or reclassify liabilities before the payment provider completes a refund; provider success
then clears the relevant external movement. Failed refund execution never changes the original
entitlement.

## Host entitlement and balance model

### Entitlement lifecycle

Recommended orthogonal fields rather than one overloaded status:

- `ownership_state`: `PROVISIONAL`, `PAYABLE`, `REVERSED`, or `RECOVERY_DUE`;
- `release_state`: `NOT_SCHEDULED`, `SCHEDULED`, `AVAILABLE`, `HELD`, `RESERVED`, or `CONSUMED`;
- `movement_state`: no payout, payout pending, in transit, paid, failed, or returned;
- `reconciliation_state`: unreconciled, matched, exception, or resolved.

One materialized host-payable allocation references the journal posting that created it. Derived
status may be cached, but the underlying journal, release decisions, holds, reserves, and payout
items remain authoritative.

### Balance equations

For one host, legal entity, accounting book, and currency, a derived closing payable position is:

```text
closing payable
  = opening payable
  + payable credits created
  - payable debits from reversals, recoveries, and payout reservations
  + additive corrections
```

Available-to-payout is not simply a ledger account balance:

```text
available-to-payout
  = matured unconsumed payable allocations
  - active holds
  - required reserves
  - approved offsets/recoveries
```

Every subtraction must reference explicit item allocations. A negative result follows negative
balance policy and does not generate a negative payout instruction.

### Release schedule

Each payable allocation stores or references:

- release-policy version and trigger type;
- scheduled release instant and timezone/cutoff provenance;
- booking, stay/service period, and completion/cancellation state requirements;
- required payment-settlement or fraud-delay condition;
- dispute/chargeback window behavior;
- host compliance/tax readiness requirements;
- reserve rule and maximum amount/duration;
- reevaluation reason and last authoritative facts/version.

Release workers never rely only on elapsed time. They fetch current authoritative holds and
eligibility, use a database time source, and commit item-level decisions with fencing.

## Holds, reserves, withholding, and payout eligibility

### Hold types

Use typed, scoped holds such as:

- booking incident/dispute hold;
- payment outcome or chargeback hold;
- host identity, tax, sanctions, or compliance hold;
- payout-destination cooling-off hold;
- account-takeover or payout-diversion risk hold;
- legal/regulatory hold;
- negative-balance recovery hold;
- finance reconciliation hold;
- manual emergency hold.

Each hold identifies issuer domain, subject, affected scope/maximum amount, reason code, public-safe
explanation, start/expiry/review deadline, policy version, evidence reference, actor, approval, and
release/replacement relationship. Free-form notes are not sufficient policy.

### Reserve model

A reserve is not an unbounded flag. It has an approved basis, amount calculation, currency, cap,
start, maturity/release schedule, contract/policy version, and host disclosure. Reserve movement and
release produce ledger effects if accounting policy requires them.

Risk models may recommend reserve review within approved bounds, but a deterministic policy owns the
amount and action. The platform must monitor disproportionate effects and provide an appeal/review
path where policy requires.

### Withholding

Tax or regulatory withholding comes only from an approved effective-dated tax/compliance decision.
Payout cannot invent a percentage from host country or profile fields. The posting and statement
retain authority/type, base, amount, tax decision version, document/reporting linkage, and whether
the amount reduces payout while preserving gross entitlement disclosure.

### Eligibility decision

A payout item is eligible only when all required predicates pass:

```text
payable source exists and is unconsumed
release instant/trigger satisfied
verified collection/settlement condition satisfied
no applicable active hold
reserve and recovery allocations applied
host and legal-entity capability active
destination verified, compatible, active, and outside cooling-off
currency/rail supported
minimum and maximum payout policy satisfied
statement and reconciliation gates satisfied where required
```

The result records every input version and reason. A failed predicate produces an explainable hold or
deferred state, not disappearance from the host balance.

## Payout destination and account-change protection

The platform should use provider-hosted onboarding/tokenization where possible and store only opaque
destination/account references plus safe display metadata. Raw bank credentials, full account
numbers, private wallet secrets, or provider credentials must not enter application tables, logs,
events, analytics, or general support views.

A destination lifecycle may be:

```text
DRAFT -> PENDING_VERIFICATION -> ACTIVE -> SUSPENDED -> RETIRED
                         |
                         +-> REJECTED
```

Material destination changes require:

- authenticated host authority and resource ownership;
- step-up authentication and recent-session policy;
- provider verification and ownership/capability result;
- notification through an independently trusted channel;
- configurable cooling-off period before first payout;
- invalidation or review of payout drafts referencing the old destination;
- risk evaluation without exposing detection rules;
- immutable old/new token references, actor, request source, approval, and effective time.

An attacker who changes the destination must not be able to suppress the security notification using
the same compromised action. Emergency lock and verified recovery flows are required.

## Payout planning and execution

### Schedule and grouping

Payout policy determines cadence, cutoff, minimum/maximum amount, weekend/holiday handling, host
preferences, rail availability, fee treatment, currency, and legal-entity/provider-account routing.
Calendar interpretation uses an explicit policy timezone and cutoff version.

Eligible items may be grouped only when they share at least:

- host and legal entity;
- accounting book and payout currency;
- compatible active destination and provider account;
- statement/tax/document treatment;
- payout schedule and hold/reserve outcome.

Do not net one host against another or one legal entity/currency against another.

### Payout instruction state machine

```text
DRAFT -> ITEMS_RESERVED -> READY_TO_SUBMIT -> SUBMITTING -> SUBMITTED -> IN_TRANSIT -> PAID
  |            |                 |                 |            |            |
  +------------+-----------------+-----------------+----------> FAILED <------+
  +------------+-----------------> CANCELLED                     |
                                                               RETRYABLE
                                                                  |
                                                             MANUAL_REVIEW

PAID -> RETURNED -> RECOVERY_POSTED -> READY_FOR_RETRY or HELD
```

State names must be mapped to each provider's actual capabilities without discarding native
evidence. `PAID` means the approved verified finality condition was met; provider `submitted` or
`processed` must not be mapped optimistically.

### Planning transaction

In one short transaction:

1. claim eligible payable allocations in canonical order;
2. revalidate host, release, hold, reserve, recovery, destination, currency, and policy versions;
3. create the payout instruction with exact amount and canonical input hash;
4. create unique payout items consuming/reserving each source allocation once;
5. create the draft statement relationship and provider-operation task;
6. commit an outbox event.

If the minimum threshold is not met, leave items unreserved. If policy permits zero-value statements,
create one separately; never create a zero or negative transfer.

### Submission fence

Before the network call, atomically acquire a lease/fencing token and transition the instruction to
`SUBMITTING`. The provider adapter submits the stored amount, currency, destination, legal entity,
and stable idempotency key. It cannot recalculate the payout or add newly eligible items.

A timeout moves to unknown/pending resolution. Query by provider key/reference before retrying. Never
submit a second instruction while the original outcome is unknown.

### Partial provider outcomes

If a provider can split or partially settle one instruction, model child transfer operations and
allocate exact results back to payout items deterministically. The MVP should select a rail that
supports one clearly observable transfer outcome. Do not mark the full payout paid from partial
evidence.

### Failure and retry

Classify failure as:

- safe pre-submission retry;
- provider/network unknown requiring query;
- retryable destination/rail failure after a policy delay;
- host action required;
- compliance/risk hold;
- terminal destination failure;
- manual reconciliation required.

Retry uses the same payout instruction if the provider contract guarantees idempotency for the
original request. A new instruction may be created only after the old attempt is proven terminal and
the reissue relationship prevents duplicate settlement.

### Return after payment

A returned bank transfer is a new verified event. It records return amount/currency, provider/bank
reference, reason, occurred/received times, and original payout relationship. Finance posts the
return/recovery, restores the appropriate host payable or held balance, updates the statement with an
additive return line, and decides whether destination correction/retry is permitted.

The original payout and statement remain visible as historical activity.

## Host statements and financial documents

### Statement content

A statement for one host/legal entity/currency and period or payout includes:

- statement identity, version, status, period, issued time, and opening balance;
- booking and service dates;
- host gross entitlement by source line;
- host-funded discounts and contractual deductions;
- commission/platform fees and tax on those fees where applicable;
- withholding by approved authority/type;
- reserves created, released, or consumed;
- refunds, cancellations, disputes, chargebacks, recoveries, and adjustments;
- payout and FX fees;
- available, held, reserved, in-transit, paid, returned, and closing amounts;
- destination safe display and provider transfer reference where appropriate;
- links to relevant invoice, credit note, or seller-reporting document;
- calculation/version identifiers and user-safe reason codes.

Statement totals derive from exact ledger and payout allocations. They are not recomputed from today's
fee, tax, cancellation, or FX settings.

### Statement lifecycle

Recommended states:

```text
DRAFT -> GENERATED -> ISSUED
  |          |
  +--------> FAILED

ISSUED -> SUPERSEDED_BY_CORRECTION
```

An issued statement is immutable. A correction creates a new version or supplemental statement and
links the original. Rendering failure may retry from the same statement data hash.

### Invoice and tax-document boundary

Tax/document services own legal numbering, issuer/recipient requirements, language, format,
signatures, credit notes, and retention. Finance supplies exact posted amounts and references.
Statement display is not automatically a tax invoice, and a payout receipt does not replace required
seller reporting.

### Document and seller-reporting instruction

Where the operating model requires a platform invoice, supplier/host invoice, self-billed document,
tax invoice, credit note, withholding certificate, or seller report, the responsible tax/document
domain consumes a typed instruction containing:

- legal issuer, supplier, recipient, and marketplace role snapshots;
- source booking/revision, service period, journal lines, and tax-decision version;
- exact line bases, tax, gross/net, withholding, and currency amounts;
- issue/accounting dates, jurisdiction, document type, correction/original relationship;
- numbering/reporting policy version and idempotency identity.

Finance reconciles issued documents and filings to the ledger but does not invent legal content or
numbering. A rendering or delivery failure is recoverable and does not change the journal. A rejected
filing or incorrect issued document creates a governed correction/credit-note flow; it is not edited
in place.

## Reconciliation architecture

### Reconciliation layers

At minimum, support these controls:

1. booking financial snapshot to collection obligation;
2. collection obligation to verified capture/refund totals;
3. verified payment facts to journal transactions;
4. provider transaction/fee/settlement report to provider-clearing journal;
5. provider settlement batch to bank statement;
6. host payable allocations to payout instruction/items;
7. payout provider operation to payout-in-transit and bank/provider clearing;
8. returned payout to recovery/restored payable;
9. tax payable/withholding to tax document, filing, and remittance where in scope;
10. subledger balance/export to downstream general ledger where one exists.

Each layer has its own source identifiers, coverage window, timing tolerance, materiality, and owner.
One green aggregate total cannot hide transaction-level duplicates or cross-host misallocation.

### External evidence ingestion

Reconciliation may consume signed webhooks, authenticated provider APIs, controlled object storage,
bank feeds, or approved statement files. Ingestion records:

- source/provider account and legal entity;
- artifact ID, period, generation/receipt timestamps, cutoff timezone;
- content hash, schema/parser version, sequence/version, and completeness markers;
- secure raw-artifact reference with retention/access class;
- normalized rows with native reference, type, amount, currency, fee, status, and timestamps;
- duplicate/superseding-artifact relationship and processing watermark.

Raw evidence is immutable and access-restricted. CSV formula injection, malformed encodings, oversized
files, decompression bombs, path traversal, and duplicate uploads require explicit defenses.

### Deterministic matching ladder

Apply match rules in approved order:

1. exact provider account plus immutable provider object/operation ID;
2. exact platform idempotency/reference included in provider metadata;
3. exact settlement/payout batch and child reference;
4. approved one-to-many or many-to-one aggregation with equal currency and exact sum;
5. bounded composite match using amount, currency, type, time window, and account;
6. candidate suggestion for human review.

Steps 1–4 may auto-match only when uniqueness and rule confidence are deterministic. Step 5 requires
an explicit ambiguity policy and normally review. Fuzzy or ML matches are suggestions and may not
post, close, or write off money.

### Outcomes

Useful result codes include:

```text
MATCHED_EXACT
MATCHED_AGGREGATE
EXPECTED_TIMING_DIFFERENCE
AMOUNT_MISMATCH
CURRENCY_MISMATCH
STATUS_MISMATCH
DUPLICATE_EXTERNAL
DUPLICATE_INTERNAL
MISSING_INTERNAL
MISSING_EXTERNAL
UNKNOWN_REFERENCE
OUTSIDE_COVERAGE
PARSER_OR_SCHEMA_ERROR
```

Every result records internal/external identities, matched amounts, difference, currency, rule
version, run, evidence, materiality, and next action.

### Balance equations and cutoff

For a provider clearing account and defined coverage interval, a control may test:

```text
expected closing
  = opening external balance
  + verified captures/credits
  - verified refunds
  - provider fees
  - transfers/payouts
  +/- explicit provider adjustments
```

The exact sign and included transaction types depend on the provider contract. Both internal and
external sides must use the same provider account, currency, cutoff timezone, availability versus
pending definition, and report version. Otherwise the system should classify a timing/scope
difference, not force equality.

### Reconciliation case lifecycle

```text
OPEN -> TRIAGED -> ASSIGNED -> INVESTIGATING -> RESOLUTION_PROPOSED -> RESOLVED
  |         |          |             |                    |
  +---------+----------+-------------+------------------> ESCALATED

RESOLVED -> REOPENED when new contradictory evidence arrives
```

A case contains type, amount/currency, severity/materiality, age, owner, due time, linked bookings,
payments, payouts, journals, artifacts, evidence, notes, proposed resolution, approval, and final
effect. Resolution types include confirmed timing, provider correction requested, internal missing
fact recovered, duplicate compensated, approved journal correction, refund/payout action,
write-off, or false positive.

### Reconciliation does not create unexplained plugs

The matching engine never inserts a balancing journal solely to make a report agree. An approved
resolution produces a domain command with a recognized source type, posting rule, accounts,
evidence, materiality approval, and reversal path.

## Multi-currency and FX

### Recommended initial constraint

For MVP:

```text
booking currency = collection currency = ledger transaction currency = payout currency
```

Presentation conversion may be informational but cannot become payment or statement authority.

### Currency roles

Future flows may distinguish listing/public, guest presentation, guest charge, booking accounting,
host entitlement, host payout, provider settlement, bank, and platform functional-reporting
currencies. Each amount identifies its role. Reports must not sum currencies without an explicit
conversion basis.

### FX transaction

A transactional conversion records:

- source and target currency/amount;
- decimal rate, quotation convention, source/provider, timestamp, and expiry;
- markup/spread, fee, owner, and tax treatment;
- currency minor-unit metadata and rounding-rule version;
- remainder and gain/loss allocation;
- original booking/payment/payout references;
- balanced source-currency and target-currency journal relationships.

Refund, chargeback, returned-payout, and host-payout conversion policies must choose original-rate,
current-rate, guaranteed-rate, or provider-result treatment and assign gain/loss. The system never
silently converts at the current display rate.

## Negative host balances, recovery, and write-off

### Causes

A host can owe value after a payout because of cancellation, refund, chargeback, dispute, damage or
guest remedy, withholding correction, duplicate payout, provider return fee, or approved adjustment.
The causal instruction must state which party funds the effect and the maximum recoverable amount.

### Recovery waterfall

An approved, market-specific waterfall may use:

1. unapplied host reserve;
2. current available host payable;
3. future host payable offset within contract/legal bounds;
4. authorized debit or repayment request;
5. protection/insurance/partner recovery;
6. platform loss and approved write-off.

This order is a recommendation, not a universal legal rule. Each step records amount consumed,
remaining balance, notice, consent/contract basis, policy version, and appeal/collection status.

No cross-currency offset occurs without an approved FX transaction. No cross-host or cross-legal-
entity offset occurs by default. Host statements must show gross source, recovery, remaining debt,
and future effect clearly.

### Recovery state

```text
OPEN -> OFFSETTING -> COLLECTION_PENDING -> RECOVERED
  |            |              |
  +------------+------------> DISPUTED
  +-------------------------> WRITTEN_OFF
  +-------------------------> MANUAL_REVIEW
```

Write-off is a new approved accounting event. It does not delete the receivable, causal evidence, or
host history.

## Manual adjustments and approval

### No free-form posting endpoint

Finance tooling exposes an allowlisted adjustment catalog such as correction of duplicated fee,
provider fee true-up, approved goodwill funding, rounding correction, recovery, or write-off. Each
type declares required source, allowed accounts/dimensions, amount bounds, approvals, evidence, tax/
document impact, and posting rule.

### Maker-checker

An adjustment request may move through:

```text
DRAFT -> SUBMITTED -> APPROVED -> POSTED
  |          |           |
  +--------> REJECTED    +-> FAILED_REVIEW_REQUIRED
```

The proposer cannot approve their own above-threshold or self-related adjustment. Approval is bound
to an exact request hash; any material edit invalidates prior approval. Emergency actions use a
break-glass role, short expiry, independent after-the-fact review, and alert.

### Evidence and explanation

Required metadata includes actor, reason taxonomy, affected source allocations, amount/currency,
legal entity, evidence references, approval, accounting date, posting rule, user-facing explanation,
and reversal/correction linkage. Free text is supplemental and redacted for sensitive data.

## Financial periods, close, aging, and audit export

### Period lifecycle

```text
OPEN -> SOFT_CLOSING -> HARD_CLOSED
  ^           |              |
  |           +-> OPEN       +-> REOPENED under approval
  +--------------------------------------+
```

Soft close runs posting completeness, reconciliation, aging, balance, document, and export checks.
Hard close stores approvals and immutable evidence. A reopen is rare, time-bounded, separately
authorized, and followed by re-close with a new version.

### Close controls

For every book/legal entity/currency/period, record:

- event and ingestion watermarks;
- unposted/rejected source-event counts and amounts;
- journal balance and source-to-posting uniqueness checks;
- clearing, payout-in-transit, host payable, tax payable, and recovery aging;
- reconciliation coverage and open case materiality;
- provider/bank artifact completeness;
- statement/document/export completion;
- sign-off actors, timestamps, exceptions, and evidence hashes.

### Late-arriving facts

The approved close policy determines whether a late provider event or correction posts in the current
open period with original-service attribution, triggers an adjusting period, or requires reopening.
The system records both occurred and accounting dates and never backdates silently.

### Audit export

Exports are versioned, reproducible, encrypted in transit/at rest, access-controlled, and tied to a
source watermark/hash. They include stable account, journal, posting, dimension, source, currency,
and reversal relationships—not mutable labels alone. Re-export of the same request is idempotent.

## Conceptual data model

The following structures are proposed target concepts, not implemented or approved migration names.

### Current versus proposed records

Current tables:

- `bookings` and `booking_nights` contain summary and nightly contract values;
- `payment_attempts` records one coarse provider attempt;
- `refunds` records one coarse refund attempt;
- `payment_webhook_events` deduplicates provider events.

Proposed finance structures supplement rather than reinterpret those records. Existing summary
fields can remain compatibility projections until immutable financial lines are authoritative and
reconciled.

### Governance and accounting policy

`accounting_books`

- legal entity, purpose, base/reporting currency policy;
- active period, lifecycle, owner, and version.

`ledger_accounts`

- book, stable account code/name, class, normal balance;
- currency and required/forbidden dimension policy;
- effective interval, active/retired state, report mappings.

`posting_rule_versions`

- source fact/schema type, scope, precedence, effective interval;
- approved deterministic rule/configuration artifact and hash;
- author, approver, publication state, test-vector version.

`accounting_periods`

- book, period start/end and timezone;
- state, watermarks, close/reopen versions, approvals, evidence hash.

### Journal

`ledger_transactions`

- book/legal entity, currency, occurred/accounting timestamps;
- source type/ID/version, posting purpose, source input hash;
- posting-rule version, status, description/reason code;
- reversal-of/supersession relationship;
- actor/process, approval, correlation/causation, created/posted timestamps.

`ledger_postings`

- transaction, sequence, account, debit/credit direction, positive amount;
- host/guest/listing/booking/revision/financial-line dimensions;
- provider account/payment/refund/payout/tax/promotion/dispute dimensions;
- service period and reconciliation category.

Important defenses include:

- unique `(book_id, source_type, source_id, posting_purpose)` with the pinned rule version and source
  hash stored on the transaction;
- unique posting sequence within a transaction;
- positive amount and valid currency/account constraints;
- one book/legal entity/currency per transaction;
- immutable rows after `POSTED`;
- balance validation in the posting transaction, plus deferred database enforcement or a controlled
  posting procedure if needed;
- reversal uniqueness/ceiling according to policy.

### Host payable, release, hold, and reserve

`host_payable_allocations`

- source ledger posting/allocation and host;
- original, remaining, reserved, consumed, and recovered amounts;
- release-policy/version, release instant, state, optimistic version.

`host_fund_release_decisions`

- payable allocation, evaluated time, input versions/hash;
- result/reason codes, next review, policy version, actor/process.

`payout_holds`

- issuer domain, host/booking/allocation/payout scope;
- maximum amount/currency, reason/public reason, policy/evidence;
- active interval, review/expiry, release/replacement, actor/approval.

`host_reserves` and `host_reserve_allocations`

- reserve policy/basis, target/cap, currency, schedule/state;
- source/consumed/released allocations and ledger relationships.

Cross-row amount ceilings are checked under locked allocation rows and audited by reconciliation.

### Destinations and payouts

`payout_destinations`

- host, provider account, opaque token/reference, rail/type;
- currency/country/capability and safe display metadata;
- verification, lifecycle, cooling-off, risk, consent, version;
- created/changed/retired actor and audit references.

`payout_instructions`

- host, book/legal entity, destination/provider account;
- amount/currency, schedule/cutoff and policy versions;
- state, request/input hash, idempotency/provider keys;
- provider reference, submitted/settled/failed/returned times;
- failure/return classification, retry/reissue relationship, version.

`payout_items`

- instruction, payable allocation/source posting;
- selected amount/currency, reserve/recovery relation;
- unique active/settled consumption identity.

`payout_operations` and `payout_observations`

- submit/query/cancel/retry operation, lease/fencing, provider request/response classification;
- immutable verified webhook/API/file/bank evidence and native times/references.

`host_statements` and `host_statement_lines`

- host/legal entity/currency, period/payout, opening/closing balances;
- status/version/data hash, issued/superseded references;
- typed source amounts linked to ledger postings/payout items/documents.

### Reconciliation

`external_financial_artifacts`

- source/provider/bank account, legal entity, coverage period;
- immutable object reference/hash, schema/parser version, sequence/completeness, retention class.

`external_financial_records`

- artifact, native row/object identity and type;
- amount/currency/fee/status/timestamps/references;
- normalized safe metadata and row hash.

`reconciliation_runs`

- control type, source/accounts/currency/coverage;
- rule/materiality version, input watermarks/hashes;
- counts/amounts by result, state, started/completed times.

`reconciliation_matches`

- run/rule, internal and external record allocations;
- match type/confidence class, matched/difference amounts, currency;
- immutable match/supersession relationship.

`reconciliation_cases`

- type/severity/materiality, subject identities, amount/currency;
- state/owner/SLA, evidence, proposed/final resolution;
- approval, correction/action links, reopen history, version.

### Adjustments, recovery, idempotency, and audit

`finance_adjustment_requests` and `finance_adjustment_approvals`

- allowlisted adjustment type, exact source/amount/dimensions;
- request hash, evidence, proposer/approver, thresholds, status;
- resulting journal/reversal/document/payout relationships.

`host_recoveries` and `host_recovery_allocations`

- causal instruction, original/remaining amount, currency;
- waterfall policy, reserve/offset/collection/write-off allocations;
- notices, dispute/appeal, state, ledger links.

`finance_idempotency_records`, `finance_outbox_events`, and `finance_inbox_events`

- scoped operation identity, canonical request hash, state/result;
- event schema, aggregate version, publication/consumption attempts.

`finance_audit_actions`

- actor/session/authentication strength, action/resource;
- old/new references or hashes, reason/evidence, approval, time, correlation.

### Indexes and immutability

Plan indexes around bounded operational queries:

- unposted/rejected source work by next attempt and age;
- host payable by host/currency/release state/release instant;
- active holds by scope and expiry/review time;
- eligible payout items by provider/legal entity/currency/schedule;
- payout operations by state/lease/next retry/provider reference;
- statement timeline by host/period;
- external records by provider account/native reference/currency/time;
- reconciliation results/cases by state/severity/SLA/amount;
- journals by source, booking, host, payout, provider, accounting period, and account.

Use database permissions/triggers or controlled repository boundaries to prevent ordinary updates to
posted/issued/closed records. Prove immutability with integration tests and periodic controls.

### Migration and backfill

Recommended forward deployment:

1. add accounting policy, journal, idempotency, outbox/inbox, and audit structures;
2. add immutable booking financial lines before generating production postings;
3. dual-validate new allocation totals against existing booking summaries;
4. backfill only source facts whose ownership/legal entity/currency/tax provenance is provable;
5. classify ambiguous legacy bookings and payments into a bounded legacy/manual queue;
6. run journal posting in shadow mode and compare expected balances without enabling payouts;
7. ingest provider evidence and prove payment-to-ledger reconciliation;
8. add host payable/release/hold/reserve/destination/payout/statement structures;
9. run payout shadow statements, then enable a controlled host cohort;
10. retire legacy finance reads only after balance, replay, close, and rollback gates pass.

Do not derive historical commission, tax ownership, recognition date, or host entitlement solely from
`service_fee_minor`, `tax_minor`, a coarse policy label, or a provider success state. Record unknown
provenance explicitly.

## Service boundaries

These are logical modules and may initially share the Spring Boot deployment and PostgreSQL database.

### `AccountingPolicyService`

Publishes and resolves accounting books, accounts, posting rules, recognition/release policy, periods,
and test vectors. It requires governed approval and never posts transactions directly.

### `LedgerPostingService`

Accepts allowlisted immutable source identities, resolves the pinned rule, produces one balanced
transaction, enforces idempotency, and commits postings/outbox atomically. Callers cannot supply
arbitrary debit/credit pairs.

### `LedgerQueryService`

Returns authorized journal, balance, source-trace, and aging projections with source watermark. It
does not mutate entries or become the payout selector.

### `HostEntitlementService`

Maps approved booking/cancellation/dispute allocations to host-payable sources and exposes exact
remaining allocations. It does not determine booking price or provider movement.

### `HostFundReleaseService`

Evaluates maturity, compliance, risk/dispute holds, reserves, recoveries, and destination readiness
under versioned policy. It records item-level release decisions and reasons.

### `PayoutDestinationService`

Coordinates provider-hosted onboarding/token references, ownership authorization, verification,
cooling-off, activation, suspension, retirement, and security notification.

### `PayoutPlanningService`

Selects and reserves eligible payable allocations in one transaction, creates the exact payout
instruction/items and statement relationship, and emits durable work.

### `PayoutExecutionService` and provider adapters

Submit/query/cancel one immutable payout instruction outside local transactions, verify provider
evidence, and reduce provider-specific status into the internal movement state. They do not select
amounts or release held funds.

### `StatementService`

Builds immutable, localized host-facing statements from ledger and payout allocations and coordinates
rendering/delivery. It does not recalculate contractual amounts.

### `ReconciliationIngestionService`

Authenticates sources, stores immutable artifacts, normalizes rows through versioned parsers, detects
duplicates/completeness, and publishes ready evidence.

### `ReconciliationService`

Runs deterministic controls/matching, records results, creates cases, and verifies resolution. It may
request a governed corrective command but cannot write arbitrary balancing entries.

### `FinanceAdjustmentService`

Validates allowlisted adjustment/reversal/recovery/write-off requests, thresholds, evidence, and
maker-checker approval, then submits an exact source fact to the posting service.

### `FinancialCloseService`

Coordinates watermarks, controls, approvals, close/reopen, aging snapshots, and audit exports. It
does not block late fact ingestion; it applies the approved period policy.

### Neighbor contracts

- Pricing/booking snapshot supplies immutable allocation; finance never re-prices.
- Payment supplies verified movement; finance never infers provider success.
- Cancellation/support/dispute supplies exact entitlement/funding/hold/recovery instructions;
  finance never re-adjudicates policy.
- Host compliance and risk supply signed/versioned capability decisions; finance records effects and
  reasons without copying their internal models.
- Tax/document services supply tax decisions and legal documents; finance supplies posted amounts.
- Notifications render committed user-safe facts; delivery failure never rolls back money state.

## API behavior

The examples below are illustrative target operations, not implemented routes.

### Host operations

```text
GET  /api/v1/host/earnings/summary
GET  /api/v1/host/earnings/transactions
GET  /api/v1/host/payouts
GET  /api/v1/host/payouts/{payoutId}
GET  /api/v1/host/statements
GET  /api/v1/host/statements/{statementId}
GET  /api/v1/host/payout-destinations
POST /api/v1/host/payout-destinations
POST /api/v1/host/payout-destinations/{id}/verification
PUT  /api/v1/host/payout-destinations/{id}/status
```

Host balance responses distinguish:

```json
{
  "currency": "VND",
  "payableMinor": 5200000,
  "availableMinor": 4100000,
  "heldMinor": 600000,
  "reservedMinor": 500000,
  "inTransitMinor": 0,
  "negativeBalanceMinor": 0,
  "asOf": "2026-09-06T10:00:00Z",
  "sourceWatermark": "finwm_01..."
}
```

The client never sends authoritative balances, payout amounts, destination ownership, or release
state. Creating/changing a destination uses provider-issued tokens and server-side host authorization.

### Finance operations

```text
GET  /api/v1/finance/journals/{journalId}
GET  /api/v1/finance/sources/{sourceType}/{sourceId}/trace
GET  /api/v1/finance/balances
GET  /api/v1/finance/payouts
POST /api/v1/finance/payout-runs
POST /api/v1/finance/payouts/{id}/retry
POST /api/v1/finance/payouts/{id}/hold
GET  /api/v1/finance/reconciliation-runs
POST /api/v1/finance/reconciliation-runs
GET  /api/v1/finance/reconciliation-cases
POST /api/v1/finance/reconciliation-cases/{id}/resolution-proposals
POST /api/v1/finance/adjustments
POST /api/v1/finance/adjustments/{id}/approve
POST /api/v1/finance/periods/{id}/close
POST /api/v1/finance/periods/{id}/reopen
POST /api/v1/finance/audit-exports
```

These are task-oriented APIs, not unrestricted CRUD. Every mutation requires a scoped idempotency
key, expected version, reason, and—where configured—step-up/approval. Payout runs accept a bounded
scope such as legal entity, provider account, currency, and schedule; they never accept a caller-
calculated total.

### Internal operations

```text
POST /internal/v1/finance/posting-requests
POST /internal/v1/finance/host-release-evaluations
POST /internal/v1/finance/payout-hold-instructions
POST /internal/v1/finance/host-recovery-instructions
POST /internal/v1/finance/provider-evidence
```

Internal authorization uses workload identity and explicit producer allowlists. Requests identify an
authoritative immutable source and expected version/hash; they do not carry arbitrary account pairs.

### Read consistency and disclosure

Transactional journal/payout detail reads come from PostgreSQL or an equally authoritative replica
with declared freshness. Host dashboard summaries may be cached but expose `asOf`/watermark and offer
a source-detail path. Do not tell a host funds were paid when only submitted; use user-safe states
such as scheduled, held, processing, sent, received/settled, failed, or returned according to the
verified evidence available.

Hosts see their own gross-to-net lines and safe hold reasons. They do not see internal fraud rules,
other hosts, provider credentials, full bank data, internal account codes, or confidential platform
margin unless contractually required.

### Error semantics

| Condition | HTTP | Stable code | Retry/action |
| --- | --- | --- | --- |
| Unknown or unauthorized finance resource | 404/403 | `FINANCE_RESOURCE_NOT_FOUND` / `FINANCE_ACCESS_DENIED` | Stop; avoid existence leak |
| Same key, different material request | 409 | `IDEMPOTENCY_KEY_REUSED` | Use a new key only for new intent |
| Stale expected version | 409 | `FINANCE_VERSION_CONFLICT` | Reload authoritative state |
| Source already posted | 200/409 | `JOURNAL_ALREADY_POSTED` | Return original if identical; investigate mismatch |
| Source incomplete or unverifiable | 422 | `FINANCE_SOURCE_INCOMPLETE` | Repair source/route to review |
| Posting rule unavailable | 503/422 | `POSTING_RULE_UNAVAILABLE` | Do not guess; retry or review |
| Journal would not balance | 500/422 | `JOURNAL_IMBALANCED` | Quarantine; finance/engineering review |
| Currency/legal entity mismatch | 409 | `FINANCE_SCOPE_MISMATCH` | Correct authoritative source |
| Accounting period closed | 409 | `ACCOUNTING_PERIOD_CLOSED` | Apply approved late-event process |
| Host funds not yet eligible | 409 | `PAYOUT_FUNDS_NOT_AVAILABLE` | Show reasons/next review if safe |
| Payout destination cooling off | 409 | `PAYOUT_DESTINATION_COOLING_OFF` | Wait or use governed recovery |
| Payout held | 409 | `PAYOUT_HELD` | Follow safe host/support action |
| Payout already processing/unknown | 202/409 | `PAYOUT_OUTCOME_PENDING` | Query/reconcile; do not resubmit |
| Payout provider unavailable pre-submit | 503 | `PAYOUT_PROVIDER_UNAVAILABLE` | Retry same instruction safely |
| Payout return requires host action | 422 | `PAYOUT_DESTINATION_ACTION_REQUIRED` | Update/verify destination |
| Reconciliation artifact duplicate | 200/409 | `RECONCILIATION_ARTIFACT_DUPLICATE` | Return original ingestion result |
| Reconciliation coverage incomplete | 409 | `RECONCILIATION_COVERAGE_INCOMPLETE` | Obtain missing evidence |
| Case resolution requires approval | 409 | `FINANCE_APPROVAL_REQUIRED` | Submit maker-checker request |
| Adjustment exceeds authority | 403/422 | `FINANCE_ADJUSTMENT_LIMIT_EXCEEDED` | Escalate to authorized tier |

Provider-native errors are mapped and redacted. HTTP success for command acceptance does not imply
external settlement; responses include the durable internal state and polling/resource link.

## Event contracts

Events are committed past-tense facts. Producers write an outbox record in the same transaction as
the business state. Delivery is at least once; consumers use an inbox and domain-specific uniqueness.

Representative events:

```text
AccountingPolicyPublished
JournalPostingRejected
JournalTransactionPosted
JournalTransactionReversed
HostEntitlementRecorded
HostFundsReleaseScheduled
HostFundsAvailable
PayoutHoldPlaced
PayoutHoldReleased
HostReserveCreated
HostReserveReleased
PayoutDestinationChanged
PayoutDestinationActivated
HostPayoutPlanned
HostPayoutSubmitted
HostPayoutOutcomeBecameUnknown
HostPayoutPaid
HostPayoutFailed
HostPayoutReturned
HostRecoveryOpened
HostRecoveryCompleted
HostStatementIssued
ExternalFinancialArtifactIngested
ReconciliationRunCompleted
ReconciliationMismatchDetected
ReconciliationCaseResolved
FinanceAdjustmentPosted
AccountingPeriodClosed
```

Minimum event envelope:

```text
event_id, event_type, schema_version, occurred_at
aggregate_type, aggregate_id, aggregate_version
accounting_book_id, legal_entity_id where required
correlation_id, causation_id, trace_context
actor/process identity where appropriate
minimal source/policy/version identities
```

`JournalTransactionPosted` should identify the journal, source identity, book/legal entity, currency,
posting rule, accounting date, and aggregate version. Do not put the entire journal, tax identifier,
bank data, evidence artifact, internal notes, or host personal data in a general event.

`HostPayoutPaid` should identify the payout instruction, host through a controlled identifier,
amount/currency, provider account/reference, verified observation, settled instant, statement, and
version. Consumers must not infer entitlement or account balance from this event alone.

Ordering is guaranteed only where explicitly designed per aggregate. A consumer receiving version
`n+1` before `n` defers, fetches current authoritative state, or applies a monotonic reducer. Replay
uses original policy/source versions and produces no duplicate journal, reservation, transfer,
statement, or case.

## Concurrency and idempotency

### Canonical lock order

Within finance transactions, use a documented order such as:

```text
idempotency/inbox source
  -> accounting book/period when required
  -> source allocation or host payable rows ordered by ID
  -> hold/reserve/recovery rows ordered by ID
  -> payout instruction/items
  -> journal transaction/postings
  -> outbox
```

Do not acquire booking/inventory locks while holding payout locks. Cross-domain coordination uses
immutable instructions, expected versions, events, and compensation.

### Journal races

- Two consumers of one source event contend on the unique posting identity; one posts and the other
  returns the original.
- A same-key request with a different source hash is quarantined as corruption/conflict.
- A reversal racing another reversal locks the original/allocation and applies the approved ceiling.
- Period close races posting by locking or fencing the period watermark; the loser follows late-event
  policy.

### Release and payout races

| Race | Required outcome |
| --- | --- |
| Release worker versus new hold | Payout reservation revalidates under lock; one version wins, or payout is held/recovered explicitly |
| Two payout planners | Unique active consumption plus row locks let one reserve each payable allocation |
| Destination change versus payout plan | Expected destination version/cooling-off fence decides; stale draft cannot submit |
| Payout submission versus cancellation/refund | Entitlement remains committed; unpaid items are excluded/held, submitted items create recovery if required |
| Retry worker versus webhook/query | Payout operation version and provider idempotency converge to one movement |
| Payout paid versus provider return | Paid remains historical; return posts additive recovery/restored payable |
| Recovery offset versus new payout | Locked host payable/recovery allocations consume each minor unit once |
| Statement issue versus late adjustment | Issued version remains immutable; supplemental/corrected statement follows |

### Payout idempotency scopes

Separate keys exist for:

- payout run request;
- payable-item reservation;
- payout instruction;
- provider submit/query/cancel operation;
- provider webhook/file observation;
- journal submission/settlement/return posting;
- statement generation/render/delivery;
- reconciliation artifact/run/match/case resolution;
- finance adjustment approval/posting.

Reusing one string across all scopes is insufficient. Each key binds actor/resource, canonical input
hash, created result, and replay response.

### Worker fencing

Workers claim bounded batches with `FOR UPDATE SKIP LOCKED` or leases containing owner, expiry, and
monotonic fencing token. Applying an outcome requires the current fence/version. A crashed worker's
lease expires; its late result cannot overwrite a newer attempt.

## Security, privacy, and access control

### Authorization

- Hosts may view only their own balances, destinations, payouts, and statements.
- Co-host access requires an explicit delegated finance permission and may exclude destination
  changes or full statements.
- Support sees a redacted timeline and bounded domain actions, not arbitrary journal/posting tools.
- Finance readers, payout operators, reconciliation analysts, adjustment makers/checkers, accounting
  policy administrators, auditors, and security responders are distinct roles.
- Service identities are allowlisted by source fact and operation.
- Cross-legal-entity access is denied unless the role explicitly grants it.

Resource authorization is checked server-side on every read and mutation. Object identifiers are not
authorization proof.

### Sensitive data

Protect and minimize:

- payout destination and safe bank/wallet metadata;
- host legal/tax identity;
- provider and bank account references;
- guest billing/payment associations;
- exact booking/address/service facts where not required;
- reconciliation artifacts, evidence, adjustment notes, and dispute data;
- internal margin, risk reason, account mapping, and control thresholds.

Use provider tokens, encryption, scoped keys, strict storage buckets, malware/content validation,
field redaction, access logging, and retention/deletion policy. Logs and events contain opaque
references and safe codes, never credentials or unbounded raw files.

### High-risk operations

Destination changes, manual payout release, large adjustment/refund/recovery, write-off, account/rule
publication, period reopen, evidence export, and kill-switch changes require step-up and/or
maker-checker according to policy. Detect self-dealing, employee-related accounts, unusual bulk
actions, role escalation, and compromised sessions.

### Abuse and fraud

Controls should address payout diversion, account takeover, synthetic hosts, collusion, self-booking,
money laundering indicators, duplicate destinations, refund/payout cycling, forged evidence,
statement scraping, adjustment abuse, and reconciliation-case suppression. Risk signals inform
explicit decisions; opaque model scores do not directly seize or transfer funds.

### Retention and subject requests

Financial/audit retention may limit deletion of business records. Privacy workflows should minimize
or pseudonymize personal dimensions where legally permitted while preserving mandatory financial
evidence and referential integrity. Retention rules are market/legal-entity/data-category specific
and must be approved before launch.

## Observability and operations

### Correctness and business metrics

- unposted/rejected source event count, amount, and oldest age;
- journal imbalance attempts and duplicate-prevention count;
- source-to-journal completeness by fact type/version;
- host payable, available, held, reserved, in-transit, negative, and aging by currency;
- release-policy delay and hold/reserve rate/reason/age;
- payout scheduled-to-submit and submit-to-settle latency;
- payout success, unknown, failure, retry, and return rate/value;
- destination-change cooling-off and security-lock rate;
- statement generation/correction/dispute rate;
- provider clearing and bank cash unmatched balance;
- reconciliation coverage, exact-match rate, mismatch value, case age, and reopen rate;
- recovery success, future-offset age, disputed recovery, and write-off value;
- close readiness, close duration, late-event count, and unresolved material exceptions.

Metrics label legal entity, provider account, currency, market, rail, source type, and policy/rule
version where cardinality is controlled. They never aggregate currencies into one unlabeled amount.

### Technical metrics

- posting latency, transaction retries, lock waits, deadlocks, and constraint conflicts;
- payout/release worker queue depth, lease expiration, attempts, and oldest item;
- provider request latency/error/timeout and query-recovery rate;
- webhook/artifact ingestion lag, parser failures, duplicate artifacts, and missing sequences;
- reconciliation run duration, rows, match-rule distribution, and bounded-query violations;
- projection watermark lag, statement rendering/delivery failures, and export latency;
- outbox/inbox age, retry count, dead-letter/review queue, and replay throughput.

### Initial service-level objective candidates

Before launch, define measurable objectives such as:

- 100% of posted transactions balance and have one approved source/rule identity;
- zero duplicate payable-item consumption or duplicate provider payout caused by platform retry;
- verified capture/refund facts post or enter an owned exception within a bounded time;
- eligible scheduled payouts submit within the promised host window, excluding disclosed holds;
- unknown payout outcomes are queried/escalated within a shorter bound than blind retry;
- provider/bank artifacts reach declared reconciliation coverage by the finance cutoff;
- material cases breach neither ownership nor resolution SLA;
- statement totals reproduce from source entries and are available by promised issuance time.

Exact targets depend on provider/report cadence, host promise, market, support coverage, and finance
close requirements.

### Dashboards and alerts

Dashboards should provide:

- booking/payment-to-journal completeness;
- ledger balance/control health by book/currency;
- host-fund release and hold/reserve aging;
- payout funnel and unknown/failure/return queue;
- provider clearing/bank reconciliation coverage;
- exception materiality/age/owner;
- negative balances/recovery and statement corrections;
- accounting-period readiness and late facts.

Page on call for potential duplicate payout, journal integrity failure, unexplained material balance
movement, destination-security anomaly, widespread provider unknown state, missing critical report,
or payout return spike. Route ordinary host action and non-material timing differences to queues.

### Manual operations and runbooks

Runbooks must cover:

- verified payment fact not posted;
- imbalanced/rejected posting-rule output;
- payout provider outage or timeout;
- duplicate/partial payout suspicion;
- destination compromise and emergency freeze;
- payout failure/return and safe reissue;
- cancellation/refund after payout and host recovery;
- missing/duplicate/corrected provider or bank report;
- reconciliation mismatch and approved correction;
- negative host balance/dispute/write-off;
- statement error/correction;
- close with late event, provider outage, or material open exception;
- backup restoration followed by replay and full balance reconciliation.

Runbook actions use product commands with idempotency and audit, never direct database mutation.

### Kill switches

Provide independently auditable controls to:

- stop new payout planning while preserving host payable;
- stop submission for one provider account, rail, legal entity, market, or currency;
- quarantine one posting rule/source type;
- pause release evaluation or apply a scoped emergency hold;
- quarantine one reconciliation artifact/parser/feed;
- stop automated case closure or recovery offset;
- stop statement issuance while preserving underlying finance truth.

Recovery workers, reads, reconciliation, and safe provider queries should remain available where
possible. A kill switch must not delete or reclassify money.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Immutable booking allocation missing/inconsistent | Reject posting, retain source in owned queue, block dependent payout; never infer ownership |
| Posting rule missing or invalid | Quarantine source/rule version, alert finance/engineering, create no partial journal |
| Duplicate source event | Return existing journal when hash matches; raise conflict when it differs |
| Process crash during journal transaction | Atomic commit yields either complete balanced transaction or no transaction |
| Outbox unavailable after journal work | Commit journal and outbox atomically or neither; relay retries |
| Host compliance/risk service unavailable | Follow approved fail-closed/defer policy; preserve payable and schedule reevaluation |
| Hold arrives after payout reservation | Revalidate before submit; if already submitted, record recovery/manual path without rewriting entitlement |
| Payout provider unavailable before submit | Keep instruction durable and items reserved; retry with same key or pause route |
| Payout submit times out | Mark outcome unknown, query/reconcile; do not create a replacement instruction blindly |
| Duplicate/out-of-order payout webhook | Deduplicate and reduce monotonically; no duplicate posting or notification |
| Partial payout evidence | Allocate verified part only; leave remainder pending/exception; do not mark full amount paid |
| Payout fails terminally | Post/restore liability according to policy, release or hold items, request safe host action |
| Paid payout is returned | Record new return evidence and journal/recovery; retain original paid history |
| Statement rendering/delivery fails | Retry from immutable statement data; payout/journal remain committed |
| External report missing | Mark coverage incomplete, alert by cutoff, preserve internal truth and open period policy |
| Corrected provider artifact arrives | Store superseding artifact, rerun affected controls, reopen contradicted cases explicitly |
| Reconciliation match is ambiguous | Create review candidate/case; do not auto-post or auto-close |
| Bank/provider total differs | Preserve both sides, classify scope/timing first, escalate material unexplained difference |
| Adjustment approval expires or input changes | Invalidate approval; require a new exact review |
| Period closes while late event arrives | Fence with watermark and apply approved late-event/reopen policy |
| Projection/cache stale | Show watermark or fall back to authoritative query; never use it for payout consumption |
| Backup restored | Replay inbox/outbox and provider evidence, rerun reconciliation, prove balances before payout resumes |

## Testing and verification

### Deterministic posting tests

- Golden fixtures cover every launched source type, legal entity, product class, rule version, and
  accounting-date boundary.
- Every output balances by book/legal entity/currency and uses only valid active accounts/dimensions.
- Tax payable is never classified as platform revenue.
- Host payable, reserve, provider clearing, payout in transit, fee, and recovery mappings match
  finance-approved expectations.
- Historical replay pins original source/rule versions despite current configuration changes.
- Rounding and allocation preserve every minor unit.
- Full reversal nets every original dimension to zero; correction remains separately visible.

### Database and property tests

- No posted journal can be partially present, edited, or deleted through application paths.
- Unique source identity prevents duplicate accounting effects under concurrent inserts.
- Debit/credit equality holds for generated transaction sets.
- A host payable allocation cannot be reserved/consumed beyond remaining amount.
- One source payout allocation cannot be settled twice.
- Sum of statement lines matches opening/activity/closing and payout amounts exactly.
- Recovery/reserve allocation never exceeds its approved source ceiling.
- Currencies are never added or netted without an explicit FX relationship.

### Release and payout state tests

- Release before/at/after cutoff and listing/provider/accounting timezone boundaries.
- Compliance, risk, dispute, reserve, destination cooling-off, threshold, and negative-balance
  combinations.
- Two payout planners racing for the same item.
- Hold/destination change/cancellation racing plan or submission.
- Provider success, failure, timeout, unknown, duplicate webhook, late success, partial outcome, and
  return after paid.
- Retry/reissue proves one external transfer under provider contract fixtures.
- Failed or returned payout preserves/restores the correct host liability and statement history.

### Reconciliation tests

- Exact ID, metadata ID, batch, one-to-many, many-to-one, and bounded composite matches.
- Duplicate external/internal row, missing side, wrong amount, wrong currency, wrong account, wrong
  status, fee netting, timing difference, corrected artifact, and missing sequence.
- Provider opening/activity/closing equation with exact cutoff semantics.
- Payment-to-ledger, ledger-to-provider clearing, payout-to-bank, and host-payable-to-payout controls.
- Materiality boundary below/at/above threshold.
- Ambiguous candidates never auto-close or create a journal.
- Case resolution, approval, correction, reopen, and replay retain original evidence.

### State-machine and idempotency tests

- Every allowed/forbidden journal, payout, destination, statement, recovery, case, and period
  transition.
- Same command/key/hash returns the original result; same key/different hash conflicts.
- Duplicate/out-of-order events cannot regress terminal facts.
- Crash before/after every transaction/outbox/network/evidence boundary converges.
- Expired worker lease cannot apply a late result after a newer fencing token.

### Security and privacy tests

- Cross-host, cross-co-host, cross-legal-entity, and privilege-escalation access attempts.
- Step-up and maker-checker thresholds, self-approval, expired approval, and break-glass review.
- Destination token/redaction, compromised-session lock, and independent notification.
- Webhook signature/API authentication, artifact upload validation, malware/parser/path/formula attacks.
- Logs, traces, events, exports, support views, and analytics contain no prohibited credentials or
  unrestricted evidence.
- Retention and pseudonymization preserve financial integrity while honoring approved privacy policy.

### Provider and document contract tests

- Payout idempotency, supported currencies/rails, status mapping, reference uniqueness, timeout/query,
  webhook, return, and corrected report behavior in provider sandbox/recorded fixtures.
- Statement render is deterministic from one data hash and localization does not alter amounts.
- Tax/invoice/document handoff preserves exact source/journal references.
- Provider API/schema/version changes fail safely and alert before corrupting normalization.

### Recovery and operational tests

- Replay a source event and full outbox/inbox history without duplicate effects.
- Rebuild balance/statement projections from journal and compare hashes.
- Restore a production-like backup, ingest missed evidence, reconcile all control accounts, and only
  then re-enable payout.
- Exercise provider outage, destination compromise, duplicate suspicion, returned payout, missing
  report, material mismatch, late close event, and mass hold game days.
- Finance/support resolves reference cases without SQL updates.

## Caching, performance, and scaling

### Authoritative versus cached reads

Never use a cached balance, statement summary, warehouse result, or search index to post a journal,
release a hold, reserve payable items, submit payout, or close reconciliation. Those operations use
locked authoritative rows and exact source versions.

Host dashboards may cache derived summaries keyed by host/legal entity/currency/watermark. Responses
show freshness. A cache miss or lag falls back to an authoritative bounded query or an explicit
temporarily unavailable response; it never fabricates zero balance.

### Query and batch shape

- Keep journal insert transactions small and bounded by one business fact.
- Maintain incremental account/host balance projections from posted journal events, but reconcile
  them periodically to raw postings.
- Partition work queues by legal entity/provider/currency/time only when volume justifies it.
- Claim release, payout, ingestion, reconciliation, and statement batches with bounded size and
  `SKIP LOCKED`/leases.
- Page transaction history by stable keyset such as `(accounting_at, id)`, not large offsets.
- Stream and hash large artifacts; do not load an unbounded provider report into memory.
- Reconciliation rules must declare maximum time windows and candidate counts to prevent quadratic
  matching.

### Hotspots

Potential hotspots include one high-volume provider clearing account, a large professional host,
one payout schedule cutoff, period close, and bulk provider reports. Avoid one mutable global balance
row as the sole serialization point. Prefer append-only postings, sharded projections where measured,
and item-level payout reservation.

### Scaling triggers

Remain a modular monolith until measurements show sustained contention, queue isolation, regulatory
segregation, or team ownership justifies extraction. Before partitioning tables or splitting
services, capture posting throughput, lock wait, payout batch duration, artifact size, reconciliation
candidate fan-out, projection lag, restore/replay time, and close window.

## Appropriate use of AI

AI and machine learning may assist only around deterministic finance authority.

Useful bounded applications include:

- rank likely reconciliation candidates after deterministic identifiers are exhausted;
- classify safe provider failure/return descriptions into a review taxonomy with confidence;
- prioritize exception queues by estimated operational risk, age, and materiality;
- detect anomalous payout destinations, duplicate patterns, reconciliation drift, or adjustment abuse;
- summarize a booking-to-payment-to-journal-to-payout timeline for authorized staff with citations to
  source records;
- suggest a user-safe explanation or runbook step from already-decided reason codes;
- forecast payout volume, liquidity needs, failure risk, and staffing without executing movement.

Prerequisites include point-in-time-correct labels, source provenance, approved feature access,
representative evaluation by market/provider/host cohort, calibrated confidence, human-review
workflow, drift monitoring, audit, and a kill switch. Model output is stored as a recommendation with
version and evidence, separate from the final decision.

AI/ML may not:

- calculate authoritative balances, tax, entitlement, FX, rounding, journal postings, or statements;
- decide economic ownership, recognition timing, legal/accounting treatment, or policy effective date;
- release/hold/reserve/offset host funds or select a payout amount/destination;
- prove provider movement, close a material mismatch, approve an adjustment, reopen a period, or
  write off debt;
- personalize payout timing or deductions by inferred willingness to accept delay;
- invent missing references or replace provider/bank evidence;
- expose internal risk/accounting detail or personal/financial data in prompts.

Low-confidence or unavailable models fall back to deterministic matching, static priority, and human
review. Disabling every model must leave ledger, payout, reconciliation, and close correctness intact.

## Rollout plan

### Phase 0 — Legal, finance, security, and provider decisions

Choose first legal entity/market/currency, marketplace role, account/recognition model, chart of
accounts, posting rules, host entitlement/release promise, reserve/hold/recovery policy, provider
accounts, payout rail/destination onboarding, reconciliation sources/cutoffs/materiality, document/
retention rules, roles/approvals, and operating owner. Approve Architecture Decision Records (ADRs),
golden posting fixtures, threat model, and close checklist.

Exit: every launch choice has an accountable owner, approved examples, provider evidence contract,
user disclosure, operational SLA, and revisit trigger.

### Phase 1 — Immutable source allocation and balanced ledger

Add forward schemas for booking financial lines, books/accounts/rules, journal/postings, finance
idempotency, inbox/outbox, audit, and periods. Implement deterministic capture/refund posting in
shadow mode, balance/property checks, and source trace. Do not enable payout.

Exit: every eligible new verified payment fact yields exactly one reproducible balanced proposal;
unknown legacy provenance is quantified and routed, not guessed.

### Phase 2 — Posted finance vertical slice and payment reconciliation

Enable posting for one legal entity/currency/source set. Reconcile booking allocation, collection
obligation, verified capture/refund, journal, provider clearing, fees, and bank settlement. Add
restricted finance diagnostics and correction workflow.

Exit: reference days close with complete source-to-journal coverage, exact control balances, owned
exceptions, replay safety, and zero unexplained material difference.

### Phase 3 — Host payable, release, and shadow statements

Create host payable allocations, release schedule, holds/reserves/recovery structures, eligibility
decisions, and host balance projections. Generate shadow statements and compare them with approved
manual expectations. Do not submit transfers.

Exit: host gross-to-net and available/held/reserved balances reproduce for booking, cancellation,
refund, dispute, and adjustment fixtures; support/finance can explain every line.

### Phase 4 — One payout rail and controlled cohort

Add tokenized destinations, step-up/cooling-off, payout planning/reservation, one provider adapter,
unknown-outcome recovery, statements, notifications, and provider/bank reconciliation. Enable a small
host cohort with conservative limits and manual oversight.

Exit: no duplicate item/transfer occurs under retries; eligible payouts meet the promise; failure and
return restore the right liability; every transfer reconciles to provider and bank evidence.

### Phase 5 — Governed exceptions, recovery, and close

Add reconciliation cases/SLA, adjustment catalog/maker-checker, negative-balance waterfall, returns,
write-offs, period close/reopen, aging, audit exports, and tested runbooks.

Exit: finance closes a full production-like period without SQL repair; all material exceptions have
owner/evidence/resolution; restored backup reconciles before payout resumes.

### Phase 6 — Additional schedules, reserves, rails, and documents

Add approved host payout schedules, thresholds, reserve programs, tax withholding/document links,
new rails/provider accounts, and professional-host reporting one capability at a time. Run shadow,
canary, reconciliation, and rollback gates for each.

Exit: each new combination has golden postings, provider contracts, host disclosure, measured SLO,
security review, and independent reconciliation.

### Phase 7 — Multi-currency, multi-entity, and bounded intelligence

Only after legal/accounting design is approved, add FX contracts, currency bridge entries, payout
conversion, due-to/due-from, functional-reporting exports, and market-specific close. Introduce AI
candidate ranking/anomaly/forecasting in shadow and human-review modes.

Exit: every currency/entity independently balances and reconciles; FX gain/loss and returns reproduce;
models meet calibration/fairness/privacy guardrails and can be disabled without operational impact.

## Verification checklist

### Financial and functional correctness

- [ ] Every posted journal belongs to one book/legal entity/currency and balances exactly.
- [ ] Every source fact maps through one approved pinned posting-rule version and unique source key.
- [ ] Posted entries, issued statements, and closed-period evidence are immutable.
- [ ] Reversal and correction preserve full provenance and never overwrite history.
- [ ] Booking allocation, verified movement, recognition, host payable, available funds, payout, and
  revenue remain distinct.
- [ ] Tax payable and third-party funds cannot become platform revenue accidentally.
- [ ] Every host balance and statement line traces to journal/source allocation.
- [ ] Hold/reserve/withholding/recovery affects only the approved amount and scope.
- [ ] No payout item is consumed twice and no negative/zero payout is submitted.
- [ ] Failed/returned payouts preserve or restore the correct host liability.
- [ ] Currency/FX rounding preserves every minor unit and never nets unsupported scopes.

### Reconciliation, close, and recovery

- [ ] Booking, payment, ledger, provider clearing, payout, bank, and statement controls cover the full
  launched flow.
- [ ] Artifacts have authenticated source, immutable hash, parser version, coverage, and completeness.
- [ ] Matching rules are deterministic, bounded, versioned, and never hide ambiguous differences.
- [ ] Material mismatch cases have owner, SLA, evidence, approval, and additive resolution.
- [ ] A corrected artifact can reopen prior conclusions without destroying history.
- [ ] Period close records watermarks, controls, exceptions, approvals, and reproducible balance hash.
- [ ] Late facts follow explicit accounting-date/reopen policy.
- [ ] Backup restore, replay, projection rebuild, and full reconciliation complete before payout resumes.

### Concurrency and idempotency

- [ ] Duplicate/replayed source, provider event, worker task, payout command, and artifact create one
  intended effect.
- [ ] Same idempotency key with different material input is rejected.
- [ ] Concurrent planners cannot reserve the same payable allocation.
- [ ] Hold, destination change, cancellation, recovery, payout, return, and close races follow the
  documented winner/compensation behavior.
- [ ] Stale leases/fencing tokens and out-of-order evidence cannot regress state.
- [ ] No database lock spans an uncontrolled network call.

### Security, privacy, and governance

- [ ] Host/co-host/support/finance/auditor/service roles are resource- and action-scoped.
- [ ] Destination changes and high-risk finance actions use step-up/maker-checker as configured.
- [ ] Raw credentials and sensitive bank/tax/evidence data never enter logs, events, or broad views.
- [ ] Provider artifacts/uploads are authenticated, bounded, validated, encrypted, and retained safely.
- [ ] Chart/rule/policy publication includes approval, effective interval, golden tests, and rollback.
- [ ] Adjustment/write-off/hold override cannot bypass allowlisted rules or self-approval controls.
- [ ] AI remains advisory, auditable, privacy-bounded, and independently disableable.

### Operations and delivery

- [ ] Dashboards distinguish contracted, captured, recognized, payable, available, held, reserved,
  in-transit, paid, returned, and reconciled values.
- [ ] Hosts receive accurate safe explanations and statement corrections without history deletion.
- [ ] Finance/support can trace and repair reference incidents through product tools without SQL.
- [ ] SLOs, alerts, ownership, materiality, escalation, and runbooks are approved and exercised.
- [ ] Kill switches pause scoped new work without deleting or reclassifying money.
- [ ] Forward migrations preserve applied booking/payment changesets and legacy ambiguity is measured.
- [ ] Shadow, dual-validation, cohort, reconciliation, and retirement gates are measurable.

## Decisions required before implementation

Record each consequential choice as an ADR containing owner, date, context, alternatives, decision,
consequences, rollout, and revisit trigger:

1. First market, legal entity, contractual/merchant role, accounting book, reporting basis, and
   functional/transaction currency.
2. Approved chart of accounts, dimension model, normal balances, account lifecycle, and downstream
   general-ledger/export boundary.
3. Source fact catalog and exact posting rules for capture, authorization release, refund, dispute,
   chargeback, processor fee, booking allocation, cancellation, host entitlement, payout, return,
   reserve, recovery, promotion, tax, FX, and adjustment.
4. Recognition timing for accommodation, platform fees/commission, cancellation penalties,
   non-refundable amounts, partner products, tax, promotions, and variable costs.
5. Whether host entitlement is created/earned at capture, check-in, checkout, completion, or another
   contract event, and how cancellation/no-show/early departure changes it.
6. Exact host fund release policy, timezone/cutoff, payment-settlement prerequisite, dispute window,
   and promise shown to hosts.
7. Hold taxonomy, issuing authorities, scope, expiry/review, disclosure, appeal, emergency behavior,
   and race after payout reservation/submission.
8. Reserve programs, calculation/cap/duration, accounting treatment, host contract/disclosure, model
   role, release, and regulatory review.
9. Tax withholding authority, source, payout effect, host display, document/reporting integration,
   correction, and fallback when profile/content is incomplete.
10. First payout provider/account/rail, supported destination types/currencies/countries, provider
    finality mapping, idempotency/query/webhook/report contract, and build-versus-buy boundary.
11. Payout cadence, cutoff timezone, weekend/holiday calendar, minimum/maximum threshold, fee bearer,
    grouping, partial outcome, retry, reissue, and return policy.
12. Destination onboarding/verification, token storage, ownership, cooling-off, step-up, independent
    notification, compromise recovery, and old-destination draft behavior.
13. Host statement period/payout relationship, opening/closing definitions, localization, correction,
    delivery, dispute, retention, and legal-document distinction.
14. Reconciliation source inventory, artifact transport/retention, account/currency/cutoff mapping,
    parser ownership, sequence/completeness, and corrected-file semantics.
15. Deterministic match ladder, one-to-many/many-to-one rules, timing tolerance, materiality, automated
    close boundary, case severity/SLA, and escalation owner.
16. Provider clearing and bank balance equations, settlement fee/netting treatment, and which
    independent evidence proves final receipt.
17. Manual adjustment catalog, amount/role thresholds, maker-checker, self-related cases, evidence,
    reversal, emergency break-glass, and post-action review.
18. Negative host balance waterfall, future offset limits, reserve application, authorized debit,
    notices, disputes/appeals, collection, cross-booking/entity/currency restrictions, and write-off.
19. Accounting period timezone/cutoff, soft/hard close controls, late-event policy, reopen authority,
    aging definitions, and close evidence retention.
20. Multi-currency launch boundary, FX source/rate/expiry/spread, minor-unit/rounding, refund/chargeback/
    return rate policy, host payout conversion, and gain/loss owner.
21. Legacy booking/payment backfill rules, unknown-provenance treatment, opening balances, dual-run,
    shadow reconciliation, cutover, rollback, and legacy read retirement.
22. Role/permission matrix, workload identities, step-up, separation of duties, auditor access,
    sensitive-data classification, retention, privacy requests, and incident response.
23. Financial correctness and payout SLOs, material alerts, on-call/finance/support ownership, report
    delivery cutoff, disaster-recovery proof, and payout-resumption gate.
24. Permitted AI use cases, training/feature restrictions, confidence/human-review threshold,
    evaluation slices, audit/retention, and kill-switch owner.

The safest first implementation is a single-entity, single-currency, append-only balanced journal
fed by immutable allocation and verified payment facts, with complete source trace and daily
reconciliation. Host payout should begin only after payable/release semantics are approved and the
platform can prove that retries, failures, returns, and restore/replay do not lose, duplicate, or
reclassify a host's money.
