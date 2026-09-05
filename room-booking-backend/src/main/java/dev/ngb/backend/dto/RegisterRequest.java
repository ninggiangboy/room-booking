package dev.ngb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable data required to create a guest account.
 *
 * <p>Record-component annotations define HTTP-boundary validation. They do not normalize values;
 * the authentication service still trims names, lowercases email, and applies password policy.</p>
 *
 * @param email nonblank syntactically valid address of at most 320 characters
 * @param password raw password with at least eight characters
 * @param displayName nonblank public name of at most 120 characters
 */
public record RegisterRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email is invalid")
        @Size(max = 320, message = "email is invalid")
        String email,
        @NotBlank(
                message = "password must contain at least 8 characters, including an uppercase "
                        + "letter, a lowercase letter, a number, and a special character")
        @Size(
                min = 8,
                message = "password must contain at least 8 characters, including an uppercase "
                        + "letter, a lowercase letter, a number, and a special character")
        String password,
        @NotBlank(message = "displayName must not be blank")
        @Size(max = 120, message = "displayName must not exceed 120 characters")
        String displayName) {
}
