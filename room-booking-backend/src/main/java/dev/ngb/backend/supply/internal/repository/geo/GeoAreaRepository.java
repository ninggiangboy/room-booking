package dev.ngb.backend.supply.internal.repository.geo;

import dev.ngb.backend.supply.internal.model.geo.GeoArea;
import dev.ngb.backend.supply.internal.model.geo.GeoAreaType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the catalog of places a guest can name as a destination.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code geo_areas}.</p>
 *
 * <p>Every finder here filters on {@code active}. A destination that has been superseded is
 * deactivated rather than deleted, because rows across half the schema still point at it; leaving
 * the filter to the caller would mean one forgotten predicate is enough to offer a guest a place
 * that no longer exists.</p>
 *
 * <p>Spatial questions -- what is within a radius, what a boundary contains -- are deliberately not
 * here. They are answered by the PostGIS columns this aggregate does not map, through predicates
 * that belong in the query that needs them.</p>
 */
public interface GeoAreaRepository extends ListCrudRepository<GeoArea, UUID> {

    /**
     * Finds the destination one upstream record corresponds to, which is how an import reconciles.
     *
     * <p>Spring derives {@code WHERE source = ? AND source_id = ?}, matching
     * {@code uk_geo_areas_source_id}. The lookup is by source identity rather than by name because
     * a place can be renamed upstream and must still be recognised as the same row.</p>
     *
     * @param source which external catalog the record came from
     * @param sourceId identifier that catalog assigned
     * @return the destination, when the source record has already been imported
     */
    Optional<GeoArea> findBySourceAndSourceId(String source, String sourceId);

    /**
     * Lists the live destinations directly inside another, for walking down the hierarchy.
     *
     * <p>Spring derives {@code WHERE parent_id = ? AND active = true}, matching
     * {@code idx_geo_areas_parent}.</p>
     *
     * @param parentId destination whose children are wanted
     * @return possibly empty list of immediate children
     */
    List<GeoArea> findByParentIdAndActiveTrue(UUID parentId);

    /**
     * Lists the live destinations of one kind in one country, for browsing rather than searching.
     *
     * <p>Spring derives {@code WHERE country_code = ? AND area_type = ? AND active = true}, whose
     * column order matches the leading columns of {@code idx_geo_areas_browse}.</p>
     *
     * @param countryCode ISO 3166-1 alpha-2 country
     * @param areaType which kind of place to list
     * @return possibly empty list
     */
    List<GeoArea> findByCountryCodeAndAreaTypeAndActiveTrue(String countryCode,
            GeoAreaType areaType);

    /**
     * Finds the live destinations whose canonical name resembles what the guest typed.
     *
     * <pre>{@code
     * SELECT *
     * FROM geo_areas
     * WHERE active = true
     *   AND country_code = :countryCode
     *   AND normalized_name % :normalizedQuery
     * ORDER BY similarity(normalized_name, :normalizedQuery) DESC, name
     * LIMIT :limit
     * }</pre>
     *
     * <p>The {@code %} operator is {@code pg_trgm}'s similarity test, which is what
     * {@code idx_geo_areas_name_trgm} indexes; a {@code LIKE '%...%'} predicate would read the whole
     * table and would still miss a transposed letter. The query must be normalized the same way the
     * column was -- case-folded and accent-stripped -- or a guest typing accents will match nothing.
     * </p>
     *
     * <p>This searches canonical names only. A guest searching by a colloquial or former name is
     * served by {@code GeoAreaNameRepository}, which is why the two are separate tables.</p>
     *
     * @param countryCode ISO 3166-1 alpha-2 country to search within
     * @param normalizedQuery the guest's query, normalized as the column is
     * @param limit maximum number of destinations to return
     * @return possibly empty list, closest match first
     */
    @Query("""
            SELECT *
            FROM geo_areas
            WHERE active = true
              AND country_code = :countryCode
              AND normalized_name % :normalizedQuery
            ORDER BY similarity(normalized_name, :normalizedQuery) DESC, name
            LIMIT :limit
            """)
    List<GeoArea> searchByName(@Param("countryCode") String countryCode,
            @Param("normalizedQuery") String normalizedQuery,
            @Param("limit") int limit);
}
