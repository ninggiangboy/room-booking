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
 * Exactly which payable allocation, and how much of it, one payout consists of.
 *
 * <p>A partial unique index lets at most one live item hold any allocation. That is the real defence
 * against two concurrent payout planners selecting the same money, whatever the application does
 * with its locks: the second insert fails rather than the second payout succeeding.</p>
 *
 * <p>Releasing an item frees the allocation for a later payout. A returned item does not, because the
 * money already left and comes back through a recovery instead.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payout_items")
public class PayoutItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Payout the item belongs to. */
    private UUID payoutInstructionId;
    /** Allocation being consumed. */
    private UUID payableAllocationId;
    /** Journal posting behind that allocation. */
    private @Nullable UUID sourcePostingId;
    /** Positive minor units taken from the allocation. */
    private long selectedAmountMinor;
    /** ISO 4217 code, matching the instruction. */
    private String currency;
    /** Whether the item still holds its allocation. */
    private PayoutItemState state;
    /** Reserve this item feeds, when it does. */
    private @Nullable UUID hostReserveId;
    /** Recovery this item is offset against, when it is. */
    private @Nullable UUID hostRecoveryId;
    /** UTC instant it was given back to the balance. */
    private @Nullable Instant releasedAt;
    /** UTC instant it was paid or returned. */
    private @Nullable Instant settledAt;
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
     * Whether this item currently prevents its allocation being selected again.
     *
     * @return true while the item is reserved, settled, or returned
     */
    public boolean isHoldingAllocation() {
        return state != PayoutItemState.RELEASED;
    }
}
