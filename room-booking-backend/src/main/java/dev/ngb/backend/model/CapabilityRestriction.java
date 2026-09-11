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
 * A withdrawal of authority that is evaluated on top of grants rather than by editing them.
 *
 * <p>Subtracting instead of editing is what keeps both facts available: the principal was granted
 * this capability, *and* a risk or compliance decision currently suppresses it. Editing the grant
 * would destroy the first fact and leave an appeal with nothing to restore.</p>
 *
 * <p>A restriction names either one capability or a group, never both and never neither. Lifting one
 * records who lifted it and when rather than deleting the row.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("capability_restrictions")
public class CapabilityRestriction {

    /** Primary key of the restriction. */
    @Id
    private @Nullable UUID id;
    /** Kind of principal being restricted. */
    private PrincipalType principalType;
    /** Identifier of that principal. */
    private UUID principalId;
    /** Single capability withheld; mutually exclusive with {@link #capabilityGroup}. */
    private @Nullable String capability;
    /** Named group of capabilities withheld; mutually exclusive with {@link #capability}. */
    private @Nullable String capabilityGroup;
    /** How far the withdrawal reaches. */
    private AuthorizationScopeType scopeType;
    /** Resource the scope names; {@code null} only for a global restriction. */
    private @Nullable UUID scopeId;
    /** Stable reason the restriction was applied. */
    private String reasonCode;
    /** Reference to the risk or support decision that required it. */
    private @Nullable String decisionReference;
    /** UTC instant from which the withdrawal applies, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant from which it stops, exclusive; {@code null} while open-ended. */
    private @Nullable Instant effectiveUntil;
    /** Operator who lifted the restriction. */
    private @Nullable UUID liftedBy;
    /** UTC instant of that lifting; paired with {@link #liftedBy}. */
    private @Nullable Instant liftedAt;
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
     * Reports whether this restriction suppresses authority at the supplied instant.
     *
     * @param instant the command's decision instant
     * @return {@code true} when the restriction is unlifted and in force at that instant
     */
    public boolean isActiveAt(Instant instant) {
        return liftedAt == null
                && !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }
}
