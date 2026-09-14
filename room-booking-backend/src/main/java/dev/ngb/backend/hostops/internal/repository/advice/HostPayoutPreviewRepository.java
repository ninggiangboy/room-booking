package dev.ngb.backend.hostops.internal.repository.advice;

import dev.ngb.backend.hostops.internal.model.advice.HostPayoutPreview;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.advice.HostPayoutPreview;


/**
 * Reads what a host was told to expect before the money existed.
 *
 * <p>A preview is never binding and never edited: a revision is a new row with a later
 * {@code computedAt}. Migration 022 owns the statement that is binding.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_payout_previews}.</p>
 */
public interface HostPayoutPreviewRepository extends ListCrudRepository<HostPayoutPreview, UUID> {

    /**
     * Lists one host's previews, newest first.
     *
     * @param hostAccountHolderId the host
     * @return possibly empty list, most recent first
     */
    List<HostPayoutPreview> findByHostAccountHolderIdOrderByComputedAtDesc(
            UUID hostAccountHolderId);

    /**
     * Lists the previews written for one booking, newest first.
     *
     * @param bookingId the booking
     * @return possibly empty list, most recent first
     */
    List<HostPayoutPreview> findByBookingIdOrderByComputedAtDesc(UUID bookingId);

    /**
     * Finds the preview to show now for one host and period.
     *
     * <pre>{@code
     * SELECT * FROM host_payout_previews
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND subject_kind = 'PERIOD'
     *   AND period_start = :periodStart
     * ORDER BY computed_at DESC
     * LIMIT 1
     * }</pre>
     *
     * @param hostAccountHolderId the host
     * @param periodStart first day of the period
     * @return the newest preview, when one has been computed
     */
    @Query("""
            SELECT * FROM host_payout_previews
            WHERE host_account_holder_id = :hostAccountHolderId
              AND subject_kind = 'PERIOD'
              AND period_start = :periodStart
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    Optional<HostPayoutPreview> findCurrentForPeriod(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("periodStart") LocalDate periodStart);

    /**
     * Lists the previews a host may still be looking at, which is the set worth recomputing when
     * the money behind them moves.
     *
     * <pre>{@code
     * SELECT * FROM host_payout_previews
     * WHERE host_account_holder_id = :hostAccountHolderId AND valid_until > :asOf
     * ORDER BY computed_at DESC
     * }</pre>
     *
     * @param hostAccountHolderId the host
     * @param asOf the instant to judge staleness against
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM host_payout_previews
            WHERE host_account_holder_id = :hostAccountHolderId AND valid_until > :asOf
            ORDER BY computed_at DESC
            """)
    List<HostPayoutPreview> findUnexpired(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("asOf") Instant asOf);
}
