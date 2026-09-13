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
 * An additive record that something previously published was wrong.
 *
 * <p>Corrections never edit history. One names either a single event or a bounded time range, so
 * that its scope is reviewable, and a privacy suppression removes without substituting.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("data_corrections")
public class DataCorrection {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What kind of claim this correction makes. */
    private DataCorrectionKind correctionKind;
    /** What the correction addresses. */
    private CorrectionTargetKind targetKind;
    /** The single event being corrected. */
    private @Nullable UUID correctsEventId;
    /** The dataset version whose partition is being corrected. */
    private @Nullable UUID targetDataProductId;
    /** Which partition of that dataset. */
    private @Nullable String targetPartitionKey;
    /** Start of the bounded source range, inclusive. */
    private @Nullable Instant sourceRangeStart;
    /** End of that range, exclusive; an unbounded correction is refused. */
    private @Nullable Instant sourceRangeEnd;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** What went wrong, in words a reviewer can weigh. */
    private String reasonDetail;
    /** The event that replaces the corrected one. */
    private @Nullable UUID replacementEventId;
    /** Where the replacement data lives. */
    private @Nullable String replacementReference;
    /** UTC instant the thing being corrected originally happened. */
    private @Nullable Instant originalOccurredAt;
    /** UTC instant the correction takes effect, which replays must keep distinct from arrival. */
    private Instant effectiveAt;
    /** Which version of the correction contract this row follows. */
    private short correctionSchemaVersion;
    /** Who or what recorded it. */
    private CorrectionActorKind actorKind;
    /** The person or process that recorded it. */
    private String actorReference;
    /** The independent approver; required for an invalidation and never the requester. */
    private @Nullable String approvedBy;
    /** Whether the correction has been carried out. */
    private CorrectionApplicationState applicationState;
    /** UTC instant applied. */
    private @Nullable Instant appliedAt;
    /** UTC instant the correction was recorded. */
    private Instant recordedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
