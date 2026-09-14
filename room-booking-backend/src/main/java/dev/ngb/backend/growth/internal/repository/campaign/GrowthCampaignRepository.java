package dev.ngb.backend.growth.internal.repository.campaign;

import dev.ngb.backend.growth.internal.model.campaign.GrowthCampaign;
import dev.ngb.backend.growth.internal.model.campaign.CampaignStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.campaign.CampaignStatus;
import dev.ngb.backend.growth.internal.model.campaign.GrowthCampaign;


/**
 * Reads the campaigns the platform runs and the limits each one set for itself.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code growth_campaigns}.</p>
 */
public interface GrowthCampaignRepository extends ListCrudRepository<GrowthCampaign, UUID> {

    /**
     * Finds a campaign by the key it is known by.
     *
     * @param campaignKey the stable campaign key
     * @return the campaign, when one is registered
     */
    Optional<GrowthCampaign> findByCampaignKey(String campaignKey);

    /**
     * Lists the campaigns in one lifecycle state.
     *
     * @param status where the campaigns stand
     * @return possibly empty list
     */
    List<GrowthCampaign> findByStatus(CampaignStatus status);

    /**
     * Lists the campaigns that may send right now, which is the set the send worker takes.
     *
     * <pre>{@code
     * SELECT * FROM growth_campaigns
     * WHERE status = 'RUNNING' AND send_window_from <= :at AND send_window_until > :at
     * ORDER BY send_window_until
     * }</pre>
     *
     * @param at instant the worker is running at
     * @return possibly empty list, soonest to close first
     */
    @Query("""
            SELECT * FROM growth_campaigns
            WHERE status = 'RUNNING' AND send_window_from <= :at AND send_window_until > :at
            ORDER BY send_window_until
            """)
    List<GrowthCampaign> findSendable(@Param("at") Instant at);

    /**
     * Lists the campaigns that ran without a holdout, which is the set whose results cannot be
     * read as evidence of anything.
     *
     * <pre>{@code
     * SELECT * FROM growth_campaigns
     * WHERE holdout_share = 0 AND status IN ('RUNNING', 'COMPLETED')
     * ORDER BY send_window_from DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM growth_campaigns
            WHERE holdout_share = 0 AND status IN ('RUNNING', 'COMPLETED')
            ORDER BY send_window_from DESC
            """)
    List<GrowthCampaign> findWithoutHoldout();
}
