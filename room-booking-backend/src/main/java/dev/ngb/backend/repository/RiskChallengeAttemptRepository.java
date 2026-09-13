package dev.ngb.backend.repository;

import dev.ngb.backend.model.RiskChallengeAttempt;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the tries made at a challenge.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_challenge_attempts}.</p>
 */
public interface RiskChallengeAttemptRepository extends ListCrudRepository<RiskChallengeAttempt, UUID> {

    /**
     * Reads one challenge's attempts.
     *
     * <pre>{@code
     * SELECT * FROM risk_challenge_attempts WHERE risk_challenge_id = :riskChallengeId
     * ORDER BY attempt_number
     * }</pre>
     *
     * @param riskChallengeId the challenge
     * @return possibly empty list, in order
     */
    List<RiskChallengeAttempt> findByRiskChallengeIdOrderByAttemptNumber(UUID riskChallengeId);
}
