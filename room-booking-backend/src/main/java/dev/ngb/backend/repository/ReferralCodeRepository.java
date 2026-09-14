package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReferralCode;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the referral codes people share and earn from.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code referral_codes}.</p>
 */
public interface ReferralCodeRepository extends ListCrudRepository<ReferralCode, UUID> {

    /**
     * Finds the code somebody typed in.
     *
     * @param code the shareable code
     * @return the code row, when it exists
     */
    Optional<ReferralCode> findByCode(String code);

    /**
     * Finds one person's code under one set of referral terms.
     *
     * @param growthProgramVersionId the terms
     * @param ownerAccountHolderId the owner
     * @return their code, when they have one
     */
    Optional<ReferralCode> findByGrowthProgramVersionIdAndOwnerAccountHolderId(
            UUID growthProgramVersionId, UUID ownerAccountHolderId);

    /**
     * Lists every code one person holds.
     *
     * @param ownerAccountHolderId the owner
     * @return possibly empty list
     */
    List<ReferralCode> findByOwnerAccountHolderId(UUID ownerAccountHolderId);

    /**
     * Lists the codes that have earned most under one set of terms, which is where a referral ring
     * shows up first.
     *
     * <pre>{@code
     * SELECT * FROM referral_codes
     * WHERE growth_program_version_id = :growthProgramVersionId AND qualified_count > 0
     * ORDER BY qualified_count DESC
     * LIMIT :limit
     * }</pre>
     *
     * @param growthProgramVersionId the terms
     * @param limit how many codes to return
     * @return possibly empty list, most qualified referrals first
     */
    @Query("""
            SELECT * FROM referral_codes
            WHERE growth_program_version_id = :growthProgramVersionId AND qualified_count > 0
            ORDER BY qualified_count DESC
            LIMIT :limit
            """)
    List<ReferralCode> findTopEarning(
            @Param("growthProgramVersionId") UUID growthProgramVersionId,
            @Param("limit") int limit);
}
