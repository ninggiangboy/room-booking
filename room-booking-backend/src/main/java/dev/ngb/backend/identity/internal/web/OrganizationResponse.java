package dev.ngb.backend.identity.internal.web;

import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;

/**
 * Public projection of an organization's account holder.
 *
 * @param id organization identifier
 * @param displayName public name the organization is presented and contracted under
 * @param status whether the organization may act
 */
public record OrganizationResponse(UUID id, String displayName, AccountHolderStatus status) {

    /**
     * Projects a persisted organization account holder into its public response shape.
     *
     * @param organization persisted organization account holder
     * @return the response projection
     */
    public static OrganizationResponse from(AccountHolder organization) {
        return new OrganizationResponse(
                organization.getId(), organization.getDisplayName(), organization.getStatus());
    }
}
