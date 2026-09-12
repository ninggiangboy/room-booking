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
 * A bounded, disclosed retention of host money under an approved policy.
 *
 * <p>A reserve is not an unbounded flag. It has a basis, a target, a cap, a maturity, and a disclosure
 * reference, and the database refuses to hold more than the cap or to release more than it holds.
 * Risk models may recommend a review within approved bounds, but a deterministic policy owns the
 * amount and the action.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_reserves")
public class HostReserve {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Host whose money is retained. */
    private UUID hostAccountHolderId;
    /** Entity holding it. */
    private UUID legalEntityId;
    /** Book the reserve is accounted in. */
    private UUID accountingBookId;
    /** ISO 4217 code. */
    private String currency;
    /** Why the reserve exists. */
    private HostReserveBasis reserveBasis;
    /** Policy version that set the target and cap. */
    private @Nullable UUID policyVersionId;
    /** Minor units the policy wants held. */
    private long targetAmountMinor;
    /** Most that may ever be held; what makes the reserve bounded. */
    private long capAmountMinor;
    /** Minor units currently held. */
    private long heldAmountMinor;
    /** Minor units returned to the host. */
    private long releasedAmountMinor;
    /** Minor units used to fund a recovery. */
    private long consumedAmountMinor;
    /** How far the reserve has run. */
    private HostReserveState state;
    /** UTC instant it started. */
    private Instant effectiveFrom;
    /** UTC instant it begins releasing. */
    private @Nullable Instant maturesAt;
    /** UTC instant it finished. */
    private @Nullable Instant closedAt;
    /** Reference to what the host was told about it. */
    private @Nullable String disclosureReference;
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
     * Minor units the reserve may still take before reaching its cap.
     *
     * @return remaining headroom, never negative
     */
    public long headroomMinor() {
        return Math.max(0L, capAmountMinor - heldAmountMinor);
    }

    /**
     * Minor units still held and not yet released or consumed.
     *
     * @return the live balance of the reserve
     */
    public long outstandingMinor() {
        return heldAmountMinor - releasedAmountMinor - consumedAmountMinor;
    }
}
