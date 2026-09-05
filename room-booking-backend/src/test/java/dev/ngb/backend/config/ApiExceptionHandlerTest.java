package dev.ngb.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import dev.ngb.backend.dto.ApiErrorResponse;
import dev.ngb.backend.exception.EmailVerificationRateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Verifies transport details for domain-error responses. */
class ApiExceptionHandlerTest {

    /** Rate-limit failures include both the 429 status and standard retry header. */
    @Test
    void handleDomainMapsEmailVerificationRateLimit() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/email-verification/request");
        var exception = new EmailVerificationRateLimitException(
                Instant.parse("2026-09-05T12:01:00Z"), 60);

        ResponseEntity<ApiErrorResponse> response =
                new ApiExceptionHandler().handleDomain(exception, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("60", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(EmailVerificationRateLimitException.CODE, response.getBody().code());
    }
}
