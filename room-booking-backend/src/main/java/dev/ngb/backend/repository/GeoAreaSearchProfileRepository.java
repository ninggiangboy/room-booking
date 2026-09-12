package dev.ngb.backend.repository;

import dev.ngb.backend.model.GeoAreaSearchProfile;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads what the platform has learned about the usefulness of destinations.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code geo_area_search_profiles}. The geographic area is the primary key, so
 * {@code findById} takes an area identifier.</p>
 */
public interface GeoAreaSearchProfileRepository
        extends ListCrudRepository<GeoAreaSearchProfile, UUID> {

    /**
     * Returns the destinations worth offering in autocomplete, most sought after first.
     *
     * <pre>{@code
     * SELECT *
     * FROM geo_area_search_profiles
     * WHERE is_suggestable = true
     *   AND bookable_property_count > 0
     * ORDER BY search_volume_rank DESC, bookable_property_count DESC
     * LIMIT :limit
     * }</pre>
     *
     * <p>Matches {@code idx_geo_area_search_profiles_suggestions}. The property-count filter is in
     * the query rather than left to the caller because suggesting a destination with nothing bookable
     * in it wastes the guest's only query and teaches them the search does not work.</p>
     *
     * @param limit maximum number of destinations to return
     * @return possibly empty list of suggestable destinations
     */
    @Query("""
            SELECT *
            FROM geo_area_search_profiles
            WHERE is_suggestable = true
              AND bookable_property_count > 0
            ORDER BY search_volume_rank DESC, bookable_property_count DESC
            LIMIT :limit
            """)
    List<GeoAreaSearchProfile> findSuggestable(@Param("limit") int limit);
}
