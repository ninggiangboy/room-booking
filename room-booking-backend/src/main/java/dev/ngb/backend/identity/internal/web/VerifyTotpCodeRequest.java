package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Immutable command submitted to prove control of an enrolled TOTP factor.
 *
 * @param code six-digit numeric code from the holder's authenticator app
 */
public record VerifyTotpCodeRequest(
        @NotBlank(message = "code must not be blank")
        @Pattern(regexp = "^\\d{6}$", message = "code must be exactly 6 digits")
        String code) {
}
