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
 * One evaluation of one model version against one declared baseline.
 *
 * <p>A model is only ever better than something. The baselines include the deterministic rule
 * already in production and the no-prediction case, because a real serving path spends a measurable
 * share of its traffic falling back and an evaluation that ignores those cases hides the harm they
 * cause.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("model_evaluation_runs")
public class ModelEvaluationRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The version being evaluated. */
    private UUID modelVersionId;
    /** What this run measured. */
    private ModelEvaluationKind evaluationKind;
    /** What the version was compared against. */
    private EvaluationBaselineKind baselineKind;
    /** The champion compared against, required when the baseline is a champion. */
    private @Nullable UUID baselineModelVersionId;
    /** The dataset the evaluation split came from, where one applies. */
    private @Nullable UUID trainingDatasetManifestId;
    /** Where the evaluation data is stored. */
    private String evaluationDatasetReference;
    /** UTC instant beyond which the evaluation read nothing. */
    private Instant evaluationCutoffAt;
    /** Which version of the evaluation code produced these numbers. */
    private String codeVersion;
    /** Digest of the evaluation configuration. */
    private String configDigest;
    /** The metric this run is judged on. */
    private String primaryMetricKey;
    /** What the candidate scored. */
    private BigDecimal primaryMetricValue;
    /** What the baseline scored; absent only when there was no prediction to compare. */
    private @Nullable BigDecimal baselineMetricValue;
    /** How far predicted probabilities sit from observed frequencies. */
    private @Nullable BigDecimal calibrationError;
    /** How far the observed input distribution has moved from the trained one. */
    private @Nullable DriftStatus driftStatus;
    /** Ninety-fifth percentile inference latency observed during the run. */
    private @Nullable Integer latencyP95Ms;
    /** Whether the run passed, warned or failed. */
    private EvaluationResult result;
    /** What the run concluded, in prose. */
    private String conclusion;
    /** UTC instant the evaluation ran. */
    private Instant runAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
