package dev.ngb.backend.repository;

import dev.ngb.backend.model.Role;
import dev.ngb.backend.model.UserRole;
import dev.ngb.backend.model.UserRoleId;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
