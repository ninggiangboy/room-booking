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
 * The number a guest was shown before deciding whether to cancel.
 *
 * <p>It exists as a row rather than a response body, because the decision must be able to prove that it
 * settled the figure the guest accepted -- not a recalculation that ran a minute later at a different
 * price.</p>
 *
 * <p>The input hash makes staleness detectable: if the booking, the policy or the clock moved between
 * preview and decision, the recomputed hash differs. The expiry stops an unaccepted preview being
 * redeemed next week. A preview may found at most one decision.</p>
 *
 * <p>Previews are written once and never revised, so the row carries no optimistic lock.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("cancellation_previews")
public class CancellationPreview {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Booking being previewed. */
    private UUID bookingId;
    /** Revision the calculation was made against. */
    private UUID bookingRevisionId;
    /** Terms it was calculated under. */
    private UUID policyVersionId;
    /** Kind of actor that asked. */
    private BookingActorType requestedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID requestedByActorId;
    /** What the preview is calculating. */
    private CancellationActionType actionType;
    /** Structured reason from the policy version's catalogue. */
    private String reasonCode;
    /** The coarse class the funding rules are written against. */
    private CancellationCauseCategory reasonCategory;
    /** Instant the calculation treats as "now". */
    private Instant effectiveAt;
    /**
     * Arrival instant the cutoff was measured back from. Stored because a property that later
     * changes its check-in time must not change what this preview meant.
     */
    private Instant officialCheckInAt;
    /** IANA zone the civil values were read in. */
    private String propertyTimeZone;
    /** Evaluator build that produced the result. */
    private String evaluatorVersion;
    /** Configuration snapshot the evaluator read. */
    private @Nullable String configVersion;
    /** SHA-256 over the canonical inputs, lowercase hex. */
    private String inputHash;
    /** SHA-256 over the result, lowercase hex. */
    private String resultHash;
    /** The full line-by-line result as calculated. */
    private JsonDocument resultDocument;
    /** ISO 4217 code. */
    private String currency;
    /** What the policy keeps, in minor units. */
    private long retainedAmountMinor;
    /** What comes back to the guest. */
    private long refundAmountMinor;
    /** What the guest would owe instead. Never non-zero beside a refund. */
    private long newDueAmountMinor;
    /** Whether the figure is still usable. */
    private CancellationPreviewStatus status;
    /** When it stops being redeemable. */
    private Instant expiresAt;
    /** When a decision was committed against it. */
    private @Nullable Instant consumedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether the figure may still found a decision.
     *
     * @param at instant to judge against
     * @return {@code true} when the preview is active and has not expired
     */
    public boolean isRedeemableAt(Instant at) {
        return status == CancellationPreviewStatus.ACTIVE && at.isBefore(expiresAt);
    }
}
