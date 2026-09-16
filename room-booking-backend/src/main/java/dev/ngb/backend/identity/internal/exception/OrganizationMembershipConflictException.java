package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.ConflictException;

/**
 * Signals that an invitation was sent to an account holder who already has a live (not
 * {@code REMOVED}) membership in the organization.
 */
public class OrganizationMembershipConflictException extends ConflictException {

    /** Stable API code for a duplicate invitation. */
    public static final String CODE = "ORGANIZATION_MEMBERSHIP_ALREADY_EXISTS";

    /**
     * Creates a conflict failure naming the organization and the already-a-member holder.
     *
     * @param organizationId organization the invitation targeted
     * @param memberHolderId account holder who is already a live member
     */
    public OrganizationMembershipConflictException(UUID organizationId, UUID memberHolderId) {
        super(
                CODE,
                "this account holder already has a live membership in this organization",
                Map.of("organizationId", organizationId, "memberHolderId", memberHolderId));
    }
}
