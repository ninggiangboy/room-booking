# Payment orchestration

## Purpose

This document defines the target payment-orchestration architecture for Room Booking. It expands
[D09 — Payment orchestration](../marketplace-problem-breakdown.md#d09--payment-orchestration) from
the marketplace master map into an implementation-oriented design.

The central question is:

> How can the platform collect, authorize, capture, void, and return the exact contractual amount
> through unreliable asynchronous payment providers without duplicating a financial effect or
> confusing provider state with booking or accounting truth?

Payment orchestration sits between three authoritative neighbors:

- [availability and booking](availability-reservation-and-booking.md) owns inventory claims,
  accepted booking state, and the confirmation/expiry race;
- [pricing and quote](dynamic-pricing-and-settlement.md) owns immutable source amounts, allocation,
  and tax decisions;
- [ledger, reconciliation, and payout](ledger-reconciliation-and-host-payout.md) owns posting rules,
  accounting truth, host payable/release, payout, statements, close, and financial reconciliation;
- [cancellation and modification](cancellation-modification-and-refund.md) owns executable accepted
  policy, replacement-contract decisions, line-level refund entitlement, and funding instructions;
- [messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md)
  owns fact-derived payment reminders and delivery evidence but cannot authorize, capture, refund,
  or change payment/booking state;
- [trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns versioned
  payment-risk interpretation, challenges, scoped restrictions, and fraud labels but cannot mark
  provider money movement;
- [disputes, damage claims, insurance, and customer support](disputes-damage-claims-and-support.md)
  owns provider-dispute strategy, evidence manifests, participant communication, claim/remedy
  authorization, and appeal, while payment retains provider-case and movement authority.

This feature owns provider-facing payment commands, verified provider evidence, operational payment
state, and recovery. It does not decide what a stay costs, whether inventory exists, who ultimately
owns collected cash, or whether a guest is contractually entitled to a refund.

## Status and dependencies

The shared identity, time, money, request/error, idempotency, outbox/inbox, audit, secret, and
compatible migration contracts come from the [platform foundation](platform-foundation.md). Payment
adds provider-operation identities, monetary ceilings, verified evidence, and unknown-outcome
recovery.

[Vietnam market readiness and internationalization](multi-market-compliance-and-localization.md)
owns the approved legal-entity, VND, provider-account, and effective policy context used here.

This is a target design. No payment controller, service, repository, provider adapter, webhook
handler, worker, or provider configuration is implemented in Java yet.

The repository currently has these schema foundations:

- [`bookings`](../data-model/004-booking.md) stores a `PENDING_PAYMENT` booking, payable summary,
  immutable listing snapshot, and `payment_expires_at`;
- [`payment_attempts`](../data-model/005-payment.md) records provider, provider reference, global
  idempotency key, amount, currency, coarse state, and optimistic version;
- `refunds` records one provider-facing refund attempt with amount, reason, and coarse state;
- `payment_webhook_events` is an inbox deduplicated by provider and provider event ID;
- PostgreSQL constraints enforce non-negative amounts, three-character currencies, unique provider
  references, and at most one `SUCCEEDED` attempt per booking.

These tables are useful foundations, not a complete workflow. Migration
[`005-payment.sql`](../../src/main/resources/db/changelog/changes/005-payment.sql) does not distinguish
authorization from capture, model multiple operations on one attempt, represent unknown outcomes,
bind a provider account/legal entity, schedule deposits or installments, retain payment-method token
references, or model disputes and operational reconciliation. Its single-success index deliberately
precludes multiple successful collections for a booking. The Java application has no payment
provider dependency or configuration.

All schema evolution described below requires a new forward-only Liquibase migration. Migration
`005` must not be edited after application.

Recommended dependency order:

1. Decide launch legal entity, merchant model, country, currency, provider, and payment method.
2. Implement deterministic quotes and immutable booking financial snapshots.
3. Implement explicit inventory hold/claim and provisional booking recovery.
4. Add one pay-now method with provider-independent attempt and operation records.
5. Add verified webhook ingestion, server-side status retrieval, recovery, and reconciliation.
6. Connect capture/refund facts to an idempotent ledger posting boundary.
7. Complete payment-provider disputes for the approved flow; add saved/delayed methods, deposits,
   installments, or multi-provider routing only when required by an approved rate plan or measured
   resilience/optimization need.

The Vietnam target release may use one active provider account, one legal entity, VND, and the
approved guest-facing
payment method, and one clear authorize/capture policy. Provider abstraction is required for domain
isolation, but simultaneous multi-provider routing is not.

## Goals

- Initiate each intended external payment operation at most once while safely replaying client,
  worker, and provider retries.
- Represent authorization, capture, direct sale, void, refund, and partial refund without reducing
  them to one ambiguous success flag.
- Confirm a booking only from an internal, verified, provider-independent payment fact.
- Recover deterministically after client disconnect, timeout, process crash, delayed webhook,
  duplicate webhook, and out-of-order provider events.
- Support customer actions such as 3-D Secure (3DS), Strong Customer Authentication (SCA), and
  provider redirects without trusting browser success callbacks.
- Enforce exact amount, currency, cumulative capture, and cumulative refund limits under
  concurrency.
- Support pay-now first and evolve explicitly to authorization-first, deposits, installments,
  balance collection, and asynchronous payment methods.
- Use provider tokens for saved methods without storing raw card or bank credentials.
- Classify failures into safe guest action, safe retry, required query, or manual review.
- Preserve an immutable, support-readable timeline from internal command to provider evidence.
- Detect and reconcile orphan, missing, duplicated, delayed, and mismatched provider transactions.
- Keep Payment Card Industry Data Security Standard (PCI DSS) scope deliberately small.
- Provide a provider outage mode and migration strategy that never creates a blind duplicate charge.

## Non-goals

- Calculating nightly prices, tax, discounts, service fees, or the guest total.
- Deciding cancellation or refund entitlement.
- Posting arbitrary accounting journals or calculating host payout and platform revenue.
- Performing host payout; payout rails and host entitlement belong to the finance domain.
- Treating a provider dashboard, webhook, browser redirect, or analytics event as booking truth.
- Storing primary account numbers (PANs), card verification values, bank credentials, or payment
  provider secrets in ordinary application tables, logs, events, support tools, or analytics.
- Building a card vault, acquirer, fraud network, SCA engine, or dispute network in-house.
- Automatically retrying an unknown operation at another provider.
- Hiding payment-method fees, currency conversion, installments, or delayed settlement from users.
- Using an LLM to decide that money moved, calculate amounts, approve a refund, or resolve a dispute.
- Supporting every provider and payment method through a lowest-common-denominator abstraction.

## Core principles and invariants

### Payment state is not booking state or accounting state

A booking can be held while no money has moved. A provider can capture money while local booking
confirmation is delayed. Captured cash is not automatically platform revenue, host earnings, or
available payout. Payment emits verified facts; booking and finance apply their own idempotent state
transitions.

No table or enum should contain a combined state such as `BOOKED_AND_PAID_AND_SETTLED`.

### Amount authority comes from an immutable internal obligation

The client supplies a quote or checkout identifier, payment-method token, and intent—not a trusted
amount, currency, beneficiary, or capture schedule. Payment loads the accepted booking financial
snapshot and an immutable collection obligation created from it.

For every provider request:

```text
requested amount = remaining amount permitted by collection obligation and schedule
requested currency = obligation currency
provider account = eligible account for legal entity, market, currency, and method
```

An amount mismatch fails before submission or enters reconciliation; it is never rounded or
silently adjusted inside the payment adapter.

### Obligation, attempt, operation, and evidence are different facts

- A **collection obligation** says what the guest must pay and by when.
- A **payment attempt** is one guest/provider-method journey toward satisfying an obligation.
- A **payment operation** is one external side-effect request such as authorize, capture, void, or
  refund.
- A **provider event or query observation** is evidence about the provider's state.

One attempt can require several customer actions and provider observations. One authorization can
have multiple captures where policy permits. One booking can have multiple obligations for a
deposit and later balance. Conflating these concepts makes retries and reconciliation unsafe.

### External calls always have an unknown-outcome branch

A timeout does not mean failure. The provider may have completed the operation after the platform
stopped waiting. Every submitted operation records a stable provider idempotency key and moves to
`UNKNOWN` or `PENDING` until a verified webhook or server-to-server query resolves it.

Never submit a replacement operation—at the same or another provider—while the original result is
unknown unless the original is proven non-existent or safely cancelled according to provider
contract.

### Idempotency replays the original intent and result

Idempotency is scoped to actor/resource and operation, not merely a globally unique text value. The
platform stores a canonical request hash, created resource, response projection, and processing
state. Reusing the same key and same request returns the same attempt or operation. Reusing it with
different material input returns `IDEMPOTENCY_KEY_REUSED`.

Provider request keys, client command keys, webhook event IDs, outbox event IDs, consumer inbox
keys, refund instructions, and ledger posting keys are related but separate namespaces.

### Verified server evidence is required

A browser return URL, mobile deep link, SDK callback, screenshot, or guest assertion can prompt a
status refresh but cannot mark a payment successful. Success requires a signature-verified provider
event or authenticated server-to-server provider retrieval mapped through the adapter.

### Monetary ceilings are enforced transactionally

For one collection obligation in one currency:

```text
0 <= successful captures + active capture reservations <= collectible ceiling
0 <= successful refunds + reserved pending refunds <= refundable captured amount
0 <= remaining due = scheduled amount due - successful captures credited to that schedule
```

A refund does not silently reopen a satisfied collection obligation. Any replacement amount due
after a modification, failed service, or policy adjustment comes from a new versioned obligation or
schedule decision.

Authorization, capture, void, refund, dispute, and reversal are stored as positive amounts with an
explicit operation type/direction. Cross-row totals are checked under a locked obligation/payment
record and audited by reconciliation. Provider support for overcapture or incremental authorization
does not enable it without an explicit platform policy.

### State advances from evidence and never silently regresses

Events may arrive late or out of order. A provider timestamp alone cannot overwrite a newer internal
fact. The transition function evaluates the complete known provider object, operation identity,
internal version, and precedence. Terminal business facts remain immutable; corrections are new
observations, reversals, refunds, or dispute adjustments.

### Database transactions end before provider calls

The command transaction validates authority and amounts, creates a durable operation in
`READY_TO_SUBMIT`, commits it, and emits an outbox fact. A worker or post-commit executor performs the
network call. A later short transaction applies the result under lock. No database lock remains open
across an uncontrolled network call or browser challenge.

### Booking coordination is an explicit saga

Payment never writes booking status ad hoc. It publishes a verified outcome correlated to the
checkout and collection obligation. Booking owns the confirmation/expiry transition and responds
with accepted, already applied, or rejected/compensate. Late captured money is voided or refunded
idempotently if inventory cannot be reacquired under the documented booking policy.

### Payment credentials stay with a compliant vault/provider

Room Booking stores only opaque provider tokens and safe display metadata required by product and
support policy. Tokenization can reduce exposure but does not automatically remove all PCI DSS
responsibilities; actual scope depends on the integration and systems involved. The launch design
must be reviewed against the current [PCI DSS document library](https://www.pcisecuritystandards.org/document_library/?class=pcidss&doc=pci_dss)
and the provider's validated integration guidance.

### Every consequential action is attributable and reversible where the rail permits

Manual retry, refund, void, force-fail, provider-reference repair, and dispute submission record
actor, reason, evidence, approval, correlation, old/new version, and resulting operation. A support
screen may explain or request a domain command; it may not mutate rows directly.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Payment service provider (PSP) | External provider that exposes payment-method, authorization, capture, refund, and status capabilities |
| Provider account | One merchant/account configuration at a PSP, bound to a legal entity, markets, currencies, capabilities, credentials, and webhook endpoint |
| Collection obligation | Immutable amount/currency a guest owes for a booking purpose, with due time and allowed collection policy |
| Collection schedule | One or more due components such as deposit, pay-now amount, or later balance |
| Payment attempt | One provider/method/customer journey for satisfying all or part of an obligation |
| Payment operation | Durable request for one external action: authorize, capture, sale, void, refund, or query |
| Authorization | Provider approval reserving spending capacity without final capture; validity and guarantees are provider/method specific |
| Capture | Request to convert available authorization into collected funds |
| Sale | Provider operation that authorizes and captures as one commercial action |
| Void/cancel authorization | Request to release unused authorization before settlement when supported |
| Reversal | Provider-initiated or platform-requested correction of a prior movement; not interchangeable with a refund |
| Refund | Return of previously captured funds; entitlement is decided outside payment orchestration |
| Payment-method reference | Opaque vault/provider token plus non-sensitive display metadata and consent context |
| Customer action | Authentication, redirect, wallet approval, mandate, or other step completed outside the server request |
| 3-D Secure (3DS) | EMVCo protocol supporting authentication for card-not-present payments |
| Strong Customer Authentication (SCA) | Regulatory authentication requirement that may apply to a transaction in an applicable market |
| Provider evidence | Signature-verified webhook or authenticated provider API/file observation |
| Unknown outcome | The platform knows an operation may have reached the provider but cannot yet prove success or failure |
| Orphan transaction | Provider object or money movement not mapped confidently to one internal operation |
| Dispute/chargeback | External process contesting a captured payment; it is not a refund and may include deadlines and fees |
| Retrieval request | Request for information/evidence that may precede a formal dispute |
| Operational reconciliation | Matching internal operations to provider objects and statuses |
| Financial reconciliation | Matching captured/refunded/disputed/settled amounts to ledger and bank/provider clearing; owned by finance |
| PAN | Primary account number; raw card number that must not enter ordinary Room Booking storage |

All money uses integer minor units and ISO 4217 currency codes. Operation deadlines and provider
events use UTC instants. Guest-visible schedules may also store a local due date, IANA timezone, and
the deterministic conversion rule used to obtain the instant.

## End-to-end flow

### Pay-now booking flow

```text
accepted quote + active inventory hold
  -> booking creates immutable collection obligation and payment request
  -> payment validates actor, obligation, method token, risk decision, and idempotency
  -> local transaction creates attempt + READY_TO_SUBMIT operation + outbox
  -> provider adapter submits after commit
      -> immediate decline: persist failure and allow safe new attempt
      -> customer action: return bounded action descriptor; keep hold policy visible
      -> pending/unknown: wait for webhook/query; do not duplicate
      -> authorized/captured: persist verified evidence and emit payment fact
  -> booking confirms while holding the authoritative inventory locks/claim transition
      -> accepted: emit BookingConfirmed; finance posts from payment/booking facts
      -> hold lost/booking expired: request idempotent void or refund compensation
  -> notification and analytics consume committed facts asynchronously
```

### Local transaction and saga boundaries

| Boundary | Atomic work | External work after commit |
| --- | --- | --- |
| Start attempt | Validate obligation and key; create attempt/operation; reserve permitted amount; write outbox | Submit operation to PSP |
| Apply provider outcome | Lock operation/attempt/obligation; append observation; transition; write outbox | Booking or finance consumes fact |
| Confirm booking | Lock checkout, hold/claim, and booking in canonical order; transition once; write outbox | Notifications and ledger consumers run |
| Request refund | Consume approved entitlement; reserve refundable amount; create operation; write outbox | Submit refund to PSP |
| Resolve refund/dispute | Apply verified provider evidence; release/consume reservation; write outbox | Ledger/support/reconciliation consume fact |

The booking correlation ID follows the entire saga. Each local aggregate retains its own version and
idempotency boundary; distributed atomicity is achieved through durable commands, outbox/inbox,
reconciliation, and compensation—not a database transaction around the PSP.

## Source-of-truth and authority matrix

| Fact/decision | Authority | Payment behavior |
| --- | --- | --- |
| Guest identity/session | Identity | Requires an authenticated subject for guest operations |
| Listing inventory and hold | Inventory/booking | Reads correlation and deadline; cannot create availability |
| Payable total and currency | Accepted booking financial snapshot | Copies exact obligation; rejects client amount |
| Collection schedule | Booking/payment policy from accepted terms | Executes due components; never invents a new installment |
| Risk permit/challenge/block | Risk decision service | Applies versioned bounded decision; safe fallback is explicit |
| Payment-method token | PSP vault/tokenization integration | Stores reference and safe metadata, not credential |
| External money movement | PSP evidence normalized by payment | Owns provider interaction and operational state |
| Booking confirmation | Booking | Consumes payment fact and resolves inventory race |
| Refund entitlement | Cancellation/modification or approved remedy decision | Executes exact approved instruction |
| Economic ownership/ledger | Finance | Consumes verified payment facts; payment cannot calculate revenue |
| Chargeback case strategy | Dispute/support/risk | Payment imports provider case and submits authorized evidence/action |
| Provider settlement and fees | PSP files/API plus finance reconciliation | Supplies observations; finance owns ledger comparison |

## Payment aggregate and identity model

### Collection obligation

A collection obligation is the stable internal contract for collection. Recommended identity:
`payment_order_id` or `collection_obligation_id`, not provider intent ID.

It contains:

- booking/checkout ID and immutable booking financial snapshot version;
- purpose such as `BOOKING_TOTAL`, `DEPOSIT`, `BALANCE`, `MODIFICATION_DELTA`, or
  `DAMAGE_CLAIM`;
- debtor guest, legal merchant entity, amount, currency, due instant, and expiry;
- allowed payment methods and capture policy version;
- amount satisfied, refunded, disputed, and remaining, derived from successful operations;
- state/version and cancellation reason;
- correlation, creation actor, and policy provenance.

An obligation is not a provider object. Re-routing or provider migration must not change its
identity. A booking can reference several obligations only when its accepted schedule explicitly
permits that.

### Payment attempt

An attempt binds one obligation/schedule component to:

- provider and provider account;
- payment-method family and opaque token reference;
- guest/customer reference where permitted;
- requested amount/currency copied from internal authority;
- routing-policy and risk-decision versions;
- attempt number, client command idempotency identity, and creation context;
- current derived state and latest safe decline/action projection.

Creating a new attempt means new guest intent, a known-terminal previous attempt, or an approved
routing recovery. Refreshing a page or replaying a request is not a new attempt.

### Payment operation

Each side effect is a separate row with:

```text
operation_id
attempt_id and obligation_id
type = AUTHORIZE | CAPTURE | SALE | VOID | REFUND | QUERY
amount_minor and currency where monetary
internal idempotency scope/key and canonical request hash
provider request key
provider operation/reference ID
state and state version
submission count and next attempt time
request created/submitted/resolved timestamps
safe normalized result/failure fields
correlation/causation and actor
```

Retries of one operation retain the same provider request key. A new capture or partial refund gets
a new operation and key, because it represents a new financial intent.

### Provider observations

Every accepted webhook, authenticated query result, and restricted reconciliation import produces
an append-only observation containing provider account, provider object/event identity, observed
state, provider occurrence time if supplied, receipt time, normalized amounts/currency, payload
digest, verification key version, and source.

Observations are evidence. A deterministic reducer maps the complete evidence set to internal state.
The current projection can change; accepted observations do not.

## State models and transition ownership

### Collection obligation state

```text
OPEN
  -> ACTION_REQUIRED
  -> PROCESSING
  -> AUTHORIZED
  -> PARTIALLY_PAID
  -> PAID

OPEN/ACTION_REQUIRED/PROCESSING -> CANCELLED or EXPIRED
PAID -> PARTIALLY_REFUNDED -> REFUNDED
AUTHORIZED -> PARTIALLY_PAID | PAID | VOIDED | EXPIRED
any captured state -> DISPUTED exposure may coexist as a separate dimension
```

`FAILED` should normally describe an attempt, not permanently close an obligation that the guest
may satisfy with another method. An obligation becomes expired/cancelled only by its contractual
deadline or owner command.

### Attempt state

```text
CREATED
  -> SUBMITTING
      -> REQUIRES_ACTION
      -> PROCESSING
      -> AUTHORIZED
      -> PARTIALLY_CAPTURED
      -> CAPTURED

CREATED/SUBMITTING/REQUIRES_ACTION/PROCESSING -> FAILED | CANCELLED | EXPIRED
AUTHORIZED/PARTIALLY_CAPTURED -> CAPTURED | VOIDED | AUTHORIZATION_EXPIRED
CAPTURED -> PARTIALLY_REFUNDED -> REFUNDED
```

Attempt state is a projection for orchestration and UI. Operation rows remain the auditable source
for exactly what succeeded. Refund and dispute dimensions must not be compressed into the attempt's
collection status when doing so would hide simultaneous facts.

### Operation state

```text
PLANNED -> READY_TO_SUBMIT -> SUBMITTING
  -> PENDING
  -> REQUIRES_ACTION
  -> SUCCEEDED
  -> FAILED
  -> UNKNOWN
  -> CANCELLED_BEFORE_SUBMISSION

PENDING/REQUIRES_ACTION/UNKNOWN -> SUCCEEDED | FAILED | EXPIRED
```

`FAILED` requires evidence that the intended effect did not occur or cannot occur. Transport error,
HTTP timeout, malformed response after submission, and process crash after send are `UNKNOWN` until
queried or reconciled. Only `PLANNED`/`READY_TO_SUBMIT` can be cancelled locally without provider
proof because they have not crossed the submission fence.

### Transition matrix

| Evidence/command | Allowed owner | Required state effect | Forbidden shortcut |
| --- | --- | --- | --- |
| Start guest payment | Payment command | Create/replay attempt and first operation | Trust client total |
| Provider requires action | Payment reducer | Store bounded action descriptor and deadline | Mark booking paid |
| Verified authorization | Payment reducer | Record authorized amount and emit fact | Treat as captured unless contract says sale |
| Capture command | Booking/payment policy | Create operation within unused authorization/obligation | Capture from browser callback |
| Verified capture | Payment reducer | Increase captured total once; emit `PaymentCaptured` | Calculate revenue or payout |
| Booking accepts payment | Booking | Confirm claim/contract once | Payment writes booking row directly |
| Booking rejects late success | Booking | Emit compensation request | Ignore captured funds |
| Approved refund instruction | Cancellation/modification or approved remedy | Reserve refundable amount and create operation | Recalculate entitlement in adapter |
| Verified refund | Payment reducer | Consume refund reservation; emit fact | Mark booking uncancelled/cancelled |
| Dispute opened | Payment/dispute boundary | Append provider case and notify dispute/finance | Delete capture or label it refunded |

## Authorization, capture, sale, and void strategy

### Target-release provider strategy

Prefer authorization followed by capture when the selected provider/method and booking policy make
the hold-to-confirm race safer, and when authorization lifetime is sufficient. Otherwise use a
documented direct-sale flow and compensate a late success with an idempotent refund.

The exact policy is a launch decision, not a universal rule:

| Booking/payment context | Candidate strategy | Main consequence |
| --- | --- | --- |
| Instant book, short synchronous method | Authorize, confirm inventory, then capture | Easier late-expiry compensation; must recover authorized-not-captured cases |
| Instant book, provider only exposes sale | Direct sale, then confirm | Captured-before-confirmed race needs rapid refund path |
| Request to book | Authorization before host approval only if validity/disclosure permit; otherwise collect after approval | Avoid stale authorization and unexpected capture |
| Long delayed method | Do not hold scarce inventory indefinitely; use explicit policy | Booking may remain pending, or payment may require later revalidation |
| Deposit/installment | Separate scheduled obligations | Booking confirmation policy must define minimum collected/authorized amount |

### Authorization rules

- Record provider authorization amount, currency, expiry/valid-until if exposed, and capture method.
- Do not assume all methods support manual capture, partial capture, incremental authorization, or
  reliable void.
- Capture only after the internal command is durable and the booking policy condition still holds.
- Expiring authorizations require a worker; extension or reauthorization is a new explicit operation.
- Unused authorization is voided when possible; a provider reversal event is recorded separately.

### Capture rules

- The capture command references the authorization and exact schedule component.
- Under an obligation lock, reserve the requested capture amount before submission.
- Sum of successful plus in-flight reserved captures may not exceed policy-permitted amount.
- Partial captures are enabled only for a supported booking/payment schedule and otherwise fail closed.
- Provider fees are not subtracted from captured guest amount; finance records them separately.

### Direct-sale rules

A sale is one operation at the provider but still produces normalized authorization/capture evidence
as available. If the response is unknown, query the same provider reference/key. Never translate an
unknown sale into a fresh authorization or sale at another provider.

### Void versus refund

Void is used only for uncaptured or provider-reversible authorization according to provider state.
Refund returns captured funds. The platform queries when uncertain rather than selecting by age or
guessing. User communication may say "payment reversal" generically, while internal state retains
the exact operation.

## Customer action, 3DS, SCA, and redirect flows

[EMVCo describes EMV 3DS](https://www.emvco.com/emv-technologies/3-d-secure/) as an authentication
protocol for card-not-present commerce. Regulatory SCA applicability and exemptions depend on the
market, participants, transaction, and current rules; launch decisions must be reviewed against the
relevant authority, such as the European Banking Authority's
[SCA and secure-communication materials](https://www.eba.europa.eu/regulation-and-policy/payment-services-and-electronic-money?page=0).

The provider adapter returns a bounded action descriptor rather than arbitrary executable content:

```json
{
  "attemptId": "payatt_01...",
  "status": "REQUIRES_ACTION",
  "action": {
    "type": "PROVIDER_SDK" ,
    "providerClientToken": "short-lived-opaque-value",
    "expiresAt": "2026-09-06T09:15:00Z"
  },
  "holdExpiresAt": "2026-09-06T09:12:00Z",
  "pollAfterMs": 1500
}
```

Allowed action types are an explicit allowlist such as `PROVIDER_SDK`, `REDIRECT`, or
`WALLET_APPROVAL`. Redirect origins, schemes, callback destinations, and mobile deep links are
allowlisted. Provider-return parameters are untrusted hints.

Flow:

1. The server creates the attempt and provider operation.
2. The client completes the provider-controlled action through the approved SDK/hosted page.
3. The client returns to a neutral pending screen and may request current internal status.
4. The server waits for verified webhook evidence or retrieves the provider object.
5. Booking confirmation follows only from the internal verified result.

The UI must show both payment progress and remaining inventory-hold time. Completing customer action
does not automatically extend a hold. A bounded extension requires a server-observed active provider
challenge, a policy allowance, fencing, and a maximum total hold duration.

SCA exemption requests and provider risk options are versioned routing/policy inputs. A model may
recommend a challenge strategy, but the adapter and provider return the actual authentication
outcome. Authentication proof, liability-shift indicators, and safe provider references are retained
only as required for disputes/compliance and under an approved retention policy.

## Collection schedules: pay now, pay later, deposits, and installments

### Schedule representation

Do not encode installments as repeated attempts against one full booking total. Create immutable
schedule components:

```text
component 1: DEPOSIT, amount, due_at, confirmation_condition
component 2: BALANCE, amount, due_at, cancellation/default policy
sum(component amounts) = collection obligation amount
```

Each component has independent `OPEN`, `PROCESSING`, `SATISFIED`, `WAIVED`, `DEFAULTED`, or
`CANCELLED` state and can have multiple attempts. Amounts and due dates come from accepted rate-plan
terms. Modifications add replacement/delta schedule versions rather than rewriting satisfied items.

### Confirmation and default policy

The booking design must declare which condition confirms inventory:

- full amount captured;
- deposit captured and balance mandate/token validated;
- authorization of full amount;
- approved delayed-payment pending state with explicit risk ownership.

Payment reports facts; booking evaluates the chosen confirmation condition. A failed future balance
collection does not erase historical confirmation. It starts a versioned dunning/default workflow,
notifications, grace period, and eventual cancellation or support decision.

### Dunning and retries

Scheduled collection workers operate in bounded batches with `FOR UPDATE SKIP LOCKED` or equivalent
claiming. A schedule component has next-attempt time, retry policy version, attempt count, and final
deadline. Retries require a valid mandate/token and distinguish provider/network retry from asking
the guest for a new method. Quiet hours, legal notification, and card-network/provider rules are
market decisions.

Installments are a designed extension unless an approved Vietnam rate plan requires them. Their
implementation depends on proven pay-now, webhook recovery, refund allocation, ledger posting, and
reconciliation. Migration `005`'s one-success-per-booking index must be replaced only as part of an
explicit schedule migration and verified backfill.

## Saved payment methods and credential boundary

### Token model

Use provider-hosted collection, hosted fields, or approved client SDKs so raw credentials go from
the guest to the vault/provider rather than through Room Booking servers. Store only:

- opaque provider payment-method/customer token;
- owning guest and provider account scope;
- method family, safe brand/type, masked suffix, expiry month/year where permitted;
- reusable/single-use capability and mandate/consent reference;
- billing-country or fingerprint-derived risk reference only when necessary and lawful;
- lifecycle state, created/revoked timestamps, provenance, and retention category.

Do not store CVV after authorization under any circumstance. Do not store full PAN, bank account
number, magnetic-stripe data, PIN data, private wallet payload, or raw hosted-field submission.

### Ownership and portability

A provider token may be scoped to one provider account and cannot be assumed portable. Routing must
filter providers to those able to use the selected token. Provider migration requires guest
re-consent/re-entry unless the providers support a compliant, contractually approved vault migration.

Guests can list safe display metadata, set a default presentation preference, and revoke future use.
Revocation does not delete references required to explain historic transactions or active disputes;
it disables new operations and applies retention policy.

### PCI boundary

The architecture should minimize systems that can affect payment-page security, not merely avoid
persisting PAN. PCI SSC notes that systems performing tokenization or key management can remain in
scope; see its [tokenization scope FAQ](https://www.pcisecuritystandards.org/faqs/1117/). The final
self-assessment or validation category must be determined with the acquirer/provider and qualified
security/compliance owners for the exact web/mobile integration.

## Declines, retry policy, and guest recovery

Normalize provider outcomes into stable internal categories while retaining provider-specific safe
codes in a restricted field:

| Category | Meaning | Guest action | Automated action |
| --- | --- | --- | --- |
| `PAYMENT_METHOD_DECLINED` | Issuer/provider declined this method | Try another method/contact issuer | Do not hammer same operation |
| `AUTHENTICATION_REQUIRED` | Customer action is required | Complete approved challenge | Wait/query within deadline |
| `AUTHENTICATION_FAILED` | Challenge did not complete/verify | Retry only if provider permits | End attempt or new explicit attempt |
| `INSUFFICIENT_FUNDS` | Safe normalized decline where disclosure is permitted | Use another method | No immediate blind retry |
| `PAYMENT_METHOD_INVALID` | Expired/revoked/unsupported method | Replace method | Disable stale token if verified |
| `RISK_BLOCKED` | Platform/provider risk rejected | Show generic safe message | Route to review only by policy |
| `PROVIDER_TEMPORARY_FAILURE` | Provider proved no effect and allows retry | Wait/retry same intent | Backoff with same provider key |
| `OUTCOME_UNKNOWN` | Submission may have taken effect | Show pending | Query/reconcile; never duplicate |
| `CONFIGURATION_ERROR` | Merchant/account/method setup invalid | Generic unavailable message | Disable route and page operations |
| `AMOUNT_OR_CURRENCY_MISMATCH` | Provider evidence conflicts with obligation | No retry | Quarantine and reconcile |

Do not expose issuer-sensitive, fraud-rule, sanctions, internal routing, or provider-debug details.
Store both a guest-safe reason and a restricted operations reason.

Retry rules:

- transport retry uses the same operation and provider idempotency key;
- a new instrument or materially new customer action creates a new attempt;
- retry only after a known-terminal no-effect result or documented provider-safe response;
- cap attempts by booking, guest, instrument, device, IP risk, provider, and time window;
- preserve the inventory hold only within booking policy; payment retries cannot hoard inventory;
- detect card testing and distributed enumeration without revealing the detection rule.

## Provider abstraction and routing

### Capability-oriented adapter

Use an internal interface whose result types express uncertainty:

```text
createOrSubmit(operation, providerContext)
retrieve(operation/providerReference)
verifyAndParseWebhook(rawRequest, endpointContext)
cancelAuthorization(operation)
capture(operation)
refund(operation)
```

Each method returns one of:

```text
Succeeded(normalizedEvidence)
Pending(providerReference, nextQueryAt)
RequiresCustomerAction(providerReference, boundedAction)
Declined(normalizedReason, providerReference?)
RejectedBeforeSubmission(reason)
Unknown(providerReference?, queryAfter)
```

The adapter owns provider field mapping, credential selection, request signing, timeouts, response
validation, and capability discovery. It does not load booking totals, decide refund entitlement,
transition bookings, or post ledger entries.

### Provider account registry

Provider routing selects a versioned `provider_account` using:

- merchant legal entity and market eligibility;
- collection and presentation currency;
- payment-method capability and token scope;
- authorization/capture/refund/dispute support;
- SCA/3DS and redirect capability;
- amount/velocity/provider limits;
- planned outage, circuit state, and controlled rollout;
- deterministic routing priority or approved experiment assignment.

Credentials live in a secrets manager and are referenced by versioned secret aliases, not stored in
the registry row. One webhook endpoint maps deterministically to an account and accepted signing-key
versions.

### Smart routing safety

Initial routing is deterministic. Later optimization may estimate approval probability, cost,
latency, or reliability, subject to legal entity, method, currency, token, risk, and contractual
constraints. Record eligible candidates, selected route, policy/model version, and reason codes.

Failover is allowed before submission or after the previous operation is proven to have no effect.
It is forbidden merely because a response timed out. Provider health can stop new attempts without
changing existing operation ownership.

### Provider migration

Use staged routing percentages for new operations, dual webhook acceptance during credential/account
transition, and old-account reconciliation until all refunds/disputes/settlements age out. Existing
authorizations, captures, refunds, and disputes stay with the provider/account that owns them. A
provider migration does not rewrite historical references.

## Outbound operation execution

### Command transaction

For an authorize/sale/capture/refund command:

1. Authenticate the service/actor and authorize the booking/resource operation.
2. Resolve or create the client/domain idempotency record.
3. Load the immutable obligation, schedule, approved refund decision, and risk/policy versions.
4. Lock the obligation and relevant successful/in-flight operations in canonical order.
5. Validate state, exact amount/currency, deadline, token/provider eligibility, and cumulative limit.
6. Reserve the permitted amount and create `READY_TO_SUBMIT` operation with stable provider key.
7. Write outbox work/fact and commit.

### Submission fence

A worker claims the operation using an optimistic state/version or lease/fencing token. Immediately
before the network call it moves the operation to `SUBMITTING` in a short transaction. The same
operation ID and provider key are used on every safe transport retry.

After response:

1. start a new short transaction;
2. lock operation and parent obligation;
3. append a normalized observation;
4. apply the transition if it is new and valid;
5. update derived totals and release/consume reservations;
6. write committed payment facts to outbox;
7. commit before notifying booking, finance, or the client.

If the process crashes after send and before result persistence, recovery queries the provider by
provider reference, metadata, or idempotency key according to its contract. It does not create a new
operation.

### Retry scheduling

Classify errors as:

- `REJECTED_BEFORE_SUBMISSION`: safe to correct/retry without uncertainty;
- `KNOWN_RETRYABLE_NO_EFFECT`: provider contract proves no financial effect; retry same key;
- `PENDING`: provider accepted asynchronous work; wait/query;
- `UNKNOWN`: may have taken effect; query/reconcile only;
- `KNOWN_TERMINAL`: finish operation and allow new guest intent where policy permits.

Use exponential backoff with jitter, provider rate-limit hints, maximum age, and a dead-letter/manual
review path. Attempt count is not the sole termination condition; contractual deadline and outcome
certainty matter.

## Webhook ingestion and event ordering

### Ingress boundary

Webhook handling uses the exact bounded raw request bytes required by the provider signature scheme:

1. terminate TLS only at approved infrastructure and enforce endpoint/body/rate limits;
2. resolve provider account and active/grace verification keys from the endpoint context;
3. verify signature, signed timestamp/nonce, algorithm, and covered body before business processing;
4. reject invalid signatures without disclosing verification details;
5. parse the minimal event envelope and persist an inbox row before applying domain changes;
6. deduplicate by provider account plus provider event ID;
7. acknowledge a valid duplicate without repeating effects;
8. process synchronously only within a strict budget or enqueue durable processing;
9. mark processed only in the same transaction that applies the local effect and writes outbox.

Where a provider uses standardized HTTP signatures, [RFC 9421](https://www.rfc-editor.org/rfc/rfc9421.html)
explains that the application profile must define required covered components and signature
parameters. Provider-specific canonicalization remains in the adapter. TLS is still required.

Store a payload digest and normalized fields by default. A bounded encrypted raw payload may be kept
in a segregated restricted store only when provider replay, dispute, or audit requirements justify
it, with explicit retention and deletion. Raw bodies, signatures, and secrets never enter ordinary
application logs or analytics.

### Inbox lifecycle

Recommended states:

```text
RECEIVED -> PROCESSING -> PROCESSED
                     \-> RETRYABLE_FAILED -> PROCESSING
                     \-> DEAD_LETTER
RECEIVED -> IGNORED_SUPPORTED_UNKNOWN_TYPE
invalid signature -> rejected security record, never a trusted provider event
```

The existing `RECEIVED | PROCESSED | FAILED` state is a foundation. A forward migration should add
account scope, processing lease/attempts, normalized object reference, payload digest, verification
metadata, retry time, and dead-letter/ignored distinctions.

### Ordering and stale events

Do not assume webhook delivery order or exactly-once delivery. Reducer rules:

- identify the provider object and internal operation before transition;
- compare provider resource version/sequence where guaranteed, otherwise retrieve current state;
- append stale evidence but do not regress a terminal or higher-precedence operation fact;
- allow a later refund/dispute fact to coexist with capture rather than rewriting capture failure;
- quarantine amount, currency, merchant-account, or object-ownership mismatch;
- treat an unknown event type as observable/ignored, not webhook failure and not business success;
- replay through the same reducer and consumer inbox keys.

## Refund execution

### Separation of decision and movement

Cancellation, modification, support, or dispute policy produces an immutable refund instruction:

The detailed cancellation/modification calculation, allocation, approval, and instruction contract
is defined in
[`cancellation-modification-and-refund.md`](cancellation-modification-and-refund.md). Payment consumes
that result and never reruns its policy.

```text
refund_instruction_id
booking_id and financial allocation version
beneficiary guest
approved amount/currency and line allocation
funding allocation and tax/ledger references
reason code, policy/override version, actor/approval
execution deadline and idempotency identity
```

Payment selects eligible captures and executes the exact instructed amount. It must not recalculate
penalties, host impact, platform fee retention, tax, or promotion reversal.

### Transactional limit

Under a lock on the obligation/capture allocation:

```text
refundable captured amount
  = eligible successful captures
  - successful refunds
  - active refund reservations
  - provider reversals already credited
```

Create a refund reservation and operation atomically. On known terminal failure, release the
reservation. On unknown/pending, keep it reserved until provider query or reconciliation resolves
the operation. This prevents two concurrent partial refunds from exceeding captured funds.

### Multi-capture allocation

When an instruction spans several captures, create deterministic child operations ordered by
schedule component and capture time, subject to provider rules. The parent refund execution is
complete only when the sum of successful child results equals the instruction or an explicit partial
failure is escalated. Do not claim a full refund from one successful child.

### Refund lifecycle

```text
APPROVED -> RESERVED -> SUBMITTING -> PENDING -> SUCCEEDED
                                 \-> FAILED_RETRYABLE
                                 \-> FAILED_FINAL
                                 \-> UNKNOWN
SUCCEEDED may later have provider reversal/dispute evidence handled additively
```

A cancelled booking can remain `REFUND_PENDING`; inventory release must not wait on PSP completion.
Guest messaging distinguishes refund approved/submitted from funds received, because downstream bank
timing is not controlled by Room Booking.

## Disputes, chargebacks, and retrieval requests

Payment owns ingestion and normalization of the provider case. The dispute/support domain owns case
strategy, evidence quality, guest/host communication, and authorized representment. Finance owns
chargeback, fee, recovery, reserve, and loss postings.

Recommended separate state:

```text
INQUIRY_OR_RETRIEVAL
  -> ACTION_REQUIRED
  -> EVIDENCE_SUBMITTED
  -> UNDER_REVIEW
  -> WON | LOST | ACCEPTED | EXPIRED
```

For every provider dispute retain provider account/case/reference, disputed capture, amount/currency,
reason category, response deadline, evidence requirements, safe status history, fee observations,
and outcome. Deadline workers page owners before expiration. Evidence exports are allowlisted,
malware-scanned where files are involved, immutable after submission, and minimized to what the
case permits.

Hard rules:

- a dispute does not mutate the original successful capture into failure;
- a chargeback is not modeled as a refund;
- provider debit and fee are external observations until finance posts them;
- a refund and dispute can race, so reconciliation detects double-credit exposure;
- automated evidence generation may assemble verified booking facts, but consequential submission
  follows approved policy and access control;
- no LLM may invent evidence, legal claims, identity facts, or provider outcomes.

## Orphan transactions and reconciliation

### Operational reconciliation

Payment reconciliation compares:

- internal operations without a terminal provider fact;
- provider objects referenced by internal records;
- provider objects carrying internal operation/booking metadata;
- webhook inbox gaps and retrieval results;
- captures, refunds, reversals, and dispute states;
- amount, currency, provider account, timestamps, and identifiers.

Outcomes:

```text
MATCHED
TIMING_DIFFERENCE
MISSING_PROVIDER
MISSING_INTERNAL_OR_ORPHAN
AMOUNT_MISMATCH
CURRENCY_MISMATCH
ACCOUNT_MISMATCH
STATE_MISMATCH
DUPLICATE_SUSPECTED
```

### Orphan handling

An orphan provider transaction is never attached to a booking from fuzzy matching alone. Try exact
internal operation ID metadata, provider request key, provider reference, and merchant account. If
one authoritative mapping cannot be proven:

1. quarantine the transaction;
2. block automatic booking confirmation and dependent payout;
3. open a restricted reconciliation case;
4. obtain provider evidence and internal timeline;
5. attach through an audited repair command or issue an approved idempotent refund/void;
6. post finance effects through normal ledger commands.

Never edit provider references in SQL to make reports balance.

### Reconciliation ownership boundary

Payment owns operation-to-provider matching and outcome repair. Finance owns provider settlement,
fees, clearing balances, bank movement, and ledger mismatches. The two systems share immutable
provider observation IDs and reconciliation cases without duplicating authority. The finance side is
defined in
[Ledger, reconciliation, and host payout](ledger-reconciliation-and-host-payout.md).

Run near-real-time status recovery for stuck operations and scheduled daily/provider-cycle imports
for completeness. Compare counts and amounts; age exceptions by monetary materiality and booking
impact. Absence of a webhook is not proof of absence at the provider.

## Provider outage and degraded modes

Provider health is measured per account, operation, method, and region. A circuit breaker can stop
new submissions while allowing webhook ingress, queries, refunds, and reconciliation through
separate controls.

Degradation policy:

| Condition | New guest collection | In-flight operations | Refunds | Existing bookings |
| --- | --- | --- | --- | --- |
| Elevated errors with known no-effect | Backoff or route eligible new attempts | Retry same keys | Continue/capacity reserve | Preserve state |
| Timeouts/unknown outcomes | Stop blind resubmission | Query/reconcile | Query before replacement | Keep pending until booking deadline policy |
| Webhook outage only | Continue only if query capacity is proven | Poll authoritative objects | Poll/queue | Recovery worker closes gap |
| Credential/config failure | Disable affected account | Quarantine/query through repaired config | Queue safely | Never mark paid/failed by assumption |
| Provider-wide outage | Disable new attempts or use alternate route only pre-submission | Keep provider ownership | Queue unless emergency approved alternative exists | Booking applies explicit hold/expiry policy |

Kill switches are scoped independently for authorize/sale, capture, refund, webhook processing,
query, route, and provider account. Disabling capture or refunds requires incident ownership and
queue visibility; it must not discard durable instructions.

## Conceptual data model

### Current tables

`payment_attempts` currently combines the attempt, requested operation, and final collection result.
Its statuses are `CREATED`, `REQUIRES_ACTION`, `SUCCEEDED`, `FAILED`, and `CANCELLED`.

`refunds` currently represents one refund attempt with `PENDING`, `SUCCEEDED`, or `FAILED` and no
approved-entitlement reference or refund reservation.

`payment_webhook_events` currently deduplicates `(provider, provider_event_id)` and records a coarse
processing result.

Keep these tables operational during migration. Do not reinterpret historic `SUCCEEDED` rows as
authorization or capture without provider evidence and an explicit backfill rule.

### Proposed `payment_provider_accounts`

- internal ID, stable provider code, merchant legal entity, market/currency/method capabilities;
- status, routing priority/weight, environment, webhook endpoint identity;
- credential and webhook-secret aliases/versions, never secret values;
- effective period, configuration version, created/approved actor, and audit metadata.

A registry/FK replaces fixed provider check-constraint expansion as providers grow. Configuration
activation should use maker-checker approval and a health validation.

### Proposed `payment_orders` and `payment_schedule_items`

`payment_orders` represents the collection obligation:

- booking/checkout, payer, legal entity, purpose;
- obligated amount/currency and immutable financial snapshot version;
- capture policy, state, due/expiry timestamps, version;
- derived captured/refunded/disputed/remaining projections;
- idempotency/correlation and policy provenance.

`payment_schedule_items` contains amount, due instant/local due context, confirmation/default
condition, sequence, state, and replacement/version relationships. A unique `(payment_order_id,
sequence)` and exact sum validation protect the schedule.

### Evolved `payment_attempts`

Add:

- `payment_order_id` and optional schedule item;
- provider account rather than provider name alone;
- payment-method reference and method family;
- attempt state/failure category/action deadline;
- routing/risk/policy versions;
- canonical command idempotency identity and request hash;
- correlation, actor/channel, terminal timestamp, and safe display fields.

Provider references should move to operations/provider objects where their semantics are known.

### Proposed `payment_operations`

Store the operation fields defined above. Important constraints/indexes:

- unique scoped internal idempotency identity;
- unique `(provider_account_id, provider_request_key)`;
- unique provider operation/object reference when provider contract guarantees uniqueness;
- positive monetary amount for monetary operations;
- operation/currency/parent consistency validated transactionally;
- indexes for ready/retry work, unknown/pending aging, attempt history, and provider lookup;
- non-negative version and lease/fencing metadata.

### Proposed `payment_operation_observations`

Append-only provider evidence with source `API_RESPONSE`, `WEBHOOK`, `QUERY`, or
`RECONCILIATION_IMPORT`; provider IDs/account, normalized status/amount/currency, provider occurrence
and receipt timestamps, payload digest/restricted blob reference, verification metadata, and reducer
result. Deduplicate using the strongest provider identity plus source semantics.

### Proposed `payment_method_references`

Guest/provider-account scoped opaque token, safe display metadata, capabilities, consent/mandate
reference, status, usage timestamps, revocation, and retention category. Encrypt tokens at the
application/storage layer where threat modeling requires it; never expose them in list responses.

### Evolved `refunds` and refund allocation

Add immutable refund instruction ID/version, booking/payment order, approved amount/currency,
reservation state, provider account, parent/child execution, operation reference, allocation digest,
approval actor, failure category, and correlation. A child allocation table maps refunded amount to
successful captures and prevents double consumption.

### Proposed dispute and reconciliation tables

`payment_disputes`, `payment_dispute_events`, and `payment_dispute_evidence` retain provider case,
deadlines, status, amount, capture, evidence manifest, submission, and outcome.

`payment_reconciliation_runs`, `payment_reconciliation_observations`, and shared
`reconciliation_cases` retain source period/watermark, immutable external row digest, matching result,
materiality, owner, evidence, and resolution. Finance may own the shared case aggregate.

### Cross-cutting infrastructure tables

- command idempotency records scoped by subject/resource/operation;
- transactional outbox with event ID/schema/version/correlation;
- consumer inbox where payment consumes booking/refund/risk commands asynchronously;
- immutable payment timeline projection for support;
- audit records for provider configuration and privileged commands.

### Migration and backfill plan

1. Add new tables and nullable links in a forward migration; keep migration `005` unchanged.
2. Backfill one `payment_order` per existing booking with payment rows using booking totals/currency,
   marking provenance as legacy.
3. Map legacy attempts to one conservative `SALE_OR_CAPTURE_LEGACY` operation. Treat `SUCCEEDED` as
   captured only if launch provider evidence/business history establishes that meaning; otherwise
   queue review.
4. Map refunds to operations and capture allocations; validate cumulative totals.
5. Add provider-account rows for `STRIPE`, `MOMO`, `VNPAY`, and `MANUAL` only where configured.
6. Dual-read/dual-write behind a feature flag and compare derived legacy/new states.
7. Remove or relax `uk_payment_attempts_one_success` only after schedule/capture invariants are live,
   backfill is reconciled, and rollback behavior is documented.
8. Move authority to new operation/obligation state, retain legacy columns for compatibility, then
   remove them only in a later reviewed migration.

Backfill scripts must be restartable, bounded, observable, and never create provider calls.

## Service boundaries

Logical boundaries can remain packages/modules in the Spring Boot modular monolith.

### `PaymentOrchestrationService`

Creates/replays attempts and operations from authoritative obligations, coordinates customer action,
and emits provider-independent facts. It never calculates the booking total.

### `CollectionScheduleService`

Creates immutable payment orders/schedule items from accepted booking terms and determines which
component is due. Booking owns whether satisfaction confirms/cancels the contract.

### `PaymentOperationExecutor`

Claims durable work, calls one provider adapter with the stable key, classifies outcome certainty,
and persists observations. It has no permission to change booking or ledger rows.

### `PaymentProviderRegistry` and provider adapters

Resolve versioned eligible accounts/capabilities and translate provider contracts. Provider-specific
DTOs, signatures, SDKs, and status codes do not leak into controllers or booking services.

### `PaymentWebhookIngressService`

Verifies exact raw requests, stores/deduplicates the provider inbox, and schedules processing. Its
public network surface is isolated and heavily rate-limited.

### `PaymentEvidenceReducer`

Applies response, webhook, query, and reconciliation observations deterministically under lock. It
prevents stale regression and produces committed domain facts.

### `PaymentMethodReferenceService`

Registers, lists safe metadata, revokes, and validates provider-vault tokens and consent. It cannot
receive raw instrument credentials.

### `RefundExecutionService`

Consumes an approved refund instruction, reserves available refundable capture, executes provider
refund operations, and reports movement. It does not decide entitlement.

### `PaymentDisputeGateway`

Imports provider cases/deadlines and submits authorized evidence/action from the dispute domain. It
does not decide legal strategy or ledger loss.

### `PaymentRecoveryService`

Finds stuck/unknown operations, queries providers, replays safe inbox/outbox work, and opens
exceptions. Recovery uses the same commands and reducers as live traffic.

### `PaymentReconciliationService`

Matches operational provider evidence to operations. Finance reconciliation consumes its facts for
ledger/clearing/bank comparisons.

### Neighbor contracts

| Neighbor | Supplies to payment | Consumes from payment |
| --- | --- | --- |
| Booking/inventory | Checkout, obligation request, confirmation condition, hold deadline | Authorized/captured/failed/late outcome |
| Pricing/quote | Immutable amount, currency, schedule, line allocation | No provider state needed for recalculation |
| Risk/trust | Versioned allow/challenge/block and routing constraints | Attempt/outcome signals with minimized data |
| Cancellation/support | Approved refund instruction and reason | Submitted/succeeded/failed refund facts |
| Finance/ledger | Posting acknowledgements and reconciliation case ownership under the [finance design](ledger-reconciliation-and-host-payout.md) | Capture/refund/reversal/dispute provider facts |
| Notifications | Templates/preferences only | Committed user-visible state changes |

## API behavior

Public paths are illustrative and versioned. Prefer checkout/booking-scoped navigation so users do
not need provider identifiers.

### Guest operations

```text
POST /api/v1/booking-checkouts/{checkoutId}/payment-attempts
GET  /api/v1/payment-attempts/{attemptId}
POST /api/v1/payment-attempts/{attemptId}/status-refreshes
GET  /api/v1/me/payment-methods
POST /api/v1/me/payment-methods/provider-setup-sessions
DELETE /api/v1/me/payment-methods/{paymentMethodId}
GET  /api/v1/bookings/{bookingId}/payment-schedule
```

Start request:

```json
{
  "paymentMethodReferenceId": "pmr_01...",
  "returnContext": "WEB_CHECKOUT",
  "expectedPaymentOrderVersion": 3,
  "idempotencyKey": "guest-generated-opaque-key"
}
```

The API derives booking, amount, currency, provider candidates, legal entity, and operation type.
`returnContext` selects a server-configured return/deep-link policy; it is not an arbitrary URL.

Start/read response:

```json
{
  "attemptId": "payatt_01...",
  "paymentOrderId": "payord_01...",
  "status": "PROCESSING",
  "amountMinor": 4599000,
  "currency": "VND",
  "action": null,
  "holdExpiresAt": "2026-09-06T09:12:00Z",
  "nextRecommendedAction": "WAIT",
  "pollAfterMs": 1500,
  "version": 4
}
```

The response never exposes secret keys, raw tokens, provider debug payloads, full provider IDs where
unnecessary, fraud rationale, or other guests' payment data. A `202 Accepted` is suitable for
processing/unknown work; `200 OK` returns a replay/current projection; creation may return `201
Created`.

Status refresh is a rate-limited hint that enqueues or performs a safe provider query for the same
attempt. It cannot create a new charge or accept a client-declared result.

### Internal booking and refund operations

```text
POST /internal/payment-orders
POST /internal/payment-orders/{id}/capture-requests
POST /internal/payment-orders/{id}/void-requests
POST /internal/refund-instructions/{id}/executions
GET  /internal/payment-orders/{id}/verified-state
```

Internal calls use service identity, resource authorization, stable command ID, canonical request
hash, correlation/causation, and expected version. Prefer durable event/command handoff for recovery;
an HTTP response alone is not the saga record.

### Provider webhook endpoints

```text
POST /webhooks/payments/{opaqueEndpointId}
```

The opaque endpoint resolves provider account and signature policy. It does not accept provider
account selection from an untrusted query parameter. Respond quickly after durable verified inbox
storage; processing can continue asynchronously. Invalid signatures receive a generic error and
security metric. Valid duplicates receive the provider-required acknowledgement.

### Host and support visibility

Hosts may see whether a booking payment condition is satisfied, pending, failed, or refunded where
contractually appropriate, but not guest instrument/provider details. Support receives a redacted
timeline. Privileged finance/risk roles can query provider references, operations, observations,
refund allocations, and reconciliation cases according to scoped permissions.

Refunds should normally be initiated through cancellation/modification/support commands, not a
general-purpose public payment refund endpoint.

### Error semantics

| Condition | HTTP | Stable code | Retry/action |
| --- | --- | --- | --- |
| Unknown/unauthorized resource | 404/403 | `PAYMENT_RESOURCE_NOT_FOUND` / `PAYMENT_ACCESS_DENIED` | Stop; avoid existence leak |
| Checkout/obligation expired | 409 | `PAYMENT_WINDOW_EXPIRED` | Re-enter booking/quote flow |
| Booking state incompatible | 409 | `PAYMENT_BOOKING_STATE_CONFLICT` | Refresh booking |
| Stale expected version | 409 | `PAYMENT_VERSION_CONFLICT` | Read current state |
| Reused key with different input | 409 | `IDEMPOTENCY_KEY_REUSED` | New key only for new intent |
| Already processing | 202/409 | `PAYMENT_ALREADY_PROCESSING` | Poll current attempt; do not resubmit |
| Customer action required | 200 | `PAYMENT_ACTION_REQUIRED` | Complete bounded provider action |
| Method declined/invalid | 422 | `PAYMENT_METHOD_DECLINED` / `PAYMENT_METHOD_INVALID` | Use safe alternative |
| Risk denied | 422 | `PAYMENT_NOT_PERMITTED` | Generic message; no rule disclosure |
| Provider route unavailable before submit | 503 | `PAYMENT_METHOD_TEMPORARILY_UNAVAILABLE` | Retry same command later |
| Provider result unknown | 202 | `PAYMENT_OUTCOME_PENDING` | Poll; server queries/reconciles |
| Amount/currency mismatch | 409/500 | `PAYMENT_AMOUNT_MISMATCH` | Stop and reconcile |
| Capture exceeds limit | 409 | `CAPTURE_LIMIT_EXCEEDED` | Correct internal instruction |
| Refund exceeds available | 409 | `REFUND_EXCEEDS_AVAILABLE_AMOUNT` | Recalculate entitlement/allocation |
| Invalid webhook signature | 400/401 | Internal `WEBHOOK_SIGNATURE_INVALID` | Provider retry only after configuration fix |
| Unsupported valid event | 2xx | Internal `WEBHOOK_EVENT_IGNORED` | Observe; no business retry |

Provider codes are mapped and redacted. Do not promise the guest a retry is safe when outcome is
unknown. HTTP status expresses request handling; the body state expresses asynchronous payment truth.

## Event contracts

Events are committed past-tense facts, not requests disguised as events. Publish through a
transactional outbox. Consumers deduplicate by event ID and, for monetary effects, by their own
business source key.

Representative events:

```text
PaymentOrderCreated
PaymentScheduleItemDue
PaymentAttemptStarted
PaymentCustomerActionRequired
PaymentOperationSubmitted
PaymentOutcomeBecameUnknown
PaymentAuthorized
PaymentCaptureRequested
PaymentCaptured
PaymentFailed
PaymentAuthorizationVoided
PaymentAuthorizationExpired
RefundExecutionStarted
RefundSubmitted
RefundSucceeded
RefundFailed
ProviderPaymentEventAccepted
PaymentReconciliationMismatchDetected
OrphanProviderTransactionDetected
PaymentDisputeOpened
PaymentDisputeUpdated
PaymentDisputeResolved
```

`PaymentCaptured` minimal payload:

```json
{
  "eventId": "evt_01...",
  "schemaVersion": 1,
  "occurredAt": "2026-09-06T09:04:22Z",
  "paymentOrderId": "payord_01...",
  "attemptId": "payatt_01...",
  "operationId": "payop_01...",
  "bookingId": "...",
  "amountMinor": 4599000,
  "currency": "VND",
  "providerAccountId": "pspa_01...",
  "paymentOrderVersion": 5,
  "correlationId": "checkout_01...",
  "causationId": "provider-observation-id"
}
```

Do not put payment-method tokens, raw provider payloads, secret-bearing redirect data, full billing
details, or risk features in events. Consumers fetch more detail through authorized APIs.

Booking consumes authorized/captured facts according to its confirmation policy and emits an
accepted/rejected transition. Finance consumes captured/refunded/void/reversal/dispute facts and
posts with a unique `(source_event_id, posting_type, version)` key. Payment must tolerate either
consumer being temporarily unavailable.

Event evolution is additive within a schema version. Breaking semantics require a new version,
dual-publish/consume migration, and replay plan. Occurrence time is not delivery order.

## Concurrency and idempotency

### Lock and constraint strategy

Use short database transactions and canonical lock order:

```text
payment_order
  -> schedule_item
  -> payment_attempt
  -> operation/refund reservation
  -> idempotency record
```

Booking/inventory locks are not held while payment locks or PSP calls occur. Cross-domain races are
resolved by versioned commands and compensations.

Database defenses:

- unique scoped command identity plus canonical request hash;
- unique provider request key per provider account;
- unique provider reference per provider account where guaranteed;
- unique webhook event per provider account/event ID;
- unique observation identity/digest according to source;
- one active submission lease/fencing version per operation;
- non-negative amounts and consistent currency;
- transactional cumulative capture/refund reservations;
- unique ledger consumer source key outside payment;
- optimistic versions on mutable projections.

### Race outcomes

| Race | Winner rule | Loser/recovery behavior |
| --- | --- | --- |
| Duplicate start-payment commands | First scoped key insert | Same input replays; mismatch conflicts |
| Two attempts capture same obligation | Locked remaining amount/reservation | Excess attempt is rejected/voided; reconcile external anomaly |
| Worker retry versus webhook | Operation lock/reducer precedence | Both append/dedupe evidence; one transition/event |
| Capture versus void | Provider operation truth plus locked authorization reservation | Query ambiguous state; never assert both against same available amount |
| Two partial refunds | Locked refundable reservation | Second sees reduced availability |
| Refund versus dispute | Separate facts and finance policy | Detect double-credit exposure; do not erase either |
| Booking expiry versus payment capture | Booking/claim transition lock | Confirm if valid winner; otherwise compensate payment |
| Webhook replay during reprocessing | Inbox unique identity and lease | Same reducer outcome; no duplicate outbox effect |
| Provider failover versus timeout | Submission fence/outcome certainty | Unknown original retains ownership; no failover |

### Idempotency retention

Retain monetary operation identities at least as long as provider replay, refund, dispute,
reconciliation, and legal/audit windows require. Do not expire them with a short API cache. If a
response projection contains sensitive or stale action data, retain the resource identity/result but
regenerate only safe current response fields.

## Security, privacy, and access control

### Actor and resource authorization

- Guests can start/read only payment attempts for their own eligible checkout/booking.
- Hosts cannot view guest payment method, provider details, decline reasons, or risk signals.
- Booking/payment internal commands require service identity and resource-bound permission.
- Support permissions separate read timeline, request retry, request refund, and view provider
  references.
- Finance permissions separate reconciliation, refund approval, adjustment, and export.
- Provider configuration, secret rotation, routing changes, large/manual refund, orphan attachment,
  and force resolution require privileged audited roles; consequential thresholds use maker-checker.

Unknown identifiers should not reveal whether another user's payment exists.

### Data classification and minimization

Classify payment tokens, customer/provider IDs, billing details, IP/device signals, authentication
evidence, disputes, and reconciliation files. Define purpose, access, encryption, retention,
deletion/anonymization, and analytics policy for each class.

Never log or emit:

- PAN, CVV, bank credentials, PIN, track data, secret keys, or full token values;
- raw `Authorization`, cookie, signature, or redirect query headers;
- unbounded request/provider bodies;
- detailed issuer decline/risk rules in guest-visible messages.

Use structured allowlist logging with opaque internal IDs, category, state, latency, correlation, and
truncated/hash references where appropriate. Production debugging must not enable arbitrary body
logging.

### Secrets and webhook security

- Store API and webhook keys in a managed secret system with environment/account isolation.
- Support active and grace key versions for rotation; audit retrieval and rotation.
- Restrict egress destinations and use TLS/provider certificate guidance.
- Verify webhook signatures over exact required bytes before trusting content.
- Enforce replay windows/nonces where the provider scheme supports them.
- Rate-limit endpoints, cap bodies, reject unsupported content types, and monitor signature failures.
- Do not fetch arbitrary URLs from webhook payloads.

### Compliance boundary

PCI DSS scope, SCA obligations, consumer disclosures, stored-credential consent, refund timing,
chargeback process, data residency, and retention differ by integration and market. Compliance/legal
owners must approve the launch architecture and provider contracts. This document does not claim a
specific self-assessment questionnaire or universal regulatory exemption.

### Abuse and fraud

Rate-limit by guest, device, instrument fingerprint/reference, IP/network risk, booking, amount,
provider account, and market. Detect card testing, scripted challenge loops, refund abuse, account
takeover, enumeration, stolen tokens, and coordinated attempts. Controls must avoid leaking which
signal triggered a block and must have appeal/manual review appropriate to impact.

## Observability and operations

### Correctness and business metrics

- obligation amount versus captured/refunded/disputed amount;
- duplicate effect count and prevented-idempotency replay count;
- captures without eligible booking confirmation and confirmed bookings without required payment;
- late captures after hold/booking expiry and compensation age/value;
- concurrent capture/refund limit conflicts;
- orphan/missing/mismatch count and amount by provider account;
- unresolved unknown outcomes by age and amount;
- refund instruction-to-submit and submit-to-success latency;
- dispute deadlines at risk and outcome/value;
- provider approval, challenge, decline, abandonment, and recovery funnel by method/market;
- deposit/balance collection and default rate when schedules launch.

### Technical metrics

- provider request latency/error/timeout/rate-limit by operation/account;
- webhook signature failure, duplicate, ingest latency, processing lag, retries, dead letters;
- status-query volume, result, age, and throttling;
- operation queue depth, oldest ready/unknown item, leases reclaimed, retry count;
- database lock wait, deadlock/serialization retry, optimistic conflicts, constraint failures;
- outbox/inbox backlog and oldest age;
- provider circuit state and routing distribution;
- reconciliation watermark freshness and import failures.

Metric dimensions must be cardinality-bounded; do not label metrics with booking, operation, guest,
token, or raw provider IDs.

### Initial SLO candidates

- 100% of externally submitted operations have a durable internal operation ID and provider key;
- zero known duplicate capture/refund caused by platform replay;
- 99.9% of valid webhook envelopes durably acknowledged within the provider retry budget;
- 99.9% of verified terminal observations reflected internally within five minutes under normal
  provider delivery;
- 100% of captures/refunds included in scheduled reconciliation by the next provider cycle;
- no confirmed booking remains without its configured payment condition beyond the recovery SLA;
- no late captured payment remains without confirmation or compensation case beyond the incident SLA.

Exact availability/latency targets depend on the launch provider and method. Financial correctness
and explainable pending state take precedence over a false immediate failure.

### Dashboards, alerts, and runbooks

Dashboards separate provider acceptance from internal completion and counts from monetary value.
Alert on unknown-outcome age, capture-without-booking, booking-without-payment, amount/currency
mismatch, webhook lag/signature spike, refund backlog, reconciliation freshness, orphan value,
provider circuit changes, and dispute deadlines.

Runbooks cover:

- PSP timeout/unknown outcome;
- webhook secret rotation or signature failure spike;
- delayed/missing webhook;
- provider outage and route disablement;
- late capture after booking expiry;
- stuck authorization/capture/refund;
- amount/currency/account mismatch;
- orphan provider transaction;
- duplicate-suspected charge;
- refund/dispute double-credit exposure;
- reconciliation import failure;
- credential compromise and evidence preservation.

Manual tools invoke idempotent commands, preview impact, require reason/evidence, and show the resulting
timeline. They do not offer arbitrary status editing.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Client disconnect after local attempt commit | Same command key returns the existing attempt/action/current state |
| Crash before provider submission | Worker claims the durable `READY_TO_SUBMIT` operation |
| Crash during/after request send | Mark/recover as unknown and query same reference/key; never create blind replacement |
| Provider proves no-effect transient error | Retry same operation/key with bounded backoff |
| Provider returns malformed or mismatched response | Quarantine, query authoritative object, alert; do not confirm |
| Customer abandons 3DS/redirect | Expire attempt according to deadline; release booking hold by booking policy |
| Browser reports success but no provider evidence | Remain pending and query; never trust browser |
| Valid webhook before synchronous response | Inbox reducer may complete operation; response application replays current state |
| Duplicate webhook | Acknowledge; unique inbox creates no duplicate transition/effect |
| Out-of-order stale webhook | Append evidence; do not regress; retrieve current provider object if needed |
| Unknown valid event type | Mark ignored/observed and alert on volume; no business transition |
| Webhook secret misconfigured | Reject untrusted events, page owner, query impacted operations after repair |
| Booking hold expires before verified success | Booking race selects winner; captured result is compensated if confirmation fails |
| Capture succeeds but booking consumer is down | Outbox/recovery retries; block dependent payout and alert age |
| Ledger consumer fails after capture | Payment remains captured; durable event retries; finance blocks payout/reconciles |
| Two concurrent refund requests | Locked reservation enforces remaining refundable amount |
| Refund timeout | Keep amount reserved, query provider, do not submit second refund |
| Provider refund succeeds but webhook is lost | Query/reconciliation observes success and emits one fact |
| Provider unavailable | Stop eligible new work, preserve pending operations, use only safe pre-submit alternate routing |
| Provider rate limits query/webhook processing | Respect hints/backoff, prioritize expiring/high-value unknowns, expose lag |
| Orphan provider capture | Quarantine; exact-match investigation; confirm only through booking policy or refund by approval |
| Reconciliation feed unavailable | Preserve internal evidence, mark watermark stale, retry and alert |
| Database deadlock/serialization abort | Bounded retry under same command/operation identity |
| Outbox relay unavailable | Local payment truth remains committed; retry and alert oldest age |
| Clock skew | Use database/injected clock for deadlines; monitor skew; provider timestamp is evidence, not local expiry authority |

## Testing and verification

### Deterministic state and amount tests

- Every allowed and forbidden obligation, attempt, and operation transition.
- Authorize/capture, direct sale, void, partial capture, authorization expiry, and late evidence.
- Integer minor-unit calculations and currency equality; zero/negative/overflow boundaries.
- Successful plus reserved capture never exceeds obligation.
- Successful plus reserved refunds/reversals never exceed refundable capture.
- Multiple schedule items reconcile exactly to the obligation.
- Refund allocation across one and multiple captures is deterministic.
- Stale event cannot regress terminal state; refund/dispute coexists with capture.

Use an injected clock for operation, hold, challenge, authorization, schedule, retry, and dispute
deadlines.

### Real-database and concurrency tests

Use real PostgreSQL for locks, unique constraints, worker claiming, and migration behavior:

- duplicate same/different-body command keys;
- two attempts competing for remaining obligation;
- capture versus void;
- worker response versus webhook;
- two partial refunds;
- refund versus reconciliation update;
- webhook duplicate and processing lease reclaim;
- booking expiry versus captured outcome through the integration boundary;
- outbox publish/replay and consumer inbox deduplication;
- deadlock/serialization bounded retry under stable identity.

### Provider contract tests

For each adapter, maintain provider-approved sandbox/fixture tests for:

- request mapping, signing/authentication, stable idempotency key, amount/currency;
- authorize, capture, sale, void, refund, retrieve, and unsupported capability;
- success, known decline, action required, pending, rate limit, malformed response, timeout, and
  unknown outcome;
- exact raw webhook signature verification, key rotation, replay window, duplicate, unknown type,
  and out-of-order events;
- provider object/reference uniqueness and metadata mapping;
- test/live environment separation and safe redaction.

Do not make the ordinary unit suite depend on live provider availability. Run gated sandbox contract
tests with non-production credentials and synthetic instruments.

### Recovery and reconciliation tests

- Crash before submission, after submission, after provider success, before local commit, and before
  outbox relay.
- Lost synchronous response plus webhook success; lost webhook plus query success.
- Unknown state aging and eventual terminal resolution.
- Exact match, missing provider, orphan, amount/currency/account/state mismatch, duplicate suspected,
  and timing difference.
- Restartable reconciliation import with stable watermark and duplicate external rows.
- Audited repair command and compensating void/refund.

### Security tests

- Guest/host/support/finance/provider-endpoint authorization and cross-resource isolation.
- No payment existence leak through IDs or error differences.
- Invalid/expired/replayed webhook signatures, wrong account secret, oversized body, content-type,
  and rate limits.
- Redirect/deep-link allowlist and open-redirect prevention.
- Log/event/metric snapshots contain no raw credential, token, signature, secret, or sensitive
  provider payload.
- Secret rotation overlap and revoked-key behavior.
- Card-testing velocity controls and safe error normalization.
- Reconciliation/evidence file access, malware handling, and audit.

### Property and model-based tests

Generate arbitrary valid command/event interleavings and verify:

- the platform emits at most one successful effect per operation identity;
- captured/reserved/refunded totals never exceed their bounds;
- replay is observationally equivalent to processing once;
- no event ordering converts unknown into unproven failure or capture into failure;
- every externally submitted operation is terminal, pending/unknown with scheduled recovery, or in an
  owned manual case;
- booking/payment/ledger state may temporarily differ but every allowed combination has a recovery
  path;
- a failed modification/payment attempt does not alter the original accepted contract.

### Operational game days

Exercise provider latency/outage, webhook loss, secret rotation failure, queue backlog, database
failover, clock skew, reconciliation-file delay, refund backlog, and late capture. Verify kill
switches, alerts, dashboards, support timeline, compensation, and post-incident accounting handoff.

## Caching, performance, and scaling

- PostgreSQL remains authoritative for obligation, operation, refund reservation, and inbox state.
- Cache only provider capability/routing configuration and redacted read projections with explicit
  version/TTL; never authorize, capture, refund, or confirm from cache.
- Index ready work by `(state, next_attempt_at, id)`, unknown/pending by age, webhook work by
  processing state/receipt time, provider lookup by account/reference, attempts by order/time, and
  refunds/disputes by deadline/state.
- Workers claim bounded batches with leases/fencing and `SKIP LOCKED` where suitable; external calls
  occur after transaction commit.
- Apply independent concurrency budgets per provider account/operation so status-query storms do not
  starve captures or refunds.
- Bound webhook body, evidence, reconciliation batch, status history, and support timeline queries.
- Keep raw/restricted artifacts outside hot relational rows when retention requires them.
- Separate webhook ingest latency from downstream processing so bursts can be acknowledged durably.
- Use provider rate-limit headers/contracts and jittered polling; never let every client poll trigger
  a provider query.
- Archive immutable observations according to compliance while retaining sufficient indexed summary
  for disputes/reconciliation.

Do not introduce Kafka, event sourcing, sharding, multi-region active-active payment writes, or a
separate payment microservice before measured throughput, ownership, isolation, regional, or
availability needs justify the operational cost. A modular monolith with outbox/inbox and provider
workers is the target deployment while those thresholds remain unmet.

## Appropriate use of AI

Potential machine-learning uses after deterministic telemetry is trustworthy:

- predict provider approval probability, latency, or cost for constrained smart routing;
- classify normalized retryability using provider response features with rule fallback;
- detect card testing, account takeover, anomalous refund behavior, orphan patterns, and provider
  drift;
- prioritize reconciliation cases and dispute deadlines by expected impact;
- estimate likelihood of installment default for a risk policy that is separately governed;
- summarize a redacted payment timeline for authorized support agents.

Prerequisites include point-in-time features, outcome labels corrected by later webhook/query and
reconciliation, provider/market/method stratification, calibration, drift monitoring, protected-data
review, model/version logging, deterministic fallback, shadow evaluation, and a kill switch.

Models may not:

- decide or fabricate amount, currency, authorization, capture, refund, dispute, or settlement truth;
- bypass cumulative limits, SCA/legal rules, provider capability, booking state, or database
  constraints;
- retry an unknown operation or silently switch its provider;
- infer payment success from client behavior;
- approve a refund, create evidence, or make a legal representment claim;
- expose fraud reasoning or discriminate using prohibited/sensitive attributes.

Routing optimization objective must include approval, duplicate/unknown risk, latency, cost,
chargeback/fraud outcome, guest experience, and operational reliability—not approval rate alone.
Use causal experiments for routing changes and keep a deterministic eligibility layer above the
model.

## Target-release dependencies and completion gates

Dependencies 0–5 are cumulative target-release requirements. Additional collection methods and
simultaneous provider routing are designed extensions, not missing correctness in the approved flow.

### Dependency 0 — Legal, product, security, and provider decisions

Decide merchant/legal entity, launch country/currency, one PSP/account, method, authorization versus
sale, capture timing, booking confirmation condition, hold/late-success policy, refund ownership,
PCI integration, SCA/3DS handling, retention, and operational roles. Produce ADRs, provider contract
matrix, threat model, data-flow diagram, and sandbox runbook.

Exit: accountable owners approve the end-to-end money/data flow and every unresolved target decision.

### Dependency 1 — Provider-independent obligation and operation foundation

Add forward migrations for provider accounts, payment orders, attempts/operations, scoped
idempotency, observations, outbox/inbox, and support timeline. Backfill existing rows conservatively
without provider calls. Implement deterministic state reducers and amount invariants.

Exit: legacy/new projections reconcile on representative data; every operation has stable identity,
amount authority, and recovery state.

### Dependency 2 — Approved Vietnam payment flow

Implement one adapter and method using hosted/SDK tokenization, start/read APIs, durable submission,
known decline, action-required/pending handling, verified capture/authorization, and booking
confirmation handoff. No multi-provider routing or installments.

Exit: one checkout can reach confirmation without raw credentials entering the backend, and client
replay/process restart cannot duplicate the provider effect.

### Dependency 3 — Webhook, query recovery, and outage controls

Add exact raw-body signature verification, account-scoped inbox, out-of-order reducer, status query,
unknown-outcome worker, dead-letter handling, scoped kill switches, provider dashboards, and runbooks.

Exit: duplicate/delayed/lost webhook, timeout, browser disconnect, provider outage, and crash game
days converge to one explainable outcome.

### Dependency 4 — Refund and finance handoff

Consume versioned refund instructions, reserve cumulative refundable amount, execute partial/full
refunds, compensate late booking failures, and emit capture/refund/reversal facts to idempotent ledger
consumers. Add restricted exception tools.

Exit: concurrent/replayed refunds cannot exceed capture; captured/refunded values reconcile with the
booking financial snapshot and ledger handoff.

### Dependency 5 — Operational reconciliation and disputes

Add provider status/transaction imports, orphan/mismatch cases, immutable repair workflow, dispute
ingestion/deadlines/evidence gateway, and finance reconciliation correlation.

Exit: every provider transaction in the reconciliation period is matched or assigned to an owned,
aged, auditable exception; dispute deadlines are monitored.

### Designed extension — Saved methods and scheduled collections

Add consented token references, deposit/balance schedule, dunning, authorization expiry, and default
handoff. Remove the legacy one-success-per-booking constraint only after new cumulative invariants and
backfill verification are authoritative.

Exit: multiple scheduled collections and retries reconcile exactly to one obligation without
changing accepted booking history.

### Measured-scale capability — Additional providers and smart routing

Add providers/methods one at a time through capability contracts, account-specific webhook and
reconciliation, pre-submit deterministic failover, then shadow/experiment-based constrained routing.
Maintain refund/dispute support for retired routes.

Exit: each route passes provider contract, security, recovery, reconciliation, and game-day gates;
optimization can be disabled without stopping the deterministic route.

## Verification checklist

### Functional and monetary correctness

- [ ] Client cannot choose amount, currency, provider account, operation type, or successful state.
- [ ] Obligation and schedule reconcile exactly to the accepted financial snapshot.
- [ ] Authorization, capture, sale, void, refund, and dispute remain distinct facts.
- [ ] Captured plus reserved amount never exceeds the permitted obligation.
- [ ] Refunded plus reserved amount never exceeds refundable capture.
- [ ] Booking confirms only from verified provider-independent fact and owns the inventory race.
- [ ] Payment state never substitutes for ledger, host payable, or platform revenue.

### Retry and recovery

- [ ] Same command/key/body replays; same key/different body conflicts.
- [ ] Every external retry uses the original operation and provider key.
- [ ] Timeout/crash-after-send enters unknown/query recovery, not blind retry/failover.
- [ ] Duplicate and out-of-order webhooks create one valid state effect.
- [ ] Late capture is either accepted by booking or receives one tracked compensation.
- [ ] Every nonterminal operation has scheduled recovery or an owned manual case.
- [ ] Reconciliation finds missing, orphan, duplicate-suspected, amount, currency, account, and state
  mismatches.

### Security and compliance

- [ ] Raw card/bank credentials and CVV never enter application storage, logs, events, or analytics.
- [ ] Token/provider-account scope, consent, revocation, encryption, retention, and access are defined.
- [ ] Webhooks verify exact required bytes, account/key version, replay controls, and body limits.
- [ ] Redirect/deep-link targets are allowlisted and browser success is never authoritative.
- [ ] Guest, host, support, finance, risk, and configuration permissions are isolated and audited.
- [ ] Provider/PCI/SCA/legal owners approve the exact launch integration and data flow.
- [ ] Large/manual refunds, orphan repair, provider routing/configuration, and force resolution use
  approval controls.

### Operations

- [ ] Dashboards separate initiated, submitted, unknown, authorized, captured, refunded, disputed,
  settled, and reconciled amounts.
- [ ] Alerts cover unknown age, late capture, booking/payment mismatch, webhook lag/signatures,
  refund backlog, orphan value, reconciliation freshness, and dispute deadlines.
- [ ] Kill switches independently control new collection, capture, refund, query, webhook processing,
  and provider route without discarding durable work.
- [ ] Support sees a redacted immutable timeline and can invoke only idempotent domain commands.
- [ ] Provider outage, webhook loss, crash, backlog, late capture, and credential-rotation game days
  pass before launch.

### Delivery and compatibility

- [ ] Migration `005` remains unchanged; all evolution uses forward migrations.
- [ ] Legacy `SUCCEEDED` meaning and one-success constraint have an explicit backfill/transition plan.
- [ ] No payment code is launched before quote/booking amount authority and inventory hold boundary.
- [ ] The approved provider/method integration proof passes before any required extension such as
  installments or measured-scale smart routing; it is not treated as a reduced product release.
- [ ] Provider retirement preserves refunds, disputes, webhook/query, and reconciliation for historic
  operations.

## Decisions required before implementation

Create Architecture Decision Records (ADRs) with owner, date, context, alternatives, decision,
consequences, rollout, and revisit trigger for:

1. Launch merchant/legal entity model, contracting party, and provider account owner.
2. First country, collection currency, presentation currency, provider, and payment method.
3. Hosted page, hosted fields, or native SDK integration and resulting PCI DSS scope/validation.
4. Authorize-then-capture versus direct sale by booking type and payment method.
5. Exact booking confirmation condition and payment-versus-hold-expiry winner/compensation policy.
6. Authorization lifetime, capture deadline, void strategy, and behavior when void result is unknown.
7. Whether request-to-book authorizes before host approval and how expiring authorization is handled.
8. Which pay-now, deposit, pay-later, installment, and balance-collection flows are supported by the
   approved Vietnam provider and required booking policies.
9. Supported SCA/3DS/redirect flows, exemption policy ownership, return/deep-link allowlist, and
   challenge-driven hold extension.
10. Saved-method consent, token scope, portability, revocation, billing metadata, and retention.
11. Provider decline taxonomy, guest-visible messages, retry caps, card-testing controls, and appeal.
12. Provider timeout thresholds, outcome-query contract, retry budget, circuit breakers, and outage
    behavior.
13. Provider routing eligibility, deterministic priority, safe failover boundary, and future
    experiment guardrails.
14. Exact refund-instruction handoff, reservation semantics, capture allocation, partial failure, and
    guest communication, consistent with the cancellation/modification design.
15. Late capture, orphan transaction, amount/currency mismatch, duplicate-suspected charge, and
    manual repair authority.
16. Operational versus financial reconciliation cadence, source files/APIs, watermark, materiality,
    case ownership, and finance-close dependency.
17. Dispute/retrieval ownership, evidence sources, submission approval, deadlines, fee/loss funding,
    and retention.
18. Webhook raw-body storage, encryption, payload digest, replay window, key rotation, retry response,
    and dead-letter policy per provider.
19. Secret manager, credential rotation, test/live separation, egress controls, and compromise
    response.
20. Forward migration/backfill from coarse attempt/refund statuses and the condition for removing the
    one-success-per-booking index.
21. SLOs and monetary/age thresholds for paging, compensation, reconciliation, and maker-checker.
22. Which payment facts finance requires before ledger posting and host payout can proceed.

Target-release default: one approved Vietnam provider account and tokenized pay-now method,
authorize then confirm and capture where the provider contract supports it, deterministic routing,
no stored raw credentials, no installments, durable operation/outbox/inbox state, server-verified
outcomes, automatic query recovery, idempotent late-success compensation, and daily operational plus
financial reconciliation. Deposits, installments, balance collection, and simultaneous routing are
explicit product extensions unless a supported rate plan requires them; the selected pay-now scope
is complete rather than a temporary payment implementation.
