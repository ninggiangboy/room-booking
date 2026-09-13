# Migration 027 — Trust, safety, fraud and moderation

## Goal

A score is not a decision, and a decision is not enforcement. This migration exists so that the three
stay separable rows: what was observed, what was decided under which version of which rule, and which
authoritative domain command actually honoured that decision. Collapsing any two of them is how a
protection system becomes unauditable and unappealable — and the platform loses the ability to answer
the only question that matters afterwards, which is *why*.

Thirty tables, thirty-nine changesets. Nine of those changesets contain no table at all: they carry
the rules no column can express.

## Six forces shaping it

**Risk advises; domains enforce.** Nothing here writes inventory, money, sessions or publication. A
decision is recorded, and a separate enforcement row says which domain command consumed it — so "this
booking was denied" and "this booking was never created" are different claims with different
evidence. A trigger refuses an enforcement row claiming it proceeded on a decision that denied, one
honouring a decision that had expired or been superseded, and one recorded before its decision was
taken. Doing something else is permitted, provided it is recorded as a divergence with a stated
reason.

**One action has one effective decision.** The canonical evaluation identity is unique across action,
actor, resource, command and policy epoch, so a retry replays the recorded decision rather than
producing a second one. Nulls are treated as equal in that index: an action with no resource and no
command still collapses to one answer instead of to an unlimited number of rows that all look unique
because something was null. Re-evaluation is a new row naming its predecessor; the original outcome is
frozen by trigger.

**Evidence is immutable; interpretation is versioned.** Signals, feature snapshots, model predictions,
rule hits, challenge attempts, content revisions, detector assessments, reviewer actions and access
audit rows are append-only — covering `INSERT` on children of anything that can freeze, which is the
defect migration `022` found when a balanced pair of postings could be appended to a posted
transaction. A correction is a new row naming the row it corrects.

**Unknown is not safe.** Missing facts are a recorded state, not an absence. A decision that allows
while mandatory facts were missing must name the registered fallback it used; a prediction with no
score must say which fallback served it; a detector that did not run may report no category; and a
signal whose provenance is a single party's allegation may not carry a verified confidence class.

**Time bounds every temporary intervention.** A hold needs a deadline, a quarantine needs a review
date, a challenge needs an expiry and an attempt ceiling fixed at issue, and a restriction is either
declared permanent or carries an end or a review date. An active restriction's end may be brought
forward but never pushed back, because a late expiry worker extending a restriction is
indistinguishable from a punishment nobody decided — and neither the subject nor an appeal reviewer
could tell the two apart afterwards.

**Relationship does not prove culpability, and a protected attribute is not a feature.** An entity
link may only justify an adverse decision with independent corroboration behind it; a feature derived
from a protected attribute needs a named permitted purpose and a legal review reference; and a feature
kept for fairness auditing may not also be an operational decision input. That last one is the access
separation the feature document requires, made structural rather than procedural.

## Verified behaviour

One hundred and sixty-two scenarios were executed against PostgreSQL 17 with the migration applied.
Every refusal is paired with an accepted counterpart, so the table shows a rule being enforced rather
than a table that refuses everything.

| Scenario | Result |
| --- | --- |
| A duplicate subject identity | refused |
| The same source identifier under a different subject type | accepted |
| An account subject with no account holder behind it | refused |
| An urgent safety action that fails open | refused |
| An urgent safety action with no human escalation | refused |
| An urgent safety action failing closed with escalation | accepted |
| An outcome outside the six in the registry | refused |
| An action permitting a hold with no hold ceiling | refused |
| No appeal path and no documented reason for withholding one | refused |
| A registered fallback outside the action's own permitted outcomes | refused |
| A redelivered observation under the same source record | refused |
| An allegation recorded as a verified fact | refused |
| The same allegation recorded as unverified | accepted |
| A transient device observation that never expires | refused |
| A money signal with no currency | refused |
| Editing a recorded observation | refused |
| Correcting it with a new row naming it | accepted |
| A protected-attribute feature with no legal review behind it | refused |
| A fairness-audit feature also permitted as an operational input | refused |
| The same feature with operational use withheld | accepted |
| An online feature with no maximum age | refused |
| Editing an approved feature definition | refused |
| Deprecating it | accepted |
| A snapshot whose watermark is ahead of the instant it describes | refused |
| A snapshot with neither values nor a storage reference | refused |
| Editing a decision-time snapshot | refused |
| A prediction that timed out yet carries a score | refused |
| The same timeout with no score | accepted |
| A served prediction with no score | refused |
| A served prediction with a score | accepted |
| A second active policy version overlapping the first | refused |
| The author approving their own policy | refused |
| A tier-three policy activated on one approval | refused |
| The same policy once a second approver has signed | accepted |
| A policy written straight into the active state with no approval | refused |
| A policy activated over a standing rejection | refused |
| An emergency policy with no expiry | refused |
| Editing an active policy's rules | refused |
| Engaging its kill switch | accepted |
| A second decision under one canonical evaluation identity | refused |
| Re-evaluating the same action under a new policy epoch | accepted |
| A decision on an action nobody registered | refused |
| A decision on an action that is still a draft | refused |
| An outcome the action did not register | refused |
| An outcome the action did register | accepted |
| A decision naming a policy from a different epoch | refused |
| A decision under a policy that does not cover the action | refused |
| A hold with no deadline | refused |
| A limit with no stated scope | refused |
| A denial with no user-facing reason family | refused |
| Allowing while mandatory facts were missing, with no fallback named | refused |
| The same, naming the registered fallback | accepted |
| A precautionary decision that never ends | refused |
| Changing a recorded outcome | refused |
| Moving the operational projection instead | accepted |
| An explanation-only rule that moved the answer | refused |
| A rule that errored yet counted as a finding | refused |
| A mandatory rule that matched and counted | accepted |
| Editing a recorded rule hit | refused |
| A duplicate rule-hit sequence | refused |
| A command claiming it proceeded on a decision that denied | refused |
| The same command recording that it blocked | accepted |
| A divergence with no reason given | refused |
| A divergence that says why | accepted |
| Enforcing a decision that had already expired | refused |
| Enforcing it inside its window | accepted |
| Two decisions enforced by one domain command | refused |
| A hold enforced as a hold | accepted |
| Enforcement recorded before the decision was taken | refused |
| An attempt beyond the challenge ceiling | refused |
| An attempt within it | accepted |
| Raising the ceiling after issue | refused |
| Extending the expiry after issue | refused |
| Passing with no evidence behind it | refused |
| Moving the challenge forward | accepted |
| Moving it backwards | refused |
| Lowering the attempt count already used | refused |
| Passing it with evidence and an assurance level | accepted |
| Recording an attempt after it ended | refused |
| Reopening a completed challenge | refused |
| A second live restriction with the same target, scope and intent | refused |
| A second live restriction under a different intent | accepted |
| A restriction that is neither permanent nor bounded | refused |
| A legacy-status restriction that invents a policy | refused |
| The same, admitting it has no policy behind it | accepted |
| Extending an active restriction | refused |
| Making an active restriction open-ended | refused |
| Bringing its end forward | accepted |
| Repointing an active restriction at another account | refused |
| Revoking it with no reason or revoker | refused |
| Revoking it with both | accepted |
| Reactivating a revoked restriction | refused |
| Deleting a restriction outright | refused |
| An urgent safety task ordered by a model score | refused |
| The same task ordered by severity | accepted |
| A claimed task with no lease behind it | refused |
| A stale worker writing back an older fencing token | refused |
| A fresh claim taking the lease forward | accepted |
| A reviewer acting under a stale lease token | refused |
| The current lease holder acting | accepted |
| A reviewer acting on their own case | refused |
| Naming somebody else as the subject to get around it | refused |
| Overturning a decision without producing a new one | refused |
| A maker-checker action with the same person on both sides | refused |
| A terminal action on a claimed task | accepted |
| A second terminal action on the same task | refused |
| Editing a recorded reviewer action | refused |
| An appeal heard by the person who decided | refused |
| An appeal naming the wrong original decider | refused |
| An appeal heard by somebody independent | accepted |
| A second appeal on the same target, appellant and round | refused |
| A second round | accepted |
| An appeal pointing at two targets at once | refused |
| Deciding an appeal with no findings | refused |
| Granting one with no prospective restoration date | refused |
| Granting it with one | accepted |
| A bulk export with no approval or record count | refused |
| The same export, approved and counted | accepted |
| Break-glass access with no post-use review | refused |
| Break-glass recorded properly | accepted |
| Erasing an access record | refused |
| The same source event counted twice against a velocity counter | refused |
| A distinct source event contributing | accepted |
| A counter whose distinct entities outnumber its events | refused |
| A second revision under the same number | refused |
| An unscanned attachment defaulting to visible | refused |
| The same attachment quarantined | accepted |
| Editing a submitted revision | refused |
| Publishing a revision that has since been edited | refused |
| Removing that same older revision | accepted |
| Publishing the latest revision | accepted |
| A second effective decision on one revision | refused |
| A quarantine with no review deadline | refused |
| A quarantine that ends | accepted |
| A safety escalation that also hides the content | refused |
| The same escalation leaving visibility alone | accepted |
| Masking with no span map kept | refused |
| A rejection with no reason the author can be told | refused |
| An automated decision naming a human decider | refused |
| Editing a recorded moderation decision | refused |
| A detector that timed out yet reported a category | refused |
| A malware scan with no evidence kept | refused |
| Two detector versions disagreeing about one revision | accepted |
| A report whose reporter identity is disclosed by default | refused |
| The same report kept confidential | accepted |
| The same reporter filing the same complaint twice | refused |
| An urgent safety report with no review task behind it | refused |
| The same report routed to a task | accepted |
| A weak link cleared for adverse use | refused |
| The same link kept for investigation only | accepted |
| A link expiring before it was last seen | refused |
| An undirected link stored in non-canonical order | refused |
| A label trained on the decision that produced it | refused |
| A provisional label marked training eligible | refused |
| A label available for training before it was observed | refused |
| A confirmed, matured, human-adjudicated label | accepted |
| A second live label for one subject and taxonomy | refused |
| Editing an active label taxonomy | refused |
| Superseding a live restriction with a replacement for the same intent | accepted |
| Superseding a live label with a corrected one | accepted |
| Superseding a moderation decision with a later one | accepted |
| Naming a successor restriction that never arrives | refused |

Two of those results were defects the probes found rather than rules they confirmed, and both were
fixed before the migration was committed. They are described under *What probing changed* below.

## Tables

**Subjects and the action registry.** `risk_subjects`, `risk_protected_actions`.

**Evidence.** `risk_signals`, `risk_signal_subjects`, `risk_feature_definitions`,
`risk_feature_snapshots`, `risk_model_predictions`, `risk_velocity_counters`,
`risk_velocity_contributions`.

**Policy.** `risk_policies`, `risk_policy_approvals`.

**Decisions.** `risk_decisions`, `risk_decision_rule_hits`, `risk_decision_enforcements`.

**Interventions.** `risk_challenges`, `risk_challenge_attempts`, `risk_restrictions`.

**Human review and appeal.** `risk_review_queues`, `risk_review_tasks`, `risk_review_actions`,
`risk_appeals`, `risk_access_audit`.

**Content moderation.** `content_items`, `content_revisions`, `moderation_assessments`,
`moderation_decisions`, `content_reports`.

**Relationships and labels.** `entity_links`, `risk_label_taxonomy_versions`, `risk_labels`.

## The rules that are not columns

Nine changesets hold triggers. They exist because each rule is about more than one row, and a rule
about more than one row cannot be a `CHECK`.

- **`027-31` evidence is append-only.** One function serves eleven tables, because the rule is
  identical and eleven copies of six lines is eleven places for it to drift.
- **`027-32` registries are frozen.** An approved feature definition, an active policy version and an
  active label taxonomy cannot be edited. Their lifecycle columns stay open: retiring a version,
  closing its window, engaging a kill switch and recording a post-use review are operations on the
  version, not changes to what it means. An active policy's window may be closed but never reopened.
- **`027-33` maker-checker.** The author may not approve their own policy. Activating one needs at
  least one approval, two for tier three or four, and none of them may be a standing rejection. The
  guard covers `INSERT` as well as `UPDATE` — without that, a row written straight into the active
  state skips every approval the update path enforces.
- **`027-34` decisions are immutable and bound to the registry.** The outcome is frozen; the
  projection and the supersession pointer are not. A decision must name a registered, active action,
  carry an outcome that action permits, and — where it names a policy — one whose epoch matches the
  epoch it claims to have bound and which actually covers the action.
- **`027-35` enforcement fidelity.** The bridge between advice and effect, and the row most worth
  lying about.
- **`027-36` challenge lifecycle.** The state never falls, the attempt count never falls, the ceiling
  and expiry are fixed at issue, and no attempt may be recorded on a challenge that has ended or after
  it expired.
- **`027-37` restriction lifecycle.** What a live restriction is about is frozen, and its end may only
  move earlier.
- **`027-38` reviewer independence.** Nobody reviews their own case; the denormalized subject is
  validated against the task so the rule cannot be defeated by naming somebody else. Only the current
  lease holder may act, and a terminal action requires a claimed task.
- **`027-39` moderation binds to a revision.** A decision that makes content visible must name the
  latest revision of its item, so an old approval cannot publish a new one. A removal or a safety
  escalation may always name an older revision, because those are about what was said then.

## Design rules this migration follows

- Status values are `VARCHAR` + `CHECK`, never PostgreSQL enum types; the enum-width audit was widened
  this time to cover multi-column checks as well, and found one column narrower than its own longest
  literal.
- No `DEFAULT now()` on any application-written column, and no partial index predicate reads the clock
  — the worker binds its own instant, and enforcement compares that instant against stored bounds
  rather than trusting a state column a late worker may not have updated yet.
- Worker queues claim with `FOR UPDATE SKIP LOCKED` under a lease with an owner and an expiry, plus a
  monotonic fencing token so a stalled reviewer cannot decide a case somebody else has since taken.
- Append-only and freeze triggers use the `%ROWTYPE` candidate pattern.
- Overlapping live policy versions are refused by a GiST exclusion constraint, the same device
  migration `018` uses against double-booking a unit.
- The forward-pointing supersession foreign keys on restrictions, moderation decisions and labels are
  `DEFERRABLE INITIALLY DEFERRED`, because the live-row indexes force the outgoing row to name its
  replacement before that replacement exists.
- No raw credential, document, token, bank detail, message body or full device identifier is stored.
  Content lives in its owning domain or in protected storage; this domain keeps stable references,
  digests, span maps and decisions.

## What probing changed

**The activation guard covered only `UPDATE`.** A policy could be inserted directly in the active
state, skipping the approval count, the rejection check and the maker-checker rule entirely. This is
the same shape as the defect migration `022` found on posted ledger transactions: a guard that watches
a transition is no guard at all if the row can be created already past it. Fixed by covering `INSERT`
and testing the transition inside the function.

**Supersession was deadlocked on three tables.** `risk_restrictions`, `moderation_decisions` and
`risk_labels` each pair a "one live row" index with a self-referencing successor pointer, so the
outgoing row must name its replacement before that replacement exists — and an immediate foreign key
refuses a reference to a row that is not there yet. Replacing a restriction was therefore impossible
without first leaving the subject unrestricted. Fixed by deferring the three foreign keys to commit,
which was verified both ways: the pair now commits together, and naming a successor that never arrives
is still refused.

## What this migration leaves open

- **No backfill from `users.status`.** The feature document describes importing known account-wide
  limitations with origin `LEGACY_STATUS`. The origin and its constraint exist — such a row may claim
  neither policy nor decision — but the import itself belongs with the service that will dual-read the
  old column, not ahead of it.
- **No decision reference on domain commands.** The document is explicit that the nullable reference
  is added to authoritative commands only after these records exist, and required later for configured
  protected actions. `risk_decision_enforcements` records the pairing from this side meanwhile.
- **No break-glass grant registry.** `risk_access_audit` records the use and forces the post-use
  review; the grants themselves belong to administration and governance, migration `032`.
- **No graph store.** Entity links are bounded relational rows, deliberately. A graph database or
  graph neural network needs a measured need, a privacy review and an explainability strategy first,
  and none of those is a schema.
- **No feature or model store.** Feature values and model artifacts live in the machine-learning
  platform, migration `031`. What lives here is the decision-time snapshot and the prediction record
  that make one decision reproducible.

## Deviations from the plan and the feature document

**No second idempotency, outbox, inbox or audit table.** The document proposes risk-prefixed copies of
each. Migration `012` delivered `command_idempotency_records`, `outbox_events`,
`consumer_inbox_receipts` and `audit_events`; a second copy of any of them would mean two answers to
the same question and a second publisher to operate. `risk_access_audit` is not a duplicate: it records
reads and exports of protected evidence, which the general audit stream deliberately does not carry.

**`capability_restrictions` was not replaced.** Migration `014` owns the row authorization evaluates on
every request. `risk_restrictions` is the governed intervention behind it — reason, policy, decision,
appeal, review date, enforcement mode — and names the `capability_restrictions` row identity wrote to
honour it. Risk does not write that row.

**Four tables the document does not name were added.** `risk_protected_actions`, because the document
requires every action to register its latency, outcomes, failure mode and appeal path in advance and
forbids a generic endpoint accepting a client-supplied action name — a requirement with nowhere to live
otherwise. `risk_policy_approvals`, because maker-checker has to be countable. `risk_review_queues`,
because routing by skill and tier needs a queue to route to. `risk_velocity_counters` and
`risk_velocity_contributions`, because the document asks for database-backed counters that are
idempotent by source event and lockable for strong transaction limits.

**`risk_signal_subjects` is a table rather than a column.** The document describes signals with
subjects, plural. Three nullable columns would make "everything about this account" a full scan.

## What this closes

- A protection system where a model score and an enforcement action are the same row, so nobody can
  say afterwards which rule denied somebody and under what version of it.
- A denial that was recorded but never enforced, or an approval that proceeded past a denial, with
  nothing in the database able to tell the two apart.
- A restriction that quietly outlives its decision because an expiry worker ran late and pushed the
  end date out.
- A challenge whose attempt ceiling can be reset by asking again, which is a brute-force tool wearing
  a security control's name.
- An old moderation approval publishing content the author has since changed.
- A model trained on the answers it produced, and an unverified allegation treated as ground truth.
- A feature derived from a protected attribute entering an operational decision because the fairness
  audit and the production allowlist were the same list.
- A reviewer deciding their own case, or hearing the appeal against their own decision.

## Exit criteria

- `027` applies cleanly on an empty database and on a database at `026`.
- `liquibase rollback-count --count=39` reverses every changeset, leaving no table, function or
  changelog row behind; re-applying afterwards succeeds.
- The enum-width audit, across single-column and multi-column checks alike, returns no rows.
- The one hundred and sixty-two scenarios above behave as tabulated.
- Every aggregate matches its table in both directions, including nullability.
- `./gradlew compileJava javadoc` is clean, and the application starts with no derived-query failure.
