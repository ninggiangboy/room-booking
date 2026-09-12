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
 * Who the parties to a booking were at the time it was made.
 *
 * <p>Contact details are referenced rather than copied. A booking that duplicated email addresses
 * and phone numbers would become a second, stale store of personal data that erasure and correction
 * requests never reach, so {@link #contactChannelId} points at the managed record instead.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("booking_party_snapshots")
public class BookingPartySnapshot {

    /** Primary key of the snapshot. */
    @Id
    private @Nullable UUID id;
    /** Booking the party appears on. */
    private UUID bookingId;
    /** Capacity in which they appear; at most one row per role per booking. */
    private BookingPartyRole partyRole;
    /** Account holder the party is. */
    private UUID accountHolderId;
    /** Name as it stood at booking time, so a later rename does not rewrite the contract. */
    private String displayName;
    /** Locale the party preferred, for correspondence about this booking. */
    private @Nullable String preferredLocale;
    /** Managed contact record to reach them through; never a copy of the address itself. */
    private @Nullable UUID contactChannelId;
    /** Organization the party acted for, when acting in a business capacity. */
    private @Nullable UUID organizationId;
    /** UTC instant the snapshot was taken, supplied by the caller's decision clock. */
    private Instant capturedAt;
}
