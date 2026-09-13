package dev.ngb.backend.repository;

import dev.ngb.backend.model.BulkExportRequest;
import dev.ngb.backend.model.BulkExportState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads who took a copy of production data, why, and whether it is still retrievable.
 *
 * <p>The expiry sweep below is the control this table exists for. An export nobody expired is a
 * copy of the marketplace living somewhere nobody is monitoring.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code bulk_export_requests}.</p>
 */
public interface BulkExportRequestRepository extends ListCrudRepository<BulkExportRequest, UUID> {

    /**
     * Lists the exports in one state, oldest first.
     *
     * @param exportState where the export stands
     * @return possibly empty list, oldest first
     */
    List<BulkExportRequest> findByExportStateOrderByRequestedAt(BulkExportState exportState);

    /**
     * Lists what one person has asked for, newest first.
     *
     * @param requestedBy the requester
     * @return possibly empty list, most recent first
     */
    List<BulkExportRequest> findByRequestedByOrderByRequestedAtDesc(UUID requestedBy);

    /**
     * Lists the exports that are past their expiry but still marked retrievable, which is the sweep
     * that has to run for the expiry to mean anything.
     *
     * <pre>{@code
     * SELECT * FROM bulk_export_requests
     * WHERE export_state IN ('APPROVED', 'GENERATED') AND expires_at <= :asOf
     * ORDER BY expires_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM bulk_export_requests
            WHERE export_state IN ('APPROVED', 'GENERATED') AND expires_at <= :asOf
            ORDER BY expires_at
            """)
    List<BulkExportRequest> findExpirable(@Param("asOf") Instant asOf);

    /**
     * Lists the live exports carrying personal or restricted data, which is what a privacy review
     * of standing copies reads.
     *
     * <pre>{@code
     * SELECT * FROM bulk_export_requests
     * WHERE data_sensitivity IN ('PERSONAL', 'RESTRICTED') AND export_state = 'GENERATED'
     *   AND expires_at > :asOf
     * ORDER BY expires_at
     * }</pre>
     *
     * @param asOf the instant to measure against
     * @return possibly empty list, soonest to expire first
     */
    @Query("""
            SELECT * FROM bulk_export_requests
            WHERE data_sensitivity IN ('PERSONAL', 'RESTRICTED') AND export_state = 'GENERATED'
              AND expires_at > :asOf
            ORDER BY expires_at
            """)
    List<BulkExportRequest> findLiveSensitive(@Param("asOf") Instant asOf);

    /**
     * Lists the exports that turned out far larger than they were sized at, which is how a query
     * that was approved on one understanding and ran on another becomes visible.
     *
     * <pre>{@code
     * SELECT * FROM bulk_export_requests
     * WHERE actual_row_count IS NOT NULL AND estimated_row_count > 0
     *   AND actual_row_count > estimated_row_count * :toleranceFactor
     * ORDER BY actual_row_count DESC
     * }</pre>
     *
     * @param toleranceFactor how many times the estimate counts as far larger
     * @return possibly empty list, largest first
     */
    @Query("""
            SELECT * FROM bulk_export_requests
            WHERE actual_row_count IS NOT NULL AND estimated_row_count > 0
              AND actual_row_count > estimated_row_count * :toleranceFactor
            ORDER BY actual_row_count DESC
            """)
    List<BulkExportRequest> findOversized(@Param("toleranceFactor") int toleranceFactor);

    /**
     * Lists the exports that were generated and never retrieved, which is a copy of production data
     * somebody asked for and did not need.
     *
     * <pre>{@code
     * SELECT * FROM bulk_export_requests
     * WHERE export_state = 'GENERATED' AND access_count = 0
     * ORDER BY generated_at
     * }</pre>
     *
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM bulk_export_requests
            WHERE export_state = 'GENERATED' AND access_count = 0
            ORDER BY generated_at
            """)
    List<BulkExportRequest> findUnretrieved();
}
