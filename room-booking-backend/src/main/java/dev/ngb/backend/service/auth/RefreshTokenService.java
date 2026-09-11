package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.ngb.backend.exception.InvalidRefreshTokenException;
import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.util.DurationUtils;
import dev.ngb.backend.util.HashUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues, consumes, and revokes opaque refresh tokens stored as hashes in the database.
 *
 * <p>{@code @Service} exposes this capability for dependency injection. The explicit constructor,
 * rather than Lombok, validates the configured duration while accepting {@code @Value} property
 * injection on one parameter.</p>
 */
@Service
public class RefreshTokenService {

    private final AuthTokenRepository authTokenRepository;
    private final AuthTokenFactory authTokenFactory;
    private final Clock clock;
    private final Duration tokenExpiration;

    /**
     * Creates a service with a validated refresh-token lifetime.
     *
     * @param authTokenRepository persistence gateway for token records
     * @param authTokenFactory builder of token records and their raw secrets
     * @param clock shared source of current UTC time
     * @param tokenExpiration configured positive token lifetime
     */
    public RefreshTokenService(
            AuthTokenRepository authTokenRepository,
            AuthTokenFactory authTokenFactory,
            Clock clock,
            @Value("${security.jwt.refresh-token-expiration:30d}") Duration tokenExpiration) {
        this.authTokenRepository = authTokenRepository;
        this.authTokenFactory = authTokenFactory;
        this.clock = clock;
        this.tokenExpiration = DurationUtils.requirePositive(
                tokenExpiration, "refresh token expiration");
    }

    String issue(User user) {
        Instant now = clock.instant();
        // Only the SHA-256 hash is persisted; the raw secret is returned once to the client.
        AuthTokenFactory.IssuedToken issued = authTokenFactory.create(
                user.getId(), AuthTokenType.REFRESH_TOKEN, now, tokenExpiration);
        authTokenRepository.save(issued.token());
        return issued.rawToken();
    }

    UUID consume(String rawToken) {
        // Marking the token consumed implements rotation and prevents replay.
        AuthToken token = findUsable(rawToken);
        token.setConsumedAt(clock.instant());
        authTokenRepository.save(token);
        return token.getUserId();
    }

    /**
     * Idempotently revokes a refresh token, making logout safe to retry.
     *
     * @param rawToken raw secret whose stored hash identifies the token row
     */
    public void revoke(String rawToken) {
        authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.REFRESH_TOKEN)
                .filter(token -> token.getConsumedAt() == null)
                .ifPresent(token -> {
                    token.setConsumedAt(clock.instant());
                    authTokenRepository.save(token);
                });
    }

    private AuthToken findUsable(String rawToken) {
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        HashUtils.sha256Hex(rawToken), AuthTokenType.REFRESH_TOKEN)
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (!token.isUsableAt(now)) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

}
