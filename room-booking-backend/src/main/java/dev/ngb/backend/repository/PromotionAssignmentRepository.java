package dev.ngb.backend.repository;

import dev.ngb.backend.model.PromotionAssignment;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who was offered which campaign.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code promotion_assignments}.</p>
 *
 * <p>Assignments are what make a campaign measurable: guests who were eligible and did not book are
 * part of the result, and they exist nowhere else.</p>
 */
public interface PromotionAssignmentRepository extends ListCrudRepository<PromotionAssignment, UUID> {

    /**
     * Returns a signed-in guest's assignment for one set of terms.
     *
     * <p>Spring derives {@code WHERE promotion_version_id = ? AND guest_account_holder_id = ?}. A
     * partial unique index allows only one, so re-offering the same terms cannot double-count either
     * the offer or its cost.</p>
     *
     * @param promotionVersionId terms being checked
     * @param guestAccountHolderId guest being checked
     * @return the assignment, or empty when the guest was never offered it
     */
    Optional<PromotionAssignment> findByPromotionVersionIdAndGuestAccountHolderId(
            UUID promotionVersionId, UUID guestAccountHolderId);

    /**
     * Returns an anonymous unit's assignment for one set of terms.
     *
     * <p>Spring derives {@code WHERE promotion_version_id = ? AND anonymous_unit_key = ?}. Used
     * before sign-in, so an offer survives the guest creating an account mid-session.</p>
     *
     * @param promotionVersionId terms being checked
     * @param anonymousUnitKey stable key for the unidentified visitor
     * @return the assignment, or empty when the unit was never offered it
     */
    Optional<PromotionAssignment> findByPromotionVersionIdAndAnonymousUnitKey(
            UUID promotionVersionId, String anonymousUnitKey);

    /**
     * Returns every campaign a guest currently holds an offer for.
     *
     * <p>Spring derives {@code WHERE guest_account_holder_id = ?}.</p>
     *
     * @param guestAccountHolderId guest whose offers are wanted
     * @return possibly empty list of assignments
     */
    List<PromotionAssignment> findAllByGuestAccountHolderId(UUID guestAccountHolderId);
}
