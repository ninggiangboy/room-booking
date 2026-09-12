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
 * A proposed change to a live booking contract, waiting for the other party.
 *
 * <p>It is a separate row from the revision it would produce, because most proposals are never accepted
 * and an abandoned one must not leave a half-changed booking behind.</p>
 *
 * <p>A proposal that would move dates holds the new nights while it waits. Without that hold, the guest
 * accepts a change to nights that were sold to somebody else in the meantime, and the platform finds
 * out at commit time -- after telling both parties it was agreed.</p>
 *
 * <p>The money prerequisite is explicit rather than inferred: a change that costs more must name the
 * obligation that collects it, and one that costs less must name the refund instruction that returns
 * it, before the row may be committed. At most one proposal per booking is live.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_modification_proposals")
public class BookingModificationProposal {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identifier safe to show a guest or a host. */
    private String publicId;
    /** Booking being changed. */
    private UUID bookingId;
    /** Revision the proposal is calculated against. */
    private UUID currentRevisionId;
    /** Revision it produced, once committed. */
    private @Nullable UUID proposedRevisionId;
    /** Kind of actor that proposed the change. */
    private BookingActorType initiatedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID initiatedByActorId;
    /** What the proposal would change. */
    private ModificationChangeType changeType;
    /** Structured reason shown to the counterparty. */
    private String reasonCode;
    /** Quote priced for the proposed terms. */
    private @Nullable UUID proposedQuoteId;
    /** Whether the change needs fresh policy acceptance. */
    private boolean requiresPolicyAcceptance;
    /** Terms the guest would be accepting. */
    private @Nullable UUID policyVersionId;
    /** Hold keeping the new nights reservable while the proposal waits. */
    private @Nullable UUID inventoryHoldId;
    /** When that hold lapses. Required whenever a hold is held. */
    private @Nullable Instant holdExpiresAt;
    /** ISO 4217 code. */
    private String currency;
    /** Whether the change costs more, less, or the same. */
    private ModificationDeltaDirection deltaDirection;
    /** Positive minor units of difference. Zero exactly when the direction is zero. */
    private long deltaAmountMinor;
    /** Obligation that would collect an increase. */
    private @Nullable UUID collectionObligationId;
    /** Instruction that would return a decrease. */
    private @Nullable UUID refundInstructionId;
    /** Whether the guest has agreed. */
    private PartyApprovalState guestApprovalState;
    /** When they did. */
    private @Nullable Instant guestApprovedAt;
    /** Whether the host has agreed. */
    private PartyApprovalState hostApprovalState;
    /** When they did. */
    private @Nullable Instant hostApprovedAt;
    /** Lifecycle of the proposal. */
    private ModificationProposalStatus status;
    /** When an unanswered proposal lapses. */
    private Instant expiresAt;
    /** When it produced a revision. */
    private @Nullable Instant committedAt;
    /** Why a party refused. */
    private @Nullable String declinedReason;
    /** SHA-256 over the canonical request, lowercase hex. */
    private String requestHash;
    /** SHA-256 over the evaluation inputs, lowercase hex. */
    private String inputHash;
    /** SHA-256 over the result, once calculated. */
    private @Nullable String resultHash;
    /** Client key, unique within the booking, that a retry converges on. */
    private String idempotencyKey;
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
     * Whether the proposal is still awaiting or holding a decision.
     *
     * @return {@code true} while it is open or accepted
     */
    public boolean isLive() {
        return status == ModificationProposalStatus.OPEN
                || status == ModificationProposalStatus.ACCEPTED;
    }

    /**
     * Whether every party whose agreement was needed has given it.
     *
     * <p>The database enforces the same rule at commit; this exists so a service can check first.</p>
     *
     * @return {@code true} when neither side is outstanding
     */
    public boolean hasRequiredConsent() {
        return guestApprovalState != PartyApprovalState.PENDING
                && guestApprovalState != PartyApprovalState.DECLINED
                && hostApprovalState != PartyApprovalState.PENDING
                && hostApprovalState != PartyApprovalState.DECLINED;
    }

    /**
     * Whether the proposal is holding inventory that a sweeper will eventually reclaim.
     *
     * @return {@code true} when a hold is attached
     */
    public boolean holdsInventory() {
        return inventoryHoldId != null;
    }
}
