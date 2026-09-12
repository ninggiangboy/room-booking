package dev.ngb.backend.repository;

import dev.ngb.backend.model.CancellationPolicyVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads immutable cancellation terms.
 *
 * <p>Settlement always reads the version a booking cited, never "the current one". The only query that
 * resolves a version by time is the one used at quote time, and it binds the instant rather than
 * calling the clock, so the same call replayed later returns the same answer.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code cancellation_policy_versions}.</p>
 */
public interface CancellationPolicyVersionRepository extends ListCrudRepository<CancellationPolicyVersion, UUID> {

    /**
     * Finds the published version applicable to a family at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM cancellation_policy_versions
     * WHERE policy_definition_id = :policyDefinitionId
     *   AND state = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_to IS NULL OR effective_to > :at)
     * ORDER BY effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>The window is half-open, so a version that ended and one that began at the same instant do not
     * both match. The instant is bound by the caller: a quote replayed for audit must resolve the version
     * that was in force then, not the one in force now.</p>
     *
     * @param policyDefinitionId family to resolve
     * @param at instant the terms are being quoted at
     * @return the applicable version, when one exists
     */
    @Query("""
            SELECT *
            FROM cancellation_policy_versions
            WHERE policy_definition_id = :policyDefinitionId
              AND state = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_to IS NULL OR effective_to > :at)
            ORDER BY effective_from DESC
            LIMIT 1
            """)
    Optional<CancellationPolicyVersion> findApplicable(
            @Param("policyDefinitionId") UUID policyDefinitionId, @Param("at") Instant at);

    /**
     * Finds one version of a family by its number.
     *
     * <p>Spring derives {@code WHERE policy_definition_id = ? AND version_number = ?}, matching
     * {@code uk_cancellation_policy_versions_number}.</p>
     *
     * @param policyDefinitionId family the version belongs to
     * @param versionNumber number within the family
     * @return the version, when one exists
     */
    Optional<CancellationPolicyVersion> findByPolicyDefinitionIdAndVersionNumber(
            UUID policyDefinitionId, int versionNumber);

    /**
     * Returns versions carrying a content hash.
     *
     * <p>Spring derives {@code WHERE content_hash = ?}. The hash is indexed but not unique -- the same
     * rules may legitimately be published under two families -- so this returns a list.</p>
     *
     * @param contentHash SHA-256 of the rule document, lowercase hex
     * @return possibly empty list
     */
    List<CancellationPolicyVersion> findAllByContentHash(String contentHash);
}
