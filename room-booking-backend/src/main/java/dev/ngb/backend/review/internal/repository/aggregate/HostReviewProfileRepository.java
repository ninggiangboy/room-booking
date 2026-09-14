package dev.ngb.backend.review.internal.repository.aggregate;

import dev.ngb.backend.review.internal.model.aggregate.HostReviewProfile;
import dev.ngb.backend.review.types.DerivedProfileStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the ranking-quality projection for hosts.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_review_profiles}.</p>
 */
public interface HostReviewProfileRepository extends ListCrudRepository<HostReviewProfile, UUID> {

    /**
     * Finds the profile in force for a host.
     *
     * <p>Spring derives {@code WHERE host_account_holder_id = ? AND status = ?}, matching
     * {@code uk_host_review_profiles_current} when the status is {@code CURRENT}.</p>
     *
     * @param hostAccountHolderId host
     * @param status status to match, normally {@code CURRENT}
     * @return the current profile, when one exists
     */
    Optional<HostReviewProfile> findByHostAccountHolderIdAndStatus(UUID hostAccountHolderId,
            DerivedProfileStatus status);

    /**
     * Lists current profiles that are due for recomputation.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_review_profiles
     * WHERE status = 'CURRENT'
     *   AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * @param at instant to treat as now
     * @param batchSize maximum profiles to return
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM host_review_profiles
            WHERE status = 'CURRENT'
              AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<HostReviewProfile> findExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
