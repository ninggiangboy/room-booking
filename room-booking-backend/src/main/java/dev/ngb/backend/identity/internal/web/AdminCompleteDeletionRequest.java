package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable command submitted to the administrator deletion-completion endpoint.
 *
 * @param reasonCode stable reason recorded on the audit trail
 */
public record AdminCompleteDeletionRequest(
        @NotBlank(message = "reasonCode must not be blank")
        String reasonCode) {
}
