package dev.ngb.backend.model;

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
 * One request to a model and what came back, bounded and expiring.
 *
 * <p>A prediction is evidence, not an attribute of a person. The output is size-capped so that a
 * convenient log of everything the model saw cannot be written here under the name of a score, the
 * uncertainty state has no null, and a request that timed out, found no features or was refused on
 * consent is recorded as the fallback it was rather than omitted -- evaluating only the requests
 * that returned a score hides exactly the outage and fallback harm the evaluation exists to
 * find.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("prediction_records")
public class PredictionRecord {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The version that answered, or was asked and could not. */
    private UUID modelVersionId;
    /** The route resolved for this request, pinned once and recorded. */
    private @Nullable UUID modelReleaseRouteId;
    /** The service or use case that asked. */
    private String consumer;
    /** The outcome that was asked about. */
    private String targetName;
    /** Digest of the canonical request context, part of the idempotency key. */
    private String canonicalContextDigest;
    /** The caller's idempotency key; the same question returns the same stored answer. */
    private String requestKey;
    /** What the request was keyed by. */
    private FeatureEntityKind entityKind;
    /** Hex pseudonym of the subject, where the request had one. */
    private @Nullable String entityPseudonym;
    /** ISO 3166-1 alpha-2 market the request was made in. */
    private @Nullable String marketCode;
    /** The input contract the request was resolved against; must be the model's own. */
    private UUID featureSetVersionId;
    /** UTC instant of the oldest feature value used, for freshness analysis. */
    private @Nullable Instant oldestFeatureAsOf;
    /** Whether this was an online request, a batch score or a shadow call. */
    private ServingMode servingMode;
    /** The bounded model output, present only for an actual prediction. */
    private @Nullable JsonDocument outputs;
    /** Size of the output, capped so a raw feature dump cannot be logged as a score. */
    private int outputByteSize;
    /** How far the inputs sat from the range the model was validated on. */
    private UncertaintyStatus uncertaintyStatus;
    /** Approved reason codes explaining the output, never raw feature contributions. */
    private @Nullable String reasonCodes;
    /** Whether the model answered, and if not, how the request failed. */
    private PredictionStatus status;
    /** Why no prediction was produced, required whenever the status is not a prediction. */
    private @Nullable String fallbackReason;
    /** The exposure this request was made under, where one applies. */
    private @Nullable UUID experimentExposureId;
    /** How long the call took. */
    private @Nullable Integer latencyMs;
    /** UTC instant the request was answered. */
    private Instant predictedAt;
    /** UTC instant after which the answer may no longer be cited by a decision. */
    private Instant expiresAt;
    /** How long this record is kept. */
    private PredictionRetentionClass retentionClass;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
