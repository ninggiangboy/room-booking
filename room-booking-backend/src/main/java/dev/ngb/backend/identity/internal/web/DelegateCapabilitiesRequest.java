package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.service.authz.Capability;

/**
 * Immutable command to delegate a scoped subset of an organization's own authority to a co-host.
 *
 * @param coHostHolderId member receiving the delegated authority; must be an active member
 * @param capabilities capabilities to delegate; must be a subset of the organization's own
 *     effective {@code HOST} grant
 * @param scopeType resource kind the delegation is confined to; {@code GLOBAL} is rejected
 * @param scopeId identifier of that resource
 * @param reasonCode stable reason recorded for operator review
 */
public record DelegateCapabilitiesRequest(
        @NotNull(message = "coHostHolderId must not be null")
        UUID coHostHolderId,
        @NotEmpty(message = "capabilities must not be empty")
        Set<Capability> capabilities,
        @NotNull(message = "scopeType must not be null")
        AuthorizationScopeType scopeType,
        @NotNull(message = "scopeId must not be null")
        UUID scopeId,
        @NotBlank(message = "reasonCode must not be blank")
        String reasonCode) {
}
