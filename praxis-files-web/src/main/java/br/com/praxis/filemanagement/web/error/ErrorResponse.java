package br.com.praxis.filemanagement.web.error;

import java.time.Instant;
import java.util.Map;
/**
 * Standard error response contract for all REST endpoints.
 */
public record ErrorResponse(
        String code,
        String message,
        String details,
        Instant timestamp,
        String traceId
) {
    public static ErrorResponse of(String code, String message, String details) {
        return new ErrorResponse(code, message, details, Instant.now(), null);
    }

    @SuppressWarnings("unchecked")
    public static ErrorResponse fromMap(Map<String, Object> map) {
        Object ts = map.get("timestamp");
        Instant instant = ts instanceof String ? Instant.parse((String) ts) : Instant.now();
        String traceId = map.get("traceId") instanceof String ? (String) map.get("traceId") : null;
        return new ErrorResponse(
                (String) map.get("code"),
                (String) map.get("message"),
                (String) map.get("details"),
                instant,
                traceId
        );
    }

    public ErrorResponse withTraceId(String traceId) {
        return new ErrorResponse(code, message, details, timestamp, traceId);
    }
}
