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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One normalized row out of an artifact, stored so matching runs against the database.
 *
 * <p>Migration {@code 021} stores only a digest of a provider row, because it needs to know only
 * whether it saw that row. Reconciling against the ledger has to compare amounts, fees, and
 * timestamps, so the normalized row itself is kept.</p>
 *
 * <p>{@link #matchState} is the only field that may change after ingestion. Everything else is frozen,
 * because a row whose amount can be edited proves nothing at all.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_financial_records")
public class ExternalFinancialRecord {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Artifact the row came from. */
    private UUID artifactId;
    /** Position within that artifact. */
    private @Nullable Integer nativeRowNumber;
    /** The source's own identifier for the row. */
    private @Nullable String nativeReference;
    /** What the row describes. */
    private ExternalRecordType recordType;
    /** Minor units the row names. */
    private long amountMinor;
    /** Fee the source charged on it. */
    private @Nullable Long feeAmountMinor;
    /** ISO 4217 code. */
    private String currency;
    /** The source's own status string, kept verbatim. */
    private @Nullable String nativeStatus;
    /** That status in platform vocabulary. */
    private ExternalRecordStatus normalizedStatus;
    /** UTC instant the source says the movement happened. */
    private @Nullable Instant occurredAt;
    /** UTC instant it became available, where the source distinguishes the two. */
    private @Nullable Instant availableAt;
    /** Provider object the row points at. */
    private @Nullable String providerObjectRef;
    /** Platform key the source echoed back, when it did. */
    private @Nullable String platformReference;
    /** Settlement or payout batch the row belongs to. */
    private @Nullable String settlementBatchReference;
    /** Safe display text for the other side of the movement. */
    private @Nullable String counterpartyDisplay;
    /** SHA-256 of the normalized row, lowercase hex; unique within the artifact. */
    private String rowHash;
    /** Whether the row has been accounted for. */
    private ExternalRecordMatchState matchState;

    /** UTC instant the row was written. */
    @CreatedDate
    private Instant createdAt;
}
