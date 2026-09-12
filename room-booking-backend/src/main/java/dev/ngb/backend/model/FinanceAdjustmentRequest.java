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
 * A proposed manual correction, drawn from an allowlisted catalog.
 *
 * <p>There is no free-form posting endpoint. The type decides which accounts and dimensions are legal,
 * what the amount bounds are, how many approvals are needed, and which posting rule runs.</p>
 *
 * <p>{@link #requestHash} is what approval is bound to. Editing the amount after approval changes the
 * hash and invalidates the approval rather than silently carrying it forward, and the database
 * refuses to let a request reach {@code APPROVED} with fewer approvals than its own type demands.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("finance_adjustment_requests")
public class FinanceAdjustmentRequest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Short identifier finance can quote. */
    private String publicId;
    /** Which catalog entry is being proposed. */
    private FinanceAdjustmentType adjustmentType;
    /** Book the correction posts into. */
    private UUID accountingBookId;
    /** Entity it affects. */
    private UUID legalEntityId;
    /** ISO 4217 code. */
    private String currency;
    /** Positive minor units. */
    private long amountMinor;
    /** Kind of fact being corrected. */
    private String sourceType;
    /** Identity of that fact. */
    private UUID sourceId;
    /** Host affected, when one is. */
    private @Nullable UUID hostAccountHolderId;
    /** Booking affected, when one is. */
    private @Nullable UUID bookingId;
    /** Case that prompted the correction. */
    private @Nullable UUID reconciliationCaseId;
    /** Rule version that will produce the entries. */
    private @Nullable UUID postingRuleVersionId;
    /** SHA-256 of the canonical request, lowercase hex; approval is bound to it. */
    private String requestHash;
    /** Structured reason from the approved taxonomy. */
    private String reasonCode;
    /** Free-text explanation; supplemental and redacted of sensitive data. */
    private @Nullable String justification;
    /** Reference to the evidence behind it. */
    private @Nullable String evidenceReference;
    /** How many approvals this type demands. */
    private short requiresApprovals;
    /** How many have been recorded. */
    private short approvalCount;
    /** How far it has travelled through maker-checker. */
    private FinanceAdjustmentStatus status;
    /** Person who proposed it; may not also approve it. */
    private UUID proposedByActorId;
    /** UTC instant it was submitted for approval. */
    private @Nullable Instant submittedAt;
    /** UTC instant it was approved or refused. */
    private @Nullable Instant decidedAt;
    /** Journal entry it produced. */
    private @Nullable UUID postedTransactionId;
    /** Why it was refused. */
    private @Nullable String rejectionCode;
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
     * Whether the request has gathered every approval its type demands.
     *
     * @return true when the approval count meets the threshold
     */
    public boolean isFullyApproved() {
        return approvalCount >= requiresApprovals;
    }
}
