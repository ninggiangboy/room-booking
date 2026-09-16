package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Immutable command submitted to confirm a contact-channel verification code.
 *
 * @param code six-digit numeric code delivered to the channel
 */
public record ConfirmContactChannelVerificationRequest(
        @NotBlank(message = "code must not be blank")
        @Pattern(regexp = "^\\d{6}$", message = "code must be exactly 6 digits")
        String code) {
}
