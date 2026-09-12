package dev.ngb.backend.repository;

import dev.ngb.backend.model.CollectionObligation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and locks what guests owe.
 *
 * <p>The two monetary ceilings are enforced by check constraints on the row, so every write that
 * changes a captured or refunded total must happen under {@link #findByIdForUpdate}.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code collection_obligations}.</p>
 */
public interface CollectionObligationRepository extends ListCrudRepository<CollectionObligation, UUID> {

    /**
     * Loads an obligation and holds it for the rest of the transaction.
     *
     * <pre>{@code
     * SELECT * FROM collection_obligations WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> This is the first lock in the canonical
     * order — obligation, then schedule item, then attempt, then operation — and it is what makes the
     * remaining collectable and refundable amounts safe to read and act on. No provider call may
     * happen while it is held.</p>
     *
     * @param id obligation to lock
     * @return the locked obligation, when it exists
     */
    @Query("SELECT * FROM collection_obligations WHERE id = :id FOR UPDATE")
    Optional<CollectionObligation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Finds an obligation by the reference quoted to the guest.
     *
     * <p>Spring derives {@code WHERE public_id = ?}.</p>
     *
     * @param publicId reference shown to the guest
     * @return the obligation, when one bears that reference
     */
    Optional<CollectionObligation> findByPublicId(String publicId);

    /**
     * Returns everything a booking owes, oldest first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at}.</p>
     *
     * @param bookingId booking whose obligations are wanted
     * @return possibly empty list of obligations
     */
    List<CollectionObligation> findAllByBookingIdOrderByCreatedAt(UUID bookingId);

    /**
     * Returns obligations that are due and still collectable, for the collection worker.
     *
     * <pre>{@code
     * SELECT *
     * FROM collection_obligations
     * WHERE state IN ('OPEN', 'ACTION_REQUIRED', 'PROCESSING', 'AUTHORIZED', 'PARTIALLY_PAID')
     *   AND due_at <= :decisionInstant
     * ORDER BY due_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The predicate names states rather than calling {@code now()}, so the index behind it stays
     * usable and the worker's decision instant is the one in the query.</p>
     *
     * @param decisionInstant the worker's single decision instant
     * @param batchSize maximum number of obligations to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM collection_obligations
            WHERE state IN ('OPEN', 'ACTION_REQUIRED', 'PROCESSING', 'AUTHORIZED', 'PARTIALLY_PAID')
              AND due_at <= :decisionInstant
            ORDER BY due_at
            LIMIT :batchSize
            """)
    List<CollectionObligation> findDue(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns obligations whose deadline has passed without being satisfied.
     *
     * <pre>{@code
     * SELECT *
     * FROM collection_obligations
     * WHERE state IN ('OPEN', 'ACTION_REQUIRED', 'PROCESSING', 'AUTHORIZED', 'PARTIALLY_PAID')
     *   AND expires_at IS NOT NULL
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of obligations to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM collection_obligations
            WHERE state IN ('OPEN', 'ACTION_REQUIRED', 'PROCESSING', 'AUTHORIZED', 'PARTIALLY_PAID')
              AND expires_at IS NOT NULL
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<CollectionObligation> findLapsed(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
