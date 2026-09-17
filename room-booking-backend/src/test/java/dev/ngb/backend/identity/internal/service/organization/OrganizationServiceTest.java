package dev.ngb.backend.identity.internal.service.organization;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.ngb.backend.identity.internal.exception.DelegatedGrantNotFoundException;
import dev.ngb.backend.identity.internal.exception.DelegationExceedsAuthorityException;
import dev.ngb.backend.identity.internal.exception.InsufficientOrganizationAuthorityException;
import dev.ngb.backend.identity.internal.exception.InvitationNotPendingException;
import dev.ngb.backend.identity.internal.exception.LastActiveOwnerException;
import dev.ngb.backend.identity.internal.exception.OrganizationMembershipConflictException;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.OrganizationMember;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;
import dev.ngb.backend.identity.internal.model.capability.AuthorizationScopeType;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;
import dev.ngb.backend.identity.internal.model.capability.PrincipalType;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.OrganizationMemberRepository;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityGrantRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.authz.Capability;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import dev.ngb.backend.identity.internal.service.authz.RoleBundle;
import dev.ngb.backend.platform.AuditTrailWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private AccountHolderFinder accountHolderFinder;
    @Mock
    private AccountHolderRepository accountHolderRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private CapabilityGrantRepository capabilityGrantRepository;
    @Mock
    private CapabilityGrantService capabilityGrantService;
    @Mock
    private OrganizationFactory organizationFactory;
    @Mock
    private AuditTrailWriter auditTrailWriter;

    private OrganizationService service;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(
                accountHolderFinder, accountHolderRepository, organizationMemberRepository,
                capabilityGrantRepository, capabilityGrantService, organizationFactory,
                auditTrailWriter);
        organizationId = UUID.randomUUID();
    }

    @Test
    void creatingAnOrganizationSavesBothRowsAndGrantsHostAuthority() {
        UUID creatorId = UUID.randomUUID();
        AccountHolder organization = organizationHolder();
        OrganizationMember ownerMembership = membership(
                organization.getId(), creatorId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE);
        when(organizationFactory.create(creatorId, "Acme Stays", NOW))
                .thenReturn(new OrganizationFactory.NewOrganization(organization, ownerMembership));
        when(accountHolderRepository.save(organization)).thenReturn(organization);

        AccountHolder result = service.create(creatorId, "Acme Stays", NOW);

        assertThat(result).isEqualTo(organization);
        verify(organizationMemberRepository).save(ownerMembership);
        verify(capabilityGrantService).issueRoleGrant(
                PrincipalType.ORGANIZATION, organization.getId(), RoleBundle.HOST,
                dev.ngb.backend.identity.internal.model.capability.GrantSource.SELF_SERVICE,
                "ORGANIZATION_CREATED", NOW);
        verify(auditTrailWriter).record(any());
    }

    @Test
    void invitingWithoutOwnerOrAdminAuthorityFails() {
        UUID actingMemberId = UUID.randomUUID();
        when(accountHolderFinder.findById(eq(organizationId), any()))
                .thenReturn(organizationHolderWithId(organizationId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId))
                .thenReturn(Optional.of(membership(
                        organizationId, actingMemberId, OrganizationMemberRole.VIEWER, OrganizationMemberStatus.ACTIVE)));

        assertThatThrownBy(() -> service.invite(
                organizationId, actingMemberId, UUID.randomUUID(), OrganizationMemberRole.CO_HOST, NOW))
                .isInstanceOf(InsufficientOrganizationAuthorityException.class);
    }

    @Test
    void invitingSomeoneAlreadyLiveInTheOrganizationFails() {
        UUID actingMemberId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        when(accountHolderFinder.findById(eq(organizationId), any()))
                .thenReturn(organizationHolderWithId(organizationId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId))
                .thenReturn(Optional.of(membership(
                        organizationId, actingMemberId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));
        when(accountHolderFinder.findActiveById(eq(inviteeId), any()))
                .thenReturn(personHolder(inviteeId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, inviteeId))
                .thenReturn(Optional.of(membership(
                        organizationId, inviteeId, OrganizationMemberRole.CO_HOST, OrganizationMemberStatus.ACTIVE)));

        assertThatThrownBy(() -> service.invite(
                organizationId, actingMemberId, inviteeId, OrganizationMemberRole.CO_HOST, NOW))
                .isInstanceOf(OrganizationMembershipConflictException.class);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void acceptingANonPendingInvitationFails() {
        UUID holderId = UUID.randomUUID();
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, holderId))
                .thenReturn(Optional.of(membership(
                        organizationId, holderId, OrganizationMemberRole.CO_HOST, OrganizationMemberStatus.ACTIVE)));

        assertThatThrownBy(() -> service.acceptInvitation(organizationId, holderId, NOW))
                .isInstanceOf(InvitationNotPendingException.class);
    }

    @Test
    void removingTheLastActiveOwnerFails() {
        UUID actingMemberId = UUID.randomUUID();
        when(accountHolderFinder.findById(eq(organizationId), any()))
                .thenReturn(organizationHolderWithId(organizationId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId))
                .thenReturn(Optional.of(membership(
                        organizationId, actingMemberId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));
        when(organizationMemberRepository.countActiveOwnersForUpdate(organizationId)).thenReturn(1L);

        assertThatThrownBy(() -> service.removeMember(
                organizationId, actingMemberId, actingMemberId, "LEAVING", NOW))
                .isInstanceOf(LastActiveOwnerException.class);
        verify(organizationMemberRepository, never()).save(any());
    }

    @Test
    void delegatingAGlobalScopeFails() {
        UUID actingMemberId = UUID.randomUUID();
        when(accountHolderFinder.findById(eq(organizationId), any()))
                .thenReturn(organizationHolderWithId(organizationId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId))
                .thenReturn(Optional.of(membership(
                        organizationId, actingMemberId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));

        assertThatThrownBy(() -> service.delegate(
                organizationId, actingMemberId, UUID.randomUUID(),
                Set.of(Capability.LISTING_MANAGE_OWN), AuthorizationScopeType.GLOBAL, null,
                "DELEGATE", NOW))
                .isInstanceOf(DelegationExceedsAuthorityException.class);
    }

    @Test
    void delegatingACapabilityTheOrganizationDoesNotHoldFails() {
        UUID actingMemberId = UUID.randomUUID();
        UUID coHostId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        when(accountHolderFinder.findById(eq(organizationId), any()))
                .thenReturn(organizationHolderWithId(organizationId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId))
                .thenReturn(Optional.of(membership(
                        organizationId, actingMemberId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, coHostId))
                .thenReturn(Optional.of(membership(
                        organizationId, coHostId, OrganizationMemberRole.CO_HOST, OrganizationMemberStatus.ACTIVE)));
        CapabilityGrant parentGrant = CapabilityGrant.builder()
                .id(UUID.randomUUID())
                .granteeType(PrincipalType.ORGANIZATION)
                .granteeId(organizationId)
                .capabilities(new String[] {Capability.CAN_DRAFT.name()})
                .scopeType(AuthorizationScopeType.GLOBAL)
                .effectiveFrom(NOW.minusSeconds(60))
                .reasonCode("ORGANIZATION_CREATED")
                .source(dev.ngb.backend.identity.internal.model.capability.GrantSource.SELF_SERVICE)
                .build();
        when(capabilityGrantService.findEffectiveRoleGrant(organizationId, RoleBundle.HOST, NOW))
                .thenReturn(Optional.of(parentGrant));

        assertThatThrownBy(() -> service.delegate(
                organizationId, actingMemberId, coHostId,
                Set.of(Capability.LISTING_MANAGE_OWN), AuthorizationScopeType.LISTING, listingId,
                "DELEGATE", NOW))
                .isInstanceOf(DelegationExceedsAuthorityException.class);
        verify(capabilityGrantService, never()).issueDelegatedGrant(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void revokingAGrantNotDelegatedByThisOrganizationFails() {
        UUID actingMemberId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        when(accountHolderFinder.findById(eq(organizationId), any()))
                .thenReturn(organizationHolderWithId(organizationId));
        when(organizationMemberRepository.findByOrganizationIdAndMemberHolderId(organizationId, actingMemberId))
                .thenReturn(Optional.of(membership(
                        organizationId, actingMemberId, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE)));
        CapabilityGrant otherOrgsGrant = CapabilityGrant.builder()
                .id(grantId)
                .granteeType(PrincipalType.PERSON)
                .granteeId(UUID.randomUUID())
                .grantorType(PrincipalType.ORGANIZATION)
                .grantorId(UUID.randomUUID())
                .capabilities(new String[] {Capability.LISTING_MANAGE_OWN.name()})
                .scopeType(AuthorizationScopeType.LISTING)
                .scopeId(UUID.randomUUID())
                .effectiveFrom(NOW.minusSeconds(60))
                .reasonCode("DELEGATE")
                .source(dev.ngb.backend.identity.internal.model.capability.GrantSource.DELEGATION)
                .build();
        when(capabilityGrantRepository.findById(grantId)).thenReturn(Optional.of(otherOrgsGrant));

        assertThatThrownBy(() -> service.revokeDelegation(
                organizationId, actingMemberId, grantId, "MISTAKE", NOW))
                .isInstanceOf(DelegatedGrantNotFoundException.class);
        verify(capabilityGrantService, never()).revoke(any(), any(), any());
    }

    private static AccountHolder organizationHolder() {
        return organizationHolderWithId(UUID.randomUUID());
    }

    private static AccountHolder organizationHolderWithId(UUID id) {
        return AccountHolder.builder()
                .id(id)
                .holderType(AccountHolderType.ORGANIZATION)
                .displayName("Acme Stays")
                .status(AccountHolderStatus.ACTIVE)
                .build();
    }

    private static AccountHolder personHolder(UUID id) {
        return AccountHolder.builder()
                .id(id)
                .holderType(AccountHolderType.PERSON)
                .displayName("Person")
                .status(AccountHolderStatus.ACTIVE)
                .build();
    }

    private static OrganizationMember membership(
            UUID organizationId, UUID memberHolderId, OrganizationMemberRole role,
            OrganizationMemberStatus status) {
        return OrganizationMember.builder()
                .id(UUID.randomUUID())
                .organizationId(organizationId)
                .memberHolderId(memberHolderId)
                .memberRole(role)
                .status(status)
                .invitedAt(NOW.minusSeconds(120))
                .build();
    }
}
