package dev.ngb.backend.repository;

import dev.ngb.backend.model.AspectProfileVersion;
import dev.ngb.backend.model.AspectProfileSubjectType;
import dev.ngb.backend.model.DerivedProfileStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads aspect profiles.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code aspect_profile_versions}.</p>
 */
public interface AspectProfileVersionRepository extends ListCrudRepository<AspectProfileVersion, UUID> {

    /**
     * Finds the profile in force for a subject.
     *
     * <p>Spring derives {@code WHERE subject_type = ? AND subject_id = ? AND status = ?}, matching
     * {@code uk_aspect_profile_versions_current} when the status is {@code CURRENT}.</p>
     *
     * @param subjectType what the profile describes
     * @param subjectId which one
     * @param status status to match, normally {@code CURRENT}
     * @return the current profile, when one exists
     */
    Optional<AspectProfileVersion> findBySubjectTypeAndSubjectIdAndStatus(
            AspectProfileSubjectType subjectType, UUID subjectId, DerivedProfileStatus status);

    /**
     * Lists current profiles due for recomputation.
     *
     * <pre>{@code
     * SELECT *
     * FROM aspect_profile_versions
     * WHERE status = 'CURRENT'
     *   AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * @param at instant to treat as now
     * @param batchSize maximum profiles to return
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM aspect_profile_versions
            WHERE status = 'CURRENT'
              AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<AspectProfileVersion> findExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
