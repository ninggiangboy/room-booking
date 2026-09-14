package dev.ngb.backend.support.internal.repository.evidence;

import dev.ngb.backend.support.internal.model.evidence.EvidenceRedaction;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.evidence.EvidenceRedaction;


/**
 * Reads the redactions applied to case evidence.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code evidence_redactions}.</p>
 */
public interface EvidenceRedactionRepository extends ListCrudRepository<EvidenceRedaction, UUID> {

    /**
     * Lists the redactions taken from one original.
     *
     * @param sourceEvidenceId original artifact
     * @return possibly empty list
     */
    List<EvidenceRedaction> findBySourceEvidenceId(UUID sourceEvidenceId);

    /**
     * Finds the redaction behind one derivative.
     *
     * @param derivedEvidenceId derivative
     * @return the redaction, when the row is a redacted derivative
     */
    Optional<EvidenceRedaction> findByDerivedEvidenceId(UUID derivedEvidenceId);
}
