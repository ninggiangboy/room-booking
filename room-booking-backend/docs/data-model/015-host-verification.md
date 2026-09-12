# Migration 015 — Host verification, eligibility, and payout ownership

## Goal

Establish who a seller is, where they operate, whether they are eligible there, and where settlement
may go — before they are allowed to publish or be paid.

`host_profiles` from migration `001` records a bio and an `identity_status` enum. It cannot carry
legal identity, beneficial owners, screening outcomes, permits, or a verified payout destination, and
it cannot explain *why* a capability was granted. D02 has no focused design document yet; this
migration follows `../marketplace-problem-breakdown.md:386-417`.

## Tables

- `host_legal_profiles`: legal identity of the seller, individual or business.
- `beneficial_owners`: who ultimately owns or controls a business host.
- `verification_cases` and `verification_documents`: what was checked, and the evidence for it.
- `screening_checks`: sanctions, PEP, watch-list, adverse-media, age, and market-specific results.
- `host_tax_identifiers`: tax codes, registration status, withholding, seller-reporting scope.
- `regulatory_registrations`: rental permits, tourism registration, zoning, annual night limits.
- `payout_destination_claims`: where money may go, and whether ownership was proven.
- `host_eligibility_decisions`: what the *platform* concluded, and under which policy version.
- `verification_appeals`: contesting a decision, and what the review concluded.

## Design rules

- **Provider output is evidence; platform policy decides.** A vendor returning "identity matched"
  does not grant `CAN_PUBLISH`. `verification_cases` and `screening_checks` hold what was observed;
  `host_eligibility_decisions` holds what the platform concluded and which
  `market_policy_bundles` version it applied.
- **Authority still lives in one place.** An eligibility decision writes a row in
  `capability_grants` (migration `014`) and points at it via `granted_grant_id`. No service asks
  this table whether a host may publish — it asks the grant. `ck_host_eligibility_decisions_grant_link`
  forces a granted decision to name the grant it produced, which is what ties authority back to the
  evidence that justified it.
- **Failure to receive a payout must not erase entitlement.** These tables say *where* money may go.
  What a host is owed is the ledger's business in migration `022`, and is unaffected by a destination
  being rejected or detached.
- **A potential screening match is a question, not an answer.** It must be adjudicated by a named
  person before any capability decision reads it.
  `ck_screening_checks_match_consistency` also refuses the incoherent row where a `CLEAR` result
  carries matches.
- **Individual and business profiles are structurally different.**
  `ck_host_legal_profiles_individual` and `..._business` refuse the mixed row, because an age check
  that silently passes on a company that has no age is worse than one that fails.
- **Identity documents are held by reference with an explicit deletion deadline.** The bytes live in
  encrypted object storage behind the D00 secret boundary; a database dump is not a passport scan.
  `ck_verification_documents_retention` refuses a document with no retention instant, because that is
  a document nobody ever deletes.
- **Tax identifiers are never stored in full.** A digest proves sameness, the last four characters let
  a host recognize which one they gave us, and the value lives behind the secret boundary.
- **Ownership in basis points, not percentages.** 25% is `2500` exactly, so a threshold that decides
  whether someone must be screened at all has no floating-point rounding to argue about.
- **New payout destinations have a cooling-off period**, which blunts payout diversion after an
  account takeover. Only one destination may be `ACTIVE` per holder, market, and currency: two would
  make "where does this payout go" ambiguous at exactly the moment money moves.
- **An overturned appeal must produce a new decision.** Otherwise nothing actually changed for the
  host, and the capability they appealed about stays exactly as it was.
- Expiry and re-screening indexes carry no time predicate; sweeps bind their own decision instant per
  [`../conventions/04-time-and-clock.md`](../conventions/04-time-and-clock.md).

## Relationship to `host_profiles`

`host_profiles` is untouched and still backs the existing host-onboarding endpoint. It remains the
compatibility surface until the supply domain reads legal profiles instead; this migration adds the
target model beside it rather than rewriting a live path, as migration `014` does for identity.

## Exit criteria

- A host cannot publish without an eligibility decision that names its evidence and policy version.
- A confirmed screening match blocks capability until a named person adjudicates it.
- An identity document has a deletion deadline from the moment it is uploaded.
- Rejecting a payout destination leaves the host's ledger entitlement untouched.
- Only one payout destination is active per holder, market, and currency.
