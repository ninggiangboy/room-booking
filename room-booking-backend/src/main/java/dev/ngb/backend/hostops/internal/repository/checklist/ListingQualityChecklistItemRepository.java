package dev.ngb.backend.hostops.internal.repository.checklist;

import dev.ngb.backend.hostops.internal.model.checklist.ListingQualityChecklistItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.checklist.ListingQualityChecklistItem;


/**
 * Reads the individual things a listing may be asked to do.
 *
 * <p>An item that blocks publication is the gate a listing cannot be published through, so a
 * publication check reads this table rather than trusting a cached score.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_quality_checklist_items}.</p>
 */
public interface ListingQualityChecklistItemRepository extends ListCrudRepository<ListingQualityChecklistItem, UUID> {

    /**
     * Lists one checklist's items in the order the host sees them.
     *
     * @param checklistVersionId the checklist version
     * @return possibly empty list, in display order
     */
    List<ListingQualityChecklistItem> findByChecklistVersionIdOrderByOrdinal(
            UUID checklistVersionId);

    /**
     * Finds one item of one checklist by its key.
     *
     * @param checklistVersionId the checklist version
     * @param itemKey the item
     * @return the item, when the checklist carries it
     */
    Optional<ListingQualityChecklistItem> findByChecklistVersionIdAndItemKey(
            UUID checklistVersionId, String itemKey);

    /**
     * Lists the items of one checklist that stop a listing being published, which is the set a
     * publication attempt has to find satisfied.
     *
     * <pre>{@code
     * SELECT * FROM listing_quality_checklist_items
     * WHERE checklist_version_id = :checklistVersionId AND blocks_publication
     * ORDER BY ordinal
     * }</pre>
     *
     * @param checklistVersionId the checklist version
     * @return possibly empty list, in display order
     */
    @Query("""
            SELECT * FROM listing_quality_checklist_items
            WHERE checklist_version_id = :checklistVersionId AND blocks_publication
            ORDER BY ordinal
            """)
    List<ListingQualityChecklistItem> findBlocking(
            @Param("checklistVersionId") UUID checklistVersionId);
}
