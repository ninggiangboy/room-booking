package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable request containing a refresh token to rotate into a new token pair.
 *
 * @param refreshToken nonblank raw opaque token; successful use consumes it
 */
public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken must not be blank") String refreshToken) {
}
