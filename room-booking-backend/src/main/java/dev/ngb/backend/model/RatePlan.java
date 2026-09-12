package dev.ngb.backend.model;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The conditions an accommodation type is offered under.
 *
 * <p>The same room is commonly sold several times over at different prices — with breakfast and
 * without, refundable and not — so the offer has to be a separate thing from the room. A rate plan
 * carries the cancellation policy the guest accepts, what food is included, when payment is taken,
 * and the stay limits that apply to this offer specifically.</p>
 *
 * <p>Exactly one plan per accommodation type may be the default, so a quote always has an
 * unambiguous starting point when the guest expressed no preference.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("rate_plans")
public class RatePlan {

    /** Primary key of the rate plan. */
    @Id
    private @Nullable UUID id;
    /** Accommodation type this offer sells. */
    private UUID accommodationTypeId;
    /** Short human-readable reference used in operations and support. */
    private String referenceCode;
    /** Guest-facing name of the offer. */
    private String displayName;
    /** Whether this is the offer used when the guest expressed no preference. */
    private boolean isDefault;
    /** Key of the cancellation policy a guest accepts by booking this offer. */
    private String cancellationPolicyKey;
    /** What food is included. */
    private MealPlan mealPlan;
    /** When payment is taken. */
    private PrepaymentRequirement prepaymentRequirement;
    /** Whether a damage or security deposit is required. */
    private boolean depositRequired;
    /** Shortest stay this offer permits. */
    private @Nullable Short minimumStayNights;
    /** Longest stay this offer permits. */
    private @Nullable Short maximumStayNights;
    /** How far ahead this offer may be booked. */
    private @Nullable Short bookingWindowDays;
    /** Whether the offer is in service. */
    private SupplyLifecycle status;
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
     * Reports whether a stay of the given length is permitted by this offer.
     *
     * <p>These are the offer's own limits. Per-date restrictions on the calendar are stricter still
     * and are checked separately at quote and commit time.</p>
     *
     * @param nights number of nights in the stay
     * @return {@code true} when the stay length falls within this offer's bounds
     */
    public boolean permitsStayOf(int nights) {
        if (nights <= 0) {
            return false;
        }
        boolean aboveMinimum = minimumStayNights == null || nights >= minimumStayNights;
        boolean belowMaximum = maximumStayNights == null || nights <= maximumStayNights;
        return aboveMinimum && belowMaximum;
    }
}
