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
 * One external side effect: authorize, capture, sale, void, refund, or query.
 *
 * <p>This is the auditable record of what was actually asked of a provider. A transport retry
 * reuses this row and its {@link #providerRequestKey}; a new financial intent gets a new row and a
 * new key, because that key is what the provider deduplicates on.</p>
 *
 * <p>{@link PaymentOperationState#FAILED} requires evidence that the effect did not and cannot
 * occur. A timeout is {@link PaymentOperationState#UNKNOWN}, and an unknown operation is never
 * replaced by a second one — that is how a guest gets charged twice.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_operations")
public class PaymentOperation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Guest journey this operation belongs to; absent for recovery work. */
    private @Nullable UUID attemptId;
    /** Obligation the operation acts on. */
    private UUID obligationId;
    /** Authorisation a capture or void acts on, or the refund it is a child of. */
    private @Nullable UUID parentOperationId;
    /** The external action requested. */
    private PaymentOperationType operationType;
    /** ISO 4217 code; absent for a status query. */
    private @Nullable String currency;
    /**
     * Minor units the operation moves; absent for a status query.
     *
     * <p>Always positive. {@link #operationType} is what says which way the money moves.</p>
     */
    private @Nullable Long amountMinor;
    /** Namespace the internal idempotency key is unique within. */
    private String idempotencyScope;
    /** Internal key identifying this intent. */
    private String idempotencyKey;
    /** Hex SHA-256 of the canonical request, so a reused key with different input is caught. */
    private String requestHash;
    /** Merchant account the request goes to. */
    private UUID providerAccountId;
    /**
     * Key the provider deduplicates on, unique per account.
     *
     * <p>Every safe transport retry reuses it, which is what makes a retry incapable of becoming a
     * second charge.</p>
     */
    private String providerRequestKey;
    /** Provider identifier for this operation, once it answers. */
    private @Nullable String providerOperationRef;
    /** Provider identifier for the object the operation acted on. */
    private @Nullable String providerObjectRef;
    /** Progress of the request. */
    private PaymentOperationState state;
    /** UTC instant the state last moved. */
    private Instant stateChangedAt;
    /** Times the request has crossed to the provider. */
    private int submissionCount;
    /** UTC instant a worker should next act on it. */
    private @Nullable Instant nextAttemptAt;
    /** UTC instant after which it may no longer be pursued. */
    private @Nullable Instant deadlineAt;
    /** Worker currently holding the submission lease. */
    private @Nullable String leaseOwner;
    /** UTC instant that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /**
     * Monotonic token a worker must still hold to submit.
     *
     * <p>A late worker carrying a stale token cannot submit, which is what stops two workers paying
     * the same provider request twice.</p>
     */
    private long fencingToken;
    /** Safe normalised result the provider returned. */
    private @Nullable String resultCode;
    /** Normalised reason it failed. */
    private @Nullable PaymentFailureCategory failureCategory;
    /** Provider code, for operations and support only. */
    private @Nullable String restrictedFailureCode;
    /** Identifier following the whole booking saga. */
    private String correlationId;
    /** Identifier of the command or event that caused this operation. */
    private @Nullable String causationId;
    /** Kind of actor that requested it. */
    private BookingActorType actorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID actorId;
    /** UTC instant it first crossed to the provider. */
    private @Nullable Instant submittedAt;
    /** UTC instant it reached a terminal state. */
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
     * Whether the request has crossed to the provider.
     *
     * <p>Past this point the operation can only be resolved by evidence. Cancelling it locally
     * would be a claim the platform cannot support, and the database refuses to store one.</p>
     *
     * @return {@code true} once it has been submitted at least once
     */
    public boolean hasCrossedSubmissionFence() {
        return submittedAt != null;
    }

    /**
     * Whether a replacement operation for the same intent would be unsafe.
     *
     * @return {@code true} while the provider may still have acted on this request
     */
    public boolean blocksReplacement() {
        return switch (state) {
            case SUBMITTING, PENDING, REQUIRES_ACTION, UNKNOWN -> true;
            case PLANNED, READY_TO_SUBMIT, SUCCEEDED, FAILED, EXPIRED,
                    CANCELLED_BEFORE_SUBMISSION -> false;
        };
    }

    /**
     * Whether this operation collected money.
     *
     * @return {@code true} for a succeeded capture or sale
     */
    public boolean isSuccessfulCapture() {
        return state == PaymentOperationState.SUCCEEDED
                && (operationType == PaymentOperationType.CAPTURE
                        || operationType == PaymentOperationType.SALE);
    }
}
