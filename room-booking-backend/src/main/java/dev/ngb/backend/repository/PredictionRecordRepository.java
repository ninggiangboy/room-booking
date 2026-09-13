package dev.ngb.backend.repository;

import dev.ngb.backend.model.PredictionRecord;
import dev.ngb.backend.model.PredictionStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what models were asked and what came back.
 *
 * <p>The idempotency lookup is the important one: the same canonical request returns the answer
 * already stored rather than a second, different one that the decision log might then name.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code prediction_records}.</p>
 */
public interface PredictionRecordRepository extends ListCrudRepository<PredictionRecord, UUID> {

    /**
     * Finds the stored answer to a canonical request, so a retry does not produce a second.
     *
     * @param consumer the service or use case that asked
     * @param targetName the outcome asked about
     * @param canonicalContextDigest digest of the canonical request context
     * @param requestKey the caller's idempotency key
     * @return the stored prediction or fallback, when the request has been made
     */
    Optional<PredictionRecord> findByConsumerAndTargetNameAndCanonicalContextDigestAndRequestKey(
            String consumer, String targetName, String canonicalContextDigest, String requestKey);

    /**
     * Lists what one version produced, newest first.
     *
     * @param modelVersionId the version
     * @return possibly empty list, most recently predicted first
     */
    List<PredictionRecord> findByModelVersionIdOrderByPredictedAtDesc(UUID modelVersionId);

    /**
     * Counts outcomes by status for one version over a window, which is how the fallback rate
     * becomes visible instead of being averaged away by the requests that succeeded.
     *
     * <pre>{@code
     * SELECT status, count(*) AS request_count
     * FROM prediction_records
     * WHERE model_version_id = :modelVersionId AND predicted_at >= :from AND predicted_at < :to
     * GROUP BY status
     * }</pre>
     *
     * @param modelVersionId the version
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return one row per observed status
     */
    @Query("""
            SELECT status, count(*) AS request_count
            FROM prediction_records
            WHERE model_version_id = :modelVersionId AND predicted_at >= :from AND predicted_at < :to
            GROUP BY status
            """)
    List<StatusCount> countByStatus(@Param("modelVersionId") UUID modelVersionId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Lists the predictions made under one experiment exposure, for the analysis that joins
     * treatment to outcome.
     *
     * <pre>{@code
     * SELECT * FROM prediction_records
     * WHERE experiment_exposure_id = :experimentExposureId
     * ORDER BY predicted_at
     * }</pre>
     *
     * @param experimentExposureId the exposure
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM prediction_records
            WHERE experiment_exposure_id = :experimentExposureId
            ORDER BY predicted_at
            """)
    List<PredictionRecord> findByExposure(
            @Param("experimentExposureId") UUID experimentExposureId);

    /**
     * Removes expired predictions, which no decision may cite any more.
     *
     * <pre>{@code
     * DELETE FROM prediction_records
     * WHERE expires_at <= :now AND retention_class <> 'LEGAL_HOLD'
     * }</pre>
     *
     * @param now the sweep instant, from the application clock
     * @return how many rows were removed
     */
    @Query("""
            DELETE FROM prediction_records
            WHERE expires_at <= :now AND retention_class <> 'LEGAL_HOLD'
            """)
    int deleteExpired(@Param("now") Instant now);

    /**
     * How many requests to one model version ended in a given status.
     *
     * @param status how the request ended
     * @param requestCount how many requests ended that way
     */
    record StatusCount(PredictionStatus status, long requestCount) {
    }
}
