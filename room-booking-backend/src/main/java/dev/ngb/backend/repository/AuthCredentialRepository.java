package dev.ngb.backend.repository;

import dev.ngb.backend.model.AuthCredential;
import dev.ngb.backend.model.CredentialType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the authentication material enrolled for a principal.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code auth_credentials}. No method returns a usable secret: password rows hold
 * only a verifier digest, and recoverable material is held by reference.</p>
 */
public interface AuthCredentialRepository extends ListCrudRepository<AuthCredential, UUID> {

    /**
     * Finds the live credential of one type for a principal.
     *
     * <pre>{@code
     * SELECT *
     * FROM auth_credentials
     * WHERE user_id = :userId
     *   AND credential_type = :credentialType
     *   AND disabled_at IS NULL
     * }</pre>
     *
     * <p>{@code uk_auth_credentials_one_active} guarantees at most one row matches, which is what
     * stops the login path from having to guess which of two passwords is current.</p>
     *
     * @param userId principal being authenticated
     * @param credentialType kind of material required
     * @return the active credential when one is enrolled
     */
    @Query("""
            SELECT *
            FROM auth_credentials
            WHERE user_id = :userId
              AND credential_type = :credentialType
              AND disabled_at IS NULL
            """)
    Optional<AuthCredential> findActive(
            @Param("userId") UUID userId,
            @Param("credentialType") String credentialType);

    /**
     * Returns every credential ever enrolled for a principal, newest first.
     *
     * <p>Spring derives {@code WHERE user_id = ? ORDER BY enrolled_at DESC}. Includes disabled rows,
     * because an investigation needs to see that a factor was once present and when it went away.</p>
     *
     * @param userId principal whose enrolments are being reviewed
     * @return possibly empty list of credentials, newest first
     */
    List<AuthCredential> findAllByUserIdOrderByEnrolledAtDesc(UUID userId);

    /**
     * Counts the live credentials of one type for a principal.
     *
     * <p>Spring derives {@code SELECT count(*) ... WHERE user_id = ? AND credential_type = ? AND
     * disabled_at IS NULL}. Used before disabling a factor, so a principal cannot remove their last
     * means of signing in.</p>
     *
     * @param userId principal being checked
     * @param credentialType kind of material
     * @return number of active credentials of that type
     */
    long countByUserIdAndCredentialTypeAndDisabledAtIsNull(
            UUID userId,
            CredentialType credentialType);
}
