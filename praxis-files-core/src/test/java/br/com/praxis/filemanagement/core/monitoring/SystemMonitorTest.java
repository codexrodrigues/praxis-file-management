package br.com.praxis.filemanagement.core.monitoring;

import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.audit.SecurityFailureRegistry;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.VirusScanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemMonitorTest {

    @Mock
    private VirusScanningService virusScanningService;

    @Mock
    private UploadAttemptTracker uploadAttemptTracker;

    @Mock
    private SecurityFailureRegistry securityFailureRegistry;

    private SystemMonitor monitor;
    private FileManagementProperties properties;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        properties = new FileManagementProperties();
        properties.setUploadDir(tempDir.toString());
        properties.getMonitoring().setEnabled(true);
        properties.getMonitoring().setHealthCheckCacheSeconds(60);
        properties.getAuditLogging().setEnabled(false);
        properties.getVirusScanning().setEnabled(false);

        lenient().when(uploadAttemptTracker.getTrackedClientCount()).thenReturn(2);
        lenient().when(uploadAttemptTracker.getGlobalSummary()).thenReturn("upload summary");
        lenient().when(securityFailureRegistry.getTrackedIpCount()).thenReturn(1);
        lenient().when(securityFailureRegistry.getGlobalFailureSummary()).thenReturn("failure summary");
        lenient().when(securityFailureRegistry.getAllFailureStats()).thenReturn(new java.util.concurrent.ConcurrentHashMap<>());

        monitor = new SystemMonitor();
        inject("properties", properties);
        inject("virusScanningService", virusScanningService);
        inject("uploadAttemptTracker", uploadAttemptTracker);
        inject("securityFailureRegistry", securityFailureRegistry);
    }

    @Test
    @DisplayName("returns healthy status for baseline healthy environment")
    void returnsHealthyStatusForBaselineHealthyEnvironment() {
        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.HEALTHY, health.getStatus());
        assertEquals("All file management components are operating normally", health.getMessage());
        assertEquals("HEALTHY", ((Map<?, ?>) health.getComponents().get("uploadDirectory")).get("status"));
        assertEquals("DISABLED", ((Map<?, ?>) health.getComponents().get("virusScanning")).get("status"));
        assertEquals(Boolean.TRUE, health.getMetrics().get("configuration.monitoringEnabled"));
        assertEquals(2, health.getMetrics().get("security.trackedClients"));
    }

    @Test
    @DisplayName("uses cached health result within cache window")
    void usesCachedHealthResultWithinCacheWindow() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        when(virusScanningService.isVirusScanningAvailable()).thenReturn(true);

        SystemMonitor.SystemHealth first = monitor.getSystemHealth();
        SystemMonitor.SystemHealth second = monitor.getSystemHealth();

        assertSame(first, second);
        verify(virusScanningService).isVirusScanningAvailable();

        Field lastHealthCheckTime = SystemMonitor.class.getDeclaredField("lastHealthCheckTime");
        lastHealthCheckTime.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) lastHealthCheckTime.get(monitor)).set(0L);

        SystemMonitor.SystemHealth third = monitor.getSystemHealth();
        verify(virusScanningService, org.mockito.Mockito.times(2)).isVirusScanningAvailable();
        assertTrue(third.getTimestamp() != null && !third.getTimestamp().isBlank());
    }

    @Test
    @DisplayName("returns critical when upload directory does not exist")
    void returnsCriticalWhenUploadDirectoryDoesNotExist() throws Exception {
        properties.setUploadDir("/tmp/path-that-should-not-exist-praxis-monitor-test");
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.CRITICAL, health.getStatus());
        assertTrue(health.getMessage().contains("Critical issues"));
        assertEquals("CRITICAL", ((Map<?, ?>) health.getComponents().get("uploadDirectory")).get("status"));
        verify(virusScanningService, never()).isVirusScanningAvailable();
    }

    @Test
    @DisplayName("returns critical when upload path is not a directory")
    void returnsCriticalWhenUploadPathIsNotDirectory(@TempDir Path tempDir) throws Exception {
        Path regularFile = Files.createFile(tempDir.resolve("uploads-file"));
        properties.setUploadDir(regularFile.toString());
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.CRITICAL, health.getStatus());
        assertEquals("CRITICAL", ((Map<?, ?>) health.getComponents().get("uploadDirectory")).get("status"));
        assertTrue(((Map<?, ?>) health.getComponents().get("uploadDirectory")).get("error").toString().contains("not a directory"));
    }

    @Test
    @DisplayName("returns warning when suspicious security activity exists")
    void returnsWarningWhenSuspiciousSecurityActivityExists() throws Exception {
        java.util.concurrent.ConcurrentHashMap<String, SecurityFailureRegistry.SecurityFailureStats> statsMap =
            new java.util.concurrent.ConcurrentHashMap<>();
        statsMap.put("10.0.0.8", new SecurityFailureRegistry.SecurityFailureStats());
        when(securityFailureRegistry.getAllFailureStats()).thenReturn(statsMap);
        when(securityFailureRegistry.isSuspiciousIp("10.0.0.8")).thenReturn(true);
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.WARNING, health.getStatus());
        assertEquals("WARNING", ((Map<?, ?>) health.getComponents().get("securityMonitoring")).get("status"));
        assertTrue(((Map<?, ?>) health.getComponents().get("securityMonitoring")).get("alert").toString().contains("Suspicious activity"));
    }

    @Test
    @DisplayName("returns warning when virus scanner is unavailable but optional")
    void returnsWarningWhenVirusScannerIsUnavailableButOptional() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        properties.getVirusScanning().setFailOnScannerUnavailable(false);
        when(virusScanningService.isVirusScanningAvailable()).thenReturn(false);
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.WARNING, health.getStatus());
        assertEquals("WARNING", ((Map<?, ?>) health.getComponents().get("virusScanning")).get("status"));
    }

    @Test
    @DisplayName("returns critical when virus scanner is unavailable and mandatory")
    void returnsCriticalWhenVirusScannerIsUnavailableAndMandatory() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        properties.getVirusScanning().setFailOnScannerUnavailable(true);
        when(virusScanningService.isVirusScanningAvailable()).thenReturn(false);
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.CRITICAL, health.getStatus());
        assertEquals("CRITICAL", ((Map<?, ?>) health.getComponents().get("virusScanning")).get("status"));
    }

    @Test
    @DisplayName("returns critical when virus scanning service bean is missing")
    void returnsCriticalWhenVirusScanningServiceBeanIsMissing() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        inject("virusScanningService", null);
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.CRITICAL, health.getStatus());
        assertEquals("CRITICAL", ((Map<?, ?>) health.getComponents().get("virusScanning")).get("status"));
        assertTrue(((Map<?, ?>) health.getComponents().get("virusScanning")).get("error").toString().contains("not available"));
    }

    @Test
    @DisplayName("returns critical when virus scanning health check throws")
    void returnsCriticalWhenVirusScanningHealthCheckThrows() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        when(virusScanningService.isVirusScanningAvailable()).thenThrow(new IllegalStateException("clamav boom"));
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.CRITICAL, health.getStatus());
        assertEquals("CRITICAL", ((Map<?, ?>) health.getComponents().get("virusScanning")).get("status"));
        assertTrue(((Map<?, ?>) health.getComponents().get("virusScanning")).get("error").toString().contains("clamav boom"));
    }

    @Test
    @DisplayName("returns warning when audit directory is not accessible")
    void returnsWarningWhenAuditDirectoryIsNotAccessible() throws Exception {
        properties.getAuditLogging().setEnabled(true);
        Files.createFile(Path.of(properties.getUploadDir()).resolve("security-logs"));
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.WARNING, health.getStatus());
        assertEquals("WARNING", ((Map<?, ?>) health.getComponents().get("auditSystem")).get("status"));
    }

    @Test
    @DisplayName("reports healthy audit configuration when audit logging is enabled")
    void reportsHealthyAuditConfigurationWhenAuditLoggingIsEnabled() throws Exception {
        properties.getAuditLogging().setEnabled(true);
        properties.getAuditLogging().setUseStructuredLogging(false);
        properties.getAuditLogging().setLogSuccessfulUploads(false);
        properties.getAuditLogging().setMinimumSeverity(FileErrorReason.MALWARE_DETECTED.name());
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals("HEALTHY", ((Map<?, ?>) health.getComponents().get("auditSystem")).get("status"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) health.getComponents().get("auditSystem")).get("structuredLogging"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) health.getComponents().get("auditSystem")).get("logSuccessfulUploads"));
    }

    @Test
    @DisplayName("returns critical when security monitoring lookup fails")
    void returnsCriticalWhenSecurityMonitoringLookupFails() throws Exception {
        when(securityFailureRegistry.getTrackedIpCount()).thenThrow(new IllegalStateException("registry boom"));
        resetCache();

        SystemMonitor.SystemHealth health = monitor.getSystemHealth();

        assertEquals(SystemMonitor.SystemStatus.CRITICAL, health.getStatus());
        assertEquals("CRITICAL", ((Map<?, ?>) health.getComponents().get("securityMonitoring")).get("status"));
        assertTrue(((Map<?, ?>) health.getComponents().get("securityMonitoring")).get("error").toString().contains("registry boom"));
    }

    @Test
    @DisplayName("serializes health as json")
    void serializesHealthAsJson() {
        String json = monitor.getSystemHealthJson();
        assertTrue(json.contains("\"status\""));
        assertTrue(json.contains("HEALTHY"));
    }

    @Test
    @DisplayName("returns fallback json when serialization fails")
    void returnsFallbackJsonWhenSerializationFails() throws Exception {
        SystemMonitor.SystemHealth cyclic = new SystemMonitor.SystemHealth();
        cyclic.setStatus(SystemMonitor.SystemStatus.CRITICAL);
        cyclic.setMessage("cyclic");
        Map<String, Object> components = new HashMap<>();
        components.put("self", components);
        cyclic.setComponents(components);

        Field cachedHealth = SystemMonitor.class.getDeclaredField("cachedHealth");
        cachedHealth.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicReference<SystemMonitor.SystemHealth>) cachedHealth.get(monitor)).set(cyclic);

        Field lastHealthCheckTime = SystemMonitor.class.getDeclaredField("lastHealthCheckTime");
        lastHealthCheckTime.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) lastHealthCheckTime.get(monitor))
            .set(System.currentTimeMillis());

        assertEquals(
            "{\"status\":\"CRITICAL\",\"message\":\"Failed to generate health report\"}",
            monitor.getSystemHealthJson()
        );
    }

    @Test
    @DisplayName("logs health summary without throwing")
    void logsHealthSummaryWithoutThrowing() {
        monitor.logSystemHealthSummary();
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = SystemMonitor.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(monitor, value);
    }

    private void resetCache() throws Exception {
        Field cachedHealth = SystemMonitor.class.getDeclaredField("cachedHealth");
        cachedHealth.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicReference<?>) cachedHealth.get(monitor)).set(null);

        Field lastHealthCheckTime = SystemMonitor.class.getDeclaredField("lastHealthCheckTime");
        lastHealthCheckTime.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicLong) lastHealthCheckTime.get(monitor)).set(0L);
    }
}
