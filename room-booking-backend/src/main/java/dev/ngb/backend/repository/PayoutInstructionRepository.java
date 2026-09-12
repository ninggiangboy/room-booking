package dev.ngb.backend.repository;

import dev.ngb.backend.model.PayoutInstruction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and claims the transfers that send host money out.
 *
 * <p>A submitted instruction is never resubmitted. The unresolved finder exists so that such an
 * instruction is chased by query, and the claim finder deliberately excludes it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payout_instructions}.</p>
 */
public interface PayoutInstructionRepository extends ListCrudRepository<PayoutInstruction, UUID> {

    /**
     * Finds the instruction a planning request already created.
     *
     * <p>Spring derives {@code WHERE idempotency_key = ?}, matching
     * {@code uk_payout_instructions_idempotency}. A repeated payout run returns the instruction that
     * exists rather than selecting the host's balance again.</p>
     *
     * @param idempotencyKey platform key for the planning request
     * @return the instruction, when one exists
     */
    Optional<PayoutInstruction> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds the instruction behind a provider request key.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_request_key = ?}, matching
     * {@code uk_payout_instructions_provider_key}. This is the read a webhook or a query result uses to
     * place evidence that arrives without the platform's own identifier.</p>
     *
     * @param providerAccountId merchant account the key belongs to
     * @param providerRequestKey key the provider deduplicates on
     * @return the instruction, when one exists
     */
    Optional<PayoutInstruction> findByProviderAccountIdAndProviderRequestKey(
            UUID providerAccountId, String providerRequestKey);

    /**
     * Finds an instruction by the identifier a host or an agent would quote.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching
     * {@code uk_payout_instructions_public_id}.</p>
     *
     * @param publicId short public identifier
     * @return the instruction, when one exists
     */
    Optional<PayoutInstruction> findByPublicId(String publicId);

    /**
     * Returns a host's payouts, most recent first.
     *
     * <p>Spring derives {@code WHERE host_account_holder_id = ? ORDER BY created_at DESC}, matching
     * {@code idx_payout_instructions_host}.</p>
     *
     * @param hostAccountHolderId host whose payouts are wanted
     * @return possibly empty list of instructions
     */
    List<PayoutInstruction> findAllByHostAccountHolderIdOrderByCreatedAtDesc(
            UUID hostAccountHolderId);

    /**
     * Locks one instruction for update.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_instructions
     * WHERE id = :id
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> The instruction is locked after its
     * payable allocations and before the journal, in the canonical finance lock order. No provider call
     * is ever made while this lock is held.</p>
     *
     * @param id instruction to lock
     * @return the locked instruction, when it exists
     */
    @Query("""
            SELECT *
            FROM payout_instructions
            WHERE id = :id
            FOR UPDATE
            """)
    Optional<PayoutInstruction> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims instructions that are ready to reach the provider.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_instructions
     * WHERE state = 'READY_TO_SUBMIT'
     *   AND (next_attempt_at IS NULL OR next_attempt_at <= :at)
     * ORDER BY next_attempt_at NULLS FIRST
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> {@code SUBMITTED} and {@code IN_TRANSIT}
     * are deliberately absent: an instruction whose outcome is unknown is resolved by querying the
     * provider, never by sending it again. The worker takes the lease and increments the fencing token
     * before the transaction commits and the network call begins.</p>
     *
     * @param at instant the worker is claiming at
     * @param batchSize most instructions to claim
     * @return the claimed instructions
     */
    @Query("""
            SELECT *
            FROM payout_instructions
            WHERE state = 'READY_TO_SUBMIT'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :at)
            ORDER BY next_attempt_at NULLS FIRST
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<PayoutInstruction> claimSubmittable(
            @Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Returns instructions that crossed to the provider without a proven outcome.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_instructions
     * WHERE state IN ('SUBMITTED', 'IN_TRANSIT')
     *   AND submitted_at <= :olderThan
     * ORDER BY submitted_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_payout_instructions_unresolved}. These are queried, never resubmitted: the
     * provider may have paid every one of them after the platform stopped waiting.</p>
     *
     * @param olderThan only instructions submitted at or before this instant
     * @param batchSize most rows to return
     * @return possibly empty list of unresolved instructions, oldest first
     */
    @Query("""
            SELECT *
            FROM payout_instructions
            WHERE state IN ('SUBMITTED', 'IN_TRANSIT')
              AND submitted_at <= :olderThan
            ORDER BY submitted_at
            LIMIT :batchSize
            """)
    List<PayoutInstruction> findUnresolved(
            @Param("olderThan") Instant olderThan, @Param("batchSize") int batchSize);

    /**
     * Returns instructions whose worker lease has expired.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_instructions
     * WHERE lease_owner IS NOT NULL
     *   AND lease_expires_at <= :at
     * ORDER BY lease_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_payout_instructions_stalled_lease}. Recovering a lease does not mean the work
     * never happened: the recovering worker takes a higher fencing token and establishes the outcome
     * before deciding anything.</p>
     *
     * @param at instant the sweep is running at
     * @param batchSize most rows to return
     * @return possibly empty list of stalled instructions
     */
    @Query("""
            SELECT *
            FROM payout_instructions
            WHERE lease_owner IS NOT NULL
              AND lease_expires_at <= :at
            ORDER BY lease_expires_at
            LIMIT :batchSize
            """)
    List<PayoutInstruction> findStalledLeases(
            @Param("at") Instant at, @Param("batchSize") int batchSize);
}
