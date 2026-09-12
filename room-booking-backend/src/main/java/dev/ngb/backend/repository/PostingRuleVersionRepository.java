package dev.ngb.backend.repository;

import dev.ngb.backend.model.PostingRuleVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves which posting rule applies to a source fact.
 *
 * <p>There is deliberately no "latest rule" finder. A replay must use the version that was effective
 * when the fact occurred, which is why every selection binds an instant.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code posting_rule_versions}.</p>
 */
public interface PostingRuleVersionRepository extends ListCrudRepository<PostingRuleVersion, UUID> {

    /**
     * Finds the exact version a transaction pinned.
     *
     * <p>Spring derives {@code WHERE rule_key = ? AND version_number = ?}, matching
     * {@code uk_posting_rule_versions_number}.</p>
     *
     * @param ruleKey stable rule identity
     * @param versionNumber version within that key
     * @return the version, when one exists
     */
    Optional<PostingRuleVersion> findByRuleKeyAndVersionNumber(String ruleKey, int versionNumber);

    /**
     * Selects the published rule that applies to a fact at a given instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM posting_rule_versions
     * WHERE source_fact_type = :sourceFactType
     *   AND accounting_book_id = :accountingBookId
     *   AND publication_state = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     *   AND (market_code IS NULL OR market_code = :marketCode)
     * ORDER BY precedence, market_code NULLS LAST, effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>The instant is supplied rather than read from the database clock, so that reprocessing a
     * two-year-old fact resolves the rule that was correct two years ago. A market-specific rule wins
     * over a general one at equal precedence.</p>
     *
     * @param sourceFactType allowlisted kind of fact being posted
     * @param accountingBookId book the entry will post into
     * @param marketCode market of the fact, or null to match only general rules
     * @param at instant the rule must be effective at
     * @return the rule to apply, when one is published
     */
    @Query("""
            SELECT *
            FROM posting_rule_versions
            WHERE source_fact_type = :sourceFactType
              AND accounting_book_id = :accountingBookId
              AND publication_state = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
              AND (market_code IS NULL OR market_code = :marketCode)
            ORDER BY precedence, market_code NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<PostingRuleVersion> findApplicable(
            @Param("sourceFactType") String sourceFactType,
            @Param("accountingBookId") UUID accountingBookId,
            @Param("marketCode") @Nullable String marketCode,
            @Param("at") Instant at);
}
