package dev.ngb.backend.repository;

import dev.ngb.backend.model.CampaignAudienceMembership;
import dev.ngb.backend.model.CampaignArm;
import dev.ngb.backend.model.CampaignMembershipState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who is in a campaign audience, in which arm, and under whose consent.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code campaign_audience_memberships}.</p>
 */
public interface CampaignAudienceMembershipRepository extends ListCrudRepository<CampaignAudienceMembership, UUID> {

    /**
     * Finds one person's place in one campaign.
     *
     * @param growthCampaignId the campaign
     * @param accountHolderId the person
     * @return their membership, when they are in the audience
     */
    Optional<CampaignAudienceMembership> findByGrowthCampaignIdAndAccountHolderId(
            UUID growthCampaignId, UUID accountHolderId);

    /**
     * Lists the audience of one campaign in one state.
     *
     * @param growthCampaignId the campaign
     * @param state where the memberships stand
     * @return possibly empty list
     */
    List<CampaignAudienceMembership> findByGrowthCampaignIdAndState(UUID growthCampaignId,
            CampaignMembershipState state);

    /**
     * Lists the next people to contact: treatment arm, not suppressed, and holding a consent.
     *
     * <pre>{@code
     * SELECT * FROM campaign_audience_memberships
     * WHERE growth_campaign_id = :growthCampaignId
     *   AND arm = 'TREATMENT'
     *   AND state IN ('ELIGIBLE', 'CONTACTED')
     *   AND communication_consent_id IS NOT NULL
     * ORDER BY entered_at
     * LIMIT :limit
     * }</pre>
     *
     * @param growthCampaignId the campaign
     * @param limit how many to take
     * @return possibly empty list, longest in the audience first
     */
    @Query("""
            SELECT * FROM campaign_audience_memberships
            WHERE growth_campaign_id = :growthCampaignId
              AND arm = 'TREATMENT'
              AND state IN ('ELIGIBLE', 'CONTACTED')
              AND communication_consent_id IS NOT NULL
            ORDER BY entered_at
            LIMIT :limit
            """)
    List<CampaignAudienceMembership> findContactable(
            @Param("growthCampaignId") UUID growthCampaignId, @Param("limit") int limit);

    /**
     * Counts conversions in each arm of one campaign, which is the raw material an uplift result
     * is computed from and never a substitute for one.
     *
     * <pre>{@code
     * SELECT arm,
     *        count(*) AS subject_count,
     *        count(*) FILTER (WHERE state = 'CONVERTED') AS converted_count
     * FROM campaign_audience_memberships
     * WHERE growth_campaign_id = :growthCampaignId
     * GROUP BY arm
     * }</pre>
     *
     * @param growthCampaignId the campaign
     * @return one row per arm
     */
    @Query("""
            SELECT arm,
                   count(*) AS subject_count,
                   count(*) FILTER (WHERE state = 'CONVERTED') AS converted_count
            FROM campaign_audience_memberships
            WHERE growth_campaign_id = :growthCampaignId
            GROUP BY arm
            """)
    List<ArmOutcome> countByArm(@Param("growthCampaignId") UUID growthCampaignId);

    /**
     * How one arm of a campaign audience behaved.
     *
     * @param arm contacted or held out
     * @param subjectCount how many people were in the arm
     * @param convertedCount how many of them converted
     */
    record ArmOutcome(CampaignArm arm, long subjectCount, long convertedCount) {}
}
