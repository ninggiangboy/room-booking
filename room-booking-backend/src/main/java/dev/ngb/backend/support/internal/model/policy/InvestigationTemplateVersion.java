package dev.ngb.backend.support.internal.model.policy;

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
import dev.ngb.backend.platform.PolicyVersionStatus;

import dev.ngb.backend.platform.PolicyVersionStatus;
import dev.ngb.backend.support.internal.model.ApprovalTier;
import dev.ngb.backend.support.internal.model.ProofStandard;
import dev.ngb.backend.support.internal.model.SupportCaseType;


/**
 * What a case type requires before anyone may conclude anything.
 *
 * <p>Required questions, mandatory authoritative facts, permitted evidence and the proof standard are
 * fixed per version, so an agent cannot mark a required step complete without evidence.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("investigation_template_versions")
public class InvestigationTemplateVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the template. */
    private String templateKey;
    /** Which version of the template applies. */
    private int templateVersion;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Which case type this row carries. */
    private SupportCaseType caseType;
    /** Where the status stands. */
    private PolicyVersionStatus status;
    /** Effective from. */
    private @Nullable Instant effectiveFrom;
    /** Effective until. */
    private @Nullable Instant effectiveUntil;
    /** Required questions. */
    private String[] requiredQuestions;
    /** Mandatory fact domains. */
    private String[] mandatoryFactDomains;
    /** Permitted evidence kinds. */
    private String[] permittedEvidenceKinds;
    /** Conflict checks. */
    private String[] conflictChecks;
    /** Permitted outcomes. */
    private String[] permittedOutcomes;
    /** Proof standard. */
    private ProofStandard proofStandard;
    /** How long, in hours, the claimant response window runs. */
    private @Nullable Integer claimantResponseWindowHours;
    /** How long, in hours, the respondent response window runs. */
    private @Nullable Integer respondentResponseWindowHours;
    /** Specialist skill required. */
    private @Nullable String specialistSkillRequired;
    /** Approval tier required. */
    private @Nullable ApprovalTier approvalTierRequired;
    /** Reference to the disclosure rule, held in its owning system rather than copied here. */
    private @Nullable String disclosureRuleReference;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** Digest of the content, so it can be shown later to be unchanged. */
    private String contentHash;
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
