package dev.ngb.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable request body for replacing the authenticated user's password.
 *
 * <p>{@code @NotBlank} rejects {@code null}, empty, and whitespace-only JSON values. {@code @Size}
 * performs the inexpensive minimum-length check at the HTTP boundary; {@code PasswordPolicy}
 * applies the complete business rule in the service layer.</p>
 *
 * @param currentPassword raw password used to confirm the caller's identity
 * @param newPassword raw replacement password, validated before hashing
 */
public record ChangePasswordRequest(
        @NotBlank(message = "currentPassword must not be blank")
        String currentPassword,
        @NotBlank(
                message = "password must contain at least 8 characters, including an uppercase "
                        + "letter, a lowercase letter, a number, and a special character")
        @Size(
                min = 8,
                message = "password must contain at least 8 characters, including an uppercase "
                        + "letter, a lowercase letter, a number, and a special character")
        String newPassword) {
}
