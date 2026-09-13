package dev.ngb.backend.repository;

import dev.ngb.backend.model.RiskSignalSubject;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads which observations concern which subjects.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_signal_subjects}.</p>
 */
public interface RiskSignalSubjectRepository extends ListCrudRepository<RiskSignalSubject, UUID> {

    /**
     * Reads the subjects one observation names.
     *
     * <pre>{@code
     * SELECT * FROM risk_signal_subjects WHERE risk_signal_id = :riskSignalId
     * }</pre>
     *
     * @param riskSignalId the observation
     * @return possibly empty list
     */
    List<RiskSignalSubject> findByRiskSignalId(UUID riskSignalId);

    /**
     * Reads the observations that name one subject.
     *
     * <pre>{@code
     * SELECT * FROM risk_signal_subjects WHERE risk_subject_id = :riskSubjectId ORDER BY created_at DESC
     * }</pre>
     *
     * @param riskSubjectId the subject
     * @return possibly empty list, most recent first
     */
    List<RiskSignalSubject> findByRiskSubjectIdOrderByCreatedAtDesc(UUID riskSubjectId);
}
