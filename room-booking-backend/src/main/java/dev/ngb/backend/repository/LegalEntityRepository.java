package dev.ngb.backend.repository;

import dev.ngb.backend.model.LegalEntity;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the platform entities accountable for operating in a market.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code legal_entities}.</p>
 */
public interface LegalEntityRepository extends ListCrudRepository<LegalEntity, UUID> {

    /**
     * Finds an entity by its stable key.
     *
     * <p>Spring derives {@code WHERE entity_key = ?}, matching the {@code uk_legal_entities_key}
     * unique constraint.</p>
     *
     * @param entityKey stable operator-facing key
     * @return the entity when it is configured
     */
    Optional<LegalEntity> findByEntityKey(String entityKey);

    /**
     * Resolves which entity was accountable in a market at a given instant.
     *
     * <p>{@code @Query} joins through the accountability span because the answer is a property of
     * the span, not of the entity:</p>
     *
     * <pre>{@code
     * SELECT e.*
     * FROM legal_entities e
     * JOIN legal_entity_markets m ON m.legal_entity_id = e.id
     * WHERE m.market_id = :marketId
     *   AND m.effective_from <= :decisionInstant
     *   AND (m.effective_until IS NULL OR m.effective_until > :decisionInstant)
     * }</pre>
     *
     * <p>{@code ex_legal_entity_markets_no_overlap} guarantees at most one row matches, which is
     * what makes "who is the counterparty to this booking" have exactly one answer. Binding the
     * caller's decision instant means a replay resolves the entity that contracted the booking, not
     * whichever entity is accountable today.</p>
     *
     * @param marketId market being operated in
     * @param decisionInstant the command's single decision instant
     * @return the accountable entity, when one is configured
     */
    @Query("""
            SELECT e.*
            FROM legal_entities e
            JOIN legal_entity_markets m ON m.legal_entity_id = e.id
            WHERE m.market_id = :marketId
              AND m.effective_from <= :decisionInstant
              AND (m.effective_until IS NULL OR m.effective_until > :decisionInstant)
            """)
    Optional<LegalEntity> findAccountableIn(
            @Param("marketId") UUID marketId,
            @Param("decisionInstant") Instant decisionInstant);
}
