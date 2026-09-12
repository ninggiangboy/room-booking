package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentTimelineEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the human-readable history of what happened to a guest's money.
 *
 * <p>Append-only; the table rejects updates and deletes by trigger.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_timeline_entries}.</p>
 */
public interface PaymentTimelineEntryRepository extends ListCrudRepository<PaymentTimelineEntry, UUID> {

    /**
     * Returns an obligation's timeline, most recent first.
     *
     * <p>Spring derives {@code WHERE obligation_id = ? ORDER BY sequence_number DESC}. This includes
     * internal entries and is for support, not for a guest-facing screen.</p>
     *
     * @param obligationId obligation whose timeline is wanted
     * @return possibly empty list of entries
     */
    List<PaymentTimelineEntry> findAllByObligationIdOrderBySequenceNumberDesc(UUID obligationId);

    /**
     * Returns the entries one audience may see, most recent first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_timeline_entries
     * WHERE obligation_id = :obligationId
     *   AND visibility IN (:audience, 'BOTH')
     * ORDER BY sequence_number DESC
     * }</pre>
     *
     * <p>Filtering happens in SQL rather than after loading, so an internal entry is unreachable by
     * construction instead of by a caller remembering to drop it.</p>
     *
     * @param obligationId obligation whose timeline is wanted
     * @param audience {@code GUEST} or {@code HOST}; {@code INTERNAL} matches nothing but itself
     * @return possibly empty list of entries the audience may see
     */
    @Query("""
            SELECT *
            FROM payment_timeline_entries
            WHERE obligation_id = :obligationId
              AND visibility IN (:audience, 'BOTH')
            ORDER BY sequence_number DESC
            """)
    List<PaymentTimelineEntry> findVisibleTo(
            @Param("obligationId") UUID obligationId,
            @Param("audience") String audience);

    /**
     * Returns the highest sequence number recorded for an obligation.
     *
     * <pre>{@code
     * SELECT coalesce(max(sequence_number), 0)
     * FROM payment_timeline_entries
     * WHERE obligation_id = :obligationId
     * }</pre>
     *
     * @param obligationId obligation whose timeline is being appended to
     * @return the highest number used, or zero when there are none
     */
    @Query("""
            SELECT coalesce(max(sequence_number), 0)
            FROM payment_timeline_entries
            WHERE obligation_id = :obligationId
            """)
    int findHighestSequenceNumber(@Param("obligationId") UUID obligationId);
}
