# `pricing`

## Goal

Own how a nightly rate becomes a quoted, taxed price — rules, promotions, quotes, and tax — as one
module `booking` reads from, never one it reasons about internally.

## Forces that shaped it

- **`quotes` is the aggregate root** other roots orbit (in-module in-degree 5); `promotion_versions`
  is the second-most-referenced root (3), reflecting that a promotion, like a market policy bundle,
  is effective-dated and superseded rather than edited in place.
- **It is the origin of one of the two named R3 shared-type exceptions in the whole migration.**
  `QuoteLineType`, `LineDirection`, `LineTaxTreatment`, `Refundability`, and `SupplyRole` are used
  outside this module, but every consumer — `booking` above all, which already carries 14 foreign
  keys into `pricing`'s tables — is downstream of `pricing`. Per the shared-type rules in
  [`platform.md`](platform.md#shared-type-rules), R2 ("≥3 modules, no natural owner") does not apply
  here because there *is* a natural owner with every consumer downstream of it, so these five types
  stay in `pricing.types` instead of migrating to `platform`.

## What it owns

- **`rule` cluster** — `price_rules` (root), `price_rule_versions`, `manual_price_overrides`,
  `price_recommendations`, `daily_price_components`, `host_pricing_settings`.
- **`promotion` cluster** — `promotions` (root), `promotion_versions`, `promotion_assignments`,
  `promotion_redemptions`.
- **`quote` cluster** — `quotes` (root), `quote_nights`, `quote_line_items`.
- **`tax` cluster** — `tax_registrations` (root), `tax_rule_versions`, `tax_calculations`,
  `tax_calculation_lines`, `party_tax_profiles`.

See [`../data-model/019-pricing-promotions-quotes-tax.md`](../data-model/019-pricing-promotions-quotes-tax.md)
and [`../features/dynamic-pricing-and-settlement.md`](../features/dynamic-pricing-and-settlement.md).

## Aggregate clusters inside it

18 tables across 4 clusters, each with a clear single root; no table here has no home cluster.

## What it does not own

The booking a quote is attached to (`booking`), the listing/property a price rule is written against
(`supply`), or the ledger entries a settled quote produces (`ledger`) — only the rule and the number.

## Public API

`pricing.types` — the `@NamedInterface` carrying `QuoteLineType`, `LineDirection`,
`LineTaxTreatment`, `Refundability`, `SupplyRole` — is public today even before this module has a
service, because it is the one part of `pricing` another module's future code is already known to
need. No other part of `pricing` is public yet.

## Allowed dependencies

None with live code today. Schema carries 10 foreign keys into `supply`, 10 into `identity`, 7 into
`market`, 1 into `inventory`.

## Data coupling `verify()` cannot see

Inbound: `booking` (14), `growth` (5), `ledger` (2), `hostops` (2). Outbound: `supply` (10),
`identity` (10), `market` (7), `inventory` (1). Once `booking` declares
`allowedDependencies = {"pricing :: types", ...}`, that one dependency is the only part of this
coupling `ApplicationModules.verify()` actually checks; the rest remains schema-only, per
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

Whether `pricing.types` needs to grow beyond the five named enums as more consumers appear is left
open; new consumers should be checked against R0–R3 again rather than assumed to belong here.

## Exit criteria

- All 18 tables and their entities/repositories live under `dev.ngb.backend.pricing.internal.model`
  / `.repository`, in the four clusters above.
- `QuoteLineType`, `LineDirection`, `LineTaxTreatment`, `Refundability`, `SupplyRole` live in
  `pricing.types`, declared with `@NamedInterface("types")`.
- `ApplicationModules.verify()` passes with `pricing`'s only declared dependencies being `supply`,
  `identity`, `market`, and `inventory`.
