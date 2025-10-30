package br.com.praxis.filemanagement.core.services;

import br.com.praxis.filemanagement.api.dtos.FileUploadOptionsRecord;
import br.com.praxis.filemanagement.api.dtos.FileUploadResultRecord;
import br.com.praxis.filemanagement.api.enums.FileErrorReason;
import br.com.praxis.filemanagement.core.config.FileManagementProperties;
import br.com.praxis.filemanagement.core.audit.SecurityAuditLogger;
import br.com.praxis.filemanagement.core.audit.UploadAttemptTracker;
import br.com.praxis.filemanagement.core.validation.InputValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

/**
 * Testes de segurança para funcionalidades de upload de arquivo
 * Foca nas validações críticas implementadas
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("File Upload Security Tests")
class FileUploadSecurityTest {

    @Mock(lenient = true)
    private FileStructureValidator fileStructureValidator;
    
    @Mock(lenient = true)
    private MagicNumberValidator magicNumberValidator;
    
    @Mock(lenient = true)
    private VirusScanningService virusScanningService;
    
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
        // Setup properties with security enabled
        properties = new FileManagementProperties();
        
        // Configure security settings
        FileManagementProperties.Security security = new FileManagementProperties.Security();
        security.setBlockSuspiciousExtensions(true);
        security.setMagicNumberValidation(true);
        security.setMandatoryVirusScan(false); // Will be tested separately
        properties.setSecurity(security);
        
        // Configure virus scanning
        FileManagementProperties.VirusScanning virusScanning = new FileManagementProperties.VirusScanning();
        virusScanning.setEnabled(false);
        properties.setVirusScanning(virusScanning);
        
        // Configure audit logging
        FileManagementProperties.AuditLogging auditLogging = new FileManagementProperties.AuditLogging();
        auditLogging.setEnabled(false); // Simplify for testing
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
            
            java.lang.reflect.Field virusField = LocalStorageFileService.class.getDeclaredField("virusScanningService");
            virusField.setAccessible(true);
            virusField.set(fileService, virusScanningService);
            

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
        
        // Setup default mock behaviors
        setupDefaultMocks();
    }
    
    private void setupDefaultMocks() {
        // Rate limiting always allows uploads in tests
        
        // Input validation passes by default
        when(inputValidationService.validateUploadFile(any(), any())).thenReturn(
            new InputValidationService.ValidationResult(true, List.of()));
        
        // File structure validation passes by default
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        
        // Magic number validation passes by default
        when(magicNumberValidator.validateMagicNumbers(any(), any(), any()))
            .thenReturn(new MagicNumberValidator.ValidationResult(true, "Valid magic numbers", "VALID"));
        
        // File ID generation
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
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
            
        // Virus scanning service mock
        when(virusScanningService.scanFile(any(), any()))
            .thenReturn(VirusScanningService.ScanResult.clean());
    }
    
    // ==================================================================================
    // TESTES PARA BLOCK-SUSPICIOUS-EXTENSIONS
    // ==================================================================================
    
    @Test
    @DisplayName("Should block .exe files when suspicious extensions blocking is enabled")
    void shouldBlockExeFiles() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "malicious.exe",
            "application/octet-stream",
            "fake exe content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertFalse(result.success(), "Upload should fail for .exe files");
        assertEquals(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED, result.errorReason());
        assertTrue(result.errorMessage().contains("exe"));
        assertEquals("malicious.exe", result.originalFilename());
    }
    
    @Test
    @DisplayName("Should block .bat files when suspicious extensions blocking is enabled")
    void shouldBlockBatFiles() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "script.bat",
            "text/plain",
            "@echo off\necho hello".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertFalse(result.success(), "Upload should fail for .bat files");
        assertEquals(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED, result.errorReason());
        assertTrue(result.errorMessage().contains("bat"));
    }
    
    @Test
    @DisplayName("Should block .vbs files when suspicious extensions blocking is enabled")
    void shouldBlockVbsFiles() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "virus.vbs",
            "text/plain",
            "MsgBox \"Hello\"".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertFalse(result.success(), "Upload should fail for .vbs files");
        assertEquals(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED, result.errorReason());
        assertTrue(result.errorMessage().contains("vbs"));
    }
    
    @Test
    @DisplayName("Should allow .txt files when suspicious extensions blocking is enabled")
    void shouldAllowTxtFiles() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Safe document content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Mock dependencies for successful upload
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertTrue(result.success(), "Upload should succeed for .txt files");
        assertEquals("document.txt", result.originalFilename());
        assertNotNull(result.fileId());
    }
    
    @Test
    @DisplayName("Should allow all extensions when suspicious extensions blocking is disabled")
    void shouldAllowAllExtensionsWhenDisabled() {
        // Arrange
        properties.getSecurity().setBlockSuspiciousExtensions(false);
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "test.exe",
            "application/octet-stream",
            "test content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Mock dependencies for successful upload
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertTrue(result.success(), "Upload should succeed when blocking is disabled");
        assertEquals("test.exe", result.originalFilename());
    }
    
    // ==================================================================================
    // TESTES PARA MANDATORY-VIRUS-SCAN
    // ==================================================================================
    
    
    @Test
    @DisplayName("Should allow files when mandatory virus scan is disabled")  
    void shouldAllowWhenMandatoryVirusScanDisabled() {
        // Arrange
        properties.getSecurity().setMandatoryVirusScan(false);
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Safe content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Mock dependencies for successful upload
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertTrue(result.success(), "Upload should succeed when mandatory virus scan is disabled");
    }
    
    // ==================================================================================
    // TESTES PARA MAGIC-NUMBER-VALIDATION
    // ==================================================================================
    
    
    @Test
    @DisplayName("Should allow files with valid magic numbers when validation is enabled")
    void shouldAllowValidMagicNumbers() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Safe content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Mock magic number validation to pass
        when(magicNumberValidator.validateMagicNumbers(any(), any(), any()))
            .thenReturn(new MagicNumberValidator.ValidationResult(true, "Valid magic numbers", "VALID"));
        
        // Mock structure validation to pass
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertTrue(result.success(), "Upload should succeed for valid magic numbers");
    }
    
    @Test
    @DisplayName("Should skip magic number validation when disabled")
    void shouldSkipMagicNumberValidationWhenDisabled() {
        // Arrange
        properties.getSecurity().setMagicNumberValidation(false);
        
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "Safe content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Mock structure validation to pass
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertTrue(result.success(), "Upload should succeed when magic number validation is disabled");
        verify(magicNumberValidator, never()).validateMagicNumbers(any(), any(), any());
    }

    @Test
    @DisplayName("Should return error when MIME type does not match content")
    void shouldReturnMimeTypeMismatchError() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document.pdf",
            "image/png",
            "PDF content".getBytes()
        );

        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();

        when(magicNumberValidator.validateMagicNumbers(any(), any(), any()))
            .thenReturn(new MagicNumberValidator.ValidationResult(false, "Mismatch", "MIME_TYPE_MISMATCH"));
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");

        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);

        // Assert
        assertFalse(result.success(), "Upload should fail due to MIME type mismatch");
        assertEquals(FileErrorReason.MIME_TYPE_MISMATCH, result.errorReason());
    }
    
    // ==================================================================================
    // TESTES DE CASOS EDGE
    // ==================================================================================
    
    @Test
    @DisplayName("Should handle files without extensions")
    void shouldHandleFilesWithoutExtensions() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "document", // No extension
            "text/plain",
            "Safe content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Mock dependencies for successful upload
        when(fileStructureValidator.validateFileStructure(any(), any(), any()))
            .thenReturn(FileStructureValidator.ValidationResult.valid());
        when(fileIdMappingService.generateFileId(any(), any(), any(), anyLong(), any()))
            .thenReturn("test-file-id");
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertTrue(result.success(), "Upload should succeed for files without extensions");
    }
    
    @Test
    @DisplayName("Should be case insensitive for extensions")
    void shouldBeCaseInsensitiveForExtensions() {
        // Arrange
        MultipartFile file = new MockMultipartFile(
            "file",
            "MALICIOUS.EXE", // Uppercase extension
            "application/octet-stream",
            "fake exe content".getBytes()
        );
        
        FileUploadOptionsRecord options = FileUploadOptionsRecord.defaultOptions();
        
        // Act
        FileUploadResultRecord result = fileService.uploadFile(file, options);
        
        // Assert
        assertFalse(result.success(), "Upload should fail for uppercase .EXE files");
        assertEquals(FileErrorReason.SUSPICIOUS_EXTENSION_BLOCKED, result.errorReason());
    }
    
}