# 7. Workflow, definition of done, and commits

## The flow every change follows

1. **Read the rules that apply.** At minimum the non-negotiables in
   [README.md](README.md); in full, the document governing the layer you are touching.
2. **Locate the existing precedent.** Nearly every kind of change has already been made once here.
   Match it rather than inventing a second way: a new factory follows `AuthTokenFactory`, a new
   repository method follows `AuthTokenRepository`, a new exception follows
   `EmailAlreadyRegisteredException`.
3. **Write the code**, keeping the layer boundaries in [01](01-architecture-and-layering.md).
4. **Write the JavaDoc as you go**, at the density described in [06](06-documentation-and-javadoc.md).
   Retrofitting it later produces mechanical comments that explain nothing.
5. **Write the migration** if the change touches the schema, as the next forward changeset.
6. **Update the documentation** named by the update map in [06](06-documentation-and-javadoc.md).
7. **Verify** with the commands below.
8. **Commit** the whole logical change — code, migration, tests, documentation — together.

## Definition of done

A change is done when all of the following hold:

- No entity is constructed outside a factory.
- No clock is read outside the injected `Clock`, and each workflow reads it once.
- Layer dependencies point one way, and controllers contain no business logic.
- Every new type, record component, public method, and repository method is documented.
- The documentation update map has been walked.
- `git diff --check` is clean.
- `./gradlew test`, `./gradlew build`, and `./gradlew javadoc` all pass, and no new JavaDoc warning
  was introduced.
- Nothing generated (`build/`, `.gradle/`), no IDE metadata, no local secret, and no environment
  file is staged.

## Verification commands

Run from `room-booking-backend/` using the checked-in Gradle wrapper:

```bash
docker compose -f compose.local.yaml up -d          # PostgreSQL, MinIO, Mailpit
./gradlew bootRun --args='--spring.profiles.active=local'
./gradlew test
./gradlew build
./gradlew javadoc
```

## Tests

If an area has no test coverage yet, do not create tests solely to satisfy a checklist. Once testing
is established for an area, add or update the relevant tests alongside the change. Name them
`AuthenticationServiceTest`, `AuthControllerTest`; cover successful behavior, validation failures,
authorization boundaries, and persistence constraints. Prefer Spring test slices; reserve a full
application context for genuinely cross-layer behavior. Mirror the production package layout under
`src/test/java`.

## Commits

One focused commit per logical change. A feature commit includes its production code, its tests
(where the suite covers that area), its migration, and its documentation; those dependent pieces are
never split apart.

Subject: a concise, imperative sentence with an optional scope.

| Prefix | Use for | Example |
| --- | --- | --- |
| `feat` | A new user-facing capability | `feat(users): add host onboarding` |
| `fix` | A defect correction | `fix(auth): reject expired refresh tokens` |
| `refactor` | Internal restructuring with no behavior change | `refactor(user): centralize account lookup` |
| `docs` | Documentation-only changes | `docs: clarify local startup` |
| `test` | Test-only changes | `test(auth): cover token reuse` |
| `chore` | Tooling, build, or maintenance work | `chore: update Gradle wrapper` |

### Commit bodies explain the defect, not the diff

This repository's history is a reference, and the rule set in this directory was recovered from it.
A body states the problem that existed, why it was wrong, and what the new arrangement guarantees —
never a list of the files touched. Compare:

> Token rows and host profiles were assembled by inline builders at their call sites, so each
> workflow repeated the invariants a new row must satisfy and could silently drop one.
>
> Move that construction into AuthTokenFactory and HostProfileFactory […] AuthTokenFactory returns
> the token row paired with the secret that its stored digest can never reveal again, and takes the
> issuing instant from the caller so a workflow that also supersedes older tokens stamps every row
> with one decision instant rather than several clock reads.

Before committing, inspect exactly what will be included:

```bash
git status --short
git diff --check
git diff --cached
```

Stage only the files that belong to the change.

## Pull requests

Explain the change, note configuration or schema impacts, link relevant issues, include
request/response examples for API changes, and report the commands used to verify the work. Add
screenshots only for user-visible UI or rendered-documentation changes.

## Security and configuration

Never commit production credentials or JWT secrets; override local defaults through environment
variables or profile-specific configuration. Preserve the documented rules for token hashing, money
in minor units, timestamp handling, and server-side booking validation.
