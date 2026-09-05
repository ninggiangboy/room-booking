package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostProfile;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

/**
 * Provides generated CRUD operations for host profiles.
 *
 * <p>Spring creates the implementation automatically; the generic parameters declare that
 * {@link HostProfile} aggregates use {@link UUID} identifiers. This repository declares no custom
 * finder. Its inherited methods generate SQL conceptually equivalent to:</p>
 *
 * <pre>{@code
 * -- findById(userId)
 * SELECT ... FROM host_profiles WHERE user_id = ?;
 *
 * -- save(newProfile)
 * INSERT INTO host_profiles (...) VALUES (...);
 *
 * -- save(existingProfile), including optimistic-lock protection
 * UPDATE host_profiles
 * SET ..., version = version + 1
 * WHERE user_id = ? AND version = ?;
 *
 * -- deleteById(userId)
 * DELETE FROM host_profiles WHERE user_id = ?;
 * }</pre>
 *
 * <p>Spring Data chooses insert versus update from aggregate identity/version state. Ellipses
 * intentionally stand for the framework-generated mapped column list.</p>
 */
public interface HostProfileRepository extends ListCrudRepository<HostProfile, UUID> {
}
