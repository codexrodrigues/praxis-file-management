package br.com.praxis.filemanagement.web.filter;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the real client IP address when requests pass through trusted proxies.
 */
@Component
public class RemoteIpResolver {

    private final FileManagementProperties properties;

    public RemoteIpResolver(FileManagementProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolve client IP using X-Forwarded-For/X-Real-IP when the remote address
     * is a trusted proxy.
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        List<String> trusted = properties.getRateLimit().getTrustedProxies();
        if (trusted.contains(remoteAddr)) {
            String header = request.getHeader("X-Forwarded-For");
            if (header != null && !header.isBlank()) {
                return header.split(",")[0].trim();
            }
            header = request.getHeader("X-Real-IP");
            if (header != null && !header.isBlank()) {
                return header.trim();
            }
        }
        return remoteAddr;
    }
}
