package dev.ngb.backend.config;

import dev.ngb.backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Writes the JSON response used when a request has not been authenticated.
 *
 * <p>{@code @Component} registers this security adapter, while Lombok generates constructor
 * injection for Jackson's {@link ObjectMapper}. Implementing {@link AuthenticationEntryPoint}
 * lets Spring Security delegate missing/invalid authentication failures here.</p>
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Produces the API error shape instead of Spring Security's default HTML response.
     *
     * @param request request that required authentication
     * @param response servlet response to populate
     * @param authException framework authentication failure; deliberately not exposed to clients
     * @throws IOException when the JSON body cannot be written
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            @NonNull AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "authentication is required",
                Map.of(),
                request.getRequestURI()));
    }
}
