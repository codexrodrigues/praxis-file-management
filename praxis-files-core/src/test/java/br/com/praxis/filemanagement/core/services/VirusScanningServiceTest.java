package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.FileUploadMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.ClamavException;
import xyz.capybara.clamav.commands.scan.result.ScanResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VirusScanningServiceTest {

    @Mock
    private ClamavClient clamavClient;

    @Mock
    private FileUploadMetricsService metricsService;

    private VirusScanningService service;
    private FileManagementProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = new FileManagementProperties();
        FileManagementProperties.VirusScanning cfg = properties.getVirusScanning();
        cfg.setEnabled(true);
        cfg.setReadTimeout(50); // short timeout for tests
        cfg.setConnectionTimeout(50);
        cfg.setFailOnScannerUnavailable(false);

        service = new VirusScanningService();
        var field = VirusScanningService.class.getDeclaredField("properties");
        field.setAccessible(true);
        field.set(service, properties);
        field = VirusScanningService.class.getDeclaredField("metricsService");
        field.setAccessible(true);
        field.set(service, metricsService);
    }

    @Test
    void returnsCleanWhenClientUnavailableAndFailOnUnavailableFalse() {
        service.setClamavClientForTesting(null);

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isClean());
        verify(metricsService).recordSecurityRejection("av_unavailable_fallback");
    }

    @Test
    void returnsErrorWhenClientUnavailableAndFailOnUnavailableTrue() {
        properties.getVirusScanning().setFailOnScannerUnavailable(true);
        service.setClamavClientForTesting(null);

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isError());
        verify(metricsService).recordSecurityRejection("av_unavailable");
    }

    @Test
    void returnsCleanOnScanTimeoutWhenFailOnUnavailableFalse() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.scan(any(InputStream.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(properties.getVirusScanning().getReadTimeout() * 2L);
            } catch (InterruptedException ignored) {
            }
            return mock(ScanResult.class);
        });

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isClean());
        verify(metricsService).recordSecurityRejection("av_unavailable_fallback");
    }

    @Test
    void returnsErrorOnScanTimeoutWhenFailOnUnavailableTrue() throws Exception {
        properties.getVirusScanning().setFailOnScannerUnavailable(true);
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.scan(any(InputStream.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(properties.getVirusScanning().getReadTimeout() * 2L);
            } catch (InterruptedException ignored) {
            }
            return mock(ScanResult.class);
        });

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isError());
        verify(metricsService).recordSecurityRejection("av_unavailable");
    }

    @Test
    void quarantinesInfectedFileWhenEnabled() throws Exception {
        properties.getVirusScanning().setQuarantineEnabled(true);
        properties.getVirusScanning().setReadTimeout(500);
        Path quarantine = Paths.get("build/quarantine-test");
        properties.getVirusScanning().setQuarantineDir(quarantine.toString());

        service.setClamavClientForTesting(clamavClient);
        ScanResult.VirusFound virusFound = mock(ScanResult.VirusFound.class);
        when(virusFound.getFoundViruses()).thenReturn(Map.of("stream", List.of("EICAR")));
        when(clamavClient.scan(any(InputStream.class))).thenReturn(virusFound);

        VirusScanningService.ScanResult result = service.scanFile("bad".getBytes(), "bad.txt");

        assertFalse(result.isClean());
        assertTrue(Files.exists(quarantine));
        try (var files = Files.list(quarantine)) {
            assertTrue(files.findAny().isPresent());
        }
    }

    @Test
    void returnsCleanWhenScanningDisabled() {
        properties.getVirusScanning().setEnabled(false);

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isClean());
        verifyNoInteractions(metricsService);
    }

    @Test
    void returnsErrorForEmptyFile() {
        VirusScanningService.ScanResult result = service.scanFile(new byte[0], "empty.txt");

        assertTrue(result.isError());
        assertEquals("Cannot scan empty file", result.getErrorMessage().orElseThrow());
    }

    @Test
    void returnsErrorForNullFileBytes() {
        VirusScanningService.ScanResult result = service.scanFile(null, "null.txt");

        assertTrue(result.isError());
        assertEquals("Cannot scan empty file", result.getErrorMessage().orElseThrow());
    }

    @Test
    void returnsErrorForOversizedFile() {
        properties.getVirusScanning().setMaxFileSizeBytes(2);

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "big.txt");

        assertTrue(result.isError());
        assertEquals("File too large for virus scanning", result.getErrorMessage().orElseThrow());
    }

    @Test
    void returnsCleanWhenClamavReportsOk() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.scan(any(InputStream.class))).thenReturn(mock(ScanResult.OK.class));

        VirusScanningService.ScanResult result = service.scanFile("clean".getBytes(), "clean.txt");

        assertTrue(result.isClean());
        assertFalse(result.isError());
    }

    @Test
    void returnsErrorForUnknownScanResultType() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.scan(any(InputStream.class))).thenReturn(mock(ScanResult.class));

        VirusScanningService.ScanResult result = service.scanFile("mystery".getBytes(), "mystery.txt");

        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().orElseThrow().contains("Unknown scan result type"));
    }

    @Test
    void returnsUnknownVirusWhenInfectedResultHasNoEntries() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        ScanResult.VirusFound virusFound = mock(ScanResult.VirusFound.class);
        when(virusFound.getFoundViruses()).thenReturn(Map.of("stream", List.of()));
        when(clamavClient.scan(any(InputStream.class))).thenReturn(virusFound);

        VirusScanningService.ScanResult result = service.scanFile("bad".getBytes(), "bad.txt");

        assertFalse(result.isClean());
        assertEquals("Unknown virus", result.getVirusName().orElseThrow());
    }

    @Test
    void returnsUnknownVirusWhenInfectedResultHasNullMap() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        ScanResult.VirusFound virusFound = mock(ScanResult.VirusFound.class);
        when(virusFound.getFoundViruses()).thenReturn(null);
        when(clamavClient.scan(any(InputStream.class))).thenReturn(virusFound);

        VirusScanningService.ScanResult result = service.scanFile("bad".getBytes(), "bad.txt");

        assertFalse(result.isClean());
        assertEquals("Unknown virus", result.getVirusName().orElseThrow());
    }

    @Test
    void skipsNullVirusCollectionsWhenExtractingVirusName() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        ScanResult.VirusFound virusFound = mock(ScanResult.VirusFound.class);
        @SuppressWarnings("unchecked")
        Map<String, Collection<String>> viruses = mock(Map.class);
        when(viruses.isEmpty()).thenReturn(false);
        Collection<Collection<String>> virusCollections = new ArrayList<>();
        virusCollections.add(null);
        virusCollections.add(List.of("Trojan.Test"));
        when(viruses.values()).thenReturn(virusCollections);
        when(virusFound.getFoundViruses()).thenReturn(viruses);
        when(clamavClient.scan(any(InputStream.class))).thenReturn(virusFound);

        VirusScanningService.ScanResult result = service.scanFile("bad".getBytes(), "bad.txt");

        assertFalse(result.isClean());
        assertEquals("Trojan.Test", result.getVirusName().orElseThrow());
    }

    @Test
    void returnsErrorForUnexpectedScanException() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.scan(any(InputStream.class))).thenThrow(new RuntimeException("socket reset"));

        VirusScanningService.ScanResult result = service.scanFile("boom".getBytes(), "boom.txt");

        assertTrue(result.isError());
        assertTrue(result.getErrorMessage().orElseThrow().contains("Unexpected error during virus scan"));
        assertTrue(result.getErrorMessage().orElseThrow().contains("Scan failed"));
    }

    @Test
    void reportsVersionAndHealthInformation() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.isReachable(anyInt())).thenReturn(true);
        when(clamavClient.version()).thenReturn("ClamAV 1.2.3");

        assertTrue(service.isVirusScanningAvailable());
        assertTrue(service.isHealthy());
        assertEquals("ClamAV 1.2.3", service.getClamavVersion());
        assertEquals("ClamAV connected - Version: ClamAV 1.2.3", service.getHealthDetails());
    }

    @Test
    void handlesVersionAndHealthFailuresGracefully() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.isReachable(anyInt())).thenReturn(false);
        when(clamavClient.version()).thenThrow(new ClamavException(new RuntimeException("version failed")));

        assertFalse(service.isHealthy());
        assertEquals("Version check failed: java.lang.RuntimeException: version failed", service.getClamavVersion());
        assertEquals("ClamAV unreachable", service.getHealthDetails());
    }

    @Test
    void reportsClientUnavailableInHealthDetails() {
        service.setClamavClientForTesting(null);

        assertFalse(service.isVirusScanningAvailable());
        assertFalse(service.isHealthy());
        assertEquals("ClamAV client not available", service.getClamavVersion());
        assertEquals("ClamAV client not available", service.getHealthDetails());
    }

    @Test
    void allowsUnavailableScannerWithoutMetricsService() {
        service = new VirusScanningService();
        service.setClamavClientForTesting(null);
        setField("properties", properties);
        setField("metricsService", null);

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isClean());
    }

    @Test
    void allowsTimeoutWithoutMetricsService() throws Exception {
        service = new VirusScanningService();
        setField("properties", properties);
        setField("metricsService", null);
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.scan(any(InputStream.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(properties.getVirusScanning().getReadTimeout() * 2L);
            } catch (InterruptedException ignored) {
            }
            return mock(ScanResult.class);
        });

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isClean());
    }

    @Test
    void returnsErrorWhenScannerUnavailableWithoutMetricsServiceAndFailFast() {
        properties.getVirusScanning().setFailOnScannerUnavailable(true);
        service = new VirusScanningService();
        setField("properties", properties);
        setField("metricsService", null);
        service.setClamavClientForTesting(null);

        VirusScanningService.ScanResult result = service.scanFile("data".getBytes(), "file.txt");

        assertTrue(result.isError());
        assertEquals("ClamAV client not available", result.getErrorMessage().orElseThrow());
    }

    @Test
    void returnsDisabledHealthDetailsWhenFeatureIsOff() {
        properties.getVirusScanning().setEnabled(false);
        assertEquals("Virus scanning disabled", service.getHealthDetails());
    }

    @Test
    void invalidConfigurationPreventsLazyClientInitialization() {
        properties.getVirusScanning().setClamavHost(" ");

        assertFalse(service.isVirusScanningAvailable());
        assertEquals("ClamAV client not available", service.getClamavVersion());
        assertEquals("ClamAV client not available", service.getHealthDetails());
    }

    @Test
    void healthDetailsIncludesVersionFallbackWhenHealthyButVersionFails() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        when(clamavClient.isReachable(anyInt())).thenReturn(true);
        when(clamavClient.version()).thenThrow(new ClamavException(new RuntimeException("broken version")));

        assertEquals(
            "ClamAV connected - Version: Version check failed: java.lang.RuntimeException: broken version",
            service.getHealthDetails()
        );
    }

    @Test
    void cleanupClearsInjectedClient() throws Exception {
        service.setClamavClientForTesting(clamavClient);
        service.cleanup();

        var clientField = VirusScanningService.class.getDeclaredField("clamavClient");
        clientField.setAccessible(true);
        var initializedField = VirusScanningService.class.getDeclaredField("clientInitialized");
        initializedField.setAccessible(true);

        assertNull(clientField.get(service));
        assertFalse((Boolean) initializedField.get(service));
    }

    @Test
    void cleanupWithoutClientIsNoop() {
        assertDoesNotThrow(() -> service.cleanup());
    }

    @Test
    void quarantineFailureDoesNotBreakInfectedResult(@TempDir Path tempDir) throws Exception {
        properties.getVirusScanning().setQuarantineEnabled(true);
        Path blockingFile = Files.createFile(tempDir.resolve("not-a-directory"));
        properties.getVirusScanning().setQuarantineDir(blockingFile.toString());
        service.setClamavClientForTesting(clamavClient);

        ScanResult.VirusFound virusFound = mock(ScanResult.VirusFound.class);
        when(virusFound.getFoundViruses()).thenReturn(Map.of("stream", List.of("EICAR")));
        when(clamavClient.scan(any(InputStream.class))).thenReturn(virusFound);

        VirusScanningService.ScanResult result = service.scanFile("bad".getBytes(), "bad?.txt");

        assertFalse(result.isClean());
        assertEquals("EICAR", result.getVirusName().orElseThrow());
        verify(metricsService, never()).recordSecurityRejection("malware_quarantined");
    }

    @Test
    void scanResultFactoriesExposeExpectedFlags() {
        VirusScanningService.ScanResult clean = VirusScanningService.ScanResult.clean();
        VirusScanningService.ScanResult infected = VirusScanningService.ScanResult.infected("EICAR");
        VirusScanningService.ScanResult error = VirusScanningService.ScanResult.error("boom");

        assertTrue(clean.isClean());
        assertTrue(clean.getVirusName().isEmpty());
        assertTrue(clean.getErrorMessage().isEmpty());
        assertFalse(clean.isError());

        assertFalse(infected.isClean());
        assertEquals("EICAR", infected.getVirusName().orElseThrow());
        assertFalse(infected.isError());

        assertFalse(error.isClean());
        assertEquals("boom", error.getErrorMessage().orElseThrow());
        assertTrue(error.isError());
    }

    private void setField(String fieldName, Object value) {
        try {
            var field = VirusScanningService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
