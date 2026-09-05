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

import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import dev.ngb.backend.model.User;
import dev.ngb.backend.repository.AuthTokenRepository;
import dev.ngb.backend.exception.InvalidRefreshTokenException;
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthTokenRepository authTokenRepository;
    private final Clock clock;
    private final Duration tokenExpiration;

    /**
     * Creates a service with a validated refresh-token lifetime.
     *
     * @param authTokenRepository persistence gateway for token records
     * @param clock shared source of current UTC time
     * @param tokenExpiration configured positive token lifetime
     */
    public RefreshTokenService(
            AuthTokenRepository authTokenRepository,
            Clock clock,
            @Value("${security.jwt.refresh-token-expiration:30d}") Duration tokenExpiration) {
        this.authTokenRepository = authTokenRepository;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenExpiration = requirePositive(tokenExpiration);
    }

    String issue(User user) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(user.getId(), "user id must not be null");

        String rawToken = generateToken();
        Instant now = clock.instant();
        // Only the SHA-256 hash is persisted; the raw secret is returned once to the client.
        authTokenRepository.save(AuthToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .type(AuthTokenType.REFRESH_TOKEN)
                .tokenHash(hash(rawToken))
                .expiresAt(now.plus(tokenExpiration))
                .createdAt(now)
                .build());
        return rawToken;
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
        authTokenRepository.findByTokenHashAndType(hash(rawToken), AuthTokenType.REFRESH_TOKEN)
                .filter(token -> token.getConsumedAt() == null)
                .ifPresent(token -> {
                    token.setConsumedAt(clock.instant());
                    authTokenRepository.save(token);
                });
    }

    private AuthToken findUsable(String rawToken) {
        AuthToken token = authTokenRepository.findByTokenHashAndType(
                        hash(rawToken), AuthTokenType.REFRESH_TOKEN)
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        return token;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private static Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(
                    "refresh token expiration must be positive");
        }
        return duration;
    }
}
