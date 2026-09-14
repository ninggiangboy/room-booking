package dev.ngb.backend.discovery.internal.repository.event;

import dev.ngb.backend.discovery.internal.model.event.DiscoveryEvent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.event.DiscoveryEvent;
import dev.ngb.backend.discovery.internal.repository.personalization.PersonalizationErasureDirectiveRepository;


/**
 * Ingests behavioural events and reads them back for derivation and retention.
 *
 * <p>Ingestion is idempotent on the client-issued key and tolerant of out-of-order delivery. Nothing
 * here redefines booking or review truth; reconciliation runs from those domains instead.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code discovery_events}.</p>
 */
public interface DiscoveryEventRepository extends ListCrudRepository<DiscoveryEvent, UUID> {

    /**
     * Finds an event by the key the client issued, to detect a retry.
     *
     * @param eventKey client-issued event identity
     * @return the event, when it has already been ingested
     */
    Optional<DiscoveryEvent> findByEventKey(String eventKey);

    /**
     * Lists accepted events for one listing within a window, for aggregate rebuilds.
     *
     * <pre>{@code
     * SELECT * FROM discovery_events
     * WHERE listing_id = :listingId
     *   AND ingest_state = 'ACCEPTED'
     *   AND occurred_at >= :from AND occurred_at < :to
     * ORDER BY occurred_at
     * }</pre>
     *
     * @param listingId the listing
     * @param from start of the window, inclusive
     * @param to end of the window, exclusive
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM discovery_events
            WHERE listing_id = :listingId
              AND ingest_state = 'ACCEPTED'
              AND occurred_at >= :from AND occurred_at < :to
            ORDER BY occurred_at
            """)
    List<DiscoveryEvent> findAcceptedForListing(@Param("listingId") UUID listingId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Lists accepted events for one guest from an instant, honouring an erasure cutoff.
     *
     * <pre>{@code
     * SELECT * FROM discovery_events
     * WHERE account_holder_id = :accountHolderId
     *   AND ingest_state = 'ACCEPTED'
     *   AND occurred_at >= :evidenceFrom
     * ORDER BY occurred_at
     * }</pre>
     *
     * <p>Feature jobs must pass the cutoff from
     * {@code PersonalizationErasureDirectiveRepository.findEvidenceCutoff}, not an arbitrary horizon.</p>
     *
     * @param accountHolderId the guest
     * @param evidenceFrom earliest behaviour the profile may be built from
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM discovery_events
            WHERE account_holder_id = :accountHolderId
              AND ingest_state = 'ACCEPTED'
              AND occurred_at >= :evidenceFrom
            ORDER BY occurred_at
            """)
    List<DiscoveryEvent> findAcceptedForGuest(@Param("accountHolderId") UUID accountHolderId,
            @Param("evidenceFrom") Instant evidenceFrom);

    /**
     * Deletes events past their retention deadline, leaving anything on legal hold.
     *
     * <pre>{@code
     * DELETE FROM discovery_events
     * WHERE expires_at <= :at AND retention_class <> 'LEGAL_HOLD'
     * }</pre>
     *
     * @param at instant to compare against
     * @return how many rows were removed
     */
    @Query("""
            DELETE FROM discovery_events
            WHERE expires_at <= :at AND retention_class <> 'LEGAL_HOLD'
            """)
    int deleteExpired(@Param("at") Instant at);
}
