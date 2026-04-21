package br.com.praxis.filemanagement.web.response;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiEnvelopeFactoryTest {

    @Test
    void shouldBuildSuccessEnvelope() {
        Map<String, Object> response = ApiEnvelopeFactory.success(Map.of("id", "123"), "ok");

        assertEquals("success", response.get("status"));
        assertEquals("ok", response.get("message"));
        assertTrue(response.containsKey("data"));
        assertTrue(response.containsKey("timestamp"));
    }

    @Test
    void shouldBuildFailureEnvelopeWithDetails() {
        Map<String, Object> response = ApiEnvelopeFactory.failure("CODE", "bad", "details");

        assertEquals("failure", response.get("status"));
        assertEquals("bad", response.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) ((java.util.List<?>) response.get("errors")).get(0);
        assertEquals("CODE", error.get("code"));
        assertEquals("details", error.get("details"));
    }

    @Test
    void shouldConvertLegacySuccessFormat() {
        Map<String, Object> response = ApiEnvelopeFactory.fromSingleUploadResult(
            Map.of("success", true, "data", Map.of("fileId", "abc")),
            "uploaded"
        );

        assertEquals("success", response.get("status"));
        assertEquals("uploaded", response.get("message"));
    }

    @Test
    void shouldConvertLegacyFailureFormatWithoutDetails() {
        Map<String, Object> response = ApiEnvelopeFactory.fromSingleUploadResult(
            Map.of("success", false, "code", "INVALID", "message", "bad type"),
            "ignored"
        );

        assertEquals("failure", response.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) ((java.util.List<?>) response.get("errors")).get(0);
        assertEquals("INVALID", error.get("code"));
        assertFalse(error.containsKey("details"));
    }
}
