package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.api.enums.NameConflictPolicy;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.audit.SecurityAuditLogger;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.validation.InputValidationService;
import br.com.praxis.filemanagement.core.exception.FileSizeLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

/**
 * Testes de API para funcionalidades básicas de upload
 * Foca no comportamento da API pública e DTOs
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("File Upload API Tests")
public class FileUploadApiTest {

    @Mock(lenient = true)
    private FileStructureValidator fileStructureValidator;

    @Mock(lenient = true)
    private MagicNumberValidator magicNumberValidator;

    @Mock(lenient = true)
    private FileIdMappingService fileIdMappingService;

    @Mock(lenient = true)
    private ThreadSafeFileNamingService fileNamingService;

    @Mock(lenient = true)
    private AtomicFileOperationService atomicFileOperationService;

    @Mock(lenient = true)
    private InputValidationService inputValidationService;


    @Mock(lenient = true)
    private SecurityAuditLogger securityAuditLogger;

    @Mock(lenient = true)
    private UploadAttemptTracker uploadAttemptTracker;

    @Mock(lenient = true)
    private FileUploadMetricsService metricsService;

    private LocalStorageFileService fileService;
    private FileManagementProperties properties;

    @BeforeEach
    void setUp() {
        // Setup properties with security disabled for API tests
        properties = new FileManagementProperties();

        // Configure security settings - disabled for basic API tests
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setBlockSuspiciousExtensions(false);
        security.setMagicNumberValidation(false);
        security.setMandatoryVirusScan(false);
        properties.setSecurity(security);

        // Configure virus scanning - disabled
        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(false);
        properties.setVirusScanning(virusScanning);

        // Configure audit logging - disabled for simplicity
        FileManagementProperties.AuditLogging auditLogging = new FileManagementProperties.AuditLogging();
        auditLogging.setEnabled(false);
        properties.setAuditLogging(auditLogging);

        // Initialize service and inject mocked dependencies using reflection
        fileService = new LocalStorageFileService();

        // Use reflection to inject mocked dependencies
        try {
            java.lang.reflect.Field propertiesField = LocalStorageFileService.class.getDeclaredField("fileManagementProperties");
            propertiesField.setAccessible(true);
            propertiesField.set(fileService, properties);

            java.lang.reflect.Field structureValidatorField = LocalStorageFileService.class.getDeclaredField("fileStructureValidator");
            structureValidatorField.setAccessible(true);
            structureValidatorField.set(fileService, fileStructureValidator);

            java.lang.reflect.Field magicValidatorField = LocalStorageFileService.class.getDeclaredField("magicNumberValidator");
            magicValidatorField.setAccessible(true);
            magicValidatorField.set(fileService, magicNumberValidator);

            java.lang.reflect.Field fileIdField = LocalStorageFileService.class.getDeclaredField("fileIdMappingService");
            fileIdField.setAccessible(true);
            fileIdField.set(fileService, fileIdMappingService);


            properties.setUploadDir("./test-uploads");

            // Inject additional dependencies

            java.lang.reflect.Field auditField = LocalStorageFileService.class.getDeclaredField("securityAuditLogger");
            auditField.setAccessible(true);
            auditField.set(fileService, securityAuditLogger);

            java.lang.reflect.Field trackerField = LocalStorageFileService.class.getDeclaredField("uploadAttemptTracker");
            trackerField.setAccessible(true);
            trackerField.set(fileService, uploadAttemptTracker);

            java.lang.reflect.Field metricsField = LocalStorageFileService.class.getDeclaredField("metricsService");
            metricsField.setAccessible(true);
            metricsField.set(fileService, metricsService);

            java.lang.reflect.Field inputField = LocalStorageFileService.class.getDeclaredField("inputValidationService");
            inputField.setAccessible(true);
            inputField.set(fileService, inputValidationService);

            // Inject missing ThreadSafeFileNamingService
            java.lang.reflect.Field namingField = LocalStorageFileService.class.getDeclaredField("fileNamingService");
            namingField.setAccessible(true);
            namingField.set(fileService, fileNamingService);

            // Inject missing AtomicFileOperationService
            java.lang.reflect.Field atomicField = LocalStorageFileService.class.getDeclaredField("atomicFileOperationService");
            atomicField.setAccessible(true);
            atomicField.set(fileService, atomicFileOperationService);

        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test dependencies", e);
        }

        // Setup default mocks for successful upload
        setupDefaultMocks();
    }

    private void setupDefaultMocks() {
        // Input validation passes by default
        when(inputValidationService.validateUploadFile(any(), any())).thenReturn(
            new InputValidationService.ValidationResult(true, List.of()));

        // Structure validation passes by default
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());

        // Magic number validation passes by default
        when(magicNumberValidator.validateMagicNumbers(any(), any(), any()))
            .thenReturn(new MagicNumberValidator.ValidationResult(true, "Valid magic numbers", "VALID"));

        // File ID generation
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("generated-file-id");

        // File naming service for unique names - handle IOException
        try {
            when(fileNamingService.generateUniqueNameForRename(anyString(), any()))
                .thenAnswer(invocation -> {
                    String filename = invocation.getArgument(0);
                    return new ThreadSafeFileNamingService.UniqueNameResult(
                        filename, // finalName - return original name
                        null, // components - simplified for testing
                        1, // attemptsUsed
                        ThreadSafeFileNamingService.NamingStrategy.ORIGINAL // strategy
                    );
                });
        } catch (java.io.IOException e) {
            // This won't happen during mock setup, but required by compiler
        }

        // Atomic file operations - handle IOException
        try {
            when(atomicFileOperationService.executeAtomicUpload(any(), any(), anyString(), anyString(), any(), any()))
                .thenReturn(new AtomicFileOperationService.UploadTransaction()); // Return valid transaction
        } catch (java.io.IOException e) {
            // This won't happen during mock setup, but required by compiler
        }
    }

    // ==================================================================================
    // TESTES BÁSICOS DE API
    // ==================================================================================

    @Test
    @DisplayName("Should successfully upload valid file with default options")
    void shouldUploadValidFileWithDefaultOptions() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Hello World".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        // Assert
        assertTrue(result.success(), "Upload should succeed with default options");
        assertEquals("document.txt", result.originalFilename());
        assertEquals("generated-file-id", result.fileId());
        assertEquals(11, result.fileSize()); // "Hello World".length()
        assertEquals("text/plain", result.mimeType());
        assertNotNull(result.uploadTimestamp());
        assertNull(result.errorReason());
        assertNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should reject file when only allowedExtensions constraint is provided")
    void shouldRejectFileWhenOnlyAllowedExtensionsConstraintIsProvided() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            "%PDF-1.4".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .allowedExtensions(List.of("txt"))
            .build();

        FileUploadResultRecord result = fileService.uploadFile(file, options);

        assertFalse(result.success());
        assertEquals(FileErrorReason.INVALID_TYPE, result.errorReason());
    }

    @Test
    @DisplayName("Should reject file when only acceptMimeTypes constraint is provided")
    void shouldRejectFileWhenOnlyAcceptMimeTypesConstraintIsProvided() {
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "plain text".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .acceptMimeTypes(List.of("application/pdf"))
            .build();

        FileUploadResultRecord result = fileService.uploadFile(file, options);

        assertFalse(result.success());
        assertEquals(FileErrorReason.INVALID_TYPE, result.errorReason());
    }

    @Test
    @DisplayName("Should report partial failure in bulk upload when one file violates allowed extension")
    void shouldReportPartialFailureInBulkUploadWhenOneFileViolatesAllowedExtension() {
        MultipartFile ok = new MockMultipartFile(
            "files",
            "ok.txt",
            "text/plain",
            "ok".getBytes()
        );
        MultipartFile bad = new MockMultipartFile(
            "files",
            "bad.pdf",
            "application/pdf",
            "%PDF-1.4".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .allowedExtensions(List.of("txt"))
            .build();

        var result = fileService.uploadMultipleFiles(new MultipartFile[]{ok, bad}, options);

        assertEquals(2, result.results().size());
        assertEquals(1, result.totalSuccess());
        assertEquals(1, result.totalFailed());
        assertTrue(result.results().stream().anyMatch(FileUploadResultRecord::success));
        assertTrue(result.results().stream().anyMatch(item -> !item.success() && item.errorReason() == FileErrorReason.INVALID_TYPE));
    }

    @Test
    @DisplayName("Should fail upload for empty file")
    void shouldFailUploadForEmptyFile() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "empty.txt",
            "text/plain",
            new byte[0] // Empty file
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        // Assert
        assertFalse(result.success(), "Upload should fail for empty files");
        assertEquals(FileErrorReason.EMPTY_FILE, result.errorReason());
        assertEquals("empty.txt", result.originalFilename());
        assertEquals(0L, result.fileSize());
        assertNotNull(result.errorMessage());
    }

    @Test
    @DisplayName("Should fail upload for file too large")
    void shouldFailUploadForFileTooLarge() {
        // Arrange
        byte[] largeContent = new byte[1024 * 1024 * 200]; // 200MB
        MultipartFile file = new MockMultipartFile(
            "file",
            "large.txt",
            "text/plain",
            largeContent
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .maxUploadSizeMb(100L) // 100MB limit
            .build();

        // Act & Assert
        assertThrows(FileSizeLimitExceededException.class, () -> fileService.uploadFile(file, options));
    }

    @Test
    @DisplayName("Should apply default size limit when options are null")
    void shouldApplyDefaultSizeLimitWhenOptionsNull() {
        properties.setMaxFileSizeBytes(1024 * 1024); // 1MB limit
        byte[] largeContent = new byte[1024 * 1024 * 2]; // 2MB
        MultipartFile file = new MockMultipartFile(
            "file",
            "large.txt",
            "text/plain",
            largeContent
        );

        assertThrows(FileSizeLimitExceededException.class, () -> fileService.uploadFile(file, null));
    }

    // ==================================================================================
    // TESTES DE FileUploadOptionsRecord
    // ==================================================================================

    @Test
    @DisplayName("Should create FileUploadOptionsRecord with builder pattern")
    void shouldCreateOptionsWithBuilder() {
        // Act
        FileUploadOptionsRecord options = FileUploadOptionsRecord.builder()
            .maxUploadSizeMb(50L)
            .allowedExtensions(List.of("txt", "pdf", "jpg"))
            .targetDirectory("/custom/uploads")
            .nameConflictPolicy(NameConflictPolicy.OVERWRITE)
            .build();

        // Assert
        assertEquals(50L, options.maxUploadSizeMb());
        assertEquals(List.of("txt", "pdf", "jpg"), options.allowedExtensions());
        assertEquals("/custom/uploads", options.targetDirectory());
        assertEquals(NameConflictPolicy.OVERWRITE, options.nameConflictPolicy());
    }

    @Test
    @DisplayName("Should create default FileUploadOptionsRecord")
    void shouldCreateDefaultOptions() {
        // Act
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Assert
        assertNotNull(options);
        assertNotNull(options.allowedExtensions());
        assertNotNull(options.nameConflictPolicy());
        // Note: targetDirectory can be null in default options
        assertTrue(options.maxUploadSizeMb() > 0);
    }

    @Test
    @DisplayName("Should create FileUploadOptionsRecord with factory methods")
    void shouldCreateOptionsWithFactoryMethods() {
        // Act - Using builder to create different configurations
        FileUploadOptionsRecord strictOptions = FileUploadOptionsRecord.builder()
            .strictValidation(true)
            .maxUploadSizeMb(10L)
            .build();
        FileUploadOptionsRecord permissiveOptions = FileUploadOptionsRecord.builder()
            .strictValidation(false)
            .maxUploadSizeMb(100L)
            .build();

        // Assert
        assertNotNull(strictOptions);
        assertNotNull(permissiveOptions);
        // Strict should have smaller size limits
        assertTrue(strictOptions.maxUploadSizeMb() <= permissiveOptions.maxUploadSizeMb());
        assertTrue(strictOptions.strictValidation());
        assertFalse(permissiveOptions.strictValidation());
    }

    // ==================================================================================
    // TESTES DE FileUploadResultRecord
    // ==================================================================================

    @Test
    @DisplayName("Should create success FileUploadResultRecord")
    void shouldCreateSuccessResult() {
        // Act
        FileUploadResultRecord result = FileUploadResultRecord.success(
            "document.txt",
            "server-filename.txt",
            "file-id-123",
            1024L,
            "text/plain"
        );

        // Assert
        assertTrue(result.success());
        assertEquals("document.txt", result.originalFilename());
        assertEquals("server-filename.txt", result.serverFilename());
        assertEquals("file-id-123", result.fileId());
        assertEquals(1024L, result.fileSize());
        assertEquals("text/plain", result.mimeType());
        assertNull(result.errorReason());
        assertNull(result.errorMessage());
        assertNotNull(result.uploadTimestamp());
    }

    @Test
    @DisplayName("Should create error FileUploadResultRecord")
    void shouldCreateErrorResult() {
        // Act
        FileUploadResultRecord result = FileUploadResultRecord.error(
            "invalid.exe",
            FileErrorReason.DANGEROUS_FILE_TYPE,
            "File type not allowed"
        );

        // Assert
        assertFalse(result.success());
        assertEquals("invalid.exe", result.originalFilename());
        assertEquals(FileErrorReason.DANGEROUS_FILE_TYPE, result.errorReason());
        assertEquals("File type not allowed", result.errorMessage());
        assertNull(result.serverFilename());
        assertNull(result.fileId());
        assertEquals(0L, result.fileSize());
        assertNull(result.mimeType());
        assertNotNull(result.uploadTimestamp());
    }

    @Test
    @DisplayName("Should create error FileUploadResultRecord with file size")
    void shouldCreateErrorResultWithFileSize() {
        // Act
        FileUploadResultRecord result = FileUploadResultRecord.error(
            "large.pdf",
            FileErrorReason.FILE_TOO_LARGE,
            "File exceeds maximum size",
            5242880L // 5MB
        );

        // Assert
        assertFalse(result.success());
        assertEquals("large.pdf", result.originalFilename());
        assertEquals(FileErrorReason.FILE_TOO_LARGE, result.errorReason());
        assertEquals("File exceeds maximum size", result.errorMessage());
        assertEquals(5242880L, result.fileSize());
    }

    // ==================================================================================
    // TESTES DE ESTRUTURA DE VALIDAÇÃO
    // ==================================================================================


    // ==================================================================================
    // TESTES DE METADATA E INFORMAÇÕES ADICIONAIS
    // ==================================================================================

    @Test
    @DisplayName("Should preserve original filename in result")
    void shouldPreserveOriginalFilename() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "My Document (Version 2).txt",
            "text/plain",
            "Content".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        // Assert
        assertTrue(result.success());
        assertEquals("My Document (Version 2).txt", result.originalFilename());
    }

    @Test
    @DisplayName("Should generate file ID for successful uploads")
    void shouldGenerateFileIdForSuccessfulUploads() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "Content".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Mock file ID generation
        when(fileIdMappingService.generateFileId(anyString(), eq("test.txt"), anyString(), eq(7L), eq("text/plain")))
            .thenReturn("custom-file-id-12345");

        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        // Assert
        assertTrue(result.success());
        assertEquals("custom-file-id-12345", result.fileId());
        verify(fileIdMappingService).generateFileId(anyString(), eq("test.txt"), anyString(), eq(7L), eq("text/plain"));
    }


    @Test
    @DisplayName("Should record correct file size")
    void shouldRecordCorrectFileSize() {
        // Arrange
        String content = "This is a test file with specific content length";
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            content.getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        // Assert
        assertTrue(result.success());
        assertEquals(content.length(), result.fileSize());
    }
}
