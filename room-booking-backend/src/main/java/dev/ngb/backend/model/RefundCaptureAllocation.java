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
 * Which capture one part of a refund comes out of.
 *
 * <p>Money can only be returned through a capture that actually took it, and the same capture must
 * not fund two refunds beyond what it holds. The allocation is a row rather than an arithmetic
 * assumption, so the refundable ceiling can be computed under a lock.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("refund_capture_allocations")
public class RefundCaptureAllocation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Refund this allocation belongs to. */
    private UUID refundExecutionId;
    /** Successful capture the money is drawn from. */
    private UUID captureOperationId;
    /** Refund operation created for this allocation. */
    private @Nullable UUID refundOperationId;
    /** Deterministic order children are created in. */
    private short sequenceNumber;
    /** ISO 4217 code, matching the capture. */
    private String currency;
    /** Minor units claimed from the capture. */
    private long allocatedAmountMinor;
    /** Minor units actually returned through it. */
    private long settledAmountMinor;
    /** Progress of this allocation. */
    private RefundAllocationState state;
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
     * Whether this allocation is still holding money against its capture.
     *
     * @return {@code true} while the reservation counts against the refundable ceiling
     */
    public boolean isHoldingCapture() {
        return switch (state) {
            case RESERVED, SUBMITTED, SETTLED -> true;
            case RELEASED, FAILED -> false;
        };
    }
}
