# Migration 031 — ML platform

## Goal

A model advises; it never decides.

Migration 030 established that a contract, an arrival, a run and a number are four different
records. This one says the same thing one layer up: a feature, a label, a training set, a registered
model, a prediction and a decision are six different records. Keeping them apart is the whole
design, because collapsing any two of them produces a system that cannot answer the only question
that matters after something goes wrong — what did this model actually see, and who agreed it could
act on it.

Nothing here decides anything. A prediction row is evidence that a model was asked and what it
answered; the action taken belongs to the domain that took it. `decision_references` is an index for
monitoring, not an authority: it can say that a booking decision cited prediction X under policy
version Y, and it can say that the policy overrode the recommendation, but it cannot change either.
As in 030, no table here carries a foreign key into `account_holders`, `listings`, `bookings`,
payments or reviews — an inference write must never take a lock on a row a guest is trying to book.

Seventeen tables, twenty-eight changesets. Eleven of those changesets contain no table at all: they
carry the rules no column can express.

## Eight forces shaping it

**Point-in-time correctness is a column, not a convention.** An offline feature value records when
its source occurred, when that source became knowable, and the interval over which the value is
effective — and `ck_offline_feature_values_point_in_time` refuses a value that becomes effective
before it could have been read. That single constraint is what stops a training set learning from
the future, which is the failure that produces a model with excellent offline metrics and no online
effect whatsoever, with nothing in the evaluation to show why.

**Not observed is not negative.** A label observation is positive, negative, unresolved, censored or
excluded, and the last three carry no value at all. An unrendered listing is not a rejection, an
unreviewed case is not a safe one, and a stay that has not finished has no satisfaction. A resolved
label may not be dated before its own horizon has ended, and a trigger holds it to the definition's
maturity delay as well — reading a seven-day outcome on day three is not an early result, it is a
different question.

**An outcome that was only observed because an earlier system selected it must say so.** Fraud,
moderation, support and ranking outcomes are visible only for the cases some previous model or rule
chose to act on. A label definition that admits selection bias forces every observation under it to
name the selecting policy, so the bias is a column a dataset builder has to handle rather than a
footnote nobody read.

**A training set is a claim about a moment.** It either gave every label its full horizon or it says
it modelled censoring, and a trigger does that arithmetic from the label definition rather than
trusting the build. Random splits are refused for time-dependent behaviour, because the same
booking, host or near-duplicate listing on both sides of a split is the cheapest way to produce a
number that means nothing. Every manifest pins a deletion watermark, so it can be shown which
erasures it honoured.

**A registered model version is immutable from the moment it is trained.** Changing weights, prompt
configuration or the artifact is a new version — not because bytes are sacred, but because every
prediction, evaluation and decision downstream names a version, and a mutable version silently
reassigns their meaning. Widening `intended_decisions` in place is refused for a sharper reason: it
expands a model's authority without any of the approvals that authority required, while every
approval row already recorded would appear to cover the new scope.

**Approval is separation of duties, enforced.** A model owner cannot self-approve. A model whose
impact is safety, financial, pricing, eligibility or moderation cannot reach `APPROVED` without the
domain, risk, privacy and security reviews plus legal or finance, and without the evidence rows to
point at — a fairness evaluation that actually produced slices, and an offline evaluation against a
declared baseline that did not fail, on a dataset whose leakage checks passed and which is still
reusable.

**Routing is where a model becomes live, and routing is the only thing a rollback changes.**
Immutable history is never rolled back. One active route exists per consumer, decision scope and
market; a champion/challenger split has to name the experiment epoch it is measured under, because
an unmeasured traffic split is not a comparison, it is a gamble with a percentage sign. The route
rather than the model owns the fail-open or fail-closed decision, since the same model can be safe
to skip on a search page and unsafe to skip at a payout decision.

**A prediction is bounded, expiring evidence, not an attribute of a person.** Its output is size
capped at four kilobytes so that a raw feature dump cannot be logged under the name of a score, it
carries an explicit uncertainty state with no null, and a failed inference is recorded as the
fallback it was rather than quietly omitted — evaluating only the predictions that succeeded hides
exactly the outage and fallback harm the evaluation exists to find.

## Verified behaviour

A hundred and sixty-four scenarios were run against the applied schema, thirty-seven accepted and a
hundred and twenty-seven refused. Every refusal below is paired with an accepted counterpart
somewhere in the suite, because a rule that refuses everything is indistinguishable from a broken
one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | A feature key with no semantic version | refused |
| 2 | A windowed feature whose key omits the window | refused |
| 3 | A windowed feature whose key names its window | accepted |
| 4 | A categorical feature with no declared categories | refused |
| 5 | An online-served feature with no time to live | refused |
| 6 | A feature served offline and online with no parity test | refused |
| 7 | A non-reproducible feature allowed consequential use | refused |
| 8 | A non-reproducible feature restricted from consequential use | accepted |
| 9 | A sensitive attribute with no lawful basis and no review | refused |
| 10 | A sensitive attribute with a named basis and a review | accepted |
| 11 | Personal data declared eligible for training | refused |
| 12 | A second semantic version naming no predecessor | refused |
| 13 | A deprecated feature naming no replacement | refused |
| 14 | A constant default policy with no constant | refused |
| 15 | A feature available in no serving context at all | refused |
| 16 | Changing what an active feature computes | refused |
| 17 | Deprecating an active feature in favour of a named successor | accepted |
| 18 | Deleting a published feature definition | refused |
| 19 | Adding a member to a frozen feature set | refused |
| 20 | Removing a member from a frozen feature set | refused |
| 21 | A listing-keyed feature in a guest-keyed set | refused |
| 22 | Adding a guest-keyed feature to a draft guest set | accepted |
| 23 | A feature set inserted already frozen | refused |
| 24 | Freezing a feature set with no members | accepted |
| 25 | Two members of one set claiming the same ordinal | refused |
| 26 | Freezing a feature set with no members | refused |
| 27 | Two members of one set claiming the same ordinal | refused |
| 28 | A value written against a draft feature definition | refused |
| 29 | A guest feature carrying a listing-keyed value | refused |
| 30 | A value effective before it could have been read | refused |
| 31 | A value knowable before the event it describes happened | refused |
| 32 | A present value with nothing in it | refused |
| 33 | A missing value that still carries a number | refused |
| 34 | An explicit missing value recorded as its own row | accepted |
| 35 | An imputed value that does not say how it was imputed | refused |
| 36 | An imputed value naming its method | accepted |
| 37 | A category nobody declared | refused |
| 38 | A declared category | accepted |
| 39 | An integer feature stored as text | refused |
| 40 | An email address written into a pseudonym column | refused |
| 41 | A validity interval that ends before it starts | refused |
| 42 | A second value for the same entity and effective instant | refused |
| 43 | Editing a stored feature value | refused |
| 44 | Serving a feature never declared available online | refused |
| 45 | A late message overwriting a newer served value | refused |
| 46 | The same overwrite declared as a historical rebuild | accepted |
| 47 | A served value that expired before it was computed | refused |
| 48 | An erasure that leaves a suppressed placeholder behind | refused |
| 49 | An erasure that removes the value | accepted |
| 50 | An entity-scoped invalidation naming no entity | refused |
| 51 | A correction invalidation naming no correction | refused |
| 52 | A withdrawn consent that expires back into use | refused |
| 53 | Retention deleting a stored feature value | accepted |
| 54 | A label key with no semantic version | refused |
| 55 | A label that admits selection bias but does not require the policy | refused |
| 56 | A target with a zero horizon | refused |
| 57 | Personal outcome data declared eligible for training | refused |
| 58 | An observation recorded against a draft label definition | refused |
| 59 | A horizon the job computed for itself | refused |
| 60 | An outcome read the instant its window closed, before it could settle | refused |
| 61 | An outcome read after its maturity delay | accepted |
| 62 | An answer dated before its horizon had ended | refused |
| 63 | A positive outcome nobody observed | refused |
| 64 | An unresolved outcome still carrying a value | refused |
| 65 | An unresolved outcome recorded as unresolved | accepted |
| 66 | A censored outcome that does not say what censored it | refused |
| 67 | A censored outcome naming its censoring reason | accepted |
| 68 | A selectively observed outcome naming no selecting policy | refused |
| 69 | The same outcome naming the policy that caused it to be reviewed | accepted |
| 70 | A human decision recorded as clean ground truth | refused |
| 71 | A human decision carrying role, policy and confidence | accepted |
| 72 | One actor weighted enough to dominate a training set | refused |
| 73 | A corrected label naming nothing it corrects | refused |
| 74 | A correction recorded as a new revision of the same example | accepted |
| 75 | Editing a recorded outcome in place | refused |
| 76 | Retention deleting an example and its revisions together | accepted |
| 77 | An example weight above the declared ceiling | refused |
| 78 | A random split of time-dependent marketplace behaviour | refused |
| 79 | A random split of behaviour declared not time-dependent | accepted |
| 80 | A validation window that overlaps the training window | refused |
| 81 | An entity-grouped split naming no grouping key | refused |
| 82 | A label cutoff that leaves the last examples unresolved | refused |
| 83 | The same cutoff with censoring declared and modelled | accepted |
| 84 | A dataset built from a feature set whose membership can still change | refused |
| 85 | A deletion watermark pinned after the build finished | refused |
| 86 | A manifest inserted already invalidated | refused |
| 87 | More distinct subjects than examples | refused |
| 88 | A feature cutoff inside the test window | refused |
| 89 | A second manifest for the same specification and snapshots | refused |
| 90 | Restating what a built dataset contained | refused |
| 91 | Invalidating a manifest for reuse after an erasure | accepted |
| 92 | Returning an invalidated manifest to service | refused |
| 93 | A model version registered already active | refused |
| 94 | Validating a model trained on a dataset that failed its leakage checks | refused |
| 95 | Editing a built manifest to make it pass its checks | refused |
| 96 | Validating a model that was never evaluated | refused |
| 97 | Validating a model with a passing offline evaluation | accepted |
| 98 | Approving a model that skipped validation | refused |
| 99 | Approving a model with no fairness evaluation | refused |
| 100 | Approving an advisory model with the consuming domain behind it | accepted |
| 101 | Approving a financial model with no finance approval | refused |
| 102 | Approving a safety model with domain, risk, privacy, security and legal | accepted |
| 103 | A model owner approving their own model | refused |
| 104 | Routing a validated but unapproved model into shadow | refused |
| 105 | Swapping the artifact behind a registered version | refused |
| 106 | Widening what an approved model is allowed to decide | refused |
| 107 | Retiring an approved model | accepted |
| 108 | Returning a retired model to service | refused |
| 109 | Pricing inputs sent to a provider permitted to train on them | refused |
| 110 | An external provider contractually barred from training on the inputs | accepted |
| 111 | An external provider with no licence, rights or residency recorded | refused |
| 112 | Re-registering the same artifact under a new version number | refused |
| 113 | Releasing a slice too small to report without identifying people | refused |
| 114 | Releasing the same small slice because the harm is a safety harm | accepted |
| 115 | A breached fairness threshold with no mitigation recorded | refused |
| 116 | A model compared against itself as the champion | refused |
| 117 | An evaluation reporting only the candidate number | refused |
| 118 | Improving a recorded evaluation result after the fact | refused |
| 119 | An active route whose champion was only approved, never activated | accepted |
| 120 | A route pointing at a retired model | refused |
| 121 | A second active route for the same consumer, scope and market | refused |
| 122 | A market-specific route alongside the global one | accepted |
| 123 | A champion/challenger split nobody is measuring | refused |
| 124 | A champion/challenger split naming the running epoch it is measured under | refused |
| 125 | A split measured under an epoch that has already stopped | refused |
| 126 | A challenger taking all of the traffic | refused |
| 127 | A model challenging itself | refused |
| 128 | An active route nobody approved | refused |
| 129 | An active route with nothing to roll back to and no deterministic path | refused |
| 130 | A prediction produced by a retired model | refused |
| 131 | A prediction resolved against a feature set the model never declared | refused |
| 132 | A prediction recorded on a route that serves another consumer | refused |
| 133 | A prediction whose model is on neither side of its own route | refused |
| 134 | A prediction that predicted nothing | refused |
| 135 | A timed-out request that still carries an output | refused |
| 136 | A fallback that does not say why it fell back | refused |
| 137 | A stale-feature fallback recorded as the fallback it was | accepted |
| 138 | A raw feature dump logged under the name of a score | refused |
| 139 | A prediction that expired before it was made | refused |
| 140 | A retry producing a second answer to the same question | refused |
| 141 | Editing a recorded prediction | refused |
| 142 | A decision citing a prediction made after it | refused |
| 143 | A decision citing a prediction that had already expired | refused |
| 144 | A decision that says nothing about whether a model was involved | refused |
| 145 | A policy override that does not say what it overrode | refused |
| 146 | A policy override recording both the recommendation and the action | accepted |
| 147 | A decision citing a different exposure than its own prediction | refused |
| 148 | Editing what a decision selected | refused |
| 149 | Monitoring automation promoting a model on its own | refused |
| 150 | Monitoring automation rolling a model back on its own | accepted |
| 151 | An operator approving their own promotion command | refused |
| 152 | A champion/challenger split naming the running epoch it is measured under | accepted |
| 153 | A split measured under an epoch that has not started | refused |
| 154 | Validating a model against a dataset an erasure has invalidated | refused |
| 155 | An active route whose rollback target has been retired | refused |
| 156 | An active route whose champion is only running in shadow | refused |
| 157 | A shadow route for a model running in shadow | accepted |
| 158 | A decision presenting a timed-out request as a prediction it used | refused |
| 159 | The same decision recording the fallback it actually used | accepted |
| 160 | Collecting a second opinion from the same function until one says yes | refused |
| 161 | Rewriting a recorded approval | refused |
| 162 | An action marked applied with no instant at which it was applied | refused |
| 163 | An applied command whose reason was rewritten afterwards | refused |
| 164 | Marking an approved promotion command applied | accepted |

## Tables

**Feature registry** — `feature_definitions`, `feature_set_versions`, `feature_set_members`. What an
input means at one semantic version, and the frozen list of exact versions one model reads. The
membership is the contract, which is what makes "a model may not read features it did not declare"
checkable rather than aspirational.

**Feature values** — `offline_feature_values`, `online_feature_values`. Historical values with an
effective interval for point-in-time lookup, append-only; and one current served value per entity,
which is the single rebuildable projection in this migration.

**Feature invalidation** — `feature_invalidations`. The record that stored values stopped being
usable, for a correction, an erasure, a withdrawn consent, an invalidated source or a retired
definition.

**Label registry and outcomes** — `label_definitions`, `label_observations`. The horizon, the
maturity delay and the five outcome states, and the append-only observations under them with their
revision chain.

**Training sets** — `training_dataset_manifests`. Immutable descriptions of builds, invalidated for
reuse without ever editing the artifact.

**Model registry** — `model_versions`, `model_approvals`. What a version is, what it may decide, and
which functions signed off on that.

**Evaluation** — `model_evaluation_runs`, `model_evaluation_slices`. What the version was compared
against, and what it did to each measured group.

**Routing** — `model_release_routes`. Where a version is live, at what share, under which
experiment.

**Serving evidence** — `prediction_records`, `model_actions`, `decision_references`. What was asked
and what came back; what was commanded and by whom; and the monitoring index of the domain decisions
that followed.

## The rules that are not columns

Eleven changesets carry no table. Two of them add no function either: `031-18` and `031-19` attach
triggers to `platform_append_only()` and `platform_contract_freeze()`, created in 030 and reused
here so that both halves of the platform freeze contracts and protect evidence the same way.

- **`031-18` append-only.** Ten tables: feature values, set members, invalidations, label
  observations, approvals, evaluations, slices, predictions, commands and decision index rows. Each
  names the columns that may still move — a released flag, a retention decision, whether a command
  was applied — and everything else is compared as JSON and refused if it changed. `DELETE` stays
  available throughout, because retention and erasure must remove rows nobody may rewrite.
- **`031-19` definition immutability.** Feature definitions, label definitions and feature set
  versions freeze past `DRAFT`; only the lifecycle columns move, `DELETE` is refused, `RETIRED` is
  terminal.
- **`031-20` feature set membership.** Membership is sealed once the set leaves `DRAFT`, a member
  must be keyed by the same entity kind as its set, a set may not be inserted already frozen, and a
  set may not be frozen with no members — an empty input contract is a model reading nothing.
- **`031-21` feature value integrity.** A value may be written only against a definition in service,
  keyed the way the definition says, into the column its declared type requires, and — for a
  categorical feature — only as a level the registry declared. Plus the monotonicity rule: a late
  message carrying an older source state may not overwrite a newer online projection unless the
  write declares itself a historical rebuild.
- **`031-22` label observation integrity.** The horizon comes from the definition rather than the
  job, the maturity delay is enforced on top of it, and a definition that admits selection bias
  refuses any observation that does not name the policy which caused the example to be observed.
- **`031-23` manifest integrity.** A build must name a label definition in service and a frozen
  feature set, must give every label its full horizon or declare censoring modelled, must be
  inserted reusable, is frozen afterwards apart from its reuse state, and can never return to
  reusable once invalidated.
- **`031-24` model version immutability.** Everything that says what a model *is* — artifact,
  checksum, dataset, feature set, label, output schema, impact class, intended decisions, prohibited
  uses — freezes the moment the version leaves `DRAFT`.
- **`031-25` promotion requirements.** The gate between "this model scores well" and "this model
  decides things": insertion past `TRAINED` is refused outright, validation requires a clean dataset
  and a passing offline evaluation, approval requires validation first, fairness slices, the
  approvals the impact class calls for, and no approver who is the model's own owner. Shadow, canary
  and active are reachable only from approved. Rejected and retired are terminal.
- **`031-26` route integrity.** No retired, rejected or rolled-back version may be routed traffic in
  any mode; an active route's champion must be approved or already active; a challenger's epoch must
  be scheduled or running; a rollback target must itself be able to take traffic.
- **`031-27` prediction integrity.** A retired model may not produce predictions, a prediction may
  not be resolved against a feature set the model never declared, and a prediction recorded on a
  route must match that route's consumer and be one of its two arms.
- **`031-28` decision coherence.** A decision may cite only a prediction that existed and had not
  expired when it was taken, must record a fallback when the thing it cites is not a prediction, and
  may not cite an exposure other than the one the prediction was made under.

## What probing changed

Nothing. All one hundred and sixty-four scenarios behaved as designed on the first applied schema,
which is worth stating plainly rather than claiming as a result: the three defect classes 029 and
030 found — a guard watching a transition a row could be created past, a nullable column inside `col
IN (...)` silently passing a `CHECK`, and an append-only self-reference with no `ON DELETE` decision
— were all designed out of this migration up front because those migrations had already found them.

Concretely: `031-20` and `031-25` refuse insertion past the state their checks guard rather than
waving it through; every conditional vocabulary constraint here was written with an explicit `IS NOT
NULL` or as an equality between two predicates; and `label_observations.supersedes_observation_id`
carries `ON DELETE CASCADE` so a retention job can remove an example's history in any order.

Two scenarios were contaminated in their first run and restated against clean state rather than
believed: freezing an empty feature set, after an earlier scenario in the same file had added a
member to it, and a champion/challenger route whose consumer an earlier scenario had already
claimed. This is the same contamination 030 hit, and the same remedy.

## Design rules this migration follows

- Status values are `VARCHAR` with a `CHECK`, never PostgreSQL enum types.
- `version BIGINT NOT NULL DEFAULT 0` with `CHECK (version >= 0)` on optimistically-locked tables.
- No `DEFAULT now()`: every table here is written by the application, a pipeline worker or a serving
  process, and the instants come from the shared application clock.
- Fractions are `NUMERIC` bounded to zero-to-one; durations are `INTERVAL`, which is new in this
  migration and needed a `Duration` converter pair in `JdbcConversionConfig`.
- Constraint names are prefixed `pk_` / `uk_` / `fk_` / `ck_` / `idx_`.
- Pseudonyms are checked against a hex pattern, so a raw identifier cannot be written into one.

## What this migration leaves open

- **Where the data actually lives.** Embedding vectors, dataset artifacts and model artifacts live
  in approved object or analytical storage. What is stored here is the manifest, the checksum, the
  lifecycle and the access policy.
- **The serving runtime.** Model hosting, the inference gateway, feature-store technology choice and
  the batch scoring scheduler are operational choices no table can hold.
- **Generative use.** Grounding, output schema validation, hallucination and prompt-injection
  testing and provider-data review are declared in the model card and the inference contract, which
  this schema references rather than encodes.
- **Bandits and adaptive allocation.** Deliberately absent, as in 030: an allocation that moves
  while the experiment runs is a different statistical object, and the epoch sealing rule would have
  to be reopened to support it.
- **Counterfactual replay.** The decision index keeps the policy version and the recommendation
  alongside the selected action, which is what replay needs; the replay itself is a pipeline.

## Deviations from the plan and the feature document

The plan lists twelve records. Seventeen tables are created. The five additions are each a thing the
feature document describes in prose without naming a table for:

- **`feature_set_members`** — the document says feature set versions carry "immutable member
  definition versions; unique set/version/member", which is a child table.
- **`feature_invalidations`** — "Corrections, deletion, consent withdrawal, source invalidation, and
  definition retirement produce invalidation records."
- **`model_approvals`** — the registered version carries "owner, approvers, approval evidence",
  plural, and consequential uses require several named functions. One `approved_by` column cannot
  express a rule about who has and has not signed.
- **`model_evaluation_slices`** — evaluation records "metrics/slices", and the fairness section is
  entirely about per-slice results with cohort thresholds. A slice is a row.
- **`decision_references`** — "D20 may keep a cross-domain index for monitoring but does not own or
  mutate the decision."

Three smaller deviations:

- **One source data product per feature and per label**, not several. The same reasoning as
  `metric_definitions` in 030: a feature reading many sources should read from a conformed product,
  so that lineage has a single edge to follow.
- **`label_observations.selection_model_version` is a plain string**, not a foreign key into
  `model_versions`. The system that selected an example is often a deterministic rule or another
  domain's classifier, with no row in this registry; a foreign key would have forced fabricating
  one.
- **A feature key pattern**, which the document does not require. It forces the semantic version
  into the name, and forces a windowed feature's key to contain its own window, so that a 30-day
  feature cannot be silently swapped for the 365-day one in a set nobody re-read.
- **The Java enum for a feature's declared type is `FeatureDataType`**, not `FeatureValueType`:
  migration 027 already owns that name for the risk feature registry, whose vocabulary is a different
  one. Two enums may share a value set, but not a name.

## What this closes

D20's ML half: point-in-time feature storage, label design with explicit unresolved and censored
states, immutable training manifests with deletion watermarks, a model registry with enforced
separation of duties, bounded expiring prediction serving, and a monitoring index that keeps the
policy layer and the model layer separable.

## Exit criteria

- Twenty-eight changesets apply to an empty database and to the current one.
- All twenty-eight roll back cleanly: seventeen tables, ten functions and twenty-eight changelog
  rows removed, with 030's two shared functions correctly left in place.
- One hundred and sixty-four probe scenarios, thirty-seven accepted and one hundred and twenty-seven
  refused, no unexpected errors.
- Seventeen aggregates and seventeen repositories compile, `javadoc` is clean, and the application
  boots with 356 repositories and no query-derivation failure.
- Every enum matches its `CHECK` vocabulary exactly, and every enum column is wide enough for its
  longest value.
