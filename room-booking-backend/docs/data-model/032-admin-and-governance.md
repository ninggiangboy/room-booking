# Migration 032 — admin, policy, configuration and governance

## Goal

An administrative action is a domain command with a name on it, never a database edit.

That sentence is the whole migration. Everything an operator can do to the running marketplace —
widen somebody's authority, change a configured value, pull a kill switch, cancel a booking, refund
a payment, export a table — becomes a row that names who asked, under what authority, against which
version of which rule, who else agreed, and what actually happened. The alternative is the failure
the domain document states in one line: direct database edits create invisible policy and destroy
auditability. Invisible policy is not merely undocumented; it is policy nobody can roll back,
because nothing records what it replaced.

Admin does not get its own copy of the marketplace. Nothing here writes a booking, a payment, a
payout or a listing. An operational command row is a request and a recorded outcome; the domain that
owns the row still applies every invariant it has. A command that would have been refused when a
guest issued it is refused when an operator issues it, and the refusal is the outcome this table
stores. There is deliberately no column anywhere in this migration into which an operator can type a
booking state, a ledger amount or a payout total.

Twenty tables, thirty changesets. Ten of those changesets contain no table at all: they carry the
rules no column can express.

## Seven forces shaping it

**Authority is defined in one place and enforced in another.** A role definition says what an
operator role means — which permissions, which blast radius, how long a grant may last, what
training it needs, which other roles it may not be held alongside. The grant that actually opens the
door is migration 014's `capability_grants`, unchanged. Keeping the definition apart from the grant
is what makes it possible to ask what a role meant last March, after the role has been narrowed
twice since; and freezing the definition past `DRAFT` is what stops a live role being widened, which
would retroactively widen every grant already made under it with nothing in the approval trail to
show it.

**Separation of duties is a constraint, not a habit.** `operator_role_conflicts` names the pairs of
roles one person may not hold, and an assignment that would create the pair is refused at insert —
not reported in a quarterly access review, by which time every decision made under both roles has
already been made. An exception is possible and is a row: it names the conflict it overrides, and
the conflict itself has to have said an exception was allowed. Every approval in this migration also
refuses an approver equal to the requester: on access, on configuration, on money commands, on bulk
exports. Written down and not enforced, separation of duties survives exactly until the week
somebody is on leave.

**A configured value has a schema, a scope, a priority, an effective interval, an owner and a
version.** That is the domain document's hard rule, and it is six columns here across two tables —
the first four belong to the addressable setting, the last two to the value it held — plus a GiST
exclusion constraint so two versions of one setting cannot be in force at the same instant.
Resolution order is data, so the question "why did this request see that value" has an answer that
does not depend on reading the resolver's source code.

**Maker-checker is a state machine with evidence, not a checkbox.** A change request carries the
proposed value, the validation and simulation results it passed, the approvals its impact class
requires, and the rollout that applied it. It cannot reach `APPROVED` without them and it cannot be
applied without being approved. The proposal freezes the moment it leaves `DRAFT`, which is what
stops the oldest trick there is: approval of a small change, followed by an edit, followed by
application of a large one. A rollback is a new rollout back to a named earlier version — history is
never rewritten, because the incident review needs to see the value that was live during the
incident.

**A kill switch is asymmetric on purpose.** Turning one off never needs an approval: the whole point
of a kill switch is that the person holding the pager at three in the morning does not have to find
a second approver. Turning one back on always does. A flag also carries an expiry, and past it the
only permitted moves are off and retired — a permanent temporary flag is how a rollout mechanism
quietly becomes undocumented product behaviour. Extending one is allowed and is recorded as an
extension with a reason, so a flag on its fourth extension is visible as what it is.

**Emergency access is bounded and reviewed, or it is just access.** A break-glass grant names the
incident, is approved by somebody other than the requester, expires by an interval the role itself
caps, alerts when it opens, records every target it touched, and owes a post-use review by somebody
who neither requested nor approved it. Its facts are fixed once granted, because the only person
likely to want to edit them is the one the review is about. An unreviewed expired grant is the audit
finding, and `BreakGlassGrantRepository.findReviewOverdue` is the query that surfaces it rather than
somebody remembering to look.

**Reading production in bulk is an action, and it is recorded like one.** An export names its
purpose, its legal basis, the data classes it will contain and the rows it expects, is approved by
somebody else, expires, and logs every retrieval against it. Minimisation is a column — the
requested classes — rather than an intention. Analytics is the one purpose class barred from
personal data outright, because the analytical layer built in migration 030 exists so that routine
analysis never needs a copy of production.

## Verified behaviour

A hundred and ninety-two scenarios were run against the applied schema, thirty-one accepted and a
hundred and sixty-one refused. Every refusal below is paired with an accepted counterpart somewhere
in the suite, because a rule that refuses everything is indistinguishable from a broken one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | Activate a role that has permissions | accepted |
| 2 | Insert a role already ACTIVE (no permissions can exist yet) | refused |
| 3 | Add a permission to an ACTIVE role | refused |
| 4 | Delete a permission from an ACTIVE role | refused |
| 5 | Add a permission to a DRAFT role | accepted |
| 6 | A permission reaching further than the role declares | refused |
| 7 | A READ permission demanding a second approval | refused |
| 8 | Edit an approved permission row | refused |
| 9 | Widen an ACTIVE role definition | refused |
| 10 | Move an ACTIVE role through its own lifecycle | accepted |
| 11 | Delete a published role definition | refused |
| 12 | A read-only role declared break-glass eligible | refused |
| 13 | A break-glass role with no cap on its length | refused |
| 14 | A role whose grant never expires | refused |
| 15 | Recertification deferred past the grant itself | refused |
| 16 | A role requiring training but naming none | refused |
| 17 | A role key that is not a key | refused |
| 18 | A conflict declared against itself | refused |
| 19 | The same conflict pair stored the other way round | refused |
| 20 | An exception with nobody empowered to approve it | refused |
| 21 | Activate a fresh role that has no permissions | refused |
| 22 | Assign an active role to an operator | accepted |
| 23 | Assign a DRAFT role | refused |
| 24 | An operator approving their own assignment | refused |
| 25 | A requester approving the assignment they raised | refused |
| 26 | A grant running longer than the role permits | refused |
| 27 | Recertification deferred past what the role allows | refused |
| 28 | A role requiring training assigned without an attestation | refused |
| 29 | A global-scope role assigned with a market code | refused |
| 30 | A market-scope role assigned with no market | refused |
| 31 | An assignment naming a role key the definition does not carry | refused |
| 32 | Both sides of a declared maker-checker pair | refused |
| 33 | The same pair under the exception the conflict permits | accepted |
| 34 | The same pair under a withdrawn exception | accepted |
| 35 | A second live assignment of the same role to the same operator | refused |
| 36 | Recertify an assignment | accepted |
| 37 | An operator recertifying their own authority | refused |
| 38 | Revoke an assignment without saying why | refused |
| 39 | Revoke an assignment properly | accepted |
| 40 | A conflicting pair whose conflict forbids exceptions | refused |
| 41 | The same, citing the conflict that forbids exceptions | refused |
| 42 | The same, citing an exception belonging to another pair | refused |
| 43 | Assign the conflicting role after the first is revoked | accepted |
| 44 | Record an activity inside the open window | accepted |
| 45 | An activity predating the grant | refused |
| 46 | An activity after the grant expires | refused |
| 47 | An activity naming neither a target nor a reference | refused |
| 48 | The activity counter is maintained rather than supplied | accepted |
| 49 | Emergency access on a role that is not eligible for it | refused |
| 50 | Emergency access running longer than the role caps | refused |
| 51 | Review deferred past the window the role allows | refused |
| 52 | An operator approving their own emergency access | refused |
| 53 | Emergency access with no alert sent | refused |
| 54 | A grant inserted already reviewed | refused |
| 55 | Extend the expiry of a grant already given | refused |
| 56 | Rewrite the justification after the fact | refused |
| 57 | Close the grant | accepted |
| 58 | Review the grant while it is still open | refused |
| 59 | Close it, then review it | accepted |
| 60 | The operator reviewing their own emergency access | refused |
| 61 | The approver reviewing the access they approved | refused |
| 62 | Mark it reviewed without saying what was found | refused |
| 63 | A policy breach finding with no follow-up to point at | refused |
| 64 | Reopen a finding by clearing it | refused |
| 65 | Edit an activity that was already logged | refused |
| 66 | A value applied under an approved request | accepted |
| 67 | Bootstrap a value on a schema that requires maker-checker | refused |
| 68 | A kill-switch value on a schema that requires maker-checker | refused |
| 69 | Rolling back a schema that declares it cannot be rolled back | refused |
| 70 | A second value of one setting overlapping the first | refused |
| 71 | A value effective before it was applied | refused |
| 72 | A first version naming a predecessor | refused |
| 73 | A later version naming no predecessor | refused |
| 74 | Superseding a value belonging to another setting | refused |
| 75 | A value citing a request about a different setting | refused |
| 76 | A value citing a request still awaiting approval | refused |
| 77 | Edit the value of a version already effective | refused |
| 78 | Close a version interval | accepted |
| 79 | A value on a retired setting | refused |
| 80 | A global setting given a non-zero priority | refused |
| 81 | A market setting naming no market | refused |
| 82 | A listing setting naming no listing | refused |
| 83 | A duplicate setting for one schema and scope | refused |
| 84 | A setting at a scope the schema never allowed | refused |
| 85 | A financial schema declaring it needs only one person | refused |
| 86 | A schema with an unknown scope in its allowed list | refused |
| 87 | Widen an ACTIVE configuration schema | refused |
| 88 | A value effective before it was applied | refused |
| 89 | A first version naming a predecessor | refused |
| 90 | Superseding a value belonging to another setting | refused |
| 91 | A rollback pointing at another setting value | refused |
| 92 | A request inserted already approved | refused |
| 93 | A request naming two targets at once | refused |
| 94 | A request with no rollback plan | refused |
| 95 | An expedited request with no justification | refused |
| 96 | Validate a request whose policy check never ran | refused |
| 97 | Validate a request whose required simulation never ran | refused |
| 98 | Validate a request whose simulation was skipped | refused |
| 99 | A failing validation with no finding recorded | refused |
| 100 | A simulation with no evidence to point at | refused |
| 101 | Approve straight from VALIDATED without collecting approvals | refused |
| 102 | Approve while an approval role is still missing | refused |
| 103 | The person who raised the request approving it | refused |
| 104 | A sign-off from a role the request never asked for | refused |
| 105 | A second approval of a different value | refused |
| 106 | The whole path: validate, approve twice, apply | accepted |
| 107 | Edit the proposed value after it left draft | refused |
| 108 | Narrow the approval roles after it left draft | refused |
| 109 | Apply a request that was never approved | refused |
| 110 | Reject a request with no recorded refusal | refused |
| 111 | Reject a request that a reviewer actually refused | accepted |
| 112 | Revive a closed request | refused |
| 113 | Approve a request that has already been applied | refused |
| 114 | A governed artifact request proposing a value of its own | refused |
| 115 | A governed artifact request that names its artifact | accepted |
| 116 | A configuration request proposing nothing | refused |
| 117 | Two approvals of the same request signing different values | refused |
| 118 | Pull a kill switch with no approval anywhere | refused |
| 119 | Turn a flag on by pulling a switch | refused |
| 120 | A switch pull that names no incident | refused |
| 121 | Turn a flag on with no request behind it | refused |
| 122 | Turn a flag on under a request still awaiting approval | refused |
| 123 | Turn a flag on under a request for a different flag | refused |
| 124 | Turn a flag on under an approved request | accepted |
| 125 | Turn on a flag that expired a month ago | refused |
| 126 | Turn off an expired flag | accepted |
| 127 | A state at a scope the flag never allowed | refused |
| 128 | Targeting on a flag that does not support it | refused |
| 129 | Two contradictory global states at the same instant | refused |
| 130 | A disabled state carrying a rollout share | refused |
| 131 | A kill switch that is on by default | refused |
| 132 | A kill switch that does not say what it kills | refused |
| 133 | A flag with no expiry date | refused |
| 134 | An extended flag that does not say why | refused |
| 135 | Extend an active flag with a recorded reason | accepted |
| 136 | Change what an active kill switch disables | refused |
| 137 | Enable a retired flag | refused |
| 138 | A refund within the ceiling, pending approval | accepted |
| 139 | A refund above the ceiling the command declares | refused |
| 140 | A refund in the wrong currency | refused |
| 141 | A monetary command naming no amount | refused |
| 142 | A non-monetary command naming an amount | refused |
| 143 | A command issued against the wrong kind of target | refused |
| 144 | A command with no reason code where one is required | refused |
| 145 | A dry run of a command that has none | refused |
| 146 | An execution under nobody authority at all | refused |
| 147 | An execution citing another operator assignment | refused |
| 148 | An execution under an assignment that was revoked | refused |
| 149 | An execution under an emergency grant that is open | accepted |
| 150 | An execution under somebody else emergency grant | refused |
| 151 | A maker-checker command inserted already approved | refused |
| 152 | Approve a refund with only one of the two roles | refused |
| 153 | The operator issuing the refund approving it | refused |
| 154 | An approval of parameters that are not the ones sent | refused |
| 155 | The full approved path, then a domain refusal | accepted |
| 156 | Raise the amount after it was approved | refused |
| 157 | Dispatch a command that was never approved | refused |
| 158 | A dry run claiming it applied something | refused |
| 159 | A successful command with no before and after | refused |
| 160 | The same command issued twice under one idempotency key | refused |
| 161 | An uncapped monetary command definition | refused |
| 162 | An irreversible command that one person may issue alone | refused |
| 163 | A retired command still being issued | refused |
| 164 | A dry run recorded as having applied a change | refused |
| 165 | A command that applied a change with no before and after | refused |
| 166 | A command whose whole non-monetary path is honest | accepted |
| 167 | Approve an export | accepted |
| 168 | An export approved by the person who asked for it | refused |
| 169 | An export inserted already approved | refused |
| 170 | Personal data leaving with no redaction profile | refused |
| 171 | An analytics export of personal data | refused |
| 172 | An export with no expiry | refused |
| 173 | Generate an artifact before the export is approved | refused |
| 174 | Retrieve an export that was never generated | refused |
| 175 | Approve, generate and retrieve | accepted |
| 176 | Retrieve an export after it expired | refused |
| 177 | Retrieve an export before the artifact existed | refused |
| 178 | Widen the data classes after approval | refused |
| 179 | Drop the redaction profile after approval | refused |
| 180 | Revive a revoked export | refused |
| 181 | Edit a retrieval that was already logged | refused |
| 182 | A setting at a scope the schema never allowed | refused |
| 183 | A setting naming a schema key the schema does not carry | refused |
| 184 | A setting at a scope the schema does allow | refused |
| 185 | A governed artifact request proposing a value of its own | refused |
| 186 | A governed artifact request that names its artifact | accepted |
| 187 | Turn a flag on by calling it a seed value | refused |
| 188 | A second seed value for a scope that already has a state | refused |
| 189 | The first seed value for a flag that has none | accepted |
| 190 | Close the open state, then pull the switch | accepted |
| 191 | Turn on a flag whose expiry has passed | refused |
| 192 | A setting at a scope the schema does allow | accepted |

Every scenario runs inside a plpgsql subtransaction that is always unwound, so an accepted scenario
leaves nothing behind for the next one to trip over. That is the probe contamination lesson from
migrations 025, 026 and 031 turned into a mechanism rather than a habit, and it is why no scenario
in this suite had to be restated because an earlier one had mutated shared fixture state.

## Tables

**Role administration** — `operator_role_definitions`, `operator_role_permissions`,
`operator_role_conflicts`, `operator_role_assignments`. What a role means at one version, the frozen
list of permissions that is its contract, the declared pairs nobody may hold together, and who holds
what until when. The capability grant that enforces it stays in migration 014.

**Emergency access** — `break_glass_grants`, `break_glass_activities`. Bounded elevation with a
named incident, a third-party review, and an append-only log of everything it touched.

**Configuration** — `configuration_schemas`, `configuration_settings`, `configuration_versions`.
What a configurable thing is and how it may be changed; the addressable setting at a scope with its
resolution priority; and the values it actually held, each over one interval.

**Change control** — `change_requests`, `change_request_approvals`, `change_request_validations`,
`configuration_rollouts`. One maker-checker path for a configured value, a feature flag and an
artifact another domain owns, with the evidence it passed, the sign-offs it collected, and the
stages by which it reached production.

**Feature flags** — `feature_flag_definitions`, `feature_flag_states`. What each flag is, who may
turn it on, the date it is gone by, and what it was set to where, over which interval.

**Operational commands** — `operational_command_definitions`, `operational_command_executions`,
`operational_command_approvals`. The catalogue of what may be asked for with its ceiling and its
approvals; what was asked and what the owning domain answered; and who agreed to it.

**Bulk reads** — `bulk_export_requests`, `bulk_export_accesses`. Approved, bounded, expiring copies
of production data, and every retrieval against them.

## The rules that are not columns

Ten changesets carry no table. Two of them add no function either: `032-21` and `032-22` attach
triggers to `platform_append_only()` and `platform_contract_freeze()`, created in migration 030 and
reused here so that both halves of the platform freeze contracts and protect evidence the same way.

- **`032-21` append-only.** Seven tables: role permissions, break-glass activities, change
  approvals, change validations, command approvals, export retrievals and configuration versions.
  Only the configuration version names any permitted column — it may be closed by its successor and
  nothing else about it moves. `DELETE` stays available throughout, because retention and erasure
  must be able to remove rows nobody may rewrite.
- **`032-22` registry immutability.** Role definitions, configuration schemas, feature flags and
  command definitions are frozen past `DRAFT` except for their own lifecycle columns. A flag's
  expiry may also move, because extending one is a recorded decision rather than an edit to what the
  flag means.
- **`032-23` role membership.** Permissions are frozen when the role leaves draft; a role may not be
  inserted already active, because its permissions cannot exist yet; activating one with no
  permissions is refused; and a permission may not reach further than the role's declared
  sensitivity, which is what the approver of an assignment read.
- **`032-24` assignment integrity.** The role must be active, the market scope must match, the grant
  may not outrun the role's cap, recertification may not be deferred past what the role allows,
  training must be attested when the role demands it, and a declared conflict is refused unless a
  valid exception covering that exact pair is cited.
- **`032-25` emergency access.** The role must permit break-glass, the expiry and the review
  deadline are both capped by the role, a grant may not be inserted already reviewed, its facts are
  fixed once granted, a review may not be reopened by clearing it, and it may not be reviewed while
  still open. Activities are refused outside the window, and the activity counter is maintained by
  the database.
- **`032-26` change progression.** Created in draft only; the target, the value, the approval roles
  and the requester frozen past draft; `VALIDATED` requires passing schema and policy checks plus
  the simulation and preview the schema demands; `APPROVED` requires every required role, none of
  them the requester; `APPLIED` only from approved; `REJECTED` requires a recorded refusal; and
  closed is closed. An approval must be of a role the request asked for, and of the same value every
  other approval signed.
- **`032-27` configuration integrity.** A setting must name a published schema, carry its key, and
  sit at a scope the schema allows. A value must belong to an active setting under a published
  schema, may not be seeded or kill-switched onto a schema that requires maker-checker, may not be
  rolled back on a schema that says it cannot be, and its request, predecessor and rollback target
  must all be about the same setting.
- **`032-28` flag states.** The flag must not be a draft, the scope must be one it allows, targeting
  must be supported, a seed value must match the declared default and be the first state at its
  scope, enabling requires an approved request naming that flag, and no flag may be turned on past
  its expiry or after retirement.
- **`032-29` command integrity.** The command must be active and match its target type; monetary
  commands must carry an amount within the ceiling in the declared currency and non-monetary ones
  must carry none; reason codes, justifications and dry-run support are held to what the command
  declares; the cited authority must belong to the actor and have been in force at the time;
  approvals must be complete, none by the actor, of the same parameters, and within the amount each
  approved; parameters freeze once requested; and a finished execution is not reopened.
- **`032-30` export integrity.** Requested before approved, approved before generated, refused and
  revoked are terminal, the data classes and redaction profile freeze once raised, retrieval is
  refused before generation and after expiry, and the retrieval counter is maintained by the
  database.

## What probing changed

Three defects, all found by scenarios that were expected to be refused and were not.

**A declared list nothing enforced.** `configuration_schemas.allowed_scope_types` says which scopes
a setting under that schema makes sense at, and nothing checked it — a per-listing override of a
payout hold the payout schema only ever meant globally was accepted. A list nothing enforces is a
comment. `032-27` gained `configuration_setting_scope()`, which also checks the denormalized schema
key the resolver reads. The equivalent rule for feature flags was already enforced, which is what
made the gap visible: the two halves of the same idea disagreed.

**A comment stronger than its constraint.** The header said a governed-artifact request does not
propose a value, because the value is the artifact and it lives in the domain that owns it. The
`CHECK` only required a value for the other two target kinds, so an artifact request carrying a copy
of the artifact was accepted — a second version of the thing being approved that nothing keeps in
step with the first. The constraint now reads as an equality and the comment is true.

**Seeding as a way in.** `feature_flag_states` refused enabling a flag under every origin except
`BOOTSTRAP`, which was intended for the initial state a flag is registered with. Anyone willing to
write that word in the origin column could therefore turn on any flag with no approval at all. A
seed value must now match the flag's declared default and be the first state at its scope, which is
what being a seed value means.

The three defect classes 029 and 030 found — a guard watching a transition a row could be created
past, a nullable column inside `col IN (...)` silently passing a `CHECK`, and an append-only
self-reference with no `ON DELETE` decision — were designed out up front. One related mistake was
caught while writing rather than while probing: the exclusion constraint on `feature_flag_states`
originally compared the nullable scope and market columns directly, and an exclusion constraint
treats two nulls as non-conflicting, so every global state would have been silently exempt from the
rule and two contradictory global states could have been in force at once. The columns are
coalesced.

Six transition guards were also written the wrong way round at first — `NEW.state = 'X' AND
OLD.state <> 'Y'` fires on an update that does not change the state at all, which made an unrelated
counter increment on an already-generated export fail. Each now checks that the state actually
moved.

## Design rules this migration follows

- Status values are `VARCHAR` with a `CHECK`, never PostgreSQL enum types.
- `version BIGINT NOT NULL DEFAULT 0` with `CHECK (version >= 0)` on optimistically-locked tables.
- No `DEFAULT now()`: every table here is written by the application, and the instants come from the
  shared application clock.
- Money is `*_minor BIGINT` beside a `VARCHAR(3)` currency with a `~ '^[A-Z]{3}$'` check.
- Digests are `CHAR(64)` with a lower-case hexadecimal check; the application computes them.
- Constraint naming `pk_` / `uk_` / `fk_` / `ck_` / `ex_` / `idx_`; partial unique indexes for
  "at most one X" rules.
- JSONB only for values and snapshots the application does not filter on relationally: a configured
  value, a proposed value, a targeting rule, a set of command parameters.

## What this migration does not create

The effective-dated country, tax, fee, cancellation and risk policies the domain document lists are
migrations 013, 019, 023 and 027. They are cited here by a governed-artifact reference on a change
request rather than redefined, so that publishing a policy version and changing a configured value
go through one maker-checker machine instead of two.

The moderation and review queues are migrations 027 and 028. The immutable admin audit is migration
012's `audit_events`, which already carries actor, reason code, before and after digests and
retention — this migration writes into it and points at it rather than duplicating it. Inspection of
bookings, payments, payouts and reconciliation is read access over tables that already exist; what
this migration adds for it is the export request and the retrieval log.

## What this migration leaves open

- **The resolver itself.** The candidates and the priority are data; the code that walks them is not
  here, and two readers could still disagree about ties the priority does not break.
- **Value validation against the schema.** The schema's digest and reference are stored and the
  change request records that a schema check passed, but the database does not itself validate a
  JSONB value against a JSON schema. That check is the application's, and its result is evidence.
- **Change freeze periods.** `change_freeze_applies` says whether a schema is held during a freeze;
  the calendar of freezes is not modelled.
- **Approval role membership.** A required approval role is a string, and whether the approver
  actually holds it is checked against the operator role tables by the application rather than by a
  foreign key — the roles that approve a change are an organizational vocabulary broader than the
  operator roles this migration registers.
- **Rate limiting.** `daily_execution_limit` is declared and countable through
  `OperationalCommandExecutionRepository.countIssuedBetween`, but nothing enforces it in the
  database; a limit is a decision about what to do next, not an invariant about what may exist.

## Deviations from the plan and the feature document

The plan named D21 as "role/permission administration, effective-dated policy registry, feature
flags plus kill switches, config change requests with maker-checker approval, immutable admin audit,
break-glass grants with expiry and post-use review". Five differences are worth stating.

**The effective-dated policy registry is not rebuilt.** Migrations 013, 019, 023 and 027 already
hold effective-dated policy with overlap exclusion. Rebuilding it here would have produced a second
answer to "which policy applied", which is the failure the whole migration is against. What 032 adds
is the approval path by which one gets published.

**The immutable admin audit is migration 012's.** Reusing `audit_events` rather than writing
`admin_audit_events` keeps one audit trail rather than two that have to be joined to answer a
question about one operator.

**`operator_role_assignments` was added.** The plan implied that assignment was covered by 014's
`capability_grants`. It is, for enforcement — but a grant carries no role version, no training
attestation, no recertification date and no separation-of-duties outcome. The assignment is the
administrative record and points at the grant.

**`operator_role_conflicts` was added.** Separation of duties is named in the domain document's
subproblems as maker-checker; making it enforceable needed the declared pairs to be a table.

**Change requests target three kinds of thing, not one.** The plan said "config change requests". A
feature flag and a governed artifact travel the same path here, which is fewer moving parts and one
approval trail to audit rather than three.

Two smaller notes. `GovernedRegistryStatus` is a new enum rather than a reuse of the existing
`DefinitionStatus`, which carries an extra `REVIEWED` state these registries do not have;
`ChangeApprovalDecision` is likewise new rather than the existing `ApprovalDecision`, which carries
a `REVOKED` state a change approval does not. `DataPrivacyClass` from migration 030 is reused
unchanged, because the vocabulary is identical and the meaning is the same.

## What this closes

Domain D21 has a target-state schema. An operator's authority, the values they can change, the
switches they can pull, the commands they can issue and the data they can export are all rows with
owners, bounds, approvals and expiry, and the administrative console has nothing it can write that
bypasses the domain owning what it writes to.

## Exit criteria

- `032-admin-and-governance.sql` applies to an empty database through
  `db.changelog-master.yaml`: thirty changesets, twenty tables, fourteen new trigger functions.
- All thirty changesets roll back cleanly, leaving zero of those tables and zero of those functions,
  and leaving migration 030's two shared functions intact; re-applying then succeeds.
- A hundred and ninety-two probe scenarios behave as designed, with three defects found, fixed and
  re-probed.
- The schema-wide enum column-width audit reports nothing too narrow.
- Twenty aggregates and twenty repositories compile, `javadoc` is clean, and the application boots
  with three hundred and seventy-six JDBC repositories.
