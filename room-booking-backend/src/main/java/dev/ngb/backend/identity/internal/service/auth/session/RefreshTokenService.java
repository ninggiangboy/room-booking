package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.session.AuthSession;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.session.AuthSessionRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.ngb.backend.identity.internal.exception.InvalidRefreshTokenException;
import dev.ngb.backend.identity.internal.exception.SessionNotFoundException;
import dev.ngb.backend.platform.AssuranceLevel;
import dev.ngb.backend.platform.util.DurationUtils;
import dev.ngb.backend.platform.util.HashUtils;


/**
 * Issues, rotates, and revokes refresh tokens backed by a durable {@link AuthSession}.
 *
 * <p>{@code @Service} exposes this capability for dependency injection. The explicit constructor,
 * rather than Lombok, validates the configured durations while accepting {@code @Value} property
 * injection on two of them.</p>
 *
 * <p>Every refresh token issued here belongs to a session, which is what makes reuse detection
 * actionable: a replayed token identifies the session whose entire lineage must be revoked, not
 * just the one stolen secret. Migration {@code 037} added a database constraint requiring every
 * refresh token to carry a session, which is what let the session-less fallback path this class
 * used to need be deleted rather than merely left unreachable.</p>
 *
 * <p>{@link #consume} is its own {@code REQUIRES_NEW} transaction, with rollback suppressed for
 * {@link InvalidRefreshTokenException}. Both are necessary together: {@code
 * AuthenticationService.refresh} already runs inside its own {@code @Transactional} method, and
 * {@code DomainException} -- the base every stable exception in this codebase extends -- is a
 * {@code RuntimeException} specifically so a caller's transaction rolls back on it by default.
 * Joining that outer transaction (the default propagation) would let the caller's rollback undo
 * the very revocation the reuse-detection throw is supposed to guarantee, silently leaving every
 * other token in the session's lineage usable. {@code REQUIRES_NEW} alone is not enough either:
 * without suppressing rollback for this one exception, {@code consume}'s own transaction would
 * roll back itself. Forcing a genuinely separate transaction also sidesteps a deadlock a nested,
 * same-connection re-lock of the session row would otherwise risk against the row lock {@link
 * AuthSessionRepository#findByIdForUpdate} takes below.</p>
 */
@Service
public class RefreshTokenService {

    private final AuthTokenRepository authTokenRepository;
    private final AuthSessionRepository authSessionRepository;
    private final AuthTokenFactory authTokenFactory;
    private final AuthSessionFactory authSessionFactory;
    private final Clock clock;
    private final Duration idleExpiration;
    private final Duration sessionAbsoluteExpiration;

    /**
     * Creates a service with validated session and token lifetimes.
     *
     * @param authTokenRepository persistence gateway for token records
     * @param authSessionRepository persistence gateway for session records
     * @param authTokenFactory builder of token records and their raw secrets
     * @param authSessionFactory builder of session records
     * @param clock shared source of current UTC time
     * @param idleExpiration configured positive refresh-token / session idle lifetime
     * @param sessionAbsoluteExpiration configured positive session absolute lifetime
     */
    public RefreshTokenService(
            AuthTokenRepository authTokenRepository,
            AuthSessionRepository authSessionRepository,
            AuthTokenFactory authTokenFactory,
            AuthSessionFactory authSessionFactory,
            Clock clock,
            @Value("${security.jwt.refresh-token-expiration:30d}") Duration idleExpiration,
            @Value("${security.jwt.session-absolute-expiration:180d}")
                    Duration sessionAbsoluteExpiration) {
        this.authTokenRepository = authTokenRepository;
        this.authSessionRepository = authSessionRepository;
        this.authTokenFactory = authTokenFactory;
        this.authSessionFactory = authSessionFactory;
        this.clock = clock;
        this.idleExpiration = DurationUtils.requirePositive(
                idleExpiration, "refresh token expiration");
        this.sessionAbsoluteExpiration = DurationUtils.requirePositive(
                sessionAbsoluteExpiration, "session absolute expiration");
    }

    /**
     * Opens a new session for the account holder and issues its first refresh token.
     *
     * @param accountHolderId account the session belongs to
     * @param method how the principal proved who they were
     * @param assuranceLevel strength of that proof
     * @param clientDescriptor client description shown to the owner when listing their devices, or
     *     {@code null} when unavailable
     * @param originHash SHA-256 digest of the network origin, or {@code null} when unavailable
     * @return the new session's identifier and its first raw refresh-token secret
     */
    public Issued issue(
            UUID accountHolderId,
            AuthenticationMethod method,
            AssuranceLevel assuranceLevel,
            @Nullable String clientDescriptor,
            @Nullable String originHash) {
        Instant now = clock.instant();

        AuthSession session = authSessionFactory.create(
                accountHolderId, method, assuranceLevel, now, idleExpiration, sessionAbsoluteExpiration,
                clientDescriptor, originHash);
        session = authSessionRepository.save(session);

        // Only the SHA-256 hash is persisted; the raw secret is returned once to the client.
        AuthTokenFactory.IssuedToken issued = authTokenFactory.createForSession(
                accountHolderId, session.getId(), 0, now, idleExpiration);
        AuthToken savedToken = authTokenRepository.save(issued.token());

        session.setCurrentTokenId(savedToken.getId());
        authSessionRepository.save(session);

        return new Issued(session.getId(), issued.rawToken());
    }

    /**
     * Consumes a refresh token and rotates it into the next generation of its session.
     *
     * <p>A consumed token that is replayed, or an unconsumed token that is not its session's
     * current generation, is treated as reuse and revokes the whole session lineage rather than
     * being honored as an ordinary refresh.</p>
     *
     * @param rawToken raw secret identifying the token to consume
     * @return the account, session, and raw secret of the successor token
     * @throws InvalidRefreshTokenException when the token is unusable, or reuse is detected
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = InvalidRefreshTokenException.class)
    public Rotated consume(String rawToken) {
        Instant now = clock.instant();
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.REFRESH_TOKEN)
                .orElseThrow(InvalidRefreshTokenException::new);

        AuthSession session = authSessionRepository.findByIdForUpdate(token.getSessionId())
                .orElseThrow(InvalidRefreshTokenException::new);

        boolean replayed = token.getConsumedAt() != null || !session.isLiveAt(now);
        boolean staleGeneration = session.getCurrentTokenId() != null
                && !session.getCurrentTokenId().equals(token.getId());
        if (replayed || staleGeneration) {
            // A consumed token presented again, or a token that isn't the session's current
            // generation, is the signature of a replayed secret: revoke the whole lineage. This
            // method's own REQUIRES_NEW/noRollbackFor pairing (see the class Javadoc) is what lets
            // this revocation actually commit despite the exception thrown right after it.
            revokeSessionAndTokens(session, now, "REUSE_DETECTED");
            throw new InvalidRefreshTokenException();
        }
        if (!token.isUsableAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        AuthTokenFactory.IssuedToken issued = authTokenFactory.createForSession(
                token.getAccountHolderId(), session.getId(), session.getRotationGeneration() + 1, now,
                idleExpiration);
        AuthToken next = authTokenRepository.save(issued.token());

        token.consume(now, TokenConsumptionReason.ROTATED);
        token.setSupersededBy(next.getId());
        authTokenRepository.save(token);

        session.setRotationGeneration(session.getRotationGeneration() + 1);
        session.setCurrentTokenId(next.getId());
        session.setLastUsedAt(now);
        // Idle expiry slides forward on every successful rotation: idleExpiresAt ends a session
        // that has gone quiet, and a session used every day for a year is not quiet. Without this,
        // both expiries were effectively absolute and an actively used session died after one
        // idle-expiration window regardless of how often it was used.
        session.setIdleExpiresAt(now.plus(idleExpiration));
        authSessionRepository.save(session);

        return new Rotated(token.getAccountHolderId(), session.getId(), issued.rawToken());
    }

    /**
     * Idempotently revokes a refresh token's session, making logout safe to retry.
     *
     * @param rawToken raw secret whose stored hash identifies the token row
     */
    public void revoke(String rawToken) {
        authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.REFRESH_TOKEN)
                .ifPresent(token -> {
                    Instant now = clock.instant();
                    authSessionRepository.findByIdForUpdate(token.getSessionId())
                            .filter(session -> session.getRevokedAt() == null)
                            .ifPresent(session -> {
                                session.revoke(now, "LOGOUT", session.getAccountHolderId());
                                authSessionRepository.save(session);
                            });
                    if (token.getConsumedAt() == null) {
                        token.consume(now, TokenConsumptionReason.LOGOUT);
                        authTokenRepository.save(token);
                    }
                });
    }

    /**
     * Revokes every live session for an account holder, for account-wide security actions such as
     * a password reset or account closure.
     *
     * @param accountHolderId account whose sessions are revoked
     * @param now the command's decision instant
     * @param reason stable reason recorded on every revoked session and token
     */
    public void revokeAllSessionsForHolder(UUID accountHolderId, Instant now, String reason) {
        authSessionRepository.findLiveForHolder(accountHolderId, now)
                .forEach(session -> revokeSessionAndTokens(session, now, reason));
    }

    /**
     * Revokes one session belonging to an account holder, for self-service device management.
     *
     * @param accountHolderId owner the session must belong to
     * @param sessionId session to revoke
     * @param now the command's decision instant
     * @param reason stable reason recorded on the revoked session and its tokens
     * @throws SessionNotFoundException when no session with that id belongs to the account holder,
     *     deliberately identical to an absent session so a caller cannot probe another holder's
     *     session ids
     */
    public void revokeSession(UUID accountHolderId, UUID sessionId, Instant now, String reason) {
        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId)
                .filter(candidate -> candidate.getAccountHolderId().equals(accountHolderId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.isLiveAt(now)) {
            revokeSessionAndTokens(session, now, reason);
        }
    }

    private void revokeSessionAndTokens(AuthSession session, Instant now, String reason) {
        session.revoke(now, reason, null);
        authSessionRepository.save(session);
        authTokenRepository.findAllBySessionIdAndConsumedAtIsNull(session.getId())
                .forEach(token -> {
                    token.consume(now, TokenConsumptionReason.REUSE_DETECTED);
                    authTokenRepository.save(token);
                });
    }

    /**
     * The identifier and first raw secret of a newly opened session.
     *
     * @param sessionId identifier of the new session
     * @param rawRefreshToken raw secret of its first refresh token
     */
    public record Issued(UUID sessionId, String rawRefreshToken) {
    }

    /**
     * The account and session a rotated token belonged to, and the raw secret of the token that
     * replaced it.
     *
     * @param accountHolderId account the token belonged to
     * @param sessionId session the token was rotated under
     * @param rawRefreshToken raw secret of the successor token
     */
    public record Rotated(UUID accountHolderId, UUID sessionId, String rawRefreshToken) {
    }
}
