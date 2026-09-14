package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseTimelineEntry;
import dev.ngb.backend.support.internal.model.case_.TimelineSourceDomain;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the case timeline projection.
 *
 * <p>A projection, not a system of record: nothing read here authorizes a remedy, and the watermark
 * travels with every row so stale data can be labelled as stale.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_timeline_entries}.</p>
 */
public interface CaseTimelineEntryRepository extends ListCrudRepository<CaseTimelineEntry, UUID> {

    /**
     * Reads the timeline of a case in display order.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_timeline_entries
     * WHERE support_case_id = :supportCaseId
     * ORDER BY event_at DESC, tie_breaker DESC
     * LIMIT :limit
     * }</pre>
     *
     * <p>Event time orders the human story; the tie-breaker only makes equal timestamps deterministic
     * and claims no causal order between them.</p>
     *
     * @param supportCaseId case
     * @param limit maximum entries to return
     * @return possibly empty list, most recent event first
     */
    @Query("""
            SELECT *
            FROM case_timeline_entries
            WHERE support_case_id = :supportCaseId
            ORDER BY event_at DESC, tie_breaker DESC
            LIMIT :limit
            """)
    List<CaseTimelineEntry> findForDisplay(@Param("supportCaseId") UUID supportCaseId,
            @Param("limit") int limit);

    /**
     * Finds the entry a source event already produced, so a replayed consumer converges.
     *
     * @param supportCaseId case
     * @param sourceDomain domain the event came from
     * @param sourceEventId source event identity
     * @return the entry, when the event was already projected
     */
    Optional<CaseTimelineEntry> findBySupportCaseIdAndSourceDomainAndSourceEventId(
            UUID supportCaseId, TimelineSourceDomain sourceDomain, UUID sourceEventId);
}
