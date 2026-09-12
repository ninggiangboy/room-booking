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
 * Money stopped from leaving, without any change to whose money it is.
 *
 * <p>That distinction is the whole point of the table. A dispute, a compliance gap, or a cooling-off
 * period makes an amount unavailable; none of them make it the platform's revenue.</p>
 *
 * <p>A hold is replaced rather than deleted, so the reason money was ever held stays on the record and
 * a host can be told why a payout was late. A capped hold names its currency; an uncapped one holds
 * whatever its scope contains.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payout_holds")
public class PayoutHold {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which domain placed it, so it can be routed back to its owner. */
    private PayoutHoldIssuer issuerDomain;
    /** Why money is being stopped. */
    private PayoutHoldType holdType;
    /** Host whose money is affected. */
    private UUID hostAccountHolderId;
    /** Booking the hold is scoped to, when it is. */
    private @Nullable UUID bookingId;
    /** Allocation the hold is scoped to, when it is. */
    private @Nullable UUID payableAllocationId;
    /** Payout the hold is scoped to, when it is. */
    private @Nullable UUID payoutInstructionId;
    /** ISO 4217 code, present exactly when the hold is capped. */
    private @Nullable String currency;
    /** Most it can hold, when it is capped. */
    private @Nullable Long maximumAmountMinor;
    /** Structured internal reason. */
    private String reasonCode;
    /** Message key for what the host may safely be told. */
    private @Nullable String publicReasonKey;
    /** Policy version that authorised it. */
    private @Nullable UUID policyVersionId;
    /** Reference to the evidence behind it. */
    private @Nullable String evidenceReference;
    /** Whether it still stops money. */
    private PayoutHoldState state;
    /** UTC instant it started holding. */
    private Instant effectiveFrom;
    /** UTC instant somebody must look at it again. */
    private @Nullable Instant reviewDueAt;
    /** UTC instant it lapses on its own. */
    private @Nullable Instant expiresAt;
    /** UTC instant it stopped holding. */
    private @Nullable Instant releasedAt;
    /** Actor who lifted it. */
    private @Nullable UUID releasedByActorId;
    /** Why it was lifted. */
    private @Nullable String releaseReasonCode;
    /** Hold that superseded it. */
    private @Nullable UUID replacedByHoldId;
    /** Actor who placed it. */
    private @Nullable UUID placedByActorId;
    /** Approval that authorised it, where one was required. */
    private @Nullable String approvalReference;
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
     * Whether this hold is still stopping money at a given instant.
     *
     * <p>Expiry is evaluated against the caller's instant rather than the database clock, so that a
     * single eligibility decision reads one moment for every hold it considers.</p>
     *
     * @param at instant being evaluated
     * @return true when the hold is active and has not lapsed
     */
    public boolean isBlockingAt(Instant at) {
        return state == PayoutHoldState.ACTIVE
                && !effectiveFrom.isAfter(at)
                && (expiresAt == null || expiresAt.isAfter(at));
    }
}
