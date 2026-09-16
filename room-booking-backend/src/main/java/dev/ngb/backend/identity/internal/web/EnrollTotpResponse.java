package dev.ngb.backend.identity.internal.web;

import dev.ngb.backend.identity.internal.service.mfa.MfaService;

/**
 * The seed and provisioning URI shown to the holder exactly once at TOTP enrollment.
 *
 * @param secret Base32-encoded seed for manual entry into an authenticator app
 * @param provisioningUri {@code otpauth://totp/} URI the app can scan as a QR code
 */
public record EnrollTotpResponse(String secret, String provisioningUri) {

    /**
     * Projects the service's enrollment result into its public response shape.
     *
     * @param enrolled the freshly enrolled factor's seed and provisioning URI
     * @return the response projection
     */
    public static EnrollTotpResponse from(MfaService.EnrolledTotp enrolled) {
        return new EnrollTotpResponse(enrolled.base32Secret(), enrolled.provisioningUri());
    }
}
