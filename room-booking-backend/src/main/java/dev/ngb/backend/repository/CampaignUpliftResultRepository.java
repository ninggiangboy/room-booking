package dev.ngb.backend.repository;

import dev.ngb.backend.model.CampaignUpliftResult;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a campaign measurably changed against its own holdout.
 *
 * <p>Rows are append-only and cannot exist without a holdout, so nothing here is a redemption
 * count wearing the word uplift.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code campaign_uplift_results}.</p>
 */
public interface CampaignUpliftResultRepository extends ListCrudRepository<CampaignUpliftResult, UUID> {

    /**
     * Lists the measured results for one campaign, newest first.
     *
     * @param growthCampaignId the campaign
     * @return possibly empty list, most recent first
     */
    List<CampaignUpliftResult> findByGrowthCampaignIdOrderByComputedAtDesc(
            UUID growthCampaignId);

    /**
     * Finds the result for one campaign, analysis run and metric.
     *
     * @param growthCampaignId the campaign
     * @param experimentAnalysisRunId the analysis run
     * @param metricDefinitionId the metric
     * @return the result, when it was computed
     */
    Optional<CampaignUpliftResult> findByGrowthCampaignIdAndExperimentAnalysisRunIdAndMetricDefinitionId(
            UUID growthCampaignId, UUID experimentAnalysisRunId, UUID metricDefinitionId);

    /**
     * Lists the campaigns measured as having changed nothing or made things worse, which is the
     * set a decision to keep spending on them has to answer for.
     *
     * <pre>{@code
     * SELECT * FROM campaign_uplift_results
     * WHERE conclusion IN ('NO_DETECTED_EFFECT', 'HARMFUL')
     *   AND computed_at >= :since
     * ORDER BY incremental_cost_minor DESC
     * }</pre>
     *
     * @param since instant to look back to
     * @return possibly empty list, most expensive first
     */
    @Query("""
            SELECT * FROM campaign_uplift_results
            WHERE conclusion IN ('NO_DETECTED_EFFECT', 'HARMFUL')
              AND computed_at >= :since
            ORDER BY incremental_cost_minor DESC
            """)
    List<CampaignUpliftResult> findUnproven(@Param("since") Instant since);
}
