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
 * One movement into or out of a reserve, naming what it came from or went to.
 *
 * <p>Every subtraction from a host's available balance names an item. A reserve that is only a number
 * on a profile cannot be explained, appealed, or unwound, so money entering a reserve always cites
 * the payable allocation it came from.</p>
 *
 * <p>Append-only, and therefore carries no optimistic lock or update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_reserve_allocations")
public class HostReserveAllocation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Reserve the movement belongs to. */
    private UUID hostReserveId;
    /** Allocation funding it; required when money is being held. */
    private @Nullable UUID payableAllocationId;
    /** Journal posting that expressed the movement, where policy requires one. */
    private @Nullable UUID ledgerPostingId;
    /** What happened to the amount. */
    private ReserveMovement movement;
    /** Positive minor units moved. */
    private long amountMinor;
    /** ISO 4217 code, matching the reserve. */
    private String currency;
    /** Why the movement happened. */
    private @Nullable String reasonCode;
    /** UTC instant it happened. */
    private Instant occurredAt;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
