package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccessibilityClaim;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the accessibility features an accommodation type claims.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code accessibility_claims}.</p>
 */
public interface AccessibilityClaimRepository extends ListCrudRepository<AccessibilityClaim, UUID> {

    /**
     * Returns every accessibility claim for an accommodation type.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ?}. Claims recorded as {@code false}
     * are included deliberately: a guest deciding whether they can physically enter needs "this does
     * not have a step-free entrance" as much as the positive claims.</p>
     *
     * @param accommodationTypeId category whose claims are listed
     * @return possibly empty list of claims
     */
    List<AccessibilityClaim> findAllByAccommodationTypeId(UUID accommodationTypeId);

    /**
     * Finds one accessibility claim.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ? AND claim_key = ?}, matching
     * {@code uk_accessibility_claims}. An empty result means nobody has stated anything about this
     * feature, which is different from stating it is absent.</p>
     *
     * @param accommodationTypeId category in question
     * @param claimKey stable key of the feature
     * @return the claim when one has been made
     */
    Optional<AccessibilityClaim> findByAccommodationTypeIdAndClaimKey(
            UUID accommodationTypeId,
            String claimKey);
}
