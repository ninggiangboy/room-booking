package dev.ngb.backend.repository;

import dev.ngb.backend.model.Quote;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads offers made to guests.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code quotes}.</p>
 *
 * <p>Quotes are written once. There is no update path here for amounts, because re-pricing produces a
 * new quote and marks the old one superseded — an offer that changes after being shown is not an
 * offer the guest agreed to.</p>
 */
public interface QuoteRepository extends ListCrudRepository<Quote, UUID> {

    /**
     * Finds the offer a pricing request already produced.
     *
     * <p>Spring derives {@code WHERE idempotency_key = ?}. This is what makes a retry return the same
     * offer instead of minting a second one: a guest who double-taps must not be shown two different
     * prices for one trip.</p>
     *
     * @param idempotencyKey key the caller sent with the pricing request
     * @return the existing offer, or empty when this request is new
     */
    Optional<Quote> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds an offer by the identifier shown to the guest.
     *
     * <p>Spring derives {@code WHERE public_id = ?}. Used when a guest or an agent quotes it back,
     * so the internal key never has to be exposed.</p>
     *
     * @param publicId identifier as shown
     * @return the offer, or empty when no such offer exists
     */
    Optional<Quote> findByPublicId(String publicId);

    /**
     * Returns a guest's recent offers, newest first.
     *
     * <p>Spring derives {@code WHERE guest_account_holder_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param guestAccountHolderId guest whose offers are wanted
     * @return possibly empty list, most recent first
     */
    List<Quote> findAllByGuestAccountHolderIdOrderByCreatedAtDesc(UUID guestAccountHolderId);

    /**
     * Returns offers that lapsed without being accepted, for the expiry sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM quotes
     * WHERE status = 'OPEN'
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Expiring a quote also releases what it was holding — the promotion budget it reserved and,
     * where one exists, the inventory hold behind it. Until the sweep runs, both stay committed,
     * which is the safe direction to fail.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of offers to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM quotes
            WHERE status = 'OPEN'
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<Quote> findLapsed(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
