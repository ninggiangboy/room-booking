package dev.ngb.backend.repository;

import dev.ngb.backend.model.PropertyPoiDistance;
import dev.ngb.backend.model.PropertyPoiDistanceId;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads precomputed distances between properties and landmarks.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link PropertyPoiDistanceId} key and the {@code property_poi_distances} table.</p>
 */
public interface PropertyPoiDistanceRepository
        extends ListCrudRepository<PropertyPoiDistance, PropertyPoiDistanceId> {

    /**
     * Returns a property's nearest landmarks, closest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM property_poi_distances
     * WHERE property_id = :propertyId
     * ORDER BY straight_line_metres
     * LIMIT :limit
     * }</pre>
     *
     * <p>Matches {@code idx_property_poi_distances_nearest}. Bounded because a listing page shows a
     * handful, not every landmark in the city.</p>
     *
     * @param propertyId property whose landmarks are wanted
     * @param limit maximum number of landmarks to return
     * @return possibly empty list of distances, closest first
     */
    @Query("""
            SELECT *
            FROM property_poi_distances
            WHERE property_id = :propertyId
            ORDER BY straight_line_metres
            LIMIT :limit
            """)
    List<PropertyPoiDistance> findNearestFor(
            @Param("propertyId") UUID propertyId,
            @Param("limit") int limit);
}
