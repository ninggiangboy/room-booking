package dev.ngb.backend.model;

/**
 * How far a capability grant or restriction reaches.
 *
 * <p>This is what a role could never express. Holding the host role says nothing about *which*
 * listings a principal may edit; a {@link #LISTING}-scoped grant does, so owning a listing becomes
 * stronger and more specific than merely being a host.</p>
 *
 * <p>{@link #GLOBAL} is the only scope with no resource identifier, enforced by
 * {@code ck_capability_grants_scope_id}.</p>
 */
public enum AuthorizationScopeType {
    /** Applies everywhere; no resource identifier. */
    GLOBAL,
    /** Applies within one organization. */
    ORGANIZATION,
    /** Applies to one property and what hangs off it. */
    PROPERTY,
    /** Applies to one public listing. */
    LISTING,
    /** Applies to one booking. */
    BOOKING,
    /** Applies within one market. */
    MARKET
}
