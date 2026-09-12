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
 * The ranking-quality projection for one listing.
 *
 * <p>Kept apart from the public average on purpose. A posterior with an uncertainty interval is a good
 * input to ordering and a bad thing to show as "the rating", so it carries the model version that
 * produced it and an interval that must contain its own mean.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_quality_profiles")
public class ListingQualityProfile {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Listing described. */
    private UUID listingId;
    /** Version of this computation. */
    private int profileVersion;
    /** Estimated quality. */
    private @Nullable BigDecimal posteriorMean;
    /** Lower bound of the uncertainty interval. */
    private @Nullable BigDecimal posteriorIntervalLow;
    /** Upper bound of the uncertainty interval. */
    private @Nullable BigDecimal posteriorIntervalHigh;
    /** How many reviews were behind it. */
    private long evidenceCount;
    /** Weighted evidence after recency and quality weighting. */
    private @Nullable BigDecimal effectiveEvidence;
    /** Window the recent value covers. */
    private @Nullable Short recentWindowDays;
    /** Value over that recent window. */
    private @Nullable BigDecimal recentMean;
    /** Which way it has moved. */
    private @Nullable TrendDirection trendDirection;
    /** Aggregation rule the inputs followed. */
    private int aggregateRuleVersion;
    /** Model that produced the posterior. */
    private String modelVersion;
    /** How far through the inputs it read. */
    private @Nullable Instant inputWatermark;
    /** Digest of exactly what it read. */
    private @Nullable String sourceManifestDigest;
    /** Whether this is the profile in force. */
    private DerivedProfileStatus status;
    /** When it was computed. */
    private Instant computedAt;
    /** When it should be recomputed. */
    private @Nullable Instant expiresAt;
    /** Profile that replaced it. */
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
