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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One use of a promotion, and who paid for it.
 *
 * <p>Budget is committed when the quote is priced, not when the booking confirms. Every open quote is
 * otherwise an unrecorded commitment, and a campaign measured only on confirmations will overspend by
 * however much is sitting in checkout.</p>
 *
 * <p>The funding split must account for the whole benefit — {@code ck_promotion_redemptions_funding}
 * refuses anything else. A gap means somebody absorbed a cost no ledger will ever attribute; an
 * excess means the discount was funded twice.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("promotion_redemptions")
public class PromotionRedemption {

    /** Primary key of the redemption. */
    @Id
    private @Nullable UUID id;
    /** Terms under which the benefit was granted. */
    private UUID promotionVersionId;
    /** Quote the benefit was applied to. */
    private @Nullable UUID quoteId;
    /** Booking it ended up on, once one exists. */
    private @Nullable UUID bookingId;
    /** Guest who received it, when they are signed in. */
    private @Nullable UUID guestAccountHolderId;
    /** Anonymous unit that received it instead, when they are not. */
    private @Nullable String anonymousUnitKey;
    /** ISO 4217 currency of every amount here. */
    private String currency;
    /** Total value of the benefit, in minor units. */
    private long benefitAmountMinor;
    /** Share the host bears, in minor units. */
    private long hostFundedAmountMinor;
    /** Share the platform bears, in minor units; the two shares sum to the benefit. */
    private long platformFundedAmountMinor;
    /** Where this use stands. */
    private PromotionRedemptionState state;
    /** UTC instant the budget was committed. */
    private Instant reservedAt;
    /** UTC instant the benefit was actually granted. */
    private @Nullable Instant redeemedAt;
    /** UTC instant it was returned to the budget unused. */
    private @Nullable Instant releasedAt;
    /** UTC instant it was undone after the fact. */
    private @Nullable Instant reversedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this redemption still holds budget.
     *
     * @return {@code true} while it is reserved or redeemed
     */
    public boolean isCommitted() {
        return state == PromotionRedemptionState.RESERVED
                || state == PromotionRedemptionState.REDEEMED;
    }
}
