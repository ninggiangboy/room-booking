package dev.ngb.backend.repository;

import dev.ngb.backend.model.Property;
import dev.ngb.backend.model.SupplyLifecycle;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and stores the physical locations where guests stay.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code properties}. Properties are archived rather than deleted, because one
 * that stops trading still has to explain the bookings it accepted.</p>
 */
public interface PropertyRepository extends ListCrudRepository<Property, UUID> {

    /**
     * Finds a property by the short reference used in operations and support.
     *
     * <p>Spring derives {@code WHERE reference_code = ?}, matching {@code uk_properties_reference}.
     * This is what a support agent has in front of them, rather than a UUID.</p>
     *
     * @param referenceCode operational reference
     * @return the property when the reference is known
     */
    Optional<Property> findByReferenceCode(String referenceCode);

    /**
     * Returns an account holder's properties in one lifecycle state.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ? AND lifecycle_state = ?}. Backs the host's
     * own portfolio view.</p>
     *
     * @param accountHolderId owner whose properties are listed
     * @param lifecycleState state to filter by
     * @return possibly empty list of properties
     */
    List<Property> findAllByAccountHolderIdAndLifecycleState(
            UUID accountHolderId,
            SupplyLifecycle lifecycleState);

    /**
     * Returns active properties whose true position lies within a radius of a point.
     *
     * <p>{@code @Query} supplies the SQL because name derivation cannot express a PostGIS predicate:</p>
     *
     * <pre>{@code
     * SELECT *
     * FROM properties
     * WHERE lifecycle_state = 'ACTIVE'
     *   AND location IS NOT NULL
     *   AND ST_DWithin(
     *         location,
     *         ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
     *         :radiusMetres)
     * }</pre>
     *
     * <p>{@code ST_DWithin} on a {@code geography} column measures true distance in metres and uses
     * {@code idx_properties_location}. Note this searches the <em>true</em> position: it is a
     * server-side retrieval step, and what a guest is shown before booking is the obfuscated point.
     * Filtering on the obfuscated point would return the wrong properties near a radius edge.</p>
     *
     * @param latitude centre latitude in degrees
     * @param longitude centre longitude in degrees
     * @param radiusMetres search radius in metres
     * @return possibly empty list of properties within the radius
     */
    @Query("""
            SELECT *
            FROM properties
            WHERE lifecycle_state = 'ACTIVE'
              AND location IS NOT NULL
              AND ST_DWithin(
                    location,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :radiusMetres)
            """)
    List<Property> findActiveWithinRadius(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMetres") double radiusMetres);
}
