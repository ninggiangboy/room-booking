package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostLegalProfile;
import dev.ngb.backend.model.HostProfileLifecycle;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and stores the legal identity of selling hosts.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code host_legal_profiles}.</p>
 */
public interface HostLegalProfileRepository extends ListCrudRepository<HostLegalProfile, UUID> {

    /**
     * Finds the legal profile of one account holder.
     *
     * <p>Spring derives {@code WHERE account_holder_id = ?}, matching the
     * {@code uk_host_legal_profiles_holder} unique constraint: a holder has one legal identity, not
     * several.</p>
     *
     * @param accountHolderId holder whose profile is wanted
     * @return the profile when one has been started
     */
    Optional<HostLegalProfile> findByAccountHolderId(UUID accountHolderId);

    /**
     * Finds profiles whose legal name hashes to a given digest.
     *
     * <p>Spring derives {@code WHERE legal_name_digest = ?}. Screening matches on the digest so a
     * name search never scans plaintext legal names across every host on the platform. Several
     * profiles can share a digest when two hosts genuinely have the same legal name, so the result is
     * a list.</p>
     *
     * @param legalNameDigest SHA-256 digest of the name being matched
     * @return possibly empty list of profiles with that name
     */
    List<HostLegalProfile> findAllByLegalNameDigest(String legalNameDigest);

    /**
     * Returns profiles in one market and lifecycle state.
     *
     * <p>Spring derives {@code WHERE market_code = ? AND lifecycle_state = ?}. Backs operator queues
     * such as "submitted profiles awaiting verification in Vietnam".</p>
     *
     * @param marketCode market being reviewed
     * @param lifecycleState state to filter by
     * @return possibly empty list of profiles
     */
    List<HostLegalProfile> findAllByMarketCodeAndLifecycleState(
            String marketCode,
            HostProfileLifecycle lifecycleState);
}
