package dev.ngb.backend.identity.internal.exception;

import dev.ngb.backend.platform.exception.base.UnauthorizedException;

/** Signals that a refresh token cannot be used to create another session. */
public class InvalidRefreshTokenException extends UnauthorizedException {

    /** Stable API code for every unusable refresh-token state. */
    public static final String CODE = "INVALID_REFRESH_TOKEN";

    /** Creates a failure that does not reveal whether the token was unknown, expired, or reused. */
    public InvalidRefreshTokenException() {
        super(CODE, "refresh token is invalid or expired");
    }
}
