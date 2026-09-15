package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;


/**
 * Signals that no account holder exists for a requested identifier.
 *
 * <p>The type name and stable {@link #CODE} predate migration {@code 037}'s retirement of the
 * legacy {@code users} table and stay unchanged: they are the public API contract, and every
 * client that already handles {@code USER_NOT_FOUND} should keep working without a change.</p>
 */
public class UserNotFoundException extends NotFoundException {

    /** Stable API code for a missing account identifier. */
    public static final String CODE = "USER_NOT_FOUND";

    /**
     * Creates a not-found failure for one account identifier.
     *
     * @param holderId missing account holder identifier
     */
    public UserNotFoundException(UUID holderId) {
        super(CODE, "user not found", Map.of("userId", holderId));
    }
}
