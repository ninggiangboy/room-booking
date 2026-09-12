package dev.ngb.backend.repository;

import dev.ngb.backend.model.CancellationPolicyDefinition;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads cancellation policy families.
 *
 * <p>A family carries no rules. Selecting one at quote time then means resolving its applicable
 * {@code CancellationPolicyVersion}, which is the row a booking actually cites.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code cancellation_policy_definitions}.</p>
 */
public interface CancellationPolicyDefinitionRepository extends ListCrudRepository<CancellationPolicyDefinition, UUID> {

    /**
     * Finds a family by its stable key.
     *
     * <p>Spring derives {@code WHERE policy_key = ?}, matching {@code uk_cancellation_policy_definitions_key}.</p>
     *
     * @param policyKey stable key
     * @return the family, when one exists
     */
    Optional<CancellationPolicyDefinition> findByPolicyKey(String policyKey);

    /**
     * Returns the families a host may attach to their own supply in a market.
     *
     * <pre>{@code
     * SELECT *
     * FROM cancellation_policy_definitions
     * WHERE lifecycle = 'ACTIVE'
     *   AND host_selectable = true
     *   AND (market_code IS NULL OR market_code = :marketCode)
     * ORDER BY policy_key
     * }</pre>
     *
     * <p>A null market code on the row means the family is offered everywhere, so it is included
     * alongside the market's own.</p>
     *
     * @param marketCode market the supply sits in
     * @return possibly empty list, ordered by key
     */
    @Query("""
            SELECT *
            FROM cancellation_policy_definitions
            WHERE lifecycle = 'ACTIVE'
              AND host_selectable = true
              AND (market_code IS NULL OR market_code = :marketCode)
            ORDER BY policy_key
            """)
    List<CancellationPolicyDefinition> findHostSelectable(@Param("marketCode") String marketCode);
}
