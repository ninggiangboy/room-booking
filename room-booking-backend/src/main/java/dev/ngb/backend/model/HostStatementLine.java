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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One line of a host statement, tracing to the fact that produced it.
 *
 * <p>Every line names a posting, a payout item, or a payable allocation. A line with no source behind
 * it is a plug, and the database refuses one.</p>
 *
 * <p>Amounts are unsigned with an explicit direction, the same rule the booking lines follow. Lines of
 * an issued statement cannot be added to, altered, or removed, so the row carries no optimistic lock
 * and no update timestamp.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_statement_lines")
public class HostStatementLine {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Statement the line belongs to. */
    private UUID statementId;
    /** Position on the statement, unique within it. */
    private short lineNumber;
    /** What the line represents. */
    private StatementLineType lineType;
    /** Whether it adds to or subtracts from the host's position. */
    private PostingDirection direction;
    /** Positive minor units. */
    private long amountMinor;
    /** ISO 4217 code, matching the statement. */
    private String currency;
    /** Message key for the host-facing description. */
    private @Nullable String descriptionKey;
    /** Structured reason, where the line type needs one. */
    private @Nullable String reasonCode;
    /** Journal posting the amount comes from. */
    private @Nullable UUID ledgerPostingId;
    /** Payout item the amount comes from. */
    private @Nullable UUID payoutItemId;
    /** Allocation the amount comes from. */
    private @Nullable UUID payableAllocationId;
    /** Booking the line relates to. */
    private @Nullable UUID bookingId;
    /** First day of the stay the line covers. */
    private @Nullable LocalDate servicePeriodStart;
    /** Last day of that stay. */
    private @Nullable LocalDate servicePeriodEnd;
    /** Invoice, credit note, or report this line appears on. */
    private @Nullable String documentReference;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
