package dev.ngb.backend.repository;

import dev.ngb.backend.model.TaxRuleVersion;
import dev.ngb.backend.model.TaxType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the tax rules the platform applies.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code tax_rule_versions}.</p>
 *
 * <p>Only published rules are ever returned for calculation. A draft rule exists so somebody can
 * prepare a rate change; applying one would charge a guest under terms nobody approved.</p>
 */
public interface TaxRuleVersionRepository extends ListCrudRepository<TaxRuleVersion, UUID> {

    /**
     * Returns the rules in force in a jurisdiction at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM tax_rule_versions
     * WHERE jurisdiction_code = :jurisdictionCode
     *   AND publication_state = 'PUBLISHED'
     *   AND effective_from <= :instant
     *   AND (effective_until IS NULL OR effective_until > :instant)
     * ORDER BY tax_type, authority_name
     * }</pre>
     *
     * <p>Several rows are expected and correct: a national tax and a municipal one are separate
     * obligations to separate authorities, and both apply to the same night. Within one authority and
     * tax type an exclusion constraint guarantees there is only one.</p>
     *
     * @param jurisdictionCode jurisdiction the stay falls in
     * @param instant instant the tax decision applies to
     * @return possibly empty list of rules in force
     */
    @Query("""
            SELECT *
            FROM tax_rule_versions
            WHERE jurisdiction_code = :jurisdictionCode
              AND publication_state = 'PUBLISHED'
              AND effective_from <= :instant
              AND (effective_until IS NULL OR effective_until > :instant)
            ORDER BY tax_type, authority_name
            """)
    List<TaxRuleVersion> findInForce(
            @Param("jurisdictionCode") String jurisdictionCode,
            @Param("instant") Instant instant);

    /**
     * Returns every version of one authority's tax, newest first.
     *
     * <p>Spring derives
     * {@code WHERE jurisdiction_code = ? AND authority_name = ? AND tax_type = ? ORDER BY version_number DESC}.
     * Used when preparing a rate change and when explaining which rate applied to a past stay.</p>
     *
     * @param jurisdictionCode jurisdiction of interest
     * @param authorityName authority levying the tax
     * @param taxType tax of interest
     * @return possibly empty list, highest version number first
     */
    List<TaxRuleVersion> findAllByJurisdictionCodeAndAuthorityNameAndTaxTypeOrderByVersionNumberDesc(
            String jurisdictionCode, String authorityName, TaxType taxType);
}
