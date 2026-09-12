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
 * A host's standing instruction to the pricing engine.
 *
 * <p>The bounds the engine may move within, and how much freedom it has to move at all. Effective
 * dated, because lowering a floor today must not rewrite why a price was chosen last week — the
 * database refuses two settings rows whose periods overlap for one accommodation type, so a pricing
 * run a second later can never obey a different floor than the one that was in force.</p>
 *
 * <p>Attached to an accommodation type rather than a listing: migration 016 made the accommodation
 * type the sellable thing, and a listing cannot carry a price that inventory does not have.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_pricing_settings")
public class HostPricingSettings {

    /** Primary key of the settings row. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type these instructions govern. */
    private UUID accommodationTypeId;
    /** ISO 4217 currency every amount here is expressed in. */
    private String currency;
    /** Where the nightly price comes from. */
    private PricingStrategy strategy;
    /** How freely the engine may write prices; the host's recorded consent. */
    private PricingAutomationState automationState;
    /** Starting nightly amount in minor units, before any rule applies. */
    private long baseAmountMinor;
    /** Lowest nightly amount the engine may ever produce, in minor units. */
    private long floorAmountMinor;
    /** Highest it may produce, in minor units; absent means unbounded above. */
    private @Nullable Long ceilingAmountMinor;
    /** Nightly amount the host would like to net after fees, in minor units. */
    private @Nullable Long targetNetAmountMinor;
    /** Nightly amount below which the host would rather not sell, in minor units. */
    private @Nullable Long minimumNetAmountMinor;
    /** Occupancy the optimizer should aim for, as a percentage. */
    private @Nullable BigDecimal occupancyTargetPercent;
    /** Most of a discount the host is willing to fund, as a percentage. */
    private @Nullable BigDecimal promotionFundingCapPercent;
    /** UTC instant these instructions took effect. */
    private Instant effectiveFrom;
    /** UTC instant they stopped applying; absent while they are current. */
    private @Nullable Instant effectiveUntil;
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
     * Reports whether these instructions were in force at an instant.
     *
     * @param instant the command's decision instant
     * @return {@code true} when the instant falls in the half-open effective period
     */
    public boolean isInForceAt(Instant instant) {
        return !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }

    /**
     * Reports whether an amount lies within the host's declared bounds.
     *
     * @param amountMinor candidate nightly amount in minor units
     * @return {@code true} when the amount is at or above the floor and not above any ceiling
     */
    public boolean permits(long amountMinor) {
        return amountMinor >= floorAmountMinor
                && (ceilingAmountMinor == null || amountMinor <= ceilingAmountMinor);
    }
}
