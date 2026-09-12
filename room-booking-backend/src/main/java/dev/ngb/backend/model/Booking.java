package dev.ngb.backend.model;

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
 * The contract between a guest and a host for a stay.
 *
 * <p>A booking is not in one state. It is in five at once, and they move independently: the contract
 * may stand while payment is still authorising, end while a refund is failing, and complete while a
 * damage claim is open. {@link #lifecycleState}, {@link #paymentState}, {@link #stayState},
 * {@link #changeState} and {@link #refundState} are therefore separate fields with separate
 * vocabularies rather than one status column, which would otherwise need a value for every
 * intersection.</p>
 *
 * <p>The row also snapshots what was agreed rather than trusting joins to answer later. A listing
 * can be renamed, a rate plan retired, and an address corrected, and none of that may change what a
 * past booking says was booked.</p>
 *
 * <p>Nights, money lines, evidence, and transitions live in their own tables and load through their
 * own repositories; Spring Data JDBC has no lazy loading and this aggregate deliberately carries no
 * navigable associations.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("bookings")
public class Booking {

    /** Primary key of the booking. */
    @Id
    private @Nullable UUID id;
    /** Confirmation code shown to guest and host; never the primary key. */
    private String publicId;
    /** Which revision of the contract this row reflects; a modification raises it. */
    private int revision;

    /** Account that holds the booking. */
    private UUID guestAccountHolderId;
    /** Account that owns the supply. */
    private UUID hostAccountHolderId;

    /** Property the stay is at. */
    private UUID propertyId;
    /** Accommodation type sold; the inventory authority, not the listing. */
    private UUID accommodationTypeId;
    /** Listing the guest actually booked through. */
    private UUID listingId;
    /** Rate plan whose terms apply. */
    private UUID ratePlanId;
    /** Market whose rules governed this booking. */
    private String marketCode;

    /** Offer this contract was formed from; one quote yields at most one booking. */
    private UUID quoteId;
    /** Whether acceptance confirmed directly or awaited a host decision. */
    private BookingFlowType flowType;
    /** Where the booking entered the platform. */
    private BookingSourceChannel sourceChannel;

    /** Whether the contract exists and stands. */
    private BookingLifecycleState lifecycleState;
    /** How far the money has progressed; written by the payment domain. */
    private BookingPaymentState paymentState;
    /** What actually happened at the property. */
    private StayState stayState;
    /** Whether a replacement of this booking is in flight. */
    private BookingChangeState changeState;
    /** How far money owed back to the guest has travelled. */
    private BookingRefundState refundState;

    /** Nights held, half-open so a checkout date is the next guest's check-in date. */
    private StayRange stayRange;
    /** Arrival date in the property's own time zone. */
    private LocalDate checkInDate;
    /** Departure date in the property's own time zone, exclusive of the stay. */
    private LocalDate checkOutDate;
    /** IANA zone of the property, stored beside every civil value it gives meaning to. */
    private String propertyTimeZone;
    /** Local time the guest may arrive. */
    private LocalTime checkInLocalTime;
    /** Local time the guest must leave. */
    private LocalTime checkOutLocalTime;
    /** Arrival resolved to an instant at booking time, and never recomputed afterwards. */
    private Instant checkInInstant;
    /** Departure resolved to an instant at booking time, and never recomputed afterwards. */
    private Instant checkOutInstant;
    /** Time-zone database version the two instants were resolved under. */
    private @Nullable String timeZoneVersion;

    /** Adults on the booking; always at least one. */
    private short adultCount;
    /** Children on the booking. */
    private short childCount;
    /** Infants on the booking. */
    private short infantCount;
    /** Pets on the booking. */
    private short petCount;
    /** How many units of the accommodation type were sold. */
    private short unitQuantity;

    /** ISO 4217 currency of every amount on this booking. */
    private String currency;
    /** Nightly accommodation total in minor units, before discounts, fees, and tax. */
    private long accommodationAmountMinor;
    /** Total discount in minor units, stored unsigned. */
    private long discountAmountMinor;
    /** Total fees in minor units. */
    private long feeAmountMinor;
    /** Total tax in minor units. */
    private long taxAmountMinor;
    /** What the guest owes in minor units; the database forces it to reconcile with the parts. */
    private long totalAmountMinor;
    /** Expected host payout in minor units; an estimate until settlement decides. */
    private @Nullable Long hostPayoutEstimateMinor;

    /** Version of the platform terms the guest accepted. */
    private @Nullable String termsVersion;
    /** Cancellation policy key whose terms govern any refund. */
    private String cancellationPolicyKey;
    /** Version of that cancellation policy, so the entitlement can be recomputed exactly. */
    private @Nullable String cancellationPolicyVersion;
    /** Calendar restriction decision this booking was validated against. */
    private @Nullable Integer restrictionSetVersion;
    /** Pricing decision this booking's amounts came from. */
    private @Nullable Integer priceVersion;

    /**
     * When a provisional booking stops holding its nights.
     *
     * <p>Never null while {@link #lifecycleState} is {@code PROVISIONAL}: nights held without a
     * deadline are nights no sweeper can reclaim, and the database refuses to store that row.</p>
     */
    private @Nullable Instant holdExpiresAt;
    /** UTC instant the contract became binding. */
    private @Nullable Instant confirmedAt;
    /** UTC instant the contract ended. */
    private @Nullable Instant cancelledAt;
    /** Who ended it; always present when {@link #cancelledAt} is. */
    private @Nullable CancellingParty cancelledBy;
    /** Stable reason the contract ended, for policy and reporting. */
    private @Nullable String cancellationReasonCode;
    /** UTC instant arrival was evidenced. */
    private @Nullable Instant checkedInAt;
    /** UTC instant departure was evidenced. */
    private @Nullable Instant checkedOutAt;
    /** UTC instant the stay was judged finished and downstream entitlements opened. */
    private @Nullable Instant completedAt;

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
     * Reports whether the booking is a binding agreement right now.
     *
     * @return {@code true} while the contract stands and has not ended
     */
    public boolean isBinding() {
        return lifecycleState == BookingLifecycleState.CONFIRMED
                || lifecycleState == BookingLifecycleState.COMPLETED;
    }

    /**
     * Reports whether a provisional booking has lapsed at the supplied instant.
     *
     * <p>Lapsed is not the same as released. Expiry is a fact about time; releasing the nights is a
     * write that some worker still has to perform. Until it does, the nights stay consumed, which is
     * the safe direction to fail.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the booking is provisional and past its deadline
     */
    public boolean hasLapsedAt(Instant instant) {
        return lifecycleState == BookingLifecycleState.PROVISIONAL
                && holdExpiresAt != null
                && !holdExpiresAt.isAfter(instant);
    }

    /**
     * Reports whether the guest is currently in residence.
     *
     * @return {@code true} once arrival is evidenced and before departure is
     */
    public boolean isInResidence() {
        return stayState == StayState.CHECKED_IN;
    }

    /**
     * Returns how many nights were booked.
     *
     * @return night count, derived from the half-open stay range
     */
    public long nightCount() {
        return checkOutDate.toEpochDay() - checkInDate.toEpochDay();
    }

    /**
     * Reports whether money is still owed back to the guest.
     *
     * <p>True for a failed refund as well as a pending one. A refund that failed is a debt the
     * platform still owes, not a closed matter, and it must never be read as a reason to reinstate
     * a stay that has already been resold.</p>
     *
     * @return {@code true} while a refund is outstanding
     */
    public boolean hasOutstandingRefund() {
        return refundState == BookingRefundState.PENDING
                || refundState == BookingRefundState.PARTIAL
                || refundState == BookingRefundState.FAILED;
    }
}
