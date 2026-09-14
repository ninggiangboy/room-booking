package dev.ngb.backend.admin.internal.model.change;

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
 * One role's sign-off on one change.
 * <p>Append-only, never by the person who raised the change, and carrying the digest of the value
 * that
 * was approved so a later edit invalidates it rather than inheriting it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("change_request_approvals")
public class ChangeRequestApproval {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The change being approved. */
    private UUID changeRequestId;
    /** Which of the roles the change requires this sign-off satisfies. */
    private String approvalRole;
    /** Whether the reviewer agreed or refused. */
    private ChangeApprovalDecision decision;
    /** The account holder who decided, never the one who raised the change. */
    private UUID approverId;
    /** UTC instant the decision was recorded. */
    private Instant decidedAt;
    /** Digest of the value that was approved, so a later edit invalidates the approval. */
    private String approvedDigest;
    /** What the reviewer wrote; required when refusing. */
    private @Nullable String comment;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
