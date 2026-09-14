package dev.ngb.backend.discovery.internal.model.profile;

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
import dev.ngb.backend.review.types.DerivedProfileStatus;

import dev.ngb.backend.review.types.DerivedProfileStatus;


/**
 * Funnel counts for one listing over one window, beside the exposure conditions that produced them.
 *
 * <p>A raw conversion rate is not a quality signal: a listing shown at rank one gets more clicks for
 * reasons that have nothing to do with the listing. An aggregate claiming position-bias correction
 * must carry its mean position and its propensity-weighted denominator.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_outcome_aggregates")
public class ListingOutcomeAggregate {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The listing this row belongs to. */
    private UUID listingId;
    /** The window this aggregate covers. */
    private OutcomeWindowKind windowKind;
    /** First civil day in the window; absent for an all-time aggregate. */
    private @Nullable LocalDate windowStart;
    /** Last civil day in the window. */
    private LocalDate windowEnd;
    /** Whether this aggregate is the one being served. */
    private DerivedProfileStatus status;
    /** How many impression there are. */
    private long impressionCount;
    /** Impressions weighted by position propensity, which is the denominator a corrected rate needs. */
    private @Nullable BigDecimal propensityWeightedImpressions;
    /** Mean rank the listing was shown at over the window. */
    private @Nullable BigDecimal meanPosition;
    /** Whether the rates here were corrected for the exposure conditions that produced them. */
    private boolean positionBiasCorrected;
    /** How many click there are. */
    private long clickCount;
    /** How many detail view there are. */
    private long detailViewCount;
    /** How many save there are. */
    private long saveCount;
    /** How many booking started there are. */
    private long bookingStartedCount;
    /** How many booking confirmed there are. */
    private long bookingConfirmedCount;
    /** How many stay completed there are. */
    private long stayCompletedCount;
    /** How many guest cancellation there are. */
    private long guestCancellationCount;
    /** How many host cancellation there are. */
    private long hostCancellationCount;
    /** How many expiration there are. */
    private long expirationCount;
    /** How many no show there are. */
    private long noShowCount;
    /** How many refund there are. */
    private long refundCount;
    /** How many dispute there are. */
    private long disputeCount;
    /** How many repeat booking there are. */
    private long repeatBookingCount;
    /** Which version of the aggregation rules produced this row. */
    private String aggregationVersion;
    /** UTC instant up to which inputs were included. */
    private @Nullable Instant inputWatermark;
    /** UTC instant the counts were last reconciled against authoritative bookings and reviews. */
    private @Nullable Instant reconciledAt;
    /** UTC instant the aggregate was computed. */
    private Instant computedAt;
    /** UTC instant after which the aggregate is stale. */
    private @Nullable Instant expiresAt;
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
