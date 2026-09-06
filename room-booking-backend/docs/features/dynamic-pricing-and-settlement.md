# Dynamic pricing, quotes, money allocation, and settlement

## Purpose

This document defines the target money architecture for Room Booking. It covers the complete path
from a host's commercial intent and a guest's trip request to a payable quote, payment, tax
liability, platform revenue, host payout, refund, financial reporting, and reconciliation.

The central question is not merely "what is the nightly price?" It is:

> For this listing, stay, guest, market, legal jurisdiction, and instant in time, what may the guest
> be charged, who economically owns each amount, what must be withheld or remitted, what will the
> host receive, what will the platform earn, and how can every result be reproduced later?

The system therefore separates four related but different concerns:

```text
pricing      determines the public accommodation price and eligible offers
quoting      freezes a complete, explainable amount for a bounded period
accounting   records who owns or owes every amount
settlement   moves money to hosts, tax authorities, guests, and other recipients
```

Payment-provider records are evidence that external money movement occurred. They are not the
authoritative accounting ledger and do not by themselves establish platform revenue or host
earnings.

The executable decision that cancels or replaces an accepted booking, including line-level refund
entitlement and funding, is defined in
[`cancellation-modification-and-refund.md`](cancellation-modification-and-refund.md). This document
owns the underlying quote, allocation, and tax contracts consumed by that decision. The authoritative
implementation-level design for balanced journals, host payable/release, payout, statements,
reconciliation, and financial close is
[`ledger-reconciliation-and-host-payout.md`](ledger-reconciliation-and-host-payout.md).

## Status and dependencies

This is a target design, not a description of an implemented Java API. The current repository
already provides useful transactional foundations:

- `listings` stores a base price, currency, cleaning fee, and service-fee percentage;
- `availability_days` materializes a final nightly price for each local stay date;
- `bookings` snapshots accommodation, fee, tax, discount, and total summary amounts;
- `booking_nights` snapshots nightly price, discount, and tax values;
- `payment_attempts`, `refunds`, and `payment_webhook_events` provide idempotent provider-facing
  payment records.

Those tables do not yet represent price-rule provenance, discount funding, tax jurisdictions,
host entitlement, platform revenue, double-entry accounting, payout state, invoices, or
reconciliation. All structures proposed here require forward-only Liquibase migrations. Never edit
already-applied migrations `002`, `003`, `004`, or `005` to add them.

Recommended dependency order:

1. Listing catalog, publication lifecycle, and verified host identity.
2. Materialized availability calendar and host manual price controls.
3. Deterministic fee, discount, and quote calculation.
4. Authoritative booking creation and immutable financial snapshot.
5. Payment capture and refund integration.
6. Allocation, double-entry ledger, reconciliation, and host payouts.
7. Multi-jurisdiction tax configuration, invoicing, and seller reporting.
8. Market-aware public dynamic pricing.
9. Experiment-backed price elasticity and personalized offer optimization.

A correct deterministic quote, ledger, and payout system must exist before machine learning is
allowed to optimize prices or promotions.

## Goals

- Calculate a complete trip total from nightly prices, mandatory fees, discounts, credits, and
  taxes.
- Let a host express minimum net proceeds, price boundaries, risk tolerance, and commercial goals.
- Show the host an estimated payout before a price or promotion is accepted.
- Distinguish money collected for a host, platform revenue, taxes payable, processor costs, and
  promotional expense.
- Resolve tax treatment using versioned jurisdiction, party, registration, supply, and date facts.
- Preserve the exact quote, price-rule versions, tax decisions, and allocation used by a booking.
- Support partial refunds, cancellation penalties, chargebacks, reserves, adjustments, and payout
  reversals without rewriting history.
- Make all external money operations idempotent and reconcilable.
- Evolve from rule-based pricing to constrained statistical and machine-learned optimization.
- Personalize ranking, rate-plan presentation, and eligible offers without hidden willingness-to-pay
  price discrimination.
- Explain guest totals and host payouts in stable, localized line items.
- Provide finance, support, risk, and compliance teams with an auditable decision trail.

## Non-goals

- Encoding current country tax rates directly in application source code.
- Letting an LLM calculate prices, taxes, ledger postings, payouts, or refund entitlements.
- Treating captured cash as platform revenue.
- Treating every amount deducted from a host as platform revenue.
- Inferring a guest's income or maximum willingness to pay from sensitive or proxy attributes.
- Secretly increasing the public price for a guest classified as premium-oriented.
- Using payment-provider balances as the accounting system of record.
- Implementing a general-purpose enterprise resource planning system.
- Automatically entering a new country before legal, tax, payments, and operational readiness has
  been approved.
- Guaranteeing that one optimization objective is best for every host or market.

## Core principles and invariants

### Money has an owner, a payer, a beneficiary, and a reason

Every financial line must identify:

- who pays it;
- who economically benefits from it;
- who funds it when it is a discount or credit;
- whether it is revenue, expense, liability, receivable, payable, or a non-accounting display line;
- its tax treatment;
- its currency;
- the rule, contract, or event that created it.

A field named only `fee` or `discount` is insufficient. A guest-funded service fee, a host-funded
promotion, a platform-funded credit, and tax withheld from a host have different owners and
accounting treatment even when their numeric amounts happen to be equal.

### Guest payment, host payout, and platform revenue are different

The following amounts must never be used interchangeably:

```text
guest total             amount the guest is obligated to pay
captured cash           amount a payment provider successfully captured
host gross entitlement  consideration economically belonging to the host before deductions
host payout             cash currently transferable to the host
platform gross revenue  fees and commissions economically belonging to the platform
platform contribution   platform revenue less transaction-variable costs
tax payable              tax collected or withheld for a tax authority
```

### A quote balances exactly

For one quote currency:

```text
guest_total
  = accommodation
  + guest_facing_fees
  + guest_payable_taxes
  - guest_discounts
  - applied_credits
```

All values use integer minor units. Display subtotals are derived from line items and must reconcile
exactly. Floating-point money is forbidden.

### A booking allocation balances exactly

After successful capture, the complete economic allocation of captured cash must balance:

```text
captured_cash
  = host_payable
  + platform_owned_amounts
  + tax_payable
  + third_party_payable
  + refundable_or_reserved_amounts
```

Expenses paid separately by the platform, such as processor fees, may create additional balanced
ledger entries. They must not be hidden by changing the guest total.

### Rules and models propose; policy constrains; the pricing engine calculates

Model output is not a monetary fact. A model may predict demand, booking probability, price
elasticity, cancellation risk, or promotion uplift. A deterministic optimizer chooses among
permitted actions. The pricing, tax, allocation, and ledger components then produce exact money.

### Historical financial truth is immutable

Confirmed booking line items and posted ledger entries are not edited when a listing, rule, tax
rate, model, or exchange rate changes. Corrections use explicit reversals, credit notes, or
adjustment transactions linked to their originals.

### Host control has explicit boundaries

Hosts may control permitted commercial settings, such as base price, minimum net proceeds, public
price floor and ceiling, manual overrides, acceptable discount funding, and pricing strategy. They
may not override tax, consumer-protection, platform-risk, currency, sanctions, or accounting rules.

### Taxes are effective-dated policy, not timeless configuration

Tax liability can depend on the supply, location, party status, registrations, business model,
booking date, stay date, invoice date, and law effective date. Every decision records the facts and
tax-content version used at calculation time.

### Same public product, same public price

For the same listing, stay dates, occupancy, rate plan, currency, sales channel, and quote instant,
the public sell price should not silently increase because a guest is predicted to tolerate a higher
price. Transparent membership, loyalty, acquisition, retention, or recovery benefits may lower a
guest's payable amount when policy allows.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Stay date | A night consumed at the listing, interpreted in the listing's IANA timezone |
| Booking instant | The timestamp at which a quote or booking action occurs |
| Public nightly price | Sell price for one stay date before guest-specific offers and trip-level fees |
| Rate plan | A priced product with defined inclusions and cancellation/change conditions |
| Manual override | Host-authorized date price that takes precedence over automated recommendations |
| Price floor | Lowest public price permitted by host and platform policy |
| Price ceiling | Highest public price permitted by host and platform policy |
| Host target net | Desired host proceeds after modeled deductions, not a guaranteed payout |
| Host gross entitlement | Host-owned accommodation and host fee amounts before deductions |
| Commissionable base | Line-item amount on which host commission is calculated |
| Host payable | Accounting liability owed by the platform to the host |
| Available-to-payout | Portion of host payable no longer held by timing, risk, or reserve rules |
| Platform revenue | Platform-owned commission or fee, excluding collected tax |
| Contribution margin | Platform revenue less transaction-variable costs and funded incentives |
| Taxable base | Amount to which a tax rule applies after jurisdiction-specific adjustments |
| Tax liability party | Party legally responsible for the tax |
| Remittance party | Party required to collect or remit the tax |
| Withholding | Amount deducted from another party's proceeds and remitted or reported for tax |
| Quote | Versioned, expiring calculation offered to a guest before booking |
| Financial snapshot | Immutable booking-time copy of quoted lines, allocation, and policy provenance |
| Ledger transaction | One balanced business event containing two or more postings |
| Settlement | Release and transfer of payable funds to a recipient |
| Reconciliation | Comparison of internal financial truth with external provider, bank, and payout truth |

## End-to-end money flow

```text
host settings + market context + stay inventory
                    |
                    v
          public pricing decision
                    |
       rate plan + eligible promotions
                    |
                    v
            deterministic quote
        nightly lines + fees + taxes
                    |
            quote acceptance
                    |
     lock inventory and revalidate price
                    |
                    v
       immutable booking financial snapshot
                    |
            payment authorization/capture
                    |
                    v
       balanced allocation and ledger posting
          /             |              \
         v              v               v
   host payable   platform revenue    tax payable
         |              |               |
         v              v               v
     host payout   finance reporting  tax remittance/reporting
```

Search may display a fast estimate generated from materialized calendar prices. A booking may only
use an authoritative quote that has been recalculated or revalidated while the required
`availability_days` rows are locked.

## Price composition

### Separate stay price, trip adjustments, fees, discounts, and taxes

The calculation pipeline must preserve distinct categories:

1. Public price for each night.
2. Rate-plan adjustment for cancellation and included services.
3. Occupancy-dependent or trip-dependent accommodation adjustments.
4. Host and third-party fees.
5. Platform guest fees.
6. Public promotions.
7. Guest-eligible promotions and credits.
8. Taxes and statutory charges.
9. Currency presentation, if different from settlement currency.

Do not collapse these categories into a single nightly number if doing so prevents accurate guest
disclosure, commission, tax, refund, or payout treatment.

### Public nightly price

A rule-based baseline can be expressed conceptually as:

```text
raw_nightly_price(date)
  = base_price
  x weekday_factor
  x season_factor
  x event_factor
  x lead_time_factor
  x market_demand_factor
  x listing_occupancy_factor
  x booking_pace_factor
  x gap_night_factor
```

The production implementation should avoid unrestricted multiplication. Each component requires a
bounded adjustment, and the combined result requires a floor, ceiling, and change-rate guard:

```text
public_nightly_price
  = round_currency(
      clamp(
        bounded_combination(base_price, adjustments),
        effective_floor,
        effective_ceiling
      )
    )
```

An alternative implementation operates in log-price space and clamps every contribution. A simpler
initial implementation may use ordered additive percentage adjustments. The chosen method must be
deterministic for the same inputs and versions.

### Stay-date factors

Potential stay-date signals include:

- weekday and weekend patterns;
- local high and low season;
- public holidays and school holidays;
- verified local events;
- remaining comparable inventory;
- destination occupancy and booking pace;
- the listing's occupancy and booking pace;
- hard-to-sell one-night or two-night gaps;
- minimum-stay and arrival restrictions;
- expected operating costs for the date;
- quality and reliability band, used only within a legitimate comparable market.

External event and market data are untrusted inputs. Missing or anomalous data must remove the
associated adjustment rather than create an extreme price.

### Booking-instant factors

Shopping time and stay time are different. Booking-instant factors include:

- lead time before check-in;
- early-bird or last-minute window;
- remaining inventory at quote time;
- active public campaign;
- quote currency and permitted sales channel;
- rate-plan availability;
- platform and host promotion budgets.

The stay date uses the listing timezone. Quote creation, expiration, and experiment assignment use
an absolute timestamp.

### Rate plans

Priceable rate plans may include:

- flexible cancellation;
- moderate or strict cancellation;
- non-refundable;
- breakfast or another included service;
- late checkout;
- member rate;
- business-travel package.

A rate plan is not merely a percentage multiplier. It has an effective period, eligibility,
inventory applicability, cancellation policy, included line items, tax treatment, refund behavior,
and explanation text. The guest explicitly selects it.

### Fees

Fee definitions must declare:

- charging party and beneficiary;
- fixed, per-night, per-stay, per-person, or percentage calculation;
- taxable and commissionable treatment;
- whether the fee is mandatory or optional;
- included/excluded display policy;
- refund policy;
- effective period and jurisdiction scope;
- minimum, maximum, and rounding rules.

Common examples are cleaning, extra guest, pet, resort, guest service, host service, payment, and
optional ancillary-service fees. Product naming must not be allowed to obscure a mandatory fee.

## Host economics and target net proceeds

### Host settings

The host pricing contract should support:

```json
{
  "pricingMode": "BALANCED",
  "baseNightlyMinor": 1400000,
  "publicFloorNightlyMinor": 1050000,
  "publicCeilingNightlyMinor": 2200000,
  "targetNetNightlyMinor": 1250000,
  "minimumNetNightlyMinor": 1000000,
  "monthlyRevenueTargetMinor": 35000000,
  "occupancyTarget": 0.70,
  "allowDynamicPricing": true,
  "allowLastMinuteDiscount": true,
  "maximumHostFundedDiscountPercent": 10,
  "currency": "VND"
}
```

`targetNetNightlyMinor` is a commercial preference. `minimumNetNightlyMinor`, when supported by the
host contract, is a hard constraint on modeled host proceeds. Neither is a promise that taxes,
chargebacks, damage claims, currency conversion, or later adjustments can never change a payout.

The UI must state which deductions are included in the estimate.

### Settlement projection

For every candidate public price, the pricing optimizer calls a deterministic settlement projection:

```text
projection(candidate_price, trip, rate_plan, parties, jurisdiction)
  -> guest_total
  -> host_gross_entitlement
  -> host_commission
  -> host_fee_tax
  -> expected_withholding
  -> expected_host_net
  -> platform_revenue
  -> expected_platform_contribution
```

In a simple case only, required gross accommodation can be approximated by:

```text
required_gross
  = (desired_host_net + fixed_host_deductions)
    / (1 - commission_rate - proportional_withholding_rate)
```

Production code must not depend on this shortcut. Commission, discount, and withholding may have
different bases, caps, thresholds, inclusive taxes, and per-line rounding. The optimizer should
evaluate a discrete, currency-valid set of candidate prices through the real calculation engine.

### Impossible host targets

A target can be infeasible because of a public ceiling, market guardrail, contract fee, or statutory
deduction. The system must not silently violate a constraint. It returns a structured explanation:

```text
TARGET_NET_NOT_REACHABLE
required_public_price: 2,350,000 VND
effective_ceiling:     2,200,000 VND
best_estimated_net:    1,185,000 VND
```

The host can change a commercial setting but cannot disable statutory deductions.

### Host preview and statements

Before publishing a manual price or joining a promotion, the host should see:

- guest-facing accommodation price;
- estimated host gross entitlement;
- commission and fee deductions;
- estimated taxes and withholding;
- host-funded promotion amount;
- estimated payout;
- confidence or "subject to final tax status" indicator;
- which party funds each benefit;
- whether the target net remains satisfied.

After booking and payout, estimates are replaced with immutable actual statement lines.

## Platform economics

### Revenue is not cash collected

Platform-owned revenue may include:

- commission charged to the host;
- service fee charged to the guest;
- subscription or membership allocation;
- markup where the platform is contractually entitled to one;
- ancillary-service commission.

Platform-owned revenue excludes:

- accommodation consideration collected on behalf of a host under an agency model;
- VAT/GST, occupancy tax, tourist tax, or withholding payable to an authority;
- refundable guest funds;
- host reserves and host payable;
- processor funds not yet settled;
- damage deposits held for return.

### Contribution model

Transaction contribution may be evaluated as:

```text
platform_contribution
  = platform_revenue_excluding_tax
  - platform_funded_promotions
  - payment_processing_cost
  - expected_refund_and_chargeback_cost
  - payout_cost
  - other_transaction_variable_cost
```

Corporate income tax, fixed payroll, and broad overhead normally belong to company-level accounting,
not the guest quote. The finance definition of contribution must be versioned so experiment metrics
do not change silently.

### Marketplace optimization objective

Optimizing only immediate platform contribution can damage host trust, guest value, and long-term
liquidity. A constrained objective can combine:

```text
objective(candidate)
  = expected_platform_contribution
  + host_value_weight * expected_host_net
  + guest_value_weight * expected_guest_surplus_proxy
  + long_term_weight * expected_retention_value
  - cancellation_penalty
  - dissatisfaction_penalty
  - volatility_penalty
```

Hard constraints remain outside this weighted score. A high score cannot override tax, price floor,
host discount limit, guest filter, consumer disclosure, or fairness policy.

## Tax and jurisdiction engine

### Legal and tax operating model comes first

Before entering a market, the business must define whether the platform acts as an agent,
intermediary, merchant of record, reseller, or deemed supplier for each relevant supply. That choice
affects contracting, invoicing, revenue recognition, tax liability, collection, remittance, refund,
and reporting.

This is a legal and finance decision represented in software, not a value inferred by a pricing
model.

### Tax categories

The system must be able to represent separately:

Guest-facing liabilities:

- VAT or GST on accommodation;
- occupancy, lodging, city, or tourist tax;
- per-person or per-night statutory charges;
- tax on cleaning or another host fee;
- tax on the platform's guest service fee;
- tax on ancillary products.

Host-facing liabilities and deductions:

- withholding on host consideration;
- VAT/GST on platform services supplied to the host;
- reverse-charge treatment where applicable;
- host tax-registration consequences;
- corrections based on later verified tax status.

Platform obligations:

- tax on platform-owned fees;
- deemed-supplier or marketplace collection obligations;
- invoicing and credit-note obligations;
- seller due diligence and income reporting;
- registration and filing obligations by legal entity and jurisdiction.

These categories are extensible codes backed by governed definitions. Product code must not assume
that a field named `tax` has one universal meaning.

### Jurisdiction resolution inputs

The tax engine request may require:

- exact listing jurisdiction and property address;
- host legal name, entity type, residence, establishment, and tax registrations;
- platform contracting legal entity and its registrations;
- guest location or business status when legally relevant;
- supply type and rate plan;
- booking, payment, invoice, stay, cancellation, and refund dates;
- number and type of occupants when a per-person charge applies;
- line-item payer, beneficiary, and supplier;
- currency and tax-inclusive or tax-exclusive contract;
- marketplace legal model.

Exact address and tax identifiers are restricted data. The pricing model and search ranker do not
receive them merely because the tax service needs them.

### Effective-dated tax content

Each tax rule needs at least:

```text
jurisdiction code and level
tax type and authority
effective-from and effective-to timestamps
liable party and remittance party
taxable line-item types
tax base formula
rate, tier, threshold, exemption, cap, or fixed amount
inclusive/exclusive behavior
rounding level and mode
invoice and reporting codes
source/content version
approval and publication status
```

Tax content should be supplied through a reviewed internal process or a contracted tax provider.
Updates require maker-checker approval, fixtures, staged rollout, and an effective date. AI-generated
tax rates or unsourced web data must never enter production automatically.

### Tax calculation contract

Conceptual request:

```json
{
  "calculationInstant": "2026-09-06T08:00:00Z",
  "bookingDate": "2026-09-06",
  "stayPeriod": { "checkIn": "2026-12-20", "checkOut": "2026-12-23" },
  "listingJurisdiction": "resolved-internally",
  "hostTaxProfileId": "htp_...",
  "platformLegalEntityId": "ple_...",
  "guestTaxContext": { "type": "CONSUMER" },
  "businessModel": "AGENCY",
  "currency": "VND",
  "lines": []
}
```

Conceptual response:

```json
{
  "taxCalculationId": "taxcalc_...",
  "contentVersion": "tax-content-2026-09-01",
  "lines": [
    {
      "taxType": "LODGING_TAX",
      "authorityCode": "...",
      "taxableBaseMinor": 4200000,
      "taxMinor": 210000,
      "liableParty": "GUEST",
      "remittanceParty": "PLATFORM",
      "sourceLineIds": ["ql_1", "ql_2", "ql_3"]
    }
  ],
  "evidence": []
}
```

The response stores rule identifiers and evidence sufficient to reproduce the decision without
depending on the provider retaining it indefinitely.

### Tax-inclusive and tax-exclusive prices

Markets may require different display treatment. The engine must support both while retaining exact
line-level bases:

```text
exclusive: total = net + tax
inclusive: gross is displayed; tax is extracted from gross
```

Inclusive extraction is not implemented as a generic `gross * rate`. Compound taxes, per-unit taxes,
exemptions, and rounding can require rule-specific calculation.

### Tax reporting is separate from tax charging

A transaction may have no guest tax line but still create seller-reporting obligations. Store seller
identity and reportable consideration snapshots separately from tax calculation. Reporting status,
filing correction, and authority acknowledgement need their own lifecycle.

### External reference framework

The OECD International VAT/GST Guidelines describe internationally agreed approaches for applying
VAT/GST to cross-border trade. OECD guidance also discusses platform VAT/GST collection models and
model reporting rules for platform sellers, including accommodation. Jurisdiction law and approved
professional advice remain authoritative for an actual launch:

- [OECD International VAT/GST Guidelines](https://www.oecd.org/en/publications/international-vat-gst-guidelines_9789264271401-en.html)
- [OECD role of digital platforms in VAT/GST collection](https://www.oecd.org/en/publications/the-role-of-digital-platforms-in-the-collection-of-vat-gst-on-online-sales_e0e2dd2d-en.html)
- [OECD model reporting rules for digital platforms](https://www.oecd.org/en/topics/sub-issues/international-tax-compliance-policies-and-best-practices/model-reporting-rules-for-digital-platforms.html)
- [European Commission VAT in the Digital Age](https://taxation-customs.ec.europa.eu/taxation/vat/vat-digital-age-vida_en)

These references guide architecture; they are not a substitute for jurisdiction-specific tax
determinations.

## Price rules and precedence

### Rule scopes

A price or fee rule may be scoped by:

- platform legal entity;
- country, region, city, or governed market;
- host contract or pricing plan;
- listing or listing group;
- room type or accommodation category;
- stay-date interval and weekday;
- booking-instant interval;
- lead-time band;
- occupancy or booking-pace band;
- length of stay and guest count;
- rate plan;
- approved sales channel;
- promotion or experiment assignment.

The rule engine must reject ambiguous overlapping rules or resolve them using a documented priority
and specificity algorithm.

### Recommended precedence

```text
legal and tax restrictions
  -> listing currency and host contract
  -> host manual date override
  -> host floor and ceiling
  -> seasonal and weekday rules
  -> approved market/demand recommendation
  -> rate-plan adjustment
  -> occupancy and trip adjustments
  -> public promotion
  -> eligible member/personal offer
  -> coupon or credit
  -> fees and taxes in legally required order
  -> currency rounding
```

Precedence does not necessarily mean calculation order for tax. The tax engine receives the final
pre-tax lines and applies its own jurisdiction-specific sequencing.

### Manual override semantics

A manual override declares:

- exact local stay dates;
- public price and currency;
- whether dynamic suggestions are disabled or only advisory;
- whether public promotions may still apply;
- actor, timestamp, and reason;
- optional expiration or replacement policy.

The system may warn that the price violates a target or appears anomalous. It must reject values that
violate contractual, legal, currency, or absolute platform constraints.

### Rule versioning and reproducibility

Published rule versions are immutable. Editing creates a new version with a future or immediate
effective time. A price decision stores all applied rule-version identifiers, rejected rules when
important for explanation, and the final precedence outcome.

## Promotions, discounts, and credits

### Funding is mandatory

Every benefit identifies its economic funder:

```text
HOST
PLATFORM
PARTNER
CO_FUNDED
```

Co-funded benefits split into explicit child allocations. A single `discount_minor` summary cannot
determine host payout or platform margin.

### Eligibility and stacking

Promotion definitions include:

- valid booking and stay periods;
- listing, host, market, rate-plan, and guest eligibility;
- acquisition, loyalty, retention, recovery, or public campaign purpose;
- percentage, fixed, fee-waiver, credit, or bundled-benefit action;
- applicable line items;
- minimum spend and maximum benefit;
- total and per-guest budgets;
- redemption and frequency limits;
- funder allocation;
- tax treatment;
- combinability group and precedence;
- refund and cancellation behavior;
- experiment and control-group policy.

The promotion engine evaluates all eligible benefits and chooses a permitted combination. It must
not apply independent discounts sequentially without a stacking policy.

### Credits are not always discounts

A promotional discount reduces the selling consideration according to its tax treatment. A stored
travel credit may represent platform liability previously issued to a guest and used as a payment
instrument. Gift cards, cash-equivalent credits, goodwill credits, and price discounts require
different accounting and potentially different tax treatment.

### Responsible guest personalization

Guest behavior may personalize:

- search ranking by total-trip value;
- rate-plan order;
- public-price-band recommendations;
- eligible downward offers;
- loyalty rewards and travel credit;
- explanation and budget controls.

It must not silently increase the public price based on inferred income, expensive device use,
sensitive characteristics, or an estimated maximum willingness to pay. Protected attributes and
unsupported proxies are excluded from eligibility and optimization features.

## Machine learning and optimization

### Model portfolio

Use several bounded models rather than one opaque price model:

| Model | Output | Consumer |
| --- | --- | --- |
| Market demand forecast | Expected searches/bookings by market and stay date | Public price optimizer |
| Listing booking-pace forecast | Expected occupancy trajectory | Public price optimizer |
| Booking propensity | Probability of booking under candidate price/context | Ranking and optimizer |
| Price elasticity | Change in booking probability under price variation | Public price optimizer |
| Guest preference | Contextual price/quality/rate-plan preference | Discovery and rate-plan ordering |
| Promotion uplift | Incremental outcome caused by an offer | Promotion policy |
| Cancellation/no-show | Expected cancellation, refund, and reserve risk | Margin projection and risk |
| Fraud/chargeback | Bounded loss or reserve estimate | Risk policy, never tax calculation |

LLMs may extract structured intent or review aspects for discovery. They are not authoritative
pricing, tax, ledger, or payout calculators.

### Guest price preference

A guest is not globally "cheap" or "expensive." The same person may be budget-sensitive on a solo
trip and premium-oriented for a family trip. Represent preference conditionally:

```text
guest_price_preference
  = f(guest_history, explicit_filters, destination, trip_shape, rate_plan, current_session)
```

Normalize observed prices within a comparable market instead of learning raw currency amounts:

```text
relative_price_percentile
  = percentile(
      payable_trip_total,
      eligible comparable listings for the same market, dates, capacity, and room type
    )
```

Useful evidence includes explicit maximum price, sort choice, clicked/saved/booked price percentile,
reaction after total-fee disclosure, coupon use, chosen cancellation plan, trip length, and later
value satisfaction. Profiles store recency, evidence count, and confidence. Missing evidence uses
market priors.

The preference profile primarily affects matching and eligible benefits. It is not permission to
extract a predicted maximum price.

### Booking propensity

The model estimates a clearly defined event and attribution window:

```text
P(eligible booking within 24 hours | guest, listing, trip, market, display context, candidate price)
```

Training data must include non-booked impressions and correct exposure context. Important controls
include rank position, availability, fees, campaign assignment, channel, market supply, and listing
quality. Time-based splits prevent future leakage.

Start with calibrated logistic regression, GAM, or gradient-boosted trees. Where supported, enforce
a monotonic relationship so an isolated price increase cannot produce an unjustified increase in
predicted booking probability. Deep models are not justified until traffic, experiment coverage,
and operational maturity require them.

### Price elasticity requires causal evidence

Historical price and booking correlations are confounded by season, quality, ranking position,
availability, campaigns, and host decisions. A model trained naively on observed bookings may
recommend precisely the wrong price.

Estimate causal price response through small, approved, randomized variations within host and
platform guardrails. Experiments require:

- explicit host participation where contractually required;
- narrow price movement bounds;
- public-price fairness controls;
- stable control groups;
- sufficient market/listing coverage;
- predeclared primary and guardrail metrics;
- kill switches and budget limits;
- analysis of host, guest, and marketplace outcomes.

### Constrained candidate optimization

Generate currency-valid candidate prices, run every candidate through real quote and settlement
projection, then choose:

```text
price_star
  = argmax over candidate_price of
      P(book | candidate_price, context)
      x expected_value_if_booked(candidate_price)
      - risk_and_volatility_penalties
```

Subject to:

- public floor and ceiling;
- modeled host minimum net;
- host-funded discount limit;
- market and day-over-day movement limit;
- minimum platform contribution where applicable;
- rate-plan consistency;
- tax and disclosure policy;
- fairness and experiment constraints.

No candidate is accepted directly from a model output. Exact monetary calculation and constraint
validation occur after prediction.

### Promotion uplift and contextual bandits

Offer selection should optimize incremental behavior, not raw booking propensity:

```text
uplift(guest, offer, context)
  = P(book | offer, context) - P(book | no_offer, context)
```

```text
incremental_contribution
  = P(book | offer) x contribution_after_offer
  - P(book | no_offer) x contribution_without_offer
  - offer_delivery_cost
```

Randomized holdouts are necessary because guests receiving historical offers were usually selected
non-randomly. T-learners, X-learners, doubly robust learners, causal forests, or another validated
uplift method can follow a simple experimental baseline.

A contextual bandit may eventually choose among policy-approved actions such as no offer, percentage
discount, fixed discount, fee waiver, travel credit, or a non-refundable rate. It needs frequency
caps, total budgets, per-action limits, stable holdouts, delayed-reward handling, and an immediate
fallback to deterministic eligibility.

### Labels and long-term outcomes

Do not optimize only clicks or immediate capture. Labels and evaluation may include:

- qualified booking and completed stay;
- realized host net;
- realized platform contribution;
- cancellation, refund, no-show, and chargeback;
- price surprise and checkout abandonment;
- guest value rating and review-aspect satisfaction;
- host override and host retention;
- repeat booking and long-term guest retention.

Revenue labels must use finalized or appropriately matured ledger data, not payment authorization
alone.

### Model governance and fallback

Every decision records feature version, model version, experiment assignment, raw prediction,
calibration version, candidate set, optimizer version, policy rejections, and chosen action in a
restricted diagnostic record.

If a model is unavailable, stale, outside its supported market, or low-confidence, fall back in
order:

1. Host manual override.
2. Published deterministic price rules.
3. Last valid bounded calendar price.
4. Host base price within current constraints.

Tax or authoritative money-calculation failure does not permit an estimated booking charge.

## Quote lifecycle

### Quote request

An authoritative quote request identifies:

- listing;
- check-in and check-out;
- occupancy, children, pets, and applicable guest categories;
- selected rate plan and optional services;
- guest identity when available;
- promotion or coupon claims;
- presentation currency;
- locale and request channel;
- idempotency key.

Identity is optional for public estimates but required for benefits tied to an account.

### Quote response

```json
{
  "quoteId": "q_01...",
  "listingId": "...",
  "checkIn": "2026-12-20",
  "checkOut": "2026-12-23",
  "ratePlanCode": "FLEXIBLE",
  "currency": "VND",
  "accommodationMinor": 4200000,
  "feeMinor": 480000,
  "discountMinor": 300000,
  "taxMinor": 219000,
  "totalMinor": 4599000,
  "nightlyBreakdown": [],
  "lineItems": [],
  "appliedBenefits": [],
  "expiresAt": "2026-09-06T08:15:00Z",
  "pricingVersion": "pricing-2026-09-01",
  "taxContentVersion": "tax-2026-09-01",
  "termsVersion": "terms-2026-08-15"
}
```

Each line item exposes a stable code, localized label key, quantity, unit, base, amount, inclusion
status, and guest-facing explanation. Internal ownership and accounting metadata are not necessarily
returned to the guest.

### Quote expiration

A quote has a short, explicit expiration based on inventory volatility, price inputs, promotion
budget, tax-content rules, and payment risk. Expiration is not a guarantee that inventory is held.
If inventory holding is later supported, it is a separate reservation with its own timeout.

The client must display material price changes after re-quote and obtain renewed acceptance. It must
not silently submit a different amount.

### Search estimate versus quote

Search uses materialized public nightly prices and known mandatory fees to produce a fast trip-total
estimate. It must be labeled or treated as an estimate and should already minimize checkout surprise.
The authoritative quote recomputes exact occupancy, promotion, fee, tax, and rate-plan lines.

### Booking acceptance transaction

Booking creation should:

1. Authenticate and authorize the guest.
2. Resolve the quote by opaque identifier.
3. Verify ownership, intended listing/trip, and expiration.
4. Lock all requested `availability_days` rows.
5. Revalidate availability, capacity, stay rules, and rate plan.
6. Recalculate or validate all volatile quote inputs under defined tolerance policy.
7. Snapshot nightly and line-item financial details.
8. Create the booking and pending-payment state atomically.
9. Commit before calling an external payment provider where the integration pattern requires it.
10. Use an idempotency key for payment creation.

The booking summary columns must equal the immutable line-item aggregation.

## Financial allocation

### Line-item allocation dimensions

Every booking financial line needs enough metadata to derive accounting:

```text
line type and subtype
payer party
supplier/beneficiary party
economic funder
tax authority when applicable
amount and currency
quantity, unit amount, and rounding result
stay date or service period
commissionable flag and commission base
tax treatment and source tax line
refund policy
recognized-at policy
rule, promotion, tax, and contract versions
```

### Illustrative allocation

The following numbers are illustrative only and do not represent a real jurisdiction's rates:

```text
Guest-facing quote
  accommodation                         5,000,000
  cleaning fee                            300,000
  guest service fee                       500,000
  host-funded discount                   -250,000
  platform-funded discount               -150,000
  guest-facing tax                        480,000
                                         ---------
  guest total                           5,880,000
```

```text
Host projection
  host accommodation and cleaning       5,300,000
  host-funded discount                   -250,000
  host commission                        -750,000
  tax on platform fee charged to host     -75,000
  illustrative withholding               -101,000
                                         ---------
  projected host payout                 4,124,000
```

```text
Platform transaction contribution
  guest service revenue                   500,000
  host commission revenue                 750,000
  platform-funded promotion              -150,000
  payment processing cost                -120,000
                                         ---------
  contribution before other costs         980,000
```

The guest-facing tax is allocated to tax payable, not platform revenue. Tax on platform fees may be
collected in addition to or extracted from a fee depending on the applicable rule.

## Double-entry ledger

This section summarizes the accounting boundary needed by pricing. Detailed posting rules, state,
schema, APIs, controls, close, and rollout are owned by
[Ledger, reconciliation, and host payout](ledger-reconciliation-and-host-payout.md).

### Why a ledger is required

Booking totals answer what was sold. Payment records answer what a provider attempted. Neither can
reliably answer:

- how much cash belongs to hosts;
- how much is held for tax;
- what revenue the platform earned;
- whether a refund was funded by host, platform, or both;
- whether processor and bank settlements reconcile;
- what remains payable after a chargeback or adjustment.

The internal double-entry ledger is the authoritative financial subledger.

### Account model

Example account families:

Assets:

- processor clearing;
- bank cash;
- provider receivable;
- chargeback receivable;
- host recovery receivable.

Liabilities:

- host payable;
- host reserve;
- guest refundable funds;
- travel-credit liability;
- tax payable by authority and tax type;
- partner payable;
- payout in transit.

Revenue:

- host commission revenue;
- guest service-fee revenue;
- ancillary commission revenue.

Expenses and contra-revenue:

- platform-funded promotion;
- processor fee;
- payout fee;
- refund or goodwill expense;
- chargeback loss;
- FX gain/loss according to accounting policy.

Finance owns the chart of accounts. Product line-item codes map to ledger accounts through versioned
posting rules.

### Balanced posting

Every ledger transaction has:

- immutable identifier;
- idempotency key;
- business event type and source identifier;
- legal entity;
- accounting timestamp and booking timestamp;
- currency;
- two or more debit/credit postings;
- posting-rule version;
- reversal relationship where applicable;
- actor or automated process identity.

For each currency and balanced transaction:

```text
sum(debits) = sum(credits)
```

A multi-currency event uses explicit FX bridge/clearing postings. Amounts in different currencies are
never added merely to make a transaction balance.

### Example lifecycle postings

On successful capture, conceptual postings may debit processor clearing and credit host payable,
platform revenue, tax payable, and other liabilities according to the financial allocation.

When the processor deducts its fee, record the fee expense and reduction in processor clearing or
cash. When money is released to a host, debit host payable and credit payout-in-transit. Provider
confirmation then clears payout-in-transit against cash or provider clearing.

Exact debit/credit direction and timing must be approved by finance and the legal operating model.
The implementation must not infer accounting policy from these illustrative descriptions.

### Posting states

The ledger may use pending and posted states if finance requires them, but posted entries are never
mutated. Failed business events create no partial transaction. Reversal posts a complete inverse
transaction linked to the original.

## Payments and reconciliation

### Provider isolation

A payment adapter translates internal commands to provider-specific operations:

```text
authorize
capture
cancel authorization
refund
query payment
verify webhook signature
```

Provider statuses map to an internal state machine without discarding the raw provider reference,
event time, and sanitized reason codes. Never store card credentials, bank credentials, secrets, or
unbounded raw payloads.

### Idempotency

Every external operation uses a stable operation-specific idempotency key. Incoming webhooks are
deduplicated by provider and event ID. Ledger posting uses its own unique business-event key so a
replayed webhook cannot duplicate financial effects.

### Reconciliation layers

Perform at least:

1. Booking-to-quote reconciliation.
2. Booking-to-payment reconciliation.
3. Payment-to-ledger reconciliation.
4. Provider-balance-to-ledger-clearing reconciliation.
5. Provider-payout-to-bank reconciliation.
6. Host-payout-to-host-payable reconciliation.
7. Tax-return-to-tax-payable reconciliation.

Reconciliation results have `MATCHED`, `MISMATCHED`, `MISSING_INTERNAL`, `MISSING_EXTERNAL`, and
`TIMING_DIFFERENCE` outcomes with materiality thresholds and case ownership.

Do not automatically erase differences with an unexplained adjustment. Create an investigation or
an approved adjustment with evidence.

## Host payout and settlement

This section summarizes how pricing and host-economics projections connect to settlement. The
implementation-level payout, destination-security, statement, return, recovery, and reconciliation
design is [Ledger, reconciliation, and host payout](ledger-reconciliation-and-host-payout.md).

### Payout eligibility

Host payable becomes available according to:

- booking and payment success;
- check-in, checkout, or contract-specific release time;
- cancellation and dispute window;
- identity and tax-profile verification;
- sanctions and risk status;
- new-host or high-risk reserve;
- minimum payout threshold;
- payout currency and destination readiness;
- negative balance or recovery policy.

Price optimization uses expected payout timing where material, but it may not bypass payout risk
policy.

### Payout state machine

```text
SCHEDULED -> SUBMITTED -> IN_TRANSIT -> PAID
    |            |            |
    +----------> FAILED <------+
                     |
                     v
                 RETRYABLE or MANUAL_REVIEW
```

Cancellation before submission creates a cancellation record. A provider return after `PAID`
creates a returned-payout event and new ledger postings; it does not rewrite the original payout.

### Payout statement

Each statement shows:

- bookings and service periods included;
- host gross entitlement;
- host-funded discounts;
- commission and platform fees;
- tax on platform fees;
- withholding by authority/type;
- refunds, cancellation effects, reserves, and adjustments;
- currency conversion and payout fee;
- opening and closing payable balance;
- amount transferred and provider reference.

Amounts link to booking line items and ledger postings.

### Negative host balances

Refunds or chargebacks after payout can create a negative host balance. Recovery policy must specify
whether to offset future payouts, debit an authorized payment method, request repayment, use a
reserve, or absorb the loss. It must honor jurisdiction and contract rules and be visible to the
host.

## Cancellation, refund, dispute, and chargeback

### Refund calculation is line-specific

A cancellation policy determines which service lines are refundable at a given instant. The refund
engine calculates:

- unused/refundable accommodation;
- refundable host and platform fees;
- reversal of discounts by funder;
- tax reversal or retained tax;
- cancellation penalty and beneficiary;
- currency and prior-refund limits.

The sum of successful refunds must not exceed captured refundable consideration. This invariant is
enforced transactionally.

### Refund funding

Refund allocation may debit:

- host payable or host recovery;
- platform revenue or goodwill expense;
- tax payable or tax receivable;
- partner payable;
- guest credit liability.

The guest refund amount alone is insufficient to derive who funds it.

### Partial refund and adjustment

Every refund or adjustment references specific source line items or a documented allocation rule.
Use integer proportional allocation with a deterministic remainder policy. Never drop rounding
remainders.

### Disputes and chargebacks

Chargeback state is distinct from a normal refund. Record disputed amount, reason, evidence deadline,
provider fee, provisional debit, final outcome, and loss allocation. A won dispute reverses
provisional effects; a lost dispute realizes them according to risk policy.

## Currency and foreign exchange

### Currency roles

The system may need distinct currencies for:

- listing/public price;
- guest presentation;
- guest payment;
- booking accounting;
- host entitlement;
- host payout;
- platform functional reporting.

An initial product should minimize this complexity by requiring booking, charge, and financial
snapshot to use one currency. Presentation conversion can remain informational until a complete FX
settlement design exists.

### FX quote

When transactional FX is supported, record:

- base and quote currencies;
- provider and rate identifier;
- rate value using decimal, never binary floating point;
- rate timestamp and expiry;
- markup/spread owner;
- source amount, converted amount, and rounding;
- refund FX policy;
- host-payout conversion policy.

Historical refund policy must explicitly choose original-rate, current-rate, or provider-result
treatment and allocate FX gains/losses.

### Minor units and rounding

Currency metadata defines minor-unit exponent and cash/payment constraints. Store money as integer
minor units. Store rates and percentages as bounded decimal values.

Rounding policy declares:

- mode, such as half-up or half-even;
- whether rounding occurs per unit, night, line, tax, invoice, or total;
- remainder allocation;
- jurisdiction override;
- version.

Calculate at the required precision, round once at the defined boundary, and retain the rounded
result used by subsequent steps.

## Conceptual data model

The following tables are design candidates, not approved migration names.

### Pricing configuration

`host_pricing_settings`

- listing and currency;
- strategy and automation state;
- base, floor, ceiling, target net, and minimum net;
- occupancy/revenue goals;
- promotion funding limits;
- version and effective period.

`price_rules` and `price_rule_versions`

- rule type, scope, conditions, action, priority, compatibility group;
- effective period, publication state, author, approver;
- immutable version payload.

`manual_price_overrides`

- listing, local date range, public nightly amount;
- promotion behavior, actor, reason, and version.

`price_recommendations`

- listing/stay date, candidate and recommended prices;
- predictions, constraints, selected reason codes;
- feature/model/optimizer versions, generation and expiry times;
- accepted/rejected/overridden state.

`daily_price_components`

- listing/stay date and materialized price version;
- base amount, applied component codes and amounts;
- public final price and source decision.

### Promotions

`promotions` and `promotion_versions`

- purpose, eligibility, action, funding, budgets, stacking, tax treatment;
- booking/stay validity and immutable version.

`promotion_assignments`

- guest or anonymous experiment unit;
- promotion/experiment arm;
- assignment time, eligibility snapshot, expiry.

`promotion_redemptions`

- quote, booking, guest, benefit amount, funder split;
- reserved, redeemed, released, reversed state.

### Quotes and booking financial snapshots

`quotes`

- guest/listing/trip/rate plan;
- currency and summary amounts;
- pricing/tax/terms versions;
- idempotency key, created/expiry/accepted timestamps;
- status and calculation hash.

`quote_nights` and `quote_line_items`

- local stay date, quantity, unit and amount;
- payer, beneficiary, funder, supplier;
- tax/commission/refund metadata;
- rule and source relationships.

`booking_financial_snapshots` and `booking_line_items`

- immutable accepted copy of quote and allocation;
- summary reconciliation hash;
- recognition and refund attributes.

Existing `bookings` and `booking_nights` summary columns can remain as denormalized invariants while
the new lines provide authoritative detail.

### Tax

`party_tax_profiles` and `tax_registrations`

- party, entity type, residence/establishment and verified registrations;
- encrypted or tokenized restricted identifiers;
- verification source and effective period.

`tax_rule_versions`

- jurisdiction, authority, tax type, applicability and formula;
- effective period, source, approval, and immutable content version.

`tax_calculations` and `tax_calculation_lines`

- request fact snapshot and content version;
- taxable base, amount, liable/remittance parties;
- source and resulting line relationships.

`invoices`, `invoice_lines`, and `credit_notes`

- legal issuer and recipient;
- jurisdiction sequence/number;
- source booking and tax calculation;
- immutable issued document and correction relationships.

`seller_reporting_periods` and `seller_report_lines`

- authority, seller identity snapshot, reportable consideration and fees;
- filing version, acknowledgement, correction state.

### Accounting and settlement

`ledger_accounts`

- legal entity, account code/type, party or authority dimension, currency policy;
- active period and finance metadata.

`ledger_transactions` and `ledger_postings`

- business event, idempotency key, accounting time, currency;
- debit/credit account, amount, dimensions, source line;
- posting rule, reversal, and immutable audit metadata.

`host_payouts` and `host_payout_items`

- host, destination, currency, amount, state, provider reference;
- included payable postings and statement lines.

`reserves`, `settlement_adjustments`, and `reconciliation_cases`

- source, reason, owner, evidence, amount, lifecycle, and resolution.

### Database invariants

Future migrations should enforce where practical:

- non-negative unsigned line values with explicit direction/type instead of negative ambiguity;
- ISO currency format and supported-currency application validation;
- unique idempotency keys within operation scope;
- quote expiry after creation;
- immutable published rule versions;
- immutable posted ledger transactions;
- positive posting amounts;
- unique source-event posting;
- balanced transaction validation in the transaction boundary;
- cumulative refund and redemption limits;
- no payout item consumed by more than one successful payout;
- version values never negative.

Some cross-row sums require transactional service validation and reconciliation because SQL check
constraints cannot express them safely.

## Service boundaries

### `PricingPolicyService`

Loads applicable host, platform, market, manual, and rate-plan rules. Returns a deterministic,
versioned policy set.

### `DemandPredictionService`

Returns bounded predictions and confidence. It has no access to tax identifiers and does not create
money.

### `PublicPriceOptimizer`

Generates candidate nightly prices, calls quote/settlement projections, applies constraints, and
returns a recommendation plus reason codes.

### `PromotionService`

Evaluates eligibility, stacking, budget reservation, funding, experiment assignment, and benefit
redemption.

### `TaxService`

Resolves jurisdiction and returns exact tax lines from effective-dated approved content or a tax
provider adapter. It never accepts an LLM answer as tax content.

### `QuoteService`

Orchestrates availability, pricing, promotions, fees, tax, allocation preview, expiry, and guest
breakdown. It owns deterministic quote reconciliation.

### `BookingFinancialSnapshotService`

Validates an accepted quote under locked inventory and copies immutable line-level truth into the
booking transaction.

### `LedgerService`

Posts one balanced, idempotent transaction from an approved business event and posting-rule version.
Callers cannot submit arbitrary debit and credit pairs through a public API.
Its detailed contract is defined in
[`ledger-reconciliation-and-host-payout.md`](ledger-reconciliation-and-host-payout.md).

### `PaymentService`

Coordinates provider attempts and verified webhooks. It emits business events but does not calculate
platform revenue or host payout. Detailed provider operation, customer-action, webhook/query
recovery, dispute, and reconciliation behavior is defined in
[`payment-orchestration.md`](payment-orchestration.md).

### `RefundService`

Consumes an immutable line-level entitlement/funding instruction from the
[cancellation, modification, or approved remedy decision](cancellation-modification-and-refund.md),
coordinates provider refund through payment, and posts successful financial effects idempotently.
It does not independently reinterpret the accepted cancellation policy.

### `PayoutService`

Selects eligible host-payable postings, applies holds/reserves, creates statements, submits provider
payouts, and records returns or failures. Its detailed contract is defined in
[`ledger-reconciliation-and-host-payout.md`](ledger-reconciliation-and-host-payout.md).

### `ReconciliationService`

Imports restricted external settlement data, matches it to internal records, and creates auditable
cases for unexplained differences. Its detailed contract is defined in
[`ledger-reconciliation-and-host-payout.md`](ledger-reconciliation-and-host-payout.md).

These boundaries may initially live in one Spring Boot deployment. Their contracts prevent provider,
model, and tax concerns from leaking into booking invariants.

## API behavior

Potential guest-facing operations:

```text
POST /api/v1/quotes
GET  /api/v1/quotes/{quoteId}
POST /api/v1/bookings                  accepts quoteId
GET  /api/v1/bookings/{id}/price-breakdown
POST /api/v1/bookings/{id}/refund-estimates
```

Potential host-facing operations:

```text
GET  /api/v1/host/listings/{id}/pricing-settings
PUT  /api/v1/host/listings/{id}/pricing-settings
PUT  /api/v1/host/listings/{id}/price-overrides
GET  /api/v1/host/listings/{id}/price-recommendations
POST /api/v1/host/listings/{id}/payout-previews
GET  /api/v1/host/payouts
GET  /api/v1/host/payouts/{id}/statement
```

Administrative tax, ledger, and reconciliation operations require separate privileged authorization,
strong audit logging, and are not general-purpose CRUD APIs.

### Error semantics

Stable examples:

```text
QUOTE_EXPIRED
QUOTE_PRICE_CHANGED
QUOTE_INVENTORY_CHANGED
RATE_PLAN_UNAVAILABLE
PROMOTION_NO_LONGER_ELIGIBLE
PROMOTION_BUDGET_EXHAUSTED
HOST_NET_TARGET_UNREACHABLE
TAX_PROFILE_INCOMPLETE
TAX_CALCULATION_UNAVAILABLE
CURRENCY_NOT_SUPPORTED
PAYMENT_AMOUNT_MISMATCH
REFUND_EXCEEDS_AVAILABLE_AMOUNT
PAYOUT_HELD_FOR_VERIFICATION
```

Do not expose internal model scores, tax identifiers, risk rules, or ledger account details to an
unauthorized caller.

## Event contracts

Business events should carry immutable source identifiers, occurrence time, producer version,
correlation/causation identifiers, legal entity, and idempotency key.

Important events include:

```text
PublicPriceMaterialized
QuoteCreated
QuoteExpired
QuoteAccepted
BookingFinancialSnapshotCreated
PaymentAuthorized
PaymentCaptured
PaymentFailed
RefundSucceeded
BookingCancelled
StayCompleted
ChargebackOpened
ChargebackResolved
LedgerTransactionPosted
HostPayableReleased
HostPayoutSubmitted
HostPayoutPaid
HostPayoutReturned
TaxDocumentIssued
TaxReportFiled
ReconciliationMismatchDetected
```

Event publication must use an outbox or equivalent transactional pattern before asynchronous
financial consumers are introduced. At-least-once delivery is expected; consumers are idempotent.

Behavioral ML events are separate from financial business events. Analytics loss must not make a
booking or ledger incorrect.

## Concurrency and idempotency

- Lock requested calendar rows before authoritative booking creation.
- Use optimistic versions for host settings and manual calendar changes.
- Use quote request idempotency to avoid duplicate benefit reservations.
- Reserve limited promotion budget atomically and release it on expiry.
- Use provider-specific idempotency keys for charge, refund, and payout operations.
- Deduplicate webhooks before processing and lock affected payment state.
- Use a unique ledger source-event key to prevent double posting.
- Select payout items with transactional locking or safe skip-locked batching.
- Enforce cumulative refund and discount redemption limits inside a transaction.
- Never hold a database transaction open while waiting for an uncontrolled external network call.

## Security, privacy, and access control

### Sensitive data boundaries

Protect:

- host legal identity and tax identifiers;
- payout destination and bank metadata;
- exact property address used for tax;
- guest billing facts;
- payment and provider references;
- internal margin, risk, model, and tax-decision diagnostics;
- invoices and tax reports.

Use encryption or provider tokens where appropriate, least-privilege service access, audited support
access, field-level redaction, and retention schedules. Never log secrets or full payment instrument
data.

### Authorization

- Guests may read only their own quote and booking breakdown.
- Hosts may read only their listings, earnings, and payout statements.
- A host must not see guest-funded platform credits or internal guest segmentation unless disclosure
  is contractually required.
- Support access is scoped, time-bound where possible, and audited.
- Finance may access ledger and reconciliation detail.
- Tax operations may access tax profiles and filings under separate roles.
- Model-development datasets use minimized, pseudonymized features.

### Personalized-pricing fairness

Prohibit protected and highly sensitive characteristics and unjustified proxies. Monitor public-price
parity across guest cohorts for equivalent product context. Review eligibility outcomes, discount
access, false inference, and geographic proxy effects. Provide meaningful guest controls over
personalization where required or promised.

## Operations and governance

### Maker-checker controls

Require independent review for:

- tax-content publication;
- platform fee changes;
- host commission contract changes;
- chart-of-account and posting-rule changes;
- payout reserve policy;
- refund policy;
- price experiment bounds;
- promotion budget increases;
- emergency financial adjustments above threshold.

Production activation records author, approver, reason, version, and effective time.

### Kill switches

Provide scoped controls to:

- disable dynamic recommendations globally or by market;
- fall back to deterministic prices;
- stop a promotion or experiment;
- stop quote creation for a broken tax jurisdiction;
- stop capture while preserving existing bookings;
- stop payouts without changing host payable;
- stop one payment or payout provider;
- disable a tax-content version;
- quarantine a reconciliation feed.

Kill switches are audited and tested. Stopping payout must not erase or reclassify host money.

### Audit trail

For any booking, authorized staff must be able to answer:

- What did the guest see and accept?
- What rule and model versions produced it?
- Which discounts applied, and who funded them?
- Which tax jurisdictions and rules applied?
- What did the host expect and actually receive?
- What platform revenue and variable cost were recorded?
- Which ledger entries represent capture, refund, payout, and adjustments?
- Which external provider and bank records reconcile to them?
- Who changed any relevant configuration and who approved it?

## Observability and financial controls

### Pricing and guest metrics

- quote latency, availability, and expiration rate;
- search-estimate-to-quote difference;
- quote-to-book conversion;
- total-price surprise and checkout abandonment;
- price percentile and day-over-day volatility;
- promotion exposure, redemption, and incremental lift;
- guest complaint, cancellation, and value satisfaction.

### Host metrics

- projected versus realized host net;
- target-net attainment;
- price recommendation acceptance and override;
- occupancy, ADR, and host revenue;
- payout timeliness, failure, return, and reserve rate;
- statement disputes and negative balances;
- host retention.

### Platform and finance metrics

- gross booking value, separated from revenue;
- platform revenue excluding tax;
- realized contribution margin;
- promotion and processor cost;
- refund and chargeback loss;
- host payable aging;
- tax payable aging;
- unmatched clearing balance;
- reconciliation mismatch count and value;
- ledger posting delay and duplicate-prevention count.

### Tax metrics

- tax calculation success by jurisdiction and content version;
- missing or expired host registrations;
- effective-date boundary failures;
- invoice/credit-note issuance failures;
- return-to-ledger variance;
- report rejection and correction rate.

Alert thresholds use both count and monetary materiality. Dashboards must label estimate, booked,
captured, earned, payable, paid, and finalized amounts correctly.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Demand or preference model unavailable | Use deterministic price and promotion policy |
| Market/event feed stale | Remove affected adjustment and alert; keep bounded baseline |
| Tax calculation unavailable | Do not guess; block authoritative quote or jurisdiction as policy requires |
| Promotion service unavailable | Quote without optional promotion unless a reserved benefit must be honored |
| Quote store unavailable | Do not create a booking from an unverified client amount |
| Payment provider timeout | Query by idempotency/provider reference before retrying |
| Webhook duplicated | Acknowledge safely; create no duplicate state or ledger effect |
| Ledger posting fails after verified capture | Persist recoverable event, block dependent payout, alert finance |
| Payout provider unavailable | Keep host payable intact and retry safely |
| Reconciliation feed missing | Preserve ledger truth, flag incomplete reconciliation |
| FX rate expired | Re-quote or use contractually defined fallback; never silently improvise |
| Host tax profile incomplete | Apply approved fallback/withholding rule or block payout according to policy |

Financial correctness favors an explicit temporary failure over an invented price, tax, payout, or
ledger balance.

## Testing and verification

### Deterministic pricing tests

- Every precedence combination produces the expected applied rule set.
- Manual override wins exactly where documented.
- Floor, ceiling, net target, and change-rate constraints hold.
- Multi-night, occupancy, length-of-stay, and rate-plan calculations reconcile.
- Promotion stacking, budget, frequency, and funding rules hold.
- Search estimate and quote differences remain within policy.

### Money property tests

- Guest line items always reconcile to guest total.
- Booking summary always reconciles to immutable booking lines.
- Host entitlement minus deductions plus subsidies reconciles to host payable.
- Tax payable is never counted as platform revenue.
- Platform contribution uses the approved finance definition.
- Every ledger transaction balances exactly.
- Reversing a transaction produces zero net effect for the reversed dimensions.
- Cumulative successful refunds never exceed refundable captured value.
- Promotion funding allocations sum exactly to the guest benefit.
- Integer remainder allocation never loses or creates a minor unit.

### Tax tests

- Golden fixtures approved for every launched jurisdiction.
- Effective-date boundary before/at/after change.
- Inclusive and exclusive calculation.
- Per-night, per-person, fixed, threshold, cap, exemption, and compound rules where supported.
- Host registration and entity-type variations.
- Agency and deemed-supplier operating models.
- Full and partial cancellation, refund, invoice, and credit-note behavior.
- Tax provider timeout, duplicate, and content-version replay.

### Concurrency and idempotency tests

- Concurrent booking cannot consume the same stay inventory.
- Repeated quote requests do not duplicate promotion reservation.
- Replayed payment/refund/payout webhook creates one financial effect.
- Concurrent refunds respect cumulative limit.
- Concurrent payout workers cannot consume the same host-payable posting.
- Retried ledger event produces one transaction.

### Reconciliation tests

- Exact match, timing difference, provider fee, partial settlement, duplicate external row, missing
  internal row, missing external row, and currency mismatch fixtures.
- Payout return and bank reversal.
- Tax return totals against tax-payable postings.
- Historical replay from booking financial snapshots and event stream.

### ML evaluation

- Time-based and market-based holdouts.
- Probability calibration and price monotonicity.
- Causal experiment validity and sample-ratio mismatch.
- Incremental contribution, not only conversion.
- Host net and guest-value guardrails.
- Public-price parity and discount-access fairness.
- Cold-start and low-confidence fallback.
- Feature/model drift and stale-feature behavior.
- Shadow decisions reproduce exactly from stored versions.

## Rollout plan

### Phase 0 — Legal, finance, and contract decisions

1. Define agency/MoR/deemed-supplier operating model per launch country.
2. Approve chart of accounts, revenue, contribution, recognition, and reconciliation definitions.
3. Define host fee plans, payout timing, reserves, negative-balance, and refund contracts.
4. Select tax-content ownership/provider and invoice/reporting obligations.
5. Define guest price display, quote-expiry, and promotion disclosure policy.

### Phase 1 — Deterministic price and quote foundation

1. Implement price/fee/rate-plan rule versions and precedence.
2. Implement host base/floor/ceiling/manual override.
3. Create complete quote and line-item model.
4. Compute mandatory total-trip price for search.
5. Accept quote into an immutable booking financial snapshot.

### Phase 2 — Allocation, ledger, and reconciliation

1. Define booking line ownership and promotion funding.
2. Implement balanced posting rules for capture and refund.
3. Connect payment webhooks idempotently to ledger business events.
4. Reconcile provider payments, fees, and bank settlement.
5. Build restricted finance diagnostics and repair workflow.

### Phase 3 — Host payout

1. Create host payable and release policy.
2. Implement payout statements, batching, provider integration, and returns.
3. Add reserves, negative balances, and recovery.
4. Reconcile payout provider and bank movements.
5. Expose host gross-to-net previews and actual statements.

### Phase 4 — Governed tax expansion

1. Launch one approved jurisdiction with golden fixtures.
2. Add host tax profile, registration, withholding, invoice, and credit-note flows.
3. Add effective-dated tax content and operational approval.
4. Add seller reporting and tax-ledger reconciliation where required.
5. Expand country by country; do not create a universal fallback rate.

### Phase 5 — Rule-based market-aware pricing

1. Materialize weekday, seasonal, lead-time, and occupancy adjustments.
2. Add host pricing strategies and net-target settlement projection.
3. Add recommendation explanations, volatility limits, and manual controls.
4. Run in shadow mode before host opt-in or activation.

### Phase 6 — Predictive public pricing

1. Train demand, pace, booking-propensity, cancellation, and risk baselines.
2. Evaluate bounded candidate prices through the real quote engine.
3. Run controlled experiments with host and market guardrails.
4. Optimize realized multi-party value and retain deterministic fallback.

### Phase 7 — Personalized discovery and offers

1. Build contextual guest price/quality preference for ranking.
2. Personalize rate-plan presentation without hidden public-price uplift.
3. Run randomized offer experiments with funding and budgets.
4. Introduce uplift models and then constrained contextual bandits.
5. Audit price parity, benefit access, and long-term guest/host outcomes.

## Decisions required before implementation

- Is the platform an agent, merchant of record, reseller, or potentially deemed supplier in each
  initial country?
- Which legal entity contracts with guest and host, receives payment, and makes payout?
- Is the listing price tax-inclusive or tax-exclusive in each market?
- Which fees are charged to guest and host, and what is each commissionable/taxable base?
- Does the platform guarantee any host minimum net, or present only an estimate?
- Which deductions are included in a host target-net calculation?
- When is host entitlement earned and when is it available for payout?
- Who bears guest refunds, chargebacks, goodwill, FX movements, and processor fees?
- Which discounts may be host-funded, platform-funded, partner-funded, or co-funded?
- What stacking, budget, expiration, and refund rules apply to promotions and credits?
- What is the authoritative tax-content source and operational approval process?
- Which invoice, credit-note, seller-reporting, retention, and data-residency rules apply?
- Which booking, payment, accounting, payout, and presentation currencies launch first?
- What finance definition of platform contribution is used by pricing experiments?
- Which price movements may be experimented with, and how does a host opt in or out?
- Which guest personalization uses are allowed, disclosed, and controllable?
- What public-price parity and fairness thresholds trigger automatic rollback?
- Which provider or internal service owns payment, payout, tax, FX, and invoice operations?

The correct first implementation is not the most sophisticated model. It is a deterministic,
versioned, line-item quote that produces an immutable booking snapshot and balanced financial
allocation. Once those facts can be reproduced and reconciled, dynamic pricing and personalized
offer models can optimize safely without becoming the source of monetary truth.
