package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.ngb.backend.dto.ForgotPasswordRequest;
import dev.ngb.backend.dto.ResetPasswordRequest;
import dev.ngb.backend.event.PasswordResetIssued;
import dev.ngb.backend.exception.InvalidPasswordResetTokenException;
import dev.ngb.backend.exception.base.ValidationException;
import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.service.validation.PasswordPolicy;
import dev.ngb.backend.util.DurationUtils;
import dev.ngb.backend.util.HashUtils;
import dev.ngb.backend.util.SecureTokenUtils;
import dev.ngb.backend.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes one-time password-reset tokens without exposing registered emails.
 *
 * <p>{@code @Service} registers the workflow. An explicit constructor validates the
 * {@code @Value}-injected TTL and makes every dependency mandatory. Public methods use
 * {@code @Transactional} so token/user changes either commit together or roll back together.</p>
 */
@Service
public class PasswordResetService {

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final UserFinder userFinder;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration tokenTtl;

    /**
     * Creates a password-reset workflow with a validated token lifetime.
     *
     * @param authTokenRepository persistence gateway for reset and refresh tokens
     * @param userRepository persistence gateway for accounts
     * @param userFinder shared user lookup and account-status gateway
     * @param passwordEncoder verifies and hashes passwords
     * @param passwordPolicy enforces password strength and BCrypt limits
     * @param eventPublisher publishes a raw token for post-commit email delivery
     * @param clock shared testable source of current time
     * @param tokenTtl configured positive password-reset-token lifetime
     */
    public PasswordResetService(
            AuthTokenRepository authTokenRepository,
            UserRepository userRepository,
            UserFinder userFinder,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            @Value("${app.password-reset.token-ttl:30m}") Duration tokenTtl) {
        this.authTokenRepository = authTokenRepository;
        this.userRepository = userRepository;
        this.userFinder = userFinder;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.tokenTtl = DurationUtils.requirePositive(tokenTtl, "password reset token TTL");
    }

    /**
     * Sends a reset link when an active account exists. The method deliberately succeeds for all
     * syntactically valid emails so callers cannot enumerate accounts from its response.
     *
     * <p>Issuing a new token consumes any older unconsumed password-reset tokens for the account.</p>
     *
     * @param request validated email request; email is normalized before lookup
     */
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        String normalizedEmail = StringUtils.normalizeLowerCase(request.email());
        userFinder.findActiveByEmailIfPresent(normalizedEmail).ifPresent(this::issue);
    }

    /**
     * Replaces the password, confirms control of the email address, consumes the reset token, and
     * revokes every active refresh token.
     *
     * <p>Revoking refresh tokens signs the user out of existing renewable sessions after a
     * credential recovery. Existing short-lived JWT access tokens naturally expire.</p>
     *
     * @param request one-time reset token and proposed new raw password
     * @throws InvalidPasswordResetTokenException when token or owning account is unusable
     * @throws ValidationException when the password violates policy or repeats the current one
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        passwordPolicy.validate("newPassword", request.newPassword());

        Instant now = clock.instant();
        AuthToken resetToken = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(request.token()), AuthTokenType.PASSWORD_RESET)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (!resetToken.isUsableAt(now)) {
            throw new InvalidPasswordResetTokenException();
        }

        User user = userRepository.findById(resetToken.getUserId())
                .filter(User::isActive)
                .orElseThrow(InvalidPasswordResetTokenException::new);
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ValidationException(
                    "newPassword", "new password must be different from current password");
        }

        resetToken.setConsumedAt(now);
        authTokenRepository.save(resetToken);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
        userRepository.save(user);

        authTokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(
                        user.getId(), AuthTokenType.REFRESH_TOKEN)
                .forEach(token -> {
                    token.setConsumedAt(now);
                    authTokenRepository.save(token);
                });
    }

    private void issue(User user) {
        Instant now = clock.instant();
        authTokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(
                        user.getId(), AuthTokenType.PASSWORD_RESET)
                .forEach(token -> {
                    token.setConsumedAt(now);
                    authTokenRepository.save(token);
                });

        String rawToken = SecureTokenUtils.generateUrlSafe();
        authTokenRepository.save(AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .type(AuthTokenType.PASSWORD_RESET)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(now.plus(tokenTtl))
                .build());
        eventPublisher.publishEvent(new PasswordResetIssued(user.getEmail(), rawToken));
    }

}
