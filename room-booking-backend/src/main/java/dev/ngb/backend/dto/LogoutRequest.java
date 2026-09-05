package dev.ngb.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable request identifying the refresh-token session to revoke during logout.
 *
 * @param refreshToken nonblank raw opaque token previously returned by authentication
 */
public record LogoutRequest(
        @NotBlank(message = "refreshToken must not be blank") String refreshToken) {
}
