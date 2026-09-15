package dev.ngb.backend.identity.internal.exception;

import java.time.Instant;
import java.util.Map;

import dev.ngb.backend.platform.exception.base.TooManyRequestsException;


/** Signals that an identifier or account exceeded the allowed rate of failed authentication attempts. */
public class AuthAttemptRateLimitException extends TooManyRequestsException {

    /** Stable API code for velocity-limited authentication attempts. */
    public static final String CODE = "AUTH_ATTEMPT_RATE_LIMITED";

    /**
     * Creates a rate-limit failure with a precise retry time.
     *
     * @param retryAt earliest UTC instant at which another attempt may be made
     * @param retryAfterSeconds whole seconds the client should wait, rounded up
     */
    public AuthAttemptRateLimitException(Instant retryAt, long retryAfterSeconds) {
        super(
                CODE,
                "too many authentication attempts",
                Map.of("retryAt", retryAt, "retryAfterSeconds", retryAfterSeconds),
                retryAfterSeconds);
    }
}
