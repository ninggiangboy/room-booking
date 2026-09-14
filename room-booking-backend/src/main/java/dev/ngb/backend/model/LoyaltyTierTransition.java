package dev.ngb.backend.model;

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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One recorded change of a member’s loyalty tier.
 *
 * <p>Written after the membership has moved, and it must describe the move that happened: a
 * qualification goes up, a downgrade goes down, and a tier reached outside the published
 * thresholds names the person who granted it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("loyalty_tier_transitions")
public class LoyaltyTierTransition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The membership that moved. */
    private UUID loyaltyMembershipId;
    /** The tier held before; absent only on enrolment. */
    private @Nullable UUID fromTierDefinitionId;
    /** The tier held after. */
    private UUID toTierDefinitionId;
    /** Why the tier changed. */
    private LoyaltyTransitionReason transitionReason;
    /** What the change rested on, in the words the member can be shown. */
    private String evidenceSummary;
    /** Nights counted when the change was judged. */
    private int qualifyingNights;
    /** Bookings counted when the change was judged. */
    private int qualifyingBookings;
    /** Spend counted when the change was judged, in integer minor units. */
    private long qualifyingSpendMinor;
    /** ISO 4217 alphabetic code the counted spend is denominated in. */
    private String currency;
    /** First day of the window the change was judged over. */
    private LocalDate windowStart;
    /** Day after the last day of that window. */
    private LocalDate windowEnd;
    /** UTC instant the new tier took effect. */
    private Instant effectiveFrom;
    /** UTC instant the new tier is next judged. */
    private @Nullable Instant reviewAt;
    /** Operator who granted a tier outside the published thresholds. */
    private @Nullable UUID approvedBy;
    /** Approved reason code recording why they did. */
    private @Nullable String approvalReason;
    /** Migration 024 notification that told the member. */
    private @Nullable UUID notificationIntentId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
