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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One step of the recovery waterfall, with the amount it consumed.
 *
 * <p>The step number is stored so a statement can show that the reserve was used before future earnings
 * were touched. That ordering is the difference between a contractual offset and an unexplained
 * deduction.</p>
 *
 * <p>Each method names the thing it consumed: a reserve step names its reserve, a payable offset names
 * its allocation, an authorised debit names the obligation it collects through. No step can take
 * money from nowhere.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_recovery_allocations")
public class HostRecoveryAllocation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Recovery the step belongs to. */
    private UUID hostRecoveryId;
    /** Position in the waterfall, one through six, unique within the recovery. */
    private short stepNumber;
    /** Which step of the waterfall this is. */
    private HostRecoveryMethod method;
    /** Positive minor units this step took. */
    private long amountMinor;
    /** ISO 4217 code, matching the recovery. */
    private String currency;
    /** Allocation consumed, for a payable offset. */
    private @Nullable UUID payableAllocationId;
    /** Reserve consumed, for a reserve step. */
    private @Nullable UUID hostReserveId;
    /** Journal posting expressing the step. */
    private @Nullable UUID ledgerPostingId;
    /** Obligation raised against the host, for an authorised debit. */
    private @Nullable UUID collectionObligationId;
    /** Whether the step actually took effect. */
    private HostRecoveryAllocationState state;
    /** Waterfall policy version that chose it. */
    private @Nullable UUID policyVersionId;
    /** UTC instant it was applied. */
    private Instant occurredAt;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
