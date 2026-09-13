package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostBulkEditRequest;
import dev.ngb.backend.model.BulkEditRequestState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the requests to change many nights, listings or rate plans at once.
 *
 * <p>The counts on a request are checked against its target rows at commit, so what is read here
 * is the summary the target rows add up to and not a hopeful one.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_bulk_edit_requests}.</p>
 */
public interface HostBulkEditRequestRepository extends ListCrudRepository<HostBulkEditRequest, UUID> {

    /**
     * Finds a bulk edit by the key its caller used, which is what makes a retry the same request.
     *
     * @param idempotencyKey the caller-supplied key
     * @return the request, when that key has been used
     */
    Optional<HostBulkEditRequest> findByIdempotencyKey(String idempotencyKey);

    /**
     * Lists one host's bulk edits, newest first.
     *
     * @param hostAccountHolderId the host
     * @return possibly empty list, most recent first
     */
    List<HostBulkEditRequest> findByHostAccountHolderIdOrderByRequestedAtDesc(
            UUID hostAccountHolderId);

    /**
     * Lists the bulk edits in one state.
     *
     * @param requestState where the edit stands
     * @return possibly empty list
     */
    List<HostBulkEditRequest> findByRequestState(BulkEditRequestState requestState);

    /**
     * Lists the edits that did not do everything they were asked to, which is what a host is owed
     * a notification about rather than a green tick.
     *
     * <pre>{@code
     * SELECT * FROM host_bulk_edit_requests
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND request_state IN ('PARTIALLY_APPLIED', 'FAILED')
     * ORDER BY requested_at DESC
     * }</pre>
     *
     * @param hostAccountHolderId the host
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM host_bulk_edit_requests
            WHERE host_account_holder_id = :hostAccountHolderId
              AND request_state IN ('PARTIALLY_APPLIED', 'FAILED')
            ORDER BY requested_at DESC
            """)
    List<HostBulkEditRequest> findIncomplete(
            @Param("hostAccountHolderId") UUID hostAccountHolderId);

    /**
     * Finds a bulk edit for update, so a run that is applying its targets holds the row it is
     * counting against.
     *
     * <pre>{@code
     * SELECT * FROM host_bulk_edit_requests WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Must be called inside a transaction: the row lock is held until it commits.</p>
     *
     * @param id the request
     * @return the locked request, when it exists
     */
    @Query("SELECT * FROM host_bulk_edit_requests WHERE id = :id FOR UPDATE")
    Optional<HostBulkEditRequest> findByIdForUpdate(@Param("id") UUID id);
}
