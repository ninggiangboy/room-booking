# Standard prompt for marketplace feature-design documents

## Purpose

Use this prompt to turn one domain breakdown from
[`../marketplace-problem-breakdown.md`](../marketplace-problem-breakdown.md) into a complete,
implementation-oriented feature design under `docs/features/`.

The prompt is intentionally prescriptive. It makes future feature documents use the same evidence,
structure, architectural depth, repository integration, and verification process without requiring
multiple follow-up instructions.

## Minimal invocation

After this file exists in the working tree, a request can be as short as:

```text
Use docs/templates/feature-design-document-prompt.md to create the feature design for
D09 — Payment orchestration from the master map.
```

In Vietnamese:

```text
Dùng prompt chuẩn docs/templates/feature-design-document-prompt.md để tạo feature document cho
D09 — Payment orchestration từ master map.
```

When the output filename is not provided, derive a concise kebab-case filename from the domain,
such as `docs/features/payment-orchestration.md`.

## Reusable prompt

Copy the prompt below when working in another session or tool that does not automatically read this
repository file. Replace only the values in the `INPUT` block; optional fields may remain blank.

````text
You are producing one authoritative feature-design document for the Room Booking marketplace.

INPUT
- Master-map domain ID: {{DOMAIN_ID_OR_SECTION}}, for example `D09`
- Feature name: {{FEATURE_NAME}}, for example `Payment orchestration`
- Output file: {{OUTPUT_FILE_OR_AUTO}}, for example `docs/features/payment-orchestration.md`
- Additional scope or constraints: {{OPTIONAL_SCOPE}}

OBJECTIVE

Turn the selected breakdown in `docs/marketplace-problem-breakdown.md` into a detailed,
implementation-oriented feature document consistent with the repository's existing feature docs.
Complete the document and repository documentation integration in one pass. Do not stop after an
outline and do not require the user to repeat standard documentation requirements.

DEFAULT SCOPE

This task creates or updates documentation only. Do not implement Java code, database migrations,
configuration, tests, external provider changes, or infrastructure unless the user explicitly asks
for implementation in addition to the document.

When a product decision is genuinely unresolved, make the safest reasonable assumption needed to
continue, label it as an assumption or recommendation, describe alternatives and consequences, and
add the final decision to `Decisions required before implementation`. Ask the user only when choosing
would materially change authorized scope or cause an unsafe/external action.

MANDATORY DISCOVERY

Before writing:

1. Read the applicable `AGENTS.md` completely and follow it.
2. Read the complete relevant domain section in `docs/marketplace-problem-breakdown.md`, including
   its dependencies, workflows, target-release requirements, cross-domain invariants, and open
   decisions.
3. Inspect related feature documents, data-model documents, Liquibase migrations, Java packages,
   configuration, README, and GUIDE. Use `rg`/`rg --files` first for repository discovery.
4. Use these as the canonical format references where available:
   - `docs/features/personalized-discovery.md`
   - `docs/features/dynamic-pricing-and-settlement.md`
   - `docs/features/availability-reservation-and-booking.md`
   - `docs/features/multi-market-compliance-and-localization.md`
5. Identify precisely:
   - what already exists in schema/code;
   - what is documented but not implemented;
   - what the target design proposes;
   - what is a required target capability, a designed extension boundary, or a measured-scale
     capability;
   - which neighboring domains own facts consumed by this feature.
6. Do not describe a target API, table, worker, event, provider integration, or model as implemented
   unless repository evidence proves it exists.
7. If the document needs current legal, tax, security-standard, protocol, or provider-specific facts,
   research authoritative primary sources and cite direct links near the relevant claim. Do not add
   decorative or weak sources to a purely internal design.

DOCUMENT LANGUAGE AND STYLE

- Write the feature document in English to match the repository.
- Use concise technical prose, descriptive headings, CommonMark tables/lists, and fenced text diagrams
  only where they clarify flow, state, ownership, or data relationships.
- Prefer stable domain terms over vendor-specific terminology.
- Define every acronym on first use.
- Separate normative rules (`must`, invariants, constraints) from recommendations and explicitly
  excluded product categories.
- Avoid filler, generic textbook explanations, duplicated master-map prose, and premature technology.
- Link to the authoritative neighboring document instead of copying its full logic.
- Keep the document navigable despite its depth; use subsections for independently reviewable topics.

REQUIRED DOCUMENT STRUCTURE

Use this top-level skeleton in this order. A section may be adapted or omitted only when it is truly
irrelevant; do not silently omit risk, failure, testing, or decision sections.

# {{Feature title}}

## Purpose
- Define the feature and central product/system question.
- State its relationship to the master map and neighboring feature docs.

## Status and dependencies
- State explicitly that this is a target design when implementation is incomplete.
- Summarize current repository foundations with links to relevant data-model docs/migrations.
- List missing capabilities and the recommended dependency order.

## Goals

## Non-goals

## Core principles and invariants
- Give major principles their own `###` headings.
- State correctness properties that must hold under concurrency, retries, stale data, failure, and
  historical replay.

## Domain vocabulary
- Define terms, state dimensions, identifiers, dates/times, amounts, and ambiguous industry words.

## End-to-end flow
- Show the primary journey as a compact text diagram.
- Explain transaction/saga boundaries and which domain owns each decision.

## Domain-specific design sections
- Expand every subproblem listed for the selected domain in the master map.
- Add any necessary algorithms, precedence rules, state machines, transition matrices, formulas,
  examples, and edge cases.
- Use as many focused `##`/`###` sections as needed; do not force all domain logic into one section.

## Conceptual data model
- Describe proposed entities, important fields, relationships, immutable snapshots, versions,
  provenance, constraints, and indexes.
- Distinguish current tables from proposed tables.
- State migration/backfill/deployment implications without editing an applied migration.

## Service boundaries
- Name conceptual services/modules and their contracts.
- Identify the source of truth for every important fact.
- Prevent duplicated policy or calculations across domains.
- Do not imply that logical services require immediate microservice extraction.

## API behavior
- Provide illustrative guest, host, admin, and internal operations as applicable.
- Define authoritative versus client-supplied inputs, idempotency, version/concurrency fields, access
  boundaries, response behavior, and important examples.

### Error semantics
- Define stable domain error codes, suitable HTTP behavior, information-disclosure boundaries, and
  whether/how the client may retry.

## Event contracts
- Define committed past-tense facts, producers/consumers, minimal payload identity, version,
  correlation/causation, outbox behavior, replay, and deduplication.

## Concurrency and idempotency
- Include this section whenever the domain changes inventory, money, state, quotas, or external side
  effects.
- Specify locks, constraints, optimistic versions, ordering, race winners, retry semantics, and final
  database defenses.

## Security, privacy, and access control
- Cover actor/resource authorization, sensitive data, secret handling, logging/redaction, abuse,
  privileged operations, retention, and audit.

## Observability and operations
- Define business/correctness metrics, technical metrics, SLO candidates, alerts, authorized recovery
  commands, reconciliation/review domain queues, and inputs required by external runbooks. Do not
  design user interfaces, staffing/training plans, or standalone operational runbook artifacts unless
  the user explicitly expands scope.

## Failure behavior
- Provide a table of dependency failures, duplicate/out-of-order inputs, timeouts, crashes, stale
  data, partial progress, and required recovery/compensation behavior.

## Testing and verification
- Cover deterministic/unit cases, real-database constraints, concurrency, state transitions,
  property/invariant tests, provider contracts, security, replay/recovery, and failure injection as
  applicable.

## Caching, performance, and scaling
- Define safe cache authority/freshness, critical indexes/query shapes, bounded work, hotspots,
  degradation, and evidence required before partitioning or service extraction.

## Appropriate use of AI
- State useful ML/AI components, prerequisites, training/evaluation concerns, confidence/fallback,
  audit, and kill switch.
- State explicitly which factual, legal, financial, inventory, safety, or contract decisions a model
  may not own.
- If AI has no valid role, say so briefly rather than inventing one.

## Target-release dependencies and completion gates
- Describe the dependency order required to implement the complete target safely; do not present
  dependencies as reduced product releases.
- Classify every capability as required target behavior, a designed extension boundary, or a
  measured-scale capability with an explicit activation threshold.
- Include compatibility/backfill requirements and measurable cumulative completion gates. A partial
  vertical slice may validate integration but must not be described as the finished product.

## Verification checklist
- Group reviewable acceptance items under functional/correctness, recovery, security, and operations
  subsections as appropriate.
- Make each item observable or testable.

## Decisions required before implementation
- List unresolved product, legal, architecture, provider, policy, and rollout decisions.
- For each consequential decision, recommend an ADR containing owner, date, context, alternatives,
  decision, consequences, rollout, and revisit trigger.

MANDATORY DESIGN COVERAGE

Adapt the following checklist to the domain. Do not omit an item just because the master-map bullet
did not spell it out:

- actors, authorization, ownership, and source-of-truth matrix;
- aggregates/entities, identities, lifecycle states, allowed transitions, and terminal states;
- invariants enforced by application and by database/provider boundaries;
- date, time-zone, locale, currency, amount, rounding, and version semantics where applicable;
- synchronous transaction boundaries versus asynchronous sagas;
- idempotency scopes, canonical request identity, replay response, webhook/event deduplication;
- concurrency races, lock ordering, database constraints, retry and compensation;
- immutable historical snapshots and effective-dated policy/configuration;
- failure matrix, recovery workers, reconciliation, and manual exception paths;
- API errors, user-visible explanations, and prevention of sensitive information leakage;
- event contracts, outbox/inbox semantics, projections, freshness, and replay;
- privacy, secrets, fraud/abuse, privileged actions, audit, and retention;
- business metrics, correctness metrics, latency/availability signals, alerts, and SLOs;
- deterministic, integration, concurrency, property, recovery, security, and provider-contract tests;
- target-release requirements, explicit product exclusions, measured-scale activation thresholds,
  backward compatibility, backfill, rollback/kill switch;
- valid AI/ML opportunities and explicit prohibited authority;
- build-versus-buy boundary where a provider is involved;
- open decisions, recommended defaults, and revisit triggers.

PROJECT-WIDE RULES TO PRESERVE

- A missing availability row is unavailable.
- The target release supports both unique rentals and pooled hotel room types. Use `property`,
  `accommodation type`, optional `physical unit`, public `listing`, and per-date inventory quantity
  consistently; do not make a listing row the inventory authority.
- Stay ranges are half-open `[check_in, check_out)` and use listing-local dates.
- Events and deadlines use timestamps; listing time zones use IANA identifiers.
- Money uses integer minor units and ISO 4217 currency; no floating-point money.
- Vietnam is the only active launch market, while contracts carry market, currency, locale,
  time-zone, policy, and provider provenance so another market does not require a breaking change.
- Search, caches, provider records, and analytics are not transactional sources of truth.
- Client-calculated totals and client-requested state transitions are never trusted.
- Confirmed contractual and financial history is immutable; changes use new versions, adjustments,
  reversals, or replacement records.
- Database constraints remain the final defense for inventory and monetary invariants.
- Do not keep database transactions/locks open across uncontrolled network calls.
- LLMs are not authoritative for availability, money, tax, legal policy, safety, or booking state.
- Personalized ranking, review intelligence, bounded pricing recommendation, and fraud/content
  moderation are required target capabilities, with model lineage, deterministic guardrails,
  fallback, shadow/canary verification, and kill switches.
- Do not introduce microservices, Kafka, CQRS, event sourcing, sharding, vector stores, or advanced ML
  without a measured need and an accountable owner.
- Never edit an already-applied Liquibase changeset; propose or add a forward migration only when
  implementation is explicitly requested.

REPOSITORY INTEGRATION

After creating the document:

1. Add or update the `Detailed design` link in every relevant master-map domain section.
2. Update the master map's current-target and document-backlog status so it does not continue to
   describe the new document as missing.
3. Add the feature document to the root README documentation list.
4. Add links from directly affected `docs/data-model/*.md` files.
5. Link to related feature docs in both directions only where the relationship is material.
6. Update GUIDE only if its documented routes, configuration, package layout, migrations, token
   behavior, or maintenance instructions actually changed.
7. Preserve unrelated and pre-existing working-tree changes.

Do not duplicate the entire feature design in the master map. Keep the master map as a compact domain
and dependency index; keep implementation-level detail in the feature document.

VERIFICATION

Before finishing:

1. Inspect tracked and untracked changes.
2. Check heading hierarchy and balanced fenced code blocks.
3. Verify every relative Markdown link resolves.
4. Run `git diff --check`.
5. From `room-booking-backend/`, run `./gradlew test build javadoc`.
6. Confirm the generated Javadoc index exists when Javadoc succeeds.
7. Do not create tests solely for a documentation-only task when the repository has no tests.
8. Re-read the new document's Purpose, Status, principles, rollout, verification, and decisions to
   ensure they do not contradict each other or the master map.

FINAL RESPONSE

Respond in Vietnamese. Lead with completion and provide a clickable absolute link to the document.
Briefly summarize the most important architectural decisions, files cross-linked, assumptions or
unresolved decisions, and verification commands/results. Explicitly state that code/migrations were
not changed when the task was documentation-only.
````

## Recommended shorthand for this repository

For the next document, the complete user request can be:

```text
Dùng feature-doc prompt chuẩn cho D09 — Payment orchestration.
```

For another domain, replace only `D09 — Payment orchestration`. The agent should derive scope,
dependencies, filename, cross-links, and verification requirements from this prompt and the master
map.

## Batch usage

Prefer one feature document per request so dependencies and decisions from the first document can
inform the next. When intentionally generating several documents, use:

```text
Dùng feature-doc prompt chuẩn, lần lượt tạo các document cho D09, D11 và D12. Hoàn thành, tích hợp
và verify từng document trước khi chuyển sang document tiếp theo; không triển khai code/migration.
```

This still requires each document to have its own purpose, boundaries, model, dependency and
completion gates, verification, and decision set. Do not merge unrelated domains merely to reduce
file count.
