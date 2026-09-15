package dev.ngb.backend.identity.internal.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;


/**
 * Immutable API projection of one capability restriction.
 *
 * @param id restriction identifier
 * @param principalType kind of principal being restricted
 * @param principalId identifier of that principal
 * @param capability the single capability withheld
 * @param scopeType how far the withdrawal reaches
 * @param scopeId resource the scope names, or {@code null} for a global restriction
 * @param reasonCode stable reason the restriction was applied
 * @param effectiveFrom UTC instant from which the withdrawal applies
 * @param effectiveUntil UTC instant from which it stops applying, or {@code null} while open-ended
 * @param liftedAt UTC instant the restriction was lifted, or {@code null} while still active
 */
public record CapabilityRestrictionResponse(
        UUID id,
        PrincipalType principalType,
        UUID principalId,
        @Nullable String capability,
        AuthorizationScopeType scopeType,
        @Nullable UUID scopeId,
        String reasonCode,
        Instant effectiveFrom,
        @Nullable Instant effectiveUntil,
        @Nullable Instant liftedAt) {

    /**
     * Copies a persistence entity into a safe response.
     *
     * @param restriction persistence entity to project
     * @return immutable restriction response
     */
    public static CapabilityRestrictionResponse from(CapabilityRestriction restriction) {
        return new CapabilityRestrictionResponse(
                restriction.getId(),
                restriction.getPrincipalType(),
                restriction.getPrincipalId(),
                restriction.getCapability(),
                restriction.getScopeType(),
                restriction.getScopeId(),
                restriction.getReasonCode(),
                restriction.getEffectiveFrom(),
                restriction.getEffectiveUntil(),
                restriction.getLiftedAt());
    }
}
