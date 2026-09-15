package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.service.authz.Capability;


/**
 * Immutable command submitted to issue a capability restriction.
 *
 * @param principalType kind of principal being restricted
 * @param principalId identifier of that principal
 * @param capability the single capability to withhold
 * @param scopeType how far the withdrawal reaches
 * @param scopeId resource the scope names; must be {@code null} for {@link
 *     AuthorizationScopeType#GLOBAL} and non-null otherwise
 * @param reasonCode stable reason recorded for operator review
 * @param decisionReference reference to the risk or support decision that required it, or
 *     {@code null} when none applies
 * @param effectiveFrom UTC instant from which the withdrawal applies, or {@code null} to start
 *     immediately
 * @param effectiveUntil UTC instant from which it stops applying, or {@code null} while open-ended
 */
public record IssueCapabilityRestrictionRequest(
        @NotNull(message = "principalType must not be null")
        PrincipalType principalType,
        @NotNull(message = "principalId must not be null")
        UUID principalId,
        @NotNull(message = "capability must not be null")
        Capability capability,
        @NotNull(message = "scopeType must not be null")
        AuthorizationScopeType scopeType,
        @Nullable UUID scopeId,
        @NotBlank(message = "reasonCode must not be blank")
        String reasonCode,
        @Nullable String decisionReference,
        @Nullable Instant effectiveFrom,
        @Nullable Instant effectiveUntil) {
}
