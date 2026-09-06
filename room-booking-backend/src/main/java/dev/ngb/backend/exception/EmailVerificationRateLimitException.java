package dev.ngb.backend.exception;

import dev.ngb.backend.exception.base.TooManyRequestsException;

import java.time.Instant;
import java.util.Map;

/** Signals that an account requested verification email too frequently. */
public class EmailVerificationRateLimitException extends TooManyRequestsException {

    /** Stable API code for both cooldown and rolling-window limits. */
    public static final String CODE = "EMAIL_VERIFICATION_RATE_LIMITED";
    /**
     * Creates a rate-limit failure with a precise retry time.
     *
     * @param retryAt earliest UTC instant at which another request may be attempted
     * @param retryAfterSeconds whole seconds the client should wait, rounded up
     */
    public EmailVerificationRateLimitException(Instant retryAt, long retryAfterSeconds) {
        super(
                CODE,
                "too many email verification requests",
                Map.of("retryAt", retryAt, "retryAfterSeconds", retryAfterSeconds),
                retryAfterSeconds);
    }
}
