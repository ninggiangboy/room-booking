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
 * Maker-checker, bound to a digest rather than to a row.
 *
 * <p>An approval records the exact decision digest the approver saw; if the decision is re-derived the
 * digest moves and the approval no longer matches anything. Append-only, because an approval that can
 * be edited is a signature that can be moved onto a different document.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_decision_approvals")
public class CaseDecisionApproval {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case decision this row belongs to. */
    private UUID caseDecisionId;
    /** Which approval role this row carries. */
    private DecisionApprovalRole approvalRole;
    /** Approval tier. */
    private DecisionApprovalTier approvalTier;
    /** Who approved it, which is never the maker. */
    private UUID approverAccountHolderId;
    /** Who made the decision; a trigger checks this against the decision itself. */
    private UUID makerAccountHolderId;
    /** The agent skill grant this row belongs to. */
    private @Nullable UUID agentSkillGrantId;
    /** The authority policy version this row belongs to. */
    private UUID authorityPolicyVersionId;
    /** The exact decision digest the approver saw. */
    private String approvedDecisionDigest;
    /** Digest of the approval request as presented. */
    private String requestDigest;
    /** Outcome. */
    private ApprovalOutcome outcome;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String refusalReason;
    /** Reference to the comments, held in its owning system rather than copied here. */
    private @Nullable String commentsReference;
    /** The step-up authentication behind the approval, where required. */
    private @Nullable String stepUpAuthenticationReference;
    /** UTC instant decided. */
    private Instant decidedAt;
    /** UTC instant expires. */
    private @Nullable Instant expiresAt;
    /** UTC instant invalidated. */
    private @Nullable Instant invalidatedAt;
    /** Why the approval no longer stands. */
    private @Nullable ApprovalInvalidationReason invalidationReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
