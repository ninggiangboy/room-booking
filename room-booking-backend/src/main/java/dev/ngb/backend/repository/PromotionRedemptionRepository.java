package dev.ngb.backend.repository;

import dev.ngb.backend.model.PromotionRedemption;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads how much of a campaign has been spent, and by whom.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code promotion_redemptions}.</p>
 *
 * <p>Nothing here stops a campaign overspending. A budget ceiling is a sum across rows, which no
 * check constraint can express safely, so the committed total is read inside the reserving
 * transaction and the caller must take the campaign's row for update before deciding. These queries
 * inform that decision; they are not the guarantee.</p>
 */
public interface PromotionRedemptionRepository extends ListCrudRepository<PromotionRedemption, UUID> {

    /**
     * Returns how much of a campaign's budget is committed.
     *
     * <pre>{@code
     * SELECT COALESCE(SUM(benefit_amount_minor), 0)
     * FROM promotion_redemptions r
     * JOIN promotion_versions v ON v.id = r.promotion_version_id
     * WHERE v.promotion_id = :promotionId
     *   AND r.state IN ('RESERVED', 'REDEEMED')
     * }</pre>
     *
     * <p>Reserved rows count. Every open quote is a commitment, and a campaign measured only on
     * confirmed bookings will overspend by whatever is sitting in checkout at the time.</p>
     *
     * @param promotionId campaign whose spend is wanted
     * @return committed benefit in minor units, zero when nothing is committed
     */
    @Query("""
            SELECT COALESCE(SUM(r.benefit_amount_minor), 0)
            FROM promotion_redemptions r
            JOIN promotion_versions v ON v.id = r.promotion_version_id
            WHERE v.promotion_id = :promotionId
              AND r.state IN ('RESERVED', 'REDEEMED')
            """)
    long sumCommittedBenefitMinor(@Param("promotionId") UUID promotionId);

    /**
     * Counts how many times a guest has committed to one campaign.
     *
     * <pre>{@code
     * SELECT COUNT(*)
     * FROM promotion_redemptions r
     * JOIN promotion_versions v ON v.id = r.promotion_version_id
     * WHERE v.promotion_id = :promotionId
     *   AND r.guest_account_holder_id = :guestAccountHolderId
     *   AND r.state IN ('RESERVED', 'REDEEMED')
     * }</pre>
     *
     * <p>Counted across versions, because a per-guest limit is a promise about the campaign and
     * republishing its terms does not reset what a guest has already had.</p>
     *
     * @param promotionId campaign being checked
     * @param guestAccountHolderId guest being checked
     * @return number of committed redemptions
     */
    @Query("""
            SELECT COUNT(*)
            FROM promotion_redemptions r
            JOIN promotion_versions v ON v.id = r.promotion_version_id
            WHERE v.promotion_id = :promotionId
              AND r.guest_account_holder_id = :guestAccountHolderId
              AND r.state IN ('RESERVED', 'REDEEMED')
            """)
    long countCommittedForGuest(
            @Param("promotionId") UUID promotionId,
            @Param("guestAccountHolderId") UUID guestAccountHolderId);

    /**
     * Returns the redemptions attached to one quote.
     *
     * <p>Spring derives {@code WHERE quote_id = ?}. Used when a quote is accepted, superseded, or
     * abandoned, so its commitments move with it.</p>
     *
     * @param quoteId quote whose redemptions are wanted
     * @return possibly empty list of redemptions
     */
    List<PromotionRedemption> findAllByQuoteId(UUID quoteId);
}
