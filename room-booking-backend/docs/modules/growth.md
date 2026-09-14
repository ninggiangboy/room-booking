# `growth`

## Goal

Own referrals, stored value, loyalty, campaigns, affiliates, and saved demand — as the module where
every incentive names whose money it is, and whose absence costs no other module an invariant.

## Forces that shaped it

- **It is schema-complete ahead of its classification, and that is safe in exactly one direction.**
  `../marketplace-problem-breakdown.md` classifies D17 as a designed extension rather than a
  required target capability, and the foreign-key graph proves the classification can stand: this
  module carries 66 foreign keys into other modules and **receives none back**. The whole of it can
  be left unbuilt without any required domain losing an invariant; the reverse is not true, because a
  referral cannot qualify before bookings exist and credit cannot be granted before the ledger does.
- **Every programme names its owing legal entity and ledger account.** That is what migration `034`
  built, and it is why `growth` holds 10 foreign keys into `ledger` — the second-heaviest outbound
  edge in the module. An incentive without a named payer is an unfunded liability, so this coupling
  is intentional and must not be loosened into a soft reference.
- **`growth_program_versions` is the dominant root** (in-module in-degree 9) rather than
  `growth_programs` (3). Referral codes, loyalty tiers, campaigns, affiliates, gift cards, and
  eligibility evaluations all hang off the *version*, not the programme, because an incentive's terms
  must be frozen at the moment somebody qualified under them.
- **`demand` is a real cluster, not a leftover.** Saved searches, demand alerts, waitlist entries,
  and listing collections are guest-side retention artifacts with no programme behind them; folding
  them into `campaign` would imply a campaign owns them, which nothing in the schema supports.

## What it owns

- **`program` cluster** — `growth_programs` (root), `growth_program_versions`,
  `growth_eligibility_evaluations`.
- **`referral` cluster** — `referral_codes` (root), `referral_invitations`, `referral_attributions`,
  `referral_reward_grants`.
- **`storedvalue` cluster** — `stored_value_accounts` (root), `stored_value_lots`,
  `stored_value_entries`, `stored_value_holds`, `gift_cards`.
- **`loyalty` cluster** — `loyalty_tier_definitions` (root), `loyalty_memberships`,
  `loyalty_qualifying_events`, `loyalty_tier_transitions`.
- **`campaign` cluster** — `growth_campaigns` (root), `campaign_audience_memberships`,
  `campaign_touchpoints`, `campaign_uplift_results`.
- **`affiliate` cluster** — `affiliate_partners` (root), `affiliate_attributions`,
  `affiliate_commissions`.
- **`demand` cluster** — `saved_searches` (root), `demand_alerts`, `waitlist_entries`,
  `listing_collections`, `listing_collection_items`.

See [`../data-model/034-growth-and-loyalty.md`](../data-model/034-growth-and-loyalty.md). This module
has **no feature design document of its own** — the largest such gap in the system, given its size.

## Aggregate clusters inside it

28 tables across 7 clusters — the most clusters of any module except `analytics`, and for the same
reason: this is a collection of related programmes rather than one lifecycle. `gift_cards` sits in
`storedvalue` rather than in its own cluster because a gift card is a funding source for a stored
value lot, not a separate instrument.

## What it does not own

The money. Credit is expiring lots here, but the obligation and its accounting live in `ledger`
(referenced 10 times), the discount's effect on a payable amount lives in `pricing` (5), and the
message that tells a guest about a campaign lives in `messaging` (10). `growth` owns eligibility and
entitlement; it owns no balance that the ledger does not also know about.

## Public API

No live service exists yet, and nothing depends on this module. The eventual API is an
eligibility-and-entitlement surface; stored value in particular must be reachable only through a
published operation, never as an entity, because a lot consumed twice is money created.

## Allowed dependencies

None with live code today. Schema carries 16 foreign keys into `identity`, 10 each into `ledger` and
`messaging`, 8 into `market`, 7 into `booking`, 5 each into `pricing` and `supply`, 4 into
`analytics`, 1 into `discovery`.

## Data coupling `verify()` cannot see

Inbound: none. Outbound: nine modules, 66 foreign keys — the heaviest outbound coupling in the
system. Zero inbound edges is the property that makes this module optional, and it is the one
property most easily destroyed: the first foreign key pointing *into* `growth` converts a designed
extension into a required dependency of whatever added it. Treat such an edge as a change to the
target-release classification, not as a schema detail. None of this is visible to
`ApplicationModules.verify()`; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether a campaign touchpoint should be delivered by calling `messaging` directly or by publishing an
event this module owns is left to its design. Consent is the constraint either way — a campaign may
not contact anyone before `messaging`'s `communication_consents` says so, and 5 of this module's
foreign keys exist to enforce exactly that.

## Exit criteria

- All 28 tables and their entities/repositories live under `dev.ngb.backend.growth.internal.model` /
  `.repository`, in the seven clusters above.
- `ApplicationModules.verify()` passes with `growth` declaring dependencies on exactly the nine
  modules listed above and nothing else.
- No module declares a dependency on `growth`, and no migration adds a foreign key into it.
