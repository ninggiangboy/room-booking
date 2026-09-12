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
 * What the provider or the bank actually said about a transfer, kept verbatim.
 *
 * <p>Append-only. A returned transfer does not turn its settlement into a non-event; it is a later
 * observation that funds a recovery. A stale webhook is stored and marked ignored, never deleted.</p>
 *
 * <p>{@link #payoutOperationId} is nullable because evidence sometimes arrives for a transfer the
 * platform cannot yet place. That orphan is exactly the row reconciliation needs to keep, so the
 * database allows it only when the reducer says so -- an observation claiming to have been applied
 * with no operation behind it is a defect and is refused.</p>
 *
 * <p>A webhook is only evidence when its signature was checked against a named key version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payout_observations")
public class PayoutObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Operation the evidence belongs to; absent for an orphan. */
    private @Nullable UUID payoutOperationId;
    /** Instruction it appears to concern. */
    private @Nullable UUID payoutInstructionId;
    /** Merchant account it came from. */
    private UUID providerAccountId;
    /** How it reached the platform. */
    private PayoutObservationSource source;
    /** Provider event identity; unique per account, so a redelivery is one row. */
    private @Nullable String providerEventId;
    /** Provider's identifier for the transfer. */
    private @Nullable String providerReference;
    /** What it says, in platform vocabulary. */
    private NormalizedPayoutState normalizedState;
    /** What the reducer did with it. */
    private ReducerOutcome reducerOutcome;
    /** Minor units the evidence names. */
    private @Nullable Long amountMinor;
    /** Fee the provider charged, where the evidence carries one. */
    private @Nullable Long feeAmountMinor;
    /** ISO 4217 code; present whenever an amount is. */
    private @Nullable String currency;
    /** Provider reason code, kept verbatim. */
    private @Nullable String reasonCode;
    /** How the evidence was authenticated. */
    private FinanceVerificationMethod verificationMethod;
    /** Key version a webhook signature was checked against. */
    private @Nullable String signingKeyVersion;
    /** SHA-256 of the raw payload, lowercase hex. */
    private String payloadDigest;
    /** Where the raw payload is retained. */
    private @Nullable String payloadReference;
    /** UTC instant the provider says it happened. */
    private @Nullable Instant providerOccurredAt;
    /** UTC instant the platform received it. */
    private Instant observedAt;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this observation actually moved the platform's view of the transfer.
     *
     * @return true only when the reducer applied it
     */
    public boolean wasApplied() {
        return reducerOutcome == ReducerOutcome.APPLIED;
    }
}
