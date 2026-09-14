package dev.ngb.backend.review.internal.repository.aspect;

import dev.ngb.backend.review.internal.model.aspect.AspectProfileValue;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.aspect.AspectProfileValue;


/**
 * Reads the per-aspect values of a profile.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code aspect_profile_values}.</p>
 */
public interface AspectProfileValueRepository extends ListCrudRepository<AspectProfileValue, UUID> {

    /**
     * Lists every aspect value in one profile.
     *
     * <p>Spring derives {@code WHERE aspect_profile_version_id = ?}.</p>
     *
     * @param aspectProfileVersionId profile version
     * @return possibly empty list
     */
    List<AspectProfileValue> findByAspectProfileVersionId(UUID aspectProfileVersionId);

    /**
     * Lists the aspects a profile has enough evidence to speak about.
     *
     * <pre>{@code
     * SELECT *
     * FROM aspect_profile_values
     * WHERE aspect_profile_version_id = :aspectProfileVersionId
     *   AND evidence_class <> 'INSUFFICIENT'
     * ORDER BY mention_count DESC
     * }</pre>
     *
     * <p>Insufficient evidence is excluded deliberately: a caller wanting to show strengths and
     * weaknesses must not be handed rows that mean "we do not know".</p>
     *
     * @param aspectProfileVersionId profile version
     * @return possibly empty list, best-evidenced first
     */
    @Query("""
            SELECT *
            FROM aspect_profile_values
            WHERE aspect_profile_version_id = :aspectProfileVersionId
              AND evidence_class <> 'INSUFFICIENT'
            ORDER BY mention_count DESC
            """)
    List<AspectProfileValue> findEvidenced(
            @Param("aspectProfileVersionId") UUID aspectProfileVersionId);
}
