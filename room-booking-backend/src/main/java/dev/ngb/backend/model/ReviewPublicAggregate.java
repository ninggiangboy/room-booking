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
 * The transparent public average, with the arithmetic it came from.
 *
 * <p>The exact sum, the count and the distribution are stored, and check constraints make them agree:
 * the distribution must add up to the count, and the sum must be one a set of ratings between one and
 * five could actually produce. An average with nothing behind it is absent rather than zero.</p>
 *
 * <p>Nothing here is a model output. The ranking projection lives in its own table so that the number
 * shown to guests stays explainable by arithmetic.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_public_aggregates")
public class ReviewPublicAggregate {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What this counts for. */
    private AggregateSubjectType subjectType;
    /** Which one. */
    private UUID subjectId;
    /** Which direction of review is counted. */
    private ReviewDirection direction;
    /** Overall, or a named category. */
    private String dimensionCode;
    /** Aggregation rule applied. */
    private int aggregateRuleVersion;
    /** Exact sum of the ratings counted. */
    private long ratingSum;
    /** How many ratings were counted. */
    private long ratingCount;
    /** How many rated one. */
    private long distributionOne;
    /** How many rated two. */
    private long distributionTwo;
    /** How many rated three. */
    private long distributionThree;
    /** How many rated four. */
    private long distributionFour;
    /** How many rated five. */
    private long distributionFive;
    /** The average, at full precision. */
    private @Nullable BigDecimal computedAverage;
    /** The average as shown, rounded by the policy rule. */
    private @Nullable BigDecimal displayValue;
    /** Monotonic epoch a consumer compares its cache against. */
    private long publicationEpoch;
    /** How far through the source events this was computed. */
    private @Nullable Instant inputWatermark;
    /** When it was computed. */
    private @Nullable Instant computedAt;
    /** When a rebuild last agreed with it. */
    private @Nullable Instant verifiedAt;
    /** Whether newer input is known to be waiting. */
    private boolean isStale;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether there is anything to show.
     *
     * @return true once at least one rating has been counted
     */
    public boolean hasRatings() {
        return ratingCount > 0;
    }
}
