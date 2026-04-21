package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UploadAttemptTrackerTest {

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    private UploadAttemptTracker tracker;
    private FileManagementProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = new FileManagementProperties();
        properties.getAuditLogging().setEnabled(true);
        properties.getAuditLogging().setCleanupIntervalMinutes(1);
        properties.getAuditLogging().setStatisticsRetentionHours(1);

        tracker = new UploadAttemptTracker();
        inject("properties", properties);
        inject("securityAuditLogger", securityAuditLogger);
    }

    @Test
    @DisplayName("tracks attempts successes failures and security counters")
    void tracksAttemptsSuccessesFailuresAndSecurityCounters() {
        tracker.recordUploadAttempt("10.0.0.1", "ok.txt", 100);
        tracker.recordSuccessfulUpload("10.0.0.1", "ok.txt", 80);
        tracker.recordFailedUpload("10.0.0.1", "bad.exe", FileErrorReason.DANGEROUS_EXECUTABLE);
        tracker.recordFailedUpload("10.0.0.1", "virus.txt", FileErrorReason.MALWARE_DETECTED);
        tracker.recordRateLimitExceeded("10.0.0.1");

        UploadAttemptTracker.ClientUploadStats stats = tracker.getClientStats("10.0.0.1");

        assertNotNull(stats);
        assertEquals(1, stats.getTotalAttempts());
        assertEquals(1, stats.getSuccessfulUploads());
        assertEquals(2, stats.getFailedUploads());
        assertEquals(2, stats.getSecurityViolations());
        assertEquals(1, stats.getMalwareDetections());
        assertEquals(1, stats.getRateLimitExceeded());
        assertEquals(100, stats.getTotalBytesAttempted());
        assertEquals(80, stats.getTotalBytesUploaded());
        assertEquals(2, stats.getConsecutiveFailures());
        assertTrue(tracker.getGlobalSummary().contains("1 clients tracked"));
        assertTrue(tracker.getGlobalSummary().contains("2 security violations"));
    }

    @Test
    @DisplayName("ignores local and invalid client addresses")
    void ignoresLocalAndInvalidClientAddresses() {
        tracker.recordUploadAttempt(null, "a.txt", 1);
        tracker.recordUploadAttempt(" ", "a.txt", 1);
        tracker.recordUploadAttempt("unknown", "a.txt", 1);
        tracker.recordUploadAttempt("localhost", "a.txt", 1);
        tracker.recordUploadAttempt("127.0.0.1", "a.txt", 1);
        tracker.recordUploadAttempt("0:0:0:0:0:0:0:1", "a.txt", 1);

        assertEquals(0, tracker.getTrackedClientCount());
        assertNull(tracker.getClientStats("localhost"));
        verifyNoInteractions(securityAuditLogger);
    }

    @Test
    @DisplayName("logs suspicious activity when thresholds are exceeded")
    void logsSuspiciousActivityWhenThresholdsAreExceeded() {
        for (int i = 0; i < 10; i++) {
            tracker.recordUploadAttempt("10.0.0.2", "bad-" + i + ".exe", 10);
            tracker.recordFailedUpload("10.0.0.2", "bad-" + i + ".exe", FileErrorReason.DANGEROUS_EXECUTABLE);
        }

        ArgumentCaptor<SecurityAuditLogger.SecurityAuditEvent> eventCaptor =
            ArgumentCaptor.forClass(SecurityAuditLogger.SecurityAuditEvent.class);
        verify(securityAuditLogger, atLeastOnce()).logSecurityEvent(eventCaptor.capture());

        SecurityAuditLogger.SecurityAuditEvent event =
            eventCaptor.getAllValues().get(eventCaptor.getAllValues().size() - 1);
        assertEquals(SecurityAuditLogger.SecurityEventType.SUSPICIOUS_CONTENT, event.getEventType());
        assertEquals(SecurityAuditLogger.SecuritySeverity.HIGH, event.getSeverity());
        assertEquals("10.0.0.2", event.getClientIp());
        assertTrue(
            event.getSecurityDetails().contains("security violation rate") ||
            event.getSecurityDetails().contains("consecutive failures")
        );
        assertEquals(10L, event.getAdditionalData().get("total_attempts"));
    }

    @Test
    @DisplayName("cleans up stale statistics when cleanup interval elapses")
    void cleansUpStaleStatisticsWhenCleanupIntervalElapses() throws Exception {
        tracker.recordUploadAttempt("10.0.0.3", "old.txt", 1);
        tracker.recordUploadAttempt("10.0.0.4", "new.txt", 1);

        UploadAttemptTracker.ClientUploadStats staleStats = tracker.getClientStats("10.0.0.3");
        UploadAttemptTracker.ClientUploadStats freshStats = tracker.getClientStats("10.0.0.4");
        setAtomicLong(staleStats, "lastAttemptTime", System.currentTimeMillis() - (2 * 60 * 60 * 1000L));
        setAtomicLong(freshStats, "lastAttemptTime", System.currentTimeMillis());

        Field lastCleanupTimeField = UploadAttemptTracker.class.getDeclaredField("lastCleanupTime");
        lastCleanupTimeField.setAccessible(true);
        lastCleanupTimeField.setLong(tracker, System.currentTimeMillis() - (2 * 60 * 1000L));

        tracker.recordUploadAttempt("10.0.0.5", "trigger.txt", 1);

        assertNull(tracker.getClientStats("10.0.0.3"));
        assertNotNull(tracker.getClientStats("10.0.0.4"));
        assertNotNull(tracker.getClientStats("10.0.0.5"));
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = UploadAttemptTracker.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(tracker, value);
    }

    private void setAtomicLong(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        AtomicLong atomicLong = (AtomicLong) field.get(target);
        atomicLong.set(value);
    }
}
