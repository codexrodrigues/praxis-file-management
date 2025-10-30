package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for tracking and persisting security failures to enable forensic analysis.
 * Maintains both in-memory statistics and persistent failure records.
 */
@Service
public class SecurityFailureRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SecurityFailureRegistry.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private FileManagementProperties properties;

    // In-memory failure tracking
    private final ConcurrentHashMap<String, SecurityFailureStats> failureStatsByIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<FileErrorReason, AtomicLong> failureCountsByReason = new ConcurrentHashMap<>();

    // Initialize counters for all error reasons
    {
        for (FileErrorReason reason : FileErrorReason.values()) {
            failureCountsByReason.put(reason, new AtomicLong(0));
        }
    }

    /**
     * Security failure statistics for a specific IP
     */
    public static class SecurityFailureStats {
        private final AtomicLong totalFailures = new AtomicLong(0);
        private final AtomicLong malwareDetections = new AtomicLong(0);
        private final AtomicLong pathTraversalAttempts = new AtomicLong(0);
        private final AtomicLong signatureMismatches = new AtomicLong(0);
        private final AtomicLong zipBombAttempts = new AtomicLong(0);
        private final AtomicLong dangerousExecutables = new AtomicLong(0);
        private final AtomicLong dangerousScripts = new AtomicLong(0);
        private final AtomicLong suspiciousStructures = new AtomicLong(0);
        private final AtomicLong lastFailureTime = new AtomicLong(System.currentTimeMillis());

        public long getTotalFailures() { return totalFailures.get(); }
        public long getMalwareDetections() { return malwareDetections.get(); }
        public long getPathTraversalAttempts() { return pathTraversalAttempts.get(); }
        public long getSignatureMismatches() { return signatureMismatches.get(); }
        public long getZipBombAttempts() { return zipBombAttempts.get(); }
        public long getDangerousExecutables() { return dangerousExecutables.get(); }
        public long getDangerousScripts() { return dangerousScripts.get(); }
        public long getSuspiciousStructures() { return suspiciousStructures.get(); }
        public long getLastFailureTime() { return lastFailureTime.get(); }

        public void incrementFailure(FileErrorReason reason) {
            totalFailures.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            switch (reason) {
                case MALWARE_DETECTED:
                    malwareDetections.incrementAndGet();
                    break;
                case PATH_TRAVERSAL:
                    pathTraversalAttempts.incrementAndGet();
                    break;
                case SIGNATURE_MISMATCH:
                    signatureMismatches.incrementAndGet();
                    break;
                case ZIP_BOMB_DETECTED:
                    zipBombAttempts.incrementAndGet();
                    break;
                case DANGEROUS_EXECUTABLE:
                    dangerousExecutables.incrementAndGet();
                    break;
                case DANGEROUS_SCRIPT:
                    dangerousScripts.incrementAndGet();
                    break;
                case SUSPICIOUS_STRUCTURE:
                case EMBEDDED_EXECUTABLE:
                    suspiciousStructures.incrementAndGet();
                    break;
            }
        }
    }

    /**
     * Persistent failure record for forensic analysis
     */
    public static class SecurityFailureRecord {
        private String timestamp;
        private String clientIp;
        private String filename;
        private Long fileSize;
        private String mimeType;
        private FileErrorReason errorReason;
        private String errorMessage;
        private String securityDetails;
        private String userAgent;
        private String sessionId;

        public SecurityFailureRecord() {
            this.timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER);
        }

        // Getters and setters
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public FileErrorReason getErrorReason() { return errorReason; }
        public void setErrorReason(FileErrorReason errorReason) { this.errorReason = errorReason; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getSecurityDetails() { return securityDetails; }
        public void setSecurityDetails(String securityDetails) { this.securityDetails = securityDetails; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    /**
     * Register a security failure
     */
    public void registerSecurityFailure(String clientIp, String filename, Long fileSize, 
                                       String mimeType, FileErrorReason errorReason, 
                                       String errorMessage, String securityDetails) {
        if (!isValidClientIp(clientIp)) {
            return;
        }

        // Update in-memory statistics
        SecurityFailureStats stats = failureStatsByIp.computeIfAbsent(clientIp, k -> new SecurityFailureStats());
        stats.incrementFailure(errorReason);

        // Update global counters
        failureCountsByReason.get(errorReason).incrementAndGet();

        // Create persistent record
        SecurityFailureRecord record = new SecurityFailureRecord();
        record.setClientIp(clientIp);
        record.setFilename(filename);
        record.setFileSize(fileSize);
        record.setMimeType(mimeType);
        record.setErrorReason(errorReason);
        record.setErrorMessage(errorMessage);
        record.setSecurityDetails(securityDetails);

        // Persist to file if enabled and log standardized message
        if (properties.getAuditLogging().isEnabled()) {
            persistFailureRecord(record);
            logger.info("SECURITY_FAILURE: ip={}, reason={}, filename={}",
                clientIp, errorReason, filename);
        }
    }

    /**
     * Get failure statistics for a specific IP
     */
    public SecurityFailureStats getFailureStats(String clientIp) {
        return failureStatsByIp.get(clientIp);
    }

    /**
     * Get total failure count for a specific error reason
     */
    public long getTotalFailureCount(FileErrorReason reason) {
        return failureCountsByReason.get(reason).get();
    }

    /**
     * Get all failure statistics by IP
     */
    public ConcurrentHashMap<String, SecurityFailureStats> getAllFailureStats() {
        return new ConcurrentHashMap<>(failureStatsByIp);
    }

    /**
     * Check if an IP has suspicious failure patterns
     */
    public boolean isSuspiciousIp(String clientIp) {
        SecurityFailureStats stats = failureStatsByIp.get(clientIp);
        if (stats == null) {
            return false;
        }

        // Define suspicious patterns
        boolean suspicious = false;

        // High number of total failures
        if (stats.getTotalFailures() >= 20) {
            suspicious = true;
        }

        // Multiple malware detections
        if (stats.getMalwareDetections() >= 3) {
            suspicious = true;
        }

        // Multiple path traversal attempts
        if (stats.getPathTraversalAttempts() >= 5) {
            suspicious = true;
        }

        // Multiple ZIP bomb attempts
        if (stats.getZipBombAttempts() >= 3) {
            suspicious = true;
        }

        // Multiple dangerous executable uploads
        if (stats.getDangerousExecutables() >= 5) {
            suspicious = true;
        }

        // Recent activity (within last hour) with high failure rate
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
        if (stats.getLastFailureTime() > oneHourAgo && stats.getTotalFailures() >= 10) {
            suspicious = true;
        }

        return suspicious;
    }

    /**
     * Get global failure summary
     */
    public String getGlobalFailureSummary() {
        long totalFailures = 0;
        long totalMalware = 0;
        long totalPathTraversal = 0;
        long totalZipBombs = 0;
        int suspiciousIps = 0;

        for (SecurityFailureStats stats : failureStatsByIp.values()) {
            totalFailures += stats.getTotalFailures();
            totalMalware += stats.getMalwareDetections();
            totalPathTraversal += stats.getPathTraversalAttempts();
            totalZipBombs += stats.getZipBombAttempts();
        }

        for (String ip : failureStatsByIp.keySet()) {
            if (isSuspiciousIp(ip)) {
                suspiciousIps++;
            }
        }

        return String.format(
            "Security Failures: %d total, %d malware, %d path traversal, %d ZIP bombs, %d suspicious IPs",
            totalFailures, totalMalware, totalPathTraversal, totalZipBombs, suspiciousIps
        );
    }

    /**
     * Persist failure record to file
     */
    private void persistFailureRecord(SecurityFailureRecord record) {
        try {
            Path logDir = Paths.get(properties.getUploadDir()).resolve("security-logs");
            Files.createDirectories(logDir);

            String filename = "security-failures-" + 
                Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + 
                ".log";
            
            Path logFile = logDir.resolve(filename);
            
            String recordJson = objectMapper.writeValueAsString(record);
            
            Files.write(logFile, 
                (recordJson + System.lineSeparator()).getBytes(), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND);
            
            logger.debug("Security failure record persisted to: {}", logFile);
            
        } catch (IOException e) {
            logger.error("Failed to persist security failure record", e);
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
     * Get the number of tracked IPs
     */
    public int getTrackedIpCount() {
        return failureStatsByIp.size();
    }

    /**
     * Clean up old statistics to prevent memory leaks
     */
    public void cleanupOldStats() {
        final long retentionMs = properties.getAuditLogging().getStatisticsRetentionHours() * 60 * 60 * 1000L;
        final long cutoffTime = System.currentTimeMillis() - retentionMs;
        final AtomicInteger removedCount = new AtomicInteger(0);

        failureStatsByIp.entrySet().removeIf(entry -> {
            if (entry.getValue().getLastFailureTime() < cutoffTime) {
                removedCount.incrementAndGet();
                return true;
            }
            return false;
        });

        int removed = removedCount.get();
        if (removed > 0) {
            logger.info("Cleaned up {} old security failure statistics", removed);
        }
    }
}