# 3. Entities and persistence

## Every entity is built by a factory

**Rule: no persistence entity is constructed outside a dedicated factory component.** An
`Entity.builder()...build()` or a `new Entity(...)` inside a service method is a defect to fix, not
a matter of taste.

This rule comes from `refactor(service): build every entity through a factory`. Token rows and host
profiles used to be assembled by inline builders at their call sites, so each workflow repeated the
invariants a new row must satisfy — and could silently drop one. Centralizing construction means a
new host profile can never start in a reviewed identity state, a token row always stores only a
digest of a freshly generated secret, and a new user always starts with the guest role.

### The shape of a factory

Follow `UserRegistrationFactory`, `AuthTokenFactory`, and `HostProfileFactory`:

- A `@Component`, **package-private**, in the same service package as the workflow that uses it.
  Package-private visibility keeps partially constructed or sensitive values — raw token secrets,
  unencoded registration data — inside their package.
- Collaborators needed for construction (a `PasswordEncoder`, for example) are injected into the
  factory, not passed in by the caller.
- The factory returns a small `record` when a workflow must persist several rows together, or must
  receive a value that cannot be recovered from the entity afterwards:
  `UserRegistrationFactory.NewUser(user, initialRole)`,
  `AuthTokenFactory.IssuedToken(token, rawToken)` — the raw secret whose digest is what gets stored.
- **A factory never reads the clock.** The caller passes its own decision instant in, so a workflow
  that also supersedes or stamps other rows uses one instant everywhere instead of several separate
  clock reads. See [04](04-time-and-clock.md).
- A factory carries the same JavaDoc density as everything else: a class comment explaining why the
  annotations and the visibility were chosen, and `@param`/`@return` on the factory method.

There is one documented exception to "the factory sets every field", and it is documented **in the
factory**: `UserRegistrationFactory` deliberately leaves the initial role's grant instant unset,
because that row is inserted by an explicit statement rather than an audited save and must reuse the
saved user's `createdAt`. If you need a similar carve-out, write the reason into the JavaDoc the way
that one does.

## Aggregates

Aggregates live in `model`, map to a table with `@Table("snake_case_plural")`, and use:

- `@Id` on the primary key, `@Nullable` until the row is saved when the database assigns it.
- `@Version` for optimistic locking wherever two concurrent consumers could otherwise silently
  overwrite each other — `AuthToken` uses it so a token cannot be consumed twice.
- `@CreatedDate` / `@LastModifiedDate` for audit columns, populated by Spring Data JDBC auditing
  from the shared `Clock`.
- A field-level JavaDoc comment on every field, especially the security-sensitive ones (what is a
  digest, what a `null` consumption instant means, what the version column is for).

Behavior that belongs to the row itself lives on the aggregate as a small query method taking the
instant as a parameter — `AuthToken.isUsableAt(Instant)`, `User.isActive()` — never as a duplicated
condition in each service.

Spring Data JDBC aggregates are not JPA entities: there is no lazy loading and no persistence
session. Related data is loaded through its own repository query (roles are loaded via
`UserRoleRepository`), not through a navigable association.

## Repositories

- Extend `ListCrudRepository<Aggregate, IdType>`; derive queries from method names where possible
  and use `@Query` only when derivation cannot express the statement.
- Combine discriminators in the query rather than filtering in Java: `findByTokenHashAndType`
  exists so a secret issued for one purpose can never be looked up as another.
- Return `Optional` when zero or one row is expected, `List` when many are; never `null`.
- Use an explicit `...ForUpdate` query when a workflow must serialize changes for a row, and say in
  the JavaDoc that the caller must be inside a transaction (`UserRepository.findByIdForUpdate`).
- Every declared method is documented with its conceptual generated SQL, its bound parameters, its
  result cardinality, and how Spring parsed the method name — see
  [06](06-documentation-and-javadoc.md).

## Transactions

- `@Transactional` goes on the service method, never on a controller or a repository.
- Use `@Transactional(readOnly = true)` for pure reads; it documents intent as much as it optimizes.
- Domain exceptions extend `RuntimeException`, so a failure rolls the transaction back by default.
  Do not catch a domain exception to convert it into a return value.
- Rows that must be consistent with each other are written in one transactional method, so they
  commit or roll back together.
- No remote call inside a transaction; publish an event instead (see [01](01-architecture-and-layering.md)).

## Migrations

- Liquibase formatted SQL under `src/main/resources/db/changelog/changes/`, numbered with a
  three-digit prefix and a descriptive name, registered at the **end** of
  `db.changelog-master.yaml`.
- **Never edit an applied changeset** — not to change SQL, and not merely to improve its comments;
  changing a historical file affects checksum validation. Add the next forward migration instead and
  explain the history in `GUIDE.md` or the relevant data-model document.
- Every changeset has an author-prefixed id (`ninggiangboy:011-01-...`) and a `--rollback` line for
  each statement, in reverse order.
- A file-level comment explains *why* the migration exists, in the same voice as the JavaDoc.
- Money is stored in minor units. Absolute timestamps are `timestamptz`; civil values are `date` or
  `time` and are meaningless without their zone column.
- **No `DEFAULT now()` on a column the application writes.** A database default is a second,
  unsynchronised clock, and it hides an insert path that forgot to supply a value. Migration `011`
  exists purely to remove those defaults.
