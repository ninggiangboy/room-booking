# Modular monolith with Spring Modulith

## Goal

Give `room-booking-backend` module boundaries before any of the 21 business domains behind its 423
live tables grows a service layer, so that the first line of code for a new domain is written inside
a boundary rather than into one flat package that nothing enforces.

## Why now, and why this shape

Schema has run far ahead of code. Twenty-nine Liquibase changesets built 423 live tables across 24
migration groups, but only four aggregates — `User`, `UserRole`, `HostProfile`, `AuthToken` — have a
service in front of them. Every other table is reachable only through its own repository. Once the
remaining domains start getting services, a single 1,282-type `model` package and a single
424-repository `repository` package supply no boundary that stops one domain's service from calling
another domain's repository directly. There is no service to break yet, which is exactly what makes
this the cheapest possible moment to draw the lines: `docs/marketplace-problem-breakdown.md` already
named the move — start with modules and transaction boundaries inside one application, and split
deployment units only when doing so earns back its cost. Nothing here argues for splitting deployment
units; it argues for making the internal boundaries the compiler already implies but does not check.

Three measured facts make the split mechanical rather than speculative:

- **No persistence mapping to break.** This is Spring Data JDBC, not JPA: zero `@Entity`, zero
  `@ManyToOne`, zero `AggregateReference`, zero `@MappedCollection` across all 1,282 types. Every
  aggregate refers to another by a bare `UUID` column. Moving a type to a new package changes no
  mapping and creates no new inter-aggregate import.
- **Module assignment is derived, not guessed.** 423 of 424 entities carry `@Table("x")`, and every
  table name appears in exactly one `CREATE TABLE` in exactly one `db/changelog/changes/NNN-*.sql`
  file. The one exception, `auth_tokens`, is a rename target (`008` renamed `007`'s table) with no
  `CREATE TABLE` of its own, and is assigned by hand to `identity`.
- **Java-level coupling is almost nonexistent.** Assigning every type to the module of the aggregate
  that references it (transitive closure) puts 1,239 of 1,282 types in exactly one module. Only 39
  types are referenced by more than one module — the entire shared kernel, and it is small.

## Modules

Business content, ownership, and internal structure for each module are recorded in
[`../modules/README.md`](../modules/README.md) and the 22 per-module documents it indexes. That
index is the place to look up which module owns a table, not this document.

## What Spring Modulith actually buys here

Spring Modulith is not a new runtime or a new deployment unit. It is:

1. **A convention.** Every direct sub-package of `dev.ngb.backend` is a module. The module's root
   package is its public API; every sub-package is internal by default and inaccessible from other
   modules' code.
2. **An ArchUnit check.** `ApplicationModules.verify()` fails the build the moment a module reaches
   into another module's internals, or the module graph acquires a cycle that was not explicitly
   allowed. This is the enforcement layer the flat `model`/`repository` packages never had.
3. **A documentation generator.** `Documenter` renders the verified module graph as C4 and PlantUML
   diagrams, so the diagram cannot drift from the code the way a hand-drawn one would.
4. **An event publication registry.** `@ApplicationModuleListener` gives cross-module,
   post-commit event handling an at-least-once delivery guarantee backed by a database table, in
   place of a bare `ApplicationEventPublisher` that silently drops an event if the process dies
   between commit and listener. See [`event-publication-registry.md`](event-publication-registry.md)
   for what changes and what does not.

It stays one Gradle project, one deployable artifact, one database. Nothing about how the
application is built, tested, or run changes; what changes is what the compiler and a test will let
one module's code do to another's.

## The boundary Modulith cannot see

`ApplicationModules.verify()` inspects Java types and their imports. It has no visibility into SQL.
The 423 tables carry 624 foreign keys that cross migration-group boundaries — `account_holders`
alone is referenced by 164 foreign keys outside its own group, `markets` by 61, `bookings` by 43,
`listings` by 33 — and every one of them exists only as a `UUID` column, never as a Java reference.
`verify()` will pass cleanly even where two modules are tightly coupled at the data layer, because
that coupling has no Java expression for ArchUnit to catch.

This is good news for the migration — moving packages cannot make a hidden FK problem worse than it
already is — and a permanent risk afterward: the real boundary between, say, `booking` and `ledger`
is partly enforced by the module system and partly held together by nothing but convention and this
document. Two things follow from that, and both are load-bearing:

- Each module document under `../modules/` names the cross-module foreign keys that touch it, so a
  reviewer can see the coupling `verify()` cannot.
- A change that adds a new foreign key crossing a module boundary is a design decision, not a
  routine migration, and should be justified the way a new `allowedDependencies` entry would be.

## Non-negotiables this decision introduces

1. **No type lives in a module's `internal/model` root.** An entity is a persistence detail; a
   thing another module needs is exposed as its own API record, not as the entity itself. See each
   module's "Public API" section.
2. **`platform` and `config` are the only shared/open modules.** `config` is `Type.OPEN` because
   Spring configuration legitimately depends on everything; `platform` holds only the ~35 types with
   no natural single owner (see [`../modules/platform.md`](../modules/platform.md) and the R0–R3
   rules there). No third module gets either treatment. A type that looks like it belongs in
   `platform` because "several modules use it" almost always belongs in R3 instead — exposed by its
   natural owner through a `@NamedInterface` — because an enum that lives in `platform` is an enum
   nobody can change without touching every module.
3. **`@ApplicationModuleListener` is the default for events between modules**, except the two that
   already carry a raw secret (`EmailVerificationIssued`, `PasswordResetIssued`), which stay on a
   bare `@TransactionalEventListener` because the registry would persist that secret in plaintext.
   See [`event-publication-registry.md`](event-publication-registry.md).
4. **No `scanBasePackages` and no `@EnableJdbcRepositories`.** Component scanning and Spring Data
   JDBC repository discovery both anchor on the package of `@SpringBootApplication`
   (`RoomBookingBackendApplication`, which stays at `dev.ngb.backend`) and walk every descendant
   package. Adding either annotation narrows that scan to whatever package list someone hand-writes,
   and silently drops every module nobody remembered to list.
5. **`event_publication` (Spring Modulith) and `outbox_events` (migration `012`) are different
   tables that answer different questions**, and neither replaces the other. See
   [`event-publication-registry.md`](event-publication-registry.md) for the distinction; do not
   delete `outbox_events` because it looks unused — it is unused only because nothing has
   externalized an event to a broker yet, which is exactly the job it exists to do later.

## What this decision does not do

It does not change the database schema beyond the one new migration for the event publication
registry. It does not introduce a message broker, a second deployment unit, or a second database.
It does not resolve the open `users`-versus-`account_holders` question tracked in
[`../data-model/README.md`](../data-model/README.md); it places both tables in `identity` so that
whichever way that question resolves, it resolves inside one module's internals instead of across a
module boundary.

## Status

Complete and enforced. All 423 live entities and their repositories, and every existing piece of
application code (`identity`'s auth stack, `hostverification`'s legacy host-onboarding workflow — see
[`../modules/identity.md`](../modules/identity.md#the-legacy-host-onboarding-workflow) for why it is
identity's, not hostverification's — and `platform`'s mail port), live under one of the 22 modules.
`src/test/java/dev/ngb/backend/ModularityTests.java` runs `ApplicationModules.of(...).verify()` with
no allow-list and no violations: every `allowedDependencies` entry was set from the actual
cross-module Java import graph, re-derived by grep after an audit pass removed spurious imports the
move script had generated from ordinary English words in Javadoc prose matching a class's simple
name (see the commit that fixed it for the full account — it is a real failure mode of any
identifier-matching import generator, not specific to this one). `./gradlew build`, `./gradlew test`,
and `./gradlew javadoc` are all clean from a fresh clone.
