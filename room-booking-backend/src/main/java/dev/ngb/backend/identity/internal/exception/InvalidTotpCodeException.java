package dev.ngb.backend.identity.internal.exception;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a submitted TOTP code does not match the caller's active credential within the
 * accepted time-step window.
 *
 * <p>Carries no data map: naming the accepted window or the credential's age would help an
 * attacker calibrate a guess.</p>
 */
public class InvalidTotpCodeException extends BadRequestException {

    /** Stable API code for a rejected TOTP code. */
    public static final String CODE = "INVALID_TOTP_CODE";

    /** Creates a validation failure with no further detail. */
    public InvalidTotpCodeException() {
        super(CODE, "TOTP code is invalid or expired");
    }
}
