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
 * One movement of one of a booking's state dimensions.
 *
 * <p>Append-only, enforced by a database trigger: a mistaken transition is answered with a
 * compensating one, never by editing the original. This is the record a dispute is reconstructed
 * from, and a history that can be rewritten reconstructs nothing.</p>
 *
 * <p>{@link #dimension} is recorded explicitly so that a payment reaching {@code CAPTURED} and a stay
 * reaching {@code CHECKED_IN} read as different kinds of event rather than as two rows whose meaning
 * depends on knowing which machine wrote them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_state_transitions")
public class BookingStateTransition {

    /** Primary key of the transition. */
    @Id
    private @Nullable UUID id;
    /** Booking whose state moved. */
    private UUID bookingId;
    /** Position in this booking's history; unique per booking and strictly increasing. */
    private long sequenceNumber;
    /** Which dimension moved. */
    private BookingStateDimension dimension;
    /** State left behind; absent only for the first transition of a dimension. */
    private @Nullable String fromState;
    /** State arrived at; never equal to {@link #fromState}. */
    private String toState;
    /** Stable code naming the command or event that caused the move. */
    private String transitionCode;
    /** Kind of actor responsible. */
    private BookingActorType actorType;
    /** Identity of that actor, where it has one. */
    private @Nullable UUID actorId;
    /** Stable reason for the move, for policy and reporting. */
    private @Nullable String reasonCode;
    /** Command that produced this transition, for idempotent replay. */
    private @Nullable String commandId;
    /** Identifier tying this move to the wider request it belonged to. */
    private String correlationId;
    /** Identifier of the event that directly caused this one. */
    private @Nullable String causationId;
    /** Additional context, kept as an immutable snapshot rather than as queryable data. */
    private @Nullable JsonDocument metadata;
    /** UTC instant the move happened, supplied by the caller's decision clock. */
    private Instant occurredAt;
}
