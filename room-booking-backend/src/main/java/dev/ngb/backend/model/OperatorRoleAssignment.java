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
 * One operator holding one role, for a bounded time, because somebody else agreed.
 * <p>The administrative record of the authority; the capability grant it points at is the
 * enforcement.
 * Nobody approves their own, nobody recertifies their own, and an assignment that would give one
 * person both sides of a declared conflict is refused unless the conflict itself said an exception
 * was possible.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operator_role_assignments")
public class OperatorRoleAssignment {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The role version being granted, which fixes what the assignment means. */
    private UUID roleDefinitionId;
    /** The role key, denormalized because the live-assignment index is enforced on it. */
    private String roleKey;
    /** The account holder receiving the authority. */
    private UUID operatorId;
    /** ISO 3166-1 alpha-2 market the authority is limited to, for a market-scoped role. */
    private @Nullable String marketCode;
    /** Why this operator needs this role, in the words the approver read. */
    private String justification;
    /** The account holder who raised the request. */
    private UUID requestedBy;
    /** UTC instant the assignment was requested. */
    private Instant requestedAt;
    /** The account holder who approved it, never the operator and never the requester. */
    private UUID approvedBy;
    /** UTC instant it was approved. */
    private Instant approvedAt;
    /** Reference to the completed training, required when the role demands it. */
    private @Nullable String trainingAttestation;
    /** The declared conflict this assignment is an approved exception to. */
    private @Nullable UUID conflictExceptionId;
    /** The capability grant that actually opens the door, held in migration 014. */
    private @Nullable UUID capabilityGrantId;
    /** UTC instant the authority starts. */
    private Instant effectiveFrom;
    /** UTC instant the authority ends; every assignment has one. */
    private Instant effectiveUntil;
    /** UTC instant somebody other than the holder must re-examine the assignment. */
    private Instant recertificationDueAt;
    /** UTC instant it was last re-examined. */
    private @Nullable Instant recertifiedAt;
    /** The account holder who re-examined it, never the holder. */
    private @Nullable UUID recertifiedBy;
    /** Whether the authority is live, lapsed or withdrawn. */
    private RoleAssignmentState assignmentState;
    /** UTC instant the authority was withdrawn. */
    private @Nullable Instant revokedAt;
    /** The account holder who withdrew it. */
    private @Nullable UUID revokedBy;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String revocationReason;
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
