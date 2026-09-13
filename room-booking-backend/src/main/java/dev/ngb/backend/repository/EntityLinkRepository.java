package dev.ngb.backend.repository;

import dev.ngb.backend.model.EntityLink;
import dev.ngb.backend.model.EntityLinkRelation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads relationships between subjects.
 *
 * <p>Links are access-restricted and are never exposed in a user-facing reason, because showing one
 * reveals somebody else. Reads here are bounded to one subject rather than free graph traversal.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code entity_links}.</p>
 */
public interface EntityLinkRepository extends ListCrudRepository<EntityLink, UUID> {

    /**
     * Reads the live links touching one subject.
     *
     * <pre>{@code
     * SELECT *
     * FROM entity_links
     * WHERE (endpoint_a_subject_id = :subjectId OR endpoint_b_subject_id = :subjectId)
     *   AND expires_at > :at
     * ORDER BY confidence DESC
     * }</pre>
     *
     * <p>Expired links are excluded rather than merely deprioritized: a relationship observed once years
     * ago is not evidence about today.</p>
     *
     * @param subjectId the subject
     * @param at instant to treat as now
     * @return possibly empty list, most confident first
     */
    @Query("""
            SELECT *
            FROM entity_links
            WHERE (endpoint_a_subject_id = :subjectId OR endpoint_b_subject_id = :subjectId)
              AND expires_at > :at
            ORDER BY confidence DESC
            """)
    List<EntityLink> findLiveForSubject(@Param("subjectId") UUID subjectId, @Param("at") Instant at);

    /**
     * Finds one relationship between two subjects.
     *
     * <pre>{@code
     * SELECT * FROM entity_links
     * WHERE endpoint_a_subject_id = :endpointASubjectId
     *   AND endpoint_b_subject_id = :endpointBSubjectId
     *   AND relation = :relation
     * }</pre>
     *
     * <p>Matches {@code uk_entity_links_pair}. Undirected relationships are stored with the lower
     * identifier first, so callers must order the endpoints the same way.</p>
     *
     * @param endpointASubjectId first endpoint
     * @param endpointBSubjectId second endpoint
     * @param relation what they have in common
     * @return the link, when it exists
     */
    Optional<EntityLink> findByEndpointASubjectIdAndEndpointBSubjectIdAndRelation(
            UUID endpointASubjectId, UUID endpointBSubjectId, EntityLinkRelation relation);

    /**
     * Reads the links that may contribute to an adverse decision about one subject.
     *
     * <pre>{@code
     * SELECT *
     * FROM entity_links
     * WHERE (endpoint_a_subject_id = :subjectId OR endpoint_b_subject_id = :subjectId)
     *   AND adverse_use_permitted = TRUE
     *   AND expires_at > :at
     * }</pre>
     *
     * <p>Every row returned already carries independent corroboration, because the check constraint
     * refuses to set the flag without it.</p>
     *
     * @param subjectId the subject
     * @param at instant to treat as now
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM entity_links
            WHERE (endpoint_a_subject_id = :subjectId OR endpoint_b_subject_id = :subjectId)
              AND adverse_use_permitted = TRUE
              AND expires_at > :at
            """)
    List<EntityLink> findAdverseUsableForSubject(@Param("subjectId") UUID subjectId,
                                                 @Param("at") Instant at);
}
