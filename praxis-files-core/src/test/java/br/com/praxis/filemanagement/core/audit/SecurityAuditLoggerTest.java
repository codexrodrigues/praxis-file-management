package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SecurityAuditLoggerTest {

    @Mock
    private SecurityFailureRegistry securityFailureRegistry;

    private FileManagementProperties properties;
    private SecurityAuditLogger auditLogger;

    @BeforeEach
    void setUp() throws Exception {
        properties = new FileManagementProperties();
        auditLogger = new SecurityAuditLogger();

        Field propsField = SecurityAuditLogger.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        propsField.set(auditLogger, properties);

        Field registryField = SecurityAuditLogger.class.getDeclaredField("securityFailureRegistry");
        registryField.setAccessible(true);
        registryField.set(auditLogger, securityFailureRegistry);
    }

    @Test
    void skipsLoggingWhenDisabled() {
        FileManagementProperties.AuditLogging audit = new FileManagementProperties.AuditLogging();
        audit.setEnabled(false);
        properties.setAuditLogging(audit);

        auditLogger.logSecurityViolation("1.1.1.1", "file.txt", 10L, "text/plain",
                FileErrorReason.MIME_TYPE_MISMATCH, "err", "details");

        verifyNoInteractions(securityFailureRegistry);
    }

    @Test
    void logsWhenEnabled() {
        FileManagementProperties.AuditLogging audit = new FileManagementProperties.AuditLogging();
        audit.setEnabled(true);
        properties.setAuditLogging(audit);

        auditLogger.logSecurityViolation("1.1.1.1", "file.txt", 10L, "text/plain",
                FileErrorReason.MIME_TYPE_MISMATCH, "err", "details");

        verify(securityFailureRegistry).registerSecurityFailure(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void builderAndEventExposeExpectedValues() {
        SecurityAuditLogger.SecurityAuditEvent event = SecurityAuditLogger
                .createEvent(SecurityAuditLogger.SecurityEventType.UPLOAD_SUCCESS, SecurityAuditLogger.SecuritySeverity.LOW)
                .clientIp("1.1.1.1")
                .userAgent("curl")
                .sessionId("sess-1")
                .filename("server.txt")
                .originalFilename("client.txt")
                .fileSize(42L)
                .mimeType("text/plain")
                .detectedMimeType("text/plain")
                .errorReason(FileErrorReason.CORRUPTED_FILE)
                .errorMessage("boom")
                .securityDetails("details")
                .processingTime(123L)
                .additionalData("key", "value")
                .build();

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        assertEquals(SecurityAuditLogger.SecurityEventType.UPLOAD_SUCCESS, event.getEventType());
        assertEquals(SecurityAuditLogger.SecuritySeverity.LOW, event.getSeverity());
        assertEquals("1.1.1.1", event.getClientIp());
        assertEquals("curl", event.getUserAgent());
        assertEquals("sess-1", event.getSessionId());
        assertEquals("server.txt", event.getFilename());
        assertEquals("client.txt", event.getOriginalFilename());
        assertEquals(42L, event.getFileSize());
        assertEquals("text/plain", event.getMimeType());
        assertEquals("text/plain", event.getDetectedMimeType());
        assertEquals(FileErrorReason.CORRUPTED_FILE, event.getErrorReason());
        assertEquals("boom", event.getErrorMessage());
        assertEquals("details", event.getSecurityDetails());
        assertEquals(123L, event.getProcessingTimeMs());
        assertEquals(Map.of("key", "value"), event.getAdditionalData());
    }

    @Test
    void helperMethodsMapSeverityAndEventType() throws Exception {
        assertEquals(SecurityAuditLogger.SecuritySeverity.CRITICAL,
                invokeDetermineSeverity(FileErrorReason.MALWARE_DETECTED));
        assertEquals(SecurityAuditLogger.SecuritySeverity.HIGH,
                invokeDetermineSeverity(FileErrorReason.PATH_TRAVERSAL));
        assertEquals(SecurityAuditLogger.SecuritySeverity.MEDIUM,
                invokeDetermineSeverity(FileErrorReason.MIME_TYPE_MISMATCH));
        assertEquals(SecurityAuditLogger.SecuritySeverity.LOW,
                invokeDetermineSeverity(FileErrorReason.EMPTY_FILE));

        assertEquals(SecurityAuditLogger.SecurityEventType.MALWARE_DETECTED,
                invokeDetermineEventType(FileErrorReason.MALWARE_DETECTED));
        assertEquals(SecurityAuditLogger.SecurityEventType.SUSPICIOUS_CONTENT,
                invokeDetermineEventType(FileErrorReason.DANGEROUS_SCRIPT));
        assertEquals(SecurityAuditLogger.SecurityEventType.STRUCTURE_VIOLATION,
                invokeDetermineEventType(FileErrorReason.ZIP_BOMB_DETECTED));
        assertEquals(SecurityAuditLogger.SecurityEventType.VALIDATION_FAILED,
                invokeDetermineEventType(FileErrorReason.FILE_TOO_LARGE));
    }

    @Test
    void truncatesMessagesUsingConfiguredLimit() throws Exception {
        properties.getAuditLogging().setMaxErrorMessageLength(8);

        assertNull(invokeTruncate(null));
        assertEquals("short", invokeTruncate("short"));
        assertEquals("12345...", invokeTruncate("1234567890"));
    }

    @Test
    void publicLoggingHelpersRegisterExpectedFailures() {
        properties.getAuditLogging().setEnabled(true);
        properties.getAuditLogging().setMaxErrorMessageLength(10);

        auditLogger.logUploadStarted("1.1.1.1", "file.txt", 10L, "text/plain");
        auditLogger.logUploadSuccess("1.1.1.1", "file.txt", "srv.txt", 10L, "text/plain", 5L);
        auditLogger.logRateLimitExceeded("1.1.1.1", "file.txt");
        auditLogger.logMalwareDetected("1.1.1.1", "virus.txt", 12L, "EICAR", "clamav");
        auditLogger.logSecurityViolation(
                "1.1.1.1",
                "blocked.txt",
                15L,
                "text/plain",
                FileErrorReason.PATH_TRAVERSAL,
                "0123456789ABCDE",
                "details-12345");

        verify(securityFailureRegistry).registerSecurityFailure(
                eq("1.1.1.1"),
                eq("virus.txt"),
                eq(12L),
                eq(null),
                eq(FileErrorReason.MALWARE_DETECTED),
                eq("Virus: EICAR"),
                eq("Scanner: clamav"));
        verify(securityFailureRegistry).registerSecurityFailure(
                eq("1.1.1.1"),
                eq("blocked.txt"),
                eq(15L),
                eq("text/plain"),
                eq(FileErrorReason.PATH_TRAVERSAL),
                eq("0123456..."),
                eq("details..."));
    }

    @Test
    void logSecurityEventAlwaysClearsMdcContext() {
        properties.getAuditLogging().setEnabled(true);
        SecurityAuditLogger.SecurityAuditEvent event = new SecurityAuditLogger.SecurityAuditEvent();
        event.setEventType(SecurityAuditLogger.SecurityEventType.ACCESS_DENIED);
        event.setSeverity(SecurityAuditLogger.SecuritySeverity.MEDIUM);
        event.setClientIp("1.1.1.1");
        event.setFilename("server.txt");
        event.setOriginalFilename("client.txt");

        auditLogger.logSecurityEvent(event);

        assertNull(MDC.get("event_id"));
        assertNull(MDC.get("event_type"));
        assertNull(MDC.get("severity"));
        assertNull(MDC.get("client_ip"));
        assertNull(MDC.get("filename"));
        assertNull(MDC.get("original_filename"));
    }

    private SecurityAuditLogger.SecuritySeverity invokeDetermineSeverity(FileErrorReason reason) throws Exception {
        Method method = SecurityAuditLogger.class.getDeclaredMethod("determineSeverity", FileErrorReason.class);
        method.setAccessible(true);
        return (SecurityAuditLogger.SecuritySeverity) method.invoke(auditLogger, reason);
    }

    private SecurityAuditLogger.SecurityEventType invokeDetermineEventType(FileErrorReason reason) throws Exception {
        Method method = SecurityAuditLogger.class.getDeclaredMethod("determineEventType", FileErrorReason.class);
        method.setAccessible(true);
        return (SecurityAuditLogger.SecurityEventType) method.invoke(auditLogger, reason);
    }

    private String invokeTruncate(String message) throws Exception {
        Method method = SecurityAuditLogger.class.getDeclaredMethod("truncateErrorMessage", String.class);
        method.setAccessible(true);
        return (String) method.invoke(auditLogger, message);
    }
}
