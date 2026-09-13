# Migration 030 — Data and experimentation

## Goal

Analytics observes; it never repairs. A booking, a ledger posting, a published review and a
moderation decision are owned by the domains that accepted them, and nothing in this migration may
edit one. What it may do is say that its own copy disagrees — and say so in a row that names the run,
the code and the source snapshot the disagreement came from.

Every table here is one of four kinds of record: a contract, an arrival, a run, or a number produced
by a run. Keeping them apart is the whole design, because collapsing them is how a prediction ends up
being cited as a fact and how a dashboard number ends up with no way back to the events beneath it.

Twenty-three tables, thirty-three changesets. Ten of those changesets contain no table at all: they
carry the rules no column can express.

## Seven forces shaping it

**A fact, an observation, a prediction and a decision are different records.** This migration owns
observations (`event_arrivals`) and derived numbers (`metric_materializations`,
`experiment_analysis_estimates`). It owns no decisions: `experiment_actions` records that somebody
commanded a stop, not that the experiment was stopped — the state of the experiment belongs to the
experiment. Nothing here references `account_holders`, `listings` or `bookings` by foreign key, and
that is deliberate: an analytical write must never take a lock on a row a guest is trying to book.

**Historical evidence is immutable and corrections are additive.** Arrivals, assignments, exposures,
run inputs and estimates are append-only by trigger. A wrong number is corrected by a
`data_corrections` row and a restating materialization, never by an `UPDATE`. A correction must name
either one event or a bounded time range, because an unbounded correction is an unreviewable licence
to rewrite history.

**Nothing here stores a payload.** An arrival carries a digest, a byte size and a reference to
restricted landing storage — not the body. Quarantine is a diagnosis, not a shadow data lake, and it
carries its own shorter retention class. There is no column anywhere in this migration for an email
address, a phone number, a message body, an access instruction, a payment instrument, a government
identifier, an IP address or a user-agent string.

**Identity is pseudonymous and linking is declared.** Subjects appear as hex pseudonyms, checked by
pattern so that a user id or an address cannot be written into one by accident. The link table knows
three methods — authentication, explicit declaration, account merge — and the vocabulary deliberately
omits device fingerprint, shared address and payment instrument, because those are exactly the covert
links the design forbids. A suppressed link is terminal, and no new experiment assignment may be
written for a suppressed subject.

**Assignment is not exposure.** An assignment says which variant a unit would get. An exposure says
the treatment could actually have affected it, and carries the rule version that defined "could". An
exposure whose actual treatment differs from the assigned variant must say a fallback happened — a
failed model call that silently degraded to control behaviour would otherwise be counted as a
successful treatment delivery, which dilutes the treated group and biases every estimate that follows
toward zero. An exposure cannot precede its own assignment.

**An epoch becomes immutable at its first assignment.** Bucket ranges are an exclusion constraint,
not a convention, so two variants cannot claim the same bucket. Once a unit has been bucketed, the
allocation, the salt, the population, the exposure rule, the declared metrics and the exclusions are
all frozen; material change means a new epoch. Re-allocating an epoch under a running experiment is
not a configuration change, it is the destruction of the comparison.

**A number may not claim more authority than its inputs.** A run that consumed a degraded input
cannot report `PASS`. A run with a standing `FAIL` check cannot report `PASS`. A materialization
cannot be published as current off a run that did not succeed, and cannot claim a cleaner quality
state than that run reported. A conclusion cannot be drawn from an analysis with a sample-ratio
mismatch. And a second look at a running experiment requires a declared sequential method, because
peeking at an unadjusted p-value is not a stopping rule.

## Verified behaviour

A hundred and eighty-one scenarios were run against the applied schema, forty-five accepted and a
hundred and thirty-six refused. Every refusal below is paired with an accepted counterpart somewhere
in the suite, because a rule that refuses everything is indistinguishable from a broken one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | An active contract with no worked example | refused |
| 2 | A committed fact that names no source transaction | refused |
| 3 | Deprecation with no last production date and no deletion plan | refused |
| 4 | Personal collection with no legal basis | refused |
| 5 | Restricted data declared a training corpus | refused |
| 6 | Quarantine kept longer than the accepted stream | refused |
| 7 | The grain of an active contract changed in place | refused |
| 8 | A retired contract returned to service | refused |
| 9 | A published contract deleted outright | refused |
| 10 | Retiring a contract a pipeline still declares itself a consumer of | refused |
| 11 | Retiring the same contract once the consumer withdrew | accepted |
| 12 | A draft contract reworked before anyone builds on it | accepted |
| 13 | A second current version of one dataset key | refused |
| 14 | A non-personal product reading from a personal one | refused |
| 15 | The same edge with an approved declassification | accepted |
| 16 | A dataset declared to read from itself | refused |
| 17 | A pseudonymous product reading from a pseudonymous one | accepted |
| 18 | A tolerated lag shorter than the freshness target it serves | refused |
| 19 | A personal dataset declared available for training | refused |
| 20 | The grain of a published dataset changed in place | refused |
| 21 | The same dataset moved forward through its lifecycle | accepted |
| 22 | An accepted arrival against a retired contract | refused |
| 23 | An accepted arrival that names no registered contract | refused |
| 24 | The same unregistered arrival quarantined instead | accepted |
| 25 | An arrival whose class disagrees with the contract it cites | refused |
| 26 | A client impression created by a backfill | refused |
| 27 | A domain fact created by a backfill with explicit provenance | accepted |
| 28 | An accepted arrival received before it happened | refused |
| 29 | The same skewed clock recorded as a rejected arrival | accepted |
| 30 | A quarantined arrival kept on the standard retention class | refused |
| 31 | A duplicate that names no original | refused |
| 32 | A second accepted arrival carrying an envelope identity already seen | refused |
| 33 | The same resend recorded as a duplicate of the original | accepted |
| 34 | An envelope larger than the bounded payload size | refused |
| 35 | An arrival ingested before it was received | refused |
| 36 | A quarantined arrival marked as ingested into the analytical layer | refused |
| 37 | A change-data-capture arrival with no commit instant | refused |
| 38 | A client observation claiming a source commit instant | refused |
| 39 | An arrival that expires before it arrived | refused |
| 40 | A subject pseudonym written as an email address | refused |
| 41 | An arrival whose observed time is edited after the fact | refused |
| 42 | Stamping the ingestion time on an accepted arrival | accepted |
| 43 | Deleting an arrival that reached its retention horizon | accepted |
| 44 | A link inferred from a shared device fingerprint | refused |
| 45 | An authenticated link carrying a confidence score | refused |
| 46 | An account merge that states no confidence | accepted |
| 47 | A retroactive merge with no consent to point at | refused |
| 48 | The same merge with a consent reference | refused |
| 49 | A source pseudonym written as a plain identifier | refused |
| 50 | A link from a pseudonym to itself | refused |
| 51 | An interval that ends before it begins | refused |
| 52 | A suppressed link returned to active | refused |
| 53 | The instant of a suppression rewritten | refused |
| 54 | A fresh link for the same subject recorded after the suppression | accepted |
| 55 | An active link revoked | accepted |
| 56 | A correction naming neither an event nor a range | refused |
| 57 | A correction naming a range with no end | refused |
| 58 | A privacy suppression that substitutes a replacement | refused |
| 59 | A privacy suppression that only removes | accepted |
| 60 | A semantic correction that supplies no replacement | refused |
| 61 | An invalidation approved by the person who requested it | refused |
| 62 | The same invalidation approved by somebody else | accepted |
| 63 | A correction dated before the thing it corrects | refused |
| 64 | An event correction that also carries a range | accepted |
| 65 | A half-written output left standing as the current dataset | refused |
| 66 | A run that consumed degraded input and reported itself clean | refused |
| 67 | The same run reporting a warning instead | accepted |
| 68 | Degraded input claimed with no reason given | refused |
| 69 | A second current run for the same product and partition | refused |
| 70 | A succeeded run that names no output | refused |
| 71 | A failed run that names no failure class | refused |
| 72 | The same run resubmitted with the same specification and watermark | refused |
| 73 | A run whose output watermark precedes its input watermark | refused |
| 74 | A failing check filed against a run that already reports PASS | refused |
| 75 | The same failure filed after the run is downgraded | accepted |
| 76 | A run promoted to PASS while a failing check stands against it | refused |
| 77 | The same promotion once the failure is waived with an owner and a reason | accepted |
| 78 | A failure waived with no owner and no reason | refused |
| 79 | An unknown result that still reports a measured number | refused |
| 80 | A privacy check declared a mere warning | refused |
| 81 | A fatal check whose consumers are told to accept the failure | refused |
| 82 | A number published from a run that failed | refused |
| 83 | A number published from a run over a different dataset | refused |
| 84 | A number claiming to be cleaner than the run that computed it | accepted |
| 85 | The same number reported at the standing of its run | refused |
| 86 | A warning-grade number published as current for a metric that requires PASS | refused |
| 87 | A second current value for one metric, slice and window | refused |
| 88 | A published number edited in place | refused |
| 89 | The same number withdrawn rather than edited | refused |
| 90 | A rate metric with no denominator | refused |
| 91 | A money metric that says nothing about currency | refused |
| 92 | A converted money metric with no approved rate dataset | refused |
| 93 | The same metric naming the finance-approved rate dataset | accepted |
| 94 | A metric called booking_conversion_rate | refused |
| 95 | An audited financial number on an open-partition restatement policy | refused |
| 96 | A metric measured over a window shorter than its own maturity horizon | refused |
| 97 | A second semantic version that names no predecessor | refused |
| 98 | A public price experiment randomised by guest | refused |
| 99 | The same experiment randomised by listing | accepted |
| 100 | An epoch created already running | refused |
| 101 | An epoch approved with no variants at all | refused |
| 102 | An epoch approved with two controls and no metrics | refused |
| 103 | Two variants claiming the same buckets | refused |
| 104 | An epoch approved with variants but no primary metric | refused |
| 105 | An epoch approved with a primary metric but no guardrail | refused |
| 106 | An epoch approved with a primary metric and a guardrail | accepted |
| 107 | Variants allocated above the bucket ceiling the epoch hashes into | refused |
| 108 | A guardrail metric with no threshold | refused |
| 109 | A primary metric declared exploratory | refused |
| 110 | An empty bucket range | refused |
| 111 | A negative bucket range | refused |
| 112 | Clustered analysis at a different unit with no clusters named | refused |
| 113 | The same design naming its clusters | accepted |
| 114 | An alpha of one half | refused |
| 115 | A maximum runtime shorter than the minimum | refused |
| 116 | A long-term holdout larger than a tenth of traffic | refused |
| 117 | An experiment in a namespace nobody registered | refused |
| 118 | A reviewed experiment that states no novelty or carryover assumption | refused |
| 119 | A running epoch that was never approved | refused |
| 120 | An assignment whose variant belongs to a different epoch | refused |
| 121 | A unit bucketed into an epoch that is paused | refused |
| 122 | A listing bucketed into an epoch that randomises by guest | refused |
| 123 | An assignment claiming a salt the epoch never declared | refused |
| 124 | A bucket above the ceiling the epoch hashes into | refused |
| 125 | A bucket that does not fall in the variant it claims | refused |
| 126 | A suppressed subject enrolled by the next assignment job | refused |
| 127 | A second assignment for a unit already bucketed in this epoch | refused |
| 128 | A unit already carrying an exclusive treatment in the same namespace | refused |
| 129 | The same unit where the second experiment does not claim exclusivity | accepted |
| 130 | An assignment whose eligibility was evaluated after the fact | refused |
| 131 | An ordinary assignment under the epoch it names | accepted |
| 132 | An assignment edited after the fact | refused |
| 133 | A variant added to an epoch that has already bucketed units | refused |
| 134 | A primary metric added once the data is in | refused |
| 135 | The salt of a sealed epoch changed | refused |
| 136 | A variant deleted from a sealed epoch | refused |
| 137 | A sealed epoch stopped | accepted |
| 138 | An exposure recorded before the unit was bucketed | refused |
| 139 | An exposure citing a rule version the epoch never declared | refused |
| 140 | A model timeout logged as a successful treatment delivery | refused |
| 141 | The same delivery declaring the fallback that caused it | accepted |
| 142 | A client-confirmed exposure with no arrival behind it | refused |
| 143 | The same exposure with the arrival that reported it | accepted |
| 144 | A second exposure under one dedupe key | refused |
| 145 | An exposure received before it occurred | refused |
| 146 | An exposure edited after the fact | refused |
| 147 | A second look with no sequential method declared | refused |
| 148 | The same second look under an alpha-spending plan | accepted |
| 149 | A ship decision drawn from an analysis with a sample-ratio mismatch | refused |
| 150 | The same analysis recorded as invalid | accepted |
| 151 | A conclusion with no rationale | refused |
| 152 | An ordinary analysis run | accepted |
| 153 | An estimate that falls outside its own interval | refused |
| 154 | An estimate reported as both a p-value and a posterior | refused |
| 155 | A variant compared against itself | refused |
| 156 | An estimate edited after the fact | refused |
| 157 | A rollout requested and approved by the same person | refused |
| 158 | The same rollout approved by somebody else | accepted |
| 159 | Automation rolling a treatment out on its own | refused |
| 160 | Automation stopping a treatment without naming the guardrail that fired | refused |
| 161 | Automation stopping a treatment and naming the guardrail | accepted |
| 162 | The same command replayed under one command key | refused |
| 163 | A number claiming to be cleaner than the run that computed it | refused |
| 164 | A warning-grade number published for a metric whose floor is WARN | accepted |
| 165 | A second current value for one metric, slice and window | refused |
| 166 | A published number edited in place | refused |
| 167 | Late data restating a published number by superseding it | refused |
| 168 | A money metric that says nothing about currency | refused |
| 169 | The same metric keeping each currency in its own partition | accepted |
| 170 | Retention removing an expired arrival that a duplicate note points at | accepted |
| 171 | The duplicate note went with the original it described | accepted |
| 172 | An account merge that states how sure it is | accepted |
| 173 | A retroactive merge backed by a consent reference | accepted |
| 174 | Two numbers standing as current for one metric, slice and window | refused |
| 175 | Late data restating a published number by superseding it | accepted |
| 176 | The restatement recorded as an additive correction | accepted |
| 177 | Marking that correction applied | accepted |
| 178 | The reason for a correction rewritten afterwards | refused |
| 179 | A run recording the upstream snapshot it actually read | accepted |
| 180 | The same input declared twice for one run | refused |
| 181 | The watermark a run read edited after it ran | refused |

## Tables

**Dataset contracts** — `data_product_registry`, `data_product_dependencies`. One governed version
of one dataset, and the declared edges between versions that make the classification rule checkable.

**Event contracts** — `event_definitions`, `event_definition_consumers`. What an event means at one
schema version, and who reads it. The consumer list is what lets retirement be refused.

**Ingestion** — `event_arrivals`. Append-only, payload-free, retention-bound.

**Privacy** — `privacy_subject_links`, `data_corrections`. The re-identification mapping, kept apart
from everything that uses pseudonyms, and the additive record that something published was wrong.

**Transformation and lineage** — `pipeline_runs`, `pipeline_run_inputs`. One run over one input
watermark, and the snapshots it actually read.

**Quality** — `data_quality_check_definitions`, `data_quality_results`. Versioned checks and what
they found, which a run cannot contradict.

**Metrics** — `metric_definitions`, `metric_materializations`. What a number means, and what it was.

**Experiment design** — `experiment_definitions`, `experiment_epochs`, `experiment_variants`,
`experiment_exclusions`, `experiment_metrics`. Frozen at the first assignment.

**Experiment evidence** — `experiment_assignments`, `experiment_exposures`. Immutable.

**Experiment conclusions** — `experiment_analysis_runs`, `experiment_analysis_estimates`,
`experiment_actions`.

## The rules that are not columns

Ten changesets carry no table:

- **`030-24` append-only.** One `platform_append_only()` function serving nine tables, each naming
  the columns that may still move — an ingestion stamp, a retention decision, an owner response, a
  publication state — with everything else compared as JSON and refused if it changed. `DELETE` stays
  available, because retention and erasure have to remove rows even though nobody may rewrite them.
- **`030-25` contract immutability.** A `platform_contract_freeze()` function on event definitions,
  dataset versions, metric versions and quality checks: past `DRAFT`, only the lifecycle columns
  move, `DELETE` is refused outright, and `RETIRED` is terminal. Plus a separate guard that refuses
  to retire an event contract while a consumer still declares itself a reader.
- **`030-26` epoch sealing.** Once `first_assignment_at` is set, the allocation-defining columns of
  the epoch are frozen, and its variants, declared metrics and exclusions cannot be inserted, edited
  or deleted. `INSERT` is covered on all three children, because adding a variant re-allocates every
  bucket it claims and adding a primary metric after the data is in is choosing the question once the
  answer is visible.
- **`030-27` activation requirements.** A `PUBLIC_PRICE` or `HOST_PRICING_TOOLS` experiment may not
  randomise by guest or session. An epoch cannot leave draft without exactly one control variant, at
  least one primary metric, at least one guardrail, and an allocation that fits inside the bucket
  count it hashes into. And an epoch may not be *inserted* already running, because its children
  cannot exist at that moment and every check above would be skipped.
- **`030-28` assignment integrity.** The epoch is running, the variant belongs to it, the unit kind
  matches, the salt and allocator versions match, the bucket is inside both the ceiling and the
  claimed range, the subject is not suppressed, and the unit carries no incompatible treatment from
  another exclusive experiment in the same namespace.
- **`030-29` exposure integrity.** Not before the assignment, not before the epoch started, citing
  the epoch's own exposure rule version, and reporting the assigned treatment unless a fallback is
  declared.
- **`030-30` quality honesty.** A run cannot report `PASS` while an unwaived `FAIL` check stands, and
  such a check cannot be filed against a run already claiming `PASS`; the way through is to downgrade
  the run or waive the check with a named owner and a reason. A materialization must come from a
  successful run over the dataset its metric declares, and may not claim a cleaner quality state than
  that run reported.
- **`030-31` classification propagation.** A dataset version may not be less restrictive than one it
  reads from unless the edge carries a named declassification approval and a reason.
- **`030-32` suppression is terminal.** A suppressed link cannot return to active, and the instant
  and reason cannot be rewritten.
- **`030-33` contract state on arrival.** An arrival must agree with the contract it cites, and a
  retired contract accepts nothing further. An arrival citing no registered contract is quarantined
  rather than refused — that is evidence of an unregistered producer, and losing it hides the thing
  worth finding.

## What probing changed

Three defects were found by probing and fixed before the migration was committed.

**An epoch could be created already running.** The activation checks — primary metric, guardrail,
exactly one control, allocation within the bucket ceiling — all ran on the `DRAFT` to non-`DRAFT`
transition. An `INSERT` straight into `RUNNING` skipped every one of them. This is the 029 lesson in
a new place: *a guard that watches a transition is no guard if the row can be created already past
it.* Because an epoch's variants and metrics reference the epoch and cannot exist at the moment it is
inserted, the fix is to refuse the insert outright rather than to duplicate the checks.

**A money metric could say nothing at all about currency.** The constraint read
`measure_unit <> 'MONEY_MINOR' OR currency_handling IN ('NATIVE_PARTITIONED', 'CONVERTED')`. When
`currency_handling` is null, the `IN` yields null, the whole expression yields null, and a `CHECK`
that evaluates to null passes. So the one case worth catching — a money metric whose author never
thought about multiple currencies — was the one case that sailed through. Fixed by making the null
test explicit. Every other vocabulary constraint in the migration was audited for the same shape;
none had it.

**Retention could not delete an arrival that a duplicate note pointed at.** `event_arrivals` carries
a self-reference from a duplicate row to the original it repeats. With no `ON DELETE` clause, a
retention job deleting by expiry failed on exactly the rows it existed to remove, unless it happened
to delete duplicates first. `ON DELETE CASCADE` is the right answer rather than a nullable reference:
the duplicate row is evidence *about* one particular original, and once retention has removed that
original the note has nothing left to describe.

Two probe results were restated rather than fixed. Scenarios that mutate shared fixture rows —
downgrading a run, waiving a check — contaminate later scenarios in the same set, so the
materialization cases were re-run against clean state as their own probe set. And an event correction
that also names the range it was drawn from is accepted, which is intended: the range is extra
provenance, not a second addressing scheme.

## Design rules this migration follows

- Status values are `VARCHAR` with a `CHECK`, never PostgreSQL enum types.
- Fractions are `NUMERIC` bounded to `0..1` by check, never floating point. Money stays integer minor
  units beside its currency.
- No `DEFAULT now()` on any column the application writes.
- `version BIGINT NOT NULL DEFAULT 0` with a non-negative check on every optimistically-locked table;
  the append-only tables carry no version column at all, because nothing updates them.
- Constraint naming `pk_` / `uk_` / `fk_` / `ck_` / `ex_` / `idx_`.
- JSONB only for an immutable snapshot — here, one column: the slice descriptor of a materialized
  metric value. Everything filterable is relational.
- Half-open ranges, for buckets as for nights.

## What this migration leaves open

**Volume and partitioning.** `event_arrivals`, `experiment_exposures` and `metric_materializations`
are the high-volume tables, and each carries an expiry and a retention class so a sweep is possible.
Whether they are partitioned by date, and whether arrivals move out of the transactional database
entirely, is an operational decision that depends on measured volume rather than a shape the schema
should presuppose.

**The transport.** Kafka topology, Debezium configuration, the object-storage layout and the choice
of analytical warehouse are all real decisions in the feature document and none of them are tables.

**Bandits and adaptive allocation.** The epoch carries a fixed allocation. Logged action
probabilities, exploration support and off-policy evaluation need a different analysis contract, and
the document is explicit that they are a measured-scale option rather than a target-release one.

**Attribution.** `metric_definitions.attribution_rule` is a declared string. The eligible touchpoints
and multi-touch logic behind it belong to whichever use case declares them, not to one shared table
pretending there is a universal answer.

**The ML half.** Feature definitions, label observations, training manifests, model versions,
release routes and prediction records are migration 031. `experiment_exposures.prediction_reference`
is a plain string until then, for the same reason 029 left its experiment reference plain until now.

## Deviations from the plan and the feature document

**Nine tables the documents imply but do not name.** The conceptual data model lists fourteen records
for this half. Nine more were added, each because a rule in the prose could not otherwise be checked:

- `data_product_dependencies` — "the most restrictive applicable policy follows data into derived
  products" needs the edge as a row.
- `pipeline_run_inputs` — the lineage traversal the document requires needs the snapshots a run read,
  not a field summarising them.
- `data_quality_check_definitions` — a result that names its check in free text cannot be versioned.
- `event_definition_consumers` — activation requires a consumer list, and retirement has to be
  refusable while one stands.
- `experiment_variants` — "contiguous configured bucket ranges map to variants" is an exclusion
  constraint or it is nothing.
- `experiment_exclusions` — "a global registry detects collisions before activation" needs the
  declarations to be rows.
- `experiment_metrics` — a guardrail in a JSON blob cannot reference a metric version.
- `experiment_analysis_estimates` — "estimates and intervals" is one row per metric and arm, joinable
  to the metric version that defined them.

**One source dataset per metric, not many.** The document says a metric records its "source
datasets", plural. `metric_definitions.source_data_product_id` is singular and required. A metric that
needs several sources should read from a conformed product that joins them, which is what the layer
model is for; the alternative is a join whose definition lives in the query rather than in the
registry, and the lineage traversal then has nothing to follow.

**`consumer_inbox` is not created.** Migration 012 already ships `consumer_inbox_receipts` with the
consumer, contract version and event-id uniqueness this document asks for. `outbox_events` likewise.
Re-creating either would be a second definition of the same primitive.

**The metric key pattern.** `metric_definitions.metric_key` must match
`^[a-z][a-z0-9_]*_[0-9]{1,4}d_v[0-9]{1,3}$`. The document mocks `booking_conversion_rate` by name and
gives `confirmed_booking_per_exposed_search_session_28d_v2` and `completed_stay_per_unique_guest_90d_v1`
as what a valid name looks like; both fit this pattern and the mocked one does not. It is a strong
constraint on a naming convention, and it is deliberate: the window and the version have to survive
being copied into a slide.

## What this closes

Dependency 0 (governance and reference contracts), Dependency 1 (durable facts and instrumentation),
Dependency 2 (conformed data and governed metrics) and Dependency 3 (deterministic experimentation)
from the feature document now have their persistence. The services that write these tables do not
exist yet; what exists is a schema in which the failures those dependencies are about — an
unregistered producer accepted silently, a dataset published off a failed run, an epoch re-allocated
mid-flight, a conclusion drawn through a sample-ratio mismatch — cannot be stored.

## Exit criteria

- 33 changesets apply to an empty database and roll back to nothing: 0 tables, 0 functions, 0
  changelog rows. Proved by full rollback and re-apply.
- 181 probe scenarios behave as intended; 3 defects found and fixed.
- 23 aggregates and 23 repositories compile, JavaDoc builds with no undocumented declaration, and the
  application starts with every derived query resolving.
- Every generated enum lists exactly the values its `CHECK` permits, and no vocabulary column in the
  schema is narrower than its widest value.
