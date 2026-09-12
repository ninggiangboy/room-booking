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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One money line of a booking at one revision.
 *
 * <p>Amounts are unsigned and {@link #direction} carries the sign. A bare negative number cannot say
 * whether it is a discount, a refund, a correction, or a defect, and money that is ambiguous about
 * its own meaning is a defect in itself.</p>
 *
 * <p>A modification never edits these rows. It writes a new {@link #revision}, so the question "what
 * did this guest owe in March" keeps its answer permanently.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_line_items")
public class BookingLineItem {

    /** Primary key of the line. */
    @Id
    private @Nullable UUID id;
    /** Booking this line belongs to. */
    private UUID bookingId;
    /** Item this line is attributable to, when it is not booking-wide. */
    private @Nullable UUID bookingItemId;
    /** Revision of the contract this line belongs to. */
    private int revision;
    /** Position within the revision, starting at one. */
    private short lineNumber;
    /**
     * What kind of charge this is.
     *
     * <p>Shares {@link QuoteLineType} with the quote deliberately: a booking line is the quote line
     * carried forward into the contract, and giving the same concept two vocabularies would invite
     * them to drift.</p>
     */
    private QuoteLineType lineType;
    /** Stable code identifying the specific component, for reporting and reconciliation. */
    private String componentCode;
    /** Translation key for how the line is shown to a guest. */
    private @Nullable String descriptionKey;
    /** Whether the amount is owed or owed back; the only place a sign is introduced. */
    private LineDirection direction;
    /** How many units the line covers; fractional for prorated components. */
    private BigDecimal quantity;
    /** Price of one unit in minor units, unsigned. */
    private long unitAmountMinor;
    /** Total for the line in minor units, unsigned. */
    private long amountMinor;
    /** ISO 4217 currency of the line. */
    private String currency;
    /** Party that owes the amount. */
    private MoneyPartyRole payerRole;
    /** Party that receives it. */
    private MoneyPartyRole beneficiaryRole;
    /** Party actually bearing the cost, where that differs from the payer. */
    private @Nullable MoneyPartyRole funderRole;
    /** Party supplying what is being charged for. */
    private @Nullable SupplyRole supplierRole;
    /** How this line is treated for tax. */
    private LineTaxTreatment taxTreatment;
    /** Whether this line is refundable outright, never, or by policy. */
    private Refundability refundability;
    /**
     * Whether this line counts toward the commission base.
     *
     * <p>Always false for a tax line, enforced in the database, so the platform can never take
     * commission on money that belongs to a tax authority.</p>
     */
    private boolean commissionBasis;
    /** Quote line this was carried forward from. */
    private @Nullable UUID sourceQuoteLineId;
    /** Published price rule version that produced this line. */
    private @Nullable UUID priceRuleVersionId;
    /** Published promotion version that produced this line. */
    private @Nullable UUID promotionVersionId;
    /** Tax calculation line that determined this line's tax. */
    private @Nullable UUID taxCalculationLineId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Returns the amount with its sign applied.
     *
     * <p>The single place in the booking model where a sign is introduced. Everything else stores
     * magnitude and direction separately so that no reader has to guess what a minus meant.</p>
     *
     * @return positive for a charge, negative for a credit
     */
    public long signedAmountMinor() {
        return direction == LineDirection.CREDIT ? -amountMinor : amountMinor;
    }
}
