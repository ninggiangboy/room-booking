package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingPolicyAcceptance;
import dev.ngb.backend.model.BookingPolicyType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the evidence that a guest accepted each policy.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code booking_policy_acceptances}. The inherited update and delete methods will fail at
 * runtime: the table is append-only and a database trigger refuses both, because acceptance evidence
 * the application can revise proves nothing.</p>
 */
public interface BookingPolicyAcceptanceRepository
        extends ListCrudRepository<BookingPolicyAcceptance, UUID> {

    /**
     * Returns every policy a booking's guest accepted.
     *
     * <p>Spring derives {@code WHERE booking_id = ?}.</p>
     *
     * @param bookingId booking whose acceptances are wanted
     * @return possibly empty list of acceptances
     */
    List<BookingPolicyAcceptance> findAllByBookingId(UUID bookingId);

    /**
     * Returns the acceptances of one policy kind on a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND policy_type = ? ORDER BY accepted_at}.</p>
     *
     * <p>A list rather than a single row, because {@code uk_booking_policy_acceptances_policy} is
     * keyed on the policy <em>version</em> as well as its kind: a guest who re-accepts revised terms
     * during a modification leaves both acceptances behind, and discarding the earlier one would
     * discard the evidence covering the original stay.</p>
     *
     * @param bookingId booking to read
     * @param policyType policy kind wanted
     * @return possibly empty list of acceptances, earliest first
     */
    List<BookingPolicyAcceptance> findAllByBookingIdAndPolicyTypeOrderByAcceptedAt(
            UUID bookingId, BookingPolicyType policyType);

    /**
     * Returns the version of a policy that governs a booking now.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_policy_acceptances
     * WHERE booking_id = :bookingId
     *   AND policy_type = :policyType
     * ORDER BY accepted_at DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>The read a refund dispute starts from: it answers which version of the cancellation terms
     * the guest last agreed to, rather than which version happens to be current now.</p>
     *
     * @param bookingId booking to read
     * @param policyType policy kind wanted
     * @return the most recent acceptance, when one was recorded
     */
    @Query("""
            SELECT *
            FROM booking_policy_acceptances
            WHERE booking_id = :bookingId
              AND policy_type = :policyType
            ORDER BY accepted_at DESC
            LIMIT 1
            """)
    Optional<BookingPolicyAcceptance> findGoverning(
            @Param("bookingId") UUID bookingId,
            @Param("policyType") BookingPolicyType policyType);
}
