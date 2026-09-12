package dev.ngb.backend.repository;

import dev.ngb.backend.model.PolicyOverrideDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads per-booking eligibility judgements against override programmes.
 *
 * <p>Rejections are read as often as approvals: the guest asks why, and a pattern of them is how a
 * badly scoped programme is found.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code policy_override_decisions}.</p>
 */
public interface PolicyOverrideDecisionRepository extends ListCrudRepository<PolicyOverrideDecision, UUID> {

    /**
     * Finds the judgement already made on a booking for a programme.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND program_id = ?}, matching
     * {@code uk_policy_override_decisions_booking}. This is the read that stops a guest re-applying until
     * somebody says yes.</p>
     *
     * @param bookingId booking judged
     * @param programId programme applied to
     * @return the decision, when one exists
     */
    Optional<PolicyOverrideDecision> findByBookingIdAndProgramId(UUID bookingId, UUID programId);

    /**
     * Returns every override judgement on a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY decided_at DESC}.</p>
     *
     * @param bookingId booking whose judgements are wanted
     * @return possibly empty list, newest first
     */
    List<PolicyOverrideDecision> findAllByBookingIdOrderByDecidedAtDesc(UUID bookingId);

    /**
     * Returns what a programme has granted so far, in one currency.
     *
     * <pre>{@code
     * SELECT COALESCE(sum(granted_amount_minor), 0)
     * FROM policy_override_decisions
     * WHERE program_id = :programId AND result = 'APPROVED' AND currency = :currency
     * }</pre>
     *
     * <p>The programme's budget cap is a number on its version, not a constraint the database can enforce
     * across rows, so this is the read that answers whether there is budget left. Scoped by currency
     * because a cap in one currency says nothing about spending in another.</p>
     *
     * @param programId programme to total
     * @param currency ISO 4217 code the cap is expressed in
     * @return granted minor units, zero when nothing has been approved
     */
    @Query("""
            SELECT COALESCE(sum(granted_amount_minor), 0)
            FROM policy_override_decisions
            WHERE program_id = :programId AND result = 'APPROVED' AND currency = :currency
            """)
    long sumGranted(@Param("programId") UUID programId, @Param("currency") String currency);
}
