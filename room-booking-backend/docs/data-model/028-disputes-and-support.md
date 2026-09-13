# Migration 028 — Disputes, damage claims and support

## Goal

An allegation, a finding, a decision and a movement of money are four different rows. This migration
exists so that a support case can coordinate all four without becoming the authority for any of them:
what a person said, what an authorized reviewer concluded from evidence, what policy version
permitted which remedy, and which domain command actually moved the money stay separable.

Collapsing them is how a support desk starts paying claims it cannot explain and refusing claims it
cannot defend — and, later, how it discovers it has been an unbudgeted payments system for a year.

Forty-six tables, fifty-six changesets. Ten of those changesets contain no table at all: they carry
the rules no column can express.

## Eight forces shaping it

**A case coordinates authoritative facts; it never owns them.** Every neighbouring fact this domain
reads is recorded with its source domain, source identifier, source version and observed instant, so
a stale copy can be recognised as stale rather than mistaken for authority. No row here writes
inventory, money, publication or capability. A remedy instruction is a request with a stable identity,
and the receiving domain's answer arrives as a separate outcome on the instruction row.

**Allegation, assertion, finding and decision form a one-way ladder.** An intake preserves what was
reported in the reporter's own framing; `case_assertions` attributes a statement to the party who made
it; `case_findings` is an authorized interpretation that must cite evidence and name its proof
standard; `case_decisions` selects a policy version and consumes findings. An `INCONCLUSIVE` finding
may not be cited as the determinative ground of a decision, because "we could not tell" and "we found
against you" are opposite answers and only one of them is appealable on its merits.

**Every monetary effect names a funder and a ceiling that was checked.** A remedy line carries
beneficiary, funder class, amount in integer minor units, currency, decision basis and either a
reservation held against a source ceiling or a budget window it consumed. A host may fund only what a
host can economically control, so host-funded lines are refused on tax and platform-fee sources. Line
sums must agree with the remedy total in the remedy's own currency, and the catalogue entry decides
which funders and which currencies are permitted at all.

**Approval binds an exact digest, and the maker is not the checker.** An approval names the decision
digest it saw, not merely the decision; a trigger refuses an approval whose digest the decision does
not currently carry, and refuses an approval naming a maker who did not make the decision.
Self-approval is refused by constraint, one effective approval exists per decision and approval role,
and a decision may not become effective while a refusal or a stale approval still stands against it.

**Closure never hides unresolved work.** A case cannot reach `CLOSED` while a remedy instruction is
still in flight, a remedy is unfinished, or an external claim submission has an open deadline —
unless an exception owner and a next deadline are named. An open appeal blocks closure outright,
because a participant owed an answer must not be shown a closed case instead of one. Safety screening
must have finished or been handed off, and no exception may buy past that.

**An unknown external outcome is a state, not a failure.** Submission intent and a stable provider key
are persisted before the external call, the key is unique per provider account, observations are
append-only, and an observation that would move a provider claim backwards is refused as a stale
regression while still being kept as evidence about the provider. A retry reuses the key; it never
invents a second identity to make a screen move.

**Evidence is immutable and custody is additive.** Originals, transformations, redactions, reads and
disclosure manifests are append-only. A redaction produces a derivative that names its original; it
never destroys the protected bytes. An item under legal hold cannot enter a deletion state, and the
hold's scope does not widen anyone's read access. A digest proves the platform holds these exact
bytes; it proves nothing about whether the depicted event happened.

**Every temporary control is bounded.** SLA clocks, offers, evidence deadlines, provider deadlines,
work-item leases, reservations and break-glass authority each carry an expiry or a next review
instant. An open case is not, by itself, permission to hold a host's funds forever.

## Verified behaviour

Two hundred and sixty-four scenarios were run against the applied schema, seventy-two accepted and a
hundred and ninety-two refused. Every refusal below is paired with an accepted counterpart somewhere
in the suite, because a rule that refuses everything is indistinguishable from a broken one.

| # | Scenario | Result |
| --- | --- | --- |
| 1 | Editing what a published policy means | refused |
| 2 | Closing a published policy's effective window | accepted |
| 3 | Extending that closed window | refused |
| 4 | Reopening it entirely | refused |
| 5 | Retiring a published policy | accepted |
| 6 | Deleting a published policy | refused |
| 7 | A policy suppressing appeal with no external complaint route | refused |
| 8 | The same policy naming where a complaint goes instead | accepted |
| 9 | Publishing a rule set nobody validated | refused |
| 10 | A monetary catalogue entry with no formula and no ceiling | refused |
| 11 | A non-monetary catalogue entry carrying an amount | refused |
| 12 | A catalogue entry with a lower-case currency code | refused |
| 13 | A catalogue entry naming a funder nobody recognises | refused |
| 14 | A monetary catalogue entry naming no downstream owner | refused |
| 15 | An insurance distributor adjudicating its own claims | refused |
| 16 | The same role routing adjudication to the carrier | accepted |
| 17 | A deductible larger than the cover | refused |
| 18 | Break-glass with no named actions and no expiry | refused |
| 19 | Break-glass with actions, step-up, expiry and review | accepted |
| 20 | An investigation template that can conclude nothing | refused |
| 21 | A safety queue that cannot page anybody | refused |
| 22 | A queue that overflows into itself | refused |
| 23 | Granting yourself authority | refused |
| 24 | A second live grant of one skill in one scope | refused |
| 25 | Revoking a grant without saying why | refused |
| 26 | Claiming model ordering without naming a model | refused |
| 27 | An anonymous report that also names its reporter | refused |
| 28 | An anonymous safety report with no reporter | accepted |
| 29 | An unauthenticated request for money | refused |
| 30 | A safety report filed as informational | refused |
| 31 | A severity label whose rank says something else | refused |
| 32 | A case triaged below the floor its intake answers established | refused |
| 33 | The same intake command submitted twice | refused |
| 34 | A case marked duplicate of nothing | refused |
| 35 | A case that is a duplicate of itself | refused |
| 36 | A closure exception owner with no date anybody must look again | refused |
| 37 | A participant who is both one of ours and somebody else's | refused |
| 38 | A representative acting on consent with no reference and no expiry | refused |
| 39 | A guest given the internal view of the case | refused |
| 40 | The same guest as an ordinary participant | accepted |
| 41 | A second live participation in the same role | refused |
| 42 | A case linked to itself | refused |
| 43 | A support-domain link that names no case | refused |
| 44 | A merge declared by an agent nobody named | refused |
| 45 | A typed link to a payment dispute | accepted |
| 46 | A reclassification that changes nothing | refused |
| 47 | An agent reclassification with no agent | refused |
| 48 | A reclassification keeping its prior value and routing consequence | accepted |
| 49 | Editing that reclassification afterwards | refused |
| 50 | A transition from a state to itself | refused |
| 51 | A closure transition with nothing a participant can be shown | refused |
| 52 | An ordinary transition | accepted |
| 53 | The same transition command replayed | refused |
| 54 | Editing a recorded transition | refused |
| 55 | An outbound message with no approved template behind it | refused |
| 56 | An inbound contact, which needs none | accepted |
| 57 | A recorded call that does not say whether consent was captured | refused |
| 58 | Editing a recorded contact | refused |
| 59 | An internal note | accepted |
| 60 | Editing that note afterwards | refused |
| 61 | An acknowledgement clock that pauses | refused |
| 62 | A next-action clock pausing for claimant evidence | accepted |
| 63 | A second live clock of the same type | refused |
| 64 | A clock due before it started | refused |
| 65 | A claimed work item with nobody holding it | refused |
| 66 | Safety-critical work ordered by a model | refused |
| 67 | An ordinary work item | accepted |
| 68 | A second open item of the same type on one episode | refused |
| 69 | A first lease on that item | accepted |
| 70 | A second live lease on the same item | refused |
| 71 | Releasing the first lease | accepted |
| 72 | Reissuing a token that was already used | refused |
| 73 | The next worker taking a higher token | accepted |
| 74 | The released worker renewing its lease | refused |
| 75 | The stale worker completing the work anyway | refused |
| 76 | The current lease holder completing it | accepted |
| 77 | A photograph claiming to be contemporaneous with no capture metadata | refused |
| 78 | The same photograph with its capture metadata | accepted |
| 79 | A provenance rank that flatters its class | refused |
| 80 | An upload with no digest of its bytes | refused |
| 81 | A projected domain fact that does not say which version it read | refused |
| 82 | The same fact naming the version it was read at | accepted |
| 83 | An unscanned upload made available | refused |
| 84 | The same bytes submitted twice to one case | refused |
| 85 | Swapping the bytes underneath an item a decision may cite | refused |
| 86 | Narrowing who may see it | accepted |
| 87 | Deleting an evidence row outright | refused |
| 88 | A legal hold with no reference to the hold | refused |
| 89 | A proper legal hold | accepted |
| 90 | The retention worker deleting it anyway | refused |
| 91 | A derivative of itself | refused |
| 92 | A redacted derivative with full lineage | accepted |
| 93 | Editing that lineage afterwards | refused |
| 94 | A redaction that hides nothing | refused |
| 95 | A redaction reviewed by the person who made it | refused |
| 96 | A redaction naming the region it removed | accepted |
| 97 | Somebody else reviewing it, once | accepted |
| 98 | Changing that review afterwards | refused |
| 99 | An export granted with nobody having approved it | refused |
| 100 | Break-glass with no review to follow | refused |
| 101 | An ordinary purpose-bound read | accepted |
| 102 | Editing the access record afterwards | refused |
| 103 | A manifest whose count does not match what it carries | refused |
| 104 | A frozen manifest nobody approved | refused |
| 105 | An approved, frozen manifest | accepted |
| 106 | Slipping another artifact into it after freezing | refused |
| 107 | Disclosing it, which is a lifecycle move | accepted |
| 108 | An assertion attributed to two different people at once | refused |
| 109 | What the host says happened | accepted |
| 110 | Rewriting what they said | refused |
| 111 | Withdrawing it | accepted |
| 112 | Withdrawing it a second time | refused |
| 113 | A finding that concludes against somebody citing nothing | refused |
| 114 | An inconclusive finding that names no gap | refused |
| 115 | A supported finding citing the photograph | accepted |
| 116 | An inconclusive finding on responsibility, naming its gap | accepted |
| 117 | The author reviewing their own finding | refused |
| 118 | A second live answer to a question already answered | refused |
| 119 | A claim asking for nothing | refused |
| 120 | A claim against itself | refused |
| 121 | A late claim that does not say why it was late | refused |
| 122 | An eligible claim under adjudication | accepted |
| 123 | The same loss claimed twice | refused |
| 124 | An item whose accepted and rejected halves do not add up | refused |
| 125 | An item reduced without saying why | refused |
| 126 | An invoice-standard line paid on the strength of an estimate | refused |
| 127 | An accepted amount above the category cap it was measured against | refused |
| 128 | One valued item and one still open | accepted |
| 129 | Deciding the claim while an item has no answer | refused |
| 129c | Deciding it to a total its items do not support | refused |
| 130b | Deciding it with responsibility still undetermined | refused |
| 131c | Deciding it with the items adding up | accepted |
| 132 | A decided claim with no decision record behind it | refused |
| 133 | Accepting more than was ever claimed | refused |
| 134 | An offer sent with no expiry | refused |
| 135 | A draft offer with its lines | accepted |
| 136 | A line where one party funds a payment to itself | refused |
| 137 | Sending the offer | accepted |
| 138 | Adding a line after it was sent | refused |
| 139 | Raising the amount after it was sent | refused |
| 140 | Accepting a digest that is not the one on the offer | refused |
| 141 | Somebody who was not the recipient accepting it | refused |
| 142 | Acceptance with no authentication behind it | refused |
| 143 | A real acceptance | accepted |
| 144 | Withdrawing it after acceptance | refused |
| 145 | A second accepted offer on the same case | refused |
| 146b | Goodwill booked against the contract rung | refused |
| 147b | A refusal with no reason codes | refused |
| 148 | An appealable decision that says nothing about how to appeal | refused |
| 149 | An exceptional manual decision with no approved exception code | refused |
| 150 | A well-formed draft decision | accepted |
| 151 | An approval naming a maker who did not make it | refused |
| 152 | The maker approving their own decision | refused |
| 153 | An approval of a digest the decision does not carry | refused |
| 154 | An approver refusing | accepted |
| 155 | Making the decision effective over that refusal | refused |
| 156 | Editing a recorded approval | refused |
| 157 | Invalidating the refusal, which is permitted once | accepted |
| 158 | Invalidating it again | refused |
| 159 | A genuine independent approval | accepted |
| 160 | A second live approval in the same role | refused |
| 161 | The decision becoming effective on that approval | accepted |
| 162 | Changing the outcome of an effective decision | refused |
| 163 | Recording that it was communicated | accepted |
| 164 | Two effective decisions of one kind on one episode | refused |
| 165 | Citing an inconclusive finding as the determinative ground | refused |
| 166 | Citing the supported finding instead | accepted |
| 167 | Keeping the inconclusive one as context | accepted |
| 168 | A decision inserted straight into effective with the conflict checks never run | refused |
| 169 | Setting an expiry on an effective decision | accepted |
| 170 | Bringing that expiry forward | accepted |
| 171 | Pushing it back out again | refused |
| 172 | Replacing an effective decision, both rows in one transaction | accepted |
| 173 | Naming a successor that never arrives | refused |
| 174 | A monetary remedy with no amount | refused |
| 175 | A draft refund remedy | accepted |
| 176c | A host funding a tax line | refused |
| 177c | A host funding the platform's own fee | refused |
| 178b | A host drawing on the goodwill budget | refused |
| 179 | A funder the catalogue entry does not permit | refused |
| 180 | A currency the catalogue entry does not permit | refused |
| 181b | A line measured against no ceiling at all | refused |
| 182 | Reserving more of a source line than the source line has | refused |
| 183 | A reservation that expires before it is held | refused |
| 184 | A reservation held against a source ceiling | accepted |
| 185b | A guest-funded settlement line held against it | accepted |
| 186b | Approving a remedy whose lines do not add up | refused |
| 187b | Approving one whose lines do | accepted |
| 188 | An instruction carrying money with no approval behind it | refused |
| 188a | Instructing a remedy nobody has approved | refused |
| 189 | A properly authorized instruction | accepted |
| 190 | The same command identity used twice | refused |
| 191 | A retry inventing a second identity for the same key | refused |
| 192 | An undispatched instruction already claiming an answer | refused |
| 193 | Dispatching it | accepted |
| 194 | Changing the amount after it left | refused |
| 195 | Claiming success with nothing to reconcile against | refused |
| 196 | Recording the domain's own answer | accepted |
| 197 | A budget window spending more than it holds | refused |
| 198 | A window that escalates but names nowhere to escalate to | refused |
| 199 | A proper budget window | accepted |
| 200 | A second window for the same scope and period | refused |
| 201 | Recording what a line took from the window | accepted |
| 202 | The same consumption replayed | refused |
| 203 | Editing a recorded consumption | refused |
| 204 | Closing the case while an instruction is still in flight | refused |
| 205 | An appeal against the effective decision | accepted |
| 206 | Closing the case while that appeal is open, even with an exception owner | refused |
| 207 | The original decider hearing the appeal | refused |
| 208 | The appellant hearing their own appeal | refused |
| 209 | An independent reviewer taking it | accepted |
| 210 | Reversing the decision without issuing a new one | refused |
| 211 | Affirming it, which needs none | accepted |
| 212 | A second live appeal on one decision | refused |
| 213 | Closing with a named exception owner and deadline | accepted |
| 214 | Reopening without starting a new episode | refused |
| 215 | Reopening while erasing the closure | refused |
| 216 | Reopening properly, as a new episode | accepted |
| 217 | A case written straight into closed while safety is still screening | refused |
| 218 | Lowering a case's severity without saying why | refused |
| 219 | De-escalating safety-critical work mid-escalation | refused |
| 220 | An agent reviewing the quality of their own work | refused |
| 221 | A satisfactory review that nonetheless demands a correction | refused |
| 222 | A real quality review | accepted |
| 223 | Sampling the same decision twice under the same cohort | refused |
| 224 | Denying cover without citing an exclusion | refused |
| 225 | A determination that cites nothing and offers no way to contest it | refused |
| 226 | A proper coverage snapshot | accepted |
| 227 | A second set of terms claiming to have applied | refused |
| 228 | An external claim with a stable provider key | accepted |
| 229 | A retry opening a second claim under the same key | refused |
| 230 | An unknown outcome with nobody scheduled to ask again | refused |
| 231 | The same unknown with a query scheduled | accepted |
| 232 | Re-keying the claim after it went out | refused |
| 234 | A submission dispatched before the intent was recorded | refused |
| 235 | The submission, intent first | accepted |
| 236 | The same idempotency key reused on that claim | refused |
| 237 | Editing the manifest a submission already carried | refused |
| 238 | Recording the provider's answer against it | accepted |
| 239 | The provider approving the claim | accepted |
| 240 | The same provider event delivered twice | refused |
| 241 | A late message that would move the claim backwards | refused |
| 242 | The same late message kept as evidence rather than applied | accepted |
| 243 | Marking the claim reconciled with no ledger entry behind it | refused |
| 244 | A representment in flight with no frozen manifest | refused |
| 245 | A strategy chosen by nobody | refused |
| 246 | A model-drafted narrative submitted with nobody having read it | refused |
| 247 | The same submission with a human having signed it | accepted |
| 248 | A second strategy for one provider dispute | refused |
| 249 | Closing with an exception owner over an open provider claim | accepted |
| 250 | Raising an approved remedy's amount after the approval was given | refused |
| 251 | Rejecting a remedy whose instruction already succeeded | refused |
| 252 | Re-pointing an approved remedy at another catalogue entry | refused |

Scenario 233 — re-basing a submitted claim onto different coverage terms — was refused by the
not-null constraint on the coverage reference rather than by the freeze trigger it was aimed at,
because the fixture deliberately holds only one snapshot. The trigger's other branch is exercised by
scenario 232.

## Tables

**Registries** — `support_policy_versions`, `investigation_template_versions`,
`remedy_catalog_versions`, `authority_policy_versions`, `routing_policy_versions`,
`protection_program_versions`. Effective-dated and frozen once published.

**Case core** — `support_cases`, `case_participants`, `case_relationships`,
`case_classification_history`, `case_transitions`, `case_contacts`, `case_notes`,
`case_timeline_entries`.

**Work and deadlines** — `support_queues`, `agent_skill_grants`, `case_work_items`,
`work_item_leases`, `case_sla_clocks`.

**Evidence and custody** — `case_evidence_items`, `evidence_transformations`, `evidence_redactions`,
`evidence_access_log`, `evidence_disclosure_manifests`.

**Interpretation** — `case_assertions`, `case_findings`.

**Claims and negotiation** — `damage_claims`, `damage_claim_items`, `case_offers`,
`case_offer_lines`.

**Providers** — `coverage_snapshots`, `external_claims`, `external_claim_submissions`,
`external_claim_observations`, `payment_dispute_strategies`.

**Decision and remedy** — `case_decisions`, `case_decision_findings`, `case_decision_approvals`,
`case_remedies`, `case_remedy_lines`, `remedy_reservations`, `remedy_budget_windows`,
`remedy_budget_consumptions`, `remedy_instructions`.

**Review** — `case_appeals`, `case_quality_reviews`.

## The rules that are not columns

Ten changesets carry no table:

- **`028-47` append-only.** One function serves ten tables: transitions, classification history,
  contacts, notes, timeline entries, transformations, access log, decision citations, provider
  observations and budget consumptions. A correction is a new row naming the one it corrects.
- **`028-48` registry immutability.** A published registry version is frozen except for its own
  lifecycle, and a closed effective window may never be reopened or moved later. The function compares
  the two row versions as JSON with the lifecycle columns removed, so one function serves six tables
  whose remaining columns have nothing in common.
- **`028-49` evidence custody.** Evidence identity — bytes, provenance, capture and receipt — is
  frozen while its lifecycle stays open; an erased artifact cannot return to available; an assertion
  may be withdrawn once and nothing else; a redaction's review may be filled once, by somebody who is
  not the operator; a frozen manifest keeps exactly the artifacts it was frozen with.
- **`028-50` decision integrity.** An effective decision is frozen except for supersession,
  communication and an expiry that may only move earlier. An approval must name the real maker and the
  digest the decision currently carries. A decision may not become effective — on insert or on update
  — while a refusal or an approval of a different digest still stands.
- **`028-51` offer lifecycle.** A sent offer's terms are frozen, its lines cannot be added to, a
  terminal state is terminal, and acceptance is only available from sent or viewed.
- **`028-52` remedy fidelity.** Deferred constraint triggers check that lines sum to the remedy total
  in one currency; the catalogue entry decides the permitted funder and currency; an instruction may
  not exist before its remedy is approved, and its identity is frozen once dispatched.
- **`028-53` claim and provider saga.** Deferred triggers check that accepted item amounts sum to the
  claim total and that no item is left unpriced; a provider observation claiming to be applied behind
  the claim's current sequence is refused; a submitted claim cannot be re-keyed or re-based; a
  submission is frozen except for its outcome.
- **`028-54` closure predicate.** Closure is refused over unfinished remedies, in-flight instructions
  and open provider claims unless an exception owner and deadline are named; over an open appeal
  unconditionally; and over unfinished safety screening unconditionally. Reopening must advance the
  episode and may not erase the closure. Severity may be lowered only with a reason, and never while
  a safety-critical case's route is still open.
- **`028-55` work fencing.** Lease tokens are monotonic per work item, a released lease is history,
  and a work item cannot be completed by somebody who is not its current lease holder.
- **`028-56` remedy approval immutability.** An approved remedy's identity and money are frozen
  against the digest that was approved; rejection is a pre-approval outcome; and its lines cannot be
  added to, changed or removed once it leaves the proposal states.

## What probing changed

**A real defect: an approved remedy was still editable.** The digest column recorded what had been
approved, but nothing stopped the row from becoming something else afterwards. Probing doubled an
approved remedy's amount, re-pointed it at a different catalogue entry and marked it rejected — all
after its instruction had already succeeded and the money had moved — with the original approval
digest sitting unchanged beside it. Changeset `028-56` was added in response, and covers the lines as
well as the header, because a line appended to an approved remedy changes what was authorized without
changing the remedy row at all. That is the same shape of defect migration 022 found on ledger
postings and migration 027 found again on risk evidence.

**Supersession has an order, and only one of the two works.** Both lineages that pair a "one live row"
partial index with a self-referencing successor pointer — findings and decisions — need their foreign
key deferred, as migration 027 found. The deferred key solves the reference but not the index: a
partial unique index is checked immediately, so the replacement must be written *after* the outgoing
row leaves the live state, never before. Trying it the other way round is refused, and there is no
order in which both rows can be live at once.

**Four columns were narrower than their own literals.** The multi-column enum-width audit caught
`case_evidence_items.provenance_class`, `case_findings.author_actor_type`,
`case_quality_reviews.overall_outcome` and `case_relationships.relation_type`. Widened before the
first clean apply; the audit now returns nothing across migrations 012 to 028.

**Two vocabulary columns had no constraint.** `case_participants.contact_preference` mirrors a
constrained column on `support_cases` but was unconstrained, and `investigation_template_versions.case_type`
could have named a case type that cannot exist, which is configuration nothing would ever reach and
nothing would ever report. Both gained a `CHECK` before the final apply.

## Design rules this migration follows

- Money is `*_minor BIGINT` plus a `VARCHAR(3)` currency matching `^[A-Z]{3}$`. Never floating point.
- Status values are `VARCHAR` with a `CHECK`, never PostgreSQL enum types.
- `version BIGINT NOT NULL DEFAULT 0` with `CHECK (version >= 0)` on every optimistically locked table.
- No `DEFAULT now()` on any column the application writes, and no `now()` in a partial index predicate.
- Partial unique indexes for "at most one live X", with `NULLS NOT DISTINCT` wherever a nullable
  column is part of the identity.
- `FOR UPDATE SKIP LOCKED` for every worker claim; leases carry an owner, an expiry and a fencing
  token.
- Constraint naming `pk_` / `uk_` / `fk_` / `ck_` / `idx_`.
- Controlled-vocabulary arrays are `TEXT[]` checked with `<@`, matching migration 027.

## What this migration leaves open

- **Reason codes are not constrained in the database.** Rejection, adjustment, escalation and
  exception reasons are `VARCHAR` with no `CHECK`, because the approved code list is policy
  configuration that changes without a migration. The document requires an approved code; the
  registry, not the column, is where that list lives.
- **Cumulative ceilings still need service logic.** The reservation and budget rows make the check
  transactional and the constraints refuse an overspent window, but summing prior effective decisions
  against a source line is a query a service performs under lock, not something a constraint can see.
- **Cross-currency remedies are refused, not converted.** A remedy whose lines are in another currency
  is rejected outright. An approved, versioned foreign-exchange decision would be a separate table;
  nothing here pretends to do the conversion.
- **No service reads any of this yet.** The schema exists; `SupportIntakeService` and its siblings do
  not. Factories land with the services that use them.

## Deviations from the plan and the feature document

- **`support_audit_events` was not created.** Migration 012 delivered append-only `audit_events`
  alongside `outbox_events`, `consumer_inbox_receipts` and `command_idempotency_records`, and
  migration 027 declined the same duplication for the same reason: a second audit stream is a second
  thing to operate and a second place for the two to disagree. `evidence_access_log` is not that
  duplicate — it records purpose-bound reads and exports of protected case evidence at artifact
  granularity, which the general stream does not carry.
- **Four tables the document implies but does not name were added.** `case_decision_approvals`,
  because the document's own constraints section demands a unique effective approval per decision
  digest and approval role and forbids self-approval, neither of which is checkable without a row.
  `payment_dispute_strategies`, because the document devotes a section to exactly what D16 stores
  about a dispute payment owns. `remedy_budget_windows` and `remedy_budget_consumptions`, because the
  cumulative goodwill ceiling is explicitly required to be transactional and database-backed.
- **`remedy_requests` from migration 025 was left alone.** Stay operations uses it to ask another
  domain for an operational action during a live stay. `remedy_instructions` here is the monetary and
  contractual equivalent owned by a support decision: one is a request with no funder, the other must
  name a funder, a ceiling and an approval before it may exist at all.

## What this closes

Domain 16 now has a schema. A case can coordinate a booking, a payment dispute, an incident and a
protection claim without owning any of them; an allegation cannot be displayed as a finding; a remedy
cannot exist without a funder and a checked ceiling; an approval cannot survive the terms it approved
changing; a case cannot close over money still in flight; and a provider timeout is a state somebody
owns rather than a silent loss.

## Exit criteria

- Fifty-six changesets apply to an empty database and roll back to nothing — zero tables, zero
  functions, zero changelog rows — and re-apply cleanly.
- The multi-column enum-width audit returns no rows across migrations 012 to 028.
- Two hundred and sixty-four probe scenarios behave as tabulated above, with no stray errors.
- Every aggregate matches its table in both directions, including nullability: zero disagreements.
- `compileJava` succeeds and `javadoc` produces no new warnings.
- The application boots with no `PropertyReferenceException` and no `QueryCreationException`.
