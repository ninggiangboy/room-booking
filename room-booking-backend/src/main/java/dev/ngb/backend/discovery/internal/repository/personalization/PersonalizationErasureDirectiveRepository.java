package dev.ngb.backend.discovery.internal.repository.personalization;

import dev.ngb.backend.discovery.internal.model.personalization.PersonalizationErasureDirective;
import dev.ngb.backend.discovery.internal.model.personalization.ErasureDirectiveState;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.personalization.ErasureDirectiveState;
import dev.ngb.backend.discovery.internal.model.personalization.PersonalizationErasureDirective;


/**
 * Reads erasure directives and the cutoffs derived jobs must respect.
 *
 * <p>The cutoff query is the one that matters: a feature job that does not ask for it will happily
 * rebuild the profile the directive was meant to clear.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code personalization_erasure_directives}.</p>
 */
public interface PersonalizationErasureDirectiveRepository extends ListCrudRepository<PersonalizationErasureDirective, UUID> {

    /**
     * Finds the latest evidence cutoff in force for one guest.
     *
     * <pre>{@code
     * SELECT max(evidence_cutoff_at) FROM personalization_erasure_directives
     * WHERE account_holder_id = :accountHolderId
     *   AND state IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED')
     * }</pre>
     *
     * <p>The database enforces the same rule on insert, so a job that skips this call does not corrupt
     * anything; it simply fails loudly instead of quietly.</p>
     *
     * @param accountHolderId the guest
     * @return the cutoff, or null when no directive stands against this guest
     */
    @Query("""
            SELECT max(evidence_cutoff_at) FROM personalization_erasure_directives
            WHERE account_holder_id = :accountHolderId
              AND state IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED')
            """)
    @Nullable Instant findEvidenceCutoff(@Param("accountHolderId") UUID accountHolderId);

    /**
     * Lists directives still owed work, oldest deadline first, for the propagation worker.
     *
     * @param states normally pending and in progress
     * @return possibly empty list, most urgent first
     */
    List<PersonalizationErasureDirective> findByStateInOrderByDeadlineAt(
            List<ErasureDirectiveState> states);

    /**
     * Lists every directive against one guest, newest first, for the privacy console.
     *
     * @param accountHolderId the guest
     * @return possibly empty list, most recent request first
     */
    List<PersonalizationErasureDirective> findByAccountHolderIdOrderByRequestedAtDesc(
            UUID accountHolderId);
}
