package dev.ngb.backend.ml.internal.repository.label;

import dev.ngb.backend.ml.internal.model.label.LabelObservation;
import dev.ngb.backend.ml.internal.model.label.LabelValueState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads observed outcomes, including the ones that are not answers.
 *
 * <p>Append-only, and corrections arrive as new revisions. A reader that wants the standing
 * reading of an example takes the highest revision rather than the first row it finds.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code label_observations}.</p>
 */
public interface LabelObservationRepository extends ListCrudRepository<LabelObservation, UUID> {

    /**
     * Finds the standing reading of one example.
     *
     * <pre>{@code
     * SELECT * FROM label_observations
     * WHERE label_definition_id = :labelDefinitionId AND example_key = :exampleKey
     * ORDER BY revision_number DESC
     * LIMIT 1
     * }</pre>
     *
     * @param labelDefinitionId the target
     * @param exampleKey the example
     * @return the latest revision, when the example has been observed
     */
    @Query("""
            SELECT * FROM label_observations
            WHERE label_definition_id = :labelDefinitionId AND example_key = :exampleKey
            ORDER BY revision_number DESC
            LIMIT 1
            """)
    Optional<LabelObservation> findStanding(
            @Param("labelDefinitionId") UUID labelDefinitionId,
            @Param("exampleKey") String exampleKey);

    /**
     * Lists every revision of one example, oldest first, so a correction can be explained.
     *
     * @param labelDefinitionId the target
     * @param exampleKey the example
     * @return possibly empty list, by revision
     */
    List<LabelObservation> findByLabelDefinitionIdAndExampleKeyOrderByRevisionNumber(
            UUID labelDefinitionId, String exampleKey);

    /**
     * Lists the matured observations a dataset build may read, which is every reading whose
     * horizon closed and whose maturity delay ran before the cutoff, at its latest revision.
     *
     * <pre>{@code
     * SELECT o.* FROM label_observations o
     * WHERE o.label_definition_id = :labelDefinitionId
     *   AND o.matured_at IS NOT NULL
     *   AND o.matured_at <= :cutoff
     *   AND o.revision_number = (
     *       SELECT max(r.revision_number) FROM label_observations r
     *       WHERE r.label_definition_id = o.label_definition_id AND r.example_key = o.example_key)
     * ORDER BY o.prediction_at
     * }</pre>
     *
     * @param labelDefinitionId the target
     * @param cutoff the label cutoff the build declared
     * @return possibly empty list, by prediction instant
     */
    @Query("""
            SELECT o.* FROM label_observations o
            WHERE o.label_definition_id = :labelDefinitionId
              AND o.matured_at IS NOT NULL
              AND o.matured_at <= :cutoff
              AND o.revision_number = (
                  SELECT max(r.revision_number) FROM label_observations r
                  WHERE r.label_definition_id = o.label_definition_id AND r.example_key = o.example_key)
            ORDER BY o.prediction_at
            """)
    List<LabelObservation> findMaturedForBuild(
            @Param("labelDefinitionId") UUID labelDefinitionId, @Param("cutoff") Instant cutoff);

    /**
     * Counts observations by state for one target, which is how censoring and unresolved rates
     * are monitored rather than discovered in a model that learned slow outcomes never happen.
     *
     * <pre>{@code
     * SELECT value_state AS state, count(*) AS observation_count
     * FROM label_observations
     * WHERE label_definition_id = :labelDefinitionId
     * GROUP BY value_state
     * }</pre>
     *
     * @param labelDefinitionId the target
     * @return one row per observed state
     */
    @Query("""
            SELECT value_state AS state, count(*) AS observation_count
            FROM label_observations
            WHERE label_definition_id = :labelDefinitionId
            GROUP BY value_state
            """)
    List<StateCount> countByState(@Param("labelDefinitionId") UUID labelDefinitionId);

    /**
     * Lists one subject's observations, for an erasure or a subject-access request.
     *
     * @param entityPseudonym the subject
     * @return possibly empty list
     */
    List<LabelObservation> findByEntityPseudonym(String entityPseudonym);

    /**
     * How many observations of one target stand in a given state.
     *
     * @param state the observed state
     * @param observationCount how many observations are in it
     */
    record StateCount(LabelValueState state, long observationCount) {
    }
}
