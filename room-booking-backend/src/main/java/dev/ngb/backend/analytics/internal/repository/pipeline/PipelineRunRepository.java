package dev.ngb.backend.analytics.internal.repository.pipeline;

import dev.ngb.backend.analytics.internal.model.pipeline.PipelineRun;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.pipeline.PipelineRun;


/**
 * Reads and claims transformation runs.
 *
 * <p>The lease methods are the worker contract: a run is claimed with {@code FOR UPDATE SKIP LOCKED}
 * inside a transaction, and no database lock is held across the warehouse or object-storage calls
 * the run then makes.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code pipeline_runs}.</p>
 */
public interface PipelineRunRepository extends ListCrudRepository<PipelineRun, UUID> {

    /**
     * Finds the run that already computed one specification over one input.
     *
     * @param dataProductId the dataset version
     * @param specificationDigest the product, code and configuration digest
     * @param inputWatermark the input watermark
     * @param partitionKey the partition, or null when unpartitioned
     * @return the run, when it has been attempted
     */
    Optional<PipelineRun> findByDataProductIdAndSpecificationDigestAndInputWatermarkAndPartitionKey(
            UUID dataProductId, String specificationDigest, Instant inputWatermark,
            @Nullable String partitionKey);

    /**
     * Finds the run whose output consumers currently read.
     *
     * <pre>{@code
     * SELECT * FROM pipeline_runs
     * WHERE data_product_id = :dataProductId AND published_as_current = true
     *   AND (partition_key = :partitionKey OR (partition_key IS NULL AND :partitionKey IS NULL))
     * }</pre>
     *
     * @param dataProductId the dataset version
     * @param partitionKey the partition, or null when unpartitioned
     * @return the current run, when one has been published
     */
    @Query("""
            SELECT * FROM pipeline_runs
            WHERE data_product_id = :dataProductId AND published_as_current = true
              AND (partition_key = :partitionKey OR (partition_key IS NULL AND :partitionKey IS NULL))
            """)
    Optional<PipelineRun> findCurrent(@Param("dataProductId") UUID dataProductId,
            @Param("partitionKey") @Nullable String partitionKey);

    /**
     * Claims a batch of runnable runs, skipping any another worker already holds.
     *
     * <pre>{@code
     * SELECT * FROM pipeline_runs
     * WHERE state = 'PENDING' OR (state = 'RUNNING' AND lease_expires_at <= :at)
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Must be called inside a transaction. The rows stay locked until it commits, and the fencing
     * token is what stops a worker whose lease expired mid-run from writing afterwards.</p>
     *
     * @param at instant expired leases are measured against
     * @param batchSize how many to claim
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM pipeline_runs
            WHERE state = 'PENDING' OR (state = 'RUNNING' AND lease_expires_at <= :at)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<PipelineRun> claimRunnable(@Param("at") Instant at,
            @Param("batchSize") int batchSize);

    /**
     * Lists the runs of one dataset that did not end clean, newest first.
     *
     * <pre>{@code
     * SELECT * FROM pipeline_runs
     * WHERE data_product_id = :dataProductId
     *   AND (state = 'FAILED' OR quality_state IN ('WARN', 'FAIL'))
     * ORDER BY created_at DESC
     * }</pre>
     *
     * @param dataProductId the dataset version
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM pipeline_runs
            WHERE data_product_id = :dataProductId
              AND (state = 'FAILED' OR quality_state IN ('WARN', 'FAIL'))
            ORDER BY created_at DESC
            """)
    List<PipelineRun> findUnhealthy(@Param("dataProductId") UUID dataProductId);
}
