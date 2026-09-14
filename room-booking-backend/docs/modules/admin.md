# `admin`

## Goal

Own operator authority, versioned configuration, change control, and the operational command
catalogue — the module that makes an administrative action a named domain command with an approver,
never a database edit.

## Forces that shaped it

- **The governing rule is in the migration's own title**: an admin action is a named domain command,
  never a direct edit. That is why `operational_command_definitions` and their executions and
  approvals are modelled as data rather than as endpoints, and why this module exists at all instead
  of a scattering of admin controllers inside each domain.
- **Separation of duties is enforced at insert**, through `operator_role_conflicts`, which is what
  makes `role` a cluster with real invariants rather than a lookup table.
- **`change_requests` is the dominant root** (in-module in-degree 4) and reaches across clusters —
  configuration rollouts and feature flag states both hang off it. Change control is the spine; role,
  configuration, command, and export are what it governs.
- **Deliberately not merged with `market`.** Both hold versioned policy, but `market` owns *what the
  policy says for a jurisdiction* while `admin` owns *who may change it and under what approval*.
  Merging them would let a market configuration change bypass the approval machinery by being
  "the same module".

## What it owns

- **`role` cluster** — `operator_role_definitions` (root), `operator_role_permissions`,
  `operator_role_conflicts`, `operator_role_assignments`, `break_glass_grants`,
  `break_glass_activities`.
- **`configuration` cluster** — `configuration_schemas` (root), `configuration_settings`,
  `configuration_versions`, `configuration_rollouts`.
- **`change` cluster** — `change_requests` (root), `change_request_approvals`,
  `change_request_validations`, `feature_flag_definitions`, `feature_flag_states`.
- **`command` cluster** — `operational_command_definitions` (root),
  `operational_command_executions`, `operational_command_approvals`.
- **`export` cluster** — `bulk_export_requests` (root), `bulk_export_accesses`.

See [`../data-model/032-admin-and-governance.md`](../data-model/032-admin-and-governance.md). This
module has **no feature design document of its own** — a gap worth closing before it gets a service
layer, since every rule it enforces is currently recorded only as schema.

## Aggregate clusters inside it

20 tables across 5 clusters. `feature_flag_definitions` and `feature_flag_states` sit in `change`
rather than in `configuration` because a flag flip is governed by the same change-request and
approval path as any other controlled change; grouping them with configuration schemas would suggest
they are settings a schema validates, which they are not.

## What it does not own

The domain state an operational command changes. A command execution records that an authorized
operator invoked a named command; the resulting state change belongs to whichever module owns it,
and this module holds no foreign key into any of them. Nor does it own the append-only audit trail
itself — that is `platform`'s `audit_events`, referenced 3 times from here.

## Public API

No live service exists yet. Nothing depends on `admin` today and nothing is expected to: its
direction of travel is inward, invoking commands other modules publish. The eventual API is
therefore an authority-check surface other modules consult, not a command surface they call.

## Allowed dependencies

None with live code today. Schema carries 22 foreign keys into `identity`, 6 into `market`, 3 into
`platform`.

## Data coupling `verify()` cannot see

Inbound: none. Outbound: `identity` (22), `market` (6), `platform` (3). The 22 edges into `identity`
are every operator, approver, and grantee being an account holder — the second-largest single
concentration of identity coupling in the system after `support` — and none of it is visible to
`ApplicationModules.verify()`; see
[`../architecture/modular-monolith.md`](../architecture/modular-monolith.md#the-boundary-modulith-cannot-see).

## What it leaves open

How an operational command reaches the module that must execute it is left open, and it is the
natural first real use of `@ApplicationModuleListener`: a command execution row committed here, a
listener in the owning module, and a registry row proving the command was not lost between them. See
[`../architecture/event-publication-registry.md`](../architecture/event-publication-registry.md).

## Exit criteria

- All 20 tables and their entities/repositories live under `dev.ngb.backend.admin.internal.model` /
  `.repository`, in the five clusters above.
- `ApplicationModules.verify()` passes with `admin`'s only declared dependencies being `identity`,
  `market`, and `platform`.
