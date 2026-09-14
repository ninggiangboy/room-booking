package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReferralRewardGrant;
import dev.ngb.backend.model.ReferralBeneficiarySide;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the rewards owed to each side of a qualified referral.
 *
 * <p>A reward matures before it can be granted, and a granted reward is reversed rather than
 * deleted when the booking behind it is cancelled.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code referral_reward_grants}.</p>
 */
public interface ReferralRewardGrantRepository extends ListCrudRepository<ReferralRewardGrant, UUID> {

    /**
     * Lists both sides of the reward for one referral.
     *
     * @param referralAttributionId the referral
     * @return possibly empty list
     */
    List<ReferralRewardGrant> findByReferralAttributionId(UUID referralAttributionId);

    /**
     * Finds one side of the reward for one referral.
     *
     * @param referralAttributionId the referral
     * @param beneficiarySide which half
     * @return the reward, when that side has one
     */
    Optional<ReferralRewardGrant> findByReferralAttributionIdAndBeneficiarySide(
            UUID referralAttributionId, ReferralBeneficiarySide beneficiarySide);

    /**
     * Lists the rewards whose maturity delay has run, which is the set the granting job takes.
     *
     * <pre>{@code
     * SELECT * FROM referral_reward_grants
     * WHERE state = 'PENDING' AND matures_at <= :at
     * ORDER BY matures_at
     * }</pre>
     *
     * @param at instant the job is running for
     * @return possibly empty list, longest matured first
     */
    @Query("""
            SELECT * FROM referral_reward_grants
            WHERE state = 'PENDING' AND matures_at <= :at
            ORDER BY matures_at
            """)
    List<ReferralRewardGrant> findMatured(@Param("at") Instant at);

    /**
     * Sums what one programme version has actually handed out, which is what a budget is spent
     * against.
     *
     * <pre>{@code
     * SELECT coalesce(sum(amount_minor), 0) FROM referral_reward_grants
     * WHERE growth_program_version_id = :growthProgramVersionId AND state = 'GRANTED'
     * }</pre>
     *
     * @param growthProgramVersionId the terms
     * @return total granted, in integer minor units of the programme currency
     */
    @Query("""
            SELECT coalesce(sum(amount_minor), 0) FROM referral_reward_grants
            WHERE growth_program_version_id = :growthProgramVersionId AND state = 'GRANTED'
            """)
    long sumGranted(@Param("growthProgramVersionId") UUID growthProgramVersionId);

    /**
     * Counts how many rewards one person has already been granted under one set of terms, which is
     * what a per-participant cap is checked against.
     *
     * <pre>{@code
     * SELECT count(*) FROM referral_reward_grants
     * WHERE growth_program_version_id = :growthProgramVersionId
     *   AND beneficiary_holder_id = :beneficiaryHolderId
     *   AND state IN ('GRANTED', 'MATURED', 'PENDING')
     * }</pre>
     *
     * @param growthProgramVersionId the terms
     * @param beneficiaryHolderId the beneficiary
     * @return how many rewards are owed or already given
     */
    @Query("""
            SELECT count(*) FROM referral_reward_grants
            WHERE growth_program_version_id = :growthProgramVersionId
              AND beneficiary_holder_id = :beneficiaryHolderId
              AND state IN ('GRANTED', 'MATURED', 'PENDING')
            """)
    long countOutstandingFor(@Param("growthProgramVersionId") UUID growthProgramVersionId,
            @Param("beneficiaryHolderId") UUID beneficiaryHolderId);
}
