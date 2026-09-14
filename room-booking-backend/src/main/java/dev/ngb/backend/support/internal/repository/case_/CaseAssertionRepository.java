package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseAssertion;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads what parties said, attributed to them.
 *
 * <p>Never a finding. A caller that shows one of these rows must show it as an allegation, which is
 * why the table it comes from is the one that carries the attribution.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_assertions}.</p>
 */
public interface CaseAssertionRepository extends ListCrudRepository<CaseAssertion, UUID> {

    /**
     * Lists the assertions on a case, most recent first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseAssertion> findBySupportCaseIdOrderByAssertedAtDesc(UUID supportCaseId);

    /**
     * Lists the assertions made about one claim.
     *
     * @param damageClaimId claim
     * @return possibly empty list
     */
    List<CaseAssertion> findByDamageClaimId(UUID damageClaimId);
}
