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
 * A difference somebody owns until it is explained.
 *
 * <p>Resolution links the evidence and any approved correction. It never overwrites either side of the
 * comparison, and the matching engine never inserts a balancing entry purely to make a report agree:
 * a journal correction requires both a named correcting transaction and an approver, and so does a
 * write-off.</p>
 *
 * <p>A case may be reopened when contradictory evidence arrives. Its {@link #resolvedAt} is not cleared
 * when that happens -- the case genuinely was resolved once, and that history is part of what a later
 * reviewer needs.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("finance_reconciliation_cases")
public class FinanceReconciliationCase {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Short identifier finance and support can both quote. */
    private String publicId;
    /** What kind of difference it is about. */
    private FinanceCaseType caseType;
    /** How urgent it is. */
    private ExceptionSeverity severity;
    /** How much money is at stake under the approved policy. */
    private ExceptionSeverity materiality;
    /** Run that raised it. */
    private @Nullable UUID openedByRunId;
    /** Entity the difference sits in. */
    private UUID legalEntityId;
    /** Book the difference sits in. */
    private @Nullable UUID accountingBookId;
    /** Merchant account involved. */
    private @Nullable UUID providerAccountId;
    /** ISO 4217 code of the exposure. */
    private @Nullable String currency;
    /** Minor units at stake. */
    private @Nullable Long exposureAmountMinor;
    /** Booking involved. */
    private @Nullable UUID bookingId;
    /** Host whose money is affected. */
    private @Nullable UUID hostAccountHolderId;
    /** Payout involved. */
    private @Nullable UUID payoutInstructionId;
    /** Journal entry involved. */
    private @Nullable UUID ledgerTransactionId;
    /** External row involved. */
    private @Nullable UUID externalRecordId;
    /** How far it has got. */
    private FinanceCaseState state;
    /** Whether it stops the host being paid until resolved. */
    private boolean blocksPayout;
    /** Whether it stops the accounting period closing. */
    private boolean blocksPeriodClose;
    /** Person who owns it. */
    private @Nullable UUID assignedToActorId;
    /** UTC instant it is due under its service level. */
    private @Nullable Instant dueAt;
    /** The answer awaiting approval. */
    private @Nullable FinanceCaseResolution proposedResolution;
    /** The answer that was accepted. */
    private @Nullable FinanceCaseResolution resolution;
    /** Short explanation; redacted of sensitive identifiers. */
    private @Nullable String resolutionNote;
    /** Correcting entry posted, when the answer was a journal correction. */
    private @Nullable UUID correctionTransactionId;
    /** Approver, required for a correction or a write-off. */
    private @Nullable UUID approvedByActorId;
    /** Person who resolved it. */
    private @Nullable UUID resolvedByActorId;
    /** UTC instant it was resolved; survives a reopen. */
    private @Nullable Instant resolvedAt;
    /** How many times contradictory evidence reopened it. */
    private int reopenedCount;
    /** UTC instant it was raised. */
    private Instant openedAt;
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
     * Whether this case currently prevents money leaving.
     *
     * @return true while it blocks payout and is unresolved
     */
    public boolean isHoldingPayout() {
        return blocksPayout && state != FinanceCaseState.RESOLVED;
    }

    /**
     * Whether this case currently prevents an accounting period being closed.
     *
     * @return true while it blocks close and is unresolved
     */
    public boolean isBlockingClose() {
        return blocksPeriodClose && state != FinanceCaseState.RESOLVED;
    }
}
