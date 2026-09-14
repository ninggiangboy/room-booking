package dev.ngb.backend.growth.internal.model.loyalty;

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
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.platform.JsonDocument;


/**
 * One tier within a published loyalty programme version.
 *
 * <p>Sealed along with its version: the tiers are what memberships were measured against, so a
 * different set of tiers is a new version rather than an edit to this one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("loyalty_tier_definitions")
public class LoyaltyTierDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The loyalty terms this tier belongs to, frozen once published. */
    private UUID growthProgramVersionId;
    /** Stable key naming the tier within its version. */
    private String tierKey;
    /** How high the tier sits, zero being the entry tier. */
    private short tierRank;
    /** Name the tier is shown under. */
    private String displayName;
    /** How long, in days, the window counted toward this tier runs. */
    private int qualificationWindowDays;
    /** Nights needed in the window to reach this tier. */
    private @Nullable Integer qualifyingNights;
    /** Bookings needed in the window to reach this tier. */
    private @Nullable Integer qualifyingBookings;
    /** Spend needed in the window, in integer minor units. */
    private @Nullable Long qualifyingSpendMinor;
    /** ISO 4217 alphabetic code the spend threshold is denominated in. */
    private @Nullable String qualifyingCurrency;
    /** What the tier gives, in the words members were told. */
    private String benefitSummary;
    /** The benefits in the structured form the application applies. */
    private JsonDocument benefitPayload;
    /** How long, in days, a member keeps the tier after falling below its threshold. */
    private int downgradeGraceDays;
    /** Whether a status held with a partner can be matched into this tier. */
    private boolean partnerStatusMatchable;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
