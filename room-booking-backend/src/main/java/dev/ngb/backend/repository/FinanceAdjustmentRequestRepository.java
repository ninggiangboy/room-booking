package dev.ngb.backend.repository;

import dev.ngb.backend.model.FinanceAdjustmentRequest;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads proposed manual corrections.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code finance_adjustment_requests}.</p>
 */
public interface FinanceAdjustmentRequestRepository extends ListCrudRepository<FinanceAdjustmentRequest, UUID> {

    /**
     * Finds a request by the identifier finance quotes.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching
     * {@code uk_finance_adjustment_requests_public_id}.</p>
     *
     * @param publicId short public identifier
     * @return the request, when one exists
     */
    Optional<FinanceAdjustmentRequest> findByPublicId(String publicId);

    /**
     * Finds a request already proposed with identical content.
     *
     * <p>Spring derives {@code WHERE accounting_book_id = ? AND request_hash = ?}, matching
     * {@code uk_finance_adjustment_requests_hash}. A double-clicked approval screen resolves here rather
     * than posting the correction twice.</p>
     *
     * @param accountingBookId book the correction posts into
     * @param requestHash SHA-256 of the canonical request, lowercase hex
     * @return the request, when one exists
     */
    Optional<FinanceAdjustmentRequest> findByAccountingBookIdAndRequestHash(
            UUID accountingBookId, String requestHash);

    /**
     * Returns requests waiting on a person.
     *
     * <pre>{@code
     * SELECT *
     * FROM finance_adjustment_requests
     * WHERE status IN ('SUBMITTED', 'FAILED_REVIEW_REQUIRED')
     * ORDER BY submitted_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_finance_adjustment_requests_pending}. A request that failed to post after
     * approval appears here too, because an approved correction that silently never happened is worse
     * than one that was refused.</p>
     *
     * @param batchSize most rows to return
     * @return possibly empty list of pending requests, oldest first
     */
    @Query("""
            SELECT *
            FROM finance_adjustment_requests
            WHERE status IN ('SUBMITTED', 'FAILED_REVIEW_REQUIRED')
            ORDER BY submitted_at
            LIMIT :batchSize
            """)
    List<FinanceAdjustmentRequest> findPending(@Param("batchSize") int batchSize);

    /**
     * Returns the corrections affecting one host, most recent first.
     *
     * <p>Spring derives {@code WHERE host_account_holder_id = ? ORDER BY created_at DESC}, matching
     * {@code idx_finance_adjustment_requests_host}.</p>
     *
     * @param hostAccountHolderId host whose corrections are wanted
     * @return possibly empty list of requests
     */
    List<FinanceAdjustmentRequest> findAllByHostAccountHolderIdOrderByCreatedAtDesc(
            UUID hostAccountHolderId);
}
