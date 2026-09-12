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
 * The materialized price of one night, with the arithmetic that produced it.
 *
 * <p>Search and the calendar read this instead of re-running the pricing engine, which makes it the
 * row that must never disagree with what a quote later charges. The database enforces the part it
 * can: {@code ck_daily_price_components_arithmetic} refuses a row whose breakdown does not sum to the
 * price being shown, because a row that charges one number and explains another gives no way to tell
 * which of the two is wrong.</p>
 *
 * <p>Exactly one row per night and offer is current. Two would make the price a guest sees depend on
 * which row the reader happened to find first.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("daily_price_components")
public class DailyPriceComponent {

    /** Primary key of the materialized price. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type priced. */
    private UUID accommodationTypeId;
    /** Rate plan priced; the same night costs differently under different offers. */
    private UUID ratePlanId;
    /** The night, in the property's local calendar. */
    private LocalDate stayDate;
    /** Monotonic version of this night's price, so a citing row can name the exact one it used. */
    private long priceVersion;
    /** ISO 4217 currency of every amount here. */
    private String currency;
    /** Starting amount before adjustments, in minor units. */
    private long baseAmountMinor;
    /** Net of every applied component, in minor units; negative when discounts dominate. */
    private long adjustmentTotalMinor;
    /** What the guest is shown, in minor units; always base plus adjustments. */
    private long publicAmountMinor;
    /** Applied component codes and amounts, as an immutable JSON array. */
    private JsonDocument components;
    /** What ultimately decided the price. */
    private PriceSourceDecision sourceDecision;
    /** Override that produced it; required when the source is a manual override. */
    private @Nullable UUID manualOverrideId;
    /** Recommendation that produced it, when one did. */
    private @Nullable UUID priceRecommendationId;
    /** Whether this is the version currently shown; at most one per night and offer. */
    private boolean isCurrent;
    /** UTC instant the price was computed. */
    private Instant materializedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
