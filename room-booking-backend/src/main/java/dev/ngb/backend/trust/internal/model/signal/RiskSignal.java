package dev.ngb.backend.trust.internal.model.signal;

import java.math.BigDecimal;
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
import dev.ngb.backend.platform.SensitivityClass;

/**
 * One atomic observation, with its provenance attached.
 *
 * <p>Append-only: a correction is a new row naming the row it corrects, because rewriting an
 * observation makes every decision taken on it unexplainable afterwards. The deduplication identity
 * is the source that produced it rather than its value, so a redelivered fact cannot increment a
 * velocity counter twice, and both the event time and the arrival time are kept so a point-in-time
 * replay can exclude what had not arrived yet.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_signals")
public class RiskSignal {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What was observed. */
    private String signalType;
    /** Version of the signal contract this row was written against. */
    private int schemaVersion;
    /** Where it came from; part of the deduplication identity. */
    private RiskSignalSourceDomain sourceDomain;
    /** Provider account behind a vendor observation; part of the deduplication identity. */
    private @Nullable UUID providerAccountId;
    /** The source system's own identifier for it; part of the deduplication identity. */
    private String sourceRecordId;
    /** What kind of thing this is on the evidence quality ladder. */
    private SignalProvenance provenance;
    /** Which value column below actually carries the observation. */
    private SignalValueType valueType;
    /** Normalized boolean or categorical value. */
    private @Nullable String normalizedValue;
    /** Numeric measurement. */
    private @Nullable BigDecimal numericValue;
    /** Money amount in minor units. */
    private @Nullable Long amountMinor;
    /** ISO 4217 currency for the amount. */
    private @Nullable String currency;
    /** Pointer to protected detail held elsewhere; never the detail itself. */
    private @Nullable String protectedDetailReference;
    /**
     * How much weight the observation may be given.
     *
     * <p>An allegation may never be recorded as verified, which is a check constraint rather than a
     * convention.</p>
     */
    private SignalConfidenceClass confidenceClass;
    /** Optional numeric confidence between zero and one. */
    private @Nullable BigDecimal confidenceScore;
    /** The declared purpose this was collected for. */
    private String collectionPurpose;
    /** Market scope of the collection. */
    private @Nullable UUID marketId;
    /** Legal entity under whose basis it was collected. */
    private @Nullable UUID legalEntityId;
    /** How restricted the observation is. */
    private SensitivityClass sensitivityClass;
    /** How long it may be kept, and why. */
    private RiskRetentionClass retentionClass;
    /** When the observed thing happened. */
    private Instant eventTime;
    /** When the platform learned of it. */
    private Instant receivedAt;
    /** When it must be discarded; required for transient observations. */
    private @Nullable Instant expiresAt;
    /** The observation this one corrects, where it corrects one. */
    private @Nullable UUID correctsSignalId;
    /** Why the earlier observation was wrong. */
    private @Nullable String correctionReason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
