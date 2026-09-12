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
 * A guest with nowhere to sleep tonight.
 *
 * <p>The only part of this domain with a clock that matters in minutes, so the case carries its own
 * deadline and its own budget rather than borrowing the cancellation's.</p>
 *
 * <p>Hard constraints are stored explicitly, because "anything nearby" is not an acceptable offer to a
 * guest travelling with a wheelchair, a pet, or a connecting flight at six. Spend is capped by
 * constraint, whatever an agent types into a tool at two in the morning.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("relocation_cases")
public class RelocationCase {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identifier safe to show a guest or an agent. */
    private String publicId;
    /** Booking that failed. */
    private UUID bookingId;
    /** Revision in force when it failed. */
    private UUID bookingRevisionId;
    /** Decision that ended the original stay, where one was taken. */
    private @Nullable UUID cancellationDecisionId;
    /** Why the guest must be moved. Drives who funds it. */
    private RelocationCause cause;
    /** Kind of actor that opened the case. */
    private BookingActorType openedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID openedByActorId;
    /** Agent currently responsible for resolving it. */
    private @Nullable UUID ownerActorId;
    /** What any acceptable replacement must satisfy. */
    private JsonDocument hardConstraints;
    /** Whether the guest has agreed to be moved. */
    private RelocationConsentState guestConsentState;
    /** When they agreed. */
    private @Nullable Instant guestConsentAt;
    /** ISO 4217 code. */
    private String currency;
    /** Most the case may spend, in minor units. */
    private long budgetCapMinor;
    /** What has been approved so far. Never above the cap. */
    private long approvedSpendMinor;
    /** Who pays for the move. */
    private OverrideFundingParty fundingParty;
    /** Override programme decision funding it, where one applies. */
    private @Nullable UUID overrideDecisionId;
    /** Stay the guest was moved into. Never the booking that failed. */
    private @Nullable UUID replacementBookingId;
    /** Progress of the case. */
    private RelocationCaseState state;
    /** How it ended. Required once resolved. */
    private @Nullable RelocationOutcome outcome;
    /** When the guest must have an answer by. */
    private Instant resolutionDueAt;
    /** When they got one. */
    private @Nullable Instant resolvedAt;
    /** Correlation identifier for the work that wrote it. */
    private String correlationId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Budget the case has left to spend.
     *
     * @return minor units still available under the cap
     */
    public long remainingBudgetMinor() {
        return budgetCapMinor - approvedSpendMinor;
    }

    /**
     * Whether the case still needs somebody to act on it.
     *
     * @return {@code true} while it is unresolved and not abandoned
     */
    public boolean isOpen() {
        return state == RelocationCaseState.OPEN
                || state == RelocationCaseState.OFFERING
                || state == RelocationCaseState.ACCEPTED;
    }
}
