package dev.ngb.backend.platform.exception.base;

import java.util.Map;

/** Base type for domain failures that map to HTTP {@code 403 Forbidden}. */
public abstract class ForbiddenException extends DomainException {

    protected ForbiddenException(String code, String message, Map<String, Object> data) {
        super(code, message, data);
    }
}
