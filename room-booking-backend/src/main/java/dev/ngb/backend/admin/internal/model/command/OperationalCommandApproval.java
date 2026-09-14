package dev.ngb.backend.admin.internal.model.command;

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

import dev.ngb.backend.admin.internal.model.ChangeApprovalDecision;


/**
 * One role's sign-off on one operational command.
 * <p>Append-only, never by the operator issuing it, and carrying both the parameter digest and, for
 * a
 * monetary command, the largest amount the approval covers.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operational_command_approvals")
public class OperationalCommandApproval {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The execution being approved. */
    private UUID commandExecutionId;
    /** Which of the roles the command requires this sign-off satisfies. */
    private String approvalRole;
    /** Whether the approver agreed or refused. */
    private ChangeApprovalDecision decision;
    /** The account holder who decided, never the operator issuing the command. */
    private UUID approverId;
    /** UTC instant the decision was recorded. */
    private Instant decidedAt;
    /** Digest of the parameters that were approved, so a later edit invalidates the approval. */
    private String approvedDigest;
    /** Largest amount this approval covers, in integer minor units of its currency. */
    private @Nullable Long approvedAmountMinor;
    /** What the approver wrote; required when refusing. */
    private @Nullable String comment;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
