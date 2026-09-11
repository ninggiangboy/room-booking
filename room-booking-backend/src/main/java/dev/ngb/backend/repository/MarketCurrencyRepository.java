package dev.ngb.backend.repository;

import dev.ngb.backend.model.MarketCurrency;
import dev.ngb.backend.model.MarketCurrencyId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and records the currencies a market supports.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link MarketCurrencyId} key and the {@code market_currencies} table.</p>
 */
public interface MarketCurrencyRepository
        extends ListCrudRepository<MarketCurrency, MarketCurrencyId> {

    /**
     * Adds a supported currency, ignoring a repeat of one already recorded.
     *
     * <p>Explicit SQL is required because the table has a composite primary key:</p>
     *
     * <pre>{@code
     * INSERT INTO market_currencies (market_id, currency, is_contract, created_at)
     * VALUES (:marketId, :currency, :isContract, :createdAt)
     * ON CONFLICT (market_id, currency) DO NOTHING
     * }</pre>
     *
     * @param marketId market gaining the currency
     * @param currency ISO 4217 code
     * @param isContract whether contracts are denominated in it
     * @param createdAt the command's decision instant
     * @return number of inserted rows, either zero or one
     */
    @Modifying
    @Query("""
            INSERT INTO market_currencies (market_id, currency, is_contract, created_at)
            VALUES (:marketId, :currency, :isContract, :createdAt)
            ON CONFLICT (market_id, currency) DO NOTHING
            """)
    int addCurrency(
            @Param("marketId") UUID marketId,
            @Param("currency") String currency,
            @Param("isContract") boolean isContract,
            @Param("createdAt") Instant createdAt);

    /**
     * Returns the currency a market's contracts are denominated in.
     *
     * <pre>{@code
     * SELECT * FROM market_currencies
     * WHERE market_id = :marketId AND is_contract = true
     * }</pre>
     *
     * <p>{@code uk_market_currencies_one_contract} guarantees at most one row matches. Presentation
     * currencies are deliberately excluded: showing a price in one currency and contracting in
     * another is legitimate, confusing the two is not.</p>
     *
     * @param marketId market being resolved
     * @return the contract currency when the market has one configured
     */
    @Query("SELECT * FROM market_currencies WHERE market_id = :marketId AND is_contract = true")
    Optional<MarketCurrency> findContractCurrency(@Param("marketId") UUID marketId);

    /**
     * Returns every currency a market supports.
     *
     * <p>Spring derives {@code WHERE market_id = ?} from the composite-key property path.</p>
     *
     * @param marketId market being listed
     * @return possibly empty list of supported currencies
     */
    List<MarketCurrency> findAllByIdMarketId(UUID marketId);
}
