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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One external row compared against one internal operation, with the verdict.
 *
 * <p>The external row is kept by digest rather than re-fetched, so a later argument about what the
 * provider file said is settled from the record and not from a fresh download of a file that may
 * have changed since.</p>
 *
 * <p>Append-only: the table has no version and rejects updates and deletes by trigger. A revised
 * comparison is a new entry in a later run.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_reconciliation_entries")
public class PaymentReconciliationEntry {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Run that produced this comparison. */
    private UUID runId;
    /** Merchant account the row belongs to. */
    private UUID providerAccountId;
    /** Hex SHA-256 of the external row as read. */
    private String externalRowDigest;
    /** Provider object the row describes. */
    private @Nullable String providerObjectRef;
    /** Provider request key carried in the row, when it carries one. */
    private @Nullable String providerRequestKey;
    /** Internal operation matched; absent for an orphan. */
    private @Nullable UUID operationId;
    /** Verdict of the comparison. */
    private ReconciliationOutcome outcome;
    /** How much the difference matters. */
    private ExceptionSeverity materiality;
    /** ISO 4217 code of the compared amounts. */
    private @Nullable String currency;
    /** Minor units the provider reported. */
    private @Nullable Long externalAmountMinor;
    /** Minor units the platform believed. */
    private @Nullable Long internalAmountMinor;
    /** Signed difference between the two, in minor units. */
    private @Nullable Long differenceAmountMinor;
    /** UTC instant the provider says the row occurred. */
    private @Nullable Instant observedAt;
    /** UTC instant the comparison was made. */
    private Instant comparedAt;
    /** Case opened for this difference, when one was. */
    private @Nullable UUID caseId;

    /**
     * Whether this comparison needs a person.
     *
     * @return {@code true} for anything other than a clean match
     */
    public boolean isException() {
        return outcome != ReconciliationOutcome.MATCHED;
    }
}
