package dev.ngb.backend.identity.internal.service.authz;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;
import dev.ngb.backend.identity.internal.model.capability.GrantSource;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.CapabilityGranted;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;


/**
 * The only writer of {@code capability_grants} in this codebase.
 *
 * <p>{@code @Service} registers this as business logic; Lombok's {@code @RequiredArgsConstructor}
 * generates constructor injection for the repository. Centralizing every write here guarantees a
 * grant is always constructed with a materialized capability array consistent with its role
 * label, and that revoking a delegation always walks {@code CapabilityGrant.getDerivedFromGrantId()}
 * rather than leaving a co-host holding authority the owner believes was withdrawn.</p>
 */
@Service
@RequiredArgsConstructor
public class CapabilityGrantService {

    private final CapabilityGrantRepository capabilityGrantRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Issues a new global, role-labeled grant.
     *
     * <p>Scope is always {@link AuthorizationScopeType#GLOBAL} because that is all a role bundle
     * ever expresses; a resource-scoped grant such as "owns this listing" is created directly by
     * the domain that owns the resource, not through this method.</p>
     *
     * @param granteeType kind of principal receiving the grant
     * @param granteeId identifier of that principal
     * @param role bundle whose capabilities are materialized onto the grant
     * @param source why the grant exists
     * @param reasonCode stable reason recorded for operator review
     * @param decisionInstant the command's single decision instant, used as the grant's effective
     *     start and, since the row is inserted by explicit save rather than derived from another
     *     audited instant, its own creation time
     * @return the saved grant
     */
    public CapabilityGrant issueRoleGrant(
            PrincipalType granteeType,
            UUID granteeId,
            RoleBundle role,
            GrantSource source,
            String reasonCode,
            Instant decisionInstant) {
        CapabilityGrant grant = CapabilityGrant.builder()
                .id(UUID.randomUUID())
                .granteeType(granteeType)
                .granteeId(granteeId)
                .roleName(role.name())
                .capabilities(role.capabilities().stream().map(Enum::name).toArray(String[]::new))
                .scopeType(AuthorizationScopeType.GLOBAL)
                .scopeId(null)
                .effectiveFrom(decisionInstant)
                .reasonCode(reasonCode)
                .source(source)
                .build();
        CapabilityGrant saved = capabilityGrantRepository.save(grant);
        publishGranted(saved);
        return saved;
    }

    /**
     * Issues a resource-scoped grant delegated from another principal's own grant.
     *
     * <p>Unlike {@link #issueRoleGrant}, the scope narrows to a specific resource rather than
     * staying {@link AuthorizationScopeType#GLOBAL} — {@code parentGrant} is what proves the
     * grantor actually holds the authority it is handing down, and {@code capabilities} must be
     * validated by the caller as a subset of {@code parentGrant}'s own capabilities before this
     * method is reached; it trusts that check rather than repeating it, since only
     * {@code OrganizationService} today knows what "the organization's own authority" means for its
     * caller.</p>
     *
     * @param granteeType kind of principal receiving the delegated authority
     * @param granteeId identifier of that principal
     * @param grantorType kind of principal delegating the authority
     * @param grantorId identifier of the delegating principal
     * @param parentGrant the grantor's own grant this delegation narrows; revoking it must cascade
     *     to this one through {@link #revoke}
     * @param capabilities capability names this delegation confers, a subset of
     *     {@code parentGrant}'s own capabilities
     * @param scopeType resource kind the delegation is confined to; never {@code GLOBAL}
     * @param scopeId identifier of that resource
     * @param reasonCode stable reason recorded for operator review
     * @param decisionInstant the command's single decision instant, used as the grant's effective
     *     start and, since the row is inserted by explicit save rather than derived from another
     *     audited instant, its own creation time
     * @return the saved grant
     */
    public CapabilityGrant issueDelegatedGrant(
            PrincipalType granteeType,
            UUID granteeId,
            PrincipalType grantorType,
            UUID grantorId,
            CapabilityGrant parentGrant,
            String[] capabilities,
            AuthorizationScopeType scopeType,
            UUID scopeId,
            String reasonCode,
            Instant decisionInstant) {
        CapabilityGrant grant = CapabilityGrant.builder()
                .id(UUID.randomUUID())
                .granteeType(granteeType)
                .granteeId(granteeId)
                .grantorType(grantorType)
                .grantorId(grantorId)
                .capabilities(capabilities)
                .scopeType(scopeType)
                .scopeId(scopeId)
                .effectiveFrom(decisionInstant)
                .reasonCode(reasonCode)
                .source(GrantSource.DELEGATION)
                .derivedFromGrantId(parentGrant.getId())
                .build();
        CapabilityGrant saved = capabilityGrantRepository.save(grant);
        publishGranted(saved);
        return saved;
    }

    private void publishGranted(CapabilityGrant grant) {
        eventPublisher.publishEvent(new CapabilityGranted(
                grant.getId(),
                grant.getGranteeType().name(),
                grant.getGranteeId(),
                grant.getRoleName(),
                grant.getSource().name(),
                grant.getReasonCode(),
                grant.getEffectiveFrom()));
    }

    /**
     * Finds a principal's effective global grant for one role, if it already holds one.
     *
     * <p>Used to make issuing a role idempotent: repeating a request that grants the same role
     * should return the existing grant rather than issue a duplicate one.</p>
     *
     * @param granteeId principal being checked
     * @param role role bundle being looked for
     * @param decisionInstant the command's single decision instant
     * @return the matching effective grant, when one exists
     */
    public Optional<CapabilityGrant> findEffectiveRoleGrant(
            UUID granteeId, RoleBundle role, Instant decisionInstant) {
        return capabilityGrantRepository
                .findEffective(granteeId, AuthorizationScopeType.GLOBAL.name(), null, decisionInstant)
                .stream()
                .filter(grant -> role.name().equals(grant.getRoleName()))
                .findFirst();
    }

    /**
     * Returns the distinct role labels of a principal's currently effective global grants.
     *
     * <p>Used to build the {@code roleNames} field of the API's user projection now that {@code
     * user_roles} is gone; a resource-scoped grant with no role label is deliberately excluded,
     * since a role name is only ever a convenience label for a global bundle.</p>
     *
     * @param granteeId principal being evaluated
     * @param decisionInstant the command's single decision instant
     * @return distinct, possibly empty list of role labels, in the order the grants were returned
     */
    public List<String> effectiveRoleNames(UUID granteeId, Instant decisionInstant) {
        return capabilityGrantRepository
                .findEffective(granteeId, AuthorizationScopeType.GLOBAL.name(), null, decisionInstant)
                .stream()
                .map(CapabilityGrant::getRoleName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Withdraws a grant and cascades the withdrawal to everything derived from it.
     *
     * <p>The cascade is a single recursive walk down {@code derived_from_grant_id}, deliberately
     * shallow to match {@link CapabilityGrantRepository#findAllByDerivedFromGrantId}'s documented
     * contract. Nothing in this codebase creates a derived grant yet; the cascade exists so the
     * delegation roadmap item can rely on it without revisiting this method.</p>
     *
     * @param grant grant being withdrawn
     * @param reason stable reason for the withdrawal
     * @param decisionInstant the command's single decision instant
     */
    public void revoke(CapabilityGrant grant, String reason, Instant decisionInstant) {
        grant.setRevokedAt(decisionInstant);
        grant.setRevocationReason(reason);
        capabilityGrantRepository.save(grant);
        capabilityGrantRepository.findAllByDerivedFromGrantId(grant.getId())
                .forEach(derived -> revoke(derived, reason, decisionInstant));
    }
}
