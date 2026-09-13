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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The discovery serving registry: what is live, what is only shadowing, and what it falls back to.
 *
 * <p>The artifact checksum and feature-schema digest are mandatory, because a model whose inputs
 * cannot be identified cannot be reproduced, and a rank nobody can reproduce cannot be defended to a
 * host. A rollback is final: the way back into service is a new version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ranking_model_versions")
public class RankingModelVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the model this is a version of. */
    private String modelKey;
    /** Version label of the model artifact. */
    private String modelVersion;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Where this model version stands in the serving lifecycle. */
    private RankingModelStatus status;
    /** What kind of ranker it is. */
    private RankingModelFamily modelFamily;
    /** Digest of the feature schema it expects, so online and offline definitions can be compared. */
    private String featureSchemaDigest;
    /** Checksum of the model artifact, so what served can be identified later. */
    private String artifactChecksum;
    /** Reference to the artifact, held in its owning system rather than copied here. */
    private @Nullable String artifactReference;
    /** Reference to the training dataset, held in its owning system rather than copied here. */
    private @Nullable String trainingDatasetReference;
    /** Earliest example in the training window. */
    private @Nullable Instant trainingWindowStart;
    /** Latest example in it; training before this closes is label leakage. */
    private @Nullable Instant trainingWindowEnd;
    /** UTC instant training finished. */
    private @Nullable Instant trainedAt;
    /** How raw scores were calibrated into probabilities. */
    private @Nullable CalibrationMethod calibrationMethod;
    /** Reference to the evaluation, held in its owning system rather than copied here. */
    private @Nullable String evaluationReference;
    /** Share of eligible traffic this version serves. */
    private BigDecimal trafficShare;
    /** Milliseconds after which scoring is abandoned for the fallback. */
    private int servingTimeoutMs;
    /** What serves when this model times out or errors. */
    private RankingFallbackMode fallbackMode;
    /** The fallback model version this row belongs to. */
    private @Nullable UUID fallbackModelVersionId;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** UTC instant promoted. */
    private @Nullable Instant promotedAt;
    /** UTC instant it was pulled from service; a rollback is final. */
    private @Nullable Instant rolledBackAt;
    /** Why it was pulled, which cannot later be erased. */
    private @Nullable String rollbackReason;
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
