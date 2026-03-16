package br.com.praxis.filemanagement.web.error;

import br.com.praxis.filemanagement.core.exception.FileSizeLimitExceededException;
import br.com.praxis.filemanagement.core.exception.RateLimitExceededException;
import br.com.praxis.filemanagement.core.exception.QuotaExceededException;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.RateLimitingService;
import br.com.praxis.filemanagement.web.filter.RemoteIpResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GlobalExceptionHandlerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new FileErrorMetricsService(registry));

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        return mock(ObjectProvider.class, invocation -> {
            if ("getIfAvailable".equals(invocation.getMethod().getName())) {
                return value;
            }
            return null;
        });
    }

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
            assertEquals("failure", response.getBody().status());
            assertNotNull(response.getBody().errors());
            assertEquals("ARQUIVO_MUITO_GRANDE", response.getBody().errors().get(0).get("code"));
            assertFalse(response.getBody().errors().isEmpty());
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
        assertEquals("failure", response.getBody().status());
        assertEquals("Autenticação necessária", response.getBody().message());
        assertEquals("NAO_AUTORIZADO", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 429 for rate limit violations")
    void shouldReturnTooManyRequests() {
        RateLimitExceededException ex = new RateLimitExceededException("limit");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceeded(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("failure", response.getBody().status());
        assertEquals("Limite de taxa de upload excedido", response.getBody().message());
        assertEquals("LIMITE_TAXA_EXCEDIDO", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should include rate limit headers when collaborators are available")
    void shouldIncludeRateLimitHeaders() {
        SimpleMeterRegistry localRegistry = new SimpleMeterRegistry();
        FileErrorMetricsService metrics = new FileErrorMetricsService(localRegistry);
        RateLimitingService rateLimitingService = mock(RateLimitingService.class);
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setMaxUploadsPerMinute(7);
        RemoteIpResolver resolver = new RemoteIpResolver(properties);

        when(rateLimitingService.getRemainingUploadsPerMinute("203.0.113.5")).thenReturn(2L);

        GlobalExceptionHandler enriched = new GlobalExceptionHandler(
            provider(metrics),
            provider(rateLimitingService),
            provider(properties),
            provider(resolver)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5");
        properties.getRateLimit().setTrustedProxies(java.util.List.of("10.0.0.1"));

        ResponseEntity<ErrorResponse> response = enriched.handleRateLimitExceeded(new RateLimitExceededException("limit"), request);

        assertEquals("7", response.getHeaders().getFirst("X-RateLimit-Limit"));
        assertEquals("2", response.getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals("60", response.getHeaders().getFirst("X-RateLimit-Reset"));
    }

    @Test
    @DisplayName("Should return 429 for quota violations")
    void shouldReturnTooManyRequestsForQuota() {
        QuotaExceededException ex = new QuotaExceededException("tenant");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ErrorResponse> response = handler.handleQuotaExceeded(ex, request);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("failure", response.getBody().status());
        assertEquals("Cota de upload excedida", response.getBody().message());
        assertEquals("COTA_EXCEDIDA", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 400 for missing multipart part")
    void shouldHandleMissingPart() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestPart(
            new MissingServletRequestPartException("file"),
            new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CAMPO_OBRIGATORIO_AUSENTE", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 413 for max upload size exceeded")
    void shouldHandleMaxUploadSizeExceeded() {
        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSizeExceeded(
            new MaxUploadSizeExceededException(123L),
            new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals("ARQUIVO_MUITO_GRANDE", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 400 for invalid JSON options")
    void shouldHandleJsonProcessingException() {
        JsonProcessingException exception = new JsonProcessingException("bad json") {};

        ResponseEntity<ErrorResponse> response = handler.handleJsonProcessingException(exception, new MockHttpServletRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("OPCOES_JSON_INVALIDAS", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 400 for illegal arguments")
    void shouldHandleIllegalArgumentException() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(
            new IllegalArgumentException("invalid"),
            new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ARGUMENTO_INVALIDO", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 415 for unsupported content type")
    void shouldHandleUnsupportedMediaType() {
        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedMediaType(
            new HttpMediaTypeNotSupportedException("text/csv"),
            new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals("TIPO_MIDIA_NAO_SUPORTADO", response.getBody().errors().get(0).get("code"));
    }

    @Test
    @DisplayName("Should return 500 for unexpected exceptions")
    void shouldHandleGeneralException() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneralException(
            new RuntimeException("boom"),
            new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("ERRO_INTERNO", response.getBody().errors().get(0).get("code"));
    }
}
