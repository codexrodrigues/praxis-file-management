package br.com.praxis.filemanagement.web.filter;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.RateLimitingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import br.com.praxis.filemanagement.core.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de rate limiting que aplica limites de upload por endereço IP
 * antes de qualquer processamento do controller.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimitingService rateLimitingService;
    private final FileManagementProperties properties;
    private final RemoteIpResolver remoteIpResolver;

    public RateLimitingFilter(RateLimitingService rateLimitingService,
                              FileManagementProperties properties,
                              RemoteIpResolver remoteIpResolver) {
        this.rateLimitingService = rateLimitingService;
        this.properties = properties;
        this.remoteIpResolver = remoteIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return !properties.getRateLimit().isEnabled() ||
               !request.getRequestURI().startsWith("/api/files");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ipAddress = remoteIpResolver.resolve(request);
        if (!rateLimitingService.tryAcquireUploadPermit(ipAddress)) {
            logger.warn("Rate limit exceeded for IP {}", ipAddress);
            throw new RateLimitExceededException("Upload rate limit exceeded");
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            rateLimitingService.releaseUploadPermit(ipAddress);
        }
    }
}

