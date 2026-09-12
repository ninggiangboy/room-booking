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
 * A host's direct price instruction for specific nights, outranking every rule.
 *
 * <p>Recorded rather than applied in place, so that "why was this night this price" has an answer
 * naming a person and a reason, and so that withdrawing it restores the computed price instead of
 * leaving whoever comes next to guess one.</p>
 *
 * <p>Two active overrides may not cover the same night: that would give one date two host-declared
 * prices with no rule for choosing between them, so the database refuses the second and replacing an
 * override means withdrawing the first.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("manual_price_overrides")
public class ManualPriceOverride {

    /** Primary key of the override. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type whose nights are overridden. */
    private UUID accommodationTypeId;
    /** Nights covered, half-open so the upper bound is the first night not covered. */
    private StayRange stayRange;
    /** Nightly amount the host insists on, in minor units. */
    private long nightlyAmountMinor;
    /** ISO 4217 currency of that amount. */
    private String currency;
    /** Whether promotions may still reduce the overridden price. */
    private PromotionBehaviour promotionBehaviour;
    /** Whether the instruction still applies. */
    private PriceOverrideStatus status;
    /** Account holder who gave the instruction. */
    private UUID actorAccountHolderId;
    /** Why they gave it, for the host and for anyone auditing the price later. */
    private @Nullable String reason;
    /** UTC instant it stopped applying; paired with a non-active status. */
    private @Nullable Instant withdrawnAt;
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
     * Reports whether this override governs a given night.
     *
     * @param date the night in the property's local calendar
     * @return {@code true} when the override is active and the date falls in its range
     */
    public boolean covers(LocalDate date) {
        return status == PriceOverrideStatus.ACTIVE
                && !date.isBefore(stayRange.checkIn())
                && date.isBefore(stayRange.checkOut());
    }
}
