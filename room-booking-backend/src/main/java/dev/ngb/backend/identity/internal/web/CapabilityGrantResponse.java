package dev.ngb.backend.identity.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;

/**
 * Public projection of a capability grant, deliberately separate from the persistence entity.
 *
 * @param id grant identifier
 * @param granteeId principal the grant was issued to
 * @param capabilities capability names this grant confers
 * @param scopeType how far the authority reaches
 * @param scopeId resource the scope names, or {@code null} for a global grant
 * @param derivedFromGrantId the grant this one was delegated from, or {@code null} when it was not
 * @param effectiveFrom instant from which the authority applies
 * @param revokedAt instant the grant was withdrawn, or {@code null} while still in force
 */
public record CapabilityGrantResponse(
        UUID id,
        UUID granteeId,
        List<String> capabilities,
        AuthorizationScopeType scopeType,
        UUID scopeId,
        UUID derivedFromGrantId,
        Instant effectiveFrom,
        Instant revokedAt) {

    /**
     * Projects a persisted grant into its public response shape, defensively copying the
     * capability array.
     *
     * @param grant persisted grant to project
     * @return the response projection
     */
    public static CapabilityGrantResponse from(CapabilityGrant grant) {
        return new CapabilityGrantResponse(
                grant.getId(),
                grant.getGranteeId(),
                List.copyOf(List.of(grant.getCapabilities())),
                grant.getScopeType(),
                grant.getScopeId(),
                grant.getDerivedFromGrantId(),
                grant.getEffectiveFrom(),
                grant.getRevokedAt());
    }
}
