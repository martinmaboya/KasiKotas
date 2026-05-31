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
import kasiKotas.service.DailyOrderLimitService;
import kasiKotas.model.DailyOrderLimit;
import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final DailyOrderLimitService dailyOrderLimitService;

    public GlobalExceptionHandler(DailyOrderLimitService dailyOrderLimitService) {
        this.dailyOrderLimitService = dailyOrderLimitService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(InsufficientStockException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), "INSUFFICIENT_STOCK", request);
    }

    @ExceptionHandler(OrderLimitExceededException.class)
    public ResponseEntity<ApiError> handleOrderLimitExceeded(OrderLimitExceededException ex, HttpServletRequest request) {
        // Return a clear, dedicated code so frontend can react specifically to limit errors
        return build(HttpStatus.CONFLICT, ex.getMessage(), "ORDER_LIMIT_EXCEEDED", request);
    }

    @ExceptionHandler({
            ConcurrencyConflictException.class,
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            DeadlockLoserDataAccessException.class
    })
    public ResponseEntity<ApiError> handleConcurrencyConflict(Exception ex, HttpServletRequest request) {
        // If the daily limit is already zero, return a clear sold-out message instead of a generic retry.
        try {
            Optional<DailyOrderLimit> limit = dailyOrderLimitService.getOrderLimit();
            if (limit.isPresent() && limit.get().getLimitValue() <= 0) {
                return build(HttpStatus.CONFLICT,
                        "We are sold out for today. Please try again tomorrow.",
                        "ORDER_LIMIT_EXCEEDED",
                        request);
            }
        } catch (Exception ignored) {
            // Fall through to generic concurrency handling.
        }

        // Concurrency issues are transient; map to 503 Service Unavailable so clients can decide to retry
        return build(HttpStatus.SERVICE_UNAVAILABLE,
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
