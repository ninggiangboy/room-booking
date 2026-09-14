package dev.ngb.backend.stay.internal.model.access;

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
 * One attempt to make an access provider agree with a grant.
 *
 * <p>Provision, rotate and revoke carry distinct stable keys, so a retry of one can never be mistaken
 * for another. That matters most for revoke, where a lost response must be retried rather than
 * assumed.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("access_operations")
public class AccessOperation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Grant this operation serves. */
    private UUID accessGrantId;
    /** What is being asked of the provider. */
    private AccessOperationType operationType;
    /** Stable key making the ask idempotent at the provider. */
    private String operationKey;
    /** Which attempt this is. */
    private int attemptNumber;
    /** Provider account being called. */
    private @Nullable UUID providerAccountId;
    /** Provider-native identifier for this operation. */
    private @Nullable String providerReference;
    /** Hash of the canonical request. */
    private String requestHash;
    /** How far the call has got. */
    private AccessOperationState state;
    /** Why it failed, in retry-policy terms. */
    private @Nullable AccessFailureCategory failureCategory;
    /** Provider detail, kept for investigation. */
    private @Nullable String failureDetail;
    /** When a worker claimed it. */
    private @Nullable Instant claimedAt;
    /** Which worker holds the lease. */
    private @Nullable String claimedBy;
    /** When that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** When it reached the wire. Null means it never did. */
    private @Nullable Instant submittedAt;
    /** When it reached a terminal state. */
    private @Nullable Instant completedAt;
    /** When it should be tried again. */
    private @Nullable Instant nextRetryAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether the outcome of this call is still open.
     *
     * @return true while pending or unresolved
     */
    public boolean isUnresolved() {
        return state == AccessOperationState.PENDING || state == AccessOperationState.UNKNOWN;
    }

    /**
     * Whether anything left the platform for this operation.
     *
     * @return true once submitted
     */
    public boolean reachedProvider() {
        return submittedAt != null;
    }
}
