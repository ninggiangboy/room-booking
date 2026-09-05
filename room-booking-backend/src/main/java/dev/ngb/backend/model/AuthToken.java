package dev.ngb.backend.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Persistent one-time token used for email verification or refresh-token sessions.
 *
 * <p>The Lombok annotations generate mutable entity boilerplate and a builder. Spring Data JDBC
 * maps the class to {@code auth_tokens}; {@code @Id} identifies rows and {@code @Version} prevents
 * two concurrent consumers from silently updating the same version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("auth_tokens")
public class AuthToken {

    /** Primary key of the token record, not the secret exposed to the client. */
    @Id
    private UUID id;
    /** Account that owns this token. */
    private UUID userId;
    /** Purpose that prevents one token kind from being used as another. */
    private AuthTokenType type;
    /** SHA-256 digest of the raw secret; raw tokens are never persisted. */
    private String tokenHash;
    /** UTC instant after which the token is unusable. */
    private Instant expiresAt;
    /** UTC instant of use or revocation; {@code null} means not yet consumed. */
    private Instant consumedAt;
    /** UTC instant at which the token was issued. */
    @Builder.Default
    private Instant createdAt = Instant.now();
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private Long version;
}
