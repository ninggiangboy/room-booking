package dev.ngb.backend.controller;

import dev.ngb.backend.dto.ChangePasswordRequest;
import dev.ngb.backend.dto.EmailExistsResponse;
import dev.ngb.backend.dto.HostOnboardingRequest;
import dev.ngb.backend.dto.HostOnboardingResponse;
import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.service.host.HostOnboardingService;
import dev.ngb.backend.service.account.UserAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import dev.ngb.backend.dto.ApiErrorResponse;

/**
 * Exposes profile and account-management operations for users.
 *
 * <p>Spring registers the class through {@code @RestController}; {@code @RequestMapping} prefixes
 * every route, and Lombok generates the constructor Spring uses to inject the final service.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Current-user account and host-profile operations.")
public class UserController {

    private final UserAccountService userAccountService;
    private final HostOnboardingService hostOnboardingService;

    /**
     * Returns the account represented by the current access token.
     *
     * @param userId UUID injected from Spring Security's authenticated principal
     * @return safe public account details
     */
    @GetMapping("/me")
    @Operation(summary = "Get the current user", description = "Returns the safe account projection for the access token's subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public UserResponse me(@AuthenticationPrincipal UUID userId) {
        return userAccountService.getUser(userId);
    }

    /**
     * Checks a normalized email address without exposing the associated account.
     *
     * <p>{@code @RequestParam} reads the value from the URL query string.</p>
     *
     * @param email address supplied as the {@code email} query parameter
     * @return an immutable response containing only the existence flag
     */
    @GetMapping("/email-exists")
    @SecurityRequirements
    @Operation(summary = "Check email availability", description = "Reports whether the supplied email is registered without exposing account details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email existence result", content = @Content(schema = @Schema(implementation = EmailExistsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or malformed email query parameter (VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public EmailExistsResponse emailExists(@RequestParam String email) {
        return new EmailExistsResponse(userAccountService.emailExists(email));
    }

    /**
     * Replaces the authenticated user's password after checking the current password.
     *
     * @param userId UUID injected from the authenticated principal
     * @param request Bean-validated current and replacement passwords
     * @return {@code 204 No Content}
     */
    @PutMapping("/me/password")
    @Operation(summary = "Change the current password", description = "Checks the current password, applies password policy to the replacement, and revokes outstanding refresh tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed and refresh tokens revoked"),
            @ApiResponse(responseCode = "400", description = "Request or password-policy validation failure (VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token, or incorrect current password (INVALID_CREDENTIALS)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userAccountService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Atomically creates a host profile and grants the current account the host role.
     *
     * @param userId authenticated account identifier
     * @param request optional host biography
     * @return updated account roles and host profile
     */
    @PostMapping("/me/host-profile")
    @Operation(summary = "Create a host profile", description = "Atomically creates the authenticated user's host profile and grants the HOST role. Repeating the request returns the existing profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated user and host profile", content = @Content(schema = @Schema(implementation = HostOnboardingResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public HostOnboardingResponse onboardHost(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody HostOnboardingRequest request) {
        return hostOnboardingService.onboard(userId, request);
    }

    /**
     * Soft-deletes the current account and revokes all outstanding opaque tokens.
     *
     * @param userId authenticated account identifier
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete the current account", description = "Soft-deletes the authenticated account and revokes every outstanding opaque token. Access JWTs remain stateless and naturally expire after their configured lifetime.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account soft-deleted and opaque tokens revoked"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> deleteOwnAccount(@AuthenticationPrincipal UUID userId) {
        userAccountService.deleteOwnAccount(userId);
        return ResponseEntity.noContent().build();
    }
}
