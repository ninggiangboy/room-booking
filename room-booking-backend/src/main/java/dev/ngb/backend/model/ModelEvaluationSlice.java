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
 * One slice of one evaluation, and what the model did to the people in it.
 *
 * <p>An aggregate number can be excellent while one market, one language or one class of host
 * carries every error, and the marketplace has two sides that a single figure averages together. A
 * slice too small to report without identifying the people in it is not released -- unless the harm
 * is a safety harm, because a privacy threshold is a reason to handle a finding carefully, never a
 * reason for nobody to be told that a small group is being hurt.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("model_evaluation_slices")
public class ModelEvaluationSlice {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The evaluation this slice belongs to. */
    private UUID modelEvaluationRunId;
    /** What the population was cut by. */
    private String sliceDimension;
    /** Which value of that dimension this row measures. */
    private String sliceValue;
    /** Which side of the marketplace this slice is about. */
    private AffectedParty partyAffected;
    /** What kind of harm is being measured. */
    private FairnessHarmClass harmClass;
    /** How many distinct subjects fall in the slice. */
    private long subjectCount;
    /** The metric measured for this slice. */
    private String metricKey;
    /** What the slice scored. */
    private BigDecimal metricValue;
    /** What the comparison population scored. */
    private @Nullable BigDecimal referenceValue;
    /** Gap between the slice and the reference. */
    private @Nullable BigDecimal disparity;
    /** The gap above which the slice counts as breached. */
    private @Nullable BigDecimal threshold;
    /** Whether the threshold was exceeded. */
    private boolean breached;
    /** What is being done about it, required when the threshold was breached. */
    private @Nullable String mitigation;
    /** Below this, the slice is too small to report without identifying people. */
    private int minimumCohortSize;
    /** Whether the slice may be reported; only a safety harm escapes the cohort floor. */
    private boolean released;
    /** What this measurement cannot show. */
    private @Nullable String limitationNote;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
