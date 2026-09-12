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
 * The single thing that consumes inventory.
 *
 * <p>A hold, a booking, a host block, and an imported external reservation are all rows in this one
 * table. That is deliberate and load-bearing: if each lived in its own storage, nothing would stop a
 * host blocking a night a guest is simultaneously holding, because the database can only refuse a
 * conflict it can see in one place.</p>
 *
 * <p>For an exclusive resource, the {@code ex_inventory_claims_no_overlap} GiST exclusion constraint
 * refuses a second {@code ACTIVE} claim whose range overlaps an existing one — including across
 * concurrent transactions, which is exactly what application-level checking cannot do. Released and
 * expired claims fall outside that predicate, so a cancelled stay frees its nights immediately.</p>
 *
 * <p>{@link #resourceType} is a copy of the resource's own type, kept honest by a composite foreign
 * key. It exists because PostgreSQL forbids a subquery in an index predicate, so the constraint has
 * to know locally which defence applies.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("inventory_claims")
public class InventoryClaim {

    /** Primary key of the claim. */
    @Id
    private @Nullable UUID id;
    /** Resource whose nights are being consumed. */
    private UUID inventoryResourceId;
    /** Copy of that resource's type, so the exclusion constraint can be evaluated locally. */
    private InventoryResourceType resourceType;
    /** Nights consumed, half-open so a checkout date is the next guest's check-in date. */
    private StayRange stayRange;
    /** What is consuming the nights. */
    private ClaimType claimType;
    /** Whether the claim still consumes them. */
    private ClaimStatus status;
    /** How many of a pooled resource are consumed; always one for an exclusive resource. */
    private int quantity;
    /** Hold this claim belongs to, for a temporary claim. */
    private @Nullable UUID holdId;
    /** Booking this claim belongs to, once confirmed. */
    private @Nullable UUID bookingId;
    /** Block this claim belongs to, when nights are withheld. */
    private @Nullable UUID blockId;
    /** External reservation this claim represents, when imported. */
    private @Nullable UUID externalReservationId;
    /** UTC instant a temporary claim lapses; present only for a hold. */
    private @Nullable Instant expiresAt;
    /** UTC instant the claim stopped consuming nights. */
    private @Nullable Instant releasedAt;
    /** Stable reason it stopped; paired with {@link #releasedAt}. */
    private @Nullable String releaseReason;
    /** Monotonic token letting a late writer detect it is acting on a superseded decision. */
    private long fencingToken;
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
     * Reports whether this claim is currently consuming its nights.
     *
     * @return {@code true} while the claim is active
     */
    public boolean isConsuming() {
        return status == ClaimStatus.ACTIVE;
    }

    /**
     * Reports whether a temporary claim has lapsed at the supplied instant.
     *
     * <p>A lapsed hold has not necessarily been released yet: expiry is a fact about time, release is
     * a write. Equality on the deadline means expired, matching the platform's deadline convention.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the claim is active, temporary, and past its expiry
     */
    public boolean hasLapsedAt(Instant instant) {
        return status == ClaimStatus.ACTIVE
                && expiresAt != null
                && !expiresAt.isAfter(instant);
    }
}
