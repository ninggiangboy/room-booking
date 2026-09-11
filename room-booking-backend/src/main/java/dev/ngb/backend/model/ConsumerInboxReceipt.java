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
 * Proof that one consumer applied one event's effect exactly once.
 *
 * <p>The receipt is inserted and completed in the same transaction as the durable effect it
 * records. That is what makes redelivery harmless: a transport that delivers the same event twice
 * finds a completed receipt and does nothing, and a transaction that rolls back takes its receipt
 * with it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("consumer_inbox_receipts")
public class ConsumerInboxReceipt {

    /** Composite consumer-and-event primary key. */
    @Id
    private ConsumerInboxReceiptId id;
    /** Handler version that processed the event, recorded for diagnostics only. */
    private short consumerVersion;
    /** Payload schema version the handler interpreted. */
    private short schemaVersion;
    /** UTC instant the consumer first saw the event. */
    private Instant firstSeenAt;
    /** Processing progress of this receipt. */
    private InboxReceiptState state;
    /** Number of processing attempts made so far. */
    private int attemptCount;
    /** Consumer instance holding the claim, or {@code null} when unclaimed. */
    private @Nullable String leaseOwner;
    /** UTC instant at which the claim lapses and another instance may take the event. */
    private @Nullable Instant leaseExpiresAt;
    /** UTC instant the receipt reached a terminal state. */
    private @Nullable Instant completedAt;
    /** Kind of durable effect the handler produced, where one exists. */
    private @Nullable String effectType;
    /** Identifier of that effect; paired with {@link #effectType}. */
    private @Nullable UUID effectId;
    /** Stable classification of the last failure; never a raw exception message. */
    private @Nullable String failureClass;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
