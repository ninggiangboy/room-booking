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
 * Everything a cancellation decision sets in motion that is not a refund.
 *
 * <p>Credit issued to a guest, money to be recovered from a host, goodwill the platform funds, a
 * promotion returned to its holder, a tax correction, a document to reissue, a payout to hold. They
 * share one table because they share one problem -- each must be produced exactly once from a decision
 * and handed to a domain that will act on it -- and differ only in which domain reads them.</p>
 *
 * <p>{@code targetReferenceId} is deliberately untyped: it names a row in another domain whose table
 * this one must not depend on.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("adjustment_instructions")
public class AdjustmentInstruction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Decision that produced the instruction. */
    private UUID cancellationDecisionId;
    /** Booking it concerns. */
    private UUID bookingId;
    /** What is being asked for. */
    private AdjustmentInstructionType adjustmentType;
    /** Position within the decision, for several of one type. */
    private short sequenceNumber;
    /** Which party the adjustment is against. */
    private MoneyPartyRole counterpartyRole;
    /** Who, where a specific holder is named. */
    private @Nullable UUID counterpartyAccountHolderId;
    /** ISO 4217 code. Travels with the amount or not at all. */
    private @Nullable String currency;
    /** Positive minor units, for the types that move money. */
    private @Nullable Long amountMinor;
    /** Stable reference the allocation arithmetic uses. */
    private @Nullable String allocationReference;
    /** Where the instructed amount came from. */
    private @Nullable String provenanceReference;
    /** Which domain is expected to act. */
    private AdjustmentTargetDomain targetDomain;
    /** Row that domain created in response, once it has. */
    private @Nullable UUID targetReferenceId;
    /** Progress of handing it over. */
    private AdjustmentInstructionState state;
    /** When it was sent. */
    private @Nullable Instant dispatchedAt;
    /** When the target domain confirmed it took it. */
    private @Nullable Instant acknowledgedAt;
    /** Why the last dispatch failed. */
    private @Nullable String failureReason;
    /** How many times dispatch has been tried. */
    private int attemptCount;
    /** Key the target domain deduplicates on. */
    private String downstreamIdempotencyKey;
    /** Correlation identifier for the work that wrote it. */
    private String correlationId;
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
     * Whether the instruction still needs dispatching.
     *
     * @return {@code true} while it is pending or retryable
     */
    public boolean needsDispatch() {
        return state == AdjustmentInstructionState.PENDING
                || state == AdjustmentInstructionState.FAILED;
    }
}
