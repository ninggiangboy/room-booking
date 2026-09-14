package dev.ngb.backend.hostops.internal.model.benchmark;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * What a cohort of hosts looks like over one period, or the recorded reason there is no answer.
 * <p>There are deliberately no minimum or maximum columns: an extreme is one host's number wearing
 * the cohort's name, and the host who owns it recognises it immediately. Quartiles and a trimmed
 * mean are what a cohort can say without saying who. A suppressed row exists and carries its
 * reason, because a silently missing row is indistinguishable from a pipeline failure.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_benchmark_aggregates")
public class MarketBenchmarkAggregate {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /**
     * The cohort the aggregate was drawn from, which owns the privacy floor it was checked against.
     */
    private UUID cohortDefinitionId;
    /** The host-facing metric being benchmarked. */
    private UUID publicationId;
    /** First civil day the aggregate covers. */
    private LocalDate periodStart;
    /** Day after the last civil day the aggregate covers. */
    private LocalDate periodEnd;
    /** IANA zone the civil period boundaries were computed in. */
    private String periodTimeZone;
    /** Whether the aggregate carries numbers or was suppressed. */
    private BenchmarkPublicationState publicationState;
    /** Why the aggregate was suppressed, which must be the reason that actually holds. */
    private @Nullable BenchmarkSuppressionReason suppressionReason;
    /** How many distinct hosts contributed to the aggregate. */
    private int contributorCount;
    /** How many observations went into the aggregate. */
    private long observationCount;
    /** The share of the aggregate supplied by its largest single contributor. */
    private @Nullable BigDecimal largestContributorShare;
    /** Lower quartile of the cohort. */
    private @Nullable BigDecimal lowerQuartile;
    /** Median of the cohort. */
    private @Nullable BigDecimal medianValue;
    /** Upper quartile of the cohort. */
    private @Nullable BigDecimal upperQuartile;
    /** Mean of the cohort with its extremes removed. */
    private @Nullable BigDecimal trimmedMean;
    /** ISO 4217 alphabetic code the aggregate is denominated in, for money metrics. */
    private @Nullable String currency;
    /** UTC instant up to which input data was complete when the aggregate was computed. */
    private Instant inputWatermark;
    /** UTC instant the aggregate was computed. */
    private Instant computedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
