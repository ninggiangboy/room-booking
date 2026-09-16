package dev.ngb.backend.identity.internal.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole;

/**
 * Immutable command to invite an account holder to join an organization.
 *
 * @param inviteeHolderId account holder being invited
 * @param role role the invitee will hold once they accept
 */
public record InviteOrganizationMemberRequest(
        @NotNull(message = "inviteeHolderId must not be null")
        UUID inviteeHolderId,
        @NotNull(message = "role must not be null")
        OrganizationMemberRole role) {
}
