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
 * A bounded claim on one work item, held under a monotonic fencing token.
 *
 * <p>The token is what makes a stale worker harmless: it can wake after its lease expired and be refused
 * rather than finalise work somebody else has since redone.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("work_item_leases")
public class WorkItemLease {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The case work item this row belongs to. */
    private UUID caseWorkItemId;
    /** Monotonic token; a worker holding a lower one can no longer act. */
    private long fencingToken;
    /** Which owner kind this row carries. */
    private LeaseOwnerKind ownerKind;
    /** The owner account holder this row belongs to. */
    private @Nullable UUID ownerAccountHolderId;
    /** The worker process holding the lease, when it is not a person. */
    private @Nullable String ownerWorkerReference;
    /** UTC instant acquired. */
    private Instant acquiredAt;
    /** UTC instant expires. */
    private Instant expiresAt;
    /** UTC instant renewed. */
    private @Nullable Instant renewedAt;
    /** How many times the lease has been renewed. */
    private int renewalCount;
    /** UTC instant released. */
    private @Nullable Instant releasedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable LeaseReleaseReason releaseReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
