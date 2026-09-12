package dev.ngb.backend.repository;

import dev.ngb.backend.model.PolicyDisclosureVersion;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the words a guest was shown.
 *
 * <p>The acceptance stored against a booking cites a semantic hash from one of these rows. When a guest
 * disputes what they agreed to, this is the table that answers.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code policy_disclosure_versions}.</p>
 */
public interface PolicyDisclosureVersionRepository extends ListCrudRepository<PolicyDisclosureVersion, UUID> {

    /**
     * Finds the disclosure for one locale of a policy version.
     *
     * <p>Spring derives {@code WHERE policy_version_id = ? AND locale = ?}, matching
     * {@code uk_policy_disclosure_versions_locale}.</p>
     *
     * @param policyVersionId terms being disclosed
     * @param locale BCP 47 locale
     * @return the disclosure, when one exists
     */
    Optional<PolicyDisclosureVersion> findByPolicyVersionIdAndLocale(UUID policyVersionId, String locale);

    /**
     * Returns every locale a policy version has been disclosed in.
     *
     * <p>Spring derives {@code WHERE policy_version_id = ? ORDER BY locale}.</p>
     *
     * @param policyVersionId terms being disclosed
     * @return possibly empty list, ordered by locale
     */
    List<PolicyDisclosureVersion> findAllByPolicyVersionIdOrderByLocale(UUID policyVersionId);
}
