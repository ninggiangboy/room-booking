# 6. Documentation and JavaDoc

Documentation is part of the definition of done, not a follow-up task. It ships in the same commit
as the code so names, routes, configuration, and security behavior cannot drift.

## JavaDoc rules

- **Every** new class, interface, record, enum, and annotation gets type-level JavaDoc stating its
  responsibility, its architectural layer, and the important Spring/Lombok behavior.
- Explain the annotations at type or method level: what `@Component`, `@Service`, `@Transactional`,
  `@RequiredArgsConstructor`, or `@Builder` actually does here, and **why this type was given that
  visibility**. The factories are the model — each says why it is package-private.
- Public methods and package-private business entry points document purpose, side effects,
  transaction and security behavior, `@param`, `@return`, and meaningful `@throws`.
- Every record component gets an `@param`. Explain non-obvious validation annotations.
- Document enum constants whose business meaning is not self-evident, and every security-sensitive
  field: hashes, raw-token boundaries, expiry, consumption, optimistic-lock versions.
- Do not write fictional constructors or accessors that duplicate Lombok-generated code.
- `//` comments are for the reason behind a non-obvious decision — a race guard, a defensive copy, a
  post-commit failure path. Never narrate a straightforward assignment.
- Never put a password, signing secret, raw bearer token, production credential, or real personal
  data in a comment or an example.

## Repository JavaDoc

For **every** declared repository method, document:

1. The conceptual generated SQL in a `<pre>{@code ... }</pre>` block.
2. How Spring parsed the method name — which segment became which predicate, and which suffixes
   (`IsNull`, `GreaterThanEqual`, `OrderBy...Asc`) are operators that consume no argument.
3. The parameter binding.
4. The return cardinality and what an empty result means.

For a `@Query`, copy the exact statement and explain it. `AuthTokenRepository` is the reference.

## Writing style

The prose in this codebase explains **why**, in full sentences, at the point where a reader would
otherwise guess. A comment that restates the code adds nothing; a comment that records the defect a
line prevents is the one worth writing. Migration `011` and `UserRegistrationFactory.create` are
both good examples: each explains a failure mode, not a mechanism.

## Documentation update map

When a change touches the left column, update the right column in the same commit:

| Code change | Required documentation |
| --- | --- |
| Public endpoint, authentication, or payload | Root `README.md` API table and the runnable flow in `GUIDE.md` |
| New configuration property or local port | Properties comments, README configuration, GUIDE setup/configuration/troubleshooting |
| New package or major component | `package-info.java`, project tree, recommended reading order |
| Token, password, transaction, or security behavior | JavaDoc plus the GUIDE design/security explanation |
| New database migration | Changelog master, migration map, relevant data-model document |
| New or changed engineering rule | This directory, plus `AGENTS.md` if the summary there is now wrong |
| Renamed or removed type | Search README, GUIDE, JavaDoc links, tests, and examples for the old name |

Never edit comments inside an already-applied Liquibase changeset merely to improve prose.

## package-info.java

Every package has one. It states the package's responsibility and applies
`@org.jspecify.annotations.NullMarked` (except where a package documents its own nullness
convention, as `time` does). A `package-info.java` that explains a cross-cutting design links to the
design document, the way `time` links to the date-and-time-zone feature design.

## Verifying documentation

```bash
git status --short
git diff --name-status
git diff --check
```

Then find changed or untracked Java files that contain no JavaDoc at all:

```bash
{
  git diff --name-only --diff-filter=AM -- '*.java'
  git ls-files --others --exclude-standard -- '*.java'
} | sort -u | while IFS= read -r file; do
  if test -f "$file" && ! rg -q '/\*\*' "$file"; then
    echo "Review JavaDoc: $file"
  fi
done
```

That grep is a first-pass guard only. Then generate and read the output:

```bash
cd room-booking-backend
./gradlew javadoc
```

Treat new missing-member, missing-tag, invalid-link, or malformed-HTML warnings as documentation
defects. Warnings that mention only implicit or Lombok-generated constructors are non-functional:
do not "fix" them by adding duplicate boilerplate — verify instead that the type-level JavaDoc
explains how construction and injection work.
