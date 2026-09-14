package dev.ngb.backend.support.internal.repository.decision;

import dev.ngb.backend.support.internal.model.decision.CaseFinding;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.decision.CaseFinding;


/**
 * Reads the authorized interpretations a decision may rest on.
 *
 * <p>Replacing a finding moves the outgoing row out of the live state before the replacement is
 * written, because the live index is checked immediately even though the successor key is deferred.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_findings}.</p>
 */
public interface CaseFindingRepository extends ListCrudRepository<CaseFinding, UUID> {

    /**
     * Lists the live findings on a case.
     *
     * <pre>{@code
     * SELECT * FROM case_findings
     * WHERE support_case_id = :supportCaseId AND superseded_by_finding_id IS NULL
     * ORDER BY concluded_at DESC
     * }</pre>
     *
     * @param supportCaseId case
     * @return possibly empty list, most recent conclusion first
     */
    @Query("""
            SELECT * FROM case_findings
            WHERE support_case_id = :supportCaseId AND superseded_by_finding_id IS NULL
            ORDER BY concluded_at DESC
            """)
    List<CaseFinding> findLive(@Param("supportCaseId") UUID supportCaseId);

    /**
     * Finds the live answer to one question on a case.
     *
     * <pre>{@code
     * SELECT * FROM case_findings
     * WHERE support_case_id = :supportCaseId
     *   AND finding_question = :findingQuestion
     *   AND damage_claim_id IS NOT DISTINCT FROM :damageClaimId
     *   AND superseded_by_finding_id IS NULL
     * }</pre>
     *
     * <p>Matches {@code uk_case_findings_live}, so at most one row can come back: two live answers to
     * one question would leave a decision choosing between them arbitrarily.</p>
     *
     * @param supportCaseId case
     * @param findingQuestion question asked
     * @param damageClaimId claim the question is about, or null
     * @return the live finding, when the question has been answered
     */
    @Query("""
            SELECT * FROM case_findings
            WHERE support_case_id = :supportCaseId
              AND finding_question = :findingQuestion
              AND damage_claim_id IS NOT DISTINCT FROM :damageClaimId
              AND superseded_by_finding_id IS NULL
            """)
    Optional<CaseFinding> findLiveAnswer(@Param("supportCaseId") UUID supportCaseId,
            @Param("findingQuestion") String findingQuestion,
            @Param("damageClaimId") @Nullable UUID damageClaimId);
}
