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
 * One line of the authoritative breakdown behind a quote's summary.
 *
 * <p>Every line names who pays it, who receives it, who funds it, and who supplies it — four separate
 * questions that a single "amount" column collapses into one, which is how a cleaning fee ends up in
 * the wrong party's payout.</p>
 *
 * <p>Amounts are unsigned and paired with a {@link LineDirection}. A negative number would be
 * ambiguous between a discount, a refund, a correction, and a sign error, and ambiguity in money is a
 * defect. A line that reduces the price must also name the rule or promotion that granted it: an
 * unexplained deduction is indistinguishable from a bug once the booking settles.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("quote_line_items")
public class QuoteLineItem {

    /** Primary key of the line. */
    @Id
    private @Nullable UUID id;
    /** Quote this line belongs to. */
    private UUID quoteId;
    /** Position within the quote, starting at one; stable for display and reconciliation. */
    private short lineNumber;
    /** What the line is for, which drives settlement rather than presentation. */
    private QuoteLineType lineType;
    /** Stable code identifying the specific charge within its type. */
    private String componentCode;
    /** Translation key for the guest-facing label. */
    private @Nullable String descriptionKey;
    /** Whether the amount adds to what is owed or takes away from it. */
    private LineDirection direction;
    /** How many units the line covers, such as nights or guests. */
    private BigDecimal quantity;
    /** Amount per unit, in minor units, unsigned. */
    private long unitAmountMinor;
    /** Amount for the whole line, in minor units, unsigned. */
    private long amountMinor;
    /** ISO 4217 currency of both amounts. */
    private String currency;
    /** Who owes it. */
    private MoneyPartyRole payerRole;
    /** Who ends up with it. */
    private MoneyPartyRole beneficiaryRole;
    /** Who bears the cost, when that differs from who receives the money. */
    private @Nullable MoneyPartyRole funderRole;
    /** Who actually provides what the line pays for. */
    private @Nullable SupplyRole supplierRole;
    /** How the line participates in tax. */
    private LineTaxTreatment taxTreatment;
    /** Whether it comes back to the guest when the stay is cancelled. */
    private Refundability refundability;
    /** Whether commission is computed on this line. */
    private boolean commissionBasis;
    /** Rule version that produced the line, when a rule did. */
    private @Nullable UUID priceRuleVersionId;
    /** Promotion version that produced it, when a promotion did. */
    private @Nullable UUID promotionVersionId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Returns the line's effect on the quote total, in minor units.
     *
     * <p>The only place a sign is introduced, and it comes from {@link #direction} rather than from
     * how the amount happened to be stored.</p>
     *
     * @return the amount, negated when the line is a credit
     */
    public long signedAmountMinor() {
        return direction == LineDirection.CREDIT ? -amountMinor : amountMinor;
    }
}
