package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewExtractionRun;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads attempts to read aspects out of reviews.
 *
 * <p>The success lookup is what makes extraction idempotent: the same revision through the same
 * versions may succeed only once, or every mention it produced would be counted twice.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_extraction_runs}.</p>
 */
public interface ReviewExtractionRunRepository extends ListCrudRepository<ReviewExtractionRun, UUID> {

    /**
     * Finds the successful run for a canonical extraction identity.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_extraction_runs
     * WHERE review_revision_id = :reviewRevisionId
     *   AND aspect_taxonomy_version_id = :aspectTaxonomyVersionId
     *   AND extractor_version = :extractorVersion
     *   AND state = 'SUCCEEDED'
     * }</pre>
     *
     * <p>Matches {@code uk_review_extraction_runs_success}, so at most one row can come back.</p>
     *
     * @param reviewRevisionId revision
     * @param aspectTaxonomyVersionId vocabulary version
     * @param extractorVersion extractor version
     * @return the successful run, when there has been one
     */
    @Query("""
            SELECT *
            FROM review_extraction_runs
            WHERE review_revision_id = :reviewRevisionId
              AND aspect_taxonomy_version_id = :aspectTaxonomyVersionId
              AND extractor_version = :extractorVersion
              AND state = 'SUCCEEDED'
            """)
    Optional<ReviewExtractionRun> findSuccessful(@Param("reviewRevisionId") UUID reviewRevisionId,
            @Param("aspectTaxonomyVersionId") UUID aspectTaxonomyVersionId,
            @Param("extractorVersion") String extractorVersion);

    /**
     * Claims runs that are due to start or be retried.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_extraction_runs
     * WHERE state IN ('PLANNED', 'FAILED')
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The claiming worker raises the fencing token, so a stalled
     * predecessor cannot write an output after its lease lapsed.</p>
     *
     * @param batchSize maximum runs to claim
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM review_extraction_runs
            WHERE state IN ('PLANNED', 'FAILED')
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ReviewExtractionRun> claimDue(@Param("batchSize") int batchSize);

    /**
     * Finds runs whose worker lease has lapsed.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_extraction_runs
     * WHERE state = 'RUNNING'
     *   AND lease_expires_at <= :at
     * ORDER BY lease_expires_at
     * }</pre>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest lapsed first
     */
    @Query("""
            SELECT *
            FROM review_extraction_runs
            WHERE state = 'RUNNING'
              AND lease_expires_at <= :at
            ORDER BY lease_expires_at
            """)
    List<ReviewExtractionRun> findExpiredLeases(@Param("at") Instant at);
}
