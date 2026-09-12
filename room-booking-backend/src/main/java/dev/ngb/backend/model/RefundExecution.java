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
 * Carrying out a refund somebody else decided.
 *
 * <p>The entitlement, the amount, and the reason come from a cancellation, modification, or remedy
 * decision. This domain executes the exact instructed amount and never recalculates penalties,
 * host impact, platform fee retention, tax, or promotion reversal.</p>
 *
 * <p>{@link #refundInstructionId} is unique, so replaying an instruction cannot pay the guest
 * twice. It carries no foreign key yet: the instruction lives in the cancellation and modification
 * domain, which migration {@code 023} delivers and which adds the constraint forward.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("refund_executions")
public class RefundExecution {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Immutable instruction being executed. */
    private UUID refundInstructionId;
    /** Version of that instruction. */
    private int instructionVersion;
    /** Booking the refund belongs to. */
    private UUID bookingId;
    /** Obligation the money is being returned from. */
    private UUID obligationId;
    /** Account the money returns to. */
    private UUID beneficiaryAccountHolderId;
    /** Merchant account executing it. */
    private @Nullable UUID providerAccountId;
    /** ISO 4217 code, matching the obligation. */
    private String currency;
    /** Minor units the instruction approved. */
    private long approvedAmountMinor;
    /** Minor units currently held against eligible captures. */
    private long reservedAmountMinor;
    /** Minor units actually returned. */
    private long executedAmountMinor;
    /** Progress of the execution. */
    private RefundExecutionState state;
    /** Normalised reason it failed. */
    private @Nullable PaymentFailureCategory failureCategory;
    /** Provider code, for operations and support only. */
    private @Nullable String restrictedFailureCode;
    /** Why the refund was approved, copied from the instruction. */
    private String reasonCode;
    /** Version of the policy that decided it. */
    private @Nullable String policyVersion;
    /** Kind of actor that approved it. */
    private BookingActorType approvedByActorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID approvedByActorId;
    /** UTC instant it was approved. */
    private Instant approvedAt;
    /** UTC instant by which it should have moved. */
    private @Nullable Instant executionDeadlineAt;
    /** UTC instant it finished, successfully or finally not. */
    private @Nullable Instant completedAt;
    /** Key that makes resubmitting this execution safe. */
    private String idempotencyKey;
    /** Identifier following the whole booking saga. */
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
     * Minor units approved but neither reserved nor returned yet.
     *
     * @return unallocated amount in minor units
     */
    public long unallocatedMinor() {
        return approvedAmountMinor - reservedAmountMinor - executedAmountMinor;
    }

    /**
     * Whether the provider may still have acted on this refund.
     *
     * <p>While true the reservation stays held, because releasing it would let another refund
     * claim money this one may already have returned.</p>
     *
     * @return {@code true} while the outcome is not proven
     */
    public boolean hasUnprovenOutcome() {
        return state == RefundExecutionState.UNKNOWN
                || state == RefundExecutionState.PENDING
                || state == RefundExecutionState.SUBMITTING;
    }
}
