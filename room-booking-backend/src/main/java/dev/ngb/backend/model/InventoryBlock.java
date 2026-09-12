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
 * Why nights are unavailable when nobody has booked them.
 *
 * <p>{@link #sourceType} decides the rules rather than merely describing them. An iCal import may
 * replace the blocks a previous run of that same import created, but it must never delete a block a
 * host made by hand — and without the source recorded there is no way to tell them apart. The
 * uniqueness on external identity is scoped by source for exactly that reason.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("inventory_blocks")
public class InventoryBlock {

    /** Primary key of the block. */
    @Id
    private @Nullable UUID id;
    /** Resource whose nights are withheld. */
    private UUID inventoryResourceId;
    /** Nights withheld, half-open. */
    private StayRange stayRange;
    /** How many of a pooled resource are withheld. */
    private int quantity;
    /** Who or what withheld them. */
    private BlockSource sourceType;
    /** Reference to the originating system or record. */
    private @Nullable String sourceReference;
    /** Identifier the external calendar gave this event. */
    private @Nullable String externalUid;
    /** Whether the block still withholds the nights. */
    private BlockStatus status;
    /** Stable reason the nights are withheld. */
    private @Nullable String reasonCode;
    /** Host-facing note, never shown to guests. */
    private @Nullable String privateNote;
    /** Kind of principal that created the block. */
    private ActorType actorType;
    /** Identifier of that principal. */
    private @Nullable UUID actorId;
    /** UTC instant the block was lifted or superseded. */
    private @Nullable Instant releasedAt;
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
     * Reports whether a calendar import is entitled to replace this block.
     *
     * <p>Only blocks an import created may be replaced by a later run of that import. A host's own
     * block survives every sync, because a host who blocks a week for themselves should not find it
     * silently removed by a feed.</p>
     *
     * @param importingSource source of the import attempting the replacement
     * @return {@code true} when the block belongs to that same import source
     */
    public boolean isReplaceableBy(BlockSource importingSource) {
        return sourceType == importingSource
                && (sourceType == BlockSource.ICAL || sourceType == BlockSource.CHANNEL_MANAGER);
    }
}
