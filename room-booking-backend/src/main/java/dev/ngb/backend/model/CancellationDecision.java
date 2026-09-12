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
 * One committed recalculation of a booking contract.
 *
 * <p>The row says what the contract became, under which rule, on whose authority, at which instant --
 * and nothing downstream may recalculate it. A mistake is corrected by a superseding decision that
 * names this one, never by an edit, because the guest was already told this number.</p>
 *
 * <p>Committing is checked twice. A row-level constraint caps what may be retained and refunded at what
 * the contract was worth; a deferred constraint trigger, firing at {@code COMMIT}, requires the
 * decision lines to sum to these totals in one currency.</p>
 *
 * <p>The decision is written draft, given its lines, and moved to committed inside one database
 * transaction, because nothing may be added to a committed decision.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("cancellation_decisions")
public class CancellationDecision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identifier safe to show a guest or an agent. */
    private String publicId;
    /** Booking being recalculated. */
    private UUID bookingId;
    /** Revision the decision was calculated against. */
    private UUID bookingRevisionId;
    /**
     * Revision the decision produced, where it produced one. Set once, on a committed row, and never
     * changed afterwards -- the revision cannot exist until the decision has been acted on.
     */
    private @Nullable UUID resultingRevisionId;
    /** What the decision did to the contract. */
    private CancellationDecisionType decisionType;
    /** The coarse class the funding rules are written against. */
    private CancellationCauseCategory causeCategory;
    /** Structured reason from the policy version's catalogue. */
    private String reasonCode;
    /** Kind of actor that decided. */
    private BookingActorType decidedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID decidedByActorId;
    /** Instant the decision takes effect from. */
    private Instant effectiveAt;
    /** The figure the guest accepted. Redeemable once. */
    private @Nullable UUID acceptedPreviewId;
    /** Terms the decision was settled under. */
    private UUID policyVersionId;
    /** Override that changed those terms for this booking. */
    private @Nullable UUID policyOverrideDecisionId;
    /** Evaluator build that produced the arithmetic. */
    private String evaluatorVersion;
    /** Tax calculation behind the tax effects. */
    private @Nullable UUID taxCalculationId;
    /** Version of the allocation the lines were computed under. */
    private int allocationVersion;
    /** SHA-256 over the canonical inputs, lowercase hex. */
    private String inputHash;
    /** SHA-256 over the result, lowercase hex. */
    private String resultHash;
    /** ISO 4217 code. Every line must match it. */
    private String currency;
    /** What the contract was worth before the decision. */
    private long originalAmountMinor;
    /** What the policy keeps. */
    private long retainedAmountMinor;
    /** What comes back to the guest. */
    private long refundAmountMinor;
    /** What the guest owes instead. Never non-zero beside a refund. */
    private long newDueAmountMinor;
    /** What the host is compensated, where the cause warrants it. */
    private long hostCompensationMinor;
    /** What the platform funds itself. */
    private long platformCostMinor;
    /** Lifecycle. Frozen once committed. */
    private CancellationDecisionStatus status;
    /** The decision this one corrects. */
    private @Nullable UUID supersedesDecisionId;
    /** The decision that corrected this one. */
    private @Nullable UUID supersededByDecisionId;
    /** Approval record, where the decision needed one. */
    private @Nullable String approvalReference;
    /** Evidence weighed, held outside this row. */
    private @Nullable String evidenceReference;
    /** Client key, unique within the booking, that a retry converges on. */
    private String idempotencyKey;
    /** Correlation identifier for the work that wrote it. */
    private String correlationId;
    /** When the decision became final. */
    private @Nullable Instant committedAt;
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
     * Whether the decision is final.
     *
     * @return {@code true} when it has been committed
     */
    public boolean isCommitted() {
        return status == CancellationDecisionStatus.COMMITTED;
    }

    /**
     * Whether the decision ends the booking rather than changing it.
     *
     * <p>At most one such decision may be live per booking, enforced by a partial unique index: a
     * second would release the same nights twice and found a second refund.</p>
     *
     * @return {@code true} for a full cancellation or a no-show
     */
    public boolean isTerminal() {
        return decisionType == CancellationDecisionType.FULL_CANCELLATION
                || decisionType == CancellationDecisionType.NO_SHOW;
    }

    /**
     * Whether the decision returns money to the guest.
     *
     * @return {@code true} when a refund is owed
     */
    public boolean owesRefund() {
        return refundAmountMinor > 0;
    }
}
