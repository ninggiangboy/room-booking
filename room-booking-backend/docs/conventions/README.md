# Engineering conventions

This directory is the binding rule set for `room-booking-backend`. Every change — written by a
person or by an agent — must follow it. The rules were not invented here: each one was extracted
from the code as it stands and from the refactor commits that put it there, so the "why" attached
to a rule is the defect that motivated it.

`AGENTS.md` is the short orientation for a contributor; `GUIDE.md` is the learning-oriented tour of
the project; these documents are the normative source. Where they disagree, this directory wins and
the other two must be corrected in the same change.

## The documents

| Document | Governs |
| --- | --- |
| [01-architecture-and-layering.md](01-architecture-and-layering.md) | Packages, layer boundaries, what may depend on what |
| [02-java-style-and-naming.md](02-java-style-and-naming.md) | Formatting, naming, records, Lombok, nullness, modern Java |
| [03-entities-and-persistence.md](03-entities-and-persistence.md) | Factories, aggregates, repositories, transactions, migrations |
| [04-time-and-clock.md](04-time-and-clock.md) | The UTC runtime, the shared `Clock`, civil dates, decision instants |
| [05-api-dto-and-errors.md](05-api-dto-and-errors.md) | Controllers, request/response records, the exception hierarchy, OpenAPI |
| [06-documentation-and-javadoc.md](06-documentation-and-javadoc.md) | JavaDoc density, repository SQL documentation, the documentation update map |
| [07-workflow-and-commits.md](07-workflow-and-commits.md) | Definition of done, verification commands, commit and pull-request format |

## The non-negotiables

These are the rules most often forgotten, and each one has already caused a defect in this
repository. Read them before writing code; the linked document explains each in full.

1. **No entity is constructed outside a factory.** An `Entity.builder()` or `new Entity(...)` in a
   service method is a defect, not a style preference. → [03](03-entities-and-persistence.md)
2. **The application clock is the only writer of time.** No `Instant.now()`, `LocalDate.now()`,
   `new Date()`, `CURRENT_DATE`, or `DEFAULT now()`. Inject `Clock`. → [04](04-time-and-clock.md)
3. **One command, one decision instant.** Read the clock once per workflow and pass that instant
   down, including into factories. → [04](04-time-and-clock.md)
4. **A civil date is meaningless without its zone.** There is no global "today". →
   [04](04-time-and-clock.md)
5. **Controllers stay thin.** No business rule, no repository call, no entity in a response body.
   → [01](01-architecture-and-layering.md), [05](05-api-dto-and-errors.md)
6. **HTTP status comes from the abstract exception type**, never from a concrete error or a
   hand-written status in a service. → [05](05-api-dto-and-errors.md)
7. **Every new type carries JavaDoc**, every repository method documents its generated SQL, and
   documentation ships in the same commit as the code. → [06](06-documentation-and-javadoc.md)
8. **Never edit an applied Liquibase changeset.** Add the next forward migration. →
   [03](03-entities-and-persistence.md)

## Using this as a checklist

Before opening a pull request, walk [07-workflow-and-commits.md](07-workflow-and-commits.md) top to
bottom. It contains the full definition of done, including the commands whose output must be clean.
