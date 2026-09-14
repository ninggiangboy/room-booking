package dev.ngb.backend.repository;

import dev.ngb.backend.model.GeoAreaName;
import dev.ngb.backend.model.GeoAreaNameId;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the names a destination is known by, which are not the same thing as what it is called.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link GeoAreaNameId} key and the {@code geo_area_names} table.</p>
 *
 * <p>This table exists so a guest can be matched on one name and shown another. Searching it and
 * searching {@code geo_areas} answer different questions, and a caller offering autocomplete
 * generally wants both: the alias to find the place, the destination row to display it.</p>
 */
public interface GeoAreaNameRepository
        extends ListCrudRepository<GeoAreaName, GeoAreaNameId> {

    /**
     * Returns every name one destination holds, across all languages.
     *
     * <p>Spring derives {@code WHERE geo_area_id = ?} from the composite-key property path, which is
     * the leading column of {@code pk_geo_area_names}.</p>
     *
     * @param geoAreaId destination whose names are wanted
     * @return possibly empty list of names
     */
    List<GeoAreaName> findAllByIdGeoAreaId(UUID geoAreaId);

    /**
     * Returns the name to display for one destination in one language.
     *
     * <pre>{@code
     * SELECT *
     * FROM geo_area_names
     * WHERE geo_area_id = :geoAreaId
     *   AND language_code = :languageCode
     *   AND name_type = 'PREFERRED'
     * }</pre>
     *
     * <p>At most one row can come back: {@code uk_geo_area_names_preferred_language} is a partial
     * unique index over exactly this predicate. An empty result means the destination has not been
     * translated into that language, which a caller handles by falling back to {@code GeoArea.name}
     * rather than showing nothing.</p>
     *
     * @param geoAreaId destination whose display name is wanted
     * @param languageCode BCP 47 language to display in
     * @return the preferred name, when one exists in that language
     */
    @Query("""
            SELECT *
            FROM geo_area_names
            WHERE geo_area_id = :geoAreaId
              AND language_code = :languageCode
              AND name_type = 'PREFERRED'
            """)
    Optional<GeoAreaName> findPreferred(@Param("geoAreaId") UUID geoAreaId,
            @Param("languageCode") String languageCode);

    /**
     * Finds the destinations whose alternative names resemble what the guest typed.
     *
     * <pre>{@code
     * SELECT n.*
     * FROM geo_area_names n
     * JOIN geo_areas a ON a.id = n.geo_area_id
     * WHERE a.active = true
     *   AND a.country_code = :countryCode
     *   AND n.normalized_name % :normalizedQuery
     * ORDER BY similarity(n.normalized_name, :normalizedQuery) DESC, n.name
     * LIMIT :limit
     * }</pre>
     *
     * <p>The join is what keeps a deactivated destination from being reached through one of its
     * aliases, which is the way it would otherwise come back: the alias rows are not deactivated
     * alongside their destination, they are deleted with it by {@code fk_geo_area_names_area}'s
     * cascade, and a destination that is merely superseded keeps all of them.</p>
     *
     * <p>The {@code %} operator is {@code pg_trgm}'s similarity test, matching
     * {@code idx_geo_area_names_search}. The query must be normalized the same way the column was.
     * </p>
     *
     * @param countryCode ISO 3166-1 alpha-2 country to search within
     * @param normalizedQuery the guest's query, normalized as the column is
     * @param limit maximum number of names to return
     * @return possibly empty list, closest match first
     */
    @Query("""
            SELECT n.*
            FROM geo_area_names n
            JOIN geo_areas a ON a.id = n.geo_area_id
            WHERE a.active = true
              AND a.country_code = :countryCode
              AND n.normalized_name % :normalizedQuery
            ORDER BY similarity(n.normalized_name, :normalizedQuery) DESC, n.name
            LIMIT :limit
            """)
    List<GeoAreaName> searchByName(@Param("countryCode") String countryCode,
            @Param("normalizedQuery") String normalizedQuery,
            @Param("limit") int limit);
}
