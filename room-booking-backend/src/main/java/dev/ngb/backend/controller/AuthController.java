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
    public ResponseEntity<Void> requestVerification(@AuthenticationPrincipal UUID userId) {
        emailVerificationService.requestVerification(userId);
        return ResponseEntity.noContent().build();
    }
}
