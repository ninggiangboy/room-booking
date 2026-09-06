# Phase 000 — PostgreSQL foundation

## Goal

Prepare the database capabilities required by all later phases without creating domain tables.

The future shared event-contract, outbox/inbox, analytical lineage, experiment, feature, model, and
prediction foundations are specified in
[`../features/data-experimentation-and-ml-platform.md`](../features/data-experimentation-and-ml-platform.md).
They are not delivered by migration `000`; implementation must use new forward migrations.

## Delivered

- `pgcrypto` supplies database-side UUID generation.
- `citext` makes email uniqueness case-insensitive.
- `btree_gist` allows equality and date-range overlap operators in one booking exclusion constraint.

## Operational notes

Managed PostgreSQL may require an administrator to allow these extensions before the application role runs Liquibase. No PostGIS dependency is introduced yet; latitude and longitude remain ordinary numeric columns in the catalog phase.

## Exit criteria

- All three extensions are installed.
- The application role can call `gen_random_uuid()`.
- Liquibase records the changeset successfully.
