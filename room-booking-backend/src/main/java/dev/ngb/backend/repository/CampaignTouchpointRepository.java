package dev.ngb.backend.repository;

import dev.ngb.backend.model.CampaignTouchpoint;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the messages actually sent under a campaign.
 *
 * <p>Rows are append-only and each one names the consent relied on at the moment of sending, so
 * a complaint about an unwanted message is answered from the row that authorised it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code campaign_touchpoints}.</p>
 */
public interface CampaignTouchpointRepository extends ListCrudRepository<CampaignTouchpoint, UUID> {

    /**
     * Lists the messages sent to one audience member, newest first.
     *
     * @param audienceMembershipId the membership
     * @return possibly empty list, most recent first
     */
    List<CampaignTouchpoint> findByAudienceMembershipIdOrderByRequestedAtDesc(
            UUID audienceMembershipId);

    /**
     * Finds the touchpoint one notification intent belongs to.
     *
     * @param notificationIntentId the intent
     * @return the touchpoint, when the intent is one
     */
    Optional<CampaignTouchpoint> findByNotificationIntentId(UUID notificationIntentId);

    /**
     * Counts how many messages one audience member has had inside a window, which is what the
     * frequency cap is checked against.
     *
     * <pre>{@code
     * SELECT count(*) FROM campaign_touchpoints
     * WHERE audience_membership_id = :audienceMembershipId AND requested_at > :since
     * }</pre>
     *
     * @param audienceMembershipId the membership
     * @param since start of the frequency window
     * @return how many messages fell inside the window
     */
    @Query("""
            SELECT count(*) FROM campaign_touchpoints
            WHERE audience_membership_id = :audienceMembershipId AND requested_at > :since
            """)
    long countSince(@Param("audienceMembershipId") UUID audienceMembershipId,
            @Param("since") Instant since);

    /**
     * Lists the messages sent under one consent, which is what a person asking what was sent to
     * them under a permission they have since withdrawn is owed.
     *
     * <pre>{@code
     * SELECT * FROM campaign_touchpoints
     * WHERE communication_consent_id = :communicationConsentId
     * ORDER BY requested_at DESC
     * }</pre>
     *
     * @param communicationConsentId the consent
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM campaign_touchpoints
            WHERE communication_consent_id = :communicationConsentId
            ORDER BY requested_at DESC
            """)
    List<CampaignTouchpoint> findByConsent(
            @Param("communicationConsentId") UUID communicationConsentId);
}
