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
import org.springframework.data.relational.core.mapping.Table;

/**
 * What was actually shown, where, by which ranker, on what features.
 *
 * <p>This is the row that makes position-bias correction possible later. Without a durable record of
 * the position a listing was given, every conversion rate computed from the event stream is
 * confounded by the ranking that produced it, and a model trained on those rates learns to reproduce
 * its own history.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ranking_exposures")
public class RankingExposure {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The search request this row belongs to. */
    private UUID searchRequestId;
    /** The listing this row belongs to. */
    private UUID listingId;
    /** The ranking epoch this row belongs to. */
    private UUID rankingEpochId;
    /** The ranking policy version this row belongs to. */
    private UUID rankingPolicyVersionId;
    /** The ranking model version this row belongs to. */
    private @Nullable UUID rankingModelVersionId;
    /** Why this listing occupied this slot. */
    private ExposureReason exposureReason;
    /** The exploration budget window this row belongs to. */
    private @Nullable UUID explorationBudgetWindowId;
    /** Whether the paid-placement label was shown; true exactly when the placement was sponsored. */
    private boolean sponsoredLabelShown;
    /** One-based rank the listing was shown at. */
    private short position;
    /** One-based page it appeared on. */
    private short pageNumber;
    /** Final score that produced this rank. */
    private @Nullable BigDecimal score;
    /** Deterministic baseline score before any personalization. */
    private @Nullable BigDecimal baselineScore;
    /** How much personalization moved the score, which requires a named guest profile. */
    private @Nullable BigDecimal personalizedContribution;
    /** Modelled probability of this position being examined, used later to correct for position bias. */
    private @Nullable BigDecimal positionPropensity;
    /** Whether the re-ranker moved this result for diversity. */
    private boolean diversityAdjusted;
    /** The listing discovery profile this row belongs to. */
    private @Nullable UUID listingDiscoveryProfileId;
    /** The guest preference profile this row belongs to. */
    private @Nullable UUID guestPreferenceProfileId;
    /** Digest of the feature vector scored, so a model-served rank can be reproduced. */
    private @Nullable String featureVectorDigest;
    /** Which prior the listing features fell back on. */
    private PriorFallbackLevel priorFallbackLevel;
    /** Key of the experiment assignment in force for this result. */
    private @Nullable String experimentAssignmentKey;
    /** UTC instant the result was served. */
    private Instant occurredAt;
    /** UTC instant after which this exposure record must be deleted. */
    private Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
