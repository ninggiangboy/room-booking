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
 * One unit of human risk work.
 *
 * <p>Leased rather than assigned, so an abandoned claim returns to the queue without anybody deciding
 * it was abandoned. The fencing token only rises: a worker that wakes up late carries an old token
 * and is refused rather than deciding a case somebody else has since taken. An urgent safety task may
 * be ordered only by severity or deadline -- a model may order peers, never demote a tier-four
 * task.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_review_tasks")
public class RiskReviewTask {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The queue holding it. */
    private UUID riskReviewQueueId;
    /** The decision under review, where there is one. */
    private @Nullable UUID riskDecisionId;
    /** Who or what the task is about. */
    private UUID subjectId;
    /** The action being reviewed. */
    private @Nullable String protectedAction;
    /** Tier of that action. */
    private RiskActionTier actionTier;
    /** What is suspected, in policy vocabulary. */
    private String suspectedHarm;
    /** What decides its place in the queue. */
    private ReviewPriorityBasis priorityBasis;
    /** Score ordering comparable tasks, where a model assists. */
    private @Nullable BigDecimal priorityScore;
    /** Money at stake, in minor units. */
    private @Nullable Long exposureAmountMinor;
    /** Currency of that exposure. */
    private @Nullable String exposureCurrency;
    /** The minimized packet a reviewer reads. */
    private @Nullable String casePacketReference;
    /** What the reviewer may not reveal. */
    private String[] disclosureConstraints;
    /** Where the task stands. */
    private ReviewTaskState state;
    /** When it must be answered by. */
    private Instant deadlineAt;
    /** Worker currently holding it. */
    private @Nullable String leaseOwner;
    /** When that hold lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** Monotonic claim token; a lower one is a stale claim. */
    private long leaseFencingToken;
    /** The reviewer working it. */
    private @Nullable UUID assignedReviewerAccountHolderId;
    /** Where it was escalated to. */
    private @Nullable UUID escalatedToQueueId;
    /** When it was closed. */
    private @Nullable Instant closedAt;
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
