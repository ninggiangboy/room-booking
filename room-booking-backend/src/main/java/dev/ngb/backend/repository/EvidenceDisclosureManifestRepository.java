package dev.ngb.backend.repository;

import dev.ngb.backend.model.EvidenceDisclosureManifest;
import dev.ngb.backend.model.DisclosureManifestState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the frozen evidence packages that leave the platform.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code evidence_disclosure_manifests}.</p>
 */
public interface EvidenceDisclosureManifestRepository extends ListCrudRepository<EvidenceDisclosureManifest, UUID> {

    /**
     * Lists the manifests raised on a case.
     *
     * @param supportCaseId case
     * @param state manifest state
     * @return possibly empty list
     */
    List<EvidenceDisclosureManifest> findBySupportCaseIdAndState(UUID supportCaseId,
            DisclosureManifestState state);

    /**
     * Finds the highest manifest version raised for one recipient on a case.
     *
     * <pre>{@code
     * SELECT * FROM evidence_disclosure_manifests
     * WHERE support_case_id = :supportCaseId
     *   AND manifest_kind = :manifestKind
     *   AND recipient_reference = :recipientReference
     * ORDER BY manifest_version DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>A provider asking for more produces the next version; the one already sent is never edited.</p>
     *
     * @param supportCaseId case
     * @param manifestKind what the package is for
     * @param recipientReference who receives it
     * @return the latest manifest, when one exists
     */
    @Query("""
            SELECT * FROM evidence_disclosure_manifests
            WHERE support_case_id = :supportCaseId
              AND manifest_kind = :manifestKind
              AND recipient_reference = :recipientReference
            ORDER BY manifest_version DESC
            LIMIT 1
            """)
    Optional<EvidenceDisclosureManifest> findLatest(@Param("supportCaseId") UUID supportCaseId,
            @Param("manifestKind") String manifestKind,
            @Param("recipientReference") String recipientReference);
}
