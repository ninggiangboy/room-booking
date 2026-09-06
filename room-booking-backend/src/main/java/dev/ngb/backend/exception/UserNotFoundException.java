package dev.ngb.backend.exception;

import java.util.Map;
import java.util.UUID;

/** Signals that no user exists for a requested identifier. */
public class UserNotFoundException extends NotFoundException {

    /** Stable API code for a missing account identifier. */
    public static final String CODE = "USER_NOT_FOUND";

    /**
     * Creates a not-found failure for one account identifier.
     *
     * @param userId missing account identifier
     */
    public UserNotFoundException(UUID userId) {
        super(CODE, "user not found", Map.of("userId", userId));
    }
}
