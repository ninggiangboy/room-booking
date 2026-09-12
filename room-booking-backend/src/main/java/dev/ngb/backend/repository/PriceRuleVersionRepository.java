package dev.ngb.backend.repository;

import dev.ngb.backend.model.PriceRuleVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the immutable payloads of pricing rules.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code price_rule_versions}.</p>
 *
 * <p>Updates and deletes of published rows are refused by database triggers, not by this interface.
 * A published version is evidence of what a guest was charged under, and protecting it only in
 * application code would leave one forgotten path able to rewrite a receipt.</p>
 */
public interface PriceRuleVersionRepository extends ListCrudRepository<PriceRuleVersion, UUID> {

    /**
     * Returns the version of a rule that was in force at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM price_rule_versions
     * WHERE price_rule_id = :priceRuleId
     *   AND publication_state = 'PUBLISHED'
     *   AND effective_from <= :instant
     *   AND (effective_until IS NULL OR effective_until > :instant)
     * ORDER BY effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>Pricing a past stay passes that stay's instant, not the present one, so re-explaining an old
     * quote reads the terms the guest actually agreed to.</p>
     *
     * @param priceRuleId rule being resolved
     * @param instant instant the pricing decision was or is being made at
     * @return the version in force, or empty when the rule had none
     */
    @Query("""
            SELECT *
            FROM price_rule_versions
            WHERE price_rule_id = :priceRuleId
              AND publication_state = 'PUBLISHED'
              AND effective_from <= :instant
              AND (effective_until IS NULL OR effective_until > :instant)
            ORDER BY effective_from DESC
            LIMIT 1
            """)
    Optional<PriceRuleVersion> findInForce(
            @Param("priceRuleId") UUID priceRuleId,
            @Param("instant") Instant instant);

    /**
     * Returns every version of a rule, newest first.
     *
     * <p>Spring derives {@code WHERE price_rule_id = ? ORDER BY version_number DESC}.</p>
     *
     * @param priceRuleId rule whose history is wanted
     * @return possibly empty list, highest version number first
     */
    List<PriceRuleVersion> findAllByPriceRuleIdOrderByVersionNumberDesc(UUID priceRuleId);
}
