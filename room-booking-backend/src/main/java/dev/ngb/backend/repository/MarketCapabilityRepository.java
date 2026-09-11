package dev.ngb.backend.repository;

import dev.ngb.backend.model.MarketCapability;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads which capabilities, payment methods, and payout rails a market may use.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code market_capabilities}.</p>
 */
public interface MarketCapabilityRepository extends ListCrudRepository<MarketCapability, UUID> {

    /**
     * Resolves whether a capability is granted in a market at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM market_capabilities
     * WHERE market_id = :marketId
     *   AND capability = :capability
     *   AND COALESCE(method_key, '') = COALESCE(:methodKey, '')
     *   AND COALESCE(rail_key, '') = COALESCE(:railKey, '')
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     * }</pre>
     *
     * <p>The {@code COALESCE} pairs mirror {@code ex_market_capabilities_no_overlap}, so a lookup
     * with no method matches the row that also has none rather than missing it on {@code NULL}
     * inequality. An empty result means the capability is not granted: absence is a denial, not an
     * invitation to assume a default.</p>
     *
     * @param marketId market being checked
     * @param capability capability required
     * @param methodKey payment method, or {@code null} when not method-specific
     * @param railKey payout rail, or {@code null} when not rail-specific
     * @param decisionInstant the command's single decision instant
     * @return the grant in force, when one exists
     */
    @Query("""
            SELECT *
            FROM market_capabilities
            WHERE market_id = :marketId
              AND capability = :capability
              AND COALESCE(method_key, '') = COALESCE(:methodKey, '')
              AND COALESCE(rail_key, '') = COALESCE(:railKey, '')
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
            """)
    Optional<MarketCapability> findGrant(
            @Param("marketId") UUID marketId,
            @Param("capability") String capability,
            @Param("methodKey") String methodKey,
            @Param("railKey") String railKey,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every grant configured for a market, newest first.
     *
     * <p>Spring derives {@code WHERE market_id = ? ORDER BY effective_from DESC}. Used by operator
     * review rather than by domain decisions, which resolve one grant at a time.</p>
     *
     * @param marketId market whose configuration is being reviewed
     * @return possibly empty list of grants
     */
    List<MarketCapability> findAllByMarketIdOrderByEffectiveFromDesc(UUID marketId);
}
