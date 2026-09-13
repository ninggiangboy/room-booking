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
 * One historical feature value, effective over an interval, for point-in-time lookup.
 *
 * <p>This is where point-in-time correctness lives. The row records when its source occurred, when
 * that source became knowable, and the interval over which the value is effective, and a database
 * check refuses a value that becomes effective before it could have been read. Without that, a
 * training example at time t can pick up the settled outcome of the very event it is meant to
 * predict, which produces a model with excellent offline metrics and no production effect at
 * all.</p>
 *
 * <p>Missing is a state with its own row rather than an absent row, because a lookup that finds
 * nothing cannot tell "this guest has never booked" from "the pipeline did not run".</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("offline_feature_values")
public class OfflineFeatureValue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The feature version this value is an instance of. */
    private UUID featureDefinitionId;
    /** What the value is keyed by; must match the definition. */
    private FeatureEntityKind entityKind;
    /** Hex pseudonym of the entity, never a raw identifier. */
    private String entityPseudonym;
    /** Digest of the request context, absent for entity-only features. */
    private @Nullable String contextDigest;
    /** UTC instant the underlying event happened. */
    private Instant sourceOccurredAt;
    /** UTC instant that event became readable, never before it happened. */
    private Instant sourceAvailableAt;
    /** UTC instant the value becomes effective, never before it could have been read. */
    private Instant validFrom;
    /** UTC instant the value stops being effective, absent while it is the latest. */
    private @Nullable Instant validTo;
    /** Whether this is a measurement, an explicit absence or a substitution. */
    private FeatureValueState valueState;
    /** The value, for integer and decimal features. */
    private @Nullable BigDecimal valueNumeric;
    /** The value, for boolean, categorical and timestamp features. */
    private @Nullable String valueText;
    /** Where the value is stored, for embeddings held in analytical storage. */
    private @Nullable String valueReference;
    /** How the value was substituted, present only when it was imputed. */
    private @Nullable ImputationMethod imputationMethod;
    /** The run that produced this value. */
    private UUID pipelineRunId;
    /** Aggregate version of the source the computation read. */
    private @Nullable Long sourceVersion;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
