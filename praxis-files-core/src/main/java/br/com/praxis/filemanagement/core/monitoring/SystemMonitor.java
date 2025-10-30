package br.com.praxis.filemanagement.core.monitoring;

import br.com.praxis.filemanagement.core.audit.SecurityFailureRegistry;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.VirusScanningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * System monitoring service for file management components.
 * Provides health status and metrics collection without requiring Spring Boot Actuator.
 */
@Service
public class SystemMonitor {

    private static final Logger logger = LoggerFactory.getLogger(SystemMonitor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Cache for health checks to avoid expensive operations on every request
    private final AtomicReference<SystemHealth> cachedHealth = new AtomicReference<>();
    private final AtomicLong lastHealthCheckTime = new AtomicLong(0);
    private final Map<String, Object> cachedMetrics = new ConcurrentHashMap<>();

    @Autowired
    private FileManagementProperties properties;

    @Autowired(required = false)
    private VirusScanningService virusScanningService;

    @Autowired
    private UploadAttemptTracker uploadAttemptTracker;

    @Autowired
    private SecurityFailureRegistry securityFailureRegistry;

    /**
     * System status levels
     */
    public enum SystemStatus {
        HEALTHY,     // All systems operating normally
        WARNING,     // Minor issues detected
        DEGRADED,    // Significant issues affecting functionality
        CRITICAL     // Critical issues requiring immediate attention
    }

    /**
     * System health information
     */
    public static class SystemHealth {
        private SystemStatus status;
        private String message;
        private Map<String, Object> components;
        private Map<String, Object> metrics;
        private String timestamp;

        public SystemHealth() {
            this.timestamp = Instant.now().toString();
            this.components = new HashMap<>();
            this.metrics = new HashMap<>();
        }

        // Getters and setters
        public SystemStatus getStatus() { return status; }
        public void setStatus(SystemStatus status) { this.status = status; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Map<String, Object> getComponents() { return components; }
        public void setComponents(Map<String, Object> components) { this.components = components; }

        public Map<String, Object> getMetrics() { return metrics; }
        public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Get comprehensive system health status
     */
    public SystemHealth getSystemHealth() {
        // Check if we can use cached result
        long currentTime = System.currentTimeMillis();
        long cacheExpirationTime = lastHealthCheckTime.get() + (properties.getMonitoring().getHealthCheckCacheSeconds() * 1000L);
        
        if (currentTime < cacheExpirationTime) {
            SystemHealth cached = cachedHealth.get();
            if (cached != null) {
                return cached;
            }
        }

        // Perform health check and cache result
        SystemHealth health = performHealthCheck();
        cachedHealth.set(health);
        lastHealthCheckTime.set(currentTime);
        
        return health;
    }

    /**
     * Perform actual health check without caching
     */
    private SystemHealth performHealthCheck() {
        SystemHealth health = new SystemHealth();
        SystemStatus worstStatus = SystemStatus.HEALTHY;

        try {
            // Check upload directory
            SystemStatus uploadDirStatus = checkUploadDirectory(health);
            worstStatus = getWorstStatus(worstStatus, uploadDirStatus);

            // Check virus scanning
            SystemStatus virusStatus = checkVirusScanning(health);
            worstStatus = getWorstStatus(worstStatus, virusStatus);

            // Check audit system
            SystemStatus auditStatus = checkAuditSystem(health);
            worstStatus = getWorstStatus(worstStatus, auditStatus);

            // Check security monitoring
            SystemStatus securityStatus = checkSecurityMonitoring(health);
            worstStatus = getWorstStatus(worstStatus, securityStatus);

            // Add metrics
            addSystemMetrics(health);

            // Set overall status
            health.setStatus(worstStatus);
            health.setMessage(getStatusMessage(worstStatus));

        } catch (Exception e) {
            logger.error("Error checking system health", e);
            health.setStatus(SystemStatus.CRITICAL);
            health.setMessage("System health check failed: " + e.getMessage());
        }

        return health;
    }

    /**
     * Check upload directory health
     */
    private SystemStatus checkUploadDirectory(SystemHealth health) {
        Map<String, Object> uploadDirInfo = new HashMap<>();
        
        try {
            Path uploadPath = Paths.get(properties.getUploadDir()).toAbsolutePath();
            
            if (!Files.exists(uploadPath)) {
                uploadDirInfo.put("status", "CRITICAL");
                uploadDirInfo.put("error", "Directory does not exist: " + uploadPath);
                health.getComponents().put("uploadDirectory", uploadDirInfo);
                return SystemStatus.CRITICAL;
            }

            if (!Files.isDirectory(uploadPath)) {
                uploadDirInfo.put("status", "CRITICAL");
                uploadDirInfo.put("error", "Path is not a directory: " + uploadPath);
                health.getComponents().put("uploadDirectory", uploadDirInfo);
                return SystemStatus.CRITICAL;
            }

            if (!Files.isWritable(uploadPath)) {
                uploadDirInfo.put("status", "CRITICAL");
                uploadDirInfo.put("error", "Directory is not writable: " + uploadPath);
                health.getComponents().put("uploadDirectory", uploadDirInfo);
                return SystemStatus.CRITICAL;
            }

            // Check available space
            long freeSpace = Files.getFileStore(uploadPath).getUsableSpace();
            long totalSpace = Files.getFileStore(uploadPath).getTotalSpace();
            double freeSpacePercent = (double) freeSpace / totalSpace * 100;

            uploadDirInfo.put("status", "HEALTHY");
            uploadDirInfo.put("path", uploadPath.toString());
            uploadDirInfo.put("freeSpaceGB", String.format("%.2f", freeSpace / (1024.0 * 1024.0 * 1024.0)));
            uploadDirInfo.put("freeSpacePercent", String.format("%.1f%%", freeSpacePercent));

            // Warn if less than 10% free space
            if (freeSpacePercent < 10) {
                uploadDirInfo.put("status", "WARNING");
                uploadDirInfo.put("warning", "Low disk space: " + String.format("%.1f%%", freeSpacePercent));
                health.getComponents().put("uploadDirectory", uploadDirInfo);
                return SystemStatus.WARNING;
            }

            health.getComponents().put("uploadDirectory", uploadDirInfo);
            return SystemStatus.HEALTHY;

        } catch (IOException e) {
            uploadDirInfo.put("status", "CRITICAL");
            uploadDirInfo.put("error", "IO error: " + e.getMessage());
            health.getComponents().put("uploadDirectory", uploadDirInfo);
            return SystemStatus.CRITICAL;
        }
    }

    /**
     * Check virus scanning health
     */
    private SystemStatus checkVirusScanning(SystemHealth health) {
        Map<String, Object> virusInfo = new HashMap<>();

        if (!properties.getVirusScanning().isEnabled()) {
            virusInfo.put("status", "DISABLED");
            virusInfo.put("message", "Virus scanning is disabled");
            health.getComponents().put("virusScanning", virusInfo);
            return SystemStatus.HEALTHY;
        }

        if (virusScanningService == null) {
            virusInfo.put("status", "CRITICAL");
            virusInfo.put("error", "VirusScanningService not available");
            health.getComponents().put("virusScanning", virusInfo);
            return SystemStatus.CRITICAL;
        }

        try {
            boolean isAvailable = virusScanningService.isVirusScanningAvailable();
            
            if (isAvailable) {
                virusInfo.put("status", "HEALTHY");
                virusInfo.put("host", properties.getVirusScanning().getClamavHost());
                virusInfo.put("port", properties.getVirusScanning().getClamavPort());
            } else {
                if (properties.getVirusScanning().isFailOnScannerUnavailable()) {
                    virusInfo.put("status", "CRITICAL");
                    health.getComponents().put("virusScanning", virusInfo);
                    return SystemStatus.CRITICAL;
                } else {
                    virusInfo.put("status", "WARNING");
                    virusInfo.put("message", "ClamAV daemon is not available");
                    health.getComponents().put("virusScanning", virusInfo);
                    return SystemStatus.WARNING;
                }
            }

        } catch (Exception e) {
            virusInfo.put("status", "CRITICAL");
            virusInfo.put("error", "Health check failed: " + e.getMessage());
            health.getComponents().put("virusScanning", virusInfo);
            return SystemStatus.CRITICAL;
        }

        health.getComponents().put("virusScanning", virusInfo);
        return SystemStatus.HEALTHY;
    }

    /**
     * Check audit system health
     */
    private SystemStatus checkAuditSystem(SystemHealth health) {
        Map<String, Object> auditInfo = new HashMap<>();

        if (!properties.getAuditLogging().isEnabled()) {
            auditInfo.put("status", "DISABLED");
            auditInfo.put("message", "Audit logging is disabled");
            health.getComponents().put("auditSystem", auditInfo);
            return SystemStatus.HEALTHY;
        }

        try {
            // Check if audit log directory is accessible
            Path auditLogDir = Paths.get(properties.getUploadDir()).resolve("security-logs");
            
            if (Files.exists(auditLogDir)) {
                if (!Files.isDirectory(auditLogDir) || !Files.isWritable(auditLogDir)) {
                    auditInfo.put("status", "WARNING");
                    auditInfo.put("warning", "Audit log directory is not accessible");
                    health.getComponents().put("auditSystem", auditInfo);
                    return SystemStatus.WARNING;
                }
            }

            auditInfo.put("status", "HEALTHY");
            auditInfo.put("structuredLogging", properties.getAuditLogging().isUseStructuredLogging());
            auditInfo.put("logSuccessfulUploads", properties.getAuditLogging().isLogSuccessfulUploads());
            auditInfo.put("minimumSeverity", properties.getAuditLogging().getMinimumSeverity());

        } catch (Exception e) {
            auditInfo.put("status", "CRITICAL");
            auditInfo.put("error", "Audit system check failed: " + e.getMessage());
            health.getComponents().put("auditSystem", auditInfo);
            return SystemStatus.CRITICAL;
        }

        health.getComponents().put("auditSystem", auditInfo);
        return SystemStatus.HEALTHY;
    }

    /**
     * Check security monitoring health
     */
    private SystemStatus checkSecurityMonitoring(SystemHealth health) {
        Map<String, Object> securityInfo = new HashMap<>();

        try {
            // Check upload tracker
            int trackedClients = uploadAttemptTracker.getTrackedClientCount();
            securityInfo.put("uploadTracker.trackedClients", trackedClients);
            securityInfo.put("uploadTracker.summary", uploadAttemptTracker.getGlobalSummary());

            // Check failure registry
            int trackedFailureIps = securityFailureRegistry.getTrackedIpCount();
            securityInfo.put("failureRegistry.trackedIps", trackedFailureIps);
            securityInfo.put("failureRegistry.summary", securityFailureRegistry.getGlobalFailureSummary());

            // Check for suspicious activity
            boolean suspiciousActivity = false;
            for (String ip : securityFailureRegistry.getAllFailureStats().keySet()) {
                if (securityFailureRegistry.isSuspiciousIp(ip)) {
                    suspiciousActivity = true;
                    break;
                }
            }

            if (suspiciousActivity) {
                securityInfo.put("status", "WARNING");
                securityInfo.put("alert", "Suspicious activity patterns detected");
                health.getComponents().put("securityMonitoring", securityInfo);
                return SystemStatus.WARNING;
            }

            securityInfo.put("status", "HEALTHY");
            health.getComponents().put("securityMonitoring", securityInfo);
            return SystemStatus.HEALTHY;

        } catch (Exception e) {
            securityInfo.put("status", "CRITICAL");
            securityInfo.put("error", "Security monitoring check failed: " + e.getMessage());
            health.getComponents().put("securityMonitoring", securityInfo);
            return SystemStatus.CRITICAL;
        }
    }

    /**
     * Add system metrics
     */
    private void addSystemMetrics(SystemHealth health) {
        Map<String, Object> metrics = health.getMetrics();

        // Configuration metrics
        metrics.put("configuration.virusScanningEnabled", properties.getVirusScanning().isEnabled());
        metrics.put("configuration.auditLoggingEnabled", properties.getAuditLogging().isEnabled());
        metrics.put("configuration.rateLimitingEnabled", properties.getRateLimit().isEnabled());
        metrics.put("configuration.monitoringEnabled", properties.getMonitoring().isEnabled());
        metrics.put("configuration.uploadDir", properties.getUploadDir());

        // Security metrics
        metrics.put("security.trackedClients", uploadAttemptTracker.getTrackedClientCount());
        metrics.put("security.trackedFailureIps", securityFailureRegistry.getTrackedIpCount());

        // Rate limiting metrics
        if (properties.getRateLimit().isEnabled()) {
            metrics.put("rateLimit.maxUploadsPerMinute", properties.getRateLimit().getMaxUploadsPerMinute());
            metrics.put("rateLimit.maxUploadsPerHour", properties.getRateLimit().getMaxUploadsPerHour());
            metrics.put("rateLimit.maxConcurrentUploads", properties.getRateLimit().getMaxConcurrentUploads());
        }

        // Monitoring metrics
        if (properties.getMonitoring().isEnabled()) {
            metrics.put("monitoring.healthCheckCacheSeconds", properties.getMonitoring().getHealthCheckCacheSeconds());
            metrics.put("monitoring.includeDetailedMetrics", properties.getMonitoring().isIncludeDetailedMetrics());
            metrics.put("monitoring.logHealthCheckAccess", properties.getMonitoring().isLogHealthCheckAccess());
        }
    }

    /**
     * Get worst status between two statuses
     */
    private SystemStatus getWorstStatus(SystemStatus status1, SystemStatus status2) {
        return status1.ordinal() > status2.ordinal() ? status1 : status2;
    }

    /**
     * Get status message based on system status
     */
    private String getStatusMessage(SystemStatus status) {
        switch (status) {
            case HEALTHY:
                return "All file management components are operating normally";
            case WARNING:
                return "Minor issues detected - monitoring recommended";
            case DEGRADED:
                return "Significant issues affecting functionality - attention required";
            case CRITICAL:
                return "Critical issues detected - immediate intervention required";
            default:
                return "Unknown status";
        }
    }

    /**
     * Get system health as JSON string
     */
    public String getSystemHealthJson() {
        try {
            SystemHealth health = getSystemHealth();
            return objectMapper.writeValueAsString(health);
        } catch (Exception e) {
            logger.error("Failed to serialize system health to JSON", e);
            return "{\"status\":\"CRITICAL\",\"message\":\"Failed to generate health report\"}";
        }
    }

    /**
     * Log system health summary
     */
    public void logSystemHealthSummary() {
        SystemHealth health = getSystemHealth();
        logger.info("System Health Status: {} - {}", health.getStatus(), health.getMessage());
        
        for (Map.Entry<String, Object> component : health.getComponents().entrySet()) {
            if (component.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> componentDetails = (Map<String, Object>) component.getValue();
                Object status = componentDetails.get("status");
                logger.info("Component {}: {}", component.getKey(), status);
            }
        }
    }
}