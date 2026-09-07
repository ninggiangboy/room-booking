# Reviews, Aspect Intelligence, and Reputation

## Purpose

This document defines the target design for D14 — Reviews, Aspect Intelligence, and Reputation in
the [marketplace problem map](../marketplace-problem-breakdown.md). It answers the central product
and system question:

> How can Room Booking turn feedback from real stays into trustworthy public reviews, useful
> listing-quality evidence, and fair contextual reputation without enabling retaliation,
> manipulation, unsupported claims, or an irreversible score about a person?

The feature begins with verified review rights created from committed booking outcomes. It covers
guest-to-listing/host and host-to-guest feedback, review windows, double-blind publication,
immutable content revisions, public responses, private feedback, moderation integration, helpful
votes, rebuildable rating aggregates, multilingual aspect extraction, listing strengths and
weaknesses, reviewer attention, and contextual reputation.

This document separates five facts that must not collapse into one status or score:

```text
completed-stay eligibility
        != submitted content
        != moderation/visibility decision
        != public rating or aspect aggregate
        != action taken from reputation evidence
```

[Messaging, notifications, and stay operations](messaging-notifications-and-stay-operations.md)
supplies stay evidence and reminder delivery, while Booking owns the committed `StayCompleted`
fact. [Trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns
content-policy decisions, manipulation investigations, restrictions, and moderation appeals.
[Disputes, damage claims, insurance, and customer support](disputes-damage-claims-and-support.md)
owns support cases, evidence coordination, and remedies. [Personalized search and discovery](personalized-discovery.md)
consumes qualified review and aspect projections; it does not own or rewrite review truth.
[Data, experimentation, and machine-learning platform](data-experimentation-and-ml-platform.md)
owns cross-domain analytical events, experiment evidence, point-in-time datasets, feature/model
registry, and generic prediction lifecycle. D14 owns review-source truth, exact evidence attribution,
public aggregates, and whether a qualified extraction becomes a visible aspect claim.

## Status and dependencies

[Vietnam market readiness and internationalization](multi-market-compliance-and-localization.md)
owns locale, privacy, retention, policy, and market-validation context for review and model outputs.

This is an implementation-oriented target design as of 2026-09-07. It is not a description of a
working review API or production intelligence pipeline.

The repository currently provides these foundations:

- [`006-trust-engagement.sql`](../../src/main/resources/db/changelog/changes/006-trust-engagement.sql)
  creates `reviews` and `favorites`. A review has booking, listing, reviewer, optional reviewee,
  direction, one overall score, five optional listing-category scores, comment, publication time,
  optimistic version, and a unique `(booking_id, review_type)` constraint.
- [`004-booking.sql`](../../src/main/resources/db/changelog/changes/004-booking.sql) provides booking
  participants, stay dates, listing snapshots, and a coarse `COMPLETED` state from which eligibility
  can initially be derived.
- [`001-identity.sql`](../../src/main/resources/db/changelog/changes/001-identity.sql) includes
  `host_profiles.average_rating` and `review_count`. They are read-model counters, not review truth.
- [`002-listing-catalog.sql`](../../src/main/resources/db/changelog/changes/002-listing-catalog.sql)
  owns listing and current host identity but has no listing rating counters, review epoch, or
  quality/aspect profile.
- [Migration 006 data-model notes](../data-model/006-trust-engagement.md) already require completed
  bookings, verified participants, one review per direction, and rebuildable counters.

The current schema does **not** implement review rights, window/deadline snapshots, double-blind
release, drafts, immutable revisions, publication batches, public responses, translations,
attachments, private feedback, reports, helpful votes, exact-revision moderation, appeal links,
aggregate provenance, aspect taxonomy, extraction, listing quality profiles, reviewer calibration,
or contextual guest reputation. `published_at IS NULL` cannot safely represent all draft,
submitted, sealed, quarantined, removed, withdrawn, and expired states. The Java application exposes
identity, authentication, account control, and host onboarding only; there is no D14 controller,
service, repository, event handler, scheduler, or model pipeline.

All proposed records require new forward-only Liquibase migrations when implementation is
authorized. Existing changesets `001`, `002`, `004`, and `006` remain unchanged. Legacy reviews
must receive explicit provenance such as `LEGACY_IMMEDIATE_PUBLICATION`; migration code must not
invent an original deadline, moderation result, or paired-submission history.

Recommended dependency order:

1. Decide launch review directions, eligible booking outcomes, window length, publication policy,
   category schema, author-edit rules, display rules, moderation policy, and host-to-guest use.
2. Make Booking emit durable, versioned `StayCompleted` facts and corrections; define participant
   and listing/host attribution from the accepted booking revision.
3. Add review-right, lifecycle, immutable-revision, publication, idempotency, and audit records with
   deterministic double-blind release.
4. Integrate notification reminders and D15 exact-revision moderation, reports, removal,
   restoration, and appeal before relying on public text at scale.
5. Build transparent public rating/category aggregates and reconciliation before serving derived
   values to listing pages or ranking.
6. Add a versioned aspect taxonomy and asynchronous extraction in shadow mode, then expose only
   confidence-qualified evidence.
7. Add contextual reputation and guest preference evidence only after privacy, fairness, appeal,
   point-in-time data, and misuse controls are approved.

The target release includes verified guest-to-listing and host-to-guest feedback, one review per
direction, an effective-dated review window, deterministic double-blind release, governed
exact-revision moderation, rebuildable public aggregates, versioned aspect summaries, and
evidence-qualified learned reputation. Public attachments and reviewer-score calibration are
designed extensions; host-to-guest use remains bounded by explicit fairness and appeal policy.

## Goals

- Allow only verified participants in an eligible completed stay to create feedback for that stay.
- Prevent retaliatory feedback through a deterministic, explainable double-blind publication flow.
- Preserve original submissions and every material edit, moderation action, correction, and appeal.
- Publish clear overall/category ratings with visible evidence counts and reproducible rounding.
- Keep listing quality separate from host behavior, guest behavior, moderation risk, and booking
  eligibility.
- Extract multilingual aspect evidence with target, sentiment, source span, confidence, taxonomy,
  and model provenance.
- Describe strengths, weaknesses, uncertainty, recency, and trend without presenting weak inference
  as fact.
- Infer a reviewer's areas of attention separately from sentiment and share only governed features
  with personalization.
- Give hosts actionable quality evidence without exposing private feedback or anti-abuse internals.
- Make reports, moderation, removal, restoration, author withdrawal, and appeals additive and
  auditable.
- Resist fake stays, collusion, review extortion, report brigading, reciprocal manipulation, and
  helpful-vote abuse.
- Support deletion, redaction, retention, legal hold, localization, accessibility, operational
  reconciliation, and safe replay.
- Deliver a correct deterministic workflow before advanced artificial intelligence (AI) or
  machine-learning (ML) automation.

## Non-goals

- Deciding whether a booking completed, a no-show occurred, or a cancellation deserves a review.
- Letting Support directly edit a review, rating aggregate, or moderation state.
- Replacing D15 moderation, fraud labels, safety restrictions, reviewer queues, or moderation
  appeals.
- Treating a review allegation as a verified incident, damage finding, legal fact, or refund
  entitlement.
- Producing one permanent marketplace-wide trust score for a host or guest.
- Allowing a host-to-guest rating to silently deny ordinary instant-book access.
- Using protected characteristics, sensitive inferences, or neighborhood demographics in
  reputation or aspect profiles.
- Publishing private feedback, support evidence, private messages, exact addresses, or internal risk
  features.
- Letting a generative model author a review, change its meaning, decide eligibility, publish or
  remove content, or take an action against a person.
- Building a standalone microservice, event-sourced architecture, vector database, or real-time ML
  feature platform before measured scale requires it.
- Defining search ranking, financial remedies, incentives, host payout, or listing publication;
  D14 only supplies qualified evidence to their owners.

## Core principles and invariants

### A completed booking creates a bounded review right, not a review

Only a committed booking outcome can open a review right. D14 consumes `StayCompleted` and creates
rights for exact actors, direction, listing, host/operator attribution, booking revision, market
policy, and deadline. A device event, message read, checkout proposal, support claim, payment state,
or client assertion is insufficient. The right expires and cannot be transferred.

### Eligibility, content, visibility, and aggregates are separate state dimensions

A participant may be eligible while no review exists. A submitted revision may remain sealed or
quarantined. A published review can later be hidden by an additive moderation decision. Aggregates
include only revisions whose current qualified visibility permits inclusion. No nullable timestamp
or overloaded status may represent all four dimensions.

### Original meaning is immutable; corrections are additive

Every submission and material edit creates an immutable revision. Publication points to an exact
revision. Moderation creates decisions against exact revisions. Author withdrawal, legal redaction,
restoration, and appeal create later records; they never overwrite the original protected evidence
or pretend it was never visible.

### Double-blind release must not reveal who submitted first

Before the reveal condition, neither party learns whether the counterpart submitted, the text,
rating, sentiment, category values, or moderation state. Reviews become independently eligible for
publication when both sides have final submitted revisions or when the snapshotted deadline closes.
Notifications and API shapes must not leak a counterpart's activity indirectly.

### One booking grants at most one review per direction

There is at most one authoritative review aggregate for `(booking_id, direction)`, regardless of
client retries, drafts, edits, restoration, or legacy import. Immutable revisions are children of
that aggregate. A database uniqueness constraint remains the final defense.

### Public ratings are transparent; ranking quality is a different projection

The public star average is the arithmetic mean of included published ratings, with count and a
documented display rounding rule. Bayesian shrinkage, recency, uncertainty, fraud confidence, or
reviewer calibration may contribute to an internal listing-quality projection, but must not be
presented as the raw public average. Product copy names the metric it shows.

### Missing evidence is unknown, not bad

A new listing, absent category score, unmentioned aspect, untranslated review, low-confidence
extraction, or removed review contributes no negative value merely because it is missing. Priors
may stabilize ordering but cannot become fabricated guest evidence or a public defect label.

### Attention is distinct from sentiment and preference

Mentioning cleanliness frequently indicates attention to cleanliness. It does not by itself say
whether the reviewer liked clean listings, experienced a defect, or will prioritize it on every
trip. D14 stores mention target, sentiment, context, confidence, and evidence; D06 combines it with
behavior and explicit trip intent under separate personalization policy.

### Review text is an allegation or opinion until independently established

A review can truthfully describe a user's experience without proving a safety event, fraud,
property defect, discrimination, or liability. D13/D16 may link the exact revision as evidence, and
D15 may investigate policy violations. D14 never promotes unadjudicated text into a confirmed risk
label or domain action.

### Moderation decides policy; Reviews enforce visibility

D15 owns exact-revision content classification, manipulation findings, moderation actions,
restrictions, reviewer authority, and moderation appeal. D14 owns review lifecycle, seals/reveals,
current visibility projection, public surfaces, and aggregate inclusion. It applies only a valid,
versioned D15 decision and does not independently reinterpret the policy result.

### Derived intelligence always carries evidence and provenance

Every aspect mention and aggregate records source revision, evidence span or structured rating,
language, taxonomy version, extractor/model version, confidence, computation version, input
watermark, and time. A public or host-facing summary must trace to still-visible supporting reviews
and pass minimum evidence thresholds.

### Human reputation is contextual and reversible

Host and guest signals are attached to a role, interaction context, time window, evidence class,
and policy purpose. A review cannot become an irreversible universal score. Consequential use needs
an explicit owner, minimum evidence, reason, proportional action, expiry/review, and appeal path.

### Network calls never hold review locks

Translation, media scanning, AI extraction, notification delivery, and moderation-provider calls
occur after a short local transaction. Durable outbox/inbox records, idempotency, retries, and
reconciliation bridge external work. Provider timeout never creates a second review or exposes a
sealed one.

### Database constraints remain the final local defense

Application validation provides clear errors, but database uniqueness, foreign keys, rating/range
checks, monotonic revision numbers, active-publication constraints, and optimistic versions defend
invariants under concurrency and replay.

### Historical replay produces the same qualified result

Given the same review rights, immutable revisions, publication policy snapshot, moderation
decisions, taxonomy/model outputs, aggregate rule version, and cutoff time, the system can reproduce
what was public and which aggregate existed. Reprocessing with a new model creates a new derived
version; it never rewrites the historical output silently.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Review right | A bounded authorization for one actor to review one completed booking in one direction |
| Direction | `GUEST_TO_LISTING` or `HOST_TO_GUEST`; later directions require explicit policy and schema |
| Review cycle | The paired, deadline-bound publication context for both directions of one booking |
| Review window | Effective interval from eligibility opening until the snapshotted submission deadline |
| Submission | The act that freezes a selected revision as the participant's current final feedback |
| Sealed review | Submitted feedback hidden from the counterpart and public until reveal conditions hold |
| Reveal | A deterministic cycle transition that makes qualified submitted revisions publication-eligible |
| Review aggregate | One lifecycle identity for a booking/direction containing immutable revisions |
| Review revision | Immutable rating/text/private-feedback content with author and creation provenance |
| Qualified visibility | D14 publication eligibility composed with the current effective D15 moderation action |
| Public response | A host/listing representative's separate visible reply to a published guest review |
| Private feedback | Feedback visible only to authorized recipient/platform purposes and excluded from public aggregates |
| Structured category | A governed rating dimension with stable code and schema version |
| Public aggregate | Rebuildable count, sum, and arithmetic mean over included published reviews |
| Quality projection | Rebuildable confidence-aware internal representation used by discovery or host insights |
| Aspect | A controlled dimension such as `CLEANLINESS`, `WIFI`, `QUIETNESS`, or `CHECK_IN` |
| Aspect mention | One structured observation extracted from a bounded source span or explicit category |
| Target | Entity discussed by a mention: listing/property, host/service, location, external condition, or unknown |
| Sentiment | Direction/intensity of the source statement, separate from mention frequency and factual truth |
| Evidence count | Number of distinct qualified reviews; not the number of phrases emitted by a model |
| Effective evidence | Weighted evidence used for a derived projection after confidence and recency rules |
| Reputation context | Role, product purpose, interaction type, time horizon, and evidence class for a derived signal |
| Moderation action | D15-owned `PUBLISH`, `MASK`, `QUARANTINE`, `REJECT`, `REMOVE`, or restoration/supersession decision |
| Review report | A user's allegation about an exact published review or response revision |
| Helpful vote | A low-stakes user signal about usefulness; never proof of truth or policy compliance |
| Publication epoch | Monotonic version identifying a rebuild of a listing/host public review projection |
| Local deadline | A market-policy local date/time plus IANA zone resolved and stored as an exact UTC instant |

Ratings use integers from 1 through 5 at submission. Public averages use exact decimal arithmetic
over integer sums and counts. They are not money and do not use floating-point application types.
Dates tied to a stay use listing-local dates; submission, deadline, publication, moderation, and
event times use UTC instants. The review right stores the IANA zone and the exact resolved deadline
so daylight-saving or policy changes do not alter history.

## End-to-end flow

```text
Booking commits StayCompleted for accepted booking revision
        |
        v
D14 idempotently creates review cycle + exact directional rights + policy snapshot
        |
        +--> reminder intents scheduled through D12 without counterpart-status leakage
        |
eligible participant creates/edits draft -> submits exact immutable revision
        |
        v
D15 evaluates exact revision: publish / mask / quarantine / reject / escalate
        |
        +--> both directions final and qualified --------+
        |                                               |
        +--> snapshotted deadline reached --------------+--> reveal transaction
                                                            |
                                                            v
                                              publish qualified revisions
                                                            |
                      +----------------------+---------------+------------------+
                      |                      |                                  |
                      v                      v                                  v
             public review page    rating/category aggregates       async aspect extraction
                      |                      |                                  |
                      v                      v                                  v
              report/response      discovery + host insights       evidence-qualified profiles
                      |                                                         |
                      +--> D15 moderation/appeal; D16 support link <-------------+
```

Creating rights, submitting a revision, accepting a moderation result, revealing a cycle, and
publishing an aggregate delta are separate short database transactions. The submission transaction
does not call moderation, translation, storage, or notification providers. It commits immutable
content metadata and an outbox fact. Workers process optional external work and retry safely.

The reveal transaction locks the cycle and current review aggregates, re-evaluates the snapshotted
deadline and qualified revision state, assigns a single `reveal_version`, and creates publication
records atomically. Aggregate refresh and aspect analysis may be asynchronous, but public responses
must expose a freshness/version marker and reconciliation must repair any lag. Search may use a
stale quality projection within policy bounds; booking and review eligibility never depend on it.

## Ownership and source-of-truth matrix

| Fact or decision | Authoritative owner | D14 behavior |
| --- | --- | --- |
| Booking participants, accepted revision, listing, host, stay dates | Booking | Consume immutable identity/snapshot; never replace it with current listing data |
| Completed, cancelled, or no-show outcome | Booking | Open/revoke a right only from committed versioned facts |
| Check-in/out and stay evidence | D13 stay operations | Consume as linked evidence only when review policy requires it |
| Directional review right and deadline | D14 Reviews | Create and enforce exact actor/action scope |
| Original review and every revision | D14 Reviews | Preserve immutable content and provenance |
| Double-blind cycle and reveal | D14 Reviews | Decide from snapshotted publication policy and qualified submissions |
| Review content-policy/manipulation decision | D15 Trust and Safety | Enforce effective exact-revision action and consume confirmed labels |
| Moderation appeal | D15 Trust and Safety | Link appeal and apply superseding outcome; do not decide it |
| Public rating/category aggregate | D14 Reviews | Rebuild from qualified publication truth |
| Aspect taxonomy and qualified mention/profile | D14 Review Intelligence | Version, evaluate, and rebuild from review evidence |
| Guest search preference and rank | D06 Discovery | Supply evidence features; never choose rank or infer trip intent here |
| Listing facts and publication | D03 Listing | Link exact version; do not edit listing truth from a review |
| Incident, dispute, finding, remedy, or claim | D13/D16 | Link relevant case evidence; never infer entitlement from review sentiment |
| Host/guest restriction or fraud label | D15 Trust and Safety | Supply bounded review evidence; never impose restriction directly |
| Reminder/receipt delivery | D12 Notifications | Request intent from committed D14 facts; delivery does not change D14 state |
| Experiment and model release evidence | D19/D20 when implemented | Emit labels/features; retain deterministic D14 fallback and authority |

One modular monolith may implement several owners initially. Ownership means a single contract and
invariant owner, not an immediate deployment boundary.

## Eligibility and review rights

### Canonical eligibility input

D14 accepts only a versioned `StayCompleted` fact from Booking. The minimum input is:

- booking ID and accepted booking revision ID/version;
- listing ID and immutable listing identity snapshot;
- guest ID, contractual host ID, and approved operating-party attribution at completion;
- listing-local check-in/check-out dates and IANA timezone;
- completion event ID/version and completion instant;
- market and review-policy key/version;
- relevant no-show/cancellation/replacement lineage;
- correlation and causation IDs.

The server resolves policy and computes exact rights. Clients cannot supply reviewer, reviewee,
direction, listing, deadline, host attribution, or publication mode. Current listing ownership is
not used to rewrite historical attribution. A property transfer or co-host change after checkout
does not move the review to a different actor.

### Directional rights

The default cycle may create:

| Direction | Author | Subject | Public audience | Target-release use |
| --- | --- | --- | --- | --- |
| `GUEST_TO_LISTING` | Booking guest | Listing experience and attributed host service | Public after reveal/moderation | Overall/category/text aggregate and evidence |
| `HOST_TO_GUEST` | Contractual host or explicitly authorized representative | Booking-relevant guest conduct | Restricted host-facing context; public profile only if separately approved | Feedback collection; no automated denial |

A co-host may act only if Booking/Identity proves an active, booking-scoped delegated permission and
policy allows representation. The revision records both the acting user and represented host. It
must not appear as if the principal personally authored text when another actor did.

The reviewee for guest-to-listing feedback is conceptually both the historical listing and the host
service attribution. The existing schema stores `reviewee_id = NULL`; a forward design should store
an explicit subject/attribution snapshot rather than infer it later from current ownership.

### Review-right state machine

```text
PENDING_SOURCE
      |
      | verified StayCompleted
      v
     OPEN -----------------------------> EXPIRED
      |                                     ^
      | submitted                           | deadline
      v                                     |
  EXERCISED                                 |
      |                                     |
      +------------------------------ no further submissions

OPEN or EXERCISED -- authoritative booking correction --> REVOKED
```

`EXERCISED` means the right produced one review aggregate; edits occur as revisions within it. A
right is not reopened merely because the author withdraws or D15 removes content. `REVOKED` is rare
and requires a superseding Booking fact proving the eligibility basis was invalid, not a support
preference. If already published, revocation produces an auditable prospective withdrawal and
aggregate correction; it does not erase the historical publication.

### Booking outcome and exception rules

Recommended default rules are:

- `COMPLETED`: both configured directional rights open.
- full cancellation before consumption: no public stay review right.
- `NO_SHOW`: no ordinary listing-stay review; a future narrow communication/transaction feedback
  type requires separate semantics and must not be mixed with stay ratings.
- early departure with Booking still completed: eligible under the accepted completion policy;
  relevant experience can be reviewed.
- modified stay: one right per final booking lineage, attributed to the accepted completed revision.
- relocated/replacement booking: each actually completed accommodation may need an explicit lineage
  policy; do not create two full reviews from one consumed stay without a product decision.
- open incident or support case: does not automatically remove eligibility or delay publication.
  Only an explicit, time-bounded D15/legal visibility action may do so.
- refunded stay: refund alone does not invalidate a genuine experience. A superseding booking
  outcome or confirmed manipulation policy is required.
- account suspension/deletion: does not silently destroy valid reviews. Ability to submit/read is
  governed by active access and legal policy; retained public attribution can be pseudonymized.

### Eligibility policy snapshot

Each right stores the exact effective-dated policy used, including:

- eligible booking outcomes and evidence requirements;
- direction and actor rules;
- `opens_at` and `submission_deadline_at` instants;
- listing timezone and local deadline representation;
- publication mode and maximum moderation hold;
- draft/submission/edit/withdrawal rules;
- rating/category schema version;
- public response window;
- reminder schedule family;
- market/legal disclosure version.

Changing current policy never moves a historical deadline or adds a direction retroactively unless
an explicit migration/correction program records its legal/product basis and customer notice.

## Review lifecycle and double-blind publication

### Review-cycle state

The review cycle coordinates disclosure for one booking without owning either review's content:

```text
OPEN
  | first qualified submission
  v
ONE_SIDED_SEALED
  | second qualified submission             | deadline
  +------------------------------------------+
                                             v
                                         REVEALING
                                             |
                                             v
                                          REVEALED

OPEN -- deadline with no submission --> CLOSED_EMPTY
```

The state is monotonic. A late moderation removal does not move `REVEALED` backward; it changes the
affected review's qualified visibility. A cycle version and database transition guard prevent two
workers from issuing separate reveal epochs.

### Recommended window and reveal rule

The recommended launch default is a 14-calendar-day submission window beginning when
`StayCompleted` commits. The exact duration and cutoff convention remain a prerequisite policy
decision and are
effective-dated by market. The resolved UTC deadline is stored on the cycle.

Reveal occurs when either:

1. both directional reviews have a final revision currently qualified for publication; or
2. the snapshotted deadline has passed, after which every submitted qualified revision may publish.

If both parties submit before the deadline but one revision is quarantined, the qualified review
remains sealed until the moderation decision, the original review deadline, or a separately
configured maximum moderation hold—whichever policy specifies. The system must not hold an innocent
party's review indefinitely. At the maximum hold, publish each independently qualified revision and
keep the other hidden. The UI must not reveal whether the delay came from no counterpart submission
or moderation.

### Draft, submit, and edit

A draft is private to the author and not moderation/publication truth. Autosave uses optimistic
versioning and bounded retention. Submission:

1. authorizes the exact directional right;
2. validates required ratings, category schema, text, locale, and policy acknowledgments;
3. creates an immutable content revision;
4. selects it as `submitted_revision_id`;
5. marks the right `EXERCISED`;
6. emits `ReviewSubmitted` through the outbox;
7. returns the same result for an idempotent retry.

Recommended anti-retaliation edit rule: the author may replace their sealed final revision until
the counterpart submits or the cycle deadline, whichever comes first. The act of editing creates a
new immutable revision and invalidates any D15 decision for the replaced text. After reveal,
substantive edits are not allowed; typographical correction, privacy redaction, or legally required
correction follows an additive governed workflow. This rule and any post-publication correction
window require explicit product/legal approval.

The API never exposes `counterpartSubmitted`, the peer's revision count, moderation outcome, or
whether the author's edit window closed because of peer activity. It returns only a safe capability
such as `mayEditUntil` when that cannot leak protected state, or a generic `EDIT_WINDOW_CLOSED`.

### Submission and visibility dimensions

Each review aggregate keeps independent dimensions:

| Dimension | Example states |
| --- | --- |
| Authoring | `DRAFT`, `SUBMITTED_FINAL`, `AUTHOR_WITHDRAWN` |
| Cycle disclosure | `SEALED`, `REVEAL_ELIGIBLE`, `REVEALED` |
| Moderation | `PENDING`, `PUBLISH`, `MASK`, `QUARANTINE`, `REJECT`, `REMOVE`, `RESTORED` |
| Public projection | `NOT_PUBLIC`, `PUBLIC`, `PUBLIC_REDACTED`, `HIDDEN` |
| Intelligence eligibility | `PENDING`, `INCLUDED`, `EXCLUDED`, `REPROCESS_REQUIRED` |

Derived public state is computed from these dimensions. Code must not invent a single combined enum
with an unreviewable cross-product of states.

### Expiry and late events

The deadline worker claims due cycles with a fenced lease, locks the cycle, compares database time
to `submission_deadline_at`, and reveals qualified reviews once. A job running late preserves the
original deadline semantics. A client request received before the deadline but committed after it
is accepted only if policy explicitly uses trusted server receipt time and persists that receipt;
otherwise the transaction commit check fails with `REVIEW_WINDOW_EXPIRED`.

A `StayCompleted` event arriving late still opens a window from the authoritative completion time,
not consumer receipt time. If the calculated window is already expired because of platform delay,
the case enters an operational repair policy that may grant an explicit replacement right with
notice; workers must not silently backdate a usable right.

### Author withdrawal and public response

An author may request withdrawal where policy allows. Withdrawal creates a signed state transition,
hides the review prospectively, removes it from future aggregates, and retains protected history.
It does not reopen the right or delete a counterpart's review.

A host public response is a separate content aggregate tied to one published guest review and an
authorized historical/current listing representative. It has its own immutable revisions,
moderation, edit rule, and response deadline. It cannot change ratings or appear before the parent
review. Target policy allows one concise response and additive moderation correction, not
an unbounded public thread. Guests use reports or support, not nested arguments.

### Reminders

D14 emits reminder intents such as `REVIEW_WINDOW_OPENED`, `REVIEW_WINDOW_ENDING`, and
`REVIEW_PUBLISHED`; D12 owns channel, locale, consent/classification, quiet hours, template,
deduplication, delivery, and retry. Reminder copy is neutral, never asks for a positive rating, and
does not disclose counterpart activity. Delivery failure does not extend the window unless an
approved remediation program creates a separately audited exception.

## Rating, text, and feedback design

### Overall and category ratings

Overall rating is required for a public guest-to-listing review and uses an integer `1..5` scale.
Category ratings are controlled by a versioned schema with stable codes, definitions, applicability,
required/optional rules, direction, locale copy, and effective interval.

An initial guest-to-listing schema may include:

| Code | Subject | Notes |
| --- | --- | --- |
| `CLEANLINESS` | Property | Existing schema field |
| `ACCURACY` | Listing/property | Compare experience with accepted listing snapshot |
| `COMMUNICATION` | Host service | Existing schema field; attribution must be explicit |
| `CHECK_IN` | Access/operation | Proposed; distinct from general communication |
| `LOCATION` | Experienced location fit | Subjective experience, not objective safety/demographics |
| `VALUE` | Stay value | Opinion based on experienced total; not a price authority |

Existing records have no `CHECK_IN` column and no category schema version. A forward migration may
normalize category values into child rows rather than adding a column for every future category.
Host-to-guest categories require a separate reviewed vocabulary limited to observable,
booking-relevant conduct. Listing-oriented categories must remain null for that direction.

The UI must explain scale anchors consistently. Category questions should include `NOT_APPLICABLE`
rather than forcing a neutral score. Category averages never silently substitute for overall
rating, and an absent category is not imputed into public statistics.

### Text and language

The authoritative revision stores:

- original Unicode text exactly as accepted, subject to canonical storage and size limits;
- author-declared locale and detected language with confidence;
- category/overall values and structured answers;
- client submission ID, author/representative IDs, server receipt/commit times;
- content digest, schema/policy versions, and superseded revision link;
- disclosure acknowledgments and public/private field boundaries.

Normalization may reject control characters, invalid encodings, excessive repetition, or unsafe
markup but must not silently rewrite meaning. Public rendering escapes content, applies approved
line/length limits, and never interprets user text as HTML. Accessibility requires labels, keyboard
support, clear error association, and screen-reader-safe star controls.

### Translation

The original is always authoritative. A translation is a derived immutable artifact storing source
revision/digest, source/target languages, provider/model/human version, created time, confidence,
moderation compatibility, and supersession. It is labeled as translated and can be regenerated or
disabled independently.

Translation must not publish a sealed or hidden review, bypass redaction, or send protected/private
content to an unapproved provider. Users can view the original. Material translation reports enter
a correction workflow; they do not edit the review.

### Attachments

Public review attachments are a designed extension boundary. If introduced, upload sessions are short-lived and
bound to actor, review right, media type, maximum count/size, and expected digest. Objects remain
quarantined until byte-level type validation, malware scanning, metadata stripping, moderation, and
safe derivative generation complete.

Damage photos, identity documents, exact-address images, and sensitive incident evidence belong in
D16/D13 protected evidence storage, not public review media. D14 may link authorized evidence but
must never copy it into a public attachment merely because the author referenced it.

### Private feedback

Private feedback is a separately typed payload with explicit audiences, such as host operational
suggestion or platform experience feedback. It is excluded from public review text, public rating,
aspect summaries, and host-to-guest display unless policy explicitly says otherwise. Access is
purpose-bound and audited.

Private answers may contribute to aggregate product analytics only under declared purpose,
retention, minimum cohort, and privacy review. They are not automatically ML labels or D15 risk
evidence. A safety report needs a clear dedicated path that routes immediately rather than waiting
for review publication.

## Moderation, reports, appeals, and revision integrity

### Exact-revision moderation contract

Every submitted review, replacement revision, translation intended for display, attachment, and
public response is a stable D15 content item/revision. D14 submits the revision ID, digest, author,
resource scope, direction, language, visibility stage, and minimum necessary content. D15 returns a
versioned decision referring to that exact digest. A decision for revision 2 cannot approve revision
3.

The composed behavior is:

| D15 outcome | D14 lifecycle effect | Aggregate/intelligence effect |
| --- | --- | --- |
| `PUBLISH` | Revision may reveal when cycle policy permits | Include after actual publication |
| `MASK` | Publish approved redacted derivative after reveal | Use only permitted text/category evidence |
| `WARN` | Obtain required author confirmation; stay sealed | Exclude until confirmed/qualified |
| `QUARANTINE` | Keep hidden for bounded review | Exclude; do not reveal reason to counterpart |
| `REJECT` | Do not publish revision; safe reason/appeal to author | Exclude |
| `REMOVE` | Hide a previously public revision additively | Subtract/rebuild from effective time |
| `ESCALATE_SAFETY` | Route D13/D15 immediately; visibility decided separately | No automatic public/score effect |
| superseding restore | Re-publish if lifecycle and policy still permit | Add/rebuild in a new projection epoch |

Ratings and text are one review revision for lifecycle and audit, but policy may allow a text mask
while retaining a valid rating. That choice must be explicit because publishing a rating after text
rejection can still enable coercion or manipulation. The recommended safe default for confirmed
manipulation is to exclude the entire review; for personal-data-only redaction, retain qualified
ratings and an approved redacted text derivative.

### Policy categories and relevance

D15 evaluates spam, copied content, personal information, harassment, threats, discrimination,
sexual exploitation, illegal goods, malicious links, off-platform payment, extortion, incentivized
reviews, irrelevance, coordinated manipulation, and other approved categories. D14 supplies review
context and implements the visibility outcome.

Disagreement, criticism, low ratings, a host's dislike of an opinion, or the existence of a refund
is not by itself a removal reason. Factual disputes may be answered publicly or investigated through
D16. Content removal requires a defined policy basis and evidence; D14 cannot resolve contested
liability by comparing sentiment scores.

### Reports

A report identifies exact review/response revision, reporter authority, category, bounded
description, optional protected evidence reference, locale, client report ID, and creation time.
The reporter receives an acknowledgment, not a promise of removal. The reported author never sees
reporter identity.

Database uniqueness or idempotency prevents duplicate reports from one actor/request. D15 may group
reports for investigation while retaining separate allegations. Rate limits, relationship signals,
and brigading detection prevent reporting from becoming a removal vote. Safety reports bypass
ordinary queue priority and use approved urgent routing.

### Moderation appeal and restoration

The affected author appeals a specific D15 decision, not an informal current visibility flag. D15
owns eligibility, deadline, independent review, evidence, reason, and superseding outcome. D14
exposes the safe appeal link/status and applies the effective result idempotently.

Restoration creates a new publication transition and aggregate epoch. It retains original
publication/removal intervals so a historical query can explain what was visible at a past time.
Appeal success does not imply a support remedy; any service recovery is evaluated by D16.

### Legal redaction and privacy correction

Legal removal, privacy redaction, and account erasure need their own authority and reason codes.
Where retention/legal hold permits, public identity may be pseudonymized while transactional proof
and protected evidence remain. A redacted derivative points to the original digest and approved span
map. Ordinary operators cannot download originals or reverse redactions.

## Helpful votes and public interaction

A helpful vote is a low-stakes, reversible interaction on a currently visible review. One user may
have at most one active vote per review. Authors cannot vote for their own review, and anonymous
votes are not accepted unless a separate abuse-resistant design is approved.

Helpful counts are derived and may be delayed. They influence presentation only after minimum
volume, position-bias analysis, and D15 abuse controls. They never establish that a review is true,
change its star contribution, create review eligibility, or override moderation. Users can remove
their vote; the event history may be retained according to analytics/privacy policy.

Public review ordering starts with a deterministic option such as newest qualified publication.
Later `MOST_HELPFUL` ordering must disclose the sort, preserve pagination stability, avoid burying
critical recent evidence, and apply controlled anti-gaming rules. Sponsored or host-selected review
ordering is not allowed to masquerade as organic helpfulness.

## Public rating and quality aggregates

### Inclusion set

For a cutoff time, a review contributes only when all conditions hold:

- its right was valid for the correct booking/direction;
- the cycle revealed it;
- an exact revision has a qualified public publication interval containing the cutoff;
- it is not author-withdrawn, revoked, removed, or excluded by a confirmed manipulation decision at
  that cutoff;
- the rating/category belongs to the snapshotted schema and passes structural validation.

Aggregation reads authoritative D14/D15 records, not search documents, caches, analytics tables, or
model output. A review can contribute at most once per aggregate/dimension.

### Transparent public average

For included integer ratings `r_i`:

```text
ratingCount = N
ratingSum   = sum(r_i)
rawAverage = ratingSum / ratingCount
displayAverage = round(rawAverage, configuredDisplayScale, configuredHalfRule)
```

Persist `rating_sum` and `rating_count`; derive the decimal average exactly. Recommended display is
one decimal place with a documented half-up or half-even rule chosen once per surface/market. Store
neither binary floating-point sums nor rounded review-level contributions. When `N = 0`, average is
`null`, not zero.

Overall and each category maintain separate count/sum. UI text includes the count and does not imply
all reviewers answered every category. Distribution histograms can be shown only with privacy and
minimum-count controls.

### Bayesian quality projection

Discovery and host insights need uncertainty-aware evidence separate from the raw public average. A
baseline posterior for ratings normalized to `[0, 1]` is:

```text
observedMean = sum(weight_i * normalizedRating_i) / sum(weight_i)

posteriorMean =
    (effectiveEvidence * observedMean + priorStrength * cohortPrior)
    / (effectiveEvidence + priorStrength)
```

The rule version defines cohort prior, prior strength, normalization, eligible weights, and minimum
evidence. Cohorts may use market, property/room type, and period only after minimum sample/fairness
review. They must not use protected classes or opaque neighborhood demographics.

For the first deterministic release, every qualified review should have equal raw-rating weight.
Recency, calibration, and manipulation confidence belong only to separately named quality features.
Do not secretly down-weight an inconvenient but valid public review.

### Confidence and uncertainty

Store count, effective evidence, posterior interval or calibrated confidence, last evidence time,
input watermark, rule version, and missing reason. Product thresholds distinguish:

- insufficient evidence: no public strength/weakness claim;
- emerging evidence: internal/host preview with uncertainty;
- established evidence: eligible for approved public summary/ranking feature;
- conflicting evidence: show mixed experiences rather than a false single conclusion.

Confidence is about evidence sufficiency and model reliability, not certainty that a subjective
statement is objectively true.

### Recency and trends

Keep all-time public aggregates transparent. For intelligence, calculate recent fixed windows and/or
documented exponential decay:

```text
recencyWeight(ageDays) = 2 ^ (-ageDays / halfLifeDays)
```

The rule version owns `halfLifeDays`, observation-time semantics, minimum effective evidence, and
late-event handling. Trends compare compatible windows and taxonomy/model versions. A trend is
published only when its uncertainty and practical magnitude pass thresholds; otherwise it is
`UNKNOWN`.

Severe decay must not hide a persistent defect. Host tools should show recent and all-time evidence
together. Confirmed remediation may be annotated, but it does not delete historical guest feedback.

### Listing, host, and ownership attribution

A listing aggregate follows the immutable listing ID. A host-service aggregate includes only
guest-to-listing reviews attributed to that host/operating relationship in the completed booking
snapshot. Current ownership cannot claim or inherit another host's service rating accidentally.

A property sale or material relaunch raises a policy question: keep one listing history, create a
new listing identity, or create visible quality epochs. The recommended default is to retain
property-experience history for the same physical offering while separating host-service metrics;
material identity changes require a governed listing lineage decision, not an ad hoc aggregate
reset.

### Incremental update and rebuild

Publication, removal, restoration, category correction, and attribution correction emit an exact
aggregate mutation fact. An incremental worker applies `(source_event_id, aggregate_key,
rule_version)` once and advances a watermark. Nightly/continuous reconciliation rebuilds from source
truth and compares count, sum, distribution, and review IDs.

If a delta conflicts or arrives out of order, mark the aggregate stale and rebuild; do not guess a
negative count. Public reads may use the last verified projection with a freshness marker for a
short bound. A correctness alarm fires when public review count and included-review truth diverge.

## Aspect intelligence

### Versioned taxonomy

Use a governed taxonomy with stable codes, localized labels, definitions, positive/negative/neutral
examples, allowed targets, sensitive-use classification, active interval, parent group, and
successor mapping. An initial vocabulary may include:

| Group | Candidate aspects |
| --- | --- |
| Property quality | `CLEANLINESS`, `ACCURACY`, `COMFORT`, `SPACE`, `MAINTENANCE` |
| Environment | `LOCATION`, `QUIETNESS`, `VIEW`, `NEIGHBORHOOD_EXPERIENCE` |
| Connectivity/work | `WIFI`, `MOBILE_SIGNAL`, `WORKSPACE` |
| Amenities | `KITCHEN`, `AIR_CONDITIONING`, `HEATING`, `PARKING`, `LAUNDRY`, `POOL` |
| Access | `CHECK_IN`, `ACCESSIBILITY`, `PUBLIC_TRANSPORT`, `STAIRS_ELEVATOR` |
| Host service | `HOST_COMMUNICATION`, `HOST_RESPONSIVENESS` |
| Economics | `VALUE`, `FEE_TRANSPARENCY` |

`SAFETY_PERCEPTION` may capture a reviewer's stated experience for protected analysis, but it cannot
be converted into an objective neighborhood-safety claim or a demographic proxy. Public/ ranking
use requires separate legal, fairness, and policy approval.

Taxonomy changes never reinterpret stored mentions silently. A new extractor writes a new output
version against a declared taxonomy. Mappings support comparison/backfill, and incompatible outputs
remain queryable by their original version.

### Mention representation

One aspect mention records:

- exact source review revision and content digest;
- source type: explicit category, text span, structured answer, or approved human annotation;
- bounded code-point span or sentence identifier; protected original text stays in D14 storage;
- aspect code/taxonomy version;
- target: `LISTING`, `HOST_SERVICE`, `LOCATION_EXPERIENCE`, `EXTERNAL_CONDITION`, or `UNKNOWN`;
- sentiment score/direction, intensity, negation/contrast, and applicable qualifier;
- stated time/context when detectable, such as current stay versus past host response;
- language/detection confidence and translation use;
- extractor/model/prompt/provider version and output digest;
- confidence, validation status, human review/supersession, and created time.

Multiple phrases from one review do not count as multiple independent reviews. Aggregate caps apply
per `(review, aspect, target)` so verbose writing cannot dominate concise feedback.

### Extraction pipeline

```text
ReviewPublished / eligible revision changed
        |
        v
load exact authorized source -> language + structural segmentation
        |
        v
apply D15 visibility/redaction/provider-disclosure rules
        |
        +--> deterministic category-to-aspect evidence
        |
        +--> schema-constrained classifier/LLM extraction
                         |
                         v
validate taxonomy, target, ranges, spans, cardinality, version, digest
                         |
                         +--> low confidence/invalid -> exclude or evaluation queue
                         |
                         v
store immutable mention set -> aggregate new profile version -> reconcile
```

Publication never waits for aspect extraction. Jobs are idempotent by source revision, taxonomy,
extractor version, and configuration digest. Provider calls receive only approved, minimized text;
private feedback and masked spans are excluded unless a separate purpose permits them. Prompt
injection inside review text is treated as untrusted data, never instructions.

### Aspect aggregate

For each `(subject, aspect, target, taxonomy_version, aggregation_rule_version)` retain:

- distinct qualified review count and positive/neutral/negative counts;
- explicit category evidence count separately from extracted text count;
- raw and confidence-weighted sentiment sums;
- effective evidence, posterior score/interval, and calibrated confidence;
- recent/all-time windows and trend with uncertainty;
- first/last evidence time and current source watermark;
- source language coverage and missing/exclusion reasons;
- supporting visible review IDs selected by deterministic evidence policy.

A conceptual quality calculation is:

```text
reviewWeight_i = min(perReviewCap,
                     sourceReliability_i
                   * extractionConfidence_i
                   * recencyWeight_i)

aspectMean = sum(reviewWeight_i * sentiment_i) / sum(reviewWeight_i)

aspectPosterior =
    (effectiveEvidence * aspectMean + priorStrength * cohortPrior)
    / (effectiveEvidence + priorStrength)
```

Explicit category evidence is not automatically more semantically detailed than text, and model
confidence is not truth probability. Weights and priors require offline calibration, versioning,
human review, and fairness evaluation. Production serving may use simple counts and minimum thresholds before
any weighted score.

### Strength, weakness, and mixed evidence

An aspect becomes a host/public strength only when score, lower confidence bound, distinct-review
count, recency, and manipulation-quality gates pass. A weakness uses stricter thresholds because a
false negative claim can materially harm a host. Recent deterioration blocks a cheerful all-time
strength label.

Approved templates use calibrated language:

- established positive: “Guests consistently rate cleanliness highly.”
- emerging positive: “Several recent guests mentioned reliable Wi-Fi.”
- mixed: “Guest experiences with street noise are mixed.”
- qualified negative: “Some recent guests reported weak Wi-Fi.”
- insufficient: show no claim, not “poor” or zero.

Words such as “always,” “safe,” “guaranteed,” or “the best” require objective authority that review
evidence does not provide. Every summary links to supporting currently visible reviews or an
auditable evidence panel. If evidence is removed, the summary is invalidated and rebuilt.

### Host actionability and correction

Host insights should show aspect definition, evidence count, recent/all-time direction, listing or
service target, representative permitted excerpts, and suggested operational category—not reveal
reviewer calibration, private behavior, report identity, or D15 detection internals. Suggestions
such as improve cleaning checklist, verify Wi-Fi, or update listing copy remain advisory.

A host may report wrong target, aspect, sentiment, translation, or summary. This creates an
intelligence-quality item against a derived output. Correcting it supersedes the derived mention or
profile version; it does not remove the original review. Repeated errors feed evaluation and release
gates, not user punishment.

### Multilingual evaluation

Maintain adjudicated evaluation sets by supported language, locale, aspect, sentiment, target,
negation, mixed opinion, and listing type. Measure precision, recall, F1, calibration, span overlap,
target error, polarity/intensity error, and abstention quality. Release gates apply per important
slice, not only global average.

Low-resource languages may use deterministic categories, human review, or no text-derived feature.
Translation-first extraction must be evaluated separately because translation can change negation,
intensity, idiom, or target. Unknown language/model outage always degrades to structured ratings and
no unsupported summary.

## Reviewer attention and preference evidence

### Evidence dimensions

D14 may derive a reviewer evidence profile containing, per aspect:

- mention frequency capped per review;
- positive, neutral, and negative sentiment separately;
- explicit category use and deviation from their overall rating;
- number of independent completed stays and time distribution;
- target mix and contextual trip metadata approved for this purpose;
- confidence, recency, model/taxonomy version, and missing reason.

This profile says what the reviewer discussed and how they evaluated it. It is not itself the D06
guest preference profile. D06 decides how review attention combines with searches, filters,
favorites, comparable prices, bookings, cancellations, session intent, and explicit controls.

### Safe preference handoff

The handoff should contain coarse, versioned evidence such as:

```json
{
  "guestId": "...",
  "aspect": "CLEANLINESS",
  "independentStayCount": 4,
  "attention": 0.82,
  "positiveThresholdEvidence": 0.74,
  "confidence": 0.69,
  "sourceWindow": "LONG_TERM",
  "taxonomyVersion": "2026-01",
  "evidenceVersion": 7
}
```

Do not send raw private text, unsupported demographic inference, report history, or other guests'
data. D06 confidence-gates the signal, lets explicit trip intent override it, and supports opt-out,
correction, and deletion. One complaint cannot establish a permanent preference.

### Reviewer calibration

Some reviewers use ratings more strictly or generously. Later, a bounded calibration feature may
estimate a residual against comparable stays after sufficient independent reviews. It must:

- use point-in-time comparable cohorts and avoid future-data leakage;
- require a minimum count and uncertainty bound;
- cap its contribution to internal quality confidence;
- never modify displayed raw ratings or authored content;
- never punish a person for critical feedback or erase a minority experience;
- never become a public “harsh reviewer” label;
- be disabled independently and rebuilt by version.

Calibration is a designed extension activated only after demonstrated improvement and fairness.
Transparent equal weighting is
the default.

## Contextual host and guest reputation

### Separate subject profiles

At minimum distinguish:

| Profile | Evidence | Appropriate use |
| --- | --- | --- |
| Listing experience | Guest-to-listing rating/text/aspects for one listing | Public listing page, quality insights, D06 features |
| Host service | Reviews attributed to host service plus authoritative response/cancellation metrics from owners | Host profile, host coaching, bounded D06 quality |
| Guest booking conduct | Host-to-guest structured feedback tied to completed stays | Private guest feedback, support/risk context under approved policy |
| Reviewer reliability | Provenance/manipulation/calibration signals | Internal confidence/quality only; not public character judgment |

Never average these into one “trust score.” A clean property does not prove a host never cancels;
a dispute does not prove a guest is unsafe; a low rating does not prove fraud.

### Host reputation

Public host reputation may combine transparent guest ratings across attributed stays, response and
cancellation facts sourced from their owners, and tenure/verification facts with clear labels.
Each metric retains period, denominator, market/listing context, and source. D14 owns only review
components; it must not recalculate booking response, cancellation, or safety facts.

Host attribution follows the completed booking snapshot. Portfolio rollups must prevent one large
property from dominating unintentionally and must explain whether the number represents listings,
stays, or reviews. Co-host contribution is displayed only with lawful, explicit attribution.

### Host-to-guest feedback safeguards

Host-to-guest feedback has a higher risk of retaliation, discrimination, and circular exclusion.
Recommended initial policy:

- collect only structured, observable, booking-relevant dimensions with optional bounded text;
- reveal double-blind and prevent use before the booking completes;
- do not publish a global guest star score;
- do not allow a single host review or opaque aggregate to deny instant booking;
- show the guest their own feedback and provide report/appeal/correction routes;
- expose to another host only the minimum approved contextual information after a legitimate booking
  interaction and authorization check;
- route claimed safety/abuse to D15 for evidence-based decision rather than reputation shorthand;
- monitor disparities, retaliatory correlation, reciprocal rating pressure, and host-specific bias.

If request-to-book later uses guest history, D08/D15 must own the decision policy, reason, minimum
evidence, prohibited criteria, fallback, time bound, and appeal. D14 supplies only qualified facts.

### No irreversible global score

A contextual reputation projection records purpose, subject role, input families, period, minimum
evidence, output version, confidence, expiry/review time, and allowed consumers. Consequential
consumers cannot query an unconstrained generic `reputation_score`. They request a purpose-specific
view whose policy can return `INSUFFICIENT_EVIDENCE`.

Rehabilitation matters. Old evidence can decay for bounded decision support while remaining in
historical public views under retention rules. Superseding moderation or appeal outcomes propagate.
The system must not create permanent exclusion from stale, unappealable subjective feedback.

## Review integrity and manipulation resistance

Verified completion prevents unrelated reviews but not self-booking, collusion, purchased stays,
retaliation, coercion, review swapping, competitor sabotage, copied text, or account takeover. D15
owns investigation, confirmed manipulation labels, restrictions, and appeal. D14 supplies:

- exact booking/reviewer/reviewee/listing lineage and timestamps;
- review right, draft/submission/revision/reveal/publication history;
- rating/category/text similarity and translation provenance;
- reciprocal review timing and rating changes before reveal;
- helpful votes, reports, device/session references only where lawfully collected;
- host/reviewer relationship references and incentives/campaign exposure;
- aggregate influence and affected publication/profile versions.

D14 consumes only effective D15 outcomes. A raw anomaly score, one report, or suspicious similarity
does not silently remove a review or down-weight its public rating. Pending high-confidence risk may
quarantine under an explicit time-bounded policy with safe fallback and appeal.

The marketplace should not pay for positive reviews, condition credits on sentiment, or let hosts
selectively reward reviewers. If a neutral participation incentive is ever introduced, it must be
disclosed, independent of rating/content, funded and measured separately, resistant to farming, and
included in review provenance.

Review solicitation copy cannot ask only happy guests, suggest a score, threaten a consequence, or
hide the private/public boundary. Extortion allegations route to D15/D16 with preserved messages and
review revisions; agents do not delete criticism in exchange for settlement.

## Conceptual data model

### Current versus proposed records

The current `reviews` table is a useful coarse foundation but combines aggregate identity and
mutable current content. `published_at` and `updated_at` cannot reconstruct sealing, revisions,
moderation, or visibility intervals. The implementation should evolve through additive forward
migrations and compatibility views/adapters rather than editing migration `006`.

The target model is conceptual. Physical consolidation is acceptable in a modular monolith when it
preserves constraints, access separation, retention, and clear ownership.

### Policy and eligibility records

`review_policy_versions`

- stable policy/version ID, market, locale family, effective interval, status;
- eligible outcome/direction rules, window duration and deadline convention;
- double-blind/reveal rule, maximum moderation hold, edit/withdrawal/response rules;
- category schema, display/rounding/aggregate rule references;
- disclosure/consent copy version, approval, checksum, created/published times.

Published versions are immutable. Overlapping active intervals for the same policy scope are
rejected.

`review_cycles`

- cycle ID, booking ID and accepted booking revision/version;
- listing ID, guest ID, host attribution snapshot, market and listing timezone;
- source `StayCompleted` event/version and completion time;
- policy version, opens/deadline instants, local deadline representation;
- cycle state, reveal version/time/reason, optimistic version;
- creation/correction provenance and latest source version.

There is one active canonical cycle per completed booking lineage under the initial product model.

`review_rights`

- right ID, cycle/booking, direction, author ID, represented actor ID, subject IDs;
- state, opened/deadline/exercised/revoked times and reason;
- eligibility policy/source version, review ID, optimistic version;
- delegation snapshot and privacy/retention class.

Unique `(cycle_id, direction)` and `(booking_id, policy_lineage, direction)` constraints prevent
duplicates. The review ID is unique when populated.

### Review content and publication records

`review_records`

- review ID, right/cycle/booking/listing, direction, author/represented actor;
- current submitted revision ID, authoring state, created/submitted/withdrawn times;
- latest lifecycle version and retention/legal-hold markers.

Unique `right_id` and `(booking_id, direction)` constraints enforce one aggregate per direction.

`review_revisions`

- review ID and strictly increasing revision number;
- overall rating and rating schema version;
- original public text, original language/locale, detected language/confidence;
- structured public/private payload references;
- content digest, client submission ID/digest, author and acting-as provenance;
- created/received/committed times, superseded revision ID, correction reason;
- immutable disclosure/policy versions.

Revisions are insert-only. Large or sensitive private payloads may live in separately protected
encrypted records while the revision keeps a digest/reference.

`review_category_values`

- revision ID, category code/schema version, integer value or `NOT_APPLICABLE`;
- subject/target, public/private classification, optional structured reason;
- unique `(revision_id, category_code)` and rating-range checks.

`review_publications`

- publication ID, review/revision, reveal version, publication interval start/end;
- public derivative/redaction ID, publication/retraction reason;
- qualifying moderation decision/version and policy version;
- aggregate event ID, actor/system provenance, created time.

Only one current open publication interval may exist per review. Closing and restoring create
historical intervals rather than rewriting `published_at`.

`review_responses` and `review_response_revisions`

- parent published review, responding listing/host, acting representative, response state/deadline;
- immutable response text revisions/digests and exact moderation/publication records;
- unique active response per parent and response party under the recommended single-response model.

`review_private_feedback`

- revision/right, typed audience/purpose, encrypted payload/reference, retention, consent/legal basis;
- explicit exclusion from public aggregates and ordinary aspect processing.

`review_translations` and `review_media`

- exact source revision/digest, language or object/storage metadata, provider/tool version;
- quarantine/scan/moderation/visibility state, derivative references, confidence, supersession;
- access/retention/legal-hold metadata and integrity checks.

### Moderation and public interaction links

D15 should own generic content items, moderation decisions, reports, and appeals. D14 stores only
foreign references and a small applied-decision projection needed for lifecycle correctness:

`review_moderation_applications`

- review/revision, D15 decision ID/version/digest, action, effective interval;
- applied publication effect, source event ID, application time/version;
- unique source-event processing key.

`review_helpful_votes`

- review ID, voter ID, state/version, created/updated time;
- unique `(review_id, voter_id)`; author-self-vote constraint enforced with service plus database
  denormalized author defense if required.

`review_interaction_events`

- append-only helpful/report/display interactions if operational volume is bounded;
- stable event/request ID, actor/session, review/publication version, position/surface, time;
- eventually moved to D19 analytical storage so click volume cannot harm booking/review writes.

### Rating and quality projections

`review_public_aggregates`

- subject type/ID, direction, dimension/category, aggregate rule version;
- exact rating sum/count, distribution counts, computed decimal, display value;
- publication epoch, input watermark, computed/verified times, stale status;
- unique current row per `(subject, direction, dimension, rule_version)`.

`review_aggregate_contributions`

- aggregate key/version, review/publication ID, value, inclusion interval, source event;
- unique contribution identity for audit, delta idempotency, and rebuild comparison;
- may be generated only for debugging/reconciliation if source queries are sufficient at measured launch scale.

`listing_quality_profiles` and `host_review_profiles`

- subject/profile version, posterior/uncertainty fields, evidence counts, window;
- input publication watermark, aggregate/taxonomy/model versions, computed/expiry time;
- rebuild status and source manifest/digest.

These are derived projections, never the source of original ratings.

### Aspect and reviewer evidence records

`aspect_taxonomy_versions` and `aspect_definitions`

- taxonomy ID/version/status/effective interval/checksum;
- stable aspect code, group, definition, allowed targets, sensitive-use tier;
- localized label, examples, successor/deprecation relation, approvals.

`review_extraction_runs`

- run ID, source revision/digest, taxonomy/extractor/model/prompt/provider/config versions;
- input disclosure/redaction version, state, attempt/fencing token, started/completed times;
- output digest, validation summary, error class, cost/latency metadata;
- unique successful output per canonical extraction identity.

`review_aspect_mentions`

- extraction run, review/revision, aspect/target/source type;
- source span or structured category reference;
- sentiment/intensity/negation/qualifier and confidence;
- language/translation provenance, human validation/supersession;
- created time and qualified inclusion state.

`aspect_profile_versions` and `aspect_profile_values`

- listing/host subject, profile/taxonomy/aggregation versions;
- aspect/target, counts, effective evidence, sums, posterior/interval/confidence;
- recent/all-time/trend values, watermark, supporting review manifest/digest;
- status, computed time, superseded profile.

`reviewer_attention_profiles` and `reviewer_attention_values`

- reviewer/profile version, purpose, source window, taxonomy/aspect;
- distinct stay count, attention, sentiment evidence, confidence, recency;
- input manifest/watermark, opt-out/deletion status, expiry, computed time.

The data shared with D06 is a minimized view, not direct access to raw review text.

### Reputation-purpose records

`reputation_policy_versions`

- approved purpose code, actor/subject role, allowed input families, minimum evidence;
- formula/rule version, confidence/expiry, consumers, explanation and appeal requirements;
- prohibited attributes/proxies, fairness release evidence, owner and effective interval.

`contextual_reputation_views`

- subject, purpose, context key, policy/input versions, output category/value/confidence;
- evidence window/count/manifest digest, computed/expires/review-at times;
- `INSUFFICIENT_EVIDENCE` reason and supersession.

This is not a generic score table. Consumer authorization must include an approved purpose.

### Reliability, audit, and governance records

`review_command_idempotency`

- actor, operation, scoped key, canonical request digest, result resource/version/status;
- reservation/expiry time and response reference; unique scoped key.

`review_outbox_events` and `review_inbox_receipts`

- event identity/version, aggregate/version, payload reference, correlation/causation;
- publish/consume attempts, completion, error, and deduplication identity.

`review_jobs`

- job type/subject/version, state, not-before/deadline, attempt count;
- lease owner/fencing token/lease expiry, last error, next retry, dead-letter reason.

`review_audit_events`

- actor/delegation, action, resource and before/after semantic references;
- reason, policy/authority version, request/correlation ID, time, result;
- purpose-bound protected access event where applicable.

### Constraints and indexes

At minimum enforce:

- one cycle per canonical booking lineage and one right per cycle/direction;
- one review per right and one review per booking/direction;
- valid author/represented actor/subject shape by direction;
- immutable, monotonic unique `(review_id, revision_number)`;
- one submitted current revision belonging to the same review;
- rating/category ranges and schema membership;
- deadline after open time and publication not before cycle reveal;
- one open publication interval per review and non-overlapping intervals;
- one active helpful vote per review/voter and one response per approved scope;
- unique moderation/event/idempotency/extraction processing keys;
- non-negative sums/counts and distribution sum equal to rating count;
- profile values bounded to declared ranges and tied to immutable versions;
- optimistic versions non-negative.

Important indexes include:

- rights/cycles by booking, actor, state, and deadline;
- review lists by listing plus current publication time/ID for stable pagination;
- reviews by author/reviewee and direction under access policy;
- current publication/moderation application by review/revision;
- due cycles, reminders, quarantines, extraction jobs, and stale projections by partial indexes;
- aggregate/profile key and watermark;
- aspect mention by subject/aspect/version and source revision;
- reports/appeals through D15-owned queue indexes;
- audit by resource/time and actor/time under restricted access.

### Migration, backfill, and deployment

Recommended additive deployment:

1. Add policy, cycle, right, revision, publication, category, idempotency, outbox, and audit tables.
2. Dual-read legacy `reviews` through a compatibility adapter; stop direct mutable writes.
3. Backfill each existing review as one immutable revision with
   `LEGACY_IMMEDIATE_PUBLICATION`, original `published_at` when present, and unknown fields explicitly
   null/`LEGACY_UNKNOWN`.
4. Validate one-per-direction and participant/booking integrity; quarantine exceptions for manual
   review rather than fabricating eligibility.
5. Build shadow public aggregates from source publications and compare with any existing counters.
6. Switch writes to rights/revisions/publication and reads to the verified aggregate projection.
7. Add D15 integration and double-blind policy for new cycles only; do not retroactively seal
   already public legacy reviews.
8. Add taxonomy/extraction/profile tables and shadow backfill with rate/cost controls.
9. Retire compatibility paths only after reconciliation, rollback drills, privacy checks, and audit
   export succeed.

Rollback disables new capabilities and returns reads to the last compatible verified projection; it
does not delete new historical records or reverse already revealed reviews.

## Service boundaries

Logical modules may remain in one Spring Boot deployment:

### `ReviewPolicyService`

Resolves immutable effective-dated eligibility, deadline, disclosure, category, edit, reveal,
response, and aggregate policy. It validates publishing and never mutates an active version.

### `ReviewEligibilityService`

Consumes Booking outcomes, canonicalizes lineage/participants, and idempotently creates, revokes, or
corrects cycles and directional rights. It cannot decide Booking completion.

### `ReviewAuthoringService`

Authorizes draft/submission/edit/withdrawal commands, validates schema, stores immutable revisions,
enforces one-per-direction, and publishes after-commit facts.

### `ReviewPublicationService`

Owns sealed/reveal state, composes lifecycle with effective D15 decisions, creates publication
intervals, and emits exact inclusion/retraction events. It does not classify content.

### `ReviewResponseService`

Authorizes and versions a bounded public host response. It routes every revision through D15 and
cannot change the parent review.

### `ReviewModerationAdapter`

Submits exact revisions to D15, consumes versioned decisions, deduplicates outcomes, and applies
visibility effects. D15 remains the decision owner.

### `ReviewAggregationService`

Maintains transparent rating/category aggregates and confidence-aware quality projections from
qualified publication truth. It supports deterministic rebuild, diff, repair, and epoch promotion.

### `AspectTaxonomyService`

Validates and publishes immutable taxonomy versions, localization, mappings, use tiers, and release
approvals.

### `ReviewIntelligenceService`

Orchestrates extraction, validates untrusted outputs, stores immutable mention sets, computes
profiles, exposes evidence-qualified views, and supports reprocessing/kill switches.

### `ReviewerEvidenceService`

Builds minimized attention/calibration evidence and serves purpose-bound snapshots to D06. It
enforces opt-out/deletion propagation and does not choose ranking.

### `ContextualReputationService`

Evaluates approved purpose-specific views with minimum evidence, explanation, expiry, and access
controls. It does not enforce booking/account restrictions; D08/D15 own such decisions.

### `ReviewQueryService`

Serves public, author, host, and internal projections with field-level authorization, stable
pagination, translation selection, current visibility, and freshness metadata.

### Neighbor contracts

| Neighbor | Supplies to D14 | D14 supplies back |
| --- | --- | --- |
| Booking/D08 | Completed/corrected outcome, parties, listing, accepted revision | Review lifecycle facts; no booking mutation |
| D12/D13 | Stay evidence references and notification delivery | Reminder/publish intents and review-window facts |
| D15 | Moderation/manipulation decisions and restrictions | Exact content revision, reports, integrity signals, aggregate impact |
| D16 | Case/evidence references and authorized findings | Exact review/publication evidence and correction/moderation request path |
| Listing/D03 | Listing identity/version and authorized representatives | Qualified public review/quality projection; no publication command |
| Discovery/D06 | Purpose/feature contract and deletion controls | Versioned listing/aspect and reviewer-attention evidence |
| [Identity/D01](identity-accounts-and-access.md) | actor status, role, delegation, privacy state | Public review attribution and purpose-bound reputation facts |
| D19/D20 | governed experiment/model registry and point-in-time tooling | review events, labels, extraction evaluation and feature snapshots |

## API behavior

Routes are illustrative target contracts, not implemented endpoints.

### Guest and author operations

```http
GET    /api/v1/me/review-rights?state=OPEN
GET    /api/v1/bookings/{bookingId}/review-rights
PUT    /api/v1/review-rights/{rightId}/draft
POST   /api/v1/review-rights/{rightId}/submissions
GET    /api/v1/reviews/{reviewId}/author-view
POST   /api/v1/reviews/{reviewId}/withdrawals
POST   /api/v1/reviews/{reviewId}/reports
PUT    /api/v1/reviews/{reviewId}/helpful-vote
DELETE /api/v1/reviews/{reviewId}/helpful-vote
```

Submission requires authenticated right owner, `Idempotency-Key`, expected right/draft version,
category schema version, and server-validated payload. Example:

```json
{
  "expectedRightVersion": 2,
  "expectedDraftVersion": 5,
  "ratingSchemaVersion": "guest-listing-2026-01",
  "overallRating": 5,
  "categories": {
    "CLEANLINESS": 5,
    "ACCURACY": 4,
    "COMMUNICATION": 5,
    "CHECK_IN": 4,
    "LOCATION": 4,
    "VALUE": 5
  },
  "publicComment": "Very clean and the host replied quickly.",
  "privateFeedback": {
    "HOST_OPERATIONAL_NOTE": "A clearer lockbox photo would help."
  },
  "originalLocale": "en-GB"
}
```

The server derives booking/listing/author/direction/deadline and returns a safe capability view:

```json
{
  "reviewId": "...",
  "revision": 1,
  "authoringState": "SUBMITTED_FINAL",
  "publicState": "NOT_PUBLIC",
  "publicationMessageCode": "PUBLISH_AFTER_REVIEW_CYCLE_CLOSES",
  "submissionDeadlineAt": "2026-10-20T17:00:00Z",
  "mayEdit": true,
  "version": 3
}
```

It never returns whether the counterpart has submitted or is in moderation.

### Public operations

```http
GET /api/v1/listings/{listingId}/reviews?sort=NEWEST&cursor=...
GET /api/v1/listings/{listingId}/review-summary
GET /api/v1/reviews/{reviewId}?language=vi
GET /api/v1/hosts/{hostId}/review-summary
```

Public responses contain only currently qualified publication derivatives, approved author display,
rating/category values, public response, helpful count, translation label, publication time, and
summary evidence/freshness. They exclude booking ID, stay exact dates when disallowed, exact address,
private feedback, report/moderation internals, risk signals, reviewer preference/calibration, and
counterpart sealed state.

Pagination cursors bind listing/host, sort, filter, publication epoch, locale/translation mode, and
last stable `(published_at, review_id)` keys. Removal between pages omits hidden content; clients
must tolerate reduced page size. A changed epoch may return `REVIEW_CURSOR_STALE` and a restart
cursor rather than duplicate/reveal content.

### Host operations

```http
GET  /api/v1/host/listings/{listingId}/review-insights
POST /api/v1/host/reviews/{reviewId}/responses
POST /api/v1/host/review-intelligence/{outputId}/reports
GET  /api/v1/host/reputation-summary
```

Host insight responses distinguish raw public average, category aggregate, inferred aspect,
confidence/evidence, trend, and source version. They do not reveal private reviewer profile,
moderation thresholds, reporter identity, or another host's protected data. Listing-scoped co-host
permission is checked on every read/write.

### Moderation, support, and internal operations

```http
POST /internal/v1/review-moderation-decisions:apply
POST /internal/v1/review-booking-outcomes:consume
POST /internal/v1/review-cycles/{cycleId}:reveal
POST /internal/v1/review-aggregates/{subjectType}/{subjectId}:rebuild
POST /internal/v1/review-extractions/{revisionId}:run
GET  /internal/v1/reviews/{reviewId}/evidence-manifest
```

Internal commands require workload identity, purpose, scoped authority, expected versions,
idempotency, correlation/causation, and audit reason. Support gets authorized read/link/request
capabilities; it cannot call an endpoint that directly rewrites rating or moderation truth.

### Versioning and response semantics

- `ETag` or explicit `version` protects mutable command aggregates such as drafts and helpful votes.
- `Idempotency-Key` plus actor/operation/resource scope protects submissions, withdrawals,
  responses, reports, and privileged commands.
- Same key plus same canonical digest returns the original status/body; same key with different
  digest returns a conflict.
- Public APIs may be eventually consistent for counts/aspects and expose projection
  `computedAt`, `sourceWatermark`, and `stale` where operationally useful.
- Author/right reads requiring strong state query the source aggregate, not a public cache.
- All client-provided identity, visibility, aggregate, model score, and lifecycle transition fields
  are ignored/rejected.

### Error semantics

| HTTP | Code | Meaning and retry behavior |
| --- | --- | --- |
| `400` | `REVIEW_REQUEST_INVALID` | Malformed/unsupported payload; fix before retry |
| `401` | `AUTHENTICATION_REQUIRED` | Authenticate; no resource detail disclosed |
| `403` | `REVIEW_ACTION_NOT_ALLOWED` | Actor lacks right/scope; do not reveal counterpart or moderation state |
| `404` | `REVIEW_RESOURCE_NOT_FOUND` | Opaque missing/unauthorized resource |
| `409` | `REVIEW_ALREADY_SUBMITTED` | Existing direction won; fetch safe author view |
| `409` | `REVIEW_VERSION_CONFLICT` | Refresh own resource and retry deliberately |
| `409` | `REVIEW_IDEMPOTENCY_CONFLICT` | Key reused for different canonical request |
| `409` | `REVIEW_EDIT_WINDOW_CLOSED` | Final revision cannot change under snapshotted policy |
| `409` | `REVIEW_CURSOR_STALE` | Restart public pagination with returned safe cursor |
| `410` | `REVIEW_WINDOW_EXPIRED` | Submission right is no longer usable |
| `413` | `REVIEW_CONTENT_TOO_LARGE` | Reduce text/media size |
| `422` | `REVIEW_SCHEMA_MISMATCH` | Use right's category schema and applicable fields |
| `422` | `REVIEW_RATING_INVALID` | Rating/category out of range or incompatible with direction |
| `423` | `REVIEW_REVISION_UNDER_REVIEW` | Own content temporarily quarantined; retry only as instructed |
| `429` | `REVIEW_RATE_LIMITED` | Retry after safe server hint; urgent safety intake stays available |
| `503` | `REVIEW_DEPENDENCY_UNAVAILABLE` | Retryable when authoritative policy/source cannot be proven |

Generic errors and opaque identifiers prevent enumeration of booking participation, sealed review
activity, reports, restrictions, or moderation categories. A public hidden review returns the same
opaque not-found behavior as a nonexistent one unless the caller is its authorized author.

## Event contracts

Events are committed past-tense facts. Every envelope includes event ID, schema name/version,
occurred time, producer, aggregate ID/version, correlation/causation ID, actor/workload where
appropriate, market, and privacy classification. Payloads carry stable identities and minimal
facts; consumers fetch authorized detail rather than receiving raw review/private text broadly.

### D14-produced events

| Event | Meaning | Typical consumers |
| --- | --- | --- |
| `ReviewCycleOpened` | Rights/deadline/policy snapshot committed for a completed booking | D12 reminders, audit |
| `ReviewRightRevoked` | Booking correction invalidated a right | Publication, D12, operations |
| `ReviewDraftSaved` | Optional durable draft version committed | Author experience only; usually no broad event |
| `ReviewSubmitted` | Exact final revision committed while eligible | D15 moderation, D12 receipt, analytics |
| `ReviewRevisionReplaced` | Sealed final revision superseded under policy | D15, publication |
| `ReviewCycleRevealed` | One reveal version/deadline reason committed | Publication, D12, analytics |
| `ReviewPublished` | Exact qualified revision became public | Aggregates, aspects, listing/search, D12 |
| `ReviewPublicationRetracted` | Public interval closed for explicit reason | Aggregates, aspects, search, audit |
| `ReviewPublicationRestored` | Qualified revision became public again | Aggregates, aspects, search |
| `ReviewWithdrawn` | Author withdrawal committed | Publication, aggregates, D12 |
| `ReviewResponsePublished` | Qualified host response became public | Query/search projection, D12 |
| `ReviewHelpfulVoteChanged` | Authenticated helpful state changed | Helpful projection, analytics |
| `ReviewAggregateUpdated` | Verified public aggregate epoch advanced | Listing, discovery, host tools |
| `ReviewAggregateRebuilt` | Full rebuild verified/promoted an epoch | Operations, listing/search |
| `ReviewAspectExtractionCompleted` | Validated immutable mention set created | Aspect aggregation, evaluation |
| `AspectProfileUpdated` | Subject/aspect profile version promoted | Discovery, host insights, risk under policy |
| `ReviewerAttentionEvidenceUpdated` | Minimized preference evidence version changed | D06 only under purpose contract |
| `ContextualReputationUpdated` | Approved purpose-specific view changed | Authorized consumer only |

`ReviewPublished` identifies review, exact revision, listing/host attribution, direction,
publication/reveal versions, category schema, moderation decision reference, and publication time.
It should not contain raw public text unless every consumer is authorized and there is a measured
need; a content reference/digest is safer.

### Consumed facts

| Producer fact | D14 reaction |
| --- | --- |
| `StayCompleted` | Idempotently create cycle/rights from exact booking revision and policy |
| Booking completion correction/supersession | Re-evaluate right through explicit correction path |
| D15 `ContentModerationDecisionMade` | Apply exact-revision action and re-evaluate reveal/publication |
| D15 `ModerationDecisionSuperseded` | Apply restoration/removal prospectively and rebuild projections |
| D15 `ManipulationLabelAdjudicated` | Exclude/include only according to effective approved policy |
| Identity privacy/account event | Apply attribution/access/deletion policy without deleting contract proof |
| Listing lineage/ownership correction | Update only approved attribution projection; preserve review source |
| D12 delivery result | Update notification projection only; never eligibility/deadline |
| Taxonomy/model version promoted | Schedule bounded shadow/reprocessing jobs; never mutate source review |

Consumers deduplicate by producer/event ID and store the highest applied aggregate/source version.
Out-of-order events are either commutative, deferred until predecessors arrive, or trigger source
read/rebuild. A later version never gets overwritten by an earlier retry.

### Outbox, inbox, replay, and deletion

D14 writes state and outbox rows in the same transaction. Dispatch is at least once. Each consumer
uses an inbox/unique effect key and may replay from source. Large content is referenced rather than
placed on a bus. Event schemas are backward compatible within a major version and validated in
contract tests.

Privacy deletion emits a tombstone/pseudonymization fact keyed by internal subject identity. It does
not broadcast deleted text. Consumers prove propagation through status/watermark reconciliation.
Replaying public events rechecks current authorized visibility where necessary so an old
`ReviewPublished` cannot resurrect removed content in a rebuilt cache.

## Concurrency and idempotency

### Command identity

Canonical request digests use normalized operation, authenticated actor/represented principal,
right/review ID, expected version, rating schema, normalized structured fields, original content
digest, and attachment references. They exclude volatile headers and response-localized copy.

Suggested scopes:

| Operation | Idempotency scope | Replay result |
| --- | --- | --- |
| Open rights | booking lineage + completion event/version | Existing cycle/rights |
| Save draft | actor + right + client draft command | Same draft version |
| Submit review | actor + right + submission command | Same review/revision/status |
| Replace sealed revision | actor + review + edit command | Same replacement revision |
| Withdraw | actor + review + withdrawal command | Same withdrawal record |
| Public response | representative + review + response command | Same response/revision |
| Helpful vote | voter + review | Desired state under optimistic version |
| Apply moderation | D15 decision ID/version | Same applied effect |
| Reveal cycle | cycle + reveal reason/version | Same reveal epoch |
| Apply aggregate delta | source event + aggregate/rule | One contribution/effect |
| Extraction | revision digest + taxonomy + extractor config | Same validated output |

Same key and digest returns the original response even after client timeout. Same key with a
different digest is a hard conflict. In-progress reservations expire through controlled recovery;
they are not deleted blindly.

### Lock order

When a command requires multiple rows, use a consistent order:

1. idempotency reservation;
2. review cycle;
3. directional right ordered by direction;
4. review aggregate;
5. current revision/publication projection;
6. aggregate contribution/projection key.

Transactions remain short and never call D15, object storage, translation, AI, or D12. Aggregate
rebuild works in a new epoch and promotes with compare-and-set rather than locking every review.

### Important races

| Race | Required winner/behavior |
| --- | --- |
| Two submissions for one right | Unique right/review constraint permits one; idempotent same request replays |
| Submission versus deadline | Database-time policy check determines winner; no worker-local clock authority |
| First edit versus counterpart submission | Lock cycle/right; one transition closes edit capability without leaking why |
| Two reveal workers | Cycle version/unique reveal epoch permits one; loser replays |
| Reveal versus quarantine result | Compose under cycle/revision lock; no unqualified revision publishes |
| Maximum moderation hold versus approval | One publication interval per review; either path applies exact current decision once |
| Withdrawal versus publication | Locked review/version decides order; if publication wins first, withdrawal closes interval additively |
| Removal versus aggregate delta | Event identity/version and inclusion intervals converge; reconciliation verifies |
| Restore versus newer removal | Highest effective D15 decision version wins; stale restore cannot reopen |
| Helpful vote versus review removal | Vote may persist privately but public count/query excludes hidden review |
| Booking correction versus submission | If correction commits first, reject; if review commits first, correction revokes/hides additively |
| Extraction versus revision replacement/removal | Output remains historical but cannot become current profile evidence |
| Taxonomy/model promotion versus rebuild | Separate immutable profile version; atomic promotion of completed compatible epoch |
| Privacy deletion versus cache refresh | Tombstone/version prevents older profile write from resurrecting data |

### Database and worker defenses

- unique booking/direction, right/review, revision number, reveal epoch, current publication,
  idempotency, event effect, helpful vote, and extraction-output constraints;
- foreign keys proving revision/publication belongs to the correct review/right/cycle;
- check constraints for rating, time interval, state shape, count/sum, confidence, and version ranges;
- optimistic versions on cycle/right/review/draft/current projections;
- `FOR UPDATE SKIP LOCKED` or equivalent bounded queue claims with lease/fencing tokens;
- retry with bounded exponential backoff and jitter for transient conflicts;
- dead-letter/manual review for structurally invalid or repeatedly failing work;
- periodic reconciliation for rights versus completions, publications versus moderation, aggregates
  versus source, and profiles versus watermark.

## Security, privacy, and access control

### Actor and resource authorization

- A guest can author only the `GUEST_TO_LISTING` right whose booking guest ID matches them.
- A host/co-host can author only an allowed `HOST_TO_GUEST` right with current valid booking-scoped
  delegation; acting-as identity is recorded.
- Authors can read their sealed content but never peer status/content before reveal.
- Public callers see only active qualified public derivatives.
- Host insight access requires listing/portfolio scope and excludes private reviewer intelligence.
- Moderators, support, quality, legal, and privacy operators have distinct read/action permissions,
  purpose, market/language scope, and time-bound access.
- High-impact restoration, legal removal, bulk export, retention override, or reputation-policy
  change uses step-up, maker-checker where appropriate, and immutable audit.

Generic `ADMIN` is not unrestricted review authority. Authorization is checked again at execution,
not inferred from who created a draft, report, or job.

### Data classification and minimization

Separate public review data, private feedback, sealed counterpart data, moderation evidence,
support/safety evidence, reviewer preference, device/network signals, and operational audit. Encrypt
sensitive data at rest and in transit; use purpose-bound keys/access where needed. Public IDs should
be opaque and must not reveal booking confirmation or sequential counts.

Do not place original sealed/private text, exact address, phone/email, payment data, authentication
secrets, report identity, model prompt input, or sensitive moderation category in ordinary logs,
metrics, traces, events, or analytics. Structured logs use IDs, versions, safe reason codes, sizes,
and digests.

### Sealed-content isolation

Double-blind confidentiality needs explicit tests and storage/query separation. Public indexes and
caches receive content only after publication. Notification templates cannot branch visibly on peer
submission. Internal tools show sealed peer content only to authorized moderation/safety roles for a
declared purpose; ordinary support and the counterpart do not.

Timing, response size, error code, edit capability copy, reminder cancellation, and public count
must not form side channels revealing whether the counterpart submitted.

### Abuse and adversarial input

Rate-limit drafts, submissions, edits, reports, votes, translation, and media by actor/resource/IP or
approved device signals without blocking safety intake. Defend against oversized Unicode,
homographs, invisible text, markup/script injection, malicious files, decompression bombs, URLs,
prompt injection, scraping, enumeration, and replay.

Content security policy, escaping, safe link rendering, isolated media processing, short-lived
authorized object links, anti-CSRF where relevant, and malware scanning are required. AI/tool
workers treat review text as data and have no credentials or tool authority beyond the job.

### Retention, deletion, and legal hold

Define retention separately for drafts, public revisions, superseded revisions, private feedback,
media, moderation evidence, reports/appeals, helpful events, derived profiles, model inputs, and
audit. Legal hold freezes eligible protected records with reason, authority, scope, start/review/end,
and audit; it does not automatically keep public visibility.

Privacy requests may delete or pseudonymize derived preference/intelligence and public attribution
while preserving minimum transactional/moderation evidence under approved legal basis. Deletion
propagates to search caches, translations, aspect profiles, feature/training stores, exports, and
backup-expiry workflows. Erasure cannot be implemented by clearing only the latest cache.

### Fairness and non-discrimination

Prohibit protected characteristics and unjustified proxies from aspect, reviewer, host, guest, and
reputation features. Language, nationality inference, name, neighborhood demographics, disability,
family status, religion, gender, age, or economic proxies require necessity/legal review and are not
general reputation inputs.

Evaluate submission access, moderation false positives, publication delay, removals/restorations,
translation/extraction quality, public aggregate impact, and contextual reputation outcomes across
lawfully reviewed markets/languages/accessibility groups. Small cohorts require privacy-preserving
handling. Fairness monitoring does not authorize collecting sensitive data without a basis.

## Observability and operations

### Business and quality metrics

- eligible cycles and rights by outcome/direction/market;
- invitation-to-draft, submission, paired-submission, reveal, and expiry rates;
- submission time distribution and neutral reminder effect;
- public review coverage per completed stay, listing, host, and market;
- overall/category distributions and missing-category rates;
- host-response and helpful-vote participation;
- author withdrawal, report, quarantine, removal, restoration, and appeal rates;
- review/response publication-delay distributions excluding/including moderation;
- aspect coverage, confidence, positive/negative/mixed distribution, and host correction rate;
- new-listing evidence coverage and concentration of review influence;
- host-to-guest disparity, reciprocity, retaliation indicators, and approved appeal outcomes;
- opt-out/deletion/correction rates for reviewer evidence and downstream propagation time.

Metrics distinguish requested, eligible, drafted, submitted, qualified, sealed, revealed,
published, hidden, included, and projected. “Review count” without lifecycle definition is not an
operational metric.

### Correctness and technical metrics

- `StayCompleted` to right-open lag, duplicate/invalid event rate, and unmatched bookings;
- due-cycle age, reveal retries/conflicts, and leaked-state test canaries;
- outbox/inbox lag, retry age, dead letters, and source-version gaps;
- moderation queue/application lag and exact-revision digest mismatch;
- public aggregate count/sum/source drift and repair frequency;
- publication-to-listing/search projection freshness;
- extraction queue age, error/timeout/invalid-schema/abstention rate, token/cost and provider share;
- aspect precision/recall/calibration/target error by approved slice and model version;
- profile rebuild duration, watermark lag, stale/missing rate, promotion/rollback;
- database latency, lock waits, conflict rate, hot listing/host keys, query plan/index health;
- unauthorized access denials, sealed-resource probes, bulk-read anomalies, redaction failures.

### Initial service-level objective candidates

After measuring a baseline, define targets such as:

- 99.99% of eligible `StayCompleted` facts create exactly one cycle/right set within the agreed lag;
- 99.9% of due qualified cycles reveal within five minutes of the authoritative condition;
- zero known counterpart-status/content disclosures before reveal;
- 99.9% of accepted submission retries return the original review outcome;
- 99.9% of publication/removal/restoration changes reach public aggregates within ten minutes;
- 100% of sampled public aggregates reproduce exact source count/sum;
- 99% of aspect work completes within the approved batch window, with no publication dependency;
- 100% of consequential moderation/reputation applications cite policy/evidence/version/actor.

Targets are product decisions, not promises in this design. Error budgets cannot justify privacy,
sealed-content, eligibility, or aggregate-correctness violations.

### Dashboards, alerts, queues, and runbooks

Dashboards cover lifecycle funnel, deadlines/reveal, moderation, publication/aggregate freshness,
aspect quality/cost, fairness, privacy propagation, and infrastructure. Alerts prioritize:

- any pre-reveal disclosure canary or unauthorized sealed read;
- duplicate review/right/publication or impossible state transition;
- due cycles/retractions stuck past SLO;
- aggregate negative counts, checksum drift, or public/source mismatch;
- stale D15 decisions applied to a newer digest;
- privacy tombstone not propagated before deadline;
- extraction quality regression, provider data leak risk, or cost runaway;
- unexplained market/language disparity in rejection/removal/delay.

Operational queues include eligibility exceptions, bounded quarantine, expired reveal jobs,
aggregate mismatches, failed privacy propagation, invalid legacy rows, extraction validation, and
intelligence-quality reports. Each item has owner, severity, SLA, evidence, allowed action, lease,
and escalation; operators repair through supported commands, never direct SQL edits.

Runbooks cover delayed completion events, incorrect right/window, accidental early reveal, D15
outage, leaked sealed content containment, wrong publication/removal, aggregate drift, wrong aspect
summary, provider/privacy incident, backlog/cost containment, legacy backfill exception, and model
rollback/reprocessing.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Booking completion source unavailable | Do not invent rights; retry and surface lag |
| Duplicate/out-of-order `StayCompleted` | Inbox/version dedupe; converge to latest authoritative lineage |
| Review policy unavailable at right creation | Use only provably applicable immutable cached version; otherwise retry/review |
| Submission commits but response is lost | Replay exact result by idempotency key |
| Deadline worker is late | Reveal using snapshotted deadline and database time; alert on lag |
| Two reveal workers race | One reveal epoch wins; loser returns existing outcome |
| D15 unavailable | Keep new content sealed/quarantined according to approved safe policy; never bypass required moderation |
| Moderation decision references wrong digest | Reject application, fetch/reconcile exact revision, alert |
| Moderation remains pending too long | Apply maximum-hold policy; never reveal quarantined content or hold peer indefinitely |
| Translation fails | Show qualified original with language label; retry optional translation |
| Media scan fails/unknown | Keep media quarantined; text lifecycle proceeds if policy permits |
| Notification provider fails | Preserve right/deadline; D12 retries; no implicit extension |
| Aggregate delta duplicates | Unique contribution/event effect makes retry a no-op |
| Aggregate events arrive out of order | Version-gate or mark stale and rebuild from source |
| Public aggregate is corrupt | Serve last verified epoch or temporarily omit; rebuild and disclose freshness |
| Extraction provider times out | Publish review normally; retry/abstain and use structured-rating fallback |
| Extraction returns invalid taxonomy/span | Reject output, record validation error, never serve it |
| Aspect profile mixes incompatible versions | Block promotion and rebuild one compatible epoch |
| Search consumes stale profile | D06 uses bounded stale/default prior; availability/booking unaffected |
| Privacy deletion races profile rebuild | Tombstone/version wins; discard older output and re-run deletion propagation |
| Restore arrives after newer removal | Apply only highest effective D15 decision version |
| Helpful-vote store unavailable | Omit/queue vote; never affect review visibility or rating |
| Operator/model publishes wrong summary | Kill derived surface, retain reviews, supersede/rebuild and audit |
| Catastrophic review service outage | Listing/search/booking continue without new review features; no fabricated scores |

No compensation erases an already observed historical fact. Recovery creates a correction,
retraction, restoration, superseding profile, or replayable projection.

## Testing and verification

### Deterministic policy and lifecycle tests

- completed, corrected, cancelled, no-show, early-departure, relocation, and legacy eligibility;
- exact market/timezone window resolution including daylight-saving gaps/overlaps;
- both-submit, one-submit, none-submit, deadline, quarantine, maximum-hold, and restore reveal paths;
- draft/edit cutoff, author withdrawal, public response deadline, and safe capability copy;
- every allowed/forbidden right, cycle, authoring, moderation, publication, and intelligence state
  transition;
- direction/participant/delegation and category applicability;
- counterpart status never observable through response shape, error, timing class, reminder, or count.

Use a deterministic injected clock and immutable policy fixtures. Exhaustively compare state-machine
transitions against a declarative transition table.

### Database, concurrency, and property tests

- two users/requests cannot create duplicate booking/direction reviews;
- submission versus deadline, edit versus peer submission, reveal versus moderation, publication
  versus withdrawal, remove versus restore, and deletion versus rebuild races;
- uniqueness, foreign-key, interval, rating, category, monotonic revision, count/sum, and version
  constraints on real PostgreSQL;
- retry after commit returns identical resources and different digest conflicts;
- aggregate property: count equals distribution sum; exact sum/mean equals qualified source;
- remove then restore produces the expected new epoch without duplicate contribution;
- full rebuild equals incremental result for arbitrary event order/duplication;
- pagination remains stable or returns explicit stale cursor under publication changes.

### Moderation, provider, and contract tests

- exact revision/digest round-trip with every D15 outcome and supersession;
- D15/Booking/D12/D06/D16 event schema compatibility and authorization;
- outbox commit, dispatcher retry, inbox deduplication, replay, and missing predecessor recovery;
- translation/extraction/storage provider timeout, throttling, malformed response, changed contract,
  duplicate callback, and partial outage;
- media byte validation, malware quarantine, metadata stripping, and expired URL;
- provider data-minimization tests prove sealed/private fields are not transmitted without approval.

### Aggregate and intelligence tests

- exact rating rounding and zero/missing-category behavior;
- priors, evidence thresholds, confidence bounds, decay, late event, and trend significance;
- no review contributes more than once and verbose text respects per-review cap;
- negation, contrast, mixed sentiment, sarcasm/idiom samples, wrong target, external condition,
  multilingual and translation error;
- aspect taxonomy upgrade, incompatible-version prevention, shadow comparison, rollback, and
  reprocessing reproducibility;
- removal/redaction/deletion invalidates source span, summary, profile, cache, and downstream feature;
- summary template always cites sufficient currently visible evidence and abstains otherwise;
- point-in-time reviewer evidence contains no future stay/review or private prohibited fields.

### Security, privacy, abuse, and fairness tests

- cross-user/cross-host rights, sealed review reads, report identity, private feedback, and internal
  evidence access are denied;
- co-host delegation expiry/revocation and acting-as audit;
- enumeration, scraping, rate-limit evasion, XSS/HTML, Unicode, malicious URL/file, prompt injection,
  decompression bomb, and replay;
- log/event/metric/trace/export redaction and short-lived media authorization;
- retention expiry, legal hold, erasure/pseudonymization, backup and downstream deletion proofs;
- collusion, self-booking, reciprocal pressure, rating retaliation, copied reviews, report brigading,
  helpful-vote farms, and incentive abuse feed D15 without automatic guilt;
- moderation/extraction/publication delay and contextual reputation outcomes evaluated by approved
  language/market/accessibility/fairness slices;
- host-to-guest feedback cannot trigger an unapproved automatic booking denial.

### Recovery and operational acceptance

- rebuild all rights/publications/aggregates/aspects from source records in a clean projection;
- recover worker crash before/after provider call, output write, event publish, and epoch promotion;
- detect and repair wrong aggregate without direct database manipulation;
- contain accidental early publication, rotate/restrict access, identify viewers, notify/escalate
  according to incident policy, and prevent cache replay;
- roll back extractor/profile version without unpublishing reviews;
- restore from backup, replay post-backup events, apply privacy tombstones, and verify public truth;
- operators resolve every exception queue through documented, authorized, audited product tools.

## Caching, performance, and scaling

Authoring, right, cycle, and current visibility state use authoritative database reads for commands.
Never decide eligibility, editability, reveal, or moderation from a cache. Immutable policy/taxonomy
versions may be cached by version with checksum; applicability still uses authoritative scope/time.

Public review pages and summaries may use versioned caches keyed by subject, publication epoch,
sort/cursor, locale, translation mode, and audience. Removal/privacy invalidation is high priority;
reads perform a lightweight current-visibility guard where exposure risk requires it. Sealed/private
content is never placed in public cache namespaces.

Use keyset pagination on `(published_at DESC, review_id DESC)` rather than large offsets. Bound text,
category count, attachment count, public page size, report/vote rate, extraction passages, provider
tokens, and rebuild batch size. Separate interactive transactions from batch extraction and
reconciliation pools.

For hundreds or thousands of reviews per listing, PostgreSQL indexes and precomputed aggregates are
sufficient. Avoid calculating averages/aspects over raw text on every listing request. Hot listings
may contend on one aggregate row; use idempotent contribution batches or partitioned in-memory
combination before atomic epoch promotion only after measurement. Exact public source remains
rebuildable.

Move unbounded impression/helpful interaction history to D19 analytical storage before it competes
with booking/review transactions. Partition review/event tables, add read replicas, extract a search
index, introduce a vector store, or split services only after query plans, volume, retention,
contention, team ownership, and failure isolation justify the cost. Search indexes and vector stores
remain non-authoritative projections.

During degradation:

- preserve public qualified original reviews and verified raw aggregates;
- omit optional translations, helpful ordering, aspect summaries, and personalization features;
- queue bounded extraction/rebuild work with backpressure and priority;
- fail closed for new content whose required moderation cannot be proven;
- keep booking/search functional with no fabricated review score.

Capacity planning tracks completed stays/right creation, submission peaks after checkout/reminders,
review bytes/languages/media, public read fan-out, hot listings, moderation SLA, extraction
tokens/cost, rebuild throughput, retention/deletion load, and downstream profile fan-out.

## Appropriate use of AI

### Useful bounded applications

- language detection and multilingual translation with the original retained;
- aspect, target, sentiment, negation, qualifier, and source-span extraction;
- duplicate/copied-text and coordinated-pattern candidates for D15 review;
- clustering unknown phrases to propose taxonomy additions for human governance;
- evidence-grounded host summaries and operational suggestions from approved structured profiles;
- moderation/review-queue prioritization within deterministic severity/SLA floors;
- intelligence-quality anomaly detection and evaluation-set sampling;
- assistive drafting of a host response, clearly presented as a draft that the authorized host edits
  and submits through normal moderation.

Conventional classifiers, rules, embeddings, and large language models (LLMs) are options, not a
required architecture. Choose the least complex evaluated method. Extraction should be asynchronous,
schema constrained, version pinned, validated, confidence gated, replayable, and independently
disabled.

### Prerequisites and release controls

Before any production influence, require:

- lawful purpose, data/provider review, minimization, retention, deletion, and contract controls;
- governed taxonomy and adjudicated multilingual evaluation sets;
- source/content digest, prompt/model/provider/config version, output schema, and audit manifest;
- offline precision/recall/calibration/target and fairness gates per important slice;
- cost/latency/capacity limits, provider isolation, timeout/retry, and deterministic fallback;
- offline then shadow comparison, human review of high-impact errors, canary, monitoring, rollback,
  and kill switch;
- a process for user/host correction and propagation to evaluation labels.

Human corrections are not automatically ground truth; quality review distinguishes model error,
policy disagreement, subjective experience, and attempted manipulation. Training examples are
point-in-time and exclude future moderation/appeal information unless the prediction target calls
for it.

### Prohibited model authority

An AI/ML model may not author or alter a user's review, decide whether a stay completed, grant or
revoke a review right, move a deadline, reveal a cycle, publish/remove/restore content, decide a
moderation appeal, establish fraud/safety/liability, calculate the transparent public average,
impose a booking/account/listing restriction, approve a remedy, infer protected attributes, or
create an unsupported public factual claim.

Model output never bypasses D14 lifecycle or D15 policy. Low confidence, unsupported language,
provider failure, schema failure, or kill switch means abstain and fall back to structured ratings,
deterministic templates, no aspect claim, or authorized human review. Review publication and core
booking/search remain available according to deterministic policy.

## Target-release dependencies and completion gates

Dependencies 0–6 are cumulative release requirements. Contextual reputation beyond the supported
Vietnam journeys is a designed extension and must preserve evidence and fairness contracts.

### Dependency 0 — Product, policy, privacy, and fairness decisions

Approve reference journeys and architecture decision records (ADRs) for directions, eligible
outcomes, window/deadline, double-blind reveal, edit/withdrawal/response rules, category schema,
public display/rounding, moderation/removal/appeal, private feedback, host attribution,
host-to-guest use, retention, market/language scope, and operational ownership.

Define D08/D12/D13/D15/D16 contracts, abuse/threat model, data classification, authority matrix,
fairness review, runbooks, metrics, and launch SLOs. No production exit until adversarial examples
and a reference eligibility/publication matrix have accountable approval.

Exit criteria: every consequential choice has owner/date/context/alternatives/decision/consequences,
test vectors, rollout/revisit trigger, and a safe deterministic default.

### Dependency 1 — Verified rights and immutable authoring foundation

Add forward migrations for policy versions, cycles, rights, review records/revisions, normalized
categories, idempotency, outbox/inbox, and audit. Consume authoritative `StayCompleted`, authorize
exact participants, enforce one booking/direction, and implement draft/submission with no public
release. Backfill existing reviews with honest legacy provenance.

Compatibility: current `reviews` reads remain available through an adapter; direct mutable writes
are disabled only after parity/reconciliation. No legacy review is retroactively sealed.

Exit criteria: all eligible reference stays create exactly one right set, unrelated/late/duplicate
submissions fail safely, revisions are immutable, retries replay, and legacy exceptions are
measured/queued.

### Dependency 2 — Double-blind publication and governed moderation

Implement cycle state, effective-dated deadline, paired/deadline reveal, neutral reminders through
D12, exact-revision D15 moderation, bounded quarantine, publication intervals, author withdrawal,
public listing review pages, report intake, and operational queues.

Run shadow reveal against test and legacy-like cases before enabling cohorts. Use a feature flag by
market/new review cycle; disabling it stops new publication while preserving submitted content and
safe manual recovery.

Exit criteria: no pre-reveal counterpart leakage in security tests, qualified cycles publish once,
D15 decisions apply to exact digests, stuck cycles reconcile, and removal/restoration is additive.

### Dependency 3 — Rebuildable ratings, categories, and public responses

Add exact public sum/count/distribution projections, category aggregates, stable pagination,
verified epoch rebuild, listing/host attribution, public host response, translation foundation, and
listing/host summary integration. Keep Bayesian/internal quality separate from public average.

Shadow-build aggregates, compare every count/sum against source publications, then canary public
reads. Maintain rollback to the last verified epoch and legacy adapter.

Exit criteria: sampled and full reconciliation produces zero unexplained drift; rounding/count copy
is approved; publication/removal/restoration reaches listing/search within SLO; operators can repair
without SQL.

### Dependency 4 — Aspect taxonomy and shadow verification

Approve taxonomy v1, sensitive-use tiers, multilingual labeled evaluation set, provider/privacy
contract, extraction schema, evidence thresholds, host correction flow, and kill switch. Add
extraction/mention/profile records and deterministic category-to-aspect evidence.

Run text extraction offline and shadow only. Compare model families and abstention, measure quality
by language/aspect/target, cap cost, and test removal/deletion propagation. No rank/public claim uses
shadow output.

Exit criteria: per-slice release thresholds, provenance/reproducibility, cost/capacity, security,
privacy, and replay gates pass; unsupported slices deterministically abstain.

### Dependency 5 — Evidence-qualified host and public intelligence

Canary high-confidence aspects in host insights, then approved public summaries and D06 baseline
features. Require source links, uncertainty language, recent/all-time comparison, mixed-evidence
handling, host report/correction, and rapid surface kill switch.

Compatibility: missing/stale profiles omit the feature; raw reviews/aggregates remain unchanged.
Roll back only the derived profile/surface version.

Exit criteria: production precision/complaint/disparity/drift meets thresholds, every summary cites
currently visible sufficient evidence, and removal/correction invalidates downstream output within
SLO.

### Dependency 6 — Reviewer attention and responsible personalization handoff

Add purpose-bound reviewer attention evidence, opt-out/correction/deletion, point-in-time feature
generation, and minimized D06 contract. Begin with rule-based confidence gates and A/B tests against
the non-personalized baseline. Do not add reviewer calibration until separate evidence supports it.

Exit criteria: D06 can improve completed-stay satisfaction without worse cancellation/refund/low-
review or fairness guardrails; explicit trip intent always wins; opt-out/deletion propagates and
fallback is reliable.

### Designed extension — Broader contextual reputation and measured scale

Only after policy/legal/fairness approval, introduce narrow host service rollups and carefully
bounded host-to-guest context. Add minimum evidence, purpose-specific access, explanation,
expiry/rehabilitation, appeal, disparity monitoring, and D15/D08 enforcement boundaries. Consider
helpful ordering, media, calibration, advanced models, storage partitioning, or service extraction
only from measured need.

Exit criteria: no generic global score exists; every consequential consumer is purpose-approved and
appealable; online/offline quality and fairness gates pass; scale changes demonstrate benefit over
their operational/consistency cost.

## Verification checklist

### Functional and lifecycle correctness

- [ ] Only exact verified participants of an eligible committed booking outcome receive rights.
- [ ] One booking lineage has at most one review per direction under database concurrency.
- [ ] Rights store policy, category schema, attribution, timezone, deadline, and source versions.
- [ ] Draft, submission, revision, withdrawal, response, and publication transitions match policy.
- [ ] Original revisions are immutable and material edits create traceable replacements.
- [ ] Double-blind APIs, notifications, timing classes, counts, and errors do not leak peer activity.
- [ ] Both-submit and deadline reveal publish each qualified revision exactly once.
- [ ] Quarantine cannot expose content or hold a qualified peer indefinitely.
- [ ] Public pages never return sealed, rejected, removed, withdrawn, or unauthorized content.
- [ ] Booking correction/revocation changes visibility additively without erasing history.

### Aggregate and intelligence correctness

- [ ] Public overall/category count and sum rebuild exactly from qualified publication truth.
- [ ] Decimal calculation and display rounding are deterministic and documented.
- [ ] Raw public average is visibly distinct from Bayesian/recency/calibration quality projections.
- [ ] Missing category/aspect evidence is unknown, not zero or negative.
- [ ] Aspect mentions retain exact source, target, sentiment, span, language, confidence, and versions.
- [ ] One verbose review cannot count as multiple independent review evidence.
- [ ] Strength/weakness/mixed claims require configured evidence and uncertainty thresholds.
- [ ] Every public/host summary cites still-visible supporting evidence and abstains when insufficient.
- [ ] Taxonomy/model/profile changes create new versions and are reproducible/rollback-safe.
- [ ] Removal, restoration, redaction, correction, and deletion propagate to every derived projection.
- [ ] Reviewer attention stays separate from sentiment and from D06's final preference decision.

### Concurrency, retry, and recovery

- [ ] Submission/deadline, edit/peer-submit, reveal/moderation, withdrawal/publication, and
  remove/restore races have deterministic winners.
- [ ] Same idempotency key/digest replays; different digest conflicts.
- [ ] Outbox/inbox delivery tolerates duplicate, delayed, and out-of-order facts.
- [ ] Worker leases use fencing and recover crashes before/after external or local effects.
- [ ] Incremental aggregate/profile output equals clean full rebuild for tested histories.
- [ ] Corrupt/stale projections can be quarantined and replaced by a verified epoch.
- [ ] Backup restore plus event replay and privacy tombstones reconstruct current public truth.

### Security, privacy, fairness, and abuse

- [ ] Actor, direction, booking, listing, delegation, public/private, and purpose authorization is
  enforced at read and write time.
- [ ] Sealed/private content never enters public cache, broad event, log, trace, metric, or export.
- [ ] Exact report identity and protected moderation/support evidence remain restricted and audited.
- [ ] User text/media is escaped, scanned, bounded, and treated as untrusted model input.
- [ ] Rate limits and anti-abuse controls do not block urgent safety reporting.
- [ ] Retention, legal hold, pseudonymization, export, and deletion are defined per data class.
- [ ] Opt-out/deletion reaches translations, aspects, reviewer profiles, D06/D19/D20, and caches.
- [ ] D15 findings—not raw reports/scores—control manipulation exclusion or restriction.
- [ ] Host-to-guest feedback cannot become an irreversible global score or unapproved denial.
- [ ] Moderation, translation, extraction, publication delay, and reputation uses pass approved
  language/market/accessibility/fairness reviews.

### Operations, AI, and delivery

- [ ] Lifecycle, reveal, moderation, aggregate, extraction, privacy, fairness, and cost dashboards
  have accountable owners and alerts.
- [ ] Every exception queue has SLA, safe fallback, lease, audit, and supported repair command.
- [ ] Accidental early reveal and wrong removal/summary runbooks are exercised.
- [ ] AI releases have labeled evaluation, provenance, schema validation, shadow/canary, monitoring,
  fallback, rollback, and kill switch.
- [ ] No AI path owns eligibility, user text, reveal, moderation, public arithmetic, risk action, or
  remedy.
- [ ] Legacy migration/backfill uses explicit unknowns and reconciles before cutover.
- [ ] Feature flags stop new risky surfaces without corrupting existing review/publication history.
- [ ] New schema is introduced only through reviewed forward Liquibase migrations.
- [ ] Existing identity/listing/booking/review behavior remains compatible while D14 stages are off.
- [ ] README, master map, data-model notes, event catalog, API docs, runbooks, and ADRs are current.

## Decisions required before implementation

Each consequential decision should become an ADR with owner, decision date, context, alternatives,
chosen behavior, consequences, test vectors, rollout, observability, and revisit trigger.

1. Initial markets, languages, support/moderation hours, accessibility requirements, and capabilities
   intentionally unavailable outside launch scope.
2. Eligible Booking outcomes and evidence for ordinary reviews, including early departure, partial
   consumption, no-show, relocation, replacement booking, refund, and corrected completion.
3. Required review directions and whether guest feedback targets listing only, host service as well,
   or separately scored subjects.
4. Who may represent a host/co-host/operator, how acting-as identity is displayed, and what happens
   when delegation changes before submission.
5. Review-window start, duration (recommended initial 14 calendar days), local/instant deadline
   convention, outage remediation, and late-received command rule.
6. Double-blind reveal policy, maximum moderation hold, peer-delay behavior, and exact non-leakage
   product copy.
7. Draft retention/autosave and sealed edit rule; recommended close on peer final submission or
   deadline without revealing the cause.
8. Post-publication typo/privacy/substantive correction, author withdrawal, and whether ratings can
   remain when text is redacted/rejected.
9. Public response eligibility, representative authority, response window, edit/removal, length,
   and whether a guest can post a bounded follow-up.
10. Overall scale anchors and initial guest-to-listing/host-to-guest category schemas, required versus
    optional fields, `NOT_APPLICABLE`, schema evolution, and localization.
11. Public average inclusion, exact decimal/rounding/display count, minimum display threshold,
    histogram/privacy rules, and cross-surface consistency.
12. Listing versus host attribution across co-hosts, portfolios, ownership transfer, material
    relaunch, merged/split listings, and historical epochs.
13. Moderation categories, synchronous/asynchronous path, low-risk publish policy, quarantine SLA,
    safe fallback, removal/restoration reason disclosure, and D15 appeal deadline.
14. Treatment of personal-data-only mask versus confirmed manipulation, including whether valid
    rating/category components remain included.
15. Review relevance/factual-dispute/extortion policies and boundary among public response, D15
    moderation, D16 support, legal review, and financial settlement.
16. Private feedback types, recipients, operational uses, retention, analytics/ML eligibility, and
    dedicated safety-report routing.
17. Whether public attachments launch later, permitted types/count/size, storage/provider,
    moderation, sensitive evidence boundary, retention, and accessibility.
18. Translation build-versus-buy, supported languages, data residency/provider use, display labeling,
    correction, quality threshold, fallback, and cost ceiling.
19. Helpful-vote eligibility, anonymity, reversal, ordering influence, minimum volume, anti-abuse,
    privacy, and whether it is a designed extension.
20. Incentive policy; recommended default prohibits sentiment-contingent or selective rewards and
    requires disclosure/provenance for any neutral participation incentive.
21. Public review ordering and cursor epoch behavior, including newest versus helpful, critical
    recent evidence, removal between pages, and cache invalidation SLO.
22. Bayesian prior cohort/strength, confidence interval, minimum evidence, recency windows/half-life,
    trend threshold, and explicit separation from transparent public average.
23. Aspect taxonomy v1, target vocabulary, sensitive aspects such as safety perception, governance,
    localization, successor mapping, and release owner.
24. Extraction build-versus-buy/model family, approved providers, prompt/data boundary, quality gates
    by language/aspect/target, abstention, rate/cost limits, and kill switch.
25. Public and host-facing strength/weakness/mixed thresholds, evidence citations, wording,
    correction/appeal route, and liability review.
26. Reviewer attention evidence shared with D06, allowed context, minimum independent stays,
    confidence/decay, opt-out/correction/deletion, and prohibited inferences.
27. Whether reviewer calibration is ever allowed; recommended defer until it improves calibrated
    outcomes without suppressing critical/minority feedback.
28. Public host review profile composition and attribution, including which non-review metrics remain
    sourced from Booking/operations rather than D14.
29. Host-to-guest feedback audience/use, minimum evidence, reason/explanation, response/appeal,
    rehabilitation, fairness monitoring, and strict prohibition on a generic global score.
30. Any request-to-book/instant-book use of guest feedback and the D08/D15 enforcement owner;
    recommended default is no automatic denial from subjective review aggregates.
31. Confirmed manipulation label authority, pending-risk quarantine duration, aggregate exclusion
    effective time, appeal restoration, and downstream propagation.
32. Public author identity/pseudonymization, stay-date granularity, account deletion effect,
    reviewer-safety controls, scraping limits, and legal retention basis.
33. Role/permission matrix for authors, co-hosts, moderators, support, quality, legal, privacy, data,
    and model operators; maker-checker and break-glass thresholds.
34. SLOs and error budgets for right creation, reveal, moderation, aggregate propagation, extraction,
    removal/privacy invalidation, and downstream profile freshness.
35. Aggregate/event reconciliation cadence, verified epoch promotion, exception ownership, manual
    repair tooling, and disaster-recovery evidence.
36. Legacy review classification/backfill, invalid participant/duplicate rows, current host counters,
    compatibility window, cutover, and rollback.
37. Retention/legal-hold/deletion matrix for drafts, revisions, private feedback, translations,
    media, reports, moderation, votes, aspects, profiles, model inputs, audit, exports, and backups.
38. D19/D20 boundary for raw events, experiment assignments, training labels, feature registry,
    point-in-time datasets, model registry, and deletion propagation.
39. Measured thresholds that would justify a dedicated service, partitioning, read replicas,
    specialized search/vector infrastructure, or external review/translation/moderation provider.
40. Target-release gate and verification cohorts. Required foundation is verified immutable reviews,
    double-blind release, exact-revision moderation, transparent aggregates, and manual operations;
    intelligence and contextual reputation remain off until later gates pass.
