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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * What a booking was worth at one revision.
 *
 * <p>Append-only, enforced by a database trigger rather than by convention. This is the row finance,
 * tax, and support all cite when they disagree about an amount, and evidence that the application
 * can quietly revise is not evidence. A wrong figure is corrected by writing the next revision.</p>
 *
 * <p>The class carries no {@code @Version} field because the row is never updated.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_financial_snapshots")
public class BookingFinancialSnapshot {

    /** Primary key of the snapshot. */
    @Id
    private @Nullable UUID id;
    /** Booking the snapshot describes. */
    private UUID bookingId;
    /** Revision of the contract captured here; unique per booking. */
    private int revision;
    /** Why the snapshot was taken. */
    private BookingSnapshotReason reason;
    /** Quote the amounts were composed from, when one applies. */
    private @Nullable UUID quoteId;
    /** ISO 4217 currency of every amount here. */
    private String currency;
    /** Accommodation total in minor units. */
    private long accommodationAmountMinor;
    /** Discount total in minor units, unsigned. */
    private long discountAmountMinor;
    /** Fee total in minor units. */
    private long feeAmountMinor;
    /** Tax total in minor units. */
    private long taxAmountMinor;
    /** What the guest owes in minor units; forced to reconcile with the parts. */
    private long totalAmountMinor;
    /** What the guest has actually paid by this revision, in minor units. */
    private long guestPaidAmountMinor;
    /** Platform's share in minor units. */
    private long platformFeeAmountMinor;
    /** Host's share in minor units. */
    private long hostPayoutAmountMinor;
    /** Tax calculation these figures used. */
    private @Nullable UUID taxCalculationId;
    /** Digest of the inputs, so a recomputation can be shown to agree with what was recorded. */
    private String calculationHash;
    /** UTC instant the snapshot was taken, supplied by the caller's decision clock. */
    private Instant capturedAt;
}
