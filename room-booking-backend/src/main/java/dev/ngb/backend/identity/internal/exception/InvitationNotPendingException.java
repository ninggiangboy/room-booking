package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;
import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a membership was not {@code INVITED} when an accept was attempted.
 */
public class InvitationNotPendingException extends BadRequestException {

    /** Stable API code for accepting a non-pending invitation. */
    public static final String CODE = "INVITATION_NOT_PENDING";

    /**
     * Creates a validation failure naming the membership's actual status.
     *
     * @param membershipId membership that was not pending
     * @param currentStatus the membership's actual status
     */
    public InvitationNotPendingException(UUID membershipId, OrganizationMemberStatus currentStatus) {
        super(
                CODE,
                "only a pending invitation can be accepted",
                Map.of("membershipId", membershipId, "currentStatus", currentStatus.name()));
    }
}
