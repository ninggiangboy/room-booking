package dev.ngb.backend.support.internal.repository.remedy;

import dev.ngb.backend.support.internal.model.remedy.RemedyCatalogVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the allowlist of remedies support may grant.
 *
 * <p>Every remedy line names an entry here, and the trigger on {@code case_remedy_lines} reads the
 * same row to refuse a funder or a currency the entry does not permit.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code remedy_catalog_versions}.</p>
 */
public interface RemedyCatalogVersionRepository extends ListCrudRepository<RemedyCatalogVersion, UUID> {

    /**
     * Finds the catalogue entry in force for a remedy code in a market.
     *
     * <pre>{@code
     * SELECT * FROM remedy_catalog_versions
     * WHERE catalog_key = :catalogKey
     *   AND remedy_code = :remedyCode
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY market_id NULLS LAST, effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * @param catalogKey catalogue family
     * @param remedyCode remedy being granted
     * @param marketId market, or null for the global fallback
     * @param at instant to resolve at
     * @return the entry in force, when one is
     */
    @Query("""
            SELECT * FROM remedy_catalog_versions
            WHERE catalog_key = :catalogKey
              AND remedy_code = :remedyCode
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_id NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<RemedyCatalogVersion> findInForce(@Param("catalogKey") String catalogKey,
            @Param("remedyCode") String remedyCode, @Param("marketId") @Nullable UUID marketId,
            @Param("at") Instant at);

    /**
     * Lists every published entry of one catalogue version, for the agent workspace.
     *
     * <pre>{@code
     * SELECT * FROM remedy_catalog_versions
     * WHERE catalog_key = :catalogKey AND catalog_version = :catalogVersion
     *   AND status = 'PUBLISHED'
     * ORDER BY remedy_code
     * }</pre>
     *
     * @param catalogKey catalogue family
     * @param catalogVersion catalogue version
     * @return possibly empty list, ordered by remedy code
     */
    @Query("""
            SELECT * FROM remedy_catalog_versions
            WHERE catalog_key = :catalogKey AND catalog_version = :catalogVersion
              AND status = 'PUBLISHED'
            ORDER BY remedy_code
            """)
    List<RemedyCatalogVersion> findPublishedEntries(@Param("catalogKey") String catalogKey,
            @Param("catalogVersion") int catalogVersion);
}
