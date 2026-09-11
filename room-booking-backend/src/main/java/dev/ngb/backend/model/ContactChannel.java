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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One way of reaching a principal, and what is known about whether it really is theirs.
 *
 * <p>Exclusivity follows proof rather than claim. Two accounts may each register the same address —
 * people mistype, and an attacker should not be able to squat an address by claiming it first — but
 * only a *verified primary* channel is unique across accounts, so proving control is what confers
 * the exclusive right to it.</p>
 *
 * <p>Purpose is separate from type so a guest can withdraw marketing consent without losing the
 * booking confirmations they still need. Replaced channels are superseded rather than deleted, which
 * keeps the trail of where a notification was actually delivered.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("contact_channels")
public class ContactChannel {

    /** Primary key of the channel. */
    @Id
    private @Nullable UUID id;
    /** Principal the channel belongs to. */
    private UUID userId;
    /** Kind of channel. */
    private ContactChannelType channelType;
    /** Canonical form used for comparison and uniqueness; case-insensitive in the database. */
    private String normalizedValue;
    /** Value as the owner entered it, retained where policy allows. */
    private @Nullable String originalValue;
    /** What the channel is used for. */
    private ContactChannelPurpose purpose;
    /** Whether this is the channel of record for its type. */
    private boolean isPrimary;
    /** UTC instant control of the channel was proven; {@code null} while unverified. */
    private @Nullable Instant verifiedAt;
    /** How it was proven; paired with {@link #verifiedAt}. */
    private @Nullable String verificationMethod;
    /** Channel that replaced this one. */
    private @Nullable UUID supersededBy;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether control of this channel has been proven.
     *
     * @return {@code true} when the channel carries a verification instant
     */
    public boolean isVerified() {
        return verifiedAt != null;
    }
}
