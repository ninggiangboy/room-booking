package dev.ngb.backend.growth.internal.repository.affiliate;

import dev.ngb.backend.growth.internal.model.affiliate.AffiliatePartner;
import dev.ngb.backend.growth.internal.model.affiliate.AffiliatePartnerStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the affiliate agreements and the terms each one pays under.
 *
 * <p>Rows are frozen once active, so the rate a commission was computed at is the rate the
 * agreement actually carried.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code affiliate_partners}.</p>
 */
public interface AffiliatePartnerRepository extends ListCrudRepository<AffiliatePartner, UUID> {

    /**
     * Finds a partner by the key it is known by.
     *
     * @param partnerKey the stable partner key
     * @return the partner, when one is registered
     */
    Optional<AffiliatePartner> findByPartnerKey(String partnerKey);

    /**
     * Lists the agreements in one lifecycle state.
     *
     * @param status where the agreements stand
     * @return possibly empty list
     */
    List<AffiliatePartner> findByStatus(AffiliatePartnerStatus status);

    /**
     * Lists the agreements paying above a rate, which is what a review of what the platform gives
     * away per booking starts from.
     *
     * <pre>{@code
     * SELECT * FROM affiliate_partners
     * WHERE status = 'ACTIVE' AND commission_percent >= :minimumPercent
     * ORDER BY commission_percent DESC
     * }</pre>
     *
     * @param minimumPercent lowest rate to include
     * @return possibly empty list, most expensive first
     */
    @Query("""
            SELECT * FROM affiliate_partners
            WHERE status = 'ACTIVE' AND commission_percent >= :minimumPercent
            ORDER BY commission_percent DESC
            """)
    List<AffiliatePartner> findAtOrAbove(@Param("minimumPercent") BigDecimal minimumPercent);
}
