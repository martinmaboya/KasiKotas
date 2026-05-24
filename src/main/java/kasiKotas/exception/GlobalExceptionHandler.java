package kasiKotas.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", request);
    }

    @ExceptionHandler({InsufficientStockException.class, OrderLimitExceededException.class})
    public ResponseEntity<ApiError> handleBusinessConflict(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "BUSINESS_CONFLICT", request);
    }

    @ExceptionHandler({
            ConcurrencyConflictException.class,
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            DeadlockLoserDataAccessException.class
    })
    public ResponseEntity<ApiError> handleConcurrencyConflict(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT,
                "High traffic right now. Please try again.",
                "CONCURRENCY_CONFLICT",
                request);
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), "FORBIDDEN", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleInvalidPayload(Exception ignored, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request payload.", "INVALID_PAYLOAD", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException ignored, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Request conflicts with existing data.", "DATA_CONFLICT", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ignored, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", "INTERNAL_ERROR", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, String code, HttpServletRequest request) {
        String safeMessage = (message == null || message.isBlank()) ? status.getReasonPhrase() : message;
        ApiError error = ApiError.of(safeMessage, code, request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}



