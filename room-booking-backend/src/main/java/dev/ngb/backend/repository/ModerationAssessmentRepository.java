package dev.ngb.backend.repository;

import dev.ngb.backend.model.ModerationAssessment;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what detectors said.
 *
 * <p>All of them, including the ones that disagreed: a decision citing only the detector that agreed
 * with it cannot be audited.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code moderation_assessments}.</p>
 */
public interface ModerationAssessmentRepository extends ListCrudRepository<ModerationAssessment, UUID> {

    /**
     * Reads every assessment of one revision.
     *
     * <pre>{@code
     * SELECT * FROM moderation_assessments WHERE content_revision_id = :contentRevisionId
     * ORDER BY assessed_at
     * }</pre>
     *
     * @param contentRevisionId the revision
     * @return possibly empty list, oldest first
     */
    List<ModerationAssessment> findByContentRevisionIdOrderByAssessedAt(UUID contentRevisionId);

    /**
     * Finds one detector version's assessment of a revision.
     *
     * <pre>{@code
     * SELECT * FROM moderation_assessments
     * WHERE content_revision_id = :contentRevisionId
     *   AND detector_key = :detectorKey
     *   AND detector_version = :detectorVersion
     * }</pre>
     *
     * @param contentRevisionId the revision
     * @param detectorKey detector
     * @param detectorVersion its version
     * @return the assessment, when that detector ran
     */
    Optional<ModerationAssessment> findByContentRevisionIdAndDetectorKeyAndDetectorVersion(
            UUID contentRevisionId, String detectorKey, String detectorVersion);
}
