package dev.ngb.backend.review.internal.repository.aggregate;

import dev.ngb.backend.review.internal.model.aggregate.ReputationPolicyVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.aggregate.ReputationPolicyVersion;


/**
 * Reads the approved purposes reputation may be computed for.
 *
 * <p>No purpose, no computation. A consumer that cannot name an approved purpose has no business
 * reading a reputation view, and the allowlist on the version says which consumers those are.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code reputation_policy_versions}.</p>
 */
public interface ReputationPolicyVersionRepository extends ListCrudRepository<ReputationPolicyVersion, UUID> {

    /**
     * Finds the purpose version currently in force.
     *
     * <pre>{@code
     * SELECT *
     * FROM reputation_policy_versions
     * WHERE purpose_code = :purposeCode
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * }</pre>
     *
     * <p>Matches {@code uk_reputation_policy_versions_open} for the open-ended case, so at most one row
     * comes back.</p>
     *
     * @param purposeCode approved purpose
     * @param at instant to resolve at
     * @return the version in force, when there is one
     */
    @Query("""
            SELECT *
            FROM reputation_policy_versions
            WHERE purpose_code = :purposeCode
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            """)
    Optional<ReputationPolicyVersion> findInForce(@Param("purposeCode") String purposeCode,
            @Param("at") Instant at);

    /**
     * Finds one numbered version of a purpose.
     *
     * <p>Spring derives {@code WHERE purpose_code = ? AND policy_version = ?}, matching
     * {@code uk_reputation_policy_versions_identity}.</p>
     *
     * @param purposeCode purpose
     * @param policyVersion version within it
     * @return the version, when it exists
     */
    Optional<ReputationPolicyVersion> findByPurposeCodeAndPolicyVersion(String purposeCode,
            int policyVersion);
}
