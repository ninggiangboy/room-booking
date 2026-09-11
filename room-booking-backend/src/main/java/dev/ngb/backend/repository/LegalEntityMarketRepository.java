package dev.ngb.backend.repository;

import dev.ngb.backend.model.LegalEntityMarket;
import dev.ngb.backend.model.LegalEntityMarketId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records and reads which legal entity is accountable in which market, and when.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link LegalEntityMarketId} key and the {@code legal_entity_markets} table.</p>
 */
public interface LegalEntityMarketRepository
        extends ListCrudRepository<LegalEntityMarket, LegalEntityMarketId> {

    /**
     * Opens an accountability span for an entity in a market.
     *
     * <p>Explicit SQL is required for the composite key:</p>
     *
     * <pre>{@code
     * INSERT INTO legal_entity_markets
     *     (legal_entity_id, market_id, effective_from, effective_until, created_at)
     * VALUES (:legalEntityId, :marketId, :effectiveFrom, :effectiveUntil, :createdAt)
     * }</pre>
     *
     * <p>There is no conflict clause: {@code ex_legal_entity_markets_no_overlap} must reject an
     * overlapping span loudly rather than let it be silently ignored, because an overlap would make
     * the counterparty to a booking ambiguous.</p>
     *
     * @param legalEntityId entity taking accountability
     * @param marketId market being operated in
     * @param effectiveFrom UTC instant the span opens, inclusive
     * @param effectiveUntil UTC instant it closes, exclusive, or {@code null} while open-ended
     * @param createdAt the command's decision instant
     * @return number of inserted rows, always one when accepted
     */
    @Modifying
    @Query("""
            INSERT INTO legal_entity_markets
                (legal_entity_id, market_id, effective_from, effective_until, created_at)
            VALUES (:legalEntityId, :marketId, :effectiveFrom, :effectiveUntil, :createdAt)
            """)
    int openSpan(
            @Param("legalEntityId") UUID legalEntityId,
            @Param("marketId") UUID marketId,
            @Param("effectiveFrom") Instant effectiveFrom,
            @Param("effectiveUntil") Instant effectiveUntil,
            @Param("createdAt") Instant createdAt);

    /**
     * Returns the accountability history of one market, most recent span first.
     *
     * <p>Spring derives {@code WHERE market_id = ? ORDER BY effective_from DESC} from the
     * composite-key property path. Used to explain which entity contracted a historical booking.</p>
     *
     * @param marketId market whose history is being read
     * @return possibly empty list of spans, most recent first
     */
    List<LegalEntityMarket> findAllByIdMarketIdOrderByIdEffectiveFromDesc(UUID marketId);
}
