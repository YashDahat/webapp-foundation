package com.webappfoundation.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Translates thrown exceptions into a stable JSON error body <code>{ status, error, message, timestamp }</code>
 * so the frontend apiClient interceptor can surface <code>data.message</code> in a toast. Without this, Spring's
 * default error body omits the reason (<code>server.error.include-message=never</code>) and the UI sees only the
 * bare status code (e.g. "Request failed with status code 409").
 *
 * <p>Only intended, caller-thrown exceptions carry their message through: {@link ResourceNotFoundException} → 404,
 * {@link ResponseStatusException} → its status + reason, bean-validation failures → 400. Anything unexpected is left
 * to the default handler (generic 500) so internal details never leak.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Stable error shape consumed by the frontend ({@code error.response.data.message}). */
    public record ErrorResponse(int status, String error, String message, Instant timestamp) {
        static ErrorResponse of(HttpStatus status, String message) {
            return new ErrorResponse(status.value(), status.getReasonPhrase(), message, Instant.now());
        }
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message));
    }

    /** Bad credentials / failed authentication → 401 with a generic message (no account-existence leak). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Optimistic-lock clash → 409. The order board stamps {@code Order.@Version}; when two users move the
     * same card concurrently the stale write fails here so the UI can refresh and retry (Jira-style).
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(HttpStatus.CONFLICT,
                "This order was just updated by someone else — refresh and try again"));
    }
}
