package dev.ngb.backend.repository;

import dev.ngb.backend.model.IcalSyncRun;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the history of calendar synchronisation attempts.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code ical_sync_runs}. Runs are never revised.</p>
 */
public interface IcalSyncRunRepository extends ListCrudRepository<IcalSyncRun, UUID> {

    /**
     * Returns a connection's recent sync attempts, most recent first.
     *
     * <p>Spring derives {@code WHERE ical_connection_id = ? ORDER BY started_at DESC}. The history is
     * what distinguishes a feed with nothing to report from one that has quietly stopped updating —
     * they look identical in any single run, and only the second will eventually oversell the
     * host.</p>
     *
     * @param icalConnectionId connection whose history is wanted
     * @return possibly empty list of runs, most recent first
     */
    List<IcalSyncRun> findAllByIcalConnectionIdOrderByStartedAtDesc(UUID icalConnectionId);
}
