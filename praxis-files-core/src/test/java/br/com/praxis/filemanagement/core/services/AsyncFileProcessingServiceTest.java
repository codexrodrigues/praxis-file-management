package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncFileProcessingServiceTest {

    @Mock
    private FileStructureValidator fileStructureValidator;

    @Mock
    private MagicNumberValidator magicNumberValidator;

    @Mock
    private FileIdMappingService fileIdMappingService;

    @Mock
    private VirusScanningService virusScanningService;

    private FileManagementProperties properties;
    private AsyncFileProcessingService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new FileManagementProperties();
        properties.getSecurity().setBlockSuspiciousExtensions(false);
        properties.getSecurity().setMagicNumberValidation(true);
        properties.getSecurity().setMandatoryVirusScan(false);
        properties.getVirusScanning().setEnabled(false);

        service = new AsyncFileProcessingService(
            properties,
            fileStructureValidator,
            magicNumberValidator,
            fileIdMappingService
        );

        Field virusScanningField = AsyncFileProcessingService.class.getDeclaredField("virusScanningService");
        virusScanningField.setAccessible(true);
        virusScanningField.set(service, virusScanningService);

        lenient().when(magicNumberValidator.validateMagicNumbers(any(), anyString(), anyString()))
            .thenReturn(new MagicNumberValidator.ValidationResult(true, "ok", "VALID"));
        lenient().when(fileStructureValidator.validateFileStructure(any(), anyString(), anyString()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        lenient().when(fileIdMappingService.generateFileId(anyString(), anyString(), anyString(), anyLong(), anyString()))
            .thenReturn("generated-id");
    }

    @Test
    @DisplayName("returns success when all async validations pass")
    void returnsSuccessWhenAllAsyncValidationsPass() throws Exception {
        MultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.1", "saved.txt")
            .get(5, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertEquals("saved.txt", result.serverFilename());
        assertEquals("generated-id", result.fileId());
        verify(fileIdMappingService).generateFileId("saved.txt", "report.txt", "/upload", 5L, "text/plain");
    }

    @Test
    @DisplayName("blocks suspicious extension when feature is enabled")
    void blocksSuspiciousExtensionWhenFeatureIsEnabled() throws Exception {
        properties.getSecurity().setBlockSuspiciousExtensions(true);

        MultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", "MZ".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.2", "saved.exe")
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED, result.errorReason());
        verify(magicNumberValidator, never()).validateMagicNumbers(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("maps magic number validation failure to service error")
    void mapsMagicNumberValidationFailureToServiceError() throws Exception {
        when(magicNumberValidator.validateMagicNumbers(any(), anyString(), anyString()))
            .thenReturn(new MagicNumberValidator.ValidationResult(false, "Mismatch", "SIGNATURE_MISMATCH"));

        MultipartFile file = new MockMultipartFile("file", "bad.pdf", "application/pdf", "text".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.3", "saved.pdf")
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(FileErrorReason.SIGNATURE_MISMATCH, result.errorReason());
    }

    @Test
    @DisplayName("fails when structure validation rejects file")
    void failsWhenStructureValidationRejectsFile() throws Exception {
        when(fileStructureValidator.validateFileStructure(any(), anyString(), anyString()))
            .thenReturn(FileStructureValidator.ValidationResult.invalid("ZIP_BOMB", "dangerous structure", "details"));

        MultipartFile file = new MockMultipartFile("file", "archive.zip", "application/zip", "data".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.4", "saved.zip")
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(FileErrorReason.DANGEROUS_FILE_TYPE, result.errorReason());
    }

    @Test
    @DisplayName("fails when mandatory virus scanning is enabled but service is unavailable")
    void failsWhenMandatoryVirusScanningIsEnabledButServiceIsUnavailable() throws Exception {
        properties.getSecurity().setMandatoryVirusScan(true);

        Field virusScanningField = AsyncFileProcessingService.class.getDeclaredField("virusScanningService");
        virusScanningField.setAccessible(true);
        virusScanningField.set(service, null);

        MultipartFile file = new MockMultipartFile("file", "clean.txt", "text/plain", "hello".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.5", "saved.txt")
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(FileErrorReason.VIRUS_SCAN_UNAVAILABLE, result.errorReason());
    }

    @Test
    @DisplayName("fails when virus scanner detects malware")
    void failsWhenVirusScannerDetectsMalware() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        when(virusScanningService.scanFile(any(), anyString()))
            .thenReturn(VirusScanningService.ScanResult.infected("EICAR"));

        MultipartFile file = new MockMultipartFile("file", "infected.txt", "text/plain", "virus".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.6", "saved.txt")
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(FileErrorReason.VIRUS_DETECTED, result.errorReason());
    }

    @Test
    @DisplayName("returns unknown error when validation throws exception")
    void returnsUnknownErrorWhenValidationThrowsException() throws Exception {
        doThrow(new RuntimeException("boom"))
            .when(fileStructureValidator).validateFileStructure(any(), anyString(), anyString());

        MultipartFile file = new MockMultipartFile("file", "error.txt", "text/plain", "hello".getBytes());

        FileUploadResultRecord result = service.validateFileAsync(file, "10.0.0.7", "saved.txt")
            .get(5, TimeUnit.SECONDS);

        assertFalse(result.success());
        assertEquals(FileErrorReason.UNKNOWN_ERROR, result.errorReason());
        assertTrue(result.errorMessage().contains("boom"));
    }

    @Test
    @DisplayName("processes multiple files and invokes callback for each result")
    void processesMultipleFilesAndInvokesCallbackForEachResult() throws Exception {
        MultipartFile ok = new MockMultipartFile("file", "ok.txt", "text/plain", "ok".getBytes());
        MultipartFile bad = new MockMultipartFile("file", "bad.exe", "application/octet-stream", "bad".getBytes());
        properties.getSecurity().setBlockSuspiciousExtensions(true);

        List<FileUploadResultRecord> results = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> future = service.processMultipleFilesAsync(
            new MultipartFile[]{ok, bad},
            "10.0.0.8",
            results::add
        );

        future.get(5, TimeUnit.SECONDS);

        long deadline = System.currentTimeMillis() + 5000;
        while (results.size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }

        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(FileUploadResultRecord::success));
        assertTrue(results.stream().anyMatch(r -> !r.success() && r.errorReason() == FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED));
    }

    @Test
    @DisplayName("processMultipleFilesAsync normalizes empty original filename")
    void processMultipleFilesAsyncNormalizesEmptyOriginalFilename() throws Exception {
        MultipartFile broken = new MockMultipartFile("file", null, "text/plain", "x".getBytes());
        List<FileUploadResultRecord> results = new CopyOnWriteArrayList<>();

        CompletableFuture<Void> future = service.processMultipleFilesAsync(
            new MultipartFile[]{broken},
            "10.0.0.9",
            results::add
        );

        future.get(5, TimeUnit.SECONDS);

        long deadline = System.currentTimeMillis() + 5000;
        while (results.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }

        assertEquals(1, results.size());
        assertTrue(results.get(0).success());
        assertEquals("unknown", results.get(0).originalFilename());
        assertNotNull(results.get(0).serverFilename());
    }
}
