package dev.ngb.backend.discovery.internal.repository.event;

import dev.ngb.backend.discovery.internal.model.event.DiscoverySearchRequest;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.event.DiscoverySearchRequest;


/**
 * Writes the server-issued record of one search and reads it back for diagnostics.
 *
 * <p>The row is append-only once written, apart from its retention deadline. Events and exposures
 * both reference it, which is what makes a claimed rank checkable.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code discovery_search_requests}.</p>
 */
public interface DiscoverySearchRequestRepository extends ListCrudRepository<DiscoverySearchRequest, UUID> {

    /**
     * Finds one search by the key clients echo back.
     *
     * @param searchRequestKey key issued to the client
     * @return the search, when it was issued here
     */
    Optional<DiscoverySearchRequest> findBySearchRequestKey(String searchRequestKey);

    /**
     * Lists recent searches by one guest, for the "clear recent activity" screen.
     *
     * <pre>{@code
     * SELECT * FROM discovery_search_requests
     * WHERE account_holder_id = :accountHolderId AND occurred_at >= :since
     * ORDER BY occurred_at DESC
     * LIMIT :limit
     * }</pre>
     *
     * @param accountHolderId the guest
     * @param since earliest instant to include
     * @param limit page size
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM discovery_search_requests
            WHERE account_holder_id = :accountHolderId AND occurred_at >= :since
            ORDER BY occurred_at DESC
            LIMIT :limit
            """)
    List<DiscoverySearchRequest> findRecentForGuest(@Param("accountHolderId") UUID accountHolderId,
            @Param("since") Instant since, @Param("limit") int limit);

    /**
     * Deletes search logs past their retention deadline.
     *
     * <pre>{@code
     * DELETE FROM discovery_search_requests WHERE expires_at <= :at
     * }</pre>
     *
     * @param at instant to compare against
     * @return how many rows were removed
     */
    @Query("DELETE FROM discovery_search_requests WHERE expires_at <= :at")
    int deleteExpired(@Param("at") Instant at);
}
