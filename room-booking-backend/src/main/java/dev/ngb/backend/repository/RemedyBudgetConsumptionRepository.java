package dev.ngb.backend.repository;

import dev.ngb.backend.model.RemedyBudgetConsumption;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads what each remedy line took from a budget window.
 *
 * <p>Append-only in the database, and unique per line and entry kind, so a replayed message converges
 * on the same total rather than spending twice.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code remedy_budget_consumptions}.</p>
 */
public interface RemedyBudgetConsumptionRepository extends ListCrudRepository<RemedyBudgetConsumption, UUID> {

    /**
     * Lists the entries against one window, oldest first.
     *
     * @param remedyBudgetWindowId window
     * @return possibly empty list
     */
    List<RemedyBudgetConsumption> findByRemedyBudgetWindowIdOrderByRecordedAt(
            UUID remedyBudgetWindowId);

    /**
     * Lists what one remedy line took from every window it touched.
     *
     * @param caseRemedyLineId remedy line
     * @return possibly empty list
     */
    List<RemedyBudgetConsumption> findByCaseRemedyLineId(UUID caseRemedyLineId);
}
