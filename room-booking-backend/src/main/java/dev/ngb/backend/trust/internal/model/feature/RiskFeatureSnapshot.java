package dev.ngb.backend.trust.internal.model.feature;

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
import dev.ngb.backend.platform.JsonDocument;

/**
 * The feature values as they stood at the instant of one evaluation.
 *
 * <p>This is what makes a decision reproducible: not the feature store as it is now, but a digest of
 * what was actually read, together with which feature versions produced it and how far the input
 * streams had progressed. Append-only. A snapshot must carry either the values or a retention-
 * controlled reference to them -- one with neither replays nothing while looking as though it
 * would.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_feature_snapshots")
public class RiskFeatureSnapshot {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The evaluation this snapshot belongs to. */
    private String evaluationKey;
    /** Which feature set was read. */
    private String featureSetReference;
    /** The exact definition version behind each value. */
    private JsonDocument featureVersions;
    /** Hex digest of the values, for replay comparison. */
    private String valuesDigest;
    /** The values themselves, where they are small enough to keep inline. */
    private @Nullable JsonDocument valuesDocument;
    /** Where the values are held, when they are not inline. */
    private @Nullable String valuesStorageReference;
    /** The instant the snapshot claims to describe. */
    private Instant asOfTime;
    /** How far the input streams had progressed; never later than the as-of instant. */
    private Instant watermarkTime;
    /** Features that could not be computed, named rather than defaulted. */
    private String[] missingFeatureKeys;
    /** Features served beyond their maximum age. */
    private String[] staleFeatureKeys;
    /** When the snapshot was taken. */
    private Instant computedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
