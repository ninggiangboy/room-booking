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
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A domain fact committed in the same transaction as the state change it describes.
 *
 * <p>Writing the fact transactionally is what makes publication reliable: there is no window in
 * which the state changed but the fact was lost, and none in which a fact was published for a
 * change that rolled back.</p>
 *
 * <p>The payload is immutable after commit. {@link #publicationState}, {@link #attemptCount}, and
 * the lease fields are operational state that says nothing about what happened; setters exist for
 * them because the publisher advances them, not because the event can be rewritten.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("outbox_events")
public class OutboxEvent {

    /** Immutable event identifier, quoted by consumers in their receipts. */
    @Id
    private @Nullable UUID id;
    /** Name of the fact, such as {@code BookingConfirmed}. */
    private String eventName;
    /** Version of the payload schema for this event name. */
    private short schemaVersion;
    /** Kind of aggregate the fact concerns. */
    private String aggregateType;
    /** Identifier of that aggregate. */
    private UUID aggregateId;
    /** Aggregate version at the moment the fact was recorded, giving consumers an ordering. */
    private long aggregateVersion;
    /** UTC instant the fact became true in the domain. */
    private Instant occurredAt;
    /** UTC instant the row was written; never earlier than {@link #occurredAt}. */
    private Instant recordedAt;
    /** ISO 3166-1 alpha-2 market the fact belongs to, where the domain is market-scoped. */
    private @Nullable String marketCode;
    /** Correlation identifier tying this fact to its originating journey. */
    private String correlationId;
    /** Identifier of the command or fact that caused this one. */
    private @Nullable String causationId;
    /** Kind of principal responsible for the fact. */
    private @Nullable ActorType actorType;
    /** Account identifier or approved pseudonymous reference for that principal. */
    private @Nullable String actorReference;
    /** Media type of {@link #payload}, normally {@code application/json}. */
    private String contentType;
    /** Handling class governing which consumers may receive the payload. */
    private SensitivityClass sensitivityClass;
    /** Immutable JSON payload of the fact. */
    private JsonDocument payload;
    /** Deterministic business key that makes re-recording the same fact a no-op. */
    private @Nullable String deduplicationKey;
    /** Publication progress; operational state only. */
    private OutboxPublicationState publicationState;
    /** UTC instant from which the row may be claimed, used to back off after a failure. */
    private Instant availableAt;
    /** Number of publication attempts made so far. */
    private int attemptCount;
    /** Publisher instance holding the claim, or {@code null} when unclaimed. */
    private @Nullable String leaseOwner;
    /** UTC instant at which the claim lapses and another publisher may take the row. */
    private @Nullable Instant leaseExpiresAt;
    /** UTC instant the fact reached the transport. */
    private @Nullable Instant publishedAt;
    /** Stable classification of the last failure; never a raw provider message. */
    private @Nullable String lastErrorClass;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
