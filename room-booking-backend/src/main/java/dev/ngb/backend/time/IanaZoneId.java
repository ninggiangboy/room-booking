package dev.ngb.backend.time;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Bean Validation constraint requiring a civil IANA time-zone identifier.
 *
 * <p>{@code @Constraint} binds the annotation to {@link IanaZoneIdValidator}, so a request field
 * carrying a property's zone is rejected at the API boundary instead of reaching persistence as
 * unusable text. {@code null} is accepted so optionality stays the job of {@code @NotNull}.</p>
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IanaZoneIdValidator.class)
public @interface IanaZoneId {

    /**
     * Returns the message shown when the value is not a civil IANA zone.
     *
     * @return validation failure message
     */
    String message() default "must be a valid IANA time-zone identifier such as Asia/Ho_Chi_Minh";

    /**
     * Returns the validation groups this constraint belongs to.
     *
     * @return constraint groups, empty by default
     */
    Class<?>[] groups() default {};

    /**
     * Returns metadata carried by Bean Validation clients.
     *
     * @return constraint payload types, empty by default
     */
    Class<? extends Payload>[] payload() default {};
}
