package dev.ngb.backend.repository;

import dev.ngb.backend.model.IcalConnection;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the calendar feeds exchanged with other platforms.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code ical_connections}. No method returns an export token; rows carry only a
 * digest, because the feed URL discloses a host's entire occupancy pattern.</p>
 */
public interface IcalConnectionRepository extends ListCrudRepository<IcalConnection, UUID> {

    /**
     * Claims import connections that are due to be polled.
     *
     * <pre>{@code
     * SELECT *
     * FROM ical_connections
     * WHERE direction = 'IMPORT'
     *   AND status = 'ACTIVE'
     *   AND (next_sync_at IS NULL OR next_sync_at <= :decisionInstant)
     * ORDER BY next_sync_at NULLS FIRST
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>A never-synced connection sorts first, because the window in which a newly connected
     * calendar has not been read yet is exactly when an overlapping booking is most likely to slip
     * through. {@code SKIP LOCKED} lets several sync workers run without contending.</p>
     *
     * @param decisionInstant the sync pass's single decision instant
     * @param batchSize maximum number of connections to claim
     * @return possibly empty list of due connections
     */
    @Query("""
            SELECT *
            FROM ical_connections
            WHERE direction = 'IMPORT'
              AND status = 'ACTIVE'
              AND (next_sync_at IS NULL OR next_sync_at <= :decisionInstant)
            ORDER BY next_sync_at NULLS FIRST
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<IcalConnection> claimDueImports(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns a resource's calendar connections.
     *
     * <p>Spring derives {@code WHERE inventory_resource_id = ?}, including paused and failing ones,
     * which is what a host needs to see when diagnosing why their calendars disagree.</p>
     *
     * @param inventoryResourceId resource whose connections are listed
     * @return possibly empty list of connections
     */
    List<IcalConnection> findAllByInventoryResourceId(UUID inventoryResourceId);

    /**
     * Finds the export connection a feed token belongs to.
     *
     * <p>Spring derives {@code WHERE export_token_digest = ?}. The caller hashes the token presented
     * in the request and looks up the digest, so the token itself is never stored or compared in the
     * clear.</p>
     *
     * @param exportTokenDigest SHA-256 digest of the presented token
     * @return the connection when the token is valid
     */
    Optional<IcalConnection> findByExportTokenDigest(String exportTokenDigest);
}
