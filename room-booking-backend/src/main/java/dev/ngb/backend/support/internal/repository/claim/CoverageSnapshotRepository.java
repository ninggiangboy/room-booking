package dev.ngb.backend.support.internal.repository.claim;

import dev.ngb.backend.support.internal.model.claim.CoverageSnapshot;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the coverage frozen against a booking.
 *
 * <p>Never recomputed. A claim years later reads the terms that applied then, not the programme that
 * happens to be published now.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code coverage_snapshots}.</p>
 */
public interface CoverageSnapshotRepository extends ListCrudRepository<CoverageSnapshot, UUID> {

    /**
     * Finds the snapshot taken for a booking under one programme.
     *
     * <pre>{@code
     * SELECT * FROM coverage_snapshots
     * WHERE protection_program_version_id = :protectionProgramVersionId
     *   AND booking_id = :bookingId
     *   AND covered_account_holder_id = :coveredAccountHolderId
     *   AND snapshot_basis = :snapshotBasis
     * }</pre>
     *
     * <p>Matches {@code uk_coverage_snapshots_identity}, so at most one row can come back.</p>
     *
     * @param protectionProgramVersionId programme version
     * @param bookingId booking
     * @param coveredAccountHolderId covered party
     * @param snapshotBasis which moment fixed the terms
     * @return the snapshot, when one was taken
     */
    @Query("""
            SELECT * FROM coverage_snapshots
            WHERE protection_program_version_id = :protectionProgramVersionId
              AND booking_id = :bookingId
              AND covered_account_holder_id = :coveredAccountHolderId
              AND snapshot_basis = :snapshotBasis
            """)
    Optional<CoverageSnapshot> findSnapshot(
            @Param("protectionProgramVersionId") UUID protectionProgramVersionId,
            @Param("bookingId") UUID bookingId,
            @Param("coveredAccountHolderId") UUID coveredAccountHolderId,
            @Param("snapshotBasis") String snapshotBasis);

    /**
     * Lists the coverage relied on by one claim.
     *
     * @param damageClaimId claim
     * @return possibly empty list
     */
    List<CoverageSnapshot> findByDamageClaimId(UUID damageClaimId);
}
