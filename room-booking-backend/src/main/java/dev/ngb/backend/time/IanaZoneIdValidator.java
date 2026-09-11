package dev.ngb.backend.time;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jspecify.annotations.Nullable;

/**
 * Applies {@link IanaZoneId} by delegating to {@link IanaTimeZone}.
 *
 * <p>Implementing {@link ConstraintValidator} lets Bean Validation instantiate this class for
 * annotated string members. The check is delegated rather than duplicated so the API boundary and
 * the domain agree on exactly which identifiers are acceptable.</p>
 */
public class IanaZoneIdValidator implements ConstraintValidator<IanaZoneId, String> {

    /**
     * Accepts a known civil zone identifier and treats {@code null} as valid.
     *
     * @param value candidate zone identifier
     * @param context Bean Validation context, unused because the default message is sufficient
     * @return {@code true} when the value is absent or names a civil IANA zone
     */
    @Override
    public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
        return value == null || IanaTimeZone.isValidCivilZone(value);
    }
}
