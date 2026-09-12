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
 * An offer: everything a guest was told about the price of a trip at one instant, frozen.
 *
 * <p>Written once and never re-priced in place. If anything that fed it changes — a rule, a tax
 * version, availability, the party size — the answer is a new quote, because an offer that can change
 * between being shown and being accepted is not an offer the guest agreed to. The old quote is marked
 * {@link QuoteStatus#SUPERSEDED} and points at its replacement.</p>
 *
 * <p>{@link #idempotencyKey} is what makes a retried pricing request return the same offer instead of
 * minting a second one; a guest who double-taps must not see two different prices for one trip.</p>
 *
 * <p>The summary amounts are unsigned with fixed roles and must reconcile:
 * {@code ck_quotes_total} refuses a quote that would charge one number and explain another.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("quotes")
public class Quote {

    /** Primary key of the quote. */
    @Id
    private @Nullable UUID id;
    /** Opaque identifier safe to show a guest and quote back in support. */
    private String publicId;
    /** Key that makes a retried pricing request return this same offer. */
    private String idempotencyKey;
    /** Guest the offer was made to, when they are signed in. */
    private @Nullable UUID guestAccountHolderId;
    /** Anonymous unit it was made to instead, when they are not. */
    private @Nullable String anonymousUnitKey;
    /** Listing the guest was looking at. */
    private UUID listingId;
    /** Accommodation type actually being sold. */
    private UUID accommodationTypeId;
    /** Offer terms the price was computed under. */
    private UUID ratePlanId;
    /** Market whose rules and currency govern the offer. */
    private String marketCode;
    /** Hold keeping the nights reservable while the guest decides. */
    private @Nullable UUID inventoryHoldId;
    /** Nights quoted, half-open so checkout is the next guest's check-in. */
    private StayRange stayRange;
    /** Adults in the party. */
    private short adultCount;
    /** Children in the party. */
    private short childCount;
    /** Infants in the party, who usually do not count towards occupancy. */
    private short infantCount;
    /** How many units of a pooled type are being bought. */
    private short unitQuantity;
    /** ISO 4217 currency of every amount here. */
    private String currency;
    /** Nightly rates before anything else, in minor units. */
    private long accommodationAmountMinor;
    /** Total reductions, in minor units; subtracted because it is a discount, not because of a sign. */
    private long discountAmountMinor;
    /** Total fees, in minor units. */
    private long feeAmountMinor;
    /** Total tax, in minor units. */
    private long taxAmountMinor;
    /** What the guest pays, in minor units; always the components combined. */
    private long totalAmountMinor;
    /** What the host would expect to receive, in minor units; an estimate, not a promise. */
    private @Nullable Long hostPayoutEstimateMinor;
    /** Version of the resolved pricing policy set that produced the amounts. */
    private @Nullable String pricingPolicyVersion;
    /** Version of the tax content applied. */
    private @Nullable String taxContentVersion;
    /** Version of the terms the guest was shown. */
    private @Nullable String termsVersion;
    /** Lowercase hex SHA-256 of the calculation inputs, so the result can be reproduced. */
    private String calculationHash;
    /** Whether the offer still stands. */
    private QuoteStatus status;
    /** UTC instant the guest took it up. */
    private @Nullable Instant acceptedAt;
    /** Quote that replaced this one; paired with a superseded status. */
    private @Nullable UUID supersededBy;
    /** UTC instant the offer lapses. */
    private Instant expiresAt;
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
     * Reports whether this offer can still be accepted at an instant.
     *
     * <p>Expiry is a fact about time rather than a write, so an offer can be past its deadline while
     * still stored as {@link QuoteStatus#OPEN} until a sweep transitions it. Equality on the deadline
     * means expired, matching the platform's deadline convention.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the quote is open and the instant is before its expiry
     */
    public boolean isAcceptableAt(Instant instant) {
        return status == QuoteStatus.OPEN && expiresAt.isAfter(instant);
    }

    /**
     * Returns the number of nights quoted.
     *
     * @return nights between check-in and checkout
     */
    public long nights() {
        return stayRange.nights();
    }
}
