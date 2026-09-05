package dev.ngb.backend.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.dto.VerifyEmailRequest;
import dev.ngb.backend.event.EmailVerificationIssued;
import dev.ngb.backend.exception.EmailAlreadyVerifiedException;
import dev.ngb.backend.exception.InvalidEmailVerificationTokenException;
import dev.ngb.backend.exception.UserAccountDisabledException;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages one-time email-verification tokens and marks verified accounts.
 *
 * <p>{@code @Service} registers the use case, and Lombok generates constructor injection for final
 * dependencies. {@code @Value} injects token lifetime configuration into the remaining mutable
 * field. Public entry points are transactional so token consumption and user updates commit or
 * roll back together.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.email-verification.token-ttl:24h}")
    private Duration tokenTtl;

    void issue(User user) {
        if (user.getEmailVerifiedAt() != null) {
            throw new EmailAlreadyVerifiedException(user);
        }

        Instant now = clock.instant();
        // A newly issued token supersedes every older unconsumed verification token.
        authTokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(
                        user.getId(), AuthTokenType.EMAIL_VERIFICATION)
                .forEach(token -> {
            token.setConsumedAt(now);
            authTokenRepository.save(token);
        });

        String rawToken = generateToken();
        // Persist only a hash so a database leak cannot reveal a usable verification link.
        authTokenRepository.save(AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .type(AuthTokenType.EMAIL_VERIFICATION)
                .tokenHash(hash(rawToken))
                .expiresAt(now.plus(tokenTtl))
                .createdAt(now)
                .build());
        eventPublisher.publishEvent(new EmailVerificationIssued(user.getEmail(), rawToken));
    }

    /**
     * Replaces the current verification token for an active, unverified account.
     *
     * @param userId authenticated account requesting another message
     * @throws UserNotFoundException when the account no longer exists
     * @throws UserAccountDisabledException when the account is not active
     * @throws EmailAlreadyVerifiedException when verification is already complete
     */
    @Transactional
    public void requestVerification(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        User user = findUser(userId);
        ensureActive(user);
        issue(user);
    }

    /**
     * Consumes a valid token and returns the user's updated public details.
     *
     * @param request validated DTO containing the raw one-time token
     * @return updated account projection with its email verification timestamp
     * @throws InvalidEmailVerificationTokenException when the token is unknown, expired, or consumed
     */
    @Transactional
    public UserResponse verify(VerifyEmailRequest request) {
        UUID userId = consume(request.token());
        User user = findUser(userId);
        ensureActive(user);

        if (user.getEmailVerifiedAt() == null) {
            Instant now = clock.instant();
            user.setEmailVerifiedAt(now);
            user.setUpdatedAt(now);
            user = userRepository.save(user);
        }

        return UserResponse.from(user, userRoleRepository.findRolesByUserId(userId));
    }

    private UUID consume(String rawToken) {
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        hash(rawToken), AuthTokenType.EMAIL_VERIFICATION)
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        Instant now = clock.instant();
        if (token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        // Consuming before returning the user ID makes the token one-time within this transaction.
        token.setConsumedAt(now);
        authTokenRepository.save(token);
        return token.getUserId();
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private static void ensureActive(User user) {
        if (!user.isActive()) {
            throw new UserAccountDisabledException(user);
        }
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
