package dev.ngb.backend.exception;

import java.util.Map;

/** Signals that registration cannot use an email already owned by another account. */
public class EmailAlreadyRegisteredException extends DomainException {

    /** Stable API code for an email registration conflict. */
    public static final String CODE = "EMAIL_ALREADY_REGISTERED";

    /**
     * Creates a conflict discovered by a pre-insert existence query.
     *
     * @param email normalized address that is already registered
     */
    public EmailAlreadyRegisteredException(String email) {
        super(CODE, "email is already registered: " + email, Map.of("email", email));
    }

    /**
     * Creates a conflict discovered by the database unique constraint during a race.
     *
     * @param email normalized address that is already registered
     * @param cause database constraint failure
     */
    public EmailAlreadyRegisteredException(String email, Throwable cause) {
        super(CODE, "email is already registered: " + email, Map.of("email", email), cause);
    }
}
