package dev.ngb.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.identity.AccessTokenService;
import dev.ngb.backend.identity.IdentityFacts;


/**
 * Reads a bearer access token once per request and places the authenticated principal in Spring
 * Security, after reloading its current status and capabilities from PostgreSQL.
 *
 * <p>{@code @Component} makes the filter discoverable by Spring. Lombok's
 * {@code @RequiredArgsConstructor} creates a constructor for the two final collaborators.
 * Extending {@link OncePerRequestFilter} guarantees one execution per request dispatch.</p>
 *
 * <p>Authorities used to be built purely from the JWT's own {@code roles} claim, so a suspended
 * or deleted account kept full access until its access token naturally expired. Calling {@link
 * IdentityFacts} here, per request, is what closes that gap: the token still proves *who* is
 * asking, but {@code IdentityFacts} decides, right now, whether they may still act and what they
 * may do. Authorities carry raw capability names with no {@code ROLE_} prefix, since authority is
 * now a capability rather than a role.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;
    private final IdentityFacts identityFacts;

    /**
     * Attempts bearer authentication, then always continues to the next filter.
     *
     * <p>{@code @Override} asks the compiler to verify that this signature implements the parent
     * hook. Parameters are non-null by default under this package's {@code @NullMarked}.</p>
     *
     * @param request     current HTTP request
     * @param response    current HTTP response
     * @param filterChain remaining servlet filters
     * @throws ServletException when a downstream servlet/filter fails
     * @throws IOException      when request or response I/O fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authorization.substring(BEARER_PREFIX.length()), request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = accessTokenService.extractClaims(token);
            UUID subjectId = UUID.fromString(claims.getSubject());

            IdentityFacts.AuthenticatedPrincipal principal = identityFacts.resolve(subjectId);
            if (!principal.active()) {
                // A structurally valid, unexpired token belonging to a suspended or deleted
                // account remains anonymous rather than authenticated; this is the reload the
                // stateless JWT filter previously skipped.
                return;
            }

            List<SimpleGrantedAuthority> authorities = principal.capabilities().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(
                    subjectId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException _) {
            // Invalid access tokens remain anonymous and are handled by Spring Security.
        }
    }
}
