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
 * Authority for a principal to do specific things, to specific resources, over a specific span.
 *
 * <p>This is what a role could not express. "Has the host role" says nothing about *which* listings
 * a principal may edit, so a listing-scoped grant is both stronger and narrower than a role: owning
 * a listing is a fact about that listing, not about the account.</p>
 *
 * <p>Grants are evaluated against a decision instant and have active restrictions subtracted from
 * them, so a compliance hold can remove a capability without removing the grant that explains why
 * the principal had it. Revocation is recorded rather than applied by deletion, and revoking a
 * delegation must revoke everything derived from it through {@link #derivedFromGrantId}.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("capability_grants")
public class CapabilityGrant {

    /** Primary key of the grant. */
    @Id
    private @Nullable UUID id;
    /** Kind of principal holding the authority. */
    private PrincipalType granteeType;
    /** Identifier of that principal. */
    private UUID granteeId;
    /** Kind of principal that conferred it; absent for platform-conferred grants. */
    private @Nullable PrincipalType grantorType;
    /** Identifier of the granting principal. */
    private @Nullable UUID grantorId;
    /** Convenience label for the bundle, where the grant came from a named role. */
    private @Nullable String roleName;
    /** Materialized capability names this grant confers. */
    private String[] capabilities;
    /** How far the authority reaches. */
    private AuthorizationScopeType scopeType;
    /** Resource the scope names; {@code null} only for a global grant. */
    private @Nullable UUID scopeId;
    /** Market the grant is confined to, where it is market-specific. */
    private @Nullable String marketCode;
    /** UTC instant from which the authority applies, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant from which it stops, exclusive; {@code null} while open-ended. */
    private @Nullable Instant effectiveUntil;
    /** Stable reason the grant was made, for operator review. */
    private String reasonCode;
    /** Why the grant exists, distinguishing self-service from imposed authority. */
    private GrantSource source;
    /** Grant this one was delegated from; revoking that one must revoke this one. */
    private @Nullable UUID derivedFromGrantId;
    /** UTC instant the grant was withdrawn. */
    private @Nullable Instant revokedAt;
    /** Stable reason for the withdrawal; paired with {@link #revokedAt}. */
    private @Nullable String revocationReason;
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
     * Reports whether this grant confers authority at the supplied instant.
     *
     * <p>The span is half-open, matching how the database ranges are defined, so a decision taken
     * exactly at {@code effectiveUntil} is no longer covered. This answers only "was it granted";
     * an authorization decision must still subtract active restrictions.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the grant is unrevoked and in force at that instant
     */
    public boolean isEffectiveAt(Instant instant) {
        return revokedAt == null
                && !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }
}
