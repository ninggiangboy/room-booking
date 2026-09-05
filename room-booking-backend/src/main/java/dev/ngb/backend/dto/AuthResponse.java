package dev.ngb.backend.dto;

/**
 * Immutable successful-authentication response containing a token pair and safe user details.
 *
 * @param accessToken short-lived signed JWT used in the {@code Authorization} header
 * @param refreshToken long-lived opaque token used once to rotate the session
 * @param user public projection of the authenticated account
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
}
