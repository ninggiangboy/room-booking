package dev.ngb.backend.model;

/**
 * Kind of principal a capability grant or restriction applies to.
 *
 * <p>Organizations hold capabilities in their own right, separately from the members who exercise
 * them, so that removing a member does not strip the organization's authority.</p>
 */
public enum PrincipalType {
    /** An individual user account. */
    USER,
    /** An organization account holder. */
    ORGANIZATION,
    /** An internal service identity. */
    SERVICE
}
