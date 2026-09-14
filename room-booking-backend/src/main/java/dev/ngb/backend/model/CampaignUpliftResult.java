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
 * What a campaign measurably changed, against its own holdout.
 *
 * <p>Append-only, and it cannot exist without a holdout. Counting redemptions measures who took
 * the money; the difference against people who were never contacted is the only thing that
 * measures whether anybody behaved differently.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("campaign_uplift_results")
public class CampaignUpliftResult {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The campaign being measured. */
    private UUID growthCampaignId;
    /** Migration 030 analysis run this result comes from. */
    private UUID experimentAnalysisRunId;
    /** The metric the effect is measured on. */
    private UUID metricDefinitionId;
    /** How many people were contacted. */
    private long treatmentSubjects;
    /** How many were deliberately not contacted. */
    private long holdoutSubjects;
    /** Metric value among the people contacted. */
    private @Nullable BigDecimal treatmentRate;
    /** Metric value among the people held out. */
    private @Nullable BigDecimal holdoutRate;
    /** Difference between the two, which is what the campaign actually changed. */
    private BigDecimal incrementalEffect;
    /** Lower end of the interval around that difference. */
    private BigDecimal effectIntervalLow;
    /** Upper end of the interval around that difference. */
    private BigDecimal effectIntervalHigh;
    /** Confidence level the interval was computed at. */
    private BigDecimal intervalConfidence;
    /** What the campaign cost, in integer minor units. */
    private long incrementalCostMinor;
    /** ISO 4217 alphabetic code the cost is denominated in. */
    private String currency;
    /** Cost per unit of measured effect, only where there was one. */
    private @Nullable Long costPerIncrementalMinor;
    /** What the interval says, rather than what anybody hoped. */
    private CampaignUpliftConclusion conclusion;
    /** Why that conclusion follows from the numbers beside it. */
    private String conclusionRationale;
    /** UTC instant the result was computed. */
    private Instant computedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
