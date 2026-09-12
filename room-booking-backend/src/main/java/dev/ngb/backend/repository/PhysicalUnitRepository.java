package dev.ngb.backend.repository;

import dev.ngb.backend.model.PhysicalUnit;
import dev.ngb.backend.model.PhysicalUnitStatus;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the specific rooms a stay may be assigned to.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code physical_units}.</p>
 */
public interface PhysicalUnitRepository extends ListCrudRepository<PhysicalUnit, UUID> {

    /**
     * Returns a category's units in one status.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ? AND status = ?}. Passing
     * {@link PhysicalUnitStatus#AVAILABLE} gives the units a stay may actually be assigned to; a
     * caller that listed every unit and filtered in memory would offer rooms that are out of
     * service.</p>
     *
     * @param accommodationTypeId category whose units are listed
     * @param status status to filter by
     * @return possibly empty list of units
     */
    List<PhysicalUnit> findAllByAccommodationTypeIdAndStatus(
            UUID accommodationTypeId,
            PhysicalUnitStatus status);
}
