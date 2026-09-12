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
 * What was agreed for one night of one booking item.
 *
 * <p>Money is kept per night rather than as a booking total because a shortened stay, a partial
 * refund, and a per-night lodging tax each need to name the exact nights they concern. A total
 * cannot answer "which nights are we refunding".</p>
 *
 * <p>Shortening a stay marks nights released; it never deletes them. The nights were sold, and a
 * record that can be erased cannot settle a later argument about what was.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_nights")
public class BookingNight {

    /** Primary key of the night. */
    @Id
    private @Nullable UUID id;
    /** Booking this night belongs to. */
    private UUID bookingId;
    /** Item whose nights this is one of. */
    private UUID bookingItemId;
    /** The night itself, a civil date in the property's own time zone. */
    private LocalDate stayDate;
    /** Inventory resource consumed on this night. */
    private UUID inventoryResourceId;
    /** How many of a pooled resource this night consumes. */
    private short quantity;
    /** ISO 4217 currency of this night's amounts. */
    private String currency;
    /** Accommodation charge for this night in minor units. */
    private long accommodationAmountMinor;
    /** Discount applied to this night in minor units, stored unsigned. */
    private long discountAmountMinor;
    /** Fees attributed to this night in minor units. */
    private long feeAmountMinor;
    /** Tax attributed to this night in minor units. */
    private long taxAmountMinor;
    /** What this night contributes to the total; forced to reconcile with the parts. */
    private long totalAmountMinor;
    /** Published price rule version this night's price came from. */
    private @Nullable UUID priceRuleVersionId;
    /** Priced component this night was composed from, for explaining the amount later. */
    private @Nullable UUID dailyPriceComponentId;
    /** Whether this night has stopped consuming inventory. */
    private boolean isReleased;
    /** UTC instant it stopped; always present when {@link #isReleased} is set. */
    private @Nullable Instant releasedAt;
    /** Stable reason it stopped. */
    private @Nullable String releaseReason;
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
     * Reports whether this night is still sold.
     *
     * @return {@code true} while the night has not been released
     */
    public boolean isSold() {
        return !isReleased;
    }
}
