package dev.ngb.backend.identity.internal.service.auth.verification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.AccountHolderStatus;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;
import dev.ngb.backend.identity.internal.model.account.ContactChannelType;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.account.AccountHolderRepository;
import dev.ngb.backend.identity.internal.repository.account.ContactChannelRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.account.AccountHolderFinder;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import dev.ngb.backend.identity.internal.service.authz.CapabilityGrantService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.EmailVerificationIssued;
import dev.ngb.backend.identity.internal.exception.EmailAlreadyVerifiedException;
import dev.ngb.backend.identity.internal.exception.EmailVerificationRateLimitException;
import dev.ngb.backend.identity.internal.exception.InvalidEmailVerificationTokenException;
import dev.ngb.backend.identity.internal.exception.UserAccountDisabledException;
import dev.ngb.backend.identity.internal.exception.UserNotFoundException;
import dev.ngb.backend.identity.internal.web.UserResponse;
import dev.ngb.backend.identity.internal.web.VerifyEmailRequest;
import dev.ngb.backend.platform.util.HashUtils;


/**
 * Manages one-time email-verification tokens and marks verified accounts.
 *
 * <p>{@code @Service} registers the use case, and Lombok generates constructor injection for final
 * dependencies. {@code @Value} injects token lifetime and request-limit configuration into the
 * remaining mutable fields. Public entry points are transactional so token creation, consumption,
 * rate-limit checks, and account updates commit or roll back together.</p>
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String VERIFICATION_METHOD = "EMAIL_TOKEN";

    private final AuthTokenRepository authTokenRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final ContactChannelRepository contactChannelRepository;
    private final AuthTokenFactory authTokenFactory;
    private final AccountHolderFinder accountHolderFinder;
    private final CapabilityGrantService capabilityGrantService;
    private final AuthorizationService authorizationService;
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
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalStateException("email verification token TTL must be positive");
        }
        if (requestCooldown.isNegative() || requestCooldown.isZero()) {
            throw new IllegalStateException("email verification request cooldown must be positive");
        }
        if (rateLimitWindow.isNegative() || rateLimitWindow.isZero()) {
            throw new IllegalStateException("email verification rate-limit window must be positive");
        }
        if (rateLimitMaxRequests < 1) {
            throw new IllegalStateException("email verification rate-limit maximum must be positive");
        }
    }

    private void issue(AccountHolder holder, ContactChannel emailChannel) {
        if (emailChannel.isVerified()) {
            throw new EmailAlreadyVerifiedException(holder, emailChannel);
        }

        Instant now = clock.instant();
        // A newly issued token supersedes every older unconsumed verification token.
        authTokenRepository.findAllByAccountHolderIdAndTypeAndConsumedAtIsNull(
                        holder.getId(), AuthTokenType.EMAIL_VERIFICATION)
                .forEach(token -> {
            token.consume(now, TokenConsumptionReason.ROTATED);
            authTokenRepository.save(token);
        });

        // Persist only a hash so a database leak cannot reveal a usable verification link.
        AuthTokenFactory.IssuedToken issued = authTokenFactory.create(
                holder.getId(), AuthTokenType.EMAIL_VERIFICATION, now, tokenTtl);
        authTokenRepository.save(issued.token());
        eventPublisher.publishEvent(
                new EmailVerificationIssued(emailChannel.getNormalizedValue(), issued.rawToken()));
    }

    /**
     * Issues a verification token for an active, unverified account when request limits permit.
     *
     * <p>The holder row is locked before inspecting token history so concurrent requests for the
     * same account cannot independently pass the cooldown or rolling-window checks.</p>
     *
     * @param holderId authenticated account requesting another message
     * @throws UserNotFoundException when the account no longer exists
     * @throws UserAccountDisabledException when the account is not active
     * @throws EmailAlreadyVerifiedException when verification is already complete
     * @throws EmailVerificationRateLimitException when the cooldown or rolling quota is exceeded
     */
    @Transactional
    public void requestVerification(UUID holderId) {
        AccountHolder holder = accountHolderFinder.findActiveByIdForUpdate(holderId);
        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(holderId, ContactChannelType.EMAIL.name())
                .orElseThrow(() -> new IllegalStateException(
                        "registration must create a primary email channel"));
        enforceRequestLimits(holderId);
        issue(holder, emailChannel);
    }

    /**
     * Consumes a valid token and returns the account's updated public details.
     *
     * @param request validated DTO containing the raw one-time token
     * @return updated account projection with its email verification timestamp
     * @throws InvalidEmailVerificationTokenException when the token is unknown, expired, or consumed
     */
    @Transactional
    public UserResponse verify(VerifyEmailRequest request) {
        UUID holderId = consume(request.token());
        AccountHolder holder = accountHolderFinder.findActiveById(holderId);

        ContactChannel emailChannel = contactChannelRepository
                .findCurrentPrimary(holderId, ContactChannelType.EMAIL.name())
                .orElseThrow(() -> new IllegalStateException(
                        "registration must create a primary email channel"));
        if (!emailChannel.isVerified()) {
            emailChannel.markVerified(clock.instant(), VERIFICATION_METHOD);
            emailChannel = contactChannelRepository.save(emailChannel);
        }
        if (holder.getStatus() == AccountHolderStatus.PENDING_VERIFICATION) {
            // The primary channel that gated PENDING_VERIFICATION is now proven; the holder never
            // moves back to it, and no other verification completes it, so this transition happens
            // exactly once per holder.
            holder.setStatus(AccountHolderStatus.ACTIVE);
            holder = accountHolderRepository.save(holder);
        }

        Instant now = clock.instant();
        List<String> roleNames = capabilityGrantService.effectiveRoleNames(holderId, now);
        Set<String> capabilities = authorizationService.effectiveGlobalCapabilities(holderId, now)
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());
        return UserResponse.from(holder, emailChannel, null, roleNames, capabilities);
    }

    private UUID consume(String rawToken) {
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.EMAIL_VERIFICATION)
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            throw new InvalidEmailVerificationTokenException();
        }

        // Consuming before returning the holder ID makes the token one-time within this transaction.
        token.consume(now, TokenConsumptionReason.USED);
        authTokenRepository.save(token);
        return token.getAccountHolderId();
    }

    private void enforceRequestLimits(UUID holderId) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(rateLimitWindow);
        List<AuthToken> recentTokens = authTokenRepository
                .findAllByAccountHolderIdAndTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        holderId, AuthTokenType.EMAIL_VERIFICATION, windowStart);

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
