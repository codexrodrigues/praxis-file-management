package br.com.praxis.filemanagement.web.error;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
/**
 * Final public error contract for all REST endpoints.
 */
public record ErrorResponse(
        @Schema(description = "Praxis-style status indicator", example = "failure")
        String status,
        @Schema(description = "Human-readable summary message", example = "Formato JSON inválido para opções de upload")
        String message,
        @ArraySchema(schema = @Schema(description = "Canonical list of structured errors for new consumers"))
        List<Map<String, Object>> errors,
        @Schema(description = "Timestamp of the error response")
        Instant timestamp,
        @Schema(description = "Correlation identifier for tracing", example = "abc123")
        String traceId
) {
    public static ErrorResponse of(String code, String message, String details) {
        Map<String, Object> errorItem = details != null
                ? Map.of("code", code, "message", message, "details", details)
                : Map.of("code", code, "message", message);
        return new ErrorResponse("failure", message, List.of(errorItem), Instant.now(), null);
    }

    @SuppressWarnings("unchecked")
    public static ErrorResponse fromMap(Map<String, Object> map) {
        Object ts = map.get("timestamp");
        Instant instant = ts instanceof String ? Instant.parse((String) ts) : Instant.now();
        String traceId = map.get("traceId") instanceof String ? (String) map.get("traceId") : null;
        String status = map.get("status") instanceof String ? (String) map.get("status") : "failure";
        List<Map<String, Object>> errors = map.get("errors") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : null;
        if (errors == null) {
            String code = map.get("code") instanceof String ? (String) map.get("code") : "ERRO_DESCONHECIDO";
            String message = map.get("message") instanceof String ? (String) map.get("message") : "Erro interno";
            Object details = map.get("details");
            Map<String, Object> errorItem = details != null
                    ? Map.of("code", code, "message", message, "details", details)
                    : Map.of("code", code, "message", message);
            errors = List.of(errorItem);
        }
        return new ErrorResponse(
                status,
                (String) map.get("message"),
                errors,
                instant,
                traceId
        );
    }

    public ErrorResponse withTraceId(String traceId) {
        return new ErrorResponse(status, message, errors, timestamp, traceId);
    }
}
