package dev.ngb.backend.trust.internal.repository.feature;

import dev.ngb.backend.trust.internal.model.feature.RiskModelPrediction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads model estimates.
 *
 * <p>Nothing here returns an action, because a score is not one. Monitoring reads by model and
 * instant; an evaluation reads the predictions it consulted.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_model_predictions}.</p>
 */
public interface RiskModelPredictionRepository extends ListCrudRepository<RiskModelPrediction, UUID> {

    /**
     * Reads the predictions consulted by one evaluation.
     *
     * <pre>{@code
     * SELECT * FROM risk_model_predictions WHERE evaluation_key = :evaluationKey
     * }</pre>
     *
     * @param evaluationKey evaluation key
     * @return possibly empty list
     */
    List<RiskModelPrediction> findByEvaluationKey(String evaluationKey);

    /**
     * Reads one model's predictions over a window, for drift and calibration monitoring.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_model_predictions
     * WHERE model_key = :modelKey AND evaluated_at >= :from AND evaluated_at < :to
     * ORDER BY evaluated_at
     * }</pre>
     *
     * @param modelKey model key
     * @param from inclusive start
     * @param to exclusive end
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM risk_model_predictions
            WHERE model_key = :modelKey AND evaluated_at >= :from AND evaluated_at < :to
            ORDER BY evaluated_at
            """)
    List<RiskModelPrediction> findByModelAndWindow(@Param("modelKey") String modelKey,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to);
}
