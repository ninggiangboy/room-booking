package dev.ngb.backend.model;

import java.io.Serializable;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier pairing one consumer with one event.
 *
 * <p>{@code @EqualsAndHashCode} gives the key value semantics. The consumer's handler version is
 * deliberately not part of the key: a handler upgrade must not replay effects that already
 * happened, so a deliberate rebuild uses a new consumer name instead.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConsumerInboxReceiptId implements Serializable {

    /** Stable name of the consumer, such as {@code notification-dispatcher}. */
    private String consumerName;
    /** Identifier of the consumed {@link OutboxEvent}. */
    private UUID eventId;
}
