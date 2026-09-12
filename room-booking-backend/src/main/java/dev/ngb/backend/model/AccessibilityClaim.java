package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * An accessibility feature a host claims, and what backs the claim.
 *
 * <p>A guest may rely on one of these to decide whether they can physically enter the property, so
 * the row is built to be relied on. Measurements carry their unit, because {@code 32} on its own is
 * ambiguous and a doorway is not somewhere to guess. Verification is recorded with its evidence,
 * because an unverified claim and a verified one are different promises and should not look
 * identical to the person deciding whether to travel.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("accessibility_claims")
public class AccessibilityClaim {

    /** Primary key of the claim. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type the claim is about. */
    private UUID accommodationTypeId;
    /** Stable key of the feature, such as {@code STEP_FREE_ENTRANCE}. */
    private String claimKey;
    /** Whether the feature is claimed; {@code false} is a real answer. */
    private boolean isClaimed;
    /** Measured value, where the feature has one. */
    private @Nullable BigDecimal measurementValue;
    /** Unit of {@link #measurementValue}; never omitted when a value is present. */
    private @Nullable String measurementUnit;
    /** Media showing the feature, where the host supplied evidence. */
    private @Nullable UUID evidenceMediaId;
    /** UTC instant the claim was verified. */
    private @Nullable Instant verifiedAt;
    /** Operator who verified it; paired with {@link #verifiedAt}. */
    private @Nullable UUID verifiedBy;
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
     * Reports whether the claim has been independently confirmed.
     *
     * @return {@code true} when an operator verified the claim
     */
    public boolean isVerified() {
        return verifiedAt != null;
    }
}
