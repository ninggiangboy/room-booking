# Migration 034 — growth, loyalty, referrals and incentives

## Goal

Every incentive is somebody's money. A referral bonus, a credit, a gift card, a loyalty benefit and
an affiliate commission are not marketing artefacts that happen to have amounts attached; each one
is a real obligation owed by a named party. The moment the platform stops modelling one as money it
becomes either a discount nobody can audit or a promise nobody can keep.

Domain D17 names four failures to avoid — accounting ambiguity, fraud, hidden price discrimination
and marketplace imbalance — and all four begin the same way: value leaves the platform through a
path that does not name its funder, its rule version, or the ledger account it came out of. This
migration closes that path.

It builds no second discount engine. A promotional benefit reaching a guest is migration 019's
promotion version and redemption, cited here rather than copied, because 019 already carries the
stacking group, the host-funded share, the tax treatment and the redemption reversal. What 034 adds
is where the value comes from before it becomes a discount, who is owed it, when it matures, when it
expires, and what happens when the booking that earned it is cancelled.

D17 is classified in the domain breakdown as a *designed extension* rather than a required target
capability (`marketplace-problem-breakdown.md:156`). It is built here because the full documented
scope was requested, and it is built to the same standard as the required domains: the hard rule
that "incentives use the same quote, funder, ledger and reversal architecture as other discounts" is
what the whole file is organised around.

## Seven forces shaping it

**An incentive names its funder or it does not exist.** A programme that grants value carries the
legal entity that owes it, the accounting book it is recorded in and the ledger liability account it
is drawn against. Every movement of a stored-value balance cites the ledger transaction that posted
it. There is deliberately no column into which a service can write a credit without a posting behind
it, because a balance that exists only in a growth table is a liability the finance close will never
see.

**Expiry belongs to the money, not to the account.** Credits are lots, each with its own origin, its
own expiry, its own refund behaviour and its own market restriction, and a redemption draws down
named lots in expiry order. A single balance column cannot answer which currency, which market or
which promise a guest's remaining value came from, and it silently expires the oldest promise the
platform made alongside the newest.

**Eligibility is a version and a recorded reason, never a query run again later.** Every decision
about whether somebody qualified stores the programme version it was taken under, the outcome, an
approved reason code and the sentence the guest was shown. A guest asking why they were not eligible
is answered from the row that decided it, not by re-running today's rules against a question from
last quarter.

**Anti-self-referral is a constraint, not a hope.** A referrer may not be the referee, a person is
referred once per programme, a reward matures against a stay that actually completed, and a reward
earned by a booking that is later cancelled must be reversed before the referral itself can be
closed. Where a shared device, contact or payment instrument was detected, qualifying anyway
requires a named override rather than a silent pass.

**Contacting someone is not a side effect of being in an audience.** An audience membership cites
the consent that permits contact and the eligibility evaluation that put it there, a touchpoint is
one of migration 024's notification intents rather than a private send path, and the holdout arm can
never be contacted at all — if it could, it would not be a holdout.

**A claim of uplift requires a holdout.** An uplift row cites migration 030's experiment analysis
run, carries the interval around its own effect, and cannot record that a campaign worked unless
that interval excludes zero. Counting redemptions measures who took the money; it does not measure
whether anyone behaved differently, and the domain rule is explicitly that models optimise
incremental behaviour rather than redemption by guests who would have booked anyway.

**A standing alert is standing consent, and it stops itself.** Saved searches, wish lists, price and
availability alerts and waitlist entries each name the consent that permits their notification and
carry an expiry, because a subscription created once and never re-confirmed becomes a channel the
guest cannot remember agreeing to. A waitlist offer may not be sent without a quote behind it:
telling someone a room opened up without holding one is the same fabricated scarcity migration 033
refuses to let a forecast invent.

## Verified behaviour

Two hundred and twenty scenarios were run against the applied schema, forty-eight accepted and a
hundred and seventy-two refused. Every refusal below is paired with an accepted counterpart
somewhere in the suite, because a rule that refuses everything is indistinguishable from a broken
one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | Value-granting programme naming entity, book and liability account | accepted |
| 2 | Value-granting programme with no funder named | refused |
| 3 | Value-granting programme with a book but no liability account | refused |
| 4 | Referral programme declaring it grants nothing | refused |
| 5 | Campaign programme declaring it grants nothing | accepted |
| 6 | Programme opened without an opening instant | refused |
| 7 | Editing the purpose of a published programme | refused |
| 8 | Pausing a published programme | accepted |
| 9 | Deleting a published programme | refused |
| 10 | Credit reward with no validity period | refused |
| 11 | Credit reward with an amount but no currency | refused |
| 12 | Promotion reward not naming a promotion version | refused |
| 13 | Promotion reward citing migration 019's promotion version | accepted |
| 14 | Credit reward that also names a promotion version | refused |
| 15 | Commission reward with no percentage | refused |
| 16 | Reward percentage above one hundred per cent | refused |
| 17 | Version leaving DRAFT with no approver | refused |
| 18 | Version leaving DRAFT with an approver | accepted |
| 19 | Editing the reward amount of a published version | refused |
| 20 | Editing the eligibility rules of a published version | refused |
| 21 | Retiring a published version | accepted |
| 22 | Two versions of one programme sharing a number | refused |
| 23 | Version whose effective window ends before it starts | refused |
| 24 | Eligible outcome with no reason code | accepted |
| 25 | Ineligible outcome with no reason code | refused |
| 26 | Eligible outcome carrying a refusal reason anyway | refused |
| 27 | Ineligible outcome with a reason and an explanation | accepted |
| 28 | Anonymous subject naming an account holder as well | refused |
| 29 | Account-holder subject carrying an anonymous key instead | refused |
| 30 | Anonymous evaluation on its own key | accepted |
| 31 | Evaluation that expires before it was taken | refused |
| 32 | Editing a recorded eligibility decision | refused |
| 33 | Second code for the same owner under one version | refused |
| 34 | Reusing a code string another owner holds | refused |
| 35 | Suspending a code without saying why | refused |
| 36 | Reinstating a suspended code, keeping the reason on the record | accepted |
| 37 | Emailed invitation storing only a digest of the address | accepted |
| 38 | Emailed invitation with no addressee digest at all | refused |
| 39 | Shared link carrying an addressee digest | refused |
| 40 | Invitation accepted with nobody named as having accepted it | refused |
| 41 | Invitation that expires before it was sent | refused |
| 42 | Attribution recorded as pending against an active code | accepted |
| 43 | Referring yourself | refused |
| 44 | Crediting a referrer who does not own the code | refused |
| 45 | Attribution against a suspended code | refused |
| 46 | Attribution inserted already qualified | refused |
| 47 | Qualifying against a shared-device signal with no override | refused |
| 48 | Qualifying against a shared-device signal with a named override | accepted |
| 49 | Qualifying on a booking somebody else made | refused |
| 50 | Qualifying on a booking that is still provisional | refused |
| 51 | Referring one person twice under the same programme | refused |
| 52 | Reward granted before the attribution qualified | refused |
| 53 | Reward granted before it matured | refused |
| 54 | Reward granted after qualification and maturity | accepted |
| 55 | Reward granted with nowhere for the guest to spend it | refused |
| 56 | Two rewards to the same side of one attribution | refused |
| 57 | Closing an attribution while its reward is still granted | refused |
| 58 | Closing an attribution after its reward was reversed | accepted |
| 59 | Reservation exceeding the balance it is held against | refused |
| 60 | Balance driven negative | refused |
| 61 | Closing an account that still holds value | refused |
| 62 | Freezing an account without saying why | refused |
| 63 | A second account of the same kind and currency for one guest | refused |
| 64 | Credit lot with no ledger transaction behind it | refused |
| 65 | Credit lot posted to the ledger | accepted |
| 66 | Lot with more remaining than it ever held | refused |
| 67 | Active lot with nothing left in it | refused |
| 68 | Exhausted lot that still holds value | refused |
| 69 | Expired lot keeping the balance it still had when it closed | accepted |
| 70 | Revoked lot with no reason recorded | refused |
| 71 | Lot that expires before it was granted | refused |
| 72 | Grant entry moving the balance with no ledger transaction | refused |
| 73 | Grant entry citing the ledger transaction that posted it | accepted |
| 74 | Entry claiming a balance the account does not hold | refused |
| 75 | Reservation that also moves the balance | refused |
| 76 | Reservation naming no quote | refused |
| 77 | Reservation moving only what is reserved | accepted |
| 78 | Redemption that spends more than it held | refused |
| 79 | Redemption naming no booking | refused |
| 80 | Expiry entry with no reason code | refused |
| 81 | Entry drawing on a lot held by a different account | refused |
| 82 | Spending from a lot that already expired | refused |
| 83 | Spending from a frozen account | refused |
| 84 | Entry denominated in a currency the account does not hold | refused |
| 85 | Editing a recorded stored-value movement | refused |
| 86 | Two holds against one quote | refused |
| 87 | Hold that never expires | refused |
| 88 | Hold consumed without naming the booking that consumed it | refused |
| 89 | Hold released without a reason | refused |
| 90 | Hold consumed by a booking | accepted |
| 91 | Reservation naming no quote (account moved first) | refused |
| 92 | Redemption naming no booking (account moved first) | refused |
| 93 | Spending from a frozen account (account moved first) | refused |
| 94 | Editing a recorded stored-value movement (valid entry first) | refused |
| 95 | Gift card whose policy forbids expiry but carries one | refused |
| 96 | Gift card whose policy expires it but carries no date | refused |
| 97 | Gift card issued under a no-expiry policy | accepted |
| 98 | Two cards sharing a redemption code | refused |
| 99 | Card redeemed into a lot worth less than its face value | refused |
| 100 | Card redeemed into a lot of its own face value | accepted |
| 101 | Card redeemed into somebody else's balance | refused |
| 102 | Card redeemed after it expired | refused |
| 103 | Breakage recognised while the card was still spendable | refused |
| 104 | Breakage recognised after the card expired | accepted |
| 105 | Breakage under a policy that refunds instead | refused |
| 106 | Breakage recognised with no ledger transaction | refused |
| 107 | Card voided without a reason | refused |
| 108 | Adding a tier to a published loyalty version | refused |
| 109 | Removing a tier from a published loyalty version | refused |
| 110 | Editing the threshold of a published tier | refused |
| 111 | Adding a tier to a version still in draft | accepted |
| 112 | Tier above the base with no measurable threshold | refused |
| 113 | Base tier with no threshold | accepted |
| 114 | Spend threshold with no currency | refused |
| 115 | Two tiers of one version sharing a rank | refused |
| 116 | A second membership in the same programme for one guest | refused |
| 117 | Membership whose window ends before it opens | refused |
| 118 | Suspending a membership without saying why | refused |
| 119 | Accrual from a completed stay | accepted |
| 120 | Accrual from a stay somebody else booked | refused |
| 121 | Stay accrual naming no booking | refused |
| 122 | Manual adjustment with nobody answerable for it | refused |
| 123 | Manual adjustment with an approver and a reason | accepted |
| 124 | Accrual in a currency the membership does not count in | refused |
| 125 | Positive reversal of an earlier accrual | refused |
| 126 | Reversal taking back more than the event contributed | refused |
| 127 | Reversal taking back exactly what was counted | accepted |
| 128 | Two reversals of one accrual | refused |
| 129 | Tier change recording a tier the membership does not hold | refused |
| 130 | Promotion recorded after the membership moved | accepted |
| 131 | A qualification that lowers the tier | refused |
| 132 | A downgrade that raises the tier | refused |
| 133 | A hand-granted tier with nobody named | refused |
| 134 | A hand-granted tier with an approver and a reason | accepted |
| 135 | Enrolment that names a tier it came from | refused |
| 136 | A transition taking effect before the membership existed | refused |
| 137 | Editing a recorded tier change | refused |
| 138 | Campaign leaving DRAFT with no fairness review | refused |
| 139 | Campaign leaving DRAFT reviewed and attested | accepted |
| 140 | Campaign with no frequency cap at all | refused |
| 141 | Campaign holding out everybody | refused |
| 142 | Campaign whose send window closes before it opens | refused |
| 143 | Audience member contacted with no consent recorded | refused |
| 144 | Holdout member contacted once | refused |
| 145 | Contact count raised with no instant to go with it | refused |
| 146 | One person in a campaign audience twice | refused |
| 147 | Member suppressed without a reason | refused |
| 148 | Touchpoint on a treatment member with a matching consent | accepted |
| 149 | Touchpoint on the holdout | refused |
| 150 | Touchpoint citing somebody else's consent | refused |
| 151 | Touchpoint citing consent for a different channel | refused |
| 152 | Touchpoint citing consent for a different category | refused |
| 153 | Touchpoint sent before the consent was granted | refused |
| 154 | Touchpoint on a suppressed member | refused |
| 155 | Touchpoint while the campaign is not running | refused |
| 156 | Touchpoint outside the campaign send window | refused |
| 157 | A third message inside a seven-day window capped at two | refused |
| 158 | A third message once the window has rolled past | accepted |
| 159 | Two touchpoints sharing a notification intent | refused |
| 160 | Uplift claimed with no holdout | refused |
| 161 | Uplift claimed on an interval that contains zero | refused |
| 162 | An interval excluding zero recorded as no detected effect | refused |
| 163 | An interval excluding zero recorded as incremental | accepted |
| 164 | An interval wholly below zero recorded as harmful | accepted |
| 165 | An interval that does not contain its own estimate | refused |
| 166 | Cost per incremental unit on a campaign with no detected effect | refused |
| 167 | A fifty per cent interval sold as a confidence interval | refused |
| 168 | Editing the commission rate of a live agreement | refused |
| 169 | Suspending a live agreement | accepted |
| 170 | Agreement paying half the booking away | refused |
| 171 | Agreement with a year-long attribution window | refused |
| 172 | Click whose window is not the one the agreement grants | refused |
| 173 | Click carrying exactly the agreement's window | accepted |
| 174 | Click naming neither a person nor an anonymous unit | refused |
| 175 | A booking credited after the window closed | refused |
| 176 | A booking credited inside the window | accepted |
| 177 | A second partner crediting itself with the same booking | refused |
| 178 | Commission on a click that was never credited | refused |
| 179 | Commission at a rate the agreement does not pay | refused |
| 180 | Commission whose amount does not follow from its own terms | refused |
| 181 | Commission maturing before the agreed hold has run | refused |
| 182 | Commission on its own terms, held for the agreed period | accepted |
| 183 | A pending commission that already posted to the ledger | refused |
| 184 | An earned commission with no posting behind it | refused |
| 185 | Commission paid before it matured | refused |
| 186 | Commission paid once the hold had run | accepted |
| 187 | An earned commission reversed when the booking cancels | accepted |
| 188 | A paid commission reversed instead of recovered | refused |
| 189 | Two commissions on one credited click | refused |
| 190 | Collection shared by link with no link secret | refused |
| 191 | Private collection carrying a link secret anyway | refused |
| 192 | Two active collections of one guest sharing a title | refused |
| 193 | Archived collection with no instant it was archived | refused |
| 194 | Guest adding their own saved listing to their own collection | accepted |
| 195 | Guest adding somebody else's saved listing to their collection | refused |
| 196 | The same saved listing added to one collection twice | refused |
| 197 | Saved search that notifies with no consent behind it | refused |
| 198 | Saved search that notifies forever | refused |
| 199 | Saved search that only bookmarks, needing no consent | accepted |
| 200 | Saved search citing somebody else's consent | refused |
| 201 | Saved search citing a consent that was withdrawn | refused |
| 202 | The same search saved twice by one guest | refused |
| 203 | Price-drop alert with no observed baseline | refused |
| 204 | Price-drop alert against an observed baseline | accepted |
| 205 | Last-units alert that never says how few is few | refused |
| 206 | Last-units alert naming a real remaining count | accepted |
| 207 | Alert watching both a listing and a saved search | refused |
| 208 | Alert watching somebody else's saved search | refused |
| 209 | Alert recording a trigger with no instant | refused |
| 210 | Waitlist entry naming a listing, a type and an area at once | refused |
| 211 | Waitlist entry on one listing | accepted |
| 212 | Offering a room with no quote holding it | refused |
| 213 | Offering a room the quote actually holds | accepted |
| 214 | Waitlist entry that never expires | refused |
| 215 | Joining the same queue twice | refused |
| 216 | Waitlist entry withdrawn without a reason | refused |
| 217 | Waitlist entry with a price ceiling and no currency | refused |
| 218 | Waitlist entry citing a withdrawn consent | refused |
| 219 | Reordering two wish-list items through a conflicting intermediate position | accepted |
| 220 | Two wish-list items left holding the same position at commit | refused |

Scenarios 219 and 220 were run outside the probe harness. A `DEFERRABLE INITIALLY DEFERRED`
constraint never fires inside a plpgsql subtransaction, because such a subtransaction never reaches
a commit; they were restated as real transactions closed with `SET CONSTRAINTS ALL IMMEDIATE`
followed by `ROLLBACK`, which forces the check without committing anything.

## Tables

**Programmes and eligibility.** `growth_programs` registers one acquisition or retention mechanism
and, where it hands out value, the legal entity that owes it, the accounting book and the ledger
liability account. `growth_program_versions` holds the published terms — eligibility rules and their
plain-words explanation, the reward, the qualification event, the maturity delay, the caps and the
budget — frozen once they leave draft. `growth_eligibility_evaluations` is the append-only record of
who qualified for what, carrying the sentence the guest was shown.

**Referrals.** `referral_codes` is one person's code under one set of terms.
`referral_invitations` records an invitation by a digest of the address rather than the address
itself. `referral_attributions` records who referred whom, the basis, the anti-abuse screening and
any named override. `referral_reward_grants` carries each side's reward, its maturity instant, and
the credit lot or promotion redemption it eventually landed in.

**Stored value.** `stored_value_accounts` is one guest's balance of one kind in one currency, with
the ledger account the liability sits on. `stored_value_lots` is a single grant with its own expiry,
refundability and market restriction. `stored_value_entries` is the append-only movement log, every
balance-moving row citing its ledger transaction and recording the balance it produced.
`stored_value_holds` sets credit aside against one open quote and always expires. `gift_cards`
carries face value, breakage policy, redemption and breakage recognition.

**Loyalty.** `loyalty_tier_definitions` are the tiers of a published version, sealed with it.
`loyalty_memberships` is one guest's standing: current tier, qualification window and its zone, and
the counters. `loyalty_qualifying_events` is the append-only accrual log, where a cancellation
appends a negative row naming the one it reverses. `loyalty_tier_transitions` records each change
with the counts it was judged on.

**Campaigns.** `growth_campaigns` carries the audience rule, the required consent category and
channel, the frequency cap, the holdout share, the send window and the fairness review.
`campaign_audience_memberships` places one person in one arm under one consent.
`campaign_touchpoints` is the append-only record of each message actually sent.
`campaign_uplift_results` records what the campaign measurably changed against its own holdout.

**Affiliates.** `affiliate_partners` is the agreement, frozen once active.
`affiliate_attributions` is a click and the booking it may claim. `affiliate_commissions` is what is
owed, when it matures, when it posted and when it was paid or reversed.

**Guest intent.** `listing_collections` and `listing_collection_items` are wish lists over migration
029's `saved_listings`. `saved_searches` is a kept search and its notification cadence.
`demand_alerts` is a standing request to be told about a price drop or an opening.
`waitlist_entries` is a place in a queue for something not currently available.

## The rules that are not columns

Twelve trigger functions carry the rules a column cannot express. Two more — `platform_append_only`
and `platform_contract_freeze` — come from migration 030 and are reused rather than duplicated.

`loyalty_tier_definition_seal` is the one worth naming first. Freezing the version row does not
freeze its tiers, and the tiers are what a member qualified against. Without this, a published
loyalty programme can gain or lose a tier while its version number stays put, and every membership
measured against it silently changes meaning. This is the fourth shape of the same defect migrations
022, 027, 030 and 033 each found: a frozen parent with an unfrozen child table.

`stored_value_entry_integrity` refuses an entry whose recorded balance does not match the account it
is written against, which is what ties the movement log to something reconcilable; it also refuses a
currency conversion smuggled through an entry, a lot belonging to another account, and spending from
an expired lot or a frozen balance. `gift_card_redemption_integrity` requires a redemption to move
the whole face value into one lot in the same currency and, where the card names a recipient, into
that recipient's balance; it refuses redemption after expiry and breakage before it.

`referral_attribution_integrity` refuses an attribution inserted already qualified, one against a
suspended code, one crediting somebody who does not own the code, qualification on a stranger's
booking or on one that does not stand, and — the important one — closing a referral while a reward
it produced is still outstanding. `referral_reward_grant_integrity` refuses granting before
qualification and before maturity.

`campaign_touchpoint_integrity` is the densest. Every message proves, at the moment it is created,
that the membership is not in the holdout and not suppressed, that the campaign is running and
inside its send window, that the consent cited belongs to the recipient and covers this category and
this channel, that it was in force at the send instant, and that the campaign's own frequency cap
has not been used up.

`loyalty_tier_transition_integrity` requires the transition to describe the move that happened: the
tier it names as reached is the tier the membership now holds, a qualification goes up and a
downgrade goes down. `loyalty_qualifying_event_integrity` refuses accrual on a stranger's booking,
in the wrong currency, or a reversal larger than what it reverses.

`affiliate_attribution_integrity` forces the attribution window to be the agreement's rather than
the click's, and refuses crediting a booking outside it. `affiliate_commission_integrity` requires
the rate and currency to be the agreement's, the maturity to respect the payout hold, payment not to
precede maturity, and refuses reversing a commission that has already been paid — that is a recovery
through migration 022, not an edit here.

`growth_consent_ownership` and `growth_subject_ownership` keep standing subscriptions and wish lists
attached to the right person: a consent cited must be the subject's own and not withdrawn, a wish
list may not hold somebody else's saved listing, and an alert may not watch somebody else's saved
search.

## What probing changed

Three defects were found and fixed before the migration was committed. Two came from reading the
constraints back against the lifecycles they were supposed to describe; one came from probing.

**A two-way equality asserted in both directions where only one was intended.** Three checks were
written as `(state = 'X') = (column IS NOT NULL)`: the qualification of a referral, the granting of
a reward and the landing of that reward in a lot. Each made the terminal `REVERSED` state
impossible to reach without erasing the evidence the reversal is about — the booking that qualified
the referral, the instant the reward was granted, the lot it went into. Split into one-way clauses,
with a comment saying which direction each carries. This is the same defect migration 021 found in
its settlement symmetry and 033 found in its forecast-run completion.

**Freezing a parent row does not freeze a child table.** `growth_program_versions` was frozen by
`platform_contract_freeze`, but loyalty tiers live in `loyalty_tier_definitions`, so a tier could be
added to or removed from a published programme after members had been measured against it. Fixed
with `loyalty_tier_definition_seal`, and the probe fixture had to be rewritten to create the version
as a draft, insert its tiers, and only then publish it.

**A pre-earning state was locked out of a lifecycle it belongs in.** The check over
`affiliate_commissions.earned_at` was an equality over `PENDING` and `WITHHELD` together, which
made it impossible to withhold a commission that had already been earned and posted — precisely the
case a fraud review produces. Split into one-way clauses, leaving `WITHHELD` deliberately
unconstrained in both directions.

Probing also found four scenarios that had been refused by the wrong rule: entries aimed at the
purpose and frozen-account checks were caught first by the balance-agreement trigger, because the
probe had not moved the account into the state its entry claimed. They were restated so the rule
under test is the one that refuses them.

## Design rules this migration follows

- UUID primary keys with `gen_random_uuid()`; callers may supply their own.
- Money as `*_minor BIGINT` beside a `VARCHAR(3)` currency checked against `^[A-Z]{3}$`. No floating
  point anywhere.
- `TIMESTAMPTZ` for instants; `DATE` for civil values, always beside the IANA zone they are computed
  in (`loyalty_memberships.window_time_zone`).
- No `DEFAULT now()` on any column the application writes: see migration 011.
- Status values as `VARCHAR` with a `CHECK`, never a PostgreSQL enum type.
- `version BIGINT NOT NULL DEFAULT 0` with `CHECK (version >= 0)` on every optimistically locked
  table; the append-only tables carry neither `updated_at` nor `version`.
- Constraint naming `pk_` / `uk_` / `fk_` / `ck_` / `idx_`; partial unique indexes for "at most one
  X" rules.
- JSONB only for immutable snapshots — the eligibility rules as published, the screening that was
  run, the audience rule as approved — never for filterable business data.
- Stay ranges half-open `[check_in, check_out)`.

## What this migration does not create

- **Discounts, stacking, host-funded shares and their reversal** are migration 019's `promotions`,
  `promotion_versions`, `promotion_assignments` and `promotion_redemptions`. A reward that reaches a
  guest as a discount names a promotion version; it does not carry a parallel benefit calculation.
- **Quotes and their line items** are 019; **bookings** are 020; **the ledger, accounting books,
  payout instructions and host recoveries** are 022.
- **Consent, notification policies, intents and delivery** are 024. Nothing here sends anything.
- **Saved listings** are migration 029. This migration adds collections over them rather than a
  second saved-listing table.
- **Experiments, holdout registration, assignments and analysis runs** are 030, and **metric
  definitions** are 030 as well. An uplift row cites an analysis run; it does not recompute one.
- **Personalisation settings and erasure directives** are 029.
- `platform_append_only()` and `platform_contract_freeze()` come from 030 and are reused.

## What this migration leaves open

- **Multi-currency stored value across markets.** A guest with balances in two currencies holds two
  accounts and there is no conversion between them, deliberately: conversion is a pricing decision
  with a rate and a spread, and it belongs wherever that rate is governed rather than inside a
  balance movement.
- **Partner status matching.** `loyalty_tier_definitions.partner_status_matchable` says whether a
  tier may be reached that way and the transition reason records that it was, but the evidence for
  the partner status itself lives with whatever verifies it.
- **Affiliate invoicing and reconciliation.** Commissions carry their ledger postings and their
  payout instruction; the statement a partner receives and the dispute process over it are not
  modelled here.
- **Audience construction.** `audience_definition` holds the rule as approved, but the job that
  turns it into memberships, and the data products it reads, belong to migration 030's pipeline
  registry.

## Deviations from the plan and the feature document

The plan listed this migration as "referrals, guest credits, loyalty tiers, campaigns, gift
cards/stored value, affiliate tracking, waitlists, wish lists, saved searches, price/availability
alerts". Everything on that list is here. Four things were added beyond it:

- **`growth_programs` and `growth_program_versions`.** The plan implied a table per mechanism. A
  shared, versioned programme registry is what makes "eligibility is versioned and explainable" a
  property of the schema rather than of five separate implementations, and it is the one place the
  funder and the ledger liability can be required once.
- **`growth_eligibility_evaluations`.** The hard rule says eligibility is explainable. An
  explanation that is recomputed on demand is not an explanation of the decision that was taken.
- **`campaign_uplift_results`.** The hard rule says models optimise incremental behaviour rather
  than redemption. Without a row that cannot exist without a holdout, nothing in the schema
  distinguishes the two.
- **`stored_value_holds`.** Credits reaching a quote need the same reserve-and-release treatment
  inventory gets, or two open checkouts spend the same credit.

D17 is a designed extension in the breakdown rather than a required capability, and nothing in
012–033 depends on these tables. Dropping this file leaves the required target schema complete.

## What this closes

- Domain D17 in full: referral qualification with caps and anti-self-referral checks; guest credits
  with currency, expiry, market and refund behaviour; loyalty tiers with qualification windows,
  downgrade and partner matching; campaign audience, consent, frequency, attribution and incremental
  uplift; first-booking, reactivation, destination and supply campaigns; gift cards with their
  regulatory and breakage concerns; affiliate tracking with commission, fraud screening,
  cancellation reversal and payout; and waitlists, wish lists, saved searches and price and
  availability alerts.
- With 034 applied, the documented target schema for D00–D23 is complete. D23 introduces no tables;
  it is an operational concern satisfied by migration 012's primitives.

## Exit criteria

- Thirty-seven changesets apply to an empty database and to one already at 033.

- Two hundred and twenty probe scenarios: forty-eight accepted, a hundred and seventy-two refused,
  no scenario matching zero rows and no unexpected error.

- Full rollback of all thirty-seven changesets leaves no table, no function and no changelog row
  behind, and migration 030's two shared trigger functions intact; re-application is clean.

- The enum-width audit across 012–034 reports nothing, and the forty-six Java enum bindings match
  their `CHECK` vocabularies exactly.

- Model-against-schema verification reports no issue in either direction across all twenty-eight
  aggregates.

- `compileJava` and `javadoc` are clean at the unchanged hundred-warning baseline, and the
  application boots with four hundred and twenty-one JDBC repositories.
