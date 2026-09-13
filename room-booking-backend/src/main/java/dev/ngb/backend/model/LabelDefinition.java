package dev.ngb.backend.model;

import java.time.Duration;
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

/**
 * Registered definition of one prediction target, at one semantic version.
 *
 * <p>A label definition states the horizon, the maturity delay, and what counts as positive,
 * negative, unresolved, censored and excluded -- separately, because collapsing them is how "not
 * reviewed" enters training as "safe". It also records the biases it is known to carry, and whether
 * outcomes under it are only visible for cases some earlier system selected; when they are, every
 * observation must name that selecting policy.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("label_definitions")
public class LabelDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the target, carrying its meaning and its version. */
    private String labelKey;
    /** Semantic version of this definition; a changed meaning is a new version. */
    private short semanticVersion;
    /** The outcome being predicted, as consumers and model cards refer to it. */
    private String targetName;
    /** What an example is keyed by. */
    private FeatureEntityKind entityKind;
    /** Request context keys an example additionally varies by. */
    private @Nullable String contextKeys;
    /** The instant at which an example's prediction is deemed to have been made. */
    private String predictionTimeRule;
    /** How long after the prediction instant the outcome may still occur. */
    private Duration horizon;
    /** How long after the horizon closes before the answer can be treated as settled. */
    private Duration maturityDelay;
    /** What counts as the outcome occurring. */
    private String positiveDefinition;
    /** What counts as the outcome not occurring while the example stayed observable. */
    private String negativeDefinition;
    /** What makes an outcome not yet knowable. */
    private String unresolvedDefinition;
    /** What makes an example stop being observable before its horizon closes. */
    private String censoringRule;
    /** What keeps an example out of training entirely. */
    private @Nullable String exclusionRule;
    /** The registered data product the outcome is read from. */
    private UUID sourceDataProductId;
    /** How source events are mapped onto this target. */
    private String sourceEventMapping;
    /** Which version of that mapping applies. */
    private String sourceMappingVersion;
    /** Whether a corrected outcome becomes a new revision or a new label version. */
    private LabelCorrectionBehaviour correctionBehaviour;
    /** What an appeal against the underlying decision does to the label. */
    private LabelAppealBehaviour appealBehaviour;
    /** The selection, survivorship, reporting and measurement biases this target carries. */
    private String knownBiases;
    /** Whether outcomes are only visible for cases an earlier system selected. */
    private boolean selectionBiasPresent;
    /** Whether every observation must name the policy that selected it. */
    private boolean selectionPolicyRequired;
    /** How observations under this target must be handled. */
    private DataPrivacyClass privacyClass;
    /** Whether observations may enter a training dataset. */
    private boolean trainingEligible;
    /** How long observations are kept. */
    private int retentionDays;
    /** What an erasure request does to stored observations. */
    private DeletionBehaviour deletionBehaviour;
    /** Team accountable for the quality of this target. */
    private String qualityOwner;
    /** How disputed or ambiguous outcomes are resolved. */
    private String adjudicationProcess;
    /** Whether numbers under this version can be compared with the last one. */
    private boolean comparableWithPrevious;
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
