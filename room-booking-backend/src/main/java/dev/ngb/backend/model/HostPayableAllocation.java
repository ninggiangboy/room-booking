package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * What a host is owed, as a thing that can be reserved, consumed, and recovered against.
 *
 * <p>Owning the money and being able to receive it are separate questions. {@link #ownershipState}
 * says whose it is; {@link #releaseState} says whether it may enter a payout. They move
 * independently, because collapsing them into one status is how an amount silently stops being the
 * host's the moment a hold is placed on it.</p>
 *
 * <p>The database enforces the ceiling directly: reserved plus consumed plus recovered can never
 * exceed the original, and {@link #remainingAmountMinor} can never disagree with the parts it is
 * derived from. If the service holding the lock is ever wrong, the row still refuses.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_payable_allocations")
public class HostPayableAllocation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Book the entitlement was recognised in. */
    private UUID accountingBookId;
    /** Entity that owes it. */
    private UUID legalEntityId;
    /** Host it is owed to. */
    private UUID hostAccountHolderId;
    /** Journal posting that created the liability; unique, so one entry cannot make two entitlements. */
    private UUID sourcePostingId;
    /** Booking it came from. */
    private @Nullable UUID bookingId;
    /** Exact contractual line it came from. */
    private @Nullable UUID bookingLineItemId;
    /** ISO 4217 code. */
    private String currency;
    /** Minor units the posting created. */
    private long originalAmountMinor;
    /** Minor units claimed by a live payout instruction. */
    private long reservedAmountMinor;
    /** Minor units actually paid out. */
    private long consumedAmountMinor;
    /** Minor units taken back to fund a recovery. */
    private long recoveredAmountMinor;
    /** Original less consumed less recovered, stored so the planner can index on it. */
    private long remainingAmountMinor;
    /** Whose money it is. */
    private PayableOwnershipState ownershipState;
    /** Whether it may enter a payout. */
    private PayableReleaseState releaseState;
    /** Release policy version that scheduled it. */
    private @Nullable UUID releasePolicyVersionId;
    /** What the schedule keys off, such as checkout plus a delay. */
    private @Nullable String releaseTriggerType;
    /** UTC instant it becomes eligible. */
    private @Nullable Instant scheduledReleaseAt;
    /** IANA zone that instant was computed in; a civil cutoff read in the wrong zone pays a host a day early. */
    private @Nullable String releaseTimezone;
    /** UTC instant it actually became available; survives a later hold. */
    private @Nullable Instant releasedAt;
    /** First day of the stay the entitlement covers. */
    private @Nullable LocalDate servicePeriodStart;
    /** Last day of that stay. */
    private @Nullable LocalDate servicePeriodEnd;
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
     * Minor units that could still be selected into a payout right now.
     *
     * <p>Remaining less what a live instruction already holds. Derived on read; the ceiling that
     * keeps the parts honest lives on the row as a check constraint.</p>
     *
     * @return unreserved remaining minor units, never negative
     */
    public long selectableMinor() {
        return Math.max(0L, remainingAmountMinor - reservedAmountMinor);
    }

    /**
     * Whether a payout planner may consider this allocation at a given instant.
     *
     * <p>Holds, reserves, recoveries, destination checks, and policy thresholds are evaluated
     * separately against their own rows. This answers only the questions the allocation itself can.</p>
     *
     * @param at instant the planner is deciding at
     * @return true when it is payable, matured, and has something left
     */
    public boolean isSelectableAt(Instant at) {
        return ownershipState == PayableOwnershipState.PAYABLE
                && (releaseState == PayableReleaseState.AVAILABLE
                    || (releaseState == PayableReleaseState.SCHEDULED
                        && scheduledReleaseAt != null && !scheduledReleaseAt.isAfter(at)))
                && selectableMinor() > 0;
    }
}
