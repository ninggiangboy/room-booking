package dev.ngb.backend.exception;

/**
 * Signals that a password-reset token is unknown, consumed, expired, or belongs to an unusable user.
 *
 * <p>All invalid states intentionally share one public error to avoid exposing sensitive account
 * and token details.</p>
 */
public class InvalidPasswordResetTokenException extends BadRequestException {

    /** Stable API code for every unusable password-reset token state. */
    public static final String CODE = "INVALID_PASSWORD_RESET_TOKEN";

    /** Creates the intentionally nonspecific password-reset-token failure. */
    public InvalidPasswordResetTokenException() {
        super(CODE, "password reset token is invalid or expired");
    }
}
