package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentReconciliationCase;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads reconciliation differences somebody has to resolve.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_reconciliation_cases}.</p>
 */
public interface PaymentReconciliationCaseRepository extends ListCrudRepository<PaymentReconciliationCase, UUID> {

    /**
     * Finds a case by the reference it is quoted by.
     *
     * <p>Spring derives {@code WHERE public_id = ?}.</p>
     *
     * @param publicId reference the case is quoted by
     * @return the case, when one bears that reference
     */
    Optional<PaymentReconciliationCase> findByPublicId(String publicId);

    /**
     * Returns live cases, worst and oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_reconciliation_cases
     * WHERE status IN ('OPEN', 'INVESTIGATING', 'BLOCKED')
     * ORDER BY severity DESC, opened_at
     * }</pre>
     *
     * @return possibly empty list of open cases
     */
    @Query("""
            SELECT *
            FROM payment_reconciliation_cases
            WHERE status IN ('OPEN', 'INVESTIGATING', 'BLOCKED')
            ORDER BY severity DESC, opened_at
            """)
    List<PaymentReconciliationCase> findOpen();

    /**
     * Returns the cases holding one booking's payout back.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_reconciliation_cases
     * WHERE booking_id = :bookingId
     *   AND blocks_payout
     *   AND status NOT IN ('RESOLVED', 'WRITTEN_OFF')
     * ORDER BY opened_at
     * }</pre>
     *
     * <p>This is the question finance asks of this table: money it cannot release, and why.</p>
     *
     * @param bookingId booking whose blocking cases are wanted
     * @return possibly empty list of cases, oldest first
     */
    @Query("""
            SELECT *
            FROM payment_reconciliation_cases
            WHERE booking_id = :bookingId
              AND blocks_payout
              AND status NOT IN ('RESOLVED', 'WRITTEN_OFF')
            ORDER BY opened_at
            """)
    List<PaymentReconciliationCase> findPayoutBlockers(@Param("bookingId") UUID bookingId);
}
