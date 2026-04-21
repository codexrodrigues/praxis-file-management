package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.dtos.BulkUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import br.com.praxis.filemanagement.core.audit.SecurityAuditLogger;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.exception.FileSizeLimitExceededException;
import br.com.praxis.filemanagement.core.utils.FileNameHandler;
import br.com.praxis.filemanagement.core.validation.InputValidationService;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalStorageFileServiceTest {

    @Mock
    private MagicNumberValidator magicNumberValidator;
    @Mock
    private FileUploadMetricsService metricsService;
    @Mock
    private ThreadSafeFileNamingService fileNamingService;
    @Mock
    private FileIdMappingService fileIdMappingService;
    @Mock
    private AtomicFileOperationService atomicFileOperationService;
    @Mock
    private InputValidationService inputValidationService;
    @Mock
    private VirusScanningService virusScanningService;
    @Mock
    private FileStructureValidator fileStructureValidator;
    @Mock
    private SecurityAuditLogger securityAuditLogger;
    @Mock
    private UploadAttemptTracker uploadAttemptTracker;
    @Mock
    private Timer.Sample timerSample;
    @Mock
    private HttpServletRequest request;

    @TempDir
    Path tempDir;

    private LocalStorageFileService service;
    private FileManagementProperties properties;

    @BeforeEach
    void setUp() {
        service = new LocalStorageFileService();
        properties = new FileManagementProperties();
        properties.setUploadDir(tempDir.resolve("uploads").toString());
        properties.setUploadTempDir(tempDir.resolve("tmp").toString());
        properties.getSecurity().setMagicNumberValidation(false);
        properties.getSecurity().setBlockSuspiciousExtensions(false);
        properties.getVirusScanning().setEnabled(false);

        ReflectionTestUtils.setField(service, "magicNumberValidator", magicNumberValidator);
        ReflectionTestUtils.setField(service, "fileManagementProperties", properties);
        ReflectionTestUtils.setField(service, "metricsService", metricsService);
        ReflectionTestUtils.setField(service, "fileNamingService", fileNamingService);
        ReflectionTestUtils.setField(service, "fileIdMappingService", fileIdMappingService);
        ReflectionTestUtils.setField(service, "atomicFileOperationService", atomicFileOperationService);
        ReflectionTestUtils.setField(service, "inputValidationService", inputValidationService);
        ReflectionTestUtils.setField(service, "virusScanningService", virusScanningService);
        ReflectionTestUtils.setField(service, "fileStructureValidator", fileStructureValidator);
        ReflectionTestUtils.setField(service, "securityAuditLogger", securityAuditLogger);
        ReflectionTestUtils.setField(service, "uploadAttemptTracker", uploadAttemptTracker);
        ReflectionTestUtils.setField(service, "request", null);

        lenient().when(metricsService.startUploadTimer()).thenReturn(timerSample);
        lenient().when(inputValidationService.validateUploadFile(any(), any())).thenReturn(InputValidationService.ValidationResult.success());
        lenient().when(fileStructureValidator.validateFileStructure(any(), any(), any())).thenReturn(FileStructureValidator.ValidationResult.valid());
    }

    @Test
    void rejectsGloballyBlockedMimeType() throws Exception {
        properties.getSecurity().setAllowedMimeTypes(java.util.Set.of("application/pdf"));
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello world".getBytes());

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.INVALID_TYPE, result.errorReason());
        verify(metricsService).recordSecurityRejection("blocked_mime_type");
        verify(uploadAttemptTracker).recordFailedUpload(eq("unknown"), eq("note.txt"), eq(FileErrorReason.INVALID_TYPE));
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWhenMandatoryVirusScanningServiceIsUnavailable() throws Exception {
        properties.getSecurity().setMandatoryVirusScan(true);
        ReflectionTestUtils.setField(service, "virusScanningService", null);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello world".getBytes());

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.VIRUS_SCAN_UNAVAILABLE, result.errorReason());
        verify(metricsService).recordSecurityRejection("mandatory_virus_scan_unavailable");
        verify(uploadAttemptTracker).recordFailedUpload(eq("unknown"), eq("note.txt"), eq(FileErrorReason.VIRUS_SCAN_UNAVAILABLE));
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void mapsStructureValidationFailureToSuspiciousStructure() throws Exception {
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
                .thenReturn(FileStructureValidator.ValidationResult.invalid(
                        "MISSING_MANIFEST", "manifest missing", "jar without manifest"));
        MockMultipartFile file = new MockMultipartFile("file", "archive.jar", "application/java-archive", "hello world".getBytes());

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.SUSPICIOUS_STRUCTURE, result.errorReason());
        assertEquals("manifest missing", result.errorMessage());
        verify(metricsService).recordSecurityRejection("structure_validation");
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void returnsFileExistsForConflictPolicyError() throws Exception {
        Files.createDirectories(tempDir.resolve("uploads"));
        Files.writeString(tempDir.resolve("uploads").resolve("existing.txt"), "already here");
        MockMultipartFile file = new MockMultipartFile("file", "existing.txt", "text/plain", "hello world".getBytes());
        FileUploadOptionsRecord options = new FileUploadOptionsRecord(
                java.util.List.of(),
                java.util.List.of(),
                NameConflictPolicy.ERROR,
                null,
                false,
                null,
                false,
                java.util.Map.of(),
                false);

        FileUploadResultRecord result = service.uploadFile(file, options);

        assertFalse(result.success());
        assertEquals(FileErrorReason.FILE_EXISTS, result.errorReason());
        verify(uploadAttemptTracker).recordFailedUpload(eq("unknown"), eq("existing.txt"), eq(FileErrorReason.FILE_EXISTS));
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
        verify(fileIdMappingService, never()).generateFileId(any(), any(), any(), anyLong(), any());
    }

    @Test
    void uploadsSuccessfullyWithRenamePolicyAndReleasesReservation() throws Exception {
        properties.getSecurity().setMagicNumberValidation(true);
        properties.getVirusScanning().setEnabled(true);
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", "hello world".getBytes());
        FileUploadOptionsRecord options = new FileUploadOptionsRecord(
                List.of("txt"),
                List.of("text/plain"),
                NameConflictPolicy.RENAME,
                null,
                true,
                null,
                false,
                Map.of(),
                false);

        when(magicNumberValidator.validateMagicNumbers(any(), eq("text/plain"), eq("text/plain")))
                .thenReturn(new MagicNumberValidator.ValidationResult(true, "VALID", "ok"));
        when(virusScanningService.scanFile(any(), eq("report.txt")))
                .thenReturn(VirusScanningService.ScanResult.clean());
        when(fileNamingService.generateUniqueNameForRename(eq("report.txt"), any()))
                .thenReturn(new ThreadSafeFileNamingService.UniqueNameResult(
                        "report (1).txt",
                        FileNameHandler.decompose("report (1).txt"),
                        2,
                        ThreadSafeFileNamingService.NamingStrategy.INCREMENTAL));
        when(atomicFileOperationService.executeAtomicUpload(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AtomicFileOperationService.UploadTransaction());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any())).thenReturn("file-id");

        FileUploadResultRecord result = service.uploadFile(file, options);

        assertTrue(result.success());
        assertEquals("report.txt", result.originalFilename());
        assertEquals("report (1).txt", result.serverFilename());
        assertEquals("file-id", result.fileId());
        verify(fileNamingService).releaseReservedName(any(), eq("report (1).txt"));
        verify(uploadAttemptTracker).recordSuccessfulUpload(eq("unknown"), eq("report.txt"), eq(file.getSize()));
        verify(metricsService).recordSuccessfulUpload(timerSample);
    }

    @Test
    void rejectsMagicNumberValidationFailure() throws Exception {
        properties.getSecurity().setMagicNumberValidation(true);
        MockMultipartFile file = new MockMultipartFile("file", "script.txt", "text/plain", "hello world".getBytes());
        when(magicNumberValidator.validateMagicNumbers(any(), eq("text/plain"), eq("text/plain")))
                .thenReturn(new MagicNumberValidator.ValidationResult(false, "danger", "DANGEROUS_SCRIPT"));

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.DANGEROUS_SCRIPT, result.errorReason());
        assertEquals("danger", result.errorMessage());
        verify(metricsService).recordSecurityRejection("magic_number_validation");
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMalwareDetection() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        MockMultipartFile file = new MockMultipartFile("file", "infected.txt", "text/plain", "hello world".getBytes());
        when(virusScanningService.scanFile(any(), eq("infected.txt")))
                .thenReturn(VirusScanningService.ScanResult.infected("EICAR"));

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.MALWARE_DETECTED, result.errorReason());
        verify(metricsService).recordSecurityRejection("malware_detected");
        verify(securityAuditLogger).logMalwareDetected(eq("unknown"), eq("infected.txt"), eq(file.getSize()), eq("EICAR"), eq("ClamAV"));
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsVirusScanError() throws Exception {
        properties.getVirusScanning().setEnabled(true);
        MockMultipartFile file = new MockMultipartFile("file", "infected.txt", "text/plain", "hello world".getBytes());
        when(virusScanningService.scanFile(any(), eq("infected.txt")))
                .thenReturn(VirusScanningService.ScanResult.error("scanner down"));

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.UNKNOWN_ERROR, result.errorReason());
        assertTrue(result.errorMessage().contains("scanner down"));
        verify(metricsService).recordSecurityRejection("virus_scan_error");
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsSuspiciousExtensionWhenBlockingIsEnabled() throws Exception {
        properties.getSecurity().setBlockSuspiciousExtensions(true);
        MockMultipartFile file = new MockMultipartFile("file", "script.ps1", "text/plain", "hello world".getBytes());

        FileUploadResultRecord result = service.uploadFile(file, FileUploadOptionsRecord.defaultOptions());

        assertFalse(result.success());
        assertEquals(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED, result.errorReason());
        verify(metricsService).recordSecurityRejection("suspicious_extension_blocked");
        verify(atomicFileOperationService, never()).executeAtomicUpload(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidFileTypeWhenStrictValidationRequiresBothConstraints() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello world".getBytes());
        FileUploadOptionsRecord options = new FileUploadOptionsRecord(
                List.of("pdf"),
                List.of("application/pdf"),
                NameConflictPolicy.MAKE_UNIQUE,
                null,
                true,
                null,
                false,
                Map.of(),
                false);

        FileUploadResultRecord result = service.uploadFile(file, options);

        assertFalse(result.success());
        assertEquals(FileErrorReason.INVALID_TYPE, result.errorReason());
        verify(metricsService).recordSecurityRejection("invalid_file_type");
    }

    @Test
    void releasesRenameReservationWhenAtomicUploadFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", "hello world".getBytes());
        FileUploadOptionsRecord options = new FileUploadOptionsRecord(
                List.of("txt"),
                List.of("text/plain"),
                NameConflictPolicy.RENAME,
                null,
                true,
                null,
                false,
                Map.of(),
                false);

        when(fileNamingService.generateUniqueNameForRename(eq("report.txt"), any()))
                .thenReturn(new ThreadSafeFileNamingService.UniqueNameResult(
                        "report(1).txt",
                        FileNameHandler.decompose("report(1).txt"),
                        2,
                        ThreadSafeFileNamingService.NamingStrategy.INCREMENTAL));
        when(atomicFileOperationService.executeAtomicUpload(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IOException("disk error"));

        FileUploadResultRecord result = service.uploadFile(file, options);

        assertFalse(result.success());
        assertEquals(FileErrorReason.UNKNOWN_ERROR, result.errorReason());
        verify(fileNamingService).releaseReservedName(any(), eq("report(1).txt"));
    }

    @Test
    void throwsFileSizeLimitExceededExceptionUsingEffectiveLimit() {
        byte[] oversized = new byte[2 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", oversized);
        FileUploadOptionsRecord options = new FileUploadOptionsRecord(
                List.of(),
                List.of(),
                NameConflictPolicy.MAKE_UNIQUE,
                1L,
                false,
                null,
                false,
                Map.of(),
                false);

        FileSizeLimitExceededException ex =
                assertThrows(FileSizeLimitExceededException.class, () -> service.uploadFile(file, options));

        assertEquals(oversized.length, ex.getFileSize());
        assertEquals(1024L * 1024L, ex.getMaxAllowedSize());
    }

    @Test
    void usesForwardedClientIpWhenTrackingFailures() {
        ReflectionTestUtils.setField(service, "request", request);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.1.1.1, 10.1.1.2");
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello world".getBytes());
        FileUploadOptionsRecord options = new FileUploadOptionsRecord(
                List.of("pdf"),
                List.of("application/pdf"),
                NameConflictPolicy.MAKE_UNIQUE,
                null,
                true,
                null,
                false,
                Map.of(),
                false);

        service.uploadFile(file, options);

        verify(uploadAttemptTracker).recordFailedUpload(eq("10.1.1.1"), eq("note.txt"), eq(FileErrorReason.INVALID_TYPE));
    }

    @Test
    void bulkUploadRejectsWhenFileCountExceedsLimit() {
        properties.getBulkUpload().setMaxFilesPerBatch(1);
        MockMultipartFile[] files = {
                new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()),
                new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes())
        };

        BulkUploadResultRecord result = service.uploadMultipleFiles(files, FileUploadOptionsRecord.defaultOptions());

        assertEquals(2, result.totalProcessed());
        assertEquals(2, result.totalFailed());
        assertEquals(FileErrorReason.RATE_LIMIT_EXCEEDED, result.results().get(0).errorReason());
    }

    @Test
    void bulkUploadRejectsWhenTotalSizeExceedsLimit() {
        properties.getBulkUpload().setMaxTotalSizeMb(0);
        MockMultipartFile[] files = {
                new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes())
        };

        BulkUploadResultRecord result = service.uploadMultipleFiles(files, FileUploadOptionsRecord.defaultOptions());

        assertEquals(1, result.totalProcessed());
        assertEquals(1, result.totalFailed());
        assertEquals(FileErrorReason.FILE_TOO_LARGE, result.results().get(0).errorReason());
    }

    @Test
    void bulkUploadFailFastCancelsRemainingFilesAfterFailure() throws Exception {
        when(inputValidationService.validateUploadFile(any(), any())).thenAnswer(invocation -> {
            org.springframework.web.multipart.MultipartFile file = invocation.getArgument(0);
            if ("bad.txt".equals(file.getOriginalFilename())) {
                return InputValidationService.ValidationResult.failure(
                        new InputValidationService.ValidationError(
                                "file", "blocked", FileErrorReason.INVALID_TYPE, "bad.txt"));
            }
            return InputValidationService.ValidationResult.success();
        });

        MockMultipartFile[] files = {
                new MockMultipartFile("file", "good.txt", "text/plain", "good".getBytes()),
                new MockMultipartFile("file", "bad.txt", "text/plain", "bad".getBytes()),
                new MockMultipartFile("file", "later.txt", "text/plain", "later".getBytes())
        };
        when(atomicFileOperationService.executeAtomicUpload(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AtomicFileOperationService.UploadTransaction());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
                .thenReturn("file-id");

        BulkUploadResultRecord result = service.uploadMultipleFiles(
                files,
                new FileUploadOptionsRecord(List.of(), List.of(), NameConflictPolicy.MAKE_UNIQUE, null, false, null, false, Map.of(), true));

        assertEquals(3, result.totalProcessed());
        assertEquals(1, result.totalSuccess());
        assertEquals(2, result.totalFailed());
        assertEquals(1, result.totalCancelled());
        assertTrue(result.wasFailFastTriggered());
        assertEquals(FileErrorReason.INVALID_TYPE, result.results().get(1).errorReason());
        assertEquals(FileErrorReason.BULK_UPLOAD_CANCELLED, result.results().get(2).errorReason());
    }

    @Test
    void bulkUploadParallelReturnsTimeoutForSlowFiles() throws Exception {
        properties.getBulkUpload().setTimeoutSeconds(1);
        when(inputValidationService.validateUploadFile(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(1500);
            return InputValidationService.ValidationResult.success();
        });
        when(atomicFileOperationService.executeAtomicUpload(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AtomicFileOperationService.UploadTransaction());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
                .thenReturn("file-id");

        MockMultipartFile[] files = {
                new MockMultipartFile("file", "slow.txt", "text/plain", "slow".getBytes())
        };

        BulkUploadResultRecord result = service.uploadMultipleFiles(
                files,
                new FileUploadOptionsRecord(List.of(), List.of(), NameConflictPolicy.MAKE_UNIQUE, null, false, null, false, Map.of(), false));

        assertEquals(1, result.totalProcessed());
        assertEquals(1, result.totalFailed());
        assertEquals(FileErrorReason.BULK_UPLOAD_TIMEOUT, result.results().get(0).errorReason());
    }

    @Test
    void bulkUploadFailFastHandlesUnexpectedExceptionAndCancelsRemaining() throws Exception {
        when(inputValidationService.validateUploadFile(any(), any())).thenAnswer(invocation -> {
            org.springframework.web.multipart.MultipartFile file = invocation.getArgument(0);
            if ("boom.txt".equals(file.getOriginalFilename())) {
                throw new IllegalStateException("validator boom");
            }
            return InputValidationService.ValidationResult.success();
        });
        when(atomicFileOperationService.executeAtomicUpload(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AtomicFileOperationService.UploadTransaction());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
                .thenReturn("file-id");

        MockMultipartFile[] files = {
                new MockMultipartFile("file", "good.txt", "text/plain", "good".getBytes()),
                new MockMultipartFile("file", "boom.txt", "text/plain", "boom".getBytes()),
                new MockMultipartFile("file", "later.txt", "text/plain", "later".getBytes())
        };

        BulkUploadResultRecord result = service.uploadMultipleFiles(
                files,
                new FileUploadOptionsRecord(List.of(), List.of(), NameConflictPolicy.MAKE_UNIQUE, null, false, null, false, Map.of(), true));

        assertEquals(3, result.totalProcessed());
        assertEquals(1, result.totalSuccess());
        assertEquals(2, result.totalFailed());
        assertEquals(FileErrorReason.UNKNOWN_ERROR, result.results().get(1).errorReason());
        assertEquals(FileErrorReason.BULK_UPLOAD_CANCELLED, result.results().get(2).errorReason());
    }
}
