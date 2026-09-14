package dev.ngb.backend.support.internal.repository.remedy;

import dev.ngb.backend.support.internal.model.remedy.CaseOffer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.remedy.CaseOffer;


/**
 * Reads and locks the structured offers on a case.
 *
 * <p>Concurrent acceptance and withdrawal are serialized on the row, and the first valid committed
 * transition wins, which is why the lock exists at all.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_offers}.</p>
 */
public interface CaseOfferRepository extends ListCrudRepository<CaseOffer, UUID> {

    /**
     * Lists the offers still open on a case.
     *
     * <pre>{@code
     * SELECT * FROM case_offers
     * WHERE support_case_id = :supportCaseId
     *   AND state IN ('DRAFT', 'SENT', 'VIEWED')
     * ORDER BY offer_number
     * }</pre>
     *
     * @param supportCaseId case
     * @return possibly empty list, in offer order
     */
    @Query("""
            SELECT * FROM case_offers
            WHERE support_case_id = :supportCaseId
              AND state IN ('DRAFT', 'SENT', 'VIEWED')
            ORDER BY offer_number
            """)
    List<CaseOffer> findOpen(@Param("supportCaseId") UUID supportCaseId);

    /**
     * Locks one offer before it is accepted, rejected or withdrawn.
     *
     * <pre>{@code
     * SELECT * FROM case_offers WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param id offer to lock
     * @return the locked offer, when it exists
     */
    @Query("SELECT * FROM case_offers WHERE id = :id FOR UPDATE")
    Optional<CaseOffer> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims offers that have passed their expiry.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_offers
     * WHERE state IN ('SENT', 'VIEWED') AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum offers to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM case_offers
            WHERE state IN ('SENT', 'VIEWED') AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseOffer> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
