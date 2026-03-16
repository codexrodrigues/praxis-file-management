package br.com.praxis.filemanagement.web.filter;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.RateLimitingService;
import br.com.praxis.filemanagement.core.utils.ErrorMessageUtils;
import br.com.praxis.filemanagement.web.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Filtro de rate limiting que aplica limites de upload por endereço IP
 * antes de qualquer processamento do controller.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }

        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/files")) {
            return true;
        }

        return HttpMethod.GET.matches(request.getMethod()) &&
            ("/api/files/config".equals(requestUri) || "/api/files/config/".equals(requestUri));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ipAddress = remoteIpResolver.resolve(request);
        boolean permitAcquired = rateLimitingService.tryAcquireUploadPermit(ipAddress);
        if (!permitAcquired) {
            logger.warn("Rate limit exceeded for IP {}", ipAddress);
            writeRateLimitExceededResponse(response, ipAddress);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            rateLimitingService.releaseUploadPermit(ipAddress);
        }
    }

    private void writeRateLimitExceededResponse(HttpServletResponse response, String ipAddress) throws IOException {
        Map<String, Object> map = ErrorMessageUtils.createGenericErrorResponseByKey("RATE_LIMIT_EXCEEDED", null, null);
        ErrorResponse errorResponse = ErrorResponse.fromMap(map);

        long remaining = Math.max(0, rateLimitingService.getRemainingUploadsPerMinute(ipAddress));
        int limit = properties.getRateLimit().getMaxUploadsPerMinute();
        long reset = 60;

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(reset));
        OBJECT_MAPPER.writeValue(response.getWriter(), errorResponse);
    }
}

