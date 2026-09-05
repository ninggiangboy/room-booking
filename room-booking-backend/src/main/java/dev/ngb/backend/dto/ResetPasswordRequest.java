package dev.ngb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable request that consumes a one-time token and replaces the account password.
 *
 * <p>{@code @NotBlank} and {@code @Size} provide HTTP-boundary checks. The complete password
 * strength and BCrypt byte-length rules remain in {@code PasswordPolicy}.</p>
 *
 * @param token nonblank raw opaque token received through the reset email
 * @param newPassword raw replacement password containing at least eight characters
 */
public record ResetPasswordRequest(
        @NotBlank(message = "token must not be blank")
        String token,
        @NotBlank(
                message = "password must contain at least 8 characters, including an uppercase "
                        + "letter, a lowercase letter, a number, and a special character")
        @Size(
                min = 8,
                message = "password must contain at least 8 characters, including an uppercase "
                        + "letter, a lowercase letter, a number, and a special character")
        String newPassword) {
}
