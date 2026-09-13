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
 * One entry of the case read projection, not a system of record.
 *
 * <p>Each entry names the source that is authoritative for it and the version it was read at. The
 * watermark and completeness columns exist so a missing event shows as a known gap.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_timeline_entries")
public class CaseTimelineEntry {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Which version of the schema applies. */
    private int schemaVersion;
    /** Which entry kind this row carries. */
    private String entryKind;
    /** Source domain. */
    private TimelineSourceDomain sourceDomain;
    /** Which source object type this row carries. */
    private String sourceObjectType;
    /** The source object this row belongs to. */
    private @Nullable UUID sourceObjectId;
    /** Reference to the source object, held in its owning system rather than copied here. */
    private @Nullable String sourceObjectReference;
    /** Which version of the source object applies. */
    private @Nullable Long sourceObjectVersion;
    /** The source event this row belongs to. */
    private @Nullable UUID sourceEventId;
    /** Which actor class this row carries. */
    private TimelineActorClass actorClass;
    /** The actor account holder this row belongs to. */
    private @Nullable UUID actorAccountHolderId;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** UTC instant event. */
    private Instant eventAt;
    /** UTC instant received. */
    private Instant receivedAt;
    /** UTC instant committed. */
    private Instant committedAt;
    /** Deterministic ordering for equal event times; it claims no causal order. */
    private long tieBreaker;
    /** Which visibility scope this row carries. */
    private CaseTimelineVisibility visibilityScope;
    /** Stable key naming the redaction policy. */
    private @Nullable String redactionPolicyKey;
    /** Stable key naming the summary template. */
    private String summaryTemplateKey;
    /** The entry this one corrects. */
    private @Nullable UUID supersedesEntryId;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String correctionReason;
    /** How fresh the projection is, so stale data cannot drive execution. */
    private Instant projectionWatermarkAt;
    /** Whether anything is known to be missing from this entry. */
    private boolean projectionComplete;
    /** What is missing, when the projection knows it is incomplete. */
    private @Nullable String incompletenessReason;
    /** The correlation this row belongs to. */
    private @Nullable UUID correlationId;
    /** The causation this row belongs to. */
    private @Nullable UUID causationId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
