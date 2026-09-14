package dev.ngb.backend.hostops.internal.repository.checklist;

import dev.ngb.backend.hostops.internal.model.checklist.ListingQualityChecklistState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads where one listing stands on each checklist item.
 *
 * <p>One row per listing and item, carried forward as the state changes, with the evidence that
 * raised it and the instants it was acted on and looked at again.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_quality_checklist_states}.</p>
 */
public interface ListingQualityChecklistStateRepository extends ListCrudRepository<ListingQualityChecklistState, UUID> {

    /**
     * Lists one listing's standing on every item it has been measured on.
     *
     * @param listingId the listing
     * @return possibly empty list
     */
    List<ListingQualityChecklistState> findByListingId(UUID listingId);

    /**
     * Finds one listing's standing on one item.
     *
     * @param listingId the listing
     * @param checklistItemId the item
     * @return the standing, when the listing has been measured on it
     */
    Optional<ListingQualityChecklistState> findByListingIdAndChecklistItemId(UUID listingId,
            UUID checklistItemId);

    /**
     * Lists what is outstanding on one listing, most strongly put first, which is the order the
     * host screen shows it in.
     *
     * <pre>{@code
     * SELECT s.* FROM listing_quality_checklist_states s
     * JOIN listing_quality_checklist_items i ON i.id = s.checklist_item_id
     * WHERE s.listing_id = :listingId AND s.item_state = 'OPEN'
     * ORDER BY CASE i.severity
     *              WHEN 'REQUIRED' THEN 0 WHEN 'IMPORTANT' THEN 1
     *              WHEN 'SUGGESTED' THEN 2 ELSE 3
     *          END,
     *          i.ordinal
     * }</pre>
     *
     * @param listingId the listing
     * @return possibly empty list, most strongly put first
     */
    @Query("""
            SELECT s.* FROM listing_quality_checklist_states s
            JOIN listing_quality_checklist_items i ON i.id = s.checklist_item_id
            WHERE s.listing_id = :listingId AND s.item_state = 'OPEN'
            ORDER BY CASE i.severity
                         WHEN 'REQUIRED' THEN 0 WHEN 'IMPORTANT' THEN 1
                         WHEN 'SUGGESTED' THEN 2 ELSE 3
                     END,
                     i.ordinal
            """)
    List<ListingQualityChecklistState> findOutstanding(@Param("listingId") UUID listingId);

    /**
     * Counts whether anything blocking is outstanding on one listing, which is the single question
     * a publication attempt asks.
     *
     * <pre>{@code
     * SELECT count(*) FROM listing_quality_checklist_states s
     * JOIN listing_quality_checklist_items i ON i.id = s.checklist_item_id
     * WHERE s.listing_id = :listingId AND i.blocks_publication AND s.item_state = 'OPEN'
     * }</pre>
     *
     * @param listingId the listing
     * @return how many blocking items are still outstanding
     */
    @Query("""
            SELECT count(*) FROM listing_quality_checklist_states s
            JOIN listing_quality_checklist_items i ON i.id = s.checklist_item_id
            WHERE s.listing_id = :listingId AND i.blocks_publication AND s.item_state = 'OPEN'
            """)
    long countBlockingOutstanding(@Param("listingId") UUID listingId);

    /**
     * Lists the items hosts set aside most often across the platform, which is what a review of
     * whether an item is worth asking for at all has to read.
     *
     * <pre>{@code
     * SELECT s.checklist_item_id AS checklistItemId, count(*) AS dismissalCount
     * FROM listing_quality_checklist_states s
     * WHERE s.item_state = 'DISMISSED'
     * GROUP BY s.checklist_item_id
     * ORDER BY count(*) DESC
     * }</pre>
     *
     * @return one row per item hosts have set aside, most dismissed first
     */
    @Query("""
            SELECT s.checklist_item_id AS checklistItemId, count(*) AS dismissalCount
            FROM listing_quality_checklist_states s
            WHERE s.item_state = 'DISMISSED'
            GROUP BY s.checklist_item_id
            ORDER BY count(*) DESC
            """)
    List<ItemDismissalCount> countDismissals();

    /**
     * How often one checklist item was set aside by hosts.
     *
     * @param checklistItemId the item
     * @param dismissalCount how many listings set it aside
     */
    record ItemDismissalCount(UUID checklistItemId, long dismissalCount) {}
}
