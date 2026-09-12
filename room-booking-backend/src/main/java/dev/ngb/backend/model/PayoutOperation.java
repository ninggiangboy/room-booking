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
 * One attempt to make the payout provider do something.
 *
 * <p>Separated from the instruction for the same reason a payment operation is separated from an
 * obligation: one durable intent produces many external calls, whose outcomes arrive late, out of
 * order, and sometimes twice. Collapsing them is what makes retries unsafe.</p>
 *
 * <p>A query moves no money and carries no amount. Everything else does. The submission fence applies
 * here exactly as it does to payments: only an operation that never left can be abandoned outright,
 * and a timeout resolves to an unknown state that is queried rather than resent.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payout_operations")
public class PayoutOperation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Instruction the call is for. */
    private UUID payoutInstructionId;
    /** Merchant account the call goes through. */
    private @Nullable UUID providerAccountId;
    /** What the provider is being asked to do. */
    private PayoutOperationType operationType;
    /** Which attempt of this type this is. */
    private int attemptNumber;
    /** Positive minor units, absent only for a query. */
    private @Nullable Long amountMinor;
    /** ISO 4217 code, absent only for a query. */
    private @Nullable String currency;
    /** Key the provider deduplicates on. */
    private @Nullable String providerRequestKey;
    /** Provider's own identifier for the result. */
    private @Nullable String providerReference;
    /** SHA-256 of the canonical request, lowercase hex. */
    private @Nullable String requestDigest;
    /** How far the call has got. */
    private PayoutOperationState state;
    /** What the provider last said, in platform vocabulary. */
    private @Nullable NormalizedPayoutState normalizedProviderState;
    /** Category of failure; required whenever the call failed. */
    private @Nullable String failureCategory;
    /** The provider's own code, kept verbatim. */
    private @Nullable String providerFailureCode;
    /** Worker currently holding the operation. */
    private @Nullable String leaseOwner;
    /** UTC instant that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** Monotonic token guarding against a stale worker's result. */
    private long fencingToken;
    /** UTC instant the next attempt is due. */
    private @Nullable Instant nextAttemptAt;
    /** UTC instant it crossed to the provider. */
    private @Nullable Instant submittedAt;
    /** UTC instant a terminal outcome was established. */
    private @Nullable Instant resolvedAt;
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
     * Whether this call has reached the provider.
     *
     * @return true once submission happened
     */
    public boolean hasCrossedSubmissionFence() {
        return submittedAt != null;
    }

    /**
     * Whether the outcome must be established by querying rather than by trying again.
     *
     * @return true when the call left the platform and no outcome is proven
     */
    public boolean needsOutcomeQuery() {
        return submittedAt != null && resolvedAt == null;
    }
}
