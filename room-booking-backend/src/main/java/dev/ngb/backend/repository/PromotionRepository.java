package dev.ngb.backend.repository;

import dev.ngb.backend.model.Promotion;
import dev.ngb.backend.model.PromotionStatus;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads campaign identities and their budgets.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code promotions}.</p>
 */
public interface PromotionRepository extends ListCrudRepository<Promotion, UUID> {

    /**
     * Finds a promotion by the code a guest entered.
     *
     * <p>Spring derives {@code WHERE promotion_code = ?}. The column is uniquely indexed, so a code
     * identifies at most one campaign.</p>
     *
     * @param promotionCode code as entered
     * @return the promotion, or empty when no campaign uses that code
     */
    Optional<Promotion> findByPromotionCode(String promotionCode);

    /**
     * Returns a host's campaigns in one state.
     *
     * <p>Spring derives {@code WHERE owner_account_holder_id = ? AND status = ?}.</p>
     *
     * @param ownerAccountHolderId host whose campaigns are wanted
     * @param status state to include
     * @return possibly empty list of campaigns
     */
    List<Promotion> findAllByOwnerAccountHolderIdAndStatus(
            UUID ownerAccountHolderId, PromotionStatus status);

    /**
     * Returns the campaigns running in a market.
     *
     * <p>Spring derives {@code WHERE market_code = ? AND status = ?}.</p>
     *
     * @param marketCode market being priced in
     * @param status state to include, normally {@link PromotionStatus#ACTIVE}
     * @return possibly empty list of campaigns
     */
    List<Promotion> findAllByMarketCodeAndStatus(String marketCode, PromotionStatus status);
}
