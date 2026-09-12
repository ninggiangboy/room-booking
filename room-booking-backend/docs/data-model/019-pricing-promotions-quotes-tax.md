# Migration 019 — Pricing, promotions, quotes, and tax

## Goal

Make every price the platform has ever shown explainable after the fact.

A price is not a number, it is a decision. Migration `018` made inventory safe under concurrency;
this migration makes money defensible under audit — by a guest asking why Friday cost more, by a host
asking why their payout is what it is, and by a tax authority asking under whose rule a figure was
charged.

## Three forces shaping it

**Rules are published as immutable versions.** A guest who booked last March is entitled to the
terms in force last March. If rules were mutable rows, re-reading them today would answer a
different question than the one the guest agreed to. Every priced row cites the version it used.

**A quote is an offer, written once.** An offer that can silently change is not an offer. Quotes
carry an expiry and a calculation hash; re-pricing produces a new quote rather than editing an old
one. The idempotency key makes a retried request return the same offer instead of a second, possibly
different, one.

**Tax liability is recorded per line, not summed away.** A single stay can mix a marketplace-liable
city tax with a supplier-liable VAT. Who is liable, who remits, and under whose rule are three
separate facts, and collapsing them loses exactly what decides who files what.

### Verified behaviour

| Scenario | Expected | Result |
|---|---|---|
| Two overlapping active price overrides on one night | refused | exclusion constraint rejects |
| Adjacent, non-overlapping overrides | accepted | accepted |
| Daily price whose breakdown does not sum to the shown price | refused | arithmetic check rejects |
| A second *current* price for the same night and offer | refused | partial unique index rejects |
| Superseding the old price first, then writing v2 | accepted | accepted |
| Quote whose total does not equal its parts | refused | total check rejects |
| Retried quote request reusing the idempotency key | refused | unique key rejects |
| Quote expiring before it was created | refused | expiry check rejects |
| Editing a **published** price rule version | refused | trigger rejects |
| Returning a published rule version to `DRAFT` | refused | trigger rejects |
| Deleting a published rule version | refused | trigger rejects |
| Retiring a published rule version | accepted | accepted |
| Publishing a rule version with no approver | refused | publication check rejects |
| Discount funded 4m + 1m against a 6m benefit | refused | funding check rejects |
| Retried checkout re-applying one promotion to one quote | refused | partial unique index rejects |
| Releasing the first redemption, then re-reserving | accepted | accepted |
| `PERCENT_OFF` version also carrying a fixed amount | refused | benefit-shape check rejects |
| Publishing a tax rule with no approval record | refused | publication check rejects |
| Two **published** VAT rules in force at one instant | refused | exclusion constraint rejects |
| Closing the first, then publishing the second | accepted | accepted |
| Tax rate above 100% | refused | rate check rejects |
| `MARKETPLACE_LIABLE` line saying the *host* remits | refused | agreement check rejects |

## Tables

**Pricing configuration.** `host_pricing_settings` (the host's standing bounds, effective-dated),
`price_rules` + `price_rule_versions` (stable identity vs. immutable payload),
`manual_price_overrides` (the host's direct instruction, outranking every rule),
`price_recommendations` (what the model proposed, and whether it was taken).

**Materialization.** `daily_price_components` — the answer for one night, which search and the
calendar read instead of re-running the engine.

**Promotions.** `promotions` + `promotion_versions` (frozen terms including the funding split),
`promotion_assignments` (who was offered what), `promotion_redemptions` (who used it and who paid).

**Quotes.** `quotes` (the offer), `quote_nights` (per-night detail), `quote_line_items` (the
authoritative breakdown).

**Tax.** `party_tax_profiles`, `tax_registrations`, `tax_rule_versions`, `tax_calculations`,
`tax_calculation_lines`.

## Design rules

- **Money is integer minor units, never `NUMERIC` and never floating point.** Rates and percentages
  are bounded `NUMERIC` because they are ratios, not money.
- **Line amounts are unsigned with an explicit `direction`.** A negative amount is ambiguous — a
  discount, a refund, a correction, or a sign error — and ambiguity in money is a defect.
- **Summaries must reconcile with their parts.** `ck_quotes_total` and
  `ck_daily_price_components_arithmetic` refuse a row that would charge one number and explain
  another.
- **A discount must be fully funded.** `ck_promotion_redemptions_funding` requires the host and
  platform shares to sum to the benefit: a gap means somebody absorbed a cost no ledger will
  attribute, an excess means the discount was funded twice.
- **Budget is consumed at reservation, not at booking.** Every open quote is an unrecorded
  commitment; a promotion debited only on confirmation will overspend.
- **A published version is evidence.** `price_rule_versions` carries triggers, not just checks: its
  payload cannot be edited, it cannot return to `DRAFT`, and it cannot be deleted. The only forward
  move is `RETIRED`, which ends its life without rewriting what it said.
- **Nothing may be published unapproved.** A published price rule version needs an approver; a
  published tax rule version needs a `market_approval_records` row. Nobody publishes a tax rate
  alone.
- **Restricted identifiers never land here.** `tax_registrations` stores a vault token, a digest,
  and at most the last four characters — the digest recognises a resubmitted number as the same one,
  the token lets the one entitled caller retrieve the plaintext.
- JSONB only for immutable snapshots: the component breakdown, rule payloads, and the tax request
  facts. Everything filterable stays relational.

## What this migration fixes elsewhere

Changeset `019-17` widens `ck_market_approval_records_subject` from migration `013` to accept
`TAX_RULE_VERSION`. Without it the tax rule table is unusable: publishing one demands an approval
record, and an approval record naming a tax rule version was rejected by `013`'s own check. The list
is widened in a forward changeset rather than by editing `013`, which is already applied.

## Deviation from the feature document

`../features/dynamic-pricing-and-settlement.md` keys pricing on `listing_id`, which was correct when
a listing was the sellable thing. Migrations `016` and `018` moved that authority to
`accommodation_type`; pricing follows it here for the same reason. A listing is a presentation and
cannot carry a price that inventory does not have.

The document's `invoices`, `credit_notes`, `seller_reporting_periods`, and the accounting and
settlement tables are **not** in this migration. Ledger, payout, and statement structures land in
`022`, where balanced-posting invariants can be enforced in one place rather than split across two
migrations.

## Exit criteria

- A price shown to a guest can be traced to the rule versions, override, or recommendation that
  produced it.
- A published rule or tax version cannot be altered after any priced row cites it.
- A quote's total always equals its parts, and a retried pricing request never mints a second offer.
- A promotion's cost is always fully attributed to a funder.
- Every tax figure names its jurisdiction, its rule version, its liable party, and its remitter.
