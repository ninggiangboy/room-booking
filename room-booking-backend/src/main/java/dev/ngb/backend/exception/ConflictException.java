package dev.ngb.backend.exception;

import java.util.Map;

/** Base type for domain failures that map to HTTP {@code 409 Conflict}. */
public abstract class ConflictException extends DomainException {

    protected ConflictException(String code, String message, Map<String, Object> data) {
        super(code, message, data);
    }

    protected ConflictException(String code, String message, Map<String, Object> data, Throwable cause) {
        super(code, message, data, cause);
    }
}
