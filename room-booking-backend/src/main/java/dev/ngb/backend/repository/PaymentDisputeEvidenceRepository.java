package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentDisputeEvidence;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the manifest of what was sent to defend a dispute.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_dispute_evidence}.</p>
 */
public interface PaymentDisputeEvidenceRepository extends ListCrudRepository<PaymentDisputeEvidence, UUID> {

    /**
     * Returns the manifest for one case.
     *
     * <p>Spring derives {@code WHERE dispute_id = ? ORDER BY created_at}.</p>
     *
     * @param disputeId case whose evidence is wanted
     * @return possibly empty list of evidence items
     */
    List<PaymentDisputeEvidence> findAllByDisputeIdOrderByCreatedAt(UUID disputeId);

    /**
     * Returns what was actually submitted for a case, in submission order.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_dispute_evidence
     * WHERE dispute_id = :disputeId
     *   AND submitted_at IS NOT NULL
     * ORDER BY submitted_at
     * }</pre>
     *
     * <p>These rows are frozen by trigger. Reproducing exactly what the provider received is the whole
     * point of keeping them.</p>
     *
     * @param disputeId case whose submitted evidence is wanted
     * @return possibly empty list, in submission order
     */
    @Query("""
            SELECT *
            FROM payment_dispute_evidence
            WHERE dispute_id = :disputeId
              AND submitted_at IS NOT NULL
            ORDER BY submitted_at
            """)
    List<PaymentDisputeEvidence> findSubmitted(@Param("disputeId") UUID disputeId);
}
