package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.model.session.AuthSession;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import dev.ngb.backend.identity.internal.model.session.AuthenticationMethod;
import dev.ngb.backend.identity.internal.model.session.TokenConsumptionReason;
import dev.ngb.backend.identity.internal.repository.session.AuthSessionRepository;
import dev.ngb.backend.identity.internal.repository.session.AuthTokenRepository;
import dev.ngb.backend.identity.internal.service.auth.AuthTokenFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.ngb.backend.identity.internal.exception.InvalidRefreshTokenException;
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
 * just the one stolen secret. A token issued before sessions existed carries no session reference
 * and keeps the simpler one-shot behavior it always had.</p>
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
     * Opens a new session for the user and issues its first refresh token.
     *
     * @param user account the session belongs to
     * @param method how the principal proved who they were
     * @param assuranceLevel strength of that proof
     * @return raw refresh-token secret to return to the client
     */
    public String issue(User user, AuthenticationMethod method, AssuranceLevel assuranceLevel) {
        Instant now = clock.instant();

        AuthSession session = authSessionFactory.create(
                user.getId(), method, assuranceLevel, now, idleExpiration, sessionAbsoluteExpiration);
        session = authSessionRepository.save(session);

        // Only the SHA-256 hash is persisted; the raw secret is returned once to the client.
        AuthTokenFactory.IssuedToken issued = authTokenFactory.createForSession(
                user.getId(), session.getId(), 0, now, idleExpiration);
        AuthToken savedToken = authTokenRepository.save(issued.token());

        session.setCurrentTokenId(savedToken.getId());
        authSessionRepository.save(session);

        return issued.rawToken();
    }

    /**
     * Consumes a refresh token and rotates it into the next generation of its session.
     *
     * <p>A token that carries no session predates sessions and keeps the original one-shot
     * rotation. A token that carries a session is checked against that session's current
     * generation: a replayed already-consumed token, or an unconsumed token that is not the
     * session's current one, is treated as reuse and revokes the whole session lineage rather than
     * being honored as an ordinary refresh.</p>
     *
     * @param rawToken raw secret identifying the token to consume
     * @return the account the token belonged to and the raw secret of its successor
     * @throws InvalidRefreshTokenException when the token is unusable, or reuse is detected
     */
    public Rotated consume(String rawToken) {
        Instant now = clock.instant();
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.REFRESH_TOKEN)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.getSessionId() == null) {
            if (!token.isUsableAt(now)) {
                throw new InvalidRefreshTokenException();
            }
            token.consume(now, TokenConsumptionReason.ROTATED);
            authTokenRepository.save(token);
            return new Rotated(token.getUserId(), null);
        }

        AuthSession session = authSessionRepository.findByIdForUpdate(token.getSessionId())
                .orElseThrow(InvalidRefreshTokenException::new);

        boolean replayed = token.getConsumedAt() != null || !session.isLiveAt(now);
        boolean staleGeneration = session.getCurrentTokenId() != null
                && !session.getCurrentTokenId().equals(token.getId());
        if (replayed || staleGeneration) {
            // A consumed token presented again, or a token that isn't the session's current
            // generation, is the signature of a replayed secret: revoke the whole lineage.
            revokeSessionAndTokens(session, now, "REUSE_DETECTED");
            throw new InvalidRefreshTokenException();
        }
        if (!token.isUsableAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        AuthTokenFactory.IssuedToken issued = authTokenFactory.createForSession(
                token.getUserId(), session.getId(), session.getRotationGeneration() + 1, now,
                idleExpiration);
        AuthToken next = authTokenRepository.save(issued.token());

        token.consume(now, TokenConsumptionReason.ROTATED);
        token.setSupersededBy(next.getId());
        authTokenRepository.save(token);

        session.setRotationGeneration(session.getRotationGeneration() + 1);
        session.setCurrentTokenId(next.getId());
        session.setLastUsedAt(now);
        authSessionRepository.save(session);

        return new Rotated(token.getUserId(), issued.rawToken());
    }

    /**
     * Idempotently revokes a refresh token's session, making logout safe to retry.
     *
     * <p>Revoking the session, not just the presented token, is what makes "sign out everywhere"
     * from this device's session possible: a token predating sessions falls back to revoking just
     * itself.</p>
     *
     * @param rawToken raw secret whose stored hash identifies the token row
     */
    public void revoke(String rawToken) {
        authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.REFRESH_TOKEN)
                .ifPresent(token -> {
                    Instant now = clock.instant();
                    if (token.getSessionId() != null) {
                        authSessionRepository.findByIdForUpdate(token.getSessionId())
                                .filter(session -> session.getRevokedAt() == null)
                                .ifPresent(session -> {
                                    session.revoke(now, "LOGOUT", session.getUserId());
                                    authSessionRepository.save(session);
                                });
                    }
                    if (token.getConsumedAt() == null) {
                        token.consume(now, TokenConsumptionReason.LOGOUT);
                        authTokenRepository.save(token);
                    }
                });
    }

    /**
     * Revokes every live session for a user, for account-wide security actions such as a password
     * reset or account deletion.
     *
     * @param userId account whose sessions are revoked
     * @param now the command's decision instant
     * @param reason stable reason recorded on every revoked session and token
     */
    public void revokeAllSessionsForUser(UUID userId, Instant now, String reason) {
        authSessionRepository.findLiveForUser(userId, now)
                .forEach(session -> revokeSessionAndTokens(session, now, reason));
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
     * The account a rotated token belonged to, and the raw secret of the token that replaced it.
     *
     * @param userId account the token belonged to
     * @param rawRefreshToken raw secret of the successor token, or {@code null} for a token
     *     predating sessions, which is consumed without issuing a linked successor
     */
    public record Rotated(UUID userId, String rawRefreshToken) {
    }
}
