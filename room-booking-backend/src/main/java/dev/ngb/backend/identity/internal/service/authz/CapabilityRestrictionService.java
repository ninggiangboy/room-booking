package dev.ngb.backend.identity.internal.service.authz;

import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityRestrictionRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import dev.ngb.backend.identity.internal.exception.CapabilityRestrictionNotFoundException;
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditOutcome;
import dev.ngb.backend.platform.AuditTrailWriter;


/**
 * The only writer of {@code capability_restrictions} in this codebase.
 *
 * <p>{@code @Service} registers this as business logic; Lombok generates constructor injection.
 * Mirrors {@link CapabilityGrantService}: centralizing every write here guarantees a restriction is
 * always constructed through {@link CapabilityRestrictionFactory} and that lifting one is recorded
 * rather than edited out of history, matching {@link CapabilityRestriction}'s own documentation.</p>
 */
@Service
@RequiredArgsConstructor
public class CapabilityRestrictionService {

    private final CapabilityRestrictionRepository capabilityRestrictionRepository;
    private final CapabilityRestrictionFactory capabilityRestrictionFactory;
    private final AuditTrailWriter auditTrailWriter;

    /**
     * Issues a new restriction on an operator's command.
     *
     * @param principalType kind of principal being restricted
     * @param principalId identifier of that principal
     * @param capability the single capability withheld
     * @param scopeType how far the withdrawal reaches
     * @param scopeId resource the scope names; {@code null} only for {@link
     *     AuthorizationScopeType#GLOBAL}
     * @param reasonCode stable reason recorded for operator review
     * @param decisionReference reference to the risk or support decision that required it, or
     *     {@code null} when none applies
     * @param effectiveFrom UTC instant from which the withdrawal applies, or {@code null} to start
     *     at {@code decisionInstant}
     * @param effectiveUntil UTC instant from which it stops applying, or {@code null} while
     *     open-ended
     * @param actingAdminId operator issuing the command
     * @param decisionInstant the command's single decision instant, recorded on the audit trail and
     *     used as {@code effectiveFrom} when the caller did not supply one
     * @return the saved restriction
     */
    public CapabilityRestriction issue(
            PrincipalType principalType,
            UUID principalId,
            Capability capability,
            AuthorizationScopeType scopeType,
            @Nullable UUID scopeId,
            String reasonCode,
            @Nullable String decisionReference,
            @Nullable Instant effectiveFrom,
            @Nullable Instant effectiveUntil,
            UUID actingAdminId,
            Instant decisionInstant) {
        CapabilityRestriction restriction = capabilityRestrictionFactory.create(
                principalType, principalId, capability, scopeType, scopeId, reasonCode,
                decisionReference, effectiveFrom != null ? effectiveFrom : decisionInstant,
                effectiveUntil);
        restriction = capabilityRestrictionRepository.save(restriction);

        auditTrailWriter.record(new AuditEntry(
                decisionInstant,
                "capability_restriction.issued",
                "identity",
                "CapabilityRestriction",
                restriction.getId(),
                AuditOutcome.ALLOWED,
                reasonCode,
                ActorType.OPERATOR,
                actingAdminId,
                null));
        return restriction;
    }

    /**
     * Lifts a restriction, idempotently.
     *
     * <p>A restriction already lifted is returned unchanged rather than re-recorded, matching
     * {@code RefreshTokenService.revoke}'s idempotency: repeating a lift command must be safe to
     * retry.</p>
     *
     * @param restrictionId restriction being lifted
     * @param actingAdminId operator issuing the command
     * @param decisionInstant the command's single decision instant
     * @return the restriction after lifting
     * @throws CapabilityRestrictionNotFoundException when no restriction has that identifier
     */
    public CapabilityRestriction lift(UUID restrictionId, UUID actingAdminId, Instant decisionInstant) {
        CapabilityRestriction restriction = capabilityRestrictionRepository.findById(restrictionId)
                .orElseThrow(() -> new CapabilityRestrictionNotFoundException(restrictionId));
        if (restriction.getLiftedAt() != null) {
            return restriction;
        }

        restriction.setLiftedBy(actingAdminId);
        restriction.setLiftedAt(decisionInstant);
        restriction = capabilityRestrictionRepository.save(restriction);

        auditTrailWriter.record(new AuditEntry(
                decisionInstant,
                "capability_restriction.lifted",
                "identity",
                "CapabilityRestriction",
                restriction.getId(),
                AuditOutcome.ALLOWED,
                null,
                ActorType.OPERATOR,
                actingAdminId,
                null));
        return restriction;
    }
}
