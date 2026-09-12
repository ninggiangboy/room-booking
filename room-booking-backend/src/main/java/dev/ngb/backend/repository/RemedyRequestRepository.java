package dev.ngb.backend.repository;

import dev.ngb.backend.model.RemedyRequest;
import dev.ngb.backend.model.RemedyActionType;
import dev.ngb.backend.model.RemedyTargetDomain;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what incidents have asked of other domains.
 *
 * <p>The identity lookup is what makes a repeated ask the same request; the outstanding queue is
 * what tells an owner the case is waiting on somebody else.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code remedy_requests}.</p>
 */
public interface RemedyRequestRepository extends ListCrudRepository<RemedyRequest, UUID> {

    /**
     * Finds an ask by its identity.
     *
     * <p>Spring derives {@code WHERE incident_id = ? AND target_domain = ? AND action_type = ? AND
     * idempotency_key = ?}, matching {@code uk_remedy_requests_identity}. A retry of the same ask
     * returns the same row.</p>
     *
     * @param incidentId incident
     * @param targetDomain domain being asked
     * @param actionType what is being asked for
     * @param idempotencyKey key for this ask
     * @return the existing request, when it was already made
     */
    Optional<RemedyRequest> findByIncidentIdAndTargetDomainAndActionTypeAndIdempotencyKey(
            UUID incidentId, RemedyTargetDomain targetDomain, RemedyActionType actionType,
            String idempotencyKey);

    /**
     * Lists asks nobody has answered yet.
     *
     * <pre>{@code
     * SELECT *
     * FROM remedy_requests
     * WHERE state IN ('REQUESTED', 'DISPATCHED', 'PENDING')
     * ORDER BY requested_at
     * }</pre>
     *
     * <p>Matches {@code idx_remedy_requests_open}. An urgent ask sitting here is an incident that looks
     * handled and is not.</p>
     *
     * @return possibly empty list, longest waiting first
     */
    @Query("""
            SELECT *
            FROM remedy_requests
            WHERE state IN ('REQUESTED', 'DISPATCHED', 'PENDING')
            ORDER BY requested_at
            """)
    List<RemedyRequest> findOutstanding();

    /**
     * Lists what one incident has asked for, newest first.
     *
     * <p>Spring derives {@code WHERE incident_id = ? ORDER BY requested_at DESC}.</p>
     *
     * @param incidentId incident
     * @return possibly empty list
     */
    List<RemedyRequest> findByIncidentIdOrderByRequestedAtDesc(UUID incidentId);
}
