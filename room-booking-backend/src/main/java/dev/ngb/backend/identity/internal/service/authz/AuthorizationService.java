package dev.ngb.backend.identity.internal.service.authz;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;
import dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityGrantRepository;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityRestrictionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;


/**
 * Decides what a principal is currently authorized to do, and to which resource.
 *
 * <p>{@code @Service} registers this as business logic; Lombok's {@code @RequiredArgsConstructor}
 * generates constructor injection for the two repositories. This class is kept a pure decision
 * function over stored rows, per {@code docs/features/identity-accounts-and-access.md} § Service
 * boundaries: it never writes a grant or a restriction, and calling it twice with the same
 * arguments and the same stored rows always produces the same answer. {@link
 * CapabilityGrantService} is the only writer of grants.</p>
 *
 * <p>Evaluation has three steps: expand every effective grant into the capabilities it confers,
 * union them, then subtract every active restriction. Subtraction happening last is what makes a
 * restriction deny-over-allow -- it can suppress a capability without touching the grant that
 * explains why the principal once had it, so lifting the restriction later needs no new grant.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final CapabilityGrantRepository capabilityGrantRepository;
    private final CapabilityRestrictionRepository capabilityRestrictionRepository;

    /**
     * Evaluates the capabilities a principal holds over one resource at one instant.
     *
     * <p>Global grants and restrictions are always included alongside anything scoped to the named
     * resource, because a platform-wide grant or suspension has to apply everywhere, not only to
     * resources named individually.</p>
     *
     * @param granteeId principal being evaluated
     * @param scopeType kind of resource being acted on
     * @param scopeId identifier of that resource; {@code null} only when {@code scopeType} is
     *     {@link AuthorizationScopeType#GLOBAL}
     * @param decisionInstant the command's single decision instant
     * @return immutable, possibly empty set of capabilities currently in force
     */
    public Set<Capability> effectiveCapabilities(
            UUID granteeId,
            AuthorizationScopeType scopeType,
            @Nullable UUID scopeId,
            Instant decisionInstant) {
        List<CapabilityGrant> grants = capabilityGrantRepository.findEffective(
                granteeId, scopeType.name(), scopeId, decisionInstant);
        EnumSet<Capability> allowed = EnumSet.noneOf(Capability.class);
        for (CapabilityGrant grant : grants) {
            allowed.addAll(expand(grant));
        }

        List<CapabilityRestriction> restrictions = capabilityRestrictionRepository.findActive(
                granteeId, scopeType.name(), scopeId, decisionInstant);
        for (CapabilityRestriction restriction : restrictions) {
            // A restriction naming a single capability is subtracted directly. Named capability
            // groups are dead scaffolding today -- nothing issues one -- and expanding a group
            // needs a defined group-to-capability taxonomy that does not exist yet; see
            // docs/implementation/identity/09-roadmap.md.
            String capability = restriction.getCapability();
            if (capability != null) {
                allowed.remove(Capability.valueOf(capability));
            }
        }

        return allowed.isEmpty() ? Set.of() : Collections.unmodifiableSet(allowed);
    }

    /**
     * Evaluates the capabilities a principal holds globally, at one instant.
     *
     * <p>Convenience overload for the common case of checking platform-wide authority, such as the
     * per-request reload {@link dev.ngb.backend.identity.IdentityFacts} performs.</p>
     *
     * @param granteeId principal being evaluated
     * @param decisionInstant the command's single decision instant
     * @return immutable, possibly empty set of capabilities currently in force globally
     */
    public Set<Capability> effectiveGlobalCapabilities(UUID granteeId, Instant decisionInstant) {
        return effectiveCapabilities(granteeId, AuthorizationScopeType.GLOBAL, null, decisionInstant);
    }

    private static Set<Capability> expand(CapabilityGrant grant) {
        String roleName = grant.getRoleName();
        if (roleName != null) {
            // The role bundle, not the materialized array, is authoritative for a role-labeled
            // grant: if a bundle's capability set changes later, every grant carrying that role
            // name picks up the change without a data migration.
            return RoleBundle.valueOf(roleName).capabilities();
        }
        return Arrays.stream(grant.getCapabilities())
                .map(Capability::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Capability.class)));
    }
}
