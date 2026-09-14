package dev.ngb.backend.support.internal.repository.policy;

import dev.ngb.backend.support.internal.model.policy.InvestigationTemplateVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the investigation templates a case type is worked under.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code investigation_template_versions}.</p>
 */
public interface InvestigationTemplateVersionRepository extends ListCrudRepository<InvestigationTemplateVersion, UUID> {

    /**
     * Finds the template in force for a case type in a market.
     *
     * <pre>{@code
     * SELECT * FROM investigation_template_versions
     * WHERE case_type = :caseType
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY market_id NULLS LAST, effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * @param caseType case type being worked
     * @param marketId market, or null for the global fallback
     * @param at instant to resolve at
     * @return the template in force, when one is
     */
    @Query("""
            SELECT * FROM investigation_template_versions
            WHERE case_type = :caseType
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_id NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<InvestigationTemplateVersion> findInForce(@Param("caseType") String caseType,
            @Param("marketId") @Nullable UUID marketId, @Param("at") Instant at);
}
