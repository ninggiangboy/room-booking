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
 * One tax, in one jurisdiction, on one taxed line.
 *
 * <p>A stay can be simultaneously subject to a marketplace-liable city tax and a supplier-liable VAT.
 * Collapsing them into a single figure loses exactly the fact that decides who files what, so
 * liability, remitter, and rule are recorded per line.</p>
 *
 * <p>{@code ck_tax_calculation_lines_remittance_agreement} refuses a line claiming
 * {@link TaxRemittanceModel#MARKETPLACE_LIABLE} while naming the host as remitter: one of the two
 * statements must be wrong, and either way the filing would reach the wrong party.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("tax_calculation_lines")
public class TaxCalculationLine {

    /** Primary key of the line. */
    @Id
    private @Nullable UUID id;
    /** Calculation this line belongs to. */
    private UUID taxCalculationId;
    /** Position within the calculation, starting at one. */
    private short lineNumber;
    /** Rule version that produced the figure. */
    private @Nullable UUID taxRuleVersionId;
    /** Quote line that was taxed. */
    private @Nullable UUID quoteLineItemId;
    /** Jurisdiction levying it, as a country code with optional subdivisions. */
    private String jurisdictionCode;
    /** Which tax this is. */
    private TaxType taxType;
    /** ISO 4217 currency of both amounts. */
    private String currency;
    /** Amount this tax was computed on, in minor units. */
    private long taxableBaseMinor;
    /** Rate applied, as a percentage; absent for a flat levy. */
    private @Nullable BigDecimal ratePercent;
    /** Tax determined for this line, in minor units. */
    private long taxAmountMinor;
    /** Party who owes the tax. */
    private MoneyPartyRole liablePartyRole;
    /** Party who hands it to the authority. */
    private MoneyPartyRole remittancePartyRole;
    /** Which remittance regime applies; must agree with the remitting party. */
    private TaxRemittanceModel remittanceModel;
    /** Whether the tax is already inside the displayed price rather than added to it. */
    private boolean isIncludedInPrice;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
