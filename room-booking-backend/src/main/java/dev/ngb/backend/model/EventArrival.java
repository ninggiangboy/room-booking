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
 * One envelope that reached the collector, accepted or not.
 *
 * <p>Append-only, and deliberately without the payload: the body lives in restricted landing
 * storage and this row carries its digest, its size and a reference. A rejected or quarantined
 * arrival is kept because an unregistered producer or a skewed client clock is exactly the evidence
 * worth having.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("event_arrivals")
public class EventArrival {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Envelope identity, unique across everything not recorded as a duplicate. */
    private UUID eventId;
    /** The registered contract this arrival cites, absent when nobody registered one. */
    private @Nullable UUID eventDefinitionId;
    /** Event name as the envelope carried it. */
    private String eventName;
    /** Schema version as the envelope carried it. */
    private short schemaVersion;
    /** Which event class this row carries. */
    private EventClass eventClass;
    /** The component that sent it. */
    private String producer;
    /** Which environment produced it, so test traffic can be excluded rather than guessed at. */
    private ProducerEnvironment producerEnvironment;
    /** Which source kind this row carries. */
    private ArrivalSourceKind sourceKind;
    /** Client application that sent it, for client-collected evidence. */
    private @Nullable String originApplication;
    /** Version of that client application. */
    private @Nullable String applicationVersion;
    /** UTC instant the fact or observation happened. */
    private Instant occurredAt;
    /** UTC instant the source transaction committed; only change-data-capture has one. */
    private @Nullable Instant committedAt;
    /** UTC instant the collector accepted or refused it. */
    private Instant receivedAt;
    /** UTC instant the analytical layer persisted it; only accepted arrivals reach it. */
    private @Nullable Instant ingestedAt;
    /** Kind of aggregate the fact is about. */
    private @Nullable String aggregateType;
    /** The aggregate the fact is about. */
    private @Nullable UUID aggregateId;
    /** Version of that aggregate, which establishes local source order. */
    private @Nullable Long aggregateVersion;
    /** Opaque identifier for one business journey. */
    private @Nullable String correlationId;
    /** Opaque identifier of whatever caused this event. */
    private @Nullable String causationId;
    /** Pseudonymous subject the arrival concerns, never a real identifier. */
    private @Nullable String subjectPseudonym;
    /** ISO 3166-1 alpha-2 market the arrival belongs to. */
    private @Nullable String marketCode;
    /** Digest of the body, which lives in restricted landing storage rather than here. */
    private String payloadDigest;
    /** Size of that body in bytes, bounded so an oversized envelope is detectable. */
    private int payloadByteSize;
    /** Where the body is stored; this row deliberately does not hold it. */
    private @Nullable String payloadReference;
    /** What the collector decided about this arrival. */
    private ArrivalValidationState validationState;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String rejectionReason;
    /** The earlier arrival this one repeats. */
    private @Nullable UUID duplicateOfArrivalId;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String quarantineReason;
    /** Enough detail to debug the record, and no more. */
    private @Nullable String quarantineDetail;
    /** Which retention horizon applies to this arrival. */
    private ArrivalRetentionClass retentionClass;
    /** UTC instant retention may remove it. */
    private Instant expiresAt;
    /** UTC instant it was suppressed under a subject request. */
    private @Nullable Instant suppressedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
