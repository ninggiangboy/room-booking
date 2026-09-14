# `market`

## Goal

Own the one piece of configuration every other domain must resolve against instead of assuming:
which market a fact is governed under, which legal entity is accountable for it, and which exact,
effective-dated policy version applied — so that "Vietnam" (or any market added later) is data every
module reads, never a constant any module hard-codes.

## Forces that shaped it

- **Migration `013` already drew this boundary at the schema level**, and the module reproduces it
  exactly rather than reshaping it: markets, legal entities, provider accounts, and policy bundles
  are effective-dated and immutable — a bundle is never edited in place, a new version supersedes it,
  and a historical replay resolves the version recorded on the row.
- **`market` has no outgoing foreign key to any other module.** It is, along with `identity`, one of
  the two most upstream business modules in the whole schema. Nothing it owns needs to know about a
  booking, a listing, or a user to be valid.
- **Missing or expired configuration fails closed by design.** There is deliberately no default
  market and no fallback bundle; a module that cannot resolve one must fail the operation, not infer
  one from a currency or a phone number.

## What it owns

- **`market` cluster** — `markets`, `market_currencies`, `market_locales`, `market_time_zones`,
  `market_capabilities`, `market_approval_records`, `market_policy_bundles`. `markets` is the
  aggregate root (in-module in-degree 8); the rest are its supported-value and approval history.
- **`entity` cluster** — `legal_entities`, `legal_entity_markets`, `provider_accounts`. The legal
  entity accountable for obligations in a market, and the provider account through which it acts.
- **`content` cluster** — `localized_contents`. Locale-specific text bound to a market's language and
  region rules.

See [`../data-model/013-market-configuration.md`](../data-model/013-market-configuration.md) and
[`../features/multi-market-compliance-and-localization.md`](../features/multi-market-compliance-and-localization.md)
for the full contract.

## Aggregate clusters inside it

11 tables across 3 clusters — small enough that the clustering above is definitive rather than a
starting point; there is no larger internal grouping to discover as the module grows, because a
second market launch adds rows, not new tables.

## What it does not own

Any market-specific business rule belonging to another domain. A pricing rule, a tax rate, or a
disclosure requirement that happens to vary by market is owned by the module that owns the concept
(`pricing`, `ledger`, `booking`) and merely carries a `market_id` foreign key back here; `market`
itself never grows a `pricing_rules` or `tax_registrations` table.

## Public API

`markets`, `legal_entities`, `market_policy_bundles`, and their lookups — every module that needs to
resolve "which market, which entity, which policy version governs this fact" depends on this
module's root package for that lookup. No aggregate root here has a live service yet (this module
has no code today beyond its schema; a `MarketLookup`-shaped API is future work, added when the first
consuming service is written).

## Allowed dependencies

None. No live code exists in this module yet, and even once it does, `market` has no legitimate
reason to import from any business module — it is upstream of all of them.

## Data coupling `verify()` cannot see

`market` is the second-most FK-referenced module in the schema after `identity`, and every one of
these is invisible to `ApplicationModules.verify()` because each is a bare `UUID` column, never a
Java reference — see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).
Tables outside `market` that hold a foreign key into it: `ledger` (20), `payment` (13), `support`
(22), `trust` (9), `growth` (8), `discovery` (7), `pricing` (7), `admin` (6), `hostverification` (6),
`messaging` (6), `booking` (5), `stay` (4), `hostops` (4), `identity` (2), `review` (1), `supply` (1).
None of this is enforced by the module system; it is enforced only by every module correctly
resolving the market/policy version it needs instead of inferring or caching one across a boundary
that outlives the version it captured.

## What it leaves open

`market` is not itself an actor in the `admin`/`change_requests` approval workflow that governs
configuration elsewhere in the system (`market_approval_records` is its own, narrower approval
trail); whether the two converge is a decision left to when `admin`'s live code is written.

## Exit criteria

- All 11 tables and their entities/repositories live under `dev.ngb.backend.market.internal.model`
  / `.repository`, in the `market`, `entity`, and `content` sub-packages above.
- `ApplicationModules.verify()` finds no `allowedDependencies` entry needed for `market` and no
  incoming violation, since it is a leaf with no Java consumers yet.
