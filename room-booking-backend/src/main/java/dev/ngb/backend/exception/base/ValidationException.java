package dev.ngb.backend.exception.base;

import java.util.Map;

/**
 * Represents invalid client input and identifies the field that failed validation.
 *
 * <p>This complements Jakarta Bean Validation for business rules that must remain stable across
 * every transport.</p>
 */
public class ValidationException extends BadRequestException {

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

}
