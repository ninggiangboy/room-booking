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
 * One recorded change to a listing.
 *
 * <p>The history exists for disputes. A guest arguing "the listing said there was air conditioning"
 * needs the listing as it stood on the day they booked, not as it stands now — and a host editing a
 * listing after a complaint should not be able to make the original claim disappear.</p>
 *
 * <p>There is no {@code @Version} and no {@code updatedAt}: the row records that something happened
 * and is never revised.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_change_history")
public class ListingChangeRecord {

    /** Primary key of the history row. */
    @Id
    private @Nullable UUID id;
    /** Listing that changed. */
    private UUID listingId;
    /** Kind of change. */
    private ListingChangeType changeType;
    /** Which field changed, where the change was to one field. */
    private @Nullable String fieldPath;
    /** SHA-256 digest of the prior value. */
    private @Nullable String beforeDigest;
    /** SHA-256 digest of the resulting value. */
    private @Nullable String afterDigest;
    /** Minimal structured summary of what changed. */
    private @Nullable JsonDocument changeSummary;
    /** Kind of principal that made the change. */
    private ActorType actorType;
    /** Identifier of that principal. */
    private @Nullable UUID actorId;
    /** Stable reason for the change, where one applies. */
    private @Nullable String reasonCode;
    /** UTC instant the change happened. */
    private Instant occurredAt;
}
