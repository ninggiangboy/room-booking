package dev.ngb.backend.repository;

import dev.ngb.backend.model.CaseEvidenceItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the evidence held on a case.
 *
 * <p>Reading a protected artifact is itself an event: callers write an {@code evidence_access_log}
 * row for the declared purpose, which this repository does not do for them.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_evidence_items}.</p>
 */
public interface CaseEvidenceItemRepository extends ListCrudRepository<CaseEvidenceItem, UUID> {

    /**
     * Lists the evidence on a case, most recently received first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseEvidenceItem> findBySupportCaseIdOrderByReceivedAtDesc(UUID supportCaseId);

    /**
     * Lists the evidence attached to one damage claim.
     *
     * @param damageClaimId claim
     * @return possibly empty list
     */
    List<CaseEvidenceItem> findByDamageClaimId(UUID damageClaimId);

    /**
     * Finds an item by the digest of its bytes.
     *
     * <pre>{@code
     * SELECT * FROM case_evidence_items
     * WHERE support_case_id = :supportCaseId
     *   AND content_hash = :contentHash
     *   AND state <> 'DELETED_OR_CRYPTO_ERASED'
     * }</pre>
     *
     * <p>Matches {@code uk_case_evidence_items_digest}: re-uploading the same file is not new evidence.</p>
     *
     * @param supportCaseId case
     * @param contentHash digest of the bytes
     * @return the item already holding those bytes, when there is one
     */
    @Query("""
            SELECT * FROM case_evidence_items
            WHERE support_case_id = :supportCaseId
              AND content_hash = :contentHash
              AND state <> 'DELETED_OR_CRYPTO_ERASED'
            """)
    Optional<CaseEvidenceItem> findByDigest(@Param("supportCaseId") UUID supportCaseId,
            @Param("contentHash") String contentHash);

    /**
     * Claims evidence whose ordinary retention has run out.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_evidence_items
     * WHERE retention_expires_at IS NOT NULL
     *   AND retention_expires_at <= :at
     *   AND NOT legal_hold
     *   AND state NOT IN ('DELETED_OR_CRYPTO_ERASED', 'DELETION_PENDING')
     * ORDER BY retention_expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A held item is excluded here and refused again by constraint,
     * because a retention sweep racing a legal hold must lose.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum items to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM case_evidence_items
            WHERE retention_expires_at IS NOT NULL
              AND retention_expires_at <= :at
              AND NOT legal_hold
              AND state NOT IN ('DELETED_OR_CRYPTO_ERASED', 'DELETION_PENDING')
            ORDER BY retention_expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseEvidenceItem> claimRetentionExpired(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
