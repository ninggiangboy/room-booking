package dev.ngb.backend.growth.internal.repository.loyalty;

import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyQualifyingEvent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what has counted toward a member's tier, and what has been taken back.
 *
 * <p>Rows are append-only: a cancelled stay appends a negative row naming the one it reverses,
 * so the tier a member held last quarter stays explainable.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code loyalty_qualifying_events}.</p>
 */
public interface LoyaltyQualifyingEventRepository extends ListCrudRepository<LoyaltyQualifyingEvent, UUID> {

    /**
     * Lists the accruals on one membership, newest first.
     *
     * @param loyaltyMembershipId the membership
     * @return possibly empty list, most recent first
     */
    List<LoyaltyQualifyingEvent> findByLoyaltyMembershipIdOrderByOccurredAtDesc(
            UUID loyaltyMembershipId);

    /**
     * Finds the row that reverses an accrual, of which there can be at most one.
     *
     * @param reversesEventId the accrual that was taken back
     * @return the reversing row, when there is one
     */
    Optional<LoyaltyQualifyingEvent> findByReversesEventId(UUID reversesEventId);

    /**
     * Sums what one membership has counted in one window, which is the figure a tier is judged
     * against and the one the member is shown.
     *
     * <pre>{@code
     * SELECT coalesce(sum(nights_counted), 0) AS nights,
     *        coalesce(sum(bookings_counted), 0) AS bookings,
     *        coalesce(sum(spend_minor), 0) AS spend_minor
     * FROM loyalty_qualifying_events
     * WHERE loyalty_membership_id = :loyaltyMembershipId
     *   AND counted_window_start = :countedWindowStart
     * }</pre>
     *
     * @param loyaltyMembershipId the membership
     * @param countedWindowStart first day of the window
     * @return the counted totals, reversals included
     */
    @Query("""
            SELECT coalesce(sum(nights_counted), 0) AS nights,
                   coalesce(sum(bookings_counted), 0) AS bookings,
                   coalesce(sum(spend_minor), 0) AS spend_minor
            FROM loyalty_qualifying_events
            WHERE loyalty_membership_id = :loyaltyMembershipId
              AND counted_window_start = :countedWindowStart
            """)
    WindowTotals sumWindow(@Param("loyaltyMembershipId") UUID loyaltyMembershipId,
            @Param("countedWindowStart") LocalDate countedWindowStart);

    /**
     * What one membership has counted in one qualification window.
     *
     * @param nights nights counted, reversals included
     * @param bookings bookings counted, reversals included
     * @param spendMinor spend counted, in integer minor units, reversals included
     */
    record WindowTotals(int nights, int bookings, long spendMinor) {}
}
