package dev.ngb.backend.repository;

import dev.ngb.backend.model.ModerationDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads moderation outcomes.
 *
 * <p>The effective lookup is what an owning domain obeys; a partial unique index guarantees it cannot
 * return two live answers about the same text.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code moderation_decisions}.</p>
 */
public interface ModerationDecisionRepository extends ListCrudRepository<ModerationDecision, UUID> {

    /**
     * Finds the decision the owning domain should obey for one revision.
     *
     * <pre>{@code
     * SELECT * FROM moderation_decisions
     * WHERE content_revision_id = :contentRevisionId AND state = 'EFFECTIVE'
     * }</pre>
     *
     * <p>Matches {@code uk_moderation_decisions_effective}, so at most one row can come back.</p>
     *
     * @param contentRevisionId the revision
     * @return the effective decision, when one has been taken
     */
    @Query("""
            SELECT *
            FROM moderation_decisions
            WHERE content_revision_id = :contentRevisionId AND state = 'EFFECTIVE'
            """)
    Optional<ModerationDecision> findEffective(@Param("contentRevisionId") UUID contentRevisionId);

    /**
     * Reads the full decision history for one revision.
     *
     * <pre>{@code
     * SELECT * FROM moderation_decisions WHERE content_revision_id = :contentRevisionId
     * ORDER BY decided_at
     * }</pre>
     *
     * <p>Removing and restoring leaves a chain of superseding rows rather than one row whose current
     * value nobody can account for.</p>
     *
     * @param contentRevisionId the revision
     * @return possibly empty list, oldest first
     */
    List<ModerationDecision> findByContentRevisionIdOrderByDecidedAt(UUID contentRevisionId);

    /**
     * Claims quarantines whose review deadline has passed.
     *
     * <pre>{@code
     * SELECT *
     * FROM moderation_decisions
     * WHERE state = 'EFFECTIVE' AND outcome = 'QUARANTINE' AND review_deadline_at <= :at
     * ORDER BY review_deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Quarantine hides content pending a decision, so an unanswered
     * deadline is a removal nobody decided and has to surface.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum decisions to claim
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM moderation_decisions
            WHERE state = 'EFFECTIVE' AND outcome = 'QUARANTINE' AND review_deadline_at <= :at
            ORDER BY review_deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ModerationDecision> claimOverdueQuarantines(@Param("at") Instant at,
                                                     @Param("batchSize") int batchSize);
}
