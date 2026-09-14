package dev.ngb.backend.hostops.internal.repository.advice;

import dev.ngb.backend.hostops.internal.model.advice.HostAdviceDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a host did about advice they were shown.
 *
 * <p>One decision per disclosure, so reading this table beside its disclosures answers "what were
 * they told, and what did they do" without any reconstruction.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_advice_decisions}.</p>
 */
public interface HostAdviceDecisionRepository extends ListCrudRepository<HostAdviceDecision, UUID> {

    /**
     * Finds the decision against one disclosure.
     *
     * @param disclosureId the disclosure
     * @return the decision, when one was recorded
     */
    Optional<HostAdviceDecision> findByDisclosureId(UUID disclosureId);

    /**
     * Lists what one person decided, newest first.
     *
     * @param decidedBy the person
     * @return possibly empty list, most recent first
     */
    List<HostAdviceDecision> findByDecidedByOrderByDecidedAtDesc(UUID decidedBy);

    /**
     * Counts the reasons hosts gave for refusing advice of one kind, which is the only reading
     * that tells a product team whether the advice is wrong or merely unwelcome.
     *
     * <pre>{@code
     * SELECT x.reason_code AS reasonCode, count(*) AS refusalCount
     * FROM host_advice_decisions x
     * JOIN host_advice_disclosures a ON a.id = x.disclosure_id
     * WHERE x.outcome = 'REJECTED' AND a.advice_kind = :adviceKind AND x.decided_at >= :since
     * GROUP BY x.reason_code
     * ORDER BY count(*) DESC
     * }</pre>
     *
     * @param adviceKind the kind of advice
     * @param since earliest decision instant to include
     * @return one row per reason code, most frequent first
     */
    @Query("""
            SELECT x.reason_code AS reasonCode, count(*) AS refusalCount
            FROM host_advice_decisions x
            JOIN host_advice_disclosures a ON a.id = x.disclosure_id
            WHERE x.outcome = 'REJECTED' AND a.advice_kind = :adviceKind AND x.decided_at >= :since
            GROUP BY x.reason_code
            ORDER BY count(*) DESC
            """)
    List<RefusalReasonCount> countRefusalReasons(@Param("adviceKind") String adviceKind,
            @Param("since") Instant since);

    /**
     * How often one reason was given for refusing a kind of advice.
     *
     * @param reasonCode the approved reason code
     * @param refusalCount how many times it was given
     */
    record RefusalReasonCount(String reasonCode, long refusalCount) {}
}
