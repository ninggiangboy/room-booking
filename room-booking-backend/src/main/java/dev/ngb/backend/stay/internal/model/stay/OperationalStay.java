package dev.ngb.backend.stay.internal.model.stay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.supply.internal.model.property.Property;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.supply.internal.model.property.Property;


/**
 * The operational view of one committed booking revision.
 *
 * <p>Five state dimensions, deliberately not one: a property can be ready while access provisioning
 * failed, and a guest can report arrival while a safety case remains open. Collapsing them would
 * force a lie in the ordinary case.</p>
 *
 * <p>A trigger refuses a stay built on an uncommitted revision or on a revision belonging to another
 * booking, and a partial unique index allows only one live stay per booking.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("operational_stays")
public class OperationalStay {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Booking this stay operates. */
    private UUID bookingId;
    /** Committed revision the operational facts were taken from. */
    private UUID bookingRevisionId;
    /** Property being stayed in. */
    private UUID propertyId;
    /** Listing the booking was made against. */
    private UUID listingId;
    /** Specific unit, where supply is unit-addressed. */
    private @Nullable UUID physicalUnitId;
    /** ISO market the stay falls under. */
    private String marketCode;
    /** Guest. */
    private UUID guestAccountHolderId;
    /** Host. */
    private UUID hostAccountHolderId;
    /** IANA zone snapshotted at creation. */
    private String propertyTimeZone;
    /** Zone database version that resolved the instants. */
    private @Nullable String timeZoneVersion;
    /** Civil arrival date. */
    private LocalDate checkInDate;
    /** Civil departure date. */
    private LocalDate checkOutDate;
    /** Local arrival time. */
    private LocalTime checkInLocalTime;
    /** Local departure time. */
    private LocalTime checkOutLocalTime;
    /** Resolved arrival instant. */
    private Instant checkInInstant;
    /** Resolved departure instant. */
    private Instant checkOutInstant;
    /** How ready the property is. */
    private StayPreparationState preparationState;
    /** Whether the guest can get in. */
    private StayAccessState accessState;
    /** What evidence says about presence. */
    private StayPresenceState presenceState;
    /** Whether anything is currently wrong. */
    private StayIncidentExposure incidentExposure;
    /** What operations believes the stay should become. */
    private StayOutcomeProposal outcomeProposal;
    /** Access policy in force. */
    private @Nullable String accessPolicyVersion;
    /** Instruction release policy in force. */
    private @Nullable String instructionPolicyVersion;
    /** Completion evaluation policy in force. */
    private @Nullable String completionPolicyVersion;
    /** Configured gap between departure and the next arrival. */
    private int turnoverBufferMinutes;
    /** Whether this view is the one in force. */
    private OperationalStayStatus status;
    /** Stay that replaced this one. */
    private @Nullable UUID supersededByStayId;
    /** When it was replaced. */
    private @Nullable Instant supersededAt;
    /** When operations finished with it. */
    private @Nullable Instant closedAt;
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
     * Whether this is the operational view currently in force.
     *
     * @return true while the status is active
     */
    public boolean isLive() {
        return status == OperationalStayStatus.ACTIVE;
    }

    /**
     * Whether the property is ready and somebody can get in.
     *
     * <p>Both dimensions are asked because either alone is misleading.</p>
     *
     * @return true when preparation is complete and access is live or not needed
     */
    public boolean isArrivalReady() {
        return preparationState == StayPreparationState.READY
                && (accessState == StayAccessState.ACTIVE
                        || accessState == StayAccessState.NOT_REQUIRED);
    }

    /**
     * Whether completion may be evaluated at the given instant.
     *
     * @param at instant to test
     * @param graceMinutes configured grace after contractual checkout
     * @return true once checkout plus the grace has passed on a live stay
     */
    public boolean isCompletionDue(Instant at, long graceMinutes) {
        return isLive() && at.isAfter(checkOutInstant.plusSeconds(graceMinutes * 60));
    }
}
