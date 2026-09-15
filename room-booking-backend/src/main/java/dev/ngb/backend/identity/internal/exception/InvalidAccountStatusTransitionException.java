package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.platform.exception.base.BadRequestException;


/**
 * Signals that an administrator requested a status transition this endpoint does not support.
 *
 * <p>Only {@code ACTIVE} and {@code SUSPENDED} may be requested here. {@code CLOSED} remains
 * reachable only through the account holder's own self-service closure, and every other current
 * status is left unchanged rather than silently accepted.</p>
 */
public class InvalidAccountStatusTransitionException extends BadRequestException {

    /** Stable API code for an unsupported administrator status transition. */
    public static final String CODE = "INVALID_ACCOUNT_STATUS_TRANSITION";

    /**
     * Creates a validation failure describing the rejected transition.
     *
     * @param holderId account holder the transition was requested for
     * @param currentStatus the holder's status before the request
     * @param requestedStatus the status the request asked for
     */
    public InvalidAccountStatusTransitionException(
            UUID holderId, AccountHolderStatus currentStatus, AccountHolderStatus requestedStatus) {
        super(
                CODE,
                "only ACTIVE and SUSPENDED may be set through this endpoint",
                Map.of(
                        "userId", holderId,
                        "currentStatus", currentStatus.name(),
                        "requestedStatus", requestedStatus.name()));
    }
}
