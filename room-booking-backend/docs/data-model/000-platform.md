# Phase 000 — PostgreSQL foundation

## Goal

Prepare the database capabilities required by all later phases without creating domain tables.

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
