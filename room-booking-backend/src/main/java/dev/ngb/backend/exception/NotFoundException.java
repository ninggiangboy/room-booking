package dev.ngb.backend.exception;

import java.util.Map;

/** Base type for domain failures that map to HTTP {@code 404 Not Found}. */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String code, String message, Map<String, Object> data) {
        super(code, message, data);
    }
}
