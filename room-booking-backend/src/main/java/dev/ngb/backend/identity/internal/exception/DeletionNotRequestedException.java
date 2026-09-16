package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that an operator tried to complete a deletion for a holder not currently in {@link
 * AccountHolderStatus#DELETION_REQUESTED}.
 */
public class DeletionNotRequestedException extends BadRequestException {

    /** Stable API code for completing a deletion that was never requested. */
    public static final String CODE = "DELETION_NOT_REQUESTED";

    /**
     * Creates a validation failure naming the holder's actual status.
     *
     * @param holderId account holder the completion was requested for
     * @param currentStatus the holder's status before the request
     */
    public DeletionNotRequestedException(UUID holderId, AccountHolderStatus currentStatus) {
        super(
                CODE,
                "holder is not awaiting deletion completion",
                Map.of("userId", holderId, "currentStatus", currentStatus.name()));
    }
}
