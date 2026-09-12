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
 * A governed interval a transaction posts into, and the evidence of how it was closed.
 *
 * <p>Two overlapping periods in one book would make "which period does this belong to" ambiguous at
 * close, so the database forbids the overlap outright with an exclusion constraint. A hard close is
 * the claim that the numbers are final and therefore carries the watermark, balance hash, and
 * approvals that made them final; a reopen records its own reason beside that rather than erasing
 * it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("accounting_periods")
public class AccountingPeriod {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Book the period belongs to. */
    private UUID accountingBookId;
    /** Finance-facing label such as {@code 2026-09}. */
    private String periodLabel;
    /** Half-open date range the period covers. */
    private StayRange periodRange;
    /** IANA zone the period boundaries are cut in. */
    private String periodTimezone;
    /** How firmly the period is closed. */
    private AccountingPeriodState state;
    /** How far source events had been consumed when the close ran. */
    private @Nullable Instant sourceWatermarkAt;
    /** Source facts still unposted at close. */
    private int unpostedSourceCount;
    /** Reconciliation exceptions still open at close. */
    private int openExceptionCount;
    /** SHA-256 of the closing balances, lowercase hex. */
    private @Nullable String balanceHash;
    /** Increments each time the period is closed again after a reopen. */
    private int closeVersion;
    /** UTC instant close controls started. */
    private @Nullable Instant softClosedAt;
    /** UTC instant the period was declared final. */
    private @Nullable Instant hardClosedAt;
    /** UTC instant it was reopened, when it was. */
    private @Nullable Instant reopenedAt;
    /** Actor who closed it. */
    private @Nullable UUID closedByActorId;
    /** Actor who approved the close. */
    private @Nullable UUID approvedByActorId;
    /** Why it was reopened. */
    private @Nullable String reopenReasonCode;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
