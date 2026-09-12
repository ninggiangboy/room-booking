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
 * A reconciliation difference somebody has to resolve, and the record of how.
 *
 * <p>A quarantined provider transaction blocks booking confirmation and dependent payout until it
 * is either matched by proof or returned. An orphan is never attached to a booking by resemblance,
 * and editing a provider reference until a report balances is not a resolution.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_reconciliation_cases")
public class PaymentReconciliationCase {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Reference the case is quoted by. */
    private String publicId;
    /** Merchant account involved. */
    private UUID providerAccountId;
    /** Run that raised it. */
    private @Nullable UUID openedByRunId;
    /** Internal operation involved, when one is known. */
    private @Nullable UUID operationId;
    /** Obligation involved, when one is known. */
    private @Nullable UUID obligationId;
    /** Booking involved, when one is known. */
    private @Nullable UUID bookingId;
    /** What kind of problem it is. */
    private ReconciliationCaseCategory category;
    /** How much it matters. */
    private ExceptionSeverity severity;
    /** Progress of the case. */
    private ReconciliationCaseStatus status;
    /** Whether the case holds a dependent payout. */
    private boolean blocksPayout;
    /** ISO 4217 code of the exposure. */
    private @Nullable String currency;
    /** Minor units at stake. */
    private @Nullable Long exposureAmountMinor;
    /** Agent working it. */
    private @Nullable UUID assignedToActorId;
    /** How it was closed. */
    private @Nullable ReconciliationResolution resolution;
    /** What the resolver found. */
    private @Nullable String resolutionNote;
    /** Audited command that corrected the record, when one did. */
    private @Nullable UUID repairOperationId;
    /** Actor that closed it. */
    private @Nullable UUID resolvedByActorId;
    /** UTC instant it was closed. */
    private @Nullable Instant resolvedAt;
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
     * Whether this case is still holding a payout back.
     *
     * @return {@code true} while it blocks payout and has not been closed
     */
    public boolean isHoldingPayout() {
        return blocksPayout && status != ReconciliationCaseStatus.RESOLVED
                && status != ReconciliationCaseStatus.WRITTEN_OFF;
    }
}
