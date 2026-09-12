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
 * A stay sold through another channel and imported here.
 *
 * <p>{@link #conflictState} is the point of the table. When an external calendar claims nights this
 * platform has already sold, either resolution — cancelling our booking or ignoring the other
 * channel's — strands a real guest somewhere. That is a decision a person has to make, so the
 * conflict is recorded and surfaced rather than resolved by a sync job.</p>
 *
 * <p>Cancelled rows are retained rather than deleted, so nights reappearing on a calendar can be
 * explained by something other than data loss.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_reservations")
public class ExternalReservation {

    /** Primary key of the imported reservation. */
    @Id
    private @Nullable UUID id;
    /** Resource whose nights the external channel claims. */
    private UUID inventoryResourceId;
    /** Connection the reservation arrived over. */
    private @Nullable UUID icalConnectionId;
    /** Identifier the external system gave the event. */
    private String externalUid;
    /** Which external platform reported it. */
    private String externalSource;
    /** Nights claimed, half-open. */
    private StayRange stayRange;
    /** Free-text description the feed carried. */
    private @Nullable String summary;
    /** Whether the external channel still reports the stay. */
    private ExternalReservationStatus status;
    /** Whether the claim collides with nights already sold here. */
    private CalendarConflictState conflictState;
    /** UTC instant the reservation first appeared in a feed. */
    private Instant firstSeenAt;
    /** UTC instant it was last present in a feed, so a vanished event is detectable. */
    private Instant lastSeenAt;
    /** UTC instant the external channel withdrew it. */
    private @Nullable Instant cancelledAt;
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
     * Reports whether this reservation needs a person to decide what happens.
     *
     * @return {@code true} when a conflict has been detected and not yet acted on
     */
    public boolean needsResolution() {
        return conflictState == CalendarConflictState.DETECTED;
    }
}
