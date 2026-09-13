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
 * Ranking weights, caps, floors, and budgets as an approved, versioned row.
 *
 * <p>A ranking change nobody can date, attribute, or roll back is not a tuning decision but an
 * incident with a delayed fuse. Once a version leaves draft its numbers are frozen; a change is a
 * new version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ranking_policy_versions")
public class RankingPolicyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the policy this is a version of. */
    private String policyKey;
    /** Which version of the policy applies. */
    private int policyVersion;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Where this policy version stands. */
    private RankingPolicyStatus status;
    /** The deterministic baseline weights, as an immutable snapshot. */
    private String baselineWeights;
    /** How much of the final score personalization may contribute, as a fraction. */
    private BigDecimal personalizedWeight;
    /** Profile confidence below which personalization contributes nothing. */
    private BigDecimal personalizationConfidenceFloor;
    /** How strongly similarity to already-selected results is penalized. */
    private BigDecimal diversityPenalty;
    /** Longest run of consecutive results permitted from one host. */
    private @Nullable Short maxSameHostRun;
    /** Most candidates that may be enriched and scored for one search. */
    private int candidateCap;
    /** Milliseconds the whole search may take. */
    private int latencyBudgetMs;
    /** Milliseconds optional personalization enrichment may take before it is abandoned. */
    private int enrichmentDeadlineMs;
    /** Share of impressions that may be spent on exploration. */
    private BigDecimal explorationTrafficShare;
    /** Quality a listing must reach before exploration may show it. */
    private BigDecimal explorationQualityFloor;
    /** Confidence below which no explanation is shown. */
    private BigDecimal explanationMinConfidence;
    /** Digest of the content, so it can be shown later to be unchanged. */
    private String contentDigest;
    /** UTC instant the version takes effect. */
    private @Nullable Instant effectiveFrom;
    /** UTC instant it stops applying. */
    private @Nullable Instant effectiveUntil;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** The supersedes policy version this row belongs to. */
    private @Nullable UUID supersedesPolicyVersionId;
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
