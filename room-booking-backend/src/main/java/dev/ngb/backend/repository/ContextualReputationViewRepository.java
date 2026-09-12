package dev.ngb.backend.repository;

import dev.ngb.backend.model.ContextualReputationView;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads contextual reputation answers.
 *
 * <p>Every read is for a named purpose and a named context. There is no query here that returns "the
 * score for this host", because no such thing exists.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code contextual_reputation_views}.</p>
 */
public interface ContextualReputationViewRepository extends ListCrudRepository<ContextualReputationView, UUID> {

    /**
     * Finds the live answer to one question about one subject.
     *
     * <pre>{@code
     * SELECT *
     * FROM contextual_reputation_views
     * WHERE subject_type = :subjectType
     *   AND subject_id = :subjectId
     *   AND purpose_code = :purposeCode
     *   AND context_key = :contextKey
     *   AND superseded_at IS NULL
     * }</pre>
     *
     * <p>Matches {@code uk_contextual_reputation_views_live}, so at most one row comes back. Whether the
     * answer may still be acted on also depends on its expiry, which the caller checks.</p>
     *
     * @param subjectType who the answer is about
     * @param subjectId which one
     * @param purposeCode approved purpose
     * @param contextKey context it was computed for
     * @return the live answer, when there is one
     */
    @Query("""
            SELECT *
            FROM contextual_reputation_views
            WHERE subject_type = :subjectType
              AND subject_id = :subjectId
              AND purpose_code = :purposeCode
              AND context_key = :contextKey
              AND superseded_at IS NULL
            """)
    Optional<ContextualReputationView> findLive(@Param("subjectType") String subjectType,
            @Param("subjectId") UUID subjectId, @Param("purposeCode") String purposeCode,
            @Param("contextKey") String contextKey);

    /**
     * Lists live answers that have expired.
     *
     * <pre>{@code
     * SELECT *
     * FROM contextual_reputation_views
     * WHERE superseded_at IS NULL
     *   AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_contextual_reputation_views_expiry}. An expired answer is not a low answer;
     * it is no answer, and consumers must be given nothing rather than something stale.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum answers to return
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM contextual_reputation_views
            WHERE superseded_at IS NULL
              AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<ContextualReputationView> findExpired(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
