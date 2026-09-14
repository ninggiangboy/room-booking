package dev.ngb.backend.repository;

import dev.ngb.backend.model.AffiliateAttribution;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads partner clicks and the bookings they may claim.
 *
 * <p>A booking is credited to at most one partner, which is what stops two partners invoicing
 * for the same stay.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code affiliate_attributions}.</p>
 */
public interface AffiliateAttributionRepository extends ListCrudRepository<AffiliateAttribution, UUID> {

    /**
     * Finds one partner's own record of a click.
     *
     * @param affiliatePartnerId the partner
     * @param clickReference the partner's reference
     * @return the click, when it was recorded
     */
    Optional<AffiliateAttribution> findByAffiliatePartnerIdAndClickReference(
            UUID affiliatePartnerId, String clickReference);

    /**
     * Finds which partner, if any, a booking was credited to.
     *
     * @param bookingId the booking
     * @return the credited click, when there is one
     */
    Optional<AffiliateAttribution> findByBookingId(UUID bookingId);

    /**
     * Finds the click that should take the credit for a booking under the last-click rule: the
     * most recent open click by this visitor whose window still covers the booking.
     *
     * <pre>{@code
     * SELECT * FROM affiliate_attributions
     * WHERE state = 'OPEN'
     *   AND (account_holder_id = :accountHolderId OR anonymous_unit_key = :anonymousUnitKey)
     *   AND clicked_at <= :at
     *   AND expires_at > :at
     * ORDER BY clicked_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param accountHolderId the signed-in person, when there is one
     * @param anonymousUnitKey the pseudonymous visitor key
     * @param at instant the booking was made
     * @return the click to credit, when one is still open
     */
    @Query("""
            SELECT * FROM affiliate_attributions
            WHERE state = 'OPEN'
              AND (account_holder_id = :accountHolderId OR anonymous_unit_key = :anonymousUnitKey)
              AND clicked_at <= :at
              AND expires_at > :at
            ORDER BY clicked_at DESC
            LIMIT 1
            """)
    Optional<AffiliateAttribution> findLastClick(
            @Param("accountHolderId") UUID accountHolderId,
            @Param("anonymousUnitKey") String anonymousUnitKey, @Param("at") Instant at);

    /**
     * Lists the clicks whose window has closed with no booking, which is the set the expiry sweep
     * takes.
     *
     * <pre>{@code
     * SELECT * FROM affiliate_attributions
     * WHERE state = 'OPEN' AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest closed first
     */
    @Query("""
            SELECT * FROM affiliate_attributions
            WHERE state = 'OPEN' AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<AffiliateAttribution> findExpirable(@Param("at") Instant at);
}
