package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking upload attempts and detecting suspicious patterns.
 * Provides real-time monitoring of upload behavior per client IP.
 */
@Service
public class UploadAttemptTracker {

    private static final Logger logger = LoggerFactory.getLogger(UploadAttemptTracker.class);

    @Autowired
    private FileManagementProperties properties;

    @Autowired
    private SecurityAuditLogger securityAuditLogger;

    // Track upload attempts per IP
    private final ConcurrentHashMap<String, ClientUploadStats> clientStats = new ConcurrentHashMap<>();

    // Dynamic cleanup management
    private volatile long lastCleanupTime = System.currentTimeMillis();

    /**
     * Statistics for a specific client IP
     */
    public static class ClientUploadStats {
        private final AtomicLong totalAttempts = new AtomicLong(0);
        private final AtomicLong successfulUploads = new AtomicLong(0);
        private final AtomicLong failedUploads = new AtomicLong(0);
        private final AtomicLong securityViolations = new AtomicLong(0);
        private final AtomicLong malwareDetections = new AtomicLong(0);
        private final AtomicLong rateLimitExceeded = new AtomicLong(0);
        private final AtomicLong lastAttemptTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong firstAttemptTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private final AtomicLong totalBytesAttempted = new AtomicLong(0);
        private final AtomicLong totalBytesUploaded = new AtomicLong(0);

        public long getTotalAttempts() { return totalAttempts.get(); }
        public long getSuccessfulUploads() { return successfulUploads.get(); }
        public long getFailedUploads() { return failedUploads.get(); }
        public long getSecurityViolations() { return securityViolations.get(); }
        public long getMalwareDetections() { return malwareDetections.get(); }
        public long getRateLimitExceeded() { return rateLimitExceeded.get(); }
        public long getLastAttemptTime() { return lastAttemptTime.get(); }
        public long getFirstAttemptTime() { return firstAttemptTime.get(); }
        public int getConsecutiveFailures() { return consecutiveFailures.get(); }
        public long getTotalBytesAttempted() { return totalBytesAttempted.get(); }
        public long getTotalBytesUploaded() { return totalBytesUploaded.get(); }

        public double getFailureRate() {
            long total = totalAttempts.get();
            return total > 0 ? (double) failedUploads.get() / total : 0.0;
        }

        public double getSecurityViolationRate() {
            long total = totalAttempts.get();
            return total > 0 ? (double) securityViolations.get() / total : 0.0;
        }

        public long getSessionDurationMinutes() {
            return ChronoUnit.MINUTES.between(
                Instant.ofEpochMilli(firstAttemptTime.get()),
                Instant.ofEpochMilli(lastAttemptTime.get())
            );
        }
    }

    /**
     * Record an upload attempt
     */
    public void recordUploadAttempt(String clientIp, String filename, long fileSize) {
        if (!isValidClientIp(clientIp)) {
            return;
        }

        ClientUploadStats stats = clientStats.computeIfAbsent(clientIp, k -> new ClientUploadStats());
        stats.totalAttempts.incrementAndGet();
        stats.lastAttemptTime.set(System.currentTimeMillis());
        stats.totalBytesAttempted.addAndGet(fileSize);

        logger.debug("Recorded upload attempt from IP: {}, filename: {}, size: {}", clientIp, filename, fileSize);
        
        // Trigger cleanup if needed
        cleanupIfNeeded();
    }

    /**
     * Record a successful upload
     */
    public void recordSuccessfulUpload(String clientIp, String filename, long fileSize) {
        if (!isValidClientIp(clientIp)) {
            return;
        }

        ClientUploadStats stats = clientStats.get(clientIp);
        if (stats != null) {
            stats.successfulUploads.incrementAndGet();
            stats.totalBytesUploaded.addAndGet(fileSize);
            stats.consecutiveFailures.set(0); // Reset consecutive failures
            
            logger.debug("Recorded successful upload from IP: {}, filename: {}, size: {}", clientIp, filename, fileSize);
        }
    }

    /**
     * Record a failed upload
     */
    public void recordFailedUpload(String clientIp, String filename, FileErrorReason errorReason) {
        if (!isValidClientIp(clientIp)) {
            return;
        }

        ClientUploadStats stats = clientStats.get(clientIp);
        if (stats != null) {
            stats.failedUploads.incrementAndGet();
            stats.consecutiveFailures.incrementAndGet();
            
            // Count specific types of failures
            if (isSecurityViolation(errorReason)) {
                stats.securityViolations.incrementAndGet();
            }
            
            if (errorReason == FileErrorReason.MALWARE_DETECTED) {
                stats.malwareDetections.incrementAndGet();
            }
            
            logger.debug("Recorded failed upload from IP: {}, filename: {}, reason: {}", clientIp, filename, errorReason);
            
            // Check for suspicious patterns
            checkSuspiciousActivity(clientIp, stats);
        }
    }

    /**
     * Record a rate limit exceeded event
     */
    public void recordRateLimitExceeded(String clientIp) {
        if (!isValidClientIp(clientIp)) {
            return;
        }

        ClientUploadStats stats = clientStats.get(clientIp);
        if (stats != null) {
            stats.rateLimitExceeded.incrementAndGet();
            logger.debug("Recorded rate limit exceeded from IP: {}", clientIp);
        }
    }

    /**
     * Validate if client IP is trackable
     */
    private boolean isValidClientIp(String clientIp) {
        return clientIp != null && 
               !clientIp.trim().isEmpty() && 
               !"unknown".equalsIgnoreCase(clientIp.trim()) &&
               !"localhost".equals(clientIp) &&
               !"127.0.0.1".equals(clientIp) &&
               !"0:0:0:0:0:0:0:1".equals(clientIp);
    }

    /**
     * Get statistics for a specific client IP
     */
    public ClientUploadStats getClientStats(String clientIp) {
        return clientStats.get(clientIp);
    }

    /**
     * Check if an error reason represents a security violation
     */
    private boolean isSecurityViolation(FileErrorReason errorReason) {
        switch (errorReason) {
            case MALWARE_DETECTED:
            case DANGEROUS_EXECUTABLE:
            case DANGEROUS_SCRIPT:
            case SIGNATURE_MISMATCH:
            case ZIP_BOMB_DETECTED:
            case PATH_TRAVERSAL:
            case SUSPICIOUS_STRUCTURE:
            case EMBEDDED_EXECUTABLE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Check for suspicious activity patterns
     */
    private void checkSuspiciousActivity(String clientIp, ClientUploadStats stats) {
        boolean suspicious = false;
        String suspiciousReason = null;

        // Check consecutive failures
        if (stats.getConsecutiveFailures() >= 10) {
            suspicious = true;
            suspiciousReason = "High consecutive failures: " + stats.getConsecutiveFailures();
        }

        // Check security violation rate
        if (stats.getTotalAttempts() >= 5 && stats.getSecurityViolationRate() > 0.5) {
            suspicious = true;
            suspiciousReason = "High security violation rate: " + String.format("%.2f", stats.getSecurityViolationRate() * 100) + "%";
        }

        // Check malware detection rate
        if (stats.getMalwareDetections() >= 3) {
            suspicious = true;
            suspiciousReason = "Multiple malware detections: " + stats.getMalwareDetections();
        }

        // Check rapid-fire attempts
        if (stats.getTotalAttempts() >= 50 && stats.getSessionDurationMinutes() < 5) {
            suspicious = true;
            suspiciousReason = "Rapid-fire attempts: " + stats.getTotalAttempts() + " attempts in " + stats.getSessionDurationMinutes() + " minutes";
        }

        // Check high failure rate with many attempts
        if (stats.getTotalAttempts() >= 20 && stats.getFailureRate() > 0.8) {
            suspicious = true;
            suspiciousReason = "High failure rate: " + String.format("%.2f", stats.getFailureRate() * 100) + "% with " + stats.getTotalAttempts() + " attempts";
        }

        if (suspicious) {
            logger.warn("Suspicious activity detected from IP: {} - {}", clientIp, suspiciousReason);
            
            // Log suspicious activity
            if (properties.getAuditLogging().isEnabled()) {
                SecurityAuditLogger.SecurityAuditEvent event = SecurityAuditLogger.createEvent(
                    SecurityAuditLogger.SecurityEventType.SUSPICIOUS_CONTENT, 
                    SecurityAuditLogger.SecuritySeverity.HIGH)
                    .clientIp(clientIp)
                    .securityDetails(suspiciousReason)
                    .additionalData("total_attempts", stats.getTotalAttempts())
                    .additionalData("failure_rate", stats.getFailureRate())
                    .additionalData("security_violations", stats.getSecurityViolations())
                    .additionalData("consecutive_failures", stats.getConsecutiveFailures())
                    .additionalData("session_duration_minutes", stats.getSessionDurationMinutes())
                    .build();
                
                securityAuditLogger.logSecurityEvent(event);
            }
        }
    }

    /**
     * Cleanup old statistics to prevent memory leaks
     */
    private void cleanupIfNeeded() {
        long currentTime = System.currentTimeMillis();
        int cleanupIntervalMs = properties.getAuditLogging().getCleanupIntervalMinutes() * 60 * 1000;
        
        if (currentTime - lastCleanupTime > cleanupIntervalMs) {
            cleanupOldStats();
            lastCleanupTime = currentTime;
        }
    }

    /**
     * Remove statistics for IPs that haven't been active for configured retention period
     */
    private void cleanupOldStats() {
        final long retentionMs = properties.getAuditLogging().getStatisticsRetentionHours() * 60 * 60 * 1000L;
        final long cutoffTime = System.currentTimeMillis() - retentionMs;
        final AtomicInteger removedCount = new AtomicInteger(0);

        clientStats.entrySet().removeIf(entry -> {
            if (entry.getValue().getLastAttemptTime() < cutoffTime) {
                removedCount.incrementAndGet();
                return true;
            }
            return false;
        });

        int removed = removedCount.get();
        if (removed > 0) {
            logger.info("Cleaned up {} old client statistics entries", removed);
        }
    }

    /**
     * Get total number of tracked clients
     */
    public int getTrackedClientCount() {
        return clientStats.size();
    }

    /**
     * Get summary statistics for all clients
     */
    public String getGlobalSummary() {
        long totalAttempts = 0;
        long totalSuccessful = 0;
        long totalFailed = 0;
        long totalSecurityViolations = 0;
        long totalMalwareDetections = 0;

        for (ClientUploadStats stats : clientStats.values()) {
            totalAttempts += stats.getTotalAttempts();
            totalSuccessful += stats.getSuccessfulUploads();
            totalFailed += stats.getFailedUploads();
            totalSecurityViolations += stats.getSecurityViolations();
            totalMalwareDetections += stats.getMalwareDetections();
        }

        return String.format(
            "Global Upload Statistics: %d clients tracked, %d total attempts, %d successful, %d failed, %d security violations, %d malware detections",
            clientStats.size(), totalAttempts, totalSuccessful, totalFailed, totalSecurityViolations, totalMalwareDetections
        );
    }
}