package dev.ngb.backend.growth.internal.repository.referral;

import dev.ngb.backend.growth.internal.model.referral.ReferralAttribution;
import dev.ngb.backend.growth.internal.model.referral.ReferralAttributionState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.referral.ReferralAttribution;
import dev.ngb.backend.growth.internal.model.referral.ReferralAttributionState;


/**
 * Reads who referred whom, and what the anti-abuse screening found about the pair.
 *
 * <p>A person is referred once per programme, which the unique constraint enforces and this
 * repository relies on.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code referral_attributions}.</p>
 */
public interface ReferralAttributionRepository extends ListCrudRepository<ReferralAttribution, UUID> {

    /**
     * Finds whether somebody has already been referred under one programme.
     *
     * @param growthProgramId the programme
     * @param refereeAccountHolderId the person referred
     * @return the referral, when one was already recorded
     */
    Optional<ReferralAttribution> findByGrowthProgramIdAndRefereeAccountHolderId(
            UUID growthProgramId, UUID refereeAccountHolderId);

    /**
     * Lists one referrer's referrals in one state.
     *
     * @param referrerAccountHolderId the referrer
     * @param state where the referrals stand
     * @return possibly empty list
     */
    List<ReferralAttribution> findByReferrerAccountHolderIdAndState(UUID referrerAccountHolderId,
            ReferralAttributionState state);

    /**
     * Lists the referrals that qualified despite a screening signal, which is the set a fraud
     * review of the programme has to read.
     *
     * <pre>{@code
     * SELECT * FROM referral_attributions
     * WHERE state = 'QUALIFIED'
     *   AND (shared_device_signal OR shared_contact_signal OR shared_instrument_signal)
     * ORDER BY qualified_at DESC
     * }</pre>
     *
     * @return possibly empty list, most recently qualified first
     */
    @Query("""
            SELECT * FROM referral_attributions
            WHERE state = 'QUALIFIED'
              AND (shared_device_signal OR shared_contact_signal OR shared_instrument_signal)
            ORDER BY qualified_at DESC
            """)
    List<ReferralAttribution> findOverridden();

    /**
     * Lists the referrals waiting on a qualifying booking, oldest first, so a sweep can close the
     * ones that are never going to qualify.
     *
     * <pre>{@code
     * SELECT * FROM referral_attributions
     * WHERE state = 'PENDING' AND attributed_at <= :before
     * ORDER BY attributed_at
     * }</pre>
     *
     * @param before instant before which a referral counts as stale
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM referral_attributions
            WHERE state = 'PENDING' AND attributed_at <= :before
            ORDER BY attributed_at
            """)
    List<ReferralAttribution> findStalePending(@Param("before") Instant before);
}
