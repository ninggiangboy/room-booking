package dev.ngb.backend.repository;

import dev.ngb.backend.model.SavedSearch;
import dev.ngb.backend.model.SavedSearchCadence;
import dev.ngb.backend.model.SavedSearchState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the searches guests kept and how often they agreed to hear about them.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code saved_searches}.</p>
 */
public interface SavedSearchRepository extends ListCrudRepository<SavedSearch, UUID> {

    /**
     * Lists one guest's saved searches in one state.
     *
     * @param accountHolderId the guest
     * @param state where the searches stand
     * @return possibly empty list
     */
    List<SavedSearch> findByAccountHolderIdAndState(UUID accountHolderId, SavedSearchState state);

    /**
     * Finds whether a guest has already saved this search.
     *
     * @param accountHolderId the guest
     * @param criteriaDigest digest of the criteria
     * @return the saved search, when they already have it
     */
    Optional<SavedSearch> findByAccountHolderIdAndCriteriaDigest(UUID accountHolderId,
            String criteriaDigest);

    /**
     * Lists the searches due to run again on one cadence, which is the set the match job takes.
     *
     * <pre>{@code
     * SELECT * FROM saved_searches
     * WHERE state = 'ACTIVE'
     *   AND notify_cadence = :notifyCadence
     *   AND expires_at > :at
     *   AND (last_evaluated_at IS NULL OR last_evaluated_at <= :due)
     * ORDER BY last_evaluated_at NULLS FIRST
     * LIMIT :limit
     * }</pre>
     *
     * @param notifyCadence the cadence being run
     * @param at instant the job is running at
     * @param due instant a search counts as due since
     * @param limit how many to take
     * @return possibly empty list, longest unevaluated first
     */
    @Query("""
            SELECT * FROM saved_searches
            WHERE state = 'ACTIVE'
              AND notify_cadence = :notifyCadence
              AND expires_at > :at
              AND (last_evaluated_at IS NULL OR last_evaluated_at <= :due)
            ORDER BY last_evaluated_at NULLS FIRST
            LIMIT :limit
            """)
    List<SavedSearch> findDue(@Param("notifyCadence") SavedSearchCadence notifyCadence,
            @Param("at") Instant at, @Param("due") Instant due, @Param("limit") int limit);

    /**
     * Lists the subscriptions that have run out, which is the set that stops asking.
     *
     * <pre>{@code
     * SELECT * FROM saved_searches
     * WHERE state = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM saved_searches
            WHERE state = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<SavedSearch> findExpirable(@Param("at") Instant at);
}
