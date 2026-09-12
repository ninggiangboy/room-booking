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
 * A human-readable entry in the account of a booking.
 *
 * <p>Distinct from {@link BookingStateTransition}, which is the machine-readable history. This is
 * what a guest, a host, or an agent reads, and the two answer different questions.</p>
 *
 * <p>{@link #visibility} is mandatory and has no permissive default. Internal notes and guest-facing
 * updates share one table, and an entry whose audience is merely implied is how a support note ends
 * up rendered in a guest's itinerary.</p>
 *
 * <p>Text is stored as translation keys with parameters, not as rendered sentences, so the same
 * entry reads correctly in the guest's locale and the host's.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_timeline_entries")
public class BookingTimelineEntry {

    /** Primary key of the entry. */
    @Id
    private @Nullable UUID id;
    /** Booking the entry belongs to. */
    private UUID bookingId;
    /** Position in this booking's timeline; unique per booking. */
    private long sequenceNumber;
    /** Stable code for what happened. */
    private String entryCode;
    /** Who may see this entry. */
    private TimelineVisibility visibility;
    /** Translation key for the entry's heading. */
    private String titleKey;
    /** Translation key for its body. */
    private @Nullable String bodyKey;
    /** Values to interpolate into those keys. */
    private @Nullable JsonDocument parameters;
    /** Kind of actor that caused the entry. */
    private BookingActorType actorType;
    /** Identity of that actor, where it has one. */
    private @Nullable UUID actorId;
    /** UTC instant the entry describes, supplied by the caller's decision clock. */
    private Instant occurredAt;

    /**
     * Reports whether this entry may be shown to the booking's guest.
     *
     * @return {@code true} when the entry is addressed to the guest or to both parties
     */
    public boolean isVisibleToGuest() {
        return visibility == TimelineVisibility.GUEST || visibility == TimelineVisibility.BOTH;
    }

    /**
     * Reports whether this entry may be shown to the booking's host.
     *
     * @return {@code true} when the entry is addressed to the host or to both parties
     */
    public boolean isVisibleToHost() {
        return visibility == TimelineVisibility.HOST || visibility == TimelineVisibility.BOTH;
    }
}
