package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * One receipt from moving a guest.
 *
 * <p>A taxi, a night in a hotel, a meal while waiting: each is a separate claim with its own evidence
 * and its own approval, because the case budget is spent in pieces and every piece has to be
 * justifiable to finance afterwards.</p>
 *
 * <p>Nothing may be approved for more than it was claimed for, and nothing may be marked reimbursed
 * without the refund instruction that moves the money.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("relocation_expenses")
public class RelocationExpense {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Case the expense belongs to. */
    private UUID relocationCaseId;
    /** Position within the case. */
    private short sequenceNumber;
    /** What the receipt is for. */
    private RelocationExpenseType expenseType;
    /** ISO 4217 code. */
    private String currency;
    /** What was claimed, in minor units. */
    private long claimedAmountMinor;
    /** What was approved. Never more than was claimed. */
    private long approvedAmountMinor;
    /** The receipt, held outside this row. */
    private @Nullable String receiptReference;
    /** Civil date the cost was incurred. */
    private LocalDate incurredOn;
    /** Kind of actor that submitted it. */
    private BookingActorType claimedByActorType;
    /** Which actor, where one is identifiable. */
    private @Nullable UUID claimedByActorId;
    /** Progress of the claim. */
    private RelocationExpenseState state;
    /** Who approved it. */
    private @Nullable UUID approvedByActorId;
    /** When they did. */
    private @Nullable Instant approvedAt;
    /** Why it was refused. */
    private @Nullable String rejectionReason;
    /** Refund instruction moving the money back. */
    private @Nullable UUID reimbursementInstructionId;
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
     * Whether the claim is still waiting for somebody to judge it.
     *
     * @return {@code true} while it is unapproved and unrejected
     */
    public boolean awaitsApproval() {
        return state == RelocationExpenseState.CLAIMED;
    }
}
