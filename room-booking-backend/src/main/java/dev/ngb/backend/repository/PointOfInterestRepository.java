package dev.ngb.backend.repository;

import dev.ngb.backend.model.PointOfInterest;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the landmarks guests measure distance to.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code points_of_interest}. Writes supply latitude and longitude only; the
 * spatial column is derived by the database so the two representations cannot drift apart.</p>
 */
public interface PointOfInterestRepository extends ListCrudRepository<PointOfInterest, UUID> {

    /**
     * Finds a landmark by its stable key.
     *
     * <p>Spring derives {@code WHERE poi_key = ?}, matching {@code uk_points_of_interest_key}.</p>
     *
     * @param poiKey stable operator-facing key
     * @return the landmark when the key is known
     */
    Optional<PointOfInterest> findByPoiKey(String poiKey);

    /**
     * Returns the most prominent active landmarks near a point.
     *
     * <p>{@code @Query} supplies the SQL because name derivation cannot express a PostGIS predicate:</p>
     *
     * <pre>{@code
     * SELECT *
     * FROM points_of_interest
     * WHERE active = true
     *   AND ST_DWithin(
     *         position,
     *         ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
     *         :radiusMetres)
     * ORDER BY importance_rank DESC,
     *          position <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
     * LIMIT :limit
     * }</pre>
     *
     * <p>{@code ST_DWithin} on a {@code geography} column measures true metres and uses
     * {@code idx_points_of_interest_position}. Ordering by prominence before distance is deliberate:
     * a guest wants to hear that a property is near the airport before hearing it is near a bus stop
     * that happens to be closer.</p>
     *
     * @param latitude centre latitude in degrees
     * @param longitude centre longitude in degrees
     * @param radiusMetres search radius in metres
     * @param limit maximum number of landmarks to return
     * @return possibly empty list of nearby landmarks, most prominent first
     */
    @Query("""
            SELECT *
            FROM points_of_interest
            WHERE active = true
              AND ST_DWithin(
                    position,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :radiusMetres)
            ORDER BY importance_rank DESC,
                     position <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limit
            """)
    List<PointOfInterest> findNearby(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMetres") double radiusMetres,
            @Param("limit") int limit);
}
