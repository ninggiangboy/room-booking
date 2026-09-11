package dev.ngb.backend.model;

/**
 * The role a user holds within an organization.
 *
 * <p>The role is a convenient label for a bundle of delegated authority; the authority itself lives
 * in {@code capability_grants}, scoped to the resources the member may actually act on. An
 * organization must always retain at least one active {@link #OWNER}.</p>
 */
public enum OrganizationMemberRole {
    /** Ultimately accountable; at least one must remain active. */
    OWNER,
    /** Manages members and organization-wide settings. */
    ADMIN,
    /** Runs day-to-day operations across the organization's supply. */
    MANAGER,
    /** Operates specific listings on the owner's behalf. */
    CO_HOST,
    /** Sees and manages settlement and payout information. */
    FINANCE,
    /** Read-only access. */
    VIEWER
}
