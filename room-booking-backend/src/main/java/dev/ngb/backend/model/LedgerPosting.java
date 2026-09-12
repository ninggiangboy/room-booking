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
 * One positive amount on one account in one direction, with the dimensions that explain it.
 *
 * <p>Amounts are always positive and carry their direction explicitly: a debit and a credit are
 * different facts, not the same number with opposite signs. The dimension columns are relational
 * rather than a tag document, because reporting has to filter and join on them and an arbitrary JSON
 * bag cannot become the only queryable source of an entry's meaning.</p>
 *
 * <p>Postings of a posted transaction cannot be added to, altered, or removed. The row carries no
 * optimistic lock and no update timestamp for that reason.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ledger_postings")
public class LedgerPosting {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Transaction the posting belongs to. */
    private UUID transactionId;
    /** Position within the transaction, unique inside it. */
    private short sequenceNumber;
    /** Account being debited or credited. */
    private UUID ledgerAccountId;
    /** Which side of the entry this amount lands on. */
    private PostingDirection direction;
    /** Positive minor units. */
    private long amountMinor;
    /** ISO 4217 code, matching the transaction. */
    private String currency;
    /** Host dimension, where the amount is attributable to one. */
    private @Nullable UUID hostAccountHolderId;
    /** Guest dimension, where the amount is attributable to one. */
    private @Nullable UUID guestAccountHolderId;
    /** Listing dimension. */
    private @Nullable UUID listingId;
    /** Market dimension. */
    private @Nullable String marketCode;
    /** Booking dimension. */
    private @Nullable UUID bookingId;
    /** Revision of that booking the amount belongs to. */
    private @Nullable Integer bookingRevision;
    /** Exact contractual line the amount derives from. */
    private @Nullable UUID bookingLineItemId;
    /** First day of the stay or service period, listing-local. */
    private @Nullable LocalDate servicePeriodStart;
    /** Last day of that period, listing-local. */
    private @Nullable LocalDate servicePeriodEnd;
    /** Obligation dimension. */
    private @Nullable UUID collectionObligationId;
    /** Payment operation the movement corresponds to. */
    private @Nullable UUID paymentOperationId;
    /** Merchant account dimension. */
    private @Nullable UUID providerAccountId;
    /** Dispute dimension. */
    private @Nullable UUID paymentDisputeId;
    /** Payout dimension. */
    private @Nullable UUID payoutInstructionId;
    /** Exact payout item the amount belongs to. */
    private @Nullable UUID payoutItemId;
    /** Reserve dimension. */
    private @Nullable UUID hostReserveId;
    /** Recovery dimension. */
    private @Nullable UUID hostRecoveryId;
    /** Case that caused or explains the posting. */
    private @Nullable UUID reconciliationCaseId;
    /** Tax line the amount derives from. */
    private @Nullable UUID taxCalculationLineId;
    /** Promotion version that funded the amount. */
    private @Nullable UUID promotionVersionId;
    /** Control category the posting is reconciled under. */
    private @Nullable String reconciliationCategory;

    /** UTC instant the posting was written. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Signed effect of this posting on the account, for arithmetic that needs one number.
     *
     * <p>Derived on read rather than stored. The stored value stays unsigned so that no caller can
     * mistake a negative credit for a debit.</p>
     *
     * @return the amount, negated when the posting is a credit
     */
    public long signedAmountMinor() {
        return direction == PostingDirection.DEBIT ? amountMinor : -amountMinor;
    }
}
