package dev.ngb.backend.config;

import dev.ngb.backend.dto.ApiErrorResponse;
import dev.ngb.backend.exception.DomainException;
import dev.ngb.backend.exception.EmailAlreadyRegisteredException;
import dev.ngb.backend.exception.EmailAlreadyVerifiedException;
import dev.ngb.backend.exception.InvalidCredentialsException;
import dev.ngb.backend.exception.InvalidEmailVerificationTokenException;
import dev.ngb.backend.exception.InvalidRefreshTokenException;
import dev.ngb.backend.exception.InvalidPasswordResetTokenException;
import dev.ngb.backend.exception.UserAccountDisabledException;
import dev.ngb.backend.exception.UserNotFoundException;
import dev.ngb.backend.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Converts application exceptions into the API's consistent JSON error contract.
 *
 * <p>{@code @RestControllerAdvice} applies these handlers to every REST controller and serializes
 * returned bodies as JSON. Each {@code @ExceptionHandler} declares the exception types handled by
 * one method.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Maps a known business failure to an appropriate HTTP status and response body.
     *
     * @param exception domain failure raised by a service
     * @param request servlet request used to report the failed path
     * @return response containing a stable error code and structured data
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(
            DomainException exception, HttpServletRequest request) {
        HttpStatus status = statusFor(exception);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                exception.getCode(),
                exception.getMessage(),
                exception.getData(),
                request.getRequestURI()));
    }

    /**
     * Hides parser internals and reports malformed JSON or missing parameters as validation errors.
     *
     * @param exception framework parsing or parameter exception
     * @param request servlet request used to report the failed path
     * @return generic {@code 400 Bad Request} response
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                ValidationException.CODE,
                "request body or parameters are invalid",
                Map.of(),
                request.getRequestURI()));
    }

    /**
     * Converts Bean Validation failures into the same field-oriented error contract.
     *
     * @param exception validation result containing rejected fields
     * @param request servlet request used to report the failed path
     * @return {@code 400 Bad Request} describing the first rejected field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidArgument(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        var fieldError = exception.getBindingResult().getFieldErrors().getFirst();
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                ValidationException.CODE,
                fieldError.getDefaultMessage(),
                Map.of("field", fieldError.getField()),
                request.getRequestURI()));
    }

    private static HttpStatus statusFor(DomainException exception) {
        // Pattern matching keeps the HTTP mapping centralized while exceptions remain transport-neutral.
        return switch (exception) {
            case ValidationException ignored -> HttpStatus.BAD_REQUEST;
            case InvalidCredentialsException ignored -> HttpStatus.UNAUTHORIZED;
            case InvalidRefreshTokenException ignored -> HttpStatus.UNAUTHORIZED;
            case InvalidEmailVerificationTokenException ignored -> HttpStatus.BAD_REQUEST;
            case InvalidPasswordResetTokenException ignored -> HttpStatus.BAD_REQUEST;
            case UserNotFoundException ignored -> HttpStatus.NOT_FOUND;
            case UserAccountDisabledException ignored -> HttpStatus.FORBIDDEN;
            case EmailAlreadyRegisteredException ignored -> HttpStatus.CONFLICT;
            case EmailAlreadyVerifiedException ignored -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
