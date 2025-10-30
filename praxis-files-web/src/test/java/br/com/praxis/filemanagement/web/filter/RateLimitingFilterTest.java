package br.com.praxis.filemanagement.web.filter;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.RateLimitingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import br.com.praxis.filemanagement.core.exception.RateLimitExceededException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class RateLimitingFilterTest {

    private final RateLimitingService rateLimitingService = Mockito.mock(RateLimitingService.class);
    private final FileManagementProperties properties = new FileManagementProperties();
    private final RemoteIpResolver resolver = new RemoteIpResolver(properties);
    private final RateLimitingFilter filter = new RateLimitingFilter(rateLimitingService, properties, resolver);

    @Test
    @DisplayName("Should allow request when under limit")
    void shouldAllowRequest() throws Exception {
        properties.getRateLimit().setEnabled(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitingService.tryAcquireUploadPermit(anyString())).thenReturn(true);

        FilterChain chain = (ServletRequest req, ServletResponse res) ->
            ((HttpServletResponse) res).setStatus(HttpServletResponse.SC_OK);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(rateLimitingService).releaseUploadPermit(anyString());
    }

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void shouldReturnTooManyRequests() throws Exception {
        properties.getRateLimit().setEnabled(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/files/upload");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitingService.tryAcquireUploadPermit(anyString())).thenReturn(false);

        FilterChain chain = (req, res) -> fail("Chain should not be invoked");

        assertThrows(RateLimitExceededException.class, () -> filter.doFilter(request, response, chain));
        verify(rateLimitingService, never()).releaseUploadPermit(anyString());
    }

    @Test
    @DisplayName("Should resolve real client IP behind trusted proxy")
    void shouldResolveRealIpBehindProxy() throws Exception {
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setTrustedProxies(java.util.List.of("10.0.0.1"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/files/upload");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitingService.tryAcquireUploadPermit("203.0.113.5")).thenReturn(false);

        FilterChain chain = (req, res) -> fail("Chain should not be invoked");

        assertThrows(RateLimitExceededException.class, () -> filter.doFilter(request, response, chain));
        verify(rateLimitingService).tryAcquireUploadPermit("203.0.113.5");
    }
}
