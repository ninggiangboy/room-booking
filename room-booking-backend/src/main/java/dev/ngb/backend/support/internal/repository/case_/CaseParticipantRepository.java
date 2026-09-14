package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseParticipant;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

/**
 * Reads who is on a case and what each of them may see.
 *
 * <p>Visibility is per participant, so the live lookup is what a read of case content is filtered
 * through rather than a property of the case as a whole.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_participants}.</p>
 */
public interface CaseParticipantRepository extends ListCrudRepository<CaseParticipant, UUID> {

    /**
     * Lists the live participants of a case.
     *
     * <pre>{@code
     * SELECT * FROM case_participants
     * WHERE support_case_id = :supportCaseId AND effective_until IS NULL
     * ORDER BY participant_role
     * }</pre>
     *
     * @param supportCaseId case
     * @return possibly empty list of live participations
     */
    @Query("""
            SELECT * FROM case_participants
            WHERE support_case_id = :supportCaseId AND effective_until IS NULL
            ORDER BY participant_role
            """)
    List<CaseParticipant> findLive(@Param("supportCaseId") UUID supportCaseId);

    /**
     * Lists every case one person is or was a participant of.
     *
     * @param accountHolderId person
     * @return possibly empty list
     */
    List<CaseParticipant> findByAccountHolderId(UUID accountHolderId);
}
