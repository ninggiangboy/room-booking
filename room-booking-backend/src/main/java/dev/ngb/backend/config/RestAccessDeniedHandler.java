package dev.ngb.backend.config;

import dev.ngb.backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Writes the JSON response used when an authenticated user lacks permission.
 *
 * <p>{@code @Component} registers the handler, and Lombok generates constructor injection for the
 * JSON mapper. Implementing {@link AccessDeniedHandler} distinguishes authorization failure
 * ({@code 403}) from missing authentication ({@code 401}).</p>
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Produces the API error shape instead of Spring Security's default response.
     *
     * @param request forbidden request
     * @param response servlet response to populate
     * @param accessDeniedException framework authorization failure, not exposed to the client
     * @throws IOException when the JSON body cannot be written
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN",
                "you do not have permission to access this resource",
                Map.of(),
                request.getRequestURI()));
    }
}
