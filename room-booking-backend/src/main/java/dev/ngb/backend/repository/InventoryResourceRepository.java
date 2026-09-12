package dev.ngb.backend.repository;

import dev.ngb.backend.model.InventoryResource;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the calendar identities that nights are sold from.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code inventory_resources}.</p>
 */
public interface InventoryResourceRepository extends ListCrudRepository<InventoryResource, UUID> {

    /**
     * Finds the resource that sells an accommodation type's nights.
     *
     * <pre>{@code
     * SELECT *
     * FROM inventory_resources
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND resource_type <> 'PHYSICAL_UNIT'
     *   AND status <> 'RETIRED'
     * }</pre>
     *
     * <p>{@code uk_inventory_resources_type_primary} guarantees at most one row matches. Physical-unit
     * resources are excluded because they are the calendars of individual rooms within a pooled type,
     * not the thing the type is sold from — two calendars for one sellable thing is the defect this
     * shape prevents.</p>
     *
     * @param accommodationTypeId category whose calendar is wanted
     * @return the selling resource when one exists
     */
    @Query("""
            SELECT *
            FROM inventory_resources
            WHERE accommodation_type_id = :accommodationTypeId
              AND resource_type <> 'PHYSICAL_UNIT'
              AND status <> 'RETIRED'
            """)
    Optional<InventoryResource> findSellingResource(
            @Param("accommodationTypeId") UUID accommodationTypeId);

    /**
     * Finds the calendar of one specific room.
     *
     * <p>Spring derives {@code WHERE physical_unit_id = ?}, matching
     * {@code uk_inventory_resources_unit}.</p>
     *
     * @param physicalUnitId room whose calendar is wanted
     * @return the resource when the room has one
     */
    Optional<InventoryResource> findByPhysicalUnitId(UUID physicalUnitId);

    /**
     * Returns every calendar belonging to an accommodation type.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ?}, including per-room calendars.</p>
     *
     * @param accommodationTypeId category whose calendars are listed
     * @return possibly empty list of resources
     */
    List<InventoryResource> findAllByAccommodationTypeId(UUID accommodationTypeId);
}
