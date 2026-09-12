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
 * One attempt to turn an accepted offer into a booking.
 *
 * <p>Separate from the booking because most attempts never become one. A guest who abandons a
 * payment screen should leave behind a failed attempt, not a half-built contract that support has to
 * explain and a sweeper has to clean up.</p>
 *
 * <p>{@link #status} carries the guest-facing journey a checkout screen follows. The booking's own
 * state is five independent dimensions and deliberately does not look like this.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_checkouts")
public class BookingCheckout {

    /** Primary key of the checkout attempt. */
    @Id
    private @Nullable UUID id;
    /** Reference shown to the guest while the attempt is in progress. */
    private String publicId;
    /**
     * Key that makes retrying this attempt safe.
     *
     * <p>Unique across the table, so a guest who double-submits or whose network retries cannot
     * produce two bookings from one intention.</p>
     */
    private String idempotencyKey;
    /** Account attempting to book. */
    private UUID guestAccountHolderId;
    /** Listing the attempt is against. */
    private UUID listingId;
    /** Offer being accepted. */
    private UUID quoteId;
    /** Hold protecting the nights while the attempt runs. */
    private @Nullable UUID inventoryHoldId;
    /** Booking this attempt produced; present exactly when the attempt succeeded. */
    private @Nullable UUID bookingId;
    /** Whether the attempt confirms directly or awaits a host decision. */
    private BookingFlowType flowType;
    /** Progress of the attempt. */
    private CheckoutStatus status;
    /** Version of the terms the guest accepted. */
    private @Nullable String acceptedTermsVersion;
    /** UTC instant the guest accepted them; required once the attempt leaves {@code DRAFT}. */
    private @Nullable Instant termsAcceptedAt;
    /** UTC instant the attempt stops being usable. */
    private Instant expiresAt;
    /** UTC instant a booking was produced. */
    private @Nullable Instant succeededAt;
    /** Stable code for why the attempt failed. */
    private @Nullable String failureCode;
    /** Human-readable detail behind {@link #failureCode}. */
    private @Nullable String failureReason;
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
     * Reports whether the attempt is still running at the supplied instant.
     *
     * <p>Advisory only. A countdown drawn from this is a hint to the guest; whether the hold is still
     * consumable is decided by server transaction state at the moment of the write.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} while the attempt is neither finished nor past its deadline
     */
    public boolean isLiveAt(Instant instant) {
        return switch (status) {
            case DRAFT, HELD, PAYMENT_PENDING, HOST_PENDING -> expiresAt.isAfter(instant);
            case SUCCEEDED, EXPIRED, FAILED -> false;
        };
    }

    /**
     * Reports whether this attempt produced a booking.
     *
     * @return {@code true} when the attempt succeeded
     */
    public boolean hasSucceeded() {
        return status == CheckoutStatus.SUCCEEDED;
    }
}
