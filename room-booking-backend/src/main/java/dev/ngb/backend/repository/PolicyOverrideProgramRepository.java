package dev.ngb.backend.repository;

import dev.ngb.backend.model.PolicyOverrideProgram;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads approved reasons for ignoring the policy a guest agreed to.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code policy_override_programs}.</p>
 */
public interface PolicyOverrideProgramRepository extends ListCrudRepository<PolicyOverrideProgram, UUID> {

    /**
     * Finds a programme by its stable key.
     *
     * <p>Spring derives {@code WHERE program_key = ?}, matching {@code uk_policy_override_programs_key}.</p>
     *
     * @param programKey stable key
     * @return the programme, when one exists
     */
    Optional<PolicyOverrideProgram> findByProgramKey(String programKey);

    /**
     * Returns programmes still accepting applications at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM policy_override_programs
     * WHERE lifecycle = 'ACTIVE'
     *   AND (sunset_at IS NULL OR sunset_at > :at)
     * ORDER BY program_key
     * }</pre>
     *
     * <p>The instant is bound rather than read from the clock, so that judging whether a booking was
     * eligible last month resolves the programmes that were open last month.</p>
     *
     * @param at instant to judge against
     * @return possibly empty list, ordered by key
     */
    @Query("""
            SELECT *
            FROM policy_override_programs
            WHERE lifecycle = 'ACTIVE'
              AND (sunset_at IS NULL OR sunset_at > :at)
            ORDER BY program_key
            """)
    List<PolicyOverrideProgram> findOpenAt(@Param("at") Instant at);
}
