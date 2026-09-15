## Summary

<!-- What does this PR change and why? Keep it to a few sentences focused on intent, not a line-by-line diff walkthrough. -->

## Related issue

<!-- Link the issue this PR closes, if any, e.g. "Closes #123" -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor (no functional change)
- [ ] Schema / migration change
- [ ] Documentation
- [ ] Other:

## Changes

<!-- Bullet the notable changes. Call out new endpoints, new tables/columns, new dependencies, or anything a reviewer should not miss. -->

-

## Database / migrations

<!-- Delete this section if there is no Liquibase change. -->

- [ ] New migration(s) added under `room-booking-backend/src/main/resources/db/changelog`
- [ ] Migration is additive (no destructive `DROP`/`ALTER ... NOT NULL` against existing data) or the destructive step is explicitly called out below
- [ ] Migration was run locally against a populated database
- [ ] Corresponding Spring Data JDBC aggregate/repository updated to match the schema

## API changes

<!-- Delete this section if there is no HTTP-facing change. -->

- [ ] New/changed endpoints documented (OpenAPI/Swagger reflects the change)
- [ ] Request/response DTOs updated
- [ ] Backward compatibility considered (existing clients not broken)

## How was this tested?

<!-- Commands run, unit/integration tests added, manual verification steps. Include curl examples or screenshots where useful. -->

- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manually verified locally

```
<!-- test/build output or manual steps go here -->
```

## Security considerations

<!-- Delete this section if not applicable. -->

- [ ] New/changed endpoint enforces the correct authentication/authorization
- [ ] User-supplied input is validated at the boundary
- [ ] No secrets, tokens, or credentials committed

## Checklist

- [ ] Entity creation goes through a dedicated factory component, not an inline `builder()`/`new Entity(...)` in a service (see `UserRegistrationFactory` for the reference shape)
- [ ] `./gradlew check` passes locally
- [ ] Documentation (`README.md`, module docs) updated if behavior, API, or setup steps changed
- [ ] Commit messages follow the repository's conventional style

## Screenshots

<!-- If this PR has a visible effect (API response shape, docs, diagrams), attach before/after here. Delete if not applicable. -->
