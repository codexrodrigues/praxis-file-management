package br.com.praxis.filemanagement.core.audit;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFailureRegistryTest {

    private SecurityFailureRegistry registry;
    private FileManagementProperties properties;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        properties = new FileManagementProperties();
        properties.setUploadDir(tempDir.toString());
        properties.getAuditLogging().setEnabled(true);
        properties.getAuditLogging().setStatisticsRetentionHours(1);

        registry = new SecurityFailureRegistry();

        Field propertiesField = SecurityFailureRegistry.class.getDeclaredField("properties");
        propertiesField.setAccessible(true);
        propertiesField.set(registry, properties);
    }

    @Test
    @DisplayName("registers failure updates counters and persists log record")
    void registersFailureUpdatesCountersAndPersistsLogRecord(@TempDir Path tempDir) throws Exception {
        properties.setUploadDir(tempDir.toString());

        registry.registerSecurityFailure(
            "10.0.0.1",
            "malicious.exe",
            123L,
            "application/octet-stream",
            FileErrorReason.MALWARE_DETECTED,
            "malware found",
            "engine=eicar"
        );

        SecurityFailureRegistry.SecurityFailureStats stats = registry.getFailureStats("10.0.0.1");
        assertNotNull(stats);
        assertEquals(1, stats.getTotalFailures());
        assertEquals(1, stats.getMalwareDetections());
        assertEquals(1, registry.getTotalFailureCount(FileErrorReason.MALWARE_DETECTED));
        assertEquals(1, registry.getTrackedIpCount());

        Path logDir = tempDir.resolve("security-logs");
        assertTrue(Files.exists(logDir));
        Path logFile = Files.list(logDir).findFirst().orElseThrow();
        String logContents = Files.readString(logFile);
        assertTrue(logContents.contains("\"clientIp\":\"10.0.0.1\""));
        assertTrue(logContents.contains("\"errorReason\":\"MALWARE_DETECTED\""));
    }

    @Test
    @DisplayName("ignores invalid client addresses")
    void ignoresInvalidClientAddresses() {
        registry.registerSecurityFailure("localhost", "file.txt", 1L, "text/plain",
            FileErrorReason.PATH_TRAVERSAL, "blocked", "details");
        registry.registerSecurityFailure("unknown", "file.txt", 1L, "text/plain",
            FileErrorReason.PATH_TRAVERSAL, "blocked", "details");

        assertEquals(0, registry.getTrackedIpCount());
        assertNull(registry.getFailureStats("localhost"));
    }

    @Test
    @DisplayName("detects suspicious ips from multiple security patterns")
    void detectsSuspiciousIpsFromMultipleSecurityPatterns() {
        for (int i = 0; i < 5; i++) {
            registry.registerSecurityFailure("10.0.0.2", "payload-" + i, 1L, "text/plain",
                FileErrorReason.PATH_TRAVERSAL, "blocked", "path traversal");
        }

        assertTrue(registry.isSuspiciousIp("10.0.0.2"));
        assertTrue(registry.getGlobalFailureSummary().contains("5 path traversal"));
        assertTrue(registry.getGlobalFailureSummary().contains("1 suspicious IPs"));
    }

    @Test
    @DisplayName("returns false for non suspicious ip and exposes copies of stats")
    void returnsFalseForNonSuspiciousIpAndExposesCopiesOfStats() {
        registry.registerSecurityFailure("10.0.0.3", "sig.bin", 10L, "application/octet-stream",
            FileErrorReason.SIGNATURE_MISMATCH, "mismatch", "details");

        assertFalse(registry.isSuspiciousIp("10.0.0.3"));
        assertEquals(1, registry.getAllFailureStats().size());
        assertEquals(0, registry.getTotalFailureCount(FileErrorReason.MALWARE_DETECTED));
    }

    @Test
    @DisplayName("cleans up stale statistics by retention window")
    void cleansUpStaleStatisticsByRetentionWindow() throws Exception {
        registry.registerSecurityFailure("10.0.0.4", "old.zip", 10L, "application/zip",
            FileErrorReason.ZIP_BOMB_DETECTED, "zip bomb", "details");
        registry.registerSecurityFailure("10.0.0.5", "new.exe", 10L, "application/octet-stream",
            FileErrorReason.DANGEROUS_EXECUTABLE, "dangerous", "details");

        SecurityFailureRegistry.SecurityFailureStats oldStats = registry.getFailureStats("10.0.0.4");
        SecurityFailureRegistry.SecurityFailureStats newStats = registry.getFailureStats("10.0.0.5");
        setAtomicLong(oldStats, "lastFailureTime", System.currentTimeMillis() - (2 * 60 * 60 * 1000L));
        setAtomicLong(newStats, "lastFailureTime", System.currentTimeMillis());

        registry.cleanupOldStats();

        assertNull(registry.getFailureStats("10.0.0.4"));
        assertNotNull(registry.getFailureStats("10.0.0.5"));
    }

    @Test
    @DisplayName("covers security failure record bean accessors")
    void coversSecurityFailureRecordBeanAccessors() {
        SecurityFailureRegistry.SecurityFailureRecord record = new SecurityFailureRegistry.SecurityFailureRecord();
        record.setClientIp("10.0.0.6");
        record.setFilename("evil.bin");
        record.setFileSize(77L);
        record.setMimeType("application/octet-stream");
        record.setErrorReason(FileErrorReason.DANGEROUS_EXECUTABLE);
        record.setErrorMessage("blocked");
        record.setSecurityDetails("details");
        record.setUserAgent("curl");
        record.setSessionId("session");

        assertNotNull(record.getTimestamp());
        assertEquals("10.0.0.6", record.getClientIp());
        assertEquals("evil.bin", record.getFilename());
        assertEquals(77L, record.getFileSize());
        assertEquals("application/octet-stream", record.getMimeType());
        assertEquals(FileErrorReason.DANGEROUS_EXECUTABLE, record.getErrorReason());
        assertEquals("blocked", record.getErrorMessage());
        assertEquals("details", record.getSecurityDetails());
        assertEquals("curl", record.getUserAgent());
        assertEquals("session", record.getSessionId());
    }

    private void setAtomicLong(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        AtomicLong atomicLong = (AtomicLong) field.get(target);
        atomicLong.set(value);
    }
}
