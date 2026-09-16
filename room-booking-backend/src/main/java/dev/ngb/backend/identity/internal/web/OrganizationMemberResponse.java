package dev.ngb.backend.identity.internal.web;

import java.time.Instant;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.OrganizationMember;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberRole;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;

/**
 * Public projection of an organization membership.
 *
 * @param id membership identifier
 * @param organizationId organization the membership is in
 * @param memberHolderId account holder who is the member
 * @param role role label the member holds
 * @param status lifecycle of the membership
 * @param invitedAt instant the invitation was issued
 * @param joinedAt instant the invitation was accepted, or {@code null} while merely invited
 */
public record OrganizationMemberResponse(
        UUID id,
        UUID organizationId,
        UUID memberHolderId,
        OrganizationMemberRole role,
        OrganizationMemberStatus status,
        Instant invitedAt,
        Instant joinedAt) {

    /**
     * Projects a persisted membership into its public response shape.
     *
     * @param member persisted membership
     * @return the response projection
     */
    public static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.getId(),
                member.getOrganizationId(),
                member.getMemberHolderId(),
                member.getMemberRole(),
                member.getStatus(),
                member.getInvitedAt(),
                member.getJoinedAt());
    }
}
