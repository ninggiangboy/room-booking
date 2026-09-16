package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;

/**
 * Signals that no live membership matches the requested organization and account holder.
 */
public class OrganizationMembershipNotFoundException extends NotFoundException {

    /** Stable API code for an absent membership. */
    public static final String CODE = "ORGANIZATION_MEMBERSHIP_NOT_FOUND";

    /**
     * Creates a not-found failure naming the requested pair.
     *
     * @param organizationId organization that was checked
     * @param memberHolderId account holder that has no membership there
     */
    public OrganizationMembershipNotFoundException(UUID organizationId, UUID memberHolderId) {
        super(
                CODE,
                "no membership for that account holder in that organization",
                Map.of("organizationId", organizationId, "memberHolderId", memberHolderId));
    }
}
