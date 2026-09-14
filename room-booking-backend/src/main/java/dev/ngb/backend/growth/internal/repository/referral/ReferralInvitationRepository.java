package dev.ngb.backend.growth.internal.repository.referral;

import dev.ngb.backend.growth.internal.model.referral.ReferralInvitation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.referral.ReferralInvitation;


/**
 * Reads the invitations sent under a referral code.
 *
 * <p>Addressed invitations are stored by a digest of the address, so this is how the platform
 * knows it has already written to somebody without keeping a list of people who never joined.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code referral_invitations}.</p>
 */
public interface ReferralInvitationRepository extends ListCrudRepository<ReferralInvitation, UUID> {

    /**
     * Lists the invitations sent under one code, newest first.
     *
     * @param referralCodeId the code
     * @return possibly empty list, most recently sent first
     */
    List<ReferralInvitation> findByReferralCodeIdOrderByInvitedAtDesc(UUID referralCodeId);

    /**
     * Finds whether one address has already been invited under one code.
     *
     * @param referralCodeId the code
     * @param invitedContactDigest digest of the address
     * @return the invitation, when that address was already written to
     */
    Optional<ReferralInvitation> findByReferralCodeIdAndInvitedContactDigest(UUID referralCodeId,
            String invitedContactDigest);

    /**
     * Lists the invitations that have run out and can stop being counted as outstanding.
     *
     * <pre>{@code
     * SELECT * FROM referral_invitations
     * WHERE state IN ('PENDING', 'VIEWED') AND expires_at <= :at
     * ORDER BY expires_at
     * }</pre>
     *
     * @param at instant the sweep is running for
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT * FROM referral_invitations
            WHERE state IN ('PENDING', 'VIEWED') AND expires_at <= :at
            ORDER BY expires_at
            """)
    List<ReferralInvitation> findExpirable(@Param("at") Instant at);
}
