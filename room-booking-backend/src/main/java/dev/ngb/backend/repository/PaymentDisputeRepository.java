package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentDispute;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads external challenges to captured payments.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_disputes}.</p>
 */
public interface PaymentDisputeRepository extends ListCrudRepository<PaymentDispute, UUID> {

    /**
     * Finds a case already imported from a provider.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_dispute_id = ?}, matching
     * {@code uk_payment_disputes_provider}.</p>
     *
     * @param providerAccountId merchant account the case was opened against
     * @param providerDisputeId provider identifier for the case
     * @return the case, when one was already imported
     */
    Optional<PaymentDispute> findByProviderAccountIdAndProviderDisputeId(
            UUID providerAccountId, String providerDisputeId);

    /**
     * Returns the cases raised against one booking, most recent first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY opened_at DESC}.</p>
     *
     * @param bookingId booking whose cases are wanted
     * @return possibly empty list of cases
     */
    List<PaymentDispute> findAllByBookingIdOrderByOpenedAtDesc(UUID bookingId);

    /**
     * Returns live cases whose response deadline is approaching, soonest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_disputes
     * WHERE status IN ('INQUIRY_OR_RETRIEVAL', 'ACTION_REQUIRED', 'UNDER_REVIEW')
     *   AND respond_by IS NOT NULL
     *   AND respond_by <= :horizon
     * ORDER BY respond_by
     * }</pre>
     *
     * <p>Missing a deadline loses the case by default, so this read feeds a worker that pages owners
     * before expiry rather than a report somebody reads afterwards.</p>
     *
     * @param horizon how far ahead to look
     * @return possibly empty list, most urgent first
     */
    @Query("""
            SELECT *
            FROM payment_disputes
            WHERE status IN ('INQUIRY_OR_RETRIEVAL', 'ACTION_REQUIRED', 'UNDER_REVIEW')
              AND respond_by IS NOT NULL
              AND respond_by <= :horizon
            ORDER BY respond_by
            """)
    List<PaymentDispute> findDueForResponse(@Param("horizon") Instant horizon);

    /**
     * Returns every case contesting one capture.
     *
     * <p>Spring derives {@code WHERE capture_operation_id = ?}. A capture can be contested more than
     * once — an inquiry followed by a chargeback — so this returns a list rather than an optional.</p>
     *
     * @param captureOperationId capture whose cases are wanted
     * @return possibly empty list of cases
     */
    List<PaymentDispute> findAllByCaptureOperationId(UUID captureOperationId);
}
