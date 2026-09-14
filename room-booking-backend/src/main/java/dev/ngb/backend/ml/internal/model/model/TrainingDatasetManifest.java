package dev.ngb.backend.ml.internal.model.model;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.platform.DataPrivacyClass;

/**
 * Immutable description of one dataset build.
 *
 * <p>The manifest pins the population, the split, the cutoffs, the source snapshots, the code and
 * config digests, the random seed and the deletion watermark, so that the build can be shown to
 * have honoured the erasures standing at the time. Everything but the reuse state is frozen once
 * the row exists: a later deletion or correction marks the dataset unusable for a new build without
 * touching the artifact, because a model already released against it must stay reproducible.</p>
 *
 * <p>A random row split is refused for time-dependent behaviour. The same booking, host, guest or
 * near duplicate listing landing on both sides of a split is the cheapest way to produce a test
 * score that measures the leak rather than the model.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("training_dataset_manifests")
public class TrainingDatasetManifest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the dataset. */
    private String datasetKey;
    /** Semantic version of this build. */
    private short semanticVersion;
    /** What the dataset was built for. */
    private String purpose;
    /** Team accountable for the build. */
    private String ownerReference;
    /** The model family the dataset was approved to train. */
    private String approvedModelFamily;
    /** The target every example carries. */
    private UUID labelDefinitionId;
    /** The frozen feature set the examples were built from. */
    private UUID featureSetVersionId;
    /** Which examples were eligible. */
    private String populationExpression;
    /** How examples were selected from that population. */
    private SamplingStrategy samplingStrategy;
    /** How the dataset was divided into train, validation and test. */
    private SplitStrategy splitStrategy;
    /** Whether the behaviour is time-dependent, which forbids a random split. */
    private boolean timeDependent;
    /** What is kept whole across the split, required for an entity-grouped split. */
    private @Nullable String groupingExpression;
    /** UTC instant the training window opens. */
    private Instant trainRangeStart;
    /** UTC instant the training window closes. */
    private Instant trainRangeEnd;
    /** UTC instant the validation window opens. */
    private Instant validationRangeStart;
    /** UTC instant the validation window closes. */
    private Instant validationRangeEnd;
    /** UTC instant the test window opens. */
    private Instant testRangeStart;
    /** UTC instant the test window closes. */
    private Instant testRangeEnd;
    /** The instant each example's features were read as of. */
    private String predictionTimeRule;
    /** UTC instant beyond which no feature value was read. */
    private Instant featureCutoffAt;
    /** UTC instant beyond which no outcome was read. */
    private Instant labelCutoffAt;
    /** Whether the build handles examples whose horizon had not matured by the cutoff. */
    private boolean censoringModelled;
    /** The immutable source snapshots the build pinned. */
    private String sourceSnapshotReference;
    /** Which version of the build code produced it. */
    private String codeVersion;
    /** Digest of the build configuration. */
    private String configDigest;
    /** Digest of the whole specification, so a retry returns the same manifest. */
    private String specificationDigest;
    /** The seed, so the same specification reproduces the same dataset. */
    private long randomSeed;
    /** The query that selected only subjects who permitted this purpose. */
    private String consentQueryReference;
    /** What was deliberately left out. */
    private @Nullable String exclusionExpression;
    /** UTC instant up to which erasures were honoured by this build. */
    private Instant deletionWatermarkAt;
    /** How the dataset must be handled. */
    private DataPrivacyClass privacyClass;
    /** How long the dataset is kept. */
    private int retentionDays;
    /** How class imbalance was handled. */
    private @Nullable String classWeighting;
    /** Whether the build's leakage checks passed; a model cannot be validated without it. */
    private boolean leakageChecksPassed;
    /** What the dataset is known not to support. */
    private String knownLimitations;
    /** How many examples the build produced. */
    private long exampleCount;
    /** How many distinct subjects those examples cover. */
    private long subjectCount;
    /** Share of resolved examples that are positive. */
    private @Nullable BigDecimal positiveRate;
    /** Where the slice distributions are recorded. */
    private @Nullable String sliceDistributionReference;
    /** Where the dataset itself is stored. */
    private String artifactReference;
    /** Checksum of the artifact, so it can be shown later to be unchanged. */
    private String artifactChecksum;
    /** Who may read the artifact. */
    private String accessPolicy;
    /** Whether the dataset may still be used for a new release. */
    private DatasetReuseState reuseState;
    /** UTC instant the dataset stopped being reusable. */
    private @Nullable Instant invalidatedAt;
    /** What made it unusable for a new build. */
    private @Nullable String invalidationReason;
    /** UTC instant the build finished. */
    private Instant builtAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
