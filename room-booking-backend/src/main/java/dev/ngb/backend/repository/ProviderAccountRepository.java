package dev.ngb.backend.repository;

import dev.ngb.backend.model.ProviderAccount;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Selects the approved external integration context an adapter may use.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code provider_accounts}. No method here returns a credential; the rows hold
 * secret-manager references only.</p>
 */
public interface ProviderAccountRepository extends ListCrudRepository<ProviderAccount, UUID> {

    /**
     * Resolves the account an adapter should use for a capability in a market at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM provider_accounts
     * WHERE capability = :capability
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND lifecycle_state = 'ACTIVE'
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     * ORDER BY market_id NULLS LAST, account_version DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>A market-scoped account wins over a global one — {@code NULLS LAST} is what expresses that
     * preference — and the newest credential version wins within a scope. An empty result means no
     * approved integration exists, and the caller fails closed instead of guessing a provider.</p>
     *
     * @param capability capability the adapter needs
     * @param marketId market being served
     * @param decisionInstant the command's single decision instant
     * @return the account to use, when one is approved
     */
    @Query("""
            SELECT *
            FROM provider_accounts
            WHERE capability = :capability
              AND (market_id = :marketId OR market_id IS NULL)
              AND lifecycle_state = 'ACTIVE'
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
            ORDER BY market_id NULLS LAST, account_version DESC
            LIMIT 1
            """)
    Optional<ProviderAccount> findUsable(
            @Param("capability") String capability,
            @Param("marketId") UUID marketId,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Finds one account by its key and credential version.
     *
     * <p>Spring derives {@code WHERE account_key = ? AND account_version = ?}, matching the
     * {@code uk_provider_accounts_key} unique constraint. This is how a stored external reference is
     * resolved back to the integration context that created it, including after rotation.</p>
     *
     * @param accountKey stable integration key
     * @param accountVersion credential version
     * @return the account when that version is known
     */
    Optional<ProviderAccount> findByAccountKeyAndAccountVersion(
            String accountKey,
            short accountVersion);

    /**
     * Returns every configured version of one integration, newest credential first.
     *
     * <p>Spring derives {@code WHERE account_key = ? ORDER BY account_version DESC}. Used when
     * reconciling provider resources recorded under a superseded credential.</p>
     *
     * @param accountKey stable integration key
     * @return possibly empty list of account versions
     */
    List<ProviderAccount> findAllByAccountKeyOrderByAccountVersionDesc(String accountKey);
}
