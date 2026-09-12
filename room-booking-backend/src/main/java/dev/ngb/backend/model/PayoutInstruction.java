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
 * A durable intent to move an exact amount to one host destination.
 *
 * <p>The amount is fixed when the instruction is planned and the provider adapter cannot recompute it.
 * That is what stops a retry from quietly paying out newly eligible money the approval never
 * covered, and it is why the adapter submits the stored amount, currency, destination, and
 * idempotency key rather than re-running the planner.</p>
 *
 * <p>The same submission fence a payment crosses applies here. An instruction that reached the provider
 * can never be marked cancelled -- the database refuses it -- because the provider may have paid it
 * after the platform stopped waiting. A terminal failure must say what kind of failure it was, and a
 * timeout leaves the instruction submitted with no settlement, a state that is queried rather than
 * retried.</p>
 *
 * <p>Its payout items must sum exactly to {@link #amountMinor}. That is a cross-row rule, so it is
 * enforced by a deferred constraint trigger at {@code COMMIT} rather than by any single check.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payout_instructions")
public class PayoutInstruction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Short identifier a host and support can both quote. */
    private String publicId;
    /** Host being paid. */
    private UUID hostAccountHolderId;
    /** Book the payout is accounted in. */
    private UUID accountingBookId;
    /** Entity sending the money. */
    private UUID legalEntityId;
    /** Verified destination from migration {@code 015}. */
    private UUID destinationClaimId;
    /** Merchant account the transfer goes through. */
    private @Nullable UUID providerAccountId;
    /** Positive minor units to send; never zero or negative. */
    private long amountMinor;
    /** ISO 4217 code. */
    private String currency;
    /** Payment rail the transfer uses. */
    private String railKey;
    /** Schedule policy version that produced the run. */
    private @Nullable UUID schedulePolicyVersionId;
    /** Payout policy version that set thresholds and fees. */
    private @Nullable UUID payoutPolicyVersionId;
    /** UTC instant of the cutoff the run was planned against. */
    private @Nullable Instant scheduleCutoffAt;
    /** IANA zone that cutoff was interpreted in. */
    private @Nullable String scheduleTimezone;
    /** SHA-256 of the canonical planning input, lowercase hex. */
    private String requestInputHash;
    /** Platform-side key making the planning request replay-safe. */
    private String idempotencyKey;
    /** Key the provider deduplicates on; reused on every retry. */
    private @Nullable String providerRequestKey;
    /** Provider's own identifier for the transfer. */
    private @Nullable String providerReference;
    /** How far the transfer has got. */
    private PayoutInstructionState state;
    /** Worker currently holding the instruction. */
    private @Nullable String leaseOwner;
    /** UTC instant that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** Monotonic token; a crashed worker's late result cannot overwrite a newer attempt. */
    private long fencingToken;
    /** How many times submission has been attempted. */
    private int attemptCount;
    /** UTC instant the next attempt is due. */
    private @Nullable Instant nextAttemptAt;
    /** UTC instant it crossed to the provider; once set, cancellation is impossible. */
    private @Nullable Instant submittedAt;
    /** UTC instant the verified finality condition was met. */
    private @Nullable Instant settledAt;
    /** UTC instant it terminally failed. */
    private @Nullable Instant failedAt;
    /** UTC instant the receiving bank sent it back. */
    private @Nullable Instant returnedAt;
    /** UTC instant it was abandoned before submission. */
    private @Nullable Instant cancelledAt;
    /** What kind of failure, and therefore what may be done about it. */
    private @Nullable PayoutFailureClass failureClass;
    /** Structured reason behind that classification. */
    private @Nullable String failureReasonCode;
    /** Why the transfer was returned. */
    private @Nullable String returnReasonCode;
    /** Replacement instruction, only once this one is proven terminal. */
    private @Nullable UUID reissuedAsInstructionId;
    /** Statement this payout appears on. */
    private @Nullable UUID statementId;
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
     * Whether the instruction has reached the provider.
     *
     * <p>Past this point the outcome can only be established by evidence. A timeout is not a failure,
     * and a second instruction must never be created while this one is unresolved.</p>
     *
     * @return true once submission happened
     */
    public boolean hasCrossedSubmissionFence() {
        return submittedAt != null;
    }

    /**
     * Whether the money is known to have arrived.
     *
     * @return true only for the verified finality condition, never for provider acceptance
     */
    public boolean isPaid() {
        return settledAt != null && state == PayoutInstructionState.PAID;
    }

    /**
     * Whether this instruction still blocks a replacement being created.
     *
     * <p>An unresolved transfer holds the host's money hostage to evidence: reissuing before it is
     * proven terminal is how a host is paid twice.</p>
     *
     * @return true while the outcome is unknown or still moving
     */
    public boolean blocksReissue() {
        return state == PayoutInstructionState.SUBMITTING
                || state == PayoutInstructionState.SUBMITTED
                || state == PayoutInstructionState.IN_TRANSIT
                || state == PayoutInstructionState.PAID
                || state == PayoutInstructionState.MANUAL_REVIEW;
    }
}
