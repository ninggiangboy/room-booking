package dev.ngb.backend.exception;

import java.util.Map;

/**
 * Represents invalid client input and identifies the field that failed validation.
 *
 * <p>This complements Jakarta Bean Validation: DTO annotations protect HTTP entry points, while
 * these helpers protect service calls made from any transport.</p>
 */
public class ValidationException extends DomainException {

    /** Stable API code shared by field-oriented validation failures. */
    public static final String CODE = "VALIDATION_ERROR";

    /**
     * Creates a field-oriented validation failure.
     *
     * @param field request or command field that failed
     * @param message human-readable validation rule
     */
    public ValidationException(String field, String message) {
        super(CODE, message, Map.of("field", field));
    }

    /**
     * Requires a value to be present and reports the supplied field name on failure.
     *
     * @param field field name to return to the client
     * @param value value to check
     * @param <T> value type
     */
    public static <T> void requireNonNull(String field, T value) {
        if (value == null) {
            throw new ValidationException(field, field + " must not be null");
        }
    }

    /**
     * Requires text to contain at least one non-whitespace character.
     *
     * @param field field name to return to the client
     * @param value text to check
     */
    public static void requireNonBlank(String field, String value) {
        if (value == null) {
            throw new ValidationException(field, field + " must not be null");
        }
        if (value.isBlank()) {
            throw new ValidationException(field, field + " must not be blank");
        }
    }
}
