package dev.ngb.backend.analytics.internal.repository.contract;

import dev.ngb.backend.analytics.internal.model.contract.DataCorrection;
import dev.ngb.backend.analytics.internal.model.contract.CorrectionApplicationState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.contract.CorrectionApplicationState;
import dev.ngb.backend.analytics.internal.model.contract.DataCorrection;


/**
 * Reads the additive record that something published was wrong.
 *
 * <p>Nothing here edits history. A reader reconstructing what a number meant at a point in time
 * reads the original alongside the corrections that were in force by then.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code data_corrections}.</p>
 */
public interface DataCorrectionRepository extends ListCrudRepository<DataCorrection, UUID> {

    /**
     * Lists the corrections against one event.
     *
     * @param correctsEventId the event
     * @return possibly empty list of corrections
     */
    List<DataCorrection> findByCorrectsEventId(UUID correctsEventId);

    /**
     * Lists the corrections in force against one dataset partition.
     *
     * <pre>{@code
     * SELECT * FROM data_corrections
     * WHERE target_data_product_id = :dataProductId AND target_partition_key = :partitionKey
     *   AND effective_at <= :at
     * ORDER BY effective_at
     * }</pre>
     *
     * @param dataProductId the dataset version
     * @param partitionKey the partition
     * @param at instant to resolve at
     * @return possibly empty list, oldest effective first
     */
    @Query("""
            SELECT * FROM data_corrections
            WHERE target_data_product_id = :dataProductId AND target_partition_key = :partitionKey
              AND effective_at <= :at
            ORDER BY effective_at
            """)
    List<DataCorrection> findInForce(@Param("dataProductId") UUID dataProductId,
            @Param("partitionKey") String partitionKey, @Param("at") Instant at);

    /**
     * Lists corrections still owed work, oldest first, for the application worker.
     *
     * @param applicationState normally {@code PENDING}
     * @return possibly empty list, oldest recorded first
     */
    List<DataCorrection> findByApplicationStateOrderByRecordedAtAsc(
            CorrectionApplicationState applicationState);
}
