package dev.ngb.backend.repository;

import dev.ngb.backend.model.BlockSource;
import dev.ngb.backend.model.InventoryBlock;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads why nights are unavailable when nobody has booked them.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code inventory_blocks}.</p>
 */
public interface InventoryBlockRepository extends ListCrudRepository<InventoryBlock, UUID> {

    /**
     * Returns the active blocks overlapping a range on one resource.
     *
     * <pre>{@code
     * SELECT *
     * FROM inventory_blocks
     * WHERE inventory_resource_id = :resourceId
     *   AND status = 'ACTIVE'
     *   AND stay_range && daterange(:from, :to, '[)')
     * }</pre>
     *
     * <p>Used to explain to a host why their calendar shows nights as unavailable when nothing is
     * booked.</p>
     *
     * @param resourceId inventory resource being inspected
     * @param from first date, inclusive
     * @param to end date, exclusive
     * @return possibly empty list of active blocks
     */
    @Query("""
            SELECT *
            FROM inventory_blocks
            WHERE inventory_resource_id = :resourceId
              AND status = 'ACTIVE'
              AND stay_range && daterange(:from, :to, '[)')
            """)
    List<InventoryBlock> findActiveOverlapping(
            @Param("resourceId") UUID resourceId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Finds the live block a given external calendar event created.
     *
     * <p>Spring derives {@code WHERE inventory_resource_id = ? AND source_type = ? AND external_uid
     * = ? AND status = 'ACTIVE'} from the method name, matching
     * {@code uk_inventory_blocks_external}.</p>
     *
     * <p>The source is part of the lookup deliberately. An import must find and update only the block
     * <em>it</em> created; a host's own block for the same dates is invisible to this query and
     * therefore survives every sync, which is the rule that stops a feed silently unblocking a week a
     * host reserved for themselves.</p>
     *
     * @param inventoryResourceId resource the block belongs to
     * @param sourceType import source performing the lookup
     * @param externalUid identifier the external calendar gave the event
     * @return the block that import previously created, when one exists
     */
    Optional<InventoryBlock> findByInventoryResourceIdAndSourceTypeAndExternalUidAndStatus(
            UUID inventoryResourceId,
            BlockSource sourceType,
            String externalUid,
            dev.ngb.backend.model.BlockStatus status);
}
