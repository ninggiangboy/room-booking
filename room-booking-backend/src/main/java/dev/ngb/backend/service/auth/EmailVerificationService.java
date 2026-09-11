package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.dto.UserResponse;
import dev.ngb.backend.dto.VerifyEmailRequest;
import dev.ngb.backend.event.EmailVerificationIssued;
import dev.ngb.backend.exception.EmailAlreadyVerifiedException;
import dev.ngb.backend.exception.EmailVerificationRateLimitException;
import dev.ngb.backend.exception.InvalidEmailVerificationTokenException;
import dev.ngb.backend.exception.UserAccountDisabledException;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.model.TokenConsumptionReason;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.repository.UserRepository;
import dev.ngb.backend.repository.UserRoleRepository;
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.util.HashUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages one-time email-verification tokens and marks verified accounts.
 *
 * <p>{@code @Service} registers the use case, and Lombok generates constructor injection for final
 * dependencies. {@code @Value} injects token lifetime and request-limit configuration into the
 * remaining mutable fields. Public entry points are transactional so token creation, consumption,
 * rate-limit checks, and user updates commit or roll back together.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthTokenFactory authTokenFactory;
    private final UserFinder userFinder;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${app.email-verification.token-ttl:24h}")
    private Duration tokenTtl;
    @Value("${app.email-verification.request-cooldown:60s}")
    private Duration requestCooldown;
    @Value("${app.email-verification.rate-limit-window:1h}")
    private Duration rateLimitWindow;
    @Value("${app.email-verification.rate-limit-max-requests:5}")
    private int rateLimitMaxRequests;

    /** Ensures invalid request-limit configuration fails during application startup. */
    @PostConstruct
    void validateConfiguration() {
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalStateException("email verification token TTL must be positive");
        }
        if (requestCooldown == null || requestCooldown.isNegative() || requestCooldown.isZero()) {
            throw new IllegalStateException("email verification request cooldown must be positive");
        }
        if (rateLimitWindow == null || rateLimitWindow.isNegative() || rateLimitWindow.isZero()) {
            throw new IllegalStateException("email verification rate-limit window must be positive");
        }
        if (rateLimitMaxRequests < 1) {
            throw new IllegalStateException("email verification rate-limit maximum must be positive");
        }
    }

    private void issue(User user) {
        if (user.getEmailVerifiedAt() != null) {
            throw new EmailAlreadyVerifiedException(user);
        }

        Instant now = clock.instant();
        // A newly issued token supersedes every older unconsumed verification token.
        authTokenRepository.findAllByUserIdAndTypeAndConsumedAtIsNull(
                        user.getId(), AuthTokenType.EMAIL_VERIFICATION)
                .forEach(token -> {
            token.consume(now, TokenConsumptionReason.ROTATED);
            authTokenRepository.save(token);
        });

        // Persist only a hash so a database leak cannot reveal a usable verification link.
        AuthTokenFactory.IssuedToken issued = authTokenFactory.create(
                user.getId(), AuthTokenType.EMAIL_VERIFICATION, now, tokenTtl);
        authTokenRepository.save(issued.token());
        eventPublisher.publishEvent(
                new EmailVerificationIssued(user.getEmail(), issued.rawToken()));
    }

    /**
     * Issues a verification token for an active, unverified account when request limits permit.
     *
     * <p>The user row is locked before inspecting token history so concurrent requests for the
     * same account cannot independently pass the cooldown or rolling-window checks.</p>
     *
     * @param userId authenticated account requesting another message
     * @throws UserNotFoundException when the account no longer exists
     * @throws UserAccountDisabledException when the account is not active
     * @throws EmailAlreadyVerifiedException when verification is already complete
     * @throws EmailVerificationRateLimitException when the cooldown or rolling quota is exceeded
     */
    @Transactional
    public void requestVerification(UUID userId) {
        User user = userFinder.findActiveByIdForUpdate(userId);
        enforceRequestLimits(userId);
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
        User user = userFinder.findActiveById(userId);

        if (user.getEmailVerifiedAt() == null) {
            Instant now = clock.instant();
            user.setEmailVerifiedAt(now);
            user = userRepository.save(user);
        }

        return UserResponse.from(user, userRoleRepository.findRolesByUserId(userId));
    }

    private UUID consume(String rawToken) {
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.EMAIL_VERIFICATION)
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        // Consuming before returning the user ID makes the token one-time within this transaction.
        token.consume(now, TokenConsumptionReason.USED);
        authTokenRepository.save(token);
        return token.getUserId();
    }

    private void enforceRequestLimits(UUID userId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(rateLimitWindow);
        List<AuthToken> recentTokens = authTokenRepository
                .findAllByUserIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        userId, AuthTokenType.EMAIL_VERIFICATION, windowStart);

        if (!recentTokens.isEmpty()) {
            Instant cooldownEndsAt = recentTokens.getLast().getCreatedAt().plus(requestCooldown);
            if (cooldownEndsAt.isAfter(now)) {
                throw rateLimitedUntil(now, cooldownEndsAt);
            }
        }

        if (recentTokens.size() >= rateLimitMaxRequests) {
            int firstRelevantIndex = recentTokens.size() - rateLimitMaxRequests;
            Instant quotaResetsAt = recentTokens.get(firstRelevantIndex)
                    .getCreatedAt()
                    .plus(rateLimitWindow);
            throw rateLimitedUntil(now, quotaResetsAt);
        }
    }

    private static EmailVerificationRateLimitException rateLimitedUntil(
            Instant now, Instant retryAt) {
        long remainingMillis = Duration.between(now, retryAt).toMillis();
        long retryAfterSeconds = Math.max(1, Math.ceilDiv(remainingMillis, 1_000));
        return new EmailVerificationRateLimitException(retryAt, retryAfterSeconds);
    }

}
