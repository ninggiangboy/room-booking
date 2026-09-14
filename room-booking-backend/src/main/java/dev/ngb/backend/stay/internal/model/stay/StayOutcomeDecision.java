package dev.ngb.backend.stay.internal.model.stay;

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
import dev.ngb.backend.booking.types.BookingActorType;

/**
 * What operations concluded about a stay, and what booking did with it.
 *
 * <p>A proposal, not a transition. Review eligibility and host-fund release consume booking committed
 * facts, never this row.</p>
 *
 * <p>Its basis -- proposal, policy version, evidence and effective instant -- is frozen by trigger.
 * The result and the booking transition arrive later and may be written once. Correction is
 * supersession, so a later evaluation never erases what the earlier one saw.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("stay_outcome_decisions")
public class StayOutcomeDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stay decided about. */
    private UUID operationalStayId;
    /** Revision the evaluation was made against. */
    private UUID bookingRevisionId;
    /** What operations proposed. */
    private StayOutcomeProposal proposal;
    /** Completion policy applied. */
    private String policyVersion;
    /** Why this proposal and not another. */
    private String reasonCode;
    /** Instant the proposal speaks about. */
    private Instant effectiveAt;
    /** Observations weighed. */
    private UUID[] evidenceObservationIds;
    /** Hash of those inputs; a different hash means the inputs moved. */
    private String evidenceHash;
    /** What kind of actor decided. */
    private BookingActorType decidedByActorType;
    /** Which actor decided, where there was one. */
    private @Nullable UUID decidedByActorId;
    /** What booking answered. */
    private StayOutcomeResult result;
    /** Transition booking made, where it accepted. */
    private @Nullable UUID bookingTransitionId;
    /** Why booking refused. */
    private @Nullable String rejectionReason;
    /** When the proposal was put to booking. */
    private Instant requestedAt;
    /** When booking answered. */
    private @Nullable Instant respondedAt;
    /** Decision that replaced this one. */
    private @Nullable UUID supersededByDecisionId;
    /** When it was replaced. */
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
     * Whether this is the proposal currently standing for its stay.
     *
     * @return true while neither superseded nor withdrawn
     */
    public boolean isLive() {
        return supersededAt == null && result != StayOutcomeResult.WITHDRAWN;
    }

    /**
     * Whether booking acted on this proposal.
     *
     * @return true when accepted and the transition is named
     */
    public boolean wasActedOn() {
        return result == StayOutcomeResult.ACCEPTED_BY_BOOKING && bookingTransitionId != null;
    }
}
