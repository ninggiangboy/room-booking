package dev.ngb.backend.identity.internal.service.organization;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.AccountHolderType;
import dev.ngb.backend.identity.internal.model.account.MarketContextState;
import dev.ngb.backend.identity.internal.model.account.OrganizationMember;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;

/**
 * Constructs the rows an organization's creation must persist together: the organization's own
 * account holder, and its founding {@code OWNER} membership.
 *
 * <p>{@code @Component} makes the factory injectable; it needs no collaborators, matching
 * {@code ContactChannelFactory}. Package-private visibility keeps this factory's job — never
 * assembling either row with an inline builder at a call site — internal to this package, following
 * every other factory in this codebase.</p>
 */
@Component
class OrganizationFactory {

    /**
     * Builds an unsaved organization account holder and its unsaved founding owner membership.
     *
     * <p>Like {@code UserRegistrationFactory}, the account holder is created with an unresolved
     * market context: no market-resolution signal is available at creation time, and an operator
     * must resolve it before the organization can transact. The founding membership starts
     * {@code ACTIVE} rather than {@code INVITED}, since a creator does not invite themselves.</p>
     *
     * @param creatorId account holder creating the organization; becomes its first active owner
     * @param displayName public name the organization is presented and contracted under
     * @param issuedAt creation command's single decision instant, used for rows whose creation
     *     time cannot be recovered from audited fields after the fact
     * @return the unsaved organization and its unsaved founding owner membership
     */
    NewOrganization create(UUID creatorId, String displayName, Instant issuedAt) {
        AccountHolder organization = AccountHolder.builder()
                .id(UUID.randomUUID())
                .holderType(AccountHolderType.ORGANIZATION)
                .displayName(displayName)
                .status(AccountHolderStatus.ACTIVE)
                .marketCode(null)
                .contextState(MarketContextState.UNRESOLVED)
                .build();

        OrganizationMember ownerMembership = OrganizationMember.builder()
                .id(UUID.randomUUID())
                .organizationId(organization.getId())
                .memberHolderId(creatorId)
                .memberRole(OrganizationMemberRole.OWNER)
                .status(OrganizationMemberStatus.ACTIVE)
                .invitedByAccountHolderId(creatorId)
                .invitedAt(issuedAt)
                .joinedAt(issuedAt)
                .build();

        return new NewOrganization(organization, ownerMembership);
    }

    /**
     * Every row an organization's creation must persist together.
     *
     * @param organization new organization account holder
     * @param ownerMembership new active {@code OWNER} membership for the creator
     */
    record NewOrganization(AccountHolder organization, OrganizationMember ownerMembership) {
    }
}
