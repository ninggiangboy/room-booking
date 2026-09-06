package dev.ngb.backend.exception;

import java.util.Map;

import dev.ngb.backend.model.User;

/** Signals that an account does not need another email-verification token. */
public class EmailAlreadyVerifiedException extends ConflictException {

    /** Stable API code for an unnecessary verification request. */
    public static final String CODE = "EMAIL_ALREADY_VERIFIED";

    /**
     * Creates a conflict containing safe account context.
     *
     * @param user account whose email is already verified
     */
    public EmailAlreadyVerifiedException(User user) {
        super(
                CODE,
                "email is already verified",
                Map.of("userId", user.getId(), "email", user.getEmail()));
    }
}
