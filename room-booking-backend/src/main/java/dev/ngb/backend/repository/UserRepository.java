package dev.ngb.backend.repository;

import dev.ngb.backend.model.User;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides generated CRUD operations and email lookups for user aggregates.
 *
 * <p>Spring creates the implementation at runtime. {@code ListCrudRepository<User, UUID>} declares
 * both the managed aggregate and primary-key type; no handwritten SQL is needed for these derived
 * methods. Inherited operations conceptually generate {@code SELECT ... WHERE id = ?} for
 * {@code findById}, {@code INSERT}/{@code UPDATE} for {@code save}, and
 * {@code DELETE ... WHERE id = ?} for delete-by-ID. The exact selected column list and update form
 * remain an implementation detail of Spring Data JDBC.</p>
 */
public interface UserRepository extends ListCrudRepository<User, UUID> {

    /**
     * Loads one user while taking a transaction-scoped row lock.
     *
     * <p>The explicit query is:</p>
     *
     * <pre>{@code
     * SELECT * FROM users WHERE id = ? FOR UPDATE
     * }</pre>
     *
     * <p>{@code userId} is bound to the named parameter. The lock serializes concurrent
     * verification-email requests for the same account until the surrounding transaction ends.</p>
     *
     * @param userId account identifier
     * @return optional locked user, empty when no row matches
     */
    @Query("SELECT * FROM users WHERE id = :userId FOR UPDATE")
    Optional<User> findByIdForUpdate(UUID userId);

    /**
     * Finds the user whose normalized address equals the supplied value.
     *
     * <p>Spring Data parses {@code findByEmail}: {@code findBy} selects rows and {@code Email}
     * becomes the mapped {@code email} column. It generates SQL equivalent to:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM users
     * WHERE email = ?
     * }</pre>
     *
     * <p>The method argument is safely bound to {@code ?}; it is not concatenated into SQL.</p>
     *
     * @param email normalized email address
     * @return optional user, empty when no row matches
     */
    Optional<User> findByEmail(String email);

    /**
     * Performs an existence query without loading an entire user row.
     *
     * <p>Spring Data parses {@code existsByEmail} as an existence projection with an email
     * predicate. The generated SQL is conceptually:</p>
     *
     * <pre>{@code
     * SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
     * FROM users
     * WHERE email = ?
     * }</pre>
     *
     * <p>The concrete dialect may use {@code COUNT}, {@code EXISTS}, or a limited query; callers
     * should rely on the returned boolean rather than a particular SQL rendering.</p>
     *
     * @param email normalized email address
     * @return whether a matching row exists
     */
    boolean existsByEmail(String email);
}
