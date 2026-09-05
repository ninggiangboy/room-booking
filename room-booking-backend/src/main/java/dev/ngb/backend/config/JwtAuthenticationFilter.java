package dev.ngb.backend.config;

import dev.ngb.backend.service.auth.AccessTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Reads a bearer access token once per request and places the authenticated user in Spring Security.
 *
 * <p>{@code @Component} makes the filter discoverable by Spring. Lombok's
 * {@code @RequiredArgsConstructor} creates a constructor for the final token service. Extending
 * {@link OncePerRequestFilter} guarantees one execution per request dispatch.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenService accessTokenService;

    /**
     * Attempts bearer authentication, then always continues to the next filter.
     *
     * <p>{@code @Override} asks the compiler to verify that this signature implements the parent
     * hook. {@code @NonNull} documents Spring's nullness contract for framework-provided values.</p>
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain remaining servlet filters
     * @throws ServletException when a downstream servlet/filter fails
     * @throws IOException when request or response I/O fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
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
            UUID userId = UUID.fromString(claims.getSubject());
            List<SimpleGrantedAuthority> authorities = extractRoles(claims).stream()
                    // Spring Security's hasRole checks expect authorities to use the ROLE_ prefix.
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ignored) {
            // Invalid access tokens remain anonymous and are handled by Spring Security.
        }
    }

    private static List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (!(roles instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
