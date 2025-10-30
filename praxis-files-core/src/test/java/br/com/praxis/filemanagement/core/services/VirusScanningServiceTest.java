package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.services.FileUploadMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.capybara.clamav.ClamavClient;
import xyz.capybara.clamav.commands.scan.result.ScanResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
}
