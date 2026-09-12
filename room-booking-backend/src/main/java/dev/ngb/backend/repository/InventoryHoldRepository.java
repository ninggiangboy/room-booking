package dev.ngb.backend.repository;

import dev.ngb.backend.model.InventoryHold;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and locks temporary inventory holds.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code inventory_holds}.</p>
 */
public interface InventoryHoldRepository extends ListCrudRepository<InventoryHold, UUID> {

    /**
     * Finds a hold by the identifier given to the client.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching {@code uk_inventory_holds_public_id}.
     * Clients quote this rather than the primary key, so an identifier that leaks discloses nothing
     * about how many holds the platform has issued.</p>
     *
     * @param publicId opaque client-facing identifier
     * @return the hold when the identifier is known
     */
    Optional<InventoryHold> findByPublicId(String publicId);

    /**
     * Loads a hold and locks it for the duration of the transaction.
     *
     * <pre>{@code
     * SELECT * FROM inventory_holds WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Consuming a hold and expiring it are
     * the same race from opposite ends: a sweeper and a payment confirmation can reach the row at the
     * same instant. The lock is what makes exactly one of them win, and the loser sees the settled
     * status rather than releasing nights a guest has just paid for.</p>
     *
     * @param id hold to lock
     * @return the locked hold when it exists
     */
    @Query("SELECT * FROM inventory_holds WHERE id = :id FOR UPDATE")
    Optional<InventoryHold> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns holds that have lapsed, for the expiry sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM inventory_holds
     * WHERE status = 'ACTIVE'
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>{@code SKIP LOCKED} lets several sweeper instances run concurrently without contending, and
     * skips a hold another transaction is currently confirming — which is exactly the hold that must
     * not be expired.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of holds to claim
     * @return possibly empty list of lapsed holds, most overdue first
     */
    @Query("""
            SELECT *
            FROM inventory_holds
            WHERE status = 'ACTIVE'
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<InventoryHold> claimLapsed(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
