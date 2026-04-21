package br.com.praxis.filemanagement.web.error;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileErrorMetricsServiceTest {

    @Test
    void shouldIncrementCountersUsingFallbackTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FileErrorMetricsService service = new FileErrorMetricsService(registry);

        service.increment(null, null);
        service.increment(FileErrorReason.FILE_TOO_LARGE, "/api/files/upload");
        service.increment(FileErrorReason.FILE_TOO_LARGE, "/api/files/upload");

        assertEquals(1.0, registry.get("file_errors_total")
            .tag("reason", "UNKNOWN_ERROR")
            .tag("endpoint", "unknown")
            .counter().count());

        assertEquals(2.0, registry.get("file_errors_total")
            .tag("reason", "FILE_TOO_LARGE")
            .tag("endpoint", "/api/files/upload")
            .counter().count());
    }

    @Test
    void shouldBuildErrorResponseViaFactoryMethod() {
        ErrorResponse response = ErrorResponse.of("CODE", "message", "details");

        assertEquals("failure", response.status());
        assertEquals("CODE", response.errors().get(0).get("code"));
        assertEquals("details", response.errors().get(0).get("details"));
    }
}
