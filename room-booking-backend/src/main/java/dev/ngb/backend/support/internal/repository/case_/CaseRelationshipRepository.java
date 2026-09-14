package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseRelationship;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.CaseRelationship;


/**
 * Reads the typed links between a case and other cases or domain objects.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_relationships}.</p>
 */
public interface CaseRelationshipRepository extends ListCrudRepository<CaseRelationship, UUID> {

    /**
     * Lists the live links out of a case.
     *
     * <pre>{@code
     * SELECT * FROM case_relationships
     * WHERE support_case_id = :supportCaseId AND detached_at IS NULL
     * ORDER BY established_at
     * }</pre>
     *
     * @param supportCaseId case
     * @return possibly empty list, oldest link first
     */
    @Query("""
            SELECT * FROM case_relationships
            WHERE support_case_id = :supportCaseId AND detached_at IS NULL
            ORDER BY established_at
            """)
    List<CaseRelationship> findLive(@Param("supportCaseId") UUID supportCaseId);

    /**
     * Lists the links pointing at one case, so an unmerge can find what to restore.
     *
     * @param relatedCaseId case pointed at
     * @return possibly empty list
     */
    List<CaseRelationship> findByRelatedCaseId(UUID relatedCaseId);
}
