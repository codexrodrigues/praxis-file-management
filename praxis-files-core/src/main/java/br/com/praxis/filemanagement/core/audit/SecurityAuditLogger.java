package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Security audit logger for comprehensive tracking of file upload security events.
 * Provides structured logging with correlation IDs, security context, and detailed event information.
 */
@Service
public class SecurityAuditLogger {

    private static final Logger auditLogger = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditLogger.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    @Autowired
    private FileManagementProperties properties;
    
    @Autowired
    private SecurityFailureRegistry securityFailureRegistry;

    /**
     * Security event types for categorization
     */
    public enum SecurityEventType {
        UPLOAD_STARTED,
        UPLOAD_SUCCESS,
        UPLOAD_FAILED,
        SECURITY_VIOLATION,
        RATE_LIMIT_EXCEEDED,
        MALWARE_DETECTED,
        SUSPICIOUS_CONTENT,
        VALIDATION_FAILED,
        STRUCTURE_VIOLATION,
        ACCESS_DENIED
    }

    /**
     * Security event severity levels
     */
    public enum SecuritySeverity {
        LOW,      // Informational events
        MEDIUM,   // Warning events
        HIGH,     // Security violations
        CRITICAL  // Critical security threats
    }

    /**
     * Security audit event data structure
     */
    public static class SecurityAuditEvent {
        private String eventId;
        private String timestamp;
        private SecurityEventType eventType;
        private SecuritySeverity severity;
        private String clientIp;
        private String userAgent;
        private String sessionId;
        private String filename;
        private String originalFilename;
        private Long fileSize;
        private String mimeType;
        private String detectedMimeType;
        private FileErrorReason errorReason;
        private String errorMessage;
        private String securityDetails;
        private Map<String, Object> additionalData;
        private Long processingTimeMs;

        public SecurityAuditEvent() {
            this.eventId = UUID.randomUUID().toString();
            this.timestamp = Instant.now().atOffset(ZoneOffset.UTC).format(ISO_FORMATTER);
            this.additionalData = new HashMap<>();
        }

        // Getters and setters
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public SecurityEventType getEventType() { return eventType; }
        public void setEventType(SecurityEventType eventType) { this.eventType = eventType; }

        public SecuritySeverity getSeverity() { return severity; }
        public void setSeverity(SecuritySeverity severity) { this.severity = severity; }

        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }

        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public String getDetectedMimeType() { return detectedMimeType; }
        public void setDetectedMimeType(String detectedMimeType) { this.detectedMimeType = detectedMimeType; }

        public FileErrorReason getErrorReason() { return errorReason; }
        public void setErrorReason(FileErrorReason errorReason) { this.errorReason = errorReason; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getSecurityDetails() { return securityDetails; }
        public void setSecurityDetails(String securityDetails) { this.securityDetails = securityDetails; }

        public Map<String, Object> getAdditionalData() { return additionalData; }
        public void setAdditionalData(Map<String, Object> additionalData) { this.additionalData = additionalData; }

        public Long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

        public void addAdditionalData(String key, Object value) {
            this.additionalData.put(key, value);
        }
    }

    /**
     * Builder for creating security audit events
     */
    public static class SecurityAuditEventBuilder {
        private final SecurityAuditEvent event;

        public SecurityAuditEventBuilder(SecurityEventType eventType, SecuritySeverity severity) {
            this.event = new SecurityAuditEvent();
            this.event.setEventType(eventType);
            this.event.setSeverity(severity);
        }

        public SecurityAuditEventBuilder clientIp(String clientIp) {
            event.setClientIp(clientIp);
            return this;
        }

        public SecurityAuditEventBuilder userAgent(String userAgent) {
            event.setUserAgent(userAgent);
            return this;
        }

        public SecurityAuditEventBuilder sessionId(String sessionId) {
            event.setSessionId(sessionId);
            return this;
        }

        public SecurityAuditEventBuilder filename(String filename) {
            event.setFilename(filename);
            return this;
        }

        public SecurityAuditEventBuilder originalFilename(String originalFilename) {
            event.setOriginalFilename(originalFilename);
            return this;
        }

        public SecurityAuditEventBuilder fileSize(Long fileSize) {
            event.setFileSize(fileSize);
            return this;
        }

        public SecurityAuditEventBuilder mimeType(String mimeType) {
            event.setMimeType(mimeType);
            return this;
        }

        public SecurityAuditEventBuilder detectedMimeType(String detectedMimeType) {
            event.setDetectedMimeType(detectedMimeType);
            return this;
        }

        public SecurityAuditEventBuilder errorReason(FileErrorReason errorReason) {
            event.setErrorReason(errorReason);
            return this;
        }

        public SecurityAuditEventBuilder errorMessage(String errorMessage) {
            event.setErrorMessage(errorMessage);
            return this;
        }

        public SecurityAuditEventBuilder securityDetails(String securityDetails) {
            event.setSecurityDetails(securityDetails);
            return this;
        }

        public SecurityAuditEventBuilder processingTime(Long processingTimeMs) {
            event.setProcessingTimeMs(processingTimeMs);
            return this;
        }

        public SecurityAuditEventBuilder additionalData(String key, Object value) {
            event.addAdditionalData(key, value);
            return this;
        }

        public SecurityAuditEvent build() {
            return event;
        }
    }

    /**
     * Log a security audit event
     */
    public void logSecurityEvent(SecurityAuditEvent event) {
        if (!properties.getAuditLogging().isEnabled()) {
            return;
        }
        try {
            // Set correlation context in MDC
            setMDCContext(event);

            // Convert event to JSON for structured logging
            String eventJson = objectMapper.writeValueAsString(event);

            // Log at appropriate level based on severity
            switch (event.getSeverity()) {
                case LOW:
                    auditLogger.info("SECURITY_EVENT: {}", eventJson);
                    break;
                case MEDIUM:
                    auditLogger.warn("SECURITY_EVENT: {}", eventJson);
                    break;
                case HIGH:
                    auditLogger.error("SECURITY_EVENT: {}", eventJson);
                    break;
                case CRITICAL:
                    auditLogger.error("CRITICAL_SECURITY_EVENT: {}", eventJson);
                    break;
            }

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize security audit event", e);
            // Fallback to simple logging
            auditLogger.error("SECURITY_EVENT_SERIALIZATION_FAILED: eventType={}, severity={}, filename={}, error={}", 
                event.getEventType(), event.getSeverity(), event.getFilename(), e.getMessage());
        } finally {
            // Clean up MDC context
            clearMDCContext();
        }
    }

    /**
     * Create builder for security audit events
     */
    public static SecurityAuditEventBuilder createEvent(SecurityEventType eventType, SecuritySeverity severity) {
        return new SecurityAuditEventBuilder(eventType, severity);
    }

    /**
     * Log upload started event
     */
    public void logUploadStarted(String clientIp, String originalFilename, Long fileSize, String mimeType) {
        if (!properties.getAuditLogging().isEnabled()) {
            return;
        }
        SecurityAuditEvent event = createEvent(SecurityEventType.UPLOAD_STARTED, SecuritySeverity.LOW)
            .clientIp(clientIp)
            .originalFilename(originalFilename)
            .fileSize(fileSize)
            .mimeType(mimeType)
            .build();

        logSecurityEvent(event);
    }

    /**
     * Log upload success event
     */
    public void logUploadSuccess(String clientIp, String originalFilename, String serverFilename,
                                Long fileSize, String detectedMimeType, Long processingTimeMs) {
        if (!properties.getAuditLogging().isEnabled()) {
            return;
        }
        SecurityAuditEvent event = createEvent(SecurityEventType.UPLOAD_SUCCESS, SecuritySeverity.LOW)
            .clientIp(clientIp)
            .originalFilename(originalFilename)
            .filename(serverFilename)
            .fileSize(fileSize)
            .detectedMimeType(detectedMimeType)
            .processingTime(processingTimeMs)
            .build();

        logSecurityEvent(event);
    }

    /**
     * Log security violation event
     */
    public void logSecurityViolation(String clientIp, String originalFilename, Long fileSize,
                                   String detectedMimeType, FileErrorReason errorReason,
                                   String errorMessage, String securityDetails) {
        if (!properties.getAuditLogging().isEnabled()) {
            return;
        }

        SecuritySeverity severity = determineSeverity(errorReason);
        SecurityEventType eventType = determineEventType(errorReason);

        // Truncate error message if necessary
        String truncatedErrorMessage = truncateErrorMessage(errorMessage);
        String truncatedSecurityDetails = truncateErrorMessage(securityDetails);

        SecurityAuditEvent event = createEvent(eventType, severity)
            .clientIp(clientIp)
            .originalFilename(originalFilename)
            .fileSize(fileSize)
            .detectedMimeType(detectedMimeType)
            .errorReason(errorReason)
            .errorMessage(truncatedErrorMessage)
            .securityDetails(truncatedSecurityDetails)
            .build();

        logSecurityEvent(event);

        // Register security failure for forensic analysis
        securityFailureRegistry.registerSecurityFailure(clientIp, originalFilename, fileSize,
            detectedMimeType, errorReason, truncatedErrorMessage, truncatedSecurityDetails);
    }

    /**
     * Log rate limit exceeded event
     */
    public void logRateLimitExceeded(String clientIp, String originalFilename) {
        if (!properties.getAuditLogging().isEnabled()) {
            return;
        }
        SecurityAuditEvent event = createEvent(SecurityEventType.RATE_LIMIT_EXCEEDED, SecuritySeverity.MEDIUM)
            .clientIp(clientIp)
            .originalFilename(originalFilename)
            .securityDetails("Rate limit policy enforced")
            .build();

        logSecurityEvent(event);
    }

    /**
     * Log malware detection event
     */
    public void logMalwareDetected(String clientIp, String originalFilename, Long fileSize,
                                  String virusName, String scannerType) {
        if (!properties.getAuditLogging().isEnabled()) {
            return;
        }
        SecurityAuditEvent event = createEvent(SecurityEventType.MALWARE_DETECTED, SecuritySeverity.CRITICAL)
            .clientIp(clientIp)
            .originalFilename(originalFilename)
            .fileSize(fileSize)
            .securityDetails("Virus: " + virusName)
            .additionalData("scanner_type", scannerType)
            .additionalData("virus_name", virusName)
            .build();

        logSecurityEvent(event);
        
        // Register security failure for forensic analysis
        securityFailureRegistry.registerSecurityFailure(clientIp, originalFilename, fileSize, 
            null, FileErrorReason.MALWARE_DETECTED, "Virus: " + virusName, "Scanner: " + scannerType);
    }

    /**
     * Determine event severity based on error reason
     */
    private SecuritySeverity determineSeverity(FileErrorReason errorReason) {
        switch (errorReason) {
            case MALWARE_DETECTED:
            case DANGEROUS_EXECUTABLE:
            case ZIP_BOMB_DETECTED:
                return SecuritySeverity.CRITICAL;
            
            case DANGEROUS_SCRIPT:
            case SUSPICIOUS_STRUCTURE:
            case PATH_TRAVERSAL:
            case EMBEDDED_EXECUTABLE:
                return SecuritySeverity.HIGH;
            
            case SIGNATURE_MISMATCH:
            case CORRUPTED_FILE:
            case MIME_TYPE_MISMATCH:
                return SecuritySeverity.MEDIUM;
            
            default:
                return SecuritySeverity.LOW;
        }
    }

    /**
     * Determine event type based on error reason
     */
    private SecurityEventType determineEventType(FileErrorReason errorReason) {
        switch (errorReason) {
            case MALWARE_DETECTED:
                return SecurityEventType.MALWARE_DETECTED;
            
            case DANGEROUS_EXECUTABLE:
            case DANGEROUS_SCRIPT:
            case SUSPICIOUS_STRUCTURE:
            case EMBEDDED_EXECUTABLE:
                return SecurityEventType.SUSPICIOUS_CONTENT;
            
            case ZIP_BOMB_DETECTED:
            case PATH_TRAVERSAL:
                return SecurityEventType.STRUCTURE_VIOLATION;
            
            default:
                return SecurityEventType.VALIDATION_FAILED;
        }
    }

    /**
     * Set MDC context for correlation
     */
    private void setMDCContext(SecurityAuditEvent event) {
        // Always set required fields
        MDC.put("event_id", event.getEventId() != null ? event.getEventId() : "unknown");
        MDC.put("event_type", event.getEventType() != null ? event.getEventType().toString() : "UNKNOWN");
        MDC.put("severity", event.getSeverity() != null ? event.getSeverity().toString() : "LOW");
        
        // Set optional fields only if not null and not empty
        if (event.getClientIp() != null && !event.getClientIp().trim().isEmpty()) {
            MDC.put("client_ip", event.getClientIp());
        }
        
        if (event.getFilename() != null && !event.getFilename().trim().isEmpty()) {
            MDC.put("filename", event.getFilename());
        }
        
        if (event.getOriginalFilename() != null && !event.getOriginalFilename().trim().isEmpty()) {
            MDC.put("original_filename", event.getOriginalFilename());
        }
    }

    /**
     * Truncate error messages to configured maximum length
     */
    private String truncateErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        
        int maxLength = properties.getAuditLogging().getMaxErrorMessageLength();
        if (message.length() <= maxLength) {
            return message;
        }
        
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Clear MDC context
     */
    private void clearMDCContext() {
        MDC.remove("event_id");
        MDC.remove("event_type");
        MDC.remove("severity");
        MDC.remove("client_ip");
        MDC.remove("filename");
        MDC.remove("original_filename");
    }
}