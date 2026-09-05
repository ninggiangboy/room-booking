package dev.ngb.backend.repository;

import dev.ngb.backend.model.AuthToken;
import dev.ngb.backend.model.AuthTokenType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence queries for opaque authentication and verification tokens.
 *
 * <p>Spring Data derives both queries from their method names. Combining hash with token type
 * prevents a secret issued for one purpose from being looked up as another. CRUD methods inherited
 * from {@code ListCrudRepository} generate normal ID-based select, insert/update, and delete SQL
 * for the {@code auth_tokens} table.</p>
 */
public interface AuthTokenRepository extends ListCrudRepository<AuthToken, UUID> {

    /**
     * Finds a token by its non-reversible hash and intended use.
     *
     * <p>{@code findBy} starts a select query. {@code TokenHashAndType} becomes two equality
     * predicates joined by {@code AND}, using the entity-to-column naming convention:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM auth_tokens
     * WHERE token_hash = ?
     *   AND type = ?
     * }</pre>
     *
     * <p>Both values are bound parameters. Returning {@link Optional} expresses that zero or one
     * row is expected; multiple matching rows indicate a violated database invariant.</p>
     *
     * @param tokenHash SHA-256 digest of the raw token
     * @param type required token purpose
     * @return optional matching token record
     */
    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthTokenType type);

    /**
     * Finds every unconsumed token of one type so older tokens can be invalidated.
     *
     * <p>Spring splits the property path at each {@code And}. The suffix {@code IsNull} is an
     * operator and therefore produces {@code IS NULL} without consuming a method argument:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM auth_tokens
     * WHERE user_id = ?
     *   AND type = ?
     *   AND consumed_at IS NULL
     * }</pre>
     *
     * <p>The list return type asks Spring Data to materialize every matching row, returning an
     * empty list rather than {@code null} when nothing matches.</p>
     *
     * @param userId token owner
     * @param type token purpose
     * @return possibly empty list of unconsumed records
     */
    List<AuthToken> findAllByUserIdAndTypeAndConsumedAtIsNull(
            UUID userId,
            AuthTokenType type);
}
