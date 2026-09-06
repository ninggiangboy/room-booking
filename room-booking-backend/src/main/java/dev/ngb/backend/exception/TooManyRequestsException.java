package dev.ngb.backend.exception;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/** Base type for domain failures that map to HTTP {@code 429 Too Many Requests}. */
public abstract class TooManyRequestsException extends DomainException {

    private final @Nullable Long retryAfterSeconds;

    protected TooManyRequestsException(String code, String message, Map<String, Object> data) {
        this(code, message, data, null);
    }

    protected TooManyRequestsException(
            String code,
            String message,
            Map<String, Object> data,
            @Nullable Long retryAfterSeconds) {
        super(code, message, data);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Returns the delay for the HTTP {@code Retry-After} header, when one is known.
     *
     * @return whole seconds until another request may be attempted, or {@code null}
     */
    public @Nullable Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
