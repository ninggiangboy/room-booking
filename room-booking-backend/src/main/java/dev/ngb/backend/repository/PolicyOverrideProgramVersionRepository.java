package dev.ngb.backend.repository;

import dev.ngb.backend.model.PolicyOverrideProgramVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads the frozen scope and funding of override programmes.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code policy_override_program_versions}.</p>
 */
public interface PolicyOverrideProgramVersionRepository extends ListCrudRepository<PolicyOverrideProgramVersion, UUID> {

    /**
     * Finds one version of a programme by its number.
     *
     * <p>Spring derives {@code WHERE program_id = ? AND version_number = ?}, matching
     * {@code uk_policy_override_program_versions_number}.</p>
     *
     * @param programId programme the version belongs to
     * @param versionNumber number within the programme
     * @return the version, when one exists
     */
    Optional<PolicyOverrideProgramVersion> findByProgramIdAndVersionNumber(
            UUID programId, int versionNumber);

    /**
     * Returns the newest published scope for a programme.
     *
     * <pre>{@code
     * SELECT *
     * FROM policy_override_program_versions
     * WHERE program_id = :programId AND published_at IS NOT NULL
     * ORDER BY version_number DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>Used when judging a new application. A decision already taken cites the version it was judged
     * under and must never be re-read through this method, because an event's footprint widens.</p>
     *
     * @param programId programme whose current scope is wanted
     * @return the newest published version, when one exists
     */
    @Query("""
            SELECT *
            FROM policy_override_program_versions
            WHERE program_id = :programId AND published_at IS NOT NULL
            ORDER BY version_number DESC
            LIMIT 1
            """)
    Optional<PolicyOverrideProgramVersion> findLatestPublished(@Param("programId") UUID programId);
}
