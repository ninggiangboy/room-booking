package dev.ngb.backend.ml.internal.repository.model;

import dev.ngb.backend.ml.internal.model.model.ModelAction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads the commands issued against model versions and their routes.
 *
 * <p>Append-only apart from whether the command was applied. What it records is who asked, who
 * granted, and what authority was being moved -- which is what an incident review needs and what
 * a status field on the model would not have kept.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code model_actions}.</p>
 */
public interface ModelActionRepository extends ListCrudRepository<ModelAction, UUID> {

    /**
     * Lists the commands against one version, newest first.
     *
     * @param modelVersionId the version
     * @return possibly empty list, most recently requested first
     */
    List<ModelAction> findByModelVersionIdOrderByRequestedAtDesc(UUID modelVersionId);

    /**
     * Lists the commands against one route, newest first.
     *
     * @param modelReleaseRouteId the route
     * @return possibly empty list, most recently requested first
     */
    List<ModelAction> findByModelReleaseRouteIdOrderByRequestedAtDesc(UUID modelReleaseRouteId);

    /**
     * Lists commands that were issued and never took effect, which is the queue somebody has to
     * clear rather than a state the model silently stayed in.
     *
     * <pre>{@code
     * SELECT * FROM model_actions
     * WHERE NOT applied
     * ORDER BY requested_at
     * }</pre>
     *
     * @return possibly empty list, oldest request first
     */
    @Query("""
            SELECT * FROM model_actions
            WHERE NOT applied
            ORDER BY requested_at
            """)
    List<ModelAction> findUnapplied();

    /**
     * Lists what monitoring automation did on its own, which is always a pause or a rollback and
     * always names the evaluation that triggered it.
     *
     * <pre>{@code
     * SELECT * FROM model_actions
     * WHERE actor_kind = 'MONITORING_AUTOMATION'
     * ORDER BY requested_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recently requested first
     */
    @Query("""
            SELECT * FROM model_actions
            WHERE actor_kind = 'MONITORING_AUTOMATION'
            ORDER BY requested_at DESC
            """)
    List<ModelAction> findAutomated();
}
