package dev.ngb.backend.repository;

import dev.ngb.backend.model.Role;
import dev.ngb.backend.model.UserRole;
import dev.ngb.backend.model.UserRoleId;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores role assignments and reads the roles granted to a user.
 *
 * <p>{@code @Query} is used because callers need an enum projection rather than complete
 * {@link UserRole} rows. CRUD methods inherited from {@code ListCrudRepository} operate on the
 * composite {@link UserRoleId} key and the {@code user_roles} table.</p>
 */
public interface UserRoleRepository extends ListCrudRepository<UserRole, UserRoleId> {

    /**
     * Idempotently grants a role using explicit SQL for the composite primary key.
     *
     * <pre>{@code
     * INSERT INTO user_roles (user_id, role, created_at)
     * VALUES (:userId, :role, :createdAt)
     * ON CONFLICT (user_id, role) DO NOTHING
     * }</pre>
     *
     * <p>The named parameters bind the account UUID, enum name, and grant instant. The returned
     * count is one for a new assignment and zero when the assignment already existed.</p>
     *
     * @param userId account receiving the role
     * @param role enum name stored by the table constraint
     * @param createdAt grant instant
     * @return number of inserted rows, either zero or one
     */
    @Modifying
    @Query("""
            INSERT INTO user_roles (user_id, role, created_at)
            VALUES (:userId, :role, :createdAt)
            ON CONFLICT (user_id, role) DO NOTHING
            """)
    int grantRole(
            @Param("userId") UUID userId,
            @Param("role") String role,
            @Param("createdAt") Instant createdAt);

    /**
     * Returns roles granted to one user in deterministic enum-text order.
     *
     * <p>This method is not name-derived: {@code @Query} supplies the exact SQL:</p>
     *
     * <pre>{@code
     * SELECT role
     * FROM user_roles
     * WHERE user_id = :userId
     * ORDER BY role
     * }</pre>
     *
     * <p>{@code @Param("userId")} binds the UUID argument to {@code :userId}. Selecting only
     * {@code role} lets Spring Data convert each returned database value directly to {@link Role}
     * rather than constructing complete {@link UserRole} objects.</p>
     *
     * @param userId account whose roles should be loaded
     * @return possibly empty list of roles
     */
    @Query("SELECT role FROM user_roles WHERE user_id = :userId ORDER BY role")
    List<Role> findRolesByUserId(@Param("userId") UUID userId);
}
