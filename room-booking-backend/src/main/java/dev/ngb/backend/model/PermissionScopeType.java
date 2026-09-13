package dev.ngb.backend.model;

/**
 * The resource scope a permission is exercised at, mirroring the capability scopes migration 014
 * grants authority against.
 */
public enum PermissionScopeType {

    /** Everywhere; no resource to name. */
    GLOBAL,

    /** Within one organization. */
    ORGANIZATION,

    /** Within one property. */
    PROPERTY,

    /** Within one listing. */
    LISTING,

    /** Within one booking. */
    BOOKING,

    /** Within one market. */
    MARKET
}
