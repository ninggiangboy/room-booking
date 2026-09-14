package dev.ngb.backend.repository;

import dev.ngb.backend.model.LoyaltyTierDefinition;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the tiers of a published loyalty programme version.
 *
 * <p>These rows are sealed along with their version, so the tier a member qualified against a
 * year ago still says what it said.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code loyalty_tier_definitions}.</p>
 */
public interface LoyaltyTierDefinitionRepository extends ListCrudRepository<LoyaltyTierDefinition, UUID> {

    /**
     * Lists the tiers of one version, lowest first.
     *
     * @param growthProgramVersionId the loyalty terms
     * @return possibly empty list, by rank
     */
    List<LoyaltyTierDefinition> findByGrowthProgramVersionIdOrderByTierRank(
            UUID growthProgramVersionId);

    /**
     * Finds one named tier of one version.
     *
     * @param growthProgramVersionId the loyalty terms
     * @param tierKey the tier key
     * @return the tier, when the version defines it
     */
    Optional<LoyaltyTierDefinition> findByGrowthProgramVersionIdAndTierKey(
            UUID growthProgramVersionId, String tierKey);

    /**
     * Finds the highest tier a member's counted activity actually reaches, which is what a
     * qualification run compares the held tier against.
     *
     * <pre>{@code
     * SELECT * FROM loyalty_tier_definitions
     * WHERE growth_program_version_id = :growthProgramVersionId
     *   AND (qualifying_nights IS NULL OR qualifying_nights <= :nights)
     *   AND (qualifying_bookings IS NULL OR qualifying_bookings <= :bookings)
     *   AND (qualifying_spend_minor IS NULL OR qualifying_spend_minor <= :spendMinor)
     * ORDER BY tier_rank DESC
     * LIMIT 1
     * }</pre>
     *
     * @param growthProgramVersionId the loyalty terms
     * @param nights nights counted in the window
     * @param bookings bookings counted in the window
     * @param spendMinor spend counted in the window, in integer minor units
     * @return the highest tier reached, when any is
     */
    @Query("""
            SELECT * FROM loyalty_tier_definitions
            WHERE growth_program_version_id = :growthProgramVersionId
              AND (qualifying_nights IS NULL OR qualifying_nights <= :nights)
              AND (qualifying_bookings IS NULL OR qualifying_bookings <= :bookings)
              AND (qualifying_spend_minor IS NULL OR qualifying_spend_minor <= :spendMinor)
            ORDER BY tier_rank DESC
            LIMIT 1
            """)
    Optional<LoyaltyTierDefinition> findReachedTier(
            @Param("growthProgramVersionId") UUID growthProgramVersionId,
            @Param("nights") int nights, @Param("bookings") int bookings,
            @Param("spendMinor") long spendMinor);
}
