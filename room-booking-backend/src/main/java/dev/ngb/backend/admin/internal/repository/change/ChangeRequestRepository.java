package dev.ngb.backend.admin.internal.repository.change;

import dev.ngb.backend.admin.internal.model.change.ChangeRequest;
import dev.ngb.backend.admin.internal.model.change.ChangeRequestState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.admin.internal.model.change.ChangeRequest;
import dev.ngb.backend.admin.internal.model.change.ChangeRequestState;


/**
 * Reads the maker-checker queue.
 *
 * <p>One path for a configured value, a feature flag and an artifact another domain owns, so that
 * the approval trail for publishing a policy and for changing a timeout look the same to whoever
 * has to audit them.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code change_requests}.</p>
 */
public interface ChangeRequestRepository extends ListCrudRepository<ChangeRequest, UUID> {

    /**
     * Lists the requests in one state, oldest first, which is the reviewer queue.
     *
     * @param requestState where the request stands
     * @return possibly empty list, longest waiting first
     */
    List<ChangeRequest> findByRequestStateOrderByRequestedAt(ChangeRequestState requestState);

    /**
     * Lists the requests against one setting, newest first.
     *
     * @param configurationSettingId the setting
     * @return possibly empty list, newest first
     */
    List<ChangeRequest> findByConfigurationSettingIdOrderByRequestedAtDesc(
            UUID configurationSettingId);

    /**
     * Lists the requests against one feature flag, newest first.
     *
     * @param featureFlagDefinitionId the flag
     * @return possibly empty list, newest first
     */
    List<ChangeRequest> findByFeatureFlagDefinitionIdOrderByRequestedAtDesc(
            UUID featureFlagDefinitionId);

    /**
     * Lists the requests to publish one governed artifact, which is how another domain finds the
     * approval trail for something it owns.
     *
     * @param artifactDomain the owning domain
     * @param artifactReference the artifact
     * @return possibly empty list
     */
    List<ChangeRequest> findByArtifactDomainAndArtifactReference(String artifactDomain,
            String artifactReference);

    /**
     * Lists the requests awaiting a sign-off from one role, which is what an approver opens.
     *
     * <pre>{@code
     * SELECT * FROM change_requests
     * WHERE request_state = 'AWAITING_APPROVAL'
     *   AND :approvalRole = ANY (required_approval_roles)
     * ORDER BY impact_class DESC, requested_at
     * }</pre>
     *
     * @param approvalRole the approval role
     * @return possibly empty list, most consequential and longest waiting first
     */
    @Query("""
            SELECT * FROM change_requests
            WHERE request_state = 'AWAITING_APPROVAL'
              AND :approvalRole = ANY (required_approval_roles)
            ORDER BY impact_class DESC, requested_at
            """)
    List<ChangeRequest> findAwaiting(@Param("approvalRole") String approvalRole);

    /**
     * Lists more than one request in flight against the same thing, which is the conflict a change
     * about to be approved has to be checked for.
     *
     * <pre>{@code
     * SELECT * FROM change_requests
     * WHERE request_state IN ('DRAFT', 'VALIDATED', 'AWAITING_APPROVAL', 'APPROVED')
     *   AND configuration_setting_id = :configurationSettingId
     *   AND id <> :excludingId
     * ORDER BY requested_at
     * }</pre>
     *
     * @param configurationSettingId the setting
     * @param excludingId the request being checked
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM change_requests
            WHERE request_state IN ('DRAFT', 'VALIDATED', 'AWAITING_APPROVAL', 'APPROVED')
              AND configuration_setting_id = :configurationSettingId
              AND id <> :excludingId
            ORDER BY requested_at
            """)
    List<ChangeRequest> findCompeting(
            @Param("configurationSettingId") UUID configurationSettingId,
            @Param("excludingId") UUID excludingId);

    /**
     * Lists the expedited changes whose review is overdue. An expedited change skips waiting, never
     * reviewing, and this is where the difference is kept honest.
     *
     * <pre>{@code
     * SELECT * FROM change_requests
     * WHERE expedited AND expedited_review_due_at <= :asOf AND request_state = 'APPLIED'
     * ORDER BY expedited_review_due_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM change_requests
            WHERE expedited AND expedited_review_due_at <= :asOf AND request_state = 'APPLIED'
            ORDER BY expedited_review_due_at
            """)
    List<ChangeRequest> findExpeditedReviewOverdue(@Param("asOf") Instant asOf);
}
