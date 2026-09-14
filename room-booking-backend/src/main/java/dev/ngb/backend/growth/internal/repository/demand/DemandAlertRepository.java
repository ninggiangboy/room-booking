package dev.ngb.backend.growth.internal.repository.demand;

import dev.ngb.backend.growth.internal.model.demand.DemandAlert;
import dev.ngb.backend.growth.internal.model.demand.DemandAlertKind;
import dev.ngb.backend.growth.internal.model.demand.DemandAlertState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the standing requests to be told when a price falls or a room opens.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code demand_alerts}.</p>
 */
public interface DemandAlertRepository extends ListCrudRepository<DemandAlert, UUID> {

    /**
     * Lists one guest's alerts in one state.
     *
     * @param accountHolderId the guest
     * @param state where the alerts stand
     * @return possibly empty list
     */
    List<DemandAlert> findByAccountHolderIdAndState(UUID accountHolderId, DemandAlertState state);

    /**
     * Lists the live alerts of one kind watching one listing.
     *
     * @param listingId the listing
     * @param alertKind what they watch for
     * @return possibly empty list
     */
    List<DemandAlert> findByListingIdAndAlertKindAndState(UUID listingId,
            DemandAlertKind alertKind, DemandAlertState state);

    /**
     * Lists the price-drop alerts on one listing whose baseline a new price would undercut by
     * enough to be worth telling somebody about.
     *
     * <pre>{@code
     * SELECT * FROM demand_alerts
     * WHERE listing_id = :listingId
     *   AND alert_kind = 'PRICE_DROP'
     *   AND state = 'ACTIVE'
     *   AND expires_at > :at
     *   AND baseline_amount_minor IS NOT NULL
     *   AND (threshold_percent IS NULL
     *        OR :priceMinor <= baseline_amount_minor * (1 - threshold_percent))
     *   AND (threshold_amount_minor IS NULL
     *        OR baseline_amount_minor - :priceMinor >= threshold_amount_minor)
     * ORDER BY created_at
     * }</pre>
     *
     * @param listingId the listing
     * @param priceMinor the new price, in integer minor units
     * @param at instant the price changed at
     * @return possibly empty list, oldest alert first
     */
    @Query("""
            SELECT * FROM demand_alerts
            WHERE listing_id = :listingId
              AND alert_kind = 'PRICE_DROP'
              AND state = 'ACTIVE'
              AND expires_at > :at
              AND baseline_amount_minor IS NOT NULL
              AND (threshold_percent IS NULL
                   OR :priceMinor <= baseline_amount_minor * (1 - threshold_percent))
              AND (threshold_amount_minor IS NULL
                   OR baseline_amount_minor - :priceMinor >= threshold_amount_minor)
            ORDER BY created_at
            """)
    List<DemandAlert> findTriggeredByPrice(@Param("listingId") UUID listingId,
            @Param("priceMinor") long priceMinor, @Param("at") Instant at);

    /**
     * Lists the alerts that have run out, which is the set that stops watching.
     *
     * <pre>{@code
     * SELECT * FROM demand_alerts
     * WHERE state = 'ACTIVE' AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM demand_alerts
            WHERE state = 'ACTIVE' AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<DemandAlert> findExpirable(@Param("at") Instant at);
}
