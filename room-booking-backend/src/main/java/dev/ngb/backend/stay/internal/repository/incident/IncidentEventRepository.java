package dev.ngb.backend.stay.internal.repository.incident;

import dev.ngb.backend.stay.internal.model.incident.IncidentEvent;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads incident timelines.
 *
 * <p>Append-only on a sequence the incident issued, so reading in sequence order is reading in the
 * order things actually happened.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code incident_events}.</p>
 */
public interface IncidentEventRepository extends ListCrudRepository<IncidentEvent, UUID> {

    /**
     * Reads a timeline in order.
     *
     * <p>Spring derives {@code WHERE incident_id = ? ORDER BY sequence_number}, matching
     * {@code idx_incident_events_incident}.</p>
     *
     * @param incidentId incident
     * @return possibly empty list, oldest entry first
     */
    List<IncidentEvent> findByIncidentIdOrderBySequenceNumber(UUID incidentId);

    /**
     * Reads a timeline from a cursor.
     *
     * <p>Spring derives {@code WHERE incident_id = ? AND sequence_number > ? ORDER BY sequence_number}.
     * The sequence is the cursor precisely because it has no gaps the allocator did not intend.</p>
     *
     * @param incidentId incident
     * @param sequenceNumber last entry the caller has seen
     * @return possibly empty list, oldest entry first
     */
    List<IncidentEvent> findByIncidentIdAndSequenceNumberGreaterThanOrderBySequenceNumber(
            UUID incidentId, long sequenceNumber);
}
