package dev.ngb.backend.exception;

import java.util.Map;

import lombok.Getter;

/**
 * Base business exception carrying a stable machine code and structured response data.
 *
 * <p>It extends {@link RuntimeException}, so transactional service methods roll back by default.
 * Lombok's {@code @Getter} generates accessors for {@code code} and {@code data}; the inherited
 * {@link #getMessage()} accessor supplies the human-readable message.</p>
 */
@Getter
public class DomainException extends RuntimeException {

    /** Stable value used by clients instead of parsing exception messages. */
    private final String code;
    /** Immutable safe context copied into the API error response. */
    private final Map<String, Object> data;

    /**
     * Creates a domain failure without additional structured data.
     *
     * @param code stable machine-readable error code
     * @param message human-readable explanation
     */
    public DomainException(String code, String message) {
        this(code, message, Map.of());
    }

    /**
     * Creates a domain failure with safe fields that may be returned to API clients.
     *
     * @param code stable machine-readable error code
     * @param message human-readable explanation
     * @param data safe structured response context
     */
    public DomainException(String code, String message, Map<String, Object> data) {
        super(message);
        this.code = code;
        this.data = Map.copyOf(data);
    }

    /**
     * Wraps an infrastructure failure while preserving a stable public error contract.
     *
     * @param code stable machine-readable error code
     * @param message human-readable explanation
     * @param cause underlying technical failure retained for diagnostics
     */
    public DomainException(String code, String message, Throwable cause) {
        this(code, message, Map.of(), cause);
    }

    /**
     * Creates a fully described domain failure with structured data and an underlying cause.
     *
     * @param code stable machine-readable error code
     * @param message human-readable explanation
     * @param data safe structured response context
     * @param cause underlying technical failure retained for diagnostics
     */
    public DomainException(String code, String message, Map<String, Object> data, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.data = Map.copyOf(data);
    }

}
