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
 * One human-readable step in what happened to a guest's money.
 *
 * <p>A projection written from committed facts, so answering "what happened to my money" never
 * means reading raw provider payloads or joining six tables under time pressure.</p>
 *
 * <p>Append-only, with a mandatory audience. An entry whose visibility is merely implied is how an
 * internal note ends up rendered to a guest.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_timeline_entries")
public class PaymentTimelineEntry {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Obligation the entry belongs to. */
    private UUID obligationId;
    /** Position in the obligation's timeline. */
    private int sequenceNumber;
    /** What happened. */
    private String entryType;
    /** Attempt the entry is about, when it is about one. */
    private @Nullable UUID attemptId;
    /** Operation the entry is about, when it is about one. */
    private @Nullable UUID operationId;
    /** Dispute the entry is about, when it is about one. */
    private @Nullable UUID disputeId;
    /** ISO 4217 code of any amount the entry mentions. */
    private @Nullable String currency;
    /** Minor units the entry mentions. */
    private @Nullable Long amountMinor;
    /** Stable code the rendered sentence is built from. */
    private String summaryCode;
    /** Additional human-readable detail. */
    private @Nullable String detail;
    /** Who may see the entry. */
    private TimelineVisibility visibility;
    /** UTC instant it happened. */
    private Instant occurredAt;
    /** UTC instant it was written. */
    private Instant recordedAt;
    /** Kind of actor responsible. */
    private BookingActorType actorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID actorId;

    /**
     * Whether the guest may see this entry.
     *
     * @return {@code true} for guest-facing and shared entries
     */
    public boolean isVisibleToGuest() {
        return visibility == TimelineVisibility.GUEST || visibility == TimelineVisibility.BOTH;
    }

    /**
     * Whether the host may see this entry.
     *
     * @return {@code true} for host-facing and shared entries
     */
    public boolean isVisibleToHost() {
        return visibility == TimelineVisibility.HOST || visibility == TimelineVisibility.BOTH;
    }
}
