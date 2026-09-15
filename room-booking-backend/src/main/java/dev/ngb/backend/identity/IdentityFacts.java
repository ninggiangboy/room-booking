package dev.ngb.backend.identity;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import dev.ngb.backend.identity.internal.model.account.User;
import dev.ngb.backend.identity.internal.repository.account.UserRepository;
import dev.ngb.backend.identity.internal.service.authz.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * Reloads the facts a request needs to trust about its authenticated principal, straight from
 * PostgreSQL, on every call.
 *
 * <p>{@code @Service} makes this stateless capability injectable; Lombok's {@code
 * @RequiredArgsConstructor} generates constructor injection for its dependencies. It is promoted
 * to this module root for the same reason {@link AccessTokenService} is: {@code
 * dev.ngb.backend.config.JwtAuthenticationFilter} is a genuine external consumer, not an internal
 * collaborator, and everything under {@code internal} is invisible outside this module. See
 * {@code docs/modules/identity.md}.</p>
 *
 * <p>The filter that calls this class used to build authorities purely from the JWT's own claims,
 * which meant a suspended or deleted account kept full access until its access token naturally
 * expired. Resolving status and capabilities here, per request, is what closes that gap -- and it
 * is also what makes capabilities rather than JWT role claims the source of authority, since a
 * claim signed fifteen minutes ago cannot reflect a grant issued one minute ago.</p>
 */
@Service
@RequiredArgsConstructor
public class IdentityFacts {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    /**
     * Resolves whether a principal is currently active and what it may currently do.
     *
     * @param userId principal identified by the request's authenticated subject
     * @return current status and global capabilities, or an inactive result with no capabilities
     *     when the principal no longer exists
     */
    public AuthenticatedPrincipal resolve(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new AuthenticatedPrincipal(userId, user.isActive(), capabilityNames(userId)))
                .orElseGet(() -> new AuthenticatedPrincipal(userId, false, Set.of()));
    }

    private Set<String> capabilityNames(UUID userId) {
        return authorizationService.effectiveGlobalCapabilities(userId, clock.instant()).stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * The facts a request needs about its authenticated principal, reloaded fresh from the
     * database rather than trusted from a signed claim that may be minutes stale.
     *
     * @param holderId identifier of the resolved principal, echoed back from the request
     * @param active {@code true} only when the principal may currently perform business
     *     operations; {@code false} for a suspended, closed, or deleted {@link User}
     * @param capabilities immutable set of capability names currently in force globally
     */
    public record AuthenticatedPrincipal(UUID holderId, boolean active, Set<String> capabilities) {
    }
}
