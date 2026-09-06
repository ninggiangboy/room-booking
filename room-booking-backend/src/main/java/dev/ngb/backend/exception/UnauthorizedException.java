package dev.ngb.backend.exception;

/** Base type for domain failures that map to HTTP {@code 401 Unauthorized}. */
public abstract class UnauthorizedException extends DomainException {

    protected UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
