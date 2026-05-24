package kasiKotas.exception;

import java.time.Instant;

/**
 * Consistent API error payload returned by GlobalExceptionHandler.
 */
public record ApiError(
        String message,
        String code,
        Instant timestamp,
        String path
) {
    public static ApiError of(String message, String code, String path) {
        return new ApiError(message, code, Instant.now(), path);
    }
}

