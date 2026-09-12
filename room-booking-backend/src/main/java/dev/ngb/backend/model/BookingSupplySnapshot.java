package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * What the guest was shown and the host offered, frozen at booking time.
 *
 * <p>A listing can be renamed, re-photographed, re-addressed, or archived, and a rate plan can be
 * retired. None of that may change what a past booking says was booked, so the presentation is
 * copied here rather than resolved by joining to rows that have since moved on.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_supply_snapshots")
public class BookingSupplySnapshot {

    /** Primary key of the snapshot. */
    @Id
    private @Nullable UUID id;
    /** Booking the snapshot belongs to; exactly one per booking. */
    private UUID bookingId;
    /** Listing title as shown when the guest booked. */
    private String listingTitle;
    /** Property name as it then stood. */
    private @Nullable String propertyName;
    /** Room type sold. */
    private String roomType;
    /** How the space was shared. */
    private String spaceSharing;
    /** Rate plan name as shown. */
    private String ratePlanName;
    /** Meal plan included. */
    private String mealPlan;
    /** Street address as disclosed to the guest. */
    private @Nullable String addressLine;
    /** Locality of the property. */
    private @Nullable String locality;
    /** Administrative region of the property. */
    private @Nullable String region;
    /** Postal code of the property. */
    private @Nullable String postalCode;
    /** ISO 3166-1 alpha-2 country of the property. */
    private String countryCode;
    /** Latitude disclosed to the guest; paired with {@link #longitude} or both absent. */
    private @Nullable BigDecimal latitude;
    /** Longitude disclosed to the guest. */
    private @Nullable BigDecimal longitude;
    /** IANA zone the stay's civil times are expressed in. */
    private String timeZone;
    /** Locale the guest was shown this content in. */
    private String displayLocale;
    /** Remaining display detail, kept as an immutable snapshot rather than as queryable data. */
    private @Nullable JsonDocument displayPayload;
    /** UTC instant the snapshot was taken, supplied by the caller's decision clock. */
    private Instant capturedAt;
}
