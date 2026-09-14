package dev.ngb.backend.admin.internal.repository.change;

import dev.ngb.backend.admin.internal.model.change.FeatureFlagState;
import dev.ngb.backend.admin.internal.model.ConfigurationScopeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a flag was set to, where, and over what interval.
 *
 * <p>The lookup is the same shape as the configuration one: the candidates and the interval are
 * both data, so what the runtime saw at a given instant is reconstructable afterwards.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code feature_flag_states}.</p>
 */
public interface FeatureFlagStateRepository extends ListCrudRepository<FeatureFlagState, UUID> {

    /**
     * Finds the state in force for one flag at one scope at one instant.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_states
     * WHERE feature_flag_definition_id = :featureFlagDefinitionId
     *   AND scope_type = :scopeType
     *   AND scope_id IS NOT DISTINCT FROM :scopeId
     *   AND market_code IS NOT DISTINCT FROM :marketCode
     *   AND effective_from <= :asOf
     *   AND (effective_until IS NULL OR effective_until > :asOf)
     * }</pre>
     *
     * @param featureFlagDefinitionId the flag
     * @param scopeType the scope kind
     * @param scopeId the scoped resource, or null
     * @param marketCode the market, or null
     * @param asOf the instant to read at
     * @return the state in force, when one is
     */
    @Query("""
            SELECT * FROM feature_flag_states
            WHERE feature_flag_definition_id = :featureFlagDefinitionId
              AND scope_type = :scopeType
              AND scope_id IS NOT DISTINCT FROM :scopeId
              AND market_code IS NOT DISTINCT FROM :marketCode
              AND effective_from <= :asOf
              AND (effective_until IS NULL OR effective_until > :asOf)
            """)
    Optional<FeatureFlagState> findInForce(
            @Param("featureFlagDefinitionId") UUID featureFlagDefinitionId,
            @Param("scopeType") ConfigurationScopeType scopeType,
            @Param("scopeId") @Nullable UUID scopeId,
            @Param("marketCode") @Nullable String marketCode, @Param("asOf") Instant asOf);

    /**
     * Lists the whole history of one flag, newest first.
     *
     * @param featureFlagDefinitionId the flag
     * @return possibly empty list, newest first
     */
    List<FeatureFlagState> findByFeatureFlagDefinitionIdOrderByEffectiveFromDesc(
            UUID featureFlagDefinitionId);

    /**
     * Lists the emergency pulls in a window. Every one of them is an incident somebody had to reach
     * for the brake during, whether or not an incident record was ever opened.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_states
     * WHERE origin = 'KILL_SWITCH_PULL' AND effective_from >= :from AND effective_from < :to
     * ORDER BY effective_from
     * }</pre>
     *
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM feature_flag_states
            WHERE origin = 'KILL_SWITCH_PULL' AND effective_from >= :from AND effective_from < :to
            ORDER BY effective_from
            """)
    List<FeatureFlagState> findPullsBetween(@Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Lists the flags that are on right now, which is the runtime picture a console shows.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_states
     * WHERE enabled AND effective_from <= :asOf
     *   AND (effective_until IS NULL OR effective_until > :asOf)
     * ORDER BY effective_from DESC
     * }</pre>
     *
     * @param asOf the instant to read at
     * @return possibly empty list, most recently set first
     */
    @Query("""
            SELECT * FROM feature_flag_states
            WHERE enabled AND effective_from <= :asOf
              AND (effective_until IS NULL OR effective_until > :asOf)
            ORDER BY effective_from DESC
            """)
    List<FeatureFlagState> findEnabled(@Param("asOf") Instant asOf);

    /**
     * Lists the states set for one incident, so a post-incident timeline can show what was switched
     * off and when it came back.
     *
     * <pre>{@code
     * SELECT * FROM feature_flag_states
     * WHERE incident_reference = :incidentReference
     * ORDER BY effective_from
     * }</pre>
     *
     * @param incidentReference the incident
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM feature_flag_states
            WHERE incident_reference = :incidentReference
            ORDER BY effective_from
            """)
    List<FeatureFlagState> findForIncident(
            @Param("incidentReference") String incidentReference);
}
