package dev.ngb.backend.model;

import java.math.BigDecimal;
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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One line of a cancellation decision's arithmetic.
 *
 * <p>Each line traces to the booking line it recalculates and says what happened to it: how much was
 * consumed by nights already stayed, how much the policy retained, how much comes back, and which party
 * funds each part.</p>
 *
 * <p>Funding is split out because "the guest got a full refund" and "the host paid for it" are
 * different facts, and every settlement dispute is about the second one. A row-level constraint
 * requires the four funding columns to sum to the refund exactly.</p>
 *
 * <p>Lines of a committed decision cannot be added to, altered or removed, so the row carries no
 * optimistic lock and no update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("cancellation_decision_lines")
public class CancellationDecisionLine {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Decision this line belongs to. */
    private UUID cancellationDecisionId;
    /** Position within the decision, unique inside it. */
    private short lineNumber;
    /** Booking line being recalculated. */
    private @Nullable UUID sourceBookingLineId;
    /** Stable reference the allocation arithmetic uses. */
    private @Nullable String allocationReference;
    /** What the line represents. */
    private DecisionLineCategory lineCategory;
    /** How much of the original line the recalculation touches. */
    private BigDecimal affectedQuantity;
    /** First service date covered. */
    private @Nullable LocalDate serviceFromDate;
    /** Last service date covered. */
    private @Nullable LocalDate serviceToDate;
    /** ISO 4217 code, matching the decision. */
    private String currency;
    /** What the line was worth before the decision. */
    private long originalAmountMinor;
    /** How much of it was cancelled. */
    private long cancelledAmountMinor;
    /** How much was already delivered and cannot come back. */
    private long consumedAmountMinor;
    /** How much the policy keeps. */
    private long retainedAmountMinor;
    /** How much returns to the guest. */
    private long refundAmountMinor;
    /** How much becomes newly owed. Never non-zero beside a refund. */
    private long newDueAmountMinor;
    /** Share of the refund the guest effectively funds. */
    private long guestFundedMinor;
    /** Share the host funds. */
    private long hostFundedMinor;
    /** Share the platform funds. */
    private long platformFundedMinor;
    /** Share a partner funds. */
    private long partnerFundedMinor;
    /** Tax consequence of the line. */
    private long taxEffectMinor;
    /** Promotion consequence of the line. */
    private long promotionEffectMinor;
    /** How the split remainder was disposed of. */
    private MoneyRoundingRule roundingRule;
    /** The remainder that rule disposed of. */
    private long roundingRemainderMinor;
    /** Structured code explaining the outcome to a reader. */
    private String explanationCode;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether every refunded minor unit on this line is attributed to a funder.
     *
     * <p>The database enforces the same rule; this exists so a service can check before writing.</p>
     *
     * @return {@code true} when the funding columns sum to the refund
     */
    public boolean isFullyFunded() {
        return refundAmountMinor
                == guestFundedMinor + hostFundedMinor + platformFundedMinor + partnerFundedMinor;
    }
}
