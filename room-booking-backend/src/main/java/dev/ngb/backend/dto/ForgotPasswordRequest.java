package dev.ngb.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable request for a password-reset email.
 *
 * <p>Jakarta Bean Validation annotations reject malformed input before the controller calls the
 * service. The service still returns the same empty response for known and unknown valid addresses
 * so this endpoint cannot be used to enumerate accounts.</p>
 *
 * @param email nonblank syntactically valid account address of at most 320 characters
 */
public record ForgotPasswordRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email is invalid")
        @Size(max = 320, message = "email is invalid")
        String email) {
}
