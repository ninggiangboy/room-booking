package dev.ngb.backend.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ngb.backend.config.ApiErrorResponse;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.OrganizationMember;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;
import dev.ngb.backend.identity.internal.model.capability.CapabilityGrant;
import dev.ngb.backend.identity.internal.service.organization.OrganizationService;

/**
 * Exposes organization creation, membership, and co-host delegation over HTTP.
 *
 * <p>{@code @RestController} registers the class; {@code @RequestMapping} supplies the common URL
 * prefix. Authorization for every route beyond authentication itself is resource-scoped — whether
 * the caller is an active {@code OWNER} or {@code ADMIN} of the specific organization named in the
 * path — and is therefore checked inside {@code OrganizationService}, not by a route-level
 * {@code hasAuthority} rule the way {@code /api/v1/admin/**} is.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization creation, membership, and co-host delegation.")
public class OrganizationController {

    private final OrganizationService organizationService;
    private final Clock clock;

    /**
     * Creates a new organization with the caller as its first active owner.
     *
     * @param callerId authenticated account identifier
     * @param request organization display name
     * @return the newly created organization
     */
    @PostMapping
    @Operation(summary = "Create an organization", description = "Creates a new organization account holder, makes the caller its first active OWNER, and grants the organization the HOST role bundle.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Organization created", content = @Content(schema = @Schema(implementation = OrganizationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<OrganizationResponse> create(
            @AuthenticationPrincipal UUID callerId,
            @Valid @RequestBody CreateOrganizationRequest request) {
        AccountHolder organization = organizationService.create(
                callerId, request.displayName(), clock.instant());
        return ResponseEntity.status(201).body(OrganizationResponse.from(organization));
    }

    /**
     * Lists every non-removed membership the caller holds, active and pending invitations alike.
     *
     * @param callerId authenticated account identifier
     * @return possibly empty list of the caller's memberships
     */
    @GetMapping("/mine")
    @Operation(summary = "List my organization memberships", description = "Returns every non-removed membership the caller holds, active and pending invitations alike.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Caller's memberships", content = @Content(schema = @Schema(implementation = OrganizationMemberResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public List<OrganizationMemberResponse> listMyMemberships(@AuthenticationPrincipal UUID callerId) {
        return organizationService.listMyMemberships(callerId).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
    }

    /**
     * Lists an organization's members in one status.
     *
     * @param organizationId organization being listed
     * @param callerId authenticated account identifier; must hold any active membership
     * @param status status to filter by
     * @return possibly empty list of memberships in that status
     */
    @GetMapping("/{organizationId}/members")
    @Operation(summary = "List an organization's members", description = "Returns the organization's members in one status. The caller must hold any active membership in the organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members in that status", content = @Content(schema = @Schema(implementation = OrganizationMemberResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Caller has no membership in this organization (ORGANIZATION_MEMBERSHIP_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public List<OrganizationMemberResponse> listMembers(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerId,
            @RequestParam OrganizationMemberStatus status) {
        return organizationService.listMembers(organizationId, callerId, status).stream()
                .map(OrganizationMemberResponse::from)
                .toList();
    }

    /**
     * Invites an account holder to join an organization with a given role.
     *
     * @param organizationId organization the invitation is for
     * @param callerId authenticated account identifier; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param request invitee and role
     * @return the new pending membership
     */
    @PostMapping("/{organizationId}/members")
    @Operation(summary = "Invite an organization member", description = "Invites an account holder to join with a given role. The caller must be an active OWNER or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Invitation issued", content = @Content(schema = @Schema(implementation = OrganizationMemberResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller is not an active OWNER or ADMIN (INSUFFICIENT_ORGANIZATION_AUTHORITY)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "409", description = "Invitee already has a live membership (ORGANIZATION_MEMBERSHIP_ALREADY_EXISTS)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<OrganizationMemberResponse> inviteMember(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerId,
            @Valid @RequestBody InviteOrganizationMemberRequest request) {
        OrganizationMember membership = organizationService.invite(
                organizationId, callerId, request.inviteeHolderId(), request.role(), clock.instant());
        return ResponseEntity.status(201).body(OrganizationMemberResponse.from(membership));
    }

    /**
     * Accepts a pending invitation on behalf of the authenticated account holder.
     *
     * @param organizationId organization the invitation is for
     * @param callerId authenticated, invited account identifier
     * @return the membership after acceptance
     */
    @PostMapping("/{organizationId}/members/me/accept")
    @Operation(summary = "Accept an organization invitation", description = "Accepts the caller's own pending invitation to this organization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted", content = @Content(schema = @Schema(implementation = OrganizationMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Membership is not pending (INVITATION_NOT_PENDING)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No membership for the caller in this organization (ORGANIZATION_MEMBERSHIP_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public OrganizationMemberResponse acceptInvitation(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerId) {
        OrganizationMember membership = organizationService.acceptInvitation(
                organizationId, callerId, clock.instant());
        return OrganizationMemberResponse.from(membership);
    }

    /**
     * Removes a member from an organization.
     *
     * @param organizationId organization the member belongs to
     * @param memberHolderId member being removed
     * @param callerId authenticated account identifier; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param request audit reason
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{organizationId}/members/{memberHolderId}")
    @Operation(summary = "Remove an organization member", description = "Marks a membership removed rather than deleting it, so actions taken while active stay attributable. The caller must be an active OWNER or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Member removed, or already removed"),
            @ApiResponse(responseCode = "400", description = "Removing this member would leave the organization with no active owner (LAST_ACTIVE_OWNER)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller is not an active OWNER or ADMIN (INSUFFICIENT_ORGANIZATION_AUTHORITY)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No membership for that account holder in this organization (ORGANIZATION_MEMBERSHIP_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID memberHolderId,
            @AuthenticationPrincipal UUID callerId,
            @Valid @RequestBody AuditedActionRequest request) {
        organizationService.removeMember(
                organizationId, callerId, memberHolderId, request.reasonCode(), clock.instant());
        return ResponseEntity.noContent().build();
    }

    /**
     * Delegates a scoped subset of the organization's own authority to a co-host.
     *
     * @param organizationId delegating organization
     * @param callerId authenticated account identifier; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param request co-host, capabilities, scope, and audit reason
     * @return the newly issued delegated grant
     */
    @PostMapping("/{organizationId}/delegations")
    @Operation(summary = "Delegate capabilities to a co-host", description = "Grants a member a resource-scoped subset of the organization's own effective HOST authority. The caller must be an active OWNER or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Delegation issued", content = @Content(schema = @Schema(implementation = CapabilityGrantResponse.class))),
            @ApiResponse(responseCode = "400", description = "Scope not narrowed, or requested capabilities exceed the organization's own authority (DELEGATION_EXCEEDS_AUTHORITY or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller is not an active OWNER or ADMIN (INSUFFICIENT_ORGANIZATION_AUTHORITY)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Co-host is not an active member of this organization (ORGANIZATION_MEMBERSHIP_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<CapabilityGrantResponse> delegate(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerId,
            @Valid @RequestBody DelegateCapabilitiesRequest request) {
        CapabilityGrant grant = organizationService.delegate(
                organizationId,
                callerId,
                request.coHostHolderId(),
                request.capabilities(),
                request.scopeType(),
                request.scopeId(),
                request.reasonCode(),
                clock.instant());
        return ResponseEntity.status(201).body(CapabilityGrantResponse.from(grant));
    }

    /**
     * Revokes a grant this organization previously delegated.
     *
     * @param organizationId delegating organization
     * @param grantId delegated grant being revoked
     * @param callerId authenticated account identifier; must be an active {@code OWNER} or
     *     {@code ADMIN}
     * @param request audit reason
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{organizationId}/delegations/{grantId}")
    @Operation(summary = "Revoke a delegated grant", description = "Withdraws a grant this organization previously delegated. The caller must be an active OWNER or ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Delegation revoked"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller is not an active OWNER or ADMIN (INSUFFICIENT_ORGANIZATION_AUTHORITY)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No grant delegated by this organization with that id (DELEGATED_GRANT_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> revokeDelegation(
            @PathVariable UUID organizationId,
            @PathVariable UUID grantId,
            @AuthenticationPrincipal UUID callerId,
            @Valid @RequestBody AuditedActionRequest request) {
        organizationService.revokeDelegation(
                organizationId, callerId, grantId, request.reasonCode(), clock.instant());
        return ResponseEntity.noContent().build();
    }
}
