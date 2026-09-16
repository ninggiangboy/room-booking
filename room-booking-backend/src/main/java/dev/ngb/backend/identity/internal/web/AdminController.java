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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.ngb.backend.config.ApiErrorResponse;
import dev.ngb.backend.identity.internal.model.capability.CapabilityRestriction;
import dev.ngb.backend.identity.internal.repository.capability.CapabilityRestrictionRepository;
import dev.ngb.backend.identity.internal.service.account.AdminAccountService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityRestrictionService;


/**
 * Exposes operator-only account and authorization administration over HTTP.
 *
 * <p>{@code @RestController} registers the class; {@code @RequestMapping} supplies the common URL
 * prefix, which {@code SecurityConfig} gates entirely behind {@code hasAuthority("ACCOUNT_SUSPEND")}
 * — every route here shares that one coarse admin authorization rule rather than a per-endpoint
 * capability check, consistent with the codebase's current design.</p>
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Administration", description = "Operator-only account status and authorization operations.")
public class AdminController {

    private final AdminAccountService adminAccountService;
    private final CapabilityRestrictionService capabilityRestrictionService;
    private final CapabilityRestrictionRepository capabilityRestrictionRepository;
    private final Clock clock;

    /**
     * Suspends or reactivates an account.
     *
     * @param userId account holder whose status is being changed
     * @param adminId operator identifier injected from the authenticated principal
     * @param request requested status and audit reason
     * @return {@code 204 No Content}
     */
    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Change an account's status", description = "Suspends or reactivates an account, revoking its sessions when suspended.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Unsupported status transition (INVALID_ACCOUNT_STATUS_TRANSITION or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller lacks ACCOUNT_SUSPEND"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> updateAccountStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody AdminUpdateAccountStatusRequest request) {
        adminAccountService.updateStatus(userId, request.status(), request.reasonCode(), adminId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Completes a holder's self-requested deletion, closing the account.
     *
     * @param userId account holder whose deletion is being completed
     * @param adminId operator identifier injected from the authenticated principal
     * @param request audit reason
     * @return {@code 204 No Content}
     */
    @PostMapping("/users/{userId}/complete-deletion")
    @Operation(summary = "Complete a requested account deletion", description = "Closes an account currently DELETION_REQUESTED. This codebase does not yet check settlement or booking obligations before allowing it — see the identity roadmap's erasure section.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deletion completed, account closed"),
            @ApiResponse(responseCode = "400", description = "Holder is not currently DELETION_REQUESTED (DELETION_NOT_REQUESTED or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller lacks ACCOUNT_SUSPEND"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> completeDeletion(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody AdminCompleteDeletionRequest request) {
        adminAccountService.completeDeletion(userId, request.reasonCode(), adminId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Resolves an account holder's market.
     *
     * @param userId account holder whose market is being resolved
     * @param adminId operator identifier injected from the authenticated principal
     * @param request market code and audit reason
     * @return {@code 204 No Content}
     */
    @PutMapping("/users/{userId}/market")
    @Operation(summary = "Resolve an account's market", description = "Records the market an account holder operates in, unblocking consequential workflows that require canTransact().")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Market resolved"),
            @ApiResponse(responseCode = "400", description = "Unknown or unusable market code (UNKNOWN_MARKET or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller lacks ACCOUNT_SUSPEND"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> resolveMarket(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody AdminResolveMarketRequest request) {
        adminAccountService.resolveMarket(userId, request.marketCode(), request.reasonCode(), adminId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Issues a new capability restriction.
     *
     * @param adminId operator identifier injected from the authenticated principal
     * @param request restriction details
     * @return the newly issued restriction
     */
    @PostMapping("/capability-restrictions")
    @Operation(summary = "Issue a capability restriction", description = "Withdraws a single capability from a principal, subtracted from its grants at evaluation time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restriction issued", content = @Content(schema = @Schema(implementation = CapabilityRestrictionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller lacks ACCOUNT_SUSPEND"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public CapabilityRestrictionResponse issueRestriction(
            @AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody IssueCapabilityRestrictionRequest request) {
        Instant now = clock.instant();
        CapabilityRestriction restriction = capabilityRestrictionService.issue(
                request.principalType(),
                request.principalId(),
                request.capability(),
                request.scopeType(),
                request.scopeId(),
                request.reasonCode(),
                request.decisionReference(),
                request.effectiveFrom(),
                request.effectiveUntil(),
                adminId,
                now);
        return CapabilityRestrictionResponse.from(restriction);
    }

    /**
     * Lifts a capability restriction.
     *
     * @param restrictionId restriction being lifted
     * @param adminId operator identifier injected from the authenticated principal
     * @return the restriction after lifting
     */
    @DeleteMapping("/capability-restrictions/{restrictionId}")
    @Operation(summary = "Lift a capability restriction", description = "Records who lifted the restriction and when, idempotently, rather than deleting its history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restriction lifted, or already lifted", content = @Content(schema = @Schema(implementation = CapabilityRestrictionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller lacks ACCOUNT_SUSPEND"),
            @ApiResponse(responseCode = "404", description = "No restriction with that identifier (CAPABILITY_RESTRICTION_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public CapabilityRestrictionResponse liftRestriction(
            @PathVariable UUID restrictionId, @AuthenticationPrincipal UUID adminId) {
        CapabilityRestriction restriction = capabilityRestrictionService.lift(
                restrictionId, adminId, clock.instant());
        return CapabilityRestrictionResponse.from(restriction);
    }

    /**
     * Lists every restriction recorded for one principal, newest first.
     *
     * @param principalId principal whose history is being reviewed
     * @return possibly empty list of restrictions, including lifted ones
     */
    @GetMapping("/capability-restrictions")
    @Operation(summary = "List a principal's capability restrictions", description = "Returns every restriction recorded for one principal, newest first, including lifted ones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Restriction history", content = @Content(schema = @Schema(implementation = CapabilityRestrictionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Caller lacks ACCOUNT_SUSPEND"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public List<CapabilityRestrictionResponse> listRestrictions(@RequestParam UUID principalId) {
        return capabilityRestrictionRepository.findAllByPrincipalIdOrderByEffectiveFromDesc(principalId)
                .stream()
                .map(CapabilityRestrictionResponse::from)
                .toList();
    }
}
