package dev.ngb.backend.exception;

/** Signals that a verification token is missing, unknown, consumed, or expired. */
public class InvalidEmailVerificationTokenException extends DomainException {

    /** Stable API code for every unusable email-verification token state. */
    public static final String CODE = "INVALID_EMAIL_VERIFICATION_TOKEN";

    /** Creates a failure that does not reveal which token-validity check failed. */
    public InvalidEmailVerificationTokenException() {
        super(CODE, "email verification token is invalid or expired");
    }
}
