package dev.ngb.backend.stay.internal.model.stay;

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
import dev.ngb.backend.platform.RetentionClass;

import dev.ngb.backend.stay.internal.model.EvidenceConfidence;
import dev.ngb.backend.stay.internal.model.OperationalEvidenceSource;

/**
 * A typed observation about what happened during a stay.
 *
 * <p>Clients submit observations; they never set an outcome. Both times are kept, because the gap
 * between them is what tells a reviewer that a device reported an arrival four hours late rather than
 * that the guest arrived late.</p>
 *
 * <p>Frozen at insert except for retention and legal hold: a correction is a further observation.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("stay_observations")
public class StayObservation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stay observed. */
    private UUID operationalStayId;
    /** Revision in force when it was observed. */
    private UUID bookingRevisionId;
    /** What the observation is about. */
    private StayObservationSubject subject;
    /** How it was made. */
    private StayObservationType observationType;
    /** Where it came from. */
    private OperationalEvidenceSource source;
    /** Who reported it, where a person did. */
    private @Nullable UUID reportedByAccountHolderId;
    /** When the thing observed happened. */
    private Instant eventTime;
    /** When the platform learned of it. */
    private Instant receivedAt;
    /** Zone the source expressed its own time in. */
    private @Nullable String sourceTimeZone;
    /** How much weight it can carry. */
    private EvidenceConfidence confidence;
    /** Reservations about the reading. */
    private String[] qualityFlags;
    /** Provider that produced it. */
    private @Nullable UUID providerAccountId;
    /** Provider-native identity, used for deduplication. */
    private @Nullable String providerReference;
    /** Access event it was derived from. */
    private @Nullable UUID accessObservationId;
    /** Reference to the immutable payload. */
    private @Nullable String payloadReference;
    /** Hash of that payload. */
    private @Nullable String payloadHash;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether deletion is barred. */
    private boolean legalHold;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether a person put their name to this observation.
     *
     * @return true when an account holder reported it
     */
    public boolean isAttested() {
        return reportedByAccountHolderId != null;
    }

    /**
     * Lag between the thing happening and the platform hearing about it, in seconds.
     *
     * <p>Negative values are possible and meaningful: they say the source clock disagrees with
     * ours, which is itself a reason to weigh the evidence less.</p>
     *
     * @return seconds between event time and receipt
     */
    public long reportingLagSeconds() {
        return receivedAt.getEpochSecond() - eventTime.getEpochSecond();
    }
}
