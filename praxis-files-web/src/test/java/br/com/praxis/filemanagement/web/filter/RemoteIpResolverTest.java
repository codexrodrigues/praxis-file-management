package br.com.praxis.filemanagement.web.filter;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteIpResolverTest {

    @Test
    void shouldUseForwardedHeaderWhenProxyIsTrusted() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setTrustedProxies(List.of("10.0.0.1"));
        RemoteIpResolver resolver = new RemoteIpResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        assertEquals("203.0.113.7", resolver.resolve(request));
    }

    @Test
    void shouldUseRealIpHeaderWhenTrustedProxyHasNoForwardedFor() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setTrustedProxies(List.of("10.0.0.1"));
        RemoteIpResolver resolver = new RemoteIpResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.4");

        assertEquals("198.51.100.4", resolver.resolve(request));
    }

    @Test
    void shouldKeepRemoteAddressWhenProxyIsNotTrusted() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setTrustedProxies(List.of("10.0.0.1"));
        RemoteIpResolver resolver = new RemoteIpResolver(properties);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "203.0.113.7");

        assertEquals("192.0.2.10", resolver.resolve(request));
    }
}
