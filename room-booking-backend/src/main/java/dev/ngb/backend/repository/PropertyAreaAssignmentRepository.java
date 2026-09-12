package dev.ngb.backend.repository;

import dev.ngb.backend.model.PropertyAreaAssignment;
import dev.ngb.backend.model.PropertyAreaAssignmentId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads and maintains which destinations a property appears under.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link PropertyAreaAssignmentId} key and the {@code property_area_assignments} table.</p>
 */
public interface PropertyAreaAssignmentRepository
        extends ListCrudRepository<PropertyAreaAssignment, PropertyAreaAssignmentId> {

    /**
     * Returns every destination a property appears under.
     *
     * <p>Spring derives {@code WHERE property_id = ?} from the composite-key property path. A
     * property is simultaneously in a country, a province, a city, and a neighbourhood, so several
     * rows are normal.</p>
     *
     * @param propertyId property whose assignments are listed
     * @return possibly empty list of assignments
     */
    List<PropertyAreaAssignment> findAllByIdPropertyId(UUID propertyId);

    /**
     * Returns the properties filed under one destination.
     *
     * <p>Spring derives {@code WHERE geo_area_id = ?}, matching
     * {@code idx_property_area_assignments_area}. This is the direction search traffic goes.</p>
     *
     * @param geoAreaId destination being searched
     * @return possibly empty list of assignments
     */
    List<PropertyAreaAssignment> findAllByIdGeoAreaId(UUID geoAreaId);

    /**
     * Removes only the assignments a catalog reimport is entitled to rebuild.
     *
     * <pre>{@code
     * DELETE FROM property_area_assignments
     * WHERE property_id = :propertyId
     *   AND assignment_source IN ('BOUNDARY', 'PROXIMITY')
     * }</pre>
     *
     * <p>Host-declared and operator-corrected assignments are deliberately left alone. A reimport
     * that cleared everything would silently discard every human correction anyone had made, and
     * those are exactly the ones that exist because the automatic answer was wrong.</p>
     *
     * @param propertyId property whose derived assignments are being rebuilt
     * @return number of assignments removed
     */
    @Modifying
    @Query("""
            DELETE FROM property_area_assignments
            WHERE property_id = :propertyId
              AND assignment_source IN ('BOUNDARY', 'PROXIMITY')
            """)
    int deleteRecomputableFor(@Param("propertyId") UUID propertyId);
}
