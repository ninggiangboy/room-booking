package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Immutable market-resolution command submitted to the administrator market endpoint.
 *
 * @param marketCode ISO 3166-1 alpha-2 code of the market to resolve the holder into
 * @param reasonCode stable reason recorded on the audit trail
 */
public record AdminResolveMarketRequest(
        @NotBlank(message = "marketCode must not be blank")
        @Pattern(regexp = "^[A-Z]{2}$", message = "marketCode must be a 2-letter ISO 3166-1 alpha-2 code")
        String marketCode,
        @NotBlank(message = "reasonCode must not be blank")
        String reasonCode) {
}
