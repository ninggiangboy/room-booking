package dev.ngb.backend.identity.internal.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import dev.ngb.backend.identity.internal.model.session.AuthToken;
import dev.ngb.backend.identity.internal.model.session.AuthTokenType;
import org.springframework.stereotype.Component;

import dev.ngb.backend.platform.util.HashUtils;
import dev.ngb.backend.platform.util.SecureTokenUtils;


/**
 * Constructs one-time authentication tokens together with the secret handed to the client.
 *
 * <p>{@code @Component} makes the factory injectable. Centralizing construction here guarantees
 * every token kind is created the same way: a fresh random secret, only its digest persisted, and
 * an expiry derived from the caller's own decision instant.</p>
 *
 * <p>{@code public} rather than package-private, unlike {@code UserRegistrationFactory}: this
 * factory is shared by workflows split across three sibling packages ({@code auth.session},
 * {@code auth.verification}, {@code auth.passwordreset}), each of which needs to build a token row
 * without duplicating the digest/expiry construction logic. {@link IssuedToken} still exposes its
 * raw secret only to the immediate caller, which remains responsible for returning it to the
 * client exactly once and never persisting it.</p>
 */
@Component
public class AuthTokenFactory {

    /**
     * Builds an unsaved token row and returns its raw secret exactly once.
     *
     * <p>The issuing instant is supplied rather than read from a clock so a workflow that also
     * supersedes older tokens stamps every row with one decision instant instead of several
     * separate clock reads.</p>
     *
     * @param userId account the token belongs to
     * @param type purpose that prevents one token kind from being used as another
     * @param issuedAt workflow's single decision instant
     * @param ttl positive lifetime added to {@code issuedAt} to obtain the expiry
     * @return token row to persist and the raw secret to deliver to the client
     */
    public IssuedToken create(UUID userId, AuthTokenType type, Instant issuedAt, Duration ttl) {
        String rawToken = SecureTokenUtils.generateUrlSafe();
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(type)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(issuedAt.plus(ttl))
                .build();
        return new IssuedToken(token, rawToken);
    }

    /**
     * Builds an unsaved refresh token linked to its owning session and rotation generation.
     *
     * @param userId account the token belongs to
     * @param sessionId session this token was issued under
     * @param rotationGeneration position in the session's rotation chain
     * @param issuedAt workflow's single decision instant
     * @param ttl positive lifetime added to {@code issuedAt} to obtain the expiry
     * @return token row to persist and the raw secret to deliver to the client
     */
    public IssuedToken createForSession(
            UUID userId, UUID sessionId, int rotationGeneration, Instant issuedAt, Duration ttl) {
        String rawToken = SecureTokenUtils.generateUrlSafe();
        AuthToken token = AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(AuthTokenType.REFRESH_TOKEN)
                .tokenHash(HashUtils.sha256Hex(rawToken))
                .expiresAt(issuedAt.plus(ttl))
                .sessionId(sessionId)
                .rotationGeneration(rotationGeneration)
                .build();
        return new IssuedToken(token, rawToken);
    }

    /**
     * Token row and the secret that is never recoverable from it afterwards.
     *
     * @param token unsaved token record holding only the secret's digest
     * @param rawToken secret to return to the client once and never store
     */
    public record IssuedToken(AuthToken token, String rawToken) {
    }
}
