package br.com.praxis.filemanagement.web.response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralizes the final API envelopes for praxis-file-management.
 */
public final class ApiEnvelopeFactory {

    private ApiEnvelopeFactory() {
    }

    public static Map<String, Object> success(Object data, String message) {
        return response("success", message, data, Map.of());
    }

    public static Map<String, Object> response(
            String status,
            String message,
            Object data,
            Map<String, Object> extensions
    ) {
        LinkedHashMap<String, Object> body = baseEnvelope(status, message);
        if (data != null) {
            body.put("data", data);
        }
        body.putAll(extensions);
        return Map.copyOf(body);
    }

    public static Map<String, Object> failure(String code, String message, String details) {
        return failure(code, message, details, Map.of());
    }

    public static Map<String, Object> failure(
            String code,
            String message,
            String details,
            Map<String, Object> extensions
    ) {
        LinkedHashMap<String, Object> body = baseEnvelope("failure", message);
        LinkedHashMap<String, Object> errorItem = new LinkedHashMap<>();
        errorItem.put("code", code);
        errorItem.put("message", message);
        if (details != null) {
            errorItem.put("details", details);
        }
        body.put("errors", List.of(Map.copyOf(errorItem)));
        body.putAll(extensions);
        return Map.copyOf(body);
    }

    public static Map<String, Object> fromSingleUploadResult(Map<String, Object> standardFormat, String defaultSuccessMessage) {
        boolean success = Boolean.TRUE.equals(standardFormat.get("success"));
        if (success) {
            return success(standardFormat.get("data"), defaultSuccessMessage);
        }

        String message = stringValue(standardFormat.get("message"));
        String details = stringValue(standardFormat.get("details"));
        String code = stringValue(standardFormat.get("code"));
        return failure(code != null ? code : "ERRO_DESCONHECIDO", message != null ? message : "Erro no upload", details);
    }

    private static LinkedHashMap<String, Object> baseEnvelope(String status, String message) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());
        return body;
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }
}
