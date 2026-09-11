# Migration 013 — Market configuration and policy provenance

## Goal

Make the rules that governed a decision recoverable years after the decision, and make a decision
impossible when no approved rules exist.

The platform launches in Vietnam, but "Vietnam" cannot be a constant compiled into pricing, tax,
payout, and disclosure logic. Every consequential decision has to name the market whose rules
governed it, the legal entity accountable for it, and the exact policy version it applied.

## Tables

- `markets`: stable code, contract currency, default locale and IANA zone, lifecycle, activation.
- `market_currencies`, `market_locales`, `market_time_zones`: supported values, kept relational
  because eligibility and rendering filter on them.
- `legal_entities` and `legal_entity_markets`: who is accountable, in which market, for which span.
- `market_policy_bundles`: effective-dated, immutable, digest-identified product/legal/tax/privacy/
  retention/cancellation/disclosure/payout rules.
- `market_capabilities`: which capability, method, and rail are available, with effective dates.
- `provider_accounts`: approved external integration context and a secret *reference* — never a
  secret.
- `localized_contents`: semantic key plus immutable reviewed rendering per locale.
- `market_approval_records`: who approved what, when, on what evidence, and what superseded it.

## Design rules

- **Configuration is versioned and superseded, never edited.** A policy bundle row is immutable. A
  new version supersedes it with its own effective span, so a historical replay resolves the version
  recorded on the booking rather than whatever is active today.
- **Nothing overlaps.** `ex_market_policy_bundles_no_overlap`, `ex_market_capabilities_no_overlap`,
  and `ex_legal_entity_markets_no_overlap` are GiST exclusion constraints over `tstzrange`. Without
  them, two decisions made a second apart could apply different approved rules, and "who is the
  counterparty to this booking" could have two answers.
- **Missing configuration fails closed.** There is no default market and no fallback bundle.
  Inferring a market from currency, phone number, or IP address is how a booking gets contracted
  under rules nobody approved.
- **Approved content names its reviewer.** `ck_localized_contents_review` refuses an `APPROVED` row
  without a reviewer and a review instant, because content that renders a legal disclosure with no
  reviewer is exactly the content that must not be renderable.
- **Time zones are full IANA `Region/City` identifiers.** `UTC`, `GMT`, and `CET` are rejected: they
  carry no DST history, and property-local dates depend on that history. This matches
  [`../conventions/04-time-and-clock.md`](../conventions/04-time-and-clock.md).
- **Secrets stay in a secret manager.** `provider_accounts` holds `secret_reference` and
  `webhook_secret_ref`, so a database dump is not a credential leak.
- `COALESCE(method_key, '')` in the capability exclusion constraint makes two rows with no method
  collide as the same capability rather than pass as two unrelated `NULL`s.

## What is deliberately not seeded

Changeset `013-09` seeds only the objective facts about Vietnam: its code, contract currency
`VND`, default locale `vi-VN`, and zone `Asia/Ho_Chi_Minh`. The market is seeded `DRAFT`.

No legal entity, policy bundle, provider account, or approval record is seeded. Those are the output
of the decision gate in
[`../features/multi-market-compliance-and-localization.md`](../features/multi-market-compliance-and-localization.md):
an accountable owner must approve the platform's legal role, tax interpretation, and providers.
Seeding an `APPROVED` bundle nobody approved would fabricate the exact evidence these tables exist to
preserve, and would defeat the fail-closed rule — publication, quoting, and booking are meant to be
impossible until real configuration is loaded.

**Activating the market is therefore a prerequisite for the supply and booking domains**, not a
migration step.

## Exit criteria

- A decision made today can still name the exact bundle version it applied after that bundle is
  superseded twice.
- Two overlapping versions of one bundle type in one market cannot both exist.
- A market with no approved bundle of a required type cannot be used to publish, quote, or book.
- A database dump contains no provider credential.
