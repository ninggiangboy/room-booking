package dev.ngb.backend.repository;

import dev.ngb.backend.model.ChangeRequestValidation;
import dev.ngb.backend.model.ChangeValidationKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the evidence a change was checked.
 *
 * <p>Append-only, and a retry is a new attempt rather than an edit, so a check that failed and was
 * made to pass shows both.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code change_request_validations}.</p>
 */
public interface ChangeRequestValidationRepository extends ListCrudRepository<ChangeRequestValidation, UUID> {

    /**
     * Lists every check run against one request, in the order they ran.
     *
     * @param changeRequestId the request
     * @return possibly empty list, oldest first
     */
    List<ChangeRequestValidation> findByChangeRequestIdOrderByPerformedAt(UUID changeRequestId);

    /**
     * Finds the latest attempt of one check, which is the row the progression guard reads.
     *
     * <pre>{@code
     * SELECT * FROM change_request_validations
     * WHERE change_request_id = :changeRequestId AND validation_kind = :validationKind
     * ORDER BY attempt_number DESC
     * LIMIT 1
     * }</pre>
     *
     * @param changeRequestId the request
     * @param validationKind which check
     * @return the latest attempt, when the check has run
     */
    @Query("""
            SELECT * FROM change_request_validations
            WHERE change_request_id = :changeRequestId AND validation_kind = :validationKind
            ORDER BY attempt_number DESC
            LIMIT 1
            """)
    Optional<ChangeRequestValidation> findLatest(@Param("changeRequestId") UUID changeRequestId,
            @Param("validationKind") ChangeValidationKind validationKind);

    /**
     * Lists the checks that did not pass, which is what somebody deciding whether to approve a
     * change reads before the approval rather than after the incident.
     *
     * <pre>{@code
     * SELECT * FROM change_request_validations
     * WHERE change_request_id = :changeRequestId AND outcome <> 'PASS'
     * ORDER BY performed_at
     * }</pre>
     *
     * @param changeRequestId the request
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM change_request_validations
            WHERE change_request_id = :changeRequestId AND outcome <> 'PASS'
            ORDER BY performed_at
            """)
    List<ChangeRequestValidation> findUnsatisfied(@Param("changeRequestId") UUID changeRequestId);

    /**
     * Counts how many times one check had to be repeated before it passed, which is a signal about
     * the change rather than about the checker.
     *
     * <pre>{@code
     * SELECT count(*) FROM change_request_validations
     * WHERE change_request_id = :changeRequestId AND validation_kind = :validationKind
     * }</pre>
     *
     * @param changeRequestId the request
     * @param validationKind which check
     * @return how many attempts there were
     */
    @Query("""
            SELECT count(*) FROM change_request_validations
            WHERE change_request_id = :changeRequestId AND validation_kind = :validationKind
            """)
    long countAttempts(@Param("changeRequestId") UUID changeRequestId,
            @Param("validationKind") ChangeValidationKind validationKind);
}
