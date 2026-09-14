package dev.ngb.backend.support.internal.repository.remedy;

import dev.ngb.backend.support.internal.model.remedy.CaseRemedyLine;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.remedy.CaseRemedyLine;


/**
 * Reads the funding split of a remedy.
 *
 * <p>Writable only while the remedy is still a proposal: once it has been approved, the lines are
 * what somebody approved.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_remedy_lines}.</p>
 */
public interface CaseRemedyLineRepository extends ListCrudRepository<CaseRemedyLine, UUID> {

    /**
     * Lists the lines of a remedy in line order.
     *
     * @param caseRemedyId remedy
     * @return possibly empty list, in line order
     */
    List<CaseRemedyLine> findByCaseRemedyIdOrderByLineNumber(UUID caseRemedyId);

    /**
     * Sums the lines of a remedy.
     *
     * <pre>{@code
     * SELECT coalesce(sum(amount_minor), 0)
     * FROM case_remedy_lines
     * WHERE case_remedy_id = :caseRemedyId
     * }</pre>
     *
     * <p>The same figure the deferred balance trigger computes at commit; reading it beforehand lets a
     * caller show the discrepancy rather than discover it in an exception.</p>
     *
     * @param caseRemedyId remedy
     * @return total in minor units, zero when there are no lines
     */
    @Query("""
            SELECT coalesce(sum(amount_minor), 0)
            FROM case_remedy_lines
            WHERE case_remedy_id = :caseRemedyId
            """)
    long sumLines(@Param("caseRemedyId") UUID caseRemedyId);
}
