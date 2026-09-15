package dev.ngb.backend.identity.internal.service.authz;

import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;


/**
 * Constructs a withdrawal of authority evaluated on top of grants rather than by editing them.
 *
 * <p>{@code @Component} makes the factory injectable. Package-private visibility keeps
 * construction inside the authorization package. The caller supplies its own decision instant,
 * consistent with every other factory in this codebase.</p>
 *
 * <p>{@code capabilityGroup} is never set: {@link AuthorizationService#effectiveCapabilities}
 * documents named capability groups as scaffolding with no defined group-to-capability taxonomy, so
 * this factory only ever builds a single-capability restriction.</p>
 */
@Component
class CapabilityRestrictionFactory {

    /**
     * Builds an unsaved, unlifted restriction naming exactly one capability.
     *
     * @param principalType kind of principal being restricted
     * @param principalId identifier of that principal
     * @param capability the single capability withheld
     * @param scopeType how far the withdrawal reaches
     * @param scopeId resource the scope names; {@code null} only for {@link
     *     AuthorizationScopeType#GLOBAL}
     * @param reasonCode stable reason the restriction was applied
     * @param decisionReference reference to the risk or support decision that required it, or
     *     {@code null} when none applies
     * @param effectiveFrom UTC instant from which the withdrawal applies
     * @param effectiveUntil UTC instant from which it stops applying, or {@code null} while
     *     open-ended
     * @return restriction row to persist
     */
    CapabilityRestriction create(
            PrincipalType principalType,
            UUID principalId,
            Capability capability,
            AuthorizationScopeType scopeType,
            @Nullable UUID scopeId,
            String reasonCode,
            @Nullable String decisionReference,
            Instant effectiveFrom,
            @Nullable Instant effectiveUntil) {
        return CapabilityRestriction.builder()
                .id(UUID.randomUUID())
                .principalType(principalType)
                .principalId(principalId)
                .capability(capability.name())
                .scopeType(scopeType)
                .scopeId(scopeId)
                .reasonCode(reasonCode)
                .decisionReference(decisionReference)
                .effectiveFrom(effectiveFrom)
                .effectiveUntil(effectiveUntil)
                .build();
    }
}
