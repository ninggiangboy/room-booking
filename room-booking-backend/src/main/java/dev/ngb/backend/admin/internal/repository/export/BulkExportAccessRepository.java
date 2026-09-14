package dev.ngb.backend.admin.internal.repository.export;

import dev.ngb.backend.admin.internal.model.export.BulkExportAccess;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads every retrieval of an export artifact.
 *
 * <p>Append-only, and refused outside the approved window, so a retrieval after expiry is not
 * something the log has to be read carefully to notice.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code bulk_export_accesses}.</p>
 */
public interface BulkExportAccessRepository extends ListCrudRepository<BulkExportAccess, UUID> {

    /**
     * Lists the retrievals of one export, in order.
     *
     * @param bulkExportRequestId the export
     * @return possibly empty list, in sequence
     */
    List<BulkExportAccess> findByBulkExportRequestIdOrderBySequenceNumber(
            UUID bulkExportRequestId);

    /**
     * Lists what one person has retrieved, newest first.
     *
     * @param accessorId the person retrieving
     * @return possibly empty list, most recent first
     */
    List<BulkExportAccess> findByAccessorIdOrderByAccessedAtDesc(UUID accessorId);

    /**
     * Sums the bytes one person pulled down in a window, which is the figure a monitoring view
     * compares between operators rather than reading row by row.
     *
     * <pre>{@code
     * SELECT coalesce(sum(byte_count), 0) FROM bulk_export_accesses
     * WHERE accessor_id = :accessorId AND accessed_at >= :from AND accessed_at < :to
     * }</pre>
     *
     * @param accessorId the person retrieving
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return total bytes, zero when none
     */
    @Query("""
            SELECT coalesce(sum(byte_count), 0) FROM bulk_export_accesses
            WHERE accessor_id = :accessorId AND accessed_at >= :from AND accessed_at < :to
            """)
    long sumBytesRetrieved(@Param("accessorId") UUID accessorId, @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Lists the retrievals by somebody other than the person the export was approved for, which is
     * sharing that was never part of the approval.
     *
     * <pre>{@code
     * SELECT a.* FROM bulk_export_accesses a
     * JOIN bulk_export_requests r ON r.id = a.bulk_export_request_id
     * WHERE a.accessor_id <> r.requested_by
     * ORDER BY a.accessed_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT a.* FROM bulk_export_accesses a
            JOIN bulk_export_requests r ON r.id = a.bulk_export_request_id
            WHERE a.accessor_id <> r.requested_by
            ORDER BY a.accessed_at DESC
            """)
    List<BulkExportAccess> findByThirdParties();
}
