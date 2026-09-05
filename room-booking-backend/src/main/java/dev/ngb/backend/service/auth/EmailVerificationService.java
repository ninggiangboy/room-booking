package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import dev.ngb.backend.service.user.UserFinder;
import dev.ngb.backend.service.validation.UserAccountPolicy;
import dev.ngb.backend.util.HashUtils;
import dev.ngb.backend.util.SecureTokenUtils;
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

    private final AuthTokenRepository authTokenRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserFinder userFinder;
    private final UserAccountPolicy userAccountPolicy;
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

        String rawToken = SecureTokenUtils.generateUrlSafe();
        // Persist only a hash so a database leak cannot reveal a usable verification link.
        authTokenRepository.save(AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .type(AuthTokenType.EMAIL_VERIFICATION)
                .tokenHash(HashUtils.sha256Hex(rawToken))
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
        User user = userFinder.findById(userId);
        userAccountPolicy.requireActive(user);
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
        User user = userFinder.findById(userId);
        userAccountPolicy.requireActive(user);

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
                        HashUtils.sha256Hex(rawToken), AuthTokenType.EMAIL_VERIFICATION)
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        // Consuming before returning the user ID makes the token one-time within this transaction.
        token.setConsumedAt(now);
        authTokenRepository.save(token);
        return token.getUserId();
    }

}
