package dev.ngb.backend.repository;

import dev.ngb.backend.model.ConfigurationLifecycle;
import dev.ngb.backend.model.Market;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the markets whose rules govern supply, contracting, payment, and payout.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for the {@code markets} table.</p>
 */
public interface MarketRepository extends ListCrudRepository<Market, UUID> {

    /**
     * Finds a market by its stable code.
     *
     * <p>Spring derives a single equality predicate from the property name:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM markets
     * WHERE market_code = ?
     * }</pre>
     *
     * <p>{@link Optional} is empty for an unknown code, which callers must treat as a refusal
     * rather than a reason to fall back to a default market.</p>
     *
     * @param marketCode ISO 3166-1 alpha-2 code
     * @return the market when it is configured
     */
    Optional<Market> findByMarketCode(String marketCode);

    /**
     * Finds a market by code only when it may actually be used.
     *
     * <p>Spring derives {@code WHERE market_code = ? AND lifecycle_state = ?}. Pushing the lifecycle
     * check into the query is deliberate: a caller that loaded the row first and forgot to check its
     * state would quote and contract under a draft or retired market.</p>
     *
     * @param marketCode ISO 3166-1 alpha-2 code
     * @param lifecycleState pass {@link ConfigurationLifecycle#ACTIVE}
     * @return the market when it is configured and usable
     */
    Optional<Market> findByMarketCodeAndLifecycleState(
            String marketCode,
            ConfigurationLifecycle lifecycleState);

    /**
     * Returns every market in one lifecycle state, ordered by code for stable presentation.
     *
     * <p>Spring derives {@code WHERE lifecycle_state = ? ORDER BY market_code ASC}.</p>
     *
     * @param lifecycleState state to list
     * @return possibly empty list of markets
     */
    List<Market> findAllByLifecycleStateOrderByMarketCodeAsc(ConfigurationLifecycle lifecycleState);
}
