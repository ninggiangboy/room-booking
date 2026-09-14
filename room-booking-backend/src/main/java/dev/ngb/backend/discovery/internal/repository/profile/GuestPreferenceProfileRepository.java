package dev.ngb.backend.discovery.internal.repository.profile;

import dev.ngb.backend.discovery.internal.model.profile.GuestPreferenceProfile;
import dev.ngb.backend.review.types.DerivedProfileStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.discovery.internal.model.profile.GuestPreferenceProfile;
import dev.ngb.backend.review.types.DerivedProfileStatus;


/**
 * Reads one guest's derived preference profile.
 *
 * <p>Callers must treat absence as the anonymous case rather than as an error: an opted-out guest, a
 * new guest, and a guest whose profile is being rebuilt all look the same here, and all three are
 * served the deterministic baseline.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code guest_preference_profiles}.</p>
 */
public interface GuestPreferenceProfileRepository extends ListCrudRepository<GuestPreferenceProfile, UUID> {

    /**
     * Finds the current profile for one guest.
     *
     * @param accountHolderId the guest
     * @param status normally {@code CURRENT}
     * @return the profile, when one stands
     */
    Optional<GuestPreferenceProfile> findByAccountHolderIdAndStatus(UUID accountHolderId,
            DerivedProfileStatus status);

    /**
     * Lists every version of one guest's profile, newest first, for the privacy console.
     *
     * @param accountHolderId the guest
     * @return possibly empty list, newest version first
     */
    List<GuestPreferenceProfile> findByAccountHolderIdOrderByProfileVersionDesc(
            UUID accountHolderId);

    /**
     * Lists current profiles past their freshness deadline, for the refresh worker.
     *
     * <pre>{@code
     * SELECT * FROM guest_preference_profiles
     * WHERE status = 'CURRENT' AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :limit
     * }</pre>
     *
     * @param at instant to compare against
     * @param limit batch size
     * @return possibly empty list, most stale first
     */
    @Query("""
            SELECT * FROM guest_preference_profiles
            WHERE status = 'CURRENT' AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :limit
            """)
    List<GuestPreferenceProfile> findStale(@Param("at") Instant at, @Param("limit") int limit);
}
