package dev.ngb.backend.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.util.HashUtils;
import dev.ngb.backend.util.SecureTokenUtils;
import org.springframework.stereotype.Component;

/**
 * Constructs one-time authentication tokens together with the secret handed to the client.
 *
 * <p>{@code @Component} makes the factory injectable. Package-private visibility keeps raw token
 * secrets inside the authentication package. Centralizing construction here guarantees every token
 * kind is created the same way: a fresh random secret, only its digest persisted, and an expiry
 * derived from the caller's own decision instant.</p>
 */
@Component
class AuthTokenFactory {

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
    IssuedToken create(UUID userId, AuthTokenType type, Instant issuedAt, Duration ttl) {
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
     * Token row and the secret that is never recoverable from it afterwards.
     *
     * @param token unsaved token record holding only the secret's digest
     * @param rawToken secret to return to the client once and never store
     */
    record IssuedToken(AuthToken token, String rawToken) {
    }
}
