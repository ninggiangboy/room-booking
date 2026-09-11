package dev.ngb.backend.repository;

import dev.ngb.backend.model.MarketLocale;
import dev.ngb.backend.model.MarketLocaleId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads and records the presentation locales a market supports.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link MarketLocaleId} key and the {@code market_locales} table.</p>
 */
public interface MarketLocaleRepository extends ListCrudRepository<MarketLocale, MarketLocaleId> {

    /**
     * Adds a supported locale, ignoring a repeat of one already recorded.
     *
     * <pre>{@code
     * INSERT INTO market_locales (market_id, locale, is_default, created_at)
     * VALUES (:marketId, :locale, :isDefault, :createdAt)
     * ON CONFLICT (market_id, locale) DO NOTHING
     * }</pre>
     *
     * @param marketId market gaining the locale
     * @param locale BCP 47 locale
     * @param isDefault whether it is used when the reader states no preference
     * @param createdAt the command's decision instant
     * @return number of inserted rows, either zero or one
     */
    @Modifying
    @Query("""
            INSERT INTO market_locales (market_id, locale, is_default, created_at)
            VALUES (:marketId, :locale, :isDefault, :createdAt)
            ON CONFLICT (market_id, locale) DO NOTHING
            """)
    int addLocale(
            @Param("marketId") UUID marketId,
            @Param("locale") String locale,
            @Param("isDefault") boolean isDefault,
            @Param("createdAt") Instant createdAt);

    /**
     * Returns every locale a market supports.
     *
     * <p>Spring derives {@code WHERE market_id = ?} from the composite-key property path. Used to
     * decide whether a requested locale can be honoured before falling back to the default.</p>
     *
     * @param marketId market being listed
     * @return possibly empty list of supported locales
     */
    List<MarketLocale> findAllByIdMarketId(UUID marketId);
}
