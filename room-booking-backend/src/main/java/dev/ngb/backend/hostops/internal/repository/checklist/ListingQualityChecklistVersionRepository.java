package dev.ngb.backend.hostops.internal.repository.checklist;

import dev.ngb.backend.hostops.internal.model.checklist.ListingQualityChecklistVersion;
import dev.ngb.backend.platform.GovernedRegistryStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the published sets of things a listing needs before it competes on equal terms.
 *
 * <p>Membership is frozen with the version, so the set of items read here is the set the listings
 * measured against it were actually measured against.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code listing_quality_checklist_versions}.</p>
 */
public interface ListingQualityChecklistVersionRepository extends ListCrudRepository<ListingQualityChecklistVersion, UUID> {

    /**
     * Finds one exact version of a checklist.
     *
     * @param checklistKey the checklist
     * @param checklistVersion which version of it
     * @return the version, when it is registered
     */
    Optional<ListingQualityChecklistVersion> findByChecklistKeyAndChecklistVersion(
            String checklistKey, int checklistVersion);

    /**
     * Lists the checklists of one market in one lifecycle state.
     *
     * @param marketCode the market
     * @param status the lifecycle state
     * @return possibly empty list
     */
    List<ListingQualityChecklistVersion> findByMarketCodeAndStatus(String marketCode,
            GovernedRegistryStatus status);

    /**
     * Finds the checklist a listing should currently be measured against: the active version for
     * its market, falling back to the one that applies everywhere.
     *
     * <pre>{@code
     * SELECT * FROM listing_quality_checklist_versions
     * WHERE status = 'ACTIVE' AND (market_code = :marketCode OR market_code IS NULL)
     * ORDER BY market_code NULLS LAST, checklist_version DESC
     * LIMIT 1
     * }</pre>
     *
     * @param marketCode the market
     * @return the applicable checklist, when one is active
     */
    @Query("""
            SELECT * FROM listing_quality_checklist_versions
            WHERE status = 'ACTIVE' AND (market_code = :marketCode OR market_code IS NULL)
            ORDER BY market_code NULLS LAST, checklist_version DESC
            LIMIT 1
            """)
    Optional<ListingQualityChecklistVersion> findApplicable(
            @Param("marketCode") String marketCode);
}
