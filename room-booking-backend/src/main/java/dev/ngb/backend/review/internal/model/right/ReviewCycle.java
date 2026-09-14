package dev.ngb.backend.review.internal.model.right;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * The disclosure coordinator for one completed booking.
 *
 * <p>It owns neither review's content. It owns when either may become visible, which is why its
 * state is monotonic and its reveal epoch is issued exactly once under a lease -- both enforced by
 * trigger. Two workers cannot produce two reveals, and nothing may publish before this row says it
 * revealed.</p>
 *
 * <p>Host attribution is snapshotted rather than inferred later from current ownership: a listing
 * that changes hands must not silently reattribute what a guest said about somebody else.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_cycles")
public class ReviewCycle {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Booking that completed. */
    private UUID bookingId;
    /** Accepted revision the completion was judged on. */
    private UUID bookingRevisionId;
    /** Listing stayed at. */
    private UUID listingId;
    /** Property stayed at. */
    private UUID propertyId;
    /** Guest. */
    private UUID guestAccountHolderId;
    /** Host as attributed at completion time. */
    private UUID hostAccountHolderId;
    /** Market whose rules apply. */
    private String marketCode;
    /** IANA zone the local deadline is expressed in. */
    private String listingTimeZone;
    /** Completion event this cycle was opened from. */
    private UUID sourceEventId;
    /** Booking version that event reported. */
    private @Nullable Integer sourceAggregateVersion;
    /** When the stay completed. */
    private Instant completedAt;
    /** Policy version that set the window. */
    private UUID reviewPolicyVersionId;
    /** When submission opens. */
    private Instant opensAt;
    /** When submission closes. Snapshotted and never moved. */
    private Instant submissionDeadlineAt;
    /** Civil date of that deadline. */
    private LocalDate localDeadlineDate;
    /** Civil time of that deadline. */
    private LocalTime localDeadlineTime;
    /** How far disclosure has progressed. */
    private ReviewCycleState state;
    /** Orderable form of the state; the monotonicity guard reads this. */
    private short stateRank;
    /** Reveal epoch, issued once. */
    private @Nullable Integer revealVersion;
    /** When the reveal happened. */
    private @Nullable Instant revealedAt;
    /** Why it revealed when it did. */
    private @Nullable RevealReason revealReason;
    /** When one side may no longer be held for the other. */
    private @Nullable Instant maximumHoldExpiresAt;
    /** When a worker claimed it. */
    private @Nullable Instant claimedAt;
    /** Which worker holds the lease. */
    private @Nullable String claimedBy;
    /** When that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** Monotonic token a late worker is fenced against. */
    private long fencingToken;
    /** Why a correction produced a replacement cycle. */
    private @Nullable String correctionReason;
    /** Cycle that replaced this one. */
    private @Nullable UUID supersededByCycleId;
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
     * Whether anything may publish from this cycle yet.
     *
     * @return true once the reveal has been recorded
     */
    public boolean hasRevealed() {
        return revealedAt != null;
    }

    /**
     * Whether the submission window is still open at the given instant.
     *
     * @param at instant to test
     * @return true between the opening instant and the snapshotted deadline
     */
    public boolean acceptsSubmissionsAt(Instant at) {
        return !at.isBefore(opensAt) && at.isBefore(submissionDeadlineAt);
    }

    /**
     * Whether the deadline has passed and the cycle has not acted on it.
     *
     * @param at instant to treat as now
     * @return true when a reveal or an empty close is due
     */
    public boolean isRevealDue(Instant at) {
        return !hasRevealed()
                && (state == ReviewCycleState.OPEN || state == ReviewCycleState.ONE_SIDED_SEALED)
                && !at.isBefore(submissionDeadlineAt);
    }
}
