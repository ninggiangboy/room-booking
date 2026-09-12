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
 * One bounded comparison: one control layer, one account, one currency, one coverage interval.
 *
 * <p>The layer is stored on the run because a green payment report proves nothing about whether the
 * bank agrees with the ledger. One aggregate total at one layer must never be allowed to stand in
 * for another, and each layer carries its own identifiers, timing tolerance, and owner.</p>
 *
 * <p>Distinct from migration {@code 021}'s payment reconciliation, which compares a provider's payment
 * report against payment operations. This side compares external artifacts against the journal, the
 * clearing accounts, and the payouts.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("finance_reconciliation_runs")
public class FinanceReconciliationRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which pair of facts the run compares. */
    private ReconciliationControlLayer controlLayer;
    /** Book the internal side comes from. */
    private @Nullable UUID accountingBookId;
    /** Entity the run is scoped to. */
    private UUID legalEntityId;
    /** Merchant account the run is scoped to. */
    private @Nullable UUID providerAccountId;
    /** Control account being reconciled, where the layer names one. */
    private @Nullable UUID ledgerAccountId;
    /** External evidence the run read. */
    private @Nullable UUID artifactId;
    /** ISO 4217 code; a run never mixes currencies. */
    private String currency;
    /** UTC instant the covered interval begins. */
    private Instant coverageStart;
    /** UTC instant it ends. */
    private Instant coverageEnd;
    /** IANA zone both sides interpret their cutoff in. */
    private String cutoffTimezone;
    /** Matching rule version the run applied. */
    private String ruleVersion;
    /** Materiality policy version the run applied. */
    private String materialityVersion;
    /** How far internal facts had been consumed when the run started. */
    private @Nullable Instant inputWatermarkAt;
    /** SHA-256 of the canonical inputs, lowercase hex, so a rerun is deterministic. */
    private @Nullable String inputHash;
    /** How far the run has got. */
    private ReconciliationRunState state;
    /** Internal facts considered. */
    private int internalRecordCount;
    /** External rows considered. */
    private int externalRecordCount;
    /** Comparisons that agreed. */
    private int matchedCount;
    /** Comparisons that did not. */
    private int exceptionCount;
    /** Minor units accounted for. */
    private long matchedAmountMinor;
    /** Minor units still unexplained. */
    private long differenceAmountMinor;
    /** UTC instant the run began. */
    private Instant startedAt;
    /** UTC instant it finished. */
    private @Nullable Instant completedAt;
    /** Why the run itself failed, when it did. */
    private @Nullable String failureClass;
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
