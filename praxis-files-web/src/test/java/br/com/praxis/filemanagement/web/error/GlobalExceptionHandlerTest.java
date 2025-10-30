package br.com.praxis.filemanagement.web.error;

import br.com.praxis.filemanagement.core.exception.FileSizeLimitExceededException;
import br.com.praxis.filemanagement.core.exception.RateLimitExceededException;
import br.com.praxis.filemanagement.core.exception.QuotaExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalExceptionHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new FileErrorMetricsService(registry));

    @Test
    @DisplayName("Should return 413 when FileSizeLimitExceededException is handled")
    void shouldReturnPayloadTooLarge() {
        FileSizeLimitExceededException ex = new FileSizeLimitExceededException(200L, 100L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/upload");
        MDC.put("traceId", "trace-123");
        try {
            ResponseEntity<ErrorResponse> response = handler.handleFileSizeLimitExceeded(ex, request);

            assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ARQUIVO_MUITO_GRANDE", response.getBody().code());
            assertEquals("trace-123", response.getBody().traceId());
            assertEquals(1.0,
                    registry.get("file_errors_total")
                            .tag("reason", "FILE_TOO_LARGE")
                            .tag("endpoint", "/upload")
                            .counter().count(),
                    0.001);
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("Should return 401 for authentication errors")
    void shouldReturnUnauthorized() {
        BadCredentialsException ex = new BadCredentialsException("bad creds");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("NAO_AUTORIZADO", response.getBody().code());
    }

    @Test
    @DisplayName("Should return 429 for rate limit violations")
    void shouldReturnTooManyRequests() {
        RateLimitExceededException ex = new RateLimitExceededException("limit");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceeded(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("LIMITE_TAXA_EXCEDIDO", response.getBody().code());
    }

    @Test
    @DisplayName("Should return 429 for quota violations")
    void shouldReturnTooManyRequestsForQuota() {
        QuotaExceededException ex = new QuotaExceededException("tenant");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ErrorResponse> response = handler.handleQuotaExceeded(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("COTA_EXCEDIDA", response.getBody().code());
    }
}
