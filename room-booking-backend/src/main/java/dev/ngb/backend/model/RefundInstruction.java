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
 * The entitlement to have money returned.
 *
 * <p>This domain decides what is owed and to whom; the payment domain decides how it moves and whether
 * it arrived. The separation is the point: a refund a provider has not completed is still owed, and a
 * refund this domain cannot execute is still decided.</p>
 *
 * <p>{@code projectedExecutionState} exists so a guest-facing screen can say "sent to your bank"
 * without joining across domains. It is explicitly not authority -- payment owns
 * {@link RefundExecution}, and a write here can never make a refund successful.</p>
 *
 * <p>{@code instructionVersion} is the reissue counter. A refund whose destination failed is reissued
 * as a new version of the same instruction rather than as a second instruction, which is what makes the
 * payment domain's uniqueness on instruction and version meaningful.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("refund_instructions")
public class RefundInstruction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identifier safe to show a guest or an agent. */
    private String publicId;
    /** Which kind of decision entitled the beneficiary. */
    private RefundInstructionSource sourceDecisionType;
    /** The decision itself. Absent only for a support correction. */
    private @Nullable UUID cancellationDecisionId;
    /** Booking the refund belongs to. */
    private UUID bookingId;
    /** Revision the entitlement was calculated against. */
    private UUID bookingRevisionId;
    /** Version of the allocation behind the amount. */
    private int allocationVersion;
    /** Reissue counter. The payment domain keys executions on it. */
    private int instructionVersion;
    /** Position within the decision, for a split refund. */
    private short sequenceNumber;
    /** Which party is owed. */
    private MoneyPartyRole beneficiaryRole;
    /** Who is owed. */
    private UUID beneficiaryAccountHolderId;
    /** ISO 4217 code. */
    private String currency;
    /** Positive minor units owed. */
    private long amountMinor;
    /** Share of the amount the guest effectively funds. */
    private long guestFundedMinor;
    /** Share the host funds. */
    private long hostFundedMinor;
    /** Share the platform funds. */
    private long platformFundedMinor;
    /** Share a partner funds. */
    private long partnerFundedMinor;
    /** Tax calculation behind any tax effect. */
    private @Nullable UUID taxCalculationId;
    /** Journal entry the finance domain posted for it. */
    private @Nullable UUID ledgerTransactionId;
    /** Credit note or other document issued for it. */
    private @Nullable String documentReference;
    /** Structured reason shown to the beneficiary. */
    private String reasonCode;
    /** Terms the entitlement was calculated under. */
    private @Nullable UUID policyVersionId;
    /** Kind of actor that approved it. */
    private BookingActorType approvedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID approvedByActorId;
    /** When it was approved. */
    private Instant approvedAt;
    /** When the payment domain should have acted by. */
    private @Nullable Instant executionDeadlineAt;
    /** Whether the entitlement stands. */
    private RefundInstructionState state;
    /** Last state the payment domain reported. A projection for display, never authority. */
    private @Nullable RefundExecutionState projectedExecutionState;
    /** When that projection was observed. */
    private @Nullable Instant projectionObservedAt;
    /** When the entitlement was withdrawn. */
    private @Nullable Instant cancelledAt;
    /** Why it was withdrawn. */
    private @Nullable String cancellationReason;
    /** Key the payment domain deduplicates on. */
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
     * Whether the instruction may still be withdrawn.
     *
     * <p>Once an execution has accepted it the money may already be moving, so the remedy is a new
     * instruction rather than a withdrawal of this one.</p>
     *
     * @return {@code true} while no execution has taken it
     */
    public boolean isWithdrawable() {
        return state == RefundInstructionState.ISSUED;
    }

    /**
     * Whether the payment domain has reported the whole amount returned.
     *
     * @return {@code true} when the entitlement is settled
     */
    public boolean isSettled() {
        return state == RefundInstructionState.SETTLED;
    }
}
