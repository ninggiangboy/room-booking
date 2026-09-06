package dev.ngb.backend.controller;

import dev.ngb.backend.dto.AuthResponse;
import dev.ngb.backend.dto.ForgotPasswordRequest;
import dev.ngb.backend.dto.LoginRequest;
import dev.ngb.backend.dto.LogoutRequest;
import dev.ngb.backend.dto.RefreshTokenRequest;
import dev.ngb.backend.dto.RegisterRequest;
import dev.ngb.backend.dto.ResetPasswordRequest;
import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.dto.VerifyEmailRequest;
import dev.ngb.backend.service.auth.AuthenticationService;
import dev.ngb.backend.service.auth.EmailVerificationService;
import dev.ngb.backend.service.auth.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import dev.ngb.backend.dto.ApiErrorResponse;

/**
 * Exposes registration, session-token, and email-verification operations over HTTP.
 *
 * <p>{@code @RestController} registers the class and serializes return values as JSON.
 * {@code @RequestMapping} supplies the common URL prefix. Lombok's
 * {@code @RequiredArgsConstructor} generates a constructor for the two {@code final} services,
 * which Spring uses for dependency injection.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, token sessions, password recovery, and email verification.")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    /**
     * Creates a guest account and returns its first access/refresh token pair.
     *
     * <p>{@code @Valid} runs the record's Bean Validation constraints before this method;
     * {@code @RequestBody} deserializes JSON. {@code @PostMapping} binds the method to HTTP POST.</p>
     *
     * @param request validated registration JSON
     * @return {@code 201 Created} with the authentication response
     */
    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Register a guest account", description = "Creates a guest account and immediately issues its first access and refresh token pair.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created and token pair issued", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Request validation or password-policy failure (VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email is already registered (EMAIL_ALREADY_REGISTERED)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authenticationService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticates an existing account and starts a new token session.
     *
     * @param request validated email and raw password
     * @return newly issued access/refresh tokens and user details
     */
    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in", description = "Validates credentials and creates a new token session. The response does not reveal whether the email or password was invalid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token pair and authenticated user", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials (INVALID_CREDENTIALS)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    /**
     * Rotates a usable refresh token and returns a new token pair.
     *
     * @param request validated raw refresh token
     * @return replacement token pair and current user details
     */
    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Refresh a token session", description = "Consumes a valid refresh token and returns a replacement access and refresh token pair. A consumed token cannot be reused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replacement token pair", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid, expired, or already used (INVALID_REFRESH_TOKEN)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authenticationService.refresh(request);
    }

    /**
     * Revokes the supplied refresh token; repeating the request remains safe.
     *
     * @param request refresh token identifying the session
     * @return {@code 204 No Content}
     */
    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Log out", description = "Revokes the supplied refresh token. The operation is idempotent and returns the same empty response when the token was already revoked.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh token revoked or already absent"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Requests a one-time reset link. The same empty response is returned whether the account
     * exists or not, preventing email-address enumeration.
     *
     * @param request validated account email
     * @return {@code 204 No Content} for both known and unknown valid addresses
     */
    @PostMapping("/password/forgot")
    @SecurityRequirements
    @Operation(summary = "Request a password reset", description = "Requests a one-time reset email. A valid address always receives 204 so the endpoint cannot be used to discover registered accounts.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Reset request accepted; no account-existence information is disclosed"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationError"),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Consumes a one-time reset token and replaces the account password.
     *
     * @param request validated reset token and new password
     * @return {@code 204 No Content}
     */
    @PostMapping("/password/reset")
    @SecurityRequirements
    @Operation(summary = "Reset a password", description = "Consumes a one-time reset token, changes the password, and revokes existing refresh tokens for that user.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password reset completed"),
            @ApiResponse(responseCode = "400", description = "Invalid reset token or password validation failure (INVALID_PASSWORD_RESET_TOKEN or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Consumes an email-verification token and returns the updated user.
     *
     * @param request validated one-time verification token
     * @return user details including the verification timestamp
     */
    @PostMapping("/email-verification/confirm")
    @SecurityRequirements
    @Operation(summary = "Confirm an email address", description = "Consumes the one-time verification token and returns the updated user projection.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified user", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid verification token or request validation failure (INVALID_EMAIL_VERIFICATION_TOKEN or VALIDATION_ERROR)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public UserResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return emailVerificationService.verify(request);
    }

    /**
     * Requests a verification email for the authenticated user.
     *
     * <p>{@code @AuthenticationPrincipal} injects the UUID placed in the security context by the
     * JWT filter; the client cannot choose this identifier in the request body.</p>
     *
     * @param userId authenticated account identifier
     * @return {@code 204 No Content}
     */
    @PostMapping("/email-verification/request")
    @Operation(summary = "Request an email verification", description = "Sends a one-time verification email for the authenticated user. Requests are limited by a cooldown and rolling quota.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Verification email request accepted"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/AccountDisabled"),
            @ApiResponse(responseCode = "409", description = "Email has already been verified (EMAIL_ALREADY_VERIFIED)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Verification request limit reached (EMAIL_VERIFICATION_RATE_LIMITED); includes Retry-After header", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "500", ref = "#/components/responses/InternalServerError")
    })
    public ResponseEntity<Void> requestVerification(@AuthenticationPrincipal UUID userId) {
        emailVerificationService.requestVerification(userId);
        return ResponseEntity.noContent().build();
    }
}
