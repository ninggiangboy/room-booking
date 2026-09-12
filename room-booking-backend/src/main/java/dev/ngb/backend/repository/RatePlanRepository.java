package dev.ngb.backend.repository;

import dev.ngb.backend.model.RatePlan;
import dev.ngb.backend.model.SupplyLifecycle;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the offers an accommodation type is sold under.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code rate_plans}.</p>
 */
public interface RatePlanRepository extends ListCrudRepository<RatePlan, UUID> {

    /**
     * Finds a rate plan by its operational reference.
     *
     * <p>Spring derives {@code WHERE reference_code = ?}, matching {@code uk_rate_plans_reference}.</p>
     *
     * @param referenceCode operational reference
     * @return the rate plan when the reference is known
     */
    Optional<RatePlan> findByReferenceCode(String referenceCode);

    /**
     * Finds the offer used when the guest expressed no preference.
     *
     * <pre>{@code
     * SELECT *
     * FROM rate_plans
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND is_default = true
     *   AND status <> 'ARCHIVED'
     * }</pre>
     *
     * <p>{@code uk_rate_plans_one_default} guarantees at most one row matches, so a quote always has
     * an unambiguous starting point. An empty result means the category cannot be quoted yet.</p>
     *
     * @param accommodationTypeId category being quoted
     * @return the default offer when one is configured
     */
    @Query("""
            SELECT *
            FROM rate_plans
            WHERE accommodation_type_id = :accommodationTypeId
              AND is_default = true
              AND status <> 'ARCHIVED'
            """)
    Optional<RatePlan> findDefaultFor(@Param("accommodationTypeId") UUID accommodationTypeId);

    /**
     * Returns a category's offers in one status.
     *
     * <p>Spring derives {@code WHERE accommodation_type_id = ? AND status = ?}.</p>
     *
     * @param accommodationTypeId category whose offers are listed
     * @param status status to filter by
     * @return possibly empty list of rate plans
     */
    List<RatePlan> findAllByAccommodationTypeIdAndStatus(
            UUID accommodationTypeId,
            SupplyLifecycle status);
}
