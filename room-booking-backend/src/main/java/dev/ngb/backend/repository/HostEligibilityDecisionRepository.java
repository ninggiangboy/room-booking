package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostEligibilityDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what the platform concluded about a host's capabilities, and why.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code host_eligibility_decisions}. Decisions are superseded rather than edited, so nothing
 * here updates one.</p>
 *
 * <p>This repository answers "why does this host hold this capability". It does not answer "may this
 * host publish" — that question is asked of {@code capability_grants}, which remains the single
 * authority the rest of the platform evaluates.</p>
 */
public interface HostEligibilityDecisionRepository
        extends ListCrudRepository<HostEligibilityDecision, UUID> {

    /**
     * Finds the decision currently standing for one capability in one market.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_eligibility_decisions
     * WHERE host_legal_profile_id = :profileId
     *   AND market_code = :marketCode
     *   AND capability = :capability
     *   AND superseded_by IS NULL
     * ORDER BY decided_at DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>Used to explain a capability to an operator or a host, and to decide what a new assessment
     * would be superseding.</p>
     *
     * @param profileId legal profile in question
     * @param marketCode market whose rules applied
     * @param capability capability being explained
     * @return the standing decision, when one has been made
     */
    @Query("""
            SELECT *
            FROM host_eligibility_decisions
            WHERE host_legal_profile_id = :profileId
              AND market_code = :marketCode
              AND capability = :capability
              AND superseded_by IS NULL
            ORDER BY decided_at DESC
            LIMIT 1
            """)
    Optional<HostEligibilityDecision> findStanding(
            @Param("profileId") UUID profileId,
            @Param("marketCode") String marketCode,
            @Param("capability") String capability);

    /**
     * Returns the full decision history for a profile, newest first.
     *
     * <p>Spring derives {@code WHERE host_legal_profile_id = ? ORDER BY decided_at DESC}. Includes
     * superseded decisions, because the reasoning behind a past decision is exactly what an appeal or
     * an audit needs to see.</p>
     *
     * @param hostLegalProfileId legal profile whose history is being read
     * @return possibly empty decision history, newest first
     */
    List<HostEligibilityDecision> findAllByHostLegalProfileIdOrderByDecidedAtDesc(
            UUID hostLegalProfileId);
}
