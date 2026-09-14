package dev.ngb.backend.support.internal.repository.evidence;

import dev.ngb.backend.support.internal.model.evidence.EvidenceTransformation;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the lineage from an original artifact to its derivatives.
 *
 * <p>Append-only in the database.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code evidence_transformations}.</p>
 */
public interface EvidenceTransformationRepository extends ListCrudRepository<EvidenceTransformation, UUID> {

    /**
     * Lists what was derived from one artifact.
     *
     * @param sourceEvidenceId original artifact
     * @return possibly empty list
     */
    List<EvidenceTransformation> findBySourceEvidenceIdOrderByPerformedAtDesc(UUID sourceEvidenceId);

    /**
     * Finds the transformation that produced one derivative.
     *
     * @param derivedEvidenceId derivative
     * @return the transformation, when the row is a derivative
     */
    Optional<EvidenceTransformation> findByDerivedEvidenceId(UUID derivedEvidenceId);
}
