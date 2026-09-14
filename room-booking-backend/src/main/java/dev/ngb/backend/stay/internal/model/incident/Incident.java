package dev.ngb.backend.stay.internal.model.incident;

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
import dev.ngb.backend.market.internal.model.market.Market;
import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.supply.internal.model.property.Property;
import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.messaging.internal.model.conversation.Conversation;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.market.internal.model.market.Market;
import dev.ngb.backend.messaging.internal.model.conversation.Conversation;
import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.supply.internal.model.property.Property;


/**
 * Something is wrong during a stay.
 *
 * <p>Category and severity are separate facts, and severity carries an orderable rank paired to it by
 * a check constraint. A declared safety report sits at the deterministic floor and cannot be filed in
 * an ordinary queue; a trigger refuses any lowering without a reason, refuses lowering a safety report
 * without a named reviewer, and refuses clearing the safety flag at all.</p>
 *
 * <p>The incident also allocates its own event sequence, so two agents acting at once cannot both
 * write the same numbered entry in its timeline.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("incidents")
public class Incident {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Booking affected. */
    private @Nullable UUID bookingId;
    /** Stay affected. */
    private @Nullable UUID operationalStayId;
    /** Listing affected. */
    private @Nullable UUID listingId;
    /** Property affected. */
    private @Nullable UUID propertyId;
    /** Market whose obligations apply. */
    private @Nullable String marketCode;
    /** Conversation opened for it. */
    private @Nullable UUID conversationId;
    /** Who reported it. */
    private @Nullable UUID reporterAccountHolderId;
    /** In what capacity. */
    private IncidentReporterRole reporterRole;
    /** Reference to what the reporter wrote. */
    private @Nullable String descriptionReference;
    /** What kind of thing went wrong. */
    private IncidentCategory category;
    /** How urgent it is. */
    private IncidentSeverity severity;
    /** Orderable form of the severity; zero is most urgent. */
    private short severityRank;
    /** Whether danger was declared. Cannot be cleared once set. */
    private boolean safetyFlag;
    /** Why severity last changed. */
    private @Nullable String severityChangeReason;
    /** Who reviewed a lowering. */
    private @Nullable UUID severityReviewedByAccountHolderId;
    /** When that review happened. */
    private @Nullable Instant severityReviewedAt;
    /** Where the incident stands. */
    private IncidentState state;
    /** What kind of owner it has. */
    private @Nullable IncidentOwnerType ownerType;
    /** Named owner. */
    private @Nullable UUID ownerAccountHolderId;
    /** Queue holding it, where nobody is named. */
    private @Nullable String ownerQueue;
    /** Incident policy in force. */
    private String policyVersion;
    /** When the response obligation falls due. */
    private @Nullable Instant sloTargetAt;
    /** When somebody first responded. */
    private @Nullable Instant firstResponseAt;
    /** When immediate impact was reduced. */
    private @Nullable Instant mitigatedAt;
    /** When it was operationally resolved. */
    private @Nullable Instant resolvedAt;
    /** When it was closed. */
    private @Nullable Instant closedAt;
    /** Incident this duplicates. */
    private @Nullable UUID duplicateOfIncidentId;
    /** Domain it was handed to. */
    private @Nullable IncidentTransferDomain transferredDomain;
    /** That domain case reference. */
    private @Nullable String transferredReference;
    /** When it was handed over. */
    private @Nullable Instant transferredAt;
    /** Number the next timeline entry will take. */
    private long nextSequence;
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
     * Whether this incident is still somebody's active concern.
     *
     * @return true while not resolved, closed, duplicated or transferred
     */
    public boolean isOpen() {
        return state != IncidentState.RESOLVED && state != IncidentState.CLOSED
                && state != IncidentState.DUPLICATE && state != IncidentState.TRANSFERRED;
    }

    /**
     * Whether the response obligation has been missed at the given instant.
     *
     * @param at instant to test
     * @return true when open, a target exists, and it has passed
     */
    public boolean isBreachingSlo(Instant at) {
        return isOpen() && sloTargetAt != null && at.isAfter(sloTargetAt);
    }

    /**
     * Highest timeline sequence the incident has issued.
     *
     * @return the last issued number, or zero when nothing has been written
     */
    public long lastIssuedSequence() {
        return nextSequence - 1;
    }
}
