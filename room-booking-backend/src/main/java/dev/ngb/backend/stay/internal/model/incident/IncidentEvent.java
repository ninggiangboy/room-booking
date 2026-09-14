package dev.ngb.backend.stay.internal.model.incident;

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
import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.TimelineVisibility;

import dev.ngb.backend.platform.ActorType;
import dev.ngb.backend.platform.TimelineVisibility;


/**
 * One entry in an incident timeline.
 *
 * <p>Append-only, on a number the incident itself issued. A trigger refuses a sequence the allocator
 * never handed out, because a gap or a reordering here changes what the record says about how fast
 * anybody responded.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("incident_events")
public class IncidentEvent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Incident this belongs to. */
    private UUID incidentId;
    /** Position in the timeline. */
    private long sequenceNumber;
    /** What the entry records. */
    private IncidentEventType eventType;
    /** State before, for a transition. */
    private @Nullable String previousState;
    /** State after, for a transition. */
    private @Nullable String newState;
    /** Severity before, for a severity change. */
    private @Nullable String previousSeverity;
    /** Severity after, for a severity change. */
    private @Nullable String newSeverity;
    /** Kind of actor that acted. */
    private ActorType actorType;
    /** Which account holder acted. */
    private @Nullable UUID actorAccountHolderId;
    /** Why. Required for a severity change. */
    private @Nullable String reasonCode;
    /** Reference to note text held elsewhere. */
    private @Nullable String noteReference;
    /** Who may see this entry. */
    private TimelineVisibility visibility;
    /** Kind of thing it points at. */
    private @Nullable IncidentEventReferenceType relatedReferenceType;
    /** Which one. */
    private @Nullable UUID relatedReferenceId;
    /** What it did to the response clock. */
    private SloClockEffect sloClockEffect;
    /** Incident version the actor expected. */
    private @Nullable Long incidentVersionBefore;
    /** Hash of the entry content. */
    private @Nullable String contentHash;
    /** When it happened. */
    private Instant occurredAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
