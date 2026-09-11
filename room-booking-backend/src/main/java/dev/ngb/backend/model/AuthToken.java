package dev.ngb.backend.model;

import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Persistent one-time token used for email verification or refresh-token sessions.
 *
 * <p>The Lombok annotations generate mutable entity boilerplate and a builder. Spring Data JDBC
 * maps the class to {@code auth_tokens}; {@code @Id} identifies rows and {@code @Version} prevents
 * two concurrent consumers from silently updating the same version.</p>
 *
 * <p>{@link #sessionId}, {@link #rotationGeneration}, {@link #supersededBy}, and
 * {@link #consumptionReason} make rotation lineage expressible, which is what refresh-token reuse
 * detection needs. Without them a stolen token replayed after the legitimate client has already
 * rotated looks exactly like an ordinary refresh, and the only safe response -- revoking the whole
 * session -- has nothing to revoke. The session reference is nullable because tokens issued before
 * migration {@code 014} predate sessions entirely.</p>
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
    private @Nullable UUID id;
    /** Account that owns this token. */
    private UUID userId;
    /** Purpose that prevents one token kind from being used as another. */
    private AuthTokenType type;
    /** SHA-256 digest of the raw secret; raw tokens are never persisted. */
    private String tokenHash;
    /** UTC instant after which the token is unusable. */
    private Instant expiresAt;
    /** UTC instant of use or revocation; {@code null} means not yet consumed. */
    private @Nullable Instant consumedAt;
    /** Why the token stopped being usable; paired with {@link #consumedAt}. */
    private @Nullable TokenConsumptionReason consumptionReason;
    /** Session this token belongs to; {@code null} for tokens issued before sessions existed. */
    private @Nullable UUID sessionId;
    /** Position in the session's rotation chain, used to spot a replayed generation. */
    private int rotationGeneration;
    /** Token that replaced this one when it was rotated. */
    private @Nullable UUID supersededBy;
    /** UTC instant at which the token was issued, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Checks whether this one-time token can be consumed at the supplied instant.
     *
     * @param instant current time
     * @return {@code true} when the token is unconsumed and has not expired
     */
    public boolean isUsableAt(Instant instant) {
        return consumedAt == null && expiresAt.isAfter(instant);
    }

    /**
     * Marks the token consumed, recording why in the same step.
     *
     * <p>The instant and the reason are set together because the database requires both or neither:
     * a consumed token that does not say why makes normal rotation and an attacker replaying a
     * stolen secret the same row shape, and telling those apart is the whole point of reuse
     * detection. Setting them through one method means a caller cannot supply only one.</p>
     *
     * @param instant the command's decision instant
     * @param reason why the token stopped being usable
     */
    public void consume(Instant instant, TokenConsumptionReason reason) {
        this.consumedAt = instant;
        this.consumptionReason = reason;
    }
}
