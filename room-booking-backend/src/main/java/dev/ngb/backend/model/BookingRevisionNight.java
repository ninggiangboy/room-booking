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
import org.springframework.data.relational.core.mapping.Table;

/**
 * The nightly shape of one booking revision.
 *
 * <p>Kept per revision rather than per booking, because shortening a stay must leave the original
 * nights readable rather than delete them. A later question about what the guest paid for still has an
 * answer.</p>
 *
 * <p>Nights of a committed revision cannot be added to, altered or removed, so the row carries no
 * optimistic lock and no update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_revision_nights")
public class BookingRevisionNight {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Revision this night belongs to. */
    private UUID bookingRevisionId;
    /** The night itself, named by the date it starts on. */
    private LocalDate stayDate;
    /** Supply sold for this night. */
    private UUID listingId;
    /** The specific unit, where the supply names units. */
    private @Nullable UUID physicalUnitId;
    /** Resource whose capacity the night consumes. */
    private UUID inventoryResourceId;
    /** Claim holding it, where one is held. */
    private @Nullable UUID inventoryClaimId;
    /** Units consumed on this night. */
    private short unitQuantity;
    /** ISO 4217 code, matching the revision. */
    private String currency;
    /** Positive minor units for this night. */
    private long nightlyAmountMinor;
    /** Price version the amount came from. */
    private @Nullable Integer priceVersion;
    /** Price component that explains the amount. */
    private @Nullable UUID dailyPriceComponentId;
    /** Stable reference the allocation arithmetic uses. */
    private @Nullable String allocationReference;
    /** What became of the inventory behind the night. */
    private NightConsumptionState consumptionState;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether the night is still being held for the guest.
     *
     * @return {@code true} when the claim has not been given back or moved
     */
    public boolean isHeld() {
        return consumptionState == NightConsumptionState.CONSUMED;
    }
}
