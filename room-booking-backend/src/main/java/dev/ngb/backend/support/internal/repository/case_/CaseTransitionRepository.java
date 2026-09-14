package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseTransition;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the state-change history of a case.
 *
 * <p>Append-only in the database. The command lookup is what makes a retried transition replay the
 * recorded result rather than fire a second time.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_transitions}.</p>
 */
public interface CaseTransitionRepository extends ListCrudRepository<CaseTransition, UUID> {

    /**
     * Finds the transition a command already performed.
     *
     * @param supportCaseId case
     * @param commandId command identity
     * @return the transition, when that command already ran
     */
    Optional<CaseTransition> findBySupportCaseIdAndCommandId(UUID supportCaseId, String commandId);

    /**
     * Lists the transitions of a case, newest first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseTransition> findBySupportCaseIdOrderBySequenceNumberDesc(UUID supportCaseId);
}
