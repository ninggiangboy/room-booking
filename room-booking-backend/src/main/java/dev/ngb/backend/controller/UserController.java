package dev.ngb.backend.controller;

import dev.ngb.backend.dto.ChangePasswordRequest;
import dev.ngb.backend.dto.EmailExistsResponse;
import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.service.account.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes profile and account-management operations for users.
 *
 * <p>Spring registers the class through {@code @RestController}; {@code @RequestMapping} prefixes
 * every route, and Lombok generates the constructor Spring uses to inject the final service.</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService userAccountService;

    /**
     * Returns the account represented by the current access token.
     *
     * @param userId UUID injected from Spring Security's authenticated principal
     * @return safe public account details
     */
    @GetMapping("/me")
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
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        userAccountService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
