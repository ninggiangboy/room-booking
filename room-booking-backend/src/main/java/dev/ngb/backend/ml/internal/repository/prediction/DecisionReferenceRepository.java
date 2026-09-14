package dev.ngb.backend.ml.internal.repository.prediction;

import dev.ngb.backend.ml.internal.model.prediction.DecisionReference;
import dev.ngb.backend.ml.internal.model.prediction.DecisionDomain;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the monitoring index of domain decisions a model or a fallback fed into.
 *
 * <p>Nothing here is authoritative. What it supports is the join an evaluation needs: which
 * decisions used a prediction, which fell back, and which the policy overrode -- because a model
 * is not credited for an outcome the policy changed.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code decision_references}.</p>
 */
public interface DecisionReferenceRepository extends ListCrudRepository<DecisionReference, UUID> {

    /**
     * Finds the index row for one domain decision.
     *
     * @param decisionDomain the owning domain
     * @param decisionReference that domain's identifier for the decision
     * @return the index row, when the decision was indexed
     */
    Optional<DecisionReference> findByDecisionDomainAndDecisionReference(
            DecisionDomain decisionDomain, String decisionReference);

    /**
     * Lists the decisions that cited one prediction.
     *
     * @param predictionRecordId the prediction
     * @return possibly empty list
     */
    List<DecisionReference> findByPredictionRecordId(UUID predictionRecordId);

    /**
     * Lists the decisions in one domain over a window that fell back rather than using a
     * prediction. Evaluating only the decisions a model informed hides what the outage cost.
     *
     * <pre>{@code
     * SELECT * FROM decision_references
     * WHERE decision_domain = :decisionDomain
     *   AND fallback_reason IS NOT NULL
     *   AND decided_at >= :from AND decided_at < :to
     * ORDER BY decided_at
     * }</pre>
     *
     * @param decisionDomain the owning domain
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM decision_references
            WHERE decision_domain = :decisionDomain
              AND fallback_reason IS NOT NULL
              AND decided_at >= :from AND decided_at < :to
            ORDER BY decided_at
            """)
    List<DecisionReference> findFellBack(@Param("decisionDomain") DecisionDomain decisionDomain,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Lists the decisions where policy changed the outcome away from what was recommended,
     * which measures the policy as much as the model.
     *
     * <pre>{@code
     * SELECT * FROM decision_references
     * WHERE decision_domain = :decisionDomain
     *   AND policy_override_applied
     *   AND decided_at >= :from AND decided_at < :to
     * ORDER BY decided_at
     * }</pre>
     *
     * @param decisionDomain the owning domain
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM decision_references
            WHERE decision_domain = :decisionDomain
              AND policy_override_applied
              AND decided_at >= :from AND decided_at < :to
            ORDER BY decided_at
            """)
    List<DecisionReference> findOverridden(
            @Param("decisionDomain") DecisionDomain decisionDomain, @Param("from") Instant from,
            @Param("to") Instant to);
}
