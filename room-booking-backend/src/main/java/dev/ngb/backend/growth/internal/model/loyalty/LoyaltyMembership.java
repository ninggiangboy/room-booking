package dev.ngb.backend.growth.internal.model.loyalty;

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
 * One guest’s standing in one loyalty programme.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("loyalty_memberships")
public class LoyaltyMembership {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The member. */
    private UUID accountHolderId;
    /** The loyalty programme, one membership per person. */
    private UUID growthProgramId;
    /** The tier the member holds now. */
    private UUID currentTierDefinitionId;
    /** The version whose tiers the member is measured against. */
    private UUID currentProgramVersionId;
    /** First day of the qualification window now being counted. */
    private LocalDate windowStart;
    /** Day after the last day of that window. */
    private LocalDate windowEnd;
    /** IANA zone the qualification window is computed in. */
    private String windowTimeZone;
    /** Nights counted so far in the current window. */
    private int qualifyingNights;
    /** Bookings counted so far in the current window. */
    private int qualifyingBookings;
    /** Spend counted so far in the current window, in integer minor units. */
    private long qualifyingSpendMinor;
    /** ISO 4217 alphabetic code the accrued spend is denominated in. */
    private String spendCurrency;
    /** UTC instant the current tier took effect. */
    private Instant tierEffectiveFrom;
    /** UTC instant the tier is next judged. */
    private Instant tierReviewAt;
    /** UTC instant until which the tier is held despite the thresholds. */
    private @Nullable Instant downgradeProtectedUntil;
    /** Where the membership stands. */
    private LoyaltyMembershipState membershipState;
    /** Approved reason code recording why it was suspended or closed. */
    private @Nullable String stateReason;
    /** UTC instant the member joined. */
    private Instant joinedAt;
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
