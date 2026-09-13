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
 * One effect estimate for one metric and arm.
 *
 * <p>Append-only. The point estimate lies inside its own interval, and a result is reported either
 * frequentist or Bayesian -- never both, which would let whichever reading looks better be the one
 * quoted.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_analysis_estimates")
public class ExperimentAnalysisEstimate {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment analysis run this row belongs to. */
    private UUID experimentAnalysisRunId;
    /** The declared metric this estimate is for. */
    private UUID experimentMetricId;
    /** The arm being estimated. */
    private UUID experimentVariantId;
    /** The arm it is compared against. */
    private UUID comparisonVariantId;
    /** The heterogeneity slice, absent for the overall estimate. */
    private @Nullable String sliceKey;
    /** The point estimate, which always lies inside its own interval. */
    private BigDecimal estimate;
    /** Lower bound of the interval. */
    private BigDecimal intervalLow;
    /** Upper bound of the interval. */
    private BigDecimal intervalHigh;
    /** The same effect expressed relative to the comparison arm. */
    private @Nullable BigDecimal relativeEffect;
    /** Frequentist result; never reported alongside a posterior. */
    private @Nullable BigDecimal pValue;
    /** Bayesian result; never reported alongside a p-value. */
    private @Nullable BigDecimal posteriorProbability;
    /** How many units in the arm being estimated. */
    private long treatmentSampleSize;
    /** How many in the comparison arm. */
    private long controlSampleSize;
    /** Whether this reading was pre-registered rather than explored afterwards. */
    private boolean confirmatory;
    /** Whether this estimate crosses its guardrail threshold. */
    private boolean guardrailBreached;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
