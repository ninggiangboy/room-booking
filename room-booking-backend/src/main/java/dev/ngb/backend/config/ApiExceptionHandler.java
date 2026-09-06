package dev.ngb.backend.config;

import dev.ngb.backend.dto.ApiErrorResponse;
import dev.ngb.backend.exception.BadRequestException;
import dev.ngb.backend.exception.ConflictException;
import dev.ngb.backend.exception.DomainException;
import dev.ngb.backend.exception.ForbiddenException;
import dev.ngb.backend.exception.NotFoundException;
import dev.ngb.backend.exception.TooManyRequestsException;
import dev.ngb.backend.exception.UnauthorizedException;
import dev.ngb.backend.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts application exceptions into the API's consistent JSON error contract.
 *
 * <p>{@code @RestControllerAdvice} applies these handlers to every REST controller and serializes
 * returned bodies as JSON. Each {@code @ExceptionHandler} declares the exception types handled by
 * one method.</p>
 */
@RestControllerAdvice
@Slf4j
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
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (exception instanceof TooManyRequestsException rateLimitException
                && rateLimitException.getRetryAfterSeconds() != null) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    rateLimitException.getRetryAfterSeconds().toString());
        }
        return response.body(new ApiErrorResponse(
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
     * @return {@code 400 Bad Request} describing every rejected field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidArgument(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, List<String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.mapping(ApiExceptionHandler::validationMessage, Collectors.toList())));
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                ValidationException.CODE,
                "validation failed",
                Map.of("errors", errors),
                request.getRequestURI()));
    }

    private static String validationMessage(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return message != null ? message : "invalid value";
    }

    /**
     * Converts unexpected failures into a safe, consistent response without exposing internals.
     *
     * @param exception unexpected failure retained in server logs for diagnosis
     * @param request servlet request used to report the failed path
     * @return generic {@code 500 Internal Server Error} response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("Unexpected error while handling {}", request.getRequestURI(), exception);
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                "INTERNAL_SERVER_ERROR",
                "an unexpected error occurred",
                Map.of(),
                request.getRequestURI()));
    }

    private static HttpStatus statusFor(DomainException exception) {
        // HTTP semantics are defined by the abstract domain exception type, not each concrete error.
        return switch (exception) {
            case BadRequestException ignored -> HttpStatus.BAD_REQUEST;
            case UnauthorizedException ignored -> HttpStatus.UNAUTHORIZED;
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case TooManyRequestsException ignored -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
