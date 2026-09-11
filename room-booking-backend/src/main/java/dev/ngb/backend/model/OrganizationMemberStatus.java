package dev.ngb.backend.model;

/**
 * Lifecycle of a person's membership in an organization.
 *
 * <p>Removed members are retained rather than deleted, because the actions they took while active
 * must remain attributable.</p>
 */
public enum OrganizationMemberStatus {
    /** Invited but not yet accepted; holds no authority. */
    INVITED,
    /** Accepted and able to act within granted capabilities. */
    ACTIVE,
    /** Temporarily barred; grants derived from the membership do not evaluate. */
    SUSPENDED,
    /** No longer a member; retained so past actions stay attributable. */
    REMOVED
}
