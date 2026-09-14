package dev.ngb.backend.stay.internal.repository.stay;

import dev.ngb.backend.stay.internal.model.stay.StayOutcomeDecision;
import dev.ngb.backend.stay.internal.model.stay.StayOutcomeResult;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.stay.internal.model.stay.StayOutcomeDecision;
import dev.ngb.backend.stay.internal.model.stay.StayOutcomeResult;


/**
 * Reads what operations proposed about a stay.
 *
 * <p>A proposal is not a transition. The pending queue is what the service reading booking answers
 * works through.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code stay_outcome_decisions}.</p>
 */
public interface StayOutcomeDecisionRepository extends ListCrudRepository<StayOutcomeDecision, UUID> {

    /**
     * Finds the proposal currently standing for a stay.
     *
     * <pre>{@code
     * SELECT *
     * FROM stay_outcome_decisions
     * WHERE operational_stay_id = :operationalStayId
     *   AND superseded_at IS NULL
     *   AND result <> 'WITHDRAWN'
     * }</pre>
     *
     * <p>Matches {@code uk_stay_outcome_decisions_live}, so at most one row can come back.</p>
     *
     * @param operationalStayId stay
     * @return the live proposal, when there is one
     */
    @Query("""
            SELECT *
            FROM stay_outcome_decisions
            WHERE operational_stay_id = :operationalStayId
              AND superseded_at IS NULL
              AND result <> 'WITHDRAWN'
            """)
    Optional<StayOutcomeDecision> findLive(@Param("operationalStayId") UUID operationalStayId);

    /**
     * Lists proposals booking has not yet answered.
     *
     * <p>Spring derives {@code WHERE result = ? ORDER BY requested_at}, matching
     * {@code idx_stay_outcome_decisions_pending} when the result is {@code PENDING}.</p>
     *
     * @param result result to match, normally {@code PENDING}
     * @return possibly empty list, longest waiting first
     */
    List<StayOutcomeDecision> findByResultOrderByRequestedAt(StayOutcomeResult result);

    /**
     * Lists every proposal ever made about a stay, newest first.
     *
     * <p>Spring derives {@code WHERE operational_stay_id = ? ORDER BY created_at DESC}. Superseded rows
     * are kept, because a later evaluation must never erase what an earlier one saw.</p>
     *
     * @param operationalStayId stay
     * @return possibly empty list, newest first
     */
    List<StayOutcomeDecision> findByOperationalStayIdOrderByCreatedAtDesc(UUID operationalStayId);
}
