package dev.ngb.backend.repository;

import dev.ngb.backend.model.VerificationDocument;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads references to identity documents held in encrypted storage.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code verification_documents}. No method returns document bytes; rows carry a
 * storage location, a digest, and a key version.</p>
 */
public interface VerificationDocumentRepository
        extends ListCrudRepository<VerificationDocument, UUID> {

    /**
     * Returns the documents attached to one verification case.
     *
     * <p>Spring derives {@code WHERE verification_case_id = ?}. Includes documents that have not yet
     * been scanned, so a caller must check {@code isReadable} before opening one.</p>
     *
     * @param verificationCaseId case whose evidence is listed
     * @return possibly empty list of document records
     */
    List<VerificationDocument> findAllByVerificationCaseId(UUID verificationCaseId);

    /**
     * Returns documents whose retention deadline has passed and whose bytes still exist.
     *
     * <pre>{@code
     * SELECT *
     * FROM verification_documents
     * WHERE deleted_at IS NULL
     *   AND retain_until <= :decisionInstant
     * ORDER BY retain_until
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>This is the query behind actually erasing identity documents. A retention deadline that
     * nothing reads is a promise the platform quietly breaks, so the sweep is part of the design
     * rather than an operational afterthought.</p>
     *
     * @param decisionInstant the erasure run's single decision instant
     * @param batchSize maximum number of documents to return
     * @return possibly empty list of documents due for erasure, most overdue first
     */
    @Query("""
            SELECT *
            FROM verification_documents
            WHERE deleted_at IS NULL
              AND retain_until <= :decisionInstant
            ORDER BY retain_until
            LIMIT :batchSize
            """)
    List<VerificationDocument> findDueForErasure(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
