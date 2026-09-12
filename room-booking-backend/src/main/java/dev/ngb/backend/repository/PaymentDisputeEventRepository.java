package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentDisputeEvent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the append-only history of a dispute case.
 *
 * <p>The table rejects updates and deletes by trigger, because the history of a case is itself
 * evidence in the case.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_dispute_events}.</p>
 */
public interface PaymentDisputeEventRepository extends ListCrudRepository<PaymentDisputeEvent, UUID> {

    /**
     * Returns a case's history, most recent first.
     *
     * <p>Spring derives {@code WHERE dispute_id = ? ORDER BY sequence_number DESC}.</p>
     *
     * @param disputeId case whose history is wanted
     * @return possibly empty list of events
     */
    List<PaymentDisputeEvent> findAllByDisputeIdOrderBySequenceNumberDesc(UUID disputeId);

    /**
     * Returns the highest sequence number recorded for a case.
     *
     * <pre>{@code
     * SELECT coalesce(max(sequence_number), 0)
     * FROM payment_dispute_events
     * WHERE dispute_id = :disputeId
     * }</pre>
     *
     * <p>Resolved in the database, so two writers competing for the next number collide on
     * {@code uk_payment_dispute_events_sequence} instead of agreeing on the same one.</p>
     *
     * @param disputeId case whose history is being appended to
     * @return the highest number used, or zero when there are none
     */
    @Query("""
            SELECT coalesce(max(sequence_number), 0)
            FROM payment_dispute_events
            WHERE dispute_id = :disputeId
            """)
    int findHighestSequenceNumber(@Param("disputeId") UUID disputeId);
}
