# Migration 000 — PostgreSQL foundation

## Goal

Record the PostgreSQL capabilities installed by the first historical changeset without creating
domain tables.

The target shared identifier, time, locale, money, request/error, command-idempotency, outbox/inbox,
audit, security, compatible-deployment, and recovery contracts are specified in
[`../features/platform-foundation.md`](../features/platform-foundation.md). They are not delivered by
migration `000`; implementation must use new forward migrations.

The future event taxonomy/schema governance, analytical lineage, experiment, feature, model, and
prediction contracts are specified in
[`../features/data-experimentation-and-ml-platform.md`](../features/data-experimentation-and-ml-platform.md).
D00 owns the durable transport primitives while D19/D20 own taxonomy, data, experiment, and model
governance.

## Delivered

- `pgcrypto` supplies database-side UUID generation.
- `citext` makes email uniqueness case-insensitive.
- `btree_gist` allows equality and date-range overlap operators in one booking exclusion constraint.

## Operational notes

Managed PostgreSQL may require an administrator to allow these extensions before the application
role runs Liquibase. Migration `000` did not introduce PostGIS; catalog coordinates remained numeric
until migration `010` added the target spatial foundation.

## Migration verification

- All three extensions are installed.
- The application role can call `gen_random_uuid()`.
- Liquibase records the changeset successfully.
