package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitingServiceTest {

    @Test
    @DisplayName("returns permissive defaults when rate limit is disabled")
    void returnsPermissiveDefaultsWhenDisabled() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setEnabled(false);
        RateLimitingService service = new RateLimitingService(properties);

        assertTrue(service.tryAcquireUploadPermit("127.0.0.1"));
        service.releaseUploadPermit("127.0.0.1");
        assertEquals(Long.MAX_VALUE, service.getRemainingUploadsPerMinute("127.0.0.1"));
        assertEquals(0, service.getActiveBucketCount());
        assertEquals(0, service.getCurrentConcurrentUploads("127.0.0.1"));
    }

    @Test
    @DisplayName("enforces concurrent upload limit without consuming extra tokens")
    void enforcesConcurrentUploadLimitWithoutConsumingExtraTokens() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxUploadsPerMinute(5);
        properties.getRateLimit().setMaxUploadsPerHour(10);
        properties.getRateLimit().setMaxConcurrentUploads(1);

        RateLimitingService service = new RateLimitingService(properties);

        assertTrue(service.tryAcquireUploadPermit("10.0.0.1"));
        assertEquals(1, service.getCurrentConcurrentUploads("10.0.0.1"));
        assertEquals(4, service.getRemainingUploadsPerMinute("10.0.0.1"));

        assertFalse(service.tryAcquireUploadPermit("10.0.0.1"));
        assertEquals(4, service.getRemainingUploadsPerMinute("10.0.0.1"));

        service.releaseUploadPermit("10.0.0.1");
        assertEquals(0, service.getCurrentConcurrentUploads("10.0.0.1"));
        assertTrue(service.tryAcquireUploadPermit("10.0.0.1"));
    }

    @Test
    @DisplayName("enforces minute rate limit and allows reset")
    void enforcesMinuteRateLimitAndAllowsReset() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxUploadsPerMinute(1);
        properties.getRateLimit().setMaxUploadsPerHour(10);
        properties.getRateLimit().setMaxConcurrentUploads(2);

        RateLimitingService service = new RateLimitingService(properties);

        assertTrue(service.tryAcquireUploadPermit("10.0.0.2"));
        service.releaseUploadPermit("10.0.0.2");
        assertFalse(service.tryAcquireUploadPermit("10.0.0.2"));
        assertEquals(0, service.getRemainingUploadsPerMinute("10.0.0.2"));

        service.resetRateLimits("10.0.0.2");
        assertEquals(0, service.getActiveBucketCount());
        assertTrue(service.tryAcquireUploadPermit("10.0.0.2"));
    }

    @Test
    @DisplayName("cleanup removes expired buckets and concurrent counters")
    void cleanupRemovesExpiredBucketsAndConcurrentCounters() throws Exception {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxUploadsPerMinute(2);
        properties.getRateLimit().setMaxUploadsPerHour(10);
        properties.getRateLimit().setMaxConcurrentUploads(2);

        RateLimitingService service = new RateLimitingService(properties);
        assertTrue(service.tryAcquireUploadPermit("10.0.0.3"));

        Field lastAccessTimeField = RateLimitingService.class.getDeclaredField("lastAccessTime");
        lastAccessTimeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Instant> lastAccessTime = (Map<String, Instant>) lastAccessTimeField.get(service);
        lastAccessTime.put("10.0.0.3", Instant.now().minusSeconds(25 * 60 * 60));

        service.cleanupExpiredBuckets();

        assertEquals(0, service.getActiveBucketCount());
        assertEquals(0, service.getCurrentConcurrentUploads("10.0.0.3"));
    }

    @Test
    @DisplayName("cleanup on destroy clears all runtime state")
    void cleanupOnDestroyClearsAllRuntimeState() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxUploadsPerMinute(2);
        properties.getRateLimit().setMaxUploadsPerHour(10);
        properties.getRateLimit().setMaxConcurrentUploads(2);

        RateLimitingService service = new RateLimitingService(properties);
        assertTrue(service.tryAcquireUploadPermit("10.0.0.4"));
        assertEquals(1, service.getActiveBucketCount());

        service.cleanup();

        assertEquals(0, service.getActiveBucketCount());
        assertEquals(0, service.getCurrentConcurrentUploads("10.0.0.4"));
    }
}
