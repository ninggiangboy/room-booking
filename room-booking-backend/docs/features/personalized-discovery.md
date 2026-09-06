# Personalized search and discovery

## Purpose

This document defines how Room Booking should retrieve, understand, score, and rank listings for
an individual guest. It extends the geographic candidate retrieval described in
[`location-search.md`](location-search.md) with listing intelligence, guest preferences, trip
context, behavioral signals, review analysis, recommendation models, and responsible use of AI.

The central product question is not merely "which listings are close to this destination?" It is:

> Among the listings that can actually serve this trip, which ones are most likely to satisfy this
> guest, and why?

The answer is contextual. The same guest may prefer inexpensive accommodation on a solo trip,
quietness and a desk on a business trip, or more space and a kitchen on a family trip. A listing
also cannot be represented by one average rating: it may be excellent for cleanliness and host
communication while being weak for Wi-Fi, noise, or access to the city center.

This design therefore treats discovery as a match between three evolving profiles:

```text
listing intelligence x guest preference x current trip context
                              |
                              v
                    personal relevance score
```

Ranking is never allowed to override inventory correctness, explicit guest filters, security,
privacy, or marketplace policy.

Authoritative total-trip calculation, host proceeds, platform economics, tax, promotion funding,
financial accounting, and safe use of price/promotion models are defined separately in
[`dynamic-pricing-and-settlement.md`](dynamic-pricing-and-settlement.md). Discovery consumes its
price and quote outputs; it does not recreate monetary logic inside the ranker.

[Trust, safety, fraud, and content moderation](trust-safety-fraud-and-moderation.md) owns confirmed
manipulation labels, content/listing/review moderation, and protective restrictions. Discovery may
consume versioned eligible projections for retrieval quality and abuse-resistant learning, but it
must not turn an unreviewed risk score or user report into search authority.

[Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md) owns
verified review rights, original revisions, double-blind publication, transparent aggregates,
aspect evidence, and reviewer-attention projections. The review-analysis sections below describe
features consumed by discovery; D06 does not decide review eligibility, visibility, or aggregate
truth and must respect D14 evidence/version/freshness contracts.

[Data, experimentation, and machine-learning platform](data-experimentation-and-ml-platform.md)
owns the event taxonomy, experiment assignment/exposure, point-in-time training data, feature/model
registry, and prediction lifecycle used by discovery. D06 still owns hard eligibility, final result
order, diversity/policy constraints, explanation, and deterministic fallback.

## Status and dependencies

This is a target design, not a description of an implemented API. The current repository contains
the location-search schema and the transactional foundations for listings, calendars, bookings,
reviews, and favorites. It does not yet contain the search, behavior-event, feature-pipeline, or
personal-ranking Java implementation described here.

The recommended dependency order is:

1. Listing catalog and publication lifecycle.
2. Calendar, nightly pricing, and authoritative availability lookup.
3. Booking lifecycle and completed-stay truth.
4. Reviews and favorites.
5. Geographic candidate retrieval.
6. Search-event instrumentation and deterministic baseline ranking.
7. Review intelligence and guest/listing profiles.
8. Personalized learning-to-rank and controlled exploration.

Personalization must not delay a correct non-personalized search. Anonymous users and guests with
no history always need a high-quality fallback.

## Goals

- Return only listings that satisfy the requested trip and explicit filters.
- Understand the strengths, weaknesses, and suitable audiences of each listing.
- Infer a guest's stable preferences and current intent without requiring a long questionnaire.
- Personalize ranking using behavior, bookings, and review content while preserving guest control.
- Produce deterministic, explainable fallbacks when profiles or models are unavailable.
- Give new listings and new guests a fair cold-start experience.
- Measure completed stays and satisfaction, not clicks alone.
- Make model inputs, outputs, versions, and experiments auditable.
- Prevent review manipulation, popularity lock-in, discriminatory targeting, and privacy leaks.
- Allow the implementation to evolve from SQL and rules to machine-learned ranking without
  rewriting authoritative booking logic.

## Non-goals

- Letting an AI model decide whether a listing is available or bookable.
- Replacing explicit filters with inferred preferences.
- Generating or altering review text presented as if a guest wrote it.
- Using protected or highly sensitive personal characteristics for ranking.
- Guaranteeing that the first recommendation is objectively best for every guest.
- Building a real-time feature platform before traffic and operational needs justify it.
- Making sponsored placement indistinguishable from organic recommendations.
- Treating a model score as a permanent property of a listing or guest.

## Core principles

### Correctness before relevance

Publication status, date availability, occupancy, stay limits, currency, and explicitly selected
filters are hard constraints. They are evaluated before ranking. An unavailable listing with a
perfect recommendation score is not a candidate.

Search availability is still a snapshot. Booking creation must repeat validation, lock the
requested `availability_days`, calculate authoritative totals, and rely on the PostgreSQL overlap
constraint as the final concurrency guard.

### Explicit intent before inferred intent

If a guest selects `ENTIRE_PLACE`, a maximum total price, wheelchair access, or a specific amenity,
the system must honor it. A historical preference must never silently broaden or contradict an
explicit filter. Inference is mainly useful for ordering otherwise valid candidates.

### Total-trip value instead of headline nightly price

Price preference is based on the payable trip total in the requested currency, including nightly
prices and known mandatory fees. A low base price with high cleaning or service fees must not be
ranked as an inexpensive option. Relative price is evaluated against comparable candidates for the
same destination, dates, capacity, and room type.

### Attention is not sentiment

A guest who frequently mentions Wi-Fi demonstrates that Wi-Fi matters to them. Whether they prefer
a listing depends on the direction of those observations: repeated praise for strong Wi-Fi and
complaints about weak Wi-Fi provide more information than the mention count alone. Guest profiles
therefore keep separate values for aspect importance, preferred direction, and confidence.

### Recent intent and long-term preference are different

Long-term signals describe stable tendencies such as price sensitivity or preference for entire
homes. Session and trip signals describe the current task. Recent family-search behavior should be
able to temporarily outweigh a historical pattern of solo business travel without erasing it.

### Evidence and confidence travel together

Every aggregate score must expose its evidence count, recency, and confidence. A single five-star
review does not establish that a listing is cleaner than another listing with hundreds of strong
reviews. Missing evidence means unknown, not bad.

### Responsible personalization

Personalization must be visible, controllable, deletable, and limited to data needed for the
feature. Exact address, private messages, payment credentials, password/authentication data, and
unsupported sensitive inferences are never ranking features.

## Domain vocabulary

| Term | Meaning |
| --- | --- |
| Search request | One request with destination, dates, party, filters, locale, and session context |
| Candidate | A listing that passed retrieval and hard eligibility constraints |
| Impression | A candidate actually rendered where the guest could reasonably see it |
| Interaction | A click, detail view, map selection, favorite, share, or other measured action |
| Conversion | A defined downstream outcome such as booking creation, confirmation, or completion |
| Aspect | A controlled listing quality dimension such as cleanliness, quietness, Wi-Fi, or value |
| Aspect mention | A review passage associated with an aspect, sentiment, and extraction confidence |
| Listing intelligence profile | Versioned aggregate facts and inferred aspect qualities for a listing |
| Guest preference profile | Versioned long-term interests, sensitivities, and confidence for a guest |
| Trip context | The current destination, dates, party, budget, filters, locale, and session intent |
| Feature | A typed, versioned input used by a ranking rule or model |
| Label | A measured outcome used to train or evaluate a model |
| Organic rank | Rank determined without paid promotion |
| Exploration | Controlled exposure assigned to learn about uncertain candidates |

## End-to-end search flow

```text
request validation
        |
destination / radius / viewport retrieval
        |
hard listing, capacity, stay, amenity and availability filters
        |
authoritative trip-price calculation
        |
candidate feature enrichment
        |
baseline or personalized ranking
        |
diversity, fairness and exploration re-ranking
        |
stable pagination and public-field projection
        |
impression events and outcome attribution
```

Candidate retrieval and ranking must remain separate. Retrieval maximizes coverage of eligible
listings with predictable database cost. Ranking spends more computation on a bounded candidate
set. For a destination with hundreds of listings, PostgreSQL plus application-side scoring is
sufficient initially. Specialized search indexes, approximate nearest-neighbor retrieval, and a
dedicated online feature store are later scaling options, not MVP requirements.

## Eligibility and candidate retrieval

### Required request validation

A search request should validate:

- a selected internal `destinationId`, a valid radius center, or valid viewport bounds;
- `checkIn < checkOut`, maximum search horizon, and maximum stay length;
- a positive guest count within product limits;
- recognized ISO currency and locale values;
- non-negative price bounds with `minPrice <= maxPrice`;
- known property, room, amenity, accessibility, and policy filters;
- bounded page size and an opaque cursor belonging to the same normalized query.

### Hard candidate constraints

A candidate must satisfy all applicable conditions:

- listing status is `PUBLISHED`;
- host account remains eligible under account and marketplace policy;
- destination, radius, or viewport match follows the location-search design;
- `max_guests >= requested guests`;
- every stay date in `[checkIn, checkOut)` exists and is `AVAILABLE`;
- requested nights satisfy listing and per-day minimum-stay rules and listing maximum stay;
- no active booking conflicts with the requested stay;
- property type, room type, amenities, accessibility, instant-book, and other explicit filters match;
- server-calculated total is within explicit price limits;
- listing and price currency can be displayed or converted under a defined exchange-rate policy.

The search query may use efficient prechecks, but booking remains authoritative. Cached
availability and derived search documents must never create a booking without revalidation.

### Candidate-set size

The first implementation should retrieve a bounded set, for example the best 300 to 1,000 eligible
candidates by geographic/textual relevance, depending on measured query cost. It should not score
every listing in the marketplace for every request. The exact cap is a configuration and experiment
parameter, not an API contract.

If retrieval truncates a large eligible set, it should preserve several useful pools rather than
only the most popular listings:

- geographically/textually closest matches;
- high-quality established listings;
- strong value options;
- recently published listings with sufficient content;
- candidates related to explicit amenities or inferred high-confidence interests.

Union and deduplicate these pools before ranking. This improves recall and reduces popularity
lock-in.

## Signal taxonomy

### Listing facts

These are directly stored or deterministically calculated:

- property type, room type, capacity, bedrooms, beds, and bathrooms;
- normalized amenity codes and accessibility attributes;
- host rules, instant booking, cancellation policy, and check-in window;
- geographic area, distance, neighborhood, and nearby destination relationship;
- nightly and total-trip price, fees, discount, and currency;
- future availability density and minimum-stay restrictions;
- listing age, last meaningful update, image/content completeness;
- host response and cancellation metrics when those workflows exist.

### Listing outcome signals

- search impressions and detail-view rate;
- favorite and share rate;
- booking-start, booking-confirmation, and completed-stay rate;
- guest cancellation, host cancellation, expiration, no-show, refund, and dispute rate;
- repeat bookings and repeat views;
- overall and aspect ratings;
- review volume, recency, verified-stay status, and confidence;
- customer-support or safety enforcement outcomes, exposed only as appropriately governed aggregate
  features rather than raw case content.

Raw conversion rates are biased by historical position, price, availability, and traffic source.
They must be normalized or modeled with those exposure conditions. A listing shown at rank one is
not automatically better merely because it receives more clicks.

### Guest signals

- explicit filters and saved search preferences;
- impressions, skips, clicks, dwell time, map interactions, and listing comparisons;
- favorites, unfavorites, shares, and repeated views;
- booking attempts, confirmed/completed stays, cancellations, and repeat bookings;
- submitted overall/category ratings and review aspect mentions;
- relative prices, room types, amenities, locations, and trip shapes previously chosen;
- negative outcomes that indicate mismatch, with care not to treat unavoidable cancellations as
  preference signals;
- recent session activity and longer-term behavior stored separately.

A single passive event is weak evidence. Completed stays, explicit ratings, repeated choices, and
consistent patterns have greater weight. Dwell time must be capped and interpreted cautiously
because an open tab does not prove interest.

### Trip and session context

- destination hierarchy and distance/search mode;
- check-in/check-out, number of nights, season, weekend mix, and booking lead time;
- party size and, only when explicitly supplied and legitimately needed, party composition;
- requested currency, locale, device category, and coarse traffic channel;
- explicit filters, sort mode, map/list mode, and query refinements;
- session-level observed intent such as repeated selection of kitchens or workspaces;
- supply conditions such as local price distribution and eligible-candidate count.

Do not infer sensitive attributes from destination, language, names, review text, or behavioral
proxies. Device and channel features require a demonstrated product benefit and privacy review.

## Review intelligence

The authoritative review lifecycle, taxonomy, extraction, aggregation, correction, and contextual
reputation design is defined in
[Reviews, aspect intelligence, and reputation](review-reputation-and-aspect-intelligence.md). This
section focuses on how D06 consumes those outputs for discovery and remains non-authoritative.

### Why structured category ratings are not enough

The current `reviews` table stores overall rating plus optional `cleanliness`, `accuracy`,
`communication`, `location`, and `value` ratings for guest-to-listing reviews. These fields are
strong explicit signals, but free text can contain additional evidence:

- "The apartment was spotless, but the Wi-Fi dropped during video calls."
- "Quiet at night even though it is close to the old town."
- "The fifth-floor walk-up was difficult with luggage."
- "The photos were accurate, but the kitchen lacked basic cookware."

Review analysis converts those observations into controlled aspect mentions without replacing the
original review or claiming more certainty than the text supports.

### Versioned aspect taxonomy

Use a centrally governed, versioned taxonomy. A reasonable initial vocabulary is:

| Group | Aspects |
| --- | --- |
| Property quality | `CLEANLINESS`, `ACCURACY`, `COMFORT`, `SPACE`, `MAINTENANCE` |
| Environment | `LOCATION`, `QUIETNESS`, `SAFETY_PERCEPTION`, `VIEW`, `NEIGHBORHOOD` |
| Connectivity | `WIFI`, `MOBILE_SIGNAL`, `WORKSPACE` |
| Amenities | `KITCHEN`, `AIR_CONDITIONING`, `HEATING`, `PARKING`, `LAUNDRY`, `POOL` |
| Access | `CHECK_IN`, `ACCESSIBILITY`, `PUBLIC_TRANSPORT`, `STAIRS_ELEVATOR` |
| Service | `HOST_COMMUNICATION`, `HOST_RESPONSIVENESS` |
| Economics | `VALUE`, `FEE_TRANSPARENCY` |

The taxonomy should use stable codes, localized display names, definitions, positive/negative
examples, and an `active` state. New codes can be added, but old extracted records retain their
taxonomy and model version for reproducibility. Closely related phrases such as "spotless", "clean
bathroom", and "fresh linens" map to `CLEANLINESS`; their source excerpts are not used as separate
unbounded ranking features.

Safety-related text is particularly sensitive. `SAFETY_PERCEPTION` records a reviewer's stated
experience, not an objective crime score or a characteristic of neighborhood residents. It must
not be inferred from protected-class proxies or used to create discriminatory geographic outcomes.

### Aspect extraction pipeline

Review publication emits an after-commit event. An asynchronous worker then:

1. Verifies that the review is published, tied to a completed stay, and eligible for analysis.
2. Detects language while retaining the declared locale when available.
3. Applies moderation and removes data that must not be sent to an external model provider.
4. Splits text into sentences or bounded semantic passages.
5. Extracts zero or more taxonomy aspects per passage.
6. Assigns sentiment on a continuous scale such as `[-1, 1]` and extraction confidence `[0, 1]`.
7. Detects negation, contrast, subject, and whether the statement refers to the listing, host, or an
   external condition.
8. Stores normalized mentions with taxonomy, extractor, and model versions.
9. Recomputes affected listing aggregates and, when allowed, the reviewer's preference evidence.

Example normalized result:

```json
{
  "reviewId": "...",
  "taxonomyVersion": "2026-01",
  "extractorVersion": "review-aspect-v3",
  "mentions": [
    {
      "aspect": "CLEANLINESS",
      "sentiment": 0.92,
      "confidence": 0.96,
      "subject": "LISTING"
    },
    {
      "aspect": "WIFI",
      "sentiment": -0.78,
      "confidence": 0.91,
      "subject": "LISTING"
    }
  ]
}
```

The model output is untrusted input. Validate enum codes, numeric ranges, maximum mention count,
review ownership, and versions before storage. Invalid or low-confidence outputs are discarded or
queued for evaluation; they never block review publication.

### Aggregating listing aspect quality

Each eligible mention contributes evidence according to:

- extraction confidence;
- verified completed-stay status;
- review recency using a gradual, documented decay;
- explicit matching category rating when available;
- duplicate/spam and moderation confidence;
- reviewer calibration only after sufficient history.

Use a Bayesian or shrinkage estimate so low-volume listings remain close to a destination or
marketplace prior. One conceptual formulation is:

```text
weightedMean = sum(evidenceWeight_i * sentiment_i) / sum(evidenceWeight_i)

aspectQuality =
    (effectiveEvidence * weightedMean + priorStrength * marketPrior)
    / (effectiveEvidence + priorStrength)
```

Store at least `score`, `confidence`, `positive_count`, `negative_count`, `effective_evidence`,
`last_evidence_at`, and aggregation version. UI summaries should require minimum evidence and must
not present a weak inferred score as a confirmed defect.

Recent evidence can have more influence, but severe time decay can hide persistent issues. Keep
both recent-window and all-time aggregates, then let the ranker use their difference as an
improvement or deterioration signal.

### Reviewer calibration

Reviewers use rating scales differently. One guest may give almost every acceptable stay five
stars; another may reserve five stars for exceptional experiences. Calibration can estimate a
reviewer's systematic residual relative to comparable stays, but only after enough reviews.

Calibration must be bounded and used as a minor confidence adjustment. It must not erase a valid
minority complaint or punish a guest for being exacting. Raw ratings and text remain immutable,
while calibrated features are derived and versioned.

### Detecting listing strengths and weaknesses

An aspect can be reported as a strength when:

- quality exceeds a configured threshold;
- confidence and effective evidence exceed minimums;
- recent evidence is not materially worse than the historical aggregate;
- the result is not driven by a small group of suspiciously correlated reviewers.

Weaknesses need stricter disclosure because false negative labels harm hosts. Prefer language such
as "some recent guests mentioned weak Wi-Fi" over an absolute claim. Hosts should see evidence
counts, trend, and eligible review context, and must be able to report extraction errors without
removing legitimate reviews.

### Review-analysis quality controls

- Maintain a manually labeled multilingual evaluation set.
- Measure aspect precision/recall, sentiment error, negation error, and subject-attribution error.
- Evaluate important languages and listing types independently.
- Shadow new extractors against the current version before promotion.
- Reprocess historical reviews with explicit jobs; never silently mix incompatible versions.
- Sample high-impact positive and negative labels for human audit.
- Monitor aspect distribution shifts and unexplained country/language disparities.
- Retain the ability to rebuild all derived data from authoritative reviews.

## Listing intelligence profile

The listing profile is a versioned, rebuildable read model. It may contain:

```json
{
  "listingId": "...",
  "profileVersion": 17,
  "computedAt": "2026-09-06T10:00:00Z",
  "quality": {
    "smoothedOverallRating": 0.89,
    "completedStayCount": 241,
    "hostCancellationRate": 0.006
  },
  "aspects": {
    "CLEANLINESS": { "score": 0.93, "confidence": 0.96, "trend": 0.02 },
    "WIFI": { "score": 0.57, "confidence": 0.81, "trend": -0.08 },
    "QUIETNESS": { "score": 0.85, "confidence": 0.73, "trend": 0.04 }
  },
  "value": {
    "relativePricePercentile": 0.31,
    "feeTransparency": 0.91
  }
}
```

This profile must not duplicate authoritative mutable inventory. Availability and exact trip price
are request-time features because they change by date. Profile refresh occurs after relevant events
and through periodic reconciliation. Staleness limits depend on the feature: review aggregates may
tolerate minutes, whereas current trip price and availability cannot.

## Guest preference profile

### Profile dimensions

For every supported aspect or categorical preference, retain separate values:

- `importance`: how much the dimension appears to affect the guest;
- `direction` or desired level: positive, negative, or target range;
- `confidence`: quantity, quality, consistency, and recency of evidence;
- `longTermValue`: stable history;
- `recentValue`: recent sessions/trips;
- `updatedAt` and feature-generation version.

Example:

```json
{
  "guestId": "...",
  "price": {
    "relativePercentileTarget": 0.24,
    "importance": 0.87,
    "confidence": 0.82
  },
  "aspects": {
    "CLEANLINESS": { "importance": 0.94, "preferredScore": 0.90, "confidence": 0.88 },
    "WIFI": { "importance": 0.78, "preferredScore": 0.82, "confidence": 0.69 },
    "QUIETNESS": { "importance": 0.71, "preferredScore": 0.80, "confidence": 0.64 }
  },
  "roomTypes": {
    "ENTIRE_PLACE": 0.76,
    "PRIVATE_ROOM": 0.22
  }
}
```

### Inferring aspect importance

Evidence may include:

- explicit category ratings and free-text aspects in reviews written by the guest;
- consistently selecting, favoriting, booking, and positively rating listings strong in an aspect;
- avoiding or negatively rating stays weak in an aspect;
- repeated explicit filters, such as parking or a workspace;
- recent session comparisons between candidates that mainly differ on the aspect.

Review mention frequency alone is insufficient. A guest may mention cleanliness because it was
unexpectedly bad once, not because it dominates every trip. Evidence should accumulate across
independent stays and behaviors, with stronger weight for explicit, completed outcomes.

### Inferring price sensitivity

Never infer price preference from raw amounts across different countries and dates. Normalize each
observed choice against comparable available supply at decision time:

```text
relativePrice = chosenTripTotal percentile among comparable eligible candidates
```

Useful evidence includes repeated chosen percentiles, reaction to price changes, use of price
filters, abandonment after total-price display, and satisfaction with value. A guest can be
price-sensitive for solo trips and less sensitive for family trips, so preserve contextual segments
only after there is enough evidence.

### Negative feedback and missing data

- `unfavorite` is weaker than a review describing a problem.
- cancellation is not automatically negative preference evidence; reason and responsibility matter.
- a skipped result is weak evidence because it may not have been seen.
- absence of clicks on an aspect is not dislike.
- no history produces an unknown profile and invokes the anonymous fallback.
- old history gradually loses weight but remains available for rebuilding and auditing according to
  retention policy.

### User control

Guests should eventually be able to:

- see broad preference explanations, such as "you often choose highly rated cleanliness";
- correct or disable inferred preferences;
- clear recent search/recommendation history;
- opt out of personalized ranking while retaining functional search;
- request deletion/export according to account and legal policy.

Controls should operate on source behavior and derived profiles. Clearing only a cached profile is
not sufficient if the next batch job immediately reconstructs it from data that should have been
deleted.

## Matching guest, listing, and trip

### Aspect fit

A deterministic baseline can calculate:

```text
aspectFit = sum(
    guestImportance_a
    * guestConfidence_a
    * listingConfidence_a
    * compatibility(guestPreference_a, listingQuality_a)
) / effectiveWeight
```

Unknown listing aspects contribute neither positive nor negative evidence. They may reduce
confidence in the final explanation, but must not be treated as zero quality.

### Cross features

Cross features represent compatibility rather than isolated quality:

- guest price percentile preference x candidate relative total-price percentile;
- preferred aspect importance x listing aspect quality;
- historical room-type affinity x current party size;
- workspace/Wi-Fi preference x weekday business-trip context;
- kitchen/space affinity x long stay or larger party;
- quietness preference x recent listing quietness evidence;
- destination familiarity x distance from selected center;
- cancellation flexibility preference x candidate policy;
- prior interaction with the same listing or host.

These are examples, not permission to infer trip purpose or personal characteristics without
sufficient legitimate evidence.

## Ranking strategy

### Stage 1: deterministic baseline

Start with an explainable normalized score. An example, to be tuned rather than treated as a final
contract, is:

```text
baseScore =
    0.25 * listingQuality
  + 0.20 * explicitQueryRelevance
  + 0.15 * locationFit
  + 0.15 * totalPriceValue
  + 0.10 * reliability
  + 0.10 * freshnessAndUncertainty
  + 0.05 * stableTieBreakerComponent

personalizedScore =
    0.55 * baseScore
  + 0.30 * guestListingFit
  + 0.15 * tripSpecificFit
```

The anonymous fallback uses `baseScore`. Personalized contributions should be confidence-gated so
a weak profile cannot radically reorder strong results. Explicit `PRICE_LOW_TO_HIGH`,
`RATING_HIGH_TO_LOW`, `DISTANCE`, and `NEWEST` sort modes bypass the recommendation order while
still applying eligibility constraints and deterministic ties.

The stable final tie-breaker should be derived from listing ID and a bounded ranking epoch or search
seed. Plain random ordering breaks cursor pagination; a permanent fixed ordering prevents useful
rotation.

### Quality and reliability

`listingQuality` should use smoothed ratings and aspect confidence rather than raw averages.
`reliability` may use completed booking rate, host cancellation rate, listing accuracy, and policy
compliance. Do not double-count the same review evidence through several highly correlated terms
without validation.

### Stage 2: learning-to-rank

After event quality and traffic are sufficient, train a model on `guest x listing x trip` examples.
A gradient-boosted decision-tree learning-to-rank model is a pragmatic first choice because it:

- handles nonlinear numeric and categorical features;
- works with less data than deep recommenders;
- is fast for hundreds of enriched candidates;
- supports feature-importance and explanation tooling;
- can be trained and served independently of an LLM.

Training labels should reflect the funnel and marketplace outcome:

| Outcome | Example role |
| --- | --- |
| Visible impression with no interaction | weak negative / sampled non-conversion |
| Detail view or meaningful map interaction | weak positive |
| Favorite/share | medium positive |
| Booking started | stronger positive |
| Booking confirmed | strong positive |
| Stay completed | stronger long-term positive |
| High post-stay satisfaction | strongest quality signal |
| Host cancellation, substantiated mismatch, or poor review | negative outcome |

Do not train a single model to maximize clicks only. Attractive photos or misleading titles can
increase clicks while harming completed stays. A practical objective may combine calibrated
probabilities:

```text
expectedUtility =
    P(confirmed booking | impression)
    * P(completed stay | confirmed booking)
    * expectedSatisfaction
```

Business value can be a monitored metric, but undisclosed commission or host payment must not
silently dominate guest relevance. Any paid placement is labeled and governed separately.

### Position bias and training leakage

Observed behavior depends on the previous ranker. Correct for this with randomized or interleaved
exploration traffic, position propensity features/weights, and careful counterfactual evaluation.
Never use outcomes that occur after the prediction timestamp as training features for that row.
Train/validation/test splits should be time-based and should test generalization to new listings,
new guests, and new destinations.

### Advanced models

At much larger scale, a two-tower model can retrieve candidates by guest/context and listing
embeddings, followed by a richer ranker. Sequence models can represent recent session intent.
These add value only after event semantics, negative sampling, leakage controls, and online/offline
feature parity are mature. They do not replace geographic and inventory filtering.

## Re-ranking, diversity, and exploration

The raw score is followed by a policy-aware re-ranker:

- avoid long runs from the same host;
- reduce near-duplicate listings with almost identical attributes;
- preserve useful price, room-type, and neighborhood variety when scores are close;
- reserve bounded exposure for high-quality new or uncertain listings;
- enforce trust/safety and legal policies;
- insert sponsored results only in declared slots with clear labeling;
- maintain cursor stability within the ranking epoch.

Maximal Marginal Relevance is one possible diversity algorithm:

```text
nextCandidate = argmax(
    relevance(candidate)
    - diversityPenalty * similarity(candidate, alreadySelected)
)
```

Similarity can combine host, location, price band, room type, and amenity vectors. Diversity must
not violate explicit filters or introduce materially worse results merely to make the list look
varied.

Exploration can start with a small deterministic pool and later use a contextual bandit. It needs a
strict traffic budget, quality floor, safety eligibility, per-listing caps, and experiment logging.
It is not unrestricted randomness.

## Cold start

### New or anonymous guest

Use explicit request context, destination-level priors, total-price value, smoothed quality,
reliability, and diversity. Session actions can adjust ranking within the current session without
creating a durable profile unless policy permits.

Optional onboarding preferences should be few, concrete, and skippable: budget range, room type,
and high-level priorities such as cleanliness, quietness, workspace, or family amenities.

### New listing

Use content completeness, amenities, price competitiveness, host reliability where legitimately
transferable, location relevance, and bounded exploration. Do not fabricate review quality. A new
listing's aspect score is unknown and shrunk to the market prior until verified stays provide
evidence.

### New destination

Fall back from neighborhood priors to locality, administrative area, country, and finally global
priors. Record which fallback was used. Geographic hierarchy should not mix incomparable markets
when calculating relative price.

## Behavioral event contract

### Required events

An initial event vocabulary should include:

- `SEARCH_SUBMITTED`;
- `SEARCH_RESULTS_RETURNED`;
- `LISTING_IMPRESSION`;
- `LISTING_CLICKED`;
- `LISTING_VIEWED`;
- `MAP_MARKER_SELECTED`;
- `LISTING_FAVORITED` and `LISTING_UNFAVORITED`;
- `BOOKING_STARTED`, `BOOKING_CREATED`, `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, and
  `STAY_COMPLETED`;
- `REVIEW_SUBMITTED` and `REVIEW_PUBLISHED`.

### Common event fields

```json
{
  "eventId": "...",
  "eventType": "LISTING_IMPRESSION",
  "occurredAt": "2026-09-06T10:00:00Z",
  "schemaVersion": 1,
  "searchRequestId": "...",
  "sessionId": "...",
  "guestId": null,
  "listingId": "...",
  "position": 4,
  "page": 1,
  "rankerVersion": "baseline-v1",
  "experimentAssignments": {},
  "context": {
    "destinationId": "...",
    "checkIn": "2026-10-10",
    "checkOut": "2026-10-13",
    "guestCount": 2,
    "currency": "VND"
  }
}
```

An impression is emitted only when a listing is actually rendered in a visible result region, not
when it merely existed in a server response. Clients attach the server-issued `searchRequestId` and
ranking metadata. The server must deduplicate retried events by `eventId` and validate that listing
and request identifiers are plausible.

Do not place exact addresses, precise public coordinates, review text, access tokens, IP addresses,
or unrestricted user-agent strings in general ranking events. Apply event retention and
pseudonymization policies deliberately.

### Delivery and reconciliation

Transactional outcomes should publish through an after-commit event or transactional outbox so a
failed analytics sink never rolls back a booking. Client interaction events can use an idempotent
ingestion endpoint. Consumers must tolerate duplicates and out-of-order delivery.

Maintain periodic reconciliation from authoritative bookings and reviews. Analytics events help
attribution; they do not redefine booking or review truth.

## Conceptual derived data model

The exact schema belongs in a future forward migration. Do not modify applied migrations `002`,
`004`, `006`, or `010`. The likely logical records are:

| Record | Purpose |
| --- | --- |
| `discovery_events` | Append-only validated behavior and attribution facts for an MVP |
| `review_aspect_mentions` | Versioned normalized aspects extracted from published reviews |
| `listing_aspect_scores` | Rebuildable per-listing/per-aspect aggregates and confidence |
| `listing_discovery_profiles` | Versioned materialized listing-quality features |
| `guest_preference_features` | Per-guest preference values, confidence, and time windows |
| `ranking_model_versions` | Model identity, feature schema, status, timestamps, and artifact checksum |
| `ranking_exposures` | Rank, score/version, experiment, and candidate exposure needed for evaluation |

Large event volumes should eventually move to an analytical event store or warehouse. PostgreSQL
can remain the source for serving small derived profiles or an MVP event log, but unbounded raw
clickstream growth must not compete with booking transactions. Derived tables need `computed_at`,
input watermark, schema/model version, and rebuild procedures.

## Service boundaries

A modular-monolith implementation can use these conceptual components:

- `ListingCandidateService`: geographic and hard-constraint candidate retrieval;
- `TripPricingService`: authoritative comparable trip totals;
- `ListingFeatureService`: listing profile and request-time feature enrichment;
- `GuestPreferenceService`: durable and session preference lookup;
- `ReviewIntelligenceService`: extraction job orchestration and aggregate refresh;
- `RankingService`: baseline/model scoring with deterministic fallback;
- `DiscoveryRerankingService`: diversity, exploration, and policy constraints;
- `RecommendationExplanationService`: reason generation from approved structured features;
- `DiscoveryEventService`: validation, deduplication, and publication of behavior events.

Controllers remain thin. Ranking failures should degrade to the deterministic baseline; feature
pipeline or AI extraction failure must not make search or booking unavailable.

## API behavior

The main search shape can extend the location design:

```http
GET /api/v1/listings/search
    ?destinationId=...
    &checkIn=2026-10-10
    &checkOut=2026-10-13
    &guests=2
    &amenities=WIFI,KITCHEN
    &sort=RECOMMENDED
    &cursor=...
```

Authenticated and anonymous callers share eligibility rules. `RECOMMENDED` may be personalized only
for an authenticated guest who has not opted out and whose feature profile is available. The
response may include restrained explanation codes:

```json
{
  "listingId": "...",
  "tripTotal": { "amountMinor": 4200000, "currency": "VND" },
  "recommendation": {
    "personalized": true,
    "reasons": ["MATCHES_CLEANLINESS_PREFERENCE", "STRONG_VALUE_FOR_THIS_SEARCH"]
  }
}
```

Clients localize approved reason codes. Do not send raw model features, private behavioral history,
other users' attributes, or claims unsupported by confidence thresholds. Search responses must
continue to follow the location document's address-disclosure policy.

The cursor should bind normalized query hash, sort mode, ranking epoch/model version, experiment
assignment, and last stable sort keys. Changing filters starts a new search. If availability changes
between pages, duplicates should be prevented and invalid candidates omitted; total counts are
approximate unless the API explicitly guarantees otherwise.

## Recommendation explanations

Explanation is a separate policy layer over structured, confidence-qualified reasons. It should be:

- truthful: derived from features actually influential for this result;
- specific enough to help but not invasive;
- stable enough that small model changes do not create contradictory messages;
- careful about uncertainty and review volume;
- localized from controlled templates;
- independent from generative text in the critical response path.

Good examples:

- "Highly rated for cleanliness."
- "Usually within the price range you choose."
- "Guests often mention quiet nights."
- "Good value compared with similar stays for these dates."

Avoid:

- "We know you are traveling for work" when the guest did not say so;
- "This neighborhood is safe" based only on subjective review language;
- "Perfect for you" or other unjustified certainty;
- exposing a private inference such as family status, income, religion, health, or relationship.

## Appropriate use of AI

### Useful AI components

- multilingual aspect and sentiment extraction from published review text;
- semantic matching of free-text search intent to approved amenities/aspects;
- embeddings for content similarity and candidate retrieval at larger scale;
- learning-to-rank models trained on behavior and completed-stay outcomes;
- anomaly models for spam/manipulation detection, subject to separate trust review.

### Where an LLM should not be authoritative

- availability, price, capacity, stay-rule, payment, or booking decisions;
- review eligibility and publication policy;
- exact-address disclosure;
- generation of numeric listing scores without stored evidence and calibration;
- real-time unvalidated output directly controlling rank or user-facing claims;
- inference of sensitive personal attributes.

LLM extraction should be asynchronous, schema-constrained, version-pinned, retryable, and cached as
derived data. Prompts, provider, model version, safety configuration, and evaluation results are
part of the release artifact. A deterministic or traditional classifier fallback is desirable for
cost control and provider outages.

## Privacy, security, and governance

- Establish a documented lawful/product purpose for each collected event and derived feature.
- Minimize event payloads and separate operational identifiers from analytical pseudonyms where
  practical.
- Encrypt transport and storage; tightly restrict raw review/event/profile access.
- Never log JWTs, auth tokens, exact listing addresses, payment details, or raw sensitive prompts.
- Define retention windows independently for raw events, aggregates, experiment exposures, and
  model artifacts.
- Propagate account deletion and personalization opt-out to derived profiles, caches, training
  datasets where feasible, and future model builds.
- Keep model/version audit data without retaining unnecessary personal content.
- Prevent one host from querying another listing's private diagnostics or individual guest
  preferences.
- Rate-limit event ingestion and protect it against fabricated impressions/clicks.
- Review third-party AI/provider data usage, residency, retention, and training terms before sending
  any review content.
- Restrict child/minor-related, disability-related, and other sensitive signals to explicit
  functional requirements and applicable policy; do not use them as speculative targeting inputs.

## Abuse, bias, and marketplace integrity

### Review and behavior manipulation

Potential attacks include fake bookings/reviews, click farms, self-favoriting, coordinated negative
reviews, host-controlled traffic, and repeated event replay. Mitigations include verified completed
stays, uniqueness constraints, idempotent events, anomaly detection, account/device risk signals
under separate governance, evidence thresholds, and manual review for high-impact enforcement.

### Popularity bias

Historical rank creates exposure, exposure creates interactions, and interactions can reinforce the
same rank. Counter this with Bayesian smoothing, position-bias correction, new-listing exploration,
diverse candidate pools, and monitoring exposure concentration by host and market.

### Geographic and socioeconomic bias

Price, device, language, and location may act as proxies for sensitive attributes. Compare ranking
quality, no-result rates, exposure, and conversion across legitimate operational slices while
avoiding the creation of unnecessary sensitive labels. Human review is required before using
neighborhood-level reputation or safety features.

### Host fairness

Hosts need clear quality feedback, but internal ranking weights and anti-abuse controls must not be
fully exposed in a way that enables gaming. Product guidance should focus on genuine improvements:
accurate content, cleanliness, reliable amenities, transparent prices, and avoiding cancellations.

## Evaluation

### Offline data-quality checks

- event completeness, uniqueness, ordering tolerance, and schema-version distribution;
- impression-to-search and outcome-to-listing join rates;
- feature freshness and online/offline parity;
- profile coverage for listings and eligible guests;
- review extraction quality by language, aspect, and sentiment;
- point-in-time correctness and absence of future-data leakage;
- price and currency normalization correctness;
- missing-value behavior and fallback coverage.

### Offline ranking metrics

- Recall@K for completed/high-satisfaction bookings;
- NDCG@K or MAP for graded funnel outcomes;
- calibration of booking/completion/satisfaction predictions;
- coverage of listings, hosts, room types, price bands, and neighborhoods;
- diversity and novelty at K;
- new-listing and new-guest slice performance;
- cancellation/refund/poor-review rate among highly ranked results;
- stability under small input or feature changes.

Offline metrics are directional because logged outcomes came from a previous ranking policy.

### Online experiment metrics

Primary success should include completed booking and post-stay satisfaction, although these arrive
slowly. Guardrails should include:

- search latency and error rate;
- zero-result and reformulation rate;
- detail-view/favorite/booking funnel;
- guest and host cancellation rates;
- refund, dispute, and low-review rate;
- total-price surprise or checkout abandonment;
- listing/host exposure concentration;
- new-listing exposure and time to first qualified impression;
- opt-out, hide, or "not relevant" feedback;
- repeat booking and longer-term retention.

Use A/B assignments stable at the guest or anonymous session level. Predefine hypotheses, sample
size, exposure duration, primary metric, guardrails, and rollback criteria. Do not repeatedly peek
and ship on noisy short-term click gains.

## Observability and operations

Monitor:

- request volume, p50/p95/p99 search latency, timeout and fallback rates;
- candidate counts after every hard-filter stage;
- database query plans and spatial/availability index usage;
- feature lookup latency, cache hit rate, freshness, and missing rate;
- score and rank distributions by model version and market;
- model serving error, timeout, and baseline fallback rate;
- event ingestion lag, deduplication, dead-letter volume, and reconciliation gaps;
- review extraction queue age, provider errors, token/cost use, and quality drift;
- exposure concentration, exploration allocation, and cold-start coverage;
- outcome and calibration drift over time.

Each search log should use a correlation ID and record versions, timings, counts, and fallback codes
without raw private profile content. Operators need a restricted diagnostic view that can reproduce
a rank from approved feature snapshots; ordinary API clients do not.

## Failure behavior

| Failure | Required behavior |
| --- | --- |
| Guest profile unavailable | Use anonymous/contextual baseline |
| Listing profile missing/stale | Use stored facts and conservative priors |
| Ranking model timeout/error | Use deterministic baseline and emit metric |
| Review extraction unavailable | Publish eligible review; retry analysis asynchronously |
| Event sink unavailable | Preserve booking correctness; buffer/outbox and retry |
| Price/availability lookup fails | Do not guess; fail or omit candidate according to API contract |
| Experiment configuration invalid | Use last known safe/default ranker |
| Explanation cannot be supported | Omit the explanation, not the listing |

Circuit breakers and time budgets should reserve most search latency for authoritative retrieval and
pricing. Optional personalization enrichment has a stricter deadline and degrades independently.

## Caching and freshness

- Cache destination metadata and stable listing content by version.
- Cache listing intelligence profiles with event-driven invalidation or short bounded TTLs.
- Cache guest profiles only in authenticated, access-controlled keys and evict on opt-out/deletion.
- Do not use exact address in public cache keys or documents.
- Bind cached search pages to normalized request, ranking version/epoch, experiment, locale, and
  currency.
- Treat availability and trip price as date-specific, short-lived data; revalidate at booking.
- Prevent a stale cached result from exposing an archived/suspended listing by applying a final
  lightweight eligibility check or rapid invalidation.

## Rollout plan

### Phase 0 — Foundations

1. Complete listing, availability, booking, review, favorite, and geographic-search APIs.
2. Define event names, ownership, schemas, retention, consent/opt-out, and data-quality dashboards.
3. Implement deterministic candidate filtering and authoritative total-trip pricing.
4. Establish anonymous baseline metrics before personalization.

### Phase 1 — Explainable baseline

1. Add smoothed overall/category ratings and reliable listing outcome aggregates.
2. Implement deterministic normalized ranking plus stable cursor pagination.
3. Add diversity rules and a small, guarded new-listing exposure pool.
4. Return approved non-personalized reason codes.

### Phase 2 — Review intelligence

1. Approve taxonomy and label a multilingual evaluation dataset.
2. Add a forward migration for versioned aspect mentions and listing aspect aggregates.
3. Run extraction asynchronously in shadow mode.
4. Audit quality, then enable high-confidence aspects in listing summaries and baseline ranking.
5. Add host-facing strengths/trends only after disclosure thresholds and appeal tooling exist.

### Phase 3 — Rule-based personalization

1. Build price, room type, amenity, and aspect preference features.
2. Separate long-term, recent, and current-session signals.
3. Confidence-gate personalized score contribution.
4. Provide opt-out and broad preference explanations.
5. A/B test against the anonymous baseline with completed-stay guardrails.

### Phase 4 — Machine-learned ranking

1. Produce point-in-time training examples and correct for position bias.
2. Train and calibrate an interpretable learning-to-rank model.
3. Shadow-serve and compare outputs, latency, slices, and failure fallback.
4. Gradually increase experiment traffic after predefined gates pass.
5. Automate drift monitoring, model registry, rollback, and periodic retraining.

### Phase 5 — Advanced retrieval and intent

1. Add semantic free-text intent mapping to approved taxonomy/filter concepts.
2. Evaluate embedding/two-tower retrieval only when candidate scale requires it.
3. Evaluate contextual bandits under strict exploration budgets.
4. Optimize long-term satisfaction and repeat use, not only immediate conversion.

## Verification checklist

### Functional correctness

- Explicit filters and eligibility constraints cannot be overridden by personalization.
- Search and booking use the same date-range and money conventions.
- Total-trip price ranking includes mandatory fees and correct currency treatment.
- Anonymous, opted-out, new-guest, and missing-profile fallbacks work.
- Cursor pagination is stable and does not leak incompatible experiment/model state.
- Archived, paused, unavailable, or over-capacity listings cannot remain candidates.
- Public results never expose exact address or restricted profile fields.

### Review intelligence

- Only eligible published completed-stay reviews contribute listing quality evidence.
- Aspect extraction handles negation, contrast, multiple aspects, and non-listing subjects.
- Unsupported taxonomy codes and out-of-range confidence/sentiment are rejected.
- Low evidence is represented as uncertainty, not poor quality.
- Reprocessing is reproducible by taxonomy/extractor/model version.
- Removing or moderating a review rebuilds affected aggregates.
- A failed extraction does not block review publication or search.

### Preference inference

- Importance and sentiment/direction are stored separately.
- Raw prices are normalized within a comparable trip market.
- Cancellations and skipped impressions are not treated as unconditional dislike.
- Long-term, recent, and session intent can be independently inspected and expired.
- Opt-out/deletion clears serving data and is honored by future feature jobs.
- Sensitive characteristics and exact location/address data are absent from ranking features.

### Ranking and ML

- Feature computation is point-in-time correct and reproducible.
- Online and offline feature definitions match within tolerance.
- Model timeout falls back inside the search latency budget.
- Training/evaluation splits include chronological and cold-start tests.
- Position bias, exposure bias, missing values, and class imbalance are addressed.
- New model releases pass quality, latency, fairness, privacy, and rollback gates.
- Exploration is bounded, logged, and limited to eligible quality-floor candidates.

### Operations

- Event ingestion is idempotent and tolerates out-of-order delivery.
- Booking/review reconciliation detects missing analytical events.
- Model, feature, taxonomy, experiment, and ranker versions appear in restricted diagnostics.
- Alerts cover latency, fallback, freshness, extraction drift, and exposure concentration.
- AI provider outage and event backlog recovery are rehearsed.
- Raw event growth cannot exhaust the transactional database.

## Decisions required before implementation

- Which aspects are in the first supported taxonomy and which languages are evaluated at launch?
- What minimum evidence/confidence permits guest-facing and host-facing aspect claims?
- What event retention, personalization consent, export, and deletion policies apply by market?
- Which exact outcome defines ranking success: confirmed booking, completed stay, satisfaction, or a
  calibrated combination?
- What candidate cap and latency budget are acceptable for each search mode?
- How is relative price computed across taxes, fees, promotions, and currency conversion?
- What traffic percentage and quality floor are allowed for exploration?
- Which slices require fairness monitoring and human review?
- Which AI provider or self-hosted model may receive review text, under what retention terms?
- When does scale justify moving events/profiles from PostgreSQL to dedicated analytical and online
  serving systems?

These decisions should be recorded before adding migrations or public API contracts. Model weights,
thresholds, and candidate caps remain versioned configuration supported by measurement, not hidden
constants scattered through application services.
