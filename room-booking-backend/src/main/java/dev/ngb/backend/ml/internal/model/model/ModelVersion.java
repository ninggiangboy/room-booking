package dev.ngb.backend.ml.internal.model.model;

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
 * One registered model version: its artifact, its inputs, its authority and its lifecycle.
 *
 * <p>Immutable from the moment it leaves DRAFT. Retuning weights or changing a prompt is a new
 * version, and so is widening {@code intendedDecisions} -- that would expand the model's authority
 * without any of the approvals that authority required, while every approval already recorded would
 * appear to cover the new scope.</p>
 *
 * <p>The impact class decides how many independent functions must approve the version before it can
 * be routed traffic, and it is declared at registration rather than argued about at promotion. A
 * model whose impact is safety, money, price, eligibility or moderation also may not send its
 * inputs to a provider permitted to train on them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("model_versions")
public class ModelVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the model across its versions. */
    private String modelKey;
    /** This version's identifier, unique within the model key. */
    private String modelVersion;
    /** What kind of model this is, and therefore how it is evaluated. */
    private ModelFamily modelFamily;
    /** The outcome it predicts. */
    private String targetName;
    /** How far ahead it predicts, absent for models with no horizon. */
    private @Nullable Duration horizon;
    /** How consequential its advice is, and therefore who must approve it. */
    private ModelImpactClass impactClass;
    /** The decisions this version is approved to inform. */
    private String intendedDecisions;
    /** What it may explicitly not be used for. */
    private String prohibitedUses;
    /** The dataset build it was trained on. */
    private UUID trainingDatasetManifestId;
    /** The exact input contract it reads; a prediction from any other is refused. */
    private UUID featureSetVersionId;
    /** The target definition its labels came from. */
    private UUID labelDefinitionId;
    /** Where the model artifact is stored. */
    private String artifactReference;
    /** Checksum of the artifact; changing the bytes means a new version. */
    private String artifactChecksum;
    /** Which version of the training and serving code produced it. */
    private String codeVersion;
    /** Digest of the environment and dependency versions used. */
    private String environmentDigest;
    /** The seed, so the training run can be reproduced. */
    private @Nullable Long randomSeed;
    /** The card stating purpose, population, limitations and excluded uses. */
    private @Nullable String modelCardReference;
    /** The declared request and response contract. */
    private String inferenceContractReference;
    /** Digest of the output schema consumers validate against. */
    private String outputSchemaDigest;
    /** The input validation the runtime applies before execution. */
    private String inputValidationReference;
    /** How long inference may take before the consumer falls back. */
    private int latencyBudgetMs;
    /** Cost ceiling per call, in millionths of a currency unit. */
    private @Nullable Long costBudgetMicros;
    /** What the consumer does when this model cannot answer. */
    private ModelFallbackBehaviour fallbackBehaviour;
    /** Where the monitoring thresholds for this version are defined. */
    private @Nullable String monitoringReference;
    /** What would require this version to be retrained. */
    private @Nullable String retrainingTrigger;
    /** What would require this version to be withdrawn. */
    private @Nullable String retirementTrigger;
    /** The earlier version routing falls back to. */
    private @Nullable UUID rollbackTargetId;
    /** Team accountable for the version, which cannot count towards its approvals. */
    private String ownerReference;
    /** The hosted provider, when the model is not trained and run here. */
    private @Nullable String externalProvider;
    /** The provider licence, required when there is a provider. */
    private @Nullable String providerLicenceReference;
    /** Whether the provider may train on the inputs it is sent. */
    private @Nullable ProviderTrainingRights providerTrainingRights;
    /** How long the provider retains what it is sent. */
    private @Nullable Integer providerRetentionDays;
    /** Where the provider processes the data. */
    private @Nullable String providerResidency;
    /** Where the version stands in its lifecycle. */
    private ModelVersionStatus status;
    /** UTC instant training finished. */
    private @Nullable Instant trainedAt;
    /** UTC instant the version passed validation. */
    private @Nullable Instant validatedAt;
    /** UTC instant the required approvals were complete. */
    private @Nullable Instant approvedAt;
    /** UTC instant the version became the champion of a route. */
    private @Nullable Instant activatedAt;
    /** UTC instant the version was withdrawn. */
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
