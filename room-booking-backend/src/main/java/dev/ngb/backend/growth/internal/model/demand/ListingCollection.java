package dev.ngb.backend.growth.internal.model.demand;

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
 * One guest’s wish list of saved listings.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_collections")
public class ListingCollection {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The guest whose list this is. */
    private UUID accountHolderId;
    /** What the guest called the list. */
    private String title;
    /** The guest’s own note about the list. */
    private @Nullable String description;
    /** Who can see the list. */
    private ListingCollectionVisibility visibility;
    /** Digest of the sharing link; the link itself is a secret and is not stored. */
    private @Nullable String shareTokenDigest;
    /** How many listings are in the list. */
    private int itemCount;
    /** Where the list stands. */
    private ListingCollectionState state;
    /** UTC instant the list was archived. */
    private @Nullable Instant archivedAt;
    /** UTC instant the list was deleted. */
    private @Nullable Instant deletedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
