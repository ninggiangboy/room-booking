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
 * One saved listing’s place in a wish list.
 *
 * <p>The saved listing must belong to the same guest as the collection, and positions are unique
 * within a list only at commit, so a reorder can pass through a conflicting intermediate
 * state.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_collection_items")
public class ListingCollectionItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The list this entry belongs to. */
    private UUID listingCollectionId;
    /** The migration 029 saved listing, which must be the same guest’s. */
    private UUID savedListingId;
    /** Where the entry sits in the list, unique there at commit. */
    private int position;
    /** The guest’s note about this listing. */
    private @Nullable String note;
    /** UTC instant the listing was added to the list. */
    private Instant addedAt;
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
