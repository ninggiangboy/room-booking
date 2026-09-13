# Migration 029 — Discovery projections

## Goal

Discovery reads; it never decides. Every table here is derived, rebuildable, and subordinate to the
authoritative rows it summarises: a listing profile is not inventory, a preference feature is not a
filter, an aspect score is not a review, and a ranking exposure is not an outcome.

If every table in this migration were truncated, search would degrade to the deterministic baseline
and nothing about a booking, a price, or a review would change. That property is the point, and a
number of the constraints below exist only to keep it true.

Twenty tables, twenty-eight changesets. Eight of those changesets contain no table at all: they carry
the rules no column can express.

## Eight forces shaping it

**Behaviour is evidence, not truth.** A `discovery_events` row records that something was observed —
an impression rendered, a listing clicked, a booking started. It is append-only, deduplicated by the
client-issued event key, and it never redefines what a booking or a review says. Transactional
outcomes reconcile from their own domains; the event stream only helps attribution. An event claiming
a rank must name the search request that produced that rank, so a fabricated impression has nowhere
to attach.

**Raw event growth may not compete with bookings.** Every event row carries its own expiry and a
retention class, because an unbounded clickstream in the transactional database is a capacity
incident waiting for a traffic spike. Nothing here stores an exact address, a coordinate, a token, a
payment detail, an IP address, or an unrestricted user-agent string; the columns for them
deliberately do not exist, so no future job can be tempted to fill them.

**Opting out means the next job may not rebuild it.** A personalization opt-out that clears a cached
profile and lets the following batch reconstruct it from the same behaviour is not an opt-out. A
guest preference profile is therefore refused outright for a subject who turned behavioural profiling
off, and an erasure directive carries an evidence cutoff that every later profile must respect: a
profile whose evidence begins before the cutoff is refused by trigger, not by convention. Each
derived store records its own application of the directive, so "propagated" is a row rather than a
claim.

**Importance and direction are different numbers.** A guest who always chooses quiet listings and a
guest who mentions noise constantly are not the same guest. Every preference feature stores
importance, preferred level, and confidence separately, and keeps long-term and recent values apart
so one can be expired without destroying the other. Session intent is a third thing again: it lives
in its own table, it always has an expiry, and it is never the durable profile.

**Low evidence is uncertainty, not poor quality.** An unknown aspect contributes nothing, positive or
negative. A profile value with no evidence may not present itself as a strength or a weakness, a
listing with no reviews is shrunk to a recorded market prior rather than scored zero, and the prior
fallback level actually used is stored — because "we fell back to the country prior" and "this
neighbourhood is like this" are different statements.

**Exposure is a budget, not a side effect.** Exploration traffic given to new or uncertain listings
has a window, a traffic ceiling, a per-listing cap, and a quality floor. An exploration exposure that
names no open window is refused, and a window can never report consuming more than it allocated.
Sponsored placement is a separate, labelled reason that may never masquerade as organic relevance.

**An explanation must be able to point at something.** Reason codes are an approved, versioned
vocabulary with minimum evidence and confidence thresholds attached. A reason reaches a result
through a foreign key into that vocabulary — a free-text array cannot be checked — and a reason
declared personalized may not appear on an anonymous result. Where a reason cannot be supported, the
reason is omitted; the listing is not.

**A rank must be reproducible.** An exposure names its ranking epoch, its policy version, its model
version, its position, its score, and the digest of the feature vector that produced it. Epoch,
policy and model must agree with each other, so a cursor bound to one epoch cannot silently be served
by a different ranker. Exposures are append-only: the record of what was shown is not editable after
the fact, which is the whole basis of position-bias correction later.

## Verified behaviour

A hundred and seventy-one scenarios were run against the applied schema, forty-nine accepted and a
hundred and twenty-two refused. Every refusal below is paired with an accepted counterpart somewhere
in the suite, because a rule that refuses everything is indistinguishable from a broken one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | A guest saves a listing | accepted |
| 2 | The same guest saves the same listing twice | refused |
| 3 | Unsaved without an unsaved instant | refused |
| 4 | Unsaving names the instant | accepted |
| 5 | Re-saving while an unsaved instant is still set | refused |
| 6 | Re-saving clears the unsaved instant and counts the save | accepted |
| 7 | Unsaved before it was saved | refused |
| 8 | A save from a surface nobody declared | refused |
| 9 | A save that has happened zero times | refused |
| 10 | Personalized ranking with profiling turned off | refused |
| 11 | A regulatory decision that names no market | refused |
| 12 | Profiling kept while personalized ranking is declined | accepted |
| 13 | A second settings row for the same guest | refused |
| 14 | A guest asks for their behavioural profile to be cleared | accepted |
| 15 | A regulatory erasure with no legal basis recorded | refused |
| 16 | A regulatory erasure that names its order | accepted |
| 17 | Completed without saying when | refused |
| 18 | Completed before it was requested | refused |
| 19 | The profile store records what it erased | accepted |
| 20 | A training set declines to erase and explains nothing | refused |
| 21 | A deferral that says why and until when | accepted |
| 22 | The same store applies the same directive twice | refused |
| 23 | An erasure record is rewritten after the fact | refused |
| 24 | A personalized weight with no confidence floor beneath it | refused |
| 25 | Exploration traffic with no quality floor | refused |
| 26 | An enrichment deadline longer than the whole latency budget | refused |
| 27 | A policy goes live with nobody having approved it | refused |
| 28 | A second active policy for the same key and market | refused |
| 29 | A candidate cap of zero | refused |
| 30 | The weights of a live policy are edited in place | refused |
| 31 | A live policy is superseded and closed off | accepted |
| 32 | A superseded policy is put back into service | refused |
| 33 | A policy that served results is deleted | refused |
| 34 | A shadow model quietly given ten percent of traffic | refused |
| 35 | An active model nobody approved or promoted | refused |
| 36 | Falling back to a previous model that is not named | refused |
| 37 | A model trained before its own training window closed | refused |
| 38 | A calibration method nobody declared | refused |
| 39 | A registered model with a declared calibration method | accepted |
| 40 | The artifact behind a serving model is swapped | refused |
| 41 | A serving model is rolled back with a reason | accepted |
| 42 | A rolled back model is restored to service | refused |
| 43 | A rolled back model that keeps half the traffic | refused |
| 44 | A second open epoch in the same market | refused |
| 45 | A closed epoch beside the open one | accepted |
| 46 | An epoch pulled from under live cursors with no reason given | refused |
| 47 | A closed epoch with no closing instant | refused |
| 48 | An epoch that closes before it opens | refused |
| 49 | A claim that needs evidence but names no supporting feature | refused |
| 50 | A personalized claim with no confidence threshold | refused |
| 51 | Paid placement without a disclosure label | refused |
| 52 | Paid placement dressed up as a personal recommendation | refused |
| 53 | An active reason with no localization key | refused |
| 54 | The evidence bar of a live reason code is lowered in place | refused |
| 55 | A draft reason code is still being edited | accepted |
| 56 | A live reason code is retired | accepted |
| 57 | A retired reason code is brought back | accepted |
| 58 | A personalized search with nobody to personalize it for | refused |
| 59 | An explicit price sort that also claims to be personalized | refused |
| 60 | An explicit price sort that says so | accepted |
| 61 | A three night stay recorded as five nights | refused |
| 62 | A stay that checks out before it checks in | refused |
| 63 | A stay with an arrival and no departure | refused |
| 64 | A hard filter that produced more candidates than it received | refused |
| 65 | More results returned than were ever ranked | refused |
| 66 | A search log that expired before it was written | refused |
| 67 | A replayed search request key | refused |
| 68 | The funnel counts of a logged search are rewritten | refused |
| 69 | The retention deadline of a search log is extended | accepted |
| 70 | An impression rendered where a guest could see it | accepted |
| 71 | A retried event with a key that was already ingested | refused |
| 72 | An impression for a listing that was never rendered visibly | refused |
| 73 | An impression that names no search and no position | refused |
| 74 | A position claimed with no search behind it | refused |
| 75 | A click on no listing in particular | refused |
| 76 | A browser tab left open for a day counted as a day of interest | refused |
| 77 | A dwell inside the cap | accepted |
| 78 | An event type nobody declared | refused |
| 79 | An event rejected for no stated reason | refused |
| 80 | A suspicious event quarantined with a reason | accepted |
| 81 | An event with no future in which to be deleted | refused |
| 82 | The position an impression was shown at is rewritten | refused |
| 83 | An ingested event is quarantined after review | accepted |
| 84 | An event is placed under legal hold | accepted |
| 85 | An expired event is deleted by the retention job | accepted |
| 86 | A global prior that also names one neighbourhood | refused |
| 87 | A locality prior for no locality in particular | refused |
| 88 | A current prior computed from four listings | refused |
| 89 | A thin prior that is being rebuilt rather than served | accepted |
| 90 | A prior with no strength, which is not a prior | refused |
| 91 | A second current prior for the same area and metric | accepted |
| 92 | A brand new listing awarded a rating out of nowhere | refused |
| 93 | A new listing shrunk to a named locality prior | accepted |
| 94 | A neighbourhood prior that names no neighbourhood | refused |
| 95 | A listing rated nine point nine out of five | refused |
| 96 | A price percentile above one hundred percent | refused |
| 97 | A second current profile for the same listing | refused |
| 98 | A profile that expired before it was computed | refused |
| 99 | A strength with evidence and confidence behind it | accepted |
| 100 | A weakness asserted with no evidence at all | refused |
| 101 | An unknown aspect stored as unknown rather than as zero | accepted |
| 102 | One feature carrying two kinds of value at once | refused |
| 103 | The same feature written twice into one profile version | refused |
| 104 | A computed feature value is edited after the fact | refused |
| 105 | A current profile is superseded | accepted |
| 106 | A feature appended to a profile version that is no longer current | refused |
| 107 | A superseded profile is edited | refused |
| 108 | An aggregate that claims correction with no exposure conditions | refused |
| 109 | A corrected aggregate that carries its own denominator | accepted |
| 110 | An all time window that starts somewhere | refused |
| 111 | A mean position above the first result | refused |
| 112 | A negative number of clicks | refused |
| 113 | A second aggregate for the same listing, window kind and end date | refused |
| 114 | A second current prior once one already stands | refused |
| 115 | A profile built for a guest who turned profiling off | refused |
| 116 | A profile for a guest who has expressed no preference either way | accepted |
| 117 | Confidence of nine tenths from no evidence whatsoever | refused |
| 118 | An evidence window that ends before it begins | refused |
| 119 | A guest asks that behaviour older than thirty days be forgotten | accepted |
| 120 | The existing profile is cleared by superseding it | accepted |
| 121 | Tonight the batch job rebuilds the profile from the same two hundred days | refused |
| 122 | The rebuild starts after the cutoff and names the directive it honours | accepted |
| 123 | Importance, direction and confidence recorded separately | accepted |
| 124 | A confident preference inferred from nothing | refused |
| 125 | A preference the guest set themselves stands on its own | accepted |
| 126 | A target range whose floor is above its ceiling | refused |
| 127 | A price band the guest keeps choosing within | accepted |
| 128 | A range stated for a direction that has no range | refused |
| 129 | An unknown direction that nonetheless states a preferred level | refused |
| 130 | A family trip segment carved out with no family trip behind it | refused |
| 131 | A segment backed by three completed family stays | accepted |
| 132 | A preference dimension nobody declared | refused |
| 133 | An inferred preference is edited inside a published profile | refused |
| 134 | An anonymous session that keeps choosing kitchens | accepted |
| 135 | An anonymous session promoted into a durable profile | refused |
| 136 | Session intent promoted for a guest who declined to be profiled | refused |
| 137 | Session intent promoted for a guest who permitted it | accepted |
| 138 | Session intent that expired before the session started | refused |
| 139 | The same session records the same dimension twice | refused |
| 140 | A personalized result recorded with the profile that produced it | accepted |
| 141 | An exposure claiming a policy the epoch was not opened under | refused |
| 142 | A shadow model quietly serving inside a live epoch | refused |
| 143 | A result served under an epoch that was invalidated | refused |
| 144 | An anonymous search carrying a personalized contribution | refused |
| 145 | One guest's preference profile ranking another guest's search | refused |
| 146 | Rank nine hundred out of two hundred ranked candidates | refused |
| 147 | A listing shown at position one | accepted |
| 148 | Two listings occupying position one of the same page | refused |
| 149 | A model-served rank that cannot be reproduced | refused |
| 150 | Exploration with no budget behind it | refused |
| 151 | An organic result drawing on the exploration budget | refused |
| 152 | Paid placement shown without a label | refused |
| 153 | Paid placement shown with its label | accepted |
| 154 | The same listing explored twice, up to its cap | accepted |
| 155 | A third exploration impression for a listing capped at two | refused |
| 156 | A different listing spends the last of the budget | accepted |
| 157 | Exploration continues after the window is spent | refused |
| 158 | The window closed itself at its ceiling | accepted |
| 159 | The position a result was shown at is rewritten afterwards | refused |
| 160 | A cleanliness claim that clears its own bar | accepted |
| 161 | The same claim repeated at a confidence below its threshold | refused |
| 162 | A reason still in draft shown to a guest | refused |
| 163 | The same reason code attached twice to one result | refused |
| 164 | A confident claim resting on two mentions | refused |
| 165 | A cleanliness claim supported by the wifi feature | refused |
| 166 | A claim with no evidence recorded at all | refused |
| 167 | A personal claim made beside an anonymous result | refused |
| 168 | A personal claim beside a personalized result | accepted |
| 169 | A sponsorship disclosure on an organic result | refused |
| 170 | A sponsorship disclosure on a sponsored result | accepted |
| 171 | The evidence behind a shown reason is revised later | refused |

Scenario 158 reads the exploration window back rather than attempting a write: it confirms that the
window moved itself to `EXHAUSTED` at exactly its allocation of three impressions, which is the state
scenario 157 is then refused by.

## Tables

**Explicit signals** — `saved_listings`. The one preference signal a guest states outright, kept as
state so that saving, unsaving and re-saving all survive.

**Consent and erasure** — `personalization_settings`, `personalization_erasure_directives`,
`personalization_erasure_applications`.

**Ranking configuration** — `ranking_policy_versions`, `ranking_model_versions`, `ranking_epochs`,
`recommendation_reason_codes`, `exploration_budget_windows`. Frozen once they leave draft.

**Behaviour** — `discovery_search_requests`, `discovery_events`. Append-only and retention-bound.

**Listing intelligence** — `listing_discovery_profiles`, `listing_discovery_features`,
`listing_outcome_aggregates`, `discovery_market_priors`.

**Guest intelligence** — `guest_preference_profiles`, `guest_preference_features`,
`guest_session_intents`.

**What was shown** — `ranking_exposures`, `ranking_exposure_reasons`.

## The rules that are not columns

Eight changesets carry no table:

- **`029-21` append-only.** One function serves seven tables: events, search logs, exposures,
  exposure reasons, listing features, preference features and erasure applications. The permitted
  columns are passed per table, so the same function enforces "nothing may change" and "only the
  retention deadline may change" without a branch. Deletion stays permitted, deliberately: retention
  windows and erasure directives have to be able to remove these rows, which is a different act from
  quietly changing what they said.
- **`029-22` derived profile supersession.** A superseded or failed profile version is frozen, and —
  the part that is easy to forget — its feature rows are frozen with it, including against `INSERT`. A
  profile that can gain a new feature after it stopped being current is not a version, it is a mutable
  table with a version column. This is the same defect migration 022 found on ledger postings.
- **`029-23` personalization honoured.** A preference profile is refused for a guest who turned
  behavioural profiling off, and refused when its evidence window reaches back past any erasure
  cutoff standing against that guest. Session intent may not be promoted into the durable profile for
  a guest who did not permit it.
- **`029-24` exposure integrity.** An exposure must agree with the search that produced it: the same
  epoch, the policy and model that epoch was opened under, a position within what was actually
  ranked, no personalized contribution on an anonymous search, and never another guest's preference
  profile — which is not a relevance bug but a disclosure.
- **`029-25` exploration budget.** A window that is closed, suspended, exhausted or outside its own
  dates funds nothing; a listing that has reached its per-listing cap stops there; and the draw-down
  happens in the same transaction as the exposure it pays for, moving the window to `EXHAUSTED` at
  its ceiling.
- **`029-26` reason truthfulness.** A reason code must be active, must clear its own confidence and
  evidence thresholds, must be supported by the feature it declares, may not speak about a guest
  beside an anonymous result, and may not carry a sponsorship disclosure on an organic placement.
- **`029-27` registry immutability.** A policy version, a model version and a reason code stop being
  editable the moment they leave draft. Lifecycle columns still move; the weights, the checksum and
  the wording do not. Deleting one is refused outright, because results were served under it.
- **`029-28` terminal states.** A rollback is a fact and cannot be erased, a rolled-back model cannot
  return to service, and a superseded policy version cannot be revived. In all three cases the way
  forward is a new version.

## What probing changed

**A real defect: a rolled-back model could go straight back into service.** The registry recorded
that a model had been pulled and why, but nothing held the row to it. Probing set a model rolled back
for a latency regression back to `ACTIVE` and it was accepted — leaving a row that simultaneously
claimed to be serving traffic and to have been withdrawn from service, with the original approval
still standing in for a promotion nobody had re-made. Recording that something was rolled back is not
a guard unless the row is frozen against it. Changeset `029-28` makes the rollback final and
unerasable, and a check constraint refuses `ACTIVE` beside a rollback instant on insert as well as on
update — because a row can be created already past a transition, and a guard that only watches the
change would never see it.

**A guard that blocked compliance with its own directive.** The erasure trigger originally ran on
every write to a preference profile. That made it self-defeating: once a directive landed, the stale
profile it was meant to clear could no longer be superseded, because its evidence window predated the
cutoff. The profile the guest asked to have cleared became permanent. The trigger now applies only to
profiles entering or remaining in a servable state; retiring one is always allowed, since retiring it
is how a service complies.

**A trigger that raised the wrong error.** `TG_ARGV` is `NULL`, not an empty array, when a trigger
takes no arguments, so the three append-only triggers with no permitted columns failed with a
`FOREACH` error instead of the intended refusal. They refused the write either way, which is exactly
why it was worth catching: a rule that is right by accident stops being right the moment somebody
adds a permitted column.

**Two probe-ordering corrections, not schema defects.** The one-current-prior rule initially appeared
not to fire because the preceding scenarios had all been refused, leaving no current row to collide
with; restated as scenario 114 with a prior already standing, it refuses. Scenario 85's retention
delete named an event that an earlier dwell-cap refusal had prevented from existing.

**An audit found nothing this time.** The multi-column enum-width audit across migrations 012–029
returns no rows, and every controlled-vocabulary column in this migration carries a `CHECK` — the two
classes of defect migration 028 found late.

## Design rules this migration follows

- Status values are `VARCHAR` with a `CHECK`, never PostgreSQL enum types.
- `version BIGINT NOT NULL DEFAULT 0` with `CHECK (version >= 0)` on every optimistically-locked
  table; the append-only tables have no version because they are never updated.
- No `DEFAULT now()` on any column the application writes.
- Fractions are `NUMERIC` bounded to `0..1` by check, never floating point.
- `NULLS NOT DISTINCT` on unique indexes where a nullable column is part of the identity — the
  market-scoped active policy, the geographic prior, the contextual preference segment.
- Partial unique indexes for "at most one live row" rules, with `DEFERRABLE INITIALLY DEFERRED`
  self-foreign-keys where a successor pointer pairs with one.
- Stay ranges half-open: `check_out_date > check_in_date`, and the night count must equal the span.
- Constraint naming `pk_` / `uk_` / `fk_` / `ck_` / `idx_`.

## What this migration leaves open

- **Event volume.** PostgreSQL holds a bounded launch event log. The feature document is explicit
  that unbounded clickstream belongs in an analytical store; the retention class and per-row expiry
  here are what make the move survivable rather than urgent. Partitioning is an operational decision,
  not a schema one.
- **The event registry.** Event names, owners, schemas and retention windows belong to the data
  platform. Migration 030 delivers `event_definitions`; until then `discovery_events` carries its own
  vocabulary as a check constraint.
- **Point-in-time feature storage.** Training examples must be reconstructable as of the prediction
  instant. `ranking_exposures.feature_vector_digest` records which vector was scored; the durable
  feature store that can return it arrives with migration 031.
- **Experiment assignment.** `ranking_exposures.experiment_assignment_key` is a key, not a foreign
  key, because `experiment_assignments` arrives in migration 030.
- **Reason code wording.** The database holds the localization key and the thresholds; the translated
  strings live in the localization catalogue, not here.
- **Exploration selection policy.** Which listing gets the next exploration impression is a service
  decision. The schema bounds the spend; it does not choose the spender.
- **Fairness slices.** Exposure concentration by host and market is computable from
  `ranking_exposures`, but the monitoring definitions and their review cadence are not schema.

## Deviations from the plan and the feature document

**`listing_aspect_scores` is not created.** The feature document lists it, and the plan repeats it,
but migration 026 already delivers per-listing aspect aggregates as `aspect_profile_versions` plus
`aspect_profile_values`, with counts, posteriors, intervals, trend and an evidence class. A second
copy would be a second definition of listing quality, and the two would disagree within a week.
`listing_discovery_profiles` names the aspect profile version it read instead of restating its
numbers. `review_aspect_mentions`, also listed, is likewise already 026's.

**`favorites` is not dropped here.** The plan describes dropping it in this migration. Migration 016
already dropped it with the rest of the listing-centric foundation, so 029 only creates its target
replacement, `saved_listings`, keyed on `account_holders` rather than the retired `users` table.

**Seven tables the documents imply but do not name.** `discovery_search_requests` gives the
server-issued `searchRequestId` the event contract requires something to reference.
`personalization_settings`, `personalization_erasure_directives` and
`personalization_erasure_applications` make the opt-out and deletion rules enforceable rather than
procedural. `ranking_policy_versions` and `ranking_epochs` hold what the document calls "versioned
configuration supported by measurement, not hidden constants". `exploration_budget_windows` holds the
"strict traffic budget, quality floor, safety eligibility, per-listing caps" the document requires
exploration to run under. `recommendation_reason_codes`, `ranking_exposure_reasons`,
`listing_outcome_aggregates`, `listing_discovery_features`, `guest_preference_features` and
`discovery_market_priors` are the relational form of structures the document gives only as JSON.

**`ranking_model_versions` is the serving registry, not the lifecycle registry.** The plan places it
here and the general `model_versions` registry in migration 031. This table answers what is live,
what is shadowing, what share of traffic it takes and what serves when it times out; 031 owns
training, evaluation and release routing, and the two link by reference when it lands.

## What this closes

Dependency 0's event contract, Dependency 1's explainable baseline with stable cursor pagination and
approved reason codes, Dependency 2's consumption of versioned aspect intelligence, Dependency 3's
separated long-term, recent and session signals with confidence gating and opt-out, and Dependency
4's model registry, exposure logging and position-bias groundwork all have their schema. What remains
for those dependencies is services, jobs and measurement, not tables.

## Exit criteria

- Twenty-eight changesets apply cleanly to an empty database and to one already at 028.
- Full rollback removes all twenty tables, all eleven functions and all twenty-two triggers, leaving no changelog
  rows; re-application is proved from that state.
- A hundred and seventy-one probe scenarios run with no stray errors: forty-nine accepted, a hundred
  and twenty-two refused.
- The multi-column enum-width audit across 012–029 returns no rows.
- Every model field matches its column in name and nullability, and every column has a field.
- `compileJava` and `javadoc` succeed; the application boots with no repository wiring failure.
