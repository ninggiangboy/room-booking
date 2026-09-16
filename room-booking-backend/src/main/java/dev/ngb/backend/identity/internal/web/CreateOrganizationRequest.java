package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Immutable command to create a new organization with the caller as its first active owner.
 *
 * @param displayName public name the organization is presented and contracted under
 */
public record CreateOrganizationRequest(
        @NotBlank(message = "displayName must not be blank")
        @Size(max = 160, message = "displayName must be at most 160 characters")
        String displayName) {
}
