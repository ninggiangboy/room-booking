package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentRoute;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the routing configuration for payment providers.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_routes}.</p>
 */
public interface PaymentRouteRepository extends ListCrudRepository<PaymentRoute, UUID> {

    /**
     * Returns routes able to take a given instrument in a given currency, best first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_routes
     * WHERE method_family = :methodFamily
     *   AND currency = :currency
     *   AND submissions_enabled
     *   AND circuit_state <> 'OPEN'
     *   AND effective_from <= :decisionInstant
     *   AND (effective_until IS NULL OR effective_until > :decisionInstant)
     * ORDER BY routing_priority, routing_weight DESC
     * }</pre>
     *
     * <p>The effective window is compared against a bound instant rather than {@code now()}, so a
     * routing decision is reproducible from the instant it was made.</p>
     *
     * @param methodFamily instrument family the guest chose
     * @param currency ISO 4217 code the obligation is denominated in
     * @param decisionInstant the caller's single decision instant
     * @return possibly empty list of eligible routes, most preferred first
     */
    @Query("""
            SELECT *
            FROM payment_routes
            WHERE method_family = :methodFamily
              AND currency = :currency
              AND submissions_enabled
              AND circuit_state <> 'OPEN'
              AND effective_from <= :decisionInstant
              AND (effective_until IS NULL OR effective_until > :decisionInstant)
            ORDER BY routing_priority, routing_weight DESC
            """)
    List<PaymentRoute> findEligible(
            @Param("methodFamily") String methodFamily,
            @Param("currency") String currency,
            @Param("decisionInstant") Instant decisionInstant);

    /**
     * Returns every route configured for one merchant account.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ?}.</p>
     *
     * @param providerAccountId merchant account whose routes are wanted
     * @return possibly empty list of routes
     */
    List<PaymentRoute> findAllByProviderAccountId(UUID providerAccountId);
}
