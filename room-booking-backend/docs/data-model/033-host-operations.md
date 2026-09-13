# Migration 033 — host operations, performance and revenue management

## Goal

Advice that does not say what it is based on, how sure it is, and who pays for it is not advice; it
is pressure with a number on it.

That sentence is the whole migration. Everything the platform tells a host about their own business
— how they are performing, how that compares to the market, what demand is coming, what a promotion
would cost, what is wrong with a listing, what a payout will be — becomes a row that carries its
evidence, its uncertainty, its expected impact and its economic effect, and a second row recording
what the host decided to do about it. The failure the domain document names is a short one:
recommendations that are opaque or coercive. Opacity and coercion are the same defect seen from two
sides. A host who cannot see why a number was produced cannot argue with it, and a host who cannot
argue with it has not been advised.

The platform does not get to invent facts about a host's business. Nothing here computes a booking,
a price, a payout or a review score. A performance metric cites migration 030's metric definition
that produced it and the pipeline watermark it was cut at; a payout preview cites the obligations it
summed and is explicitly non-binding; a quality checklist item cites the listing content or review
aspect that raised it.

Seventeen tables, twenty-six changesets. Nine of those changesets contain no table at all: they
carry the rules no column can express. Most of D18 was already built — migration 019 owns the host's
pricing bounds and its per-date recommendations, 026 owns the listing quality score, 016 owns co-
host assignment, 018 owns the channel-manager connection and 022 owns the statement — so this
migration is the measurement, comparison and advice layer over them, not a second copy of them.

## Six forces shaping it

**A number shown to a host is a published metric, not a query.** Which metrics a host may see, in
what words, and how many observations are needed before the number means anything, is a registry row
frozen once it leaves `DRAFT`. Two screens computing "occupancy" two different ways is not a display
inconsistency; it is two different claims about the same business, and the host has no way to tell
which one their decision was based on.

**A rate is published with its denominator or not at all.** Response rate, acceptance rate and
cancellation rate are stored beside the counts that produced them and checked against them, and a
metric whose observation count falls under its own published minimum is recorded as insufficient
rather than rounded into a number. Ninety per cent of ten is a different fact from ninety per cent
of a thousand, and a host penalised by the first deserves to see which it was.

**Market intelligence is aggregate or it is a leak.** A benchmark names the cohort it was drawn
from, and the cohort names the minimum number of distinct contributors it will publish under — never
fewer than five — plus the largest share one contributor may supply. Below either floor the row
exists and carries a suppression reason instead of a value, because a silently missing row is
indistinguishable from a pipeline failure. Extremes are never published: a maximum is one host's
number wearing a cohort's name.

**A forecast carries an interval or it is a guess with a decimal point.** Every forecast row names
its run, its model version and its horizon, states the probability its interval was computed at, and
its interval must contain its own point estimate. The domain document forbids fabricated scarcity
and guaranteed earnings, and an interval is what makes the difference visible: a wide one says the
platform does not know.

**Advice is disclosed before it is decided, and both halves are kept.** A disclosure records the
four facts the domain document requires — evidence, uncertainty, expected impact, economic effect —
as four columns that cannot be null, plus a digest of what was actually rendered. A decision must
cite a disclosure about the same piece of advice that preceded it, so "the host accepted it" can
never be recorded for advice the host was never shown the basis of. Ignoring advice is a recordable
outcome, because a system that only stores acceptances cannot tell a good recommendation from one
nobody dared refuse.

**A bulk edit reports what it did to every target, not that it succeeded.** One request against four
hundred nights is four hundred outcomes, each of which may be refused by the domain that owns it — a
night under an active claim, a price under a floor, a restriction the market forbids. A request that
says `APPLIED` while sixty nights silently did not change is how a host discovers in a support case
that their calendar was never what they saw.

## Verified behaviour

A hundred and ninety-four scenarios were run against the applied schema, forty-three accepted and a
hundred and fifty-one refused. Every refusal below is paired with an accepted counterpart somewhere
in the suite, because a rule that refuses everything is indistinguishable from a broken one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | Publication with all four host-facing texts | accepted |
| 2 | Publication with a zero evidence floor | refused |
| 3 | Publication with an empty subject list | refused |
| 4 | Ranking-affecting metric with no consequence explained | refused |
| 5 | Standing-affecting metric with no consequence explained | refused |
| 6 | Informational metric carrying a consequence explanation | refused |
| 7 | Active publication with no published instant | refused |
| 8 | Second publication of one metric on the same surface | refused |
| 9 | Lower the evidence floor on a published metric | refused |
| 10 | Reword the explanation of a published metric | refused |
| 11 | Deprecate a published metric | accepted |
| 12 | Delete a published metric publication | refused |
| 13 | Edit a metric publication still in draft | accepted |
| 14 | Ratio metric with sufficient evidence and a denominator | accepted |
| 15 | Ratio metric below its own evidence floor reported as a number | refused |
| 16 | The same thin month reported as insufficient evidence | accepted |
| 17 | A well-evidenced month hidden as insufficient | refused |
| 18 | Sufficient evidence carrying no value at all | refused |
| 19 | Insufficient evidence carrying a value anyway | refused |
| 20 | A ratio published with no denominator | refused |
| 21 | A numerator larger than its denominator | refused |
| 22 | A subject kind the publication does not cover | refused |
| 23 | A value produced from a metric still in draft | refused |
| 24 | Money metric with its minor amount and currency | accepted |
| 25 | Money metric with no minor amount | refused |
| 26 | A ratio metric carrying a money amount | refused |
| 27 | A minor amount with no currency beside it | refused |
| 28 | A period that ends before it begins | refused |
| 29 | A period aggregated without a real time zone | refused |
| 30 | A comparison value with no comparison period | refused |
| 31 | A consistent month of response, acceptance and cancellation | accepted |
| 32 | A response rate that flatters the counts beside it | refused |
| 33 | A rate with no enquiries behind it | refused |
| 34 | Enquiries arrived but no rate was published | refused |
| 35 | A host who answered more enquiries than arrived | refused |
| 36 | Request outcomes outnumbering the requests | refused |
| 37 | More cancellations than confirmed bookings | refused |
| 38 | More excused cancellations than cancellations | refused |
| 39 | A slowest decile faster than the median | refused |
| 40 | A host moved to WARNED with no explanation | refused |
| 41 | A host in good standing carrying a warning explanation | refused |
| 42 | A warned host with the reason recorded | accepted |
| 43 | A cohort with a floor of eight contributors | accepted |
| 44 | A cohort of three | refused |
| 45 | A cohort with fewer observations than contributors | refused |
| 46 | A cohort letting one host supply four fifths of it | refused |
| 47 | A price band with no currency | refused |
| 48 | Raise the floor on a published cohort | refused |
| 49 | Lower the floor on a published cohort | refused |
| 50 | A benchmark published with twelve contributors | accepted |
| 51 | A benchmark published with six contributors | refused |
| 52 | The same thin cohort suppressed instead | accepted |
| 53 | A quality failure reported as a privacy suppression | refused |
| 54 | A benchmark dominated by one contributor | refused |
| 55 | A benchmark that does not say who the largest contributor was | refused |
| 56 | A benchmark below the observation floor | refused |
| 57 | A benchmark of a metric that is not benchmarkable | refused |
| 58 | A benchmark drawn against a cohort still in draft | refused |
| 59 | A suppressed benchmark that still carries its numbers | refused |
| 60 | A suppressed benchmark with no reason given | refused |
| 61 | Quartiles published out of order | refused |
| 62 | A concentration suppression on a well-spread cohort | refused |
| 63 | A retirement suppression on a live cohort | refused |
| 64 | A forecast inside a running run, with an interval | accepted |
| 65 | A point estimate with no interval around it | refused |
| 66 | An interval that does not contain its own estimate | refused |
| 67 | A forecast with no stated interval confidence | refused |
| 68 | A booking count interval that dips below zero | refused |
| 69 | A forecast outside the window the run declared | refused |
| 70 | A forecast appended to a run that already succeeded | refused |
| 71 | Two forecasts for one date in one run | refused |
| 72 | An unavailable forecast carrying a number anyway | refused |
| 73 | A date with no forecast, recorded as unavailable | accepted |
| 74 | A run reaching three years out | refused |
| 75 | A run covering more than its own horizon | refused |
| 76 | A failed run with no reason recorded | refused |
| 77 | Close a run against a count it does not hold | refused |
| 78 | Close an empty run as a success | refused |
| 79 | Reopen a run that already succeeded | refused |
| 80 | Supersede a run that already succeeded | accepted |
| 81 | A run that supersedes itself | refused |
| 82 | A superseded run with nothing named as its replacement | refused |
| 83 | A pending run that claims to have completed | refused |
| 84 | Edit the prediction on a forecast that was written | refused |
| 85 | Edit the confidence wording on a forecast that was written | refused |
| 86 | A suggestion with a baseline, an interval and a cost | accepted |
| 87 | A model-based suggestion naming no model | refused |
| 88 | An interval that does not contain the estimate | refused |
| 89 | A platform-funded suggestion that still charges the host | refused |
| 90 | A shared suggestion that does not say how it is shared | refused |
| 91 | A suggestion with no shelf life | refused |
| 92 | A suggestion that discounts by four fifths | refused |
| 93 | A suggestion created already accepted | refused |
| 94 | A suggestion reaching past the forecast run that backs it | refused |
| 95 | A suggestion backed by a run that is still running | refused |
| 96 | Accept a suggestion into a live promotion | accepted |
| 97 | Accept a suggestion into a promotion that is still a draft | refused |
| 98 | Accept a suggestion while quietly halving the estimate | refused |
| 99 | Reject a suggestion | accepted |
| 100 | Revise the cost of a pending suggestion | accepted |
| 101 | A price recommendation disclosed with all four facts | accepted |
| 102 | A disclosure with no uncertainty stated | refused |
| 103 | A disclosure with no economic effect stated | refused |
| 104 | A disclosure pointing at two pieces of advice at once | refused |
| 105 | A disclosure whose kind and reference disagree | refused |
| 106 | Advice a host may neither refuse nor override | refused |
| 107 | An expired recommendation shown to a host | refused |
| 108 | A recommendation the host already answered, shown again | refused |
| 109 | A forecast from a completed run | accepted |
| 110 | A disclosure with a malformed rendered digest | refused |
| 111 | The host accepts the suggestion they were shown | accepted |
| 112 | The host refuses it, with a reason code | accepted |
| 113 | A refusal with no reason code | refused |
| 114 | The host ignores the advice | accepted |
| 115 | An acceptance recorded with nobody behind it | refused |
| 116 | An acceptance attributed to someone who was never shown it | refused |
| 117 | A decision taken before the advice was shown | refused |
| 118 | A modification with no summary of what changed | refused |
| 119 | A modification recording what the host changed | accepted |
| 120 | A co-host decision recorded as the host's own | accepted |
| 121 | An applied reference recorded against a refusal | refused |
| 122 | A forecast from a run that is still running, shown to a host | refused |
| 123 | A checklist item already satisfied, shown as outstanding | refused |
| 124 | A host-funded suggestion shown as costing the host nothing | refused |
| 125 | Two decisions against one disclosure | refused |
| 126 | Edit a disclosure after the host answered it | refused |
| 127 | Edit a recorded decision | refused |
| 128 | A blocking item that is also dismissible | refused |
| 129 | A blocking item marked merely suggested | refused |
| 130 | A blocking item that is required and not dismissible | accepted |
| 131 | Two items at the same position in one checklist | refused |
| 132 | Add an item to a published checklist | refused |
| 133 | Reword a published checklist | refused |
| 134 | Measure a listing against a checklist still in draft | refused |
| 135 | The host dismisses a dismissible item | accepted |
| 136 | The host dismisses the smoke alarm declaration | refused |
| 137 | A dismissal with nobody named | refused |
| 138 | An item marked satisfied with no second look | refused |
| 139 | An item verified before it was fixed | refused |
| 140 | An item fixed and then verified | accepted |
| 141 | Reopen a satisfied item on the observation that was already resolved | refused |
| 142 | Reopen a satisfied item on a fresh observation | accepted |
| 143 | An item marked not applicable with no reason | refused |
| 144 | Two states for one item on one listing | refused |
| 145 | An item last evaluated before it was detected | refused |
| 146 | Add an item to a published checklist | refused |
| 147 | Remove an item from a published checklist | refused |
| 148 | Add an item to a checklist still in draft | accepted |
| 149 | Remove an item from a checklist still in draft | accepted |
| 150 | Edit the guidance on a checklist item | refused |
| 151 | A price set by a named rule | accepted |
| 152 | A price a rule set, with no rule named | refused |
| 153 | A price falling back to the rate plan | accepted |
| 154 | A nightly price with no amount | refused |
| 155 | A source that says it overrode itself | refused |
| 156 | A cell the host cannot edit, with no reason shown | refused |
| 157 | A cell locked by a booking, with the reason shown | accepted |
| 158 | An editable cell carrying a lock reason | refused |
| 159 | Two active sources for one value on one night | refused |
| 160 | A minimum stay carrying a money amount | refused |
| 161 | A bulk edit created as a draft | accepted |
| 162 | A bulk edit created already applied | refused |
| 163 | A bulk edit with no scope at all | refused |
| 164 | A second bulk edit under the same idempotency key | refused |
| 165 | Begin applying a previewed edit | accepted |
| 166 | Change the scope after the host approved the preview | refused |
| 167 | Change the requested value after the preview | refused |
| 168 | Apply an edit straight from preview, skipping the applying state | refused |
| 169 | Record an outcome while the edit is only previewed | refused |
| 170 | A full, honest apply of three nights | accepted |
| 171 | The same run reported as partially applied | accepted |
| 172 | A refusal with no reason code | refused |
| 173 | An applied target that does not say what it set | refused |
| 174 | An applied target carrying a refusal code | refused |
| 175 | An availability target with no night named | refused |
| 176 | Two targets at the same position | refused |
| 177 | Cancel an edit that has begun applying | refused |
| 178 | Cancel an edit that was only previewed | accepted |
| 179 | Edit a recorded target outcome | refused |
| 180 | Three nights applied and reported as three | accepted |
| 181 | Report APPLIED while one night was refused | refused |
| 182 | The same run reported as partially applied | accepted |
| 183 | Report three applied nights while writing only two rows | refused |
| 184 | Report a partial apply that does not add up to the target count | refused |
| 185 | A preview whose net equals its own components | accepted |
| 186 | A preview whose net does not fall out of its components | refused |
| 187 | A preview with no expiry | refused |
| 188 | A booking preview naming no booking | refused |
| 189 | A period preview with no period | refused |
| 190 | A preview with no assumptions written down | refused |
| 191 | A preview paying out before the funds can be released | refused |
| 192 | A preview with a negative platform fee | refused |
| 193 | A preview carrying a negative adjustment that nets out | accepted |
| 194 | Revise a preview in place | refused |

## Tables

- `host_metric_publications` — which of migration 030's metrics a host may see, on which surface, in
  what words, and with what evidence floor. Frozen past `DRAFT`.

- `host_performance_metrics` — one computed figure about one listing, accommodation type, property
  or host, over one period, with the counts that produced it. Append-only.

- `host_response_metrics` — response, acceptance, cancellation and compliance for one host over one
  period, with every rate stored beside its numerator and denominator. Append-only.

- `benchmark_cohort_definitions` — how a peer set is drawn and the privacy floors it publishes
  under. Frozen past `DRAFT`.

- `market_benchmark_aggregates` — what a cohort looked like over one period, as quartiles and a
  trimmed mean, or the recorded reason there is no answer. Append-only.

- `demand_forecast_runs` — one pass of the demand model over one market, with its horizon and its
  measured backtest error.

- `demand_forecasts` — what the platform expects one night to do, with the interval around it.
  Append-only.

- `promotion_suggestions` — a promotion worth running, with its baseline, its incremental estimate,
  its interval, its cost and who bears it. Frozen once decided.

- `host_advice_disclosures` — what a host was actually shown about one piece of advice, including
  the four mandatory facts and a digest of the rendering. Append-only.

- `host_advice_decisions` — what the host did about it, citing the disclosure that preceded it. One
  per disclosure, append-only.

- `listing_quality_checklist_versions` — a published set of things a listing needs. Frozen past
  `DRAFT`, and its membership is frozen with it.

- `listing_quality_checklist_items` — one thing a listing may be asked to do, with the effect a host
  should expect from doing it. Append-only, and sealed once its checklist is published.

- `listing_quality_checklist_states` — where one listing stands on one item, with the evidence that
  raised it.

- `calendar_value_sources` — which layer set the value a host sees on one night, which layer it
  beat, and why the host may or may not change it.

- `host_bulk_edit_requests` — one request to change many nights, listings or rate plans at once,
  with the preview digest the host approved and the counts the targets add up to.

- `host_bulk_edit_targets` — what actually happened to each one. Append-only.

- `host_payout_previews` — what a host should expect to receive, before the money exists, with a net
  that must equal the components explaining it. Append-only.

## The rules that are not columns

- **A metric below its own floor may not be shown as a number, and one above it may not be hidden.**
  `host_metric_sufficiency()` checks the observation count both ways. The second direction matters
  as much as the first: marking a figure insufficient while the evidence was there is how an
  uncomfortable number disappears from a dashboard without anybody deciding that it should.

- **A published benchmark clears its cohort's floors, and a suppression reason has to be the true
  one.** `benchmark_privacy_floor()` refuses to publish below the contributor count, the observation
  count or the concentration ceiling — and refuses a `TOO_FEW_CONTRIBUTORS` suppression on a cohort
  that comfortably met its floor. Without the second half, a quality failure can be reported to
  hosts as a privacy control.

- **Forecasts are written while their run is running, and the run is closed against its own row
  count.** `demand_forecast_integrity()` and `demand_forecast_run_closure()`. A run that claims four
  hundred forecasts and holds three is read by a host as coverage they do not have.

- **A host-funded suggestion may not be disclosed as costing the host nothing.**
  `host_advice_disclosure_integrity()` compares the disclosure's `who_pays` against the suggestion's
  own `funding_split`. It also refuses to disclose advice that has expired or already been decided,
  and a forecast from a run that has not succeeded.

- **A decision cites a disclosure that preceded it, taken by the person it was shown to.**
  `host_advice_decision_integrity()`. A refusal cannot be recorded against advice that offered no
  way to decline, and a modification cannot be recorded against advice that offered no override.

- **A decided suggestion is frozen.** `promotion_suggestion_integrity()`. If the estimate can still
  move after the host agreed to it, the disclosure digest sitting beside it documents nothing.

- **A bulk edit is previewed, then applied, then reported.** `host_bulk_edit_progression()` freezes
  the scope and the change once the preview exists, `host_bulk_edit_target_window()` allows outcomes
  only while the edit is applying, and a deferred constraint trigger checks the request's counts
  against the target rows at commit — on both tables, because otherwise the summary could be changed
  after the targets were written.

- **An item a host may not dismiss cannot be dismissed, and satisfied means the platform looked
  again.** `listing_quality_checklist_state_integrity()`. Reopening a satisfied item needs a fresh
  observation rather than the one that was already resolved.

- **A published checklist's membership is sealed.** `listing_quality_checklist_item_seal()`.
  Freezing the version row is not enough when the items live in another table.

- **Evidence is append-only.** Nine tables reuse migration 030's `platform_append_only()`, and three
  registries reuse its `platform_contract_freeze()`. A figure restated in place makes last month's
  decision look as though it were taken on this month's number.

## What probing changed

Three defects, all found by scenarios that were expected to be refused and were not.

**A superseded forecast run could never have been a finished one.**
`ck_demand_forecast_runs_completion` was written as an equality — `run_state IN ('SUCCEEDED',
'FAILED')` equals `completed_at IS NOT NULL` — which made it impossible to move a completed run to
`SUPERSEDED` without erasing the instant it finished. Superseding a completed run is the ordinary
case: it is what every fresh overnight run does to yesterday's. The two directions are different
rules and are now written as two clauses. This is the same shape as migration 021's symmetric
settlement check: an equality asserts something in both directions, and only one of them was
intended.

**Items could be appended to a published checklist.** The checklist version is frozen against update
and delete, but its items are a separate table, so a ninth item could be added to a live checklist
and every listing measured against it would silently acquire an outstanding action nobody versioned.
Migrations 022, 027 and 030 each found this same append-to-a-frozen-parent shape in their own
tables; 033 found it in a fourth. Fixed by `listing_quality_checklist_item_seal()`, which covers
insert and delete.

**Three columns Spring Data JDBC could not map.** `percentile_25`, `percentile_50` and
`percentile_75` became `percentile25`, `percentile50` and `percentile75` in Java, and the default
naming strategy maps those back without the underscore — so the columns and the fields would never
have met. This was caught by the model-against-schema verification rather than by probing, and fixed
by naming the columns `lower_quartile`, `median_value` and `upper_quartile`. A column name whose
camel-case form does not round-trip is a mapping bug waiting for the first read.

One harness limitation also surfaced. Two scenarios that exercise the deferred totals trigger were
reported as accepted inside the probe harness, because a plpgsql subtransaction never reaches a
commit and a deferred constraint trigger fires only there. They were restated as real transactions
closed with `SET CONSTRAINTS ALL IMMEDIATE`, and both are refused. This is migration 027's lesson
recurring in a new harness.

## Design rules this migration follows

- Status values are `VARCHAR` with a `CHECK`, never PostgreSQL enum types.

- Money is `*_minor BIGINT` beside a `VARCHAR(3)` currency with a format check. No floating point.

- Civil periods are `DATE` columns beside the IANA zone they were computed in.

- No `DEFAULT now()` on any column the application writes, per migration 011.

- Optimistically-locked tables carry `version BIGINT NOT NULL DEFAULT 0` with `CHECK (version >=
  0)`; append-only evidence tables carry neither a version nor an `updated_at`.

- Constraint naming is `pk_` / `uk_` / `fk_` / `ck_` / `ex_` / `idx_`.

- Every conditional vocabulary check carries an explicit `IS NOT NULL` or is written as an equality
  of two predicates, so a null column cannot slip past it.

- Every transition guard first checks that the state actually moved, so an unrelated column update
  does not trip it.

## What this migration does not create

- **Host pricing bounds and automatic-pricing opt-in.** Migration 019's `host_pricing_settings`
  already carries the floor, the ceiling, the desired net and the `OFF` / `SUGGEST` / `APPLY`
  consent the domain document calls automatic-pricing opt-in.

- **Per-date price recommendations.** Migration 019's `price_recommendations`. This migration adds
  the disclosure and the decision record over them rather than a second recommendation table.

- **Listing quality scores and review-aspect trends.** Migration 026's `listing_quality_profiles`
  and aspect profiles. What this adds is the actionable checklist over them, not a second score.

- **Co-host assignment and portfolio membership.** Migration 016's `property_collaborators`. A
  portfolio view is a host-level metric subject, not a new grouping concept.

- **External PMS and channel-manager connections.** Migration 018's `ical_connections` and sync
  runs; `CHANNEL_SYNC` appears here only as a calendar value source.

- **Host statements.** Migration 022. What this adds is a forward-looking, explicitly non-binding
  preview.

- **The metric registry.** Migration 030's `metric_definitions`, which already carries the
  `HOST_ECONOMICS` family, the window, the time zone and the restatement policy. Publishing from it
  rather than defining a parallel vocabulary is what stops the two drifting.

- **Model versions and predictions.** Migration 031.

## What this migration leaves open

- The calendar resolver itself. `calendar_value_sources` records which layer won; the precedence
  order is data on the row, but nothing here computes it.

- Notification of a partial bulk edit. The row says what did not change; telling the host is
  migration 024's job.

- Recomputation triggers. Nothing here decides when a metric, benchmark or preview is stale enough
  to recompute.

- Cohort construction. The definition says how a peer set is drawn in prose and in a few filter
  columns; the query that draws it lives in the pipeline.

- Automatic application of accepted advice. A decision records the reference of whatever the
  acceptance produced; writing the price or the promotion is the owning domain's work.

## Deviations from the plan and the feature document

D18 has no focused feature design, so the plan's table list came from the domain breakdown. Five
differences, each deliberate.

- **`pricing_recommendation_settings` was not created.** It is migration 019's
  `host_pricing_settings`, which already carries everything the breakdown asks of it. Building a
  second one would have given the pricing engine two places to read a floor from.

- **`host_metric_publications` and `benchmark_cohort_definitions` were added.** The plan named
  `host_performance_metrics` and `market_benchmark_aggregates` but nothing to define what they mean
  or what privacy threshold they clear. A threshold with nowhere to live is a comment in a pipeline.

- **`host_advice_disclosures` and `host_advice_decisions` were added.** The breakdown's first hard
  rule for D18 is that recommendations state evidence, uncertainty, expected impact and economic
  effect, and its second is that hosts can opt out and override. Neither is expressible as a column
  on a recommendation; both are facts about what was shown and what was chosen.

- **`calendar_value_sources` and the two bulk-edit tables were added.** The breakdown lists bulk
  editing and seeing the active source of each calendar value as subproblems; the plan's table list
  omitted them.

- **`host_payout_previews` was added.** The breakdown lists expected payout preview and statement
  explanation. Migration 022 owns the statement; the preview had nowhere to live.

Three enums were reused rather than duplicated — `PropertyType` from 016, `GovernedRegistryStatus`
from 032 and `RecommendationConfidence` from 019 — because their value sets are identical to what
this migration needed. `CalendarSourceLayer` is named apart from the `CalendarValueSource` aggregate
it belongs to, since the table name and the vocabulary name would otherwise collide in the model
package.

## What this closes

- D18's measurement, comparison, forecasting, advice, bulk-editing and payout-preview subproblems
  have schema.

- The four D18 hard rules are constraints: recommendations state evidence, uncertainty, expected
  impact and economic effect; hosts can set bounds, opt out, override and see the active source of
  each calendar value; no table can hold a fabricated scarcity claim or a guaranteed earnings
  figure; and no aggregate can be published that exposes another host's private data.

- With 033 applied the target schema is complete apart from D17, which the domain breakdown
  classifies as a designed extension rather than a required capability.

## Exit criteria

- Twenty-six changesets apply to an empty database and to one already at 032.

- A hundred and ninety-four probe scenarios: forty-three accepted, a hundred and fifty-one refused,
  no scenario matching zero rows and no unexpected error.

- Full rollback of all twenty-six changesets leaves no table, no function and no changelog row
  behind, and migration 030's two shared trigger functions intact; re-application is clean.

- The enum-width audit across 012–033 reports nothing, and the thirty-three Java enums match their
  `CHECK` vocabularies exactly.

- Model-against-schema verification reports no issue in either direction across all seventeen
  aggregates.

- `compileJava` and `javadoc` are clean at the unchanged hundred-warning baseline, and the
  application boots with three hundred and ninety-three JDBC repositories.
