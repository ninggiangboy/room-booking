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
 * An instruction to hand a booking's nights back to the calendar.
 *
 * <p>It is an instruction rather than a direct write, because inventory is another domain's authority
 * and the release may fail, be retried, or arrive after a sweeper already released the claim.</p>
 *
 * <p>One release per decision per claim, enforced by a unique key. Without it, a retried worker could
 * release a claim a second time after the resource had been re-sold.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("inventory_release_instructions")
public class InventoryReleaseInstruction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Decision that ordered the release. */
    private UUID cancellationDecisionId;
    /** Claim to give back. */
    private UUID inventoryClaimId;
    /** Resource the claim sits on. */
    private UUID inventoryResourceId;
    /** Half-open range of nights to release. */
    private StayRange releaseRange;
    /** Units to release, for pooled supply. */
    private int quantity;
    /** Structured reason the calendar records against the release. */
    private String releaseReason;
    /** Progress of applying it. */
    private InventoryReleaseState state;
    /** When the calendar accepted it. */
    private @Nullable Instant appliedAt;
    /**
     * Fencing token the release acted under. Required once applied, so the outcome can be checked
     * against the claim it says it released.
     */
    private @Nullable Long appliedFencingToken;
    /** Why the last attempt failed. */
    private @Nullable String failureReason;
    /** How many times it has been tried. */
    private int attemptCount;
    /** Key the inventory domain deduplicates on. */
    private String idempotencyKey;
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
     * Whether the release still needs a worker to act on it.
     *
     * @return {@code true} while it is pending or retryable
     */
    public boolean needsWork() {
        return state == InventoryReleaseState.PENDING || state == InventoryReleaseState.FAILED;
    }
}
