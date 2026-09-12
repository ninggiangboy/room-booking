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
 * Something the provider said about a dispute, in the order it was learned.
 *
 * <p>Append-only, because the history of a case is itself evidence in the case. The table has no
 * version and rejects updates and deletes by trigger.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_dispute_events")
public class PaymentDisputeEvent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Case this event belongs to. */
    private UUID disputeId;
    /** Position in the case history. */
    private int sequenceNumber;
    /** What happened. */
    private String eventType;
    /** Status before the event, when it moved one. */
    private @Nullable DisputeStatus fromStatus;
    /** Status after the event, when it moved one. */
    private @Nullable DisputeStatus toStatus;
    /** ISO 4217 code of any amount the event carried. */
    private @Nullable String currency;
    /** Minor units the event carried. */
    private @Nullable Long amountMinor;
    /** Provider event identifier, when it came from one. */
    private @Nullable String providerEventId;
    /** Hex SHA-256 of the provider payload behind it. */
    private @Nullable String payloadDigest;
    /** UTC instant it happened. */
    private Instant occurredAt;
    /** UTC instant the platform learned of it. */
    private Instant recordedAt;
    /** Kind of actor responsible. */
    private BookingActorType actorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID actorId;
    /** Human-readable detail. */
    private @Nullable String note;
}
