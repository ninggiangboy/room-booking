package dev.ngb.backend.model;

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

/**
 * What a booking contract said, at one point in its life.
 *
 * <p>A modification does not edit dates, nights and amounts in place: it commits a new revision that
 * supersedes the previous one and names it as its predecessor. Exactly one committed revision per
 * booking is current, the chain cannot fork, and a committed revision is frozen by trigger.</p>
 *
 * <p>The booking row's own columns are the projection of whichever revision is current; this row is
 * the authority they project.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_revisions")
public class BookingRevision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Booking whose terms these are. */
    private UUID bookingId;
    /** Monotonic number within the booking. Revision one is the original. */
    private int revisionNumber;
    /**
     * The revision this one replaces. Null only on revision one, and claimed at most once, so the
     * chain cannot fork into two histories that both look authoritative.
     */
    private @Nullable UUID predecessorRevisionId;
    /** Why this revision exists. */
    private BookingRevisionType revisionType;
    /** How much evidence stands behind the terms. */
    private BookingRevisionProvenance provenance;
    /** Supply being sold at this revision. */
    private UUID listingId;
    /** Rate plan, and therefore the terms, at this revision. */
    private UUID ratePlanId;
    /** Half-open stay range. The checkout date is the next guest's check-in. */
    private StayRange stayRange;
    /** Civil arrival date. */
    private LocalDate checkInDate;
    /** Civil departure date. */
    private LocalDate checkOutDate;
    /** IANA zone the civil values are read in. */
    private String propertyTimeZone;
    /** Official arrival wall time, which cancellation cutoffs count back from. */
    private LocalTime checkInLocalTime;
    /** That wall time resolved to an instant when the revision was written. */
    private Instant checkInInstant;
    /** Adults on the booking. */
    private short adultCount;
    /** Children. */
    private short childCount;
    /** Infants. */
    private short infantCount;
    /** Pets. */
    private short petCount;
    /** Units of supply held. */
    private short unitQuantity;
    /** ISO 4217 code. */
    private String currency;
    /** What the contract was worth at this revision, in minor units. */
    private long totalAmountMinor;
    /** Quote the terms came from, where one exists. */
    private @Nullable UUID quoteId;
    /** Cancellation terms in force at this revision. */
    private @Nullable UUID policyVersionId;
    /** The acceptance that proves the guest was shown them. */
    private @Nullable UUID policyAcceptanceId;
    /** Snapshot of what the contract was worth. */
    private @Nullable UUID financialSnapshotId;
    /** Version of the allocation these terms were priced under. */
    private int allocationVersion;
    /** Modification proposal that produced this revision. */
    private @Nullable UUID sourceProposalId;
    /** Cancellation decision that produced it. */
    private @Nullable UUID sourceDecisionId;
    /** Correlation identifier for the work that wrote it. */
    private String correlationId;
    /** SHA-256 over the terms, lowercase hex. */
    private String termsHash;
    /** SHA-256 over the nightly shape, lowercase hex. */
    private String nightsHash;
    /** Lifecycle. Frozen once committed. */
    private BookingRevisionStatus status;
    /** Kind of actor that committed it. */
    private @Nullable BookingActorType committedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID committedByActorId;
    /** When it became the agreed terms. */
    private @Nullable Instant committedAt;
    /** When a later revision replaced it. */
    private @Nullable Instant supersededAt;
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
     * Whether these are the booking's current terms.
     *
     * @return {@code true} when the revision is committed and not yet superseded
     */
    public boolean isCurrent() {
        return status == BookingRevisionStatus.COMMITTED;
    }

    /**
     * Whether the revision may still be altered.
     *
     * <p>A committed revision is frozen by trigger; a draft may still be given nights and totals.</p>
     *
     * @return {@code true} while the revision is a draft
     */
    public boolean isMutable() {
        return status == BookingRevisionStatus.DRAFT;
    }

    /**
     * Number of nights the stay covers at this revision.
     *
     * @return nights between arrival and departure
     */
    public long nightCount() {
        return checkOutDate.toEpochDay() - checkInDate.toEpochDay();
    }
}
