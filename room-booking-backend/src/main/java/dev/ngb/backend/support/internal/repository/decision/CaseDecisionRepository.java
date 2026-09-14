package dev.ngb.backend.support.internal.repository.decision;

import dev.ngb.backend.support.internal.model.decision.CaseDecision;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the decisions taken on a case.
 *
 * <p>An effective decision is immutable. Replacing one moves the outgoing row out of the effective
 * state before the replacement is written, because the effective index is checked immediately even
 * though the successor key is deferred.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_decisions}.</p>
 */
public interface CaseDecisionRepository extends ListCrudRepository<CaseDecision, UUID> {

    /**
     * Finds the effective decision of one kind on a case episode.
     *
     * <pre>{@code
     * SELECT * FROM case_decisions
     * WHERE support_case_id = :supportCaseId
     *   AND lifecycle_episode = :lifecycleEpisode
     *   AND decision_kind = :decisionKind
     *   AND damage_claim_id IS NOT DISTINCT FROM :damageClaimId
     *   AND state = 'EFFECTIVE'
     * }</pre>
     *
     * <p>Matches {@code uk_case_decisions_effective}, so at most one row can come back.</p>
     *
     * @param supportCaseId case
     * @param lifecycleEpisode episode
     * @param decisionKind kind of decision
     * @param damageClaimId claim the decision is about, or null
     * @return the effective decision, when one has been taken
     */
    @Query("""
            SELECT * FROM case_decisions
            WHERE support_case_id = :supportCaseId
              AND lifecycle_episode = :lifecycleEpisode
              AND decision_kind = :decisionKind
              AND damage_claim_id IS NOT DISTINCT FROM :damageClaimId
              AND state = 'EFFECTIVE'
            """)
    Optional<CaseDecision> findEffective(@Param("supportCaseId") UUID supportCaseId,
            @Param("lifecycleEpisode") short lifecycleEpisode,
            @Param("decisionKind") String decisionKind,
            @Param("damageClaimId") @Nullable UUID damageClaimId);

    /**
     * Finds the decision a command already took.
     *
     * @param supportCaseId case
     * @param commandId command identity
     * @return the decision, when that command already ran
     */
    Optional<CaseDecision> findBySupportCaseIdAndCommandId(UUID supportCaseId, String commandId);

    /**
     * Lists the decisions on a case, newest first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseDecision> findBySupportCaseIdOrderByCreatedAtDesc(UUID supportCaseId);

    /**
     * Locks one decision before it is superseded or communicated.
     *
     * <pre>{@code
     * SELECT * FROM case_decisions WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param id decision to lock
     * @return the locked decision, when it exists
     */
    @Query("SELECT * FROM case_decisions WHERE id = :id FOR UPDATE")
    Optional<CaseDecision> findByIdForUpdate(@Param("id") UUID id);
}
