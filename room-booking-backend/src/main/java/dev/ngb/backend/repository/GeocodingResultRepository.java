package dev.ngb.backend.repository;

import dev.ngb.backend.model.GeocodingResult;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads how a property's coordinates were arrived at.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code geocoding_results}. Attempts are never revised, and superseded ones are kept.</p>
 */
public interface GeocodingResultRepository extends ListCrudRepository<GeocodingResult, UUID> {

    /**
     * Finds the attempt a property's coordinates actually came from.
     *
     * <pre>{@code
     * SELECT * FROM geocoding_results
     * WHERE property_id = :propertyId AND is_applied = true
     * }</pre>
     *
     * <p>{@code uk_geocoding_results_one_applied} guarantees at most one row matches. This is the
     * provenance answer: when a guest is sent to the wrong place, this row names the provider and the
     * confidence that produced the point.</p>
     *
     * @param propertyId property whose provenance is wanted
     * @return the applied attempt when the property has resolved coordinates
     */
    @Query("SELECT * FROM geocoding_results WHERE property_id = :propertyId AND is_applied = true")
    Optional<GeocodingResult> findApplied(@Param("propertyId") UUID propertyId);

    /**
     * Returns every geocoding attempt for a property, most recent first.
     *
     * <p>Spring derives {@code WHERE property_id = ? ORDER BY requested_at DESC}, including failed
     * and superseded attempts — which is the point, since diagnosing a bad position means seeing what
     * else was tried.</p>
     *
     * @param propertyId property whose attempts are listed
     * @return possibly empty list of attempts, most recent first
     */
    List<GeocodingResult> findAllByPropertyIdOrderByRequestedAtDesc(UUID propertyId);
}
