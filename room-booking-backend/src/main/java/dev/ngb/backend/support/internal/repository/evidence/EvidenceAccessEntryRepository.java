package dev.ngb.backend.support.internal.repository.evidence;

import dev.ngb.backend.support.internal.model.evidence.EvidenceAccessEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads the purpose-bound access record for case evidence.
 *
 * <p>Append-only in the database. This is the proof that access was evaluated at read time, so it is
 * written on every read of a protected artifact, including the ones that were refused.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code evidence_access_log}.</p>
 */
public interface EvidenceAccessEntryRepository extends ListCrudRepository<EvidenceAccessEntry, UUID> {

    /**
     * Lists who read one artifact, most recent first.
     *
     * @param caseEvidenceItemId artifact
     * @return possibly empty list
     */
    List<EvidenceAccessEntry> findByCaseEvidenceItemIdOrderByAccessedAtDesc(UUID caseEvidenceItemId);

    /**
     * Lists break-glass reads still owing a review.
     *
     * <pre>{@code
     * SELECT *
     * FROM evidence_access_log
     * WHERE break_glass AND post_use_review_required
     * ORDER BY accessed_at DESC
     * LIMIT :limit
     * }</pre>
     *
     * <p>An emergency read nobody looks at again is an unlogged privilege with extra steps, which is
     * why the review flag is a column rather than a convention.</p>
     *
     * @param limit maximum entries to return
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT *
            FROM evidence_access_log
            WHERE break_glass AND post_use_review_required
            ORDER BY accessed_at DESC
            LIMIT :limit
            """)
    List<EvidenceAccessEntry> findBreakGlassAwaitingReview(@Param("limit") int limit);
}
