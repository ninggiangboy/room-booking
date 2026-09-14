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
import org.springframework.data.relational.core.mapping.Table;


/**
 * One accrual toward a loyalty tier.
 *
 * <p>Append-only. A cancelled stay takes its nights back by appending a negative row naming the
 * one it reverses, never by editing the row that counted them, so the tier a guest held last
 * quarter stays explainable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("loyalty_qualifying_events")
public class LoyaltyQualifyingEvent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The membership this accrual belongs to. */
    private UUID loyaltyMembershipId;
    /** What produced the accrual. */
    private LoyaltyQualifyingEventKind eventKind;
    /** The booking that produced it. */
    private @Nullable UUID bookingId;
    /** UTC instant the qualifying activity happened. */
    private Instant occurredAt;
    /** First day of the window this accrual counts toward. */
    private LocalDate countedWindowStart;
    /** Nights this row contributes, negative when it reverses another. */
    private int nightsCounted;
    /** Bookings this row contributes, negative when it reverses another. */
    private int bookingsCounted;
    /** Spend this row contributes, in integer minor units. */
    private long spendMinor;
    /** ISO 4217 alphabetic code the spend is denominated in. */
    private String currency;
    /** The earlier accrual this row takes back. */
    private @Nullable UUID reversesEventId;
    /** Approved reason code recording why it was taken back. */
    private @Nullable String reversalReason;
    /** Approved reason code recording why credit was added by hand. */
    private @Nullable String adjustmentReason;
    /** Operator answerable for a hand-made adjustment. */
    private @Nullable UUID approvedBy;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
