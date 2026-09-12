package dev.ngb.backend.repository;

import dev.ngb.backend.model.InventoryClaim;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads the claims that consume inventory.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code inventory_claims}.</p>
 *
 * <p>Nothing here prevents overselling. The {@code ex_inventory_claims_no_overlap} exclusion
 * constraint does that, at write time, under concurrency. These queries inform a preflight check so a
 * guest gets a clear refusal instead of a constraint violation — but a preflight that passes is never
 * a guarantee, because another transaction may commit between the read and the write.</p>
 */
public interface InventoryClaimRepository extends ListCrudRepository<InventoryClaim, UUID> {

    /**
     * Returns the active claims overlapping a stay on one resource.
     *
     * <pre>{@code
     * SELECT *
     * FROM inventory_claims
     * WHERE inventory_resource_id = :resourceId
     *   AND status = 'ACTIVE'
     *   AND stay_range && daterange(:checkIn, :checkOut, '[)')
     * }</pre>
     *
     * <p>{@code &&} is PostgreSQL's range-overlap operator, and building the probe range as
     * {@code '[)'} makes it agree exactly with how claims are stored: a stay ending on the 5th does
     * not overlap one beginning on the 5th.</p>
     *
     * @param resourceId inventory resource being checked
     * @param checkIn first night, inclusive
     * @param checkOut departure date, exclusive
     * @return possibly empty list of conflicting claims
     */
    @Query("""
            SELECT *
            FROM inventory_claims
            WHERE inventory_resource_id = :resourceId
              AND status = 'ACTIVE'
              AND stay_range && daterange(:checkIn, :checkOut, '[)')
            """)
    List<InventoryClaim> findOverlapping(
            @Param("resourceId") UUID resourceId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut);

    /**
     * Returns temporary claims that have lapsed, for the release sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM inventory_claims
     * WHERE status = 'ACTIVE'
     *   AND expires_at IS NOT NULL
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The instant is bound by the caller rather than read by the database: time passing does not
     * change an index entry, so a worker has to transition stale claims explicitly. Until it does,
     * the nights stay consumed — which is the safe direction to fail.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of claims to return
     * @return possibly empty list of lapsed claims, most overdue first
     */
    @Query("""
            SELECT *
            FROM inventory_claims
            WHERE status = 'ACTIVE'
              AND expires_at IS NOT NULL
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<InventoryClaim> findLapsed(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns the claims belonging to one booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ?}. A stay spanning several pooled rooms has more
     * than one.</p>
     *
     * @param bookingId booking whose claims are wanted
     * @return possibly empty list of claims
     */
    List<InventoryClaim> findAllByBookingId(UUID bookingId);

    /**
     * Returns the claims belonging to one hold.
     *
     * <p>Spring derives {@code WHERE hold_id = ?}. Used when a hold is consumed or released, so its
     * claims move together with it.</p>
     *
     * @param holdId hold whose claims are wanted
     * @return possibly empty list of claims
     */
    List<InventoryClaim> findAllByHoldId(UUID holdId);
}
