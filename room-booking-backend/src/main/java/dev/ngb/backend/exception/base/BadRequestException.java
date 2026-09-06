package dev.ngb.backend.exception.base;

import java.util.Map;

/** Base type for domain failures that map to HTTP {@code 400 Bad Request}. */
public abstract class BadRequestException extends DomainException {

    protected BadRequestException(String code, String message) {
        super(code, message);
    }

    protected BadRequestException(String code, String message, Map<String, Object> data) {
        super(code, message, data);
    }
}
