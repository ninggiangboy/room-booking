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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One durable sign-in, owning the refresh-token lineage issued under it.
 *
 * <p>The session is what makes "show me my devices" and "sign out everywhere" possible: without it,
 * a refresh token is an isolated secret with nothing to revoke alongside it. It is also what makes
 * reuse detection actionable — a replayed token identifies the session whose entire lineage must
 * be revoked.</p>
 *
 * <p>Two independent expiries are kept deliberately. {@link #idleExpiresAt} ends a session that has
 * gone quiet; {@link #absoluteExpiresAt} ends one that has lived too long regardless of activity, so
 * a stolen session cannot be kept alive indefinitely by using it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("auth_sessions")
public class AuthSession {

    /** Primary key of the session. */
    @Id
    private @Nullable UUID id;
    /** User the session belongs to. */
    private UUID userId;
    /** Organization the session is currently acting for, where one is selected. */
    private @Nullable UUID activeOrganizationId;
    /** How the principal proved who they were when the session opened. */
    private AuthenticationMethod authenticationMethod;
    /** Strength of that proof, compared against what a sensitive command demands. */
    private AssuranceLevel assuranceLevel;
    /** UTC instant assurance was last proven, which step-up refreshes. */
    private Instant lastAssuranceProofAt;
    /** Client description shown to the owner when listing their devices. */
    private @Nullable String clientDescriptor;
    /** SHA-256 digest of the network origin; the raw address is not retained. */
    private @Nullable String originHash;
    /** Number of refresh rotations performed, used to spot a replayed generation. */
    private int rotationGeneration;
    /** Refresh token currently valid for this session. */
    private @Nullable UUID currentTokenId;
    /** UTC instant the session was last exercised. */
    private Instant lastUsedAt;
    /** UTC instant the session ends if left unused. */
    private Instant idleExpiresAt;
    /** UTC instant the session ends regardless of activity. */
    private Instant absoluteExpiresAt;
    /** UTC instant the session was revoked. */
    private @Nullable Instant revokedAt;
    /** Stable reason for revocation; paired with {@link #revokedAt}. */
    private @Nullable String revocationReason;
    /** Principal that revoked the session, where it was not the owner. */
    private @Nullable UUID revokedBy;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether the session may still be exercised at the supplied instant.
     *
     * <p>Equality on either deadline means expired, matching the convention that a deadline reached
     * exactly has passed.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the session is unrevoked and within both expiries
     */
    public boolean isLiveAt(Instant instant) {
        return revokedAt == null
                && idleExpiresAt.isAfter(instant)
                && absoluteExpiresAt.isAfter(instant);
    }
}
