package dev.ngb.backend.stay.internal.model.instruction;

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
 * One version of the arrival instructions for a stay.
 *
 * <p>Fields are banded by sensitivity and released per band, so a guest can see the arrival window
 * long before the exact address and the access code later still. Nothing here is the content itself;
 * each band is a reference to material stored elsewhere.</p>
 *
 * <p>Released versions are frozen by trigger. A change is a superseding version, never an edit,
 * because the access audit points at a version whose meaning has to stay fixed.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("instruction_sets")
public class InstructionSet {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stay these instructions are for. */
    private UUID operationalStayId;
    /** Revision they were written against. */
    private UUID bookingRevisionId;
    /** Version within the stay, starting at one. */
    private int versionNumber;
    /** How far this version has progressed. */
    private InstructionSetStatus status;
    /** When it came into force. */
    private @Nullable Instant effectiveFrom;
    /** When it stopped being in force. */
    private @Nullable Instant effectiveUntil;
    /** Who wrote it. */
    private @Nullable UUID authoredByAccountHolderId;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountHolderId;
    /** When it was approved. */
    private @Nullable Instant approvedAt;
    /** Arrival window and preparation checklist. */
    private @Nullable String earlyFieldsReference;
    /** Approximate directions and house reminders. */
    private @Nullable String confirmedFieldsReference;
    /** Exact address, unit, entry route, meeting point. */
    private @Nullable String timeGatedFieldsReference;
    /** Envelope-encrypted access code or token material. */
    private @Nullable String secretFieldsReference;
    /** Appliances, parking, network, checkout details. */
    private @Nullable String postEntryFieldsReference;
    /** Hash of the whole version, for replay and diffing. */
    private String contentHash;
    /** Release condition: the booking is confirmed. */
    private boolean requiresConfirmedBooking;
    /** Release condition: the payment requirement is met. */
    private boolean requiresPaymentSatisfied;
    /** Release condition: identity or compliance is satisfied. */
    private boolean requiresIdentityVerified;
    /** Instant the time-gated band becomes retrievable. */
    private @Nullable Instant timeGateOpensAt;
    /** Version this one replaces. */
    private @Nullable UUID supersedesInstructionSetId;
    /** Why it was replaced. */
    private @Nullable String supersessionReason;
    /** Whether policy demands a guest-visible acknowledgement. */
    private boolean acknowledgementRequired;
    /** When retrieval was withdrawn. */
    private @Nullable Instant revokedAt;
    /** Why retrieval was withdrawn. */
    private @Nullable String revocationReason;
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
     * Whether this version is the one retrieval should evaluate.
     *
     * @return true while released
     */
    public boolean isCurrent() {
        return status == InstructionSetStatus.RELEASED;
    }

    /**
     * Whether the time-gated band has opened at the given instant.
     *
     * <p>Says nothing about the other release conditions, which are evaluated against
     * authoritative facts rather than against this row.</p>
     *
     * @param at instant to test
     * @return true when no gate is set, or the gate has passed
     */
    public boolean isTimeGateOpen(Instant at) {
        return timeGateOpensAt == null || !at.isBefore(timeGateOpensAt);
    }
}
