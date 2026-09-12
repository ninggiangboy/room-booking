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
 * What a host is told about a period or a payout, frozen at the moment they were told it.
 *
 * <p>An issued statement is immutable: its totals, its period, its hash, and its lines are all refused
 * further change by triggers. A correction is a new version that links back, so a host who saved a
 * copy and a host who reloads the page are looking at the same numbers.</p>
 *
 * <p>Totals are composed from {@code host_statement_lines}, each of which traces to a posting, a payout
 * item, or an allocation. They are not recomputed from today's fee, tax, cancellation, or FX
 * settings, which is what makes a two-year-old statement still reproducible.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_statements")
public class HostStatement {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Short identifier a host and support can both quote. */
    private String publicId;
    /** Host the statement is for. */
    private UUID hostAccountHolderId;
    /** Entity issuing it. */
    private UUID legalEntityId;
    /** Book the figures come from. */
    private UUID accountingBookId;
    /** ISO 4217 code; a statement never mixes currencies. */
    private String currency;
    /** Half-open date range the statement covers. */
    private StayRange periodRange;
    /** IANA zone those boundaries were cut in. */
    private String periodTimezone;
    /** Payout the statement accompanies, when it accompanies one. */
    private @Nullable UUID payoutInstructionId;
    /** Balance carried in from the previous period. */
    private long openingBalanceMinor;
    /** What the host earned before deductions. */
    private long grossEntitlementMinor;
    /** Commission, platform fees, and host-funded discounts. */
    private long deductionsMinor;
    /** Tax withheld under an approved authority decision. */
    private long withholdingMinor;
    /** Moved into reserves during the period. */
    private long reservedMinor;
    /** Stopped by holds at the close of the period. */
    private long heldMinor;
    /** Collected against debts the host owes. */
    private long recoveredMinor;
    /** Actually transferred. */
    private long paidOutMinor;
    /** Transferred and returned. */
    private long returnedMinor;
    /** Balance carried out to the next period. */
    private long closingBalanceMinor;
    /** Version within the same host, entity, currency, and period. */
    private int statementVersion;
    /** How far it has got, and whether it can still change. */
    private HostStatementStatus status;
    /** SHA-256 of the composed figures, lowercase hex; a failed render retries from it. */
    private String dataHash;
    /** Where the rendered document is retained. */
    private @Nullable String documentReference;
    /** UTC instant the figures were composed. */
    private @Nullable Instant generatedAt;
    /** UTC instant the host was shown it; survives a later correction. */
    private @Nullable Instant issuedAt;
    /** Corrected version that replaces it. */
    private @Nullable UUID supersededByStatementId;
    /** Why rendering failed, when it did. */
    private @Nullable String failureReasonCode;
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
     * Whether the statement is frozen.
     *
     * @return true once the host has been shown it
     */
    public boolean isIssued() {
        return issuedAt != null;
    }
}
