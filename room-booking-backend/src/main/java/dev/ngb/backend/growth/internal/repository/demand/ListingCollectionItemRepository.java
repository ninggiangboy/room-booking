package dev.ngb.backend.growth.internal.repository.demand;

import dev.ngb.backend.growth.internal.model.demand.ListingCollectionItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what is in a wish list and in what order.
 *
 * <p>Positions are unique within a list only at commit, so a reorder may pass through a
 * conflicting intermediate state inside one transaction.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_collection_items}.</p>
 */
public interface ListingCollectionItemRepository extends ListCrudRepository<ListingCollectionItem, UUID> {

    /**
     * Lists the entries of one list in the order the guest arranged them.
     *
     * @param listingCollectionId the list
     * @return possibly empty list, by position
     */
    List<ListingCollectionItem> findByListingCollectionIdOrderByPosition(
            UUID listingCollectionId);

    /**
     * Finds whether a saved listing is already in a list.
     *
     * @param listingCollectionId the list
     * @param savedListingId the saved listing
     * @return the entry, when it is already there
     */
    Optional<ListingCollectionItem> findByListingCollectionIdAndSavedListingId(
            UUID listingCollectionId, UUID savedListingId);

    /**
     * Finds the next free position in a list, so an addition lands at the end rather than on top
     * of something.
     *
     * <pre>{@code
     * SELECT coalesce(max(position) + 1, 0) FROM listing_collection_items
     * WHERE listing_collection_id = :listingCollectionId
     * }</pre>
     *
     * @param listingCollectionId the list
     * @return the next position, zero for an empty list
     */
    @Query("""
            SELECT coalesce(max(position) + 1, 0) FROM listing_collection_items
            WHERE listing_collection_id = :listingCollectionId
            """)
    int nextPosition(@Param("listingCollectionId") UUID listingCollectionId);
}
