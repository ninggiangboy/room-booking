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
 * One model estimate of a named risk over a named horizon.
 *
 * <p>There is deliberately no action column: a score is not a decision, and the only way to keep that
 * true under years of pressure is for the row to have nowhere to put one. A served prediction carries
 * a score and a fallback carries none, paired by check constraint, so a timeout cannot be read
 * afterwards as a confident zero.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_model_predictions")
public class RiskModelPrediction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The evaluation this prediction was produced for. */
    private String evaluationKey;
    /** Which model. */
    private String modelKey;
    /** Which version of it. */
    private String modelVersion;
    /** The served artifact. */
    private String artifactReference;
    /** The feature set the model read. */
    private String featureSetReference;
    /** The snapshot the values came from. */
    private @Nullable UUID riskFeatureSnapshotId;
    /** The event being estimated. */
    private String targetEvent;
    /** How far ahead the estimate reaches. */
    private int horizonSeconds;
    /** The estimate between zero and one; absent exactly when a fallback served. */
    private @Nullable BigDecimal score;
    /** How uncertain the estimate is. */
    private @Nullable BigDecimal uncertainty;
    /** The calibration the score was produced under. */
    private @Nullable String calibrationReference;
    /** Why no score was produced, or that the model served normally. */
    private ModelServingFallback servingFallback;
    /** How long serving took. */
    private @Nullable Integer latencyMs;
    /** When the estimate was made. */
    private Instant evaluatedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
