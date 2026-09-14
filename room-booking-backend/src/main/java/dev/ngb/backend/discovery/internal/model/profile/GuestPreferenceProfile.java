package dev.ngb.backend.discovery.internal.model.profile;

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
import dev.ngb.backend.review.types.DerivedProfileStatus;

/**
 * One guest's derived preference profile, bounded by the evidence window it was built from.
 *
 * <p>The evidence start is what makes an erasure directive real: a profile reaching back past a
 * directive's cutoff is refused even though every field in it is individually valid.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("guest_preference_profiles")
public class GuestPreferenceProfile {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The account holder this row belongs to. */
    private UUID accountHolderId;
    /** Which version of the profile applies. */
    private int profileVersion;
    /** Whether this profile version is the one being served. */
    private DerivedProfileStatus status;
    /** Earliest behaviour this profile was built from; an erasure cutoff is enforced against it. */
    private Instant evidenceFrom;
    /** Latest behaviour this profile was built from. */
    private Instant evidenceTo;
    /** UTC instant up to which inputs were included. */
    private @Nullable Instant inputWatermark;
    /** The erasure directive this row belongs to. */
    private @Nullable UUID erasureDirectiveId;
    /** Which version of the feature-generation job produced this profile. */
    private String featureGenerationVersion;
    /** Digest of the feature schema, so it can be shown later to be unchanged. */
    private String featureSchemaDigest;
    /** How much the profile as a whole can be relied on, as a fraction. */
    private BigDecimal overallConfidence;
    /** How many behavioural events stand behind the profile. */
    private long evidenceEventCount;
    /** How many completed stays stand behind it, which weigh far more than events. */
    private long evidenceStayCount;
    /** UTC instant the profile was computed. */
    private Instant computedAt;
    /** UTC instant after which the profile is stale. */
    private Instant expiresAt;
    /** The superseded by profile this row belongs to. */
    private @Nullable UUID supersededByProfileId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
