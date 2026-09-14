package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseNote;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.CaseNote;


/**
 * Reads the internal notes on a case.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_notes}.</p>
 */
public interface CaseNoteRepository extends ListCrudRepository<CaseNote, UUID> {

    /**
     * Lists the notes on a case, most recent first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseNote> findBySupportCaseIdOrderByWrittenAtDesc(UUID supportCaseId);
}
