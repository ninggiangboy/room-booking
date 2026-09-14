package dev.ngb.backend.analytics.internal.repository.contract;

import dev.ngb.backend.analytics.internal.model.contract.PrivacySubjectLink;
import dev.ngb.backend.analytics.internal.model.contract.SubjectLinkState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.analytics.internal.model.contract.PrivacySubjectLink;
import dev.ngb.backend.analytics.internal.model.contract.SubjectLinkState;


/**
 * Reads the declared links between pseudonymous subjects.
 *
 * <p>Access to this table is the re-identification capability, so it is deliberately separate from
 * the analytical tables that use pseudonyms. The suppression query is the one that matters: an
 * assignment written for a suppressed subject is refused by the database.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code privacy_subject_links}.</p>
 */
public interface PrivacySubjectLinkRepository extends ListCrudRepository<PrivacySubjectLink, UUID> {

    /**
     * Lists the links in force from one pseudonym.
     *
     * @param sourcePseudonym the pseudonym being linked from
     * @param state normally {@code ACTIVE}
     * @return possibly empty list of links
     */
    List<PrivacySubjectLink> findBySourcePseudonymAndState(String sourcePseudonym,
            SubjectLinkState state);

    /**
     * Lists the links in force onto one pseudonym.
     *
     * @param targetPseudonym the pseudonym being linked to
     * @param state normally {@code ACTIVE}
     * @return possibly empty list of links
     */
    List<PrivacySubjectLink> findByTargetPseudonymAndState(String targetPseudonym,
            SubjectLinkState state);

    /**
     * Reports whether a subject has been suppressed under an erasure request.
     *
     * <pre>{@code
     * SELECT count(*) > 0 FROM privacy_subject_links
     * WHERE state = 'SUPPRESSED'
     *   AND (source_pseudonym = :pseudonym OR target_pseudonym = :pseudonym)
     * }</pre>
     *
     * <p>Calling this is a courtesy to the caller, not a safety mechanism: the database refuses a new
     * assignment for a suppressed subject whether or not anybody asked first.</p>
     *
     * @param pseudonym the subject to test
     * @return {@code true} when the subject asked to be forgotten
     */
    @Query("""
            SELECT count(*) > 0 FROM privacy_subject_links
            WHERE state = 'SUPPRESSED'
              AND (source_pseudonym = :pseudonym OR target_pseudonym = :pseudonym)
            """)
    boolean isSuppressed(@Param("pseudonym") String pseudonym);

    /**
     * Lists suppressions recorded since an instant, for the propagation worker.
     *
     * <pre>{@code
     * SELECT * FROM privacy_subject_links
     * WHERE state = 'SUPPRESSED' AND suppressed_at >= :since
     * ORDER BY suppressed_at
     * }</pre>
     *
     * @param since how far back to look
     * @return possibly empty list, oldest suppression first
     */
    @Query("""
            SELECT * FROM privacy_subject_links
            WHERE state = 'SUPPRESSED' AND suppressed_at >= :since
            ORDER BY suppressed_at
            """)
    List<PrivacySubjectLink> findSuppressedSince(@Param("since") Instant since);
}
