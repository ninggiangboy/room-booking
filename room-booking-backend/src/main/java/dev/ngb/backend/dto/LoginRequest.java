package dev.ngb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable credentials submitted to the login endpoint.
 *
 * <p>The component annotations are Jakarta Bean Validation constraints. Spring evaluates them
 * because the controller parameter uses {@code @Valid}; invalid input never reaches the service.</p>
 *
 * @param email account email constrained to a nonblank, valid address of at most 320 characters
 * @param password nonblank raw password compared with the stored password hash
 */
public record LoginRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email is invalid")
        @Size(max = 320, message = "email is invalid")
        String email,
        @NotBlank(message = "password must not be blank")
        String password) {
}
