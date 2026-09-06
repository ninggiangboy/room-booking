package dev.ngb.backend.exception;

import dev.ngb.backend.exception.base.UnauthorizedException;

/** Reports failed authentication without revealing whether the email or password was wrong. */
public class InvalidCredentialsException extends UnauthorizedException {

    /** Stable API code shared by unknown-email and wrong-password failures. */
    public static final String CODE = "INVALID_CREDENTIALS";

    /** Creates the intentionally nonspecific authentication failure. */
    public InvalidCredentialsException() {
        super(CODE, "invalid email or password");
    }
}
