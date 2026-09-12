package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccommodationTypeAmenity;
import dev.ngb.backend.model.AccommodationTypeAmenityId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads and records which amenities an accommodation type claims.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link AccommodationTypeAmenityId} key and the {@code accommodation_type_amenities} table.</p>
 */
public interface AccommodationTypeAmenityRepository
        extends ListCrudRepository<AccommodationTypeAmenity, AccommodationTypeAmenityId> {

    /**
     * Records or updates an amenity claim.
     *
     * <p>Explicit SQL is required because the table has a composite primary key, and the conflict
     * clause makes re-saving a claim idempotent rather than an error:</p>
     *
     * <pre>{@code
     * INSERT INTO accommodation_type_amenities
     *     (accommodation_type_id, amenity_definition_id, count_value, text_value,
     *      created_at, updated_at)
     * VALUES (:accommodationTypeId, :amenityDefinitionId, :countValue, :textValue,
     *         :decisionInstant, :decisionInstant)
     * ON CONFLICT (accommodation_type_id, amenity_definition_id)
     * DO UPDATE SET count_value = EXCLUDED.count_value,
     *               text_value = EXCLUDED.text_value,
     *               updated_at = EXCLUDED.updated_at
     * }</pre>
     *
     * @param accommodationTypeId category making the claim
     * @param amenityDefinitionId term being claimed
     * @param countValue quantity, for a count-valued term
     * @param textValue text, for a text-valued term
     * @param decisionInstant the command's single decision instant
     * @return number of rows written, always one
     */
    @Modifying
    @Query("""
            INSERT INTO accommodation_type_amenities
                (accommodation_type_id, amenity_definition_id, count_value, text_value,
                 created_at, updated_at)
            VALUES (:accommodationTypeId, :amenityDefinitionId, :countValue, :textValue,
                    :decisionInstant, :decisionInstant)
            ON CONFLICT (accommodation_type_id, amenity_definition_id)
            DO UPDATE SET count_value = EXCLUDED.count_value,
                          text_value = EXCLUDED.text_value,
                          updated_at = EXCLUDED.updated_at
            """)
    int claimAmenity(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("amenityDefinitionId") UUID amenityDefinitionId,
            @Param("countValue") Short countValue,
            @Param("textValue") String textValue,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every amenity an accommodation type claims.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ?} from the composite-key property
     * path.</p>
     *
     * @param accommodationTypeId category whose claims are listed
     * @return possibly empty list of claims
     */
    List<AccommodationTypeAmenity> findAllByIdAccommodationTypeId(UUID accommodationTypeId);
}
