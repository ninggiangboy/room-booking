package dev.ngb.backend.trust.internal.model.review;

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
import dev.ngb.backend.platform.AssuranceLevel;

/**
 * What a reviewer did about a task.
 *
 * <p>Append-only, and a reviewer never edits an automated decision: overturning one requires producing
 * a new decision. The subject is carried on the row so self-review is a constraint rather than a check
 * somebody remembers to run, and a trigger validates that carried value against the task so it cannot
 * be defeated by naming somebody else. Only the current lease holder may act, and only one terminal
 * action per task is possible.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_review_actions")
public class RiskReviewAction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The task acted on. */
    private UUID riskReviewTaskId;
    /** Position in the task's history; unique within it. */
    private int actionSequence;
    /** Who acted. */
    private UUID reviewerAccountHolderId;
    /** The account the task concerns; validated against the task. */
    private @Nullable UUID subjectAccountHolderId;
    /** Under what authority they acted. */
    private ReviewAuthoritySource authoritySource;
    /** What they did. */
    private ReviewActionType actionType;
    /** Whether this closes the task; at most one per task. */
    private boolean terminal;
    /** Stable internal reason. */
    private String reasonCode;
    /** Internal notes, kept apart from user-facing explanation. */
    private @Nullable String reviewerNotesReference;
    /** What they relied on. */
    private String[] evidenceReferences;
    /** Policy version they applied. */
    private @Nullable UUID riskPolicyId;
    /** The decision produced; required to overturn. */
    private @Nullable UUID resultingDecisionId;
    /** The domain command issued as a result. */
    private @Nullable String domainCommandReference;
    /** The widest scope their authority allowed. */
    private String maxPermittedScope;
    /** The authentication strength the action was taken under. */
    private AssuranceLevel stepUpAssuranceLevel;
    /** Whether maker-checker applies to this action. */
    private boolean requiresSecondApproval;
    /** The second approver; never the same person. */
    private @Nullable UUID secondApproverAccountHolderId;
    /** The claim token they held; must be the task's current one. */
    private long leaseFencingToken;
    /** When they acted. */
    private Instant actedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
