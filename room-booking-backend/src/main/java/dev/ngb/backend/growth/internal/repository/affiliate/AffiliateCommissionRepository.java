package dev.ngb.backend.growth.internal.repository.affiliate;

import dev.ngb.backend.growth.internal.model.affiliate.AffiliateCommission;
import dev.ngb.backend.growth.internal.model.affiliate.AffiliateCommissionState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.affiliate.AffiliateCommission;
import dev.ngb.backend.growth.internal.model.affiliate.AffiliateCommissionState;


/**
 * Reads what each partner is owed, and what has been paid or reversed.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code affiliate_commissions}.</p>
 */
public interface AffiliateCommissionRepository extends ListCrudRepository<AffiliateCommission, UUID> {

    /**
     * Finds the commission owed on one credited click, of which there is at most one.
     *
     * @param affiliateAttributionId the credited click
     * @return the commission, when one was raised
     */
    Optional<AffiliateCommission> findByAffiliateAttributionId(UUID affiliateAttributionId);

    /**
     * Lists one partner's commissions in one state.
     *
     * @param affiliatePartnerId the partner
     * @param state where the commissions stand
     * @return possibly empty list
     */
    List<AffiliateCommission> findByAffiliatePartnerIdAndState(UUID affiliatePartnerId,
            AffiliateCommissionState state);

    /**
     * Lists the commissions raised against one booking, which is what a cancellation has to
     * reverse.
     *
     * @param bookingId the booking
     * @return possibly empty list
     */
    List<AffiliateCommission> findByBookingId(UUID bookingId);

    /**
     * Lists the commissions whose hold has run and which may now be paid.
     *
     * <pre>{@code
     * SELECT * FROM affiliate_commissions
     * WHERE state = 'EARNED' AND matures_at <= :at
     * ORDER BY matures_at
     * }</pre>
     *
     * @param at instant the payment run is for
     * @return possibly empty list, longest matured first
     */
    @Query("""
            SELECT * FROM affiliate_commissions
            WHERE state = 'EARNED' AND matures_at <= :at
            ORDER BY matures_at
            """)
    List<AffiliateCommission> findPayable(@Param("at") Instant at);

    /**
     * Sums what one partner is owed and not yet paid, which is what an invoice is checked against.
     *
     * <pre>{@code
     * SELECT coalesce(sum(commission_minor), 0) FROM affiliate_commissions
     * WHERE affiliate_partner_id = :affiliatePartnerId AND state IN ('PENDING', 'EARNED')
     * }</pre>
     *
     * @param affiliatePartnerId the partner
     * @return total outstanding, in integer minor units
     */
    @Query("""
            SELECT coalesce(sum(commission_minor), 0) FROM affiliate_commissions
            WHERE affiliate_partner_id = :affiliatePartnerId AND state IN ('PENDING', 'EARNED')
            """)
    long sumOutstanding(@Param("affiliatePartnerId") UUID affiliatePartnerId);
}
