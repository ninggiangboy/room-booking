package dev.ngb.backend.analytics.internal.repository.arrival;

import dev.ngb.backend.analytics.internal.model.arrival.EventArrival;
import dev.ngb.backend.analytics.internal.model.arrival.ArrivalValidationState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the envelopes that reached the collector.
 *
 * <p>Append-only, and without the payload: this table answers whether something arrived, when, and
 * what was decided about it. What it said lives in restricted landing storage.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code event_arrivals}.</p>
 */
public interface EventArrivalRepository extends ListCrudRepository<EventArrival, UUID> {

    /**
     * Finds the arrival that claimed one envelope identity.
     *
     * @param eventId the envelope identity
     * @param validationState normally {@code ACCEPTED}
     * @return the arrival, when one was recorded
     */
    Optional<EventArrival> findByEventIdAndValidationState(UUID eventId,
            ArrivalValidationState validationState);

    /**
     * Lists the arrivals for one aggregate, in source order.
     *
     * @param aggregateType the kind of aggregate
     * @param aggregateId the aggregate
     * @return possibly empty list, oldest aggregate version first
     */
    List<EventArrival> findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
            String aggregateType, UUID aggregateId);

    /**
     * Lists arrivals that were not accepted, newest first, for the ingestion console.
     *
     * <pre>{@code
     * SELECT * FROM event_arrivals
     * WHERE validation_state <> 'ACCEPTED' AND received_at >= :since
     * ORDER BY received_at DESC
     * }</pre>
     *
     * @param since how far back to look
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM event_arrivals
            WHERE validation_state <> 'ACCEPTED' AND received_at >= :since
            ORDER BY received_at DESC
            """)
    List<EventArrival> findUnhealthy(@Param("since") Instant since);

    /**
     * Counts accepted arrivals per contract over a window, for volume monitoring.
     *
     * <pre>{@code
     * SELECT count(*) FROM event_arrivals
     * WHERE event_name = :eventName AND schema_version = :schemaVersion
     *   AND validation_state = 'ACCEPTED' AND received_at >= :since
     * }</pre>
     *
     * @param eventName the event name
     * @param schemaVersion the schema version
     * @param since start of the window
     * @return how many were accepted in the window
     */
    @Query("""
            SELECT count(*) FROM event_arrivals
            WHERE event_name = :eventName AND schema_version = :schemaVersion
              AND validation_state = 'ACCEPTED' AND received_at >= :since
            """)
    long countAccepted(@Param("eventName") String eventName,
            @Param("schemaVersion") short schemaVersion, @Param("since") Instant since);

    /**
     * Lists arrivals whose retention horizon has passed, for the retention worker.
     *
     * <pre>{@code
     * SELECT * FROM event_arrivals
     * WHERE expires_at <= :at AND retention_class <> 'LEGAL_HOLD'
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>A legal hold is excluded here rather than filtered by the caller, because a retention job that
     * forgets the exclusion deletes exactly the evidence somebody asked to be kept.</p>
     *
     * @param at instant to compare against
     * @param batchSize how many to claim at once
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM event_arrivals
            WHERE expires_at <= :at AND retention_class <> 'LEGAL_HOLD'
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<EventArrival> findExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Lists arrivals concerning one subject, for a deletion request.
     *
     * <pre>{@code
     * SELECT * FROM event_arrivals
     * WHERE subject_pseudonym = :subjectPseudonym AND suppressed_at IS NULL
     * ORDER BY received_at
     * }</pre>
     *
     * @param subjectPseudonym the pseudonymous subject
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM event_arrivals
            WHERE subject_pseudonym = :subjectPseudonym AND suppressed_at IS NULL
            ORDER BY received_at
            """)
    List<EventArrival> findForSubject(@Param("subjectPseudonym") String subjectPseudonym);
}
