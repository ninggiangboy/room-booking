package dev.ngb.backend.repository;

import dev.ngb.backend.model.CaseClassificationChange;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the classification trail of a case.
 *
 * <p>Append-only in the database, so this repository never updates: a correction is a new row.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_classification_history}.</p>
 */
public interface CaseClassificationChangeRepository extends ListCrudRepository<CaseClassificationChange, UUID> {

    /**
     * Lists the classification changes of a case, newest first.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<CaseClassificationChange> findBySupportCaseIdOrderByCommittedAtDesc(UUID supportCaseId);
}
