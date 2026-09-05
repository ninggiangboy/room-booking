package dev.ngb.backend.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Stable immutable JSON error returned by controllers and Spring Security handlers.
 *
 * <p>Jackson serializes each record component as a same-named JSON property. Clients should branch
 * on {@code status} and {@code code}, not exact human-readable message text.</p>
 *
 * @param timestamp UTC instant at which the response was created
 * @param status numeric HTTP status
 * @param code stable machine-readable application code
 * @param message human-readable explanation
 * @param data safe structured context, such as the invalid field
 * @param path request path on which the error occurred
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, Object> data,
        String path) {
}
