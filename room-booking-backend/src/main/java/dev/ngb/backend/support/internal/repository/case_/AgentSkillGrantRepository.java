package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.AgentSkillGrant;
import dev.ngb.backend.support.internal.model.case_.SkillGrantState;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads what an individual agent currently holds.
 *
 * <p>Revocation closes an interval rather than deleting a row, so the live lookup filters on state
 * rather than on the presence of a row.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code agent_skill_grants}.</p>
 */
public interface AgentSkillGrantRepository extends ListCrudRepository<AgentSkillGrant, UUID> {

    /**
     * Finds the live grants an agent holds in a market.
     *
     * <pre>{@code
     * SELECT * FROM agent_skill_grants
     * WHERE account_holder_id = :accountHolderId
     *   AND state = 'ACTIVE'
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND (effective_until IS NULL OR effective_until > :at)
     * }</pre>
     *
     * @param accountHolderId agent
     * @param marketId market, or null for grants that are not market-scoped
     * @param at instant to treat as now
     * @return possibly empty list of live grants
     */
    @Query("""
            SELECT * FROM agent_skill_grants
            WHERE account_holder_id = :accountHolderId
              AND state = 'ACTIVE'
              AND (market_id = :marketId OR market_id IS NULL)
              AND (effective_until IS NULL OR effective_until > :at)
            """)
    List<AgentSkillGrant> findLive(@Param("accountHolderId") UUID accountHolderId,
            @Param("marketId") @Nullable UUID marketId, @Param("at") Instant at);

    /**
     * Lists everybody holding one skill in a given state, for staffing and for revocation sweeps.
     *
     * @param skillCode skill
     * @param state grant state
     * @return possibly empty list
     */
    List<AgentSkillGrant> findBySkillCodeAndState(String skillCode, SkillGrantState state);
}
