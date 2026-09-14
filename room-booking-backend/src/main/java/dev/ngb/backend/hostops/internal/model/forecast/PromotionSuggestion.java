package dev.ngb.backend.hostops.internal.model.forecast;

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
 * A promotion the platform thinks is worth running, with what it would add, what it would cost, and
 * who would pay.
 * <p>The baseline is on the row because an estimate of extra bookings means nothing without the
 * number it is extra to: twelve bookings where the baseline was eleven is a discount on demand the
 * host had anyway. The interval is allowed to cross zero, because an honest model sometimes says a
 * promotion may cost bookings. Once decided the whole estimate is frozen, so the disclosure the
 * host agreed to still describes what they agreed to.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("promotion_suggestions")
public class PromotionSuggestion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The accommodation type the promotion would apply to. */
    private UUID accommodationTypeId;
    /** ISO 3166-1 alpha-2 market the suggestion is made in. */
    private String marketCode;
    /** What shape of promotion is being suggested. */
    private PromotionSuggestionKind suggestionKind;
    /** First stay date the suggested promotion would cover. */
    private LocalDate appliesFrom;
    /** Day after the last stay date the suggested promotion would cover. */
    private LocalDate appliesUntil;
    /** How much the suggested promotion would take off the nightly price. */
    private BigDecimal discountPercent;
    /** Least number of nights a stay must be for the suggested promotion to apply. */
    private @Nullable Short minimumNights;
    /** ISO 4217 alphabetic code the money on this row is denominated in. */
    private String currency;
    /**
     * How many bookings are expected without the promotion; an estimate of extra bookings means
     * nothing without it.
     */
    private BigDecimal baselineBookings;
    /** Revenue expected without the promotion, in integer minor units. */
    private long baselineRevenueMinor;
    /** How many bookings the promotion is expected to add over the baseline. */
    private BigDecimal incrementalBookings;
    /**
     * Bottom of the incremental-bookings interval, which is allowed to be negative because an
     * honest model sometimes says the promotion may cost bookings.
     */
    private BigDecimal incrementalIntervalLow;
    /** Top of the incremental-bookings interval. */
    private BigDecimal incrementalIntervalHigh;
    /** Revenue the promotion is expected to add over the baseline, in integer minor units. */
    private long incrementalRevenueMinor;
    /** What the promotion is expected to cost the host, in integer minor units. */
    private long hostCostMinor;
    /** Who pays for the discount. */
    private PromotionFundingSplit fundingSplit;
    /** The hosts share of the discount, where it is shared. */
    private @Nullable BigDecimal hostFundedSharePercent;
    /** The forecast run the estimate was computed against. */
    private @Nullable UUID forecastRunId;
    /** The model version that produced the estimate. */
    private @Nullable UUID modelVersionId;
    /** What the estimate rests on. */
    private SuggestionEvidenceBasis evidenceBasis;
    /** What the host did about the suggestion. */
    private SuggestionDecisionState decisionState;
    /** The promotion actually created when the host accepted. */
    private @Nullable UUID acceptedPromotionId;
    /** UTC instant the suggestion was produced. */
    private Instant generatedAt;
    /**
     * UTC instant after which the suggestion no longer describes the demand picture it was computed
     * against.
     */
    private Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
