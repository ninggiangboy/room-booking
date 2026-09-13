package dev.ngb.backend.repository;

import dev.ngb.backend.model.RoutingPolicyVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the deterministic routing rules a queue assignment is made under.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code routing_policy_versions}.</p>
 */
public interface RoutingPolicyVersionRepository extends ListCrudRepository<RoutingPolicyVersion, UUID> {

    /**
     * Finds the routing policy in force for a market.
     *
     * <pre>{@code
     * SELECT * FROM routing_policy_versions
     * WHERE routing_policy_key = :routingPolicyKey
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY market_id NULLS LAST, effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * @param routingPolicyKey routing family
     * @param marketId market, or null for the global fallback
     * @param at instant to resolve at
     * @return the routing policy in force, when one is
     */
    @Query("""
            SELECT * FROM routing_policy_versions
            WHERE routing_policy_key = :routingPolicyKey
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_id NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<RoutingPolicyVersion> findInForce(@Param("routingPolicyKey") String routingPolicyKey,
            @Param("marketId") @Nullable UUID marketId, @Param("at") Instant at);
}
