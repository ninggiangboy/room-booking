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
 * One comparison of what the platform believes against what a provider reports.
 *
 * <p>The absence of a webhook is not proof of absence at the provider, which is why recovery runs
 * exist alongside scheduled imports. The watermark is stored so a rerun covers the same period
 * deterministically, and the counts so "the import was short" is answerable without re-reading
 * the file.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_reconciliation_runs")
public class PaymentReconciliationRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Merchant account being reconciled. */
    private UUID providerAccountId;
    /** Why the run was started. */
    private ReconciliationRunType runType;
    /** Provider file or export the run read. */
    private @Nullable String sourceReference;
    /** UTC instant the covered period begins. */
    private Instant periodStart;
    /** UTC instant it ends. */
    private Instant periodEnd;
    /** Provider cursor the run resumed from. */
    private @Nullable String watermark;
    /** Progress of the run. */
    private ReconciliationRunState state;
    /** External rows read. */
    private int externalRowCount;
    /** Rows one internal operation accounted for. */
    private int matchedCount;
    /** Rows that did not match. */
    private int exceptionCount;
    /** UTC instant the run began. */
    private Instant startedAt;
    /** UTC instant it stopped. */
    private @Nullable Instant completedAt;
    /** Why it stopped without completing. */
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
