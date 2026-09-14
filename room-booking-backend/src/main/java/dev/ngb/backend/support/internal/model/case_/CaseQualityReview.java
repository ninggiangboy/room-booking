package dev.ngb.backend.support.internal.model.case_;

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
 * A sampled review of one decision, stratified rather than random-only.
 *
 * <p>The dimensions are assessed separately because a single score cannot distinguish an agent who was
 * slow from one who cited no evidence, and only one of those is a quality problem worth coaching.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_quality_reviews")
public class CaseQualityReview {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The case decision this row belongs to. */
    private @Nullable UUID caseDecisionId;
    /** Which stratum the case was drawn from, so sampling is not random-only. */
    private QualitySampleCohort sampleCohort;
    /** Sample stratum. */
    private String sampleStratum;
    /** Reference to the sampling policy, held in its owning system rather than copied here. */
    private String samplingPolicyReference;
    /** The reviewer account holder this row belongs to. */
    private UUID reviewerAccountHolderId;
    /** Whose work was reviewed, which is never the reviewer. */
    private @Nullable UUID reviewedAccountHolderId;
    /** UTC instant reviewed. */
    private Instant reviewedAt;
    /** How the evidence citation dimension scored. */
    private QualityRating evidenceCitationRating;
    /** How the classification dimension scored. */
    private QualityRating classificationRating;
    /** How the policy selection dimension scored. */
    private QualityRating policySelectionRating;
    /** How the amount accuracy dimension scored. */
    private QualityRating amountAccuracyRating;
    /** How the authorization dimension scored. */
    private QualityRating authorizationRating;
    /** How the communication dimension scored. */
    private QualityRating communicationRating;
    /** How the timeliness dimension scored. */
    private QualityRating timelinessRating;
    /** How the privacy dimension scored. */
    private QualityRating privacyRating;
    /** How the downstream completion dimension scored. */
    private QualityRating downstreamCompletionRating;
    /** Overall outcome. */
    private QualityReviewOutcome overallOutcome;
    /** Reference to the findings, held in its owning system rather than copied here. */
    private @Nullable String findingsReference;
    /** What the review requires be done about it. */
    private @Nullable QualityCorrectionKind correctionKind;
    /** Reference to the correction, held in its owning system rather than copied here. */
    private @Nullable String correctionReference;
    /** UTC instant correction due. */
    private @Nullable Instant correctionDueAt;
    /** Whether the reviewed party contests the finding. */
    private boolean disputedByReviewedParty;
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
