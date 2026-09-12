package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExternalFinancialRecord;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the normalized rows of external evidence.
 *
 * <p>Only the match state of a record may be written after ingestion; everything else is frozen by the
 * database.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code external_financial_records}.</p>
 */
public interface ExternalFinancialRecordRepository extends ListCrudRepository<ExternalFinancialRecord, UUID> {

    /**
     * Returns an artifact's rows in source order.
     *
     * <p>Spring derives {@code WHERE artifact_id = ? ORDER BY native_row_number}, matching
     * {@code idx_external_financial_records_artifact}.</p>
     *
     * @param artifactId artifact whose rows are wanted
     * @return possibly empty list of records
     */
    List<ExternalFinancialRecord> findAllByArtifactIdOrderByNativeRowNumber(UUID artifactId);

    /**
     * Finds rows naming a provider object.
     *
     * <p>Spring derives {@code WHERE provider_object_ref = ?}, matching
     * {@code idx_external_financial_records_object}. This is the first rung of the matching ladder: an
     * exact provider object identity.</p>
     *
     * @param providerObjectRef provider object the row points at
     * @return possibly empty list of records
     */
    List<ExternalFinancialRecord> findAllByProviderObjectRef(String providerObjectRef);

    /**
     * Finds rows echoing back a platform key.
     *
     * <p>Spring derives {@code WHERE platform_reference = ?}, matching
     * {@code idx_external_financial_records_platform_ref}. This is the second rung: the platform's own
     * idempotency or request key, carried in the provider's metadata.</p>
     *
     * @param platformReference key the platform sent and the source echoed
     * @return possibly empty list of records
     */
    List<ExternalFinancialRecord> findAllByPlatformReference(String platformReference);

    /**
     * Returns rows nothing has accounted for in a coverage window.
     *
     * <pre>{@code
     * SELECT *
     * FROM external_financial_records
     * WHERE match_state = 'UNMATCHED'
     *   AND currency = :currency
     *   AND occurred_at >= :from
     *   AND occurred_at < :to
     * ORDER BY occurred_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_external_financial_records_unmatched}. Every one of these is either a
     * platform fact that was never recorded or a provider movement the platform did not cause, and both
     * are worth knowing about.</p>
     *
     * @param currency ISO 4217 code
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @param batchSize most rows to return
     * @return possibly empty list of unmatched records, oldest first
     */
    @Query("""
            SELECT *
            FROM external_financial_records
            WHERE match_state = 'UNMATCHED'
              AND currency = :currency
              AND occurred_at >= :from
              AND occurred_at < :to
            ORDER BY occurred_at
            LIMIT :batchSize
            """)
    List<ExternalFinancialRecord> findUnmatched(
            @Param("currency") String currency,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("batchSize") int batchSize);
}
