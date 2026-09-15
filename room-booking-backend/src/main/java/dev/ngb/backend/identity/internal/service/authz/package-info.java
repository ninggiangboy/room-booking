/**
 * Capability catalog, role bundles, and the pure decision function that evaluates what a principal
 * may currently do.
 *
 * <p>{@link dev.ngb.backend.identity.internal.service.authz.Capability} and {@link
 * dev.ngb.backend.identity.internal.service.authz.RoleBundle} are the single source of truth that
 * migration {@code 014}'s backfill SQL copies, not the other way round: the SQL is a snapshot of
 * this catalog at one point in time, and this catalog is what changes going forward.</p>
 *
 * <p>{@link dev.ngb.backend.identity.internal.service.authz.AuthorizationService} is kept a pure
 * decision function over stored grants and restrictions, per {@code
 * docs/features/identity-accounts-and-access.md} § Service boundaries: it reads {@code
 * capability_grants} and {@code capability_restrictions} and never writes either table. {@link
 * dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService} is the only writer of
 * {@code capability_grants} in this codebase, so every grant this application issues is
 * constructed the same way.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.authz;
