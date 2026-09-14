package dev.ngb.backend.ml.internal.model.feature;

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
import dev.ngb.backend.analytics.types.DeletionBehaviour;

import dev.ngb.backend.ml.internal.model.DefinitionStatus;
import dev.ngb.backend.ml.internal.model.FeatureEntityKind;

/**
 * Registered definition of one model input, at one semantic version.
 *
 * <p>A definition is a contract: it states what the feature means, where it comes from, when it is
 * knowable, what it may be used for and who owns it. Once the row leaves DRAFT a database trigger
 * freezes everything except the lifecycle columns, because values have been written and models
 * trained under this meaning and changing it in place would rewrite what they were.</p>
 *
 * <p>Two of the columns carry rules that are easy to lose under deadline. A feature that cannot be
 * reproduced offline may exist, but may not be the basis of a consequential decision, because there
 * would be no way to show afterwards what it actually was. And a feature offered on both the
 * offline and the online path must name the parity test that proves the two agree, since two
 * independent implementations of one formula diverge and the divergence is normally discovered in
 * production.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("feature_definitions")
public class FeatureDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the feature, carrying its meaning, its window and its version. */
    private String featureKey;
    /** Semantic version of this definition; a changed meaning is a new version. */
    private short semanticVersion;
    /** Human-readable name for registry listings. */
    private String displayName;
    /** What the feature is keyed by, and therefore which sets may contain it. */
    private FeatureEntityKind entityKind;
    /** Request context keys the value additionally varies by, when it is not entity-only. */
    private @Nullable String contextKeys;
    /** Declared type, which decides which value column an instance must use. */
    private FeatureDataType valueType;
    /** Permitted numeric range, for validation and skew monitoring. */
    private @Nullable String allowedRange;
    /** Comma-separated permitted levels; an undeclared level is refused on write. */
    private @Nullable String allowedCategories;
    /** How the serving path presents absence to the model. */
    private FeatureMissingRepresentation missingRepresentation;
    /** What is substituted when no value qualifies. */
    private FeatureDefaultPolicy defaultPolicy;
    /** The constant substituted, present only when the policy is a constant. */
    private @Nullable String defaultValue;
    /** How the value is derived from its source. */
    private FeatureComputationKind computationKind;
    /** The reviewed computation, in prose, that both execution paths implement. */
    private String computationSpecification;
    /** Digest of the computation specification, so a silent edit is detectable. */
    private String computationDigest;
    /** When and how values are produced. */
    private FeatureMaterializationMode materializationMode;
    /** The registered data product this feature reads. */
    private UUID sourceDataProductId;
    /** Which instant the window is measured against. */
    private FeatureEventTimeBasis eventTimeBasis;
    /** Length of the trailing window, absent for features that have none. */
    private @Nullable Integer windowDays;
    /** How far back the computation reads, never less than the window. */
    private @Nullable Integer lookbackDays;
    /** How stale a value may be before the feature is considered late. */
    private int freshnessTargetMinutes;
    /** How long a served value stays usable; required for online serving. */
    private @Nullable Integer timeToLiveMinutes;
    /** Whether historical values are stored for training. */
    private boolean availableOffline;
    /** Whether the feature is produced for batch inference. */
    private boolean availableBatchServing;
    /** Whether the feature is served under a request deadline. */
    private boolean availableOnlineServing;
    /** The test proving the offline and online paths agree, required when both exist. */
    private @Nullable String parityTestReference;
    /** Whether the served value can be reconstructed from stored sources afterwards. */
    private boolean reproducibleOffline;
    /** Whether the feature may inform a consequential decision. */
    private boolean consequentialUseAllowed;
    /** Tests the owner maintains for this definition. */
    private @Nullable String validationTestReference;
    /** Training-serving skew above which monitoring raises. */
    private @Nullable BigDecimal skewThreshold;
    /** How the values must be handled. */
    private DataPrivacyClass privacyClass;
    /** Whether the feature encodes a protected or highly sensitive trait. */
    private boolean sensitiveAttribute;
    /** The lawful basis for holding it, required when it is sensitive. */
    private @Nullable String sensitiveAttributeBasis;
    /** The review that approved holding it, required when it is sensitive. */
    private @Nullable String sensitiveReviewReference;
    /** The single purpose the data was collected and may be used for. */
    private String purpose;
    /** Consumers explicitly barred from reading it. */
    private @Nullable String prohibitedConsumers;
    /** Whether values may enter a training dataset. */
    private boolean trainingEligible;
    /** How long stored values are kept. */
    private int retentionDays;
    /** What an erasure request does to stored values. */
    private DeletionBehaviour deletionBehaviour;
    /** Team accountable for what the feature means. */
    private String businessOwner;
    /** Team accountable for producing it. */
    private String technicalSteward;
    /** The definition that supersedes this one, required once it is deprecated. */
    private @Nullable UUID replacedById;
    /** The earlier semantic version this one replaces. */
    private @Nullable UUID supersedesId;
    /** Where the definition stands in its lifecycle. */
    private DefinitionStatus status;
    /** UTC instant the definition was reviewed. */
    private @Nullable Instant reviewedAt;
    /** UTC instant the definition entered service. */
    private @Nullable Instant activatedAt;
    /** UTC instant the definition was deprecated. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant the definition was withdrawn. */
    private @Nullable Instant retiredAt;
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
