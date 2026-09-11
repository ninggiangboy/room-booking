package dev.ngb.backend.repository;

import dev.ngb.backend.model.MarketPolicyBundle;
import dev.ngb.backend.model.PolicyBundleType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the approved rule set that governs a decision, at the instant the decision is made.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code market_policy_bundles}. Bundles are immutable, so nothing here updates one.</p>
 */
public interface MarketPolicyBundleRepository extends ListCrudRepository<MarketPolicyBundle, UUID> {

    /**
     * Resolves the one bundle of a type in force for a market at a given instant.
     *
     * <p>{@code @Query} supplies the SQL because the caller must bind its own decision instant: the
     * database must not read a second clock, and a replay has to resolve the rules that applied
     * then, not the ones that apply now.</p>
     *
     * <pre>{@code
     * SELECT *
     * FROM market_policy_bundles
     * WHERE market_id = :marketId
     *   AND bundle_type = :bundleType
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     * }</pre>
     *
     * <p>The span is half-open, matching {@code ex_market_policy_bundles_no_overlap}, so at most one
     * row can match. An empty result means no approved rules exist and the caller must fail closed
     * rather than substitute a default.</p>
     *
     * @param marketId market whose rules are being resolved
     * @param bundleType rule set required
     * @param decisionInstant the command's single decision instant
     * @return the governing bundle version, when one is approved
     */
    @Query("""
            SELECT *
            FROM market_policy_bundles
            WHERE market_id = :marketId
              AND bundle_type = :bundleType
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
            """)
    Optional<MarketPolicyBundle> findGoverning(
            @Param("marketId") UUID marketId,
            @Param("bundleType") String bundleType,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every version of one rule set for a market, newest first.
     *
     * <p>Spring derives {@code WHERE market_id = ? AND bundle_type = ?} and descending ordering from
     * the {@code OrderByBundleVersionDesc} suffix. Used to show how rules changed over time.</p>
     *
     * @param marketId market whose history is being read
     * @param bundleType rule set to list
     * @return possibly empty list of versions, newest first
     */
    List<MarketPolicyBundle> findAllByMarketIdAndBundleTypeOrderByBundleVersionDesc(
            UUID marketId,
            PolicyBundleType bundleType);
}
