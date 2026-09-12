# Migration 026 — Reviews and reputation

## Goal

A completed booking creates a bounded right to say something, not a review. This migration exists so
that what somebody said about a stay is attributable, sealed until both sides have spoken or the
window has closed, immutable once written, and separable from every derived number computed out of
it — and so that none of those properties depends on a service remembering them.

Twenty-nine tables, thirty-six changesets. Seven of those changesets contain no table at all: they
carry the rules no column can express.

## Six forces shaping it

**The right and the review are different objects.** Completion opens a right with a deadline and a
policy snapshot; exercising it produces one review aggregate. One right per cycle and direction, one
review per right, one review per booking and direction — three unique constraints, because a second
review for the same stay is a rating the platform never earned.

**Double blind is a timing rule, and timing rules leak.** A review is sealed until both sides submit
or the deadline passes, so nothing may publish before its cycle revealed. That is a trigger, not a
column, because a publication row written a moment early tells the other party what happened. The
cycle state is monotonic for the same reason: a late moderation removal changes a review's
visibility, never the fact that the cycle revealed.

**Original meaning is immutable; corrections are additive.** Revisions are insert-only and numbered,
the category ratings belonging to a submitted revision cannot be appended to afterwards, and a
substantive edit after reveal is refused outright. Typographical, privacy and legal corrections are a
named *kind* of revision rather than an exception a service may grant itself.

**Visibility has five dimensions, not one status.** Authoring, cycle disclosure, moderation, public
projection and intelligence eligibility move independently and by different authorities; the feature
document is explicit that collapsing them produces an unreviewable cross-product. Publication
intervals are rows, so removing and restoring a review leaves a history rather than rewriting a
timestamp.

**Derived numbers are projections and say what they were derived from.** The public average stores
the exact sum, count and distribution it came from, and check constraints make those agree. Aspect
profiles, reviewer attention and reputation views carry the taxonomy, model and policy versions plus
a manifest digest. None of them is the source of a rating.

**Missing evidence is unknown, not bad.** A reputation view with too little behind it records
`INSUFFICIENT_EVIDENCE` and a reason rather than a low number, and an aspect value may only claim a
strength or a weakness when it has mentions behind it.

## Verified behaviour

One hundred and twenty-one scenarios were executed against PostgreSQL 17 with the migration applied.
Every refusal is paired with an accepted counterpart, so the table shows a rule being enforced rather
than a table that refuses everything.

| Scenario | Result |
| --- | --- |
| A draft review policy, then publishing it | accepted |
| A second open published version for one policy and market | refused |
| A published policy nobody approved | refused |
| Lengthening the window of a published policy | refused |
| Closing its effective interval | accepted |
| Deleting a published policy | refused |
| A submission window of zero days | refused |
| A review cycle for a completed booking | accepted |
| A second live cycle for one booking | refused |
| A deadline before the window opens | refused |
| A state and a rank that disagree | refused |
| Revealing without recording when, why or under which epoch | refused |
| Sealing after the first submission | accepted |
| Unsealing a cycle | refused |
| Moving a historical deadline | refused |
| Winding a fencing token backwards | refused |
| Revealing once, with an epoch and a reason | accepted |
| Issuing a second reveal epoch | refused |
| The guest's right to review the listing | accepted |
| A second right in the same direction | refused |
| A listing review with no listing named | refused |
| A co-host writing for the host, with both recorded | accepted |
| An exercised right that produced no review | refused |
| Revoking a right without a superseding booking fact | refused |
| The review aggregate for that right | accepted |
| A second review for one right | refused |
| A second review for one booking and direction | refused |
| Making a sealed review public | refused |
| A submitted review with no revision behind it | refused |
| The first revision of a review | accepted |
| Two revisions sharing a number | refused |
| A rating of six out of five | refused |
| A redaction that names neither reason nor predecessor | refused |
| The same client submission key twice | refused |
| Rewriting or deleting a revision | refused |
| A category rating on a revision not yet submitted | accepted |
| A category both rated and not applicable | refused |
| A category that is neither rated nor declared inapplicable | refused |
| The same category twice on one revision | refused |
| Changing a category rating after the fact | refused |
| Appending a category rating to a submitted revision | refused |
| A substantive edit after the cycle revealed | refused |
| A privacy redaction, which stays possible | accepted |
| Publishing a review whose cycle is still sealed | refused |
| Publishing once the cycle revealed | accepted |
| Backdating a publication to before the reveal | refused |
| A publication carrying a reveal epoch the cycle never issued | refused |
| Publishing one review's text under another review | refused |
| A second open publication interval for one review | refused |
| Closing the interval when moderation removed it | accepted |
| Moving the end of a closed interval | refused |
| Deleting a publication interval | refused |
| A fresh interval when the review was restored | accepted |
| An interval overlapping one already recorded | refused |
| The host starting a response | accepted |
| A second live response to one review | refused |
| A submitted response with no text behind it | refused |
| The text of the response | accepted |
| Rewriting a response after the fact | refused |
| Private feedback the public never sees | accepted |
| Consent-based feedback with no consent to point at | refused |
| Quietly letting private feedback into the aggregates | refused |
| Deleting held private feedback | refused |
| A machine translation of an exact revision | accepted |
| The same engine translating the same text twice | refused |
| A translation with no translation in it | refused |
| A photograph awaiting scanning | accepted |
| Showing a photograph that has not been scanned | refused |
| Applying a moderation decision to an exact revision | accepted |
| Applying the same decision event twice | refused |
| Rewriting which decision was applied | refused |
| Somebody else finding the review helpful | accepted |
| The author voting for their own review | refused |
| Voting twice on one review | refused |
| Recording that the review was displayed | accepted |
| Rewriting an interaction event | refused |
| The public average, with the arithmetic it came from | accepted |
| A distribution that does not add up to the count | refused |
| A sum no two ratings could produce | refused |
| An average computed from no ratings at all | refused |
| Two aggregates for one subject, dimension and rule version | refused |
| Winding the publication epoch backwards | refused |
| Moving a body of ratings onto a different subject | refused |
| Recording which publication contributed the five | accepted |
| Counting one publication into one aggregate twice | refused |
| Changing what a publication contributed | refused |
| A draft taxonomy with an aspect in it, then activating it | accepted |
| Adding an aspect to an active taxonomy | refused |
| Editing an active taxonomy | refused |
| A second active version of one taxonomy | refused |
| A successful extraction run | accepted |
| A second successful run over the same text and versions | refused |
| A run that succeeded without producing anything | refused |
| One thing the review said about noise | accepted |
| A confidence above one | refused |
| A span that ends before it starts | refused |
| Excluding a mention from the profiles | accepted |
| Rewriting or deleting a mention | refused |
| An aspect profile with the counts behind each value | accepted |
| A sentiment breakdown that does not add up | refused |
| A declared strength with nothing behind it | refused |
| An uncertainty interval that excludes its own mean | refused |
| A second current profile for one listing | refused |
| The ranking-quality projection, kept apart from the public average | accepted |
| A second current quality profile for one listing | refused |
| What this reviewer repeatedly notices, for a named purpose | accepted |
| An opted-out profile that is still current | refused |
| An attention score above one, or derived from no mentions | refused |
| An approved reputation purpose, with its consumers named | accepted |
| A published purpose with no fairness evidence behind it | refused |
| A published purpose nobody is allowed to consume | refused |
| Too little evidence recorded as unknown rather than as bad | accepted |
| An insufficient answer that still carries a number | refused |
| A real answer that also claims insufficient evidence | refused |
| A second live answer to the same question | refused |
| An answer that expired before it was computed | refused |

Full rollback and re-apply were each proved twice: zero tables, zero functions, zero changelog rows
after rollback, and a clean re-apply afterwards.

## Tables

**Policy and eligibility.** `review_policy_versions`, `review_cycles`, `review_rights`.

**Content.** `review_records`, `review_revisions`, `review_category_values`, `review_publications`,
`review_responses`, `review_response_revisions`, `review_private_feedback`, `review_translations`,
`review_media`.

**Moderation and public interaction.** `review_moderation_applications`, `review_helpful_votes`,
`review_interaction_events`.

**Rating and quality projections.** `review_public_aggregates`, `review_aggregate_contributions`,
`listing_quality_profiles`, `host_review_profiles`.

**Aspect intelligence.** `aspect_taxonomy_versions`, `aspect_definitions`, `review_extraction_runs`,
`review_aspect_mentions`, `aspect_profile_versions`, `aspect_profile_values`.

**Reviewer evidence and reputation.** `reviewer_attention_profiles`, `reviewer_attention_values`,
`reputation_policy_versions`, `contextual_reputation_views`.

## Design rules this migration follows

- Status values are `VARCHAR` + `CHECK`, never PostgreSQL enum types; the enum-width audit found three
  columns narrower than their own longest literal and they were widened before the migration was
  committed.
- No `DEFAULT now()` on any application-written column, and no partial index predicate reads the
  clock — the worker binds its own instant.
- Worker queues claim with `FOR UPDATE SKIP LOCKED` under a lease with an owner and an expiry, plus a
  monotonic fencing token where a stalled worker could otherwise write a late result.
- Append-only and freeze triggers use the `%ROWTYPE` candidate pattern, and cover `INSERT` where a
  child could otherwise be appended to a frozen parent — `aspect_definitions` under an active
  taxonomy, `review_category_values` under a submitted revision.
- Overlapping publication intervals are refused by a GiST exclusion constraint, the same device
  migration `018` uses against double-booking a unit.
- The forward-pointing supersession foreign keys are `DEFERRABLE INITIALLY DEFERRED`, because the
  live-row indexes force the outgoing row to name its replacement before that replacement exists.

## What this migration leaves open

- **No legacy backfill.** The feature document describes importing the old `reviews` table as
  `LEGACY_IMMEDIATE_PUBLICATION` revisions. Migration `016` retired that table with the rest of the
  listing-centric stack, so there is nothing to import; the revision kind and the reveal rule that
  would express it exist for a future import from an external source.
- **No moderation decisions.** Generic content items, decisions, reports and appeals belong to trust
  and safety, which is migration `027`. What lives here is the applied-decision projection this
  domain needs to be correct about visibility.
- **No reviewer calibration model.** The document describes adjusting for reviewers who rate
  systematically high or low. The tables support it — attention profiles and effective evidence are
  in place — but the calibration itself is a model, not a schema.
- **No analytical storage for interactions.** `review_interaction_events` is deliberately narrow and
  will move to the data domain in migration `030`; nothing in the review write path reads it.

## Deviations from the plan and the feature document

**No second idempotency, outbox, inbox, jobs or audit table.** The document proposes
`review_command_idempotency`, `review_outbox_events`, `review_inbox_receipts`, `review_jobs` and
`review_audit_events`. Migration `012` delivered `command_idempotency_records`, `outbox_events`,
`consumer_inbox_receipts` and append-only `audit_events`. For the job queue this migration follows the
house pattern set in `021` and `024` — an owner, an expiry and a fencing token on the row being worked
— rather than a separate table, so a reveal or an extraction is claimed where it lives.

**Publication is checked against the cycle, not only against the service.** The document says
publication must not precede cycle reveal. A column cannot express it, because the reveal lives on
another row, so a `BEFORE INSERT` trigger refuses a publication whose cycle has not revealed, one that
starts before the reveal instant, one carrying an epoch the cycle never issued, and one citing a
revision belonging to a different review.

**Cycle state carries an orderable rank.** The document says the state is monotonic. `state_rank` is
stored beside `state` and paired to it by a check constraint, so the guard compares one integer rather
than parsing names, and `CLOSED_EMPTY` shares a rank with `REVEALED` because both are terminal
outcomes of the same window.

**The self-vote rule is a check constraint.** The document suggests service enforcement "plus a
database denormalized author defense if required". The author is denormalized onto every vote row and
the constraint is unconditional, because this is the cheapest possible defence against the most
obvious manipulation.

**The distribution must equal the count, and the sum must be possible.** The document asks for
non-negative sums and a distribution summing to the rating count. Both are here, plus a bound tying
the sum to the count: with ratings from one to five, a sum below the count or above five times it
cannot have come from real ratings, and the displayed average could not be trusted either.

**A published reputation purpose must name its consumers and its fairness evidence.** The document
lists these as required fields. `ck_reputation_policy_versions_publication` and
`ck_reputation_policy_versions_consumers` make them conditions of publication, which is what stops a
general-purpose score being created by leaving the governance fields blank.

## What this closes

Nothing was left dangling by an earlier migration for this one to pick up, and nothing here leaves a
forward reference open. `review_cycles` takes references to `bookings` and `booking_revisions` from
`020` and `023`, `listings` and `properties` from `016`, `account_holders` from `014`, and
`review_extraction_runs` references `provider_accounts` from `013` — all of which already existed.

The moderation decision identifiers on `review_publications` and `review_moderation_applications` are
deliberately unconstrained references rather than foreign keys: the decisions live in migration `027`,
and this domain must keep working when a decision is recorded in a system it does not own.

## Exit criteria

- Thirty-six changesets applied cleanly, and the enum-width audit returns no rows.
- One hundred and twenty-one probe scenarios behave as tabulated above.
- Full rollback leaves no table, no function and no changelog row; re-apply is clean. Both proved
  twice.
- Every model field maps to a real column and every `NOT NULL` column has a field, nullability
  included.
- `compileJava` and `javadoc` are clean, and the application boots with all twenty-nine repositories
  resolved.
