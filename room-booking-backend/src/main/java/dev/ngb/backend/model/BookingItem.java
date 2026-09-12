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
 * One claimed inventory resource within a booking.
 *
 * <p>A whole-home stay has exactly one item; three interchangeable hotel rooms have three. The
 * indirection earns its keep the moment a guest cancels one room of three: the release names one
 * resource's nights and one resource's money instead of approximating both across the booking.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_items")
public class BookingItem {

    /** Primary key of the item. */
    @Id
    private @Nullable UUID id;
    /** Booking this item belongs to. */
    private UUID bookingId;
    /** Position of the item within its booking, starting at one. */
    private short itemNumber;
    /** Inventory resource whose nights this item consumes. */
    private UUID inventoryResourceId;
    /** Rate plan whose terms apply to this item. */
    private UUID ratePlanId;
    /** Specific room assigned, when the supply identifies rooms individually. */
    private @Nullable UUID physicalUnitId;
    /** Nights this item consumes, half-open. */
    private StayRange stayRange;
    /** How many of a pooled resource this item consumes. */
    private short quantity;
    /** Adults assigned to this item. */
    private short adultCount;
    /** Children assigned to this item. */
    private short childCount;
    /** Infants assigned to this item. */
    private short infantCount;
    /** Whether this item still holds its nights. */
    private BookingItemStatus itemStatus;
    /** ISO 4217 currency of this item's amounts. */
    private String currency;
    /** Accommodation total for this item in minor units. */
    private long accommodationAmountMinor;
    /** What this item contributes to the booking total, in minor units. */
    private long totalAmountMinor;
    /** UTC instant this item was cancelled. */
    private @Nullable Instant cancelledAt;
    /** Stable reason this item was cancelled. */
    private @Nullable String cancellationReasonCode;
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
     * Reports whether this item still consumes inventory.
     *
     * @return {@code true} while the item is active
     */
    public boolean isConsuming() {
        return itemStatus == BookingItemStatus.ACTIVE;
    }
}
