package dev.ngb.backend.discovery.internal.repository.profile;

import dev.ngb.backend.discovery.internal.model.profile.GuestPreferenceFeature;
import dev.ngb.backend.discovery.internal.model.PreferenceDimensionKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.PreferenceDimensionKind;
import dev.ngb.backend.discovery.internal.model.profile.GuestPreferenceFeature;


/**
 * Reads the preference dimensions inside one guest profile version.
 *
 * <p>Importance and direction are separate columns and must stay separate in use: a dimension a guest
 * cares about deeply and dislikes is not the same as one they are indifferent to.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code guest_preference_features}.</p>
 */
public interface GuestPreferenceFeatureRepository extends ListCrudRepository<GuestPreferenceFeature, UUID> {

    /**
     * Lists the dimensions of one profile version.
     *
     * @param guestPreferenceProfileId the profile version
     * @return possibly empty list
     */
    List<GuestPreferenceFeature> findByGuestPreferenceProfileId(UUID guestPreferenceProfileId);

    /**
     * Lists one kind of dimension within a profile version.
     *
     * @param guestPreferenceProfileId the profile version
     * @param dimensionKind the kind
     * @return possibly empty list
     */
    List<GuestPreferenceFeature> findByGuestPreferenceProfileIdAndDimensionKind(
            UUID guestPreferenceProfileId, PreferenceDimensionKind dimensionKind);

    /**
     * Lists the dimensions of a profile version that clear a confidence floor.
     *
     * <pre>{@code
     * SELECT * FROM guest_preference_features
     * WHERE guest_preference_profile_id = :guestPreferenceProfileId
     *   AND confidence >= :confidenceFloor
     * ORDER BY importance DESC
     * }</pre>
     *
     * <p>The floor comes from the active ranking policy rather than from a constant, so a weak profile
     * cannot reorder a page of strong results.</p>
     *
     * @param guestPreferenceProfileId the profile version
     * @param confidenceFloor floor from the ranking policy
     * @return possibly empty list, most important first
     */
    @Query("""
            SELECT * FROM guest_preference_features
            WHERE guest_preference_profile_id = :guestPreferenceProfileId
              AND confidence >= :confidenceFloor
            ORDER BY importance DESC
            """)
    List<GuestPreferenceFeature> findConfident(
            @Param("guestPreferenceProfileId") UUID guestPreferenceProfileId,
            @Param("confidenceFloor") BigDecimal confidenceFloor);
}
