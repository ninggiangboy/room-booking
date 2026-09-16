package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Immutable single-field command carrying only the audit-trail reason for an action whose target
 * is already named by the URL path.
 *
 * @param reasonCode stable reason recorded on the audit trail
 */
public record AuditedActionRequest(
        @NotBlank(message = "reasonCode must not be blank")
        String reasonCode) {
}
