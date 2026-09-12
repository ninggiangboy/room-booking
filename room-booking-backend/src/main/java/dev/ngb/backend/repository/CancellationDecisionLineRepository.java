package dev.ngb.backend.repository;

import dev.ngb.backend.model.CancellationDecisionLine;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the line-by-line arithmetic of a cancellation decision.
 *
 * <p>These are the rows a guest, a host, and finance all cite when they disagree about an amount.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code cancellation_decision_lines}.</p>
 */
public interface CancellationDecisionLineRepository extends ListCrudRepository<CancellationDecisionLine, UUID> {

    /**
     * Returns one decision's lines in order.
     *
     * <p>Spring derives {@code WHERE cancellation_decision_id = ? ORDER BY line_number}.</p>
     *
     * @param cancellationDecisionId decision whose lines are wanted
     * @return possibly empty list, in line order
     */
    List<CancellationDecisionLine> findAllByCancellationDecisionIdOrderByLineNumber(
            UUID cancellationDecisionId);

    /**
     * Returns what a decision's lines allocate, by funder.
     *
     * <pre>{@code
     * SELECT COALESCE(sum(host_funded_minor), 0)
     * FROM cancellation_decision_lines
     * WHERE cancellation_decision_id = :cancellationDecisionId
     * }</pre>
     *
     * <p>The host's share is the one finance has to act on: it becomes a recovery or a reduced payout.
     * The database already guarantees the lines sum to the decision's own totals, so this is a read for
     * reporting rather than a check.</p>
     *
     * @param cancellationDecisionId decision to total
     * @return host-funded minor units, zero when there are no lines
     */
    @Query("""
            SELECT COALESCE(sum(host_funded_minor), 0)
            FROM cancellation_decision_lines
            WHERE cancellation_decision_id = :cancellationDecisionId
            """)
    long sumHostFunded(@Param("cancellationDecisionId") UUID cancellationDecisionId);
}
