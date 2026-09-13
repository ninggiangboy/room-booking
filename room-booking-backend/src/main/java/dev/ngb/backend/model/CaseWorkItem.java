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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The unit a person or worker actually claims.
 *
 * <p>Work is separate from case state because a case can be investigating while three people hold three
 * tasks on it, and an expired claim must return the work without changing who owns the case.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_work_items")
public class CaseWorkItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Which work item type this row carries. */
    private WorkItemType workItemType;
    /** The support queue this row belongs to. */
    private UUID supportQueueId;
    /** Required skill. */
    private @Nullable String requiredSkill;
    /** The case severity rank at the time the work was created. */
    private short severityRank;
    /** Priority. */
    private int priority;
    /** What set the priority; a model may order peers but never urgent work. */
    private WorkPriorityBasis priorityBasis;
    /** UTC instant due. */
    private @Nullable Instant dueAt;
    /** Where the state stands. */
    private WorkItemState state;
    /** The owner account holder this row belongs to. */
    private @Nullable UUID ownerAccountHolderId;
    /** How many attempt there are. */
    private int attemptCount;
    /** Maximum attempts. */
    private int maximumAttempts;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String lastFailureReason;
    /** Evidence that a required step was actually done. */
    private @Nullable String completionEvidenceReference;
    /** UTC instant completed. */
    private @Nullable Instant completedAt;
    /** The completed by account holder this row belongs to. */
    private @Nullable UUID completedByAccountHolderId;
    /** The transferred from account holder this row belongs to. */
    private @Nullable UUID transferredFromAccountHolderId;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String transferReason;
    /** The handoff note written when the work changed hands. */
    private @Nullable UUID handoffSummaryNoteId;
    /** The escalated to queue this row belongs to. */
    private @Nullable UUID escalatedToQueueId;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String escalationReason;
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
