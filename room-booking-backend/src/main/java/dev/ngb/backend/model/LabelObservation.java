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
 * One observed outcome for one example under one label definition.
 *
 * <p>Append-only. A correction is a new revision naming the row it replaces, so that a model
 * released against the earlier reading stays reproducible whatever the outcome turns out to be
 * later. An answer may not be dated before its horizon has ended and its maturity delay has run:
 * reading a seven-day outcome on day three is not an early result, it is a different question.</p>
 *
 * <p>A human decision is recorded with the reviewer's role class, the policy version and a
 * confidence, because a raw agent override is not clean ground truth and the disagreement between
 * reviewers is itself something a dataset builder has to be able to measure.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("label_observations")
public class LabelObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The target this outcome was observed for. */
    private UUID labelDefinitionId;
    /** Stable key naming the example, unique per target and revision. */
    private String exampleKey;
    /** What the example is keyed by; must match the definition. */
    private FeatureEntityKind entityKind;
    /** Hex pseudonym of the entity, never a raw identifier. */
    private String entityPseudonym;
    /** Digest of the request context, absent for entity-only targets. */
    private @Nullable String contextDigest;
    /** UTC instant at which the prediction for this example is deemed to have been made. */
    private Instant predictionAt;
    /** UTC instant the outcome window closes, computed from the definition's horizon. */
    private Instant horizonEndsAt;
    /** UTC instant the outcome was seen, absent while it is unresolved. */
    private @Nullable Instant observedAt;
    /** UTC instant the outcome could be treated as settled, never before the maturity delay. */
    private @Nullable Instant maturedAt;
    /** What is known about the outcome; three of the five states are not an answer. */
    private LabelValueState valueState;
    /** The outcome value, present only for a resolved observation. */
    private @Nullable BigDecimal labelValue;
    /** Training weight, capped so one actor's volume cannot dominate a dataset. */
    private BigDecimal exampleWeight;
    /** Why the example stopped being observable, required when it is censored. */
    private @Nullable String censoringReason;
    /** Why the example is kept out of training, required when it is excluded. */
    private @Nullable String exclusionReason;
    /** How this example came to be observed at all. */
    private LabelObservationBasis observationBasis;
    /** The policy that caused this example to be observed, where one did. */
    private @Nullable String selectionPolicyVersion;
    /** The model that selected this example, where one did; often another system. */
    private @Nullable String selectionModelVersion;
    /** Role class of the person who decided, required for a human review. */
    private @Nullable String reviewerRoleClass;
    /** How sure the reviewer was, required for a human review. */
    private @Nullable BigDecimal reviewerConfidence;
    /** The policy version the reviewer applied, required for a human review. */
    private @Nullable String policyVersion;
    /** How an appeal against the underlying decision was resolved. */
    private @Nullable LabelAppealOutcome appealOutcome;
    /** Digest of the source revisions this reading was taken from. */
    private @Nullable String sourceRevisionDigest;
    /** Which revision of this example's outcome this is; the first is one. */
    private int revisionNumber;
    /** The revision this one corrects, required for every revision after the first. */
    private @Nullable UUID supersedesObservationId;
    /** The run that recorded this observation. */
    private UUID pipelineRunId;
    /** UTC instant the observation was written. */
    private Instant recordedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
