package dev.ngb.backend.repository;

import dev.ngb.backend.model.ProtectionProgramVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the approved protection and insurance programmes.
 *
 * <p>Coverage is snapshotted against the version in force at the qualifying moment, so nothing here
 * is read to re-decide a historical booking.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code protection_program_versions}.</p>
 */
public interface ProtectionProgramVersionRepository extends ListCrudRepository<ProtectionProgramVersion, UUID> {

    /**
     * Finds the programme in force for a market at an instant.
     *
     * <pre>{@code
     * SELECT * FROM protection_program_versions
     * WHERE program_key = :programKey AND market_id = :marketId
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * LIMIT 1
     * }</pre>
     *
     * @param programKey programme family
     * @param marketId market
     * @param at instant to resolve at
     * @return the programme in force, when one is
     */
    @Query("""
            SELECT * FROM protection_program_versions
            WHERE program_key = :programKey AND market_id = :marketId
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            LIMIT 1
            """)
    Optional<ProtectionProgramVersion> findInForce(@Param("programKey") String programKey,
            @Param("marketId") UUID marketId, @Param("at") Instant at);
}
