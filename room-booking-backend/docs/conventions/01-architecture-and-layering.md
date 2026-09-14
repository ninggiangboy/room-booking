# 1. Architecture and layering

## Package map

Production code lives under `dev.ngb.backend`, organized by module first and by responsibility
second. There is no top-level `model`, `repository`, `service`, `controller`, `dto`, `event`, or
`exception` package — each of those responsibilities exists **inside every module**, under
`internal/`. `docs/modules/README.md` is the binding index of the 22 modules; the table below is the
responsibility layer that repeats inside each one:

| Sub-package (inside a module) | Contains | May depend on |
| --- | --- | --- |
| `internal/web` | HTTP entry points and their request/response records | this module's `internal/service` |
| `internal/service` | Use cases, business rules, factories, policies | this module's `internal/model`, `internal/repository`, and — for another module — only its root package or a `@NamedInterface` |
| `internal/repository` | Spring Data JDBC interfaces | this module's `internal/model` |
| `internal/model` | Persistence aggregates and domain enums, subdivided by aggregate cluster | nothing in the application |

The dependency direction inside a module is one-way: `internal/web → internal/service →
internal/repository → internal/model`. A repository must never be injected into a controller, and
`internal/model` must never import from `internal/service` or above.

Two packages sit outside every module's `internal/`, by design:

| Package | Contains | May depend on |
| --- | --- | --- |
| `platform` (root) | The shared kernel: value types and enums genuinely used by 3+ modules with no natural owner. `platform.time`, `platform.util`, `platform.exception.base` moved in unchanged because they depend on nothing else in the application. | nothing else in the application — every other module depends on `platform`, never the reverse |
| `config` | Spring configuration, security wiring, the global exception handler | anything — the one module Spring Modulith declares `Type.OPEN` |

A module may also expose a `<module>.types` package for an enum used by a downstream module that is
already dependent on it in every other respect (see `docs/modules/platform.md`'s R3 rule) — a
`@NamedInterface`, not an escape hatch for anything else.

`ApplicationModules.verify()` enforces the module boundary at build time: a reference into another
module's `internal/` package fails the check regardless of what this document says. See
`docs/architecture/modular-monolith.md` for what it checks and — just as important — what it
structurally cannot see (a foreign key crossing a module boundary carries no Java import).

## Module and service subpackages

Inside `internal/service`, work is further divided by capability, the same way `identity` already
does it — `service.auth`, `service.account`, `service.host`, `service.user`, `service.validation`
(and `platform`'s `internal.service.mail`). A new capability gets its own subpackage with its own
`package-info.java`.

Subpackage visibility is load-bearing, not cosmetic, at **both** levels this codebase now has. Types
that exist only to support one workflow — factories above all — are **package-private** so that
partially constructed state and raw secrets cannot escape the package that knows how to handle them;
only the type a different package actually calls is `public`. The same principle now also applies
one level up: only the type a different *module* actually calls belongs at that module's root —
everything else stays under `internal`, even if it is `public` for intra-module reasons. Getting this
wrong is not hypothetical: building this migration surfaced two real cases where a caller's
promotion to a module's public root stranded a package-private collaborator it needed —
`AccessTokenService` (`identity`) and `HostOnboardingService` (identity's own legacy host-onboarding
workflow, not `hostverification`'s) both needed their factory moved to the same root package,
package-private modifier intact, rather than widened to `public`. See `docs/modules/identity.md`.

## Keep controllers thin

A controller method may do exactly three things: accept a validated request, delegate to one
service, and shape the HTTP response (`ResponseEntity.noContent()`, a response record). It must not
branch on business state, call a repository, catch a domain exception, or return a persistence
entity.

## Extract a shared collaborator instead of duplicating a rule

When the same lookup, check, or construction appears in a second service, move it behind a
collaborator rather than copying it. The repository already did this twice, and both moves are the
precedent to follow:

- `UserFinder` (`refactor(user): centralize account lookup and activation checks`) owns every "load
  this user and decide what an absent or disabled account means" path. Services no longer repeat
  `findById(...).orElseThrow(...)` plus an `isActive()` check. Its methods take a
  `Supplier<? extends RuntimeException>` so each caller keeps its own not-found semantics without
  reimplementing the lookup.
- The factories (`refactor(service): build every entity through a factory`) own construction, for
  the same reason: a duplicated invariant is an invariant that will eventually be forgotten at one
  of its call sites.

A collaborator is preferable to a base class or a static utility: it is injected, it can be
replaced in a test, and its dependencies stay explicit.

## Events for post-commit work

Work that must not roll back the command and must not run inside its transaction — sending an
email, above all — is published as an application event and handled after commit. A service must
never call an SMTP client, an HTTP client, or any other remote system inline inside a
`@Transactional` method.

**`@ApplicationModuleListener` is the default for an event crossing a module boundary.** It composes
`@Async @Transactional(REQUIRES_NEW) @TransactionalEventListener` and, backed by
`spring-modulith-starter-jdbc`, records the event durably: a listener that has not yet run survives a
process restart instead of being silently lost the way a bare `ApplicationEventPublisher` would lose
it. See `docs/architecture/event-publication-registry.md` for the mechanics and the two settings that
make it behave like an outbox rather than a write-only log.

**No event carrying a secret, raw personal data, or anything without a retention policy may go
through the registry.** The registry serializes the full event payload as plaintext JSON into a table
with no retention policy of its own. `EmailVerificationIssued` and `PasswordResetIssued` — both carry
a raw token secret — are the one named exception: they stay on a bare `@TransactionalEventListener`,
documented as such in `AuthEmailNotifier`'s Javadoc, exactly as they behaved before Spring Modulith
was introduced.
