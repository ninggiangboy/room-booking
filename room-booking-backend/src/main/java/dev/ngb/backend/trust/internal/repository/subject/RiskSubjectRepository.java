package dev.ngb.backend.trust.internal.repository.subject;

import dev.ngb.backend.trust.internal.model.subject.RiskSubject;
import dev.ngb.backend.trust.internal.model.subject.RiskSubjectType;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.subject.RiskSubject;
import dev.ngb.backend.trust.internal.model.subject.RiskSubjectType;


/**
 * Reads the typed handles everything else in this domain points at.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_subjects}.</p>
 */
public interface RiskSubjectRepository extends ListCrudRepository<RiskSubject, UUID> {

    /**
     * Finds the subject for one typed source identifier.
     *
     * <pre>{@code
     * SELECT * FROM risk_subjects WHERE subject_type = :subjectType AND source_id = :sourceId
     * }</pre>
     *
     * <p>Matches {@code uk_risk_subjects_identity}, so at most one row can come back.</p>
     *
     * @param subjectType kind of thing
     * @param sourceId owning domain's identifier
     * @return the subject, when one has been created
     */
    Optional<RiskSubject> findBySubjectTypeAndSourceId(RiskSubjectType subjectType, String sourceId);

    /**
     * Finds every subject that resolves to one account holder.
     *
     * <pre>{@code
     * SELECT * FROM risk_subjects WHERE account_holder_id = :accountHolderId
     * }</pre>
     *
     * @param accountHolderId account holder
     * @return possibly empty list
     */
    List<RiskSubject> findByAccountHolderId(UUID accountHolderId);
}
