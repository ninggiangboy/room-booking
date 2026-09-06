package dev.ngb.backend.service.validation;

import java.nio.charset.StandardCharsets;

import dev.ngb.backend.exception.base.ValidationException;
import org.springframework.stereotype.Component;

/**
 * Centralizes password rules so registration and password changes remain consistent.
 *
 * <p>{@code @Component} creates one stateless bean. Bean Validation performs basic HTTP checks,
 * while this service-level policy protects the same invariant for every caller, including future
 * jobs or message consumers that do not use MVC.</p>
 */
@Component
public class PasswordPolicy {

    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    static final String STRENGTH_MESSAGE = "password must contain at least 8 characters, "
            + "including an uppercase letter, a lowercase letter, a number, and a special character";

    /**
     * Validates the conventional {@code password} request field.
     *
     * @param password raw password to validate
     * @throws ValidationException when strength or BCrypt byte-length requirements fail
     */
    public void validate(String password) {
        validate("password", password);
    }

    /**
     * Validates a password while preserving the caller's field name in validation errors.
     *
     * @param field request field reported on failure
     * @param password raw password to validate
     * @throws ValidationException when strength or BCrypt byte-length requirements fail
     */
    public void validate(String field, String password) {
        if (password.length() < MIN_PASSWORD_LENGTH
                || password.codePoints().noneMatch(Character::isUpperCase)
                || password.codePoints().noneMatch(Character::isLowerCase)
                || password.codePoints().noneMatch(Character::isDigit)
                || password.codePoints().noneMatch(PasswordPolicy::isSpecialCharacter)) {
            throw new ValidationException(field, STRENGTH_MESSAGE);
        }
        // BCrypt truncates input after 72 bytes, so count encoded bytes rather than Java characters.
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new ValidationException(field, "password must not exceed 72 UTF-8 bytes");
        }
    }

    private static boolean isSpecialCharacter(int codePoint) {
        return !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
    }
}
