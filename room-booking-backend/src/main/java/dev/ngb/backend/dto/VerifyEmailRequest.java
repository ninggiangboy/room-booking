package dev.ngb.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable request containing the one-time token delivered by email.
 *
 * @param token nonblank raw URL-safe token copied from the verification link
 */
public record VerifyEmailRequest(
        @NotBlank(message = "token must not be blank") String token) {
}
