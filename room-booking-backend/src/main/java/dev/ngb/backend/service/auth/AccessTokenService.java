package dev.ngb.backend.service.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import dev.ngb.backend.util.DurationUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates and validates short-lived, signed JWT access tokens.
 *
 * <p>{@code @Service} makes this stateless capability injectable. Unlike opaque refresh tokens,
 * JWT access tokens are not stored: integrity comes from the HMAC signature, and validity comes
 * from signature and expiration checks performed by the configured parser.</p>
 */
@Service
public class AccessTokenService {

    private final SecretKey signingKey;
    private final Duration accessTokenExpiration;
    private final Clock clock;
    private final JwtParser jwtParser;

    /**
     * Builds a token service from external configuration and the shared application clock.
     *
     * <p>{@code @Value} injects values from Spring properties. Constructor validation deliberately
     * fails application startup when cryptographic configuration is unsafe.</p>
     *
     * @param base64Secret Base64-encoded HMAC secret containing at least 256 decoded bits
     * @param accessTokenExpiration positive lifetime of an access token
     * @param clock shared clock used for issuance and parser expiration checks
     */
    public AccessTokenService(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.access-token-expiration:15m}") Duration accessTokenExpiration,
            Clock clock) {
        this.signingKey = createSigningKey(base64Secret);
        this.accessTokenExpiration = DurationUtils.requirePositive(
                accessTokenExpiration, "access token expiration");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(this.clock.instant()))
                .build();
    }

    /**
     * Generates an access token without additional application claims.
     *
     * @param userId value written to the standard JWT subject claim
     * @return compact signed JWT string
     */
    public String generateAccessToken(UUID userId) {
        return generateAccessToken(userId, Map.of());
    }

    /**
     * Generates a token whose subject is the user ID and whose payload includes extra claims.
     *
     * @param userId value written to the standard JWT subject claim
     * @param additionalClaims application claims such as email and roles
     * @return compact signed JWT string
     */
    public String generateAccessToken(UUID userId, Map<String, ?> additionalClaims) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(additionalClaims, "additionalClaims must not be null");

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenExpiration);

        return Jwts.builder()
                .claims(additionalClaims)
                .subject(userId.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies a token and returns the UUID stored in its subject claim.
     *
     * @param token compact signed JWT
     * @return authenticated user identifier
     * @throws io.jsonwebtoken.JwtException when signature, structure, or expiry is invalid
     * @throws IllegalArgumentException when the token or subject is not usable
     */
    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    /**
     * Verifies a token and resolves one value from its claims.
     *
     * @param token compact signed JWT
     * @param claimsResolver function selecting or transforming a claim
     * @param <T> selected value type
     * @return value produced by the resolver
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Objects.requireNonNull(claimsResolver, "claimsResolver must not be null");
        return claimsResolver.apply(extractClaims(token));
    }

    /**
     * Parses and cryptographically verifies all claims, including token expiration.
     *
     * @param token compact signed JWT
     * @return verified claims payload
     */
    public Claims extractClaims(String token) {
        requireToken(token);
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    /**
     * Returns a boolean instead of exposing parsing exceptions for an unusable token.
     *
     * @param token candidate compact JWT
     * @return {@code true} when parsing, signature, and expiration checks succeed
     */
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Checks both token validity and whether its subject matches the expected user.
     *
     * @param token candidate compact JWT
     * @param expectedUserId required JWT subject
     * @return {@code true} only when the token is valid and belongs to that user
     */
    public boolean isTokenValid(String token, UUID expectedUserId) {
        if (expectedUserId == null) {
            return false;
        }

        try {
            return expectedUserId.equals(extractUserId(token));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static SecretKey createSigningKey(String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException("security.jwt.secret must not be blank");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "security.jwt.secret must be valid Base64",
                    exception);
        }

        // HMAC-SHA signing needs at least 256 bits to provide an appropriate security margin.
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "security.jwt.secret must contain at least 256 bits after Base64 decoding");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }
}
