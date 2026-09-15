package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;


/**
 * Signals that no live session with the given identifier belongs to the caller.
 *
 * <p>Used identically whether the session id is absent, belongs to another account holder, or has
 * already been revoked, so a caller cannot use this endpoint to probe another holder's session
 * ids.</p>
 */
public class SessionNotFoundException extends NotFoundException {

    /** Stable API code for a session identifier the caller cannot act on. */
    public static final String CODE = "SESSION_NOT_FOUND";

    /**
     * Creates a not-found failure for one session identifier.
     *
     * @param sessionId session identifier that could not be revoked
     */
    public SessionNotFoundException(UUID sessionId) {
        super(CODE, "session not found", Map.of("sessionId", sessionId));
    }
}
