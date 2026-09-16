package dev.ngb.backend.identity.internal.service.organization;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.DelegatedGrantNotFoundException;
import dev.ngb.backend.identity.internal.exception.DelegationExceedsAuthorityException;
import dev.ngb.backend.identity.internal.exception.InsufficientOrganizationAuthorityException;
import dev.ngb.backend.identity.internal.exception.InvitationNotPendingException;
import dev.ngb.backend.identity.internal.exception.LastActiveOwnerException;
import dev.ngb.backend.identity.internal.exception.OrganizationMembershipConflictException;
import dev.ngb.backend.identity.internal.exception.OrganizationMembershipNotFoundException;
import dev.ngb.backend.identity.internal.exception.OrganizationNotFoundException;
import dev.ngb.backend.identity.internal.exception.UserNotFoundException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.OrganizationMember;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;
import dev.ngb.backend.identity.internal.model.capability.GrantSource;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.OrganizationMemberRepository;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityGrantRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.authz.Capability;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.authz.RoleBundle;
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditOutcome;
import dev.ngb.backend.platform.AuditTrailWriter;

import static dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole.ADMIN;
import static dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole.OWNER;
import static dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus.ACTIVE;
import static dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus.REMOVED;

/**
 * Lets a person create an organization, manage its membership, and delegate a scoped subset of the
 * organization's own capability authority to a co-host.
 *
 * <p>{@code @Service} identifies business logic; Lombok generates constructor injection for the
 * final dependencies. Organization authority (who may invite, remove, or delegate) is a fact about
 * one organization, not a global capability, so it is checked here rather than through Spring
 * Security's {@code hasAuthority} gate — the same account holder is {@code OWNER} of one
 * organization and a stranger to another.</p>
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final AccountHolderFinder accountHolderFinder;
    private final AccountHolderRepository accountHolderRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final CapabilityGrantRepository capabilityGrantRepository;
    private final CapabilityGrantService capabilityGrantService;
    private final OrganizationFactory organizationFactory;
    private final AuditTrailWriter auditTrailWriter;

    /**
     * Creates a new organization with the caller as its first active owner, and grants the
     * organization itself the {@link RoleBundle#HOST} bundle — the authority a delegation later
     * narrows a subset of.
     *
     * @param creatorId account holder creating the organization
     * @param displayName public name the organization is presented and contracted under
     * @param decisionInstant the command's single decision instant
     * @return the new organization's account holder
     */
    @Transactional
    public AccountHolder create(UUID creatorId, String displayName, Instant decisionInstant) {
        accountHolderFinder.findActiveById(creatorId);
        OrganizationFactory.NewOrganization built =
                organizationFactory.create(creatorId, displayName, decisionInstant);

        AccountHolder organization = accountHolderRepository.save(built.organization());
        organizationMemberRepository.save(built.ownerMembership());
        capabilityGrantService.issueRoleGrant(
                PrincipalType.ORGANIZATION,
                organization.getId(),
                RoleBundle.HOST,
                GrantSource.SELF_SERVICE,
                "ORGANIZATION_CREATED",
                decisionInstant);

        auditTrailWriter.record(new AuditEntry(
                decisionInstant,
                "organization.created",
                "identity",
                "AccountHolder",
                organization.getId(),
                AuditOutcome.ALLOWED,
                "ORGANIZATION_CREATED",
                ActorType.USER,
                creatorId,
                null));
        return organization;
    }

    /**
     * Lists every non-removed membership a person holds, active and pending invitations alike.
     *
     * @param holderId person whose memberships are listed
     * @return possibly empty list of the person's memberships
     */
    @Transactional(readOnly = true)
    public List<OrganizationMember> listMyMemberships(UUID holderId) {
        return organizationMemberRepository.findAllByMemberHolderIdAndStatusNot(holderId, REMOVED);
    }

    /**
     * Lists an organization's members in one status, for a caller who already belongs to it.
     *
     * @param organizationId organization being listed
     * @param callerId caller requesting the list; must hold any non-removed membership
     * @param status status to filter by
     * @return possibly empty list of memberships in that status
     * @throws OrganizationMembershipNotFoundException when the caller has no membership there
     */
    @Transactional(readOnly = true)
    public List<OrganizationMember> listMembers(
            UUID organizationId, UUID callerId, OrganizationMemberStatus status) {
        requireAnyMembership(organizationId, callerId);
        return organizationMemberRepository.findAllByOrganizationIdAndStatus(organizationId, status);
    }

    /**
     * Invites an account holder to join an organization with a given role.
     *
     * @param organizationId organization the invitation is for
     * @param actingMemberId caller issuing the invitation; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param inviteeHolderId account holder being invited
     * @param role role the invitee will hold once they accept
     * @param decisionInstant the command's single decision instant
     * @return the new pending membership
     * @throws OrganizationMembershipConflictException when the invitee already has a live
     *     membership in this organization
     */
    @Transactional
    public OrganizationMember invite(
            UUID organizationId, UUID actingMemberId, UUID inviteeHolderId,
            OrganizationMemberRole role, Instant decisionInstant) {
        requireOwnerOrAdmin(organizationId, actingMemberId);
        accountHolderFinder.findActiveById(
                inviteeHolderId, () -> new UserNotFoundException(inviteeHolderId));

        organizationMemberRepository
                .findByOrganizationIdAndMemberHolderId(organizationId, inviteeHolderId)
                .filter(existing -> existing.getStatus() != REMOVED)
                .ifPresent(existing -> {
                    throw new OrganizationMembershipConflictException(organizationId, inviteeHolderId);
                });

        OrganizationMember invitation = OrganizationMember.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .memberHolderId(inviteeHolderId)
                .memberRole(role)
                .status(OrganizationMemberStatus.INVITED)
                .invitedByAccountHolderId(actingMemberId)
                .invitedAt(decisionInstant)
                .build();
        return organizationMemberRepository.save(invitation);
    }

    /**
     * Accepts a pending invitation on behalf of the invited holder.
     *
     * @param organizationId organization the invitation is for
     * @param holderId invited account holder accepting
     * @param decisionInstant the command's single decision instant
     * @return the membership after acceptance
     * @throws OrganizationMembershipNotFoundException when no membership exists for this pair
     * @throws InvitationNotPendingException when the membership is not {@code INVITED}
     */
    @Transactional
    public OrganizationMember acceptInvitation(
            UUID organizationId, UUID holderId, Instant decisionInstant) {
        OrganizationMember membership = organizationMemberRepository
                .findByOrganizationIdAndMemberHolderId(organizationId, holderId)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(organizationId, holderId));
        if (membership.getStatus() != OrganizationMemberStatus.INVITED) {
            throw new InvitationNotPendingException(membership.getId(), membership.getStatus());
        }
        membership.setStatus(ACTIVE);
        membership.setJoinedAt(decisionInstant);
        return organizationMemberRepository.save(membership);
    }

    /**
     * Removes a member from an organization.
     *
     * <p>Removal is a status change, not a row deletion, so actions the member took while active
     * stay attributable.</p>
     *
     * @param organizationId organization the member belongs to
     * @param actingMemberId caller removing the member; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param targetHolderId member being removed
     * @param reasonCode stable reason recorded on the audit trail
     * @param decisionInstant the command's single decision instant
     * @throws OrganizationMembershipNotFoundException when the target has no membership there
     * @throws LastActiveOwnerException when removing this member would leave the organization
     *     with no active owner
     */
    @Transactional
    public void removeMember(
            UUID organizationId, UUID actingMemberId, UUID targetHolderId, String reasonCode,
            Instant decisionInstant) {
        requireOwnerOrAdmin(organizationId, actingMemberId);
        OrganizationMember target = organizationMemberRepository
                .findByOrganizationIdAndMemberHolderId(organizationId, targetHolderId)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(organizationId, targetHolderId));
        if (target.getStatus() == REMOVED) {
            return;
        }

        if (target.getMemberRole() == OWNER && target.getStatus() == ACTIVE) {
            long activeOwners = organizationMemberRepository.countActiveOwnersForUpdate(organizationId);
            if (activeOwners <= 1) {
                throw new LastActiveOwnerException(organizationId);
            }
        }

        target.setStatus(REMOVED);
        target.setRemovedAt(decisionInstant);
        organizationMemberRepository.save(target);

        auditTrailWriter.record(new AuditEntry(
                decisionInstant,
                "organization.member_removed",
                "identity",
                "OrganizationMember",
                target.getId(),
                AuditOutcome.ALLOWED,
                reasonCode,
                ActorType.USER,
                actingMemberId,
                null));
    }

    /**
     * Delegates a scoped subset of the organization's own {@link RoleBundle#HOST} authority to one
     * of its members.
     *
     * <p>The delegation must narrow to a specific resource — {@code scopeType} may not be
     * {@link AuthorizationScopeType#GLOBAL} — and {@code capabilities} must be a subset of what the
     * organization's own effective {@code HOST} grant confers, so a co-host can never end up with
     * more authority than the organization delegating it holds.</p>
     *
     * @param organizationId delegating organization
     * @param actingMemberId caller issuing the delegation; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param coHostHolderId member receiving the delegated authority; must be an active member
     * @param capabilities capabilities to delegate, a subset of the organization's own {@code HOST}
     *     grant
     * @param scopeType resource kind the delegation is confined to
     * @param scopeId identifier of that resource
     * @param reasonCode stable reason recorded for operator review
     * @param decisionInstant the command's single decision instant
     * @return the newly issued delegated grant
     * @throws DelegationExceedsAuthorityException when the scope is not narrowed, the organization
     *     holds no effective {@code HOST} grant, or the requested capabilities exceed it
     */
    @Transactional
    public CapabilityGrant delegate(
            UUID organizationId, UUID actingMemberId, UUID coHostHolderId,
            Set<Capability> capabilities, AuthorizationScopeType scopeType, UUID scopeId,
            String reasonCode, Instant decisionInstant) {
        requireOwnerOrAdmin(organizationId, actingMemberId);
        if (scopeType == AuthorizationScopeType.GLOBAL) {
            throw new DelegationExceedsAuthorityException(
                    organizationId, "a delegation must narrow to a specific resource scope");
        }
        requireAnyMembership(organizationId, coHostHolderId);

        CapabilityGrant parentGrant = capabilityGrantService
                .findEffectiveRoleGrant(organizationId, RoleBundle.HOST, decisionInstant)
                .orElseThrow(() -> new DelegationExceedsAuthorityException(
                        organizationId, "the organization holds no effective HOST authority to delegate"));

        Set<String> parentCapabilities = Set.of(parentGrant.getCapabilities());
        String[] requested = capabilities.stream().map(Enum::name).toArray(String[]::new);
        for (String capability : requested) {
            if (!parentCapabilities.contains(capability)) {
                throw new DelegationExceedsAuthorityException(
                        organizationId,
                        "requested capability " + capability + " exceeds the organization's own authority");
            }
        }

        CapabilityGrant delegated = capabilityGrantService.issueDelegatedGrant(
                PrincipalType.PERSON,
                coHostHolderId,
                PrincipalType.ORGANIZATION,
                organizationId,
                parentGrant,
                requested,
                scopeType,
                scopeId,
                reasonCode,
                decisionInstant);

        auditTrailWriter.record(new AuditEntry(
                decisionInstant,
                "organization.capability_delegated",
                "identity",
                "CapabilityGrant",
                delegated.getId(),
                AuditOutcome.ALLOWED,
                reasonCode,
                ActorType.USER,
                actingMemberId,
                null));
        return delegated;
    }

    /**
     * Revokes a grant this organization previously delegated.
     *
     * @param organizationId delegating organization
     * @param actingMemberId caller revoking the delegation; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param grantId delegated grant being revoked
     * @param reasonCode stable reason for the withdrawal
     * @param decisionInstant the command's single decision instant
     * @throws DelegatedGrantNotFoundException when no grant delegated by this organization matches
     */
    @Transactional
    public void revokeDelegation(
            UUID organizationId, UUID actingMemberId, UUID grantId, String reasonCode,
            Instant decisionInstant) {
        requireOwnerOrAdmin(organizationId, actingMemberId);
        CapabilityGrant grant = capabilityGrantRepository.findById(grantId)
                .filter(candidate -> candidate.getGrantorType() == PrincipalType.ORGANIZATION)
                .filter(candidate -> organizationId.equals(candidate.getGrantorId()))
                .orElseThrow(() -> new DelegatedGrantNotFoundException(grantId));
        capabilityGrantService.revoke(grant, reasonCode, decisionInstant);

        auditTrailWriter.record(new AuditEntry(
                decisionInstant,
                "organization.delegation_revoked",
                "identity",
                "CapabilityGrant",
                grant.getId(),
                AuditOutcome.ALLOWED,
                reasonCode,
                ActorType.USER,
                actingMemberId,
                null));
    }

    private void requireOwnerOrAdmin(UUID organizationId, UUID actingMemberId) {
        requireOrganization(organizationId);
        organizationMemberRepository
                .findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId)
                .filter(candidate -> candidate.getStatus() == ACTIVE)
                .filter(candidate -> candidate.getMemberRole() == OWNER
                        || candidate.getMemberRole() == ADMIN)
                .orElseThrow(() -> new InsufficientOrganizationAuthorityException(organizationId));
    }

    private void requireAnyMembership(UUID organizationId, UUID holderId) {
        organizationMemberRepository
                .findByOrganizationIdAndMemberHolderId(organizationId, holderId)
                .filter(candidate -> candidate.getStatus() == ACTIVE)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(organizationId, holderId));
    }

    private void requireOrganization(UUID organizationId) {
        AccountHolder holder = accountHolderFinder.findById(
                organizationId, () -> new OrganizationNotFoundException(organizationId));
        if (holder.getHolderType() != AccountHolderType.ORGANIZATION) {
            throw new OrganizationNotFoundException(organizationId);
        }
    }
}
