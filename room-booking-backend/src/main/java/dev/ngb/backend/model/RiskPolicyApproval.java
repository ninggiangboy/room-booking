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
 * One approver's answer on a policy version.
 *
 * <p>Maker-checker as rows. The author may not approve their own policy, and counting the approvals
 * is a database question at activation rather than a screen a deploy pipeline can be told to skip.
 * Append-only.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_policy_approvals")
public class RiskPolicyApproval {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The policy version being approved. */
    private UUID riskPolicyId;
    /** Who approved it; never the author. */
    private UUID approverAccountHolderId;
    /** Which authority they signed under. */
    private PolicyApprovalRole approvalRole;
    /** What they said. */
    private PolicyApprovalDecision decision;
    /** Why; required for a rejection. */
    private @Nullable String rationale;
    /** The authentication strength the approval was given under. */
    private AssuranceLevel stepUpAssuranceLevel;
    /** When they decided. */
    private Instant decidedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
