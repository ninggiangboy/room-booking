package dev.ngb.backend.repository;

import dev.ngb.backend.model.VerificationAppeal;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads host challenges to eligibility decisions.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code verification_appeals}.</p>
 */
public interface VerificationAppealRepository extends ListCrudRepository<VerificationAppeal, UUID> {

    /**
     * Returns appeals awaiting review, oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM verification_appeals
     * WHERE status IN ('OPEN', 'IN_REVIEW')
     * ORDER BY submitted_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Oldest first because verification decides whether someone may earn a living on the platform,
     * and a queue that reorders leaves the earliest complaint waiting longest.</p>
     *
     * @param batchSize maximum number of appeals to return
     * @return possibly empty appeal queue
     */
    @Query("""
            SELECT *
            FROM verification_appeals
            WHERE status IN ('OPEN', 'IN_REVIEW')
            ORDER BY submitted_at
            LIMIT :batchSize
            """)
    List<VerificationAppeal> findQueue(@Param("batchSize") int batchSize);

    /**
     * Returns a host's appeals, newest first.
     *
     * <p>Spring derives {@code WHERE host_legal_profile_id = ? ORDER BY submitted_at DESC}.</p>
     *
     * @param hostLegalProfileId legal profile whose appeals are listed
     * @return possibly empty list of appeals, newest first
     */
    List<VerificationAppeal> findAllByHostLegalProfileIdOrderBySubmittedAtDesc(
            UUID hostLegalProfileId);
}
