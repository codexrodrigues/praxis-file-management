package br.com.praxis.filemanagement.core.config;

import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileManagementPropertiesTest {

    @Test
    void exposesDefaultsAndConvenienceMethods() {
        FileManagementProperties properties = new FileManagementProperties();

        assertEquals("./uploads", properties.getUploadDir());
        assertEquals(System.getProperty("java.io.tmpdir"), properties.getUploadTempDir());
        assertEquals(NameConflictPolicy.MAKE_UNIQUE, properties.getDefaultConflictPolicy());
        assertEquals(properties.getVirusScanning().getMaxFileSizeBytes(), properties.getMaxFileSize());
        assertEquals(List.of("jpg", "jpeg", "png", "gif", "pdf", "txt", "csv"), properties.getAllowedFileTypes());
        assertTrue(properties.getSecurity().getDangerousMimeTypes().contains("text/html"));
        assertTrue(properties.getSecurity().getSuspiciousExtensions().contains("exe"));
    }

    @Test
    void allowedFileTypesIsEmptyWhenMimeWhitelistConfigured() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.getSecurity().setAllowedMimeTypes(Set.of("text/plain"));

        assertTrue(properties.getAllowedFileTypes().isEmpty());
    }

    @Test
    void validateConfigurationNormalizesDefaultsAndValidatesNestedBlocks() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.setUploadDir("/tmp/uploads");
        properties.setUploadTempDir("   ");
        properties.setDefaultConflictPolicy(null);
        properties.setMaxFileSizeBytes(1024);

        FileManagementProperties.RateLimit rateLimit = new FileManagementProperties.RateLimit();
        rateLimit.setMaxUploadsPerMinute(5);
        rateLimit.setMaxUploadsPerHour(10);
        properties.setRateLimit(rateLimit);

        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setScriptDetectionBufferSize(2048);
        security.setMaxFilenameLength(128);
        security.setContentTypeValidation("strict");
        properties.setSecurity(security);

        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(true);
        virusScanning.setClamavHost("clamav");
        virusScanning.setClamavPort(3310);
        properties.setVirusScanning(virusScanning);

        FileManagementProperties.AuditLogging auditLogging = new FileManagementProperties.AuditLogging();
        auditLogging.setMinimumSeverity("MEDIUM");
        properties.setAuditLogging(auditLogging);

        FileManagementProperties.Monitoring monitoring = new FileManagementProperties.Monitoring();
        monitoring.setHealthCheckCacheSeconds(30);
        properties.setMonitoring(monitoring);

        FileManagementProperties.Quota quota = new FileManagementProperties.Quota();
        quota.setTenant(Map.of("tenant-a", 2));
        quota.setUser(Map.of("user-a", 3));
        properties.setQuota(quota);

        FileManagementProperties.BulkUpload bulkUpload = new FileManagementProperties.BulkUpload();
        bulkUpload.setMaxConcurrentUploads(2);
        bulkUpload.setMaxFilesPerBatch(3);
        properties.setBulkUpload(bulkUpload);

        properties.validateConfiguration();

        assertEquals(System.getProperty("java.io.tmpdir"), properties.getUploadTempDir());
        assertEquals(NameConflictPolicy.MAKE_UNIQUE, properties.getDefaultConflictPolicy());
    }

    @Test
    void validateConfigurationRejectsInvalidRootSettings() {
        FileManagementProperties properties = new FileManagementProperties();
        properties.setUploadDir(" ");
        assertThrows(IllegalArgumentException.class, properties::validateConfiguration);

        properties.setUploadDir("/tmp/uploads");
        properties.setMaxFileSizeBytes(0);
        assertThrows(IllegalArgumentException.class, properties::validateConfiguration);
    }

    @Test
    void securityValidationRejectsInvalidValues() {
        FileManagementProperties.Security security = new FileManagementProperties.Security();

        security.setScriptDetectionBufferSize(0);
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setScriptDetectionBufferSize(70000);
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setScriptDetectionBufferSize(1024);
        security.setMaxFilenameLength(0);
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setMaxFilenameLength(1001);
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setMaxFilenameLength(255);
        security.setContentTypeValidation("weird");
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setContentTypeValidation("strict");
        security.setUseMimeWhitelist(true);
        security.setAllowedMimeTypes(Set.of());
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setAllowedMimeTypes(Set.of("text/plain"));
        security.setMandatoryVirusScan(true);
        security.setDeepContentInspection(false);
        assertThrows(IllegalArgumentException.class, security::validate);

        security.setDeepContentInspection(true);
        assertDoesNotThrow(security::validate);
    }

    @Test
    void rateLimitValidationRejectsInvalidValues() {
        FileManagementProperties.RateLimit rateLimit = new FileManagementProperties.RateLimit();

        rateLimit.setMaxUploadsPerMinute(0);
        assertThrows(IllegalArgumentException.class, rateLimit::validate);

        rateLimit.setMaxUploadsPerMinute(5);
        rateLimit.setMaxUploadsPerHour(0);
        assertThrows(IllegalArgumentException.class, rateLimit::validate);

        rateLimit.setMaxUploadsPerHour(10);
        rateLimit.setMaxConcurrentUploads(0);
        assertThrows(IllegalArgumentException.class, rateLimit::validate);

        rateLimit.setMaxConcurrentUploads(2);
        rateLimit.setEnablePerIpLimiting(true);
        rateLimit.setMaxUploadsPerIpPerHour(0);
        assertThrows(IllegalArgumentException.class, rateLimit::validate);

        rateLimit.setMaxUploadsPerIpPerHour(4);
        rateLimit.setMaxUploadsPerMinute(11);
        rateLimit.setMaxUploadsPerHour(10);
        assertThrows(IllegalArgumentException.class, rateLimit::validate);

        rateLimit.setMaxUploadsPerMinute(5);
        rateLimit.setTrustedProxies(List.of("127.0.0.1"));
        assertDoesNotThrow(rateLimit::validate);
        assertEquals(List.of("127.0.0.1"), rateLimit.getTrustedProxies());
        assertTrue(rateLimit.isEnablePerIpLimiting());
    }

    @Test
    void quotaValidationRejectsNonPositiveValues() {
        FileManagementProperties.Quota quota = new FileManagementProperties.Quota();
        quota.setEnabled(true);
        quota.setTenant(Map.of("tenant-a", 0));
        assertThrows(IllegalArgumentException.class, quota::validate);

        quota.setTenant(Map.of("tenant-a", 1));
        quota.setUser(Map.of("user-a", -1));
        assertThrows(IllegalArgumentException.class, quota::validate);

        quota.setUser(Map.of("user-a", 2));
        assertDoesNotThrow(quota::validate);
        assertTrue(quota.isEnabled());
    }

    @Test
    void virusScanningValidationRejectsInvalidValues() {
        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(true);
        virusScanning.setClamavHost("clamav");
        virusScanning.setQuarantineEnabled(true);
        virusScanning.setQuarantineDir("/tmp/quarantine");
        virusScanning.setLogScanResults(true);
        virusScanning.setRequireFreshSignatures(true);
        virusScanning.setMaxScanRetries(2);

        virusScanning.setConnectionTimeout(-1);
        assertThrows(IllegalArgumentException.class, virusScanning::validate);

        virusScanning.setConnectionTimeout(10);
        virusScanning.setReadTimeout(-1);
        assertThrows(IllegalArgumentException.class, virusScanning::validate);

        virusScanning.setReadTimeout(10);
        virusScanning.setMaxFileSizeBytes(-1);
        assertThrows(IllegalArgumentException.class, virusScanning::validate);

        virusScanning.setMaxFileSizeBytes(10);
        virusScanning.setClamavPort(0);
        assertThrows(IllegalArgumentException.class, virusScanning::validate);

        virusScanning.setClamavPort(3310);
        virusScanning.setClamavHost(" ");
        assertThrows(IllegalArgumentException.class, virusScanning::validate);

        virusScanning.setClamavHost("clamav");
        assertDoesNotThrow(virusScanning::validate);
        assertTrue(virusScanning.isEnabled());
        assertTrue(virusScanning.isQuarantineEnabled());
        assertTrue(virusScanning.isLogScanResults());
        assertTrue(virusScanning.isRequireFreshSignatures());
        assertEquals(2, virusScanning.getMaxScanRetries());
        assertEquals("/tmp/quarantine", virusScanning.getQuarantineDir());
    }

    @Test
    void auditLoggingValidationRejectsInvalidValues() {
        FileManagementProperties.AuditLogging auditLogging = new FileManagementProperties.AuditLogging();
        auditLogging.setEnabled(false);
        auditLogging.setLogSuccessfulUploads(false);
        auditLogging.setLogAllSecurityEvents(false);
        auditLogging.setIncludeSensitiveData(false);
        auditLogging.setUseStructuredLogging(false);
        auditLogging.setLogRateLimitViolations(true);
        auditLogging.setLogVirusScanResults(true);
        auditLogging.setLogFileAccess(true);

        auditLogging.setMaxErrorMessageLength(99);
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setMaxErrorMessageLength(10001);
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setMaxErrorMessageLength(1000);
        auditLogging.setMinimumSeverity("TRACE");
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setMinimumSeverity("HIGH");
        auditLogging.setCleanupIntervalMinutes(0);
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setCleanupIntervalMinutes(1441);
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setCleanupIntervalMinutes(60);
        auditLogging.setStatisticsRetentionHours(0);
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setStatisticsRetentionHours(169);
        assertThrows(IllegalArgumentException.class, auditLogging::validate);

        auditLogging.setStatisticsRetentionHours(24);
        assertDoesNotThrow(auditLogging::validate);
        assertFalse(auditLogging.isEnabled());
        assertFalse(auditLogging.isLogSuccessfulUploads());
        assertFalse(auditLogging.isLogAllSecurityEvents());
        assertFalse(auditLogging.isIncludeSensitiveData());
        assertFalse(auditLogging.isUseStructuredLogging());
        assertTrue(auditLogging.isLogRateLimitViolations());
        assertTrue(auditLogging.isLogVirusScanResults());
        assertTrue(auditLogging.isLogFileAccess());
    }

    @Test
    void monitoringValidationRejectsInvalidValues() {
        FileManagementProperties.Monitoring monitoring = new FileManagementProperties.Monitoring();
        monitoring.setEnabled(false);
        monitoring.setIncludeDetailedMetrics(false);
        monitoring.setLogHealthCheckAccess(true);
        monitoring.setTrackUploadPerformance(true);
        monitoring.setAlertOnHighErrorRate(true);
        monitoring.setMonitorVirusScannerHealth(true);
        monitoring.setRealTimeAlerting(true);
        monitoring.setRequiredRole("OPS");
        monitoring.setScannerHealthCheckInterval(120);

        monitoring.setHealthCheckCacheSeconds(-1);
        assertThrows(IllegalArgumentException.class, monitoring::validate);

        monitoring.setHealthCheckCacheSeconds(301);
        assertThrows(IllegalArgumentException.class, monitoring::validate);

        monitoring.setHealthCheckCacheSeconds(60);
        assertDoesNotThrow(monitoring::validate);
        assertFalse(monitoring.isEnabled());
        assertFalse(monitoring.isIncludeDetailedMetrics());
        assertTrue(monitoring.isLogHealthCheckAccess());
        assertTrue(monitoring.isTrackUploadPerformance());
        assertTrue(monitoring.isAlertOnHighErrorRate());
        assertTrue(monitoring.isMonitorVirusScannerHealth());
        assertTrue(monitoring.isRealTimeAlerting());
        assertEquals("OPS", monitoring.getRequiredRole());
        assertEquals(120, monitoring.getScannerHealthCheckInterval());
    }

    @Test
    void bulkUploadValidationRejectsInvalidValues() {
        FileManagementProperties.BulkUpload bulkUpload = new FileManagementProperties.BulkUpload();
        bulkUpload.setEnableParallelProcessing(false);
        bulkUpload.setFailFastMode(true);
        bulkUpload.setEnableBulkRateLimit(true);

        bulkUpload.setMaxTotalSizeMb(0);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxTotalSizeMb(1025);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxTotalSizeMb(100);
        bulkUpload.setMaxConcurrentUploads(0);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxConcurrentUploads(21);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxConcurrentUploads(2);
        bulkUpload.setTimeoutSeconds(0);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setTimeoutSeconds(3601);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setTimeoutSeconds(60);
        bulkUpload.setMaxFilesPerBatch(0);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxFilesPerBatch(101);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxFilesPerBatch(1);
        bulkUpload.setMaxConcurrentUploads(2);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxFilesPerBatch(3);
        bulkUpload.setEnableBulkRateLimit(true);
        bulkUpload.setMaxBulkUploadsPerHour(0);
        assertThrows(IllegalArgumentException.class, bulkUpload::validate);

        bulkUpload.setMaxBulkUploadsPerHour(2);
        assertDoesNotThrow(bulkUpload::validate);
        assertFalse(bulkUpload.isEnableParallelProcessing());
        assertTrue(bulkUpload.isFailFastMode());
        assertTrue(bulkUpload.isEnableBulkRateLimit());
    }
}
