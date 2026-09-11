package dev.ngb.backend.repository;

import dev.ngb.backend.model.MarketTimeZone;
import dev.ngb.backend.model.MarketTimeZoneId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads and records the IANA zones properties in a market may sit in.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link MarketTimeZoneId} key and the {@code market_time_zones} table.</p>
 */
public interface MarketTimeZoneRepository
        extends ListCrudRepository<MarketTimeZone, MarketTimeZoneId> {

    /**
     * Adds a supported zone, ignoring a repeat of one already recorded.
     *
     * <pre>{@code
     * INSERT INTO market_time_zones (market_id, time_zone, created_at)
     * VALUES (:marketId, :timeZone, :createdAt)
     * ON CONFLICT (market_id, time_zone) DO NOTHING
     * }</pre>
     *
     * @param marketId market gaining the zone
     * @param timeZone full IANA {@code Region/City} identifier
     * @param createdAt the command's decision instant
     * @return number of inserted rows, either zero or one
     */
    @Modifying
    @Query("""
            INSERT INTO market_time_zones (market_id, time_zone, created_at)
            VALUES (:marketId, :timeZone, :createdAt)
            ON CONFLICT (market_id, time_zone) DO NOTHING
            """)
    int addTimeZone(
            @Param("marketId") UUID marketId,
            @Param("timeZone") String timeZone,
            @Param("createdAt") Instant createdAt);

    /**
     * Returns every zone a market permits.
     *
     * <p>Spring derives {@code WHERE market_id = ?} from the composite-key property path. A market
     * can span several zones, so this bounds which zone a property may declare; the property's own
     * zone stays authoritative for its stay dates.</p>
     *
     * @param marketId market being listed
     * @return possibly empty list of permitted zones
     */
    List<MarketTimeZone> findAllByIdMarketId(UUID marketId);
}
