package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.AuthAttemptRateLimitException;
import dev.ngb.backend.identity.internal.model.session.AuthAttempt;
import dev.ngb.backend.identity.internal.model.session.AuthAttemptOutcome;
import dev.ngb.backend.identity.internal.model.session.AuthAttemptType;
import dev.ngb.backend.identity.internal.repository.session.AuthAttemptRepository;
import dev.ngb.backend.platform.util.DurationUtils;


/**
 * Records authentication attempts and enforces velocity limits over them.
 *
 * <p>{@code @Service} exposes this capability for dependency injection. The explicit constructor,
 * rather than Lombok, validates the configured window and retention while accepting {@code @Value}
 * property injection, matching {@link RefreshTokenService}'s constructor.</p>
 *
 * <p>Counting is deliberately per-account and per-identifier rather than per-source: a credential-
 * stuffing attack tries many accounts from one source, while a targeted account-takeover attempt
 * tries one account from many sources, and the two checks together bound both. Only {@code FAILURE}
 * outcomes count toward the limit, so a challenged attempt does not trip it.</p>
 *
 * <p>{@link #record} runs in its own {@code REQUIRES_NEW} transaction for the same reason {@code
 * RefreshTokenService#consume}'s reuse-detection revoke does: the caller records a failed attempt
 * and then throws {@code InvalidCredentialsException} in the same method, and {@code
 * DomainException} rolls back its caller's transaction by default. Without a genuinely separate
 * transaction, every failed-login attempt this class exists to count would vanish with the
 * rollback it triggers, leaving velocity control permanently blind to failures.</p>
 */
@Service
public class AuthAttemptService {

    private final AuthAttemptRepository authAttemptRepository;
    private final AuthAttemptFactory authAttemptFactory;
    private final long maxFailures;
    private final Duration window;
    private final Duration retention;

    /**
     * Creates a service with a validated rolling window and retention period.
     *
     * @param authAttemptRepository persistence gateway for attempt records
     * @param authAttemptFactory builder of attempt records
     * @param maxFailures configured positive count of failures allowed within {@code window}
     * @param window configured positive rolling window over which failures are counted
     * @param retention configured positive duration attempt rows are retained before pruning
     */
    public AuthAttemptService(
            AuthAttemptRepository authAttemptRepository,
            AuthAttemptFactory authAttemptFactory,
            @Value("${app.auth-attempts.max-failures:5}") long maxFailures,
            @Value("${app.auth-attempts.window:15m}") Duration window,
            @Value("${app.auth-attempts.retention:90d}") Duration retention) {
        this.authAttemptRepository = authAttemptRepository;
        this.authAttemptFactory = authAttemptFactory;
        if (maxFailures <= 0) {
            throw new IllegalArgumentException("auth attempts max failures must be positive");
        }
        this.maxFailures = maxFailures;
        this.window = DurationUtils.requirePositive(window, "auth attempts window");
        this.retention = DurationUtils.requirePositive(retention, "auth attempts retention");
    }

    /**
     * Rejects the attempt before it is evaluated when either the account or the identifier has
     * already reached the failure limit within the rolling window.
     *
     * @param accountHolderId account the attempt has resolved to, or {@code null} when not yet known
     * @param identifierDigest SHA-256 digest of the identifier tried, or {@code null} when the
     *     attempt already resolved to an account
     * @param attemptType kind of attempt being limited
     * @param now the command's decision instant
     * @throws AuthAttemptRateLimitException when either count is at or beyond the configured limit
     */
    public void checkVelocity(
            @Nullable UUID accountHolderId,
            @Nullable String identifierDigest,
            AuthAttemptType attemptType,
            Instant now) {
        Instant windowStart = now.minus(window);
        boolean accountLimited = accountHolderId != null
                && authAttemptRepository.countRecentFailuresForAccountHolder(
                        accountHolderId, attemptType.name(), windowStart) >= maxFailures;
        boolean identifierLimited = identifierDigest != null
                && authAttemptRepository.countRecentFailuresForIdentifier(
                        identifierDigest, attemptType.name(), windowStart) >= maxFailures;
        if (accountLimited || identifierLimited) {
            throw new AuthAttemptRateLimitException(now.plus(window), window.toSeconds());
        }
    }

    /**
     * Records the outcome of an authentication attempt.
     *
     * @param accountHolderId account the attempt resolved to, or {@code null} when it did not
     * @param identifierDigest SHA-256 digest of the identifier tried, or {@code null} when the
     *     attempt already resolved to an account
     * @param attemptType which authentication action was attempted
     * @param outcomeClass how the attempt ended
     * @param failureReasonClass stable classification of why it failed, or {@code null} on success
     * @param sourceHash SHA-256 digest of the network origin, or {@code null} when unknown
     * @param deviceHash SHA-256 digest of the device descriptor, or {@code null} when unknown
     * @param now the command's decision instant
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            @Nullable UUID accountHolderId,
            @Nullable String identifierDigest,
            AuthAttemptType attemptType,
            AuthAttemptOutcome outcomeClass,
            @Nullable String failureReasonClass,
            @Nullable String sourceHash,
            @Nullable String deviceHash,
            Instant now) {
        AuthAttempt attempt = authAttemptFactory.create(
                accountHolderId, identifierDigest, attemptType, outcomeClass, failureReasonClass,
                sourceHash, deviceHash, now, retention);
        authAttemptRepository.save(attempt);
    }
}
