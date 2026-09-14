package dev.ngb.backend.stay.internal.repository.incident;

import dev.ngb.backend.stay.internal.model.incident.IncidentEvidenceLink;
import dev.ngb.backend.stay.internal.model.incident.EvidenceSourceDomain;
import dev.ngb.backend.stay.internal.model.incident.EvidenceSourceType;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the artifacts linked to incidents.
 *
 * <p>Links, never copies. The source lookup is how a transfer to claims or trust is reconciled back
 * to what operations actually held.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code incident_evidence_links}.</p>
 */
public interface IncidentEvidenceLinkRepository extends ListCrudRepository<IncidentEvidenceLink, UUID> {

    /**
     * Lists what is linked to one case, newest first.
     *
     * <p>Spring derives {@code WHERE incident_id = ? ORDER BY linked_at DESC}.</p>
     *
     * @param incidentId incident
     * @return possibly empty list
     */
    List<IncidentEvidenceLink> findByIncidentIdOrderByLinkedAtDesc(UUID incidentId);

    /**
     * Finds whether an artifact is already linked to a case.
     *
     * <p>Spring derives {@code WHERE incident_id = ? AND source_domain = ? AND source_type = ? AND
     * source_id = ?}, matching {@code uk_incident_evidence_links_source}. Linking the same artifact
     * twice would give two custody records for one thing.</p>
     *
     * @param incidentId incident
     * @param sourceDomain domain that owns the artifact
     * @param sourceType kind of artifact
     * @param sourceId identity of the artifact
     * @return the existing link, when there is one
     */
    Optional<IncidentEvidenceLink> findByIncidentIdAndSourceDomainAndSourceTypeAndSourceId(
            UUID incidentId, EvidenceSourceDomain sourceDomain, EvidenceSourceType sourceType,
            UUID sourceId);

    /**
     * Lists every case an artifact has been linked to.
     *
     * <p>Spring derives {@code WHERE source_domain = ? AND source_type = ? AND source_id = ?}, matching
     * {@code idx_incident_evidence_links_source}. Answers whether deleting something is safe.</p>
     *
     * @param sourceDomain domain that owns the artifact
     * @param sourceType kind of artifact
     * @param sourceId identity of the artifact
     * @return possibly empty list
     */
    List<IncidentEvidenceLink> findBySourceDomainAndSourceTypeAndSourceId(
            EvidenceSourceDomain sourceDomain, EvidenceSourceType sourceType, UUID sourceId);

    /**
     * Lists links a legal hold bars from deletion.
     *
     * <p>Spring derives {@code WHERE legal_hold = true}, matching
     * {@code idx_incident_evidence_links_hold}. A retention job reads this before it deletes
     * anything.</p>
     *
     * @param legalHold hold flag to match
     * @return possibly empty list
     */
    List<IncidentEvidenceLink> findByLegalHold(boolean legalHold);
}
