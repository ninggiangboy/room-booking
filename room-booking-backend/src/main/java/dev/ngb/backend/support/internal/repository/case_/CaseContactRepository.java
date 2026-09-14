package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseContact;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.CaseContact;


/**
 * Reads the participant-facing interactions on a case.
 *
 * <p>Deliberately separate from {@code case_notes}: nothing here can be an internal observation and
 * nothing there can be delivered to a participant.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_contacts}.</p>
 */
public interface CaseContactRepository extends ListCrudRepository<CaseContact, UUID> {

    /**
     * Lists the contacts on a case, most recent first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseContact> findBySupportCaseIdOrderByOccurredAtDesc(UUID supportCaseId);
}
