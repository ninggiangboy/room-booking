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
 * One analysis of one epoch at one data cutoff.
 *
 * <p>A second look requires a declared sequential method, because peeking at an unadjusted p-value
 * is not a stopping rule. An analysis with a sample-ratio mismatch may record that it is invalid;
 * it may not record a ship decision.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_analysis_runs")
public class ExperimentAnalysisRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment epoch this row belongs to. */
    private UUID experimentEpochId;
    /** Which version of the pre-registered plan was followed. */
    private short analysisPlanVersion;
    /** Which look this is; a second one requires a sequential method. */
    private int lookNumber;
    /** Latest data included. */
    private Instant dataCutoffAt;
    /** What quantity was estimated. */
    private ExperimentEstimand estimand;
    /** How intervals were computed. */
    private IntervalMethod intervalMethod;
    /** How this look was paid for. */
    private SequentialMethod sequentialMethod;
    /** How much of the error budget this look used. */
    private @Nullable BigDecimal alphaSpent;
    /** Whether observed counts matched the declared allocation. */
    private SampleRatioStatus srmStatus;
    /** The mismatch test result. */
    private @Nullable BigDecimal srmPValue;
    /** Standing of the other automated integrity checks. */
    private AnalysisIntegrityStatus integrityStatus;
    /** How complete the inputs were, between zero and one. */
    private @Nullable BigDecimal dataCompleteness;
    /** How many units were assigned. */
    private long assignmentCount;
    /** How many were actually exposed. */
    private long exposureCount;
    /**
     * UTC instant the novelty period is taken to end, so early and mature effects can be read
     * apart.
     */
    private @Nullable Instant noveltyPeriodEnd;
    /**
     * Digest of the analysis code, so the result can be reproduced from the historical definition.
     */
    private String codeDigest;
    /** The snapshot the analysis read. */
    private String datasetSnapshotReference;
    /** What was concluded; unavailable when integrity checks failed. */
    private @Nullable AnalysisConclusion conclusion;
    /** Why, in words a reviewer can weigh. */
    private @Nullable String conclusionRationale;
    /** Who ran and signed off the analysis. */
    private String analyzedBy;
    /** UTC instant the analysis ran. */
    private Instant runAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
