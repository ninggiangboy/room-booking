package dev.ngb.backend.identity.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.config.ApiErrorResponse;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.repository.session.AuthSessionRepository;
import dev.ngb.backend.identity.internal.service.auth.session.RefreshTokenService;
import dev.ngb.backend.identity.internal.service.contact.ContactChannelService;
import dev.ngb.backend.identity.internal.service.host.HostOnboardingService;
import dev.ngb.backend.identity.internal.service.account.UserAccountService;
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.AuditEntry;
import dev.ngb.backend.platform.AuditOutcome;
import dev.ngb.backend.platform.AuditTrailWriter;



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
    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final ContactChannelService contactChannelService;
    private final AuditTrailWriter auditTrailWriter;
    private final Clock clock;

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
    @Operation(summary = "Change the current password", description = "Checks the current password, applies password policy to the replacement, and revokes outstanding refresh tokens. Requires a fresh step-up proof (from POST /api/v1/users/me/mfa/totp/step-up) when the caller has TOTP enrolled.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed and refresh tokens revoked"),
            @ApiResponse(responseCode = "400", description = "Request or password-policy validation failure (VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token, incorrect current password (INVALID_CREDENTIALS), missing step-up proof (STEP_UP_REQUIRED), or invalid step-up proof (INVALID_STEP_UP_PROOF)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
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
     * Grants the current account the host capability, idempotently.
     *
     * @param userId authenticated account identifier
     * @return updated account projection including the host role and its capabilities
     */
    @PostMapping("/me/host-capability")
    @Operation(summary = "Grant the host capability", description = "Idempotently grants the authenticated account the HOST role. Repeating the request returns the current projection rather than a duplicate grant.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated account projection", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public UserResponse onboardHost(@AuthenticationPrincipal UUID userId) {
        return hostOnboardingService.onboard(userId);
    }

    /**
     * Requests deletion of the current account and revokes all outstanding opaque tokens.
     *
     * @param userId authenticated account identifier
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/me")
    @Operation(summary = "Request deletion of the current account", description = "Moves the authenticated account to DELETION_REQUESTED and revokes every outstanding session and opaque token. Access JWTs remain stateless and naturally expire, but every request re-checks status, so one stops authenticating immediately. An operator completes the closure separately; this codebase does not yet check settlement or booking obligations before doing so.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deletion requested, sessions and opaque tokens revoked"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> deleteOwnAccount(@AuthenticationPrincipal UUID userId) {
        userAccountService.deleteOwnAccount(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists the authenticated account's live sessions, most recently used first.
     *
     * @param userId authenticated account identifier
     * @return possibly empty list of live sessions
     */
    @GetMapping("/me/sessions")
    @Operation(summary = "List the current user's sessions", description = "Returns every session the authenticated account could still act through, most recently used first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Live sessions", content = @Content(schema = @Schema(implementation = SessionResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public List<SessionResponse> listSessions(@AuthenticationPrincipal UUID userId) {
        return authSessionRepository.findLiveForHolder(userId, clock.instant())
                .stream()
                .map(SessionResponse::from)
                .toList();
    }

    /**
     * Revokes one of the authenticated account's sessions.
     *
     * @param userId authenticated account identifier
     * @param sessionId session to revoke
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/me/sessions/{sessionId}")
    @Operation(summary = "Revoke a session", description = "Revokes one session belonging to the authenticated account and every token issued under it.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session revoked, or already revoked"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No live session with that identifier belongs to the caller (SESSION_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID sessionId) {
        Instant now = clock.instant();
        refreshTokenService.revokeSession(userId, sessionId, now, "USER_REVOKED_SESSION");
        auditTrailWriter.record(new AuditEntry(
                now,
                "session.revoked",
                "identity",
                "AuthSession",
                sessionId,
                AuditOutcome.ALLOWED,
                "USER_REVOKED_SESSION",
                ActorType.USER,
                userId,
                null));
        return ResponseEntity.noContent().build();
    }

    /**
     * Signs the authenticated account out of every session.
     *
     * @param userId authenticated account identifier
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/me/sessions")
    @Operation(summary = "Sign out everywhere", description = "Revokes every live session belonging to the authenticated account, including the one used to issue this request.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Every session revoked"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal UUID userId) {
        Instant now = clock.instant();
        refreshTokenService.revokeAllSessionsForHolder(userId, now, "USER_SIGN_OUT_EVERYWHERE");
        auditTrailWriter.record(new AuditEntry(
                now,
                "session.revoked_all",
                "identity",
                "AccountHolder",
                userId,
                AuditOutcome.ALLOWED,
                "USER_SIGN_OUT_EVERYWHERE",
                ActorType.USER,
                userId,
                null));
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists the authenticated account's live contact channels.
     *
     * @param userId authenticated account identifier
     * @return possibly empty list of the holder's current channels
     */
    @GetMapping("/me/contact-channels")
    @Operation(summary = "List the current user's contact channels", description = "Returns every live channel the authenticated account has registered, verified or not.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Live contact channels", content = @Content(schema = @Schema(implementation = ContactChannelResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public List<ContactChannelResponse> listContactChannels(@AuthenticationPrincipal UUID userId) {
        return contactChannelService.list(userId).stream().map(ContactChannelResponse::from).toList();
    }

    /**
     * Registers a new, unverified contact channel for the authenticated account.
     *
     * @param userId authenticated account identifier
     * @param request channel type, purpose, and value
     * @return the newly created channel
     */
    @PostMapping("/me/contact-channels")
    @Operation(summary = "Add a contact channel", description = "Registers a new, unverified channel. Becomes the holder's primary channel for its type only when none exists yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Channel added", content = @Content(schema = @Schema(implementation = ContactChannelResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed value for the declared channel type (INVALID_CONTACT_CHANNEL_VALUE or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/UserNotFound"),
            @ApiResponse(responseCode = "409", description = "The holder already has a live channel of this type and value (CONTACT_CHANNEL_ALREADY_REGISTERED)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<ContactChannelResponse> addContactChannel(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AddContactChannelRequest request) {
        ContactChannel channel = contactChannelService.add(
                userId, request.channelType(), request.purpose(), request.value());
        return ResponseEntity.status(201).body(ContactChannelResponse.from(channel));
    }

    /**
     * Issues and delivers a verification code for one of the caller's own channels.
     *
     * @param userId authenticated account identifier
     * @param channelId channel to verify
     * @return {@code 204 No Content}
     */
    @PostMapping("/me/contact-channels/{channelId}/verification/request")
    @Operation(summary = "Request contact-channel verification", description = "Issues a numeric code and delivers it by SMS or email depending on the channel's type.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Verification code issued"),
            @ApiResponse(responseCode = "400", description = "Channel is already verified (CONTACT_CHANNEL_ALREADY_VERIFIED)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No live channel with that identifier belongs to the caller (CONTACT_CHANNEL_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> requestContactChannelVerification(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID channelId) {
        contactChannelService.requestVerification(userId, channelId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Confirms a verification code for one of the caller's own channels.
     *
     * @param userId authenticated account identifier
     * @param channelId channel the code was requested for
     * @param request the code as the holder typed it
     * @return the channel after verification
     */
    @PostMapping("/me/contact-channels/{channelId}/verification/confirm")
    @Operation(summary = "Confirm contact-channel verification", description = "Consumes a valid code and marks the channel verified.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Channel verified", content = @Content(schema = @Schema(implementation = ContactChannelResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid, expired, or already-verified channel (INVALID_CONTACT_CHANNEL_VERIFICATION_CODE, CONTACT_CHANNEL_ALREADY_VERIFIED, or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No live channel with that identifier belongs to the caller (CONTACT_CHANNEL_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ContactChannelResponse confirmContactChannelVerification(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID channelId,
            @Valid @RequestBody ConfirmContactChannelVerificationRequest request) {
        ContactChannel channel = contactChannelService.confirmVerification(
                userId, channelId, request.code());
        return ContactChannelResponse.from(channel);
    }

    /**
     * Removes one of the caller's own, non-primary contact channels.
     *
     * @param userId authenticated account identifier
     * @param channelId channel to remove
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/me/contact-channels/{channelId}")
    @Operation(summary = "Remove a contact channel", description = "Marks a non-primary channel removed. The current primary channel for its type cannot be removed through this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Channel removed, or already removed"),
            @ApiResponse(responseCode = "400", description = "The channel is currently primary for its type (PRIMARY_CONTACT_CHANNEL_CANNOT_BE_REMOVED)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "404", description = "No live channel with that identifier belongs to the caller (CONTACT_CHANNEL_NOT_FOUND)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> removeContactChannel(
            @AuthenticationPrincipal UUID userId, @PathVariable UUID channelId) {
        contactChannelService.remove(userId, channelId);
        return ResponseEntity.noContent().build();
    }
}
