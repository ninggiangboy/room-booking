package dev.ngb.backend.trust.internal.repository.intervention;

import dev.ngb.backend.trust.internal.model.intervention.RiskProtectedAction;
import dev.ngb.backend.trust.internal.model.intervention.RiskActionStatus;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.intervention.RiskActionStatus;
import dev.ngb.backend.trust.internal.model.intervention.RiskProtectedAction;


/**
 * Reads the registry of what may be evaluated at all.
 *
 * <p>A decision naming an action absent from here, or one that is not active, is refused by trigger,
 * so this is the list an orchestrator must resolve against before it evaluates anything.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_protected_actions}.</p>
 */
public interface RiskProtectedActionRepository extends ListCrudRepository<RiskProtectedAction, UUID> {

    /**
     * Finds one registered action.
     *
     * <pre>{@code
     * SELECT * FROM risk_protected_actions WHERE action_key = :actionKey
     * }</pre>
     *
     * @param actionKey stable action key
     * @return the registration, when the action exists
     */
    Optional<RiskProtectedAction> findByActionKey(String actionKey);

    /**
     * Lists the actions currently open for evaluation.
     *
     * <pre>{@code
     * SELECT * FROM risk_protected_actions WHERE status = :status
     * }</pre>
     *
     * @param status registration status
     * @return possibly empty list
     */
    List<RiskProtectedAction> findByStatus(RiskActionStatus status);
}
